package io.github.larsonix.trailoforbis.lootfilter.repository;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.database.DataManager;
import io.github.larsonix.trailoforbis.lootfilter.model.FilterCondition;
import io.github.larsonix.trailoforbis.lootfilter.model.PlayerFilterState;

import javax.annotation.Nonnull;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistence layer for per-player loot filter state.
 *
 * <p>Uses a {@link ConcurrentHashMap} cache for fast reads during pickup evaluation,
 * backed by a single JSON blob per player in the database.
 */
public final class LootFilterRepository {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final String SELECT = "SELECT filter_data FROM rpg_loot_filters WHERE uuid = ?";
    private static final String UPSERT_H2 =
            "MERGE INTO rpg_loot_filters (uuid, filter_data, last_modified) KEY(uuid) VALUES (?, ?, CURRENT_TIMESTAMP)";
    private static final String UPSERT_MYSQL =
            "INSERT INTO rpg_loot_filters (uuid, filter_data, last_modified) VALUES (?, ?, CURRENT_TIMESTAMP) " +
            "ON DUPLICATE KEY UPDATE filter_data = VALUES(filter_data), last_modified = CURRENT_TIMESTAMP";
    private static final String UPSERT_POSTGRES =
            "INSERT INTO rpg_loot_filters (uuid, filter_data, last_modified) VALUES (?, ?, CURRENT_TIMESTAMP) " +
            "ON CONFLICT (uuid) DO UPDATE SET filter_data = EXCLUDED.filter_data, last_modified = CURRENT_TIMESTAMP";

    private final DataManager dataManager;
    private final ConcurrentHashMap<UUID, PlayerFilterState> cache = new ConcurrentHashMap<>();
    private final Gson gson;

    public LootFilterRepository(@Nonnull DataManager dataManager) {
        this.dataManager = dataManager;
        this.gson = new GsonBuilder()
                .registerTypeAdapter(Instant.class, new InstantTypeAdapter())
                .registerTypeHierarchyAdapter(FilterCondition.class, new FilterConditionTypeAdapter())
                .create();
    }

    /**
     * Get cached state, loading from DB if not cached.
     */
    @Nonnull
    public Optional<PlayerFilterState> get(@Nonnull UUID uuid) {
        PlayerFilterState cached = cache.get(uuid);
        if (cached != null) return Optional.of(cached);

        // Load from DB
        try (Connection conn = dataManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String json = rs.getString("filter_data");
                    PlayerFilterState state = gson.fromJson(json, PlayerFilterState.class);
                    cache.put(uuid, state);
                    return Optional.of(state);
                }
            }
        } catch (SQLException e) {
            LOGGER.atSevere().withCause(e).log("Failed to load filter state for %s", uuid);
        }
        return Optional.empty();
    }

    /**
     * Get existing state or create a new empty one.
     */
    @Nonnull
    public PlayerFilterState getOrCreate(@Nonnull UUID uuid) {
        return get(uuid).orElseGet(() -> {
            PlayerFilterState empty = PlayerFilterState.builder()
                    .playerId(uuid)
                    .build();
            cache.put(uuid, empty);
            return empty;
        });
    }

    /**
     * Save state to cache and DB.
     */
    public void save(@Nonnull PlayerFilterState state) {
        cache.put(state.getPlayerId(), state);
        String json = gson.toJson(state);

        try (Connection conn = dataManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(getUpsertSql(conn))) {
            stmt.setString(1, state.getPlayerId().toString());
            stmt.setString(2, json);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.atSevere().withCause(e).log("Failed to save filter state for %s", state.getPlayerId());
        }
    }

    /**
     * Batch save all cached entries (called on shutdown).
     */
    public void saveAll() {
        if (cache.isEmpty()) return;
        int count = 0;

        try (Connection conn = dataManager.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement stmt = conn.prepareStatement(getUpsertSql(conn))) {
                for (PlayerFilterState state : cache.values()) {
                    stmt.setString(1, state.getPlayerId().toString());
                    stmt.setString(2, gson.toJson(state));
                    stmt.addBatch();
                    count++;
                }
                stmt.executeBatch();
                conn.commit();
                LOGGER.atInfo().log("Saved %d loot filter states", count);
            } catch (SQLException e) {
                conn.rollback();
                LOGGER.atSevere().withCause(e).log("Failed to batch save filter states");
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            LOGGER.atSevere().withCause(e).log("Failed to get connection for batch save");
        }
    }

    /**
     * Remove from cache (called after save on disconnect).
     */
    public void evict(@Nonnull UUID uuid) {
        cache.remove(uuid);
    }

    /**
     * Clear entire cache (called on shutdown after saveAll).
     */
    public void clearCache() {
        cache.clear();
    }

    @Nonnull
    Gson getGson() {
        return gson;
    }

    private String getUpsertSql(Connection conn) throws SQLException {
        String productName = conn.getMetaData().getDatabaseProductName().toLowerCase();
        if (productName.contains("mysql") || productName.contains("mariadb")) {
            return UPSERT_MYSQL;
        } else if (productName.contains("postgresql")) {
            return UPSERT_POSTGRES;
        }
        return UPSERT_H2;
    }
}
