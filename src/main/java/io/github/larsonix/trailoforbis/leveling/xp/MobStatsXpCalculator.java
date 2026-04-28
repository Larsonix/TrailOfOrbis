package io.github.larsonix.trailoforbis.leveling.xp;

import io.github.larsonix.trailoforbis.leveling.config.LevelingConfig;
import io.github.larsonix.trailoforbis.mobs.classification.MobClassificationConfig;
import io.github.larsonix.trailoforbis.mobs.classification.RPGMobClass;
import io.github.larsonix.trailoforbis.mobs.component.MobScalingComponent;
import io.github.larsonix.trailoforbis.mobs.model.MobStats;

import javax.annotation.Nonnull;

/**
 * Calculates XP rewards based on mob stats.
 *
 * <p>Uses the mob's level, total stat pool, RPG class,
 * and distance from origin to compute XP rewards.
 */
public class MobStatsXpCalculator implements XpCalculator {

    /** Global XP multiplier applied to all mob kills (tuning knob for overall XP rate) */
    private static final double GLOBAL_XP_MULTIPLIER = 1.2;

    /** XP multiplier bonus per distance level (0.028 = +2.8% per level) */
    private static final double DISTANCE_XP_PER_LEVEL = 0.028;

    private final LevelingConfig.XpGainConfig config;
    private final MobClassificationConfig classificationConfig;

    /**
     * Creates a new calculator with the given configuration.
     *
     * @param config XP gain configuration
     * @param classificationConfig Mob classification configuration
     */
    public MobStatsXpCalculator(@Nonnull LevelingConfig.XpGainConfig config,
                                @Nonnull MobClassificationConfig classificationConfig) {
        this.config = config;
        this.classificationConfig = classificationConfig;
    }

    @Override
    public long calculateMobKillXp(@Nonnull MobScalingComponent mobScaling) {
        MobStats stats = mobScaling.getStats();

        // Base XP from mob level
        double baseXp = stats.level() * config.getXpPerMobLevel();

        // Bonus from stat pool (health + damage + armor + other stats combined)
        double poolBonus = stats.totalPool() * config.getPoolMultiplier();

        // Tier multiplier from classification
        double tierMult = getTierMultiplier(mobScaling);

        // Distance XP multiplier - rewards exploring farther from spawn
        // distanceLevel is estimated mob level from distance (40 blocks per level)
        double distanceMult = 1.0 + (mobScaling.getDistanceLevel() * DISTANCE_XP_PER_LEVEL);

        // Calculate final XP
        long xp = (long) Math.ceil((baseXp + poolBonus) * tierMult * GLOBAL_XP_MULTIPLIER * distanceMult);

        // Ensure at least 1 XP
        return Math.max(1, xp);
    }

    /** Gets the tier multiplier based on mob type. */
    private double getTierMultiplier(@Nonnull MobScalingComponent mobScaling) {
        return classificationConfig.getXpMultiplier(mobScaling.getClassification());
    }

    /**
     * Calculates XP using raw values (for testing or custom calculations).
     *
     * @param mobLevel The mob's level
     * @param totalPool The mob's total stat pool
     * @param distanceLevel The mob's distance level (for XP bonus)
     * @param classification The mob's RPG class
     * @return The calculated XP
     */
    public long calculateXp(int mobLevel, double totalPool, int distanceLevel, RPGMobClass classification) {
        double baseXp = mobLevel * config.getXpPerMobLevel();
        double poolBonus = totalPool * config.getPoolMultiplier();

        double tierMult = classificationConfig.getXpMultiplier(classification);

        // Distance XP multiplier
        double distanceMult = 1.0 + (distanceLevel * DISTANCE_XP_PER_LEVEL);

        long xp = (long) Math.ceil((baseXp + poolBonus) * tierMult * GLOBAL_XP_MULTIPLIER * distanceMult);
        return Math.max(1, xp);
    }

    /** Gets the XP gain configuration. */
    @Nonnull
    public LevelingConfig.XpGainConfig getConfig() {
        return config;
    }

    /**
     * Gets base XP for unscaled mobs (chunk-loaded, safe zone, or non-hostile).
     *
     * <p>These mobs don't have MobScalingComponent, so we grant a flat base XP.
     * This ensures players still get some reward for killing any mob.
     *
     * @return Base XP for unscaled mobs (xpPerMobLevel × 2)
     */
    public long getBaseXpForUnscaledMob() {
        // Grant base XP equivalent to a level 2 mob with no pool
        return Math.max(1, (long) (config.getXpPerMobLevel() * 2));
    }

    /**
     * Calculates XP for a mob without MobScalingComponent using raw entity data.
     *
     * <p>This is the fallback calculation for chunk-loaded mobs or mobs that
     * spawned before the scaling system was ready.
     *
     * @param maxHealth Maximum health of the mob
     * @param distanceFromOrigin Distance from world origin (0, 0)
     * @param classification The mob's RPG class
     * @return Calculated XP reward
     */
    public long calculateXpFromRawStats(double maxHealth, double distanceFromOrigin, RPGMobClass classification) {
        // Estimate mob level from distance (100 blocks per level, minimum level 1)
        int estimatedLevel = Math.max(1, (int) (distanceFromOrigin / 100.0));

        // Distance level for XP bonus (40 blocks per level, matching DistanceBonusCalculator)
        int distanceLevel = (int) (distanceFromOrigin / 40.0);

        // Estimate stat pool from health
        // Baseline unscaled mob has 50 HP, each pool point adds ~2 HP
        double healthPool = Math.max(0, (maxHealth - 50.0) / 2.0);

        // Base XP from estimated level
        double baseXp = estimatedLevel * config.getXpPerMobLevel();

        // Pool bonus from estimated health pool
        double poolBonus = healthPool * config.getPoolMultiplier();

        // Tier multiplier
        double tierMult = classificationConfig.getXpMultiplier(classification);

        // Distance XP multiplier - rewards killing mobs farther from spawn
        double distanceMult = 1.0 + (distanceLevel * DISTANCE_XP_PER_LEVEL);

        // Calculate final XP
        long xp = (long) Math.ceil((baseXp + poolBonus) * tierMult * GLOBAL_XP_MULTIPLIER * distanceMult);

        // Ensure at least 1 XP
        return Math.max(1, xp);
    }
}
