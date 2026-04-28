package io.github.larsonix.trailoforbis.skilltree.config;

import io.github.larsonix.trailoforbis.skilltree.SkillTreeRegion;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * Root configuration for the skill tree, loaded from skill-tree.yml.
 */
public class SkillTreeConfig {
    private Map<String, SkillNode> nodes = new HashMap<>();
    private NodeAllocationFeedbackConfig feedback = new NodeAllocationFeedbackConfig();

    // Default constructor for YAML
    public SkillTreeConfig() {
    }

    @Nonnull
    public Map<String, SkillNode> getNodes() {
        return nodes != null ? nodes : Collections.emptyMap();
    }

    @Nonnull
    public NodeAllocationFeedbackConfig getFeedback() {
        return feedback != null ? feedback : new NodeAllocationFeedbackConfig();
    }

    public void setFeedback(NodeAllocationFeedbackConfig feedback) {
        this.feedback = feedback;
    }

    public void setNodes(Map<String, SkillNode> nodes) {
        this.nodes = nodes;
        // Set id field from map key for convenience
        if (nodes != null) {
            nodes.forEach((key, node) -> {
                if (node.getId().isEmpty()) {
                    node.setId(key);
                }
            });
        }
    }

    @Nullable
    public SkillNode getNode(String id) {
        return nodes != null ? nodes.get(id) : null;
    }

    @Nonnull
    public List<SkillNode> getStartNodes() {
        if (nodes == null) return Collections.emptyList();
        return nodes.values().stream()
            .filter(SkillNode::isStartNode)
            .toList();
    }

    @Nonnull
    public List<SkillNode> getKeystones() {
        if (nodes == null) return Collections.emptyList();
        return nodes.values().stream()
            .filter(SkillNode::isKeystone)
            .toList();
    }

    @Nonnull
    public List<SkillNode> getNotables() {
        if (nodes == null) return Collections.emptyList();
        return nodes.values().stream()
            .filter(SkillNode::isNotable)
            .toList();
    }

    /**
     * Gets all nodes belonging to a specific region.
     *
     * @param region The region to filter by
     * @return List of nodes in that region
     */
    @Nonnull
    public List<SkillNode> getNodesByRegion(@Nonnull SkillTreeRegion region) {
        if (nodes == null) return Collections.emptyList();
        return nodes.values().stream()
            .filter(n -> n.getSkillTreeRegion() == region)
            .toList();
    }

    /**
     * Gets the count of nodes in a specific region.
     *
     * @param region The region to count
     * @return Number of nodes in that region
     */
    public int getNodeCountByRegion(@Nonnull SkillTreeRegion region) {
        if (nodes == null) return 0;
        return (int) nodes.values().stream()
            .filter(n -> n.getSkillTreeRegion() == region)
            .count();
    }

    /**
     * Gets a breakdown of node counts by region.
     *
     * @return Map of region to node count
     */
    @Nonnull
    public Map<SkillTreeRegion, Integer> getRegionNodeCounts() {
        Map<SkillTreeRegion, Integer> counts = new EnumMap<>(SkillTreeRegion.class);
        for (SkillTreeRegion region : SkillTreeRegion.values()) {
            counts.put(region, getNodeCountByRegion(region));
        }
        return counts;
    }

    /**
     * Gets nodes that act as bridges between regions.
     * A bridge node has connections to nodes in different regions.
     *
     * @return List of bridge nodes
     */
    @Nonnull
    public List<SkillNode> getBridgeNodes() {
        if (nodes == null) return Collections.emptyList();
        List<SkillNode> bridges = new ArrayList<>();

        for (SkillNode node : nodes.values()) {
            SkillTreeRegion nodeRegion = node.getSkillTreeRegion();
            for (String connectionId : node.getConnections()) {
                SkillNode connected = nodes.get(connectionId);
                if (connected != null && connected.getSkillTreeRegion() != nodeRegion) {
                    bridges.add(node);
                    break;  // Only add once per node
                }
            }
        }
        return bridges;
    }

    /**
     * Validates the skill tree configuration.
     *
     * @return List of validation errors (empty if valid)
     */
    @Nonnull
    public List<String> validate() {
        List<String> errors = new ArrayList<>();

        if (nodes == null || nodes.isEmpty()) {
            errors.add("No nodes defined in skill tree");
            return errors;
        }

        // Check for at least one start node
        if (getStartNodes().isEmpty()) {
            errors.add("No start nodes defined (set startNode: true)");
        }

        // Check all connections reference existing nodes
        for (SkillNode node : nodes.values()) {
            for (String connection : node.getConnections()) {
                if (!nodes.containsKey(connection)) {
                    errors.add(String.format("Node '%s' references non-existent node '%s'",
                        node.getId(), connection));
                }
            }
        }

        // Check bidirectional connections
        for (SkillNode node : nodes.values()) {
            for (String connection : node.getConnections()) {
                SkillNode other = nodes.get(connection);
                if (other != null && !other.getConnections().contains(node.getId())) {
                    errors.add(String.format("Connection '%s' -> '%s' is not bidirectional",
                        node.getId(), connection));
                }
            }
        }

        return errors;
    }

    public int getNodeCount() {
        return nodes != null ? nodes.size() : 0;
    }
}
