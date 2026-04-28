package io.github.larsonix.trailoforbis.simulation;

import io.github.larsonix.trailoforbis.config.ConfigManager;
import io.github.larsonix.trailoforbis.config.RPGConfig;
import io.github.larsonix.trailoforbis.simulation.core.AvoidanceModel;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig;
import io.github.larsonix.trailoforbis.leveling.config.LevelingConfig;
import io.github.larsonix.trailoforbis.mobs.stats.MobStatPoolConfig;
import io.github.larsonix.trailoforbis.simulation.builds.BuildArchetype;
import io.github.larsonix.trailoforbis.simulation.builds.BuildFactory;
import io.github.larsonix.trailoforbis.simulation.core.StatPipeline;
import io.github.larsonix.trailoforbis.simulation.gear.VirtualGearFactory;
import io.github.larsonix.trailoforbis.simulation.scenarios.ComprehensiveScenario;
import io.github.larsonix.trailoforbis.simulation.scenarios.PowerCurveScenario;
import io.github.larsonix.trailoforbis.simulation.scenarios.XpEconomyScenario;
import io.github.larsonix.trailoforbis.skilltree.config.SkillTreeConfig;
import io.github.larsonix.trailoforbis.util.LevelScaling;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Balance simulation entry point.
 *
 * <p>Loads real game configs, creates virtual builds at every level,
 * and runs combat simulations using the actual RPGDamageCalculator
 * and AttributeCalculator — zero formula drift.
 *
 * <p>Usage: {@code ./gradlew simulate}
 *
 * <p>Output: CSV files in build/simulation-output/
 */
public final class SimulationMain {

    public static void main(String[] args) {
        try {
            run(args);
        } catch (Throwable t) {
            System.err.println("SIMULATION FAILED:");
            t.printStackTrace(System.err);
            System.err.flush();
            System.exit(1);
        }
    }

    private static void run(String[] args) throws Exception {
        Path outputDir = args.length > 0
                ? Path.of(args[0])
                : Path.of("build", "simulation-output");

        System.out.println("=== Trail of Orbis — Balance Simulation ===");
        System.out.println("Output: " + outputDir.toAbsolutePath());
        System.out.println();

        long startTime = System.currentTimeMillis();

        // =====================================================================
        // 1. Load Configs (same YAML files the game uses)
        // =====================================================================
        System.out.println("[1/5] Loading game configs...");

        // ConfigManager constructor appends "/config" to dataFolder, so pass the parent
        Path configDir = Path.of("src", "main", "resources");
        if (!Files.isDirectory(configDir)) {
            System.err.println("Config directory not found: " + configDir.toAbsolutePath());
            System.err.println("Run from project root directory.");
            System.exit(1);
        }

        ConfigManager configManager = new ConfigManager(configDir);
        configManager.loadConfigs();
        configManager.loadGearConfigs();

        RPGConfig rpgConfig = configManager.getConfig();
        MobStatPoolConfig mobConfig = configManager.getMobStatPoolConfig();
        LevelingConfig levelingConfig = configManager.getLevelingConfig();
        GearBalanceConfig gearBalance = configManager.getGearBalanceConfig();
        ModifierConfig modifierConfig = configManager.getModifierConfig();

        // Load skill tree config (loaded separately from ConfigManager)
        // Skill tree is in the config/ subdirectory
        SkillTreeConfig skillTreeConfig = loadSkillTreeConfig(configDir.resolve("config"));

        // Configure LevelScaling from RPGConfig
        RPGConfig.ScalingConfig scaling = rpgConfig.getScaling();
        if (scaling != null) {
            LevelScaling.configure(
                    scaling.getTransitionLevel(),
                    scaling.getMaxMultiplierRatio(),
                    scaling.getDecayDivisor()
            );
        }

        System.out.printf("  Loaded: RPG config, %d mob stat types, %d skill tree nodes%n",
                mobConfig.getStatConfigs().size(),
                skillTreeConfig.getNodes().size());

        // =====================================================================
        // 2. Build Simulation Components
        // =====================================================================
        System.out.println("[2/5] Building simulation components...");

        StatPipeline pipeline = new StatPipeline(rpgConfig, skillTreeConfig);
        VirtualGearFactory gearFactory = new VirtualGearFactory(gearBalance, modifierConfig);
        BuildFactory buildFactory = new BuildFactory(skillTreeConfig, gearFactory);

        // Create AvoidanceModel from loaded evasion config (reads actual config.yml values)
        RPGConfig.CombatConfig.EvasionConfig evasionCfg = rpgConfig.getCombat().getEvasion();
        AvoidanceModel avoidanceModel = new AvoidanceModel(
                evasionCfg.getMinHitChance(),
                evasionCfg.getMaxHitChance(),
                evasionCfg.getEvasionScalingFactor(),
                evasionCfg.getEvasionExponent(),
                evasionCfg.getHitChanceConstant()
        );
        System.out.printf("  Evasion config: scalingFactor=%.2f, exponent=%.1f, hitConst=%.2f%n",
                evasionCfg.getEvasionScalingFactor(), evasionCfg.getEvasionExponent(),
                evasionCfg.getHitChanceConstant());

        // Generate ALL build archetypes from skill tree connectivity
        java.util.List<BuildArchetype> archetypes = BuildArchetype.generateAll(skillTreeConfig);
        System.out.printf("  Archetypes: %d builds (6 single + 15 double-element + %d element+octant)%n",
                archetypes.size(), archetypes.size() - 21);

        // =====================================================================
        // 3. Run Comprehensive Scenario (levels 1-100, all dimensions)
        // =====================================================================
        System.out.println("[3/6] Running Comprehensive scenario (levels 1-100, all builds, all mob types)...");

        ComprehensiveScenario comprehensive = new ComprehensiveScenario(
                pipeline, buildFactory, archetypes, mobConfig, levelingConfig, avoidanceModel);
        comprehensive.run(outputDir);

        // =====================================================================
        // 4. Run Power Curve Scenario (levels 1-1000 sampled)
        // =====================================================================
        System.out.println("[4/6] Running Power Curve scenario (levels 1-1000)...");

        PowerCurveScenario powerCurve = new PowerCurveScenario(pipeline, buildFactory, archetypes, mobConfig, avoidanceModel);
        int powerPoints = powerCurve.run(outputDir);

        // =====================================================================
        // 5. Run XP Economy Scenario
        // =====================================================================
        System.out.println("[5/6] Running XP Economy scenario...");

        XpEconomyScenario xpEconomy = new XpEconomyScenario(levelingConfig, mobConfig);
        int xpPoints = xpEconomy.run(outputDir);

        // =====================================================================
        // 6. Summary
        // =====================================================================
        long elapsed = System.currentTimeMillis() - startTime;
        System.out.println();
        System.out.println("[6/6] Simulation complete!");
        System.out.printf("  Total data points: %d + comprehensive CSVs%n", powerPoints + xpPoints);
        System.out.printf("  Time: %.1f seconds%n", elapsed / 1000.0);
        System.out.println();
        System.out.println("Output files:");
        System.out.println("  " + outputDir.resolve("power_curves.csv"));
        System.out.println("  " + outputDir.resolve("xp_economy.csv"));
        System.out.println();
        System.out.println("Quick checks:");
        System.out.println("  - Compare PURE_EARTH survivability vs PURE_FIRE (Earth should be higher)");
        System.out.println("  - Check efficiency_ratio in xp_economy.csv (DG vs open-world)");
        System.out.println("  - Look for TTD divergence points where one build becomes dominant");
    }

    /**
     * Loads SkillTreeConfig from skill-tree.yml using SnakeYAML.
     */
    private static SkillTreeConfig loadSkillTreeConfig(Path configDir) throws Exception {
        Path skillTreePath = configDir.resolve("skill-tree.yml");
        if (!Files.exists(skillTreePath)) {
            System.err.println("Skill tree config not found: " + skillTreePath);
            return new SkillTreeConfig();
        }

        LoaderOptions options = new LoaderOptions();
        Constructor constructor = new Constructor(SkillTreeConfig.class, options);
        Yaml yaml = new Yaml(constructor);

        try (InputStream input = Files.newInputStream(skillTreePath)) {
            SkillTreeConfig config = yaml.loadAs(input, SkillTreeConfig.class);
            return config != null ? config : new SkillTreeConfig();
        }
    }
}
