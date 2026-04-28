package io.github.larsonix.trailoforbis.maps.listeners;

import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.maps.RealmsManager;
import io.github.larsonix.trailoforbis.maps.config.RealmModifierConfig;
import io.github.larsonix.trailoforbis.maps.config.RealmsConfig;
import io.github.larsonix.trailoforbis.maps.items.RealmMapGenerator;
import io.github.larsonix.trailoforbis.maps.listeners.RealmMapDropListener.MobType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RealmMapDropListener}.
 */
@DisplayName("RealmMapDropListener")
class RealmMapDropListenerTest {

    @Mock
    private TrailOfOrbis plugin;

    @Mock
    private RealmsManager realmsManager;

    private RealmsConfig config;
    private RealmModifierConfig modifierConfig;
    private RealmMapGenerator generator;
    private RealmMapDropListener listener;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        config = new RealmsConfig();
        modifierConfig = new RealmModifierConfig();
        generator = new RealmMapGenerator(config, modifierConfig);

        // Configure mock to return our test objects
        when(plugin.getRealmsManager()).thenReturn(realmsManager);
        when(realmsManager.getConfig()).thenReturn(config);
        when(realmsManager.getMapGenerator()).thenReturn(generator);

        listener = new RealmMapDropListener(plugin);
    }

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTION
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("creates with valid plugin")
        void createsWithValidPlugin() {
            assertNotNull(listener);
            assertSame(plugin, listener.getPlugin());
        }

        @Test
        @DisplayName("throws on null plugin")
        void throwsOnNullPlugin() {
            assertThrows(NullPointerException.class, () ->
                    new RealmMapDropListener(null));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // DROP CHANCE CALCULATION
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Drop Chance Calculation")
    class DropChanceCalculation {

        @Test
        @DisplayName("calculates base chance for normal mob")
        void calculatesBaseChanceForNormalMob() {
            // Default: 0.01 (1%) base chance
            double chance = listener.calculateDropChance(config, 1, MobType.NORMAL);

            // At level 1: 0.01 + (1 * 0.0001) = 0.0101
            assertEquals(0.0101, chance, 0.0001);
        }

        @Test
        @DisplayName("increases chance with mob level")
        void increasesChanceWithMobLevel() {
            // Level 100: 0.01 + (100 * 0.0001) = 0.02
            double chance = listener.calculateDropChance(config, 100, MobType.NORMAL);
            assertEquals(0.02, chance, 0.0001);
        }

        @Test
        @DisplayName("applies elite multiplier")
        void appliesEliteMultiplier() {
            // Default elite multiplier: 2.0
            // Level 1 base: 0.0101, with elite: 0.0202
            double chance = listener.calculateDropChance(config, 1, MobType.ELITE);
            assertEquals(0.0202, chance, 0.0001);
        }

        @Test
        @DisplayName("applies boss multiplier")
        void appliesBossMultiplier() {
            // Default boss multiplier: 5.0
            // Level 1 base: 0.0101, with boss: 0.0505
            double chance = listener.calculateDropChance(config, 1, MobType.BOSS);
            assertEquals(0.0505, chance, 0.0001);
        }

        @Test
        @DisplayName("caps at 100% chance")
        void capsAt100Percent() {
            // Very high level should cap at 1.0
            double chance = listener.calculateDropChance(config, 100000, MobType.BOSS);
            assertEquals(1.0, chance, 0.0001);
        }

        @Test
        @DisplayName("minimum is 0%")
        void minimumIsZeroPercent() {
            // Negative level (edge case) should not go negative
            double chance = listener.calculateDropChance(config, -100, MobType.NORMAL);
            assertTrue(chance >= 0.0);
        }

        @Test
        @DisplayName("respects custom base chance config")
        void respectsCustomBaseChanceConfig() {
            config.setBaseMapDropChance(0.10); // 10% base

            // Level 1: 0.10 + (1 * 0.0001) = 0.1001
            double chance = listener.calculateDropChance(config, 1, MobType.NORMAL);
            assertEquals(0.1001, chance, 0.0001);
        }

        @Test
        @DisplayName("respects custom per-level config")
        void respectsCustomPerLevelConfig() {
            config.setMapDropChancePerLevel(0.001); // 0.1% per level

            // Level 100: 0.01 + (100 * 0.001) = 0.11
            double chance = listener.calculateDropChance(config, 100, MobType.NORMAL);
            assertEquals(0.11, chance, 0.0001);
        }

        @Test
        @DisplayName("respects custom boss multiplier config")
        void respectsCustomBossMultiplierConfig() {
            config.setBossMapDropMultiplier(10.0); // 10x for bosses

            // Level 1: (0.01 + 0.0001) * 10 = 0.101
            double chance = listener.calculateDropChance(config, 1, MobType.BOSS);
            assertEquals(0.101, chance, 0.0001);
        }

        @Test
        @DisplayName("respects custom elite multiplier config")
        void respectsCustomEliteMultiplierConfig() {
            config.setEliteMapDropMultiplier(3.0); // 3x for elites

            // Level 1: (0.01 + 0.0001) * 3 = 0.0303
            double chance = listener.calculateDropChance(config, 1, MobType.ELITE);
            assertEquals(0.0303, chance, 0.0001);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // MOB TYPE ENUM
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("MobType Enum")
    class MobTypeEnum {

        @Test
        @DisplayName("NORMAL has multiplier 1.0")
        void normalHasMultiplier1() {
            assertEquals(1.0, MobType.NORMAL.getMultiplier());
        }

        @Test
        @DisplayName("ELITE has multiplier 2.0")
        void eliteHasMultiplier2() {
            assertEquals(2.0, MobType.ELITE.getMultiplier());
        }

        @Test
        @DisplayName("BOSS has multiplier 5.0")
        void bossHasMultiplier5() {
            assertEquals(5.0, MobType.BOSS.getMultiplier());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CONFIG INTEGRATION
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Config Integration")
    class ConfigIntegration {

        @Test
        @DisplayName("uses map item ID from config")
        void usesMapItemIdFromConfig() {
            config.setMapItemId("custom:my_map");

            assertEquals("custom:my_map", config.getMapItemId());
        }

        @Test
        @DisplayName("default map item ID is hytale:realm_map")
        void defaultMapItemIdIsCorrect() {
            assertEquals("hytale:realm_map", config.getMapItemId());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // EDGE CASES
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("handles level 0")
        void handlesLevelZero() {
            // Level 0: 0.01 + (0 * 0.0001) = 0.01
            double chance = listener.calculateDropChance(config, 0, MobType.NORMAL);
            assertEquals(0.01, chance, 0.0001);
        }

        @Test
        @DisplayName("handles very high level")
        void handlesVeryHighLevel() {
            // Level 10000: 0.01 + (10000 * 0.0001) = 1.01, capped to 1.0
            double chance = listener.calculateDropChance(config, 10000, MobType.NORMAL);
            assertEquals(1.0, chance, 0.0001);
        }

        @Test
        @DisplayName("handles zero base chance")
        void handlesZeroBaseChance() {
            config.setBaseMapDropChance(0.0);

            // Level 100: 0 + (100 * 0.0001) = 0.01
            double chance = listener.calculateDropChance(config, 100, MobType.NORMAL);
            assertEquals(0.01, chance, 0.0001);
        }

        @Test
        @DisplayName("handles zero per-level chance")
        void handlesZeroPerLevelChance() {
            config.setMapDropChancePerLevel(0.0);

            // Level 1000: 0.01 + (1000 * 0) = 0.01
            double chance = listener.calculateDropChance(config, 1000, MobType.NORMAL);
            assertEquals(0.01, chance, 0.0001);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // DROP CHANCE SCENARIOS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Drop Chance Scenarios")
    class DropChanceScenarios {

        @Test
        @DisplayName("low-level normal mob has ~1% chance")
        void lowLevelNormalMobHas1PercentChance() {
            double chance = listener.calculateDropChance(config, 10, MobType.NORMAL);
            // 0.01 + (10 * 0.0001) = 0.011 = 1.1%
            assertTrue(chance >= 0.01 && chance <= 0.015);
        }

        @Test
        @DisplayName("mid-level elite mob has ~3% chance")
        void midLevelEliteMobHas3PercentChance() {
            double chance = listener.calculateDropChance(config, 50, MobType.ELITE);
            // (0.01 + (50 * 0.0001)) * 2 = 0.015 * 2 = 0.03 = 3%
            assertEquals(0.03, chance, 0.001);
        }

        @Test
        @DisplayName("high-level boss has ~10% chance")
        void highLevelBossHas10PercentChance() {
            double chance = listener.calculateDropChance(config, 100, MobType.BOSS);
            // (0.01 + (100 * 0.0001)) * 5 = 0.02 * 5 = 0.10 = 10%
            assertEquals(0.10, chance, 0.001);
        }

        @Test
        @DisplayName("endgame level boss has capped 100% chance")
        void endgameLevelBossHasCappedChance() {
            double chance = listener.calculateDropChance(config, 5000, MobType.BOSS);
            // Would be > 100%, capped to 1.0
            assertEquals(1.0, chance, 0.0001);
        }
    }
}
