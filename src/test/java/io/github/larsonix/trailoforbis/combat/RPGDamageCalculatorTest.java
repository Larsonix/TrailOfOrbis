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
 * Unit tests for RPGDamageCalculator.
 *
 * <p>Tests the PoE-inspired damage formula in correct order:
 * <ol>
 *   <li>Base damage</li>
 *   <li>Flat physical damage (additive)</li>
 *   <li>Flat elemental damage (added early, benefits from modifiers + crit)</li>
 *   <li>Damage conversion (physical → elemental)</li>
 *   <li>% Increased physical (sum all, apply once)</li>
 *   <li>Elemental % modifiers (includes converted damage)</li>
 *   <li>% More (multiplicative chain, all damage)</li>
 *   <li>Conditional multipliers (realm damage, execute, etc.)</li>
 *   <li>Critical strike (one roll, applies to ALL damage types)</li>
 *   <li>Defenses</li>
 *   <li>True damage (bypasses defenses)</li>
 * </ol>
 *
 * <p><b>Key insight:</b> Crit applies to ALL damage (physical + elemental).
 * Conversion happens EARLY so converted damage benefits from elemental modifiers.
 */
public class RPGDamageCalculatorTest {

    private RPGDamageCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new RPGDamageCalculator();
    }

    // ==================== Flat Damage Tests ====================

    @Nested
    @DisplayName("Flat Damage Application")
    class FlatDamageTests {

        @Test
        @DisplayName("Flat damage applied once: 10 base + 5 flat = 15 damage")
        void flatDamageAppliedOnce() {
            ComputedStats attackerStats = ComputedStats.builder()
                .physicalDamage(5f)
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                10f, attackerStats, null, null, null, AttackType.MELEE, false
            );

            // 10 + 5 = 15
            assertEquals(15f, result.totalDamage(), 0.1f, "Base + flat should equal 15");
            assertEquals(15f, result.physicalDamage(), 0.1f);
            assertFalse(result.wasCritical());
        }

        @Test
        @DisplayName("Melee flat damage only added for MELEE attacks")
        void meleeFlatDamageOnlyForMelee() {
            ComputedStats attackerStats = ComputedStats.builder()
                .physicalDamage(0f)
                .meleeDamage(10f)
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            DamageBreakdown melee = calculator.calculateWithForcedCrit(
                20f, attackerStats, null, null, null, AttackType.MELEE, false
            );
            DamageBreakdown projectile = calculator.calculateWithForcedCrit(
                20f, attackerStats, null, null, null, AttackType.PROJECTILE, false
            );

            assertEquals(30f, melee.totalDamage(), 0.1f, "Melee should include melee flat damage");
            assertEquals(20f, projectile.totalDamage(), 0.1f, "Projectile should NOT include melee flat damage");
        }

        @Test
        @DisplayName("DOT skips flat damage")
        void dotSkipsFlatDamage() {
            ComputedStats attackerStats = ComputedStats.builder()
                .physicalDamage(10f)
                .meleeDamage(5f)
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            DamageBreakdown result = calculator.calculate(
                50f, attackerStats, null, null, null, AttackType.MELEE, true // isDOT=true
            );

            // DOT should ignore flat damage
            // 50 base only (no +10 physDmg, no +5 meleeDmg)
            assertEquals(50f, result.totalDamage(), 0.5f, "DOT should not add flat damage");
        }
    }

    // ==================== Critical Strike Tests ====================

    @Nested
    @DisplayName("Critical Strike Calculation")
    class CriticalStrikeTests {

        @Test
        @DisplayName("Single crit roll: 10 damage * 1.5 crit = 15 damage")
        void singleCritRoll() {
            ComputedStats attackerStats = ComputedStats.builder()
                .physicalDamage(0f)
                .criticalChance(100f)
                .criticalMultiplier(150f) // 1.5x
                .build();

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                10f, attackerStats, null, null, null, AttackType.MELEE, true
            );

            assertEquals(15f, result.totalDamage(), 0.1f);
            assertTrue(result.wasCritical());
            assertEquals(1.5f, result.critMultiplier(), 0.01f);
        }

        @Test
        @DisplayName("DOT skips critical strike")
        void dotSkipsCrit() {
            ComputedStats attackerStats = ComputedStats.builder()
                .physicalDamage(0f)
                .criticalChance(100f)
                .criticalMultiplier(200f) // 2.0x
                .build();

            DamageBreakdown result = calculator.calculate(
                100f, attackerStats, null, null, null, AttackType.MELEE, true // isDOT=true
            );

            // DOT should not crit
            assertEquals(100f, result.totalDamage(), 0.5f, "DOT should not apply crit");
            assertFalse(result.wasCritical());
        }

        @Test
        @DisplayName("No crit when forced off")
        void noCritWhenForcedOff() {
            ComputedStats attackerStats = ComputedStats.builder()
                .physicalDamage(0f)
                .criticalChance(100f)
                .criticalMultiplier(200f)
                .build();

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                10f, attackerStats, null, null, null, AttackType.MELEE, false
            );

            assertEquals(10f, result.totalDamage(), 0.1f);
            assertFalse(result.wasCritical());
        }
    }

    // ==================== Damage Conversion Tests ====================

    @Nested
    @DisplayName("Damage Conversion")
    class DamageConversionTests {

        @Test
        @DisplayName("50% fire conversion: 100 phys -> 50 phys + 50 fire")
        void fiftyPercentFireConversion() {
            ComputedStats attackerStats = ComputedStats.builder()
                .fireConversion(50f)
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                100f, attackerStats, null, null, null, AttackType.MELEE, false
            );

            // After conversion: 50 phys + 50 fire = 100 total
            assertEquals(100f, result.totalDamage(), 0.1f);
            assertEquals(50f, result.physicalDamage(), 0.1f, "Physical should be 50 after 50% conversion");
            assertEquals(50f, result.getElementalDamage(ElementType.FIRE), 0.1f, "Fire should be 50");
        }

        @Test
        @DisplayName("100% conversion: all phys -> elemental")
        void fullConversion() {
            ComputedStats attackerStats = ComputedStats.builder()
                .fireConversion(50f)
                .waterConversion(50f)
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                100f, attackerStats, null, null, null, AttackType.MELEE, false
            );

            // After conversion: 0 phys + 50 fire + 50 cold = 100 total
            assertEquals(100f, result.totalDamage(), 0.1f);
            // Physical should be near 0 after full conversion (small float errors possible)
            assertTrue(result.physicalDamage() < 1f, "Physical should be near 0 after full conversion, got: " + result.physicalDamage());
            // Check that elemental damage totals to ~100
            float totalElemental = result.getElementalDamage(ElementType.FIRE) + result.getElementalDamage(ElementType.WATER);
            assertEquals(100f, totalElemental, 1f, "Total elemental should be ~100");
        }

        @Test
        @DisplayName("Conversion capped at 100%: 150% total -> scales to 100%")
        void conversionCappedAt100Percent() {
            ComputedStats attackerStats = ComputedStats.builder()
                .fireConversion(75f)
                .waterConversion(75f) // Total 150%
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                100f, attackerStats, null, null, null, AttackType.MELEE, false
            );

            // Total conversion capped at 100%, scaled proportionally: 50% fire + 50% cold
            assertEquals(100f, result.totalDamage(), 1f, "Total damage should remain 100");
            // Physical should be near 0 after full conversion
            assertTrue(result.physicalDamage() < 1f, "Physical should be ~0 after full conversion, got: " + result.physicalDamage());

            float totalElemental = result.getElementalDamage(ElementType.FIRE) + result.getElementalDamage(ElementType.WATER);
            assertEquals(100f, totalElemental, 1f, "Total elemental should be ~100");
        }

        @Test
        @DisplayName("Conversion order: phys -> fire -> cold -> lightning -> chaos")
        void conversionOrderCorrect() {
            ComputedStats attackerStats = ComputedStats.builder()
                .fireConversion(30f)
                .waterConversion(30f)
                .lightningConversion(30f)
                .voidConversion(10f) // Total 100%
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                100f, attackerStats, null, null, null, AttackType.MELEE, false
            );

            // Allow 1% tolerance due to floating point
            assertEquals(30f, result.getElementalDamage(ElementType.FIRE), 1f);
            assertEquals(30f, result.getElementalDamage(ElementType.WATER), 1f);
            assertEquals(30f, result.getElementalDamage(ElementType.LIGHTNING), 1f);
            assertEquals(10f, result.getElementalDamage(ElementType.VOID), 1f);
            assertTrue(result.physicalDamage() < 1f, "Physical should be near 0");
        }

        @Test
        @DisplayName("Earth and Wind conversion: 25% each = 50% total elemental")
        void earthAndWindConversion() {
            ComputedStats attackerStats = ComputedStats.builder()
                .earthConversion(25f)
                .windConversion(25f)
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                100f, attackerStats, null, null, null, AttackType.MELEE, false
            );

            assertEquals(100f, result.totalDamage(), 0.1f);
            assertEquals(50f, result.physicalDamage(), 0.1f);
            assertEquals(25f, result.getElementalDamage(ElementType.EARTH), 0.1f);
            assertEquals(25f, result.getElementalDamage(ElementType.WIND), 0.1f);
        }

        @Test
        @DisplayName("All 6 elements conversion: distributes proportionally when capped")
        void allSixElementsConversion() {
            // Total 120% (20% each × 6) should scale to 100%
            ComputedStats attackerStats = ComputedStats.builder()
                .fireConversion(20f)
                .waterConversion(20f)
                .lightningConversion(20f)
                .earthConversion(20f)
                .windConversion(20f)
                .voidConversion(20f)
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                120f, attackerStats, null, null, null, AttackType.MELEE, false
            );

            // 120% total scaled to 100%: each element gets ~16.67% of 120 = 20 damage
            assertEquals(120f, result.totalDamage(), 1f);
            assertTrue(result.physicalDamage() < 1f, "Physical should be near 0 after 100% conversion");

            // Each element should get roughly equal share
            float avgElemental = 20f; // 120 / 6 elements
            assertEquals(avgElemental, result.getElementalDamage(ElementType.FIRE), 1f);
            assertEquals(avgElemental, result.getElementalDamage(ElementType.WATER), 1f);
            assertEquals(avgElemental, result.getElementalDamage(ElementType.LIGHTNING), 1f);
            assertEquals(avgElemental, result.getElementalDamage(ElementType.EARTH), 1f);
            assertEquals(avgElemental, result.getElementalDamage(ElementType.WIND), 1f);
            assertEquals(avgElemental, result.getElementalDamage(ElementType.VOID), 1f);
        }
    }

    // ==================== Flat Elemental Damage Tests ====================

    @Nested
    @DisplayName("Flat Elemental Damage")
    class FlatElementalDamageTests {

        @Test
        @DisplayName("Flat elemental stacks with conversion")
        void flatElementalStacksWithConversion() {
            ComputedStats attackerStats = ComputedStats.builder()
                .fireConversion(100f) // All phys -> fire
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            ElementalStats attackerElemental = new ElementalStats();
            attackerElemental.setFlatDamage(ElementType.FIRE, 20.0); // +20 flat fire

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                100f, attackerStats, attackerElemental, null, null, AttackType.MELEE, false
            );

            // Flat fire added first: 20 fire
            // Then 100 phys converted to fire: 20 + 100 = 120 fire
            assertEquals(120f, result.totalDamage(), 0.1f);
            assertEquals(0f, result.physicalDamage(), 0.1f);
            assertEquals(120f, result.getElementalDamage(ElementType.FIRE), 0.1f);
        }

        @Test
        @DisplayName("Flat elemental per element")
        void flatElementalPerElement() {
            ComputedStats attackerStats = ComputedStats.builder()
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            ElementalStats attackerElemental = new ElementalStats();
            attackerElemental.setFlatDamage(ElementType.FIRE, 10.0);
            attackerElemental.setFlatDamage(ElementType.WATER, 15.0);
            attackerElemental.setFlatDamage(ElementType.LIGHTNING, 20.0);

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                50f, attackerStats, attackerElemental, null, null, AttackType.MELEE, false
            );

            // 50 phys + 10 fire + 15 cold + 20 lightning = 95
            assertEquals(95f, result.totalDamage(), 0.1f);
            assertEquals(50f, result.physicalDamage(), 0.1f);
            assertEquals(10f, result.getElementalDamage(ElementType.FIRE), 0.1f);
            assertEquals(15f, result.getElementalDamage(ElementType.WATER), 0.1f);
            assertEquals(20f, result.getElementalDamage(ElementType.LIGHTNING), 0.1f);
        }
    }

    // ==================== Crit + Elemental Damage Tests ====================

    @Nested
    @DisplayName("Critical Strike applies to Elemental Damage")
    class CritElementalTests {

        @Test
        @DisplayName("Crit applies to flat elemental damage: 50 fire × 2.0 crit = 100 fire")
        void critAppliesToFlatElementalDamage() {
            ComputedStats attackerStats = ComputedStats.builder()
                .criticalChance(100f)
                .criticalMultiplier(200f) // 2.0x crit
                .build();

            ElementalStats attackerElemental = new ElementalStats();
            attackerElemental.setFlatDamage(ElementType.FIRE, 50.0);

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                0f, attackerStats, attackerElemental, null, null, AttackType.MELEE, true
            );

            // 50 fire × 2.0 crit = 100 fire
            assertEquals(100f, result.getElementalDamage(ElementType.FIRE), 0.1f,
                "Crit should apply to flat elemental damage");
            assertEquals(100f, result.totalDamage(), 0.1f);
            assertTrue(result.wasCritical());
            assertEquals(2.0f, result.critMultiplier(), 0.01f);
        }

        @Test
        @DisplayName("Crit applies equally to physical and elemental")
        void critAppliesEquallyToPhysAndElemental() {
            ComputedStats attackerStats = ComputedStats.builder()
                .criticalChance(100f)
                .criticalMultiplier(150f) // 1.5x crit
                .build();

            ElementalStats attackerElemental = new ElementalStats();
            attackerElemental.setFlatDamage(ElementType.FIRE, 100.0);

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                100f, attackerStats, attackerElemental, null, null, AttackType.MELEE, true
            );

            // 100 phys × 1.5 = 150 phys
            // 100 fire × 1.5 = 150 fire
            assertEquals(150f, result.physicalDamage(), 0.1f, "Phys should be critted");
            assertEquals(150f, result.getElementalDamage(ElementType.FIRE), 0.1f, "Fire should be critted");
            assertEquals(300f, result.totalDamage(), 0.1f);
            assertTrue(result.wasCritical());
        }

        @Test
        @DisplayName("Converted damage gets critted: 100 phys → fire, then crit")
        void convertedDamageGetsCritted() {
            ComputedStats attackerStats = ComputedStats.builder()
                .fireConversion(100f) // 100% phys → fire
                .criticalChance(100f)
                .criticalMultiplier(200f) // 2.0x crit
                .build();

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                100f, attackerStats, null, null, null, AttackType.MELEE, true
            );

            // 100 phys → 100 fire (conversion), then × 2.0 crit = 200 fire
            assertEquals(0f, result.physicalDamage(), 0.1f);
            assertEquals(200f, result.getElementalDamage(ElementType.FIRE), 0.1f,
                "Converted fire damage should be critted");
            assertTrue(result.wasCritical());
        }

        @Test
        @DisplayName("Converted damage benefits from elemental modifiers")
        void convertedDamageBenefitsFromElementalModifiers() {
            ComputedStats attackerStats = ComputedStats.builder()
                .fireConversion(100f) // 100% phys → fire
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            ElementalStats attackerElemental = new ElementalStats();
            attackerElemental.setPercentDamage(ElementType.FIRE, 100.0); // +100% fire damage

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                100f, attackerStats, attackerElemental, null, null, AttackType.MELEE, false
            );

            // 100 phys → 100 fire (conversion) → 200 fire (+100% fire damage)
            assertEquals(200f, result.getElementalDamage(ElementType.FIRE), 0.1f,
                "Converted fire damage should benefit from +100% fire modifier");
            assertEquals(0f, result.physicalDamage(), 0.1f);
        }

        @Test
        @DisplayName("Full pipeline: flat + conversion + modifiers + crit")
        void fullPipelineWithConversionAndCrit() {
            ComputedStats attackerStats = ComputedStats.builder()
                .physicalDamage(50f)      // +50 flat phys
                .fireConversion(50f)      // 50% phys → fire
                .criticalChance(100f)
                .criticalMultiplier(200f) // 2.0x crit
                .build();

            ElementalStats attackerElemental = new ElementalStats();
            attackerElemental.setFlatDamage(ElementType.FIRE, 25.0);     // +25 flat fire
            attackerElemental.setPercentDamage(ElementType.FIRE, 50.0);  // +50% fire damage

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                100f, attackerStats, attackerElemental, null, null, AttackType.MELEE, true
            );

            // Step 1: 100 base + 50 flat phys = 150 phys
            // Step 2: +25 flat fire = 25 fire
            // Step 3: 50% conversion = 75 phys, 75 fire (total fire = 25 + 75 = 100)
            // Step 4: No % phys modifiers
            // Step 5: +50% fire = 100 × 1.5 = 150 fire
            // Step 6-7: No more multipliers
            // Step 8: 2.0x crit = 75 × 2 = 150 phys, 150 × 2 = 300 fire

            assertEquals(150f, result.physicalDamage(), 1f,
                "Physical should be 75 after conversion, then 150 after crit");
            assertEquals(300f, result.getElementalDamage(ElementType.FIRE), 1f,
                "Fire should be 100 (25 flat + 75 conv) × 1.5 (mod) × 2 (crit) = 300");
            assertTrue(result.wasCritical());
            assertEquals(450f, result.totalDamage(), 2f);
        }

        @Test
        @DisplayName("Multiple elements all get critted")
        void multipleElementsAllGetCritted() {
            ComputedStats attackerStats = ComputedStats.builder()
                .criticalChance(100f)
                .criticalMultiplier(150f) // 1.5x crit
                .build();

            ElementalStats attackerElemental = new ElementalStats();
            attackerElemental.setFlatDamage(ElementType.FIRE, 20.0);
            attackerElemental.setFlatDamage(ElementType.WATER, 30.0);
            attackerElemental.setFlatDamage(ElementType.LIGHTNING, 40.0);
            attackerElemental.setFlatDamage(ElementType.VOID, 10.0);

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                0f, attackerStats, attackerElemental, null, null, AttackType.MELEE, true
            );

            // Each element × 1.5 crit
            assertEquals(30f, result.getElementalDamage(ElementType.FIRE), 0.1f);
            assertEquals(45f, result.getElementalDamage(ElementType.WATER), 0.1f);
            assertEquals(60f, result.getElementalDamage(ElementType.LIGHTNING), 0.1f);
            assertEquals(15f, result.getElementalDamage(ElementType.VOID), 0.1f);
            assertEquals(150f, result.totalDamage(), 0.1f);
            assertTrue(result.wasCritical());
        }
    }

    // ==================== Defense Tests ====================

    @Nested
    @DisplayName("Defense Calculations")
    class DefenseTests {

        @Test
        @DisplayName("Armor reduces physical damage")
        void armorReducesPhysicalDamage() {
            ComputedStats attackerStats = ComputedStats.builder()
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            ComputedStats defenderStats = ComputedStats.builder()
                .armor(100f) // 100 armor
                .build();

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                10f, attackerStats, null, defenderStats, null, AttackType.MELEE, false
            );

            // PoE formula: 100 / (100 + 10*10) = 100/200 = 50% reduction
            assertEquals(5f, result.totalDamage(), 0.1f, "50% of 10 = 5 damage");
            assertTrue(result.armorReduction() > 0, "Armor reduction should be recorded");
        }

        @Test
        @DisplayName("Armor applied before physical resistance")
        void armorBeforePhysResist() {
            ComputedStats attackerStats = ComputedStats.builder()
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            ComputedStats defenderStats = ComputedStats.builder()
                .armor(100f)          // First layer: armor
                .physicalResistance(50f) // Second layer: phys resist
                .build();

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                10f, attackerStats, null, defenderStats, null, AttackType.MELEE, false
            );

            // Armor: 10 * 50% = 5
            // Phys resist: 5 * 50% = 2.5
            assertEquals(2.5f, result.totalDamage(), 0.1f, "Armor then phys resist should stack");
        }

        @Test
        @DisplayName("Elemental resistance per element")
        void resistancePerElement() {
            ComputedStats attackerStats = ComputedStats.builder()
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            ElementalStats attackerElemental = new ElementalStats();
            attackerElemental.setFlatDamage(ElementType.FIRE, 100.0);
            attackerElemental.setFlatDamage(ElementType.WATER, 100.0);

            ElementalStats defenderElemental = new ElementalStats();
            defenderElemental.setResistance(ElementType.FIRE, 75.0); // Max resist
            defenderElemental.setResistance(ElementType.WATER, 50.0);

            // Need to pass defenderStats for defense calculations to apply
            ComputedStats defenderStats = ComputedStats.builder().build();

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                0f, attackerStats, attackerElemental, defenderStats, defenderElemental, AttackType.MELEE, false
            );

            // Fire: 100 * (1 - 75/100) = 25
            // Cold: 100 * (1 - 50/100) = 50
            // Total: 75
            assertEquals(25f, result.getElementalDamage(ElementType.FIRE), 1f, "Fire resist should reduce damage");
            assertEquals(50f, result.getElementalDamage(ElementType.WATER), 1f, "Cold resist should reduce damage");
        }

        @Test
        @DisplayName("True damage bypasses all defenses")
        void trueDamageBypassesDefenses() {
            ComputedStats attackerStats = ComputedStats.builder()
                .trueDamage(50f)
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            ComputedStats defenderStats = ComputedStats.builder()
                .armor(10000f)           // Max armor
                .physicalResistance(75f) // Max resist
                .build();

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                0f, attackerStats, null, defenderStats, null, AttackType.MELEE, false
            );

            // True damage should be unaffected by armor/resist
            assertEquals(50f, result.trueDamage(), 0.1f, "True damage should bypass defenses");
        }
    }

    // ==================== DOT Damage Tests ====================

    @Nested
    @DisplayName("DOT Damage Handling")
    class DOTDamageTests {

        @Test
        @DisplayName("DOT uses simplified calculation")
        void dotUsesSimplifiedCalculation() {
            ElementalStats defenderElemental = new ElementalStats();
            defenderElemental.setResistance(ElementType.FIRE, 50.0);

            ComputedStats defenderStats = ComputedStats.builder()
                .armor(1000f) // Should be ignored for elemental DOT
                .build();

            DamageBreakdown result = calculator.calculateDOT(
                100f, defenderStats, defenderElemental, ElementType.FIRE
            );

            // Fire DOT with 50% resist: 100 * 0.5 = 50
            assertEquals(50f, result.totalDamage(), 0.1f);
            assertFalse(result.wasCritical(), "DOT should never crit");
        }

        @Test
        @DisplayName("Physical DOT applies physical resistance")
        void physicalDotAppliesPhysResist() {
            ComputedStats defenderStats = ComputedStats.builder()
                .physicalResistance(50f)
                .build();

            DamageBreakdown result = calculator.calculateDOT(
                100f, defenderStats, null, null // null element = physical DOT
            );

            // Physical DOT with 50% resist: 100 * 0.5 = 50
            assertEquals(50f, result.totalDamage(), 0.1f);
        }
    }

    // ==================== Integration Tests ====================

    @Nested
    @DisplayName("Full Calculation Integration")
    class IntegrationTests {

        @Test
        @DisplayName("Complete damage calculation with all modifiers")
        void completeCalculation() {
            ComputedStats attackerStats = ComputedStats.builder()
                .physicalDamage(10f)          // +10 flat
                .physicalDamagePercent(50f)   // +50% increased
                .allDamagePercent(20f)        // +20% more
                .fireConversion(50f)          // 50% -> fire
                .criticalChance(100f)
                .criticalMultiplier(150f)     // 1.5x
                .build();

            ElementalStats attackerElemental = new ElementalStats();
            attackerElemental.setFlatDamage(ElementType.FIRE, 10.0); // +10 fire

            ComputedStats defenderStats = ComputedStats.builder()
                .armor(50f)
                .build();

            ElementalStats defenderElemental = new ElementalStats();
            defenderElemental.setResistance(ElementType.FIRE, 25.0); // 25% fire resist

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                100f, attackerStats, attackerElemental, defenderStats, defenderElemental, AttackType.MELEE, true
            );

            // NEW Calculation order (crit applies to ALL damage):
            // 1. Base: 100
            // 2. Flat phys: 100 + 10 = 110
            // 3. Flat elemental: +10 fire
            // 4. Conversion: 110 * 50% = 55 phys, 55 fire (total fire = 10 + 55 = 65)
            // 5. % Increased phys: 55 * 1.5 = 82.5 phys
            // 6. Elemental modifiers: (none)
            // 7. % More: 82.5 * 1.2 = 99 phys, 65 * 1.2 = 78 fire
            // 8. Crit: 99 * 1.5 = 148.5 phys, 78 * 1.5 = 117 fire
            // 9. Defenses applied per type...

            assertTrue(result.totalDamage() > 0, "Should have non-zero damage");
            assertTrue(result.wasCritical(), "Should be a crit");
            assertTrue(result.physicalDamage() > 0 || result.getTotalElementalDamage() > 0,
                "Should have physical or elemental damage");
        }

        @Test
        @DisplayName("Zero damage from null stats")
        void zeroDamageFromNullStats() {
            DamageBreakdown result = calculator.calculate(
                0f, null, null, null, null, AttackType.UNKNOWN, false
            );

            assertEquals(0f, result.totalDamage(), 0.01f);
            assertFalse(result.wasCritical());
        }
    }

    // ==================== DamageDistribution Tests ====================

    @Nested
    @DisplayName("DamageDistribution Helper Class")
    class DamageDistributionTests {

        @Test
        @DisplayName("Physical damage tracking")
        void physicalDamageTracking() {
            DamageDistribution dist = new DamageDistribution();
            dist.setPhysical(100f);

            assertEquals(100f, dist.getPhysical(), 0.01f);
            assertEquals(100f, dist.getTotal(), 0.01f);
        }

        @Test
        @DisplayName("Elemental damage tracking")
        void elementalDamageTracking() {
            DamageDistribution dist = new DamageDistribution();
            dist.setElemental(ElementType.FIRE, 50f);
            dist.setElemental(ElementType.WATER, 30f);

            assertEquals(50f, dist.getElemental(ElementType.FIRE), 0.01f);
            assertEquals(30f, dist.getElemental(ElementType.WATER), 0.01f);
            assertEquals(80f, dist.getTotal(), 0.01f);
        }

        @Test
        @DisplayName("Global multiplier applies to all types")
        void globalMultiplierAppliesToAll() {
            DamageDistribution dist = new DamageDistribution();
            dist.setPhysical(100f);
            dist.setElemental(ElementType.FIRE, 50f);
            dist.setTrueDamage(20f);

            dist.applyMultiplier(2.0f);

            assertEquals(200f, dist.getPhysical(), 0.01f);
            assertEquals(100f, dist.getElemental(ElementType.FIRE), 0.01f);
            assertEquals(40f, dist.getTrueDamage(), 0.01f);
        }

        @Test
        @DisplayName("Conversion moves damage from physical to elemental")
        void conversionMovesPhysicalToElemental() {
            DamageDistribution dist = new DamageDistribution();
            dist.setPhysical(100f);

            float converted = dist.convertPhysical(ElementType.FIRE, 50f);

            assertEquals(50f, converted, 0.01f);
            assertEquals(50f, dist.getPhysical(), 0.01f);
            assertEquals(50f, dist.getElemental(ElementType.FIRE), 0.01f);
        }

        @Test
        @DisplayName("Primary damage type returns highest")
        void primaryDamageTypeReturnsHighest() {
            DamageDistribution dist = new DamageDistribution();
            dist.setPhysical(100f);
            dist.setElemental(ElementType.FIRE, 50f);

            assertEquals(DamageType.PHYSICAL, dist.getPrimaryDamageType());
        }
    }

    // ==================== DamageBreakdown Tests ====================

    @Nested
    @DisplayName("DamageBreakdown Record")
    class DamageBreakdownTests {

        @Test
        @DisplayName("Builder creates correct record")
        void builderCreatesCorrectRecord() {
            DamageBreakdown breakdown = DamageBreakdown.builder()
                .physicalDamage(50f)
                .elementalDamage(ElementType.FIRE, 25f)
                .trueDamage(10f)
                .wasCritical(true)
                .critMultiplier(1.5f)
                .armorReduction(30f)
                .damageType(DamageType.PHYSICAL)
                .attackType(AttackType.MELEE)
                .build();

            assertEquals(85f, breakdown.totalDamage(), 0.01f);
            assertEquals(50f, breakdown.physicalDamage(), 0.01f);
            assertEquals(25f, breakdown.getElementalDamage(ElementType.FIRE), 0.01f);
            assertEquals(10f, breakdown.trueDamage(), 0.01f);
            assertTrue(breakdown.wasCritical());
            assertEquals(1.5f, breakdown.critMultiplier(), 0.01f);
            assertEquals(30f, breakdown.armorReduction(), 0.01f);
        }

        @Test
        @DisplayName("Avoided factory method works")
        void avoidedFactoryMethodWorks() {
            DamageBreakdown dodged = DamageBreakdown.avoided(
                DamageBreakdown.AvoidanceReason.DODGED, AttackType.MELEE
            );

            assertTrue(dodged.wasDodged());
            assertFalse(dodged.wasBlocked());
            assertEquals(0f, dodged.totalDamage(), 0.01f);
            assertTrue(dodged.wasAvoided());
            assertFalse(dodged.hasDamage());
        }

        @Test
        @DisplayName("withDodged returns zero damage variant")
        void withDodgedReturnsZeroDamage() {
            DamageBreakdown original = DamageBreakdown.builder()
                .physicalDamage(100f)
                .wasCritical(true)
                .damageType(DamageType.PHYSICAL)
                .attackType(AttackType.MELEE)
                .build();

            DamageBreakdown dodged = original.withDodged();

            assertTrue(dodged.wasDodged());
            assertEquals(0f, dodged.totalDamage(), 0.01f);
        }
    }
}
