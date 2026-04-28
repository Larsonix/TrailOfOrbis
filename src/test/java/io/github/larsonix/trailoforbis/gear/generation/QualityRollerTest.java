package io.github.larsonix.trailoforbis.gear.generation;

import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig.QualityConfig;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig.QualityDropDistribution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for QualityRoller - 15 test cases.
 */
class QualityRollerTest {

    private QualityConfig config;

    @BeforeEach
    void setUp() {
        QualityDropDistribution dist = new QualityDropDistribution(0.15, 0.25, 0.10, 0.30, 0.195, 0.005);
        config = new QualityConfig(50, 1, 100, 101, dist);
    }

    // =========================================================================
    // BASIC ROLLING TESTS
    // =========================================================================

    @Nested
    @DisplayName("Basic Rolling")
    class BasicRollingTests {

        @Test
        @DisplayName("Roll returns value in valid range")
        void roll_ReturnsValueInRange() {
            QualityRoller roller = new QualityRoller(config);
            for (int i = 0; i < 1000; i++) {
                int quality = roller.roll();
                assertTrue(quality >= 1 && quality <= 101, "Quality should be 1-101, was: " + quality);
            }
        }

        @Test
        @DisplayName("Roll distribution matches config")
        void roll_DistributionMatchesConfig() {
            QualityRoller roller = new QualityRoller(config);
            Map<String, Integer> brackets = new HashMap<>();

            for (int i = 0; i < 10000; i++) {
                int quality = roller.roll();
                String bracket = roller.getBracketName(quality);
                brackets.merge(bracket, 1, Integer::sum);
            }

            // Poor should be ~15%
            double poorPercent = brackets.getOrDefault("Poor", 0) / 10000.0;
            assertTrue(poorPercent >= 0.12 && poorPercent <= 0.18,
                "Poor should be ~15%, was: " + poorPercent);

            // Perfect should be ~0.5%
            double perfectPercent = brackets.getOrDefault("Perfect", 0) / 10000.0;
            assertTrue(perfectPercent >= 0.002 && perfectPercent <= 0.008,
                "Perfect should be ~0.5%, was: " + perfectPercent);
        }

        @Test
        @DisplayName("Poor bracket returns values 1-25")
        void roll_PoorBracket_Values1To25() {
            QualityConfig poorOnlyConfig = createQualityConfig(1.0, 0, 0, 0, 0, 0);
            QualityRoller roller = new QualityRoller(poorOnlyConfig);

            for (int i = 0; i < 1000; i++) {
                int quality = roller.roll();
                assertTrue(quality >= 1 && quality <= 25, "Poor quality should be 1-25, was: " + quality);
            }
        }

        @Test
        @DisplayName("Normal bracket always returns exactly 50")
        void roll_NormalBracket_AlwaysExactly50() {
            QualityConfig normalOnlyConfig = createQualityConfig(0, 0, 1.0, 0, 0, 0);
            QualityRoller roller = new QualityRoller(normalOnlyConfig);

            for (int i = 0; i < 100; i++) {
                assertEquals(50, roller.roll());
            }
        }

        @Test
        @DisplayName("Perfect bracket always returns 101")
        void roll_PerfectBracket_Always101() {
            QualityConfig perfectOnlyConfig = createQualityConfig(0, 0, 0, 0, 0, 1.0);
            QualityRoller roller = new QualityRoller(perfectOnlyConfig);

            for (int i = 0; i < 100; i++) {
                assertEquals(101, roller.roll());
            }
        }
    }

    // =========================================================================
    // NON-PERFECT ROLLING TESTS
    // =========================================================================

    @Nested
    @DisplayName("Non-Perfect Rolling")
    class NonPerfectRollingTests {

        @Test
        @DisplayName("rollNonPerfect never returns 101")
        void rollNonPerfect_NeverReturns101() {
            QualityRoller roller = new QualityRoller(config);

            for (int i = 0; i < 10000; i++) {
                int quality = roller.rollNonPerfect();
                assertTrue(quality <= 100, "Non-perfect should be <= 100, was: " + quality);
            }
        }

        @Test
        @DisplayName("rollNonPerfect distribution is renormalized")
        void rollNonPerfect_DistributionRenormalized() {
            QualityRoller roller = new QualityRoller(config);

            Set<String> brackets = new HashSet<>();
            for (int i = 0; i < 10000; i++) {
                brackets.add(roller.getBracketName(roller.rollNonPerfect()));
            }

            assertTrue(brackets.contains("Poor"), "Should see Poor bracket");
            assertTrue(brackets.contains("Below Average"), "Should see Below Average bracket");
            assertTrue(brackets.contains("Normal"), "Should see Normal bracket");
            assertTrue(brackets.contains("Above Average"), "Should see Above Average bracket");
            assertTrue(brackets.contains("Excellent"), "Should see Excellent bracket");
            assertFalse(brackets.contains("Perfect"), "Should not see Perfect bracket");
        }
    }

    // =========================================================================
    // BRACKET NAME AND PROBABILITY TESTS
    // =========================================================================

    @Nested
    @DisplayName("Bracket Names and Probabilities")
    class BracketTests {

        @Test
        @DisplayName("All bracket names are correctly assigned")
        void getBracketName_AllBracketsCovered() {
            QualityRoller roller = new QualityRoller(config);

            assertEquals("Poor", roller.getBracketName(1));
            assertEquals("Poor", roller.getBracketName(25));
            assertEquals("Below Average", roller.getBracketName(26));
            assertEquals("Below Average", roller.getBracketName(49));
            assertEquals("Normal", roller.getBracketName(50));
            assertEquals("Above Average", roller.getBracketName(51));
            assertEquals("Above Average", roller.getBracketName(75));
            assertEquals("Excellent", roller.getBracketName(76));
            assertEquals("Excellent", roller.getBracketName(100));
            assertEquals("Perfect", roller.getBracketName(101));
        }

        @Test
        @DisplayName("getBracketProbability matches config")
        void getBracketProbability_MatchesConfig() {
            QualityRoller roller = new QualityRoller(config);

            assertEquals(config.dropDistribution().poor(), roller.getBracketProbability("poor"), 0.001);
            assertEquals(config.dropDistribution().perfect(), roller.getBracketProbability("perfect"), 0.001);
        }

        @Test
        @DisplayName("Unknown bracket returns zero probability")
        void getBracketProbability_UnknownBracket_ReturnsZero() {
            QualityRoller roller = new QualityRoller(config);
            assertEquals(0, roller.getBracketProbability("invalid"), 0.001);
        }
    }

    // =========================================================================
    // EDGE CASES AND MISC
    // =========================================================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Null config throws exception")
        void constructor_NullConfig_ThrowsException() {
            assertThrows(NullPointerException.class, () -> new QualityRoller(null));
        }

        @Test
        @DisplayName("Deterministic with seed")
        void roll_DeterministicWithSeed() {
            QualityRoller roller1 = new QualityRoller(config, new Random(54321));
            QualityRoller roller2 = new QualityRoller(config, new Random(54321));

            for (int i = 0; i < 100; i++) {
                assertEquals(roller1.roll(), roller2.roll());
            }
        }

        @Test
        @DisplayName("getConfig returns original config")
        void getConfig_ReturnsOriginalConfig() {
            QualityRoller roller = new QualityRoller(config);
            assertSame(config, roller.getConfig());
        }

        @Test
        @DisplayName("Excellent bracket returns values 76-100")
        void roll_ExcellentBracket_Values76To100() {
            QualityConfig excellentOnlyConfig = createQualityConfig(0, 0, 0, 0, 1.0, 0);
            QualityRoller roller = new QualityRoller(excellentOnlyConfig);

            for (int i = 0; i < 1000; i++) {
                int quality = roller.roll();
                assertTrue(quality >= 76 && quality <= 100, "Excellent quality should be 76-100, was: " + quality);
            }
        }

        @Test
        @DisplayName("Above Average bracket returns values 51-75")
        void roll_AboveAverageBracket_Values51To75() {
            QualityConfig aboveAvgOnlyConfig = createQualityConfig(0, 0, 0, 1.0, 0, 0);
            QualityRoller roller = new QualityRoller(aboveAvgOnlyConfig);

            for (int i = 0; i < 1000; i++) {
                int quality = roller.roll();
                assertTrue(quality >= 51 && quality <= 75, "Above Average quality should be 51-75, was: " + quality);
            }
        }
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private QualityConfig createQualityConfig(double poor, double belowAvg, double normal,
                                              double aboveAvg, double excellent, double perfect) {
        QualityDropDistribution dist = new QualityDropDistribution(
            poor, belowAvg, normal, aboveAvg, excellent, perfect
        );
        return new QualityConfig(50, 1, 100, 101, dist);
    }
}
