package io.github.larsonix.trailoforbis.gear.config;

import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig.*;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GearBalanceConfig and its nested configuration records.
 */
class GearBalanceConfigTest {

    // =========================================================================
    // RARITY CONFIG TESTS
    // =========================================================================

    @Nested
    @DisplayName("RarityConfig")
    class RarityConfigTests {

        @Test
        @DisplayName("Valid config creates successfully")
        void validConfig_createsSuccessfully() {
            RarityConfig config = new RarityConfig(0.8, 3, 1, 2, 1, 2, 15.0);

            assertEquals(0.8, config.statMultiplier());
            assertEquals(3, config.maxModifiers());
            assertEquals(1, config.minPrefixes());
            assertEquals(2, config.maxPrefixes());
            assertEquals(1, config.minSuffixes());
            assertEquals(2, config.maxSuffixes());
            assertEquals(15.0, config.dropWeight());
            assertEquals(0.0, config.minRollPercentile());
        }

        @Test
        @DisplayName("Valid config with min roll percentile")
        void validConfigWithMinRoll_createsSuccessfully() {
            RarityConfig config = new RarityConfig(1.2, 4, 2, 2, 2, 2, 0.1, 0.75);

            assertEquals(0.75, config.minRollPercentile());
        }

        @Test
        @DisplayName("Zero stat multiplier throws exception")
        void zeroStatMultiplier_throwsException() {
            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new RarityConfig(0, 3, 1, 2, 1, 2, 15.0)
            );
            assertTrue(ex.getMessage().contains("statMultiplier must be positive"));
        }

        @Test
        @DisplayName("Negative stat multiplier throws exception")
        void negativeStatMultiplier_throwsException() {
            assertThrows(
                IllegalArgumentException.class,
                () -> new RarityConfig(-0.5, 3, 1, 2, 1, 2, 15.0)
            );
        }

        @Test
        @DisplayName("Invalid max modifiers (0) throws exception")
        void invalidMaxModifiers_zero_throwsException() {
            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new RarityConfig(0.8, 0, 1, 2, 1, 2, 15.0)
            );
            assertTrue(ex.getMessage().contains("maxModifiers must be 1-6"));
        }

        @Test
        @DisplayName("Invalid max modifiers (7) throws exception")
        void invalidMaxModifiers_seven_throwsException() {
            assertThrows(
                IllegalArgumentException.class,
                () -> new RarityConfig(0.8, 7, 1, 2, 1, 2, 15.0)
            );
        }

        @Test
        @DisplayName("Invalid prefix range (min > max) throws exception")
        void invalidPrefixRange_minGreaterThanMax_throwsException() {
            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new RarityConfig(0.8, 3, 2, 1, 1, 2, 15.0)
            );
            assertTrue(ex.getMessage().contains("Invalid prefix range"));
        }

        @Test
        @DisplayName("Invalid suffix range (max > 3) throws exception")
        void invalidSuffixRange_maxTooHigh_throwsException() {
            assertThrows(
                IllegalArgumentException.class,
                () -> new RarityConfig(0.8, 3, 1, 2, 1, 4, 15.0)
            );
        }

        @Test
        @DisplayName("Negative drop weight throws exception")
        void negativeDropWeight_throwsException() {
            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new RarityConfig(0.8, 3, 1, 2, 1, 2, -10)
            );
            assertTrue(ex.getMessage().contains("dropWeight"));
        }

        @Test
        @DisplayName("Min roll percentile > 1 throws exception")
        void minRollPercentile_greaterThan1_throwsException() {
            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new RarityConfig(0.8, 3, 1, 2, 1, 2, 15.0, 1.5)
            );
            assertTrue(ex.getMessage().contains("minRollPercentile must be 0-1"));
        }

        @Test
        @DisplayName("Negative min roll percentile throws exception")
        void negativeMinRollPercentile_throwsException() {
            assertThrows(
                IllegalArgumentException.class,
                () -> new RarityConfig(0.8, 3, 1, 2, 1, 2, 15.0, -0.1)
            );
        }
    }

    // =========================================================================
    // QUALITY CONFIG TESTS
    // =========================================================================

    @Nested
    @DisplayName("QualityConfig")
    class QualityConfigTests {

        private QualityDropDistribution validDistribution() {
            return new QualityDropDistribution(0.15, 0.25, 0.10, 0.30, 0.195, 0.005);
        }

        @Test
        @DisplayName("Valid quality config creates successfully")
        void validConfig_createsSuccessfully() {
            QualityConfig config = new QualityConfig(50, 1, 100, 101, validDistribution());

            assertEquals(50, config.baseline());
            assertEquals(1, config.min());
            assertEquals(100, config.max());
            assertEquals(101, config.perfect());
        }

        @Test
        @DisplayName("Quality multiplier at baseline returns 1.0")
        void qualityMultiplier_baseline50_returns1() {
            QualityConfig config = new QualityConfig(50, 1, 100, 101, validDistribution());

            assertEquals(1.0, config.multiplier(50));
        }

        @Test
        @DisplayName("Quality multiplier at 100 returns 1.5")
        void qualityMultiplier_quality100_returns15() {
            QualityConfig config = new QualityConfig(50, 1, 100, 101, validDistribution());

            assertEquals(1.5, config.multiplier(100));
        }

        @Test
        @DisplayName("Quality multiplier at 25 returns 0.75")
        void qualityMultiplier_quality25_returns075() {
            QualityConfig config = new QualityConfig(50, 1, 100, 101, validDistribution());

            assertEquals(0.75, config.multiplier(25));
        }

        @Test
        @DisplayName("Zero baseline throws exception")
        void zeroBaseline_throwsException() {
            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new QualityConfig(0, 1, 100, 101, validDistribution())
            );
            assertTrue(ex.getMessage().contains("baseline must be positive"));
        }

        @Test
        @DisplayName("Invalid min/max range throws exception")
        void invalidMinMaxRange_throwsException() {
            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new QualityConfig(50, 100, 50, 101, validDistribution())
            );
            assertTrue(ex.getMessage().contains("Invalid min/max range"));
        }

        @Test
        @DisplayName("Perfect <= max throws exception")
        void perfectNotGreaterThanMax_throwsException() {
            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new QualityConfig(50, 1, 100, 100, validDistribution())
            );
            assertTrue(ex.getMessage().contains("perfect must be > max"));
        }

        @Test
        @DisplayName("Null distribution throws exception")
        void nullDistribution_throwsException() {
            assertThrows(
                NullPointerException.class,
                () -> new QualityConfig(50, 1, 100, 101, null)
            );
        }
    }

    // =========================================================================
    // QUALITY DROP DISTRIBUTION TESTS
    // =========================================================================

    @Nested
    @DisplayName("QualityDropDistribution")
    class QualityDropDistributionTests {

        @Test
        @DisplayName("Valid distribution sums to 1.0")
        void validDistribution_createsSuccessfully() {
            QualityDropDistribution dist = new QualityDropDistribution(
                0.15, 0.25, 0.10, 0.30, 0.195, 0.005
            );

            double sum = dist.poor() + dist.belowAverage() + dist.normal() +
                         dist.aboveAverage() + dist.excellent() + dist.perfect();
            assertEquals(1.0, sum, 0.001);
        }

        @Test
        @DisplayName("Distribution not summing to 1.0 throws exception")
        void distributionNotOne_throwsException() {
            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new QualityDropDistribution(0.5, 0.25, 0.10, 0.30, 0.195, 0.005)
            );
            assertTrue(ex.getMessage().contains("sum to 1.0"));
        }

        @Test
        @DisplayName("Negative probability throws exception")
        void negativeProbability_throwsException() {
            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new QualityDropDistribution(-0.1, 0.35, 0.10, 0.30, 0.30, 0.05)
            );
            assertTrue(ex.getMessage().contains("non-negative"));
        }
    }

    // =========================================================================
    // ATTRIBUTE REQUIREMENTS CONFIG TESTS
    // =========================================================================

    @Nested
    @DisplayName("AttributeRequirementsConfig")
    class AttributeRequirementsConfigTests {

        private Map<GearRarity, Double> validMultipliers() {
            Map<GearRarity, Double> multipliers = new EnumMap<>(GearRarity.class);
            multipliers.put(GearRarity.COMMON, 0.1);
            multipliers.put(GearRarity.UNCOMMON, 0.25);
            multipliers.put(GearRarity.RARE, 0.5);
            multipliers.put(GearRarity.EPIC, 0.75);
            multipliers.put(GearRarity.LEGENDARY, 0.9);
            multipliers.put(GearRarity.MYTHIC, 1.0);
            multipliers.put(GearRarity.UNIQUE, 1.0);
            return multipliers;
        }

        @Test
        @DisplayName("Valid config creates successfully")
        void validConfig_createsSuccessfully() {
            AttributeRequirementsConfig config = new AttributeRequirementsConfig(
                0.5, 5, validMultipliers()
            );

            assertEquals(0.5, config.levelToBaseRatio());
            assertEquals(5, config.minItemLevelForRequirements());
        }

        @Test
        @DisplayName("Calculate requirement at level 50 Epic")
        void calculateRequirement_level50Epic_returns18() {
            AttributeRequirementsConfig config = new AttributeRequirementsConfig(
                0.5, 5, validMultipliers()
            );

            // 50 * 0.5 * 0.75 = 18.75 -> floor = 18
            assertEquals(18, config.calculateRequirement(50, GearRarity.EPIC));
        }

        @Test
        @DisplayName("Calculate requirement below min level returns 0")
        void calculateRequirement_belowMinLevel_returns0() {
            AttributeRequirementsConfig config = new AttributeRequirementsConfig(
                0.5, 5, validMultipliers()
            );

            assertEquals(0, config.calculateRequirement(3, GearRarity.MYTHIC));
        }

        @Test
        @DisplayName("Calculate requirement at level 100 Mythic")
        void calculateRequirement_level100Mythic_returns50() {
            AttributeRequirementsConfig config = new AttributeRequirementsConfig(
                0.5, 5, validMultipliers()
            );

            // 100 * 0.5 * 1.0 = 50
            assertEquals(50, config.calculateRequirement(100, GearRarity.MYTHIC));
        }

        @Test
        @DisplayName("Level to base ratio > 1 throws exception")
        void levelToBaseRatioGreaterThan1_throwsException() {
            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new AttributeRequirementsConfig(1.5, 5, validMultipliers())
            );
            assertTrue(ex.getMessage().contains("levelToBaseRatio must be in (0, 1]"));
        }

        @Test
        @DisplayName("Negative min item level throws exception")
        void negativeMinItemLevel_throwsException() {
            assertThrows(
                IllegalArgumentException.class,
                () -> new AttributeRequirementsConfig(0.5, -1, validMultipliers())
            );
        }

        @Test
        @DisplayName("Missing rarity multiplier throws exception")
        void missingRarityMultiplier_throwsException() {
            Map<GearRarity, Double> incomplete = new EnumMap<>(GearRarity.class);
            incomplete.put(GearRarity.COMMON, 0.1);
            // Missing other rarities

            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new AttributeRequirementsConfig(0.5, 5, incomplete)
            );
            assertTrue(ex.getMessage().contains("Missing rarity multiplier"));
        }

        @Test
        @DisplayName("Rarity multiplier > 1 throws exception")
        void rarityMultiplierGreaterThan1_throwsException() {
            Map<GearRarity, Double> invalid = validMultipliers();
            invalid.put(GearRarity.MYTHIC, 1.5);

            assertThrows(
                IllegalArgumentException.class,
                () -> new AttributeRequirementsConfig(0.5, 5, invalid)
            );
        }

        @Test
        @DisplayName("Rarity multipliers map is immutable")
        void rarityMultipliers_isImmutable() {
            AttributeRequirementsConfig config = new AttributeRequirementsConfig(
                0.5, 5, validMultipliers()
            );

            assertThrows(
                UnsupportedOperationException.class,
                () -> config.rarityMultipliers().put(GearRarity.COMMON, 0.5)
            );
        }
    }

    // =========================================================================
    // DISTANCE SCALING CONFIG TESTS
    // =========================================================================

    @Nested
    @DisplayName("DistanceScalingConfig")
    class DistanceScalingConfigTests {

        @Test
        @DisplayName("Valid config creates successfully")
        void validConfig_createsSuccessfully() {
            DistanceScalingConfig config = new DistanceScalingConfig(true, 100, 50);

            assertTrue(config.enabled());
            assertEquals(100, config.blocksPerPercent());
            assertEquals(50, config.maxBonus());
        }

        @Test
        @DisplayName("Calculate bonus at 1000 blocks returns 10%")
        void calculateBonus_1000Blocks_returns10() {
            DistanceScalingConfig config = new DistanceScalingConfig(true, 100, 50);

            assertEquals(10.0, config.calculateBonus(1000));
        }

        @Test
        @DisplayName("Calculate bonus caps at max")
        void calculateBonus_10000Blocks_capsAt50() {
            DistanceScalingConfig config = new DistanceScalingConfig(true, 100, 50);

            assertEquals(50.0, config.calculateBonus(10000));
        }

        @Test
        @DisplayName("Disabled scaling returns 0")
        void calculateBonus_disabled_returns0() {
            DistanceScalingConfig config = new DistanceScalingConfig(false, 100, 50);

            assertEquals(0.0, config.calculateBonus(10000));
        }

        @Test
        @DisplayName("Zero blocks per percent throws exception")
        void zeroBlocksPerPercent_throwsException() {
            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new DistanceScalingConfig(true, 0, 50)
            );
            assertTrue(ex.getMessage().contains("blocksPerPercent must be positive"));
        }

        @Test
        @DisplayName("Negative max bonus throws exception")
        void negativeMaxBonus_throwsException() {
            assertThrows(
                IllegalArgumentException.class,
                () -> new DistanceScalingConfig(true, 100, -10)
            );
        }
    }

    // =========================================================================
    // MOB BONUS CONFIG TESTS
    // =========================================================================

    @Nested
    @DisplayName("MobBonusConfig")
    class MobBonusConfigTests {

        @Test
        @DisplayName("Valid config creates successfully")
        void validConfig_createsSuccessfully() {
            MobBonusConfig config = new MobBonusConfig(0.5, 0.25);

            assertEquals(0.5, config.quantityBonus());
            assertEquals(0.25, config.rarityBonus());
        }

        @Test
        @DisplayName("NONE constant has zero bonuses")
        void noneConstant_hasZeroBonuses() {
            assertEquals(0, MobBonusConfig.NONE.quantityBonus());
            assertEquals(0, MobBonusConfig.NONE.rarityBonus());
        }

        @Test
        @DisplayName("Quantity bonus < -1 throws exception")
        void quantityBonusLessThanNegative1_throwsException() {
            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new MobBonusConfig(-1.5, 0)
            );
            assertTrue(ex.getMessage().contains("quantityBonus"));
        }

        @Test
        @DisplayName("Rarity bonus < -1 throws exception")
        void rarityBonusLessThanNegative1_throwsException() {
            assertThrows(
                IllegalArgumentException.class,
                () -> new MobBonusConfig(0, -1.5)
            );
        }
    }

    // =========================================================================
    // STONE DROP CONFIG TESTS
    // =========================================================================

    @Nested
    @DisplayName("StoneDropConfig")
    class StoneDropConfigTests {

        @Test
        @DisplayName("Valid config creates successfully")
        void validConfig_createsSuccessfully() {
            StoneDropConfig config = new StoneDropConfig(0.05, Map.of(
                "chaos_stone", 80,
                "corruption_stone", 8
            ));

            assertEquals(0.05, config.baseDropChance());
        }

        @Test
        @DisplayName("Stone weight lookup returns correct value")
        void stoneWeight_existingStone_returnsWeight() {
            StoneDropConfig config = new StoneDropConfig(0.05, Map.of(
                "chaos_stone", 80,
                "corruption_stone", 8
            ));

            assertEquals(80, config.stoneWeight("chaos_stone"));
        }

        @Test
        @DisplayName("Stone weight lookup is case insensitive")
        void stoneWeight_caseInsensitive() {
            StoneDropConfig config = new StoneDropConfig(0.05, Map.of(
                "chaos_stone", 80
            ));

            assertEquals(80, config.stoneWeight("CHAOS_STONE"));
        }

        @Test
        @DisplayName("Unknown stone returns 0")
        void stoneWeight_unknown_returns0() {
            StoneDropConfig config = new StoneDropConfig(0.05, Map.of(
                "chaos_stone", 80
            ));

            assertEquals(0, config.stoneWeight("nonexistent"));
        }

        @Test
        @DisplayName("Total weight is sum of all weights")
        void totalWeight_sumOfAllWeights() {
            StoneDropConfig config = new StoneDropConfig(0.05, Map.of(
                "chaos_stone", 80,
                "corruption_stone", 8,
                "quality_stone", 25
            ));

            assertEquals(113, config.totalWeight());
        }

        @Test
        @DisplayName("Base drop chance > 1 throws exception")
        void baseDropChanceGreaterThan1_throwsException() {
            assertThrows(
                IllegalArgumentException.class,
                () -> new StoneDropConfig(1.5, Map.of())
            );
        }

        @Test
        @DisplayName("Negative stone weight throws exception")
        void negativeStoneWeight_throwsException() {
            assertThrows(
                IllegalArgumentException.class,
                () -> new StoneDropConfig(0.05, Map.of("bad_stone", -10))
            );
        }
    }

    // =========================================================================
    // MODIFIER SCALING CONFIG TESTS
    // =========================================================================

    @Nested
    @DisplayName("ModifierScalingConfig")
    class ModifierScalingConfigTests {

        @Test
        @DisplayName("Valid config creates successfully")
        void validConfig_createsSuccessfully() {
            ModifierScalingConfig config = new ModifierScalingConfig(0.02, 0.3, Map.of(
                "common", 50,
                "rare", 10
            ));

            assertEquals(0.02, config.globalScalePerLevel());
            assertEquals(0.3, config.rollVariance());
        }

        @Test
        @DisplayName("Category weight lookup returns correct value")
        void categoryWeight_existingCategory_returnsWeight() {
            ModifierScalingConfig config = new ModifierScalingConfig(0.02, 0.3, Map.of(
                "common", 50,
                "rare", 10
            ));

            assertEquals(50, config.categoryWeight("common"));
        }

        @Test
        @DisplayName("Unknown category returns default 50")
        void categoryWeight_unknown_returns50() {
            ModifierScalingConfig config = new ModifierScalingConfig(0.02, 0.3, Map.of());

            assertEquals(50, config.categoryWeight("unknown"));
        }

        @Test
        @DisplayName("Negative global scale throws exception")
        void negativeGlobalScale_throwsException() {
            assertThrows(
                IllegalArgumentException.class,
                () -> new ModifierScalingConfig(-0.02, 0.3, Map.of())
            );
        }

        @Test
        @DisplayName("Roll variance > 1 throws exception")
        void rollVarianceGreaterThan1_throwsException() {
            assertThrows(
                IllegalArgumentException.class,
                () -> new ModifierScalingConfig(0.02, 1.5, Map.of())
            );
        }
    }

    // =========================================================================
    // LOOT CONFIG TESTS
    // =========================================================================

    @Nested
    @DisplayName("LootConfig")
    class LootConfigTests {

        @Test
        @DisplayName("Valid config creates successfully")
        void validConfig_createsSuccessfully() {
            LootConfig config = new LootConfig(
                0.05,
                0.5,
                new DistanceScalingConfig(true, 100, 50),
                Map.of(
                    "normal", new MobBonusConfig(0, 0),
                    "boss", new MobBonusConfig(1.0, 0.5)
                )
            );

            assertEquals(0.05, config.baseDropChance());
            assertEquals(0.5, config.luckToRarityPercent());
        }

        @Test
        @DisplayName("Mob bonus lookup returns correct config")
        void mobBonus_existingType_returnsConfig() {
            LootConfig config = new LootConfig(
                0.05,
                0.5,
                new DistanceScalingConfig(true, 100, 50),
                Map.of(
                    "normal", new MobBonusConfig(0, 0),
                    "boss", new MobBonusConfig(1.0, 0.5)
                )
            );

            assertEquals(1.0, config.mobBonus("boss").quantityBonus());
        }

        @Test
        @DisplayName("Unknown mob type falls back to normal")
        void mobBonus_unknownType_fallsBackToNormal() {
            LootConfig config = new LootConfig(
                0.05,
                0.5,
                new DistanceScalingConfig(true, 100, 50),
                Map.of(
                    "normal", new MobBonusConfig(0, 0),
                    "boss", new MobBonusConfig(1.0, 0.5)
                )
            );

            assertEquals(0, config.mobBonus("unknown").quantityBonus());
        }

        @Test
        @DisplayName("Base drop chance > 1 throws exception")
        void baseDropChanceGreaterThan1_throwsException() {
            assertThrows(
                IllegalArgumentException.class,
                () -> new LootConfig(
                    1.5,
                    0.5,
                    new DistanceScalingConfig(true, 100, 50),
                    Map.of()
                )
            );
        }

        @Test
        @DisplayName("Negative luck to rarity throws exception")
        void negativeLuckToRarity_throwsException() {
            assertThrows(
                IllegalArgumentException.class,
                () -> new LootConfig(
                    0.05,
                    -0.5,
                    new DistanceScalingConfig(true, 100, 50),
                    Map.of()
                )
            );
        }

        @Test
        @DisplayName("Null distance scaling throws exception")
        void nullDistanceScaling_throwsException() {
            assertThrows(
                NullPointerException.class,
                () -> new LootConfig(0.05, 0.5, null, Map.of())
            );
        }
    }
}
