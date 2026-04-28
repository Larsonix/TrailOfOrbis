package io.github.larsonix.trailoforbis.maps.reward;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * Periodically monitors open reward chests and saves state when closed.
 *
 * <p>This is NOT a full ECS tick system — reward chests are temporary and rare
 * (1 per realm at a time). Instead, this uses a simple scheduled check that
 * runs periodically via the RealmsManager's scheduler.
 *
 * <p>For each player who has a reward chest open, it checks whether the
 * container window is still open. When the player closes the chest:
 * <ol>
 *   <li>Reads remaining items from the physical container</li>
 *   <li>Saves them back to RewardChestManager</li>
 *   <li>Clears the physical container (ready for next player)</li>
 *   <li>Marks the player's chest as closed</li>
 * </ol>
 *
 * @see RewardChestManager
 * @see RewardChestInterceptor
 */
public class RewardChestMonitor {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final ComponentType<ChunkStore, ItemContainerBlock> CONTAINER_COMP_TYPE =
        ItemContainerBlock.getComponentType();

    private final RewardChestManager chestManager;

    /**
     * Creates a new monitor.
     *
     * @param chestManager The reward chest manager
     */
    public RewardChestMonitor(@Nonnull RewardChestManager chestManager) {
        this.chestManager = Objects.requireNonNull(chestManager, "chestManager cannot be null");
    }

    /**
     * Checks all open reward chests and saves state for any that have been closed.
     *
     * <p>Call this periodically (e.g., every 500ms) from the scheduler.
     * Safe to call from any thread — individual world operations are dispatched
     * to the correct world thread.
     */
    public void tick() {
        Set<UUID> playersWithOpenChests = chestManager.getPlayersWithOpenChests();
        if (playersWithOpenChests.isEmpty()) {
            return;
        }

        // Copy to avoid ConcurrentModificationException
        List<UUID> toCheck = new ArrayList<>(playersWithOpenChests);

        for (UUID playerId : toCheck) {
            try {
                checkPlayerChest(playerId);
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Error checking reward chest for player %s",
                    playerId.toString().substring(0, 8));
                // Safety: remove tracking to avoid repeated errors
                chestManager.markChestClosed(playerId);
            }
        }
    }

    /**
     * Checks if a player's reward chest window is still open.
     */
    private void checkPlayerChest(@Nonnull UUID playerId) {
        RewardChestManager.RewardChestInfo chestInfo = chestManager.getOpenChest(playerId);
        if (chestInfo == null) {
            return;
        }

        World world = Universe.get().getWorld(chestInfo.worldId());
        if (world == null || !world.isAlive()) {
            // World gone — clean up
            chestManager.markChestClosed(playerId);
            return;
        }

        try {
            world.execute(() -> checkPlayerChestOnWorldThread(playerId, chestInfo, world));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log(
                "Failed to dispatch reward chest check for player %s",
                playerId.toString().substring(0, 8));
            chestManager.markChestClosed(playerId);
        }
    }

    /**
     * Runs the chest inspection on the world's thread.
     */
    private void checkPlayerChestOnWorldThread(
            @Nonnull UUID playerId,
            @Nonnull RewardChestManager.RewardChestInfo expectedChestInfo,
            @Nonnull World world) {
        try {
            // Chest may have changed while the task was queued.
            RewardChestManager.RewardChestInfo chestInfo = chestManager.getOpenChest(playerId);
            if (chestInfo == null || !chestInfo.equals(expectedChestInfo)) {
                return;
            }

            if (!world.isAlive()) {
                chestManager.markChestClosed(playerId);
                return;
            }

            Vector3i pos = chestInfo.position();

            // Get the block's ECS ref and look up the ItemContainerBlock component
            WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
            if (chunk == null) {
                chestManager.markChestClosed(playerId);
                return;
            }
            Ref<ChunkStore> blockRef = chunk.getBlockComponentEntity(pos.x, pos.y, pos.z);
            if (blockRef == null || !blockRef.isValid()) {
                chestManager.markChestClosed(playerId);
                return;
            }
            Store<ChunkStore> chunkStoreStore = world.getChunkStore().getStore();
            ItemContainerBlock containerState = chunkStoreStore.getComponent(blockRef, CONTAINER_COMP_TYPE);

            if (containerState == null) {
                // Block was replaced or destroyed — clean up
                chestManager.markChestClosed(playerId);
                return;
            }

            // Check if the container window is still open
            if (!containerState.getWindows().isEmpty()) {
                return; // Still open, nothing to do
            }

            // Window closed — save remaining items
            UUID realmId = chestInfo.realmId();
            List<ItemStack> remaining = new ArrayList<>();
            short capacity = containerState.getItemContainer().getCapacity();
            for (short i = 0; i < capacity; i++) {
                ItemStack item = containerState.getItemContainer().getItemStack(i);
                if (item != null && !item.isEmpty()) {
                    remaining.add(item);
                }
            }

            // Save back to the manager
            chestManager.savePlayerState(realmId, playerId, remaining);

            // Clear the physical container (ready for next player)
            containerState.getItemContainer().clear();

            // Mark closed
            chestManager.markChestClosed(playerId);

            LOGGER.atFine().log("Player %s closed reward chest (realm %s), saved %d remaining items",
                playerId.toString().substring(0, 8),
                realmId.toString().substring(0, 8),
                remaining.size());
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log(
                "Error checking reward chest on world thread for player %s",
                playerId.toString().substring(0, 8));
            chestManager.markChestClosed(playerId);
        }
    }
}
