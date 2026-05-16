package io.github.larsonix.trailoforbis.leveling.formula;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Boundary tests for LevelFormula interface default methods and
 * formula implementations at extreme values.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>getLevelProgress edge cases (max level, zero XP, exact thresholds)</li>
 *   <li>getXpToNextLevel edge cases (max level, beyond max, mid-level)</li>
 *   <li>EffortBasedFormula overflow protection (Long.MAX_VALUE capping)</li>
 *   <li>Cross-formula LevelFormula contract verification</li>
 * </ul>
 */
class LevelFormulaBoundaryTest {

    private ExponentialFormula exponentialFormula;
    private EffortBasedFormula effortFormula;

    @BeforeEach
    void setUp() {
        exponentialFormula = new ExponentialFormula(100, 1.7, 100);
        effortFormula = createEffortFormula(3.0, 150.0, 100, 100);
    }

    private EffortBasedFormula createEffortFormula(double baseMobs, double targetMobs, int targetLevel, int maxLevel) {
        EffortCurve curve = new EffortCurve(baseMobs, targetMobs, targetLevel);
        MobXpEstimator estimator = new MobXpEstimator(5.0, 0.1, 20.0, true, 30, 0.3);
        return new EffortBasedFormula(curve, estimator, maxLevel);
    }

    // ═══════════════════════════════════════════════════════════════════
    // getLevelProgress BOUNDARY TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getLevelProgress Boundaries")
    class GetLevelProgressBoundaries {

        @Test
        @DisplayName("At max level should return 1.0")
        void atMaxLevel_shouldReturn1() {
            for (LevelFormula formula : new LevelFormula[]{exponentialFormula, effortFormula}) {
                long maxXp = formula.getXpForLevel(formula.getMaxLevel());
                assertEquals(1.0f, formula.getLevelProgress(maxXp),
                    "Progress at max level XP should be 1.0 for " + formula);
            }
        }

        @Test
        @DisplayName("Beyond max level should return 1.0")
        void beyondMaxLevel_shouldReturn1() {
            for (LevelFormula formula : new LevelFormula[]{exponentialFormula, effortFormula}) {
                long beyondMax = formula.getXpForLevel(formula.getMaxLevel()) + 1_000_000;
                assertEquals(1.0f, formula.getLevelProgress(beyondMax),
                    "Progress beyond max should be 1.0 for " + formula);
            }
        }

        @Test
        @DisplayName("At level 1 with zero XP should return 0.0")
        void atLevel1_withZeroXp_shouldReturn0() {
            for (LevelFormula formula : new LevelFormula[]{exponentialFormula, effortFormula}) {
                assertEquals(0.0f, formula.getLevelProgress(0),
                    "Progress at 0 XP should be 0.0 for " + formula);
            }
        }

        @Test
        @DisplayName("Just before next level should be near 1.0")
        void justBeforeNextLevel_shouldBeNear1() {
            for (LevelFormula formula : new LevelFormula[]{exponentialFormula, effortFormula}) {
                long almostLevel2 = formula.getXpForLevel(2) - 1;
                float progress = formula.getLevelProgress(almostLevel2);
                assertTrue(progress > 0.5f && progress < 1.0f,
                    "Progress just before level 2 should be close to 1.0 but < 1.0: " + progress);
            }
        }

        @Test
        @DisplayName("Exactly at level threshold should return 0.0 (start of new level)")
        void exactlyAtThreshold_shouldReturn0() {
            for (LevelFormula formula : new LevelFormula[]{exponentialFormula, effortFormula}) {
                long xpForLevel5 = formula.getXpForLevel(5);
                float progress = formula.getLevelProgress(xpForLevel5);
                assertEquals(0.0f, progress, 0.001f,
                    "Progress at exact level 5 threshold should be 0 for " + formula);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // getXpToNextLevel BOUNDARY TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getXpToNextLevel Boundaries")
    class GetXpToNextLevelBoundaries {

        @Test
        @DisplayName("At max level should return 0")
        void atMaxLevel_shouldReturn0() {
            for (LevelFormula formula : new LevelFormula[]{exponentialFormula, effortFormula}) {
                long maxXp = formula.getXpForLevel(formula.getMaxLevel());
                assertEquals(0, formula.getXpToNextLevel(maxXp),
                    "XP to next level at max should be 0 for " + formula);
            }
        }

        @Test
        @DisplayName("Beyond max level should return 0")
        void beyondMaxLevel_shouldReturn0() {
            for (LevelFormula formula : new LevelFormula[]{exponentialFormula, effortFormula}) {
                assertEquals(0, formula.getXpToNextLevel(Long.MAX_VALUE),
                    "XP to next at Long.MAX_VALUE should be 0 for " + formula);
            }
        }

        @Test
        @DisplayName("At level 1 should return level 2 threshold")
        void atLevel1_shouldReturnLevel2Threshold() {
            for (LevelFormula formula : new LevelFormula[]{exponentialFormula, effortFormula}) {
                long xpToNext = formula.getXpToNextLevel(0);
                long level2Xp = formula.getXpForLevel(2);
                assertEquals(level2Xp, xpToNext,
                    "XP to next from 0 should equal level 2 threshold for " + formula);
            }
        }

        @Test
        @DisplayName("Mid-level should return correct remainder")
        void midLevel_shouldReturnRemainder() {
            for (LevelFormula formula : new LevelFormula[]{exponentialFormula, effortFormula}) {
                long xpForLevel5 = formula.getXpForLevel(5);
                long xpForLevel6 = formula.getXpForLevel(6);
                long midPoint = xpForLevel5 + (xpForLevel6 - xpForLevel5) / 2;

                long xpToNext = formula.getXpToNextLevel(midPoint);
                assertEquals(xpForLevel6 - midPoint, xpToNext,
                    "XP to next from mid-level should be remainder for " + formula);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // EFFORT FORMULA OVERFLOW PROTECTION
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("EffortBasedFormula Overflow Protection")
    class OverflowProtection {

        @Test
        @DisplayName("Extreme params should cap at Long.MAX_VALUE without exception")
        void extremeParams_shouldCapAtMaxLong() {
            // Very steep curve with huge XP per mob → will overflow long before maxLevel
            EffortCurve extremeCurve = new EffortCurve(1.0, 1e10, 100);
            MobXpEstimator bigEstimator = new MobXpEstimator(1000.0, 1.0, 1000.0, false, 100, 1.0);

            assertDoesNotThrow(() -> new EffortBasedFormula(extremeCurve, bigEstimator, 500));

            EffortBasedFormula extreme = new EffortBasedFormula(extremeCurve, bigEstimator, 500);

            // The highest levels should be capped at Long.MAX_VALUE
            long xpAtMax = extreme.getXpForLevel(500);
            assertTrue(xpAtMax > 0, "XP at max should be positive");

            // getLevelForXp at MAX_VALUE should return a valid level
            int levelAtMax = extreme.getLevelForXp(Long.MAX_VALUE);
            assertTrue(levelAtMax >= 1 && levelAtMax <= 500,
                "Level at MAX_VALUE should be in valid range: " + levelAtMax);
        }

        @Test
        @DisplayName("Binary search at exact thresholds returns correct level")
        void binarySearch_atExactThresholds_shouldReturnCorrectLevel() {
            // Test both formulas: for every level, getLevelForXp(getXpForLevel(L)) == L
            for (LevelFormula formula : new LevelFormula[]{exponentialFormula, effortFormula}) {
                for (int level = 1; level <= formula.getMaxLevel(); level++) {
                    long xp = formula.getXpForLevel(level);
                    int derivedLevel = formula.getLevelForXp(xp);
                    assertEquals(level, derivedLevel,
                        "getLevelForXp(getXpForLevel(" + level + ")) should return " + level
                            + " for " + formula.getClass().getSimpleName());
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CROSS-FORMULA CONTRACT VERIFICATION
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("LevelFormula Contract")
    class ContractVerification {

        @Test
        @DisplayName("Level 1 always requires 0 XP")
        void level1_alwaysRequires0Xp() {
            for (LevelFormula formula : new LevelFormula[]{exponentialFormula, effortFormula}) {
                assertEquals(0, formula.getXpForLevel(1),
                    "Level 1 must require 0 XP for " + formula);
            }
        }

        @Test
        @DisplayName("XP requirements are monotonically increasing")
        void xpRequirements_areMonotonicallyIncreasing() {
            for (LevelFormula formula : new LevelFormula[]{exponentialFormula, effortFormula}) {
                long previousXp = 0;
                for (int level = 2; level <= formula.getMaxLevel(); level++) {
                    long currentXp = formula.getXpForLevel(level);
                    assertTrue(currentXp > previousXp,
                        "XP at level " + level + " (" + currentXp + ") must exceed level "
                            + (level - 1) + " (" + previousXp + ") for " + formula);
                    previousXp = currentXp;
                }
            }
        }

        @Test
        @DisplayName("getLevelForXp(0) always returns 1")
        void getLevelForXp0_alwaysReturns1() {
            for (LevelFormula formula : new LevelFormula[]{exponentialFormula, effortFormula}) {
                assertEquals(1, formula.getLevelForXp(0),
                    "Level for 0 XP must be 1 for " + formula);
            }
        }

        @Test
        @DisplayName("getLevelForXp(MAX_VALUE) returns maxLevel")
        void getLevelForXpMaxValue_returnsMaxLevel() {
            for (LevelFormula formula : new LevelFormula[]{exponentialFormula, effortFormula}) {
                assertEquals(formula.getMaxLevel(), formula.getLevelForXp(Long.MAX_VALUE),
                    "Level for MAX_VALUE XP must be maxLevel for " + formula);
            }
        }
    }
}
