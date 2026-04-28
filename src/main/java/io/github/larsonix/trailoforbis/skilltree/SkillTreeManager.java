package io.github.larsonix.trailoforbis.skilltree;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.api.services.AttributeService;
import io.github.larsonix.trailoforbis.api.services.SkillTreeService;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.config.ConfigManager;
import io.github.larsonix.trailoforbis.skilltree.api.SkillTreeEventDispatcher;
import io.github.larsonix.trailoforbis.skilltree.api.SkillTreeEvents;
import io.github.larsonix.trailoforbis.skilltree.config.SkillNode;
import io.github.larsonix.trailoforbis.skilltree.config.SkillTreeConfig;
import io.github.larsonix.trailoforbis.skilltree.config.SkillTreeSettings;
import io.github.larsonix.trailoforbis.skilltree.model.SkillTreeData;
import io.github.larsonix.trailoforbis.skilltree.repository.SkillTreeRepository;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.logging.Level;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core implementation of skill tree logic.
 *
 * <p>Handles allocation/deallocation validation, point management,
 * and coordinates with the repository for persistence.
 */
public class SkillTreeManager implements SkillTreeService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /**
     * The ID of the origin node - the immutable root of the skill tree.
     * This node is always pre-allocated for new players and cannot be deallocated.
     * Removing it would break BFS traversal and adjacency calculations.
     */
    public static final String ORIGIN_NODE_ID = "origin";

    private final SkillTreeRepository repository;
    private final ConfigManager configManager;
    private final SkillTreeEventDispatcher eventDispatcher;
    private SkillTreeConfig treeConfig;

    // Per-player locks to prevent race conditions during allocation
    private final Map<UUID, Object> playerLocks = new ConcurrentHashMap<>();

    /**
     * Creates a new SkillTreeManager.
     *
     * @param repository The skill tree repository for persistence
     * @param configManager The config manager for settings
     */
    public SkillTreeManager(@Nonnull SkillTreeRepository repository,
                            @Nonnull ConfigManager configManager) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.configManager = Objects.requireNonNull(configManager, "configManager cannot be null");
        this.eventDispatcher = new SkillTreeEventDispatcher();
    }

    /**
     * Loads and validates the skill tree configuration.
     *
     * @param config The skill tree configuration to load
     * @throws IllegalArgumentException if config validation fails
     */
    public void loadConfig(@Nonnull SkillTreeConfig config) {
        List<String> errors = config.validate();
        if (!errors.isEmpty()) {
            LOGGER.at(Level.SEVERE).log("Skill tree config validation failed:");
            errors.forEach(e -> LOGGER.at(Level.SEVERE).log("  - %s", e));
            throw new IllegalArgumentException("Invalid skill tree configuration");
        }
        this.treeConfig = config;
        LOGGER.at(Level.INFO).log("Loaded skill tree with %d nodes", config.getNodeCount());
    }

    /**
     * Gets the loaded skill tree configuration.
     *
     * @return The skill tree configuration, or null if not loaded
     */
    public SkillTreeConfig getTreeConfig() {
        return treeConfig;
    }

    @Override
    @Nonnull
    public SkillTreeData getSkillTreeData(@Nonnull UUID playerId) {
        int startingPoints = getSettings().getStartingPoints();
        return repository.getOrCreate(playerId, startingPoints);
    }

    /**
     * Saves skill tree data directly. Used by refund stone handlers.
     */
    public void saveData(@Nonnull SkillTreeData data) {
        repository.save(data);
    }

    @Override
    public boolean allocateNode(@Nonnull UUID playerId, @Nonnull String nodeId) {
        synchronized (getLock(playerId)) {
            if (!canAllocate(playerId, nodeId)) {
                return false;
            }

            SkillTreeData data = getSkillTreeData(playerId);
            SkillNode node = treeConfig.getNode(nodeId);
            int cost = node != null ? node.getCost() : 1;
            SkillTreeData updated = data.withAllocatedNode(nodeId, cost);
            repository.save(updated);

            // Capture stat snapshot BEFORE recalculation
            ComputedStats statsBefore = getStatSnapshot(playerId);

            // CRITICAL: Invalidate player stats to trigger recalculation with new modifiers
            invalidatePlayerStats(playerId);

            // Capture stat snapshot AFTER recalculation
            ComputedStats statsAfter = getStatSnapshot(playerId);

            // Fire event for other systems to react
            if (node != null) {
                eventDispatcher.dispatchNodeAllocated(playerId, node, updated.getSkillPoints(),
                        statsBefore, statsAfter);
            }

            return true;
        }
    }

    @Override
    public boolean deallocateNode(@Nonnull UUID playerId, @Nonnull String nodeId) {
        synchronized (getLock(playerId)) {
            if (!canDeallocate(playerId, nodeId)) {
                return false;
            }

            SkillTreeData data = getSkillTreeData(playerId);

            // Deallocation costs 1 skill refund point
            if (data.getSkillRefundPoints() <= 0) {
                return false;
            }

            SkillNode node = treeConfig.getNode(nodeId);
            int refundPoints = node != null ? node.getCost() : 0;

            SkillTreeData updated = data.withRemovedNode(nodeId)
                .toBuilder()
                .skillPoints(data.getSkillPoints() + refundPoints)
                .skillRefundPoints(data.getSkillRefundPoints() - 1)
                .build();
            repository.save(updated);

            // Capture stat snapshot BEFORE recalculation
            ComputedStats statsBefore = getStatSnapshot(playerId);

            // CRITICAL: Invalidate player stats to trigger recalculation with removed modifiers
            invalidatePlayerStats(playerId);

            // Capture stat snapshot AFTER recalculation
            ComputedStats statsAfter = getStatSnapshot(playerId);

            // Fire event for other systems to react
            if (node != null) {
                eventDispatcher.dispatchNodeDeallocated(playerId, node, refundPoints,
                        updated.getSkillPoints(), updated.getSkillRefundPoints(),
                        statsBefore, statsAfter);
            }

            return true;
        }
    }

    /**
     * Invalidates a player's stats cache, forcing recalculation on next access.
     *
     * <p>This is called after skill tree modifications to ensure ComputedStats
     * reflects the new skill tree modifiers immediately.
     *
     * <p>The recalculation happens atomically under the player's lock in AttributeManager.
     *
     * @param playerId The player's UUID
     * @throws IllegalStateException if AttributeService is not available
     */
    private void invalidatePlayerStats(UUID playerId) {
        // AttributeService is required - if unavailable, skill tree changes cannot take effect
        // and the player would spend points without seeing any stat changes.
        AttributeService service = ServiceRegistry.require(AttributeService.class);
        service.recalculateStats(playerId);
        LOGGER.at(Level.FINE).log("Recalculated stats for %s after skill tree modification", playerId);
    }

    /**
     * Takes a snapshot of the player's current computed stats.
     *
     * <p>Returns a deep copy so the snapshot is unaffected by later recalculations.
     * Falls back to null if stats are unavailable (e.g., player offline).
     *
     * @param playerId The player's UUID
     * @return A copy of the player's ComputedStats, or null
     */
    @javax.annotation.Nullable
    private ComputedStats getStatSnapshot(UUID playerId) {
        try {
            AttributeService service = ServiceRegistry.require(AttributeService.class);
            ComputedStats stats = service.getStats(playerId);
            return stats != null ? stats.copy() : null;
        } catch (Exception e) {
            LOGGER.at(Level.FINE).log("Could not snapshot stats for %s: %s", playerId, e.getMessage());
            return null;
        }
    }

    /**
     * Gets the total refund cost for a full respec (sum of all allocated node costs).
     */
    public int calculateFullRespecCost(@Nonnull UUID playerId) {
        SkillTreeData data = getSkillTreeData(playerId);
        int totalCost = 0;
        for (String nodeId : data.getAllocatedNodes()) {
            if (ORIGIN_NODE_ID.equals(nodeId)) continue;
            SkillNode node = treeConfig != null ? treeConfig.getNode(nodeId) : null;
            totalCost += node != null ? node.getCost() : 1;
        }
        return totalCost;
    }

    @Override
    public boolean fullRespec(@Nonnull UUID playerId) {
        synchronized (getLock(playerId)) {
            SkillTreeData data = getSkillTreeData(playerId);

            int freeRespecs = getSettings().getFreeRespecs();
            boolean isFree = data.getRespecs() < freeRespecs;

            if (!isFree) {
                // Paid respec: costs 50% of total allocated node costs in refund points
                int totalNodeCosts = calculateFullRespecCost(playerId);
                int respecCost = (int) Math.ceil(totalNodeCosts * 0.5);
                if (data.getSkillRefundPoints() < respecCost) {
                    return false; // Not enough refund points
                }
                // Deduct refund points
                data = data.toBuilder()
                    .skillRefundPoints(data.getSkillRefundPoints() - respecCost)
                    .build();
            }

            // Capture data before respec for event — exclude origin since it's preserved
            Set<String> clearedNodes = new HashSet<>(data.getAllocatedNodes());
            clearedNodes.remove(ORIGIN_NODE_ID);
            int pointsBeforeRespec = data.getSkillPoints();

            SkillTreeData updated = data.withRespec();
            if (!isFree) {
                // Preserve the deducted refund points (withRespec doesn't touch them)
                updated = updated.toBuilder()
                    .skillRefundPoints(data.getSkillRefundPoints())
                    .build();
            }

            // Validate post-respec consistency: skillPoints MUST equal totalPointsEarned
            // (all points refunded, only origin allocated at cost 0)
            if (updated.getSkillPoints() != updated.getTotalPointsEarned()) {
                LOGGER.at(Level.SEVERE).log(
                    "POST-RESPEC INCONSISTENCY for %s: skillPoints=%d but totalPointsEarned=%d. Force-correcting.",
                    playerId, updated.getSkillPoints(), updated.getTotalPointsEarned());
                updated = updated.toBuilder()
                    .skillPoints(updated.getTotalPointsEarned())
                    .build();
            }

            repository.save(updated);

            // CRITICAL: Invalidate player stats to trigger recalculation with cleared modifiers
            invalidatePlayerStats(playerId);

            // Fire event for other systems to react
            int pointsRefunded = updated.getSkillPoints() - pointsBeforeRespec;
            int freeRespecsRemaining = Math.max(0, freeRespecs - updated.getRespecs());
            eventDispatcher.dispatchRespec(playerId, clearedNodes, pointsRefunded, freeRespecsRemaining);

            return true;
        }
    }

    @Override
    public boolean canAllocate(@Nonnull UUID playerId, @Nonnull String nodeId) {
        if (treeConfig == null) return false;

        SkillTreeData data = getSkillTreeData(playerId);
        SkillNode node = treeConfig.getNode(nodeId);

        // Node must exist
        if (node == null) return false;

        // Cannot allocate if already allocated
        if (data.getAllocatedNodes().contains(nodeId)) return false;

        // Must have enough skill points for this node's cost
        if (data.getSkillPoints() < node.getCost()) return false;

        // If no nodes allocated, must be start node
        if (data.getAllocatedNodes().isEmpty()) {
            return node.isStartNode();
        }

        // Must be adjacent to an allocated node
        return node.getConnections().stream()
            .anyMatch(data.getAllocatedNodes()::contains);
    }

    @Override
    public boolean canDeallocate(@Nonnull UUID playerId, @Nonnull String nodeId) {
        // Origin node can NEVER be deallocated - it's the immutable graph root
        if (ORIGIN_NODE_ID.equals(nodeId)) {
            return false;
        }

        if (treeConfig == null) return false;

        SkillTreeData data = getSkillTreeData(playerId);

        // Must be allocated
        if (!data.getAllocatedNodes().contains(nodeId)) return false;

        // Simulate removal and check connectivity
        Set<String> remaining = new HashSet<>(data.getAllocatedNodes());
        remaining.remove(nodeId);

        // If removing last node, it's OK
        if (remaining.isEmpty()) return true;

        // BFS from any start node to verify all remaining nodes are reachable
        Set<String> reachable = bfsFromStarts(remaining);
        return reachable.equals(remaining);
    }

    /**
     * BFS traversal from start nodes to find all reachable allocated nodes.
     */
    private Set<String> bfsFromStarts(Set<String> allocated) {
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();

        // Find start nodes in allocated set
        for (String nodeId : allocated) {
            SkillNode node = treeConfig.getNode(nodeId);
            if (node != null && node.isStartNode()) {
                queue.offer(nodeId);
                visited.add(nodeId);
            }
        }

        // If no start nodes in allocated, find any node adjacent to a start
        if (queue.isEmpty() && !allocated.isEmpty()) {
            for (String nodeId : allocated) {
                SkillNode node = treeConfig.getNode(nodeId);
                if (node != null) {
                    for (String conn : node.getConnections()) {
                        SkillNode connNode = treeConfig.getNode(conn);
                        if (connNode != null && connNode.isStartNode()) {
                            queue.offer(nodeId);
                            visited.add(nodeId);
                            break;
                        }
                    }
                }
                if (!queue.isEmpty()) break;
            }
        }

        // BFS traversal
        while (!queue.isEmpty()) {
            String current = queue.poll();
            SkillNode node = treeConfig.getNode(current);
            if (node == null) continue;

            for (String neighbor : node.getConnections()) {
                if (allocated.contains(neighbor) && !visited.contains(neighbor)) {
                    visited.add(neighbor);
                    queue.offer(neighbor);
                }
            }
        }

        return visited;
    }

    @Override
    public int getAvailablePoints(@Nonnull UUID playerId) {
        return getSkillTreeData(playerId).getSkillPoints();
    }

    @Override
    @Nonnull
    public Set<String> getAllocatedNodes(@Nonnull UUID playerId) {
        return getSkillTreeData(playerId).getAllocatedNodes();
    }

    @Override
    @Nonnull
    public Optional<SkillNode> getNode(@Nonnull String nodeId) {
        if (treeConfig == null) return Optional.empty();
        return Optional.ofNullable(treeConfig.getNode(nodeId));
    }

    @Override
    @Nonnull
    public Collection<SkillNode> getAllNodes() {
        if (treeConfig == null) return Collections.emptyList();
        return treeConfig.getNodes().values();
    }

    @Override
    public void grantSkillPoints(@Nonnull UUID playerId, int points) {
        synchronized (getLock(playerId)) {
            SkillTreeData data = getSkillTreeData(playerId);
            SkillTreeData updated = data.withAddedSkillPoints(points);
            repository.save(updated);
        }
    }

    @Override
    public int getFreeRespecsRemaining(@Nonnull UUID playerId) {
        SkillTreeData data = getSkillTreeData(playerId);
        int freeRespecs = getSettings().getFreeRespecs();
        return Math.max(0, freeRespecs - data.getRespecs());
    }

    @Override
    public void saveAll() {
        repository.saveAll();
    }

    /**
     * Gets the per-player lock for thread safety.
     */
    private Object getLock(UUID playerId) {
        return playerLocks.computeIfAbsent(playerId, k -> new Object());
    }

    /**
     * Gets the skill tree settings from config.
     */
    private SkillTreeSettings getSettings() {
        return configManager.getRPGConfig().getSkillTree();
    }

    // ==================== Admin Methods ====================

    @Override
    public void setSkillPoints(@Nonnull UUID playerId, int points) {
        synchronized (getLock(playerId)) {
            SkillTreeData data = getSkillTreeData(playerId);
            int clampedPoints = Math.max(0, points);

            // Maintain consistency: totalPointsEarned = skillPoints + sum of allocated node costs
            // Without this, the HUD and other systems see an impossible state where
            // totalPointsEarned implies points are allocated that don't exist as nodes.
            int allocatedCost = calculateFullRespecCost(playerId);
            int newTotal = clampedPoints + allocatedCost;

            SkillTreeData updated = data.toBuilder()
                .skillPoints(clampedPoints)
                .totalPointsEarned(newTotal)
                .build();
            repository.save(updated);
        }
    }

    @Override
    public boolean removeSkillPoints(@Nonnull UUID playerId, int points) {
        synchronized (getLock(playerId)) {
            SkillTreeData data = getSkillTreeData(playerId);
            if (data.getSkillPoints() < points) {
                return false;
            }
            SkillTreeData updated = data.withSkillPoints(data.getSkillPoints() - points);
            repository.save(updated);
            return true;
        }
    }

    @Override
    public void adminReset(@Nonnull UUID playerId) {
        synchronized (getLock(playerId)) {
            SkillTreeData data = getSkillTreeData(playerId);

            // Capture data before reset for event — exclude origin since it's preserved
            Set<String> clearedNodes = new HashSet<>(data.getAllocatedNodes());
            clearedNodes.remove(ORIGIN_NODE_ID);
            int pointsBeforeReset = data.getSkillPoints();

            // Use withRespec but don't increment respec counter for admin resets
            // Preserve origin - it's the graph root with no cost/modifiers
            int refundedPoints = data.getTotalPointsEarned();
            SkillTreeData updated = data.toBuilder()
                .allocatedNodes(Set.of(ORIGIN_NODE_ID))
                .skillPoints(refundedPoints)
                .build();

            // Validate consistency
            if (updated.getSkillPoints() != updated.getTotalPointsEarned()) {
                LOGGER.at(Level.SEVERE).log(
                    "POST-ADMIN-RESET INCONSISTENCY for %s: skillPoints=%d but totalPointsEarned=%d. Force-correcting.",
                    playerId, updated.getSkillPoints(), updated.getTotalPointsEarned());
                updated = updated.toBuilder()
                    .skillPoints(updated.getTotalPointsEarned())
                    .build();
            }

            repository.save(updated);

            // Invalidate stats to trigger recalculation
            invalidatePlayerStats(playerId);

            // Fire respec event for other systems to react (e.g., sanctum visual update)
            int pointsRefunded = updated.getSkillPoints() - pointsBeforeReset;
            eventDispatcher.dispatchRespec(playerId, clearedNodes, pointsRefunded, -1); // -1 = admin reset (unlimited)
        }
    }

    @Override
    public boolean adminAllocateNode(@Nonnull UUID playerId, @Nonnull String nodeId) {
        if (treeConfig == null) return false;
        SkillNode node = treeConfig.getNode(nodeId);
        if (node == null) return false;

        synchronized (getLock(playerId)) {
            SkillTreeData data = getSkillTreeData(playerId);

            // Skip if already allocated
            if (data.getAllocatedNodes().contains(nodeId)) {
                return true; // Already allocated, not an error
            }

            // Don't deduct points - admin force allocation
            Set<String> newNodes = new HashSet<>(data.getAllocatedNodes());
            newNodes.add(nodeId);
            SkillTreeData updated = data.toBuilder()
                .allocatedNodes(newNodes)
                .build();
            repository.save(updated);

            // Capture stat snapshot BEFORE recalculation
            ComputedStats statsBefore = getStatSnapshot(playerId);

            // Invalidate stats to trigger recalculation
            invalidatePlayerStats(playerId);

            // Capture stat snapshot AFTER recalculation
            ComputedStats statsAfter = getStatSnapshot(playerId);

            // Fire event for other systems to react (e.g., sanctum visual update)
            eventDispatcher.dispatchNodeAllocated(playerId, node, updated.getSkillPoints(),
                    statsBefore, statsAfter);

            return true;
        }
    }

    @Override
    public boolean adminDeallocateNode(@Nonnull UUID playerId, @Nonnull String nodeId, boolean refundPoint) {
        // Origin node can NEVER be deallocated - even by admins
        if (ORIGIN_NODE_ID.equals(nodeId)) {
            LOGGER.at(Level.WARNING).log("Attempted to deallocate origin node for %s - denied", playerId);
            return false;
        }

        synchronized (getLock(playerId)) {
            SkillTreeData data = getSkillTreeData(playerId);
            if (!data.getAllocatedNodes().contains(nodeId)) {
                return false;
            }

            SkillNode node = treeConfig.getNode(nodeId);
            int refund = (refundPoint && node != null) ? node.getCost() : 0;

            Set<String> newNodes = new HashSet<>(data.getAllocatedNodes());
            newNodes.remove(nodeId);
            SkillTreeData.Builder builder = data.toBuilder()
                .allocatedNodes(newNodes);

            if (refundPoint) {
                builder.skillPoints(data.getSkillPoints() + refund);
            }

            SkillTreeData updated = builder.build();
            repository.save(updated);

            // Capture stat snapshot BEFORE recalculation
            ComputedStats statsBefore = getStatSnapshot(playerId);

            // Invalidate stats to trigger recalculation
            invalidatePlayerStats(playerId);

            // Capture stat snapshot AFTER recalculation
            ComputedStats statsAfter = getStatSnapshot(playerId);

            // Fire event for other systems to react (e.g., sanctum visual update)
            if (node != null) {
                eventDispatcher.dispatchNodeDeallocated(playerId, node, refund,
                        updated.getSkillPoints(), updated.getSkillRefundPoints(),
                        statsBefore, statsAfter);
            }

            return true;
        }
    }

    // ==================== Lifecycle Methods ====================

    @Override
    public void cleanupPlayer(@Nonnull UUID playerId) {
        Objects.requireNonNull(playerId, "playerId cannot be null");
        playerLocks.remove(playerId);
    }

    // ==================== Event Listener Registration ====================

    @Override
    public void registerNodeAllocatedListener(@Nonnull SkillTreeEvents.NodeAllocatedListener listener) {
        eventDispatcher.registerNodeAllocatedListener(listener);
    }

    @Override
    public void registerNodeDeallocatedListener(@Nonnull SkillTreeEvents.NodeDeallocatedListener listener) {
        eventDispatcher.registerNodeDeallocatedListener(listener);
    }

    @Override
    public void registerRespecListener(@Nonnull SkillTreeEvents.RespecListener listener) {
        eventDispatcher.registerRespecListener(listener);
    }

    @Override
    public void unregisterNodeAllocatedListener(@Nonnull SkillTreeEvents.NodeAllocatedListener listener) {
        eventDispatcher.unregisterNodeAllocatedListener(listener);
    }

    @Override
    public void unregisterNodeDeallocatedListener(@Nonnull SkillTreeEvents.NodeDeallocatedListener listener) {
        eventDispatcher.unregisterNodeDeallocatedListener(listener);
    }

    @Override
    public void unregisterRespecListener(@Nonnull SkillTreeEvents.RespecListener listener) {
        eventDispatcher.unregisterRespecListener(listener);
    }
}
