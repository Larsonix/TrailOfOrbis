package io.github.larsonix.trailoforbis.maps.reward;

import com.hypixel.hytale.server.core.inventory.ItemStack;

import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig.LevelBlendingConfig;
import io.github.larsonix.trailoforbis.gear.generation.RarityRoller;
import io.github.larsonix.trailoforbis.gear.loot.DropLevelBlender;
import io.github.larsonix.trailoforbis.gear.loot.LootGenerator;
import io.github.larsonix.trailoforbis.maps.core.RealmLayoutSize;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;
import io.github.larsonix.trailoforbis.maps.items.RealmMapGenerator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for VictoryRewardGenerator.
 *
 * <p>Verifies:
 * <ul>
 *   <li>Constructor validation</li>
 *   <li>IIQ bonus roll mechanics (calculateItemCount)</li>
 *   <li>VictoryRewards record behavior</li>
 *   <li>Config accessors</li>
 * </ul>
 *
 * <p>Note: Full integration tests for generate() require Hytale runtime
 * and should be done via manual testing or server integration tests.
 */
@ExtendWith(MockitoExtension.class)
class VictoryRewardGeneratorTest {

    @Mock
    private RealmMapGenerator mapGenerator;

    @Mock
    private LootGenerator lootGenerator;

    @Mock
    private RarityRoller rarityRoller;

    @Mock
    private RealmInstance realmInstance;

    @Mock
    private ItemStack mockItemStack;

    private DropLevelBlender blender;
    private VictoryRewardConfig config;
    private UUID playerId;

    @BeforeEach
    void setUp() {
        blender = new DropLevelBlender(LevelBlendingConfig.DISABLED);
        config = createTestConfig();
        playerId = UUID.randomUUID();
    }

    private VictoryRewardConfig createTestConfig() {
        VictoryRewardConfig cfg = new VictoryRewardConfig();
        // Default values are already set in the config
        return cfg;
    }

    // =========================================================================
    // CONSTRUCTOR TESTS
    // =========================================================================

    @Nested
    @DisplayName("Constructor")
    class Constructor {

        @Test
        @DisplayName("rejects null config")
        void rejectsNullConfig() {
            assertThrows(NullPointerException.class, () ->
                new VictoryRewardGenerator(null, mapGenerator, lootGenerator, rarityRoller, blender));
        }

        @Test
        @DisplayName("rejects null mapGenerator")
        void rejectsNullMapGenerator() {
            assertThrows(NullPointerException.class, () ->
                new VictoryRewardGenerator(config, null, lootGenerator, rarityRoller, blender));
        }

        @Test
        @DisplayName("rejects null lootGenerator")
        void rejectsNullLootGenerator() {
            assertThrows(NullPointerException.class, () ->
                new VictoryRewardGenerator(config, mapGenerator, null, rarityRoller, blender));
        }

        @Test
        @DisplayName("rejects null rarityRoller")
        void rejectsNullRarityRoller() {
            assertThrows(NullPointerException.class, () ->
                new VictoryRewardGenerator(config, mapGenerator, lootGenerator, null, blender));
        }

        @Test
        @DisplayName("creates generator with valid parameters")
        void createsGeneratorWithValidParams() {
            VictoryRewardGenerator generator = new VictoryRewardGenerator(
                config, mapGenerator, lootGenerator, rarityRoller, blender);
            assertNotNull(generator);
            assertSame(config, generator.getConfig());
            assertSame(mapGenerator, generator.getMapGenerator());
            assertSame(lootGenerator, generator.getLootGenerator());
            assertSame(rarityRoller, generator.getRarityRoller());
        }

        @Test
        @DisplayName("creates generator with custom random")
        void createsGeneratorWithCustomRandom() {
            Random customRandom = new Random(42);
            VictoryRewardGenerator generator = new VictoryRewardGenerator(
                config, mapGenerator, lootGenerator, rarityRoller, blender, customRandom);
            assertNotNull(generator);
        }
    }

    // =========================================================================
    // ITEM COUNT CALCULATION TESTS
    // =========================================================================

    @Nested
    @DisplayName("Item Count Calculation")
    class ItemCountCalculation {

        @Test
        @DisplayName("returns 0 when baseCount is 0")
        void returnsZeroWhenBaseCountIsZero() {
            VictoryRewardGenerator generator = new VictoryRewardGenerator(
                config, mapGenerator, lootGenerator, rarityRoller, blender, new Random(42));

            int count = generator.calculateItemCount(0, 0.5);
            assertEquals(0, count);
        }

        @Test
        @DisplayName("returns baseCount when IIQ is 0")
        void returnsBaseCountWhenIiqIsZero() {
            VictoryRewardGenerator generator = new VictoryRewardGenerator(
                config, mapGenerator, lootGenerator, rarityRoller, blender, new Random(42));

            int count = generator.calculateItemCount(1, 0.0);
            assertEquals(1, count);
        }

        @Test
        @DisplayName("IIQ bonus adds extra items probabilistically")
        void iiqBonusAddsExtraItems() {
            // With 100% IIQ, we should always get extra items (statistically)
            int totalWithHighIiq = 0;
            int totalWithNoIiq = 0;

            for (int i = 0; i < 1000; i++) {
                VictoryRewardGenerator gen1 = new VictoryRewardGenerator(
                    config, mapGenerator, lootGenerator, rarityRoller, blender, new Random(i));
                VictoryRewardGenerator gen2 = new VictoryRewardGenerator(
                    config, mapGenerator, lootGenerator, rarityRoller, blender, new Random(i + 10000));

                totalWithNoIiq += gen1.calculateItemCount(1, 0.0);
                totalWithHighIiq += gen2.calculateItemCount(1, 1.0); // 100% IIQ
            }

            assertTrue(totalWithHighIiq > totalWithNoIiq,
                "High IIQ should produce more items. No IIQ: " + totalWithNoIiq + ", High IIQ: " + totalWithHighIiq);
        }

        @Test
        @DisplayName("bonus count capped by maxBonusPerType")
        void bonusCountCappedByMax() {
            VictoryRewardConfig cappedConfig = new VictoryRewardConfig();
            cappedConfig.setMaxBonusPerType(2); // Max 2 bonus items

            // With guaranteed IIQ (using a controlled random that always succeeds)
            VictoryRewardGenerator generator = new VictoryRewardGenerator(
                cappedConfig, mapGenerator, lootGenerator, rarityRoller, blender, new Random(42));

            // Even with 1000% IIQ, can't exceed base + maxBonus
            int count = generator.calculateItemCount(1, 10.0);
            assertTrue(count <= 1 + cappedConfig.getMaxBonusPerType(),
                "Count should not exceed base + maxBonus, was: " + count);
        }

        @Test
        @DisplayName("returns exact base count for negative IIQ")
        void returnsBaseCountForNegativeIiq() {
            VictoryRewardGenerator generator = new VictoryRewardGenerator(
                config, mapGenerator, lootGenerator, rarityRoller, blender, new Random(42));

            // Negative IIQ should be treated as 0 (no bonus)
            int count = generator.calculateItemCount(1, -0.5);
            assertEquals(1, count, "Negative IIQ should not reduce below base count");
        }

        @Test
        @DisplayName("handles large base count")
        void handlesLargeBaseCount() {
            VictoryRewardGenerator generator = new VictoryRewardGenerator(
                config, mapGenerator, lootGenerator, rarityRoller, blender, new Random(42));

            int count = generator.calculateItemCount(5, 0.5);
            assertTrue(count >= 5, "Count should be at least base count");
        }
    }

    // =========================================================================
    // VICTORY REWARDS RECORD TESTS
    // =========================================================================

    @Nested
    @DisplayName("VictoryRewards Record")
    class VictoryRewardsRecord {

        @Test
        @DisplayName("empty() returns empty rewards")
        void emptyReturnsEmptyRewards() {
            VictoryRewardGenerator.VictoryRewards empty = VictoryRewardGenerator.VictoryRewards.empty();

            assertFalse(empty.hasRewards());
            assertEquals(0, empty.totalCount());
            assertTrue(empty.maps().isEmpty());
            assertTrue(empty.gear().isEmpty());
            assertTrue(empty.stones().isEmpty());
            assertTrue(empty.allItems().isEmpty());
        }

        @Test
        @DisplayName("totalCount sums all item types")
        void totalCountSumsAllTypes() {
            List<ItemStack> maps = List.of(mockItemStack);
            List<ItemStack> gear = List.of(mockItemStack, mockItemStack);
            List<ItemStack> stones = List.of(mockItemStack, mockItemStack, mockItemStack);

            VictoryRewardGenerator.VictoryRewards rewards =
                new VictoryRewardGenerator.VictoryRewards(maps, gear, stones);

            assertEquals(6, rewards.totalCount());
        }

        @Test
        @DisplayName("hasRewards returns true when non-empty")
        void hasRewardsReturnsTrue() {
            List<ItemStack> maps = List.of(mockItemStack);
            VictoryRewardGenerator.VictoryRewards rewards =
                new VictoryRewardGenerator.VictoryRewards(maps, List.of(), List.of());

            assertTrue(rewards.hasRewards());
        }

        @Test
        @DisplayName("allItems combines all lists")
        void allItemsCombinesAllLists() {
            List<ItemStack> maps = List.of(mockItemStack);
            List<ItemStack> gear = List.of(mockItemStack);
            List<ItemStack> stones = List.of(mockItemStack);

            VictoryRewardGenerator.VictoryRewards rewards =
                new VictoryRewardGenerator.VictoryRewards(maps, gear, stones);

            assertEquals(3, rewards.allItems().size());
        }

        @Test
        @DisplayName("lists are immutable")
        void listsAreImmutable() {
            List<ItemStack> maps = new ArrayList<>();
            maps.add(mockItemStack);

            VictoryRewardGenerator.VictoryRewards rewards =
                new VictoryRewardGenerator.VictoryRewards(maps, List.of(), List.of());

            assertThrows(UnsupportedOperationException.class, () -> rewards.maps().add(mockItemStack));
        }

        @Test
        @DisplayName("allItems returns new list each call")
        void allItemsReturnsNewList() {
            List<ItemStack> maps = List.of(mockItemStack);
            VictoryRewardGenerator.VictoryRewards rewards =
                new VictoryRewardGenerator.VictoryRewards(maps, List.of(), List.of());

            List<ItemStack> first = rewards.allItems();
            List<ItemStack> second = rewards.allItems();

            assertNotSame(first, second, "allItems should return a new list each call");
        }
    }

    // =========================================================================
    // CONFIG TESTS
    // =========================================================================

    @Nested
    @DisplayName("VictoryRewardConfig")
    class ConfigTests {

        @Test
        @DisplayName("default config has correct size rewards")
        void defaultConfigHasCorrectSizeRewards() {
            VictoryRewardConfig cfg = new VictoryRewardConfig();

            // SMALL: 2 random items
            var small = cfg.getSizeRewards(RealmLayoutSize.SMALL);
            assertEquals(2, small.getTotalItems());
            assertEquals(0, small.getBonusIir());
            assertEquals(0, small.getBonusIiq());

            // MEDIUM: 3 random items
            var medium = cfg.getSizeRewards(RealmLayoutSize.MEDIUM);
            assertEquals(3, medium.getTotalItems());
            assertEquals(0, medium.getBonusIir());
            assertEquals(0, medium.getBonusIiq());

            // LARGE: 3 random items + 25% IIR
            var large = cfg.getSizeRewards(RealmLayoutSize.LARGE);
            assertEquals(3, large.getTotalItems());
            assertEquals(25, large.getBonusIir());
            assertEquals(0, large.getBonusIiq());

            // MASSIVE: 3 random items + 50% IIR + 10% IIQ
            var massive = cfg.getSizeRewards(RealmLayoutSize.MASSIVE);
            assertEquals(3, massive.getTotalItems());
            assertEquals(50, massive.getBonusIir());
            assertEquals(10, massive.getBonusIiq());
        }

        @Test
        @DisplayName("default map level variance is 1")
        void defaultMapLevelVarianceIsOne() {
            VictoryRewardConfig cfg = new VictoryRewardConfig();
            assertEquals(1, cfg.getMapLevelVariance());
        }

        @Test
        @DisplayName("default gear level offsets are -3 to 0")
        void defaultGearLevelOffsetsAreCorrect() {
            VictoryRewardConfig cfg = new VictoryRewardConfig();
            assertEquals(-3, cfg.getGearLevelMinOffset());
            assertEquals(0, cfg.getGearLevelMaxOffset());
        }

        @Test
        @DisplayName("default max bonus per type is 2")
        void defaultMaxBonusPerTypeIsTwo() {
            VictoryRewardConfig cfg = new VictoryRewardConfig();
            assertEquals(2, cfg.getMaxBonusPerType());
        }

        @Test
        @DisplayName("calculateMapLevel respects variance")
        void calculateMapLevelRespectsVariance() {
            VictoryRewardConfig cfg = new VictoryRewardConfig();
            cfg.setMapLevelVariance(2); // ±2

            Set<Integer> levels = new HashSet<>();
            Random random = new Random(42);
            for (int i = 0; i < 1000; i++) {
                levels.add(cfg.calculateMapLevel(50, random));
            }

            // Should have levels between 48 and 52 inclusive
            assertTrue(levels.stream().allMatch(l -> l >= 48 && l <= 52),
                "All levels should be within ±2 of 50, but got: " + levels);
            // Should have variety
            assertTrue(levels.size() > 1, "Should have multiple different levels");
        }

        @Test
        @DisplayName("calculateGearLevel respects offsets")
        void calculateGearLevelRespectsOffsets() {
            VictoryRewardConfig cfg = new VictoryRewardConfig();
            // Default: -3 to 0

            Set<Integer> levels = new HashSet<>();
            Random random = new Random(42);
            for (int i = 0; i < 1000; i++) {
                levels.add(cfg.calculateGearLevel(50, random));
            }

            // Should have levels between 47 and 50 inclusive
            assertTrue(levels.stream().allMatch(l -> l >= 47 && l <= 50),
                "All levels should be between 47-50, but got: " + levels);
        }
    }

    // =========================================================================
    // SIZE REWARDS TESTS
    // =========================================================================

    @Nested
    @DisplayName("SizeRewards")
    class SizeRewardsTests {

        @Test
        @DisplayName("setters update values")
        void settersUpdateValues() {
            VictoryRewardConfig.SizeRewards rewards = new VictoryRewardConfig.SizeRewards();
            rewards.setTotalItems(3);
            rewards.setBonusIir(25);
            rewards.setBonusIiq(10);

            assertEquals(3, rewards.getTotalItems());
            assertEquals(25, rewards.getBonusIir());
            assertEquals(10, rewards.getBonusIiq());
        }

    }
}
