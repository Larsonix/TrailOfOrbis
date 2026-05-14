package io.github.larsonix.trailoforbis.mobs.modifiers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link MobModifierConfig} defaults and validation.
 *
 * <p>Critical for production: if the config file is missing or has
 * invalid values, the system must work with safe defaults. If exclusion
 * rules are broken, banned modifier combos appear in-game.
 */
@DisplayName("MobModifierConfig")
class MobModifierConfigTest {

    private MobModifierConfig config;

    @BeforeEach
    void setUp() {
        config = MobModifierConfig.createDefaults();
        config.validate();
    }

    @Nested
    @DisplayName("Defaults")
    class Defaults {

        @Test
        @DisplayName("Enabled by default")
        void enabledByDefault() {
            assertTrue(config.isEnabled());
        }

        @Test
        @DisplayName("Elite rolls 1 modifier by default")
        void elite_1modifier() {
            assertEquals(1, config.getModifierCount("elite"));
        }

        @Test
        @DisplayName("Boss rolls 2 modifiers by default")
        void boss_2modifiers() {
            assertEquals(2, config.getModifierCount("boss"));
        }

        @Test
        @DisplayName("Elite boss rolls 3 modifiers by default")
        void eliteBoss_3modifiers() {
            assertEquals(3, config.getModifierCount("elite_boss"));
        }

        @Test
        @DisplayName("Unknown tier rolls 0 modifiers")
        void unknownTier_0modifiers() {
            assertEquals(0, config.getModifierCount("nonexistent"));
        }
    }

    @Nested
    @DisplayName("Exclusion Rules")
    class ExclusionRules {

        @Test
        @DisplayName("Same modifier is always excluded (no duplicates)")
        void sameModifier_alwaysExcluded() {
            assertTrue(config.isExcluded(ModifierType.HARDENED, ModifierType.HARDENED));
            assertTrue(config.isExcluded(ModifierType.BLAZING, ModifierType.BLAZING));
        }

        @Test
        @DisplayName("Hardened + Warding is excluded")
        void hardened_warding_excluded() {
            assertTrue(config.isExcluded(ModifierType.HARDENED, ModifierType.WARDING));
            assertTrue(config.isExcluded(ModifierType.WARDING, ModifierType.HARDENED));
        }

        @Test
        @DisplayName("Evasive + Frost Aura is excluded")
        void evasive_frostAura_excluded() {
            assertTrue(config.isExcluded(ModifierType.EVASIVE, ModifierType.FROST_AURA));
            assertTrue(config.isExcluded(ModifierType.FROST_AURA, ModifierType.EVASIVE));
        }

        @Test
        @DisplayName("Non-excluded pair is not excluded")
        void nonExcluded_notExcluded() {
            assertFalse(config.isExcluded(ModifierType.BLAZING, ModifierType.ENRAGED));
            assertFalse(config.isExcluded(ModifierType.HARDENED, ModifierType.FIERCE));
        }
    }

    @Nested
    @DisplayName("Reward Scaling")
    class RewardScaling {

        @Test
        @DisplayName("1 modifier: 2.0x IIQ, 1.5x IIR")
        void oneModifier_scaling() {
            MobModifierConfig.RewardScaling scaling = config.getRewardScaling(1);
            assertEquals(2.0, scaling.getIiq(), 0.001);
            assertEquals(1.5, scaling.getIir(), 0.001);
        }

        @Test
        @DisplayName("2 modifiers: 4.0x IIQ, 3.0x IIR")
        void twoModifiers_scaling() {
            MobModifierConfig.RewardScaling scaling = config.getRewardScaling(2);
            assertEquals(4.0, scaling.getIiq(), 0.001);
            assertEquals(3.0, scaling.getIir(), 0.001);
        }

        @Test
        @DisplayName("3 modifiers: 7.0x IIQ, 5.0x IIR")
        void threeModifiers_scaling() {
            MobModifierConfig.RewardScaling scaling = config.getRewardScaling(3);
            assertEquals(7.0, scaling.getIiq(), 0.001);
            assertEquals(5.0, scaling.getIir(), 0.001);
        }

        @Test
        @DisplayName("0 modifiers: 1.0x (no bonus)")
        void zeroModifiers_noBonus() {
            MobModifierConfig.RewardScaling scaling = config.getRewardScaling(0);
            assertEquals(1.0, scaling.getIiq(), 0.001);
            assertEquals(1.0, scaling.getIir(), 0.001);
        }

        @Test
        @DisplayName("Reward scaling is super-linear (3 > 3x of 1)")
        void rewardScaling_superLinear() {
            double iiq1 = config.getRewardScaling(1).getIiq();
            double iiq3 = config.getRewardScaling(3).getIiq();
            assertTrue(iiq3 > 3 * iiq1,
                "3-modifier IIQ (" + iiq3 + ") should be more than 3x single (" + (3 * iiq1) + ")");
        }
    }

    @Nested
    @DisplayName("Visual Config")
    class VisualConfig {

        @Test
        @DisplayName("Elite scale default is 1.15")
        void eliteScale() {
            assertEquals(1.15, config.getVisuals().getEliteScale(), 0.001);
        }

        @Test
        @DisplayName("Boss scale default is 1.30")
        void bossScale() {
            assertEquals(1.30, config.getVisuals().getBossScale(), 0.001);
        }

        @Test
        @DisplayName("Nameplate prefixes contain star characters")
        void nameplatePrefixes_haveStars() {
            assertTrue(config.getVisuals().getNameplatePrefixElite().contains("\u2605"));
            assertTrue(config.getVisuals().getNameplatePrefixBoss().contains("\u2605"));
        }
    }
}
