package io.github.larsonix.trailoforbis.mobs.infobar;

import com.hypixel.hytale.server.core.Message;
import io.github.larsonix.trailoforbis.elemental.ElementType;
import io.github.larsonix.trailoforbis.mobs.classification.RPGMobClass;
import io.github.larsonix.trailoforbis.mobs.component.MobScalingComponent;
import io.github.larsonix.trailoforbis.mobs.model.MobStats;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Formats mob info bar text with tier-based colors.
 *
 * <p>Creates display messages based on RPG Classification using the Hytale Message API.
 * Supports rich text formatting with colors and bold styling for mob tiers.
 */
public final class MobInfoFormatter {

    // ==================== Private Constructor ====================

    private MobInfoFormatter() {
        // Utility class - no instantiation
    }

    // ==================== Formatting Methods ====================

    /**
     * Formats the mob info bar text from a MobScalingComponent.
     *
     * @param scaling The mob's scaling component
     * @return Formatted Message with colors
     */
    @Nonnull
    public static Message format(@Nonnull MobScalingComponent scaling) {
        MobStats stats = scaling.getStats();
        ElementType primaryElement = null;
        if (stats != null && stats.hasElementalDamage()) {
            primaryElement = stats.getPrimaryElement();
        }

        return format(
            scaling.getMobLevel(),
            scaling.getEffectivePower(),
            scaling.getClassification(),
            primaryElement
        );
    }

    /**
     * Formats the mob info bar text from individual values (without element).
     */
    @Nonnull
    public static Message format(int level, int power, RPGMobClass classification) {
        return format(level, power, classification, null);
    }

    /**
     * Formats the mob info bar text from level and MobStats (assumes MONSTER class if unknown).
     */
    @Nonnull
    public static Message format(int level, @Nonnull MobStats stats) {
        ElementType primaryElement = null;
        if (stats.hasElementalDamage()) {
            primaryElement = stats.getPrimaryElement();
        }

        return format(
            level,
            stats.getEffectivePower(),
            RPGMobClass.HOSTILE,
            primaryElement
        );
    }

    /**
     * Formats the mob info bar text from individual values with element indicator.
     *
     * @param level The mob's level
     * @param power The mob's power level (currently unused for display)
     * @param classification The mob's RPG class
     * @param primaryElement The mob's primary element (nullable)
     * @return Formatted plain text string (nameplates don't support colors)
     */
    @Nonnull
    public static Message format(int level, int power, RPGMobClass classification,
                                @Nullable ElementType primaryElement) {
        return Message.raw(formatPlainText(level, power, classification, primaryElement));
    }

    /**
     * Formats the mob info bar as plain text (for nameplates).
     *
     * @param level The mob's level
     * @param power The mob's power level (currently unused)
     * @param classification The mob's RPG class
     * @param primaryElement The mob's primary element (currently unused)
     * @return Plain text like "Lv27" or "[Elite] Lv27"
     */
    @Nonnull
    public static String formatPlainText(int level, int power, RPGMobClass classification,
                                        @Nullable ElementType primaryElement) {
        StringBuilder text = new StringBuilder();

        String badge = getBadge(classification);
        if (!badge.isEmpty()) {
            text.append(badge).append(" ");
        }

        // Simple level-only display: "Lv27" or "[Elite] Lv27"
        text.append("Lv").append(level);

        return text.toString();
    }

    /**
     * Formats an element indicator for display.
     *
     * <p>Creates a compact indicator like [F], [C], [L], or [X] for
     * Fire, Cold, Lightning, or Chaos respectively.
     *
     * @param element The element type
     * @return Formatted Message with element color
     */
    @Nonnull
    public static Message formatElementIndicator(@Nonnull ElementType element) {
        String color;
        switch (element) {
            case FIRE: color = MessageColors.ERROR; break;
            case WATER: color = MessageColors.INFO; break;
            case LIGHTNING: color = MessageColors.WARNING; break;
            case VOID: color = MessageColors.DARK_PURPLE; break;
            default: color = MessageColors.WHITE;
        }
        return Message.raw("[" + element.getShortCode() + "]").color(color);
    }

    /**
     * Formats a simple level-only display (for non-scaled mobs).
     *
     * @param level The mob's level
     * @return Formatted Message with level
     */
    @Nonnull
    public static Message formatLevelOnly(int level) {
        return Message.raw("Lv" + level).color(MessageColors.INFO);
    }

    /**
     * Gets the color code for a mob tier.
     */
    @Nonnull
    private static String getTierColor(RPGMobClass classification) {
        switch (classification) {
            case BOSS:
                return MessageColors.DARK_PURPLE;
            case ELITE:
                return MessageColors.GOLD;
            default:
                return MessageColors.WHITE;
        }
    }

    /**
     * Gets the badge text for a mob tier.
     */
    @Nonnull
    private static String getBadge(RPGMobClass classification) {
        switch (classification) {
            case BOSS: return "[Boss]";
            case ELITE: return "[Elite]";
            default: return "";
        }
    }

    /**
     * Strips all color codes from a string.
     *
     * @param text Text with color codes
     * @return Plain text without color codes
     */
    @Nonnull
    public static String stripColors(@Nonnull String text) {
        return text.replaceAll("\u00A7[0-9a-fk-or]", "");
    }
}
