package io.github.larsonix.trailoforbis.gear.listener;

import io.github.larsonix.trailoforbis.gear.sync.ItemSyncCoordinator;
import io.github.larsonix.trailoforbis.gear.util.EquipmentSectionIds;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.logging.Level;

/**
 * Marks equipment changes as dirty in the sync coordinator.
 *
 * <p>This listener monitors changes to equipment containers (armor, hotbar, utility)
 * and marks them as dirty so the coordinator's next flush sends updated tooltips
 * (with correct requirement colors based on current stats).
 *
 * <p>Stat recalculation is handled by {@code EquipmentChangeListener} which runs
 * before this handler. This listener only handles the sync coordinator dirty-marking.
 *
 * <p>Item definition sync (preventing "?" items) is handled by
 * {@code ImmediateItemSyncHandler} which runs before all other handlers.
 */
public final class GearEquipmentListener {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @Nullable private final ItemSyncCoordinator syncCoordinator;

    /**
     * Creates a GearEquipmentListener.
     *
     * @param syncCoordinator The sync coordinator (may be null)
     */
    public GearEquipmentListener(@Nullable ItemSyncCoordinator syncCoordinator) {
        this.syncCoordinator = syncCoordinator;
    }

    // =========================================================================
    // REGISTRATION
    // =========================================================================

    /**
     * Registers the equipment change listener.
     *
     * <p>Call this from the main plugin class.
     *
     * @param registry The event registry
     */
    public void register(@Nonnull EventRegistry registry) {
        Objects.requireNonNull(registry, "registry cannot be null");

        // Note: Inventory change listener is now registered via InventoryChangeEventSystem.
        // Call addHandler() on the system instead of registerGlobal().
        LOGGER.at(Level.INFO).log("GearEquipmentListener registered");
    }

    // =========================================================================
    // EVENT HANDLING
    // =========================================================================

    /**
     * Handles inventory change events.
     *
     * <p>Marks equipment as dirty in the coordinator so that gear tooltips
     * (requirement colors, etc.) are refreshed on the next flush. Stats are
     * already recalculated synchronously by {@code EquipmentChangeListener}
     * which runs before this handler — no need to recalculate again.
     *
     * <p>Prior to this refactor, this handler debounced stat recalculation
     * for 100ms and then called markEquipmentDirty(). Combined with the
     * coordinator's own 100ms flush delay, total sync latency was ~200ms.
     * Now we mark dirty immediately — the coordinator's single 100ms
     * debounce provides all the coalescing needed.
     */
    public void onInventoryChange(@Nonnull Player player, @Nonnull InventoryChangeEvent event) {
        // Check if this is an equipment-related container
        if (!isEquipmentContainer(player, event.getItemContainer())) {
            return;
        }

        // Get PlayerRef via ECS component lookup
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        UUID playerId = playerRef.getUuid();

        LOGGER.atFine().log("Equipment change detected for player %s", playerId);

        // Mark dirty immediately — the coordinator coalesces multiple changes
        // within its flush window (100ms) into one batch sync.
        if (syncCoordinator != null) {
            syncCoordinator.markEquipmentDirty(playerId);
        }
    }

    /**
     * Checks if a container is an equipment container.
     */
    private boolean isEquipmentContainer(Player player, ItemContainer changedContainer) {
        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return false;
        }

        // Check if it's armor, hotbar, or utility
        return changedContainer == inventory.getArmor()
                || changedContainer == inventory.getHotbar()
                || changedContainer == inventory.getUtility();
    }

    // =========================================================================
    // CLEANUP
    // =========================================================================

    /**
     * Shuts down the listener.
     */
    public void shutdown() {
        LOGGER.at(Level.INFO).log("GearEquipmentListener shut down");
    }

    /**
     * No-op — no per-player state to clean up.
     *
     * @param playerId The player's UUID
     */
    public void removePlayer(@Nonnull UUID playerId) {
        // Nothing to clean up — no per-player debounce state
    }
}
