package io.github.larsonix.trailoforbis.attributes;

import io.github.larsonix.trailoforbis.config.RPGConfig;
import io.github.larsonix.trailoforbis.database.models.PlayerData;
import io.github.larsonix.trailoforbis.attributes.BaseStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AttributeCalculator.
 *
 * <p>Verifies that all stat formulas correctly use config grants
 * and base values are applied properly.
 *
 * <p>The elemental attribute system uses 6 elements with zero overlap (30 unique stats):
 * <ul>
 *   <li>FIRE: phys dmg, charged atk dmg, crit mult, burn dmg, ignite</li>
 *   <li>WATER: spell dmg, mana, barrier, mana regen, freeze</li>
 *   <li>LIGHTNING: atk speed, move speed, crit chance, stam regen, shock</li>
 *   <li>EARTH: max HP%, armor, HP regen, block, KB resist</li>
 *   <li>WIND: evasion, accuracy, proj dmg, jump, proj speed</li>
 *   <li>VOID: life steal, % hit as true dmg, DoT, mana/kill, effect duration</li>
 * </ul>
 */
public class AttributeCalculatorTest {
    private RPGConfig config;
    private AttributeCalculator calculator;

    @BeforeEach
    void setUp() {
        config = new RPGConfig();
        calculator = new AttributeCalculator(config);
    }

    @Test
    @DisplayName("Zero attributes produces base stats only")
    void testZeroAttributesBaseStats() {
        PlayerData data = PlayerData.builder()
            .uuid(UUID.randomUUID())
            .username("TestPlayer")
            .fire(0)
            .water(0)
            .lightning(0)
            .earth(0)
            .wind(0)
            .voidAttr(0)
            .build();

        ComputedStats stats = calculator.calculateStats(data, BaseStats.defaults());

        // Base values only
        assertEquals(100f, stats.getMaxHealth(), 0.01f, "Base health should be 100");
        assertEquals(0f, stats.getMaxMana(), 0.01f, "Base mana should be 0");
        assertEquals(10f, stats.getMaxStamina(), 0.01f, "Base stamina should be 10");
        assertEquals(5f, stats.getCriticalChance(), 0.01f, "Base crit chance should be 5%");
        assertEquals(150f, stats.getCriticalMultiplier(), 0.01f, "Base crit multiplier should be 150%");

        // No damage bonuses
        assertEquals(0f, stats.getPhysicalDamage(), 0.01f);
        assertEquals(0f, stats.getSpellDamage(), 0.01f);
        assertEquals(0f, stats.getMovementSpeedPercent(), 0.01f);
        assertEquals(0f, stats.getArmor(), 0.01f);
    }

    @Test
    @DisplayName("FIRE grants physical damage, charged attack damage, crit multiplier, burn, ignite")
    void testFireGrants() {
        PlayerData data = PlayerData.builder()
            .uuid(UUID.randomUUID())
            .username("TestPlayer")
            .fire(50)
            .water(0)
            .lightning(0)
            .earth(0)
            .wind(0)
            .voidAttr(0)
            .build();

        ComputedStats stats = calculator.calculateStats(data, BaseStats.defaults());

        // Physical Damage %: 50 * 0.4 = 20%
        assertEquals(20f, stats.getPhysicalDamagePercent(), 0.01f);

        // Charged Attack Damage %: 50 * 0.3 = 15%
        assertEquals(15f, stats.getChargedAttackDamagePercent(), 0.01f);

        // Crit Multiplier: 150 base + (50 * 0.6) = 180%
        assertEquals(180f, stats.getCriticalMultiplier(), 0.01f);

        // Burn Damage %: 50 * 0.4 = 20%
        assertEquals(20f, stats.getBurnDamagePercent(), 0.01f);

        // Ignite Chance: 50 * 0.1 = 5%
        assertEquals(5f, stats.getIgniteChance(), 0.01f);

        // Health should be base only (no fire penalty anymore)
        assertEquals(100f, stats.getMaxHealth(), 0.01f);
    }

    @Test
    @DisplayName("WATER grants spell damage, mana, barrier, mana regen, and freeze")
    void testWaterGrants() {
        PlayerData data = PlayerData.builder()
            .uuid(UUID.randomUUID())
            .username("TestPlayer")
            .fire(0)
            .water(50)
            .lightning(0)
            .earth(0)
            .wind(0)
            .voidAttr(0)
            .build();

        ComputedStats stats = calculator.calculateStats(data, BaseStats.defaults());

        // Spell Damage %: 50 * 0.5 = 25%
        assertEquals(25f, stats.getSpellDamagePercent(), 0.01f);

        // Max Mana: 0 base + (50 * 1.5) = 75
        assertEquals(75f, stats.getMaxMana(), 0.01f);

        // Energy Shield (barrier): 50 * 2.0 = 100
        assertEquals(100f, stats.getEnergyShield(), 0.01f);

        // Mana Regen: 50 * 0.15 = 7.5
        assertEquals(7.5f, stats.getManaRegen(), 0.01f);

        // Freeze Chance: 50 * 0.1 = 5%
        assertEquals(5f, stats.getFreezeChance(), 0.01f);

        // Move speed should be 0 (no water penalty anymore)
        assertEquals(0f, stats.getMovementSpeedPercent(), 0.01f);
    }

    @Test
    @DisplayName("LIGHTNING grants attack speed, move speed, crit chance, stamina regen, shock")
    void testLightningGrants() {
        PlayerData data = PlayerData.builder()
            .uuid(UUID.randomUUID())
            .username("TestPlayer")
            .fire(0)
            .water(0)
            .lightning(30)
            .earth(0)
            .wind(0)
            .voidAttr(0)
            .build();

        ComputedStats stats = calculator.calculateStats(data, BaseStats.defaults());

        // Attack Speed %: 30 * 0.3 = 9%
        assertEquals(9f, stats.getAttackSpeedPercent(), 0.01f);

        // Movement Speed: 30 * 0.15 = 4.5%
        assertEquals(4.5f, stats.getMovementSpeedPercent(), 0.01f);

        // Crit Chance: 5 base + (30 * 0.1) = 8%
        assertEquals(8f, stats.getCriticalChance(), 0.01f);

        // Stamina Regen: 30 * 0.1 = 3
        assertEquals(3f, stats.getStaminaRegen(), 0.01f);

        // Shock Chance: 30 * 0.1 = 3%
        assertEquals(3f, stats.getShockChance(), 0.01f);

        // Spell damage should be 0 (spell damage is Water now)
        assertEquals(0f, stats.getSpellDamagePercent(), 0.01f);
    }

    @Test
    @DisplayName("EARTH grants max health %, armor, health regen, block and KB resist")
    void testEarthGrants() {
        PlayerData data = PlayerData.builder()
            .uuid(UUID.randomUUID())
            .username("TestPlayer")
            .fire(0)
            .water(0)
            .lightning(0)
            .earth(40)
            .wind(0)
            .voidAttr(0)
            .build();

        ComputedStats stats = calculator.calculateStats(data, BaseStats.defaults());

        // Health: 100 base (no flat HP from Earth anymore)
        assertEquals(100f, stats.getMaxHealth(), 0.01f);

        // Max Health %: 40 * 0.5 = 20%
        assertEquals(20f, stats.getMaxHealthPercent(), 0.01f);

        // Armor: 40 * 5.0 = 200
        assertEquals(200f, stats.getArmor(), 0.01f);

        // Health Regen: 40 * 0.2 = 8
        assertEquals(8f, stats.getHealthRegen(), 0.01f);

        // Block Chance: 40 * 0.2 = 8%
        assertEquals(8f, stats.getPassiveBlockChance(), 0.01f);

        // Knockback Resistance: 40 * 0.3 = 12%
        assertEquals(12f, stats.getKnockbackResistance(), 0.01f);
    }

    @Test
    @DisplayName("WIND grants evasion, accuracy, projectile damage, jump, and proj speed")
    void testWindGrants() {
        PlayerData data = PlayerData.builder()
            .uuid(UUID.randomUUID())
            .username("TestPlayer")
            .fire(0)
            .water(0)
            .lightning(0)
            .earth(0)
            .wind(20)
            .voidAttr(0)
            .build();

        ComputedStats stats = calculator.calculateStats(data, BaseStats.defaults());

        // Evasion: 20 * 5.0 = 100
        assertEquals(100f, stats.getEvasion(), 0.01f);

        // Accuracy: 10 base + (20 * 3.0) = 70
        assertEquals(70f, stats.getAccuracy(), 0.01f);

        // Projectile Damage %: 20 * 0.5 = 10%
        assertEquals(10f, stats.getProjectileDamagePercent(), 0.01f);

        // Jump Force %: 20 * 0.15 = 3%
        assertEquals(3f, stats.getJumpForceBonus(), 0.01f);

        // Projectile Speed %: 20 * 0.3 = 6%
        assertEquals(6f, stats.getProjectileSpeedPercent(), 0.01f);

        // Move speed should be 0 (move speed is Lightning now)
        assertEquals(0f, stats.getMovementSpeedPercent(), 0.01f);
    }

    @Test
    @DisplayName("VOID grants life steal, % hit as true damage, DoT, mana on kill, and effect duration")
    void testVoidGrants() {
        PlayerData data = PlayerData.builder()
            .uuid(UUID.randomUUID())
            .username("TestPlayer")
            .fire(0)
            .water(0)
            .lightning(0)
            .earth(0)
            .wind(0)
            .voidAttr(25)
            .build();

        ComputedStats stats = calculator.calculateStats(data, BaseStats.defaults());

        // Life Steal: 25 * 0.1 = 2.5%
        assertEquals(2.5f, stats.getLifeSteal(), 0.01f);

        // % Hit as True Damage: 25 * 0.05 = 1.25%
        assertEquals(1.25f, stats.getPercentHitAsTrueDamage(), 0.01f);

        // DoT Damage %: 25 * 0.3 = 7.5%
        assertEquals(7.5f, stats.getDotDamagePercent(), 0.01f);

        // Mana on Kill: 25 * 0.5 = 12.5
        assertEquals(12.5f, stats.getManaOnKill(), 0.01f);

        // Status Effect Duration: 25 * 0.3 = 7.5%
        assertEquals(7.5f, stats.getStatusEffectDuration(), 0.01f);

        // Spell damage should be 0 (spell damage is Water now)
        assertEquals(0f, stats.getSpellDamagePercent(), 0.01f);
    }

    @Test
    @DisplayName("Config values are used, not hardcoded")
    void testConfigValuesUsed() {
        // Modify config to non-default values (2x default)
        config.getAttributes().getFireGrants().setPhysicalDamagePercent(0.8f);
        config.getAttributes().getWaterGrants().setSpellDamagePercent(1.0f);
        config.getAttributes().getLightningGrants().setAttackSpeedPercent(0.6f);
        config.getAttributes().getEarthGrants().setArmor(10.0f);
        config.getAttributes().getWindGrants().setEvasion(10.0f);
        config.getAttributes().getVoidGrants().setLifeSteal(0.2f);

        PlayerData data = PlayerData.builder()
            .uuid(UUID.randomUUID())
            .username("TestPlayer")
            .fire(10)
            .water(10)
            .lightning(10)
            .earth(10)
            .wind(10)
            .voidAttr(10)
            .build();

        ComputedStats stats = calculator.calculateStats(data, BaseStats.defaults());

        // Physical Damage %: 10 * 0.8 = 8%
        assertEquals(8f, stats.getPhysicalDamagePercent(), 0.01f);

        // Spell Damage %: 10 * 1.0 = 10%
        assertEquals(10f, stats.getSpellDamagePercent(), 0.01f);

        // Attack Speed %: 10 * 0.6 = 6%
        assertEquals(6f, stats.getAttackSpeedPercent(), 0.01f);

        // Armor: 10 * 10.0 = 100
        assertEquals(100f, stats.getArmor(), 0.01f);

        // Evasion: 10 * 10.0 = 100
        assertEquals(100f, stats.getEvasion(), 0.01f);

        // Life Steal: 10 * 0.2 = 2%
        assertEquals(2f, stats.getLifeSteal(), 0.01f);
    }

    @Test
    @DisplayName("High attribute values calculate correctly")
    void testHighAttributeValues() {
        PlayerData data = PlayerData.builder()
            .uuid(UUID.randomUUID())
            .username("TestPlayer")
            .fire(100)
            .water(100)
            .lightning(100)
            .earth(100)
            .wind(100)
            .voidAttr(100)
            .build();

        ComputedStats stats = calculator.calculateStats(data, BaseStats.defaults());

        // FIRE: physDmg = 100*0.4=40, heavyAtkDmg=100*0.3=30, critMult=150+100*0.6=210
        assertEquals(40f, stats.getPhysicalDamagePercent(), 0.01f);
        assertEquals(30f, stats.getChargedAttackDamagePercent(), 0.01f);
        assertEquals(210f, stats.getCriticalMultiplier(), 0.01f);

        // WATER: spellDmg=100*0.5=50, mana=100*1.5=150, barrier=100*2.0=200
        assertEquals(50f, stats.getSpellDamagePercent(), 0.01f);
        assertEquals(150f, stats.getMaxMana(), 0.01f);
        assertEquals(200f, stats.getEnergyShield(), 0.01f);

        // LIGHTNING: atkSpd=100*0.3=30, moveSpd=100*0.15=15
        assertEquals(30f, stats.getAttackSpeedPercent(), 0.01f);
        assertEquals(15f, stats.getMovementSpeedPercent(), 0.01f);

        // EARTH: HP=100 base, HP%=100*0.5=50, armor=100*5.0=500
        assertEquals(100f, stats.getMaxHealth(), 0.01f);
        assertEquals(50f, stats.getMaxHealthPercent(), 0.01f);
        assertEquals(500f, stats.getArmor(), 0.01f);

        // WIND: evasion=100*5.0=500, accuracy=10+100*3.0=310
        assertEquals(500f, stats.getEvasion(), 0.01f);
        assertEquals(310f, stats.getAccuracy(), 0.01f);

        // VOID: lifeSteal=100*0.1=10, hitAsTrueDmg=100*0.05=5
        assertEquals(10f, stats.getLifeSteal(), 0.01f);
        assertEquals(5f, stats.getPercentHitAsTrueDamage(), 0.01f);
    }

    @Test
    @DisplayName("Base constants are correctly defined")
    void testBaseConstants() {
        assertEquals(5.0f, AttributeCalculator.BASE_CRIT_CHANCE);
        assertEquals(150.0f, AttributeCalculator.BASE_CRIT_MULTIPLIER);
    }
}
