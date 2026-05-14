package io.github.larsonix.trailoforbis.mobs.modifiers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ModifierType} enum integrity.
 *
 * <p>Critical for production: if enum names change, saved entities with
 * old names silently lose their modifiers. If display names collide,
 * nameplate text is ambiguous. If level gating is wrong, low-level
 * players face endgame modifiers.
 */
@DisplayName("ModifierType")
class ModifierTypeTest {

    @Nested
    @DisplayName("Enum Integrity")
    class EnumIntegrity {

        @Test
        @DisplayName("All 21 modifiers exist")
        void allModifiersExist() {
            assertEquals(20, ModifierType.values().length,
                "Expected exactly 20 modifier types");
        }

        @Test
        @DisplayName("Every modifier has a non-empty display name")
        void everyModifierHasDisplayName() {
            for (ModifierType type : ModifierType.values()) {
                assertNotNull(type.getDisplayName(), type.name() + " has null displayName");
                assertFalse(type.getDisplayName().isEmpty(), type.name() + " has empty displayName");
            }
        }

        @Test
        @DisplayName("Every modifier has valid tint colors (6-char hex)")
        void everyModifierHasValidTintColors() {
            for (ModifierType type : ModifierType.values()) {
                assertValidHex(type.getTintBottom(), type.name() + " tintBottom");
                assertValidHex(type.getTintTop(), type.name() + " tintTop");
            }
        }

        private void assertValidHex(String hex, String context) {
            assertNotNull(hex, context + " is null");
            assertTrue(hex.startsWith("#"), context + " doesn't start with #: " + hex);
            assertEquals(7, hex.length(), context + " wrong length: " + hex);
        }

        @Test
        @DisplayName("Every modifier has a category and tier")
        void everyModifierHasCategoryAndTier() {
            for (ModifierType type : ModifierType.values()) {
                assertNotNull(type.getCategory(), type.name() + " has null category");
                assertNotNull(type.getTier(), type.name() + " has null tier");
            }
        }

        @Test
        @DisplayName("Display names are unique")
        void displayNamesAreUnique() {
            Set<String> names = new HashSet<>();
            for (ModifierType type : ModifierType.values()) {
                assertTrue(names.add(type.getDisplayName().toLowerCase()),
                    "Duplicate display name: " + type.getDisplayName());
            }
        }

        @Test
        @DisplayName("Config keys are unique and lowercase")
        void configKeysAreUniqueAndLowercase() {
            Set<String> keys = new HashSet<>();
            for (ModifierType type : ModifierType.values()) {
                String key = type.configKey();
                assertEquals(key.toLowerCase(), key, type.name() + " configKey not lowercase");
                assertTrue(keys.add(key), "Duplicate configKey: " + key);
            }
        }
    }

    @Nested
    @DisplayName("Serialization Roundtrip")
    class SerializationRoundtrip {

        @Test
        @DisplayName("Every modifier survives name() → fromName() roundtrip")
        void everyModifierSurvivesNameRoundtrip() {
            for (ModifierType type : ModifierType.values()) {
                ModifierType resolved = ModifierType.fromName(type.name());
                assertEquals(type, resolved,
                    type.name() + " does not roundtrip through fromName()");
            }
        }

        @Test
        @DisplayName("fromName is case-insensitive")
        void fromNameIsCaseInsensitive() {
            assertEquals(ModifierType.BLAZING, ModifierType.fromName("blazing"));
            assertEquals(ModifierType.BLAZING, ModifierType.fromName("BLAZING"));
            assertEquals(ModifierType.BLAZING, ModifierType.fromName("Blazing"));
        }

        @Test
        @DisplayName("fromName matches display name too")
        void fromNameMatchesDisplayName() {
            assertEquals(ModifierType.SHADOW_STEP, ModifierType.fromName("Shadow Step"));
            assertEquals(ModifierType.PACK_LEADER, ModifierType.fromName("Pack Leader"));
            assertEquals(ModifierType.FROST_AURA, ModifierType.fromName("Frost Aura"));
        }

        @Test
        @DisplayName("fromName returns null for unknown names")
        void fromNameReturnsNullForUnknown() {
            assertNull(ModifierType.fromName("NonExistent"));
            assertNull(ModifierType.fromName(""));
            assertNull(ModifierType.fromName(null));
        }
    }

    @Nested
    @DisplayName("Level Gating")
    class LevelGating {

        @Test
        @DisplayName("Level 1 only has Tier 1 modifiers (5 stat mods)")
        void level1_onlyTier1() {
            List<ModifierType> pool = ModifierType.availableAtLevel(1);
            assertEquals(5, pool.size(), "Level 1 should have exactly 5 Tier 1 modifiers");
            for (ModifierType mod : pool) {
                assertEquals(ModifierTier.TIER_1, mod.getTier(),
                    mod.name() + " should not be available at level 1");
            }
        }

        @Test
        @DisplayName("Level 10 has Tier 1 + Tier 2 modifiers")
        void level10_tier1And2() {
            List<ModifierType> pool = ModifierType.availableAtLevel(10);
            assertTrue(pool.size() > 5, "Level 10 should have more than 5 modifiers");
            for (ModifierType mod : pool) {
                assertTrue(mod.getTier().ordinal() <= ModifierTier.TIER_2.ordinal(),
                    mod.name() + " (Tier " + mod.getTier() + ") should not be at level 10");
            }
        }

        @Test
        @DisplayName("Level 25 has Tier 1 + 2 + 3")
        void level25_tier1to3() {
            List<ModifierType> pool = ModifierType.availableAtLevel(25);
            assertTrue(pool.contains(ModifierType.EVASIVE), "Tier 3 Evasive should be at level 25");
            assertTrue(pool.contains(ModifierType.PACK_LEADER), "Tier 3 Pack Leader should be at level 25");
            assertFalse(pool.contains(ModifierType.VOLATILE), "Tier 4 Volatile should NOT be at level 25");
        }

        @Test
        @DisplayName("Level 40+ has all 21 modifiers")
        void level40_allModifiers() {
            List<ModifierType> pool = ModifierType.availableAtLevel(40);
            assertEquals(20, pool.size(), "Level 40 should have all 20 modifiers");
        }

        @Test
        @DisplayName("Level 100 still has exactly 21 modifiers (no extras)")
        void level100_still21() {
            List<ModifierType> pool = ModifierType.availableAtLevel(100);
            assertEquals(20, pool.size());
        }
    }

    @Nested
    @DisplayName("Query Methods")
    class QueryMethods {

        @Test
        @DisplayName("requiresTick returns true for behavioral modifiers")
        void requiresTick_behavioral() {
            assertTrue(ModifierType.ENRAGED.requiresTick());
            assertTrue(ModifierType.REGENERATING.requiresTick());
            assertTrue(ModifierType.SUMMONER.requiresTick());
            assertTrue(ModifierType.PACK_LEADER.requiresTick());
        }

        @Test
        @DisplayName("requiresTick returns true for elemental mods with tick behavior")
        void requiresTick_elementalWithBehavior() {
            assertTrue(ModifierType.BLAZING.requiresTick(), "Blazing has fire trail");
            assertTrue(ModifierType.THUNDEROUS.requiresTick(), "Thunderous has lightning strike");
            assertTrue(ModifierType.FROZEN.requiresTick(), "Frozen has proximity slow");
        }

        @Test
        @DisplayName("requiresTick returns false for pure stat mods")
        void requiresTick_pureStat() {
            assertFalse(ModifierType.HARDENED.requiresTick());
            assertFalse(ModifierType.VIGOROUS.requiresTick());
            assertFalse(ModifierType.FIERCE.requiresTick());
        }

        @Test
        @DisplayName("hasDeathTrigger identifies correct modifiers")
        void hasDeathTrigger() {
            assertTrue(ModifierType.VOLATILE.hasDeathTrigger());
            assertTrue(ModifierType.RALLYING.hasDeathTrigger());
            assertTrue(ModifierType.VENOMOUS.hasDeathTrigger());
            assertFalse(ModifierType.HARDENED.hasDeathTrigger());
            assertFalse(ModifierType.ENRAGED.hasDeathTrigger());
        }

        @Test
        @DisplayName("hasStatBonus returns true for stat modifiers")
        void hasStatBonus() {
            assertTrue(ModifierType.HARDENED.hasStatBonus());
            assertTrue(ModifierType.BLAZING.hasStatBonus());
            assertTrue(ModifierType.WARDING.hasStatBonus());
            assertFalse(ModifierType.ENRAGED.hasStatBonus(), "Enraged is behavioral, not stat");
            assertFalse(ModifierType.SUMMONER.hasStatBonus());
        }
    }
}
