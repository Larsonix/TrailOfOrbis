package io.github.larsonix.trailoforbis.attributes;

import io.github.larsonix.trailoforbis.config.ConfigManager;
import io.github.larsonix.trailoforbis.config.RPGConfig;
import io.github.larsonix.trailoforbis.database.DataManager;
import io.github.larsonix.trailoforbis.database.models.PlayerData;
import io.github.larsonix.trailoforbis.database.repository.PlayerDataRepository;
import io.github.larsonix.trailoforbis.systems.StatProvider;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AttributeManager.
 *
 * <p>Tests core allocation and recalculation logic without ECS dependencies.
 */
public class AttributeManagerTest {
    @TempDir
    Path tempDir;

    private DataManager dataManager;
    private ConfigManager configManager;
    private PlayerDataRepository repository;
    private AttributeManager manager;

    @BeforeEach
    void setUp() {
        RPGConfig config = new RPGConfig();
        configManager = new ConfigManager(tempDir, config);
        dataManager = new DataManager(tempDir, config);
        assertTrue(dataManager.initialize(), "DataManager must initialize for tests");

        repository = new PlayerDataRepository(dataManager);
        AttributeCalculator calculator = new AttributeCalculator(config);

        // Use default base stats for testing
        StatProvider testProvider = playerId -> BaseStats.defaults();

        manager = new AttributeManager(repository, calculator, configManager, testProvider);
    }

    @AfterEach
    void tearDown() {
        if (dataManager != null) {
            dataManager.shutdown();
        }
    }

    // ==================== Allocation Tests ====================

    @Test
    @DisplayName("Allocation fails when player not found")
    void testAllocationFailsPlayerNotFound() {
        UUID unknownId = UUID.randomUUID();

        boolean result = manager.allocateAttribute(unknownId, AttributeType.FIRE);

        assertFalse(result, "Should fail for unknown player");
    }

    @Test
    @DisplayName("Allocation fails with no unallocated points")
    void testAllocationFailsNoPoints() {
        UUID playerId = UUID.randomUUID();
        repository.create(playerId, "TestPlayer", 0);

        boolean result = manager.allocateAttribute(playerId, AttributeType.FIRE);

        assertFalse(result, "Should fail with 0 unallocated points");

        Optional<PlayerData> data = repository.get(playerId);
        assertTrue(data.isPresent());
        assertEquals(0, data.get().getFire());
    }

    @Test
    @DisplayName("Allocation succeeds and updates attribute")
    void testAllocationSucceeds() {
        UUID playerId = UUID.randomUUID();
        repository.create(playerId, "TestPlayer", 5);

        boolean result = manager.allocateAttribute(playerId, AttributeType.FIRE);

        assertTrue(result, "Should succeed with available points");

        Optional<PlayerData> data = repository.get(playerId);
        assertTrue(data.isPresent());
        assertEquals(1, data.get().getFire());
        assertEquals(4, data.get().getUnallocatedPoints());
    }

    @Test
    @DisplayName("Allocation works for all attribute types")
    void testAllocationAllTypes() {
        UUID playerId = UUID.randomUUID();
        repository.create(playerId, "TestPlayer", 10);

        assertTrue(manager.allocateAttribute(playerId, AttributeType.FIRE));
        assertTrue(manager.allocateAttribute(playerId, AttributeType.WATER));
        assertTrue(manager.allocateAttribute(playerId, AttributeType.LIGHTNING));
        assertTrue(manager.allocateAttribute(playerId, AttributeType.EARTH));
        assertTrue(manager.allocateAttribute(playerId, AttributeType.WIND));
        assertTrue(manager.allocateAttribute(playerId, AttributeType.VOID));

        Optional<PlayerData> data = repository.get(playerId);
        assertTrue(data.isPresent());
        assertEquals(1, data.get().getFire());
        assertEquals(1, data.get().getWater());
        assertEquals(1, data.get().getLightning());
        assertEquals(1, data.get().getEarth());
        assertEquals(1, data.get().getWind());
        assertEquals(1, data.get().getVoidAttr());
        assertEquals(4, data.get().getUnallocatedPoints());
    }

    @Test
    @DisplayName("Allocation updates ComputedStats in cache")
    void testAllocationUpdatesComputedStats() {
        UUID playerId = UUID.randomUUID();
        repository.create(playerId, "TestPlayer", 50);

        // Allocate 50 points to EARTH (health element)
        for (int i = 0; i < 50; i++) {
            manager.allocateAttribute(playerId, AttributeType.EARTH);
        }

        Optional<PlayerData> data = repository.get(playerId);
        assertTrue(data.isPresent());

        ComputedStats stats = data.get().getComputedStats();
        // Health: 100 base × (1 + 25% from Earth) = 125
        assertEquals(125f, stats.getMaxHealth(), 0.01f);
        // Max Health %: 50 * 0.5 = 25%
        assertEquals(25f, stats.getMaxHealthPercent(), 0.01f);
    }

    // ==================== Recalculation Tests ====================

    @Test
    @DisplayName("recalculateStats returns null for missing player")
    void testRecalculateMissingPlayer() {
        UUID unknownId = UUID.randomUUID();

        ComputedStats result = manager.recalculateStats(unknownId);

        assertNull(result, "Should return null for unknown player");
    }

    @Test
    @DisplayName("recalculateStats computes correct values")
    void testRecalculateComputes() {
        UUID playerId = UUID.randomUUID();
        PlayerData initial = repository.create(playerId, "TestPlayer", 0)
            .withFire(20)
            .withWater(15)
            .withLightning(10)
            .withEarth(25);
        repository.save(initial);

        ComputedStats stats = manager.recalculateStats(playerId);

        assertNotNull(stats);
        // Health: 100 base × (1 + 12.5% from Earth) = 112.5
        assertEquals(112.5f, stats.getMaxHealth(), 0.01f);
        // Max Health %: 25 * 0.5 = 12.5%
        assertEquals(12.5f, stats.getMaxHealthPercent(), 0.01f);
    }

    @Test
    @DisplayName("recalculateStats updates cache")
    void testRecalculateUpdatesCache() {
        UUID playerId = UUID.randomUUID();
        PlayerData initial = repository.create(playerId, "TestPlayer", 0)
            .withEarth(30);
        repository.save(initial);

        manager.recalculateStats(playerId);

        Optional<PlayerData> data = repository.get(playerId);
        assertTrue(data.isPresent());
        ComputedStats stats = data.get().getComputedStats();
        // Health: 100 base × (1 + 15% from Earth) = 115
        assertEquals(115f, stats.getMaxHealth(), 0.01f);
        // Max Health %: 30 * 0.5 = 15%
        assertEquals(15f, stats.getMaxHealthPercent(), 0.01f);
    }

    // ==================== getStats Tests ====================

    @Test
    @DisplayName("getStats returns null for missing player")
    void testGetStatsMissingPlayer() {
        UUID unknownId = UUID.randomUUID();

        ComputedStats result = manager.getStats(unknownId);

        assertNull(result);
    }

    @Test
    @DisplayName("getStats calculates if not cached")
    void testGetStatsCalculatesIfNotCached() {
        UUID playerId = UUID.randomUUID();
        PlayerData initial = repository.create(playerId, "TestPlayer", 0)
            .withLightning(20);
        repository.save(initial);
        repository.clearCache();
        repository.get(playerId);

        ComputedStats stats = manager.getStats(playerId);

        assertNotNull(stats);
        // Movement Speed: 20 LIGHTNING * 0.15 = 3%
        assertEquals(3f, stats.getMovementSpeedPercent(), 0.01f);
    }

    // ==================== Helper Method Tests ====================

    @Test
    @DisplayName("calculateSpeedMultiplier returns correct value")
    void testCalculateSpeedMultiplier() {
        assertEquals(1.5f, manager.calculateSpeedMultiplier(50f), 0.01f);
        assertEquals(2.0f, manager.calculateSpeedMultiplier(100f), 0.01f);
        assertEquals(1.0f, manager.calculateSpeedMultiplier(0f), 0.01f);
    }

    // ==================== Getter Tests ====================

    @Test
    @DisplayName("Getters return correct instances")
    void testGetters() {
        assertNotNull(manager.getPlayerDataRepository());
        assertNotNull(manager.getCalculator());
        assertNotNull(manager.getConfigManager());
    }

    // ==================== Constants Tests ====================

    @Test
    @DisplayName("Constants are correctly defined")
    void testConstants() {
        assertEquals("rpg_attribute_bonus", AttributeManager.RPG_MODIFIER_ID);
        assertEquals(1.65f, AttributeManager.BASE_SPRINT_SPEED, 0.01f);
    }

    // ==================== Null Safety Tests ====================

    @Test
    @DisplayName("allocateAttribute throws on null playerId")
    void testAllocateNullPlayerId() {
        assertThrows(NullPointerException.class,
            () -> manager.allocateAttribute(null, AttributeType.FIRE));
    }

    @Test
    @DisplayName("allocateAttribute throws on null type")
    void testAllocateNullType() {
        assertThrows(NullPointerException.class,
            () -> manager.allocateAttribute(UUID.randomUUID(), null));
    }

    @Test
    @DisplayName("recalculateStats throws on null playerId")
    void testRecalculateNullPlayerId() {
        assertThrows(NullPointerException.class,
            () -> manager.recalculateStats(null));
    }
}
