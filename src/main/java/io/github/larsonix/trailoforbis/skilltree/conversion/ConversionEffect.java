package io.github.larsonix.trailoforbis.skilltree.conversion;

import javax.annotation.Nonnull;

/**
 * Represents a single damage conversion effect from skill tree nodes.
 *
 * <p>Example: "30% Water Damage converted to Fire Damage"
 * <pre>
 * new ConversionEffect(DamageElement.WATER, DamageElement.FIRE, 30.0f)
 * </pre>
 *
 * <p>YAML syntax:
 * <pre>
 * conversions:
 *   - source: WATER
 *     target: FIRE
 *     percent: 30.0
 * </pre>
 *
 * @param source The element being converted FROM
 * @param target The element being converted TO
 * @param percent The percentage of damage to convert (0-100)
 */
public record ConversionEffect(
    @Nonnull DamageElement source,
    @Nonnull DamageElement target,
    float percent
) {
    /**
     * Validates that this is a legal conversion.
     *
     * @return true if the conversion is valid
     */
    public boolean isValid() {
        return source.canConvertTo(target) && percent > 0 && percent <= 100;
    }

    /**
     * Gets the conversion as a decimal multiplier.
     *
     * @return The multiplier (e.g., 0.3 for 30%)
     */
    public float asMultiplier() {
        return percent / 100.0f;
    }

    @Override
    public String toString() {
        return String.format("%.0f%% %s > %s", percent, source.getDisplayName(), target.getDisplayName());
    }
}
