package io.github.larsonix.trailoforbis.combat;

import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.elemental.ElementType;
import io.github.larsonix.trailoforbis.elemental.ElementalStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests defense edge cases through the full RPGDamageCalculator pipeline.
 *
 * <p>Covers scenarios that could cause NaN propagation, negative damage,
 * or incorrect tank vs. DPS balance at extreme values.
 */
class DefenseEdgeCasePipelineTest {

    private RPGDamageCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new RPGDamageCalculator();
    }

    // ==================== Armor Caps ====================

    @Nested
    @DisplayName("Armor Caps in Pipeline")
    class ArmorCaps {

        @Test
        @DisplayName("Extreme armor capped at 90% reduction in pipeline")
        void maxArmorReduction_cappedAt90Percent() {
            ComputedStats attackerStats = ComputedStats.builder()
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            ComputedStats defenderStats = ComputedStats.builder()
                .armor(99999f)
                .build();

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                100f, attackerStats, null, defenderStats, null,
                AttackType.MELEE, false, 1);

            // Max 90% reduction → at least 10% passes through
            assertTrue(result.physicalDamage() >= 9.5f,
                "Armor cap should let at least 10% through, got: " + result.physicalDamage());
            assertTrue(result.physicalDamage() <= 10.5f,
                "Should be ~10 damage (10% of 100), got: " + result.physicalDamage());
        }

        @Test
        @DisplayName("Higher attacker level reduces armor effectiveness")
        void armorWithLevelScaling_higherLevel_lessReduction() {
            ComputedStats attackerStats = ComputedStats.builder()
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            ComputedStats defenderStats = ComputedStats.builder()
                .armor(100f)
                .build();

            DamageBreakdown atLevel1 = calculator.calculate(
                100f, attackerStats, null, defenderStats, null,
                AttackType.MELEE, false, 1.0f, 1);

            DamageBreakdown atLevel50 = calculator.calculate(
                100f, attackerStats, null, defenderStats, null,
                AttackType.MELEE, false, 1.0f, 50);

            assertTrue(atLevel50.physicalDamage() > atLevel1.physicalDamage(),
                "Higher attacker level should reduce armor effectiveness. " +
                "Lv1=" + atLevel1.physicalDamage() + " Lv50=" + atLevel50.physicalDamage());
        }
    }

    // ==================== Physical Resistance ====================

    @Nested
    @DisplayName("Physical Resistance in Pipeline")
    class PhysicalResistance {

        @Test
        @DisplayName("Physical resistance applied after armor")
        void physicalResistance_appliedAfterArmor() {
            ComputedStats attackerStats = ComputedStats.builder()
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            ComputedStats defenderStats = ComputedStats.builder()
                .armor(100f)
                .physicalResistance(50f)
                .build();

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                100f, attackerStats, null, defenderStats, null,
                AttackType.MELEE, false, 1);

            // Armor first, then phys resist — multiplicative stacking
            // Should be less than just armor alone AND less than just resist alone
            ComputedStats armorOnly = ComputedStats.builder().armor(100f).build();
            ComputedStats resistOnly = ComputedStats.builder().physicalResistance(50f).build();

            DamageBreakdown armorResult = calculator.calculateWithForcedCrit(
                100f, attackerStats, null, armorOnly, null,
                AttackType.MELEE, false, 1);
            DamageBreakdown resistResult = calculator.calculateWithForcedCrit(
                100f, attackerStats, null, resistOnly, null,
                AttackType.MELEE, false, 1);

            assertTrue(result.physicalDamage() < armorResult.physicalDamage(),
                "Armor + resist should reduce more than armor alone");
            assertTrue(result.physicalDamage() < resistResult.physicalDamage(),
                "Armor + resist should reduce more than resist alone");
        }

        @Test
        @DisplayName("Physical resistance capped at 75%")
        void physicalResistance_cappedAt75() {
            ComputedStats attackerStats = ComputedStats.builder()
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            ComputedStats def75 = ComputedStats.builder().physicalResistance(75f).build();
            ComputedStats def100 = ComputedStats.builder().physicalResistance(100f).build();

            DamageBreakdown result75 = calculator.calculateWithForcedCrit(
                100f, attackerStats, null, def75, null,
                AttackType.MELEE, false, 1);
            DamageBreakdown result100 = calculator.calculateWithForcedCrit(
                100f, attackerStats, null, def100, null,
                AttackType.MELEE, false, 1);

            assertEquals(result75.physicalDamage(), result100.physicalDamage(), 0.01f,
                "100% resist should produce same result as 75% (capped)");
        }
    }

    // ==================== Null/Zero Edge Cases ====================

    @Nested
    @DisplayName("Null and Zero Edge Cases")
    class NullAndZero {

        @Test
        @DisplayName("Zero base damage produces zero, no NaN")
        void zeroDamage_noNaN() {
            ComputedStats attackerStats = ComputedStats.builder()
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            ComputedStats defenderStats = ComputedStats.builder()
                .armor(100f)
                .physicalResistance(50f)
                .build();

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                0f, attackerStats, null, defenderStats, null,
                AttackType.MELEE, false, 1);

            assertTrue(Float.isFinite(result.totalDamage()), "Result should not be NaN");
            assertEquals(0f, result.totalDamage(), 0.001f,
                "Zero base should produce zero output");
        }

        @Test
        @DisplayName("Null defender means no defenses applied")
        void nullDefender_noDefenses() {
            ComputedStats attackerStats = ComputedStats.builder()
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                100f, attackerStats, null, null, null,
                AttackType.MELEE, false, 1);

            assertEquals(100f, result.totalDamage(), 0.1f,
                "Null defender should mean full damage passes through");
        }

        @Test
        @DisplayName("Null attacker produces only base damage minus defenses")
        void nullAttacker_onlyDefenses() {
            ComputedStats defenderStats = ComputedStats.builder()
                .armor(100f)
                .build();

            DamageBreakdown result = calculator.calculate(
                100f, null, null, defenderStats, null,
                AttackType.UNKNOWN, false);

            // No attacker stats → no flat, no percent, no crit, no conversion
            // Just base damage minus armor
            assertTrue(result.totalDamage() < 100f,
                "Armor should reduce even with null attacker");
            assertTrue(result.totalDamage() > 0f,
                "Some damage should pass through");
            assertFalse(result.wasCritical(),
                "No attacker means no crit");
        }
    }

    // ==================== Large Values ====================

    @Nested
    @DisplayName("Large Value Safety")
    class LargeValues {

        @Test
        @DisplayName("Very large damage does not lose precision")
        void veryLargeDamage_noPrecisionLoss() {
            ComputedStats attackerStats = ComputedStats.builder()
                .physicalDamagePercent(100f)
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                1_000_000f, attackerStats, null, null, null,
                AttackType.MELEE, false, 1);

            // 1M × 2.0 = 2M
            float expected = 2_000_000f;
            float tolerance = expected * 0.01f; // 1% tolerance
            assertEquals(expected, result.totalDamage(), tolerance,
                "Large damage should not lose precision");
            assertTrue(Float.isFinite(result.totalDamage()));
        }

        @Test
        @DisplayName("Negative armor pen treated as zero")
        void negativeArmorPen_treatedAsZero() {
            ComputedStats attackerStats = ComputedStats.builder()
                .armorPenetration(-50f) // Negative should not increase armor
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            ComputedStats defenderStats = ComputedStats.builder().armor(100f).build();

            DamageBreakdown withNegPen = calculator.calculateWithForcedCrit(
                100f, attackerStats, null, defenderStats, null,
                AttackType.MELEE, false, 1);

            ComputedStats noPenStats = ComputedStats.builder()
                .criticalChance(0f).criticalMultiplier(100f).build();

            DamageBreakdown noPen = calculator.calculateWithForcedCrit(
                100f, noPenStats, null, defenderStats, null,
                AttackType.MELEE, false, 1);

            assertEquals(noPen.physicalDamage(), withNegPen.physicalDamage(), 0.01f,
                "Negative pen should have no effect (treated as 0)");
        }
    }
}
