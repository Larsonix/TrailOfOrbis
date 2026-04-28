package io.github.larsonix.trailoforbis.stones.tooltip;

import com.hypixel.hytale.server.core.Message;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.gear.tooltip.TooltipStyles;
import io.github.larsonix.trailoforbis.stones.ItemTargetType;
import io.github.larsonix.trailoforbis.stones.StoneItemData;
import io.github.larsonix.trailoforbis.stones.StoneType;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Builds rich text tooltips for stone items using Hytale's Message API.
 *
 * <p>Creates tooltips with:
 * <ul>
 *   <li>Rarity badge (colored and bold)</li>
 *   <li>Stone name (colored by rarity)</li>
 *   <li>Description (effect explanation)</li>
 *   <li>Target type (gear, map, or both)</li>
 *   <li>Corruption status compatibility</li>
 * </ul>
 *
 * <p>All text is styled using hex colors for native Hytale rendering.
 */
public final class StoneTooltipBuilder {

    // =========================================================================
    // STONE TOOLTIP COLORS
    // =========================================================================

    /** Color for description text */
    private static final String DESCRIPTION_COLOR = "#CCCCCC";

    /** Color for target type info */
    private static final String TARGET_COLOR = "#88CCFF";

    /** Color for positive status (can use on corrupted) */
    private static final String POSITIVE_STATUS_COLOR = "#88FF88";

    /** Color for negative status (cannot use on corrupted) */
    private static final String NEGATIVE_STATUS_COLOR = "#888888";

    /** Color for usage hint text */
    private static final String USAGE_HINT_COLOR = "#AAAAAA";

    /**
     * Creates a new StoneTooltipBuilder.
     */
    public StoneTooltipBuilder() {
        // No configuration required
    }

    // =========================================================================
    // MAIN BUILD METHODS
    // =========================================================================

    /**
     * Builds a complete tooltip for a stone item.
     *
     * @param stoneData The stone data to format
     * @return Complete tooltip as a Message
     */
    @Nonnull
    public Message build(@Nonnull StoneItemData stoneData) {
        Objects.requireNonNull(stoneData, "stoneData cannot be null");
        return build(stoneData.stoneType());
    }

    /**
     * Builds a complete tooltip for a stone type.
     *
     * @param stoneType The stone type to format
     * @return Complete tooltip as a Message
     */
    @Nonnull
    public Message build(@Nonnull StoneType stoneType) {
        Objects.requireNonNull(stoneType, "stoneType cannot be null");

        GearRarity rarity = stoneType.getRarity();
        Message tooltip = Message.empty();

        // Section 1: Rarity badge + name
        tooltip = tooltip.insert(buildNameSection(stoneType, rarity));

        // Section 2: Description
        tooltip = tooltip.insert(buildDescriptionSection(stoneType.getDescription()));

        // Section 3: Target and usage info
        tooltip = tooltip.insert(buildUsageSection(stoneType));

        return tooltip;
    }

    /**
     * Builds only the description portion of a tooltip for native item tooltip rendering.
     *
     * <p>Excludes name/rarity section (handled by Hytale's native #Name label).
     * Used by {@code StoneTooltipSyncService} to generate translation values
     * that are injected into the native tooltip via UpdateTranslations.
     *
     * @param stoneType The stone type to format
     * @return Description + usage info as a Message
     */
    @Nonnull
    public Message buildDescription(@Nonnull StoneType stoneType) {
        Objects.requireNonNull(stoneType, "stoneType cannot be null");

        Message tooltip = Message.empty();
        tooltip = tooltip.insert(buildDescriptionSection(stoneType.getDescription()));
        tooltip = tooltip.insert(buildUsageSection(stoneType));
        return tooltip;
    }

    /**
     * Builds a compact tooltip (one line) for action bar display.
     *
     * @param stoneType The stone type to format
     * @return Compact tooltip as a Message
     */
    @Nonnull
    public Message buildCompact(@Nonnull StoneType stoneType) {
        Objects.requireNonNull(stoneType, "stoneType cannot be null");

        GearRarity rarity = stoneType.getRarity();
        String rarityColor = TooltipStyles.getRarityColor(rarity);

        // Format: "[RARE] Gaia's Calibration"
        return Message.empty()
            .insert(Message.raw("[" + rarity.name() + "]").color(rarityColor).bold(true))
            .insert(Message.raw(" " + stoneType.getDisplayName()).color(rarityColor));
    }

    // =========================================================================
    // SECTION BUILDERS
    // =========================================================================

    /**
     * Builds the name section with rarity badge.
     *
     * <p>Format: "[RARE] Gaia's Calibration"
     */
    @Nonnull
    private Message buildNameSection(@Nonnull StoneType stoneType, @Nonnull GearRarity rarity) {
        String rarityColor = TooltipStyles.getRarityColor(rarity);
        String badgeText = "[" + rarity.getHytaleQualityId().toUpperCase() + "]";

        return Message.empty()
            .insert(Message.raw(badgeText).color(rarityColor).bold(true))
            .insert(Message.raw(" " + stoneType.getDisplayName()).color(rarityColor));
    }

    /**
     * Builds the description section.
     *
     * <p>Format: "\nRandomises the numeric values of all modifiers"
     */
    @Nonnull
    private Message buildDescriptionSection(@Nonnull String description) {
        return Message.raw("\n" + description).color(DESCRIPTION_COLOR);
    }

    /**
     * Builds the usage info section.
     *
     * <p>Format:
     * <pre>
     *
     * Target: Gear & Maps
     * (Works on corrupted items)
     * </pre>
     */
    @Nonnull
    private Message buildUsageSection(@Nonnull StoneType stoneType) {
        Message section = Message.empty();

        // Target type
        String targetText = getTargetTypeText(stoneType.getTargetType());
        section = section
            .insert(Message.raw("\n\nTarget : ").color(TooltipStyles.LABEL_GRAY))
            .insert(Message.raw(targetText).color(TARGET_COLOR));

        // Corruption status
        if (stoneType.worksOnCorrupted()) {
            section = section.insert(
                Message.raw("\n(Works on corrupted items)").color(POSITIVE_STATUS_COLOR).italic(true));
        } else {
            section = section.insert(
                Message.raw("\n(Cannot use on corrupted items)").color(NEGATIVE_STATUS_COLOR).italic(true));
        }

        // Usage hint
        String usageHint = stoneType.isRefundStone()
            ? "(Right-click to consume)"
            : "(Right-click to use)";
        section = section.insert(
            Message.raw("\n" + usageHint).color(USAGE_HINT_COLOR).italic(true));

        return section;
    }

    /**
     * Gets human-readable text for a target type.
     */
    @Nonnull
    private String getTargetTypeText(@Nonnull ItemTargetType targetType) {
        return switch (targetType) {
            case GEAR_ONLY -> "Gear Only";
            case MAP_ONLY -> "Maps Only";
            case BOTH -> "Gear & Maps";
        };
    }

    // =========================================================================
    // STRING BUILDERS (for compatibility)
    // =========================================================================

    /**
     * Builds a plain text tooltip (for logging/debugging).
     *
     * @param stoneType The stone type to format
     * @return Plain text tooltip as a String
     */
    @Nonnull
    public String buildPlainText(@Nonnull StoneType stoneType) {
        Objects.requireNonNull(stoneType, "stoneType cannot be null");

        StringBuilder sb = new StringBuilder();

        // Header
        sb.append("[").append(stoneType.getRarity().name()).append("] ");
        sb.append(stoneType.getDisplayName()).append("\n");

        // Description
        sb.append(stoneType.getDescription()).append("\n");

        // Target type
        sb.append("\nTarget : ").append(getTargetTypeText(stoneType.getTargetType()));

        // Corruption status
        if (stoneType.worksOnCorrupted()) {
            sb.append("\n(Works on corrupted items)");
        } else {
            sb.append("\n(Cannot use on corrupted items)");
        }

        // Usage hint
        sb.append("\n").append(stoneType.isRefundStone()
            ? "(Right-click to consume)"
            : "(Right-click to use)");

        return sb.toString();
    }

    /**
     * Builds a plain text tooltip for stone data.
     *
     * @param stoneData The stone data to format
     * @return Plain text tooltip as a String
     */
    @Nonnull
    public String buildPlainText(@Nonnull StoneItemData stoneData) {
        Objects.requireNonNull(stoneData, "stoneData cannot be null");
        return buildPlainText(stoneData.stoneType());
    }
}
