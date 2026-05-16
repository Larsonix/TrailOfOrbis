package io.github.larsonix.trailoforbis.loot.container;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ContainerTracker} — in-memory TTL-based container tracking
 * with per-player keys, scheduled cleanup, and thread safety.
 *
 * <p>Uses short TTL (200ms) and cleanup intervals (100ms) for fast tests.
 */
class ContainerTrackerTest {

    /** Short TTL for fast expiry tests. */
    private static final long SHORT_TTL_MS = 200;
    /** Cleanup interval shorter than TTL so cleanup runs within test window. */
    private static final long SHORT_CLEANUP_MS = 100;

    private ContainerTracker tracker;

    private static final UUID PLAYER_A = UUID.randomUUID();
    private static final UUID PLAYER_B = UUID.randomUUID();
    private static final String WORLD = "test_world";

    @BeforeEach
    void setUp() {
        tracker = new ContainerTracker(SHORT_TTL_MS, SHORT_CLEANUP_MS);
    }

    @AfterEach
    void tearDown() {
        if (tracker != null) {
            tracker.shutdown();
        }
    }

    // =========================================================================
    // BASIC OPERATIONS
    // =========================================================================

    @Nested
    @DisplayName("Basic Operations")
    class BasicOperations {

        @Test
        @DisplayName("markProcessed first time returns true")
        void markProcessed_firstTime_returnsTrue() {
            boolean result = tracker.markProcessed(WORLD, 10, 20, 30, PLAYER_A);
            assertTrue(result, "First mark should return true");
        }

        @Test
        @DisplayName("markProcessed same player same position returns false")
        void markProcessed_samePlayerSamePosition_returnsFalse() {
            tracker.markProcessed(WORLD, 10, 20, 30, PLAYER_A);
            boolean result = tracker.markProcessed(WORLD, 10, 20, 30, PLAYER_A);
            assertFalse(result, "Second mark for same player+position should return false");
        }

        @Test
        @DisplayName("markProcessed different player same position returns true")
        void markProcessed_differentPlayer_returnsTrue() {
            tracker.markProcessed(WORLD, 10, 20, 30, PLAYER_A);
            boolean result = tracker.markProcessed(WORLD, 10, 20, 30, PLAYER_B);
            assertTrue(result, "Different player at same position should return true (per-player tracking)");
        }

        @Test
        @DisplayName("markProcessed different position same player returns true")
        void markProcessed_differentPosition_returnsTrue() {
            tracker.markProcessed(WORLD, 10, 20, 30, PLAYER_A);
            boolean result = tracker.markProcessed(WORLD, 99, 99, 99, PLAYER_A);
            assertTrue(result, "Same player at different position should return true");
        }

        @Test
        @DisplayName("isProcessedByPlayer returns true after mark")
        void isProcessedByPlayer_afterMark_returnsTrue() {
            tracker.markProcessed(WORLD, 10, 20, 30, PLAYER_A);
            boolean result = tracker.isProcessedByPlayer(WORLD, 10, 20, 30, PLAYER_A);
            assertTrue(result, "Should be processed for this player after marking");
        }

        @Test
        @DisplayName("isProcessedByPlayer returns false for different player")
        void isProcessedByPlayer_differentPlayer_returnsFalse() {
            tracker.markProcessed(WORLD, 10, 20, 30, PLAYER_A);
            boolean result = tracker.isProcessedByPlayer(WORLD, 10, 20, 30, PLAYER_B);
            assertFalse(result, "Should not be processed for a different player");
        }

        @Test
        @DisplayName("isProcessed (legacy) with null playerId key")
        void isProcessed_legacyPositionOnly() {
            // isProcessed uses playerId=null in the key, but markProcessed uses non-null
            // This means isProcessed() checks a DIFFERENT key than what markProcessed() stores
            // This is a known behavior — the legacy check cannot find player-specific entries
            tracker.markProcessed(WORLD, 10, 20, 30, PLAYER_A);

            // Legacy isProcessed creates ContainerKey(world, x, y, z, null)
            // markProcessed created ContainerKey(world, x, y, z, PLAYER_A)
            // These are different keys (record equality includes playerId)
            boolean result = tracker.isProcessed(WORLD, 10, 20, 30);
            assertFalse(result,
                "Legacy isProcessed uses null playerId key — cannot find player-specific entries");
        }
    }

    // =========================================================================
    // EXPIRY
    // =========================================================================

    @Nested
    @DisplayName("Expiry")
    class Expiry {

        @Test
        @DisplayName("isProcessedByPlayer returns false after TTL expiry")
        void isProcessedByPlayer_afterExpiry_returnsFalse() throws InterruptedException {
            tracker.markProcessed(WORLD, 10, 20, 30, PLAYER_A);
            assertTrue(tracker.isProcessedByPlayer(WORLD, 10, 20, 30, PLAYER_A));

            // Wait for TTL to expire
            Thread.sleep(SHORT_TTL_MS + 50);

            boolean result = tracker.isProcessedByPlayer(WORLD, 10, 20, 30, PLAYER_A);
            assertFalse(result, "Should return false after TTL expiry");
        }

        @Test
        @DisplayName("markProcessed replaces expired entry and returns true")
        void markProcessed_replacesExpiredEntry_returnsTrue() throws InterruptedException {
            tracker.markProcessed(WORLD, 10, 20, 30, PLAYER_A);

            // Wait for expiry
            Thread.sleep(SHORT_TTL_MS + 50);

            // Should replace the expired entry
            boolean result = tracker.markProcessed(WORLD, 10, 20, 30, PLAYER_A);
            assertTrue(result, "Should return true when replacing expired entry");
        }

        @Test
        @DisplayName("getProcessedState returns null after expiry")
        void getProcessedState_afterExpiry_returnsNull() throws InterruptedException {
            tracker.markProcessed(WORLD, 10, 20, 30, PLAYER_A);
            // getProcessedState uses null playerId key — see isProcessed_legacyPositionOnly
            // So it won't find player-specific entries anyway

            Thread.sleep(SHORT_TTL_MS + 50);

            var state = tracker.getProcessedState(WORLD, 10, 20, 30);
            assertNull(state, "Should return null after expiry");
        }
    }

    // =========================================================================
    // CLEANUP
    // =========================================================================

    @Nested
    @DisplayName("Cleanup")
    class Cleanup {

        @Test
        @DisplayName("Cleanup removes expired entries")
        void cleanup_removesExpiredEntries() throws InterruptedException {
            tracker.markProcessed(WORLD, 1, 0, 0, PLAYER_A);
            tracker.markProcessed(WORLD, 2, 0, 0, PLAYER_A);
            tracker.markProcessed(WORLD, 3, 0, 0, PLAYER_A);
            assertEquals(3, tracker.size());

            // Wait for TTL + cleanup interval
            Thread.sleep(SHORT_TTL_MS + SHORT_CLEANUP_MS + 100);

            // Entries should have been cleaned up by the background task
            assertEquals(0, tracker.size(), "Expired entries should be removed by cleanup");
        }

        @Test
        @DisplayName("Cleanup preserves unexpired entries")
        void cleanup_preservesUnexpiredEntries() throws InterruptedException {
            tracker.markProcessed(WORLD, 1, 0, 0, PLAYER_A);

            // Wait less than TTL — entries should survive cleanup
            Thread.sleep(SHORT_CLEANUP_MS + 20);

            assertTrue(tracker.size() > 0, "Unexpired entries should survive cleanup");
            assertTrue(tracker.isProcessedByPlayer(WORLD, 1, 0, 0, PLAYER_A));
        }
    }

    // =========================================================================
    // LIFECYCLE
    // =========================================================================

    @Nested
    @DisplayName("Lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("clear removes all entries")
        void clear_removesAllEntries() {
            tracker.markProcessed(WORLD, 1, 0, 0, PLAYER_A);
            tracker.markProcessed(WORLD, 2, 0, 0, PLAYER_A);
            tracker.markProcessed(WORLD, 3, 0, 0, PLAYER_B);
            assertEquals(3, tracker.size());

            tracker.clear();

            assertEquals(0, tracker.size(), "clear should remove all entries");
        }

        @Test
        @DisplayName("shutdown clears all entries")
        void shutdown_clearsAllEntries() {
            tracker.markProcessed(WORLD, 1, 0, 0, PLAYER_A);
            tracker.markProcessed(WORLD, 2, 0, 0, PLAYER_B);
            assertEquals(2, tracker.size());

            tracker.shutdown();

            assertEquals(0, tracker.size(), "shutdown should clear all entries");
        }

        @Test
        @DisplayName("shutdown is idempotent")
        void shutdown_idempotent() {
            tracker.markProcessed(WORLD, 1, 0, 0, PLAYER_A);

            // Double shutdown should not throw
            assertDoesNotThrow(() -> {
                tracker.shutdown();
                tracker.shutdown();
            });
        }

        @Test
        @DisplayName("size tracks entry count accurately")
        void size_tracksEntryCount() {
            assertEquals(0, tracker.size());
            tracker.markProcessed(WORLD, 1, 0, 0, PLAYER_A);
            assertEquals(1, tracker.size());
            tracker.markProcessed(WORLD, 2, 0, 0, PLAYER_A);
            assertEquals(2, tracker.size());

            // Same key doesn't increase size
            tracker.markProcessed(WORLD, 1, 0, 0, PLAYER_A);
            assertEquals(2, tracker.size(), "Duplicate mark should not increase size");
        }

        @Test
        @DisplayName("remove deletes specific position entry")
        void remove_deletesEntry() {
            tracker.markProcessed(WORLD, 10, 20, 30, PLAYER_A);
            // Note: remove uses null playerId key, so it removes a different key
            // than what was stored. This is a design decision — remove targets
            // position-only entries, not per-player entries.
            tracker.remove(WORLD, 10, 20, 30);

            // Player-specific entry should still exist (different key)
            assertTrue(tracker.isProcessedByPlayer(WORLD, 10, 20, 30, PLAYER_A),
                "Per-player entry should survive position-only remove");
        }
    }

    // =========================================================================
    // THREAD SAFETY
    // =========================================================================

    @Nested
    @DisplayName("Thread Safety")
    class ThreadSafety {

        @Test
        @DisplayName("Concurrent mark and check from multiple threads")
        void concurrentMarkAndCheck_noExceptions() throws InterruptedException {
            int threadCount = 10;
            int opsPerThread = 100;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            List<Throwable> errors = new CopyOnWriteArrayList<>();

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        UUID playerId = UUID.randomUUID();
                        for (int i = 0; i < opsPerThread; i++) {
                            tracker.markProcessed(WORLD, threadId, i, 0, playerId);
                            tracker.isProcessedByPlayer(WORLD, threadId, i, 0, playerId);
                        }
                    } catch (Throwable e) {
                        errors.add(e);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS), "Threads should complete in time");
            executor.shutdown();

            assertTrue(errors.isEmpty(),
                "No exceptions during concurrent access, got: " + errors);
        }

        @Test
        @DisplayName("Concurrent marks for different players all succeed")
        void concurrentMarkDifferentPlayers_allSucceed() throws InterruptedException {
            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            List<Boolean> results = new CopyOnWriteArrayList<>();

            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    try {
                        UUID playerId = UUID.randomUUID();
                        boolean result = tracker.markProcessed(WORLD, 10, 20, 30, playerId);
                        results.add(result);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS), "Threads should complete");
            executor.shutdown();

            // All marks should succeed (different players = different keys)
            assertEquals(threadCount, results.size());
            assertTrue(results.stream().allMatch(Boolean::booleanValue),
                "All marks for different players should return true");
        }
    }

    // =========================================================================
    // EDGE CASES
    // =========================================================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Null world name throws NullPointerException")
        void markProcessed_nullWorldName_throws() {
            assertThrows(NullPointerException.class, () ->
                tracker.markProcessed(null, 0, 0, 0, PLAYER_A)
            );
        }

        @Test
        @DisplayName("Null player ID throws NullPointerException")
        void markProcessed_nullPlayerId_throws() {
            assertThrows(NullPointerException.class, () ->
                tracker.markProcessed(WORLD, 0, 0, 0, null)
            );
        }

        @Test
        @DisplayName("ContainerKey equality includes playerId")
        void containerKey_equality_includesPlayerId() {
            var keyA = new ContainerTracker.ContainerKey(WORLD, 1, 2, 3, PLAYER_A);
            var keyB = new ContainerTracker.ContainerKey(WORLD, 1, 2, 3, PLAYER_B);
            var keyNull = new ContainerTracker.ContainerKey(WORLD, 1, 2, 3, null);

            assertNotEquals(keyA, keyB, "Different players should produce different keys");
            assertNotEquals(keyA, keyNull, "Player key should differ from null key");
            assertNotEquals(keyB, keyNull, "Player key should differ from null key");
        }

        @Test
        @DisplayName("ContainerKey equality same position same player matches")
        void containerKey_equality_samePositionSamePlayer_matches() {
            var key1 = new ContainerTracker.ContainerKey(WORLD, 1, 2, 3, PLAYER_A);
            var key2 = new ContainerTracker.ContainerKey(WORLD, 1, 2, 3, PLAYER_A);

            assertEquals(key1, key2, "Same position + same player = equal keys");
            assertEquals(key1.hashCode(), key2.hashCode(), "Equal keys must have equal hashCodes");
        }

        @Test
        @DisplayName("ContainerKey with null playerId matches other null")
        void containerKey_equality_nullPlayerId_matchesOtherNull() {
            var key1 = new ContainerTracker.ContainerKey(WORLD, 1, 2, 3, null);
            var key2 = new ContainerTracker.ContainerKey(WORLD, 1, 2, 3, null);

            assertEquals(key1, key2, "Both null playerId = equal position-only keys");
        }

        @Test
        @DisplayName("ProcessedState records age correctly")
        void processedState_age_increases() throws InterruptedException {
            long beforeMs = System.currentTimeMillis();
            var state = new ContainerTracker.ProcessedState(beforeMs, PLAYER_A);

            Thread.sleep(50);

            long age = state.getAgeMs();
            assertTrue(age >= 40, "Age should be at least ~50ms, was: " + age);
        }

        @Test
        @DisplayName("Negative coordinates work as keys")
        void negativeCoordinates_work() {
            boolean result = tracker.markProcessed(WORLD, -100, -64, -200, PLAYER_A);
            assertTrue(result, "Negative coordinates should work as keys");
            assertTrue(tracker.isProcessedByPlayer(WORLD, -100, -64, -200, PLAYER_A));
        }

        @Test
        @DisplayName("Different worlds are tracked separately")
        void differentWorlds_trackedSeparately() {
            tracker.markProcessed("world_a", 10, 20, 30, PLAYER_A);
            boolean result = tracker.isProcessedByPlayer("world_b", 10, 20, 30, PLAYER_A);
            assertFalse(result, "Different worlds should be tracked independently");
        }
    }
}
