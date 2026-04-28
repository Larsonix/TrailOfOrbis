package io.github.larsonix.trailoforbis.maps.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RealmBiomeType")
class RealmBiomeTypeTest {

    @Nested
    @DisplayName("getTemplateName")
    class GetTemplateName {

        @Test
        @DisplayName("Should return base template name for forest medium (no suffix)")
        void forestMedium() {
            // MEDIUM uses base template name without size suffix
            assertEquals("Realm_Forest",
                RealmBiomeType.FOREST.getTemplateName(RealmLayoutSize.MEDIUM));
        }

        @Test
        @DisplayName("Should return biome and size specific template for volcano large")
        void volcanoLarge() {
            assertEquals("Realm_Volcano_large",
                RealmBiomeType.VOLCANO.getTemplateName(RealmLayoutSize.LARGE));
        }

        @Test
        @DisplayName("Should return biome and size specific template for corrupted small")
        void corruptedSmall() {
            assertEquals("Realm_Corrupted_small",
                RealmBiomeType.CORRUPTED.getTemplateName(RealmLayoutSize.SMALL));
        }

        @Test
        @DisplayName("Should return skill sanctum base template for medium (no suffix)")
        void skillSanctum() {
            // MEDIUM uses base template name without size suffix
            assertEquals("realm_skill_sanctum",
                RealmBiomeType.SKILL_SANCTUM.getTemplateName(RealmLayoutSize.MEDIUM));
        }
    }

    @Nested
    @DisplayName("isHighDifficulty")
    class IsHighDifficulty {

        @Test
        @DisplayName("Volcano should be high difficulty")
        void volcanoIsHigh() {
            assertTrue(RealmBiomeType.VOLCANO.isHighDifficulty());
        }

        @Test
        @DisplayName("Void should be high difficulty")
        void voidIsHigh() {
            assertTrue(RealmBiomeType.VOID.isHighDifficulty());
        }

        @Test
        @DisplayName("Corrupted should be high difficulty")
        void corruptedIsHigh() {
            assertTrue(RealmBiomeType.CORRUPTED.isHighDifficulty());
        }

        @Test
        @DisplayName("Forest should not be high difficulty")
        void forestIsNotHigh() {
            assertFalse(RealmBiomeType.FOREST.isHighDifficulty());
        }

        @Test
        @DisplayName("Beach should not be high difficulty")
        void beachIsNotHigh() {
            assertFalse(RealmBiomeType.BEACH.isHighDifficulty());
        }
    }

    @Nested
    @DisplayName("hasEnvironmentalHazards")
    class HasEnvironmentalHazards {

        @Test
        @DisplayName("Volcano should have hazards")
        void volcanoHasHazards() {
            assertTrue(RealmBiomeType.VOLCANO.hasEnvironmentalHazards());
        }

        @Test
        @DisplayName("Swamp should have hazards")
        void swampHasHazards() {
            assertTrue(RealmBiomeType.SWAMP.hasEnvironmentalHazards());
        }

        @Test
        @DisplayName("Forest should not have hazards")
        void forestNoHazards() {
            assertFalse(RealmBiomeType.FOREST.hasEnvironmentalHazards());
        }
    }

    @Nested
    @DisplayName("randomNonCorrupted")
    class RandomNonCorrupted {

        @Test
        @DisplayName("Should never return CORRUPTED")
        void neverReturnsCorrupted() {
            Random random = new Random(42);
            for (int i = 0; i < 100; i++) {
                RealmBiomeType biome = RealmBiomeType.randomNonCorrupted(random);
                assertNotEquals(RealmBiomeType.CORRUPTED, biome);
            }
        }
    }

    @Nested
    @DisplayName("fromString")
    class FromString {

        @Test
        @DisplayName("Should parse uppercase")
        void parseUppercase() {
            assertEquals(RealmBiomeType.FOREST, RealmBiomeType.fromString("FOREST"));
        }

        @Test
        @DisplayName("Should parse lowercase")
        void parseLowercase() {
            assertEquals(RealmBiomeType.VOLCANO, RealmBiomeType.fromString("volcano"));
        }

        @Test
        @DisplayName("Should parse mixed case")
        void parseMixedCase() {
            assertEquals(RealmBiomeType.JUNGLE, RealmBiomeType.fromString("JuNgLe"));
        }

        @Test
        @DisplayName("Should throw on invalid name")
        void throwsOnInvalid() {
            assertThrows(IllegalArgumentException.class,
                () -> RealmBiomeType.fromString("invalid"));
        }

        @Test
        @DisplayName("Should throw on null")
        void throwsOnNull() {
            assertThrows(IllegalArgumentException.class,
                () -> RealmBiomeType.fromString(null));
        }
    }

    @Nested
    @DisplayName("getThemeColorHex")
    class GetThemeColorHex {

        @Test
        @DisplayName("Forest should return green hex")
        void forestGreen() {
            assertEquals("#228B22", RealmBiomeType.FOREST.getThemeColorHex());
        }

        @Test
        @DisplayName("Volcano should return red hex")
        void volcanoRed() {
            assertEquals("#8B0000", RealmBiomeType.VOLCANO.getThemeColorHex());
        }
    }
}
