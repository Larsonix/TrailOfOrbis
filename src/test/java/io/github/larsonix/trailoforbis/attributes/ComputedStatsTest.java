package io.github.larsonix.trailoforbis.attributes;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ComputedStats immutable builder pattern.
 */
public class ComputedStatsTest {

    @Test
    @DisplayName("Builder defaults all fields to 0.0f")
    void builderDefaults() {
        ComputedStats stats = ComputedStats.builder().build();

        // Core
        assertEquals(0.0f, stats.getMaxHealth());
        assertEquals(0.0f, stats.getMaxMana());
        assertEquals(0.0f, stats.getMaxStamina());

        // Flat Damage
        assertEquals(0.0f, stats.getPhysicalDamage());
        assertEquals(0.0f, stats.getSpellDamage());
        assertEquals(0.0f, stats.getFireDamage());
        assertEquals(0.0f, stats.getWaterDamage());
        assertEquals(0.0f, stats.getLightningDamage());

        // Percent Damage
        assertEquals(0.0f, stats.getPhysicalDamagePercent());
        assertEquals(0.0f, stats.getSpellDamagePercent());
        assertEquals(0.0f, stats.getFireDamagePercent());
        assertEquals(0.0f, stats.getWaterDamagePercent());
        assertEquals(0.0f, stats.getLightningDamagePercent());

        // Critical
        assertEquals(0.0f, stats.getCriticalChance());
        assertEquals(0.0f, stats.getCriticalMultiplier());

        // Attack Types
        assertEquals(0.0f, stats.getMeleeDamagePercent());
        assertEquals(0.0f, stats.getProjectileDamagePercent());

        // Speed
        assertEquals(0.0f, stats.getMovementSpeedPercent());
        assertEquals(0.0f, stats.getAttackSpeedPercent());

        // Defense
        assertEquals(0.0f, stats.getArmor());
        assertEquals(0.0f, stats.getEvasion());
        assertEquals(0.0f, stats.getEnergyShield());

        // Resistances
        assertEquals(0.0f, stats.getFireResistance());
        assertEquals(0.0f, stats.getWaterResistance());
        assertEquals(0.0f, stats.getLightningResistance());
        assertEquals(0.0f, stats.getVoidResistance());

        // Regen
        assertEquals(0.0f, stats.getHealthRegen());
        assertEquals(0.0f, stats.getManaRegen());

        // Utility
        assertEquals(0.0f, stats.getExperienceGainPercent());
    }

    @Test
    @DisplayName("Builder correctly sets values")
    void builderSetsValues() {
        ComputedStats stats = ComputedStats.builder()
            .maxHealth(150f)
            .maxMana(100f)
            .maxStamina(50f)
            .physicalDamage(10f)
            .spellDamage(15f)
            .fireDamage(5f)
            .waterDamage(3f)
            .lightningDamage(7f)
            .physicalDamagePercent(20f)
            .spellDamagePercent(25f)
            .fireDamagePercent(10f)
            .waterDamagePercent(8f)
            .lightningDamagePercent(12f)
            .criticalChance(5f)
            .criticalMultiplier(150f)
            .meleeDamagePercent(15f)
            .projectileDamagePercent(10f)
            .movementSpeedPercent(10f)
            .attackSpeedPercent(5f)
            .armor(50f)
            .evasion(30f)
            .energyShield(25f)
            .fireResistance(20f)
            .waterResistance(15f)
            .lightningResistance(10f)
            .voidResistance(5f)
            .healthRegen(2.5f)
            .manaRegen(1.5f)
            .build();

        assertEquals(150f, stats.getMaxHealth());
        assertEquals(100f, stats.getMaxMana());
        assertEquals(50f, stats.getMaxStamina());
        assertEquals(10f, stats.getPhysicalDamage());
        assertEquals(15f, stats.getSpellDamage());
        assertEquals(5f, stats.getFireDamage());
        assertEquals(3f, stats.getWaterDamage());
        assertEquals(7f, stats.getLightningDamage());
        assertEquals(20f, stats.getPhysicalDamagePercent());
        assertEquals(25f, stats.getSpellDamagePercent());
        assertEquals(10f, stats.getFireDamagePercent());
        assertEquals(8f, stats.getWaterDamagePercent());
        assertEquals(12f, stats.getLightningDamagePercent());
        assertEquals(5f, stats.getCriticalChance());
        assertEquals(150f, stats.getCriticalMultiplier());
        assertEquals(15f, stats.getMeleeDamagePercent());
        assertEquals(10f, stats.getProjectileDamagePercent());
        assertEquals(10f, stats.getMovementSpeedPercent());
        assertEquals(5f, stats.getAttackSpeedPercent());
        assertEquals(50f, stats.getArmor());
        assertEquals(30f, stats.getEvasion());
        assertEquals(25f, stats.getEnergyShield());
        assertEquals(20f, stats.getFireResistance());
        assertEquals(15f, stats.getWaterResistance());
        assertEquals(10f, stats.getLightningResistance());
        assertEquals(5f, stats.getVoidResistance());
        assertEquals(2.5f, stats.getHealthRegen());
        assertEquals(1.5f, stats.getManaRegen());
    }

    @Test
    @DisplayName("toBuilder preserves values when modifying")
    void toBuilderPreservesValues() {
        ComputedStats original = ComputedStats.builder()
            .maxHealth(100f)
            .maxMana(50f)
            .criticalChance(5f)
            .armor(30f)
            .fireResistance(15f)
            .build();

        // Modify only maxHealth
        ComputedStats modified = original.toBuilder()
            .maxHealth(200f)
            .build();

        // maxHealth should be updated
        assertEquals(200f, modified.getMaxHealth());

        // All other values should be preserved
        assertEquals(50f, modified.getMaxMana());
        assertEquals(5f, modified.getCriticalChance());
        assertEquals(30f, modified.getArmor());
        assertEquals(15f, modified.getFireResistance());

        // Original should be unchanged (immutability)
        assertEquals(100f, original.getMaxHealth());
    }

    @Test
    @DisplayName("copy creates new instance with same values")
    void copyCreatesNewInstance() {
        ComputedStats original = ComputedStats.builder()
            .maxHealth(100f)
            .maxMana(50f)
            .criticalChance(5f)
            .build();

        ComputedStats copy = original.copy();

        // Should be equal
        assertEquals(original, copy);

        // But not the same instance
        assertNotSame(original, copy);

        // Values should match
        assertEquals(original.getMaxHealth(), copy.getMaxHealth());
        assertEquals(original.getMaxMana(), copy.getMaxMana());
        assertEquals(original.getCriticalChance(), copy.getCriticalChance());
    }

    @Test
    @DisplayName("All getters return correct values")
    void allGettersWork() {
        // Test that all 30 stats are accessible
        ComputedStats stats = ComputedStats.builder()
            // Core (3)
            .maxHealth(1f)
            .maxMana(2f)
            .maxStamina(3f)
            // Flat Damage (5)
            .physicalDamage(4f)
            .spellDamage(5f)
            .fireDamage(6f)
            .waterDamage(7f)
            .lightningDamage(8f)
            // Percent Damage (5)
            .physicalDamagePercent(9f)
            .spellDamagePercent(10f)
            .fireDamagePercent(11f)
            .waterDamagePercent(12f)
            .lightningDamagePercent(13f)
            // Critical (2)
            .criticalChance(14f)
            .criticalMultiplier(15f)
            // Attack Types (2)
            .meleeDamagePercent(16f)
            .projectileDamagePercent(17f)
            // Speed (2)
            .movementSpeedPercent(19f)
            .attackSpeedPercent(20f)
            // Defense (3)
            .armor(22f)
            .evasion(23f)
            .energyShield(24f)
            // Resistances (4)
            .fireResistance(25f)
            .waterResistance(26f)
            .lightningResistance(27f)
            .voidResistance(28f)
            // Regen (2)
            .healthRegen(29f)
            .manaRegen(30f)
            .build();

        // Verify all 30 getters
        assertEquals(1f, stats.getMaxHealth());
        assertEquals(2f, stats.getMaxMana());
        assertEquals(3f, stats.getMaxStamina());
        assertEquals(4f, stats.getPhysicalDamage());
        assertEquals(5f, stats.getSpellDamage());
        assertEquals(6f, stats.getFireDamage());
        assertEquals(7f, stats.getWaterDamage());
        assertEquals(8f, stats.getLightningDamage());
        assertEquals(9f, stats.getPhysicalDamagePercent());
        assertEquals(10f, stats.getSpellDamagePercent());
        assertEquals(11f, stats.getFireDamagePercent());
        assertEquals(12f, stats.getWaterDamagePercent());
        assertEquals(13f, stats.getLightningDamagePercent());
        assertEquals(14f, stats.getCriticalChance());
        assertEquals(15f, stats.getCriticalMultiplier());
        assertEquals(16f, stats.getMeleeDamagePercent());
        assertEquals(17f, stats.getProjectileDamagePercent());
        assertEquals(19f, stats.getMovementSpeedPercent());
        assertEquals(20f, stats.getAttackSpeedPercent());
        assertEquals(22f, stats.getArmor());
        assertEquals(23f, stats.getEvasion());
        assertEquals(24f, stats.getEnergyShield());
        assertEquals(25f, stats.getFireResistance());
        assertEquals(26f, stats.getWaterResistance());
        assertEquals(27f, stats.getLightningResistance());
        assertEquals(28f, stats.getVoidResistance());
        assertEquals(29f, stats.getHealthRegen());
        assertEquals(30f, stats.getManaRegen());
    }

    @Test
    @DisplayName("equals and hashCode work correctly")
    void equalsAndHashCode() {
        ComputedStats stats1 = ComputedStats.builder()
            .maxHealth(100f)
            .criticalChance(5f)
            .build();

        ComputedStats stats2 = ComputedStats.builder()
            .maxHealth(100f)
            .criticalChance(5f)
            .build();

        ComputedStats stats3 = ComputedStats.builder()
            .maxHealth(100f)
            .criticalChance(10f) // Different
            .build();

        assertEquals(stats1, stats2);
        assertEquals(stats1.hashCode(), stats2.hashCode());
        assertNotEquals(stats1, stats3);
    }

    @Test
    @DisplayName("toString provides readable output")
    void toStringReadable() {
        ComputedStats stats = ComputedStats.builder()
            .maxHealth(100f)
            .maxMana(50f)
            .physicalDamage(10f)
            .spellDamage(5f)
            .criticalChance(5f)
            .armor(30f)
            .build();

        String str = stats.toString();
        assertNotNull(str);
        assertTrue(str.contains("hp=100.0"));
        assertTrue(str.contains("mana=50.0"));
        assertTrue(str.contains("crit=5.0%"));
    }

    @Test
    @DisplayName("experienceGainPercent round-trips through builder and toBuilder")
    void experienceGainPercentRoundTrips() {
        ComputedStats stats = ComputedStats.builder()
            .experienceGainPercent(25f)
            .build();

        assertEquals(25f, stats.getExperienceGainPercent());

        // toBuilder preserves value
        ComputedStats copy = stats.toBuilder().build();
        assertEquals(25f, copy.getExperienceGainPercent());
        assertEquals(stats, copy);

        // Modifying via toBuilder works
        ComputedStats modified = stats.toBuilder()
            .experienceGainPercent(50f)
            .build();
        assertEquals(50f, modified.getExperienceGainPercent());
        assertNotEquals(stats, modified);
    }
}
