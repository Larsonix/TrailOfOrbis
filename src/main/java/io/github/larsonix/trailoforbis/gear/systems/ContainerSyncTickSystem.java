package io.github.larsonix.trailoforbis.gear.systems;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ItemContainerWindow;
import com.hypixel.hytale.server.core.entity.entities.player.windows.Window;
import com.hypixel.hytale.server.core.entity.entities.player.windows.WindowManager;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.gear.GearManager;
import io.github.larsonix.trailoforbis.gear.GearService;
import io.github.larsonix.trailoforbis.gear.item.CustomItemData;
import io.github.larsonix.trailoforbis.gear.item.CustomItemSyncService;
import io.github.larsonix.trailoforbis.gear.item.ItemSyncService;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.items.RealmMapUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ECS tick system that syncs custom RPG item definitions when players open containers.
 *
 * <p>This system ensures that custom items (gear, maps, gems) stored in external
 * containers (chests, storage blocks) display correctly for ALL players viewing
 * the container, including when items are added by other players.
 *
 * <p><b>How it works:</b>
 * <pre>
 * Player opens a chest/container
 *     ↓
 * WindowManager adds ItemContainerWindow
 *     ↓
 * ContainerSyncTickSystem.tick() detects new window
 *     ↓
 * Scans container for RPG items (rpg_gear_*, rpg_map_*, rpg_gem_*)
 *     ↓
 * Registers + syncs definitions for all found items
 *     ↓
 * Tracks which item IDs have been synced per window
 *     ↓
 * On subsequent ticks, detects NEW items added by other players
 *     ↓
 * Syncs only the new definitions + forces window update
 * </pre>
 *
 * <p><b>Thread Safety:</b> Uses ConcurrentHashMap for tracking synced windows/items.
 *
 * @see RPGItemPreSyncSystem
 * @see ItemSyncService
 * @see CustomItemSyncService
 */
public class ContainerSyncTickSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Prefix shared by all RPG custom item IDs. */
    private static final String RPG_ITEM_PREFIX = "rpg_";

    /**
     * Tracks which RPG item IDs have been synced per player per window.
     * Map of player UUID → (window ID → set of synced RPG item IDs).
     *
     * <p>This replaces the old per-window boolean tracking, allowing us to detect
     * when new items are added to an already-open container by another player.
     */
    private final Map<UUID, Map<Integer, Set<String>>> syncedWindows = new ConcurrentHashMap<>();

    /** Component types for efficient query - cached for performance */
    private final ComponentType<EntityStore, Player> playerType;
    private final ComponentType<EntityStore, PlayerRef> playerRefType;

    /** Archetype query matching only player entities */
    private Archetype<EntityStore> playerQuery = null;

    /** Cached service reference — resolved once on first tick, never changes */
    @Nullable private GearManager cachedGearManager;

    public ContainerSyncTickSystem() {
        this.playerType = Player.getComponentType();
        this.playerRefType = PlayerRef.getComponentType();
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        if (playerQuery == null) {
            playerQuery = Archetype.of(playerType, playerRefType);
        }
        return playerQuery;
    }

    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        Ref<EntityStore> entityRef = archetypeChunk.getReferenceTo(index);
        if (entityRef == null || !entityRef.isValid()) {
            return;
        }

        Player player = store.getComponent(entityRef, playerType);
        PlayerRef playerRef = store.getComponent(entityRef, playerRefType);

        if (player == null || playerRef == null) {
            return;
        }

        if (cachedGearManager == null) {
            GearService svc = ServiceRegistry.get(GearService.class).orElse(null);
            if (!(svc instanceof GearManager gm)) {
                return;
            }
            cachedGearManager = gm;
        }

        WindowManager windowManager = player.getWindowManager();
        if (windowManager == null) {
            return;
        }

        UUID playerId = playerRef.getUuid();
        Map<Integer, Set<String>> windowSyncMap = syncedWindows.computeIfAbsent(
                playerId, k -> new ConcurrentHashMap<>());

        List<Window> windows = windowManager.getWindows();
        for (Window window : windows) {
            if (!(window instanceof ItemContainerWindow icw)) {
                continue;
            }

            int windowId = window.getId();
            boolean isNewWindow = !windowSyncMap.containsKey(windowId);
            Set<String> syncedItemIds = windowSyncMap.computeIfAbsent(
                    windowId, k -> ConcurrentHashMap.newKeySet());

            // Scan container for RPG items not yet synced for this player+window
            ItemContainer container = icw.getItemContainer();
            List<ItemStack> itemsToSync = collectUnsyncedRpgItems(container, syncedItemIds);

            if (!itemsToSync.isEmpty()) {
                boolean synced = syncContainerItems(playerRef, cachedGearManager, itemsToSync);

                if (synced) {
                    // Force container update so client re-renders items with new definitions
                    windowManager.updateWindow(window);
                }

                LOGGER.atFine().log("[ContainerSync] Synced %d new item(s) in window %d for player %s",
                        itemsToSync.size(), windowId, playerId);
            }

            // Register close handler for new windows to clean up tracking
            if (isNewWindow) {
                final int wId = windowId;
                window.registerCloseEvent(event -> windowSyncMap.remove(wId));
            }
        }
    }

    /**
     * Scans a container and collects RPG items whose IDs have not yet been synced.
     *
     * <p>Uses only {@link ItemStack#getItemId()} (a field access) and a string
     * prefix check — no BSON parsing until we know we need to sync.
     *
     * @param container The container to scan
     * @param syncedItemIds Set of already-synced RPG item IDs (modified: new IDs are added)
     * @return List of item stacks that need syncing (may be empty)
     */
    private List<ItemStack> collectUnsyncedRpgItems(
            @Nonnull ItemContainer container,
            @Nonnull Set<String> syncedItemIds) {

        List<ItemStack> result = new ArrayList<>();

        for (short i = 0; i < container.getCapacity(); i++) {
            ItemStack item = container.getItemStack(i);
            if (item == null || item.isEmpty()) {
                continue;
            }

            String itemId = item.getItemId();
            // Quick prefix check — all our custom items start with "rpg_"
            if (itemId.startsWith(RPG_ITEM_PREFIX) && syncedItemIds.add(itemId)) {
                // add() returns true only if the ID was new (not already in the set)
                result.add(item);
            }
        }

        return result;
    }

    /**
     * Syncs RPG item definitions to a player for a specific set of items.
     *
     * <p>Handles three item types:
     * <ul>
     *   <li>Gear items (swords, armor, etc.) — via {@link ItemSyncService}</li>
     *   <li>Realm maps — via {@link CustomItemSyncService}</li>
     *   <li>Gems — via {@link CustomItemSyncService}</li>
     * </ul>
     *
     * @param playerRef The player to sync items to
     * @param gearManager The gear manager instance
     * @param items The specific items to sync
     * @return true if any items were registered or synced
     */
    private boolean syncContainerItems(
            @Nonnull PlayerRef playerRef,
            @Nonnull GearManager gearManager,
            @Nonnull List<ItemStack> items) {

        if (items.isEmpty()) {
            return false;
        }

        UUID playerId = playerRef.getUuid();

        try {
            // 1. Register unregistered gear items in server's asset map
            int registered = gearManager.ensureItemsRegistered(items);

            // 2. Register unregistered custom items (maps, gems) in server's asset map
            registered += gearManager.ensureCustomItemsRegistered(items);

            // 3. Sync gear item definitions to client
            ItemSyncService itemSyncService = gearManager.getItemSyncService();
            int synced = 0;
            if (itemSyncService != null && itemSyncService.isEnabled()) {
                synced = itemSyncService.syncAllItems(playerRef, items);
            }

            // 4. Sync custom item (maps, gems) definitions to client
            CustomItemSyncService customItemSyncService = gearManager.getCustomItemSyncService();
            if (customItemSyncService != null) {
                List<CustomItemData> customItems = extractCustomItems(items);
                if (!customItems.isEmpty()) {
                    synced += customItemSyncService.syncAllItems(playerRef, customItems);
                }
            }

            if (registered > 0 || synced > 0) {
                LOGGER.atFine().log("[ContainerSync] Player %s: registered=%d, synced=%d items from container",
                        playerId, registered, synced);
            }

            return (registered > 0 || synced > 0);

        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log(
                    "[ContainerSync] Failed to sync container items for player %s", playerId);
            return false;
        }
    }

    /**
     * Extracts CustomItemData from item stacks (maps and gems).
     */
    private List<CustomItemData> extractCustomItems(@Nonnull Collection<ItemStack> items) {
        List<CustomItemData> result = new ArrayList<>();

        for (ItemStack item : items) {
            if (item == null || item.isEmpty()) {
                continue;
            }

            Optional<RealmMapData> mapOpt = RealmMapUtils.readMapDataWithFallback(item);
            if (mapOpt.isPresent() && mapOpt.get().hasInstanceId()) {
                result.add(mapOpt.get());
            }
        }

        return result;
    }

    /**
     * Cleans up tracking for a player when they disconnect.
     *
     * @param playerId The player's UUID
     */
    public void cleanupPlayer(@Nonnull UUID playerId) {
        syncedWindows.remove(playerId);
        LOGGER.atFine().log("[ContainerSync] Cleaned up tracking for player %s", playerId);
    }
}
