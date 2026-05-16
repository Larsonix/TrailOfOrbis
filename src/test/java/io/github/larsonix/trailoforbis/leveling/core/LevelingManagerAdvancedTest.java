package io.github.larsonix.trailoforbis.leveling.core;

import io.github.larsonix.trailoforbis.leveling.config.LevelingConfig;
import io.github.larsonix.trailoforbis.leveling.formula.ExponentialFormula;
import io.github.larsonix.trailoforbis.leveling.repository.LevelingRepository;
import io.github.larsonix.trailoforbis.leveling.xp.XpSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Advanced tests for LevelingManager — covering overflow, non-loss source
 * level-down behavior, concurrency, and lifecycle edge cases.
 *
 * <p>These complement the main LevelingManagerTest with tests that the
 * basic suite does not cover.
 */
@ExtendWith(MockitoExtension.class)
class LevelingManagerAdvancedTest {

    @Mock
    private LevelingRepository repository;

    private ExponentialFormula formula;
    private LevelingConfig config;
    private LevelingManager levelingManager;

    private final UUID testPlayer = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        formula = new ExponentialFormula(100, 1.7, 100);
        config = new LevelingConfig();
        config.setEnabled(true);

        levelingManager = new LevelingManager(repository, formula, null, config);
    }

    // ═══════════════════════════════════════════════════════════════════
    // XP OVERFLOW PROTECTION
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("XP Overflow Protection")
    class XpOverflowProtection {

        @Test
        @DisplayName("addXp near Long.MAX_VALUE should cap, not wrap to 0")
        void addXp_nearMaxLong_shouldCap() {
            long nearMax = Long.MAX_VALUE - 100;
            PlayerLevelData data = PlayerLevelData.createWithXp(testPlayer, nearMax);
            when(repository.getOrDefault(testPlayer)).thenReturn(data);

            levelingManager.addXp(testPlayer, 200, XpSource.MOB_KILL);

            // After fix: should cap at Long.MAX_VALUE, not wrap to negative and clamp to 0
            verify(repository).save(argThat(saved -> {
                assertTrue(saved.xp() > 0, "XP must never be 0 from overflow");
                assertTrue(saved.xp() >= nearMax, "XP must not decrease");
                return true;
            }));
        }

        @Test
        @DisplayName("addXp at exactly Long.MAX_VALUE should stay at max")
        void addXp_exactlyAtMax_shouldStayAtMax() {
            PlayerLevelData data = PlayerLevelData.createWithXp(testPlayer, Long.MAX_VALUE);
            when(repository.getOrDefault(testPlayer)).thenReturn(data);

            levelingManager.addXp(testPlayer, 1, XpSource.MOB_KILL);

            verify(repository).save(argThat(saved -> {
                assertEquals(Long.MAX_VALUE, saved.xp(), "XP must stay at MAX_VALUE");
                return true;
            }));
        }

        @Test
        @DisplayName("removeXp with Long.MAX_VALUE amount should clamp to 0")
        void removeXp_hugeAmount_shouldClampTo0() {
            PlayerLevelData data = PlayerLevelData.createWithXp(testPlayer, 5000);
            when(repository.getOrDefault(testPlayer)).thenReturn(data);

            // ADMIN_COMMAND bypasses level-floor guard
            levelingManager.removeXp(testPlayer, Long.MAX_VALUE, XpSource.ADMIN_COMMAND);

            verify(repository).save(argThat(saved -> {
                assertEquals(0, saved.xp(), "XP should be clamped to 0");
                return true;
            }));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // NON-LOSS SOURCE LEVEL DOWN (ASYMMETRIC BEHAVIOR)
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Non-Loss Source Level Down")
    class NonLossSourceLevelDown {

        @Test
        @DisplayName("removeXp with ADMIN_COMMAND should allow level-down")
        void removeXp_adminCommand_shouldAllowLevelDown() {
            AtomicReference<int[]> capturedLevels = new AtomicReference<>();
            levelingManager.registerLevelDownListener((playerId, newLevel, oldLevel, totalXp) ->
                capturedLevels.set(new int[]{newLevel, oldLevel}));

            long xpAtLevel5 = formula.getXpForLevel(5) + 100;
            PlayerLevelData data = PlayerLevelData.createWithXp(testPlayer, xpAtLevel5);
            when(repository.getOrDefault(testPlayer)).thenReturn(data);

            // Remove all XP via admin command — should drop to level 1
            levelingManager.removeXp(testPlayer, xpAtLevel5, XpSource.ADMIN_COMMAND);

            assertNotNull(capturedLevels.get(), "Level-down event must fire for ADMIN_COMMAND");
            assertEquals(1, capturedLevels.get()[0], "Should drop to level 1");
            assertEquals(5, capturedLevels.get()[1], "Old level should be 5");
        }

        @Test
        @DisplayName("removeXp with OTHER source should allow level-down")
        void removeXp_otherSource_shouldAllowLevelDown() {
            AtomicReference<int[]> capturedLevels = new AtomicReference<>();
            levelingManager.registerLevelDownListener((playerId, newLevel, oldLevel, totalXp) ->
                capturedLevels.set(new int[]{newLevel, oldLevel}));

            long xpAtLevel3 = formula.getXpForLevel(3) + 50;
            PlayerLevelData data = PlayerLevelData.createWithXp(testPlayer, xpAtLevel3);
            when(repository.getOrDefault(testPlayer)).thenReturn(data);

            levelingManager.removeXp(testPlayer, 10000, XpSource.OTHER);

            assertNotNull(capturedLevels.get(), "Level-down event must fire for OTHER source");
        }

        @Test
        @DisplayName("removeXp with DEATH_PENALTY at exact level floor should do nothing")
        void removeXp_deathPenalty_atLevelFloor_shouldDoNothing() {
            // Player at exactly level 5 threshold — zero progress
            long xpAtLevel5 = formula.getXpForLevel(5);
            PlayerLevelData data = PlayerLevelData.createWithXp(testPlayer, xpAtLevel5);
            when(repository.getOrDefault(testPlayer)).thenReturn(data);

            levelingManager.removeXp(testPlayer, 100, XpSource.DEATH_PENALTY);

            // actualLoss = min(100, data.xp()) = 100
            // levelFloor = getXpForLevel(5) = xpAtLevel5
            // maxLoss = max(0, xpAtLevel5 - xpAtLevel5) = 0
            // actualLoss = min(100, 0) = 0 → returns early
            verify(repository, never()).save(any());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CONCURRENT ADD AND REMOVE
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Concurrent addXp and removeXp")
    class ConcurrentAddAndRemove {

        @Test
        @DisplayName("Concurrent add and remove should not corrupt state")
        void concurrentAddAndRemove_shouldNotCorruptState() throws InterruptedException {
            // Use a stateful mock: track cumulative XP through saves
            AtomicLong currentXp = new AtomicLong(10_000);

            lenient().when(repository.getOrDefault(testPlayer)).thenAnswer(inv ->
                PlayerLevelData.createWithXp(testPlayer, currentXp.get()));

            lenient().doAnswer(inv -> {
                PlayerLevelData saved = inv.getArgument(0);
                currentXp.set(saved.xp());
                return null;
            }).when(repository).save(any(PlayerLevelData.class));

            int threadCount = 5;
            int opsPerThread = 50;
            Thread[] addThreads = new Thread[threadCount];
            Thread[] removeThreads = new Thread[threadCount];

            for (int i = 0; i < threadCount; i++) {
                addThreads[i] = new Thread(() -> {
                    for (int j = 0; j < opsPerThread; j++) {
                        levelingManager.addXp(testPlayer, 10, XpSource.MOB_KILL);
                    }
                });
                removeThreads[i] = new Thread(() -> {
                    for (int j = 0; j < opsPerThread; j++) {
                        levelingManager.removeXp(testPlayer, 5, XpSource.ADMIN_COMMAND);
                    }
                });
            }

            for (int i = 0; i < threadCount; i++) {
                addThreads[i].start();
                removeThreads[i].start();
            }
            for (int i = 0; i < threadCount; i++) {
                addThreads[i].join();
                removeThreads[i].join();
            }

            // Net change: 5 threads × 50 × 10 = 2500 added, 5 × 50 × 5 = 1250 removed
            // Expected: 10000 + 2500 - 1250 = 11250
            // Due to per-player locking, this should be exact
            long finalXp = currentXp.get();
            assertTrue(finalXp > 0, "XP must remain positive");
            // With per-player lock, the result should be deterministic
            assertEquals(11250, finalXp, "Final XP should be 10000 + 2500 - 1250 = 11250");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // RAPID RECONNECT
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Rapid Reconnect")
    class RapidReconnect {

        @Test
        @DisplayName("unload then immediate load should not lose XP")
        void unloadThenLoad_shouldNotLoseXp() {
            // Player loaded with 500 XP
            PlayerLevelData original = PlayerLevelData.createWithXp(testPlayer, 500);
            when(repository.loadOrCreate(testPlayer)).thenReturn(original);
            levelingManager.loadPlayer(testPlayer);

            // Add 100 XP (cache updated to 600)
            when(repository.getOrDefault(testPlayer)).thenReturn(
                PlayerLevelData.createWithXp(testPlayer, 500));
            levelingManager.addXp(testPlayer, 100, XpSource.MOB_KILL);

            // Capture the data that was saved (should be 600 XP)
            AtomicReference<PlayerLevelData> lastSaved = new AtomicReference<>();
            doAnswer(inv -> {
                lastSaved.set(inv.getArgument(0));
                return null;
            }).when(repository).save(any(PlayerLevelData.class));
            when(repository.getOrDefault(testPlayer)).thenReturn(
                PlayerLevelData.createWithXp(testPlayer, 500));
            levelingManager.addXp(testPlayer, 100, XpSource.MOB_KILL);

            // Simulate what saveSync does: persist to "DB"
            PlayerLevelData savedData = lastSaved.get();
            assertNotNull(savedData);

            // Unload: triggers saveSync
            when(repository.get(testPlayer)).thenReturn(savedData);
            levelingManager.unloadPlayer(testPlayer);
            verify(repository).saveSync(savedData);

            // Reload: should see the saved data
            when(repository.loadOrCreate(testPlayer)).thenReturn(savedData);
            PlayerLevelData reloaded = levelingManager.loadPlayer(testPlayer);

            assertTrue(reloaded.xp() >= 500, "XP should not decrease on reconnect");
        }

        @Test
        @DisplayName("unloadPlayer after shutdown should not throw")
        void unloadAfterShutdown_shouldNotThrow() {
            levelingManager.shutdown();

            when(repository.get(testPlayer)).thenReturn(null);
            assertDoesNotThrow(() -> levelingManager.unloadPlayer(testPlayer));
        }
    }
}
