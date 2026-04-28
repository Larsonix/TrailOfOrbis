package io.github.larsonix.trailoforbis.api.services;

import io.github.larsonix.trailoforbis.attributes.AttributeType;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.attributes.breakdown.StatBreakdownResult;
import io.github.larsonix.trailoforbis.database.repository.PlayerDataRepository;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;

/**
 * Service interface for attribute management operations.
 *
 * <p>Provides decoupled access to attribute allocation and stat calculation
 * without requiring direct dependency on the main plugin class.
 */
public interface AttributeService {

    /**
     * Allocates 1 point to an attribute for a player.
     *
     * @return true if allocation succeeded, false if validation failed
     */
    boolean allocateAttribute(@Nonnull UUID playerId, @Nonnull AttributeType type);

    /** @return null if player not found */
    @Nullable
    ComputedStats getStats(@Nonnull UUID playerId);

    /**
     * Per-source contribution breakdown for each stat.
     * Computed on demand (not cached).
     *
     * @return null if player not found
     */
    @Nullable
    StatBreakdownResult getStatBreakdown(@Nonnull UUID playerId);

    /**
     * Used by the equipment validation system to check gear requirements.
     *
     * @return empty map if player not found
     */
    @Nonnull
    Map<AttributeType, Integer> getPlayerAttributes(@Nonnull UUID playerId);

    /**
     * Recalculates and caches all stats for a player.
     *
     * @return null if player not found
     */
    @Nullable
    ComputedStats recalculateStats(@Nonnull UUID playerId);

    /** Direct access to underlying player data. */
    @Nonnull
    PlayerDataRepository getPlayerDataRepository();

    // ==================== Admin Methods ====================

    /**
     * @param points must be >= 0
     * @return true if operation succeeded, false if player not found or invalid value
     */
    boolean setUnallocatedPoints(@Nonnull UUID playerId, int points);

    /**
     * Positive delta adds points, negative removes. Will not reduce below 0.
     *
     * @return false if player not found or would go negative
     */
    boolean modifyUnallocatedPoints(@Nonnull UUID playerId, int delta);

    /**
     * @param value must be >= 0
     * @return false if player not found or invalid value
     */
    boolean setAttribute(@Nonnull UUID playerId, @Nonnull AttributeType type, int value);

    /**
     * Positive delta adds, negative removes. Will not reduce below 0.
     *
     * @return false if player not found or would go negative
     */
    boolean modifyAttribute(@Nonnull UUID playerId, @Nonnull AttributeType type, int delta);

    /**
     * Resets all attributes to 0 and refunds all allocated points.
     * Costs 50% of the total allocated points in attribute refund points
     * (half what individual deallocation would cost one-by-one).
     *
     * @return the number of refunded points, -1 if player not found, -2 if not enough refund points
     */
    int resetAllAttributes(@Nonnull UUID playerId);

    /**
     * Admin override: resets all attributes to 0, bypassing refund point cost.
     *
     * @return the number of refunded points, or -1 if player not found
     */
    int resetAllAttributesAdmin(@Nonnull UUID playerId);

    // ==================== Cleanup Methods ====================

    /**
     * Removes per-player caches and locks to prevent memory leaks.
     * Call from the PlayerDisconnect event handler.
     */
    void cleanupPlayer(@Nonnull UUID playerId);

    // ==================== Refund Points ====================

    /**
     * Gets a player's current attribute refund points.
     */
    int getAttributeRefundPoints(@Nonnull UUID playerId);

    /**
     * Modifies a player's attribute refund points by a delta.
     */
    boolean modifyAttributeRefundPoints(@Nonnull UUID playerId, int delta);
}
