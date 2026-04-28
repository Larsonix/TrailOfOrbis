package io.github.larsonix.trailoforbis.maps.tooltip;

import com.hypixel.hytale.server.core.Message;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.gear.tooltip.TooltipStyles;
import io.github.larsonix.trailoforbis.maps.core.RealmBiomeType;
import io.github.larsonix.trailoforbis.maps.core.RealmLayoutSize;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifier;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifierType;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Builds rich text tooltips for realm maps using Hytale's Message API.
 *
 * <p>Layout mirrors gear tooltips:
 * <pre>
 * [LEGENDARY] Level 50
 * Quality : Excellent (92%)
 * Forest (Large)
 * ────────
 * +50% Monster Damage              (red, quality-adjusted)
 * +63% Monster Life                (red, quality-adjusted)
 * +31% Monster Attack Speed        (red, quality-adjusted)
 * --------                         (separator)
 * +31% Item Rarity                 (gold, quality-adjusted)
 * +19% Experience                  (gold, quality-adjusted)
 * +13% Monster Count               (gold, quality-adjusted)
 * ────────
 * Item Quantity: +25% + 10%        (base gold, compass green)
 * </pre>
 *
 * <p>All modifier values are quality-adjusted (WYSIWYG).
 */
public final class RealmMapTooltipBuilder {

    // =========================================================================
    // COLORS
    // =========================================================================

    /** Difficulty modifiers (prefixes) — red */
    private static final String DIFFICULTY_COLOR = "#FF6666";

    /** Reward modifiers (suffixes) — gold */
    private static final String REWARD_COLOR = "#FFD700";

    /** Fortune's Compass bonus line */
    private static final String COMPASS_COLOR = "#7CF2A7";

    /** Biome name */
    private static final String BIOME_COLOR = "#88CCFF";

    /** Size name */
    private static final String SIZE_COLOR = "#AAFFAA";

    /** Corrupted status */
    private static final String CORRUPTED_COLOR = "#FF4444";

    /** Unidentified status */
    private static final String UNIDENTIFIED_COLOR = "#AAAAAA";

    public RealmMapTooltipBuilder() {
    }

    // =========================================================================
    // MAIN BUILD METHODS
    // =========================================================================

    /**
     * Builds a complete tooltip for a realm map.
     *
     * @param mapData The map data to format
     * @return Complete tooltip as a Message
     */
    @Nonnull
    public Message build(@Nonnull RealmMapData mapData) {
        Objects.requireNonNull(mapData, "mapData cannot be null");

        Message tooltip = Message.empty();

        // [LEGENDARY] Level 50
        tooltip = tooltip.insert(buildRarityBadge(mapData.rarity()));
        tooltip = tooltip.insert(buildLevelSection(mapData.level()));

        // Quality : Excellent (92%)
        tooltip = tooltip.insert(buildQualitySection(mapData.quality()));

        // Forest (Large)
        tooltip = tooltip.insert(buildBiomeSection(mapData.biome(), mapData.size()));

        // Modifiers section (prefixes + separator + suffixes)
        if (mapData.identified() && (mapData.prefixCount() > 0 || mapData.suffixCount() > 0)) {
            tooltip = tooltip.insert(buildSeparator());
            tooltip = tooltip.insert(buildModifiersSection(mapData));
        }

        // IIQ summary (always shown at bottom)
        tooltip = tooltip.insert(buildSeparator());
        tooltip = tooltip.insert(buildIiqSummary(mapData));

        // Unidentified notice
        if (!mapData.identified()) {
            tooltip = tooltip.insert(buildSeparator());
            tooltip = tooltip.insert(buildUnidentifiedNotice());
        }

        // Corrupted status
        if (mapData.corrupted()) {
            tooltip = tooltip.insert(buildCorruptedNotice());
        }

        return tooltip;
    }

    /**
     * Builds a compact tooltip (one line) for action bar display.
     *
     * @param mapData The map data to format
     * @return Compact tooltip as a Message
     */
    @Nonnull
    public Message buildCompact(@Nonnull RealmMapData mapData) {
        Objects.requireNonNull(mapData, "mapData cannot be null");

        GearRarity rarity = mapData.rarity();
        String rarityColor = TooltipStyles.getRarityColor(rarity);

        return Message.empty()
            .insert(Message.raw("[" + rarity.name() + "]").color(rarityColor).bold(true))
            .insert(Message.raw(" Realm Map (Lv. " + mapData.level() + ") - ").color(TooltipStyles.VALUE_WHITE))
            .insert(Message.raw(mapData.biome().getDisplayName()).color(BIOME_COLOR))
            .insert(Message.raw(" (" + mapData.size().getDisplayName() + ")").color(SIZE_COLOR));
    }

    // =========================================================================
    // SECTION BUILDERS
    // =========================================================================

    @Nonnull
    public Message buildRarityBadge(@Nonnull GearRarity rarity) {
        String badgeText = "[" + rarity.getHytaleQualityId().toUpperCase() + "]";
        return Message.raw(badgeText)
            .color(TooltipStyles.getRarityColor(rarity))
            .bold(true);
    }

    @Nonnull
    public Message buildLevelSection(int level) {
        return Message.empty()
            .insert(Message.raw(" Level ").color(TooltipStyles.LABEL_GRAY))
            .insert(Message.raw(String.valueOf(level)).color(TooltipStyles.VALUE_WHITE));
    }

    @Nonnull
    public Message buildQualitySection(int quality) {
        String qualityName = TooltipStyles.getQualityName(quality);
        String qualityColor = TooltipStyles.getQualityColor(quality);

        return Message.raw("\nQuality : ")
                .color(TooltipStyles.LABEL_GRAY)
                .insert(Message.raw(qualityName + " (" + quality + "%)")
                        .color(qualityColor));
    }

    @Nonnull
    public Message buildBiomeSection(@Nonnull RealmBiomeType biome, @Nonnull RealmLayoutSize size) {
        return Message.empty()
            .insert(Message.raw("\n"))
            .insert(Message.raw(biome.getDisplayName()).color(BIOME_COLOR))
            .insert(Message.raw(" (").color(TooltipStyles.LABEL_GRAY))
            .insert(Message.raw(size.getDisplayName()).color(SIZE_COLOR))
            .insert(Message.raw(")").color(TooltipStyles.LABEL_GRAY));
    }

    /**
     * Builds the IIQ summary line at the bottom of the tooltip.
     *
     * <p>Formats:
     * <ul>
     *   <li>Identified + compass: "Item Quantity: +25% + 10%"</li>
     *   <li>Identified, no compass: "Item Quantity: +25%"</li>
     *   <li>Unidentified + compass: "Item Quantity: ? + 10%"</li>
     *   <li>Unidentified, no compass: "Item Quantity: ?"</li>
     * </ul>
     *
     * <p>The base value (gold) comes from ITEM_QUANTITY modifiers, quality-adjusted.
     * The compass value (green) comes from Fortune's Compass stone, only shown if applied.
     */
    @Nonnull
    public Message buildIiqSummary(@Nonnull RealmMapData mapData) {
        Message line = Message.raw("\nItem Quantity: ").color(TooltipStyles.LABEL_GRAY);

        if (mapData.identified()) {
            line = line.insert(Message.raw("+" + mapData.getBaseItemQuantity() + "%").color(REWARD_COLOR));
        } else {
            line = line.insert(Message.raw("?").color(UNIDENTIFIED_COLOR));
        }

        if (mapData.fortunesCompassBonus() > 0) {
            line = line.insert(Message.raw(" + " + mapData.fortunesCompassBonus() + "%").color(COMPASS_COLOR));
        }

        return line;
    }

    /**
     * Builds the unified modifiers section with quality-adjusted values.
     *
     * <p>Mirrors gear's {@code RichTooltipFormatter.buildModifiersSection()}:
     * prefixes above the dashed separator, suffixes below.
     * All values are multiplied by {@code qualityMultiplier()}.
     *
     * @param mapData The map data (provides modifiers and quality multiplier)
     * @return Formatted modifier section
     */
    @Nonnull
    public Message buildModifiersSection(@Nonnull RealmMapData mapData) {
        Message section = Message.empty();
        double qualityMult = mapData.qualityMultiplier();

        // Prefix zone (difficulty — red)
        for (RealmModifier mod : mapData.prefixes()) {
            section = section.insert(buildModifierLine(mod, qualityMult, true));
        }

        // Dashed separator between prefix and suffix zones
        section = section.insert(buildModifierGroupSeparator());

        // Suffix zone (rewards — gold)
        for (RealmModifier mod : mapData.suffixes()) {
            section = section.insert(buildModifierLine(mod, qualityMult, false));
        }

        return section;
    }

    /**
     * Builds a single modifier line with quality adjustment.
     *
     * <p>Format: "+40% Monster Damage" where value is quality-adjusted.
     * Binary modifiers (like NO_REGENERATION) display name only.
     *
     * @param mod The modifier
     * @param qualityMult Quality multiplier to apply
     * @param isDifficulty true for red (prefix), false for gold (suffix)
     */
    @Nonnull
    private Message buildModifierLine(@Nonnull RealmModifier mod, double qualityMult, boolean isDifficulty) {
        RealmModifierType type = mod.type();
        String color = isDifficulty ? DIFFICULTY_COLOR : REWARD_COLOR;

        String displayText;
        if (type.isBinary()) {
            displayText = type.getDisplayName();
        } else {
            int adjustedValue = (int) Math.round(mod.value() * qualityMult);
            displayText = "+" + adjustedValue + "% " + type.getDisplayName();
        }

        Message line = Message.raw("\n" + displayText).color(color);

        if (mod.locked()) {
            line = line.insert(Message.raw(" [Locked]").color(TooltipStyles.LABEL_GRAY).italic(true));
        }

        return line;
    }

    /**
     * Builds a dark gray dashed separator between prefix and suffix zones.
     */
    @Nonnull
    private Message buildModifierGroupSeparator() {
        return Message.raw("\n--------").color(TooltipStyles.SEPARATOR);
    }

    @Nonnull
    public Message buildUnidentifiedNotice() {
        return Message.raw("\n(Unidentified)").color(UNIDENTIFIED_COLOR).italic(true);
    }

    @Nonnull
    public Message buildCorruptedNotice() {
        return Message.raw("\n(Corrupted)").color(CORRUPTED_COLOR).bold(true);
    }

    @Nonnull
    public Message buildSeparator() {
        return Message.raw("\n").color(TooltipStyles.SEPARATOR);
    }

    // =========================================================================
    // PLAIN TEXT (for logging/debugging)
    // =========================================================================

    /**
     * Builds a plain text tooltip (for logging/debugging).
     *
     * @param mapData The map data to format
     * @return Plain text tooltip
     */
    @Nonnull
    public String buildPlainText(@Nonnull RealmMapData mapData) {
        Objects.requireNonNull(mapData, "mapData cannot be null");

        StringBuilder sb = new StringBuilder();
        double qualityMult = mapData.qualityMultiplier();

        // Header
        sb.append("[").append(mapData.rarity().name()).append("] ");
        sb.append("Level ").append(mapData.level()).append("\n");

        // Quality
        sb.append("Quality : ").append(TooltipStyles.getQualityName(mapData.quality()));
        sb.append(" (").append(mapData.quality()).append("%)\n");

        // Biome and size
        sb.append(mapData.biome().getDisplayName());
        sb.append(" (").append(mapData.size().getDisplayName()).append(")");

        // Modifiers (quality-adjusted)
        if (mapData.identified() && (mapData.prefixCount() > 0 || mapData.suffixCount() > 0)) {
            sb.append("\n");

            // Prefixes
            for (RealmModifier mod : mapData.prefixes()) {
                sb.append("\n").append(formatModifierPlainText(mod, qualityMult));
                if (mod.locked()) sb.append(" [Locked]");
            }

            // Separator
            sb.append("\n--------");

            // Suffixes
            for (RealmModifier mod : mapData.suffixes()) {
                sb.append("\n").append(formatModifierPlainText(mod, qualityMult));
                if (mod.locked()) sb.append(" [Locked]");
            }
        }

        // IIQ summary
        sb.append("\n\nItem Quantity: ");
        if (mapData.identified()) {
            sb.append("+").append(mapData.getBaseItemQuantity()).append("%");
        } else {
            sb.append("?");
        }
        if (mapData.fortunesCompassBonus() > 0) {
            sb.append(" + ").append(mapData.fortunesCompassBonus()).append("%");
        }

        // Status
        if (!mapData.identified()) {
            sb.append("\n\n(Unidentified)");
        }

        if (mapData.corrupted()) {
            sb.append("\n(Corrupted)");
        }

        return sb.toString();
    }

    /**
     * Formats a modifier value for plain text, quality-adjusted.
     */
    private String formatModifierPlainText(@Nonnull RealmModifier mod, double qualityMult) {
        if (mod.type().isBinary()) {
            return mod.type().getDisplayName();
        }
        int adjusted = (int) Math.round(mod.value() * qualityMult);
        return "+" + adjusted + "% " + mod.type().getDisplayName();
    }
}
