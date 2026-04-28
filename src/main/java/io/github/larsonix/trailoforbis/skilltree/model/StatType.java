package io.github.larsonix.trailoforbis.skilltree.model;

import io.github.larsonix.trailoforbis.attributes.ComputedStats;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Maps 1:1 with ComputedStats fields for type-safe modifier application.
 *
 * <p>Each enum value includes:
 * <ul>
 *   <li>{@code fieldName} — exact field name in ComputedStats</li>
 *   <li>{@code shortName} — compact label for nameplates (max ~10 chars, e.g., "HP", "Crit Multi")</li>
 *   <li>{@code displayName} — full readable name for detail pages (e.g., "Max Health", "Critical Multiplier")</li>
 * </ul>
 *
 * <p>Neither {@code shortName} nor {@code displayName} includes "Percent" or "Multiplier" suffixes —
 * those are the responsibility of {@link ModifierType} when formatting display strings.
 */
public enum StatType {
    // Core Stats
    MAX_HEALTH("maxHealth", "HP", "Max Health", false,
        ComputedStats::getMaxHealth, ComputedStats.Builder::maxHealth),
    MAX_MANA("maxMana", "Mana", "Max Mana", false,
        ComputedStats::getMaxMana, ComputedStats.Builder::maxMana),
    MAX_STAMINA("maxStamina", "Stamina", "Max Stamina", false,
        ComputedStats::getMaxStamina, ComputedStats.Builder::maxStamina),

    // Flat Damage
    PHYSICAL_DAMAGE("physicalDamage", "Phys DMG", "Physical Damage", false,
        ComputedStats::getPhysicalDamage, ComputedStats.Builder::physicalDamage),
    SPELL_DAMAGE("spellDamage", "Spell DMG", "Spell Damage", false,
        ComputedStats::getSpellDamage, ComputedStats.Builder::spellDamage),
    FIRE_DAMAGE("fireDamage", "Fire DMG", "Fire Damage", false,
        ComputedStats::getFireDamage, ComputedStats.Builder::fireDamage),
    WATER_DAMAGE("waterDamage", "Water DMG", "Water Damage", false,
        ComputedStats::getWaterDamage, ComputedStats.Builder::waterDamage),
    LIGHTNING_DAMAGE("lightningDamage", "Light DMG", "Lightning Damage", false,
        ComputedStats::getLightningDamage, ComputedStats.Builder::lightningDamage),
    EARTH_DAMAGE("earthDamage", "Earth DMG", "Earth Damage", false,
        ComputedStats::getEarthDamage, ComputedStats.Builder::earthDamage),
    WIND_DAMAGE("windDamage", "Wind DMG", "Wind Damage", false,
        ComputedStats::getWindDamage, ComputedStats.Builder::windDamage),
    VOID_DAMAGE("voidDamage", "Void DMG", "Void Damage", false,
        ComputedStats::getVoidDamage, ComputedStats.Builder::voidDamage),

    // Percent Damage
    PHYSICAL_DAMAGE_PERCENT("physicalDamagePercent", "Phys DMG", "Physical Damage", false,
        ComputedStats::getPhysicalDamagePercent, ComputedStats.Builder::physicalDamagePercent),
    SPELL_DAMAGE_PERCENT("spellDamagePercent", "Spell DMG", "Spell Damage", false,
        ComputedStats::getSpellDamagePercent, ComputedStats.Builder::spellDamagePercent),
    FIRE_DAMAGE_PERCENT("fireDamagePercent", "Fire DMG", "Fire Damage", false,
        ComputedStats::getFireDamagePercent, ComputedStats.Builder::fireDamagePercent),
    WATER_DAMAGE_PERCENT("waterDamagePercent", "Water DMG", "Water Damage", false,
        ComputedStats::getWaterDamagePercent, ComputedStats.Builder::waterDamagePercent),
    LIGHTNING_DAMAGE_PERCENT("lightningDamagePercent", "Light DMG", "Lightning Damage", false,
        ComputedStats::getLightningDamagePercent, ComputedStats.Builder::lightningDamagePercent),
    EARTH_DAMAGE_PERCENT("earthDamagePercent", "Earth DMG", "Earth Damage", false,
        ComputedStats::getEarthDamagePercent, ComputedStats.Builder::earthDamagePercent),
    WIND_DAMAGE_PERCENT("windDamagePercent", "Wind DMG", "Wind Damage", false,
        ComputedStats::getWindDamagePercent, ComputedStats.Builder::windDamagePercent),
    VOID_DAMAGE_PERCENT("voidDamagePercent", "Void DMG", "Void Damage", false,
        ComputedStats::getVoidDamagePercent, ComputedStats.Builder::voidDamagePercent),

    // Damage Multipliers (PoE-style "more" damage)
    FIRE_DAMAGE_MULTIPLIER("fireDamageMultiplier", "Fire DMG", "Fire Damage", true,
        ComputedStats::getFireDamageMultiplier, ComputedStats.Builder::fireDamageMultiplier),
    WATER_DAMAGE_MULTIPLIER("waterDamageMultiplier", "Water DMG", "Water Damage", true,
        ComputedStats::getWaterDamageMultiplier, ComputedStats.Builder::waterDamageMultiplier),
    LIGHTNING_DAMAGE_MULTIPLIER("lightningDamageMultiplier", "Light DMG", "Lightning Damage", true,
        ComputedStats::getLightningDamageMultiplier, ComputedStats.Builder::lightningDamageMultiplier),
    EARTH_DAMAGE_MULTIPLIER("earthDamageMultiplier", "Earth DMG", "Earth Damage", true,
        ComputedStats::getEarthDamageMultiplier, ComputedStats.Builder::earthDamageMultiplier),
    WIND_DAMAGE_MULTIPLIER("windDamageMultiplier", "Wind DMG", "Wind Damage", true,
        ComputedStats::getWindDamageMultiplier, ComputedStats.Builder::windDamageMultiplier),
    VOID_DAMAGE_MULTIPLIER("voidDamageMultiplier", "Void DMG", "Void Damage", true,
        ComputedStats::getVoidDamageMultiplier, ComputedStats.Builder::voidDamageMultiplier),

    // Penetration (ignore enemy resistance)
    FIRE_PENETRATION("firePenetration", "Fire Pen", "Fire Penetration", true,
        ComputedStats::getFirePenetration, ComputedStats.Builder::firePenetration),
    WATER_PENETRATION("waterPenetration", "Water Pen", "Water Penetration", true,
        ComputedStats::getWaterPenetration, ComputedStats.Builder::waterPenetration),
    LIGHTNING_PENETRATION("lightningPenetration", "Light Pen", "Lightning Penetration", true,
        ComputedStats::getLightningPenetration, ComputedStats.Builder::lightningPenetration),
    EARTH_PENETRATION("earthPenetration", "Earth Pen", "Earth Penetration", true,
        ComputedStats::getEarthPenetration, ComputedStats.Builder::earthPenetration),
    WIND_PENETRATION("windPenetration", "Wind Pen", "Wind Penetration", true,
        ComputedStats::getWindPenetration, ComputedStats.Builder::windPenetration),
    VOID_PENETRATION("voidPenetration", "Void Pen", "Void Penetration", true,
        ComputedStats::getVoidPenetration, ComputedStats.Builder::voidPenetration),
    ARMOR_PENETRATION("armorPenetration", "Armor Pen", "Armor Penetration", true,
        ComputedStats::getArmorPenetration, ComputedStats.Builder::armorPenetration),

    // Conversion (convert source element to target)
    FIRE_CONVERSION("fireConversion", "Fire Conv", "Fire Conversion", true,
        ComputedStats::getFireConversion, ComputedStats.Builder::fireConversion),
    WATER_CONVERSION("waterConversion", "Water Conv", "Water Conversion", true,
        ComputedStats::getWaterConversion, ComputedStats.Builder::waterConversion),
    LIGHTNING_CONVERSION("lightningConversion", "Light Conv", "Lightning Conversion", true,
        ComputedStats::getLightningConversion, ComputedStats.Builder::lightningConversion),
    EARTH_CONVERSION("earthConversion", "Earth Conv", "Earth Conversion", true,
        ComputedStats::getEarthConversion, ComputedStats.Builder::earthConversion),
    WIND_CONVERSION("windConversion", "Wind Conv", "Wind Conversion", true,
        ComputedStats::getWindConversion, ComputedStats.Builder::windConversion),
    VOID_CONVERSION("voidConversion", "Void Conv", "Void Conversion", true,
        ComputedStats::getVoidConversion, ComputedStats.Builder::voidConversion),

    // Status Effects
    STATUS_EFFECT_CHANCE("statusEffectChance", "Status%", "Status Effect Chance", true,
        ComputedStats::getStatusEffectChance, ComputedStats.Builder::statusEffectChance),
    STATUS_EFFECT_DURATION("statusEffectDuration", "Status Dur", "Status Effect Duration", true,
        ComputedStats::getStatusEffectDuration, ComputedStats.Builder::statusEffectDuration),

    // Elemental Ailment Damage
    BURN_DAMAGE("burnDamage", "Burn DMG", "Burn Damage", false,
        ComputedStats::getBurnDamage, ComputedStats.Builder::burnDamage),
    FREEZE_DAMAGE("freezeDamage", "Freeze DMG", "Freeze Damage", false,
        ComputedStats::getFreezeDamage, ComputedStats.Builder::freezeDamage),
    SHOCK_DAMAGE("shockDamage", "Shock DMG", "Shock Damage", false,
        ComputedStats::getShockDamage, ComputedStats.Builder::shockDamage),
    POISON_DAMAGE("poisonDamage", "Poison DMG", "Poison Damage", false,
        ComputedStats::getPoisonDamage, ComputedStats.Builder::poisonDamage),

    // Elemental Ailment Damage Percent
    BURN_DAMAGE_PERCENT("burnDamagePercent", "Burn DMG", "Burn Damage", true,
        ComputedStats::getBurnDamagePercent, ComputedStats.Builder::burnDamagePercent),
    FROST_DAMAGE_PERCENT("frostDamagePercent", "Frost DMG", "Frost Damage", true,
        ComputedStats::getFrostDamagePercent, ComputedStats.Builder::frostDamagePercent),
    SHOCK_DAMAGE_PERCENT("shockDamagePercent", "Shock DMG", "Shock Damage", true,
        ComputedStats::getShockDamagePercent, ComputedStats.Builder::shockDamagePercent),

    // Elemental Ailment Threshold
    BURN_THRESHOLD("burnThreshold", "Burn Thresh", "Burn Threshold", false,
        ComputedStats::getBurnThreshold, ComputedStats.Builder::burnThreshold),
    FREEZE_THRESHOLD("freezeThreshold", "Freeze Thresh", "Freeze Threshold", false,
        ComputedStats::getFreezeThreshold, ComputedStats.Builder::freezeThreshold),
    SHOCK_THRESHOLD("shockThreshold", "Shock Thresh", "Shock Threshold", false,
        ComputedStats::getShockThreshold, ComputedStats.Builder::shockThreshold),

    // Critical
    CRITICAL_CHANCE("criticalChance", "Crit", "Critical Chance", true,
        ComputedStats::getCriticalChance, ComputedStats.Builder::criticalChance),
    CRITICAL_MULTIPLIER("criticalMultiplier", "Crit Multi", "Crit Damage", true,
        ComputedStats::getCriticalMultiplier, ComputedStats.Builder::criticalMultiplier),

    // Attack Type Modifiers
    MELEE_DAMAGE_PERCENT("meleeDamagePercent", "Melee DMG", "Melee Damage", false,
        ComputedStats::getMeleeDamagePercent, ComputedStats.Builder::meleeDamagePercent),
    PROJECTILE_DAMAGE_PERCENT("projectileDamagePercent", "Proj DMG", "Projectile Damage", false,
        ComputedStats::getProjectileDamagePercent, ComputedStats.Builder::projectileDamagePercent),
    CHARGED_ATTACK_DAMAGE_PERCENT("chargedAttackDamagePercent", "Charged DMG", "Charged Attack Damage", false,
        ComputedStats::getChargedAttackDamagePercent, ComputedStats.Builder::chargedAttackDamagePercent),

    // Speed & Movement
    MOVEMENT_SPEED_PERCENT("movementSpeedPercent", "Move Spd", "Movement Speed", false,
        ComputedStats::getMovementSpeedPercent, ComputedStats.Builder::movementSpeedPercent),
    WALK_SPEED_PERCENT("walkSpeedPercent", "Walk Spd", "Walk Speed", false,
        ComputedStats::getWalkSpeedPercent, ComputedStats.Builder::walkSpeedPercent),
    ATTACK_SPEED_PERCENT("attackSpeedPercent", "Atk Spd", "Attack Speed", false,
        ComputedStats::getAttackSpeedPercent, ComputedStats.Builder::attackSpeedPercent),
    JUMP_FORCE_BONUS("jumpForceBonus", "Jump", "Jump Force", false,
        ComputedStats::getJumpForceBonus, ComputedStats.Builder::jumpForceBonus),
    JUMP_FORCE_PERCENT("jumpForcePercent", "Jump", "Jump Force", false,
        ComputedStats::getJumpForcePercent, ComputedStats.Builder::jumpForcePercent),
    SPRINT_SPEED_BONUS("sprintSpeedBonus", "Sprint Spd", "Sprint Speed", false,
        ComputedStats::getSprintSpeedBonus, ComputedStats.Builder::sprintSpeedBonus),
    CLIMB_SPEED_BONUS("climbSpeedBonus", "Climb Spd", "Climb Speed", false,
        ComputedStats::getClimbSpeedBonus, ComputedStats.Builder::climbSpeedBonus),
    CROUCH_SPEED_PERCENT("crouchSpeedPercent", "Crouch Spd", "Crouch Speed", false,
        ComputedStats::getCrouchSpeedPercent, ComputedStats.Builder::crouchSpeedPercent),

    // Defense
    ARMOR("armor", "Armor", "Armor", false,
        ComputedStats::getArmor, ComputedStats.Builder::armor),
    ARMOR_PERCENT("armorPercent", "Armor", "Armor", false,
        ComputedStats::getArmorPercent, ComputedStats.Builder::armorPercent),
    EVASION("evasion", "Evasion", "Evasion", false,
        ComputedStats::getEvasion, ComputedStats.Builder::evasion),
    ENERGY_SHIELD("energyShield", "ES", "Energy Shield", false,
        ComputedStats::getEnergyShield, ComputedStats.Builder::energyShield),
    KNOCKBACK_RESISTANCE("knockbackResistance", "KB Res", "Knockback Resistance", true,
        ComputedStats::getKnockbackResistance, ComputedStats.Builder::knockbackResistance),
    PARRY_CHANCE("parryChance", "Parry", "Parry Chance", true,
        ComputedStats::getParryChance, ComputedStats.Builder::parryChance),

    // Resistances
    FIRE_RESISTANCE("fireResistance", "Fire Res", "Fire Resistance", true,
        ComputedStats::getFireResistance, ComputedStats.Builder::fireResistance),
    WATER_RESISTANCE("waterResistance", "Water Res", "Water Resistance", true,
        ComputedStats::getWaterResistance, ComputedStats.Builder::waterResistance),
    LIGHTNING_RESISTANCE("lightningResistance", "Light Res", "Lightning Resistance", true,
        ComputedStats::getLightningResistance, ComputedStats.Builder::lightningResistance),
    EARTH_RESISTANCE("earthResistance", "Earth Res", "Earth Resistance", true,
        ComputedStats::getEarthResistance, ComputedStats.Builder::earthResistance),
    WIND_RESISTANCE("windResistance", "Wind Res", "Wind Resistance", true,
        ComputedStats::getWindResistance, ComputedStats.Builder::windResistance),
    VOID_RESISTANCE("voidResistance", "Void Res", "Void Resistance", true,
        ComputedStats::getVoidResistance, ComputedStats.Builder::voidResistance),

    // Regeneration
    HEALTH_REGEN("healthRegen", "HP Regen", "Health Regen", false,
        ComputedStats::getHealthRegen, ComputedStats.Builder::healthRegen),
    MANA_REGEN("manaRegen", "Mana Regen", "Mana Regen", false,
        ComputedStats::getManaRegen, ComputedStats.Builder::manaRegen),
    STAMINA_REGEN("staminaRegen", "Stam Regen", "Stamina Regen", false,
        ComputedStats::getStaminaRegen, ComputedStats.Builder::staminaRegen),
    HEALTH_REGEN_PERCENT("healthRegenPercent", "HP Regen", "Health Regen", false,
        ComputedStats::getHealthRegenPercent, ComputedStats.Builder::healthRegenPercent),

    // Global Damage
    ALL_DAMAGE_PERCENT("allDamagePercent", "All DMG", "All Damage", false,
        ComputedStats::getAllDamagePercent, ComputedStats.Builder::allDamagePercent),
    MELEE_DAMAGE("meleeDamage", "Melee DMG", "Melee Damage", false,
        ComputedStats::getMeleeDamage, ComputedStats.Builder::meleeDamage),

    // Accuracy & Evasion
    ACCURACY("accuracy", "Accuracy", "Accuracy", false,
        ComputedStats::getAccuracy, ComputedStats.Builder::accuracy),
    ACCURACY_PERCENT("accuracyPercent", "Accuracy", "Accuracy", true,
        ComputedStats::getAccuracyPercent, ComputedStats.Builder::accuracyPercent),
    EVASION_PERCENT("evasionPercent", "Evasion", "Evasion", false,
        null, null),
    DODGE_CHANCE("dodgeChance", "Dodge", "Dodge Chance", true,
        ComputedStats::getDodgeChance, ComputedStats.Builder::dodgeChance),

    // Leech & Sustain
    LIFE_LEECH("lifeLeech", "Life Leech", "Life Leech", true,
        ComputedStats::getLifeLeech, ComputedStats.Builder::lifeLeech),
    MANA_LEECH("manaLeech", "Mana Leech", "Mana Leech", true,
        ComputedStats::getManaLeech, ComputedStats.Builder::manaLeech),

    // Conditional
    DAMAGE_AT_LOW_LIFE("damageAtLowLife", "Low Life DMG", "Damage at Low Life", false,
        ComputedStats::getDamageAtLowLife, ComputedStats.Builder::damageAtLowLife),

    // Cost
    MANA_COST_REDUCTION("manaCostReduction", "Mana Cost", "Mana Cost Reduction", true,
        ComputedStats::getManaCostReduction, ComputedStats.Builder::manaCostReduction),

    // Special
    MANA_AS_DAMAGE_BUFFER("manaAsDamageBuffer", "Mana Shield", "Mana as Damage Buffer", false,
        ComputedStats::getManaAsDamageBuffer, ComputedStats.Builder::manaAsDamageBuffer),
    EXPERIENCE_GAIN("experienceGain", "XP Gain", "Experience Gain", true,
        ComputedStats::getExperienceGainPercent, ComputedStats.Builder::experienceGainPercent),
    CRIT_NULLIFY_CHANCE("critNullifyChance", "Crit Null", "Crit Nullify Chance", true,
        ComputedStats::getCritNullifyChance, ComputedStats.Builder::critNullifyChance),
    CRITICAL_REDUCTION("criticalReduction", "Crit Red", "Critical Reduction", true,
        ComputedStats::getCriticalReduction, ComputedStats.Builder::criticalReduction),

    // Active Block Modifiers
    BLOCK_CHANCE("blockChance", "Block", "Block Chance", true,
        ComputedStats::getBlockChance, ComputedStats.Builder::blockChance),
    BLOCK_DAMAGE_REDUCTION("blockDamageReduction", "Block Red", "Block Damage Reduction", true,
        ComputedStats::getBlockDamageReduction, ComputedStats.Builder::blockDamageReduction),
    STAMINA_DRAIN_REDUCTION("staminaDrainReduction", "Stam Drain", "Stamina Drain Reduction", true,
        ComputedStats::getStaminaDrainReduction, ComputedStats.Builder::staminaDrainReduction),

    // Resistances
    ELEMENTAL_RESISTANCE("elementalResistance", "Elem Res", "Elemental Resistance", true,
        null, null),
    PHYSICAL_RESISTANCE("physicalResistance", "Phys Res", "Physical Resistance", true,
        ComputedStats::getPhysicalResistance, ComputedStats.Builder::physicalResistance),

    // Damage Reduction
    FALL_DAMAGE_REDUCTION("fallDamageReduction", "Fall Red", "Fall Damage Reduction", true,
        ComputedStats::getFallDamageReduction, ComputedStats.Builder::fallDamageReduction),

    // ═══════════════════════════════════════════════════════════════════
    // SKILL TREE SPECIFIC STATS
    // These are for skill tree nodes and may not have combat implementations yet
    // ═══════════════════════════════════════════════════════════════════

    // Advanced Conversion (source-to-target type)
    SPELL_TO_PHYSICAL_CONVERSION("spellToPhysicalConversion", "Spell>Phys", "Spell to Physical Conversion", true,
        null, null),
    PHYSICAL_TO_SPELL_CONVERSION("physicalToSpellConversion", "Phys>Spell", "Physical to Spell Conversion", true,
        null, null),
    PHYSICAL_TO_FIRE_CONVERSION("physicalToFireConversion", "Phys>Fire", "Physical to Fire Conversion", true,
        null, null),
    DAMAGE_TO_MANA_CONVERSION("damageToManaConversion", "DMG>Mana", "Damage to Mana Conversion", true,
        null, null),
    DAMAGE_TO_VOID_CONVERSION("damageToVoidConversion", "DMG>Void", "Damage to Void Conversion", true,
        null, null),

    // Enemy Debuffs
    ENEMY_ELEMENTAL_VULNERABILITY("enemyElementalVulnerability", "Elem Vuln", "Enemy Elemental Vulnerability", true,
        null, null),
    ENEMY_RESISTANCE_REDUCTION("enemyResistanceReduction", "Res Shred", "Enemy Resistance Reduction", true,
        null, null),

    // Percent Stats
    MAX_HEALTH_PERCENT("maxHealthPercent", "HP", "Max Health", false,
        ComputedStats::getMaxHealthPercent, ComputedStats.Builder::maxHealthPercent),
    MAX_MANA_PERCENT("maxManaPercent", "Mana", "Max Mana", false,
        ComputedStats::getMaxManaPercent, ComputedStats.Builder::maxManaPercent),
    DAMAGE_PERCENT("damagePercent", "DMG", "Damage", false,
        ComputedStats::getDamagePercent, ComputedStats.Builder::damagePercent),
    DAMAGE_MULTIPLIER("damageMultiplier", "DMG", "Damage", true,
        ComputedStats::getDamageMultiplier, ComputedStats.Builder::damageMultiplier),
    DAMAGE_TAKEN_PERCENT("damageTakenPercent", "DMG Taken", "Damage Taken", false,
        ComputedStats::getDamageTakenPercent, ComputedStats.Builder::damageTakenPercent),
    DAMAGE_WHEN_HIT_PERCENT("damageWhenHitPercent", "DMG on Hit", "Damage When Hit", false,
        ComputedStats::getDamageWhenHitPercent, ComputedStats.Builder::damageWhenHitPercent),
    THORNS_DAMAGE_PERCENT("thornsDamagePercent", "Thorns", "Thorns Damage", false,
        ComputedStats::getThornsDamagePercent, ComputedStats.Builder::thornsDamagePercent),
    EXECUTE_DAMAGE_PERCENT("executeDamagePercent", "Execute", "Execute Damage", false,
        ComputedStats::getExecuteDamagePercent, ComputedStats.Builder::executeDamagePercent),
    DOT_DAMAGE_PERCENT("dotDamagePercent", "DoT DMG", "DoT Damage", false,
        ComputedStats::getDotDamagePercent, ComputedStats.Builder::dotDamagePercent),
    PERCENT_HIT_AS_TRUE_DAMAGE("percentHitAsTrueDamage", "True DMG", "True Damage", true,
        ComputedStats::getPercentHitAsTrueDamage, ComputedStats.Builder::percentHitAsTrueDamage),
    NON_CRIT_DAMAGE_PERCENT("nonCritDamagePercent", "Non-Crit", "Non-Crit Damage", false,
        ComputedStats::getNonCritDamagePercent, ComputedStats.Builder::nonCritDamagePercent),

    // Conditional Damage
    DAMAGE_VS_FROZEN_PERCENT("damageVsFrozenPercent", "vs Frozen", "Damage vs Frozen", false,
        ComputedStats::getDamageVsFrozenPercent, ComputedStats.Builder::damageVsFrozenPercent),
    DAMAGE_VS_SHOCKED_PERCENT("damageVsShockedPercent", "vs Shocked", "Damage vs Shocked", false,
        ComputedStats::getDamageVsShockedPercent, ComputedStats.Builder::damageVsShockedPercent),
    DAMAGE_FROM_MANA_PERCENT("damageFromManaPercent", "Mana>DMG", "Damage from Mana", false,
        ComputedStats::getDamageFromManaPercent, ComputedStats.Builder::damageFromManaPercent),

    // Status Chances
    IGNITE_CHANCE("igniteChance", "Ignite", "Ignite Chance", true,
        ComputedStats::getIgniteChance, ComputedStats.Builder::igniteChance),
    FREEZE_CHANCE("freezeChance", "Freeze", "Freeze Chance", true,
        ComputedStats::getFreezeChance, ComputedStats.Builder::freezeChance),
    SHOCK_CHANCE("shockChance", "Shock", "Shock Chance", true,
        ComputedStats::getShockChance, ComputedStats.Builder::shockChance),

    // Duration Modifiers
    BURN_DURATION_PERCENT("burnDurationPercent", "Burn Dur", "Burn Duration", true,
        ComputedStats::getBurnDurationPercent, ComputedStats.Builder::burnDurationPercent),

    // Leech & Steal (separate from LIFE_LEECH/MANA_LEECH)
    LIFE_STEAL("lifeSteal", "Life Steal", "Life Steal", true,
        ComputedStats::getLifeSteal, ComputedStats.Builder::lifeSteal),
    MANA_STEAL("manaSteal", "Mana Steal", "Mana Steal", true,
        ComputedStats::getManaSteal, ComputedStats.Builder::manaSteal),
    MANA_ON_KILL("manaOnKill", "Mana/Kill", "Mana on Kill", false,
        ComputedStats::getManaOnKill, ComputedStats.Builder::manaOnKill),

    // Recovery & Regen
    HEALTH_RECOVERY_PERCENT("healthRecoveryPercent", "HP Recovery", "Health Recovery", false,
        ComputedStats::getHealthRecoveryPercent, ComputedStats.Builder::healthRecoveryPercent),

    // Passive Block & Shield
    PASSIVE_BLOCK_CHANCE("passiveBlockChance", "Pass Block", "Passive Block Chance", true,
        ComputedStats::getPassiveBlockChance, ComputedStats.Builder::passiveBlockChance),
    BLOCK_RECOVERY_PERCENT("blockRecoveryPercent", "Block Rec", "Block Recovery", false,
        ComputedStats::getBlockRecoveryPercent, ComputedStats.Builder::blockRecoveryPercent),
    BLOCK_HEAL_PERCENT("blockHealPercent", "Block Heal", "Block Heal", false,
        ComputedStats::getBlockHealPercent, ComputedStats.Builder::blockHealPercent),
    SHIELD_EFFECTIVENESS_PERCENT("shieldEffectivenessPercent", "Shield Eff", "Shield Effectiveness", false,
        ComputedStats::getShieldEffectivenessPercent, ComputedStats.Builder::shieldEffectivenessPercent),

    // Projectile
    PROJECTILE_SPEED_PERCENT("projectileSpeedPercent", "Proj Spd", "Projectile Speed", false,
        ComputedStats::getProjectileSpeedPercent, ComputedStats.Builder::projectileSpeedPercent),
    PROJECTILE_GRAVITY_PERCENT("projectileGravityPercent", "Proj Grav", "Projectile Gravity", false,
        ComputedStats::getProjectileGravityPercent, ComputedStats.Builder::projectileGravityPercent),

    // Mana Cost
    MANA_COST_PERCENT("manaCostPercent", "Mana Cost", "Mana Cost", false,
        ComputedStats::getManaCostPercent, ComputedStats.Builder::manaCostPercent),

    // Elemental Combined
    ALL_ELEMENTAL_DAMAGE_PERCENT("allElementalDamagePercent", "Elem DMG", "Elemental Damage", false,
        ComputedStats::getAllElementalDamagePercent, null),
    SPELL_PENETRATION("spellPenetration", "Spell Pen", "Spell Penetration", true,
        ComputedStats::getSpellPenetration, ComputedStats.Builder::spellPenetration),

    // Signature Energy
    SIGNATURE_ENERGY_MAX_PERCENT("signatureEnergyMaxPercent", "Sig Energy", "Signature Energy", true,
        ComputedStats::getSignatureEnergyMaxPercent, ComputedStats.Builder::signatureEnergyMaxPercent),
    SIGNATURE_ENERGY_PER_HIT("signatureEnergyPerHit", "Sig/Hit", "Signature Energy per Hit", false,
        ComputedStats::getSignatureEnergyPerHit, ComputedStats.Builder::signatureEnergyPerHit),

    // Stamina Percent Stats
    MAX_STAMINA_PERCENT("maxStaminaPercent", "Stamina", "Max Stamina", true,
        ComputedStats::getMaxStaminaPercent, ComputedStats.Builder::maxStaminaPercent),
    STAMINA_REGEN_PERCENT("staminaRegenPercent", "Stam Regen", "Stamina Regen", true,
        ComputedStats::getStaminaRegenPercent, ComputedStats.Builder::staminaRegenPercent),
    STAMINA_REGEN_START_DELAY("staminaRegenStartDelay", "Stam Delay", "Stamina Regen Delay", true,
        ComputedStats::getStaminaRegenStartDelay, ComputedStats.Builder::staminaRegenStartDelay),

    // ═══════════════════════════════════════════════════════════════════
    // OCTANT KEYSTONE STATS (enum-only — combat pipeline integration deferred)
    // ═══════════════════════════════════════════════════════════════════

    /** Havoc KS2: Crits on DoT-affected targets deal remaining DoT as burst damage */
    DETONATE_DOT_ON_CRIT("detonateDotOnCrit", "Detonate", "Detonate DoT on Crit", false,
        ComputedStats::getDetonateDotOnCrit, ComputedStats.Builder::detonateDotOnCrit),
    /** Juggernaut KS2: Bonus Physical Damage % per 100 Max HP */
    HP_SCALING_DAMAGE("hpScalingDamage", "HP>DMG", "HP Scaling Damage", false,
        null, null),
    /** Striker KS2: Each consecutive hit within 2s grants stacking damage bonus */
    CONSECUTIVE_HIT_BONUS("consecutiveHitBonus", "Combo DMG", "Consecutive Hit Bonus", false,
        ComputedStats::getConsecutiveHitBonus, ComputedStats.Builder::consecutiveHitBonus),
    /** Warden KS2: Successful blocks deal % of blocked damage back to attacker */
    BLOCK_COUNTER_DAMAGE("blockCounterDamage", "Counter", "Block Counter Damage", false,
        ComputedStats::getBlockCounterDamage, ComputedStats.Builder::blockCounterDamage),
    /** Warlock KS2: X% chance spell/magic damage repeats for 50% as Void damage */
    SPELL_ECHO_CHANCE("spellEchoChance", "Spell Echo", "Spell Echo Chance", false,
        ComputedStats::getSpellEchoChance, ComputedStats.Builder::spellEchoChance),
    /** Lich KS2: DoTs inflicted restore Energy Shield proportional to tick damage */
    SHIELD_REGEN_ON_DOT("shieldRegenOnDot", "ES on DoT", "Shield Regen on DoT", false,
        ComputedStats::getShieldRegenOnDot, ComputedStats.Builder::shieldRegenOnDot),
    /** Tempest KS2: Movement Speed bonus converts to Spell Damage % */
    SPEED_TO_SPELL_POWER("speedToSpellPower", "Spd>Spell", "Speed to Spell Power", false,
        null, null),
    /** Tempest KS1: Attack Speed bonus converts to Spell Damage % */
    ATK_SPEED_TO_SPELL_POWER("atkSpeedToSpellPower", "Atk>Spell", "Attack Speed to Spell Power", false,
        null, null),
    /** Sentinel KS2: Gain element resistance when hit by corresponding ailment */
    IMMUNITY_ON_AILMENT("immunityOnAilment", "Ailment Imm", "Immunity on Ailment", false,
        ComputedStats::getImmunityOnAilment, ComputedStats.Builder::immunityOnAilment),
    /** Sentinel KS1: Convert X% of Evasion into bonus Armor */
    EVASION_TO_ARMOR("evasionToArmor", "Eva>Armor", "Evasion to Armor", false,
        ComputedStats::getEvasionToArmor, ComputedStats.Builder::evasionToArmor),

    // ═══════════════════════════════════════════════════════════════════
    // HEXCODE COMPATIBILITY STATS (overlay-only — active when Hexcode loaded)
    // ═══════════════════════════════════════════════════════════════════

    /** Hexcode: Maximum volatility budget for spell crafting */
    VOLATILITY_MAX("volatilityMax", "Vol Max", "Volatility Max", false,
        ComputedStats::getVolatilityMax, ComputedStats.Builder::volatilityMax),
    /** Hexcode: Amplifies hex construct magnitude */
    MAGIC_POWER("magicPower", "Mag Pwr", "Magic Power", true,
        ComputedStats::getMagicPower, ComputedStats.Builder::magicPower),
    /** Hexcode: Precision bonus for drawn glyphs */
    DRAW_ACCURACY("drawAccuracy", "Draw Acc", "Draw Accuracy", true,
        ComputedStats::getDrawAccuracy, ComputedStats.Builder::drawAccuracy),
    /** Hexcode: Speed of hex deployment */
    CAST_SPEED("castSpeed", "Cast Spd", "Cast Speed", true,
        ComputedStats::getCastSpeed, ComputedStats.Builder::castSpeed),
    /** Hexcode: Number of simultaneously active hex constructs */
    MAGIC_CHARGES("magicCharges", "Charges", "Magic Charges", false,
        stats -> (float) stats.getMagicCharges(),
        (builder, value) -> builder.magicCharges((int) value));

    @FunctionalInterface
    public interface StatGetter {
        float apply(@Nonnull ComputedStats stats);
    }

    @FunctionalInterface
    public interface StatSetter {
        ComputedStats.Builder apply(@Nonnull ComputedStats.Builder builder, float value);
    }

    private final String fieldName;
    private final String shortName;
    private final String displayName;
    private final boolean isPercent;
    @Nullable private final StatGetter getter;
    @Nullable private final StatSetter setter;

    StatType(@Nonnull String fieldName, @Nonnull String shortName, @Nonnull String displayName,
             boolean isPercent,
             @Nullable StatGetter getter, @Nullable StatSetter setter) {
        this.fieldName = fieldName;
        this.shortName = shortName;
        this.displayName = displayName;
        this.isPercent = isPercent;
        this.getter = getter;
        this.setter = setter;
    }

    /**
     * Gets the exact field name in ComputedStats that this stat type maps to.
     * Used for reflection-based or explicit switch-based modifier application.
     */
    @Nonnull
    public String getFieldName() {
        return fieldName;
    }

    /**
     * Gets the compact display name for nameplate and tooltip display.
     * Suitable for tight spaces like 3D nameplates (max ~10 chars).
     *
     * <p>Examples: "HP", "Crit Multi", "Proj DMG", "Fire Pen"
     */
    @Nonnull
    public String getShortName() {
        return shortName;
    }

    /**
     * Gets the full human-readable display name.
     * Suitable for detail pages, synergy descriptions, and tooltips with more room.
     *
     * <p>Does NOT include "Percent" or "Multiplier" suffixes — those come from
     * {@link ModifierType} formatting.
     *
     * <p>Examples: "Max Health", "Critical Multiplier", "Projectile Speed"
     */
    @Nonnull
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Reads this stat's value from a ComputedStats instance.
     * Returns 0f for stats without a getter (fan-out, derived, not-yet-wired).
     */
    public float resolveFrom(@Nonnull ComputedStats stats) {
        return getter != null ? getter.apply(stats) : 0f;
    }

    /**
     * Writes this stat's value to a ComputedStats.Builder.
     * No-op for stats without a setter (fan-out, derived, not-yet-wired).
     */
    public void applyToBuilder(@Nonnull ComputedStats.Builder builder, float value) {
        if (setter != null) {
            setter.apply(builder, value);
        }
    }

    /**
     * Whether this stat has both getter and setter wired to ComputedStats.
     */
    public boolean hasAccessor() {
        return getter != null && setter != null;
    }

    /**
     * Gets the display name for a stat given its enum name string.
     *
     * <p>Used by UI code that stores stat types as strings (e.g., synergy/conditional configs).
     * Falls back to Title Case conversion if the name doesn't match any enum constant.
     *
     * @param enumName The stat type enum name (e.g., "MAX_HEALTH_PERCENT")
     * @return The display name (e.g., "Max Health")
     */
    @Nonnull
    public static String getDisplayNameFor(@Nonnull String enumName) {
        try {
            return StatType.valueOf(enumName).getDisplayName();
        } catch (IllegalArgumentException e) {
            // Fallback: SNAKE_CASE -> Title Case, strip common suffixes
            String cleaned = enumName
                .replaceAll("_PERCENT$", "")
                .replaceAll("_MULTIPLIER$", "");
            String[] parts = cleaned.toLowerCase().split("_");
            StringBuilder sb = new StringBuilder();
            for (String part : parts) {
                if (!part.isEmpty()) {
                    if (!sb.isEmpty()) sb.append(" ");
                    sb.append(Character.toUpperCase(part.charAt(0)));
                    if (part.length() > 1) sb.append(part.substring(1));
                }
            }
            return sb.toString();
        }
    }

    /**
     * Checks if this stat inherently represents a percentage value.
     *
     * <p>Percentage stats should always display with % suffix, even when used
     * with FLAT modifier type (which adds the flat percentage value).
     *
     * <p>Examples:
     * <ul>
     *   <li>FIRE_RESISTANCE: +5 flat -> "+5% Fire Res"</li>
     *   <li>CRITICAL_CHANCE: +2 flat -> "+2% Crit"</li>
     *   <li>PHYSICAL_DAMAGE: +10 flat -> "+10 Phys DMG" (not a percentage)</li>
     * </ul>
     */
    public boolean isInherentlyPercent() {
        return isPercent;
    }
}
