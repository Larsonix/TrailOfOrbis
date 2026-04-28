package io.github.larsonix.trailoforbis.gear.generation;

import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig.ModifierDefinition;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig.StatType;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig.ValueRange;
import io.github.larsonix.trailoforbis.gear.config.TestConfigFactory;
import io.github.larsonix.trailoforbis.gear.model.GearModifier;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.gear.model.ModifierType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ModifierPool - 25 test cases.
 */
class ModifierPoolTest {

    private ModifierConfig modConfig;
    private GearBalanceConfig balanceConfig;

    @BeforeEach
    void setUp() {
        modConfig = createTestModifierConfig();
        balanceConfig = createTestBalanceConfig();
    }

    // =========================================================================
    // SELECTION TESTS (10 cases)
    // =========================================================================

    @Nested
    @DisplayName("Modifier Selection")
    class SelectionTests {

        @Test
        @DisplayName("rollPrefixes returns requested count")
        void rollPrefixes_ReturnsRequestedCount() {
            ModifierPool pool = new ModifierPool(modConfig, balanceConfig);
            List<GearModifier> prefixes = pool.rollPrefixes(2, 50, "weapon", GearRarity.EPIC);
            assertEquals(2, prefixes.size());
        }

        @Test
        @DisplayName("rollSuffixes returns requested count")
        void rollSuffixes_ReturnsRequestedCount() {
            ModifierPool pool = new ModifierPool(modConfig, balanceConfig);
            List<GearModifier> suffixes = pool.rollSuffixes(2, 50, "weapon", GearRarity.EPIC);
            assertEquals(2, suffixes.size());
        }

        @Test
        @DisplayName("rollPrefixes with zero count returns empty list")
        void rollPrefixes_ZeroCount_ReturnsEmptyList() {
            ModifierPool pool = new ModifierPool(modConfig, balanceConfig);
            List<GearModifier> prefixes = pool.rollPrefixes(0, 50, "weapon", GearRarity.EPIC);
            assertTrue(prefixes.isEmpty());
        }

        @Test
        @DisplayName("rollPrefixes returns no duplicates")
        void rollPrefixes_NoDuplicates() {
            ModifierPool pool = new ModifierPool(modConfig, balanceConfig);
            List<GearModifier> prefixes = pool.rollPrefixes(2, 50, "weapon", GearRarity.LEGENDARY);

            Set<String> ids = prefixes.stream()
                .map(GearModifier::id)
                .collect(Collectors.toSet());

            assertEquals(prefixes.size(), ids.size());
        }

        @Test
        @DisplayName("rollPrefixes respects slot restriction")
        void rollPrefixes_RespectsSlotRestriction() {
            ModifierPool pool = new ModifierPool(modConfig, balanceConfig);

            // "of_speed" suffix is restricted to feet
            List<GearModifier> chestSuffixes = pool.rollSuffixes(10, 50, "chest", GearRarity.LEGENDARY);

            for (GearModifier mod : chestSuffixes) {
                assertNotEquals("of_speed", mod.id());
            }
        }

        @Test
        @DisplayName("Modifiers allowed on slot are included")
        void rollPrefixes_AllowedOnSlot_Included() {
            ModifierPool pool = new ModifierPool(modConfig, balanceConfig);

            // "swift" is allowed on weapon
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < 100; i++) {
                pool.rollPrefixes(2, 50, "weapon", GearRarity.LEGENDARY)
                    .forEach(m -> seen.add(m.id()));
            }

            assertTrue(seen.contains("swift"), "Should see 'swift' modifier");
        }

        @Test
        @DisplayName("Unknown slot returns unrestricted modifiers")
        void rollPrefixes_UnknownSlot_ReturnsUnrestrictedModifiers() {
            ModifierPool pool = new ModifierPool(modConfig, balanceConfig);
            List<GearModifier> prefixes = pool.rollPrefixes(2, 50, "unknown_slot", GearRarity.EPIC);

            // Should still get modifiers with no slot restriction
            assertFalse(prefixes.isEmpty());
        }

        @Test
        @DisplayName("More than available returns what's available")
        void rollPrefixes_MoreThanAvailable_ReturnsAvailable() {
            // Create config with only 2 prefixes for all slots
            ModifierConfig limitedConfig = createLimitedModifierConfig(2, 2);
            ModifierPool pool = new ModifierPool(limitedConfig, balanceConfig);

            List<GearModifier> prefixes = pool.rollPrefixes(5, 50, "weapon", GearRarity.LEGENDARY);

            assertEquals(2, prefixes.size());
        }

        @Test
        @DisplayName("Weighted selection matches weight distribution")
        void selectWeighted_DistributionMatchesWeights() {
            ModifierPool pool = new ModifierPool(modConfig, balanceConfig);

            Map<String, Integer> counts = new HashMap<>();
            for (int i = 0; i < 10000; i++) {
                List<GearModifier> mods = pool.rollPrefixes(1, 50, "weapon", GearRarity.COMMON);
                if (!mods.isEmpty()) {
                    counts.merge(mods.get(0).id(), 1, Integer::sum);
                }
            }

            // "sharp" (weight 100) should appear more than "deadly" (weight 10)
            assertTrue(counts.getOrDefault("sharp", 0) > counts.getOrDefault("deadly", 0) * 5,
                "Sharp should appear 5x+ more than deadly");
        }

        @Test
        @DisplayName("Selection probability matches weight")
        void getSelectionProbability_MatchesWeight() {
            ModifierPool pool = new ModifierPool(modConfig, balanceConfig);

            double sharpProb = pool.getSelectionProbability("sharp", ModifierType.PREFIX, "weapon");
            double deadlyProb = pool.getSelectionProbability("deadly", ModifierType.PREFIX, "weapon");

            // Sharp (100) should be ~10x more likely than Deadly (10)
            assertTrue(sharpProb / deadlyProb >= 9.0 && sharpProb / deadlyProb <= 11.0,
                "Sharp probability ratio should be ~10x deadly");
        }
    }

    // =========================================================================
    // VALUE CALCULATION TESTS (10 cases)
    // =========================================================================

    @Nested
    @DisplayName("Value Calculation")
    class ValueCalculationTests {

        @Test
        @DisplayName("Higher level produces higher value")
        void calculateValue_HigherLevel_HigherValue() {
            ModifierPool pool = new ModifierPool(modConfig, balanceConfig, new Random(123));
            ModifierDefinition def = modConfig.getPrefix("sharp").orElseThrow();

            double lowLevel = pool.calculateValue(def, 10, GearRarity.EPIC);
            double highLevel = pool.calculateValue(def, 100, GearRarity.EPIC);

            assertTrue(highLevel > lowLevel, "Higher level should have higher value");
        }

        @Test
        @DisplayName("Legendary has extended range")
        void calculateValue_Legendary_ExtendedRange() {
            ModifierPool pool = new ModifierPool(modConfig, balanceConfig, new Random(456));
            ModifierDefinition def = modConfig.getPrefix("sharp").orElseThrow();

            List<Double> epicValues = new ArrayList<>();
            List<Double> legendaryValues = new ArrayList<>();

            for (int i = 0; i < 1000; i++) {
                epicValues.add(pool.calculateValue(def, 50, GearRarity.EPIC));
                legendaryValues.add(pool.calculateValue(def, 50, GearRarity.LEGENDARY));
            }

            double epicMax = epicValues.stream().mapToDouble(Double::doubleValue).max().orElse(0);
            double legendaryMax = legendaryValues.stream().mapToDouble(Double::doubleValue).max().orElse(0);

            assertTrue(legendaryMax > epicMax, "Legendary max should be higher than epic");
        }

        @Test
        @DisplayName("Mythic has minimum roll percentile")
        void calculateValue_Mythic_MinimumRoll() {
            ModifierPool pool = new ModifierPool(modConfig, balanceConfig);
            ModifierDefinition def = modConfig.getPrefix("sharp").orElseThrow();

            List<Double> mythicValues = new ArrayList<>();
            for (int i = 0; i < 1000; i++) {
                mythicValues.add(pool.calculateValue(def, 50, GearRarity.MYTHIC));
            }

            ValueRange range = def.calculateRange(50);
            double extendedMax = range.max() * balanceConfig.rarityConfig(GearRarity.MYTHIC).statMultiplier();
            double expectedMin = range.min() + (extendedMax - range.min()) * 0.75;

            double actualMin = mythicValues.stream().mapToDouble(Double::doubleValue).min().orElse(0);
            double varianceBuffer = expectedMin * balanceConfig.modifierScaling().rollVariance();

            assertTrue(actualMin >= expectedMin - varianceBuffer - 0.1,
                "Mythic min should respect minimum roll percentile");
        }

        @Test
        @DisplayName("Common has lower stat multiplier")
        void calculateValue_Common_LowerStatMultiplier() {
            ModifierPool pool = new ModifierPool(modConfig, balanceConfig, new Random(789));
            ModifierDefinition def = modConfig.getPrefix("sharp").orElseThrow();

            List<Double> commonValues = new ArrayList<>();
            List<Double> epicValues = new ArrayList<>();

            for (int i = 0; i < 1000; i++) {
                commonValues.add(pool.calculateValue(def, 50, GearRarity.COMMON));
                epicValues.add(pool.calculateValue(def, 50, GearRarity.EPIC));
            }

            double commonAvg = commonValues.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double epicAvg = epicValues.stream().mapToDouble(Double::doubleValue).average().orElse(0);

            assertTrue(commonAvg < epicAvg, "Common avg should be less than epic");
        }

        @Test
        @DisplayName("Deterministic with seed")
        void calculateValue_Deterministic_WithSeed() {
            ModifierPool pool1 = new ModifierPool(modConfig, balanceConfig, new Random(111));
            ModifierPool pool2 = new ModifierPool(modConfig, balanceConfig, new Random(111));
            ModifierDefinition def = modConfig.getPrefix("sharp").orElseThrow();

            for (int i = 0; i < 10; i++) {
                assertEquals(pool1.calculateValue(def, 50, GearRarity.EPIC),
                    pool2.calculateValue(def, 50, GearRarity.EPIC), 0.0001);
            }
        }

        @Test
        @DisplayName("Rolled modifiers have correct type")
        void rollModifiers_CorrectType() {
            ModifierPool pool = new ModifierPool(modConfig, balanceConfig);

            List<GearModifier> prefixes = pool.rollPrefixes(2, 50, "weapon", GearRarity.EPIC);
            List<GearModifier> suffixes = pool.rollSuffixes(2, 50, "weapon", GearRarity.EPIC);

            for (GearModifier mod : prefixes) {
                assertEquals(ModifierType.PREFIX, mod.type());
            }
            for (GearModifier mod : suffixes) {
                assertEquals(ModifierType.SUFFIX, mod.type());
            }
        }

        @Test
        @DisplayName("Rolled modifiers have correct stat")
        void rollModifiers_HasCorrectStat() {
            ModifierPool pool = new ModifierPool(modConfig, balanceConfig);
            List<GearModifier> prefixes = pool.rollPrefixes(1, 50, "weapon", GearRarity.EPIC);

            if (!prefixes.isEmpty()) {
                GearModifier mod = prefixes.get(0);
                ModifierDefinition def = modConfig.getPrefix(mod.id()).orElseThrow();
                assertEquals(def.stat(), mod.statId());
            }
        }

        @Test
        @DisplayName("Rolled modifiers have reasonable value range")
        void rollModifiers_ValueInReasonableRange() {
            ModifierPool pool = new ModifierPool(modConfig, balanceConfig);
            List<GearModifier> prefixes = pool.rollPrefixes(1, 50, "weapon", GearRarity.EPIC);

            if (!prefixes.isEmpty()) {
                GearModifier mod = prefixes.get(0);
                assertTrue(mod.value() > 0, "Value should be > 0");
                assertTrue(mod.value() < 10000, "Value should be < 10000");
            }
        }

        @Test
        @DisplayName("getAvailablePrefixCount matches config")
        void getAvailablePrefixCount_MatchesConfig() {
            ModifierPool pool = new ModifierPool(modConfig, balanceConfig);

            int count = pool.getAvailablePrefixCount("weapon");
            int expected = modConfig.prefixesForSlot("weapon").size();

            assertEquals(expected, count);
        }

        @Test
        @DisplayName("getAvailableSuffixCount matches config")
        void getAvailableSuffixCount_MatchesConfig() {
            ModifierPool pool = new ModifierPool(modConfig, balanceConfig);

            int count = pool.getAvailableSuffixCount("feet");
            int expected = modConfig.suffixesForSlot("feet").size();

            assertEquals(expected, count);
        }
    }

    // =========================================================================
    // EDGE CASES (5 cases)
    // =========================================================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Null modifier config throws exception")
        void constructor_NullModifierConfig_ThrowsException() {
            assertThrows(NullPointerException.class, () -> new ModifierPool(null, balanceConfig));
        }

        @Test
        @DisplayName("Null balance config throws exception")
        void constructor_NullBalanceConfig_ThrowsException() {
            assertThrows(NullPointerException.class, () -> new ModifierPool(modConfig, null));
        }

        @Test
        @DisplayName("Negative count returns empty list")
        void rollPrefixes_NegativeCount_ReturnsEmpty() {
            ModifierPool pool = new ModifierPool(modConfig, balanceConfig);
            List<GearModifier> prefixes = pool.rollPrefixes(-1, 50, "weapon", GearRarity.EPIC);
            assertTrue(prefixes.isEmpty());
        }

        @Test
        @DisplayName("No modifiers for slot returns empty list")
        void rollPrefixes_NoModifiersForSlot_ReturnsEmpty() {
            ModifierConfig emptyConfig = createEmptyModifierConfig();
            ModifierPool pool = new ModifierPool(emptyConfig, balanceConfig);

            List<GearModifier> prefixes = pool.rollPrefixes(2, 50, "accessory", GearRarity.EPIC);
            assertTrue(prefixes.isEmpty());
        }

        @Test
        @DisplayName("Modifier not found returns zero probability")
        void getSelectionProbability_NotFound_ReturnsZero() {
            ModifierPool pool = new ModifierPool(modConfig, balanceConfig);
            double prob = pool.getSelectionProbability("nonexistent", ModifierType.PREFIX, "weapon");
            assertEquals(0, prob, 0.0001);
        }
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private ModifierConfig createTestModifierConfig() {
        Map<String, ModifierDefinition> prefixes = new HashMap<>();
        prefixes.put("sharp", new ModifierDefinition(
            "sharp", "Sharp", "physical_damage", StatType.FLAT,
            5.0, 15.0, 0.5, 100, null, null // weight 100, all slots
        ));
        prefixes.put("deadly", new ModifierDefinition(
            "deadly", "Deadly", "critical_damage", StatType.PERCENT,
            2.0, 8.0, 0.3, 10, null, null // weight 10, all slots
        ));
        prefixes.put("swift", new ModifierDefinition(
            "swift", "Swift", "attack_speed", StatType.PERCENT,
            1.0, 5.0, 0.2, 50, null, null // weight 50, all slots
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
            3.0, 10.0, 0.3, 30, null, Set.of("feet") // only on feet
        ));

        return TestConfigFactory.createModifierConfig(prefixes, suffixes);
    }

    private ModifierConfig createLimitedModifierConfig(int prefixCount, int suffixCount) {
        Map<String, ModifierDefinition> prefixes = new HashMap<>();
        for (int i = 0; i < prefixCount; i++) {
            prefixes.put("prefix_" + i, new ModifierDefinition(
                "prefix_" + i, "Prefix " + i, "stat_" + i, StatType.FLAT,
                1.0, 10.0, 0.1, 50, null, null
            ));
        }

        Map<String, ModifierDefinition> suffixes = new HashMap<>();
        for (int i = 0; i < suffixCount; i++) {
            suffixes.put("suffix_" + i, new ModifierDefinition(
                "suffix_" + i, "Suffix " + i, "stat_" + i, StatType.FLAT,
                1.0, 10.0, 0.1, 50, null, null
            ));
        }

        return TestConfigFactory.createModifierConfig(prefixes, suffixes);
    }

    private ModifierConfig createEmptyModifierConfig() {
        return TestConfigFactory.createEmptyModifierConfig();
    }

    private GearBalanceConfig createTestBalanceConfig() {
        return TestConfigFactory.createDefaultBalanceConfig();
    }
}
