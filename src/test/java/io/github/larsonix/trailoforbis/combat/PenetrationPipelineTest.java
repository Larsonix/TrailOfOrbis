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
 * Tests penetration mechanics through the RPGDamageCalculator pipeline (Step 9).
 *
 * <p>Three penetration types:
 * <ul>
 *   <li>Armor penetration — 50% floor (min armor effectiveness)</li>
 *   <li>Elemental penetration — per-element, uses {@code max(-200, min(75, res - pen))}</li>
 *   <li>Spell penetration — added to element pen for SPELL attacks only</li>
 * </ul>
 *
 * <p>Negative effective resistance amplifies damage (e.g., -50% resist = 1.5x damage).
 */
class PenetrationPipelineTest {

    private RPGDamageCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new RPGDamageCalculator();
    }

    // ==================== Armor Penetration ====================

    @Nested
    @DisplayName("Armor Penetration Through Pipeline")
    class ArmorPenetration {

        @Test
        @DisplayName("Armor penetration reduces effective armor")
        void armorPenetration_reducesEffectiveArmor() {
            ComputedStats attackerStats = ComputedStats.builder()
                .armorPenetration(25f) // 25% pen
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            ComputedStats defenderStats = ComputedStats.builder()
                .armor(200f)
                .build();

            DamageBreakdown withPen = calculator.calculateWithForcedCrit(
                100f, attackerStats, null, defenderStats, null,
                AttackType.MELEE, false, 1);

            // Without pen for comparison
            ComputedStats noPenStats = ComputedStats.builder()
                .criticalChance(0f).criticalMultiplier(100f).build();
            DamageBreakdown noPen = calculator.calculateWithForcedCrit(
                100f, noPenStats, null, defenderStats, null,
                AttackType.MELEE, false, 1);

            assertTrue(withPen.physicalDamage() > noPen.physicalDamage(),
                "Armor pen should increase physical damage through armor");
        }

        @Test
        @DisplayName("Armor penetration has 50% floor (can't reduce below 50% effectiveness)")
        void armorPenetration_floorAt50Percent() {
            ComputedStats pen50 = ComputedStats.builder()
                .armorPenetration(50f)
                .criticalChance(0f).criticalMultiplier(100f)
                .build();

            ComputedStats pen100 = ComputedStats.builder()
                .armorPenetration(100f) // Should be same as 50% due to floor
                .criticalChance(0f).criticalMultiplier(100f)
                .build();

            ComputedStats defenderStats = ComputedStats.builder().armor(200f).build();

            DamageBreakdown result50 = calculator.calculateWithForcedCrit(
                100f, pen50, null, defenderStats, null, AttackType.MELEE, false, 1);
            DamageBreakdown result100 = calculator.calculateWithForcedCrit(
                100f, pen100, null, defenderStats, null, AttackType.MELEE, false, 1);

            assertEquals(result50.physicalDamage(), result100.physicalDamage(), 0.01f,
                "100% pen should produce same result as 50% pen (floor)");
        }
    }

    // ==================== Elemental Penetration ====================

    @Nested
    @DisplayName("Elemental Penetration")
    class ElementalPen {

        @Test
        @DisplayName("Fire pen 30 reduces 50% fire resist to 20% effective")
        void elementalPenetration_reducesResistance() {
            ComputedStats attackerStats = ComputedStats.builder()
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();
            ElementalStats attackerElemental = new ElementalStats();
            attackerElemental.setFlatDamage(ElementType.FIRE, 100.0);
            attackerElemental.setPenetration(ElementType.FIRE, 30.0); // 30% pen

            ComputedStats defenderStats = ComputedStats.builder().build();
            ElementalStats defenderElemental = new ElementalStats();
            defenderElemental.setResistance(ElementType.FIRE, 50.0);

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                0f, attackerStats, attackerElemental, defenderStats, defenderElemental,
                AttackType.MELEE, false, 1);

            // Effective resist = min(75, 50 - 30) = 20%
            // Fire: 100 × (1 - 20/100) = 80
            assertEquals(80f, result.getElementalDamage(ElementType.FIRE), 1f,
                "50% resist - 30% pen = 20% effective → 80 fire damage");
        }

        @Test
        @DisplayName("Penetration pushes resistance negative (damage amplification)")
        void elementalPenetration_pushesNegative() {
            ComputedStats attackerStats = ComputedStats.builder()
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();
            ElementalStats attackerElemental = new ElementalStats();
            attackerElemental.setFlatDamage(ElementType.FIRE, 100.0);
            attackerElemental.setPenetration(ElementType.FIRE, 80.0); // 80% pen

            ComputedStats defenderStats = ComputedStats.builder().build();
            ElementalStats defenderElemental = new ElementalStats();
            defenderElemental.setResistance(ElementType.FIRE, 30.0);

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                0f, attackerStats, attackerElemental, defenderStats, defenderElemental,
                AttackType.MELEE, false, 1);

            // Effective resist = max(-200, min(75, 30 - 80)) = max(-200, -50) = -50%
            // Fire: 100 × (1 - (-50/100)) = 100 × 1.5 = 150
            assertEquals(150f, result.getElementalDamage(ElementType.FIRE), 1f,
                "Negative resistance should amplify: -50% resist → 1.5x damage");
        }

        @Test
        @DisplayName("Penetration floors at -200% effective resistance")
        void elementalPenetration_floorsAtMinus200() {
            ComputedStats attackerStats = ComputedStats.builder()
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();
            ElementalStats attackerElemental = new ElementalStats();
            attackerElemental.setFlatDamage(ElementType.FIRE, 100.0);
            attackerElemental.setPenetration(ElementType.FIRE, 300.0); // Massive pen

            ComputedStats defenderStats = ComputedStats.builder().build();
            ElementalStats defenderElemental = new ElementalStats();
            defenderElemental.setResistance(ElementType.FIRE, 50.0);

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                0f, attackerStats, attackerElemental, defenderStats, defenderElemental,
                AttackType.MELEE, false, 1);

            // Effective = max(-200, min(75, 50 - 300)) = max(-200, -250) = -200%
            // Fire: 100 × (1 - (-200/100)) = 100 × 3.0 = 300
            assertEquals(300f, result.getElementalDamage(ElementType.FIRE), 1f,
                "Resistance floored at -200%: 100 × 3.0 = 300");
        }
    }

    // ==================== Negative Resistance ====================

    @Nested
    @DisplayName("Negative Resistance Amplification")
    class NegativeResistance {

        @Test
        @DisplayName("Negative resistance amplifies damage in pipeline")
        void negativeResistance_amplifies_inPipeline() {
            ComputedStats attackerStats = ComputedStats.builder()
                .fireConversion(100f) // All phys → fire
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            ComputedStats defenderStats = ComputedStats.builder().build();
            ElementalStats defenderElemental = new ElementalStats();
            defenderElemental.setResistance(ElementType.FIRE, -50.0); // Negative!

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                100f, attackerStats, null, defenderStats, defenderElemental,
                AttackType.MELEE, false, 1);

            // 100 → 100 fire (conversion)
            // Fire resist = max(-200, min(75, -50)) = -50%
            // Fire: 100 × (1 - (-50/100)) = 100 × 1.5 = 150
            assertEquals(150f, result.getElementalDamage(ElementType.FIRE), 1f,
                "-50% resist → 1.5x fire damage");
        }
    }

    // ==================== Independent Per-Element Penetration ====================

    @Nested
    @DisplayName("Independent Per-Element Penetration")
    class IndependentPen {

        @Test
        @DisplayName("Fire pen does not affect water resistance")
        void multipleElements_independentPenetration() {
            ComputedStats attackerStats = ComputedStats.builder()
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();
            ElementalStats attackerElemental = new ElementalStats();
            attackerElemental.setFlatDamage(ElementType.FIRE, 100.0);
            attackerElemental.setFlatDamage(ElementType.WATER, 100.0);
            attackerElemental.setPenetration(ElementType.FIRE, 40.0); // Only fire pen
            // No water pen

            ComputedStats defenderStats = ComputedStats.builder().build();
            ElementalStats defenderElemental = new ElementalStats();
            defenderElemental.setResistance(ElementType.FIRE, 50.0);
            defenderElemental.setResistance(ElementType.WATER, 50.0);

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                0f, attackerStats, attackerElemental, defenderStats, defenderElemental,
                AttackType.MELEE, false, 1);

            // Fire: 50 - 40 = 10% effective → 100 × 0.9 = 90
            assertEquals(90f, result.getElementalDamage(ElementType.FIRE), 1f);
            // Water: 50 - 0 = 50% effective → 100 × 0.5 = 50
            assertEquals(50f, result.getElementalDamage(ElementType.WATER), 1f,
                "Water should not be affected by fire pen");
        }
    }

    // ==================== Defense Stacking ====================

    @Nested
    @DisplayName("Defense Stacking")
    class DefenseStacking {

        @Test
        @DisplayName("Armor and resistance stack multiplicatively on physical")
        void armorAndResistance_stackMultiplicatively() {
            ComputedStats attackerStats = ComputedStats.builder()
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            ComputedStats defenderStats = ComputedStats.builder()
                .armor(100f)
                .physicalResistance(50f) // 50% resist after armor
                .build();

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                100f, attackerStats, null, defenderStats, null,
                AttackType.MELEE, false, 1);

            // Armor: 100 / (100 + 9*1 + 50) = 100/159 = ~62.9% → ~37.1 damage
            // Phys resist: 37.1 × (1 - 50%) = ~18.55
            float afterArmorExpected = 100f * (1f - 100f / (100f + 9f + 50f));
            float afterResistExpected = afterArmorExpected * (1f - 50f / 100f);

            assertEquals(afterResistExpected, result.physicalDamage(), 0.5f,
                "Armor then physical resist should stack multiplicatively");
        }

        @Test
        @DisplayName("Zero armor results in no physical reduction")
        void zeroArmor_noReduction() {
            ComputedStats attackerStats = ComputedStats.builder()
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            ComputedStats defenderStats = ComputedStats.builder().armor(0f).build();

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                100f, attackerStats, null, defenderStats, null,
                AttackType.MELEE, false, 1);

            assertEquals(100f, result.physicalDamage(), 0.1f,
                "Zero armor should not reduce physical damage");
        }
    }
}
