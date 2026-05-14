package io.github.larsonix.trailoforbis.maps.persistence;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.database.DataManager;

import javax.annotation.Nonnull;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Repository for realm state persistence and crash recovery.
 *
 * <p>Manages two tables:
 * <ul>
 *   <li>{@code rpg_active_realms} — observability, records which realms were active</li>
 *   <li>{@code rpg_realm_players} — crash recovery, records where players should return</li>
 * </ul>
 *
 * <p>All writes are async via CompletableFuture. Recovery reads are synchronous
 * (called once during startup before players can join).
 */
public class RealmStateRepository {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Active Realms SQL
    private static final String INSERT_REALM =
        "INSERT INTO rpg_active_realms (realm_id, owner_id, world_name, biome, level, size, state, created_at, timeout_seconds, total_monsters, killed_monsters) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String UPDATE_REALM_STATE =
        "UPDATE rpg_active_realms SET state = ? WHERE realm_id = ?";

    private static final String UPDATE_REALM_PROGRESS =
        "UPDATE rpg_active_realms SET killed_monsters = ? WHERE realm_id = ?";

    private static final String DELETE_REALM =
        "DELETE FROM rpg_active_realms WHERE realm_id = ?";

    private static final String DELETE_ALL_REALMS =
        "DELETE FROM rpg_active_realms";

    // Realm Players SQL
    private static final String INSERT_PLAYER =
        "INSERT INTO rpg_realm_players (player_id, realm_id, entered_at, return_world_uuid, return_x, return_y, return_z) " +
        "VALUES (?, ?, CURRENT_TIMESTAMP, ?, ?, ?, ?)";

    private static final String DELETE_PLAYER =
        "DELETE FROM rpg_realm_players WHERE player_id = ?";

    private static final String SELECT_ALL_ORPHANED_PLAYERS =
        "SELECT player_id, realm_id, return_world_uuid, return_x, return_y, return_z FROM rpg_realm_players";

    private static final String DELETE_ALL_PLAYERS =
        "DELETE FROM rpg_realm_players";

    private final DataManager dataManager;

    public RealmStateRepository(@Nonnull DataManager dataManager) {
        this.dataManager = dataManager;
    }

    // ═══════════════════════════════════════════════════════════════════
    // ACTIVE REALMS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Records a realm as active. Called when a realm is created.
     */
    public CompletableFuture<Void> insertRealm(
            @Nonnull UUID realmId, @Nonnull UUID ownerId, @Nonnull String worldName,
            @Nonnull String biome, int level, @Nonnull String size, @Nonnull String state,
            @Nonnull Timestamp createdAt, int timeoutSeconds, int totalMonsters) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(INSERT_REALM)) {
                ps.setString(1, realmId.toString());
                ps.setString(2, ownerId.toString());
                ps.setString(3, worldName);
                ps.setString(4, biome);
                ps.setInt(5, level);
                ps.setString(6, size);
                ps.setString(7, state);
                ps.setTimestamp(8, createdAt);
                ps.setInt(9, timeoutSeconds);
                ps.setInt(10, totalMonsters);
                ps.setInt(11, 0);
                ps.executeUpdate();
            } catch (SQLException e) {
                LOGGER.atWarning().withCause(e).log("Failed to insert active realm %s", realmId);
            }
        });
    }

    /**
     * Updates realm state (e.g., READY → ACTIVE → ENDING).
     */
    public CompletableFuture<Void> updateState(@Nonnull UUID realmId, @Nonnull String state) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(UPDATE_REALM_STATE)) {
                ps.setString(1, state);
                ps.setString(2, realmId.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                LOGGER.atWarning().withCause(e).log("Failed to update realm state %s → %s", realmId, state);
            }
        });
    }

    /**
     * Updates kill progress for a realm.
     */
    public CompletableFuture<Void> updateProgress(@Nonnull UUID realmId, int killedMonsters) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(UPDATE_REALM_PROGRESS)) {
                ps.setInt(1, killedMonsters);
                ps.setString(2, realmId.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                LOGGER.atWarning().withCause(e).log("Failed to update realm progress %s", realmId);
            }
        });
    }

    /**
     * Removes a realm record. Called when a realm closes.
     */
    public CompletableFuture<Void> deleteRealm(@Nonnull UUID realmId) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(DELETE_REALM)) {
                ps.setString(1, realmId.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                LOGGER.atWarning().withCause(e).log("Failed to delete realm %s", realmId);
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // REALM PLAYERS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Records a player entering a realm with their return coordinates.
     */
    public CompletableFuture<Void> insertPlayer(
            @Nonnull UUID playerId, @Nonnull UUID realmId,
            @Nonnull UUID returnWorldUuid, double returnX, double returnY, double returnZ) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(INSERT_PLAYER)) {
                ps.setString(1, playerId.toString());
                ps.setString(2, realmId.toString());
                ps.setString(3, returnWorldUuid.toString());
                ps.setDouble(4, returnX);
                ps.setDouble(5, returnY);
                ps.setDouble(6, returnZ);
                ps.executeUpdate();
            } catch (SQLException e) {
                LOGGER.atWarning().withCause(e).log("Failed to insert realm player %s", playerId);
            }
        });
    }

    /**
     * Removes a player record. Called when a player exits a realm normally.
     */
    public CompletableFuture<Void> removePlayer(@Nonnull UUID playerId) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(DELETE_PLAYER)) {
                ps.setString(1, playerId.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                LOGGER.atWarning().withCause(e).log("Failed to remove realm player %s", playerId);
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // CRASH RECOVERY
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Loads all orphaned players (still in DB after restart = crash).
     *
     * <p>Called synchronously during startup before players can connect.
     *
     * @return List of orphaned player states
     */
    @Nonnull
    public List<OrphanedPlayerState> loadOrphanedPlayers() {
        List<OrphanedPlayerState> result = new ArrayList<>();
        try (Connection conn = dataManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_ALL_ORPHANED_PLAYERS);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new OrphanedPlayerState(
                    UUID.fromString(rs.getString("player_id")),
                    UUID.fromString(rs.getString("realm_id")),
                    UUID.fromString(rs.getString("return_world_uuid")),
                    rs.getDouble("return_x"),
                    rs.getDouble("return_y"),
                    rs.getDouble("return_z")
                ));
            }
        } catch (SQLException e) {
            LOGGER.atSevere().withCause(e).log("Failed to load orphaned realm players");
        }
        return result;
    }

    /**
     * Clears ALL realm data (both tables). Called after processing orphans on startup.
     */
    public void clearAll() {
        try (Connection conn = dataManager.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(DELETE_ALL_PLAYERS)) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(DELETE_ALL_REALMS)) {
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to clear realm state tables");
        }
    }

    /**
     * Orphaned player state record — represents a player who was in a realm
     * when the server crashed and needs to be returned to their origin.
     */
    public record OrphanedPlayerState(
            @Nonnull UUID playerId,
            @Nonnull UUID realmId,
            @Nonnull UUID returnWorldUuid,
            double returnX,
            double returnY,
            double returnZ) {}
}
