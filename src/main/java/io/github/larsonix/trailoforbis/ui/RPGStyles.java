package io.github.larsonix.trailoforbis.ui;

/**
 * Shared style definitions for all RPG UI pages and HUDs.
 *
 * <p>Provides consistent colors and formatting across stats, attributes,
 * skill tree, sanctum, realm, and stone UI elements following vanilla
 * Hytale's visual design language with brighter, more readable colors.
 */
public final class RPGStyles {

    private RPGStyles() {
        // Utility class
    }

    // ═══════════════════════════════════════════════════════════════════
    // COLOR PALETTE - Brighter, more vanilla Hytale-appropriate
    // ═══════════════════════════════════════════════════════════════════

    /** Primary text color (white) */
    public static final String TEXT_PRIMARY = "#FFFFFF";

    /** Secondary text color (light blue-gray, good for stat labels) */
    public static final String TEXT_SECONDARY = "#D0DCEA";

    /** Muted text color (gray, for hints/details) */
    public static final String TEXT_MUTED = "#777788";

    /** Standard gray text (secondary labels, descriptions) */
    public static final String TEXT_GRAY = "#AAAAAA";

    /** Info text color (cyan, for informational highlights) */
    public static final String TEXT_INFO = "#55FFFF";

    /** Warning text color (yellow, for caution/warning highlights) */
    public static final String TEXT_WARNING = "#FFFF55";

    /** Orange text for warnings and countdowns */
    public static final String TEXT_ORANGE = "#FFAA00";

    /** Title/header color (gold) */
    public static final String TITLE_GOLD = "#FFD700";

    /** Positive modifier color (bright green) */
    public static final String POSITIVE = "#55FF55";

    /** Negative modifier color (bright red) */
    public static final String NEGATIVE = "#FF5555";

    /** Stat value color (light blue-gray) */
    public static final String STAT_VALUE = "#CCDDEE";

    /** Dark gray for subtle text and separators */
    public static final String DARK_GRAY = "#666666";

    // ═══════════════════════════════════════════════════════════════════
    // ELEMENT COLORS (softer tones for UI labels, based on ElementType)
    // ═══════════════════════════════════════════════════════════════════

    /** Fire element label color (warm red-orange) */
    public static final String ELEMENT_FIRE = "#FF7755";

    /** Water element label color (cool cyan) */
    public static final String ELEMENT_WATER = "#55CCEE";

    /** Lightning element label color (bright yellow) */
    public static final String ELEMENT_LIGHTNING = "#FFEE55";

    /** Earth element label color (warm brown-gold) */
    public static final String ELEMENT_EARTH = "#DDAA55";

    /** Wind element label color (soft green) */
    public static final String ELEMENT_WIND = "#77DD77";

    /** Void element label color (soft purple) */
    public static final String ELEMENT_VOID = "#BB77DD";

    // ═══════════════════════════════════════════════════════════════════
    // VALUE FORMATTING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Formats a stat value with appropriate color based on its sign.
     *
     * @param value The stat value
     * @param isPercent Whether to format as percentage
     * @return Formatted string (no HTML, just the formatted value)
     */
    public static String formatValue(float value, boolean isPercent) {
        String prefix = "";

        if (value > 0) {
            prefix = "+";
        }

        String formattedValue;
        if (isPercent) {
            formattedValue = prefix + formatNumber(value) + "%";
        } else {
            formattedValue = prefix + formatNumber(value);
        }

        return formattedValue;
    }

    /**
     * Gets the color for a value based on its sign.
     *
     * @param value The stat value
     * @return Hex color code
     */
    public static String getValueColor(float value) {
        if (value > 0) {
            return POSITIVE;
        } else if (value < 0) {
            return NEGATIVE;
        } else {
            return TEXT_MUTED;
        }
    }

    /**
     * Formats a number with up to 2 decimal places.
     * Trailing zeros are trimmed for clean display (6.30 → "6.3", 6.00 → "6").
     *
     * @param value The value to format
     * @return Formatted string
     */
    public static String formatNumber(float value) {
        // Whole numbers (no decimals)
        if (value == (int) value) {
            return String.valueOf((int) value);
        }

        // Format with 2 decimal places
        String formatted = String.format("%.2f", value);

        // Trim trailing zeros: "6.30" → "6.3", "6.00" → "6"
        if (formatted.contains(".")) {
            formatted = formatted.replaceAll("0+$", "");  // Remove trailing zeros
            formatted = formatted.replaceAll("\\.$", ""); // Remove trailing dot
        }

        return formatted;
    }

    /**
     * Formats a flat value (no sign prefix, just the number).
     *
     * @param value The value to format
     * @return Formatted string
     */
    public static String formatFlat(float value) {
        return formatNumber(value);
    }
}
