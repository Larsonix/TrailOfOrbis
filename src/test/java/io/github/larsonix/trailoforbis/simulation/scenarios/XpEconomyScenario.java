package io.github.larsonix.trailoforbis.simulation.scenarios;

import io.github.larsonix.trailoforbis.leveling.config.LevelingConfig;
import io.github.larsonix.trailoforbis.leveling.formula.EffortBasedFormula;
import io.github.larsonix.trailoforbis.leveling.formula.EffortCurve;
import io.github.larsonix.trailoforbis.leveling.formula.LevelFormula;
import io.github.larsonix.trailoforbis.leveling.formula.MobXpEstimator;
import io.github.larsonix.trailoforbis.mobs.stats.MobStatPoolConfig;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Models XP economy: how many kills and how much time to level up,
 * comparing dungeon grinding vs open-world farming.
 *
 * <p>Output: xp_economy.csv with XP requirements and efficiency ratios.
 */
public final class XpEconomyScenario {

    /** Mob XP formula constants from leveling.yml. */
    private static final double XP_PER_MOB_LEVEL = 5.0;
    private static final double POOL_MULTIPLIER = 0.1;
    private static final double GLOBAL_XP_MULTIPLIER = 1.2;

    /** Classification multipliers (from mob-classification.yml). */
    private static final double HOSTILE_MULT = 1.0;
    private static final double ELITE_MULT = 1.5;
    private static final double BOSS_MULT = 5.0;

    /** Dungeon assumptions. */
    private static final double DUNGEON_ELITE_RATIO = 0.20; // 20% of dungeon mobs are elite
    private static final double DUNGEON_BOSS_RATIO = 0.02;  // ~1 boss per dungeon run
    private static final double DUNGEON_MOB_DENSITY_MULT = 2.5; // mobs encountered per minute in DG

    /** Open-world assumptions. */
    private static final double OPENWORLD_MOB_DENSITY_MULT = 1.0; // baseline

    /** Estimated seconds per kill (will be replaced by PowerCurve TTK data). */
    private static final double EST_SECONDS_PER_KILL = 4.0;

    private final LevelingConfig levelingConfig;
    private final MobStatPoolConfig mobConfig;

    public XpEconomyScenario(@Nonnull LevelingConfig levelingConfig,
                              @Nonnull MobStatPoolConfig mobConfig) {
        this.levelingConfig = levelingConfig;
        this.mobConfig = mobConfig;
    }

    /**
     * Runs the XP economy simulation and writes results to CSV.
     */
    public int run(@Nonnull Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        Path csvPath = outputDir.resolve("xp_economy.csv");

        // Build level formula from config
        LevelFormula formula = buildLevelFormula();

        int totalPoints = 0;

        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(csvPath))) {
            writer.println("level,xp_to_next,hostile_xp,elite_xp,boss_xp,"
                    + "kills_hostile,kills_elite,kills_boss,"
                    + "openworld_kills_needed,dungeon_kills_needed,"
                    + "openworld_xp_per_min,dungeon_xp_per_min,efficiency_ratio");

            List<Integer> levels = buildLevelList();

            for (int level : levels) {
                long xpToNext = formula.getXpBetweenLevels(level);

                // XP per mob at this level
                double mobPool = estimateMobPool(level);
                double hostileXp = calculateMobXp(level, mobPool, HOSTILE_MULT);
                double eliteXp = calculateMobXp(level, mobPool, ELITE_MULT);
                double bossXp = calculateMobXp(level, mobPool, BOSS_MULT);

                // Kills needed
                double killsHostile = xpToNext / Math.max(1, hostileXp);
                double killsElite = xpToNext / Math.max(1, eliteXp);
                double killsBoss = xpToNext / Math.max(1, bossXp);

                // Open-world: all hostile mobs
                double owKills = killsHostile;
                double owXpPerMin = (hostileXp * OPENWORLD_MOB_DENSITY_MULT * 60.0) / EST_SECONDS_PER_KILL;

                // Dungeon: mix of hostile (78%) + elite (20%) + boss (2%)
                double avgDungeonXp = hostileXp * 0.78 + eliteXp * DUNGEON_ELITE_RATIO + bossXp * DUNGEON_BOSS_RATIO;
                double dgKills = xpToNext / Math.max(1, avgDungeonXp);
                double dgXpPerMin = (avgDungeonXp * DUNGEON_MOB_DENSITY_MULT * 60.0) / EST_SECONDS_PER_KILL;

                double efficiencyRatio = dgXpPerMin / Math.max(1, owXpPerMin);

                writer.printf("%d,%d,%.1f,%.1f,%.1f,%.0f,%.0f,%.0f,%.0f,%.0f,%.1f,%.1f,%.2f%n",
                        level, xpToNext,
                        hostileXp, eliteXp, bossXp,
                        killsHostile, killsElite, killsBoss,
                        owKills, dgKills,
                        owXpPerMin, dgXpPerMin,
                        efficiencyRatio);

                totalPoints++;
            }
        }

        System.out.printf("  [XpEconomy] Wrote %d data points to %s%n", totalPoints, csvPath);
        return totalPoints;
    }

    // =========================================================================
    // Mob XP Calculation (mirrors MobStatsXpCalculator)
    // =========================================================================

    /**
     * Calculates XP from killing a mob at the given level.
     * Mirrors MobStatsXpCalculator.calculateMobKillXp().
     */
    private double calculateMobXp(int mobLevel, double totalPool, double classificationMult) {
        double baseXp = mobLevel * XP_PER_MOB_LEVEL;
        double poolBonus = totalPool * POOL_MULTIPLIER;
        return Math.max(1, Math.ceil((baseXp + poolBonus) * classificationMult * GLOBAL_XP_MULTIPLIER));
    }

    /**
     * Estimates the total stat pool for a same-level mob.
     * Mirrors MobXpEstimator logic.
     */
    private double estimateMobPool(int level) {
        double pointsPerLevel = mobConfig.getPointsPerLevel();

        // Progressive scaling ramp
        int softCap = mobConfig.getProgressiveScalingSoftCapLevel();
        double minFactor = mobConfig.getProgressiveScalingMinFactor();
        double scalingFactor;
        if (level >= softCap) {
            scalingFactor = 1.0;
        } else {
            scalingFactor = minFactor + (1.0 - minFactor) * ((double) level / softCap);
        }

        double expMultiplier = io.github.larsonix.trailoforbis.util.LevelScaling.getMultiplier(level);
        double effectiveLevel = (expMultiplier - 1.0)
                * io.github.larsonix.trailoforbis.util.LevelScaling.getTransitionLevel() + 1.0;
        double levelPool = effectiveLevel * pointsPerLevel * scalingFactor;
        return levelPool * expMultiplier;
    }

    // =========================================================================
    // Level Formula Construction
    // =========================================================================

    private LevelFormula buildLevelFormula() {
        LevelingConfig.FormulaConfig formulaConfig = levelingConfig.getFormula();
        int maxLevel = formulaConfig.getMaxLevel();

        LevelingConfig.EffortConfig effort = formulaConfig.getEffort();
        EffortCurve curve = new EffortCurve(
                effort.getBaseMobs(),
                effort.getTargetMobs(),
                effort.getTargetLevel()
        );

        MobXpEstimator estimator = new MobXpEstimator(
                levelingConfig.getXpGain().getXpPerMobLevel(),
                levelingConfig.getXpGain().getPoolMultiplier(),
                mobConfig.getPointsPerLevel(),
                mobConfig.isProgressiveScalingEnabled(),
                mobConfig.getProgressiveScalingSoftCapLevel(),
                mobConfig.getProgressiveScalingMinFactor()
        );

        return new EffortBasedFormula(curve, estimator, maxLevel);
    }

    private List<Integer> buildLevelList() {
        List<Integer> levels = new ArrayList<>();
        // Every level 1-100
        for (int l = 1; l <= 100; l++) levels.add(l);
        // Every 25 from 125-500
        for (int l = 125; l <= 500; l += 25) levels.add(l);
        // Every 50 from 550-1000
        for (int l = 550; l <= 1000; l += 50) levels.add(l);
        return levels;
    }
}
