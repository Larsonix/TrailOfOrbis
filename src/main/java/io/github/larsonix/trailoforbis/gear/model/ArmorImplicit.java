package io.github.larsonix.trailoforbis.gear.model;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.Random;

/**
 * Represents the implicit base defense stat on an armor piece.
 *
 * <p>Unlike modifiers (prefixes/suffixes), implicits are:
 * <ul>
 *   <li><b>Quality-independent</b>: Not affected by gear quality</li>
 *   <li><b>Level-scaled</b>: Scales with armor level using exponential scaling</li>
 *   <li><b>Has a range</b>: Rolled within [min-max], displayable as "[72-98] Armor"</li>
 *   <li><b>Material-differentiated</b>: Plate gives armor, leather gives evasion, etc.</li>
 *   <li><b>Slot-scaled</b>: Chest gives more than hands via slot multipliers</li>
 *   <li><b>Rerollable</b>: Can be rerolled within its range using a calibration stone</li>
 * </ul>
 *
 * <p>Material-to-stat mapping:
 * <ul>
 *   <li>PLATE → armor (physical damage reduction)</li>
 *   <li>LEATHER → evasion (dodge chance)</li>
 *   <li>CLOTH → energy_shield (absorb layer)</li>
 *   <li>WOOD → max_health (vitality/nature)</li>
 *   <li>SPECIAL → armor (default defense)</li>
 *   <li>SHIELD → block_chance (guaranteed block)</li>
 * </ul>
 *
 * @param defenseType The type of defense (e.g., "armor", "evasion", "energy_shield", "max_health", "block_chance")
 * @param minValue The minimum value of the range at this level
 * @param maxValue The maximum value of the range at this level
 * @param rolledValue The actual rolled value within the range
 */
public record ArmorImplicit(
    @Nonnull String defenseType,
    double minValue,
    double maxValue,
    double rolledValue
) {
    /**
     * Compact constructor with validation.
     */
    public ArmorImplicit {
        Objects.requireNonNull(defenseType, "defenseType cannot be null");
        if (defenseType.isBlank()) {
            throw new IllegalArgumentException("defenseType cannot be blank");
        }
        if (minValue < 0) {
            throw new IllegalArgumentException("minValue cannot be negative: " + minValue);
        }
        if (maxValue < minValue) {
            throw new IllegalArgumentException(
                "maxValue (" + maxValue + ") cannot be less than minValue (" + minValue + ")");
        }
        if (rolledValue < minValue || rolledValue > maxValue) {
            throw new IllegalArgumentException(
                "rolledValue (" + rolledValue + ") must be within range [" + minValue + ", " + maxValue + "]");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // FACTORY METHODS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a new ArmorImplicit with a rolled value within the given range.
     *
     * @param defenseType The type of defense
     * @param minValue The minimum value of the range
     * @param maxValue The maximum value of the range
     * @param random The random source for rolling
     * @return A new ArmorImplicit with a random value in range
     */
    @Nonnull
    public static ArmorImplicit roll(
            @Nonnull String defenseType,
            double minValue,
            double maxValue,
            @Nonnull Random random
    ) {
        double rolledValue = rollValueInRange(minValue, maxValue, random);
        return new ArmorImplicit(defenseType, minValue, maxValue, rolledValue);
    }

    /**
     * Creates a new ArmorImplicit with a specific value (no randomness).
     *
     * <p>Useful for testing or creating guaranteed values.
     *
     * @param defenseType The type of defense
     * @param minValue The minimum value of the range
     * @param maxValue The maximum value of the range
     * @param rolledValue The specific value to use
     * @return A new ArmorImplicit with the specified value
     */
    @Nonnull
    public static ArmorImplicit of(
            @Nonnull String defenseType,
            double minValue,
            double maxValue,
            double rolledValue
    ) {
        return new ArmorImplicit(defenseType, minValue, maxValue, rolledValue);
    }

    // ═══════════════════════════════════════════════════════════════════
    // MUTATION METHODS (return new instances)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a new ArmorImplicit with a rerolled value within the same range.
     *
     * @param random The random source for rolling
     * @return A new ArmorImplicit with the same range but a new rolled value
     */
    @Nonnull
    public ArmorImplicit withRerolledValue(@Nonnull Random random) {
        double newValue = rollValueInRange(minValue, maxValue, random);
        return new ArmorImplicit(defenseType, minValue, maxValue, newValue);
    }

    /**
     * Creates a new ArmorImplicit with a different defense type.
     *
     * @param newDefenseType The new defense type
     * @return A new ArmorImplicit with the updated defense type
     */
    @Nonnull
    public ArmorImplicit withDefenseType(@Nonnull String newDefenseType) {
        return new ArmorImplicit(newDefenseType, minValue, maxValue, rolledValue);
    }

    // ═══════════════════════════════════════════════════════════════════
    // QUERY METHODS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the rolled value as an integer (rounded).
     *
     * @return The rolled value as a rounded integer
     */
    public int rolledValueAsInt() {
        return (int) Math.round(rolledValue);
    }

    /**
     * Gets the minimum value as an integer (floored).
     *
     * @return The minimum value as a floored integer
     */
    public int minValueAsInt() {
        return (int) Math.floor(minValue);
    }

    /**
     * Gets the maximum value as an integer (ceiled).
     *
     * @return The maximum value as a ceiled integer
     */
    public int maxValueAsInt() {
        return (int) Math.ceil(maxValue);
    }

    /**
     * Calculates the percentile of the rolled value within the range.
     *
     * <p>Returns 1.0 if min == max (perfect roll by definition).
     *
     * @return A value between 0.0 (minimum) and 1.0 (maximum)
     */
    public double rollPercentile() {
        if (maxValue == minValue) {
            return 1.0;
        }
        return (rolledValue - minValue) / (maxValue - minValue);
    }

    /**
     * @return true if defenseType is "armor"
     */
    public boolean isArmor() {
        return "armor".equals(defenseType);
    }

    /**
     * @return true if defenseType is "evasion"
     */
    public boolean isEvasion() {
        return "evasion".equals(defenseType);
    }

    /**
     * @return true if defenseType is "energy_shield"
     */
    public boolean isEnergyShield() {
        return "energy_shield".equals(defenseType);
    }

    /**
     * @return true if defenseType is "max_health"
     */
    public boolean isMaxHealth() {
        return "max_health".equals(defenseType);
    }

    /**
     * @return true if defenseType is "block_chance"
     */
    public boolean isBlockChance() {
        return "block_chance".equals(defenseType);
    }

    /**
     * Gets the display name for the defense type.
     *
     * <p>Converts snake_case to Title Case (e.g., "energy_shield" → "Energy Shield").
     *
     * @return The human-readable defense type name
     */
    @Nonnull
    public String defenseTypeDisplayName() {
        String[] parts = defenseType.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!sb.isEmpty()) {
                sb.append(" ");
            }
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    sb.append(part.substring(1).toLowerCase());
                }
            }
        }
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Rolls a value uniformly within the given range.
     */
    private static double rollValueInRange(double min, double max, Random random) {
        if (max == min) {
            return min;
        }
        return min + random.nextDouble() * (max - min);
    }

    @Override
    public String toString() {
        return String.format(
            "ArmorImplicit[%s: %.1f (range: %.1f-%.1f, %.0f%%)]",
            defenseType, rolledValue, minValue, maxValue, rollPercentile() * 100
        );
    }
}
