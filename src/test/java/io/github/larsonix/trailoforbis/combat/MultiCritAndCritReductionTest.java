package io.github.larsonix.trailoforbis.combat;

import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.elemental.ElementalStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests multicrit tiers and critical damage reduction.
 *
 * <p>Multicrit formula: {@code effectiveMult = 1 + tier × (baseMult - 1)}
 * <ul>
 *   <li>critChance 100% → guaranteed tier 1</li>
 *   <li>critChance 200% → guaranteed tier 2</li>
 *   <li>critChance 150% → guaranteed tier 1 + 50% chance for tier 2</li>
 *   <li>Remainder is rolled per-hit via ThreadLocalRandom</li>
 * </ul>
 *
 * <p>Crit reduction formula: {@code effective = 1 + (rawMult - 1) × (1 - min(reduction, 75) / 100)}
 */
class MultiCritAndCritReductionTest {

    private RPGDamageCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new RPGDamageCalculator();
    }

    // ==================== Multicrit Tiers ====================

    @Nested
    @DisplayName("Multicrit Tier Calculation")
    class MulticritTiers {

        @Test
        @DisplayName("100% crit chance → tier 1, standard multiplier")
        void singleCrit_tier1_standardMultiplier() {
            ComputedStats stats = ComputedStats.builder()
                .criticalChance(100f)
                .criticalMultiplier(200f) // 2.0x
                .build();

            // Run multiple times — 100% chance means always crit, always tier 1
            for (int i = 0; i < 20; i++) {
                DamageBreakdown result = calculator.calculate(
                    100f, stats, null, null, null, AttackType.MELEE, false);

                assertTrue(result.wasCritical(), "100% crit should always crit");
                assertEquals(1, result.critTier(), "100% crit = tier 1");
                assertEquals(2.0f, result.critMultiplier(), 0.01f,
                    "Tier 1 with 200% mult = 2.0x");
                assertEquals(200f, result.totalDamage(), 0.1f);
            }
        }

        @Test
        @DisplayName("0% crit chance → tier 0, no crit")
        void noCrit_zeroChance() {
            ComputedStats stats = ComputedStats.builder()
                .criticalChance(0f)
                .criticalMultiplier(200f)
                .build();

            for (int i = 0; i < 20; i++) {
                DamageBreakdown result = calculator.calculate(
                    100f, stats, null, null, null, AttackType.MELEE, false);

                assertFalse(result.wasCritical());
                assertEquals(0, result.critTier());
                assertEquals(100f, result.totalDamage(), 0.1f);
            }
        }

        @Test
        @DisplayName("200% crit chance → guaranteed tier 2")
        void guaranteedTier2_200percent() {
            ComputedStats stats = ComputedStats.builder()
                .criticalChance(200f)
                .criticalMultiplier(200f) // baseMult = 2.0, bonus = 1.0
                .build();

            for (int i = 0; i < 20; i++) {
                DamageBreakdown result = calculator.calculate(
                    100f, stats, null, null, null, AttackType.MELEE, false);

                assertTrue(result.wasCritical());
                assertEquals(2, result.critTier(), "200% = guaranteed 2 tiers");
                // effectiveMult = 1 + 2 × (2.0 - 1) = 1 + 2 = 3.0x
                assertEquals(3.0f, result.critMultiplier(), 0.01f);
                assertEquals(300f, result.totalDamage(), 0.1f);
            }
        }

        @Test
        @DisplayName("300% crit chance → guaranteed tier 3")
        void guaranteedTier3_300percent() {
            ComputedStats stats = ComputedStats.builder()
                .criticalChance(300f)
                .criticalMultiplier(200f)
                .build();

            for (int i = 0; i < 20; i++) {
                DamageBreakdown result = calculator.calculate(
                    100f, stats, null, null, null, AttackType.MELEE, false);

                assertTrue(result.wasCritical());
                assertEquals(3, result.critTier(), "300% = guaranteed 3 tiers");
                // effectiveMult = 1 + 3 × 1.0 = 4.0x
                assertEquals(4.0f, result.critMultiplier(), 0.01f);
                assertEquals(400f, result.totalDamage(), 0.1f);
            }
        }

        @Test
        @DisplayName("150% crit: statistical distribution of tier 1 vs tier 2")
        void remainder_150percentChance_statisticalDistribution() {
            ComputedStats stats = ComputedStats.builder()
                .criticalChance(150f) // Guaranteed tier 1, 50% chance tier 2
                .criticalMultiplier(200f)
                .build();

            int tier1Count = 0;
            int tier2Count = 0;
            int runs = 2000;

            for (int i = 0; i < runs; i++) {
                DamageBreakdown result = calculator.calculate(
                    100f, stats, null, null, null, AttackType.MELEE, false);

                assertTrue(result.wasCritical(), "150% crit should always crit");
                assertTrue(result.critTier() >= 1, "Should be at least tier 1");
                assertTrue(result.critTier() <= 2, "Should be at most tier 2");

                if (result.critTier() == 1) tier1Count++;
                else tier2Count++;
            }

            // With 50% remainder, expect roughly 50/50 distribution
            // Allow ±10% tolerance for randomness
            float tier2Ratio = (float) tier2Count / runs;
            assertTrue(tier2Ratio > 0.35f && tier2Ratio < 0.65f,
                "Expected ~50% tier 2 rate, got " + (tier2Ratio * 100f) + "%"
                    + " (tier1=" + tier1Count + ", tier2=" + tier2Count + ")");
        }

        @Test
        @DisplayName("critTier field populated correctly in DamageBreakdown")
        void critTierField_populated() {
            ComputedStats stats = ComputedStats.builder()
                .criticalChance(200f)
                .criticalMultiplier(150f)
                .build();

            DamageBreakdown result = calculator.calculate(
                100f, stats, null, null, null, AttackType.MELEE, false);

            assertEquals(2, result.critTier());
            // effectiveMult = 1 + 2 × (1.5 - 1) = 1 + 2 × 0.5 = 2.0x
            assertEquals(2.0f, result.critMultiplier(), 0.01f);
        }
    }

    // ==================== Crit Reduction ====================

    @Nested
    @DisplayName("Critical Damage Reduction")
    class CritReduction {

        @Test
        @DisplayName("50% crit reduction halves the bonus portion")
        void critReduction_reducesBonus() {
            ComputedStats attackerStats = ComputedStats.builder()
                .criticalChance(100f)
                .criticalMultiplier(200f) // 2.0x raw
                .build();

            ComputedStats defenderStats = ComputedStats.builder()
                .criticalReduction(50f) // 50% reduction on BONUS
                .build();

            DamageBreakdown result = calculator.calculate(
                100f, attackerStats, null, defenderStats, null,
                AttackType.MELEE, false, 1.0f, 1);

            assertTrue(result.wasCritical());
            // Raw mult = 2.0, bonus = 1.0, reduced bonus = 1.0 × (1 - 50/100) = 0.5
            // Effective = 1 + 0.5 = 1.5x
            assertEquals(1.5f, result.critMultiplier(), 0.01f,
                "50% crit reduction: 2.0x → bonus 1.0 × 50% = 0.5 → 1.5x");
            assertEquals(150f, result.totalDamage(), 0.5f);
        }

        @Test
        @DisplayName("Crit reduction capped at 75%")
        void critReduction_cappedAt75() {
            ComputedStats attackerStats = ComputedStats.builder()
                .criticalChance(100f)
                .criticalMultiplier(200f)
                .build();

            ComputedStats defenderStats = ComputedStats.builder()
                .criticalReduction(100f) // Should be capped to 75
                .build();

            DamageBreakdown result = calculator.calculate(
                100f, attackerStats, null, defenderStats, null,
                AttackType.MELEE, false, 1.0f, 1);

            // Capped at 75%: bonus = 1.0 × (1 - 75/100) = 0.25 → effective = 1.25x
            assertEquals(1.25f, result.critMultiplier(), 0.01f,
                "100% reduction capped to 75%: 2.0x → 1.25x");
            assertEquals(125f, result.totalDamage(), 0.5f);
        }

        @Test
        @DisplayName("Zero crit reduction has no effect")
        void critReduction_zeroCritReduction_noEffect() {
            ComputedStats attackerStats = ComputedStats.builder()
                .criticalChance(100f)
                .criticalMultiplier(200f)
                .build();

            ComputedStats defenderStats = ComputedStats.builder()
                .criticalReduction(0f)
                .build();

            DamageBreakdown result = calculator.calculate(
                100f, attackerStats, null, defenderStats, null,
                AttackType.MELEE, false, 1.0f, 1);

            assertEquals(2.0f, result.critMultiplier(), 0.01f,
                "0% reduction should leave crit unchanged");
            assertEquals(200f, result.totalDamage(), 0.1f);
        }

        @Test
        @DisplayName("Crit reduction works with multicrit tier 2")
        void critReduction_withMulticrit() {
            ComputedStats attackerStats = ComputedStats.builder()
                .criticalChance(200f) // Guaranteed tier 2
                .criticalMultiplier(200f)
                .build();

            ComputedStats defenderStats = ComputedStats.builder()
                .criticalReduction(50f)
                .build();

            DamageBreakdown result = calculator.calculate(
                100f, attackerStats, null, defenderStats, null,
                AttackType.MELEE, false, 1.0f, 1);

            // Tier 2 raw mult = 1 + 2 × 1.0 = 3.0x
            // Bonus = 2.0, reduced = 2.0 × (1 - 0.5) = 1.0
            // Effective = 1 + 1.0 = 2.0x
            assertEquals(2.0f, result.critMultiplier(), 0.01f,
                "Tier 2 (3.0x) with 50% reduction → 2.0x");
            assertEquals(200f, result.totalDamage(), 0.5f);
        }
    }

    // ==================== DOT + Crit Interaction ====================

    @Nested
    @DisplayName("DOT and Crit Interaction")
    class DotCritInteraction {

        @Test
        @DisplayName("DOT skips crit even with 200% crit chance")
        void dotSkipsCrit_evenWithHighChance() {
            ComputedStats stats = ComputedStats.builder()
                .criticalChance(200f)
                .criticalMultiplier(300f)
                .build();

            DamageBreakdown result = calculator.calculate(
                100f, stats, null, null, null,
                AttackType.MELEE, true, // isDOT = true
                1.0f, 1);

            assertFalse(result.wasCritical(), "DOT should never crit");
            assertEquals(0, result.critTier(), "DOT crit tier should be 0");
            assertEquals(100f, result.totalDamage(), 0.5f,
                "DOT damage should not be modified by crit");
        }
    }

    // ==================== Crit + Conditionals Order ====================

    @Nested
    @DisplayName("Crit Applied After Conditionals")
    class CritAfterConditionals {

        @Test
        @DisplayName("Conditional multiplier baked before crit")
        void critApplied_afterConditionals() {
            ComputedStats stats = ComputedStats.builder()
                .criticalChance(100f)
                .criticalMultiplier(200f) // 2.0x
                .build();

            // With conditional multiplier = 1.5x
            DamageBreakdown result = calculator.calculate(
                100f, stats, null, null, null,
                AttackType.MELEE, false, 1.5f, 1);

            // Step 7: 100 × 1.5 = 150 (conditional)
            // Step 8: 150 × 2.0 = 300 (crit on top of conditional)
            assertTrue(result.wasCritical());
            assertEquals(300f, result.totalDamage(), 0.5f,
                "Crit should multiply the conditional'd damage: 100 × 1.5 × 2.0 = 300");
        }
    }
}
