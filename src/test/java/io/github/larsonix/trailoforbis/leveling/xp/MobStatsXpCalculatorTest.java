package io.github.larsonix.trailoforbis.leveling.xp;

import io.github.larsonix.trailoforbis.leveling.config.LevelingConfig;
import io.github.larsonix.trailoforbis.mobs.classification.MobClassificationConfig;
import io.github.larsonix.trailoforbis.mobs.classification.RPGMobClass;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link MobStatsXpCalculator}.
 *
 * <p>Tests XP calculation based on mob level, stat pool, distance,
 * and classification multipliers.
 */
@DisplayName("MobStatsXpCalculator")
class MobStatsXpCalculatorTest {

    private LevelingConfig.XpGainConfig xpConfig;
    private MobClassificationConfig classConfig;
    private MobStatsXpCalculator calculator;

    @BeforeEach
    void setUp() {
        xpConfig = new LevelingConfig.XpGainConfig();
        // Default values: xpPerMobLevel=5.0, poolMultiplier=0.1

        classConfig = new MobClassificationConfig();
        // Default multipliers: PASSIVE=0.1, MINOR=0.5, HOSTILE=1.0, ELITE=1.5, BOSS=5.0

        calculator = new MobStatsXpCalculator(xpConfig, classConfig);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Basic XP Calculation Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Basic XP Calculation")
    class BasicXpCalculationTests {

        @Test
        @DisplayName("Level 1 hostile with no pool returns minimum XP")
        void level1Hostile_noPool_returnsMinimumXp() {
            long xp = calculator.calculateXp(1, 0, 0, RPGMobClass.HOSTILE);

            // baseXp = 1 * 5.0 = 5, poolBonus = 0, tierMult = 1.0, distMult = 1.0
            // xp = (5 + 0) * 1.0 * 1.0 = 5
            assertTrue(xp >= 1, "Should return at least 1 XP");
        }

        @Test
        @DisplayName("Higher level gives more XP")
        void higherLevel_givesMoreXp() {
            long xpLevel1 = calculator.calculateXp(1, 0, 0, RPGMobClass.HOSTILE);
            long xpLevel10 = calculator.calculateXp(10, 0, 0, RPGMobClass.HOSTILE);
            long xpLevel50 = calculator.calculateXp(50, 0, 0, RPGMobClass.HOSTILE);

            assertTrue(xpLevel10 > xpLevel1);
            assertTrue(xpLevel50 > xpLevel10);
        }

        @Test
        @DisplayName("Higher stat pool gives more XP")
        void higherStatPool_givesMoreXp() {
            long xpNoPool = calculator.calculateXp(10, 0, 0, RPGMobClass.HOSTILE);
            long xpSmallPool = calculator.calculateXp(10, 100, 0, RPGMobClass.HOSTILE);
            long xpLargePool = calculator.calculateXp(10, 1000, 0, RPGMobClass.HOSTILE);

            assertTrue(xpSmallPool > xpNoPool);
            assertTrue(xpLargePool > xpSmallPool);
        }

        @Test
        @DisplayName("Never returns 0 XP")
        void neverReturns0Xp() {
            long xp = calculator.calculateXp(1, 0, 0, RPGMobClass.PASSIVE);

            assertTrue(xp >= 1, "Should always return at least 1 XP");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Classification Multiplier Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Classification Multipliers")
    class ClassificationMultiplierTests {

        @Test
        @DisplayName("Elite gives more XP than Hostile")
        void elite_givesMoreXpThanHostile() {
            long hostileXp = calculator.calculateXp(20, 500, 0, RPGMobClass.HOSTILE);
            long eliteXp = calculator.calculateXp(20, 500, 0, RPGMobClass.ELITE);

            assertTrue(eliteXp > hostileXp,
                "Elite should give more XP than Hostile");
        }

        @Test
        @DisplayName("Boss gives more XP than Elite")
        void boss_givesMoreXpThanElite() {
            long eliteXp = calculator.calculateXp(20, 500, 0, RPGMobClass.ELITE);
            long bossXp = calculator.calculateXp(20, 500, 0, RPGMobClass.BOSS);

            assertTrue(bossXp > eliteXp,
                "Boss should give more XP than Elite");
        }

        @Test
        @DisplayName("Boss gives the most XP")
        void boss_givesMostXp() {
            long hostileXp = calculator.calculateXp(50, 1000, 0, RPGMobClass.HOSTILE);
            long bossXp = calculator.calculateXp(50, 1000, 0, RPGMobClass.BOSS);

            assertTrue(bossXp > hostileXp,
                "Boss should give significantly more XP");
        }

        @Test
        @DisplayName("Minor gives less XP than Hostile")
        void minor_givesLessXpThanHostile() {
            long minorXp = calculator.calculateXp(20, 500, 0, RPGMobClass.MINOR);
            long hostileXp = calculator.calculateXp(20, 500, 0, RPGMobClass.HOSTILE);

            assertTrue(minorXp < hostileXp,
                "Minor should give less XP than Hostile");
        }

        @Test
        @DisplayName("Minor gives more XP than Passive")
        void minor_givesMoreXpThanPassive() {
            long passiveXp = calculator.calculateXp(20, 500, 0, RPGMobClass.PASSIVE);
            long minorXp = calculator.calculateXp(20, 500, 0, RPGMobClass.MINOR);

            assertTrue(minorXp > passiveXp,
                "Minor should give more XP than Passive");
        }

        @Test
        @DisplayName("Minor gives approximately 50% of Hostile XP")
        void minor_givesHalfHostileXp() {
            long minorXp = calculator.calculateXp(50, 1000, 0, RPGMobClass.MINOR);
            long hostileXp = calculator.calculateXp(50, 1000, 0, RPGMobClass.HOSTILE);

            // Minor is 0.5x, Hostile is 1.0x
            // minorXp should be roughly half of hostileXp
            double ratio = (double) minorXp / hostileXp;
            assertTrue(ratio >= 0.45 && ratio <= 0.55,
                "Minor should give approximately 50% of Hostile XP, got " +
                String.format("%.1f%%", ratio * 100));
        }

        @Test
        @DisplayName("Passive gives less XP than Hostile")
        void passive_givesLessXpThanHostile() {
            long passiveXp = calculator.calculateXp(10, 100, 0, RPGMobClass.PASSIVE);
            long hostileXp = calculator.calculateXp(10, 100, 0, RPGMobClass.HOSTILE);

            assertTrue(passiveXp <= hostileXp,
                "Passive should give less or equal XP than Hostile");
        }

        @Test
        @DisplayName("Passive gives minimal XP (if configured with non-zero multiplier)")
        void passive_givesMinimalXp() {
            // With 0.1 multiplier (default), this will return minimal XP
            long passiveXp = calculator.calculateXp(10, 100, 0, RPGMobClass.PASSIVE);

            assertTrue(passiveXp >= 1);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Distance Multiplier Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Distance Multiplier")
    class DistanceMultiplierTests {

        @Test
        @DisplayName("Farther distance gives more XP")
        void fartherDistance_givesMoreXp() {
            long xpAtSpawn = calculator.calculateXp(20, 500, 0, RPGMobClass.HOSTILE);
            long xpFar = calculator.calculateXp(20, 500, 10, RPGMobClass.HOSTILE);
            long xpVeryFar = calculator.calculateXp(20, 500, 50, RPGMobClass.HOSTILE);

            assertTrue(xpFar > xpAtSpawn,
                "Distance should increase XP");
            assertTrue(xpVeryFar > xpFar,
                "More distance should increase XP more");
        }

        @Test
        @DisplayName("Distance level 0 uses base XP")
        void distanceLevel0_usesBaseXp() {
            // At distance 0, distanceMult = 1.0 + (0 * 0.028) = 1.0
            long xp = calculator.calculateXp(10, 100, 0, RPGMobClass.HOSTILE);

            assertNotNull(xp);
            assertTrue(xp >= 1);
        }

        @Test
        @DisplayName("Distance bonus is approximately 2.8% per level")
        void distanceBonus_isApprox2_8PercentPerLevel() {
            long baseXp = calculator.calculateXp(100, 1000, 0, RPGMobClass.HOSTILE);
            long xpAt10 = calculator.calculateXp(100, 1000, 10, RPGMobClass.HOSTILE);

            // At distance 10, bonus = 10 * 2.8% = 28%
            // xpAt10 should be approximately baseXp * 1.28
            double expectedIncrease = 1.28;
            double actualIncrease = (double) xpAt10 / baseXp;

            // Allow some tolerance due to rounding
            assertTrue(actualIncrease >= 1.2 && actualIncrease <= 1.4,
                "Distance bonus should be approximately 28% at level 10, got " +
                String.format("%.2f%%", (actualIncrease - 1) * 100));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Configuration Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Configuration")
    class ConfigurationTests {

        @Test
        @DisplayName("getConfig returns injected config")
        void getConfig_returnsInjectedConfig() {
            assertSame(xpConfig, calculator.getConfig());
        }

        @Test
        @DisplayName("xpPerMobLevel affects XP calculation")
        void xpPerMobLevel_affectsXpCalculation() {
            // Create separate configs to compare
            LevelingConfig.XpGainConfig defaultConfig = new LevelingConfig.XpGainConfig();
            LevelingConfig.XpGainConfig doubledConfig = new LevelingConfig.XpGainConfig();
            doubledConfig.setXpPerMobLevel(10.0); // Double the default (5.0)

            MobStatsXpCalculator defaultCalc = new MobStatsXpCalculator(defaultConfig, classConfig);
            MobStatsXpCalculator doubledCalc = new MobStatsXpCalculator(doubledConfig, classConfig);

            long defaultXp = defaultCalc.calculateXp(10, 0, 0, RPGMobClass.HOSTILE);
            long doubledXp = doubledCalc.calculateXp(10, 0, 0, RPGMobClass.HOSTILE);

            assertTrue(doubledXp > defaultXp,
                "Higher xpPerMobLevel should give more XP");
        }

        @Test
        @DisplayName("poolMultiplier affects XP calculation")
        void poolMultiplier_affectsXpCalculation() {
            LevelingConfig.XpGainConfig highPoolConfig = new LevelingConfig.XpGainConfig();
            highPoolConfig.setPoolMultiplier(0.5); // 5x the default
            MobStatsXpCalculator customCalc = new MobStatsXpCalculator(highPoolConfig, classConfig);

            long defaultXp = calculator.calculateXp(10, 1000, 0, RPGMobClass.HOSTILE);
            long highPoolXp = customCalc.calculateXp(10, 1000, 0, RPGMobClass.HOSTILE);

            assertTrue(highPoolXp > defaultXp,
                "Higher poolMultiplier should give more XP for same pool");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Unscaled Mob Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Unscaled Mob Handling")
    class UnscaledMobTests {

        @Test
        @DisplayName("getBaseXpForUnscaledMob returns at least 1")
        void getBaseXpForUnscaledMob_returnsAtLeast1() {
            long baseXp = calculator.getBaseXpForUnscaledMob();

            assertTrue(baseXp >= 1);
        }

        @Test
        @DisplayName("getBaseXpForUnscaledMob returns xpPerMobLevel * 2")
        void getBaseXpForUnscaledMob_returnsXpPerMobLevelTimes2() {
            // Default xpPerMobLevel = 5.0
            long baseXp = calculator.getBaseXpForUnscaledMob();

            assertEquals(10, baseXp); // 5.0 * 2
        }

        @Test
        @DisplayName("calculateXpFromRawStats handles zero health")
        void calculateXpFromRawStats_handlesZeroHealth() {
            long xp = calculator.calculateXpFromRawStats(0, 100, RPGMobClass.HOSTILE);

            assertTrue(xp >= 1, "Should return at least 1 XP");
        }

        @Test
        @DisplayName("calculateXpFromRawStats scales with distance")
        void calculateXpFromRawStats_scalesWithDistance() {
            long xpNear = calculator.calculateXpFromRawStats(100, 0, RPGMobClass.HOSTILE);
            long xpFar = calculator.calculateXpFromRawStats(100, 2000, RPGMobClass.HOSTILE);

            assertTrue(xpFar > xpNear,
                "XP should increase with distance");
        }

        @Test
        @DisplayName("calculateXpFromRawStats scales with health")
        void calculateXpFromRawStats_scalesWithHealth() {
            long xpLowHealth = calculator.calculateXpFromRawStats(50, 500, RPGMobClass.HOSTILE);
            long xpHighHealth = calculator.calculateXpFromRawStats(500, 500, RPGMobClass.HOSTILE);

            assertTrue(xpHighHealth > xpLowHealth,
                "XP should increase with health");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Edge Cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Level 0 returns at least 1 XP")
        void level0_returnsAtLeast1Xp() {
            long xp = calculator.calculateXp(0, 0, 0, RPGMobClass.HOSTILE);

            assertTrue(xp >= 1);
        }

        @Test
        @DisplayName("Negative level returns at least 1 XP")
        void negativeLevel_returnsAtLeast1Xp() {
            long xp = calculator.calculateXp(-10, 0, 0, RPGMobClass.HOSTILE);

            assertTrue(xp >= 1);
        }

        @Test
        @DisplayName("Very high level doesn't overflow")
        void veryHighLevel_doesNotOverflow() {
            long xp = calculator.calculateXp(100000, 100000, 1000, RPGMobClass.BOSS);

            assertTrue(xp > 0);
            assertFalse(Long.MAX_VALUE == xp, "Should not overflow to MAX_VALUE");
        }

        @Test
        @DisplayName("Negative stat pool is handled")
        void negativeStatPool_isHandled() {
            long xp = calculator.calculateXp(10, -100, 0, RPGMobClass.HOSTILE);

            // Might reduce XP but should still work
            assertTrue(xp >= 1);
        }

        @Test
        @DisplayName("Null classification handled by config")
        void nullClassification_handledByConfig() {
            // MobClassificationConfig should handle null gracefully
            // This tests the integration
            assertDoesNotThrow(() -> {
                calculator.calculateXp(10, 100, 0, null);
            });
        }

        @Test
        @DisplayName("Very large distance level")
        void veryLargeDistanceLevel() {
            long xp = calculator.calculateXp(50, 500, 10000, RPGMobClass.HOSTILE);

            assertTrue(xp > 0);
            // Distance multiplier would be 1 + 10000 * 0.028 = 281x
            // This is a very large multiplier
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // XP Formula Verification Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("XP Formula Verification")
    class XpFormulaVerificationTests {

        @Test
        @DisplayName("Formula produces expected XP for known inputs")
        void formula_producesExpectedXp() {
            // Level 10, pool 100, distance 0, HOSTILE
            // baseXp = 10 * 5.0 = 50
            // poolBonus = 100 * 0.1 = 10
            // tierMult = 1.0 (HOSTILE default)
            // globalMult = 1.2
            // distMult = 1.0 + (0 * 0.028) = 1.0
            // final = (50 + 10) * 1.0 * 1.2 * 1.0 = 72

            long xp = calculator.calculateXp(10, 100, 0, RPGMobClass.HOSTILE);

            assertEquals(72, xp, "XP should match formula calculation");
        }

        @Test
        @DisplayName("Pool bonus contributes to XP")
        void poolBonus_contributesToXp() {
            long xpNoPool = calculator.calculateXp(10, 0, 0, RPGMobClass.HOSTILE);
            long xpWithPool = calculator.calculateXp(10, 500, 0, RPGMobClass.HOSTILE);

            // poolBonus = 500 * 0.1 = 50
            // Additional XP from pool = 50
            assertTrue(xpWithPool > xpNoPool);
        }
    }
}
