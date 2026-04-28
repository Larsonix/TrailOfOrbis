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
 * Documentation tests for the RPG damage calculation formula.
 *
 * <p>This test class serves two purposes:
 * <ol>
 *   <li>Document the exact damage formula for developers</li>
 *   <li>Protect against formula regressions with explicit value verification</li>
 * </ol>
 *
 * <h2>Damage Formula (PoE-style)</h2>
 *
 * <p>The damage calculation follows this order:
 * <pre>
 * 1. BASE DAMAGE
 *    - From weapon implicit (RPG gear) OR vanilla weapon damage
 *
 * 2. FLAT PHYSICAL DAMAGE (additive)
 *    - + physical_damage (from STR attribute)
 *    - + melee_damage (if MELEE attack type)
 *
 * 3. FLAT ELEMENTAL DAMAGE (additive, early so it benefits from modifiers + crit)
 *    - + fire_damage, cold_damage, lightning_damage, chaos_damage
 *
 * 4. DAMAGE CONVERSION (physical → elemental)
 *    - Happens EARLY so converted damage benefits from elemental modifiers
 *    - Capped at 100% total conversion
 *    - Proportionally scaled if overcapped
 *
 * 5. % INCREASED PHYSICAL (sum all, apply once)
 *    damage × (1 + SUM(%) / 100)
 *    - physical_damage_percent
 *    - melee_damage_percent (if MELEE)
 *    - projectile_damage_percent (if PROJECTILE)
 *    - area_damage_percent (if AREA)
 *    - damage_percent (global)
 *
 * 6. ELEMENTAL MODIFIERS (% increased and % more per element)
 *    - fire_damage_percent, cold_damage_percent, etc.
 *    - NOW includes converted damage!
 *
 * 7. % MORE (multiplicative chain, applies to ALL damage)
 *    damage × (1 + more1/100) × (1 + more2/100) × ...
 *    - all_damage_percent
 *    - damage_multiplier
 *
 * 8. CONDITIONAL MULTIPLIERS
 *    damage × conditionalMult
 *    - Attack type multiplier (from vanilla weapon profile)
 *    - Realm damage bonus
 *    - Execute bonus (vs low HP)
 *    - Damage vs Frozen/Shocked
 *
 * 9. CRITICAL STRIKE (one roll, applies to ALL damage types)
 *    if crit: damage × (critical_multiplier / 100)
 *    - Applied AFTER all scaling so it multiplies BOTH phys AND elemental
 *
 * 10. DEFENSES (applied per damage type)
 *    - Armor (physical): damage × (1 - armor / (armor + rawDamage × 10))
 *    - Physical Resistance: damage × (1 - physResist / 100)
 *    - Elemental Resistance: damage × (1 - elemResist / 100), capped at 75%
 *
 * 11. TRUE DAMAGE (added at the end, bypasses all defenses)
 *    - + true_damage
 * </pre>
 */
class DamageFormulaDocTest {

    private RPGDamageCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new RPGDamageCalculator();
    }

    // =========================================================================
    // STEP-BY-STEP FORMULA VERIFICATION
    // =========================================================================

    @Nested
    @DisplayName("Step 1-2: Base + Flat Damage")
    class BasePlusFlatTests {

        @Test
        @DisplayName("Formula: base(100) + flat_phys(10) = 110")
        void formula_BasePlusFlat() {
            ComputedStats attacker = ComputedStats.builder()
                .physicalDamage(10f)
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                100f, attacker, null, null, null, AttackType.MELEE, false
            );

            assertEquals(110f, result.totalDamage(), 0.1f,
                "FORMULA: 100 base + 10 flat physical = 110");
        }

        @Test
        @DisplayName("Formula: base(100) + flat_phys(10) + melee_dmg(5) = 115 (MELEE)")
        void formula_BasePlusFlatPlusMelee() {
            ComputedStats attacker = ComputedStats.builder()
                .physicalDamage(10f)
                .meleeDamage(5f)
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            DamageBreakdown melee = calculator.calculateWithForcedCrit(
                100f, attacker, null, null, null, AttackType.MELEE, false
            );
            DamageBreakdown projectile = calculator.calculateWithForcedCrit(
                100f, attacker, null, null, null, AttackType.PROJECTILE, false
            );

            assertEquals(115f, melee.totalDamage(), 0.1f,
                "FORMULA (MELEE): 100 + 10 physDmg + 5 meleeDmg = 115");
            assertEquals(110f, projectile.totalDamage(), 0.1f,
                "FORMULA (PROJECTILE): 100 + 10 physDmg (no meleeDmg) = 110");
        }
    }

    @Nested
    @DisplayName("Step 3: % Increased (Additive)")
    class PercentIncreasedTests {

        @Test
        @DisplayName("Formula: 100 × (1 + 50%/100) = 150")
        void formula_PercentIncreased() {
            ComputedStats attacker = ComputedStats.builder()
                .physicalDamagePercent(50f)
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                100f, attacker, null, null, null, AttackType.MELEE, false
            );

            assertEquals(150f, result.totalDamage(), 0.1f,
                "FORMULA: 100 × (1 + 50/100) = 150");
        }

        @Test
        @DisplayName("Formula: 100 × (1 + 30% physDmg + 20% meleeDmg) = 150")
        void formula_MultiplePercentIncreased_Additive() {
            ComputedStats attacker = ComputedStats.builder()
                .physicalDamagePercent(30f)
                .meleeDamagePercent(20f)
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                100f, attacker, null, null, null, AttackType.MELEE, false
            );

            // 100 × (1 + 30/100 + 20/100) = 100 × 1.5 = 150
            assertEquals(150f, result.totalDamage(), 0.1f,
                "FORMULA: % increased bonuses are ADDITIVE within this step");
        }
    }

    @Nested
    @DisplayName("Step 4: % More (Multiplicative)")
    class PercentMoreTests {

        @Test
        @DisplayName("Formula: 100 × (1 + 20% more) = 120")
        void formula_PercentMore() {
            ComputedStats attacker = ComputedStats.builder()
                .allDamagePercent(20f)
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                100f, attacker, null, null, null, AttackType.MELEE, false
            );

            assertEquals(120f, result.totalDamage(), 0.1f,
                "FORMULA: 100 × (1 + 20/100) = 120");
        }

        @Test
        @DisplayName("Formula: 100 × (1 + 20% more1) × (1 + 10% more2) = 132")
        void formula_MultiplePercentMore_Multiplicative() {
            ComputedStats attacker = ComputedStats.builder()
                .allDamagePercent(20f)   // First MORE
                .damageMultiplier(10f)   // Second MORE
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                100f, attacker, null, null, null, AttackType.MELEE, false
            );

            // 100 × 1.2 × 1.1 = 132
            assertEquals(132f, result.totalDamage(), 0.1f,
                "FORMULA: % more bonuses are MULTIPLICATIVE");
        }
    }

    @Nested
    @DisplayName("Step 6: Critical Strike")
    class CriticalStrikeTests {

        @Test
        @DisplayName("Formula: 100 × 1.5 crit = 150")
        void formula_CriticalStrike() {
            ComputedStats attacker = ComputedStats.builder()
                .criticalChance(100f)
                .criticalMultiplier(150f) // 1.5×
                .build();

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                100f, attacker, null, null, null, AttackType.MELEE, true
            );

            assertEquals(150f, result.totalDamage(), 0.1f,
                "FORMULA: 100 × (150/100) = 150");
            assertTrue(result.wasCritical());
            assertEquals(1.5f, result.critMultiplier(), 0.01f);
        }

        @Test
        @DisplayName("Formula: crit applies AFTER flat and % modifiers")
        void formula_CritAfterModifiers() {
            // base(100) + flat(10) = 110
            // × (1 + 50% increased) = 165
            // × 1.5 crit = 247.5
            ComputedStats attacker = ComputedStats.builder()
                .physicalDamage(10f)
                .physicalDamagePercent(50f)
                .criticalChance(100f)
                .criticalMultiplier(150f)
                .build();

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                100f, attacker, null, null, null, AttackType.MELEE, true
            );

            assertEquals(247.5f, result.totalDamage(), 0.5f,
                "FORMULA: (100 + 10) × 1.5 × 1.5 = 247.5");
        }
    }

    @Nested
    @DisplayName("Step 7: Damage Conversion")
    class DamageConversionTests {

        @Test
        @DisplayName("Formula: 100 phys with 50% fire conv = 50 phys + 50 fire")
        void formula_Conversion50Percent() {
            ComputedStats attacker = ComputedStats.builder()
                .fireConversion(50f)
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                100f, attacker, null, null, null, AttackType.MELEE, false
            );

            assertEquals(50f, result.physicalDamage(), 0.1f,
                "FORMULA: 100 × (1 - 50/100) = 50 physical");
            assertEquals(50f, result.getElementalDamage(ElementType.FIRE), 0.1f,
                "FORMULA: 100 × 50/100 = 50 fire");
            assertEquals(100f, result.totalDamage(), 0.1f,
                "Total damage preserved");
        }

        @Test
        @DisplayName("Formula: 150% total conversion scales to 100%")
        void formula_ConversionCap() {
            ComputedStats attacker = ComputedStats.builder()
                .fireConversion(75f)
                .waterConversion(75f) // Total 150%
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                100f, attacker, null, null, null, AttackType.MELEE, false
            );

            // Scaled to 50% each
            assertEquals(50f, result.getElementalDamage(ElementType.FIRE), 0.5f,
                "FORMULA: fire = 100 × (75/150) = 50");
            assertEquals(50f, result.getElementalDamage(ElementType.WATER), 0.5f,
                "FORMULA: cold = 100 × (75/150) = 50");
            assertTrue(result.physicalDamage() < 1f,
                "Physical should be ~0 after full conversion");
        }
    }

    @Nested
    @DisplayName("Step 10: Defense Calculations")
    class DefenseTests {

        @Test
        @DisplayName("Formula: Armor reduction = armor / (armor + rawDmg × 10)")
        void formula_ArmorReduction() {
            ComputedStats attacker = ComputedStats.builder()
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();
            ComputedStats defender = ComputedStats.builder()
                .armor(100f)
                .build();

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                10f, attacker, null, defender, null, AttackType.MELEE, false
            );

            // Armor formula: 100 / (100 + 10*10) = 100/200 = 0.5 = 50% reduction
            // 10 × (1 - 0.5) = 5
            assertEquals(5f, result.totalDamage(), 0.1f,
                "FORMULA: armor reduction = 100 / (100 + 10×10) = 50%");
        }

        @Test
        @DisplayName("Formula: Elemental resistance = 1 - resist/100 (capped at 75%)")
        void formula_ElementalResistance() {
            ComputedStats attacker = ComputedStats.builder()
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();
            ElementalStats attackerElem = new ElementalStats();
            attackerElem.setFlatDamage(ElementType.FIRE, 100.0);

            ComputedStats defender = ComputedStats.builder().build();
            ElementalStats defenderElem = new ElementalStats();
            defenderElem.setResistance(ElementType.FIRE, 75.0); // Max resist

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                0f, attacker, attackerElem, defender, defenderElem, AttackType.MELEE, false
            );

            // 100 fire × (1 - 75/100) = 100 × 0.25 = 25
            assertEquals(25f, result.getElementalDamage(ElementType.FIRE), 0.5f,
                "FORMULA: 100 fire × (1 - 75%) = 25");
        }

        @Test
        @DisplayName("Formula: True damage bypasses all defenses")
        void formula_TrueDamage() {
            ComputedStats attacker = ComputedStats.builder()
                .trueDamage(50f)
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();
            ComputedStats defender = ComputedStats.builder()
                .armor(10000f)
                .physicalResistance(75f)
                .build();

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                0f, attacker, null, defender, null, AttackType.MELEE, false
            );

            assertEquals(50f, result.trueDamage(), 0.1f,
                "FORMULA: true damage ignores armor and resistance");
        }
    }

    // =========================================================================
    // COMPLETE FORMULA INTEGRATION TEST
    // =========================================================================

    @Nested
    @DisplayName("Complete Formula Integration")
    class CompleteFormulaTests {

        @Test
        @DisplayName("Full formula example with all steps")
        void formula_CompleteExample() {
            // Build attacker with multiple modifiers
            ComputedStats attacker = ComputedStats.builder()
                .physicalDamage(10f)          // Step 2: +10 flat physical
                .physicalDamagePercent(50f)   // Step 5: +50% increased physical
                .allDamagePercent(20f)        // Step 7: ×1.2 more (all damage)
                .fireConversion(50f)          // Step 4: 50% → fire
                .criticalChance(100f)         // Step 9: always crit
                .criticalMultiplier(150f)     // Step 9: ×1.5
                .trueDamage(10f)              // Step 11: +10 true
                .build();

            // NEW calculation order (crit applies to ALL damage):
            // 1. Base: 100
            // 2. Flat phys: 100 + 10 = 110
            // 3. (no flat elemental)
            // 4. Conversion: 110 × 50% = 55 phys, 55 fire
            // 5. % Inc phys: 55 × 1.5 = 82.5 phys (fire unchanged = 55)
            // 6. (no elemental modifiers)
            // 7. % More: 82.5 × 1.2 = 99 phys, 55 × 1.2 = 66 fire
            // 8. (no conditional multiplier)
            // 9. Crit: 99 × 1.5 = 148.5 phys, 66 × 1.5 = 99 fire
            // 10. (no defenses)
            // 11. True: +10
            // Total: 148.5 + 99 + 10 = 257.5

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                100f, attacker, null, null, null, AttackType.MELEE, true
            );

            assertTrue(result.wasCritical());
            // Allow some tolerance for floating point
            float expectedPhys = 148.5f;
            float expectedFire = 99f;
            float expectedTrue = 10f;

            assertEquals(expectedPhys, result.physicalDamage(), 0.5f,
                "Phys: 110 base → 55 after conversion → 82.5 after %inc → 99 after %more → 148.5 after crit");
            assertEquals(expectedFire, result.getElementalDamage(ElementType.FIRE), 0.5f,
                "Fire: 55 from conversion → 55 (no elem mod) → 66 after %more → 99 after crit");
            assertEquals(expectedTrue, result.trueDamage(), 0.1f);
            assertEquals(257.5f, result.totalDamage(), 1f,
                "FORMULA: Complete calculation should be ~257.5");
        }
    }
}
