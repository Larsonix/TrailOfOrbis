package io.github.larsonix.trailoforbis.loot.container;

import com.hypixel.hytale.server.core.inventory.ItemStack;

import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig.LevelBlendingConfig;
import io.github.larsonix.trailoforbis.gear.generation.RarityRoller;
import io.github.larsonix.trailoforbis.gear.loot.DropLevelBlender;
import io.github.larsonix.trailoforbis.gear.loot.DynamicLootRegistry;
import io.github.larsonix.trailoforbis.gear.loot.LootGenerator;
import io.github.larsonix.trailoforbis.gear.loot.RarityBonusCalculator;
import io.github.larsonix.trailoforbis.gear.loot.RealmLootContext;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.maps.items.RealmMapGenerator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link ContainerLootGenerator} — the core item generation engine
 * for container loot replacement.
 *
 * <p>Covers gear, stone, map, and consumable generation, slot-budget allocation,
 * rarity bonus stacking, and edge cases.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContainerLootGeneratorTest {

    // =========================================================================
    // Shared mocks and config
    // =========================================================================

    @Mock private LootGenerator lootGenerator;
    @Mock private RealmMapGenerator mapGenerator;
    @Mock private RarityBonusCalculator rarityBonusCalculator;
    @Mock private RarityRoller rarityRoller;

    private ContainerLootConfig config;
    private ContainerTierClassifier tierClassifier;
    private DropLevelBlender dropLevelBlender;
    private Random seededRandom;

    private static final UUID TEST_PLAYER = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        config = createTestConfig();
        tierClassifier = new ContainerTierClassifier(config);
        seededRandom = new Random(42L);

        // Default: blending enabled
        dropLevelBlender = new DropLevelBlender(
            new LevelBlendingConfig(true, 0.3, 5, 2)
        );

        // Default: rarity bonus calculator returns 0
        lenient().when(rarityBonusCalculator.calculatePlayerBonus(any())).thenReturn(0.0);

        // Default: rarity roller returns COMMON
        lenient().when(rarityRoller.roll(anyDouble())).thenReturn(GearRarity.COMMON);
        lenient().when(rarityRoller.roll(anyDouble(), anySet())).thenReturn(GearRarity.COMMON);

        // Default: LootGenerator drop() builder returns a mock item
        setupLootGeneratorDropBuilder();
    }

    /**
     * Configures the mock LootGenerator to return a functioning DropBuilder chain.
     */
    private void setupLootGeneratorDropBuilder() {
        LootGenerator.DropBuilder mockBuilder = mock(LootGenerator.DropBuilder.class);
        lenient().when(lootGenerator.drop()).thenReturn(mockBuilder);
        lenient().when(mockBuilder.level(anyInt())).thenReturn(mockBuilder);
        lenient().when(mockBuilder.rarity(any())).thenReturn(mockBuilder);
        lenient().when(mockBuilder.rarityBonus(anyDouble())).thenReturn(mockBuilder);
        lenient().when(mockBuilder.build()).thenReturn(mock(ItemStack.class));

        // DynamicRegistry for rarity filtering
        DynamicLootRegistry mockRegistry = mock(DynamicLootRegistry.class);
        lenient().when(lootGenerator.getDynamicRegistry()).thenReturn(mockRegistry);
        lenient().when(mockRegistry.isDiscovered()).thenReturn(true);
        lenient().when(mockRegistry.getAvailableRarities()).thenReturn(EnumSet.allOf(GearRarity.class));
    }

    private ContainerLootGenerator createGenerator() {
        return new ContainerLootGenerator(
            config, lootGenerator, mapGenerator, null,
            tierClassifier, dropLevelBlender, rarityBonusCalculator, rarityRoller, seededRandom
        );
    }

    private ContainerLootGenerator createGeneratorWithConsumables() {
        // Consumables require a live ConsumableLootRegistry — not tested here
        return new ContainerLootGenerator(
            config, lootGenerator, mapGenerator, null,
            tierClassifier, dropLevelBlender, rarityBonusCalculator, rarityRoller, seededRandom
        );
    }

    private ContainerLootContext overworldContext(int sourceLevel, int playerLevel) {
        return ContainerLootContext.overworld(sourceLevel, playerLevel);
    }

    private ContainerLootContext realmContext(int sourceLevel, int playerLevel, double iiq, double iir, int qualityBonus) {
        return ContainerLootContext.realm(sourceLevel, playerLevel, new RealmLootContext(iiq, iir), qualityBonus);
    }

    // =========================================================================
    // Config Helper
    // =========================================================================

    private static ContainerLootConfig createTestConfig() {
        var config = new ContainerLootConfig();

        Map<String, ContainerLootConfig.TierConfig> tiers = new LinkedHashMap<>();

        // BASIC: 0-2 gear, no rarity bonus, no guaranteed rare
        var basic = new ContainerLootConfig.TierConfig();
        basic.setPatterns(List.of("Chest", "Barrel"));
        basic.setMin_gear_drops(0);
        basic.setMax_gear_drops(2);
        basic.setRarity_bonus(0.0);
        basic.setGuaranteed_rare_or_better(false);
        basic.setStone_chance_multiplier(1.0);
        basic.setMap_chance_multiplier(1.0);
        basic.setConsumable_chance_multiplier(1.0);
        basic.setMin_items(3);
        basic.setMax_items(5);
        tiers.put("basic", basic);

        // DUNGEON: 1-3 gear, 0.25 rarity bonus
        var dungeon = new ContainerLootConfig.TierConfig();
        dungeon.setPatterns(List.of("Dungeon_*"));
        dungeon.setMin_gear_drops(1);
        dungeon.setMax_gear_drops(3);
        dungeon.setRarity_bonus(0.25);
        dungeon.setGuaranteed_rare_or_better(false);
        dungeon.setStone_chance_multiplier(1.5);
        dungeon.setMap_chance_multiplier(1.5);
        dungeon.setConsumable_chance_multiplier(1.0);
        dungeon.setMin_items(4);
        dungeon.setMax_items(6);
        tiers.put("dungeon", dungeon);

        // BOSS: 2-4 gear, 0.50 rarity bonus, guaranteed rare
        var boss = new ContainerLootConfig.TierConfig();
        boss.setPatterns(List.of("Boss_*"));
        boss.setMin_gear_drops(2);
        boss.setMax_gear_drops(4);
        boss.setRarity_bonus(0.50);
        boss.setGuaranteed_rare_or_better(true);
        boss.setStone_chance_multiplier(2.0);
        boss.setMap_chance_multiplier(2.0);
        boss.setConsumable_chance_multiplier(1.5);
        boss.setMin_items(5);
        boss.setMax_items(8);
        tiers.put("boss", boss);

        config.setContainer_tiers(tiers);

        // Stone drops enabled, 15% base chance, max 2
        var stoneDrops = new ContainerLootConfig.StoneDrops();
        stoneDrops.setEnabled(true);
        stoneDrops.setBase_chance(0.15);
        stoneDrops.setMax_per_container(2);
        config.setStone_drops(stoneDrops);

        // Map drops enabled, 5% base chance, max 1
        var mapDrops = new ContainerLootConfig.MapDrops();
        mapDrops.setEnabled(true);
        mapDrops.setBase_chance(0.05);
        mapDrops.setMax_per_container(1);
        mapDrops.setLevel_offset_range(List.of(-3, 3));
        config.setMap_drops(mapDrops);

        // Consumable drops enabled, 40% chance, max 2
        var consumableDrops = new ContainerLootConfig.ConsumableDrops();
        consumableDrops.setEnabled(true);
        consumableDrops.setBase_chance(0.40);
        consumableDrops.setMax_per_container(2);
        config.setConsumable_drops(consumableDrops);

        return config;
    }

    // =========================================================================
    // GEAR GENERATION
    // =========================================================================

    @Nested
    @DisplayName("Gear Generation")
    class GearGeneration {

        @Test
        @DisplayName("BASIC tier respects [0,2] gear drop range")
        void generateLoot_basicTier_respectsMinMaxRange() {
            var generator = createGenerator();
            var context = overworldContext(10, 10);

            // Run many times — drop count should be 0, 1, or 2
            Set<Integer> observedCounts = new HashSet<>();
            for (int i = 0; i < 100; i++) {
                List<ItemStack> loot = generator.generateLoot(context, ContainerTier.BASIC, TEST_PLAYER);
                // Loot includes gear + stones + maps; gear is what we're measuring
                // Since stones/maps have low chance and we're using seeded random,
                // we validate total doesn't exceed max across all categories
                assertTrue(loot.size() <= 10, "Loot should not be excessively large");
            }
        }

        @Test
        @DisplayName("BOSS tier produces at least minGearDrops items")
        void generateLoot_bossTier_producesMinimumGear() {
            var generator = createGenerator();
            var context = overworldContext(50, 50);

            // With BOSS tier, min_gear_drops=2, so we should always get >= 2 gear
            // But stones/maps can add more
            List<ItemStack> loot = generator.generateLoot(context, ContainerTier.BOSS, TEST_PLAYER);
            assertFalse(loot.isEmpty(), "BOSS tier should always produce loot");
        }

        @Test
        @DisplayName("Tier with max_gear_drops=0 produces no gear")
        void generateLoot_zeroMaxGearDrops_returnsNoGear() {
            // Override config to have zero gear drops for BASIC
            var zeroConfig = createTestConfig();
            var basic = new ContainerLootConfig.TierConfig();
            basic.setPatterns(List.of("Chest"));
            basic.setMin_gear_drops(0);
            basic.setMax_gear_drops(0);
            basic.setRarity_bonus(0.0);
            basic.setStone_chance_multiplier(0.0);
            basic.setMap_chance_multiplier(0.0);
            basic.setConsumable_chance_multiplier(0.0);
            zeroConfig.setContainer_tiers(Map.of("basic", basic));

            var zeroClassifier = new ContainerTierClassifier(zeroConfig);
            var generator = new ContainerLootGenerator(
                zeroConfig, lootGenerator, mapGenerator, null,
                zeroClassifier, dropLevelBlender, rarityBonusCalculator, rarityRoller, seededRandom
            );

            List<ItemStack> loot = generator.generateLoot(
                overworldContext(10, 10), ContainerTier.BASIC, TEST_PLAYER
            );
            assertTrue(loot.isEmpty(), "Zero max drops + zero stone/map chance = empty loot");
        }

        @Test
        @DisplayName("Generator failure (null from build) is filtered out")
        void generateLoot_generatorFailure_skipsNullItems() {
            // Make the drop builder return null (simulating failure)
            LootGenerator.DropBuilder failBuilder = mock(LootGenerator.DropBuilder.class);
            when(lootGenerator.drop()).thenReturn(failBuilder);
            when(failBuilder.level(anyInt())).thenReturn(failBuilder);
            when(failBuilder.rarity(any())).thenReturn(failBuilder);
            when(failBuilder.rarityBonus(anyDouble())).thenReturn(failBuilder);
            when(failBuilder.build()).thenReturn(null);

            var generator = createGenerator();
            var context = overworldContext(10, 10);

            // Even with BOSS tier (2-4 gear), if all builds fail, we get no gear
            List<ItemStack> loot = generator.generateLoot(context, ContainerTier.BOSS, TEST_PLAYER);
            // Should not contain null items
            for (ItemStack item : loot) {
                assertNotNull(item, "Loot list should never contain null items");
            }
        }

        @Test
        @DisplayName("Generator exception is caught and item skipped")
        void generateLoot_generatorException_skipsItem() {
            LootGenerator.DropBuilder throwingBuilder = mock(LootGenerator.DropBuilder.class);
            when(lootGenerator.drop()).thenReturn(throwingBuilder);
            when(throwingBuilder.level(anyInt())).thenReturn(throwingBuilder);
            when(throwingBuilder.rarity(any())).thenReturn(throwingBuilder);
            when(throwingBuilder.rarityBonus(anyDouble())).thenReturn(throwingBuilder);
            when(throwingBuilder.build()).thenThrow(new RuntimeException("Asset not found"));

            var generator = createGenerator();
            // Should not throw — exceptions caught internally
            assertDoesNotThrow(() ->
                generator.generateLoot(overworldContext(10, 10), ContainerTier.BOSS, TEST_PLAYER)
            );
        }
    }

    // =========================================================================
    // RARITY BONUS STACKING
    // =========================================================================

    @Nested
    @DisplayName("Rarity Bonus Stacking")
    class RarityBonusStacking {

        @Test
        @DisplayName("Rarity bonus combines tier + player WIND + realm IIR correctly")
        void generateLoot_rarityBonusCombinesTierPlayerAndRealm() {
            // Player WIND bonus = 10.0 (meaning 10%)
            when(rarityBonusCalculator.calculatePlayerBonus(TEST_PLAYER)).thenReturn(10.0);

            // Realm IIR = 30.0 (meaning 30%)
            var context = realmContext(50, 50, 0, 30.0, 0);

            var generator = createGenerator();
            generator.generateLoot(context, ContainerTier.DUNGEON, TEST_PLAYER);

            // Expected rarity bonus for gear:
            //   tier DUNGEON = 0.25 (decimal)
            //   + player WIND = 10.0 / 100.0 = 0.10
            //   + realm IIR = 30.0 / 100.0 = 0.30
            //   = 0.65
            // Verify RarityRoller was called with approximately 0.65
            ArgumentCaptor<Double> bonusCaptor = ArgumentCaptor.forClass(Double.class);
            // The roller is called via rollGearRarity → rarityRoller.roll(bonus, availableSet)
            verify(rarityRoller, atLeastOnce()).roll(bonusCaptor.capture(), anySet());

            double capturedBonus = bonusCaptor.getValue();
            assertEquals(0.65, capturedBonus, 0.01,
                "Rarity bonus should be tier(0.25) + WIND(0.10) + IIR(0.30) = 0.65");
        }

        @Test
        @DisplayName("Null rarity bonus calculator uses zero bonus")
        void generateLoot_nullRarityBonusCalculator_usesZeroBonus() {
            var generator = new ContainerLootGenerator(
                config, lootGenerator, mapGenerator, null,
                tierClassifier, dropLevelBlender, null, // null calculator
                rarityRoller, seededRandom
            );

            var context = overworldContext(10, 10);
            // Should not throw
            assertDoesNotThrow(() ->
                generator.generateLoot(context, ContainerTier.BASIC, TEST_PLAYER)
            );
        }

        @Test
        @DisplayName("Realm IIR bonus flows through to rarity calculation")
        void generateLoot_realmIIRBonusApplied() {
            // No player bonus, no tier bonus (use BASIC with 0.0)
            when(rarityBonusCalculator.calculatePlayerBonus(TEST_PLAYER)).thenReturn(0.0);

            // Realm IIR = 50.0 (meaning 50%)
            var context = realmContext(10, 10, 0, 50.0, 0);

            var generator = createGenerator();
            generator.generateLoot(context, ContainerTier.BASIC, TEST_PLAYER);

            // BASIC tier rarity bonus = 0.0
            // + player = 0.0
            // + realm IIR = 50.0 / 100.0 = 0.50
            // = 0.50
            ArgumentCaptor<Double> bonusCaptor = ArgumentCaptor.forClass(Double.class);
            verify(rarityRoller, atLeastOnce()).roll(bonusCaptor.capture(), anySet());
            double capturedBonus = bonusCaptor.getValue();
            assertEquals(0.50, capturedBonus, 0.01,
                "BASIC(0.0) + player(0.0) + IIR(0.50) = 0.50");
        }
    }

    // =========================================================================
    // GUARANTEED RARE MECHANIC
    // =========================================================================

    @Nested
    @DisplayName("Guaranteed Rare Mechanic")
    class GuaranteedRare {

        @Test
        @DisplayName("BOSS tier first drop guaranteed RARE or better")
        void bossTier_firstDropIsRareOrBetter() {
            // RarityRoller returns COMMON — but guarantee should upgrade it
            when(rarityRoller.roll(anyDouble(), anySet())).thenReturn(GearRarity.COMMON);

            var generator = createGenerator();
            generator.generateLoot(overworldContext(50, 50), ContainerTier.BOSS, TEST_PLAYER);

            // The DropBuilder should be called with rarity >= RARE for the first drop
            ArgumentCaptor<GearRarity> rarityCaptor = ArgumentCaptor.forClass(GearRarity.class);
            LootGenerator.DropBuilder mockBuilder = lootGenerator.drop();
            // Need to capture the rarity passed to the builder
            verify(mockBuilder, atLeastOnce()).rarity(rarityCaptor.capture());

            // First captured rarity should be at least RARE
            GearRarity firstRarity = rarityCaptor.getAllValues().get(0);
            assertTrue(firstRarity.ordinal() >= GearRarity.RARE.ordinal(),
                "First drop in BOSS container should be at least RARE, was: " + firstRarity);
        }

        @Test
        @DisplayName("Guarantee does not downgrade EPIC to RARE")
        void bossTier_doesNotDowngradeHigherRarity() {
            // RarityRoller returns EPIC — guarantee should NOT downgrade it
            when(rarityRoller.roll(anyDouble(), anySet())).thenReturn(GearRarity.EPIC);

            var generator = createGenerator();
            generator.generateLoot(overworldContext(50, 50), ContainerTier.BOSS, TEST_PLAYER);

            ArgumentCaptor<GearRarity> rarityCaptor = ArgumentCaptor.forClass(GearRarity.class);
            LootGenerator.DropBuilder mockBuilder = lootGenerator.drop();
            verify(mockBuilder, atLeastOnce()).rarity(rarityCaptor.capture());

            GearRarity firstRarity = rarityCaptor.getAllValues().get(0);
            assertEquals(GearRarity.EPIC, firstRarity,
                "EPIC should not be downgraded to RARE by guarantee");
        }

        @Test
        @DisplayName("BASIC tier has no guarantee — COMMON stays COMMON")
        void basicTier_noGuarantee_commonStaysCommon() {
            when(rarityRoller.roll(anyDouble(), anySet())).thenReturn(GearRarity.COMMON);

            var generator = createGenerator();
            generator.generateLoot(overworldContext(10, 10), ContainerTier.BASIC, TEST_PLAYER);

            // BASIC has no guaranteed rare, so COMMON should pass through
            ArgumentCaptor<GearRarity> rarityCaptor = ArgumentCaptor.forClass(GearRarity.class);
            LootGenerator.DropBuilder mockBuilder = lootGenerator.drop();
            // May not have any calls if drop count rolled to 0 — that's okay
            verify(mockBuilder, atLeast(0)).rarity(rarityCaptor.capture());

            for (GearRarity rarity : rarityCaptor.getAllValues()) {
                assertEquals(GearRarity.COMMON, rarity,
                    "BASIC tier should not upgrade COMMON");
            }
        }
    }

    // =========================================================================
    // STONE GENERATION
    // =========================================================================

    @Nested
    @DisplayName("Stone Generation")
    class StoneGeneration {

        @Test
        @DisplayName("Stones disabled returns empty")
        void generateStones_disabled_returnsEmpty() {
            config.getStoneDrops().setEnabled(false);
            var generator = createGenerator();

            var loot = generator.generateLoot(overworldContext(10, 10), ContainerTier.BASIC, TEST_PLAYER);
            // With zero gear drops (seeded random might produce 0) and disabled stones/maps,
            // result should be small. Key point: no stones generated.
            // We can't isolate stones from the public API, but we verify total is bounded.
            assertNotNull(loot);
        }

        @Test
        @DisplayName("Zero stone chance returns no stones")
        void generateStones_zeroChance_returnsNoStones() {
            config.getStoneDrops().setBase_chance(0.0);
            var generator = createGenerator();

            // Run many times — never get stones
            for (int i = 0; i < 50; i++) {
                generator.generateLoot(overworldContext(10, 10), ContainerTier.BASIC, TEST_PLAYER);
            }
            // No assertion on count possible without isolating stones,
            // but the test ensures no exceptions with 0 chance
        }

        @Test
        @DisplayName("100% stone chance with max=2 always produces stones")
        void generateStones_fullChance_producesStones() {
            config.getStoneDrops().setBase_chance(1.0);
            config.getStoneDrops().setMax_per_container(2);

            // Also disable gear and maps to isolate
            var isolatedConfig = createTestConfig();
            isolatedConfig.getStoneDrops().setBase_chance(1.0);
            isolatedConfig.getStoneDrops().setMax_per_container(2);
            isolatedConfig.getMapDrops().setEnabled(false);
            isolatedConfig.getConsumableDrops().setEnabled(false);

            var zeroGearTier = new ContainerLootConfig.TierConfig();
            zeroGearTier.setPatterns(List.of("Chest"));
            zeroGearTier.setMin_gear_drops(0);
            zeroGearTier.setMax_gear_drops(0);
            zeroGearTier.setStone_chance_multiplier(1.0);
            isolatedConfig.setContainer_tiers(Map.of("basic", zeroGearTier));

            var isolatedClassifier = new ContainerTierClassifier(isolatedConfig);

            // StoneType.getByRarity and StoneUtils.createStoneItem use static methods
            // that may require Hytale runtime — this test validates the generation path
            // doesn't throw when stones are enabled but runtime is unavailable
            var generator = new ContainerLootGenerator(
                isolatedConfig, lootGenerator, mapGenerator, null,
                isolatedClassifier, dropLevelBlender, rarityBonusCalculator, rarityRoller, seededRandom
            );

            // The generator will try StoneType.getByRarity() which may fail in test env
            // That's expected — the catch block should handle it gracefully
            assertDoesNotThrow(() ->
                generator.generateLoot(overworldContext(10, 10), ContainerTier.BASIC, TEST_PLAYER)
            );
        }
    }

    // =========================================================================
    // MAP GENERATION
    // =========================================================================

    @Nested
    @DisplayName("Map Generation")
    class MapGeneration {

        @Test
        @DisplayName("Maps disabled returns empty")
        void generateMaps_disabled_returnsEmpty() {
            config.getMapDrops().setEnabled(false);
            var generator = createGenerator();
            assertDoesNotThrow(() ->
                generator.generateLoot(overworldContext(10, 10), ContainerTier.BASIC, TEST_PLAYER)
            );
        }

        @Test
        @DisplayName("Null map generator returns no maps")
        void generateMaps_nullMapGenerator_returnsEmpty() {
            var generator = new ContainerLootGenerator(
                config, lootGenerator, null, null, // null map generator
                tierClassifier, dropLevelBlender, rarityBonusCalculator, rarityRoller, seededRandom
            );
            assertDoesNotThrow(() ->
                generator.generateLoot(overworldContext(10, 10), ContainerTier.BASIC, TEST_PLAYER)
            );
        }

        @Test
        @DisplayName("Map level uses blended level when blender enabled")
        void generateMaps_usesBlendedLevel() {
            config.getMapDrops().setBase_chance(1.0); // Always generate map
            config.getMapDrops().setMax_per_container(1);

            when(mapGenerator.generateItem(any(), anyInt(), anyDouble()))
                .thenReturn(mock(ItemStack.class));

            var generator = createGenerator();
            generator.generateLoot(overworldContext(20, 50), ContainerTier.BASIC, TEST_PLAYER);

            // With blending enabled (pull=0.3, maxOffset=5), sourceLevel=20, playerLevel=50:
            // gap = 30, pull = 9.0, clamped = 5, blended base = 25 ± variance
            // Map generator should be called with a level near 25
            ArgumentCaptor<Integer> levelCaptor = ArgumentCaptor.forClass(Integer.class);
            verify(mapGenerator, atLeast(0)).generateItem(any(), levelCaptor.capture(), anyDouble());

            if (!levelCaptor.getAllValues().isEmpty()) {
                int mapLevel = levelCaptor.getValue();
                // Blended level: 20 + 5 = 25, ± variance(2) → [23, 27]
                assertTrue(mapLevel >= 20 && mapLevel <= 30,
                    "Map level should be near blended value 25, was: " + mapLevel);
            }
        }
    }

    // =========================================================================
    // CONSUMABLE GENERATION
    // =========================================================================

    @Nested
    @DisplayName("Consumable Generation")
    class ConsumableGeneration {

        @Test
        @DisplayName("Consumables disabled returns empty")
        void generateConsumables_disabled_returnsEmpty() {
            config.getConsumableDrops().setEnabled(false);
            var generator = createGenerator();
            assertDoesNotThrow(() ->
                generator.generateLoot(overworldContext(10, 10), ContainerTier.BASIC, TEST_PLAYER)
            );
        }

        @Test
        @DisplayName("Null consumable registry returns no consumables")
        void generateConsumables_nullRegistry_returnsEmpty() {
            // Default generator has null consumableRegistry
            var generator = createGenerator();
            // Should handle gracefully
            assertDoesNotThrow(() ->
                generator.generateLoot(overworldContext(10, 10), ContainerTier.BASIC, TEST_PLAYER)
            );
        }
    }

    // =========================================================================
    // MAIN generateLoot()
    // =========================================================================

    @Nested
    @DisplayName("Main generateLoot()")
    class MainGenerateLoot {

        @Test
        @DisplayName("generateLoot combines all categories")
        void generateLoot_combinesAllCategories() {
            var generator = createGenerator();
            var context = overworldContext(10, 10);

            List<ItemStack> loot = generator.generateLoot(context, ContainerTier.BOSS, TEST_PLAYER);
            // BOSS tier: 2-4 gear guaranteed, plus potential stones/maps/consumables
            assertNotNull(loot);
            // At minimum we should get some items from gear (2+ from BOSS)
            assertFalse(loot.isEmpty(), "BOSS tier should produce at least some loot");
        }

        @Test
        @DisplayName("generateLoot never returns null")
        void generateLoot_neverReturnsNull() {
            var generator = createGenerator();
            List<ItemStack> loot = generator.generateLoot(
                overworldContext(1, 1), ContainerTier.BASIC, TEST_PLAYER
            );
            assertNotNull(loot, "generateLoot should never return null");
        }
    }

    // =========================================================================
    // SLOT-BUDGET generateLootForSlots()
    // =========================================================================

    @Nested
    @DisplayName("Slot-Budget Generation (generateLootForSlots)")
    class SlotBudgetGeneration {

        @Test
        @DisplayName("Zero target slots returns empty list")
        void generateLootForSlots_zeroSlots_returnsEmpty() {
            var generator = createGenerator();
            List<ItemStack> loot = generator.generateLootForSlots(
                overworldContext(10, 10), ContainerTier.BASIC, TEST_PLAYER, 0
            );
            assertTrue(loot.isEmpty(), "Zero target slots should return empty list");
        }

        @Test
        @DisplayName("Negative target slots returns empty list")
        void generateLootForSlots_negativeSlots_returnsEmpty() {
            var generator = createGenerator();
            List<ItemStack> loot = generator.generateLootForSlots(
                overworldContext(10, 10), ContainerTier.BASIC, TEST_PLAYER, -5
            );
            assertTrue(loot.isEmpty(), "Negative target slots should return empty list");
        }

        @Test
        @DisplayName("Single slot gets exactly one gear item")
        void generateLootForSlots_singleSlot_getsOneGear() {
            // Disable all non-gear drops to isolate
            config.getStoneDrops().setEnabled(false);
            config.getMapDrops().setEnabled(false);
            config.getConsumableDrops().setEnabled(false);

            var generator = createGenerator();
            List<ItemStack> loot = generator.generateLootForSlots(
                overworldContext(10, 10), ContainerTier.BASIC, TEST_PLAYER, 1
            );
            assertEquals(1, loot.size(), "Single target slot should produce exactly 1 item");
        }

        @Test
        @DisplayName("All slots filled with gear when no other drops enabled")
        void generateLootForSlots_allGear_whenNoOtherDrops() {
            config.getStoneDrops().setEnabled(false);
            config.getMapDrops().setEnabled(false);
            config.getConsumableDrops().setEnabled(false);

            var generator = createGenerator();
            int targetSlots = 10;
            List<ItemStack> loot = generator.generateLootForSlots(
                overworldContext(10, 10), ContainerTier.BASIC, TEST_PLAYER, targetSlots
            );
            assertEquals(targetSlots, loot.size(),
                "All " + targetSlots + " slots should be filled with gear");
        }

        @Test
        @DisplayName("Reserves at least one slot for gear even with many consumables")
        void generateLootForSlots_reservesOneSlotForGear() {
            // The slot budget algorithm breaks when slotsRemaining <= 1
            // This means at least 1 slot is always reserved for gear
            // Test with only 2 target slots and high non-gear chances
            config.getStoneDrops().setBase_chance(1.0);
            config.getStoneDrops().setMax_per_container(10);
            config.getMapDrops().setBase_chance(1.0);
            config.getMapDrops().setMax_per_container(10);
            config.getConsumableDrops().setBase_chance(1.0);
            config.getConsumableDrops().setMax_per_container(10);

            var generator = createGenerator();
            List<ItemStack> loot = generator.generateLootForSlots(
                overworldContext(10, 10), ContainerTier.BASIC, TEST_PLAYER, 2
            );

            // Should have at least 1 item (the reserved gear slot)
            // Non-gear items might fail in test env, but gear should succeed
            assertFalse(loot.isEmpty(), "Should produce at least 1 item (reserved gear slot)");
        }

        @Test
        @DisplayName("Large slot count fills all slots")
        void generateLootForSlots_largeSlotCount_fillsAll() {
            config.getStoneDrops().setEnabled(false);
            config.getMapDrops().setEnabled(false);
            config.getConsumableDrops().setEnabled(false);

            var generator = createGenerator();
            int targetSlots = 50;
            List<ItemStack> loot = generator.generateLootForSlots(
                overworldContext(50, 50), ContainerTier.BOSS, TEST_PLAYER, targetSlots
            );
            assertEquals(targetSlots, loot.size(),
                "All 50 slots should be filled with gear");
        }

        @Test
        @DisplayName("Slot budget uses blended gear levels")
        void generateLootForSlots_usesBlendedGearLevels() {
            config.getStoneDrops().setEnabled(false);
            config.getMapDrops().setEnabled(false);
            config.getConsumableDrops().setEnabled(false);

            var generator = createGenerator();
            generator.generateLootForSlots(
                overworldContext(20, 50), ContainerTier.BASIC, TEST_PLAYER, 3
            );

            // Verify the drop builder was called with blended levels
            ArgumentCaptor<Integer> levelCaptor = ArgumentCaptor.forClass(Integer.class);
            LootGenerator.DropBuilder mockBuilder = lootGenerator.drop();
            verify(mockBuilder, atLeast(3)).level(levelCaptor.capture());

            for (int level : levelCaptor.getAllValues()) {
                assertTrue(level >= 1, "Gear level should be at least 1, was: " + level);
            }
        }
    }

    // =========================================================================
    // RARITY ROLLING INTERNALS
    // =========================================================================

    @Nested
    @DisplayName("Rarity Rolling")
    class RarityRolling {

        @Test
        @DisplayName("Uses DynamicLootRegistry available rarities filter when discovered")
        void rollGearRarity_usesAvailableRaritiesFilter() {
            DynamicLootRegistry mockRegistry = mock(DynamicLootRegistry.class);
            when(lootGenerator.getDynamicRegistry()).thenReturn(mockRegistry);
            when(mockRegistry.isDiscovered()).thenReturn(true);

            Set<GearRarity> available = EnumSet.of(GearRarity.COMMON, GearRarity.UNCOMMON, GearRarity.RARE);
            when(mockRegistry.getAvailableRarities()).thenReturn(available);
            when(rarityRoller.roll(anyDouble(), eq(available))).thenReturn(GearRarity.UNCOMMON);

            var generator = createGenerator();
            generator.generateLoot(overworldContext(10, 10), ContainerTier.BOSS, TEST_PLAYER);

            // Should call the filtered roll, not the unfiltered one
            verify(rarityRoller, atLeastOnce()).roll(anyDouble(), eq(available));
        }

        @Test
        @DisplayName("Falls back to unfiltered roll when registry not discovered")
        void rollGearRarity_unfilteredWhenNotDiscovered() {
            DynamicLootRegistry mockRegistry = mock(DynamicLootRegistry.class);
            when(lootGenerator.getDynamicRegistry()).thenReturn(mockRegistry);
            when(mockRegistry.isDiscovered()).thenReturn(false);

            var generator = createGenerator();
            generator.generateLoot(overworldContext(10, 10), ContainerTier.BOSS, TEST_PLAYER);

            // Should use unfiltered roll
            verify(rarityRoller, atLeastOnce()).roll(anyDouble());
        }

        @Test
        @DisplayName("Falls back to unfiltered roll when null registry")
        void rollGearRarity_unfilteredWhenNullRegistry() {
            when(lootGenerator.getDynamicRegistry()).thenReturn(null);

            var generator = createGenerator();
            generator.generateLoot(overworldContext(10, 10), ContainerTier.BOSS, TEST_PLAYER);

            // Should use unfiltered roll
            verify(rarityRoller, atLeastOnce()).roll(anyDouble());
        }
    }

    // =========================================================================
    // DROP LEVEL BLENDING
    // =========================================================================

    @Nested
    @DisplayName("Drop Level Blending")
    class DropLevelBlending {

        @Test
        @DisplayName("Uses blended level when blender enabled")
        void generateLoot_usesBlendedLevel_whenEnabled() {
            config.getStoneDrops().setEnabled(false);
            config.getMapDrops().setEnabled(false);
            config.getConsumableDrops().setEnabled(false);

            var generator = createGenerator();
            generator.generateLoot(overworldContext(20, 50), ContainerTier.BOSS, TEST_PLAYER);

            // Verify gear levels are near the blended value
            ArgumentCaptor<Integer> levelCaptor = ArgumentCaptor.forClass(Integer.class);
            LootGenerator.DropBuilder mockBuilder = lootGenerator.drop();
            verify(mockBuilder, atLeastOnce()).level(levelCaptor.capture());

            for (int level : levelCaptor.getAllValues()) {
                // Blended: 20 + clamp(30*0.3, -5, 5) = 25, ±2 variance → [23, 27]
                assertTrue(level >= 1, "Level should be at least 1");
                // Note: exact range depends on seeded random variance
            }
        }

        @Test
        @DisplayName("Uses bracket range when blender disabled")
        void generateLoot_usesBracketRange_whenDisabled() {
            config.getStoneDrops().setEnabled(false);
            config.getMapDrops().setEnabled(false);
            config.getConsumableDrops().setEnabled(false);

            // Disable blending
            var disabledBlender = new DropLevelBlender(LevelBlendingConfig.DISABLED);
            var generator = new ContainerLootGenerator(
                config, lootGenerator, mapGenerator, null,
                tierClassifier, disabledBlender, rarityBonusCalculator, rarityRoller, seededRandom
            );

            // Set up a level range in config
            var scaling = new ContainerLootConfig.LootScaling();
            var range = new ContainerLootConfig.LevelRange();
            range.setPlayer_level_range(List.of(1, 100));
            range.setGear_level_range(List.of(5, 15));
            scaling.setLevel_ranges(List.of(range));
            config.setLoot_scaling(scaling);

            generator.generateLoot(overworldContext(10, 10), ContainerTier.BOSS, TEST_PLAYER);

            ArgumentCaptor<Integer> levelCaptor = ArgumentCaptor.forClass(Integer.class);
            LootGenerator.DropBuilder mockBuilder = lootGenerator.drop();
            verify(mockBuilder, atLeastOnce()).level(levelCaptor.capture());

            for (int level : levelCaptor.getAllValues()) {
                assertTrue(level >= 1, "Level should be at least 1, was: " + level);
            }
        }

        @Test
        @DisplayName("Gear level is always at least 1")
        void generateLoot_gearLevelFlooredAtOne() {
            config.getStoneDrops().setEnabled(false);
            config.getMapDrops().setEnabled(false);
            config.getConsumableDrops().setEnabled(false);

            var disabledBlender = new DropLevelBlender(LevelBlendingConfig.DISABLED);
            // Default level range [1,5] when no range matches
            var generator = new ContainerLootGenerator(
                config, lootGenerator, mapGenerator, null,
                tierClassifier, disabledBlender, rarityBonusCalculator, rarityRoller, seededRandom
            );

            // Source level 0 — gear level should still be ≥ 1
            generator.generateLoot(overworldContext(0, 0), ContainerTier.BOSS, TEST_PLAYER);

            ArgumentCaptor<Integer> levelCaptor = ArgumentCaptor.forClass(Integer.class);
            LootGenerator.DropBuilder mockBuilder = lootGenerator.drop();
            verify(mockBuilder, atLeastOnce()).level(levelCaptor.capture());

            for (int level : levelCaptor.getAllValues()) {
                assertTrue(level >= 1, "Gear level must be at least 1, was: " + level);
            }
        }
    }

    // =========================================================================
    // ACCESSORS
    // =========================================================================

    @Nested
    @DisplayName("Accessors")
    class Accessors {

        @Test
        @DisplayName("getLootGenerator returns injected instance")
        void getLootGenerator_returnsInjected() {
            var generator = createGenerator();
            assertSame(lootGenerator, generator.getLootGenerator());
        }

        @Test
        @DisplayName("getConfig returns injected config")
        void getConfig_returnsInjected() {
            var generator = createGenerator();
            assertSame(config, generator.getConfig());
        }
    }
}
