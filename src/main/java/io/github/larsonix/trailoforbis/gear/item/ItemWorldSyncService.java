package io.github.larsonix.trailoforbis.gear.item;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.util.GearUtils;
import io.github.larsonix.trailoforbis.gems.GemManager;
import io.github.larsonix.trailoforbis.gems.item.GemItemData;
import io.github.larsonix.trailoforbis.gems.model.GemData;
import io.github.larsonix.trailoforbis.gems.model.GemDefinition;
import io.github.larsonix.trailoforbis.gems.util.GemUtils;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.items.RealmMapUtils;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Centralized service for syncing custom item definitions to nearby players.
 *
 * <p>This service solves the pickup notification display issue where Hytale's
 * built-in {@code notifyPickupItem()} fires BEFORE our listeners can sync
 * custom item definitions to clients.
 *
 * <h2>Solution</h2>
 * <p>Sync item definitions when items SPAWN into the world, not when picked up.
 * This ensures any player who picks up the item already has the definition.
 *
 * <h2>Batch Sync (Loot Drops)</h2>
 * <p>When multiple items drop at once (mob loot), definitions are batched per-player:
 * all items are pre-parsed once, then sent in a single {@code UpdateTranslations} +
 * {@code UpdateItems} packet pair per player. This reduces packet count from
 * O(items × players × 2) to O(players × 2).
 *
 * <h2>Thread Safety</h2>
 * <p>All public methods are thread-safe using concurrent collections.
 *
 * @see ItemSyncService For player-specific item syncing
 * @see CustomItemSyncService For custom item (maps, stones) syncing
 */
public final class ItemWorldSyncService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Default range to sync items to nearby players (in blocks). */
    public static final double DEFAULT_SYNC_RANGE = 64.0;

    /** How long recently batch-synced item IDs are remembered (seconds). */
    private static final long BATCH_SYNCED_EXPIRY_SECONDS = 5;

    private final ItemSyncService itemSyncService;
    private final CustomItemSyncService customItemSyncService;
    private final ItemRegistryService itemRegistryService;
    private final double syncRange;
    private final double syncRangeSquared;

    /**
     * Cache to track which items have been synced to which players.
     * Key: player UUID, Value: Set of custom item IDs already synced for world drops
     */
    private final Map<UUID, Set<String>> worldDropSyncCache = new ConcurrentHashMap<>();

    /**
     * Item IDs that were recently batch-synced via {@link #syncItemsToNearbyPlayers}.
     * Used by {@code ItemEntitySpawnSyncSystem} to skip redundant deserialization
     * and range checks for items that were already synced to all nearby players.
     */
    private final Set<String> recentlyBatchSyncedIds = ConcurrentHashMap.newKeySet();

    /** Scheduler for cleaning up expired batch-synced IDs. */
    private final ScheduledExecutorService cleanupScheduler;

    /**
     * Creates an ItemWorldSyncService with default sync range.
     */
    public ItemWorldSyncService(
            @Nonnull ItemSyncService itemSyncService,
            @Nonnull CustomItemSyncService customItemSyncService,
            @Nullable ItemRegistryService itemRegistryService) {
        this(itemSyncService, customItemSyncService, itemRegistryService, DEFAULT_SYNC_RANGE);
    }

    /**
     * Creates an ItemWorldSyncService with custom sync range.
     */
    public ItemWorldSyncService(
            @Nonnull ItemSyncService itemSyncService,
            @Nonnull CustomItemSyncService customItemSyncService,
            @Nullable ItemRegistryService itemRegistryService,
            double syncRange) {
        this.itemSyncService = Objects.requireNonNull(itemSyncService, "itemSyncService cannot be null");
        this.customItemSyncService = Objects.requireNonNull(customItemSyncService, "customItemSyncService cannot be null");
        this.itemRegistryService = itemRegistryService; // May be null
        this.syncRange = syncRange;
        this.syncRangeSquared = syncRange * syncRange;
        this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ItemWorldSync-Cleanup");
            t.setDaemon(true);
            return t;
        });
    }

    // =========================================================================
    // PUBLIC API - BATCH SYNC
    // =========================================================================

    /**
     * Syncs all custom items in a list to ALL nearby players using batched packets.
     *
     * <p>This method should be called BEFORE spawning item entities to ensure
     * players have the definitions when the pickup notification fires.
     *
     * <p>Optimization: items are pre-parsed once (not per-player), server-side
     * registration happens once per item, and definitions are sent in batched
     * packets (2 per player instead of 2 per item per player).
     *
     * @param store The entity store for player lookups
     * @param position The position to check for nearby players
     * @param items The items to sync
     * @return Total number of items synced across all players
     */
    public int syncItemsToNearbyPlayers(
            @Nonnull Store<EntityStore> store,
            @Nonnull Vector3d position,
            @Nonnull List<ItemStack> items) {
        Objects.requireNonNull(store, "store cannot be null");
        Objects.requireNonNull(position, "position cannot be null");
        Objects.requireNonNull(items, "items cannot be null");

        if (items.isEmpty()) {
            return 0;
        }

        // Find all players within range (ONCE, not per-item)
        List<PlayerRef> nearbyPlayers = findPlayersInRange(store, position);
        if (nearbyPlayers.isEmpty()) {
            LOGGER.atFine().log("No players within %.0f blocks of (%.0f, %.0f, %.0f)",
                syncRange, position.x, position.y, position.z);
            return 0;
        }

        // ── Phase 1: Pre-parse all items ONCE ──
        // Separate gear items (batch-syncable) from non-gear custom items
        List<ParsedGearItem> gearItems = new ArrayList<>();
        List<ItemStack> nonGearCustomItems = new ArrayList<>();

        for (ItemStack item : items) {
            if (item == null || item.isEmpty()) continue;

            Optional<GearData> gearOpt = GearUtils.readGearData(item);
            if (gearOpt.isPresent() && gearOpt.get().hasInstanceId()) {
                gearItems.add(new ParsedGearItem(item, gearOpt.get()));
                continue;
            }

            // Check non-gear custom items (gems, maps) — these are rare in loot
            if (isNonGearCustomItem(item)) {
                nonGearCustomItems.add(item);
            }
        }

        if (gearItems.isEmpty() && nonGearCustomItems.isEmpty()) {
            return 0;
        }

        // ── Phase 2: Server-side registration ONCE per item ──
        // GearGenerator already registers during generation, so these are almost always no-ops.
        // But we must ensure registration before syncing to any player.
        for (ParsedGearItem parsed : gearItems) {
            ensureServerSideRegistration(parsed.itemStack, parsed.gearData);
        }

        // ── Phase 3: Batch sync to each nearby player ──
        int totalSynced = 0;

        for (PlayerRef playerRef : nearbyPlayers) {
            UUID playerId = playerRef.getUuid();

            try {
                // Gear items: collect those not yet cached for this player
                List<ItemStack> uncachedGearForPlayer = new ArrayList<>();
                List<String> uncachedGearIds = new ArrayList<>();

                for (ParsedGearItem parsed : gearItems) {
                    String itemId = parsed.gearData.getItemId();
                    if (!isAlreadySyncedForWorldDrop(playerId, itemId)) {
                        uncachedGearForPlayer.add(parsed.itemStack);
                        uncachedGearIds.add(itemId);
                    }
                }

                // Batch sync gear items (2 packets total: 1 UpdateTranslations + 1 UpdateItems)
                int gearSynced = 0;
                if (!uncachedGearForPlayer.isEmpty()) {
                    gearSynced = itemSyncService.syncAllItems(playerRef, uncachedGearForPlayer);

                    // Mark ALL items as synced in the world drop cache, regardless of whether
                    // syncAllItems sent packets (hash dedup may have skipped some, but the
                    // player already has those definitions from a prior sync)
                    for (String itemId : uncachedGearIds) {
                        markSyncedForWorldDrop(playerId, itemId);
                    }
                }

                // Non-gear custom items: sync individually (very low volume, not worth batching)
                int customSynced = 0;
                for (ItemStack customItem : nonGearCustomItems) {
                    if (syncItemToPlayer(playerRef, customItem)) {
                        customSynced++;
                    }
                }

                int playerTotal = gearSynced + customSynced;
                totalSynced += playerTotal;

                LOGGER.atFine().log("[WorldDropSync] Player %s: %d gear batched (%d sent), %d custom — %d total",
                    playerId.toString().substring(0, 8),
                    uncachedGearForPlayer.size(), gearSynced,
                    customSynced, playerTotal);

            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log(
                    "[WorldDropSync] Failed to batch-sync items to player %s",
                    playerId.toString().substring(0, 8));
            }
        }

        // ── Phase 4: Mark items as recently batch-synced ──
        // ItemEntitySpawnSyncSystem checks this to skip redundant deserialization
        Set<String> batchedIds = new HashSet<>();
        for (ParsedGearItem parsed : gearItems) {
            batchedIds.add(parsed.gearData.getItemId());
        }
        if (!batchedIds.isEmpty()) {
            recentlyBatchSyncedIds.addAll(batchedIds);
            // Schedule cleanup after expiry
            cleanupScheduler.schedule(() -> {
                recentlyBatchSyncedIds.removeAll(batchedIds);
            }, BATCH_SYNCED_EXPIRY_SECONDS, TimeUnit.SECONDS);
        }

        if (totalSynced > 0) {
            LOGGER.atFine().log("[WorldDropSync] Batch-synced %d item(s) to %d nearby player(s) at (%.0f, %.0f, %.0f)",
                totalSynced, nearbyPlayers.size(), position.x, position.y, position.z);
        }

        return totalSynced;
    }

    /**
     * Syncs items to a specific list of players using batched packets.
     *
     * <p>Use this when you already know which players should receive the sync
     * (e.g., killer + party members).
     */
    public int syncItemsToPlayers(
            @Nonnull Collection<PlayerRef> players,
            @Nonnull List<ItemStack> items) {
        Objects.requireNonNull(players, "players cannot be null");
        Objects.requireNonNull(items, "items cannot be null");

        if (players.isEmpty() || items.isEmpty()) {
            return 0;
        }

        // Pre-parse gear items once
        List<ParsedGearItem> gearItems = new ArrayList<>();
        List<ItemStack> nonGearCustomItems = new ArrayList<>();

        for (ItemStack item : items) {
            if (item == null || item.isEmpty()) continue;

            Optional<GearData> gearOpt = GearUtils.readGearData(item);
            if (gearOpt.isPresent() && gearOpt.get().hasInstanceId()) {
                gearItems.add(new ParsedGearItem(item, gearOpt.get()));
                continue;
            }
            if (isNonGearCustomItem(item)) {
                nonGearCustomItems.add(item);
            }
        }

        // Server-side registration once per item
        for (ParsedGearItem parsed : gearItems) {
            ensureServerSideRegistration(parsed.itemStack, parsed.gearData);
        }

        int totalSynced = 0;
        for (PlayerRef playerRef : players) {
            UUID playerId = playerRef.getUuid();

            // Gear batch sync
            List<ItemStack> uncached = new ArrayList<>();
            for (ParsedGearItem parsed : gearItems) {
                String itemId = parsed.gearData.getItemId();
                if (!isAlreadySyncedForWorldDrop(playerId, itemId)) {
                    uncached.add(parsed.itemStack);
                    markSyncedForWorldDrop(playerId, itemId);
                }
            }
            if (!uncached.isEmpty()) {
                totalSynced += itemSyncService.syncAllItems(playerRef, uncached);
            }

            // Non-gear individual sync
            for (ItemStack customItem : nonGearCustomItems) {
                if (syncItemToPlayer(playerRef, customItem)) {
                    totalSynced++;
                }
            }
        }
        return totalSynced;
    }

    // =========================================================================
    // PUBLIC API - SINGLE ITEM SYNC
    // =========================================================================

    /**
     * Syncs a single item to all nearby players.
     *
     * <p>Use this for items dropped from non-mob sources (player drops, containers, etc.)
     */
    public int syncItemToNearbyPlayers(
            @Nonnull Store<EntityStore> store,
            @Nonnull Vector3d position,
            @Nonnull ItemStack item) {
        Objects.requireNonNull(store, "store cannot be null");
        Objects.requireNonNull(position, "position cannot be null");
        Objects.requireNonNull(item, "item cannot be null");

        List<PlayerRef> nearbyPlayers = findPlayersInRange(store, position);
        if (nearbyPlayers.isEmpty()) {
            return 0;
        }

        int synced = 0;
        for (PlayerRef playerRef : nearbyPlayers) {
            if (syncItemToPlayer(playerRef, item)) {
                synced++;
            }
        }
        return synced;
    }

    /**
     * Syncs a single item to a specific player with idempotency.
     */
    public boolean syncItemToPlayer(@Nonnull PlayerRef playerRef, @Nonnull ItemStack item) {
        Objects.requireNonNull(playerRef, "playerRef cannot be null");
        Objects.requireNonNull(item, "item cannot be null");

        if (item.isEmpty()) {
            return false;
        }

        // Try gear first
        Optional<GearData> gearDataOpt = GearUtils.readGearData(item);
        if (gearDataOpt.isPresent()) {
            GearData gearData = gearDataOpt.get();
            if (gearData.hasInstanceId()) {
                return syncGearItem(playerRef, item, gearData);
            }
        }

        // Try gem
        Optional<GemData> gemDataOpt = GemUtils.readGemData(item);
        if (gemDataOpt.isPresent()) {
            Optional<GemManager> gemManagerOpt = ServiceRegistry.get(GemManager.class);
            if (gemManagerOpt.isPresent()
                    && gemManagerOpt.get().getRegistry() != null) {
                Optional<GemDefinition> defOpt = gemManagerOpt.get().getRegistry()
                        .getDefinition(gemDataOpt.get().gemId());
                CustomItemInstanceId instanceId = CustomItemInstanceId.fromItemId(item.getItemId());
                if (defOpt.isPresent() && instanceId != null) {
                    return syncCustomItem(playerRef,
                            new GemItemData(instanceId, defOpt.get(), gemDataOpt.get()));
                }
            }
        }

        // Stones are now native Hytale items — no custom sync needed

        // Try realm map
        Optional<RealmMapData> mapDataOpt = RealmMapUtils.readMapDataWithFallback(item);
        if (mapDataOpt.isPresent() && mapDataOpt.get().hasInstanceId()) {
            return syncCustomItem(playerRef, mapDataOpt.get());
        }

        return false;
    }

    // =========================================================================
    // BATCH SYNC SKIP SET
    // =========================================================================

    /**
     * Checks if an item was recently batch-synced to all nearby players.
     *
     * <p>Used by {@code ItemEntitySpawnSyncSystem} to skip expensive
     * deserialization and range checks for items that were already synced
     * by {@link #syncItemsToNearbyPlayers} in the same tick.
     *
     * @param itemId The item ID to check (e.g., "rpg_gear_xxx")
     * @return true if this item was recently batch-synced
     */
    public boolean wasRecentlyBatchSynced(@Nonnull String itemId) {
        return recentlyBatchSyncedIds.contains(itemId);
    }

    // =========================================================================
    // INTERNAL SYNC METHODS
    // =========================================================================

    /**
     * Syncs a gear item to a player with caching.
     */
    private boolean syncGearItem(
            @Nonnull PlayerRef playerRef,
            @Nonnull ItemStack item,
            @Nonnull GearData gearData) {

        String itemId = gearData.getItemId();
        UUID playerId = playerRef.getUuid();

        // Check world drop cache
        if (isAlreadySyncedForWorldDrop(playerId, itemId)) {
            return false;
        }

        // CRITICAL: Register item server-side FIRST
        ensureServerSideRegistration(item, gearData);

        // Sync the item definition to client
        boolean synced = itemSyncService.syncItem(playerRef, item, gearData);
        if (synced) {
            markSyncedForWorldDrop(playerId, itemId);
            LOGGER.atFine().log("World-synced gear %s to player %s", itemId, playerId);
        }
        return synced;
    }

    /**
     * Ensures a gear item is registered in Hytale's server-side asset map.
     */
    private void ensureServerSideRegistration(@Nonnull ItemStack item, @Nonnull GearData gearData) {
        if (itemRegistryService == null || !itemRegistryService.isInitialized()) {
            return;
        }

        String customId = gearData.getItemId();
        if (customId == null) {
            return;
        }

        if (itemRegistryService.isRegistered(customId)) {
            itemRegistryService.reinjectReskinResourceType(customId, gearData.rarity());
            return;
        }

        String baseItemId = GearUtils.getBaseItemId(item);
        if (baseItemId == null) {
            baseItemId = item.getItemId();
        }

        Item baseItem = Item.getAssetMap().getAsset(baseItemId);

        if (baseItem == null || baseItem == Item.UNKNOWN) {
            LOGGER.atWarning().log("Cannot register gear %s - base item %s not found",
                customId, baseItemId);
            return;
        }

        try {
            itemRegistryService.createAndRegisterSync(baseItem, customId, gearData.rarity());
            LOGGER.atInfo().log("Server-side registered gear %s (base: %s)", customId, baseItemId);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to register gear %s", customId);
        }
    }

    /**
     * Syncs a custom item (gem/map) to a player with caching.
     */
    private boolean syncCustomItem(
            @Nonnull PlayerRef playerRef,
            @Nonnull CustomItemData customData) {

        String itemId = customData.getItemId();
        UUID playerId = playerRef.getUuid();

        if (itemId == null) {
            return false;
        }

        if (isAlreadySyncedForWorldDrop(playerId, itemId)) {
            return false;
        }

        boolean synced = customItemSyncService.syncItem(playerRef, customData);
        if (synced) {
            markSyncedForWorldDrop(playerId, itemId);
            LOGGER.atFine().log("World-synced custom item %s to player %s", itemId, playerId);
        }
        return synced;
    }

    /**
     * Checks if an item is a non-gear custom item (gem or map) that needs sync.
     */
    private boolean isNonGearCustomItem(@Nonnull ItemStack item) {
        // Check gem
        Optional<GemData> gemOpt = GemUtils.readGemData(item);
        if (gemOpt.isPresent()) {
            return true;
        }

        // Check realm map
        if (RealmMapUtils.isMapAnyMethod(item)) {
            var mapOpt = RealmMapUtils.readMapDataWithFallback(item);
            return mapOpt.isPresent() && mapOpt.get().hasInstanceId();
        }

        return false;
    }

    // =========================================================================
    // PLAYER QUERIES
    // =========================================================================

    @Nonnull
    public List<PlayerRef> findPlayersInRange(
            @Nonnull Store<EntityStore> store,
            @Nonnull Vector3d position) {
        Objects.requireNonNull(store, "store cannot be null");
        Objects.requireNonNull(position, "position cannot be null");

        List<PlayerRef> result = new ArrayList<>();

        World world = getWorldFromStore(store);
        if (world == null) {
            return result;
        }

        for (PlayerRef playerRef : world.getPlayerRefs()) {
            Ref<EntityStore> entityRef = playerRef.getReference();
            if (entityRef == null || !entityRef.isValid()) {
                continue;
            }

            TransformComponent transform = store.getComponent(entityRef, TransformComponent.getComponentType());
            if (transform == null) {
                continue;
            }

            Vector3d playerPos = transform.getPosition();
            if (isWithinRange(position, playerPos)) {
                result.add(playerRef);
            }
        }

        return result;
    }

    @Nonnull
    public List<PlayerRef> findPlayersInRange(
            @Nonnull World world,
            @Nonnull Vector3d position) {
        Objects.requireNonNull(world, "world cannot be null");
        Objects.requireNonNull(position, "position cannot be null");

        List<PlayerRef> result = new ArrayList<>();
        Store<EntityStore> store = world.getEntityStore().getStore();

        for (PlayerRef playerRef : world.getPlayerRefs()) {
            Ref<EntityStore> entityRef = playerRef.getReference();
            if (entityRef == null || !entityRef.isValid()) {
                continue;
            }

            TransformComponent transform = store.getComponent(entityRef, TransformComponent.getComponentType());
            if (transform == null) {
                continue;
            }

            Vector3d playerPos = transform.getPosition();
            if (isWithinRange(position, playerPos)) {
                result.add(playerRef);
            }
        }

        return result;
    }

    private boolean isWithinRange(@Nonnull Vector3d pos1, @Nonnull Vector3d pos2) {
        double dx = pos1.x - pos2.x;
        double dy = pos1.y - pos2.y;
        double dz = pos1.z - pos2.z;
        return (dx * dx + dy * dy + dz * dz) <= syncRangeSquared;
    }

    /**
     * Cache of Store→World mappings. Avoids iterating all worlds on every call.
     * Uses identity comparison (same Store object = same world). Entries are
     * naturally bounded by the number of active worlds (typically 1-10).
     */
    private final Map<Store<EntityStore>, World> storeToWorldCache = new ConcurrentHashMap<>();

    @Nullable
    private World getWorldFromStore(@Nonnull Store<EntityStore> store) {
        // Check cache first (O(1) vs O(worlds))
        World cached = storeToWorldCache.get(store);
        if (cached != null && cached.isAlive()) {
            return cached;
        }

        Universe universe = Universe.get();
        if (universe == null) {
            return null;
        }

        for (World world : universe.getWorlds().values()) {
            if (world.getEntityStore().getStore() == store) {
                storeToWorldCache.put(store, world);
                return world;
            }
        }

        LOGGER.atWarning().log("getWorldFromStore: No world matched store (%d worlds checked)",
                universe.getWorlds().size());
        return null;
    }

    // =========================================================================
    // WORLD DROP SYNC CACHE
    // =========================================================================

    private boolean isAlreadySyncedForWorldDrop(@Nonnull UUID playerId, @Nonnull String itemId) {
        Set<String> synced = worldDropSyncCache.get(playerId);
        return synced != null && synced.contains(itemId);
    }

    private void markSyncedForWorldDrop(@Nonnull UUID playerId, @Nonnull String itemId) {
        worldDropSyncCache
            .computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet())
            .add(itemId);
    }

    public void onPlayerDisconnect(@Nonnull UUID playerId) {
        worldDropSyncCache.remove(playerId);
    }

    public void shutdown() {
        worldDropSyncCache.clear();
        recentlyBatchSyncedIds.clear();
        storeToWorldCache.clear();
        cleanupScheduler.shutdown();
        try {
            if (!cleanupScheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                cleanupScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOGGER.atInfo().log("ItemWorldSyncService shut down");
    }

    // =========================================================================
    // ACCESSORS
    // =========================================================================

    public double getSyncRange() {
        return syncRange;
    }

    @Nonnull
    public ItemSyncService getItemSyncService() {
        return itemSyncService;
    }

    @Nonnull
    public CustomItemSyncService getCustomItemSyncService() {
        return customItemSyncService;
    }

    // =========================================================================
    // INNER TYPES
    // =========================================================================

    /**
     * Pre-parsed gear item — avoids redundant GearData deserialization across players.
     */
    private record ParsedGearItem(@Nonnull ItemStack itemStack, @Nonnull GearData gearData) {}
}
