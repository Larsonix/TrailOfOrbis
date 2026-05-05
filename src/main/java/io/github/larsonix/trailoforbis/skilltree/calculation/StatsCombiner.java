package io.github.larsonix.trailoforbis.skilltree.calculation;

import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.skilltree.model.StatType;

import javax.annotation.Nonnull;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Combines base stats with aggregated modifiers using the ARPG formula:
 *
 * <pre>
 * Final = (Base + ALL_FLAT) × (1 + ALL_PERCENT/100) × Π(1 + MORE/100)
 * </pre>
 *
 * <p>ALL sources (attributes, skill tree, gear, conditionals) deposit into three tiers
 * per stat. The formula is applied ONCE at the end of the pipeline — not per-source.
 *
 * <p>This class handles the skill tree's contribution by depositing modifiers into
 * the correct accumulator fields. The final formula application happens later:
 * <ul>
 *   <li>Resource stats (HP, Mana, etc.): {@code consolidateResourcePercents()} in ComputedStats</li>
 *   <li>Damage stats: {@code RPGDamageCalculator} at combat time</li>
 *   <li>Movement stats: {@code StatsApplicationSystem} at application time</li>
 * </ul>
 *
 * <h3>Three stat categories:</h3>
 * <ol>
 *   <li><b>Accumulator stats</b> (_PERCENT, _MULTIPLIER fields): All modifier types
 *       (FLAT, PERCENT, MULTIPLIER) add to the value. These are percentage pools
 *       consumed by a later formula step.</li>
 *   <li><b>Base stats with a percent pair</b> (MAX_HEALTH → MAX_HEALTH_PERCENT):
 *       FLAT adds to the base. PERCENT routes to the paired accumulator.
 *       MULTIPLIER stores for end-of-pipeline application.</li>
 *   <li><b>Standalone base stats</b> (CRITICAL_CHANCE, resistances, etc.):
 *       All modifier types add directly. No PoE multiplication — the values are
 *       final-use fields consumed as-is by combat/movement systems.</li>
 * </ol>
 */
public class StatsCombiner {

    /**
     * Maps base stats to their percent accumulator counterpart.
     *
     * <p>When a base stat receives a PERCENT-type modifier from the skill tree,
     * the value is routed to the paired accumulator instead of multiplying the base.
     * This ensures all percent sources stack additively in one pool.
     */
    private static final Map<StatType, StatType> PERCENT_ROUTING;

    static {
        PERCENT_ROUTING = new EnumMap<>(StatType.class);
        PERCENT_ROUTING.put(StatType.MAX_HEALTH, StatType.MAX_HEALTH_PERCENT);
        PERCENT_ROUTING.put(StatType.MAX_MANA, StatType.MAX_MANA_PERCENT);
        PERCENT_ROUTING.put(StatType.MAX_STAMINA, StatType.MAX_STAMINA_PERCENT);
        PERCENT_ROUTING.put(StatType.ARMOR, StatType.ARMOR_PERCENT);
        PERCENT_ROUTING.put(StatType.ACCURACY, StatType.ACCURACY_PERCENT);
        PERCENT_ROUTING.put(StatType.PHYSICAL_DAMAGE, StatType.PHYSICAL_DAMAGE_PERCENT);
        PERCENT_ROUTING.put(StatType.SPELL_DAMAGE, StatType.SPELL_DAMAGE_PERCENT);
        PERCENT_ROUTING.put(StatType.FIRE_DAMAGE, StatType.FIRE_DAMAGE_PERCENT);
        PERCENT_ROUTING.put(StatType.WATER_DAMAGE, StatType.WATER_DAMAGE_PERCENT);
        PERCENT_ROUTING.put(StatType.LIGHTNING_DAMAGE, StatType.LIGHTNING_DAMAGE_PERCENT);
        PERCENT_ROUTING.put(StatType.EARTH_DAMAGE, StatType.EARTH_DAMAGE_PERCENT);
        PERCENT_ROUTING.put(StatType.WIND_DAMAGE, StatType.WIND_DAMAGE_PERCENT);
        PERCENT_ROUTING.put(StatType.VOID_DAMAGE, StatType.VOID_DAMAGE_PERCENT);
        PERCENT_ROUTING.put(StatType.HEALTH_REGEN, StatType.HEALTH_REGEN_PERCENT);
        PERCENT_ROUTING.put(StatType.MANA_REGEN, StatType.MANA_REGEN_PERCENT);
        PERCENT_ROUTING.put(StatType.STAMINA_REGEN, StatType.STAMINA_REGEN_PERCENT);
        PERCENT_ROUTING.put(StatType.ENERGY_SHIELD_REGEN, StatType.ENERGY_SHIELD_REGEN_PERCENT);
        PERCENT_ROUTING.put(StatType.BURN_DAMAGE, StatType.BURN_DAMAGE_PERCENT);
        PERCENT_ROUTING.put(StatType.ENERGY_SHIELD, StatType.ENERGY_SHIELD_PERCENT);
    }

    /**
     * Applies aggregated modifiers to base stats using the deposit model.
     *
     * <p>Instead of applying the PoE formula per-stat, this method deposits
     * modifier values into the correct flat/percent/multiplier accumulators.
     * The actual formula is applied once at the end of the pipeline.
     *
     * @param baseStats Stats from AttributeCalculator (base values)
     * @param modifiers Aggregated modifiers from skill tree
     * @return New ComputedStats with modifiers deposited into correct accumulators
     */
    @Nonnull
    public ComputedStats combine(@Nonnull ComputedStats baseStats,
                                  @Nonnull AggregatedModifiers modifiers) {
        ComputedStats.Builder builder = baseStats.toBuilder();

        // Pending percent values routed from base stats to their accumulator pairs.
        // Collected during phase 1, applied during phase 2.
        Map<StatType, Float> pendingPercent = new EnumMap<>(StatType.class);

        // ═══════════════════════════════════════════════════════════════════
        // PHASE 1: Base stats (FLAT adds to base, PERCENT routes to accumulator)
        // ═══════════════════════════════════════════════════════════════════

        // Core Stats
        depositBase(builder, modifiers, StatType.MAX_HEALTH, baseStats.getMaxHealth(), pendingPercent);
        depositBase(builder, modifiers, StatType.MAX_MANA, baseStats.getMaxMana(), pendingPercent);
        depositBase(builder, modifiers, StatType.MAX_STAMINA, baseStats.getMaxStamina(), pendingPercent);

        // Flat Damage
        depositBase(builder, modifiers, StatType.PHYSICAL_DAMAGE, baseStats.getPhysicalDamage(), pendingPercent);
        depositBase(builder, modifiers, StatType.SPELL_DAMAGE, baseStats.getSpellDamage(), pendingPercent);
        depositBase(builder, modifiers, StatType.FIRE_DAMAGE, baseStats.getFireDamage(), pendingPercent);
        depositBase(builder, modifiers, StatType.WATER_DAMAGE, baseStats.getWaterDamage(), pendingPercent);
        depositBase(builder, modifiers, StatType.LIGHTNING_DAMAGE, baseStats.getLightningDamage(), pendingPercent);
        depositBase(builder, modifiers, StatType.EARTH_DAMAGE, baseStats.getEarthDamage(), pendingPercent);
        depositBase(builder, modifiers, StatType.WIND_DAMAGE, baseStats.getWindDamage(), pendingPercent);
        depositBase(builder, modifiers, StatType.VOID_DAMAGE, baseStats.getVoidDamage(), pendingPercent);

        // Defense
        depositBase(builder, modifiers, StatType.ARMOR, baseStats.getArmor(), pendingPercent);
        depositBase(builder, modifiers, StatType.ENERGY_SHIELD, baseStats.getEnergyShield(), pendingPercent);
        depositBase(builder, modifiers, StatType.ACCURACY, baseStats.getAccuracy(), pendingPercent);

        // Regeneration
        depositBase(builder, modifiers, StatType.HEALTH_REGEN, baseStats.getHealthRegen(), pendingPercent);
        depositBase(builder, modifiers, StatType.MANA_REGEN, baseStats.getManaRegen(), pendingPercent);
        depositBase(builder, modifiers, StatType.STAMINA_REGEN, baseStats.getStaminaRegen(), pendingPercent);
        depositBase(builder, modifiers, StatType.ENERGY_SHIELD_REGEN, baseStats.getEnergyShieldRegen(), pendingPercent);
        depositBase(builder, modifiers, StatType.ENERGY_SHIELD_REGEN_DELAY, baseStats.getEnergyShieldRegenDelay(), pendingPercent);

        // Ailment Damage
        depositBase(builder, modifiers, StatType.BURN_DAMAGE, baseStats.getBurnDamage(), pendingPercent);
        depositBase(builder, modifiers, StatType.FREEZE_DAMAGE, baseStats.getFreezeDamage(), pendingPercent);
        depositBase(builder, modifiers, StatType.SHOCK_DAMAGE, baseStats.getShockDamage(), pendingPercent);
        depositBase(builder, modifiers, StatType.POISON_DAMAGE, baseStats.getPoisonDamage(), pendingPercent);

        // Global Damage
        depositBase(builder, modifiers, StatType.MELEE_DAMAGE, baseStats.getMeleeDamage(), pendingPercent);

        // ═══════════════════════════════════════════════════════════════════
        // PHASE 1b: Standalone stats (all types add directly, no routing)
        // ═══════════════════════════════════════════════════════════════════

        // Critical
        depositStandalone(builder, modifiers, StatType.CRITICAL_CHANCE, baseStats.getCriticalChance());
        depositStandalone(builder, modifiers, StatType.CRITICAL_MULTIPLIER, baseStats.getCriticalMultiplier());

        // Defense (standalone — no percent pair)
        depositStandalone(builder, modifiers, StatType.EVASION, baseStats.getEvasion());
        depositStandalone(builder, modifiers, StatType.CRIT_NULLIFY_CHANCE, baseStats.getCritNullifyChance());
        depositStandalone(builder, modifiers, StatType.KNOCKBACK_RESISTANCE, baseStats.getKnockbackResistance());
        depositStandalone(builder, modifiers, StatType.PARRY_CHANCE, baseStats.getParryChance());
        depositStandalone(builder, modifiers, StatType.ARMOR_PENETRATION, baseStats.getArmorPenetration());

        // Penetration
        depositStandalone(builder, modifiers, StatType.FIRE_PENETRATION, baseStats.getFirePenetration());
        depositStandalone(builder, modifiers, StatType.WATER_PENETRATION, baseStats.getWaterPenetration());
        depositStandalone(builder, modifiers, StatType.LIGHTNING_PENETRATION, baseStats.getLightningPenetration());
        depositStandalone(builder, modifiers, StatType.EARTH_PENETRATION, baseStats.getEarthPenetration());
        depositStandalone(builder, modifiers, StatType.WIND_PENETRATION, baseStats.getWindPenetration());
        depositStandalone(builder, modifiers, StatType.VOID_PENETRATION, baseStats.getVoidPenetration());

        // Resistances
        depositStandalone(builder, modifiers, StatType.FIRE_RESISTANCE, baseStats.getFireResistance());
        depositStandalone(builder, modifiers, StatType.WATER_RESISTANCE, baseStats.getWaterResistance());
        depositStandalone(builder, modifiers, StatType.LIGHTNING_RESISTANCE, baseStats.getLightningResistance());
        depositStandalone(builder, modifiers, StatType.EARTH_RESISTANCE, baseStats.getEarthResistance());
        depositStandalone(builder, modifiers, StatType.WIND_RESISTANCE, baseStats.getWindResistance());
        depositStandalone(builder, modifiers, StatType.VOID_RESISTANCE, baseStats.getVoidResistance());

        // Status Effects
        depositStandalone(builder, modifiers, StatType.STATUS_EFFECT_CHANCE, baseStats.getStatusEffectChance());
        depositStandalone(builder, modifiers, StatType.STATUS_EFFECT_DURATION, baseStats.getStatusEffectDuration());

        // Conversion
        depositStandalone(builder, modifiers, StatType.FIRE_CONVERSION, baseStats.getFireConversion());
        depositStandalone(builder, modifiers, StatType.WATER_CONVERSION, baseStats.getWaterConversion());
        depositStandalone(builder, modifiers, StatType.LIGHTNING_CONVERSION, baseStats.getLightningConversion());
        depositStandalone(builder, modifiers, StatType.EARTH_CONVERSION, baseStats.getEarthConversion());
        depositStandalone(builder, modifiers, StatType.WIND_CONVERSION, baseStats.getWindConversion());
        depositStandalone(builder, modifiers, StatType.VOID_CONVERSION, baseStats.getVoidConversion());

        // True Damage
        depositStandalone(builder, modifiers, StatType.PERCENT_HIT_AS_TRUE_DAMAGE, baseStats.getPercentHitAsTrueDamage());

        // Leech & Sustain
        depositStandalone(builder, modifiers, StatType.LIFE_LEECH, baseStats.getLifeLeech());
        depositStandalone(builder, modifiers, StatType.MANA_LEECH, baseStats.getManaLeech());
        depositStandalone(builder, modifiers, StatType.LIFE_STEAL, baseStats.getLifeSteal());
        depositStandalone(builder, modifiers, StatType.MANA_STEAL, baseStats.getManaSteal());
        depositStandalone(builder, modifiers, StatType.MANA_ON_KILL, baseStats.getManaOnKill());
        depositStandalone(builder, modifiers, StatType.DAMAGE_AT_LOW_LIFE, baseStats.getDamageAtLowLife());
        depositStandalone(builder, modifiers, StatType.MANA_COST_REDUCTION, baseStats.getManaCostReduction());
        depositStandalone(builder, modifiers, StatType.MANA_AS_DAMAGE_BUFFER, baseStats.getManaAsDamageBuffer());

        // Ailment Thresholds
        depositStandalone(builder, modifiers, StatType.BURN_THRESHOLD, baseStats.getBurnThreshold());
        depositStandalone(builder, modifiers, StatType.FREEZE_THRESHOLD, baseStats.getFreezeThreshold());
        depositStandalone(builder, modifiers, StatType.SHOCK_THRESHOLD, baseStats.getShockThreshold());

        // Block & Shield
        depositStandalone(builder, modifiers, StatType.BLOCK_CHANCE, baseStats.getBlockChance());
        depositStandalone(builder, modifiers, StatType.BLOCK_DAMAGE_REDUCTION, baseStats.getBlockDamageReduction());
        depositStandalone(builder, modifiers, StatType.STAMINA_DRAIN_REDUCTION, baseStats.getStaminaDrainReduction());
        depositStandalone(builder, modifiers, StatType.FALL_DAMAGE_REDUCTION, baseStats.getFallDamageReduction());
        depositStandalone(builder, modifiers, StatType.PHYSICAL_RESISTANCE, baseStats.getPhysicalResistance());
        depositStandalone(builder, modifiers, StatType.DODGE_CHANCE, baseStats.getDodgeChance());
        depositStandalone(builder, modifiers, StatType.CRITICAL_REDUCTION, baseStats.getCriticalReduction());
        depositStandalone(builder, modifiers, StatType.SPELL_PENETRATION, baseStats.getSpellPenetration());

        // Ailment chances (additive percentage points, not accumulators)
        depositStandalone(builder, modifiers, StatType.IGNITE_CHANCE, baseStats.getIgniteChance());
        depositStandalone(builder, modifiers, StatType.FREEZE_CHANCE, baseStats.getFreezeChance());
        depositStandalone(builder, modifiers, StatType.SHOCK_CHANCE, baseStats.getShockChance());

        // Speed bonuses (flat values, not percent accumulators)
        depositStandalone(builder, modifiers, StatType.JUMP_FORCE_BONUS, baseStats.getJumpForceBonus());
        depositStandalone(builder, modifiers, StatType.SPRINT_SPEED_BONUS, baseStats.getSprintSpeedBonus());
        depositStandalone(builder, modifiers, StatType.CLIMB_SPEED_BONUS, baseStats.getClimbSpeedBonus());

        // Flat resource modifiers (not percent pools)
        depositStandalone(builder, modifiers, StatType.SIGNATURE_ENERGY_PER_HIT, baseStats.getSignatureEnergyPerHit());
        depositStandalone(builder, modifiers, StatType.STAMINA_REGEN_START_DELAY, baseStats.getStaminaRegenStartDelay());

        // Hexcode stats (flat values bridged to EntityStatMap)
        depositStandalone(builder, modifiers, StatType.VOLATILITY_MAX, baseStats.getVolatilityMax());
        depositStandalone(builder, modifiers, StatType.MAGIC_POWER, baseStats.getMagicPower());
        depositStandalone(builder, modifiers, StatType.MAGIC_CHARGES, (float) baseStats.getMagicCharges());
        depositStandalone(builder, modifiers, StatType.DRAW_ACCURACY, baseStats.getDrawAccuracy());
        depositStandalone(builder, modifiers, StatType.CAST_SPEED, baseStats.getCastSpeed());

        // ═══════════════════════════════════════════════════════════════════
        // PHASE 2: Accumulator stats (all types additive + pending from phase 1)
        // ═══════════════════════════════════════════════════════════════════

        // Resource percent accumulators
        depositAccumulator(builder, modifiers, StatType.MAX_HEALTH_PERCENT, baseStats.getMaxHealthPercent(), pendingPercent);
        depositAccumulator(builder, modifiers, StatType.MAX_MANA_PERCENT, baseStats.getMaxManaPercent(), pendingPercent);
        depositAccumulator(builder, modifiers, StatType.MAX_STAMINA_PERCENT, baseStats.getMaxStaminaPercent(), pendingPercent);

        // Defense percent accumulators
        depositAccumulator(builder, modifiers, StatType.ARMOR_PERCENT, baseStats.getArmorPercent(), pendingPercent);
        depositAccumulator(builder, modifiers, StatType.ENERGY_SHIELD_PERCENT, baseStats.getEnergyShieldPercent(), pendingPercent);
        depositAccumulator(builder, modifiers, StatType.ACCURACY_PERCENT, baseStats.getAccuracyPercent(), pendingPercent);
        depositAccumulator(builder, modifiers, StatType.HEALTH_REGEN_PERCENT, baseStats.getHealthRegenPercent(), pendingPercent);

        // Damage percent accumulators
        depositAccumulator(builder, modifiers, StatType.PHYSICAL_DAMAGE_PERCENT, baseStats.getPhysicalDamagePercent(), pendingPercent);
        depositAccumulator(builder, modifiers, StatType.SPELL_DAMAGE_PERCENT, baseStats.getSpellDamagePercent(), pendingPercent);
        depositAccumulator(builder, modifiers, StatType.FIRE_DAMAGE_PERCENT, baseStats.getFireDamagePercent(), pendingPercent);
        depositAccumulator(builder, modifiers, StatType.WATER_DAMAGE_PERCENT, baseStats.getWaterDamagePercent(), pendingPercent);
        depositAccumulator(builder, modifiers, StatType.LIGHTNING_DAMAGE_PERCENT, baseStats.getLightningDamagePercent(), pendingPercent);
        depositAccumulator(builder, modifiers, StatType.EARTH_DAMAGE_PERCENT, baseStats.getEarthDamagePercent(), pendingPercent);
        depositAccumulator(builder, modifiers, StatType.WIND_DAMAGE_PERCENT, baseStats.getWindDamagePercent(), pendingPercent);
        depositAccumulator(builder, modifiers, StatType.VOID_DAMAGE_PERCENT, baseStats.getVoidDamagePercent(), pendingPercent);

        // Damage multipliers (PoE "more" — additive within field, multiplicative at combat time)
        depositAccumulator(builder, modifiers, StatType.FIRE_DAMAGE_MULTIPLIER, baseStats.getFireDamageMultiplier(), pendingPercent);
        depositAccumulator(builder, modifiers, StatType.WATER_DAMAGE_MULTIPLIER, baseStats.getWaterDamageMultiplier(), pendingPercent);
        depositAccumulator(builder, modifiers, StatType.LIGHTNING_DAMAGE_MULTIPLIER, baseStats.getLightningDamageMultiplier(), pendingPercent);
        depositAccumulator(builder, modifiers, StatType.EARTH_DAMAGE_MULTIPLIER, baseStats.getEarthDamageMultiplier(), pendingPercent);
        depositAccumulator(builder, modifiers, StatType.WIND_DAMAGE_MULTIPLIER, baseStats.getWindDamageMultiplier(), pendingPercent);
        depositAccumulator(builder, modifiers, StatType.VOID_DAMAGE_MULTIPLIER, baseStats.getVoidDamageMultiplier(), pendingPercent);

        // Attack type percent accumulators
        depositAccumulator(builder, modifiers, StatType.MELEE_DAMAGE_PERCENT, baseStats.getMeleeDamagePercent(), pendingPercent);
        depositAccumulator(builder, modifiers, StatType.PROJECTILE_DAMAGE_PERCENT, baseStats.getProjectileDamagePercent(), pendingPercent);
        depositAccumulator(builder, modifiers, StatType.CHARGED_ATTACK_DAMAGE_PERCENT, baseStats.getChargedAttackDamagePercent(), pendingPercent);

        // Speed percent accumulators
        depositAccumulator(builder, modifiers, StatType.MOVEMENT_SPEED_PERCENT, baseStats.getMovementSpeedPercent(), pendingPercent);
        depositAccumulator(builder, modifiers, StatType.WALK_SPEED_PERCENT, baseStats.getWalkSpeedPercent(), pendingPercent);
        depositAccumulator(builder, modifiers, StatType.ATTACK_SPEED_PERCENT, baseStats.getAttackSpeedPercent(), pendingPercent);
        depositAccumulator(builder, modifiers, StatType.JUMP_FORCE_PERCENT, baseStats.getJumpForcePercent(), pendingPercent);
        depositAccumulator(builder, modifiers, StatType.CROUCH_SPEED_PERCENT, baseStats.getCrouchSpeedPercent(), pendingPercent);

        // Global damage accumulators
        depositAccumulator(builder, modifiers, StatType.ALL_DAMAGE_PERCENT, baseStats.getAllDamagePercent(), pendingPercent);
        depositAccumulator(builder, modifiers, StatType.DAMAGE_PERCENT, baseStats.getDamagePercent(), pendingPercent);
        depositAccumulator(builder, modifiers, StatType.DAMAGE_MULTIPLIER, baseStats.getDamageMultiplier(), pendingPercent);

        // Ailment percent accumulators
        depositAccumulator(builder, modifiers, StatType.BURN_DAMAGE_PERCENT, baseStats.getBurnDamagePercent(), pendingPercent);
        depositAccumulator(builder, modifiers, StatType.FROST_DAMAGE_PERCENT, baseStats.getFrostDamagePercent(), pendingPercent);
        depositAccumulator(builder, modifiers, StatType.SHOCK_DAMAGE_PERCENT, baseStats.getShockDamagePercent(), pendingPercent);

        // Other percent accumulators
        depositAccumulator(builder, modifiers, StatType.DOT_DAMAGE_PERCENT, baseStats.getDotDamagePercent(), pendingPercent);
        depositAccumulator(builder, modifiers, StatType.EXECUTE_DAMAGE_PERCENT, baseStats.getExecuteDamagePercent(), pendingPercent);
        depositAccumulator(builder, modifiers, StatType.NON_CRIT_DAMAGE_PERCENT, baseStats.getNonCritDamagePercent(), pendingPercent);
        depositAccumulator(builder, modifiers, StatType.DAMAGE_VS_FROZEN_PERCENT, baseStats.getDamageVsFrozenPercent(), pendingPercent);
        depositAccumulator(builder, modifiers, StatType.DAMAGE_VS_SHOCKED_PERCENT, baseStats.getDamageVsShockedPercent(), pendingPercent);
        depositAccumulator(builder, modifiers, StatType.DAMAGE_FROM_MANA_PERCENT, baseStats.getDamageFromManaPercent(), pendingPercent);
        depositAccumulator(builder, modifiers, StatType.BURN_DURATION_PERCENT, baseStats.getBurnDurationPercent(), pendingPercent);
        depositAccumulator(builder, modifiers, StatType.PROJECTILE_SPEED_PERCENT, baseStats.getProjectileSpeedPercent(), pendingPercent);
        depositAccumulator(builder, modifiers, StatType.PROJECTILE_GRAVITY_PERCENT, baseStats.getProjectileGravityPercent(), pendingPercent);
        depositAccumulator(builder, modifiers, StatType.THORNS_DAMAGE_PERCENT, baseStats.getThornsDamagePercent(), pendingPercent);
        depositAccumulator(builder, modifiers, StatType.BLOCK_HEAL_PERCENT, baseStats.getBlockHealPercent(), pendingPercent);
        depositAccumulator(builder, modifiers, StatType.BLOCK_RECOVERY_PERCENT, baseStats.getBlockRecoveryPercent(), pendingPercent);
        depositAccumulator(builder, modifiers, StatType.SHIELD_EFFECTIVENESS_PERCENT, baseStats.getShieldEffectivenessPercent(), pendingPercent);
        depositAccumulator(builder, modifiers, StatType.HEALTH_RECOVERY_PERCENT, baseStats.getHealthRecoveryPercent(), pendingPercent);
        depositAccumulator(builder, modifiers, StatType.DAMAGE_TAKEN_PERCENT, baseStats.getDamageTakenPercent(), pendingPercent);
        depositAccumulator(builder, modifiers, StatType.DAMAGE_WHEN_HIT_PERCENT, baseStats.getDamageWhenHitPercent(), pendingPercent);
        depositAccumulator(builder, modifiers, StatType.MANA_COST_PERCENT, baseStats.getManaCostPercent(), pendingPercent);
        depositAccumulator(builder, modifiers, StatType.SIGNATURE_ENERGY_MAX_PERCENT, baseStats.getSignatureEnergyMaxPercent(), pendingPercent);
        depositAccumulator(builder, modifiers, StatType.STAMINA_REGEN_PERCENT, baseStats.getStaminaRegenPercent(), pendingPercent);
        depositAccumulator(builder, modifiers, StatType.MANA_REGEN_PERCENT, baseStats.getManaRegenPercent(), pendingPercent);
        depositAccumulator(builder, modifiers, StatType.ENERGY_SHIELD_REGEN_PERCENT, baseStats.getEnergyShieldRegenPercent(), pendingPercent);

        // ═══════════════════════════════════════════════════════════════════
        // PHASE 3: Octant keystone stats (Tier 2-3: stored in ComputedStats)
        // ═══════════════════════════════════════════════════════════════════
        depositStandalone(builder, modifiers, StatType.DETONATE_DOT_ON_CRIT, baseStats.getDetonateDotOnCrit());
        depositStandalone(builder, modifiers, StatType.CONSECUTIVE_HIT_BONUS, baseStats.getConsecutiveHitBonus());
        depositStandalone(builder, modifiers, StatType.BLOCK_COUNTER_DAMAGE, baseStats.getBlockCounterDamage());
        depositStandalone(builder, modifiers, StatType.SPELL_ECHO_CHANCE, baseStats.getSpellEchoChance());
        depositStandalone(builder, modifiers, StatType.SHIELD_REGEN_ON_DOT, baseStats.getShieldRegenOnDot());
        depositStandalone(builder, modifiers, StatType.IMMUNITY_ON_AILMENT, baseStats.getImmunityOnAilment());

        // ═══════════════════════════════════════════════════════════════════
        // PHASE 4: Special fan-out stats (custom logic, unchanged)
        // ═══════════════════════════════════════════════════════════════════

        // Evasion Percent: fold into existing evasion as a percent modifier
        if (modifiers.hasModifiers(StatType.EVASION_PERCENT)) {
            float evasionBase = builder.build().getEvasion();
            float evasionPctBonus = modifiers.getFlatSum(StatType.EVASION_PERCENT)
                                  + modifiers.getPercentSum(StatType.EVASION_PERCENT);
            builder.evasion(evasionBase * (1f + evasionPctBonus / 100f));
        }

        // Elemental Resistance: distribute computed value to all 6 individual element resistances
        if (modifiers.hasModifiers(StatType.ELEMENTAL_RESISTANCE)) {
            float flat = modifiers.getFlatSum(StatType.ELEMENTAL_RESISTANCE);
            float percent = modifiers.getPercentSum(StatType.ELEMENTAL_RESISTANCE);
            float elemResValue = flat + percent;
            for (float mult : modifiers.getMultipliers(StatType.ELEMENTAL_RESISTANCE)) {
                elemResValue += mult;
            }
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
            float allElemValue = flat + percent;
            for (float mult : modifiers.getMultipliers(StatType.ALL_ELEMENTAL_DAMAGE_PERCENT)) {
                allElemValue += mult;
            }
            ComputedStats intermediate = builder.build();
            builder.fireDamagePercent(intermediate.getFireDamagePercent() + allElemValue);
            builder.waterDamagePercent(intermediate.getWaterDamagePercent() + allElemValue);
            builder.lightningDamagePercent(intermediate.getLightningDamagePercent() + allElemValue);
            builder.earthDamagePercent(intermediate.getEarthDamagePercent() + allElemValue);
            builder.windDamagePercent(intermediate.getWindDamagePercent() + allElemValue);
            builder.voidDamagePercent(intermediate.getVoidDamagePercent() + allElemValue);
        }

        // ═══════════════════════════════════════════════════════════════════
        // PHASE 5: Derived keystones (Tier 1: computed from already-combined stats)
        // ═══════════════════════════════════════════════════════════════════

        // HP_SCALING_DAMAGE: bonus physDmg% = (maxHP / 100) × statValue
        if (modifiers.hasModifiers(StatType.HP_SCALING_DAMAGE)) {
            float hpScaling = modifiers.getFlatSum(StatType.HP_SCALING_DAMAGE)
                            + modifiers.getPercentSum(StatType.HP_SCALING_DAMAGE);
            ComputedStats intermediate = builder.build();
            float bonusPct = (intermediate.getMaxHealth() / 100f) * hpScaling;
            builder.physicalDamagePercent(intermediate.getPhysicalDamagePercent() + bonusPct);
        }

        // SPEED_TO_SPELL_POWER: bonus spellDmg% = moveSpeedPct × (statValue / 100)
        if (modifiers.hasModifiers(StatType.SPEED_TO_SPELL_POWER)) {
            float speedConvert = modifiers.getFlatSum(StatType.SPEED_TO_SPELL_POWER)
                               + modifiers.getPercentSum(StatType.SPEED_TO_SPELL_POWER);
            ComputedStats intermediate = builder.build();
            float bonusPct = intermediate.getMovementSpeedPercent() * (speedConvert / 100f);
            builder.spellDamagePercent(intermediate.getSpellDamagePercent() + bonusPct);
        }

        // ATK_SPEED_TO_SPELL_POWER: bonus spellDmg% = atkSpeedPct × (statValue / 100)
        if (modifiers.hasModifiers(StatType.ATK_SPEED_TO_SPELL_POWER)) {
            float atkSpeedConvert = modifiers.getFlatSum(StatType.ATK_SPEED_TO_SPELL_POWER)
                                  + modifiers.getPercentSum(StatType.ATK_SPEED_TO_SPELL_POWER);
            ComputedStats intermediate = builder.build();
            float bonusPct = intermediate.getAttackSpeedPercent() * (atkSpeedConvert / 100f);
            builder.spellDamagePercent(intermediate.getSpellDamagePercent() + bonusPct);
        }

        // EVASION_TO_ARMOR: bonus armor = evasion × (statValue / 100)
        if (modifiers.hasModifiers(StatType.EVASION_TO_ARMOR)) {
            float convertPct = modifiers.getFlatSum(StatType.EVASION_TO_ARMOR)
                             + modifiers.getPercentSum(StatType.EVASION_TO_ARMOR);
            ComputedStats intermediate = builder.build();
            float bonusArmor = intermediate.getEvasion() * (convertPct / 100f);
            builder.armor(intermediate.getArmor() + bonusArmor);
        }

        return builder.build();
    }

    /**
     * Deposits modifiers for a BASE stat that has a percent accumulator pair.
     *
     * <ul>
     *   <li>FLAT → adds to the base field</li>
     *   <li>PERCENT → routes to the paired percent accumulator (via pendingPercent)</li>
     *   <li>MULTIPLIER → routes to the paired percent accumulator as pending
     *       (future: dedicated multiplier storage for resource stats)</li>
     * </ul>
     */
    private void depositBase(ComputedStats.Builder builder, AggregatedModifiers mods,
                             StatType stat, float baseValue,
                             Map<StatType, Float> pendingPercent) {
        if (!mods.hasModifiers(stat)) return;

        float flat = mods.getFlatSum(stat);
        float percent = mods.getPercentSum(stat);
        List<Float> multipliers = mods.getMultipliers(stat);

        // FLAT adds to the base value
        setStatInBuilder(builder, stat, baseValue + flat);

        // PERCENT routes to the paired accumulator
        if (percent != 0f) {
            StatType pair = PERCENT_ROUTING.get(stat);
            if (pair != null) {
                pendingPercent.merge(pair, percent, Float::sum);
            }
        }

        // MULTIPLIER chains into the multiplier product for this stat.
        // Applied during consolidateResourcePercents() as the final tier:
        // base × (1 + percent/100) × Π(1 + more/100)
        for (float mult : multipliers) {
            builder.chainMultiplier(stat.getFieldName(), mult);
        }
    }

    /**
     * Deposits modifiers for a STANDALONE base stat (no percent pair).
     *
     * <p>All modifier types are additive — they add directly to the stat value.
     * This is correct for stats like CRITICAL_CHANCE (where "+5% crit" means
     * +5 percentage points) and resistances (where "+10% fire res" means +10).
     */
    private void depositStandalone(ComputedStats.Builder builder, AggregatedModifiers mods,
                                    StatType stat, float baseValue) {
        if (!mods.hasModifiers(stat)) return;

        float flat = mods.getFlatSum(stat);
        float percent = mods.getPercentSum(stat);

        float result = baseValue + flat + percent;
        for (float mult : mods.getMultipliers(stat)) {
            result += mult;
        }

        setStatInBuilder(builder, stat, result);
    }

    /**
     * Deposits modifiers for an ACCUMULATOR stat (_PERCENT, _MULTIPLIER fields).
     *
     * <p>All modifier types are additive. Accumulator stats hold running totals
     * of percentage/multiplier bonuses that are consumed by a later formula step.
     * Additionally includes any pending values routed from base stat PERCENT modifiers.
     */
    private void depositAccumulator(ComputedStats.Builder builder, AggregatedModifiers mods,
                                     StatType stat, float baseValue,
                                     Map<StatType, Float> pendingPercent) {
        float flat = mods.hasModifiers(stat) ? mods.getFlatSum(stat) : 0f;
        float percent = mods.hasModifiers(stat) ? mods.getPercentSum(stat) : 0f;
        float pending = pendingPercent.getOrDefault(stat, 0f);

        // All types additive for accumulators
        float result = baseValue + flat + percent + pending;

        if (mods.hasModifiers(stat)) {
            for (float mult : mods.getMultipliers(stat)) {
                result += mult;
            }
        }

        // Only set if we actually have changes (avoid unnecessary builder dirty marking)
        if (flat != 0f || percent != 0f || pending != 0f ||
                (mods.hasModifiers(stat) && !mods.getMultipliers(stat).isEmpty())) {
            setStatInBuilder(builder, stat, result);
        }
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
