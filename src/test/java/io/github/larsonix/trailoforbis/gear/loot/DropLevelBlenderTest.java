package io.github.larsonix.trailoforbis.gear.loot;

import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig.LevelBlendingConfig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DropLevelBlender.
 */
class DropLevelBlenderTest {

    /**
     * Creates a blender with controlled random that returns a fixed int value.
     */
    private static Random fixedVarianceRandom(int varianceRoll) {
        Random mock = new Random() {
            @Override
            public int nextInt(int bound) {
                return varianceRoll;
            }
        };
        return mock;
    }

    /** Random that returns middle of variance range (no variance shift). */
    private static Random neutralRandom(int variance) {
        return fixedVarianceRandom(variance); // middle of (2*v+1) range → offset = 0
    }

    @Test
    @DisplayName("Gap=0: sourceLevel ± variance only")
    void gapZero_sourceWithVariance() {
        var config = new LevelBlendingConfig(true, 0.3, 5, 2);
        var blender = new DropLevelBlender(config);

        // variance=2, range=5, roll middle (2) → offset=0
        int result = blender.calculate(30, 30, neutralRandom(2));
        assertEquals(30, result);
    }

    @Test
    @DisplayName("Small positive gap: pulls toward player")
    void smallPositiveGap_pullsUp() {
        var config = new LevelBlendingConfig(true, 0.3, 5, 0);
        var blender = new DropLevelBlender(config);

        // gap = 40-30 = 10, pull = 10*0.3 = 3.0, clamp(3, -5, 5) = 3
        // blended = 30+3 = 33
        int result = blender.calculate(30, 40, neutralRandom(0));
        assertEquals(33, result);
    }

    @Test
    @DisplayName("Large positive gap: capped at +maxOffset")
    void largePositiveGap_capped() {
        var config = new LevelBlendingConfig(true, 0.3, 5, 0);
        var blender = new DropLevelBlender(config);

        // gap = 58-23 = 35, pull = 35*0.3 = 10.5, clamp(10.5, -5, 5) = 5
        // blended = 23+5 = 28
        int result = blender.calculate(23, 58, neutralRandom(0));
        assertEquals(28, result);
    }

    @Test
    @DisplayName("Small negative gap: pulls toward player (lower)")
    void smallNegativeGap_pullsDown() {
        var config = new LevelBlendingConfig(true, 0.3, 5, 0);
        var blender = new DropLevelBlender(config);

        // gap = 20-30 = -10, pull = -10*0.3 = -3.0, clamp(-3, -5, 5) = -3
        // blended = 30-3 = 27
        int result = blender.calculate(30, 20, neutralRandom(0));
        assertEquals(27, result);
    }

    @Test
    @DisplayName("Large negative gap: capped at -maxOffset")
    void largeNegativeGap_capped() {
        var config = new LevelBlendingConfig(true, 0.3, 5, 0);
        var blender = new DropLevelBlender(config);

        // gap = 5-40 = -35, pull = -35*0.3 = -10.5, clamp(-10.5, -5, 5) = -5
        // blended = 40-5 = 35
        int result = blender.calculate(40, 5, neutralRandom(0));
        assertEquals(35, result);
    }

    @Test
    @DisplayName("Floor at 1: low levels don't go below 1")
    void floorAtOne() {
        var config = new LevelBlendingConfig(true, 0.3, 5, 2);
        var blender = new DropLevelBlender(config);

        // source=1, player=1, pull=0, variance=-2 (roll=0 → offset=-2)
        // blended = 1 + 0 - 2 = -1 → clamped to 1
        int result = blender.calculate(1, 1, fixedVarianceRandom(0));
        assertEquals(1, result);
    }

    @Test
    @DisplayName("Disabled config: source ± variance only, no pull")
    void disabledConfig_noPull() {
        var config = new LevelBlendingConfig(false, 0.3, 5, 0);
        var blender = new DropLevelBlender(config);

        // disabled: no pull regardless of gap
        int result = blender.calculate(23, 58, neutralRandom(0));
        assertEquals(23, result);
    }

    @Test
    @DisplayName("Variance=0: exact blended value")
    void varianceZero_exactBlended() {
        var config = new LevelBlendingConfig(true, 0.3, 5, 0);
        var blender = new DropLevelBlender(config);

        // gap = 40-30 = 10, pull = 3, no variance
        int result = blender.calculate(30, 40, neutralRandom(0));
        assertEquals(33, result);
    }

    @Test
    @DisplayName("pullFactor=0: no pull applied")
    void pullFactorZero_noPull() {
        var config = new LevelBlendingConfig(true, 0.0, 5, 0);
        var blender = new DropLevelBlender(config);

        // pull = 0 regardless of gap
        int result = blender.calculate(23, 58, neutralRandom(0));
        assertEquals(23, result);
    }

    @Test
    @DisplayName("pullFactor=1: full pull, capped by maxOffset")
    void pullFactorOne_fullPullCapped() {
        var config = new LevelBlendingConfig(true, 1.0, 5, 0);
        var blender = new DropLevelBlender(config);

        // gap = 58-23 = 35, pull = 35*1.0 = 35, clamped to 5
        // blended = 23+5 = 28
        int result = blender.calculate(23, 58, neutralRandom(0));
        assertEquals(28, result);
    }

    @Test
    @DisplayName("Variance adds positive offset")
    void variancePositiveOffset() {
        var config = new LevelBlendingConfig(true, 0.3, 5, 2);
        var blender = new DropLevelBlender(config);

        // gap=0, pull=0, variance roll=4 → offset = 4-2 = +2
        int result = blender.calculate(30, 30, fixedVarianceRandom(4));
        assertEquals(32, result);
    }

    @Test
    @DisplayName("Variance adds negative offset")
    void varianceNegativeOffset() {
        var config = new LevelBlendingConfig(true, 0.3, 5, 2);
        var blender = new DropLevelBlender(config);

        // gap=0, pull=0, variance roll=0 → offset = 0-2 = -2
        int result = blender.calculate(30, 30, fixedVarianceRandom(0));
        assertEquals(28, result);
    }

    @Test
    @DisplayName("pullFactor=0.5: half pull applied")
    void pullFactorHalf_halfPull() {
        var config = new LevelBlendingConfig(true, 0.5, 10, 0);
        var blender = new DropLevelBlender(config);

        // gap = 40-20 = 20, pull = 20*0.5 = 10.0, clamp(10, -10, 10) = 10
        // blended = 20+10 = 30
        int result = blender.calculate(20, 40, neutralRandom(0));
        assertEquals(30, result);
    }

    @Test
    @DisplayName("Edge case: sourceLevel=1, playerLevel=100, small maxOffset")
    void edgeCase_lowSourceHighPlayer_smallMaxOffset() {
        var config = new LevelBlendingConfig(true, 0.5, 3, 0);
        var blender = new DropLevelBlender(config);

        // gap = 100-1 = 99, pull = 99*0.5 = 49.5, clamp(49.5, -3, 3) = 3
        // blended = 1+3 = 4
        int result = blender.calculate(1, 100, neutralRandom(0));
        assertEquals(4, result);
    }

    @Test
    @DisplayName("Edge case: sourceLevel=1, playerLevel=100, large maxOffset")
    void edgeCase_lowSourceHighPlayer_largeMaxOffset() {
        var config = new LevelBlendingConfig(true, 0.5, 100, 0);
        var blender = new DropLevelBlender(config);

        // gap = 100-1 = 99, pull = 99*0.5 = 49.5, clamp(49.5, -100, 100) = 49.5
        // blended = round(1 + 49.5) = round(50.5) = 51 (Math.round rounds .5 up)
        int result = blender.calculate(1, 100, neutralRandom(0));
        assertEquals(51, result);
    }

    @Test
    @DisplayName("Disabled config with variance: applies variance but no pull")
    void disabledConfig_withVariance() {
        var config = new LevelBlendingConfig(false, 0.5, 10, 3);
        var blender = new DropLevelBlender(config);

        // disabled: base=sourceLevel=20, variance roll=5 → offset = 5-3 = +2
        // result = 20 + 2 = 22
        int result = blender.calculate(20, 100, fixedVarianceRandom(5));
        assertEquals(22, result);
    }

    @Test
    @DisplayName("Disabled config with variance causing floor clamp")
    void disabledConfig_varianceFloor() {
        var config = new LevelBlendingConfig(false, 0.5, 10, 3);
        var blender = new DropLevelBlender(config);

        // disabled: base=sourceLevel=2, variance roll=0 → offset = 0-3 = -3
        // result = 2 - 3 = -1 → clamped to 1
        int result = blender.calculate(2, 100, fixedVarianceRandom(0));
        assertEquals(1, result);
    }

    @Test
    @DisplayName("Statistical variance: seeded random stays within bounds over many rolls")
    void varianceBounds_seededRandom() {
        var config = new LevelBlendingConfig(true, 0.3, 5, 3);
        var blender = new DropLevelBlender(config);
        var seeded = new Random(42L);

        int source = 50;
        int player = 50; // gap=0, pull=0, so only variance applies

        for (int i = 0; i < 1000; i++) {
            int result = blender.calculate(source, player, seeded);
            // variance=3, so result should be within [47, 53], but floor at 1
            assertTrue(result >= 47 && result <= 53,
                    "Result %d out of expected range [47, 53]".formatted(result));
        }
    }
}
