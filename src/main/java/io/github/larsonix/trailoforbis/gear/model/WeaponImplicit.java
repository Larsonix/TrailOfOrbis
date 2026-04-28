package io.github.larsonix.trailoforbis.gear.model;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.Random;

/**
 * Represents the implicit base damage stat on a weapon.
 *
 * <p>Unlike modifiers (prefixes/suffixes), implicits are:
 * <ul>
 *   <li><b>Quality-independent</b>: Not affected by gear quality</li>
 *   <li><b>Level-scaled</b>: Scales with weapon level using exponential scaling</li>
 *   <li><b>Has a range</b>: Rolled within [min-max], displayable as "[153-198] Physical Damage"</li>
 *   <li><b>Weapon-type differentiated</b>: Longswords have higher base than daggers</li>
 *   <li><b>Damage-type aware</b>: Physical for physical weapons, Spell for magic weapons</li>
 *   <li><b>Rerollable</b>: Can be rerolled within its range using a calibration stone</li>
 * </ul>
 *
 * <p>The implicit is calculated at generation time with the weapon's level and type,
 * then the actual value is rolled within the calculated range. The range is stored
 * to enable rerolling and for tooltip display.
 *
 * @param damageType The type of damage (e.g., "physical_damage", "spell_damage")
 * @param minValue The minimum value of the range at this level
 * @param maxValue The maximum value of the range at this level
 * @param rolledValue The actual rolled value within the range
 */
public record WeaponImplicit(
    @Nonnull String damageType,
    double minValue,
    double maxValue,
    double rolledValue
) {
    /**
     * Compact constructor with validation.
     */
    public WeaponImplicit {
        Objects.requireNonNull(damageType, "damageType cannot be null");
        if (damageType.isBlank()) {
            throw new IllegalArgumentException("damageType cannot be blank");
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
     * Creates a new WeaponImplicit with a rolled value within the given range.
     *
     * @param damageType The type of damage
     * @param minValue The minimum value of the range
     * @param maxValue The maximum value of the range
     * @param random The random source for rolling
     * @return A new WeaponImplicit with a random value in range
     */
    @Nonnull
    public static WeaponImplicit roll(
            @Nonnull String damageType,
            double minValue,
            double maxValue,
            @Nonnull Random random
    ) {
        double rolledValue = rollValueInRange(minValue, maxValue, random);
        return new WeaponImplicit(damageType, minValue, maxValue, rolledValue);
    }

    /**
     * Creates a new WeaponImplicit with a specific value (no randomness).
     *
     * <p>Useful for testing or creating guaranteed values.
     *
     * @param damageType The type of damage
     * @param minValue The minimum value of the range
     * @param maxValue The maximum value of the range
     * @param rolledValue The specific value to use
     * @return A new WeaponImplicit with the specified value
     */
    @Nonnull
    public static WeaponImplicit of(
            @Nonnull String damageType,
            double minValue,
            double maxValue,
            double rolledValue
    ) {
        return new WeaponImplicit(damageType, minValue, maxValue, rolledValue);
    }

    // ═══════════════════════════════════════════════════════════════════
    // MUTATION METHODS (return new instances)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a new WeaponImplicit with a rerolled value within the same range.
     *
     * @param random The random source for rolling
     * @return A new WeaponImplicit with the same range but a new rolled value
     */
    @Nonnull
    public WeaponImplicit withRerolledValue(@Nonnull Random random) {
        double newValue = rollValueInRange(minValue, maxValue, random);
        return new WeaponImplicit(damageType, minValue, maxValue, newValue);
    }

    /**
     * Creates a new WeaponImplicit with a different damage type.
     *
     * @param newDamageType The new damage type
     * @return A new WeaponImplicit with the updated damage type
     */
    @Nonnull
    public WeaponImplicit withDamageType(@Nonnull String newDamageType) {
        return new WeaponImplicit(newDamageType, minValue, maxValue, rolledValue);
    }

    // ═══════════════════════════════════════════════════════════════════
    // QUERY METHODS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the rolled value as an integer (rounded).
     *
     * <p>Use this for display purposes where whole numbers are preferred.
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
            return 1.0; // No range means perfect roll
        }
        return (rolledValue - minValue) / (maxValue - minValue);
    }

    /**
     * Checks if this is a physical damage implicit.
     *
     * @return true if damageType is "physical_damage"
     */
    public boolean isPhysicalDamage() {
        return "physical_damage".equals(damageType);
    }

    /**
     * Checks if this is a spell damage implicit.
     *
     * @return true if damageType is "spell_damage"
     */
    public boolean isSpellDamage() {
        return "spell_damage".equals(damageType);
    }

    /**
     * Gets the display name for the damage type.
     *
     * <p>Converts snake_case to Title Case (e.g., "physical_damage" → "Physical Damage").
     *
     * @return The human-readable damage type name
     */
    @Nonnull
    public String damageTypeDisplayName() {
        String[] parts = damageType.split("_");
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
            "WeaponImplicit[%s: %.1f (range: %.1f-%.1f, %.0f%%)]",
            damageType, rolledValue, minValue, maxValue, rollPercentile() * 100
        );
    }
}
