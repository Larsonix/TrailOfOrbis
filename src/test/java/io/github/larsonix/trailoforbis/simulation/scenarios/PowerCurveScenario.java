package io.github.larsonix.trailoforbis.simulation.scenarios;

import io.github.larsonix.trailoforbis.attributes.BaseStats;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.mobs.stats.MobStatGenerator;
import io.github.larsonix.trailoforbis.mobs.stats.MobStatPoolConfig;
import io.github.larsonix.trailoforbis.mobs.stats.MobStatProfile;
import io.github.larsonix.trailoforbis.simulation.builds.BuildArchetype;
import io.github.larsonix.trailoforbis.simulation.builds.BuildFactory;
import io.github.larsonix.trailoforbis.simulation.builds.BuildFactory.VirtualBuild;
import io.github.larsonix.trailoforbis.simulation.core.CombatSimulator;
import io.github.larsonix.trailoforbis.simulation.core.CombatSimulator.CombatResult;
import io.github.larsonix.trailoforbis.simulation.core.MobHpFormula;
import io.github.larsonix.trailoforbis.simulation.core.StatPipeline;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Simulates DPS, EHP, TTK, TTD, and survivability for every build archetype
 * across levels 1-1000.
 *
 * <p>Output: power_curves.csv with one row per (level, build) combination.
 */
public final class PowerCurveScenario {

    private static final int COMBAT_ITERATIONS = 50;
    private static final long MOB_SEED = 42L; // Deterministic mob generation

    private final StatPipeline pipeline;
    private final BuildFactory buildFactory;
    private final java.util.List<BuildArchetype> archetypes;
    private final CombatSimulator combatSim;
    private final MobStatGenerator mobGenerator;
    private final MobHpFormula hpFormula;

    public PowerCurveScenario(
            @Nonnull StatPipeline pipeline,
            @Nonnull BuildFactory buildFactory,
            @Nonnull java.util.List<BuildArchetype> archetypes,
            @Nonnull MobStatPoolConfig mobConfig,
            @Nonnull io.github.larsonix.trailoforbis.simulation.core.AvoidanceModel avoidanceModel) {
        this.pipeline = pipeline;
        this.buildFactory = buildFactory;
        this.archetypes = archetypes;
        this.combatSim = new CombatSimulator(avoidanceModel);
        this.mobGenerator = new MobStatGenerator(mobConfig);
        this.hpFormula = new MobHpFormula(mobConfig);
    }

    /**
     * Runs the power curve simulation and writes results to CSV.
     *
     * @param outputDir Directory for output files
     * @return Number of data points generated
     */
    public int run(@Nonnull Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        Path csvPath = outputDir.resolve("power_curves.csv");

        List<int[]> levelRanges = buildLevelList();
        int totalPoints = 0;

        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(csvPath))) {
            // CSV header
            writer.println("level,build,player_dps,mob_hp,mob_dps,"
                    + "ttk_sec,ttd_sec,survivability,"
                    + "avoid_rate,dodge_rate,evasion_rate,block_rate,"
                    + "health_regen,crit_rate,player_hp,avg_player_hit,avg_mob_hit,"
                    + "evasion_rating,block_chance_stat");

            for (int level : flattenLevels(levelRanges)) {
                // Generate one mob at this level (same for all builds — fair comparison)
                MobStatProfile mobProfile = mobGenerator.generate(level, 0, MOB_SEED + level);
                ComputedStats mobStats = hpFormula.toActualCombatStats(mobProfile, false);

                for (BuildArchetype archetype : archetypes) {
                    VirtualBuild build = buildFactory.create(archetype, level);

                    ComputedStats playerStats = pipeline.compute(
                            build.playerData(),
                            BaseStats.defaults(),
                            build.skillTreeData(),
                            build.gearBonuses());

                    CombatResult result = combatSim.simulate(playerStats, mobStats, COMBAT_ITERATIONS);

                    writer.printf("%d,%s,%.2f,%.2f,%.2f,%.2f,%.2f,%.4f,"
                            + "%.4f,%.4f,%.4f,%.4f,"
                            + "%.2f,%.4f,%.2f,%.2f,%.2f,"
                            + "%.2f,%.2f%n",
                            level,
                            archetype.name(),
                            result.playerDPS(),
                            result.mobHP(),
                            result.mobDPS(),
                            result.ttk(),
                            result.ttd(),
                            result.survivability(),
                            result.playerAvoidRate(),
                            result.dodgeRate(),
                            result.evasionRate(),
                            result.blockRate(),
                            result.healthRegen(),
                            result.critRate(),
                            result.playerHP(),
                            result.avgPlayerHit(),
                            result.avgMobHit(),
                            result.evasionRating(),
                            result.blockChance());

                    totalPoints++;
                }
            }
        }

        System.out.printf("  [PowerCurve] Wrote %d data points to %s%n", totalPoints, csvPath);
        return totalPoints;
    }

    // =========================================================================
    // Level Sampling Strategy
    // =========================================================================

    /**
     * Builds level sample points:
     * - Every level from 1-100 (full resolution in early game)
     * - Every 25 levels from 100-500
     * - Every 50 levels from 500-1000
     */
    private List<int[]> buildLevelList() {
        List<int[]> ranges = new ArrayList<>();
        ranges.add(new int[]{1, 100, 1});    // 1-100 every level
        ranges.add(new int[]{125, 500, 25}); // 125-500 every 25
        ranges.add(new int[]{550, 1000, 50}); // 550-1000 every 50
        return ranges;
    }

    private List<Integer> flattenLevels(List<int[]> ranges) {
        List<Integer> levels = new ArrayList<>();
        for (int[] range : ranges) {
            for (int l = range[0]; l <= range[1]; l += range[2]) {
                levels.add(l);
            }
        }
        return levels;
    }
}
