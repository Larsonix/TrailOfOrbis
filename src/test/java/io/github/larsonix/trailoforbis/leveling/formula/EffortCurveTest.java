package io.github.larsonix.trailoforbis.leveling.formula;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EffortCurveTest {

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTION TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Constructor Validation")
    class ConstructorValidation {

        @Test
        @DisplayName("Should reject non-positive baseMobs")
        void shouldRejectNonPositiveBaseMobs() {
            assertThrows(IllegalArgumentException.class,
                () -> new EffortCurve(0, 150, 100));
            assertThrows(IllegalArgumentException.class,
                () -> new EffortCurve(-1, 150, 100));
        }

        @Test
        @DisplayName("Should reject targetMobs <= baseMobs")
        void shouldRejectTargetMobsNotGreaterThanBase() {
            assertThrows(IllegalArgumentException.class,
                () -> new EffortCurve(3, 3, 100));
            assertThrows(IllegalArgumentException.class,
                () -> new EffortCurve(3, 2, 100));
        }

        @Test
        @DisplayName("Should reject targetLevel < 2")
        void shouldRejectTargetLevelLessThanTwo() {
            assertThrows(IllegalArgumentException.class,
                () -> new EffortCurve(3, 150, 1));
            assertThrows(IllegalArgumentException.class,
                () -> new EffortCurve(3, 150, 0));
        }

        @Test
        @DisplayName("Should accept valid parameters")
        void shouldAcceptValidParameters() {
            assertDoesNotThrow(() -> new EffortCurve(3, 150, 100));
            assertDoesNotThrow(() -> new EffortCurve(1, 10, 2));
            assertDoesNotThrow(() -> new EffortCurve(5, 200, 50));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // POWER-LAW CURVE TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getMobsPerLevel")
    class GetMobsPerLevel {

        @Test
        @DisplayName("Level 1 should return baseMobs")
        void levelOneShouldReturnBaseMobs() {
            EffortCurve curve = new EffortCurve(3, 150, 100);
            assertEquals(3.0, curve.getMobsPerLevel(1), 0.01);
        }

        @Test
        @DisplayName("Target level should return targetMobs")
        void targetLevelShouldReturnTargetMobs() {
            EffortCurve curve = new EffortCurve(3, 150, 100);
            assertEquals(150.0, curve.getMobsPerLevel(100), 0.5);
        }

        @Test
        @DisplayName("Should be monotonically increasing")
        void shouldBeMonotonicallyIncreasing() {
            EffortCurve curve = new EffortCurve(3, 150, 100);

            double previous = 0;
            for (int level = 1; level <= 200; level++) {
                double mobs = curve.getMobsPerLevel(level);
                assertTrue(mobs > previous,
                    "Mobs should increase: level " + level + " gave " + mobs + " <= " + previous);
                previous = mobs;
            }
        }

        @Test
        @DisplayName("Should handle level 0 and negative levels")
        void shouldHandleInvalidLevels() {
            EffortCurve curve = new EffortCurve(3, 150, 100);
            // max(1, level) means these all give baseMobs
            assertEquals(3.0, curve.getMobsPerLevel(0), 0.01);
            assertEquals(3.0, curve.getMobsPerLevel(-5), 0.01);
        }

        @Test
        @DisplayName("Should scale correctly at intermediate levels")
        void shouldScaleAtIntermediateLevels() {
            EffortCurve curve = new EffortCurve(3, 150, 100);

            // At level 10, should be between baseMobs(3) and targetMobs(150)
            double mobsAt10 = curve.getMobsPerLevel(10);
            assertTrue(mobsAt10 > 3, "Level 10 should require more than 3 mobs");
            assertTrue(mobsAt10 < 150, "Level 10 should require fewer than 150 mobs");

            // At level 50, should be between level 10 and level 100
            double mobsAt50 = curve.getMobsPerLevel(50);
            assertTrue(mobsAt50 > mobsAt10, "Level 50 should require more mobs than level 10");
            assertTrue(mobsAt50 < 150, "Level 50 should require fewer than 150 mobs");
        }

        @Test
        @DisplayName("Should continue growing past target level")
        void shouldGrowPastTargetLevel() {
            EffortCurve curve = new EffortCurve(3, 150, 100);

            double mobsAt100 = curve.getMobsPerLevel(100);
            double mobsAt200 = curve.getMobsPerLevel(200);
            assertTrue(mobsAt200 > mobsAt100,
                "Level 200 should require more mobs than level 100");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // EXPONENT DERIVATION TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Exponent Derivation")
    class ExponentDerivation {

        @Test
        @DisplayName("Derived exponent should be positive")
        void derivedExponentShouldBePositive() {
            EffortCurve curve = new EffortCurve(3, 150, 100);
            assertTrue(curve.getExponent() > 0,
                "Exponent should be positive: " + curve.getExponent());
        }

        @Test
        @DisplayName("Different params should produce different exponents")
        void differentParamsDifferentExponents() {
            EffortCurve steep = new EffortCurve(1, 1000, 100);
            EffortCurve gentle = new EffortCurve(1, 10, 100);
            assertTrue(steep.getExponent() > gentle.getExponent(),
                "Steeper curve should have higher exponent");
        }

        @Test
        @DisplayName("Exponent formula: ln(target/base) / ln(targetLevel)")
        void exponentFormulaShouldBeCorrect() {
            EffortCurve curve = new EffortCurve(3, 150, 100);
            double expected = Math.log(150.0 / 3.0) / Math.log(100);
            assertEquals(expected, curve.getExponent(), 0.0001);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // toString
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("toString should include baseMobs and exponent")
    void toStringShouldIncludeParams() {
        EffortCurve curve = new EffortCurve(3, 150, 100);
        String str = curve.toString();
        assertTrue(str.contains("baseMobs=3.0"), "Should contain baseMobs");
        assertTrue(str.contains("exponent="), "Should contain exponent");
    }
}
