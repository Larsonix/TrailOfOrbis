package io.github.larsonix.trailoforbis.ailments;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AilmentState} immutable record.
 */
@DisplayName("AilmentState")
class AilmentStateTest {

    private static final UUID SOURCE_UUID = UUID.randomUUID();

    @Test
    @DisplayName("burn factory creates valid state")
    void burnFactoryCreatesValidState() {
        AilmentState burn = AilmentState.burn(10.0f, 4.0f, SOURCE_UUID);

        assertEquals(AilmentType.BURN, burn.type());
        assertEquals(SOURCE_UUID, burn.sourceUuid());
        assertEquals(4.0f, burn.remainingDuration());
        assertEquals(4.0f, burn.totalDuration());
        assertEquals(10.0f, burn.magnitude());
    }

    @Test
    @DisplayName("freeze factory creates valid state with capped magnitude")
    void freezeFactoryCreatesValidState() {
        AilmentState freeze = AilmentState.freeze(25.0f, 3.0f, SOURCE_UUID);

        assertEquals(AilmentType.FREEZE, freeze.type());
        assertEquals(25.0f, freeze.magnitude());
    }

    @Test
    @DisplayName("freeze factory caps at 30%")
    void freezeFactoryCapsAt30() {
        AilmentState freeze = AilmentState.freeze(50.0f, 3.0f, SOURCE_UUID);

        assertEquals(30.0f, freeze.magnitude());
    }

    @Test
    @DisplayName("shock factory creates valid state with capped magnitude")
    void shockFactoryCreatesValidState() {
        AilmentState shock = AilmentState.shock(35.0f, 2.0f, SOURCE_UUID);

        assertEquals(AilmentType.SHOCK, shock.type());
        assertEquals(35.0f, shock.magnitude());
    }

    @Test
    @DisplayName("shock factory caps at 50%")
    void shockFactoryCapsAt50() {
        AilmentState shock = AilmentState.shock(80.0f, 2.0f, SOURCE_UUID);

        assertEquals(50.0f, shock.magnitude());
    }

    @Test
    @DisplayName("poison factory creates valid state")
    void poisonFactoryCreatesValidState() {
        AilmentState poison = AilmentState.poison(8.0f, 5.0f, SOURCE_UUID);

        assertEquals(AilmentType.POISON, poison.type());
        assertEquals(8.0f, poison.magnitude());
    }

    @Test
    @DisplayName("State is not expired when duration > 0")
    void stateNotExpiredWhenDurationPositive() {
        AilmentState state = AilmentState.freeze(25.0f, 3.0f, SOURCE_UUID);

        assertFalse(state.isExpired());
    }

    @Test
    @DisplayName("State is expired when duration <= 0")
    void stateExpiredWhenDurationZeroOrNegative() {
        AilmentState state = AilmentState.burn(10.0f, 3.0f, SOURCE_UUID);
        AilmentState expired = state.withDuration(0f);

        assertTrue(expired.isExpired());
    }

    @Test
    @DisplayName("withDuration returns new state with updated duration")
    void withDurationReturnsNewState() {
        AilmentState original = AilmentState.burn(10.0f, 4.0f, SOURCE_UUID);

        AilmentState updated = original.withDuration(2.5f);

        // Original unchanged
        assertEquals(4.0f, original.remainingDuration());
        // New state has updated duration
        assertEquals(2.5f, updated.remainingDuration());
        // Other fields unchanged
        assertEquals(original.type(), updated.type());
        assertEquals(original.sourceUuid(), updated.sourceUuid());
        assertEquals(original.magnitude(), updated.magnitude());
    }

    @Test
    @DisplayName("afterTick reduces duration by dt")
    void afterTickReducesDuration() {
        AilmentState state = AilmentState.shock(30.0f, 2.0f, SOURCE_UUID);

        AilmentState ticked = state.afterTick(0.25f);

        assertEquals(1.75f, ticked.remainingDuration(), 0.001f);
    }

    @Test
    @DisplayName("afterTick creates new state (immutability)")
    void afterTickCreatesNewState() {
        AilmentState original = AilmentState.poison(8.0f, 5.0f, SOURCE_UUID);

        AilmentState ticked = original.afterTick(1.0f);

        // Original unchanged
        assertEquals(5.0f, original.remainingDuration());
        // New state ticked
        assertEquals(4.0f, ticked.remainingDuration());
    }

    @Test
    @DisplayName("refresh extends duration if new is longer")
    void refreshExtendsWithLongerDuration() {
        AilmentState original = AilmentState.burn(10.0f, 2.0f, SOURCE_UUID);

        AilmentState refreshed = original.refresh(4.0f, 15.0f);

        assertEquals(4.0f, refreshed.remainingDuration());
        assertEquals(15.0f, refreshed.magnitude());
    }

    @Test
    @DisplayName("refresh keeps longer existing duration")
    void refreshKeepsLongerExistingDuration() {
        AilmentState original = AilmentState.freeze(20.0f, 5.0f, SOURCE_UUID);

        AilmentState refreshed = original.refresh(3.0f, 25.0f);

        assertEquals(5.0f, refreshed.remainingDuration());
    }

    @Test
    @DisplayName("refresh updates to higher magnitude")
    void refreshUpdatesToHigherMagnitude() {
        AilmentState original = AilmentState.shock(20.0f, 2.0f, SOURCE_UUID);

        AilmentState refreshed = original.refresh(2.0f, 35.0f);

        assertEquals(35.0f, refreshed.magnitude());
    }

    @Test
    @DisplayName("refresh keeps higher existing magnitude")
    void refreshKeepsHigherExistingMagnitude() {
        AilmentState original = AilmentState.shock(40.0f, 2.0f, SOURCE_UUID);

        AilmentState refreshed = original.refresh(2.0f, 25.0f);

        assertEquals(40.0f, refreshed.magnitude());
    }

    @Test
    @DisplayName("calculateDamageThisTick returns magnitude * dt for DoT")
    void calculateDamageThisTickForDoT() {
        AilmentState burn = AilmentState.burn(15.5f, 4.0f, SOURCE_UUID);

        // 15.5 DPS × 0.25s = 3.875 damage
        assertEquals(3.875f, burn.calculateDamageThisTick(0.25f), 0.001f);
    }

    @Test
    @DisplayName("calculateDamageThisTick returns 0 for debuffs")
    void calculateDamageThisTickZeroForDebuffs() {
        AilmentState freeze = AilmentState.freeze(25.0f, 3.0f, SOURCE_UUID);

        assertEquals(0f, freeze.calculateDamageThisTick(0.25f));
    }

    @Test
    @DisplayName("getDurationFraction returns correct value")
    void getDurationFractionCorrect() {
        AilmentState state = AilmentState.burn(10.0f, 4.0f, SOURCE_UUID);
        AilmentState halfWay = state.withDuration(2.0f);

        assertEquals(1.0f, state.getDurationFraction(), 0.001f);
        assertEquals(0.5f, halfWay.getDurationFraction(), 0.001f);
    }

    @Test
    @DisplayName("withMagnitude returns new state with updated magnitude")
    void withMagnitudeReturnsNewState() {
        AilmentState original = AilmentState.burn(10.0f, 4.0f, SOURCE_UUID);

        AilmentState updated = original.withMagnitude(20.0f);

        // Original unchanged
        assertEquals(10.0f, original.magnitude());
        // New state has updated magnitude
        assertEquals(20.0f, updated.magnitude());
    }
}
