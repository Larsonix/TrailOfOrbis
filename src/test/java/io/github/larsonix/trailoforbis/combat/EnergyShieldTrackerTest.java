package io.github.larsonix.trailoforbis.combat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link EnergyShieldTracker}.
 */
class EnergyShieldTrackerTest {

    private static final UUID PLAYER = UUID.randomUUID();
    private EnergyShieldTracker tracker;

    @BeforeEach
    void setUp() {
        // 3-second regen delay
        tracker = new EnergyShieldTracker(3000L);
    }

    @Test
    void absorbDamage_noState_returnsFullDamage() {
        // No shield state initialized
        float remaining = tracker.absorbDamage(PLAYER, 50f);
        assertEquals(50f, remaining, 0.001f);
    }

    @Test
    void absorbDamage_fullShield_absorbsFully() {
        // Initialize shield via tickRegen
        tracker.tickRegen(PLAYER, 100f, 20f, 0.1f);

        // Shield is full (100), damage is 60 → absorb 60, remaining 0
        float remaining = tracker.absorbDamage(PLAYER, 60f);
        assertEquals(0f, remaining, 0.001f);

        // Shield should have 40 left
        var state = tracker.getState(PLAYER);
        assertNotNull(state);
        assertEquals(40f, state.currentShield(), 0.001f);
    }

    @Test
    void absorbDamage_partialShield_absorbsPartially() {
        // Initialize shield via tickRegen
        tracker.tickRegen(PLAYER, 100f, 20f, 0.1f);

        // Shield is full (100), damage is 150 → absorb 100, remaining 50
        float remaining = tracker.absorbDamage(PLAYER, 150f);
        assertEquals(50f, remaining, 0.001f);

        // Shield should be depleted
        var state = tracker.getState(PLAYER);
        assertNotNull(state);
        assertEquals(0f, state.currentShield(), 0.001f);
    }

    @Test
    void tickRegen_initializesToFullShield() {
        tracker.tickRegen(PLAYER, 100f, 20f, 0.1f);

        var state = tracker.getState(PLAYER);
        assertNotNull(state);
        assertEquals(100f, state.currentShield(), 0.001f);
    }

    @Test
    void tickRegen_zeroMaxShield_removesState() {
        // Initialize first
        tracker.tickRegen(PLAYER, 100f, 20f, 0.1f);
        assertNotNull(tracker.getState(PLAYER));

        // Now max shield is 0 (e.g., gear removed)
        tracker.tickRegen(PLAYER, 0f, 0f, 0.1f);
        assertNull(tracker.getState(PLAYER));
    }

    @Test
    void tickRegen_afterHit_respectsDelay() {
        // Initialize shield
        tracker.tickRegen(PLAYER, 100f, 20f, 0.1f);

        // Take damage (depletes shield partially, sets lastHitTime)
        tracker.absorbDamage(PLAYER, 80f);
        var state = tracker.getState(PLAYER);
        assertEquals(20f, state.currentShield(), 0.001f);
        assertTrue(state.lastHitTimeMs() > 0);

        // Immediately try to regen - should NOT regen (within 3s delay)
        tracker.tickRegen(PLAYER, 100f, 20f, 1.0f);
        state = tracker.getState(PLAYER);
        // Should still be 20 (no regen during delay)
        assertEquals(20f, state.currentShield(), 0.001f);
    }

    @Test
    void tickRegen_afterDelay_regenerates() throws Exception {
        // Use a very short delay for testing
        tracker = new EnergyShieldTracker(50L); // 50ms delay

        // Initialize shield
        tracker.tickRegen(PLAYER, 100f, 20f, 0.1f);

        // Take damage
        tracker.absorbDamage(PLAYER, 80f);
        assertEquals(20f, tracker.getState(PLAYER).currentShield(), 0.001f);

        // Wait past delay
        Thread.sleep(100);

        // Now regen should work: 20 + (20 * 1.0) = 40
        tracker.tickRegen(PLAYER, 100f, 20f, 1.0f);
        float shieldAfter = tracker.getState(PLAYER).currentShield();
        assertEquals(40f, shieldAfter, 0.001f);
    }

    @Test
    void tickRegen_capsAtMax() throws Exception {
        tracker = new EnergyShieldTracker(0L); // No delay

        // Initialize
        tracker.tickRegen(PLAYER, 100f, 20f, 0.1f);

        // Take some damage
        tracker.absorbDamage(PLAYER, 10f);
        assertEquals(90f, tracker.getState(PLAYER).currentShield(), 0.001f);

        // Regen with huge dt - should cap at 100
        tracker.tickRegen(PLAYER, 100f, 100f, 10.0f);
        assertEquals(100f, tracker.getState(PLAYER).currentShield(), 0.001f);
    }

    @Test
    void cleanupPlayer_removesState() {
        tracker.tickRegen(PLAYER, 100f, 20f, 0.1f);
        assertNotNull(tracker.getState(PLAYER));

        tracker.cleanupPlayer(PLAYER);
        assertNull(tracker.getState(PLAYER));
    }

    @Test
    void multipleAbsorbs_drainShieldProgressively() {
        tracker.tickRegen(PLAYER, 100f, 20f, 0.1f);

        // Three hits: 30 + 30 + 30 = 90 absorbed, shield left = 10
        assertEquals(0f, tracker.absorbDamage(PLAYER, 30f), 0.001f);
        assertEquals(0f, tracker.absorbDamage(PLAYER, 30f), 0.001f);
        assertEquals(0f, tracker.absorbDamage(PLAYER, 30f), 0.001f);
        assertEquals(10f, tracker.getState(PLAYER).currentShield(), 0.001f);

        // Fourth hit: 20 damage, only 10 shield left → 10 passes through
        assertEquals(10f, tracker.absorbDamage(PLAYER, 20f), 0.001f);
        assertEquals(0f, tracker.getState(PLAYER).currentShield(), 0.001f);
    }

    @Test
    void tickRegen_maxShieldDecreased_capsCurrentToNewMax() throws Exception {
        tracker = new EnergyShieldTracker(0L);

        // Initialize at 100
        tracker.tickRegen(PLAYER, 100f, 20f, 0.1f);
        assertEquals(100f, tracker.getState(PLAYER).currentShield(), 0.001f);

        // Max decreased to 50 (e.g., gear removed)
        tracker.tickRegen(PLAYER, 50f, 10f, 0.1f);
        // Should be capped to 50
        assertEquals(50f, tracker.getState(PLAYER).currentShield(), 0.001f);
    }

    // ==================== addShield Tests (SHIELD_REGEN_ON_DOT) ====================

    @Test
    void addShield_increasesShield() {
        // Initialize shield at 100 max
        tracker.tickRegen(PLAYER, 100f, 20f, 0.1f);
        assertEquals(100f, tracker.getState(PLAYER).currentShield(), 0.001f);

        // Take some damage to bring shield down
        tracker.absorbDamage(PLAYER, 60f);
        assertEquals(40f, tracker.getState(PLAYER).currentShield(), 0.001f);

        // Add 30 shield via addShield (used by SHIELD_REGEN_ON_DOT)
        tracker.addShield(PLAYER, 30f, 100f);
        assertEquals(70f, tracker.getState(PLAYER).currentShield(), 0.001f);
    }

    @Test
    void addShield_capsAtMax() {
        // Initialize full shield at 100 max
        tracker.tickRegen(PLAYER, 100f, 20f, 0.1f);
        assertEquals(100f, tracker.getState(PLAYER).currentShield(), 0.001f);

        // Try to add 50 more — should stay capped at 100
        tracker.addShield(PLAYER, 50f, 100f);
        assertEquals(100f, tracker.getState(PLAYER).currentShield(), 0.001f);
    }
}
