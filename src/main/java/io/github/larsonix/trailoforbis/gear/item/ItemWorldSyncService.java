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
 * <h2>Usage</h2>
 * <pre>{@code
 * // When dropping loot
 * List<ItemStack> drops = generateDrops();
 * itemWorldSyncService.syncItemsToNearbyPlayers(store, position, drops);
 * spawnItemEntities(drops);
 *
 * // When an item entity spawns
 * itemWorldSyncService.syncItemToNearbyPlayers(store, position, itemStack);
 * }</pre>
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

    private final ItemSyncService itemSyncService;
    private final CustomItemSyncService customItemSyncService;
    private final ItemRegistryService itemRegistryService;
    private final double syncRange;
    private final double syncRangeSquared;

    /**
     * Cache to track which items have been synced to which players.
     * Key: player UUID, Value: Set of custom item IDs already synced for world drops
     *
     * <p>This prevents redundant syncs when items are in range of multiple sync calls.
     */
    private final Map<UUID, Set<String>> worldDropSyncCache = new ConcurrentHashMap<>();

    /**
     * Creates an ItemWorldSyncService with default sync range.
     *
     * @param itemSyncService The gear item sync service
     * @param customItemSyncService The custom item sync service
     * @param itemRegistryService The item registry service for server-side registration
     */
    public ItemWorldSyncService(
            @Nonnull ItemSyncService itemSyncService,
            @Nonnull CustomItemSyncService customItemSyncService,
            @Nullable ItemRegistryService itemRegistryService) {
        this(itemSyncService, customItemSyncService, itemRegistryService, DEFAULT_SYNC_RANGE);
    }

    /**
     * Creates an ItemWorldSyncService with custom sync range.
     *
     * @param itemSyncService The gear item sync service
     * @param customItemSyncService The custom item sync service
     * @param itemRegistryService The item registry service for server-side registration
     * @param syncRange The range in blocks to sync items to players
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
    }

    // =========================================================================
    // PUBLIC API - BATCH SYNC
    // =========================================================================

    /**
     * Syncs all custom items in a list to ALL nearby players.
     *
     * <p>This method should be called BEFORE spawning item entities to ensure
     * players have the definitions when the pickup notification fires.
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

        // Find all players within range
        List<PlayerRef> nearbyPlayers = findPlayersInRange(store, position);
        if (nearbyPlayers.isEmpty()) {
            LOGGER.atFine().log("No players within %.0f blocks of (%.0f, %.0f, %.0f)",
                syncRange, position.x, position.y, position.z);
            return 0;
        }

        int totalSynced = 0;

        // Sync each item to each nearby player
        for (PlayerRef playerRef : nearbyPlayers) {
            for (ItemStack item : items) {
                if (syncItemToPlayer(playerRef, item)) {
                    totalSynced++;
                }
            }
        }

        if (totalSynced > 0) {
            LOGGER.atInfo().log("Pre-synced %d item(s) to %d nearby player(s) at (%.0f, %.0f, %.0f)",
                totalSynced, nearbyPlayers.size(), position.x, position.y, position.z);
        }

        return totalSynced;
    }

    /**
     * Syncs items to a specific list of players.
     *
     * <p>Use this when you already know which players should receive the sync
     * (e.g., killer + party members).
     *
     * @param players The players to sync to
     * @param items The items to sync
     * @return Total number of items synced
     */
    public int syncItemsToPlayers(
            @Nonnull Collection<PlayerRef> players,
            @Nonnull List<ItemStack> items) {
        Objects.requireNonNull(players, "players cannot be null");
        Objects.requireNonNull(items, "items cannot be null");

        if (players.isEmpty() || items.isEmpty()) {
            return 0;
        }

        int totalSynced = 0;
        for (PlayerRef playerRef : players) {
            for (ItemStack item : items) {
                if (syncItemToPlayer(playerRef, item)) {
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
     *
     * @param store The entity store for player lookups
     * @param position The position to check for nearby players
     * @param item The item to sync
     * @return Number of players the item was synced to
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
     *
     * <p>This method checks if the item was already synced via world drop
     * cache to avoid redundant syncs.
     *
     * @param playerRef The player to sync to
     * @param item The item to sync
     * @return true if the item was synced, false if skipped (already synced or not custom)
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
    // INTERNAL SYNC METHODS
    // =========================================================================

    /**
     * Syncs a gear item to a player with caching.
     *
     * <p>CRITICAL: This method registers the item server-side BEFORE syncing
     * to the client. This ensures {@code itemStack.getItem()} returns our
     * custom Item when {@code notifyPickupItem()} fires.
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
        // This ensures Item.getAssetMap().getAsset(itemId) returns our custom Item
        // when notifyPickupItem() calls itemStack.getItem()
        ensureServerSideRegistration(item, gearData);

        // Sync the item definition to client
        boolean synced = itemSyncService.syncItem(playerRef, item, gearData);
        if (synced) {
            markSyncedForWorldDrop(playerId, itemId);
            LOGGER.atInfo().log("World-synced gear %s to player %s", itemId, playerId);
        }
        return synced;
    }

    /**
     * Ensures a gear item is registered in Hytale's server-side asset map.
     *
     * <p>This is CRITICAL for pickup notifications. When a player picks up an item,
     * Hytale calls {@code itemStack.getItem().getTranslationKey()}. If our custom
     * item isn't registered, it returns {@code Item.UNKNOWN} and the notification
     * shows the wrong name/color.
     *
     * @param item The ItemStack containing the gear (used to get base item ID from metadata)
     * @param gearData The gear data (used to get custom item ID)
     */
    private void ensureServerSideRegistration(@Nonnull ItemStack item, @Nonnull GearData gearData) {
        if (itemRegistryService == null || !itemRegistryService.isInitialized()) {
            return;
        }

        String customId = gearData.getItemId();
        if (customId == null) {
            return;
        }

        // Skip if already registered
        if (itemRegistryService.isRegistered(customId)) {
            return;
        }

        // Get the base item ID from ItemStack metadata
        String baseItemId = GearUtils.getBaseItemId(item);
        if (baseItemId == null) {
            // Fallback to current itemId (shouldn't happen for properly created gear)
            baseItemId = item.getItemId();
        }

        Item baseItem = Item.getAssetMap().getAsset(baseItemId);

        if (baseItem == null || baseItem == Item.UNKNOWN) {
            LOGGER.atWarning().log("Cannot register gear %s - base item %s not found",
                customId, baseItemId);
            return;
        }

        // Register in server-side asset map
        try {
            itemRegistryService.createAndRegisterSync(baseItem, customId);
            LOGGER.atInfo().log("Server-side registered gear %s (base: %s)", customId, baseItemId);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to register gear %s", customId);
        }
    }

    /**
     * Syncs a custom item (stone/map) to a player with caching.
     */
    private boolean syncCustomItem(
            @Nonnull PlayerRef playerRef,
            @Nonnull CustomItemData customData) {

        String itemId = customData.getItemId();
        UUID playerId = playerRef.getUuid();

        if (itemId == null) {
            return false;
        }

        // Check world drop cache
        if (isAlreadySyncedForWorldDrop(playerId, itemId)) {
            return false;
        }

        // Sync the item
        boolean synced = customItemSyncService.syncItem(playerRef, customData);
        if (synced) {
            markSyncedForWorldDrop(playerId, itemId);
            LOGGER.atFine().log("World-synced custom item %s to player %s", itemId, playerId);
        }
        return synced;
    }

    // =========================================================================
    // PLAYER QUERIES
    // =========================================================================

    /**
     * Finds all players within sync range of a position.
     *
     * @param store The entity store
     * @param position The center position
     * @return List of player refs within range
     */
    @Nonnull
    public List<PlayerRef> findPlayersInRange(
            @Nonnull Store<EntityStore> store,
            @Nonnull Vector3d position) {
        Objects.requireNonNull(store, "store cannot be null");
        Objects.requireNonNull(position, "position cannot be null");

        List<PlayerRef> result = new ArrayList<>();

        // Get the world from the store
        World world = getWorldFromStore(store);
        if (world == null) {
            return result;
        }

        // Iterate through all players in the world
        for (PlayerRef playerRef : world.getPlayerRefs()) {
            Ref<EntityStore> entityRef = playerRef.getReference();
            if (entityRef == null || !entityRef.isValid()) {
                continue;
            }

            // Get player position
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

    /**
     * Finds all players within sync range of a position (using World directly).
     *
     * @param world The world to search
     * @param position The center position
     * @return List of player refs within range
     */
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

    /**
     * Checks if two positions are within sync range.
     */
    private boolean isWithinRange(@Nonnull Vector3d pos1, @Nonnull Vector3d pos2) {
        double dx = pos1.x - pos2.x;
        double dy = pos1.y - pos2.y;
        double dz = pos1.z - pos2.z;
        return (dx * dx + dy * dy + dz * dz) <= syncRangeSquared;
    }

    /**
     * Gets the World from an EntityStore.
     */
    @Nullable
    private World getWorldFromStore(@Nonnull Store<EntityStore> store) {
        // Try to get from Universe
        Universe universe = Universe.get();
        if (universe == null) {
            return null;
        }

        // Find a world that uses this store
        for (World world : universe.getWorlds().values()) {
            if (world.getEntityStore().getStore() == store) {
                return world;
            }
        }

        // Fallback: just return the first world (most common case is single world)
        LOGGER.atWarning().log("getWorldFromStore: No world matched store, falling back to first world (%d worlds checked)",
                universe.getWorlds().size());
        Collection<World> worlds = universe.getWorlds().values();
        return worlds.isEmpty() ? null : worlds.iterator().next();
    }

    // =========================================================================
    // WORLD DROP SYNC CACHE
    // =========================================================================

    /**
     * Checks if an item was already synced for a world drop to a player.
     */
    private boolean isAlreadySyncedForWorldDrop(@Nonnull UUID playerId, @Nonnull String itemId) {
        Set<String> synced = worldDropSyncCache.get(playerId);
        return synced != null && synced.contains(itemId);
    }

    /**
     * Marks an item as synced for a world drop to a player.
     */
    private void markSyncedForWorldDrop(@Nonnull UUID playerId, @Nonnull String itemId) {
        worldDropSyncCache
            .computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet())
            .add(itemId);
    }

    /**
     * Clears the world drop sync cache for a player.
     *
     * <p>Call when a player disconnects.
     *
     * @param playerId The player's UUID
     */
    public void onPlayerDisconnect(@Nonnull UUID playerId) {
        worldDropSyncCache.remove(playerId);
    }

    /**
     * Clears all caches.
     *
     * <p>Call during shutdown.
     */
    public void shutdown() {
        worldDropSyncCache.clear();
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
}
