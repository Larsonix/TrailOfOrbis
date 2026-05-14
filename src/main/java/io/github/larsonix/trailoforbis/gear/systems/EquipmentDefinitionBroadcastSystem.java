package io.github.larsonix.trailoforbis.gear.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.ItemBase;
import com.hypixel.hytale.protocol.UpdateType;
import com.hypixel.hytale.protocol.packets.assets.UpdateItems;
import com.hypixel.hytale.protocol.packets.assets.UpdateTranslations;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.modules.entity.tracker.LegacyEntityTrackerSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.gear.item.ItemDefinitionBuilder;
import io.github.larsonix.trailoforbis.gear.item.ItemDisplayNameService;
import io.github.larsonix.trailoforbis.gear.item.TranslationSyncService;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.util.GearUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Broadcasts RPG gear item definitions to nearby players so armor renders
 * correctly on remote clients.
 *
 * <h2>Problem</h2>
 * <p>Hytale's {@link LegacyEntityTrackerSystems.LegacyEquipment} sends
 * {@code Equipment} packets containing item ID strings ({@code rpg_gear_*})
 * to all viewers. But {@code UpdateItems} definitions for those IDs are only
 * sent to the owning player. Remote clients can't resolve the IDs and the
 * armor renders as invisible.
 *
 * <h2>Solution</h2>
 * <p>This system runs in {@code QUEUE_UPDATE_GROUP} BEFORE
 * {@code LegacyEquipment}. It detects two cases:
 * <ol>
 *   <li><b>New viewer:</b> A player just entered view range
 *       ({@code newlyVisibleTo} is not empty) — send definitions to them.</li>
 *   <li><b>Equipment changed:</b> The observed player's equipped RPG gear
 *       changed since last tick — send updated definitions to all viewers
 *       after a cooldown period to coalesce rapid changes.</li>
 * </ol>
 *
 * <h2>Performance</h2>
 * <p>Throttled via per-player cooldown ({@link #BROADCAST_COOLDOWN_TICKS}).
 * Rapid hotbar scrolling accumulates a pending flag instead of broadcasting
 * every tick. Remote viewers receive name-only translations (no full tooltip
 * computation) — they only need the 3D model to render, not stat tooltips.
 * Translations are batched into a single packet per viewer.
 */
public final class EquipmentDefinitionBroadcastSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /**
     * Cooldown between broadcasts for the same observed player (nanoseconds).
     * 200ms coalesces rapid hotbar scrolling into a single broadcast.
     *
     * <p>Uses wall-clock time instead of tick counting because
     * {@code EntityTickingSystem.tick()} is called once PER ENTITY per game tick,
     * not once per game tick. A tick-based counter with N players advances N times
     * per game tick, making the effective cooldown {@code 200ms / N_players}.
     * Wall-clock time is independent of player count.
     */
    private static final long BROADCAST_COOLDOWN_NANOS = 200_000_000L; // 200ms

    private final ComponentType<EntityStore, EntityTrackerSystems.Visible> visibleType;
    private final ComponentType<EntityStore, PlayerRef> playerRefType;
    @Nonnull
    private final Query<EntityStore> query;

    private final ItemDefinitionBuilder definitionBuilder;
    private final TranslationSyncService translationService;
    private final ItemDisplayNameService displayNameService;

    /**
     * Per observed player: the set of rpg_gear_* IDs they had equipped last tick.
     * Used for LIGHTWEIGHT change detection — just string IDs, no GearData deserialization.
     */
    private final ConcurrentHashMap<UUID, Set<String>> lastKnownGearIds = new ConcurrentHashMap<>();

    /**
     * Per viewer: the set of rpg_gear_* IDs whose definitions have been sent.
     * Cumulative within a world session (never wiped — client keeps definitions).
     * Cleared on world join (syncOnPlayerJoin) and disconnect (onPlayerDisconnect).
     */
    private final ConcurrentHashMap<UUID, Set<String>> viewerSentCache = new ConcurrentHashMap<>();

    /**
     * Per observed player: cached built definitions for their equipped gear.
     * Avoids rebuilding unchanged item definitions on every broadcast.
     * Invalidated only when the equipped gear ID set changes.
     */
    private final ConcurrentHashMap<UUID, Map<String, ItemBase>> definitionCache = new ConcurrentHashMap<>();

    /**
     * Per observed player: cooldown state for throttling broadcasts during rapid
     * equipment changes (e.g., hotbar scrolling).
     */
    private final ConcurrentHashMap<UUID, BroadcastCooldown> cooldowns = new ConcurrentHashMap<>();


    public EquipmentDefinitionBroadcastSystem(
            @Nonnull ItemDefinitionBuilder definitionBuilder,
            @Nonnull TranslationSyncService translationService,
            @Nonnull ItemDisplayNameService displayNameService) {
        this.definitionBuilder = definitionBuilder;
        this.translationService = translationService;
        this.displayNameService = displayNameService;
        this.visibleType = EntityTrackerSystems.Visible.getComponentType();
        this.playerRefType = PlayerRef.getComponentType();
        this.query = Query.and(visibleType, playerRefType);
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    /**
     * Run in the same group as LegacyEquipment (QUEUE_UPDATE_GROUP)
     * to guarantee we're in the same tick phase.
     */
    @Nullable
    @Override
    public com.hypixel.hytale.component.SystemGroup<EntityStore> getGroup() {
        return EntityTrackerSystems.QUEUE_UPDATE_GROUP;
    }

    /**
     * Run BEFORE Hytale's LegacyEquipment system so UpdateItems arrives
     * before the Equipment packet that references the item IDs.
     */
    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(
            new SystemDependency<>(Order.BEFORE, LegacyEntityTrackerSystems.LegacyEquipment.class)
        );
    }

    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        // Only process Player entities (not NPCs/mobs).
        if (!(EntityUtils.getEntity(index, archetypeChunk) instanceof Player player)) {
            return;
        }

        EntityTrackerSystems.Visible visible = archetypeChunk.getComponent(index, visibleType);
        if (visible == null) {
            return;
        }

        PlayerRef observedPlayerRef = archetypeChunk.getComponent(index, playerRefType);
        if (observedPlayerRef == null) {
            return;
        }
        UUID observedId = observedPlayerRef.getUuid();

        // ── LIGHTWEIGHT change detection (no GearData deserialization) ──
        // Only read item ID strings from equipped slots. This runs every tick
        // for every player, so it must be as cheap as possible.
        Set<String> currentIds = collectEquippedRpgItemIds(player);

        // Detect equipment change since last tick
        Set<String> previousIds = lastKnownGearIds.put(observedId, currentIds);
        boolean gearChanged = previousIds == null || !previousIds.equals(currentIds);
        boolean hasNewViewers = !visible.newlyVisibleTo.isEmpty();

        // Handle cooldown for gear changes
        BroadcastCooldown cooldown = cooldowns.computeIfAbsent(observedId, k -> new BroadcastCooldown());

        if (gearChanged) {
            cooldown.pendingGearChange = true;
            // Invalidate cached definitions for items that changed
            Map<String, ItemBase> cached = definitionCache.get(observedId);
            if (cached != null && previousIds != null) {
                // Remove definitions for items no longer equipped
                for (String oldId : previousIds) {
                    if (!currentIds.contains(oldId)) {
                        cached.remove(oldId);
                    }
                }
            }
        }

        // Determine what to broadcast this tick (wall-clock cooldown)
        long now = System.nanoTime();
        boolean cooldownExpired = cooldown.pendingGearChange
                && (now - cooldown.lastBroadcastNanos >= BROADCAST_COOLDOWN_NANOS);

        if (!cooldownExpired && !hasNewViewers) {
            return; // Nothing to do this tick
        }

        if (currentIds.isEmpty()) {
            if (cooldownExpired) {
                cooldown.pendingGearChange = false;
                cooldown.lastBroadcastNanos = now;
            }
            return;
        }

        // Determine target viewers
        Map<Ref<EntityStore>, EntityTrackerSystems.EntityViewer> targetViewers;
        if (cooldownExpired && !visible.visibleTo.isEmpty()) {
            targetViewers = visible.visibleTo;
            cooldown.pendingGearChange = false;
            cooldown.lastBroadcastNanos = now;
        } else if (hasNewViewers) {
            targetViewers = visible.newlyVisibleTo;
        } else {
            return;
        }

        if (targetViewers.isEmpty()) {
            return;
        }

        // ── FULL collection + build (only when actually broadcasting) ──
        // Now that we know we need to send, do the expensive GearData work.
        List<EquippedGear> equippedGear = collectEquippedRpgGear(player);
        if (equippedGear.isEmpty()) {
            return;
        }

        // Build definitions using cache — only rebuild items not already cached
        Map<String, ItemBase> definitions = getOrBuildDefinitions(observedId, equippedGear);
        if (definitions.isEmpty()) {
            return;
        }

        int viewersSynced = 0;
        for (Map.Entry<Ref<EntityStore>, EntityTrackerSystems.EntityViewer> entry : targetViewers.entrySet()) {
            Ref<EntityStore> viewerRef = entry.getKey();
            if (!viewerRef.isValid()) continue;

            PlayerRef viewerPlayerRef = store.getComponent(viewerRef, playerRefType);
            if (viewerPlayerRef == null) continue;

            UUID viewerId = viewerPlayerRef.getUuid();
            if (viewerId.equals(observedId)) continue;

            // Per-viewer cumulative dedup
            Set<String> alreadySent = viewerSentCache.computeIfAbsent(
                    viewerId, k -> ConcurrentHashMap.newKeySet());

            // Filter to only items this viewer hasn't received
            Map<String, ItemBase> toSend = new HashMap<>();
            List<EquippedGear> gearToSync = new ArrayList<>();
            for (EquippedGear equipped : equippedGear) {
                ItemBase def = definitions.get(equipped.itemId);
                if (def != null && alreadySent.add(equipped.itemId)) {
                    toSend.put(equipped.itemId, def);
                    gearToSync.add(equipped);
                }
            }
            if (toSend.isEmpty()) continue;

            registerMinimalTranslationsForViewer(viewerPlayerRef, gearToSync);
            sendDefinitions(viewerPlayerRef, toSend);
            viewersSynced++;
        }

        if (viewersSynced > 0) {
            LOGGER.atFine().log("Broadcast %d gear definition(s) for %s to %d viewer(s)",
                    definitions.size(), observedId.toString().substring(0, 8), viewersSynced);
        }
    }

    // =========================================================================
    // GEAR COLLECTION
    // =========================================================================

    /**
     * Lightweight gear ID collection — reads only item ID strings, no metadata
     * deserialization. Used for per-tick change detection. At 30 TPS × 20 players,
     * this runs 600 times/sec and must be as cheap as possible.
     *
     * @return Set of rpg_gear_* item IDs currently equipped (empty if none)
     */
    @Nonnull
    private Set<String> collectEquippedRpgItemIds(@Nonnull Player player) {
        @SuppressWarnings("deprecation")
        Inventory inventory = player.getInventory();
        if (inventory == null) return Set.of();

        Set<String> ids = new HashSet<>(6);

        // Armor slots
        ItemContainer armor = inventory.getArmor();
        if (armor != null) {
            for (short i = 0; i < armor.getCapacity(); i++) {
                addIfRpgGearId(armor.getItemStack(i), ids);
            }
        }

        // Active hotbar item
        addIfRpgGearId(inventory.getActiveHotbarItem(), ids);

        // Active utility item
        addIfRpgGearId(inventory.getUtilityItem(), ids);

        return ids;
    }

    /** Adds the item's ID to the set if it looks like RPG gear (prefix check, no deserialization). */
    private static void addIfRpgGearId(@Nullable ItemStack item, @Nonnull Set<String> ids) {
        if (item == null || item.isEmpty()) return;
        String id = item.getItemId();
        if (id != null && id.startsWith("rpg_gear_")) {
            ids.add(id);
        }
    }

    /**
     * Full gear collection with GearData deserialization. Only called when
     * actually broadcasting (not on every tick).
     */
    private List<EquippedGear> collectEquippedRpgGear(@Nonnull Player player) {
        @SuppressWarnings("deprecation")
        Inventory inventory = player.getInventory();
        if (inventory == null) return List.of();

        List<EquippedGear> result = new ArrayList<>(6);

        // Armor slots
        ItemContainer armor = inventory.getArmor();
        if (armor != null) {
            for (short i = 0; i < armor.getCapacity(); i++) {
                ItemStack item = armor.getItemStack(i);
                addIfRpgGear(item, result);
            }
        }

        // Active hotbar item (right hand)
        ItemStack handItem = inventory.getActiveHotbarItem();
        addIfRpgGear(handItem, result);

        // Active utility item (left hand)
        ItemStack utilityItem = inventory.getUtilityItem();
        addIfRpgGear(utilityItem, result);

        return result;
    }

    private void addIfRpgGear(@Nullable ItemStack item, @Nonnull List<EquippedGear> result) {
        if (item == null || item.isEmpty()) return;
        Optional<GearData> gearOpt = GearUtils.readGearData(item);
        if (gearOpt.isPresent() && gearOpt.get().hasInstanceId()) {
            GearData gear = gearOpt.get();
            result.add(new EquippedGear(gear.getItemId(), item, gear));
        }
    }

    // =========================================================================
    // DEFINITION BUILDING
    // =========================================================================

    /**
     * Gets or builds definitions using the per-player cache. Only builds definitions
     * for items not already cached, avoiding redundant {@code ItemDefinitionBuilder.build()}
     * calls for unchanged armor/utility during rapid hotbar scrolling.
     */
    @Nonnull
    private Map<String, ItemBase> getOrBuildDefinitions(
            @Nonnull UUID observedId,
            @Nonnull List<EquippedGear> gear) {

        Map<String, ItemBase> cached = definitionCache.computeIfAbsent(
                observedId, k -> new ConcurrentHashMap<>());

        Map<String, ItemBase> result = new HashMap<>(gear.size());
        for (EquippedGear equipped : gear) {
            // Use cached definition if available, otherwise build fresh
            ItemBase def = cached.get(equipped.itemId);
            if (def == null) {
                def = definitionBuilder.build(equipped.itemStack, equipped.gearData);
                if (def != null) {
                    cached.put(equipped.itemId, def);
                }
            }
            if (def != null) {
                result.put(equipped.itemId, def);
            }
        }
        return result;
    }

    /**
     * Builds definitions without caching. Used by {@link #syncOnPlayerJoin}
     * where caching is not needed (one-shot operation).
     */
    @Nonnull
    private Map<String, ItemBase> buildDefinitions(@Nonnull List<EquippedGear> gear) {
        Map<String, ItemBase> defs = new HashMap<>(gear.size());
        for (EquippedGear equipped : gear) {
            ItemBase def = definitionBuilder.build(equipped.itemStack, equipped.gearData);
            if (def != null) {
                defs.put(equipped.itemId, def);
            }
        }
        return defs;
    }

    // =========================================================================
    // PACKET SENDING
    // =========================================================================

    /**
     * Registers minimal translations (name only, no tooltip) for remote viewers.
     *
     * <p>Remote viewers only see the 3D armor model — they never hover over
     * another player's equipment. Full tooltip computation via
     * {@code RichTooltipFormatter.build()} is extremely expensive (modifier range
     * calculation, ServiceRegistry lookups, Message serialization per item).
     *
     * <p>This method sends just the display name as both name AND description,
     * avoiding the entire tooltip pipeline. All translations are batched into
     * a single {@code UpdateTranslations} packet per viewer.
     */
    private void registerMinimalTranslationsForViewer(
            @Nonnull PlayerRef viewerRef,
            @Nonnull List<EquippedGear> gear) {

        Map<String, String> translations = new HashMap<>(gear.size() * 2);

        for (EquippedGear equipped : gear) {
            String compactId = equipped.gearData.instanceId().toCompactString();

            // Check if already registered for this viewer
            if (translationService.isRegistered(viewerRef.getUuid(), compactId)) {
                continue;
            }

            // Name-only translation — cheap string operation, no tooltip formatting
            String nameText = displayNameService.getGearDisplayName(equipped.gearData, equipped.itemStack);

            String nameKey = TranslationSyncService.getNameKey(compactId);
            String descKey = TranslationSyncService.getDescriptionKey(compactId);
            translations.put(nameKey, nameText);
            translations.put(descKey, nameText); // Description = name for remote viewers
        }

        if (translations.isEmpty()) {
            return;
        }

        // Single batched packet for all translations
        try {
            UpdateTranslations packet = new UpdateTranslations();
            packet.type = UpdateType.AddOrUpdate;
            packet.translations = translations;
            viewerRef.getPacketHandler().writeNoCache(packet);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log(
                    "Failed to send minimal translations to viewer %s",
                    viewerRef.getUuid().toString().substring(0, 8));
        }
    }

    private void sendDefinitions(
            @Nonnull PlayerRef viewerRef,
            @Nonnull Map<String, ItemBase> definitions) {
        if (definitions.isEmpty()) return;

        UpdateItems packet = new UpdateItems();
        packet.type = UpdateType.AddOrUpdate;
        packet.items = definitions;
        packet.updateModels = false;
        packet.updateIcons = false;

        try {
            viewerRef.getPacketHandler().writeNoCache(packet);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log(
                    "Failed to send armor definitions to viewer %s",
                    viewerRef.getUuid().toString().substring(0, 8));
        }
    }

    // =========================================================================
    // JOIN SYNC (called from event handler, not ECS tick)
    // =========================================================================

    /**
     * Syncs RPG gear definitions between the joining player and all other
     * players in the same world.
     *
     * <p>Handles the {@code sendPlayerSelf} timing gap: when a player first
     * appears to other clients, Hytale sends the Equipment packet immediately
     * (before the ECS tick where this system runs). This method is called from
     * the join event handler to ensure definitions arrive first.
     *
     * <p>Two-way sync:
     * <ol>
     *   <li>Send the joining player's RPG gear to all other players</li>
     *   <li>Send all other players' RPG gear to the joining player</li>
     * </ol>
     *
     * @param joiningPlayer The player who just joined
     * @param store The entity store
     */
    public void syncOnPlayerJoin(
            @Nonnull PlayerRef joiningPlayer,
            @Nonnull Player joiningEntity,
            @Nonnull World world) {

        UUID joiningId = joiningPlayer.getUuid();
        Store<EntityStore> store = world.getEntityStore().getStore();

        // Reset the joining player's viewer cache — they have a fresh client
        // (world transition clears all definitions on the client side).
        viewerSentCache.remove(joiningId);

        // Collect joining player's equipped RPG gear
        List<EquippedGear> joiningGear = collectEquippedRpgGear(joiningEntity);
        Map<String, ItemBase> joiningDefs = buildDefinitions(joiningGear);

        // Find all other players in the world
        List<PlayerRef> otherPlayers = new ArrayList<>();
        for (PlayerRef ref : world.getPlayerRefs()) {
            if (!ref.getUuid().equals(joiningId)) {
                otherPlayers.add(ref);
            }
        }

        if (otherPlayers.isEmpty()) {
            return;
        }

        int totalSynced = 0;

        // Direction 1: Send joining player's gear TO each other player
        if (!joiningDefs.isEmpty()) {
            for (PlayerRef otherPlayer : otherPlayers) {
                UUID otherUuid = otherPlayer.getUuid();
                registerMinimalTranslationsForViewer(otherPlayer, joiningGear);
                sendDefinitions(otherPlayer, joiningDefs);
                totalSynced++;

                // Record in viewer cache so the broadcast system doesn't re-send
                Set<String> otherSent = viewerSentCache.computeIfAbsent(
                        otherUuid, k -> ConcurrentHashMap.newKeySet());
                otherSent.addAll(joiningDefs.keySet());
            }
        }

        // Direction 2: Send each other player's gear TO the joining player
        Set<String> joiningSent = viewerSentCache.computeIfAbsent(
                joiningId, k -> ConcurrentHashMap.newKeySet());

        for (PlayerRef otherPlayer : otherPlayers) {
            Ref<EntityStore> otherRef = otherPlayer.getReference();
            if (otherRef == null || !otherRef.isValid()) continue;

            com.hypixel.hytale.server.core.entity.Entity otherEntity =
                    EntityUtils.getEntity(otherRef, store);
            if (!(otherEntity instanceof Player otherPlayerEntity)) continue;

            List<EquippedGear> otherGear = collectEquippedRpgGear(otherPlayerEntity);
            if (otherGear.isEmpty()) continue;

            Map<String, ItemBase> otherDefs = buildDefinitions(otherGear);
            if (otherDefs.isEmpty()) continue;

            registerMinimalTranslationsForViewer(joiningPlayer, otherGear);
            sendDefinitions(joiningPlayer, otherDefs);
            totalSynced++;

            // Record in joining player's viewer cache
            joiningSent.addAll(otherDefs.keySet());
        }

        if (totalSynced > 0) {
            LOGGER.atInfo().log("Join sync: exchanged gear definitions for %s with %d other player(s)",
                    joiningId.toString().substring(0, 8), otherPlayers.size());
        }
    }

    // =========================================================================
    // LIFECYCLE
    // =========================================================================

    /**
     * Clean up tracking state for a disconnecting player.
     * Call from GearManager shutdown or disconnect handler.
     */
    public void onPlayerDisconnect(@Nonnull UUID playerId) {
        lastKnownGearIds.remove(playerId);
        viewerSentCache.remove(playerId);
        definitionCache.remove(playerId);
        cooldowns.remove(playerId);
    }

    /**
     * Clears all tracking state. Called on plugin shutdown.
     */
    public void shutdown() {
        lastKnownGearIds.clear();
        viewerSentCache.clear();
        definitionCache.clear();
        cooldowns.clear();
    }

    // =========================================================================
    // INNER TYPES
    // =========================================================================

    private record EquippedGear(
            @Nonnull String itemId,
            @Nonnull ItemStack itemStack,
            @Nonnull GearData gearData) {}

    /**
     * Mutable cooldown state per observed player. Tracks whether a gear change
     * is pending broadcast and when the last broadcast fired (wall-clock nanos).
     */
    private static final class BroadcastCooldown {
        volatile boolean pendingGearChange = false;
        volatile long lastBroadcastNanos = 0;
    }
}
