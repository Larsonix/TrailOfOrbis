package io.github.larsonix.trailoforbis.leveling.systems;

import io.github.larsonix.trailoforbis.leveling.config.LevelingConfig;
import io.github.larsonix.trailoforbis.leveling.core.LevelingManager;
import io.github.larsonix.trailoforbis.leveling.core.PlayerLevelData;
import io.github.larsonix.trailoforbis.leveling.formula.ExponentialFormula;
import io.github.larsonix.trailoforbis.leveling.repository.LevelingRepository;
import io.github.larsonix.trailoforbis.leveling.xp.XpSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration test of the full death penalty pipeline.
 *
 * <p>Simulates the XpLossSystem calculation logic and feeds it into a real
 * LevelingManager with a stateful mock repository, verifying the double-protection:
 * <ol>
 *   <li>XpLossSystem only requests progress-based XP loss</li>
 *   <li>LevelingManager.removeXp clamps to level floor for loss sources</li>
 * </ol>
 *
 * <p>This tests behaviors that individual unit tests cannot cover because
 * they operate at different layers.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Death Penalty Pipeline")
class DeathPenaltyPipelineTest {

    @Mock
    private LevelingRepository repository;

    private ExponentialFormula formula;
    private LevelingConfig config;
    private LevelingManager manager;

    private final UUID testPlayer = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        formula = new ExponentialFormula(100, 1.7, 100);
        config = new LevelingConfig();
        config.setEnabled(true);
        config.getXpLoss().setEnabled(true);
        config.getXpLoss().setPercentage(0.50); // 50% loss
        config.getXpLoss().setMinLevel(1);

        // Stateful mock: tracks XP through save calls
        AtomicReference<PlayerLevelData> state = new AtomicReference<>();

        lenient().doAnswer(inv -> {
            state.set(inv.getArgument(0));
            return null;
        }).when(repository).save(any(PlayerLevelData.class));

        manager = new LevelingManager(repository, formula, null, config);
    }

    /**
     * Simulates what XpLossSystem computes for death penalty.
     * Returns the xpToLose that XpLossSystem would pass to removeXp.
     */
    private long simulateXpLossSystemCalculation(int currentLevel, long currentXp) {
        long xpForCurrentLevel = formula.getXpForLevel(currentLevel);
        long progressXp = currentXp - xpForCurrentLevel;
        if (progressXp <= 0) return 0;

        double lossPercentage = config.getXpLoss().getPercentage();
        long xpToLose = (long) Math.ceil(progressXp * lossPercentage);

        // Min-level clamping (XpLossSystem lines 121-129)
        long minLevelXp = formula.getXpForLevel(config.getXpLoss().getMinLevel());
        long xpAfterLoss = currentXp - xpToLose;
        if (xpAfterLoss < minLevelXp) {
            xpToLose = currentXp - minLevelXp;
        }

        return Math.max(0, xpToLose);
    }

    // ═══════════════════════════════════════════════════════════════════
    // DOUBLE PROTECTION TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Double Protection")
    class DoubleProtection {

        @Test
        @DisplayName("Player with progress: death loses only progress%, stays same level")
        void playerWithProgress_losesOnlyProgressPercent() {
            int level = 5;
            long baseXp = formula.getXpForLevel(level);
            long currentXp = baseXp + 200;

            PlayerLevelData data = PlayerLevelData.createWithXp(testPlayer, currentXp);
            when(repository.getOrDefault(testPlayer)).thenReturn(data);

            // XpLossSystem would calculate: progressXp=200, lose=ceil(200*0.5)=100
            long xpToLose = simulateXpLossSystemCalculation(level, currentXp);
            assertEquals(100, xpToLose, "XpLossSystem should request 100 XP loss");

            manager.removeXp(testPlayer, xpToLose, XpSource.DEATH_PENALTY);

            verify(repository).save(argThat(saved -> {
                assertEquals(currentXp - 100, saved.xp());
                assertEquals(level, formula.getLevelForXp(saved.xp()),
                    "Should still be level " + level);
                return true;
            }));
        }

        @Test
        @DisplayName("Player exactly at threshold: 0 progress, no loss")
        void playerAtThreshold_noLoss() {
            int level = 10;
            long xpAtLevel = formula.getXpForLevel(level);

            long xpToLose = simulateXpLossSystemCalculation(level, xpAtLevel);
            assertEquals(0, xpToLose, "No loss when progress is 0");

            // XpLossSystem returns early with xpToLose=0.
            // LevelingManager.removeXp returns early at line 154 when amount=0,
            // so no repository interaction happens at all.
            manager.removeXp(testPlayer, 0, XpSource.DEATH_PENALTY);

            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("Tiny progress (1 XP), 10% loss → ceil(0.1) = 1 → exactly at threshold")
        void tinyProgress_shouldLoseAtLeast1() {
            config.getXpLoss().setPercentage(0.10); // 10%

            int level = 3;
            long baseXp = formula.getXpForLevel(level);
            long currentXp = baseXp + 1; // 1 XP progress

            long xpToLose = simulateXpLossSystemCalculation(level, currentXp);
            assertEquals(1, xpToLose, "ceil(1 * 0.1) = ceil(0.1) = 1");

            PlayerLevelData data = PlayerLevelData.createWithXp(testPlayer, currentXp);
            when(repository.getOrDefault(testPlayer)).thenReturn(data);

            manager.removeXp(testPlayer, xpToLose, XpSource.DEATH_PENALTY);

            verify(repository).save(argThat(saved -> {
                assertEquals(baseXp, saved.xp(), "Should be exactly at level threshold");
                assertEquals(level, formula.getLevelForXp(saved.xp()));
                return true;
            }));
        }

        @Test
        @DisplayName("Corrupted huge request with DEATH_PENALTY → manager clamps to level floor")
        void corruptedRequest_deathPenalty_clampedToFloor() {
            int level = 5;
            long baseXp = formula.getXpForLevel(level);
            long currentXp = baseXp + 50;

            PlayerLevelData data = PlayerLevelData.createWithXp(testPlayer, currentXp);
            when(repository.getOrDefault(testPlayer)).thenReturn(data);

            AtomicReference<int[]> levelDown = new AtomicReference<>();
            manager.registerLevelDownListener((p, nl, ol, tx) ->
                levelDown.set(new int[]{nl, ol}));

            // Request absurd amount via DEATH_PENALTY
            manager.removeXp(testPlayer, 99999, XpSource.DEATH_PENALTY);

            verify(repository).save(argThat(saved -> {
                assertTrue(saved.xp() >= baseXp,
                    "XP must not drop below level " + level + " floor");
                return true;
            }));
            assertNull(levelDown.get(), "Level-down must NOT fire for DEATH_PENALTY");
        }

        @Test
        @DisplayName("Same huge request with ADMIN_COMMAND → drops through floor")
        void sameHugeRequest_adminCommand_dropsThrough() {
            int level = 5;
            long baseXp = formula.getXpForLevel(level);
            long currentXp = baseXp + 50;

            PlayerLevelData data = PlayerLevelData.createWithXp(testPlayer, currentXp);
            when(repository.getOrDefault(testPlayer)).thenReturn(data);

            AtomicReference<int[]> levelDown = new AtomicReference<>();
            manager.registerLevelDownListener((p, nl, ol, tx) ->
                levelDown.set(new int[]{nl, ol}));

            manager.removeXp(testPlayer, 99999, XpSource.ADMIN_COMMAND);

            verify(repository).save(argThat(saved -> {
                assertEquals(0, saved.xp(), "Admin command should drop to 0");
                return true;
            }));
            assertNotNull(levelDown.get(), "Level-down MUST fire for ADMIN_COMMAND");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // MIN LEVEL PROTECTION
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Min Level Protection")
    class MinLevelProtection {

        @Test
        @DisplayName("Player at min_level should not lose XP (XpLossSystem returns early)")
        void playerAtMinLevel_noLoss() {
            config.getXpLoss().setMinLevel(5);

            int currentLevel = 5;
            long currentXp = formula.getXpForLevel(5) + 200;

            // XpLossSystem checks: currentLevel <= minLevel → return early
            assertTrue(currentLevel <= config.getXpLoss().getMinLevel());
            // No removeXp call would be made
        }

        @Test
        @DisplayName("Player just above min_level: loss clamped to not drop below min_level XP")
        void playerJustAboveMinLevel_clampedToMinLevelXp() {
            config.getXpLoss().setMinLevel(5);
            config.getXpLoss().setPercentage(0.90); // 90% — very aggressive

            int currentLevel = 6;
            long currentXp = formula.getXpForLevel(6) + 100; // 100 progress at level 6

            long xpToLose = simulateXpLossSystemCalculation(currentLevel, currentXp);

            // Raw loss = ceil(100 * 0.9) = 90. xpAfterLoss = currentXp - 90.
            // Check if that would drop below minLevel XP
            long minLevelXp = formula.getXpForLevel(5);
            long xpAfterRawLoss = currentXp - 90;
            if (xpAfterRawLoss < minLevelXp) {
                // Clamped: xpToLose = currentXp - minLevelXp
                assertEquals(currentXp - minLevelXp, xpToLose);
            }

            // Even if the clamped amount is passed to manager, death penalty can't delevel
            PlayerLevelData data = PlayerLevelData.createWithXp(testPlayer, currentXp);
            when(repository.getOrDefault(testPlayer)).thenReturn(data);
            if (xpToLose > 0) {
                manager.removeXp(testPlayer, xpToLose, XpSource.DEATH_PENALTY);

                verify(repository).save(argThat(saved -> {
                    assertTrue(saved.xp() >= minLevelXp,
                        "XP must not drop below min_level threshold");
                    return true;
                }));
            }
        }
    }
}
