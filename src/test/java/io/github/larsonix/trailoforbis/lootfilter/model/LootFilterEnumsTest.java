package io.github.larsonix.trailoforbis.lootfilter.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for loot filter enums: {@link FilterAction}, {@link ConditionType},
 * {@link CorruptionFilter}, {@link ModifierFilterCategory}.
 */
class LootFilterEnumsTest {

    @Nested
    @DisplayName("FilterAction")
    class FilterActionTests {

        @Test
        @DisplayName("has exactly ALLOW and BLOCK")
        void hasExpectedValues() {
            assertEquals(2, FilterAction.values().length);
            assertNotNull(FilterAction.ALLOW);
            assertNotNull(FilterAction.BLOCK);
        }
    }

    @Nested
    @DisplayName("ConditionType")
    class ConditionTypeTests {

        @Test
        @DisplayName("has exactly 12 values")
        void hasTwelveValues() {
            assertEquals(12, ConditionType.values().length);
        }

        @ParameterizedTest
        @EnumSource(ConditionType.class)
        @DisplayName("all values have non-empty display names")
        void allHaveDisplayNames(ConditionType type) {
            assertNotNull(type.getDisplayName());
            assertFalse(type.getDisplayName().isBlank());
        }
    }

    @Nested
    @DisplayName("CorruptionFilter")
    class CorruptionFilterTests {

        @Test
        @DisplayName("has exactly 3 values")
        void hasThreeValues() {
            assertEquals(3, CorruptionFilter.values().length);
            assertNotNull(CorruptionFilter.CORRUPTED_ONLY);
            assertNotNull(CorruptionFilter.NOT_CORRUPTED);
            assertNotNull(CorruptionFilter.EITHER);
        }
    }

    @Nested
    @DisplayName("ModifierFilterCategory")
    class ModifierFilterCategoryTests {

        @Test
        @DisplayName("has exactly 8 categories")
        void hasEightCategories() {
            assertEquals(8, ModifierFilterCategory.values().length);
        }

        @ParameterizedTest
        @EnumSource(ModifierFilterCategory.class)
        @DisplayName("all categories have display name and color")
        void allHaveDisplayNameAndColor(ModifierFilterCategory cat) {
            assertNotNull(cat.getDisplayName());
            assertFalse(cat.getDisplayName().isBlank());
            assertNotNull(cat.getColor());
            assertTrue(cat.getColor().startsWith("#"), cat + " color should be hex");
        }
    }
}
