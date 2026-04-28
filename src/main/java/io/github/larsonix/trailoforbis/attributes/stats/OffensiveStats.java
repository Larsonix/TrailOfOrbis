package io.github.larsonix.trailoforbis.attributes.stats;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Container for offensive combat stats (damage, crit, penetration, etc.).
 *
 * <p>This class holds all stats that contribute to dealing damage.
 * Used as part of the composed {@code ComputedStats} structure.
 *
 * <p>Thread-safe: This class is mutable but designed for single-threaded access
 * during entity processing. For concurrent access, external synchronization is needed.
 */
public final class OffensiveStats {

    // ==================== Base Damage ====================
    private float physicalDamage;
    private float physicalDamagePercent;
    private float spellDamage;
    private float spellDamagePercent;

    // ==================== Critical Strikes ====================
    private float criticalChance;
    private float criticalMultiplier;

    // ==================== Combat Modifiers ====================
    private float accuracy;
    private float armorPenetration;
    /**
     * Life steal percentage (0-50 cap). Steal HP FROM enemy: you heal AND enemy takes extra damage.
     * <p>Steal = take resources FROM enemy (enemy loses what you gain).
     * <p>Capped at 50% to prevent double-damage exploit loops.
     */
    private float lifeSteal;
    private float trueDamage;
    /** Flat bonus to charged attack damage. */
    private float chargedAttackDamage;
    /** % bonus to charged attack damage. */
    private float chargedAttackDamagePercent;
    /** % multiplier applied to flat trueDamage (0 flat × 100% = 0). */
    private float trueDamagePercent;
    /** X% of total dealt damage bypasses armor as true damage. */
    private float percentHitAsTrueDamage;

    // ==================== Damage Type Bonuses ====================
    private float meleeDamagePercent;
    private float projectileDamagePercent;

    // ==================== Speed Modifiers ====================
    private float attackSpeedPercent;

    // ==================== Global Damage ====================
    /** Global damage bonus percentage. Applies to ALL damage types (physical + elemental). */
    private float allDamagePercent;
    /** Flat melee damage bonus. Added to physical damage for melee attacks only. */
    private float meleeDamage;

    // ==================== Leech & Steal ====================
    /**
     * Life leech percentage (0-100). Restores HP equal to this % of damage dealt.
     * <p>Leech = gain resources from YOUR damage output (always works).
     */
    private float lifeLeech;

    /**
     * Mana leech percentage (0-100). Restores mana equal to this % of damage dealt.
     * <p>Leech = gain resources from YOUR damage output (always works).
     */
    private float manaLeech;

    // ==================== Conditional ====================
    /** Damage bonus percentage when at low life (≤35% HP). */
    private float damageAtLowLife;

    // ==================== Cost Reduction ====================
    /** Mana cost reduction percentage (0-100). Reduces mana spent on abilities. */
    private float manaCostReduction;

    // ==================== Status Effects ====================
    /** Bonus chance to apply status effects (additive %). */
    private float statusEffectChance;
    /** Bonus duration for applied status effects (additive %). */
    private float statusEffectDuration;

    // ==================== Damage Conversion ====================
    /** Percentage of physical damage converted to fire (PoE-style, total capped at 100%). */
    private float fireConversion;
    /** Percentage of physical damage converted to water (PoE-style, total capped at 100%). */
    private float waterConversion;
    /** Percentage of physical damage converted to lightning (PoE-style, total capped at 100%). */
    private float lightningConversion;
    /** Percentage of physical damage converted to void (PoE-style, total capped at 100%). */
    private float voidConversion;
    /** Percentage of physical damage converted to earth (PoE-style, total capped at 100%). */
    private float earthConversion;
    /** Percentage of physical damage converted to wind (PoE-style, total capped at 100%). */
    private float windConversion;

    // ==================== Ailment Damage ====================
    /** Flat burn (fire DoT) damage per tick. */
    private float burnDamage;
    /** Flat freeze (cold DoT) damage per tick. */
    private float freezeDamage;
    /** Flat shock (lightning DoT) damage per tick. */
    private float shockDamage;
    /** Flat poison (chaos DoT) damage per tick. */
    private float poisonDamage;

    // ==================== Ailment Damage Percent Modifiers ====================
    /** % increased burn (fire DoT) damage. Multiplies flat burnDamage. */
    private float burnDamagePercent;
    /** % increased frost/freeze damage. Multiplies flat freezeDamage. */
    private float frostDamagePercent;
    /** % increased shock effect damage. Multiplies flat shockDamage. */
    private float shockDamagePercent;

    // ==================== Accuracy Modifier ====================
    /** % bonus to accuracy stat. Applied multiplicatively: finalAcc = baseAcc * (1 + pct/100). */
    private float accuracyPercent;

    // ==================== Status Effect Chances ====================
    /** Chance to ignite enemies (0-100%). */
    private float igniteChance;
    /** Chance to freeze enemies (0-100%). */
    private float freezeChance;
    /** Chance to shock enemies (0-100%). */
    private float shockChance;

    // ==================== Generic Damage Modifiers ====================
    /** Generic damage percentage bonus (applies to all damage types). */
    private float damagePercent;
    /** Generic damage multiplier (PoE-style "more" damage). */
    private float damageMultiplier;
    /** Execute damage percentage (bonus damage vs low HP targets). */
    private float executeDamagePercent;
    /** Damage over time percentage bonus. */
    private float dotDamagePercent;
    /** Non-critical damage percentage bonus. */
    private float nonCritDamagePercent;

    // ==================== Conditional Damage Modifiers ====================
    /** Bonus damage percentage vs frozen enemies. */
    private float damageVsFrozenPercent;
    /** Bonus damage percentage vs shocked enemies. */
    private float damageVsShockedPercent;
    /** Bonus damage percentage from own mana. */
    private float damageFromManaPercent;

    // ==================== Mana Steal ====================
    /**
     * Mana steal percentage (0-50 cap). Steal mana FROM enemy: you gain AND enemy loses mana.
     * <p>Steal = take resources FROM enemy (only works if enemy has mana).
     * <p>Capped at 50% to prevent infinite mana exploits.
     */
    private float manaSteal;

    // ==================== Duration Modifiers ====================
    /** Burn duration percentage modifier. */
    private float burnDurationPercent;

    // ==================== Projectile Modifiers ====================
    /** Projectile speed percentage modifier. */
    private float projectileSpeedPercent;
    /** Projectile gravity percentage modifier (negative = floats longer). */
    private float projectileGravityPercent;

    // ==================== Spell Modifiers ====================
    /** Spell penetration (ignores spell resistance). */
    private float spellPenetration;
    /** All elemental damage percentage (applies to fire, cold, lightning, chaos). */
    private float allElementalDamagePercent;

    // ==================== Magic Stats (Hexcode Integration) ====================
    /** Max glyph budget per hex cast. Bridged to Hexcode's Volatility stat. */
    private float volatilityMax;
    /** Direct multiplier on hex effect magnitude. Bridged to Hexcode's Magic_Power stat. */
    private float magicPower;
    /** Max concurrent active spells. Bridged to Hexcode's MagicCharges stat. */
    private int magicCharges;
    /** Bonus to glyph draw quality (ToO-internal, not bridged to EntityStatMap). */
    private float drawAccuracy;
    /** Affects cast decay rate (ToO-internal, not bridged to EntityStatMap). */
    private float castSpeed;

    // ==================== Octant Keystone Stats ====================
    /** % of remaining DoT dealt as burst damage when critting a DoT target. */
    private float detonateDotOnCrit;
    /** % damage bonus per consecutive hit (2s window). */
    private float consecutiveHitBonus;
    /** % chance spell/magic damage repeats for 50% as Void damage. */
    private float spellEchoChance;
    /** % of blocked damage reflected back to the attacker. */
    private float blockCounterDamage;

    // ==================== Weapon Base Damage ====================
    /**
     * Base damage from equipped weapon's implicit stat.
     *
     * <p>This replaces vanilla weapon damage completely. It's set from
     * the weapon's {@code WeaponImplicit.rolledValue()} during stats calculation.
     *
     * <p>When 0, unarmed base damage from config is used as fallback.
     */
    private float weaponBaseDamage;

    /**
     * The vanilla item ID of the equipped weapon (e.g., "Weapon_Daggers_Iron").
     *
     * <p>Used to look up the weapon's attack effectiveness profile from
     * {@code VanillaWeaponDiscovery}. Null if no weapon is equipped.
     */
    private String weaponItemId;

    /**
     * Whether the player is holding RPG-generated gear (regardless of requirements).
     *
     * <p>This is critical for damage path selection: when true, the damage system
     * uses the RPG path even if {@code weaponBaseDamage} is 0 (e.g., when the player
     * doesn't meet attribute requirements). This prevents unequippable RPG weapons
     * from dealing full vanilla damage.
     */
    private boolean holdingRpgGear;

    /**
     * Creates a new OffensiveStats with all values initialized to 0.
     */
    public OffensiveStats() {
        // All fields default to 0
    }

    /**
     * Private constructor for builder.
     */
    private OffensiveStats(Builder builder) {
        this.physicalDamage = builder.physicalDamage;
        this.physicalDamagePercent = builder.physicalDamagePercent;
        this.spellDamage = builder.spellDamage;
        this.spellDamagePercent = builder.spellDamagePercent;
        this.criticalChance = builder.criticalChance;
        this.criticalMultiplier = builder.criticalMultiplier;
        this.accuracy = builder.accuracy;
        this.armorPenetration = builder.armorPenetration;
        this.lifeSteal = builder.lifeSteal;
        this.trueDamage = builder.trueDamage;
        this.chargedAttackDamage = builder.chargedAttackDamage;
        this.chargedAttackDamagePercent = builder.chargedAttackDamagePercent;
        this.trueDamagePercent = builder.trueDamagePercent;
        this.percentHitAsTrueDamage = builder.percentHitAsTrueDamage;
        this.meleeDamagePercent = builder.meleeDamagePercent;
        this.projectileDamagePercent = builder.projectileDamagePercent;
        this.attackSpeedPercent = builder.attackSpeedPercent;
        this.allDamagePercent = builder.allDamagePercent;
        this.meleeDamage = builder.meleeDamage;
        this.lifeLeech = builder.lifeLeech;
        this.manaLeech = builder.manaLeech;
        this.damageAtLowLife = builder.damageAtLowLife;
        this.manaCostReduction = builder.manaCostReduction;
        this.statusEffectChance = builder.statusEffectChance;
        this.statusEffectDuration = builder.statusEffectDuration;
        this.fireConversion = builder.fireConversion;
        this.waterConversion = builder.waterConversion;
        this.lightningConversion = builder.lightningConversion;
        this.voidConversion = builder.voidConversion;
        this.earthConversion = builder.earthConversion;
        this.windConversion = builder.windConversion;
        this.burnDamage = builder.burnDamage;
        this.freezeDamage = builder.freezeDamage;
        this.shockDamage = builder.shockDamage;
        this.poisonDamage = builder.poisonDamage;
        this.igniteChance = builder.igniteChance;
        this.freezeChance = builder.freezeChance;
        this.shockChance = builder.shockChance;
        this.burnDamagePercent = builder.burnDamagePercent;
        this.frostDamagePercent = builder.frostDamagePercent;
        this.shockDamagePercent = builder.shockDamagePercent;
        this.accuracyPercent = builder.accuracyPercent;
        this.damagePercent = builder.damagePercent;
        this.damageMultiplier = builder.damageMultiplier;
        this.executeDamagePercent = builder.executeDamagePercent;
        this.dotDamagePercent = builder.dotDamagePercent;
        this.nonCritDamagePercent = builder.nonCritDamagePercent;
        this.damageVsFrozenPercent = builder.damageVsFrozenPercent;
        this.damageVsShockedPercent = builder.damageVsShockedPercent;
        this.damageFromManaPercent = builder.damageFromManaPercent;
        this.manaSteal = builder.manaSteal;
        this.burnDurationPercent = builder.burnDurationPercent;
        this.projectileSpeedPercent = builder.projectileSpeedPercent;
        this.projectileGravityPercent = builder.projectileGravityPercent;
        this.spellPenetration = builder.spellPenetration;
        this.allElementalDamagePercent = builder.allElementalDamagePercent;
        this.detonateDotOnCrit = builder.detonateDotOnCrit;
        this.consecutiveHitBonus = builder.consecutiveHitBonus;
        this.spellEchoChance = builder.spellEchoChance;
        this.blockCounterDamage = builder.blockCounterDamage;
        this.volatilityMax = builder.volatilityMax;
        this.magicPower = builder.magicPower;
        this.magicCharges = builder.magicCharges;
        this.drawAccuracy = builder.drawAccuracy;
        this.castSpeed = builder.castSpeed;
        this.weaponBaseDamage = builder.weaponBaseDamage;
        this.weaponItemId = builder.weaponItemId;
        this.holdingRpgGear = builder.holdingRpgGear;
    }

    // ==================== Getters ====================

    public float getPhysicalDamage() {
        return physicalDamage;
    }

    public float getPhysicalDamagePercent() {
        return physicalDamagePercent;
    }

    public float getSpellDamage() {
        return spellDamage;
    }

    public float getSpellDamagePercent() {
        return spellDamagePercent;
    }

    public float getCriticalChance() {
        return criticalChance;
    }

    public float getCriticalMultiplier() {
        return criticalMultiplier;
    }

    public float getAccuracy() {
        return accuracy;
    }

    public float getArmorPenetration() {
        return armorPenetration;
    }

    public float getLifeSteal() {
        return lifeSteal;
    }

    public float getTrueDamage() {
        return trueDamage;
    }

    public float getChargedAttackDamage() {
        return chargedAttackDamage;
    }

    public float getChargedAttackDamagePercent() {
        return chargedAttackDamagePercent;
    }

    public float getTrueDamagePercent() {
        return trueDamagePercent;
    }

    public float getPercentHitAsTrueDamage() {
        return percentHitAsTrueDamage;
    }

    public float getMeleeDamagePercent() {
        return meleeDamagePercent;
    }

    public float getProjectileDamagePercent() {
        return projectileDamagePercent;
    }

    public float getAttackSpeedPercent() {
        return attackSpeedPercent;
    }

    public float getAllDamagePercent() {
        return allDamagePercent;
    }

    public float getMeleeDamage() {
        return meleeDamage;
    }

    public float getLifeLeech() {
        return lifeLeech;
    }

    public float getManaLeech() {
        return manaLeech;
    }

    public float getDamageAtLowLife() {
        return damageAtLowLife;
    }

    public float getManaCostReduction() {
        return manaCostReduction;
    }

    public float getStatusEffectChance() {
        return statusEffectChance;
    }

    public float getStatusEffectDuration() {
        return statusEffectDuration;
    }

    public float getFireConversion() {
        return fireConversion;
    }

    public float getWaterConversion() {
        return waterConversion;
    }

    public float getLightningConversion() {
        return lightningConversion;
    }

    public float getVoidConversion() {
        return voidConversion;
    }

    public float getEarthConversion() {
        return earthConversion;
    }

    public float getWindConversion() {
        return windConversion;
    }

    public float getBurnDamage() {
        return burnDamage;
    }

    public float getFreezeDamage() {
        return freezeDamage;
    }

    public float getShockDamage() {
        return shockDamage;
    }

    public float getPoisonDamage() {
        return poisonDamage;
    }

    public float getIgniteChance() {
        return igniteChance;
    }

    public float getFreezeChance() {
        return freezeChance;
    }

    public float getShockChance() {
        return shockChance;
    }

    public float getBurnDamagePercent() {
        return burnDamagePercent;
    }

    public float getFrostDamagePercent() {
        return frostDamagePercent;
    }

    public float getShockDamagePercent() {
        return shockDamagePercent;
    }

    public float getAccuracyPercent() {
        return accuracyPercent;
    }

    public float getDamagePercent() {
        return damagePercent;
    }

    public float getDamageMultiplier() {
        return damageMultiplier;
    }

    public float getExecuteDamagePercent() {
        return executeDamagePercent;
    }

    public float getDotDamagePercent() {
        return dotDamagePercent;
    }

    public float getNonCritDamagePercent() {
        return nonCritDamagePercent;
    }

    public float getDamageVsFrozenPercent() {
        return damageVsFrozenPercent;
    }

    public float getDamageVsShockedPercent() {
        return damageVsShockedPercent;
    }

    public float getDamageFromManaPercent() {
        return damageFromManaPercent;
    }

    public float getManaSteal() {
        return manaSteal;
    }

    public float getBurnDurationPercent() {
        return burnDurationPercent;
    }

    public float getProjectileSpeedPercent() {
        return projectileSpeedPercent;
    }

    public float getProjectileGravityPercent() {
        return projectileGravityPercent;
    }

    public float getSpellPenetration() {
        return spellPenetration;
    }

    public float getAllElementalDamagePercent() {
        return allElementalDamagePercent;
    }

    public float getDetonateDotOnCrit() {
        return detonateDotOnCrit;
    }

    public float getConsecutiveHitBonus() {
        return consecutiveHitBonus;
    }

    public float getSpellEchoChance() {
        return spellEchoChance;
    }

    public float getBlockCounterDamage() {
        return blockCounterDamage;
    }

    // ==================== Magic Stat Getters ====================

    public float getVolatilityMax() {
        return volatilityMax;
    }

    public float getMagicPower() {
        return magicPower;
    }

    public int getMagicCharges() {
        return magicCharges;
    }

    public float getDrawAccuracy() {
        return drawAccuracy;
    }

    public float getCastSpeed() {
        return castSpeed;
    }

    /** @return The weapon's implicit damage value, or 0 if unarmed */
    public float getWeaponBaseDamage() {
        return weaponBaseDamage;
    }

    /**
     * Gets the vanilla item ID of the equipped weapon.
     *
     * <p>Used to look up the weapon's attack effectiveness profile from
     * {@code VanillaWeaponDiscovery}.
     *
     * @return The weapon item ID (e.g., "Weapon_Daggers_Iron"), or null if no weapon
     */
    public String getWeaponItemId() {
        return weaponItemId;
    }

    /**
     * Checks if the player is holding RPG-generated gear.
     *
     * <p>This is true even if the player doesn't meet the weapon's attribute requirements.
     * Used by the damage system to determine whether to use the RPG damage path
     * (which will apply 0 base damage for unequippable gear) vs the vanilla path.
     *
     * @return True if holding RPG gear (regardless of requirements), false for vanilla weapons
     */
    public boolean isHoldingRpgGear() {
        return holdingRpgGear;
    }

    // ==================== Setters ====================

    public void setPhysicalDamage(float physicalDamage) {
        this.physicalDamage = physicalDamage;
    }

    public void setPhysicalDamagePercent(float physicalDamagePercent) {
        this.physicalDamagePercent = physicalDamagePercent;
    }

    public void setSpellDamage(float spellDamage) {
        this.spellDamage = spellDamage;
    }

    public void setSpellDamagePercent(float spellDamagePercent) {
        this.spellDamagePercent = spellDamagePercent;
    }

    public void setCriticalChance(float criticalChance) {
        this.criticalChance = criticalChance;
    }

    public void setCriticalMultiplier(float criticalMultiplier) {
        this.criticalMultiplier = criticalMultiplier;
    }

    public void setAccuracy(float accuracy) {
        this.accuracy = accuracy;
    }

    public void setArmorPenetration(float armorPenetration) {
        this.armorPenetration = armorPenetration;
    }

    public void setLifeSteal(float lifeSteal) {
        // Clamp to 0-50% to prevent double-damage exploit loops
        this.lifeSteal = Math.max(0f, Math.min(50f, lifeSteal));
    }

    public void setTrueDamage(float trueDamage) {
        this.trueDamage = trueDamage;
    }

    public void setChargedAttackDamage(float chargedAttackDamage) {
        this.chargedAttackDamage = chargedAttackDamage;
    }

    public void setChargedAttackDamagePercent(float chargedAttackDamagePercent) {
        this.chargedAttackDamagePercent = chargedAttackDamagePercent;
    }

    public void setTrueDamagePercent(float trueDamagePercent) {
        this.trueDamagePercent = trueDamagePercent;
    }

    public void setPercentHitAsTrueDamage(float percentHitAsTrueDamage) {
        this.percentHitAsTrueDamage = percentHitAsTrueDamage;
    }

    public void setMeleeDamagePercent(float meleeDamagePercent) {
        this.meleeDamagePercent = meleeDamagePercent;
    }

    public void setProjectileDamagePercent(float projectileDamagePercent) {
        this.projectileDamagePercent = projectileDamagePercent;
    }

    public void setAttackSpeedPercent(float attackSpeedPercent) {
        this.attackSpeedPercent = attackSpeedPercent;
    }

    public void setAllDamagePercent(float allDamagePercent) {
        this.allDamagePercent = allDamagePercent;
    }

    public void setMeleeDamage(float meleeDamage) {
        this.meleeDamage = meleeDamage;
    }

    public void setLifeLeech(float lifeLeech) {
        // Clamp to 0-100% range
        this.lifeLeech = Math.max(0f, Math.min(100f, lifeLeech));
    }

    public void setManaLeech(float manaLeech) {
        // Clamp to 0-100% range
        this.manaLeech = Math.max(0f, Math.min(100f, manaLeech));
    }

    public void setDamageAtLowLife(float damageAtLowLife) {
        this.damageAtLowLife = damageAtLowLife;
    }

    public void setManaCostReduction(float manaCostReduction) {
        this.manaCostReduction = manaCostReduction;
    }

    public void setStatusEffectChance(float statusEffectChance) {
        this.statusEffectChance = statusEffectChance;
    }

    public void setStatusEffectDuration(float statusEffectDuration) {
        this.statusEffectDuration = statusEffectDuration;
    }

    public void setFireConversion(float fireConversion) {
        this.fireConversion = fireConversion;
    }

    public void setWaterConversion(float waterConversion) {
        this.waterConversion = waterConversion;
    }

    public void setLightningConversion(float lightningConversion) {
        this.lightningConversion = lightningConversion;
    }

    public void setVoidConversion(float voidConversion) {
        this.voidConversion = voidConversion;
    }

    public void setEarthConversion(float earthConversion) {
        this.earthConversion = earthConversion;
    }

    public void setWindConversion(float windConversion) {
        this.windConversion = windConversion;
    }

    public void setBurnDamage(float burnDamage) {
        this.burnDamage = burnDamage;
    }

    public void setFreezeDamage(float freezeDamage) {
        this.freezeDamage = freezeDamage;
    }

    public void setShockDamage(float shockDamage) {
        this.shockDamage = shockDamage;
    }

    public void setPoisonDamage(float poisonDamage) {
        this.poisonDamage = poisonDamage;
    }

    public void setIgniteChance(float igniteChance) {
        this.igniteChance = igniteChance;
    }

    public void setFreezeChance(float freezeChance) {
        this.freezeChance = freezeChance;
    }

    public void setShockChance(float shockChance) {
        this.shockChance = shockChance;
    }

    public void setBurnDamagePercent(float burnDamagePercent) {
        this.burnDamagePercent = burnDamagePercent;
    }

    public void setFrostDamagePercent(float frostDamagePercent) {
        this.frostDamagePercent = frostDamagePercent;
    }

    public void setShockDamagePercent(float shockDamagePercent) {
        this.shockDamagePercent = shockDamagePercent;
    }

    public void setAccuracyPercent(float accuracyPercent) {
        this.accuracyPercent = accuracyPercent;
    }

    public void setDamagePercent(float damagePercent) {
        this.damagePercent = damagePercent;
    }

    public void setDamageMultiplier(float damageMultiplier) {
        this.damageMultiplier = damageMultiplier;
    }

    public void setExecuteDamagePercent(float executeDamagePercent) {
        this.executeDamagePercent = executeDamagePercent;
    }

    public void setDotDamagePercent(float dotDamagePercent) {
        this.dotDamagePercent = dotDamagePercent;
    }

    public void setNonCritDamagePercent(float nonCritDamagePercent) {
        this.nonCritDamagePercent = nonCritDamagePercent;
    }

    public void setDamageVsFrozenPercent(float damageVsFrozenPercent) {
        this.damageVsFrozenPercent = damageVsFrozenPercent;
    }

    public void setDamageVsShockedPercent(float damageVsShockedPercent) {
        this.damageVsShockedPercent = damageVsShockedPercent;
    }

    public void setDamageFromManaPercent(float damageFromManaPercent) {
        this.damageFromManaPercent = damageFromManaPercent;
    }

    public void setManaSteal(float manaSteal) {
        // Clamp to 0-50% to prevent infinite mana exploits
        this.manaSteal = Math.max(0f, Math.min(50f, manaSteal));
    }

    public void setBurnDurationPercent(float burnDurationPercent) {
        this.burnDurationPercent = burnDurationPercent;
    }

    public void setProjectileSpeedPercent(float projectileSpeedPercent) {
        this.projectileSpeedPercent = projectileSpeedPercent;
    }

    public void setProjectileGravityPercent(float projectileGravityPercent) {
        this.projectileGravityPercent = projectileGravityPercent;
    }

    public void setSpellPenetration(float spellPenetration) {
        this.spellPenetration = spellPenetration;
    }

    public void setAllElementalDamagePercent(float allElementalDamagePercent) {
        this.allElementalDamagePercent = allElementalDamagePercent;
    }

    public void setDetonateDotOnCrit(float detonateDotOnCrit) {
        this.detonateDotOnCrit = detonateDotOnCrit;
    }

    public void setConsecutiveHitBonus(float consecutiveHitBonus) {
        this.consecutiveHitBonus = consecutiveHitBonus;
    }

    public void setSpellEchoChance(float spellEchoChance) {
        this.spellEchoChance = spellEchoChance;
    }

    public void setBlockCounterDamage(float blockCounterDamage) {
        this.blockCounterDamage = blockCounterDamage;
    }

    // ==================== Magic Stat Setters ====================

    public void setVolatilityMax(float volatilityMax) {
        this.volatilityMax = volatilityMax;
    }

    public void setMagicPower(float magicPower) {
        this.magicPower = magicPower;
    }

    public void setMagicCharges(int magicCharges) {
        this.magicCharges = magicCharges;
    }

    public void setDrawAccuracy(float drawAccuracy) {
        this.drawAccuracy = drawAccuracy;
    }

    public void setCastSpeed(float castSpeed) {
        this.castSpeed = castSpeed;
    }

    /** Sets the base damage from equipped weapon's implicit stat. */
    public void setWeaponBaseDamage(float weaponBaseDamage) {
        this.weaponBaseDamage = weaponBaseDamage;
    }

    /** Sets the vanilla item ID of the equipped weapon. */
    public void setWeaponItemId(String weaponItemId) {
        this.weaponItemId = weaponItemId;
    }

    /** Sets whether the player is holding RPG-generated gear. */
    public void setHoldingRpgGear(boolean holdingRpgGear) {
        this.holdingRpgGear = holdingRpgGear;
    }

    // ==================== Utility Methods ====================

    /** Creates a copy of this OffensiveStats. */
    @Nonnull
    public OffensiveStats copy() {
        return toBuilder().build();
    }

    /**
     * Resets all values to 0.
     */
    public void reset() {
        physicalDamage = 0;
        physicalDamagePercent = 0;
        spellDamage = 0;
        spellDamagePercent = 0;
        criticalChance = 0;
        criticalMultiplier = 0;
        accuracy = 0;
        armorPenetration = 0;
        lifeSteal = 0;
        trueDamage = 0;
        chargedAttackDamage = 0;
        chargedAttackDamagePercent = 0;
        trueDamagePercent = 0;
        percentHitAsTrueDamage = 0;
        meleeDamagePercent = 0;
        projectileDamagePercent = 0;
        attackSpeedPercent = 0;
        allDamagePercent = 0;
        meleeDamage = 0;
        lifeLeech = 0;
        manaLeech = 0;
        damageAtLowLife = 0;
        manaCostReduction = 0;
        statusEffectChance = 0;
        statusEffectDuration = 0;
        fireConversion = 0;
        waterConversion = 0;
        lightningConversion = 0;
        voidConversion = 0;
        earthConversion = 0;
        windConversion = 0;
        burnDamage = 0;
        freezeDamage = 0;
        shockDamage = 0;
        poisonDamage = 0;
        igniteChance = 0;
        freezeChance = 0;
        shockChance = 0;
        burnDamagePercent = 0;
        frostDamagePercent = 0;
        shockDamagePercent = 0;
        accuracyPercent = 0;
        damagePercent = 0;
        damageMultiplier = 0;
        executeDamagePercent = 0;
        dotDamagePercent = 0;
        nonCritDamagePercent = 0;
        damageVsFrozenPercent = 0;
        damageVsShockedPercent = 0;
        damageFromManaPercent = 0;
        manaSteal = 0;
        burnDurationPercent = 0;
        projectileSpeedPercent = 0;
        projectileGravityPercent = 0;
        spellPenetration = 0;
        allElementalDamagePercent = 0;
        detonateDotOnCrit = 0;
        consecutiveHitBonus = 0;
        spellEchoChance = 0;
        blockCounterDamage = 0;
        volatilityMax = 0;
        magicPower = 0;
        magicCharges = 0;
        drawAccuracy = 0;
        castSpeed = 0;
        weaponBaseDamage = 0;
        weaponItemId = null;
        holdingRpgGear = false;
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
            .physicalDamage(physicalDamage)
            .physicalDamagePercent(physicalDamagePercent)
            .spellDamage(spellDamage)
            .spellDamagePercent(spellDamagePercent)
            .criticalChance(criticalChance)
            .criticalMultiplier(criticalMultiplier)
            .accuracy(accuracy)
            .armorPenetration(armorPenetration)
            .lifeSteal(lifeSteal)
            .trueDamage(trueDamage)
            .chargedAttackDamage(chargedAttackDamage)
            .chargedAttackDamagePercent(chargedAttackDamagePercent)
            .trueDamagePercent(trueDamagePercent)
            .percentHitAsTrueDamage(percentHitAsTrueDamage)
            .meleeDamagePercent(meleeDamagePercent)
            .projectileDamagePercent(projectileDamagePercent)
            .attackSpeedPercent(attackSpeedPercent)
            .allDamagePercent(allDamagePercent)
            .meleeDamage(meleeDamage)
            .lifeLeech(lifeLeech)
            .manaLeech(manaLeech)
            .damageAtLowLife(damageAtLowLife)
            .manaCostReduction(manaCostReduction)
            .statusEffectChance(statusEffectChance)
            .statusEffectDuration(statusEffectDuration)
            .fireConversion(fireConversion)
            .waterConversion(waterConversion)
            .lightningConversion(lightningConversion)
            .voidConversion(voidConversion)
            .earthConversion(earthConversion)
            .windConversion(windConversion)
            .burnDamage(burnDamage)
            .freezeDamage(freezeDamage)
            .shockDamage(shockDamage)
            .poisonDamage(poisonDamage)
            .igniteChance(igniteChance)
            .freezeChance(freezeChance)
            .shockChance(shockChance)
            .burnDamagePercent(burnDamagePercent)
            .frostDamagePercent(frostDamagePercent)
            .shockDamagePercent(shockDamagePercent)
            .accuracyPercent(accuracyPercent)
            .damagePercent(damagePercent)
            .damageMultiplier(damageMultiplier)
            .executeDamagePercent(executeDamagePercent)
            .dotDamagePercent(dotDamagePercent)
            .nonCritDamagePercent(nonCritDamagePercent)
            .damageVsFrozenPercent(damageVsFrozenPercent)
            .damageVsShockedPercent(damageVsShockedPercent)
            .damageFromManaPercent(damageFromManaPercent)
            .manaSteal(manaSteal)
            .burnDurationPercent(burnDurationPercent)
            .projectileSpeedPercent(projectileSpeedPercent)
            .projectileGravityPercent(projectileGravityPercent)
            .spellPenetration(spellPenetration)
            .allElementalDamagePercent(allElementalDamagePercent)
            .detonateDotOnCrit(detonateDotOnCrit)
            .consecutiveHitBonus(consecutiveHitBonus)
            .spellEchoChance(spellEchoChance)
            .blockCounterDamage(blockCounterDamage)
            .volatilityMax(volatilityMax)
            .magicPower(magicPower)
            .magicCharges(magicCharges)
            .drawAccuracy(drawAccuracy)
            .castSpeed(castSpeed)
            .weaponBaseDamage(weaponBaseDamage)
            .weaponItemId(weaponItemId)
            .holdingRpgGear(holdingRpgGear);
    }

    public static final class Builder {
        private float physicalDamage;
        private float physicalDamagePercent;
        private float spellDamage;
        private float spellDamagePercent;
        private float criticalChance;
        private float criticalMultiplier;
        private float accuracy;
        private float armorPenetration;
        private float lifeSteal;
        private float trueDamage;
        private float chargedAttackDamage;
        private float chargedAttackDamagePercent;
        private float trueDamagePercent;
        private float percentHitAsTrueDamage;
        private float meleeDamagePercent;
        private float projectileDamagePercent;
        private float attackSpeedPercent;
        private float allDamagePercent;
        private float meleeDamage;
        private float lifeLeech;
        private float manaLeech;
        private float damageAtLowLife;
        private float manaCostReduction;
        private float statusEffectChance;
        private float statusEffectDuration;
        private float fireConversion;
        private float waterConversion;
        private float lightningConversion;
        private float voidConversion;
        private float earthConversion;
        private float windConversion;
        private float burnDamage;
        private float freezeDamage;
        private float shockDamage;
        private float poisonDamage;
        private float igniteChance;
        private float freezeChance;
        private float shockChance;
        private float burnDamagePercent;
        private float frostDamagePercent;
        private float shockDamagePercent;
        private float accuracyPercent;
        private float damagePercent;
        private float damageMultiplier;
        private float executeDamagePercent;
        private float dotDamagePercent;
        private float nonCritDamagePercent;
        private float damageVsFrozenPercent;
        private float damageVsShockedPercent;
        private float damageFromManaPercent;
        private float manaSteal;
        private float burnDurationPercent;
        private float projectileSpeedPercent;
        private float projectileGravityPercent;
        private float spellPenetration;
        private float allElementalDamagePercent;
        private float detonateDotOnCrit;
        private float consecutiveHitBonus;
        private float spellEchoChance;
        private float blockCounterDamage;
        private float volatilityMax;
        private float magicPower;
        private int magicCharges;
        private float drawAccuracy;
        private float castSpeed;
        private float weaponBaseDamage;
        private String weaponItemId;
        private boolean holdingRpgGear;

        private Builder() {}

        public Builder physicalDamage(float value) {
            this.physicalDamage = value;
            return this;
        }

        public Builder physicalDamagePercent(float value) {
            this.physicalDamagePercent = value;
            return this;
        }

        public Builder spellDamage(float value) {
            this.spellDamage = value;
            return this;
        }

        public Builder spellDamagePercent(float value) {
            this.spellDamagePercent = value;
            return this;
        }

        public Builder criticalChance(float value) {
            this.criticalChance = value;
            return this;
        }

        public Builder criticalMultiplier(float value) {
            this.criticalMultiplier = value;
            return this;
        }

        public Builder accuracy(float value) {
            this.accuracy = value;
            return this;
        }

        public Builder armorPenetration(float value) {
            this.armorPenetration = value;
            return this;
        }

        public Builder lifeSteal(float value) {
            this.lifeSteal = value;
            return this;
        }

        public Builder trueDamage(float value) {
            this.trueDamage = value;
            return this;
        }

        public Builder chargedAttackDamage(float value) {
            this.chargedAttackDamage = value;
            return this;
        }

        public Builder chargedAttackDamagePercent(float value) {
            this.chargedAttackDamagePercent = value;
            return this;
        }

        public Builder trueDamagePercent(float value) {
            this.trueDamagePercent = value;
            return this;
        }

        public Builder percentHitAsTrueDamage(float value) {
            this.percentHitAsTrueDamage = value;
            return this;
        }

        public Builder meleeDamagePercent(float value) {
            this.meleeDamagePercent = value;
            return this;
        }

        public Builder projectileDamagePercent(float value) {
            this.projectileDamagePercent = value;
            return this;
        }

        public Builder attackSpeedPercent(float value) {
            this.attackSpeedPercent = value;
            return this;
        }

        public Builder allDamagePercent(float value) {
            this.allDamagePercent = value;
            return this;
        }

        public Builder meleeDamage(float value) {
            this.meleeDamage = value;
            return this;
        }

        public Builder lifeLeech(float value) {
            this.lifeLeech = value;
            return this;
        }

        public Builder manaLeech(float value) {
            this.manaLeech = value;
            return this;
        }

        public Builder damageAtLowLife(float value) {
            this.damageAtLowLife = value;
            return this;
        }

        public Builder manaCostReduction(float value) {
            this.manaCostReduction = value;
            return this;
        }

        public Builder statusEffectChance(float value) {
            this.statusEffectChance = value;
            return this;
        }

        public Builder statusEffectDuration(float value) {
            this.statusEffectDuration = value;
            return this;
        }

        public Builder fireConversion(float value) {
            this.fireConversion = value;
            return this;
        }

        public Builder waterConversion(float value) {
            this.waterConversion = value;
            return this;
        }

        public Builder lightningConversion(float value) {
            this.lightningConversion = value;
            return this;
        }

        public Builder voidConversion(float value) {
            this.voidConversion = value;
            return this;
        }

        public Builder earthConversion(float value) {
            this.earthConversion = value;
            return this;
        }

        public Builder windConversion(float value) {
            this.windConversion = value;
            return this;
        }

        public Builder burnDamage(float value) {
            this.burnDamage = value;
            return this;
        }

        public Builder freezeDamage(float value) {
            this.freezeDamage = value;
            return this;
        }

        public Builder shockDamage(float value) {
            this.shockDamage = value;
            return this;
        }

        public Builder poisonDamage(float value) {
            this.poisonDamage = value;
            return this;
        }

        public Builder igniteChance(float value) {
            this.igniteChance = value;
            return this;
        }

        public Builder freezeChance(float value) {
            this.freezeChance = value;
            return this;
        }

        public Builder shockChance(float value) {
            this.shockChance = value;
            return this;
        }

        public Builder burnDamagePercent(float value) {
            this.burnDamagePercent = value;
            return this;
        }

        public Builder frostDamagePercent(float value) {
            this.frostDamagePercent = value;
            return this;
        }

        public Builder shockDamagePercent(float value) {
            this.shockDamagePercent = value;
            return this;
        }

        public Builder accuracyPercent(float value) {
            this.accuracyPercent = value;
            return this;
        }

        public Builder damagePercent(float value) {
            this.damagePercent = value;
            return this;
        }

        public Builder damageMultiplier(float value) {
            this.damageMultiplier = value;
            return this;
        }

        public Builder executeDamagePercent(float value) {
            this.executeDamagePercent = value;
            return this;
        }

        public Builder dotDamagePercent(float value) {
            this.dotDamagePercent = value;
            return this;
        }

        public Builder nonCritDamagePercent(float value) {
            this.nonCritDamagePercent = value;
            return this;
        }

        public Builder damageVsFrozenPercent(float value) {
            this.damageVsFrozenPercent = value;
            return this;
        }

        public Builder damageVsShockedPercent(float value) {
            this.damageVsShockedPercent = value;
            return this;
        }

        public Builder damageFromManaPercent(float value) {
            this.damageFromManaPercent = value;
            return this;
        }

        public Builder manaSteal(float value) {
            this.manaSteal = value;
            return this;
        }

        public Builder burnDurationPercent(float value) {
            this.burnDurationPercent = value;
            return this;
        }

        public Builder projectileSpeedPercent(float value) {
            this.projectileSpeedPercent = value;
            return this;
        }

        public Builder projectileGravityPercent(float value) {
            this.projectileGravityPercent = value;
            return this;
        }

        public Builder spellPenetration(float value) {
            this.spellPenetration = value;
            return this;
        }

        public Builder allElementalDamagePercent(float value) {
            this.allElementalDamagePercent = value;
            return this;
        }

        public Builder detonateDotOnCrit(float value) {
            this.detonateDotOnCrit = value;
            return this;
        }

        public Builder consecutiveHitBonus(float value) {
            this.consecutiveHitBonus = value;
            return this;
        }

        public Builder spellEchoChance(float value) {
            this.spellEchoChance = value;
            return this;
        }

        public Builder blockCounterDamage(float value) {
            this.blockCounterDamage = value;
            return this;
        }

        public Builder volatilityMax(float value) {
            this.volatilityMax = value;
            return this;
        }

        public Builder magicPower(float value) {
            this.magicPower = value;
            return this;
        }

        public Builder magicCharges(int value) {
            this.magicCharges = value;
            return this;
        }

        public Builder drawAccuracy(float value) {
            this.drawAccuracy = value;
            return this;
        }

        public Builder castSpeed(float value) {
            this.castSpeed = value;
            return this;
        }

        public Builder weaponBaseDamage(float value) {
            this.weaponBaseDamage = value;
            return this;
        }

        public Builder weaponItemId(String value) {
            this.weaponItemId = value;
            return this;
        }

        public Builder holdingRpgGear(boolean value) {
            this.holdingRpgGear = value;
            return this;
        }

        public OffensiveStats build() {
            return new OffensiveStats(this);
        }
    }

    // ==================== Object Methods ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OffensiveStats that = (OffensiveStats) o;
        return Float.compare(physicalDamage, that.physicalDamage) == 0
            && Float.compare(physicalDamagePercent, that.physicalDamagePercent) == 0
            && Float.compare(spellDamage, that.spellDamage) == 0
            && Float.compare(spellDamagePercent, that.spellDamagePercent) == 0
            && Float.compare(criticalChance, that.criticalChance) == 0
            && Float.compare(criticalMultiplier, that.criticalMultiplier) == 0
            && Float.compare(accuracy, that.accuracy) == 0
            && Float.compare(armorPenetration, that.armorPenetration) == 0
            && Float.compare(lifeSteal, that.lifeSteal) == 0
            && Float.compare(trueDamage, that.trueDamage) == 0
            && Float.compare(meleeDamagePercent, that.meleeDamagePercent) == 0
            && Float.compare(projectileDamagePercent, that.projectileDamagePercent) == 0
            && Float.compare(attackSpeedPercent, that.attackSpeedPercent) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(physicalDamage, spellDamage, criticalChance, criticalMultiplier, armorPenetration,
            allDamagePercent, meleeDamage, fireConversion, waterConversion);
    }

    @Override
    public String toString() {
        return String.format(
            "OffensiveStats{phys=%.1f(+%.0f%%), spell=%.1f(+%.0f%%), crit=%.1f%%/%.1fx}",
            physicalDamage, physicalDamagePercent * 100,
            spellDamage, spellDamagePercent * 100,
            criticalChance * 100, criticalMultiplier
        );
    }
}
