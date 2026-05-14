package io.github.larsonix.trailoforbis.elemental;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ElementalCalculator}.
 *
 * <p>Tests the PoE-style elemental damage formulas:
 * <ul>
 *   <li>Damage: (Base + Flat) × (1 + Percent/100) × (1 + Multiplier/100)</li>
 *   <li>Resistance: damage × (1 - effectiveResist/100)</li>
 *   <li>Penetration: reduces effective resistance (floors at 0%)</li>
 * </ul>
 */
@DisplayName("ElementalCalculator")
class ElementalCalculatorTest {

    private ElementalStats attackerStats;
    private ElementalStats targetStats;

    @BeforeEach
    void setUp() {
        attackerStats = new ElementalStats();
        targetStats = new ElementalStats();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Damage Calculation Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Damage Calculation")
    class DamageCalculationTests {

        @Test
        @DisplayName("Zero base and zero flat returns zero damage")
        void zeroBaseAndZeroFlat_returnsZeroDamage() {
            double damage = ElementalCalculator.calculateDamage(0, attackerStats, ElementType.FIRE);
            assertEquals(0.0, damage, 0.001);
        }

        @Test
        @DisplayName("Base damage only returns base damage")
        void baseDamageOnly_returnsBaseDamage() {
            double damage = ElementalCalculator.calculateDamage(100, attackerStats, ElementType.FIRE);
            assertEquals(100.0, damage, 0.001);
        }

        @Test
        @DisplayName("Flat damage only returns flat damage")
        void flatDamageOnly_returnsFlatDamage() {
            attackerStats.setFlatDamage(ElementType.FIRE, 50);
            double damage = ElementalCalculator.calculateDamage(0, attackerStats, ElementType.FIRE);
            assertEquals(50.0, damage, 0.001);
        }

        @Test
        @DisplayName("Base + Flat adds together before percent")
        void baseAndFlat_addTogether() {
            attackerStats.setFlatDamage(ElementType.FIRE, 20);
            // (50 + 20) = 70
            double damage = ElementalCalculator.calculateDamage(50, attackerStats, ElementType.FIRE);
            assertEquals(70.0, damage, 0.001);
        }

        @Test
        @DisplayName("Percent bonus multiplies total: 50% = ×1.5")
        void percentBonus_multipliesTotalBy1Point5() {
            attackerStats.setPercentDamage(ElementType.FIRE, 50);
            // 100 × 1.5 = 150
            double damage = ElementalCalculator.calculateDamage(100, attackerStats, ElementType.FIRE);
            assertEquals(150.0, damage, 0.001);
        }

        @Test
        @DisplayName("Multiplier bonus multiplies total: 30% = ×1.3")
        void multiplierBonus_multipliesTotalBy1Point3() {
            attackerStats.setMultiplierDamage(ElementType.FIRE, 30);
            // 100 × 1.3 = 130
            double damage = ElementalCalculator.calculateDamage(100, attackerStats, ElementType.FIRE);
            assertEquals(130.0, damage, 0.001);
        }

        @Test
        @DisplayName("Full formula: (Base + Flat) × Percent × Multiplier")
        void fullFormula_appliesAllModifiers() {
            attackerStats.setFlatDamage(ElementType.FIRE, 20);      // +20 flat
            attackerStats.setPercentDamage(ElementType.FIRE, 50);   // +50% = ×1.5
            attackerStats.setMultiplierDamage(ElementType.FIRE, 30); // +30% = ×1.3

            // (50 + 20) × 1.5 × 1.3 = 70 × 1.5 × 1.3 = 136.5
            double damage = ElementalCalculator.calculateDamage(50, attackerStats, ElementType.FIRE);
            assertEquals(136.5, damage, 0.001);
        }

        @Test
        @DisplayName("Convenience method without base uses zero base")
        void convenienceMethod_usesZeroBase() {
            attackerStats.setFlatDamage(ElementType.FIRE, 100);
            double damage = ElementalCalculator.calculateDamage(attackerStats, ElementType.FIRE);
            assertEquals(100.0, damage, 0.001);
        }

        @Test
        @DisplayName("Negative damage is floored at zero")
        void negativeDamage_flooredAtZero() {
            // This shouldn't happen in practice, but formula should handle it
            double damage = ElementalCalculator.calculateDamage(-50, attackerStats, ElementType.FIRE);
            assertEquals(0.0, damage, 0.001);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Resistance Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Resistance Application")
    class ResistanceTests {

        @Test
        @DisplayName("Zero resistance passes full damage")
        void zeroResistance_passesFullDamage() {
            double result = ElementalCalculator.applyResistance(100, 0);
            assertEquals(100.0, result, 0.001);
        }

        @Test
        @DisplayName("50% resistance reduces damage by half")
        void fiftyPercentResistance_halvesDamage() {
            double result = ElementalCalculator.applyResistance(100, 50);
            assertEquals(50.0, result, 0.001);
        }

        @Test
        @DisplayName("75% resistance (cap) reduces damage to 25%")
        void seventyFivePercentResistance_reduces75Percent() {
            double result = ElementalCalculator.applyResistance(100, 75);
            assertEquals(25.0, result, 0.001);
        }

        @Test
        @DisplayName("Resistance above 75% is capped")
        void resistanceAboveCap_isCapped() {
            // 100% resistance should be capped to 75%
            double result = ElementalCalculator.applyResistance(100, 100);
            assertEquals(25.0, result, 0.001);
        }

        @Test
        @DisplayName("Negative resistance increases damage taken")
        void negativeResistance_increasesDamage() {
            // -50% resistance = 1.5× damage
            double result = ElementalCalculator.applyResistance(100, -50);
            assertEquals(150.0, result, 0.001);
        }

        @Test
        @DisplayName("Negative resistance floors at -200% (3× damage)")
        void negativeResistance_floorsAtMinus200() {
            // -200% resistance = 3× damage (at the floor)
            double result = ElementalCalculator.applyResistance(100, -200);
            assertEquals(300.0, result, 0.001);
        }

        @Test
        @DisplayName("-150% resistance is NOT clamped (within floor)")
        void negativeResistance_minus150_notClamped() {
            // -150% resistance = 2.5× damage (within -200 floor)
            double result = ElementalCalculator.applyResistance(100, -150);
            assertEquals(250.0, result, 0.001);
        }

        @Test
        @DisplayName("-300% resistance is clamped to -200% floor")
        void negativeResistance_minus300_clampedToFloor() {
            // -300% resistance should be floored to -200% = 3× damage
            double result = ElementalCalculator.applyResistance(100, -300);
            assertEquals(300.0, result, 0.001);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Penetration Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Penetration")
    class PenetrationTests {

        @Test
        @DisplayName("Penetration reduces effective resistance")
        void penetration_reducesEffectiveResistance() {
            // 60% resist - 20% pen = 40% effective
            double result = ElementalCalculator.applyResistance(100, 60, 20);
            assertEquals(60.0, result, 0.001); // 100 × (1 - 0.4)
        }

        @Test
        @DisplayName("Penetration pushes resistance negative (vulnerability)")
        void penetration_pushesNegative() {
            // 30% resist - 50% pen = -20% effective → 1.2× damage
            double result = ElementalCalculator.applyResistance(100, 30, 50);
            assertEquals(120.0, result, 0.001);
        }

        @Test
        @DisplayName("Penetration applies to negative resistance too")
        void penetration_appliesEvenToNegativeResistance() {
            // -50% resist - 20% pen = -70% effective → 1.7× damage
            double result = ElementalCalculator.applyResistance(100, -50, 20);
            assertEquals(170.0, result, 0.001);
        }

        @Test
        @DisplayName("Penetration on zero resistance creates vulnerability")
        void penetration_onZero_createsVulnerability() {
            // 0% resist - 50% pen = -50% effective → 1.5× damage
            double result = ElementalCalculator.applyResistance(100, 0, 50);
            assertEquals(150.0, result, 0.001);
        }

        @Test
        @DisplayName("Penetration floors at -200% (3× damage max)")
        void penetration_floorsAtMinus200() {
            // 50% resist - 300% pen = -250%, floored to -200% → 3× damage
            double result = ElementalCalculator.applyResistance(100, 50, 300);
            assertEquals(300.0, result, 0.001);
        }

        @Test
        @DisplayName("Overcapped resistance absorbs penetration (pen before cap)")
        void overcapped_absorbsPenetration() {
            // 120% resist - 30% pen = 90%, capped to 75% → 0.25× damage
            double result = ElementalCalculator.applyResistance(100, 120, 30);
            assertEquals(25.0, result, 0.001);
        }

        @Test
        @DisplayName("Overcapped resistance partially absorbs penetration")
        void overcapped_partiallyAbsorbsPenetration() {
            // 120% resist - 50% pen = 70%, under cap → 0.30× damage
            double result = ElementalCalculator.applyResistance(100, 120, 50);
            assertEquals(30.0, result, 0.001);
        }

        @Test
        @DisplayName("At-cap resistance with penetration (no overcap buffer)")
        void atCap_withPenetration_noBuffer() {
            // 75% resist - 39.4% pen = 35.6% effective → 0.644× damage
            double result = ElementalCalculator.applyResistance(100, 75, 39.4);
            assertEquals(64.4, result, 0.1);
        }

        @Test
        @DisplayName("Example from docs: 60% resist, 20% pen = 40% effective")
        void docExample_worksCorrectly() {
            // From class javadoc
            attackerStats.setFlatDamage(ElementType.FIRE, 20);
            attackerStats.setPercentDamage(ElementType.FIRE, 50);
            attackerStats.setMultiplierDamage(ElementType.FIRE, 30);
            attackerStats.setPenetration(ElementType.FIRE, 20);
            targetStats.setResistance(ElementType.FIRE, 60);

            // Damage = (50 + 20) × 1.5 × 1.3 = 136.5
            // EffectiveResist = max(-200, min(75, 60 - 20)) = 40%
            // After Resist = 136.5 × (1 - 0.4) = 81.9
            double result = ElementalCalculator.calculateFinalDamage(50, attackerStats, targetStats, ElementType.FIRE);
            assertEquals(81.9, result, 0.1);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Full Damage Pipeline Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Full Damage Pipeline")
    class FullDamagePipelineTests {

        @Test
        @DisplayName("calculateFinalDamage combines damage calc and resistance")
        void calculateFinalDamage_combinesCalcAndResistance() {
            attackerStats.setFlatDamage(ElementType.WATER, 50);
            targetStats.setResistance(ElementType.WATER, 50);

            // 50 flat damage, 50% resistance = 25 final damage
            double result = ElementalCalculator.calculateFinalDamage(0, attackerStats, targetStats, ElementType.WATER);
            assertEquals(25.0, result, 0.001);
        }

        @Test
        @DisplayName("calculateFinalDamage with penetration")
        void calculateFinalDamage_withPenetration() {
            attackerStats.setFlatDamage(ElementType.LIGHTNING, 100);
            attackerStats.setPenetration(ElementType.LIGHTNING, 30);
            targetStats.setResistance(ElementType.LIGHTNING, 50);

            // 100 damage, 50% resist - 30% pen = 20% effective
            // 100 × 0.8 = 80
            double result = ElementalCalculator.calculateFinalDamage(attackerStats, targetStats, ElementType.LIGHTNING);
            assertEquals(80.0, result, 0.001);
        }

        @Test
        @DisplayName("calculateTotalElementalDamage sums all elements")
        void calculateTotalElementalDamage_sumsAllElements() {
            attackerStats.setFlatDamage(ElementType.FIRE, 50);
            attackerStats.setFlatDamage(ElementType.WATER, 30);
            targetStats.setResistance(ElementType.FIRE, 50);
            targetStats.setResistance(ElementType.WATER, 0);

            // Fire: 50 × 0.5 = 25
            // Cold: 30 × 1.0 = 30
            // Total: 55
            double result = ElementalCalculator.calculateTotalElementalDamage(attackerStats, targetStats);
            assertEquals(55.0, result, 0.001);
        }

        @Test
        @DisplayName("calculateTotalElementalDamage skips elements with no damage")
        void calculateTotalElementalDamage_skipsZeroDamageElements() {
            attackerStats.setFlatDamage(ElementType.FIRE, 100);
            // Other elements have no damage
            targetStats.setResistance(ElementType.FIRE, 0);
            targetStats.setResistance(ElementType.WATER, 0);
            targetStats.setResistance(ElementType.LIGHTNING, 0);
            targetStats.setResistance(ElementType.VOID, 0);

            double result = ElementalCalculator.calculateTotalElementalDamage(attackerStats, targetStats);
            assertEquals(100.0, result, 0.001);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Effective Resistance Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Effective Resistance")
    class EffectiveResistanceTests {

        @Test
        @DisplayName("getEffectiveResistance caps at 75%")
        void getEffectiveResistance_capsAt75() {
            double effective = ElementalCalculator.getEffectiveResistance(100);
            assertEquals(75.0, effective, 0.001);
        }

        @Test
        @DisplayName("getEffectiveResistance floors at -200%")
        void getEffectiveResistance_floorsAtMinus200() {
            // -150% is within floor, not clamped
            assertEquals(-150.0, ElementalCalculator.getEffectiveResistance(-150), 0.001);
            // -250% is below floor, clamped to -200%
            assertEquals(-200.0, ElementalCalculator.getEffectiveResistance(-250), 0.001);
        }

        @Test
        @DisplayName("getEffectiveResistance with penetration reduces correctly")
        void getEffectiveResistance_withPenetration_reduces() {
            double effective = ElementalCalculator.getEffectiveResistance(60, 20);
            assertEquals(40.0, effective, 0.001);
        }

        @Test
        @DisplayName("getEffectiveResistance with penetration pushes negative")
        void getEffectiveResistance_withPenetration_pushesNegative() {
            // 30% resist - 50% pen = -20% (vulnerability)
            assertEquals(-20.0, ElementalCalculator.getEffectiveResistance(30, 50), 0.001);
            // 0% resist - 50% pen = -50%
            assertEquals(-50.0, ElementalCalculator.getEffectiveResistance(0, 50), 0.001);
            // -50% resist - 30% pen = -80%
            assertEquals(-80.0, ElementalCalculator.getEffectiveResistance(-50, 30), 0.001);
        }

        @Test
        @DisplayName("getEffectiveResistance with penetration floors at -200%")
        void getEffectiveResistance_withPenetration_floorsAtMinus200() {
            // 50% resist - 300% pen = -250%, clamped to -200%
            assertEquals(-200.0, ElementalCalculator.getEffectiveResistance(50, 300), 0.001);
        }

        @Test
        @DisplayName("getEffectiveResistance overcap absorbs penetration (pen before cap)")
        void getEffectiveResistance_overcap_absorbsPen() {
            // 120% resist - 30% pen = 90%, capped to 75%
            assertEquals(75.0, ElementalCalculator.getEffectiveResistance(120, 30), 0.001);
            // 120% resist - 50% pen = 70%, under cap
            assertEquals(70.0, ElementalCalculator.getEffectiveResistance(120, 50), 0.001);
        }

        @Test
        @DisplayName("getResistanceMultiplier returns 0.5 for 50% resistance")
        void getResistanceMultiplier_returnsHalfForFiftyPercent() {
            double mult = ElementalCalculator.getResistanceMultiplier(50);
            assertEquals(0.5, mult, 0.001);
        }

        @Test
        @DisplayName("getResistanceMultiplier returns 2.0 for -100% resistance")
        void getResistanceMultiplier_returnsTwoForMinusHundred() {
            double mult = ElementalCalculator.getResistanceMultiplier(-100);
            assertEquals(2.0, mult, 0.001);
        }

        @Test
        @DisplayName("isResistanceCapped returns true for 75%+")
        void isResistanceCapped_returnsTrueForCapped() {
            assertTrue(ElementalCalculator.isResistanceCapped(75));
            assertTrue(ElementalCalculator.isResistanceCapped(100));
        }

        @Test
        @DisplayName("isResistanceCapped returns false below 75%")
        void isResistanceCapped_returnsFalseForUncapped() {
            assertFalse(ElementalCalculator.isResistanceCapped(74));
            assertFalse(ElementalCalculator.isResistanceCapped(0));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Format Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Format Methods")
    class FormatTests {

        @Test
        @DisplayName("formatDamageBreakdown contains element name")
        void formatDamageBreakdown_containsElementName() {
            attackerStats.setFlatDamage(ElementType.FIRE, 50);
            targetStats.setResistance(ElementType.FIRE, 30);

            String breakdown = ElementalCalculator.formatDamageBreakdown(
                100, attackerStats, targetStats, ElementType.FIRE);

            assertTrue(breakdown.contains("Fire"));
        }

        @Test
        @DisplayName("formatDamageBreakdown shows penetration when present")
        void formatDamageBreakdown_showsPenetrationWhenPresent() {
            attackerStats.setFlatDamage(ElementType.FIRE, 50);
            attackerStats.setPenetration(ElementType.FIRE, 20);
            targetStats.setResistance(ElementType.FIRE, 60);

            String breakdown = ElementalCalculator.formatDamageBreakdown(
                100, attackerStats, targetStats, ElementType.FIRE);

            assertTrue(breakdown.contains("pen"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Edge Cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Very large values don't overflow")
        void veryLargeValues_dontOverflow() {
            attackerStats.setFlatDamage(ElementType.FIRE, 1_000_000);
            attackerStats.setPercentDamage(ElementType.FIRE, 1000); // ×11
            attackerStats.setMultiplierDamage(ElementType.FIRE, 1000); // ×11

            double damage = ElementalCalculator.calculateDamage(1_000_000, attackerStats, ElementType.FIRE);

            // Should be (1M + 1M) × 11 × 11 = 242M
            assertTrue(damage > 0);
            assertFalse(Double.isInfinite(damage));
            assertFalse(Double.isNaN(damage));
        }

        @Test
        @DisplayName("Very small values work correctly")
        void verySmallValues_workCorrectly() {
            attackerStats.setFlatDamage(ElementType.FIRE, 0.001);

            double damage = ElementalCalculator.calculateDamage(0.001, attackerStats, ElementType.FIRE);

            assertEquals(0.002, damage, 0.0001);
        }
    }
}
