package io.github.larsonix.trailoforbis.maps.spawning;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.AssetModule;
import com.hypixel.hytale.assetstore.AssetPack;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.modules.prefabspawner.PrefabSpawnerBlock;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferUtil;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.PrefabBuffer;
import com.hypixel.hytale.server.core.util.PrefabUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import io.github.larsonix.trailoforbis.maps.core.RealmBiomeType;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;
import io.github.larsonix.trailoforbis.maps.templates.RealmTemplateRegistry;
import io.github.larsonix.trailoforbis.sanctum.SanctumBlockPlacer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates a labyrinth of Goblin_Lair corridors carved from solid terrain for the
 * MOUNTAINS biome. Unlike {@link RealmStructurePlacer} which scatters structures ON
 * terrain, this system carves playable space INTO solid stone using {@code force=true}.
 *
 * <p>Architecture:
 * <ul>
 *   <li><b>Phase 1 — Main Spine</b>: Guaranteed linear path from entry to boss room</li>
 *   <li><b>Phase 2 — Side Branches</b>: Probabilistic corridors/rooms from junctions</li>
 *   <li><b>Phase 3 — Paste</b>: All pieces carved with {@code force=true}, selective spawner expansion</li>
 *   <li><b>Phase 4 — Validate</b>: Minimum piece count, boss room check, retry on failure</li>
 * </ul>
 *
 * <p>Outputs a {@link LabyrinthResult} containing valid mob spawn positions (inside carved
 * corridors) for {@link RealmMobSpawner} to use instead of terrain scanning.
 *
 * @see <a href="docs/WorldgenV2/LABYRINTH_GENERATION_SYSTEM.md">Design Document</a>
 */
public class RealmLabyrinthPlacer {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // ═══════════════════════════════════════════════════════════════════
    // CONSTANTS
    // ═══════════════════════════════════════════════════════════════════

    /** Maximum attempts to generate a valid labyrinth before falling back. */
    private static final int MAX_GENERATION_ATTEMPTS = 3;

    /** Maximum spawner expansion depth (matches RealmStructurePlacer). */
    private static final int MAX_EXPANSION_DEPTH = 3;

    /** Keep labyrinth within this fraction of arena radius. */
    private static final double WALL_MARGIN = 0.80;

    /** Minimum distance from arena center where labyrinth starts (spawn clearing). */
    private static final int SPAWN_CLEARING_RADIUS = 8;

    /** Standard corridor width assumption for grid cell sizing. */
    private static final int GRID_CELL_SIZE = 10;

    /** Width of connecting corridors carved between pieces (in blocks). */
    private static final int CORRIDOR_WIDTH = 4;

    /** Height of connecting corridors carved between pieces (in blocks). */
    private static final int CORRIDOR_HEIGHT = 5;

    /** Spawner paths that should be expanded (decorative content inside rooms). */
    private static final Set<String> EXPAND_WHITELIST = Set.of(
        "Houses", "Mushrooms", "Decorations", "Torches"
    );

    private static final Rotation[] ROTATIONS = {
        Rotation.None, Rotation.Ninety, Rotation.OneEighty, Rotation.TwoSeventy
    };

    // ═══════════════════════════════════════════════════════════════════
    // RESULT RECORD
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Output of labyrinth generation. Contains everything the mob spawner needs.
     *
     * @param spawnPositions  valid mob spawn points inside carved corridors (air + solid floor)
     * @param bossRoomCenter  boss spawn location (center of boss room piece)
     * @param totalPieces     number of pieces placed
     * @param success         false if fell back to open chamber (labyrinth generation failed)
     */
    public record LabyrinthResult(
        @Nonnull List<Vector3i> spawnPositions,
        @Nonnull Vector3i bossRoomCenter,
        int totalPieces,
        boolean success
    ) {}

    // ═══════════════════════════════════════════════════════════════════
    // PIECE DEFINITIONS
    // ═══════════════════════════════════════════════════════════════════

    /** Category determines how a piece is used in generation. */
    private enum PieceCategory {
        CORRIDOR,   // Straight passage connecting two points
        JUNCTION,   // T-junction, corner, or shift — creates branching
        ROOM,       // Large open chamber (beast, large)
        BOSS_ROOM,  // Boss encounter arena (spine terminus)
        CAP,        // Dead end terminator
        TREASURE,   // Hidden treasure dead end
        HOUSE       // Goblin residential structure
    }

    /** Cardinal direction for piece placement. */
    private enum Direction {
        NORTH(0, -1), SOUTH(0, 1), EAST(1, 0), WEST(-1, 0);

        final int dx, dz;
        Direction(int dx, int dz) { this.dx = dx; this.dz = dz; }

        Direction opposite() {
            return switch (this) {
                case NORTH -> SOUTH;
                case SOUTH -> NORTH;
                case EAST -> WEST;
                case WEST -> EAST;
            };
        }

        Direction turnLeft() {
            return switch (this) {
                case NORTH -> WEST;
                case WEST -> SOUTH;
                case SOUTH -> EAST;
                case EAST -> NORTH;
            };
        }

        Direction turnRight() {
            return switch (this) {
                case NORTH -> EAST;
                case EAST -> SOUTH;
                case SOUTH -> WEST;
                case WEST -> NORTH;
            };
        }
    }

    /** Metadata for a single labyrinth piece. Loaded at startup from actual prefabs. */
    private record PieceMetadata(
        String dirPath,          // Prefab directory path
        PieceCategory category,
        int sizeX,               // Bounding box dimensions
        int sizeY,
        int sizeZ,
        int longestAxis          // 0=X, 2=Z — corridor alignment direction
    ) {}

    /** A placed piece in the labyrinth. */
    private record PlacedPiece(
        PieceMetadata metadata,
        int worldX,              // World position (paste origin)
        int worldY,
        int worldZ,
        Direction facing,        // Direction this piece extends from parent
        Rotation rotation        // Rotation applied to match facing
    ) {}

    // ═══════════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════════

    private final ConcurrentHashMap<String, PrefabBuffer> prefabCache = new ConcurrentHashMap<>();
    private final StructureBoundsRegistry boundsRegistry;

    // Piece pools (populated on first use from scanned prefab directories)
    private volatile List<PieceMetadata> corridorPieces;
    private volatile List<PieceMetadata> junctionPieces;
    private volatile List<PieceMetadata> roomPieces;
    private volatile List<PieceMetadata> capPieces;
    private volatile List<PieceMetadata> treasurePieces;
    private volatile List<PieceMetadata> housePieces;
    private volatile PieceMetadata bossRoomPiece;

    public RealmLabyrinthPlacer(@Nonnull StructureBoundsRegistry boundsRegistry) {
        this.boundsRegistry = boundsRegistry;
    }

    // ═══════════════════════════════════════════════════════════════════
    // MAIN ENTRY POINT
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Generate a labyrinth for the given realm instance. Called after chunks load
     * but before mob spawning — same lifecycle position as
     * {@link RealmStructurePlacer#placeStructures}.
     *
     * @param world the realm world (on world thread)
     * @param realm the realm instance with map data
     * @return labyrinth result containing spawn positions and boss room center
     */
    @Nonnull
    public LabyrinthResult generate(@Nonnull World world, @Nonnull RealmInstance realm) {
        ensurePiecesLoaded();

        double arenaRadius = realm.getMapData().computeArenaRadius();
        int baseY = (int) RealmTemplateRegistry.getBaseYForBiome(realm.getBiome());
        UUID realmId = realm.getRealmId();
        String realmIdShort = realmId.toString().substring(0, 8);

        LOGGER.atInfo().log("Generating labyrinth for realm %s (radius=%.0f, baseY=%d)",
            realmIdShort, arenaRadius, baseY);

        for (int attempt = 1; attempt <= MAX_GENERATION_ATTEMPTS; attempt++) {
            Random random = new Random(realm.getMapData().hashCode() + attempt * 31L);

            List<PlacedPiece> pieces = generateLayout(arenaRadius, baseY, random);

            // Validate
            int minPieces = computeMinPieces(arenaRadius);
            boolean hasBoss = pieces.stream()
                .anyMatch(p -> p.metadata.category == PieceCategory.BOSS_ROOM);

            if (pieces.size() >= minPieces && hasBoss) {
                // Paste all pieces
                LabyrinthResult result = pasteLabyrinth(world, realm, pieces, baseY, random);
                LOGGER.atInfo().log("Labyrinth generated for realm %s: %d pieces, %d spawn positions (attempt %d)",
                    realmIdShort, result.totalPieces(), result.spawnPositions().size(), attempt);
                return result;
            }

            LOGGER.atWarning().log("Labyrinth attempt %d/%d failed: %d pieces (need %d), boss=%b",
                attempt, MAX_GENERATION_ATTEMPTS, pieces.size(), minPieces, hasBoss);
        }

        // All attempts failed — fall back to open chamber
        LOGGER.atWarning().log("Labyrinth generation failed for realm %s — falling back to open chamber",
            realmIdShort);
        return createFallbackResult(baseY);
    }

    // ═══════════════════════════════════════════════════════════════════
    // LAYOUT GENERATION (Phase 1 + 2)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Generate the labyrinth layout using breadth-first flood fill.
     *
     * <p>Instead of a single-direction spine, grows outward from center in ALL directions
     * simultaneously. Each growth front can turn, split, and dead-end naturally.
     * The boss room is pre-placed at a guaranteed distance, and the flood fill
     * naturally reaches it. This fills the arena volume properly regardless of size.
     *
     * <p>Growth fronts are processed in a queue (BFS). Each front steps forward one cell,
     * places a piece, then optionally forks into 1-2 perpendicular fronts. This creates
     * an organic branching labyrinth that spreads evenly through the arena.
     */
    @Nonnull
    private List<PlacedPiece> generateLayout(double arenaRadius, int baseY, @Nonnull Random random) {
        List<PlacedPiece> pieces = new ArrayList<>();
        Set<Long> occupiedCells = new HashSet<>();
        double maxExtent = arenaRadius * WALL_MARGIN;
        int targetPieces = computeMinPieces(arenaRadius) + 10; // Aim above minimum

        // ─── Boss room: placed first at guaranteed position ───
        Direction bossDir = Direction.values()[random.nextInt(4)];
        int bossDistance = (int) (maxExtent * 0.55);
        int bossX = bossDir.dx * bossDistance;
        int bossZ = bossDir.dz * bossDistance;
        if (bossRoomPiece != null) {
            pieces.add(new PlacedPiece(bossRoomPiece, bossX, baseY, bossZ, bossDir,
                getRotationForDirection(bossDir)));
            occupiedCells.add(cellKey(bossX, bossZ));
        }

        // ─── Flood fill: BFS from center in all 4 directions ───
        // Each growth front is (x, z, direction). The queue processes them breadth-first,
        // ensuring even spread rather than one long corridor.
        record GrowthFront(int x, int z, Direction dir, int depth) {}

        Deque<GrowthFront> queue = new ArrayDeque<>();

        // Seed 4 initial growth fronts from spawn clearing edge
        for (Direction dir : Direction.values()) {
            queue.add(new GrowthFront(
                dir.dx * GRID_CELL_SIZE,
                dir.dz * GRID_CELL_SIZE,
                dir, 0));
        }

        int maxDepth = computeSpineLength(arenaRadius) + 3; // Max corridor length per branch

        while (!queue.isEmpty() && pieces.size() < targetPieces) {
            GrowthFront front = queue.poll();

            // Bounds check
            double dist = Math.sqrt(front.x * front.x + front.z * front.z);
            if (dist > maxExtent) continue;
            if (front.depth > maxDepth) continue;

            // Cell overlap check
            long cellKey = cellKey(front.x, front.z);
            if (occupiedCells.contains(cellKey)) continue;

            // Pick piece type based on context
            PieceMetadata piece;
            double roll = random.nextDouble();

            if (roll < 0.08 && !roomPieces.isEmpty()) {
                // 8% chance: combat room
                piece = pickRandom(roomPieces, random);
            } else if (roll < 0.15 && !treasurePieces.isEmpty() && front.depth > 3) {
                // 7% chance (deep only): treasure dead end — don't continue this front
                piece = pickRandom(treasurePieces, random);
                pieces.add(new PlacedPiece(piece, front.x, baseY, front.z, front.dir,
                    getRotationForDirection(front.dir)));
                occupiedCells.add(cellKey);
                continue; // Dead end — no further growth
            } else {
                piece = pickRandom(corridorPieces, random);
            }

            if (piece == null) continue;

            // Place piece
            pieces.add(new PlacedPiece(piece, front.x, baseY, front.z, front.dir,
                getRotationForDirection(front.dir)));
            occupiedCells.add(cellKey);

            // Continue forward (always — main corridor extension)
            queue.add(new GrowthFront(
                front.x + front.dir.dx * GRID_CELL_SIZE,
                front.z + front.dir.dz * GRID_CELL_SIZE,
                front.dir, front.depth + 1));

            // Fork: 35% chance to branch left or right (creates junction)
            if (random.nextDouble() < 0.35) {
                Direction forkDir = random.nextBoolean()
                    ? front.dir.turnLeft() : front.dir.turnRight();
                queue.add(new GrowthFront(
                    front.x + forkDir.dx * GRID_CELL_SIZE,
                    front.z + forkDir.dz * GRID_CELL_SIZE,
                    forkDir, front.depth + 1));
            }

            // Rare second fork: 10% chance to branch the other way too
            if (random.nextDouble() < 0.10) {
                Direction forkDir2 = random.nextBoolean()
                    ? front.dir.turnLeft() : front.dir.turnRight();
                queue.add(new GrowthFront(
                    front.x + forkDir2.dx * GRID_CELL_SIZE,
                    front.z + forkDir2.dz * GRID_CELL_SIZE,
                    forkDir2, front.depth + 1));
            }
        }

        return pieces;
    }

    // ═══════════════════════════════════════════════════════════════════
    // PASTING (Phase 3)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Paste all layout pieces into the world with force=true, carve connecting
     * corridors between them, and carve a spawn clearing at center.
     */
    @Nonnull
    private LabyrinthResult pasteLabyrinth(@Nonnull World world, @Nonnull RealmInstance realm,
                                            @Nonnull List<PlacedPiece> pieces, int baseY,
                                            @Nonnull Random random) {
        List<Vector3i> spawnPositions = new ArrayList<>();
        Vector3i bossRoomCenter = new Vector3i(0, baseY, 0);
        int pastedCount = 0;

        BlockType airBlock = BlockType.getAssetMap().getAsset("Empty");
        SanctumBlockPlacer blockPlacer = new SanctumBlockPlacer();

        // ─── Step 1: Carve spawn clearing at arena center ───
        // Cylindrical air pocket: radius 8, height 6 blocks (baseY to baseY+5)
        carveVolume(world, blockPlacer, airBlock, 0, baseY, 0, 8, 6);
        spawnPositions.add(new Vector3i(0, baseY + 1, 0));

        // ─── Step 2: Paste each labyrinth piece ───
        for (PlacedPiece placed : pieces) {
            boolean success = pastePiece(world, placed, random);
            if (success) {
                pastedCount++;
                spawnPositions.add(new Vector3i(placed.worldX, baseY + 1, placed.worldZ));

                if (placed.metadata.category == PieceCategory.BOSS_ROOM) {
                    bossRoomCenter = new Vector3i(placed.worldX, baseY + 1, placed.worldZ);
                }

                int halfSize = GRID_CELL_SIZE / 2;
                boundsRegistry.register(realm.getRealmId(),
                    placed.worldX - halfSize, placed.worldZ - halfSize,
                    placed.worldX + halfSize, placed.worldZ + halfSize);
            }
        }

        // ─── Step 3: Carve connecting corridors between consecutive pieces ───
        // Each piece connects to the next via a straight corridor carved through stone.
        // Also connect first piece to spawn clearing at (0, baseY, 0).
        if (!pieces.isEmpty()) {
            // Connect spawn clearing to first piece
            PlacedPiece first = pieces.get(0);
            carveCorridor(world, blockPlacer, airBlock,
                0, baseY, 0,
                first.worldX, baseY, first.worldZ,
                CORRIDOR_WIDTH, CORRIDOR_HEIGHT);
        }

        for (int i = 0; i < pieces.size() - 1; i++) {
            PlacedPiece from = pieces.get(i);
            PlacedPiece to = pieces.get(i + 1);
            carveCorridor(world, blockPlacer, airBlock,
                from.worldX, baseY, from.worldZ,
                to.worldX, baseY, to.worldZ,
                CORRIDOR_WIDTH, CORRIDOR_HEIGHT);
        }

        LOGGER.atFine().log("Carved %d connecting corridors + spawn clearing",
            pieces.size());

        return new LabyrinthResult(
            Collections.unmodifiableList(spawnPositions),
            bossRoomCenter,
            pastedCount,
            true
        );
    }

    /**
     * Paste a single labyrinth piece into the world with force=true.
     * Air blocks in the prefab CARVE corridors from solid terrain.
     */
    private boolean pastePiece(@Nonnull World world, @Nonnull PlacedPiece placed,
                                @Nonnull Random random) {
        try {
            List<Path> prefabFiles = findPrefabFilesInDirectory(placed.metadata.dirPath);
            if (prefabFiles.isEmpty()) {
                LOGGER.atFine().log("No prefab files in directory: %s", placed.metadata.dirPath);
                return false;
            }

            Path chosen = prefabFiles.get(random.nextInt(prefabFiles.size()));
            PrefabBuffer buffer = loadPrefabFile(chosen);
            if (buffer == null) return false;

            IPrefabBuffer accessor = buffer.newAccess();
            Vector3i pastePos = new Vector3i(placed.worldX, placed.worldY, placed.worldZ);

            // FORCE=TRUE: Air blocks in prefab overwrite solid terrain, carving corridors.
            // This is the fundamental difference from RealmStructurePlacer (force=false).
            PrefabUtil.paste(
                accessor, world,
                pastePos,
                placed.rotation,
                true,                // force=true: AIR CARVES INTO STONE
                random,
                0, false, false,
                false,               // loadEntities: NO vanilla NPCs
                world.getEntityStore().getStore()
            );

            // Selective spawner expansion: only expand decorative children
            selectiveExpandSpawnerBlocks(world, pastePos, accessor, random, 0);

            // Clear any waterlogged blocks
            clearWaterloggedBlocks(world, pastePos, accessor);

            return true;
        } catch (Throwable t) {
            LOGGER.atWarning().withCause(t).log("Failed to paste labyrinth piece %s at (%d,%d,%d)",
                placed.metadata.dirPath, placed.worldX, placed.worldY, placed.worldZ);
            return false;
        }
    }

    /**
     * Expand PrefabSpawnerBlocks ONLY for decorative paths (Houses, Mushrooms).
     * Strip all corridor/room/junction spawners — the labyrinth controls layout.
     */
    private void selectiveExpandSpawnerBlocks(@Nonnull World world, @Nonnull Vector3i pastePos,
                                               @Nonnull IPrefabBuffer buffer, @Nonnull Random random,
                                               int depth) {
        if (depth >= MAX_EXPANSION_DEPTH) return;

        int minX = buffer.getMinX() + pastePos.x;
        int maxX = buffer.getMaxX() + pastePos.x;
        int minY = buffer.getMinY() + pastePos.y;
        int maxY = buffer.getMaxY() + pastePos.y;
        int minZ = buffer.getMinZ() + pastePos.z;
        int maxZ = buffer.getMaxZ() + pastePos.z;

        SanctumBlockPlacer blockPlacer = new SanctumBlockPlacer();
        BlockType airBlock = BlockType.getAssetMap().getAsset("Empty");

        for (int wx = minX; wx <= maxX; wx++) {
            for (int wz = minZ; wz <= maxZ; wz++) {
                WorldChunk chunk = world.getChunkIfInMemory(
                    ChunkUtil.indexChunkFromBlock(wx, wz));
                if (chunk == null) continue;

                for (int wy = minY; wy <= maxY; wy++) {
                    try {
                        Ref<ChunkStore> blockRef = chunk.getBlockComponentEntity(wx, wy, wz);
                        if (blockRef == null) continue;

                        PrefabSpawnerBlock spawner = blockRef.getStore()
                            .getComponent(blockRef, PrefabSpawnerBlock.getComponentType());
                        if (spawner == null) continue;

                        String dotPath = spawner.getPrefabPath();
                        if (dotPath == null || dotPath.isEmpty()) {
                            blockPlacer.placeBlock(world, wx, wy, wz, airBlock);
                            continue;
                        }

                        String slashPath = dotPath.replace('.', '/');

                        // Check whitelist: only expand decorative spawners
                        boolean isDecorative = EXPAND_WHITELIST.stream()
                            .anyMatch(slashPath::contains);

                        if (isDecorative) {
                            // Expand this decorative child (houses, mushrooms, etc.)
                            pastePrefabFromDirectoryUnchecked(world, slashPath, wx, wy, wz, random);
                        }

                        // Always strip the spawner block (replace with air)
                        blockPlacer.placeBlock(world, wx, wy, wz, airBlock);

                    } catch (Throwable e) {
                        LOGGER.atFine().log("Error processing spawner block at (%d,%d,%d): %s", wx, wy, wz, e.getMessage());
                    }
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CORRIDOR CARVING (set blocks to air between labyrinth pieces)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Carve a cylindrical volume of air at a position. Used for spawn clearing.
     *
     * @param radius horizontal radius in blocks
     * @param height vertical height in blocks (from baseY upward)
     */
    private void carveVolume(@Nonnull World world, @Nonnull SanctumBlockPlacer blockPlacer,
                              @Nonnull BlockType airBlock,
                              int centerX, int baseY, int centerZ, int radius, int height) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                // Circular shape
                if (dx * dx + dz * dz > radius * radius) continue;

                for (int dy = 0; dy < height; dy++) {
                    try {
                        blockPlacer.placeBlock(world,
                            centerX + dx, baseY + dy, centerZ + dz, airBlock);
                    } catch (Throwable e) {
                        LOGGER.atFine().log("Failed to carve block at (%d,%d,%d): %s", centerX + dx, baseY + dy, centerZ + dz, e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Carve a straight corridor of air between two points. Walks from (x1,z1) to (x2,z2)
     * at the given Y, clearing a rectangular cross-section (width × height) along the path.
     * Uses Bresenham-style stepping — same proven pattern as BossStructurePlacer dirt paths.
     */
    private void carveCorridor(@Nonnull World world, @Nonnull SanctumBlockPlacer blockPlacer,
                                @Nonnull BlockType airBlock,
                                int x1, int baseY, int z1,
                                int x2, int baseY2, int z2,
                                int width, int height) {
        int halfW = width / 2;

        // Step along the longest axis
        int dx = x2 - x1;
        int dz = z2 - z1;
        int steps = Math.max(Math.abs(dx), Math.abs(dz));
        if (steps == 0) return;

        float stepX = (float) dx / steps;
        float stepZ = (float) dz / steps;

        for (int s = 0; s <= steps; s++) {
            int cx = x1 + Math.round(stepX * s);
            int cz = z1 + Math.round(stepZ * s);

            // Clear a width × height cross-section perpendicular to the path
            for (int wx = -halfW; wx <= halfW; wx++) {
                for (int wz = -halfW; wz <= halfW; wz++) {
                    for (int wy = 0; wy < height; wy++) {
                        try {
                            blockPlacer.placeBlock(world,
                                cx + wx, baseY + wy, cz + wz, airBlock);
                        } catch (Throwable e) {
                            LOGGER.atFine().log("Failed to carve corridor block at (%d,%d,%d): %s", cx + wx, baseY + wy, cz + wz, e.getMessage());
                        }
                    }
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // PREFAB LOADING (mirrors RealmStructurePlacer pattern)
    // ═══════════════════════════════════════════════════════════════════

    @Nonnull
    private List<Path> findPrefabFilesInDirectory(@Nonnull String dirPath) {
        List<Path> found = new ArrayList<>();
        try {
            List<AssetPack> packs = AssetModule.get().getAssetPacks();
            for (int i = packs.size() - 1; i >= 0; --i) {
                Path prefabsDir = packs.get(i).getRoot()
                    .resolve("Server").resolve("Prefabs").resolve(dirPath);
                if (!Files.isDirectory(prefabsDir)) continue;

                try (var stream = Files.list(prefabsDir)) {
                    stream.filter(p -> {
                        String name = p.getFileName().toString();
                        return name.endsWith(".prefab.json") || name.endsWith(".prefab.json.lpf");
                    }).forEach(found::add);
                }

                if (!found.isEmpty()) break;
            }
        } catch (Throwable t) {
            LOGGER.atFine().log("Error scanning directory %s: %s", dirPath, t.getMessage());
        }
        return found;
    }

    @Nullable
    private PrefabBuffer loadPrefabFile(@Nonnull Path path) {
        String key = path.toString();
        try {
            return prefabCache.computeIfAbsent(key, k -> {
                try {
                    Path loadPath = path;
                    if (k.endsWith(".lpf")) {
                        String jsonName = k.substring(0, k.length() - 4);
                        loadPath = Path.of(jsonName);
                    }
                    return PrefabBufferUtil.loadBuffer(loadPath);
                } catch (Throwable e) {
                    LOGGER.atFine().log("Failed to load prefab: %s", k);
                    return null;
                }
            });
        } catch (Throwable e) {
            LOGGER.atFine().log("Failed to load prefab %s: %s", key, e.getMessage());
            return null;
        }
    }

    /**
     * Paste without overlap checks — for spawner expansion children.
     */
    private boolean pastePrefabFromDirectoryUnchecked(@Nonnull World world, @Nonnull String dirPath,
                                                       int x, int y, int z, @Nonnull Random random) {
        try {
            List<Path> prefabFiles = findPrefabFilesInDirectory(dirPath);
            if (prefabFiles.isEmpty()) return false;

            Path chosen = prefabFiles.get(random.nextInt(prefabFiles.size()));
            PrefabBuffer buffer = loadPrefabFile(chosen);
            if (buffer == null) return false;

            Rotation rotation = ROTATIONS[random.nextInt(ROTATIONS.length)];
            IPrefabBuffer accessor = buffer.newAccess();
            Vector3i pastePos = new Vector3i(x, y, z);

            // Children use force=true too (they're inside the labyrinth)
            PrefabUtil.paste(accessor, world, pastePos, rotation, true, random,
                0, false, false, false, world.getEntityStore().getStore());
            clearWaterloggedBlocks(world, pastePos, accessor);
            return true;
        } catch (Throwable e) {
            LOGGER.atFine().log("Failed to paste unchecked prefab %s at (%d,%d,%d): %s", dirPath, x, y, z, e.getMessage());
            return false;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // WATERLOG CLEARING (matches RealmStructurePlacer pattern exactly)
    // ═══════════════════════════════════════════════════════════════════

    private void clearWaterloggedBlocks(@Nonnull World world, @Nonnull Vector3i pastePos,
                                         @Nonnull IPrefabBuffer buffer) {
        // Match RealmStructurePlacer's proven pattern exactly — scan with margins
        int half = Math.max(
            Math.max(Math.abs(buffer.getMinX()), Math.abs(buffer.getMaxX())),
            Math.max(Math.abs(buffer.getMinZ()), Math.abs(buffer.getMaxZ()))
        ) + 20;
        int halfY = Math.max(Math.abs(buffer.getMinY()), Math.abs(buffer.getMaxY())) + 20;

        var chunkStore = world.getChunkStore();
        BlockType emptyBlock = BlockType.getAssetMap().getAsset("Empty");

        for (int wx = pastePos.x - half; wx <= pastePos.x + half; wx++) {
            for (int wz = pastePos.z - half; wz <= pastePos.z + half; wz++) {
                WorldChunk chunk = world.getChunkIfInMemory(
                    ChunkUtil.indexChunkFromBlock(wx, wz));
                if (chunk == null) continue;

                for (int wy = pastePos.y - halfY; wy <= pastePos.y + halfY; wy++) {
                    try {
                        int fluidId = chunk.getFluidId(wx, wy, wz);
                        if (fluidId == 0) continue;

                        BlockType block = world.getBlockType(wx, wy, wz);
                        if (block == null || block == emptyBlock) continue;

                        // Non-Empty block + fluid = waterlogged → clear fluid
                        Ref<ChunkStore> section = chunkStore.getChunkSectionReference(
                            ChunkUtil.chunkCoordinate(wx),
                            ChunkUtil.chunkCoordinate(wy),
                            ChunkUtil.chunkCoordinate(wz));
                        if (section == null || !section.isValid()) continue;

                        FluidSection fluidSection = section.getStore()
                            .getComponent(section, FluidSection.getComponentType());
                        if (fluidSection != null) {
                            fluidSection.setFluid(wx, wy, wz, 0, (byte) 0);
                        }
                    } catch (Throwable e) {
                        LOGGER.atFine().log("Failed to clear waterlogged block at (%d,%d,%d): %s", wx, wy, wz, e.getMessage());
                    }
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // PIECE LOADING & CATEGORIZATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Scan Goblin_Lair prefab directories and categorize pieces.
     * Called once on first use (lazy initialization, thread-safe via volatile).
     */
    private void ensurePiecesLoaded() {
        if (corridorPieces != null) return;

        synchronized (this) {
            if (corridorPieces != null) return;

            List<PieceMetadata> corridors = new ArrayList<>();
            List<PieceMetadata> junctions = new ArrayList<>();
            List<PieceMetadata> rooms = new ArrayList<>();
            List<PieceMetadata> caps = new ArrayList<>();
            List<PieceMetadata> treasures = new ArrayList<>();
            List<PieceMetadata> houses = new ArrayList<>();
            PieceMetadata boss = null;

            // ─── Prefabs_Mine corridors (primary corridor system) ───
            for (String lengthType : List.of("Short", "Medium", "Long")) {
                PieceMetadata pm = loadPieceMetadata(
                    "Dungeon/Goblin_Lair/Prefabs_Mine/Corridor/" + lengthType,
                    PieceCategory.CORRIDOR);
                if (pm != null) corridors.add(pm);
            }

            // ─── Mine junction types ───
            for (String jType : List.of("T", "Shift", "Side")) {
                PieceMetadata pm = loadPieceMetadata(
                    "Dungeon/Goblin_Lair/Prefabs_Mine/Corridor/" + jType,
                    PieceCategory.JUNCTION);
                if (pm != null) junctions.add(pm);
            }

            // ─── Mine dead ends ───
            PieceMetadata endPiece = loadPieceMetadata(
                "Dungeon/Goblin_Lair/Prefabs_Mine/Corridor/End", PieceCategory.CAP);
            if (endPiece != null) caps.add(endPiece);

            PieceMetadata secretEnd = loadPieceMetadata(
                "Dungeon/Goblin_Lair/Prefabs_Mine/Corridor/End_Secret", PieceCategory.TREASURE);
            if (secretEnd != null) treasures.add(secretEnd);

            // ─── Mine rooms ───
            for (String rType : List.of("End", "T", "Y")) {
                PieceMetadata pm = loadPieceMetadata(
                    "Dungeon/Goblin_Lair/Prefabs_Mine/Room/" + rType,
                    PieceCategory.ROOM);
                if (pm != null) rooms.add(pm);
            }

            // ─── Goblin City rooms (major chambers) ───
            PieceMetadata beastRoom = loadPieceMetadata(
                "Dungeon/Goblin_Lair/Prefabs_Goblin/Room/Beast", PieceCategory.ROOM);
            if (beastRoom != null) rooms.add(beastRoom);

            for (String lType : List.of("Large1", "Large2")) {
                PieceMetadata pm = loadPieceMetadata(
                    "Dungeon/Goblin_Lair/Prefabs_Goblin/Room/" + lType,
                    PieceCategory.ROOM);
                if (pm != null) rooms.add(pm);
            }

            // ─── Boss room ───
            boss = loadPieceMetadata(
                "Dungeon/Goblin_Lair/Prefabs_Goblin/Room/Boss", PieceCategory.BOSS_ROOM);

            // ─── Goblin houses ───
            PieceMetadata basaltHouses = loadPieceMetadata(
                "Dungeon/Goblin_Lair/Prefabs_Goblin/Houses/Basalt", PieceCategory.HOUSE);
            if (basaltHouses != null) houses.add(basaltHouses);

            PieceMetadata stoneHouses = loadPieceMetadata(
                "Dungeon/Goblin_Lair/Prefabs_Goblin/Houses/Stone", PieceCategory.HOUSE);
            if (stoneHouses != null) houses.add(stoneHouses);

            // ─── Goblin caps ───
            PieceMetadata deadEnd = loadPieceMetadata(
                "Dungeon/Goblin_Lair/Prefabs_Goblin/Cap/Dead_End", PieceCategory.CAP);
            if (deadEnd != null) caps.add(deadEnd);

            PieceMetadata treasureCap = loadPieceMetadata(
                "Dungeon/Goblin_Lair/Prefabs_Goblin/Cap/Hidden_Treasure", PieceCategory.TREASURE);
            if (treasureCap != null) treasures.add(treasureCap);

            // ─── Goblin corridors (backup) ───
            for (int len : List.of(7, 9, 11, 14, 24)) {
                PieceMetadata pm = loadPieceMetadata(
                    "Dungeon/Goblin_Lair/Prefabs_Goblin/Straight/Length_" + len,
                    PieceCategory.CORRIDOR);
                if (pm != null) corridors.add(pm);
            }

            // ─── Goblin junctions ───
            PieceMetadata corner = loadPieceMetadata(
                "Dungeon/Goblin_Lair/Prefabs_Goblin/Corner", PieceCategory.JUNCTION);
            if (corner != null) junctions.add(corner);

            PieceMetadata tJunction = loadPieceMetadata(
                "Dungeon/Goblin_Lair/Prefabs_Goblin/T-Junction", PieceCategory.JUNCTION);
            if (tJunction != null) junctions.add(tJunction);

            // Publish (volatile writes — thread-safe lazy init)
            this.bossRoomPiece = boss;
            this.housePieces = List.copyOf(houses);
            this.treasurePieces = List.copyOf(treasures);
            this.capPieces = List.copyOf(caps);
            this.roomPieces = List.copyOf(rooms);
            this.junctionPieces = List.copyOf(junctions);
            this.corridorPieces = List.copyOf(corridors);  // Published last (gate for null check)

            LOGGER.atInfo().log("Labyrinth pieces loaded: %d corridors, %d junctions, %d rooms, " +
                    "%d caps, %d treasures, %d houses, boss=%b",
                corridors.size(), junctions.size(), rooms.size(),
                caps.size(), treasures.size(), houses.size(), boss != null);
        }
    }

    /**
     * Load metadata for a piece by scanning its prefab directory.
     * Gets bounding box dimensions from the first available prefab file.
     */
    @Nullable
    private PieceMetadata loadPieceMetadata(@Nonnull String dirPath, @Nonnull PieceCategory category) {
        List<Path> files = findPrefabFilesInDirectory(dirPath);
        if (files.isEmpty()) {
            LOGGER.atFine().log("No prefab files found for labyrinth piece: %s", dirPath);
            return null;
        }

        PrefabBuffer buffer = loadPrefabFile(files.get(0));
        if (buffer == null) return null;

        IPrefabBuffer accessor = buffer.newAccess();
        int sizeX = accessor.getMaxX() - accessor.getMinX() + 1;
        int sizeY = accessor.getMaxY() - accessor.getMinY() + 1;
        int sizeZ = accessor.getMaxZ() - accessor.getMinZ() + 1;
        int longestAxis = sizeX >= sizeZ ? 0 : 2;

        LOGGER.atFine().log("Loaded piece %s: %dx%dx%d (category=%s, files=%d)",
            dirPath, sizeX, sizeY, sizeZ, category, files.size());

        return new PieceMetadata(dirPath, category, sizeX, sizeY, sizeZ, longestAxis);
    }

    // ═══════════════════════════════════════════════════════════════════
    // UTILITY METHODS
    // ═══════════════════════════════════════════════════════════════════

    private int computeSpineLength(double arenaRadius) {
        if (arenaRadius <= 40) return 3;
        if (arenaRadius <= 60) return 5;
        if (arenaRadius <= 80) return 7;
        if (arenaRadius <= 100) return 10;
        return 14;
    }

    private int computeMinPieces(double arenaRadius) {
        if (arenaRadius <= 40) return 5;
        if (arenaRadius <= 60) return 10;
        if (arenaRadius <= 80) return 18;
        return 25;
    }

    private int computeBranchDepth(double arenaRadius) {
        if (arenaRadius <= 50) return 1;
        if (arenaRadius <= 80) return 2;
        return 3;
    }

    private double computeBranchChance(double arenaRadius) {
        if (arenaRadius <= 40) return 0.5;
        if (arenaRadius <= 70) return 0.65;
        return 0.8;
    }

    private int getExtentInDirection(@Nonnull PieceMetadata piece, @Nonnull Direction dir) {
        // Use piece's actual size along the facing direction, or grid cell size as minimum
        int extent = (dir == Direction.EAST || dir == Direction.WEST) ? piece.sizeX : piece.sizeZ;
        return Math.max(extent, GRID_CELL_SIZE);
    }

    private Rotation getRotationForDirection(@Nonnull Direction dir) {
        return switch (dir) {
            case NORTH -> Rotation.None;
            case EAST -> Rotation.Ninety;
            case SOUTH -> Rotation.OneEighty;
            case WEST -> Rotation.TwoSeventy;
        };
    }

    private long cellKey(int x, int z) {
        int cx = Math.floorDiv(x, GRID_CELL_SIZE);
        int cz = Math.floorDiv(z, GRID_CELL_SIZE);
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    @Nullable
    private <T> T pickRandom(@Nonnull List<T> list, @Nonnull Random random) {
        if (list.isEmpty()) return null;
        return list.get(random.nextInt(list.size()));
    }

    /**
     * Fallback when labyrinth generation fails — returns center as spawn point
     * with no carving. The solid terrain remains, but mobs can still spawn at
     * the open spawn clearing area.
     */
    @Nonnull
    private LabyrinthResult createFallbackResult(int baseY) {
        Vector3i center = new Vector3i(0, baseY + 1, 0);
        return new LabyrinthResult(
            List.of(center),
            center,
            0,
            false
        );
    }

    public void shutdown() {
        prefabCache.clear();
    }
}
