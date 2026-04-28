package io.github.larsonix.trailoforbis.simulation.debug;

import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.config.ConfigManager;
import io.github.larsonix.trailoforbis.config.RPGConfig;
import io.github.larsonix.trailoforbis.mobs.stats.MobStatGenerator;
import io.github.larsonix.trailoforbis.mobs.stats.MobStatPoolConfig;
import io.github.larsonix.trailoforbis.mobs.stats.MobStatProfile;
import io.github.larsonix.trailoforbis.util.LevelScaling;

import java.nio.file.Path;

/**
 * Debug tool: generates mob stats at specific levels with/without classification multipliers.
 * Useful for verifying hypotheses about mob HP bugs.
 *
 * Usage: ./gradlew simulateDebug
 */
public final class MobStatDebug {

    public static void main(String[] args) {
        try {
            run();
        } catch (Throwable t) {
            System.err.println("DEBUG FAILED:");
            t.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void run() throws Exception {
        Path configDir = Path.of("src", "main", "resources", "config");
        ConfigManager configManager = new ConfigManager(configDir);
        configManager.loadConfigs();

        RPGConfig rpgConfig = configManager.getConfig();
        MobStatPoolConfig mobConfig = configManager.getMobStatPoolConfig();

        // Configure LevelScaling
        RPGConfig.ScalingConfig scaling = rpgConfig.getScaling();
        if (scaling != null) {
            LevelScaling.configure(
                    scaling.getTransitionLevel(),
                    scaling.getMaxMultiplierRatio(),
                    scaling.getDecayDivisor()
            );
        }

        MobStatGenerator generator = new MobStatGenerator(mobConfig);

        System.out.println("=== Mob Stat Debug — Level 2 Cow Analysis ===");
        System.out.println();

        // Config values
        System.out.printf("Config: points_per_level=%.1f, progressive_scaling=%s, soft_cap=%d, min_factor=%.2f%n",
                mobConfig.getPointsPerLevel(),
                mobConfig.isProgressiveScalingEnabled(),
                mobConfig.getProgressiveScalingSoftCapLevel(),
                mobConfig.getProgressiveScalingMinFactor());
        System.out.printf("LevelScaling: transition=%d, multiplier_at_2=%.4f, bonus_pct_at_2=%.4f%n",
                LevelScaling.getTransitionLevel(),
                LevelScaling.getMultiplier(2),
                LevelScaling.getBonusPercent(2));
        System.out.println();

        // =====================================================================
        // Scenario A: Standard generate() — NO classification multiplier
        // This is what MobLevelRefreshSystem does (Bug #2)
        // =====================================================================
        System.out.println("--- Scenario A: generate(level=2, distance=0, seed=42) ---");
        System.out.println("    (What MobLevelRefreshSystem produces — no classification multiplier)");
        MobStatProfile profileA = generator.generate(2, 0, 42L);
        printProfile(profileA);

        // =====================================================================
        // Scenario B: generateSpecial() with PASSIVE 0.1x multiplier
        // This is what MobScalingSystem does (correct behavior)
        // =====================================================================
        System.out.println("--- Scenario B: generateSpecial(level=2, distance=0, multiplier=0.1, seed=42) ---");
        System.out.println("    (What MobScalingSystem produces — PASSIVE 0.1x classification)");
        MobStatProfile profileB = generator.generateSpecial(2, 0, 0.1, 42L);
        printProfile(profileB);

        // =====================================================================
        // Scenario C: Standard generate at level 1 (minimum)
        // =====================================================================
        System.out.println("--- Scenario C: generate(level=1, distance=0, seed=42) ---");
        System.out.println("    (Level 1 mob for comparison)");
        MobStatProfile profileC = generator.generate(1, 0, 42L);
        printProfile(profileC);

        // =====================================================================
        // Health modifier math (what goes into the ECS)
        // =====================================================================
        System.out.println("=== Health Modifier Calculation ===");
        System.out.println();

        double vanillaCowHP = 103.0; // From Cow.json
        double unscaledHP = 50.0;    // MobStats.UNSCALED health

        // What MobLevelRefreshSystem calculates (MULTIPLICATIVE)
        double refreshHP = profileA.maxHealth();
        double multModifier = refreshHP / unscaledHP;
        double resultMult = vanillaCowHP * multModifier;
        System.out.printf("MobLevelRefreshSystem (MULTIPLICATIVE bug):%n");
        System.out.printf("  RPG maxHealth from generate(): %.1f%n", refreshHP);
        System.out.printf("  healthMultiplier = %.1f / %.1f = %.4f%n", refreshHP, unscaledHP, multModifier);
        System.out.printf("  Hytale result: vanillaHP × multiplier = %.0f × %.4f = %.1f HP%n",
                vanillaCowHP, multModifier, resultMult);
        System.out.println();

        // What MobScalingSystem calculates (ADDITIVE, with PASSIVE multiplier)
        double correctHP = profileB.maxHealth();
        System.out.printf("MobScalingSystem (ADDITIVE, correct):%n");
        System.out.printf("  RPG maxHealth from generateSpecial(0.1): %.1f%n", correctHP);
        System.out.printf("  Hytale result: vanillaHP + RPG bonus = %.0f + %.1f = %.1f HP%n",
                vanillaCowHP, correctHP, vanillaCowHP + correctHP);
        System.out.println();

        System.out.println("=== Health Regen Comparison ===");
        System.out.printf("generate() healthRegen: %.4f/s (full hostile stats)%n", profileA.healthRegen());
        System.out.printf("generateSpecial(0.1) healthRegen: %.4f/s (PASSIVE 0.1x)%n", profileB.healthRegen());
        System.out.printf("Vanilla cow regen: unknown (depends on regeneratingValues field)%n");
        System.out.println();

        System.out.println("=== Conclusion ===");
        System.out.printf("Player sees: ~215 HP → closest to MULTIPLICATIVE bug: %.1f HP%n", resultMult);
        System.out.printf("Correct HP should be: %.1f HP (ADDITIVE + PASSIVE 0.1x)%n", vanillaCowHP + correctHP);
    }

    private static void printProfile(MobStatProfile p) {
        System.out.printf("  level=%d, totalPool=%.1f%n", p.mobLevel(), p.totalPool());
        System.out.printf("  maxHealth=%.2f, physDmg=%.2f, armor=%.2f%n",
                p.maxHealth(), p.physicalDamage(), p.armor());
        System.out.printf("  healthRegen=%.4f, moveSpeed=%.4f, critChance=%.4f%n",
                p.healthRegen(), p.moveSpeed(), p.criticalChance());
        System.out.printf("  dodgeChance=%.4f, blockChance=%.4f, lifeSteal=%.4f%n",
                p.dodgeChance(), p.blockChance(), p.lifeSteal());

        ComputedStats cs = p.toComputedStats();
        System.out.printf("  → ComputedStats: maxHP=%.2f, physDmg=%.2f, armor=%.2f, healthRegen=%.4f%n",
                cs.getMaxHealth(), cs.getPhysicalDamage(), cs.getArmor(), cs.getHealthRegen());
        System.out.println();
    }
}
