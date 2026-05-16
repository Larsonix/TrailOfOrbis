package io.github.larsonix.trailoforbis.loot.container;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ContainerLootConfig} — config defaults, level range lookup,
 * null safety of YAML deserialization setters, and ReplacementScope enum.
 */
class ContainerLootConfigTest {

    // =========================================================================
    // LEVEL RANGE LOOKUP
    // =========================================================================

    @Nested
    @DisplayName("Level Range Lookup")
    class LevelRangeLookup {

        private ContainerLootConfig.LootScaling scalingWithRanges() {
            var scaling = new ContainerLootConfig.LootScaling();
            var ranges = new ArrayList<ContainerLootConfig.LevelRange>();

            var low = new ContainerLootConfig.LevelRange();
            low.setPlayer_level_range(List.of(1, 20));
            low.setGear_level_range(List.of(1, 10));
            ranges.add(low);

            var mid = new ContainerLootConfig.LevelRange();
            mid.setPlayer_level_range(List.of(21, 50));
            mid.setGear_level_range(List.of(15, 35));
            ranges.add(mid);

            var high = new ContainerLootConfig.LevelRange();
            high.setPlayer_level_range(List.of(60, 100));
            high.setGear_level_range(List.of(50, 90));
            ranges.add(high);

            scaling.setLevel_ranges(ranges);
            return scaling;
        }

        @Test
        @DisplayName("findLevelRange returns match when player level is within range")
        void findLevelRange_withinRange_returnsMatch() {
            var scaling = scalingWithRanges();
            var range = scaling.findLevelRange(10);
            assertNotNull(range, "Level 10 should match the [1,20] range");
            assertEquals(1, range.getGearLevelMin());
            assertEquals(10, range.getGearLevelMax());
        }

        @Test
        @DisplayName("findLevelRange returns match at exact boundaries")
        void findLevelRange_atBoundaries_returnsMatch() {
            var scaling = scalingWithRanges();

            assertNotNull(scaling.findLevelRange(1), "Level 1 (min of first range) should match");
            assertNotNull(scaling.findLevelRange(20), "Level 20 (max of first range) should match");
            assertNotNull(scaling.findLevelRange(21), "Level 21 (min of second range) should match");
        }

        @Test
        @DisplayName("findLevelRange returns null for gap between ranges")
        void findLevelRange_betweenRanges_returnsNull() {
            var scaling = scalingWithRanges();
            // Gap: 51-59 (between [21,50] and [60,100])
            var range = scaling.findLevelRange(55);
            assertNull(range, "Level 55 falls in the gap between ranges");
        }

        @Test
        @DisplayName("findLevelRange returns null below all ranges")
        void findLevelRange_belowAllRanges_returnsNull() {
            var scaling = scalingWithRanges();
            var range = scaling.findLevelRange(0);
            assertNull(range, "Level 0 is below all ranges");
        }

        @Test
        @DisplayName("findLevelRange returns null above all ranges")
        void findLevelRange_aboveAllRanges_returnsNull() {
            var scaling = scalingWithRanges();
            var range = scaling.findLevelRange(200);
            assertNull(range, "Level 200 is above all ranges");
        }

        @Test
        @DisplayName("getGearLevelRange returns match range values")
        void getGearLevelRange_withMatch_returnsRangeValues() {
            var scaling = scalingWithRanges();
            int[] range = scaling.getGearLevelRange(30);
            assertArrayEquals(new int[]{15, 35}, range,
                "Level 30 should match [21,50] range → gear [15,35]");
        }

        @Test
        @DisplayName("getGearLevelRange returns default when no match")
        void getGearLevelRange_noMatch_returnsDefault() {
            var scaling = scalingWithRanges();
            int[] range = scaling.getGearLevelRange(55); // Gap
            assertArrayEquals(new int[]{1, 5}, range,
                "Gap level should return default gear range [1,5]");
        }

        @Test
        @DisplayName("getRarityWeights returns default when no match")
        void getRarityWeights_noMatch_returnsDefaultWeights() {
            var scaling = scalingWithRanges();
            Map<String, Integer> weights = scaling.getRarityWeights(55); // Gap
            assertNotNull(weights);
            assertTrue(weights.containsKey("common"), "Default weights should include 'common'");
            assertEquals(70, weights.get("common"), "Default common weight should be 70");
        }

        @Test
        @DisplayName("Empty ranges list falls back to defaults for all levels")
        void emptyRanges_alwaysReturnsDefault() {
            var scaling = new ContainerLootConfig.LootScaling();
            assertNull(scaling.findLevelRange(10));
            assertArrayEquals(new int[]{1, 5}, scaling.getGearLevelRange(10));
        }
    }

    // =========================================================================
    // CONFIG DEFAULTS
    // =========================================================================

    @Nested
    @DisplayName("Config Defaults")
    class ConfigDefaults {

        @Test
        @DisplayName("createDefaults returns config with all fields non-null")
        void createDefaults_allFieldsNonNull() {
            var config = ContainerLootConfig.createDefaults();
            assertNotNull(config.getSources());
            assertNotNull(config.getLootScaling());
            assertNotNull(config.getContainerTiers());
            assertNotNull(config.getStoneDrops());
            assertNotNull(config.getMapDrops());
            assertNotNull(config.getConsumableDrops());
            assertNotNull(config.getItemRemoval());
            assertNotNull(config.getAdvanced());
        }

        @Test
        @DisplayName("Default scope is 'all_worlds'")
        void scope_defaultsToAllWorlds() {
            var config = ContainerLootConfig.createDefaults();
            assertEquals("all_worlds", config.getScope());
            assertEquals(ContainerLootConfig.ReplacementScope.ALL_WORLDS, config.getReplacementScope());
        }

        @Test
        @DisplayName("Default enabled is true")
        void enabled_defaultsToTrue() {
            var config = ContainerLootConfig.createDefaults();
            assertTrue(config.isEnabled());
        }

        @Test
        @DisplayName("Default clearAllVanilla is true")
        void clearAllVanilla_defaultsToTrue() {
            var config = ContainerLootConfig.createDefaults();
            assertTrue(config.isClearAllVanilla());
        }

        @Test
        @DisplayName("TierConfig defaults are sensible")
        void tierConfig_defaultValues() {
            var tc = new ContainerLootConfig.TierConfig();
            assertEquals(1.0, tc.getLootMultiplier());
            assertEquals(0.0, tc.getRarityBonus());
            assertEquals(1.0, tc.getMapChanceMultiplier());
            assertEquals(1.0, tc.getStoneChanceMultiplier());
            assertEquals(1.0, tc.getConsumableChanceMultiplier());
            assertEquals(0, tc.getMinGearDrops());
            assertEquals(2, tc.getMaxGearDrops());
            assertEquals(3, tc.getMinItems());
            assertEquals(5, tc.getMaxItems());
            assertFalse(tc.isGuaranteedRareOrBetter());
            assertNotNull(tc.getPatterns());
            assertTrue(tc.getPatterns().isEmpty());
        }

        @Test
        @DisplayName("StoneDrops defaults")
        void stoneDrops_defaultValues() {
            var sd = new ContainerLootConfig.StoneDrops();
            assertTrue(sd.isEnabled());
            assertEquals(0.15, sd.getBaseChance(), 0.001);
            assertEquals(2, sd.getMaxPerContainer());
            assertNotNull(sd.getRarityWeights());
            assertFalse(sd.getRarityWeights().isEmpty());
        }

        @Test
        @DisplayName("MapDrops defaults")
        void mapDrops_defaultValues() {
            var md = new ContainerLootConfig.MapDrops();
            assertTrue(md.isEnabled());
            assertEquals(0.05, md.getBaseChance(), 0.001);
            assertEquals(1, md.getMaxPerContainer());
            assertEquals(-5, md.getLevelOffsetMin());
            assertEquals(5, md.getLevelOffsetMax());
        }

        @Test
        @DisplayName("ConsumableDrops defaults")
        void consumableDrops_defaultValues() {
            var cd = new ContainerLootConfig.ConsumableDrops();
            assertTrue(cd.isEnabled());
            assertEquals(0.40, cd.getBaseChance(), 0.001);
            assertEquals(2, cd.getMaxPerContainer());
            assertEquals(0.5, cd.getFoodWeight(), 0.001);
            assertEquals(0.5, cd.getPotionWeight(), 0.001);
        }

        @Test
        @DisplayName("Advanced defaults")
        void advanced_defaultValues() {
            var adv = new ContainerLootConfig.Advanced();
            assertEquals(3600000, adv.getContainerMemoryDurationMs());
            assertEquals(300000, adv.getCleanupIntervalMs());
            assertTrue(adv.isSkipCreativeMode());
            assertFalse(adv.isDebugLogging());
        }

        @Test
        @DisplayName("ItemRemoval defaults")
        void itemRemoval_defaultValues() {
            var ir = new ContainerLootConfig.ItemRemoval();
            assertTrue(ir.isRemoveWeapons());
            assertTrue(ir.isRemoveArmor());
            assertTrue(ir.isPreserveMaterials());
            assertTrue(ir.isPreserveConsumables());
            assertTrue(ir.isPreserveTools());
            assertNotNull(ir.getPreservedItems());
            assertTrue(ir.getPreservedItems().isEmpty());
            assertNotNull(ir.getRemovedItems());
            assertTrue(ir.getRemovedItems().isEmpty());
        }

        @Test
        @DisplayName("Sources defaults")
        void sources_defaultValues() {
            var src = new ContainerLootConfig.Sources();
            assertTrue(src.isTreasureChests());
            assertTrue(src.isRegularContainers());
        }
    }

    // =========================================================================
    // REPLACEMENT SCOPE ENUM
    // =========================================================================

    @Nested
    @DisplayName("ReplacementScope Enum")
    class ReplacementScopeEnum {

        @Test
        @DisplayName("fromConfigKey is case insensitive")
        void fromConfigKey_caseInsensitive() {
            assertEquals(ContainerLootConfig.ReplacementScope.REALM_ONLY,
                ContainerLootConfig.ReplacementScope.fromConfigKey("REALM_ONLY"));
            assertEquals(ContainerLootConfig.ReplacementScope.ALL_WORLDS,
                ContainerLootConfig.ReplacementScope.fromConfigKey("All_Worlds"));
            assertEquals(ContainerLootConfig.ReplacementScope.DISABLED,
                ContainerLootConfig.ReplacementScope.fromConfigKey("disabled"));
        }

        @Test
        @DisplayName("Unknown config key defaults to REALM_ONLY")
        void fromConfigKey_unknownKey_defaultsToRealmOnly() {
            assertEquals(ContainerLootConfig.ReplacementScope.REALM_ONLY,
                ContainerLootConfig.ReplacementScope.fromConfigKey("invalid_value"));
        }

        @Test
        @DisplayName("Null config key defaults to ALL_WORLDS")
        void fromConfigKey_nullKey_defaultsToAllWorlds() {
            assertEquals(ContainerLootConfig.ReplacementScope.ALL_WORLDS,
                ContainerLootConfig.ReplacementScope.fromConfigKey(null));
        }

        @Test
        @DisplayName("Empty config key defaults to ALL_WORLDS")
        void fromConfigKey_emptyKey_defaultsToAllWorlds() {
            assertEquals(ContainerLootConfig.ReplacementScope.ALL_WORLDS,
                ContainerLootConfig.ReplacementScope.fromConfigKey(""));
        }

        @Test
        @DisplayName("Each scope has correct config key")
        void configKeys_areCorrect() {
            assertEquals("realm_only", ContainerLootConfig.ReplacementScope.REALM_ONLY.getConfigKey());
            assertEquals("all_worlds", ContainerLootConfig.ReplacementScope.ALL_WORLDS.getConfigKey());
            assertEquals("disabled", ContainerLootConfig.ReplacementScope.DISABLED.getConfigKey());
        }
    }

    // =========================================================================
    // NULL SAFETY OF SETTERS
    // =========================================================================

    @Nested
    @DisplayName("Setter Null Safety")
    class SetterNullSafety {

        @Test
        @DisplayName("All config setters handle null gracefully")
        void setters_nullInput_defaultsApplied() {
            var config = new ContainerLootConfig();

            // These should set default values, not throw
            config.setScope(null);
            config.setSources(null);
            config.setLoot_scaling(null);
            config.setContainer_tiers(null);
            config.setStone_drops(null);
            config.setMap_drops(null);
            config.setConsumable_drops(null);
            config.setItem_removal(null);
            config.setAdvanced(null);

            // All should have non-null defaults
            assertEquals("all_worlds", config.getScope());
            assertNotNull(config.getSources());
            assertNotNull(config.getLootScaling());
            assertNotNull(config.getContainerTiers());
            assertNotNull(config.getStoneDrops());
            assertNotNull(config.getMapDrops());
            assertNotNull(config.getConsumableDrops());
            assertNotNull(config.getItemRemoval());
            assertNotNull(config.getAdvanced());
        }

        @Test
        @DisplayName("TierConfig setPatterns(null) defaults to empty list")
        void tierConfig_setPatterns_null_defaultsToEmpty() {
            var tc = new ContainerLootConfig.TierConfig();
            tc.setPatterns(null);
            assertNotNull(tc.getPatterns());
            assertTrue(tc.getPatterns().isEmpty());
        }

        @Test
        @DisplayName("StoneDrops setRarityWeights(null) defaults to empty map")
        void stoneDrops_setRarityWeights_null_defaultsToEmpty() {
            var sd = new ContainerLootConfig.StoneDrops();
            sd.setRarity_weights(null);
            assertNotNull(sd.getRarityWeights());
        }

        @Test
        @DisplayName("ItemRemoval setPreservedItems(null) defaults to empty list")
        void itemRemoval_setPreservedItems_null_defaultsToEmpty() {
            var ir = new ContainerLootConfig.ItemRemoval();
            ir.setPreserved_items(null);
            assertNotNull(ir.getPreservedItems());
            assertTrue(ir.getPreservedItems().isEmpty());
        }

        @Test
        @DisplayName("LevelRange setters handle null lists")
        void levelRange_setters_handleNull() {
            var lr = new ContainerLootConfig.LevelRange();
            // null lists should not change defaults
            lr.setPlayer_level_range(null);
            lr.setGear_level_range(null);
            lr.setRarity_weights(null);

            // Should still have defaults
            assertEquals(1, lr.getPlayerLevelMin());
            assertEquals(10, lr.getPlayerLevelMax());
            assertEquals(1, lr.getGearLevelMin());
            assertEquals(5, lr.getGearLevelMax());
            assertNotNull(lr.getRarityWeights());
        }

        @Test
        @DisplayName("LevelRange setters handle short lists")
        void levelRange_setters_handleShortLists() {
            var lr = new ContainerLootConfig.LevelRange();
            // Lists shorter than 2 should not change defaults
            lr.setPlayer_level_range(List.of(99));
            lr.setGear_level_range(List.of());

            // Defaults preserved
            assertEquals(1, lr.getPlayerLevelMin());
            assertEquals(10, lr.getPlayerLevelMax());
        }
    }
}
