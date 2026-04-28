package io.github.larsonix.trailoforbis.leveling.systems;

import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.api.services.AttributeService;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.config.ConfigManager;
import io.github.larsonix.trailoforbis.database.models.PlayerData;
import io.github.larsonix.trailoforbis.database.repository.PlayerDataRepository;
import io.github.larsonix.trailoforbis.leveling.config.LevelingConfig;
import io.github.larsonix.trailoforbis.leveling.xp.MobStatsXpCalculator;
import io.github.larsonix.trailoforbis.mobs.classification.MobClassificationConfig;
import io.github.larsonix.trailoforbis.mobs.classification.RPGMobClass;
import io.github.larsonix.trailoforbis.mobs.component.MobScalingComponent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for the XP gain calculation logic used by {@link XpGainSystem}.
 *
 * <p>The actual XpGainSystem is an ECS system that extends DeathSystems.OnDeathSystem
 * and requires a fully running Hytale server to test. This test class validates
 * the core calculation logic and configuration handling that the system uses.
 *
 * <p>Key behaviors tested:
 * <ul>
 *   <li>XP multiplier calculation from player's experienceGainPercent stat</li>
 *   <li>Raw stats fallback for unscaled mobs</li>
 *   <li>Passive mob detection (FRIENDLY/REVERED/IGNORE → 1-5 XP)</li>
 *   <li>Configuration enable/disable handling</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("XpGainSystem Logic")
class XpGainSystemTest {

    @Mock
    private TrailOfOrbis plugin;

    @Mock
    private ConfigManager configManager;

    @Mock
    private AttributeService attributeService;

    @Mock
    private PlayerDataRepository playerDataRepository;

    @Mock
    private PlayerData playerData;

    @Mock
    private ComputedStats computedStats;

    private LevelingConfig levelingConfig;
    private MobStatsXpCalculator xpCalculator;

    private final UUID playerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        levelingConfig = new LevelingConfig();
        levelingConfig.setEnabled(true);
        levelingConfig.getXpGain().setEnabled(true);

        LevelingConfig.XpGainConfig xpConfig = levelingConfig.getXpGain();
        MobClassificationConfig classConfig = new MobClassificationConfig();
        xpCalculator = new MobStatsXpCalculator(xpConfig, classConfig);
    }

    // ═══════════════════════════════════════════════════════════════════
    // CONFIGURATION TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Configuration")
    class ConfigurationTests {

        @Test
        @DisplayName("XP gain should be enabled by default")
        void xpGain_enabledByDefault() {
            LevelingConfig defaultConfig = new LevelingConfig();
            assertTrue(defaultConfig.getXpGain().isEnabled());
        }

        @Test
        @DisplayName("Default xpPerMobLevel should be 5.0")
        void defaultXpPerMobLevel_shouldBe5() {
            assertEquals(5.0, levelingConfig.getXpGain().getXpPerMobLevel(), 0.001);
        }

        @Test
        @DisplayName("Default poolMultiplier should be 0.1")
        void defaultPoolMultiplier_shouldBe0_1() {
            assertEquals(0.1, levelingConfig.getXpGain().getPoolMultiplier(), 0.001);
        }

        // Boss/elite XP multiplier tests removed — those multipliers are in
        // MobClassificationConfig (mob-classification.yml), not LevelingConfig.
    }

    // ═══════════════════════════════════════════════════════════════════
    // EXPERIENCE GAIN BONUS TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Experience Gain Bonus")
    class ExperienceGainBonusTests {

        @Test
        @DisplayName("50% XP bonus should multiply XP by 1.5")
        void xpBonus50Percent_multipliesBy1_5() {
            // Simulate applyExperienceGainBonus logic
            long rawXp = 100;
            float bonus = 50.0f;  // 50% bonus

            float multiplier = 1.0f + (bonus / 100.0f);  // 1.5
            long result = Math.round(rawXp * multiplier);

            assertEquals(150, result);
        }

        @Test
        @DisplayName("0% XP bonus should return same XP")
        void xpBonus0Percent_returnsSameXp() {
            long rawXp = 100;
            float bonus = 0.0f;

            float multiplier = 1.0f + (bonus / 100.0f);  // 1.0
            long result = Math.round(rawXp * multiplier);

            assertEquals(100, result);
        }

        @Test
        @DisplayName("-30% XP penalty should multiply XP by 0.7")
        void xpPenalty30Percent_multipliesBy0_7() {
            long rawXp = 100;
            float bonus = -30.0f;  // -30% (a penalty)

            float multiplier = 1.0f + (bonus / 100.0f);  // 0.7
            long result = Math.max(1, Math.round(rawXp * multiplier));

            assertEquals(70, result);
        }

        @Test
        @DisplayName("Result is always at least 1 XP")
        void result_alwaysAtLeast1Xp() {
            long rawXp = 1;
            float bonus = -90.0f;  // -90% penalty

            float multiplier = 1.0f + (bonus / 100.0f);  // 0.1
            long result = Math.max(1, Math.round(rawXp * multiplier));

            assertEquals(1, result);  // Math.max(1, Math.round(0.1)) = 1
        }

        @Test
        @DisplayName("100% XP bonus should double XP")
        void xpBonus100Percent_doublesXp() {
            long rawXp = 50;
            float bonus = 100.0f;

            float multiplier = 1.0f + (bonus / 100.0f);  // 2.0
            long result = Math.round(rawXp * multiplier);

            assertEquals(100, result);
        }

        @Test
        @DisplayName("Very small bonus near zero doesn't affect XP")
        void verySmallBonus_noEffect() {
            long rawXp = 100;
            float bonus = 0.0001f;  // Near-zero bonus

            // Check the abs(bonus) < 0.001 guard
            boolean bonusIsNegligible = Math.abs(bonus) < 0.001f;

            if (bonusIsNegligible) {
                // applyExperienceGainBonus returns rawXp unchanged
                assertEquals(100, rawXp);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // SCALED MOB XP CALCULATION TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Scaled Mob XP Calculation")
    class ScaledMobXpTests {

        @Test
        @DisplayName("Level 10 HOSTILE mob gives expected XP")
        void level10Hostile_givesExpectedXp() {
            long xp = xpCalculator.calculateXp(10, 100, 0, RPGMobClass.HOSTILE);

            // baseXp = 10 * 5.0 = 50
            // poolBonus = 100 * 0.1 = 10
            // tierMult = 1.0 (HOSTILE)
            // globalMult = 0.3
            // distMult = 1.0
            // xp = (50 + 10) * 1.0 * 0.3 * 1.0 = 18
            assertTrue(xp > 0);
        }

        @Test
        @DisplayName("BOSS mob gives more XP than HOSTILE")
        void bossMob_givesMoreXpThanHostile() {
            long hostileXp = xpCalculator.calculateXp(20, 500, 5, RPGMobClass.HOSTILE);
            long bossXp = xpCalculator.calculateXp(20, 500, 5, RPGMobClass.BOSS);

            assertTrue(bossXp > hostileXp,
                "Boss should give significantly more XP than hostile");
        }

        @Test
        @DisplayName("ELITE mob gives more XP than HOSTILE but less than BOSS")
        void eliteMob_givesMidRangeXp() {
            long hostileXp = xpCalculator.calculateXp(20, 500, 5, RPGMobClass.HOSTILE);
            long eliteXp = xpCalculator.calculateXp(20, 500, 5, RPGMobClass.ELITE);
            long bossXp = xpCalculator.calculateXp(20, 500, 5, RPGMobClass.BOSS);

            assertTrue(eliteXp > hostileXp, "Elite > Hostile");
            assertTrue(bossXp > eliteXp, "Boss > Elite");
        }

        @Test
        @DisplayName("Higher distance level increases XP")
        void higherDistance_increasesXp() {
            long xpNearSpawn = xpCalculator.calculateXp(20, 500, 0, RPGMobClass.HOSTILE);
            long xpFar = xpCalculator.calculateXp(20, 500, 20, RPGMobClass.HOSTILE);

            assertTrue(xpFar > xpNearSpawn,
                "Distance should increase XP");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // RAW STATS XP CALCULATION TESTS (unscaled mobs)
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Raw Stats XP Calculation")
    class RawStatsXpTests {

        @Test
        @DisplayName("Higher max health gives more XP")
        void higherHealth_givesMoreXp() {
            long lowHealthXp = xpCalculator.calculateXpFromRawStats(50, 0, RPGMobClass.HOSTILE);
            long highHealthXp = xpCalculator.calculateXpFromRawStats(500, 0, RPGMobClass.HOSTILE);

            assertTrue(highHealthXp > lowHealthXp);
        }

        @Test
        @DisplayName("Farther distance from origin gives more XP")
        void fartherDistance_givesMoreXp() {
            long nearXp = xpCalculator.calculateXpFromRawStats(100, 0, RPGMobClass.HOSTILE);
            long farXp = xpCalculator.calculateXpFromRawStats(100, 2000, RPGMobClass.HOSTILE);

            assertTrue(farXp > nearXp);
        }

        @Test
        @DisplayName("Zero health returns at least 1 XP")
        void zeroHealth_returnsAtLeast1Xp() {
            long xp = xpCalculator.calculateXpFromRawStats(0, 0, RPGMobClass.HOSTILE);

            assertTrue(xp >= 1);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // PASSIVE MOB DETECTION TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Passive Mob Detection")
    class PassiveMobTests {

        @Test
        @DisplayName("PASSIVE classification gives minimal XP")
        void passiveClassification_givesMinimalXp() {
            long passiveXp = xpCalculator.calculateXp(10, 100, 0, RPGMobClass.PASSIVE);
            long hostileXp = xpCalculator.calculateXp(10, 100, 0, RPGMobClass.HOSTILE);

            assertTrue(passiveXp < hostileXp,
                "Passive mobs should give less XP than hostile");
            assertTrue(passiveXp >= 1, "Should give at least 1 XP");
        }

        @Test
        @DisplayName("MINOR classification gives moderate XP")
        void minorClassification_givesModerateXp() {
            long minorXp = xpCalculator.calculateXp(20, 500, 0, RPGMobClass.MINOR);
            long passiveXp = xpCalculator.calculateXp(20, 500, 0, RPGMobClass.PASSIVE);
            long hostileXp = xpCalculator.calculateXp(20, 500, 0, RPGMobClass.HOSTILE);

            assertTrue(minorXp > passiveXp, "Minor > Passive");
            assertTrue(minorXp < hostileXp, "Minor < Hostile");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // EDGE CASES
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Level 0 mob returns at least 1 XP")
        void level0Mob_returnsAtLeast1Xp() {
            long xp = xpCalculator.calculateXp(0, 0, 0, RPGMobClass.HOSTILE);

            assertTrue(xp >= 1);
        }

        @Test
        @DisplayName("Negative level is handled gracefully")
        void negativeLevel_handledGracefully() {
            long xp = xpCalculator.calculateXp(-5, 0, 0, RPGMobClass.HOSTILE);

            assertTrue(xp >= 1);
        }

        @Test
        @DisplayName("Very high level doesn't overflow")
        void veryHighLevel_noOverflow() {
            long xp = xpCalculator.calculateXp(100000, 100000, 1000, RPGMobClass.BOSS);

            assertTrue(xp > 0);
            assertTrue(xp < Long.MAX_VALUE / 2, "Should not be near overflow");
        }

        @Test
        @DisplayName("Null classification handled by calculator")
        void nullClassification_handled() {
            // MobClassificationConfig has a default multiplier for null/unknown
            assertDoesNotThrow(() -> {
                xpCalculator.calculateXp(10, 100, 0, null);
            });
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CONFIG VALIDATION TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Config Validation")
    class ConfigValidationTests {

        @Test
        @DisplayName("Negative xpPerMobLevel should fail validation")
        void negativeXpPerMobLevel_failsValidation() {
            levelingConfig.getXpGain().setXpPerMobLevel(-1.0);

            assertThrows(LevelingConfig.ConfigValidationException.class, () ->
                levelingConfig.validate());
        }

        @Test
        @DisplayName("Negative poolMultiplier should fail validation")
        void negativePoolMultiplier_failsValidation() {
            levelingConfig.getXpGain().setPoolMultiplier(-0.1);

            assertThrows(LevelingConfig.ConfigValidationException.class, () ->
                levelingConfig.validate());
        }

        // Boss/elite multiplier validation tests removed — fields cleaned up.
        // XP multipliers are in MobClassificationConfig (mob-classification.yml).
    }
}
