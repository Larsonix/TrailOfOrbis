package io.github.larsonix.trailoforbis.gear.tooltip;

import io.github.larsonix.trailoforbis.gear.model.GearRarity;

import javax.annotation.Nonnull;

/**
 * Centralized style constants for rich text tooltips.
 *
 * <p>Provides hex color codes for use with Hytale's Message API.
 * All colors are in "#RRGGBB" format.
 */
public final class TooltipStyles {

    private TooltipStyles() {
        // Utility class
    }

    // =========================================================================
    // QUALITY TIER COLORS
    // =========================================================================

    /** Poor quality (1-19%): Gray */
    public static final String QUALITY_POOR = "#808080";

    /** Below average quality (20-39%): Dark yellow/orange */
    public static final String QUALITY_BELOW_AVERAGE = "#CC9900";

    /** Average quality (40-69%): White */
    public static final String QUALITY_AVERAGE = "#FFFFFF";

    /** Above average quality (70-89%): Aqua/cyan */
    public static final String QUALITY_ABOVE_AVERAGE = "#55FFFF";

    /** Excellent quality (90-99%): Green */
    public static final String QUALITY_EXCELLENT = "#55FF55";

    /** Perfect quality (100%+): Gold */
    public static final String QUALITY_PERFECT = "#FFD700";

    // =========================================================================
    // MODIFIER COLORS
    // =========================================================================

    /** Positive modifier: Green */
    public static final String POSITIVE_MODIFIER = "#55FF55";

    /** Negative modifier: Red */
    public static final String NEGATIVE_MODIFIER = "#FF5555";

    /** Locked modifier: Light teal (conveys "frozen/protected") */
    public static final String LOCKED_MODIFIER = "#55DDDD";

    // =========================================================================
    // REQUIREMENT COLORS
    // =========================================================================

    /** Requirement met: Green */
    public static final String REQUIREMENT_MET = "#55FF55";

    /** Requirement not met: Red */
    public static final String REQUIREMENT_UNMET = "#FF5555";

    // =========================================================================
    // GENERAL UI COLORS
    // =========================================================================

    /** Label text: Gray */
    public static final String LABEL_GRAY = "#888888";

    /** Value text: White */
    public static final String VALUE_WHITE = "#FFFFFF";

    /** Separator/divider: Dark gray */
    public static final String SEPARATOR = "#555555";

    /** Hex casting section header: Medium slate blue */
    public static final String HEX_SECTION = "#7B68EE";

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    /**
     * Gets the appropriate color for a quality percentage.
     *
     * @param quality The quality value (1-101)
     * @return Hex color code for the quality tier
     */
    @Nonnull
    public static String getQualityColor(int quality) {
        if (quality >= 100) return QUALITY_PERFECT;
        if (quality >= 90) return QUALITY_EXCELLENT;
        if (quality >= 70) return QUALITY_ABOVE_AVERAGE;
        if (quality >= 40) return QUALITY_AVERAGE;
        if (quality >= 20) return QUALITY_BELOW_AVERAGE;
        return QUALITY_POOR;
    }

    /**
     * Gets the quality tier name for display.
     *
     * @param quality The quality value (1-101)
     * @return Human-readable quality tier name
     */
    @Nonnull
    public static String getQualityName(int quality) {
        if (quality >= 100) return "Perfect";
        if (quality >= 90) return "Excellent";
        if (quality >= 70) return "Above Average";
        if (quality >= 40) return "Average";
        if (quality >= 20) return "Below Average";
        return "Poor";
    }

    /**
     * Gets the hex color for a rarity tier.
     *
     * <p>Delegates to {@link GearRarity#getHexColor()} for consistency.
     *
     * @param rarity The gear rarity
     * @return Hex color code
     */
    @Nonnull
    public static String getRarityColor(@Nonnull GearRarity rarity) {
        return rarity.getHexColor();
    }

    /**
     * Gets the appropriate color for a modifier value.
     *
     * @param value The modifier value (positive or negative)
     * @return Green for positive/zero, red for negative
     */
    @Nonnull
    public static String getModifierColor(double value) {
        return value >= 0 ? POSITIVE_MODIFIER : NEGATIVE_MODIFIER;
    }

    /**
     * Gets the appropriate color for a modifier value, accounting for lock state.
     *
     * <p>Locked modifiers use teal regardless of sign, conveying "frozen/protected".
     * Unlocked modifiers use green (positive) or red (negative).
     *
     * @param value The modifier value (positive or negative)
     * @param locked Whether the modifier is locked
     * @return Teal for locked, green for positive/zero unlocked, red for negative unlocked
     */
    @Nonnull
    public static String getModifierValueColor(double value, boolean locked) {
        if (locked) {
            return LOCKED_MODIFIER;
        }
        return value >= 0 ? POSITIVE_MODIFIER : NEGATIVE_MODIFIER;
    }

    /**
     * Gets the appropriate color for a requirement check.
     *
     * @param met Whether the requirement is met
     * @return Green if met, red if not
     */
    @Nonnull
    public static String getRequirementColor(boolean met) {
        return met ? REQUIREMENT_MET : REQUIREMENT_UNMET;
    }
}
