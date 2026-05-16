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
 * Tests true damage calculation in the pipeline (Step 10).
 *
 * <p>Three independent sources of true damage:
 * <ol>
 *   <li>{@code trueDamage} — flat amount, bypasses all defenses</li>
 *   <li>{@code percentHitAsTrueDamage} — X% of total post-defense damage added as true</li>
 *   <li>{@code voidToTrueDamagePercent} — X% of post-defense Void damage added as true</li>
 * </ol>
 *
 * <p>All three stack additively. True damage is added AFTER defenses (Step 9)
 * and is NOT affected by crit (crit applies in Step 8, true damage added in Step 10).
 */
class TrueDamageCalculatorTest {

    private RPGDamageCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new RPGDamageCalculator();
    }

    // ==================== Flat True Damage ====================

    @Nested
    @DisplayName("Flat True Damage")
    class FlatTrueDamage {

        @Test
        @DisplayName("Flat true damage bypasses armor")
        void flatTrueDamage_addedAfterDefenses() {
            ComputedStats attackerStats = ComputedStats.builder()
                .trueDamage(30f)
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            ComputedStats defenderStats = ComputedStats.builder()
                .armor(10000f) // Max armor
                .build();

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                0f, attackerStats, null, defenderStats, null,
                AttackType.MELEE, false, 1);

            assertEquals(30f, result.trueDamage(), 0.1f,
                "True damage should bypass all armor");
            assertEquals(30f, result.totalDamage(), 0.1f,
                "Only true damage should remain");
        }

        @Test
        @DisplayName("Flat true damage is NOT multiplied by crit")
        void flatTrueDamage_notAffectedByCrit() {
            ComputedStats attackerStats = ComputedStats.builder()
                .trueDamage(30f)
                .criticalChance(100f)
                .criticalMultiplier(300f) // 3.0x
                .build();

            // Use heavy armor to eliminate physical — only true damage should show
            ComputedStats defenderStats = ComputedStats.builder()
                .armor(100000f) // Caps at 90% reduction
                .build();

            DamageBreakdown result = calculator.calculate(
                100f, attackerStats, null, defenderStats, null,
                AttackType.MELEE, false);

            assertTrue(result.wasCritical());
            // Physical: 100 × 3.0x crit = 300, then 90% armor = 30 physical
            // True: 30 flat (added AFTER crit step, not multiplied)
            assertEquals(30f, result.trueDamage(), 0.1f,
                "True damage should not be affected by crit multiplier");
        }
    }

    // ==================== Percent Hit As True Damage ====================

    @Nested
    @DisplayName("percentHitAsTrueDamage")
    class PercentHitAsTrueDamage {

        @Test
        @DisplayName("10% of post-defense total added as true")
        void percentHitAsTrueDamage_usesPostDefenseTotal() {
            ComputedStats attackerStats = ComputedStats.builder()
                .percentHitAsTrueDamage(10f) // 10%
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                100f, attackerStats, null, null, null,
                AttackType.MELEE, false, 1);

            // No defenses: 100 phys post-defense
            // True = 100 × 10% = 10
            assertEquals(10f, result.trueDamage(), 0.1f,
                "10% of 100 post-defense = 10 true");
            assertEquals(110f, result.totalDamage(), 0.1f,
                "100 phys + 10 true = 110");
        }

        @Test
        @DisplayName("Post-defense base reduced by armor → true uses reduced value")
        void percentHitAsTrueDamage_withHeavyArmor() {
            ComputedStats attackerStats = ComputedStats.builder()
                .percentHitAsTrueDamage(50f) // 50%
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            ComputedStats defenderStats = ComputedStats.builder()
                .armor(10000f) // ~90% reduction
                .build();

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                100f, attackerStats, null, defenderStats, null,
                AttackType.MELEE, false, 1);

            // Physical: 100 × (1 - 90%) = 10 post-defense
            float physDmg = result.physicalDamage();
            float expectedTrue = physDmg * 50f / 100f;
            assertEquals(expectedTrue, result.trueDamage(), 0.5f,
                "True damage should use post-defense total, not pre-defense");
        }
    }

    // ==================== Void To True Damage ====================

    @Nested
    @DisplayName("voidToTrueDamagePercent")
    class VoidToTrueDamage {

        @Test
        @DisplayName("20% of post-defense void added as true")
        void voidToTrueDamage_usesPostDefenseVoid() {
            ComputedStats attackerStats = ComputedStats.builder()
                .voidConversion(100f) // All physical → void
                .voidToTrueDamagePercent(20f)
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            ComputedStats defenderStats = ComputedStats.builder().build();
            ElementalStats defenderElemental = new ElementalStats();
            defenderElemental.setResistance(ElementType.VOID, 50.0); // 50% void resist

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                100f, attackerStats, null, defenderStats, defenderElemental,
                AttackType.MELEE, false, 1);

            // 100 phys → 100 void (conversion)
            // 100 void × (1 - 50%) = 50 void post-defense
            // True = 50 × 20% = 10
            float voidDmg = result.getElementalDamage(ElementType.VOID);
            float expectedTrue = voidDmg * 20f / 100f;
            assertEquals(expectedTrue, result.trueDamage(), 0.5f,
                "True damage from void should use post-defense void amount");
        }

        @Test
        @DisplayName("Zero void damage → zero true damage from void source")
        void voidToTrueDamage_zeroVoid_noTrue() {
            ComputedStats attackerStats = ComputedStats.builder()
                .voidToTrueDamagePercent(50f) // 50% but no void damage
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                100f, attackerStats, null, null, null,
                AttackType.MELEE, false, 1);

            assertEquals(0f, result.trueDamage(), 0.001f,
                "No void damage → no true damage from void source");
            assertEquals(100f, result.totalDamage(), 0.1f);
        }
    }

    // ==================== Stacking ====================

    @Nested
    @DisplayName("True Damage Stacking")
    class TrueDamageStacking {

        @Test
        @DisplayName("All three true damage sources stack")
        void allTrueSources_stack() {
            ComputedStats attackerStats = ComputedStats.builder()
                .trueDamage(5f) // +5 flat
                .percentHitAsTrueDamage(10f) // +10% of post-defense
                .voidConversion(100f) // All phys → void
                .voidToTrueDamagePercent(20f) // +20% of void
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                100f, attackerStats, null, null, null,
                AttackType.MELEE, false, 1);

            // 100 phys → 100 void (no resist = full void)
            // flat true = 5
            // pctAsTrueDmg = 100 × 10% = 10
            // voidToTrue = 100 × 20% = 20
            // Total true = 5 + 10 + 20 = 35
            assertEquals(35f, result.trueDamage(), 0.5f,
                "All three sources should stack: 5 + 10 + 20 = 35");
            assertEquals(135f, result.totalDamage(), 0.5f,
                "100 void + 35 true = 135");
        }

        @Test
        @DisplayName("True damage included in totalDamage()")
        void trueDamage_inTotalDamage() {
            ComputedStats attackerStats = ComputedStats.builder()
                .trueDamage(25f)
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                100f, attackerStats, null, null, null,
                AttackType.MELEE, false, 1);

            assertEquals(125f, result.totalDamage(), 0.1f,
                "Total should include 100 phys + 25 true");
            assertEquals(25f, result.trueDamage(), 0.1f);
        }

        @Test
        @DisplayName("No true damage stats → zero true damage")
        void trueDamage_zeroWhenNoSources() {
            ComputedStats attackerStats = ComputedStats.builder()
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                100f, attackerStats, null, null, null,
                AttackType.MELEE, false, 1);

            assertEquals(0f, result.trueDamage(), 0.001f,
                "No true damage stats → 0 true damage");
        }
    }

    // ==================== True Damage + Conversion ====================

    @Nested
    @DisplayName("True Damage with Conversion")
    class TrueDamageWithConversion {

        @Test
        @DisplayName("100% void conversion + voidToTrue uses post-defense void")
        void trueDamage_withConversion() {
            ComputedStats attackerStats = ComputedStats.builder()
                .voidConversion(100f)
                .voidToTrueDamagePercent(10f)
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                200f, attackerStats, null, null, null,
                AttackType.MELEE, false, 1);

            // 200 phys → 200 void (100% conversion), no defenses
            // True = 200 × 10% = 20
            assertEquals(200f, result.getElementalDamage(ElementType.VOID), 0.1f);
            assertEquals(20f, result.trueDamage(), 0.1f);
            assertEquals(220f, result.totalDamage(), 0.1f);
        }
    }
}
