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
 * Tests attack-type modifier interactions in Step 4 of the pipeline.
 *
 * <p>Key behavior: For non-spell attacks, Step 4 applies
 * {@code (atkTypePct + dmgPct)} to ALL non-zero elemental buckets.
 * {@code physicalDamagePercent} only scales the physical slot.
 *
 * <p>This means a fire sword with {@code meleeDamagePercent} gets both
 * its physical AND fire damage scaled by melee%. This is intentional — melee%
 * represents "total melee prowess" not "physical-only melee".
 */
class DamageTypeModifierPipelineTest {

    private RPGDamageCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new RPGDamageCalculator();
    }

    // ==================== Melee Damage Percent ====================

    @Nested
    @DisplayName("Melee Damage Percent")
    class MeleeDamagePercent {

        @Test
        @DisplayName("meleeDmgPct applied to physical for MELEE attacks")
        void meleeDamagePercent_appliedToPhysical() {
            ComputedStats stats = ComputedStats.builder()
                .meleeDamagePercent(50f)
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                100f, stats, null, null, null, AttackType.MELEE, false, 1);

            // physPct(0) + meleePct(50) + dmgPct(0) = 50% increase
            assertEquals(150f, result.physicalDamage(), 0.1f,
                "100 × (1 + 50/100) = 150 physical");
        }

        @Test
        @DisplayName("meleeDmgPct ignored for PROJECTILE attacks")
        void meleeDamagePercent_ignoredForProjectile() {
            ComputedStats stats = ComputedStats.builder()
                .meleeDamagePercent(50f)
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                100f, stats, null, null, null, AttackType.PROJECTILE, false, 1);

            assertEquals(100f, result.physicalDamage(), 0.1f,
                "PROJECTILE should not get meleeDamagePercent");
        }
    }

    // ==================== Projectile Damage Percent ====================

    @Nested
    @DisplayName("Projectile Damage Percent")
    class ProjectileDamagePercent {

        @Test
        @DisplayName("projDmgPct applied for PROJECTILE attacks")
        void projectileDamagePercent_appliedForProjectile() {
            ComputedStats stats = ComputedStats.builder()
                .projectileDamagePercent(30f)
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                100f, stats, null, null, null, AttackType.PROJECTILE, false, 1);

            assertEquals(130f, result.physicalDamage(), 0.1f,
                "100 × (1 + 30/100) = 130 physical");
        }

        @Test
        @DisplayName("projDmgPct ignored for MELEE attacks")
        void projectileDamagePercent_ignoredForMelee() {
            ComputedStats stats = ComputedStats.builder()
                .projectileDamagePercent(30f)
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                100f, stats, null, null, null, AttackType.MELEE, false, 1);

            assertEquals(100f, result.physicalDamage(), 0.1f,
                "MELEE should not get projectileDamagePercent");
        }
    }

    // ==================== Damage Percent (Global) ====================

    @Nested
    @DisplayName("Damage Percent (Global)")
    class DamagePercentGlobal {

        @Test
        @DisplayName("dmgPct additive with attack type percent")
        void damagePercent_additiveWithType() {
            ComputedStats stats = ComputedStats.builder()
                .meleeDamagePercent(30f)
                .damagePercent(20f)
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                100f, stats, null, null, null, AttackType.MELEE, false, 1);

            // physPct(0) + meleePct(30) + dmgPct(20) = 50% total increase
            assertEquals(150f, result.physicalDamage(), 0.1f,
                "100 × (1 + 50/100) = 150");
        }
    }

    // ==================== Physical Damage Percent vs Attack Type ====================

    @Nested
    @DisplayName("physicalDamagePercent Only Scales Physical")
    class PhysicalDamagePercentScope {

        @Test
        @DisplayName("physDmgPct scales physical but NOT elemental buckets")
        void physicalDamagePercent_onlyScalesPhysical() {
            ComputedStats stats = ComputedStats.builder()
                .physicalDamagePercent(50f)
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            ElementalStats elemStats = new ElementalStats();
            elemStats.setFlatDamage(ElementType.FIRE, 50.0); // 50 flat fire

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                100f, stats, elemStats, null, null, AttackType.MELEE, false, 1);

            // Physical: 100 × (1 + 50/100) = 150 (physPct applies)
            assertEquals(150f, result.physicalDamage(), 0.1f,
                "Physical should be scaled by physDmgPct");
            // Fire: 50 (no physPct scaling — only atkTypePct + dmgPct scale elemental)
            assertEquals(50f, result.getElementalDamage(ElementType.FIRE), 0.1f,
                "Fire should NOT be scaled by physicalDamagePercent");
        }

        @Test
        @DisplayName("attackTypePct scales ALL non-zero elemental buckets")
        void attackTypePercent_scalesElementalBuckets() {
            ComputedStats stats = ComputedStats.builder()
                .meleeDamagePercent(50f) // This should scale fire too!
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            ElementalStats elemStats = new ElementalStats();
            elemStats.setFlatDamage(ElementType.FIRE, 50.0);

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                100f, stats, elemStats, null, null, AttackType.MELEE, false, 1);

            // Physical: 100 × (1 + 50/100) = 150 (meleePct in physPct+atkType+dmg)
            assertEquals(150f, result.physicalDamage(), 0.1f);
            // Fire: 50 × (1 + 50/100) = 75 (atkTypePct=50 + dmgPct=0 = 50%)
            assertEquals(75f, result.getElementalDamage(ElementType.FIRE), 0.1f,
                "Melee% should scale elemental buckets too");
        }
    }

    // ==================== All Three Percent Types Stacking ====================

    @Nested
    @DisplayName("Triple Percent Stacking")
    class TriplePercentStacking {

        @Test
        @DisplayName("physPct + meleePct + dmgPct all contribute to physical")
        void allThreePercentStack() {
            ComputedStats stats = ComputedStats.builder()
                .physicalDamagePercent(10f)
                .meleeDamagePercent(20f)
                .damagePercent(30f)
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                100f, stats, null, null, null, AttackType.MELEE, false, 1);

            // Physical: 100 × (1 + (10 + 20 + 30) / 100) = 100 × 1.6 = 160
            assertEquals(160f, result.physicalDamage(), 0.1f);
        }
    }

    // ==================== AREA and UNKNOWN Attack Types ====================

    @Nested
    @DisplayName("AREA and UNKNOWN Attack Types")
    class NonStandardAttackTypes {

        @Test
        @DisplayName("AREA gets only physPct + dmgPct, no attack type bonus")
        void noAttackTypeBonus_forAREA() {
            ComputedStats stats = ComputedStats.builder()
                .physicalDamagePercent(20f)
                .meleeDamagePercent(50f) // Should be ignored for AREA
                .damagePercent(10f)
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                100f, stats, null, null, null, AttackType.AREA, false, 1);

            // AREA: atkTypePct = 0, so total = physPct(20) + 0 + dmgPct(10) = 30%
            assertEquals(130f, result.physicalDamage(), 0.1f,
                "AREA should only get physPct + dmgPct, not meleePct");
        }

        @Test
        @DisplayName("UNKNOWN gets only physPct + dmgPct, no attack type bonus")
        void noAttackTypeBonus_forUNKNOWN() {
            ComputedStats stats = ComputedStats.builder()
                .physicalDamagePercent(20f)
                .projectileDamagePercent(50f) // Should be ignored
                .damagePercent(10f)
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                100f, stats, null, null, null, AttackType.UNKNOWN, false, 1);

            assertEquals(130f, result.physicalDamage(), 0.1f,
                "UNKNOWN should only get physPct + dmgPct, not projectilePct");
        }
    }

    // ==================== Spell Damage Percent for SPELL ====================

    @Nested
    @DisplayName("Spell Damage Percent for SPELL Attack")
    class SpellDamagePercentForSpell {

        @Test
        @DisplayName("spellDmgPct applies for SPELL attack type")
        void spellDamagePercent_appliedForSpellAttack() {
            ComputedStats stats = ComputedStats.builder()
                .spellDamagePercent(40f)
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            DamageBreakdown result = calculator.calculate(
                100f, stats, new ElementalStats(), null, null,
                AttackType.SPELL, false, 1.0f, false, ElementType.FIRE, 1, false);

            assertEquals(140f, result.getElementalDamage(ElementType.FIRE), 0.1f,
                "100 × (1 + 40/100) = 140 fire");
        }
    }
}
