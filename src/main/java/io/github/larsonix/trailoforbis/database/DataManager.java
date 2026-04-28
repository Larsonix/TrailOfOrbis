package io.github.larsonix.trailoforbis.database;

import com.hypixel.hytale.logger.HytaleLogger;
import java.util.logging.Level;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.larsonix.trailoforbis.config.RPGConfig;
import io.github.larsonix.trailoforbis.config.RPGConfig.DatabaseConfig;

import javax.annotation.Nonnull;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;

/**
 * Manages database connections using HikariCP connection pool.
 *
 * <p>Supports H2 (embedded), MySQL, and PostgreSQL backends.
 * Configuration is read from {@link RPGConfig.DatabaseConfig}.
 *
 * <p>Usage:
 * <pre>
 * DataManager dm = new DataManager(dataFolder, config);
 * if (dm.initialize()) {
 *     try (Connection conn = dm.getConnection()) {
 *         // use connection
 *     }
 * }
 * // On shutdown:
 * dm.shutdown();
 * </pre>
 */
public class DataManager {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String SCHEMA_RESOURCE = "db/schema.sql";

    private final Path dataFolder;
    private final RPGConfig config;
    private HikariDataSource dataSource;
    private DatabaseType databaseType;

    /** @param dataFolder plugin data folder (for H2 database file) */
    public DataManager(@Nonnull Path dataFolder, @Nonnull RPGConfig config) {
        this.dataFolder = dataFolder;
        this.config = config;
    }

    /** Initializes the connection pool and runs schema. */
    public boolean initialize() {
        DatabaseConfig dbConfig = config.getDatabase();

        try {
            // Parse database type
            databaseType = DatabaseType.fromString(dbConfig.getType());

            // Configure HikariCP
            HikariConfig hikariConfig = createHikariConfig(dbConfig);

            // Create connection pool
            dataSource = new HikariDataSource(hikariConfig);

            LOGGER.at(Level.INFO).log("Database pool initialized: %s", databaseType);

            // Run schema
            if (!executeSchema()) {
                shutdown();
                return false;
            }

            // Run migrations (add missing columns for updates)
            performMigrations();

            return true;

        } catch (IllegalArgumentException e) {
            LOGGER.at(Level.SEVERE).log("%s", e.getMessage());
            return false;
        } catch (Exception e) {
            LOGGER.at(Level.SEVERE).withCause(e).log("Database initialization failed: %s", e.getMessage());
            return false;
        }
    }

    /**
     * Performs database migrations (e.g. adding missing columns).
     *
     * <p>Note: The current schema uses elemental attributes (fire, water, lightning,
     * earth, wind, void_attr). No migrations needed for fresh installs - the schema
     * already defines all columns.
     */
    private void performMigrations() {
        // No migrations needed - schema already has all elemental attribute columns.
        // The old 5-attribute system (STR, DEX, INT, VIT, LUCK) has been replaced with
        // 6 elements (fire, water, lightning, earth, wind, void_attr).
        // Since this plugin is not yet released, no backward compatibility is needed.
    }

    /**
     * Safely adds a column using database-specific syntax
     * (H2/MySQL: IF NOT EXISTS, PostgreSQL: catches duplicate column error).
     */
    private void migrateAddColumn(Statement stmt, String table, String column, String definition) {
        try {
            if (databaseType == DatabaseType.POSTGRESQL) {
                // PostgreSQL doesn't support IF NOT EXISTS for ADD COLUMN in older versions
                // Use DO block for safe column addition
                String sql = String.format(
                    "DO $$ BEGIN " +
                    "ALTER TABLE %s ADD COLUMN %s %s; " +
                    "EXCEPTION WHEN duplicate_column THEN NULL; END $$",
                    table, column, definition);
                stmt.execute(sql);
            } else {
                // H2 and MySQL support IF NOT EXISTS
                String sql = String.format(
                    "ALTER TABLE %s ADD COLUMN IF NOT EXISTS %s %s",
                    table, column, definition);
                stmt.execute(sql);
            }
            LOGGER.at(Level.INFO).log("Migration: Ensured column '%s' exists in %s", column, table);
        } catch (SQLException e) {
            // Check for duplicate column errors using SQL state codes (cross-database compatible)
            if (isDuplicateColumnError(e)) {
                LOGGER.at(Level.FINE).log("Migration: Column '%s' already exists in %s", column, table);
            } else {
                LOGGER.at(Level.SEVERE).log("Migration failed for column '%s' in %s: [%s] %s",
                    column, table, e.getSQLState(), e.getMessage());
            }
        }
    }

    /** Checks SQL state codes: 42S21 (H2/MySQL), 42701 (PostgreSQL). */
    private boolean isDuplicateColumnError(SQLException e) {
        String sqlState = e.getSQLState();
        if (sqlState == null) {
            return false;
        }
        // H2/MySQL: 42S21 (Column already exists)
        // PostgreSQL: 42701 (duplicate_column)
        return "42S21".equals(sqlState) || "42701".equals(sqlState);
    }

    /**
     * @throws SQLException if unable to get connection
     * @throws IllegalStateException if not initialized
     */
    @Nonnull
    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new IllegalStateException("DataManager not initialized or already shut down");
        }
        return dataSource.getConnection();
    }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            LOGGER.at(Level.INFO).log("Database connections closed");
        }
        dataSource = null;
    }

    /** @return null if not initialized */
    public DatabaseType getDatabaseType() {
        return databaseType;
    }

    public boolean isInitialized() {
        return dataSource != null && !dataSource.isClosed();
    }

    // ==================== Private Methods ====================

    private HikariConfig createHikariConfig(DatabaseConfig dbConfig) {
        HikariConfig hikariConfig = new HikariConfig();

        // Basic connection settings
        hikariConfig.setJdbcUrl(databaseType.buildJdbcUrl(dataFolder, dbConfig));
        hikariConfig.setDriverClassName(databaseType.getDriverClassName());

        // Credentials (not needed for H2 file mode)
        if (databaseType != DatabaseType.H2) {
            hikariConfig.setUsername(dbConfig.getUsername());
            hikariConfig.setPassword(dbConfig.getPassword());
        }

        // Pool settings
        hikariConfig.setPoolName("TrailOfOrbis-Pool");
        hikariConfig.setMaximumPoolSize(dbConfig.getPoolSize());
        hikariConfig.setMinimumIdle(Math.max(1, dbConfig.getPoolSize() / 2));

        // Timeout settings (milliseconds)
        hikariConfig.setConnectionTimeout(30_000);      // 30 seconds to get connection
        hikariConfig.setIdleTimeout(600_000);           // 10 minutes idle before removal
        hikariConfig.setMaxLifetime(1_800_000);         // 30 minutes max connection lifetime
        hikariConfig.setLeakDetectionThreshold(60_000); // 1 minute leak detection

        // Performance settings
        hikariConfig.setAutoCommit(true);
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        return hikariConfig;
    }

    private boolean executeSchema() {
        String schema = loadSchemaFromResources();
        if (schema == null) {
            LOGGER.at(Level.SEVERE).log("Failed to load schema.sql from resources");
            return false;
        }

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            // Remove SQL comments (lines starting with --) before processing
            String cleanedSchema = schema.lines()
                .filter(line -> !line.trim().startsWith("--"))
                .collect(Collectors.joining("\n"));

            // Execute each statement separately (H2 and others handle multi-statement differently)
            for (String sql : cleanedSchema.split(";")) {
                String trimmed = sql.trim();
                if (!trimmed.isEmpty()) {
                    stmt.execute(trimmed);
                }
            }

            LOGGER.at(Level.INFO).log("Database schema initialized");
            return true;

        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).withCause(e).log("Failed to execute schema: %s", e.getMessage());
            return false;
        }
    }

    private String loadSchemaFromResources() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(SCHEMA_RESOURCE)) {
            if (is == null) {
                return null;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            LOGGER.at(Level.SEVERE).log("Error reading schema: %s", e.getMessage());
            return null;
        }
    }
}
