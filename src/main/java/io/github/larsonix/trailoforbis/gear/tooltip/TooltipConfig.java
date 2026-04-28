package io.github.larsonix.trailoforbis.gear.tooltip;

import io.github.larsonix.trailoforbis.gear.model.GearRarity;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration for rich tooltip formatting.
 *
 * <p>Controls which sections are displayed and styling options.
 * Loaded from tooltip.yml.
 *
 * @param showRarityBadge Whether to show the rarity badge (e.g., "[LEGENDARY]")
 * @param showItemLevel Whether to show item level
 * @param showQuality Whether to show quality rating
 * @param showModifiers Whether to show stat modifiers
 * @param showRequirements Whether to show attribute requirements
 * @param showHexInfo Whether to show Hexcode hex casting/spellbook info (requires Hexcode loaded)
 * @param boldHighRarity Whether to bold high-rarity item names
 * @param boldThreshold Minimum rarity for bold styling
 * @param includePrefix Whether to include prefix in item name
 * @param includeSuffix Whether to include suffix in item name
 */
public record TooltipConfig(
        boolean showRarityBadge,
        boolean showItemLevel,
        boolean showQuality,
        boolean showModifiers,
        boolean showRequirements,
        boolean showHexInfo,
        boolean boldHighRarity,
        GearRarity boldThreshold,
        boolean includePrefix,
        boolean includeSuffix
) {

    /**
     * Creates a TooltipConfig with validation.
     */
    public TooltipConfig {
        Objects.requireNonNull(boldThreshold, "boldThreshold cannot be null");
    }

    /**
     * Returns the default configuration.
     *
     * <p>Defaults:
     * <ul>
     *   <li>All sections enabled</li>
     *   <li>Bold threshold: EPIC</li>
     *   <li>Include prefix and suffix in names</li>
     * </ul>
     */
    @Nonnull
    public static TooltipConfig defaults() {
        return new TooltipConfig(
                true,              // showRarityBadge
                true,              // showItemLevel
                true,              // showQuality
                true,              // showModifiers
                true,              // showRequirements
                true,              // showHexInfo
                true,              // boldHighRarity
                GearRarity.EPIC,   // boldThreshold
                true,              // includePrefix
                true               // includeSuffix
        );
    }

    /**
     * Creates a builder for custom configuration.
     */
    @Nonnull
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a builder initialized with this config's values.
     */
    @Nonnull
    public Builder toBuilder() {
        return new Builder()
                .showRarityBadge(showRarityBadge)
                .showItemLevel(showItemLevel)
                .showQuality(showQuality)
                .showModifiers(showModifiers)
                .showRequirements(showRequirements)
                .showHexInfo(showHexInfo)
                .boldHighRarity(boldHighRarity)
                .boldThreshold(boldThreshold)
                .includePrefix(includePrefix)
                .includeSuffix(includeSuffix);
    }

    /**
     * Builder for TooltipConfig.
     */
    public static final class Builder {
        private boolean showRarityBadge = true;
        private boolean showItemLevel = true;
        private boolean showQuality = true;
        private boolean showModifiers = true;
        private boolean showRequirements = true;
        private boolean showHexInfo = true;
        private boolean boldHighRarity = true;
        private GearRarity boldThreshold = GearRarity.EPIC;
        private boolean includePrefix = true;
        private boolean includeSuffix = true;

        private Builder() {}

        public Builder showRarityBadge(boolean show) {
            this.showRarityBadge = show;
            return this;
        }

        public Builder showItemLevel(boolean show) {
            this.showItemLevel = show;
            return this;
        }

        public Builder showQuality(boolean show) {
            this.showQuality = show;
            return this;
        }

        public Builder showModifiers(boolean show) {
            this.showModifiers = show;
            return this;
        }

        public Builder showRequirements(boolean show) {
            this.showRequirements = show;
            return this;
        }

        public Builder showHexInfo(boolean show) {
            this.showHexInfo = show;
            return this;
        }

        public Builder boldHighRarity(boolean bold) {
            this.boldHighRarity = bold;
            return this;
        }

        public Builder boldThreshold(@Nonnull GearRarity threshold) {
            this.boldThreshold = Objects.requireNonNull(threshold);
            return this;
        }

        public Builder includePrefix(boolean include) {
            this.includePrefix = include;
            return this;
        }

        public Builder includeSuffix(boolean include) {
            this.includeSuffix = include;
            return this;
        }

        @Nonnull
        public TooltipConfig build() {
            return new TooltipConfig(
                    showRarityBadge,
                    showItemLevel,
                    showQuality,
                    showModifiers,
                    showRequirements,
                    showHexInfo,
                    boldHighRarity,
                    boldThreshold,
                    includePrefix,
                    includeSuffix
            );
        }
    }

    /**
     * Loads configuration from a YAML map.
     *
     * @param config The configuration map (from YAML)
     * @return Parsed TooltipConfig
     */
    @Nonnull
    @SuppressWarnings("unchecked")
    public static TooltipConfig fromYaml(@Nonnull Map<String, Object> config) {
        Objects.requireNonNull(config, "config cannot be null");

        Builder builder = builder();

        // Parse sections
        Object sectionsObj = config.get("sections");
        if (sectionsObj instanceof Map<?, ?> sections) {
            builder.showRarityBadge(getBoolean(sections, "rarity_badge", true));
            builder.showItemLevel(getBoolean(sections, "item_level", true));
            builder.showQuality(getBoolean(sections, "quality", true));
            builder.showModifiers(getBoolean(sections, "modifiers", true));
            builder.showRequirements(getBoolean(sections, "requirements", true));
            builder.showHexInfo(getBoolean(sections, "hex_info", true));
        }

        // Parse styling
        Object stylingObj = config.get("styling");
        if (stylingObj instanceof Map<?, ?> styling) {
            builder.boldHighRarity(getBoolean(styling, "bold_high_rarity", true));

            Object thresholdObj = styling.get("bold_threshold");
            if (thresholdObj instanceof String thresholdStr) {
                try {
                    builder.boldThreshold(GearRarity.fromString(thresholdStr));
                } catch (IllegalArgumentException ignored) {
                    // Keep default
                }
            }
        }

        // Parse name format
        Object nameFormatObj = config.get("name_format");
        if (nameFormatObj instanceof Map<?, ?> nameFormat) {
            builder.includePrefix(getBoolean(nameFormat, "include_prefix", true));
            builder.includeSuffix(getBoolean(nameFormat, "include_suffix", true));
        }

        return builder.build();
    }

    /**
     * Gets a boolean from a map with a default value.
     */
    private static boolean getBoolean(Map<?, ?> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return defaultValue;
    }
}
