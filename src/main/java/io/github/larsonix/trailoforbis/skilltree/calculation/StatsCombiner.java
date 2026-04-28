package io.github.larsonix.trailoforbis.skilltree.calculation;

import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.skilltree.model.StatType;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Combines base stats with aggregated modifiers using PoE-style calculation.
 *
 * <p>Formula for each stat:
 * Final = (Base + Flat) × (1 + Sum(Percent)/100) × Product(1 + Multiplier/100)
 *
 * <p>Example:
 * <ul>
 *   <li>Base physical damage: 100</li>
 *   <li>Flat from nodes: +20</li>
 *   <li>Percent from nodes: +50%, +30% (total 80%)</li>
 *   <li>Multiplier from keystone: 20% more</li>
 *   <li>Final: (100 + 20) × 1.80 × 1.20 = 259.2</li>
 * </ul>
 */
public class StatsCombiner {

    /**
     * Applies aggregated modifiers to base stats.
     *
     * @param baseStats Stats from AttributeCalculator (base values)
     * @param modifiers Aggregated modifiers from skill tree
     * @return New ComputedStats with modifiers applied
     */
    @Nonnull
    public ComputedStats combine(@Nonnull ComputedStats baseStats,
                                  @Nonnull AggregatedModifiers modifiers) {
        ComputedStats.Builder builder = baseStats.toBuilder();

        // Apply each stat type
        // Core Stats
        applyModifier(builder, modifiers, StatType.MAX_HEALTH, baseStats.getMaxHealth());
        applyModifier(builder, modifiers, StatType.MAX_MANA, baseStats.getMaxMana());
        applyModifier(builder, modifiers, StatType.MAX_STAMINA, baseStats.getMaxStamina());

        // Flat Damage
        applyModifier(builder, modifiers, StatType.PHYSICAL_DAMAGE, baseStats.getPhysicalDamage());
        applyModifier(builder, modifiers, StatType.SPELL_DAMAGE, baseStats.getSpellDamage());
        applyModifier(builder, modifiers, StatType.FIRE_DAMAGE, baseStats.getFireDamage());
        applyModifier(builder, modifiers, StatType.WATER_DAMAGE, baseStats.getWaterDamage());
        applyModifier(builder, modifiers, StatType.LIGHTNING_DAMAGE, baseStats.getLightningDamage());
        applyModifier(builder, modifiers, StatType.EARTH_DAMAGE, baseStats.getEarthDamage());
        applyModifier(builder, modifiers, StatType.WIND_DAMAGE, baseStats.getWindDamage());
        applyModifier(builder, modifiers, StatType.VOID_DAMAGE, baseStats.getVoidDamage());

        // Percent Damage
        applyModifier(builder, modifiers, StatType.PHYSICAL_DAMAGE_PERCENT, baseStats.getPhysicalDamagePercent());
        applyModifier(builder, modifiers, StatType.SPELL_DAMAGE_PERCENT, baseStats.getSpellDamagePercent());
        applyModifier(builder, modifiers, StatType.FIRE_DAMAGE_PERCENT, baseStats.getFireDamagePercent());
        applyModifier(builder, modifiers, StatType.WATER_DAMAGE_PERCENT, baseStats.getWaterDamagePercent());
        applyModifier(builder, modifiers, StatType.LIGHTNING_DAMAGE_PERCENT, baseStats.getLightningDamagePercent());
        applyModifier(builder, modifiers, StatType.EARTH_DAMAGE_PERCENT, baseStats.getEarthDamagePercent());
        applyModifier(builder, modifiers, StatType.WIND_DAMAGE_PERCENT, baseStats.getWindDamagePercent());
        applyModifier(builder, modifiers, StatType.VOID_DAMAGE_PERCENT, baseStats.getVoidDamagePercent());

        // Damage Multipliers (PoE-style "more" damage)
        applyModifier(builder, modifiers, StatType.FIRE_DAMAGE_MULTIPLIER, baseStats.getFireDamageMultiplier());
        applyModifier(builder, modifiers, StatType.WATER_DAMAGE_MULTIPLIER, baseStats.getWaterDamageMultiplier());
        applyModifier(builder, modifiers, StatType.LIGHTNING_DAMAGE_MULTIPLIER, baseStats.getLightningDamageMultiplier());
        applyModifier(builder, modifiers, StatType.EARTH_DAMAGE_MULTIPLIER, baseStats.getEarthDamageMultiplier());
        applyModifier(builder, modifiers, StatType.WIND_DAMAGE_MULTIPLIER, baseStats.getWindDamageMultiplier());
        applyModifier(builder, modifiers, StatType.VOID_DAMAGE_MULTIPLIER, baseStats.getVoidDamageMultiplier());

        // Critical
        applyModifier(builder, modifiers, StatType.CRITICAL_CHANCE, baseStats.getCriticalChance());
        applyModifier(builder, modifiers, StatType.CRITICAL_MULTIPLIER, baseStats.getCriticalMultiplier());

        // Attack Types
        applyModifier(builder, modifiers, StatType.MELEE_DAMAGE_PERCENT, baseStats.getMeleeDamagePercent());
        applyModifier(builder, modifiers, StatType.PROJECTILE_DAMAGE_PERCENT, baseStats.getProjectileDamagePercent());
        applyModifier(builder, modifiers, StatType.CHARGED_ATTACK_DAMAGE_PERCENT, baseStats.getChargedAttackDamagePercent());

        // Speed & Movement
        applyModifier(builder, modifiers, StatType.MOVEMENT_SPEED_PERCENT, baseStats.getMovementSpeedPercent());
        applyModifier(builder, modifiers, StatType.WALK_SPEED_PERCENT, baseStats.getWalkSpeedPercent());
        applyModifier(builder, modifiers, StatType.ATTACK_SPEED_PERCENT, baseStats.getAttackSpeedPercent());
        applyModifier(builder, modifiers, StatType.JUMP_FORCE_BONUS, baseStats.getJumpForceBonus());
        applyModifier(builder, modifiers, StatType.JUMP_FORCE_PERCENT, baseStats.getJumpForcePercent());
        applyModifier(builder, modifiers, StatType.SPRINT_SPEED_BONUS, baseStats.getSprintSpeedBonus());
        applyModifier(builder, modifiers, StatType.CLIMB_SPEED_BONUS, baseStats.getClimbSpeedBonus());
        applyModifier(builder, modifiers, StatType.CROUCH_SPEED_PERCENT, baseStats.getCrouchSpeedPercent());

        // Defense
        applyModifier(builder, modifiers, StatType.ARMOR, baseStats.getArmor());
        applyModifier(builder, modifiers, StatType.ARMOR_PERCENT, baseStats.getArmorPercent());
        applyModifier(builder, modifiers, StatType.EVASION, baseStats.getEvasion());
        applyModifier(builder, modifiers, StatType.ENERGY_SHIELD, baseStats.getEnergyShield());
        applyModifier(builder, modifiers, StatType.CRIT_NULLIFY_CHANCE, baseStats.getCritNullifyChance());
        applyModifier(builder, modifiers, StatType.KNOCKBACK_RESISTANCE, baseStats.getKnockbackResistance());
        applyModifier(builder, modifiers, StatType.PARRY_CHANCE, baseStats.getParryChance());
        applyModifier(builder, modifiers, StatType.CRITICAL_REDUCTION, baseStats.getCriticalReduction());
        applyModifier(builder, modifiers, StatType.BLOCK_DAMAGE_REDUCTION, baseStats.getBlockDamageReduction());
        applyModifier(builder, modifiers, StatType.STAMINA_DRAIN_REDUCTION, baseStats.getStaminaDrainReduction());
        applyModifier(builder, modifiers, StatType.ARMOR_PENETRATION, baseStats.getArmorPenetration());

        // Resistances
        applyModifier(builder, modifiers, StatType.FIRE_RESISTANCE, baseStats.getFireResistance());
        applyModifier(builder, modifiers, StatType.WATER_RESISTANCE, baseStats.getWaterResistance());
        applyModifier(builder, modifiers, StatType.LIGHTNING_RESISTANCE, baseStats.getLightningResistance());
        applyModifier(builder, modifiers, StatType.EARTH_RESISTANCE, baseStats.getEarthResistance());
        applyModifier(builder, modifiers, StatType.WIND_RESISTANCE, baseStats.getWindResistance());
        applyModifier(builder, modifiers, StatType.VOID_RESISTANCE, baseStats.getVoidResistance());

        // Penetration
        applyModifier(builder, modifiers, StatType.FIRE_PENETRATION, baseStats.getFirePenetration());
        applyModifier(builder, modifiers, StatType.WATER_PENETRATION, baseStats.getWaterPenetration());
        applyModifier(builder, modifiers, StatType.LIGHTNING_PENETRATION, baseStats.getLightningPenetration());
        applyModifier(builder, modifiers, StatType.EARTH_PENETRATION, baseStats.getEarthPenetration());
        applyModifier(builder, modifiers, StatType.WIND_PENETRATION, baseStats.getWindPenetration());
        applyModifier(builder, modifiers, StatType.VOID_PENETRATION, baseStats.getVoidPenetration());

        // Regeneration
        applyModifier(builder, modifiers, StatType.HEALTH_REGEN, baseStats.getHealthRegen());
        applyModifier(builder, modifiers, StatType.MANA_REGEN, baseStats.getManaRegen());
        applyModifier(builder, modifiers, StatType.STAMINA_REGEN, baseStats.getStaminaRegen());
        applyModifier(builder, modifiers, StatType.HEALTH_REGEN_PERCENT, baseStats.getHealthRegenPercent());

        // Accuracy
        applyModifier(builder, modifiers, StatType.ACCURACY, baseStats.getAccuracy());
        applyModifier(builder, modifiers, StatType.ACCURACY_PERCENT, baseStats.getAccuracyPercent());

        // True Damage
        applyModifier(builder, modifiers, StatType.PERCENT_HIT_AS_TRUE_DAMAGE, baseStats.getPercentHitAsTrueDamage());

        // Life Leech (maps to lifeLeech field — separate from lifeSteal)
        applyModifier(builder, modifiers, StatType.LIFE_LEECH, baseStats.getLifeLeech());

        // Global Damage
        applyModifier(builder, modifiers, StatType.ALL_DAMAGE_PERCENT, baseStats.getAllDamagePercent());
        applyModifier(builder, modifiers, StatType.MELEE_DAMAGE, baseStats.getMeleeDamage());

        // Leech
        applyModifier(builder, modifiers, StatType.MANA_LEECH, baseStats.getManaLeech());

        // Defensive - new stats
        applyModifier(builder, modifiers, StatType.PHYSICAL_RESISTANCE, baseStats.getPhysicalResistance());
        applyModifier(builder, modifiers, StatType.DODGE_CHANCE, baseStats.getDodgeChance());

        // Conditional
        applyModifier(builder, modifiers, StatType.DAMAGE_AT_LOW_LIFE, baseStats.getDamageAtLowLife());

        // Cost
        applyModifier(builder, modifiers, StatType.MANA_COST_REDUCTION, baseStats.getManaCostReduction());

        // Special
        applyModifier(builder, modifiers, StatType.MANA_AS_DAMAGE_BUFFER, baseStats.getManaAsDamageBuffer());

        // Conversion
        applyModifier(builder, modifiers, StatType.FIRE_CONVERSION, baseStats.getFireConversion());
        applyModifier(builder, modifiers, StatType.WATER_CONVERSION, baseStats.getWaterConversion());
        applyModifier(builder, modifiers, StatType.LIGHTNING_CONVERSION, baseStats.getLightningConversion());
        applyModifier(builder, modifiers, StatType.EARTH_CONVERSION, baseStats.getEarthConversion());
        applyModifier(builder, modifiers, StatType.WIND_CONVERSION, baseStats.getWindConversion());
        applyModifier(builder, modifiers, StatType.VOID_CONVERSION, baseStats.getVoidConversion());

        // Status Effects
        applyModifier(builder, modifiers, StatType.STATUS_EFFECT_CHANCE, baseStats.getStatusEffectChance());
        applyModifier(builder, modifiers, StatType.STATUS_EFFECT_DURATION, baseStats.getStatusEffectDuration());

        // Ailment Damage
        applyModifier(builder, modifiers, StatType.BURN_DAMAGE, baseStats.getBurnDamage());
        applyModifier(builder, modifiers, StatType.FREEZE_DAMAGE, baseStats.getFreezeDamage());
        applyModifier(builder, modifiers, StatType.SHOCK_DAMAGE, baseStats.getShockDamage());
        applyModifier(builder, modifiers, StatType.POISON_DAMAGE, baseStats.getPoisonDamage());

        // Ailment Damage Percent
        applyModifier(builder, modifiers, StatType.BURN_DAMAGE_PERCENT, baseStats.getBurnDamagePercent());
        applyModifier(builder, modifiers, StatType.FROST_DAMAGE_PERCENT, baseStats.getFrostDamagePercent());
        applyModifier(builder, modifiers, StatType.SHOCK_DAMAGE_PERCENT, baseStats.getShockDamagePercent());

        // Ailment Thresholds
        applyModifier(builder, modifiers, StatType.BURN_THRESHOLD, baseStats.getBurnThreshold());
        applyModifier(builder, modifiers, StatType.FREEZE_THRESHOLD, baseStats.getFreezeThreshold());
        applyModifier(builder, modifiers, StatType.SHOCK_THRESHOLD, baseStats.getShockThreshold());

        // Thorns
        applyModifier(builder, modifiers, StatType.THORNS_DAMAGE_PERCENT, baseStats.getThornsDamagePercent());

        // Fall Damage Reduction and Block Chance
        applyModifier(builder, modifiers, StatType.FALL_DAMAGE_REDUCTION, baseStats.getFallDamageReduction());
        applyModifier(builder, modifiers, StatType.PASSIVE_BLOCK_CHANCE, baseStats.getPassiveBlockChance());

        // ═══════════════════════════════════════════════════════════════════
        // NEW STATS - Offensive
        // ═══════════════════════════════════════════════════════════════════

        // Status Effect Chances
        applyModifier(builder, modifiers, StatType.IGNITE_CHANCE, baseStats.getIgniteChance());
        applyModifier(builder, modifiers, StatType.FREEZE_CHANCE, baseStats.getFreezeChance());
        applyModifier(builder, modifiers, StatType.SHOCK_CHANCE, baseStats.getShockChance());

        // Generic Damage Modifiers
        applyModifier(builder, modifiers, StatType.DAMAGE_PERCENT, baseStats.getDamagePercent());
        applyModifier(builder, modifiers, StatType.DAMAGE_MULTIPLIER, baseStats.getDamageMultiplier());
        applyModifier(builder, modifiers, StatType.EXECUTE_DAMAGE_PERCENT, baseStats.getExecuteDamagePercent());
        applyModifier(builder, modifiers, StatType.DOT_DAMAGE_PERCENT, baseStats.getDotDamagePercent());
        applyModifier(builder, modifiers, StatType.NON_CRIT_DAMAGE_PERCENT, baseStats.getNonCritDamagePercent());

        // Conditional Damage
        applyModifier(builder, modifiers, StatType.DAMAGE_VS_FROZEN_PERCENT, baseStats.getDamageVsFrozenPercent());
        applyModifier(builder, modifiers, StatType.DAMAGE_VS_SHOCKED_PERCENT, baseStats.getDamageVsShockedPercent());
        applyModifier(builder, modifiers, StatType.DAMAGE_FROM_MANA_PERCENT, baseStats.getDamageFromManaPercent());

        // Leech
        applyModifier(builder, modifiers, StatType.MANA_STEAL, baseStats.getManaSteal());

        // Duration Modifiers
        applyModifier(builder, modifiers, StatType.BURN_DURATION_PERCENT, baseStats.getBurnDurationPercent());

        // Projectile
        applyModifier(builder, modifiers, StatType.PROJECTILE_SPEED_PERCENT, baseStats.getProjectileSpeedPercent());
        applyModifier(builder, modifiers, StatType.PROJECTILE_GRAVITY_PERCENT, baseStats.getProjectileGravityPercent());

        // Spell Modifiers
        applyModifier(builder, modifiers, StatType.SPELL_PENETRATION, baseStats.getSpellPenetration());

        // Life Steal
        applyModifier(builder, modifiers, StatType.LIFE_STEAL, baseStats.getLifeSteal());

        // ═══════════════════════════════════════════════════════════════════
        // NEW STATS - Defensive
        // ═══════════════════════════════════════════════════════════════════

        applyModifier(builder, modifiers, StatType.BLOCK_CHANCE, baseStats.getBlockChance());
        applyModifier(builder, modifiers, StatType.BLOCK_HEAL_PERCENT, baseStats.getBlockHealPercent());
        applyModifier(builder, modifiers, StatType.BLOCK_RECOVERY_PERCENT, baseStats.getBlockRecoveryPercent());
        applyModifier(builder, modifiers, StatType.SHIELD_EFFECTIVENESS_PERCENT, baseStats.getShieldEffectivenessPercent());
        applyModifier(builder, modifiers, StatType.HEALTH_RECOVERY_PERCENT, baseStats.getHealthRecoveryPercent());
        applyModifier(builder, modifiers, StatType.DAMAGE_TAKEN_PERCENT, baseStats.getDamageTakenPercent());
        applyModifier(builder, modifiers, StatType.DAMAGE_WHEN_HIT_PERCENT, baseStats.getDamageWhenHitPercent());

        // ═══════════════════════════════════════════════════════════════════
        // NEW STATS - Resource
        // ═══════════════════════════════════════════════════════════════════

        applyModifier(builder, modifiers, StatType.MAX_HEALTH_PERCENT, baseStats.getMaxHealthPercent());
        applyModifier(builder, modifiers, StatType.MAX_MANA_PERCENT, baseStats.getMaxManaPercent());
        applyModifier(builder, modifiers, StatType.MANA_COST_PERCENT, baseStats.getManaCostPercent());
        applyModifier(builder, modifiers, StatType.MAX_STAMINA_PERCENT, baseStats.getMaxStaminaPercent());
        applyModifier(builder, modifiers, StatType.STAMINA_REGEN_PERCENT, baseStats.getStaminaRegenPercent());
        applyModifier(builder, modifiers, StatType.STAMINA_REGEN_START_DELAY, baseStats.getStaminaRegenStartDelay());
        applyModifier(builder, modifiers, StatType.SIGNATURE_ENERGY_MAX_PERCENT, baseStats.getSignatureEnergyMaxPercent());
        applyModifier(builder, modifiers, StatType.SIGNATURE_ENERGY_PER_HIT, baseStats.getSignatureEnergyPerHit());
        applyModifier(builder, modifiers, StatType.MANA_ON_KILL, baseStats.getManaOnKill());

        // ═══════════════════════════════════════════════════════════════════
        // Octant Keystone Stats (Tier 2-3: stored in ComputedStats fields)
        // ═══════════════════════════════════════════════════════════════════
        applyModifier(builder, modifiers, StatType.DETONATE_DOT_ON_CRIT, baseStats.getDetonateDotOnCrit());
        applyModifier(builder, modifiers, StatType.CONSECUTIVE_HIT_BONUS, baseStats.getConsecutiveHitBonus());
        applyModifier(builder, modifiers, StatType.BLOCK_COUNTER_DAMAGE, baseStats.getBlockCounterDamage());
        applyModifier(builder, modifiers, StatType.SPELL_ECHO_CHANCE, baseStats.getSpellEchoChance());
        applyModifier(builder, modifiers, StatType.SHIELD_REGEN_ON_DOT, baseStats.getShieldRegenOnDot());
        applyModifier(builder, modifiers, StatType.IMMUNITY_ON_AILMENT, baseStats.getImmunityOnAilment());

        // Hexcode Compatibility Stats (active when Hexcode loaded)
        applyModifier(builder, modifiers, StatType.VOLATILITY_MAX, baseStats.getVolatilityMax());
        applyModifier(builder, modifiers, StatType.MAGIC_POWER, baseStats.getMagicPower());
        applyModifier(builder, modifiers, StatType.DRAW_ACCURACY, baseStats.getDrawAccuracy());
        applyModifier(builder, modifiers, StatType.CAST_SPEED, baseStats.getCastSpeed());
        applyModifier(builder, modifiers, StatType.MAGIC_CHARGES, (float) baseStats.getMagicCharges());

        // Evasion Percent: fold into existing evasion as a percent modifier
        if (modifiers.hasModifiers(StatType.EVASION_PERCENT)) {
            float evasionBase = builder.build().getEvasion(); // get current evasion after EVASION was applied
            float evasionPctBonus = modifiers.getFlatSum(StatType.EVASION_PERCENT);
            builder.evasion(evasionBase * (1f + evasionPctBonus / 100f));
        }

        // Elemental Resistance: distribute computed value to all 6 individual element resistances
        if (modifiers.hasModifiers(StatType.ELEMENTAL_RESISTANCE)) {
            float flat = modifiers.getFlatSum(StatType.ELEMENTAL_RESISTANCE);
            float percent = modifiers.getPercentSum(StatType.ELEMENTAL_RESISTANCE);
            float elemResValue = flat;
            elemResValue *= (1f + percent / 100f);
            for (float mult : modifiers.getMultipliers(StatType.ELEMENTAL_RESISTANCE)) {
                elemResValue *= (1f + mult / 100f);
            }
            // Build intermediate to read current values, then re-apply with additions
            ComputedStats intermediate = builder.build();
            builder.fireResistance(intermediate.getFireResistance() + elemResValue);
            builder.waterResistance(intermediate.getWaterResistance() + elemResValue);
            builder.lightningResistance(intermediate.getLightningResistance() + elemResValue);
            builder.earthResistance(intermediate.getEarthResistance() + elemResValue);
            builder.windResistance(intermediate.getWindResistance() + elemResValue);
            builder.voidResistance(intermediate.getVoidResistance() + elemResValue);
        }

        // All Elemental Damage Percent: distribute to all 6 element damage percentages
        if (modifiers.hasModifiers(StatType.ALL_ELEMENTAL_DAMAGE_PERCENT)) {
            float flat = modifiers.getFlatSum(StatType.ALL_ELEMENTAL_DAMAGE_PERCENT);
            float percent = modifiers.getPercentSum(StatType.ALL_ELEMENTAL_DAMAGE_PERCENT);
            float allElemValue = flat;
            allElemValue *= (1f + percent / 100f);
            for (float mult : modifiers.getMultipliers(StatType.ALL_ELEMENTAL_DAMAGE_PERCENT)) {
                allElemValue *= (1f + mult / 100f);
            }
            // Build intermediate to read current values, then re-apply with additions
            ComputedStats intermediate = builder.build();
            builder.fireDamagePercent(intermediate.getFireDamagePercent() + allElemValue);
            builder.waterDamagePercent(intermediate.getWaterDamagePercent() + allElemValue);
            builder.lightningDamagePercent(intermediate.getLightningDamagePercent() + allElemValue);
            builder.earthDamagePercent(intermediate.getEarthDamagePercent() + allElemValue);
            builder.windDamagePercent(intermediate.getWindDamagePercent() + allElemValue);
            builder.voidDamagePercent(intermediate.getVoidDamagePercent() + allElemValue);
        }

        // ═══════════════════════════════════════════════════════════════════
        // Octant Keystone Stats (Tier 1: derived from already-combined stats)
        // ═══════════════════════════════════════════════════════════════════

        // HP_SCALING_DAMAGE: bonus physDmg% = (maxHP / 100) × statValue
        if (modifiers.hasModifiers(StatType.HP_SCALING_DAMAGE)) {
            float hpScaling = modifiers.getFlatSum(StatType.HP_SCALING_DAMAGE);
            ComputedStats intermediate = builder.build();
            float bonusPct = (intermediate.getMaxHealth() / 100f) * hpScaling;
            builder.physicalDamagePercent(intermediate.getPhysicalDamagePercent() + bonusPct);
        }

        // SPEED_TO_SPELL_POWER: bonus spellDmg% = moveSpeedPct × (statValue / 100)
        if (modifiers.hasModifiers(StatType.SPEED_TO_SPELL_POWER)) {
            float speedConvert = modifiers.getFlatSum(StatType.SPEED_TO_SPELL_POWER);
            ComputedStats intermediate = builder.build();
            float bonusPct = intermediate.getMovementSpeedPercent() * (speedConvert / 100f);
            builder.spellDamagePercent(intermediate.getSpellDamagePercent() + bonusPct);
        }

        // ATK_SPEED_TO_SPELL_POWER: bonus spellDmg% = atkSpeedPct × (statValue / 100)
        if (modifiers.hasModifiers(StatType.ATK_SPEED_TO_SPELL_POWER)) {
            float atkSpeedConvert = modifiers.getFlatSum(StatType.ATK_SPEED_TO_SPELL_POWER);
            ComputedStats intermediate = builder.build();
            float bonusPct = intermediate.getAttackSpeedPercent() * (atkSpeedConvert / 100f);
            builder.spellDamagePercent(intermediate.getSpellDamagePercent() + bonusPct);
        }

        // EVASION_TO_ARMOR: bonus armor = evasion × (statValue / 100)
        if (modifiers.hasModifiers(StatType.EVASION_TO_ARMOR)) {
            float convertPct = modifiers.getFlatSum(StatType.EVASION_TO_ARMOR);
            ComputedStats intermediate = builder.build();
            float bonusArmor = intermediate.getEvasion() * (convertPct / 100f);
            builder.armor(intermediate.getArmor() + bonusArmor);
        }

        return builder.build();
    }

    /**
     * Applies the PoE formula to a single stat and sets it in the builder.
     */
    private void applyModifier(ComputedStats.Builder builder, AggregatedModifiers mods,
                               StatType stat, float baseValue) {
        // Skip if no modifiers for this stat (optimization)
        if (!mods.hasModifiers(stat)) {
            return;
        }

        float flat = mods.getFlatSum(stat);
        float percent = mods.getPercentSum(stat);
        List<Float> multipliers = mods.getMultipliers(stat);

        // PoE formula: (Base + Flat) × (1 + Percent/100) × Product(1 + Mult/100)
        float result = baseValue + flat;
        result *= (1f + percent / 100f);
        for (float mult : multipliers) {
            result *= (1f + mult / 100f);
        }

        // Prevent negative stats (can happen with extreme negative modifiers like -150%)
        result = Math.max(0f, result);

        // Apply to builder using explicit switch (compile-time type safety)
        setStatInBuilder(builder, stat, result);
    }

    /**
     * Sets a stat value in the builder based on StatType.
     * Delegates to the table-driven setter on each StatType enum value.
     * Stats without a setter (fan-out, derived, not-yet-wired) are no-ops.
     */
    private void setStatInBuilder(ComputedStats.Builder builder, StatType stat, float value) {
        stat.applyToBuilder(builder, value);
    }
}
