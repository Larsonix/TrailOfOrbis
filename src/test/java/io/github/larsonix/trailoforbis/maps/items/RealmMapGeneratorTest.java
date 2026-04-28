package io.github.larsonix.trailoforbis.maps.items;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.maps.config.RealmsConfig;
import io.github.larsonix.trailoforbis.maps.config.RealmsConfig.BiomeSettings;
import io.github.larsonix.trailoforbis.maps.config.RealmModifierConfig;
import io.github.larsonix.trailoforbis.maps.core.RealmBiomeType;
import io.github.larsonix.trailoforbis.maps.core.RealmLayoutShape;
import io.github.larsonix.trailoforbis.maps.core.RealmLayoutSize;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifier;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifierType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for RealmMapGenerator.
 */
@ExtendWith(MockitoExtension.class)
class RealmMapGeneratorTest {

    @Mock
    private ItemStack mockItemStack;

    @Mock
    private ItemStack mockResultStack;

    private RealmsConfig realmsConfig;
    private RealmModifierConfig modifierConfig;

    @BeforeEach
    void setUp() {
        realmsConfig = createTestRealmsConfig();
        modifierConfig = createTestModifierConfig();

        // Setup ItemStack mocking
        lenient().when(mockItemStack.withMetadata(anyString(), any(Codec.class), any()))
            .thenReturn(mockResultStack);
        lenient().when(mockResultStack.withMetadata(anyString(), any(Codec.class), any()))
            .thenReturn(mockResultStack);
    }

    // =========================================================================
    // TEST CONFIG FACTORIES
    // =========================================================================

    private RealmsConfig createTestRealmsConfig() {
        RealmsConfig config = new RealmsConfig();
        config.setMaxLevel(10000);
        config.setStartingLevel(1);

        // Enable all biomes and sizes
        config.setEnabledBiomes(new HashSet<>(Arrays.asList(RealmBiomeType.values())));
        config.setEnabledSizes(new HashSet<>(Arrays.asList(RealmLayoutSize.values())));

        return config;
    }

    private RealmModifierConfig createTestModifierConfig() {
        // Use default config which already has all modifiers configured
        return new RealmModifierConfig();
    }

    // =========================================================================
    // BASIC GENERATION TESTS
    // =========================================================================

    @Nested
    @DisplayName("Basic Generation")
    class BasicGeneration {

        @Test
        @DisplayName("generate returns non-null RealmMapData")
        void generateReturnsNonNull() {
            RealmMapGenerator generator = new RealmMapGenerator(realmsConfig, modifierConfig);
            RealmMapData data = generator.generate(50);
            assertNotNull(data);
        }

        @Test
        @DisplayName("generate uses correct level")
        void generateUsesCorrectLevel() {
            RealmMapGenerator generator = new RealmMapGenerator(realmsConfig, modifierConfig);
            RealmMapData data = generator.generate(75);
            assertEquals(75, data.level());
        }

        @Test
        @DisplayName("generate clamps level to max")
        void generateClampsLevelToMax() {
            RealmMapGenerator generator = new RealmMapGenerator(realmsConfig, modifierConfig);
            RealmMapData data = generator.generate(99999);
            assertEquals(10000, data.level());
        }

        @Test
        @DisplayName("generate clamps level to min")
        void generateClampsLevelToMin() {
            RealmMapGenerator generator = new RealmMapGenerator(realmsConfig, modifierConfig);
            RealmMapData data = generator.generate(-10);
            assertEquals(1, data.level());
        }

        @Test
        @DisplayName("generate returns valid rarity")
        void generateReturnsValidRarity() {
            RealmMapGenerator generator = new RealmMapGenerator(realmsConfig, modifierConfig);
            RealmMapData data = generator.generate(50);
            assertNotNull(data.rarity());
        }

        @Test
        @DisplayName("generate returns valid quality (1-101)")
        void generateReturnsValidQuality() {
            RealmMapGenerator generator = new RealmMapGenerator(realmsConfig, modifierConfig);
            for (int i = 0; i < 100; i++) {
                RealmMapData data = generator.generate(50);
                assertTrue(data.quality() >= 1 && data.quality() <= 101,
                    "Quality should be 1-101, was: " + data.quality());
            }
        }

        @Test
        @DisplayName("generate returns valid biome")
        void generateReturnsValidBiome() {
            RealmMapGenerator generator = new RealmMapGenerator(realmsConfig, modifierConfig);
            RealmMapData data = generator.generate(50);
            assertNotNull(data.biome());
            assertTrue(realmsConfig.getEnabledBiomes().contains(data.biome()));
        }

        @Test
        @DisplayName("generate returns valid size")
        void generateReturnsValidSize() {
            RealmMapGenerator generator = new RealmMapGenerator(realmsConfig, modifierConfig);
            RealmMapData data = generator.generate(50);
            assertNotNull(data.size());
            assertTrue(realmsConfig.getEnabledSizes().contains(data.size()));
        }

        @Test
        @DisplayName("generate returns valid shape")
        void generateReturnsValidShape() {
            RealmMapGenerator generator = new RealmMapGenerator(realmsConfig, modifierConfig);
            RealmMapData data = generator.generate(50);
            assertNotNull(data.shape());
        }

        @Test
        @DisplayName("generated maps start unidentified")
        void generatedMapsStartUnidentified() {
            RealmMapGenerator generator = new RealmMapGenerator(realmsConfig, modifierConfig);
            RealmMapData data = generator.generate(50);
            assertFalse(data.identified());
        }

        @Test
        @DisplayName("generated maps start uncorrupted")
        void generatedMapsStartUncorrupted() {
            RealmMapGenerator generator = new RealmMapGenerator(realmsConfig, modifierConfig);
            RealmMapData data = generator.generate(50);
            assertFalse(data.corrupted());
        }
    }

    // =========================================================================
    // FORCED RARITY TESTS
    // =========================================================================

    @Nested
    @DisplayName("Forced Rarity")
    class ForcedRarity {

        @Test
        @DisplayName("generate with forced COMMON rarity")
        void generateWithCommonRarity() {
            RealmMapGenerator generator = new RealmMapGenerator(realmsConfig, modifierConfig);
            RealmMapData data = generator.generate(50, GearRarity.COMMON);
            assertEquals(GearRarity.COMMON, data.rarity());
        }

        @Test
        @DisplayName("generate with forced EPIC rarity")
        void generateWithEpicRarity() {
            RealmMapGenerator generator = new RealmMapGenerator(realmsConfig, modifierConfig);
            RealmMapData data = generator.generate(50, GearRarity.EPIC);
            assertEquals(GearRarity.EPIC, data.rarity());
        }

        @Test
        @DisplayName("generate with forced LEGENDARY rarity")
        void generateWithLegendaryRarity() {
            RealmMapGenerator generator = new RealmMapGenerator(realmsConfig, modifierConfig);
            RealmMapData data = generator.generate(50, GearRarity.LEGENDARY);
            assertEquals(GearRarity.LEGENDARY, data.rarity());
        }

        @Test
        @DisplayName("generate with forced MYTHIC rarity")
        void generateWithMythicRarity() {
            RealmMapGenerator generator = new RealmMapGenerator(realmsConfig, modifierConfig);
            RealmMapData data = generator.generate(50, GearRarity.MYTHIC);
            assertEquals(GearRarity.MYTHIC, data.rarity());
        }

        @Test
        @DisplayName("generate rejects null rarity")
        void generateRejectsNullRarity() {
            RealmMapGenerator generator = new RealmMapGenerator(realmsConfig, modifierConfig);
            assertThrows(NullPointerException.class, () -> generator.generate(50, (GearRarity) null));
        }
    }

    // =========================================================================
    // RARITY BONUS TESTS
    // =========================================================================

    @Nested
    @DisplayName("Rarity Bonus")
    class RarityBonus {

        @Test
        @DisplayName("higher rarity bonus shifts distribution toward rarer")
        void higherBonusShiftsTowardRarer() {
            int rareCountLowBonus = 0;
            int rareCountHighBonus = 0;

            for (int i = 0; i < 1000; i++) {
                RealmMapGenerator gen1 = new RealmMapGenerator(realmsConfig, modifierConfig, new Random(i));
                RealmMapGenerator gen2 = new RealmMapGenerator(realmsConfig, modifierConfig, new Random(i + 10000));

                GearRarity r1 = gen1.rollRarity(0.0);
                GearRarity r2 = gen2.rollRarity(2.0);

                if (r1.ordinal() >= GearRarity.RARE.ordinal()) rareCountLowBonus++;
                if (r2.ordinal() >= GearRarity.RARE.ordinal()) rareCountHighBonus++;
            }

            assertTrue(rareCountHighBonus > rareCountLowBonus,
                "High bonus should produce more rare+ items. Low: " + rareCountLowBonus + ", High: " + rareCountHighBonus);
        }

        @Test
        @DisplayName("rollRarity clamps negative bonus to 0")
        void rollRarityClampsNegativeBonus() {
            RealmMapGenerator generator = new RealmMapGenerator(realmsConfig, modifierConfig, new Random(42));
            // Should not throw
            GearRarity rarity = generator.rollRarity(-5.0);
            assertNotNull(rarity);
        }

        @Test
        @DisplayName("rollRarity clamps excessive bonus to 10")
        void rollRarityClampsExcessiveBonus() {
            RealmMapGenerator generator = new RealmMapGenerator(realmsConfig, modifierConfig, new Random(42));
            // Should not throw
            GearRarity rarity = generator.rollRarity(100.0);
            assertNotNull(rarity);
        }
    }

    // =========================================================================
    // QUALITY ROLLING TESTS
    // =========================================================================

    @Nested
    @DisplayName("Quality Rolling")
    class QualityRolling {

        @Test
        @DisplayName("rollQuality returns values in valid range")
        void rollQualityReturnsValidRange() {
            RealmMapGenerator generator = new RealmMapGenerator(realmsConfig, modifierConfig);
            for (int i = 0; i < 1000; i++) {
                int quality = generator.rollQuality();
                assertTrue(quality >= 1 && quality <= 101,
                    "Quality should be 1-101, was: " + quality);
            }
        }

        @Test
        @DisplayName("rollQuality can produce perfect quality (101)")
        void rollQualityCanProducePerfect() {
            // Perfect quality has 0.01% chance, so run many times
            boolean foundPerfect = false;
            for (int seed = 0; seed < 100000 && !foundPerfect; seed++) {
                RealmMapGenerator generator = new RealmMapGenerator(realmsConfig, modifierConfig, new Random(seed));
                if (generator.rollQuality() == 101) {
                    foundPerfect = true;
                }
            }
            // Note: This test may fail occasionally due to randomness, but with 100k tries
            // at 0.01% chance, we should find at least one
            assertTrue(foundPerfect, "Should find at least one perfect quality in 100k rolls");
        }

        @Test
        @DisplayName("rollQuality distribution favors middle values")
        void rollQualityFavorsMiddle() {
            RealmMapGenerator generator = new RealmMapGenerator(realmsConfig, modifierConfig, new Random(12345));
            int lowCount = 0, midCount = 0, highCount = 0;

            for (int i = 0; i < 10000; i++) {
                int quality = generator.rollQuality();
                if (quality <= 33) lowCount++;
                else if (quality <= 66) midCount++;
                else if (quality < 101) highCount++;
            }

            // Middle should have more than extremes (bell curve effect)
            assertTrue(midCount > lowCount && midCount > highCount,
                "Middle range should have most values. Low: " + lowCount + ", Mid: " + midCount + ", High: " + highCount);
        }
    }

    // =========================================================================
    // BIOME ROLLING TESTS
    // =========================================================================

    @Nested
    @DisplayName("Biome Rolling")
    class BiomeRolling {

        @Test
        @DisplayName("rollBiome returns enabled biome")
        void rollBiomeReturnsEnabled() {
            RealmMapGenerator generator = new RealmMapGenerator(realmsConfig, modifierConfig);
            for (int i = 0; i < 100; i++) {
                RealmBiomeType biome = generator.rollBiome(50);
                assertTrue(realmsConfig.getEnabledBiomes().contains(biome));
            }
        }

        @Test
        @DisplayName("rollBiome respects minimum level requirement")
        void rollBiomeRespectsMinLevel() {
            // Create config with VOID requiring level 500
            RealmsConfig restrictedConfig = new RealmsConfig();
            restrictedConfig.setMaxLevel(10000);
            restrictedConfig.setEnabledBiomes(new HashSet<>(Arrays.asList(
                RealmBiomeType.FOREST, RealmBiomeType.VOID)));

            // Set VOID to require level 500
            BiomeSettings voidSettings = new BiomeSettings();
            voidSettings.setMinLevel(500);
            restrictedConfig.setBiomeOverride(RealmBiomeType.VOID, voidSettings);

            RealmMapGenerator generator = new RealmMapGenerator(restrictedConfig, modifierConfig);

            // At level 10, should only get FOREST
            Set<RealmBiomeType> biomesAtLowLevel = new HashSet<>();
            for (int i = 0; i < 100; i++) {
                biomesAtLowLevel.add(generator.rollBiome(10));
            }
            assertEquals(Set.of(RealmBiomeType.FOREST), biomesAtLowLevel);

            // At level 1000, should get both
            Set<RealmBiomeType> biomesAtHighLevel = new HashSet<>();
            for (int i = 0; i < 100; i++) {
                biomesAtHighLevel.add(generator.rollBiome(1000));
            }
            assertTrue(biomesAtHighLevel.size() == 2, "Should have both biomes at high level");
        }

        @Test
        @DisplayName("rollBiome falls back to all enabled if none meet level")
        void rollBiomeFallsBackIfNoneMeetLevel() {
            // Create config where all biomes require high level
            RealmsConfig restrictedConfig = new RealmsConfig();
            restrictedConfig.setMaxLevel(10000);
            restrictedConfig.setEnabledBiomes(new HashSet<>(Set.of(RealmBiomeType.VOID)));

            BiomeSettings voidSettings = new BiomeSettings();
            voidSettings.setMinLevel(500);
            restrictedConfig.setBiomeOverride(RealmBiomeType.VOID, voidSettings);

            RealmMapGenerator generator = new RealmMapGenerator(restrictedConfig, modifierConfig);

            // At level 1, should still return VOID as fallback
            RealmBiomeType biome = generator.rollBiome(1);
            assertEquals(RealmBiomeType.VOID, biome);
        }
    }

    // =========================================================================
    // SIZE ROLLING TESTS
    // =========================================================================

    @Nested
    @DisplayName("Size Rolling")
    class SizeRolling {

        @Test
        @DisplayName("rollSize returns enabled size at high level")
        void rollSizeReturnsEnabled() {
            RealmMapGenerator generator = new RealmMapGenerator(realmsConfig, modifierConfig);
            for (int i = 0; i < 100; i++) {
                RealmLayoutSize size = generator.rollSize(100);
                assertTrue(realmsConfig.getEnabledSizes().contains(size));
            }
        }

        @Test
        @DisplayName("rollSize uses weighted distribution at high level")
        void rollSizeUsesWeightedDistribution() {
            RealmMapGenerator generator = new RealmMapGenerator(realmsConfig, modifierConfig, new Random(12345));
            Map<RealmLayoutSize, Integer> counts = new EnumMap<>(RealmLayoutSize.class);
            for (RealmLayoutSize size : RealmLayoutSize.values()) {
                counts.put(size, 0);
            }

            // Level 100 = all sizes fully ramped
            for (int i = 0; i < 10000; i++) {
                RealmLayoutSize size = generator.rollSize(100);
                counts.put(size, counts.get(size) + 1);
            }

            // SMALL (40%) should be most common, MASSIVE (5%) should be least common
            assertTrue(counts.get(RealmLayoutSize.SMALL) > counts.get(RealmLayoutSize.MASSIVE),
                "SMALL should be more common than MASSIVE");
            assertTrue(counts.get(RealmLayoutSize.MEDIUM) > counts.get(RealmLayoutSize.LARGE),
                "MEDIUM should be more common than LARGE");
        }

        @Test
        @DisplayName("rollSize respects level gating")
        void rollSizeRespectsLevelGating() {
            RealmMapGenerator generator = new RealmMapGenerator(realmsConfig, modifierConfig, new Random(42));

            // Level 1: only SMALL should be available (MEDIUM requires 10)
            Set<RealmLayoutSize> sizesAtLevel1 = new HashSet<>();
            for (int i = 0; i < 500; i++) {
                sizesAtLevel1.add(generator.rollSize(1));
            }
            assertEquals(Set.of(RealmLayoutSize.SMALL), sizesAtLevel1,
                "Level 1 should only get SMALL maps");

            // Level 15: SMALL and MEDIUM should be available (LARGE requires 25)
            Set<RealmLayoutSize> sizesAtLevel15 = new HashSet<>();
            for (int i = 0; i < 500; i++) {
                sizesAtLevel15.add(generator.rollSize(15));
            }
            assertEquals(Set.of(RealmLayoutSize.SMALL, RealmLayoutSize.MEDIUM), sizesAtLevel15,
                "Level 15 should get SMALL and MEDIUM maps");

            // Level 50+: all sizes should be available
            Set<RealmLayoutSize> sizesAtLevel50 = new HashSet<>();
            for (int i = 0; i < 2000; i++) {
                sizesAtLevel50.add(generator.rollSize(50));
            }
            assertEquals(Set.of(RealmLayoutSize.SMALL, RealmLayoutSize.MEDIUM,
                RealmLayoutSize.LARGE, RealmLayoutSize.MASSIVE), sizesAtLevel50,
                "Level 50 should get all map sizes");
        }

        @Test
        @DisplayName("rollSize ramp increases larger size chance with level")
        void rollSizeRampingIncreasesLargerSizeChance() {
            // At level 10, MEDIUM just unlocked (10% base weight)
            // At level 20, MEDIUM fully ramped (100% base weight)
            // The proportion of MEDIUM should increase with level
            RealmMapGenerator gen1 = new RealmMapGenerator(realmsConfig, modifierConfig, new Random(12345));
            RealmMapGenerator gen2 = new RealmMapGenerator(realmsConfig, modifierConfig, new Random(12345));

            int mediumCountAtLevel10 = 0;
            int mediumCountAtLevel20 = 0;
            int trials = 5000;

            for (int i = 0; i < trials; i++) {
                if (gen1.rollSize(10) == RealmLayoutSize.MEDIUM) mediumCountAtLevel10++;
                if (gen2.rollSize(20) == RealmLayoutSize.MEDIUM) mediumCountAtLevel20++;
            }

            assertTrue(mediumCountAtLevel20 > mediumCountAtLevel10,
                "MEDIUM should appear more often at level 20 than level 10. " +
                "At 10: " + mediumCountAtLevel10 + ", At 20: " + mediumCountAtLevel20);
        }

        @Test
        @DisplayName("rollSize with limited enabled sizes respects level gating")
        void rollSizeWithLimitedSizesRespectsLevelGating() {
            RealmsConfig limitedConfig = new RealmsConfig();
            limitedConfig.setMaxLevel(10000);
            limitedConfig.setEnabledBiomes(new HashSet<>(Arrays.asList(RealmBiomeType.values())));
            limitedConfig.setEnabledSizes(new HashSet<>(Set.of(RealmLayoutSize.SMALL, RealmLayoutSize.LARGE)));

            RealmMapGenerator generator = new RealmMapGenerator(limitedConfig, modifierConfig, new Random(12345));

            // Level 1: only SMALL (LARGE requires level 25)
            Set<RealmLayoutSize> sizesAtLevel1 = new HashSet<>();
            for (int i = 0; i < 200; i++) {
                sizesAtLevel1.add(generator.rollSize(1));
            }
            assertEquals(Set.of(RealmLayoutSize.SMALL), sizesAtLevel1);

            // Level 50: both SMALL and LARGE
            Set<RealmLayoutSize> sizesAtLevel50 = new HashSet<>();
            for (int i = 0; i < 200; i++) {
                sizesAtLevel50.add(generator.rollSize(50));
            }
            assertEquals(Set.of(RealmLayoutSize.SMALL, RealmLayoutSize.LARGE), sizesAtLevel50);
        }
    }

    // =========================================================================
    // ITEMSTACK GENERATION TESTS
    // =========================================================================

    @Nested
    @DisplayName("ItemStack Generation")
    class ItemStackGeneration {

        @Test
        @DisplayName("generateItem returns modified ItemStack")
        void generateItemReturnsModifiedStack() {
            RealmMapGenerator generator = new RealmMapGenerator(realmsConfig, modifierConfig);
            ItemStack result = generator.generateItem(mockItemStack, 50);
            assertNotNull(result);
        }

        @Test
        @DisplayName("generateItem calls writeMapData")
        void generateItemCallsWriteMapData() {
            RealmMapGenerator generator = new RealmMapGenerator(realmsConfig, modifierConfig);
            generator.generateItem(mockItemStack, 50);

            // Verify metadata was written (marker key and data key)
            verify(mockItemStack, atLeastOnce()).withMetadata(anyString(), any(Codec.class), any());
        }

        @Test
        @DisplayName("generateItem rejects null ItemStack")
        void generateItemRejectsNullItemStack() {
            RealmMapGenerator generator = new RealmMapGenerator(realmsConfig, modifierConfig);
            assertThrows(NullPointerException.class, () -> generator.generateItem(null, 50));
        }

        @Test
        @DisplayName("generateItem with rarity bonus")
        void generateItemWithRarityBonus() {
            RealmMapGenerator generator = new RealmMapGenerator(realmsConfig, modifierConfig);
            ItemStack result = generator.generateItem(mockItemStack, 50, 1.0);
            assertNotNull(result);
        }

        @Test
        @DisplayName("generateItem with forced rarity")
        void generateItemWithForcedRarity() {
            RealmMapGenerator generator = new RealmMapGenerator(realmsConfig, modifierConfig);
            ItemStack result = generator.generateItem(mockItemStack, 50, GearRarity.LEGENDARY);
            assertNotNull(result);
        }
    }

    // =========================================================================
    // BUILDER TESTS
    // =========================================================================

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("builder creates valid map data")
        void builderCreatesValidMapData() {
            RealmMapGenerator generator = new RealmMapGenerator(realmsConfig, modifierConfig);
            RealmMapData data = generator.builder()
                .level(100)
                .build();

            assertEquals(100, data.level());
        }

        @Test
        @DisplayName("builder with all properties")
        void builderWithAllProperties() {
            RealmMapGenerator generator = new RealmMapGenerator(realmsConfig, modifierConfig);
            RealmMapData data = generator.builder()
                .level(200)
                .rarity(GearRarity.EPIC)
                .quality(80)
                .biome(RealmBiomeType.VOLCANO)
                .size(RealmLayoutSize.LARGE)
                .shape(RealmLayoutShape.RECTANGULAR)
                .identified(true)
                .corrupted(true)
                .build();

            assertEquals(200, data.level());
            assertEquals(GearRarity.EPIC, data.rarity());
            assertEquals(80, data.quality());
            assertEquals(RealmBiomeType.VOLCANO, data.biome());
            assertEquals(RealmLayoutSize.LARGE, data.size());
            assertEquals(RealmLayoutShape.RECTANGULAR, data.shape());
            assertTrue(data.identified());
            assertTrue(data.corrupted());
        }

        @Test
        @DisplayName("builder with forced modifiers")
        void builderWithForcedModifiers() {
            RealmMapGenerator generator = new RealmMapGenerator(realmsConfig, modifierConfig);

            List<RealmModifier> forcedMods = List.of(
                new RealmModifier(RealmModifierType.MONSTER_HEALTH, 100, false),
                new RealmModifier(RealmModifierType.MONSTER_DAMAGE, 50, false)
            );

            RealmMapData data = generator.builder()
                .level(100)
                .rarity(GearRarity.RARE)
                .modifiers(forcedMods)
                .build();

            assertEquals(2, data.modifiers().size());
            assertEquals(RealmModifierType.MONSTER_HEALTH, data.modifiers().get(0).type());
            assertEquals(RealmModifierType.MONSTER_DAMAGE, data.modifiers().get(1).type());
        }

        @Test
        @DisplayName("builder requires level")
        void builderRequiresLevel() {
            RealmMapGenerator generator = new RealmMapGenerator(realmsConfig, modifierConfig);
            assertThrows(IllegalStateException.class, () -> generator.builder().build());
        }

        @Test
        @DisplayName("builder rarityBonus is ignored when rarity is set")
        void builderRarityBonusIgnoredWhenRaritySet() {
            RealmMapGenerator generator = new RealmMapGenerator(realmsConfig, modifierConfig);
            RealmMapData data = generator.builder()
                .level(50)
                .rarity(GearRarity.COMMON)
                .rarityBonus(10.0) // Should be ignored
                .build();

            assertEquals(GearRarity.COMMON, data.rarity());
        }

        @Test
        @DisplayName("builder can build ItemStack")
        void builderCanBuildItemStack() {
            RealmMapGenerator generator = new RealmMapGenerator(realmsConfig, modifierConfig);
            ItemStack result = generator.builder()
                .level(50)
                .rarity(GearRarity.EPIC)
                .build(mockItemStack);

            assertNotNull(result);
        }

        @Test
        @DisplayName("builder build ItemStack rejects null")
        void builderBuildItemStackRejectsNull() {
            RealmMapGenerator generator = new RealmMapGenerator(realmsConfig, modifierConfig);
            RealmMapGenerator.GenerationBuilder builder = generator.builder().level(50);
            assertThrows(NullPointerException.class, () -> builder.build(null));
        }
    }

    // =========================================================================
    // ACCESSOR TESTS
    // =========================================================================

    @Nested
    @DisplayName("Accessors")
    class Accessors {

        @Test
        @DisplayName("getRealmsConfig returns config")
        void getRealmsConfigReturnsConfig() {
            RealmMapGenerator generator = new RealmMapGenerator(realmsConfig, modifierConfig);
            assertSame(realmsConfig, generator.getRealmsConfig());
        }

        @Test
        @DisplayName("getModifierRoller returns roller")
        void getModifierRollerReturnsRoller() {
            RealmMapGenerator generator = new RealmMapGenerator(realmsConfig, modifierConfig);
            assertNotNull(generator.getModifierRoller());
        }

        @Test
        @DisplayName("getEnabledBiomes returns unmodifiable list")
        void getEnabledBiomesReturnsUnmodifiable() {
            RealmMapGenerator generator = new RealmMapGenerator(realmsConfig, modifierConfig);
            List<RealmBiomeType> biomes = generator.getEnabledBiomes();
            assertThrows(UnsupportedOperationException.class, () -> biomes.add(RealmBiomeType.VOID));
        }

        @Test
        @DisplayName("getEnabledSizes returns unmodifiable list")
        void getEnabledSizesReturnsUnmodifiable() {
            RealmMapGenerator generator = new RealmMapGenerator(realmsConfig, modifierConfig);
            List<RealmLayoutSize> sizes = generator.getEnabledSizes();
            assertThrows(UnsupportedOperationException.class, () -> sizes.add(RealmLayoutSize.MASSIVE));
        }
    }

    // =========================================================================
    // EDGE CASE TESTS
    // =========================================================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("constructor rejects null realmsConfig")
        void constructorRejectsNullRealmsConfig() {
            assertThrows(NullPointerException.class,
                () -> new RealmMapGenerator(null, modifierConfig));
        }

        @Test
        @DisplayName("constructor rejects null modifierConfig")
        void constructorRejectsNullModifierConfig() {
            assertThrows(NullPointerException.class,
                () -> new RealmMapGenerator(realmsConfig, null));
        }

        @Test
        @DisplayName("constructor handles empty enabled biomes")
        void constructorHandlesEmptyEnabledBiomes() {
            RealmsConfig emptyConfig = new RealmsConfig();
            emptyConfig.setMaxLevel(10000);
            emptyConfig.setEnabledBiomes(new HashSet<>()); // Empty
            emptyConfig.setEnabledSizes(new HashSet<>(Arrays.asList(RealmLayoutSize.values())));

            // Should not throw, should use defaults
            RealmMapGenerator generator = new RealmMapGenerator(emptyConfig, modifierConfig);
            assertFalse(generator.getEnabledBiomes().isEmpty());
        }

        @Test
        @DisplayName("constructor handles empty enabled sizes")
        void constructorHandlesEmptyEnabledSizes() {
            RealmsConfig emptyConfig = new RealmsConfig();
            emptyConfig.setMaxLevel(10000);
            emptyConfig.setEnabledBiomes(new HashSet<>(Arrays.asList(RealmBiomeType.values())));
            emptyConfig.setEnabledSizes(new HashSet<>()); // Empty

            // Should not throw, should use defaults
            RealmMapGenerator generator = new RealmMapGenerator(emptyConfig, modifierConfig);
            assertFalse(generator.getEnabledSizes().isEmpty());
        }

        @Test
        @DisplayName("generate with full customization")
        void generateWithFullCustomization() {
            RealmMapGenerator generator = new RealmMapGenerator(realmsConfig, modifierConfig);
            RealmMapData data = generator.generate(
                500,
                GearRarity.LEGENDARY,
                RealmBiomeType.CORRUPTED,
                RealmLayoutSize.MASSIVE
            );

            assertEquals(500, data.level());
            assertEquals(GearRarity.LEGENDARY, data.rarity());
            assertEquals(RealmBiomeType.CORRUPTED, data.biome());
            assertEquals(RealmLayoutSize.MASSIVE, data.size());
        }

        @Test
        @DisplayName("deterministic generation with seeded random")
        void deterministicGenerationWithSeededRandom() {
            RealmMapGenerator gen1 = new RealmMapGenerator(realmsConfig, modifierConfig, new Random(42));
            RealmMapGenerator gen2 = new RealmMapGenerator(realmsConfig, modifierConfig, new Random(42));

            RealmMapData data1 = gen1.generate(50);
            RealmMapData data2 = gen2.generate(50);

            assertEquals(data1.level(), data2.level());
            assertEquals(data1.rarity(), data2.rarity());
            assertEquals(data1.quality(), data2.quality());
            assertEquals(data1.biome(), data2.biome());
            assertEquals(data1.size(), data2.size());
            assertEquals(data1.shape(), data2.shape());
        }
    }
}
