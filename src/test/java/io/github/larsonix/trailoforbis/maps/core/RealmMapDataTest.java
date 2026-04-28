package io.github.larsonix.trailoforbis.maps.core;

import com.hypixel.hytale.codec.ExtraInfo;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifier;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifierType;
import org.bson.BsonDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@DisplayName("RealmMapData")
class RealmMapDataTest {

    @Nested
    @DisplayName("Constructor")
    class Constructor {

        @Test
        @DisplayName("Should create with valid values")
        void createValid() {
            RealmMapData map = new RealmMapData(
                50, GearRarity.RARE, 75,
                RealmBiomeType.FOREST, RealmLayoutSize.MEDIUM, RealmLayoutShape.CIRCULAR,
                List.of(), // prefixes
                List.of(), // suffixes
                false, true,
                null // instanceId
            );

            assertEquals(50, map.level());
            assertEquals(GearRarity.RARE, map.rarity());
            assertEquals(75, map.quality());
            assertEquals(RealmBiomeType.FOREST, map.biome());
            assertEquals(RealmLayoutSize.MEDIUM, map.size());
            assertFalse(map.corrupted());
            assertTrue(map.identified());
        }

        @Test
        @DisplayName("Should clamp level to valid range")
        void clampLevel() {
            RealmMapData tooLow = new RealmMapData(
                -10, GearRarity.COMMON, 50,
                RealmBiomeType.FOREST, RealmLayoutSize.SMALL, RealmLayoutShape.CIRCULAR,
                List.of(), // prefixes
                List.of(), // suffixes
                false, false,
                null // instanceId
            );
            assertEquals(1, tooLow.level());

            RealmMapData tooHigh = new RealmMapData(
                1_500_000, GearRarity.COMMON, 50,
                RealmBiomeType.FOREST, RealmLayoutSize.SMALL, RealmLayoutShape.CIRCULAR,
                List.of(), // prefixes
                List.of(), // suffixes
                false, false,
                null // instanceId
            );
            assertEquals(1_000_000, tooHigh.level());
        }

        @Test
        @DisplayName("Should clamp quality to valid range")
        void clampQuality() {
            RealmMapData tooLow = new RealmMapData(
                50, GearRarity.COMMON, -5,
                RealmBiomeType.FOREST, RealmLayoutSize.SMALL, RealmLayoutShape.CIRCULAR,
                List.of(), // prefixes
                List.of(), // suffixes
                false, false,
                null // instanceId
            );
            assertEquals(1, tooLow.quality());

            RealmMapData tooHigh = new RealmMapData(
                50, GearRarity.COMMON, 200,
                RealmBiomeType.FOREST, RealmLayoutSize.SMALL, RealmLayoutShape.CIRCULAR,
                List.of(), // prefixes
                List.of(), // suffixes
                false, false,
                null // instanceId
            );
            assertEquals(101, tooHigh.quality());
        }

        @Test
        @DisplayName("Should clamp Fortune's Compass bonus to valid range")
        void clampFortunesCompassBonus() {
            RealmMapData tooLow = RealmMapData.builder()
                .fortunesCompassBonus(-10)
                .build();
            RealmMapData tooHigh = RealmMapData.builder()
                .fortunesCompassBonus(999)
                .build();

            assertEquals(0, tooLow.fortunesCompassBonus());
            assertEquals(20, tooHigh.fortunesCompassBonus());
        }

        @Test
        @DisplayName("Should enforce max modifiers by rarity")
        void enforceMaxModifiers() {
            // COMMON can only have 1 modifier total, split between prefixes and suffixes
            List<RealmModifier> tooManyPrefixes = List.of(
                RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 50),
                RealmModifier.of(RealmModifierType.MONSTER_HEALTH, 50)
            );
            List<RealmModifier> tooManySuffixes = List.of(
                RealmModifier.of(RealmModifierType.ITEM_QUANTITY, 20),
                RealmModifier.of(RealmModifierType.ITEM_RARITY, 20)
            );

            RealmMapData map = new RealmMapData(
                50, GearRarity.COMMON, 50,
                RealmBiomeType.FOREST, RealmLayoutSize.SMALL, RealmLayoutShape.CIRCULAR,
                tooManyPrefixes,
                tooManySuffixes,
                false, false,
                null // instanceId
            );

            // Should be clamped to max for COMMON (1)
            assertEquals(1, map.modifierCount());
        }
    }

    @Nested
    @DisplayName("create factory")
    class CreateFactory {

        @Test
        @DisplayName("Should create unidentified map with defaults")
        void createWithDefaults() {
            RealmMapData map = RealmMapData.create(100, GearRarity.EPIC, RealmBiomeType.VOLCANO, RealmLayoutSize.LARGE);

            assertEquals(100, map.level());
            assertEquals(GearRarity.EPIC, map.rarity());
            assertEquals(50, map.quality()); // Default quality
            assertEquals(RealmBiomeType.VOLCANO, map.biome());
            assertEquals(RealmLayoutSize.LARGE, map.size());
            assertEquals(RealmLayoutShape.CIRCULAR, map.shape()); // Default shape
            assertTrue(map.modifiers().isEmpty());
            assertFalse(map.corrupted());
            assertFalse(map.identified());
        }
    }

    @Nested
    @DisplayName("builder")
    class BuilderTests {

        @Test
        @DisplayName("Should build with all values")
        void buildComplete() {
            RealmMapData map = RealmMapData.builder()
                .level(200)
                .rarity(GearRarity.LEGENDARY)
                .quality(101)
                .biome(RealmBiomeType.VOID)
                .size(RealmLayoutSize.MASSIVE)
                .shape(RealmLayoutShape.IRREGULAR)
                .addModifier(RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 80))
                .corrupted(true)
                .identified(true)
                .build();

            assertEquals(200, map.level());
            assertEquals(GearRarity.LEGENDARY, map.rarity());
            assertEquals(101, map.quality());
            assertEquals(RealmBiomeType.VOID, map.biome());
            assertEquals(RealmLayoutSize.MASSIVE, map.size());
            assertEquals(1, map.modifiers().size());
            assertTrue(map.corrupted());
            assertTrue(map.identified());
        }
    }

    @Nested
    @DisplayName("ModifiableItem interface")
    class ModifiableItemInterface {

        @Test
        @DisplayName("maxModifiers should match rarity")
        void maxModifiersMatchesRarity() {
            RealmMapData common = RealmMapData.create(50, GearRarity.COMMON, RealmBiomeType.FOREST, RealmLayoutSize.SMALL);
            RealmMapData legendary = RealmMapData.create(50, GearRarity.LEGENDARY, RealmBiomeType.FOREST, RealmLayoutSize.SMALL);

            assertEquals(1, common.maxModifiers());
            assertEquals(5, legendary.maxModifiers());
        }

        @Test
        @DisplayName("mapQuantityBonus should always return 0 (quality scales modifiers directly)")
        void mapQuantityBonusAlwaysZero() {
            RealmMapData lowQuality = RealmMapData.builder().quality(10).build();
            RealmMapData highQuality = RealmMapData.builder().quality(100).build();
            RealmMapData perfectQuality = RealmMapData.builder().quality(101).build();

            assertEquals(0, lowQuality.mapQuantityBonus());
            assertEquals(0, highQuality.mapQuantityBonus());
            assertEquals(0, perfectQuality.mapQuantityBonus());
        }

        @Test
        @DisplayName("qualityMultiplier should match gear formula")
        void qualityMultiplierMatchesGear() {
            RealmMapData q1 = RealmMapData.builder().quality(1).build();
            RealmMapData q50 = RealmMapData.builder().quality(50).build();
            RealmMapData q101 = RealmMapData.builder().quality(101).build();

            assertEquals(0.51, q1.qualityMultiplier(), 0.001);
            assertEquals(1.0, q50.qualityMultiplier(), 0.001);
            assertEquals(1.51, q101.qualityMultiplier(), 0.001);
        }

        @Test
        @DisplayName("canBeModified should return false when corrupted")
        void canBeModifiedFalseWhenCorrupted() {
            RealmMapData normal = RealmMapData.builder().corrupted(false).build();
            RealmMapData corrupted = RealmMapData.builder().corrupted(true).build();

            assertTrue(normal.canBeModified());
            assertFalse(corrupted.canBeModified());
        }
    }

    @Nested
    @DisplayName("computed properties")
    class ComputedProperties {

        @Test
        @DisplayName("getDifficultyRating should sum weights")
        void difficultyRatingSumsWeights() {
            RealmMapData map = RealmMapData.builder()
                .rarity(GearRarity.UNCOMMON) // Need UNCOMMON to allow 2 modifiers
                .addModifier(RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 50)) // weight 1
                .addModifier(RealmModifier.of(RealmModifierType.REDUCED_TIME, 20)) // weight 2
                .build();

            assertEquals(3, map.getDifficultyRating());
        }

        @Test
        @DisplayName("getTotalItemQuantity should include quality bonus")
        void totalIIQIncludesQuality() {
            RealmMapData map = RealmMapData.builder()
                .quality(100) // +10% from quality
                .addModifier(RealmModifier.of(RealmModifierType.ITEM_QUANTITY, 20)) // +20% from modifier
                .build();

            assertEquals(30, map.getTotalItemQuantity()); // 20 + 10
        }

        @Test
        @DisplayName("getTotalItemQuantity should include Fortune's Compass bonus")
        void totalIIQIncludesFortunesCompass() {
            RealmMapData map = RealmMapData.builder()
                .quality(100) // +10% from quality
                .addModifier(RealmModifier.of(RealmModifierType.ITEM_QUANTITY, 20)) // +20% from suffix
                .fortunesCompassBonus(15) // +15% from Compass
                .build();

            assertEquals(30, map.getBaseItemQuantity());
            assertEquals(45, map.getTotalItemQuantity());
        }

        @Test
        @DisplayName("getMonsterDamageMultiplier should calculate correctly")
        void monsterDamageMultiplier() {
            RealmMapData noMod = RealmMapData.builder().build();
            assertEquals(1.0f, noMod.getMonsterDamageMultiplier());

            RealmMapData withMod = RealmMapData.builder()
                .addModifier(RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 50))
                .build();
            assertEquals(1.5f, withMod.getMonsterDamageMultiplier());
        }

        @Test
        @DisplayName("calculateMonsterCount should factor in modifiers")
        void monsterCountFactorsModifiers() {
            RealmMapData noMod = RealmMapData.builder()
                .level(1)
                .size(RealmLayoutSize.MEDIUM)
                .build();

            RealmMapData withExtraMonsters = RealmMapData.builder()
                .level(1)
                .size(RealmLayoutSize.MEDIUM)
                .addModifier(RealmModifier.of(RealmModifierType.EXTRA_MONSTERS, 50))
                .build();

            int baseCount = noMod.calculateMonsterCount();
            int boostedCount = withExtraMonsters.calculateMonsterCount();

            assertTrue(boostedCount > baseCount);
            assertEquals(Math.round(baseCount * 1.5f), boostedCount);
        }

        @Test
        @DisplayName("getTimeoutSeconds should reduce with modifier")
        void timeoutReducedByModifier() {
            RealmMapData noMod = RealmMapData.builder()
                .size(RealmLayoutSize.MEDIUM) // 600 seconds base
                .build();

            RealmMapData withMod = RealmMapData.builder()
                .size(RealmLayoutSize.MEDIUM)
                .addModifier(RealmModifier.of(RealmModifierType.REDUCED_TIME, 20)) // -20%
                .build();

            assertEquals(600, noMod.getTimeoutSeconds());
            assertEquals(480, withMod.getTimeoutSeconds()); // 600 - 120 = 480
        }
    }

    @Nested
    @DisplayName("hasModifier")
    class HasModifier {

        @Test
        @DisplayName("Should return true when modifier present")
        void trueWhenPresent() {
            RealmMapData map = RealmMapData.builder()
                .addModifier(RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 50))
                .build();

            assertTrue(map.hasModifier(RealmModifierType.MONSTER_DAMAGE));
        }

        @Test
        @DisplayName("Should return false when modifier absent")
        void falseWhenAbsent() {
            RealmMapData map = RealmMapData.builder()
                .addModifier(RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 50))
                .build();

            assertFalse(map.hasModifier(RealmModifierType.ITEM_QUANTITY));
        }
    }

    @Nested
    @DisplayName("copy methods")
    class CopyMethods {

        @Test
        @DisplayName("identify should set identified flag")
        void identifySetsFlag() {
            RealmMapData unidentified = RealmMapData.builder().identified(false).build();
            RealmMapData identified = unidentified.identify();

            assertFalse(unidentified.identified());
            assertTrue(identified.identified());
        }

        @Test
        @DisplayName("identify should return same instance if already identified")
        void identifyReturnsSameIfAlready() {
            RealmMapData identified = RealmMapData.builder().identified(true).build();
            assertSame(identified, identified.identify());
        }

        @Test
        @DisplayName("corrupt should set corrupted flag")
        void corruptSetsFlag() {
            RealmMapData normal = RealmMapData.builder().corrupted(false).build();
            RealmMapData corrupted = normal.corrupt();

            assertFalse(normal.corrupted());
            assertTrue(corrupted.corrupted());
        }

        @Test
        @DisplayName("withModifiers should replace modifiers")
        void withModifiersReplaces() {
            RealmMapData original = RealmMapData.builder()
                .addModifier(RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 50))
                .build();

            List<RealmModifier> newMods = List.of(
                RealmModifier.of(RealmModifierType.ITEM_QUANTITY, 20)
            );

            RealmMapData updated = original.withModifiers(newMods);

            assertEquals(1, original.modifiers().size());
            assertEquals(RealmModifierType.MONSTER_DAMAGE, original.modifiers().get(0).type());

            assertEquals(1, updated.modifiers().size());
            assertEquals(RealmModifierType.ITEM_QUANTITY, updated.modifiers().get(0).type());
        }

        @Test
        @DisplayName("withFortunesCompassBonus should update compass bonus only")
        void withFortunesCompassBonusUpdatesValue() {
            RealmMapData original = RealmMapData.builder()
                .quality(100)
                .fortunesCompassBonus(5)
                .build();

            RealmMapData updated = original.withFortunesCompassBonus(20);

            assertEquals(5, original.fortunesCompassBonus());
            assertEquals(20, updated.fortunesCompassBonus());
            assertEquals(original.quality(), updated.quality());
            assertEquals(original.modifierCount(), updated.modifierCount());
        }
    }

    @Nested
    @DisplayName("getTemplateName")
    class GetTemplateName {

        @Test
        @DisplayName("Should return biome and size specific template")
        void returnsCorrectTemplate() {
            RealmMapData map = RealmMapData.builder()
                .biome(RealmBiomeType.VOLCANO)
                .size(RealmLayoutSize.LARGE)
                .build();

            assertEquals("Realm_Volcano_large", map.getTemplateName());
        }
    }

    @Nested
    @DisplayName("getMaxModifiersForRarity")
    class GetMaxModifiersForRarity {

        @Test
        @DisplayName("Should return correct counts for each rarity")
        void correctCountsPerRarity() {
            assertEquals(1, RealmMapData.getMaxModifiersForRarity(GearRarity.COMMON));
            assertEquals(2, RealmMapData.getMaxModifiersForRarity(GearRarity.UNCOMMON));
            assertEquals(3, RealmMapData.getMaxModifiersForRarity(GearRarity.RARE));
            assertEquals(4, RealmMapData.getMaxModifiersForRarity(GearRarity.EPIC));
            assertEquals(5, RealmMapData.getMaxModifiersForRarity(GearRarity.LEGENDARY));
            assertEquals(6, RealmMapData.getMaxModifiersForRarity(GearRarity.MYTHIC));
        }
    }

    @Nested
    @DisplayName("prefix/suffix separation")
    class PrefixSuffixSeparation {

        @Test
        @DisplayName("prefixCount should return count of prefix modifiers")
        void prefixCountShouldReturnPrefixes() {
            RealmMapData map = new RealmMapData(
                50, GearRarity.RARE, 50,
                RealmBiomeType.FOREST, RealmLayoutSize.MEDIUM, RealmLayoutShape.CIRCULAR,
                List.of(
                    RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 30),
                    RealmModifier.of(RealmModifierType.MONSTER_HEALTH, 40)
                ),
                List.of(
                    RealmModifier.of(RealmModifierType.ITEM_QUANTITY, 20)
                ),
                false, true,
                null // instanceId
            );

            assertEquals(2, map.prefixCount());
        }

        @Test
        @DisplayName("suffixCount should return count of suffix modifiers")
        void suffixCountShouldReturnSuffixes() {
            RealmMapData map = new RealmMapData(
                50, GearRarity.RARE, 50,
                RealmBiomeType.FOREST, RealmLayoutSize.MEDIUM, RealmLayoutShape.CIRCULAR,
                List.of(
                    RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 30)
                ),
                List.of(
                    RealmModifier.of(RealmModifierType.ITEM_QUANTITY, 20),
                    RealmModifier.of(RealmModifierType.ITEM_RARITY, 25)
                ),
                false, true,
                null // instanceId
            );

            assertEquals(2, map.suffixCount());
        }

        @Test
        @DisplayName("modifierCount should return total of prefixes and suffixes")
        void modifierCountShouldReturnTotal() {
            RealmMapData map = new RealmMapData(
                50, GearRarity.RARE, 50,
                RealmBiomeType.FOREST, RealmLayoutSize.MEDIUM, RealmLayoutShape.CIRCULAR,
                List.of(
                    RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 30),
                    RealmModifier.of(RealmModifierType.MONSTER_HEALTH, 40)
                ),
                List.of(
                    RealmModifier.of(RealmModifierType.ITEM_QUANTITY, 20)
                ),
                false, true,
                null // instanceId
            );

            assertEquals(3, map.modifierCount());
        }

        @Test
        @DisplayName("modifiers should return combined list with prefixes first")
        void modifiersShouldReturnCombined() {
            RealmMapData map = new RealmMapData(
                50, GearRarity.RARE, 50,
                RealmBiomeType.FOREST, RealmLayoutSize.MEDIUM, RealmLayoutShape.CIRCULAR,
                List.of(
                    RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 30)
                ),
                List.of(
                    RealmModifier.of(RealmModifierType.ITEM_QUANTITY, 20)
                ),
                false, true,
                null // instanceId
            );

            List<RealmModifier> all = map.modifiers();

            assertEquals(2, all.size());
            assertEquals(RealmModifierType.MONSTER_DAMAGE, all.get(0).type());
            assertEquals(RealmModifierType.ITEM_QUANTITY, all.get(1).type());
        }
    }

    @Nested
    @DisplayName("combined index mapping")
    class CombinedIndexMapping {

        @Test
        @DisplayName("getModifier should return prefix for index < prefixCount")
        void getModifierShouldReturnPrefix() {
            RealmMapData map = new RealmMapData(
                50, GearRarity.RARE, 50,
                RealmBiomeType.FOREST, RealmLayoutSize.MEDIUM, RealmLayoutShape.CIRCULAR,
                List.of(
                    RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 30),
                    RealmModifier.of(RealmModifierType.MONSTER_HEALTH, 40)
                ),
                List.of(
                    RealmModifier.of(RealmModifierType.ITEM_QUANTITY, 20)
                ),
                false, true,
                null // instanceId
            );

            assertEquals(RealmModifierType.MONSTER_DAMAGE, map.getModifier(0).type());
            assertEquals(RealmModifierType.MONSTER_HEALTH, map.getModifier(1).type());
        }

        @Test
        @DisplayName("getModifier should return suffix for index >= prefixCount")
        void getModifierShouldReturnSuffix() {
            RealmMapData map = new RealmMapData(
                50, GearRarity.RARE, 50,
                RealmBiomeType.FOREST, RealmLayoutSize.MEDIUM, RealmLayoutShape.CIRCULAR,
                List.of(
                    RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 30)
                ),
                List.of(
                    RealmModifier.of(RealmModifierType.ITEM_QUANTITY, 20),
                    RealmModifier.of(RealmModifierType.ITEM_RARITY, 25)
                ),
                false, true,
                null // instanceId
            );

            // Index 0 = prefix[0]
            assertEquals(RealmModifierType.MONSTER_DAMAGE, map.getModifier(0).type());
            // Index 1 = suffix[0] (since prefixCount is 1)
            assertEquals(RealmModifierType.ITEM_QUANTITY, map.getModifier(1).type());
            // Index 2 = suffix[1]
            assertEquals(RealmModifierType.ITEM_RARITY, map.getModifier(2).type());
        }

        @Test
        @DisplayName("getModifier should throw for out of bounds index")
        void getModifierShouldThrowOutOfBounds() {
            RealmMapData map = new RealmMapData(
                50, GearRarity.COMMON, 50,
                RealmBiomeType.FOREST, RealmLayoutSize.SMALL, RealmLayoutShape.CIRCULAR,
                List.of(RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 30)),
                List.of(),
                false, true,
                null // instanceId
            );

            assertThrows(IndexOutOfBoundsException.class, () -> map.getModifier(-1));
            assertThrows(IndexOutOfBoundsException.class, () -> map.getModifier(1));
        }

        @Test
        @DisplayName("Combined index should work correctly for prefix-only map")
        void combinedIndexPrefixOnly() {
            RealmMapData map = new RealmMapData(
                50, GearRarity.COMMON, 50,
                RealmBiomeType.FOREST, RealmLayoutSize.SMALL, RealmLayoutShape.CIRCULAR,
                List.of(RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 30)),
                List.of(),
                false, true,
                null // instanceId
            );

            assertEquals(1, map.prefixCount());
            assertEquals(0, map.suffixCount());
            assertEquals(1, map.modifierCount());
            assertEquals(RealmModifierType.MONSTER_DAMAGE, map.getModifier(0).type());
        }

        @Test
        @DisplayName("Combined index should work correctly for suffix-only map")
        void combinedIndexSuffixOnly() {
            RealmMapData map = new RealmMapData(
                50, GearRarity.COMMON, 50,
                RealmBiomeType.FOREST, RealmLayoutSize.SMALL, RealmLayoutShape.CIRCULAR,
                List.of(),
                List.of(RealmModifier.of(RealmModifierType.ITEM_QUANTITY, 20)),
                false, true,
                null // instanceId
            );

            assertEquals(0, map.prefixCount());
            assertEquals(1, map.suffixCount());
            assertEquals(1, map.modifierCount());
            assertEquals(RealmModifierType.ITEM_QUANTITY, map.getModifier(0).type());
        }
    }

    @Nested
    @DisplayName("codec")
    class CodecTests {

        @Test
        @DisplayName("Should serialize and deserialize Fortune's Compass bonus")
        void codecRoundTripPreservesFortunesCompassBonus() {
            RealmMapData original = RealmMapData.builder()
                .level(120)
                .rarity(GearRarity.LEGENDARY)
                .quality(90)
                .biome(RealmBiomeType.VOLCANO)
                .size(RealmLayoutSize.LARGE)
                .fortunesCompassBonus(15)
                .addModifier(RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 30))
                .build();

            ExtraInfo extraInfo = mock(ExtraInfo.class);
            BsonDocument encoded = RealmMapData.CODEC.encode(original, extraInfo).asDocument();
            RealmMapData decoded = RealmMapData.CODEC.decode(encoded, extraInfo);

            assertEquals(15, decoded.fortunesCompassBonus());
            assertEquals(original.getTotalItemQuantity(), decoded.getTotalItemQuantity());
        }

        @Test
        @DisplayName("Should default Fortune's Compass bonus to zero when missing")
        void codecMissingCompassBonusDefaultsToZero() {
            RealmMapData original = RealmMapData.builder()
                .quality(100)
                .build();

            ExtraInfo extraInfo = mock(ExtraInfo.class);
            BsonDocument encoded = RealmMapData.CODEC.encode(original, extraInfo).asDocument();
            encoded.remove("fortunesCompassBonus");

            RealmMapData decoded = RealmMapData.CODEC.decode(encoded, extraInfo);
            assertEquals(0, decoded.fortunesCompassBonus());
            assertEquals(decoded.getBaseItemQuantity(), decoded.getTotalItemQuantity());
        }
    }
}
