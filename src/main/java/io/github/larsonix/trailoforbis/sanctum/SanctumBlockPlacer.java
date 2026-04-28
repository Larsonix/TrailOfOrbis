package io.github.larsonix.trailoforbis.sanctum;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility for placing and removing blocks in the Skill Sanctum world.
 *
 * <p>Used by {@link SkillSanctumConnectionRenderer} to render connections
 * as colored glowing blocks instead of laser beam packets. Block-based
 * rendering has no client-side cap (unlike laser/debug/particle methods
 * which cap at 40-50 visible elements).
 *
 * <h3>Block Placement Strategy</h3>
 * <p>Connections are rendered using 3D Bresenham line rasterization:
 * each connection becomes a series of blocks along the integer grid
 * between two floating-point node positions. The sanctum is a private
 * void-world instance, so block placement is safe and isolated.
 *
 * <h3>Thread Safety</h3>
 * <p>All block placement/removal methods must be called from the world
 * thread via {@code world.execute()}. The public methods do NOT dispatch
 * to the world thread — callers are responsible for ensuring correct context.
 *
 * @see SkillSanctumConnectionRenderer
 */
public class SanctumBlockPlacer {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Cache of resolved BlockType instances by asset name. */
    private final Map<String, BlockType> blockTypeCache = new HashMap<>();

    /** Resolved air block type for removal. */
    @Nullable
    private BlockType airBlockType;

    // ═══════════════════════════════════════════════════════════════════
    // BLOCK TYPE RESOLUTION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Resolves a {@link BlockType} from its asset name, with caching.
     *
     * @param assetName The block asset name (e.g., "Connection_Fire")
     * @return The resolved BlockType, or null if not found
     */
    @Nullable
    public BlockType resolveBlockType(@Nonnull String assetName) {
        BlockType cached = blockTypeCache.get(assetName);
        if (cached != null) {
            return cached;
        }

        BlockType blockType = BlockType.getAssetMap().getAsset(assetName);
        if (blockType == null || blockType.isUnknown()) {
            LOGGER.atWarning().log("Block type not found: %s", assetName);
            return null;
        }

        blockTypeCache.put(assetName, blockType);
        return blockType;
    }

    /**
     * Gets the air block type for removing blocks.
     *
     * @return The air BlockType, or null if not found
     */
    @Nullable
    private BlockType getAirBlockType() {
        if (airBlockType == null) {
            airBlockType = BlockType.getAssetMap().getAsset("hytale:air");
            if (airBlockType == null) {
                LOGGER.atWarning().log("Could not resolve air block type");
            }
        }
        return airBlockType;
    }

    // ═══════════════════════════════════════════════════════════════════
    // SINGLE BLOCK OPERATIONS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Places a single block at the given world coordinates.
     *
     * <p><b>Must be called on the world thread.</b>
     *
     * @param world     The world to place the block in
     * @param x         Block X coordinate
     * @param y         Block Y coordinate (0-319)
     * @param z         Block Z coordinate
     * @param blockType The block type to place
     * @return true if the block was placed successfully
     */
    public boolean placeBlock(@Nonnull World world, int x, int y, int z, @Nonnull BlockType blockType) {
        if (y < 0 || y > 319) {
            return false;
        }

        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            return false;
        }

        try {
            chunk.setBlock(x, y, z, blockType.getId());
            return true;
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Error placing block at (%d, %d, %d)", x, y, z);
            return false;
        }
    }

    /**
     * Removes a single block (sets to air) at the given world coordinates.
     *
     * <p><b>Must be called on the world thread.</b>
     *
     * @param world The world to remove the block from
     * @param x     Block X coordinate
     * @param y     Block Y coordinate
     * @param z     Block Z coordinate
     * @return true if the block was removed successfully
     */
    public boolean removeBlock(@Nonnull World world, int x, int y, int z) {
        BlockType air = getAirBlockType();
        if (air == null) {
            return false;
        }
        return placeBlock(world, x, y, z, air);
    }

    // ═══════════════════════════════════════════════════════════════════
    // LINE OPERATIONS (3D Bresenham)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Computes block positions along a 3D Bresenham line between two points.
     *
     * <p>This performs integer rasterization of a line segment in 3D space.
     * The result includes the start and end block positions, with no gaps.
     *
     * @param start Start position (floating-point world coordinates)
     * @param end   End position (floating-point world coordinates)
     * @return List of (x, y, z) block positions along the line
     */
    @Nonnull
    public static List<int[]> computeBresenhamLine(@Nonnull Vector3d start, @Nonnull Vector3d end) {
        int x0 = (int) Math.floor(start.x);
        int y0 = (int) Math.floor(start.y);
        int z0 = (int) Math.floor(start.z);
        int x1 = (int) Math.floor(end.x);
        int y1 = (int) Math.floor(end.y);
        int z1 = (int) Math.floor(end.z);

        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int dz = Math.abs(z1 - z0);

        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int sz = z0 < z1 ? 1 : -1;

        // Dominant axis determines step count
        int maxSteps = Math.max(dx, Math.max(dy, dz));
        List<int[]> positions = new ArrayList<>(maxSteps + 1);

        if (maxSteps == 0) {
            positions.add(new int[]{x0, y0, z0});
            return positions;
        }

        // 3D Bresenham using error accumulation
        int errXY = dx - dy;
        int errXZ = dx - dz;
        int errYZ = dy - dz;

        int x = x0, y = y0, z = z0;

        for (int i = 0; i <= maxSteps; i++) {
            positions.add(new int[]{x, y, z});

            if (x == x1 && y == y1 && z == z1) {
                break;
            }

            int errXY2 = errXY * 2;
            int errXZ2 = errXZ * 2;
            int errYZ2 = errYZ * 2;

            // Step along the axis with greatest accumulated error
            boolean stepX = false, stepY = false, stepZ = false;

            if (errXY2 > -dy && errXZ2 > -dz) {
                stepX = true;
            }
            if (errXY2 < dx && errYZ2 > -dz) {
                stepY = true;
            }
            if (errXZ2 < dx && errYZ2 < dy) {
                stepZ = true;
            }

            if (stepX) {
                errXY -= dy;
                errXZ -= dz;
                x += sx;
            }
            if (stepY) {
                errXY += dx;
                errYZ -= dz;
                y += sy;
            }
            if (stepZ) {
                errXZ += dx;
                errYZ += dy;
                z += sz;
            }
        }

        return positions;
    }

    /**
     * Computes block positions for a dashed line (blocks with gaps).
     *
     * <p>Alternates between "dash" segments of {@code dashLength} blocks
     * and "gap" segments of {@code gapLength} blocks along the Bresenham line.
     *
     * @param start      Start position
     * @param end        End position
     * @param dashLength Number of blocks per dash segment
     * @param gapLength  Number of blocks per gap segment
     * @return List of block positions (only the "on" segments)
     */
    @Nonnull
    public static List<int[]> computeDashedLine(
            @Nonnull Vector3d start, @Nonnull Vector3d end,
            int dashLength, int gapLength) {

        List<int[]> fullLine = computeBresenhamLine(start, end);
        List<int[]> dashedPositions = new ArrayList<>();

        int cycleLength = dashLength + gapLength;
        for (int i = 0; i < fullLine.size(); i++) {
            int posInCycle = i % cycleLength;
            if (posInCycle < dashLength) {
                dashedPositions.add(fullLine.get(i));
            }
        }

        return dashedPositions;
    }

    /**
     * Places blocks along a line between two positions.
     *
     * <p><b>Must be called on the world thread.</b>
     *
     * @param world     The world to place blocks in
     * @param positions Pre-computed block positions
     * @param blockType The block type to place
     * @return Number of blocks actually placed
     */
    public int placeBlocks(
            @Nonnull World world,
            @Nonnull List<int[]> positions,
            @Nonnull BlockType blockType) {

        int placed = 0;
        for (int[] pos : positions) {
            if (placeBlock(world, pos[0], pos[1], pos[2], blockType)) {
                placed++;
            }
        }
        return placed;
    }

    /**
     * Removes blocks at the given positions (sets to air).
     *
     * <p><b>Must be called on the world thread.</b>
     *
     * @param world     The world to remove blocks from
     * @param positions Block positions to clear
     * @return Number of blocks actually removed
     */
    public int removeBlocks(@Nonnull World world, @Nonnull List<int[]> positions) {
        BlockType air = getAirBlockType();
        if (air == null) {
            return 0;
        }

        int removed = 0;
        for (int[] pos : positions) {
            if (placeBlock(world, pos[0], pos[1], pos[2], air)) {
                removed++;
            }
        }
        return removed;
    }

    /**
     * Clears the block type cache. Call when shutting down or when
     * assets may have changed.
     */
    public void clearCache() {
        blockTypeCache.clear();
        airBlockType = null;
    }
}
