package io.github.larsonix.trailoforbis.api.services;

import io.github.larsonix.trailoforbis.skilltree.api.SkillTreeEvents;
import io.github.larsonix.trailoforbis.skilltree.config.SkillNode;
import io.github.larsonix.trailoforbis.skilltree.model.SkillTreeData;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Service interface for skill tree operations.
 *
 * <p>Provides decoupled access to skill tree allocation, validation, and data
 * without requiring direct dependency on the main plugin class.
 */
public interface SkillTreeService {

    /** Gets or creates the player's skill tree data. */
    @Nonnull
    SkillTreeData getSkillTreeData(@Nonnull UUID playerId);

    /** @return false if validation failed */
    boolean allocateNode(@Nonnull UUID playerId, @Nonnull String nodeId);

    /** @return false if would orphan other nodes */
    boolean deallocateNode(@Nonnull UUID playerId, @Nonnull String nodeId);

    /** @return false if no free respecs remaining */
    boolean fullRespec(@Nonnull UUID playerId);

    boolean canAllocate(@Nonnull UUID playerId, @Nonnull String nodeId);

    boolean canDeallocate(@Nonnull UUID playerId, @Nonnull String nodeId);

    int getAvailablePoints(@Nonnull UUID playerId);

    @Nonnull
    Set<String> getAllocatedNodes(@Nonnull UUID playerId);

    /** @return empty if not found */
    @Nonnull
    Optional<SkillNode> getNode(@Nonnull String nodeId);

    @Nonnull
    Collection<SkillNode> getAllNodes();

    /** Called on level up. */
    void grantSkillPoints(@Nonnull UUID playerId, int points);

    int getFreeRespecsRemaining(@Nonnull UUID playerId);

    void saveAll();

    // ==================== Admin Methods ====================

    void setSkillPoints(@Nonnull UUID playerId, int points);

    /** @return false if player doesn't have enough points */
    boolean removeSkillPoints(@Nonnull UUID playerId, int points);

    /** Bypasses free respec limit. */
    void adminReset(@Nonnull UUID playerId);

    /** Bypasses point check and adjacency. */
    boolean adminAllocateNode(@Nonnull UUID playerId, @Nonnull String nodeId);

    /**
     * Bypasses connectivity check.
     *
     * @param refundPoint whether to refund the point
     */
    boolean adminDeallocateNode(@Nonnull UUID playerId, @Nonnull String nodeId, boolean refundPoint);

    // ==================== Lifecycle Methods ====================

    /** Removes per-player lock to prevent memory leaks. */
    void cleanupPlayer(@Nonnull UUID playerId);

    // ==================== Event Listeners ====================

    void registerNodeAllocatedListener(@Nonnull SkillTreeEvents.NodeAllocatedListener listener);

    void registerNodeDeallocatedListener(@Nonnull SkillTreeEvents.NodeDeallocatedListener listener);

    void registerRespecListener(@Nonnull SkillTreeEvents.RespecListener listener);

    void unregisterNodeAllocatedListener(@Nonnull SkillTreeEvents.NodeAllocatedListener listener);

    void unregisterNodeDeallocatedListener(@Nonnull SkillTreeEvents.NodeDeallocatedListener listener);

    void unregisterRespecListener(@Nonnull SkillTreeEvents.RespecListener listener);
}
