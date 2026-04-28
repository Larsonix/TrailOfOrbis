package io.github.larsonix.trailoforbis.gear.generation;

import io.github.larsonix.trailoforbis.gear.config.EquipmentStatConfig;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig.RarityConfig;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig.ModifierDefinition;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig.ValueRange;
import io.github.larsonix.trailoforbis.gear.model.EquipmentType;
import io.github.larsonix.trailoforbis.gear.model.GearModifier;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.gear.model.ModifierType;

import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Selects modifiers using weighted random selection.
 *
 * <p>Handles:
 * <ul>
 *   <li>Slot-based filtering (some modifiers only on certain gear)</li>
 *   <li><b>Equipment type filtering</b> (daggers get crit, staves get magic)</li>
 *   <li>Weighted random selection (rarer modifiers have lower weights)</li>
 *   <li>Value calculation based on item level and roll variance</li>
 *   <li>Rarity effects (Legendary extended range, Mythic minimum roll)</li>
 *   <li>Preventing duplicate modifiers</li>
 * </ul>
 *
 * <p>This class is thread-safe if the provided Random is thread-safe.
 */
public final class ModifierPool {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final ModifierConfig modifierConfig;
    private final GearBalanceConfig balanceConfig;
    private final EquipmentStatConfig equipmentStatConfig;
    private final Random random;

    /**
     * Creates a ModifierPool with the given configs and random source.
     *
     * @param modifierConfig The modifier configuration
     * @param balanceConfig The gear balance configuration
     * @param equipmentStatConfig The equipment stat restriction config
     * @param random The random number generator
     */
    public ModifierPool(
            ModifierConfig modifierConfig,
            GearBalanceConfig balanceConfig,
            EquipmentStatConfig equipmentStatConfig,
            Random random
    ) {
        this.modifierConfig = Objects.requireNonNull(modifierConfig);
        this.balanceConfig = Objects.requireNonNull(balanceConfig);
        this.equipmentStatConfig = Objects.requireNonNull(equipmentStatConfig);
        this.random = Objects.requireNonNull(random);
    }

    /**
     * Creates a ModifierPool with a new Random instance.
     */
    public ModifierPool(
            ModifierConfig modifierConfig,
            GearBalanceConfig balanceConfig,
            EquipmentStatConfig equipmentStatConfig
    ) {
        this(modifierConfig, balanceConfig, equipmentStatConfig, new Random());
    }

    /**
     * Creates a ModifierPool without equipment stat restrictions (legacy compatibility).
     *
     * @deprecated Use constructor with EquipmentStatConfig instead
     */
    @Deprecated
    public ModifierPool(
            ModifierConfig modifierConfig,
            GearBalanceConfig balanceConfig,
            Random random
    ) {
        this(modifierConfig, balanceConfig, EquipmentStatConfig.unrestricted(), random);
    }

    /**
     * Creates a ModifierPool without equipment stat restrictions (legacy compatibility).
     *
     * @deprecated Use constructor with EquipmentStatConfig instead
     */
    @Deprecated
    public ModifierPool(ModifierConfig modifierConfig, GearBalanceConfig balanceConfig) {
        this(modifierConfig, balanceConfig, EquipmentStatConfig.unrestricted(), new Random());
    }

    /**
     * Rolls a list of prefixes for the given parameters.
     *
     * @param count The number of prefixes to roll
     * @param itemLevel The item level
     * @param slot The gear slot (for filtering)
     * @param rarity The gear rarity (affects value ranges)
     * @return List of rolled GearModifier instances
     */
    public List<GearModifier> rollPrefixes(
            int count,
            int itemLevel,
            String slot,
            GearRarity rarity
    ) {
        return rollModifiers(ModifierType.PREFIX, count, itemLevel, slot, rarity, null);
    }

    /**
     * Rolls a list of prefixes with equipment type filtering.
     *
     * @param count The number of prefixes to roll
     * @param itemLevel The item level
     * @param slot The gear slot (for filtering)
     * @param rarity The gear rarity (affects value ranges)
     * @param equipmentType The equipment type for stat restrictions (nullable)
     * @return List of rolled GearModifier instances
     */
    public List<GearModifier> rollPrefixes(
            int count,
            int itemLevel,
            String slot,
            GearRarity rarity,
            @Nullable EquipmentType equipmentType
    ) {
        return rollModifiers(ModifierType.PREFIX, count, itemLevel, slot, rarity, equipmentType);
    }

    /**
     * Rolls a list of suffixes for the given parameters.
     */
    public List<GearModifier> rollSuffixes(
            int count,
            int itemLevel,
            String slot,
            GearRarity rarity
    ) {
        return rollModifiers(ModifierType.SUFFIX, count, itemLevel, slot, rarity, null);
    }

    /**
     * Rolls a list of suffixes with equipment type filtering.
     *
     * @param count The number of suffixes to roll
     * @param itemLevel The item level
     * @param slot The gear slot (for filtering)
     * @param rarity The gear rarity (affects value ranges)
     * @param equipmentType The equipment type for stat restrictions (nullable)
     * @return List of rolled GearModifier instances
     */
    public List<GearModifier> rollSuffixes(
            int count,
            int itemLevel,
            String slot,
            GearRarity rarity,
            @Nullable EquipmentType equipmentType
    ) {
        return rollModifiers(ModifierType.SUFFIX, count, itemLevel, slot, rarity, equipmentType);
    }

    /**
     * Rolls modifiers of the specified type.
     *
     * @param type PREFIX or SUFFIX
     * @param count Number of modifiers to roll
     * @param itemLevel Item level for value scaling
     * @param slot Gear slot for filtering
     * @param rarity Rarity for value adjustments
     * @return List of unique modifiers
     */
    public List<GearModifier> rollModifiers(
            ModifierType type,
            int count,
            int itemLevel,
            String slot,
            GearRarity rarity
    ) {
        return rollModifiers(type, count, itemLevel, slot, rarity, null);
    }

    /**
     * Rolls modifiers of the specified type with equipment type filtering.
     *
     * <p>The filtering works in two stages:
     * <ol>
     *   <li><b>Slot filtering</b>: Uses gear-modifiers.yml allowed_slots</li>
     *   <li><b>Equipment type filtering</b>: Uses equipment-stats.yml allowed lists</li>
     * </ol>
     *
     * @param type PREFIX or SUFFIX
     * @param count Number of modifiers to roll
     * @param itemLevel Item level for value scaling
     * @param slot Gear slot for slot-based filtering
     * @param rarity Rarity for value adjustments
     * @param equipmentType Equipment type for stat restrictions (nullable)
     * @return List of unique modifiers
     */
    public List<GearModifier> rollModifiers(
            ModifierType type,
            int count,
            int itemLevel,
            String slot,
            GearRarity rarity,
            @Nullable EquipmentType equipmentType
    ) {
        if (count <= 0) {
            return List.of();
        }

        // Stage 1: Get available modifiers for this slot (from gear-modifiers.yml)
        List<ModifierDefinition> available = type == ModifierType.PREFIX
                ? modifierConfig.prefixesForSlot(slot)
                : modifierConfig.suffixesForSlot(slot);

        if (available.isEmpty()) {
            LOGGER.atFine().log("No %s modifiers available for slot %s", type, slot);
            return List.of();
        }

        // Stage 2: Filter by equipment type restrictions (from equipment-stats.yml)
        if (equipmentType != null) {
            int beforeCount = available.size();
            Set<String> allowedIds = type == ModifierType.PREFIX
                    ? equipmentStatConfig.getAllowedPrefixes(equipmentType)
                    : equipmentStatConfig.getAllowedSuffixes(equipmentType);

            // Only filter if restrictions are configured (empty set = no restrictions)
            if (!allowedIds.isEmpty()) {
                available = available.stream()
                        .filter(mod -> allowedIds.contains(mod.id().toLowerCase()))
                        .toList();

                if (available.size() < beforeCount) {
                    LOGGER.atFine().log("Equipment type %s filtered %s from %d to %d modifiers",
                            equipmentType, type, beforeCount, available.size());
                }
            }
        }

        if (available.isEmpty()) {
            LOGGER.atFine().log("No %s modifiers available for equipment type %s after filtering",
                    type, equipmentType);
            return List.of();
        }

        // Track selected IDs to prevent duplicates
        Set<String> selectedIds = new HashSet<>();
        List<GearModifier> result = new ArrayList<>();

        for (int i = 0; i < count && !available.isEmpty(); i++) {
            // Filter out already selected
            List<ModifierDefinition> remaining = available.stream()
                    .filter(m -> !selectedIds.contains(m.id()))
                    .toList();

            if (remaining.isEmpty()) {
                break;
            }

            // Roll one modifier
            ModifierDefinition selected = selectWeighted(remaining);
            selectedIds.add(selected.id());

            // Calculate value
            double value = calculateValue(selected, itemLevel, rarity);

            // Create GearModifier with all required fields (defaults to unlocked)
            result.add(GearModifier.of(
                    selected.id(),
                    selected.displayName(),
                    type,
                    selected.stat(),
                    selected.statType().name().toLowerCase(), // "flat" or "percent"
                    value
            ));
        }

        // Warn if pool exhaustion prevented rolling the requested count
        if (result.size() < count) {
            LOGGER.atWarning().log(
                    "Could only roll %d/%d %s modifiers for slot=%s, equipmentType=%s (pool exhausted)",
                    result.size(), count, type, slot, equipmentType);
        }

        return List.copyOf(result);
    }

    /**
     * Selects a modifier using weighted random selection.
     *
     * @param candidates List of candidate modifiers
     * @return The selected modifier definition
     */
    ModifierDefinition selectWeighted(List<ModifierDefinition> candidates) {
        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        // Calculate total weight
        int totalWeight = candidates.stream()
                .mapToInt(ModifierDefinition::weight)
                .sum();

        // Roll
        int roll = random.nextInt(totalWeight);
        int cumulative = 0;

        for (ModifierDefinition mod : candidates) {
            cumulative += mod.weight();
            if (roll < cumulative) {
                return mod;
            }
        }

        // Fallback (should never reach)
        return candidates.get(candidates.size() - 1);
    }

    /**
     * Calculates the final value for a modifier.
     *
     * <p>The calculation considers:
     * <ul>
     *   <li>Base range from modifier definition</li>
     *   <li>Level scaling from modifier definition</li>
     *   <li><b>Exponential scaling multiplier</b> (if enabled)</li>
     *   <li>Roll variance from balance config</li>
     *   <li>Rarity stat multiplier</li>
     *   <li>Mythic minimum roll percentile</li>
     *   <li>Legendary/Mythic extended range</li>
     * </ul>
     *
     * @param definition The modifier definition
     * @param itemLevel The item level
     * @param rarity The gear rarity
     * @return The calculated value
     */
    double calculateValue(ModifierDefinition definition, int itemLevel, GearRarity rarity) {
        RarityConfig rarityConfig = balanceConfig.rarityConfig(rarity);
        double rollVariance = balanceConfig.modifierScaling().rollVariance();

        // Get base range for this level (linear scaling from ModifierDefinition)
        ValueRange baseRange = definition.calculateRange(itemLevel);

        // Apply exponential scaling multiplier to the base range
        // This transforms linear growth into exponential growth for dramatic progression
        double expMultiplier = balanceConfig.exponentialScaling().calculateMultiplier(itemLevel);
        double scaledMin = baseRange.min() * expMultiplier;
        double scaledMax = baseRange.max() * expMultiplier;

        // Apply rarity stat multiplier for extended range
        // Legendary/Mythic have >1.0 multiplier, extending the max
        double extendedMax = scaledMax * rarityConfig.statMultiplier();
        double effectiveMin = scaledMin;
        double effectiveMax = extendedMax;

        // Roll factor (0.0 to 1.0)
        double rollFactor = random.nextDouble();

        // For Mythic, enforce minimum roll percentile
        if (rarityConfig.minRollPercentile() > 0) {
            double minPercentile = rarityConfig.minRollPercentile();
            // Scale roll to be within [minPercentile, 1.0]
            rollFactor = minPercentile + (rollFactor * (1.0 - minPercentile));
        }

        // Calculate base value from range
        double baseValue = effectiveMin + (effectiveMax - effectiveMin) * rollFactor;

        // Apply roll variance
        // Variance allows values to be ±variance% of calculated base
        double varianceFactor = 1.0 + ((random.nextDouble() * 2 - 1) * rollVariance);
        double finalValue = baseValue * varianceFactor;

        // Ensure minimum is at least effectiveMin after variance
        return Math.max(effectiveMin * (1 - rollVariance), finalValue);
    }

    /**
     * Gets the selection probability for a specific modifier.
     *
     * <p>Useful for testing and debugging.
     *
     * @param modifierId The modifier ID
     * @param type PREFIX or SUFFIX
     * @param slot The gear slot
     * @return Probability between 0 and 1, or 0 if not available
     */
    public double getSelectionProbability(String modifierId, ModifierType type, String slot) {
        List<ModifierDefinition> available = type == ModifierType.PREFIX
                ? modifierConfig.prefixesForSlot(slot)
                : modifierConfig.suffixesForSlot(slot);

        int totalWeight = available.stream()
                .mapToInt(ModifierDefinition::weight)
                .sum();

        Optional<ModifierDefinition> target = available.stream()
                .filter(m -> m.id().equalsIgnoreCase(modifierId))
                .findFirst();

        return target.map(m -> (double) m.weight() / totalWeight).orElse(0.0);
    }

    /**
     * Gets available modifier count for a slot.
     */
    public int getAvailablePrefixCount(String slot) {
        return modifierConfig.prefixesForSlot(slot).size();
    }

    /**
     * Gets available suffix count for a slot.
     */
    public int getAvailableSuffixCount(String slot) {
        return modifierConfig.suffixesForSlot(slot).size();
    }
}
