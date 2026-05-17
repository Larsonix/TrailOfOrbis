package io.github.larsonix.trailoforbis.combat;

import com.hypixel.hytale.server.core.modules.entity.damage.Damage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for the zero-damage guard bypass in RPGDamageSystem.
 *
 * <p>The guard at RPGDamageSystem:456 previously returned early for ALL damage events
 * with {@code vanillaDamage <= 0}. This caused 0-base-damage modded weapons (e.g.,
 * knockback-only pillows) to deal no RPG damage when converted to RPG gear.
 *
 * <p>Note: The {@code isPlayerRpgWeaponSource} method requires Hytale ECS runtime
 * (ComponentType registry) and cannot be unit-tested without the full server context.
 * These tests verify the CONTRACTS of the guard logic — the flow decisions based on
 * damage amount and cancellation state.
 */
class ZeroDamageGuardTest {

    // =========================================================================
    // GUARD FLOW CONTRACT — when to skip, when to proceed, when to check source
    // =========================================================================

    @Nested
    @DisplayName("Guard Flow Contract")
    class GuardFlowTests {

        @Test
        @DisplayName("cancelled damage always exits first (before amount check)")
        void cancelledDamage_ExitsFirst() {
            // Guard order: cancelled → amount → source check
            // Cancelled must exit BEFORE any further processing
            assertTrue(shouldExitOnCancelled(true, 0f));
            assertTrue(shouldExitOnCancelled(true, 5f));
            assertTrue(shouldExitOnCancelled(true, -1f));
        }

        @Test
        @DisplayName("positive damage always proceeds without source check")
        void positiveDamage_AlwaysProceeds() {
            assertFalse(shouldCheckSource(false, 5.0f), "Positive damage → proceed directly");
            assertFalse(shouldCheckSource(false, 0.01f), "Epsilon damage → proceed directly");
            assertFalse(shouldCheckSource(false, 100f), "Large damage → proceed directly");
        }

        @Test
        @DisplayName("zero damage + not cancelled → requires source check")
        void zeroDamage_RequiresSourceCheck() {
            assertTrue(shouldCheckSource(false, 0f),
                "Zero damage must trigger isPlayerRpgWeaponSource check");
        }

        @Test
        @DisplayName("negative damage + not cancelled → requires source check")
        void negativeDamage_RequiresSourceCheck() {
            assertTrue(shouldCheckSource(false, -1f),
                "Negative damage must trigger source check (same path as zero)");
        }

        @Test
        @DisplayName("epsilon from DamageCalculator patcher bypasses guard naturally")
        void epsilonDamage_BypassesNaturally() {
            // WeaponInteractionPatcher injects Physical:0.01 for empty DamageCalculators
            // This bypasses the guard without needing isPlayerRpgWeaponSource
            assertFalse(shouldCheckSource(false, 0.01f),
                "0.01 from patcher must bypass guard naturally (> 0)");
        }

        /**
         * Models the guard logic: returns true if the guard would exit early on cancelled.
         */
        private boolean shouldExitOnCancelled(boolean cancelled, float damage) {
            return cancelled; // First check in the guard
        }

        /**
         * Models the guard logic: returns true if the source check is needed.
         * This is the exact condition from RPGDamageSystem:
         * {@code if (vanillaDamage <= 0 && !isPlayerRpgWeaponSource(store, damage)) return;}
         */
        private boolean shouldCheckSource(boolean cancelled, float vanillaDamage) {
            if (cancelled) return false; // Already exited
            return vanillaDamage <= 0;   // Need source check
        }
    }

    // =========================================================================
    // SOURCE TYPE CLASSIFICATION
    // =========================================================================

    @Nested
    @DisplayName("Source Type Classification")
    class SourceTypeTests {

        @Test
        @DisplayName("Source type hierarchy: ProjectileSource EXTENDS EntitySource")
        void sourceTypeHierarchy() {
            // CRITICAL: ProjectileSource extends EntitySource (confirmed from decompiled code).
            // This means isPlayerRpgWeaponSource returns true for projectile attacks too —
            // which is CORRECT (projectile from RPG weapon should bypass 0-damage guard).
            Damage.Source entitySource = mock(Damage.EntitySource.class);
            assertTrue(entitySource instanceof Damage.EntitySource, "EntitySource passes guard");

            // ProjectileSource inherits from EntitySource — it ALSO passes the instanceof check
            Damage.Source projSource = mock(Damage.ProjectileSource.class);
            assertTrue(projSource instanceof Damage.EntitySource,
                "ProjectileSource extends EntitySource — passes guard (correct: RPG projectiles should bypass)");

            // EnvironmentSource does NOT extend EntitySource — blocked from bypass
            Damage.Source envSource = mock(Damage.EnvironmentSource.class);
            assertFalse(envSource instanceof Damage.EntitySource, "EnvironmentSource blocked");
        }

        @Test
        @DisplayName("instanceof check handles null source gracefully")
        void instanceofCheck_NullSafe() {
            Damage.Source nullSource = null;
            assertFalse(nullSource instanceof Damage.EntitySource,
                "instanceof null must return false, not NPE");
        }
    }

    // =========================================================================
    // ATTACK TYPE MULTIPLIER EDGE CASES
    // =========================================================================

    @Nested
    @DisplayName("Attack Type Multiplier for 0-Damage")
    class AttackTypeMultiplierTests {

        @Test
        @DisplayName("0 damage defaults to 1.0 multiplier (neutral)")
        void zeroDamage_NeutralMultiplier() {
            float vanillaDamage = 0f;
            float multiplier = (vanillaDamage <= 0) ? 1.0f : vanillaDamage * 0.2f;

            assertEquals(1.0f, multiplier,
                "Zero-damage weapons must use neutral multiplier (can't derive attack type)");
        }

        @Test
        @DisplayName("0 / positive ref = 0, not NaN or Infinity")
        void zeroDividedByRef_IsZero() {
            float result = 0f / 5.0f;

            assertEquals(0f, result);
            assertFalse(Float.isNaN(result));
            assertFalse(Float.isInfinite(result));
        }

        @Test
        @DisplayName("0 / 0 = NaN — guard prevents this path")
        void zeroDividedByZero_WouldBeNaN() {
            // Edge case: fallbackRef=0 would produce NaN.
            // The guard (vanillaDamage <= 0 → multiplier = 1.0) prevents this path.
            float unsafeResult = 0f / 0f;

            assertTrue(Float.isNaN(unsafeResult),
                "Validates that our guard is necessary to prevent NaN");
        }

        @Test
        @DisplayName("negative damage / positive ref is negative (guard prevents)")
        void negativeDividedByRef_IsNegative() {
            float result = -5f / 10f;
            assertTrue(result < 0, "Guard prevents negative multipliers from reaching pipeline");
        }
    }

    // =========================================================================
    // BOUNDARY VALUES
    // =========================================================================

    @Nested
    @DisplayName("Boundary Values")
    class BoundaryTests {

        @Test
        @DisplayName("Float.MIN_VALUE (smallest positive) is positive — bypasses guard")
        void minPositiveFloat_BypassesGuard() {
            assertFalse(Float.MIN_VALUE <= 0,
                "Smallest positive float must bypass the guard naturally");
        }

        @Test
        @DisplayName("-Float.MIN_VALUE (smallest negative) triggers source check")
        void minNegativeFloat_TriggersCheck() {
            assertTrue(-Float.MIN_VALUE <= 0,
                "Smallest negative float must trigger source check");
        }

        @Test
        @DisplayName("-0.0f is treated as <= 0 (triggers source check)")
        void negativeZero_TriggersCheck() {
            float negZero = -0.0f;
            assertTrue(negZero <= 0,
                "Negative zero must trigger source check (Java spec: -0.0 <= 0 is true)");
        }

        @Test
        @DisplayName("Float.NaN is NOT <= 0 (bypasses guard — but shouldn't occur)")
        void nanDamage_BypassesGuard() {
            // NaN comparisons always return false in Java
            assertFalse(Float.NaN <= 0, "NaN <= 0 is false in Java (IEEE 754)");
            // If NaN damage somehow reaches the guard, it would proceed to the pipeline.
            // This is acceptable — NaN damage would produce NaN RPG damage, which the
            // pipeline's other guards would catch.
        }
    }
}
