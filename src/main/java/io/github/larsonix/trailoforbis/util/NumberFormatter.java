package io.github.larsonix.trailoforbis.util;

import javax.annotation.Nonnull;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Centralized number formatting for all player-visible stat displays.
 *
 * <p>Provides consistent formatting across StatsPage, chat commands,
 * combat log, death recap, tooltips, and sanctum UIs. Each method
 * targets a specific stat category with appropriate precision.
 *
 * <h3>Formatting rules by category:</h3>
 * <table>
 *   <tr><th>Category</th><th>Method</th><th>Examples</th></tr>
 *   <tr><td>Flat large (HP, mana, armor, ES, damage)</td><td>{@link #flat}</td><td>"1,234", "45"</td></tr>
 *   <tr><td>Flat small (regen rates, leech)</td><td>{@link #smallFlat}</td><td>"4.5", "0.2"</td></tr>
 *   <tr><td>Percentage</td><td>{@link #percent}</td><td>"12.5%", "0.0%"</td></tr>
 *   <tr><td>Multiplier</td><td>{@link #multiplier}</td><td>"x1.50", "x2.75"</td></tr>
 *   <tr><td>Time (delays)</td><td>{@link #time}</td><td>"3.0s", "1.5s"</td></tr>
 *   <tr><td>Integer-only (levels, counts)</td><td>{@link #integer}</td><td>"50", "12"</td></tr>
 *   <tr><td>Signed values</td><td>{@link #signed}</td><td>"+12.5", "-3.0"</td></tr>
 * </table>
 */
public final class NumberFormatter {

    private static final DecimalFormat COMMA_FORMAT;
    private static final DecimalFormat ONE_DECIMAL;
    private static final DecimalFormat TWO_DECIMAL;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        COMMA_FORMAT = new DecimalFormat("#,##0", symbols);
        ONE_DECIMAL = new DecimalFormat("0.0", symbols);
        TWO_DECIMAL = new DecimalFormat("0.00", symbols);
    }

    private NumberFormatter() {
        // Utility class
    }

    // ═══════════════════════════════════════════════════════════════════
    // PRIMARY FORMAT METHODS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Formats a large flat value (HP, mana, armor, ES, damage totals).
     *
     * <p>Integer with comma separator for values >= 1, no decimals.
     * Values that are essentially whole numbers are shown without decimals.
     *
     * <p>Examples: "1,234", "45", "0"
     *
     * @param value The value to format
     * @return Formatted string
     */
    @Nonnull
    public static String flat(float value) {
        return COMMA_FORMAT.format(Math.round(value));
    }

    /**
     * Formats a small flat value (regen rates, leech amounts, small bonuses).
     *
     * <p>One decimal place, trailing zero preserved for consistency.
     * Whole numbers still show ".0" to indicate this is a rate/small value.
     *
     * <p>Examples: "4.5", "0.2", "12.0"
     *
     * @param value The value to format
     * @return Formatted string
     */
    @Nonnull
    public static String smallFlat(float value) {
        return ONE_DECIMAL.format(value);
    }

    /**
     * Formats a percentage value with % suffix.
     *
     * <p>One decimal place + "%". Always shows one decimal for readability.
     *
     * <p>Examples: "12.5%", "0.0%", "100.0%"
     *
     * @param value The percentage value (e.g., 12.5 for 12.5%)
     * @return Formatted string with % suffix
     */
    @Nonnull
    public static String percent(float value) {
        return ONE_DECIMAL.format(value) + "%";
    }

    /**
     * Formats a regen rate with +sign and /s suffix.
     *
     * <p>One decimal + "/s". Positive values get "+" prefix.
     *
     * <p>Examples: "+4.5/s", "+0.2/s", "-1.0/s"
     *
     * @param value The regen rate per second
     * @return Formatted string with sign and /s suffix
     */
    @Nonnull
    public static String regen(float value) {
        String prefix = value >= 0 ? "+" : "";
        return prefix + ONE_DECIMAL.format(value) + "/s";
    }

    /**
     * Formats a multiplier value with x prefix.
     *
     * <p>Two decimal places for precision.
     *
     * <p>Examples: "x1.50", "x2.75", "x0.33"
     *
     * @param value The multiplier value
     * @return Formatted string with x prefix
     */
    @Nonnull
    public static String multiplier(float value) {
        return "x" + TWO_DECIMAL.format(value);
    }

    /**
     * Formats a time/duration value with s suffix.
     *
     * <p>One decimal + "s".
     *
     * <p>Examples: "3.0s", "1.5s", "0.3s"
     *
     * @param value The time in seconds
     * @return Formatted string with s suffix
     */
    @Nonnull
    public static String time(float value) {
        return ONE_DECIMAL.format(value) + "s";
    }

    /**
     * Formats an integer value (levels, counts, points).
     *
     * <p>No decimals, no commas. For pure integer display.
     *
     * <p>Examples: "50", "12", "0"
     *
     * @param value The integer value
     * @return Formatted string
     */
    @Nonnull
    public static String integer(int value) {
        return String.valueOf(value);
    }

    /**
     * Formats a signed value with explicit +/- prefix.
     *
     * <p>One decimal place. Positive values get "+" prefix.
     * Used for stat changes, bonuses, and modifiers.
     *
     * <p>Examples: "+12.5", "-3.0", "+0.0"
     *
     * @param value The value to format
     * @return Formatted string with sign prefix
     */
    @Nonnull
    public static String signed(float value) {
        String prefix = value >= 0 ? "+" : "";
        return prefix + ONE_DECIMAL.format(value);
    }

    /**
     * Formats a signed percentage with explicit +/- prefix and % suffix.
     *
     * <p>One decimal place. Positive values get "+" prefix.
     *
     * <p>Examples: "+12.5%", "-3.0%", "+0.0%"
     *
     * @param value The percentage value
     * @return Formatted string with sign and % suffix
     */
    @Nonnull
    public static String signedPercent(float value) {
        String prefix = value >= 0 ? "+" : "";
        return prefix + ONE_DECIMAL.format(value) + "%";
    }

    // ═══════════════════════════════════════════════════════════════════
    // ADAPTIVE FORMAT METHODS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Formats a flat value adaptively: uses comma-separated integer for
     * large values (>= 50), one decimal for small values (< 50).
     *
     * <p>Useful when the caller doesn't know the magnitude at compile time.
     *
     * @param value The value to format
     * @return Formatted string
     */
    @Nonnull
    public static String autoFlat(float value) {
        if (Math.abs(value) >= 50) {
            return flat(value);
        }
        return smallFlat(value);
    }

    /**
     * Formats a number with up to 2 decimal places, trimming trailing zeros.
     *
     * <p>This is a compatibility method matching the old RPGStyles.formatNumber behavior.
     * Prefer the specific category methods for new code.
     *
     * <p>Examples: "6.3", "6", "0.25"
     *
     * @param value The value to format
     * @return Formatted string with trailing zeros trimmed
     */
    @Nonnull
    public static String trimmed(float value) {
        if (value == (int) value) {
            return String.valueOf((int) value);
        }
        String formatted = TWO_DECIMAL.format(value);
        // Trim trailing zeros: "6.30" → "6.3", "6.00" → "6"
        if (formatted.contains(".")) {
            formatted = formatted.replaceAll("0+$", "");
            formatted = formatted.replaceAll("\\.$", "");
        }
        return formatted;
    }
}
