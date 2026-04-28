package io.github.larsonix.trailoforbis.maps.spawning;

import com.hypixel.hytale.assetstore.AssetPack;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.AssetModule;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.modules.prefabspawner.PrefabSpawnerBlock;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferUtil;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.PrefabBuffer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.util.PrefabUtil;
import io.github.larsonix.trailoforbis.maps.core.RealmBiomeType;
import io.github.larsonix.trailoforbis.maps.templates.RealmTemplateRegistry;
import io.github.larsonix.trailoforbis.sanctum.SanctumBlockPlacer;
import io.github.larsonix.trailoforbis.util.TerrainUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

/**
 * Places boss camp structures in realm arenas — a center piece surrounded by satellite structures.
 *
 * <p>Each biome has tiered camp configurations ({@link CampTier}) that scale with arena radius:
 * small arenas get simple camps, large arenas get fortified compounds. Camp tiers define
 * center/satellite prefab pools, satellite counts, and placement distances.
 *
 * <p>Structures are placed via runtime {@link PrefabUtil#paste} (not WorldGen prefab spawners)
 * to avoid the WorldGen PrefabSpawnerBlock crash. After placement, embedded PrefabSpawnerBlocks
 * are recursively expanded and waterlogged blocks are cleared.
 *
 * @see StructureBoundsRegistry
 * @see RealmMobSpawner
 */
public class BossStructurePlacer {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // ═══════════════════════════════════════════════════════════════════
    // CAMP TIER DEFINITIONS (per-biome, radius-scaled)
    // ═══════════════════════════════════════════════════════════════════

    /** Per-biome boss camp tiers — each biome has 3 tiers scaling with arena radius */
    private static final Map<RealmBiomeType, List<CampTier>> BOSS_CAMPS;

    private static final Rotation[] ROTATIONS;
    private static final int MAX_CAMPS_PER_REALM = 3;
    private static final int MAX_EXPANSION_DEPTH = 3;
    private static final BlockType EMPTY_BLOCK;

    // ═══════════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════════

    private final Map<String, PrefabBuffer> prefabCache = new ConcurrentHashMap<>();
    private final StructureBoundsRegistry boundsRegistry;
    private final Map<UUID, Integer> realmCampCounts = new ConcurrentHashMap<>();

    public BossStructurePlacer(@Nonnull StructureBoundsRegistry boundsRegistry) {
        this.boundsRegistry = boundsRegistry;
    }

    // ═══════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Places a boss camp structure at the given position.
     *
     * <p>A camp consists of a center piece (the boss's main structure) surrounded by
     * satellite structures (watchtowers, tents, traps, etc.). The number and spread
     * of satellites depends on the {@link CampTier} matched by arena radius.
     *
     * <p>Center placement tries up to 6 jittered positions before falling back to exact center.
     * Satellites are placed at random angles/distances from center, with overlap and occupation checks.
     *
     * @return true if at least one structure piece was placed
     */
    public boolean placeStructureForBoss(@Nonnull World world, @Nonnull UUID realmId,
                                          @Nonnull RealmBiomeType biome, double arenaRadius,
                                          @Nonnull Vector3d position) {
        // Enforce camp limit per realm
        int currentCount = realmCampCounts.getOrDefault(realmId, 0);
        if (currentCount >= MAX_CAMPS_PER_REALM) {
            return false;
        }
        realmCampCounts.put(realmId, currentCount + 1);

        CampTier tier = findTier(biome, arenaRadius);
        if (tier == null) {
            return false;
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        Set<String> terrainMaterials = biome.getTerrainMaterials();
        int maxScanY = (int) RealmTemplateRegistry.getBaseYForBiome(biome) + 30;

        int centerX = (int) Math.floor(position.x);
        int centerZ = (int) Math.floor(position.z);
        int placedCount = 0;

        // ── Place center piece (try jittered positions, then exact center) ──
        String centerPath = tier.centerPool().get(random.nextInt(tier.centerPool().size()));
        boolean centerPlaced = false;

        for (int attempt = 0; attempt < 6; attempt++) {
            int tryX = centerX + (attempt == 0 ? 0 : (int) ((random.nextDouble() - 0.5) * 16.0));
            int tryZ = centerZ + (attempt == 0 ? 0 : (int) ((random.nextDouble() - 0.5) * 16.0));
            int tryGroundY = TerrainUtils.findStructureGroundLevel(world, tryX, tryZ, maxScanY, terrainMaterials, 5);

            if (pastePrefabFromDirectory(world, centerPath, tryX, tryGroundY, tryZ, random, realmId, terrainMaterials)) {
                placedCount++;
                centerPlaced = true;
                break;
            }
        }

        // Fallback: force place at exact center position
        if (!centerPlaced) {
            int fallbackY = TerrainUtils.findStructureGroundLevel(world, centerX, centerZ, maxScanY, terrainMaterials, 5);
            if (pastePrefabFromDirectory(world, centerPath, centerX, fallbackY, centerZ, random)) {
                placedCount++;
            }
        }

        // ── Place satellite structures around center ──
        int satelliteCount = tier.minSatellites() + random.nextInt(tier.maxSatellites() - tier.minSatellites() + 1);

        for (int i = 0; i < satelliteCount; i++) {
            String satPath = tier.satellitePool().get(random.nextInt(tier.satellitePool().size()));
            double angle = random.nextDouble() * 2.0 * Math.PI;
            int distance = tier.minDistance() + random.nextInt(tier.maxDistance() - tier.minDistance() + 1);
            int satX = centerX + (int) (Math.cos(angle) * distance);
            int satZ = centerZ + (int) (Math.sin(angle) * distance);
            int satGroundY = TerrainUtils.findStructureGroundLevel(world, satX, satZ, maxScanY, terrainMaterials, 4);

            if (pastePrefabFromDirectory(world, satPath, satX, satGroundY, satZ, random, realmId, terrainMaterials)) {
                placedCount++;
            }
        }

        LOGGER.atInfo().log("Boss camp: %d pieces placed for realm %s [%s R%.0f] at (%d, %d)",
                placedCount, realmId.toString().substring(0, 8), biome.name(), arenaRadius, centerX, centerZ);
        return placedCount > 0;
    }

    public void onRealmClosed(@Nonnull UUID realmId) {
        realmCampCounts.remove(realmId);
    }

    public void shutdown() {
        int cached = prefabCache.size();
        prefabCache.clear();
        realmCampCounts.clear();
        LOGGER.atInfo().log("BossStructurePlacer shut down — %d cached prefabs released", cached);
    }

    // ═══════════════════════════════════════════════════════════════════
    // PREFAB SPAWNER EXPANSION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Recursively expands PrefabSpawnerBlocks embedded in a pasted prefab.
     *
     * <p>Vanilla prefabs can contain PrefabSpawnerBlocks that reference sub-prefabs.
     * WorldGen handles these automatically, but runtime paste does not — we expand
     * them manually by loading and pasting the referenced prefab at the spawner's position,
     * then replacing the spawner block with air.
     *
     * @param depth current recursion depth (capped at {@link #MAX_EXPANSION_DEPTH})
     * @return number of spawner blocks expanded
     */
    private int expandSpawnerBlocks(@Nonnull World world, @Nonnull Vector3i pastePos,
                                    @Nonnull IPrefabBuffer buffer, @Nonnull Random random, int depth) {
        if (depth >= MAX_EXPANSION_DEPTH) {
            return 0;
        }

        int minX = buffer.getMinX() + pastePos.x;
        int maxX = buffer.getMaxX() + pastePos.x;
        int minY = buffer.getMinY() + pastePos.y;
        int maxY = buffer.getMaxY() + pastePos.y;
        int minZ = buffer.getMinZ() + pastePos.z;
        int maxZ = buffer.getMaxZ() + pastePos.z;

        SanctumBlockPlacer blockPlacer = new SanctumBlockPlacer();
        BlockType airBlock = (BlockType) BlockType.getAssetMap().getAsset("Empty");
        int expanded = 0;

        for (int wx = minX; wx <= maxX; wx++) {
            for (int wz = minZ; wz <= maxZ; wz++) {
                WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(wx, wz));
                if (chunk == null) continue;

                for (int wy = minY; wy <= maxY; wy++) {
                    try {
                        Ref blockRef = chunk.getBlockComponentEntity(wx, wy, wz);
                        if (blockRef == null) continue;
                        PrefabSpawnerBlock spawner = (PrefabSpawnerBlock) blockRef.getStore()
                                .getComponent(blockRef, PrefabSpawnerBlock.getComponentType());
                        if (spawner == null) continue;

                        String dotPath = spawner.getPrefabPath();
                        if (dotPath == null || dotPath.isEmpty()) {
                            blockPlacer.placeBlock(world, wx, wy, wz, airBlock);
                            continue;
                        }

                        // Convert dot-notation path to slash-notation for asset lookup
                        String slashPath = dotPath.replace('.', '/');
                        LOGGER.atFine().log("Expanding spawner at (%d,%d,%d): %s → %s", wx, wy, wz, dotPath, slashPath);

                        boolean childPlaced = pastePrefabFromDirectory(world, slashPath, wx, wy, wz, random);
                        if (childPlaced) {
                            expanded++;
                            // Recurse into the child prefab's spawner blocks
                            List<Path> childFiles = findPrefabFilesInDirectory(slashPath);
                            if (!childFiles.isEmpty()) {
                                PrefabBuffer childBuffer = loadPrefabFile(childFiles.get(random.nextInt(childFiles.size())));
                                if (childBuffer != null) {
                                    expanded += expandSpawnerBlocks(world, new Vector3i(wx, wy, wz),
                                            (IPrefabBuffer) childBuffer.newAccess(), random, depth + 1);
                                }
                            }
                        }
                        // Replace the spawner block with air regardless of whether child pasted
                        blockPlacer.placeBlock(world, wx, wy, wz, airBlock);
                    } catch (Exception e) {
                        LOGGER.atFine().log("Error expanding spawner at (%d,%d,%d): %s", wx, wy, wz, e.getMessage());
                    }
                }
            }
        }

        if (expanded > 0) {
            LOGGER.atInfo().log("Expanded %d PrefabSpawner blocks (depth=%d)", expanded, depth);
        }
        return expanded;
    }

    // ═══════════════════════════════════════════════════════════════════
    // PREFAB PASTE
    // ═══════════════════════════════════════════════════════════════════

    private boolean pastePrefabFromDirectory(@Nonnull World world, @Nonnull String dirPath,
                                             int x, int y, int z, @Nonnull Random random) {
        return pastePrefabFromDirectory(world, dirPath, x, y, z, random, null, null);
    }

    /**
     * Loads a random prefab from the given directory and pastes it at the specified position.
     *
     * <p>When {@code realmId} is provided, checks for overlap with existing structures
     * via {@link StructureBoundsRegistry} and terrain occupation via {@link TerrainUtils}.
     * After paste, expands embedded PrefabSpawnerBlocks and clears waterlogged blocks.
     */
    private boolean pastePrefabFromDirectory(@Nonnull World world, @Nonnull String dirPath,
                                             int x, int y, int z, @Nonnull Random random,
                                             @Nullable UUID realmId, @Nullable Set<String> terrainMaterials) {
        try {
            List<Path> prefabFiles = findPrefabFilesInDirectory(dirPath);
            if (prefabFiles.isEmpty()) {
                LOGGER.atFine().log("No prefab files found for: %s", dirPath);
                return false;
            }

            Path chosen = prefabFiles.get(random.nextInt(prefabFiles.size()));
            PrefabBuffer buffer = loadPrefabFile(chosen);
            if (buffer == null) {
                return false;
            }

            Rotation rotation = ROTATIONS[random.nextInt(ROTATIONS.length)];
            PrefabBuffer.PrefabBufferAccessor accessor = buffer.newAccess();
            Vector3i pastePos = new Vector3i(x, y, z);

            // Overlap and occupation checks (only for realm-tracked placements)
            if (realmId != null && terrainMaterials != null) {
                int halfW = Math.max(Math.abs(accessor.getMinX()), Math.abs(accessor.getMaxX()));
                int halfD = Math.max(Math.abs(accessor.getMinZ()), Math.abs(accessor.getMaxZ()));

                if (boundsRegistry.overlaps(realmId, x - halfW, z - halfD, x + halfW, z + halfD)) {
                    LOGGER.atFine().log("Boss satellite %s at (%d,%d) overlaps — skipping", dirPath, x, z);
                    return false;
                }
                if (TerrainUtils.isAreaOccupied(world, x, z, halfW, halfD, y, terrainMaterials)) {
                    LOGGER.atFine().log("Boss satellite %s at (%d,%d) area occupied — skipping", dirPath, x, z);
                    return false;
                }
            }

            // Paste the prefab
            PrefabUtil.paste((IPrefabBuffer) accessor, world, pastePos, rotation,
                    false, random, 0, false, false, false,
                    (ComponentAccessor) world.getEntityStore().getStore());

            // Register bounds for overlap prevention
            if (realmId != null) {
                int halfW = Math.max(Math.abs(accessor.getMinX()), Math.abs(accessor.getMaxX()));
                int halfD = Math.max(Math.abs(accessor.getMinZ()), Math.abs(accessor.getMaxZ()));
                boundsRegistry.register(realmId, x - halfW, z - halfD, x + halfW, z + halfD);
            }

            // Post-paste cleanup
            expandSpawnerBlocks(world, pastePos, (IPrefabBuffer) accessor, random, 0);
            clearWaterloggedBlocks(world, pastePos, (IPrefabBuffer) accessor);

            return true;
        } catch (Exception e) {
            LOGGER.atFine().log("Failed to paste prefab from %s: %s", dirPath, e.getMessage());
            return false;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // WATERLOG CLEANUP
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Clears fluid from non-empty blocks in and around the pasted area.
     *
     * <p>Waterlogged = non-Empty block + fluid present. Prefab paste can create waterlogged
     * blocks when pasting over existing terrain that had fluid. This method clears the fluid
     * component while preserving the pasted block.
     *
     * <p>Scans with a 20-block margin around the prefab bounds to catch edge cases.
     */
    private int clearWaterloggedBlocks(@Nonnull World world, @Nonnull Vector3i pastePos,
                                       @Nonnull IPrefabBuffer buffer) {
        int half = Math.max(
                Math.max(Math.abs(buffer.getMinX()), Math.abs(buffer.getMaxX())),
                Math.max(Math.abs(buffer.getMinZ()), Math.abs(buffer.getMaxZ()))
        ) + 20;
        int halfY = Math.max(Math.abs(buffer.getMinY()), Math.abs(buffer.getMaxY())) + 20;
        ChunkStore chunkStore = world.getChunkStore();
        int cleared = 0;

        for (int wx = pastePos.x - half; wx <= pastePos.x + half; wx++) {
            for (int wz = pastePos.z - half; wz <= pastePos.z + half; wz++) {
                WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(wx, wz));
                if (chunk == null) continue;

                for (int wy = pastePos.y - halfY; wy <= pastePos.y + halfY; wy++) {
                    try {
                        int fluidId = chunk.getFluidId(wx, wy, wz);
                        if (fluidId == 0) continue;

                        BlockType block = world.getBlockType(wx, wy, wz);
                        if (block == null || block == EMPTY_BLOCK) continue;

                        // Non-empty block WITH fluid = waterlogged → clear the fluid
                        Ref section = chunkStore.getChunkSectionReference(
                                ChunkUtil.chunkCoordinate(wx), ChunkUtil.chunkCoordinate(wy), ChunkUtil.chunkCoordinate(wz));
                        if (section == null || !section.isValid()) continue;

                        FluidSection fluidSection = (FluidSection) section.getStore()
                                .getComponent(section, FluidSection.getComponentType());
                        if (fluidSection == null) continue;

                        fluidSection.setFluid(wx, wy, wz, 0, (byte) 0);
                        cleared++;
                    } catch (Exception e) {
                        LOGGER.atFine().log("Failed to clear fluid at %d,%d,%d: %s", wx, wy, wz, e.getMessage());
                    }
                }
            }
        }
        return cleared;
    }

    // ═══════════════════════════════════════════════════════════════════
    // PREFAB FILE LOADING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Scans asset packs for .prefab.json files in the given directory path.
     * Searches packs in reverse order (last loaded = highest priority).
     */
    @Nonnull
    private List<Path> findPrefabFilesInDirectory(@Nonnull String dirPath) {
        ArrayList<Path> found = new ArrayList<>();
        try {
            List<AssetPack> packs = AssetModule.get().getAssetPacks();
            for (int i = packs.size() - 1; i >= 0; i--) {
                Path prefabsDir = packs.get(i).getRoot().resolve("Server").resolve("Prefabs").resolve(dirPath);
                if (!Files.isDirectory(prefabsDir)) continue;

                try (Stream<Path> stream = Files.list(prefabsDir)) {
                    stream.filter(p -> {
                        String name = p.getFileName().toString();
                        return name.endsWith(".prefab.json") || name.endsWith(".prefab.json.lpf");
                    }).forEach(found::add);
                }
                if (!found.isEmpty()) break;
            }
        } catch (Exception e) {
            LOGGER.atFine().log("Error scanning directory %s: %s", dirPath, e.getMessage());
        }
        return found;
    }

    @Nullable
    private CampTier findTier(@Nonnull RealmBiomeType biome, double arenaRadius) {
        List<CampTier> tiers = BOSS_CAMPS.get(biome);
        if (tiers == null) return null;
        for (CampTier tier : tiers) {
            if (tier.matches(arenaRadius)) return tier;
        }
        return null;
    }

    @Nullable
    private PrefabBuffer loadPrefabFile(@Nonnull Path path) {
        String key = path.toString();
        try {
            return prefabCache.computeIfAbsent(key, k -> {
                try {
                    Path loadPath = path;
                    // Handle .lpf compressed format — strip suffix to get .json path
                    if (k.endsWith(".lpf")) {
                        String jsonName = k.substring(0, k.length() - 4);
                        loadPath = Path.of(jsonName);
                    }
                    return PrefabBufferUtil.loadBuffer(loadPath);
                } catch (Exception e) {
                    LOGGER.atFine().log("Failed to load prefab: %s", k);
                    return null;
                }
            });
        } catch (Exception e) {
            LOGGER.atFine().log("Failed to load prefab from cache: %s", e.getMessage());
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CAMP TIER RECORD
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Defines a boss camp configuration tier for a specific arena radius range.
     *
     * @param minRadius    minimum arena radius for this tier (inclusive)
     * @param maxRadius    maximum arena radius for this tier (exclusive)
     * @param centerPool   prefab directory paths for the main boss structure
     * @param satellitePool prefab directory paths for surrounding structures
     * @param minSatellites minimum satellite structures to place
     * @param maxSatellites maximum satellite structures to place
     * @param minDistance   minimum distance from center for satellites
     * @param maxDistance   maximum distance from center for satellites
     */
    private record CampTier(int minRadius, int maxRadius,
                            List<String> centerPool, List<String> satellitePool,
                            int minSatellites, int maxSatellites,
                            int minDistance, int maxDistance) {
        boolean matches(double arenaRadius) {
            return arenaRadius >= minRadius && arenaRadius < maxRadius;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // STATIC INITIALIZATION — per-biome camp tier tables
    // ═══════════════════════════════════════════════════════════════════

    static {
        EnumMap<RealmBiomeType, List<CampTier>> map = new EnumMap<>(RealmBiomeType.class);

        // ─── Forest: Trork faction camps ───
        map.put(RealmBiomeType.FOREST, List.of(
                new CampTier(0, 55,
                        List.of("Npc/Trork/Tent", "Npc/Trork/Tier_1/Encampment/Hunter/Forest_Birch", "Npc/Trork/Tier_1/Encampment/Hunter/Forest_Flower"),
                        List.of("Npc/Trork/Bonfire", "Npc/Trork/Fireplace", "Npc/Trork/Trap"),
                        1, 2, 6, 10),
                new CampTier(55, 90,
                        List.of("Npc/Trork/Tier_2/Encampment/Lumber/Forest_Aspen", "Npc/Trork/Tier_2/Encampment/Lumber/Forest_Autumn", "Npc/Trork/Tier_2/Encampment/Lumber/Forest_Gully", "Npc/Trork/Tier_2/Encampment/Quarry", "Npc/Trork/Warehouse", "Npc/Trork/Tier_2/Store"),
                        List.of("Npc/Trork/Tent", "Npc/Trork/Bonfire", "Npc/Trork/Fireplace", "Npc/Trork/Tier_1/Watchtower", "Npc/Trork/Tier_2/Watchtower", "Npc/Trork/Trap", "Npc/Trork/Warning"),
                        2, 4, 10, 18),
                new CampTier(90, 999,
                        List.of("Npc/Trork/Tier_3/Encampment/Castle", "Npc/Trork/Misc/Large", "Npc/Trork/Tier_3/Watchtower", "Npc/Trork/Tier_3/Store"),
                        List.of("Npc/Trork/Tent", "Npc/Trork/Bonfire", "Npc/Trork/Fireplace", "Npc/Trork/Tier_2/Watchtower", "Npc/Trork/Tier_1/Watchtower", "Npc/Trork/Warehouse", "Npc/Trork/Warning", "Npc/Trork/Trap", "Npc/Trork/Burrow", "Npc/Trork/Misc/Large"),
                        4, 7, 12, 22)));

        // ─── Desert: Scorched ruins with undead + Scarak ───
        map.put(RealmBiomeType.DESERT, List.of(
                new CampTier(0, 55,
                        List.of("Monuments/Incidental/Sandstone/Tent", "Monuments/Incidental/Sandstone/Normal"),
                        List.of("Monuments/Incidental/Sandstone/Grass/Camp", "Monuments/Incidental/Sandstone/Red/Camp", "Monuments/Incidental/Sandstone/White/Camp"),
                        1, 2, 6, 10),
                new CampTier(55, 90,
                        List.of("Monuments/Encounter/Zone2/Tier1/Outpost", "Monuments/Incidental/Sandstone/Oasis/Rock_Camp", "Monuments/Incidental/Sandstone/Tent"),
                        List.of("Monuments/Incidental/Sandstone/Grass/Camp", "Monuments/Incidental/Sandstone/Red/Camp", "Monuments/Incidental/Sandstone/White/Camp", "Monuments/Incidental/Sandstone/Grass/Well", "Monuments/Incidental/Sandstone/Red/Well", "Monuments/Incidental/Sandstone/Grass/Drywood"),
                        2, 4, 10, 18),
                new CampTier(90, 999,
                        List.of("Monuments/Encounter/Zone2/Tier1/Outpost", "Monuments/Encounter/Zone2/Tier2/Outpost", "Monuments/Incidental/Sandstone/Oasis/Rock_Camp"),
                        List.of("Monuments/Incidental/Sandstone/Tent", "Monuments/Incidental/Sandstone/Grass/Camp", "Monuments/Incidental/Sandstone/Red/Camp", "Monuments/Incidental/Sandstone/White/Camp", "Monuments/Incidental/Sandstone/Grass/Well", "Monuments/Incidental/Sandstone/Red/Well", "Monuments/Incidental/Sandstone/White/Well", "Monuments/Incidental/Sandstone/Grass/Drywood", "Monuments/Incidental/Sandstone/Red/Drywood"),
                        4, 7, 12, 22)));

        // ─── Volcano: Geological formations (fossil-based, no faction) ───
        map.put(RealmBiomeType.VOLCANO, List.of(
                new CampTier(0, 55,
                        List.of("Rock_Formations/Hotsprings/Desert", "Rock_Formations/Fossils/Small"),
                        List.of("Rock_Formations/Rocks/Volcanic/Large", "Rock_Formations/Rocks/Basalt/Hexagon", "Rock_Formations/Rocks/Volcanic/Spiked/Large"),
                        1, 2, 6, 10),
                new CampTier(55, 90,
                        List.of("Rock_Formations/Fossils/Large/Normal", "Rock_Formations/Fossils/Large/Ruined", "Rock_Formations/Hotsprings/Desert"),
                        List.of("Rock_Formations/Fossils/Small", "Rock_Formations/Hotsprings/Desert", "Rock_Formations/Rocks/Volcanic/Spiked/Large", "Rock_Formations/Rocks/Basalt/Hexagon", "Rock_Formations/Stalactites/Basalt/Floor"),
                        2, 4, 10, 18),
                new CampTier(90, 999,
                        List.of("Rock_Formations/Fossils/Gigantic/Normal", "Rock_Formations/Fossils/Gigantic/Ruined", "Rock_Formations/Fossils/Large/Normal"),
                        List.of("Rock_Formations/Fossils/Small", "Rock_Formations/Fossils/Large/Normal", "Rock_Formations/Fossils/Large/Ruined", "Rock_Formations/Hotsprings/Desert", "Rock_Formations/Rocks/Volcanic/Spiked/Large", "Rock_Formations/Rocks/Basalt/Hexagon"),
                        4, 7, 12, 22)));

        // ─── Tundra: Outlander frost strongholds + Yeti camps ───
        map.put(RealmBiomeType.TUNDRA, List.of(
                new CampTier(0, 55,
                        List.of("Npc/Yeti/Camps", "Npc/Outlander/Houses/Tier0"),
                        List.of("Npc/Outlander/Spikes", "Npc/Outlander/Braziers", "Npc/Outlander/Totems"),
                        1, 2, 6, 10),
                new CampTier(55, 90,
                        List.of("Npc/Outlander/Forts/Tier1", "Npc/Outlander/Forts/Tier2", "Npc/Yeti/Camps"),
                        List.of("Npc/Outlander/Houses/Tier0", "Npc/Outlander/Houses/Tier1", "Npc/Outlander/Towers/Tier1", "Npc/Outlander/Spikes", "Npc/Outlander/Braziers", "Npc/Outlander/Misc"),
                        2, 4, 10, 18),
                new CampTier(90, 999,
                        List.of("Npc/Outlander/Camps/Tier3/Base", "Npc/Outlander/Forts/Tier3", "Npc/Outlander/Houses/Tier3"),
                        List.of("Npc/Outlander/Houses/Tier1", "Npc/Outlander/Houses/Tier2", "Npc/Outlander/Towers/Tier1", "Npc/Outlander/Towers/Tier2", "Npc/Outlander/Forts/Tier1", "Npc/Outlander/Spikes", "Npc/Outlander/Braziers", "Npc/Outlander/Misc", "Npc/Outlander/Totems"),
                        4, 7, 12, 22)));

        // ─── Caverns: Spider nests + underground formations ───
        map.put(RealmBiomeType.CAVERNS, List.of(
                new CampTier(0, 55,
                        List.of("Cave/Nodes/Rock_Stone/Spider", "Cave/Nodes/Rock_Stone/Spider_Deep"),
                        List.of("Cave/Nodes/Rock_Stone/Rat", "Cave/Nodes/Rock_Stone/Boomshroom", "Rock_Formations/Rocks/Stone/Large"),
                        1, 2, 6, 10),
                new CampTier(55, 90,
                        List.of("Cave/Nodes/Rock_Stone/Spider_Deep", "Cave/Nodes/Rock_Stone/Spider"),
                        List.of("Cave/Nodes/Rock_Stone/Spider", "Cave/Nodes/Rock_Stone/Boomshroom", "Cave/Nodes/Rock_Stone/Rat", "Rock_Formations/Rocks/Basalt/Large"),
                        2, 4, 8, 16),
                new CampTier(90, 999,
                        List.of("Cave/Nodes/Rock_Stone/Spider_Deep", "Cave/Nodes/Rock_Stone/Spider"),
                        List.of("Cave/Nodes/Rock_Stone/Spider_Deep", "Cave/Nodes/Rock_Stone/Spider", "Cave/Nodes/Rock_Stone/Boomshroom_Deep", "Cave/Nodes/Rock_Stone/Rat", "Rock_Formations/Rocks/Stone/Large"),
                        3, 5, 10, 18)));

        // ─── Frozen Crypts: Ice ruins + frozen monuments ───
        map.put(RealmBiomeType.FROZEN_CRYPTS, List.of(
                new CampTier(0, 55,
                        List.of("Monuments/Incidental/Shale/Ruins/Frozen", "Rock_Formations/Fossils/Small"),
                        List.of("Rock_Formations/Rocks/Frozenstone/Small", "Rock_Formations/Rocks/Frozenstone/Snowy", "Rock_Formations/Rocks/Basalt/Snowy"),
                        1, 2, 6, 10),
                new CampTier(55, 90,
                        List.of("Monuments/Encounter/Zone3/Tier2/Frozen/Outpost", "Monuments/Encounter/Shale/Frozen"),
                        List.of("Monuments/Incidental/Shale/Ruins/Frozen", "Rock_Formations/Fossils/Small", "Rock_Formations/Rocks/Frozenstone/Snowy", "Rock_Formations/Rocks/Basalt/Snowy"),
                        2, 3, 8, 14),
                new CampTier(90, 999,
                        List.of("Monuments/Encounter/Shale/Frozen", "Npc/Outlander/Ice_Caves"),
                        List.of("Monuments/Incidental/Shale/Ruins/Frozen", "Monuments/Encounter/Zone3/Tier2/Frozen/Outpost", "Rock_Formations/Fossils/Small", "Rock_Formations/Rocks/Frozenstone/Snowy", "Rock_Formations/Rocks/Basalt/Snowy"),
                        3, 5, 10, 18)));

        // ─── Sand Tombs: Ancient sandstone structures ───
        map.put(RealmBiomeType.SAND_TOMBS, List.of(
                new CampTier(0, 55,
                        List.of("Monuments/Incidental/Sandstone/!Normal", "Rock_Formations/Fossils/Small"),
                        List.of("Rock_Formations/Rocks/Sandstone/Large", "Rock_Formations/Rocks/Sandstone/Small", "Rock_Formations/Rocks/Sandstone/Red/Small"),
                        1, 2, 6, 10),
                new CampTier(55, 90,
                        List.of("Monuments/Incidental/Treasure_Rooms/Sandstone", "Monuments/Incidental/Sandstone/!Wells/White"),
                        List.of("Monuments/Incidental/Sandstone/!Normal", "Rock_Formations/Fossils/Small", "Rock_Formations/Rocks/Sandstone/Large", "Rock_Formations/Rocks/Sandstone/Pillars/Medium"),
                        2, 4, 8, 16),
                new CampTier(90, 999,
                        List.of("Monuments/Incidental/Treasure_Rooms/Sandstone", "Monuments/Incidental/Sandstone/Normal"),
                        List.of("Monuments/Incidental/Sandstone/!Normal", "Monuments/Incidental/Sandstone/!Wells/Yellow", "Rock_Formations/Fossils/Small", "Rock_Formations/Rocks/Sandstone/Large", "Rock_Formations/Rocks/Sandstone/Pillars/Large"),
                        3, 6, 10, 20)));

        // ─── Swamp: Kweebec settlements + mushroom formations ───
        map.put(RealmBiomeType.SWAMP, List.of(
                new CampTier(0, 55,
                        List.of("Npc/Kweebec/Swamp/Old_Farms", "Rock_Formations/Mushrooms/Rock/Large"),
                        List.of("Rock_Formations/Mushrooms/Rock/Small", "Rock_Formations/Arches/Swamp/Small", "Rock_Formations/Rocks/Grass/Small"),
                        1, 2, 6, 10),
                new CampTier(55, 90,
                        List.of("Npc/Kweebec/Swamp/Houses", "Npc/Kweebec/Swamp/Guards", "Rock_Formations/Mushrooms/Rock/Large"),
                        List.of("Npc/Kweebec/Swamp/Old_Farms", "Rock_Formations/Mushrooms/Rock/Small", "Rock_Formations/Mushrooms/Pillar", "Rock_Formations/Arches/Swamp/Small", "Rock_Formations/Pillars/Rock_Stone/Swamp"),
                        2, 4, 8, 16),
                new CampTier(90, 999,
                        List.of("Npc/Kweebec/Swamp/Houses", "Monuments/Encounter/Zone4/Tier4/Village/Swamp/House"),
                        List.of("Npc/Kweebec/Swamp/Old_Farms", "Npc/Kweebec/Swamp/Guards", "Npc/Kweebec/Swamp/Farms", "Rock_Formations/Mushrooms/Rock/Large", "Rock_Formations/Mushrooms/Pillar", "Rock_Formations/Arches/Swamp/Large"),
                        3, 6, 10, 20)));

        // ─── Beach: Sunken corsair shipwrecks + ocean ruins ───
        map.put(RealmBiomeType.BEACH, List.of(
                new CampTier(0, 55,
                        List.of("Monuments/Incidental/Shipwrecks/Tropical", "Monuments/Incidental/Grasslands/Camps/Beach"),
                        List.of("Monuments/Encounter/City_Oceans/Buildings_Small/Coral", "Rock_Formations/Fossils/Small", "Rock_Formations/Rocks/Sandstone/Large"),
                        1, 2, 6, 10),
                new CampTier(55, 90,
                        List.of("Monuments/Incidental/Shipwrecks/Temperate", "Monuments/Encounter/City_Oceans/Buildings_Medium/Coral"),
                        List.of("Monuments/Incidental/Shipwrecks/Tropical", "Monuments/Encounter/City_Oceans/Buildings_Small/Coral", "Monuments/Incidental/Grasslands/Camps/Beach", "Rock_Formations/Fossils/Small"),
                        2, 4, 10, 18),
                new CampTier(90, 999,
                        List.of("Monuments/Encounter/City_Oceans/Buildings_Capital", "Monuments/Encounter/City_Oceans/Buildings_Tower", "Monuments/Incidental/Shipwrecks/Temperate"),
                        List.of("Monuments/Encounter/City_Oceans/Buildings_Medium/Coral", "Monuments/Encounter/City_Oceans/Buildings_Small/Coral", "Monuments/Incidental/Shipwrecks/Tropical", "Monuments/Incidental/Grasslands/Camps/Beach", "Monuments/Incidental/Ocean/Shipwrecks", "Rock_Formations/Fossils/Small"),
                        4, 7, 12, 22)));

        BOSS_CAMPS = Collections.unmodifiableMap(map);
        ROTATIONS = new Rotation[]{Rotation.None, Rotation.Ninety, Rotation.OneEighty, Rotation.TwoSeventy};
        EMPTY_BLOCK = (BlockType) BlockType.getAssetMap().getAsset("Empty");
    }
}
