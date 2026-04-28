package io.github.larsonix.trailoforbis.combat;

import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CombatCalculator.
 *
 * <p>Tests the PoE-inspired damage formulas:
 * <ul>
 *   <li>Attacker: (Base + Flat) * (1 + %/100) * CritMultiplier</li>
 *   <li>Defender: Armor / (Armor + 10*Damage), capped at 90%</li>
 * </ul>
 */
public class CombatCalculatorTest {

    private CombatCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new CombatCalculator();
    }

    // ==================== Attacker Damage Tests ====================

    @Nested
    @DisplayName("Attacker Damage Calculations")
    class AttackerDamageTests {

        @Test
        @DisplayName("Flat bonus: 10 base + 5 flat = 15 damage")
        void flatBonusApplied() {
            ComputedStats stats = ComputedStats.builder()
                .physicalDamage(5f)
                .physicalDamagePercent(0f)
                .criticalChance(0f)
                .criticalMultiplier(100f) // 1.0x multiplier (no bonus)
                .build();

            var result = calculator.calculateAttackerDamage(10f, stats, false);

            assertEquals(15f, result.finalDamage(), 0.001f);
            assertEquals(10f, result.baseDamage(), 0.001f);
            assertEquals(5f, result.flatBonus(), 0.001f);
            assertEquals(0f, result.percentBonus(), 0.001f);
            assertFalse(result.wasCritical());
        }

        @Test
        @DisplayName("Percent bonus: 10 base * 1.5 = 15 damage (50% increase)")
        void percentBonusApplied() {
            ComputedStats stats = ComputedStats.builder()
                .physicalDamage(0f)
                .physicalDamagePercent(50f) // +50%
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            var result = calculator.calculateAttackerDamage(10f, stats, false);

            assertEquals(15f, result.finalDamage(), 0.001f);
            assertEquals(50f, result.percentBonus(), 0.001f);
        }

        @Test
        @DisplayName("Combined: (10 + 5) * 1.5 = 22.5 damage")
        void flatAndPercentCombined() {
            ComputedStats stats = ComputedStats.builder()
                .physicalDamage(5f)
                .physicalDamagePercent(50f)
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            var result = calculator.calculateAttackerDamage(10f, stats, false);

            // (10 + 5) * (1 + 50/100) = 15 * 1.5 = 22.5
            assertEquals(22.5f, result.finalDamage(), 0.001f);
        }

        @Test
        @DisplayName("Critical hit: 10 damage * 1.5 crit = 15 damage")
        void criticalHitApplied() {
            ComputedStats stats = ComputedStats.builder()
                .physicalDamage(0f)
                .physicalDamagePercent(0f)
                .criticalChance(100f) // Ignored when forceCrit=true
                .criticalMultiplier(150f) // 1.5x
                .build();

            var result = calculator.calculateAttackerDamage(10f, stats, true);

            assertEquals(15f, result.finalDamage(), 0.001f);
            assertTrue(result.wasCritical());
            assertEquals(1.5f, result.critMultiplier(), 0.001f);
        }

        @Test
        @DisplayName("No crit when forced off, even with 100% crit chance")
        void noCritWhenForcedOff() {
            ComputedStats stats = ComputedStats.builder()
                .physicalDamage(0f)
                .physicalDamagePercent(0f)
                .criticalChance(100f)
                .criticalMultiplier(200f)
                .build();

            var result = calculator.calculateAttackerDamage(10f, stats, false);

            assertEquals(10f, result.finalDamage(), 0.001f);
            assertFalse(result.wasCritical());
            assertEquals(1.0f, result.critMultiplier(), 0.001f);
        }

        @Test
        @DisplayName("Full formula: (10 + 5) * 1.2 * 2.0 crit = 36 damage")
        void fullFormulaWithCrit() {
            ComputedStats stats = ComputedStats.builder()
                .physicalDamage(5f)
                .physicalDamagePercent(20f) // +20%
                .criticalChance(100f)
                .criticalMultiplier(200f) // 2.0x
                .build();

            var result = calculator.calculateAttackerDamage(10f, stats, true);

            // (10 + 5) * (1 + 20/100) * 2.0 = 15 * 1.2 * 2.0 = 36
            assertEquals(36f, result.finalDamage(), 0.001f);
            assertTrue(result.wasCritical());
        }

        @Test
        @DisplayName("Zero base damage results in flat bonus only")
        void zeroBaseDamage() {
            ComputedStats stats = ComputedStats.builder()
                .physicalDamage(10f)
                .physicalDamagePercent(50f)
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            var result = calculator.calculateAttackerDamage(0f, stats, false);

            // (0 + 10) * 1.5 = 15
            assertEquals(15f, result.finalDamage(), 0.001f);
        }

        @Test
        @DisplayName("allDamagePercent: 10 base * 1.25 = 12.5 (25% all damage)")
        void allDamagePercentApplied() {
            ComputedStats stats = ComputedStats.builder()
                .physicalDamage(0f)
                .physicalDamagePercent(0f)
                .allDamagePercent(25f)
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            var result = calculator.calculateAttackerDamage(10f, stats, AttackType.MELEE, false);

            // 10 * (1 + 25/100) = 12.5
            assertEquals(12.5f, result.finalDamage(), 0.001f);
        }

        @Test
        @DisplayName("allDamagePercent stacks multiplicatively with physDmgPercent")
        void allDamagePercentStacksMultiplicatively() {
            ComputedStats stats = ComputedStats.builder()
                .physicalDamage(0f)
                .physicalDamagePercent(50f)
                .allDamagePercent(20f)
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            var result = calculator.calculateAttackerDamage(10f, stats, AttackType.UNKNOWN, false);

            // 10 * 1.5 (physPercent) * 1.2 (allDmg) = 18
            assertEquals(18f, result.finalDamage(), 0.001f);
        }

        @Test
        @DisplayName("meleeDamage: flat +5 only for MELEE attacks")
        void meleeDamageAppliedForMelee() {
            ComputedStats stats = ComputedStats.builder()
                .physicalDamage(0f)
                .physicalDamagePercent(0f)
                .meleeDamage(5f)
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            var melee = calculator.calculateAttackerDamage(10f, stats, AttackType.MELEE, false);
            var proj = calculator.calculateAttackerDamage(10f, stats, AttackType.PROJECTILE, false);

            assertEquals(15f, melee.finalDamage(), 0.001f); // 10 + 5
            assertEquals(10f, proj.finalDamage(), 0.001f);   // no melee bonus
        }

        @Test
        @DisplayName("Zero stats results in unchanged damage")
        void zeroStats() {
            ComputedStats stats = ComputedStats.builder()
                .physicalDamage(0f)
                .physicalDamagePercent(0f)
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            var result = calculator.calculateAttackerDamage(25f, stats, false);

            assertEquals(25f, result.finalDamage(), 0.001f);
        }
    }

    // ==================== Defender Armor Tests ====================

    @Nested
    @DisplayName("Defender Armor Reduction")
    class DefenderArmorTests {

        @Test
        @DisplayName("PoE formula: 100 armor vs 10 damage = 50% reduction")
        void armorFormulaBasic() {
            ComputedStats stats = ComputedStats.builder()
                .armor(100f)
                .build();

            var result = calculator.calculateDefenderReduction(10f, stats);

            // 100 / (100 + 10*10) = 100 / 200 = 0.5 = 50%
            assertEquals(50f, result.reductionPercent(), 0.001f);
            assertEquals(5f, result.finalDamage(), 0.001f);
            assertEquals(10f, result.beforeArmor(), 0.001f);
            assertEquals(100f, result.armorValue(), 0.001f);
        }

        @Test
        @DisplayName("Armor vs high damage = lower reduction")
        void armorVsHighDamage() {
            ComputedStats stats = ComputedStats.builder()
                .armor(100f)
                .build();

            var result = calculator.calculateDefenderReduction(100f, stats);

            // 100 / (100 + 10*100) = 100 / 1100 = 0.0909 = 9.09%
            assertEquals(9.09f, result.reductionPercent(), 0.1f);
            assertEquals(90.9f, result.finalDamage(), 0.1f);
        }

        @Test
        @DisplayName("Armor caps at 90% reduction")
        void armorCapAt90Percent() {
            ComputedStats stats = ComputedStats.builder()
                .armor(10000f) // Very high armor
                .build();

            var result = calculator.calculateDefenderReduction(10f, stats);

            // Would be 10000 / (10000 + 100) = 99.01%, but capped at 90%
            assertTrue(result.reductionPercent() <= 90f, "Reduction should be capped at 90%");
            assertEquals(90f, result.reductionPercent(), 0.001f);
            assertEquals(1f, result.finalDamage(), 0.001f); // 10% gets through
        }

        @Test
        @DisplayName("Zero armor = no reduction")
        void zeroArmorNoReduction() {
            ComputedStats stats = ComputedStats.builder()
                .armor(0f)
                .build();

            var result = calculator.calculateDefenderReduction(50f, stats);

            assertEquals(0f, result.reductionPercent(), 0.001f);
            assertEquals(50f, result.finalDamage(), 0.001f);
        }

        @Test
        @DisplayName("Negative armor treated as zero")
        void negativeArmorTreatedAsZero() {
            ComputedStats stats = ComputedStats.builder()
                .armor(-50f)
                .build();

            var result = calculator.calculateDefenderReduction(50f, stats);

            assertEquals(0f, result.reductionPercent(), 0.001f);
            assertEquals(50f, result.finalDamage(), 0.001f);
        }

        @Test
        @DisplayName("Zero damage = zero damage through")
        void zeroDamageInput() {
            ComputedStats stats = ComputedStats.builder()
                .armor(100f)
                .build();

            var result = calculator.calculateDefenderReduction(0f, stats);

            assertEquals(0f, result.reductionPercent(), 0.001f);
            assertEquals(0f, result.finalDamage(), 0.001f);
        }

        @Test
        @DisplayName("Diminishing returns: armor scales better vs small hits")
        void diminishingReturns() {
            ComputedStats stats = ComputedStats.builder()
                .armor(50f)
                .build();

            // Small hit: 50 / (50 + 10*5) = 50/100 = 50%
            var smallHit = calculator.calculateDefenderReduction(5f, stats);
            assertEquals(50f, smallHit.reductionPercent(), 0.1f);

            // Large hit: 50 / (50 + 10*50) = 50/550 = 9.09%
            var largeHit = calculator.calculateDefenderReduction(50f, stats);
            assertEquals(9.09f, largeHit.reductionPercent(), 0.1f);

            // Verify small hits get more reduction
            assertTrue(smallHit.reductionPercent() > largeHit.reductionPercent(),
                "Small hits should receive more armor reduction");
        }

        @Test
        @DisplayName("+50% armor percent increases effective armor by 50%")
        void armorPercent_increasesEffectiveArmor() {
            ComputedStats stats = ComputedStats.builder()
                .armor(100f)
                .armorPercent(50f)
                .build();

            var result = calculator.calculateDefenderReduction(10f, stats);

            // Effective armor = 100 * (1 + 50/100) = 150
            // Reduction = 150 / (150 + 10*10) = 150/250 = 60%
            assertEquals(60f, result.reductionPercent(), 0.1f);
            assertEquals(4f, result.finalDamage(), 0.1f);
        }

        @Test
        @DisplayName("0% armor percent leaves armor unchanged")
        void zeroArmorPercent_noChange() {
            ComputedStats withPercent = ComputedStats.builder()
                .armor(100f)
                .armorPercent(0f)
                .build();
            ComputedStats without = ComputedStats.builder()
                .armor(100f)
                .build();

            var resultWith = calculator.calculateDefenderReduction(10f, withPercent);
            var resultWithout = calculator.calculateDefenderReduction(10f, without);

            assertEquals(resultWithout.reductionPercent(), resultWith.reductionPercent(), 0.001f);
        }

        @Test
        @DisplayName("+100% armor percent doubles armor effectiveness")
        void hundredArmorPercent_doublesArmor() {
            ComputedStats stats = ComputedStats.builder()
                .armor(100f)
                .armorPercent(100f)
                .build();

            var result = calculator.calculateDefenderReduction(10f, stats);

            // Effective armor = 100 * 2 = 200
            // Reduction = 200 / (200 + 100) = 200/300 = 66.67%
            assertEquals(66.67f, result.reductionPercent(), 0.1f);
        }

        @Test
        @DisplayName("Negative armor percent reduces armor")
        void negativeArmorPercent_reducesArmor() {
            ComputedStats stats = ComputedStats.builder()
                .armor(100f)
                .armorPercent(-50f)
                .build();

            var result = calculator.calculateDefenderReduction(10f, stats);

            // Effective armor = 100 * 0.5 = 50
            // Reduction = 50 / (50 + 100) = 50/150 = 33.33%
            assertEquals(33.33f, result.reductionPercent(), 0.1f);
        }

        @Test
        @DisplayName("Armor percent applies before armor penetration")
        void armorPercent_appliesBeforePenetration() {
            ComputedStats stats = ComputedStats.builder()
                .armor(100f)
                .armorPercent(50f)
                .build();

            // 50% penetration
            var result = calculator.calculateDefenderReduction(10f, stats, 50f);

            // Effective armor = 100 * 1.5 = 150, then pen: 150 * 0.5 = 75
            // Reduction = 75 / (75 + 100) = 75/175 = 42.86%
            assertEquals(42.86f, result.reductionPercent(), 0.1f);
        }
    }

    // ==================== Armor Penetration Floor Tests ====================

    @Nested
    @DisplayName("Armor Penetration with Floor")
    class ArmorPenetrationFloorTests {

        @Test
        @DisplayName("25% pen reduces armor to 75% (before floor)")
        void twentyFivePercentPen_reducesArmorTo75Percent() {
            ComputedStats stats = ComputedStats.builder()
                .armor(100f)
                .build();

            var result = calculator.calculateDefenderReduction(10f, stats, 25f);

            // Effective armor = 100 * max(0.5, 1 - 0.25) = 100 * 0.75 = 75
            // Reduction = 75 / (75 + 10*10) = 75/175 = 42.86%
            assertEquals(75f, result.effectiveArmor(), 0.1f);
            assertEquals(42.86f, result.reductionPercent(), 0.1f);
        }

        @Test
        @DisplayName("50% pen hits floor (50% effectiveness)")
        void fiftyPercentPen_hitsFloor() {
            ComputedStats stats = ComputedStats.builder()
                .armor(100f)
                .build();

            var result = calculator.calculateDefenderReduction(10f, stats, 50f);

            // Effective armor = 100 * max(0.5, 1 - 0.5) = 100 * 0.5 = 50
            // Reduction = 50 / (50 + 100) = 50/150 = 33.33%
            assertEquals(50f, result.effectiveArmor(), 0.1f);
            assertEquals(33.33f, result.reductionPercent(), 0.1f);
        }

        @Test
        @DisplayName("75% pen still limited by floor (50%)")
        void seventyFivePercentPen_limitedByFloor() {
            ComputedStats stats = ComputedStats.builder()
                .armor(100f)
                .build();

            var result50 = calculator.calculateDefenderReduction(10f, stats, 50f);
            var result75 = calculator.calculateDefenderReduction(10f, stats, 75f);

            // Both should have same effective armor due to floor
            assertEquals(result50.effectiveArmor(), result75.effectiveArmor(), 0.001f);
            assertEquals(50f, result75.effectiveArmor(), 0.1f);
        }

        @Test
        @DisplayName("100% pen still limited by floor (50%)")
        void hundredPercentPen_limitedByFloor() {
            ComputedStats stats = ComputedStats.builder()
                .armor(100f)
                .build();

            var result = calculator.calculateDefenderReduction(10f, stats, 100f);

            // Even 100% pen can't go below floor
            assertEquals(50f, result.effectiveArmor(), 0.1f);
        }

        @Test
        @DisplayName("Custom floor setting respected (25%)")
        void customFloor_respected() {
            CombatCalculator custom = new CombatCalculator();
            custom.setMinArmorEffectiveness(0.25f); // 25% floor

            ComputedStats stats = ComputedStats.builder()
                .armor(100f)
                .build();

            var result = custom.calculateDefenderReduction(10f, stats, 100f);

            // Effective armor = 100 * 0.25 = 25
            assertEquals(25f, result.effectiveArmor(), 0.1f);
        }

        @Test
        @DisplayName("Zero floor allows full penetration")
        void zeroFloor_allowsFullPenetration() {
            CombatCalculator custom = new CombatCalculator();
            custom.setMinArmorEffectiveness(0f); // No floor

            ComputedStats stats = ComputedStats.builder()
                .armor(100f)
                .build();

            var result = custom.calculateDefenderReduction(10f, stats, 100f);

            // 100% pen with no floor = 0 effective armor
            assertEquals(0f, result.effectiveArmor(), 0.001f);
            assertEquals(0f, result.reductionPercent(), 0.001f);
            assertEquals(10f, result.finalDamage(), 0.001f); // Full damage
        }

        @Test
        @DisplayName("100% floor makes armor pen have no effect")
        void hundredPercentFloor_noEffect() {
            CombatCalculator custom = new CombatCalculator();
            custom.setMinArmorEffectiveness(1f); // 100% floor

            ComputedStats stats = ComputedStats.builder()
                .armor(100f)
                .build();

            var result = custom.calculateDefenderReduction(10f, stats, 100f);

            // Even 100% pen does nothing with 100% floor
            assertEquals(100f, result.effectiveArmor(), 0.1f);
        }

        @Test
        @DisplayName("Floor applies after armor percent bonus")
        void floor_appliesAfterArmorPercent() {
            ComputedStats stats = ComputedStats.builder()
                .armor(100f)
                .armorPercent(50f) // +50% = 150 armor
                .build();

            var result = calculator.calculateDefenderReduction(10f, stats, 75f);

            // Armor after % bonus = 100 * 1.5 = 150
            // Pen effect = max(0.5, 1 - 0.75) = 0.5 (floor)
            // Effective armor = 150 * 0.5 = 75
            assertEquals(75f, result.effectiveArmor(), 0.1f);
        }

        @Test
        @DisplayName("Realistic mob scenario: Ravager with 50% armor pen")
        void realisticScenario_ravagerArmorPen() {
            // Player has 1000 armor from gear + VIT
            ComputedStats playerStats = ComputedStats.builder()
                .armor(1000f)
                .build();

            // Ravager has 50% armor pen (3.5× base multiplier)
            var result = calculator.calculateDefenderReduction(100f, playerStats, 50f);

            // With floor: effective = 1000 * max(0.5, 0.5) = 500
            // Without floor: effective = 1000 * 0.5 = 500 (same at exactly 50%)
            assertEquals(500f, result.effectiveArmor(), 0.1f);
            // Reduction = 500 / (500 + 1000) = 500/1500 = 33.33%
            assertEquals(33.33f, result.reductionPercent(), 0.1f);
        }

        @Test
        @DisplayName("Realistic mob scenario: Executioner with 75% armor pen (capped)")
        void realisticScenario_executionerArmorPen() {
            // Player has 1000 armor
            ComputedStats playerStats = ComputedStats.builder()
                .armor(1000f)
                .build();

            // Executioner has 75% armor pen - but floor caps it at 50%
            var result = calculator.calculateDefenderReduction(100f, playerStats, 75f);

            // With floor: effective = 1000 * max(0.5, 0.25) = 1000 * 0.5 = 500
            assertEquals(500f, result.effectiveArmor(), 0.1f);
            // Same reduction as 50% pen due to floor
            assertEquals(33.33f, result.reductionPercent(), 0.1f);
        }

        @Test
        @DisplayName("Negative pen treated as 0% (no effect on armor)")
        void negativePen_noEffect() {
            ComputedStats stats = ComputedStats.builder()
                .armor(100f)
                .build();

            var result = calculator.calculateDefenderReduction(10f, stats, -25f);

            // Negative pen should not increase armor
            // -25% → clamp to 0% → effectiveness = max(0.5, 1) = 1
            assertEquals(100f, result.effectiveArmor(), 0.1f);
        }

        @Test
        @DisplayName("Floor getter returns default value")
        void floorGetter_returnsDefault() {
            assertEquals(0.5f, calculator.getMinArmorEffectiveness(), 0.001f);
        }

        @Test
        @DisplayName("Floor setter clamps values to 0-1 range")
        void floorSetter_clampsValues() {
            CombatCalculator custom = new CombatCalculator();

            custom.setMinArmorEffectiveness(1.5f); // Above 1
            assertEquals(1.0f, custom.getMinArmorEffectiveness(), 0.001f);

            custom.setMinArmorEffectiveness(-0.5f); // Below 0
            assertEquals(0.0f, custom.getMinArmorEffectiveness(), 0.001f);
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Result records contain correct values")
        void resultRecordsCorrect() {
            ComputedStats attackerStats = ComputedStats.builder()
                .physicalDamage(3f)
                .physicalDamagePercent(10f)
                .criticalMultiplier(150f)
                .build();

            var damageResult = calculator.calculateAttackerDamage(7f, attackerStats, true);

            assertEquals(7f, damageResult.baseDamage());
            assertEquals(3f, damageResult.flatBonus());
            assertEquals(10f, damageResult.percentBonus());
            assertTrue(damageResult.wasCritical());
            assertEquals(1.5f, damageResult.critMultiplier(), 0.001f);

            ComputedStats defenderStats = ComputedStats.builder()
                .armor(200f)
                .build();

            var armorResult = calculator.calculateDefenderReduction(20f, defenderStats);

            assertEquals(20f, armorResult.beforeArmor());
            assertEquals(200f, armorResult.armorValue());
        }

        @Test
        @DisplayName("Large values don't overflow")
        void largeValuesNoOverflow() {
            ComputedStats stats = ComputedStats.builder()
                .physicalDamage(1000f)
                .physicalDamagePercent(500f)
                .criticalMultiplier(300f)
                .build();

            var result = calculator.calculateAttackerDamage(1000f, stats, true);

            // (1000 + 1000) * 6.0 * 3.0 = 36000
            assertEquals(36000f, result.finalDamage(), 0.1f);
            assertTrue(Float.isFinite(result.finalDamage()));
        }

        @Test
        @DisplayName("Random crit respects 0% chance")
        void zeroCritChanceNeverCrits() {
            ComputedStats stats = ComputedStats.builder()
                .physicalDamage(0f)
                .physicalDamagePercent(0f)
                .criticalChance(0f)
                .criticalMultiplier(500f)
                .build();

            // Run multiple times to test randomness
            for (int i = 0; i < 100; i++) {
                var result = calculator.calculateAttackerDamage(10f, stats);
                assertFalse(result.wasCritical(), "0% crit chance should never crit");
            }
        }
    }

    // ==================== Percentage-Based Reduction Tests ====================

    @Nested
    @DisplayName("Percentage-Based Damage Reduction")
    class PercentageReductionTests {

        @Test
        @DisplayName("50% reduction halves damage")
        void fiftyPercentReduction_halvesDamage() {
            var result = CombatCalculator.applyPercentageReduction(100f, 50f);

            assertEquals(50f, result.finalDamage(), 0.001f);
            assertEquals(100f, result.originalDamage(), 0.001f);
            assertEquals(50f, result.reductionPercent(), 0.001f);
            assertEquals(50f, result.damageReduced(), 0.001f);
            assertTrue(result.wasReduced());
            assertFalse(result.wasFullyNegated());
        }

        @Test
        @DisplayName("100% reduction negates all damage")
        void hundredPercentReduction_negatesAllDamage() {
            var result = CombatCalculator.applyPercentageReduction(100f, 100f);

            assertEquals(0f, result.finalDamage(), 0.001f);
            assertEquals(100f, result.reductionPercent(), 0.001f);
            assertEquals(100f, result.damageReduced(), 0.001f);
            assertTrue(result.wasReduced());
            assertTrue(result.wasFullyNegated());
        }

        @Test
        @DisplayName("Reduction capped at 100% (no negative damage)")
        void reductionCappedAtHundredPercent() {
            var result = CombatCalculator.applyPercentageReduction(100f, 150f);

            assertEquals(0f, result.finalDamage(), 0.001f);
            assertEquals(100f, result.reductionPercent(), 0.001f); // Capped at 100
            assertEquals(100f, result.damageReduced(), 0.001f);
            assertTrue(result.wasFullyNegated());
        }

        @Test
        @DisplayName("0% reduction leaves damage unchanged")
        void zeroReduction_noChange() {
            var result = CombatCalculator.applyPercentageReduction(75f, 0f);

            assertEquals(75f, result.finalDamage(), 0.001f);
            assertEquals(0f, result.reductionPercent(), 0.001f);
            assertEquals(0f, result.damageReduced(), 0.001f);
            assertFalse(result.wasReduced());
            assertFalse(result.wasFullyNegated());
        }

        @Test
        @DisplayName("Negative reduction treated as 0%")
        void negativeReduction_treatedAsZero() {
            var result = CombatCalculator.applyPercentageReduction(100f, -25f);

            assertEquals(100f, result.finalDamage(), 0.001f);
            assertEquals(0f, result.reductionPercent(), 0.001f);
            assertFalse(result.wasReduced());
        }

        @Test
        @DisplayName("Zero damage stays zero")
        void zeroDamage_staysZero() {
            var result = CombatCalculator.applyPercentageReduction(0f, 50f);

            assertEquals(0f, result.finalDamage(), 0.001f);
            assertEquals(0f, result.originalDamage(), 0.001f);
            assertFalse(result.wasReduced());
            assertFalse(result.wasFullyNegated());
        }

        @Test
        @DisplayName("Negative damage stays zero")
        void negativeDamage_returnsZero() {
            var result = CombatCalculator.applyPercentageReduction(-50f, 50f);

            assertEquals(0f, result.finalDamage(), 0.001f);
        }

        @Test
        @DisplayName("Small percentages work correctly")
        void smallPercentages_workCorrectly() {
            var result = CombatCalculator.applyPercentageReduction(1000f, 0.5f);

            // 1000 * (1 - 0.5/100) = 1000 * 0.995 = 995
            assertEquals(995f, result.finalDamage(), 0.001f);
            assertEquals(0.5f, result.reductionPercent(), 0.001f);
            assertEquals(5f, result.damageReduced(), 0.001f);
        }
    }

    // ==================== Fall Damage Reduction Tests ====================

    @Nested
    @DisplayName("Fall Damage Reduction")
    class FallDamageReductionTests {

        @Test
        @DisplayName("Fall damage reduced by VIT-based stat")
        void fallDamageReduced_byVitStat() {
            ComputedStats stats = ComputedStats.builder()
                .fallDamageReduction(30f) // 30% from VIT
                .build();

            var result = CombatCalculator.applyFallDamageReduction(100f, stats);

            assertEquals(70f, result.finalDamage(), 0.001f);
            assertEquals(30f, result.reductionPercent(), 0.001f);
            assertEquals(30f, result.damageReduced(), 0.001f);
            assertTrue(result.wasReduced());
        }

        @Test
        @DisplayName("Max reduction config respected (90% cap)")
        void maxReduction_respectsConfigCap() {
            // With 200 VIT at 0.5% per point = 100%, but config caps at 90%
            // AttributeCalculator applies: Math.min(maxReduction, vit * perVitReduction)
            ComputedStats stats = ComputedStats.builder()
                .fallDamageReduction(90f) // Pre-capped by AttributeCalculator
                .build();

            var result = CombatCalculator.applyFallDamageReduction(100f, stats);

            assertEquals(10f, result.finalDamage(), 0.001f);
            assertEquals(90f, result.reductionPercent(), 0.001f);
        }

        @Test
        @DisplayName("No reduction when stat is 0")
        void noReduction_whenStatIsZero() {
            ComputedStats stats = ComputedStats.builder()
                .fallDamageReduction(0f)
                .build();

            var result = CombatCalculator.applyFallDamageReduction(50f, stats);

            assertEquals(50f, result.finalDamage(), 0.001f);
            assertFalse(result.wasReduced());
        }

        @Test
        @DisplayName("Edge case: very high fall damage with moderate reduction")
        void highFallDamage_moderateReduction() {
            ComputedStats stats = ComputedStats.builder()
                .fallDamageReduction(45f)
                .build();

            var result = CombatCalculator.applyFallDamageReduction(500f, stats);

            // 500 * (1 - 0.45) = 500 * 0.55 = 275
            assertEquals(275f, result.finalDamage(), 0.001f);
            assertEquals(225f, result.damageReduced(), 0.001f);
        }

        @Test
        @DisplayName("Realistic scenario: 50 VIT player takes fall damage")
        void realisticScenario_fiftyVitPlayer() {
            // 50 VIT * 0.5% per VIT = 25% reduction (from default config)
            ComputedStats stats = ComputedStats.builder()
                .fallDamageReduction(25f)
                .build();

            var result = CombatCalculator.applyFallDamageReduction(40f, stats);

            // 40 * 0.75 = 30
            assertEquals(30f, result.finalDamage(), 0.001f);
            assertEquals(10f, result.damageReduced(), 0.001f);
        }
    }

    // ==================== Physical Resistance Tests ====================

    @Nested
    @DisplayName("Physical Resistance")
    class PhysicalResistanceTests {

        @Test
        @DisplayName("Physical resistance reduces damage with default 75% cap")
        void physicalResistance_reducesWithDefaultCap() {
            ComputedStats stats = ComputedStats.builder()
                .physicalResistance(50f) // 50% resistance
                .build();

            var result = CombatCalculator.applyPhysicalResistance(100f, stats);

            assertEquals(50f, result.finalDamage(), 0.001f);
            assertEquals(100f, result.originalDamage(), 0.001f);
            assertEquals(50f, result.reductionPercent(), 0.001f);
            assertEquals(50f, result.damageReduced(), 0.001f);
            assertTrue(result.wasReduced());
        }

        @Test
        @DisplayName("Resistance capped at configured maximum (75% default)")
        void resistanceCapped_atConfiguredMaximum() {
            ComputedStats stats = ComputedStats.builder()
                .physicalResistance(90f) // Would be 90%, but capped
                .build();

            // Using default 75% cap
            var result = CombatCalculator.applyPhysicalResistance(100f, stats);

            assertEquals(25f, result.finalDamage(), 0.001f);
            assertEquals(75f, result.reductionPercent(), 0.001f);
            assertTrue(result.wasReduced());
        }

        @Test
        @DisplayName("Custom cap respected (e.g., 50%)")
        void customCap_respected() {
            ComputedStats stats = ComputedStats.builder()
                .physicalResistance(80f) // 80%, but 50% cap
                .build();

            var result = CombatCalculator.applyPhysicalResistance(100f, stats, 50f);

            assertEquals(50f, result.finalDamage(), 0.001f);
            assertEquals(50f, result.reductionPercent(), 0.001f); // Capped at 50
        }

        @Test
        @DisplayName("Zero resistance = no reduction")
        void zeroResistance_noReduction() {
            ComputedStats stats = ComputedStats.builder()
                .physicalResistance(0f)
                .build();

            var result = CombatCalculator.applyPhysicalResistance(100f, stats);

            assertEquals(100f, result.finalDamage(), 0.001f);
            assertEquals(0f, result.reductionPercent(), 0.001f);
            assertFalse(result.wasReduced());
        }

        @Test
        @DisplayName("Negative resistance treated as zero")
        void negativeResistance_treatedAsZero() {
            ComputedStats stats = ComputedStats.builder()
                .physicalResistance(-20f)
                .build();

            var result = CombatCalculator.applyPhysicalResistance(100f, stats);

            assertEquals(100f, result.finalDamage(), 0.001f);
            assertFalse(result.wasReduced());
        }

        @Test
        @DisplayName("Small resistance values work correctly")
        void smallResistance_workCorrectly() {
            ComputedStats stats = ComputedStats.builder()
                .physicalResistance(5f) // 5% reduction
                .build();

            var result = CombatCalculator.applyPhysicalResistance(200f, stats);

            // 200 * (1 - 0.05) = 200 * 0.95 = 190
            assertEquals(190f, result.finalDamage(), 0.001f);
            assertEquals(5f, result.reductionPercent(), 0.001f);
            assertEquals(10f, result.damageReduced(), 0.001f);
        }

        @Test
        @DisplayName("100% cap allows full resistance")
        void hundredPercentCap_allowsFullResistance() {
            ComputedStats stats = ComputedStats.builder()
                .physicalResistance(100f)
                .build();

            var result = CombatCalculator.applyPhysicalResistance(100f, stats, 100f);

            assertEquals(0f, result.finalDamage(), 0.001f);
            assertEquals(100f, result.reductionPercent(), 0.001f);
            assertTrue(result.wasFullyNegated());
        }

        @Test
        @DisplayName("Zero damage input returns zero damage")
        void zeroDamage_returnsZero() {
            ComputedStats stats = ComputedStats.builder()
                .physicalResistance(50f)
                .build();

            var result = CombatCalculator.applyPhysicalResistance(0f, stats);

            assertEquals(0f, result.finalDamage(), 0.001f);
            assertFalse(result.wasReduced());
        }

        @Test
        @DisplayName("Realistic scenario: geared tank with 60% resistance")
        void realisticScenario_gearedTank() {
            // A tank with high gear might have ~60% physical resistance
            ComputedStats stats = ComputedStats.builder()
                .physicalResistance(60f)
                .build();

            var result = CombatCalculator.applyPhysicalResistance(150f, stats);

            // 150 * (1 - 0.60) = 150 * 0.40 = 60
            assertEquals(60f, result.finalDamage(), 0.001f);
            assertEquals(90f, result.damageReduced(), 0.001f);
        }

        @Test
        @DisplayName("Over-cap resistance with custom cap")
        void overCapResistance_customCap() {
            // Player has 100% resistance but server allows only 80%
            ComputedStats stats = ComputedStats.builder()
                .physicalResistance(100f)
                .build();

            var result = CombatCalculator.applyPhysicalResistance(100f, stats, 80f);

            assertEquals(20f, result.finalDamage(), 0.001f);
            assertEquals(80f, result.reductionPercent(), 0.001f);
        }

        @Test
        @DisplayName("Interaction with armor - physical resistance is separate layer")
        void separateFromArmor_resistanceAfterArmor() {
            // Simulate: 100 damage → 50 after armor → then 75% physical resistance
            ComputedStats stats = ComputedStats.builder()
                .physicalResistance(40f) // 40% physical resistance
                .build();

            // After armor, 50 damage remains
            var result = CombatCalculator.applyPhysicalResistance(50f, stats);

            // 50 * (1 - 0.40) = 50 * 0.60 = 30
            assertEquals(30f, result.finalDamage(), 0.001f);
            assertEquals(20f, result.damageReduced(), 0.001f);
        }
    }
}
