package io.github.larsonix.trailoforbis.combat.tracking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ConsecutiveHitTracker}.
 *
 * <p>Tests the consecutive hit counting and damage multiplier calculation
 * used by the CONSECUTIVE_HIT_BONUS octant keystone stat.
 */
class ConsecutiveHitTrackerTest {

    private static final UUID ATTACKER = UUID.randomUUID();

    private ConsecutiveHitTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new ConsecutiveHitTracker();
    }

    @Test
    @DisplayName("recordHit increments count on successive calls")
    void recordHit_incrementsCount() {
        assertEquals(1, tracker.recordHit(ATTACKER), "First hit should be count 1");
        assertEquals(2, tracker.recordHit(ATTACKER), "Second hit should be count 2");
        assertEquals(3, tracker.recordHit(ATTACKER), "Third hit should be count 3");
    }

    @Test
    @DisplayName("recordHit resets count after 2s window expires")
    void recordHit_resetsAfterTimeout() throws Exception {
        // Record initial hits
        tracker.recordHit(ATTACKER);
        tracker.recordHit(ATTACKER);
        assertEquals(2, tracker.getCount(ATTACKER), "Should have 2 consecutive hits");

        // Wait for the 2-second window to expire
        // The window is 2_000_000_000 nanos = 2 seconds
        Thread.sleep(2100);

        // Next hit should reset to 1 (window expired)
        assertEquals(1, tracker.recordHit(ATTACKER),
            "Hit after timeout should reset count to 1");
    }

    @Test
    @DisplayName("getMultiplier calculates correctly: 1.0 + count * bonus/100")
    void getMultiplier_calculatesCorrectly() {
        // Record 3 hits
        tracker.recordHit(ATTACKER);
        tracker.recordHit(ATTACKER);
        tracker.recordHit(ATTACKER);

        // bonusPerHit=5 → 1.0 + 3 * 5/100 = 1.15
        float multiplier = tracker.getMultiplier(ATTACKER, 5f);
        assertEquals(1.15f, multiplier, 0.001f,
            "3 hits with 5% bonus each = 1.15 multiplier");
    }

    @Test
    @DisplayName("getMultiplier returns 1.0 with zero bonus")
    void getMultiplier_zeroBonusReturnsOne() {
        tracker.recordHit(ATTACKER);
        tracker.recordHit(ATTACKER);

        assertEquals(1.0f, tracker.getMultiplier(ATTACKER, 0f), 0.001f,
            "Zero bonus should return 1.0 multiplier");
    }

    @Test
    @DisplayName("cleanup removes all state for player")
    void cleanup_removesState() {
        tracker.recordHit(ATTACKER);
        tracker.recordHit(ATTACKER);
        assertEquals(2, tracker.getCount(ATTACKER));

        tracker.cleanup(ATTACKER);

        assertEquals(0, tracker.getCount(ATTACKER),
            "Count should be 0 after cleanup");
        assertEquals(1.0f, tracker.getMultiplier(ATTACKER, 10f), 0.001f,
            "Multiplier should be 1.0 after cleanup");
    }

    @Test
    @DisplayName("Different attackers are tracked independently")
    void differentAttackers_trackedIndependently() {
        UUID attacker2 = UUID.randomUUID();

        tracker.recordHit(ATTACKER);
        tracker.recordHit(ATTACKER);
        tracker.recordHit(ATTACKER);

        tracker.recordHit(attacker2);

        assertEquals(3, tracker.getCount(ATTACKER));
        assertEquals(1, tracker.getCount(attacker2));
    }
}
