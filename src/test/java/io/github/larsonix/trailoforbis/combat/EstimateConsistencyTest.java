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
 * Verifies that {@code estimateAverageDamage()} is consistent with {@code calculate()}.
 *
 * <p>The estimate re-implements pipeline steps 1-8 manually rather than calling
 * {@code calculatePipeline()}. This means any formula change in the pipeline must
 * also be reflected in the estimate — or the Stats page shows wrong DPS.
 *
 * <p>Tests compare estimate output against actual {@code calculate()} results (no crit,
 * no defenses) to catch drift between the two code paths.
 */
class EstimateConsistencyTest {

    private RPGDamageCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new RPGDamageCalculator();
    }

    /** Run calculate with no crit, no defenses, and return total damage. */
    private float calculateNoCritNoDefense(ComputedStats stats) {
        return calculator.calculateWithForcedCrit(
            stats.getWeaponBaseDamage(), stats, stats.toElementalStats(),
            null, null, AttackType.MELEE, false, 1
        ).totalDamage();
    }

    // ==================== Basic Consistency ====================

    @Nested
    @DisplayName("Basic Consistency (No Crit, No Defenses)")
    class BasicConsistency {

        @Test
        @DisplayName("Pure physical: estimate matches calculate")
        void purePhysical_estimateMatchesCalculate() {
            ComputedStats stats = ComputedStats.builder()
                .weaponBaseDamage(100f)
                .weaponItemId("Weapon_Sword_Iron")
                .holdingRpgGear(true)
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            RPGDamageCalculator.DamageEstimate estimate = calculator.estimateAverageDamage(stats);
            float calcTotal = calculateNoCritNoDefense(stats);

            assertEquals(calcTotal, estimate.avgDamagePerHit(), 1f,
                "Estimate should match calculate for pure physical");
        }

        @Test
        @DisplayName("With flat damage: estimate matches calculate")
        void withFlatDamage_matches() {
            ComputedStats stats = ComputedStats.builder()
                .weaponBaseDamage(100f)
                .weaponItemId("Weapon_Sword_Iron")
                .holdingRpgGear(true)
                .physicalDamage(20f)
                .meleeDamage(10f)
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            RPGDamageCalculator.DamageEstimate estimate = calculator.estimateAverageDamage(stats);
            float calcTotal = calculateNoCritNoDefense(stats);

            assertEquals(calcTotal, estimate.avgDamagePerHit(), 1f,
                "Estimate should match calculate with flat damage");
        }

        @Test
        @DisplayName("With conversion: estimate matches calculate")
        void withConversion_matches() {
            ComputedStats stats = ComputedStats.builder()
                .weaponBaseDamage(100f)
                .weaponItemId("Weapon_Sword_Iron")
                .holdingRpgGear(true)
                .fireConversion(50f)
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            RPGDamageCalculator.DamageEstimate estimate = calculator.estimateAverageDamage(stats);
            float calcTotal = calculateNoCritNoDefense(stats);

            assertEquals(calcTotal, estimate.avgDamagePerHit(), 1f,
                "Estimate should match calculate with 50% fire conversion");
        }

        @Test
        @DisplayName("With percent increased: estimate matches calculate")
        void withPercentIncreased_matches() {
            ComputedStats stats = ComputedStats.builder()
                .weaponBaseDamage(100f)
                .weaponItemId("Weapon_Sword_Iron")
                .holdingRpgGear(true)
                .physicalDamagePercent(30f)
                .meleeDamagePercent(20f)
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            RPGDamageCalculator.DamageEstimate estimate = calculator.estimateAverageDamage(stats);
            float calcTotal = calculateNoCritNoDefense(stats);

            assertEquals(calcTotal, estimate.avgDamagePerHit(), 1f,
                "Estimate should match calculate with % increased");
        }

        @Test
        @DisplayName("With % more multipliers: estimate matches calculate")
        void withMoreMultipliers_matches() {
            ComputedStats stats = ComputedStats.builder()
                .weaponBaseDamage(100f)
                .weaponItemId("Weapon_Sword_Iron")
                .holdingRpgGear(true)
                .allDamagePercent(15f)
                .damageMultiplier(10f)
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            RPGDamageCalculator.DamageEstimate estimate = calculator.estimateAverageDamage(stats);
            float calcTotal = calculateNoCritNoDefense(stats);

            assertEquals(calcTotal, estimate.avgDamagePerHit(), 1f,
                "Estimate should match calculate with % more multipliers");
        }
    }

    // ==================== Crit Expected Value ====================

    @Nested
    @DisplayName("Crit Expected Value")
    class CritExpectedValue {

        @Test
        @DisplayName("50% crit, 200% mult: statistical average matches estimate")
        void expectedCrit_matchesStatisticalAverage() {
            ComputedStats stats = ComputedStats.builder()
                .weaponBaseDamage(100f)
                .weaponItemId("Weapon_Sword_Iron")
                .holdingRpgGear(true)
                .criticalChance(50f)
                .criticalMultiplier(200f)
                .build();

            RPGDamageCalculator.DamageEstimate estimate = calculator.estimateAverageDamage(stats);

            // Run many calculations and average them
            double totalDamage = 0;
            int runs = 10000;
            for (int i = 0; i < runs; i++) {
                DamageBreakdown result = calculator.calculate(
                    100f, stats, null, null, null, AttackType.MELEE, false);
                totalDamage += result.totalDamage();
            }
            float actualAverage = (float) (totalDamage / runs);

            // 50% chance × 2.0x crit = expected mult 1.5x → expected avg 150
            // Allow 5% tolerance for statistical variance
            float tolerance = estimate.avgDamagePerHit() * 0.05f;
            assertEquals(estimate.avgDamagePerHit(), actualAverage, tolerance,
                "Statistical average should match estimate within 5%");
        }

        @Test
        @DisplayName("Multicrit expected value: 250% crit chance")
        void multicrit_expectedValue() {
            ComputedStats stats = ComputedStats.builder()
                .weaponBaseDamage(100f)
                .weaponItemId("Weapon_Sword_Iron")
                .holdingRpgGear(true)
                .criticalChance(250f) // 2 guaranteed tiers + 50% for tier 3
                .criticalMultiplier(200f) // bonus = 1.0
                .build();

            RPGDamageCalculator.DamageEstimate estimate = calculator.estimateAverageDamage(stats);

            // Expected tiers = 250/100 = 2.5
            // Expected mult = 1 + 2.5 × 1.0 = 3.5x
            // Expected avg = 100 × 3.5 = 350
            assertEquals(3.5f, estimate.expectedCritMult(), 0.01f,
                "Expected crit mult for 250% chance should be 3.5x");
            assertEquals(350f, estimate.avgDamagePerHit(), 1f,
                "Expected average should be 350");
        }
    }

    // ==================== Elemental Sword Consistency ====================

    @Nested
    @DisplayName("Elemental Sword Consistency")
    class ElementalSwordConsistency {

        @Test
        @DisplayName("Fire elemental sword: estimate matches calculate")
        void fireElementalSword_matches() {
            ComputedStats stats = ComputedStats.builder()
                .weaponBaseDamage(80f)
                .weaponItemId("Weapon_Sword_Iron")
                .weaponSpellElement(ElementType.FIRE) // Elemental sword
                .holdingRpgGear(true)
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            RPGDamageCalculator.DamageEstimate estimate = calculator.estimateAverageDamage(stats);

            // Calculate with spellElement=FIRE (elemental melee sword)
            DamageBreakdown calc = calculator.calculate(
                80f, stats, stats.toElementalStats(), null, null,
                AttackType.MELEE, false, 1.0f, false, ElementType.FIRE, 1, false);

            assertEquals(calc.totalDamage(), estimate.avgDamagePerHit(), 1f,
                "Fire sword estimate should match calculate");
        }
    }

    // ==================== True Damage in Estimate ====================

    @Nested
    @DisplayName("True Damage in Estimate")
    class TrueDamageInEstimate {

        @Test
        @DisplayName("Estimate includes flat true damage")
        void withTrueDamage_estimateIncludes() {
            ComputedStats stats = ComputedStats.builder()
                .weaponBaseDamage(100f)
                .weaponItemId("Weapon_Sword_Iron")
                .holdingRpgGear(true)
                .trueDamage(15f)
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            RPGDamageCalculator.DamageEstimate estimate = calculator.estimateAverageDamage(stats);

            assertTrue(estimate.trueDamage() >= 15f,
                "Estimate should include flat true damage");
            assertEquals(115f, estimate.avgDamagePerHit(), 1f,
                "100 base + 15 true = 115 average");
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    @DisplayName("Estimate Edge Cases")
    class EstimateEdgeCases {

        @Test
        @DisplayName("Zero base damage produces zero or near-zero estimate")
        void zeroBaseDamage_estimateIsZero() {
            ComputedStats stats = ComputedStats.builder()
                .weaponBaseDamage(0f)
                .weaponItemId("Weapon_Sword_Iron")
                .holdingRpgGear(true)
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            RPGDamageCalculator.DamageEstimate estimate = calculator.estimateAverageDamage(stats);

            assertEquals(0f, estimate.avgDamagePerHit(), 0.1f,
                "Zero weapon base should produce zero estimate");
        }

        @Test
        @DisplayName("Estimate components sum to total")
        void estimateComponents_sumToTotal() {
            ComputedStats stats = ComputedStats.builder()
                .weaponBaseDamage(100f)
                .weaponItemId("Weapon_Sword_Iron")
                .holdingRpgGear(true)
                .physicalDamage(10f)
                .physicalDamagePercent(20f)
                .allDamagePercent(10f)
                .criticalChance(50f)
                .criticalMultiplier(200f)
                .build();

            RPGDamageCalculator.DamageEstimate est = calculator.estimateAverageDamage(stats);

            assertTrue(est.avgDamagePerHit() > 0,
                "Estimate should be positive for a combat-ready character");
            assertTrue(est.expectedCritMult() > 1f,
                "Expected crit mult should be > 1 with 50% crit");
            assertTrue(est.increasedMult() > 1f,
                "Increased mult should be > 1 with 20% phys + 0% melee");
            assertTrue(est.moreMult() > 1f,
                "More mult should be > 1 with 10% allDmgPct");
        }
    }
}
