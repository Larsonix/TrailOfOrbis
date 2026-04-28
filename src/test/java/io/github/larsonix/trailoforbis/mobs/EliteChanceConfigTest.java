package io.github.larsonix.trailoforbis.mobs;

import io.github.larsonix.trailoforbis.mobs.MobScalingConfig.EliteChanceConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link EliteChanceConfig}.
 *
 * <p>The random elite system replaces deterministic elite classification.
 * Instead of specific roles (chieftain, berserker) always being ELITE,
 * any hostile mob now has a chance to become elite at spawn time.
 */
@DisplayName("EliteChanceConfig")
class EliteChanceConfigTest {

    private EliteChanceConfig config;

    @BeforeEach
    void setUp() {
        config = new EliteChanceConfig();
    }

    @Nested
    @DisplayName("Default Values")
    class DefaultValuesTests {

        @Test
        @DisplayName("Default enabled is true")
        void defaultEnabled_isTrue() {
            assertTrue(config.isEnabled());
        }

        @Test
        @DisplayName("Default base chance is 5%")
        void defaultBaseChance_isFivePercent() {
            assertEquals(0.05, config.getBaseChance(), 0.001);
        }

        @Test
        @DisplayName("Default chance per level is 0.01%")
        void defaultChancePerLevel_isPointZeroOnePercent() {
            assertEquals(0.0001, config.getChancePerLevel(), 0.00001);
        }

        @Test
        @DisplayName("Default max chance is 25%")
        void defaultMaxChance_isTwentyFivePercent() {
            assertEquals(0.25, config.getMaxChance(), 0.001);
        }
    }

    @Nested
    @DisplayName("calculateChance()")
    class CalculateChanceTests {

        @Test
        @DisplayName("Level 1 returns base chance")
        void levelOne_returnsBaseChance() {
            // level 1: 0.05 + (1 * 0.0001) = 0.0501
            double chance = config.calculateChance(1);
            assertEquals(0.0501, chance, 0.0001);
        }

        @Test
        @DisplayName("Level 0 returns base chance")
        void levelZero_returnsBaseChance() {
            double chance = config.calculateChance(0);
            assertEquals(0.05, chance, 0.0001);
        }

        @Test
        @DisplayName("Level 100 adds 1% to base chance")
        void levelHundred_addsOnePercent() {
            // level 100: 0.05 + (100 * 0.0001) = 0.06
            double chance = config.calculateChance(100);
            assertEquals(0.06, chance, 0.0001);
        }

        @Test
        @DisplayName("High level is capped at max chance")
        void highLevel_cappedAtMaxChance() {
            // level 10000: 0.05 + (10000 * 0.0001) = 1.05, capped to 0.25
            double chance = config.calculateChance(10000);
            assertEquals(0.25, chance, 0.0001);
        }

        @Test
        @DisplayName("Disabled returns 0")
        void disabled_returnsZero() {
            config.setEnabled(false);
            double chance = config.calculateChance(100);
            assertEquals(0.0, chance, 0.0001);
        }

        @Test
        @DisplayName("Custom base chance is used")
        void customBaseChance_isUsed() {
            config.setBaseChance(0.10); // 10%
            double chance = config.calculateChance(0);
            assertEquals(0.10, chance, 0.0001);
        }

        @Test
        @DisplayName("Custom chance per level is used")
        void customChancePerLevel_isUsed() {
            config.setChancePerLevel(0.001); // 0.1% per level
            // level 50: 0.05 + (50 * 0.001) = 0.10
            double chance = config.calculateChance(50);
            assertEquals(0.10, chance, 0.0001);
        }

        @Test
        @DisplayName("Custom max chance is enforced")
        void customMaxChance_isEnforced() {
            config.setMaxChance(0.15); // 15% cap
            // level 10000: would exceed 15%, capped
            double chance = config.calculateChance(10000);
            assertEquals(0.15, chance, 0.0001);
        }
    }

    @Nested
    @DisplayName("Validation")
    class ValidationTests {

        @Test
        @DisplayName("Valid config passes validation")
        void validConfig_passesValidation() {
            assertDoesNotThrow(() -> config.validate());
        }

        @Test
        @DisplayName("Negative base chance fails validation")
        void negativeBaseChance_failsValidation() {
            config.setBaseChance(-0.01);
            assertThrows(MobScalingConfig.ConfigValidationException.class, () -> config.validate());
        }

        @Test
        @DisplayName("Base chance over 1 fails validation")
        void baseChanceOverOne_failsValidation() {
            config.setBaseChance(1.5);
            assertThrows(MobScalingConfig.ConfigValidationException.class, () -> config.validate());
        }

        @Test
        @DisplayName("Negative chance per level fails validation")
        void negativeChancePerLevel_failsValidation() {
            config.setChancePerLevel(-0.001);
            assertThrows(MobScalingConfig.ConfigValidationException.class, () -> config.validate());
        }

        @Test
        @DisplayName("Negative max chance fails validation")
        void negativeMaxChance_failsValidation() {
            config.setMaxChance(-0.1);
            assertThrows(MobScalingConfig.ConfigValidationException.class, () -> config.validate());
        }

        @Test
        @DisplayName("Max chance over 1 fails validation")
        void maxChanceOverOne_failsValidation() {
            config.setMaxChance(1.5);
            assertThrows(MobScalingConfig.ConfigValidationException.class, () -> config.validate());
        }

        @Test
        @DisplayName("Max chance less than base chance fails validation")
        void maxChanceLessThanBaseChance_failsValidation() {
            config.setBaseChance(0.20);
            config.setMaxChance(0.10);
            assertThrows(MobScalingConfig.ConfigValidationException.class, () -> config.validate());
        }
    }

    @Nested
    @DisplayName("YAML Snake Case Setters")
    class YamlSnakeCaseSetterTests {

        @Test
        @DisplayName("setBase_chance works")
        void setBase_chance_works() {
            config.setBase_chance(0.15);
            assertEquals(0.15, config.getBaseChance(), 0.001);
        }

        @Test
        @DisplayName("setChance_per_level works")
        void setChance_per_level_works() {
            config.setChance_per_level(0.002);
            assertEquals(0.002, config.getChancePerLevel(), 0.0001);
        }

        @Test
        @DisplayName("setMax_chance works")
        void setMax_chance_works() {
            config.setMax_chance(0.30);
            assertEquals(0.30, config.getMaxChance(), 0.001);
        }
    }
}
