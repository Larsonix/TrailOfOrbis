package io.github.larsonix.trailoforbis.maps.modifiers;

import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.maps.config.RealmModifierConfig;
import io.github.larsonix.trailoforbis.maps.config.RealmModifierConfig.ModifierSettings;
import io.github.larsonix.trailoforbis.util.LevelScaling;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifierRoller.RollResult;

/**
 * Unit tests for {@link RealmModifierRoller}.
 */
@DisplayName("RealmModifierRoller")
class RealmModifierRollerTest {

    /** Default test level used by most existing tests. */
    private static final int TEST_LEVEL = 50;

    private RealmModifierConfig config;
    private RealmModifierPool pool;
    private RealmModifierRoller roller;

    @BeforeEach
    void setUp() {
        // Ensure default scaling config (public API)
        LevelScaling.configure(100, 2.0, 5.0);
        config = new RealmModifierConfig();
        pool = new RealmModifierPool(config);
        roller = new RealmModifierRoller(pool);
    }

    @AfterEach
    void cleanUp() {
        LevelScaling.configure(100, 2.0, 5.0);
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("Should reject null pool")
        void shouldRejectNullPool() {
            assertThrows(NullPointerException.class,
                () -> new RealmModifierRoller((RealmModifierPool) null));
        }

        @Test
        @DisplayName("Should accept config directly")
        void shouldAcceptConfigDirectly() {
            RealmModifierRoller fromConfig = new RealmModifierRoller(config);
            assertNotNull(fromConfig.getPool());
            assertNotNull(fromConfig.getConfig());
        }
    }

    @Nested
    @DisplayName("rollModifiersSplit(GearRarity)")
    class RollModifiersByRarityTests {

        @Test
        @DisplayName("Should return prefixes and suffixes based on rarity")
        void shouldReturnModifiersBasedOnRarity() {
            Random random = new Random(42);

            // Common: 0-1 prefixes + 0-1 suffixes = 0-2 total
            RollResult common = roller.rollModifiersSplit(GearRarity.COMMON, random, TEST_LEVEL);
            assertTrue(common.prefixes().size() <= 1,
                "Common should have 0-1 prefixes, got " + common.prefixes().size());
            assertTrue(common.suffixes().size() <= 1,
                "Common should have 0-1 suffixes, got " + common.suffixes().size());

            // Mythic: exactly 3 prefixes + 3 suffixes = 6 total
            RollResult mythic = roller.rollModifiersSplit(GearRarity.MYTHIC, random, TEST_LEVEL);
            assertEquals(3, mythic.prefixes().size(), "Mythic should have 3 prefixes");
            assertEquals(3, mythic.suffixes().size(), "Mythic should have 3 suffixes");
        }

        @Test
        @DisplayName("Higher rarity should have more modifiers on average")
        void higherRarityShouldHaveMoreModifiers() {
            Random random = new Random(12345);
            int iterations = 100;

            double commonAvg = 0, rareAvg = 0, epicAvg = 0;
            for (int i = 0; i < iterations; i++) {
                commonAvg += roller.rollModifiersSplit(GearRarity.COMMON, random, TEST_LEVEL).totalCount();
                rareAvg += roller.rollModifiersSplit(GearRarity.RARE, random, TEST_LEVEL).totalCount();
                epicAvg += roller.rollModifiersSplit(GearRarity.EPIC, random, TEST_LEVEL).totalCount();
            }
            commonAvg /= iterations;
            rareAvg /= iterations;
            epicAvg /= iterations;

            assertTrue(commonAvg < rareAvg,
                "Common avg (" + commonAvg + ") should be less than Rare avg (" + rareAvg + ")");
            assertTrue(rareAvg < epicAvg,
                "Rare avg (" + rareAvg + ") should be less than Epic avg (" + epicAvg + ")");
        }

        @Test
        @DisplayName("Prefixes should all be prefix types, suffixes should all be suffix types")
        void shouldSeparatePrefixesAndSuffixes() {
            Random random = new Random(42);
            RollResult result = roller.rollModifiersSplit(GearRarity.LEGENDARY, random, TEST_LEVEL);

            for (RealmModifier mod : result.prefixes()) {
                assertTrue(mod.type().isPrefix(),
                    mod.type() + " in prefixes list should be a prefix type");
            }
            for (RealmModifier mod : result.suffixes()) {
                assertTrue(mod.type().isSuffix(),
                    mod.type() + " in suffixes list should be a suffix type");
            }
        }

        @Test
        @DisplayName("Should not have duplicate modifier types within each pool")
        void shouldNotHaveDuplicateTypes() {
            Random random = new Random(42);
            RollResult result = roller.rollModifiersSplit(GearRarity.MYTHIC, random, TEST_LEVEL);

            Set<RealmModifierType> prefixTypes = new HashSet<>();
            for (RealmModifier mod : result.prefixes()) {
                assertTrue(prefixTypes.add(mod.type()),
                    "Duplicate prefix type: " + mod.type());
            }

            Set<RealmModifierType> suffixTypes = new HashSet<>();
            for (RealmModifier mod : result.suffixes()) {
                assertTrue(suffixTypes.add(mod.type()),
                    "Duplicate suffix type: " + mod.type());
            }
        }
    }

    @Nested
    @DisplayName("rollModifier (single)")
    class RollSingleModifierTests {

        @Test
        @DisplayName("Should return modifier of correct type")
        void shouldReturnCorrectType() {
            RealmModifier mod = roller.rollModifier(RealmModifierType.MONSTER_DAMAGE, new Random(42), TEST_LEVEL);
            assertEquals(RealmModifierType.MONSTER_DAMAGE, mod.type());
        }

        @Test
        @DisplayName("Should return value within configured range for level")
        void shouldReturnValueWithinRange() {
            Random random = new Random(12345);
            ModifierSettings settings = config.getModifierSettings(RealmModifierType.ITEM_QUANTITY);
            int expectedMin = settings.getMinValue(TEST_LEVEL);
            int expectedMax = settings.getMaxValue(TEST_LEVEL);

            for (int i = 0; i < 100; i++) {
                RealmModifier mod = roller.rollModifier(RealmModifierType.ITEM_QUANTITY, random, TEST_LEVEL);
                assertTrue(mod.value() >= expectedMin && mod.value() <= expectedMax,
                    "Value should be " + expectedMin + "-" + expectedMax + ", got " + mod.value());
            }
        }

        @Test
        @DisplayName("Should return unlocked modifier")
        void shouldReturnUnlockedModifier() {
            RealmModifier mod = roller.rollModifier(RealmModifierType.MONSTER_HEALTH, new Random(42), TEST_LEVEL);
            assertFalse(mod.locked());
        }
    }

    @Nested
    @DisplayName("rerollValues")
    class RerollValuesTests {

        @Test
        @DisplayName("Should preserve modifier types")
        void shouldPreserveTypes() {
            List<RealmModifier> original = List.of(
                RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 30),
                RealmModifier.of(RealmModifierType.ITEM_QUANTITY, 15)
            );

            List<RealmModifier> rerolled = roller.rerollValues(original, new Random(42), TEST_LEVEL);

            assertEquals(2, rerolled.size());
            assertEquals(RealmModifierType.MONSTER_DAMAGE, rerolled.get(0).type());
            assertEquals(RealmModifierType.ITEM_QUANTITY, rerolled.get(1).type());
        }

        @Test
        @DisplayName("Should not reroll locked modifiers")
        void shouldNotRerollLocked() {
            RealmModifier locked = new RealmModifier(RealmModifierType.MONSTER_DAMAGE, 30, true);
            RealmModifier unlocked = RealmModifier.of(RealmModifierType.ITEM_QUANTITY, 15);

            List<RealmModifier> rerolled = roller.rerollValues(List.of(locked, unlocked), new Random(42), TEST_LEVEL);

            assertEquals(2, rerolled.size());
            assertEquals(30, rerolled.get(0).value(), "Locked modifier value should be unchanged");
        }

        @Test
        @DisplayName("Should produce different values (statistical)")
        void shouldProduceDifferentValues() {
            List<RealmModifier> original = List.of(
                RealmModifier.of(RealmModifierType.ITEM_QUANTITY, 25)
            );

            Set<Integer> values = new HashSet<>();
            Random random = new Random(12345);
            for (int i = 0; i < 50; i++) {
                List<RealmModifier> rerolled = roller.rerollValues(original, random, TEST_LEVEL);
                values.add(rerolled.get(0).value());
            }

            assertTrue(values.size() > 3, "Should produce variety of values");
        }
    }

    @Nested
    @DisplayName("rerollTypes")
    class RerollTypesTests {

        @Test
        @DisplayName("Should preserve locked modifiers")
        void shouldPreserveLocked() {
            RealmModifier locked = new RealmModifier(RealmModifierType.MONSTER_DAMAGE, 30, true);
            RealmModifier unlocked = RealmModifier.of(RealmModifierType.ITEM_QUANTITY, 15);

            List<RealmModifier> rerolled = roller.rerollTypes(List.of(locked, unlocked), new Random(42), TEST_LEVEL);

            assertEquals(2, rerolled.size());
            // Locked modifier should be preserved
            assertTrue(rerolled.stream().anyMatch(
                m -> m.type() == RealmModifierType.MONSTER_DAMAGE && m.value() == 30 && m.locked()));
        }

        @Test
        @DisplayName("Should change unlocked modifier types")
        void shouldChangeUnlockedTypes() {
            List<RealmModifier> original = List.of(
                RealmModifier.of(RealmModifierType.ITEM_QUANTITY, 15),
                RealmModifier.of(RealmModifierType.ITEM_RARITY, 20)
            );

            Set<RealmModifierType> originalTypes = Set.of(
                RealmModifierType.ITEM_QUANTITY, RealmModifierType.ITEM_RARITY);

            // Run multiple times to check type changes statistically
            boolean foundChange = false;
            Random random = new Random(12345);
            for (int i = 0; i < 20; i++) {
                List<RealmModifier> rerolled = roller.rerollTypes(original, random, TEST_LEVEL);
                for (RealmModifier mod : rerolled) {
                    if (!originalTypes.contains(mod.type())) {
                        foundChange = true;
                        break;
                    }
                }
                if (foundChange) break;
            }

            assertTrue(foundChange, "Should produce at least one type change over many rolls");
        }

        @Test
        @DisplayName("Should preserve count")
        void shouldPreserveCount() {
            List<RealmModifier> original = List.of(
                RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 30),
                RealmModifier.of(RealmModifierType.MONSTER_HEALTH, 40),
                RealmModifier.of(RealmModifierType.ITEM_QUANTITY, 15)
            );

            List<RealmModifier> rerolled = roller.rerollTypes(original, new Random(42), TEST_LEVEL);

            assertEquals(original.size(), rerolled.size());
        }

        @Test
        @DisplayName("Should return original if all locked")
        void shouldReturnOriginalIfAllLocked() {
            List<RealmModifier> original = List.of(
                new RealmModifier(RealmModifierType.MONSTER_DAMAGE, 30, true),
                new RealmModifier(RealmModifierType.ITEM_QUANTITY, 15, true)
            );

            List<RealmModifier> rerolled = roller.rerollTypes(original, new Random(42), TEST_LEVEL);

            assertSame(original, rerolled);
        }
    }

    @Nested
    @DisplayName("addModifierSplit")
    class AddModifierTests {

        @Test
        @DisplayName("Should add a modifier when below max")
        void shouldAddWhenBelowMax() {
            List<RealmModifier> prefixes = List.of(
                RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 30)
            );
            List<RealmModifier> suffixes = List.of();

            RollResult result = roller.addModifierSplit(prefixes, suffixes, 6, new Random(42), TEST_LEVEL);

            assertEquals(2, result.totalCount());
        }

        @Test
        @DisplayName("Should not add when at max")
        void shouldNotAddWhenAtMax() {
            List<RealmModifier> prefixes = List.of(
                RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 30),
                RealmModifier.of(RealmModifierType.MONSTER_HEALTH, 40)
            );
            List<RealmModifier> suffixes = List.of();

            RollResult result = roller.addModifierSplit(prefixes, suffixes, 2, new Random(42), TEST_LEVEL);

            assertEquals(2, result.totalCount());
        }

        @Test
        @DisplayName("Should not add duplicate type")
        void shouldNotAddDuplicateType() {
            List<RealmModifier> prefixes = List.of(
                RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 30)
            );
            List<RealmModifier> suffixes = List.of();

            RollResult result = roller.addModifierSplit(prefixes, suffixes, 6, new Random(42), TEST_LEVEL);

            Set<RealmModifierType> types = new HashSet<>();
            for (RealmModifier mod : result.allModifiers()) {
                assertTrue(types.add(mod.type()), "Should not have duplicate types");
            }
        }
    }

    @Nested
    @DisplayName("removeModifier")
    class RemoveModifierTests {

        @Test
        @DisplayName("Should remove one unlocked modifier")
        void shouldRemoveOneUnlocked() {
            List<RealmModifier> original = List.of(
                RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 30),
                RealmModifier.of(RealmModifierType.MONSTER_HEALTH, 40),
                RealmModifier.of(RealmModifierType.ITEM_QUANTITY, 15)
            );

            List<RealmModifier> result = roller.removeModifier(original, new Random(42));

            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("Should not remove locked modifiers")
        void shouldNotRemoveLocked() {
            List<RealmModifier> original = List.of(
                new RealmModifier(RealmModifierType.MONSTER_DAMAGE, 30, true),
                RealmModifier.of(RealmModifierType.ITEM_QUANTITY, 15)
            );

            Random random = new Random(42);
            for (int i = 0; i < 20; i++) {
                List<RealmModifier> result = roller.removeModifier(original, random);
                // The locked one should always remain
                assertTrue(result.stream().anyMatch(m -> m.type() == RealmModifierType.MONSTER_DAMAGE),
                    "Locked modifier should always remain");
            }
        }

        @Test
        @DisplayName("Should return original if all locked")
        void shouldReturnOriginalIfAllLocked() {
            List<RealmModifier> original = List.of(
                new RealmModifier(RealmModifierType.MONSTER_DAMAGE, 30, true),
                new RealmModifier(RealmModifierType.ITEM_QUANTITY, 15, true)
            );

            List<RealmModifier> result = roller.removeModifier(original, new Random(42));

            assertSame(original, result);
        }

        @Test
        @DisplayName("Should return empty list style when removing last unlocked")
        void shouldHandleRemovingLast() {
            List<RealmModifier> original = List.of(
                RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 30)
            );

            List<RealmModifier> result = roller.removeModifier(original, new Random(42));

            assertEquals(0, result.size());
        }
    }

    @Nested
    @DisplayName("calculateDifficulty")
    class CalculateDifficultyTests {

        @Test
        @DisplayName("Should return 0 for empty list")
        void shouldReturnZeroForEmpty() {
            assertEquals(0, roller.calculateDifficulty(List.of()));
        }

        @Test
        @DisplayName("Should not count reward modifiers")
        void shouldNotCountRewardModifiers() {
            List<RealmModifier> rewardOnly = List.of(
                RealmModifier.of(RealmModifierType.ITEM_QUANTITY, 50),
                RealmModifier.of(RealmModifierType.ITEM_RARITY, 40)
            );

            assertEquals(0, roller.calculateDifficulty(rewardOnly));
        }

        @Test
        @DisplayName("Should count difficulty modifiers")
        void shouldCountDifficultyModifiers() {
            List<RealmModifier> withDifficulty = List.of(
                RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 30),
                RealmModifier.of(RealmModifierType.MONSTER_HEALTH, 40)
            );

            int difficulty = roller.calculateDifficulty(withDifficulty);
            assertTrue(difficulty > 0, "Should have positive difficulty");
        }

        @Test
        @DisplayName("Higher values should contribute more difficulty")
        void higherValuesShouldContributeMore() {
            // Uses enum's default bounds (10-100 for MONSTER_DAMAGE)
            List<RealmModifier> lowValue = List.of(
                RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 10)  // min value, 0% percentile
            );

            List<RealmModifier> highValue = List.of(
                RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 100) // max value, 100% percentile
            );

            int lowDifficulty = roller.calculateDifficulty(lowValue);
            int highDifficulty = roller.calculateDifficulty(highValue);

            assertTrue(highDifficulty > lowDifficulty,
                "High value (" + highDifficulty + ") should be greater than low value (" + lowDifficulty + ")");
        }
    }

    @Nested
    @DisplayName("calculateRewardMultiplier")
    class CalculateRewardMultiplierTests {

        @Test
        @DisplayName("Should return 1.0 for empty list")
        void shouldReturnOneForEmpty() {
            assertEquals(1.0f, roller.calculateRewardMultiplier(List.of()));
        }

        @Test
        @DisplayName("Should add reward modifier bonuses")
        void shouldAddRewardBonuses() {
            List<RealmModifier> withRewards = List.of(
                RealmModifier.of(RealmModifierType.ITEM_QUANTITY, 50) // +50% IIQ = +0.5
            );

            float multiplier = roller.calculateRewardMultiplier(withRewards);
            assertTrue(multiplier > 1.0f, "Should be > 1.0 with IIQ");
        }

        @Test
        @DisplayName("Difficulty modifiers should also increase rewards")
        void difficultyModifiersShouldIncreaseRewards() {
            List<RealmModifier> withDifficulty = List.of(
                RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 50),
                RealmModifier.of(RealmModifierType.MONSTER_HEALTH, 50)
            );

            float multiplier = roller.calculateRewardMultiplier(withDifficulty);
            assertTrue(multiplier > 1.0f,
                "Difficulty mods should increase reward multiplier");
        }
    }

    @Nested
    @DisplayName("Bonus getters")
    class BonusGetterTests {

        @Test
        @DisplayName("getItemQuantityBonus should sum IIQ modifiers")
        void shouldSumItemQuantity() {
            List<RealmModifier> mods = List.of(
                RealmModifier.of(RealmModifierType.ITEM_QUANTITY, 25),
                RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 30)
            );

            assertEquals(25, roller.getItemQuantityBonus(mods));
        }

        @Test
        @DisplayName("getItemRarityBonus should sum IIR modifiers")
        void shouldSumItemRarity() {
            List<RealmModifier> mods = List.of(
                RealmModifier.of(RealmModifierType.ITEM_RARITY, 30),
                RealmModifier.of(RealmModifierType.ITEM_QUANTITY, 25)
            );

            assertEquals(30, roller.getItemRarityBonus(mods));
        }

        @Test
        @DisplayName("getExperienceBonus should sum XP modifiers")
        void shouldSumExperience() {
            List<RealmModifier> mods = List.of(
                RealmModifier.of(RealmModifierType.EXPERIENCE_BONUS, 40),
                RealmModifier.of(RealmModifierType.ITEM_QUANTITY, 25)
            );

            assertEquals(40, roller.getExperienceBonus(mods));
        }

        @Test
        @DisplayName("Should return 0 when no matching modifiers")
        void shouldReturnZeroWhenNoMatching() {
            List<RealmModifier> mods = List.of(
                RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 30)
            );

            assertEquals(0, roller.getItemQuantityBonus(mods));
            assertEquals(0, roller.getItemRarityBonus(mods));
            assertEquals(0, roller.getExperienceBonus(mods));
        }
    }

    @Nested
    @DisplayName("RollResult record")
    class RollResultTests {

        @Test
        @DisplayName("Should store prefixes and suffixes separately")
        void shouldStoreSeparately() {
            List<RealmModifier> prefixes = List.of(
                RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 30)
            );
            List<RealmModifier> suffixes = List.of(
                RealmModifier.of(RealmModifierType.ITEM_QUANTITY, 20)
            );

            RollResult result = new RollResult(prefixes, suffixes);

            assertEquals(1, result.prefixes().size());
            assertEquals(1, result.suffixes().size());
            assertEquals(RealmModifierType.MONSTER_DAMAGE, result.prefixes().get(0).type());
            assertEquals(RealmModifierType.ITEM_QUANTITY, result.suffixes().get(0).type());
        }

        @Test
        @DisplayName("allModifiers should combine prefixes and suffixes")
        void allModifiersShouldCombine() {
            List<RealmModifier> prefixes = List.of(
                RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 30),
                RealmModifier.of(RealmModifierType.MONSTER_HEALTH, 40)
            );
            List<RealmModifier> suffixes = List.of(
                RealmModifier.of(RealmModifierType.ITEM_QUANTITY, 20)
            );

            RollResult result = new RollResult(prefixes, suffixes);
            List<RealmModifier> all = result.allModifiers();

            assertEquals(3, all.size());
            // Prefixes come first
            assertEquals(RealmModifierType.MONSTER_DAMAGE, all.get(0).type());
            assertEquals(RealmModifierType.MONSTER_HEALTH, all.get(1).type());
            assertEquals(RealmModifierType.ITEM_QUANTITY, all.get(2).type());
        }

        @Test
        @DisplayName("totalCount should return combined count")
        void totalCountShouldReturnCombined() {
            List<RealmModifier> prefixes = List.of(
                RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 30),
                RealmModifier.of(RealmModifierType.MONSTER_HEALTH, 40)
            );
            List<RealmModifier> suffixes = List.of(
                RealmModifier.of(RealmModifierType.ITEM_QUANTITY, 20),
                RealmModifier.of(RealmModifierType.ITEM_RARITY, 25)
            );

            RollResult result = new RollResult(prefixes, suffixes);

            assertEquals(4, result.totalCount());
        }

        @Test
        @DisplayName("Empty result should have zero counts")
        void emptyResultShouldHaveZeroCounts() {
            RollResult result = new RollResult(List.of(), List.of());

            assertEquals(0, result.prefixes().size());
            assertEquals(0, result.suffixes().size());
            assertEquals(0, result.totalCount());
            assertTrue(result.allModifiers().isEmpty());
        }
    }

    @Nested
    @DisplayName("rollModifiersSplit")
    class RollModifiersSplitTests {

        @Test
        @DisplayName("Should split modifiers by type")
        void shouldSplitByType() {
            Random random = new Random(42);

            RollResult result = roller.rollModifiersSplit(GearRarity.RARE, random, TEST_LEVEL);

            // All prefixes should have prefix types
            for (RealmModifier mod : result.prefixes()) {
                assertTrue(mod.isPrefix(),
                    mod.type().name() + " in prefixes should be prefix type");
            }

            // All suffixes should have suffix types
            for (RealmModifier mod : result.suffixes()) {
                assertTrue(mod.isSuffix(),
                    mod.type().name() + " in suffixes should be suffix type");
            }
        }

        @Test
        @DisplayName("Total count should match rarity limits")
        void totalCountShouldMatchRarity() {
            Random random = new Random(42);

            // Rare: 0-3 prefixes + 0-3 suffixes = 0-6 total
            RollResult result = roller.rollModifiersSplit(GearRarity.RARE, random, TEST_LEVEL);
            assertTrue(result.prefixes().size() <= 3,
                "Rare should have 0-3 prefixes, got " + result.prefixes().size());
            assertTrue(result.suffixes().size() <= 3,
                "Rare should have 0-3 suffixes, got " + result.suffixes().size());

            // Mythic: exactly 3+3 = 6 total
            RollResult mythic = roller.rollModifiersSplit(GearRarity.MYTHIC, random, TEST_LEVEL);
            assertEquals(3, mythic.prefixes().size(), "Mythic should have 3 prefixes");
            assertEquals(3, mythic.suffixes().size(), "Mythic should have 3 suffixes");
        }
    }

    @Nested
    @DisplayName("rerollValuesSplit")
    class RerollValuesSplitTests {

        @Test
        @DisplayName("Should preserve prefix/suffix separation")
        void shouldPreserveSeparation() {
            List<RealmModifier> prefixes = List.of(
                RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 30)
            );
            List<RealmModifier> suffixes = List.of(
                RealmModifier.of(RealmModifierType.ITEM_QUANTITY, 20)
            );

            RollResult result = roller.rerollValuesSplit(prefixes, suffixes, new Random(42), TEST_LEVEL);

            assertEquals(1, result.prefixes().size());
            assertEquals(1, result.suffixes().size());
            assertEquals(RealmModifierType.MONSTER_DAMAGE, result.prefixes().get(0).type());
            assertEquals(RealmModifierType.ITEM_QUANTITY, result.suffixes().get(0).type());
        }

        @Test
        @DisplayName("Should not reroll locked modifiers in either list")
        void shouldNotRerollLocked() {
            List<RealmModifier> prefixes = List.of(
                new RealmModifier(RealmModifierType.MONSTER_DAMAGE, 30, true)
            );
            List<RealmModifier> suffixes = List.of(
                new RealmModifier(RealmModifierType.ITEM_QUANTITY, 20, true)
            );

            RollResult result = roller.rerollValuesSplit(prefixes, suffixes, new Random(42), TEST_LEVEL);

            assertEquals(30, result.prefixes().get(0).value());
            assertEquals(20, result.suffixes().get(0).value());
        }
    }

    @Nested
    @DisplayName("addModifierSplit")
    class AddModifierSplitTests {

        @Test
        @DisplayName("Should add modifier to appropriate list")
        void shouldAddToAppropriateList() {
            List<RealmModifier> prefixes = List.of();
            List<RealmModifier> suffixes = List.of();

            // Run many times to ensure we get both prefix and suffix additions
            Set<Boolean> addedToPrefix = new HashSet<>();
            Random random = new Random(12345);
            for (int i = 0; i < 50; i++) {
                RollResult result = roller.addModifierSplit(prefixes, suffixes, 6, random, TEST_LEVEL);
                if (result.prefixes().size() > 0) {
                    addedToPrefix.add(true);
                    assertTrue(result.prefixes().get(0).isPrefix());
                }
                if (result.suffixes().size() > 0) {
                    addedToPrefix.add(false);
                    assertTrue(result.suffixes().get(0).isSuffix());
                }
            }

            // Should have added both prefixes and suffixes over many runs
            assertEquals(2, addedToPrefix.size(), "Should have added both prefix and suffix types");
        }

        @Test
        @DisplayName("Should not exceed max total")
        void shouldNotExceedMaxTotal() {
            List<RealmModifier> prefixes = List.of(
                RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 30),
                RealmModifier.of(RealmModifierType.MONSTER_HEALTH, 40)
            );
            List<RealmModifier> suffixes = List.of(
                RealmModifier.of(RealmModifierType.ITEM_QUANTITY, 20)
            );

            RollResult result = roller.addModifierSplit(prefixes, suffixes, 3, new Random(42), TEST_LEVEL);

            // Should not add (already at max)
            assertEquals(3, result.totalCount());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // LEVEL-BASED SCALING TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Level-based modifier scaling")
    class LevelScalingTests {

        @BeforeEach
        void configureMonsterDamage() {
            // Set up a known scaling config for MONSTER_DAMAGE:
            // base_min=3, base_max=15, scale_per_level=1.5
            ModifierSettings settings = new ModifierSettings();
            settings.setBaseMin(3);
            settings.setBaseMax(15);
            settings.setScalePerLevel(1.5);
            settings.setEnabled(true);
            config.setModifierSettings(RealmModifierType.MONSTER_DAMAGE, settings);
        }

        @Test
        @DisplayName("Level 10 map should produce low MONSTER_DAMAGE values (~3-15)")
        void level10_lowValues() {
            Random random = new Random(12345);
            int minSeen = Integer.MAX_VALUE;
            int maxSeen = Integer.MIN_VALUE;

            for (int i = 0; i < 200; i++) {
                RealmModifier mod = roller.rollModifier(RealmModifierType.MONSTER_DAMAGE, random, 10);
                minSeen = Math.min(minSeen, mod.value());
                maxSeen = Math.max(maxSeen, mod.value());
            }

            // At level 10 with default decayDivisor=5:
            // multiplier ~1.07, effectiveLevel ~7, scaled range ~13-25
            // Values should be modest, well under level 100 values
            ModifierSettings settings = config.getModifierSettings(RealmModifierType.MONSTER_DAMAGE);
            int expectedMin = settings.getMinValue(10);
            int expectedMax = settings.getMaxValue(10);

            assertTrue(minSeen >= expectedMin,
                "Min seen (" + minSeen + ") should be >= expectedMin (" + expectedMin + ")");
            assertTrue(maxSeen <= expectedMax,
                "Max seen (" + maxSeen + ") should be <= expectedMax (" + expectedMax + ")");
            // Values at level 10 should be much lower than at level 100
            assertTrue(expectedMax < 30,
                "Level 10 max (" + expectedMax + ") should be under 30");
        }

        @Test
        @DisplayName("Level 100 map should produce moderate MONSTER_DAMAGE values")
        void level100_moderateValues() {
            ModifierSettings settings = config.getModifierSettings(RealmModifierType.MONSTER_DAMAGE);
            int expectedMin = settings.getMinValue(100);
            int expectedMax = settings.getMaxValue(100);

            // At level 100: effectiveLevel ~50, scaled range ~78-90
            assertTrue(expectedMin >= 30,
                "Level 100 min (" + expectedMin + ") should be >= 30");
            assertTrue(expectedMax >= 50,
                "Level 100 max (" + expectedMax + ") should be >= 50");

            // Verify rolled values fall within range
            Random random = new Random(12345);
            for (int i = 0; i < 100; i++) {
                RealmModifier mod = roller.rollModifier(RealmModifierType.MONSTER_DAMAGE, random, 100);
                assertTrue(mod.value() >= expectedMin && mod.value() <= expectedMax,
                    "Value " + mod.value() + " should be in [" + expectedMin + ", " + expectedMax + "]");
            }
        }

        @Test
        @DisplayName("Higher level should produce higher values on average")
        void higherLevel_higherValues() {
            Random random = new Random(42);
            int iterations = 200;

            double avgLevel10 = 0, avgLevel50 = 0, avgLevel100 = 0;
            for (int i = 0; i < iterations; i++) {
                avgLevel10 += roller.rollModifier(RealmModifierType.MONSTER_DAMAGE, random, 10).value();
                avgLevel50 += roller.rollModifier(RealmModifierType.MONSTER_DAMAGE, random, 50).value();
                avgLevel100 += roller.rollModifier(RealmModifierType.MONSTER_DAMAGE, random, 100).value();
            }
            avgLevel10 /= iterations;
            avgLevel50 /= iterations;
            avgLevel100 /= iterations;

            assertTrue(avgLevel10 < avgLevel50,
                "Level 10 avg (" + avgLevel10 + ") should be < level 50 avg (" + avgLevel50 + ")");
            assertTrue(avgLevel50 < avgLevel100,
                "Level 50 avg (" + avgLevel50 + ") should be < level 100 avg (" + avgLevel100 + ")");
        }

        @Test
        @DisplayName("Binary modifiers should return 1 regardless of level")
        void binaryModifiers_ignoreLevel() {
            // Set up NO_REGENERATION as binary (min==max)
            ModifierSettings binarySettings = new ModifierSettings();
            binarySettings.setBaseMin(1);
            binarySettings.setBaseMax(1);
            binarySettings.setScalePerLevel(0.0);
            binarySettings.setEnabled(true);
            config.setModifierSettings(RealmModifierType.NO_REGENERATION, binarySettings);

            Random random = new Random(42);
            for (int level : new int[]{1, 10, 50, 100, 500, 1000}) {
                RealmModifier mod = roller.rollModifier(RealmModifierType.NO_REGENERATION, random, level);
                assertEquals(1, mod.value(),
                    "Binary modifier should be 1 at level " + level);
            }
        }

        @Test
        @DisplayName("Level 1 should use base values only (no scaling)")
        void level1_baseValues() {
            ModifierSettings settings = config.getModifierSettings(RealmModifierType.MONSTER_DAMAGE);
            int minAtLevel1 = settings.getMinValue(1);
            int maxAtLevel1 = settings.getMaxValue(1);

            // At level 1, multiplier is 1.0, effectiveLevel is 0, so only base values
            assertEquals(3, minAtLevel1, "Level 1 min should be base_min (3)");
            assertEquals(15, maxAtLevel1, "Level 1 max should be base_max (15)");
        }
    }
}
