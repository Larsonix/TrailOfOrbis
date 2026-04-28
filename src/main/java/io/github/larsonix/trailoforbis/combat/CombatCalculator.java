package io.github.larsonix.trailoforbis.combat;

import io.github.larsonix.trailoforbis.attributes.ComputedStats;

import javax.annotation.Nonnull;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Pure damage calculation logic with no Hytale dependencies.
 *
 * <p>This class contains all the math for combat calculations, making it
 * fully unit-testable without requiring Hytale server runtime.
 *
 * <p><b>PoE-Inspired Damage Formula:</b>
 * <pre>
 * FinalDamage = (BaseDamage + FlatBonus) * (1 + Sum(%Increases)/100) * CritMultiplier
 * AfterArmor = FinalDamage * (1 - min(ArmorReduction, 0.9))
 * </pre>
 */
public class CombatCalculator {

    /** Default maximum armor damage reduction (90% cap) */
    public static final float DEFAULT_MAX_ARMOR_REDUCTION = 0.9f;

    /** Default armor formula divisor (PoE standard) */
    public static final float DEFAULT_ARMOR_FORMULA_DIVISOR = 10.0f;

    /**
     * Default minimum armor effectiveness after penetration (50% floor).
     *
     * <p>This prevents armor penetration from reducing effective armor below this
     * fraction of the original. With 50% floor, even 100% armor pen only reduces
     * armor to half its value, ensuring armor investment remains meaningful.
     */
    public static final float DEFAULT_MIN_ARMOR_EFFECTIVENESS = 0.5f;

    /** Maximum armor damage reduction (configurable, defaults to 90%) */
    private float maxArmorReduction = DEFAULT_MAX_ARMOR_REDUCTION;

    /** Armor formula divisor: armor / (armor + divisor * damage) */
    private float armorFormulaDivisor = DEFAULT_ARMOR_FORMULA_DIVISOR;

    /** Minimum armor effectiveness after penetration (configurable, defaults to 50%) */
    private float minArmorEffectiveness = DEFAULT_MIN_ARMOR_EFFECTIVENESS;

    public CombatCalculator() {
    }

    /**
     * @param maxArmorReduction Maximum armor reduction (0.0 to 1.0)
     */
    public CombatCalculator(float maxArmorReduction) {
        this.maxArmorReduction = Math.max(0f, Math.min(1f, maxArmorReduction));
    }

    /**
     * Creates a CombatCalculator with custom armor settings.
     *
     * @param maxArmorReduction Maximum armor reduction (0.0 to 1.0)
     * @param armorFormulaDivisor Divisor in armor formula (must be positive)
     */
    public CombatCalculator(float maxArmorReduction, float armorFormulaDivisor) {
        this.maxArmorReduction = Math.max(0f, Math.min(1f, maxArmorReduction));
        this.armorFormulaDivisor = Math.max(0.01f, armorFormulaDivisor);
    }

    /**
     * @param maxArmorReduction Maximum armor reduction (0.0 to 1.0)
     */
    public void setMaxArmorReduction(float maxArmorReduction) {
        this.maxArmorReduction = Math.max(0f, Math.min(1f, maxArmorReduction));
    }

    /**
     * @return Maximum armor reduction (0.0 to 1.0)
     */
    public float getMaxArmorReduction() {
        return maxArmorReduction;
    }

    /**
     * @param armorFormulaDivisor Divisor in armor formula (must be positive)
     */
    public void setArmorFormulaDivisor(float armorFormulaDivisor) {
        this.armorFormulaDivisor = Math.max(0.01f, armorFormulaDivisor);
    }

    public float getArmorFormulaDivisor() {
        return armorFormulaDivisor;
    }

    /**
     * Sets the minimum armor effectiveness after penetration.
     *
     * <p>This value acts as a floor - armor penetration cannot reduce effective
     * armor below this fraction of the original. For example, with a floor of 0.5,
     * even 100% armor penetration only reduces armor to 50% of its value.
     *
     * @param minArmorEffectiveness Minimum effectiveness (0.0 to 1.0, clamped)
     */
    public void setMinArmorEffectiveness(float minArmorEffectiveness) {
        this.minArmorEffectiveness = Math.max(0f, Math.min(1f, minArmorEffectiveness));
    }

    /**
     * @return Minimum armor effectiveness (0.0 to 1.0)
     */
    public float getMinArmorEffectiveness() {
        return minArmorEffectiveness;
    }

    /**
     * Result of damage calculation with full breakdown for UI/logging.
     *
     * @param finalDamage The calculated final damage
     * @param baseDamage The original base damage before modifications
     * @param flatBonus The flat damage bonus applied (from STR)
     * @param percentBonus The percentage bonus applied (from STR)
     * @param wasCritical Whether this attack was a critical hit
     * @param critMultiplier The critical multiplier used (as decimal, e.g., 1.5 for 150%)
     */
    public record DamageResult(
        float finalDamage,
        float baseDamage,
        float flatBonus,
        float percentBonus,
        boolean wasCritical,
        float critMultiplier
    ) {}

    /**
     * Result of armor reduction calculation.
     *
     * @param finalDamage The damage after armor reduction
     * @param beforeArmor The damage before armor reduction
     * @param armorValue The defender's armor value
     * @param effectiveArmor Armor after penetration is applied
     * @param armorPenetration The attacker's armor penetration percentage
     * @param reductionPercent The percentage of damage reduced (0-90%)
     */
    public record ArmorResult(
        float finalDamage,
        float beforeArmor,
        float armorValue,
        float effectiveArmor,
        float armorPenetration,
        float reductionPercent
    ) {}

    /**
     * Calculates attacker damage bonuses from stats.
     *
     * <p>Applies in order:
     * <ol>
     *   <li>Flat physical damage bonus (from STR)</li>
     *   <li>Percentage physical damage increase (from STR)</li>
     *   <li>Attack type-specific damage bonus (melee/projectile/area)</li>
     *   <li>Critical strike (from LUCK/DEX)</li>
     * </ol>
     *
     * @param baseDamage The base weapon/attack damage
     * @param stats The attacker's computed stats
     * @return Full damage breakdown for logging/UI
     */
    @Nonnull
    public DamageResult calculateAttackerDamage(float baseDamage, @Nonnull ComputedStats stats) {
        return calculateAttackerDamage(baseDamage, stats, AttackType.UNKNOWN);
    }

    /**
     * Calculates attacker damage bonuses from stats with attack type modifier.
     *
     * <p>Applies in order:
     * <ol>
     *   <li>Flat physical damage bonus (from STR)</li>
     *   <li>Percentage physical damage increase (from STR)</li>
     *   <li>Attack type-specific damage bonus (melee/projectile/area)</li>
     *   <li>Critical strike (from LUCK/DEX)</li>
     * </ol>
     *
     * @param baseDamage The base weapon/attack damage
     * @param stats The attacker's computed stats
     * @param attackType The type of attack (melee, projectile, area)
     * @return Full damage breakdown for logging/UI
     */
    @Nonnull
    public DamageResult calculateAttackerDamage(float baseDamage, @Nonnull ComputedStats stats, @Nonnull AttackType attackType) {
        // 1. Flat physical damage from STR
        float flatBonus = stats.getPhysicalDamage();

        // 1b. Flat melee damage bonus (only for melee attacks)
        if (attackType == AttackType.MELEE) {
            flatBonus += stats.getMeleeDamage();
        }

        float afterFlat = baseDamage + flatBonus;

        // 2. Percentage increases (sum all applicable modifiers)
        float percentBonus = stats.getPhysicalDamagePercent();

        // 3. Attack type-specific modifiers
        float attackTypeBonus = getAttackTypeBonus(stats, attackType);
        percentBonus += attackTypeBonus;

        float afterPercent = afterFlat * (1.0f + percentBonus / 100.0f);

        // 3b. Global all-damage percent multiplier (separate multiplier layer)
        float allDmgPct = stats.getAllDamagePercent();
        if (allDmgPct != 0f) {
            afterPercent *= (1.0f + allDmgPct / 100.0f);
        }

        // 4. Critical strike
        float critChance = stats.getCriticalChance();
        float critMultiplier = stats.getCriticalMultiplier() / 100.0f; // Convert from 150 to 1.5
        boolean wasCrit = rollCritical(critChance);
        float finalDamage = wasCrit ? afterPercent * critMultiplier : afterPercent;

        return new DamageResult(
            finalDamage,
            baseDamage,
            flatBonus,
            percentBonus,
            wasCrit,
            wasCrit ? critMultiplier : 1.0f
        );
    }

    /**
     * @return The percentage bonus for this attack type (0 if unknown)
     */
    private float getAttackTypeBonus(@Nonnull ComputedStats stats, @Nonnull AttackType attackType) {
        return switch (attackType) {
            case MELEE -> stats.getMeleeDamagePercent();
            case PROJECTILE -> stats.getProjectileDamagePercent();
            case AREA, UNKNOWN -> 0f;
        };
    }

    /**
     * Calculates attacker damage with explicit crit result (for testing).
     *
     * @param baseDamage The base weapon/attack damage
     * @param stats The attacker's computed stats
     * @param forceCrit If true, force a critical hit; if false, force no crit
     * @return Full damage breakdown
     */
    @Nonnull
    public DamageResult calculateAttackerDamage(float baseDamage, @Nonnull ComputedStats stats, boolean forceCrit) {
        return calculateAttackerDamage(baseDamage, stats, AttackType.UNKNOWN, forceCrit);
    }

    /**
     * Calculates attacker damage with attack type and explicit crit result (for testing).
     *
     * @param baseDamage The base weapon/attack damage
     * @param stats The attacker's computed stats
     * @param attackType The type of attack
     * @param forceCrit If true, force a critical hit; if false, force no crit
     * @return Full damage breakdown
     */
    @Nonnull
    public DamageResult calculateAttackerDamage(float baseDamage, @Nonnull ComputedStats stats, @Nonnull AttackType attackType, boolean forceCrit) {
        float flatBonus = stats.getPhysicalDamage();
        if (attackType == AttackType.MELEE) {
            flatBonus += stats.getMeleeDamage();
        }
        float afterFlat = baseDamage + flatBonus;

        float percentBonus = stats.getPhysicalDamagePercent();
        percentBonus += getAttackTypeBonus(stats, attackType);
        float afterPercent = afterFlat * (1.0f + percentBonus / 100.0f);

        float allDmgPct = stats.getAllDamagePercent();
        if (allDmgPct != 0f) {
            afterPercent *= (1.0f + allDmgPct / 100.0f);
        }

        float critMultiplier = stats.getCriticalMultiplier() / 100.0f;
        float finalDamage = forceCrit ? afterPercent * critMultiplier : afterPercent;

        return new DamageResult(
            finalDamage,
            baseDamage,
            flatBonus,
            percentBonus,
            forceCrit,
            forceCrit ? critMultiplier : 1.0f
        );
    }

    /**
     * Calculates defender armor reduction using PoE-inspired formula.
     *
     * <p><b>Formula:</b>
     * <pre>
     * EffectiveArmor = Armor * (1 - ArmorPenetration / 100)
     * Reduction = EffectiveArmor / (EffectiveArmor + divisor * Damage)
     * FinalDamage = Damage * (1 - min(Reduction, maxReduction))
     * </pre>
     *
     * <p>This formula has diminishing returns:
     * <ul>
     *   <li>Low damage + high armor = high reduction</li>
     *   <li>High damage + low armor = low reduction</li>
     *   <li>Armor scales better against small hits</li>
     *   <li>Armor penetration reduces effective armor before calculation</li>
     * </ul>
     *
     * @param damage The incoming damage
     * @param defenderStats The defender's computed stats
     * @return Full armor reduction breakdown
     */
    @Nonnull
    public ArmorResult calculateDefenderReduction(float damage, @Nonnull ComputedStats defenderStats) {
        return calculateDefenderReduction(damage, defenderStats, 0f);
    }

    /**
     * Calculates defender armor reduction with attacker's armor penetration.
     *
     * @param damage The incoming damage
     * @param defenderStats The defender's computed stats
     * @param armorPenetration The attacker's armor penetration percentage (0-100)
     * @return Full armor reduction breakdown
     */
    @Nonnull
    public ArmorResult calculateDefenderReduction(float damage, @Nonnull ComputedStats defenderStats, float armorPenetration) {
        float baseArmor = defenderStats.getArmor();

        // Apply armor percent bonus: armor * (1 + armorPercent / 100)
        float armorPercent = defenderStats.getArmorPercent();
        float armor = baseArmor * (1.0f + armorPercent / 100.0f);
        armor = Math.max(0f, armor); // Can't go negative

        if (armor <= 0 || damage <= 0) {
            return new ArmorResult(damage, damage, armor, armor, armorPenetration, 0f);
        }

        // Apply armor penetration with floor: armor pen cannot reduce effectiveness below minArmorEffectiveness
        // Formula: effectiveness = max(floor, 1 - pen%), then effectiveArmor = armor * effectiveness
        // Clamp pen to 0-100 range (negative pen shouldn't buff armor)
        float clampedPen = Math.max(0f, Math.min(armorPenetration, 100f));
        float penFactor = clampedPen / 100.0f;
        float effectiveness = Math.max(minArmorEffectiveness, 1.0f - penFactor);
        float effectiveArmor = armor * effectiveness;

        // PoE armor formula: Reduction = EffectiveArmor / (EffectiveArmor + divisor * Damage)
        float reduction = effectiveArmor / (effectiveArmor + armorFormulaDivisor * damage);

        // Cap at maxArmorReduction (default 90%)
        reduction = Math.min(reduction, maxArmorReduction);

        // Apply reduction
        float finalDamage = damage * (1.0f - reduction);

        return new ArmorResult(
            finalDamage,
            damage,
            armor,
            effectiveArmor,
            armorPenetration,
            reduction * 100f // Convert to percentage for display
        );
    }

    /**
     * Rolls for critical strike based on crit chance.
     *
     * @param critChance The critical chance as a percentage (e.g., 15.0 = 15%)
     * @return true if the roll was a critical hit
     */
    protected boolean rollCritical(float critChance) {
        if (critChance <= 0) {
            return false;
        }
        // Roll 0-100 and compare to crit chance
        return ThreadLocalRandom.current().nextFloat() * 100f < critChance;
    }

    // ==================== Percentage-Based Damage Reduction ====================

    /**
     * Maximum allowed percentage reduction (prevents negative damage).
     */
    public static final float MAX_PERCENTAGE_REDUCTION = 100f;

    /**
     * Applies a percentage-based damage reduction.
     *
     * <p>This is used for flat percentage reductions like fall damage reduction,
     * physical resistance, and similar mechanics that don't use the armor formula.
     *
     * <p><b>Formula:</b> {@code finalDamage = damage * (1 - min(reduction, 100) / 100)}
     *
     * <p>Examples:
     * <ul>
     *   <li>50% reduction: 100 damage → 50 damage</li>
     *   <li>100% reduction: 100 damage → 0 damage (immune)</li>
     *   <li>0% reduction: 100 damage → 100 damage (no change)</li>
     * </ul>
     *
     * @param damage The incoming damage amount
     * @param reductionPercent The reduction percentage (0-100+, capped at 100)
     * @return Result containing final damage and effective reduction applied
     */
    @Nonnull
    public static PercentageReductionResult applyPercentageReduction(float damage, float reductionPercent) {
        if (damage <= 0) {
            return new PercentageReductionResult(0f, 0f, 0f, 0f);
        }

        if (reductionPercent <= 0) {
            return new PercentageReductionResult(damage, damage, 0f, 0f);
        }

        // Cap at 100% to prevent negative damage
        float effectiveReduction = Math.min(reductionPercent, MAX_PERCENTAGE_REDUCTION);

        // Apply reduction: damage * (1 - reduction%)
        float finalDamage = damage * (1f - effectiveReduction / 100f);
        float damageReduced = damage - finalDamage;

        return new PercentageReductionResult(finalDamage, damage, effectiveReduction, damageReduced);
    }

    /**
     * Convenience method for applying fall damage reduction from defender stats.
     *
     * @param fallDamage The incoming fall damage
     * @param defenderStats The defender's computed stats
     * @return Result containing final damage and reduction applied
     */
    @Nonnull
    public static PercentageReductionResult applyFallDamageReduction(float fallDamage, @Nonnull ComputedStats defenderStats) {
        return applyPercentageReduction(fallDamage, defenderStats.getFallDamageReduction());
    }

    /**
     * Applies physical resistance to physical damage.
     *
     * <p>Physical resistance is a separate layer from armor that provides
     * flat percentage reduction. Unlike armor (which has diminishing returns
     * against high damage), physical resistance is always the same percentage.
     *
     * <p><b>Damage Reduction Order:</b>
     * <ol>
     *   <li>Armor (PoE formula with diminishing returns)</li>
     *   <li>Physical Resistance (flat percentage, this method)</li>
     *   <li>Elemental Resistances (separate, not applied here)</li>
     * </ol>
     *
     * <p><b>Note:</b> This should only be called for physical damage sources,
     * not for elemental, fall, or environmental damage.
     *
     * @param physicalDamage The incoming physical damage (after armor reduction)
     * @param defenderStats The defender's computed stats
     * @param maxResistanceCap The maximum resistance cap from config (e.g., 75%)
     * @return Result containing final damage and reduction applied
     */
    @Nonnull
    public static PercentageReductionResult applyPhysicalResistance(
        float physicalDamage,
        @Nonnull ComputedStats defenderStats,
        float maxResistanceCap
    ) {
        float physResist = defenderStats.getPhysicalResistance();
        if (physResist <= 0) {
            return new PercentageReductionResult(physicalDamage, physicalDamage, 0f, 0f);
        }

        // Apply cap from config
        float cappedResist = Math.min(physResist, maxResistanceCap);

        return applyPercentageReduction(physicalDamage, cappedResist);
    }

    /**
     * Convenience overload using default 75% cap.
     *
     * @param physicalDamage The incoming physical damage
     * @param defenderStats The defender's computed stats
     * @return Result containing final damage and reduction applied
     */
    @Nonnull
    public static PercentageReductionResult applyPhysicalResistance(
        float physicalDamage,
        @Nonnull ComputedStats defenderStats
    ) {
        return applyPhysicalResistance(physicalDamage, defenderStats, 75f);
    }

    /**
     * Result of a percentage-based damage reduction calculation.
     *
     * @param finalDamage The damage after reduction
     * @param originalDamage The original incoming damage
     * @param reductionPercent The effective reduction percentage applied (capped at 100)
     * @param damageReduced The amount of damage that was reduced
     */
    public record PercentageReductionResult(
        float finalDamage,
        float originalDamage,
        float reductionPercent,
        float damageReduced
    ) {
        /**
         * Whether any reduction was applied.
         */
        public boolean wasReduced() {
            return reductionPercent > 0 && damageReduced > 0;
        }

        /**
         * Whether the damage was fully negated (100% reduction).
         */
        public boolean wasFullyNegated() {
            return finalDamage <= 0 && originalDamage > 0;
        }
    }
}
