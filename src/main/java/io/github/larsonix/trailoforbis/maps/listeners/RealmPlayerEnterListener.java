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
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import io.github.larsonix.trailoforbis.maps.RealmsManager;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.Optional;
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
     * Handles player ready in a realm - hides vanilla UI elements.
     *
     * <p>CRITICAL: This must be in PlayerReadyEvent, NOT AddPlayerToWorldEvent.
     * During world transfer, resetManagers() is called AFTER AddPlayerToWorldEvent,
     * which resets the HUD to defaults (re-adding ObjectivePanel). PlayerReadyEvent
     * fires AFTER all reset/setup operations are complete.
     *
     * <p>This method handles TWO separate vanilla UI elements:
     * <ol>
     *   <li><b>Portal Timer UI</b>: Controlled by {@link UpdatePortal} packets. TrackerSystem
     *       sends an initial packet when {@code portalWorld.exists() == true}, regardless of
     *       whether we set {@code timeoutTimer = null}. We must send {@code UpdatePortal(null, null)}
     *       to hide it.</li>
     *   <li><b>ObjectivePanel HUD</b>: A separate HUD component hidden via
     *       {@code hideHudComponents(HudComponent.ObjectivePanel)}.</li>
     * </ol>
     */
    private void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();
        if (world == null) {
            return;
        }

        // Check if this is a realm world
        Optional<RealmInstance> realmOpt = realmsManager.getRealmByWorld(world);
        if (realmOpt.isEmpty()) {
            return;
        }

        world.execute(() -> {
            // Re-check ref validity on world thread (player may have disconnected)
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

            // Hide vanilla portal timer UI by sending UpdatePortal(null, null)
            PortalWorld portalWorld = store.getResource(PortalWorld.getResourceType());
            if (portalWorld != null && portalWorld.exists()) {
                playerRef.getPacketHandler().write(new UpdatePortal(null, null));

                if (playerId != null) {
                    portalWorld.getSeesUi().remove(playerId);
                }

                LOGGER.atInfo().log("Hid vanilla portal timer for player %s in realm",
                    playerId != null ? playerId.toString().substring(0, 8) : "unknown");
            }

            // Hide ObjectivePanel (a separate HUD element, not the portal timer)
            HudManager hudManager = player.getHudManager();
            hudManager.hideHudComponents(playerRef, HudComponent.ObjectivePanel);
            LOGGER.atFine().log("Hid vanilla ObjectivePanel for player %s in realm",
                playerId != null ? playerId.toString().substring(0, 8) : "unknown");

            // Defer combat HUD to NEXT tick — same reason as XP bar deferral.
            // MultipleHUD.setCustomHud() double-show() generates Set commands that
            // reference Append-created elements. If both packets arrive during
            // JoinWorld processing, the Set crashes. One tick of deferral ensures
            // the client's UI state is stable.
            RealmInstance realm = realmOpt.get();
            if (playerId != null && !realm.getBiome().isUtilityBiome()) {
                final UUID deferredPlayerId = playerId;
                final PlayerRef deferredRef = playerRef;
                world.execute(() -> {
                    if (!deferredRef.isValid()) return;
                    realmsManager.getHudManager().showCombatHud(deferredPlayerId, deferredRef, realm);
                });
            }
        });
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
