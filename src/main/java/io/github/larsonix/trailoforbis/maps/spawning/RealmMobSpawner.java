package io.github.larsonix.trailoforbis.maps.spawning;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.maps.components.RealmMobComponent;
import io.github.larsonix.trailoforbis.maps.config.MobPoolConfig;
import io.github.larsonix.trailoforbis.maps.core.RealmBiomeType;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;
import io.github.larsonix.trailoforbis.maps.spawning.RealmEntitySpawner.SpawnRequest;
import io.github.larsonix.trailoforbis.maps.spawning.RealmMobCalculator.SpawnDistribution;
import io.github.larsonix.trailoforbis.maps.spawning.RealmMobCalculator.WaveParameters;
import io.github.larsonix.trailoforbis.util.TerrainUtils;
import io.github.larsonix.trailoforbis.maps.spawning.WeightedMob.MobClassification;
import io.github.larsonix.trailoforbis.maps.templates.MonsterSpawnPoint;
import io.github.larsonix.trailoforbis.maps.templates.RealmTemplate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Spawns and manages mobs in realm instances.
 *
 * <p>The spawner coordinates mob spawning across the realm lifecycle:
 * <ol>
 *   <li>Initial wave spawning when realm activates</li>
 *   <li>Periodic reinforcement waves during combat</li>
 *   <li>Boss spawning at designated points</li>
 *   <li>Death tracking for completion detection</li>
 * </ol>
 *
 * <h2>Spawning Strategy</h2>
 * <p>All mobs spawn in the initial wave when the realm activates.
 * Mob counts are kept reasonable (15-70 base) so a single wave works well.
 * <ul>
 *   <li><b>Initial Wave:</b> All mobs spawn immediately</li>
 *   <li><b>Despawn Recovery:</b> Mobs that despawn are respawned in throttled batches</li>
 *   <li><b>Bosses:</b> Spawn at designated boss spawn points</li>
 * </ul>
 *
 * <h2>Mob Selection</h2>
 * <p>Mobs are selected from biome-specific pools using weighted random selection:
 * <ul>
 *   <li>Normal mobs form the bulk of spawns</li>
 *   <li>Bosses spawn at designated points or by chance</li>
 * </ul>
 *
 * <h2>Elite System</h2>
 * <p>Elite is a spawn-time modifier, NOT a separate classification:
 * <ul>
 *   <li>Any mob (normal or boss) can roll to become elite at spawn time</li>
 *   <li>Elite chance is level-scaled and configured in {@link MobPoolConfig}</li>
 *   <li>Elite mobs receive bonus stats (see {@link WeightedMob#ELITE_STAT_MULTIPLIER})</li>
 *   <li>Elite bosses are possible and receive both elite AND boss multipliers</li>
 * </ul>
 *
 * @see RealmMobCalculator
 * @see BiomeMobPool
 * @see RealmInstance
 */
public class RealmMobSpawner {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /**
     * Maximum times a mob can be respawned before being abandoned.
     * Prevents infinite respawn loops if a mob's AI behavior tree
     * includes an ActionDespawn that triggers every time it spawns.
     * Abandoned mobs are marked killed and written off in the completion tracker.
     */
    private static final int MAX_RESPAWN_ATTEMPTS = 3;

    // ═══════════════════════════════════════════════════════════════════
    // DEPENDENCIES
    // ═══════════════════════════════════════════════════════════════════

    private final RealmMobCalculator calculator;
    private final Map<RealmBiomeType, BiomeMobPool> mobPools;
    private final RealmEntitySpawner entitySpawner;
    /** Lazy-initialized to avoid loading Hytale prefab classes in test environment. */
    private volatile BossStructurePlacer bossStructurePlacer;
    /** Shared bounds registry for structure overlap prevention. Set via setter after construction. */
    @Nullable
    private StructureBoundsRegistry boundsRegistry;

    // ═══════════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════════

    /** Tracking spawned mobs per realm */
    private final Map<UUID, SpawnState> realmSpawnStates = new ConcurrentHashMap<>();

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a new mob spawner with the required entity spawner.
     *
     * @param entitySpawner The entity spawner service for actual NPC spawning
     */
    public RealmMobSpawner(@Nonnull RealmEntitySpawner entitySpawner) {
        this(entitySpawner, RealmMobCalculator.withDefaults(), new HashMap<>());
    }

    /**
     * Creates a new mob spawner with custom configuration.
     *
     * @param entitySpawner The entity spawner service
     * @param config        The mob pool configuration
     */
    public RealmMobSpawner(@Nonnull RealmEntitySpawner entitySpawner, @Nonnull MobPoolConfig config) {
        this(entitySpawner, new RealmMobCalculator(config), new HashMap<>());
    }

    /**
     * Creates a new mob spawner with full customization.
     *
     * @param entitySpawner The entity spawner service
     * @param calculator    The mob calculator
     * @param mobPools      Pre-loaded mob pools by biome
     */
    public RealmMobSpawner(
            @Nonnull RealmEntitySpawner entitySpawner,
            @Nonnull RealmMobCalculator calculator,
            @Nonnull Map<RealmBiomeType, BiomeMobPool> mobPools) {
        this.entitySpawner = Objects.requireNonNull(entitySpawner, "entitySpawner cannot be null");
        this.calculator = Objects.requireNonNull(calculator, "calculator cannot be null");
        this.mobPools = new EnumMap<>(RealmBiomeType.class);
        this.mobPools.putAll(mobPools);
    }

    /**
     * Sets the shared structure bounds registry for overlap prevention.
     * Called after construction by RealmsManager.
     */
    public void setBoundsRegistry(@Nonnull StructureBoundsRegistry registry) {
        this.boundsRegistry = registry;
    }

    // ═══════════════════════════════════════════════════════════════════
    // MOB POOL MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Registers a mob pool for a biome.
     *
     * @param pool The mob pool to register
     */
    public void registerPool(@Nonnull BiomeMobPool pool) {
        Objects.requireNonNull(pool, "pool cannot be null");
        mobPools.put(pool.getBiome(), pool);
        LOGGER.atInfo().log("Registered mob pool for biome %s with %d mobs",
            pool.getBiome(), pool.getTotalMobCount());
    }

    /**
     * Gets the mob pool for a biome.
     *
     * @param biome The biome type
     * @return The mob pool, or empty pool if not registered
     */
    @Nonnull
    public BiomeMobPool getPool(@Nonnull RealmBiomeType biome) {
        return mobPools.getOrDefault(biome, BiomeMobPool.empty(biome));
    }

    /**
     * Checks if a biome has a registered mob pool.
     *
     * @param biome The biome type
     * @return true if a pool is registered
     */
    public boolean hasPool(@Nonnull RealmBiomeType biome) {
        BiomeMobPool pool = mobPools.get(biome);
        return pool != null && !pool.isEmpty();
    }

    // ═══════════════════════════════════════════════════════════════════
    // SPAWNING METHODS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Initializes spawning for a realm instance.
     *
     * <p>This should be called when the realm transitions to ACTIVE state.
     *
     * @param realm The realm instance
     * @return Future that completes when initial spawning is done
     */
    @Nonnull
    public CompletableFuture<SpawnResult> initializeSpawning(@Nonnull RealmInstance realm) {
        Objects.requireNonNull(realm, "realm cannot be null");

        RealmMapData mapData = realm.getMapData();
        RealmTemplate template = realm.getTemplate();
        World world = realm.getWorld();

        if (world == null) {
            return CompletableFuture.completedFuture(
                SpawnResult.failed("Realm world not available"));
        }

        // Calculate spawn distribution
        SpawnDistribution distribution = calculator.calculate(mapData);
        WaveParameters waves = calculator.calculateWaves(distribution);

        // Get the mob pool for this biome
        BiomeMobPool pool = getPool(mapData.biome());
        if (pool.isEmpty()) {
            LOGGER.atWarning().log("No mob pool for biome %s, using fallback",
                mapData.biome());
            // TODO: Create fallback pool
        }

        // Get player count for stat scaling (at least 1)
        int playerCount = Math.max(1, realm.getPlayerCount());

        // Extract player spawn position from template for exclusion zone
        Vector3d playerSpawnPos = template.playerSpawn().getPosition();

        // Create spawn state
        SpawnState state = new SpawnState(
            realm.getRealmId(),
            mapData,
            distribution,
            waves,
            pool,
            template.spawnPoints(),
            playerCount,
            playerSpawnPos
        );
        realmSpawnStates.put(realm.getRealmId(), state);

        // Execute initial wave
        return executeInitialWave(realm, state);
    }

    /**
     * Spawns the initial wave of mobs.
     */
    private CompletableFuture<SpawnResult> executeInitialWave(
            @Nonnull RealmInstance realm,
            @Nonnull SpawnState state) {

        World world = realm.getWorld();
        if (world == null) {
            return CompletableFuture.completedFuture(
                SpawnResult.failed("Realm world unavailable"));
        }

        int toSpawn = state.distribution.totalCount();
        Random random = new Random();

        // Calculate how many of each type to spawn (no eliteCount - elite is per-mob roll)
        int bossCount = Math.min(
            state.distribution.guaranteedBosses(),
            (int) (toSpawn * state.distribution.bossPercentage())
        );
        int normalCount = toSpawn - bossCount;

        // Spawn mobs (initial wave = not reinforcement)
        // Elite is rolled per-mob at spawn time
        List<SpawnedMob> spawned = new ArrayList<>();

        // Spawn bosses at designated boss spawn points first
        spawned.addAll(spawnMobsAtPoints(
            world, state, MobClassification.BOSS, bossCount, random, false));

        // Spawn normal mobs
        spawned.addAll(spawnMobsAtPoints(
            world, state, MobClassification.NORMAL, normalCount, random, false));

        state.spawnedCount.addAndGet(spawned.size());
        state.initialWaveComplete = true;

        // Count elites for logging
        int eliteCount = (int) spawned.stream().filter(SpawnedMob::isElite).count();

        // Synchronize completion tracker with actual spawn count
        realm.getCompletionTracker().setTotalMonsters(spawned.size());

        LOGGER.atInfo().log("Initial wave complete for realm %s: %d mobs spawned " +
            "(%d normal, %d boss, %d rolled elite)",
            realm.getRealmId(), spawned.size(),
            normalCount, bossCount, eliteCount);

        return CompletableFuture.completedFuture(
            SpawnResult.success(spawned.size(), state.distribution.totalCount()));
    }

    /**
     * Spawns a reinforcement wave.
     *
     * <p>Called periodically to spawn additional mobs.
     *
     * @param realm The realm instance
     * @return Number of mobs spawned
     */
    public int spawnReinforcementWave(@Nonnull RealmInstance realm) {
        SpawnState state = realmSpawnStates.get(realm.getRealmId());
        if (state == null || !state.initialWaveComplete) {
            return 0;
        }

        World world = realm.getWorld();
        if (world == null) {
            return 0;
        }

        // Check if we've spawned all mobs
        int totalSpawned = state.spawnedCount.get();
        int totalTarget = state.distribution.totalCount();
        if (totalSpawned >= totalTarget) {
            return 0;
        }

        int remaining = totalTarget - totalSpawned;
        int toSpawn = Math.min(remaining, state.waves.subsequentWaveSize());

        // Increment wave number for reinforcement tracking
        state.currentWaveNumber++;

        Random random = new Random();

        // Calculate type distribution for this wave (no eliteCount - elite is per-mob roll)
        int bossCount = (int) (toSpawn * state.distribution.bossPercentage());
        int normalCount = toSpawn - bossCount;

        // Spawn reinforcement wave mobs (isReinforcement = true)
        // Elite is rolled per-mob at spawn time
        List<SpawnedMob> spawned = new ArrayList<>();
        spawned.addAll(spawnMobsAtPoints(world, state, MobClassification.NORMAL, normalCount, random, true));
        spawned.addAll(spawnMobsAtPoints(world, state, MobClassification.BOSS, bossCount, random, true));

        state.spawnedCount.addAndGet(spawned.size());

        LOGGER.atFine().log("Reinforcement wave for realm %s: %d mobs spawned (total: %d/%d)",
            realm.getRealmId(), spawned.size(), state.spawnedCount.get(), totalTarget);

        return spawned.size();
    }

    /**
     * Spawns mobs at designated spawn points using the Hytale NPC API.
     *
     * <p>Uses {@link RealmEntitySpawner} to spawn actual NPCs via
     * {@link com.hypixel.hytale.server.npc.NPCPlugin#spawnEntity}.
     *
     * <p><b>Elite Roll:</b> Each mob independently rolls for elite status
     * based on {@code state.distribution.eliteChance()}. This means any mob
     * (normal or boss) can become elite at spawn time, receiving bonus stats.
     */
    private List<SpawnedMob> spawnMobsAtPoints(
            @Nonnull World world,
            @Nonnull SpawnState state,
            @Nonnull MobClassification classification,
            int count,
            @Nonnull Random random,
            boolean isReinforcement) {

        if (count <= 0) {
            return List.of();
        }

        List<SpawnedMob> spawned = new ArrayList<>();
        int level = state.mapLevel;
        BiomeMobPool pool = state.pool;
        double eliteChance = state.distribution.eliteChance();

        // Find appropriate spawn points for this classification
        List<MonsterSpawnPoint> points = getSpawnPointsForClassification(
            state.spawnPoints, classification);

        if (points.isEmpty()) {
            // Fallback: use all spawn points
            points = state.spawnPoints;
        }

        if (points.isEmpty()) {
            LOGGER.atWarning().log("No spawn points available for realm");
            return List.of();
        }

        // Arena radius and exclusion are constant for the realm — compute once
        double arenaRadius = state.mapData.computeArenaRadius();
        double exclusionRadius = calculator.getConfig().getSpawnExclusionRadius();

        for (int i = 0; i < count; i++) {
            // Select a mob from the pool
            WeightedMob mobEntry = pool.selectMobForLevel(classification, level, random);
            if (mobEntry == null) {
                // Try any classification as fallback
                mobEntry = pool.selectMobForLevel(MobClassification.NORMAL, level, random);
            }
            if (mobEntry == null) {
                LOGGER.atWarning().log("No mobs available for classification %s at level %d",
                    classification, level);
                continue;
            }

            // Roll for elite status - any mob can become elite at spawn time
            boolean isElite = random.nextDouble() < eliteChance;

            // Compute visual scale for clearance checks — bosses and elites are larger
            // and need proportionally more space to avoid spawning inside structures.
            // Scale values match RealmEntitySpawner.applyMobScale() for consistency.
            float mobScale = 1.0f;
            if (classification == MobClassification.BOSS) {
                mobScale *= calculator.getConfig().getBossScale();
            }
            if (isElite) {
                mobScale *= calculator.getConfig().getEliteScale();
            }
            boolean isBoss = (classification == MobClassification.BOSS);

            // Generate spawn position with retry. Dense biomes (jungle, forest) have
            // vegetation clusters that block clearance over 5-10 block areas. The old
            // approach tried ±3 block offsets from one position — not enough to escape
            // a dense tree cluster. Now: if local offsets fail, generate a completely
            // new random position (up to MAX_POSITION_RETRIES times). Each new position
            // is in a different part of the arena, likely in a different vegetation cluster.
            int maxScanY = (int) io.github.larsonix.trailoforbis.maps.templates
                .RealmTemplateRegistry.getBaseYForBiome(state.mapData.biome()) + 30;
            var terrainMaterials = state.mapData.biome().getTerrainMaterials();

            int blockX = 0, blockZ = 0, groundY = 0;
            boolean positionFound = false;

            // Phase 1: Try random positions with local offsets (fast, preferred).
            // Each attempt generates a new random arena position, then checks nearby offsets.
            // Bosses search wider offsets since they need more clearance space.
            int maxOffset = isBoss ? 10 : 6;
            for (int posAttempt = 0; posAttempt < 12 && !positionFound; posAttempt++) {
                Vector3d spawnPos = generateSpawnPosition(
                    classification, arenaRadius,
                    state.playerSpawnPosition, exclusionRadius, random
                );
                blockX = (int) Math.floor(spawnPos.x);
                blockZ = (int) Math.floor(spawnPos.z);
                groundY = TerrainUtils.findGroundLevel(world, blockX, blockZ, maxScanY, terrainMaterials);

                // Check clearance at primary position (2x2x2 base, scaled for elite/boss)
                if (TerrainUtils.isSpawnPositionClear(world, blockX, groundY, blockZ,
                        mobScale, isBoss, isElite)) {
                    positionFound = true;
                    break;
                }

                // Spiral outward from the position — closest valid spot to original
                for (int abs = 1; abs <= maxOffset && !positionFound; abs++) {
                    for (int sign : new int[]{1, -1}) {
                        int offset = abs * sign;
                        int testGroundX = TerrainUtils.findGroundLevel(
                            world, blockX + offset, blockZ, maxScanY, terrainMaterials);
                        if (TerrainUtils.isSpawnPositionClear(world, blockX + offset, testGroundX, blockZ,
                                mobScale, isBoss, isElite)) {
                            blockX += offset;
                            groundY = testGroundX;
                            positionFound = true;
                            break;
                        }
                        int testGroundZ = TerrainUtils.findGroundLevel(
                            world, blockX, blockZ + offset, maxScanY, terrainMaterials);
                        if (TerrainUtils.isSpawnPositionClear(world, blockX, testGroundZ, blockZ + offset,
                                mobScale, isBoss, isElite)) {
                            blockZ += offset;
                            groundY = testGroundZ;
                            positionFound = true;
                            break;
                        }
                    }
                }
            }

            // Phase 2: Exhaustive fallback — mob MUST spawn. No mob left behind.
            // If 12 random positions all failed, do a systematic grid scan from arena center
            // outward with expanding radius. Guarantees a position is found as long as ANY
            // open ground exists in the arena.
            if (!positionFound) {
                LOGGER.atFine().log("Phase 1 failed after 12 attempts — starting exhaustive scan");
                int centerX = (int) Math.floor(state.playerSpawnPosition.x);
                int centerZ = (int) Math.floor(state.playerSpawnPosition.z);
                int scanRadius = (int) Math.min(arenaRadius * 0.85, 100);

                for (int r = 5; r <= scanRadius && !positionFound; r += 3) {
                    // Sample 8 points on a ring at distance r
                    for (int pt = 0; pt < 8 && !positionFound; pt++) {
                        double angle = (pt / 8.0) * 2.0 * Math.PI + random.nextDouble() * 0.5;
                        int testX = centerX + (int) (Math.cos(angle) * r);
                        int testZ = centerZ + (int) (Math.sin(angle) * r);
                        int testY = TerrainUtils.findGroundLevel(world, testX, testZ, maxScanY, terrainMaterials);
                        if (TerrainUtils.isSpawnPositionClear(world, testX, testY, testZ,
                                mobScale, isBoss, isElite)) {
                            blockX = testX;
                            blockZ = testZ;
                            groundY = testY;
                            positionFound = true;
                        }
                    }
                }
            }

            if (!positionFound) {
                // Absolute last resort — spawn at a random position anyway.
                // Better to clip slightly than to lose a mob entirely.
                LOGGER.atWarning().log("Exhaustive scan failed — force-spawning at last attempted position");
            }

            Vector3d spawnPos = new Vector3d(blockX + 0.5, groundY + 0.5, blockZ + 0.5);

            // Place a boss structure at the first boss mob's position.
            // The structure (tent, camp, castle) scales with arena size and matches the biome's
            // faction theme. Only one structure per realm — subsequent bosses spawn normally.
            // Placement is fire-and-forget: failure never prevents boss spawning.
            if (classification == MobClassification.BOSS) {
                try {
                    if (bossStructurePlacer == null) {
                        bossStructurePlacer = new BossStructurePlacer(
                            boundsRegistry != null ? boundsRegistry : new StructureBoundsRegistry());
                    }
                    bossStructurePlacer.placeStructureForBoss(
                        world, state.realmId, state.mapData.biome(), arenaRadius, spawnPos);
                } catch (Throwable t) {
                    LOGGER.atWarning().withCause(t).log(
                        "Boss structure placement failed — boss spawns normally");
                }
            }

            // Create spawn request for the entity spawner (includes isElite)
            SpawnRequest request;
            if (isReinforcement) {
                request = SpawnRequest.reinforcement(
                    state.realmId,
                    mobEntry.mobTypeId(),
                    classification,
                    spawnPos,
                    state.currentWaveNumber,
                    isElite,
                    state.mapData,
                    state.playerCount
                );
            } else {
                request = SpawnRequest.initial(
                    state.realmId,
                    mobEntry.mobTypeId(),
                    classification,
                    spawnPos,
                    isElite,
                    state.mapData,
                    state.playerCount
                );
            }

            // Create persistent mob state BEFORE spawning (includes isElite)
            RealmMobState mobState;
            if (isReinforcement) {
                mobState = RealmMobState.forReinforcement(
                    mobEntry.mobTypeId(),
                    classification,
                    spawnPos,
                    state.currentWaveNumber,
                    isElite
                );
            } else {
                mobState = RealmMobState.forInitialSpawn(
                    mobEntry.mobTypeId(),
                    classification,
                    spawnPos,
                    isElite
                );
            }

            // Spawn the entity via NPCPlugin
            RealmEntitySpawner.SpawnResult result = entitySpawner.spawnNow(world, request);

            if (result.success()) {
                Ref<EntityStore> entityRef = result.entityRef();

                // Diagnostic: check if ref is valid immediately after spawn (FINE level)
                boolean refValidAtSpawn = entityRef != null && entityRef.isValid();
                LOGGER.atFine().log("[SpawnDiag] Spawned %s%s, ref=%s, validAtSpawn=%b",
                    mobEntry.mobTypeId(),
                    isElite ? " (ELITE)" : "",
                    entityRef != null ? entityRef.toString().substring(0, Math.min(50, entityRef.toString().length())) : "null",
                    refValidAtSpawn);

                // Update mob state with entity reference
                mobState.setEntityRef(entityRef);
                state.mobStates.put(mobState.getMobId(), mobState);

                SpawnedMob mob = new SpawnedMob(
                    entityRef,
                    mobEntry.mobTypeId(),
                    classification,
                    spawnPos,
                    level,
                    isElite
                );
                spawned.add(mob);
                state.aliveEntityRefs.add(entityRef);
            } else {
                LOGGER.atWarning().log("Failed to spawn %s: %s",
                    mobEntry.mobTypeId(), result.error());
            }
        }

        return spawned;
    }

    /**
     * Gets spawn points appropriate for a mob classification.
     */
    private List<MonsterSpawnPoint> getSpawnPointsForClassification(
            @Nonnull List<MonsterSpawnPoint> allPoints,
            @Nonnull MobClassification classification) {

        return allPoints.stream()
            .filter(p -> matchesClassification(p, classification))
            .toList();
    }

    /**
     * Checks if a spawn point matches a mob classification.
     *
     * <p>Note: Elite is now a spawn-time modifier, not a classification.
     * The ELITE case has been removed - elite mobs spawn at the same
     * points as their base classification (NORMAL or BOSS).
     */
    private boolean matchesClassification(
            @Nonnull MonsterSpawnPoint point,
            @Nonnull MobClassification classification) {
        return switch (classification) {
            case BOSS -> point.isBossSpawn();
            case NORMAL -> point.isNormalSpawn() || point.isPackSpawn() || point.isEliteSpawn();
        };
    }

    /**
     * Generates a spawn position using uniform disk sampling within the circular arena.
     *
     * <p>Replaces the old spawn-point-based positioning which used fixed spiral points
     * with tiny scatter radii (causing visible lines) and a square clamp (causing wall spawns).
     *
     * <p>Uses {@code sqrt(random)} for uniform area distribution — without sqrt, positions
     * cluster at the center because inner rings have less area than outer rings.
     *
     * <p>Boss mobs are constrained to the outer 40-80% of the arena radius. On tiny arenas
     * where this overlaps with the exclusion zone, the range is pushed outward.
     *
     * @param classification The mob classification (affects distance range)
     * @param arenaRadius The circular arena radius in blocks
     * @param playerSpawnPos The player spawn position (center of exclusion zone)
     * @param exclusionRadius The radius around player spawn where mobs cannot spawn
     * @param random Random source
     * @return Position within the circular arena, outside the exclusion zone
     */
    private Vector3d generateSpawnPosition(
            @Nonnull MobClassification classification,
            double arenaRadius,
            @Nonnull Vector3d playerSpawnPos,
            double exclusionRadius,
            @Nonnull Random random) {

        // Distance range based on classification
        double minDist, maxDist;
        if (classification == MobClassification.BOSS) {
            minDist = arenaRadius * 0.4;
            maxDist = arenaRadius * 0.8;
            // On tiny arenas, boss range may overlap exclusion zone — push outward
            if (maxDist < exclusionRadius + 1.0) {
                minDist = exclusionRadius + 1.0;
                maxDist = arenaRadius;
            }
        } else {
            minDist = 0.0;
            maxDist = arenaRadius;
        }

        // Retry loop: generate position, check exclusion zone
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = random.nextDouble() * 2.0 * Math.PI;

            // Uniform disk/annular sampling: sqrt(U * (Rmax² - Rmin²) + Rmin²)
            double u = random.nextDouble();
            double distance = Math.sqrt(u * (maxDist * maxDist - minDist * minDist) + minDist * minDist);

            double x = Math.cos(angle) * distance;
            double z = Math.sin(angle) * distance;

            // Check exclusion zone (horizontal distance to player spawn)
            double dx = x - playerSpawnPos.x;
            double dz = z - playerSpawnPos.z;
            double distToPlayer = Math.sqrt(dx * dx + dz * dz);

            if (distToPlayer >= exclusionRadius) {
                return new Vector3d(x, 0, z);
            }
        }

        // Fallback after 10 retries: place just outside exclusion zone at random angle
        double fallbackAngle = random.nextDouble() * 2.0 * Math.PI;
        double fallbackDist = Math.min(exclusionRadius + 1.0, arenaRadius);
        return new Vector3d(
            Math.cos(fallbackAngle) * fallbackDist,
            0,
            Math.sin(fallbackAngle) * fallbackDist
        );
    }

    /**
     * Clamps a position to the circular arena bounds.
     *
     * <p>Uses circular distance check instead of per-axis square clamp.
     * A square clamp allows diagonal positions at r×√2 ≈ 1.41× the radius,
     * which places mobs deep inside arena walls.
     *
     * @param pos The position to clamp
     * @param maxRadius Maximum distance from origin (circular)
     * @return Position clamped to within maxRadius of origin
     */
    private Vector3d clampToArenaBounds(@Nonnull Vector3d pos, double maxRadius) {
        double dist = Math.sqrt(pos.x * pos.x + pos.z * pos.z);
        if (dist <= maxRadius) {
            return pos;
        }
        double scale = maxRadius / dist;
        return new Vector3d(pos.x * scale, pos.y, pos.z * scale);
    }

    // ═══════════════════════════════════════════════════════════════════
    // DEATH TRACKING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Records a mob death in the realm using entity reference.
     *
     * <p>This method should be called by {@code RealmMobDeathListener} when
     * an entity with a {@code RealmMobComponent} dies.
     *
     * <p>This marks the mob as killed in the persistent mob state, preventing
     * it from being respawned. The entity ref is also removed from the alive set.
     *
     * @param realmId   The realm ID
     * @param entityRef The entity reference of the dead mob
     */
    public void onMobDeath(@Nonnull UUID realmId, @Nonnull Ref<EntityStore> entityRef) {
        SpawnState state = realmSpawnStates.get(realmId);
        if (state == null) {
            return;
        }

        // Remove from alive refs
        state.aliveEntityRefs.remove(entityRef);
        state.killCount.incrementAndGet();

        // Find and mark the corresponding mob state as killed
        // This prevents the mob from being respawned
        for (RealmMobState mobState : state.mobStates.values()) {
            Ref<EntityStore> mobRef = mobState.getEntityRef();
            if (mobRef != null && mobRef.equals(entityRef)) {
                mobState.markKilled();
                LOGGER.atFine().log("Marked mob %s as killed in realm %s",
                    mobState.getMobId().toString().substring(0, 8),
                    realmId.toString().substring(0, 8));
                break;
            }
        }
    }

    /**
     * Records a mob death in the realm using entity UUID.
     *
     * <p>Legacy method for compatibility. Searches for matching entity ref.
     *
     * @param realmId  The realm ID
     * @param entityId The entity UUID of the dead mob (for backwards compatibility)
     * @deprecated Use {@link #onMobDeath(UUID, Ref)} instead
     */
    @Deprecated
    public void onMobDeath(@Nonnull UUID realmId, @Nonnull UUID entityId) {
        SpawnState state = realmSpawnStates.get(realmId);
        if (state != null) {
            // Remove any invalid refs and increment kill count
            // The actual ref removal happens via the Ref-based overload
            state.killCount.incrementAndGet();
            // Clean up any invalid refs
            state.aliveEntityRefs.removeIf(ref -> !ref.isValid());
        }
    }

    /**
     * Gets the number of mobs still alive in a realm.
     *
     * <p>This uses the persistent mob state to determine the count, which is
     * authoritative even if some entity refs have become invalid (despawned).
     * Mobs that despawned but weren't killed still count as alive and will be respawned.
     *
     * @param realmId The realm ID
     * @return Number of alive mobs, or -1 if realm not tracked
     */
    public int getAliveMobCount(@Nonnull UUID realmId) {
        SpawnState state = realmSpawnStates.get(realmId);
        if (state == null) {
            return -1;
        }
        // Use persistent mob state for authoritative count
        return state.getAliveMobCount();
    }

    /**
     * Gets the set of alive mob entity refs for a realm (for map markers, etc.).
     *
     * @param realmId The realm ID
     * @return Unmodifiable set of alive entity refs, or empty set if not tracked
     */
    @Nonnull
    public Set<Ref<EntityStore>> getAliveEntityRefs(@Nonnull UUID realmId) {
        SpawnState state = realmSpawnStates.get(realmId);
        if (state == null) {
            return Set.of();
        }
        return Collections.unmodifiableSet(state.aliveEntityRefs);
    }

    /**
     * Enforces arena boundaries for all alive mobs in a realm.
     *
     * <p>Mobs that exceed the computed arena radius are clamped back to the
     * arena edge at the same angle — visually they appear to hit an invisible wall.
     * This replaces the unreliable NPC leash system whose radius cannot be set
     * at runtime (LeashDistance/HardLeashDistance are final fields baked into
     * the NPC role template at server load time).
     *
     * <p>MUST be called on the world thread (ECS store access required).
     *
     * @param realmId The realm ID
     * @param world The realm's world instance
     */
    public void enforceArenaBoundaries(@Nonnull UUID realmId, @Nonnull World world) {
        SpawnState state = realmSpawnStates.get(realmId);
        if (state == null) {
            return;
        }

        double arenaRadius = state.mapData.computeArenaRadius();
        // Buffer: small leeway for natural movement, but tight enough to prevent
        // mobs from reaching wall edges and falling off (stuck 20+ blocks below).
        double hardEnforceRadius = arenaRadius + 5;
        double hardEnforceRadiusSq = hardEnforceRadius * hardEnforceRadius;
        // Teleport destination: well inside the arena, not at the edge.
        // Placing at the edge could cause the mob to immediately walk back out.
        double teleportRadius = arenaRadius * 0.85;
        var store = world.getEntityStore().getStore();

        // Terrain data for ground-level lookup when rescuing drowned mobs
        int maxScanY = (int) io.github.larsonix.trailoforbis.maps.templates
            .RealmTemplateRegistry.getBaseYForBiome(state.mapData.biome()) + 30;
        var terrainMaterials = state.mapData.biome().getTerrainMaterials();

        // Cache player positions for proximity checks.
        // A mob near a player is likely in combat — don't teleport it.
        List<Vector3d> playerPositions = new ArrayList<>();
        for (var player : world.getPlayers()) {
            if (player != null) {
                playerPositions.add(player.getTransformComponent().getPosition().clone());
            }
        }
        double chaseRangeSq = 40.0 * 40.0; // If a player is within 40 blocks, mob is likely chasing

        for (Ref<EntityStore> ref : state.aliveEntityRefs) {
            if (!ref.isValid()) continue;
            try {
                TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
                if (transform == null) continue;

                Vector3d pos = transform.getPosition();
                int bx = (int) Math.floor(pos.x);
                int by = (int) Math.floor(pos.y);
                int bz = (int) Math.floor(pos.z);

                // ── SUFFOCATION CHECK: mob body inside solid blocks ──
                // ALWAYS rescue — being stuck inside terrain is never intentional,
                // even during active combat. Checks both feet and head positions.
                BlockType blockFeet = world.getBlockType(bx, by, bz);
                BlockType blockHead = world.getBlockType(bx, by + 1, bz);
                BlockType emptyBlock = BlockType.getAssetMap().getAsset("Empty");
                boolean suffocating = blockFeet != null && blockFeet != emptyBlock
                    && blockHead != null && blockHead != emptyBlock;

                if (suffocating) {
                    int groundY = TerrainUtils.findGroundLevel(world, bx, bz, maxScanY, terrainMaterials);
                    transform.teleportPosition(new Vector3d(bx + 0.5, groundY, bz + 0.5));
                    continue;
                }

                // ── FLUID CHECK: drowning or lava submersion ──
                // Rescue only when no player is nearby — mobs in combat should
                // take environmental damage naturally (lava hurts, water drowns).
                boolean submerged = false;
                com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk chunk =
                    world.getChunkIfInMemory(
                        com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(bx, bz));
                if (chunk != null) {
                    int fluidFeet = chunk.getFluidId(bx, by, bz);
                    int fluidHead = chunk.getFluidId(bx, by + 1, bz);
                    submerged = (fluidFeet != 0 && fluidHead != 0);
                }

                if (submerged) {
                    boolean playerNearby = false;
                    for (Vector3d pp : playerPositions) {
                        double pdx = pos.x - pp.x;
                        double pdz = pos.z - pp.z;
                        if (pdx * pdx + pdz * pdz < chaseRangeSq) {
                            playerNearby = true;
                            break;
                        }
                    }
                    if (!playerNearby) {
                        // No player nearby — rescue to dry ground
                        int groundY = TerrainUtils.findGroundLevel(world, bx, bz, maxScanY, terrainMaterials);
                        boolean groundDry = true;
                        if (chunk != null) {
                            groundDry = chunk.getFluidId(bx, groundY, bz) == 0;
                        }
                        if (!groundDry) {
                            // Ground at this XZ is also submerged — teleport toward arena center
                            double dist = Math.sqrt(pos.x * pos.x + pos.z * pos.z);
                            if (dist > 1.0) {
                                double scale = Math.min(teleportRadius, dist * 0.5) / dist;
                                int safeX = (int) (pos.x * scale);
                                int safeZ = (int) (pos.z * scale);
                                groundY = TerrainUtils.findGroundLevel(world, safeX, safeZ, maxScanY, terrainMaterials);
                                bx = safeX;
                                bz = safeZ;
                            }
                        }
                        transform.teleportPosition(new Vector3d(bx + 0.5, groundY, bz + 0.5));
                    }
                    continue; // Skip distance check — already handled
                }

                // ── DISTANCE CHECK: too far from arena center ──
                double distSq = pos.x * pos.x + pos.z * pos.z;

                if (distSq > hardEnforceRadiusSq) {
                    // Check if any player is nearby — if so, mob is chasing them
                    boolean playerNearby = false;
                    for (Vector3d pp : playerPositions) {
                        double dx = pos.x - pp.x;
                        double dz = pos.z - pp.z;
                        if (dx * dx + dz * dz < chaseRangeSq) {
                            playerNearby = true;
                            break;
                        }
                    }
                    if (playerNearby) {
                        continue; // Player is close — mob is likely chasing, don't teleport
                    }

                    // No player nearby and far outside arena — teleport inward
                    double dist = Math.sqrt(distSq);
                    double scale = teleportRadius / dist;
                    double newX = pos.x * scale;
                    double newZ = pos.z * scale;
                    int newGroundY = TerrainUtils.findGroundLevel(
                        world, (int) newX, (int) newZ, maxScanY, terrainMaterials);
                    transform.teleportPosition(new Vector3d(newX, newGroundY, newZ));
                }
            } catch (Exception e) {
                // Entity ref became invalid during iteration — skip
            }
        }
    }

    /**
     * Gets the total kill count for a realm.
     *
     * @param realmId The realm ID
     * @return Kill count, or -1 if realm not tracked
     */
    public int getKillCount(@Nonnull UUID realmId) {
        SpawnState state = realmSpawnStates.get(realmId);
        return state != null ? state.killCount.get() : -1;
    }

    /**
     * Checks if all mobs have been spawned and killed.
     *
     * <p>Uses the persistent mob state to determine if all mobs are dead.
     * This is authoritative even if entity refs become invalid.
     *
     * @param realmId The realm ID
     * @return true if realm is cleared
     */
    public boolean isRealmCleared(@Nonnull UUID realmId) {
        SpawnState state = realmSpawnStates.get(realmId);
        if (state == null) {
            return false;
        }

        // All mobs spawned and all dead (using persistent state)
        return state.spawnedCount.get() >= state.distribution.totalCount()
            && state.getAliveMobCount() == 0;
    }

    // ═══════════════════════════════════════════════════════════════════
    // DESPAWN RECOVERY
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Checks for despawned mobs and respawns them at their original positions.
     *
     * <p>This method should be called periodically (e.g., every 5 seconds) to
     * detect mobs that have despawned unexpectedly (chunk unloading, engine cleanup,
     * etc.) and respawn them.
     *
     * <p>Only mobs that are marked as alive but have invalid entity refs will be
     * respawned. Mobs that were killed via combat are not respawned.
     *
     * @param realm The realm instance
     * @return Number of mobs respawned
     */
    public int checkAndRespawnDespawnedMobs(@Nonnull RealmInstance realm) {
        Objects.requireNonNull(realm, "realm cannot be null");

        SpawnState state = realmSpawnStates.get(realm.getRealmId());
        if (state == null) {
            return 0;
        }

        World world = realm.getWorld();
        if (world == null) {
            return 0;
        }

        // Diagnostic: count all mob states by their status
        int totalMobs = state.mobStates.size();
        int aliveCount = 0;
        int deadCount = 0;
        int validRefCount = 0;
        int nullRefCount = 0;
        int invalidRefCount = 0;

        for (RealmMobState mobState : state.mobStates.values()) {
            if (mobState.isAlive()) {
                aliveCount++;
                Ref<EntityStore> ref = mobState.getEntityRef();
                if (ref == null) {
                    nullRefCount++;
                } else if (ref.isValid()) {
                    validRefCount++;
                } else {
                    invalidRefCount++;
                }
            } else {
                deadCount++;
            }
        }

        // Get mobs that need respawning
        List<RealmMobState> needRespawn = state.getMobsNeedingRespawn();
        if (needRespawn.isEmpty()) {
            return 0;
        }

        // Throttle respawns to batch size per cycle to prevent performance spikes
        int batchSize = calculator.getConfig().getSpawnBatchSize();

        // Diagnostic - use FINE level to avoid log spam
        LOGGER.atFine().log("[DespawnRecovery] Realm %s state: total=%d, alive=%d (validRef=%d, nullRef=%d, invalidRef=%d), dead=%d, needRespawn=%d, batch=%d",
            realm.getRealmId().toString().substring(0, 8),
            totalMobs, aliveCount, validRefCount, nullRefCount, invalidRefCount, deadCount,
            needRespawn.size(), batchSize);

        int respawnedCount = 0;
        int abandonedCount = 0;

        for (RealmMobState mobState : needRespawn) {
            if (respawnedCount >= batchSize) {
                break; // Throttle: remaining will be picked up next cycle
            }

            // Check max respawn attempts — if a mob keeps despawning (e.g., its
            // NPC behavior tree includes ActionDespawn), stop trying and write it off.
            if (mobState.getRespawnCount() >= MAX_RESPAWN_ATTEMPTS) {
                mobState.markKilled();
                state.aliveEntityRefs.removeIf(ref -> !ref.isValid());
                realm.getCompletionTracker().onMonsterKilledNoKiller(1);
                abandonedCount++;
                LOGGER.atWarning().log("[DespawnRecovery] Abandoned %s (id=%s) after %d respawn attempts in realm %s",
                    mobState.getMobTypeId(),
                    mobState.getMobId().toString().substring(0, 8),
                    mobState.getRespawnCount(),
                    realm.getRealmId().toString().substring(0, 8));
                continue;
            }

            // Respawn the mob at its original position
            boolean success = respawnMob(world, state, mobState);
            if (success) {
                respawnedCount++;
            }
        }

        if (respawnedCount > 0 || abandonedCount > 0) {
            LOGGER.atInfo().log("[DespawnRecovery] Realm %s: respawned %d, abandoned %d (of %d needing respawn)",
                realm.getRealmId().toString().substring(0, 8),
                respawnedCount, abandonedCount, needRespawn.size());
        }

        return respawnedCount;
    }

    /**
     * Respawns a single mob at its original position.
     *
     * <p>Preserves the mob's elite status from the original spawn.
     *
     * @param world The world to spawn in
     * @param state The spawn state
     * @param mobState The mob state to respawn
     * @return true if respawn was successful
     */
    private boolean respawnMob(
            @Nonnull World world,
            @Nonnull SpawnState state,
            @Nonnull RealmMobState mobState) {

        // Create spawn request using original mob data (including isElite)
        SpawnRequest request;
        if (mobState.isReinforcement()) {
            request = SpawnRequest.reinforcement(
                state.realmId,
                mobState.getMobTypeId(),
                mobState.getClassification(),
                mobState.getSpawnPosition(),
                mobState.getWaveNumber(),
                mobState.isElite(),
                state.mapData,
                state.playerCount
            );
        } else {
            request = SpawnRequest.initial(
                state.realmId,
                mobState.getMobTypeId(),
                mobState.getClassification(),
                mobState.getSpawnPosition(),
                mobState.isElite(),
                state.mapData,
                state.playerCount
            );
        }

        // Spawn the entity
        RealmEntitySpawner.SpawnResult result = entitySpawner.spawnNow(world, request);

        if (result.success()) {
            Ref<EntityStore> newRef = result.entityRef();

            // Update mob state with new entity reference
            mobState.recordRespawn(newRef);
            state.aliveEntityRefs.add(newRef);

            LOGGER.atFine().log("[DespawnRecovery] Respawned %s%s (id=%s) at (%.1f, %.1f, %.1f), respawn count: %d",
                mobState.getMobTypeId(),
                mobState.isElite() ? " (ELITE)" : "",
                mobState.getMobId().toString().substring(0, 8),
                mobState.getSpawnPosition().x,
                mobState.getSpawnPosition().y,
                mobState.getSpawnPosition().z,
                mobState.getRespawnCount());

            return true;
        } else {
            LOGGER.atWarning().log("[DespawnRecovery] Failed to respawn %s: %s",
                mobState.getMobTypeId(), result.error());
            return false;
        }
    }

    /**
     * Gets the count of mobs that need respawning in a realm.
     *
     * @param realmId The realm ID
     * @return Number of mobs needing respawn, or 0 if realm not tracked
     */
    public int getDespawnedMobCount(@Nonnull UUID realmId) {
        SpawnState state = realmSpawnStates.get(realmId);
        if (state == null) {
            return 0;
        }
        return state.getMobsNeedingRespawn().size();
    }

    /**
     * Despawns all remaining mobs in a realm using ECS component query.
     *
     * <p>This method uses {@code store.forEachChunk()} to iterate all entities
     * with a {@link RealmMobComponent}, filtering by realm ID. This approach uses fresh
     * entity refs from the current archetype chunks, avoiding the stale-ref problem where
     * {@code Ref.isValid()} returns false after ECS store compaction.
     *
     * <p>This method should be called when a realm is completed (victory) or timed out
     * to remove any remaining mobs. It:
     * <ul>
     *   <li>Queries all entities with {@link RealmMobComponent} belonging to this realm</li>
     *   <li>Removes them via {@code commandBuffer.removeEntity()}</li>
     *   <li>Marks all internal mob states as killed</li>
     *   <li>Clears the alive entity refs set</li>
     * </ul>
     *
     * <p><b>IMPORTANT:</b> This method must be called on the realm's world thread.
     *
     * @param realm The realm instance
     * @return Number of mobs despawned
     */
    public int despawnAllMobs(@Nonnull RealmInstance realm) {
        Objects.requireNonNull(realm, "realm cannot be null");

        UUID realmId = realm.getRealmId();

        World world = realm.getWorld();
        if (world == null || !world.isAlive()) {
            LOGGER.atWarning().log("[VictoryCleanup] Cannot despawn mobs: realm %s has no valid world",
                realmId.toString().substring(0, 8));
            return 0;
        }

        // Query the ECS store for all entities with RealmMobComponent belonging to this realm.
        // This uses fresh refs from archetypeChunk.getReferenceTo() which are valid during iteration,
        // unlike the stored refs in SpawnState which become stale after ECS compaction.
        Store<EntityStore> store = world.getEntityStore().getStore();
        ComponentType<EntityStore, RealmMobComponent> realmMobType = RealmMobComponent.getComponentType();
        Query<EntityStore> query = Archetype.of(realmMobType);

        AtomicInteger despawnCount = new AtomicInteger(0);

        // Use forEachChunk (sequential, world-thread-safe) instead of forEachEntityParallel.
        // forEachEntityParallel dispatches to ForkJoinPool workers, but Store.getComponent()
        // requires the world thread — causing "Assert not in thread!" crashes.
        store.forEachChunk(query, (archetypeChunk, commandBuffer) -> {
            for (int i = 0; i < archetypeChunk.size(); i++) {
                Ref<EntityStore> ref = archetypeChunk.getReferenceTo(i);
                if (ref == null || !ref.isValid()) continue;

                RealmMobComponent comp = store.getComponent(ref, realmMobType);
                if (comp == null || !comp.belongsToRealm(realmId)) continue;

                commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
                despawnCount.incrementAndGet();
            }
        });

        // Also mark all internal mob states as killed
        SpawnState state = realmSpawnStates.get(realmId);
        if (state != null) {
            for (RealmMobState mobState : state.mobStates.values()) {
                if (mobState.isAlive()) {
                    mobState.markKilled();
                }
            }
            state.aliveEntityRefs.clear();
        }

        LOGGER.atInfo().log("[VictoryCleanup] Realm %s: despawned %d mobs via ECS query",
            realmId.toString().substring(0, 8), despawnCount.get());

        return despawnCount.get();
    }

    // ═══════════════════════════════════════════════════════════════════
    // CLEANUP
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Cleans up spawning state for a realm.
     *
     * @param realmId The realm ID
     */
    public void cleanupRealm(@Nonnull UUID realmId) {
        SpawnState state = realmSpawnStates.remove(realmId);
        if (state != null) {
            LOGGER.atInfo().log("Cleaned up spawn state for realm %s: %d spawned, %d killed",
                realmId, state.spawnedCount.get(), state.killCount.get());
        }
        if (bossStructurePlacer != null) {
            bossStructurePlacer.onRealmClosed(realmId);
        }
    }

    /**
     * Cleans up all realm spawn states and pending spawns.
     */
    public void shutdown() {
        int count = realmSpawnStates.size();
        realmSpawnStates.clear();
        mobPools.clear();
        entitySpawner.clearPendingSpawns();
        if (bossStructurePlacer != null) {
            bossStructurePlacer.shutdown();
        }
        LOGGER.atInfo().log("RealmMobSpawner shutdown, cleared %d realm states", count);
    }

    // ═══════════════════════════════════════════════════════════════════
    // GETTERS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the mob calculator.
     *
     * @return The calculator
     */
    @Nonnull
    public RealmMobCalculator getCalculator() {
        return calculator;
    }

    /**
     * Gets the entity spawner.
     *
     * @return The entity spawner
     */
    @Nonnull
    public RealmEntitySpawner getEntitySpawner() {
        return entitySpawner;
    }

    /**
     * Processes any pending spawns from the entity spawner.
     *
     * <p>Call this periodically (e.g., once per tick) to process spawns
     * that were queued due to ECS timing constraints.
     *
     * @return Number of spawns processed
     */
    public int processPendingSpawns() {
        return entitySpawner.processPendingSpawns();
    }

    /**
     * Gets spawn statistics for a realm.
     *
     * <p>Uses the persistent mob state for authoritative alive count.
     *
     * @param realmId The realm ID
     * @return Spawn statistics, or null if not tracked
     */
    @Nullable
    public SpawnStatistics getStatistics(@Nonnull UUID realmId) {
        SpawnState state = realmSpawnStates.get(realmId);
        if (state == null) {
            return null;
        }
        // Use persistent mob state for authoritative alive count
        return new SpawnStatistics(
            state.distribution.totalCount(),
            state.spawnedCount.get(),
            state.killCount.get(),
            state.getAliveMobCount()
        );
    }

    // ═══════════════════════════════════════════════════════════════════
    // NESTED CLASSES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Internal spawn state tracking per realm.
     *
     * <p>This class now uses persistent mob state tracking via {@link RealmMobState}
     * objects instead of just entity refs. This allows mobs to be respawned if they
     * despawn unexpectedly (chunk unloading, engine cleanup, etc.).
     */
    private static class SpawnState {
        final UUID realmId;
        final RealmMapData mapData;
        final SpawnDistribution distribution;
        final WaveParameters waves;
        final BiomeMobPool pool;
        final List<MonsterSpawnPoint> spawnPoints;
        final int mapLevel;
        final int playerCount; // Player count at spawn time for stat scaling
        final Vector3d playerSpawnPosition; // Position to exclude mobs from (entry/respawn location)

        final AtomicInteger spawnedCount = new AtomicInteger(0);
        final AtomicInteger killCount = new AtomicInteger(0);

        /**
         * Persistent mob states keyed by mob ID.
         * <p>This tracks ALL mobs that should exist (alive or dead), allowing
         * respawn recovery if mobs despawn unexpectedly.
         */
        final Map<UUID, RealmMobState> mobStates = new ConcurrentHashMap<>();

        /**
         * Legacy: Set of entity refs for mobs currently alive.
         * <p>Kept for backwards compatibility and quick alive-count checks.
         * Refs may become invalid if mobs despawn - use mobStates for authoritative state.
         */
        final Set<Ref<EntityStore>> aliveEntityRefs = ConcurrentHashMap.newKeySet();

        volatile boolean initialWaveComplete = false;
        volatile int currentWaveNumber = 0;

        SpawnState(
                UUID realmId,
                RealmMapData mapData,
                SpawnDistribution distribution,
                WaveParameters waves,
                BiomeMobPool pool,
                List<MonsterSpawnPoint> spawnPoints,
                int playerCount,
                Vector3d playerSpawnPosition) {
            this.realmId = realmId;
            this.mapData = mapData;
            this.distribution = distribution;
            this.waves = waves;
            this.pool = pool;
            this.spawnPoints = spawnPoints;
            this.mapLevel = mapData.level();
            this.playerCount = Math.max(1, playerCount); // At least 1 player
            this.playerSpawnPosition = playerSpawnPosition;
        }

        /**
         * Gets the count of mobs that should be alive (based on mobStates).
         * <p>This is the authoritative count - it doesn't depend on entity refs being valid.
         *
         * @return Number of mobs that should be alive
         */
        int getAliveMobCount() {
            return (int) mobStates.values().stream()
                .filter(RealmMobState::isAlive)
                .count();
        }

        /**
         * Gets mobs that need respawning (alive but no valid entity ref).
         *
         * @return List of mob states that need respawning
         */
        List<RealmMobState> getMobsNeedingRespawn() {
            return mobStates.values().stream()
                .filter(RealmMobState::needsRespawn)
                .collect(Collectors.toList());
        }
    }

    /**
     * Result of a spawn operation.
     */
    public record SpawnResult(
        boolean success,
        int spawned,
        int target,
        @Nullable String error
    ) {
        public static SpawnResult success(int spawned, int target) {
            return new SpawnResult(true, spawned, target, null);
        }

        public static SpawnResult failed(String error) {
            return new SpawnResult(false, 0, 0, error);
        }
    }

    /**
     * Information about a spawned mob.
     *
     * @param entityRef      The entity reference (may be null or invalid if despawned)
     * @param mobTypeId      The NPC type identifier (e.g., "Trork_Guard")
     * @param classification The mob classification (NORMAL or BOSS)
     * @param position       The spawn position
     * @param level          The realm level when spawned
     * @param isElite        Whether this mob rolled elite at spawn time
     */
    public record SpawnedMob(
        @Nullable Ref<EntityStore> entityRef,
        @Nonnull String mobTypeId,
        @Nonnull MobClassification classification,
        @Nonnull Vector3d position,
        int level,
        boolean isElite
    ) {
        /**
         * Checks if this mob has a valid entity reference.
         *
         * @return true if the entity reference is valid
         */
        public boolean hasValidRef() {
            return entityRef != null && entityRef.isValid();
        }
    }

    /**
     * Statistics about spawning in a realm.
     */
    public record SpawnStatistics(
        int targetTotal,
        int spawned,
        int killed,
        int alive
    ) {
        public double completionPercentage() {
            return targetTotal > 0 ? (double) killed / targetTotal * 100 : 0;
        }

        public double spawnProgress() {
            return targetTotal > 0 ? (double) spawned / targetTotal * 100 : 0;
        }
    }
}
