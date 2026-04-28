package io.github.larsonix.trailoforbis.maps.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RealmCompletionTracker}.
 */
@DisplayName("RealmCompletionTracker")
class RealmCompletionTrackerTest {

    private UUID realmId;
    private RealmCompletionTracker tracker;

    @BeforeEach
    void setUp() {
        realmId = UUID.randomUUID();
        tracker = new RealmCompletionTracker(realmId, 20);
    }

    @Nested
    @DisplayName("Initialization")
    class Initialization {

        @Test
        @DisplayName("Should initialize with specified total monsters")
        void shouldInitializeWithSpecifiedTotalMonsters() {
            assertEquals(20, tracker.getTotalMonsters());
            assertEquals(20, tracker.getRemainingMonsters());
            assertEquals(0, tracker.getKilledByPlayers());
        }

        @Test
        @DisplayName("Should initialize as not completed")
        void shouldInitializeAsNotCompleted() {
            assertFalse(tracker.isCompleted());
            assertTrue(tracker.getCompletionTime().isEmpty());
        }

        @Test
        @DisplayName("Should not have started")
        void shouldNotHaveStarted() {
            assertTrue(tracker.getStartTime().isEmpty());
        }

        @Test
        @DisplayName("Should have zero participants")
        void shouldHaveZeroParticipants() {
            assertEquals(0, tracker.getParticipantCount());
            assertTrue(tracker.getParticipants().isEmpty());
        }

        @Test
        @DisplayName("Should clamp negative total to 0")
        void shouldClampNegativeTotal() {
            RealmCompletionTracker zeroTracker = new RealmCompletionTracker(realmId, -10);
            assertEquals(0, zeroTracker.getTotalMonsters());
            assertEquals(0, zeroTracker.getRemainingMonsters());
        }
    }

    @Nested
    @DisplayName("Recording kills")
    class RecordingKills {

        private UUID killerId;

        @BeforeEach
        void setUpKiller() {
            killerId = UUID.randomUUID();
        }

        @Test
        @DisplayName("onMonsterKilled should decrement remaining monsters")
        void onMonsterKilledShouldDecrementRemaining() {
            tracker.onMonsterKilled(killerId, 1, false, false);

            assertEquals(19, tracker.getRemainingMonsters());
            assertEquals(1, tracker.getKilledByPlayers());
        }

        @Test
        @DisplayName("onMonsterKilled should track elite kills")
        void onMonsterKilledShouldTrackEliteKills() {
            tracker.onMonsterKilled(killerId, 1, true, false);

            assertEquals(1, tracker.getElitesKilled());
            assertEquals(0, tracker.getBossesKilled());
        }

        @Test
        @DisplayName("onMonsterKilled should track boss kills")
        void onMonsterKilledShouldTrackBossKills() {
            tracker.onMonsterKilled(killerId, 1, false, true);

            assertEquals(0, tracker.getElitesKilled());
            assertEquals(1, tracker.getBossesKilled());
        }

        @Test
        @DisplayName("onMonsterKilled should track kills per player")
        void onMonsterKilledShouldTrackKillsPerPlayer() {
            UUID player1 = UUID.randomUUID();
            UUID player2 = UUID.randomUUID();

            tracker.onMonsterKilled(player1, 1, false, false);
            tracker.onMonsterKilled(player1, 1, false, false);
            tracker.onMonsterKilled(player2, 1, false, false);

            assertEquals(2, tracker.getPlayerKills(player1));
            assertEquals(1, tracker.getPlayerKills(player2));
        }

        @Test
        @DisplayName("onMonsterKilled should add killer as participant")
        void onMonsterKilledShouldAddKillerAsParticipant() {
            tracker.onMonsterKilled(killerId, 1, false, false);

            assertTrue(tracker.hasParticipated(killerId));
            assertTrue(tracker.getParticipants().contains(killerId));
        }

        @Test
        @DisplayName("Multiple kills should update all counters correctly")
        void multipleKillsShouldUpdateAllCounters() {
            for (int i = 0; i < 5; i++) {
                tracker.onMonsterKilled(killerId, 1, false, false);
            }
            tracker.onMonsterKilled(killerId, 1, true, false); // Elite
            tracker.onMonsterKilled(killerId, 1, false, true); // Boss

            assertEquals(13, tracker.getRemainingMonsters());
            assertEquals(7, tracker.getKilledByPlayers());
            assertEquals(1, tracker.getElitesKilled());
            assertEquals(1, tracker.getBossesKilled());
            assertEquals(7, tracker.getPlayerKills(killerId));
        }
    }

    @Nested
    @DisplayName("Completion tracking")
    class CompletionTracking {

        private UUID killerId;

        @BeforeEach
        void setUpKiller() {
            killerId = UUID.randomUUID();
        }

        @Test
        @DisplayName("Should return true when completing")
        void shouldReturnTrueWhenCompleting() {
            // 10 mobs, buffer=3 → effective=7, required=ceil(7*0.95)=7
            tracker = new RealmCompletionTracker(realmId, 10);

            // Kill 6 — not enough yet
            for (int i = 0; i < 6; i++) {
                assertFalse(tracker.onMonsterKilled(killerId, 1, false, false));
                assertFalse(tracker.isCompleted());
            }

            // Kill #7 triggers completion
            assertTrue(tracker.onMonsterKilled(killerId, 1, false, false));
            assertTrue(tracker.isCompleted());
            assertTrue(tracker.getCompletionTime().isPresent());
        }

        @Test
        @DisplayName("Should be completed with zero monsters")
        void shouldBeCompletedWithZeroMonsters() {
            RealmCompletionTracker zeroTracker = new RealmCompletionTracker(realmId, 0);

            // Not automatically completed until we check progress
            assertEquals(1.0f, zeroTracker.getCompletionProgress(), 0.01);
        }
    }

    @Nested
    @DisplayName("Progress tracking")
    class ProgressTracking {

        private UUID killerId;

        @BeforeEach
        void setUpKiller() {
            killerId = UUID.randomUUID();
        }

        @Test
        @DisplayName("Should return 0% progress when not started")
        void shouldReturn0ProgressWhenNotStarted() {
            assertEquals(0.0f, tracker.getCompletionProgress(), 0.01);
        }

        @Test
        @DisplayName("Should return correct partial progress")
        void shouldReturnCorrectPartialProgress() {
            // Kill 6 out of 20 = 30%
            for (int i = 0; i < 6; i++) {
                tracker.onMonsterKilled(killerId, 1, false, false);
            }

            assertEquals(0.30f, tracker.getCompletionProgress(), 0.01);
            assertEquals(30, tracker.getCompletionPercentage());
        }

        @Test
        @DisplayName("Should return 100% when complete")
        void shouldReturn100WhenComplete() {
            for (int i = 0; i < 20; i++) {
                tracker.onMonsterKilled(killerId, 1, false, false);
            }

            assertEquals(1.0f, tracker.getCompletionProgress(), 0.01);
            assertEquals(100, tracker.getCompletionPercentage());
        }

        @Test
        @DisplayName("Should return 100% for zero total monsters")
        void shouldReturn100ForZeroTotalMonsters() {
            RealmCompletionTracker zeroTracker = new RealmCompletionTracker(realmId, 0);
            assertEquals(1.0f, zeroTracker.getCompletionProgress(), 0.01);
        }
    }

    @Nested
    @DisplayName("Player participation")
    class PlayerParticipation {

        @Test
        @DisplayName("addParticipant should track player")
        void addParticipantShouldTrackPlayer() {
            UUID playerId = UUID.randomUUID();

            tracker.addParticipant(playerId);

            assertTrue(tracker.hasParticipated(playerId));
            assertEquals(1, tracker.getParticipantCount());
        }

        @Test
        @DisplayName("Should track multiple participants")
        void shouldTrackMultipleParticipants() {
            UUID player1 = UUID.randomUUID();
            UUID player2 = UUID.randomUUID();
            UUID player3 = UUID.randomUUID();

            tracker.addParticipant(player1);
            tracker.addParticipant(player2);
            tracker.addParticipant(player3);
            tracker.addParticipant(player1); // Duplicate

            assertEquals(3, tracker.getParticipantCount());
        }

        @Test
        @DisplayName("Should return zero kills for non-participant")
        void shouldReturnZeroKillsForNonParticipant() {
            UUID nonParticipant = UUID.randomUUID();
            assertEquals(0, tracker.getPlayerKills(nonParticipant));
        }
    }

    @Nested
    @DisplayName("Start time tracking")
    class StartTimeTracking {

        @Test
        @DisplayName("start() should set start time")
        void startShouldSetStartTime() {
            assertTrue(tracker.getStartTime().isEmpty());

            tracker.start();

            assertTrue(tracker.getStartTime().isPresent());
        }

        @Test
        @DisplayName("start() should only set time once")
        void startShouldOnlySetTimeOnce() {
            tracker.start();
            var firstTime = tracker.getStartTime().orElseThrow();

            tracker.start();
            var secondTime = tracker.getStartTime().orElseThrow();

            assertEquals(firstTime, secondTime);
        }
    }

    @Nested
    @DisplayName("Monster spawning")
    class MonsterSpawning {

        @Test
        @DisplayName("onMonstersSpawned should increase totals")
        void onMonstersSpawnedShouldIncreaseTotals() {
            tracker.onMonstersSpawned(5);

            assertEquals(25, tracker.getTotalMonsters());
            assertEquals(25, tracker.getRemainingMonsters());
        }
    }

    @Nested
    @DisplayName("Timeout tracking")
    class TimeoutTracking {

        @Test
        @DisplayName("markTimedOut should mark timeout")
        void markTimedOutShouldMarkTimeout() {
            assertTrue(tracker.markTimedOut());
            assertTrue(tracker.isTimedOut());
            assertTrue(tracker.isFinished());
        }

        @Test
        @DisplayName("markTimedOut should not work if already completed")
        void markTimedOutShouldNotWorkIfCompleted() {
            tracker.markCompleted();

            assertFalse(tracker.markTimedOut());
            assertFalse(tracker.isTimedOut());
        }
    }

    @Nested
    @DisplayName("Player statistics")
    class PlayerStatistics {

        @Test
        @DisplayName("Should track damage dealt")
        void shouldTrackDamageDealt() {
            UUID playerId = UUID.randomUUID();

            tracker.recordDamage(playerId, 100);
            tracker.recordDamage(playerId, 50);

            assertEquals(150, tracker.getPlayerDamage(playerId));
            assertTrue(tracker.hasParticipated(playerId));
        }

        @Test
        @DisplayName("Should find top killer")
        void shouldFindTopKiller() {
            UUID player1 = UUID.randomUUID();
            UUID player2 = UUID.randomUUID();

            tracker.onMonsterKilled(player1, 5, false, false);
            tracker.onMonsterKilled(player2, 3, false, false);

            var topKiller = tracker.getTopKiller();
            assertTrue(topKiller.isPresent());
            assertEquals(player1, topKiller.get());
        }

        @Test
        @DisplayName("Should calculate contribution percentage")
        void shouldCalculateContributionPercentage() {
            UUID player1 = UUID.randomUUID();
            UUID player2 = UUID.randomUUID();

            tracker.onMonsterKilled(player1, 3, false, false);
            tracker.onMonsterKilled(player2, 1, false, false);

            // Player1: 3/4 = 75%
            assertEquals(75.0f, tracker.getPlayerContribution(player1), 0.01);
            // Player2: 1/4 = 25%
            assertEquals(25.0f, tracker.getPlayerContribution(player2), 0.01);
        }
    }
}
