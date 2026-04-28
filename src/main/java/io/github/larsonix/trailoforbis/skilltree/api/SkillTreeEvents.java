package io.github.larsonix.trailoforbis.skilltree.api;

import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.skilltree.config.SkillNode;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;
import java.util.UUID;

/**
 * Event listener interfaces for the skill tree system.
 *
 * <p>Register listeners via {@link io.github.larsonix.trailoforbis.api.services.SkillTreeService}:
 * <pre>
 * skillTreeService.registerNodeAllocatedListener((playerId, node, pointsRemaining, statsBefore, statsAfter) -> {
 *     showAllocationEffect(playerId, node);
 * });
 * </pre>
 *
 * <p>All callbacks are invoked synchronously on the calling thread.
 */
public final class SkillTreeEvents {

    private SkillTreeEvents() {
        // Static class - prevent instantiation
    }

    /**
     * Listener for node allocation events.
     *
     * <p>Called when a player successfully allocates a skill node.
     * The before/after stat snapshots enable feedback systems to show
     * the exact impact of the allocation on computed stats.
     */
    @FunctionalInterface
    public interface NodeAllocatedListener {
        /**
         * Called when a player allocates a skill node.
         *
         * @param playerId The player's UUID
         * @param node The allocated node
         * @param pointsRemaining The player's remaining skill points after allocation
         * @param statsBefore ComputedStats snapshot before allocation (null if unavailable)
         * @param statsAfter ComputedStats snapshot after allocation (null if unavailable)
         */
        void onNodeAllocated(@Nonnull UUID playerId, @Nonnull SkillNode node, int pointsRemaining,
                             @Nullable ComputedStats statsBefore, @Nullable ComputedStats statsAfter);
    }

    /**
     * Listener for node deallocation events.
     *
     * <p>Called when a player successfully deallocates (refunds) a skill node.
     */
    @FunctionalInterface
    public interface NodeDeallocatedListener {
        /**
         * Called when a player deallocates a skill node.
         *
         * @param playerId The player's UUID
         * @param node The deallocated node
         * @param pointsRefunded The number of skill points refunded (equals node cost)
         * @param pointsRemaining The player's remaining skill points after refund
         * @param refundPointsRemaining The player's remaining refund points after consuming one
         * @param statsBefore ComputedStats snapshot before deallocation (null if unavailable)
         * @param statsAfter ComputedStats snapshot after deallocation (null if unavailable)
         */
        void onNodeDeallocated(@Nonnull UUID playerId, @Nonnull SkillNode node, int pointsRefunded,
                               int pointsRemaining, int refundPointsRemaining,
                               @Nullable ComputedStats statsBefore, @Nullable ComputedStats statsAfter);
    }

    /**
     * Listener for full respec events.
     *
     * <p>Called when a player performs a full respec, deallocating all nodes.
     */
    @FunctionalInterface
    public interface RespecListener {
        /**
         * Called when a player performs a full respec.
         *
         * @param playerId The player's UUID
         * @param nodesCleared The set of node IDs that were deallocated
         * @param pointsRefunded The total number of points refunded
         * @param freeRespecsRemaining The number of free respecs remaining after this one
         */
        void onRespec(@Nonnull UUID playerId, @Nonnull Set<String> nodesCleared, int pointsRefunded, int freeRespecsRemaining);
    }
}
