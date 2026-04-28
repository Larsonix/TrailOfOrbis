package io.github.larsonix.trailoforbis.guide;

import com.hypixel.hytale.server.core.Message;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses guide milestone text with inline formatting markers into
 * Hytale Message TextSpans for rich rendering in HyUI labels.
 *
 * <p>Supported markers:
 * <ul>
 *   <li>{@code **text**} — bold, white (#FFFFFF)</li>
 *   <li>{@code *text*} — italic, muted (#888899)</li>
 *   <li>{@code ||text||} — bold, gold (#FFD700) — for commands and key terms</li>
 * </ul>
 *
 * <p>Markers cannot be nested. Process order: {@code ||} first, then {@code **}, then {@code *}.
 */
public final class GuideTextParser {

    private static final String COLOR_WHITE = "#FFFFFF";
    private static final String COLOR_GOLD = "#FFD700";
    private static final String COLOR_MUTED = "#888899";

    private GuideTextParser() {}

    /**
     * Parses a markup string into a list of Message segments.
     *
     * @param input The markup text
     * @param defaultColor The color for unmarked text (e.g., body vs hook)
     * @return Ordered list of Message segments for use with LabelBuilder.withTextSpans()
     */
    @Nonnull
    public static List<Message> parse(@Nonnull String input, @Nonnull String defaultColor) {
        List<Segment> segments = tokenize(input);
        List<Message> messages = new ArrayList<>();

        for (Segment seg : segments) {
            Message msg = Message.raw(seg.text);
            switch (seg.style) {
                case BOLD -> msg = msg.color(COLOR_WHITE).bold(true);
                case ITALIC -> msg = msg.color(COLOR_MUTED).italic(true);
                case GOLD -> msg = msg.color(COLOR_GOLD).bold(true);
                case PLAIN -> msg = msg.color(defaultColor);
            }
            messages.add(msg);
        }

        return messages;
    }

    /**
     * Tokenizes a markup string into styled segments.
     * Process order: || (gold) first, then ** (bold), then * (italic).
     * This prevents ** from consuming the * in *italic*.
     */
    private static List<Segment> tokenize(@Nonnull String input) {
        // First pass: extract || gold || markers
        List<Segment> pass1 = extractMarker(input, "||", Style.GOLD);

        // Second pass: extract ** bold ** from plain segments
        List<Segment> pass2 = new ArrayList<>();
        for (Segment seg : pass1) {
            if (seg.style == Style.PLAIN) {
                pass2.addAll(extractMarker(seg.text, "**", Style.BOLD));
            } else {
                pass2.add(seg);
            }
        }

        // Third pass: extract * italic * from remaining plain segments
        List<Segment> pass3 = new ArrayList<>();
        for (Segment seg : pass2) {
            if (seg.style == Style.PLAIN) {
                pass3.addAll(extractMarker(seg.text, "*", Style.ITALIC));
            } else {
                pass3.add(seg);
            }
        }

        return pass3;
    }

    /**
     * Extracts all occurrences of a marker pair from text,
     * returning a list of plain and styled segments in order.
     */
    private static List<Segment> extractMarker(@Nonnull String text, @Nonnull String marker, @Nonnull Style style) {
        List<Segment> result = new ArrayList<>();
        int pos = 0;
        int markerLen = marker.length();

        while (pos < text.length()) {
            int start = text.indexOf(marker, pos);
            if (start == -1) {
                // No more markers — rest is plain
                if (pos < text.length()) {
                    result.add(new Segment(text.substring(pos), Style.PLAIN));
                }
                break;
            }

            int end = text.indexOf(marker, start + markerLen);
            if (end == -1) {
                // Unclosed marker — treat as plain text
                result.add(new Segment(text.substring(pos), Style.PLAIN));
                break;
            }

            // Text before marker
            if (start > pos) {
                result.add(new Segment(text.substring(pos, start), Style.PLAIN));
            }

            // Marked text (without the markers)
            String markedText = text.substring(start + markerLen, end);
            if (!markedText.isEmpty()) {
                result.add(new Segment(markedText, style));
            }

            pos = end + markerLen;
        }

        // Edge case: empty input
        if (result.isEmpty() && !text.isEmpty()) {
            result.add(new Segment(text, Style.PLAIN));
        }

        return result;
    }

    private enum Style {
        PLAIN, BOLD, ITALIC, GOLD
    }

    private record Segment(String text, Style style) {}
}
