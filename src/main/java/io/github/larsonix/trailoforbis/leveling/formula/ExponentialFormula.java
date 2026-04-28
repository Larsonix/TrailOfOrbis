package io.github.larsonix.trailoforbis.leveling.formula;

/**
 * Exponential XP formula: cumulative XP = baseXp × (level-1)^exponent
 *
 * <p>The XP required between levels grows exponentially, making early levels
 * quick to achieve while later levels require significantly more XP.
 *
 * <p>Formula details:
 * <ul>
 *   <li>Level 1: 0 XP (start)</li>
 *   <li>Level 2: baseXp × 1^exponent = baseXp</li>
 *   <li>Level 3: baseXp × 2^exponent</li>
 *   <li>Level N: baseXp × (N-1)^exponent</li>
 * </ul>
 *
 * <p>Example with baseXp=100, exponent=1.7:
 * <pre>
 * Level 1:  0 XP (start)
 * Level 2:  100 XP
 * Level 5:  713 XP
 * Level 10: 5,179 XP
 * Level 20: 35,566 XP
 * Level 50: 339,988 XP
 * </pre>
 *
 * <p>Uses pre-computed lookup table for O(1) level lookups.
 */
public class ExponentialFormula implements LevelFormula {

    private final double baseXp;
    private final double exponent;
    private final int maxLevel;

    // Pre-computed XP thresholds for each level
    private final long[] xpTable;

    /**
     * Creates an exponential formula.
     *
     * @param baseXp Base XP multiplier (e.g., 100.0)
     * @param exponent Growth exponent (e.g., 1.7)
     * @param maxLevel Maximum level (e.g., 100)
     */
    public ExponentialFormula(double baseXp, double exponent, int maxLevel) {
        if (baseXp <= 0) {
            throw new IllegalArgumentException("baseXp must be positive: " + baseXp);
        }
        if (exponent <= 0) {
            throw new IllegalArgumentException("exponent must be positive: " + exponent);
        }
        if (maxLevel < 1) {
            throw new IllegalArgumentException("maxLevel must be at least 1: " + maxLevel);
        }

        this.baseXp = baseXp;
        this.exponent = exponent;
        this.maxLevel = maxLevel;

        // Pre-compute XP table
        this.xpTable = computeXpTable();
    }

    /**
     * Creates a formula with default values (100 base, 1.7 exponent, 100 max).
     */
    public ExponentialFormula() {
        this(100.0, 1.7, 100);
    }

    @Override
    public long getXpForLevel(int level) {
        if (level <= 1) {
            return 0;
        }
        if (level > maxLevel) {
            level = maxLevel;
        }
        return xpTable[level];
    }

    @Override
    public long getXpBetweenLevels(int level) {
        if (level < 1) {
            level = 1;
        }
        // Return 0 if the next level would be at or beyond max level
        // Note: This means level maxLevel-1 also returns 0 (by design - reaching maxLevel is the cap)
        if (level + 1 >= maxLevel) {
            return 0;
        }
        return xpTable[level + 1] - xpTable[level];
    }

    @Override
    public int getLevelForXp(long xp) {
        if (xp <= 0) {
            return 1;
        }

        // Binary search for the highest level whose threshold <= xp
        // xpTable[L] = XP needed to reach level L (for L >= 2)
        // xpTable[1] = 0, xpTable[2] = baseXp, etc.
        int low = 1;
        int high = maxLevel;

        while (low < high) {
            int mid = (low + high + 1) / 2;
            if (xp >= xpTable[mid]) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }

        return low;
    }

    @Override
    public int getMaxLevel() {
        return maxLevel;
    }

    /** Gets the base XP multiplier. */
    public double getBaseXp() {
        return baseXp;
    }

    /** Gets the growth exponent. */
    public double getExponent() {
        return exponent;
    }

    // ═══════════════════════════════════════════════════════════════════
    // PRIVATE METHODS
    // ═══════════════════════════════════════════════════════════════════

    private long[] computeXpTable() {
        // Index 0 unused, index 1 = level 1, etc.
        long[] table = new long[maxLevel + 1];
        table[1] = 0; // Level 1 starts at 0 XP

        for (int level = 2; level <= maxLevel; level++) {
            // Cumulative XP = baseXp * (level - 1)^exponent
            double value = baseXp * Math.pow(level - 1, exponent);
            table[level] = (long) Math.ceil(value);
        }

        return table;
    }

    @Override
    public String toString() {
        return String.format("ExponentialFormula{baseXp=%.1f, exponent=%.2f, maxLevel=%d}",
            baseXp, exponent, maxLevel);
    }
}
