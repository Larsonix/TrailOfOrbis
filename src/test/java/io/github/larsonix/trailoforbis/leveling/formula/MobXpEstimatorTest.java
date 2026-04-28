package io.github.larsonix.trailoforbis.leveling.formula;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class MobXpEstimatorTest {

    /**
     * Creates a default estimator using the current production config values.
     * xpPerMobLevel=5, poolMultiplier=0.1, pointsPerLevel=20,
     * progressiveScaling enabled, softCap=30, minFactor=0.3
     */
    private MobXpEstimator createDefaultEstimator() {
        return new MobXpEstimator(5.0, 0.1, 20.0, true, 30, 0.3);
    }

    // ═══════════════════════════════════════════════════════════════════
    // BASIC ESTIMATION TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Basic Estimation")
    class BasicEstimation {

        @Test
        @DisplayName("Should return at least 1 XP for any level")
        void shouldReturnAtLeastOneXp() {
            MobXpEstimator estimator = createDefaultEstimator();
            for (int level = 1; level <= 200; level++) {
                assertTrue(estimator.estimateXpPerMob(level) >= 1,
                    "Level " + level + " should give at least 1 XP");
            }
        }

        @Test
        @DisplayName("XP should generally increase with level")
        void xpShouldIncreaseWithLevel() {
            MobXpEstimator estimator = createDefaultEstimator();

            long xpAt1 = estimator.estimateXpPerMob(1);
            long xpAt10 = estimator.estimateXpPerMob(10);
            long xpAt50 = estimator.estimateXpPerMob(50);
            long xpAt100 = estimator.estimateXpPerMob(100);

            assertTrue(xpAt10 > xpAt1, "Level 10 XP should be > level 1 XP");
            assertTrue(xpAt50 > xpAt10, "Level 50 XP should be > level 10 XP");
            assertTrue(xpAt100 > xpAt50, "Level 100 XP should be > level 50 XP");
        }

        @Test
        @DisplayName("Should handle level 0 and negative levels")
        void shouldHandleInvalidLevels() {
            MobXpEstimator estimator = createDefaultEstimator();
            assertTrue(estimator.estimateXpPerMob(0) >= 1, "Level 0 should give at least 1 XP");
            assertTrue(estimator.estimateXpPerMob(-5) >= 1, "Negative level should give at least 1 XP");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // PROGRESSIVE SCALING TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Progressive Scaling Effect")
    class ProgressiveScaling {

        @Test
        @DisplayName("Should produce less XP at low levels when progressive scaling is on")
        void progressiveScalingShouldReduceEarlyXp() {
            MobXpEstimator withScaling = new MobXpEstimator(5.0, 0.1, 20.0, true, 30, 0.3);
            MobXpEstimator withoutScaling = new MobXpEstimator(5.0, 0.1, 20.0, false, 30, 0.3);

            long xpWith = withScaling.estimateXpPerMob(5);
            long xpWithout = withoutScaling.estimateXpPerMob(5);

            assertTrue(xpWith < xpWithout,
                "Progressive scaling should reduce XP at level 5: " + xpWith + " vs " + xpWithout);
        }

        @Test
        @DisplayName("Should produce same XP at/above soft cap level")
        void shouldProduceSameXpAboveSoftCap() {
            MobXpEstimator withScaling = new MobXpEstimator(5.0, 0.1, 20.0, true, 30, 0.3);
            MobXpEstimator withoutScaling = new MobXpEstimator(5.0, 0.1, 20.0, false, 30, 0.3);

            // At soft cap level, scaling factor is 1.0
            assertEquals(withScaling.estimateXpPerMob(30), withoutScaling.estimateXpPerMob(30));
            assertEquals(withScaling.estimateXpPerMob(50), withoutScaling.estimateXpPerMob(50));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CONSISTENCY WITH ACTUAL CALCULATOR
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Consistency Checks")
    class ConsistencyChecks {

        @Test
        @DisplayName("Level 1 XP should be small but positive")
        void levelOneXpShouldBeSmall() {
            MobXpEstimator estimator = createDefaultEstimator();
            long xp = estimator.estimateXpPerMob(1);
            // Level 1: base = 1*5 = 5, pool is very small due to progressive scaling
            // Total should be modest
            assertTrue(xp >= 1 && xp < 100,
                "Level 1 XP should be modest: " + xp);
        }

        @Test
        @DisplayName("Level 100 XP should be significantly higher than level 1")
        void levelHundredXpShouldBeHigher() {
            MobXpEstimator estimator = createDefaultEstimator();
            long xpAt1 = estimator.estimateXpPerMob(1);
            long xpAt100 = estimator.estimateXpPerMob(100);

            assertTrue(xpAt100 > xpAt1 * 10,
                "Level 100 should give at least 10x more XP than level 1: " +
                xpAt100 + " vs " + xpAt1);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // toString
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("toString should include all parameters")
    void toStringShouldIncludeParams() {
        MobXpEstimator estimator = createDefaultEstimator();
        String str = estimator.toString();
        assertTrue(str.contains("xpPerMobLevel=5.0"), "Should contain xpPerMobLevel");
        assertTrue(str.contains("poolMultiplier=0.10"), "Should contain poolMultiplier");
        assertTrue(str.contains("pointsPerLevel=20.0"), "Should contain pointsPerLevel");
    }
}
