package io.github.larsonix.trailoforbis.leveling.formula;

/**
 * Interface for XP-to-level calculation formulas.
 *
 * <p>Implementations define how XP thresholds scale with level.
 * The formula determines:
 * <ul>
 *   <li>How much XP is needed to reach each level</li>
 *   <li>What level a player has based on their XP</li>
 * </ul>
 *
 * <p>Contract:
 * <ul>
 *   <li>Level 1 always requires 0 XP</li>
 *   <li>XP requirements must be monotonically increasing</li>
 *   <li>Levels must be within [1, maxLevel]</li>
 * </ul>
 *
 * <p>Example implementations:
 * <ul>
 *   <li>{@link ExponentialFormula}: XP = baseXp × (level-1)^exponent</li>
 * </ul>
 */
public interface LevelFormula {

    /**
     * Gets the total cumulative XP required to reach a specific level.
     *
     * <p>For level 1, always returns 0.
     * For level 2+, returns the sum of XP for all previous levels.
     *
     * @param level The target level (1+)
     * @return The cumulative XP required (0 for level 1)
     */
    long getXpForLevel(int level);

    /**
     * Gets the XP required to advance from one level to the next.
     *
     * <p>This is the "delta" XP needed between levels.
     *
     * @param level The current level
     * @return XP required to advance from level to level+1
     */
    long getXpBetweenLevels(int level);

    /**
     * Calculates the level based on total XP.
     *
     * <p>The returned level is the highest level whose XP threshold
     * is less than or equal to the given XP.
     *
     * @param xp The total XP amount
     * @return The corresponding level (1 to maxLevel)
     */
    int getLevelForXp(long xp);

    /**
     * Gets the maximum level supported by this formula.
     *
     * @return The maximum level
     */
    int getMaxLevel();

    /**
     * Gets progress within the current level as a percentage.
     *
     * @param xp The total XP amount
     * @return Progress between 0.0 (just reached level) and 1.0 (about to level up)
     */
    default float getLevelProgress(long xp) {
        int level = getLevelForXp(xp);
        if (level >= getMaxLevel()) {
            return 1.0f;
        }

        long currentLevelXp = getXpForLevel(level);
        long nextLevelXp = getXpForLevel(level + 1);
        long xpIntoLevel = xp - currentLevelXp;
        long xpForLevel = nextLevelXp - currentLevelXp;

        if (xpForLevel <= 0) {
            return 1.0f;
        }

        return Math.min(1.0f, (float) xpIntoLevel / xpForLevel);
    }

    /**
     * Gets the XP remaining until the next level.
     *
     * @param xp The total XP amount
     * @return XP needed until next level, or 0 if at max level
     */
    default long getXpToNextLevel(long xp) {
        int level = getLevelForXp(xp);
        if (level >= getMaxLevel()) {
            return 0;
        }

        long nextLevelXp = getXpForLevel(level + 1);
        return Math.max(0, nextLevelXp - xp);
    }
}
