package io.github.larsonix.trailoforbis.skilltree.synergy;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.skilltree.SkillTreeRegion;
import io.github.larsonix.trailoforbis.skilltree.config.SkillNode;
import io.github.larsonix.trailoforbis.skilltree.config.SkillTreeConfig;
import io.github.larsonix.trailoforbis.skilltree.model.ModifierType;
import io.github.larsonix.trailoforbis.skilltree.model.StatModifier;
import io.github.larsonix.trailoforbis.skilltree.model.StatType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * Calculates synergy bonuses for skill tree nodes.
 *
 * <p>Synergy nodes provide bonuses that scale based on other allocations:
 * <ul>
 *   <li><b>ELEMENTAL_COUNT</b>: Per X nodes in same element/arm, gain Y bonus</li>
 *   <li><b>STAT_COUNT</b>: Per X nodes granting a specific stat type, gain Y bonus</li>
 *   <li><b>BRANCH_COUNT</b>: Per X nodes in same branch/region, gain Y bonus</li>
 *   <li><b>TIER_COUNT</b>: Per X notables/keystones allocated, gain Y bonus</li>
 *   <li><b>TOTAL_COUNT</b>: Per X total nodes allocated, gain Y bonus</li>
 * </ul>
 *
 * <p>Example: A FIRE synergy node with "per 3 FIRE nodes, +3% Fire Damage (cap 30%)"
 * would grant +15% Fire Damage if the player has allocated 15 FIRE nodes.
 *
 * @see SynergyConfig
 * @see SynergyType
 */
public class SynergyNodeCalculator {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final SkillTreeConfig config;

    /**
     * Creates a new synergy calculator with the given skill tree configuration.
     *
     * @param config The skill tree configuration containing node definitions
     */
    public SynergyNodeCalculator(@Nonnull SkillTreeConfig config) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
    }

    /**
     * Calculates the progress snapshot for a single synergy node.
     *
     * <p>This is used by the UI to display live progress information like
     * "15 nodes → +10% | Next at 18" alongside the synergy description.
     *
     * @param node             The synergy node to calculate progress for
     * @param synergy          The synergy configuration (primary or additional)
     * @param allocatedNodeIds Set of currently allocated node IDs
     * @return Progress snapshot, or null if the synergy has no valid bonus config
     */
    @Nullable
    public SynergyProgress calculateProgress(@Nonnull SkillNode node, @Nonnull SynergyConfig synergy,
                                             @Nonnull Set<String> allocatedNodeIds) {
        if (synergy.getBonus() == null) {
            return null;
        }

        AllocationContext context = buildAllocationContext(allocatedNodeIds);
        int count = getRelevantCount(synergy, node, context);
        int perCount = synergy.getPerCount();
        int increments = count > 0 ? count / perCount : 0;

        double bonusValue = synergy.getBonus().getValue();
        double totalBonus = increments * bonusValue;
        double cap = synergy.hasCap() ? synergy.getCap() : 0.0;
        boolean capped = synergy.hasCap() && totalBonus >= cap;
        if (capped) {
            totalBonus = cap;
        }

        // Calculate next threshold (count needed for next increment)
        int nextThreshold = 0;
        if (!capped && perCount > 0) {
            nextThreshold = (increments + 1) * perCount;
        }

        String countLabel = buildCountLabel(synergy, node);

        return new SynergyProgress(count, perCount, increments, totalBonus, cap, capped, nextThreshold, countLabel);
    }

    /**
     * Builds a human-readable label for what's being counted.
     */
    @Nonnull
    private String buildCountLabel(@Nonnull SynergyConfig synergy, @Nonnull SkillNode node) {
        return switch (synergy.getType()) {
            case ELEMENTAL_COUNT -> {
                SkillTreeRegion element = synergy.getElementRegion();
                if (element == SkillTreeRegion.CORE && synergy.getElement() == null) {
                    element = node.getSkillTreeRegion();
                }
                yield element.getDisplayName() + " nodes";
            }
            case STAT_COUNT -> {
                String statType = synergy.getStatType();
                yield statType != null ? statType.toLowerCase().replace("_", " ") + " nodes" : "nodes";
            }
            case BRANCH_COUNT -> node.getSkillTreeRegion().getDisplayName() + " nodes";
            case TIER_COUNT -> {
                String tier = synergy.getTier();
                if (tier == null || tier.isBlank() || tier.equalsIgnoreCase("ANY")) {
                    yield "notables/keystones";
                } else {
                    yield tier.toLowerCase() + "s";
                }
            }
            case TOTAL_COUNT -> "total nodes";
        };
    }

    /**
     * Calculates synergy bonus modifiers for all synergy nodes a player has allocated.
     *
     * @param allocatedNodeIds Set of allocated node IDs
     * @return List of calculated synergy bonus modifiers
     */
    @Nonnull
    public List<StatModifier> calculateSynergyBonuses(@Nonnull Set<String> allocatedNodeIds) {
        if (allocatedNodeIds.isEmpty()) {
            return Collections.emptyList();
        }

        // Build allocation context (counts by region, stat, tier)
        AllocationContext context = buildAllocationContext(allocatedNodeIds);

        // Find synergy nodes among allocated nodes and calculate bonuses
        List<StatModifier> synergyBonuses = new ArrayList<>();

        for (String nodeId : allocatedNodeIds) {
            SkillNode node = config.getNode(nodeId);
            if (node == null) continue;

            // Primary synergy
            if (node.hasSynergy()) {
                StatModifier bonus = calculateSynergyBonus(node, node.getSynergy(), context);
                if (bonus != null) {
                    synergyBonuses.add(bonus);
                    LOGGER.atFine().log("Synergy node %s grants: %s", nodeId, bonus.toShortString());
                }
            }

            // Additional synergies (from overlays like Hexcode)
            for (SynergyConfig additional : node.getAdditionalSynergies()) {
                StatModifier bonus = calculateSynergyBonus(node, additional, context);
                if (bonus != null) {
                    synergyBonuses.add(bonus);
                    LOGGER.atFine().log("Synergy node %s (overlay) grants: %s", nodeId, bonus.toShortString());
                }
            }
        }

        return synergyBonuses;
    }

    /**
     * Calculates the synergy bonus for a single synergy config on a node.
     *
     * @param node    The synergy node (used for region context)
     * @param synergy The synergy config to calculate
     * @param context The allocation context with node counts
     * @return The calculated bonus modifier, or null if no bonus
     */
    @Nullable
    private StatModifier calculateSynergyBonus(@Nonnull SkillNode node, @Nonnull SynergyConfig synergy,
                                                @Nonnull AllocationContext context) {
        if (synergy.getBonus() == null) {
            return null;
        }

        // Get the relevant count based on synergy type
        int count = getRelevantCount(synergy, node, context);
        if (count <= 0) {
            return null;
        }

        // Calculate number of bonus increments
        int increments = count / synergy.getPerCount();
        if (increments <= 0) {
            return null;
        }

        // Calculate total bonus value
        SynergyConfig.SynergyBonus bonus = synergy.getBonus();
        double totalValue = increments * bonus.getValue();

        // Apply cap if configured
        if (synergy.hasCap() && totalValue > synergy.getCap()) {
            totalValue = synergy.getCap();
        }

        // Parse stat type and modifier type
        StatType statType = parseStatType(bonus.getStat());
        if (statType == null) {
            LOGGER.atWarning().log("Unknown stat type '%s' in synergy node %s", bonus.getStat(), node.getId());
            return null;
        }

        ModifierType modifierType = parseModifierType(bonus.getModifierType());

        return new StatModifier(statType, (float) totalValue, modifierType);
    }

    /**
     * Gets the relevant count based on the synergy type.
     */
    private int getRelevantCount(@Nonnull SynergyConfig synergy, @Nonnull SkillNode node, @Nonnull AllocationContext context) {
        return switch (synergy.getType()) {
            case ELEMENTAL_COUNT -> {
                // Count nodes in the specified element (arm)
                SkillTreeRegion element = synergy.getElementRegion();
                // If element not specified, use the node's own region
                if (element == SkillTreeRegion.CORE && synergy.getElement() == null) {
                    element = node.getSkillTreeRegion();
                }
                yield context.countByRegion.getOrDefault(element, 0);
            }
            case STAT_COUNT -> {
                // Count nodes granting a specific stat type
                String statTypeStr = synergy.getStatType();
                if (statTypeStr == null || statTypeStr.isBlank()) {
                    yield 0;
                }
                yield context.countByStatType.getOrDefault(statTypeStr.toUpperCase(), 0);
            }
            case BRANCH_COUNT -> {
                // Count nodes in the same branch/region as this node
                SkillTreeRegion nodeRegion = node.getSkillTreeRegion();
                yield context.countByRegion.getOrDefault(nodeRegion, 0);
            }
            case TIER_COUNT -> {
                // Count notables, keystones, or both
                String tier = synergy.getTier();
                if (tier == null || tier.isBlank() || tier.equalsIgnoreCase("ANY")) {
                    yield context.notableCount + context.keystoneCount;
                } else if (tier.equalsIgnoreCase("NOTABLE")) {
                    yield context.notableCount;
                } else if (tier.equalsIgnoreCase("KEYSTONE")) {
                    yield context.keystoneCount;
                } else {
                    yield context.notableCount + context.keystoneCount;
                }
            }
            case TOTAL_COUNT -> context.totalCount;
        };
    }

    /**
     * Builds the allocation context with node counts by various categories.
     */
    @Nonnull
    private AllocationContext buildAllocationContext(@Nonnull Set<String> allocatedNodeIds) {
        AllocationContext context = new AllocationContext();
        context.totalCount = allocatedNodeIds.size();

        for (String nodeId : allocatedNodeIds) {
            SkillNode node = config.getNode(nodeId);
            if (node == null) {
                continue;
            }

            // Count by region
            SkillTreeRegion region = node.getSkillTreeRegion();
            context.countByRegion.merge(region, 1, Integer::sum);

            // Count notables and keystones
            if (node.isNotable()) {
                context.notableCount++;
            }
            if (node.isKeystone()) {
                context.keystoneCount++;
            }

            // Count by stat types (from node's modifiers)
            for (StatModifier modifier : node.getModifiers()) {
                String statName = modifier.getStat().name();
                context.countByStatType.merge(statName, 1, Integer::sum);

                // Also count by base stat name (e.g., FIRE_DAMAGE_PERCENT → FIRE_DAMAGE)
                String baseStatName = getBaseStatName(statName);
                if (!baseStatName.equals(statName)) {
                    context.countByStatType.merge(baseStatName, 1, Integer::sum);
                }
            }
        }

        return context;
    }

    /**
     * Gets the base stat name by stripping suffixes like _PERCENT, _MULTIPLIER.
     */
    @Nonnull
    private String getBaseStatName(@Nonnull String statName) {
        if (statName.endsWith("_PERCENT")) {
            return statName.substring(0, statName.length() - "_PERCENT".length());
        }
        if (statName.endsWith("_MULTIPLIER")) {
            return statName.substring(0, statName.length() - "_MULTIPLIER".length());
        }
        return statName;
    }

    /**
     * Parses a stat type from a string.
     */
    @Nullable
    private StatType parseStatType(@Nonnull String statName) {
        if (statName.isBlank()) {
            return null;
        }
        try {
            return StatType.valueOf(statName.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Parses a modifier type from a string.
     */
    @Nonnull
    private ModifierType parseModifierType(@Nonnull String modifierTypeName) {
        if (modifierTypeName.isBlank()) {
            return ModifierType.PERCENT;
        }
        try {
            return ModifierType.valueOf(modifierTypeName.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return ModifierType.PERCENT;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // INNER CLASSES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Context holding node counts for synergy calculations.
     */
    private static class AllocationContext {
        int totalCount = 0;
        int notableCount = 0;
        int keystoneCount = 0;
        final Map<SkillTreeRegion, Integer> countByRegion = new EnumMap<>(SkillTreeRegion.class);
        final Map<String, Integer> countByStatType = new HashMap<>();
    }
}
