package io.github.larsonix.trailoforbis.leveling.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link LevelingConfig}.
 *
 * <p>Tests configuration validation, default values, nested config classes,
 * and YAML snake_case compatibility.
 */
@DisplayName("LevelingConfig")
class LevelingConfigTest {

    private LevelingConfig config;

    @BeforeEach
    void setUp() {
        config = new LevelingConfig();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Default Values Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Default Values")
    class DefaultValuesTests {

        @Test
        @DisplayName("enabled defaults to true")
        void enabled_defaultsToTrue() {
            assertTrue(config.isEnabled());
        }

        @Test
        @DisplayName("formula config has defaults")
        void formulaConfig_hasDefaults() {
            LevelingConfig.FormulaConfig formula = config.getFormula();

            assertNotNull(formula);
            assertEquals("effort", formula.getType());
            assertEquals(1_000_000, formula.getMaxLevel());
            // Effort config defaults
            assertNotNull(formula.getEffort());
            assertEquals(3.0, formula.getEffort().getBaseMobs());
            assertEquals(150.0, formula.getEffort().getTargetMobs());
            assertEquals(100, formula.getEffort().getTargetLevel());
            // Legacy defaults still present
            assertEquals(50.0, formula.getBaseXp());
            assertEquals(2.2, formula.getExponent());
        }

        @Test
        @DisplayName("xpGain config has defaults")
        void xpGainConfig_hasDefaults() {
            LevelingConfig.XpGainConfig xpGain = config.getXpGain();

            assertNotNull(xpGain);
            assertTrue(xpGain.isEnabled());
            assertEquals(5.0, xpGain.getXpPerMobLevel());
            assertEquals(0.1, xpGain.getPoolMultiplier());
            // Boss/elite multipliers removed from LevelingConfig (now in MobClassificationConfig)
        }

        @Test
        @DisplayName("xpLoss config has defaults")
        void xpLossConfig_hasDefaults() {
            LevelingConfig.XpLossConfig xpLoss = config.getXpLoss();

            assertNotNull(xpLoss);
            assertFalse(xpLoss.isEnabled()); // Disabled by default
            assertEquals(0.10, xpLoss.getPercentage());
            assertEquals(1, xpLoss.getMinLevel());
        }

        @Test
        @DisplayName("ui config has defaults")
        void uiConfig_hasDefaults() {
            LevelingConfig.UiConfig ui = config.getUi();

            assertNotNull(ui);
            assertTrue(ui.isShowXpBar());
            assertTrue(ui.isShowLevelUpNotification());
            assertTrue(ui.isShowXpGainNotification());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Validation Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Validation")
    class ValidationTests {

        @Test
        @DisplayName("Valid config passes validation")
        void validConfig_passesValidation() {
            assertDoesNotThrow(() -> config.validate());
        }

        @Test
        @DisplayName("maxLevel < 1 fails validation")
        void maxLevelLessThan1_failsValidation() {
            config.getFormula().setMaxLevel(0);

            LevelingConfig.ConfigValidationException ex = assertThrows(
                LevelingConfig.ConfigValidationException.class,
                () -> config.validate()
            );

            assertTrue(ex.getMessage().contains("max_level"));
        }

        @Test
        @DisplayName("baseXp <= 0 fails validation (exponential mode)")
        void baseXpZeroOrNegative_failsValidation() {
            config.getFormula().setType("exponential");
            config.getFormula().setBaseXp(0);

            LevelingConfig.ConfigValidationException ex = assertThrows(
                LevelingConfig.ConfigValidationException.class,
                () -> config.validate()
            );

            assertTrue(ex.getMessage().contains("base_xp"));
        }

        @Test
        @DisplayName("exponent <= 0 fails validation (exponential mode)")
        void exponentZeroOrNegative_failsValidation() {
            config.getFormula().setType("exponential");
            config.getFormula().setExponent(0);

            LevelingConfig.ConfigValidationException ex = assertThrows(
                LevelingConfig.ConfigValidationException.class,
                () -> config.validate()
            );

            assertTrue(ex.getMessage().contains("exponent"));
        }

        @Test
        @DisplayName("effort baseMobs <= 0 fails validation")
        void effortBaseMobsZero_failsValidation() {
            config.getFormula().setType("effort");
            config.getFormula().getEffort().setBaseMobs(0);

            LevelingConfig.ConfigValidationException ex = assertThrows(
                LevelingConfig.ConfigValidationException.class,
                () -> config.validate()
            );

            assertTrue(ex.getMessage().contains("base_mobs"));
        }

        @Test
        @DisplayName("effort targetMobs <= baseMobs fails validation")
        void effortTargetMobsNotGreater_failsValidation() {
            config.getFormula().setType("effort");
            config.getFormula().getEffort().setBaseMobs(10);
            config.getFormula().getEffort().setTargetMobs(5);

            LevelingConfig.ConfigValidationException ex = assertThrows(
                LevelingConfig.ConfigValidationException.class,
                () -> config.validate()
            );

            assertTrue(ex.getMessage().contains("target_mobs"));
        }

        @Test
        @DisplayName("effort targetLevel < 2 fails validation")
        void effortTargetLevelLessThan2_failsValidation() {
            config.getFormula().setType("effort");
            config.getFormula().getEffort().setTargetLevel(1);

            LevelingConfig.ConfigValidationException ex = assertThrows(
                LevelingConfig.ConfigValidationException.class,
                () -> config.validate()
            );

            assertTrue(ex.getMessage().contains("target_level"));
        }

        @Test
        @DisplayName("invalid formula type fails validation")
        void invalidFormulaType_failsValidation() {
            config.getFormula().setType("quadratic");

            LevelingConfig.ConfigValidationException ex = assertThrows(
                LevelingConfig.ConfigValidationException.class,
                () -> config.validate()
            );

            assertTrue(ex.getMessage().contains("effort"));
        }

        @Test
        @DisplayName("xpPerMobLevel < 0 fails validation")
        void xpPerMobLevelNegative_failsValidation() {
            config.getXpGain().setXpPerMobLevel(-1);

            LevelingConfig.ConfigValidationException ex = assertThrows(
                LevelingConfig.ConfigValidationException.class,
                () -> config.validate()
            );

            assertTrue(ex.getMessage().contains("xp_per_mob_level"));
        }

        @Test
        @DisplayName("poolMultiplier < 0 fails validation")
        void poolMultiplierNegative_failsValidation() {
            config.getXpGain().setPoolMultiplier(-0.1);

            LevelingConfig.ConfigValidationException ex = assertThrows(
                LevelingConfig.ConfigValidationException.class,
                () -> config.validate()
            );

            assertTrue(ex.getMessage().contains("pool_multiplier"));
        }

        // bossMultiplier/eliteMultiplier validation tests removed — fields cleaned up.
        // XP multipliers are validated in MobClassificationConfig.
        @Test
        @DisplayName("Placeholder for removed boss/elite validation tests")
        void removedBossEliteValidation_placeholder() {
            // Boss/elite XP multipliers moved to mob-classification.yml
            assertNotNull(config);
        }

        @Test
        @DisplayName("xpLoss percentage > 1.0 fails validation")
        void xpLossPercentageGreaterThan1_failsValidation() {
            config.getXpLoss().setPercentage(1.5);

            LevelingConfig.ConfigValidationException ex = assertThrows(
                LevelingConfig.ConfigValidationException.class,
                () -> config.validate()
            );

            assertTrue(ex.getMessage().contains("percentage"));
        }

        @Test
        @DisplayName("xpLoss percentage < 0 fails validation")
        void xpLossPercentageNegative_failsValidation() {
            config.getXpLoss().setPercentage(-0.1);

            LevelingConfig.ConfigValidationException ex = assertThrows(
                LevelingConfig.ConfigValidationException.class,
                () -> config.validate()
            );

            assertTrue(ex.getMessage().contains("percentage"));
        }

        @Test
        @DisplayName("xpLoss minLevel < 1 fails validation")
        void xpLossMinLevelLessThan1_failsValidation() {
            config.getXpLoss().setMinLevel(0);

            LevelingConfig.ConfigValidationException ex = assertThrows(
                LevelingConfig.ConfigValidationException.class,
                () -> config.validate()
            );

            assertTrue(ex.getMessage().contains("min_level"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // YAML Snake_Case Compatibility Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("YAML Snake_Case Compatibility")
    class YamlSnakeCaseTests {

        @Test
        @DisplayName("setXp_gain sets xpGain")
        void setXp_gain_setsXpGain() {
            LevelingConfig.XpGainConfig newConfig = new LevelingConfig.XpGainConfig();
            newConfig.setXpPerMobLevel(99);

            config.setXp_gain(newConfig);

            assertEquals(99, config.getXpGain().getXpPerMobLevel());
        }

        @Test
        @DisplayName("setXp_loss sets xpLoss")
        void setXp_loss_setsXpLoss() {
            LevelingConfig.XpLossConfig newConfig = new LevelingConfig.XpLossConfig();
            newConfig.setPercentage(0.5);

            config.setXp_loss(newConfig);

            assertEquals(0.5, config.getXpLoss().getPercentage());
        }

        @Test
        @DisplayName("FormulaConfig setMax_level works")
        void formulaConfig_setMaxLevel_works() {
            config.getFormula().setMax_level(500);

            assertEquals(500, config.getFormula().getMaxLevel());
        }

        @Test
        @DisplayName("FormulaConfig setBase_xp works")
        void formulaConfig_setBaseXp_works() {
            config.getFormula().setBase_xp(200.0);

            assertEquals(200.0, config.getFormula().getBaseXp());
        }

        @Test
        @DisplayName("XpGainConfig setXp_per_mob_level works")
        void xpGainConfig_setXpPerMobLevel_works() {
            config.getXpGain().setXp_per_mob_level(15.0);

            assertEquals(15.0, config.getXpGain().getXpPerMobLevel());
        }

        @Test
        @DisplayName("XpGainConfig setPool_multiplier works")
        void xpGainConfig_setPoolMultiplier_works() {
            config.getXpGain().setPool_multiplier(0.5);

            assertEquals(0.5, config.getXpGain().getPoolMultiplier());
        }

        // setBoss_multiplier / setElite_multiplier tests removed — dead code cleaned up.
        // XP multipliers are managed by MobClassificationConfig (mob-classification.yml).

        @Test
        @DisplayName("XpLossConfig setMin_level works")
        void xpLossConfig_setMinLevel_works() {
            config.getXpLoss().setMin_level(5);

            assertEquals(5, config.getXpLoss().getMinLevel());
        }

        @Test
        @DisplayName("UiConfig setShow_xp_bar works")
        void uiConfig_setShowXpBar_works() {
            config.getUi().setShow_xp_bar(false);

            assertFalse(config.getUi().isShowXpBar());
        }

        @Test
        @DisplayName("UiConfig setShow_level_up_notification works")
        void uiConfig_setShowLevelUpNotification_works() {
            config.getUi().setShow_level_up_notification(false);

            assertFalse(config.getUi().isShowLevelUpNotification());
        }

        @Test
        @DisplayName("UiConfig setShow_xp_gain_notification works")
        void uiConfig_setShowXpGainNotification_works() {
            config.getUi().setShow_xp_gain_notification(false);

            assertFalse(config.getUi().isShowXpGainNotification());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Diagnostics Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Diagnostics")
    class DiagnosticsTests {

        @Test
        @DisplayName("getDiagnostics returns non-null string")
        void getDiagnostics_returnsNonNull() {
            String diag = config.getDiagnostics();

            assertNotNull(diag);
            assertFalse(diag.isBlank());
        }

        @Test
        @DisplayName("getDiagnostics contains config sections")
        void getDiagnostics_containsConfigSections() {
            String diag = config.getDiagnostics();

            assertTrue(diag.contains("LevelingConfig"));
            assertTrue(diag.contains("[Formula]"));
            assertTrue(diag.contains("[XP Gain]"));
            assertTrue(diag.contains("[XP Loss]"));
            assertTrue(diag.contains("[UI]"));
        }

        @Test
        @DisplayName("getDiagnostics contains effort curve info (default type)")
        void getDiagnostics_containsEffortInfo() {
            String diag = config.getDiagnostics();

            assertTrue(diag.contains("type: effort"));
            assertTrue(diag.contains("base_mobs:"));
            assertTrue(diag.contains("target_mobs:"));
            assertTrue(diag.contains("derived_exponent:"));
        }

        @Test
        @DisplayName("getDiagnostics contains XP examples (exponential mode)")
        void getDiagnostics_containsXpExamples_exponential() {
            config.getFormula().setType("exponential");
            String diag = config.getDiagnostics();

            assertTrue(diag.contains("XP for level 10"));
            assertTrue(diag.contains("XP for level 50"));
        }

        @Test
        @DisplayName("getDiagnostics shows current values")
        void getDiagnostics_showsCurrentValues() {
            config.setEnabled(false);
            config.getFormula().setMaxLevel(999);

            String diag = config.getDiagnostics();

            assertTrue(diag.contains("enabled: false"));
            assertTrue(diag.contains("max_level: 999"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Setter Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Setters")
    class SetterTests {

        @Test
        @DisplayName("setEnabled changes value")
        void setEnabled_changesValue() {
            config.setEnabled(false);
            assertFalse(config.isEnabled());

            config.setEnabled(true);
            assertTrue(config.isEnabled());
        }

        @Test
        @DisplayName("setFormula replaces config")
        void setFormula_replacesConfig() {
            LevelingConfig.FormulaConfig newFormula = new LevelingConfig.FormulaConfig();
            newFormula.setMaxLevel(999);

            config.setFormula(newFormula);

            assertEquals(999, config.getFormula().getMaxLevel());
        }

        @Test
        @DisplayName("setXpGain replaces config")
        void setXpGain_replacesConfig() {
            LevelingConfig.XpGainConfig newConfig = new LevelingConfig.XpGainConfig();
            newConfig.setEnabled(false);

            config.setXpGain(newConfig);

            assertFalse(config.getXpGain().isEnabled());
        }

        @Test
        @DisplayName("setXpLoss replaces config")
        void setXpLoss_replacesConfig() {
            LevelingConfig.XpLossConfig newConfig = new LevelingConfig.XpLossConfig();
            newConfig.setEnabled(true);

            config.setXpLoss(newConfig);

            assertTrue(config.getXpLoss().isEnabled());
        }

        @Test
        @DisplayName("setUi replaces config")
        void setUi_replacesConfig() {
            LevelingConfig.UiConfig newConfig = new LevelingConfig.UiConfig();
            newConfig.setShowXpBar(false);

            config.setUi(newConfig);

            assertFalse(config.getUi().isShowXpBar());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Edge Cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Boundary values pass validation")
        void boundaryValues_passValidation() {
            config.getFormula().setMaxLevel(1);
            config.getFormula().setBaseXp(0.001);
            config.getFormula().setExponent(0.001);
            config.getXpGain().setXpPerMobLevel(0);
            config.getXpGain().setPoolMultiplier(0);
            // Boss/elite multipliers removed from XpGainConfig
            config.getXpLoss().setPercentage(0);
            config.getXpLoss().setMinLevel(1);

            assertDoesNotThrow(() -> config.validate());
        }

        @Test
        @DisplayName("Maximum boundary values pass validation")
        void maxBoundaryValues_passValidation() {
            config.getXpLoss().setPercentage(1.0);

            assertDoesNotThrow(() -> config.validate());
        }

        @Test
        @DisplayName("XP formula calculation preview in diagnostics (exponential)")
        void xpFormulaCalculation_previewInDiagnostics() {
            // Set specific values for predictable output
            config.getFormula().setType("exponential");
            config.getFormula().setBaseXp(100);
            config.getFormula().setExponent(2.0);

            String diag = config.getDiagnostics();

            // XP for level 10 = 100 × 9^2 = 8100
            assertTrue(diag.contains("8100"));
        }
    }
}
