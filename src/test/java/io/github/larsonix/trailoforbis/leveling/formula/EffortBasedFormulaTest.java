package io.github.larsonix.trailoforbis.leveling.formula;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for EffortBasedFormula — the effort-based XP calculation.
 *
 * <p>Mirrors the structure of ExponentialFormulaTest to ensure the same
 * LevelFormula contract is satisfied.
 */
class EffortBasedFormulaTest {

    /**
     * Creates a formula with default production config values.
     */
    private EffortBasedFormula createDefaultFormula() {
        return createFormula(3.0, 150.0, 100, 100);
    }

    /**
     * Creates a formula with custom effort curve params.
     */
    private EffortBasedFormula createFormula(double baseMobs, double targetMobs, int targetLevel, int maxLevel) {
        EffortCurve curve = new EffortCurve(baseMobs, targetMobs, targetLevel);
        MobXpEstimator estimator = new MobXpEstimator(5.0, 0.1, 20.0, true, 30, 0.3);
        return new EffortBasedFormula(curve, estimator, maxLevel);
    }

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTION TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Constructor Validation")
    class ConstructorValidation {

        @Test
        @DisplayName("Should reject null curve")
        void shouldRejectNullCurve() {
            MobXpEstimator estimator = new MobXpEstimator(5.0, 0.1, 20.0, true, 30, 0.3);
            assertThrows(IllegalArgumentException.class,
                () -> new EffortBasedFormula(null, estimator, 100));
        }

        @Test
        @DisplayName("Should reject null estimator")
        void shouldRejectNullEstimator() {
            EffortCurve curve = new EffortCurve(3, 150, 100);
            assertThrows(IllegalArgumentException.class,
                () -> new EffortBasedFormula(curve, null, 100));
        }

        @Test
        @DisplayName("Should reject maxLevel less than 1")
        void shouldRejectMaxLevelLessThanOne() {
            EffortCurve curve = new EffortCurve(3, 150, 100);
            MobXpEstimator estimator = new MobXpEstimator(5.0, 0.1, 20.0, true, 30, 0.3);
            assertThrows(IllegalArgumentException.class,
                () -> new EffortBasedFormula(curve, estimator, 0));
        }

        @Test
        @DisplayName("Should accept valid parameters")
        void shouldAcceptValidParameters() {
            assertDoesNotThrow(() -> createDefaultFormula());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // XP FOR LEVEL TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getXpForLevel")
    class GetXpForLevelTests {

        @Test
        @DisplayName("Level 1 should have 0 XP")
        void levelOneShouldHaveZeroXp() {
            EffortBasedFormula formula = createDefaultFormula();
            assertEquals(0, formula.getXpForLevel(1));
        }

        @Test
        @DisplayName("Level 2 should have positive XP")
        void levelTwoShouldHavePositiveXp() {
            EffortBasedFormula formula = createDefaultFormula();
            assertTrue(formula.getXpForLevel(2) > 0,
                "Level 2 should require some XP");
        }

        @Test
        @DisplayName("Should clamp to max level when exceeding max")
        void shouldClampToMaxLevel() {
            EffortBasedFormula formula = createFormula(3, 150, 100, 50);

            long maxXp = formula.getXpForLevel(50);
            assertEquals(maxXp, formula.getXpForLevel(51));
            assertEquals(maxXp, formula.getXpForLevel(1000));
        }

        @Test
        @DisplayName("Should return 0 for level 0 or negative")
        void shouldReturnZeroForInvalidLevels() {
            EffortBasedFormula formula = createDefaultFormula();
            assertEquals(0, formula.getXpForLevel(0));
            assertEquals(0, formula.getXpForLevel(-5));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // XP BETWEEN LEVELS TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getXpBetweenLevels")
    class GetXpBetweenLevelsTests {

        @Test
        @DisplayName("Should return positive XP for mid-range levels")
        void shouldReturnPositiveXpForMidRange() {
            EffortBasedFormula formula = createDefaultFormula();
            assertTrue(formula.getXpBetweenLevels(1) > 0,
                "XP between level 1 and 2 should be positive");
            assertTrue(formula.getXpBetweenLevels(50) > 0,
                "XP between level 50 and 51 should be positive");
        }

        @Test
        @DisplayName("Should return 0 at max level")
        void shouldReturnZeroAtMaxLevel() {
            EffortBasedFormula formula = createFormula(3, 150, 100, 50);
            assertEquals(0, formula.getXpBetweenLevels(49));
            assertEquals(0, formula.getXpBetweenLevels(50));
        }

        @Test
        @DisplayName("XP between levels should increase with level")
        void xpBetweenLevelsShouldIncrease() {
            EffortBasedFormula formula = createDefaultFormula();

            long xpAtLow = formula.getXpBetweenLevels(5);
            long xpAtMid = formula.getXpBetweenLevels(50);

            assertTrue(xpAtMid > xpAtLow,
                "XP between levels should increase: " + xpAtMid + " vs " + xpAtLow);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // LEVEL FOR XP TESTS (Binary Search)
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getLevelForXp")
    class GetLevelForXpTests {

        @Test
        @DisplayName("Should return level 1 for 0 XP")
        void shouldReturnLevelOneForZeroXp() {
            EffortBasedFormula formula = createDefaultFormula();
            assertEquals(1, formula.getLevelForXp(0));
        }

        @Test
        @DisplayName("Should return level 1 for negative XP")
        void shouldReturnLevelOneForNegativeXp() {
            EffortBasedFormula formula = createDefaultFormula();
            assertEquals(1, formula.getLevelForXp(-100));
        }

        @Test
        @DisplayName("Should return max level for very high XP")
        void shouldReturnMaxLevelForHighXp() {
            EffortBasedFormula formula = createFormula(3, 150, 100, 50);

            long maxXp = formula.getXpForLevel(50);
            assertEquals(50, formula.getLevelForXp(maxXp));
            assertEquals(50, formula.getLevelForXp(maxXp + 1000));
            assertEquals(50, formula.getLevelForXp(Long.MAX_VALUE));
        }

        @Test
        @DisplayName("Roundtrip: getXpForLevel -> getLevelForXp should be identity")
        void roundtripShouldBeIdentity() {
            EffortBasedFormula formula = createFormula(3, 150, 100, 100);

            for (int level = 1; level <= 100; level++) {
                long xp = formula.getXpForLevel(level);
                assertEquals(level, formula.getLevelForXp(xp),
                    "Roundtrip failed at level " + level);
            }
        }

        @Test
        @DisplayName("Binary search should be correct across all level boundaries")
        void binarySearchShouldBeCorrect() {
            EffortBasedFormula formula = createFormula(3, 150, 100, 100);

            for (int level = 2; level <= 100; level++) {
                long xpAtLevel = formula.getXpForLevel(level);

                // XP exactly at threshold gives this level
                assertEquals(level, formula.getLevelForXp(xpAtLevel),
                    "Level " + level + " boundary failed");

                // One less XP gives previous level
                assertEquals(level - 1, formula.getLevelForXp(xpAtLevel - 1),
                    "Level " + (level - 1) + " boundary (xp-1) failed");
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // MONOTONICITY AND INTEGRITY
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Formula Integrity")
    class FormulaIntegrity {

        @Test
        @DisplayName("XP table should be strictly monotonically increasing")
        void xpTableShouldBeMonotonic() {
            EffortBasedFormula formula = createDefaultFormula();

            long previousXp = -1;
            for (int level = 1; level <= 100; level++) {
                long xp = formula.getXpForLevel(level);
                assertTrue(xp > previousXp,
                    "XP table not monotonic at level " + level + ": " + xp + " <= " + previousXp);
                previousXp = xp;
            }
        }

        @Test
        @DisplayName("Level progress should be between 0 and 1")
        void levelProgressShouldBeValid() {
            EffortBasedFormula formula = createFormula(3, 150, 100, 100);

            for (int level = 1; level < 100; level++) {
                long xpAtLevel = formula.getXpForLevel(level);
                long xpAtNextLevel = formula.getXpForLevel(level + 1);

                float progress = formula.getLevelProgress(xpAtLevel + (xpAtNextLevel - xpAtLevel) / 2);
                assertTrue(progress > 0 && progress < 1,
                    "Progress should be between 0 and 1 at level " + level + ": " + progress);
            }
        }

        @Test
        @DisplayName("Progress at level threshold should be 0")
        void progressAtLevelShouldBeZero() {
            EffortBasedFormula formula = createFormula(3, 150, 100, 100);

            for (int level = 1; level <= 99; level++) {
                long xpAtLevel = formula.getXpForLevel(level);
                float progress = formula.getLevelProgress(xpAtLevel);
                assertEquals(0f, progress, 0.001f,
                    "Progress at level " + level + " should be 0");
            }
        }

        @Test
        @DisplayName("getMaxLevel should return configured value")
        void getMaxLevelShouldReturnConfigured() {
            assertEquals(100, createFormula(3, 150, 100, 100).getMaxLevel());
            assertEquals(50, createFormula(3, 150, 100, 50).getMaxLevel());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // EDGE CASES
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Should handle small max level")
        void shouldHandleSmallMaxLevel() {
            EffortBasedFormula formula = createFormula(3, 150, 100, 5);

            assertEquals(0, formula.getXpForLevel(1));
            assertTrue(formula.getXpForLevel(5) > 0, "Level 5 should have XP");
            assertEquals(formula.getXpForLevel(5), formula.getXpForLevel(10)); // Clamped
        }

        @Test
        @DisplayName("Should handle steep curve")
        void shouldHandleSteepCurve() {
            // 1 mob at level 1, 1000 at level 100
            EffortBasedFormula formula = createFormula(1, 1000, 100, 100);

            // Should still produce valid, monotonic results
            long previousXp = -1;
            for (int level = 1; level <= 100; level++) {
                long xp = formula.getXpForLevel(level);
                assertTrue(xp > previousXp,
                    "Steep curve not monotonic at level " + level);
                previousXp = xp;
            }
        }

        @Test
        @DisplayName("Should handle gentle curve")
        void shouldHandleGentleCurve() {
            // 5 mobs at level 1, 20 at level 100
            EffortBasedFormula formula = createFormula(5, 20, 100, 100);

            // Should still produce valid, monotonic results
            long previousXp = -1;
            for (int level = 1; level <= 100; level++) {
                long xp = formula.getXpForLevel(level);
                assertTrue(xp > previousXp,
                    "Gentle curve not monotonic at level " + level);
                previousXp = xp;
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ACCESSORS
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getCurve should return the curve")
    void getCurveShouldReturnCurve() {
        EffortBasedFormula formula = createDefaultFormula();
        assertNotNull(formula.getCurve());
        assertEquals(3.0, formula.getCurve().getBaseMobs(), 0.01);
    }

    @Test
    @DisplayName("getEstimator should return the estimator")
    void getEstimatorShouldReturnEstimator() {
        EffortBasedFormula formula = createDefaultFormula();
        assertNotNull(formula.getEstimator());
    }

    @Test
    @DisplayName("toString should include curve info")
    void toStringShouldIncludeInfo() {
        EffortBasedFormula formula = createDefaultFormula();
        String str = formula.toString();
        assertTrue(str.contains("EffortBasedFormula"), "Should contain class name");
        assertTrue(str.contains("maxLevel="), "Should contain maxLevel");
    }
}
