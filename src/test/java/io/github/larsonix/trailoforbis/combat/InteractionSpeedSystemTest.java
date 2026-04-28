package io.github.larsonix.trailoforbis.combat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link InteractionSpeedSystem} cooldown scaling formula.
 */
class InteractionSpeedSystemTest {

    @Nested
    @DisplayName("calculateScaledCooldown (attack speed)")
    class AttackSpeedScaling {

        @Test
        @DisplayName("0% attack speed returns base cooldown unchanged")
        void zeroAttackSpeed() {
            float result = InteractionSpeedSystem.calculateScaledCooldown(0.35f, 0.0f);
            assertEquals(0.35f, result, 0.001f);
        }

        @Test
        @DisplayName("+50% attack speed reduces cooldown by ~33%")
        void fiftyPercentAttackSpeed() {
            // 0.35 / (1 + 0.5) = 0.35 / 1.5 = 0.2333
            float result = InteractionSpeedSystem.calculateScaledCooldown(0.35f, 50.0f);
            assertEquals(0.2333f, result, 0.001f);
        }

        @Test
        @DisplayName("+100% attack speed halves cooldown")
        void hundredPercentAttackSpeed() {
            // 0.35 / (1 + 1.0) = 0.35 / 2.0 = 0.175
            float result = InteractionSpeedSystem.calculateScaledCooldown(0.35f, 100.0f);
            assertEquals(0.175f, result, 0.001f);
        }

        @Test
        @DisplayName("+200% attack speed reduces cooldown to 1/3")
        void twoHundredPercentAttackSpeed() {
            // 0.35 / (1 + 2.0) = 0.35 / 3.0 = 0.1167
            float result = InteractionSpeedSystem.calculateScaledCooldown(0.35f, 200.0f);
            assertEquals(0.1167f, result, 0.001f);
        }

        @Test
        @DisplayName("-25% attack speed increases cooldown")
        void negativeAttackSpeed() {
            // 0.35 / (1 + (-0.25)) = 0.35 / 0.75 = 0.4667
            float result = InteractionSpeedSystem.calculateScaledCooldown(0.35f, -25.0f);
            assertEquals(0.4667f, result, 0.001f);
        }

        @Test
        @DisplayName("extreme attack speed clamps to MAX_ATTACK_SPEED_PERCENT")
        void extremeAttackSpeed() {
            // 1000% clamped to 500%: 0.35 / (1 + 5.0) = 0.35 / 6.0 = 0.0583
            float result = InteractionSpeedSystem.calculateScaledCooldown(0.35f, 1000.0f);
            float expected = 0.35f / 6.0f;
            assertEquals(expected, result, 0.001f);
        }

        @Test
        @DisplayName("result never goes below MIN_COOLDOWN")
        void minimumCooldownCap() {
            float result = InteractionSpeedSystem.calculateScaledCooldown(0.35f, 500.0f);
            assertTrue(result >= InteractionSpeedSystem.MIN_COOLDOWN,
                    "Cooldown should not go below MIN_COOLDOWN, got: " + result);
        }

        @Test
        @DisplayName("-100% attack speed does not produce zero multiplier")
        void negativeHundredPercent() {
            float result = InteractionSpeedSystem.calculateScaledCooldown(0.35f, -100.0f);
            assertTrue(result > 0, "Cooldown must be positive, got: " + result);
        }

        @Test
        @DisplayName("-200% attack speed handled gracefully")
        void extremeNegativeAttackSpeed() {
            float result = InteractionSpeedSystem.calculateScaledCooldown(0.35f, -200.0f);
            assertTrue(result > 0, "Cooldown must be positive, got: " + result);
            assertTrue(result >= InteractionSpeedSystem.MIN_COOLDOWN);
        }

        @Test
        @DisplayName("works with different base cooldowns")
        void differentBaseCooldown() {
            // 1.0s base with +100% speed = 0.5s
            float result = InteractionSpeedSystem.calculateScaledCooldown(1.0f, 100.0f);
            assertEquals(0.5f, result, 0.001f);
        }

        @Test
        @DisplayName("very small base cooldown still respects MIN_COOLDOWN")
        void smallBaseCooldown() {
            float result = InteractionSpeedSystem.calculateScaledCooldown(0.01f, 100.0f);
            assertEquals(InteractionSpeedSystem.MIN_COOLDOWN, result, 0.001f);
        }
    }

    @Nested
    @DisplayName("calculateScaledCooldown (with custom max)")
    class CustomMaxSpeedScaling {

        @Test
        @DisplayName("+100% speed halves cooldown")
        void hundredPercentSpeed() {
            float result = InteractionSpeedSystem.calculateScaledCooldown(
                    2.0f, 100.0f, InteractionSpeedSystem.MAX_ATTACK_SPEED_PERCENT);
            assertEquals(1.0f, result, 0.001f);
        }

        @Test
        @DisplayName("speed uses max cap")
        void speedMaxCap() {
            // 1000% clamped to 500%: 2.0 / (1 + 5.0) = 2.0 / 6.0 = 0.333
            float result = InteractionSpeedSystem.calculateScaledCooldown(
                    2.0f, 1000.0f, InteractionSpeedSystem.MAX_ATTACK_SPEED_PERCENT);
            assertEquals(2.0f / 6.0f, result, 0.001f);
        }

        @Test
        @DisplayName("0% speed returns base cooldown unchanged")
        void zeroSpeed() {
            float result = InteractionSpeedSystem.calculateScaledCooldown(
                    2.0f, 0.0f, InteractionSpeedSystem.MAX_ATTACK_SPEED_PERCENT);
            assertEquals(2.0f, result, 0.001f);
        }

        @Test
        @DisplayName("negative speed increases cooldown")
        void negativeSpeed() {
            // 2.0 / (1 + (-0.5)) = 2.0 / 0.5 = 4.0
            float result = InteractionSpeedSystem.calculateScaledCooldown(
                    2.0f, -50.0f, InteractionSpeedSystem.MAX_ATTACK_SPEED_PERCENT);
            assertEquals(4.0f, result, 0.001f);
        }

        @Test
        @DisplayName("speed respects MIN_COOLDOWN")
        void speedMinCooldown() {
            float result = InteractionSpeedSystem.calculateScaledCooldown(
                    0.01f, 500.0f, InteractionSpeedSystem.MAX_ATTACK_SPEED_PERCENT);
            assertEquals(InteractionSpeedSystem.MIN_COOLDOWN, result, 0.001f);
        }
    }

    @Nested
    @DisplayName("Constants")
    class Constants {

        @Test
        @DisplayName("MIN_COOLDOWN is positive")
        void minCooldownPositive() {
            assertTrue(InteractionSpeedSystem.MIN_COOLDOWN > 0);
        }

        @Test
        @DisplayName("MAX_ATTACK_SPEED_PERCENT is reasonable")
        void maxAttackSpeedReasonable() {
            assertTrue(InteractionSpeedSystem.MAX_ATTACK_SPEED_PERCENT > 0);
            assertTrue(InteractionSpeedSystem.MAX_ATTACK_SPEED_PERCENT <= 1000);
        }
    }
}
