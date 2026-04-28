package io.github.larsonix.trailoforbis.systems;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for VanillaStatCache - 12 test cases.
 *
 * <p>These tests protect against the bug fixed in commit a849f76 where walk speed
 * fallback was incorrectly set to 1.0 instead of 0.3, causing players to walk
 * at 3.3× the intended speed when not in cache.
 *
 * <p><b>Key Protection:</b> The fallback constants must match Hytale's
 * MovementManager.MASTER_DEFAULT values exactly. Any deviation causes movement bugs.
 *
 * <p><b>Why These Tests Matter:</b> VanillaStatCache is a static utility class that
 * stores "vanilla" (unmodified) player stats. When a player joins, their original
 * movement settings are cached. All stat calculations then use these cached values
 * as the base, preventing cascading multiplier bugs where each recalculation
 * compounds the previous one.
 */
class VanillaStatCacheTest {

    private UUID testPlayer1;
    private UUID testPlayer2;

    @BeforeEach
    void setUp() {
        testPlayer1 = UUID.randomUUID();
        testPlayer2 = UUID.randomUUID();
        VanillaStatCache.clearAll();
    }

    @AfterEach
    void tearDown() {
        VanillaStatCache.clearAll();
    }

    // =========================================================================
    // FALLBACK VALUE TESTS (Regression Protection)
    // =========================================================================

    @Nested
    @DisplayName("Fallback Values (Regression Protection)")
    class FallbackValueTests {

        /**
         * CRITICAL REGRESSION TEST for commit a849f76.
         * Walk speed was incorrectly 1.0 (same as run), making players walk at 3.3× normal speed.
         */
        @Test
        @DisplayName("Walk speed fallback is 0.3 (NOT 1.0)")
        void walkSpeedFallback_Is0Point3_Not1Point0() {
            // When player is not cached, fallback should be used
            assertFalse(VanillaStatCache.hasCached(testPlayer1));

            // Walk speed must be 0.3 (30% of run speed), NOT 1.0
            assertEquals(0.3f, VanillaStatCache.getBaseForwardWalkSpeed(testPlayer1), 0.001f,
                "REGRESSION: Walk speed fallback must be 0.3, not 1.0!");

            assertEquals(0.3f, VanillaStatCache.getBaseBackwardWalkSpeed(testPlayer1), 0.001f,
                "Backward walk speed should also be 0.3");

            assertEquals(0.3f, VanillaStatCache.getBaseStrafeWalkSpeed(testPlayer1), 0.001f,
                "Strafe walk speed should also be 0.3");
        }

        @Test
        @DisplayName("Backward run speed fallback is 0.65")
        void backwardRunSpeedFallback_Is0Point65() {
            assertEquals(0.65f, VanillaStatCache.getBaseBackwardRunSpeed(testPlayer1), 0.001f);
        }

        @Test
        @DisplayName("Strafe run speed fallback is 0.8")
        void strafeRunSpeedFallback_Is0Point8() {
            assertEquals(0.8f, VanillaStatCache.getBaseStrafeRunSpeed(testPlayer1), 0.001f);
        }

        @Test
        @DisplayName("Forward crouch fallback is 0.55")
        void forwardCrouchFallback_Is0Point55() {
            assertEquals(0.55f, VanillaStatCache.getBaseForwardCrouchSpeed(testPlayer1), 0.001f);
        }

        @Test
        @DisplayName("Backward crouch fallback is 0.4")
        void backwardCrouchFallback_Is0Point4() {
            assertEquals(0.4f, VanillaStatCache.getBaseBackwardCrouchSpeed(testPlayer1), 0.001f);
        }

        @Test
        @DisplayName("Strafe crouch fallback is 0.45")
        void strafeCrouchFallback_Is0Point45() {
            assertEquals(0.45f, VanillaStatCache.getBaseStrafeCrouchSpeed(testPlayer1), 0.001f);
        }

        @Test
        @DisplayName("Sprint speed fallback is 1.65")
        void sprintSpeedFallback_Is1Point65() {
            assertEquals(1.65f, VanillaStatCache.getBaseForwardSprintSpeed(testPlayer1), 0.001f);
        }

        @Test
        @DisplayName("Jump force fallback is 11.8")
        void jumpForceFallback_Is11Point8() {
            assertEquals(11.8f, VanillaStatCache.getBaseJumpForce(testPlayer1), 0.001f);
        }

        @Test
        @DisplayName("Run speed fallback is 1.0")
        void runSpeedFallback_Is1Point0() {
            assertEquals(1.0f, VanillaStatCache.getBaseForwardRunSpeed(testPlayer1), 0.001f);
        }

        @Test
        @DisplayName("Climb speed fallback is 0.035")
        void climbSpeedFallback_Is0Point035() {
            assertEquals(0.035f, VanillaStatCache.getBaseClimbSpeed(testPlayer1), 0.001f);
            assertEquals(0.035f, VanillaStatCache.getBaseClimbSpeedLateral(testPlayer1), 0.001f);
        }
    }

    // =========================================================================
    // CACHE BEHAVIOR TESTS
    // =========================================================================

    @Nested
    @DisplayName("Cache Behavior")
    class CacheBehaviorTests {

        @Test
        @DisplayName("hasCached returns false for unknown UUID")
        void hasCached_UnknownUUID_ReturnsFalse() {
            assertFalse(VanillaStatCache.hasCached(testPlayer1));
            assertFalse(VanillaStatCache.hasCached(testPlayer2));
            assertFalse(VanillaStatCache.hasCached(UUID.randomUUID()));
        }

        @Test
        @DisplayName("clear removes player from cache")
        void clear_RemovesPlayerFromCache() {
            // We can't add to cache without Hytale runtime, but we can verify clear doesn't throw
            assertDoesNotThrow(() -> VanillaStatCache.clear(testPlayer1));

            // Verify still returns fallback (not cached)
            assertFalse(VanillaStatCache.hasCached(testPlayer1));
        }

        @Test
        @DisplayName("clearAll empties entire cache")
        void clearAll_EmptiesEntireCache() {
            // Verify clearAll doesn't throw and cache size becomes 0
            assertDoesNotThrow(() -> VanillaStatCache.clearAll());
            assertEquals(0, VanillaStatCache.getCacheSize());
        }

        @Test
        @DisplayName("getCacheSize returns 0 when empty")
        void getCacheSize_WhenEmpty_ReturnsZero() {
            VanillaStatCache.clearAll();
            assertEquals(0, VanillaStatCache.getCacheSize());
        }

        @Test
        @DisplayName("getMovementSettings returns null for uncached player")
        void getMovementSettings_UncachedPlayer_ReturnsNull() {
            assertNull(VanillaStatCache.getMovementSettings(testPlayer1));
        }
    }
}
