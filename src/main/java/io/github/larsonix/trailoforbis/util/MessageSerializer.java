package io.github.larsonix.trailoforbis.util;

import com.hypixel.hytale.protocol.FormattedMessage;
import com.hypixel.hytale.protocol.MaybeBool;
import com.hypixel.hytale.server.core.Message;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Utility for serializing Hytale Message objects to various string formats.
 *
 * <p>Provides conversions for different display contexts:
 * <ul>
 *   <li>{@link #toLegacyString(Message)} - Legacy § codes for nameplates</li>
 *   <li>{@link #toJson(Message)} - JSON format for ItemTranslationProperties</li>
 *   <li>{@link #toPlainText(Message)} - Plain text without formatting</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>All methods are thread-safe and stateless.
 */
public final class MessageSerializer {

    private static final Map<String, String> HEX_TO_LEGACY = new HashMap<>();

    static {
        // Mapping based on MessageColors.java
        HEX_TO_LEGACY.put("#FF5555", "\u00A7c"); // ERROR / Red
        HEX_TO_LEGACY.put("#55FF55", "\u00A7a"); // SUCCESS / Green
        HEX_TO_LEGACY.put("#55FFFF", "\u00A7b"); // INFO / Aqua
        HEX_TO_LEGACY.put("#FFFF55", "\u00A7e"); // WARNING / Yellow
        HEX_TO_LEGACY.put("#FFAA00", "\u00A76"); // GOLD
        HEX_TO_LEGACY.put("#AAAAAA", "\u00A77"); // GRAY
        HEX_TO_LEGACY.put("#FFFFFF", "\u00A7f"); // WHITE
        HEX_TO_LEGACY.put("#5555FF", "\u00A79"); // BLUE
        HEX_TO_LEGACY.put("#AA00AA", "\u00A75"); // DARK_PURPLE
        HEX_TO_LEGACY.put("#AA55FF", "\u00A7d"); // PURPLE (Mapping to Light Purple/Pink for visibility)
        HEX_TO_LEGACY.put("#CC4444", "\u00A74"); // DARK_RED
        HEX_TO_LEGACY.put("#00FFCC", "\u00A73"); // XP_GAIN (Dark Aqua as closest approximation)
        HEX_TO_LEGACY.put("#FFD700", "\u00A76"); // LEVEL_UP (Gold)
    }

    /**
     * Converts a Message object to a legacy formatted string.
     *
     * @param message The message to convert
     * @return Legacy formatted string (using § codes)
     */
    public static String toLegacyString(Message message) {
        StringBuilder sb = new StringBuilder();
        appendLegacy(message, sb);
        return sb.toString();
    }

    private static void appendLegacy(Message message, StringBuilder sb) {
        FormattedMessage fmt = message.getFormattedMessage();

        // Color
        String color = message.getColor();
        if (color != null) {
            String legacy = HEX_TO_LEGACY.get(color);
            if (legacy != null) {
                sb.append(legacy);
            }
        }

        // Styles
        if (fmt.bold == MaybeBool.True) sb.append("\u00A7l");
        if (fmt.italic == MaybeBool.True) sb.append("\u00A7o");
        if (fmt.underlined == MaybeBool.True) sb.append("\u00A7n");
        // Monospace usually doesn't map well to legacy, ignoring.

        // Text
        String text = message.getRawText();
        if (text != null) {
            sb.append(text);
        } else if (message.getMessageId() != null) {
            sb.append(message.getMessageId());
        }

        // Children
        for (Message child : message.getChildren()) {
            appendLegacy(child, sb);
        }
    }

    // =========================================================================
    // JSON SERIALIZATION (for ItemTranslationProperties)
    // =========================================================================

    /**
     * Converts a Message to its JSON representation.
     *
     * <p>The JSON format is compatible with Hytale's FormattedMessage protocol,
     * which can be used in ItemTranslationProperties for styled display.
     *
     * @param message The message to serialize (must not be null)
     * @return JSON string representation
     */
    @Nonnull
    public static String toJson(@Nonnull Message message) {
        Objects.requireNonNull(message, "message cannot be null");
        return buildJsonManually(message);
    }

    /**
     * Manually builds JSON for a Message (fallback).
     */
    @Nonnull
    private static String buildJsonManually(@Nonnull Message message) {
        StringBuilder json = new StringBuilder("{");
        boolean hasField = false;

        // Raw text
        String rawText = message.getRawText();
        if (rawText != null) {
            json.append("\"RawText\":").append(escapeJsonString(rawText));
            hasField = true;
        }

        // Message ID (i18n key)
        String messageId = message.getMessageId();
        if (messageId != null) {
            if (hasField) json.append(",");
            json.append("\"MessageId\":").append(escapeJsonString(messageId));
            hasField = true;
        }

        // Color
        String color = message.getColor();
        if (color != null) {
            if (hasField) json.append(",");
            json.append("\"Color\":").append(escapeJsonString(color));
            hasField = true;
        }

        // Bold, Italic from FormattedMessage
        FormattedMessage fm = message.getFormattedMessage();
        if (fm.bold == MaybeBool.True) {
            if (hasField) json.append(",");
            json.append("\"Bold\":true");
            hasField = true;
        }

        if (fm.italic == MaybeBool.True) {
            if (hasField) json.append(",");
            json.append("\"Italic\":true");
            hasField = true;
        }

        if (fm.underlined == MaybeBool.True) {
            if (hasField) json.append(",");
            json.append("\"Underline\":true");
            hasField = true;
        }

        // Children
        List<Message> children = message.getChildren();
        if (!children.isEmpty()) {
            if (hasField) json.append(",");
            json.append("\"Children\":[");
            for (int i = 0; i < children.size(); i++) {
                if (i > 0) json.append(",");
                json.append(buildJsonManually(children.get(i)));
            }
            json.append("]");
        }

        json.append("}");
        return json.toString();
    }

    /**
     * Escapes a string for JSON encoding.
     */
    @Nonnull
    private static String escapeJsonString(@Nonnull String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    // =========================================================================
    // PLAIN TEXT EXTRACTION
    // =========================================================================

    /**
     * Extracts plain text from a Message including all children.
     *
     * <p>Recursively traverses the Message tree and concatenates all text,
     * stripping all formatting.
     *
     * @param message The message to extract text from
     * @return Plain text content including children
     */
    @Nonnull
    public static String toPlainText(@Nonnull Message message) {
        Objects.requireNonNull(message, "message cannot be null");

        StringBuilder sb = new StringBuilder();
        appendPlainText(message, sb);
        return sb.toString();
    }

    /**
     * Recursively appends plain text to a StringBuilder.
     */
    private static void appendPlainText(@Nonnull Message message, @Nonnull StringBuilder sb) {
        String rawText = message.getRawText();
        if (rawText != null) {
            sb.append(rawText);
        } else {
            String messageId = message.getMessageId();
            if (messageId != null) {
                sb.append(messageId);
            }
        }

        for (Message child : message.getChildren()) {
            appendPlainText(child, sb);
        }
    }

    // =========================================================================
    // MULTILINE HELPERS
    // =========================================================================

    /**
     * Joins multiple Messages into a single JSON with line breaks.
     *
     * <p>Creates a parent Message containing all input messages as children,
     * separated by newlines.
     *
     * @param lines The messages to join
     * @return Combined JSON string
     */
    @Nonnull
    public static String joinLinesAsJson(@Nonnull List<Message> lines) {
        Objects.requireNonNull(lines, "lines cannot be null");

        if (lines.isEmpty()) {
            return "{}";
        }

        if (lines.size() == 1) {
            return toJson(lines.get(0));
        }

        Message combined = Message.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                combined = combined.insert(Message.raw("\n"));
            }
            combined = combined.insert(lines.get(i));
        }

        return toJson(combined);
    }

    /**
     * Joins multiple Messages into plain text with line breaks.
     *
     * @param lines The messages to join
     * @return Combined plain text string
     */
    @Nonnull
    public static String joinLinesAsPlainText(@Nonnull List<Message> lines) {
        Objects.requireNonNull(lines, "lines cannot be null");

        if (lines.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                sb.append("\n");
            }
            sb.append(toPlainText(lines.get(i)));
        }

        return sb.toString();
    }

    // =========================================================================
    // FORMATTED TEXT FOR TRANSLATIONS (Hytale Markup)
    // =========================================================================

    /**
     * Converts a Message to formatted text suitable for translation values.
     *
     * <p>Uses Hytale's native markup format found in .lang files:
     * <ul>
     *   <li>{@code <color is="#RRGGBB">text</color>} - for colors</li>
     *   <li>{@code <b>text</b>} - for bold</li>
     *   <li>{@code <i>text</i>} - for italic</li>
     * </ul>
     *
     * <p>Example output: {@code <color is="#55FF55"><b>+10 Strength</b></color>}
     *
     * @param message The message to convert (must not be null)
     * @return Hytale markup formatted text string
     */
    @Nonnull
    public static String toFormattedText(@Nonnull Message message) {
        Objects.requireNonNull(message, "message cannot be null");
        StringBuilder sb = new StringBuilder();
        appendHytaleMarkup(message, sb);
        return sb.toString();
    }

    /**
     * Recursively appends Hytale markup text to a StringBuilder.
     *
     * <p>Uses Hytale's native markup format:
     * <ul>
     *   <li>{@code <color is="#RRGGBB">} for colors</li>
     *   <li>{@code <b>} for bold</li>
     *   <li>{@code <i>} for italic</li>
     * </ul>
     */
    private static void appendHytaleMarkup(@Nonnull Message message, @Nonnull StringBuilder sb) {
        String color = message.getColor();
        FormattedMessage fm = message.getFormattedMessage();
        boolean hasColor = color != null;
        boolean hasBold = fm.bold == MaybeBool.True;
        boolean hasItalic = fm.italic == MaybeBool.True;

        // Opening tags (in order: color, bold, italic)
        // Hytale uses: <color is="#RRGGBB">
        if (hasColor) sb.append("<color is=\"").append(color).append("\">");
        if (hasBold) sb.append("<b>");
        if (hasItalic) sb.append("<i>");

        // Text content
        String text = message.getRawText();
        if (text != null) {
            sb.append(text);
        } else if (message.getMessageId() != null) {
            // For translation references, include the key
            sb.append(message.getMessageId());
        }

        // Process children BEFORE closing tags (to keep proper nesting)
        for (Message child : message.getChildren()) {
            appendHytaleMarkup(child, sb);
        }

        // Closing tags (reverse order)
        if (hasItalic) sb.append("</i>");
        if (hasBold) sb.append("</b>");
        if (hasColor) sb.append("</color>");
    }

    /**
     * Converts a Message to markup text with HTML-like tags (deprecated).
     *
     * <p>Use {@link #toFormattedText(Message)} instead, which uses Hytale's native format.
     *
     * @param message The message to convert (must not be null)
     * @return Markup text string
     * @deprecated Use {@link #toFormattedText(Message)} for Hytale-compatible markup
     */
    @Deprecated
    @Nonnull
    public static String toMarkupText(@Nonnull Message message) {
        Objects.requireNonNull(message, "message cannot be null");
        return toFormattedText(message);
    }

    // =========================================================================
    // CONVENIENCE METHODS
    // =========================================================================

    /**
     * Creates a colored text JSON string.
     *
     * @param text The text content
     * @param hexColor The color in hex format (e.g., "#FFD700")
     * @return JSON string for the colored text
     */
    @Nonnull
    public static String coloredTextJson(@Nonnull String text, @Nonnull String hexColor) {
        return toJson(Message.raw(text).color(hexColor));
    }

    /**
     * Creates a bold colored text JSON string.
     *
     * @param text The text content
     * @param hexColor The color in hex format
     * @return JSON string for bold colored text
     */
    @Nonnull
    public static String boldColoredTextJson(@Nonnull String text, @Nonnull String hexColor) {
        return toJson(Message.raw(text).color(hexColor).bold(true));
    }

    /**
     * Checks if a Message has any visual content.
     *
     * @param message The message to check (may be null)
     * @return true if the message has text or children
     */
    public static boolean hasContent(@Nullable Message message) {
        if (message == null) {
            return false;
        }

        if (message.getRawText() != null && !message.getRawText().isEmpty()) {
            return true;
        }

        if (message.getMessageId() != null && !message.getMessageId().isEmpty()) {
            return true;
        }

        return !message.getChildren().isEmpty();
    }

    // Private constructor to prevent instantiation
    private MessageSerializer() {
        throw new UnsupportedOperationException("Utility class");
    }
}
