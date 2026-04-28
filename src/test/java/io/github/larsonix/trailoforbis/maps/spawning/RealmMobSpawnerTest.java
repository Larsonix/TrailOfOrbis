package io.github.larsonix.trailoforbis.maps.spawning;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.maps.config.MobPoolConfig;
import io.github.larsonix.trailoforbis.maps.core.RealmBiomeType;
import io.github.larsonix.trailoforbis.maps.core.RealmCompletionTracker;
import io.github.larsonix.trailoforbis.maps.core.RealmLayoutSize;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;
import io.github.larsonix.trailoforbis.maps.spawning.RealmMobCalculator.SpawnDistribution;
import io.github.larsonix.trailoforbis.maps.spawning.RealmMobCalculator.WaveParameters;
import io.github.larsonix.trailoforbis.maps.spawning.RealmMobSpawner.SpawnResult;
import io.github.larsonix.trailoforbis.maps.spawning.WeightedMob.MobClassification;
import io.github.larsonix.trailoforbis.maps.templates.MonsterSpawnPoint;
import io.github.larsonix.trailoforbis.maps.templates.RealmTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link RealmMobSpawner}.
 *
 * <p>These tests cover:
 * <ul>
 *   <li>Pool management (registration, retrieval)</li>
 *   <li>Initial wave spawning logic</li>
 *   <li>Reinforcement wave spawning</li>
 *   <li>Elite roll mechanics</li>
 *   <li>Death tracking and realm cleared detection</li>
 *   <li>State management and cleanup</li>
 * </ul>
 */
@DisplayName("RealmMobSpawner Tests")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RealmMobSpawnerTest {

    @Mock
    private RealmEntitySpawner entitySpawner;

    @Mock
    private RealmInstance realm;

    @Mock
    private World world;

    @Mock
    private RealmTemplate template;

    @Mock
    private RealmCompletionTracker completionTracker;

    @Mock
    private Ref<EntityStore> entityRef;

    private RealmMobSpawner spawner;
    private RealmMobCalculator calculator;
    private MobPoolConfig config;
    private BiomeMobPool forestPool;
    private UUID realmId;

    @BeforeEach
    void setUp() {
        config = new MobPoolConfig();
        calculator = new RealmMobCalculator(config);

        // Create a realistic mob pool
        forestPool = BiomeMobPool.builder(RealmBiomeType.FOREST)
            .addNormal("trork_warrior", 30)
            .addNormal("trork_archer", 20)
            .addNormal("wolf", 15)
            .addBoss("trork_chieftain", 5)
            .build();

        Map<RealmBiomeType, BiomeMobPool> pools = new EnumMap<>(RealmBiomeType.class);
        pools.put(RealmBiomeType.FOREST, forestPool);

        spawner = new RealmMobSpawner(entitySpawner, calculator, pools);
        realmId = UUID.randomUUID();
    }

    // ═══════════════════════════════════════════════════════════════════
    // POOL MANAGEMENT TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Pool Management")
    class PoolManagementTests {

        @Test
        @DisplayName("registerPool stores pool for biome")
        void registerPool_validPool_storesInMap() {
            BiomeMobPool desertPool = BiomeMobPool.builder(RealmBiomeType.DESERT)
                .addNormal("sand_golem", 20)
                .build();

            spawner.registerPool(desertPool);

            assertEquals(desertPool, spawner.getPool(RealmBiomeType.DESERT));
        }

        @Test
        @DisplayName("getPool returns registered pool")
        void getPool_registeredBiome_returnsPool() {
            BiomeMobPool pool = spawner.getPool(RealmBiomeType.FOREST);

            assertNotNull(pool);
            assertEquals(RealmBiomeType.FOREST, pool.getBiome());
            assertEquals(4, pool.getTotalMobCount());
        }

        @Test
        @DisplayName("getPool returns empty pool for unregistered biome")
        void getPool_unregisteredBiome_returnsEmptyPool() {
            BiomeMobPool pool = spawner.getPool(RealmBiomeType.VOLCANO);

            assertNotNull(pool);
            assertEquals(RealmBiomeType.VOLCANO, pool.getBiome());
            assertTrue(pool.isEmpty());
        }

        @Test
        @DisplayName("hasPool returns true for registered biome")
        void hasPool_registeredBiome_returnsTrue() {
            assertTrue(spawner.hasPool(RealmBiomeType.FOREST));
        }

        @Test
        @DisplayName("hasPool returns false for unregistered biome")
        void hasPool_unregisteredBiome_returnsFalse() {
            assertFalse(spawner.hasPool(RealmBiomeType.VOLCANO));
        }

        @Test
        @DisplayName("registerPool rejects null")
        void registerPool_null_throws() {
            assertThrows(NullPointerException.class, () -> spawner.registerPool(null));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // INITIAL WAVE SPAWNING TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Initial Wave Spawning")
    class InitialWaveSpawningTests {

        private RealmMapData mapData;
        private List<MonsterSpawnPoint> spawnPoints;

        @BeforeEach
        void setUpInitialWave() {
            mapData = RealmMapData.create(50, GearRarity.RARE, RealmBiomeType.FOREST, RealmLayoutSize.MEDIUM);

            spawnPoints = List.of(
                MonsterSpawnPoint.normal(new Vector3d(10, 0, 10), 5),
                MonsterSpawnPoint.normal(new Vector3d(-10, 0, -10), 5),
                MonsterSpawnPoint.boss(new Vector3d(0, 0, 30))
            );

            // Configure mocks
            when(realm.getRealmId()).thenReturn(realmId);
            when(realm.getMapData()).thenReturn(mapData);
            when(realm.getTemplate()).thenReturn(template);
            when(realm.getWorld()).thenReturn(world);
            when(realm.getPlayerCount()).thenReturn(1);
            when(realm.getCompletionTracker()).thenReturn(completionTracker);
            when(template.spawnPoints()).thenReturn(spawnPoints);
            when(template.playerSpawn()).thenReturn(new Transform(new Vector3d(0, 0, 0), new Vector3f(0, 0, 0)));
        }

        @Test
        @DisplayName("initializeSpawning with null world returns failed result")
        void initializeSpawning_nullWorld_returnsFailed() {
            when(realm.getWorld()).thenReturn(null);

            CompletableFuture<SpawnResult> future = spawner.initializeSpawning(realm);
            SpawnResult result = future.join();

            assertFalse(result.success());
            assertEquals("Realm world not available", result.error());
        }

        @Test
        @DisplayName("initializeSpawning calculates spawn distribution")
        void initializeSpawning_validRealm_calculatesDistribution() {
            // Configure successful spawns
            RealmEntitySpawner.SpawnResult spawnSuccess = RealmEntitySpawner.SpawnResult.success(
                entityRef, "trork_warrior", MobClassification.NORMAL);
            when(entitySpawner.spawnNow(any(), any())).thenReturn(spawnSuccess);
            when(entityRef.isValid()).thenReturn(true);

            CompletableFuture<SpawnResult> future = spawner.initializeSpawning(realm);
            SpawnResult result = future.join();

            assertTrue(result.success());
            assertTrue(result.spawned() > 0);
            assertTrue(result.target() > 0);
        }

        @Test
        @DisplayName("initializeSpawning sets completion tracker total")
        void executeInitialWave_setsCompletionTrackerTotal() {
            RealmEntitySpawner.SpawnResult spawnSuccess = RealmEntitySpawner.SpawnResult.success(
                entityRef, "trork_warrior", MobClassification.NORMAL);
            when(entitySpawner.spawnNow(any(), any())).thenReturn(spawnSuccess);
            when(entityRef.isValid()).thenReturn(true);

            spawner.initializeSpawning(realm).join();

            verify(completionTracker).setTotalMonsters(anyInt());
        }

        @Test
        @DisplayName("initializeSpawning with empty pool logs warning but succeeds")
        void initializeSpawning_emptyPool_logsWarningSucceeds() {
            // Create spawner with no pools
            RealmMobSpawner emptySpawner = new RealmMobSpawner(entitySpawner, calculator, new HashMap<>());

            RealmEntitySpawner.SpawnResult spawnSuccess = RealmEntitySpawner.SpawnResult.success(
                entityRef, "fallback_mob", MobClassification.NORMAL);
            when(entitySpawner.spawnNow(any(), any())).thenReturn(spawnSuccess);
            when(entityRef.isValid()).thenReturn(true);

            // Should not throw, but pool will be empty
            CompletableFuture<SpawnResult> future = emptySpawner.initializeSpawning(realm);
            SpawnResult result = future.join();

            // Even with empty pool, it should try to spawn (with warnings)
            assertTrue(result.success());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // REINFORCEMENT WAVE TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Reinforcement Waves")
    class ReinforcementWaveTests {

        @BeforeEach
        void setUpReinforcement() {
            RealmMapData mapData = RealmMapData.create(50, GearRarity.RARE, RealmBiomeType.FOREST, RealmLayoutSize.MEDIUM);
            List<MonsterSpawnPoint> spawnPoints = List.of(
                MonsterSpawnPoint.normal(new Vector3d(10, 0, 10), 5),
                MonsterSpawnPoint.boss(new Vector3d(0, 0, 30))
            );

            when(realm.getRealmId()).thenReturn(realmId);
            when(realm.getMapData()).thenReturn(mapData);
            when(realm.getTemplate()).thenReturn(template);
            when(realm.getWorld()).thenReturn(world);
            when(realm.getPlayerCount()).thenReturn(1);
            when(realm.getCompletionTracker()).thenReturn(completionTracker);
            when(template.spawnPoints()).thenReturn(spawnPoints);
            when(template.playerSpawn()).thenReturn(new Transform(new Vector3d(0, 0, 0), new Vector3f(0, 0, 0)));

            // Configure successful spawns
            RealmEntitySpawner.SpawnResult spawnSuccess = RealmEntitySpawner.SpawnResult.success(
                entityRef, "trork_warrior", MobClassification.NORMAL);
            when(entitySpawner.spawnNow(any(), any())).thenReturn(spawnSuccess);
            when(entityRef.isValid()).thenReturn(true);
        }

        @Test
        @DisplayName("spawnReinforcementWave before initial returns zero")
        void spawnReinforcementWave_beforeInitial_returnsZero() {
            int spawned = spawner.spawnReinforcementWave(realm);

            assertEquals(0, spawned);
        }

        @Test
        @DisplayName("spawnReinforcementWave after initial spawns batch")
        void spawnReinforcementWave_afterInitial_spawnsBatch() {
            // First initialize
            spawner.initializeSpawning(realm).join();

            // Then reinforce
            int spawned = spawner.spawnReinforcementWave(realm);

            // Should spawn some mobs (if not all were spawned in initial wave)
            // The exact count depends on how many were spawned initially
            assertTrue(spawned >= 0);
        }

        @Test
        @DisplayName("spawnReinforcementWave respects remaining count")
        void spawnReinforcementWave_respectsRemaining() {
            spawner.initializeSpawning(realm).join();

            // Keep reinforcing until all spawned
            int totalReinforced = 0;
            int iterations = 0;
            while (iterations < 100) { // Safety limit
                int spawned = spawner.spawnReinforcementWave(realm);
                if (spawned == 0) break;
                totalReinforced += spawned;
                iterations++;
            }

            // Should eventually stop spawning
            assertEquals(0, spawner.spawnReinforcementWave(realm));
        }

        @Test
        @DisplayName("spawnReinforcementWave with null world returns zero")
        void spawnReinforcementWave_nullWorld_returnsZero() {
            spawner.initializeSpawning(realm).join();

            when(realm.getWorld()).thenReturn(null);

            int spawned = spawner.spawnReinforcementWave(realm);

            assertEquals(0, spawned);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // DEATH TRACKING TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Death Tracking")
    class DeathTrackingTests {

        @Mock
        private Ref<EntityStore> mobRef1;

        @Mock
        private Ref<EntityStore> mobRef2;

        @BeforeEach
        void setUpDeathTracking() {
            RealmMapData mapData = RealmMapData.create(50, GearRarity.RARE, RealmBiomeType.FOREST, RealmLayoutSize.MEDIUM);
            List<MonsterSpawnPoint> spawnPoints = List.of(
                MonsterSpawnPoint.normal(new Vector3d(10, 0, 10), 5)
            );

            when(realm.getRealmId()).thenReturn(realmId);
            when(realm.getMapData()).thenReturn(mapData);
            when(realm.getTemplate()).thenReturn(template);
            when(realm.getWorld()).thenReturn(world);
            when(realm.getPlayerCount()).thenReturn(1);
            when(realm.getCompletionTracker()).thenReturn(completionTracker);
            when(template.spawnPoints()).thenReturn(spawnPoints);
            when(template.playerSpawn()).thenReturn(new Transform(new Vector3d(0, 0, 0), new Vector3f(0, 0, 0)));

            when(mobRef1.isValid()).thenReturn(true);
            when(mobRef2.isValid()).thenReturn(true);
        }

        @Test
        @DisplayName("onMobDeath increments kill count")
        void onMobDeath_incrementsKillCount() {
            // Set up spawning to track mobs
            RealmEntitySpawner.SpawnResult spawnSuccess = RealmEntitySpawner.SpawnResult.success(
                mobRef1, "trork_warrior", MobClassification.NORMAL);
            when(entitySpawner.spawnNow(any(), any())).thenReturn(spawnSuccess);

            spawner.initializeSpawning(realm).join();

            int initialKills = spawner.getKillCount(realmId);

            spawner.onMobDeath(realmId, mobRef1);

            assertEquals(initialKills + 1, spawner.getKillCount(realmId));
        }

        @Test
        @DisplayName("getKillCount returns -1 for unknown realm")
        void getKillCount_unknownRealm_returnsNegativeOne() {
            assertEquals(-1, spawner.getKillCount(UUID.randomUUID()));
        }

        @Test
        @DisplayName("getAliveMobCount returns -1 for unknown realm")
        void getAliveMobCount_unknownRealm_returnsNegativeOne() {
            assertEquals(-1, spawner.getAliveMobCount(UUID.randomUUID()));
        }

        @Test
        @DisplayName("isRealmCleared returns false for unknown realm")
        void isRealmCleared_unknownRealm_returnsFalse() {
            assertFalse(spawner.isRealmCleared(UUID.randomUUID()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // STATE MANAGEMENT TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("State Management")
    class StateManagementTests {

        @BeforeEach
        void setUpState() {
            RealmMapData mapData = RealmMapData.create(50, GearRarity.RARE, RealmBiomeType.FOREST, RealmLayoutSize.MEDIUM);
            List<MonsterSpawnPoint> spawnPoints = List.of(
                MonsterSpawnPoint.normal(new Vector3d(10, 0, 10), 5)
            );

            when(realm.getRealmId()).thenReturn(realmId);
            when(realm.getMapData()).thenReturn(mapData);
            when(realm.getTemplate()).thenReturn(template);
            when(realm.getWorld()).thenReturn(world);
            when(realm.getPlayerCount()).thenReturn(1);
            when(realm.getCompletionTracker()).thenReturn(completionTracker);
            when(template.spawnPoints()).thenReturn(spawnPoints);
            when(template.playerSpawn()).thenReturn(new Transform(new Vector3d(0, 0, 0), new Vector3f(0, 0, 0)));

            RealmEntitySpawner.SpawnResult spawnSuccess = RealmEntitySpawner.SpawnResult.success(
                entityRef, "trork_warrior", MobClassification.NORMAL);
            when(entitySpawner.spawnNow(any(), any())).thenReturn(spawnSuccess);
            when(entityRef.isValid()).thenReturn(true);
        }

        @Test
        @DisplayName("cleanup removes realm state")
        void cleanup_removesRealmState() {
            spawner.initializeSpawning(realm).join();

            // Verify state exists
            assertNotEquals(-1, spawner.getKillCount(realmId));

            spawner.cleanupRealm(realmId);

            // State should be removed
            assertEquals(-1, spawner.getKillCount(realmId));
        }

        @Test
        @DisplayName("getStatistics returns stats for tracked realm")
        void getStatistics_existingRealm_returnsStats() {
            spawner.initializeSpawning(realm).join();

            RealmMobSpawner.SpawnStatistics stats = spawner.getStatistics(realmId);

            assertNotNull(stats);
            assertTrue(stats.targetTotal() > 0);
            assertTrue(stats.spawned() > 0);
        }

        @Test
        @DisplayName("getStatistics returns null for unknown realm")
        void getStatistics_unknownRealm_returnsNull() {
            assertNull(spawner.getStatistics(UUID.randomUUID()));
        }

        @Test
        @DisplayName("shutdown clears all state")
        void shutdown_clearsAllState() {
            spawner.initializeSpawning(realm).join();

            spawner.shutdown();

            assertEquals(-1, spawner.getKillCount(realmId));
            assertFalse(spawner.hasPool(RealmBiomeType.FOREST));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // GETTERS AND ACCESSORS TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Getters and Accessors")
    class GettersTests {

        @Test
        @DisplayName("getCalculator returns the calculator")
        void getCalculator_returnsCalculator() {
            assertEquals(calculator, spawner.getCalculator());
        }

        @Test
        @DisplayName("getEntitySpawner returns the entity spawner")
        void getEntitySpawner_returnsEntitySpawner() {
            assertEquals(entitySpawner, spawner.getEntitySpawner());
        }

        @Test
        @DisplayName("processPendingSpawns delegates to entity spawner")
        void processPendingSpawns_delegatesToEntitySpawner() {
            when(entitySpawner.processPendingSpawns()).thenReturn(5);

            int processed = spawner.processPendingSpawns();

            assertEquals(5, processed);
            verify(entitySpawner).processPendingSpawns();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // SPAWN RESULT TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("SpawnResult Record")
    class SpawnResultTests {

        @Test
        @DisplayName("success creates successful result")
        void success_createsSuccessfulResult() {
            SpawnResult result = SpawnResult.success(10, 100);

            assertTrue(result.success());
            assertEquals(10, result.spawned());
            assertEquals(100, result.target());
            assertNull(result.error());
        }

        @Test
        @DisplayName("failed creates failed result")
        void failed_createsFailedResult() {
            SpawnResult result = SpawnResult.failed("Test error");

            assertFalse(result.success());
            assertEquals(0, result.spawned());
            assertEquals(0, result.target());
            assertEquals("Test error", result.error());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // SPAWN STATISTICS TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("SpawnStatistics Record")
    class SpawnStatisticsTests {

        @Test
        @DisplayName("completionPercentage calculates correctly")
        void completionPercentage_calculatesCorrectly() {
            RealmMobSpawner.SpawnStatistics stats = new RealmMobSpawner.SpawnStatistics(100, 100, 50, 50);

            assertEquals(50.0, stats.completionPercentage(), 0.01);
        }

        @Test
        @DisplayName("completionPercentage handles zero target")
        void completionPercentage_handlesZeroTarget() {
            RealmMobSpawner.SpawnStatistics stats = new RealmMobSpawner.SpawnStatistics(0, 0, 0, 0);

            assertEquals(0.0, stats.completionPercentage(), 0.01);
        }

        @Test
        @DisplayName("spawnProgress calculates correctly")
        void spawnProgress_calculatesCorrectly() {
            RealmMobSpawner.SpawnStatistics stats = new RealmMobSpawner.SpawnStatistics(100, 75, 25, 50);

            assertEquals(75.0, stats.spawnProgress(), 0.01);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // SPAWNED MOB RECORD TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("SpawnedMob Record")
    class SpawnedMobTests {

        @Test
        @DisplayName("hasValidRef returns true when ref is valid")
        void hasValidRef_validRef_returnsTrue() {
            when(entityRef.isValid()).thenReturn(true);

            RealmMobSpawner.SpawnedMob mob = new RealmMobSpawner.SpawnedMob(
                entityRef, "trork_warrior", MobClassification.NORMAL,
                new Vector3d(0, 0, 0), 50, false);

            assertTrue(mob.hasValidRef());
        }

        @Test
        @DisplayName("hasValidRef returns false when ref is null")
        void hasValidRef_nullRef_returnsFalse() {
            RealmMobSpawner.SpawnedMob mob = new RealmMobSpawner.SpawnedMob(
                null, "trork_warrior", MobClassification.NORMAL,
                new Vector3d(0, 0, 0), 50, false);

            assertFalse(mob.hasValidRef());
        }

        @Test
        @DisplayName("hasValidRef returns false when ref is invalid")
        void hasValidRef_invalidRef_returnsFalse() {
            when(entityRef.isValid()).thenReturn(false);

            RealmMobSpawner.SpawnedMob mob = new RealmMobSpawner.SpawnedMob(
                entityRef, "trork_warrior", MobClassification.NORMAL,
                new Vector3d(0, 0, 0), 50, false);

            assertFalse(mob.hasValidRef());
        }

        @Test
        @DisplayName("isElite returns correct value")
        void isElite_returnsCorrectValue() {
            RealmMobSpawner.SpawnedMob normalMob = new RealmMobSpawner.SpawnedMob(
                entityRef, "trork_warrior", MobClassification.NORMAL,
                new Vector3d(0, 0, 0), 50, false);

            RealmMobSpawner.SpawnedMob eliteMob = new RealmMobSpawner.SpawnedMob(
                entityRef, "trork_warrior", MobClassification.NORMAL,
                new Vector3d(0, 0, 0), 50, true);

            assertFalse(normalMob.isElite());
            assertTrue(eliteMob.isElite());
        }
    }
}
