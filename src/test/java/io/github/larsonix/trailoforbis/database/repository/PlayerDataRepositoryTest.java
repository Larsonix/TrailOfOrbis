package io.github.larsonix.trailoforbis.database.repository;

import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.config.RPGConfig;
import io.github.larsonix.trailoforbis.database.DataManager;
import io.github.larsonix.trailoforbis.database.models.PlayerData;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PlayerDataRepository.
 */
public class PlayerDataRepositoryTest {
    @TempDir
    Path tempDir;

    private DataManager dataManager;
    private PlayerDataRepository repository;

    @BeforeEach
    public void setUp() {
        RPGConfig config = new RPGConfig();
        dataManager = new DataManager(tempDir, config);
        assertTrue(dataManager.initialize(), "DataManager must initialize for tests");
        repository = new PlayerDataRepository(dataManager);
    }

    @AfterEach
    public void tearDown() {
        if (dataManager != null) {
            dataManager.shutdown();
        }
    }

    @Test
    @DisplayName("Create inserts new player with default values")
    public void testCreate() {
        UUID uuid = UUID.randomUUID();
        String username = "TestPlayer";

        PlayerData data = repository.create(uuid, username, 0);

        assertNotNull(data);
        assertEquals(uuid, data.getUuid());
        assertEquals(username, data.getUsername());
        assertEquals(0, data.getFire());
        assertEquals(0, data.getWater());
        assertEquals(0, data.getLightning());
        assertEquals(0, data.getEarth());
        assertEquals(0, data.getWind());
        assertEquals(0, data.getVoidAttr());
        assertEquals(0, data.getUnallocatedPoints());
        assertNotNull(data.getCreatedAt());
        assertNotNull(data.getLastSeen());
    }

    @Test
    @DisplayName("Create with starting points works correctly")
    public void testCreateWithStartingPoints() {
        UUID uuid = UUID.randomUUID();

        PlayerData data = repository.create(uuid, "TestPlayer", 10);

        assertEquals(10, data.getUnallocatedPoints());
    }

    @Test
    @DisplayName("Get returns created player from cache")
    public void testGetFromCache() {
        UUID uuid = UUID.randomUUID();
        repository.create(uuid, "CacheTest", 0);

        Optional<PlayerData> result = repository.get(uuid);

        assertTrue(result.isPresent());
        assertEquals(uuid, result.get().getUuid());
        assertEquals(1, repository.getCacheSize());
    }

    @Test
    @DisplayName("Get returns player from database after cache clear")
    public void testGetFromDatabase() {
        UUID uuid = UUID.randomUUID();
        repository.create(uuid, "DbTest", 0);
        repository.clearCache();

        assertEquals(0, repository.getCacheSize());

        Optional<PlayerData> result = repository.get(uuid);

        assertTrue(result.isPresent());
        assertEquals(uuid, result.get().getUuid());
        assertEquals("DbTest", result.get().getUsername());
        assertEquals(1, repository.getCacheSize());
    }

    @Test
    @DisplayName("Get returns empty for non-existent player")
    public void testGetNonExistent() {
        UUID uuid = UUID.randomUUID();

        Optional<PlayerData> result = repository.get(uuid);

        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("Save updates existing player")
    public void testSaveUpdatesPlayer() {
        UUID uuid = UUID.randomUUID();
        PlayerData original = repository.create(uuid, "Original", 0);

        PlayerData updated = original
            .withFire(10)
            .withWater(5)
            .withUnallocatedPoints(3);
        repository.save(updated);
        repository.clearCache();

        Optional<PlayerData> loaded = repository.get(uuid);
        assertTrue(loaded.isPresent());
        assertEquals(10, loaded.get().getFire());
        assertEquals(5, loaded.get().getWater());
        assertEquals(3, loaded.get().getUnallocatedPoints());
    }

    @Test
    @DisplayName("Delete removes player from database and cache")
    public void testDelete() {
        UUID uuid = UUID.randomUUID();
        repository.create(uuid, "DeleteMe", 0);

        repository.delete(uuid);

        assertFalse(repository.get(uuid).isPresent());
        assertEquals(0, repository.getCacheSize());
    }

    @Test
    @DisplayName("SaveAll batch saves all cached players")
    public void testSaveAll() {
        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();
        UUID uuid3 = UUID.randomUUID();

        repository.create(uuid1, "Player1", 0);
        repository.create(uuid2, "Player2", 0);
        repository.create(uuid3, "Player3", 0);

        repository.save(repository.get(uuid1).get().withFire(100));
        repository.save(repository.get(uuid2).get().withWater(50));

        repository.saveAll();
        repository.clearCache();

        assertEquals(100, repository.get(uuid1).get().getFire());
        assertEquals(50, repository.get(uuid2).get().getWater());
        assertTrue(repository.get(uuid3).isPresent());
    }

    @Test
    @DisplayName("ClearCache removes all cached entries")
    public void testClearCache() {
        repository.create(UUID.randomUUID(), "P1", 0);
        repository.create(UUID.randomUUID(), "P2", 0);
        assertEquals(2, repository.getCacheSize());

        repository.clearCache();

        assertEquals(0, repository.getCacheSize());
    }

    @Test
    @DisplayName("GetCachedUuids returns unmodifiable set")
    public void testGetCachedUuidsUnmodifiable() {
        UUID uuid = UUID.randomUUID();
        repository.create(uuid, "Test", 0);

        assertThrows(UnsupportedOperationException.class, () -> {
            repository.getCachedUuids().add(UUID.randomUUID());
        });
    }

    @Test
    @DisplayName("PlayerData withX methods create new instances")
    public void testPlayerDataImmutability() {
        UUID uuid = UUID.randomUUID();
        PlayerData original = repository.create(uuid, "Immutable", 0);

        PlayerData modified = original.withFire(99);

        assertNotSame(original, modified);
        assertEquals(0, original.getFire());
        assertEquals(99, modified.getFire());
    }

    @Test
    @DisplayName("PlayerData ComputedStats integration")
    public void testPlayerDataWithComputedStats() {
        UUID uuid = UUID.randomUUID();
        PlayerData player = repository.create(uuid, "StatsTest", 0);

        assertNull(player.getComputedStats(), "Uncalculated stats should be null");

        ComputedStats stats = ComputedStats.builder()
            .maxHealth(150f)
            .maxMana(100f)
            .criticalChance(5f)
            .armor(30f)
            .build();

        PlayerData playerWithStats = player.withComputedStats(stats);

        assertNotNull(playerWithStats.getComputedStats());
        assertEquals(150f, playerWithStats.getComputedStats().getMaxHealth());
        assertEquals(100f, playerWithStats.getComputedStats().getMaxMana());
        assertEquals(5f, playerWithStats.getComputedStats().getCriticalChance());
        assertEquals(30f, playerWithStats.getComputedStats().getArmor());

        assertNull(player.getComputedStats(), "Original player stats should remain null");

        repository.save(playerWithStats);
        repository.clearCache();

        Optional<PlayerData> loaded = repository.get(uuid);
        assertTrue(loaded.isPresent());
        assertNull(loaded.get().getComputedStats(), "Loaded player stats should be null");
    }
}
