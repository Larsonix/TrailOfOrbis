package io.github.larsonix.trailoforbis.maps.reward;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.transaction.ClearTransaction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;

/**
 * ECS event system that intercepts chest opens for reward chests.
 *
 * <p>When a player uses (right-clicks) a reward chest, this system:
 * <ol>
 *   <li>Checks if the block is a tracked reward chest</li>
 *   <li>Loads the player's per-player reward items into the container</li>
 *   <li>Marks the player as having the chest open (for monitoring)</li>
 *   <li>Does NOT cancel the event — vanilla opens the container UI</li>
 * </ol>
 *
 * <p>This system follows L4E's UseBlockEventPre pattern: intercept before
 * the vanilla open, populate the container with per-player items, then
 * let the normal Open_Container interaction proceed.
 *
 * <p>This system is only active in standalone mode (when L4E is not present).
 *
 * @see RewardChestManager
 * @see RewardChestMonitor
 */
public class RewardChestInterceptor extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final ComponentType<ChunkStore, ItemContainerBlock> CONTAINER_COMP_TYPE =
        ItemContainerBlock.getComponentType();

    private final RewardChestManager chestManager;

    /**
     * Creates a new interceptor.
     *
     * @param chestManager The reward chest manager
     */
    public RewardChestInterceptor(@Nonnull RewardChestManager chestManager) {
        super(UseBlockEvent.Pre.class);
        this.chestManager = chestManager;
    }

    @Override
    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull UseBlockEvent.Pre event) {

        // Only handle "Use" interaction (right-click)
        if (event.getInteractionType() != InteractionType.Use) {
            return;
        }

        Vector3i target = event.getTargetBlock();
        Ref<EntityStore> playerRef = event.getContext().getEntity();
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null) {
            return;
        }

        // Check if this block is one of our reward chests
        UUID worldId = player.getWorld().getWorldConfig().getUuid();
        UUID realmId = chestManager.findRealmIdForChest(target.x, target.y, target.z, worldId);
        if (realmId == null) {
            return; // Not a reward chest — let vanilla handle it
        }

        // Get player UUID via ECS PlayerRef (Entity.getUuid() deprecated for removal)
        PlayerRef playerRefComp = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (playerRefComp == null) return;
        UUID playerId = playerRefComp.getUuid();

        // Get the block's ECS ref and look up the ItemContainerBlock component
        World world = player.getWorld();
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(target.x, target.z));
        if (chunk == null) return;
        Ref<ChunkStore> blockRef = chunk.getBlockComponentEntity(target.x, target.y, target.z);
        if (blockRef == null || !blockRef.isValid()) return;
        Store<ChunkStore> chunkStoreStore = world.getChunkStore().getStore();
        ItemContainerBlock containerState = chunkStoreStore.getComponent(blockRef, CONTAINER_COMP_TYPE);
        if (containerState == null) {
            LOGGER.atWarning().log("Reward chest at %s is not an ItemContainerBlock", target);
            return;
        }

        // If someone else has the chest open, cancel to prevent interference
        if (!containerState.getWindows().isEmpty()) {
            LOGGER.atFine().log("Reward chest at %s already open, cancelling for player %s",
                target, playerId.toString().substring(0, 8));
            event.setCancelled(true);
            return;
        }

        // Load this player's rewards into the container
        List<ItemStack> rewards = chestManager.getPlayerRewards(realmId, playerId);

        // Clear the container first (it may have another player's items from before)
        ClearTransaction clearTx = containerState.getItemContainer().clear();
        if (!clearTx.succeeded()) {
            LOGGER.atWarning().log("Failed to clear reward chest container at %s", target);
            event.setCancelled(true);
            return;
        }

        // Populate with this player's items
        short capacity = containerState.getItemContainer().getCapacity();
        for (int i = 0; i < rewards.size() && i < capacity; i++) {
            ItemStack item = rewards.get(i);
            if (item != null && !item.isEmpty()) {
                containerState.getItemContainer().setItemStackForSlot((short) i, item);
            }
        }

        // Track that this player has the chest open
        RewardChestManager.RewardChestInfo chestInfo = chestManager.getChestInfo(realmId);
        if (chestInfo != null) {
            chestManager.markChestOpen(playerId, chestInfo);
        }

        LOGGER.atInfo().log("Loaded %d reward items into chest for player %s (realm %s)",
            rewards.size(),
            playerId.toString().substring(0, 8),
            realmId.toString().substring(0, 8));

        // Do NOT cancel — let vanilla Open_Container interaction proceed
        // This opens the container UI and triggers the open animation
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }
}
