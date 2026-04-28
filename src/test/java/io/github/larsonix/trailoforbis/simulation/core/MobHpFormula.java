package io.github.larsonix.trailoforbis.simulation.core;

import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.mobs.stats.MobStatPoolConfig;
import io.github.larsonix.trailoforbis.mobs.stats.MobStatProfile;
import io.github.larsonix.trailoforbis.util.LevelScaling;

import javax.annotation.Nonnull;

/**
 * Replicates the EXACT in-game mob HP and damage formulas from MobScalingSystem
 * and RPGDamageSystem.
 *
 * <p><b>HP formula</b> (MobScalingSystem.applyStatModifiers, lines 577-631):
 * <pre>
 *   weight       = √(vanillaHP / 100)
 *   effectiveRpg = max(rpgHP × progressiveScale × expMult, 10 × bossMult)
 *   finalHP      = effectiveRpg × weight
 * </pre>
 *
 * <p><b>Damage formula</b> (RPGDamageSystem.calculateWeightedMobDamage, lines 1317-1376):
 * <pre>
 *   weight       = √(max(vanillaDmg, 5) / 10)
 *   effectiveRpg = max(rpgDmg × progressiveScale × classMult, 5 × classMult)
 *   finalDmg     = effectiveRpg × weight
 * </pre>
 *
 * <p>The weighted damage is passed to RPGDamageCalculator as baseDamage.
 * RPGDamageCalculator then skips flat damage for mobs (isMobStats=true)
 * and applies all other steps normally (% increased, conversion, crit, defenses).
 *
 * @see io.github.larsonix.trailoforbis.mobs.systems.MobScalingSystem
 */
public final class MobHpFormula {

    // === HP Constants (from MobScalingSystem) ===

    /** Default vanilla HP for standard mobs (Health.json asset max). */
    private static final double DEFAULT_VANILLA_HP = 100.0;

    /** Floor HP. Matches MobScalingSystem line 611. */
    private static final double HP_FLOOR = 10.0;

    /** Boss floor multiplier for HP. Matches MobScalingSystem line 601. */
    private static final double BOSS_HP_MULT = 2.5;

    // === Damage Constants (from RPGDamageSystem.calculateWeightedMobDamage) ===

    /** Default vanilla attack damage for standard mobs. */
    private static final double DEFAULT_VANILLA_DMG = 10.0;

    /** Minimum vanilla damage for weight calculation. Matches line 1355. */
    private static final double MIN_VANILLA_DMG = 5.0;

    /** Vanilla damage divisor for weight. Matches line 1358: √(vanillaDmg / 10). */
    private static final double VANILLA_DMG_DIVISOR = 10.0;

    /** Floor damage per mob. Matches line 1362: 5.0 × classMult. */
    private static final double DMG_FLOOR = 5.0;

    /** Classification damage multipliers. Matches RPGDamageSystem lines 1347-1351. */
    private static final double BOSS_DMG_MULT = 2.0;
    private static final double ELITE_DMG_MULT = 1.5;
    private static final double HOSTILE_DMG_MULT = 1.0;

    private final MobStatPoolConfig poolConfig;

    public MobHpFormula(@Nonnull MobStatPoolConfig poolConfig) {
        this.poolConfig = poolConfig;
    }

    // =========================================================================
    // HP
    // =========================================================================

    /**
     * Calculates actual in-game HP, matching MobScalingSystem exactly.
     */
    public double calculateActualHP(@Nonnull MobStatProfile profile, double vanillaHP, boolean isBoss) {
        double rpgTargetHP = profile.maxHealth();
        int mobLevel = profile.mobLevel();

        double progressiveScale = poolConfig.calculateScalingFactor(mobLevel);
        double expMultiplier = LevelScaling.getMultiplier(mobLevel);
        double bossMultiplier = isBoss ? BOSS_HP_MULT : 1.0;
        double weight = Math.sqrt(vanillaHP / 100.0);

        double effectiveRpg = Math.max(rpgTargetHP * progressiveScale * expMultiplier, HP_FLOOR * bossMultiplier);
        return effectiveRpg * weight;
    }

    /**
     * Convenience: standard mob (vanillaHP = 100).
     */
    public double calculateActualHP(@Nonnull MobStatProfile profile, boolean isBoss) {
        return calculateActualHP(profile, DEFAULT_VANILLA_HP, isBoss);
    }

    // =========================================================================
    // DAMAGE
    // =========================================================================

    /**
     * Calculates actual in-game base damage for a mob attack.
     * This is what gets passed to RPGDamageCalculator.calculate() as baseDamage.
     *
     * <p>Matches RPGDamageSystem.calculateWeightedMobDamage() lines 1317-1376.
     *
     * @param profile       The mob stat profile
     * @param vanillaDamage The vanilla attack damage (10 for standard mobs)
     * @param isBoss        BOSS classification
     * @param isElite       ELITE classification
     * @return The actual base damage passed to RPGDamageCalculator
     */
    public double calculateActualDamage(@Nonnull MobStatProfile profile, double vanillaDamage,
                                         boolean isBoss, boolean isElite) {
        double rpgTargetDmg = profile.physicalDamage();
        int mobLevel = profile.mobLevel();

        double progressiveScale = poolConfig.calculateScalingFactor(mobLevel);
        double classMult = isBoss ? BOSS_DMG_MULT : isElite ? ELITE_DMG_MULT : HOSTILE_DMG_MULT;

        double effectiveVanilla = Math.max(vanillaDamage, MIN_VANILLA_DMG);
        double weight = Math.sqrt(effectiveVanilla / VANILLA_DMG_DIVISOR);

        double effectiveRpg = Math.max(rpgTargetDmg * progressiveScale * classMult, DMG_FLOOR * classMult);
        return effectiveRpg * weight;
    }

    /**
     * Convenience: standard hostile mob (vanillaDamage = 10).
     */
    public double calculateActualDamage(@Nonnull MobStatProfile profile, boolean isBoss, boolean isElite) {
        return calculateActualDamage(profile, DEFAULT_VANILLA_DMG, isBoss, isElite);
    }

    // =========================================================================
    // COMBINED — produces corrected ComputedStats for simulation
    // =========================================================================

    /**
     * Creates ComputedStats with corrected HP and the weighted base damage stored
     * in weaponBaseDamage (so CombatSimulator reads it as the mob's attack power).
     *
     * <p>Other stats (armor, dodge, block, crit, etc.) remain as-is from the profile —
     * they ARE used directly by RPGDamageCalculator for the mob's defensive stats.
     */
    @Nonnull
    public ComputedStats toActualCombatStats(@Nonnull MobStatProfile profile, boolean isBoss, boolean isElite) {
        double actualHP = calculateActualHP(profile, isBoss);
        double actualDmg = calculateActualDamage(profile, isBoss, isElite);

        // Build via toBuilder() so we can set mobStats=true (which is final).
        // MobStatProfile.toComputedStats() does NOT set mobStats — we must do it here.
        // Without mobStats=true, RPGDamageCalculator would add physicalDamage as flat bonus
        // on top of baseDamage, double-counting the damage.
        ComputedStats base = profile.toComputedStats();
        return base.toBuilder()
                .maxHealth((float) actualHP)
                .physicalDamage((float) actualDmg)
                .mobStats(true)
                .build();
    }

    /**
     * Overload for backward compat — HOSTILE classification (not elite, not boss).
     */
    @Nonnull
    public ComputedStats toActualCombatStats(@Nonnull MobStatProfile profile, boolean isBoss) {
        return toActualCombatStats(profile, isBoss, false);
    }
}
