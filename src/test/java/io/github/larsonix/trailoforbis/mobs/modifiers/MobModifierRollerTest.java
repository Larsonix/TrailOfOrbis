package io.github.larsonix.trailoforbis.mobs.modifiers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link MobModifierRoller}.
 *
 * <p>Critical for production: if the roller produces banned combos,
 * players encounter Hardened+Warding (no damage type works) or
 * Evasive+Frost Aura (can't approach + can't hit). If seeding breaks,
 * debugging modifier issues becomes impossible.
 */
@DisplayName("MobModifierRoller")
class MobModifierRollerTest {

    private MobModifierConfig config;
    private MobModifierRoller roller;

    @BeforeEach
    void setUp() {
        config = MobModifierConfig.createDefaults();
        roller = new MobModifierRoller(config);
    }

    @Nested
    @DisplayName("Basic Rolling")
    class BasicRolling {

        @Test
        @DisplayName("Elite tier rolls exactly 1 modifier")
        void eliteTier_rolls1() {
            List<ModifierType> mods = roller.roll(50, "elite", 12345L);
            assertEquals(1, mods.size());
        }

        @Test
        @DisplayName("Boss tier rolls exactly 2 modifiers")
        void bossTier_rolls2() {
            List<ModifierType> mods = roller.roll(50, "boss", 12345L);
            assertEquals(2, mods.size());
        }

        @Test
        @DisplayName("Elite boss tier rolls exactly 3 modifiers")
        void eliteBossTier_rolls3() {
            List<ModifierType> mods = roller.roll(50, "elite_boss", 12345L);
            assertEquals(3, mods.size());
        }

        @Test
        @DisplayName("Unknown tier rolls 0 modifiers")
        void unknownTier_rolls0() {
            List<ModifierType> mods = roller.roll(50, "unknown", 12345L);
            assertEquals(0, mods.size());
        }

        @Test
        @DisplayName("Normal tier rolls 0 modifiers")
        void normalTier_rolls0() {
            List<ModifierType> mods = roller.roll(50, "normal", 12345L);
            assertEquals(0, mods.size());
        }
    }

    @Nested
    @DisplayName("No Duplicates")
    class NoDuplicates {

        @RepeatedTest(50)
        @DisplayName("Boss never has duplicate modifiers")
        void boss_noDuplicates() {
            long seed = System.nanoTime();
            List<ModifierType> mods = roller.roll(50, "boss", seed);
            Set<ModifierType> unique = new HashSet<>(mods);
            assertEquals(mods.size(), unique.size(),
                "Duplicate modifiers found: " + mods);
        }

        @RepeatedTest(50)
        @DisplayName("Elite boss never has duplicate modifiers")
        void eliteBoss_noDuplicates() {
            long seed = System.nanoTime();
            List<ModifierType> mods = roller.roll(50, "elite_boss", seed);
            Set<ModifierType> unique = new HashSet<>(mods);
            assertEquals(mods.size(), unique.size(),
                "Duplicate modifiers found: " + mods);
        }
    }

    @Nested
    @DisplayName("Exclusion Rules")
    class ExclusionRules {

        @RepeatedTest(100)
        @DisplayName("Hardened and Warding never co-occur on a boss")
        void hardened_warding_excluded() {
            long seed = System.nanoTime();
            List<ModifierType> mods = roller.roll(50, "boss", seed);
            boolean hasHardened = mods.contains(ModifierType.HARDENED);
            boolean hasWarding = mods.contains(ModifierType.WARDING);
            assertFalse(hasHardened && hasWarding,
                "Hardened + Warding should be excluded but got: " + mods);
        }

        @RepeatedTest(100)
        @DisplayName("Evasive and Frost Aura never co-occur on a boss")
        void evasive_frostAura_excluded() {
            long seed = System.nanoTime();
            List<ModifierType> mods = roller.roll(50, "boss", seed);
            boolean hasEvasive = mods.contains(ModifierType.EVASIVE);
            boolean hasFrostAura = mods.contains(ModifierType.FROST_AURA);
            assertFalse(hasEvasive && hasFrostAura,
                "Evasive + Frost Aura should be excluded but got: " + mods);
        }

        @RepeatedTest(100)
        @DisplayName("Exclusions also work on elite boss (3 modifiers)")
        void exclusions_eliteBoss() {
            long seed = System.nanoTime();
            List<ModifierType> mods = roller.roll(50, "elite_boss", seed);
            boolean hasHardened = mods.contains(ModifierType.HARDENED);
            boolean hasWarding = mods.contains(ModifierType.WARDING);
            assertFalse(hasHardened && hasWarding,
                "Hardened + Warding should be excluded on elite boss: " + mods);
        }
    }

    @Nested
    @DisplayName("Level Gating")
    class LevelGating {

        @RepeatedTest(50)
        @DisplayName("Level 1 elite only gets Tier 1 modifiers")
        void level1_onlyTier1() {
            long seed = System.nanoTime();
            List<ModifierType> mods = roller.roll(1, "elite", seed);
            for (ModifierType mod : mods) {
                assertEquals(ModifierTier.TIER_1, mod.getTier(),
                    "Level 1 elite got non-Tier-1 modifier: " + mod.name());
            }
        }

        @RepeatedTest(50)
        @DisplayName("Level 5 elite only gets Tier 1 modifiers")
        void level5_onlyTier1() {
            long seed = System.nanoTime();
            List<ModifierType> mods = roller.roll(5, "elite", seed);
            for (ModifierType mod : mods) {
                assertEquals(ModifierTier.TIER_1, mod.getTier(),
                    "Level 5 elite got non-Tier-1 modifier: " + mod.name());
            }
        }
    }

    @Nested
    @DisplayName("Deterministic Seeding")
    class DeterministicSeeding {

        @Test
        @DisplayName("Same seed produces same modifiers")
        void sameSeed_sameResult() {
            long seed = 42L;
            List<ModifierType> first = roller.roll(50, "boss", seed);
            List<ModifierType> second = roller.roll(50, "boss", seed);
            assertEquals(first, second, "Same seed should produce same modifiers");
        }

        @Test
        @DisplayName("Different seeds produce different modifiers (usually)")
        void differentSeeds_differentResults() {
            // Not guaranteed but statistically near-certain with 21 modifiers
            List<ModifierType> a = roller.roll(50, "boss", 1L);
            List<ModifierType> b = roller.roll(50, "boss", 999999L);
            // At least one should differ (probability of identical = ~1/441)
            // We test 10 seed pairs to be safe
            boolean foundDifference = false;
            for (int i = 0; i < 10; i++) {
                List<ModifierType> x = roller.roll(50, "boss", (long) i);
                List<ModifierType> y = roller.roll(50, "boss", (long) (i + 1000));
                if (!x.equals(y)) {
                    foundDifference = true;
                    break;
                }
            }
            assertTrue(foundDifference, "10 different seed pairs all produced identical modifiers — seeding is broken");
        }
    }

    @Nested
    @DisplayName("Result Properties")
    class ResultProperties {

        @Test
        @DisplayName("Returned list is unmodifiable")
        void resultIsUnmodifiable() {
            List<ModifierType> mods = roller.roll(50, "elite", 12345L);
            assertThrows(UnsupportedOperationException.class, () -> mods.add(ModifierType.BLAZING));
        }

        @Test
        @DisplayName("Level 0 still gets Tier 1 modifiers (default fallback)")
        void level0_getTsTier1() {
            // highestForLevel(0) defaults to TIER_1 even at level 0
            // This is intentional — level 0 mobs can still be elite
            List<ModifierType> pool = ModifierType.availableAtLevel(0);
            assertEquals(5, pool.size(), "Level 0 should have 5 Tier 1 modifiers (default)");
        }
    }
}
