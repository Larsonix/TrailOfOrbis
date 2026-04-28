package io.github.larsonix.trailoforbis.skilltree.model;

import io.github.larsonix.trailoforbis.skilltree.SkillTreeManager;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.util.*;

/**
 * Immutable player skill tree state, persisted to rpg_skill_tree table.
 * Uses builder pattern for immutable updates.
 */
public final class SkillTreeData {
    private final UUID uuid;
    private final Set<String> allocatedNodes;
    private final int skillPoints;
    private final int totalPointsEarned;
    private final int respecs;
    private final int skillRefundPoints;
    private final Instant createdAt;
    private final Instant lastModified;

    private SkillTreeData(Builder builder) {
        this.uuid = Objects.requireNonNull(builder.uuid, "uuid cannot be null");
        this.allocatedNodes = Collections.unmodifiableSet(new HashSet<>(builder.allocatedNodes));
        this.skillPoints = builder.skillPoints;
        this.totalPointsEarned = builder.totalPointsEarned;
        this.respecs = builder.respecs;
        this.skillRefundPoints = builder.skillRefundPoints;
        this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
        this.lastModified = builder.lastModified != null ? builder.lastModified : Instant.now();
    }

    // Getters
    @Nonnull
    public UUID getUuid() {
        return uuid;
    }

    @Nonnull
    public Set<String> getAllocatedNodes() {
        return allocatedNodes;
    }

    public int getSkillPoints() {
        return skillPoints;
    }

    public int getTotalPointsEarned() {
        return totalPointsEarned;
    }

    public int getRespecs() {
        return respecs;
    }

    public int getSkillRefundPoints() {
        return skillRefundPoints;
    }

    @Nonnull
    public Instant getCreatedAt() {
        return createdAt;
    }

    @Nonnull
    public Instant getLastModified() {
        return lastModified;
    }

    // Immutable update methods

    /**
     * Returns a new SkillTreeData with the node allocated and skill points decremented by the node's cost.
     *
     * @param nodeId The node to allocate
     * @param cost The skill point cost of this node (type-based: Basic=1, Notable=2, Keystone=3)
     */
    public SkillTreeData withAllocatedNode(String nodeId, int cost) {
        Set<String> newNodes = new HashSet<>(allocatedNodes);
        newNodes.add(nodeId);
        return toBuilder()
            .allocatedNodes(newNodes)
            .skillPoints(skillPoints - cost)
            .lastModified(Instant.now())
            .build();
    }

    /**
     * Returns a new SkillTreeData with the node removed (no point refund - use deallocate for that).
     */
    public SkillTreeData withRemovedNode(String nodeId) {
        Set<String> newNodes = new HashSet<>(allocatedNodes);
        newNodes.remove(nodeId);
        return toBuilder()
            .allocatedNodes(newNodes)
            .lastModified(Instant.now())
            .build();
    }

    /**
     * Returns a new SkillTreeData with the specified skill points.
     */
    public SkillTreeData withSkillPoints(int points) {
        return toBuilder()
            .skillPoints(points)
            .lastModified(Instant.now())
            .build();
    }

    /**
     * Returns a new SkillTreeData with additional skill points added.
     * Also increments totalPointsEarned.
     */
    public SkillTreeData withAddedSkillPoints(int points) {
        return toBuilder()
            .skillPoints(skillPoints + points)
            .totalPointsEarned(totalPointsEarned + points)
            .lastModified(Instant.now())
            .build();
    }

    /**
     * Returns a new SkillTreeData with a full respec applied:
     * - Clears all allocated nodes EXCEPT origin (which is always allocated)
     * - Refunds all totalPointsEarned as skillPoints
     * - Increments respecs counter
     *
     * <p>Origin is preserved because it has no cost, provides no modifiers, and is
     * required as the starting point for the skill tree graph. Clearing it would
     * break adjacency calculations, making all nodes LOCKED.
     */
    public SkillTreeData withRespec() {
        return toBuilder()
            .allocatedNodes(Set.of(SkillTreeManager.ORIGIN_NODE_ID))  // Preserve origin - it's the graph root
            .skillPoints(totalPointsEarned)
            .respecs(respecs + 1)
            .lastModified(Instant.now())
            .build();
    }

    public Builder toBuilder() {
        return new Builder()
            .uuid(uuid)
            .allocatedNodes(allocatedNodes)
            .skillPoints(skillPoints)
            .totalPointsEarned(totalPointsEarned)
            .respecs(respecs)
            .skillRefundPoints(skillRefundPoints)
            .createdAt(createdAt)
            .lastModified(lastModified);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private UUID uuid;
        private Set<String> allocatedNodes = new HashSet<>();
        private int skillPoints = 0;
        private int totalPointsEarned = 0;
        private int respecs = 0;
        private int skillRefundPoints = 10;
        private Instant createdAt;
        private Instant lastModified;

        public Builder uuid(UUID uuid) {
            this.uuid = uuid;
            return this;
        }

        public Builder allocatedNodes(Set<String> nodes) {
            this.allocatedNodes = new HashSet<>(nodes);
            return this;
        }

        public Builder skillPoints(int points) {
            this.skillPoints = points;
            return this;
        }

        public Builder totalPointsEarned(int points) {
            this.totalPointsEarned = points;
            return this;
        }

        public Builder respecs(int respecs) {
            this.respecs = respecs;
            return this;
        }

        public Builder skillRefundPoints(int skillRefundPoints) {
            this.skillRefundPoints = skillRefundPoints;
            return this;
        }

        public Builder createdAt(Instant instant) {
            this.createdAt = instant;
            return this;
        }

        public Builder lastModified(Instant instant) {
            this.lastModified = instant;
            return this;
        }

        public SkillTreeData build() {
            return new SkillTreeData(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SkillTreeData that)) return false;
        return uuid.equals(that.uuid);
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }

    @Override
    public String toString() {
        return String.format("SkillTreeData{uuid=%s, nodes=%d, points=%d}",
            uuid, allocatedNodes.size(), skillPoints);
    }
}
