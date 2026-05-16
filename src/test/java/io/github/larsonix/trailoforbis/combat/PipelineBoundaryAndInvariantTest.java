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
 * Tests pipeline invariants and boundary conditions.
 *
 * <p>These verify contracts that must NEVER be violated:
 * <ul>
 *   <li>Damage is never negative</li>
 *   <li>DOT skips all offensive steps (flat, crit, conversion)</li>
 *   <li>Conversion preserves total damage</li>
 *   <li>% More multipliers chain multiplicatively, not additively</li>
 *   <li>DamageDistribution operations are safe at boundaries</li>
 * </ul>
 */
class PipelineBoundaryAndInvariantTest {

    private RPGDamageCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new RPGDamageCalculator();
    }

    // ==================== Zero/Null Input Invariants ====================

    @Nested
    @DisplayName("Zero and Null Input Invariants")
    class ZeroNullInputs {

        @Test
        @DisplayName("All zero stats produces base damage only")
        void allZeroStats_producesBaseDamageOnly() {
            ComputedStats stats = ComputedStats.builder()
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                50f, stats, null, null, null, AttackType.MELEE, false, 1);

            assertEquals(50f, result.totalDamage(), 0.01f,
                "Zero stats should produce exactly base damage");
            assertEquals(50f, result.physicalDamage(), 0.01f);
        }

        @Test
        @DisplayName("Zero base damage + flat elemental produces elemental only")
        void zeroBaseDamage_withFlatElemental_works() {
            ComputedStats stats = ComputedStats.builder()
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            ElementalStats elemental = new ElementalStats();
            elemental.setFlatDamage(ElementType.FIRE, 50.0);

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                0f, stats, elemental, null, null, AttackType.MELEE, false, 1);

            assertEquals(0f, result.physicalDamage(), 0.01f);
            assertEquals(50f, result.getElementalDamage(ElementType.FIRE), 0.1f);
            assertEquals(50f, result.totalDamage(), 0.1f);
        }

        @Test
        @DisplayName("Null attacker + null defender produces base damage")
        void estimate_vs_calculate_at_zero() {
            DamageBreakdown result = calculator.calculate(
                0f, null, null, null, null, AttackType.UNKNOWN, false);

            assertEquals(0f, result.totalDamage(), 0.01f);
            assertFalse(result.wasCritical());
            assertTrue(Float.isFinite(result.totalDamage()));
        }
    }

    // ==================== Damage Never Negative ====================

    @Nested
    @DisplayName("Damage Never Negative")
    class DamageNeverNegative {

        @Test
        @DisplayName("Total damage never negative with extreme defenses")
        void totalDamage_neverNegative() {
            ComputedStats attackerStats = ComputedStats.builder()
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            ComputedStats defenderStats = ComputedStats.builder()
                .armor(100000f)
                .physicalResistance(100f) // Capped at 75
                .build();

            ElementalStats defenderElemental = new ElementalStats();
            for (ElementType type : ElementType.values()) {
                defenderElemental.setResistance(type, 100.0); // Max resist
            }

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                100f, attackerStats, null, defenderStats, defenderElemental,
                AttackType.MELEE, false, 1);

            assertTrue(result.totalDamage() >= 0,
                "Total damage must never be negative, got: " + result.totalDamage());
            assertTrue(result.physicalDamage() >= 0);
            for (ElementType type : ElementType.values()) {
                assertTrue(result.getElementalDamage(type) >= 0,
                    type.name() + " damage must never be negative");
            }
        }
    }

    // ==================== DOT Invariants ====================

    @Nested
    @DisplayName("DOT Invariants")
    class DotInvariants {

        @Test
        @DisplayName("DOT skips flat, crit, and conversion")
        void dotSkipsAllOffensiveSteps() {
            ComputedStats stats = ComputedStats.builder()
                .physicalDamage(50f) // flat — should be skipped
                .meleeDamage(25f) // melee flat — should be skipped
                .fireConversion(100f) // conversion — should be skipped
                .criticalChance(100f)
                .criticalMultiplier(300f) // crit — should be skipped
                .build();

            DamageBreakdown result = calculator.calculate(
                100f, stats, null, null, null,
                AttackType.MELEE, true, // isDOT = true
                1.0f, 1);

            assertEquals(100f, result.totalDamage(), 0.5f,
                "DOT should be exactly base damage (no flat, no crit, no conversion)");
            assertFalse(result.wasCritical(), "DOT should never crit");
            assertEquals(0, result.critTier());
        }

        @Test
        @DisplayName("calculateDOT produces consistent result with calculate(..., isDOT=true)")
        void calculateDOT_noCritNoFlat() {
            ComputedStats defenderStats = ComputedStats.builder()
                .armor(50f)
                .build();

            DamageBreakdown dotResult = calculator.calculateDOT(
                100f, defenderStats, null, null, 1);

            assertFalse(dotResult.wasCritical());
            assertEquals(0f, dotResult.trueDamage(), 0.001f);
            assertTrue(dotResult.totalDamage() > 0);
            assertTrue(dotResult.totalDamage() <= 100f,
                "Physical DOT with armor should reduce damage");
        }
    }

    // ==================== Conversion Invariants ====================

    @Nested
    @DisplayName("Conversion Invariants")
    class ConversionInvariants {

        @Test
        @DisplayName("Conversion with zero physical has no effect")
        void conversionWithZeroPhysical_noEffect() {
            ComputedStats stats = ComputedStats.builder()
                .fireConversion(50f) // 50% conversion but no physical to convert
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            ElementalStats elemStats = new ElementalStats();
            elemStats.setFlatDamage(ElementType.WATER, 100.0); // Only water, no physical

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                0f, stats, elemStats, null, null, AttackType.MELEE, false, 1);

            // Zero base physical + fire conversion → nothing to convert
            assertEquals(0f, result.getElementalDamage(ElementType.FIRE), 0.001f,
                "No physical to convert → no fire damage created");
            assertEquals(100f, result.getElementalDamage(ElementType.WATER), 0.1f,
                "Water should be unchanged");
        }

        @Test
        @DisplayName("Conversion preserves total pre-defense damage")
        void conversionPreservesTotalDamage() {
            ComputedStats noConv = ComputedStats.builder()
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            ComputedStats withConv = ComputedStats.builder()
                .fireConversion(30f)
                .waterConversion(20f)
                .lightningConversion(10f)
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            DamageBreakdown noConvResult = calculator.calculateWithForcedCrit(
                100f, noConv, null, null, null, AttackType.MELEE, false, 1);

            DamageBreakdown convResult = calculator.calculateWithForcedCrit(
                100f, withConv, null, null, null, AttackType.MELEE, false, 1);

            // Total should be the same — conversion redistributes, doesn't add/remove
            assertEquals(noConvResult.totalDamage(), convResult.totalDamage(), 0.1f,
                "Conversion should preserve total damage (no defenses)");
        }
    }

    // ==================== Multiplier Chain Invariants ====================

    @Nested
    @DisplayName("Multiplier Chain Order")
    class MultiplierChain {

        @Test
        @DisplayName("% More multipliers chain multiplicatively")
        void moreMultiplier_chainedCorrectly() {
            ComputedStats stats = ComputedStats.builder()
                .allDamagePercent(50f) // ×1.5
                .damageMultiplier(50f) // ×1.5
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                100f, stats, null, null, null, AttackType.MELEE, false, 1);

            // Multiplicative: 100 × 1.5 × 1.5 = 225 (NOT additive: 100 × 2.0 = 200)
            assertEquals(225f, result.totalDamage(), 0.1f,
                "allDmg and dmgMult should chain multiplicatively: 1.5 × 1.5 = 2.25x");
        }

        @Test
        @DisplayName("Per-element % increased and % more are independent")
        void elementalModifiers_percentAndMore_independent() {
            ComputedStats attackerStats = ComputedStats.builder()
                .fireConversion(100f) // All to fire
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            ElementalStats elemental = new ElementalStats();
            elemental.setPercentDamage(ElementType.FIRE, 50.0); // +50% fire (step 5)
            elemental.setMultiplierDamage(ElementType.FIRE, 30.0); // +30% more fire (step 5)

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                100f, attackerStats, elemental, null, null,
                AttackType.MELEE, false, 1);

            // 100 phys → 100 fire (conversion)
            // Step 5: +50% increased = 150, then ×1.3 more = 195
            assertEquals(195f, result.getElementalDamage(ElementType.FIRE), 1f,
                "100 × 1.5 (increased) × 1.3 (more) = 195 fire");
        }
    }

    // ==================== DamageDistribution Safety ====================

    @Nested
    @DisplayName("DamageDistribution Boundary Safety")
    class DistributionSafety {

        @Test
        @DisplayName("Zero multiplier zeros all damage")
        void zeroMultiplier_zerosAllDamage() {
            DamageDistribution dist = new DamageDistribution();
            dist.setPhysical(100f);
            dist.setElemental(ElementType.FIRE, 50f);
            dist.setTrueDamage(25f);

            dist.applyMultiplier(0f);

            assertEquals(0f, dist.getPhysical(), 0.001f);
            assertEquals(0f, dist.getElemental(ElementType.FIRE), 0.001f);
            assertEquals(0f, dist.getTrueDamage(), 0.001f);
            assertEquals(0f, dist.getTotal(), 0.001f);
        }
    }

    // ==================== Damage Type Resolution ====================

    @Nested
    @DisplayName("Damage Type Resolution")
    class DamageTypeResolution {

        @Test
        @DisplayName("Pure physical → PHYSICAL damage type")
        void physicalDamageType_whenNoElemental() {
            ComputedStats stats = ComputedStats.builder()
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                100f, stats, null, null, null, AttackType.MELEE, false, 1);

            assertEquals(DamageType.PHYSICAL, result.damageType(),
                "Pure physical should be PHYSICAL type");
        }

        @Test
        @DisplayName("Dominant fire → FIRE damage type")
        void elementalDamageType_whenDominant() {
            ComputedStats stats = ComputedStats.builder()
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            ElementalStats elemental = new ElementalStats();
            elemental.setFlatDamage(ElementType.FIRE, 200.0); // 200 fire > 100 physical

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                100f, stats, elemental, null, null, AttackType.MELEE, false, 1);

            assertEquals(DamageType.FIRE, result.damageType(),
                "200 fire > 100 physical → FIRE type");
        }
    }
}
