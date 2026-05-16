package io.github.larsonix.trailoforbis.attributes.debug;

import io.github.larsonix.trailoforbis.attributes.ComputedStats;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Registry mapping human-readable stat names to ComputedStats getter/setter pairs.
 *
 * <p>Used by the debug stat override system to read and write individual stats
 * by name. Stat names use snake_case and match the vocabulary in
 * {@link io.github.larsonix.trailoforbis.gear.stats.StatMapping} where applicable.
 *
 * <p>Each entry has:
 * <ul>
 *   <li>{@code getter} — reads the current value from ComputedStats</li>
 *   <li>{@code setter} — writes an absolute value to ComputedStats</li>
 *   <li>{@code category} — grouping for display (resource, offensive, defensive, movement, elemental)</li>
 * </ul>
 */
public final class DebugStatRegistry {

    /**
     * A registered stat with its name, category, getter, and setter.
     */
    public record StatEntry(
        String name,
        String category,
        Function<ComputedStats, Float> getter,
        BiConsumer<ComputedStats, Float> setter
    ) {}

    private static final Map<String, StatEntry> ENTRIES = new LinkedHashMap<>();
    private static final Map<String, List<StatEntry>> BY_CATEGORY = new LinkedHashMap<>();

    static {
        // =====================================================================
        // RESOURCE STATS
        // =====================================================================
        register("max_health", "resource", ComputedStats::getMaxHealth, (s, v) -> s.setMaxHealth(v));
        register("max_mana", "resource", ComputedStats::getMaxMana, (s, v) -> s.setMaxMana(v));
        register("max_stamina", "resource", ComputedStats::getMaxStamina, (s, v) -> s.setMaxStamina(v));
        register("max_oxygen", "resource", ComputedStats::getMaxOxygen, (s, v) -> s.setMaxOxygen(v));
        register("max_signature_energy", "resource", ComputedStats::getMaxSignatureEnergy, (s, v) -> s.setMaxSignatureEnergy(v));
        register("health_regen", "resource", ComputedStats::getHealthRegen, (s, v) -> s.setHealthRegen(v));
        register("mana_regen", "resource", ComputedStats::getManaRegen, (s, v) -> s.setManaRegen(v));
        register("stamina_regen", "resource", ComputedStats::getStaminaRegen, (s, v) -> s.setStaminaRegen(v));
        register("oxygen_regen", "resource", ComputedStats::getOxygenRegen, (s, v) -> s.setOxygenRegen(v));
        register("signature_energy_regen", "resource", ComputedStats::getSignatureEnergyRegen, (s, v) -> s.setSignatureEnergyRegen(v));
        register("health_regen_percent", "resource", ComputedStats::getHealthRegenPercent, (s, v) -> s.setHealthRegenPercent(v));
        register("energy_shield", "resource", ComputedStats::getEnergyShield, (s, v) -> s.setEnergyShield(v));
        register("mana_on_kill", "resource", ComputedStats::getManaOnKill, (s, v) -> s.setManaOnKill(v));
        register("signature_energy_per_hit", "resource", ComputedStats::getSignatureEnergyPerHit, (s, v) -> s.setSignatureEnergyPerHit(v));

        // =====================================================================
        // OFFENSIVE — DAMAGE
        // =====================================================================
        register("physical_damage", "offensive", ComputedStats::getPhysicalDamage, (s, v) -> s.setPhysicalDamage(v));
        register("physical_damage_percent", "offensive", ComputedStats::getPhysicalDamagePercent, (s, v) -> s.setPhysicalDamagePercent(v));
        register("spell_damage", "offensive", ComputedStats::getSpellDamage, (s, v) -> s.setSpellDamage(v));
        register("spell_damage_percent", "offensive", ComputedStats::getSpellDamagePercent, (s, v) -> s.setSpellDamagePercent(v));
        register("melee_damage", "offensive", ComputedStats::getMeleeDamage, (s, v) -> s.setMeleeDamage(v));
        register("melee_damage_percent", "offensive", ComputedStats::getMeleeDamagePercent, (s, v) -> s.setMeleeDamagePercent(v));
        register("projectile_damage_percent", "offensive", ComputedStats::getProjectileDamagePercent, (s, v) -> s.setProjectileDamagePercent(v));
        register("charged_attack_damage", "offensive", ComputedStats::getChargedAttackDamage, (s, v) -> s.setChargedAttackDamage(v));
        register("charged_attack_damage_percent", "offensive", ComputedStats::getChargedAttackDamagePercent, (s, v) -> s.setChargedAttackDamagePercent(v));
        register("true_damage", "offensive", ComputedStats::getTrueDamage, (s, v) -> s.setTrueDamage(v));
        register("true_damage_percent", "offensive", ComputedStats::getTrueDamagePercent, (s, v) -> s.setTrueDamagePercent(v));
        register("percent_hit_as_true_damage", "offensive", ComputedStats::getPercentHitAsTrueDamage, (s, v) -> s.setPercentHitAsTrueDamage(v));
        register("all_damage_percent", "offensive", ComputedStats::getAllDamagePercent, (s, v) -> s.setAllDamagePercent(v));

        // =====================================================================
        // OFFENSIVE — CRITICAL
        // =====================================================================
        register("crit_chance", "offensive", ComputedStats::getCriticalChance, (s, v) -> s.setCritChance(v));
        register("crit_multiplier", "offensive", ComputedStats::getCriticalMultiplier, (s, v) -> s.setCritMultiplier(v));

        // =====================================================================
        // OFFENSIVE — SPEED & UTILITY
        // =====================================================================
        register("attack_speed_percent", "offensive", ComputedStats::getAttackSpeedPercent, (s, v) -> s.setAttackSpeedPercent(v));
        register("cooldown_recovery_percent", "offensive", ComputedStats::getCooldownRecoveryPercent, (s, v) -> s.setCooldownRecoveryPercent(v));
        register("charge_speed_percent", "offensive", ComputedStats::getChargeSpeedPercent, (s, v) -> s.setChargeSpeedPercent(v));
        register("combo_speed_bonus", "offensive", ComputedStats::getComboSpeedBonus, (s, v) -> s.setComboSpeedBonus(v));
        register("projectile_attack_speed_percent", "offensive", ComputedStats::getProjectileAttackSpeedPercent, (s, v) -> s.setProjectileAttackSpeedPercent(v));
        register("cast_speed_percent", "offensive", ComputedStats::getCastSpeedPercent, (s, v) -> s.setCastSpeedPercent(v));
        register("accuracy", "offensive", ComputedStats::getAccuracy, (s, v) -> s.setAccuracy(v));
        register("accuracy_percent", "offensive", ComputedStats::getAccuracyPercent, (s, v) -> s.setAccuracyPercent(v));
        register("armor_penetration", "offensive", ComputedStats::getArmorPenetration, (s, v) -> s.setArmorPenetration(v));
        register("life_steal", "offensive", ComputedStats::getLifeSteal, (s, v) -> s.setLifeSteal(v));
        register("life_leech", "offensive", ComputedStats::getLifeLeech, (s, v) -> s.setLifeLeech(v));
        register("mana_leech", "offensive", ComputedStats::getManaLeech, (s, v) -> s.setManaLeech(v));
        register("mana_steal", "offensive", ComputedStats::getManaSteal, (s, v) -> s.setManaSteal(v));
        register("mana_cost_reduction", "offensive", ComputedStats::getManaCostReduction, (s, v) -> s.setManaCostReduction(v));
        register("damage_at_low_life", "offensive", ComputedStats::getDamageAtLowLife, (s, v) -> s.setDamageAtLowLife(v));

        // =====================================================================
        // OFFENSIVE — STATUS EFFECTS & AILMENTS
        // =====================================================================
        register("status_effect_chance", "offensive", ComputedStats::getStatusEffectChance, (s, v) -> s.setStatusEffectChance(v));
        register("status_effect_duration", "offensive", ComputedStats::getStatusEffectDuration, (s, v) -> s.setStatusEffectDuration(v));
        register("ignite_chance", "offensive", ComputedStats::getIgniteChance, (s, v) -> s.setIgniteChance(v));
        register("freeze_chance", "offensive", ComputedStats::getFreezeChance, (s, v) -> s.setFreezeChance(v));
        register("shock_chance", "offensive", ComputedStats::getShockChance, (s, v) -> s.setShockChance(v));
        register("burn_damage", "offensive", ComputedStats::getBurnDamage, (s, v) -> s.setBurnDamage(v));
        register("freeze_damage", "offensive", ComputedStats::getFreezeDamage, (s, v) -> s.setFreezeDamage(v));
        register("shock_damage", "offensive", ComputedStats::getShockDamage, (s, v) -> s.setShockDamage(v));
        register("poison_damage", "offensive", ComputedStats::getPoisonDamage, (s, v) -> s.setPoisonDamage(v));
        register("dot_damage_percent", "offensive", ComputedStats::getDotDamagePercent, (s, v) -> s.setDotDamagePercent(v));
        register("burn_damage_percent", "offensive", ComputedStats::getBurnDamagePercent, (s, v) -> s.setBurnDamagePercent(v));
        register("frost_damage_percent", "offensive", ComputedStats::getFrostDamagePercent, (s, v) -> s.setFrostDamagePercent(v));
        register("shock_damage_percent", "offensive", ComputedStats::getShockDamagePercent, (s, v) -> s.setShockDamagePercent(v));
        register("burn_duration_percent", "offensive", ComputedStats::getBurnDurationPercent, (s, v) -> s.getOffensive().setBurnDurationPercent(v));

        // =====================================================================
        // OFFENSIVE — CONDITIONAL DAMAGE
        // =====================================================================
        register("execute_damage_percent", "offensive", ComputedStats::getExecuteDamagePercent, (s, v) -> s.setExecuteDamagePercent(v));
        register("damage_vs_frozen_percent", "offensive", ComputedStats::getDamageVsFrozenPercent, (s, v) -> s.setDamageVsFrozenPercent(v));
        register("damage_vs_shocked_percent", "offensive", ComputedStats::getDamageVsShockedPercent, (s, v) -> s.setDamageVsShockedPercent(v));
        register("non_crit_damage_percent", "offensive", ComputedStats::getNonCritDamagePercent, (s, v) -> s.getOffensive().setNonCritDamagePercent(v));
        register("damage_from_mana_percent", "offensive", ComputedStats::getDamageFromManaPercent, (s, v) -> s.getOffensive().setDamageFromManaPercent(v));

        // =====================================================================
        // OFFENSIVE — CONVERSION
        // =====================================================================
        register("fire_conversion", "offensive", ComputedStats::getFireConversion, (s, v) -> s.setFireConversion(v));
        register("water_conversion", "offensive", ComputedStats::getWaterConversion, (s, v) -> s.setWaterConversion(v));
        register("lightning_conversion", "offensive", ComputedStats::getLightningConversion, (s, v) -> s.setLightningConversion(v));
        register("earth_conversion", "offensive", ComputedStats::getEarthConversion, (s, v) -> s.setEarthConversion(v));
        register("wind_conversion", "offensive", ComputedStats::getWindConversion, (s, v) -> s.setWindConversion(v));
        register("void_conversion", "offensive", ComputedStats::getVoidConversion, (s, v) -> s.setVoidConversion(v));

        // =====================================================================
        // OFFENSIVE — PROJECTILE
        // =====================================================================
        register("projectile_speed_percent", "offensive", ComputedStats::getProjectileSpeedPercent, (s, v) -> s.setProjectileSpeedPercent(v));
        register("projectile_gravity_percent", "offensive", ComputedStats::getProjectileGravityPercent, (s, v) -> s.setProjectileGravityPercent(v));
        register("spell_penetration", "offensive", ComputedStats::getSpellPenetration, (s, v) -> s.setSpellPenetration(v));
        register("all_elemental_damage_percent", "offensive", ComputedStats::getAllElementalDamagePercent, (s, v) -> s.setAllElementalDamagePercent(v));

        // =====================================================================
        // OFFENSIVE — MAGIC (Hexcode)
        // =====================================================================
        register("magic_power", "offensive", ComputedStats::getMagicPower, (s, v) -> s.setMagicPower(v));
        register("volatility_max", "offensive", ComputedStats::getVolatilityMax, (s, v) -> s.setVolatilityMax(v));
        register("draw_accuracy", "offensive", ComputedStats::getDrawAccuracy, (s, v) -> s.setDrawAccuracy(v));
        register("cast_speed", "offensive", ComputedStats::getCastSpeed, (s, v) -> s.setCastSpeed(v));

        // =====================================================================
        // OFFENSIVE — KEYSTONE
        // =====================================================================
        register("detonate_dot_on_crit", "offensive", ComputedStats::getDetonateDotOnCrit, (s, v) -> s.setDetonateDotOnCrit(v));
        register("consecutive_hit_bonus", "offensive", ComputedStats::getConsecutiveHitBonus, (s, v) -> s.setConsecutiveHitBonus(v));
        register("spell_echo_chance", "offensive", ComputedStats::getSpellEchoChance, (s, v) -> s.setSpellEchoChance(v));
        register("block_counter_damage", "offensive", ComputedStats::getBlockCounterDamage, (s, v) -> s.setBlockCounterDamage(v));

        // =====================================================================
        // DEFENSIVE STATS
        // =====================================================================
        register("armor", "defensive", ComputedStats::getArmor, (s, v) -> s.setArmor(v));
        register("armor_percent", "defensive", ComputedStats::getArmorPercent, (s, v) -> s.setArmorPercent(v));
        register("evasion", "defensive", ComputedStats::getEvasion, (s, v) -> s.setEvasion(v));
        register("dodge_chance", "defensive", ComputedStats::getDodgeChance, (s, v) -> s.setDodgeChance(v));
        register("block_chance", "defensive", ComputedStats::getBlockChance, (s, v) -> s.setBlockChance(v));
        register("block_damage_reduction", "defensive", ComputedStats::getBlockDamageReduction, (s, v) -> s.setBlockDamageReduction(v));
        register("passive_block_chance", "defensive", ComputedStats::getPassiveBlockChance, (s, v) -> s.setPassiveBlockChance(v));
        register("parry_chance", "defensive", ComputedStats::getParryChance, (s, v) -> s.setParryChance(v));
        register("knockback_resistance", "defensive", ComputedStats::getKnockbackResistance, (s, v) -> s.setKnockbackResistance(v));
        register("fall_damage_reduction", "defensive", ComputedStats::getFallDamageReduction, (s, v) -> s.setFallDamageReduction(v));
        register("crit_nullify_chance", "defensive", ComputedStats::getCritNullifyChance, (s, v) -> s.setCritNullifyChance(v));
        register("physical_resistance", "defensive", ComputedStats::getPhysicalResistance, (s, v) -> s.setPhysicalResistance(v));
        register("critical_reduction", "defensive", ComputedStats::getCriticalReduction, (s, v) -> s.setCriticalReduction(v));
        register("damage_taken_percent", "defensive", ComputedStats::getDamageTakenPercent, (s, v) -> s.setDamageTakenPercent(v));
        register("stamina_drain_reduction", "defensive", ComputedStats::getStaminaDrainReduction, (s, v) -> s.setStaminaDrainReduction(v));
        register("block_heal_percent", "defensive", ComputedStats::getBlockHealPercent, (s, v) -> s.setBlockHealPercent(v));
        register("shield_effectiveness_percent", "defensive", ComputedStats::getShieldEffectivenessPercent, (s, v) -> s.setShieldEffectivenessPercent(v));
        register("health_recovery_percent", "defensive", ComputedStats::getHealthRecoveryPercent, (s, v) -> s.setHealthRecoveryPercent(v));
        register("mana_as_damage_buffer", "defensive", ComputedStats::getManaAsDamageBuffer, (s, v) -> s.setManaAsDamageBuffer(v));

        // Thorns / Reflect
        register("thorns_damage", "defensive", ComputedStats::getThornsDamage, (s, v) -> s.setThornsDamage(v));
        register("thorns_damage_percent", "defensive", ComputedStats::getThornsDamagePercent, (s, v) -> s.setThornsDamagePercent(v));
        register("reflect_damage_percent", "defensive", ComputedStats::getReflectDamagePercent, (s, v) -> s.setReflectDamagePercent(v));

        // Ailment thresholds
        register("burn_threshold", "defensive", ComputedStats::getBurnThreshold, (s, v) -> s.setBurnThreshold(v));
        register("freeze_threshold", "defensive", ComputedStats::getFreezeThreshold, (s, v) -> s.setFreezeThreshold(v));
        register("shock_threshold", "defensive", ComputedStats::getShockThreshold, (s, v) -> s.setShockThreshold(v));

        // Keystone defensive
        register("shield_regen_on_dot", "defensive", ComputedStats::getShieldRegenOnDot, (s, v) -> s.setShieldRegenOnDot(v));
        register("immunity_on_ailment", "defensive", ComputedStats::getImmunityOnAilment, (s, v) -> s.setImmunityOnAilment(v));
        register("evasion_to_armor", "defensive", ComputedStats::getEvasionToArmor, (s, v) -> s.setEvasionToArmor(v));

        // =====================================================================
        // ELEMENTAL — DAMAGE / RESISTANCE / PENETRATION
        // =====================================================================
        for (var elem : List.of("fire", "water", "lightning", "earth", "wind", "void")) {
            registerElemental(elem);
        }

        // =====================================================================
        // ELEMENTAL RESISTANCES (also accessible directly)
        // =====================================================================
        // Already registered via registerElemental()

        // =====================================================================
        // MOVEMENT STATS
        // =====================================================================
        register("movement_speed_percent", "movement", ComputedStats::getMovementSpeedPercent, (s, v) -> s.setMovementSpeedPercent(v));
        register("walk_speed_percent", "movement", ComputedStats::getWalkSpeedPercent, (s, v) -> s.setWalkSpeedPercent(v));
        register("run_speed_percent", "movement", ComputedStats::getRunSpeedPercent, (s, v) -> s.setRunSpeedPercent(v));
        register("sprint_speed_bonus", "movement", ComputedStats::getSprintSpeedBonus, (s, v) -> s.setSprintSpeedBonus(v));
        register("crouch_speed_percent", "movement", ComputedStats::getCrouchSpeedPercent, (s, v) -> s.setCrouchSpeedPercent(v));
        register("jump_force_bonus", "movement", ComputedStats::getJumpForceBonus, (s, v) -> s.setJumpForceBonus(v));
        register("jump_force_percent", "movement", ComputedStats::getJumpForcePercent, (s, v) -> s.setJumpForcePercent(v));
        register("climb_speed_bonus", "movement", ComputedStats::getClimbSpeedBonus, (s, v) -> s.setClimbSpeedBonus(v));

        // =====================================================================
        // UTILITY
        // =====================================================================
        register("experience_gain_percent", "utility", ComputedStats::getExperienceGainPercent, (s, v) -> s.setExperienceGainPercent(v));
    }

    private static void register(
        String name,
        String category,
        Function<ComputedStats, Float> getter,
        BiConsumer<ComputedStats, Float> setter
    ) {
        StatEntry entry = new StatEntry(name, category, getter, setter);
        ENTRIES.put(name.toLowerCase(), entry);
        BY_CATEGORY.computeIfAbsent(category, k -> new ArrayList<>()).add(entry);
    }

    /**
     * Registers elemental damage, resistance, and penetration for one element.
     */
    private static void registerElemental(String element) {
        String cat = "elemental";
        switch (element) {
            case "fire" -> {
                register("fire_damage", cat, ComputedStats::getFireDamage, (s, v) -> s.setFireDamage(v));
                register("fire_damage_percent", cat, ComputedStats::getFireDamagePercent, (s, v) -> s.setFireDamagePercent(v));
                register("fire_resistance", cat, ComputedStats::getFireResistance, (s, v) -> s.setFireResistance(v));
                register("fire_penetration", cat, ComputedStats::getFirePenetration, (s, v) -> s.setFirePenetration(v));
            }
            case "water" -> {
                register("water_damage", cat, ComputedStats::getWaterDamage, (s, v) -> s.setWaterDamage(v));
                register("water_damage_percent", cat, ComputedStats::getWaterDamagePercent, (s, v) -> s.setWaterDamagePercent(v));
                register("water_resistance", cat, ComputedStats::getWaterResistance, (s, v) -> s.setWaterResistance(v));
                register("water_penetration", cat, ComputedStats::getWaterPenetration, (s, v) -> s.setWaterPenetration(v));
            }
            case "lightning" -> {
                register("lightning_damage", cat, ComputedStats::getLightningDamage, (s, v) -> s.setLightningDamage(v));
                register("lightning_damage_percent", cat, ComputedStats::getLightningDamagePercent, (s, v) -> s.setLightningDamagePercent(v));
                register("lightning_resistance", cat, ComputedStats::getLightningResistance, (s, v) -> s.setLightningResistance(v));
                register("lightning_penetration", cat, ComputedStats::getLightningPenetration, (s, v) -> s.setLightningPenetration(v));
            }
            case "earth" -> {
                register("earth_damage", cat, ComputedStats::getEarthDamage, (s, v) -> s.setEarthDamage(v));
                register("earth_damage_percent", cat, ComputedStats::getEarthDamagePercent, (s, v) -> s.setEarthDamagePercent(v));
                register("earth_resistance", cat, ComputedStats::getEarthResistance, (s, v) -> s.setEarthResistance(v));
                register("earth_penetration", cat, ComputedStats::getEarthPenetration, (s, v) -> s.setEarthPenetration(v));
            }
            case "wind" -> {
                register("wind_damage", cat, ComputedStats::getWindDamage, (s, v) -> s.setWindDamage(v));
                register("wind_damage_percent", cat, ComputedStats::getWindDamagePercent, (s, v) -> s.setWindDamagePercent(v));
                register("wind_resistance", cat, ComputedStats::getWindResistance, (s, v) -> s.setWindResistance(v));
                register("wind_penetration", cat, ComputedStats::getWindPenetration, (s, v) -> s.setWindPenetration(v));
            }
            case "void" -> {
                register("void_damage", cat, ComputedStats::getVoidDamage, (s, v) -> s.setVoidDamage(v));
                register("void_damage_percent", cat, ComputedStats::getVoidDamagePercent, (s, v) -> s.setVoidDamagePercent(v));
                register("void_resistance", cat, ComputedStats::getVoidResistance, (s, v) -> s.setVoidResistance(v));
                register("void_penetration", cat, ComputedStats::getVoidPenetration, (s, v) -> s.setVoidPenetration(v));
            }
        }
    }

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /** Looks up a stat entry by name (case-insensitive). */
    @Nullable
    public static StatEntry get(@Nonnull String name) {
        return ENTRIES.get(name.toLowerCase().trim());
    }

    /** Returns true if a stat name is registered. */
    public static boolean exists(@Nonnull String name) {
        return ENTRIES.containsKey(name.toLowerCase().trim());
    }

    /** Gets all registered stat entries (insertion-ordered). */
    @Nonnull
    public static Collection<StatEntry> all() {
        return Collections.unmodifiableCollection(ENTRIES.values());
    }

    /** Gets all registered stat names (insertion-ordered). */
    @Nonnull
    public static Set<String> allNames() {
        return Collections.unmodifiableSet(ENTRIES.keySet());
    }

    /** Gets stat entries for a specific category. */
    @Nonnull
    public static List<StatEntry> byCategory(@Nonnull String category) {
        return BY_CATEGORY.getOrDefault(category.toLowerCase(), List.of());
    }

    /** Gets all category names. */
    @Nonnull
    public static Set<String> categories() {
        return Collections.unmodifiableSet(BY_CATEGORY.keySet());
    }

    /** Gets the total count of registered stats. */
    public static int size() {
        return ENTRIES.size();
    }

    private DebugStatRegistry() {}
}
