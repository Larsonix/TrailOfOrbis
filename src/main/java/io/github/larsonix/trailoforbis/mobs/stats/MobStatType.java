package io.github.larsonix.trailoforbis.mobs.stats;

/**
 * Enumeration of all mob stat types used in the RPG scaling system.
 *
 * <p>Each stat defines its config key, display name, category, base/min/max values, and
 * Dirichlet alpha weight. Categories group stats into combat, movement, elemental, defense,
 * AI behavior, and special types.
 */
public enum MobStatType {
    MAX_HEALTH("maxHealth", "Health Points", MobStatType.Category.COMBAT, 100.0, 1.0, 100000.0, 1.0),
    PHYSICAL_DAMAGE("physicalDamage", "Physical Damage", MobStatType.Category.COMBAT, 10.0, 1.0, 10000.0, 1.0),
    ARMOR("armor", "Armor", MobStatType.Category.COMBAT, 0.0, 0.0, 10000.0, 0.5),
    MOVE_SPEED("moveSpeed", "Movement Speed", MobStatType.Category.MOVEMENT, 1.0, 0.3, 3.0, 1.0),
    ATTACK_SPEED("attackSpeed", "Attack Speed", MobStatType.Category.MOVEMENT, 1.0, 0.5, 2.5, 1.0),
    ATTACK_RANGE("attackRange", "Attack Range", MobStatType.Category.MOVEMENT, 2.0, 1.0, 20.0, 1.0),
    ATTACK_COOLDOWN("attackCooldown", "Attack Cooldown", MobStatType.Category.MOVEMENT, 1.0, 0.1, 3.0, 1.0),
    CRITICAL_CHANCE("criticalChance", "Critical Chance", MobStatType.Category.CRITICAL, 5.0, 0.0, 75.0, 1.0),
    CRITICAL_MULTIPLIER("criticalMultiplier", "Critical Multiplier", MobStatType.Category.CRITICAL, 150.0, 100.0, 500.0, 1.0),
    DODGE_CHANCE("dodgeChance", "Dodge Chance", MobStatType.Category.DEFENSE, 0.0, 0.0, 50.0, 1.0),
    BLOCK_CHANCE("blockChance", "Block Chance", MobStatType.Category.DEFENSE, 0.0, 0.0, 40.0, 1.0),
    PARRY_CHANCE("parryChance", "Parry Chance", MobStatType.Category.DEFENSE, 0.0, 0.0, 30.0, 1.0),
    LIFE_STEAL("lifeSteal", "Life Steal", MobStatType.Category.SUSTAIN, 0.0, 0.0, 30.0, 1.0),
    HEALTH_REGEN("healthRegen", "Health Regen", MobStatType.Category.SUSTAIN, 0.0, 0.0, 50.0, 0.1),
    FIRE_DAMAGE("fireDamage", "Fire Damage", MobStatType.Category.ELEMENTAL_DAMAGE, 0.0, 0.0, 5000.0, 1.0),
    WATER_DAMAGE("waterDamage", "Water Damage", MobStatType.Category.ELEMENTAL_DAMAGE, 0.0, 0.0, 5000.0, 1.0),
    LIGHTNING_DAMAGE("lightningDamage", "Lightning Damage", MobStatType.Category.ELEMENTAL_DAMAGE, 0.0, 0.0, 5000.0, 1.0),
    EARTH_DAMAGE("earthDamage", "Earth Damage", MobStatType.Category.ELEMENTAL_DAMAGE, 0.0, 0.0, 5000.0, 1.0),
    WIND_DAMAGE("windDamage", "Wind Damage", MobStatType.Category.ELEMENTAL_DAMAGE, 0.0, 0.0, 5000.0, 1.0),
    VOID_DAMAGE("voidDamage", "Void Damage", MobStatType.Category.ELEMENTAL_DAMAGE, 0.0, 0.0, 5000.0, 1.0),
    FIRE_RESISTANCE("fireResistance", "Fire Resistance", MobStatType.Category.ELEMENTAL_RESISTANCE, 0.0, 0.0, 90.0, 1.0),
    WATER_RESISTANCE("waterResistance", "Water Resistance", MobStatType.Category.ELEMENTAL_RESISTANCE, 0.0, 0.0, 90.0, 1.0),
    LIGHTNING_RESISTANCE("lightningResistance", "Lightning Resistance", MobStatType.Category.ELEMENTAL_RESISTANCE, 0.0, 0.0, 90.0, 1.0),
    EARTH_RESISTANCE("earthResistance", "Earth Resistance", MobStatType.Category.ELEMENTAL_RESISTANCE, 0.0, 0.0, 90.0, 1.0),
    WIND_RESISTANCE("windResistance", "Wind Resistance", MobStatType.Category.ELEMENTAL_RESISTANCE, 0.0, 0.0, 90.0, 1.0),
    VOID_RESISTANCE("voidResistance", "Void Resistance", MobStatType.Category.ELEMENTAL_RESISTANCE, 0.0, 0.0, 90.0, 1.0),
    FIRE_PENETRATION("firePenetration", "Fire Penetration", MobStatType.Category.ELEMENTAL_PENETRATION, 0.0, 0.0, 75.0, 1.0),
    WATER_PENETRATION("waterPenetration", "Water Penetration", MobStatType.Category.ELEMENTAL_PENETRATION, 0.0, 0.0, 75.0, 1.0),
    LIGHTNING_PENETRATION("lightningPenetration", "Lightning Penetration", MobStatType.Category.ELEMENTAL_PENETRATION, 0.0, 0.0, 75.0, 1.0),
    EARTH_PENETRATION("earthPenetration", "Earth Penetration", MobStatType.Category.ELEMENTAL_PENETRATION, 0.0, 0.0, 75.0, 1.0),
    WIND_PENETRATION("windPenetration", "Wind Penetration", MobStatType.Category.ELEMENTAL_PENETRATION, 0.0, 0.0, 75.0, 1.0),
    VOID_PENETRATION("voidPenetration", "Void Penetration", MobStatType.Category.ELEMENTAL_PENETRATION, 0.0, 0.0, 75.0, 1.0),
    // Elemental Increased Damage (additive % bonus - PoE-style "increased")
    FIRE_INCREASED_DAMAGE("fireIncreasedDamage", "Fire Increased Damage", MobStatType.Category.ELEMENTAL_INCREASED_DAMAGE, 0.0, 0.0, 200.0, 1.0),
    WATER_INCREASED_DAMAGE("waterIncreasedDamage", "Water Increased Damage", MobStatType.Category.ELEMENTAL_INCREASED_DAMAGE, 0.0, 0.0, 200.0, 1.0),
    LIGHTNING_INCREASED_DAMAGE("lightningIncreasedDamage", "Lightning Increased Damage", MobStatType.Category.ELEMENTAL_INCREASED_DAMAGE, 0.0, 0.0, 200.0, 1.0),
    EARTH_INCREASED_DAMAGE("earthIncreasedDamage", "Earth Increased Damage", MobStatType.Category.ELEMENTAL_INCREASED_DAMAGE, 0.0, 0.0, 200.0, 1.0),
    WIND_INCREASED_DAMAGE("windIncreasedDamage", "Wind Increased Damage", MobStatType.Category.ELEMENTAL_INCREASED_DAMAGE, 0.0, 0.0, 200.0, 1.0),
    VOID_INCREASED_DAMAGE("voidIncreasedDamage", "Void Increased Damage", MobStatType.Category.ELEMENTAL_INCREASED_DAMAGE, 0.0, 0.0, 200.0, 1.0),
    // Elemental More Damage (multiplicative % bonus - PoE-style "more")
    FIRE_MORE_DAMAGE("fireMoreDamage", "Fire More Damage", MobStatType.Category.ELEMENTAL_MORE_DAMAGE, 0.0, 0.0, 100.0, 1.0),
    WATER_MORE_DAMAGE("waterMoreDamage", "Water More Damage", MobStatType.Category.ELEMENTAL_MORE_DAMAGE, 0.0, 0.0, 100.0, 1.0),
    LIGHTNING_MORE_DAMAGE("lightningMoreDamage", "Lightning More Damage", MobStatType.Category.ELEMENTAL_MORE_DAMAGE, 0.0, 0.0, 100.0, 1.0),
    EARTH_MORE_DAMAGE("earthMoreDamage", "Earth More Damage", MobStatType.Category.ELEMENTAL_MORE_DAMAGE, 0.0, 0.0, 100.0, 1.0),
    WIND_MORE_DAMAGE("windMoreDamage", "Wind More Damage", MobStatType.Category.ELEMENTAL_MORE_DAMAGE, 0.0, 0.0, 100.0, 1.0),
    VOID_MORE_DAMAGE("voidMoreDamage", "Void More Damage", MobStatType.Category.ELEMENTAL_MORE_DAMAGE, 0.0, 0.0, 100.0, 1.0),
    AGGRO_RANGE("aggroRange", "Aggro Range", MobStatType.Category.AI, 16.0, 8.0, 64.0, 1.0),
    REACTION_DELAY("reactionDelay", "Reaction Delay", MobStatType.Category.AI, 1.0, 0.1, 2.0, 1.0),
    CHARGE_TIME("chargeTime", "Charge Time", MobStatType.Category.AI, 1.0, 0.2, 3.0, 1.0),
    CHARGE_DISTANCE("chargeDistance", "Charge Distance", MobStatType.Category.AI, 5.0, 2.0, 20.0, 1.0),
    ARMOR_PENETRATION("armorPenetration", "Armor Penetration", MobStatType.Category.SPECIAL, 0.0, 0.0, 75.0, 1.0),
    TRUE_DAMAGE("trueDamage", "True Damage", MobStatType.Category.SPECIAL, 0.0, 0.0, 1000.0, 1.0),
    ACCURACY("accuracy", "Accuracy", MobStatType.Category.COMBAT, 100.0, 50.0, 300.0, 1.0),
    KNOCKBACK_RESISTANCE("knockbackResistance", "Knockback Resistance", MobStatType.Category.DEFENSE, 0.0, 0.0, 100.0, 1.0);

    public final String configKey;
    public final String displayName;
    public final Category category;
    public final double baseValue;
    public final double minValue;
    public final double maxValue;
    public final double alphaWeight;

    MobStatType(String configKey, String displayName, Category category, double baseValue, double minValue, double maxValue, double alphaWeight) {
        this.configKey = configKey;
        this.displayName = displayName;
        this.category = category;
        this.baseValue = baseValue;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.alphaWeight = alphaWeight;
    }

    public enum Category {
        COMBAT,
        MOVEMENT,
        CRITICAL,
        DEFENSE,
        SUSTAIN,
        ELEMENTAL_DAMAGE,
        ELEMENTAL_RESISTANCE,
        ELEMENTAL_PENETRATION,
        ELEMENTAL_INCREASED_DAMAGE,
        ELEMENTAL_MORE_DAMAGE,
        AI,
        SPECIAL
    }
}
