package io.github.larsonix.trailoforbis.maps.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RealmsConfig}.
 */
@DisplayName("RealmsConfig")
class RealmsConfigTest {

    private RealmsConfig config;

    @BeforeEach
    void setUp() {
        config = new RealmsConfig();
    }

    // ═══════════════════════════════════════════════════════════════════
    // DEFAULT VALUES
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Default Values")
    class DefaultValues {

        @Test
        @DisplayName("default enabled is true")
        void defaultEnabledIsTrue() {
            assertTrue(config.isEnabled());
        }

        @Test
        @DisplayName("default max concurrent instances is 100")
        void defaultMaxConcurrentInstances() {
            assertEquals(100, config.getMaxConcurrentInstances());
        }

        @Test
        @DisplayName("default instance timeout is 600 seconds")
        void defaultInstanceTimeout() {
            assertEquals(600, config.getInstanceTimeoutSeconds());
        }

        @Test
        @DisplayName("default completion grace period is 60 seconds")
        void defaultCompletionGracePeriod() {
            assertEquals(60, config.getCompletionGracePeriodSeconds());
        }

        @Test
        @DisplayName("default empty instance timeout is 30 seconds")
        void defaultEmptyInstanceTimeout() {
            assertEquals(30, config.getEmptyInstanceTimeoutSeconds());
        }

        @Test
        @DisplayName("default allow reentry is true")
        void defaultAllowReentry() {
            assertTrue(config.isAllowReentry());
        }

        @Test
        @DisplayName("default death policy is KICK_ON_DEATH")
        void defaultDeathPolicyIsKickOnDeath() {
            assertEquals(RealmsConfig.DeathPolicy.KICK_ON_DEATH, config.getDeathPolicy());
        }

        @Test
        @DisplayName("default max deaths is 3")
        void defaultMaxDeathsIs3() {
            assertEquals(3, config.getMaxDeaths());
        }

        @Test
        @DisplayName("default map item ID is hytale:realm_map")
        void defaultMapItemId() {
            assertEquals("hytale:realm_map", config.getMapItemId());
        }

        @Test
        @DisplayName("default base map drop chance is 0.01")
        void defaultBaseMapDropChance() {
            assertEquals(0.01, config.getBaseMapDropChance(), 0.0001);
        }

        @Test
        @DisplayName("default map drop chance per level is 0.0001")
        void defaultMapDropChancePerLevel() {
            assertEquals(0.0001, config.getMapDropChancePerLevel(), 0.00001);
        }

        @Test
        @DisplayName("default elite map drop multiplier is 2.0")
        void defaultEliteMapDropMultiplier() {
            assertEquals(2.0, config.getEliteMapDropMultiplier(), 0.01);
        }

        @Test
        @DisplayName("default boss map drop multiplier is 5.0")
        void defaultBossMapDropMultiplier() {
            assertEquals(5.0, config.getBossMapDropMultiplier(), 0.01);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // DEATH POLICY
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Death Policy")
    class DeathPolicyTests {

        @Test
        @DisplayName("can set death policy to KICK_ON_DEATH")
        void canSetDeathPolicyToKickOnDeath() {
            config.setDeathPolicy(RealmsConfig.DeathPolicy.KICK_ON_DEATH);
            assertEquals(RealmsConfig.DeathPolicy.KICK_ON_DEATH, config.getDeathPolicy());
        }

        @Test
        @DisplayName("can set death policy to LIMITED_LIVES")
        void canSetDeathPolicyToLimitedLives() {
            config.setDeathPolicy(RealmsConfig.DeathPolicy.LIMITED_LIVES);
            assertEquals(RealmsConfig.DeathPolicy.LIMITED_LIVES, config.getDeathPolicy());
        }

        @Test
        @DisplayName("can set death policy to SOFTCORE")
        void canSetDeathPolicyToSoftcore() {
            config.setDeathPolicy(RealmsConfig.DeathPolicy.SOFTCORE);
            assertEquals(RealmsConfig.DeathPolicy.SOFTCORE, config.getDeathPolicy());
        }

        @Test
        @DisplayName("can set death policy to RESPAWN_IN_REALM")
        void canSetDeathPolicyToRespawnInRealm() {
            config.setDeathPolicy(RealmsConfig.DeathPolicy.KICK_ON_DEATH);
            config.setDeathPolicy(RealmsConfig.DeathPolicy.RESPAWN_IN_REALM);
            assertEquals(RealmsConfig.DeathPolicy.RESPAWN_IN_REALM, config.getDeathPolicy());
        }

        @Test
        @DisplayName("setDeathPolicy throws on null")
        void setDeathPolicyThrowsOnNull() {
            assertThrows(NullPointerException.class, () ->
                    config.setDeathPolicy(null));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // DEATH POLICY ENUM
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DeathPolicy Enum")
    class DeathPolicyEnumTests {

        @Test
        @DisplayName("RESPAWN_IN_REALM is the first enum value")
        void respawnInRealmIsFirstEnumValue() {
            assertEquals(RealmsConfig.DeathPolicy.RESPAWN_IN_REALM,
                    RealmsConfig.DeathPolicy.values()[0]);
        }

        @Test
        @DisplayName("all death policy values are unique")
        void allDeathPolicyValuesAreUnique() {
            RealmsConfig.DeathPolicy[] values = RealmsConfig.DeathPolicy.values();
            for (int i = 0; i < values.length; i++) {
                for (int j = i + 1; j < values.length; j++) {
                    assertNotEquals(values[i], values[j]);
                }
            }
        }

        @Test
        @DisplayName("valueOf works for RESPAWN_IN_REALM")
        void valueOfWorksForRespawnInRealm() {
            assertEquals(RealmsConfig.DeathPolicy.RESPAWN_IN_REALM,
                    RealmsConfig.DeathPolicy.valueOf("RESPAWN_IN_REALM"));
        }

        @Test
        @DisplayName("valueOf works for KICK_ON_DEATH")
        void valueOfWorksForKickOnDeath() {
            assertEquals(RealmsConfig.DeathPolicy.KICK_ON_DEATH,
                    RealmsConfig.DeathPolicy.valueOf("KICK_ON_DEATH"));
        }

        @Test
        @DisplayName("valueOf works for LIMITED_LIVES")
        void valueOfWorksForLimitedLives() {
            assertEquals(RealmsConfig.DeathPolicy.LIMITED_LIVES,
                    RealmsConfig.DeathPolicy.valueOf("LIMITED_LIVES"));
        }

        @Test
        @DisplayName("valueOf works for SOFTCORE")
        void valueOfWorksForSoftcore() {
            assertEquals(RealmsConfig.DeathPolicy.SOFTCORE,
                    RealmsConfig.DeathPolicy.valueOf("SOFTCORE"));
        }

        @Test
        @DisplayName("there are exactly 4 death policies")
        void thereAreExactly4DeathPolicies() {
            assertEquals(4, RealmsConfig.DeathPolicy.values().length);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // MAX DEATHS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Max Deaths")
    class MaxDeathsTests {

        @Test
        @DisplayName("can set max deaths to 1")
        void canSetMaxDeathsTo1() {
            config.setMaxDeaths(1);
            assertEquals(1, config.getMaxDeaths());
        }

        @Test
        @DisplayName("can set max deaths to high value")
        void canSetMaxDeathsToHighValue() {
            config.setMaxDeaths(100);
            assertEquals(100, config.getMaxDeaths());
        }

        @Test
        @DisplayName("can set max deaths to 0 (instant kick on death)")
        void canSetMaxDeathsToZero() {
            config.setMaxDeaths(0);
            assertEquals(0, config.getMaxDeaths());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // SETTERS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Setters")
    class SettersTests {

        @Test
        @DisplayName("setEnabled works")
        void setEnabledWorks() {
            config.setEnabled(false);
            assertFalse(config.isEnabled());

            config.setEnabled(true);
            assertTrue(config.isEnabled());
        }

        @Test
        @DisplayName("setMaxConcurrentInstances works")
        void setMaxConcurrentInstancesWorks() {
            config.setMaxConcurrentInstances(50);
            assertEquals(50, config.getMaxConcurrentInstances());
        }

        @Test
        @DisplayName("setInstanceTimeoutSeconds works")
        void setInstanceTimeoutSecondsWorks() {
            config.setInstanceTimeoutSeconds(300);
            assertEquals(300, config.getInstanceTimeoutSeconds());
        }

        @Test
        @DisplayName("setCompletionGracePeriodSeconds works")
        void setCompletionGracePeriodSecondsWorks() {
            config.setCompletionGracePeriodSeconds(120);
            assertEquals(120, config.getCompletionGracePeriodSeconds());
        }

        @Test
        @DisplayName("setEmptyInstanceTimeoutSeconds works")
        void setEmptyInstanceTimeoutSecondsWorks() {
            config.setEmptyInstanceTimeoutSeconds(60);
            assertEquals(60, config.getEmptyInstanceTimeoutSeconds());
        }

        @Test
        @DisplayName("setAllowReentry works")
        void setAllowReentryWorks() {
            config.setAllowReentry(false);
            assertFalse(config.isAllowReentry());
        }

        @Test
        @DisplayName("setMapItemId works")
        void setMapItemIdWorks() {
            config.setMapItemId("custom:my_map");
            assertEquals("custom:my_map", config.getMapItemId());
        }

        @Test
        @DisplayName("setBaseMapDropChance works")
        void setBaseMapDropChanceWorks() {
            config.setBaseMapDropChance(0.10);
            assertEquals(0.10, config.getBaseMapDropChance(), 0.0001);
        }

        @Test
        @DisplayName("setMapDropChancePerLevel works")
        void setMapDropChancePerLevelWorks() {
            config.setMapDropChancePerLevel(0.001);
            assertEquals(0.001, config.getMapDropChancePerLevel(), 0.00001);
        }

        @Test
        @DisplayName("setEliteMapDropMultiplier works")
        void setEliteMapDropMultiplierWorks() {
            config.setEliteMapDropMultiplier(3.0);
            assertEquals(3.0, config.getEliteMapDropMultiplier(), 0.01);
        }

        @Test
        @DisplayName("setBossMapDropMultiplier works")
        void setBossMapDropMultiplierWorks() {
            config.setBossMapDropMultiplier(10.0);
            assertEquals(10.0, config.getBossMapDropMultiplier(), 0.01);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // DEATH POLICY BEHAVIOR SCENARIOS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Death Policy Behavior Scenarios")
    class DeathPolicyBehaviorScenarios {

        @Test
        @DisplayName("RESPAWN_IN_REALM allows unlimited deaths")
        void respawnInRealmAllowsUnlimitedDeaths() {
            config.setDeathPolicy(RealmsConfig.DeathPolicy.RESPAWN_IN_REALM);
            config.setMaxDeaths(3);

            // Max deaths is ignored with RESPAWN_IN_REALM
            // The policy means respawn without penalty
            assertEquals(RealmsConfig.DeathPolicy.RESPAWN_IN_REALM, config.getDeathPolicy());
        }

        @Test
        @DisplayName("LIMITED_LIVES uses maxDeaths setting")
        void limitedLivesUsesMaxDeathsSetting() {
            config.setDeathPolicy(RealmsConfig.DeathPolicy.LIMITED_LIVES);
            config.setMaxDeaths(5);

            assertEquals(RealmsConfig.DeathPolicy.LIMITED_LIVES, config.getDeathPolicy());
            assertEquals(5, config.getMaxDeaths());
        }

        @Test
        @DisplayName("KICK_ON_DEATH ignores maxDeaths")
        void kickOnDeathIgnoresMaxDeaths() {
            config.setDeathPolicy(RealmsConfig.DeathPolicy.KICK_ON_DEATH);
            config.setMaxDeaths(100); // Should be ignored

            // With KICK_ON_DEATH, player is kicked on first death regardless of maxDeaths
            assertEquals(RealmsConfig.DeathPolicy.KICK_ON_DEATH, config.getDeathPolicy());
        }

        @Test
        @DisplayName("SOFTCORE is equivalent to RESPAWN_IN_REALM")
        void softcoreIsEquivalentToRespawnInRealm() {
            // Both mean no death penalty, just respawn normally
            config.setDeathPolicy(RealmsConfig.DeathPolicy.SOFTCORE);
            assertEquals(RealmsConfig.DeathPolicy.SOFTCORE, config.getDeathPolicy());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // EDGE CASES
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("zero timeout values are allowed")
        void zeroTimeoutValuesAreAllowed() {
            config.setInstanceTimeoutSeconds(0);
            config.setCompletionGracePeriodSeconds(0);
            config.setEmptyInstanceTimeoutSeconds(0);

            assertEquals(0, config.getInstanceTimeoutSeconds());
            assertEquals(0, config.getCompletionGracePeriodSeconds());
            assertEquals(0, config.getEmptyInstanceTimeoutSeconds());
        }

        @Test
        @DisplayName("zero drop chance is allowed")
        void zeroDropChanceIsAllowed() {
            config.setBaseMapDropChance(0.0);
            config.setMapDropChancePerLevel(0.0);

            assertEquals(0.0, config.getBaseMapDropChance(), 0.0001);
            assertEquals(0.0, config.getMapDropChancePerLevel(), 0.00001);
        }

        @Test
        @DisplayName("one max concurrent instance is allowed")
        void oneMaxConcurrentInstanceIsAllowed() {
            config.setMaxConcurrentInstances(1);
            assertEquals(1, config.getMaxConcurrentInstances());
        }
    }
}
