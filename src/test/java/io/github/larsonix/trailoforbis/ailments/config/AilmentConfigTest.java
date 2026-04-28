package io.github.larsonix.trailoforbis.ailments.config;

import io.github.larsonix.trailoforbis.ailments.config.AilmentConfig.BurnConfig;
import io.github.larsonix.trailoforbis.ailments.config.AilmentConfig.ConfigValidationException;
import io.github.larsonix.trailoforbis.ailments.config.AilmentConfig.FreezeConfig;
import io.github.larsonix.trailoforbis.ailments.config.AilmentConfig.PoisonConfig;
import io.github.larsonix.trailoforbis.ailments.config.AilmentConfig.ShockConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AilmentConfig} validation logic and default values.
 */
@DisplayName("AilmentConfig")
class AilmentConfigTest {

    // ==================== Default Values ====================

    @Nested
    @DisplayName("Default values")
    class DefaultValues {

        @Test
        @DisplayName("enabled defaults to true")
        void enabledDefaultsToTrue() {
            AilmentConfig config = new AilmentConfig();
            assertTrue(config.isEnabled());
        }

        @Test
        @DisplayName("tickRateSeconds defaults to 0.25")
        void tickRateDefaultsToQuarterSecond() {
            AilmentConfig config = new AilmentConfig();
            assertEquals(0.25f, config.getTickRateSeconds());
        }

        @Test
        @DisplayName("burn config has correct defaults")
        void burnDefaults() {
            BurnConfig burn = new BurnConfig();
            assertEquals(10.0f, burn.getBaseChance());
            assertEquals(4.0f, burn.getBaseDuration());
            assertEquals(0.5f, burn.getDamageRatio());
        }

        @Test
        @DisplayName("freeze config has correct defaults")
        void freezeDefaults() {
            FreezeConfig freeze = new FreezeConfig();
            assertEquals(10.0f, freeze.getBaseChance());
            assertEquals(3.0f, freeze.getBaseDuration());
            assertEquals(30.0f, freeze.getMaxSlowPercent());
            assertEquals(5.0f, freeze.getMinMagnitude());
        }

        @Test
        @DisplayName("shock config has correct defaults")
        void shockDefaults() {
            ShockConfig shock = new ShockConfig();
            assertEquals(10.0f, shock.getBaseChance());
            assertEquals(2.0f, shock.getBaseDuration());
            assertEquals(50.0f, shock.getMaxDamageIncrease());
            assertEquals(5.0f, shock.getMinMagnitude());
        }

        @Test
        @DisplayName("poison config has correct defaults")
        void poisonDefaults() {
            PoisonConfig poison = new PoisonConfig();
            assertEquals(10.0f, poison.getBaseChance());
            assertEquals(5.0f, poison.getBaseDuration());
            assertEquals(0.3f, poison.getDamageRatio());
            assertEquals(10, poison.getMaxStacks());
        }

        @Test
        @DisplayName("all nested configs are non-null by default")
        void nestedConfigsNonNull() {
            AilmentConfig config = new AilmentConfig();
            assertNotNull(config.getBurn());
            assertNotNull(config.getFreeze());
            assertNotNull(config.getShock());
            assertNotNull(config.getPoison());
        }

        @Test
        @DisplayName("default config passes validation")
        void defaultConfigPassesValidation() {
            AilmentConfig config = new AilmentConfig();
            assertDoesNotThrow(config::validate);
        }
    }

    // ==================== Top-Level Validation ====================

    @Nested
    @DisplayName("Top-level validation")
    class TopLevelValidation {

        @Test
        @DisplayName("tickRateSeconds at 0 rejects")
        void tickRateZeroRejects() {
            AilmentConfig config = new AilmentConfig();
            config.setTickRateSeconds(0f);

            ConfigValidationException ex = assertThrows(ConfigValidationException.class, config::validate);
            assertTrue(ex.getMessage().contains("tick_rate_seconds"));
        }

        @Test
        @DisplayName("tickRateSeconds negative rejects")
        void tickRateNegativeRejects() {
            AilmentConfig config = new AilmentConfig();
            config.setTickRateSeconds(-1.0f);

            assertThrows(ConfigValidationException.class, config::validate);
        }

        @Test
        @DisplayName("tickRateSeconds above 5 rejects")
        void tickRateAboveFiveRejects() {
            AilmentConfig config = new AilmentConfig();
            config.setTickRateSeconds(5.1f);

            assertThrows(ConfigValidationException.class, config::validate);
        }

        @Test
        @DisplayName("tickRateSeconds at exactly 5 accepts")
        void tickRateExactlyFiveAccepts() {
            AilmentConfig config = new AilmentConfig();
            config.setTickRateSeconds(5.0f);

            assertDoesNotThrow(config::validate);
        }

        @Test
        @DisplayName("tickRateSeconds just above 0 accepts")
        void tickRateJustAboveZeroAccepts() {
            AilmentConfig config = new AilmentConfig();
            config.setTickRateSeconds(0.01f);

            assertDoesNotThrow(config::validate);
        }
    }

    // ==================== Burn Config Validation ====================

    @Nested
    @DisplayName("BurnConfig validation")
    class BurnValidation {

        @Test
        @DisplayName("valid burn config passes")
        void validBurnPasses() {
            BurnConfig burn = new BurnConfig();
            assertDoesNotThrow(burn::validate);
        }

        @Test
        @DisplayName("baseChance at 0 accepts")
        void baseChanceZeroAccepts() {
            BurnConfig burn = new BurnConfig();
            burn.setBaseChance(0f);
            assertDoesNotThrow(burn::validate);
        }

        @Test
        @DisplayName("baseChance negative rejects")
        void baseChanceNegativeRejects() {
            BurnConfig burn = new BurnConfig();
            burn.setBaseChance(-1.0f);

            ConfigValidationException ex = assertThrows(ConfigValidationException.class, burn::validate);
            assertTrue(ex.getMessage().contains("burn.base_chance"));
        }

        @Test
        @DisplayName("baseDuration at 0 rejects")
        void baseDurationZeroRejects() {
            BurnConfig burn = new BurnConfig();
            burn.setBaseDuration(0f);

            ConfigValidationException ex = assertThrows(ConfigValidationException.class, burn::validate);
            assertTrue(ex.getMessage().contains("burn.base_duration"));
        }

        @Test
        @DisplayName("baseDuration negative rejects")
        void baseDurationNegativeRejects() {
            BurnConfig burn = new BurnConfig();
            burn.setBaseDuration(-2.0f);

            assertThrows(ConfigValidationException.class, burn::validate);
        }

        @Test
        @DisplayName("damageRatio at 0 accepts")
        void damageRatioZeroAccepts() {
            BurnConfig burn = new BurnConfig();
            burn.setDamageRatio(0f);
            assertDoesNotThrow(burn::validate);
        }

        @Test
        @DisplayName("damageRatio at 2.0 accepts")
        void damageRatioAtMaxAccepts() {
            BurnConfig burn = new BurnConfig();
            burn.setDamageRatio(2.0f);
            assertDoesNotThrow(burn::validate);
        }

        @Test
        @DisplayName("damageRatio negative rejects")
        void damageRatioNegativeRejects() {
            BurnConfig burn = new BurnConfig();
            burn.setDamageRatio(-0.1f);

            ConfigValidationException ex = assertThrows(ConfigValidationException.class, burn::validate);
            assertTrue(ex.getMessage().contains("burn.damage_ratio"));
        }

        @Test
        @DisplayName("damageRatio above 2.0 rejects")
        void damageRatioAboveMaxRejects() {
            BurnConfig burn = new BurnConfig();
            burn.setDamageRatio(2.1f);

            assertThrows(ConfigValidationException.class, burn::validate);
        }
    }

    // ==================== Freeze Config Validation ====================

    @Nested
    @DisplayName("FreezeConfig validation")
    class FreezeValidation {

        @Test
        @DisplayName("valid freeze config passes")
        void validFreezePasses() {
            FreezeConfig freeze = new FreezeConfig();
            assertDoesNotThrow(freeze::validate);
        }

        @Test
        @DisplayName("baseChance at 0 accepts")
        void baseChanceZeroAccepts() {
            FreezeConfig freeze = new FreezeConfig();
            freeze.setBaseChance(0f);
            assertDoesNotThrow(freeze::validate);
        }

        @Test
        @DisplayName("baseChance negative rejects")
        void baseChanceNegativeRejects() {
            FreezeConfig freeze = new FreezeConfig();
            freeze.setBaseChance(-5.0f);

            ConfigValidationException ex = assertThrows(ConfigValidationException.class, freeze::validate);
            assertTrue(ex.getMessage().contains("freeze.base_chance"));
        }

        @Test
        @DisplayName("baseDuration at 0 rejects")
        void baseDurationZeroRejects() {
            FreezeConfig freeze = new FreezeConfig();
            freeze.setBaseDuration(0f);

            assertThrows(ConfigValidationException.class, freeze::validate);
        }

        @Test
        @DisplayName("baseDuration negative rejects")
        void baseDurationNegativeRejects() {
            FreezeConfig freeze = new FreezeConfig();
            freeze.setBaseDuration(-1.0f);

            assertThrows(ConfigValidationException.class, freeze::validate);
        }

        @Test
        @DisplayName("maxSlowPercent at 0 rejects (exclusive lower bound)")
        void maxSlowPercentZeroRejects() {
            FreezeConfig freeze = new FreezeConfig();
            freeze.setMaxSlowPercent(0f);

            ConfigValidationException ex = assertThrows(ConfigValidationException.class, freeze::validate);
            assertTrue(ex.getMessage().contains("freeze.max_slow_percent"));
        }

        @Test
        @DisplayName("maxSlowPercent at 100 accepts")
        void maxSlowPercentAtHundredAccepts() {
            FreezeConfig freeze = new FreezeConfig();
            freeze.setMaxSlowPercent(100.0f);
            assertDoesNotThrow(freeze::validate);
        }

        @Test
        @DisplayName("maxSlowPercent above 100 rejects")
        void maxSlowPercentAboveHundredRejects() {
            FreezeConfig freeze = new FreezeConfig();
            freeze.setMaxSlowPercent(100.1f);

            assertThrows(ConfigValidationException.class, freeze::validate);
        }

        @Test
        @DisplayName("maxSlowPercent negative rejects")
        void maxSlowPercentNegativeRejects() {
            FreezeConfig freeze = new FreezeConfig();
            freeze.setMaxSlowPercent(-10.0f);

            assertThrows(ConfigValidationException.class, freeze::validate);
        }

        @Test
        @DisplayName("minMagnitude at 0 accepts")
        void minMagnitudeZeroAccepts() {
            FreezeConfig freeze = new FreezeConfig();
            freeze.setMinMagnitude(0f);
            assertDoesNotThrow(freeze::validate);
        }

        @Test
        @DisplayName("minMagnitude equal to maxSlowPercent accepts")
        void minMagnitudeEqualToMaxAccepts() {
            FreezeConfig freeze = new FreezeConfig();
            freeze.setMinMagnitude(30.0f); // default maxSlowPercent is 30
            assertDoesNotThrow(freeze::validate);
        }

        @Test
        @DisplayName("minMagnitude above maxSlowPercent rejects")
        void minMagnitudeAboveMaxRejects() {
            FreezeConfig freeze = new FreezeConfig();
            freeze.setMinMagnitude(31.0f); // default maxSlowPercent is 30

            ConfigValidationException ex = assertThrows(ConfigValidationException.class, freeze::validate);
            assertTrue(ex.getMessage().contains("freeze.min_magnitude"));
        }

        @Test
        @DisplayName("minMagnitude negative rejects")
        void minMagnitudeNegativeRejects() {
            FreezeConfig freeze = new FreezeConfig();
            freeze.setMinMagnitude(-1.0f);

            assertThrows(ConfigValidationException.class, freeze::validate);
        }
    }

    // ==================== Shock Config Validation ====================

    @Nested
    @DisplayName("ShockConfig validation")
    class ShockValidation {

        @Test
        @DisplayName("valid shock config passes")
        void validShockPasses() {
            ShockConfig shock = new ShockConfig();
            assertDoesNotThrow(shock::validate);
        }

        @Test
        @DisplayName("baseChance at 0 accepts")
        void baseChanceZeroAccepts() {
            ShockConfig shock = new ShockConfig();
            shock.setBaseChance(0f);
            assertDoesNotThrow(shock::validate);
        }

        @Test
        @DisplayName("baseChance negative rejects")
        void baseChanceNegativeRejects() {
            ShockConfig shock = new ShockConfig();
            shock.setBaseChance(-3.0f);

            ConfigValidationException ex = assertThrows(ConfigValidationException.class, shock::validate);
            assertTrue(ex.getMessage().contains("shock.base_chance"));
        }

        @Test
        @DisplayName("baseDuration at 0 rejects")
        void baseDurationZeroRejects() {
            ShockConfig shock = new ShockConfig();
            shock.setBaseDuration(0f);

            assertThrows(ConfigValidationException.class, shock::validate);
        }

        @Test
        @DisplayName("baseDuration negative rejects")
        void baseDurationNegativeRejects() {
            ShockConfig shock = new ShockConfig();
            shock.setBaseDuration(-0.5f);

            assertThrows(ConfigValidationException.class, shock::validate);
        }

        @Test
        @DisplayName("maxDamageIncrease at 0 rejects (exclusive lower bound)")
        void maxDamageIncreaseZeroRejects() {
            ShockConfig shock = new ShockConfig();
            shock.setMaxDamageIncrease(0f);

            ConfigValidationException ex = assertThrows(ConfigValidationException.class, shock::validate);
            assertTrue(ex.getMessage().contains("shock.max_damage_increase"));
        }

        @Test
        @DisplayName("maxDamageIncrease at 200 accepts")
        void maxDamageIncreaseAtCeilingAccepts() {
            ShockConfig shock = new ShockConfig();
            shock.setMaxDamageIncrease(200.0f);
            shock.setMinMagnitude(5.0f); // keep minMagnitude within new max
            assertDoesNotThrow(shock::validate);
        }

        @Test
        @DisplayName("maxDamageIncrease above 200 rejects")
        void maxDamageIncreaseAboveCeilingRejects() {
            ShockConfig shock = new ShockConfig();
            shock.setMaxDamageIncrease(200.1f);

            assertThrows(ConfigValidationException.class, shock::validate);
        }

        @Test
        @DisplayName("maxDamageIncrease negative rejects")
        void maxDamageIncreaseNegativeRejects() {
            ShockConfig shock = new ShockConfig();
            shock.setMaxDamageIncrease(-10.0f);

            assertThrows(ConfigValidationException.class, shock::validate);
        }

        @Test
        @DisplayName("minMagnitude at 0 accepts")
        void minMagnitudeZeroAccepts() {
            ShockConfig shock = new ShockConfig();
            shock.setMinMagnitude(0f);
            assertDoesNotThrow(shock::validate);
        }

        @Test
        @DisplayName("minMagnitude equal to maxDamageIncrease accepts")
        void minMagnitudeEqualToMaxAccepts() {
            ShockConfig shock = new ShockConfig();
            shock.setMinMagnitude(50.0f); // default maxDamageIncrease is 50
            assertDoesNotThrow(shock::validate);
        }

        @Test
        @DisplayName("minMagnitude above maxDamageIncrease rejects")
        void minMagnitudeAboveMaxRejects() {
            ShockConfig shock = new ShockConfig();
            shock.setMinMagnitude(51.0f); // default maxDamageIncrease is 50

            ConfigValidationException ex = assertThrows(ConfigValidationException.class, shock::validate);
            assertTrue(ex.getMessage().contains("shock.min_magnitude"));
        }

        @Test
        @DisplayName("minMagnitude negative rejects")
        void minMagnitudeNegativeRejects() {
            ShockConfig shock = new ShockConfig();
            shock.setMinMagnitude(-1.0f);

            assertThrows(ConfigValidationException.class, shock::validate);
        }
    }

    // ==================== Poison Config Validation ====================

    @Nested
    @DisplayName("PoisonConfig validation")
    class PoisonValidation {

        @Test
        @DisplayName("valid poison config passes")
        void validPoisonPasses() {
            PoisonConfig poison = new PoisonConfig();
            assertDoesNotThrow(poison::validate);
        }

        @Test
        @DisplayName("baseChance at 0 accepts")
        void baseChanceZeroAccepts() {
            PoisonConfig poison = new PoisonConfig();
            poison.setBaseChance(0f);
            assertDoesNotThrow(poison::validate);
        }

        @Test
        @DisplayName("baseChance negative rejects")
        void baseChanceNegativeRejects() {
            PoisonConfig poison = new PoisonConfig();
            poison.setBaseChance(-1.0f);

            ConfigValidationException ex = assertThrows(ConfigValidationException.class, poison::validate);
            assertTrue(ex.getMessage().contains("poison.base_chance"));
        }

        @Test
        @DisplayName("baseDuration at 0 rejects")
        void baseDurationZeroRejects() {
            PoisonConfig poison = new PoisonConfig();
            poison.setBaseDuration(0f);

            assertThrows(ConfigValidationException.class, poison::validate);
        }

        @Test
        @DisplayName("baseDuration negative rejects")
        void baseDurationNegativeRejects() {
            PoisonConfig poison = new PoisonConfig();
            poison.setBaseDuration(-3.0f);

            assertThrows(ConfigValidationException.class, poison::validate);
        }

        @Test
        @DisplayName("damageRatio at 0 accepts")
        void damageRatioZeroAccepts() {
            PoisonConfig poison = new PoisonConfig();
            poison.setDamageRatio(0f);
            assertDoesNotThrow(poison::validate);
        }

        @Test
        @DisplayName("damageRatio at 2.0 accepts")
        void damageRatioAtMaxAccepts() {
            PoisonConfig poison = new PoisonConfig();
            poison.setDamageRatio(2.0f);
            assertDoesNotThrow(poison::validate);
        }

        @Test
        @DisplayName("damageRatio negative rejects")
        void damageRatioNegativeRejects() {
            PoisonConfig poison = new PoisonConfig();
            poison.setDamageRatio(-0.01f);

            ConfigValidationException ex = assertThrows(ConfigValidationException.class, poison::validate);
            assertTrue(ex.getMessage().contains("poison.damage_ratio"));
        }

        @Test
        @DisplayName("damageRatio above 2.0 rejects")
        void damageRatioAboveMaxRejects() {
            PoisonConfig poison = new PoisonConfig();
            poison.setDamageRatio(2.5f);

            assertThrows(ConfigValidationException.class, poison::validate);
        }

        @Test
        @DisplayName("maxStacks at 1 accepts")
        void maxStacksAtOneAccepts() {
            PoisonConfig poison = new PoisonConfig();
            poison.setMaxStacks(1);
            assertDoesNotThrow(poison::validate);
        }

        @Test
        @DisplayName("maxStacks at 100 accepts")
        void maxStacksAtCeilingAccepts() {
            PoisonConfig poison = new PoisonConfig();
            poison.setMaxStacks(100);
            assertDoesNotThrow(poison::validate);
        }

        @Test
        @DisplayName("maxStacks at 0 rejects")
        void maxStacksZeroRejects() {
            PoisonConfig poison = new PoisonConfig();
            poison.setMaxStacks(0);

            ConfigValidationException ex = assertThrows(ConfigValidationException.class, poison::validate);
            assertTrue(ex.getMessage().contains("poison.max_stacks"));
        }

        @Test
        @DisplayName("maxStacks negative rejects")
        void maxStacksNegativeRejects() {
            PoisonConfig poison = new PoisonConfig();
            poison.setMaxStacks(-5);

            assertThrows(ConfigValidationException.class, poison::validate);
        }

        @Test
        @DisplayName("maxStacks above 100 rejects")
        void maxStacksAboveCeilingRejects() {
            PoisonConfig poison = new PoisonConfig();
            poison.setMaxStacks(101);

            assertThrows(ConfigValidationException.class, poison::validate);
        }
    }

    // ==================== Null Coalescing in Setters ====================

    @Nested
    @DisplayName("Null coalescing")
    class NullCoalescing {

        @Test
        @DisplayName("setBurn(null) replaces with defaults")
        void setBurnNullCreatesDefaults() {
            AilmentConfig config = new AilmentConfig();
            config.setBurn(null);

            assertNotNull(config.getBurn());
            assertEquals(10.0f, config.getBurn().getBaseChance());
            assertEquals(4.0f, config.getBurn().getBaseDuration());
            assertEquals(0.5f, config.getBurn().getDamageRatio());
        }

        @Test
        @DisplayName("setFreeze(null) replaces with defaults")
        void setFreezeNullCreatesDefaults() {
            AilmentConfig config = new AilmentConfig();
            config.setFreeze(null);

            assertNotNull(config.getFreeze());
            assertEquals(10.0f, config.getFreeze().getBaseChance());
            assertEquals(3.0f, config.getFreeze().getBaseDuration());
            assertEquals(30.0f, config.getFreeze().getMaxSlowPercent());
            assertEquals(5.0f, config.getFreeze().getMinMagnitude());
        }

        @Test
        @DisplayName("setShock(null) replaces with defaults")
        void setShockNullCreatesDefaults() {
            AilmentConfig config = new AilmentConfig();
            config.setShock(null);

            assertNotNull(config.getShock());
            assertEquals(10.0f, config.getShock().getBaseChance());
            assertEquals(2.0f, config.getShock().getBaseDuration());
            assertEquals(50.0f, config.getShock().getMaxDamageIncrease());
            assertEquals(5.0f, config.getShock().getMinMagnitude());
        }

        @Test
        @DisplayName("setPoison(null) replaces with defaults")
        void setPoisonNullCreatesDefaults() {
            AilmentConfig config = new AilmentConfig();
            config.setPoison(null);

            assertNotNull(config.getPoison());
            assertEquals(10.0f, config.getPoison().getBaseChance());
            assertEquals(5.0f, config.getPoison().getBaseDuration());
            assertEquals(0.3f, config.getPoison().getDamageRatio());
            assertEquals(10, config.getPoison().getMaxStacks());
        }

        @Test
        @DisplayName("config with null coalesced values passes validation")
        void nullCoalescedConfigPassesValidation() {
            AilmentConfig config = new AilmentConfig();
            config.setBurn(null);
            config.setFreeze(null);
            config.setShock(null);
            config.setPoison(null);

            assertDoesNotThrow(config::validate);
        }
    }

    // ==================== YAML snake_case Setters ====================

    @Nested
    @DisplayName("YAML snake_case binding")
    class SnakeCaseBinding {

        @Test
        @DisplayName("setTick_rate_seconds sets tickRateSeconds")
        void snakeCaseTickRate() {
            AilmentConfig config = new AilmentConfig();
            config.setTick_rate_seconds(1.5f);

            assertEquals(1.5f, config.getTickRateSeconds());
        }

        @Test
        @DisplayName("burn snake_case setters work")
        void burnSnakeCaseSetters() {
            BurnConfig burn = new BurnConfig();
            burn.setBase_chance(15.0f);
            burn.setBase_duration(6.0f);
            burn.setDamage_ratio(0.8f);

            assertEquals(15.0f, burn.getBaseChance());
            assertEquals(6.0f, burn.getBaseDuration());
            assertEquals(0.8f, burn.getDamageRatio());
        }

        @Test
        @DisplayName("freeze snake_case setters work")
        void freezeSnakeCaseSetters() {
            FreezeConfig freeze = new FreezeConfig();
            freeze.setBase_chance(20.0f);
            freeze.setBase_duration(5.0f);
            freeze.setMax_slow_percent(50.0f);
            freeze.setMin_magnitude(10.0f);

            assertEquals(20.0f, freeze.getBaseChance());
            assertEquals(5.0f, freeze.getBaseDuration());
            assertEquals(50.0f, freeze.getMaxSlowPercent());
            assertEquals(10.0f, freeze.getMinMagnitude());
        }

        @Test
        @DisplayName("shock snake_case setters work")
        void shockSnakeCaseSetters() {
            ShockConfig shock = new ShockConfig();
            shock.setBase_chance(12.0f);
            shock.setBase_duration(3.0f);
            shock.setMax_damage_increase(80.0f);
            shock.setMin_magnitude(8.0f);

            assertEquals(12.0f, shock.getBaseChance());
            assertEquals(3.0f, shock.getBaseDuration());
            assertEquals(80.0f, shock.getMaxDamageIncrease());
            assertEquals(8.0f, shock.getMinMagnitude());
        }

        @Test
        @DisplayName("poison snake_case setters work")
        void poisonSnakeCaseSetters() {
            PoisonConfig poison = new PoisonConfig();
            poison.setBase_chance(8.0f);
            poison.setBase_duration(7.0f);
            poison.setDamage_ratio(0.6f);
            poison.setMax_stacks(20);

            assertEquals(8.0f, poison.getBaseChance());
            assertEquals(7.0f, poison.getBaseDuration());
            assertEquals(0.6f, poison.getDamageRatio());
            assertEquals(20, poison.getMaxStacks());
        }

        @Test
        @DisplayName("values set via snake_case setters are validated correctly")
        void snakeCaseValuesValidated() {
            AilmentConfig config = new AilmentConfig();
            config.setTick_rate_seconds(0f);

            assertThrows(ConfigValidationException.class, config::validate);
        }
    }

    // ==================== Cascading Validation ====================

    @Nested
    @DisplayName("Cascading validation through validate()")
    class CascadingValidation {

        @Test
        @DisplayName("invalid burn config fails top-level validate()")
        void invalidBurnFailsTopLevel() {
            AilmentConfig config = new AilmentConfig();
            config.getBurn().setBaseDuration(0f);

            assertThrows(ConfigValidationException.class, config::validate);
        }

        @Test
        @DisplayName("invalid freeze config fails top-level validate()")
        void invalidFreezeFailsTopLevel() {
            AilmentConfig config = new AilmentConfig();
            config.getFreeze().setMaxSlowPercent(0f);

            assertThrows(ConfigValidationException.class, config::validate);
        }

        @Test
        @DisplayName("invalid shock config fails top-level validate()")
        void invalidShockFailsTopLevel() {
            AilmentConfig config = new AilmentConfig();
            config.getShock().setMaxDamageIncrease(0f);

            assertThrows(ConfigValidationException.class, config::validate);
        }

        @Test
        @DisplayName("invalid poison config fails top-level validate()")
        void invalidPoisonFailsTopLevel() {
            AilmentConfig config = new AilmentConfig();
            config.getPoison().setMaxStacks(0);

            assertThrows(ConfigValidationException.class, config::validate);
        }

        @Test
        @DisplayName("disabled config still validates inner configs")
        void disabledConfigStillValidates() {
            AilmentConfig config = new AilmentConfig();
            config.setEnabled(false);
            config.getBurn().setDamageRatio(5.0f); // out of range

            assertThrows(ConfigValidationException.class, config::validate);
        }
    }

    // ==================== Boundary Interaction Tests ====================

    @Nested
    @DisplayName("Cross-field boundary interactions")
    class BoundaryInteractions {

        @Test
        @DisplayName("freeze: minMagnitude and maxSlowPercent both at minimum valid values")
        void freezeMinimumValidBounds() {
            FreezeConfig freeze = new FreezeConfig();
            freeze.setMaxSlowPercent(0.01f);
            freeze.setMinMagnitude(0f);

            assertDoesNotThrow(freeze::validate);
        }

        @Test
        @DisplayName("freeze: minMagnitude equals maxSlowPercent at upper bound")
        void freezeMinEqualsMaxAtUpperBound() {
            FreezeConfig freeze = new FreezeConfig();
            freeze.setMaxSlowPercent(100.0f);
            freeze.setMinMagnitude(100.0f);

            assertDoesNotThrow(freeze::validate);
        }

        @Test
        @DisplayName("shock: minMagnitude and maxDamageIncrease both at minimum valid values")
        void shockMinimumValidBounds() {
            ShockConfig shock = new ShockConfig();
            shock.setMaxDamageIncrease(0.01f);
            shock.setMinMagnitude(0f);

            assertDoesNotThrow(shock::validate);
        }

        @Test
        @DisplayName("shock: minMagnitude equals maxDamageIncrease at upper bound")
        void shockMinEqualsMaxAtUpperBound() {
            ShockConfig shock = new ShockConfig();
            shock.setMaxDamageIncrease(200.0f);
            shock.setMinMagnitude(200.0f);

            assertDoesNotThrow(shock::validate);
        }

        @Test
        @DisplayName("shock: lowering maxDamageIncrease below minMagnitude rejects")
        void shockLowerMaxBelowMinRejects() {
            ShockConfig shock = new ShockConfig();
            shock.setMinMagnitude(10.0f);
            shock.setMaxDamageIncrease(5.0f);

            assertThrows(ConfigValidationException.class, shock::validate);
        }

        @Test
        @DisplayName("freeze: lowering maxSlowPercent below minMagnitude rejects")
        void freezeLowerMaxBelowMinRejects() {
            FreezeConfig freeze = new FreezeConfig();
            freeze.setMinMagnitude(20.0f);
            freeze.setMaxSlowPercent(10.0f);

            assertThrows(ConfigValidationException.class, freeze::validate);
        }
    }
}
