package io.github.larsonix.trailoforbis.ailments;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ailment detonation mechanics used by the DETONATE_DOT_ON_CRIT
 * octant keystone stat.
 *
 * <p>Tests the DoT damage calculation and detonation behavior via
 * {@link AilmentTracker#getRemainingDotDamage(UUID)} and
 * {@link AilmentTracker#detonateAllDots(UUID)}.
 */
class AilmentDetonationTest {

    private static final UUID TARGET = UUID.randomUUID();
    private static final UUID ATTACKER = UUID.randomUUID();

    private AilmentTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new AilmentTracker();
    }

    @Test
    @DisplayName("getRemainingDotDamage sums Burn and Poison stacks")
    void getRemainingDotDamage_sumsBurnAndPoison() {
        // Burn: DPS=10, duration=5s → remaining = 10 * 5 = 50
        tracker.applyAilment(TARGET, AilmentState.burn(10f, 5f, ATTACKER));

        // Poison stack 1: DPS=3, duration=4s → remaining = 3 * 4 = 12
        tracker.applyAilment(TARGET, AilmentState.poison(3f, 4f, ATTACKER));

        // Poison stack 2: DPS=3, duration=4s → remaining = 3 * 4 = 12
        tracker.applyAilment(TARGET, AilmentState.poison(3f, 4f, ATTACKER));

        // Total = 50 + 12 + 12 = 74
        float totalDot = tracker.getRemainingDotDamage(TARGET);
        assertEquals(74f, totalDot, 0.5f,
            "Total DoT = burn(50) + poison1(12) + poison2(12) = 74");
    }

    @Test
    @DisplayName("detonateAllDots clears Burn and Poison, keeps Freeze and Shock")
    void detonateAllDots_clearsDoTsKeepsNonDoT() {
        // Apply DoT ailments
        tracker.applyAilment(TARGET, AilmentState.burn(10f, 5f, ATTACKER));
        tracker.applyAilment(TARGET, AilmentState.poison(3f, 4f, ATTACKER));
        tracker.applyAilment(TARGET, AilmentState.poison(3f, 4f, ATTACKER));

        // Apply non-DoT ailments
        tracker.applyAilment(TARGET, AilmentState.freeze(20f, 3f, ATTACKER));
        tracker.applyAilment(TARGET, AilmentState.shock(30f, 4f, ATTACKER));

        // Verify all ailments are present
        assertTrue(tracker.hasAilment(TARGET, AilmentType.BURN), "Should have burn before detonate");
        assertTrue(tracker.hasAilment(TARGET, AilmentType.POISON), "Should have poison before detonate");
        assertTrue(tracker.hasAilment(TARGET, AilmentType.FREEZE), "Should have freeze before detonate");
        assertTrue(tracker.hasAilment(TARGET, AilmentType.SHOCK), "Should have shock before detonate");

        // Detonate all DoTs
        tracker.detonateAllDots(TARGET);

        // DoTs should be gone
        assertFalse(tracker.hasAilment(TARGET, AilmentType.BURN), "Burn should be removed after detonate");
        assertFalse(tracker.hasAilment(TARGET, AilmentType.POISON), "Poison should be removed after detonate");

        // Non-DoTs should remain
        assertTrue(tracker.hasAilment(TARGET, AilmentType.FREEZE), "Freeze should survive detonate");
        assertTrue(tracker.hasAilment(TARGET, AilmentType.SHOCK), "Shock should survive detonate");

        // Remaining DoT damage should be 0
        assertEquals(0f, tracker.getRemainingDotDamage(TARGET), 0.001f,
            "No remaining DoT damage after detonation");
    }

    @Test
    @DisplayName("getRemainingDotDamage returns 0 for entity with no ailments")
    void getRemainingDotDamage_noAilments_returnsZero() {
        assertEquals(0f, tracker.getRemainingDotDamage(TARGET), 0.001f,
            "No ailments should return 0 remaining DoT damage");
    }

    @Test
    @DisplayName("getRemainingDotDamage ignores non-DoT ailments (Freeze, Shock)")
    void getRemainingDotDamage_ignoresNonDoTs() {
        tracker.applyAilment(TARGET, AilmentState.freeze(20f, 3f, ATTACKER));
        tracker.applyAilment(TARGET, AilmentState.shock(30f, 4f, ATTACKER));

        assertEquals(0f, tracker.getRemainingDotDamage(TARGET), 0.001f,
            "Freeze and Shock should not contribute to remaining DoT damage");
    }
}
