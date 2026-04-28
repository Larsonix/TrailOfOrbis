package io.github.larsonix.trailoforbis.leveling.formula;

/**
 * Effort-based XP formula: XP requirements derived from "mobs per level" curve.
 *
 * <p>Instead of defining XP thresholds directly, this formula defines the
 * <em>effort</em> (number of mob kills) to advance each level, then derives
 * XP thresholds by multiplying effort by estimated XP per mob.
 *
 * <p>The effort curve is a power-law: {@code mobs(L) = baseMobs × L^exponent},
 * where the exponent is derived from designer-facing parameters (base_mobs,
 * target_mobs, target_level). See {@link EffortCurve}.
 *
 * <p>This approach has a key advantage: if mob XP rates change (via xp_gain
 * config, mob-stat-pool config, or LevelScaling), XP requirements auto-adjust
 * to maintain the intended "kills per level" experience.
 *
 * <p>Uses pre-computed lookup table for O(1) level lookups, identical to
 * {@link ExponentialFormula}.
 */
public class EffortBasedFormula implements LevelFormula {

    private final EffortCurve curve;
    private final MobXpEstimator estimator;
    private final int maxLevel;

    // Pre-computed cumulative XP thresholds for each level
    private final long[] xpTable;

    /**
     * Creates an effort-based formula.
     *
     * @param curve     The effort curve defining mobs per level
     * @param estimator The mob XP estimator
     * @param maxLevel  Maximum level (1+)
     */
    public EffortBasedFormula(EffortCurve curve, MobXpEstimator estimator, int maxLevel) {
        if (curve == null) {
            throw new IllegalArgumentException("curve must not be null");
        }
        if (estimator == null) {
            throw new IllegalArgumentException("estimator must not be null");
        }
        if (maxLevel < 1) {
            throw new IllegalArgumentException("maxLevel must be at least 1: " + maxLevel);
        }

        this.curve = curve;
        this.estimator = estimator;
        this.maxLevel = maxLevel;

        this.xpTable = computeXpTable();
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

    /**
     * Gets the effort curve used by this formula.
     */
    public EffortCurve getCurve() {
        return curve;
    }

    /**
     * Gets the mob XP estimator used by this formula.
     */
    public MobXpEstimator getEstimator() {
        return estimator;
    }

    // ═══════════════════════════════════════════════════════════════════
    // PRIVATE METHODS
    // ═══════════════════════════════════════════════════════════════════

    private long[] computeXpTable() {
        long[] table = new long[maxLevel + 1];
        table[1] = 0; // Level 1 starts at 0 XP

        for (int level = 2; level <= maxLevel; level++) {
            // How many mobs to kill to advance FROM level-1
            double mobs = curve.getMobsPerLevel(level - 1);

            // How much XP does one mob at level-1 give?
            long xpPerMob = estimator.estimateXpPerMob(level - 1);

            // XP for this level transition
            long xpForLevel = Math.max(1, (long) Math.ceil(mobs * xpPerMob));

            // Cumulative XP with overflow protection
            long cumulative = table[level - 1] + xpForLevel;
            if (cumulative < table[level - 1]) {
                // Overflow — cap at Long.MAX_VALUE
                cumulative = Long.MAX_VALUE;
            }
            table[level] = cumulative;

            // Once we hit the cap, fill the rest
            if (cumulative == Long.MAX_VALUE) {
                for (int remaining = level + 1; remaining <= maxLevel; remaining++) {
                    table[remaining] = Long.MAX_VALUE;
                }
                break;
            }
        }

        return table;
    }

    @Override
    public String toString() {
        return String.format("EffortBasedFormula{curve=%s, maxLevel=%d}", curve, maxLevel);
    }
}
