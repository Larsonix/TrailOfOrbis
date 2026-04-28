package io.github.larsonix.trailoforbis.util;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.protocol.DrawType;
import com.hypixel.hytale.protocol.Opacity;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;

/**
 * Utility for terrain-aware ground detection.
 *
 * <p>Hytale's {@link BlockMaterial} only has {@code Empty} and {@code Solid},
 * so leaves, logs, bushes, and cacti are all considered "Solid". This utility
 * combines material, opacity, and draw type checks to distinguish actual
 * terrain blocks (dirt, stone, sand) from vegetation (leaves, plants, flowers).
 *
 * <p>A block is considered terrain if ALL of:
 * <ul>
 *   <li>{@code material == BlockMaterial.Solid}</li>
 *   <li>{@code opacity == Opacity.Solid} (rejects leaves=Cutout, plants=Transparent)</li>
 *   <li>{@code drawType == DrawType.Cube || drawType == DrawType.CubeWithModel}
 *       (rejects Model-type vegetation)</li>
 * </ul>
 */
public final class TerrainUtils {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Maximum Y to start scanning from. */
    private static final int SCAN_START_Y = 256;

    /** Minimum Y to stop scanning at. */
    private static final int SCAN_MIN_Y = 30;

    /** Default fallback Y position if ground detection fails. */
    private static final int FALLBACK_GROUND_Y = 65;

    private TerrainUtils() {
        // Utility class
    }

    /**
     * Checks if a block is terrain using a biome-specific material whitelist.
     *
     * <p>Only blocks whose ID is in the provided set count as ground.
     * This is sourced from {@code RealmBiomeType.getTerrainMaterials()}
     * which mirrors the surface/sub/deep materials in the biome generator.
     * Any block not in the set (tree trunks, mushrooms, any future prop)
     * is automatically skipped.
     *
     * @param block The block type to check, or null
     * @param terrainMaterials The set of valid terrain block IDs for this biome
     * @return true if the block is terrain for this biome
     */
    public static boolean isTerrainBlock(@Nullable BlockType block, @Nonnull Set<String> terrainMaterials) {
        if (block == null || block.isUnknown()) {
            return false;
        }
        String id = block.getId();
        if (id == null) {
            return false;
        }
        // Biome-specific terrain materials (Soil_Mud, Rock_Stone, etc.)
        if (terrainMaterials.contains(id)) {
            return true;
        }
        // Universal spawnable surfaces — structure floors that mobs should walk on.
        // All plank types (11 wood variants) + Kweebec platforms. These appear inside
        // faction structures placed by RealmStructurePlacer and must be valid ground
        // for mob spawning regardless of biome.
        return id.endsWith("_Planks") || id.equals("Furniture_Kweebec_Platform");
    }

    /**
     * Fallback: checks if a block is terrain using generic prefix matching.
     * Used when no biome-specific material set is available.
     *
     * @param block The block type to check, or null
     * @return true if the block looks like terrain
     */
    public static boolean isTerrainBlock(@Nullable BlockType block) {
        if (block == null || block.isUnknown()) {
            return false;
        }
        String id = block.getId();
        return id != null && (id.startsWith("Soil_") || id.startsWith("Rock_"));
    }

    /**
     * Finds ground Y at (x, z) by scanning down for the first terrain block.
     *
     * <p>Scans from Y=256 down to Y=30, skipping vegetation, leaves, and
     * other non-terrain blocks. Returns the Y coordinate one block above
     * the terrain surface (suitable for placing a portal or entity).
     *
     * @param world The world to scan in
     * @param x The X coordinate
     * @param z The Z coordinate
     * @return The Y coordinate one block above ground, or a fallback value
     */
    public static int findGroundLevel(@Nonnull World world, int x, int z) {
        return findGroundLevel(world, x, z, SCAN_START_Y);
    }

    /**
     * Finds ground Y at (x, z) by scanning down from a maximum Y.
     *
     * <p>The maxY parameter allows callers to start the scan below ceilings
     * or other overhead structures. For biomes with a ceiling (Caverns),
     * pass maxY below the ceiling height to find the cave floor, not the
     * ceiling's top surface.
     *
     * @param world The world to scan in
     * @param x The X coordinate
     * @param z The Z coordinate
     * @param maxY The Y coordinate to start scanning from (must be > SCAN_MIN_Y)
     * @return The Y coordinate one block above ground, or a fallback value
     */
    public static int findGroundLevel(@Nonnull World world, int x, int z, int maxY) {
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            LOGGER.atFine().log("Chunk not loaded at (%d, %d), using fallback Y=%d", x, z, FALLBACK_GROUND_Y);
            return FALLBACK_GROUND_Y;
        }

        for (int y = Math.min(maxY, SCAN_START_Y); y >= SCAN_MIN_Y; y--) {
            BlockType block = world.getBlockType(x, y, z);
            if (isTerrainBlock(block)) {
                int spawnY = y + 1;

                // Guarantee 2-block clearance — both spawnY and spawnY+1 must be non-solid
                BlockType atSpawn = world.getBlockType(x, spawnY, z);
                BlockType aboveSpawn = world.getBlockType(x, spawnY + 1, z);

                boolean spawnClear = (atSpawn == null || atSpawn.getMaterial() != BlockMaterial.Solid);
                boolean aboveClear = (aboveSpawn == null || aboveSpawn.getMaterial() != BlockMaterial.Solid);

                if (spawnClear && aboveClear) {
                    return spawnY;
                }
                // Blocked — continue scanning down for next valid surface
                continue;
            }
        }

        LOGGER.atFine().log("No terrain found at (%d, %d) from maxY=%d, using fallback Y=%d", x, z, maxY, FALLBACK_GROUND_Y);
        return FALLBACK_GROUND_Y;
    }

    /**
     * Finds ground Y using a biome-specific terrain material whitelist.
     *
     * <p>Only blocks whose ID is in {@code terrainMaterials} count as ground.
     * Tree trunks, mushrooms, and any other prop blocks are automatically skipped.
     *
     * <p><b>Guarantee:</b> The returned Y always has 2 blocks of clearance (non-terrain,
     * non-solid) above it. If a terrain surface has solid blocks above (inside a hill,
     * under a structure), the scan continues downward to find the next valid surface.
     * This prevents mobs from spawning inside terrain or structures.
     *
     * @param world The world to scan in
     * @param x The X block coordinate
     * @param z The Z block coordinate
     * @param maxY The Y coordinate to start scanning from
     * @param terrainMaterials Valid terrain block IDs for this biome
     * @return The Y coordinate one block above ground with 2-block clearance, or a fallback value
     */
    public static int findGroundLevel(@Nonnull World world, int x, int z, int maxY,
                                      @Nonnull Set<String> terrainMaterials) {
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            LOGGER.atFine().log("Chunk not loaded at (%d, %d), using fallback Y=%d", x, z, FALLBACK_GROUND_Y);
            return FALLBACK_GROUND_Y;
        }

        for (int y = Math.min(maxY, SCAN_START_Y); y >= SCAN_MIN_Y; y--) {
            BlockType block = world.getBlockType(x, y, z);
            if (isTerrainBlock(block, terrainMaterials)) {
                int spawnY = y + 1;

                // GUARANTEE: verify 2 blocks of clearance above the candidate spawn Y.
                // Both spawnY and spawnY+1 must be non-solid (air, plants, empty).
                // If either is solid (inside terrain, under a structure), skip this
                // surface and continue scanning down for the next valid one.
                BlockType atSpawn = world.getBlockType(x, spawnY, z);
                BlockType aboveSpawn = world.getBlockType(x, spawnY + 1, z);

                boolean spawnClear = (atSpawn == null || atSpawn.getMaterial() != BlockMaterial.Solid);
                boolean aboveClear = (aboveSpawn == null || aboveSpawn.getMaterial() != BlockMaterial.Solid);

                if (spawnClear && aboveClear) {
                    return spawnY;
                }

                // This surface is blocked — continue scanning down.
                // The terrain block at Y is valid ground, but there's no room above.
                // Skip this entire solid column by scanning past it.
                continue;
            }
        }

        LOGGER.atFine().log("No terrain found at (%d, %d) from maxY=%d, using fallback Y=%d", x, z, maxY, FALLBACK_GROUND_Y);
        return FALLBACK_GROUND_Y;
    }

    /**
     * Finds the ground level for placing a structure by sampling multiple points
     * across the structure's footprint and returning the <b>lowest</b> valid Y.
     *
     * <p>When terrain is uneven (hills, noise amplitude &gt; 0.2), a single-point
     * ground scan at the structure's center may land on a terrain peak. The structure
     * — a rigid prefab — then floats above the surrounding lower terrain.
     *
     * <p>This method samples a 3&times;3 grid around the center (center + 8 offsets)
     * at {@code footprintRadius} spacing. Using the <b>minimum</b> Y ensures the
     * structure sits at the lowest nearby terrain surface, preventing visible gaps.
     * Some parts may clip slightly into nearby peaks — this is far less noticeable
     * than floating above valleys.
     *
     * @param world            The world to scan in (chunks must be loaded)
     * @param centerX          Center X of the structure placement
     * @param centerZ          Center Z of the structure placement
     * @param maxY             Upper Y bound for scanning
     * @param terrainMaterials Valid terrain block IDs for this biome
     * @param footprintRadius  Half-size of the sampling grid (typically 3-5)
     * @return The lowest ground Y across the sampled footprint
     */
    public static int findStructureGroundLevel(@Nonnull World world, int centerX, int centerZ,
                                                int maxY, @Nonnull Set<String> terrainMaterials,
                                                int footprintRadius) {
        int lowestY = findGroundLevel(world, centerX, centerZ, maxY, terrainMaterials);

        // Sample 8 surrounding points in a 3×3 grid
        int[] offsets = {-footprintRadius, 0, footprintRadius};
        for (int dx : offsets) {
            for (int dz : offsets) {
                if (dx == 0 && dz == 0) {
                    continue; // Already sampled center
                }
                int y = findGroundLevel(world, centerX + dx, centerZ + dz, maxY, terrainMaterials);
                if (y < lowestY) {
                    lowestY = y;
                }
            }
        }

        return lowestY;
    }

    /**
     * Checks if a position has enough clearance for an NPC to spawn.
     * Defaults to 2x2x2 for normal mobs (mobs occupy more than a single column).
     *
     * @param world   The world to check in
     * @param x       Block X coordinate
     * @param groundY The Y coordinate to check (should be above terrain surface)
     * @param z       Block Z coordinate
     * @return true if the position has sufficient air clearance
     */
    public static boolean isSpawnPositionClear(@Nonnull World world, int x, int groundY, int z) {
        return isSpawnPositionClear(world, x, groundY, z, 1.0f, false, false);
    }

    /**
     * Checks if a position has enough clearance for an NPC to spawn, scaled by mob size.
     *
     * <p>Normal mobs need a 2x2x2 clear volume (mobs are wider than 1 block).
     * Bosses and elites are visually scaled, so their clearance volume scales too:
     * <ul>
     *   <li>Normal: 2×2×2 blocks</li>
     *   <li>Elite (1.20x): 3×3×3 blocks (ceil(2 × 1.20))</li>
     *   <li>Boss (1.50x): 3×3×3 blocks (ceil(2 × 1.50))</li>
     *   <li>Elite Boss (1.80x): 4×4×4 blocks (ceil(2 × 1.80))</li>
     * </ul>
     *
     * <p>If the required volume is obstructed at the given position, the caller
     * should try nearby offsets or a new random position entirely. This prevents
     * mobs from spawning inside structures, trees, or terrain where they suffocate.
     *
     * @param world      The world to check in
     * @param x          Block X coordinate (center of footprint)
     * @param groundY    The Y coordinate to check (should be above terrain surface)
     * @param z          Block Z coordinate (center of footprint)
     * @param scale      Visual scale multiplier (1.0 for normal, configured for elite/boss)
     * @param isBoss     Whether this is a boss mob
     * @param isElite    Whether this mob rolled elite
     * @return true if the position has sufficient air clearance for the mob's size
     */
    public static boolean isSpawnPositionClear(@Nonnull World world, int x, int groundY, int z,
                                                float scale, boolean isBoss, boolean isElite) {
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            return true; // Can't check — assume clear
        }

        // Base footprint: 2 blocks wide, 2 blocks tall (all mobs are wider than 1 block)
        int baseSize = 2;
        int scaledSize = Math.max(baseSize, (int) Math.ceil(baseSize * scale));
        int halfXZ = scaledSize / 2;  // Horizontal radius from center

        // Check all blocks in the footprint volume
        for (int dx = -halfXZ; dx < halfXZ; dx++) {
            for (int dz = -halfXZ; dz < halfXZ; dz++) {
                for (int dy = 0; dy < scaledSize; dy++) {
                    BlockType block = world.getBlockType(x + dx, groundY + dy, z + dz);
                    if (block != null && block.getMaterial() == BlockMaterial.Solid) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Checks if an area is already occupied by non-terrain solid blocks (props, structures).
     * <p>
     * Samples blocks in a grid across the footprint at ground level and one block above.
     * If more than 30% of sampled positions have non-terrain solid blocks, the area
     * is considered occupied. Used to prevent runtime structures from pasting over
     * WorldGen props (trees, rocks, other structures).
     * <p>
     * Sampling every 2 blocks keeps performance fast (~25 checks for a 10×10 area).
     *
     * @param world            The world to check in
     * @param centerX          Center X of the area to check
     * @param centerZ          Center Z of the area to check
     * @param halfWidth        Half-width of the footprint (X axis)
     * @param halfDepth        Half-depth of the footprint (Z axis)
     * @param groundY          The Y level to check at (ground surface)
     * @param terrainMaterials Biome terrain materials (blocks in this set are NOT obstructions)
     * @return true if the area is occupied (>30% non-terrain solid)
     */
    public static boolean isAreaOccupied(@Nonnull World world, int centerX, int centerZ,
                                          int halfWidth, int halfDepth, int groundY,
                                          @Nonnull Set<String> terrainMaterials) {
        int solidCount = 0;
        int totalChecked = 0;

        for (int dx = -halfWidth; dx <= halfWidth; dx += 2) {
            for (int dz = -halfDepth; dz <= halfDepth; dz += 2) {
                int bx = centerX + dx;
                int bz = centerZ + dz;

                // Check at ground level
                BlockType block = world.getBlockType(bx, groundY, bz);
                if (block != null && block.getMaterial() == BlockMaterial.Solid
                        && !isTerrainBlock(block, terrainMaterials)) {
                    solidCount++;
                }

                // Check 1 block above (tree trunks, structure walls)
                BlockType above = world.getBlockType(bx, groundY + 1, bz);
                if (above != null && above.getMaterial() == BlockMaterial.Solid
                        && !isTerrainBlock(above, terrainMaterials)) {
                    solidCount++;
                }

                totalChecked++;
            }
        }

        // >30% of sampled positions have non-terrain obstructions
        return totalChecked > 0 && (solidCount * 100 / (totalChecked * 2)) > 30;
    }
}
