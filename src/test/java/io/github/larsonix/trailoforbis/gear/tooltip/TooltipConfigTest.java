package io.github.larsonix.trailoforbis.gear.tooltip;

import io.github.larsonix.trailoforbis.gear.model.GearRarity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TooltipConfig record.
 */
@DisplayName("TooltipConfig")
class TooltipConfigTest {

    // =========================================================================
    // DEFAULTS TESTS
    // =========================================================================

    @Nested
    @DisplayName("defaults()")
    class DefaultsTests {

        @Test
        @DisplayName("returns non-null config")
        void returnsNonNullConfig() {
            assertNotNull(TooltipConfig.defaults());
        }

        @Test
        @DisplayName("has all sections enabled by default")
        void hasAllSectionsEnabled() {
            TooltipConfig config = TooltipConfig.defaults();

            assertTrue(config.showRarityBadge());
            assertTrue(config.showItemLevel());
            assertTrue(config.showQuality());
            assertTrue(config.showModifiers());
            assertTrue(config.showRequirements());
        }

        @Test
        @DisplayName("has bold high rarity enabled by default")
        void hasBoldHighRarityEnabled() {
            TooltipConfig config = TooltipConfig.defaults();

            assertTrue(config.boldHighRarity());
        }

        @Test
        @DisplayName("has EPIC as bold threshold by default")
        void hasEpicAsBoldThreshold() {
            TooltipConfig config = TooltipConfig.defaults();

            assertEquals(GearRarity.EPIC, config.boldThreshold());
        }

        @Test
        @DisplayName("includes prefix and suffix by default")
        void includesPrefixAndSuffixByDefault() {
            TooltipConfig config = TooltipConfig.defaults();

            assertTrue(config.includePrefix());
            assertTrue(config.includeSuffix());
        }
    }

    // =========================================================================
    // BUILDER TESTS
    // =========================================================================

    @Nested
    @DisplayName("builder()")
    class BuilderTests {

        @Test
        @DisplayName("creates config with custom values")
        void createsConfigWithCustomValues() {
            TooltipConfig config = TooltipConfig.builder()
                    .showRarityBadge(false)
                    .showItemLevel(false)
                    .showQuality(false)
                    .showModifiers(false)
                    .showRequirements(false)
                    .boldHighRarity(false)
                    .boldThreshold(GearRarity.LEGENDARY)
                    .includePrefix(false)
                    .includeSuffix(false)
                    .build();

            assertFalse(config.showRarityBadge());
            assertFalse(config.showItemLevel());
            assertFalse(config.showQuality());
            assertFalse(config.showModifiers());
            assertFalse(config.showRequirements());
            assertFalse(config.boldHighRarity());
            assertEquals(GearRarity.LEGENDARY, config.boldThreshold());
            assertFalse(config.includePrefix());
            assertFalse(config.includeSuffix());
        }

        @Test
        @DisplayName("throws for null bold threshold")
        void throwsForNullBoldThreshold() {
            assertThrows(NullPointerException.class, () ->
                    TooltipConfig.builder().boldThreshold(null).build());
        }
    }

    // =========================================================================
    // TO BUILDER TESTS
    // =========================================================================

    @Nested
    @DisplayName("toBuilder()")
    class ToBuilderTests {

        @Test
        @DisplayName("preserves original values")
        void preservesOriginalValues() {
            TooltipConfig original = TooltipConfig.builder()
                    .showRarityBadge(false)
                    .showQuality(false)
                    .boldThreshold(GearRarity.MYTHIC)
                    .build();

            TooltipConfig copy = original.toBuilder().build();

            assertEquals(original.showRarityBadge(), copy.showRarityBadge());
            assertEquals(original.showQuality(), copy.showQuality());
            assertEquals(original.boldThreshold(), copy.boldThreshold());
        }

        @Test
        @DisplayName("allows modification")
        void allowsModification() {
            TooltipConfig original = TooltipConfig.defaults();

            TooltipConfig modified = original.toBuilder()
                    .showRarityBadge(false)
                    .build();

            assertTrue(original.showRarityBadge());
            assertFalse(modified.showRarityBadge());
        }
    }

    // =========================================================================
    // FROM YAML TESTS
    // =========================================================================

    @Nested
    @DisplayName("fromYaml()")
    class FromYamlTests {

        @Test
        @DisplayName("parses complete config")
        void parsesCompleteConfig() {
            Map<String, Object> config = createFullYamlConfig();

            TooltipConfig result = TooltipConfig.fromYaml(config);

            assertFalse(result.showRarityBadge());
            assertFalse(result.showItemLevel());
            assertFalse(result.showQuality());
            assertFalse(result.showModifiers());
            assertFalse(result.showRequirements());
            assertFalse(result.boldHighRarity());
            assertEquals(GearRarity.LEGENDARY, result.boldThreshold());
            assertFalse(result.includePrefix());
            assertFalse(result.includeSuffix());
        }

        @Test
        @DisplayName("uses defaults for missing sections")
        void usesDefaultsForMissingSections() {
            Map<String, Object> config = new HashMap<>();

            TooltipConfig result = TooltipConfig.fromYaml(config);

            // All defaults should be applied
            assertTrue(result.showRarityBadge());
            assertTrue(result.showItemLevel());
            assertEquals(GearRarity.EPIC, result.boldThreshold());
        }

        @Test
        @DisplayName("uses defaults for missing values in sections")
        void usesDefaultsForMissingValuesInSections() {
            Map<String, Object> config = new HashMap<>();
            Map<String, Object> sections = new HashMap<>();
            sections.put("rarity_badge", false);
            // item_level missing - should default to true
            config.put("sections", sections);

            TooltipConfig result = TooltipConfig.fromYaml(config);

            assertFalse(result.showRarityBadge());
            assertTrue(result.showItemLevel());
        }

        @Test
        @DisplayName("handles invalid bold threshold gracefully")
        void handlesInvalidBoldThreshold() {
            Map<String, Object> config = new HashMap<>();
            Map<String, Object> styling = new HashMap<>();
            styling.put("bold_threshold", "INVALID_RARITY");
            config.put("styling", styling);

            TooltipConfig result = TooltipConfig.fromYaml(config);

            // Should keep default (EPIC)
            assertEquals(GearRarity.EPIC, result.boldThreshold());
        }

        @Test
        @DisplayName("throws for null config")
        void throwsForNullConfig() {
            assertThrows(NullPointerException.class, () -> TooltipConfig.fromYaml(null));
        }
    }

    // =========================================================================
    // RECORD VALIDATION TESTS
    // =========================================================================

    @Nested
    @DisplayName("Record Validation")
    class RecordValidationTests {

        @Test
        @DisplayName("throws for null bold threshold in constructor")
        void throwsForNullBoldThresholdInConstructor() {
            assertThrows(NullPointerException.class, () ->
                    new TooltipConfig(true, true, true, true, true, true, true, null, true, true));
        }

        @Test
        @DisplayName("accepts valid parameters")
        void acceptsValidParameters() {
            assertDoesNotThrow(() ->
                    new TooltipConfig(true, true, true, true, true, true, true, GearRarity.EPIC, true, true));
        }
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private Map<String, Object> createFullYamlConfig() {
        Map<String, Object> config = new HashMap<>();

        Map<String, Object> sections = new HashMap<>();
        sections.put("rarity_badge", false);
        sections.put("item_level", false);
        sections.put("quality", false);
        sections.put("modifiers", false);
        sections.put("requirements", false);
        config.put("sections", sections);

        Map<String, Object> styling = new HashMap<>();
        styling.put("bold_high_rarity", false);
        styling.put("bold_threshold", "LEGENDARY");
        config.put("styling", styling);

        Map<String, Object> nameFormat = new HashMap<>();
        nameFormat.put("include_prefix", false);
        nameFormat.put("include_suffix", false);
        config.put("name_format", nameFormat);

        return config;
    }
}
