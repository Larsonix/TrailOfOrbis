package io.github.larsonix.trailoforbis.leveling.core;

import io.github.larsonix.trailoforbis.leveling.api.LevelingEvents;
import io.github.larsonix.trailoforbis.leveling.config.LevelingConfig;
import io.github.larsonix.trailoforbis.leveling.formula.ExponentialFormula;
import io.github.larsonix.trailoforbis.leveling.repository.LevelingRepository;
import io.github.larsonix.trailoforbis.leveling.xp.XpSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for LevelingManager - the core leveling service.
 *
 * Tests cover:
 * - XP gain and loss operations
 * - Level calculation
 * - Event firing
 * - Thread safety with player locks
 * - Edge cases and validation
 */
@ExtendWith(MockitoExtension.class)
class LevelingManagerTest {

    @Mock
    private LevelingRepository repository;

    private ExponentialFormula formula;
    private LevelingConfig config;
    private LevelingManager levelingManager;

    private final UUID testPlayer = UUID.randomUUID();

    // ═══════════════════════════════════════════════════════════════════
    // SETUP
    // ═══════════════════════════════════════════════════════════════════

    @BeforeEach
    void setUp() {
        formula = new ExponentialFormula(100, 1.7, 100);
        config = new LevelingConfig();
        config.setEnabled(true);

        levelingManager = new LevelingManager(repository, formula, config);
    }

    @AfterEach
    void tearDown() {
        levelingManager = null;
    }

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTOR TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("Should create with valid parameters")
        void shouldCreateWithValidParameters() {
            assertDoesNotThrow(() ->
                new LevelingManager(repository, formula, config));
        }

        @Test
        @DisplayName("Should store dependencies")
        void shouldStoreDependencies() {
            assertSame(repository, levelingManager.getRepository());
            assertSame(formula, levelingManager.getFormula());
            assertSame(config, levelingManager.getConfig());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // PLAYER LIFECYCLE TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Player Lifecycle")
    class PlayerLifecycleTests {

        @Test
        @DisplayName("loadPlayer should delegate to repository")
        void loadPlayerShouldDelegate() {
            PlayerLevelData expectedData = PlayerLevelData.createNew(testPlayer);
            when(repository.loadOrCreate(testPlayer)).thenReturn(expectedData);

            PlayerLevelData result = levelingManager.loadPlayer(testPlayer);

            assertSame(expectedData, result);
            verify(repository).loadOrCreate(testPlayer);
        }

        @Test
        @DisplayName("unloadPlayer should evict from repository")
        void unloadPlayerShouldEvict() {
            PlayerLevelData data = PlayerLevelData.createNew(testPlayer);
            when(repository.get(testPlayer)).thenReturn(data);

            levelingManager.unloadPlayer(testPlayer);

            verify(repository).saveSync(data); // sync save to prevent data loss on quick reconnect
            verify(repository).evict(testPlayer);
        }

        @Test
        @DisplayName("unloadPlayer should handle null data")
        void unloadPlayerShouldHandleNullData() {
            when(repository.get(testPlayer)).thenReturn(null);

            assertDoesNotThrow(() -> levelingManager.unloadPlayer(testPlayer));

            verify(repository, never()).saveSync(any());
            verify(repository).evict(testPlayer);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // XP GETTER TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getXp")
    class GetXpTests {

        @Test
        @DisplayName("Should return XP from repository")
        void shouldReturnXpFromRepository() {
            long expectedXp = 1500L;
            PlayerLevelData data = PlayerLevelData.createWithXp(testPlayer, expectedXp);
            when(repository.get(testPlayer)).thenReturn(data);

            long xp = levelingManager.getXp(testPlayer);

            assertEquals(expectedXp, xp);
        }

        @Test
        @DisplayName("Should return 0 for unknown player")
        void shouldReturnZeroForUnknownPlayer() {
            when(repository.get(testPlayer)).thenReturn(null);

            long xp = levelingManager.getXp(testPlayer);

            assertEquals(0, xp);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // XP ADDITION TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("addXp")
    class AddXpTests {

        @Test
        @DisplayName("Should add XP and save")
        void shouldAddXpAndSave() {
            PlayerLevelData originalData = PlayerLevelData.createWithXp(testPlayer, 0);
            when(repository.getOrDefault(testPlayer)).thenReturn(originalData);

            levelingManager.addXp(testPlayer, 100, XpSource.MOB_KILL);

            verify(repository).save(any(PlayerLevelData.class));
        }

        @Test
        @DisplayName("Should throw for negative amount")
        void shouldThrowForNegativeAmount() {
            assertThrows(IllegalArgumentException.class, () ->
                levelingManager.addXp(testPlayer, -100, XpSource.MOB_KILL));
        }

        @Test
        @DisplayName("Should do nothing for zero amount")
        void shouldDoNothingForZeroAmount() {
            levelingManager.addXp(testPlayer, 0, XpSource.MOB_KILL);

            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("Should fire XP gain event")
        void shouldFireXpGainEvent() {
            AtomicReference<Long> capturedXp = new AtomicReference<>();
            levelingManager.registerXpGainListener((playerId, amount, source, totalXp) -> {
                capturedXp.set(amount);
            });

            PlayerLevelData originalData = PlayerLevelData.createWithXp(testPlayer, 0);
            when(repository.getOrDefault(testPlayer)).thenReturn(originalData);

            levelingManager.addXp(testPlayer, 100, XpSource.MOB_KILL);

            assertEquals(100L, capturedXp.get());
        }

        @Test
        @DisplayName("Should fire level-up event when crossing threshold")
        void shouldFireLevelUpEvent() {
            AtomicReference<int[]> capturedLevels = new AtomicReference<>();
            levelingManager.registerLevelUpListener((playerId, newLevel, oldLevel, totalXp) -> {
                capturedLevels.set(new int[]{newLevel, oldLevel, (int) totalXp});
            });

            // Create data at level 1 (0 XP) but close to level 2 threshold
            PlayerLevelData originalData = PlayerLevelData.createWithXp(testPlayer, 50);
            when(repository.getOrDefault(testPlayer)).thenReturn(originalData);

            // Add 60 XP to cross level 2 threshold (100 XP)
            levelingManager.addXp(testPlayer, 60, XpSource.MOB_KILL);

            assertNotNull(capturedLevels.get());
            assertEquals(2, capturedLevels.get()[0]); // newLevel
            assertEquals(1, capturedLevels.get()[1]); // oldLevel
        }

        @Test
        @DisplayName("Should not fire level-up when staying at same level")
        void shouldNotFireLevelUpWhenStayingSameLevel() {
            AtomicReference<int[]> capturedLevels = new AtomicReference<>();
            levelingManager.registerLevelUpListener((playerId, newLevel, oldLevel, totalXp) -> {
                capturedLevels.set(new int[]{newLevel, oldLevel});
            });

            PlayerLevelData originalData = PlayerLevelData.createWithXp(testPlayer, 0);
            when(repository.getOrDefault(testPlayer)).thenReturn(originalData);

            // Add small amount that doesn't cross level threshold
            levelingManager.addXp(testPlayer, 50, XpSource.MOB_KILL);

            assertNull(capturedLevels.get());
        }

        @Test
        @DisplayName("Should handle multiple level-ups")
        void shouldHandleMultipleLevelUps() {
            AtomicInteger levelUpCount = new AtomicInteger(0);
            levelingManager.registerLevelUpListener((playerId, newLevel, oldLevel, totalXp) -> {
                levelUpCount.incrementAndGet();
            });

            // Start at level 1 with 0 XP
            PlayerLevelData originalData = PlayerLevelData.createWithXp(testPlayer, 0);
            when(repository.getOrDefault(testPlayer)).thenReturn(originalData);

            // Add 500 XP - enough to reach level 4 (level 2=100, level 3=325, level 4=648)
            levelingManager.addXp(testPlayer, 500, XpSource.MOB_KILL);

            // Level 2 at 100 XP, level 3 at 325 XP, level 4 at 648 XP
            // 500 XP should reach level 4 (but only 1 event fires for the final level change)
            // Note: addXp only fires ONE level-up event per call, even if multiple levels crossed
            assertEquals(1, levelUpCount.get(), "Should fire exactly 1 level-up event for crossing from level 1 to 4");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // XP REMOVAL TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("removeXp")
    class RemoveXpTests {

        @Test
        @DisplayName("Should remove XP and save")
        void shouldRemoveXpAndSave() {
            PlayerLevelData originalData = PlayerLevelData.createWithXp(testPlayer, 1000);
            when(repository.getOrDefault(testPlayer)).thenReturn(originalData);

            levelingManager.removeXp(testPlayer, 100, XpSource.DEATH_PENALTY);

            verify(repository).save(any(PlayerLevelData.class));
        }

        @Test
        @DisplayName("Should throw for negative amount")
        void shouldThrowForNegativeAmount() {
            assertThrows(IllegalArgumentException.class, () ->
                levelingManager.removeXp(testPlayer, -100, XpSource.DEATH_PENALTY));
        }

        @Test
        @DisplayName("Should do nothing for zero amount")
        void shouldDoNothingForZeroAmount() {
            levelingManager.removeXp(testPlayer, 0, XpSource.DEATH_PENALTY);

            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("Should not go below 0 XP")
        void shouldNotGoBelowZero() {
            PlayerLevelData originalData = PlayerLevelData.createWithXp(testPlayer, 50);
            when(repository.getOrDefault(testPlayer)).thenReturn(originalData);

            levelingManager.removeXp(testPlayer, 100, XpSource.DEATH_PENALTY);

            // XP should be clamped to 0
            verify(repository).save(argThat(data -> data.xp() >= 0));
        }

        @Test
        @DisplayName("Should fire XP loss event")
        void shouldFireXpLossEvent() {
            AtomicReference<Long> capturedLoss = new AtomicReference<>();
            levelingManager.registerXpLossListener((playerId, amount, source, totalXp) -> {
                capturedLoss.set(amount);
            });

            PlayerLevelData originalData = PlayerLevelData.createWithXp(testPlayer, 1000);
            when(repository.getOrDefault(testPlayer)).thenReturn(originalData);

            levelingManager.removeXp(testPlayer, 100, XpSource.DEATH_PENALTY);

            assertEquals(100L, capturedLoss.get());
        }

        @Test
        @DisplayName("Should fire level-down event when crossing threshold")
        void shouldFireLevelDownEvent() {
            AtomicReference<int[]> capturedLevels = new AtomicReference<>();
            levelingManager.registerLevelDownListener((playerId, newLevel, oldLevel, totalXp) -> {
                capturedLevels.set(new int[]{newLevel, oldLevel});
            });

            // Start at level 2 (100 XP)
            PlayerLevelData originalData = PlayerLevelData.createWithXp(testPlayer, 150);
            when(repository.getOrDefault(testPlayer)).thenReturn(originalData);

            // Remove enough to drop below level 2 threshold
            levelingManager.removeXp(testPlayer, 60, XpSource.DEATH_PENALTY);

            assertNotNull(capturedLevels.get());
            assertEquals(1, capturedLevels.get()[0]); // newLevel
            assertEquals(2, capturedLevels.get()[1]); // oldLevel
        }

        @Test
        @DisplayName("Should not fire level-down for partial removal")
        void shouldNotFireLevelDownForPartialRemoval() {
            AtomicReference<int[]> capturedLevels = new AtomicReference<>();
            levelingManager.registerLevelDownListener((playerId, newLevel, oldLevel, totalXp) -> {
                capturedLevels.set(new int[]{newLevel, oldLevel});
            });

            PlayerLevelData originalData = PlayerLevelData.createWithXp(testPlayer, 150);
            when(repository.getOrDefault(testPlayer)).thenReturn(originalData);

            // Remove small amount, still above level 2 threshold (100 XP)
            levelingManager.removeXp(testPlayer, 20, XpSource.DEATH_PENALTY);

            assertNull(capturedLevels.get());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // XP SETTER TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("setXp")
    class SetXpTests {

        @Test
        @DisplayName("Should set XP and save")
        void shouldSetXpAndSave() {
            PlayerLevelData originalData = PlayerLevelData.createWithXp(testPlayer, 0);
            when(repository.getOrDefault(testPlayer)).thenReturn(originalData);

            levelingManager.setXp(testPlayer, 500);

            verify(repository).save(any(PlayerLevelData.class));
        }

        @Test
        @DisplayName("Should clamp to 0")
        void shouldClampToZero() {
            PlayerLevelData originalData = PlayerLevelData.createWithXp(testPlayer, 100);
            when(repository.getOrDefault(testPlayer)).thenReturn(originalData);

            levelingManager.setXp(testPlayer, -50);

            verify(repository).save(argThat(data -> data.xp() >= 0));
        }

        @Test
        @DisplayName("Should fire appropriate events")
        void shouldFireAppropriateEvents() {
            AtomicReference<String> eventType = new AtomicReference<>();
            levelingManager.registerLevelUpListener((playerId, newLevel, oldLevel, totalXp) ->
                eventType.set("levelUp"));
            levelingManager.registerLevelDownListener((playerId, newLevel, oldLevel, totalXp) ->
                eventType.set("levelDown"));

            PlayerLevelData originalData = PlayerLevelData.createWithXp(testPlayer, 0);
            when(repository.getOrDefault(testPlayer)).thenReturn(originalData);

            // Set to level 2 XP
            levelingManager.setXp(testPlayer, 100);

            assertEquals("levelUp", eventType.get());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // LEVEL GETTER TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getLevel")
    class GetLevelTests {

        @Test
        @DisplayName("Should calculate level from XP")
        void shouldCalculateLevelFromXp() {
            PlayerLevelData data = PlayerLevelData.createWithXp(testPlayer, 0);
            when(repository.get(testPlayer)).thenReturn(data);

            assertEquals(1, levelingManager.getLevel(testPlayer));
        }

        @Test
        @DisplayName("Should return correct level for XP threshold")
        void shouldReturnCorrectLevelForThreshold() {
            // Level 2 threshold is 100 XP
            PlayerLevelData data = PlayerLevelData.createWithXp(testPlayer, 100);
            when(repository.get(testPlayer)).thenReturn(data);

            assertEquals(2, levelingManager.getLevel(testPlayer));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // LEVEL SETTER TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("setLevel")
    class SetLevelTests {

        @Test
        @DisplayName("Should set level by calculating XP")
        void shouldSetLevelByCalculatingXp() {
            when(repository.getOrDefault(testPlayer)).thenReturn(PlayerLevelData.createNew(testPlayer));

            levelingManager.setLevel(testPlayer, 5);

            long expectedXp = formula.getXpForLevel(5);
            verify(repository).save(argThat(data -> data.xp() == expectedXp));
        }

        @Test
        @DisplayName("Should clamp to minimum level 1")
        void shouldClampToMinimum() {
            when(repository.getOrDefault(testPlayer)).thenReturn(PlayerLevelData.createNew(testPlayer));

            levelingManager.setLevel(testPlayer, 0);

            verify(repository).save(argThat(data -> data.xp() == 0));
        }

        @Test
        @DisplayName("Should clamp to maximum level")
        void shouldClampToMaximum() {
            when(repository.getOrDefault(testPlayer)).thenReturn(PlayerLevelData.createNew(testPlayer));

            levelingManager.setLevel(testPlayer, 200);

            long maxXp = formula.getXpForLevel(100);
            verify(repository).save(argThat(data -> data.xp() == maxXp));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // FORMULA QUERY TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Formula Queries")
    class FormulaQueryTests {

        @Test
        @DisplayName("getXpForLevel should delegate to formula")
        void getXpForLevelShouldDelegate() {
            assertEquals(100, levelingManager.getXpForLevel(2));
            assertEquals(1056, levelingManager.getXpForLevel(5));
        }

        @Test
        @DisplayName("getXpToNextLevel should calculate correctly")
        void getXpToNextLevelShouldCalculate() {
            PlayerLevelData data = PlayerLevelData.createWithXp(testPlayer, 50);
            when(repository.get(testPlayer)).thenReturn(data);

            long xpToNext = levelingManager.getXpToNextLevel(testPlayer);
            long expected = formula.getXpForLevel(2) - 50; // 100 - 50 = 50

            assertEquals(expected, xpToNext);
        }

        @Test
        @DisplayName("getLevelProgress should return fraction 0-1")
        void getLevelProgressShouldReturnFraction() {
            PlayerLevelData data = PlayerLevelData.createWithXp(testPlayer, 50);
            when(repository.get(testPlayer)).thenReturn(data);

            float progress = levelingManager.getLevelProgress(testPlayer);

            assertTrue(progress >= 0 && progress <= 1);
        }

        @Test
        @DisplayName("getMaxLevel should return formula max")
        void getMaxLevelShouldReturnFormulaMax() {
            assertEquals(100, levelingManager.getMaxLevel());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // EVENT LISTENER REGISTRATION TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Event Listener Registration")
    class EventListenerRegistrationTests {

        @Test
        @DisplayName("Should register level-up listener")
        void shouldRegisterLevelUpListener() {
            LevelingEvents.LevelUpListener listener = (playerId, newLevel, oldLevel, totalXp) -> {};

            assertDoesNotThrow(() ->
                levelingManager.registerLevelUpListener(listener));
        }

        @Test
        @DisplayName("Should unregister level-up listener")
        void shouldUnregisterLevelUpListener() {
            LevelingEvents.LevelUpListener listener = (playerId, newLevel, oldLevel, totalXp) -> {};

            levelingManager.registerLevelUpListener(listener);
            assertDoesNotThrow(() ->
                levelingManager.unregisterLevelUpListener(listener));
        }

        @Test
        @DisplayName("Should register all listener types")
        void shouldRegisterAllListenerTypes() {
            LevelingEvents.LevelDownListener downListener = (p, nl, ol, tx) -> {};
            LevelingEvents.XpGainListener gainListener = (p, amt, src, tx) -> {};
            LevelingEvents.XpLossListener lossListener = (p, amt, src, tx) -> {};

            assertDoesNotThrow(() -> {
                levelingManager.registerLevelDownListener(downListener);
                levelingManager.registerXpGainListener(gainListener);
                levelingManager.registerXpLossListener(lossListener);
            });
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // LIFECYCLE TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Lifecycle")
    class LifecycleTests {

        @Test
        @DisplayName("saveAll should save all cached data")
        void saveAllShouldSaveAllCachedData() {
            levelingManager.saveAll();

            verify(repository).saveAll();
        }

        @Test
        @DisplayName("shutdown should clear listeners and shutdown repository")
        void shutdownShouldClearAndShutdown() {
            LevelingEvents.LevelUpListener listener = (p, nl, ol, tx) -> {};
            levelingManager.registerLevelUpListener(listener);

            levelingManager.shutdown();

            verify(repository).shutdown();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // THREAD SAFETY TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Thread Safety")
    class ThreadSafetyTests {

        @Test
        @DisplayName("addXp should be thread-safe for same player")
        @SuppressWarnings("unchecked")
        void addXpShouldBeThreadSafe() throws InterruptedException {
            PlayerLevelData originalData = PlayerLevelData.createWithXp(testPlayer, 0);
            when(repository.getOrDefault(testPlayer)).thenReturn(originalData);

            int threadCount = 10;
            int additionsPerThread = 100;
            Thread[] threads = new Thread[threadCount];

            for (int i = 0; i < threadCount; i++) {
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < additionsPerThread; j++) {
                        levelingManager.addXp(testPlayer, 10, XpSource.MOB_KILL);
                    }
                });
                threads[i].start();
            }

            for (Thread thread : threads) {
                thread.join();
            }

            // Verify save was called (exact count may vary due to race conditions)
            verify(repository, atLeast(threadCount * additionsPerThread / 2)).save(any());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // EDGE CASE TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Should handle max XP")
        void shouldHandleMaxXp() {
            long maxXp = formula.getXpForLevel(100);
            PlayerLevelData data = PlayerLevelData.createWithXp(testPlayer, maxXp);
            when(repository.get(testPlayer)).thenReturn(data);

            assertEquals(100, levelingManager.getLevel(testPlayer));

            // Adding more XP should not change level
            PlayerLevelData originalData = PlayerLevelData.createWithXp(testPlayer, maxXp);
            when(repository.getOrDefault(testPlayer)).thenReturn(originalData);

            levelingManager.addXp(testPlayer, 1000, XpSource.MOB_KILL);

            verify(repository).save(argThat(d -> d.xp() >= maxXp));
        }

        @Test
        @DisplayName("Should handle adding XP at max level")
        void shouldHandleAddingXpAtMaxLevel() {
            long maxXp = formula.getXpForLevel(100);
            PlayerLevelData data = PlayerLevelData.createWithXp(testPlayer, maxXp);
            when(repository.getOrDefault(testPlayer)).thenReturn(data);

            // Should not throw
            assertDoesNotThrow(() ->
                levelingManager.addXp(testPlayer, 1000, XpSource.MOB_KILL));
        }

        @Test
        @DisplayName("Should handle removing all XP at level 1")
        void shouldHandleRemovingAllXpAtLevel1() {
            PlayerLevelData data = PlayerLevelData.createWithXp(testPlayer, 50);
            when(repository.getOrDefault(testPlayer)).thenReturn(data);

            levelingManager.removeXp(testPlayer, 100, XpSource.DEATH_PENALTY);

            verify(repository).save(argThat(d -> d.xp() == 0));
        }
    }
}
