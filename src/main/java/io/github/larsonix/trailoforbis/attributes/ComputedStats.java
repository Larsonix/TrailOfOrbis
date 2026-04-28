package io.github.larsonix.trailoforbis.attributes;

import io.github.larsonix.trailoforbis.attributes.stats.DefensiveStats;
import io.github.larsonix.trailoforbis.attributes.stats.MovementStats;
import io.github.larsonix.trailoforbis.attributes.stats.OffensiveStats;
import io.github.larsonix.trailoforbis.attributes.stats.ResourceStats;
import io.github.larsonix.trailoforbis.elemental.ElementType;
import io.github.larsonix.trailoforbis.elemental.ElementalStats;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Mutable computed stats container using composition.
 *
 * <p>ComputedStats represents the final calculated stats that combat/movement systems use.
 * These are derived from attributes (STR/DEX/INT/VIT) plus equipment, skills, and buffs.
 *
 * <p><b>CRITICAL:</b> Combat and movement systems must read from ComputedStats, never
 * from raw attribute values directly. This enables future expansion without refactoring.
 *
 * <p>This class composes 5 specialized stat containers:
 * <ul>
 *   <li>{@link ResourceStats} - Health, mana, stamina pools and regeneration</li>
 *   <li>{@link OffensiveStats} - Damage, crit, penetration, attack speed</li>
 *   <li>{@link DefensiveStats} - Armor, evasion, blocking, resistances</li>
 *   <li>{@link MovementStats} - Movement speed, jump, sprint bonuses</li>
 *   <li>{@link ElementalStats} - Elemental damage, resistances, penetration</li>
 * </ul>
 *
 * <p>All top-level getter/setter methods delegate to the appropriate sub-object,
 * maintaining full backward compatibility with existing code.
 *
 * <p>Example usage:
 * <pre>
 * ComputedStats stats = ComputedStats.builder()
 *     .maxHealth(150f)
 *     .criticalChance(5f)
 *     .build();
 *
 * // Access sub-objects directly for grouped operations
 * stats.getResource().setMaxHealth(200f);
 * stats.getOffensive().setCriticalChance(0.1f);
 *
 * // Or use delegate methods (backward compatible)
 * stats.setMaxHealth(200);
 * </pre>
 */
public final class ComputedStats {

    // ==================== Composed Sub-Objects ====================
    private final ResourceStats resource;
    private final OffensiveStats offensive;
    private final DefensiveStats defensive;
    private final MovementStats movement;
    private final ElementalStats elemental;

    // ==================== Utility Stats ====================
    /** Percentage bonus to XP gained (e.g., 20 = +20% XP). */
    private float experienceGainPercent;

    /** Mind over Matter: percentage of damage taken from mana instead of life (PoE-style). */
    private float manaAsDamageBuffer;

    /**
     * Flag indicating these stats originate from a mob (via MobStats.toComputedStats()).
     *
     * <p>When true, the damage system should:
     * <ul>
     *   <li>Skip flat damage addition (already baked into weighted formula)</li>
     *   <li>Use mob-specific damage scaling instead of player gear/attribute bonuses</li>
     * </ul>
     */
    private final boolean mobStats;

    /**
     * Creates a ComputedStats with all default values (0.0f).
     */
    public ComputedStats() {
        this.resource = new ResourceStats();
        this.offensive = new OffensiveStats();
        this.defensive = new DefensiveStats();
        this.movement = new MovementStats();
        this.elemental = new ElementalStats();
        this.mobStats = false;
    }

    private ComputedStats(Builder builder) {
        this.resource = builder.resource.build();
        this.offensive = builder.offensive.build();
        this.defensive = builder.defensive.build();
        this.movement = builder.movement.build();
        this.elemental = builder.elemental.copy();
        this.experienceGainPercent = builder.experienceGainPercent;
        this.manaAsDamageBuffer = builder.manaAsDamageBuffer;
        this.mobStats = builder.mobStats;
    }

    // ==================== Sub-Object Accessors ====================

    /** Gets the resource stats sub-object (health, mana, stamina, etc.). */
    @Nonnull
    public ResourceStats getResource() {
        return resource;
    }

    /** Gets the offensive stats sub-object (damage, crit, etc.). */
    @Nonnull
    public OffensiveStats getOffensive() {
        return offensive;
    }

    /** Gets the defensive stats sub-object (armor, evasion, etc.). */
    @Nonnull
    public DefensiveStats getDefensive() {
        return defensive;
    }

    /** Gets the movement stats sub-object (speed bonuses, etc.). */
    @Nonnull
    public MovementStats getMovement() {
        return movement;
    }

    /** Gets the elemental stats sub-object (elemental damage, resistances, etc.). */
    @Nonnull
    public ElementalStats getElemental() {
        return elemental;
    }

    // ==================== Utility Stat Accessors ====================

    /** Returns the experience gain percentage bonus (e.g., 20 = +20% XP). */
    public float getExperienceGainPercent() {
        return experienceGainPercent;
    }

    /** Sets the experience gain percentage bonus. */
    public void setExperienceGainPercent(float experienceGainPercent) {
        this.experienceGainPercent = experienceGainPercent;
    }

    /** Returns the Mind over Matter percentage (damage taken from mana). */
    public float getManaAsDamageBuffer() {
        return manaAsDamageBuffer;
    }

    /** Sets the Mind over Matter percentage. */
    public void setManaAsDamageBuffer(double value) {
        this.manaAsDamageBuffer = (float) value;
    }

    /**
     * Returns whether these stats originate from a mob.
     *
     * <p>When true, indicates these stats were created via {@code MobStats.toComputedStats()}
     * and the damage system should skip flat damage addition (already baked into weighted formula).
     *
     * @return true if these are mob-origin stats
     */
    public boolean isMobStats() {
        return mobStats;
    }

    // ==================== Resource Delegate Getters ====================

    public float getMaxHealth() {
        return resource.getMaxHealth();
    }

    public float getMaxMana() {
        return resource.getMaxMana();
    }

    public float getMaxStamina() {
        return resource.getMaxStamina();
    }

    public float getMaxOxygen() {
        return resource.getMaxOxygen();
    }

    public float getMaxSignatureEnergy() {
        return resource.getMaxSignatureEnergy();
    }

    public float getHealthRegen() {
        return resource.getHealthRegen();
    }

    public float getManaRegen() {
        return resource.getManaRegen();
    }

    public float getStaminaRegen() {
        return resource.getStaminaRegen();
    }

    public float getOxygenRegen() {
        return resource.getOxygenRegen();
    }

    public float getSignatureEnergyRegen() {
        return resource.getSignatureEnergyRegen();
    }

    public float getHealthRegenPercent() {
        return resource.getHealthRegenPercent();
    }

    // New resource field getters
    public float getMaxHealthPercent() {
        return resource.getMaxHealthPercent();
    }

    public float getMaxManaPercent() {
        return resource.getMaxManaPercent();
    }

    public float getManaCostPercent() {
        return resource.getManaCostPercent();
    }

    public float getMaxStaminaPercent() {
        return resource.getMaxStaminaPercent();
    }

    public float getStaminaRegenPercent() {
        return resource.getStaminaRegenPercent();
    }

    public float getStaminaRegenStartDelay() {
        return resource.getStaminaRegenStartDelay();
    }

    public float getSignatureEnergyMaxPercent() {
        return resource.getSignatureEnergyMaxPercent();
    }

    public float getSignatureEnergyPerHit() {
        return resource.getSignatureEnergyPerHit();
    }

    public float getManaOnKill() {
        return resource.getManaOnKill();
    }

    // ==================== Offensive Delegate Getters ====================

    public float getPhysicalDamage() {
        return offensive.getPhysicalDamage();
    }

    public float getSpellDamage() {
        return offensive.getSpellDamage();
    }

    public float getPhysicalDamagePercent() {
        return offensive.getPhysicalDamagePercent();
    }

    public float getSpellDamagePercent() {
        return offensive.getSpellDamagePercent();
    }

    public float getCriticalChance() {
        return offensive.getCriticalChance();
    }

    public float getCriticalMultiplier() {
        return offensive.getCriticalMultiplier();
    }

    public float getMeleeDamagePercent() {
        return offensive.getMeleeDamagePercent();
    }

    public float getProjectileDamagePercent() {
        return offensive.getProjectileDamagePercent();
    }

    public float getAttackSpeedPercent() {
        return offensive.getAttackSpeedPercent();
    }

    public float getAccuracy() {
        return offensive.getAccuracy();
    }

    public float getArmorPenetration() {
        return offensive.getArmorPenetration();
    }

    public float getLifeSteal() {
        return offensive.getLifeSteal();
    }

    public float getTrueDamage() {
        return offensive.getTrueDamage();
    }

    public float getChargedAttackDamage() {
        return offensive.getChargedAttackDamage();
    }

    public float getChargedAttackDamagePercent() {
        return offensive.getChargedAttackDamagePercent();
    }

    public float getTrueDamagePercent() {
        return offensive.getTrueDamagePercent();
    }

    public float getPercentHitAsTrueDamage() {
        return offensive.getPercentHitAsTrueDamage();
    }

    public float getAllDamagePercent() {
        return offensive.getAllDamagePercent();
    }

    public float getMeleeDamage() {
        return offensive.getMeleeDamage();
    }

    public float getLifeLeech() {
        return offensive.getLifeLeech();
    }

    public float getManaLeech() {
        return offensive.getManaLeech();
    }

    public float getDamageAtLowLife() {
        return offensive.getDamageAtLowLife();
    }

    public float getManaCostReduction() {
        return offensive.getManaCostReduction();
    }

    public float getStatusEffectChance() {
        return offensive.getStatusEffectChance();
    }

    public float getStatusEffectDuration() {
        return offensive.getStatusEffectDuration();
    }

    public float getFireConversion() {
        return offensive.getFireConversion();
    }

    public float getWaterConversion() {
        return offensive.getWaterConversion();
    }

    public float getLightningConversion() {
        return offensive.getLightningConversion();
    }

    public float getVoidConversion() {
        return offensive.getVoidConversion();
    }

    public float getEarthConversion() {
        return offensive.getEarthConversion();
    }

    public float getWindConversion() {
        return offensive.getWindConversion();
    }

    public float getBurnDamage() {
        return offensive.getBurnDamage();
    }

    public float getFreezeDamage() {
        return offensive.getFreezeDamage();
    }

    public float getShockDamage() {
        return offensive.getShockDamage();
    }

    public float getPoisonDamage() {
        return offensive.getPoisonDamage();
    }

    // New offensive field getters
    public float getIgniteChance() {
        return offensive.getIgniteChance();
    }

    public float getFreezeChance() {
        return offensive.getFreezeChance();
    }

    public float getShockChance() {
        return offensive.getShockChance();
    }

    public float getDamagePercent() {
        return offensive.getDamagePercent();
    }

    public float getDamageMultiplier() {
        return offensive.getDamageMultiplier();
    }

    public float getExecuteDamagePercent() {
        return offensive.getExecuteDamagePercent();
    }

    public float getDotDamagePercent() {
        return offensive.getDotDamagePercent();
    }

    public float getNonCritDamagePercent() {
        return offensive.getNonCritDamagePercent();
    }

    public float getDamageVsFrozenPercent() {
        return offensive.getDamageVsFrozenPercent();
    }

    public float getDamageVsShockedPercent() {
        return offensive.getDamageVsShockedPercent();
    }

    public float getDamageFromManaPercent() {
        return offensive.getDamageFromManaPercent();
    }

    public float getManaSteal() {
        return offensive.getManaSteal();
    }

    public float getBurnDurationPercent() {
        return offensive.getBurnDurationPercent();
    }

    public float getProjectileSpeedPercent() {
        return offensive.getProjectileSpeedPercent();
    }

    public float getProjectileGravityPercent() {
        return offensive.getProjectileGravityPercent();
    }

    public float getSpellPenetration() {
        return offensive.getSpellPenetration();
    }

    public float getAllElementalDamagePercent() {
        return offensive.getAllElementalDamagePercent();
    }

    // Octant keystone offensive delegates
    public float getDetonateDotOnCrit() {
        return offensive.getDetonateDotOnCrit();
    }

    public float getConsecutiveHitBonus() {
        return offensive.getConsecutiveHitBonus();
    }

    public float getSpellEchoChance() {
        return offensive.getSpellEchoChance();
    }

    public float getBlockCounterDamage() {
        return offensive.getBlockCounterDamage();
    }

    public float getBurnDamagePercent() {
        return offensive.getBurnDamagePercent();
    }

    public float getFrostDamagePercent() {
        return offensive.getFrostDamagePercent();
    }

    public float getShockDamagePercent() {
        return offensive.getShockDamagePercent();
    }

    public float getAccuracyPercent() {
        return offensive.getAccuracyPercent();
    }

    // ==================== Magic Stat Delegate Getters ====================

    public float getVolatilityMax() {
        return offensive.getVolatilityMax();
    }

    public float getMagicPower() {
        return offensive.getMagicPower();
    }

    public int getMagicCharges() {
        return offensive.getMagicCharges();
    }

    public float getDrawAccuracy() {
        return offensive.getDrawAccuracy();
    }

    public float getCastSpeed() {
        return offensive.getCastSpeed();
    }

    /**
     * Gets the base damage from equipped weapon's implicit stat.
     *
     * <p>This replaces vanilla weapon damage completely. The value comes from
     * the weapon's {@code WeaponImplicit.rolledValue()}.
     *
     * @return The weapon's implicit damage value, or 0 if unarmed
     */
    public float getWeaponBaseDamage() {
        return offensive.getWeaponBaseDamage();
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
        return offensive.getWeaponItemId();
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
        return offensive.isHoldingRpgGear();
    }

    // ==================== Defensive Delegate Getters ====================

    public float getArmor() {
        return defensive.getArmor();
    }

    public float getArmorPercent() {
        return defensive.getArmorPercent();
    }

    public float getEvasion() {
        return defensive.getEvasion();
    }

    public float getEnergyShield() {
        return defensive.getEnergyShield();
    }

    public float getPassiveBlockChance() {
        return defensive.getPassiveBlockChance();
    }

    public float getParryChance() {
        return defensive.getParryChance();
    }

    public float getKnockbackResistance() {
        return defensive.getKnockbackResistance();
    }

    public float getFallDamageReduction() {
        return defensive.getFallDamageReduction();
    }

    /**
     * Gets crit nullify chance percentage (0-100).
     * This is the % chance to turn an enemy's critical hit into a normal hit.
     * At 100%, all incoming critical hits are guaranteed to be nullified.
     */
    public float getCritNullifyChance() {
        return defensive.getCritNullifyChance();
    }

    public float getPhysicalResistance() {
        return defensive.getPhysicalResistance();
    }

    public float getDodgeChance() {
        return defensive.getDodgeChance();
    }

    public float getBurnThreshold() {
        return defensive.getBurnThreshold();
    }

    public float getFreezeThreshold() {
        return defensive.getFreezeThreshold();
    }

    public float getShockThreshold() {
        return defensive.getShockThreshold();
    }

    // New defensive field getters
    public float getBlockHealPercent() {
        return defensive.getBlockHealPercent();
    }

    public float getBlockRecoveryPercent() {
        return defensive.getBlockRecoveryPercent();
    }

    public float getShieldEffectivenessPercent() {
        return defensive.getShieldEffectivenessPercent();
    }

    public float getHealthRecoveryPercent() {
        return defensive.getHealthRecoveryPercent();
    }

    public float getDamageTakenPercent() {
        return defensive.getDamageTakenPercent();
    }

    public float getDamageWhenHitPercent() {
        return defensive.getDamageWhenHitPercent();
    }

    public float getCriticalReduction() {
        return defensive.getCriticalReduction();
    }

    public float getBlockChance() {
        return defensive.getBlockChance();
    }

    public float getBlockDamageReduction() {
        return defensive.getBlockDamageReduction();
    }

    public float getStaminaDrainReduction() {
        return defensive.getStaminaDrainReduction();
    }

    /**
     * Gets the flat thorns damage dealt back to attackers when hit.
     */
    public float getThornsDamage() {
        return defensive.getThornsDamage();
    }

    /**
     * Gets the percentage bonus to thorns damage.
     */
    public float getThornsDamagePercent() {
        return defensive.getThornsDamagePercent();
    }

    /**
     * Gets the percentage of damage taken that is reflected back to the attacker.
     */
    public float getReflectDamagePercent() {
        return defensive.getReflectDamagePercent();
    }

    // Octant keystone defensive delegates
    public float getShieldRegenOnDot() {
        return defensive.getShieldRegenOnDot();
    }

    public float getImmunityOnAilment() {
        return defensive.getImmunityOnAilment();
    }

    public float getEvasionToArmor() {
        return defensive.getEvasionToArmor();
    }

    // ==================== Movement Delegate Getters ====================

    public float getMovementSpeedPercent() {
        return movement.getMovementSpeedPercent();
    }

    public float getJumpForceBonus() {
        return movement.getJumpForceBonus();
    }

    public float getJumpForcePercent() {
        return movement.getJumpForcePercent();
    }

    public float getSprintSpeedBonus() {
        return movement.getSprintSpeedBonus();
    }

    public float getClimbSpeedBonus() {
        return movement.getClimbSpeedBonus();
    }

    public float getWalkSpeedPercent() {
        return movement.getWalkSpeedPercent();
    }

    public float getCrouchSpeedPercent() {
        return movement.getCrouchSpeedPercent();
    }

    public float getRunSpeedPercent() {
        return movement.getRunSpeedPercent();
    }

    // ==================== Elemental Delegate Getters ====================

    public float getFireDamage() {
        return (float) elemental.getFlatDamage(ElementType.FIRE);
    }

    public float getWaterDamage() {
        return (float) elemental.getFlatDamage(ElementType.WATER);
    }

    public float getLightningDamage() {
        return (float) elemental.getFlatDamage(ElementType.LIGHTNING);
    }

    public float getVoidDamage() {
        return (float) elemental.getFlatDamage(ElementType.VOID);
    }

    public float getFireDamagePercent() {
        return (float) elemental.getPercentDamage(ElementType.FIRE);
    }

    public float getWaterDamagePercent() {
        return (float) elemental.getPercentDamage(ElementType.WATER);
    }

    public float getLightningDamagePercent() {
        return (float) elemental.getPercentDamage(ElementType.LIGHTNING);
    }

    public float getVoidDamagePercent() {
        return (float) elemental.getPercentDamage(ElementType.VOID);
    }

    public float getFireDamageMultiplier() {
        return (float) elemental.getMultiplierDamage(ElementType.FIRE);
    }

    public float getWaterDamageMultiplier() {
        return (float) elemental.getMultiplierDamage(ElementType.WATER);
    }

    public float getLightningDamageMultiplier() {
        return (float) elemental.getMultiplierDamage(ElementType.LIGHTNING);
    }

    public float getVoidDamageMultiplier() {
        return (float) elemental.getMultiplierDamage(ElementType.VOID);
    }

    public float getFireResistance() {
        return (float) elemental.getResistance(ElementType.FIRE);
    }

    public float getWaterResistance() {
        return (float) elemental.getResistance(ElementType.WATER);
    }

    public float getLightningResistance() {
        return (float) elemental.getResistance(ElementType.LIGHTNING);
    }

    public float getVoidResistance() {
        return (float) elemental.getResistance(ElementType.VOID);
    }

    public float getFirePenetration() {
        return (float) elemental.getPenetration(ElementType.FIRE);
    }

    public float getWaterPenetration() {
        return (float) elemental.getPenetration(ElementType.WATER);
    }

    public float getLightningPenetration() {
        return (float) elemental.getPenetration(ElementType.LIGHTNING);
    }

    public float getVoidPenetration() {
        return (float) elemental.getPenetration(ElementType.VOID);
    }

    // ==================== Earth Elemental Delegate Getters ====================

    public float getEarthDamage() {
        return (float) elemental.getFlatDamage(ElementType.EARTH);
    }

    public float getEarthDamagePercent() {
        return (float) elemental.getPercentDamage(ElementType.EARTH);
    }

    public float getEarthDamageMultiplier() {
        return (float) elemental.getMultiplierDamage(ElementType.EARTH);
    }

    public float getEarthResistance() {
        return (float) elemental.getResistance(ElementType.EARTH);
    }

    public float getEarthPenetration() {
        return (float) elemental.getPenetration(ElementType.EARTH);
    }

    // ==================== Wind Elemental Delegate Getters ====================

    public float getWindDamage() {
        return (float) elemental.getFlatDamage(ElementType.WIND);
    }

    public float getWindDamagePercent() {
        return (float) elemental.getPercentDamage(ElementType.WIND);
    }

    public float getWindDamageMultiplier() {
        return (float) elemental.getMultiplierDamage(ElementType.WIND);
    }

    public float getWindResistance() {
        return (float) elemental.getResistance(ElementType.WIND);
    }

    public float getWindPenetration() {
        return (float) elemental.getPenetration(ElementType.WIND);
    }

    // ==================== Resource Percent Consolidation ====================

    /**
     * Folds percent resource modifiers into actual resource values.
     *
     * <p>The attribute system and skill tree store percent bonuses as separate fields
     * (e.g., {@code maxHealthPercent = 25.0} means +25% max HP). This method applies
     * those percent bonuses to the base resource values so all consumers (ECS, combat,
     * UI) see the correct effective values.
     *
     * <p>The percent fields are preserved for display purposes (stat breakdowns, tooltips).
     *
     * <p><b>Call timing:</b> After skill tree modifiers, before gear bonuses — so gear's
     * flat/percent bonuses correctly layer on top of the percent-adjusted base.
     */
    public void consolidateResourcePercents() {
        float healthPct = getMaxHealthPercent();
        if (healthPct != 0f) {
            setMaxHealth(getMaxHealth() * (1f + healthPct / 100f));
        }

        float manaPct = getMaxManaPercent();
        if (manaPct != 0f) {
            setMaxMana(getMaxMana() * (1f + manaPct / 100f));
        }

        float staminaPct = getMaxStaminaPercent();
        if (staminaPct != 0f) {
            setMaxStamina(getMaxStamina() * (1f + staminaPct / 100f));
        }

        float sigEnergyPct = getSignatureEnergyMaxPercent();
        if (sigEnergyPct != 0f) {
            setMaxSignatureEnergy(getMaxSignatureEnergy() * (1f + sigEnergyPct / 100f));
        }
    }

    // ==================== Resource Delegate Setters ====================

    public void setMaxHealth(double value) {
        resource.setMaxHealth((float) value);
    }

    public void setMaxMana(double value) {
        resource.setMaxMana((float) value);
    }

    public void setMaxStamina(double value) {
        resource.setMaxStamina((float) value);
    }

    public void setMaxOxygen(double value) {
        resource.setMaxOxygen((float) value);
    }

    public void setMaxSignatureEnergy(double value) {
        resource.setMaxSignatureEnergy((float) value);
    }

    public void setHealthRegen(double value) {
        resource.setHealthRegen((float) value);
    }

    public void setManaRegen(double value) {
        resource.setManaRegen((float) value);
    }

    public void setStaminaRegen(double value) {
        resource.setStaminaRegen((float) value);
    }

    public void setOxygenRegen(double value) {
        resource.setOxygenRegen((float) value);
    }

    public void setSignatureEnergyRegen(double value) {
        resource.setSignatureEnergyRegen((float) value);
    }

    public void setHealthRegenPercent(double value) {
        resource.setHealthRegenPercent((float) value);
    }

    public void setSignatureEnergyMaxPercent(double value) {
        resource.setSignatureEnergyMaxPercent((float) value);
    }

    public void setSignatureEnergyPerHit(double value) {
        resource.setSignatureEnergyPerHit((float) value);
    }

    public void setManaOnKill(double value) {
        resource.setManaOnKill((float) value);
    }

    public void setMaxStaminaPercent(double value) {
        resource.setMaxStaminaPercent((float) value);
    }

    public void setStaminaRegenPercent(double value) {
        resource.setStaminaRegenPercent((float) value);
    }

    public void setStaminaRegenStartDelay(double value) {
        resource.setStaminaRegenStartDelay((float) value);
    }

    // ==================== Offensive Delegate Setters ====================

    public void setPhysicalDamage(double value) {
        offensive.setPhysicalDamage((float) value);
    }

    public void setSpellDamage(double value) {
        offensive.setSpellDamage((float) value);
    }

    public void setPhysicalDamagePercent(double value) {
        offensive.setPhysicalDamagePercent((float) value);
    }

    public void setSpellDamagePercent(double value) {
        offensive.setSpellDamagePercent((float) value);
    }

    public void setCritChance(double value) {
        offensive.setCriticalChance((float) value);
    }

    public void setCritMultiplier(double value) {
        offensive.setCriticalMultiplier((float) value);
    }

    public void setMeleeDamagePercent(double value) {
        offensive.setMeleeDamagePercent((float) value);
    }

    public void setProjectileDamagePercent(double value) {
        offensive.setProjectileDamagePercent((float) value);
    }

    public void setAttackSpeedPercent(double value) {
        offensive.setAttackSpeedPercent((float) value);
    }

    public void setAccuracy(double value) {
        offensive.setAccuracy((float) value);
    }

    public void setArmorPenetration(double value) {
        offensive.setArmorPenetration((float) value);
    }

    public void setLifeSteal(double value) {
        offensive.setLifeSteal((float) value);
    }

    public void setTrueDamage(double value) {
        offensive.setTrueDamage((float) value);
    }

    public void setChargedAttackDamage(double value) {
        offensive.setChargedAttackDamage((float) value);
    }

    public void setChargedAttackDamagePercent(double value) {
        offensive.setChargedAttackDamagePercent((float) value);
    }

    public void setTrueDamagePercent(double value) {
        offensive.setTrueDamagePercent((float) value);
    }

    public void setPercentHitAsTrueDamage(double value) {
        offensive.setPercentHitAsTrueDamage((float) value);
    }

    public void setAllDamagePercent(double value) {
        offensive.setAllDamagePercent((float) value);
    }

    public void setMeleeDamage(double value) {
        offensive.setMeleeDamage((float) value);
    }

    public void setLifeLeech(double value) {
        offensive.setLifeLeech((float) value);
    }

    public void setManaLeech(double value) {
        offensive.setManaLeech((float) value);
    }

    public void setDamageAtLowLife(double value) {
        offensive.setDamageAtLowLife((float) value);
    }

    public void setManaCostReduction(double value) {
        offensive.setManaCostReduction((float) value);
    }

    public void setStatusEffectChance(double value) {
        offensive.setStatusEffectChance((float) value);
    }

    public void setStatusEffectDuration(double value) {
        offensive.setStatusEffectDuration((float) value);
    }

    public void setFireConversion(double value) {
        offensive.setFireConversion((float) value);
    }

    public void setWaterConversion(double value) {
        offensive.setWaterConversion((float) value);
    }

    public void setLightningConversion(double value) {
        offensive.setLightningConversion((float) value);
    }

    public void setVoidConversion(double value) {
        offensive.setVoidConversion((float) value);
    }

    public void setEarthConversion(double value) {
        offensive.setEarthConversion((float) value);
    }

    public void setWindConversion(double value) {
        offensive.setWindConversion((float) value);
    }

    public void setBurnDamage(double value) {
        offensive.setBurnDamage((float) value);
    }

    public void setFreezeDamage(double value) {
        offensive.setFreezeDamage((float) value);
    }

    public void setShockDamage(double value) {
        offensive.setShockDamage((float) value);
    }

    public void setPoisonDamage(double value) {
        offensive.setPoisonDamage((float) value);
    }

    // New offensive field setters
    public void setIgniteChance(double value) {
        offensive.setIgniteChance((float) value);
    }

    public void setFreezeChance(double value) {
        offensive.setFreezeChance((float) value);
    }

    public void setShockChance(double value) {
        offensive.setShockChance((float) value);
    }

    public void setDotDamagePercent(double value) {
        offensive.setDotDamagePercent((float) value);
    }

    public void setExecuteDamagePercent(double value) {
        offensive.setExecuteDamagePercent((float) value);
    }

    public void setDamageVsFrozenPercent(double value) {
        offensive.setDamageVsFrozenPercent((float) value);
    }

    public void setDamageVsShockedPercent(double value) {
        offensive.setDamageVsShockedPercent((float) value);
    }

    public void setManaSteal(double value) {
        offensive.setManaSteal((float) value);
    }

    public void setProjectileSpeedPercent(double value) {
        offensive.setProjectileSpeedPercent((float) value);
    }

    public void setProjectileGravityPercent(double value) {
        offensive.setProjectileGravityPercent((float) value);
    }

    public void setSpellPenetration(double value) {
        offensive.setSpellPenetration((float) value);
    }

    public void setAllElementalDamagePercent(double value) {
        offensive.setAllElementalDamagePercent((float) value);
    }

    public void setDetonateDotOnCrit(double value) {
        offensive.setDetonateDotOnCrit((float) value);
    }

    public void setConsecutiveHitBonus(double value) {
        offensive.setConsecutiveHitBonus((float) value);
    }

    public void setSpellEchoChance(double value) {
        offensive.setSpellEchoChance((float) value);
    }

    public void setBlockCounterDamage(double value) {
        offensive.setBlockCounterDamage((float) value);
    }

    public void setBurnDamagePercent(double value) {
        offensive.setBurnDamagePercent((float) value);
    }

    public void setFrostDamagePercent(double value) {
        offensive.setFrostDamagePercent((float) value);
    }

    public void setShockDamagePercent(double value) {
        offensive.setShockDamagePercent((float) value);
    }

    public void setAccuracyPercent(double value) {
        offensive.setAccuracyPercent((float) value);
    }

    // ==================== Magic Stat Delegate Setters ====================

    public void setVolatilityMax(double value) {
        offensive.setVolatilityMax((float) value);
    }

    public void setMagicPower(double value) {
        offensive.setMagicPower((float) value);
    }

    public void setMagicCharges(int value) {
        offensive.setMagicCharges(value);
    }

    public void setDrawAccuracy(double value) {
        offensive.setDrawAccuracy((float) value);
    }

    public void setCastSpeed(double value) {
        offensive.setCastSpeed((float) value);
    }

    /** Sets the base damage from equipped weapon's implicit stat. */
    public void setWeaponBaseDamage(double value) {
        offensive.setWeaponBaseDamage((float) value);
    }

    /** Sets the vanilla item ID of the equipped weapon. */
    public void setWeaponItemId(String itemId) {
        offensive.setWeaponItemId(itemId);
    }

    /** Sets whether the player is holding RPG-generated gear. */
    public void setHoldingRpgGear(boolean holding) {
        offensive.setHoldingRpgGear(holding);
    }

    // ==================== Defensive Delegate Setters ====================

    public void setArmor(double value) {
        defensive.setArmor((float) value);
    }

    public void setArmorPercent(double value) {
        defensive.setArmorPercent((float) value);
    }

    public void setEvasion(double value) {
        defensive.setEvasion((float) value);
    }

    public void setEnergyShield(double value) {
        defensive.setEnergyShield((float) value);
    }

    public void setPassiveBlockChance(double value) {
        defensive.setPassiveBlockChance((float) value);
    }

    public void setParryChance(double value) {
        defensive.setParryChance((float) value);
    }

    public void setKnockbackResistance(double value) {
        defensive.setKnockbackResistance((float) value);
    }

    public void setFallDamageReduction(double value) {
        defensive.setFallDamageReduction((float) value);
    }

    public void setCritNullifyChance(double value) {
        defensive.setCritNullifyChance((float) value);
    }

    public void setPhysicalResistance(double value) {
        defensive.setPhysicalResistance((float) value);
    }

    public void setDodgeChance(double value) {
        defensive.setDodgeChance((float) value);
    }

    public void setBurnThreshold(double value) {
        defensive.setBurnThreshold((float) value);
    }

    public void setFreezeThreshold(double value) {
        defensive.setFreezeThreshold((float) value);
    }

    public void setShockThreshold(double value) {
        defensive.setShockThreshold((float) value);
    }

    // New defensive field setters
    public void setBlockHealPercent(double value) {
        defensive.setBlockHealPercent((float) value);
    }

    public void setShieldEffectivenessPercent(double value) {
        defensive.setShieldEffectivenessPercent((float) value);
    }

    public void setDamageTakenPercent(double value) {
        defensive.setDamageTakenPercent((float) value);
    }

    public void setHealthRecoveryPercent(double value) {
        defensive.setHealthRecoveryPercent((float) value);
    }

    public void setCriticalReduction(double value) {
        defensive.setCriticalReduction((float) value);
    }

    public void setBlockChance(double value) {
        defensive.setBlockChance((float) value);
    }

    public void setBlockDamageReduction(double value) {
        defensive.setBlockDamageReduction((float) value);
    }

    public void setStaminaDrainReduction(double value) {
        defensive.setStaminaDrainReduction((float) value);
    }

    public void setThornsDamage(double value) {
        defensive.setThornsDamage((float) value);
    }

    public void setThornsDamagePercent(double value) {
        defensive.setThornsDamagePercent((float) value);
    }

    public void setReflectDamagePercent(double value) {
        defensive.setReflectDamagePercent((float) value);
    }

    public void setShieldRegenOnDot(double value) {
        defensive.setShieldRegenOnDot((float) value);
    }

    public void setImmunityOnAilment(double value) {
        defensive.setImmunityOnAilment((float) value);
    }

    public void setEvasionToArmor(double value) {
        defensive.setEvasionToArmor((float) value);
    }

    // ==================== Movement Delegate Setters ====================

    public void setMovementSpeedPercent(double value) {
        movement.setMovementSpeedPercent((float) value);
    }

    public void setJumpForceBonus(double value) {
        movement.setJumpForceBonus((float) value);
    }

    public void setJumpForcePercent(double value) {
        movement.setJumpForcePercent((float) value);
    }

    public void setSprintSpeedBonus(double value) {
        movement.setSprintSpeedBonus((float) value);
    }

    public void setClimbSpeedBonus(double value) {
        movement.setClimbSpeedBonus((float) value);
    }

    public void setWalkSpeedPercent(double value) {
        movement.setWalkSpeedPercent((float) value);
    }

    public void setCrouchSpeedPercent(double value) {
        movement.setCrouchSpeedPercent((float) value);
    }

    public void setRunSpeedPercent(double value) {
        movement.setRunSpeedPercent((float) value);
    }

    // ==================== Elemental Delegate Setters ====================

    public void setFireDamage(double value) {
        elemental.setFlatDamage(ElementType.FIRE, value);
    }

    public void setWaterDamage(double value) {
        elemental.setFlatDamage(ElementType.WATER, value);
    }

    public void setLightningDamage(double value) {
        elemental.setFlatDamage(ElementType.LIGHTNING, value);
    }

    public void setVoidDamage(double value) {
        elemental.setFlatDamage(ElementType.VOID, value);
    }

    public void setFireDamagePercent(double value) {
        elemental.setPercentDamage(ElementType.FIRE, value);
    }

    public void setWaterDamagePercent(double value) {
        elemental.setPercentDamage(ElementType.WATER, value);
    }

    public void setLightningDamagePercent(double value) {
        elemental.setPercentDamage(ElementType.LIGHTNING, value);
    }

    public void setVoidDamagePercent(double value) {
        elemental.setPercentDamage(ElementType.VOID, value);
    }

    public void setFireMultiplier(double value) {
        elemental.setMultiplierDamage(ElementType.FIRE, value);
    }

    public void setWaterMultiplier(double value) {
        elemental.setMultiplierDamage(ElementType.WATER, value);
    }

    public void setLightningMultiplier(double value) {
        elemental.setMultiplierDamage(ElementType.LIGHTNING, value);
    }

    public void setVoidMultiplier(double value) {
        elemental.setMultiplierDamage(ElementType.VOID, value);
    }

    public void setFireResistance(double value) {
        elemental.setResistance(ElementType.FIRE, value);
    }

    public void setWaterResistance(double value) {
        elemental.setResistance(ElementType.WATER, value);
    }

    public void setLightningResistance(double value) {
        elemental.setResistance(ElementType.LIGHTNING, value);
    }

    public void setVoidResistance(double value) {
        elemental.setResistance(ElementType.VOID, value);
    }

    public void setFirePenetration(double value) {
        elemental.setPenetration(ElementType.FIRE, value);
    }

    public void setWaterPenetration(double value) {
        elemental.setPenetration(ElementType.WATER, value);
    }

    public void setLightningPenetration(double value) {
        elemental.setPenetration(ElementType.LIGHTNING, value);
    }

    public void setVoidPenetration(double value) {
        elemental.setPenetration(ElementType.VOID, value);
    }

    // ==================== Earth Elemental Delegate Setters ====================

    public void setEarthDamage(double value) {
        elemental.setFlatDamage(ElementType.EARTH, value);
    }

    public void setEarthDamagePercent(double value) {
        elemental.setPercentDamage(ElementType.EARTH, value);
    }

    public void setEarthMultiplier(double value) {
        elemental.setMultiplierDamage(ElementType.EARTH, value);
    }

    public void setEarthResistance(double value) {
        elemental.setResistance(ElementType.EARTH, value);
    }

    public void setEarthPenetration(double value) {
        elemental.setPenetration(ElementType.EARTH, value);
    }

    // ==================== Wind Elemental Delegate Setters ====================

    public void setWindDamage(double value) {
        elemental.setFlatDamage(ElementType.WIND, value);
    }

    public void setWindDamagePercent(double value) {
        elemental.setPercentDamage(ElementType.WIND, value);
    }

    public void setWindMultiplier(double value) {
        elemental.setMultiplierDamage(ElementType.WIND, value);
    }

    public void setWindResistance(double value) {
        elemental.setResistance(ElementType.WIND, value);
    }

    public void setWindPenetration(double value) {
        elemental.setPenetration(ElementType.WIND, value);
    }

    // ==================== Builder Methods ====================

    /** Creates a new builder with default values (all 0.0f). */
    @Nonnull
    public static Builder builder() {
        return new Builder();
    }

    /** Creates a builder pre-populated with this instance's values. */
    @Nonnull
    public Builder toBuilder() {
        return new Builder()
            // Resource
            .maxHealth(resource.getMaxHealth())
            .maxMana(resource.getMaxMana())
            .maxStamina(resource.getMaxStamina())
            .maxOxygen(resource.getMaxOxygen())
            .maxSignatureEnergy(resource.getMaxSignatureEnergy())
            .healthRegen(resource.getHealthRegen())
            .manaRegen(resource.getManaRegen())
            .staminaRegen(resource.getStaminaRegen())
            .oxygenRegen(resource.getOxygenRegen())
            .signatureEnergyRegen(resource.getSignatureEnergyRegen())
            .healthRegenPercent(resource.getHealthRegenPercent())
            .maxHealthPercent(resource.getMaxHealthPercent())
            .maxManaPercent(resource.getMaxManaPercent())
            .manaCostPercent(resource.getManaCostPercent())
            .maxStaminaPercent(resource.getMaxStaminaPercent())
            .staminaRegenPercent(resource.getStaminaRegenPercent())
            .staminaRegenStartDelay(resource.getStaminaRegenStartDelay())
            .signatureEnergyMaxPercent(resource.getSignatureEnergyMaxPercent())
            .signatureEnergyPerHit(resource.getSignatureEnergyPerHit())
            .manaOnKill(resource.getManaOnKill())
            // Offensive
            .physicalDamage(offensive.getPhysicalDamage())
            .spellDamage(offensive.getSpellDamage())
            .physicalDamagePercent(offensive.getPhysicalDamagePercent())
            .spellDamagePercent(offensive.getSpellDamagePercent())
            .criticalChance(offensive.getCriticalChance())
            .criticalMultiplier(offensive.getCriticalMultiplier())
            .meleeDamagePercent(offensive.getMeleeDamagePercent())
            .projectileDamagePercent(offensive.getProjectileDamagePercent())
            .attackSpeedPercent(offensive.getAttackSpeedPercent())
            .accuracy(offensive.getAccuracy())
            .armorPenetration(offensive.getArmorPenetration())
            .lifeSteal(offensive.getLifeSteal())
            .trueDamage(offensive.getTrueDamage())
            .chargedAttackDamage(offensive.getChargedAttackDamage())
            .chargedAttackDamagePercent(offensive.getChargedAttackDamagePercent())
            .trueDamagePercent(offensive.getTrueDamagePercent())
            .percentHitAsTrueDamage(offensive.getPercentHitAsTrueDamage())
            .allDamagePercent(offensive.getAllDamagePercent())
            .meleeDamage(offensive.getMeleeDamage())
            .lifeLeech(offensive.getLifeLeech())
            .manaLeech(offensive.getManaLeech())
            .damageAtLowLife(offensive.getDamageAtLowLife())
            .manaCostReduction(offensive.getManaCostReduction())
            .statusEffectChance(offensive.getStatusEffectChance())
            .statusEffectDuration(offensive.getStatusEffectDuration())
            .fireConversion(offensive.getFireConversion())
            .waterConversion(offensive.getWaterConversion())
            .lightningConversion(offensive.getLightningConversion())
            .voidConversion(offensive.getVoidConversion())
            .earthConversion(offensive.getEarthConversion())
            .windConversion(offensive.getWindConversion())
            .burnDamage(offensive.getBurnDamage())
            .freezeDamage(offensive.getFreezeDamage())
            .shockDamage(offensive.getShockDamage())
            .poisonDamage(offensive.getPoisonDamage())
            .igniteChance(offensive.getIgniteChance())
            .freezeChance(offensive.getFreezeChance())
            .shockChance(offensive.getShockChance())
            .burnDamagePercent(offensive.getBurnDamagePercent())
            .frostDamagePercent(offensive.getFrostDamagePercent())
            .shockDamagePercent(offensive.getShockDamagePercent())
            .accuracyPercent(offensive.getAccuracyPercent())
            .damagePercent(offensive.getDamagePercent())
            .damageMultiplier(offensive.getDamageMultiplier())
            .executeDamagePercent(offensive.getExecuteDamagePercent())
            .dotDamagePercent(offensive.getDotDamagePercent())
            .nonCritDamagePercent(offensive.getNonCritDamagePercent())
            .damageVsFrozenPercent(offensive.getDamageVsFrozenPercent())
            .damageVsShockedPercent(offensive.getDamageVsShockedPercent())
            .damageFromManaPercent(offensive.getDamageFromManaPercent())
            .manaSteal(offensive.getManaSteal())
            .burnDurationPercent(offensive.getBurnDurationPercent())
            .projectileSpeedPercent(offensive.getProjectileSpeedPercent())
            .projectileGravityPercent(offensive.getProjectileGravityPercent())
            .spellPenetration(offensive.getSpellPenetration())
            .allElementalDamagePercent(offensive.getAllElementalDamagePercent())
            .detonateDotOnCrit(offensive.getDetonateDotOnCrit())
            .consecutiveHitBonus(offensive.getConsecutiveHitBonus())
            .spellEchoChance(offensive.getSpellEchoChance())
            .blockCounterDamage(offensive.getBlockCounterDamage())
            .volatilityMax(offensive.getVolatilityMax())
            .magicPower(offensive.getMagicPower())
            .magicCharges(offensive.getMagicCharges())
            .drawAccuracy(offensive.getDrawAccuracy())
            .castSpeed(offensive.getCastSpeed())
            .weaponBaseDamage(offensive.getWeaponBaseDamage())
            .weaponItemId(offensive.getWeaponItemId())
            .holdingRpgGear(offensive.isHoldingRpgGear())
            // Defensive
            .armor(defensive.getArmor())
            .armorPercent(defensive.getArmorPercent())
            .evasion(defensive.getEvasion())
            .energyShield(defensive.getEnergyShield())
            .passiveBlockChance(defensive.getPassiveBlockChance())
            .parryChance(defensive.getParryChance())
            .knockbackResistance(defensive.getKnockbackResistance())
            .fallDamageReduction(defensive.getFallDamageReduction())
            .critNullifyChance(defensive.getCritNullifyChance())
            .physicalResistance(defensive.getPhysicalResistance())
            .dodgeChance(defensive.getDodgeChance())
            .burnThreshold(defensive.getBurnThreshold())
            .freezeThreshold(defensive.getFreezeThreshold())
            .shockThreshold(defensive.getShockThreshold())
            .criticalReduction(defensive.getCriticalReduction())
            .blockChance(defensive.getBlockChance())
            .blockDamageReduction(defensive.getBlockDamageReduction())
            .staminaDrainReduction(defensive.getStaminaDrainReduction())
            .blockHealPercent(defensive.getBlockHealPercent())
            .blockRecoveryPercent(defensive.getBlockRecoveryPercent())
            .shieldEffectivenessPercent(defensive.getShieldEffectivenessPercent())
            .healthRecoveryPercent(defensive.getHealthRecoveryPercent())
            .damageTakenPercent(defensive.getDamageTakenPercent())
            .damageWhenHitPercent(defensive.getDamageWhenHitPercent())
            .thornsDamage(defensive.getThornsDamage())
            .thornsDamagePercent(defensive.getThornsDamagePercent())
            .reflectDamagePercent(defensive.getReflectDamagePercent())
            .shieldRegenOnDot(defensive.getShieldRegenOnDot())
            .immunityOnAilment(defensive.getImmunityOnAilment())
            .evasionToArmor(defensive.getEvasionToArmor())
            // Movement
            .movementSpeedPercent(movement.getMovementSpeedPercent())
            .jumpForceBonus(movement.getJumpForceBonus())
            .jumpForcePercent(movement.getJumpForcePercent())
            .sprintSpeedBonus(movement.getSprintSpeedBonus())
            .climbSpeedBonus(movement.getClimbSpeedBonus())
            .walkSpeedPercent(movement.getWalkSpeedPercent())
            .crouchSpeedPercent(movement.getCrouchSpeedPercent())
            .runSpeedPercent(movement.getRunSpeedPercent())
            // Elemental (copy directly)
            .elemental(elemental)
            // Utility
            .experienceGainPercent(experienceGainPercent)
            .manaAsDamageBuffer(manaAsDamageBuffer)
            // Mob flag
            .mobStats(mobStats);
    }

    /** Creates a deep copy of this ComputedStats instance. */
    @Nonnull
    public ComputedStats copy() {
        return toBuilder().build();
    }

    // ==================== Elemental Conversion ====================

    /**
     * Returns the ElementalStats sub-object for damage calculation.
     *
     * <p>This allows the {@code RPGDamageSystem} to use the same elemental
     * damage calculation for both players and mobs.
     *
     * @return ElementalStats instance (same reference as getElemental())
     */
    @Nonnull
    public ElementalStats toElementalStats() {
        return elemental;
    }

    // ==================== Object Methods ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ComputedStats that = (ComputedStats) o;
        return Objects.equals(resource, that.resource)
            && Objects.equals(offensive, that.offensive)
            && Objects.equals(defensive, that.defensive)
            && Objects.equals(movement, that.movement)
            && Objects.equals(elemental, that.elemental)
            && Float.compare(experienceGainPercent, that.experienceGainPercent) == 0
            && Float.compare(manaAsDamageBuffer, that.manaAsDamageBuffer) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(resource, offensive, defensive, movement, elemental, experienceGainPercent, manaAsDamageBuffer);
    }

    @Override
    public String toString() {
        return String.format(
            "ComputedStats{hp=%.1f, mana=%.1f, phys=%.1f, spell=%.1f, crit=%.1f%%, armor=%.1f, acc=%.1f}",
            resource.getMaxHealth(), resource.getMaxMana(),
            offensive.getPhysicalDamage(), offensive.getSpellDamage(),
            offensive.getCriticalChance(), defensive.getArmor(), offensive.getAccuracy()
        );
    }

    // ==================== Builder Class ====================

    /**
     * Builder for creating ComputedStats instances.
     *
     * <p>Uses sub-object builders internally for cleaner organization.
     */
    public static final class Builder {
        private final ResourceStats.Builder resource = ResourceStats.builder();
        private final OffensiveStats.Builder offensive = OffensiveStats.builder();
        private final DefensiveStats.Builder defensive = DefensiveStats.builder();
        private final MovementStats.Builder movement = MovementStats.builder();
        private ElementalStats elemental = new ElementalStats();
        private float experienceGainPercent;
        private float manaAsDamageBuffer;
        private boolean mobStats;

        private Builder() {}

        // ==================== Sub-Object Setters ====================

        /** Sets the elemental stats directly from an existing ElementalStats. */
        public Builder elemental(@Nonnull ElementalStats stats) {
            this.elemental = stats.copy();
            return this;
        }

        // ==================== Resource Delegates ====================

        public Builder maxHealth(float value) {
            resource.maxHealth(value);
            return this;
        }

        public Builder maxMana(float value) {
            resource.maxMana(value);
            return this;
        }

        public Builder maxStamina(float value) {
            resource.maxStamina(value);
            return this;
        }

        public Builder maxOxygen(float value) {
            resource.maxOxygen(value);
            return this;
        }

        public Builder maxSignatureEnergy(float value) {
            resource.maxSignatureEnergy(value);
            return this;
        }

        public Builder healthRegen(float value) {
            resource.healthRegen(value);
            return this;
        }

        public Builder manaRegen(float value) {
            resource.manaRegen(value);
            return this;
        }

        public Builder staminaRegen(float value) {
            resource.staminaRegen(value);
            return this;
        }

        public Builder oxygenRegen(float value) {
            resource.oxygenRegen(value);
            return this;
        }

        public Builder signatureEnergyRegen(float value) {
            resource.signatureEnergyRegen(value);
            return this;
        }

        public Builder healthRegenPercent(float value) {
            resource.healthRegenPercent(value);
            return this;
        }

        // ==================== Offensive Delegates ====================

        public Builder physicalDamage(float value) {
            offensive.physicalDamage(value);
            return this;
        }

        public Builder spellDamage(float value) {
            offensive.spellDamage(value);
            return this;
        }

        public Builder physicalDamagePercent(float value) {
            offensive.physicalDamagePercent(value);
            return this;
        }

        public Builder spellDamagePercent(float value) {
            offensive.spellDamagePercent(value);
            return this;
        }

        public Builder criticalChance(float value) {
            offensive.criticalChance(value);
            return this;
        }

        public Builder criticalMultiplier(float value) {
            offensive.criticalMultiplier(value);
            return this;
        }

        public Builder meleeDamagePercent(float value) {
            offensive.meleeDamagePercent(value);
            return this;
        }

        public Builder projectileDamagePercent(float value) {
            offensive.projectileDamagePercent(value);
            return this;
        }

        public Builder attackSpeedPercent(float value) {
            offensive.attackSpeedPercent(value);
            return this;
        }

        public Builder accuracy(float value) {
            offensive.accuracy(value);
            return this;
        }

        public Builder armorPenetration(float value) {
            offensive.armorPenetration(value);
            return this;
        }

        public Builder lifeSteal(float value) {
            offensive.lifeSteal(value);
            return this;
        }

        public Builder trueDamage(float value) {
            offensive.trueDamage(value);
            return this;
        }

        public Builder chargedAttackDamage(float value) {
            offensive.chargedAttackDamage(value);
            return this;
        }

        public Builder chargedAttackDamagePercent(float value) {
            offensive.chargedAttackDamagePercent(value);
            return this;
        }

        public Builder trueDamagePercent(float value) {
            offensive.trueDamagePercent(value);
            return this;
        }

        public Builder percentHitAsTrueDamage(float value) {
            offensive.percentHitAsTrueDamage(value);
            return this;
        }

        public Builder allDamagePercent(float value) {
            offensive.allDamagePercent(value);
            return this;
        }

        public Builder meleeDamage(float value) {
            offensive.meleeDamage(value);
            return this;
        }

        public Builder lifeLeech(float value) {
            offensive.lifeLeech(value);
            return this;
        }

        public Builder manaLeech(float value) {
            offensive.manaLeech(value);
            return this;
        }

        public Builder damageAtLowLife(float value) {
            offensive.damageAtLowLife(value);
            return this;
        }

        public Builder manaCostReduction(float value) {
            offensive.manaCostReduction(value);
            return this;
        }

        public Builder statusEffectChance(float value) {
            offensive.statusEffectChance(value);
            return this;
        }

        public Builder statusEffectDuration(float value) {
            offensive.statusEffectDuration(value);
            return this;
        }

        public Builder fireConversion(float value) {
            offensive.fireConversion(value);
            return this;
        }

        public Builder waterConversion(float value) {
            offensive.waterConversion(value);
            return this;
        }

        public Builder lightningConversion(float value) {
            offensive.lightningConversion(value);
            return this;
        }

        public Builder voidConversion(float value) {
            offensive.voidConversion(value);
            return this;
        }

        public Builder earthConversion(float value) {
            offensive.earthConversion(value);
            return this;
        }

        public Builder windConversion(float value) {
            offensive.windConversion(value);
            return this;
        }

        public Builder burnDamage(float value) {
            offensive.burnDamage(value);
            return this;
        }

        public Builder freezeDamage(float value) {
            offensive.freezeDamage(value);
            return this;
        }

        public Builder shockDamage(float value) {
            offensive.shockDamage(value);
            return this;
        }

        public Builder poisonDamage(float value) {
            offensive.poisonDamage(value);
            return this;
        }

        // ==================== Defensive Delegates ====================

        public Builder armor(float value) {
            defensive.armor(value);
            return this;
        }

        public Builder armorPercent(float value) {
            defensive.armorPercent(value);
            return this;
        }

        public Builder evasion(float value) {
            defensive.evasion(value);
            return this;
        }

        public Builder energyShield(float value) {
            defensive.energyShield(value);
            return this;
        }

        public Builder passiveBlockChance(float value) {
            defensive.passiveBlockChance(value);
            return this;
        }

        public Builder parryChance(float value) {
            defensive.parryChance(value);
            return this;
        }

        public Builder knockbackResistance(float value) {
            defensive.knockbackResistance(value);
            return this;
        }

        public Builder fallDamageReduction(float value) {
            defensive.fallDamageReduction(value);
            return this;
        }

        public Builder critNullifyChance(float value) {
            defensive.critNullifyChance(value);
            return this;
        }

        public Builder physicalResistance(float value) {
            defensive.physicalResistance(value);
            return this;
        }

        public Builder dodgeChance(float value) {
            defensive.dodgeChance(value);
            return this;
        }

        public Builder burnThreshold(float value) {
            defensive.burnThreshold(value);
            return this;
        }

        public Builder freezeThreshold(float value) {
            defensive.freezeThreshold(value);
            return this;
        }

        public Builder shockThreshold(float value) {
            defensive.shockThreshold(value);
            return this;
        }

        // ==================== Movement Delegates ====================

        public Builder movementSpeedPercent(float value) {
            movement.movementSpeedPercent(value);
            return this;
        }

        public Builder jumpForceBonus(float value) {
            movement.jumpForceBonus(value);
            return this;
        }

        public Builder jumpForcePercent(float value) {
            movement.jumpForcePercent(value);
            return this;
        }

        public Builder sprintSpeedBonus(float value) {
            movement.sprintSpeedBonus(value);
            return this;
        }

        public Builder climbSpeedBonus(float value) {
            movement.climbSpeedBonus(value);
            return this;
        }

        public Builder walkSpeedPercent(float value) {
            movement.walkSpeedPercent(value);
            return this;
        }

        public Builder crouchSpeedPercent(float value) {
            movement.crouchSpeedPercent(value);
            return this;
        }

        public Builder runSpeedPercent(float value) {
            movement.runSpeedPercent(value);
            return this;
        }

        // ==================== Utility Delegates ====================

        public Builder experienceGainPercent(float value) {
            this.experienceGainPercent = value;
            return this;
        }

        public Builder manaAsDamageBuffer(float value) {
            this.manaAsDamageBuffer = value;
            return this;
        }

        /**
         * Marks these stats as originating from a mob.
         *
         * <p>When true, the damage system skips flat damage addition since
         * mob damage is calculated via the weighted RPG formula upfront.
         *
         * @param value true for mob-origin stats
         * @return This builder
         */
        public Builder mobStats(boolean value) {
            this.mobStats = value;
            return this;
        }

        // ==================== Elemental Delegates ====================

        public Builder fireDamage(float value) {
            elemental.setFlatDamage(ElementType.FIRE, value);
            return this;
        }

        public Builder waterDamage(float value) {
            elemental.setFlatDamage(ElementType.WATER, value);
            return this;
        }

        public Builder lightningDamage(float value) {
            elemental.setFlatDamage(ElementType.LIGHTNING, value);
            return this;
        }

        public Builder voidDamage(float value) {
            elemental.setFlatDamage(ElementType.VOID, value);
            return this;
        }

        public Builder fireDamagePercent(float value) {
            elemental.setPercentDamage(ElementType.FIRE, value);
            return this;
        }

        public Builder waterDamagePercent(float value) {
            elemental.setPercentDamage(ElementType.WATER, value);
            return this;
        }

        public Builder lightningDamagePercent(float value) {
            elemental.setPercentDamage(ElementType.LIGHTNING, value);
            return this;
        }

        public Builder voidDamagePercent(float value) {
            elemental.setPercentDamage(ElementType.VOID, value);
            return this;
        }

        public Builder fireDamageMultiplier(float value) {
            elemental.setMultiplierDamage(ElementType.FIRE, value);
            return this;
        }

        public Builder waterDamageMultiplier(float value) {
            elemental.setMultiplierDamage(ElementType.WATER, value);
            return this;
        }

        public Builder lightningDamageMultiplier(float value) {
            elemental.setMultiplierDamage(ElementType.LIGHTNING, value);
            return this;
        }

        public Builder voidDamageMultiplier(float value) {
            elemental.setMultiplierDamage(ElementType.VOID, value);
            return this;
        }

        public Builder fireResistance(float value) {
            elemental.setResistance(ElementType.FIRE, value);
            return this;
        }

        public Builder waterResistance(float value) {
            elemental.setResistance(ElementType.WATER, value);
            return this;
        }

        public Builder lightningResistance(float value) {
            elemental.setResistance(ElementType.LIGHTNING, value);
            return this;
        }

        public Builder voidResistance(float value) {
            elemental.setResistance(ElementType.VOID, value);
            return this;
        }

        public Builder firePenetration(float value) {
            elemental.setPenetration(ElementType.FIRE, value);
            return this;
        }

        public Builder waterPenetration(float value) {
            elemental.setPenetration(ElementType.WATER, value);
            return this;
        }

        public Builder lightningPenetration(float value) {
            elemental.setPenetration(ElementType.LIGHTNING, value);
            return this;
        }

        public Builder voidPenetration(float value) {
            elemental.setPenetration(ElementType.VOID, value);
            return this;
        }

        // ==================== Earth Elemental Delegates ====================

        public Builder earthDamage(float value) {
            elemental.setFlatDamage(ElementType.EARTH, value);
            return this;
        }

        public Builder earthDamagePercent(float value) {
            elemental.setPercentDamage(ElementType.EARTH, value);
            return this;
        }

        public Builder earthDamageMultiplier(float value) {
            elemental.setMultiplierDamage(ElementType.EARTH, value);
            return this;
        }

        public Builder earthResistance(float value) {
            elemental.setResistance(ElementType.EARTH, value);
            return this;
        }

        public Builder earthPenetration(float value) {
            elemental.setPenetration(ElementType.EARTH, value);
            return this;
        }

        // ==================== Wind Elemental Delegates ====================

        public Builder windDamage(float value) {
            elemental.setFlatDamage(ElementType.WIND, value);
            return this;
        }

        public Builder windDamagePercent(float value) {
            elemental.setPercentDamage(ElementType.WIND, value);
            return this;
        }

        public Builder windDamageMultiplier(float value) {
            elemental.setMultiplierDamage(ElementType.WIND, value);
            return this;
        }

        public Builder windResistance(float value) {
            elemental.setResistance(ElementType.WIND, value);
            return this;
        }

        public Builder windPenetration(float value) {
            elemental.setPenetration(ElementType.WIND, value);
            return this;
        }

        // ==================== New Offensive Field Delegates ====================

        public Builder igniteChance(float value) {
            offensive.igniteChance(value);
            return this;
        }

        public Builder freezeChance(float value) {
            offensive.freezeChance(value);
            return this;
        }

        public Builder shockChance(float value) {
            offensive.shockChance(value);
            return this;
        }

        public Builder damagePercent(float value) {
            offensive.damagePercent(value);
            return this;
        }

        public Builder damageMultiplier(float value) {
            offensive.damageMultiplier(value);
            return this;
        }

        public Builder executeDamagePercent(float value) {
            offensive.executeDamagePercent(value);
            return this;
        }

        public Builder dotDamagePercent(float value) {
            offensive.dotDamagePercent(value);
            return this;
        }

        public Builder nonCritDamagePercent(float value) {
            offensive.nonCritDamagePercent(value);
            return this;
        }

        public Builder damageVsFrozenPercent(float value) {
            offensive.damageVsFrozenPercent(value);
            return this;
        }

        public Builder damageVsShockedPercent(float value) {
            offensive.damageVsShockedPercent(value);
            return this;
        }

        public Builder damageFromManaPercent(float value) {
            offensive.damageFromManaPercent(value);
            return this;
        }

        public Builder manaSteal(float value) {
            offensive.manaSteal(value);
            return this;
        }

        public Builder burnDurationPercent(float value) {
            offensive.burnDurationPercent(value);
            return this;
        }

        public Builder projectileSpeedPercent(float value) {
            offensive.projectileSpeedPercent(value);
            return this;
        }

        public Builder projectileGravityPercent(float value) {
            offensive.projectileGravityPercent(value);
            return this;
        }

        public Builder spellPenetration(float value) {
            offensive.spellPenetration(value);
            return this;
        }

        public Builder allElementalDamagePercent(float value) {
            offensive.allElementalDamagePercent(value);
            return this;
        }

        public Builder detonateDotOnCrit(float value) {
            offensive.detonateDotOnCrit(value);
            return this;
        }

        public Builder consecutiveHitBonus(float value) {
            offensive.consecutiveHitBonus(value);
            return this;
        }

        public Builder spellEchoChance(float value) {
            offensive.spellEchoChance(value);
            return this;
        }

        public Builder blockCounterDamage(float value) {
            offensive.blockCounterDamage(value);
            return this;
        }

        public Builder volatilityMax(float value) {
            offensive.volatilityMax(value);
            return this;
        }

        public Builder magicPower(float value) {
            offensive.magicPower(value);
            return this;
        }

        public Builder magicCharges(int value) {
            offensive.magicCharges(value);
            return this;
        }

        public Builder drawAccuracy(float value) {
            offensive.drawAccuracy(value);
            return this;
        }

        public Builder castSpeed(float value) {
            offensive.castSpeed(value);
            return this;
        }

        public Builder weaponBaseDamage(float value) {
            offensive.weaponBaseDamage(value);
            return this;
        }

        public Builder weaponItemId(String value) {
            offensive.weaponItemId(value);
            return this;
        }

        public Builder holdingRpgGear(boolean value) {
            offensive.holdingRpgGear(value);
            return this;
        }

        // ==================== New Defensive Field Delegates ====================

        public Builder blockHealPercent(float value) {
            defensive.blockHealPercent(value);
            return this;
        }

        public Builder blockRecoveryPercent(float value) {
            defensive.blockRecoveryPercent(value);
            return this;
        }

        public Builder shieldEffectivenessPercent(float value) {
            defensive.shieldEffectivenessPercent(value);
            return this;
        }

        public Builder healthRecoveryPercent(float value) {
            defensive.healthRecoveryPercent(value);
            return this;
        }

        public Builder damageTakenPercent(float value) {
            defensive.damageTakenPercent(value);
            return this;
        }

        public Builder damageWhenHitPercent(float value) {
            defensive.damageWhenHitPercent(value);
            return this;
        }

        // ==================== New Resource Field Delegates ====================

        public Builder maxHealthPercent(float value) {
            resource.maxHealthPercent(value);
            return this;
        }

        public Builder maxManaPercent(float value) {
            resource.maxManaPercent(value);
            return this;
        }

        public Builder manaCostPercent(float value) {
            resource.manaCostPercent(value);
            return this;
        }

        public Builder signatureEnergyMaxPercent(float value) {
            resource.signatureEnergyMaxPercent(value);
            return this;
        }

        public Builder signatureEnergyPerHit(float value) {
            resource.signatureEnergyPerHit(value);
            return this;
        }

        public Builder manaOnKill(float value) {
            resource.manaOnKill(value);
            return this;
        }

        public Builder maxStaminaPercent(float value) {
            resource.maxStaminaPercent(value);
            return this;
        }

        public Builder staminaRegenPercent(float value) {
            resource.staminaRegenPercent(value);
            return this;
        }

        public Builder staminaRegenStartDelay(float value) {
            resource.staminaRegenStartDelay(value);
            return this;
        }

        // ==================== New Offensive Field Delegates (Ailment Damage Percent) ====================

        public Builder burnDamagePercent(float value) {
            offensive.burnDamagePercent(value);
            return this;
        }

        public Builder frostDamagePercent(float value) {
            offensive.frostDamagePercent(value);
            return this;
        }

        public Builder shockDamagePercent(float value) {
            offensive.shockDamagePercent(value);
            return this;
        }

        public Builder accuracyPercent(float value) {
            offensive.accuracyPercent(value);
            return this;
        }

        // ==================== New Defensive Field Delegates ====================

        public Builder criticalReduction(float value) {
            defensive.criticalReduction(value);
            return this;
        }

        public Builder blockChance(float value) {
            defensive.blockChance(value);
            return this;
        }

        public Builder blockDamageReduction(float value) {
            defensive.blockDamageReduction(value);
            return this;
        }

        public Builder staminaDrainReduction(float value) {
            defensive.staminaDrainReduction(value);
            return this;
        }

        public Builder thornsDamage(float value) {
            defensive.thornsDamage(value);
            return this;
        }

        public Builder thornsDamagePercent(float value) {
            defensive.thornsDamagePercent(value);
            return this;
        }

        public Builder reflectDamagePercent(float value) {
            defensive.reflectDamagePercent(value);
            return this;
        }

        public Builder shieldRegenOnDot(float value) {
            defensive.shieldRegenOnDot(value);
            return this;
        }

        public Builder immunityOnAilment(float value) {
            defensive.immunityOnAilment(value);
            return this;
        }

        public Builder evasionToArmor(float value) {
            defensive.evasionToArmor(value);
            return this;
        }

        /** Builds the ComputedStats instance. */
        @Nonnull
        public ComputedStats build() {
            return new ComputedStats(this);
        }
    }
}
