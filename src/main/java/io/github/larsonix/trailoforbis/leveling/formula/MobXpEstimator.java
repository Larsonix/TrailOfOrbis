package io.github.larsonix.trailoforbis.leveling.formula;

import io.github.larsonix.trailoforbis.util.LevelScaling;

/**
 * Estimates the XP a player would receive from killing one same-level hostile mob.
 *
 * <p>This mirrors the actual XP calculation pipeline without importing mob classes:
 * <ol>
 *   <li>{@link io.github.larsonix.trailoforbis.mobs.stats.MobStatGenerator} — generates the
 *       mob's total stat pool from level, progressive scaling, and LevelScaling</li>
 *   <li>{@link io.github.larsonix.trailoforbis.leveling.xp.MobStatsXpCalculator} — converts
 *       level + pool into XP using xpPerMobLevel and poolMultiplier</li>
 * </ol>
 *
 * <p>The estimator assumes:
 * <ul>
 *   <li>HOSTILE classification (1.0× XP multiplier — baseline)</li>
 *   <li>Zero distance bonus (mob spawned near the player)</li>
 *   <li>No boss/elite multiplier</li>
 *   <li>Global XP multiplier of 1.2× (matches {@code MobStatsXpCalculator.GLOBAL_XP_MULTIPLIER})</li>
 * </ul>
 *
 * <p>This is intentionally a conservative estimate — actual XP may be higher due to
 * distance bonuses, elite/boss multipliers, and classification multipliers.
 */
public class MobXpEstimator {

    /** Matches MobStatsXpCalculator.GLOBAL_XP_MULTIPLIER */
    private static final double GLOBAL_XP_MULTIPLIER = 1.2;

    private final double xpPerMobLevel;
    private final double poolMultiplier;
    private final double pointsPerLevel;
    private final boolean progressiveScalingEnabled;
    private final int softCapLevel;
    private final double minScalingFactor;

    /**
     * Creates an estimator with parameters sourced from existing configs.
     *
     * @param xpPerMobLevel            From leveling.yml xp_gain.xp_per_mob_level
     * @param poolMultiplier           From leveling.yml xp_gain.pool_multiplier
     * @param pointsPerLevel           From mob-stat-pool.yml points_per_level
     * @param progressiveScalingEnabled From mob-stat-pool.yml progressive_scaling.enabled
     * @param softCapLevel             From mob-stat-pool.yml progressive_scaling.soft_cap_level
     * @param minScalingFactor         From mob-stat-pool.yml progressive_scaling.min_scaling_factor
     */
    public MobXpEstimator(
            double xpPerMobLevel,
            double poolMultiplier,
            double pointsPerLevel,
            boolean progressiveScalingEnabled,
            int softCapLevel,
            double minScalingFactor
    ) {
        this.xpPerMobLevel = xpPerMobLevel;
        this.poolMultiplier = poolMultiplier;
        this.pointsPerLevel = pointsPerLevel;
        this.progressiveScalingEnabled = progressiveScalingEnabled;
        this.softCapLevel = softCapLevel;
        this.minScalingFactor = minScalingFactor;
    }

    /**
     * Estimates XP from killing one same-level HOSTILE mob at the given level.
     *
     * <p>Mirrors MobStatGenerator.generate() → MobStatsXpCalculator.calculateMobKillXp():
     * <pre>
     * scalingFactor = progressive scaling ramp (minFactor→1.0 over softCapLevel)
     * effectiveLevel = (LevelScaling.getMultiplier(level) - 1.0) * transitionLevel + 1.0
     * levelPool = effectiveLevel * pointsPerLevel * scalingFactor
     * totalPool = levelPool * LevelScaling.getMultiplier(level)
     * xp = ceil((level * xpPerMobLevel + totalPool * poolMultiplier) * 1.2)
     * </pre>
     *
     * @param level The mob/player level
     * @return Estimated XP (always >= 1)
     */
    public long estimateXpPerMob(int level) {
        if (level < 1) {
            level = 1;
        }

        // Mirror MobStatPoolConfig.calculateScalingFactor()
        double scalingFactor = calculateScalingFactor(level);

        // Mirror MobStatGenerator.generate() lines 32-39
        double expMultiplier = LevelScaling.getMultiplier(level);
        double effectiveLevel = (expMultiplier - 1.0) * LevelScaling.getTransitionLevel() + 1.0;
        double levelPool = effectiveLevel * pointsPerLevel * scalingFactor;
        double totalPool = levelPool * expMultiplier;

        // Mirror MobStatsXpCalculator.calculateMobKillXp() lines 45-58
        // HOSTILE classification = 1.0× multiplier, distance = 0
        double baseXp = level * xpPerMobLevel;
        double poolBonus = totalPool * poolMultiplier;

        long xp = (long) Math.ceil((baseXp + poolBonus) * GLOBAL_XP_MULTIPLIER);
        return Math.max(1, xp);
    }

    /**
     * Mirrors MobStatPoolConfig.calculateScalingFactor() — progressive scaling ramp.
     */
    private double calculateScalingFactor(int level) {
        if (!progressiveScalingEnabled) {
            return 1.0;
        }
        if (level >= softCapLevel) {
            return 1.0;
        }
        double progress = (double) level / softCapLevel;
        return minScalingFactor + (1.0 - minScalingFactor) * progress;
    }

    @Override
    public String toString() {
        return String.format(
            "MobXpEstimator{xpPerMobLevel=%.1f, poolMultiplier=%.2f, pointsPerLevel=%.1f, " +
            "progressive=%s, softCap=%d, minFactor=%.2f}",
            xpPerMobLevel, poolMultiplier, pointsPerLevel,
            progressiveScalingEnabled, softCapLevel, minScalingFactor);
    }
}
