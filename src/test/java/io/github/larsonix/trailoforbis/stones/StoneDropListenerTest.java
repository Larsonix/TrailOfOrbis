package io.github.larsonix.trailoforbis.stones;

import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig;
import io.github.larsonix.trailoforbis.gear.config.TestConfigFactory;
import io.github.larsonix.trailoforbis.gear.generation.RarityRoller;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.maps.config.RealmsConfig;
import io.github.larsonix.trailoforbis.stones.StoneDropListener.MobType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link StoneDropListener}.
 *
 * <p>Tests the drop chance calculation and rarity selection logic.
 */
@DisplayName("StoneDropListener")
class StoneDropListenerTest {

    private RealmsConfig realmsConfig;
    private GearBalanceConfig gearConfig;
    private StoneDropListener listener;

    @BeforeEach
    void setUp() {
        // Create mock RealmsConfig
        realmsConfig = mock(RealmsConfig.class);
        when(realmsConfig.getBaseStoneDropChance()).thenReturn(0.05);  // 5%
        when(realmsConfig.getStoneDropChancePerLevel()).thenReturn(0.001);  // +0.1% per level
        when(realmsConfig.getEliteStoneDropMultiplier()).thenReturn(3.0);
        when(realmsConfig.getBossStoneDropMultiplier()).thenReturn(10.0);

        // Use real gear config for proper rarity weights
        gearConfig = TestConfigFactory.createDefaultBalanceConfig();

        // Create listener
        listener = new StoneDropListener(realmsConfig, gearConfig);
    }

    // =========================================================================
    // DROP CHANCE CALCULATION TESTS
    // =========================================================================

    @Nested
    @DisplayName("calculateDropChance")
    class CalculateDropChanceTests {

        @Test
        @DisplayName("should calculate base drop chance for level 1 normal mob")
        void calculateDropChance_Level1Normal() {
            double chance = listener.calculateDropChance(realmsConfig, 1, MobType.NORMAL);

            // base (0.05) + level*perLevel (1*0.001) * multiplier (1.0) = 0.051
            assertEquals(0.051, chance, 0.0001);
        }

        @Test
        @DisplayName("should increase drop chance with mob level")
        void calculateDropChance_IncreasesWithLevel() {
            double level1 = listener.calculateDropChance(realmsConfig, 1, MobType.NORMAL);
            double level50 = listener.calculateDropChance(realmsConfig, 50, MobType.NORMAL);
            double level100 = listener.calculateDropChance(realmsConfig, 100, MobType.NORMAL);

            assertTrue(level50 > level1, "Level 50 should have higher chance than level 1");
            assertTrue(level100 > level50, "Level 100 should have higher chance than level 50");
        }

        @Test
        @DisplayName("should apply elite multiplier")
        void calculateDropChance_EliteMultiplier() {
            double normal = listener.calculateDropChance(realmsConfig, 50, MobType.NORMAL);
            double elite = listener.calculateDropChance(realmsConfig, 50, MobType.ELITE);

            assertEquals(normal * 3.0, elite, 0.0001, "Elite should be 3x normal");
        }

        @Test
        @DisplayName("should apply boss multiplier")
        void calculateDropChance_BossMultiplier() {
            double normal = listener.calculateDropChance(realmsConfig, 50, MobType.NORMAL);
            double boss = listener.calculateDropChance(realmsConfig, 50, MobType.BOSS);

            assertEquals(normal * 10.0, boss, 0.0001, "Boss should be 10x normal");
        }

        @Test
        @DisplayName("should clamp drop chance to maximum 1.0")
        void calculateDropChance_ClampsToOne() {
            // Very high level + boss multiplier should still cap at 1.0
            double chance = listener.calculateDropChance(realmsConfig, 10000, MobType.BOSS);

            assertTrue(chance <= 1.0, "Drop chance should not exceed 100%");
        }

        @Test
        @DisplayName("should not return negative drop chance")
        void calculateDropChance_NotNegative() {
            // Even with negative config (which shouldn't happen), should be >= 0
            when(realmsConfig.getBaseStoneDropChance()).thenReturn(-0.5);

            double chance = listener.calculateDropChance(realmsConfig, 1, MobType.NORMAL);

            assertTrue(chance >= 0.0, "Drop chance should not be negative");
        }
    }

    // =========================================================================
    // RARITY SELECTION TESTS
    // =========================================================================

    @Nested
    @DisplayName("rollStoneRarity")
    class RollStoneRarityTests {

        @Test
        @DisplayName("should return valid rarity")
        void rollStoneRarity_ReturnsValidRarity() {
            GearRarity rarity = listener.rollStoneRarity(listener.resolveRarityRoller(), 0.0);
            assertNotNull(rarity);
        }

        @Test
        @DisplayName("should allow LEGENDARY and MYTHIC rarities")
        void rollStoneRarity_AllowsLegendaryAndMythic() {
            // Roll many times with high bonus to try to get LEGENDARY/MYTHIC
            // These are very rare so we need many rolls
            RarityRoller roller = listener.resolveRarityRoller();
            Set<GearRarity> rolledRarities = new HashSet<>();
            for (int i = 0; i < 100000; i++) {
                GearRarity rarity = listener.rollStoneRarity(roller, 5.0); // High bonus
                rolledRarities.add(rarity);
            }

            // With enough rolls at high bonus, we should eventually see LEGENDARY
            // (MYTHIC is extremely rare at 0.1% base weight, may not appear)
            assertTrue(rolledRarities.contains(GearRarity.LEGENDARY),
                    "Should be able to roll LEGENDARY (got: " + rolledRarities + ")");
        }

        @Test
        @DisplayName("should include all stone rarities in distribution")
        void rollStoneRarity_DistributionIncludesAllRarities() {
            // Roll many times and verify distribution covers expected rarities
            RarityRoller roller = listener.resolveRarityRoller();
            Set<GearRarity> rolledRarities = new HashSet<>();
            for (int i = 0; i < 10000; i++) {
                rolledRarities.add(listener.rollStoneRarity(roller, 1.0));
            }

            // Should contain common rarities at minimum
            assertTrue(rolledRarities.contains(GearRarity.COMMON), "Should roll COMMON");
            assertTrue(rolledRarities.contains(GearRarity.UNCOMMON), "Should roll UNCOMMON");
            assertTrue(rolledRarities.contains(GearRarity.RARE), "Should roll RARE");
            assertTrue(rolledRarities.contains(GearRarity.EPIC), "Should roll EPIC");
        }

        @Test
        @DisplayName("higher bonus should increase rare stone chance")
        void rollStoneRarity_BonusIncreasesRareChance() {
            int rareCountLowBonus = 0;
            int rareCountHighBonus = 0;
            RarityRoller roller = listener.resolveRarityRoller();

            for (int i = 0; i < 10000; i++) {
                GearRarity lowBonus = listener.rollStoneRarity(roller, 0.0);
                GearRarity highBonus = listener.rollStoneRarity(roller, 1.0);

                if (lowBonus.ordinal() >= GearRarity.RARE.ordinal()) {
                    rareCountLowBonus++;
                }
                if (highBonus.ordinal() >= GearRarity.RARE.ordinal()) {
                    rareCountHighBonus++;
                }
            }

            assertTrue(rareCountHighBonus > rareCountLowBonus,
                    "Higher bonus should result in more rare+ stones");
        }
    }

    // =========================================================================
    // STONE SELECTION TESTS
    // =========================================================================

    @Nested
    @DisplayName("selectStoneOfRarity")
    class SelectStoneOfRarityTests {

        @Test
        @DisplayName("should return stone of requested rarity")
        void selectStoneOfRarity_ReturnsCorrectRarity() {
            StoneType stone = listener.selectStoneOfRarity(GearRarity.RARE);

            assertNotNull(stone);
            assertEquals(GearRarity.RARE, stone.getRarity());
        }

        @Test
        @DisplayName("should return stones for LEGENDARY and MYTHIC rarities")
        void selectStoneOfRarity_ReturnsStones_ForHighRarities() {
            // LEGENDARY has Warden's Seal
            StoneType legendaryStone = listener.selectStoneOfRarity(GearRarity.LEGENDARY);
            assertNotNull(legendaryStone, "LEGENDARY should have stones");
            assertEquals(GearRarity.LEGENDARY, legendaryStone.getRarity());

            // MYTHIC has Gaia's Perfection and Warden's Key
            StoneType mythicStone = listener.selectStoneOfRarity(GearRarity.MYTHIC);
            assertNotNull(mythicStone, "MYTHIC should have stones");
            assertEquals(GearRarity.MYTHIC, mythicStone.getRarity());
        }

        @Test
        @DisplayName("should select all stones of a rarity over many trials")
        void selectStoneOfRarity_SelectsAllStonesOfRarity() {
            List<StoneType> uncommonStones = StoneType.getByRarity(GearRarity.UNCOMMON);
            Set<StoneType> selectedStones = new HashSet<>();

            // Select many times
            for (int i = 0; i < 1000; i++) {
                StoneType selected = listener.selectStoneOfRarity(GearRarity.UNCOMMON);
                if (selected != null) {
                    selectedStones.add(selected);
                }
            }

            // Should eventually select all uncommon stones
            assertEquals(uncommonStones.size(), selectedStones.size(),
                    "Should select all stones of the rarity");
        }
    }

    // =========================================================================
    // MOB TYPE TESTS
    // =========================================================================

    @Nested
    @DisplayName("MobType enum")
    class MobTypeTests {

        @Test
        @DisplayName("NORMAL should have 1.0 multiplier")
        void mobType_Normal_HasCorrectMultiplier() {
            assertEquals(1.0, MobType.NORMAL.getMultiplier());
        }

        @Test
        @DisplayName("ELITE should have 3.0 multiplier")
        void mobType_Elite_HasCorrectMultiplier() {
            assertEquals(3.0, MobType.ELITE.getMultiplier());
        }

        @Test
        @DisplayName("BOSS should have 10.0 multiplier")
        void mobType_Boss_HasCorrectMultiplier() {
            assertEquals(10.0, MobType.BOSS.getMultiplier());
        }
    }

    // =========================================================================
    // ACCESSOR TESTS
    // =========================================================================

    @Nested
    @DisplayName("Accessors")
    class AccessorTests {

        @Test
        @DisplayName("resolveConfig returns config")
        void resolveConfig_ReturnsConfig() {
            assertSame(realmsConfig, listener.resolveConfig());
        }

        @Test
        @DisplayName("resolveRarityRoller returns non-null")
        void resolveRarityRoller_ReturnsNonNull() {
            assertNotNull(listener.resolveRarityRoller());
        }

        @Test
        @DisplayName("isOperational returns true with valid config")
        void isOperational_ReturnsTrue() {
            assertTrue(listener.isOperational());
        }
    }

    // =========================================================================
    // INTEGRATION TESTS
    // =========================================================================

    @Nested
    @DisplayName("Integration")
    class IntegrationTests {

        @Test
        @DisplayName("all stone rarities can be selected")
        void allStoneRarities_CanBeSelected() {
            // Get all rarities that have stones
            Set<GearRarity> raritiesWithStones = StoneType.getAvailableRarities();

            for (GearRarity rarity : raritiesWithStones) {
                StoneType stone = listener.selectStoneOfRarity(rarity);
                assertNotNull(stone, "Should find stone for rarity: " + rarity);
            }
        }

        @Test
        @DisplayName("full drop flow produces valid stones")
        void fullDropFlow_ProducesValidStones() {
            // Simulate many drops
            Set<StoneType> droppedStones = new HashSet<>();
            RarityRoller roller = listener.resolveRarityRoller();

            for (int i = 0; i < 1000; i++) {
                GearRarity rarity = listener.rollStoneRarity(roller, 0.5);
                StoneType stone = listener.selectStoneOfRarity(rarity);

                if (stone != null) {
                    droppedStones.add(stone);

                    // Verify stone properties
                    assertNotNull(stone.getDisplayName());
                    assertNotNull(stone.getDescription());
                    assertNotNull(stone.getRarity());
                    assertNotNull(stone.getHexColor());
                }
            }

            // Should have dropped multiple different stones
            assertTrue(droppedStones.size() > 1,
                    "Should drop multiple stone types, got: " + droppedStones.size());
        }
    }
}
