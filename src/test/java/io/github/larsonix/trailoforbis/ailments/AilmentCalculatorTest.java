package io.github.larsonix.trailoforbis.ailments;

import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.elemental.ElementType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AilmentCalculator}.
 */
@DisplayName("AilmentCalculator")
class AilmentCalculatorTest {

    private AilmentCalculator calculator;
    private ComputedStats attackerStats;
    private UUID attackerUuid;

    @BeforeEach
    void setUp() {
        // Use seeded random for deterministic tests
        calculator = new AilmentCalculator(new Random(12345));
        attackerStats = new ComputedStats();
        attackerUuid = UUID.randomUUID();
    }

    // ==================== Application Chance ====================

    @Test
    @DisplayName("calculateApplicationChance uses base chance from ailment type")
    void applicationChanceUsesBaseChance() {
        // Burn has 10% base chance by default
        float chance = calculator.calculateApplicationChance(AilmentType.BURN, attackerStats);

        assertEquals(10.0f, chance, 0.001f);
    }

    @Test
    @DisplayName("calculateApplicationChance adds statusEffectChance bonus")
    void applicationChanceAddsBonus() {
        // Set 15% status effect chance bonus
        attackerStats.setStatusEffectChance(15.0f);

        // 10% base + 15% bonus = 25%
        float chance = calculator.calculateApplicationChance(AilmentType.BURN, attackerStats);

        assertEquals(25.0f, chance, 0.001f);
    }

    // ==================== Duration Calculation ====================

    @Test
    @DisplayName("calculateDuration uses base duration from ailment type")
    void durationUsesBaseDuration() {
        // Burn has 4s base duration by default
        float duration = calculator.calculateDuration(AilmentType.BURN, attackerStats);

        assertEquals(4.0f, duration, 0.001f);
    }

    @Test
    @DisplayName("calculateDuration applies statusEffectDuration bonus")
    void durationAppliesBonus() {
        // Set 50% duration bonus
        attackerStats.setStatusEffectDuration(50.0f);

        // 4s base × (1 + 50/100) = 4 × 1.5 = 6s
        float duration = calculator.calculateDuration(AilmentType.BURN, attackerStats);

        assertEquals(6.0f, duration, 0.001f);
    }

    // ==================== Burn DPS ====================

    @Test
    @DisplayName("calculateBurnDps computes DPS correctly")
    void burnDpsComputation() {
        // 100 damage × 0.5 ratio = 50 total, over 4s = 12.5 DPS
        float dps = calculator.calculateBurnDps(100f, 0f, 4.0f, 0.5f);

        assertEquals(12.5f, dps, 0.001f);
    }

    @Test
    @DisplayName("calculateBurnDps adds flat burn damage bonus")
    void burnDpsAddsFlatBonus() {
        // 100 damage × 0.5 ratio = 50, + 10 flat = 60 total, over 4s = 15 DPS
        float dps = calculator.calculateBurnDps(100f, 10f, 4.0f, 0.5f);

        assertEquals(15.0f, dps, 0.001f);
    }

    @Test
    @DisplayName("calculateBurnDps returns 0 for zero duration")
    void burnDpsZeroForZeroDuration() {
        float dps = calculator.calculateBurnDps(100f, 10f, 0f, 0.5f);

        assertEquals(0f, dps);
    }

    // ==================== Freeze Slow Percent ====================

    @Test
    @DisplayName("calculateFreezeSlowPercent scales with damage/health ratio")
    void freezeSlowScalesWithRatio() {
        // 100 damage / 1000 health × 100 = 10%
        float slow = calculator.calculateFreezeSlowPercent(100f, 1000f);

        assertEquals(10.0f, slow, 0.001f);
    }

    @Test
    @DisplayName("calculateFreezeSlowPercent caps at 30%")
    void freezeSlowCapsAt30() {
        // 500 damage / 1000 health × 100 = 50%, capped to 30%
        float slow = calculator.calculateFreezeSlowPercent(500f, 1000f);

        assertEquals(30.0f, slow, 0.001f);
    }

    @Test
    @DisplayName("calculateFreezeSlowPercent has minimum of 5%")
    void freezeSlowMinimum5() {
        // 1 damage / 1000 health × 100 = 0.1%, raised to 5% minimum
        float slow = calculator.calculateFreezeSlowPercent(1f, 1000f);

        assertEquals(5.0f, slow, 0.001f);
    }

    // ==================== Shock Damage Increase ====================

    @Test
    @DisplayName("calculateShockDamageIncrease scales with damage/health ratio")
    void shockIncreaseScalesWithRatio() {
        // 200 damage / 1000 health × 100 = 20%
        float increase = calculator.calculateShockDamageIncrease(200f, 1000f);

        assertEquals(20.0f, increase, 0.001f);
    }

    @Test
    @DisplayName("calculateShockDamageIncrease caps at 50%")
    void shockIncreaseCapsAt50() {
        // 800 damage / 1000 health × 100 = 80%, capped to 50%
        float increase = calculator.calculateShockDamageIncrease(800f, 1000f);

        assertEquals(50.0f, increase, 0.001f);
    }

    @Test
    @DisplayName("calculateShockDamageIncrease has minimum of 5%")
    void shockIncreaseMinimum5() {
        // 1 damage / 1000 health × 100 = 0.1%, raised to 5% minimum
        float increase = calculator.calculateShockDamageIncrease(1f, 1000f);

        assertEquals(5.0f, increase, 0.001f);
    }

    // ==================== Poison DPS ====================

    @Test
    @DisplayName("calculatePoisonDps computes DPS correctly")
    void poisonDpsComputation() {
        // 100 damage × 0.3 ratio = 30 total, over 5s = 6 DPS
        float dps = calculator.calculatePoisonDps(100f, 0f, 5.0f, 0.3f);

        assertEquals(6.0f, dps, 0.001f);
    }

    @Test
    @DisplayName("calculatePoisonDps adds flat poison damage bonus")
    void poisonDpsAddsFlatBonus() {
        // 100 damage × 0.3 ratio = 30, + 20 flat = 50 total, over 5s = 10 DPS
        float dps = calculator.calculatePoisonDps(100f, 20f, 5.0f, 0.3f);

        assertEquals(10.0f, dps, 0.001f);
    }

    // ==================== tryApplyAilment ====================

    @Test
    @DisplayName("tryApplyAilment returns applied state on successful roll")
    void appliedOnSuccessfulRoll() {
        // With seeded random and 100% chance, should always apply
        attackerStats.setStatusEffectChance(90.0f); // 10 base + 90 = 100%

        AilmentCalculator.AilmentApplicationResult result = calculator.tryApplyAilment(
                ElementType.FIRE, 100f, attackerStats, 1000f, attackerUuid);

        assertTrue(result.applied());
        assertNotNull(result.ailmentState());
        assertEquals(AilmentType.BURN, result.ailmentState().type());
    }

    @Test
    @DisplayName("Applied ailment has correct type for element")
    void correctTypeForElement() {
        attackerStats.setStatusEffectChance(90.0f);

        // Fire -> Burn
        AilmentCalculator.AilmentApplicationResult fireResult = calculator.tryApplyAilment(
                ElementType.FIRE, 100f, attackerStats, 1000f, attackerUuid);
        assertEquals(AilmentType.BURN, fireResult.ailmentState().type());
    }

    @ParameterizedTest
    @CsvSource({
            "FIRE, BURN",
            "WATER, FREEZE",
            "LIGHTNING, SHOCK",
            "VOID, POISON"
    })
    @DisplayName("Elements map to correct ailment types")
    void elementToAilmentMapping(ElementType element, AilmentType expectedAilment) {
        attackerStats.setStatusEffectChance(90.0f);

        AilmentCalculator.AilmentApplicationResult result = calculator.tryApplyAilment(
                element, 100f, attackerStats, 1000f, attackerUuid);

        // With high chance, should apply
        if (result.applied()) {
            assertEquals(expectedAilment, result.ailmentState().type());
        }
    }

    // ==================== Magnitude Calculations ====================

    @Test
    @DisplayName("calculateMagnitude returns correct DPS for BURN")
    void magnitudeForBurn() {
        float magnitude = calculator.calculateMagnitude(
                AilmentType.BURN, 100f, attackerStats, 1000f, 4.0f);

        // 100 × 0.5 / 4 = 12.5 DPS
        assertEquals(12.5f, magnitude, 0.001f);
    }

    @Test
    @DisplayName("calculateMagnitude returns correct slow% for FREEZE")
    void magnitudeForFreeze() {
        float magnitude = calculator.calculateMagnitude(
                AilmentType.FREEZE, 200f, attackerStats, 1000f, 3.0f);

        // 200/1000 × 100 = 20%
        assertEquals(20.0f, magnitude, 0.001f);
    }

    @Test
    @DisplayName("calculateMagnitude returns correct +dmg% for SHOCK")
    void magnitudeForShock() {
        float magnitude = calculator.calculateMagnitude(
                AilmentType.SHOCK, 300f, attackerStats, 1000f, 2.0f);

        // 300/1000 × 100 = 30%
        assertEquals(30.0f, magnitude, 0.001f);
    }

    @Test
    @DisplayName("calculateMagnitude returns correct DPS for POISON")
    void magnitudeForPoison() {
        float magnitude = calculator.calculateMagnitude(
                AilmentType.POISON, 100f, attackerStats, 1000f, 5.0f);

        // 100 × 0.3 / 5 = 6 DPS
        assertEquals(6.0f, magnitude, 0.001f);
    }

    // ==================== Edge Cases ====================

    @Test
    @DisplayName("Zero elemental damage still applies with minimum magnitude")
    void zeroElementalDamageAppliesMinimum() {
        attackerStats.setStatusEffectChance(90.0f);

        AilmentCalculator.AilmentApplicationResult result = calculator.tryApplyAilment(
                ElementType.WATER, 0f, attackerStats, 1000f, attackerUuid);

        if (result.applied()) {
            // Should have minimum magnitude (5%)
            assertEquals(5.0f, result.ailmentState().magnitude(), 0.001f);
        }
    }

    @Test
    @DisplayName("Very high defender health reduces Freeze/Shock magnitude")
    void highDefenderHealthReducesMagnitude() {
        // 100 damage / 10000 health = 1%, raised to minimum 5%
        float freezeMagnitude = calculator.calculateFreezeSlowPercent(100f, 10000f);
        float shockMagnitude = calculator.calculateShockDamageIncrease(100f, 10000f);

        assertEquals(5.0f, freezeMagnitude, 0.001f);
        assertEquals(5.0f, shockMagnitude, 0.001f);
    }

    // ==================== Threshold Calculations ====================

    @Test
    @DisplayName("calculateThresholdMultiplier returns 1.0 when no threshold set")
    void thresholdMultiplierReturnsOneWhenNoThreshold() {
        // Default thresholds are 0
        float multiplier = calculator.calculateThresholdMultiplier(
                AilmentType.BURN, 50f, attackerStats);

        assertEquals(1.0f, multiplier, 0.001f);
    }

    @Test
    @DisplayName("calculateThresholdMultiplier returns 1.0 when damage above threshold")
    void thresholdMultiplierReturnsOneAboveThreshold() {
        attackerStats.setBurnThreshold(50f);

        float multiplier = calculator.calculateThresholdMultiplier(
                AilmentType.BURN, 100f, attackerStats);

        assertEquals(1.0f, multiplier, 0.001f);
    }

    @Test
    @DisplayName("calculateThresholdMultiplier reduces chance below threshold")
    void thresholdMultiplierReducesBelowThreshold() {
        attackerStats.setBurnThreshold(100f);

        // 50 damage / 100 threshold = 0.5 multiplier
        float multiplier = calculator.calculateThresholdMultiplier(
                AilmentType.BURN, 50f, attackerStats);

        assertEquals(0.5f, multiplier, 0.001f);
    }

    @Test
    @DisplayName("Poison ignores thresholds (always 1.0 multiplier)")
    void poisonIgnoresThresholds() {
        // Even if we set some arbitrary threshold, poison should ignore it
        float multiplier = calculator.calculateThresholdMultiplier(
                AilmentType.POISON, 10f, attackerStats);

        assertEquals(1.0f, multiplier, 0.001f);
    }
}
