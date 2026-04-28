package io.github.larsonix.trailoforbis.database;

import io.github.larsonix.trailoforbis.config.RPGConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DataManager with H2 database.
 */
public class DataManagerTest {
    @TempDir
    Path tempDir;

    private DataManager dataManager;
    private RPGConfig config;

    @BeforeEach
    public void setUp() {
        config = new RPGConfig();
        // Default config uses H2
        dataManager = new DataManager(tempDir, config);
    }

    @AfterEach
    public void tearDown() {
        if (dataManager != null) {
            dataManager.shutdown();
        }
    }

    @Test
    @DisplayName("H2 database initializes successfully")
    public void testH2Initialization() {
        assertTrue(dataManager.initialize(), "DataManager should initialize with H2");
        assertTrue(dataManager.isInitialized(), "DataManager should be initialized");
        assertEquals(DatabaseType.H2, dataManager.getDatabaseType());
    }

    @Test
    @DisplayName("Connection pool provides valid connections")
    public void testConnectionPooling() throws SQLException {
        assertTrue(dataManager.initialize());

        // Get multiple connections (should come from pool)
        try (Connection conn1 = dataManager.getConnection();
             Connection conn2 = dataManager.getConnection()) {

            assertNotNull(conn1);
            assertNotNull(conn2);
            assertFalse(conn1.isClosed());
            assertFalse(conn2.isClosed());
        }
    }

    @Test
    @DisplayName("Schema creates rpg_players table")
    public void testSchemaExecution() throws SQLException {
        assertTrue(dataManager.initialize());

        try (Connection conn = dataManager.getConnection();
             ResultSet rs = conn.getMetaData().getTables(null, null, "RPG_PLAYERS", null)) {

            assertTrue(rs.next(), "rpg_players table should exist");
        }
    }

    @Test
    @DisplayName("Shutdown closes connection pool gracefully")
    public void testGracefulShutdown() {
        assertTrue(dataManager.initialize());
        assertTrue(dataManager.isInitialized());

        dataManager.shutdown();

        assertFalse(dataManager.isInitialized());
        assertThrows(IllegalStateException.class, dataManager::getConnection,
            "getConnection should throw after shutdown");
    }

    @Test
    @DisplayName("Invalid database type returns false on initialize")
    public void testInvalidDatabaseType() {
        RPGConfig badConfig = new RPGConfig();
        badConfig.getDatabase().setType("INVALID_DB");

        DataManager badManager = new DataManager(tempDir, badConfig);
        try {
            assertFalse(badManager.initialize(), "Should fail with invalid database type");
        } finally {
            badManager.shutdown();
        }
    }

    @Test
    @DisplayName("Multiple initializations are safe")
    public void testDoubleInitialization() {
        assertTrue(dataManager.initialize());

        // Second initialization should be handled gracefully
        // Current implementation will create a new pool (not ideal but not crashing)
        // The important thing is it doesn't throw
        dataManager.initialize();

        assertTrue(dataManager.isInitialized());
    }

    @Test
    @DisplayName("Connection returns to pool after close")
    public void testConnectionReturn() throws SQLException {
        assertTrue(dataManager.initialize());

        // Get connection, use it, close it
        Connection conn = dataManager.getConnection();
        assertFalse(conn.isClosed());
        conn.close();
        assertTrue(conn.isClosed()); // Proxy shows as closed

        // Should be able to get another connection
        try (Connection conn2 = dataManager.getConnection()) {
            assertNotNull(conn2);
            assertFalse(conn2.isClosed());
        }
    }
}
