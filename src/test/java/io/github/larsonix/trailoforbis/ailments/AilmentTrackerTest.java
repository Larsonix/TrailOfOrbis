package io.github.larsonix.trailoforbis.ailments;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AilmentTracker}.
 */
@DisplayName("AilmentTracker")
class AilmentTrackerTest {

    private AilmentTracker tracker;
    private UUID targetUuid;
    private UUID attackerUuid;

    @BeforeEach
    void setUp() {
        tracker = new AilmentTracker();
        targetUuid = UUID.randomUUID();
        attackerUuid = UUID.randomUUID();
    }

    // ==================== Basic Operations ====================

    @Test
    @DisplayName("New tracker is empty")
    void newTrackerIsEmpty() {
        assertEquals(0, tracker.getTrackedEntityCount());
    }

    @Test
    @DisplayName("getOrCreate creates new state for unknown UUID")
    void getOrCreateCreatesNewState() {
        EntityAilmentState state = tracker.getOrCreate(targetUuid);

        assertNotNull(state);
        assertFalse(state.hasAnyAilment());
    }

    @Test
    @DisplayName("getOrCreate returns same instance for same UUID")
    void getOrCreateReturnsSameInstance() {
        EntityAilmentState first = tracker.getOrCreate(targetUuid);
        EntityAilmentState second = tracker.getOrCreate(targetUuid);

        assertSame(first, second);
    }

    @Test
    @DisplayName("get returns null for unknown UUID")
    void getReturnsNullForUnknown() {
        assertNull(tracker.get(UUID.randomUUID()));
    }

    @Test
    @DisplayName("get returns state after getOrCreate")
    void getReturnsStateAfterCreate() {
        tracker.getOrCreate(targetUuid);

        assertNotNull(tracker.get(targetUuid));
    }

    @Test
    @DisplayName("cleanup removes entity state")
    void cleanupRemovesState() {
        tracker.getOrCreate(targetUuid);
        assertEquals(1, tracker.getTrackedEntityCount());

        tracker.cleanup(targetUuid);
        assertEquals(0, tracker.getTrackedEntityCount());
        assertNull(tracker.get(targetUuid));
    }

    @Test
    @DisplayName("clearAll removes all states")
    void clearAllRemovesAllStates() {
        tracker.getOrCreate(UUID.randomUUID());
        tracker.getOrCreate(UUID.randomUUID());
        tracker.getOrCreate(UUID.randomUUID());
        assertEquals(3, tracker.getTrackedEntityCount());

        tracker.clearAll();
        assertEquals(0, tracker.getTrackedEntityCount());
    }

    // ==================== Ailment Application ====================

    @Test
    @DisplayName("applyAilment adds ailment to entity")
    void applyAilmentAddsToEntity() {
        AilmentState burn = AilmentState.burn(10.0f, 4.0f, attackerUuid);

        assertTrue(tracker.applyAilment(targetUuid, burn));
        assertTrue(tracker.hasAilment(targetUuid, AilmentType.BURN));
    }

    @Test
    @DisplayName("hasAnyAilment returns true after applying ailment")
    void hasAnyAilmentAfterApply() {
        assertFalse(tracker.hasAnyAilment(targetUuid));

        tracker.applyAilment(targetUuid, AilmentState.burn(10.0f, 4.0f, attackerUuid));

        assertTrue(tracker.hasAnyAilment(targetUuid));
    }

    @Test
    @DisplayName("Multiple ailment types can coexist")
    void multipleAilmentsCanCoexist() {
        tracker.applyAilment(targetUuid, AilmentState.burn(10.0f, 4.0f, attackerUuid));
        tracker.applyAilment(targetUuid, AilmentState.freeze(25.0f, 3.0f, attackerUuid));
        tracker.applyAilment(targetUuid, AilmentState.shock(30.0f, 2.0f, attackerUuid));

        assertTrue(tracker.hasAilment(targetUuid, AilmentType.BURN));
        assertTrue(tracker.hasAilment(targetUuid, AilmentType.FREEZE));
        assertTrue(tracker.hasAilment(targetUuid, AilmentType.SHOCK));
    }

    // ==================== Freeze (COLD) ====================

    @Test
    @DisplayName("getFreezeSlowPercent returns 0 when not frozen")
    void freezeSlowZeroWhenNotFrozen() {
        assertEquals(0f, tracker.getFreezeSlowPercent(targetUuid));
    }

    @Test
    @DisplayName("getFreezeSlowPercent returns magnitude when frozen")
    void freezeSlowReturnsMagnitude() {
        tracker.applyAilment(targetUuid, AilmentState.freeze(25.0f, 3.0f, attackerUuid));

        assertEquals(25.0f, tracker.getFreezeSlowPercent(targetUuid), 0.001f);
    }

    // ==================== Shock (LIGHTNING) ====================

    @Test
    @DisplayName("getShockDamageIncreasePercent returns 0 when not shocked")
    void shockIncreaseZeroWhenNotShocked() {
        assertEquals(0f, tracker.getShockDamageIncreasePercent(targetUuid));
    }

    @Test
    @DisplayName("getShockDamageIncreasePercent returns magnitude when shocked")
    void shockIncreaseReturnsMagnitude() {
        tracker.applyAilment(targetUuid, AilmentState.shock(35.0f, 2.0f, attackerUuid));

        assertEquals(35.0f, tracker.getShockDamageIncreasePercent(targetUuid), 0.001f);
    }

    // ==================== Poison (CHAOS) Stacking ====================

    @Test
    @DisplayName("getPoisonStackCount returns 0 when not poisoned")
    void poisonStackCountZeroWhenNotPoisoned() {
        assertEquals(0, tracker.getPoisonStackCount(targetUuid));
    }

    @Test
    @DisplayName("Poison stacks multiple times")
    void poisonStacksMultipleTimes() {
        tracker.applyAilment(targetUuid, AilmentState.poison(3.0f, 5.0f, attackerUuid));
        tracker.applyAilment(targetUuid, AilmentState.poison(3.0f, 5.0f, attackerUuid));
        tracker.applyAilment(targetUuid, AilmentState.poison(3.0f, 5.0f, attackerUuid));

        assertEquals(3, tracker.getPoisonStackCount(targetUuid));
    }

    @Test
    @DisplayName("getTotalPoisonDps sums all stacks")
    void totalPoisonDpsSumsAllStacks() {
        // 3 stacks at 3.0 DPS each = 9.0 total
        tracker.applyAilment(targetUuid, AilmentState.poison(3.0f, 5.0f, attackerUuid));
        tracker.applyAilment(targetUuid, AilmentState.poison(3.0f, 5.0f, attackerUuid));
        tracker.applyAilment(targetUuid, AilmentState.poison(3.0f, 5.0f, attackerUuid));

        assertEquals(9.0f, tracker.getTotalPoisonDps(targetUuid), 0.001f);
    }

    // ==================== Remove Ailment ====================

    @Test
    @DisplayName("removeAilment removes specific ailment")
    void removeAilmentRemovesSpecific() {
        tracker.applyAilment(targetUuid, AilmentState.burn(10.0f, 4.0f, attackerUuid));
        tracker.applyAilment(targetUuid, AilmentState.freeze(25.0f, 3.0f, attackerUuid));

        tracker.removeAilment(targetUuid, AilmentType.BURN);

        assertFalse(tracker.hasAilment(targetUuid, AilmentType.BURN));
        assertTrue(tracker.hasAilment(targetUuid, AilmentType.FREEZE));
    }

    @Test
    @DisplayName("removeAilment for poison removes all stacks")
    void removeAilmentRemovesAllPoisonStacks() {
        tracker.applyAilment(targetUuid, AilmentState.poison(3.0f, 5.0f, attackerUuid));
        tracker.applyAilment(targetUuid, AilmentState.poison(3.0f, 5.0f, attackerUuid));
        tracker.applyAilment(targetUuid, AilmentState.poison(3.0f, 5.0f, attackerUuid));

        tracker.removeAilment(targetUuid, AilmentType.POISON);

        assertEquals(0, tracker.getPoisonStackCount(targetUuid));
        assertFalse(tracker.hasAilment(targetUuid, AilmentType.POISON));
    }

    // ==================== Tick Processing ====================

    @Test
    @DisplayName("tickEntity returns 0 for entity without ailments")
    void tickEntityReturnsZeroWithoutAilments() {
        float dotDamage = tracker.tickEntity(targetUuid, 0.25f);

        assertEquals(0f, dotDamage);
    }

    @Test
    @DisplayName("tickEntity returns DoT damage for Burn")
    void tickEntityReturnsBurnDamage() {
        // 10 DPS for 0.25 seconds = 2.5 damage
        tracker.applyAilment(targetUuid, AilmentState.burn(10.0f, 4.0f, attackerUuid));

        float dotDamage = tracker.tickEntity(targetUuid, 0.25f);

        assertEquals(2.5f, dotDamage, 0.001f);
    }

    @Test
    @DisplayName("tickEntity returns DoT damage for Poison stacks")
    void tickEntityReturnsPoisonDamage() {
        // 3 stacks at 3 DPS each = 9 DPS, for 0.25 seconds = 2.25 damage
        tracker.applyAilment(targetUuid, AilmentState.poison(3.0f, 5.0f, attackerUuid));
        tracker.applyAilment(targetUuid, AilmentState.poison(3.0f, 5.0f, attackerUuid));
        tracker.applyAilment(targetUuid, AilmentState.poison(3.0f, 5.0f, attackerUuid));

        float dotDamage = tracker.tickEntity(targetUuid, 0.25f);

        assertEquals(2.25f, dotDamage, 0.001f);
    }

    @Test
    @DisplayName("tickEntity removes expired ailments")
    void tickEntityRemovesExpiredAilments() {
        // Burn with 0.2s duration, tick for 0.25s -> should expire
        tracker.applyAilment(targetUuid, AilmentState.burn(10.0f, 0.2f, attackerUuid));

        tracker.tickEntity(targetUuid, 0.25f);

        assertFalse(tracker.hasAilment(targetUuid, AilmentType.BURN));
    }

    @Test
    @DisplayName("tickEntity does return 0 DoT for debuffs")
    void tickEntityKeepsDebuffs() {
        tracker.applyAilment(targetUuid, AilmentState.freeze(25.0f, 3.0f, attackerUuid));
        tracker.applyAilment(targetUuid, AilmentState.shock(30.0f, 2.0f, attackerUuid));

        float dotDamage = tracker.tickEntity(targetUuid, 0.25f);

        assertEquals(0f, dotDamage); // No DoT damage from debuffs
        // Debuffs should still be active (duration reduced but not expired)
        assertTrue(tracker.hasAilment(targetUuid, AilmentType.FREEZE));
        assertTrue(tracker.hasAilment(targetUuid, AilmentType.SHOCK));
    }

    // ==================== getAllAilments ====================

    @Test
    @DisplayName("getAllAilments returns empty list for unknown UUID")
    void getAllAilmentsEmptyForUnknown() {
        List<AilmentState> ailments = tracker.getAllAilments(UUID.randomUUID());

        assertNotNull(ailments);
        assertTrue(ailments.isEmpty());
    }

    @Test
    @DisplayName("getAllAilments returns all active ailments")
    void getAllAilmentsReturnsAll() {
        tracker.applyAilment(targetUuid, AilmentState.burn(10.0f, 4.0f, attackerUuid));
        tracker.applyAilment(targetUuid, AilmentState.freeze(25.0f, 3.0f, attackerUuid));
        tracker.applyAilment(targetUuid, AilmentState.poison(3.0f, 5.0f, attackerUuid));
        tracker.applyAilment(targetUuid, AilmentState.poison(3.0f, 5.0f, attackerUuid));

        List<AilmentState> ailments = tracker.getAllAilments(targetUuid);

        // 1 Burn + 1 Freeze + 2 Poison stacks = 4 total
        assertEquals(4, ailments.size());
    }
}
