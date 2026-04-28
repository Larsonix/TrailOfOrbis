package io.github.larsonix.trailoforbis.util;

/**
 * Constants for Hytale Message API hex colors.
 *
 * <p>Hytale uses hex color codes (e.g., "#FF5555") instead of Minecraft-style
 * section codes (e.g., "§c"). Use these constants with {@code Message.raw("text").color(MessageColors.ERROR)}.
 *
 * <p>Color mapping from Minecraft codes:
 * <ul>
 *   <li>§c (Red) → #FF5555</li>
 *   <li>§a (Green) → #55FF55</li>
 *   <li>§b (Aqua) → #55FFFF</li>
 *   <li>§e (Yellow) → #FFFF55</li>
 *   <li>§6 (Gold) → #FFAA00</li>
 *   <li>§7 (Gray) → #AAAAAA</li>
 *   <li>§f (White) → #FFFFFF</li>
 *   <li>§9 (Blue) → #5555FF</li>
 * </ul>
 */
public final class MessageColors {
    /** Red - for errors and warnings (§c) */
    public static final String ERROR = "#FF5555";

    /** Green - for success messages (§a) */
    public static final String SUCCESS = "#55FF55";

    /** Aqua - for info messages (§b) */
    public static final String INFO = "#55FFFF";

    /** Yellow - for highlights and labels (§e) */
    public static final String WARNING = "#FFFF55";

    /** Gold - for headers and important text (§6) */
    public static final String GOLD = "#FFAA00";

    /** Gray - for secondary/muted text (§7) */
    public static final String GRAY = "#AAAAAA";

    /** White - for values and normal text (§f) */
    public static final String WHITE = "#FFFFFF";

    /** Blue - for intelligence/mana (§9) */
    public static final String BLUE = "#5555FF";

    /** Orange - for stamina/dexterity */
    public static final String ORANGE = "#FFA500";

    /** Purple - for magic/signature energy */
    public static final String PURPLE = "#AA55FF";

    /** Dark Red - for strength */
    public static final String DARK_RED = "#CC4444";

    /** Dark Purple - for mythic/legendary mobs (§5) */
    public static final String DARK_PURPLE = "#AA00AA";

    /** Light Blue - for oxygen/water */
    public static final String LIGHT_BLUE = "#88CCFF";

    /** Pink - for vitality/health */
    public static final String PINK = "#FF88AA";

    /** XP Gain - bright cyan/turquoise for experience points */
    public static final String XP_GAIN = "#00FFCC";

    /** Level Up - bright gold for level up notifications */
    public static final String LEVEL_UP = "#FFD700";

    private MessageColors() {
        // Utility class - prevent instantiation
    }
}
