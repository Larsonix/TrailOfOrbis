package io.github.larsonix.trailoforbis.leveling.formula;

/**
 * Power-law curve defining how many mobs a player should kill per level.
 *
 * <p>The curve is defined by three designer-facing parameters:
 * <ul>
 *   <li>{@code baseMobs} — mobs per level at level 1 (e.g., 3)</li>
 *   <li>{@code targetMobs} — mobs per level at {@code targetLevel} (e.g., 150)</li>
 *   <li>{@code targetLevel} — the reference level (e.g., 100)</li>
 * </ul>
 *
 * <p>The growth exponent is derived automatically:
 * <pre>
 * exponent = ln(targetMobs / baseMobs) / ln(targetLevel)
 * </pre>
 *
 * <p>Then for any level:
 * <pre>
 * mobsPerLevel(L) = baseMobs * max(1, L) ^ exponent
 * </pre>
 *
 * <p>Example with baseMobs=3, targetMobs=150, targetLevel=100:
 * <pre>
 * exponent = ln(50) / ln(100) ≈ 1.95
 * Level 1:   3 mobs
 * Level 5:   ~65 mobs
 * Level 10:  ~267 mobs  — wait, that's too many...
 * </pre>
 * Actually: exponent ≈ 1.95, so Level 10 = 3 * 10^1.95 ≈ 267 — the curve is steep.
 * With baseMobs=3, targetMobs=150, targetLevel=100:
 * exponent = ln(150/3) / ln(100) = ln(50)/ln(100) ≈ 3.912/4.605 ≈ 0.849
 * Level 1:   3 * 1^0.85 = 3
 * Level 10:  3 * 10^0.85 ≈ 21
 * Level 50:  3 * 50^0.85 ≈ 93
 * Level 100: 3 * 100^0.85 ≈ 150
 */
public class EffortCurve {

    private final double baseMobs;
    private final double exponent;

    /**
     * Creates an effort curve from designer-facing parameters.
     *
     * @param baseMobs    Mobs per level at level 1 (must be > 0)
     * @param targetMobs  Mobs per level at targetLevel (must be > baseMobs)
     * @param targetLevel Reference level for the target (must be >= 2)
     */
    public EffortCurve(double baseMobs, double targetMobs, int targetLevel) {
        if (baseMobs <= 0) {
            throw new IllegalArgumentException("baseMobs must be > 0: " + baseMobs);
        }
        if (targetMobs <= baseMobs) {
            throw new IllegalArgumentException(
                "targetMobs must be > baseMobs: targetMobs=" + targetMobs + ", baseMobs=" + baseMobs);
        }
        if (targetLevel < 2) {
            throw new IllegalArgumentException("targetLevel must be >= 2: " + targetLevel);
        }

        this.baseMobs = baseMobs;
        this.exponent = Math.log(targetMobs / baseMobs) / Math.log(targetLevel);
    }

    /**
     * Returns how many mobs a player should kill to advance from this level.
     *
     * @param level The current level (1+)
     * @return Estimated mobs to kill at this level
     */
    public double getMobsPerLevel(int level) {
        return baseMobs * Math.pow(Math.max(1, level), exponent);
    }

    /**
     * Returns the base mobs value (mobs at level 1).
     */
    public double getBaseMobs() {
        return baseMobs;
    }

    /**
     * Returns the derived growth exponent.
     */
    public double getExponent() {
        return exponent;
    }

    @Override
    public String toString() {
        return String.format("EffortCurve{baseMobs=%.1f, exponent=%.4f}", baseMobs, exponent);
    }
}
