package io.github.larsonix.trailoforbis.mobs.calculator;

import com.hypixel.hytale.math.vector.Vector3d;
import io.github.larsonix.trailoforbis.mobs.MobScalingConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link DistanceBonusCalculator}.
 *
 * <p>Tests the distance-based bonus stat pool calculation for mob scaling.
 * Uses linear scaling with safe zone and transition zone handling.
 */
@DisplayName("DistanceBonusCalculator")
class DistanceBonusCalculatorTest {

    private MobScalingConfig config;
    private DistanceBonusCalculator calculator;

    @BeforeEach
    void setUp() {
        config = new MobScalingConfig();
        // Default config:
        // - Safe zone radius: 200 blocks
        // - Transition end: 200 blocks
        // - Scaling start: 200 blocks
        // - Pool per block: 0.3
        // - Max bonus pool: 0 (unlimited)
        calculator = new DistanceBonusCalculator(config);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Safe Zone Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Safe Zone (0-200 blocks)")
    class SafeZoneTests {

        @Test
        @DisplayName("Distance 0 returns zero bonus")
        void distanceZero_returnsZeroBonus() {
            double bonus = calculator.calculateBonusPool(0);
            assertEquals(0.0, bonus, 0.001);
        }

        @Test
        @DisplayName("Distance at safe zone edge (200) returns zero bonus")
        void distanceAtSafeZoneEdge_returnsZeroBonus() {
            double bonus = calculator.calculateBonusPool(200);
            assertEquals(0.0, bonus, 0.001);
        }

        @Test
        @DisplayName("Distance inside safe zone returns zero bonus")
        void distanceInsideSafeZone_returnsZeroBonus() {
            double bonus = calculator.calculateBonusPool(100);
            assertEquals(0.0, bonus, 0.001);
        }

        @Test
        @DisplayName("isInSafeZone returns true for distance within safe zone")
        void isInSafeZone_withinSafeZone_returnsTrue() {
            assertTrue(calculator.isInSafeZone(0));
            assertTrue(calculator.isInSafeZone(100));
            assertTrue(calculator.isInSafeZone(200));
        }

        @Test
        @DisplayName("isInSafeZone returns false for distance outside safe zone")
        void isInSafeZone_outsideSafeZone_returnsFalse() {
            assertFalse(calculator.isInSafeZone(201));
            assertFalse(calculator.isInSafeZone(300));
            assertFalse(calculator.isInSafeZone(1000));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Transition Zone Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Transition Zone (200-200 blocks, empty with default config)")
    class TransitionZoneTests {

        @Test
        @DisplayName("Distance just past safe zone returns bonus (in scaling zone)")
        void distanceJustPastSafeZone_returnsBonusInScalingZone() {
            // With radius=200 and transitionEnd=200, there's no transition zone
            // Distance 201 is already in scaling zone: (201 - 200) * 0.3 = 0.3
            double bonus = calculator.calculateBonusPool(201);
            assertEquals(0.3, bonus, 0.001);
        }

        @Test
        @DisplayName("isInTransitionZone returns false with default config (no transition zone)")
        void isInTransitionZone_defaultConfig_returnsFalse() {
            // With radius=200 and transitionEnd=200, there's no transition zone
            assertFalse(calculator.isInTransitionZone(100)); // In safe zone
            assertFalse(calculator.isInTransitionZone(200)); // At boundary
            assertFalse(calculator.isInTransitionZone(201)); // In scaling zone
        }

        @Test
        @DisplayName("isInTransitionZone works with custom config")
        void isInTransitionZone_customConfig_works() {
            // Set up a transition zone: radius=100, transitionEnd=200
            config.getSafeZone().setRadius(100);
            config.getSafeZone().setTransitionEnd(200);
            config.getDistanceScaling().setScalingStart(200);

            assertTrue(calculator.isInTransitionZone(150));
            assertFalse(calculator.isInTransitionZone(100)); // Still in safe zone
            assertFalse(calculator.isInTransitionZone(200)); // At scaling start
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Scaling Zone Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Scaling Zone (200+ blocks)")
    class ScalingZoneTests {

        @Test
        @DisplayName("Distance at scaling start (200) returns zero bonus")
        void distanceAtScalingStart_returnsZeroBonus() {
            // At exactly scaling start, effective distance is 0
            double bonus = calculator.calculateBonusPool(200);
            assertEquals(0.0, bonus, 0.001);
        }

        @Test
        @DisplayName("Distance 300 blocks returns correct bonus (30 pool)")
        void distance300_returnsCorrectBonus() {
            // (300 - 200) * 0.3 = 30
            double bonus = calculator.calculateBonusPool(300);
            assertEquals(30.0, bonus, 0.001);
        }

        @Test
        @DisplayName("Distance 1000 blocks returns correct bonus (240 pool)")
        void distance1000_returnsCorrectBonus() {
            // (1000 - 200) * 0.3 = 240
            double bonus = calculator.calculateBonusPool(1000);
            assertEquals(240.0, bonus, 0.001);
        }

        @Test
        @DisplayName("Distance 3000 blocks returns correct bonus (840 pool)")
        void distance3000_returnsCorrectBonus() {
            // (3000 - 200) * 0.3 = 840
            double bonus = calculator.calculateBonusPool(3000);
            assertEquals(840.0, bonus, 0.001);
        }

        @Test
        @DisplayName("Distance 5000 blocks returns correct bonus (1440 pool)")
        void distance5000_returnsCorrectBonus() {
            // (5000 - 200) * 0.3 = 1440
            double bonus = calculator.calculateBonusPool(5000);
            assertEquals(1440.0, bonus, 0.001);
        }

        @Test
        @DisplayName("Distance 10000 blocks returns correct bonus (2940 pool)")
        void distance10000_returnsCorrectBonus() {
            // (10000 - 200) * 0.3 = 2940
            double bonus = calculator.calculateBonusPool(10000);
            assertEquals(2940.0, bonus, 0.001);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Max Bonus Pool Cap Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Max Bonus Pool Cap")
    class MaxBonusPoolCapTests {

        @Test
        @DisplayName("No cap when maxBonusPool is 0 (default)")
        void noCap_whenMaxBonusPoolIsZero() {
            // Default config has maxBonusPool = 0 (unlimited)
            double bonus = calculator.calculateBonusPool(100000);
            assertEquals(29940.0, bonus, 0.001); // (100000 - 200) * 0.3
        }

        @Test
        @DisplayName("Caps at maxBonusPool when set")
        void capsAtMaxBonusPool_whenSet() {
            // Set max pool to 1000
            config.getDistanceScaling().setMaxBonusPool(1000);

            // At 5000 blocks, bonus would be 1440 but capped at 1000
            double bonus = calculator.calculateBonusPool(5000);
            assertEquals(1000.0, bonus, 0.001);
        }

        @Test
        @DisplayName("Returns uncapped value when below maxBonusPool")
        void returnsUncappedValue_whenBelowMaxBonusPool() {
            config.getDistanceScaling().setMaxBonusPool(1000);

            // At 500 blocks, bonus is 90 which is below cap
            double bonus = calculator.calculateBonusPool(500);
            assertEquals(90.0, bonus, 0.001); // (500 - 200) * 0.3
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Level Estimation Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Level Estimation from Distance")
    class LevelEstimationTests {

        @Test
        @DisplayName("Distance 0 returns level 1")
        void distanceZero_returnsLevel1() {
            int level = calculator.estimateLevelFromDistance(0);
            assertEquals(1, level);
        }

        @Test
        @DisplayName("Distance at scaling start returns level 1")
        void distanceAtScalingStart_returnsLevel1() {
            int level = calculator.estimateLevelFromDistance(200);
            assertEquals(1, level);
        }

        @Test
        @DisplayName("Distance 275 blocks returns level 2")
        void distance275_returnsLevel2() {
            // 1 + (275 - 200) / 75 = 1 + 1 = 2
            int level = calculator.estimateLevelFromDistance(275);
            assertEquals(2, level);
        }

        @Test
        @DisplayName("Distance 1000 blocks returns level 11")
        void distance1000_returnsLevel11() {
            // 1 + (1000 - 200) / 75 = 1 + 10.67 = 11
            int level = calculator.estimateLevelFromDistance(1000);
            assertEquals(11, level);
        }

        @Test
        @DisplayName("Distance 3000 blocks returns level 38")
        void distance3000_returnsLevel38() {
            // 1 + (3000 - 200) / 75 = 1 + 37.33 = 38
            int level = calculator.estimateLevelFromDistance(3000);
            assertEquals(38, level);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Zone Name Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Zone Name Detection")
    class ZoneNameTests {

        @Test
        @DisplayName("Returns 'Safe' for distance in safe zone")
        void returnsSafe_forSafeZone() {
            assertEquals("Safe", calculator.getZoneName(0));
            assertEquals("Safe", calculator.getZoneName(100));
            assertEquals("Safe", calculator.getZoneName(200));
        }

        @Test
        @DisplayName("Returns 'Scaling' for distance in scaling zone (no transition with default config)")
        void returnsScaling_forScalingZone() {
            // With radius=200 and transitionEnd=200, there's no transition zone
            assertEquals("Scaling", calculator.getZoneName(201));
            assertEquals("Scaling", calculator.getZoneName(500));
            assertEquals("Scaling", calculator.getZoneName(10000));
        }

        @Test
        @DisplayName("Returns 'Transition' with custom config that has a transition zone")
        void returnsTransition_withCustomConfig() {
            // Set up a transition zone: radius=100, transitionEnd=200
            config.getSafeZone().setRadius(100);
            config.getSafeZone().setTransitionEnd(200);
            config.getDistanceScaling().setScalingStart(200);

            assertEquals("Transition", calculator.getZoneName(150));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Static Helper Methods Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Static Distance Calculation")
    class StaticDistanceCalculationTests {

        @Test
        @DisplayName("calculateDistanceFromOrigin with (0, 0) returns 0")
        void calculateDistanceFromOrigin_origin_returnsZero() {
            double distance = DistanceBonusCalculator.calculateDistanceFromOrigin(0, 0);
            assertEquals(0.0, distance, 0.001);
        }

        @Test
        @DisplayName("calculateDistanceFromOrigin with (3, 4) returns 5")
        void calculateDistanceFromOrigin_3_4_returns5() {
            // 3-4-5 triangle
            double distance = DistanceBonusCalculator.calculateDistanceFromOrigin(3, 4);
            assertEquals(5.0, distance, 0.001);
        }

        @Test
        @DisplayName("calculateDistanceFromOrigin with (100, 0) returns 100")
        void calculateDistanceFromOrigin_100_0_returns100() {
            double distance = DistanceBonusCalculator.calculateDistanceFromOrigin(100, 0);
            assertEquals(100.0, distance, 0.001);
        }

        @Test
        @DisplayName("calculateDistanceFromOrigin with negative coordinates works correctly")
        void calculateDistanceFromOrigin_negativeCoordinates_worksCorrectly() {
            // (-3, -4) should also have distance 5
            double distance = DistanceBonusCalculator.calculateDistanceFromOrigin(-3, -4);
            assertEquals(5.0, distance, 0.001);
        }

        @Test
        @DisplayName("calculateDistanceSquaredFromOrigin avoids sqrt")
        void calculateDistanceSquaredFromOrigin_avoidsSqrt() {
            // (3, 4) -> 9 + 16 = 25
            double distSquared = DistanceBonusCalculator.calculateDistanceSquaredFromOrigin(3, 4);
            assertEquals(25.0, distSquared, 0.001);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Custom Config Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Custom Configuration")
    class CustomConfigTests {

        @Test
        @DisplayName("Custom safe zone radius works correctly")
        void customSafeZoneRadius_worksCorrectly() {
            config.getSafeZone().setRadius(100);
            config.getSafeZone().setTransitionEnd(200);
            config.getDistanceScaling().setScalingStart(200);

            // 150 is now in safe zone transition
            assertTrue(calculator.isInTransitionZone(150));
            assertEquals(0.0, calculator.calculateBonusPool(150), 0.001);

            // 300 is in scaling zone
            // (300 - 200) * 0.3 = 30
            assertEquals(30.0, calculator.calculateBonusPool(300), 0.001);
        }

        @Test
        @DisplayName("Custom pool per block rate works correctly")
        void customPoolPerBlockRate_worksCorrectly() {
            config.getDistanceScaling().setPoolPerBlock(0.5);

            // At 500 blocks: (500 - 200) * 0.5 = 150
            double bonus = calculator.calculateBonusPool(500);
            assertEquals(150.0, bonus, 0.001);
        }

        @Test
        @DisplayName("Custom scaling start moves where bonus begins")
        void customScalingStart_movesBonusStart() {
            config.getSafeZone().setRadius(50);
            config.getSafeZone().setTransitionEnd(100);
            config.getDistanceScaling().setScalingStart(100);
            config.getDistanceScaling().setPoolPerBlock(1.0);

            // At 50 blocks: safe zone, no bonus
            assertEquals(0.0, calculator.calculateBonusPool(50), 0.001);
            // At 75 blocks: transition zone, no bonus
            assertEquals(0.0, calculator.calculateBonusPool(75), 0.001);
            // At 100 blocks: exactly at scaling start, effective = 0
            assertEquals(0.0, calculator.calculateBonusPool(100), 0.001);
            // At 150 blocks: (150 - 100) * 1.0 = 50
            assertEquals(50.0, calculator.calculateBonusPool(150), 0.001);
        }

        @Test
        @DisplayName("Zero pool per block produces no bonus anywhere")
        void zeroPoolPerBlock_producesNoBonus() {
            config.getDistanceScaling().setPoolPerBlock(0.0);

            assertEquals(0.0, calculator.calculateBonusPool(500), 0.001);
            assertEquals(0.0, calculator.calculateBonusPool(10000), 0.001);
        }

        @Test
        @DisplayName("Very small pool per block produces very small bonus")
        void verySmallPoolPerBlock_producesSmallBonus() {
            config.getDistanceScaling().setPoolPerBlock(0.001);

            // At 1200 blocks: (1200 - 200) * 0.001 = 1.0
            assertEquals(1.0, calculator.calculateBonusPool(1200), 0.001);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Negative Distance Handling Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Negative Distance Handling")
    class NegativeDistanceTests {

        @Test
        @DisplayName("Negative distance treated as safe zone (returns zero bonus)")
        void negativeDistance_returnsZeroBonus() {
            // Negative distance is less than safe zone radius, so safe zone applies
            double bonus = calculator.calculateBonusPool(-100);
            assertEquals(0.0, bonus, 0.001);
        }

        @Test
        @DisplayName("Large negative distance still returns zero bonus")
        void largeNegativeDistance_returnsZeroBonus() {
            double bonus = calculator.calculateBonusPool(-5000);
            assertEquals(0.0, bonus, 0.001);
        }

        @Test
        @DisplayName("Negative distance is in safe zone")
        void negativeDistance_isInSafeZone() {
            assertTrue(calculator.isInSafeZone(-50));
        }

        @Test
        @DisplayName("Negative distance estimates level 1")
        void negativeDistance_estimatesLevel1() {
            int level = calculator.estimateLevelFromDistance(-200);
            assertEquals(1, level);
        }

        @Test
        @DisplayName("Negative distance zone name is Safe")
        void negativeDistance_zoneNameIsSafe() {
            assertEquals("Safe", calculator.getZoneName(-100));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Fractional Distance Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Fractional Distance Values")
    class FractionalDistanceTests {

        @Test
        @DisplayName("Fractional distance at boundary produces correct bonus")
        void fractionalDistance_atBoundary_producesCorrectBonus() {
            // 200.5 blocks: (200.5 - 200) * 0.3 = 0.15
            double bonus = calculator.calculateBonusPool(200.5);
            assertEquals(0.15, bonus, 0.001);
        }

        @Test
        @DisplayName("Fractional distance in scaling zone is precise")
        void fractionalDistance_inScalingZone_isPrecise() {
            // 333.33 blocks: (333.33 - 200) * 0.3 = 39.999
            double bonus = calculator.calculateBonusPool(333.33);
            assertEquals(39.999, bonus, 0.001);
        }

        @Test
        @DisplayName("Very small fractional distance past safe zone")
        void verySmallFractional_pastSafeZone() {
            // 200.001 blocks: (200.001 - 200) * 0.3 = 0.0003
            double bonus = calculator.calculateBonusPool(200.001);
            assertEquals(0.0003, bonus, 0.0001);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Static calculateDistanceFromSpawn Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("calculateDistanceFromSpawn with custom origin")
    class DistanceFromSpawnTests {

        @Test
        @DisplayName("Distance from custom origin at same point is zero")
        void distanceFromCustomOrigin_samePoint_isZero() {
            Vector3d origin = new Vector3d(500, 0, 500);
            double distance = DistanceBonusCalculator.calculateDistanceFromSpawn(500, 500, origin);
            assertEquals(0.0, distance, 0.001);
        }

        @Test
        @DisplayName("Distance from custom origin uses XZ only")
        void distanceFromCustomOrigin_usesXZOnly() {
            // Origin at (100, 200, 300), point at (400, 999, 700)
            // dx=300, dz=400 -> sqrt(300^2 + 400^2) = 500 (3-4-5 scaled by 100)
            Vector3d origin = new Vector3d(100, 200, 300);
            double distance = DistanceBonusCalculator.calculateDistanceFromSpawn(400, 700, origin);
            assertEquals(500.0, distance, 0.001);
        }

        @Test
        @DisplayName("Distance from negative origin works correctly")
        void distanceFromNegativeOrigin_worksCorrectly() {
            Vector3d origin = new Vector3d(-100, 0, -100);
            // From (-100, -100) to (200, 300): dx=300, dz=400 -> 500
            double distance = DistanceBonusCalculator.calculateDistanceFromSpawn(200, 300, origin);
            assertEquals(500.0, distance, 0.001);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Level Estimation Edge Cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Level Estimation Edge Cases")
    class LevelEstimationEdgeCaseTests {

        @Test
        @DisplayName("Distance exactly at scaling start + 75 returns level 2")
        void distanceAtScalingStartPlus75_returnsLevel2() {
            // 1 + (275 - 200) / 75 = 1 + 1 = 2
            assertEquals(2, calculator.estimateLevelFromDistance(275));
        }

        @Test
        @DisplayName("Distance just below a level boundary rounds down correctly")
        void distanceJustBelowLevelBoundary_roundsDownCorrectly() {
            // 274 blocks: 1 + (274 - 200) / 75 = 1 + 0.986... = 1 (int cast truncates)
            assertEquals(1, calculator.estimateLevelFromDistance(274));
        }

        @Test
        @DisplayName("Very large distance produces high level")
        void veryLargeDistance_producesHighLevel() {
            // 100000 blocks: 1 + (100000 - 200) / 75 = 1 + 1330 = 1331
            int level = calculator.estimateLevelFromDistance(100000);
            assertEquals(1331, level);
        }

        @Test
        @DisplayName("Custom config scaling start affects level estimation")
        void customScalingStart_affectsLevelEstimation() {
            config.getDistanceScaling().setScalingStart(500);

            // At 500 blocks: within scaling start, level 1
            assertEquals(1, calculator.estimateLevelFromDistance(500));
            // At 575 blocks: 1 + (575 - 500) / 75 = 1 + 1 = 2
            assertEquals(2, calculator.estimateLevelFromDistance(575));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Cache Management Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Cache Management")
    class CacheManagementTests {

        @Test
        @DisplayName("clearCache does not throw")
        void clearCache_doesNotThrow() {
            assertDoesNotThrow(DistanceBonusCalculator::clearCache);
        }

        @Test
        @DisplayName("clearCacheForWorld with null does not throw")
        void clearCacheForWorld_null_doesNotThrow() {
            assertDoesNotThrow(() -> DistanceBonusCalculator.clearCacheForWorld(null));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Bonus Pool Cap Edge Cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Max Bonus Pool Cap Edge Cases")
    class MaxBonusPoolCapEdgeCaseTests {

        @Test
        @DisplayName("Bonus pool exactly at cap returns cap value")
        void bonusPoolExactlyAtCap_returnsCapValue() {
            config.getDistanceScaling().setMaxBonusPool(90.0);

            // At 500 blocks: (500 - 200) * 0.3 = 90.0 exactly
            double bonus = calculator.calculateBonusPool(500);
            assertEquals(90.0, bonus, 0.001);
        }

        @Test
        @DisplayName("Bonus pool just over cap returns cap")
        void bonusPoolJustOverCap_returnsCap() {
            config.getDistanceScaling().setMaxBonusPool(89.0);

            // At 500 blocks: (500 - 200) * 0.3 = 90.0, capped to 89.0
            double bonus = calculator.calculateBonusPool(500);
            assertEquals(89.0, bonus, 0.001);
        }

        @Test
        @DisplayName("Very small cap clips large bonus")
        void verySmallCap_clipsLargeBonus() {
            config.getDistanceScaling().setMaxBonusPool(1.0);

            double bonus = calculator.calculateBonusPool(10000);
            assertEquals(1.0, bonus, 0.001);
        }
    }
}
