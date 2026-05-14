package io.github.larsonix.trailoforbis.maps.spawning;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;
import io.github.larsonix.trailoforbis.maps.templates.RealmTemplateRegistry;

import javax.annotation.Nonnull;

/**
 * Removes all container blocks (chests, barrels, crates) from a realm arena
 * when the realm ends.
 *
 * <p>Runs on the world thread, scanning the circular arena area for blocks
 * with the {@link ItemContainerBlock} ECS component. Each container is
 * replaced with Empty (air). When the block is removed, Hytale automatically
 * invalidates any open container windows on the client.
 *
 * <p>Follows the same iteration pattern as {@link SpawnZoneClearer}.
 *
 * @see SpawnZoneClearer
 */
public final class RealmContainerClearer {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final ComponentType<ChunkStore, ItemContainerBlock> CONTAINER_COMP_TYPE =
        ItemContainerBlock.getComponentType();

    private static final BlockType EMPTY_BLOCK = BlockType.getAssetMap().getAsset("Empty");

    /** Y levels below base Y to scan (underground structures). */
    private static final int SCAN_BELOW = 20;

    /** Y levels above base Y to scan (surface + elevated structures). */
    private static final int SCAN_ABOVE = 50;

    private RealmContainerClearer() {
        // Utility class
    }

    /**
     * Removes all container blocks in the realm arena.
     *
     * <p>Scans the circular arena area for blocks with the {@link ItemContainerBlock}
     * ECS component and replaces them with Empty. When the block is removed, Hytale
     * invalidates any open container windows on the client (same mechanism as
     * {@link io.github.larsonix.trailoforbis.maps.reward.RewardChestManager#cleanup}).
     *
     * <p><b>Must be called on the world thread.</b>
     *
     * @param world the realm world
     * @param realm the realm instance (provides arena bounds)
     * @return number of containers removed
     */
    public static int clearContainers(@Nonnull World world, @Nonnull RealmInstance realm) {
        if (EMPTY_BLOCK == null) {
            LOGGER.atWarning().log("Cannot clear containers — Empty block type not resolved");
            return 0;
        }

        // Determine scan bounds
        int arenaRadius = realm.getMapData().computeArenaRadius();
        int baseY = (int) RealmTemplateRegistry.getBaseYForBiome(realm.getBiome());
        int minY = baseY - SCAN_BELOW;
        int maxY = baseY + SCAN_ABOVE;
        int radiusSq = arenaRadius * arenaRadius;

        String emptyId = EMPTY_BLOCK.getId();
        Store<ChunkStore> chunkStoreStore = world.getChunkStore().getStore();
        int removed = 0;

        for (int dx = -arenaRadius; dx <= arenaRadius; dx++) {
            for (int dz = -arenaRadius; dz <= arenaRadius; dz++) {
                // Circular check — skip corners
                if (dx * dx + dz * dz > radiusSq) {
                    continue;
                }

                int x = dx; // Arena center is always (0, 0)
                int z = dz;

                WorldChunk chunk = world.getChunkIfInMemory(
                    ChunkUtil.indexChunkFromBlock(x, z));
                if (chunk == null) {
                    continue;
                }

                for (int y = minY; y <= maxY; y++) {
                    try {
                        Ref<ChunkStore> blockRef = chunk.getBlockComponentEntity(x, y, z);
                        if (blockRef == null || !blockRef.isValid()) {
                            continue;
                        }

                        ItemContainerBlock container =
                            chunkStoreStore.getComponent(blockRef, CONTAINER_COMP_TYPE);
                        if (container != null) {
                            // Clear the container's inventory BEFORE destroying the block.
                            // Without this, Hytale's ItemContainerState.onDestroy() calls
                            // dropAllItemStacks() and spawns all items as vanilla entities.
                            container.getItemContainer().clear();
                            chunk.setBlock(x, y, z, emptyId);
                            removed++;
                        }
                    } catch (Exception e) {
                        LOGGER.atFine().log("Failed to check/clear block at (%d,%d,%d): %s",
                            x, y, z, e.getMessage());
                    }
                }
            }
        }

        if (removed > 0) {
            LOGGER.atInfo().log("Cleared %d containers in realm %s (radius=%d, Y=%d-%d)",
                removed, realm.getRealmId().toString().substring(0, 8),
                arenaRadius, minY, maxY);
        }

        return removed;
    }
}
