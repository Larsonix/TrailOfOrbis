package io.github.larsonix.trailoforbis.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the {@link LevelScaling} utility.
 *
 * <p>Verifies the two-phase scaling curve: power-of-log below the transition
 * level, asymptotic diminishing returns above it.
 *
 * <p>Default config: transition=100, maxRatio=2.0, decayDivisor=5.
 */
class LevelScalingTest {

    @BeforeEach
    void resetScaling() {
        LevelScaling.resetDefaults();
    }

    @AfterEach
    void cleanUp() {
        LevelScaling.resetDefaults();
    }

    // =========================================================================
    // Phase 1: Below Transition (unchanged power-of-log)
    // =========================================================================

    @Test
    @DisplayName("Level 1 should have 0% bonus")
    void level1_zeroBonusPercent() {
        assertEquals(0.0, LevelScaling.getBonusPercent(1), 0.001);
    }

    @Test
    @DisplayName("Level 0 should have 0% bonus (edge case)")
    void level0_zeroBonusPercent() {
        assertEquals(0.0, LevelScaling.getBonusPercent(0), 0.001);
    }

    @Test
    @DisplayName("Negative level should have 0% bonus (edge case)")
    void negativeLevel_zeroBonusPercent() {
        assertEquals(0.0, LevelScaling.getBonusPercent(-10), 0.001);
    }

    @ParameterizedTest
    @DisplayName("Below-transition bonus should match original formula")
    @CsvSource({
        "20, 15.0, 5.0",    // Early game: ~15% +/- 5
        "100, 50.0, 10.0"   // Transition point: ~50% +/- 10
    })
    void bonusBelowTransition(int level, double expectedBonus, double tolerance) {
        double actual = LevelScaling.getBonusPercent(level);
        assertEquals(expectedBonus, actual, tolerance,
            String.format("Level %d: expected ~%.0f%% bonus, got %.2f%%",
                level, expectedBonus, actual));
    }

    @Test
    @DisplayName("Level 1 should have 1.0x multiplier")
    void level1_baseMultiplier() {
        assertEquals(1.0, LevelScaling.getMultiplier(1), 0.001);
    }

    @ParameterizedTest
    @DisplayName("Below-transition multiplier should match original formula")
    @CsvSource({
        "20, 1.15, 0.05",    // Early game: ~1.15x +/- 0.05
        "100, 1.50, 0.10"    // Transition point: ~1.50x +/- 0.10
    })
    void multiplierBelowTransition(int level, double expectedMultiplier, double tolerance) {
        double actual = LevelScaling.getMultiplier(level);
        assertEquals(expectedMultiplier, actual, tolerance,
            String.format("Level %d: expected ~%.2fx multiplier, got %.2fx",
                level, expectedMultiplier, actual));
    }

    // =========================================================================
    // Phase 2: Diminishing Returns (above transition)
    // =========================================================================

    @Test
    @DisplayName("Curve should be continuous at transition point")
    void continuityAtTransition() {
        double atTransition = LevelScaling.getMultiplier(100);
        double justAbove = LevelScaling.getMultiplier(101);
        // Should be very close — no jump
        double gap = Math.abs(justAbove - atTransition);
        assertTrue(gap < 0.05,
            String.format("Discontinuity at transition: level 100=%.4f, level 101=%.4f, gap=%.4f",
                atTransition, justAbove, gap));
    }

    @Test
    @DisplayName("Above-transition gains should diminish")
    void diminishingReturns() {
        // Gain from 100->200 should be larger than 200->300
        double at100 = LevelScaling.getMultiplier(100);
        double at200 = LevelScaling.getMultiplier(200);
        double at300 = LevelScaling.getMultiplier(300);

        double gain100to200 = at200 - at100;
        double gain200to300 = at300 - at200;

        assertTrue(gain100to200 > gain200to300,
            String.format("100->200 gain (%.4f) should be > 200->300 gain (%.4f)",
                gain100to200, gain200to300));
    }

    @Test
    @DisplayName("Values should never exceed ceiling")
    void neverExceedsCeiling() {
        double ceiling = LevelScaling.getCeiling();
        for (int level : new int[]{200, 500, 1000, 5000, 10000, 100000, Integer.MAX_VALUE}) {
            double multiplier = LevelScaling.getMultiplier(level);
            // At extreme levels, floating point rounds exp(-huge) to 0.0, giving exactly ceiling
            assertTrue(multiplier <= ceiling,
                String.format("Level %d multiplier (%.6f) should be <= ceiling (%.6f)",
                    level, multiplier, ceiling));
        }
    }

    @Test
    @DisplayName("Level 2000 should be close to ceiling with default decayDivisor=5")
    void level2000_nearCeiling() {
        // With decayDivisor=5, the curve reaches ~63% at transitionLevel*5=500 levels past
        // transition, meaning level 2000 should be >95% of ceiling
        double multiplier = LevelScaling.getMultiplier(2000);
        double ceiling = LevelScaling.getCeiling();
        double pctOfCeiling = multiplier / ceiling;
        assertTrue(pctOfCeiling > 0.95,
            String.format("Level 2000 (%.4f) should be >95%% of ceiling (%.4f), was %.1f%%",
                multiplier, ceiling, pctOfCeiling * 100));
    }

    @Test
    @DisplayName("Level 500 should be ~2.32x with default decayDivisor=5")
    void level500_expectedMultiplier() {
        double multiplier = LevelScaling.getMultiplier(500);
        assertEquals(2.32, multiplier, 0.15,
            String.format("Level 500 expected ~2.32x, got %.4f", multiplier));
    }

    // =========================================================================
    // Consistency Tests
    // =========================================================================

    @Test
    @DisplayName("Multiplier should be consistent with bonus percent")
    void multiplierConsistentWithBonus() {
        for (int level : new int[]{1, 10, 50, 100, 150, 500, 1000}) {
            double bonus = LevelScaling.getBonusPercent(level);
            double multiplier = LevelScaling.getMultiplier(level);
            double expectedMultiplier = 1.0 + bonus / 100.0;
            assertEquals(expectedMultiplier, multiplier, 0.0001,
                String.format("Level %d: multiplier inconsistent with bonus", level));
        }
    }

    @Test
    @DisplayName("Scaling should be monotonically increasing across full range")
    void scalingMonotonicallyIncreasing() {
        double previousMultiplier = 0;
        // Test up to 10000 — beyond this, floating point precision makes adjacent levels identical
        for (int level = 1; level <= 10000; level = Math.max(level + 1, (int)(level * 1.5))) {
            double currentMultiplier = LevelScaling.getMultiplier(level);
            assertTrue(currentMultiplier >= previousMultiplier,
                String.format("Level %d multiplier (%.6f) should be >= previous (%.6f)",
                    level, currentMultiplier, previousMultiplier));
            previousMultiplier = currentMultiplier;
        }
        // Verify strictly increasing up to a moderate range
        double at100 = LevelScaling.getMultiplier(100);
        double at200 = LevelScaling.getMultiplier(200);
        double at500 = LevelScaling.getMultiplier(500);
        assertTrue(at200 > at100, "Level 200 should be strictly > level 100");
        assertTrue(at500 > at200, "Level 500 should be strictly > level 200");
    }

    // =========================================================================
    // Configuration Tests
    // =========================================================================

    @Nested
    @DisplayName("Custom configuration")
    class ConfigurationTests {

        @Test
        @DisplayName("Custom transition level should shift the curve")
        void customTransitionLevel() {
            // With default transition=100, get level 2000 value
            double defaultAt2000 = LevelScaling.getMultiplier(2000);

            // Now lower the transition to 50 — ceiling becomes 2x value_at_50
            LevelScaling.configure(50, 2.0);
            double at50 = LevelScaling.getMultiplier(50);
            double customAt2000 = LevelScaling.getMultiplier(2000);
            double customCeiling = LevelScaling.getCeiling();

            // Ceiling should be 2x the value at level 50
            assertEquals(at50 * 2.0, customCeiling, 0.001);
            // Level 2000 should be lower than with default config (ceiling is lower)
            assertTrue(customAt2000 < defaultAt2000,
                String.format("With transition=50, level 2000 (%.4f) should be < default (%.4f)",
                    customAt2000, defaultAt2000));
            // Level 2000 should be near the custom ceiling
            assertTrue(customAt2000 > customCeiling * 0.95,
                "Level 2000 should be near the custom ceiling");
        }

        @Test
        @DisplayName("Higher maxMultiplierRatio raises the ceiling")
        void higherRatioRaisesCeiling() {
            LevelScaling.configure(100, 3.0);
            double ceiling = LevelScaling.getCeiling();
            double transitionValue = LevelScaling.getMultiplier(100);
            assertEquals(transitionValue * 3.0, ceiling, 0.01);
        }

        @Test
        @DisplayName("Configuration applies to getters")
        void configAppliedToGetters() {
            LevelScaling.configure(80, 2.5);
            assertEquals(80, LevelScaling.getTransitionLevel());
            assertEquals(2.5, LevelScaling.getMaxMultiplierRatio(), 0.001);
        }
    }

    @Nested
    @DisplayName("DecayDivisor configuration")
    class DecayDivisorTests {

        @Test
        @DisplayName("Default decayDivisor should be 5.0")
        void defaultDecayDivisor() {
            assertEquals(5.0, LevelScaling.getDecayDivisor(), 0.001);
        }

        @Test
        @DisplayName("DecayDivisor=1 should reach ceiling faster (old behavior)")
        void decayDivisor1_fasterCeiling() {
            LevelScaling.configure(100, 2.0, 1.0);

            double at1000 = LevelScaling.getMultiplier(1000);
            double ceiling = LevelScaling.getCeiling();
            double pctOfCeiling = at1000 / ceiling;

            // With divisor=1, level 1000 should be very close to ceiling (>95%)
            assertTrue(pctOfCeiling > 0.95,
                String.format("With divisor=1, level 1000 (%.4f) should be >95%% of ceiling (%.4f), was %.1f%%",
                    at1000, ceiling, pctOfCeiling * 100));
        }

        @Test
        @DisplayName("DecayDivisor=5 should reach ceiling slower")
        void decayDivisor5_slowerCeiling() {
            // Default is divisor=5
            double at1000 = LevelScaling.getMultiplier(1000);
            double ceiling = LevelScaling.getCeiling();
            double pctOfCeiling = at1000 / ceiling;

            // With divisor=5, level 1000 is ~92%, NOT >95%
            assertTrue(pctOfCeiling > 0.85 && pctOfCeiling < 0.96,
                String.format("With divisor=5, level 1000 (%.4f) should be 85-96%% of ceiling (%.4f), was %.1f%%",
                    at1000, ceiling, pctOfCeiling * 100));
        }

        @Test
        @DisplayName("Higher decayDivisor means slower approach to ceiling")
        void higherDivisor_slowerApproach() {
            // At level 500, compare divisor=1 vs divisor=5
            LevelScaling.configure(100, 2.0, 1.0);
            double atDivisor1 = LevelScaling.getMultiplier(500);

            LevelScaling.configure(100, 2.0, 5.0);
            double atDivisor5 = LevelScaling.getMultiplier(500);

            assertTrue(atDivisor1 > atDivisor5,
                String.format("Divisor 1 at level 500 (%.4f) should be > divisor 5 (%.4f)",
                    atDivisor1, atDivisor5));
        }

        @Test
        @DisplayName("3-param configure should set decayDivisor")
        void configureWithDecayDivisor() {
            LevelScaling.configure(100, 2.0, 10.0);
            assertEquals(10.0, LevelScaling.getDecayDivisor(), 0.001);
            assertEquals(100, LevelScaling.getTransitionLevel());
            assertEquals(2.0, LevelScaling.getMaxMultiplierRatio(), 0.001);
        }

        @Test
        @DisplayName("2-param configure should preserve existing decayDivisor")
        void configureWithoutDecayDivisor_preserves() {
            LevelScaling.configure(100, 2.0, 10.0);
            assertEquals(10.0, LevelScaling.getDecayDivisor(), 0.001);

            // Now call 2-param version — should keep divisor=10
            LevelScaling.configure(80, 2.5);
            assertEquals(10.0, LevelScaling.getDecayDivisor(), 0.001);
            assertEquals(80, LevelScaling.getTransitionLevel());
            assertEquals(2.5, LevelScaling.getMaxMultiplierRatio(), 0.001);
        }

        @Test
        @DisplayName("DecayDivisor below 1.0 should be clamped to 1.0")
        void decayDivisorClamped() {
            LevelScaling.configure(100, 2.0, 0.1);
            assertEquals(1.0, LevelScaling.getDecayDivisor(), 0.001);
        }
    }

    // =========================================================================
    // Edge Cases
    // =========================================================================

    @ParameterizedTest
    @DisplayName("Edge case levels should not throw exceptions")
    @ValueSource(ints = {Integer.MIN_VALUE, -1000, -1, 0, 1, 2, Integer.MAX_VALUE})
    void edgeCaseLevels_noException(int level) {
        assertDoesNotThrow(() -> LevelScaling.getBonusPercent(level));
        assertDoesNotThrow(() -> LevelScaling.getMultiplier(level));
    }

    @Test
    @DisplayName("Very high levels should be finite and at most the ceiling")
    void veryHighLevels_noOverflow() {
        double multiplier = LevelScaling.getMultiplier(Integer.MAX_VALUE);
        assertTrue(Double.isFinite(multiplier), "Multiplier should be finite for max int");
        assertTrue(multiplier > 1.0, "Multiplier should be > 1.0 for high levels");
        assertTrue(multiplier <= LevelScaling.getCeiling(), "Multiplier should be <= ceiling");
    }
}
