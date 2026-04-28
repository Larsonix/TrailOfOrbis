package io.github.larsonix.trailoforbis.gear.listener;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.notification.PickupNotificationService;
import io.github.larsonix.trailoforbis.gear.util.GearUtils;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.items.RealmMapUtils;
import io.github.larsonix.trailoforbis.stones.StoneType;
import io.github.larsonix.trailoforbis.stones.StoneUtils;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Unified listener for ALL item pickups (gear, maps, stones).
 *
 * <p>This listener replaces the fragmented approach of having separate listeners
 * for different item types. It delegates all pickup handling to the centralized
 * {@link PickupNotificationService} which handles:
 * <ul>
 *   <li>Syncing item definitions to the player client</li>
 *   <li>Sending chat notifications for notable items</li>
 * </ul>
 *
 * <h2>Architecture</h2>
 * <pre>
 * Player picks up item
 *         |
 *         v
 * LivingEntityInventoryChangeEvent
 *         |
 *         v
 * UnifiedPickupListener.onInventoryChange()
 *         |
 *         v
 * PickupNotificationService.handlePickup()
 *         |
 *     +---+---+
 *     |   |   |
 *     v   v   v
 *  Gear Map Stone
 * </pre>
 *
 * <h2>Item Type Handling</h2>
 * <table>
 * <tr><th>Item Type</th><th>Sync</th><th>Chat Notification</th></tr>
 * <tr><td>Gear</td><td>Yes</td><td>Uncommon+ rarity</td></tr>
 * <tr><td>Map</td><td>Yes</td><td>Always</td></tr>
 * <tr><td>Stone</td><td>Yes</td><td>Never (avoid spam)</td></tr>
 * </table>
 *
 * <h2>Thread Safety</h2>
 * <p>All public methods are thread-safe using concurrent collections.
 *
 * @see PickupNotificationService
 */
public final class UnifiedPickupListener {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final PickupNotificationService pickupService;

    /**
     * Tracks inventory state to detect NEW items.
     * <p>Map: playerId -> Set of item hashes currently in backpack
     */
    private final Map<UUID, Set<Integer>> playerInventoryState = new ConcurrentHashMap<>();

    /**
     * Creates a UnifiedPickupListener.
     *
     * @param pickupService The unified pickup notification service
     */
    public UnifiedPickupListener(@Nonnull PickupNotificationService pickupService) {
        this.pickupService = Objects.requireNonNull(pickupService, "pickupService cannot be null");
    }

    // =========================================================================
    // REGISTRATION
    // =========================================================================

    /**
     * Registers the pickup listener.
     *
     * @param registry The event registry
     */
    public void register(@Nonnull EventRegistry registry) {
        Objects.requireNonNull(registry, "registry cannot be null");

        // Note: Inventory change listener is now registered via InventoryChangeEventSystem.
        // Call addHandler() on the system instead of registerGlobal().
        LOGGER.at(Level.INFO).log("UnifiedPickupListener registered");
    }

    // =========================================================================
    // EVENT HANDLING
    // =========================================================================

    /**
     * Handles inventory change events.
     */
    public void onInventoryChange(@Nonnull Player player, @Nonnull InventoryChangeEvent event) {
        // Only care about non-equipment containers (backpack, main inventory)
        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return;
        }

        ItemContainer changedContainer = event.getItemContainer();

        // Skip equipment containers (handled by GearEquipmentListener)
        if (isEquipmentContainer(inventory, changedContainer)) {
            return;
        }

        // Get PlayerRef via ECS component lookup (future-proof pattern)
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        // Check for new items and delegate to unified service
        checkForNewItems(playerRef, changedContainer);
    }

    /**
     * Checks if a container is an equipment container.
     */
    private boolean isEquipmentContainer(@Nonnull Inventory inventory, @Nonnull ItemContainer container) {
        return container == inventory.getArmor()
                || container == inventory.getHotbar()
                || container == inventory.getUtility();
    }

    /**
     * Checks the container for new items and delegates to the pickup service.
     */
    private void checkForNewItems(@Nonnull PlayerRef playerRef, @Nonnull ItemContainer container) {
        UUID playerId = playerRef.getUuid();
        Set<Integer> currentState = playerInventoryState.computeIfAbsent(playerId, k -> new HashSet<>());
        Set<Integer> newState = new HashSet<>();

        // Scan container for items
        for (short i = 0; i < container.getCapacity(); i++) {
            ItemStack item = container.getItemStack(i);
            if (item == null || item.isEmpty()) {
                continue;
            }

            // Generate a hash for this item to track presence
            int itemHash = computeItemHash(item);
            newState.add(itemHash);

            // Check if this is a NEW item (not in previous state)
            if (!currentState.contains(itemHash)) {
                // Delegate to unified pickup service
                // It handles all item types: gear, maps, stones
                pickupService.handlePickup(playerRef, item);
                pickupService.checkInventoryFullness(playerRef);

                LOGGER.atFine().log("Detected new item pickup for player %s, delegated to PickupNotificationService",
                        playerId);
            }
        }

        // Update state
        playerInventoryState.put(playerId, newState);
    }

    /**
     * Computes a hash for an item to track its presence.
     *
     * <p>Uses item ID, quantity, and key RPG properties to create a unique hash.
     * This ensures we can detect when the "same" item appears vs when it's truly new.
     */
    private int computeItemHash(@Nonnull ItemStack item) {
        int hash = 17;
        hash = 31 * hash + (item.getItemId() != null ? item.getItemId().hashCode() : 0);
        hash = 31 * hash + item.getQuantity();

        // Include RPG gear data in hash if present
        Optional<GearData> gearData = GearUtils.readGearData(item);
        if (gearData.isPresent()) {
            GearData data = gearData.get();
            hash = 31 * hash + data.level();
            hash = 31 * hash + data.rarity().ordinal();
            hash = 31 * hash + data.quality();
            hash = 31 * hash + data.modifierCount();
            // Include instance ID if present for uniqueness
            if (data.hasInstanceId()) {
                hash = 31 * hash + data.instanceId().hashCode();
            }
        }

        // Include realm map data in hash if present (use fallback for consistency during world transitions)
        Optional<RealmMapData> mapData = RealmMapUtils.readMapDataWithFallback(item);
        if (mapData.isPresent()) {
            RealmMapData data = mapData.get();
            hash = 31 * hash + data.level();
            hash = 31 * hash + data.rarity().ordinal();
            if (data.hasInstanceId()) {
                hash = 31 * hash + data.getItemId().hashCode();
            }
        }

        // Include stone type in hash if present (stones are native items, just use type ordinal)
        Optional<StoneType> stoneType = StoneUtils.readStoneType(item);
        if (stoneType.isPresent()) {
            hash = 31 * hash + stoneType.get().ordinal();
        }

        return hash;
    }

    // =========================================================================
    // CLEANUP
    // =========================================================================

    /**
     * Clears tracked state for a player.
     *
     * <p>Call when a player disconnects.
     *
     * @param playerId The player's UUID
     */
    public void removePlayer(@Nonnull UUID playerId) {
        playerInventoryState.remove(playerId);
    }

    /**
     * Clears all tracked state.
     *
     * <p>Call during shutdown.
     */
    public void shutdown() {
        playerInventoryState.clear();
        LOGGER.at(Level.INFO).log("UnifiedPickupListener shut down");
    }
}
