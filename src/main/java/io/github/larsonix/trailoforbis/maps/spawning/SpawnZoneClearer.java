package io.github.larsonix.trailoforbis.maps.spawning;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import io.github.larsonix.trailoforbis.maps.core.RealmBiomeType;
import io.github.larsonix.trailoforbis.util.TerrainUtils;

import javax.annotation.Nonnull;
import java.util.Set;

/**
 * Clears WorldGen V2 props and fluid around the player spawn point in realm arenas.
 *
 * <p>WorldGen V2 has no awareness of spawn coordinates — trees, rocks, and bushes
 * are distributed purely by noise functions and grid positions, meaning a Stage_4
 * oak tree can land directly at origin (0, 0) where the player spawns and where
 * the victory portal later appears.
 *
 * <p>This utility runs <b>after</b> chunk generation completes but <b>before</b>
 * the player enters, removing non-terrain blocks and fluid in a configurable
 * radius. It is one layer of a tiered defense:
 * <ol>
 *   <li><b>SpawnZoneClearer</b> (this) — removes WorldGen props (R=10)</li>
 *   <li><b>StructureBoundsRegistry</b> — pre-registered spawn zone prevents
 *       runtime structure overlap (R=12)</li>
 *   <li><b>RealmStructurePlacer</b> — exclusion radius for scattered structures (R=16)</li>
 *   <li><b>RealmMobSpawner</b> — configurable mob exclusion zone (R=20)</li>
 * </ol>
 *
 * <h2>Algorithm (per column)</h2>
 * <ol>
 *   <li>Find biome-aware terrain ground level (skips vegetation, finds real soil/rock)</li>
 *   <li>Clear all non-terrain, non-empty blocks from ground level upward</li>
 *   <li>Clear all fluid from ground level upward</li>
 * </ol>
 *
 * <p><b>Preserves:</b> terrain blocks (biome whitelist from {@link RealmBiomeType#getTerrainMaterials()}),
 * empty air, Caverns ceilings (terrain material).
 * <br><b>Removes:</b> trees, rocks, bushes, grass, flowers, mushrooms, any WorldGen props.
 *
 * @see RealmStructurePlacer
 * @see StructureBoundsRegistry
 */
public final class SpawnZoneClearer {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Default clearing radius (blocks). Covers typical tree canopy overhang at center. */
    public static final int DEFAULT_CLEAR_RADIUS = 10;

    /**
     * Maximum Y levels to scan above the per-column terrain surface.
     * Covers the tallest vanilla trees (~20 blocks) with margin.
     */
    private static final int SCAN_HEIGHT_ABOVE_GROUND = 30;

    /** Resolved empty block type for setting cleared positions to air. */
    private static final BlockType EMPTY_BLOCK = BlockType.getAssetMap().getAsset("Empty");

    private SpawnZoneClearer() {
        // Utility class
    }

    /**
     * Result of a spawn zone clearing operation.
     *
     * @param blocksCleared number of non-terrain blocks replaced with air
     * @param fluidCleared  number of fluid sources removed
     */
    public record ClearingResult(int blocksCleared, int fluidCleared) {
        /** Total modifications made. */
        public int total() { return blocksCleared + fluidCleared; }
    }

    // ═══════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Clear WorldGen props and fluid in a circular radius around the spawn point.
     *
     * <p><b>Must be called on the world thread after all arena chunks are loaded.</b>
     *
     * <p>For each (x, z) within the circular radius:
     * <ol>
     *   <li>Finds per-column terrain ground level using the biome's material whitelist</li>
     *   <li>Scans upward from ground level, replacing non-terrain solid blocks with air</li>
     *   <li>Removes any fluid (water) above ground level</li>
     * </ol>
     *
     * <p>Terrain blocks are preserved (soil, rock, ice, sand — whatever the biome defines).
     * Caverns ceilings are safe because ceiling blocks are terrain material.
     *
     * @param world       the realm world (chunks must be loaded)
     * @param biome       the realm biome (provides terrain material whitelist)
     * @param centerX     spawn X coordinate (typically 0)
     * @param centerZ     spawn Z coordinate (typically 0)
     * @param clearRadius radius in blocks to clear around center
     * @param maxScanY    maximum Y to start ground detection from (use baseY + 1 for ceiling biomes)
     * @return clearing result with counts of blocks and fluid removed
     */
    @Nonnull
    public static ClearingResult clearSpawnArea(
            @Nonnull World world,
            @Nonnull RealmBiomeType biome,
            int centerX, int centerZ,
            int clearRadius,
            int maxScanY) {

        if (EMPTY_BLOCK == null) {
            LOGGER.atWarning().log("Cannot clear spawn area — Empty block type not resolved");
            return new ClearingResult(0, 0);
        }

        Set<String> terrainMaterials = biome.getTerrainMaterials();
        String emptyId = EMPTY_BLOCK.getId();
        int radiusSq = clearRadius * clearRadius;
        int blocksCleared = 0;
        int fluidCleared = 0;

        var chunkStore = world.getChunkStore();

        for (int dx = -clearRadius; dx <= clearRadius; dx++) {
            for (int dz = -clearRadius; dz <= clearRadius; dz++) {
                // Circular check — skip corners of the bounding square
                if (dx * dx + dz * dz > radiusSq) {
                    continue;
                }

                int x = centerX + dx;
                int z = centerZ + dz;

                WorldChunk chunk = world.getChunkIfInMemory(
                    ChunkUtil.indexChunkFromBlock(x, z));
                if (chunk == null) {
                    continue;
                }

                // Find terrain ground level for THIS column.
                // Uses biome-aware whitelist so tree trunks and mushrooms are skipped,
                // landing on actual soil/rock. Returns Y+1 (one above terrain surface).
                int groundY = TerrainUtils.findGroundLevel(
                    world, x, z, maxScanY, terrainMaterials);
                int clearMaxY = groundY + SCAN_HEIGHT_ABOVE_GROUND;

                // Single pass: clear blocks and fluid at each Y level
                for (int y = groundY; y <= clearMaxY; y++) {

                    // ── Block clearing ──
                    try {
                        BlockType block = world.getBlockType(x, y, z);
                        if (block != null && block.getMaterial() != BlockMaterial.Empty) {
                            String blockId = block.getId();
                            if (blockId != null && !terrainMaterials.contains(blockId)) {
                                chunk.setBlock(x, y, z, emptyId);
                                blocksCleared++;
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.atFine().log("Failed to clear block at (%d,%d,%d): %s",
                            x, y, z, e.getMessage());
                    }

                    // ── Fluid clearing ──
                    // Fluid is a separate ECS component (FluidSection), not part of
                    // the block. Must be cleared independently to prevent spawning
                    // in water (Beach/Swamp biomes).
                    try {
                        int fluidId = chunk.getFluidId(x, y, z);
                        if (fluidId != 0) {
                            Ref<ChunkStore> section = chunkStore.getChunkSectionReference(
                                ChunkUtil.chunkCoordinate(x),
                                ChunkUtil.chunkCoordinate(y),
                                ChunkUtil.chunkCoordinate(z));
                            if (section != null && section.isValid()) {
                                FluidSection fluidSection = section.getStore()
                                    .getComponent(section, FluidSection.getComponentType());
                                if (fluidSection != null) {
                                    fluidSection.setFluid(x, y, z, 0, (byte) 0);
                                    fluidCleared++;
                                }
                            }
                        }
                    } catch (Throwable e) {
                        LOGGER.atFine().log("Failed to clear fluid at (%d,%d,%d): %s",
                            x, y, z, e.getMessage());
                    }
                }
            }
        }

        if (blocksCleared > 0 || fluidCleared > 0) {
            LOGGER.atInfo().log("Spawn zone cleared at (%d,%d) R=%d: %d blocks, %d fluid [%s]",
                centerX, centerZ, clearRadius, blocksCleared, fluidCleared, biome.name());
        }

        return new ClearingResult(blocksCleared, fluidCleared);
    }
}
