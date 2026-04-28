package io.github.larsonix.trailoforbis.leveling.repository;

import com.hypixel.hytale.logger.HytaleLogger;
import java.util.logging.Level;
import io.github.larsonix.trailoforbis.database.DataManager;
import io.github.larsonix.trailoforbis.leveling.core.PlayerLevelData;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Repository for player leveling data with cache-aside pattern.
 *
 * <p>Uses {@link ConcurrentHashMap} for in-memory caching with async
 * database persistence. Reads are served from cache when available,
 * writes update cache immediately and persist asynchronously.
 *
 * <p>Thread safety: All public methods are thread-safe.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>On player join: {@link #loadOrCreate(UUID)} populates cache</li>
 *   <li>During play: {@link #get(UUID)} returns cached data</li>
 *   <li>On XP change: {@link #save(PlayerLevelData)} updates cache + async persist</li>
 *   <li>On player quit: {@link #evict(UUID)} removes from cache</li>
 *   <li>On shutdown: {@link #shutdown()} saves all + closes executor</li>
 * </ol>
 */
public class LevelingRepository {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final String INSERT_SQL =
        "INSERT INTO rpg_levels (uuid, xp, created_at, last_updated) VALUES (?, ?, ?, ?)";
    private static final String UPDATE_SQL =
        "UPDATE rpg_levels SET xp = ?, last_updated = ? WHERE uuid = ?";
    private static final String SELECT_SQL =
        "SELECT xp, created_at, last_updated FROM rpg_levels WHERE uuid = ?";
    private static final String DELETE_SQL =
        "DELETE FROM rpg_levels WHERE uuid = ?";

    private final DataManager dataManager;
    private final Map<UUID, PlayerLevelData> cache = new ConcurrentHashMap<>();
    private final ExecutorService saveExecutor;

    /** Creates a new LevelingRepository. */
    public LevelingRepository(@Nonnull DataManager dataManager) {
        this.dataManager = dataManager;
        // Single-threaded executor for ordered async saves
        this.saveExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "TrailOfOrbis-LevelingSave");
            t.setDaemon(true);
            return t;
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // CACHE OPERATIONS
    // ═══════════════════════════════════════════════════════════════════

    /** @return The cached data, or null if not in cache */
    @Nullable
    public PlayerLevelData get(@Nonnull UUID playerId) {
        return cache.get(playerId);
    }

    /** @return The cached data, or new default data if not found */
    @Nonnull
    public PlayerLevelData getOrDefault(@Nonnull UUID playerId) {
        PlayerLevelData data = cache.get(playerId);
        return data != null ? data : PlayerLevelData.createNew(playerId);
    }

    /** @return true if data is cached */
    public boolean isCached(@Nonnull UUID playerId) {
        return cache.containsKey(playerId);
    }

    /** @return The removed data, or null if not found */
    @Nullable
    public PlayerLevelData evict(@Nonnull UUID playerId) {
        return cache.remove(playerId);
    }

    // ═══════════════════════════════════════════════════════════════════
    // DATABASE OPERATIONS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Loads player data from database, or creates new if not found.
     *
     * <p>Updates cache with the loaded/created data.
     *
     * @return The loaded or newly created data
     */
    @Nonnull
    public PlayerLevelData loadOrCreate(@Nonnull UUID playerId) {
        // Check cache first
        PlayerLevelData cached = cache.get(playerId);
        if (cached != null) {
            return cached;
        }

        // Try to load from database
        PlayerLevelData loaded = loadFromDatabase(playerId);
        if (loaded != null) {
            cache.put(playerId, loaded);
            return loaded;
        }

        // Create new record
        PlayerLevelData newData = PlayerLevelData.createNew(playerId);
        cache.put(playerId, newData);

        // Persist to database asynchronously
        insertAsync(newData);

        return newData;
    }

    /** Saves player data to cache and schedules async database persist. */
    public void save(@Nonnull PlayerLevelData data) {
        cache.put(data.uuid(), data);
        updateAsync(data);
    }

    /**
     * Saves player data synchronously (blocking).
     *
     * <p>Use for shutdown or when immediate persistence is required.
     *
     * @return true if save succeeded
     */
    public boolean saveSync(@Nonnull PlayerLevelData data) {
        cache.put(data.uuid(), data);
        return updateSync(data);
    }

    /** Deletes player data from database and cache. */
    @Nonnull
    public CompletableFuture<Boolean> delete(@Nonnull UUID playerId) {
        cache.remove(playerId);
        return CompletableFuture.supplyAsync(() -> deleteSync(playerId), saveExecutor)
            .exceptionally(ex -> {
                LOGGER.atSevere().withCause(ex).log("Async operation failed: %s", ex.getMessage());
                return false;
            });
    }

    // ═══════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Saves all cached data synchronously (without clearing cache or shutting down).
     *
     * <p>Call before shutdown to ensure all data is persisted.
     */
    public void saveAll() {
        for (PlayerLevelData data : cache.values()) {
            try {
                updateSync(data);
            } catch (Exception e) {
                LOGGER.at(Level.SEVERE).withCause(e).log("Failed to save leveling data for %s", data.uuid());
            }
        }
    }

    /**
     * Saves all cached data synchronously and shuts down executor.
     *
     * <p>Call during plugin shutdown.
     */
    public void shutdown() {
        // Save all cached data
        for (PlayerLevelData data : cache.values()) {
            try {
                updateSync(data);
            } catch (Exception e) {
                LOGGER.at(Level.SEVERE).withCause(e).log("Failed to save leveling data for %s", data.uuid());
            }
        }

        // Shutdown executor
        saveExecutor.shutdown();

        // Clear cache
        cache.clear();

        LOGGER.at(Level.INFO).log("LevelingRepository shutdown complete");
    }

    /** Gets the number of cached entries. */
    public int getCacheSize() {
        return cache.size();
    }

    // ═══════════════════════════════════════════════════════════════════
    // PRIVATE DATABASE METHODS
    // ═══════════════════════════════════════════════════════════════════

    @Nullable
    private PlayerLevelData loadFromDatabase(@Nonnull UUID playerId) {
        try (Connection conn = dataManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_SQL)) {

            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    long xp = rs.getLong("xp");
                    Timestamp createdTs = rs.getTimestamp("created_at");
                    Timestamp updatedTs = rs.getTimestamp("last_updated");

                    Instant createdAt = createdTs != null ? createdTs.toInstant() : Instant.now();
                    Instant lastUpdated = updatedTs != null ? updatedTs.toInstant() : Instant.now();

                    return new PlayerLevelData(playerId, xp, createdAt, lastUpdated);
                }
            }
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).withCause(e).log("Failed to load leveling data for %s", playerId);
        }
        return null;
    }

    private void insertAsync(@Nonnull PlayerLevelData data) {
        saveExecutor.execute(() -> {
            try (Connection conn = dataManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(INSERT_SQL)) {

                stmt.setString(1, data.uuid().toString());
                stmt.setLong(2, data.xp());
                stmt.setTimestamp(3, Timestamp.from(data.createdAt()));
                stmt.setTimestamp(4, Timestamp.from(data.lastUpdated()));
                stmt.executeUpdate();

            } catch (SQLException e) {
                LOGGER.at(Level.SEVERE).withCause(e).log("Failed to insert leveling data for %s", data.uuid());
            }
        });
    }

    private void updateAsync(@Nonnull PlayerLevelData data) {
        saveExecutor.execute(() -> updateSync(data));
    }

    private boolean updateSync(@Nonnull PlayerLevelData data) {
        try (Connection conn = dataManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(UPDATE_SQL)) {

            stmt.setLong(1, data.xp());
            stmt.setTimestamp(2, Timestamp.from(data.lastUpdated()));
            stmt.setString(3, data.uuid().toString());

            int rows = stmt.executeUpdate();

            // If no rows updated, the record doesn't exist - insert it
            if (rows == 0) {
                try (PreparedStatement insertStmt = conn.prepareStatement(INSERT_SQL)) {
                    insertStmt.setString(1, data.uuid().toString());
                    insertStmt.setLong(2, data.xp());
                    insertStmt.setTimestamp(3, Timestamp.from(data.createdAt()));
                    insertStmt.setTimestamp(4, Timestamp.from(data.lastUpdated()));
                    insertStmt.executeUpdate();
                }
            }

            return true;

        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).withCause(e).log("Failed to update leveling data for %s", data.uuid());
            return false;
        }
    }

    private boolean deleteSync(@Nonnull UUID playerId) {
        try (Connection conn = dataManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(DELETE_SQL)) {

            stmt.setString(1, playerId.toString());
            stmt.executeUpdate();
            return true;

        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).withCause(e).log("Failed to delete leveling data for %s", playerId);
            return false;
        }
    }
}
