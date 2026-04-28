package io.github.larsonix.trailoforbis.database.repository;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.database.DataManager;
import io.github.larsonix.trailoforbis.database.DatabaseType;

import javax.annotation.Nonnull;
import java.sql.*;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Repository for spawn gateway state persistence.
 *
 * <p>Tracks which worlds have had spawn gateway portals placed around their
 * spawn point. This prevents re-creating portals on server restart.
 *
 * <h2>Thread Safety</h2>
 * <p>All public methods are thread-safe. Database operations use connection pooling.
 */
public class SpawnGatewayRepository {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // SQL Queries
    private static final String SELECT_BY_WORLD =
        "SELECT gateways_placed, portal_count, ring_radius, placed_at " +
        "FROM rpg_spawn_gateways WHERE world_uuid = ?";

    private static final String UPSERT_MYSQL =
        "INSERT INTO rpg_spawn_gateways (world_uuid, gateways_placed, portal_count, ring_radius, placed_at) " +
        "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP) " +
        "ON DUPLICATE KEY UPDATE gateways_placed = VALUES(gateways_placed), " +
        "portal_count = VALUES(portal_count), ring_radius = VALUES(ring_radius)";

    private static final String UPSERT_H2 =
        "MERGE INTO rpg_spawn_gateways (world_uuid, gateways_placed, portal_count, ring_radius, placed_at) " +
        "KEY(world_uuid) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)";

    private static final String UPSERT_POSTGRESQL =
        "INSERT INTO rpg_spawn_gateways (world_uuid, gateways_placed, portal_count, ring_radius, placed_at) " +
        "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP) " +
        "ON CONFLICT (world_uuid) DO UPDATE SET gateways_placed = EXCLUDED.gateways_placed, " +
        "portal_count = EXCLUDED.portal_count, ring_radius = EXCLUDED.ring_radius";

    private static final String DELETE_BY_WORLD =
        "DELETE FROM rpg_spawn_gateways WHERE world_uuid = ?";

    private final DataManager dataManager;

    public record GatewayState(
            @Nonnull UUID worldUuid,
            boolean gatewaysPlaced,
            int portalCount,
            int ringRadius,
            @Nonnull Timestamp placedAt) {

        public GatewayState {
            Objects.requireNonNull(worldUuid, "worldUuid cannot be null");
            Objects.requireNonNull(placedAt, "placedAt cannot be null");
        }
    }

    public SpawnGatewayRepository(@Nonnull DataManager dataManager) {
        this.dataManager = Objects.requireNonNull(dataManager, "dataManager cannot be null");
    }

    // =========================================================================
    // READ OPERATIONS
    // =========================================================================

    public boolean hasGatewaysPlaced(@Nonnull UUID worldUuid) {
        Objects.requireNonNull(worldUuid, "worldUuid cannot be null");

        try (Connection conn = dataManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_BY_WORLD)) {

            stmt.setString(1, worldUuid.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean("gateways_placed");
                }
            }

        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to check gateway state for world %s",
                worldUuid.toString().substring(0, 8));
        }

        return false;
    }

    /** @return empty if world has no gateway record */
    @Nonnull
    public Optional<GatewayState> getGatewayState(@Nonnull UUID worldUuid) {
        Objects.requireNonNull(worldUuid, "worldUuid cannot be null");

        try (Connection conn = dataManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_BY_WORLD)) {

            stmt.setString(1, worldUuid.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new GatewayState(
                        worldUuid,
                        rs.getBoolean("gateways_placed"),
                        rs.getInt("portal_count"),
                        rs.getInt("ring_radius"),
                        rs.getTimestamp("placed_at")
                    ));
                }
            }

        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to get gateway state for world %s",
                worldUuid.toString().substring(0, 8));
        }

        return Optional.empty();
    }

    // =========================================================================
    // WRITE OPERATIONS
    // =========================================================================

    /** Async version. */
    @Nonnull
    public CompletableFuture<Void> markGatewaysPlaced(
            @Nonnull UUID worldUuid,
            int portalCount,
            int ringRadius) {

        Objects.requireNonNull(worldUuid, "worldUuid cannot be null");

        return CompletableFuture.runAsync(() -> {
            markGatewaysPlacedSync(worldUuid, portalCount, ringRadius);
        }).exceptionally(ex -> {
            LOGGER.atSevere().withCause(ex).log("Async operation failed: %s", ex.getMessage());
            return null;
        });
    }

    public void markGatewaysPlacedSync(
            @Nonnull UUID worldUuid,
            int portalCount,
            int ringRadius) {

        Objects.requireNonNull(worldUuid, "worldUuid cannot be null");

        String upsertSql = getUpsertSql();

        try (Connection conn = dataManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(upsertSql)) {

            stmt.setString(1, worldUuid.toString());
            stmt.setBoolean(2, true);
            stmt.setInt(3, portalCount);
            stmt.setInt(4, ringRadius);
            stmt.executeUpdate();

            LOGGER.atFine().log("Marked gateways placed for world %s: %d portals, radius %d",
                worldUuid.toString().substring(0, 8), portalCount, ringRadius);

        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to mark gateways placed for world %s",
                worldUuid.toString().substring(0, 8));
        }
    }

    /** Use to re-place gateways (e.g., after config change). */
    public boolean removeGatewayState(@Nonnull UUID worldUuid) {
        Objects.requireNonNull(worldUuid, "worldUuid cannot be null");

        try (Connection conn = dataManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(DELETE_BY_WORLD)) {

            stmt.setString(1, worldUuid.toString());
            int affected = stmt.executeUpdate();
            return affected > 0;

        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to remove gateway state for world %s",
                worldUuid.toString().substring(0, 8));
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
