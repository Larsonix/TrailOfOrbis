package io.github.larsonix.trailoforbis.attributes;

import io.github.larsonix.trailoforbis.config.RPGConfig;
import io.github.larsonix.trailoforbis.config.RPGConfig.AttributeConfig;
import io.github.larsonix.trailoforbis.database.models.PlayerData;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Calculator that converts player elemental attributes into computed gameplay stats.
 *
 * <p>This is a pure function class - same input always produces same output.
 * All stat formulas are driven by {@link RPGConfig} values, not hardcoded.
 *
 * <p>The elemental system uses 6 elements, each with 5 unique stats (zero overlap):
 * <ul>
 *   <li>FIRE: phys dmg%, heavy atk dmg%, crit mult, burn dmg%, ignite</li>
 *   <li>WATER: spell dmg%, mana, barrier, mana regen, freeze</li>
 *   <li>LIGHTNING: atk speed%, move speed%, crit chance, stam regen, shock</li>
 *   <li>EARTH: max HP%, armor, HP regen, block, KB resist</li>
 *   <li>WIND: evasion, accuracy, proj dmg%, jump, proj speed%</li>
 *   <li>VOID: life steal, % hit as true dmg, DoT dmg%, mana/kill, effect duration</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 * AttributeCalculator calc = new AttributeCalculator(config);
 * ComputedStats stats = calc.calculateStats(playerData, baseStats);
 * </pre>
 */
public class AttributeCalculator {
    /** Base critical strike chance (%) */
    public static final float BASE_CRIT_CHANCE = 5.0f;

    /** Base critical strike multiplier (%) */
    public static final float BASE_CRIT_MULTIPLIER = 150.0f;

    /** Base accuracy (%) - all players start with this */
    public static final float BASE_ACCURACY = 10.0f;

    private final RPGConfig config;

    /**
     * @throws NullPointerException if config is null
     */
    public AttributeCalculator(@Nonnull RPGConfig config) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
    }

    /**
     * Calculates ALL gameplay stats from player attributes.
     *
     * <p>PURE FUNCTION: Same input always produces same output.
     * No side effects, fully deterministic.
     *
     * @return New ComputedStats instance (never null)
     */
    @Nonnull
    public ComputedStats calculateStats(@Nonnull PlayerData data, @Nonnull BaseStats baseStats) {
        return calculateStats(data, baseStats, 0f, 1);
    }

    /**
     * Calculates ALL gameplay stats from player attributes with equipment armor.
     *
     * @param equipmentArmor Base armor value from equipped armor pieces
     * @return New ComputedStats instance (never null)
     */
    @Nonnull
    public ComputedStats calculateStats(@Nonnull PlayerData data, @Nonnull BaseStats baseStats, float equipmentArmor) {
        return calculateStats(data, baseStats, equipmentArmor, 1);
    }

    /**
     * Calculates ALL gameplay stats from player elemental attributes with equipment armor and level.
     *
     * <p>PURE FUNCTION: Same input always produces same output.
     * No side effects, fully deterministic.
     *
     * <p>Each element contributes exactly 5 unique stats (no overlap between elements),
     * plus magic stats from WATER/FIRE/LIGHTNING for Hexcode integration:
     * <ul>
     *   <li>FIRE: physicalDamagePercent, chargedAttackDamagePercent, critMultiplier, burnDamagePercent, igniteChance, magicPower</li>
     *   <li>WATER: spellDamagePercent, maxMana, energyShield, manaRegen, freezeChance, magicPower, volatilityMax</li>
     *   <li>LIGHTNING: attackSpeedPercent, moveSpeedPercent, critChance, staminaRegen, shockChance, castSpeed</li>
     *   <li>EARTH: maxHealthPercent, armor, healthRegen, blockChance, knockbackResistance</li>
     *   <li>WIND: evasion, accuracy, projectileDamagePercent, jumpForcePercent, projectileSpeedPercent</li>
     *   <li>VOID: lifeSteal, percentHitAsTrueDamage, dotDamagePercent, manaOnKill, statusEffectDuration</li>
     * </ul>
     *
     * @param equipmentArmor Base armor value from equipped armor pieces
     * @param playerLevel The player's current level (for magic charges calculation)
     * @return New ComputedStats instance (never null)
     */
    @Nonnull
    public ComputedStats calculateStats(@Nonnull PlayerData data, @Nonnull BaseStats baseStats, float equipmentArmor, int playerLevel) {
        Objects.requireNonNull(data, "data cannot be null");
        Objects.requireNonNull(baseStats, "baseStats cannot be null");

        // Read element values from player data
        int fire = data.getFire();
        int water = data.getWater();
        int lightning = data.getLightning();
        int earth = data.getEarth();
        int wind = data.getWind();
        int voidAttr = data.getVoidAttr();

        // Get grant configurations
        AttributeConfig.FireGrants fireGrants = config.getAttributes().getFireGrants();
        AttributeConfig.WaterGrants waterGrants = config.getAttributes().getWaterGrants();
        AttributeConfig.LightningGrants lightningGrants = config.getAttributes().getLightningGrants();
        AttributeConfig.EarthGrants earthGrants = config.getAttributes().getEarthGrants();
        AttributeConfig.WindGrants windGrants = config.getAttributes().getWindGrants();
        AttributeConfig.VoidGrants voidGrants = config.getAttributes().getVoidGrants();

        // ==================== Calculate Stats (1:1 element mapping) ====================

        // FIRE stats
        float physicalDamagePercent = fire * fireGrants.getPhysicalDamagePercent();
        float chargedAttackDamagePercent = fire * fireGrants.getChargedAttackDamagePercent();
        float critMultiplier = BASE_CRIT_MULTIPLIER + (fire * fireGrants.getCriticalMultiplier());
        float burnDamagePercent = fire * fireGrants.getBurnDamagePercent();
        float igniteChance = fire * fireGrants.getIgniteChance();

        // WATER stats
        float spellDamagePercent = water * waterGrants.getSpellDamagePercent();
        float maxMana = baseStats.getMaxMana() + (water * waterGrants.getMaxMana());
        float energyShield = water * waterGrants.getEnergyShield();
        float manaRegen = water * waterGrants.getManaRegen();
        float freezeChance = water * waterGrants.getFreezeChance();

        // LIGHTNING stats
        float attackSpeedPercent = lightning * lightningGrants.getAttackSpeedPercent();
        float moveSpeedPercent = lightning * lightningGrants.getMoveSpeedPercent();
        float critChance = BASE_CRIT_CHANCE + (lightning * lightningGrants.getCritChance());
        float staminaRegen = lightning * lightningGrants.getStaminaRegen();
        float shockChance = lightning * lightningGrants.getShockChance();

        // EARTH stats
        float maxHealth = baseStats.getMaxHealth();
        float maxHealthPercent = earth * earthGrants.getMaxHealthPercent();
        float armor = equipmentArmor + (earth * earthGrants.getArmor());
        float healthRegen = earth * earthGrants.getHealthRegen();
        float blockChance = earth * earthGrants.getBlockChance();
        float knockbackResistance = earth * earthGrants.getKnockbackResistance();

        // WIND stats
        float evasion = wind * windGrants.getEvasion();
        float accuracy = BASE_ACCURACY + (wind * windGrants.getAccuracy());
        float projectileDamagePercent = wind * windGrants.getProjectileDamagePercent();
        float jumpForceBonus = wind * windGrants.getJumpForcePercent();
        float projectileSpeedPercent = wind * windGrants.getProjectileSpeedPercent();

        // VOID stats
        float lifeSteal = voidAttr * voidGrants.getLifeSteal();
        float percentHitAsTrueDamage = voidAttr * voidGrants.getPercentHitAsTrueDamage();
        float dotDamagePercent = voidAttr * voidGrants.getDotDamagePercent();
        float manaOnKill = voidAttr * voidGrants.getManaOnKill();
        float statusEffectDuration = voidAttr * voidGrants.getStatusEffectDuration();

        // MAGIC stats (Hexcode integration — from WATER, FIRE, LIGHTNING)
        float magicPower = (water * waterGrants.getMagicPower()) + (fire * fireGrants.getMagicPower());
        float volatilityMax = water * waterGrants.getVolatilityMax();
        float castSpeed = lightning * lightningGrants.getCastSpeed();

        // Magic charges from player level (not attributes)
        RPGConfig.AttributeConfig.MagicChargesConfig chargesConfig = config.getAttributes().getMagicCharges();
        int perLevels = Math.max(1, chargesConfig.getPerLevels()); // prevent division by zero
        int magicCharges = chargesConfig.getBase() + (playerLevel / perLevels);

        return ComputedStats.builder()
            // ==================== Core Resources ====================
            .maxHealth(maxHealth)
            .maxHealthPercent(maxHealthPercent)
            .maxMana(maxMana)
            .maxStamina(baseStats.getMaxStamina())
            .maxOxygen(baseStats.getMaxOxygen())
            .maxSignatureEnergy(baseStats.getMaxSignatureEnergy())

            // ==================== Regeneration ====================
            .healthRegen(healthRegen)
            .manaRegen(manaRegen)
            .staminaRegen(staminaRegen)

            // ==================== Defense ====================
            .armor(armor)
            .energyShield(energyShield)
            .evasion(evasion)
            .blockChance(blockChance)
            .knockbackResistance(knockbackResistance)

            // ==================== Offense: FIRE ====================
            .physicalDamagePercent(physicalDamagePercent)
            .chargedAttackDamagePercent(chargedAttackDamagePercent)
            .criticalMultiplier(critMultiplier)
            .burnDamagePercent(burnDamagePercent)
            .igniteChance(igniteChance)

            // ==================== Offense: WATER ====================
            .spellDamagePercent(spellDamagePercent)
            .freezeChance(freezeChance)

            // ==================== Offense: LIGHTNING ====================
            .criticalChance(critChance)
            .attackSpeedPercent(attackSpeedPercent)
            .shockChance(shockChance)

            // ==================== Offense: WIND ====================
            .accuracy(accuracy)
            .projectileDamagePercent(projectileDamagePercent)
            .projectileSpeedPercent(projectileSpeedPercent)

            // ==================== Offense: VOID ====================
            .lifeSteal(lifeSteal)
            .percentHitAsTrueDamage(percentHitAsTrueDamage)
            .dotDamagePercent(dotDamagePercent)
            .statusEffectDuration(statusEffectDuration)

            // ==================== Movement ====================
            .movementSpeedPercent(moveSpeedPercent)
            .jumpForceBonus(jumpForceBonus)

            // ==================== Special ====================
            .manaOnKill(manaOnKill)

            // ==================== Magic (Hexcode Integration) ====================
            .magicPower(magicPower)
            .volatilityMax(volatilityMax)
            .castSpeed(castSpeed)
            .magicCharges(magicCharges)

            .build();
    }

    /** Gets the RPG configuration used by this calculator. */
    @Nonnull
    public RPGConfig getConfig() {
        return config;
    }
}
