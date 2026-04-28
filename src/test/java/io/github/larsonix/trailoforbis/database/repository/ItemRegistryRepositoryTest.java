package io.github.larsonix.trailoforbis.database.repository;

import io.github.larsonix.trailoforbis.database.DataManager;
import io.github.larsonix.trailoforbis.database.DatabaseType;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ItemRegistryRepository.
 *
 * <p>Tests data persistence for RPG gear items:
 * <ul>
 *   <li>loadAll() - startup item restoration</li>
 *   <li>register() / registerSync() - item registration</li>
 *   <li>registerBatch() - batch operations</li>
 *   <li>getBaseItemId() - lookup</li>
 *   <li>cleanupOldEntries() - stale item cleanup</li>
 *   <li>migrateSchema() - column migration</li>
 * </ul>
 */
public class ItemRegistryRepositoryTest {

    @Mock
    private DataManager dataManager;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement preparedStatement;

    @Mock
    private Statement statement;

    @Mock
    private ResultSet resultSet;

    private ItemRegistryRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        // Default mock setup
        when(dataManager.getConnection()).thenReturn(connection);
        when(dataManager.getDatabaseType()).thenReturn(DatabaseType.H2);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(connection.createStatement()).thenReturn(statement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);

        repository = new ItemRegistryRepository(dataManager);
    }

    // ==================== Constructor Tests ====================

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("Null data manager throws NPE")
        void constructor_nullDataManager_throws() {
            assertThrows(NullPointerException.class, () ->
                new ItemRegistryRepository(null)
            );
        }
    }

    // ==================== loadAll Tests ====================

    @Nested
    @DisplayName("loadAll()")
    class LoadAllTests {

        @Test
        @DisplayName("Returns empty map when no entries")
        void loadAll_noEntries_returnsEmptyMap() throws Exception {
            when(resultSet.next()).thenReturn(false);

            Map<String, ItemRegistryRepository.ItemRegistryEntry> result = repository.loadAll();

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Returns entries from database")
        void loadAll_withEntries_returnsMap() throws Exception {
            when(resultSet.next()).thenReturn(true, true, false);
            when(resultSet.getString("custom_id"))
                .thenReturn("rpg_gear_001", "rpg_gear_002");
            when(resultSet.getString("base_item_id"))
                .thenReturn("Weapon_Sword_Iron", "Armor_Chestplate_Copper");
            when(resultSet.getString("secondary_interaction_id"))
                .thenReturn(null, "RPG_Stone_Secondary");

            Map<String, ItemRegistryRepository.ItemRegistryEntry> result = repository.loadAll();

            assertEquals(2, result.size());
            assertEquals("Weapon_Sword_Iron", result.get("rpg_gear_001").baseItemId());
            assertNull(result.get("rpg_gear_001").secondaryInteractionId());
            assertEquals("Armor_Chestplate_Copper", result.get("rpg_gear_002").baseItemId());
            assertEquals("RPG_Stone_Secondary", result.get("rpg_gear_002").secondaryInteractionId());
        }

        @Test
        @DisplayName("Handles SQL exception gracefully")
        void loadAll_sqlException_returnsEmptyMap() throws Exception {
            when(dataManager.getConnection()).thenThrow(new SQLException("Connection failed"));

            Map<String, ItemRegistryRepository.ItemRegistryEntry> result = repository.loadAll();

            assertTrue(result.isEmpty());
        }
    }

    // ==================== getBaseItemId Tests ====================

    @Nested
    @DisplayName("getBaseItemId()")
    class GetBaseItemIdTests {

        @Test
        @DisplayName("Null custom ID throws NPE")
        void getBaseItemId_nullId_throws() {
            assertThrows(NullPointerException.class, () ->
                repository.getBaseItemId(null)
            );
        }

        @Test
        @DisplayName("Returns empty for non-existent item")
        void getBaseItemId_notFound_returnsEmpty() throws Exception {
            when(resultSet.next()).thenReturn(false);

            Optional<String> result = repository.getBaseItemId("non_existent");

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Returns base item ID when found")
        void getBaseItemId_found_returnsValue() throws Exception {
            when(resultSet.next()).thenReturn(true);
            when(resultSet.getString("base_item_id")).thenReturn("Weapon_Axe_Gold");

            Optional<String> result = repository.getBaseItemId("rpg_gear_123");

            assertTrue(result.isPresent());
            assertEquals("Weapon_Axe_Gold", result.get());
        }

        @Test
        @DisplayName("Handles SQL exception gracefully")
        void getBaseItemId_sqlException_returnsEmpty() throws Exception {
            when(dataManager.getConnection()).thenThrow(new SQLException("Query failed"));

            Optional<String> result = repository.getBaseItemId("rpg_gear_123");

            assertTrue(result.isEmpty());
        }
    }

    // ==================== registerSync Tests ====================

    @Nested
    @DisplayName("registerSync()")
    class RegisterSyncTests {

        @Test
        @DisplayName("Null custom ID throws NPE")
        void registerSync_nullCustomId_throws() {
            assertThrows(NullPointerException.class, () ->
                repository.registerSync(null, "base_id")
            );
        }

        @Test
        @DisplayName("Null base item ID throws NPE")
        void registerSync_nullBaseId_throws() {
            assertThrows(NullPointerException.class, () ->
                repository.registerSync("custom_id", null)
            );
        }

        @Test
        @DisplayName("Executes upsert statement")
        void registerSync_validInput_executesUpsert() throws Exception {
            when(preparedStatement.executeUpdate()).thenReturn(1);

            repository.registerSync("rpg_gear_001", "Weapon_Sword");

            verify(preparedStatement).setString(1, "rpg_gear_001");
            verify(preparedStatement).setString(2, "Weapon_Sword");
            verify(preparedStatement).executeUpdate();
        }

        @Test
        @DisplayName("Handles SQL exception gracefully")
        void registerSync_sqlException_doesNotThrow() throws Exception {
            when(preparedStatement.executeUpdate()).thenThrow(new SQLException("Insert failed"));

            assertDoesNotThrow(() ->
                repository.registerSync("rpg_gear_001", "Weapon_Sword")
            );
        }

        @Test
        @DisplayName("Null secondary interaction sets null")
        void registerSync_nullSecondary_setsNull() throws Exception {
            repository.registerSync("rpg_gear_001", "Weapon_Sword", null);

            verify(preparedStatement).setNull(3, Types.VARCHAR);
        }

        @Test
        @DisplayName("Non-null secondary interaction sets value")
        void registerSync_withSecondary_setsValue() throws Exception {
            repository.registerSync("rpg_gear_001", "Weapon_Sword", "RPG_Stone");

            verify(preparedStatement).setString(3, "RPG_Stone");
        }
    }

    // ==================== register (async) Tests ====================

    @Nested
    @DisplayName("register() async")
    class RegisterAsyncTests {

        @Test
        @DisplayName("Null custom ID throws NPE")
        void register_nullCustomId_throws() {
            assertThrows(NullPointerException.class, () ->
                repository.register(null, "base_id")
            );
        }

        @Test
        @DisplayName("Null base item ID throws NPE")
        void register_nullBaseId_throws() {
            assertThrows(NullPointerException.class, () ->
                repository.register("custom_id", null)
            );
        }

        @Test
        @DisplayName("Returns completed future")
        void register_validInput_returnsFuture() throws Exception {
            when(preparedStatement.executeUpdate()).thenReturn(1);

            CompletableFuture<Void> future = repository.register("rpg_gear_001", "Weapon_Sword");

            assertNotNull(future);
            assertDoesNotThrow(() -> future.get(5, TimeUnit.SECONDS));
        }
    }

    // ==================== registerBatch Tests ====================

    @Nested
    @DisplayName("registerBatch()")
    class RegisterBatchTests {

        @Test
        @DisplayName("Null items throws NPE")
        void registerBatch_nullItems_throws() {
            assertThrows(NullPointerException.class, () ->
                repository.registerBatch(null)
            );
        }

        @Test
        @DisplayName("Empty map returns 0")
        void registerBatch_emptyMap_returnsZero() {
            int result = repository.registerBatch(new HashMap<>());
            assertEquals(0, result);
        }

        @Test
        @DisplayName("Registers all items")
        void registerBatch_multipleItems_registersAll() throws Exception {
            Map<String, ItemRegistryRepository.ItemRegistryEntry> items = new HashMap<>();
            items.put("gear_001", new ItemRegistryRepository.ItemRegistryEntry("Sword", null));
            items.put("gear_002", new ItemRegistryRepository.ItemRegistryEntry("Axe", "Secondary"));
            items.put("gear_003", new ItemRegistryRepository.ItemRegistryEntry("Shield", null));

            when(preparedStatement.executeBatch()).thenReturn(new int[]{1, 1, 1});

            int result = repository.registerBatch(items);

            assertEquals(3, result);
            verify(preparedStatement, times(3)).addBatch();
            verify(connection).setAutoCommit(false);
            verify(connection).commit();
        }

        @Test
        @DisplayName("Handles SQL exception gracefully")
        void registerBatch_sqlException_returnsZero() throws Exception {
            Map<String, ItemRegistryRepository.ItemRegistryEntry> items = new HashMap<>();
            items.put("gear_001", new ItemRegistryRepository.ItemRegistryEntry("Sword", null));

            when(preparedStatement.executeBatch()).thenThrow(new SQLException("Batch failed"));

            int result = repository.registerBatch(items);

            assertEquals(0, result);
        }
    }

    // ==================== getCount Tests ====================

    @Nested
    @DisplayName("getCount()")
    class GetCountTests {

        @Test
        @DisplayName("Returns count from database")
        void getCount_returnsCount() throws Exception {
            when(resultSet.next()).thenReturn(true);
            when(resultSet.getInt(1)).thenReturn(42);

            int result = repository.getCount();

            assertEquals(42, result);
        }

        @Test
        @DisplayName("Returns 0 on empty table")
        void getCount_emptyTable_returnsZero() throws Exception {
            when(resultSet.next()).thenReturn(true);
            when(resultSet.getInt(1)).thenReturn(0);

            int result = repository.getCount();

            assertEquals(0, result);
        }

        @Test
        @DisplayName("Returns 0 on SQL exception")
        void getCount_sqlException_returnsZero() throws Exception {
            when(dataManager.getConnection()).thenThrow(new SQLException("Count failed"));

            int result = repository.getCount();

            assertEquals(0, result);
        }
    }

    // ==================== cleanupOldEntries Tests ====================

    @Nested
    @DisplayName("cleanupOldEntries()")
    class CleanupTests {

        @Test
        @DisplayName("Zero days returns 0")
        void cleanupOldEntries_zeroDays_returnsZero() {
            int result = repository.cleanupOldEntries(0);
            assertEquals(0, result);
        }

        @Test
        @DisplayName("Negative days returns 0")
        void cleanupOldEntries_negativeDays_returnsZero() {
            int result = repository.cleanupOldEntries(-5);
            assertEquals(0, result);
        }

        @Test
        @DisplayName("Deletes old entries")
        void cleanupOldEntries_validDays_deletesEntries() throws Exception {
            when(preparedStatement.executeUpdate()).thenReturn(10);

            int result = repository.cleanupOldEntries(30);

            assertEquals(10, result);
            verify(preparedStatement).setTimestamp(eq(1), any(Timestamp.class));
            verify(preparedStatement).executeUpdate();
        }

        @Test
        @DisplayName("Handles SQL exception gracefully")
        void cleanupOldEntries_sqlException_returnsZero() throws Exception {
            when(dataManager.getConnection()).thenThrow(new SQLException("Delete failed"));

            int result = repository.cleanupOldEntries(30);

            assertEquals(0, result);
        }
    }

    // ==================== remove Tests ====================

    @Nested
    @DisplayName("remove()")
    class RemoveTests {

        @Test
        @DisplayName("Null custom ID throws NPE")
        void remove_nullId_throws() {
            assertThrows(NullPointerException.class, () ->
                repository.remove(null)
            );
        }

        @Test
        @DisplayName("Returns true when item removed")
        void remove_itemExists_returnsTrue() throws Exception {
            when(preparedStatement.executeUpdate()).thenReturn(1);

            boolean result = repository.remove("rpg_gear_001");

            assertTrue(result);
        }

        @Test
        @DisplayName("Returns false when item not found")
        void remove_itemNotFound_returnsFalse() throws Exception {
            when(preparedStatement.executeUpdate()).thenReturn(0);

            boolean result = repository.remove("non_existent");

            assertFalse(result);
        }

        @Test
        @DisplayName("Handles SQL exception gracefully")
        void remove_sqlException_returnsFalse() throws Exception {
            when(dataManager.getConnection()).thenThrow(new SQLException("Delete failed"));

            boolean result = repository.remove("rpg_gear_001");

            assertFalse(result);
        }
    }

    // ==================== updateLastSeen Tests ====================

    @Nested
    @DisplayName("updateLastSeen()")
    class UpdateLastSeenTests {

        @Test
        @DisplayName("Null collection throws NPE")
        void updateLastSeen_nullCollection_throws() {
            assertThrows(NullPointerException.class, () ->
                repository.updateLastSeen(null)
            );
        }

        @Test
        @DisplayName("Empty collection returns 0")
        void updateLastSeen_emptyCollection_returnsZero() {
            int result = repository.updateLastSeen(Collections.emptyList());
            assertEquals(0, result);
        }

        @Test
        @DisplayName("Updates all items")
        void updateLastSeen_multipleItems_updatesAll() throws Exception {
            Collection<String> customIds = List.of("gear_001", "gear_002", "gear_003");
            when(preparedStatement.executeBatch()).thenReturn(new int[]{1, 1, 1});

            int result = repository.updateLastSeen(customIds);

            assertEquals(3, result);
            verify(preparedStatement, times(3)).addBatch();
        }

        @Test
        @DisplayName("Handles SQL exception gracefully")
        void updateLastSeen_sqlException_returnsZero() throws Exception {
            when(dataManager.getConnection()).thenThrow(new SQLException("Update failed"));

            int result = repository.updateLastSeen(List.of("gear_001"));

            assertEquals(0, result);
        }
    }

    // ==================== migrateSchema Tests ====================

    @Nested
    @DisplayName("migrateSchema()")
    class MigrateSchemaTests {

        @Test
        @DisplayName("Executes migration for H2")
        void migrateSchema_h2_executesMigration() throws Exception {
            when(dataManager.getDatabaseType()).thenReturn(DatabaseType.H2);

            repository.migrateSchema();

            verify(statement).execute(contains("IF NOT EXISTS"));
        }

        @Test
        @DisplayName("Executes migration for MySQL")
        void migrateSchema_mysql_executesMigration() throws Exception {
            when(dataManager.getDatabaseType()).thenReturn(DatabaseType.MYSQL);

            repository.migrateSchema();

            verify(statement).execute(anyString());
        }

        @Test
        @DisplayName("Executes migration for PostgreSQL")
        void migrateSchema_postgresql_executesMigration() throws Exception {
            when(dataManager.getDatabaseType()).thenReturn(DatabaseType.POSTGRESQL);

            repository.migrateSchema();

            verify(statement).execute(contains("IF NOT EXISTS"));
        }

        @Test
        @DisplayName("Ignores duplicate column error")
        void migrateSchema_duplicateColumn_noException() throws Exception {
            when(statement.execute(anyString()))
                .thenThrow(new SQLException("duplicate column"));

            assertDoesNotThrow(() -> repository.migrateSchema());
        }

        @Test
        @DisplayName("Ignores already exists error")
        void migrateSchema_alreadyExists_noException() throws Exception {
            when(statement.execute(anyString()))
                .thenThrow(new SQLException("already exists"));

            assertDoesNotThrow(() -> repository.migrateSchema());
        }
    }

    // ==================== ItemRegistryEntry Tests ====================

    @Nested
    @DisplayName("ItemRegistryEntry Record")
    class ItemRegistryEntryTests {

        @Test
        @DisplayName("Null base item ID throws NPE")
        void entry_nullBaseId_throws() {
            assertThrows(NullPointerException.class, () ->
                new ItemRegistryRepository.ItemRegistryEntry(null, null)
            );
        }

        @Test
        @DisplayName("Null secondary is allowed")
        void entry_nullSecondary_allowed() {
            var entry = new ItemRegistryRepository.ItemRegistryEntry("Base", null);
            assertEquals("Base", entry.baseItemId());
            assertNull(entry.secondaryInteractionId());
        }

        @Test
        @DisplayName("All fields preserved")
        void entry_allFields_preserved() {
            var entry = new ItemRegistryRepository.ItemRegistryEntry("Weapon_Sword", "RPG_Stone");
            assertEquals("Weapon_Sword", entry.baseItemId());
            assertEquals("RPG_Stone", entry.secondaryInteractionId());
        }
    }

    // ==================== Database Type-Specific SQL Tests ====================

    @Nested
    @DisplayName("Database-Specific SQL")
    class DatabaseSpecificTests {

        @Test
        @DisplayName("H2 uses MERGE syntax")
        void h2_usesMergeSyntax() throws Exception {
            when(dataManager.getDatabaseType()).thenReturn(DatabaseType.H2);

            repository.registerSync("id", "base");

            verify(connection).prepareStatement(contains("MERGE INTO"));
        }

        @Test
        @DisplayName("MySQL uses ON DUPLICATE KEY UPDATE")
        void mysql_usesOnDuplicateKey() throws Exception {
            when(dataManager.getDatabaseType()).thenReturn(DatabaseType.MYSQL);

            repository.registerSync("id", "base");

            verify(connection).prepareStatement(contains("ON DUPLICATE KEY UPDATE"));
        }

        @Test
        @DisplayName("PostgreSQL uses ON CONFLICT")
        void postgresql_usesOnConflict() throws Exception {
            when(dataManager.getDatabaseType()).thenReturn(DatabaseType.POSTGRESQL);

            repository.registerSync("id", "base");

            verify(connection).prepareStatement(contains("ON CONFLICT"));
        }
    }
}
