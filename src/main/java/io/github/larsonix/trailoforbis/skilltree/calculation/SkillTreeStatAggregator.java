package io.github.larsonix.trailoforbis.skilltree.calculation;

import io.github.larsonix.trailoforbis.skilltree.config.SkillNode;
import io.github.larsonix.trailoforbis.skilltree.config.SkillTreeConfig;
import io.github.larsonix.trailoforbis.skilltree.model.SkillTreeData;
import io.github.larsonix.trailoforbis.skilltree.model.StatModifier;
import io.github.larsonix.trailoforbis.skilltree.synergy.SynergyNodeCalculator;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Aggregates all stat modifiers from a player's allocated skill tree nodes.
 *
 * <p>This class collects modifiers from each allocated node in the skill tree
 * and produces an {@link AggregatedModifiers} object ready for calculation.
 *
 * <p>The aggregation process:
 * <ol>
 *   <li>Collect base modifiers from all allocated nodes</li>
 *   <li>Calculate synergy bonuses from synergy nodes</li>
 *   <li>Collect drawbacks from keystone nodes (negative tradeoff effects)</li>
 *   <li>Combine all modifiers into the result</li>
 * </ol>
 *
 * @see SynergyNodeCalculator
 */
public class SkillTreeStatAggregator {
    private final SkillTreeConfig config;
    private final SynergyNodeCalculator synergyCalculator;

    /**
     * Creates a new aggregator with the given skill tree configuration.
     *
     * @param config The skill tree configuration containing node definitions
     */
    public SkillTreeStatAggregator(@Nonnull SkillTreeConfig config) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.synergyCalculator = new SynergyNodeCalculator(config);
    }

    /**
     * Collects all modifiers from allocated nodes, including synergy bonuses and drawbacks.
     *
     * <p>The aggregation order:
     * <ol>
     *   <li>Collect base modifiers from each allocated node</li>
     *   <li>Calculate synergy bonuses based on allocation counts</li>
     *   <li>Collect drawbacks from keystone nodes (negative modifiers)</li>
     *   <li>Combine all modifiers</li>
     * </ol>
     *
     * @param data Player's skill tree data containing allocated node IDs
     * @return Aggregated modifiers ready for calculation
     */
    @Nonnull
    public AggregatedModifiers aggregate(@Nonnull SkillTreeData data) {
        AggregatedModifiers.Builder builder = AggregatedModifiers.builder();

        Set<String> allocated = data.getAllocatedNodes();

        // Step 1: Collect base modifiers from allocated nodes
        for (String nodeId : allocated) {
            SkillNode node = config.getNode(nodeId);
            if (node != null) {
                builder.addAllModifiers(node.getModifiers());
            }
        }

        // Step 2: Calculate and add synergy bonuses
        List<StatModifier> synergyBonuses = synergyCalculator.calculateSynergyBonuses(allocated);
        builder.addAllModifiers(synergyBonuses);

        // Step 3: Collect drawbacks from keystone nodes (negative effects)
        for (String nodeId : allocated) {
            SkillNode node = config.getNode(nodeId);
            if (node != null && node.hasDrawbacks()) {
                builder.addAllModifiers(node.getDrawbacks());
            }
        }

        return builder.build();
    }

    /**
     * Collects base modifiers only (without synergy bonuses but including drawbacks).
     *
     * <p>Useful for debugging or when synergy calculation should be deferred.
     *
     * @param data Player's skill tree data containing allocated node IDs
     * @return Aggregated base modifiers (no synergies)
     */
    @Nonnull
    public AggregatedModifiers aggregateBaseOnly(@Nonnull SkillTreeData data) {
        AggregatedModifiers.Builder builder = AggregatedModifiers.builder();

        Set<String> allocated = data.getAllocatedNodes();
        for (String nodeId : allocated) {
            SkillNode node = config.getNode(nodeId);
            if (node != null) {
                builder.addAllModifiers(node.getModifiers());
                // Include drawbacks in base modifiers
                if (node.hasDrawbacks()) {
                    builder.addAllModifiers(node.getDrawbacks());
                }
            }
        }

        return builder.build();
    }

    /**
     * Calculates synergy bonuses only (without base modifiers).
     *
     * <p>Useful for displaying synergy contribution in UI.
     *
     * @param data Player's skill tree data containing allocated node IDs
     * @return List of synergy bonus modifiers
     */
    @Nonnull
    public List<StatModifier> calculateSynergyBonuses(@Nonnull SkillTreeData data) {
        return synergyCalculator.calculateSynergyBonuses(data.getAllocatedNodes());
    }

    /**
     * Collects drawback modifiers only (negative effects from keystones).
     *
     * <p>Useful for displaying keystone tradeoffs in UI.
     *
     * @param data Player's skill tree data containing allocated node IDs
     * @return List of drawback modifiers (typically negative values)
     */
    @Nonnull
    public List<StatModifier> collectDrawbacks(@Nonnull SkillTreeData data) {
        java.util.ArrayList<StatModifier> drawbacks = new java.util.ArrayList<>();

        for (String nodeId : data.getAllocatedNodes()) {
            SkillNode node = config.getNode(nodeId);
            if (node != null && node.hasDrawbacks()) {
                drawbacks.addAll(node.getDrawbacks());
            }
        }

        return drawbacks;
    }

    /**
     * Gets the underlying skill tree configuration.
     *
     * @return The skill tree configuration
     */
    @Nonnull
    public SkillTreeConfig getConfig() {
        return config;
    }

    /**
     * Gets the synergy calculator.
     *
     * @return The synergy node calculator
     */
    @Nonnull
    public SynergyNodeCalculator getSynergyCalculator() {
        return synergyCalculator;
    }
}
