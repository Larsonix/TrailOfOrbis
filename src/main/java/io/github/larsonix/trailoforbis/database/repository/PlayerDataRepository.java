package io.github.larsonix.trailoforbis.database.repository;

import com.hypixel.hytale.logger.HytaleLogger;
import java.util.logging.Level;
import io.github.larsonix.trailoforbis.database.DataManager;
import io.github.larsonix.trailoforbis.database.DatabaseType;
import io.github.larsonix.trailoforbis.database.models.PlayerData;

import javax.annotation.Nonnull;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Repository for player data with cache-aside pattern.
 *
 * <p>Uses an in-memory cache backed by database persistence.
 * Cache is updated on reads and writes; never cleared automatically.
 *
 * <p>Thread-safe: All cache operations use ConcurrentHashMap.
 */
public class PlayerDataRepository {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final String SELECT_BY_UUID =
        "SELECT uuid, username, fire, water, lightning, earth, wind, void_attr, " +
        "unallocated_points, attribute_refund_points, attribute_respecs, created_at, last_seen FROM rpg_players WHERE uuid = ?";

    private static final String SELECT_BY_USERNAME =
        "SELECT uuid, username, fire, water, lightning, earth, wind, void_attr, " +
        "unallocated_points, attribute_refund_points, attribute_respecs, created_at, last_seen FROM rpg_players WHERE LOWER(username) = LOWER(?)";

    private static final String INSERT_PLAYER =
        "INSERT INTO rpg_players (uuid, username, fire, water, lightning, earth, wind, " +
        "void_attr, unallocated_points, attribute_refund_points, attribute_respecs, created_at, last_seen) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String DELETE_PLAYER = "DELETE FROM rpg_players WHERE uuid = ?";

    private static final String UPSERT_MYSQL =
        "INSERT INTO rpg_players (uuid, username, fire, water, lightning, earth, wind, " +
        "void_attr, unallocated_points, attribute_refund_points, attribute_respecs, created_at, last_seen) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
        "ON DUPLICATE KEY UPDATE username = VALUES(username), fire = VALUES(fire), " +
        "water = VALUES(water), lightning = VALUES(lightning), earth = VALUES(earth), " +
        "wind = VALUES(wind), void_attr = VALUES(void_attr), unallocated_points = VALUES(unallocated_points), " +
        "attribute_refund_points = VALUES(attribute_refund_points), attribute_respecs = VALUES(attribute_respecs), " +
        "last_seen = VALUES(last_seen)";

    private static final String UPSERT_H2 =
        "MERGE INTO rpg_players (uuid, username, fire, water, lightning, earth, wind, " +
        "void_attr, unallocated_points, attribute_refund_points, attribute_respecs, created_at, last_seen) KEY(uuid) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String UPSERT_POSTGRESQL =
        "INSERT INTO rpg_players (uuid, username, fire, water, lightning, earth, wind, " +
        "void_attr, unallocated_points, attribute_refund_points, attribute_respecs, created_at, last_seen) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
        "ON CONFLICT (uuid) DO UPDATE SET username = EXCLUDED.username, fire = EXCLUDED.fire, " +
        "water = EXCLUDED.water, lightning = EXCLUDED.lightning, earth = EXCLUDED.earth, " +
        "wind = EXCLUDED.wind, void_attr = EXCLUDED.void_attr, unallocated_points = EXCLUDED.unallocated_points, " +
        "attribute_refund_points = EXCLUDED.attribute_refund_points, attribute_respecs = EXCLUDED.attribute_respecs, " +
        "last_seen = EXCLUDED.last_seen";

    private final DataManager dataManager;
    private final Map<UUID, PlayerData> cache;
    private final Map<String, UUID> usernameIndex; // lowercase username → UUID
    private final Map<UUID, AtomicLong> saveVersions;

    public PlayerDataRepository(@Nonnull DataManager dataManager) {
        this.dataManager = dataManager;
        this.cache = new ConcurrentHashMap<>();
        this.usernameIndex = new ConcurrentHashMap<>();
        this.saveVersions = new ConcurrentHashMap<>();
    }

    /** Checks cache first, then database. */
    @Nonnull
    public Optional<PlayerData> get(@Nonnull UUID uuid) {
        // Check cache first
        PlayerData cached = cache.get(uuid);
        if (cached != null) {
            return Optional.of(cached);
        }

        // Load from database
        try (Connection conn = dataManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_BY_UUID)) {

            stmt.setString(1, uuid.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    PlayerData data = mapResultSet(rs);
                    addToCache(data);
                    return Optional.of(data);
                }
            }

        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to load player data for %s: %s", uuid, e.getMessage());
        }

        return Optional.empty();
    }

    /** Case-insensitive lookup. O(1) via username index for cached players, then database. */
    @Nonnull
    public Optional<PlayerData> getByUsername(@Nonnull String username) {
        // O(1) lookup via username index
        UUID cachedUuid = usernameIndex.get(username.toLowerCase());
        if (cachedUuid != null) {
            PlayerData cached = cache.get(cachedUuid);
            if (cached != null) {
                return Optional.of(cached);
            }
            // Index out of sync - clean up stale entry
            usernameIndex.remove(username.toLowerCase());
        }

        // Load from database
        try (Connection conn = dataManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_BY_USERNAME)) {

            stmt.setString(1, username);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    PlayerData data = mapResultSet(rs);
                    addToCache(data);
                    return Optional.of(data);
                }
            }

        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to load player data for username %s: %s", username, e.getMessage());
        }

        return Optional.empty();
    }

    /** Uses database-specific UPSERT for atomic insert-or-update. */
    public void save(@Nonnull PlayerData data) {
        String upsertSql = getUpsertSql();

        try (Connection conn = dataManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(upsertSql)) {

            setUpsertParameters(stmt, data);
            stmt.executeUpdate();

            // Update cache and username index
            addToCache(data);

        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to save player data for %s: %s", data.getUuid(), e.getMessage());
        }
    }

    /** @param startingPoints initial unallocated attribute points */
    @Nonnull
    public PlayerData create(@Nonnull UUID uuid, @Nonnull String username, int startingPoints) {
        Instant now = Instant.now();
        PlayerData data = PlayerData.builder()
            .uuid(uuid)
            .username(username)
            .fire(0)
            .water(0)
            .lightning(0)
            .earth(0)
            .wind(0)
            .voidAttr(0)
            .unallocatedPoints(startingPoints)
            .createdAt(now)
            .lastSeen(now)
            .build();

        try (Connection conn = dataManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(INSERT_PLAYER)) {

            setInsertParameters(stmt, data);
            stmt.executeUpdate();

            // Add to cache and username index
            addToCache(data);

        } catch (SQLException e) {
            String error = "Failed to create player data for " + uuid + ": " + e.getMessage();
            LOGGER.at(Level.SEVERE).log("Failed to create player data for %s: %s", uuid, e.getMessage());
            throw new RuntimeException(error, e);
        }

        return data;
    }

    public void delete(@Nonnull UUID uuid) {
        try (Connection conn = dataManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(DELETE_PLAYER)) {

            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();

        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to delete player data for %s: %s", uuid, e.getMessage());
        }

        // Always remove from cache and username index
        removeFromCache(uuid);
    }

    /** Batch saves all cached players. Does NOT clear cache after save. */
    public void saveAll() {
        if (cache.isEmpty()) {
            return;
        }

        String upsertSql = getUpsertSql();
        int saved = 0;
        int failed = 0;

        try (Connection conn = dataManager.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false); // Use transaction for batch

                try (PreparedStatement stmt = conn.prepareStatement(upsertSql)) {
                    for (PlayerData data : cache.values()) {
                        try {
                            setUpsertParameters(stmt, data);
                            stmt.addBatch();
                            saved++;
                        } catch (SQLException e) {
                            LOGGER.at(Level.SEVERE).log("Failed to batch player %s: %s", data.getUuid(), e.getMessage());
                            failed++;
                        }
                    }

                    stmt.executeBatch();
                    conn.commit();

                } catch (SQLException e) {
                    conn.rollback();
                    LOGGER.at(Level.SEVERE).log("Batch save failed, rolling back: %s", e.getMessage());
                    failed = saved;
                    saved = 0;
                }
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }

        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to get connection for batch save: %s", e.getMessage());
        }

        if (saved > 0) {
            LOGGER.at(Level.INFO).log("Batch saved %d players%s", saved,
                (failed > 0 ? " (" + failed + " failed)" : ""));
        }
    }

    /** For testing or admin reload. Does NOT affect database. */
    public void clearCache() {
        cache.clear();
        usernameIndex.clear();
    }

    /**
     * Evicts a player from the cache and username index without affecting the database.
     *
     * <p>Call this when a player disconnects (after saving) to prevent
     * unbounded cache growth over time. The player's data remains in the
     * database and will be re-cached on next login.
     *
     * @param uuid The player's UUID to evict from cache
     */
    public void evict(@Nonnull UUID uuid) {
        removeFromCache(uuid);
    }

    /**
     * Gets the current save version for a player.
     *
     * <p>Used for versioned saves to prevent race conditions when a player
     * disconnects and reconnects quickly.
     *
     * @param uuid The player's UUID
     * @return The current save version
     */
    public long getSaveVersion(@Nonnull UUID uuid) {
        return saveVersions.computeIfAbsent(uuid, k -> new AtomicLong(0)).get();
    }

    /**
     * Increments and returns the save version for a player.
     *
     * <p>Call this when a player connects to invalidate any pending async saves
     * from a previous session. This prevents stale data from overwriting newer data
     * if the player disconnects and reconnects quickly.
     *
     * @param uuid The player's UUID
     * @return The new save version
     */
    public long incrementSaveVersion(@Nonnull UUID uuid) {
        return saveVersions.computeIfAbsent(uuid, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * For async disconnect saves. Saves to database WITHOUT updating the cache.
     * Skips if version mismatch (player has reconnected since snapshot).
     *
     * @param expectedVersion the save version when the save was initiated
     * @return false if skipped due to version mismatch
     */
    public boolean saveWithVersion(@Nonnull PlayerData data, long expectedVersion) {
        UUID uuid = data.getUuid();
        long currentVersion = getSaveVersion(uuid);

        if (currentVersion != expectedVersion) {
            LOGGER.at(Level.INFO).log(
                "Skipping stale async save for %s (expected version %d, current %d) - player reconnected",
                uuid, expectedVersion, currentVersion);
            return false;
        }

        String upsertSql = getUpsertSql();

        try (Connection conn = dataManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(upsertSql)) {

            setUpsertParameters(stmt, data);
            stmt.executeUpdate();

            // Note: We intentionally do NOT update the cache here.
            // This is an async save of a snapshot taken at disconnect time.
            // If the player has reconnected, they have fresh data in cache.

            return true;

        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to save player data for %s: %s", uuid, e.getMessage());
            return false;
        }
    }


    /** Updates cache only (no DB write). For transient data like ComputedStats. */
    public void updateCache(@Nonnull UUID uuid, @Nonnull PlayerData data) {
        addToCache(data);
    }

    public int getCacheSize() {
        return cache.size();
    }

    @Nonnull
    public Set<UUID> getCachedUuids() {
        return Collections.unmodifiableSet(cache.keySet());
    }

    // ==================== Private Helpers ====================

    /** Handles username changes by removing old index entry. */
    private void addToCache(@Nonnull PlayerData data) {
        UUID uuid = data.getUuid();
        String newUsernameLower = data.getUsername().toLowerCase();

        // Check if this player already exists with a different username
        PlayerData existing = cache.get(uuid);
        if (existing != null) {
            String oldUsernameLower = existing.getUsername().toLowerCase();
            if (!oldUsernameLower.equals(newUsernameLower)) {
                // Username changed - remove old index entry
                usernameIndex.remove(oldUsernameLower);
            }
        }

        // Update both cache and index
        cache.put(uuid, data);
        usernameIndex.put(newUsernameLower, uuid);
    }

    private void removeFromCache(@Nonnull UUID uuid) {
        PlayerData existing = cache.remove(uuid);
        if (existing != null) {
            usernameIndex.remove(existing.getUsername().toLowerCase());
        }
    }

    private String getUpsertSql() {
        DatabaseType dbType = dataManager.getDatabaseType();
        // Using traditional switch to avoid anonymous inner class generation issues with Shadow plugin
        switch (dbType) {
            case H2:
                return UPSERT_H2;
            case MYSQL:
                return UPSERT_MYSQL;
            case POSTGRESQL:
                return UPSERT_POSTGRESQL;
            default:
                throw new IllegalStateException("Unknown database type: " + dbType);
        }
    }

    private PlayerData mapResultSet(ResultSet rs) throws SQLException {
        Timestamp createdAtTs = rs.getTimestamp("created_at");
        Timestamp lastSeenTs = rs.getTimestamp("last_seen");

        return PlayerData.builder()
            .uuid(UUID.fromString(rs.getString("uuid")))
            .username(rs.getString("username"))
            .fire(rs.getInt("fire"))
            .water(rs.getInt("water"))
            .lightning(rs.getInt("lightning"))
            .earth(rs.getInt("earth"))
            .wind(rs.getInt("wind"))
            .voidAttr(rs.getInt("void_attr"))
            .unallocatedPoints(rs.getInt("unallocated_points"))
            .attributeRefundPoints(rs.getInt("attribute_refund_points"))
            .attributeRespecs(rs.getInt("attribute_respecs"))
            .createdAt(createdAtTs != null ? createdAtTs.toInstant() : Instant.now())
            .lastSeen(lastSeenTs != null ? lastSeenTs.toInstant() : Instant.now())
            .build();
    }

    private void setInsertParameters(PreparedStatement stmt, PlayerData data) throws SQLException {
        stmt.setString(1, data.getUuid().toString());
        stmt.setString(2, data.getUsername());
        stmt.setInt(3, data.getFire());
        stmt.setInt(4, data.getWater());
        stmt.setInt(5, data.getLightning());
        stmt.setInt(6, data.getEarth());
        stmt.setInt(7, data.getWind());
        stmt.setInt(8, data.getVoidAttr());
        stmt.setInt(9, data.getUnallocatedPoints());
        stmt.setInt(10, data.getAttributeRefundPoints());
        stmt.setInt(11, data.getAttributeRespecs());
        stmt.setTimestamp(12, Timestamp.from(data.getCreatedAt()));
        stmt.setTimestamp(13, Timestamp.from(data.getLastSeen()));
    }

    private void setUpsertParameters(PreparedStatement stmt, PlayerData data) throws SQLException {
        // Same parameter order for all UPSERT variants
        setInsertParameters(stmt, data);
    }
}
