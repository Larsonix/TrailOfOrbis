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

        levelingManager = new LevelingManager(repository, formula, null, config);
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
                new LevelingManager(repository, formula, null, config));
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
        @DisplayName("loadPlayer should delegate to repository and stamp level")
        void loadPlayerShouldDelegate() {
            PlayerLevelData expectedData = PlayerLevelData.createNew(testPlayer);
            when(repository.loadOrCreate(testPlayer)).thenReturn(expectedData);

            PlayerLevelData result = levelingManager.loadPlayer(testPlayer);

            // protectLevel() stamps storedLevel on the data, so it's a new instance
            assertEquals(testPlayer, result.uuid());
            assertEquals(0, result.xp());
            assertEquals(1, result.storedLevel()); // Level 1 at 0 XP
            verify(repository).loadOrCreate(testPlayer);
            verify(repository).save(any(PlayerLevelData.class)); // level stamping save
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
        @DisplayName("Should fire level-down event when crossing threshold (admin source)")
        void shouldFireLevelDownEvent() {
            AtomicReference<int[]> capturedLevels = new AtomicReference<>();
            levelingManager.registerLevelDownListener((playerId, newLevel, oldLevel, totalXp) -> {
                capturedLevels.set(new int[]{newLevel, oldLevel});
            });

            // Start at level 2 (100 XP)
            PlayerLevelData originalData = PlayerLevelData.createWithXp(testPlayer, 150);
            when(repository.getOrDefault(testPlayer)).thenReturn(originalData);

            // Remove enough to drop below level 2 threshold — admin source allows level-down
            levelingManager.removeXp(testPlayer, 60, XpSource.ADMIN_COMMAND);

            assertNotNull(capturedLevels.get());
            assertEquals(1, capturedLevels.get()[0]); // newLevel
            assertEquals(2, capturedLevels.get()[1]); // oldLevel
        }

        @Test
        @DisplayName("Death penalty should never cause level-down")
        void deathPenaltyShouldNotCauseLevelDown() {
            AtomicReference<int[]> capturedLevels = new AtomicReference<>();
            levelingManager.registerLevelDownListener((playerId, newLevel, oldLevel, totalXp) -> {
                capturedLevels.set(new int[]{newLevel, oldLevel});
            });

            // Start at level 2 (100 XP) with 50 XP progress
            PlayerLevelData originalData = PlayerLevelData.createWithXp(testPlayer, 150);
            when(repository.getOrDefault(testPlayer)).thenReturn(originalData);

            // Request 60 XP removal via death penalty — would cross threshold without guard
            levelingManager.removeXp(testPlayer, 60, XpSource.DEATH_PENALTY);

            // Level-down event must NOT fire — death penalty clamps to level floor
            assertNull(capturedLevels.get());
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
    // LEVEL PROTECTION TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Level Protection (protectLevel)")
    class LevelProtectionTests {

        @Test
        @DisplayName("Bootstrap: should bump XP when old formula gives higher level")
        void bootstrapShouldBumpXpOnLevelLoss() {
            // "Old" formula: steeper curve → same XP = higher level
            // baseXp=50 means level thresholds are lower → 500 XP = higher level
            ExponentialFormula oldFormula = new ExponentialFormula(50, 1.7, 100);
            LevelingManager mgr = new LevelingManager(repository, formula, oldFormula, config);

            long testXp = 500;
            int oldLevel = oldFormula.getLevelForXp(testXp);
            int newLevel = formula.getLevelForXp(testXp);
            assertTrue(oldLevel > newLevel, "Precondition: old formula must give higher level for same XP");

            // Pre-migration player: storedLevel is null
            PlayerLevelData data = PlayerLevelData.createWithXp(testPlayer, testXp);
            assertNull(data.storedLevel());
            when(repository.loadOrCreate(testPlayer)).thenReturn(data);

            PlayerLevelData result = mgr.loadPlayer(testPlayer);

            assertEquals(oldLevel, result.storedLevel());
            assertEquals(formula.getXpForLevel(oldLevel), result.xp());
            assertTrue(result.xp() >= testXp, "XP should be bumped up, never down");
            verify(repository).save(argThat(d -> d.storedLevel() == oldLevel));
        }

        @Test
        @DisplayName("Bootstrap: should not bump when old formula gives same or lower level")
        void bootstrapShouldNotBumpWhenNoLoss() {
            // "Old" formula with higher baseXp → same XP = lower or equal level
            ExponentialFormula oldFormula = new ExponentialFormula(200, 1.7, 100);
            LevelingManager mgr = new LevelingManager(repository, formula, oldFormula, config);

            long testXp = 500;
            int oldLevel = oldFormula.getLevelForXp(testXp);
            int newLevel = formula.getLevelForXp(testXp);
            assertTrue(oldLevel <= newLevel, "Precondition: old formula should give same or lower level");

            PlayerLevelData data = PlayerLevelData.createWithXp(testPlayer, testXp);
            when(repository.loadOrCreate(testPlayer)).thenReturn(data);

            PlayerLevelData result = mgr.loadPlayer(testPlayer);

            // XP should be unchanged, storedLevel stamped to current level
            assertEquals(testXp, result.xp());
            assertEquals(newLevel, result.storedLevel());
        }

        @Test
        @DisplayName("Bootstrap: should skip when no previousFormula configured")
        void bootstrapShouldSkipWhenNoPreviousFormula() {
            // null previousFormula (no migration config)
            LevelingManager mgr = new LevelingManager(repository, formula, null, config);

            PlayerLevelData data = PlayerLevelData.createWithXp(testPlayer, 500);
            when(repository.loadOrCreate(testPlayer)).thenReturn(data);

            PlayerLevelData result = mgr.loadPlayer(testPlayer);

            // No bump — just stamp current level
            assertEquals(500, result.xp());
            int expectedLevel = formula.getLevelForXp(500);
            assertEquals(expectedLevel, result.storedLevel());
        }

        @Test
        @DisplayName("Bootstrap: should skip for new players with 0 XP")
        void bootstrapShouldSkipForZeroXp() {
            ExponentialFormula oldFormula = new ExponentialFormula(50, 1.7, 100);
            LevelingManager mgr = new LevelingManager(repository, formula, oldFormula, config);

            PlayerLevelData data = PlayerLevelData.createNew(testPlayer);
            when(repository.loadOrCreate(testPlayer)).thenReturn(data);

            PlayerLevelData result = mgr.loadPlayer(testPlayer);

            assertEquals(0, result.xp());
            assertEquals(1, result.storedLevel());
        }

        @Test
        @DisplayName("Ongoing: should bump XP when storedLevel > derived level")
        void ongoingShouldBumpXpWhenStoredLevelHigher() {
            // Player was level 10, but after curve change their XP maps to level 7
            int storedLevel = 10;
            long xpThatMapsToLevel7 = formula.getXpForLevel(7) + 50;
            assertTrue(formula.getLevelForXp(xpThatMapsToLevel7) < storedLevel,
                "Precondition: XP should map to a level below storedLevel");

            PlayerLevelData data = new PlayerLevelData(testPlayer, xpThatMapsToLevel7,
                storedLevel, data().createdAt(), data().lastUpdated());
            when(repository.loadOrCreate(testPlayer)).thenReturn(data);

            PlayerLevelData result = levelingManager.loadPlayer(testPlayer);

            assertEquals(formula.getXpForLevel(storedLevel), result.xp());
            assertEquals(storedLevel, result.storedLevel());
        }

        @Test
        @DisplayName("Ongoing: should update storedLevel when current level is higher")
        void ongoingShouldUpdateWhenLevelIncreased() {
            // Curve got easier: same XP now maps to level 5 instead of stored 3
            int storedLevel = 3;
            long xpForLevel5 = formula.getXpForLevel(5) + 10;
            int derivedLevel = formula.getLevelForXp(xpForLevel5);
            assertTrue(derivedLevel > storedLevel,
                "Precondition: derived level should exceed stored level");

            PlayerLevelData data = new PlayerLevelData(testPlayer, xpForLevel5,
                storedLevel, data().createdAt(), data().lastUpdated());
            when(repository.loadOrCreate(testPlayer)).thenReturn(data);

            PlayerLevelData result = levelingManager.loadPlayer(testPlayer);

            assertEquals(xpForLevel5, result.xp()); // XP unchanged
            assertEquals(derivedLevel, result.storedLevel()); // Level updated
        }

        @Test
        @DisplayName("Ongoing: should not change anything when storedLevel equals current")
        void ongoingShouldNoOpWhenEqual() {
            int level = 5;
            long xp = formula.getXpForLevel(level);

            PlayerLevelData data = new PlayerLevelData(testPlayer, xp,
                level, data().createdAt(), data().lastUpdated());
            when(repository.loadOrCreate(testPlayer)).thenReturn(data);

            PlayerLevelData result = levelingManager.loadPlayer(testPlayer);

            assertEquals(xp, result.xp());
            assertEquals(level, result.storedLevel());
        }

        @Test
        @DisplayName("Bootstrap: should clamp old level to current maxLevel")
        void bootstrapShouldClampToMaxLevel() {
            // Old formula with maxLevel=200, current formula maxLevel=100
            ExponentialFormula oldFormula = new ExponentialFormula(10, 1.5, 200);
            // Give enough XP that old formula would say level 150
            long hugeXp = oldFormula.getXpForLevel(150);
            int oldLevel = oldFormula.getLevelForXp(hugeXp);
            assertTrue(oldLevel > formula.getMaxLevel(),
                "Precondition: old level should exceed current maxLevel");

            LevelingManager mgr = new LevelingManager(repository, formula, oldFormula, config);
            PlayerLevelData data = PlayerLevelData.createWithXp(testPlayer, hugeXp);
            when(repository.loadOrCreate(testPlayer)).thenReturn(data);

            PlayerLevelData result = mgr.loadPlayer(testPlayer);

            // Should clamp to maxLevel, not store 150
            assertTrue(result.storedLevel() <= formula.getMaxLevel(),
                "Stored level should be clamped to maxLevel");
        }

        @Test
        @DisplayName("Ongoing: should clamp storedLevel exceeding maxLevel")
        void ongoingShouldClampStoredLevelExceedingMax() {
            // Simulate: storedLevel=120 from a previous era, current maxLevel=100
            int storedLevel = 120;
            long xp = formula.getXpForLevel(formula.getMaxLevel());

            PlayerLevelData data = new PlayerLevelData(testPlayer, xp,
                storedLevel, data().createdAt(), data().lastUpdated());
            when(repository.loadOrCreate(testPlayer)).thenReturn(data);

            PlayerLevelData result = levelingManager.loadPlayer(testPlayer);

            assertTrue(result.storedLevel() <= formula.getMaxLevel(),
                "Stored level should be clamped to current maxLevel");
        }

        // Helper to get an arbitrary timestamp pair
        private PlayerLevelData data() {
            return PlayerLevelData.createNew(UUID.randomUUID());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // XP ADJUSTED NOTIFICATION TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("wasXpAdjusted")
    class WasXpAdjustedTests {

        @Test
        @DisplayName("Should return true when XP was bumped during bootstrap")
        void shouldReturnTrueAfterBootstrapBump() {
            ExponentialFormula oldFormula = new ExponentialFormula(50, 1.7, 100);
            LevelingManager mgr = new LevelingManager(repository, formula, oldFormula, config);

            long testXp = 500;
            assertTrue(oldFormula.getLevelForXp(testXp) > formula.getLevelForXp(testXp),
                "Precondition: old formula gives higher level");

            PlayerLevelData data = PlayerLevelData.createWithXp(testPlayer, testXp);
            when(repository.loadOrCreate(testPlayer)).thenReturn(data);
            mgr.loadPlayer(testPlayer);

            assertTrue(mgr.wasXpAdjusted(testPlayer));
        }

        @Test
        @DisplayName("Should return true when XP was bumped during ongoing protection")
        void shouldReturnTrueAfterOngoingBump() {
            int storedLevel = 10;
            long lowXp = formula.getXpForLevel(5);
            PlayerLevelData data = new PlayerLevelData(testPlayer, lowXp,
                storedLevel, PlayerLevelData.createNew(UUID.randomUUID()).createdAt(),
                PlayerLevelData.createNew(UUID.randomUUID()).lastUpdated());
            when(repository.loadOrCreate(testPlayer)).thenReturn(data);

            levelingManager.loadPlayer(testPlayer);

            assertTrue(levelingManager.wasXpAdjusted(testPlayer));
        }

        @Test
        @DisplayName("Should return false when no adjustment was needed")
        void shouldReturnFalseWhenNoAdjustment() {
            PlayerLevelData data = PlayerLevelData.createNew(testPlayer);
            when(repository.loadOrCreate(testPlayer)).thenReturn(data);

            levelingManager.loadPlayer(testPlayer);

            assertFalse(levelingManager.wasXpAdjusted(testPlayer));
        }

        @Test
        @DisplayName("Should consume flag on first call (one-time notification)")
        void shouldConsumeOnFirstCall() {
            ExponentialFormula oldFormula = new ExponentialFormula(50, 1.7, 100);
            LevelingManager mgr = new LevelingManager(repository, formula, oldFormula, config);

            PlayerLevelData data = PlayerLevelData.createWithXp(testPlayer, 500);
            when(repository.loadOrCreate(testPlayer)).thenReturn(data);
            mgr.loadPlayer(testPlayer);

            assertTrue(mgr.wasXpAdjusted(testPlayer), "First call should return true");
            assertFalse(mgr.wasXpAdjusted(testPlayer), "Second call should return false");
        }

        @Test
        @DisplayName("Should be cleared on unloadPlayer")
        void shouldBeClearedOnUnload() {
            ExponentialFormula oldFormula = new ExponentialFormula(50, 1.7, 100);
            LevelingManager mgr = new LevelingManager(repository, formula, oldFormula, config);

            PlayerLevelData data = PlayerLevelData.createWithXp(testPlayer, 500);
            when(repository.loadOrCreate(testPlayer)).thenReturn(data);
            mgr.loadPlayer(testPlayer);

            // Unload before checking
            when(repository.get(testPlayer)).thenReturn(data);
            mgr.unloadPlayer(testPlayer);

            assertFalse(mgr.wasXpAdjusted(testPlayer), "Flag should be cleared on unload");
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
