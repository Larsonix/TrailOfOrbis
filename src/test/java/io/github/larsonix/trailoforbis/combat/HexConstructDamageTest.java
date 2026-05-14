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
 * Tests for hex construct spell damage normalization.
 *
 * <p>Glaciate's damage is velocity-based: {@code speed × 1.0 × magicPowerMultiplier}.
 * The "offset" slot controls spawn height (default 5 blocks), NOT damage directly.
 * Velocity grows as √(2·g·h), so normalizing by the slot default (height) was
 * dimensionally wrong — it divided velocity by height, producing a 2.8x multiplier
 * at default settings and up to 10x at terminal velocity.
 *
 * <p>The fix reverses the physics ({@code h = v²/2g}) to recover actual height,
 * then computes a linear ratio ({@code height / defaultHeight}). These tests
 * verify the math at known reference points.
 *
 * <p><b>Physics reference</b> (from GlaciatePhysicsConfig):
 * <ul>
 *   <li>gravity = 20 m/s²</li>
 *   <li>terminalVelocityAir = 50 m/s</li>
 *   <li>Default offset (spawn height) = 5 blocks</li>
 *   <li>velocity at height h: v = √(2·g·h) = √(40·h)</li>
 * </ul>
 */
class HexConstructDamageTest {

    // Physics constants matching GlaciatePhysicsConfig
    private static final float GRAVITY = 20.0f;
    private static final float TERMINAL_VELOCITY = 50.0f;
    private static final float DEFAULT_HEIGHT = 5.0f; // Glaciate.json offset DefaultValue

    private RPGDamageCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new RPGDamageCalculator();
    }

    // =========================================================================
    // PHYSICS REVERSAL FORMULA VERIFICATION
    // =========================================================================

    /**
     * Verifies that our physics reversal formula correctly recovers height from velocity.
     * This is the core math: h = v² / (2·g)
     */
    @Nested
    @DisplayName("Physics Reversal: velocity → height")
    class PhysicsReversal {

        @Test
        @DisplayName("Default height (5 blocks): v=14.14 → h=5.0")
        void defaultHeight_reversesCorrectly() {
            float velocity = velocityFromHeight(5.0f);
            assertEquals(14.142f, velocity, 0.01f, "velocity at 5 blocks");

            float recoveredHeight = heightFromVelocity(velocity);
            assertEquals(5.0f, recoveredHeight, 0.01f, "recovered height from velocity");
        }

        @Test
        @DisplayName("10 blocks: v=20.0 → h=10.0")
        void tenBlocks_reversesCorrectly() {
            float velocity = velocityFromHeight(10.0f);
            assertEquals(20.0f, velocity, 0.01f);

            float recoveredHeight = heightFromVelocity(velocity);
            assertEquals(10.0f, recoveredHeight, 0.01f);
        }

        @Test
        @DisplayName("20 blocks: v=28.28 → h=20.0")
        void twentyBlocks_reversesCorrectly() {
            float velocity = velocityFromHeight(20.0f);
            assertEquals(28.284f, velocity, 0.01f);

            float recoveredHeight = heightFromVelocity(velocity);
            assertEquals(20.0f, recoveredHeight, 0.01f);
        }

        @Test
        @DisplayName("1 block: v=6.32 → h=1.0")
        void oneBlock_reversesCorrectly() {
            float velocity = velocityFromHeight(1.0f);
            assertEquals(6.324f, velocity, 0.01f);

            float recoveredHeight = heightFromVelocity(velocity);
            assertEquals(1.0f, recoveredHeight, 0.01f);
        }

        @Test
        @DisplayName("Terminal velocity (50 m/s) → h=62.5 blocks")
        void terminalVelocity_height() {
            float recoveredHeight = heightFromVelocity(TERMINAL_VELOCITY);
            assertEquals(62.5f, recoveredHeight, 0.01f);
        }
    }

    // =========================================================================
    // HEIGHT MULTIPLIER CALCULATION
    // =========================================================================

    @Nested
    @DisplayName("Height Multiplier: linear height → damage scaling")
    class HeightMultiplier {

        @Test
        @DisplayName("Default height (5 blocks) → 1.0x multiplier")
        void defaultHeight_oneX() {
            float mult = computeHeightMultiplier(DEFAULT_HEIGHT);
            assertEquals(1.0f, mult, 0.001f);
        }

        @Test
        @DisplayName("10 blocks → 2.0x multiplier (linear)")
        void tenBlocks_twoX() {
            float mult = computeHeightMultiplier(10.0f);
            assertEquals(2.0f, mult, 0.001f);
        }

        @Test
        @DisplayName("20 blocks → 4.0x multiplier (linear)")
        void twentyBlocks_fourX() {
            float mult = computeHeightMultiplier(20.0f);
            assertEquals(4.0f, mult, 0.001f);
        }

        @Test
        @DisplayName("2.5 blocks → 0.5x multiplier (below default)")
        void halfHeight_halfX() {
            float mult = computeHeightMultiplier(2.5f);
            assertEquals(0.5f, mult, 0.001f);
        }

        @Test
        @DisplayName("1 block → clamped to 0.5x minimum")
        void veryLow_clampedToMin() {
            float mult = computeHeightMultiplierClamped(1.0f, 0.5f, 5.0f);
            assertEquals(0.5f, mult, 0.001f);
        }

        @Test
        @DisplayName("Terminal velocity → clamped to 5.0x maximum")
        void terminalVelocity_clampedToMax() {
            float height = heightFromVelocity(TERMINAL_VELOCITY); // 62.5 blocks
            float mult = computeHeightMultiplierClamped(height, 0.5f, 5.0f);
            assertEquals(5.0f, mult, 0.001f);
        }

        @Test
        @DisplayName("25 blocks → 5.0x, exactly at the cap")
        void atCap_exactlyFive() {
            float mult = computeHeightMultiplierClamped(25.0f, 0.5f, 5.0f);
            assertEquals(5.0f, mult, 0.001f);
        }

        @Test
        @DisplayName("24 blocks → 4.8x, just below cap")
        void justBelowCap() {
            float mult = computeHeightMultiplierClamped(24.0f, 0.5f, 5.0f);
            assertEquals(4.8f, mult, 0.001f);
        }
    }

    // =========================================================================
    // END-TO-END: VELOCITY → RPG DAMAGE (full formula)
    // =========================================================================

    @Nested
    @DisplayName("End-to-End: velocity input → RPG base damage output")
    class EndToEnd {

        @Test
        @DisplayName("Default Glaciate (5 blocks, magicPower=1.0): rpgBase = ourPower × 1.0")
        void defaultGlaciate_correctDamage() {
            float rpgPower = 50f; // weapon + flat spell
            float magicPower = 1.0f;
            float velocity = velocityFromHeight(DEFAULT_HEIGHT); // ~14.14
            float vanillaDamage = velocity * 1.0f * magicPower; // Hexcode formula

            float rpgBase = computeConstructRpgDamage(vanillaDamage, magicPower, rpgPower);
            assertEquals(rpgPower, rpgBase, 0.5f, "Default height should give 1.0x RPG power");
        }

        @Test
        @DisplayName("10-block Glaciate: rpgBase = ourPower × 2.0")
        void tenBlockGlaciate_doubleDamage() {
            float rpgPower = 50f;
            float magicPower = 1.0f;
            float velocity = velocityFromHeight(10.0f); // 20.0
            float vanillaDamage = velocity * 1.0f * magicPower;

            float rpgBase = computeConstructRpgDamage(vanillaDamage, magicPower, rpgPower);
            assertEquals(rpgPower * 2.0f, rpgBase, 0.5f, "10 blocks should give 2.0x");
        }

        @Test
        @DisplayName("20-block Glaciate: rpgBase = ourPower × 4.0")
        void twentyBlockGlaciate_quadDamage() {
            float rpgPower = 50f;
            float magicPower = 1.0f;
            float velocity = velocityFromHeight(20.0f); // ~28.28
            float vanillaDamage = velocity * 1.0f * magicPower;

            float rpgBase = computeConstructRpgDamage(vanillaDamage, magicPower, rpgPower);
            assertEquals(rpgPower * 4.0f, rpgBase, 0.5f, "20 blocks should give 4.0x");
        }

        @Test
        @DisplayName("Terminal velocity Glaciate: rpgBase capped at ourPower × 5.0")
        void terminalVelocity_capped() {
            float rpgPower = 50f;
            float magicPower = 1.0f;
            float vanillaDamage = TERMINAL_VELOCITY * 1.0f * magicPower;

            float rpgBase = computeConstructRpgDamage(vanillaDamage, magicPower, rpgPower);
            assertEquals(rpgPower * 5.0f, rpgBase, 0.5f, "Terminal velocity capped at 5.0x");
        }

        @Test
        @DisplayName("magicPower > 1 is correctly stripped from vanilla damage")
        void magicPowerStripping() {
            float rpgPower = 50f;
            float magicPower = 3.0f; // Player has 3x magic power from stats
            float velocity = velocityFromHeight(DEFAULT_HEIGHT); // ~14.14
            float vanillaDamage = velocity * 1.0f * magicPower; // Hexcode multiplies by magicPower

            float rpgBase = computeConstructRpgDamage(vanillaDamage, magicPower, rpgPower);
            // After stripping magicPower, we should still get 1.0x at default height
            assertEquals(rpgPower, rpgBase, 0.5f,
                "Magic power should be stripped, leaving 1.0x at default height");
        }

        @Test
        @DisplayName("High magic power + high height: both scale correctly")
        void highPower_highHeight() {
            float rpgPower = 80f;
            float magicPower = 5.0f;
            float velocity = velocityFromHeight(10.0f); // 20.0
            float vanillaDamage = velocity * 1.0f * magicPower; // 100.0

            float rpgBase = computeConstructRpgDamage(vanillaDamage, magicPower, rpgPower);
            // After stripping: rawVel = 100 / 5 = 20.0
            // height = 20² / 40 = 10.0, ratio = 10 / 5 = 2.0
            assertEquals(rpgPower * 2.0f, rpgBase, 0.5f,
                "Should be 2.0x regardless of magic power level");
        }

        @Test
        @DisplayName("Very low velocity (0.1) → clamped to 0.5x minimum")
        void veryLowVelocity_clampedMin() {
            float rpgPower = 50f;
            float magicPower = 1.0f;
            float vanillaDamage = 0.1f * magicPower; // Barely moving

            float rpgBase = computeConstructRpgDamage(vanillaDamage, magicPower, rpgPower);
            assertEquals(rpgPower * 0.5f, rpgBase, 0.5f, "Very low velocity clamped to 0.5x");
        }
    }

    // =========================================================================
    // RPG PIPELINE INTEGRATION (through the actual calculator)
    // =========================================================================

    @Nested
    @DisplayName("RPG Pipeline: construct base damage flows through calculator correctly")
    class PipelineIntegration {

        @Test
        @DisplayName("Hex spell with WATER element places damage in water slot")
        void waterElement_routedToWaterSlot() {
            ComputedStats stats = ComputedStats.builder()
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            // Simulate: base=50 (from construct formula), spell element=WATER
            DamageBreakdown result = calculator.calculate(
                50f, stats, null, null, null,
                AttackType.SPELL, false,
                1.0f, false, ElementType.WATER, 1, true);

            assertEquals(50f, result.getElementalDamage(ElementType.WATER), 0.5f,
                "Damage should route to WATER slot");
            assertEquals(0f, result.physicalDamage(), 0.001f,
                "No physical damage for spell attacks");
        }

        @Test
        @DisplayName("Hex spell with spell damage % scaling applied")
        void spellDamagePercent_applied() {
            ComputedStats stats = ComputedStats.builder()
                .spellDamagePercent(100f) // +100% spell damage
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            DamageBreakdown result = calculator.calculate(
                50f, stats, null, null, null,
                AttackType.SPELL, false,
                1.0f, false, ElementType.WATER, 1, true);

            // 50 base × (1 + 100/100) = 50 × 2 = 100
            assertEquals(100f, result.totalDamage(), 1.0f,
                "Spell damage % should double the damage");
        }

        @Test
        @DisplayName("Hex spell with water resistance on defender reduces damage")
        void waterResistance_reducesDamage() {
            ComputedStats attackerStats = ComputedStats.builder()
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            // defenderStats must be non-null — applyDefenses early-returns on null
            ComputedStats defenderStats = ComputedStats.builder().build();
            ElementalStats defenderElemental = new ElementalStats();
            defenderElemental.setResistance(ElementType.WATER, 50.0); // 50% water resist

            DamageBreakdown result = calculator.calculate(
                100f, attackerStats, null, defenderStats, defenderElemental,
                AttackType.SPELL, false,
                1.0f, false, ElementType.WATER, 1, true);

            // 100 base × (1 - 0.50) = 50
            assertEquals(50f, result.totalDamage(), 1.0f,
                "50% water resistance should halve damage");
        }

        @Test
        @DisplayName("hexBakedElement parameter flows through calculateTraced to pipeline")
        void hexBakedElement_flowsThrough() {
            // hexBakedElement controls which element's flat damage is NOT added by Step 2.
            // Step 1 (flat spell) currently applies regardless — it only affects trace logging.
            // This test verifies the parameter reaches the pipeline without error.
            ComputedStats stats = ComputedStats.builder()
                .spellDamage(20f) // +20 flat spell damage
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            ElementalStats attackerElemental = new ElementalStats();
            attackerElemental.setFlatDamage(ElementType.WATER, 10.0); // +10 flat water

            // With hexBakedElement=WATER: Step 2 skips WATER flat (already baked).
            // Non-baked elements still enter the pipeline normally.
            DamageTrace traceWithBaked = calculator.calculateTraced(
                50f, stats, attackerElemental, null, null,
                AttackType.SPELL, 1.0f, null, 1.0f,
                ElementType.WATER, 1, true, ElementType.WATER);

            // Without hexBakedElement: WATER flat IS added by Step 2 → +10 more
            DamageTrace traceWithoutBaked = calculator.calculateTraced(
                50f, stats, attackerElemental, null, null,
                AttackType.SPELL, 1.0f, null, 1.0f,
                ElementType.WATER, 1, true, null);

            float withBakedTotal = traceWithBaked.breakdown().totalDamage();
            float withoutBakedTotal = traceWithoutBaked.breakdown().totalDamage();

            // With baked: 50 base + 20 flat spell + 0 flat water (skipped) = 70
            // Without baked: 50 base + 20 flat spell + 10 flat water = 80
            assertTrue(withoutBakedTotal > withBakedTotal,
                String.format("Without hexBakedElement (%.1f) should be higher than with (%.1f) " +
                    "because Step 2 adds flat elemental when not baked",
                    withoutBakedTotal, withBakedTotal));
        }

        @Test
        @DisplayName("projectileSpell=true benefits from projectile damage %")
        void projectileSpell_benefitsFromProjectilePercent() {
            ComputedStats stats = ComputedStats.builder()
                .projectileDamagePercent(50f) // +50% projectile damage
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            // With projectileSpell=true
            DamageBreakdown withProjectile = calculator.calculate(
                50f, stats, null, null, null,
                AttackType.SPELL, false,
                1.0f, false, ElementType.WATER, 1, true);

            // Without projectileSpell (false)
            DamageBreakdown withoutProjectile = calculator.calculate(
                50f, stats, null, null, null,
                AttackType.SPELL, false,
                1.0f, false, ElementType.WATER, 1, false);

            assertTrue(withProjectile.totalDamage() > withoutProjectile.totalDamage(),
                "projectileSpell=true should benefit from projectile damage %");
        }

        @Test
        @DisplayName("Crit applies to spell damage (all elements)")
        void crit_appliesToSpellDamage() {
            ComputedStats stats = ComputedStats.builder()
                .criticalChance(100f)
                .criticalMultiplier(200f) // 200% crit multiplier = 2x damage
                .build();

            DamageBreakdown result = calculator.calculate(
                50f, stats, null, null, null,
                AttackType.SPELL, false,
                1.0f, false, ElementType.WATER, 1, true);

            assertTrue(result.wasCritical(), "Should be a crit with 100% chance");
            assertEquals(100f, result.totalDamage(), 1.0f,
                "200% crit multiplier on 50 base = 100");
        }
    }

    // =========================================================================
    // REGRESSION: OLD FORMULA vs NEW FORMULA
    // =========================================================================

    @Nested
    @DisplayName("Regression: old formula produced ~2.8x at default, new produces 1.0x")
    class RegressionTests {

        @Test
        @DisplayName("Old formula: velocity/slotDefault = 14.14/5 = 2.83x (WRONG)")
        void oldFormula_wasWrong() {
            float velocity = velocityFromHeight(DEFAULT_HEIGHT);
            float oldMultiplier = velocity / DEFAULT_HEIGHT; // The broken formula
            assertTrue(oldMultiplier > 2.5f, "Old formula gave 2.8x at default height");
            assertTrue(oldMultiplier < 3.0f);
        }

        @Test
        @DisplayName("New formula: height(v)/slotDefault = 5.0/5 = 1.0x (CORRECT)")
        void newFormula_isCorrect() {
            float velocity = velocityFromHeight(DEFAULT_HEIGHT);
            float height = heightFromVelocity(velocity);
            float newMultiplier = height / DEFAULT_HEIGHT; // The fixed formula
            assertEquals(1.0f, newMultiplier, 0.01f, "New formula gives 1.0x at default height");
        }

        @Test
        @DisplayName("Old formula at terminal velocity: 50/5 = 10x (the reported bug)")
        void oldFormula_tenXAtTerminal() {
            float oldMultiplier = TERMINAL_VELOCITY / DEFAULT_HEIGHT;
            assertEquals(10.0f, oldMultiplier, 0.001f, "Old formula gave 10x at terminal velocity");
        }

        @Test
        @DisplayName("New formula at terminal velocity: 62.5/5 = 12.5 → capped at 5.0x")
        void newFormula_cappedAtTerminal() {
            float height = heightFromVelocity(TERMINAL_VELOCITY);
            float newMultiplier = height / DEFAULT_HEIGHT;
            assertEquals(12.5f, newMultiplier, 0.01f, "Uncapped would be 12.5x");

            float capped = Math.max(0.5f, Math.min(5.0f, newMultiplier));
            assertEquals(5.0f, capped, 0.001f, "Capped at 5.0x");
        }

        @Test
        @DisplayName("Linearity: doubling height doubles multiplier (old was sqrt, new is linear)")
        void linearity_verification() {
            float mult5 = computeHeightMultiplier(5.0f);
            float mult10 = computeHeightMultiplier(10.0f);
            float mult20 = computeHeightMultiplier(20.0f);

            assertEquals(mult5 * 2.0f, mult10, 0.001f, "10 blocks = 2× of 5 blocks");
            assertEquals(mult5 * 4.0f, mult20, 0.001f, "20 blocks = 4× of 5 blocks");
            assertEquals(mult10 * 2.0f, mult20, 0.001f, "20 blocks = 2× of 10 blocks");
        }
    }

    // =========================================================================
    // EDGE CASES
    // =========================================================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Zero velocity → height=0 → clamped to 0.5x minimum")
        void zeroVelocity() {
            float height = heightFromVelocity(0f);
            assertEquals(0f, height, 0.001f);

            float mult = computeHeightMultiplierClamped(height, 0.5f, 5.0f);
            assertEquals(0.5f, mult, 0.001f);
        }

        @Test
        @DisplayName("Negative velocity (impossible) → treated as height=0")
        void negativeVelocity() {
            // abs(v) should be used, but formula uses v² so negative works anyway
            float height = heightFromVelocity(-10.0f);
            assertTrue(height > 0, "v² makes negative velocity produce positive height");
            assertEquals(heightFromVelocity(10.0f), height, 0.001f,
                "Negative velocity = same height as positive");
        }

        @Test
        @DisplayName("slotDefault=0 → multiplier defaults to 1.0")
        void zeroSlotDefault() {
            float slotDefault = 0f;
            float mult = (slotDefault > 0) ? (5.0f / slotDefault) : 1.0f;
            assertEquals(1.0f, mult, 0.001f, "Zero slot default should default to 1.0x");
        }

        @Test
        @DisplayName("Very high RPG power multiplied correctly")
        void highRpgPower() {
            float rpgPower = 500f;
            float rpgBase = computeConstructRpgDamage(
                velocityFromHeight(DEFAULT_HEIGHT), 1.0f, rpgPower);
            assertEquals(rpgPower, rpgBase, 1.0f, "High RPG power × 1.0 = RPG power");
        }

        @Test
        @DisplayName("RPG power of 0 → clamped to minimum 1.0")
        void zeroRpgPower() {
            float rpgBase = computeConstructRpgDamage(
                velocityFromHeight(DEFAULT_HEIGHT), 1.0f, 0f);
            // min(1, 0+0+0) = 1, then × 1.0 = 1.0
            assertEquals(1.0f, rpgBase, 0.5f, "Zero RPG power clamped to 1.0");
        }
    }

    // =========================================================================
    // HELPER METHODS (mirror the actual implementation in RPGDamageSystem)
    // =========================================================================

    /** v = √(2·g·h) */
    private static float velocityFromHeight(float height) {
        return (float) Math.sqrt(2.0 * GRAVITY * height);
    }

    /** h = v² / (2·g) */
    private static float heightFromVelocity(float velocity) {
        return (velocity * velocity) / (2f * GRAVITY);
    }

    /** heightMultiplier = actualHeight / defaultHeight */
    private static float computeHeightMultiplier(float actualHeight) {
        return actualHeight / DEFAULT_HEIGHT;
    }

    /** heightMultiplier clamped to [min, max] */
    private static float computeHeightMultiplierClamped(float actualHeight, float min, float max) {
        float mult = actualHeight / DEFAULT_HEIGHT;
        return Math.max(min, Math.min(max, mult));
    }

    /**
     * Full construct RPG damage formula (mirrors RPGDamageSystem lines 946-959).
     *
     * @param vanillaDamage Hexcode's output: speed × damageMultiplier × magicPowerMultiplier
     * @param magicPower    The magicPowerMultiplier captured pre-cast
     * @param rpgPower      Our RPG power: weapon + flatSpell + flatElemental (pre-clamped)
     * @return RPG base damage for this construct hit
     */
    private static float computeConstructRpgDamage(float vanillaDamage, float magicPower, float rpgPower) {
        float rawVelocity = vanillaDamage / magicPower;
        float actualHeight = (rawVelocity * rawVelocity) / (2f * GRAVITY);
        float heightMultiplier = (DEFAULT_HEIGHT > 0) ? (actualHeight / DEFAULT_HEIGHT) : 1f;
        heightMultiplier = Math.max(0.5f, Math.min(5.0f, heightMultiplier));
        float effectiveRpgPower = Math.max(1f, rpgPower);
        return effectiveRpgPower * heightMultiplier;
    }
}
