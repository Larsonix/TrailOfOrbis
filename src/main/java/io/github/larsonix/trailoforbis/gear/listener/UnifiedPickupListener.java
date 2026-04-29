package io.github.larsonix.trailoforbis.gear.listener;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.SlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.Transaction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import io.github.larsonix.trailoforbis.gear.notification.PickupNotificationService;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.logging.Level;

/**
 * Detects new items in the player's inventory and sends pickup notifications.
 *
 * <p>This listener handles <b>notifications only</b> (chat messages, guide milestones).
 * Item definition sync is handled separately by {@code ImmediateItemSyncHandler} which
 * runs before this handler in the event chain.
 *
 * <h2>Detection Strategy</h2>
 * <p>Uses the {@link Transaction} data from the {@code InventoryChangeEvent} to identify
 * which items were added. This is precise — no snapshot tracking needed:
 * <ul>
 *   <li>{@link ItemStackTransaction} — wraps multiple slot transactions (pickups, addItemStack)</li>
 *   <li>{@link SlotTransaction} — single slot change (setItemStackForSlot, crafting output)</li>
 * </ul>
 *
 * <p>Only processes items that appear in <b>non-equipment</b> containers (storage, backpack).
 * Equipment containers (armor, hotbar, utility) are handled by other systems and don't
 * need pickup notifications.
 *
 * @see PickupNotificationService
 * @see io.github.larsonix.trailoforbis.gear.sync.ImmediateItemSyncHandler
 */
public final class UnifiedPickupListener {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final PickupNotificationService pickupService;

    public UnifiedPickupListener(@Nonnull PickupNotificationService pickupService) {
        this.pickupService = Objects.requireNonNull(pickupService, "pickupService cannot be null");
    }

    // =========================================================================
    // REGISTRATION
    // =========================================================================

    public void register(@Nonnull EventRegistry registry) {
        Objects.requireNonNull(registry, "registry cannot be null");
        LOGGER.at(Level.INFO).log("UnifiedPickupListener registered");
    }

    // =========================================================================
    // EVENT HANDLING
    // =========================================================================

    /**
     * Handles inventory change events by detecting new items via transaction data.
     */
    public void onInventoryChange(@Nonnull Player player, @Nonnull InventoryChangeEvent event) {
        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return;
        }

        // Skip equipment containers (notifications not needed for equip/swap)
        ItemContainer changedContainer = event.getItemContainer();
        if (isEquipmentContainer(inventory, changedContainer)) {
            return;
        }

        // Get PlayerRef
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        // Extract new items from the transaction and send notifications
        processTransaction(playerRef, event.getTransaction());
    }

    /**
     * Processes the transaction to find newly added items and trigger notifications.
     */
    private void processTransaction(@Nonnull PlayerRef playerRef, Transaction transaction) {
        if (transaction == null) {
            return;
        }

        // ItemStackTransaction wraps multiple slot transactions (e.g., addItemStack → pickup)
        if (transaction instanceof ItemStackTransaction ist) {
            for (ItemStackSlotTransaction slotTx : ist.getSlotTransactions()) {
                handleSlotAddition(playerRef, slotTx);
            }
            return;
        }

        // Single slot transaction (e.g., setItemStackForSlot → crafting, conversion)
        if (transaction instanceof SlotTransaction slotTx) {
            handleSlotAddition(playerRef, slotTx);
        }
    }

    /**
     * Checks if a slot transaction represents an item addition and handles it.
     */
    private void handleSlotAddition(@Nonnull PlayerRef playerRef, @Nonnull SlotTransaction slotTx) {
        if (!slotTx.succeeded()) {
            return;
        }

        ItemStack after = slotTx.getSlotAfter();
        if (ItemStack.isEmpty(after)) {
            return;
        }

        // Only notify for genuinely new items (slot was empty before, or item changed)
        ItemStack before = slotTx.getSlotBefore();
        if (!ItemStack.isEmpty(before) && isSameItem(before, after)) {
            return;
        }

        // Delegate to notification service (handles gear, maps, stones, gems)
        pickupService.handlePickup(playerRef, after);
        pickupService.checkInventoryFullness(playerRef);
    }

    /**
     * Checks if two item stacks represent the same logical item (same ID and quantity increase).
     * Returns true if this is just a quantity change on the same item (stacking), not a new item.
     */
    private boolean isSameItem(@Nonnull ItemStack before, @Nonnull ItemStack after) {
        if (before.getItemId() == null || after.getItemId() == null) {
            return false;
        }
        // Same item ID = stacking (quantity change), not a new item
        return before.getItemId().equals(after.getItemId());
    }

    private boolean isEquipmentContainer(@Nonnull Inventory inventory, @Nonnull ItemContainer container) {
        return container == inventory.getArmor()
                || container == inventory.getHotbar()
                || container == inventory.getUtility();
    }

    // =========================================================================
    // CLEANUP
    // =========================================================================

    public void removePlayer(@Nonnull java.util.UUID playerId) {
        // No per-player state to clean up
    }

    public void shutdown() {
        LOGGER.at(Level.INFO).log("UnifiedPickupListener shut down");
    }
}
