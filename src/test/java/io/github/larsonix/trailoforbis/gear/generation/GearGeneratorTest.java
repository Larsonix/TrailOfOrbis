package io.github.larsonix.trailoforbis.gear.generation;

import io.github.larsonix.trailoforbis.gear.config.EquipmentStatConfig;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig.*;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig.ModifierDefinition;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig.StatType;
import io.github.larsonix.trailoforbis.gear.config.TestConfigFactory;
import io.github.larsonix.trailoforbis.gear.item.ItemRegistryService;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.GearModifier;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.gear.model.ModifierType;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for GearGenerator - 35 test cases.
 */
@ExtendWith(MockitoExtension.class)
class GearGeneratorTest {

    @Mock
    private ItemStack baseItem;

    @Mock
    private ItemRegistryService itemRegistry;

    private GearBalanceConfig balanceConfig;
    private ModifierConfig modConfig;

    @BeforeEach
    void setUp() {
        balanceConfig = createTestBalanceConfig();
        modConfig = createTestModifierConfig();

        // Mock ItemRegistryService behavior
        lenient().when(itemRegistry.isInitialized()).thenReturn(true);
        lenient().when(itemRegistry.isRegistered(anyString())).thenReturn(false);
    }

    private void setupMockItem(ItemStack itemStack, double maxDurability) {
        Item item = mock(Item.class);
        lenient().when(item.getMaxDurability()).thenReturn(maxDurability);
        lenient().when(itemStack.getItem()).thenReturn(item);
        lenient().when(itemStack.withRestoredDurability(anyDouble())).thenReturn(itemStack);
        lenient().when(itemStack.withMetadata(anyString(), any(Codec.class), any())).thenReturn(itemStack);
    }

    // =========================================================================
    // BASIC GENERATION TESTS (10 cases)
    // =========================================================================

    @Nested
    @DisplayName("Basic Generation")
    class BasicGenerationTests {

        @Test
        @Disabled("Requires Hytale runtime environment - Item.getAssetStore()")
        @DisplayName("generate returns non-null item")
        void generate_ReturnsNonNullItem() {
            setupMockItem(baseItem, 100);
            GearGenerator gen = new GearGenerator(balanceConfig, modConfig, EquipmentStatConfig.unrestricted(), itemRegistry);
            ItemStack result = gen.generate(baseItem, 50, "weapon");
            assertNotNull(result);
        }

        @Test
        @DisplayName("generateData returns GearData with correct level")
        void generate_CorrectLevel() {
            GearGenerator gen = new GearGenerator(balanceConfig, modConfig, EquipmentStatConfig.unrestricted(), itemRegistry);
            GearData data = gen.generateData(75, "weapon", GearRarity.EPIC);
            assertEquals(75, data.level());
        }

        @Test
        @DisplayName("generateData returns valid rarity")
        void generate_ValidRarity() {
            GearGenerator gen = new GearGenerator(balanceConfig, modConfig, EquipmentStatConfig.unrestricted(), itemRegistry);
            GearData data = gen.generateData(50, "weapon", GearRarity.EPIC);
            assertNotNull(data.rarity());
        }

        @Test
        @DisplayName("generateData returns valid quality")
        void generate_ValidQuality() {
            GearGenerator gen = new GearGenerator(balanceConfig, modConfig, EquipmentStatConfig.unrestricted(), itemRegistry);
            GearData data = gen.generateData(50, "weapon", GearRarity.EPIC);
            assertTrue(data.quality() >= 1 && data.quality() <= 101);
        }

        @Test
        @DisplayName("generateData can have modifiers")
        void generate_HasModifiers() {
            GearGenerator gen = new GearGenerator(balanceConfig, modConfig, EquipmentStatConfig.unrestricted(), itemRegistry);

            boolean hasModifiers = false;
            for (int i = 0; i < 100; i++) {
                GearData data = gen.generateData(50, "weapon", GearRarity.LEGENDARY);
                if (!data.prefixes().isEmpty() || !data.suffixes().isEmpty()) {
                    hasModifiers = true;
                    break;
                }
            }

            assertTrue(hasModifiers, "Should generate items with modifiers");
        }

        @Test
        @DisplayName("generate with forced rarity uses specified rarity")
        void generate_ForcedRarity_UsesSpecifiedRarity() {
            GearGenerator gen = new GearGenerator(balanceConfig, modConfig, EquipmentStatConfig.unrestricted(), itemRegistry);
            GearData data = gen.generateData(50, "weapon", GearRarity.LEGENDARY);
            assertEquals(GearRarity.LEGENDARY, data.rarity());
        }

        @Test
        @DisplayName("generate with rarity bonus shifts distribution")
        void generate_WithRarityBonus_ShiftsDistribution() {
            // Use a single shared Random to avoid Java LCG correlation with sequential seeds.
            // Sequential seeds produce clustered first-nextDouble() values that don't reach
            // the high cumulative thresholds needed for rare+ with geometric weight gaps.
            GearGenerator gen = new GearGenerator(balanceConfig, modConfig, EquipmentStatConfig.unrestricted(), itemRegistry, new Random(42));
            RarityRoller roller = gen.getRarityRoller();

            int rareNoBonus = 0, rareWithBonus = 0;
            for (int i = 0; i < 10000; i++) {
                if (roller.roll(0.0).ordinal() >= GearRarity.RARE.ordinal()) rareNoBonus++;
            }
            for (int i = 0; i < 10000; i++) {
                if (roller.roll(2.0).ordinal() >= GearRarity.RARE.ordinal()) rareWithBonus++;
            }

            assertTrue(rareWithBonus > rareNoBonus,
                String.format("With bonus should have more rare+: noBonus=%d, withBonus=%d", rareNoBonus, rareWithBonus));
        }

        @Test
        @DisplayName("generate is deterministic with seed")
        void generate_Deterministic_WithSeed() {
            GearGenerator gen1 = new GearGenerator(balanceConfig, modConfig, EquipmentStatConfig.unrestricted(), itemRegistry, new Random(99999));
            GearGenerator gen2 = new GearGenerator(balanceConfig, modConfig, EquipmentStatConfig.unrestricted(), itemRegistry, new Random(99999));

            for (int i = 0; i < 10; i++) {
                GearData data1 = gen1.generateData(50, "weapon", GearRarity.EPIC);
                GearData data2 = gen2.generateData(50, "weapon", GearRarity.EPIC);

                // Compare all fields except instanceId (which is timestamp-based, not Random-based)
                assertEquals(data1.level(), data2.level());
                assertEquals(data1.rarity(), data2.rarity());
                assertEquals(data1.quality(), data2.quality());
                assertEquals(data1.prefixes(), data2.prefixes());
                assertEquals(data1.suffixes(), data2.suffixes());
            }
        }

        @Test
        @DisplayName("different slots can have different modifiers")
        void generate_DifferentSlots_DifferentModifiers() {
            GearGenerator gen = new GearGenerator(balanceConfig, modConfig, EquipmentStatConfig.unrestricted(), itemRegistry);

            Set<String> weaponMods = new HashSet<>();
            Set<String> feetMods = new HashSet<>();

            for (int i = 0; i < 100; i++) {
                GearData weaponData = gen.generateData(50, "weapon", GearRarity.LEGENDARY);
                GearData feetData = gen.generateData(50, "feet", GearRarity.LEGENDARY);

                weaponData.suffixes().forEach(m -> weaponMods.add(m.id()));
                feetData.suffixes().forEach(m -> feetMods.add(m.id()));
            }

            // "of_speed" should be in feet but not weapon
            assertTrue(feetMods.contains("of_speed"), "Feet should have of_speed");
            assertFalse(weaponMods.contains("of_speed"), "Weapon should not have of_speed");
        }

        @Test
        @Disabled("Requires Hytale runtime environment - Item.getAssetStore()")
        @DisplayName("generate calls setGearData on ItemStack")
        void generate_HasGearData() {
            setupMockItem(baseItem, 100);
            GearGenerator gen = new GearGenerator(balanceConfig, modConfig, EquipmentStatConfig.unrestricted(), itemRegistry);

            gen.generate(baseItem, 50, "weapon");

            // Verify withMetadata was called (setGearData uses it)
            verify(baseItem, atLeastOnce()).withMetadata(anyString(), any(Codec.class), any());
        }
    }

    // =========================================================================
    // MODIFIER COUNT TESTS (8 cases)
    // =========================================================================

    @Nested
    @DisplayName("Modifier Counts")
    class ModifierCountTests {

        @Test
        @DisplayName("Common gear has max one modifier")
        void generate_Common_MaxOneModifier() {
            GearGenerator gen = new GearGenerator(balanceConfig, modConfig, EquipmentStatConfig.unrestricted(), itemRegistry);

            for (int i = 0; i < 100; i++) {
                GearData data = gen.generateData(50, "weapon", GearRarity.COMMON);

                int totalMods = data.prefixes().size() + data.suffixes().size();
                assertTrue(totalMods <= 1, "Common should have <= 1 modifier");
            }
        }

        @Test
        @DisplayName("Legendary gear respects modifier limits")
        void generate_Legendary_RespectsModifierLimits() {
            GearGenerator gen = new GearGenerator(balanceConfig, modConfig, EquipmentStatConfig.unrestricted(), itemRegistry);

            for (int i = 0; i < 100; i++) {
                GearData data = gen.generateData(50, "weapon", GearRarity.LEGENDARY);

                int totalMods = data.prefixes().size() + data.suffixes().size();
                assertTrue(totalMods <= 5, "Legendary should have <= 5 modifiers");
                assertTrue(totalMods >= 2, "Legendary should have >= 2 modifiers");
                assertTrue(data.prefixes().size() <= 2, "Legendary max prefixes is 2");
                assertTrue(data.suffixes().size() <= 3, "Legendary max suffixes is 3");
            }
        }

        @Test
        @DisplayName("Mythic gear respects modifier limits")
        void generate_Mythic_RespectsModifierLimits() {
            GearGenerator gen = new GearGenerator(balanceConfig, modConfig, EquipmentStatConfig.unrestricted(), itemRegistry);

            for (int i = 0; i < 100; i++) {
                GearData data = gen.generateData(50, "weapon", GearRarity.MYTHIC);

                int totalMods = data.prefixes().size() + data.suffixes().size();
                assertTrue(totalMods <= 6, "Mythic should have <= 6 modifiers");
                assertTrue(totalMods >= 2, "Mythic should have >= 2 modifiers");
                assertTrue(data.prefixes().size() <= 3, "Mythic max prefixes is 3");
                assertTrue(data.suffixes().size() <= 3, "Mythic max suffixes is 3");
            }
        }

        @Test
        @DisplayName("Epic gear has max four modifiers")
        void generate_Epic_MaxFourModifiers() {
            GearGenerator gen = new GearGenerator(balanceConfig, modConfig, EquipmentStatConfig.unrestricted(), itemRegistry);

            for (int i = 0; i < 100; i++) {
                GearData data = gen.generateData(50, "weapon", GearRarity.EPIC);

                int totalMods = data.prefixes().size() + data.suffixes().size();
                assertTrue(totalMods <= 4, "Epic should have <= 4 modifiers");
                assertTrue(totalMods >= 2, "Epic should have >= 2 modifiers");
            }
        }

        @Test
        @DisplayName("Modifier count enforces max limit")
        void generate_ModifierCountEnforcesMax() {
            GearBalanceConfig restrictedConfig = createConfigWithMaxModifiers(3);
            GearGenerator gen = new GearGenerator(restrictedConfig, modConfig, EquipmentStatConfig.unrestricted(), itemRegistry);

            for (int i = 0; i < 100; i++) {
                GearData data = gen.generateData(50, "weapon", GearRarity.LEGENDARY);
                int total = data.prefixes().size() + data.suffixes().size();
                assertTrue(total <= 3, "Should not exceed max modifiers");
            }
        }

        @Test
        @DisplayName("Config with no prefix range produces no prefixes")
        void generate_NoPrefixModifiers_OnlySuffixes() {
            GearBalanceConfig noPrefixConfig = createConfigWithPrefixRange(0, 0);
            GearGenerator gen = new GearGenerator(noPrefixConfig, modConfig, EquipmentStatConfig.unrestricted(), itemRegistry);

            for (int i = 0; i < 50; i++) {
                GearData data = gen.generateData(50, "weapon", GearRarity.EPIC);
                assertTrue(data.prefixes().isEmpty(), "Should have no prefixes");
            }
        }

        @Test
        @DisplayName("Config with no suffix range produces no suffixes")
        void generate_NoSuffixModifiers_OnlyPrefixes() {
            GearBalanceConfig noSuffixConfig = createConfigWithSuffixRange(0, 0);
            GearGenerator gen = new GearGenerator(noSuffixConfig, modConfig, EquipmentStatConfig.unrestricted(), itemRegistry);

            for (int i = 0; i < 50; i++) {
                GearData data = gen.generateData(50, "weapon", GearRarity.EPIC);
                assertTrue(data.suffixes().isEmpty(), "Should have no suffixes");
            }
        }

        @Test
        @DisplayName("Modifiers are unique")
        void generate_ModifiersAreUnique() {
            GearGenerator gen = new GearGenerator(balanceConfig, modConfig, EquipmentStatConfig.unrestricted(), itemRegistry);

            for (int i = 0; i < 100; i++) {
                GearData data = gen.generateData(50, "weapon", GearRarity.LEGENDARY);

                Set<String> prefixIds = data.prefixes().stream()
                    .map(GearModifier::id).collect(Collectors.toSet());
                Set<String> suffixIds = data.suffixes().stream()
                    .map(GearModifier::id).collect(Collectors.toSet());

                assertEquals(data.prefixes().size(), prefixIds.size());
                assertEquals(data.suffixes().size(), suffixIds.size());
            }
        }
    }

    // =========================================================================
    // MODIFIER DISTRIBUTION REGRESSION TESTS (5 cases)
    // =========================================================================

    /**
     * Regression tests for commit a3b4372.
     *
     * <p>The bug: Items generated with fewer modifiers than max_modifiers because
     * prefix and suffix counts were rolled independently. If prefix rolled 1 and
     * suffix rolled 2, total was 3 even when max_modifiers was 5.
     *
     * <p>The fix: The generator now distributes max_modifiers between prefixes and
     * suffixes, respecting min/max bounds, ensuring items hit their maximum.
     */
    @Nested
    @DisplayName("Modifier Distribution (Regression)")
    class ModifierDistributionRegressionTests {

        /**
         * Creates a config with production-like Legendary settings.
         * Legendary: max_modifiers=5, prefix_range=[2,2], suffix_range=[2,3]
         */
        private GearBalanceConfig createProductionLegendaryConfig() {
            Map<GearRarity, RarityConfig> rarityConfigs = new EnumMap<>(GearRarity.class);
            // Legendary: 2 prefixes (fixed) + 2-3 suffixes = 4-5 total, max=5
            rarityConfigs.put(GearRarity.LEGENDARY, new RarityConfig(1.7, 5, 2, 2, 2, 3, 0.9));
            // Fill other rarities with defaults
            rarityConfigs.put(GearRarity.COMMON, new RarityConfig(0.3, 1, 0, 1, 0, 1, 50.0));
            rarityConfigs.put(GearRarity.UNCOMMON, new RarityConfig(0.5, 2, 0, 1, 0, 2, 30.0));
            rarityConfigs.put(GearRarity.RARE, new RarityConfig(0.8, 3, 1, 2, 1, 2, 15.0));
            rarityConfigs.put(GearRarity.EPIC, new RarityConfig(1.2, 4, 1, 2, 1, 2, 4.0));
            rarityConfigs.put(GearRarity.MYTHIC, new RarityConfig(2.3, 6, 2, 3, 2, 3, 0.1, 0.75));
            rarityConfigs.put(GearRarity.UNIQUE, new RarityConfig(2.8, 6, 2, 3, 2, 3, 0.05, 0.80));
            return TestConfigFactory.createBalanceConfigWithRarities(rarityConfigs);
        }

        @Test
        @DisplayName("Legendary generates at least 4 modifiers (regression test)")
        void generate_Legendary_AtLeastFourModifiers() {
            // Use production-like config: prefix=[2,2], suffix=[2,3], max=5
            GearBalanceConfig prodConfig = createProductionLegendaryConfig();
            GearGenerator gen = new GearGenerator(prodConfig, modConfig, EquipmentStatConfig.unrestricted(), itemRegistry);

            for (int i = 0; i < 100; i++) {
                GearData data = gen.generateData(50, "weapon", GearRarity.LEGENDARY);
                int total = data.prefixes().size() + data.suffixes().size();

                // Minimum should be prefix_min + suffix_min = 2 + 2 = 4
                assertTrue(total >= 4,
                    "Legendary should have at least 4 modifiers, got " + total);

                // Maximum should be capped at max_modifiers=5
                assertTrue(total <= 5,
                    "Legendary should have at most 5 modifiers, got " + total);
            }
        }

        @Test
        @DisplayName("Epic generates at least 2 modifiers")
        void generate_Epic_AtLeastTwoModifiers() {
            GearBalanceConfig prodConfig = createProductionLegendaryConfig();
            GearGenerator gen = new GearGenerator(prodConfig, modConfig, EquipmentStatConfig.unrestricted(), itemRegistry);

            for (int i = 0; i < 100; i++) {
                GearData data = gen.generateData(50, "weapon", GearRarity.EPIC);
                int total = data.prefixes().size() + data.suffixes().size();

                // Epic: prefix_min=1, suffix_min=1 → at least 2
                assertTrue(total >= 2,
                    "Epic should have at least 2 modifiers, got " + total);

                // Epic: max_modifiers=4
                assertTrue(total <= 4,
                    "Epic should have at most 4 modifiers, got " + total);
            }
        }

        @Test
        @DisplayName("Rare generates at least 2 modifiers")
        void generate_Rare_AtLeastTwoModifiers() {
            GearBalanceConfig prodConfig = createProductionLegendaryConfig();
            GearGenerator gen = new GearGenerator(prodConfig, modConfig, EquipmentStatConfig.unrestricted(), itemRegistry);

            for (int i = 0; i < 100; i++) {
                GearData data = gen.generateData(50, "weapon", GearRarity.RARE);
                int total = data.prefixes().size() + data.suffixes().size();

                // Rare: prefix_min=1, suffix_min=1 → at least 2
                assertTrue(total >= 2,
                    "Rare should have at least 2 modifiers, got " + total);

                // Rare: max_modifiers=3
                assertTrue(total <= 3,
                    "Rare should have at most 3 modifiers, got " + total);
            }
        }

        @Test
        @DisplayName("Distribution respects prefix/suffix min/max bounds")
        void generate_Distribution_RespectsBounds() {
            GearBalanceConfig prodConfig = createProductionLegendaryConfig();
            GearGenerator gen = new GearGenerator(prodConfig, modConfig, EquipmentStatConfig.unrestricted(), itemRegistry);

            for (int i = 0; i < 100; i++) {
                GearData data = gen.generateData(50, "weapon", GearRarity.LEGENDARY);

                // Legendary config: prefix_range=[2,2] (exactly 2)
                assertEquals(2, data.prefixes().size(),
                    "Legendary should have exactly 2 prefixes");

                // Legendary config: suffix_range=[2,3]
                int suffixCount = data.suffixes().size();
                assertTrue(suffixCount >= 2 && suffixCount <= 3,
                    "Legendary should have 2-3 suffixes, got " + suffixCount);
            }
        }

        @Test
        @DisplayName("Mythic generates 4-6 modifiers")
        void generate_Mythic_FourToSixModifiers() {
            GearBalanceConfig prodConfig = createProductionLegendaryConfig();
            GearGenerator gen = new GearGenerator(prodConfig, modConfig, EquipmentStatConfig.unrestricted(), itemRegistry);

            for (int i = 0; i < 100; i++) {
                GearData data = gen.generateData(50, "weapon", GearRarity.MYTHIC);
                int total = data.prefixes().size() + data.suffixes().size();

                // Mythic: prefix=[2,3], suffix=[2,3], max=6 → 4-6 total
                assertTrue(total >= 4,
                    "Mythic should have at least 4 modifiers, got " + total);
                assertTrue(total <= 6,
                    "Mythic should have at most 6 modifiers, got " + total);
            }
        }
    }

    // =========================================================================
    // BUILDER TESTS (8 cases)
    // =========================================================================

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("Basic builder usage")
        void builder_BasicUsage() {
            GearGenerator gen = new GearGenerator(balanceConfig, modConfig, EquipmentStatConfig.unrestricted(), itemRegistry);

            GearData data = gen.builder()
                .level(50)
                .slot("weapon")
                .buildData();

            assertEquals(50, data.level());
            assertNotNull(data.rarity());
        }

        @Test
        @DisplayName("Builder with forced rarity")
        void builder_ForcedRarity() {
            GearGenerator gen = new GearGenerator(balanceConfig, modConfig, EquipmentStatConfig.unrestricted(), itemRegistry);

            GearData data = gen.builder()
                .level(50)
                .slot("weapon")
                .rarity(GearRarity.EPIC)
                .buildData();

            assertEquals(GearRarity.EPIC, data.rarity());
        }

        @Test
        @DisplayName("Builder with forced quality")
        void builder_ForcedQuality() {
            GearGenerator gen = new GearGenerator(balanceConfig, modConfig, EquipmentStatConfig.unrestricted(), itemRegistry);

            GearData data = gen.builder()
                .level(50)
                .slot("weapon")
                .quality(100)
                .buildData();

            assertEquals(100, data.quality());
        }

        @Test
        @DisplayName("Builder with forced modifiers")
        void builder_ForcedModifiers() {
            GearGenerator gen = new GearGenerator(balanceConfig, modConfig, EquipmentStatConfig.unrestricted(), itemRegistry);
            List<GearModifier> forcedPrefixes = List.of(
                GearModifier.of("sharp", "Sharp", ModifierType.PREFIX, "physical_damage", "flat", 50.0)
            );

            GearData data = gen.builder()
                .level(50)
                .slot("weapon")
                .rarity(GearRarity.EPIC)
                .forcePrefixes(forcedPrefixes)
                .buildData();

            assertEquals(forcedPrefixes, data.prefixes());
        }

        @Test
        @DisplayName("Builder missing level throws exception")
        void builder_MissingLevel_ThrowsException() {
            GearGenerator gen = new GearGenerator(balanceConfig, modConfig, EquipmentStatConfig.unrestricted(), itemRegistry);

            assertThrows(IllegalStateException.class, () ->
                gen.builder().slot("weapon").buildData());
        }

        @Test
        @DisplayName("Builder missing slot throws exception")
        void builder_MissingSlot_ThrowsException() {
            GearGenerator gen = new GearGenerator(balanceConfig, modConfig, EquipmentStatConfig.unrestricted(), itemRegistry);

            assertThrows(IllegalStateException.class, () ->
                gen.builder().level(50).buildData());
        }

        @Test
        @Disabled("Requires Hytale runtime environment - Item.getAssetStore()")
        @DisplayName("Builder build to ItemStack")
        void builder_BuildToItemStack() {
            setupMockItem(baseItem, 100);
            GearGenerator gen = new GearGenerator(balanceConfig, modConfig, EquipmentStatConfig.unrestricted(), itemRegistry);

            ItemStack result = gen.builder()
                .level(50)
                .slot("weapon")
                .rarity(GearRarity.LEGENDARY)
                .build(baseItem);

            assertNotNull(result);
        }

        @Test
        @DisplayName("Builder rarity bonus applies when no forced rarity")
        void builder_RarityBonus_AppliesWhenNoForcedRarity() {
            GearGenerator gen = new GearGenerator(balanceConfig, modConfig, EquipmentStatConfig.unrestricted(), itemRegistry);

            int rareCount = 0;
            for (int i = 0; i < 1000; i++) {
                GearData data = gen.builder()
                    .level(50)
                    .slot("weapon")
                    .rarityBonus(5.0)
                    .buildData();

                if (data.rarity().ordinal() >= GearRarity.RARE.ordinal()) {
                    rareCount++;
                }
            }

            assertTrue(rareCount > 80, "Should have many rare+ items with high bonus");
        }
    }

    // =========================================================================
    // EDGE CASES (5 cases)
    // =========================================================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Null base item throws exception")
        void generate_NullBaseItem_ThrowsException() {
            GearGenerator gen = new GearGenerator(balanceConfig, modConfig, EquipmentStatConfig.unrestricted(), itemRegistry);

            assertThrows(NullPointerException.class, () ->
                gen.generate(null, 50, "weapon"));
        }

        @Test
        @DisplayName("Null slot throws exception")
        void generate_NullSlot_ThrowsException() {
            setupMockItem(baseItem, 100);
            GearGenerator gen = new GearGenerator(balanceConfig, modConfig, EquipmentStatConfig.unrestricted(), itemRegistry);

            assertThrows(NullPointerException.class, () ->
                gen.generate(baseItem, 50, null));
        }

        @Test
        @DisplayName("Null rarity throws exception")
        void generate_NullRarity_ThrowsException() {
            setupMockItem(baseItem, 100);
            GearGenerator gen = new GearGenerator(balanceConfig, modConfig, EquipmentStatConfig.unrestricted(), itemRegistry);

            assertThrows(NullPointerException.class, () ->
                gen.generate(baseItem, 50, "weapon", (GearRarity) null));
        }

        @Test
        @DisplayName("Level below min throws exception")
        void generate_LevelBelowMin_ThrowsException() {
            setupMockItem(baseItem, 100);
            GearGenerator gen = new GearGenerator(balanceConfig, modConfig, EquipmentStatConfig.unrestricted(), itemRegistry);

            assertThrows(IllegalArgumentException.class, () ->
                gen.generate(baseItem, 0, "weapon"));
        }

        @Test
        @DisplayName("Level above max throws exception")
        void generate_LevelAboveMax_ThrowsException() {
            setupMockItem(baseItem, 100);
            GearGenerator gen = new GearGenerator(balanceConfig, modConfig, EquipmentStatConfig.unrestricted(), itemRegistry);

            assertThrows(IllegalArgumentException.class, () ->
                gen.generate(baseItem, 1_000_001, "weapon"));
        }
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private GearBalanceConfig createTestBalanceConfig() {
        return TestConfigFactory.createDefaultBalanceConfig();
    }

    private GearBalanceConfig createConfigWithMaxModifiers(int maxMods) {
        Map<GearRarity, RarityConfig> rarityConfigs = new EnumMap<>(GearRarity.class);
        for (GearRarity rarity : GearRarity.values()) {
            if (rarity == GearRarity.MYTHIC) {
                rarityConfigs.put(rarity, new RarityConfig(2.3, maxMods, 2, 2, 2, 2, 0.1, 0.75));
            } else {
                rarityConfigs.put(rarity, new RarityConfig(1.2, maxMods, 2, 2, 2, 2, 1.0));
            }
        }
        return TestConfigFactory.createBalanceConfigWithRarities(rarityConfigs);
    }

    private GearBalanceConfig createConfigWithPrefixRange(int min, int max) {
        Map<GearRarity, RarityConfig> rarityConfigs = new EnumMap<>(GearRarity.class);
        for (GearRarity rarity : GearRarity.values()) {
            if (rarity == GearRarity.MYTHIC) {
                rarityConfigs.put(rarity, new RarityConfig(2.3, 4, min, max, 2, 2, 0.1, 0.75));
            } else {
                rarityConfigs.put(rarity, new RarityConfig(1.2, 4, min, max, 2, 2, 1.0));
            }
        }
        return TestConfigFactory.createBalanceConfigWithRarities(rarityConfigs);
    }

    private GearBalanceConfig createConfigWithSuffixRange(int min, int max) {
        Map<GearRarity, RarityConfig> rarityConfigs = new EnumMap<>(GearRarity.class);
        for (GearRarity rarity : GearRarity.values()) {
            if (rarity == GearRarity.MYTHIC) {
                rarityConfigs.put(rarity, new RarityConfig(2.3, 4, 2, 2, min, max, 0.1, 0.75));
            } else {
                rarityConfigs.put(rarity, new RarityConfig(1.2, 4, 2, 2, min, max, 1.0));
            }
        }
        return TestConfigFactory.createBalanceConfigWithRarities(rarityConfigs);
    }

    private ModifierConfig createTestModifierConfig() {
        Map<String, ModifierDefinition> prefixes = new HashMap<>();
        prefixes.put("sharp", new ModifierDefinition(
            "sharp", "Sharp", "physical_damage", StatType.FLAT,
            5.0, 15.0, 0.5, 100, null, null
        ));
        prefixes.put("deadly", new ModifierDefinition(
            "deadly", "Deadly", "critical_damage", StatType.PERCENT,
            2.0, 8.0, 0.3, 10, null, null
        ));
        prefixes.put("swift", new ModifierDefinition(
            "swift", "Swift", "attack_speed", StatType.PERCENT,
            1.0, 5.0, 0.2, 50, null, null
        ));

        Map<String, ModifierDefinition> suffixes = new HashMap<>();
        suffixes.put("of_the_whale", new ModifierDefinition(
            "of_the_whale", "of the Whale", "max_health", StatType.FLAT,
            10.0, 30.0, 1.0, 50, null, null
        ));
        suffixes.put("of_protection", new ModifierDefinition(
            "of_protection", "of Protection", "armor", StatType.FLAT,
            5.0, 20.0, 0.5, 80, null, null
        ));
        suffixes.put("of_speed", new ModifierDefinition(
            "of_speed", "of Speed", "movement_speed", StatType.PERCENT,
            3.0, 10.0, 0.3, 30, null, Set.of("feet")
        ));

        return TestConfigFactory.createModifierConfig(prefixes, suffixes);
    }
}
