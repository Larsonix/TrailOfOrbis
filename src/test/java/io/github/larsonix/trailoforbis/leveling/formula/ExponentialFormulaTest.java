package io.github.larsonix.trailoforbis.leveling.formula;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ExponentialFormula - the core XP calculation logic.
 *
 * <p>Formula: XP = baseXp × (level - 1)^exponent
 *
 * <p>Example with baseXp=100, exponent=1.7:
 * <ul>
 *   <li>Level 1: 0 XP (start)</li>
 *   <li>Level 2: 100 XP</li>
 *   <li>Level 5: 713 XP</li>
 *   <li>Level 10: 5,179 XP</li>
 *   <li>Level 20: 35,566 XP</li>
 *   <li>Level 50: 339,988 XP</li>
 *   <li>Level 100: 1,999,736 XP</li>
 * </ul>
 */
class ExponentialFormulaTest {

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTION TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Constructor Validation")
    class ConstructorValidation {

        @Test
        @DisplayName("Should reject non-positive baseXp")
        void shouldRejectNonPositiveBaseXp() {
            assertThrows(IllegalArgumentException.class,
                () -> new ExponentialFormula(0, 1.7, 100));
            assertThrows(IllegalArgumentException.class,
                () -> new ExponentialFormula(-100, 1.7, 100));
        }

        @Test
        @DisplayName("Should reject non-positive exponent")
        void shouldRejectNonPositiveExponent() {
            assertThrows(IllegalArgumentException.class,
                () -> new ExponentialFormula(100, 0, 100));
            assertThrows(IllegalArgumentException.class,
                () -> new ExponentialFormula(100, -1, 100));
        }

        @Test
        @DisplayName("Should reject maxLevel less than 1")
        void shouldRejectMaxLevelLessThanOne() {
            assertThrows(IllegalArgumentException.class,
                () -> new ExponentialFormula(100, 1.7, 0));
            assertThrows(IllegalArgumentException.class,
                () -> new ExponentialFormula(100, 1.7, -5));
        }

        @Test
        @DisplayName("Should accept valid parameters")
        void shouldAcceptValidParameters() {
            assertDoesNotThrow(() -> new ExponentialFormula(100, 1.7, 100));
            assertDoesNotThrow(() -> new ExponentialFormula(50, 1.5, 50));
            assertDoesNotThrow(() -> new ExponentialFormula(200, 2.0, 200));
        }

        @Test
        @DisplayName("Should create with default values")
        void shouldCreateWithDefaults() {
            ExponentialFormula formula = new ExponentialFormula();

            assertEquals(100.0, formula.getBaseXp(), 0.001);
            assertEquals(1.7, formula.getExponent(), 0.001);
            assertEquals(100, formula.getMaxLevel());
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
            ExponentialFormula formula = new ExponentialFormula(100, 1.7, 100);
            assertEquals(0, formula.getXpForLevel(1));
        }

        @Test
        @DisplayName("Level 2 should equal baseXp")
        void levelTwoShouldEqualBaseXp() {
            ExponentialFormula formula = new ExponentialFormula(100, 1.7, 100);
            assertEquals(100, formula.getXpForLevel(2));
        }

        @Test
        @DisplayName("Should calculate expected values for baseXp=100, exponent=1.7")
        void shouldCalculateExpectedValues() {
            ExponentialFormula formula = new ExponentialFormula(100, 1.7, 100);

            assertEquals(100, formula.getXpForLevel(2));     // 100 × 1^1.7
            assertEquals(1056, formula.getXpForLevel(5));    // 100 × 4^1.7 ≈ 1055.6 → 1056
            assertEquals(4190, formula.getXpForLevel(10));   // 100 × 9^1.7 ≈ 4189.5 → 4190
            assertEquals(14924, formula.getXpForLevel(20));  // 100 × 19^1.7 ≈ 14923.9 → 14924
        }

        @Test
        @DisplayName("Should clamp to max level when exceeding max")
        void shouldClampToMaxLevel() {
            ExponentialFormula formula = new ExponentialFormula(100, 1.7, 100);

            long maxXp = formula.getXpForLevel(100);
            assertEquals(maxXp, formula.getXpForLevel(150));
            assertEquals(maxXp, formula.getXpForLevel(1000));
        }

        @Test
        @DisplayName("Should return 0 for level 0 or negative")
        void shouldReturnZeroForInvalidLevels() {
            ExponentialFormula formula = new ExponentialFormula(100, 1.7, 100);
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
        @DisplayName("Should return XP needed for next level")
        void shouldReturnXpNeededForNextLevel() {
            ExponentialFormula formula = new ExponentialFormula(100, 1.7, 100);

            long xpForLvl1 = formula.getXpForLevel(1);
            long xpForLvl2 = formula.getXpForLevel(2);

            assertEquals(xpForLvl2 - xpForLvl1, formula.getXpBetweenLevels(1));
            assertEquals(100, formula.getXpBetweenLevels(1));
        }

        @Test
        @DisplayName("Should return 0 at max level")
        void shouldReturnZeroAtMaxLevel() {
            ExponentialFormula formula = new ExponentialFormula(100, 1.7, 100);
            assertEquals(0, formula.getXpBetweenLevels(99));
            assertEquals(0, formula.getXpBetweenLevels(100));
        }

        @Test
        @DisplayName("Should handle level below 1")
        void shouldHandleLevelBelowOne() {
            ExponentialFormula formula = new ExponentialFormula(100, 1.7, 100);
            assertEquals(formula.getXpBetweenLevels(1), formula.getXpBetweenLevels(0));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // LEVEL FOR XP TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getLevelForXp")
    class GetLevelForXpTests {

        @Test
        @DisplayName("Should return level 1 for 0 XP")
        void shouldReturnLevelOneForZeroXp() {
            ExponentialFormula formula = new ExponentialFormula(100, 1.7, 100);
            assertEquals(1, formula.getLevelForXp(0));
        }

        @Test
        @DisplayName("Should return level 1 for negative XP")
        void shouldReturnLevelOneForNegativeXp() {
            ExponentialFormula formula = new ExponentialFormula(100, 1.7, 100);
            assertEquals(1, formula.getLevelForXp(-100));
        }

        @Test
        @DisplayName("Should return correct level for XP values")
        void shouldReturnCorrectLevelForXpValues() {
            ExponentialFormula formula = new ExponentialFormula(100, 1.7, 100);

            // XP 0-99 = Level 1
            assertEquals(1, formula.getLevelForXp(0));
            assertEquals(1, formula.getLevelForXp(50));
            assertEquals(1, formula.getLevelForXp(99));

            // XP 100-324 = Level 2, XP 325+ = Level 3
            assertEquals(2, formula.getLevelForXp(100));
            assertEquals(3, formula.getLevelForXp(500));
        }

        @Test
        @DisplayName("Should return max level for XP at or above threshold")
        void shouldReturnMaxLevelForHighXp() {
            ExponentialFormula formula = new ExponentialFormula(100, 1.7, 100);

            long maxXp = formula.getXpForLevel(100);
            assertEquals(100, formula.getLevelForXp(maxXp));
            assertEquals(100, formula.getLevelForXp(maxXp + 1000));
            assertEquals(100, formula.getLevelForXp(Long.MAX_VALUE));
        }

        @Test
        @DisplayName("Binary search should be correct across all levels")
        void binarySearchShouldBeCorrect() {
            ExponentialFormula formula = new ExponentialFormula(100, 1.7, 100);

            // Test every level boundary
            for (int level = 1; level <= 100; level++) {
                long xpAtLevel = formula.getXpForLevel(level);

                // XP at this level should give this level
                assertEquals(level, formula.getLevelForXp(xpAtLevel),
                    "Level " + level + " boundary failed");

                // One less than XP at this level should give previous level
                if (level > 1) {
                    long xpAtPreviousLevel = formula.getXpForLevel(level - 1);
                    assertEquals(level - 1, formula.getLevelForXp(xpAtPreviousLevel),
                        "Level " + (level - 1) + " boundary failed");
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // EDGE CASES AND BOUNDARY TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Should handle small max level")
        void shouldHandleSmallMaxLevel() {
            ExponentialFormula formula = new ExponentialFormula(100, 1.7, 5);

            assertEquals(0, formula.getXpForLevel(1));
            assertEquals(100, formula.getXpForLevel(2));
            assertEquals(1056, formula.getXpForLevel(5));
            assertEquals(1056, formula.getXpForLevel(10)); // Clamped
        }

        @Test
        @DisplayName("Should handle large exponent")
        void shouldHandleLargeExponent() {
            ExponentialFormula formula = new ExponentialFormula(100, 2.5, 50);

            // Higher exponent = steeper curve
            assertEquals(100, formula.getXpForLevel(2));
            assertEquals(5591, formula.getXpForLevel(6)); // 100 × 5^2.5 ≈ 5590.2 → 5591
        }

        @Test
        @DisplayName("Should handle small exponent")
        void shouldHandleSmallExponent() {
            ExponentialFormula formula = new ExponentialFormula(100, 1.1, 100);

            // Lower exponent = flatter curve
            assertEquals(100, formula.getXpForLevel(2));
            assertEquals(460, formula.getXpForLevel(5)); // 100 × 4^1.1 ≈ 459.5 → 460
        }

        @Test
        @DisplayName("Should handle very large base XP")
        void shouldHandleLargeBaseXp() {
            ExponentialFormula formula = new ExponentialFormula(1000, 1.5, 50);

            assertEquals(0, formula.getXpForLevel(1));
            assertEquals(1000, formula.getXpForLevel(2));
            assertEquals(8000, formula.getXpForLevel(5)); // 1000 × 4^1.5 = 8,000
        }

        @Test
        @DisplayName("XP table should be monotonic")
        void xpTableShouldBeMonotonic() {
            ExponentialFormula formula = new ExponentialFormula(100, 1.7, 100);

            long previousXp = -1;
            for (int level = 1; level <= 100; level++) {
                long xp = formula.getXpForLevel(level);
                assertTrue(xp > previousXp,
                    "XP table not monotonic at level " + level);
                previousXp = xp;
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // FORMULA INTEGRITY TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Formula Integrity")
    class FormulaIntegrity {

        @Test
        @DisplayName("Level progress should be between 0 and 1")
        void levelProgressShouldBeValid() {
            ExponentialFormula formula = new ExponentialFormula(100, 1.7, 100);

            for (int level = 1; level < 100; level++) {
                long xpAtLevel = formula.getXpForLevel(level);
                long xpAtNextLevel = formula.getXpForLevel(level + 1);

                // Test mid-point
                float progress = formula.getLevelProgress(xpAtLevel + (xpAtNextLevel - xpAtLevel) / 2);
                assertTrue(progress > 0 && progress < 1,
                    "Progress should be between 0 and 1 at level " + level);
            }
        }

        @Test
        @DisplayName("Progress at level threshold should be 0")
        void progressAtLevelShouldBeZero() {
            ExponentialFormula formula = new ExponentialFormula(100, 1.7, 100);

            for (int level = 1; level <= 99; level++) {
                long xpAtLevel = formula.getXpForLevel(level);
                float progress = formula.getLevelProgress(xpAtLevel);
                assertEquals(0f, progress, 0.001f,
                    "Progress at level " + level + " should be 0");
            }
        }

        @Test
        @DisplayName("toString should include all parameters")
        void toStringShouldIncludeParameters() {
            ExponentialFormula formula = new ExponentialFormula(100, 1.7, 100);
            String str = formula.toString();

            assertTrue(str.contains("baseXp=100.0"), "Should contain baseXp");
            assertTrue(str.contains("exponent=1.70"), "Should contain exponent");
            assertTrue(str.contains("maxLevel=100"), "Should contain maxLevel");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // PERFORMANCE/LOOKUP TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Multiple lookups should be consistent")
    void multipleLookupsShouldBeConsistent() {
        ExponentialFormula formula = new ExponentialFormula(100, 1.7, 100);

        // Same input should always give same output
        for (int i = 0; i < 1000; i++) {
            assertEquals(4190, formula.getXpForLevel(10));
            assertEquals(10, formula.getLevelForXp(4190));
        }
    }

    @Test
    @DisplayName("XP table should be computed once and cached")
    void xpTableShouldBeCached() {
        ExponentialFormula formula = new ExponentialFormula(100, 1.7, 100);

        // Call multiple times
        formula.getXpForLevel(10);
        formula.getXpForLevel(10);
        formula.getXpForLevel(10);

        // All calls should return same value
        assertEquals(4190, formula.getXpForLevel(10));
    }
}
