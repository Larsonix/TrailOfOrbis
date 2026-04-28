package io.github.larsonix.trailoforbis.util;

/**
 * Centralized level-based scaling for all RPG systems.
 *
 * <p>Uses a power-of-logarithm formula for smooth progression up to a configurable
 * transition level, then switches to asymptotic diminishing returns:
 *
 * <h3>Phase 1 (level 1 → transition)</h3>
 * <pre>
 * bonusPercent = 7.35 × log₁₀(level)^2.75
 * </pre>
 *
 * <h3>Phase 2 (above transition)</h3>
 * <pre>
 * ceiling = transitionMultiplier × maxMultiplierRatio
 * multiplier = ceiling - (ceiling - transitionMultiplier) × e^(-k × overshoot)
 * </pre>
 * Where {@code k} is auto-derived so the curve reaches ~63% of the way to ceiling
 * after {@code transitionLevel} additional levels.
 *
 * <p>This single formula is used by: mob stats, gear stats, damage scaling.
 *
 * <h3>Default Scaling Table (transition=100, maxRatio=2.0, decayDivisor=5)</h3>
 * <table>
 *   <tr><th>Level</th><th>Multiplier</th><th>Phase</th></tr>
 *   <tr><td>1</td><td>1.0×</td><td>Baseline</td></tr>
 *   <tr><td>20</td><td>~1.15×</td><td>Early</td></tr>
 *   <tr><td>100</td><td>~1.50×</td><td>Transition</td></tr>
 *   <tr><td>200</td><td>~1.80×</td><td>Diminishing</td></tr>
 *   <tr><td>500</td><td>~2.32×</td><td>Diminishing</td></tr>
 *   <tr><td>1,000</td><td>~2.76×</td><td>Diminishing</td></tr>
 *   <tr><td>2,000</td><td>~2.96×</td><td>Near ceiling</td></tr>
 * </table>
 */
public final class LevelScaling {

    /**
     * Coefficient controlling the overall scaling strength.
     * Higher values = more power per level.
     */
    private static final double COEFFICIENT = 7.35;

    /**
     * Exponent applied to the logarithm.
     * Higher values = steeper curve at high levels.
     */
    private static final double EXPONENT = 2.75;

    // Diminishing returns config (set via configure())
    private static int transitionLevel = 100;
    private static double maxMultiplierRatio = 2.0;
    private static double decayDivisor = 5.0;

    // Cached values derived from config (recomputed on configure())
    private static double transitionMultiplier;
    private static double ceiling;
    private static double decayRate;

    static {
        recomputeDerivedValues();
    }

    private LevelScaling() {
        // Utility class - no instantiation
    }

    /**
     * Configure the diminishing returns curve.
     *
     * <p>Must be called before any system reads scaling values (typically
     * during config loading, before mob/gear managers initialize).
     *
     * @param transitionLevel Level where diminishing returns begin (default: 100)
     * @param maxMultiplierRatio How much stronger the ceiling is vs transition value (default: 2.0).
     *     With transition at 1.5× and ratio 2.0, the ceiling is 3.0×.
     * @param decayDivisor Divisor for the post-transition decay rate (default: 5.0).
     *     Higher values slow the approach to ceiling, stretching meaningful progression
     *     to higher levels. With divisor=5 and transition=100, the curve reaches ~63%
     *     of the way to ceiling after 500 additional levels instead of 100.
     */
    public static void configure(int transitionLevel, double maxMultiplierRatio, double decayDivisor) {
        if (transitionLevel < 2) {
            transitionLevel = 2;
        }
        if (maxMultiplierRatio < 1.01) {
            maxMultiplierRatio = 1.01;
        }
        if (decayDivisor < 1.0) {
            decayDivisor = 1.0;
        }
        LevelScaling.transitionLevel = transitionLevel;
        LevelScaling.maxMultiplierRatio = maxMultiplierRatio;
        LevelScaling.decayDivisor = decayDivisor;
        recomputeDerivedValues();
    }

    /** @see #configure(int, double, double) */
    public static void configure(int transitionLevel, double maxMultiplierRatio) {
        configure(transitionLevel, maxMultiplierRatio, LevelScaling.decayDivisor);
    }

    private static void recomputeDerivedValues() {
        transitionMultiplier = 1.0 + baseFormulaBonusPercent(transitionLevel) / 100.0;
        ceiling = transitionMultiplier * maxMultiplierRatio;
        // k chosen so that at (transitionLevel × decayDivisor) levels past transition,
        // we reach ~63% of the way to ceiling. Higher decayDivisor = slower approach.
        decayRate = 1.0 / (transitionLevel * decayDivisor);
    }

    private static double baseFormulaBonusPercent(int level) {
        if (level <= 1) {
            return 0.0;
        }
        double logLevel = Math.log10(level);
        return COEFFICIENT * Math.pow(logLevel, EXPONENT);
    }

    /**
     * Below transition: {@code 7.35 * log10(level)^2.75}.
     * Above transition: asymptotic diminishing returns.
     *
     * @return 0 at level 1
     */
    public static double getBonusPercent(int level) {
        return (getMultiplier(level) - 1.0) * 100.0;
    }

    /** @return 1.0 at level 1, approaches ceiling at very high levels */
    public static double getMultiplier(int level) {
        if (level <= 1) {
            return 1.0;
        }
        if (level <= transitionLevel) {
            return 1.0 + baseFormulaBonusPercent(level) / 100.0;
        }
        // Asymptotic diminishing returns:
        // ceiling - (ceiling - transitionValue) × e^(-k × overshoot)
        double overshoot = level - transitionLevel;
        return ceiling - (ceiling - transitionMultiplier) * Math.exp(-decayRate * overshoot);
    }

    /** Returns the configured transition level. */
    public static int getTransitionLevel() {
        return transitionLevel;
    }

    /** Returns the configured max multiplier ratio. */
    public static double getMaxMultiplierRatio() {
        return maxMultiplierRatio;
    }

    /** Returns the computed ceiling multiplier. */
    public static double getCeiling() {
        return ceiling;
    }

    /** Returns the configured decay divisor. */
    public static double getDecayDivisor() {
        return decayDivisor;
    }

    static void resetDefaults() {
        transitionLevel = 100;
        maxMultiplierRatio = 2.0;
        decayDivisor = 5.0;
        recomputeDerivedValues();
    }
}
