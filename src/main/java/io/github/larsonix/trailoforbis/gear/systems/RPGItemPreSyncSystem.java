package io.github.larsonix.trailoforbis.gear.systems;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.gear.GearManager;
import io.github.larsonix.trailoforbis.gear.GearService;
import io.github.larsonix.trailoforbis.gear.item.CustomItemData;
import io.github.larsonix.trailoforbis.gear.item.CustomItemSyncService;
import io.github.larsonix.trailoforbis.gear.item.ItemSyncService;
import io.github.larsonix.trailoforbis.gear.sync.ItemSyncCoordinator;
import io.github.larsonix.trailoforbis.gear.util.GearUtils;
import io.github.larsonix.trailoforbis.maps.items.RealmMapUtils;
import io.github.larsonix.trailoforbis.api.ServiceRegistry;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * ECS system that registers RPG items BEFORE inventory is sent to client.
 *
 * <p>This system solves the "reconnect glitch" where items appear as "?" after
 * disconnect/reconnect. The root cause is that Hytale's {@code PlayerAddedSystem}
 * calls {@code sendInventory()} before our plugin can send custom item definitions.
 *
 * <p><b>How it works:</b>
 * <pre>
 * world.addPlayer() called
 *     ↓
 * RPGItemPreSyncSystem.onEntityAdded()  ← Runs FIRST (Order.BEFORE)
 *     ↓
 * - Register custom items in server's asset map
 * - Send UpdateItems packet with definitions
 *     ↓
 * PlayerAddedSystem.onEntityAdded()     ← Runs SECOND
 *     ↓
 * - sendInventory() called
 * - Client receives inventory with known item IDs
 * </pre>
 *
 * <p><b>CRITICAL:</b> This system has a dependency on {@link PlayerSystems.PlayerAddedSystem}
 * with {@link Order#BEFORE}, ensuring our {@code onEntityAdded()} runs before
 * PlayerAddedSystem calls sendInventory().
 *
 * <p>This allows us to:
 * <ol>
 *   <li>Register custom items in the server's asset map (so server recognizes them)</li>
 *   <li>Send item definitions to the client (so client knows how to render them)</li>
 *   <li>THEN inventory is sent (client already has definitions)</li>
 * </ol>
 *
 * @see PlayerSystems.PlayerAddedSystem
 * @see GearManager#ensureItemsRegistered
 * @see ItemSyncService#syncAllItems
 */
public class RPGItemPreSyncSystem extends RefSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Query<EntityStore> query;

    /**
     * Creates a new RPGItemPreSyncSystem.
     *
     * <p>Uses {@link ServiceRegistry} to get the {@link GearService} when needed,
     * avoiding timing issues between system registration (setup) and service
     * initialization (start).
     */
    public RPGItemPreSyncSystem() {
        this.query = Query.and(Player.getComponentType(), PlayerRef.getComponentType());
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return this.query;
    }

    /**
     * CRITICAL: Run BEFORE PlayerAddedSystem so we can sync items before sendInventory().
     *
     * <p>PlayerAddedSystem calls sendInventory() at line 428 of PlayerSystems.java.
     * By running before it, we ensure the client has item definitions before
     * receiving the inventory packet.
     *
     * @return Set containing dependency on PlayerAddedSystem with Order.BEFORE
     */
    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(
            new SystemDependency<>(Order.BEFORE, PlayerSystems.PlayerAddedSystem.class)
        );
    }

    @Override
    public void onEntityAdded(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull AddReason reason,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        Player player = commandBuffer.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType());

        if (player == null || playerRef == null) {
            return;
        }

        // Get GearService from ServiceRegistry (allows late initialization)
        Optional<GearService> gearServiceOpt = ServiceRegistry.get(GearService.class);
        if (gearServiceOpt.isEmpty()) {
            LOGGER.atFine().log("[PreSync] GearService not available for %s", playerRef.getUuid());
            return;
        }

        GearService gearService = gearServiceOpt.get();
        if (!(gearService instanceof GearManager mgr)) {
            LOGGER.atFine().log("[PreSync] GearService is not GearManager for %s", playerRef.getUuid());
            return;
        }

        // Sync items BEFORE PlayerAddedSystem.sendInventory() is called
        syncItemsBeforeInventorySend(player, playerRef, mgr);
    }

    /**
     * Syncs RPG items before inventory is sent to client.
     *
     * <p>This method:
     * <ol>
     *   <li>Initializes player in sync service</li>
     *   <li>Collects all RPG gear items from inventory</li>
     *   <li>Registers unregistered items in server's asset map</li>
     *   <li>Sends item definitions to client via UpdateItems packet</li>
     * </ol>
     *
     * @param player The player entity
     * @param playerRef The player reference
     * @param mgr The gear manager
     */
    private void syncItemsBeforeInventorySend(Player player, PlayerRef playerRef, GearManager mgr) {
        UUID uuid = playerRef.getUuid();

        try {
            ItemSyncService syncService = mgr.getItemSyncService();
            if (syncService == null || !syncService.isEnabled()) {
                LOGGER.atFine().log("[PreSync] ItemSyncService disabled for %s", uuid);
                return;
            }

            // Initialize player in sync service (creates cache entry)
            syncService.onPlayerConnect(uuid);

            // Collect all inventory items
            if (player.getInventory() == null) {
                LOGGER.atFine().log("[PreSync] Inventory null for %s", uuid);
                return;
            }

            List<ItemStack> allItems = GearManager.collectAllInventoryItems(player.getInventory());
            if (allItems.isEmpty()) {
                LOGGER.atFine().log("[PreSync] No items for %s", uuid);
                return;
            }

            // 1. Register unregistered GEAR items in server's asset map.
            // This is server-side only (no packets) — safe during world transitions.
            int registered = mgr.ensureItemsRegistered(allItems);

            // 2. Register unregistered CUSTOM items (maps, stones) in server's asset map
            registered += mgr.ensureCustomItemsRegistered(allItems);

            // 3. Check if this is a world TRANSITION (player is suppressed by coordinator).
            // During transitions, the client is still processing JoinWorldPacket from the
            // OLD world. Sending UpdateItems packets now causes NullReferenceException
            // because the client's asset update pipeline races with world teardown.
            // The coordinator's post-PlayerReady flush handles sync after the client confirms
            // it's in the new world (ClientReady received).
            ItemSyncCoordinator coordinator = mgr.getSyncCoordinator();
            boolean isWorldTransition = coordinator != null && coordinator.isPlayerSuppressed(uuid);

            int synced = 0;
            if (!isWorldTransition) {
                // FIRST CONNECT: Safe to send packets — no old world to tear down.
                // Sync BEFORE sendInventory so client has definitions when inventory arrives.
                synced = syncService.syncAllItems(playerRef, allItems);

                CustomItemSyncService customSyncService = mgr.getCustomItemSyncService();
                if (customSyncService != null) {
                    customSyncService.onPlayerConnect(uuid);
                    List<CustomItemData> customItems = extractCustomItems(allItems);
                    if (!customItems.isEmpty()) {
                        synced += customSyncService.syncAllItems(playerRef, customItems);
                    }
                }
            } else {
                // WORLD TRANSITION: Skip packets. Initialize custom sync service only.
                CustomItemSyncService customSyncService = mgr.getCustomItemSyncService();
                if (customSyncService != null) {
                    customSyncService.onPlayerConnect(uuid);
                }
                LOGGER.atFine().log("[PreSync] Player %s: suppressed (world transition) — packets deferred to coordinator", uuid);
            }

            // Notify coordinator regardless of whether we sent packets.
            if (coordinator != null) {
                coordinator.notifyPreSyncComplete(uuid);
            }

            if (registered > 0 || synced > 0) {
                LOGGER.atFine().log("[PreSync] Player %s: registered=%d, synced=%d definitions",
                    uuid, registered, synced);
            }

            // Mark gear items as seen (updates last_seen timestamp)
            if (mgr.getItemRegistryService() != null) {
                mgr.getItemRegistryService().markItemsSeen(
                    extractCustomItemIds(allItems));
                // Also mark custom items (maps, stones) as seen
                mgr.getItemRegistryService().markItemsSeen(
                    extractCustomItemInstanceIds(allItems));
            }

        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[PreSync] Failed for %s", uuid);
        }
    }

    /**
     * Extracts custom item IDs from a list of item stacks.
     *
     * @param items List of item stacks
     * @return List of custom item IDs (only RPG gear items)
     */
    private List<String> extractCustomItemIds(List<ItemStack> items) {
        return items.stream()
            .filter(item -> item != null && !item.isEmpty())
            .map(GearUtils::readGearData)
            .filter(opt -> opt.isPresent() && opt.get().hasInstanceId())
            .map(opt -> opt.get().getItemId())
            .toList();
    }

    /**
     * Extracts CustomItemData (maps, stones) from a list of item stacks.
     *
     * <p>Uses fallback recovery methods to handle reconnect scenarios where
     * BSON metadata deserialization fails. Falls back to pattern-based detection
     * and backup metadata keys to reconstruct item data.
     *
     * @param items List of item stacks
     * @return List of CustomItemData for maps and stones with instance IDs
     */
    private List<CustomItemData> extractCustomItems(List<ItemStack> items) {
        List<CustomItemData> result = new ArrayList<>();
        for (ItemStack item : items) {
            if (item == null || item.isEmpty()) {
                continue;
            }

            // Try to extract map data (with fallback recovery from backup keys)
            var mapDataOpt = RealmMapUtils.readMapDataWithFallback(item);
            if (mapDataOpt.isPresent()) {
                var mapData = mapDataOpt.get();
                if (mapData.hasInstanceId()) {
                    result.add(mapData);
                    continue;
                }
            }

            // Stones are now native Hytale items — no custom sync needed
        }
        return result;
    }

    /**
     * Extracts custom item instance IDs (maps, stones) from a list of item stacks.
     *
     * @param items List of item stacks
     * @return List of custom item IDs (e.g., "rpg_map_xxx", "rpg_stone_xxx")
     */
    private List<String> extractCustomItemInstanceIds(List<ItemStack> items) {
        List<String> ids = new ArrayList<>();
        for (CustomItemData item : extractCustomItems(items)) {
            if (item != null && item.hasInstanceId()) {
                String itemId = item.getItemId();
                if (itemId != null) {
                    ids.add(itemId);
                }
            }
        }
        return ids;
    }

    @Override
    public void onEntityRemove(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull RemoveReason reason,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        // No cleanup needed - handled by PlayerJoinListener.onPlayerDisconnect
    }
}
