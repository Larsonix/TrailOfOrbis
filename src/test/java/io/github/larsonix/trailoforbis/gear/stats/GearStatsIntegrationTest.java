package io.github.larsonix.trailoforbis.gear.stats;

import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig.StatType;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.GearModifier;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.gear.model.ModifierType;
import io.github.larsonix.trailoforbis.gear.stats.GearStatCalculator.GearBonuses;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the gear stat calculation system.
 *
 * <p>These tests verify the complete flow from GearData -> GearBonuses -> ComputedStats.
 */
class GearStatsIntegrationTest {

    private GearStatApplier applier;

    @BeforeEach
    void setUp() {
        applier = new GearStatApplier();
    }

    @Test
    @DisplayName("Integration - flat damage bonus applied to stats")
    void integration_FlatDamageBonus_AppliedToStats() {
        ComputedStats stats = ComputedStats.builder()
            .physicalDamage(50)
            .build();

        GearBonuses bonuses = new GearBonuses(
            Map.of("physical_damage", 25.0),
            Map.of(),
            0.0,
            null,
            false
        );

        applier.apply(stats, bonuses);

        assertEquals(75, stats.getPhysicalDamage(), 0.01);
    }

    @Test
    @DisplayName("Integration - percent damage bonus applied to stats")
    void integration_PercentDamageBonus_AppliedToStats() {
        ComputedStats stats = ComputedStats.builder()
            .physicalDamagePercent(10)
            .build();

        GearBonuses bonuses = new GearBonuses(
            Map.of(),
            Map.of("physical_damage", 15.0),  // Adds to percent field
            0.0,
            null,
            false
        );

        applier.apply(stats, bonuses);

        assertEquals(25, stats.getPhysicalDamagePercent(), 0.01);
    }

    @Test
    @DisplayName("Integration - mixed bonuses from multiple gear pieces")
    void integration_MixedBonuses_MultipleGear() {
        ComputedStats stats = ComputedStats.builder()
            .maxHealth(100)
            .physicalDamage(20)
            .criticalChance(5)
            .build();

        // Simulate bonuses from multiple gear pieces summed together
        GearBonuses bonuses = new GearBonuses(
            Map.of("max_health", 50.0, "physical_damage", 30.0),
            Map.of("crit_chance", 10.0, "max_health", 20.0),
            0.0,
            null,
            false
        );

        applier.apply(stats, bonuses);

        // max_health: 100 + 50 = 150, then * 1.2 = 180
        assertEquals(180, stats.getMaxHealth(), 0.01);
        assertEquals(50, stats.getPhysicalDamage(), 0.01);
        assertEquals(15, stats.getCriticalChance(), 0.01);
    }

    @Test
    @DisplayName("Integration - quality multiplier affects modifier values")
    void integration_QualityMultiplier_AffectsValues() {
        // Create gear data with quality 100 (1.5x multiplier)
        GearModifier prefix = GearModifier.of(
            "sharp", "Sharp", ModifierType.PREFIX,
            "physical_damage", GearModifier.STAT_TYPE_FLAT, 10.0
        );

        GearData highQuality = GearData.builder()
            .level(50)
            .rarity(GearRarity.UNCOMMON)
            .quality(100)  // 1.5x multiplier
            .prefixes(List.of(prefix))
            .build();

        double qualityMultiplier = highQuality.qualityMultiplier();
        double adjustedValue = prefix.value() * qualityMultiplier;

        // 10 * 1.5 = 15
        assertEquals(15.0, adjustedValue, 0.01);
    }

    @Test
    @DisplayName("Integration - low quality reduces modifier effectiveness")
    void integration_LowQuality_ReducesValues() {
        // Create gear data with quality 25 (0.75x multiplier)
        GearModifier prefix = GearModifier.of(
            "sharp", "Sharp", ModifierType.PREFIX,
            "physical_damage", GearModifier.STAT_TYPE_FLAT, 10.0
        );

        GearData lowQuality = GearData.builder()
            .level(50)
            .rarity(GearRarity.UNCOMMON)
            .quality(25)  // 0.75x multiplier
            .prefixes(List.of(prefix))
            .build();

        double qualityMultiplier = lowQuality.qualityMultiplier();
        double adjustedValue = prefix.value() * qualityMultiplier;

        // 10 * 0.75 = 7.5
        assertEquals(7.5, adjustedValue, 0.01);
    }

    @Test
    @DisplayName("Integration - resistance stats applied correctly")
    void integration_ResistanceStats_Applied() {
        ComputedStats stats = ComputedStats.builder()
            .fireResistance(0)
            .waterResistance(0)
            .lightningResistance(0)
            .voidResistance(0)
            .build();

        GearBonuses bonuses = new GearBonuses(
            Map.of(),
            Map.of(
                "fire_resistance", 15.0,
                "water_resistance", 20.0,
                "lightning_resistance", 10.0,
                "void_resistance", 5.0
            ),
            0.0,
            null,
            false
        );

        applier.apply(stats, bonuses);

        assertEquals(15, stats.getFireResistance(), 0.01);
        assertEquals(20, stats.getWaterResistance(), 0.01);
        assertEquals(10, stats.getLightningResistance(), 0.01);
        assertEquals(5, stats.getVoidResistance(), 0.01);
    }

    @Test
    @DisplayName("Integration - defense stats applied correctly")
    void integration_DefenseStats_Applied() {
        ComputedStats stats = ComputedStats.builder()
            .armor(50)
            .evasion(10)
            .blockChance(5)
            .build();

        GearBonuses bonuses = new GearBonuses(
            Map.of("armor", 100.0, "evasion", 20.0),
            Map.of("block_chance", 10.0),
            0.0,
            null,
            false
        );

        applier.apply(stats, bonuses);

        assertEquals(150, stats.getArmor(), 0.01);
        assertEquals(30, stats.getEvasion(), 0.01);
        assertEquals(15, stats.getBlockChance(), 0.01);
    }

    @Test
    @DisplayName("Integration - regen stats applied correctly")
    void integration_RegenStats_Applied() {
        ComputedStats stats = ComputedStats.builder()
            .healthRegen(1)
            .manaRegen(0.5f)
            .staminaRegen(2)
            .build();

        GearBonuses bonuses = new GearBonuses(
            Map.of("health_regen", 2.0, "mana_regen", 1.5, "stamina_regen", 3.0),
            Map.of(),
            0.0,
            null,
            false
        );

        applier.apply(stats, bonuses);

        assertEquals(3, stats.getHealthRegen(), 0.01);
        assertEquals(2, stats.getManaRegen(), 0.01);
        assertEquals(5, stats.getStaminaRegen(), 0.01);
    }

    @Test
    @DisplayName("Integration - StatMapping directly applies to ComputedStats")
    void integration_StatMapping_DirectApply() {
        ComputedStats stats = ComputedStats.builder()
            .maxHealth(100)
            .physicalDamage(20)
            .criticalChance(5)
            .build();

        // Apply stats directly via StatMapping
        StatMapping.apply(stats, "max_health", 50.0, StatType.FLAT);
        StatMapping.apply(stats, "physical_damage", 10.0, StatType.FLAT);
        StatMapping.apply(stats, "crit_chance", 5.0, StatType.PERCENT);

        assertEquals(150, stats.getMaxHealth(), 0.01);
        assertEquals(30, stats.getPhysicalDamage(), 0.01);
        assertEquals(10, stats.getCriticalChance(), 0.01);
    }

    @Test
    @DisplayName("Integration - full gear set simulation")
    void integration_FullGearSetSimulation() {
        // Start with base stats (from attributes)
        ComputedStats stats = ComputedStats.builder()
            .maxHealth(100)
            .maxMana(50)
            .physicalDamage(25)
            .armor(30)
            .criticalChance(5)
            .build();

        // Simulate combined bonuses from a full gear set:
        // - Helmet: +20 max health, +10% max health
        // - Chest: +50 armor, +10% armor
        // - Weapon: +15 physical damage, +5% crit chance, 175 base damage
        // - Boots: +10% movement speed
        GearBonuses totalBonuses = new GearBonuses(
            Map.of(
                "max_health", 20.0,
                "armor", 50.0,
                "physical_damage", 15.0
            ),
            Map.of(
                "max_health", 10.0,
                "armor", 10.0,
                "crit_chance", 5.0,
                "movement_speed_percent", 10.0
            ),
            175.0,  // Weapon base damage from implicit
            null,   // No weapon item ID
            true    // Is RPG gear
        );

        applier.apply(stats, totalBonuses);

        // max_health: 100 + 20 = 120, then * 1.1 = 132
        assertEquals(132, stats.getMaxHealth(), 0.01);
        // armor: 30 + 50 = 80, then armor_percent applied separately
        assertEquals(80, stats.getArmor(), 0.01);
        assertEquals(10, stats.getArmorPercent(), 0.01);
        // physical_damage: 25 + 15 = 40
        assertEquals(40, stats.getPhysicalDamage(), 0.01);
        // crit_chance: 5 + 5 = 10
        assertEquals(10, stats.getCriticalChance(), 0.01);
        // movement_speed_percent: 0 + 10 = 10
        assertEquals(10, stats.getMovementSpeedPercent(), 0.01);
    }
}
