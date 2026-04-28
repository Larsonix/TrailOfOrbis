package io.github.larsonix.trailoforbis.maps.gateway;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.database.DataManager;
import io.github.larsonix.trailoforbis.database.DatabaseType;

import javax.annotation.Nonnull;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Repository for per-block gateway tier persistence.
 *
 * <p>Stores the upgrade tier of each Portal_Device block in the database,
 * keyed by (world_uuid, block_x, block_y, block_z). Includes an in-memory
 * cache for fast lookups during gameplay.
 *
 * <p>Follows the same pattern as {@link io.github.larsonix.trailoforbis.database.repository.SpawnGatewayRepository}.
 */
public class GatewayTierRepository {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // SQL Queries
    private static final String SELECT_TIER =
        "SELECT tier FROM rpg_gateway_tiers WHERE world_uuid = ? AND block_x = ? AND block_y = ? AND block_z = ?";

    private static final String SELECT_ALL_FOR_WORLD =
        "SELECT block_x, block_y, block_z, tier FROM rpg_gateway_tiers WHERE world_uuid = ?";

    private static final String UPSERT_MYSQL =
        "INSERT INTO rpg_gateway_tiers (world_uuid, block_x, block_y, block_z, tier, updated_at) " +
        "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP) " +
        "ON DUPLICATE KEY UPDATE tier = VALUES(tier), updated_at = CURRENT_TIMESTAMP";

    private static final String UPSERT_H2 =
        "MERGE INTO rpg_gateway_tiers (world_uuid, block_x, block_y, block_z, tier, updated_at) " +
        "KEY(world_uuid, block_x, block_y, block_z) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";

    private static final String UPSERT_POSTGRESQL =
        "INSERT INTO rpg_gateway_tiers (world_uuid, block_x, block_y, block_z, tier, updated_at) " +
        "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP) " +
        "ON CONFLICT (world_uuid, block_x, block_y, block_z) DO UPDATE SET tier = EXCLUDED.tier, updated_at = CURRENT_TIMESTAMP";

    private static final String DELETE_TIER =
        "DELETE FROM rpg_gateway_tiers WHERE world_uuid = ? AND block_x = ? AND block_y = ? AND block_z = ?";

    private final DataManager dataManager;

    /**
     * In-memory cache: "worldUuid:x:y:z" → tier index.
     * Populated on first world load, updated on upgrade.
     */
    private final Map<String, Integer> cache = new ConcurrentHashMap<>();

    public GatewayTierRepository(@Nonnull DataManager dataManager) {
        this.dataManager = Objects.requireNonNull(dataManager, "dataManager cannot be null");
    }

    // ═══════════════════════════════════════════════════════════════════
    // CACHE KEY
    // ═══════════════════════════════════════════════════════════════════

    private static String cacheKey(@Nonnull UUID worldUuid, int x, int y, int z) {
        return worldUuid.toString() + ":" + x + ":" + y + ":" + z;
    }

    // ═══════════════════════════════════════════════════════════════════
    // READ
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the tier of a gateway block. Checks cache first, falls back to DB.
     *
     * @return Tier index (0 = base), or 0 if not found
     */
    public int getTier(@Nonnull UUID worldUuid, int x, int y, int z) {
        String key = cacheKey(worldUuid, x, y, z);
        Integer cached = cache.get(key);
        if (cached != null) {
            return cached;
        }

        // DB lookup
        try (Connection conn = dataManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_TIER)) {

            stmt.setString(1, worldUuid.toString());
            stmt.setInt(2, x);
            stmt.setInt(3, y);
            stmt.setInt(4, z);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int tier = rs.getInt("tier");
                    cache.put(key, tier);
                    return tier;
                }
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to get gateway tier at (%d,%d,%d)", x, y, z);
        }

        return 0;
    }

    /**
     * Checks if a block position is a registered gateway.
     */
    public boolean isGateway(@Nonnull UUID worldUuid, int x, int y, int z) {
        return cache.containsKey(cacheKey(worldUuid, x, y, z));
    }

    /**
     * Returns all cached gateway positions for a world.
     * Each position is an int[3] of {x, y, z}.
     * Used by the guide system for proximity detection.
     */
    @Nonnull
    public java.util.List<int[]> getCachedPositionsForWorld(@Nonnull UUID worldUuid) {
        String prefix = worldUuid.toString() + ":";
        java.util.List<int[]> positions = new java.util.ArrayList<>();
        for (String key : cache.keySet()) {
            if (key.startsWith(prefix)) {
                String[] parts = key.substring(prefix.length()).split(":");
                if (parts.length == 3) {
                    try {
                        positions.add(new int[] {
                            Integer.parseInt(parts[0]),
                            Integer.parseInt(parts[1]),
                            Integer.parseInt(parts[2])
                        });
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return positions;
    }

    /**
     * Loads all gateways for a world into the cache.
     * Call this when a world loads or when spawn gateways are placed.
     */
    public void loadWorldGateways(@Nonnull UUID worldUuid) {
        try (Connection conn = dataManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_ALL_FOR_WORLD)) {

            stmt.setString(1, worldUuid.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                int count = 0;
                while (rs.next()) {
                    int x = rs.getInt("block_x");
                    int y = rs.getInt("block_y");
                    int z = rs.getInt("block_z");
                    int tier = rs.getInt("tier");
                    cache.put(cacheKey(worldUuid, x, y, z), tier);
                    count++;
                }
                if (count > 0) {
                    LOGGER.atFine().log("Loaded %d gateway tiers for world %s",
                        count, worldUuid.toString().substring(0, 8));
                }
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load gateway tiers for world %s",
                worldUuid.toString().substring(0, 8));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // WRITE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Sets the tier of a gateway block. Updates both cache and DB.
     */
    public void setTier(@Nonnull UUID worldUuid, int x, int y, int z, int tier) {
        String key = cacheKey(worldUuid, x, y, z);
        cache.put(key, tier);

        String upsertSql = getUpsertSql();

        try (Connection conn = dataManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(upsertSql)) {

            stmt.setString(1, worldUuid.toString());
            stmt.setInt(2, x);
            stmt.setInt(3, y);
            stmt.setInt(4, z);
            stmt.setInt(5, tier);
            stmt.executeUpdate();

            LOGGER.atFine().log("Set gateway tier=%d at (%d,%d,%d) in world %s",
                tier, x, y, z, worldUuid.toString().substring(0, 8));

        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to set gateway tier at (%d,%d,%d)", x, y, z);
        }
    }

    /**
     * Registers a new gateway at tier 0 (used when spawn gateways are placed).
     */
    public void registerGateway(@Nonnull UUID worldUuid, int x, int y, int z) {
        setTier(worldUuid, x, y, z, 0);
    }

    /**
     * Removes a gateway record (e.g., if block is destroyed).
     */
    public void removeGateway(@Nonnull UUID worldUuid, int x, int y, int z) {
        cache.remove(cacheKey(worldUuid, x, y, z));

        try (Connection conn = dataManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(DELETE_TIER)) {

            stmt.setString(1, worldUuid.toString());
            stmt.setInt(2, x);
            stmt.setInt(3, y);
            stmt.setInt(4, z);
            stmt.executeUpdate();

        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to remove gateway at (%d,%d,%d)", x, y, z);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Clears the in-memory cache (call on shutdown).
     */
    public void clearCache() {
        cache.clear();
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════

    private String getUpsertSql() {
        DatabaseType type = dataManager.getDatabaseType();
        return switch (type) {
            case MYSQL -> UPSERT_MYSQL;
            case H2 -> UPSERT_H2;
            case POSTGRESQL -> UPSERT_POSTGRESQL;
        };
    }
}
