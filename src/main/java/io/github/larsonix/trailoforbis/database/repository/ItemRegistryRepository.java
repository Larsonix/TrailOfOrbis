package io.github.larsonix.trailoforbis.database.repository;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.database.DataManager;
import io.github.larsonix.trailoforbis.database.DatabaseType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Repository for item registry persistence.
 *
 * <p>Stores custom item ID to base item ID mappings for server restart persistence.
 * Custom RPG gear items use unique IDs (e.g., "rpg_gear_xxx") that must be registered
 * in Hytale's asset map. This repository persists those mappings so they can be
 * restored on server restart.
 *
 * <h2>Thread Safety</h2>
 * <p>All public methods are thread-safe. Database operations use connection pooling.
 * Async operations use CompletableFuture for non-blocking persistence.
 *
 * <h2>Performance</h2>
 * <p>Designed for scale with 100K+ items:
 * <ul>
 *   <li>loadAll() is O(n) but only called once at startup</li>
 *   <li>register() is async to not block item generation</li>
 *   <li>Batch operations for efficiency</li>
 * </ul>
 */
public class ItemRegistryRepository {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /**
     * @param secondaryInteractionId e.g. "RPG_Stone_Secondary", or null
     */
    public record ItemRegistryEntry(
            @Nonnull String baseItemId,
            @Nullable String secondaryInteractionId) {

        public ItemRegistryEntry {
            Objects.requireNonNull(baseItemId, "baseItemId cannot be null");
        }
    }

    // SQL Queries
    private static final String SELECT_ALL =
        "SELECT custom_id, base_item_id, secondary_interaction_id FROM rpg_item_registry";

    private static final String SELECT_BY_ID =
        "SELECT base_item_id FROM rpg_item_registry WHERE custom_id = ?";

    private static final String UPSERT_MYSQL =
        "INSERT INTO rpg_item_registry (custom_id, base_item_id, secondary_interaction_id, created_at, last_seen) " +
        "VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) " +
        "ON DUPLICATE KEY UPDATE last_seen = CURRENT_TIMESTAMP, secondary_interaction_id = VALUES(secondary_interaction_id)";

    private static final String UPSERT_H2 =
        "MERGE INTO rpg_item_registry (custom_id, base_item_id, secondary_interaction_id, created_at, last_seen) " +
        "KEY(custom_id) VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";

    private static final String UPSERT_POSTGRESQL =
        "INSERT INTO rpg_item_registry (custom_id, base_item_id, secondary_interaction_id, created_at, last_seen) " +
        "VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) " +
        "ON CONFLICT (custom_id) DO UPDATE SET last_seen = CURRENT_TIMESTAMP, secondary_interaction_id = EXCLUDED.secondary_interaction_id";

    // Migration queries for adding column to existing databases
    private static final String MIGRATE_ADD_COLUMN_H2 =
        "ALTER TABLE rpg_item_registry ADD COLUMN IF NOT EXISTS secondary_interaction_id VARCHAR(128)";

    private static final String MIGRATE_ADD_COLUMN_MYSQL =
        "ALTER TABLE rpg_item_registry ADD COLUMN secondary_interaction_id VARCHAR(128)";

    private static final String MIGRATE_ADD_COLUMN_POSTGRESQL =
        "ALTER TABLE rpg_item_registry ADD COLUMN IF NOT EXISTS secondary_interaction_id VARCHAR(128)";

    private static final String UPDATE_LAST_SEEN =
        "UPDATE rpg_item_registry SET last_seen = CURRENT_TIMESTAMP WHERE custom_id = ?";

    private static final String DELETE_OLD_ENTRIES =
        "DELETE FROM rpg_item_registry WHERE last_seen < ?";

    private static final String DELETE_BY_ID =
        "DELETE FROM rpg_item_registry WHERE custom_id = ?";

    private static final String COUNT_ALL =
        "SELECT COUNT(*) FROM rpg_item_registry";

    private final DataManager dataManager;

    /**
     * Pending async registrations to batch together.
     * Map: customId → baseItemId
     */
    private final Map<String, String> pendingRegistrations = new ConcurrentHashMap<>();

    public ItemRegistryRepository(@Nonnull DataManager dataManager) {
        this.dataManager = Objects.requireNonNull(dataManager, "dataManager cannot be null");
    }

    // =========================================================================
    // SCHEMA MIGRATION
    // =========================================================================

    /**
     * Migrates the schema to add new columns for existing databases.
     *
     * <p>This method should be called during initialization to ensure the
     * secondary_interaction_id column exists. It's safe to call multiple times.
     */
    public void migrateSchema() {
        DatabaseType type = dataManager.getDatabaseType();
        String migrateSql = switch (type) {
            case MYSQL -> MIGRATE_ADD_COLUMN_MYSQL;
            case H2 -> MIGRATE_ADD_COLUMN_H2;
            case POSTGRESQL -> MIGRATE_ADD_COLUMN_POSTGRESQL;
        };

        try (Connection conn = dataManager.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute(migrateSql);
            LOGGER.atInfo().log("Item registry schema migration completed successfully");

        } catch (SQLException e) {
            // MySQL throws an error if column already exists (doesn't support IF NOT EXISTS)
            // Check if it's a "duplicate column" error which is safe to ignore
            String message = e.getMessage().toLowerCase();
            if (message.contains("duplicate column") || message.contains("already exists")) {
                LOGGER.atFine().log("Item registry migration skipped - column already exists");
            } else {
                LOGGER.atWarning().withCause(e).log("Item registry schema migration failed");
            }
        }
    }

    // =========================================================================
    // READ OPERATIONS
    // =========================================================================

    /** Called once at server startup to restore item registrations. */
    @Nonnull
    public Map<String, ItemRegistryEntry> loadAll() {
        Map<String, ItemRegistryEntry> result = new HashMap<>();

        try (Connection conn = dataManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_ALL);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String customId = rs.getString("custom_id");
                String baseItemId = rs.getString("base_item_id");
                String secondaryInteractionId = rs.getString("secondary_interaction_id");
                result.put(customId, new ItemRegistryEntry(baseItemId, secondaryInteractionId));
            }

            LOGGER.atInfo().log("Loaded %d item registrations from database", result.size());

        } catch (SQLException e) {
            LOGGER.atSevere().withCause(e).log("Failed to load item registry from database");
        }

        return result;
    }

    /** @return empty if not found */
    @Nonnull
    public Optional<String> getBaseItemId(@Nonnull String customId) {
        Objects.requireNonNull(customId, "customId cannot be null");

        try (Connection conn = dataManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_BY_ID)) {

            stmt.setString(1, customId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getString("base_item_id"));
                }
            }

        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to get base item ID for %s", customId);
        }

        return Optional.empty();
    }

    public int getCount() {
        try (Connection conn = dataManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(COUNT_ALL);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                return rs.getInt(1);
            }

        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to count item registry entries");
        }

        return 0;
    }

    // =========================================================================
    // WRITE OPERATIONS
    // =========================================================================

    /** Async upsert registration. */
    @Nonnull
    public CompletableFuture<Void> register(@Nonnull String customId, @Nonnull String baseItemId) {
        return register(customId, baseItemId, null);
    }

    /** Async upsert with optional secondary interaction. */
    @Nonnull
    public CompletableFuture<Void> register(
            @Nonnull String customId,
            @Nonnull String baseItemId,
            @Nullable String secondaryInteractionId) {

        Objects.requireNonNull(customId, "customId cannot be null");
        Objects.requireNonNull(baseItemId, "baseItemId cannot be null");

        return CompletableFuture.runAsync(() -> {
            try {
                registerSync(customId, baseItemId, secondaryInteractionId);
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log(
                    "Failed to register item %s → %s", customId, baseItemId);
            }
        });
    }

    public void registerSync(@Nonnull String customId, @Nonnull String baseItemId) {
        registerSync(customId, baseItemId, null);
    }

    /** @param secondaryInteractionId null for regular items */
    public void registerSync(
            @Nonnull String customId,
            @Nonnull String baseItemId,
            @Nullable String secondaryInteractionId) {

        Objects.requireNonNull(customId, "customId cannot be null");
        Objects.requireNonNull(baseItemId, "baseItemId cannot be null");

        String upsertSql = getUpsertSql();

        try (Connection conn = dataManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(upsertSql)) {

            stmt.setString(1, customId);
            stmt.setString(2, baseItemId);
            if (secondaryInteractionId != null) {
                stmt.setString(3, secondaryInteractionId);
            } else {
                stmt.setNull(3, Types.VARCHAR);
            }
            stmt.executeUpdate();

            if (secondaryInteractionId != null) {
                LOGGER.atFine().log("Registered item %s → %s (secondary: %s)",
                    customId, baseItemId, secondaryInteractionId);
            } else {
                LOGGER.atFine().log("Registered item %s → %s", customId, baseItemId);
            }

        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log(
                "Failed to register item %s → %s", customId, baseItemId);
        }
    }

    /** Batch upsert registration. */
    public int registerBatch(@Nonnull Map<String, ItemRegistryEntry> items) {
        Objects.requireNonNull(items, "items cannot be null");

        if (items.isEmpty()) {
            return 0;
        }

        String upsertSql = getUpsertSql();
        int registered = 0;

        try (Connection conn = dataManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(upsertSql)) {

            conn.setAutoCommit(false);

            for (Map.Entry<String, ItemRegistryEntry> entry : items.entrySet()) {
                stmt.setString(1, entry.getKey());
                stmt.setString(2, entry.getValue().baseItemId());
                String secondaryId = entry.getValue().secondaryInteractionId();
                if (secondaryId != null) {
                    stmt.setString(3, secondaryId);
                } else {
                    stmt.setNull(3, Types.VARCHAR);
                }
                stmt.addBatch();
                registered++;

                // Execute batch every 500 items
                if (registered % 500 == 0) {
                    stmt.executeBatch();
                }
            }

            // Execute remaining
            stmt.executeBatch();
            conn.commit();

            LOGGER.atInfo().log("Batch registered %d items", registered);

        } catch (SQLException e) {
            LOGGER.atSevere().withCause(e).log("Failed to batch register items");
            return 0;
        }

        return registered;
    }

    /** Called when items are seen in a player's inventory to prevent cleanup. */
    public int updateLastSeen(@Nonnull Collection<String> customIds) {
        Objects.requireNonNull(customIds, "customIds cannot be null");

        if (customIds.isEmpty()) {
            return 0;
        }

        int updated = 0;

        try (Connection conn = dataManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(UPDATE_LAST_SEEN)) {

            conn.setAutoCommit(false);

            for (String customId : customIds) {
                stmt.setString(1, customId);
                stmt.addBatch();
                updated++;

                if (updated % 500 == 0) {
                    stmt.executeBatch();
                }
            }

            stmt.executeBatch();
            conn.commit();

        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to update last_seen for items");
            return 0;
        }

        return updated;
    }

    // =========================================================================
    // CLEANUP OPERATIONS
    // =========================================================================

    /** @param daysOld number of days since last_seen to consider stale */
    public int cleanupOldEntries(int daysOld) {
        if (daysOld <= 0) {
            return 0;
        }

        // Calculate cutoff timestamp
        long cutoffMillis = System.currentTimeMillis() - (daysOld * 24L * 60L * 60L * 1000L);
        Timestamp cutoff = new Timestamp(cutoffMillis);

        try (Connection conn = dataManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(DELETE_OLD_ENTRIES)) {

            stmt.setTimestamp(1, cutoff);
            int removed = stmt.executeUpdate();

            if (removed > 0) {
                LOGGER.atInfo().log("Cleaned up %d stale item registry entries older than %d days",
                    removed, daysOld);
            }

            return removed;

        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to cleanup old item registry entries");
            return 0;
        }
    }

    public boolean remove(@Nonnull String customId) {
        Objects.requireNonNull(customId, "customId cannot be null");

        try (Connection conn = dataManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(DELETE_BY_ID)) {

            stmt.setString(1, customId);
            int affected = stmt.executeUpdate();
            return affected > 0;

        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to remove item %s from registry", customId);
            return false;
        }
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private String getUpsertSql() {
        DatabaseType type = dataManager.getDatabaseType();
        return switch (type) {
            case MYSQL -> UPSERT_MYSQL;
            case H2 -> UPSERT_H2;
            case POSTGRESQL -> UPSERT_POSTGRESQL;
        };
    }
}
