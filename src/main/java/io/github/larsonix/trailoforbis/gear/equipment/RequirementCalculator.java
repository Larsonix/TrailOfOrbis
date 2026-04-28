package io.github.larsonix.trailoforbis.gear.equipment;

import io.github.larsonix.trailoforbis.attributes.AttributeType;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig.AttributeRequirementsConfig;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig.ModifierDefinition;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.GearModifier;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;

import java.util.*;

/**
 * Calculates attribute requirements for gear based on its modifiers.
 *
 * <p>Requirements are determined by:
 * <ul>
 *   <li>Which modifiers are present (determines required attributes)</li>
 *   <li>Item level (base requirement amount)</li>
 *   <li>Rarity (multiplier on base)</li>
 * </ul>
 *
 * <p>Important: The VALUE of modifiers does NOT affect requirements.
 * Only the PRESENCE of a modifier triggers its associated attribute requirement.
 */
public final class RequirementCalculator {

    private final GearBalanceConfig balanceConfig;
    private final ModifierConfig modifierConfig;

    // Cache: stat ID -> required attribute
    private final Map<String, AttributeType> statToAttributeCache;

    /**
     * Creates a RequirementCalculator with the given configs.
     *
     * @param balanceConfig The gear balance configuration
     * @param modifierConfig The modifier configuration
     */
    public RequirementCalculator(GearBalanceConfig balanceConfig, ModifierConfig modifierConfig) {
        this.balanceConfig = Objects.requireNonNull(balanceConfig);
        this.modifierConfig = Objects.requireNonNull(modifierConfig);
        this.statToAttributeCache = buildStatToAttributeMap();
    }

    private Map<String, AttributeType> buildStatToAttributeMap() {
        Map<String, AttributeType> map = new HashMap<>();

        // Process all prefixes
        for (ModifierDefinition def : modifierConfig.prefixList()) {
            if (def.requiredAttribute() != null) {
                map.put(def.stat().toLowerCase(), def.requiredAttribute());
            }
        }

        // Process all suffixes
        for (ModifierDefinition def : modifierConfig.suffixList()) {
            if (def.requiredAttribute() != null) {
                map.put(def.stat().toLowerCase(), def.requiredAttribute());
            }
        }

        return Collections.unmodifiableMap(map);
    }

    /**
     * Calculates the attribute requirements for a piece of gear.
     *
     * @param gearData The gear data
     * @return Map of required attributes to their minimum values
     */
    public Map<AttributeType, Integer> calculateRequirements(GearData gearData) {
        if (gearData == null) {
            return Map.of();
        }

        // Collect all required attributes from modifiers
        Set<AttributeType> requiredAttributes = collectRequiredAttributes(gearData);

        if (requiredAttributes.isEmpty()) {
            return Map.of();
        }

        // Calculate the requirement amount based on level and rarity
        int requirementAmount = calculateRequirementAmount(
                gearData.level(),
                gearData.rarity()
        );

        // If requirement is 0 (low level or common), no requirements
        if (requirementAmount <= 0) {
            return Map.of();
        }

        // Build result map
        Map<AttributeType, Integer> requirements = new EnumMap<>(AttributeType.class);
        for (AttributeType attr : requiredAttributes) {
            requirements.put(attr, requirementAmount);
        }

        return Collections.unmodifiableMap(requirements);
    }

    /**
     * Collects all unique attribute requirements from gear modifiers.
     *
     * <p>Uses the modifier definition's requiredAttribute field.
     */
    Set<AttributeType> collectRequiredAttributes(GearData gearData) {
        Set<AttributeType> attributes = EnumSet.noneOf(AttributeType.class);

        // Check prefixes
        for (GearModifier mod : gearData.prefixes()) {
            AttributeType attr = getRequiredAttributeForStat(mod.statId());
            if (attr != null) {
                attributes.add(attr);
            }
        }

        // Check suffixes
        for (GearModifier mod : gearData.suffixes()) {
            AttributeType attr = getRequiredAttributeForStat(mod.statId());
            if (attr != null) {
                attributes.add(attr);
            }
        }

        return attributes;
    }

    /**
     * Gets the required attribute for a stat ID.
     *
     * @param statId The stat ID (e.g., "physical_damage", "max_health")
     * @return The required attribute, or null if none
     */
    public AttributeType getRequiredAttributeForStat(String statId) {
        return statToAttributeCache.get(statId.toLowerCase());
    }

    /**
     * Calculates the base requirement amount from level and rarity.
     *
     * <p>Formula: floor(level × levelToBaseRatio × rarityMultiplier)
     *
     * @param itemLevel The item level
     * @param rarity The gear rarity
     * @return The requirement amount, or 0 if below minimum level
     */
    int calculateRequirementAmount(int itemLevel, GearRarity rarity) {
        AttributeRequirementsConfig config = balanceConfig.attributeRequirements();

        // Check minimum level
        if (itemLevel < config.minItemLevelForRequirements()) {
            return 0;
        }

        return config.calculateRequirement(itemLevel, rarity);
    }

    /**
     * Checks if gear has any attribute requirements.
     *
     * @param gearData The gear data
     * @return true if at least one requirement exists
     */
    public boolean hasRequirements(GearData gearData) {
        if (gearData == null) {
            return false;
        }

        // Quick check: level below minimum means no requirements
        AttributeRequirementsConfig config = balanceConfig.attributeRequirements();
        if (gearData.level() < config.minItemLevelForRequirements()) {
            return false;
        }

        // Check if any modifier has a required attribute
        return !collectRequiredAttributes(gearData).isEmpty();
    }

    /**
     * Gets all stat IDs that are associated with a specific attribute.
     *
     * <p>Useful for tooltip generation.
     */
    public Set<String> getStatsForAttribute(AttributeType attribute) {
        Set<String> stats = new HashSet<>();
        for (Map.Entry<String, AttributeType> entry : statToAttributeCache.entrySet()) {
            if (entry.getValue() == attribute) {
                stats.add(entry.getKey());
            }
        }
        return Collections.unmodifiableSet(stats);
    }
}
