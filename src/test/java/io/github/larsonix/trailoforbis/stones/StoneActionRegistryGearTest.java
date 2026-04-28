package io.github.larsonix.trailoforbis.stones;

import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig;
import io.github.larsonix.trailoforbis.gear.config.TestConfigFactory;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.GearModifier;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.gear.model.ModifierType;
import io.github.larsonix.trailoforbis.maps.config.RealmModifierConfig;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link StoneActionRegistry} with gear items.
 *
 * <p>Tests all 18 stones that are compatible with gear (3 map-only stones excluded):
 * <ul>
 *   <li>GAIAS_CALIBRATION - Reroll all modifier values</li>
 *   <li>EMBER_OF_TUNING - Reroll one modifier value</li>
 *   <li>ALTERVERSE_SHARD - Reroll modifier types</li>
 *   <li>ORBISIAN_BLESSING - Reroll quality</li>
 *   <li>GAIAS_GIFT - Add a modifier</li>
 *   <li>SPARK_OF_POTENTIAL - Common→Uncommon + add modifier</li>
 *   <li>CORE_OF_ASCENSION - Uncommon→Rare + add modifier</li>
 *   <li>HEART_OF_LEGENDS - Rare→Epic + add modifier</li>
 *   <li>PURGING_EMBER - Clear all unlocked modifiers</li>
 *   <li>EROSION_SHARD - Remove one modifier</li>
 *   <li>TRANSMUTATION_CRYSTAL - Remove one + add one</li>
 *   <li>WARDENS_SEAL - Lock random modifier</li>
 *   <li>WARDENS_KEY - Unlock random modifier</li>
 *   <li>THRESHOLD_STONE - Reroll level ±3</li>
 *   <li>VARYNS_TOUCH - Corrupt item</li>
 *   <li>GAIAS_PERFECTION - Set perfect quality</li>
 *   <li>LOREKEEPERS_SCROLL - Returns failure (gear doesn't need ID)</li>
 *   <li>GENESIS_STONE - Add modifiers to empty item</li>
 * </ul>
 *
 * <p>Map-only stones NOT tested here:
 * <ul>
 *   <li>CARTOGRAPHERS_POLISH</li>
 *   <li>FORTUNES_COMPASS</li>
 *   <li>ALTERVERSE_KEY</li>
 * </ul>
 */
@DisplayName("StoneActionRegistry - Gear Items")
class StoneActionRegistryGearTest {

    private StoneActionRegistry registry;
    private Random random;

    @BeforeEach
    void setUp() {
        // Create registry with gear support using TestConfigFactory
        RealmModifierConfig realmConfig = new RealmModifierConfig();
        ModifierConfig gearConfig = TestConfigFactory.createDefaultModifierConfig();
        GearBalanceConfig gearBalance = TestConfigFactory.createDefaultBalanceConfig();

        registry = new StoneActionRegistry(realmConfig, gearConfig, gearBalance);
        random = new Random(42);
    }

    // ═══════════════════════════════════════════════════════════════════
    // TEST DATA HELPERS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Helper to create a basic gear item for testing.
     */
    private GearData createTestGear() {
        return new GearData(
            null,                   // instanceId
            50,                     // level
            GearRarity.RARE,        // rarity (allows 4 modifiers)
            50,                     // quality
            List.of(                // prefixes
                GearModifier.of("sharp", "Sharp", ModifierType.PREFIX,
                    "physical_damage", GearModifier.STAT_TYPE_FLAT, 10.0)
            ),
            List.of(                // suffixes
                GearModifier.of("of_the_whale", "of the Whale", ModifierType.SUFFIX,
                    "max_health", GearModifier.STAT_TYPE_PERCENT, 15.0)
            ),
            false,                  // corrupted
            null,                   // implicit
            null,                   // armorImplicit
            null,                   // baseItemId
            null,                   // activeGem
            List.of(),              // supportGems
            0                       // supportSlotCount
        );
    }

    /**
     * Helper to create gear with a locked modifier.
     */
    private GearData createGearWithLockedMod() {
        return new GearData(
            null,
            50,
            GearRarity.RARE,
            50,
            List.of(
                new GearModifier("sharp", "Sharp", ModifierType.PREFIX,
                    "physical_damage", GearModifier.STAT_TYPE_FLAT, 10.0, true) // locked
            ),
            List.of(
                GearModifier.of("of_the_whale", "of the Whale", ModifierType.SUFFIX,
                    "max_health", GearModifier.STAT_TYPE_PERCENT, 15.0) // unlocked
            ),
            false,
            null,
            null,
            null,
            null, List.of(), 0
        );
    }

    /**
     * Helper to create gear with no modifiers.
     */
    private GearData createEmptyGear() {
        return new GearData(
            null,
            50,
            GearRarity.COMMON,
            50,
            List.of(),
            List.of(),
            false,
            null,
            null,
            null,
            null, List.of(), 0
        );
    }

    /**
     * Helper to create corrupted gear.
     */
    private GearData createCorruptedGear() {
        return new GearData(
            null,
            50,
            GearRarity.RARE,
            50,
            List.of(
                GearModifier.of("sharp", "Sharp", ModifierType.PREFIX,
                    "physical_damage", GearModifier.STAT_TYPE_FLAT, 10.0)
            ),
            List.of(),
            true, // corrupted
            null,
            null,
            null,
            null, List.of(), 0
        );
    }

    /**
     * Helper to create gear at max modifiers for its rarity.
     */
    private GearData createMaxModGear(GearRarity rarity) {
        int maxMods = rarity.getMaxModifiers();
        List<GearModifier> prefixes = new ArrayList<>();
        List<GearModifier> suffixes = new ArrayList<>();

        // Split evenly between prefixes and suffixes
        for (int i = 0; i < maxMods; i++) {
            if (i % 2 == 0) {
                prefixes.add(GearModifier.of("prefix_" + i, "Prefix " + i, ModifierType.PREFIX,
                    "physical_damage", GearModifier.STAT_TYPE_FLAT, 10.0 + i));
            } else {
                suffixes.add(GearModifier.of("suffix_" + i, "Suffix " + i, ModifierType.SUFFIX,
                    "max_health", GearModifier.STAT_TYPE_PERCENT, 5.0 + i));
            }
        }

        return new GearData(null, 50, rarity, 50, prefixes, suffixes, false, null, null, null, null, List.of(), 0);
    }

    // ═══════════════════════════════════════════════════════════════════
    // GAIA'S CALIBRATION - Reroll All Values
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Gaia's Calibration - Reroll All Values")
    class GaiasCalibrationGearTests {

        @Test
        @DisplayName("Should reroll modifier values on gear")
        void shouldRerollValues() {
            GearData gear = createTestGear();
            StoneActionResult result = registry.execute(StoneType.GAIAS_CALIBRATION, gear, random);

            assertTrue(result.success());
            assertTrue(result.stoneConsumed());
            assertNotNull(result.modifiedItem());
            assertInstanceOf(GearData.class, result.modifiedItem());

            GearData modified = (GearData) result.modifiedItem();
            assertEquals(2, modified.modifierCount());
            // Stat IDs should be preserved
            assertEquals("physical_damage", modified.prefixes().get(0).statId());
            assertEquals("max_health", modified.suffixes().get(0).statId());
        }

        @Test
        @DisplayName("Should not reroll locked modifiers on gear")
        void shouldNotRerollLocked() {
            GearData gear = createGearWithLockedMod();
            double lockedValue = gear.prefixes().get(0).value(); // 10.0

            StoneActionResult result = registry.execute(StoneType.GAIAS_CALIBRATION, gear, random);

            assertTrue(result.success());
            GearData modified = (GearData) result.modifiedItem();

            // Locked prefix should keep its value
            assertEquals(lockedValue, modified.prefixes().get(0).value());
            assertTrue(modified.prefixes().get(0).locked());
        }

        @Test
        @DisplayName("Should fail on empty gear")
        void shouldFailOnEmptyGear() {
            GearData gear = createEmptyGear();
            StoneActionResult result = registry.execute(StoneType.GAIAS_CALIBRATION, gear, random);

            assertFalse(result.success());
            assertFalse(result.stoneConsumed());
        }

        @Test
        @DisplayName("Should fail on corrupted gear")
        void shouldFailOnCorruptedGear() {
            GearData gear = createCorruptedGear();
            StoneActionResult result = registry.execute(StoneType.GAIAS_CALIBRATION, gear, random);

            assertFalse(result.success());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // EMBER OF TUNING - Reroll One Value
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Ember of Tuning - Reroll One Value")
    class EmberOfTuningGearTests {

        @Test
        @DisplayName("Should reroll one modifier value")
        void shouldRerollOneValue() {
            GearData gear = createTestGear();
            StoneActionResult result = registry.execute(StoneType.EMBER_OF_TUNING, gear, random);

            assertTrue(result.success());
            GearData modified = (GearData) result.modifiedItem();
            assertEquals(2, modified.modifierCount());
        }

        @Test
        @DisplayName("Should not reroll locked modifiers")
        void shouldNotRerollLocked() {
            // Create gear with only one locked modifier
            GearData gear = new GearData(
                null, 50, GearRarity.RARE, 50,
                List.of(new GearModifier("sharp", "Sharp", ModifierType.PREFIX,
                    "physical_damage", GearModifier.STAT_TYPE_FLAT, 10.0, true)),
                List.of(),
                false, null, null, null,
                null, List.of(), 0
            );

            StoneActionResult result = registry.execute(StoneType.EMBER_OF_TUNING, gear, random);
            assertFalse(result.success()); // No unlocked modifiers to reroll
        }

        @Test
        @DisplayName("Should fail on empty gear")
        void shouldFailOnEmptyGear() {
            GearData gear = createEmptyGear();
            StoneActionResult result = registry.execute(StoneType.EMBER_OF_TUNING, gear, random);
            assertFalse(result.success());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ALTERVERSE SHARD - Reroll Types
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Alterverse Shard - Reroll Types")
    class AlterverseShardGearTests {

        @Test
        @DisplayName("Should reroll modifier stat types")
        void shouldRerollTypes() {
            GearData gear = createTestGear();

            // Run multiple times to verify types can change
            Set<String> seenStatIds = new HashSet<>();
            for (int i = 0; i < 20; i++) {
                Random r = new Random(i * 1000);
                StoneActionResult result = registry.execute(StoneType.ALTERVERSE_SHARD, gear, r);
                assertTrue(result.success());

                GearData modified = (GearData) result.modifiedItem();
                for (GearModifier mod : modified.allModifiers()) {
                    seenStatIds.add(mod.statId());
                }
            }

            // Should see variety of stats
            assertTrue(seenStatIds.size() > 2, "Should see variety of stat types");
        }

        @Test
        @DisplayName("Should preserve locked modifiers")
        void shouldPreserveLocked() {
            GearData gear = createGearWithLockedMod();

            for (int i = 0; i < 10; i++) {
                Random r = new Random(i * 1000);
                StoneActionResult result = registry.execute(StoneType.ALTERVERSE_SHARD, gear, r);
                assertTrue(result.success());

                GearData modified = (GearData) result.modifiedItem();
                // Locked prefix should remain unchanged
                assertTrue(modified.prefixes().stream()
                    .anyMatch(m -> m.statId().equals("physical_damage") && m.locked()));
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ORBISIAN BLESSING - Reroll Quality
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Orbisian Blessing - Reroll Quality")
    class OrbisianBlessingGearTests {

        @Test
        @DisplayName("Should reroll gear quality")
        void shouldRerollQuality() {
            GearData gear = createTestGear();

            Set<Integer> qualities = new HashSet<>();
            for (int i = 0; i < 50; i++) {
                Random r = new Random(i * 1000);
                StoneActionResult result = registry.execute(StoneType.ORBISIAN_BLESSING, gear, r);
                if (result.success()) {
                    GearData modified = (GearData) result.modifiedItem();
                    qualities.add(modified.quality());
                }
            }

            assertTrue(qualities.size() > 5, "Should produce variety of quality values");
        }

        @Test
        @DisplayName("Should fail on corrupted gear")
        void shouldFailOnCorrupted() {
            GearData gear = createCorruptedGear();
            StoneActionResult result = registry.execute(StoneType.ORBISIAN_BLESSING, gear, random);
            assertFalse(result.success());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // GAIA'S GIFT - Add Modifier
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Gaia's Gift - Add Modifier")
    class GaiasGiftGearTests {

        @Test
        @DisplayName("Should add a modifier when below max")
        void shouldAddModifier() {
            GearData gear = createTestGear(); // Has 2 modifiers, rare allows 4
            StoneActionResult result = registry.execute(StoneType.GAIAS_GIFT, gear, random);

            assertTrue(result.success());
            GearData modified = (GearData) result.modifiedItem();
            assertEquals(3, modified.modifierCount());
        }

        @Test
        @DisplayName("Should fail when at max modifiers")
        void shouldFailAtMax() {
            GearData gear = createMaxModGear(GearRarity.RARE);
            StoneActionResult result = registry.execute(StoneType.GAIAS_GIFT, gear, random);
            assertFalse(result.success());
        }

        @Test
        @DisplayName("Should fail on corrupted gear")
        void shouldFailOnCorrupted() {
            GearData gear = createCorruptedGear();
            StoneActionResult result = registry.execute(StoneType.GAIAS_GIFT, gear, random);
            assertFalse(result.success());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // TIER UPGRADE STONES
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Spark of Potential - Common to Uncommon")
    class SparkOfPotentialGearTests {

        @Test
        @DisplayName("Should upgrade Common to Uncommon and add modifier")
        void shouldUpgradeCommonToUncommon() {
            // Note: GearRarity.COMMON has maxModifiers=1, so we need gear with 0 modifiers
            // for canAddModifier() to return true before the upgrade
            GearData gear = new GearData(
                null, 50, GearRarity.COMMON, 50,
                List.of(),
                List.of(),
                false, null, null, null,
                null, List.of(), 0
            );

            StoneActionResult result = registry.execute(StoneType.SPARK_OF_POTENTIAL, gear, random);

            assertTrue(result.success());
            GearData modified = (GearData) result.modifiedItem();
            assertEquals(GearRarity.UNCOMMON, modified.rarity());
            assertEquals(1, modified.modifierCount()); // Added 1 new modifier
        }

        @Test
        @DisplayName("Should fail on non-Common rarity")
        void shouldFailOnNonCommon() {
            GearData gear = createTestGear(); // RARE rarity
            StoneActionResult result = registry.execute(StoneType.SPARK_OF_POTENTIAL, gear, random);
            assertFalse(result.success());
        }
    }

    @Nested
    @DisplayName("Core of Ascension - Uncommon to Rare")
    class CoreOfAscensionGearTests {

        @Test
        @DisplayName("Should upgrade Uncommon to Rare and add modifier")
        void shouldUpgradeUncommonToRare() {
            // Note: GearRarity.UNCOMMON has maxModifiers=2, so we use 1 modifier
            GearData gear = new GearData(
                null, 50, GearRarity.UNCOMMON, 50,
                List.of(GearModifier.of("sharp", "Sharp", ModifierType.PREFIX,
                    "physical_damage", GearModifier.STAT_TYPE_FLAT, 10.0)),
                List.of(),
                false, null, null, null,
                null, List.of(), 0
            );

            StoneActionResult result = registry.execute(StoneType.CORE_OF_ASCENSION, gear, random);

            assertTrue(result.success());
            GearData modified = (GearData) result.modifiedItem();
            assertEquals(GearRarity.RARE, modified.rarity());
            assertEquals(2, modified.modifierCount()); // Original 1 + 1 new
        }

        @Test
        @DisplayName("Should fail on non-Uncommon rarity")
        void shouldFailOnNonUncommon() {
            GearData gear = createTestGear(); // RARE rarity
            StoneActionResult result = registry.execute(StoneType.CORE_OF_ASCENSION, gear, random);
            assertFalse(result.success());
        }
    }

    @Nested
    @DisplayName("Heart of Legends - Rare to Epic")
    class HeartOfLegendsGearTests {

        @Test
        @DisplayName("Should upgrade Rare to Epic and add modifier")
        void shouldUpgradeRareToEpic() {
            // Note: GearRarity.RARE has maxModifiers=3, so we use 2 modifiers
            GearData gear = new GearData(
                null, 50, GearRarity.RARE, 50,
                List.of(GearModifier.of("sharp", "Sharp", ModifierType.PREFIX,
                    "physical_damage", GearModifier.STAT_TYPE_FLAT, 10.0)),
                List.of(GearModifier.of("of_might", "of Might", ModifierType.SUFFIX,
                    "strength", GearModifier.STAT_TYPE_FLAT, 5.0)),
                false, null, null, null,
                null, List.of(), 0
            );

            StoneActionResult result = registry.execute(StoneType.HEART_OF_LEGENDS, gear, random);

            assertTrue(result.success());
            GearData modified = (GearData) result.modifiedItem();
            assertEquals(GearRarity.EPIC, modified.rarity());
            assertEquals(3, modified.modifierCount()); // Original 2 + 1 new
        }

        @Test
        @DisplayName("Should fail on non-Rare rarity")
        void shouldFailOnNonRare() {
            GearData gear = new GearData(
                null, 50, GearRarity.EPIC, 50,
                List.of(GearModifier.of("sharp", "Sharp", ModifierType.PREFIX,
                    "physical_damage", GearModifier.STAT_TYPE_FLAT, 10.0)),
                List.of(),
                false, null, null, null,
                null, List.of(), 0
            );
            StoneActionResult result = registry.execute(StoneType.HEART_OF_LEGENDS, gear, random);
            assertFalse(result.success());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // PURGING EMBER - Clear All Unlocked
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Purging Ember - Clear All Unlocked")
    class PurgingEmberGearTests {

        @Test
        @DisplayName("Should remove all unlocked modifiers")
        void shouldRemoveAllUnlocked() {
            GearData gear = createTestGear();
            StoneActionResult result = registry.execute(StoneType.PURGING_EMBER, gear, random);

            assertTrue(result.success());
            GearData modified = (GearData) result.modifiedItem();
            assertEquals(0, modified.modifierCount());
        }

        @Test
        @DisplayName("Should preserve locked modifiers")
        void shouldPreserveLocked() {
            GearData gear = createGearWithLockedMod();
            StoneActionResult result = registry.execute(StoneType.PURGING_EMBER, gear, random);

            assertTrue(result.success());
            GearData modified = (GearData) result.modifiedItem();
            assertEquals(1, modified.modifierCount());
            assertTrue(modified.prefixes().get(0).locked());
        }

        @Test
        @DisplayName("Should fail on corrupted gear")
        void shouldFailOnCorrupted() {
            GearData gear = createCorruptedGear();
            StoneActionResult result = registry.execute(StoneType.PURGING_EMBER, gear, random);
            assertFalse(result.success());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // EROSION SHARD - Remove One
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Erosion Shard - Remove One")
    class ErosionShardGearTests {

        @Test
        @DisplayName("Should remove one random modifier")
        void shouldRemoveOne() {
            GearData gear = createTestGear();
            StoneActionResult result = registry.execute(StoneType.EROSION_SHARD, gear, random);

            assertTrue(result.success());
            GearData modified = (GearData) result.modifiedItem();
            assertEquals(1, modified.modifierCount());
        }

        @Test
        @DisplayName("Should only remove unlocked modifiers")
        void shouldOnlyRemoveUnlocked() {
            GearData gear = createGearWithLockedMod();
            StoneActionResult result = registry.execute(StoneType.EROSION_SHARD, gear, random);

            assertTrue(result.success());
            GearData modified = (GearData) result.modifiedItem();
            // The locked one should remain
            assertEquals(1, modified.modifierCount());
            assertTrue(modified.prefixes().get(0).locked());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // TRANSMUTATION CRYSTAL - Atomic Swap
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Transmutation Crystal - Atomic Swap")
    class TransmutationCrystalGearTests {

        @Test
        @DisplayName("Should remove one modifier and add one")
        void shouldSwapModifier() {
            GearData gear = createTestGear();
            int originalCount = gear.modifierCount();

            StoneActionResult result = registry.execute(StoneType.TRANSMUTATION_CRYSTAL, gear, random);

            assertTrue(result.success());
            GearData modified = (GearData) result.modifiedItem();
            // Count should remain same (remove one, add one)
            assertEquals(originalCount, modified.modifierCount());
        }

        @Test
        @DisplayName("Should not remove locked modifiers")
        void shouldNotRemoveLocked() {
            GearData gear = createGearWithLockedMod();

            StoneActionResult result = registry.execute(StoneType.TRANSMUTATION_CRYSTAL, gear, random);

            assertTrue(result.success());
            GearData modified = (GearData) result.modifiedItem();
            // Locked modifier should still be there
            assertTrue(modified.prefixes().stream()
                .anyMatch(m -> m.statId().equals("physical_damage") && m.locked()));
        }

        @Test
        @DisplayName("Should fail on gear with no unlocked modifiers")
        void shouldFailOnNoUnlocked() {
            GearData gear = new GearData(
                null, 50, GearRarity.RARE, 50,
                List.of(new GearModifier("sharp", "Sharp", ModifierType.PREFIX,
                    "physical_damage", GearModifier.STAT_TYPE_FLAT, 10.0, true)),
                List.of(),
                false, null, null, null,
                null, List.of(), 0
            );

            StoneActionResult result = registry.execute(StoneType.TRANSMUTATION_CRYSTAL, gear, random);
            assertFalse(result.success());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // WARDEN'S SEAL/KEY - Lock/Unlock
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Warden's Seal/Key - Lock/Unlock")
    class LockGearTests {

        @Test
        @DisplayName("Warden's Seal should lock a random modifier")
        void sealShouldLockModifier() {
            GearData gear = createTestGear();

            StoneActionResult result = registry.execute(StoneType.WARDENS_SEAL, gear, random);

            assertTrue(result.success());
            GearData modified = (GearData) result.modifiedItem();
            long lockedCount = modified.modifiers().stream().filter(m -> m.isLocked()).count();
            assertEquals(1, lockedCount);
        }

        @Test
        @DisplayName("Warden's Key should unlock a random modifier")
        void keyShouldUnlockModifier() {
            GearData gear = createGearWithLockedMod();

            StoneActionResult result = registry.execute(StoneType.WARDENS_KEY, gear, random);

            assertTrue(result.success());
            GearData modified = (GearData) result.modifiedItem();
            long lockedCount = modified.modifiers().stream().filter(m -> m.isLocked()).count();
            assertEquals(0, lockedCount);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // THRESHOLD STONE - Reroll Level
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Threshold Stone - Reroll Level")
    class ThresholdStoneGearTests {

        @Test
        @DisplayName("Should reroll level within ±3")
        void shouldRerollLevelWithinRange() {
            GearData gear = new GearData(
                null, 50, GearRarity.RARE, 50,
                List.of(), List.of(),
                false, null, null, null,
                null, List.of(), 0
            );

            Set<Integer> seenLevels = new HashSet<>();
            for (int i = 0; i < 50; i++) {
                Random r = new Random(i * 1000);
                StoneActionResult result = registry.execute(StoneType.THRESHOLD_STONE, gear, r);

                assertTrue(result.success());
                GearData modified = (GearData) result.modifiedItem();

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
            GearData gear = new GearData(
                null, 2, GearRarity.RARE, 50,
                List.of(), List.of(),
                false, null, null, null,
                null, List.of(), 0
            );

            for (int i = 0; i < 20; i++) {
                Random r = new Random(i * 1000);
                StoneActionResult result = registry.execute(StoneType.THRESHOLD_STONE, gear, r);

                assertTrue(result.success());
                GearData modified = (GearData) result.modifiedItem();

                assertTrue(modified.level() >= 1, "Level should not go below 1");
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // VARYN'S TOUCH - Corruption
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Varyn's Touch - Corruption")
    class VarynsTouchGearTests {

        @Test
        @DisplayName("Should corrupt the gear")
        void shouldCorruptGear() {
            GearData gear = createTestGear();
            StoneActionResult result = registry.execute(StoneType.VARYNS_TOUCH, gear, random);

            assertTrue(result.success());
            GearData modified = (GearData) result.modifiedItem();
            assertTrue(modified.corrupted());
        }

        @Test
        @DisplayName("Should fail on already corrupted gear")
        void shouldFailOnCorrupted() {
            GearData gear = createCorruptedGear();
            StoneActionResult result = registry.execute(StoneType.VARYNS_TOUCH, gear, random);

            assertFalse(result.success());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // GAIA'S PERFECTION - Perfect Quality
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Gaia's Perfection - Perfect Quality")
    class GaiasPerfectionGearTests {

        @Test
        @DisplayName("Should set perfect quality (101)")
        void shouldSetPerfectQuality() {
            GearData gear = createTestGear();
            StoneActionResult result = registry.execute(StoneType.GAIAS_PERFECTION, gear, random);

            assertTrue(result.success());
            GearData modified = (GearData) result.modifiedItem();
            assertEquals(101, modified.quality());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // LOREKEEPER'S SCROLL - Not Applicable to Gear
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Lorekeeper's Scroll - Not Applicable")
    class LorekeepersScrollGearTests {

        @Test
        @DisplayName("Should fail on gear (gear doesn't need identification)")
        void shouldFailOnGear() {
            GearData gear = createTestGear();
            StoneActionResult result = registry.execute(StoneType.LOREKEEPERS_SCROLL, gear, random);

            assertFalse(result.success());
            assertTrue(result.message().contains("identification") ||
                       result.message().contains("Gear"),
                "Message should indicate gear doesn't need identification");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // GENESIS STONE - Fill All Remaining Slots
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Genesis Stone - Fill All Slots")
    class GenesisStoneGearTests {

        @Test
        @DisplayName("Should fill all remaining slots on empty gear")
        void shouldFillAllSlotsOnEmpty() {
            GearData gear = createEmptyGear(); // COMMON allows 1 modifier
            StoneActionResult result = registry.execute(StoneType.GENESIS_STONE, gear, random);

            assertTrue(result.success());
            GearData modified = (GearData) result.modifiedItem();
            // COMMON allows 1 modifier
            assertEquals(1, modified.modifierCount());
        }

        @Test
        @DisplayName("Should fill remaining slots on partially filled gear")
        void shouldFillRemainingSlotsOnPartial() {
            GearData gear = createTestGear(); // RARE with 2 modifiers, allows 3
            StoneActionResult result = registry.execute(StoneType.GENESIS_STONE, gear, random);

            assertTrue(result.success());
            GearData modified = (GearData) result.modifiedItem();
            // Should fill to max (3 for RARE)
            assertEquals(3, modified.modifierCount());
        }

        @Test
        @DisplayName("Should fail on gear at max modifiers")
        void shouldFailOnGearAtMax() {
            GearData gear = createMaxModGear(GearRarity.RARE);
            StoneActionResult result = registry.execute(StoneType.GENESIS_STONE, gear, random);

            assertFalse(result.success());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CORRUPTION HANDLING
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Corrupted Gear Handling")
    class CorruptionGearTests {

        @Test
        @DisplayName("Stones that don't work on corrupted should fail")
        void stonesShouldFailOnCorrupted() {
            GearData corrupted = createCorruptedGear();

            // These stones cannot be used on corrupted items
            StoneType[] nonCorruptStones = {
                StoneType.GAIAS_CALIBRATION,
                StoneType.ALTERVERSE_SHARD,
                StoneType.GAIAS_GIFT,
                StoneType.PURGING_EMBER,
                StoneType.ORBISIAN_BLESSING
            };

            for (StoneType type : nonCorruptStones) {
                StoneActionResult result = registry.execute(type, corrupted, random);
                assertFalse(result.success(), type + " should fail on corrupted gear");
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // MAP-ONLY STONES (verify they fail on gear)
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Map-Only Stones Should Fail on Gear")
    class MapOnlyStoneTests {

        @Test
        @DisplayName("Cartographer's Polish should fail on gear")
        void cartographersShouldFailOnGear() {
            GearData gear = createTestGear();
            StoneActionResult result = registry.execute(StoneType.CARTOGRAPHERS_POLISH, gear, random);
            assertFalse(result.success());
        }

        @Test
        @DisplayName("Fortune's Compass should fail on gear")
        void fortunesShouldFailOnGear() {
            GearData gear = createTestGear();
            StoneActionResult result = registry.execute(StoneType.FORTUNES_COMPASS, gear, random);
            assertFalse(result.success());
        }

        @Test
        @DisplayName("Alterverse Key should fail on gear")
        void alterverseKeyShouldFailOnGear() {
            GearData gear = createTestGear();
            StoneActionResult result = registry.execute(StoneType.ALTERVERSE_KEY, gear, random);
            assertFalse(result.success());
        }
    }
}
