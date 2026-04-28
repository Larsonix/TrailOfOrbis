package io.github.larsonix.trailoforbis.skilltree.repository;

import com.hypixel.hytale.logger.HytaleLogger;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.github.larsonix.trailoforbis.database.DataManager;
import io.github.larsonix.trailoforbis.database.DatabaseType;
import io.github.larsonix.trailoforbis.skilltree.SkillTreeManager;
import io.github.larsonix.trailoforbis.skilltree.model.SkillTreeData;

import javax.annotation.Nonnull;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Repository for skill tree data with cache-aside pattern.
 *
 * <p>Uses an in-memory cache backed by database persistence.
 * Cache is updated on reads and writes; never cleared automatically.
 *
 * <p>Thread-safe: All cache operations use ConcurrentHashMap.
 */
public class SkillTreeRepository {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final String SELECT_BY_UUID =
        "SELECT uuid, allocated_nodes, skill_points, total_points_earned, " +
        "respecs, skill_refund_points, created_at, last_modified FROM rpg_skill_tree WHERE uuid = ?";

    private static final String INSERT =
        "INSERT INTO rpg_skill_tree (uuid, allocated_nodes, skill_points, " +
        "total_points_earned, respecs, skill_refund_points, created_at, last_modified) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String UPSERT_H2 =
        "MERGE INTO rpg_skill_tree (uuid, allocated_nodes, skill_points, " +
        "total_points_earned, respecs, skill_refund_points, created_at, last_modified) KEY(uuid) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String UPSERT_MYSQL =
        "INSERT INTO rpg_skill_tree (uuid, allocated_nodes, skill_points, " +
        "total_points_earned, respecs, skill_refund_points, created_at, last_modified) VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
        "ON DUPLICATE KEY UPDATE allocated_nodes = VALUES(allocated_nodes), " +
        "skill_points = VALUES(skill_points), total_points_earned = VALUES(total_points_earned), " +
        "respecs = VALUES(respecs), skill_refund_points = VALUES(skill_refund_points), last_modified = VALUES(last_modified)";

    private static final String UPSERT_POSTGRESQL =
        "INSERT INTO rpg_skill_tree (uuid, allocated_nodes, skill_points, " +
        "total_points_earned, respecs, skill_refund_points, created_at, last_modified) VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
        "ON CONFLICT (uuid) DO UPDATE SET allocated_nodes = EXCLUDED.allocated_nodes, " +
        "skill_points = EXCLUDED.skill_points, total_points_earned = EXCLUDED.total_points_earned, " +
        "respecs = EXCLUDED.respecs, skill_refund_points = EXCLUDED.skill_refund_points, last_modified = EXCLUDED.last_modified";

    private final DataManager dataManager;
    private final Map<UUID, SkillTreeData> cache;
    private final Gson gson;

    /**
     * Creates a new SkillTreeRepository.
     *
     * @param dataManager The data manager for database connections
     */
    public SkillTreeRepository(@Nonnull DataManager dataManager) {
        this.dataManager = Objects.requireNonNull(dataManager, "dataManager cannot be null");
        this.cache = new ConcurrentHashMap<>();
        this.gson = new Gson();
    }

    /**
     * Gets skill tree data by UUID.
     *
     * <p>Checks cache first, then database. If found in database, caches the result.
     *
     * @param uuid The player's UUID
     * @return Optional containing skill tree data, or empty if not found
     */
    @Nonnull
    public Optional<SkillTreeData> get(@Nonnull UUID uuid) {
        // Check cache first
        SkillTreeData cached = cache.get(uuid);
        if (cached != null) {
            return Optional.of(cached);
        }

        // Load from database
        try (Connection conn = dataManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_BY_UUID)) {

            stmt.setString(1, uuid.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    SkillTreeData data = mapResultSet(rs);
                    cache.put(uuid, data);
                    return Optional.of(data);
                }
            }

        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to load skill tree for %s: %s", uuid, e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Saves skill tree data to database and updates cache.
     *
     * <p>Uses database-specific UPSERT syntax for atomic insert-or-update.
     *
     * @param data The skill tree data to save
     */
    public void save(@Nonnull SkillTreeData data) {
        String upsertSql = getUpsertSql();

        try (Connection conn = dataManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(upsertSql)) {

            setUpsertParameters(stmt, data);
            stmt.executeUpdate();

            // Update cache
            cache.put(data.getUuid(), data);

        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to save skill tree for %s: %s", data.getUuid(), e.getMessage());
        }
    }

    /**
     * Gets skill tree data, creating a new entry if it doesn't exist.
     *
     * <p>New players are created with the origin node pre-allocated. This is required
     * because the skill tree graph uses origin as the starting point for adjacency
     * calculations. Without origin in allocatedNodes, no adjacent nodes would be
     * marked as AVAILABLE.
     *
     * @param uuid The player's UUID
     * @param startingPoints Initial skill points for new entries
     * @return The skill tree data (existing or newly created)
     */
    @Nonnull
    public SkillTreeData getOrCreate(@Nonnull UUID uuid, int startingPoints) {
        return get(uuid).orElseGet(() -> {
            SkillTreeData newData = SkillTreeData.builder()
                .uuid(uuid)
                .skillPoints(startingPoints)
                .totalPointsEarned(startingPoints)
                .allocatedNodes(Set.of(SkillTreeManager.ORIGIN_NODE_ID))  // Origin is always allocated for new players
                .build();
            save(newData);
            return newData;
        });
    }

    /**
     * Batch saves all cached skill tree data to database.
     *
     * <p>Note: Does NOT clear cache after save (data remains valid).
     */
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
                conn.setAutoCommit(false);

                try (PreparedStatement stmt = conn.prepareStatement(upsertSql)) {
                    for (SkillTreeData data : cache.values()) {
                        try {
                            setUpsertParameters(stmt, data);
                            stmt.addBatch();
                            saved++;
                        } catch (SQLException e) {
                            LOGGER.at(Level.SEVERE).log("Failed to batch skill tree %s: %s",
                                data.getUuid(), e.getMessage());
                            failed++;
                        }
                    }

                    stmt.executeBatch();
                    conn.commit();

                } catch (SQLException e) {
                    conn.rollback();
                    LOGGER.at(Level.SEVERE).log("Skill tree batch save failed, rolling back: %s", e.getMessage());
                    failed = saved;
                    saved = 0;
                }
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }

        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to get connection for skill tree batch save: %s", e.getMessage());
        }

        if (saved > 0 || failed > 0) {
            LOGGER.at(Level.INFO).log("Skill tree batch saved: %d saved%s", saved,
                (failed > 0 ? ", " + failed + " failed" : ""));
        }
    }

    /**
     * Clears the in-memory cache.
     *
     * <p>Use for testing or admin reload commands. Does NOT affect database.
     */
    public void clearCache() {
        cache.clear();
    }

    /**
     * Updates the cache only without persisting to database.
     *
     * @param data The skill tree data to cache
     */
    public void updateCache(@Nonnull SkillTreeData data) {
        cache.put(data.getUuid(), data);
    }

    /**
     * Returns the number of cached entries.
     *
     * @return Cache size
     */
    public int getCacheSize() {
        return cache.size();
    }

    // ==================== Private Helpers ====================

    private String getUpsertSql() {
        DatabaseType dbType = dataManager.getDatabaseType();
        return switch (dbType) {
            case H2 -> UPSERT_H2;
            case MYSQL -> UPSERT_MYSQL;
            case POSTGRESQL -> UPSERT_POSTGRESQL;
        };
    }

    private SkillTreeData mapResultSet(ResultSet rs) throws SQLException {
        String nodesJson = rs.getString("allocated_nodes");
        Set<String> nodes = (nodesJson != null && !nodesJson.isEmpty())
            ? gson.fromJson(nodesJson, new TypeToken<HashSet<String>>(){}.getType())
            : new HashSet<>();

        Timestamp createdAtTs = rs.getTimestamp("created_at");
        Timestamp lastModifiedTs = rs.getTimestamp("last_modified");

        return SkillTreeData.builder()
            .uuid(UUID.fromString(rs.getString("uuid")))
            .allocatedNodes(nodes)
            .skillPoints(rs.getInt("skill_points"))
            .totalPointsEarned(rs.getInt("total_points_earned"))
            .respecs(rs.getInt("respecs"))
            .skillRefundPoints(rs.getInt("skill_refund_points"))
            .createdAt(createdAtTs != null ? createdAtTs.toInstant() : Instant.now())
            .lastModified(lastModifiedTs != null ? lastModifiedTs.toInstant() : Instant.now())
            .build();
    }

    private void setUpsertParameters(PreparedStatement stmt, SkillTreeData data) throws SQLException {
        stmt.setString(1, data.getUuid().toString());
        stmt.setString(2, gson.toJson(data.getAllocatedNodes()));
        stmt.setInt(3, data.getSkillPoints());
        stmt.setInt(4, data.getTotalPointsEarned());
        stmt.setInt(5, data.getRespecs());
        stmt.setInt(6, data.getSkillRefundPoints());
        stmt.setTimestamp(7, Timestamp.from(data.getCreatedAt()));
        stmt.setTimestamp(8, Timestamp.from(data.getLastModified()));
    }
}
