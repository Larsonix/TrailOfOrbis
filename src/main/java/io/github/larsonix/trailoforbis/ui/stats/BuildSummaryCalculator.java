package io.github.larsonix.trailoforbis.ui.stats;

import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.combat.avoidance.AvoidanceProcessor;
import io.github.larsonix.trailoforbis.config.RPGConfig;
import io.github.larsonix.trailoforbis.elemental.ElementType;
import io.github.larsonix.trailoforbis.mobs.stats.MobStatGenerator;
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
        DamageBreakdownDetail damageDetail = computeAvgDamage(stats);
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
     * Computes average damage per hit following RPGDamageCalculator steps 1-8
     * with expected crit instead of RNG.
     *
     * <p>Pipeline (assumes melee, the most common attack type):
     * <ol>
     *   <li>Weapon base damage (already quality-adjusted in ComputedStats)</li>
     *   <li>+ flat physical + flat melee</li>
     *   <li>+ flat elemental (sum of all 6 elements)</li>
     *   <li>× (1 + %increased / 100) where %increased = physDmg% + melee% + dmg%</li>
     *   <li>× (1 + allDmgPct/100) × (1 + dmgMult/100) — "more" multipliers</li>
     *   <li>× (1 + critChance/100 × (critMult/100 - 1)) — expected crit</li>
     * </ol>
     */
    @Nonnull
    private static DamageBreakdownDetail computeAvgDamage(@Nonnull ComputedStats stats) {
        float weaponBase = stats.getWeaponBaseDamage();

        // If no weapon and not holding RPG gear, return zero
        if (weaponBase <= 0 && !stats.isHoldingRpgGear()) {
            return DamageBreakdownDetail.ZERO;
        }

        // Step 1-2: Flat physical + melee (assume melee attack type)
        float flatPhys = stats.getPhysicalDamage();
        float flatMelee = stats.getMeleeDamage();
        float baseTotal = weaponBase + flatPhys + flatMelee;

        // Step 2b: Flat elemental
        float flatElemental = 0f;
        for (ElementType type : ElementType.values()) {
            flatElemental += getFlatElementalDamage(stats, type);
        }
        baseTotal += flatElemental;

        // Step 4: % Increased (summed additively, applied once)
        float physPct = stats.getPhysicalDamagePercent();
        float meleePct = stats.getMeleeDamagePercent();
        float dmgPct = stats.getDamagePercent();
        float totalIncreasedPct = physPct + meleePct + dmgPct;
        float increasedMult = 1f + totalIncreasedPct / 100f;
        float afterIncreased = baseTotal * increasedMult;

        // Step 6: % More multipliers (multiplicative chain)
        float allDmgPct = stats.getAllDamagePercent();
        float dmgMult = stats.getDamageMultiplier();
        float moreMult = (1f + allDmgPct / 100f) * (1f + dmgMult / 100f);
        float afterMore = afterIncreased * moreMult;

        // Step 8: Expected crit (probability × multiplier bonus)
        float critChance = stats.getCriticalChance();
        float critMultRaw = stats.getCriticalMultiplier();
        float expectedCritMult = 1f + Math.max(0f, critChance) / 100f * (Math.max(100f, critMultRaw) / 100f - 1f);
        float avgDamage = afterMore * expectedCritMult;

        return new DamageBreakdownDetail(
                avgDamage,
                weaponBase,
                flatPhys,
                flatMelee,
                flatElemental,
                baseTotal,
                totalIncreasedPct,
                increasedMult,
                allDmgPct,
                dmgMult,
                moreMult,
                critChance,
                critMultRaw,
                expectedCritMult
        );
    }

    /**
     * Gets flat elemental damage for a specific element from ComputedStats.
     */
    private static float getFlatElementalDamage(@Nonnull ComputedStats stats, @Nonnull ElementType type) {
        return switch (type) {
            case FIRE -> stats.getFireDamage();
            case WATER -> stats.getWaterDamage();
            case LIGHTNING -> stats.getLightningDamage();
            case EARTH -> stats.getEarthDamage();
            case WIND -> stats.getWindDamage();
            case VOID -> stats.getVoidDamage();
        };
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
     *   <li>armorMit = armor / (armor + 1000), clamped [0, 0.9]</li>
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

        // Armor mitigation
        float armor = stats.getArmor();
        float armorMit = armor > 0 ? armor / (armor + 1000f) : 0f;
        armorMit = Math.min(armorMit, 0.9f);
        float ehpFromArmor = rawHP / (1f - armorMit);

        // Avoidance layers
        float dodgeChance = Math.min(stats.getDodgeChance() / 100f, 0.75f);
        dodgeChance = Math.max(dodgeChance, 0f);

        float evasionAvoid = 0f;
        if (evasionCfg != null && poolConfig != null && stats.getEvasion() > 0 && level >= 1) {
            float refAccuracy = (float) new MobStatGenerator(poolConfig)
                    .getBaseStats(level).accuracy();
            float hitChance = AvoidanceProcessor.calculateHitChance(
                    evasionCfg, refAccuracy, stats.getEvasion());
            evasionAvoid = 1f - hitChance;
        }

        float blockAvoid = Math.min(stats.getPassiveBlockChance() / 100f, 0.75f);
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
    public record BuildSummary(
            float avgDamagePerHit,
            float effectiveHP,
            @Nonnull DamageBreakdownDetail damageDetail,
            @Nonnull EHPBreakdownDetail ehpDetail,
            boolean hasWeapon,
            @Nullable String weaponItemId
    ) {}

    /**
     * Breakdown of the average damage per hit calculation for tooltip display.
     */
    public record DamageBreakdownDetail(
            float avgDamagePerHit,
            float weaponBase,
            float flatPhysical,
            float flatMelee,
            float flatElemental,
            float baseTotal,
            float totalIncreasedPct,
            float increasedMult,
            float allDamagePct,
            float damageMultiplier,
            float moreMult,
            float critChance,
            float critMultRaw,
            float expectedCritMult
    ) {
        static final DamageBreakdownDetail ZERO = new DamageBreakdownDetail(
                0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 100f, 1f
        );
    }

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
