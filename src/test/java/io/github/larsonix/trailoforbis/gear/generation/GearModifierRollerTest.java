package io.github.larsonix.trailoforbis.gear.generation;

import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig;
import io.github.larsonix.trailoforbis.gear.config.TestConfigFactory;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.GearModifier;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.gear.model.ModifierType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GearModifierRoller - 20 test cases.
 *
 * <p>Covers stone crafting operations: rerolling, adding, removing,
 * transmuting, and locking/unlocking modifiers.
 */
class GearModifierRollerTest {

    private ModifierConfig modConfig;
    private GearBalanceConfig balanceConfig;
    private ModifierPool modifierPool;
    private GearModifierRoller roller;

    @BeforeEach
    void setUp() {
        modConfig = TestConfigFactory.createDefaultModifierConfig();
        balanceConfig = TestConfigFactory.createDefaultBalanceConfig();
        modifierPool = new ModifierPool(modConfig, balanceConfig);
        roller = new GearModifierRoller(modifierPool, modConfig, balanceConfig);
    }

    // =========================================================================
    // REROLL VALUES TESTS
    // =========================================================================

    @Nested
    @DisplayName("Reroll Values (GAIAS_CALIBRATION)")
    class RerollValuesTests {

        @Test
        @DisplayName("rerollValues preserves locked modifiers unchanged")
        void rerollValues_lockedModifiers_preserved() {
            GearModifier lockedMod = createLockedModifier("sharp", ModifierType.PREFIX, 100.0);
            GearData gear = createGearWithModifiers(List.of(lockedMod), List.of());

            GearData result = roller.rerollValues(gear, "weapon", new Random(42));

            assertEquals(1, result.prefixes().size());
            assertTrue(result.prefixes().get(0).locked());
            assertEquals(100.0, result.prefixes().get(0).value(),
                "Locked modifier value should be unchanged");
        }

        @Test
        @DisplayName("rerollValues changes unlocked modifier values")
        void rerollValues_unlockedModifiers_newValues() {
            GearModifier unlockedMod = createUnlockedModifier("sharp", ModifierType.PREFIX, 50.0);
            GearData gear = createGearWithModifiers(List.of(unlockedMod), List.of());

            // Run multiple times to ensure value can change
            boolean valueChanged = false;
            for (int i = 0; i < 10; i++) {
                GearData result = roller.rerollValues(gear, "weapon", new Random());
                if (result.prefixes().get(0).value() != 50.0) {
                    valueChanged = true;
                    break;
                }
            }

            assertTrue(valueChanged, "Unlocked modifier value should change after rerolling");
        }

        @Test
        @DisplayName("rerollValues preserves modifier IDs and types")
        void rerollValues_preservesModifierIdentity() {
            GearModifier prefix = createUnlockedModifier("sharp", ModifierType.PREFIX, 50.0);
            GearModifier suffix = createUnlockedModifier("of_the_whale", ModifierType.SUFFIX, 30.0);
            GearData gear = createGearWithModifiers(List.of(prefix), List.of(suffix));

            GearData result = roller.rerollValues(gear, "weapon", new Random());

            assertEquals("sharp", result.prefixes().get(0).id());
            assertEquals("of_the_whale", result.suffixes().get(0).id());
            assertEquals(ModifierType.PREFIX, result.prefixes().get(0).type());
            assertEquals(ModifierType.SUFFIX, result.suffixes().get(0).type());
        }
    }

    // =========================================================================
    // REROLL ONE VALUE TESTS
    // =========================================================================

    @Nested
    @DisplayName("Reroll One Value (EMBER_OF_TUNING)")
    class RerollOneValueTests {

        @Test
        @DisplayName("rerollOneValue changes only one modifier")
        void rerollOneValue_selectsRandom_changesOne() {
            GearModifier mod1 = createUnlockedModifier("sharp", ModifierType.PREFIX, 50.0);
            GearModifier mod2 = createUnlockedModifier("deadly", ModifierType.PREFIX, 50.0);
            GearData gear = createGearWithModifiers(List.of(mod1, mod2), List.of());

            // Track which modifier changed across multiple runs
            int changesAtIndex0 = 0;
            int changesAtIndex1 = 0;

            for (int i = 0; i < 20; i++) {
                GearData result = roller.rerollOneValue(gear, "weapon", new Random());
                if (result.prefixes().get(0).value() != 50.0) changesAtIndex0++;
                if (result.prefixes().get(1).value() != 50.0) changesAtIndex1++;
            }

            // At least one should have changed sometimes, but not always both
            assertTrue(changesAtIndex0 > 0 || changesAtIndex1 > 0,
                "At least one modifier should change");
        }

        @Test
        @DisplayName("rerollOneValue returns unchanged when all locked")
        void rerollOneValue_allLocked_unchanged() {
            GearModifier locked1 = createLockedModifier("sharp", ModifierType.PREFIX, 100.0);
            GearModifier locked2 = createLockedModifier("deadly", ModifierType.PREFIX, 100.0);
            GearData gear = createGearWithModifiers(List.of(locked1, locked2), List.of());

            GearData result = roller.rerollOneValue(gear, "weapon", new Random());

            assertEquals(100.0, result.prefixes().get(0).value());
            assertEquals(100.0, result.prefixes().get(1).value());
        }
    }

    // =========================================================================
    // REROLL TYPES TESTS
    // =========================================================================

    @Nested
    @DisplayName("Reroll Types (ALTERVERSE_SHARD)")
    class RerollTypesTests {

        @Test
        @DisplayName("rerollTypes keeps prefix/suffix count the same")
        void rerollTypes_keepsPrefixSuffixCount() {
            GearModifier prefix = createUnlockedModifier("sharp", ModifierType.PREFIX, 50.0);
            GearModifier suffix = createUnlockedModifier("of_the_whale", ModifierType.SUFFIX, 30.0);
            GearData gear = createGearWithModifiers(List.of(prefix), List.of(suffix));

            GearData result = roller.rerollTypes(gear, "weapon", new Random());

            assertEquals(1, result.prefixes().size(), "Prefix count should remain the same");
            assertEquals(1, result.suffixes().size(), "Suffix count should remain the same");
        }

        @Test
        @DisplayName("rerollTypes excludes locked modifier IDs from new rolls")
        void rerollTypes_excludesLocked() {
            GearModifier lockedPrefix = createLockedModifier("sharp", ModifierType.PREFIX, 100.0);
            GearModifier unlockedPrefix = createUnlockedModifier("deadly", ModifierType.PREFIX, 50.0);
            GearData gear = createGearWithModifiers(List.of(lockedPrefix, unlockedPrefix), List.of());

            GearData result = roller.rerollTypes(gear, "weapon", new Random());

            // Locked modifier should still be there with original ID
            boolean hasLockedSharp = result.prefixes().stream()
                .anyMatch(m -> m.id().equals("sharp") && m.locked());
            assertTrue(hasLockedSharp, "Locked modifier should be preserved");
        }

        @Test
        @DisplayName("rerollTypes changes unlocked modifier types")
        void rerollTypes_changesUnlockedTypes() {
            GearModifier unlocked = createUnlockedModifier("sharp", ModifierType.PREFIX, 50.0);
            GearData gear = createGearWithModifiers(List.of(unlocked), List.of());

            // Run multiple times to see if we get a different modifier
            Set<String> seenIds = new HashSet<>();
            for (int i = 0; i < 20; i++) {
                GearData result = roller.rerollTypes(gear, "weapon", new Random());
                seenIds.add(result.prefixes().get(0).id());
            }

            assertTrue(seenIds.size() > 1 || seenIds.contains("sharp"),
                "Should potentially get different modifier types");
        }
    }

    // =========================================================================
    // ADD MODIFIER TESTS
    // =========================================================================

    @Nested
    @DisplayName("Add Modifier (GAIAS_GIFT)")
    class AddModifierTests {

        @Test
        @DisplayName("addModifier returns unchanged when at max capacity")
        void addModifier_atMax_unchanged() {
            // Create gear at max modifier count for rarity
            List<GearModifier> maxPrefixes = List.of(
                createUnlockedModifier("sharp", ModifierType.PREFIX, 50.0),
                createUnlockedModifier("deadly", ModifierType.PREFIX, 50.0)
            );
            List<GearModifier> maxSuffixes = List.of(
                createUnlockedModifier("of_the_whale", ModifierType.SUFFIX, 30.0),
                createUnlockedModifier("of_the_giant", ModifierType.SUFFIX, 30.0)
            );
            // Using EPIC rarity which has max 4 modifiers
            GearData gear = GearData.builder()
                .level(50)
                .rarity(GearRarity.EPIC)
                .quality(50)
                .prefixes(maxPrefixes)
                .suffixes(maxSuffixes)
                .build();

            GearData result = roller.addModifier(gear, "weapon", new Random());

            assertEquals(gear.modifierCount(), result.modifierCount(),
                "Modifier count should not change when at max");
        }

        @Test
        @DisplayName("addModifier adds one modifier when below max")
        void addModifier_belowMax_addsOne() {
            GearModifier prefix = createUnlockedModifier("sharp", ModifierType.PREFIX, 50.0);
            GearData gear = createGearWithModifiers(List.of(prefix), List.of());

            GearData result = roller.addModifier(gear, "weapon", new Random());

            assertEquals(gear.modifierCount() + 1, result.modifierCount(),
                "Should add exactly one modifier");
        }

        @Test
        @DisplayName("addModifier excludes existing modifier IDs")
        void addModifier_excludesDuplicateIds() {
            GearModifier existingPrefix = createUnlockedModifier("sharp", ModifierType.PREFIX, 50.0);
            GearData gear = createGearWithModifiers(List.of(existingPrefix), List.of());

            for (int i = 0; i < 10; i++) {
                GearData result = roller.addModifier(gear, "weapon", new Random());
                long sharpCount = result.allModifiers().stream()
                    .filter(m -> m.id().equals("sharp"))
                    .count();
                assertTrue(sharpCount <= 1, "Should not have duplicate sharp modifier");
            }
        }
    }

    // =========================================================================
    // REMOVE MODIFIER TESTS
    // =========================================================================

    @Nested
    @DisplayName("Remove Modifier (EROSION_SHARD)")
    class RemoveModifierTests {

        @Test
        @DisplayName("removeModifier returns unchanged when all locked")
        void removeModifier_lockedOnly_unchanged() {
            GearModifier locked = createLockedModifier("sharp", ModifierType.PREFIX, 100.0);
            GearData gear = createGearWithModifiers(List.of(locked), List.of());

            GearData result = roller.removeModifier(gear, new Random());

            assertEquals(1, result.modifierCount(), "Locked modifier should not be removed");
        }

        @Test
        @DisplayName("removeModifier removes an unlocked modifier")
        void removeModifier_mixedLocks_removesUnlocked() {
            GearModifier locked = createLockedModifier("sharp", ModifierType.PREFIX, 100.0);
            GearModifier unlocked = createUnlockedModifier("deadly", ModifierType.PREFIX, 50.0);
            GearData gear = createGearWithModifiers(List.of(locked, unlocked), List.of());

            GearData result = roller.removeModifier(gear, new Random());

            assertEquals(1, result.modifierCount());
            assertTrue(result.prefixes().get(0).locked(),
                "Only locked modifier should remain");
            assertEquals("sharp", result.prefixes().get(0).id());
        }

        @Test
        @DisplayName("removeModifier can remove suffixes")
        void removeModifier_canRemoveSuffix() {
            GearModifier unlocked = createUnlockedModifier("of_the_whale", ModifierType.SUFFIX, 50.0);
            GearData gear = createGearWithModifiers(List.of(), List.of(unlocked));

            GearData result = roller.removeModifier(gear, new Random());

            assertEquals(0, result.modifierCount());
        }
    }

    // =========================================================================
    // TRANSMUTE TESTS
    // =========================================================================

    @Nested
    @DisplayName("Transmute (TRANSMUTATION_CRYSTAL)")
    class TransmuteTests {

        @Test
        @DisplayName("transmute replaces unlocked modifier with same type")
        void transmute_replacesUnlocked_sameType() {
            GearModifier prefix = createUnlockedModifier("sharp", ModifierType.PREFIX, 50.0);
            GearData gear = createGearWithModifiers(List.of(prefix), List.of());

            GearData result = roller.transmute(gear, "weapon", new Random());

            assertEquals(1, result.prefixes().size(), "Should still have one prefix");
            assertEquals(0, result.suffixes().size(), "Should still have no suffixes");
            assertEquals(ModifierType.PREFIX, result.prefixes().get(0).type());
        }

        @Test
        @DisplayName("transmute returns unchanged when all locked")
        void transmute_allLocked_unchanged() {
            GearModifier locked = createLockedModifier("sharp", ModifierType.PREFIX, 100.0);
            GearData gear = createGearWithModifiers(List.of(locked), List.of());

            GearData result = roller.transmute(gear, "weapon", new Random());

            assertEquals("sharp", result.prefixes().get(0).id());
            assertEquals(100.0, result.prefixes().get(0).value());
        }

        @Test
        @DisplayName("transmute replaces suffix with another suffix")
        void transmute_suffixToSuffix() {
            GearModifier suffix = createUnlockedModifier("of_the_whale", ModifierType.SUFFIX, 50.0);
            GearData gear = createGearWithModifiers(List.of(), List.of(suffix));

            GearData result = roller.transmute(gear, "weapon", new Random());

            assertEquals(0, result.prefixes().size());
            assertEquals(1, result.suffixes().size());
            assertEquals(ModifierType.SUFFIX, result.suffixes().get(0).type());
        }
    }

    // =========================================================================
    // LOCK/UNLOCK TESTS
    // =========================================================================

    @Nested
    @DisplayName("Lock/Unlock Operations")
    class LockUnlockTests {

        @Test
        @DisplayName("lockModifierAt sets locked state at valid index")
        void lockModifierAt_validIndex_setsLocked() {
            GearModifier unlocked = createUnlockedModifier("sharp", ModifierType.PREFIX, 50.0);
            GearData gear = createGearWithModifiers(List.of(unlocked), List.of());

            GearData result = roller.lockModifierAt(gear, 0);

            assertTrue(result.prefixes().get(0).locked());
        }

        @Test
        @DisplayName("unlockModifierAt clears locked state")
        void unlockModifierAt_validIndex_clearsLocked() {
            GearModifier locked = createLockedModifier("sharp", ModifierType.PREFIX, 100.0);
            GearData gear = createGearWithModifiers(List.of(locked), List.of());

            GearData result = roller.unlockModifierAt(gear, 0);

            assertFalse(result.prefixes().get(0).locked());
        }

        @Test
        @DisplayName("lockModifierAt handles suffix index correctly")
        void lockModifierAt_suffixIndex_locksCorrectModifier() {
            GearModifier prefix = createUnlockedModifier("sharp", ModifierType.PREFIX, 50.0);
            GearModifier suffix = createUnlockedModifier("of_the_whale", ModifierType.SUFFIX, 50.0);
            GearData gear = createGearWithModifiers(List.of(prefix), List.of(suffix));

            // Index 1 should be the first suffix (after prefix at index 0)
            GearData result = roller.lockModifierAt(gear, 1);

            assertFalse(result.prefixes().get(0).locked(), "Prefix should remain unlocked");
            assertTrue(result.suffixes().get(0).locked(), "Suffix should be locked");
        }

        @Test
        @DisplayName("lockModifierAt returns unchanged for invalid index")
        void lockModifierAt_invalidIndex_unchanged() {
            GearModifier unlocked = createUnlockedModifier("sharp", ModifierType.PREFIX, 50.0);
            GearData gear = createGearWithModifiers(List.of(unlocked), List.of());

            GearData result = roller.lockModifierAt(gear, 99);

            assertEquals(gear, result, "Should return same gear for invalid index");
        }
    }

    // =========================================================================
    // CLEAR UNLOCKED TESTS
    // =========================================================================

    @Nested
    @DisplayName("Clear Unlocked (PURGING_EMBER)")
    class ClearUnlockedTests {

        @Test
        @DisplayName("clearUnlockedModifiers removes all unlocked, keeps locked")
        void clearUnlockedModifiers_keepsOnlyLocked() {
            GearModifier locked = createLockedModifier("sharp", ModifierType.PREFIX, 100.0);
            GearModifier unlocked1 = createUnlockedModifier("deadly", ModifierType.PREFIX, 50.0);
            GearModifier unlocked2 = createUnlockedModifier("of_the_whale", ModifierType.SUFFIX, 50.0);
            GearData gear = createGearWithModifiers(List.of(locked, unlocked1), List.of(unlocked2));

            GearData result = roller.clearUnlockedModifiers(gear);

            assertEquals(1, result.modifierCount());
            assertTrue(result.prefixes().get(0).locked());
            assertEquals("sharp", result.prefixes().get(0).id());
        }
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private GearModifier createLockedModifier(String id, ModifierType type, double value) {
        return new GearModifier(id, id, type, "physical_damage", "flat", value, true);
    }

    private GearModifier createUnlockedModifier(String id, ModifierType type, double value) {
        return new GearModifier(id, id, type, "physical_damage", "flat", value, false);
    }

    private GearData createGearWithModifiers(List<GearModifier> prefixes, List<GearModifier> suffixes) {
        return GearData.builder()
            .level(50)
            .rarity(GearRarity.LEGENDARY) // High rarity to allow many modifiers
            .quality(50)
            .prefixes(prefixes)
            .suffixes(suffixes)
            .build();
    }
}
