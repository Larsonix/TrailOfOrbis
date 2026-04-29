package io.github.larsonix.trailoforbis.gear.stats;

import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig.StatType;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Maps modifier stat IDs to ComputedStats field setters.
 *
 * <p>This class handles the translation between config-defined stat names
 * and the actual ComputedStats fields. It supports both flat and percent
 * modifiers for most stats.
 */
public final class StatMapping {

    // Flat stat appliers: (stats, value) -> void
    private static final Map<String, BiConsumer<ComputedStats, Double>> FLAT_APPLIERS;

    // Percent stat appliers: (stats, value) -> void
    private static final Map<String, BiConsumer<ComputedStats, Double>> PERCENT_APPLIERS;

    static {
        FLAT_APPLIERS = new HashMap<>();
        PERCENT_APPLIERS = new HashMap<>();

        // =================================================================
        // CORE STATS (Flat and Percent)
        // =================================================================

        registerBoth("max_health",
            (s, v) -> s.setMaxHealth(s.getMaxHealth() + v),
            (s, v) -> s.setMaxHealth(s.getMaxHealth() * (1 + v / 100)));

        registerBoth("max_mana",
            (s, v) -> s.setMaxMana(s.getMaxMana() + v),
            (s, v) -> s.setMaxMana(s.getMaxMana() * (1 + v / 100)));

        registerBoth("max_stamina",
            (s, v) -> s.setMaxStamina(s.getMaxStamina() + v),
            (s, v) -> s.setMaxStamina(s.getMaxStamina() * (1 + v / 100)));

        registerBoth("max_oxygen",
            (s, v) -> s.setMaxOxygen(s.getMaxOxygen() + v),
            (s, v) -> s.setMaxOxygen(s.getMaxOxygen() * (1 + v / 100)));

        registerBoth("energy_shield",
            (s, v) -> s.setEnergyShield(s.getEnergyShield() + v),
            (s, v) -> s.setEnergyShield(s.getEnergyShield() * (1 + v / 100)));

        // =================================================================
        // DAMAGE STATS (Flat and Percent)
        // =================================================================

        registerBoth("physical_damage",
            (s, v) -> s.setPhysicalDamage(s.getPhysicalDamage() + v),
            (s, v) -> s.setPhysicalDamagePercent(s.getPhysicalDamagePercent() + v));

        registerBoth("spell_damage",
            (s, v) -> s.setSpellDamage(s.getSpellDamage() + v),
            (s, v) -> s.setSpellDamagePercent(s.getSpellDamagePercent() + v));

        registerBoth("fire_damage",
            (s, v) -> s.setFireDamage(s.getFireDamage() + v),
            (s, v) -> s.setFireDamagePercent(s.getFireDamagePercent() + v));

        registerBoth("water_damage",
            (s, v) -> s.setWaterDamage(s.getWaterDamage() + v),
            (s, v) -> s.setWaterDamagePercent(s.getWaterDamagePercent() + v));

        registerBoth("lightning_damage",
            (s, v) -> s.setLightningDamage(s.getLightningDamage() + v),
            (s, v) -> s.setLightningDamagePercent(s.getLightningDamagePercent() + v));

        registerBoth("void_damage",
            (s, v) -> s.setVoidDamage(s.getVoidDamage() + v),
            (s, v) -> s.setVoidDamagePercent(s.getVoidDamagePercent() + v));

        registerBoth("earth_damage",
            (s, v) -> s.setEarthDamage(s.getEarthDamage() + v),
            (s, v) -> s.setEarthDamagePercent(s.getEarthDamagePercent() + v));

        registerBoth("wind_damage",
            (s, v) -> s.setWindDamage(s.getWindDamage() + v),
            (s, v) -> s.setWindDamagePercent(s.getWindDamagePercent() + v));

        registerFlat("true_damage",
            (s, v) -> s.setTrueDamage(s.getTrueDamage() + v));

        // =================================================================
        // ATTACK TYPE MODIFIERS (Percent only)
        // =================================================================

        registerPercent("melee_damage_percent",
            (s, v) -> s.setMeleeDamagePercent(s.getMeleeDamagePercent() + v));

        registerPercent("projectile_damage_percent",
            (s, v) -> s.setProjectileDamagePercent(s.getProjectileDamagePercent() + v));

        // =================================================================
        // CRITICAL STATS (Percent)
        // =================================================================

        registerPercent("crit_chance",
            (s, v) -> s.setCritChance(s.getCriticalChance() + v));

        registerPercent("crit_multiplier",
            (s, v) -> s.setCritMultiplier(s.getCriticalMultiplier() + v));

        // =================================================================
        // SPEED STATS
        // =================================================================

        registerPercent("attack_speed_percent",
            (s, v) -> s.setAttackSpeedPercent(s.getAttackSpeedPercent() + v));

        registerPercent("movement_speed_percent",
            (s, v) -> s.setMovementSpeedPercent(s.getMovementSpeedPercent() + v));

        registerPercent("walk_speed_percent",
            (s, v) -> s.setWalkSpeedPercent(s.getWalkSpeedPercent() + v));

        registerPercent("sprint_speed_bonus",
            (s, v) -> s.setSprintSpeedBonus(s.getSprintSpeedBonus() + v));

        registerFlat("jump_force_bonus",
            (s, v) -> s.setJumpForceBonus(s.getJumpForceBonus() + v));

        registerFlat("climb_speed_bonus",
            (s, v) -> s.setClimbSpeedBonus(s.getClimbSpeedBonus() + v));

        // =================================================================
        // DEFENSE STATS
        // =================================================================

        registerBoth("armor",
            (s, v) -> s.setArmor(s.getArmor() + v),
            (s, v) -> s.setArmorPercent(s.getArmorPercent() + v));

        registerFlat("evasion",
            (s, v) -> s.setEvasion(s.getEvasion() + v));

        // Legacy: passive_block_chance now feeds blockChance (perfect block when actively blocking)
        registerPercent("passive_block_chance",
            (s, v) -> s.setBlockChance(s.getBlockChance() + v.floatValue()));

        // Shield implicit (flat) + gear mods (percent) both feed perfect block chance.
        // "Percent" here means display format (+X%), not multiplicative — both are additive.
        registerFlat("block_chance",
            (s, v) -> s.setBlockChance(s.getBlockChance() + v.floatValue()));
        registerPercent("block_chance",
            (s, v) -> s.setBlockChance(s.getBlockChance() + v.floatValue()));

        registerPercent("parry_chance",
            (s, v) -> s.setParryChance(s.getParryChance() + v));

        // =================================================================
        // RESISTANCE STATS (Percent)
        // =================================================================

        registerPercent("fire_resistance",
            (s, v) -> s.setFireResistance(s.getFireResistance() + v));

        registerPercent("water_resistance",
            (s, v) -> s.setWaterResistance(s.getWaterResistance() + v));

        registerPercent("lightning_resistance",
            (s, v) -> s.setLightningResistance(s.getLightningResistance() + v));

        registerPercent("void_resistance",
            (s, v) -> s.setVoidResistance(s.getVoidResistance() + v));

        registerPercent("earth_resistance",
            (s, v) -> s.setEarthResistance(s.getEarthResistance() + v));

        registerPercent("wind_resistance",
            (s, v) -> s.setWindResistance(s.getWindResistance() + v));

        // =================================================================
        // REGENERATION STATS (Flat)
        // =================================================================

        registerFlat("health_regen",
            (s, v) -> s.setHealthRegen(s.getHealthRegen() + v));

        registerFlat("mana_regen",
            (s, v) -> s.setManaRegen(s.getManaRegen() + v));

        registerFlat("stamina_regen",
            (s, v) -> s.setStaminaRegen(s.getStaminaRegen() + v));

        registerPercent("stamina_regen_percent",
            (s, v) -> s.setStaminaRegenPercent(s.getStaminaRegenPercent() + v));

        registerPercent("stamina_regen_start_delay",
            (s, v) -> s.setStaminaRegenStartDelay(s.getStaminaRegenStartDelay() + v));

        registerFlat("oxygen_regen",
            (s, v) -> s.setOxygenRegen(s.getOxygenRegen() + v));

        // =================================================================
        // UTILITY STATS
        // =================================================================

        registerFlat("accuracy",
            (s, v) -> s.setAccuracy(s.getAccuracy() + v));

        registerPercent("accuracy_percent",
            (s, v) -> s.setAccuracyPercent(s.getAccuracyPercent() + v));

        registerPercent("life_steal",
            (s, v) -> s.setLifeSteal(s.getLifeSteal() + v));

        registerFlat("armor_penetration",
            (s, v) -> s.setArmorPenetration(s.getArmorPenetration() + v));

        registerPercent("knockback_resistance",
            (s, v) -> s.setKnockbackResistance(s.getKnockbackResistance() + v));

        registerPercent("fall_damage_reduction",
            (s, v) -> s.setFallDamageReduction(s.getFallDamageReduction() + v));

        // =================================================================
        // PENETRATION STATS (Percent)
        // =================================================================

        registerPercent("fire_penetration",
            (s, v) -> s.setFirePenetration(s.getFirePenetration() + v));

        registerPercent("water_penetration",
            (s, v) -> s.setWaterPenetration(s.getWaterPenetration() + v));

        registerPercent("lightning_penetration",
            (s, v) -> s.setLightningPenetration(s.getLightningPenetration() + v));

        registerPercent("void_penetration",
            (s, v) -> s.setVoidPenetration(s.getVoidPenetration() + v));

        registerPercent("earth_penetration",
            (s, v) -> s.setEarthPenetration(s.getEarthPenetration() + v));

        registerPercent("wind_penetration",
            (s, v) -> s.setWindPenetration(s.getWindPenetration() + v));

        // =================================================================
        // ELEMENTAL MULTIPLIERS
        // =================================================================

        registerPercent("fire_multiplier",
            (s, v) -> s.setFireMultiplier(s.getFireDamageMultiplier() + v));

        registerPercent("water_multiplier",
            (s, v) -> s.setWaterMultiplier(s.getWaterDamageMultiplier() + v));

        registerPercent("lightning_multiplier",
            (s, v) -> s.setLightningMultiplier(s.getLightningDamageMultiplier() + v));

        registerPercent("void_multiplier",
            (s, v) -> s.setVoidMultiplier(s.getVoidDamageMultiplier() + v));

        registerPercent("earth_multiplier",
            (s, v) -> s.setEarthMultiplier(s.getEarthDamageMultiplier() + v));

        registerPercent("wind_multiplier",
            (s, v) -> s.setWindMultiplier(s.getWindDamageMultiplier() + v));

        // =================================================================
        // GLOBAL DAMAGE MODIFIERS
        // =================================================================

        registerPercent("all_damage_percent",
            (s, v) -> s.setAllDamagePercent(s.getAllDamagePercent() + v));

        registerFlat("melee_damage",
            (s, v) -> s.setMeleeDamage(s.getMeleeDamage() + v));

        // =================================================================
        // LEECH (gain resources from YOUR damage output - always works)
        // =================================================================

        registerPercent("life_leech",
            (s, v) -> s.setLifeLeech(s.getLifeLeech() + v));

        registerPercent("mana_leech",
            (s, v) -> s.setManaLeech(s.getManaLeech() + v));

        // =================================================================
        // STEAL (take resources FROM enemy - enemy loses what you gain)
        // =================================================================

        // life_steal is registered under UTILITY STATS section

        registerPercent("mana_steal",
            (s, v) -> s.setManaSteal(s.getManaSteal() + v));

        // =================================================================
        // SUSTAIN
        // =================================================================

        registerPercent("damage_at_low_life",
            (s, v) -> s.setDamageAtLowLife(s.getDamageAtLowLife() + v));

        registerPercent("mana_cost_reduction",
            (s, v) -> s.setManaCostReduction(s.getManaCostReduction() + v));

        registerPercent("health_regen_percent",
            (s, v) -> s.setHealthRegenPercent(s.getHealthRegenPercent() + v));

        registerPercent("mana_as_damage_buffer",
            (s, v) -> s.setManaAsDamageBuffer(s.getManaAsDamageBuffer() + v));

        // =================================================================
        // STATUS EFFECT STATS
        // =================================================================

        registerPercent("status_effect_chance",
            (s, v) -> s.setStatusEffectChance(s.getStatusEffectChance() + v));

        registerPercent("status_effect_duration",
            (s, v) -> s.setStatusEffectDuration(s.getStatusEffectDuration() + v));

        // =================================================================
        // DAMAGE CONVERSION
        // =================================================================

        registerPercent("fire_conversion",
            (s, v) -> s.setFireConversion(s.getFireConversion() + v));

        registerPercent("water_conversion",
            (s, v) -> s.setWaterConversion(s.getWaterConversion() + v));

        registerPercent("lightning_conversion",
            (s, v) -> s.setLightningConversion(s.getLightningConversion() + v));

        registerPercent("void_conversion",
            (s, v) -> s.setVoidConversion(s.getVoidConversion() + v));

        registerPercent("earth_conversion",
            (s, v) -> s.setEarthConversion(s.getEarthConversion() + v));

        registerPercent("wind_conversion",
            (s, v) -> s.setWindConversion(s.getWindConversion() + v));

        // =================================================================
        // AILMENT DAMAGE
        // =================================================================

        registerFlat("burn_damage",
            (s, v) -> s.setBurnDamage(s.getBurnDamage() + v));

        registerFlat("freeze_damage",
            (s, v) -> s.setFreezeDamage(s.getFreezeDamage() + v));

        registerFlat("shock_damage",
            (s, v) -> s.setShockDamage(s.getShockDamage() + v));

        registerFlat("poison_damage",
            (s, v) -> s.setPoisonDamage(s.getPoisonDamage() + v));

        // =================================================================
        // AILMENT DAMAGE PERCENT MODIFIERS
        // =================================================================

        registerPercent("burn_damage_percent",
            (s, v) -> s.setBurnDamagePercent(s.getBurnDamagePercent() + v));

        registerPercent("frost_damage_percent",
            (s, v) -> s.setFrostDamagePercent(s.getFrostDamagePercent() + v));

        registerPercent("shock_damage_percent",
            (s, v) -> s.setShockDamagePercent(s.getShockDamagePercent() + v));

        // =================================================================
        // DEFENSIVE — NEW
        // =================================================================

        registerPercent("physical_resistance",
            (s, v) -> s.setPhysicalResistance(s.getPhysicalResistance() + v));

        registerPercent("dodge_chance",
            (s, v) -> s.setDodgeChance(s.getDodgeChance() + v));

        registerPercent("crit_nullify_chance",
            (s, v) -> s.setCritNullifyChance(s.getCritNullifyChance() + v));

        registerPercent("critical_reduction",
            (s, v) -> s.setCriticalReduction(s.getCriticalReduction() + v));

        // =================================================================
        // ACTIVE BLOCK MODIFIERS
        // =================================================================

        registerPercent("block_damage_reduction",
            (s, v) -> s.setBlockDamageReduction(s.getBlockDamageReduction() + v));

        registerPercent("stamina_drain_reduction",
            (s, v) -> s.setStaminaDrainReduction(s.getStaminaDrainReduction() + v));

        // =================================================================
        // AILMENT THRESHOLDS
        // =================================================================

        registerFlat("burn_threshold",
            (s, v) -> s.setBurnThreshold(s.getBurnThreshold() + v));

        registerFlat("freeze_threshold",
            (s, v) -> s.setFreezeThreshold(s.getFreezeThreshold() + v));

        registerFlat("shock_threshold",
            (s, v) -> s.setShockThreshold(s.getShockThreshold() + v));

        // =================================================================
        // BLOCK/SHIELD MODIFIERS
        // =================================================================

        registerPercent("block_heal_percent",
            (s, v) -> s.setBlockHealPercent(s.getBlockHealPercent() + v));

        registerPercent("shield_effectiveness_percent",
            (s, v) -> s.setShieldEffectivenessPercent(s.getShieldEffectivenessPercent() + v));

        // =================================================================
        // DAMAGE REDUCTION
        // =================================================================

        registerPercent("damage_reduction_percent",
            (s, v) -> s.setDamageTakenPercent(s.getDamageTakenPercent() - v));

        registerPercent("health_recovery_percent",
            (s, v) -> s.setHealthRecoveryPercent(s.getHealthRecoveryPercent() + v));

        // =================================================================
        // AILMENT CHANCES
        // =================================================================

        registerPercent("ignite_chance",
            (s, v) -> s.setIgniteChance(s.getIgniteChance() + v));

        registerPercent("freeze_chance",
            (s, v) -> s.setFreezeChance(s.getFreezeChance() + v));

        registerPercent("shock_chance",
            (s, v) -> s.setShockChance(s.getShockChance() + v));

        // =================================================================
        // DOT / AILMENT DAMAGE
        // =================================================================

        registerPercent("dot_damage_percent",
            (s, v) -> s.setDotDamagePercent(s.getDotDamagePercent() + v));

        // =================================================================
        // CONDITIONAL DAMAGE
        // =================================================================

        registerPercent("execute_damage_percent",
            (s, v) -> s.setExecuteDamagePercent(s.getExecuteDamagePercent() + v));

        registerPercent("damage_vs_frozen_percent",
            (s, v) -> s.setDamageVsFrozenPercent(s.getDamageVsFrozenPercent() + v));

        registerPercent("damage_vs_shocked_percent",
            (s, v) -> s.setDamageVsShockedPercent(s.getDamageVsShockedPercent() + v));

        // =================================================================
        // PROJECTILE MODIFIERS
        // =================================================================

        registerPercent("projectile_speed_percent",
            (s, v) -> s.setProjectileSpeedPercent(s.getProjectileSpeedPercent() + v));

        registerPercent("projectile_gravity_percent",
            (s, v) -> s.setProjectileGravityPercent(s.getProjectileGravityPercent() + v));

        // =================================================================
        // SPELL/ELEMENTAL
        // =================================================================

        registerFlat("spell_penetration",
            (s, v) -> s.setSpellPenetration(s.getSpellPenetration() + v));

        registerPercent("all_elemental_damage_percent",
            (s, v) -> s.setAllElementalDamagePercent(s.getAllElementalDamagePercent() + v));

        // =================================================================
        // SIGNATURE ENERGY
        // =================================================================

        registerPercent("signature_energy_max_percent",
            (s, v) -> s.setSignatureEnergyMaxPercent(s.getSignatureEnergyMaxPercent() + v));

        registerFlat("signature_energy_per_hit",
            (s, v) -> s.setSignatureEnergyPerHit(s.getSignatureEnergyPerHit() + v));

        // =================================================================
        // MOVEMENT
        // =================================================================

        registerPercent("crouch_speed_percent",
            (s, v) -> s.setCrouchSpeedPercent(s.getCrouchSpeedPercent() + v));

        registerPercent("run_speed_percent",
            (s, v) -> s.setRunSpeedPercent(s.getRunSpeedPercent() + v));

        // =================================================================
        // THORNS / REFLECT (Defensive counter-attack damage)
        // =================================================================

        registerFlat("thorns_damage",
            (s, v) -> s.setThornsDamage(s.getThornsDamage() + v));

        registerPercent("thorns_damage_percent",
            (s, v) -> s.setThornsDamagePercent(s.getThornsDamagePercent() + v));

        registerPercent("reflect_damage_percent",
            (s, v) -> s.setReflectDamagePercent(s.getReflectDamagePercent() + v));

        // =================================================================
        // MAGIC STATS (Hexcode Integration)
        // =================================================================

        registerFlat("volatility_max",
            (s, v) -> s.setVolatilityMax(s.getVolatilityMax() + v));

        registerPercent("magic_power",
            (s, v) -> s.setMagicPower(s.getMagicPower() + v));

        registerFlat("magic_charges",
            (s, v) -> s.setMagicCharges(s.getMagicCharges() + (int) Math.round(v)));

        registerPercent("draw_accuracy",
            (s, v) -> s.setDrawAccuracy(s.getDrawAccuracy() + v));

        registerPercent("cast_speed",
            (s, v) -> s.setCastSpeed(s.getCastSpeed() + v));
    }

    private static void registerFlat(String statId, BiConsumer<ComputedStats, Double> applier) {
        FLAT_APPLIERS.put(statId.toLowerCase(), applier);
    }

    private static void registerPercent(String statId, BiConsumer<ComputedStats, Double> applier) {
        PERCENT_APPLIERS.put(statId.toLowerCase(), applier);
    }

    private static void registerBoth(String statId,
            BiConsumer<ComputedStats, Double> flatApplier,
            BiConsumer<ComputedStats, Double> percentApplier) {
        FLAT_APPLIERS.put(statId.toLowerCase(), flatApplier);
        PERCENT_APPLIERS.put(statId.toLowerCase() + "_percent", percentApplier);
        // Also register the base name for percent type
        PERCENT_APPLIERS.put(statId.toLowerCase(), percentApplier);
    }

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Applies a stat bonus to ComputedStats.
     *
     * @param stats The ComputedStats to modify
     * @param statId The stat ID (e.g., "physical_damage", "max_health")
     * @param value The bonus value
     * @param type FLAT or PERCENT
     */
    public static void apply(ComputedStats stats, String statId, double value, StatType type) {
        Objects.requireNonNull(stats, "stats cannot be null");
        Objects.requireNonNull(statId, "statId cannot be null");
        Objects.requireNonNull(type, "type cannot be null");

        String normalizedId = statId.toLowerCase();

        BiConsumer<ComputedStats, Double> applier = type == StatType.FLAT
                ? FLAT_APPLIERS.get(normalizedId)
                : PERCENT_APPLIERS.get(normalizedId);

        if (applier != null) {
            applier.accept(stats, value);
        }
        // Unknown stats are silently ignored (config-driven extensibility)
    }

    /**
     * Checks if a stat ID is supported.
     *
     * @param statId The stat ID
     * @param type FLAT or PERCENT
     * @return true if the stat can be applied
     */
    public static boolean isSupported(String statId, StatType type) {
        String normalizedId = statId.toLowerCase();
        return type == StatType.FLAT
                ? FLAT_APPLIERS.containsKey(normalizedId)
                : PERCENT_APPLIERS.containsKey(normalizedId);
    }

    /**
     * Gets all supported flat stat IDs.
     */
    public static java.util.Set<String> getSupportedFlatStats() {
        return java.util.Collections.unmodifiableSet(FLAT_APPLIERS.keySet());
    }

    /**
     * Gets all supported percent stat IDs.
     */
    public static java.util.Set<String> getSupportedPercentStats() {
        return java.util.Collections.unmodifiableSet(PERCENT_APPLIERS.keySet());
    }

    private StatMapping() {}
}
