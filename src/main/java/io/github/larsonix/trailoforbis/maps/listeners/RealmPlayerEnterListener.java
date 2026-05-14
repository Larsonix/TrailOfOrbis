package io.github.larsonix.trailoforbis.maps.listeners;

import com.hypixel.hytale.builtin.portals.resources.PortalWorld;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.HudComponent;
import com.hypixel.hytale.protocol.packets.interface_.UpdatePortal;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.HudManager;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.DrainPlayerFromWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.event.events.player.RemovedPlayerFromWorldEvent;
import com.hypixel.hytale.server.core.modules.entity.player.ChunkTracker;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import io.github.larsonix.trailoforbis.maps.RealmsManager;
import io.github.larsonix.trailoforbis.maps.core.RealmBiomeType;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;
import io.github.larsonix.trailoforbis.maps.templates.RealmTemplateRegistry;
import io.github.larsonix.trailoforbis.util.TerrainUtils;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Listens for players entering and leaving realm worlds.
 *
 * <p>Handles:
 * <ul>
 *   <li>Realm entry via portals (triggers combat HUD, mob spawning)</li>
 *   <li>Realm exit via portals (triggers tracking cleanup)</li>
 *   <li>Best-effort HUD removal on world drain (with guaranteed fallback at evacuation)</li>
 *   <li>Post-removal safety net cleanup and leave message suppression</li>
 * </ul>
 */
public class RealmPlayerEnterListener {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final RealmsManager realmsManager;

    public RealmPlayerEnterListener(@Nonnull RealmsManager realmsManager) {
        this.realmsManager = Objects.requireNonNull(realmsManager, "realmsManager cannot be null");
    }

    /**
     * Registers this listener with the event registry.
     */
    public void register(@Nonnull EventRegistry eventRegistry) {
        // Best-effort HUD removal when player leaves a world
        eventRegistry.registerGlobal(
            EventPriority.NORMAL,
            DrainPlayerFromWorldEvent.class,
            this::onPlayerDrainedFromWorld
        );

        // Realm entry/exit tracking
        eventRegistry.registerGlobal(
            EventPriority.NORMAL,
            AddPlayerToWorldEvent.class,
            this::onPlayerAddedToWorld
        );

        // Hide vanilla ObjectivePanel after player is fully ready
        // This MUST be in PlayerReadyEvent, NOT AddPlayerToWorldEvent, because
        // resetManagers() is called AFTER AddPlayerToWorldEvent and re-adds the HUD
        eventRegistry.registerGlobal(
            EventPriority.NORMAL,
            PlayerReadyEvent.class,
            this::onPlayerReady
        );

        // Safety net + leave message suppression after player is fully removed from world.
        // LATE priority — runs after all other handlers since this is defensive cleanup.
        eventRegistry.registerGlobal(
            EventPriority.LATE,
            RemovedPlayerFromWorldEvent.class,
            this::onPlayerRemovedFromWorld
        );

        LOGGER.atInfo().log("RealmPlayerEnterListener registered");
    }

    /**
     * Synchronous HUD removal when player is leaving a world.
     *
     * <p>This fires on the world thread BEFORE the player leaves, allowing
     * synchronous HUD removal. This is the most reliable removal point for
     * players exiting via victory portal.
     *
     * <p>We always attempt HUD removal regardless of isPlayerInRealm() status,
     * because tracking state may be stale. The HUD manager safely handles
     * cases where the player has no active HUDs.
     */
    private void onPlayerDrainedFromWorld(@Nonnull DrainPlayerFromWorldEvent event) {
        UUID playerId = getPlayerUuid(event.getHolder());
        if (playerId == null) {
            LOGGER.atWarning().log("DrainPlayerFromWorldEvent: Could not extract player UUID");
            return;
        }

        LOGGER.atFine().log("DrainPlayerFromWorldEvent fired for player %s - discarding stale HUDs",
            playerId.toString().substring(0, 8));

        // Discard stale HUDs without sending packets. Player.resetManagers() has
        // already sent CustomHud(clear=true) which destroyed all HyUI elements on
        // the client. Calling hide() would send Set commands to those cleared
        // elements, crashing the client.
        realmsManager.getHudManager().discardAllHudsForPlayer(playerId);

        // Clean up spawn protection if active — the player's entity is leaving the
        // world, so the invulnerability flag is destroyed with it. We just need to untrack.
        realmsManager.getSpawnProtection().cleanup(playerId);
    }

    /**
     * Post-removal cleanup and leave message suppression.
     *
     * <p>Fires AFTER the player entity is fully removed from the world. Serves two purposes:
     * <ol>
     *   <li><b>Leave message suppression</b>: Players leaving realm/sanctum instance worlds
     *       should not broadcast "PlayerX left" to other worlds.</li>
     *   <li><b>Safety net</b>: Defensive sweep of tracking maps to catch any HUD or sanctum
     *       state missed by DrainPlayerFromWorldEvent (e.g., server crash during teleport,
     *       race condition, or edge case where drain didn't fire).</li>
     * </ol>
     *
     * <p>Registered at LATE priority so all other handlers run first.
     */
    private void onPlayerRemovedFromWorld(@Nonnull RemovedPlayerFromWorldEvent event) {
        UUID playerId = getPlayerUuid(event.getHolder());
        if (playerId == null) {
            return;
        }

        World world = event.getWorld();

        // Suppress leave messages for realm/sanctum instance worlds
        if (realmsManager.getRealmByWorld(world).isPresent()) {
            event.setBroadcastLeaveMessage(false);
            LOGGER.atFine().log("Suppressed leave message for player %s leaving realm world",
                playerId.toString().substring(0, 8));
        }

        // Safety net: defensive HUD cleanup in case DrainPlayerFromWorldEvent missed it.
        // Use discard (not remove) — resetManagers already cleared the client's DOM.
        realmsManager.getHudManager().discardAllHudsForPlayer(playerId);
    }

    /**
     * Handles player entering a world - detects realm entry and exit.
     */
    private void onPlayerAddedToWorld(@Nonnull AddPlayerToWorldEvent event) {
        World world = event.getWorld();
        Holder<EntityStore> holder = event.getHolder();
        UUID playerId = getPlayerUuid(holder);
        if (playerId == null) {
            return;
        }

        Optional<RealmInstance> realmOpt = realmsManager.getRealmByWorld(world);

        if (realmOpt.isPresent()) {
            // Player entered a realm world
            RealmInstance realm = realmOpt.get();

            // Guard: Don't register into completed/closing realms
            if (!realm.allowsEntry()) {
                LOGGER.atInfo().log("Player %s entered realm %s world but entry not allowed (state: %s) - ignoring",
                    playerId.toString().substring(0, 8),
                    realm.getRealmId().toString().substring(0, 8),
                    realm.getState());
                return;
            }

            if (realmsManager.isPlayerInRealm(playerId)) {
                return; // Already tracked
            }

            LOGGER.atInfo().log("Player %s entered realm %s",
                playerId.toString().substring(0, 8),
                realm.getRealmId().toString().substring(0, 8));

            realmsManager.notifyPlayerEnteredRealm(playerId, realm);
        } else {
            // Player entered a non-realm world - might have left a realm
            if (realmsManager.isPlayerInRealm(playerId)) {
                LOGGER.atInfo().log("Player %s left realm via portal",
                    playerId.toString().substring(0, 8));
                realmsManager.handlePlayerExitedViaPortal(playerId);
            }
        }
    }

    /**
     * Handles player ready in a realm — hides vanilla UI, defers combat HUD.
     *
     * <p>CRITICAL: This must be in PlayerReadyEvent, NOT AddPlayerToWorldEvent.
     * During world transfer, resetManagers() is called before PlayerReadyEvent,
     * resetting the HUD to defaults (re-adding ObjectivePanel). PlayerReadyEvent
     * fires AFTER all reset/setup operations are complete.
     *
     * <p>Vanilla UI suppression (ObjectivePanel, portal timer) runs IMMEDIATELY
     * in the event handler — no {@code world.execute()} deferral. The event
     * dispatches on the world thread (via {@code world.execute()} in Hytale's
     * {@code GamePacketHandler.handle(ClientReady)}), so store access and packet
     * sends are safe. Immediate execution eliminates the 1-frame ObjectivePanel flash.
     *
     * <p>Combat HUD creation is still deferred by one tick for MHUD stability —
     * {@code MultipleHUD.setCustomHud()} double-show() generates Set commands
     * that reference Append-created elements, which crash if both packets arrive
     * during JoinWorld processing.
     */
    private void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();
        if (world == null) {
            return;
        }

        Optional<RealmInstance> realmOpt = realmsManager.getRealmByWorld(world);
        if (realmOpt.isEmpty()) {
            return;
        }

        // ─── IMMEDIATE: suppress vanilla UI elements ─────────────────────
        // No world.execute() deferral — eliminates ObjectivePanel flash.

        Ref<EntityStore> ref = event.getPlayerRef();
        if (ref == null || !ref.isValid()) {
            return;
        }

        Store<EntityStore> store = world.getEntityStore().getStore();
        UUIDComponent uuidComp = store.getComponent(ref, UUIDComponent.getComponentType());
        UUID playerId = uuidComp != null ? uuidComp.getUuid() : null;
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());

        if (playerRef == null) {
            return;
        }

        // Hide vanilla portal timer UI
        PortalWorld portalWorld = store.getResource(PortalWorld.getResourceType());
        if (portalWorld != null && portalWorld.exists()) {
            playerRef.getPacketHandler().write(new UpdatePortal(null, null));

            if (playerId != null) {
                portalWorld.getSeesUi().remove(playerId);
            }

            LOGGER.atInfo().log("Hid vanilla portal timer for player %s in realm",
                playerId != null ? playerId.toString().substring(0, 8) : "unknown");
        }

        // Hide ObjectivePanel — immediate, no flash
        HudManager hudManager = player.getHudManager();
        hudManager.hideHudComponents(playerRef, HudComponent.ObjectivePanel);
        LOGGER.atFine().log("Hid vanilla ObjectivePanel for player %s in realm",
            playerId != null ? playerId.toString().substring(0, 8) : "unknown");

        // Restore default chunk transfer rate. Was boosted to 128/sec in
        // RealmTeleportHandler.teleportIntoRealm() to speed up chunk loading for
        // complex biomes (CellNoise2D Swamp). Now that the player has fully joined
        // the world, restore the connection-appropriate rate (36/128/256).
        ChunkTracker chunkTracker = store.getComponent(ref, ChunkTracker.getComponentType());
        if (chunkTracker != null) {
            chunkTracker.setDefaultMaxChunksPerSecond(playerRef);
            LOGGER.atFine().log("Restored default chunk rate for player %s after realm join",
                playerId != null ? playerId.toString().substring(0, 8) : "unknown");
        }

        // ─── SUFFOCATION RESCUE: post-teleport safety check ─────────────
        // Adapts the proven mob rescue pattern (RealmMobSpawner:898-911).
        // If Layer 1 (findSafeSpawnPosition) somehow placed the player inside
        // blocks, this detects and corrects it before they take damage.

        RealmInstance realm = realmOpt.get();

        if (playerId != null && !realm.getBiome().isUtilityBiome()) {
            rescueIfSuffocating(world, store, ref, playerId, realm.getBiome());
        }

        // ─── DEFERRED: combat HUD + spawn protection ─────────────────────

        // Grant spawn protection — invincible until first movement.
        // grant() handles its own world.execute() internally.
        if (playerId != null && !realm.getBiome().isUtilityBiome()) {
            realmsManager.getSpawnProtection().grant(playerId, world);
        }

        // Combat HUD — deferred for MHUD stability.
        // Defensive dedup: hasCombatHud() guard catches edge cases (e.g., rapid
        // re-entry before previous HUD is cleaned up). Hytale dispatches only ONE
        // PlayerReadyEvent per transition (AtomicReference.getAndSet(null) gate).
        if (playerId != null && !realm.getBiome().isUtilityBiome()
                && !realmsManager.getHudManager().hasCombatHud(playerId)) {
            final UUID deferredPlayerId = playerId;
            world.execute(() -> {
                // Fresh ref at execution time — immune to stale captures if the player
                // transitions again before this task runs.
                PlayerRef freshRef = com.hypixel.hytale.server.core.universe.Universe.get()
                    .getPlayer(deferredPlayerId);
                if (freshRef == null || !freshRef.isValid()) return;
                if (realmsManager.getHudManager().hasCombatHud(deferredPlayerId)) return;
                realmsManager.getHudManager().showCombatHud(deferredPlayerId, freshRef, realm);
            });
        }
    }

    /**
     * Checks if the player is suffocating (body inside solid blocks) and teleports
     * them to the nearest safe position if so.
     *
     * <p>This is a recovery mechanism for when spawn point selection (Layer 1 in
     * {@link TerrainUtils#findSafeSpawnPosition}) fails due to edge cases like
     * terrain noise peaks, late structure placement, or heightmap inaccuracies.
     *
     * <p>Adapts the proven mob suffocation rescue from {@code RealmMobSpawner}
     * (lines 898-911), which checks feet+head blocks and teleports to safe ground.
     *
     * @param world     The realm world
     * @param store     The entity store
     * @param entityRef The player's entity reference
     * @param playerId  The player's UUID (for logging)
     * @param biome     The realm biome type
     * @return true if rescue was needed and performed
     */
    private boolean rescueIfSuffocating(
            @Nonnull World world, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> entityRef, @Nonnull UUID playerId,
            @Nonnull RealmBiomeType biome) {

        TransformComponent transform = store.getComponent(entityRef, TransformComponent.getComponentType());
        if (transform == null) {
            return false;
        }

        Vector3d pos = transform.getPosition();
        int bx = (int) Math.floor(pos.x);
        int by = (int) Math.floor(pos.y);
        int bz = (int) Math.floor(pos.z);

        // Check feet and head positions — same pattern as RealmMobSpawner:898-905
        BlockType blockFeet = world.getBlockType(bx, by, bz);
        BlockType blockHead = world.getBlockType(bx, by + 1, bz);

        // Match the mob suffocation pattern: both feet AND head must be inside
        // non-empty blocks. A player with just feet clipping a decorative block
        // (flower, grass) but head clear can move normally — no rescue needed.
        // Using BlockMaterial.Empty check (not Solid) so vegetation also triggers,
        // matching RealmMobSpawner:904-905.
        boolean feetBlocked = blockFeet != null && blockFeet.getMaterial() != BlockMaterial.Empty;
        boolean headBlocked = blockHead != null && blockHead.getMaterial() != BlockMaterial.Empty;

        if (!feetBlocked || !headBlocked) {
            // At least one of feet/head is in air — player can breathe, no rescue needed
            return false;
        }

        // Player is inside blocks — find a safe position nearby
        LOGGER.atWarning().log("Player %s is suffocating at (%d, %d, %d) in realm (biome=%s, "
            + "feet=%s, head=%s) — attempting rescue",
            playerId.toString().substring(0, 8), bx, by, bz, biome.name(),
            blockFeet != null ? blockFeet.getId() : "null",
            blockHead != null ? blockHead.getId() : "null");

        int baseY = (int) RealmTemplateRegistry.getBaseYForBiome(biome);
        int maxScanY = baseY + (biome.isCeilingBiome() ? 1 : 30);

        Vector3d safePos;
        if (biome.isCeilingBiome()) {
            safePos = TerrainUtils.findSafeSpawnPosition(world, bx, bz, maxScanY, 8);
        } else {
            Set<String> terrainMaterials = biome.getTerrainMaterials();
            safePos = TerrainUtils.findSafeSpawnPosition(world, bx, bz, maxScanY, terrainMaterials, 8);
        }

        transform.teleportPosition(safePos);

        LOGGER.atWarning().log("Rescued player %s from suffocation — teleported from "
            + "(%d, %d, %d) to (%.1f, %.1f, %.1f)",
            playerId.toString().substring(0, 8), bx, by, bz,
            safePos.x, safePos.y, safePos.z);

        return true;
    }

    /**
     * Extracts player UUID from holder.
     */
    private UUID getPlayerUuid(@Nonnull Holder<EntityStore> holder) {
        try {
            UUIDComponent uuidComponent = holder.getComponent(UUIDComponent.getComponentType());
            return uuidComponent != null ? uuidComponent.getUuid() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
