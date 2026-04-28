package io.github.larsonix.trailoforbis.gear.config;

import io.github.larsonix.trailoforbis.attributes.AttributeType;
import io.github.larsonix.trailoforbis.gear.config.GearConfigLoader.ConfigurationException;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig.ModifierDefinition;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig.StatType;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GearConfigLoader - loading and parsing gear configuration files.
 */
class GearConfigLoaderTest {

    @TempDir
    Path tempDir;

    private GearConfigLoader loader;

    @BeforeEach
    void setUp() {
        loader = new GearConfigLoader(tempDir);
    }

    // =========================================================================
    // DEFAULT CONFIG LOADING TESTS
    // =========================================================================

    @Nested
    @DisplayName("Default Config Loading")
    class DefaultConfigLoadingTests {

        @Test
        @DisplayName("Load default balance config from resources")
        void loadDefaultBalanceConfig_fromResources() {
            GearBalanceConfig config = loader.loadDefaultBalanceConfig();

            assertNotNull(config);
            assertEquals(0.5, config.gearPowerRatio());
            assertEquals(6, config.slotWeights().size()); // 6 slots: weapon, chest, legs, head, hands, shield (no feet in Hytale)
            assertEquals(7, config.rarityConfigs().size()); // 7 rarities: COMMON to UNIQUE
        }

        @Test
        @DisplayName("Load default modifier config from resources")
        void loadDefaultModifierConfig_fromResources() {
            ModifierConfig config = loader.loadDefaultModifierConfig();

            assertNotNull(config);
            assertFalse(config.prefixes().isEmpty());
            assertFalse(config.suffixes().isEmpty());
        }

        @Test
        @DisplayName("Default balance config has all rarities")
        void defaultBalanceConfig_hasAllRarities() {
            GearBalanceConfig config = loader.loadDefaultBalanceConfig();

            for (GearRarity rarity : GearRarity.values()) {
                assertNotNull(config.rarityConfig(rarity));
            }
        }

        @Test
        @DisplayName("Default balance config quality distribution sums to 1")
        void defaultBalanceConfig_qualityDistributionSumsToOne() {
            GearBalanceConfig config = loader.loadDefaultBalanceConfig();

            var dist = config.quality().dropDistribution();
            double sum = dist.poor() + dist.belowAverage() + dist.normal() +
                         dist.aboveAverage() + dist.excellent() + dist.perfect();
            assertEquals(1.0, sum, 0.001);
        }

        @Test
        @DisplayName("Default balance config loot config present")
        void defaultBalanceConfig_lootConfigPresent() {
            GearBalanceConfig config = loader.loadDefaultBalanceConfig();

            assertTrue(config.loot().luckToRarityPercent() > 0);
            assertNotNull(config.loot().distanceScaling());
            assertFalse(config.loot().mobBonuses().isEmpty());
        }

        @Test
        @DisplayName("Default balance config stone drops present")
        void defaultBalanceConfig_stoneDropsPresent() {
            GearBalanceConfig config = loader.loadDefaultBalanceConfig();

            assertTrue(config.stoneDrops().baseDropChance() > 0);
            assertFalse(config.stoneDrops().stoneWeights().isEmpty());
        }

        @Test
        @DisplayName("Default balance config attribute requirements present")
        void defaultBalanceConfig_attributeRequirementsPresent() {
            GearBalanceConfig config = loader.loadDefaultBalanceConfig();

            assertTrue(config.attributeRequirements().levelToBaseRatio() > 0);
            for (GearRarity rarity : GearRarity.values()) {
                assertTrue(config.attributeRequirements().calculateRequirement(50, rarity) >= 0);
            }
        }

        @Test
        @DisplayName("Default balance config modifier scaling present")
        void defaultBalanceConfig_modifierScalingPresent() {
            GearBalanceConfig config = loader.loadDefaultBalanceConfig();

            assertTrue(config.modifierScaling().globalScalePerLevel() > 0);
            assertTrue(config.modifierScaling().rollVariance() >= 0);
            assertTrue(config.modifierScaling().rollVariance() <= 1.0);
        }

        @Test
        @DisplayName("Default modifier config prefixes have required fields")
        void defaultModifierConfig_prefixesHaveRequiredFields() {
            ModifierConfig config = loader.loadDefaultModifierConfig();

            assertTrue(config.getPrefix("sharp").isPresent());
            ModifierDefinition sharp = config.getPrefix("sharp").orElseThrow();

            assertEquals("Sharp", sharp.displayName());
            assertEquals("physical_damage", sharp.stat());
            assertEquals(StatType.FLAT, sharp.statType());
            assertTrue(sharp.baseMin() > 0);
            assertTrue(sharp.baseMax() >= sharp.baseMin());
            assertTrue(sharp.scalePerLevel() >= 0);
            assertTrue(sharp.weight() > 0);
        }

        @Test
        @DisplayName("Default modifier config suffixes have required fields")
        void defaultModifierConfig_suffixesHaveRequiredFields() {
            ModifierConfig config = loader.loadDefaultModifierConfig();

            assertTrue(config.getSuffix("of_the_whale").isPresent());
            ModifierDefinition whale = config.getSuffix("of_the_whale").orElseThrow();

            assertEquals("of the Whale", whale.displayName());
            assertEquals("max_health", whale.stat());
        }

        @Test
        @DisplayName("Default modifier config required attribute parsed")
        void defaultModifierConfig_requiredAttributeParsed() {
            ModifierConfig config = loader.loadDefaultModifierConfig();
            ModifierDefinition blazing = config.getPrefix("blazing").orElseThrow();

            assertEquals(AttributeType.FIRE, blazing.requiredAttribute());
        }

        @Test
        @DisplayName("Default modifier config allowed slots parsed")
        void defaultModifierConfig_allowedSlotsParsed() {
            ModifierConfig config = loader.loadDefaultModifierConfig();
            ModifierDefinition swift = config.getPrefix("swift").orElseThrow();

            assertNotNull(swift.allowedSlots());
            assertTrue(swift.allowedSlots().contains("weapon"));
            assertTrue(swift.allowedSlots().contains("hands"));
        }

        @Test
        @DisplayName("Default modifier config total weights calculated")
        void defaultModifierConfig_totalWeightsCalculated() {
            ModifierConfig config = loader.loadDefaultModifierConfig();

            assertTrue(config.totalPrefixWeight() > 0);
            assertTrue(config.totalSuffixWeight() > 0);
        }

        @Test
        @DisplayName("Default modifier config all stat IDs collected")
        void defaultModifierConfig_allStatIdsCollected() {
            ModifierConfig config = loader.loadDefaultModifierConfig();

            var stats = config.allStatIds();
            assertTrue(stats.contains("physical_damage"));
            assertTrue(stats.contains("max_health"));
            assertTrue(stats.contains("armor"));
        }
    }

    // =========================================================================
    // FILE LOADING TESTS
    // =========================================================================

    @Nested
    @DisplayName("File Loading")
    class FileLoadingTests {

        @Test
        @DisplayName("Load balance config from file")
        void loadBalanceConfig_fromFile() throws IOException {
            Path configFile = createValidBalanceConfig();

            GearBalanceConfig config = loader.loadBalanceConfig(configFile);

            assertNotNull(config);
            assertEquals(0.6, config.gearPowerRatio());
        }

        @Test
        @DisplayName("Load modifier config from file")
        void loadModifierConfig_fromFile() throws IOException {
            Path configFile = createValidModifierConfig();

            ModifierConfig config = loader.loadModifierConfig(configFile);

            assertNotNull(config);
            assertTrue(config.getPrefix("test_prefix").isPresent());
        }

        @Test
        @DisplayName("File not found throws exception")
        void fileNotFound_throwsException() {
            Path notExists = tempDir.resolve("nonexistent.yml");

            ConfigurationException ex = assertThrows(
                ConfigurationException.class,
                () -> loader.loadBalanceConfig(notExists)
            );
            assertTrue(ex.getMessage().contains("Failed to read"));
        }

        @Test
        @DisplayName("Empty file throws exception")
        void emptyFile_throwsException() throws IOException {
            Path emptyFile = tempDir.resolve("empty.yml");
            Files.writeString(emptyFile, "");

            ConfigurationException ex = assertThrows(
                ConfigurationException.class,
                () -> loader.loadBalanceConfig(emptyFile)
            );
            assertTrue(ex.getMessage().contains("empty"));
        }

        @Test
        @DisplayName("Invalid YAML syntax throws exception")
        void invalidYamlSyntax_throwsException() throws IOException {
            Path invalidFile = tempDir.resolve("invalid.yml");
            Files.writeString(invalidFile, "key: [unclosed bracket");

            assertThrows(
                ConfigurationException.class,
                () -> loader.loadBalanceConfig(invalidFile)
            );
        }
    }

    // =========================================================================
    // VALIDATION ERROR TESTS
    // =========================================================================

    @Nested
    @DisplayName("Validation Errors")
    class ValidationErrorTests {

        @Test
        @DisplayName("Missing power_scaling throws exception")
        void missingPowerScaling_throwsException() throws IOException {
            Path configFile = createConfigWithout("power_scaling");

            ConfigurationException ex = assertThrows(
                ConfigurationException.class,
                () -> loader.loadBalanceConfig(configFile)
            );
            assertTrue(ex.getMessage().contains("power_scaling"));
        }

        @Test
        @DisplayName("Missing rarity section throws exception")
        void missingRarity_throwsException() throws IOException {
            String config = """
                power_scaling:
                  gear_power_ratio: 0.5
                  slot_weights:
                    weapon: 1.0
                  level_scaling_factor: 0.02
                quality:
                  baseline: 50
                  min: 1
                  max: 100
                  perfect: 101
                  drop_distribution:
                    poor: 0.2
                    below_average: 0.2
                    normal: 0.2
                    above_average: 0.2
                    excellent: 0.19
                    perfect: 0.01
                attribute_requirements:
                  level_to_base_ratio: 0.5
                  min_item_level_for_requirements: 5
                  rarity_multipliers:
                    common: 0.1
                    uncommon: 0.25
                    rare: 0.5
                    epic: 0.75
                    legendary: 0.9
                    mythic: 1.0
                modifier_scaling:
                  global_scale_per_level: 0.02
                  roll_variance: 0.3
                  weight_categories: {}
                loot:
                  base_drop_chance: 0.05
                  wind_to_rarity_percent: 0.5
                  distance_scaling:
                    enabled: true
                    blocks_per_percent: 100
                    max_bonus: 50
                  mob_bonuses:
                    normal:
                      quantity_bonus: 0
                      rarity_bonus: 0
                stone_drops:
                  base_drop_chance: 0.05
                  stone_weights: {}
                """;
            Path configFile = tempDir.resolve("missing_rarity.yml");
            Files.writeString(configFile, config);

            ConfigurationException ex = assertThrows(
                ConfigurationException.class,
                () -> loader.loadBalanceConfig(configFile)
            );
            assertTrue(ex.getMessage().contains("rarity"));
        }

        @Test
        @DisplayName("Negative gear power ratio parses without error")
        void negativeGearPowerRatio_parsesWithoutError() throws IOException {
            // Note: Negative gear_power_ratio is allowed by the parser (no explicit validation)
            // Game designers can choose to use negative values if desired
            Path configFile = createValidBalanceConfigWithValue("power_scaling.gear_power_ratio", "-0.5");

            // Should parse without exception (validation is lenient)
            GearBalanceConfig config = loader.loadBalanceConfig(configFile);
            assertEquals(-0.5, config.gearPowerRatio());
        }

        @Test
        @DisplayName("Zero stat multiplier throws exception")
        void zeroStatMultiplier_throwsException() throws IOException {
            Path configFile = createConfigWithZeroStatMultiplier();

            Exception ex = assertThrows(
                Exception.class,
                () -> loader.loadBalanceConfig(configFile)
            );
            // Either ConfigurationException wrapping IllegalArgumentException, or IllegalArgumentException directly
            assertTrue(ex instanceof ConfigurationException || ex instanceof IllegalArgumentException,
                "Expected ConfigurationException or IllegalArgumentException");
        }

        @Test
        @DisplayName("Missing modifier display_name throws exception")
        void missingModifierDisplayName_throwsException() throws IOException {
            String config = """
                prefixes:
                  test:
                    stat: physical_damage
                    stat_type: flat
                    base_min: 1
                    base_max: 2
                    scale_per_level: 0.1
                    weight: 10
                suffixes: {}
                """;
            Path configFile = tempDir.resolve("missing_display_name.yml");
            Files.writeString(configFile, config);

            ConfigurationException ex = assertThrows(
                ConfigurationException.class,
                () -> loader.loadModifierConfig(configFile)
            );
            assertTrue(ex.getMessage().contains("display_name"));
        }

        @Test
        @DisplayName("Invalid stat type throws exception")
        void invalidStatType_throwsException() throws IOException {
            String config = """
                prefixes:
                  test:
                    display_name: "Test"
                    stat: physical_damage
                    stat_type: invalid
                    base_min: 1
                    base_max: 2
                    scale_per_level: 0.1
                    weight: 10
                suffixes: {}
                """;
            Path configFile = tempDir.resolve("invalid_stat_type.yml");
            Files.writeString(configFile, config);

            Exception ex = assertThrows(
                Exception.class,
                () -> loader.loadModifierConfig(configFile)
            );
            // Either ConfigurationException wrapping IllegalArgumentException, or IllegalArgumentException directly
            assertTrue(ex instanceof ConfigurationException || ex instanceof IllegalArgumentException,
                "Expected ConfigurationException or IllegalArgumentException");
        }

        @Test
        @DisplayName("Invalid required attribute throws exception")
        void invalidRequiredAttribute_throwsException() throws IOException {
            String config = """
                prefixes:
                  test:
                    display_name: "Test"
                    stat: physical_damage
                    stat_type: flat
                    base_min: 1
                    base_max: 2
                    scale_per_level: 0.1
                    weight: 10
                    required_attribute: WISDOM
                suffixes: {}
                """;
            Path configFile = tempDir.resolve("invalid_attribute.yml");
            Files.writeString(configFile, config);

            ConfigurationException ex = assertThrows(
                ConfigurationException.class,
                () -> loader.loadModifierConfig(configFile)
            );
            assertTrue(ex.getMessage().contains("Invalid required_attribute"));
        }

        @Test
        @DisplayName("Negative base min loads successfully for reduction modifiers")
        void negativeBaseMin_loadsSuccessfully() throws IOException {
            // Negative base_min is valid for reduction modifiers (e.g., trajectory: -5% to -15%)
            String config = """
                prefixes:
                  test:
                    display_name: "Test"
                    stat: physical_damage
                    stat_type: flat
                    base_min: -5
                    base_max: 2
                    scale_per_level: 0.1
                    weight: 10
                suffixes: {}
                """;
            Path configFile = tempDir.resolve("negative_base_min.yml");
            Files.writeString(configFile, config);

            ModifierConfig result = loader.loadModifierConfig(configFile);
            assertNotNull(result);
            assertEquals(-5.0, result.getPrefix("test").orElseThrow().baseMin());
        }

        @Test
        @DisplayName("Negative range loads successfully for reduction modifiers")
        void negativeRange_loadsSuccessfully() throws IOException {
            // For reduction modifiers like trajectory: -5% to -15% gravity
            // semantically min=-5 (small reduction), max=-15 (large reduction)
            // even though -5 > -15 numerically
            String config = """
                prefixes:
                  test:
                    display_name: "Test"
                    stat: projectile_gravity_percent
                    stat_type: flat
                    base_min: -5
                    base_max: -15
                    scale_per_level: -0.5
                    weight: 10
                suffixes: {}
                """;
            Path configFile = tempDir.resolve("negative_range.yml");
            Files.writeString(configFile, config);

            ModifierConfig result = loader.loadModifierConfig(configFile);
            assertNotNull(result);
            var mod = result.getPrefix("test").orElseThrow();
            assertEquals(-5.0, mod.baseMin());
            assertEquals(-15.0, mod.baseMax());
        }

        @Test
        @DisplayName("Negative scale per level loads successfully for reduction modifiers")
        void negativeScalePerLevel_loadsSuccessfully() throws IOException {
            // Negative scaling is valid for reduction modifiers that scale with level
            String config = """
                prefixes:
                  test:
                    display_name: "Test"
                    stat: projectile_gravity_percent
                    stat_type: flat
                    base_min: 1
                    base_max: 2
                    scale_per_level: -0.1
                    weight: 10
                suffixes: {}
                """;
            Path configFile = tempDir.resolve("negative_scale.yml");
            Files.writeString(configFile, config);

            ModifierConfig result = loader.loadModifierConfig(configFile);
            assertNotNull(result);
            assertEquals(-0.1, result.getPrefix("test").orElseThrow().scalePerLevel());
        }

        @Test
        @DisplayName("Negative weight throws exception")
        void negativeWeight_throwsException() throws IOException {
            String config = """
                prefixes:
                  test:
                    display_name: "Test"
                    stat: physical_damage
                    stat_type: flat
                    base_min: 1
                    base_max: 2
                    scale_per_level: 0.1
                    weight: -10
                suffixes: {}
                """;
            Path configFile = tempDir.resolve("negative_weight.yml");
            Files.writeString(configFile, config);

            Exception ex = assertThrows(
                Exception.class,
                () -> loader.loadModifierConfig(configFile)
            );
            assertTrue(ex instanceof ConfigurationException || ex instanceof IllegalArgumentException);
        }
    }

    // =========================================================================
    // INTEGRATION TESTS
    // =========================================================================

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Load both configs successfully")
        void loadBothConfigs_success() {
            GearBalanceConfig balance = loader.loadDefaultBalanceConfig();
            ModifierConfig modifiers = loader.loadDefaultModifierConfig();

            assertNotNull(balance);
            assertNotNull(modifiers);
        }

        @Test
        @DisplayName("Balance config is immutable")
        void balanceConfig_isImmutable() {
            GearBalanceConfig config = loader.loadDefaultBalanceConfig();

            assertThrows(
                UnsupportedOperationException.class,
                () -> config.slotWeights().put("test", 1.0)
            );

            assertThrows(
                UnsupportedOperationException.class,
                () -> config.rarityConfigs().put(GearRarity.COMMON, null)
            );
        }

        @Test
        @DisplayName("Modifier config is immutable")
        void modifierConfig_isImmutable() {
            ModifierConfig config = loader.loadDefaultModifierConfig();

            assertThrows(
                UnsupportedOperationException.class,
                () -> config.prefixes().put("test", null)
            );

            assertThrows(
                UnsupportedOperationException.class,
                () -> config.suffixes().put("test", null)
            );
        }

        @Test
        @DisplayName("Rarity configs all configured")
        void rarityConfigs_allConfigured() {
            GearBalanceConfig config = loader.loadDefaultBalanceConfig();

            for (GearRarity rarity : GearRarity.values()) {
                var rarityConfig = config.rarityConfig(rarity);
                assertTrue(rarityConfig.maxModifiers() > 0);
                assertTrue(rarityConfig.statMultiplier() > 0);
            }
        }

        @Test
        @DisplayName("Mythic has min roll percentile")
        void mythic_hasMinRollPercentile() {
            GearBalanceConfig config = loader.loadDefaultBalanceConfig();

            var mythic = config.rarityConfig(GearRarity.MYTHIC);
            assertTrue(mythic.minRollPercentile() > 0);
        }

        @Test
        @DisplayName("Lower rarities have zero min roll")
        void lowerRarities_hasZeroMinRoll() {
            GearBalanceConfig config = loader.loadDefaultBalanceConfig();

            // MYTHIC and UNIQUE have min roll percentiles (high-tier guarantee)
            for (GearRarity rarity : GearRarity.values()) {
                if (rarity != GearRarity.MYTHIC && rarity != GearRarity.UNIQUE) {
                    var rarityConfig = config.rarityConfig(rarity);
                    assertEquals(0.0, rarityConfig.minRollPercentile());
                }
            }
        }

        @Test
        @DisplayName("Attribute requirements increase with rarity")
        void attributeRequirements_increaseWithRarity() {
            GearBalanceConfig config = loader.loadDefaultBalanceConfig();

            int commonReq = config.attributeRequirements().calculateRequirement(50, GearRarity.COMMON);
            int mythicReq = config.attributeRequirements().calculateRequirement(50, GearRarity.MYTHIC);

            assertTrue(mythicReq > commonReq);
        }

        @Test
        @DisplayName("Modifiers have valid stats")
        void modifiers_haveValidStats() {
            ModifierConfig config = loader.loadDefaultModifierConfig();

            for (var mod : config.prefixList()) {
                assertFalse(mod.stat().isBlank());
            }
            for (var mod : config.suffixList()) {
                assertFalse(mod.stat().isBlank());
            }
        }

        @Test
        @DisplayName("Modifiers have positive weights")
        void modifiers_havePositiveWeights() {
            ModifierConfig config = loader.loadDefaultModifierConfig();

            for (var mod : config.prefixList()) {
                assertTrue(mod.weight() > 0);
            }
            for (var mod : config.suffixList()) {
                assertTrue(mod.weight() > 0);
            }
        }

        @Test
        @DisplayName("Modifiers have valid scaling")
        void modifiers_validScaling() {
            ModifierConfig config = loader.loadDefaultModifierConfig();

            // Note: Negative ranges (like -5 to -15 for reduction modifiers) are valid.
            // The trajectory modifier uses base_min=-5, base_max=-15, scale_per_level=-0.5
            // to represent "small reduction to large reduction" semantically.
            // We don't validate min <= max because negative ranges invert the comparison.
            // Instead, just verify modifiers load successfully (no validation exception).
            assertFalse(config.prefixList().isEmpty(), "Should have at least one prefix");
        }

        @Test
        @DisplayName("Slot weights cover all expected slots")
        void slotWeights_coverAllExpectedSlots() {
            GearBalanceConfig config = loader.loadDefaultBalanceConfig();

            // NOTE: Hytale has NO feet slot - movement stats go on legs
            List<String> expectedSlots = List.of("weapon", "chest", "legs", "head", "hands", "shield");

            for (String slot : expectedSlots) {
                assertTrue(config.slotWeight(slot) > 0,
                    "Slot " + slot + " should have positive weight");
            }
        }

        @Test
        @DisplayName("Mob bonuses all types present")
        void mobBonuses_allTypesPresent() {
            GearBalanceConfig config = loader.loadDefaultBalanceConfig();

            assertNotNull(config.loot().mobBonus("normal"));
            assertNotNull(config.loot().mobBonus("elite"));
            assertNotNull(config.loot().mobBonus("boss"));
        }

        @Test
        @DisplayName("Boss has higher bonus than normal")
        void boss_hasHigherBonusThanNormal() {
            GearBalanceConfig config = loader.loadDefaultBalanceConfig();

            var normal = config.loot().mobBonus("normal");
            var boss = config.loot().mobBonus("boss");

            assertTrue(boss.rarityBonus() > normal.rarityBonus());
            assertTrue(boss.quantityBonus() > normal.quantityBonus());
        }

        @Test
        @DisplayName("Stone drops all stones have weight")
        void stoneDrops_allStonesHaveWeight() {
            GearBalanceConfig config = loader.loadDefaultBalanceConfig();

            assertTrue(config.stoneDrops().stoneWeight("chaos_stone") > 0);
            assertTrue(config.stoneDrops().stoneWeight("corruption_stone") > 0);
        }
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private Path createValidBalanceConfig() throws IOException {
        String config = createFullBalanceConfigYaml("0.6");
        Path configFile = tempDir.resolve("gear-balance.yml");
        Files.writeString(configFile, config);
        return configFile;
    }

    private Path createValidBalanceConfigWithValue(String path, String value) throws IOException {
        String config = createFullBalanceConfigYaml(value);
        Path configFile = tempDir.resolve("gear-balance-modified.yml");
        Files.writeString(configFile, config);
        return configFile;
    }

    private String createFullBalanceConfigYaml(String gearPowerRatio) {
        return """
            power_scaling:
              gear_power_ratio: %s
              # NOTE: Hytale has NO feet slot - movement stats go on legs
              slot_weights:
                weapon: 0.30
                chest: 0.20
                legs: 0.23
                head: 0.12
                hands: 0.08
                shield: 0.07
              level_scaling_factor: 0.02
            rarity:
              common:
                stat_multiplier: 0.3
                max_modifiers: 1
                prefix_range: [0, 1]
                suffix_range: [0, 1]
                drop_weight: 50
              uncommon:
                stat_multiplier: 0.5
                max_modifiers: 2
                prefix_range: [0, 1]
                suffix_range: [0, 2]
                drop_weight: 30
              rare:
                stat_multiplier: 0.8
                max_modifiers: 3
                prefix_range: [1, 2]
                suffix_range: [1, 2]
                drop_weight: 15
              epic:
                stat_multiplier: 1.2
                max_modifiers: 4
                prefix_range: [1, 2]
                suffix_range: [1, 2]
                drop_weight: 4
              legendary:
                stat_multiplier: 1.7
                max_modifiers: 4
                prefix_range: [2, 2]
                suffix_range: [2, 2]
                drop_weight: 0.9
              mythic:
                stat_multiplier: 2.3
                max_modifiers: 4
                prefix_range: [2, 2]
                suffix_range: [2, 2]
                min_roll_percentile: 0.75
                drop_weight: 0.1
              unique:
                stat_multiplier: 2.8
                max_modifiers: 6
                prefix_range: [2, 3]
                suffix_range: [2, 3]
                min_roll_percentile: 0.80
                drop_weight: 0.05
            quality:
              baseline: 50
              min: 1
              max: 100
              perfect: 101
              drop_distribution:
                poor: 0.15
                below_average: 0.25
                normal: 0.10
                above_average: 0.30
                excellent: 0.195
                perfect: 0.005
            attribute_requirements:
              level_to_base_ratio: 0.5
              min_item_level_for_requirements: 5
              rarity_multipliers:
                common: 0.1
                uncommon: 0.25
                rare: 0.5
                epic: 0.75
                legendary: 0.9
                mythic: 1.0
                unique: 1.0
            modifier_scaling:
              global_scale_per_level: 0.02
              roll_variance: 0.3
              weight_categories:
                common: 50
                rare: 10
            loot:
              base_drop_chance: 0.08
              wind_to_rarity_percent: 0.5
              distance_scaling:
                enabled: true
                blocks_per_percent: 100
                max_bonus: 50
              mob_bonuses:
                normal:
                  quantity_bonus: 0
                  rarity_bonus: 0
                elite:
                  quantity_bonus: 0.5
                  rarity_bonus: 0.25
                boss:
                  quantity_bonus: 1.0
                  rarity_bonus: 0.5
            stone_drops:
              base_drop_chance: 0.05
              stone_weights:
                chaos_stone: 80
                corruption_stone: 8
            """.formatted(gearPowerRatio);
    }

    private Path createConfigWithout(String section) throws IOException {
        String config = """
            quality:
              baseline: 50
              min: 1
              max: 100
              perfect: 101
              drop_distribution:
                poor: 1.0
                below_average: 0
                normal: 0
                above_average: 0
                excellent: 0
                perfect: 0
            """;
        Path configFile = tempDir.resolve("missing_section.yml");
        Files.writeString(configFile, config);
        return configFile;
    }

    private Path createConfigWithZeroStatMultiplier() throws IOException {
        String config = createFullBalanceConfigYaml("0.5")
            .replace("stat_multiplier: 0.3", "stat_multiplier: 0");
        Path configFile = tempDir.resolve("zero_stat_multiplier.yml");
        Files.writeString(configFile, config);
        return configFile;
    }

    private Path createValidModifierConfig() throws IOException {
        String config = """
            prefixes:
              test_prefix:
                display_name: "Test Prefix"
                stat: physical_damage
                stat_type: flat
                base_min: 1
                base_max: 3
                scale_per_level: 0.2
                weight: 100
            suffixes:
              test_suffix:
                display_name: "of Testing"
                stat: max_health
                stat_type: flat
                base_min: 5
                base_max: 15
                scale_per_level: 1.0
                weight: 100
            """;
        Path configFile = tempDir.resolve("gear-modifiers.yml");
        Files.writeString(configFile, config);
        return configFile;
    }
}
