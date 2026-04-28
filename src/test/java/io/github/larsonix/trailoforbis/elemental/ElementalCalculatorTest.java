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
        @DisplayName("Negative resistance floors at -100% (2× damage)")
        void negativeResistance_floorsAtMinusHundred() {
            // -200% resistance should be floored to -100% = 2× damage
            double result = ElementalCalculator.applyResistance(100, -200);
            assertEquals(200.0, result, 0.001);
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
        @DisplayName("Penetration cannot push resistance below zero")
        void penetration_cannotPushBelowZero() {
            // 30% resist - 50% pen = 0% effective (not -20%)
            double result = ElementalCalculator.applyResistance(100, 30, 50);
            assertEquals(100.0, result, 0.001); // Full damage
        }

        @Test
        @DisplayName("Penetration does not apply to already negative resistance")
        void penetration_doesNotApplyToNegativeResistance() {
            // -50% resist should stay -50%, not become more negative
            double result = ElementalCalculator.applyResistance(100, -50, 20);
            assertEquals(150.0, result, 0.001); // 100 × 1.5
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
            // EffectiveResist = max(0, 60 - 20) = 40%
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
        @DisplayName("getEffectiveResistance floors at -100%")
        void getEffectiveResistance_floorsAtMinus100() {
            double effective = ElementalCalculator.getEffectiveResistance(-150);
            assertEquals(-100.0, effective, 0.001);
        }

        @Test
        @DisplayName("getEffectiveResistance with penetration reduces correctly")
        void getEffectiveResistance_withPenetration_reduces() {
            double effective = ElementalCalculator.getEffectiveResistance(60, 20);
            assertEquals(40.0, effective, 0.001);
        }

        @Test
        @DisplayName("getEffectiveResistance with penetration floors at 0%")
        void getEffectiveResistance_withPenetration_floorsAtZero() {
            double effective = ElementalCalculator.getEffectiveResistance(30, 50);
            assertEquals(0.0, effective, 0.001);
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
