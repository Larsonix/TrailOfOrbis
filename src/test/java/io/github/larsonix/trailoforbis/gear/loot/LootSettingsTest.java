package io.github.larsonix.trailoforbis.gear.loot;

import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig.*;
import io.github.larsonix.trailoforbis.gear.config.TestConfigFactory;
import io.github.larsonix.trailoforbis.gear.loot.LootSettings.MobBonus;
import io.github.larsonix.trailoforbis.gear.loot.LootSettings.MobType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for LootSettings - 10 test cases.
 */
@ExtendWith(MockitoExtension.class)
class LootSettingsTest {

    private GearBalanceConfig balanceConfig;

    @BeforeEach
    void setUp() {
        balanceConfig = TestConfigFactory.createDefaultBalanceConfig();
    }

    // =========================================================================
    // CONSTRUCTOR TESTS
    // =========================================================================

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("constructor with full config loads all values")
        void constructor_WithFullConfig_LoadsAllValues() {
            LootSettings settings = new LootSettings(balanceConfig);

            // Should have loaded values from config (not defaults)
            assertNotNull(settings);
            assertTrue(settings.getBaseDropChance() >= 0);
            assertTrue(settings.getLuckToRarityPercent() >= 0);
        }

        @Test
        @DisplayName("constructor with null loot section uses defaults")
        void constructor_WithNullLootSection_UsesDefaults() {
            // Create a mock config that returns null for loot()
            GearBalanceConfig mockConfig = mock(GearBalanceConfig.class);
            when(mockConfig.loot()).thenReturn(null);

            LootSettings settings = new LootSettings(mockConfig);

            // Should use default values
            assertEquals(0.05, settings.getBaseDropChance());
            assertEquals(0.5, settings.getLuckToRarityPercent());
            assertTrue(settings.isDistanceScalingEnabled());
            assertEquals(100, settings.getBlocksPerPercent());
            assertEquals(50.0, settings.getMaxDistanceBonus());
        }

        @Test
        @DisplayName("constructor throws NullPointerException for null config")
        void constructor_WithNullConfig_ThrowsNPE() {
            assertThrows(NullPointerException.class, () -> new LootSettings(null));
        }
    }

    // =========================================================================
    // MOB BONUS TESTS
    // =========================================================================

    @Nested
    @DisplayName("Mob Bonus Tests")
    class MobBonusTests {

        @Test
        @DisplayName("getMobBonus for NORMAL returns no bonus")
        void getMobBonus_Normal_NoBonus() {
            LootSettings settings = new LootSettings(balanceConfig);

            MobBonus bonus = settings.getMobBonus(MobType.NORMAL);

            assertEquals(0, bonus.quantityBonus());
            assertEquals(0, bonus.rarityBonus());
        }

        @Test
        @DisplayName("getMobBonus for ELITE has bonus")
        void getMobBonus_Elite_HasBonus() {
            // Create settings with explicit values
            LootSettings settings = new LootSettings(
                0.05, 0.5, true, 100, 50.0,
                Map.of(
                    MobType.NORMAL, MobBonus.NONE,
                    MobType.ELITE, new MobBonus(0.5, 0.25),
                    MobType.BOSS, new MobBonus(1.0, 0.5)
                )
            );

            MobBonus bonus = settings.getMobBonus(MobType.ELITE);

            assertEquals(0.5, bonus.quantityBonus());
            assertEquals(0.25, bonus.rarityBonus());
        }

        @Test
        @DisplayName("getMobBonus for BOSS has higher bonus")
        void getMobBonus_Boss_HasHigherBonus() {
            LootSettings settings = new LootSettings(
                0.05, 0.5, true, 100, 50.0,
                Map.of(
                    MobType.NORMAL, MobBonus.NONE,
                    MobType.ELITE, new MobBonus(0.5, 0.25),
                    MobType.BOSS, new MobBonus(1.0, 0.5)
                )
            );

            MobBonus bonus = settings.getMobBonus(MobType.BOSS);

            assertEquals(1.0, bonus.quantityBonus());
            assertEquals(0.5, bonus.rarityBonus());
        }
    }

    // =========================================================================
    // QUANTITY MULTIPLIER TESTS
    // =========================================================================

    @Nested
    @DisplayName("Quantity Multiplier Tests")
    class QuantityMultiplierTests {

        @Test
        @DisplayName("getQuantityMultiplier for NORMAL returns 1.0")
        void getQuantityMultiplier_Normal_Returns1() {
            LootSettings settings = new LootSettings(
                0.05, 0.5, true, 100, 50.0,
                Map.of(
                    MobType.NORMAL, MobBonus.NONE,
                    MobType.ELITE, new MobBonus(0.5, 0.25),
                    MobType.BOSS, new MobBonus(1.0, 0.5)
                )
            );

            assertEquals(1.0, settings.getQuantityMultiplier(MobType.NORMAL));
        }

        @Test
        @DisplayName("getQuantityMultiplier for ELITE returns 1.5")
        void getQuantityMultiplier_Elite_Returns1Point5() {
            LootSettings settings = new LootSettings(
                0.05, 0.5, true, 100, 50.0,
                Map.of(
                    MobType.NORMAL, MobBonus.NONE,
                    MobType.ELITE, new MobBonus(0.5, 0.25),
                    MobType.BOSS, new MobBonus(1.0, 0.5)
                )
            );

            assertEquals(1.5, settings.getQuantityMultiplier(MobType.ELITE));
        }

        @Test
        @DisplayName("getQuantityMultiplier for BOSS returns 2.0")
        void getQuantityMultiplier_Boss_Returns2() {
            LootSettings settings = new LootSettings(
                0.05, 0.5, true, 100, 50.0,
                Map.of(
                    MobType.NORMAL, MobBonus.NONE,
                    MobType.ELITE, new MobBonus(0.5, 0.25),
                    MobType.BOSS, new MobBonus(1.0, 0.5)
                )
            );

            assertEquals(2.0, settings.getQuantityMultiplier(MobType.BOSS));
        }
    }

    // =========================================================================
    // DISTANCE SCALING TESTS
    // =========================================================================

    @Nested
    @DisplayName("Distance Scaling Tests")
    class DistanceScalingTests {

        @Test
        @DisplayName("isDistanceScalingEnabled returns configured value")
        void isDistanceScalingEnabled_Default_True() {
            LootSettings settings = new LootSettings(
                0.05, 0.5, true, 100, 50.0,
                Map.of(MobType.NORMAL, MobBonus.NONE, MobType.ELITE, MobBonus.NONE, MobType.BOSS, MobBonus.NONE)
            );

            assertTrue(settings.isDistanceScalingEnabled());
        }

        @Test
        @DisplayName("getMaxDistanceBonus returns configured value")
        void getMaxDistanceBonus_Default_50() {
            LootSettings settings = new LootSettings(
                0.05, 0.5, true, 100, 50.0,
                Map.of(MobType.NORMAL, MobBonus.NONE, MobType.ELITE, MobBonus.NONE, MobType.BOSS, MobBonus.NONE)
            );

            assertEquals(50.0, settings.getMaxDistanceBonus());
        }
    }
}
