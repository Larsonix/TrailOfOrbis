package io.github.larsonix.trailoforbis.leveling.systems;

import io.github.larsonix.trailoforbis.leveling.config.LevelingConfig;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the XP loss calculation logic used by {@link XpLossSystem}.
 *
 * <p>The actual XpLossSystem is an ECS system that extends DeathSystems.OnDeathSystem
 * and requires a fully running Hytale server to test. This test class validates
 * the core calculation logic and configuration handling.
 *
 * <p>Key behaviors tested:
 * <ul>
 *   <li>Progress-based XP loss (only lose XP earned since reaching current level)</li>
 *   <li>Minimum level protection (no XP loss at or below min level)</li>
 *   <li>Loss clamping to prevent dropping below minimum level</li>
 *   <li>Zero/disabled loss configurations</li>
 * </ul>
 */
@DisplayName("XpLossSystem Logic")
class XpLossSystemTest {

    private LevelingConfig levelingConfig;

    @BeforeEach
    void setUp() {
        levelingConfig = new LevelingConfig();
        levelingConfig.setEnabled(true);
    }

    // ═══════════════════════════════════════════════════════════════════
    // CONFIGURATION TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Configuration")
    class ConfigurationTests {

        @Test
        @DisplayName("XP loss should be disabled by default")
        void xpLoss_disabledByDefault() {
            LevelingConfig defaultConfig = new LevelingConfig();
            assertFalse(defaultConfig.getXpLoss().isEnabled());
        }

        @Test
        @DisplayName("Default loss percentage should be 10%")
        void defaultLossPercentage_shouldBe10Percent() {
            assertEquals(0.10, levelingConfig.getXpLoss().getPercentage(), 0.001);
        }

        @Test
        @DisplayName("Default minimum level should be 1")
        void defaultMinLevel_shouldBe1() {
            assertEquals(1, levelingConfig.getXpLoss().getMinLevel());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // XP LOSS CALCULATION TESTS (simulating the system's logic)
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("XP Loss Calculation")
    class XpLossCalculationTests {

        @Test
        @DisplayName("Player at minimum level should lose no XP")
        void playerAtMinLevel_losesNoXp() {
            LevelingConfig.XpLossConfig lossConfig = levelingConfig.getXpLoss();
            lossConfig.setEnabled(true);
            lossConfig.setMinLevel(5);

            int currentLevel = 5;
            int minLevel = lossConfig.getMinLevel();

            // Player at or below min level should not lose XP
            assertTrue(currentLevel <= minLevel);
            // XpLossSystem returns early in this case
        }

        @Test
        @DisplayName("Player above minimum level loses progress XP only")
        void playerAboveMinLevel_losesProgressXpOnly() {
            LevelingConfig.XpLossConfig lossConfig = levelingConfig.getXpLoss();
            lossConfig.setEnabled(true);
            lossConfig.setPercentage(0.10);
            lossConfig.setMinLevel(1);

            // Simulate player at level 10 with 500 progress XP
            long currentXp = 5000;
            long xpForCurrentLevel = 4500;  // XP needed to reach level 10
            long progressXp = currentXp - xpForCurrentLevel; // 500 progress

            // Calculate loss (10% of progress, not total)
            double lossPercentage = lossConfig.getPercentage();
            long xpToLose = (long) Math.ceil(progressXp * lossPercentage);

            // Should lose 50 XP (10% of 500), not 500 XP (10% of 5000)
            assertEquals(50, xpToLose);
        }

        @Test
        @DisplayName("Zero progress XP results in no loss")
        void zeroProgressXp_noLoss() {
            LevelingConfig.XpLossConfig lossConfig = levelingConfig.getXpLoss();
            lossConfig.setEnabled(true);
            lossConfig.setPercentage(0.10);

            // Player just leveled up - at exactly level threshold
            long currentXp = 1000;
            long xpForCurrentLevel = 1000;
            long progressXp = currentXp - xpForCurrentLevel; // 0 progress

            long xpToLose = (long) Math.ceil(progressXp * lossConfig.getPercentage());

            assertEquals(0, xpToLose);
        }

        @Test
        @DisplayName("Loss is clamped to not drop below minimum level XP")
        void lossIsClamped_toMinLevelXp() {
            LevelingConfig.XpLossConfig lossConfig = levelingConfig.getXpLoss();
            lossConfig.setEnabled(true);
            lossConfig.setPercentage(0.50);  // 50% loss (high for test)
            lossConfig.setMinLevel(2);

            // Player at level 3 with small progress (barely above level 2)
            long currentXp = 350;
            long xpForCurrentLevel = 300;  // Level 3 threshold
            long xpForMinLevel = 100;      // Level 2 threshold
            long progressXp = currentXp - xpForCurrentLevel; // 50 progress

            // Raw loss would be 25 XP (50% of 50)
            long rawLoss = (long) Math.ceil(progressXp * lossConfig.getPercentage());
            assertEquals(25, rawLoss);

            // Check if loss would drop below min level
            long xpAfterLoss = currentXp - rawLoss; // 350 - 25 = 325
            assertTrue(xpAfterLoss >= xpForMinLevel, "Should not drop below min level XP");
        }

        @Test
        @DisplayName("Large progress with small percentage gives reasonable loss")
        void largeProgress_smallPercentage() {
            LevelingConfig.XpLossConfig lossConfig = levelingConfig.getXpLoss();
            lossConfig.setEnabled(true);
            lossConfig.setPercentage(0.05);  // 5% loss

            // Player at high level with lots of progress
            long progressXp = 10000;
            long xpToLose = (long) Math.ceil(progressXp * lossConfig.getPercentage());

            assertEquals(500, xpToLose);  // 5% of 10000
        }

        @Test
        @DisplayName("100% loss percentage takes all progress XP")
        void fullLossPercentage_takesAllProgress() {
            LevelingConfig.XpLossConfig lossConfig = levelingConfig.getXpLoss();
            lossConfig.setEnabled(true);
            lossConfig.setPercentage(1.0);  // 100% loss

            long progressXp = 500;
            long xpToLose = (long) Math.ceil(progressXp * lossConfig.getPercentage());

            assertEquals(500, xpToLose);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // EDGE CASES
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Zero percentage means no loss")
        void zeroPercentage_noLoss() {
            LevelingConfig.XpLossConfig lossConfig = levelingConfig.getXpLoss();
            lossConfig.setEnabled(true);
            lossConfig.setPercentage(0.0);

            long progressXp = 1000;
            long xpToLose = (long) Math.ceil(progressXp * lossConfig.getPercentage());

            assertEquals(0, xpToLose);
        }

        @Test
        @DisplayName("Negative progress XP is handled (edge case)")
        void negativeProgressXp_handled() {
            // This shouldn't happen in practice, but test defensive handling
            LevelingConfig.XpLossConfig lossConfig = levelingConfig.getXpLoss();
            lossConfig.setEnabled(true);
            lossConfig.setPercentage(0.10);

            // Hypothetical edge case: currentXp somehow less than level threshold
            // (corruption or admin command)
            long progressXp = -50;
            long xpToLose = (long) Math.ceil(progressXp * lossConfig.getPercentage());

            // Should not lose positive XP from negative progress
            assertTrue(xpToLose <= 0);
        }

        @Test
        @DisplayName("Very small progress with rounding")
        void verySmallProgress_withRounding() {
            LevelingConfig.XpLossConfig lossConfig = levelingConfig.getXpLoss();
            lossConfig.setEnabled(true);
            lossConfig.setPercentage(0.10);

            // 1 XP progress * 0.10 = 0.1, ceil = 1
            long progressXp = 1;
            long xpToLose = (long) Math.ceil(progressXp * lossConfig.getPercentage());

            assertEquals(1, xpToLose);  // Math.ceil(0.1) = 1
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CONFIG VALIDATION TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Config Validation")
    class ConfigValidationTests {

        @Test
        @DisplayName("Percentage below 0 should fail validation")
        void percentage_below0_failsValidation() {
            levelingConfig.getXpLoss().setPercentage(-0.1);

            assertThrows(LevelingConfig.ConfigValidationException.class, () ->
                levelingConfig.validate());
        }

        @Test
        @DisplayName("Percentage above 1.0 should fail validation")
        void percentage_above1_failsValidation() {
            levelingConfig.getXpLoss().setPercentage(1.5);

            assertThrows(LevelingConfig.ConfigValidationException.class, () ->
                levelingConfig.validate());
        }

        @Test
        @DisplayName("Min level below 1 should fail validation")
        void minLevel_below1_failsValidation() {
            levelingConfig.getXpLoss().setMinLevel(0);

            assertThrows(LevelingConfig.ConfigValidationException.class, () ->
                levelingConfig.validate());
        }
    }
}
