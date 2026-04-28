package io.github.larsonix.trailoforbis.stones;

import io.github.larsonix.trailoforbis.gear.model.GearRarity;
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

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link StoneActionRegistry}.
 */
@DisplayName("StoneActionRegistry")
class StoneActionRegistryTest {

    private StoneActionRegistry registry;
    private Random random;

    @BeforeEach
    void setUp() {
        registry = new StoneActionRegistry();
        random = new Random(42);
    }

    /**
     * Helper to create a basic map for testing.
     */
    private RealmMapData createTestMap() {
        return new RealmMapData(
            50,                     // level
            GearRarity.RARE,        // rarity
            50,                     // quality
            RealmBiomeType.FOREST,  // biome
            RealmLayoutSize.MEDIUM, // size
            RealmLayoutShape.CIRCULAR, // shape
            List.of(RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 30)), // prefixes (difficulty)
            List.of(RealmModifier.of(RealmModifierType.ITEM_QUANTITY, 20)),  // suffixes (reward)
            false,                  // corrupted
            true,                   // identified
            null                    // instanceId
        );
    }

    /**
     * Helper to create a map with locked modifiers.
     */
    private RealmMapData createMapWithLockedMod() {
        return new RealmMapData(
            50,
            GearRarity.RARE,
            50,
            RealmBiomeType.FOREST,
            RealmLayoutSize.MEDIUM,
            RealmLayoutShape.CIRCULAR,
            List.of(new RealmModifier(RealmModifierType.MONSTER_DAMAGE, 30, true)), // locked prefix
            List.of(RealmModifier.of(RealmModifierType.ITEM_QUANTITY, 20)),          // unlocked suffix
            false,
            true,
            null // instanceId
        );
    }

    /**
     * Helper to create an empty map (no modifiers).
     */
    private RealmMapData createEmptyMap() {
        return new RealmMapData(
            50,
            GearRarity.COMMON,
            50,
            RealmBiomeType.FOREST,
            RealmLayoutSize.MEDIUM,
            RealmLayoutShape.CIRCULAR,
            List.of(), // prefixes
            List.of(), // suffixes
            false,
            true,
            null // instanceId
        );
    }

    /**
     * Helper to create a corrupted map.
     */
    private RealmMapData createCorruptedMap() {
        return new RealmMapData(
            50,
            GearRarity.RARE,
            50,
            RealmBiomeType.FOREST,
            RealmLayoutSize.MEDIUM,
            RealmLayoutShape.CIRCULAR,
            List.of(RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 30)), // prefixes
            List.of(), // suffixes
            true, // corrupted
            true,
            null // instanceId
        );
    }

    @Nested
    @DisplayName("Constructor and getAction")
    class ConstructorTests {

        @Test
        @DisplayName("Should register actions for all stone types")
        void shouldRegisterAllStoneTypes() {
            for (StoneType type : StoneType.values()) {
                // Refund stones are handled by StonePickerPageSupplier, not the action registry
                if (type.isRefundStone()) continue;
                assertDoesNotThrow(() -> registry.getAction(type),
                    "Should have action for: " + type);
            }
        }

        @Test
        @DisplayName("Should accept custom config")
        void shouldAcceptCustomConfig() {
            RealmModifierConfig config = new RealmModifierConfig();
            StoneActionRegistry custom = new StoneActionRegistry(config);
            assertNotNull(custom.getModifierRoller());
        }
    }

    @Nested
    @DisplayName("Gaia's Calibration - Reroll Values")
    class GaiasCalibrationTests {

        @Test
        @DisplayName("Should reroll modifier values")
        void shouldRerollValues() {
            RealmMapData map = createTestMap();
            StoneActionResult result = registry.execute(StoneType.GAIAS_CALIBRATION, map, random);

            assertTrue(result.success());
            assertTrue(result.stoneConsumed());
            assertNotNull(result.modifiedItem());

            RealmMapData modified = (RealmMapData) result.modifiedItem();
            assertEquals(2, modified.modifiers().size());
            // Types should be preserved
            assertEquals(RealmModifierType.MONSTER_DAMAGE, modified.modifiers().get(0).type());
            assertEquals(RealmModifierType.ITEM_QUANTITY, modified.modifiers().get(1).type());
        }

        @Test
        @DisplayName("Should not reroll locked modifiers")
        void shouldNotRerollLocked() {
            RealmMapData map = createMapWithLockedMod();
            StoneActionResult result = registry.execute(StoneType.GAIAS_CALIBRATION, map, random);

            assertTrue(result.success());
            RealmMapData modified = (RealmMapData) result.modifiedItem();

            // Locked modifier should keep its value
            assertEquals(30, modified.modifiers().get(0).value());
        }

        @Test
        @DisplayName("Should fail on empty map")
        void shouldFailOnEmptyMap() {
            RealmMapData map = createEmptyMap();
            StoneActionResult result = registry.execute(StoneType.GAIAS_CALIBRATION, map, random);

            assertFalse(result.success());
            assertFalse(result.stoneConsumed());
        }
    }

    @Nested
    @DisplayName("Alterverse Shard - Reroll Types")
    class AlterverseShardTests {

        @Test
        @DisplayName("Should reroll modifier types")
        void shouldRerollTypes() {
            RealmMapData map = createTestMap();

            // Run multiple times to verify types can change
            Set<RealmModifierType> seenTypes = new HashSet<>();
            for (int i = 0; i < 20; i++) {
                Random r = new Random(i * 1000);
                StoneActionResult result = registry.execute(StoneType.ALTERVERSE_SHARD, map, r);
                assertTrue(result.success());

                RealmMapData modified = (RealmMapData) result.modifiedItem();
                for (RealmModifier mod : modified.modifiers()) {
                    seenTypes.add(mod.type());
                }
            }

            assertTrue(seenTypes.size() > 2, "Should see variety of modifier types");
        }

        @Test
        @DisplayName("Should preserve locked modifiers")
        void shouldPreserveLocked() {
            RealmMapData map = createMapWithLockedMod();

            for (int i = 0; i < 10; i++) {
                Random r = new Random(i * 1000);
                StoneActionResult result = registry.execute(StoneType.ALTERVERSE_SHARD, map, r);
                assertTrue(result.success());

                RealmMapData modified = (RealmMapData) result.modifiedItem();
                // Should still have the locked MONSTER_DAMAGE modifier
                assertTrue(modified.modifiers().stream()
                    .anyMatch(m -> m.type() == RealmModifierType.MONSTER_DAMAGE && m.locked()));
            }
        }
    }

    @Nested
    @DisplayName("Gaia's Gift - Add Modifier")
    class GaiasGiftTests {

        @Test
        @DisplayName("Should add a modifier when below max")
        void shouldAddModifier() {
            RealmMapData map = createTestMap(); // Has 2 modifiers, rare allows up to 4
            StoneActionResult result = registry.execute(StoneType.GAIAS_GIFT, map, random);

            assertTrue(result.success());
            RealmMapData modified = (RealmMapData) result.modifiedItem();
            assertEquals(3, modified.modifiers().size());
        }

        @Test
        @DisplayName("Should fail when at max modifiers")
        void shouldFailAtMax() {
            RealmMapData map = new RealmMapData(
                50, GearRarity.RARE, 50, RealmBiomeType.FOREST,
                RealmLayoutSize.MEDIUM, RealmLayoutShape.CIRCULAR,
                List.of(
                    RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 30),
                    RealmModifier.of(RealmModifierType.MONSTER_HEALTH, 40)
                ), // prefixes
                List.of(
                    RealmModifier.of(RealmModifierType.ITEM_QUANTITY, 20)
                ), // suffixes - RARE allows max 3 total
                false, true,
                null // instanceId
            );

            StoneActionResult result = registry.execute(StoneType.GAIAS_GIFT, map, random);
            assertFalse(result.success());
        }
    }

    @Nested
    @DisplayName("Purging Ember - Remove All Unlocked")
    class PurgingEmberTests {

        @Test
        @DisplayName("Should remove all unlocked modifiers")
        void shouldRemoveAllUnlocked() {
            RealmMapData map = createTestMap();
            StoneActionResult result = registry.execute(StoneType.PURGING_EMBER, map, random);

            assertTrue(result.success());
            RealmMapData modified = (RealmMapData) result.modifiedItem();
            assertTrue(modified.modifiers().isEmpty());
        }

        @Test
        @DisplayName("Should preserve locked modifiers")
        void shouldPreserveLocked() {
            RealmMapData map = createMapWithLockedMod();
            StoneActionResult result = registry.execute(StoneType.PURGING_EMBER, map, random);

            assertTrue(result.success());
            RealmMapData modified = (RealmMapData) result.modifiedItem();
            assertEquals(1, modified.modifiers().size());
            assertTrue(modified.modifiers().get(0).locked());
        }
    }

    @Nested
    @DisplayName("Erosion Shard - Remove One")
    class ErosionShardTests {

        @Test
        @DisplayName("Should remove one random modifier")
        void shouldRemoveOne() {
            RealmMapData map = createTestMap();
            StoneActionResult result = registry.execute(StoneType.EROSION_SHARD, map, random);

            assertTrue(result.success());
            RealmMapData modified = (RealmMapData) result.modifiedItem();
            assertEquals(1, modified.modifiers().size());
        }

        @Test
        @DisplayName("Should only remove unlocked modifiers")
        void shouldOnlyRemoveUnlocked() {
            RealmMapData map = createMapWithLockedMod();
            StoneActionResult result = registry.execute(StoneType.EROSION_SHARD, map, random);

            assertTrue(result.success());
            RealmMapData modified = (RealmMapData) result.modifiedItem();
            // The locked one should remain
            assertEquals(1, modified.modifiers().size());
            assertTrue(modified.modifiers().get(0).locked());
        }
    }

    @Nested
    @DisplayName("Warden's Seal/Key")
    class LockTests {

        @Test
        @DisplayName("Warden's Seal should lock a random modifier")
        void lockShouldLockModifier() {
            RealmMapData map = createTestMap();

            StoneActionResult result = registry.execute(StoneType.WARDENS_SEAL, map, random);

            assertTrue(result.success());
            RealmMapData modified = (RealmMapData) result.modifiedItem();
            long lockedCount = modified.modifiers().stream().filter(m -> m.locked()).count();
            assertEquals(1, lockedCount);
        }

        @Test
        @DisplayName("Warden's Key should unlock a random modifier")
        void keyShouldUnlockModifier() {
            RealmMapData map = createMapWithLockedMod();

            StoneActionResult result = registry.execute(StoneType.WARDENS_KEY, map, random);

            assertTrue(result.success());
            RealmMapData modified = (RealmMapData) result.modifiedItem();
            long lockedCount = modified.modifiers().stream().filter(m -> m.locked()).count();
            assertEquals(0, lockedCount);
        }
    }

    @Nested
    @DisplayName("Varyn's Touch - Corruption")
    class VarynsTouchTests {

        @Test
        @DisplayName("Should corrupt the item")
        void shouldCorruptItem() {
            RealmMapData map = createTestMap();
            StoneActionResult result = registry.execute(StoneType.VARYNS_TOUCH, map, random);

            assertTrue(result.success());
            RealmMapData modified = (RealmMapData) result.modifiedItem();
            assertTrue(modified.corrupted());
        }

        @Test
        @DisplayName("Should fail on already corrupted item")
        void shouldFailOnCorrupted() {
            RealmMapData map = createCorruptedMap();
            StoneActionResult result = registry.execute(StoneType.VARYNS_TOUCH, map, random);

            assertFalse(result.success());
        }
    }

    @Nested
    @DisplayName("Genesis Stone")
    class GenesisStoneTests {

        @Test
        @DisplayName("Should fill all remaining modifier slots on empty item")
        void shouldFillAllSlotsOnEmpty() {
            RealmMapData map = createEmptyMap(); // COMMON has 1 max modifier
            StoneActionResult result = registry.execute(StoneType.GENESIS_STONE, map, random);

            assertTrue(result.success());
            RealmMapData modified = (RealmMapData) result.modifiedItem();
            // COMMON allows 1 modifier, so should fill to 1
            assertEquals(1, modified.modifiers().size());
        }

        @Test
        @DisplayName("Should fill remaining slots on partially filled item")
        void shouldFillRemainingSlotsOnPartialItem() {
            // RARE allows 3 modifiers, start with 1
            RealmMapData map = new RealmMapData(
                50, GearRarity.RARE, 50, RealmBiomeType.FOREST,
                RealmLayoutSize.MEDIUM, RealmLayoutShape.CIRCULAR,
                List.of(RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 30)), // 1 prefix
                List.of(), // no suffixes
                false, true,
                null // instanceId
            );

            StoneActionResult result = registry.execute(StoneType.GENESIS_STONE, map, random);

            assertTrue(result.success());
            RealmMapData modified = (RealmMapData) result.modifiedItem();
            // Should fill to max (3 for RARE)
            assertEquals(3, modified.modifiers().size());
        }

        @Test
        @DisplayName("Should fail on item at max modifiers")
        void shouldFailOnMaxModifiers() {
            // RARE allows 3 modifiers, fill to max
            RealmMapData map = new RealmMapData(
                50, GearRarity.RARE, 50, RealmBiomeType.FOREST,
                RealmLayoutSize.MEDIUM, RealmLayoutShape.CIRCULAR,
                List.of(
                    RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 30),
                    RealmModifier.of(RealmModifierType.MONSTER_HEALTH, 40)
                ), // 2 prefixes
                List.of(RealmModifier.of(RealmModifierType.ITEM_QUANTITY, 20)), // 1 suffix = 3 total
                false, true,
                null // instanceId
            );

            StoneActionResult result = registry.execute(StoneType.GENESIS_STONE, map, random);

            assertFalse(result.success());
        }
    }

    @Nested
    @DisplayName("Lorekeeper's Scroll")
    class LorekeepersScrollTests {

        @Test
        @DisplayName("Should identify unidentified item")
        void shouldIdentify() {
            RealmMapData map = new RealmMapData(
                50, GearRarity.RARE, 50, RealmBiomeType.FOREST,
                RealmLayoutSize.MEDIUM, RealmLayoutShape.CIRCULAR,
                List.of(RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 30)), // prefixes
                List.of(), // suffixes
                false, false, // unidentified
                null // instanceId
            );

            StoneActionResult result = registry.execute(StoneType.LOREKEEPERS_SCROLL, map, random);

            assertTrue(result.success());
            RealmMapData modified = (RealmMapData) result.modifiedItem();
            assertTrue(modified.isIdentified());
        }

        @Test
        @DisplayName("Should fail on already identified item")
        void shouldFailOnIdentified() {
            RealmMapData map = createTestMap(); // Already identified
            StoneActionResult result = registry.execute(StoneType.LOREKEEPERS_SCROLL, map, random);

            assertFalse(result.success());
        }
    }

    @Nested
    @DisplayName("Quality Stones")
    class QualityTests {

        @Test
        @DisplayName("Cartographer's Polish should improve quality")
        void cartographerShouldImproveQuality() {
            RealmMapData map = new RealmMapData(
                50, GearRarity.RARE, 50, RealmBiomeType.FOREST,
                RealmLayoutSize.MEDIUM, RealmLayoutShape.CIRCULAR,
                List.of(), // prefixes
                List.of(), // suffixes
                false, true,
                null // instanceId
            );

            StoneActionResult result = registry.execute(StoneType.CARTOGRAPHERS_POLISH, map, random);

            assertTrue(result.success());
            RealmMapData modified = (RealmMapData) result.modifiedItem();
            assertTrue(modified.quality() > map.quality());
        }

        @Test
        @DisplayName("Orbisian Blessing should reroll quality")
        void orbisianShouldRerollQuality() {
            RealmMapData map = createTestMap();

            Set<Integer> qualities = new HashSet<>();
            for (int i = 0; i < 50; i++) {
                Random r = new Random(i * 1000);
                StoneActionResult result = registry.execute(StoneType.ORBISIAN_BLESSING, map, r);
                if (result.success()) {
                    RealmMapData modified = (RealmMapData) result.modifiedItem();
                    qualities.add(modified.quality());
                }
            }

            assertTrue(qualities.size() > 5, "Should produce variety of quality values");
        }

        @Test
        @DisplayName("Gaia's Perfection should set perfect quality")
        void gaiasShouldSetPerfect() {
            RealmMapData map = createTestMap();
            StoneActionResult result = registry.execute(StoneType.GAIAS_PERFECTION, map, random);

            assertTrue(result.success());
            RealmMapData modified = (RealmMapData) result.modifiedItem();
            assertEquals(101, modified.quality());
        }
    }

    @Nested
    @DisplayName("Corrupted Item Handling")
    class CorruptionTests {

        @Test
        @DisplayName("Stones that dont work on corrupted should fail")
        void stonesShouldFailOnCorrupted() {
            RealmMapData corrupted = createCorruptedMap();

            // These stones cannot be used on corrupted items
            StoneType[] nonCorruptStones = {
                StoneType.GAIAS_CALIBRATION,
                StoneType.ALTERVERSE_SHARD,
                StoneType.GAIAS_GIFT,
                StoneType.PURGING_EMBER,
                StoneType.ORBISIAN_BLESSING,
                StoneType.FORTUNES_COMPASS
            };

            for (StoneType type : nonCorruptStones) {
                StoneActionResult result = registry.execute(type, corrupted, random);
                assertFalse(result.success(), type + " should fail on corrupted item");
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // NEW STONE TESTS
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Ember of Tuning - Reroll One Modifier Value")
    class EmberOfTuningTests {

        @Test
        @DisplayName("Should reroll one modifier value")
        void shouldRerollOneValue() {
            RealmMapData map = createTestMap();
            StoneActionResult result = registry.execute(StoneType.EMBER_OF_TUNING, map, random);

            assertTrue(result.success());
            RealmMapData modified = (RealmMapData) result.modifiedItem();
            assertEquals(2, modified.modifiers().size());
            // Types should be preserved
            assertTrue(modified.modifiers().stream()
                .anyMatch(m -> m.type() == RealmModifierType.MONSTER_DAMAGE));
            assertTrue(modified.modifiers().stream()
                .anyMatch(m -> m.type() == RealmModifierType.ITEM_QUANTITY));
        }

        @Test
        @DisplayName("Should not reroll locked modifiers")
        void shouldNotRerollLocked() {
            // Create a map with only one locked modifier
            RealmMapData map = new RealmMapData(
                50, GearRarity.RARE, 50, RealmBiomeType.FOREST,
                RealmLayoutSize.MEDIUM, RealmLayoutShape.CIRCULAR,
                List.of(new RealmModifier(RealmModifierType.MONSTER_DAMAGE, 30, true)), // prefixes
                List.of(), // suffixes
                false, true,
                null // instanceId
            );

            StoneActionResult result = registry.execute(StoneType.EMBER_OF_TUNING, map, random);
            assertFalse(result.success()); // No unlocked modifiers to reroll
        }

        @Test
        @DisplayName("Should fail on empty map")
        void shouldFailOnEmptyMap() {
            RealmMapData map = createEmptyMap();
            StoneActionResult result = registry.execute(StoneType.EMBER_OF_TUNING, map, random);
            assertFalse(result.success());
        }
    }

    @Nested
    @DisplayName("Spark of Potential - Common to Uncommon Upgrade")
    class SparkOfPotentialTests {

        @Test
        @DisplayName("Should upgrade Common to Uncommon and add modifier")
        void shouldUpgradeCommonToUncommon() {
            // COMMON now has maxModifiers=1, so start with 0 modifiers
            // to allow the stone to add one after upgrading
            RealmMapData map = new RealmMapData(
                50, GearRarity.COMMON, 50, RealmBiomeType.FOREST,
                RealmLayoutSize.MEDIUM, RealmLayoutShape.CIRCULAR,
                List.of(), // prefixes
                List.of(), // suffixes
                false, true,
                null // instanceId
            );

            StoneActionResult result = registry.execute(StoneType.SPARK_OF_POTENTIAL, map, random);

            assertTrue(result.success());
            RealmMapData modified = (RealmMapData) result.modifiedItem();
            assertEquals(GearRarity.UNCOMMON, modified.rarity());
            assertEquals(1, modified.modifiers().size()); // 0 + 1 new = 1
        }

        @Test
        @DisplayName("Should fail on non-Common rarity")
        void shouldFailOnNonCommon() {
            RealmMapData map = createTestMap(); // This is RARE rarity
            StoneActionResult result = registry.execute(StoneType.SPARK_OF_POTENTIAL, map, random);
            assertFalse(result.success());
        }
    }

    @Nested
    @DisplayName("Core of Ascension - Uncommon to Rare Upgrade")
    class CoreOfAscensionTests {

        @Test
        @DisplayName("Should upgrade Uncommon to Rare and add modifier")
        void shouldUpgradeUncommonToRare() {
            RealmMapData map = new RealmMapData(
                50, GearRarity.UNCOMMON, 50, RealmBiomeType.FOREST,
                RealmLayoutSize.MEDIUM, RealmLayoutShape.CIRCULAR,
                List.of(RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 20)), // prefixes
                List.of(), // suffixes
                false, true,
                null // instanceId
            );

            StoneActionResult result = registry.execute(StoneType.CORE_OF_ASCENSION, map, random);

            assertTrue(result.success());
            RealmMapData modified = (RealmMapData) result.modifiedItem();
            assertEquals(GearRarity.RARE, modified.rarity());
            assertEquals(2, modified.modifiers().size());
        }

        @Test
        @DisplayName("Should fail on non-Uncommon rarity")
        void shouldFailOnNonUncommon() {
            RealmMapData map = createTestMap(); // This is RARE rarity
            StoneActionResult result = registry.execute(StoneType.CORE_OF_ASCENSION, map, random);
            assertFalse(result.success());
        }
    }

    @Nested
    @DisplayName("Heart of Legends - Rare to Epic Upgrade")
    class HeartOfLegendsTests {

        @Test
        @DisplayName("Should upgrade Rare to Epic and add modifier")
        void shouldUpgradeRareToEpic() {
            RealmMapData map = new RealmMapData(
                50, GearRarity.RARE, 50, RealmBiomeType.FOREST,
                RealmLayoutSize.MEDIUM, RealmLayoutShape.CIRCULAR,
                List.of(RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 20)), // prefixes
                List.of(), // suffixes
                false, true,
                null // instanceId
            );

            StoneActionResult result = registry.execute(StoneType.HEART_OF_LEGENDS, map, random);

            assertTrue(result.success());
            RealmMapData modified = (RealmMapData) result.modifiedItem();
            assertEquals(GearRarity.EPIC, modified.rarity());
            assertEquals(2, modified.modifiers().size());
        }

        @Test
        @DisplayName("Should fail on non-Rare rarity")
        void shouldFailOnNonRare() {
            RealmMapData map = new RealmMapData(
                50, GearRarity.EPIC, 50, RealmBiomeType.FOREST,
                RealmLayoutSize.MEDIUM, RealmLayoutShape.CIRCULAR,
                List.of(RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 20)), // prefixes
                List.of(), // suffixes
                false, true,
                null // instanceId
            );

            StoneActionResult result = registry.execute(StoneType.HEART_OF_LEGENDS, map, random);
            assertFalse(result.success());
        }
    }

    @Nested
    @DisplayName("Transmutation Crystal - Atomic Swap")
    class TransmutationCrystalTests {

        @Test
        @DisplayName("Should remove one modifier and add one")
        void shouldSwapModifier() {
            RealmMapData map = createTestMap();
            int originalCount = map.modifiers().size();

            StoneActionResult result = registry.execute(StoneType.TRANSMUTATION_CRYSTAL, map, random);

            assertTrue(result.success());
            RealmMapData modified = (RealmMapData) result.modifiedItem();
            // Count should remain same (remove one, add one)
            assertEquals(originalCount, modified.modifiers().size());
        }

        @Test
        @DisplayName("Should not remove locked modifiers")
        void shouldNotRemoveLocked() {
            RealmMapData map = createMapWithLockedMod();

            StoneActionResult result = registry.execute(StoneType.TRANSMUTATION_CRYSTAL, map, random);

            assertTrue(result.success());
            RealmMapData modified = (RealmMapData) result.modifiedItem();
            // Locked modifier should still be there
            assertTrue(modified.modifiers().stream()
                .anyMatch(m -> m.type() == RealmModifierType.MONSTER_DAMAGE && m.locked()));
        }

        @Test
        @DisplayName("Should fail on map with no unlocked modifiers")
        void shouldFailOnNoUnlocked() {
            RealmMapData map = new RealmMapData(
                50, GearRarity.RARE, 50, RealmBiomeType.FOREST,
                RealmLayoutSize.MEDIUM, RealmLayoutShape.CIRCULAR,
                List.of(new RealmModifier(RealmModifierType.MONSTER_DAMAGE, 30, true)), // prefixes
                List.of(), // suffixes
                false, true,
                null // instanceId
            );

            StoneActionResult result = registry.execute(StoneType.TRANSMUTATION_CRYSTAL, map, random);
            assertFalse(result.success());
        }
    }

    @Nested
    @DisplayName("Fortune's Compass - Add Item Quantity")
    class FortunesCompassTests {

        @Test
        @DisplayName("Should add dedicated Compass bonus to map")
        void shouldAddCompassBonus() {
            RealmMapData map = createEmptyMap();

            StoneActionResult result = registry.execute(StoneType.FORTUNES_COMPASS, map, random);

            assertTrue(result.success());
            RealmMapData modified = (RealmMapData) result.modifiedItem();
            assertEquals(5, modified.fortunesCompassBonus());
            assertEquals(map.modifierCount(), modified.modifierCount(), "Compass should not consume modifier slots");
        }

        @Test
        @DisplayName("Should work when map is at max modifiers")
        void shouldWorkAtMaxModifiers() {
            // Create a map at max modifiers (Common allows 1)
            RealmMapData map = new RealmMapData(
                50, GearRarity.COMMON, 50, RealmBiomeType.FOREST,
                RealmLayoutSize.MEDIUM, RealmLayoutShape.CIRCULAR,
                List.of(RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 20)), // prefixes
                List.of(), // suffixes - total is 1 which is max for COMMON
                false, true,
                null // instanceId
            );

            StoneActionResult result = registry.execute(StoneType.FORTUNES_COMPASS, map, random);
            assertTrue(result.success());
            RealmMapData modified = (RealmMapData) result.modifiedItem();
            assertEquals(5, modified.fortunesCompassBonus());
            assertEquals(1, modified.modifierCount(), "Compass should not alter maxed modifier count");
        }

        @Test
        @DisplayName("Should fail at max Compass bonus (20%)")
        void shouldFailAtMaxCompassBonus() {
            RealmMapData map = new RealmMapData(
                50, GearRarity.LEGENDARY, 50, RealmBiomeType.FOREST,
                RealmLayoutSize.MEDIUM, RealmLayoutShape.CIRCULAR,
                List.of(), // prefixes
                List.of(), // suffixes
                20, // fortunesCompassBonus
                false, true,
                null // instanceId
            );

            StoneActionResult result = registry.execute(StoneType.FORTUNES_COMPASS, map, random);
            assertFalse(result.success());
        }

        @Test
        @DisplayName("Should cap repeated apply at 20")
        void shouldCapAtTwenty() {
            RealmMapData map = createEmptyMap();
            RealmMapData current = map;

            for (int i = 0; i < 4; i++) {
                StoneActionResult result = registry.execute(StoneType.FORTUNES_COMPASS, current, random);
                assertTrue(result.success());
                current = (RealmMapData) result.modifiedItem();
            }

            assertEquals(20, current.fortunesCompassBonus());

            StoneActionResult atCap = registry.execute(StoneType.FORTUNES_COMPASS, current, random);
            assertFalse(atCap.success());
        }
    }

    @Nested
    @DisplayName("Alterverse Key - Change Biome")
    class AlterverseKeyTests {

        @Test
        @DisplayName("Should change biome to a different one")
        void shouldChangeBiome() {
            RealmMapData map = createTestMap();
            RealmBiomeType originalBiome = map.biome();

            // Run multiple times to ensure we get a different biome
            boolean foundDifferent = false;
            for (int i = 0; i < 20; i++) {
                Random r = new Random(i * 1000);
                StoneActionResult result = registry.execute(StoneType.ALTERVERSE_KEY, map, r);

                assertTrue(result.success());
                RealmMapData modified = (RealmMapData) result.modifiedItem();

                if (modified.biome() != originalBiome) {
                    foundDifferent = true;
                    break;
                }
            }

            assertTrue(foundDifferent, "Should be able to get a different biome");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // canApply() VALIDATION TESTS
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("canApply() - Pre-filtering Validation")
    class CanApplyTests {

        @Test
        @DisplayName("Gaia's Calibration - requires unlocked modifiers")
        void gaiasCalibrationCanApply() {
            StoneAction action = registry.getAction(StoneType.GAIAS_CALIBRATION);

            // Should return true for map with unlocked modifiers
            assertTrue(action.canApply(createTestMap()));

            // Should return false for empty map
            assertFalse(action.canApply(createEmptyMap()));

            // Should return false if all modifiers are locked
            RealmMapData allLocked = new RealmMapData(
                50, GearRarity.RARE, 50, RealmBiomeType.FOREST,
                RealmLayoutSize.MEDIUM, RealmLayoutShape.CIRCULAR,
                List.of(new RealmModifier(RealmModifierType.MONSTER_DAMAGE, 30, true)),
                List.of(),
                false, true, null
            );
            assertFalse(action.canApply(allLocked));
        }

        @Test
        @DisplayName("Spark of Potential - requires Common rarity")
        void sparkOfPotentialCanApply() {
            StoneAction action = registry.getAction(StoneType.SPARK_OF_POTENTIAL);

            // Should return true for Common item
            RealmMapData commonMap = new RealmMapData(
                50, GearRarity.COMMON, 50, RealmBiomeType.FOREST,
                RealmLayoutSize.MEDIUM, RealmLayoutShape.CIRCULAR,
                List.of(), List.of(),
                false, true, null
            );
            assertTrue(action.canApply(commonMap));

            // Should return false for non-Common
            assertFalse(action.canApply(createTestMap())); // RARE
        }

        @Test
        @DisplayName("Genesis Stone - requires room for more modifiers")
        void genesisStoneCanApply() {
            StoneAction action = registry.getAction(StoneType.GENESIS_STONE);

            // Should return true for empty map (has room)
            assertTrue(action.canApply(createEmptyMap()));

            // Should return true for map with room for more modifiers
            assertTrue(action.canApply(createTestMap())); // RARE has 2 mods, allows 3

            // Should return false when at max
            RealmMapData atMax = new RealmMapData(
                50, GearRarity.RARE, 50, RealmBiomeType.FOREST,
                RealmLayoutSize.MEDIUM, RealmLayoutShape.CIRCULAR,
                List.of(
                    RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 30),
                    RealmModifier.of(RealmModifierType.MONSTER_HEALTH, 40)
                ),
                List.of(RealmModifier.of(RealmModifierType.ITEM_QUANTITY, 20)),
                false, true, null
            );
            assertFalse(action.canApply(atMax)); // RARE max is 3
        }

        @Test
        @DisplayName("Warden's Seal - requires unlocked modifiers to lock")
        void wardensSealCanApply() {
            StoneAction action = registry.getAction(StoneType.WARDENS_SEAL);

            // Should return true if there are unlocked modifiers
            assertTrue(action.canApply(createTestMap()));

            // Should return false for empty map
            assertFalse(action.canApply(createEmptyMap()));

            // Should return false if all are locked
            RealmMapData allLocked = new RealmMapData(
                50, GearRarity.RARE, 50, RealmBiomeType.FOREST,
                RealmLayoutSize.MEDIUM, RealmLayoutShape.CIRCULAR,
                List.of(new RealmModifier(RealmModifierType.MONSTER_DAMAGE, 30, true)),
                List.of(),
                false, true, null
            );
            assertFalse(action.canApply(allLocked));
        }

        @Test
        @DisplayName("Warden's Key - requires locked modifiers to unlock")
        void wardensKeyCanApply() {
            StoneAction action = registry.getAction(StoneType.WARDENS_KEY);

            // Should return false for map with no locked modifiers
            assertFalse(action.canApply(createTestMap()));

            // Should return true for map with locked modifiers
            assertTrue(action.canApply(createMapWithLockedMod()));
        }

        @Test
        @DisplayName("Lorekeeper's Scroll - requires unidentified map")
        void lorekeepersScrollCanApply() {
            StoneAction action = registry.getAction(StoneType.LOREKEEPERS_SCROLL);

            // Should return false for identified map
            assertFalse(action.canApply(createTestMap())); // identified=true

            // Should return true for unidentified map
            RealmMapData unidentified = new RealmMapData(
                50, GearRarity.RARE, 50, RealmBiomeType.FOREST,
                RealmLayoutSize.MEDIUM, RealmLayoutShape.CIRCULAR,
                List.of(RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 30)),
                List.of(),
                false, false, // unidentified
                null
            );
            assertTrue(action.canApply(unidentified));
        }

        @Test
        @DisplayName("Gaia's Gift - requires room for more modifiers")
        void gaiasGiftCanApply() {
            StoneAction action = registry.getAction(StoneType.GAIAS_GIFT);

            // Should return true for map with room
            assertTrue(action.canApply(createTestMap())); // RARE allows 3, has 2

            // Should return false when at max
            RealmMapData atMax = new RealmMapData(
                50, GearRarity.RARE, 50, RealmBiomeType.FOREST,
                RealmLayoutSize.MEDIUM, RealmLayoutShape.CIRCULAR,
                List.of(
                    RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 30),
                    RealmModifier.of(RealmModifierType.MONSTER_HEALTH, 40)
                ),
                List.of(RealmModifier.of(RealmModifierType.ITEM_QUANTITY, 20)),
                false, true, null
            );
            assertFalse(action.canApply(atMax)); // RARE max is 3
        }

        @Test
        @DisplayName("Quality stones - require non-perfect quality")
        void qualityStonesCanApply() {
            StoneAction orbisian = registry.getAction(StoneType.ORBISIAN_BLESSING);
            StoneAction perfection = registry.getAction(StoneType.GAIAS_PERFECTION);

            // Should return true for normal quality
            assertTrue(orbisian.canApply(createTestMap()));
            assertTrue(perfection.canApply(createTestMap()));

            // Should return false for perfect quality
            RealmMapData perfect = new RealmMapData(
                50, GearRarity.RARE, 101, RealmBiomeType.FOREST,
                RealmLayoutSize.MEDIUM, RealmLayoutShape.CIRCULAR,
                List.of(), List.of(),
                false, true, null
            );
            assertFalse(orbisian.canApply(perfect));
            assertFalse(perfection.canApply(perfect));
        }

        @Test
        @DisplayName("Varyn's Touch - requires non-corrupted item")
        void varynsTouchCanApply() {
            StoneAction action = registry.getAction(StoneType.VARYNS_TOUCH);

            // Should return true for non-corrupted
            assertTrue(action.canApply(createTestMap()));

            // Should return false for corrupted
            assertFalse(action.canApply(createCorruptedMap()));
        }

        @Test
        @DisplayName("Fortune's Compass - allows full-modifier maps until bonus cap")
        void fortunesCompassCanApply() {
            StoneAction action = registry.getAction(StoneType.FORTUNES_COMPASS);

            RealmMapData fullModifiers = new RealmMapData(
                50, GearRarity.COMMON, 50, RealmBiomeType.FOREST,
                RealmLayoutSize.MEDIUM, RealmLayoutShape.CIRCULAR,
                List.of(RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 20)),
                List.of(),
                15,
                false, true, null
            );
            assertTrue(action.canApply(fullModifiers));

            RealmMapData capped = fullModifiers.withFortunesCompassBonus(20);
            assertFalse(action.canApply(capped));
        }
    }

    @Nested
    @DisplayName("Threshold Stone - Reroll Level")
    class ThresholdStoneTests {

        @Test
        @DisplayName("Should reroll level within ±3")
        void shouldRerollLevelWithinRange() {
            RealmMapData map = new RealmMapData(
                50, GearRarity.RARE, 50, RealmBiomeType.FOREST,
                RealmLayoutSize.MEDIUM, RealmLayoutShape.CIRCULAR,
                List.of(), // prefixes
                List.of(), // suffixes
                false, true,
                null // instanceId
            );

            Set<Integer> seenLevels = new HashSet<>();
            for (int i = 0; i < 50; i++) {
                Random r = new Random(i * 1000);
                StoneActionResult result = registry.execute(StoneType.THRESHOLD_STONE, map, r);

                assertTrue(result.success());
                RealmMapData modified = (RealmMapData) result.modifiedItem();

                int newLevel = modified.level();
                seenLevels.add(newLevel);

                // Level should be within ±3 of original (50)
                assertTrue(newLevel >= 47 && newLevel <= 53,
                    "Level " + newLevel + " should be within ±3 of 50");
            }

            // Should see variety
            assertTrue(seenLevels.size() > 1, "Should produce variety of levels");
        }

        @Test
        @DisplayName("Should not go below level 1")
        void shouldNotGoBelowOne() {
            RealmMapData map = new RealmMapData(
                2, GearRarity.RARE, 50, RealmBiomeType.FOREST,
                RealmLayoutSize.MEDIUM, RealmLayoutShape.CIRCULAR,
                List.of(), // prefixes
                List.of(), // suffixes
                false, true,
                null // instanceId
            );

            for (int i = 0; i < 20; i++) {
                Random r = new Random(i * 1000);
                StoneActionResult result = registry.execute(StoneType.THRESHOLD_STONE, map, r);

                assertTrue(result.success());
                RealmMapData modified = (RealmMapData) result.modifiedItem();

                assertTrue(modified.level() >= 1, "Level should not go below 1");
            }
        }
    }
}
