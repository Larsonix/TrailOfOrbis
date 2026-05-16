package io.github.larsonix.trailoforbis.leveling.core;

import io.github.larsonix.trailoforbis.leveling.config.LevelingConfig;
import io.github.larsonix.trailoforbis.leveling.formula.ExponentialFormula;
import io.github.larsonix.trailoforbis.leveling.repository.LevelingRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Edge case tests for LevelingManager.protectLevel() — the level protection
 * migration logic invoked during loadPlayer().
 *
 * <p>These complement the main LevelProtectionTests in LevelingManagerTest
 * by targeting boundary conditions around maxLevel, storedLevel corruption,
 * and the wasXpAdjusted state machine.
 */
@ExtendWith(MockitoExtension.class)
class LevelProtectionEdgeCaseTest {

    @Mock
    private LevelingRepository repository;

    private ExponentialFormula formula;
    private LevelingConfig config;

    private final UUID testPlayer = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        formula = new ExponentialFormula(100, 1.7, 100);
        config = new LevelingConfig();
        config.setEnabled(true);
    }

    // ═══════════════════════════════════════════════════════════════════
    // STORED LEVEL AT MAX LEVEL
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("StoredLevel at MaxLevel")
    class StoredLevelAtMaxLevel {

        @Test
        @DisplayName("storedLevel equals maxLevel, derived below max → should bump XP")
        void storedEqualsMax_derivedBelow_shouldBumpXp() {
            int maxLevel = formula.getMaxLevel(); // 100
            long lowXp = formula.getXpForLevel(80);
            assertTrue(formula.getLevelForXp(lowXp) < maxLevel, "Precondition: derived < maxLevel");

            PlayerLevelData data = new PlayerLevelData(testPlayer, lowXp,
                maxLevel, timestamps().createdAt(), timestamps().lastUpdated());
            when(repository.loadOrCreate(testPlayer)).thenReturn(data);

            LevelingManager mgr = new LevelingManager(repository, formula, null, config);
            PlayerLevelData result = mgr.loadPlayer(testPlayer);

            assertEquals(formula.getXpForLevel(maxLevel), result.xp());
            assertEquals(maxLevel, result.storedLevel());
            assertTrue(mgr.wasXpAdjusted(testPlayer));
        }

        @Test
        @DisplayName("storedLevel equals maxLevel, derived also at max → no change")
        void storedEqualsMax_derivedAlsoMax_shouldNotChange() {
            int maxLevel = formula.getMaxLevel();
            long maxXp = formula.getXpForLevel(maxLevel);

            PlayerLevelData data = new PlayerLevelData(testPlayer, maxXp,
                maxLevel, timestamps().createdAt(), timestamps().lastUpdated());
            when(repository.loadOrCreate(testPlayer)).thenReturn(data);

            LevelingManager mgr = new LevelingManager(repository, formula, null, config);
            PlayerLevelData result = mgr.loadPlayer(testPlayer);

            assertEquals(maxXp, result.xp());
            assertEquals(maxLevel, result.storedLevel());
            assertFalse(mgr.wasXpAdjusted(testPlayer));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // STORED LEVEL ABOVE MAX LEVEL (MAX REDUCED)
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("StoredLevel Above MaxLevel (Max Reduced)")
    class StoredLevelAboveMaxLevel {

        @Test
        @DisplayName("storedLevel 120 with maxLevel 100, derived at 80 → clamp and protect")
        void storedAboveMax_derivedBelow_shouldClampAndProtect() {
            int storedLevel = 120;
            long lowXp = formula.getXpForLevel(80);

            PlayerLevelData data = new PlayerLevelData(testPlayer, lowXp,
                storedLevel, timestamps().createdAt(), timestamps().lastUpdated());
            when(repository.loadOrCreate(testPlayer)).thenReturn(data);

            LevelingManager mgr = new LevelingManager(repository, formula, null, config);
            PlayerLevelData result = mgr.loadPlayer(testPlayer);

            // clampedLevel = min(120, 100) = 100; 100 > 80 → bump
            assertEquals(formula.getXpForLevel(100), result.xp());
            assertEquals(100, result.storedLevel());
            assertTrue(mgr.wasXpAdjusted(testPlayer));
        }

        @Test
        @DisplayName("storedLevel 120 with maxLevel 100, derived already at 100 → just update stored")
        void storedAboveMax_derivedAtMax_shouldJustUpdateStored() {
            int storedLevel = 120;
            long maxXp = formula.getXpForLevel(100);

            PlayerLevelData data = new PlayerLevelData(testPlayer, maxXp,
                storedLevel, timestamps().createdAt(), timestamps().lastUpdated());
            when(repository.loadOrCreate(testPlayer)).thenReturn(data);

            LevelingManager mgr = new LevelingManager(repository, formula, null, config);
            PlayerLevelData result = mgr.loadPlayer(testPlayer);

            // clampedLevel = 100, derived = 100, wasClamped = true → update storedLevel
            assertEquals(maxXp, result.xp()); // XP unchanged
            assertEquals(100, result.storedLevel()); // Updated from 120 to 100
            assertFalse(mgr.wasXpAdjusted(testPlayer));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ZERO XP WITH NON-NULL STORED LEVEL
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Zero XP with NonNull StoredLevel")
    class ZeroXpWithStoredLevel {

        @Test
        @DisplayName("XP = 0, storedLevel = 5 → should bump to level 5 XP")
        void zeroXp_storedLevel5_shouldBump() {
            PlayerLevelData data = new PlayerLevelData(testPlayer, 0,
                5, timestamps().createdAt(), timestamps().lastUpdated());
            when(repository.loadOrCreate(testPlayer)).thenReturn(data);

            LevelingManager mgr = new LevelingManager(repository, formula, null, config);
            PlayerLevelData result = mgr.loadPlayer(testPlayer);

            assertEquals(formula.getXpForLevel(5), result.xp());
            assertEquals(5, result.storedLevel());
            assertTrue(mgr.wasXpAdjusted(testPlayer));
        }

        @Test
        @DisplayName("XP = 0, storedLevel = 1 → no change needed")
        void zeroXp_storedLevel1_shouldNotChange() {
            PlayerLevelData data = new PlayerLevelData(testPlayer, 0,
                1, timestamps().createdAt(), timestamps().lastUpdated());
            when(repository.loadOrCreate(testPlayer)).thenReturn(data);

            LevelingManager mgr = new LevelingManager(repository, formula, null, config);
            PlayerLevelData result = mgr.loadPlayer(testPlayer);

            assertEquals(0, result.xp());
            assertEquals(1, result.storedLevel());
            assertFalse(mgr.wasXpAdjusted(testPlayer));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // BOOTSTRAP WITH EXTREME FORMULAS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Bootstrap with Extreme Formulas")
    class BootstrapExtremes {

        @Test
        @DisplayName("Identical old and new formula → just stamp level")
        void identicalFormulas_shouldJustStamp() {
            // Same formula for both: no level difference possible
            ExponentialFormula sameFormula = new ExponentialFormula(100, 1.7, 100);
            LevelingManager mgr = new LevelingManager(repository, formula, sameFormula, config);

            long testXp = 500;
            PlayerLevelData data = PlayerLevelData.createWithXp(testPlayer, testXp);
            when(repository.loadOrCreate(testPlayer)).thenReturn(data);

            PlayerLevelData result = mgr.loadPlayer(testPlayer);

            assertEquals(testXp, result.xp());
            assertEquals(formula.getLevelForXp(testXp), result.storedLevel());
            assertFalse(mgr.wasXpAdjusted(testPlayer));
        }

        @Test
        @DisplayName("Previous formula maxLevel exceeds current → clamp old level")
        void previousMaxExceedsCurrent_shouldClamp() {
            // Old formula maxLevel=200, current=100. Enough XP for level 150 under old formula.
            ExponentialFormula oldFormula = new ExponentialFormula(10, 1.5, 200);
            long hugeXp = oldFormula.getXpForLevel(150);
            int oldLevel = oldFormula.getLevelForXp(hugeXp);
            assertTrue(oldLevel > formula.getMaxLevel(), "Precondition: old level > current max");

            LevelingManager mgr = new LevelingManager(repository, formula, oldFormula, config);
            PlayerLevelData data = PlayerLevelData.createWithXp(testPlayer, hugeXp);
            when(repository.loadOrCreate(testPlayer)).thenReturn(data);

            PlayerLevelData result = mgr.loadPlayer(testPlayer);

            assertTrue(result.storedLevel() <= formula.getMaxLevel(),
                "Stored level must be clamped to current maxLevel");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // WAS XP ADJUSTED STATE MACHINE
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("wasXpAdjusted State Machine")
    class WasXpAdjustedStateMachine {

        @Test
        @DisplayName("Before any loadPlayer should return false")
        void beforeLoad_shouldReturnFalse() {
            LevelingManager mgr = new LevelingManager(repository, formula, null, config);
            assertFalse(mgr.wasXpAdjusted(testPlayer));
        }

        @Test
        @DisplayName("Load → unload → reload with same stale XP → flag set again")
        void reloadWithStaleXp_shouldFlagAgain() {
            int storedLevel = 10;
            long lowXp = formula.getXpForLevel(5);

            PlayerLevelData data = new PlayerLevelData(testPlayer, lowXp,
                storedLevel, timestamps().createdAt(), timestamps().lastUpdated());

            // First load: protection fires
            when(repository.loadOrCreate(testPlayer)).thenReturn(data);
            LevelingManager mgr = new LevelingManager(repository, formula, null, config);
            mgr.loadPlayer(testPlayer);
            assertTrue(mgr.wasXpAdjusted(testPlayer), "First load should flag adjustment");
            assertFalse(mgr.wasXpAdjusted(testPlayer), "Flag consumed");

            // Unload
            when(repository.get(testPlayer)).thenReturn(data);
            mgr.unloadPlayer(testPlayer);
            assertFalse(mgr.wasXpAdjusted(testPlayer), "Cleared on unload");

            // Reload with same stale data (simulating DB returning old data)
            when(repository.loadOrCreate(testPlayer)).thenReturn(data);
            mgr.loadPlayer(testPlayer);
            assertTrue(mgr.wasXpAdjusted(testPlayer), "Second load should flag again");
        }
    }

    private PlayerLevelData timestamps() {
        return PlayerLevelData.createNew(UUID.randomUUID());
    }
}
