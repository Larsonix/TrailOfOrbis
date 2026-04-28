package io.github.larsonix.trailoforbis.maps.reward;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import io.github.larsonix.trailoforbis.gear.item.CustomItemSyncService;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.items.RealmMapUtils;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * Distributes victory rewards to players' inventories.
 *
 * <p>Handles:
 * <ul>
 *   <li>Adding items to player inventory</li>
 *   <li>Custom item sync (maps, stones)</li>
 *   <li>Overflow handling (items that don't fit)</li>
 *   <li>Victory summary messages</li>
 * </ul>
 *
 * <h2>Custom Item Sync</h2>
 * <p>Maps and stones are custom items that need to be synced to the client
 * before they can be displayed properly. This class handles that automatically.
 *
 * @see VictoryRewardGenerator
 * @see CustomItemSyncService
 */
public final class VictoryRewardDistributor {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // ═══════════════════════════════════════════════════════════════════
    // FIELDS
    // ═══════════════════════════════════════════════════════════════════

    private final CustomItemSyncService customItemSyncService;

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTORS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a VictoryRewardDistributor.
     *
     * @param customItemSyncService The service for syncing custom items (may be null)
     */
    public VictoryRewardDistributor(@Nullable CustomItemSyncService customItemSyncService) {
        this.customItemSyncService = customItemSyncService;
    }

    // ═══════════════════════════════════════════════════════════════════
    // DISTRIBUTION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Result of distributing rewards to a player.
     *
     * @param delivered Number of items successfully added to inventory
     * @param overflow Number of items that couldn't fit (dropped or queued)
     */
    public record DistributionResult(int delivered, int overflow) {
        public int total() {
            return delivered + overflow;
        }

        public boolean hasOverflow() {
            return overflow > 0;
        }
    }

    /**
     * Distributes victory rewards to a player.
     *
     * <p>Steps:
     * <ol>
     *   <li>Sync custom item definitions to player (maps, stones)</li>
     *   <li>Add items to player inventory</li>
     *   <li>Handle overflow items</li>
     *   <li>Send summary message</li>
     * </ol>
     *
     * @param playerRef The player to distribute rewards to
     * @param rewards The generated rewards
     * @return Distribution result with counts
     */
    @Nonnull
    public DistributionResult distribute(
            @Nonnull PlayerRef playerRef,
            @Nonnull VictoryRewardGenerator.VictoryRewards rewards) {
        Objects.requireNonNull(playerRef, "playerRef cannot be null");
        Objects.requireNonNull(rewards, "rewards cannot be null");

        if (!rewards.hasRewards()) {
            LOGGER.atFine().log("No rewards to distribute to player %s",
                playerRef.getUuid().toString().substring(0, 8));
            return new DistributionResult(0, 0);
        }

        UUID playerId = playerRef.getUuid();
        List<ItemStack> allItems = new ArrayList<>();

        // Step 1: Sync custom items (maps only — stones are native items)
        List<ItemStack> syncedMaps = syncAndCollectMaps(playerRef, rewards.maps());

        allItems.addAll(syncedMaps);
        allItems.addAll(rewards.stones()); // Native items, no sync needed
        allItems.addAll(rewards.gear()); // Gear doesn't need custom sync

        if (allItems.isEmpty()) {
            LOGGER.atWarning().log("All reward items failed to sync for player %s",
                playerId.toString().substring(0, 8));
            return new DistributionResult(0, 0);
        }

        // Step 2: Add to inventory
        int delivered = 0;
        int overflow = 0;

        // Get the player's inventory through the entity reference
        Inventory inventory = getPlayerInventory(playerRef);
        if (inventory == null) {
            LOGGER.atWarning().log("Player %s has no inventory - cannot distribute rewards",
                playerId.toString().substring(0, 8));
            return new DistributionResult(0, allItems.size());
        }

        for (ItemStack item : allItems) {
            if (item == null || item.isEmpty()) {
                continue;
            }

            // Try to add to hotbar first, then backpack
            boolean added = addToInventory(inventory, item);

            if (added) {
                delivered++;
            } else {
                // Item didn't fit - count as overflow
                overflow++;
                LOGGER.atFine().log("Item overflow for player %s: %s",
                    playerId.toString().substring(0, 8), item.getItemId());

                // TODO: Could drop at player's feet or queue for later
                // For now, we just log it as lost
            }
        }

        // Step 3: Send summary message
        sendSummaryMessage(playerRef, rewards, delivered, overflow);

        LOGGER.atInfo().log("Distributed %d/%d victory rewards to player %s (overflow: %d)",
            delivered, allItems.size(), playerId.toString().substring(0, 8), overflow);

        return new DistributionResult(delivered, overflow);
    }

    // ═══════════════════════════════════════════════════════════════════
    // SYNC HELPERS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Syncs map items and returns the ready-to-add items.
     */
    @Nonnull
    private List<ItemStack> syncAndCollectMaps(
            @Nonnull PlayerRef playerRef,
            @Nonnull List<ItemStack> maps) {
        if (maps.isEmpty()) {
            return List.of();
        }

        List<ItemStack> result = new ArrayList<>(maps.size());

        for (ItemStack mapItem : maps) {
            if (mapItem == null || mapItem.isEmpty()) {
                continue;
            }

            // Read map data for sync
            Optional<RealmMapData> mapDataOpt = RealmMapUtils.readMapData(mapItem);
            if (mapDataOpt.isEmpty()) {
                LOGGER.atWarning().log("Failed to read map data for victory reward");
                continue;
            }

            RealmMapData mapData = mapDataOpt.get();

            // Sync to player if service available
            if (customItemSyncService != null && mapData.instanceId() != null) {
                customItemSyncService.syncItem(playerRef, mapData);
            }

            result.add(mapItem);
        }

        return result;
    }

    // ═══════════════════════════════════════════════════════════════════
    // MESSAGING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Sends a victory reward summary message to the player.
     */
    private void sendSummaryMessage(
            @Nonnull PlayerRef playerRef,
            @Nonnull VictoryRewardGenerator.VictoryRewards rewards,
            int delivered,
            int overflow) {

        // Build the summary message
        Message header = Message.empty()
            .insert(Message.raw("[").color(MessageColors.GRAY))
            .insert(Message.raw("Rewards").color(MessageColors.GOLD).bold(true))
            .insert(Message.raw("] ").color(MessageColors.GRAY));

        // Count by type
        int mapCount = rewards.maps().size();
        int gearCount = rewards.gear().size();
        int stoneCount = rewards.stones().size();

        // Build items list
        StringBuilder items = new StringBuilder();
        if (mapCount > 0) {
            items.append(mapCount).append(" map").append(mapCount > 1 ? "s" : "");
        }
        if (gearCount > 0) {
            if (items.length() > 0) items.append(", ");
            items.append(gearCount).append(" gear");
        }
        if (stoneCount > 0) {
            if (items.length() > 0) items.append(", ");
            items.append(stoneCount).append(" stone").append(stoneCount > 1 ? "s" : "");
        }

        Message content;
        if (delivered > 0) {
            content = Message.raw("Received : ").color(MessageColors.SUCCESS)
                .insert(Message.raw(items.toString()).color(MessageColors.WHITE));
        } else {
            content = Message.raw("No rewards received.").color(MessageColors.WARNING);
        }

        Message finalMessage = header.insert(content);

        // Add overflow warning if needed
        if (overflow > 0) {
            Message overflowMsg = Message.empty()
                .insert(Message.raw(" (").color(MessageColors.GRAY))
                .insert(Message.raw(overflow + " lost - inventory full").color(MessageColors.ERROR))
                .insert(Message.raw(")").color(MessageColors.GRAY));
            finalMessage = finalMessage.insert(overflowMsg);
        }

        playerRef.sendMessage(finalMessage);
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the player's inventory from the PlayerRef.
     *
     * <p>This requires accessing the entity store through the reference,
     * then getting the Player component, then the inventory.
     *
     * @param playerRef The player reference
     * @return The player's inventory, or null if not accessible
     */
    @Nullable
    private Inventory getPlayerInventory(@Nonnull PlayerRef playerRef) {
        try {
            Ref<EntityStore> entityRef = playerRef.getReference();
            if (entityRef == null || !entityRef.isValid()) {
                return null;
            }

            Player player = entityRef.getStore().getComponent(entityRef, Player.getComponentType());
            if (player == null) {
                return null;
            }

            return player.getInventory();
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to get inventory for player %s",
                playerRef.getUuid().toString().substring(0, 8));
            return null;
        }
    }

    /**
     * Attempts to add an item to the player's inventory.
     *
     * <p>Tries hotbar first, then backpack.
     *
     * @param inventory The player's inventory
     * @param item The item to add
     * @return true if the item was fully added, false otherwise
     */
    private boolean addToInventory(@Nonnull Inventory inventory, @Nonnull ItemStack item) {
        // Try hotbar first
        ItemStackTransaction transaction = inventory.getHotbar().addItemStack(item);
        if (transaction.succeeded()) {
            return true;
        }

        // Try backpack
        transaction = inventory.getBackpack().addItemStack(item);
        return transaction.succeeded();
    }

    // ═══════════════════════════════════════════════════════════════════
    // ACCESSORS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the custom item sync service.
     *
     * @return The sync service, or null if not configured
     */
    @Nullable
    public CustomItemSyncService getCustomItemSyncService() {
        return customItemSyncService;
    }
}
