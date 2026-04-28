package io.github.larsonix.trailoforbis.gear.config;

import io.github.larsonix.trailoforbis.attributes.AttributeType;
import io.github.larsonix.trailoforbis.gear.model.ModifierType;
import io.github.larsonix.trailoforbis.util.LevelScaling;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Configuration for gear modifiers loaded from gear-modifiers.yml.
 *
 * <p>Contains all prefix and suffix definitions with their scaling values,
 * weights, and slot restrictions.
 *
 * <p>This class is immutable after construction.
 */
public final class ModifierConfig {

    private final Map<String, ModifierDefinition> prefixes;
    private final Map<String, ModifierDefinition> suffixes;

    // Cached collections for efficient lookup
    private final List<ModifierDefinition> prefixList;
    private final List<ModifierDefinition> suffixList;
    private final int totalPrefixWeight;
    private final int totalSuffixWeight;

    // =========================================================================
    // CONSTRUCTOR (package-private, use GearConfigLoader)
    // =========================================================================

    ModifierConfig(
            Map<String, ModifierDefinition> prefixes,
            Map<String, ModifierDefinition> suffixes
    ) {
        this.prefixes = Map.copyOf(prefixes);
        this.suffixes = Map.copyOf(suffixes);

        // Cache lists for iteration
        this.prefixList = List.copyOf(prefixes.values());
        this.suffixList = List.copyOf(suffixes.values());

        // Pre-calculate total weights
        this.totalPrefixWeight = prefixList.stream()
                .mapToInt(ModifierDefinition::weight)
                .sum();
        this.totalSuffixWeight = suffixList.stream()
                .mapToInt(ModifierDefinition::weight)
                .sum();
    }

    // =========================================================================
    // GETTERS
    // =========================================================================

    public Map<String, ModifierDefinition> prefixes() {
        return prefixes;
    }

    public Map<String, ModifierDefinition> suffixes() {
        return suffixes;
    }

    /**
     * @return the definition, or empty if not found
     */
    public Optional<ModifierDefinition> getPrefix(String id) {
        return Optional.ofNullable(prefixes.get(id.toLowerCase()));
    }

    /**
     * @return the definition, or empty if not found
     */
    public Optional<ModifierDefinition> getSuffix(String id) {
        return Optional.ofNullable(suffixes.get(id.toLowerCase()));
    }

    public Optional<ModifierDefinition> getModifier(String id, ModifierType type) {
        return type == ModifierType.PREFIX ? getPrefix(id) : getSuffix(id);
    }

    public List<ModifierDefinition> prefixList() {
        return prefixList;
    }

    public List<ModifierDefinition> suffixList() {
        return suffixList;
    }

    /**
     * Returns prefixes that are allowed on a specific slot.
     *
     * @param slot e.g., "weapon", "chest"
     */
    public List<ModifierDefinition> prefixesForSlot(String slot) {
        String normalizedSlot = slot.toLowerCase();
        return prefixList.stream()
                .filter(mod -> mod.isAllowedOnSlot(normalizedSlot))
                .collect(Collectors.toList());
    }

    public List<ModifierDefinition> suffixesForSlot(String slot) {
        String normalizedSlot = slot.toLowerCase();
        return suffixList.stream()
                .filter(mod -> mod.isAllowedOnSlot(normalizedSlot))
                .collect(Collectors.toList());
    }

    public int totalPrefixWeight() {
        return totalPrefixWeight;
    }

    public int totalSuffixWeight() {
        return totalSuffixWeight;
    }

    public int totalPrefixWeightForSlot(String slot) {
        return prefixesForSlot(slot).stream()
                .mapToInt(ModifierDefinition::weight)
                .sum();
    }

    public int totalSuffixWeightForSlot(String slot) {
        return suffixesForSlot(slot).stream()
                .mapToInt(ModifierDefinition::weight)
                .sum();
    }

    public Set<String> allStatIds() {
        return Stream.concat(prefixList.stream(), suffixList.stream())
                .map(ModifierDefinition::stat)
                .collect(Collectors.toSet());
    }

    // =========================================================================
    // NESTED TYPES
    // =========================================================================

    /**
     * Definition of a single modifier that can roll on gear.
     */
    public record ModifierDefinition(
            String id,
            String displayName,
            String stat,
            StatType statType,
            double baseMin,
            double baseMax,
            double scalePerLevel,
            int weight,
            AttributeType requiredAttribute,  // null = no requirement
            Set<String> allowedSlots          // null = all slots
    ) {
        public ModifierDefinition {
            Objects.requireNonNull(id, "id cannot be null");
            Objects.requireNonNull(displayName, "displayName cannot be null");
            Objects.requireNonNull(stat, "stat cannot be null");
            Objects.requireNonNull(statType, "statType cannot be null");

            if (id.isBlank()) {
                throw new IllegalArgumentException("id cannot be blank");
            }
            if (displayName.isBlank()) {
                throw new IllegalArgumentException("displayName cannot be blank");
            }
            if (stat.isBlank()) {
                throw new IllegalArgumentException("stat cannot be blank");
            }
            // Note: Negative baseMin, baseMax, and scalePerLevel are valid for reduction
            // modifiers (e.g., trajectory modifier reduces projectile gravity)
            if (weight < 0) {
                throw new IllegalArgumentException("weight cannot be negative: " + weight);
            }

            // Defensive copy of allowed slots
            if (allowedSlots != null) {
                allowedSlots = Set.copyOf(allowedSlots.stream()
                        .map(String::toLowerCase)
                        .collect(Collectors.toSet()));
            }
        }

        /**
         * @param slot case-insensitive
         * @return true if allowed (null allowedSlots = all slots allowed)
         */
        public boolean isAllowedOnSlot(String slot) {
            if (allowedSlots == null) {
                return true;
            }
            return allowedSlots.contains(slot.toLowerCase());
        }

        /**
         * Calculates the base value range for a given item level.
         *
         * <p>Uses {@link LevelScaling#getMultiplier} so the linear scaling
         * also respects the diminishing returns curve above the transition level.
         */
        public ValueRange calculateRange(int itemLevel) {
            // Use the capped multiplier instead of raw level so this term
            // also flattens after the transition level (matches expMultiplier behavior)
            double effectiveLevel = (LevelScaling.getMultiplier(itemLevel) - 1.0) * LevelScaling.getTransitionLevel();
            double levelBonus = effectiveLevel * scalePerLevel;
            return new ValueRange(baseMin + levelBonus, baseMax + levelBonus);
        }

        /**
         * Calculates a specific value at given item level with roll factor.
         *
         * @param rollFactor 0.0 = min, 1.0 = max
         */
        public double calculateValue(int itemLevel, double rollFactor) {
            ValueRange range = calculateRange(itemLevel);
            return range.min + (range.max - range.min) * rollFactor;
        }
    }

    public enum StatType {
        /** Adds a flat value to the stat */
        FLAT,
        /** Adds a percentage modifier to the stat */
        PERCENT;

        /**
         * Parses a string to StatType (case-insensitive).
         *
         * @throws IllegalArgumentException if string is not "flat" or "percent"
         */
        public static StatType fromString(String s) {
            return switch (s.toLowerCase()) {
                case "flat" -> FLAT;
                case "percent", "%" -> PERCENT;
                default -> throw new IllegalArgumentException(
                    "Invalid stat type: " + s + " (expected 'flat' or 'percent')");
            };
        }
    }

    /**
     * A range of possible values for a modifier.
     *
     * <p>Note: For reduction modifiers (negative values), "min" represents the
     * smaller absolute reduction and "max" the larger. E.g., -5% to -15% gravity
     * has min=-5, max=-15 (semantically correct even though min > max numerically).
     */
    public record ValueRange(double min, double max) {
        // No validation - negative ranges (like -5 to -15 for reductions) are valid

        /**
         * Interpolates within the range.
         *
         * @param factor 0.0 = min, 1.0 = max
         */
        public double interpolate(double factor) {
            return min + (max - min) * Math.max(0, Math.min(1, factor));
        }
    }
}
