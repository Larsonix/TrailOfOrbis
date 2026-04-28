package io.github.larsonix.trailoforbis.leveling.repository;

import io.github.larsonix.trailoforbis.database.DataManager;
import io.github.larsonix.trailoforbis.leveling.core.PlayerLevelData;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.*;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

/**
 * Tests for {@link LevelingRepository}.
 *
 * <p>Tests the cache-aside pattern implementation:
 * <ul>
 *   <li>Cache operations: get, getOrDefault, isCached, evict</li>
 *   <li>Load/create logic: cache hit vs DB load vs create new</li>
 *   <li>Save operations: async vs sync persistence</li>
 *   <li>Lifecycle: shutdown saves all and clears cache</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("LevelingRepository")
class LevelingRepositoryTest {

    @Mock
    private DataManager dataManager;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement selectStmt;

    @Mock
    private PreparedStatement insertStmt;

    @Mock
    private PreparedStatement updateStmt;

    @Mock
    private ResultSet resultSet;

    private LevelingRepository repository;

    private final UUID testPlayer = UUID.randomUUID();
    private final UUID player2 = UUID.randomUUID();

    @BeforeEach
    void setUp() throws SQLException {
        repository = new LevelingRepository(dataManager);
    }

    @AfterEach
    void tearDown() {
        if (repository != null) {
            repository.shutdown();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CACHE OPERATIONS - get()
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("get()")
    class GetTests {

        @Test
        @DisplayName("get returns null when player not in cache")
        void get_returnsNull_whenNotInCache() {
            PlayerLevelData result = repository.get(testPlayer);

            assertNull(result);
        }

        @Test
        @DisplayName("get returns cached data after loadOrCreate")
        void get_returnsCachedData_afterLoadOrCreate() throws SQLException {
            // Setup: database returns no data, so loadOrCreate creates new
            setupNoDbData();

            repository.loadOrCreate(testPlayer);

            PlayerLevelData result = repository.get(testPlayer);

            assertNotNull(result);
            assertEquals(testPlayer, result.uuid());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CACHE OPERATIONS - getOrDefault()
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getOrDefault()")
    class GetOrDefaultTests {

        @Test
        @DisplayName("getOrDefault returns new PlayerLevelData when not in cache")
        void getOrDefault_returnsNewData_whenNotInCache() {
            PlayerLevelData result = repository.getOrDefault(testPlayer);

            assertNotNull(result);
            assertEquals(testPlayer, result.uuid());
            assertEquals(0, result.xp());
        }

        @Test
        @DisplayName("getOrDefault returns cached data when present")
        void getOrDefault_returnsCachedData_whenPresent() throws SQLException {
            // Setup: load existing data with 1000 XP
            setupDbDataWithXp(testPlayer, 1000);
            repository.loadOrCreate(testPlayer);

            PlayerLevelData result = repository.getOrDefault(testPlayer);

            assertEquals(1000, result.xp());
        }

        @Test
        @DisplayName("getOrDefault does not cache the default data")
        void getOrDefault_doesNotCacheDefault() {
            repository.getOrDefault(testPlayer);

            // The default is returned but not cached
            assertFalse(repository.isCached(testPlayer));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CACHE OPERATIONS - isCached()
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("isCached()")
    class IsCachedTests {

        @Test
        @DisplayName("isCached returns false for unknown player")
        void isCached_returnsFalse_forUnknownPlayer() {
            assertFalse(repository.isCached(testPlayer));
        }

        @Test
        @DisplayName("isCached returns true after loadOrCreate")
        void isCached_returnsTrue_afterLoadOrCreate() throws SQLException {
            setupNoDbData();

            repository.loadOrCreate(testPlayer);

            assertTrue(repository.isCached(testPlayer));
        }

        @Test
        @DisplayName("isCached returns false after evict")
        void isCached_returnsFalse_afterEvict() throws SQLException {
            setupNoDbData();
            repository.loadOrCreate(testPlayer);

            repository.evict(testPlayer);

            assertFalse(repository.isCached(testPlayer));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CACHE OPERATIONS - evict()
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("evict()")
    class EvictTests {

        @Test
        @DisplayName("evict returns null when player not in cache")
        void evict_returnsNull_whenNotInCache() {
            PlayerLevelData result = repository.evict(testPlayer);

            assertNull(result);
        }

        @Test
        @DisplayName("evict returns and removes cached data")
        void evict_returnsAndRemovesCachedData() throws SQLException {
            setupNoDbData();
            repository.loadOrCreate(testPlayer);

            PlayerLevelData result = repository.evict(testPlayer);

            assertNotNull(result);
            assertEquals(testPlayer, result.uuid());
            assertFalse(repository.isCached(testPlayer));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // DATABASE OPERATIONS - loadOrCreate()
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("loadOrCreate()")
    class LoadOrCreateTests {

        @Test
        @DisplayName("loadOrCreate returns cached data without DB call")
        void loadOrCreate_returnsCached_withoutDbCall() throws SQLException {
            setupNoDbData();
            PlayerLevelData firstLoad = repository.loadOrCreate(testPlayer);

            // Wait a bit for async insert to complete
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {}

            // Reset mock to verify no further DB calls
            reset(dataManager);

            PlayerLevelData secondLoad = repository.loadOrCreate(testPlayer);

            assertSame(firstLoad, secondLoad);
            verifyNoInteractions(dataManager);
        }

        @Test
        @DisplayName("loadOrCreate loads from database when not cached")
        void loadOrCreate_loadsFromDb_whenNotCached() throws SQLException {
            setupDbDataWithXp(testPlayer, 500);

            PlayerLevelData result = repository.loadOrCreate(testPlayer);

            assertEquals(500, result.xp());
            verify(dataManager).getConnection();
        }

        @Test
        @DisplayName("loadOrCreate creates new data when not in DB")
        void loadOrCreate_createsNew_whenNotInDb() throws SQLException {
            setupNoDbData();

            PlayerLevelData result = repository.loadOrCreate(testPlayer);

            assertEquals(0, result.xp());
            assertNotNull(result.createdAt());
        }

        @Test
        @DisplayName("loadOrCreate caches loaded data")
        void loadOrCreate_cachesLoadedData() throws SQLException {
            setupDbDataWithXp(testPlayer, 750);

            repository.loadOrCreate(testPlayer);

            assertTrue(repository.isCached(testPlayer));
            assertEquals(750, repository.get(testPlayer).xp());
        }

        @Test
        @DisplayName("loadOrCreate caches newly created data")
        void loadOrCreate_cachesNewData() throws SQLException {
            setupNoDbData();

            repository.loadOrCreate(testPlayer);

            assertTrue(repository.isCached(testPlayer));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // DATABASE OPERATIONS - save()
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("save()")
    class SaveTests {

        @Test
        @DisplayName("save updates cache immediately")
        void save_updatesCacheImmediately() {
            PlayerLevelData data = PlayerLevelData.createWithXp(testPlayer, 1000);

            repository.save(data);

            PlayerLevelData cached = repository.get(testPlayer);
            assertNotNull(cached);
            assertEquals(1000, cached.xp());
        }

        @Test
        @DisplayName("save schedules async DB update")
        void save_schedulesAsyncDbUpdate() throws Exception {
            // Setup mock for async update
            setupUpdateMock();

            PlayerLevelData data = PlayerLevelData.createWithXp(testPlayer, 1000);
            repository.save(data);

            // Give async executor time to run
            Thread.sleep(100);

            verify(dataManager, timeout(500)).getConnection();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // DATABASE OPERATIONS - saveSync()
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("saveSync()")
    class SaveSyncTests {

        @Test
        @DisplayName("saveSync updates cache immediately")
        void saveSync_updatesCacheImmediately() throws SQLException {
            setupUpdateMock();

            PlayerLevelData data = PlayerLevelData.createWithXp(testPlayer, 2000);

            repository.saveSync(data);

            PlayerLevelData cached = repository.get(testPlayer);
            assertEquals(2000, cached.xp());
        }

        @Test
        @DisplayName("saveSync returns true on successful update")
        void saveSync_returnsTrue_onSuccess() throws SQLException {
            setupUpdateMock();
            when(updateStmt.executeUpdate()).thenReturn(1);

            PlayerLevelData data = PlayerLevelData.createWithXp(testPlayer, 1000);

            boolean result = repository.saveSync(data);

            assertTrue(result);
        }

        @Test
        @DisplayName("saveSync falls back to insert when update affects 0 rows")
        void saveSync_fallsBackToInsert_whenUpdateAffectsZeroRows() throws SQLException {
            setupUpdateWithInsertFallbackMock();

            PlayerLevelData data = PlayerLevelData.createWithXp(testPlayer, 1000);

            boolean result = repository.saveSync(data);

            assertTrue(result);
            verify(insertStmt).executeUpdate();
        }

        @Test
        @DisplayName("saveSync returns false on SQLException")
        void saveSync_returnsFalse_onException() throws SQLException {
            when(dataManager.getConnection()).thenThrow(new SQLException("Test error"));

            PlayerLevelData data = PlayerLevelData.createWithXp(testPlayer, 1000);

            boolean result = repository.saveSync(data);

            assertFalse(result);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // LIFECYCLE - shutdown()
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("shutdown()")
    class ShutdownTests {

        @Test
        @DisplayName("shutdown clears cache")
        void shutdown_clearsCache() throws SQLException {
            // Use unified setup to avoid mock reconfiguration issues
            setupAllDbOperations();

            repository.loadOrCreate(testPlayer);
            repository.loadOrCreate(player2);
            assertEquals(2, repository.getCacheSize());

            // Wait for any async operations
            waitForAsync();

            repository.shutdown();

            assertEquals(0, repository.getCacheSize());
        }

        @Test
        @DisplayName("shutdown is idempotent")
        void shutdown_idempotent() throws SQLException {
            setupAllDbOperations();
            repository.loadOrCreate(testPlayer);
            waitForAsync();

            // First shutdown
            repository.shutdown();
            assertEquals(0, repository.getCacheSize());

            // Second shutdown should not throw
            repository.shutdown();
            assertEquals(0, repository.getCacheSize());
        }

        @Test
        @DisplayName("getCacheSize returns correct count")
        void getCacheSize_returnsCorrectCount() throws SQLException {
            setupNoDbData();

            assertEquals(0, repository.getCacheSize());

            repository.loadOrCreate(testPlayer);
            assertEquals(1, repository.getCacheSize());

            repository.loadOrCreate(player2);
            assertEquals(2, repository.getCacheSize());

            repository.evict(testPlayer);
            assertEquals(1, repository.getCacheSize());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // LIFECYCLE - saveAll()
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("saveAll()")
    class SaveAllTests {

        @Test
        @DisplayName("saveAll saves all cached data without clearing")
        void saveAll_savesAll_withoutClearing() throws SQLException {
            // Use unified setup to avoid mock reconfiguration issues
            setupAllDbOperations();

            repository.loadOrCreate(testPlayer);
            repository.loadOrCreate(player2);

            // Wait for any async operations
            waitForAsync();

            repository.saveAll();

            // Cache should still contain data (saveAll doesn't clear)
            assertTrue(repository.isCached(testPlayer));
            assertTrue(repository.isCached(player2));
            verify(dataManager, atLeast(2)).getConnection();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════

    private void setupNoDbData() throws SQLException {
        when(dataManager.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("SELECT"))).thenReturn(selectStmt);
        when(connection.prepareStatement(contains("INSERT"))).thenReturn(insertStmt);
        when(selectStmt.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);
    }

    private void setupDbDataWithXp(UUID playerId, long xp) throws SQLException {
        when(dataManager.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("SELECT"))).thenReturn(selectStmt);
        when(selectStmt.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getLong("xp")).thenReturn(xp);
        when(resultSet.getTimestamp("created_at")).thenReturn(Timestamp.from(Instant.now()));
        when(resultSet.getTimestamp("last_updated")).thenReturn(Timestamp.from(Instant.now()));
    }

    private void setupUpdateMock() throws SQLException {
        when(dataManager.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("UPDATE"))).thenReturn(updateStmt);
        when(connection.prepareStatement(contains("INSERT"))).thenReturn(insertStmt);
    }

    /**
     * Sets up all DB operations (SELECT, INSERT, UPDATE) at once.
     * Use this when the test needs to perform multiple operations
     * without reconfiguring mocks mid-test (avoids race conditions).
     *
     * <p>Uses anyString() for prepareStatement to avoid matcher conflicts
     * when multiple statement types are needed in a single test.
     */
    private void setupAllDbOperations() throws SQLException {
        when(dataManager.getConnection()).thenReturn(connection);

        // Use Answer to return different statement based on SQL content
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.toUpperCase().contains("SELECT")) {
                return selectStmt;
            } else if (sql.toUpperCase().contains("INSERT")) {
                return insertStmt;
            } else if (sql.toUpperCase().contains("UPDATE")) {
                return updateStmt;
            }
            return selectStmt;  // Fallback
        });

        when(selectStmt.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);  // No existing data
        when(insertStmt.executeUpdate()).thenReturn(1);
        when(updateStmt.executeUpdate()).thenReturn(1);
    }

    private void setupUpdateWithInsertFallbackMock() throws SQLException {
        when(dataManager.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("UPDATE"))).thenReturn(updateStmt);
        when(connection.prepareStatement(contains("INSERT"))).thenReturn(insertStmt);
        when(updateStmt.executeUpdate()).thenReturn(0); // No rows updated
        when(insertStmt.executeUpdate()).thenReturn(1);
    }

    /** Helper to match SQL containing a specific keyword */
    private static String contains(String keyword) {
        return argThat(sql -> sql != null && sql.toUpperCase().contains(keyword.toUpperCase()));
    }

    /** Wait for async operations (INSERT from loadOrCreate) to complete */
    private void waitForAsync() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
