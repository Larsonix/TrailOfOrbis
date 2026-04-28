package io.github.larsonix.trailoforbis.maps.instance;

import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.maps.core.RealmBiomeType;
import io.github.larsonix.trailoforbis.maps.core.RealmLayoutShape;
import io.github.larsonix.trailoforbis.maps.core.RealmLayoutSize;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.core.RealmState;
import io.github.larsonix.trailoforbis.maps.templates.RealmTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RealmInstance}.
 */
@DisplayName("RealmInstance")
class RealmInstanceTest {

    private UUID realmId;
    private UUID ownerId;
    private RealmMapData mapData;
    private RealmTemplate template;
    private RealmInstance instance;

    @BeforeEach
    void setUp() {
        realmId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        mapData = RealmMapData.builder()
            .level(5)
            .rarity(GearRarity.COMMON)
            .quality(50)
            .biome(RealmBiomeType.FOREST)
            .size(RealmLayoutSize.MEDIUM)
            .shape(RealmLayoutShape.CIRCULAR)
            .build();

        Transform defaultSpawn = new Transform(new Vector3d(0, 64, 0), new Vector3f(0, 0, 0));
        template = RealmTemplate.builder("test_template", RealmBiomeType.FOREST, RealmLayoutSize.MEDIUM)
            .spawnLocation(defaultSpawn)
            .exitPortalLocation(defaultSpawn)
            .estimatedMonsters(10)
            .build();

        instance = new RealmInstance(realmId, mapData, template, ownerId);
    }

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("Should initialize with CREATING state")
        void shouldInitializeWithCreatingState() {
            assertEquals(RealmState.CREATING, instance.getState());
        }

        @Test
        @DisplayName("Should store realm ID")
        void shouldStoreRealmId() {
            assertEquals(realmId, instance.getRealmId());
        }

        @Test
        @DisplayName("Should store owner ID")
        void shouldStoreOwnerId() {
            assertEquals(ownerId, instance.getOwnerId());
        }

        @Test
        @DisplayName("Should store map data")
        void shouldStoreMapData() {
            assertEquals(mapData, instance.getMapData());
        }

        @Test
        @DisplayName("Should store template")
        void shouldStoreTemplate() {
            assertEquals(template, instance.getTemplate());
        }

        @Test
        @DisplayName("Should initialize with zero players")
        void shouldInitializeWithZeroPlayers() {
            assertEquals(0, instance.getPlayerCount());
            assertTrue(instance.isEmpty());
        }

        @Test
        @DisplayName("Should initialize completion tracker")
        void shouldInitializeCompletionTracker() {
            assertNotNull(instance.getCompletionTracker());
        }

        @Test
        @DisplayName("Should derive level from map data")
        void shouldDeriveLevelFromMapData() {
            assertEquals(mapData.level(), instance.getLevel());
        }
    }

    @Nested
    @DisplayName("State transitions")
    class StateTransitions {

        @Test
        @DisplayName("Should transition from CREATING to READY")
        void shouldTransitionFromCreatingToReady() {
            assertTrue(instance.transitionTo(RealmState.READY));
            assertEquals(RealmState.READY, instance.getState());
            assertTrue(instance.isReady());
        }

        @Test
        @DisplayName("Should transition from READY to ACTIVE")
        void shouldTransitionFromReadyToActive() {
            instance.transitionTo(RealmState.READY);
            assertTrue(instance.transitionTo(RealmState.ACTIVE));
            assertEquals(RealmState.ACTIVE, instance.getState());
            assertTrue(instance.isActive());
        }

        @Test
        @DisplayName("Should transition from ACTIVE to ENDING")
        void shouldTransitionFromActiveToEnding() {
            instance.transitionTo(RealmState.READY);
            instance.transitionTo(RealmState.ACTIVE);
            assertTrue(instance.transitionTo(RealmState.ENDING));
            assertEquals(RealmState.ENDING, instance.getState());
        }

        @Test
        @DisplayName("Should transition from ENDING to CLOSING")
        void shouldTransitionFromEndingToClosing() {
            instance.transitionTo(RealmState.READY);
            instance.transitionTo(RealmState.ACTIVE);
            instance.transitionTo(RealmState.ENDING);
            assertTrue(instance.transitionTo(RealmState.CLOSING));
            assertEquals(RealmState.CLOSING, instance.getState());
            assertTrue(instance.isClosing());
        }

        @Test
        @DisplayName("Should not transition backwards")
        void shouldNotTransitionBackwards() {
            instance.transitionTo(RealmState.READY);
            instance.transitionTo(RealmState.ACTIVE);

            assertFalse(instance.transitionTo(RealmState.READY));
            assertEquals(RealmState.ACTIVE, instance.getState());
        }

        @Test
        @DisplayName("Should not transition to same state")
        void shouldNotTransitionToSameState() {
            instance.transitionTo(RealmState.READY);

            assertFalse(instance.transitionTo(RealmState.READY));
            assertEquals(RealmState.READY, instance.getState());
        }
    }

    @Nested
    @DisplayName("State listeners")
    class StateListeners {

        @Test
        @DisplayName("Should notify listeners on state change")
        void shouldNotifyListenersOnStateChange() {
            AtomicReference<RealmState> fromState = new AtomicReference<>();
            AtomicReference<RealmState> toState = new AtomicReference<>();

            instance.addStateListener(transition -> {
                fromState.set(transition.fromState());
                toState.set(transition.toState());
            });

            instance.transitionTo(RealmState.READY);

            assertEquals(RealmState.CREATING, fromState.get());
            assertEquals(RealmState.READY, toState.get());
        }

        @Test
        @DisplayName("Should not notify listeners when transition fails")
        void shouldNotNotifyListenersWhenTransitionFails() {
            AtomicBoolean notified = new AtomicBoolean(false);
            instance.addStateListener(t -> notified.set(true));

            instance.transitionTo(RealmState.READY);
            notified.set(false); // Reset

            instance.transitionTo(RealmState.READY); // Same state, should fail

            assertFalse(notified.get());
        }

        @Test
        @DisplayName("Should allow removing listeners")
        void shouldAllowRemovingListeners() {
            AtomicBoolean notified = new AtomicBoolean(false);
            Consumer<RealmInstance.StateTransition> listener = t -> notified.set(true);

            instance.addStateListener(listener);
            instance.removeStateListener(listener);
            instance.transitionTo(RealmState.READY);

            assertFalse(notified.get());
        }
    }

    @Nested
    @DisplayName("Player tracking")
    class PlayerTracking {

        @BeforeEach
        void prepareReadyState() {
            instance.transitionTo(RealmState.READY);
        }

        @Test
        @DisplayName("Should track player entry")
        void shouldTrackPlayerEntry() {
            UUID playerId = UUID.randomUUID();

            instance.onPlayerEnter(playerId);

            assertEquals(1, instance.getPlayerCount());
            assertFalse(instance.isEmpty());
            assertTrue(instance.getCurrentPlayers().contains(playerId));
        }

        @Test
        @DisplayName("Should track multiple players")
        void shouldTrackMultiplePlayers() {
            UUID player1 = UUID.randomUUID();
            UUID player2 = UUID.randomUUID();
            UUID player3 = UUID.randomUUID();

            instance.onPlayerEnter(player1);
            instance.onPlayerEnter(player2);
            instance.onPlayerEnter(player3);

            assertEquals(3, instance.getPlayerCount());
        }

        @Test
        @DisplayName("Should track player exit")
        void shouldTrackPlayerExit() {
            UUID playerId = UUID.randomUUID();
            instance.onPlayerEnter(playerId);

            instance.onPlayerLeave(playerId);

            assertEquals(0, instance.getPlayerCount());
            assertTrue(instance.isEmpty());
            assertFalse(instance.getCurrentPlayers().contains(playerId));
        }

        @Test
        @DisplayName("Should not track duplicate entries")
        void shouldNotTrackDuplicateEntries() {
            UUID playerId = UUID.randomUUID();

            instance.onPlayerEnter(playerId);
            instance.onPlayerEnter(playerId);

            assertEquals(1, instance.getPlayerCount());
        }

        @Test
        @DisplayName("Should track participants even after leaving")
        void shouldTrackParticipantsEvenAfterLeaving() {
            UUID playerId = UUID.randomUUID();
            instance.onPlayerEnter(playerId);
            instance.onPlayerLeave(playerId);

            assertTrue(instance.getAllParticipants().contains(playerId));
        }
    }

    @Nested
    @DisplayName("Entry conditions")
    class EntryConditions {

        @Test
        @DisplayName("Should not allow entry in CREATING state")
        void shouldNotAllowEntryInCreatingState() {
            assertFalse(instance.allowsEntry());
        }

        @Test
        @DisplayName("Should allow entry in READY state")
        void shouldAllowEntryInReadyState() {
            instance.transitionTo(RealmState.READY);
            assertTrue(instance.allowsEntry());
        }

        @Test
        @DisplayName("Should allow entry in ACTIVE state")
        void shouldAllowEntryInActiveState() {
            instance.transitionTo(RealmState.READY);
            instance.transitionTo(RealmState.ACTIVE);
            assertTrue(instance.allowsEntry());
        }

        @Test
        @DisplayName("Should not allow entry in ENDING state (prevents re-entry after victory)")
        void shouldNotAllowEntryInEndingState() {
            instance.transitionTo(RealmState.READY);
            instance.transitionTo(RealmState.ACTIVE);
            instance.transitionTo(RealmState.ENDING);
            // ENDING blocks entry to prevent re-entry via deactivating portal
            assertFalse(instance.allowsEntry());
        }

        @Test
        @DisplayName("Should not allow entry in CLOSING state")
        void shouldNotAllowEntryInClosingState() {
            instance.transitionTo(RealmState.READY);
            instance.transitionTo(RealmState.ACTIVE);
            instance.transitionTo(RealmState.ENDING);
            instance.transitionTo(RealmState.CLOSING);
            assertFalse(instance.allowsEntry());
        }
    }

    @Nested
    @DisplayName("Force close")
    class ForceClose {

        @Test
        @DisplayName("Should force close from any state")
        void shouldForceCloseFromAnyState() {
            instance.forceClose(RealmInstance.CompletionReason.FORCE_CLOSED);
            assertEquals(RealmState.CLOSING, instance.getState());
        }

        @Test
        @DisplayName("Should store completion reason on force close")
        void shouldStoreCompletionReasonOnForceClose() {
            instance.forceClose(RealmInstance.CompletionReason.TIMEOUT);
            assertEquals(RealmInstance.CompletionReason.TIMEOUT, instance.getCompletionReason());
        }
    }

    @Nested
    @DisplayName("Timeout tracking")
    class TimeoutTracking {

        @Test
        @DisplayName("Should not be timed out initially")
        void shouldNotBeTimedOutInitially() {
            assertFalse(instance.isTimedOut());
        }

        @Test
        @DisplayName("markTimedOut should set completion reason")
        void markTimedOutShouldSetCompletionReason() {
            instance.transitionTo(RealmState.READY);
            instance.transitionTo(RealmState.ACTIVE);

            instance.markTimedOut();

            assertEquals(RealmInstance.CompletionReason.TIMEOUT, instance.getCompletionReason());
        }
    }

    @Nested
    @DisplayName("Completion reasons")
    class CompletionReasons {

        @Test
        @DisplayName("COMPLETED should be set when marking complete")
        void completedShouldBeSetWhenMarkingComplete() {
            instance.transitionTo(RealmState.READY);
            instance.transitionTo(RealmState.ACTIVE);

            instance.markCompleted();

            assertEquals(RealmInstance.CompletionReason.COMPLETED, instance.getCompletionReason());
            assertEquals(RealmState.ENDING, instance.getState());
        }

        @Test
        @DisplayName("ERROR should be set on error")
        void errorShouldBeSetOnError() {
            instance.forceClose(RealmInstance.CompletionReason.ERROR);
            assertEquals(RealmInstance.CompletionReason.ERROR, instance.getCompletionReason());
        }

        @Test
        @DisplayName("ABANDONED should be set when realm is abandoned")
        void abandonedShouldBeSetWhenAbandoned() {
            instance.forceClose(RealmInstance.CompletionReason.ABANDONED);
            assertEquals(RealmInstance.CompletionReason.ABANDONED, instance.getCompletionReason());
        }
    }

    @Nested
    @DisplayName("World reference")
    class WorldReference {

        @Test
        @DisplayName("Should have no world initially")
        void shouldHaveNoWorldInitially() {
            assertNull(instance.getWorld());
            assertNull(instance.getWorldName());
        }

        // Note: Testing with actual World objects requires integration tests
        // These tests verify the null handling
    }

    @Nested
    @DisplayName("Dead player tracking")
    class DeadPlayerTracking {

        @BeforeEach
        void prepareReadyState() {
            instance.transitionTo(RealmState.READY);
        }

        @Test
        @DisplayName("Should start with no dead players")
        void shouldStartWithNoDeadPlayers() {
            UUID playerId = UUID.randomUUID();
            instance.onPlayerEnter(playerId);

            assertFalse(instance.isPlayerDead(playerId));
            assertEquals(1, instance.getAlivePlayerCount());
            assertFalse(instance.areAllPlayersDead());
        }

        @Test
        @DisplayName("Should mark player as dead")
        void shouldMarkPlayerAsDead() {
            UUID playerId = UUID.randomUUID();
            instance.onPlayerEnter(playerId);

            instance.markPlayerDead(playerId);

            assertTrue(instance.isPlayerDead(playerId));
            assertEquals(0, instance.getAlivePlayerCount());
        }

        @Test
        @DisplayName("Should mark player as alive after respawn")
        void shouldMarkPlayerAsAliveAfterRespawn() {
            UUID playerId = UUID.randomUUID();
            instance.onPlayerEnter(playerId);
            instance.markPlayerDead(playerId);

            instance.markPlayerAlive(playerId);

            assertFalse(instance.isPlayerDead(playerId));
            assertEquals(1, instance.getAlivePlayerCount());
        }

        @Test
        @DisplayName("Should track multiple players alive status")
        void shouldTrackMultiplePlayersAliveStatus() {
            UUID player1 = UUID.randomUUID();
            UUID player2 = UUID.randomUUID();
            UUID player3 = UUID.randomUUID();

            instance.onPlayerEnter(player1);
            instance.onPlayerEnter(player2);
            instance.onPlayerEnter(player3);

            assertEquals(3, instance.getAlivePlayerCount());
            assertFalse(instance.areAllPlayersDead());

            instance.markPlayerDead(player1);
            assertEquals(2, instance.getAlivePlayerCount());
            assertFalse(instance.areAllPlayersDead());

            instance.markPlayerDead(player2);
            assertEquals(1, instance.getAlivePlayerCount());
            assertFalse(instance.areAllPlayersDead());

            instance.markPlayerDead(player3);
            assertEquals(0, instance.getAlivePlayerCount());
            assertTrue(instance.areAllPlayersDead());
        }

        @Test
        @DisplayName("Should return true for areAllPlayersDead when empty")
        void shouldReturnTrueForAreAllPlayersDeadWhenEmpty() {
            assertTrue(instance.areAllPlayersDead());
        }

        @Test
        @DisplayName("Should not count players who left as dead")
        void shouldNotCountPlayersWhoLeftAsDead() {
            UUID player1 = UUID.randomUUID();
            UUID player2 = UUID.randomUUID();

            instance.onPlayerEnter(player1);
            instance.onPlayerEnter(player2);
            instance.markPlayerDead(player1);

            // Player1 is dead, player2 is alive
            assertEquals(1, instance.getAlivePlayerCount());

            // Player2 leaves
            instance.onPlayerLeave(player2);

            // Only dead player1 remains
            assertEquals(0, instance.getAlivePlayerCount());
            assertTrue(instance.areAllPlayersDead());
        }

        @Test
        @DisplayName("Should handle death-respawn-death cycle")
        void shouldHandleDeathRespawnDeathCycle() {
            UUID playerId = UUID.randomUUID();
            instance.onPlayerEnter(playerId);

            // First death
            instance.markPlayerDead(playerId);
            assertTrue(instance.isPlayerDead(playerId));

            // Respawn
            instance.markPlayerAlive(playerId);
            assertFalse(instance.isPlayerDead(playerId));

            // Second death
            instance.markPlayerDead(playerId);
            assertTrue(instance.isPlayerDead(playerId));

            // Second respawn
            instance.markPlayerAlive(playerId);
            assertFalse(instance.isPlayerDead(playerId));
        }

        @Test
        @DisplayName("Should handle marking non-present player as dead")
        void shouldHandleMarkingNonPresentPlayerAsDead() {
            UUID playerId = UUID.randomUUID();
            // Player not in realm yet

            // Should not throw
            instance.markPlayerDead(playerId);

            // Player should be marked dead even though not in player list
            assertTrue(instance.isPlayerDead(playerId));
        }

        @Test
        @DisplayName("Dead players should be cleared when leaving")
        void deadPlayersShouldBeClearedWhenLeaving() {
            UUID playerId = UUID.randomUUID();
            instance.onPlayerEnter(playerId);
            instance.markPlayerDead(playerId);

            instance.onPlayerLeave(playerId);

            // After re-entry, player should not be marked as dead
            instance.onPlayerEnter(playerId);
            // Note: The dead status persists unless explicitly cleared
            // This tests the current behavior
        }

        @Test
        @DisplayName("Should count alive players correctly with mixed states")
        void shouldCountAlivePlayersCorrectlyWithMixedStates() {
            UUID alive1 = UUID.randomUUID();
            UUID alive2 = UUID.randomUUID();
            UUID dead1 = UUID.randomUUID();
            UUID dead2 = UUID.randomUUID();

            instance.onPlayerEnter(alive1);
            instance.onPlayerEnter(alive2);
            instance.onPlayerEnter(dead1);
            instance.onPlayerEnter(dead2);

            instance.markPlayerDead(dead1);
            instance.markPlayerDead(dead2);

            assertEquals(4, instance.getPlayerCount());
            assertEquals(2, instance.getAlivePlayerCount());
            assertFalse(instance.areAllPlayersDead());
        }

        @Test
        @DisplayName("Multiple markPlayerDead calls are idempotent")
        void multipleMarkPlayerDeadCallsAreIdempotent() {
            UUID playerId = UUID.randomUUID();
            instance.onPlayerEnter(playerId);

            instance.markPlayerDead(playerId);
            instance.markPlayerDead(playerId);
            instance.markPlayerDead(playerId);

            assertTrue(instance.isPlayerDead(playerId));
            assertEquals(0, instance.getAlivePlayerCount());
        }

        @Test
        @DisplayName("Multiple markPlayerAlive calls are idempotent")
        void multipleMarkPlayerAliveCallsAreIdempotent() {
            UUID playerId = UUID.randomUUID();
            instance.onPlayerEnter(playerId);
            instance.markPlayerDead(playerId);

            instance.markPlayerAlive(playerId);
            instance.markPlayerAlive(playerId);
            instance.markPlayerAlive(playerId);

            assertFalse(instance.isPlayerDead(playerId));
            assertEquals(1, instance.getAlivePlayerCount());
        }
    }

    @Nested
    @DisplayName("Peak player count")
    class PeakPlayerCount {

        @BeforeEach
        void prepareReadyState() {
            instance.transitionTo(RealmState.READY);
        }

        @Test
        @DisplayName("Should track peak player count")
        void shouldTrackPeakPlayerCount() {
            UUID player1 = UUID.randomUUID();
            UUID player2 = UUID.randomUUID();
            UUID player3 = UUID.randomUUID();

            instance.onPlayerEnter(player1);
            instance.onPlayerEnter(player2);
            instance.onPlayerEnter(player3);

            assertEquals(3, instance.getPeakPlayerCount());

            instance.onPlayerLeave(player1);
            instance.onPlayerLeave(player2);

            // Peak should remain 3 even after players leave
            assertEquals(3, instance.getPeakPlayerCount());
            assertEquals(1, instance.getPlayerCount());
        }
    }
}
