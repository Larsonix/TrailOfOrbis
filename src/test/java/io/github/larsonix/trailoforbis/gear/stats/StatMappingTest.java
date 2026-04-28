package io.github.larsonix.trailoforbis.gear.stats;

import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig.StatType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for StatMapping.
 */
class StatMappingTest {

    @Test
    @DisplayName("apply - flat physical damage adds value")
    void apply_FlatPhysicalDamage_AddsValue() {
        ComputedStats stats = new ComputedStats();
        stats.setPhysicalDamage(10);

        StatMapping.apply(stats, "physical_damage", 5.0, StatType.FLAT);

        assertEquals(15, stats.getPhysicalDamage(), 0.01);
    }

    @Test
    @DisplayName("apply - percent physical damage adds to percent field")
    void apply_PercentPhysicalDamage_AddsToPercent() {
        ComputedStats stats = new ComputedStats();
        stats.setPhysicalDamagePercent(10);

        StatMapping.apply(stats, "physical_damage_percent", 5.0, StatType.PERCENT);

        assertEquals(15, stats.getPhysicalDamagePercent(), 0.01);
    }

    @Test
    @DisplayName("apply - flat max health adds value")
    void apply_FlatMaxHealth_AddsValue() {
        ComputedStats stats = new ComputedStats();
        stats.setMaxHealth(100);

        StatMapping.apply(stats, "max_health", 50.0, StatType.FLAT);

        assertEquals(150, stats.getMaxHealth(), 0.01);
    }

    @Test
    @DisplayName("apply - percent max health multiplies value")
    void apply_PercentMaxHealth_MultipliesValue() {
        ComputedStats stats = new ComputedStats();
        stats.setMaxHealth(100);

        StatMapping.apply(stats, "max_health", 50.0, StatType.PERCENT);

        assertEquals(150, stats.getMaxHealth(), 0.01);  // 100 * (1 + 0.5)
    }

    @Test
    @DisplayName("apply - crit chance is percent stat")
    void apply_CritChance_IsPercentStat() {
        ComputedStats stats = new ComputedStats();
        stats.setCritChance(5);

        StatMapping.apply(stats, "crit_chance", 3.0, StatType.PERCENT);

        assertEquals(8, stats.getCriticalChance(), 0.01);
    }

    @Test
    @DisplayName("apply - unknown stat is silently ignored")
    void apply_UnknownStat_SilentlyIgnored() {
        ComputedStats stats = new ComputedStats();

        // Should not throw
        assertDoesNotThrow(() ->
            StatMapping.apply(stats, "unknown_stat", 10.0, StatType.FLAT));
    }

    @Test
    @DisplayName("apply - stat ID is case insensitive")
    void apply_CaseInsensitive() {
        ComputedStats stats = new ComputedStats();
        stats.setPhysicalDamage(10);

        StatMapping.apply(stats, "PHYSICAL_DAMAGE", 5.0, StatType.FLAT);

        assertEquals(15, stats.getPhysicalDamage(), 0.01);
    }

    @Test
    @DisplayName("apply - null stats throws exception")
    void apply_NullStats_ThrowsException() {
        assertThrows(NullPointerException.class, () ->
            StatMapping.apply(null, "physical_damage", 5.0, StatType.FLAT));
    }

    @Test
    @DisplayName("isSupported - known flat stat returns true")
    void isSupported_KnownFlat_True() {
        assertTrue(StatMapping.isSupported("physical_damage", StatType.FLAT));
    }

    @Test
    @DisplayName("isSupported - known percent stat returns true")
    void isSupported_KnownPercent_True() {
        assertTrue(StatMapping.isSupported("crit_chance", StatType.PERCENT));
    }

    @Test
    @DisplayName("isSupported - unknown stat returns false")
    void isSupported_Unknown_False() {
        assertFalse(StatMapping.isSupported("unknown_stat", StatType.FLAT));
    }

    @Test
    @DisplayName("getSupportedFlatStats - contains physical damage")
    void getSupportedFlatStats_ContainsPhysicalDamage() {
        assertTrue(StatMapping.getSupportedFlatStats().contains("physical_damage"));
    }

    @Test
    @DisplayName("apply - all resistances work")
    void apply_AllResistances_Work() {
        ComputedStats stats = new ComputedStats();

        StatMapping.apply(stats, "fire_resistance", 10.0, StatType.PERCENT);
        StatMapping.apply(stats, "water_resistance", 15.0, StatType.PERCENT);
        StatMapping.apply(stats, "lightning_resistance", 20.0, StatType.PERCENT);
        StatMapping.apply(stats, "void_resistance", 25.0, StatType.PERCENT);

        assertEquals(10, stats.getFireResistance(), 0.01);
        assertEquals(15, stats.getWaterResistance(), 0.01);
        assertEquals(20, stats.getLightningResistance(), 0.01);
        assertEquals(25, stats.getVoidResistance(), 0.01);
    }

    @Test
    @DisplayName("apply - all regens work")
    void apply_AllRegens_Work() {
        ComputedStats stats = new ComputedStats();

        StatMapping.apply(stats, "health_regen", 1.0, StatType.FLAT);
        StatMapping.apply(stats, "mana_regen", 0.5, StatType.FLAT);
        StatMapping.apply(stats, "stamina_regen", 0.3, StatType.FLAT);

        assertEquals(1.0, stats.getHealthRegen(), 0.01);
        assertEquals(0.5, stats.getManaRegen(), 0.01);
        assertEquals(0.3, stats.getStaminaRegen(), 0.01);
    }

    @Test
    @DisplayName("apply - stamina regen percent works")
    void apply_StaminaRegenPercent_Works() {
        ComputedStats stats = new ComputedStats();
        stats.setStaminaRegenPercent(5);

        StatMapping.apply(stats, "stamina_regen_percent", 10.0, StatType.PERCENT);

        assertEquals(15, stats.getStaminaRegenPercent(), 0.01);
    }

    @Test
    @DisplayName("apply - stamina regen start delay works")
    void apply_StaminaRegenStartDelay_Works() {
        ComputedStats stats = new ComputedStats();
        stats.setStaminaRegenStartDelay(3);

        StatMapping.apply(stats, "stamina_regen_start_delay", 5.0, StatType.PERCENT);

        assertEquals(8, stats.getStaminaRegenStartDelay(), 0.01);
    }

    @Test
    @DisplayName("apply - penetration stats work")
    void apply_Penetration_Works() {
        ComputedStats stats = new ComputedStats();

        StatMapping.apply(stats, "armor_penetration", 10.0, StatType.FLAT);
        StatMapping.apply(stats, "fire_penetration", 5.0, StatType.PERCENT);

        assertEquals(10, stats.getArmorPenetration(), 0.01);
        assertEquals(5, stats.getFirePenetration(), 0.01);
    }
}
