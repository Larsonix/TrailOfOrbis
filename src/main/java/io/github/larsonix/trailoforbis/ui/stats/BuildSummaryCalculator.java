package io.github.larsonix.trailoforbis.ui.stats;

import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.combat.CombatCalculator;
import io.github.larsonix.trailoforbis.combat.RPGDamageCalculator;
import io.github.larsonix.trailoforbis.combat.RPGDamageCalculator.DamageEstimate;
import io.github.larsonix.trailoforbis.combat.avoidance.AvoidanceProcessor;
import io.github.larsonix.trailoforbis.config.RPGConfig;
import io.github.larsonix.trailoforbis.mobs.stats.MobStatFactory;
import io.github.larsonix.trailoforbis.mobs.stats.MobStatPoolConfig;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Pure computation class for the Build Summary (Overview) tab.
 *
 * <p>Computes derived values that don't appear elsewhere:
 * <ul>
 *   <li><b>Average Damage Per Hit</b> — mirrors {@code RPGDamageCalculator} steps 1-8
 *       but uses expected crit instead of rolling RNG</li>
 *   <li><b>Effective HP</b> — combines raw HP, armor mitigation, and avoidance layers</li>
 * </ul>
 *
 * <p>No UI imports, no side effects — fully unit-testable.
 */
public final class BuildSummaryCalculator {

    private BuildSummaryCalculator() {
        // Static utility class
    }

    /**
     * Computes the full build summary from player stats and config.
     *
     * @param stats      The player's computed stats
     * @param level      The player's current level (for evasion reference accuracy)
     * @param evasionCfg The evasion config (for hit chance formula)
     * @param poolConfig The mob stat pool config (for reference accuracy at level)
     * @return A complete build summary with breakdowns for tooltip display
     */
    @Nonnull
    public static BuildSummary compute(
            @Nonnull ComputedStats stats,
            int level,
            @Nullable RPGConfig.CombatConfig.EvasionConfig evasionCfg,
            @Nullable MobStatPoolConfig poolConfig
    ) {
        DamageEstimate damageDetail = computeAvgDamage(stats);
        EHPBreakdownDetail ehpDetail = computeEffectiveHP(stats, level, evasionCfg, poolConfig);

        return new BuildSummary(
                damageDetail.avgDamagePerHit(),
                ehpDetail.effectiveHP(),
                damageDetail,
                ehpDetail,
                stats.getWeaponBaseDamage() > 0 || stats.isHoldingRpgGear(),
                stats.getWeaponItemId()
        );
    }

    // ═══════════════════════════════════════════════════════════════════
    // AVERAGE DAMAGE PER HIT
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Delegates to {@link RPGDamageCalculator#estimateAverageDamage} — the single source
     * of truth for the damage pipeline. No formula reimplementation here.
     */
    @Nonnull
    private static DamageEstimate computeAvgDamage(@Nonnull ComputedStats stats) {
        if (stats.getWeaponBaseDamage() <= 0 && !stats.isHoldingRpgGear()) {
            return DamageEstimate.ZERO;
        }
        RPGDamageCalculator calculator = new RPGDamageCalculator();
        return calculator.estimateAverageDamage(stats);
    }

    // ═══════════════════════════════════════════════════════════════════
    // EFFECTIVE HP
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Computes Effective HP through armor mitigation and avoidance layers.
     *
     * <p>Formula:
     * <ol>
     *   <li>rawHP = maxHealth + energyShield</li>
     *   <li>armorMit = armor / (armor + 9*level + 50), capped at 90% ({@link CombatCalculator#estimateArmorReduction})</li>
     *   <li>ehpFromArmor = rawHP / (1 - armorMit)</li>
     *   <li>avoidance = 1 - (1-dodge)(1-evasion)(1-block)(1-parry), clamped [0, 0.95]</li>
     *   <li>effectiveHP = ehpFromArmor / (1 - avoidance)</li>
     * </ol>
     */
    @Nonnull
    private static EHPBreakdownDetail computeEffectiveHP(
            @Nonnull ComputedStats stats,
            int level,
            @Nullable RPGConfig.CombatConfig.EvasionConfig evasionCfg,
            @Nullable MobStatPoolConfig poolConfig
    ) {
        float maxHealth = stats.getMaxHealth();
        float energyShield = stats.getEnergyShield();
        float rawHP = maxHealth + energyShield;

        // Armor mitigation — uses canonical level-scaled formula from CombatCalculator
        float armor = stats.getArmor();
        float armorMit = CombatCalculator.estimateArmorReduction(armor, level) / 100f;
        float ehpFromArmor = rawHP / (1f - armorMit);

        // Avoidance layers
        float dodgeChance = Math.min(stats.getDodgeChance() / 100f, 0.75f);
        dodgeChance = Math.max(dodgeChance, 0f);

        float evasionAvoid = 0f;
        if (evasionCfg != null && poolConfig != null && stats.getEvasion() > 0 && level >= 1) {
            float refAccuracy = (float) MobStatFactory.getReferenceAccuracy(poolConfig, level);
            float hitChance = AvoidanceProcessor.calculateHitChance(
                    evasionCfg, refAccuracy, stats.getEvasion());
            evasionAvoid = 1f - hitChance;
        }

        // Perfect block requires active blocking — not factored into passive EHP
        float blockAvoid = 0f;
        blockAvoid = Math.max(blockAvoid, 0f);

        float parryAvoid = Math.min(stats.getParryChance() / 100f, 0.5f);
        parryAvoid = Math.max(parryAvoid, 0f);

        float combinedAvoid = 1f - (1f - dodgeChance) * (1f - evasionAvoid) * (1f - blockAvoid) * (1f - parryAvoid);
        combinedAvoid = Math.min(combinedAvoid, 0.95f);
        combinedAvoid = Math.max(combinedAvoid, 0f);

        float effectiveHP = ehpFromArmor / (1f - combinedAvoid);

        return new EHPBreakdownDetail(
                effectiveHP,
                maxHealth,
                energyShield,
                rawHP,
                armor,
                armorMit,
                ehpFromArmor,
                dodgeChance * 100f,
                evasionAvoid * 100f,
                blockAvoid * 100f,
                parryAvoid * 100f,
                combinedAvoid * 100f
        );
    }

    // ═══════════════════════════════════════════════════════════════════
    // DATA RECORDS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Immutable summary of computed build stats for the Overview tab.
     */
    /**
     * Immutable summary of computed build stats for the Overview tab.
     *
     * <p>Damage detail is a {@link DamageEstimate} from the real
     * {@link RPGDamageCalculator} — no reimplemented formulas.
     */
    public record BuildSummary(
            float avgDamagePerHit,
            float effectiveHP,
            @Nonnull DamageEstimate damageDetail,
            @Nonnull EHPBreakdownDetail ehpDetail,
            boolean hasWeapon,
            @Nullable String weaponItemId
    ) {}

    /**
     * Breakdown of the Effective HP calculation for tooltip display.
     *
     * <p>All avoidance values are stored as percentages (0-100).
     */
    public record EHPBreakdownDetail(
            float effectiveHP,
            float maxHealth,
            float energyShield,
            float rawHP,
            float armor,
            float armorMitigation,
            float ehpFromArmor,
            float dodgeChancePct,
            float evasionAvoidPct,
            float blockAvoidPct,
            float parryAvoidPct,
            float combinedAvoidPct
    ) {}
}
