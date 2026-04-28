package io.github.larsonix.trailoforbis.gear.tooltip;

import io.github.larsonix.trailoforbis.gear.model.GearRarity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TooltipStyles utility class.
 */
@DisplayName("TooltipStyles")
class TooltipStylesTest {

    // =========================================================================
    // QUALITY COLOR TESTS
    // =========================================================================

    @Nested
    @DisplayName("getQualityColor")
    class GetQualityColorTests {

        @Test
        @DisplayName("returns QUALITY_POOR for quality 1-19")
        void returnsQualityPoor_ForLowQuality() {
            assertEquals(TooltipStyles.QUALITY_POOR, TooltipStyles.getQualityColor(1));
            assertEquals(TooltipStyles.QUALITY_POOR, TooltipStyles.getQualityColor(10));
            assertEquals(TooltipStyles.QUALITY_POOR, TooltipStyles.getQualityColor(19));
        }

        @Test
        @DisplayName("returns QUALITY_BELOW_AVERAGE for quality 20-39")
        void returnsQualityBelowAverage_ForBelowAverageQuality() {
            assertEquals(TooltipStyles.QUALITY_BELOW_AVERAGE, TooltipStyles.getQualityColor(20));
            assertEquals(TooltipStyles.QUALITY_BELOW_AVERAGE, TooltipStyles.getQualityColor(30));
            assertEquals(TooltipStyles.QUALITY_BELOW_AVERAGE, TooltipStyles.getQualityColor(39));
        }

        @Test
        @DisplayName("returns QUALITY_AVERAGE for quality 40-69")
        void returnsQualityAverage_ForAverageQuality() {
            assertEquals(TooltipStyles.QUALITY_AVERAGE, TooltipStyles.getQualityColor(40));
            assertEquals(TooltipStyles.QUALITY_AVERAGE, TooltipStyles.getQualityColor(50));
            assertEquals(TooltipStyles.QUALITY_AVERAGE, TooltipStyles.getQualityColor(69));
        }

        @Test
        @DisplayName("returns QUALITY_ABOVE_AVERAGE for quality 70-89")
        void returnsQualityAboveAverage_ForAboveAverageQuality() {
            assertEquals(TooltipStyles.QUALITY_ABOVE_AVERAGE, TooltipStyles.getQualityColor(70));
            assertEquals(TooltipStyles.QUALITY_ABOVE_AVERAGE, TooltipStyles.getQualityColor(80));
            assertEquals(TooltipStyles.QUALITY_ABOVE_AVERAGE, TooltipStyles.getQualityColor(89));
        }

        @Test
        @DisplayName("returns QUALITY_EXCELLENT for quality 90-99")
        void returnsQualityExcellent_ForExcellentQuality() {
            assertEquals(TooltipStyles.QUALITY_EXCELLENT, TooltipStyles.getQualityColor(90));
            assertEquals(TooltipStyles.QUALITY_EXCELLENT, TooltipStyles.getQualityColor(95));
            assertEquals(TooltipStyles.QUALITY_EXCELLENT, TooltipStyles.getQualityColor(99));
        }

        @Test
        @DisplayName("returns QUALITY_PERFECT for quality 100+")
        void returnsQualityPerfect_ForPerfectQuality() {
            assertEquals(TooltipStyles.QUALITY_PERFECT, TooltipStyles.getQualityColor(100));
            assertEquals(TooltipStyles.QUALITY_PERFECT, TooltipStyles.getQualityColor(101));
        }
    }

    // =========================================================================
    // QUALITY NAME TESTS
    // =========================================================================

    @Nested
    @DisplayName("getQualityName")
    class GetQualityNameTests {

        @ParameterizedTest
        @CsvSource({
            "1, Poor",
            "19, Poor",
            "20, Below Average",
            "39, Below Average",
            "40, Average",
            "69, Average",
            "70, Above Average",
            "89, Above Average",
            "90, Excellent",
            "99, Excellent",
            "100, Perfect",
            "101, Perfect"
        })
        @DisplayName("returns correct name for quality ranges")
        void returnsCorrectName_ForQualityRanges(int quality, String expectedName) {
            assertEquals(expectedName, TooltipStyles.getQualityName(quality));
        }
    }

    // =========================================================================
    // RARITY COLOR TESTS
    // =========================================================================

    @Nested
    @DisplayName("getRarityColor")
    class GetRarityColorTests {

        @ParameterizedTest
        @EnumSource(GearRarity.class)
        @DisplayName("returns non-null color for all rarities")
        void returnsNonNull_ForAllRarities(GearRarity rarity) {
            String color = TooltipStyles.getRarityColor(rarity);
            assertNotNull(color);
            assertTrue(color.startsWith("#"), "Color should be hex format");
        }

        @Test
        @DisplayName("returns rarity's hex color")
        void returnsRarityHexColor() {
            assertEquals(GearRarity.COMMON.getHexColor(), TooltipStyles.getRarityColor(GearRarity.COMMON));
            assertEquals(GearRarity.LEGENDARY.getHexColor(), TooltipStyles.getRarityColor(GearRarity.LEGENDARY));
        }
    }

    // =========================================================================
    // MODIFIER COLOR TESTS
    // =========================================================================

    @Nested
    @DisplayName("getModifierColor")
    class GetModifierColorTests {

        @Test
        @DisplayName("returns POSITIVE_MODIFIER for positive values")
        void returnsPositive_ForPositiveValues() {
            assertEquals(TooltipStyles.POSITIVE_MODIFIER, TooltipStyles.getModifierColor(1.0));
            assertEquals(TooltipStyles.POSITIVE_MODIFIER, TooltipStyles.getModifierColor(100.0));
        }

        @Test
        @DisplayName("returns POSITIVE_MODIFIER for zero")
        void returnsPositive_ForZero() {
            assertEquals(TooltipStyles.POSITIVE_MODIFIER, TooltipStyles.getModifierColor(0.0));
        }

        @Test
        @DisplayName("returns NEGATIVE_MODIFIER for negative values")
        void returnsNegative_ForNegativeValues() {
            assertEquals(TooltipStyles.NEGATIVE_MODIFIER, TooltipStyles.getModifierColor(-1.0));
            assertEquals(TooltipStyles.NEGATIVE_MODIFIER, TooltipStyles.getModifierColor(-100.0));
        }
    }

    // =========================================================================
    // REQUIREMENT COLOR TESTS
    // =========================================================================

    @Nested
    @DisplayName("getRequirementColor")
    class GetRequirementColorTests {

        @Test
        @DisplayName("returns REQUIREMENT_MET for met requirements")
        void returnsMet_ForMetRequirements() {
            assertEquals(TooltipStyles.REQUIREMENT_MET, TooltipStyles.getRequirementColor(true));
        }

        @Test
        @DisplayName("returns REQUIREMENT_UNMET for unmet requirements")
        void returnsUnmet_ForUnmetRequirements() {
            assertEquals(TooltipStyles.REQUIREMENT_UNMET, TooltipStyles.getRequirementColor(false));
        }
    }

    // =========================================================================
    // CONSTANT VALIDATION TESTS
    // =========================================================================

    @Nested
    @DisplayName("Constants")
    class ConstantTests {

        @Test
        @DisplayName("all color constants are valid hex colors")
        void allConstantsAreValidHex() {
            assertValidHexColor(TooltipStyles.QUALITY_POOR);
            assertValidHexColor(TooltipStyles.QUALITY_BELOW_AVERAGE);
            assertValidHexColor(TooltipStyles.QUALITY_AVERAGE);
            assertValidHexColor(TooltipStyles.QUALITY_ABOVE_AVERAGE);
            assertValidHexColor(TooltipStyles.QUALITY_EXCELLENT);
            assertValidHexColor(TooltipStyles.QUALITY_PERFECT);
            assertValidHexColor(TooltipStyles.POSITIVE_MODIFIER);
            assertValidHexColor(TooltipStyles.NEGATIVE_MODIFIER);
            assertValidHexColor(TooltipStyles.REQUIREMENT_MET);
            assertValidHexColor(TooltipStyles.REQUIREMENT_UNMET);
            assertValidHexColor(TooltipStyles.LABEL_GRAY);
            assertValidHexColor(TooltipStyles.VALUE_WHITE);
            assertValidHexColor(TooltipStyles.SEPARATOR);
        }

        private void assertValidHexColor(String color) {
            assertNotNull(color, "Color should not be null");
            assertTrue(color.matches("#[0-9A-Fa-f]{6}"),
                    "Color '" + color + "' should be valid hex format #RRGGBB");
        }
    }
}
