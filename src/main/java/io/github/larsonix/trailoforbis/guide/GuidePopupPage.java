package io.github.larsonix.trailoforbis.guide;

import au.ellie.hyui.builders.LabelBuilder;
import au.ellie.hyui.builders.PageBuilder;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.ui.RPGStyles;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * HyUI page for guide milestone popups with rich inline formatting.
 *
 * <p>Uses Mockup C design: hook paragraph in bold white, body with inline
 * bold/italic/gold via TextSpans, closing quip in muted italic, thin gold
 * divider between hook and body.
 *
 * <p>Formatting is driven by markup in milestone content:
 * <ul>
 *   <li>{@code **text**} — bold, white</li>
 *   <li>{@code *text*} — italic, muted</li>
 *   <li>{@code ||text||} — bold, gold (commands, key terms, numbers)</li>
 * </ul>
 */
public class GuidePopupPage {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Layout
    private static final int CONTAINER_WIDTH = 560;
    private static final int CONTENT_PADDING = 24;
    private static final int CHARS_PER_LINE = 52;
    private static final int CONTAINER_CHROME = 65;

    // Text sizing
    private static final int HOOK_FONT_SIZE = 15;
    private static final int BODY_FONT_SIZE = 13;
    private static final int CLOSER_FONT_SIZE = 12;
    private static final int HOOK_LINE_HEIGHT = 22;
    private static final int BODY_LINE_HEIGHT = 18;
    private static final int CLOSER_LINE_HEIGHT = 17;

    // Paragraph spacing
    private static final int DIVIDER_HEIGHT = 16;
    private static final int PARA_SPACING = 14;

    // Colors for paragraph types
    private static final String COLOR_HOOK = "#FFFFFF";
    private static final String COLOR_BODY = "#D0DCEA";
    private static final String COLOR_CLOSER = "#888899";
    private static final String COLOR_DIVIDER = "#FFD70050";

    private final PlayerRef player;
    private final GuideMilestone milestone;
    private final Runnable onLearnMore;
    private final Runnable onDismiss;

    // Parsed paragraphs for height calculation and rendering
    private final String[] paragraphs;

    public GuidePopupPage(
            @Nonnull PlayerRef player,
            @Nonnull GuideMilestone milestone,
            @Nonnull Runnable onLearnMore,
            @Nonnull Runnable onDismiss) {
        this.player = player;
        this.milestone = milestone;
        this.onLearnMore = onLearnMore;
        this.onDismiss = onDismiss;
        this.paragraphs = parseParagraphs(milestone.getContent());
    }

    /**
     * Opens the guide popup page for the player.
     */
    public void open(@Nonnull Store<EntityStore> store) {
        try {
            String html = buildHtml();

            PageBuilder builder = PageBuilder.pageForPlayer(player)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(html);

            // Apply rich TextSpans to each paragraph label
            applyTextSpans(builder);

            // "Learn More" button
            if (milestone.hasWikiLink()) {
                builder.addEventListener("learn-more-btn", CustomUIEventBindingType.Activating,
                    (data, ctx) -> {
                        ctx.getPage().ifPresent(page -> page.close());
                        onLearnMore.run();
                    });
            }

            // "Got it" button
            builder.addEventListener("got-it-btn", CustomUIEventBindingType.Activating,
                (data, ctx) -> {
                    ctx.getPage().ifPresent(page -> page.close());
                    onDismiss.run();
                });

            builder.open(store);

            LOGGER.atFine().log("Opened guide popup '%s' for %s",
                milestone.getId(), player.getUuid().toString().substring(0, 8));

        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to open guide popup '%s'", milestone.getId());
            onDismiss.run();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // HTML STRUCTURE
    // ═══════════════════════════════════════════════════════════════════

    private String buildHtml() {
        int contentHeight = calculateContentHeight();
        StringBuilder sb = new StringBuilder();

        // Page overlay with centered layout
        sb.append("<div class=\"page-overlay\">\n");
        sb.append("  <div style=\"layout-mode: Middle; anchor-horizontal: 0; anchor-vertical: 0;\">\n");
        sb.append("    <div style=\"layout-mode: Top;\">\n");

        // Decorated container
        sb.append("      <div class=\"decorated-container\" data-hyui-title=\"")
          .append(escapeHtml(milestone.getTitle()))
          .append("\" style=\"anchor-width: ").append(CONTAINER_WIDTH)
          .append("; anchor-height: ").append(contentHeight).append(";\">\n");
        sb.append("        <div class=\"container-contents\" style=\"layout-mode: Top;\">\n");

        // Build content structure with placeholder labels
        sb.append(buildContentStructure());

        sb.append("        </div>\n");
        sb.append("      </div>\n");

        // Buttons below container
        sb.append("      <div style=\"layout-mode: Center; anchor-height: 70; anchor-top: 10; anchor-horizontal: 0;\">\n");
        sb.append("        <div style=\"layout-mode: Left;\">\n");

        if (milestone.hasWikiLink()) {
            sb.append("          <button id=\"learn-more-btn\" class=\"secondary-button\" ")
              .append("style=\"anchor-width: 210; anchor-height: 53;\">Learn More</button>\n");
            sb.append("          <div style=\"anchor-width: 20;\"></div>\n");
        }

        sb.append("          <button id=\"got-it-btn\" class=\"secondary-button\" ")
          .append("style=\"anchor-width: 210; anchor-height: 53;\">Got it</button>\n");

        sb.append("        </div>\n");
        sb.append("      </div>\n");

        sb.append("    </div>\n");
        sb.append("  </div>\n");
        sb.append("</div>\n");

        return sb.toString();
    }

    /**
     * Builds the content structure with paragraph placeholder labels.
     * Text content is set later via TextSpans for rich formatting.
     */
    private String buildContentStructure() {
        StringBuilder sb = new StringBuilder();

        // Top spacing
        sb.append("          <div style=\"anchor-height: 6;\"></div>\n");

        for (int i = 0; i < paragraphs.length; i++) {
            ParaType type = classifyParagraph(i);

            int fontSize = switch (type) {
                case HOOK -> HOOK_FONT_SIZE;
                case CLOSER -> CLOSER_FONT_SIZE;
                default -> BODY_FONT_SIZE;
            };

            // Paragraph wrapper with padding
            sb.append("          <div style=\"anchor-horizontal: 0;\" ")
              .append("data-hyui-style=\"Padding: (Horizontal: ").append(CONTENT_PADDING).append(")\">\n");

            // Label with ID for TextSpans — placeholder text replaced by applyTextSpans()
            sb.append("            <p id=\"para-").append(i)
              .append("\" style=\"font-size: ").append(fontSize)
              .append("; color: ").append(COLOR_BODY)
              .append("; white-space: wrap;\">.</p>\n");

            sb.append("          </div>\n");

            // Spacing after paragraph
            if (i < paragraphs.length - 1) {
                if (type == ParaType.HOOK) {
                    // Gold divider after hook
                    sb.append("          <div style=\"anchor-height: 8;\"></div>\n");
                    sb.append("          <div style=\"anchor-height: 1; anchor-horizontal: 0; background-color: ")
                      .append(COLOR_DIVIDER).append(";\" data-hyui-style=\"Padding: (Horizontal: ")
                      .append(CONTENT_PADDING).append(")\"></div>\n");
                    sb.append("          <div style=\"anchor-height: 8;\"></div>\n");
                } else {
                    sb.append("          <div style=\"anchor-height: ").append(PARA_SPACING).append(";\"></div>\n");
                }
            }
        }

        // Bottom spacing
        sb.append("          <div style=\"anchor-height: 6;\"></div>\n");

        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════════
    // RICH TEXT FORMATTING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Applies rich TextSpans to each paragraph label using the markup parser.
     * Called after fromHtml() but before open().
     */
    private void applyTextSpans(@Nonnull PageBuilder builder) {
        for (int i = 0; i < paragraphs.length; i++) {
            String paragraph = paragraphs[i];
            ParaType type = classifyParagraph(i);

            // Choose default color based on paragraph type
            String defaultColor = switch (type) {
                case HOOK -> COLOR_HOOK;
                case CLOSER -> COLOR_CLOSER;
                default -> COLOR_BODY;
            };

            // Parse markup into Message segments
            List<Message> spans = GuideTextParser.parse(paragraph, defaultColor);

            final int idx = i;
            builder.getById("para-" + idx, LabelBuilder.class).ifPresent(label -> {
                label.withTextSpans(spans);
            });
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // PARAGRAPH CLASSIFICATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Classifies a paragraph by position for visual hierarchy:
     * - HOOK: first paragraph (bold white, larger font)
     * - CLOSER: last paragraph if short (muted italic, smaller font)
     * - BODY: everything else (standard text)
     */
    private ParaType classifyParagraph(int index) {
        if (index == 0) return ParaType.HOOK;
        if (index == paragraphs.length - 1 && isShortParagraph(paragraphs[index])) {
            return ParaType.CLOSER;
        }
        return ParaType.BODY;
    }

    /**
     * A "short" paragraph is a closing quip — under ~80 chars of plain text.
     */
    private boolean isShortParagraph(String text) {
        // Strip markup markers to get actual text length
        String plain = text.replaceAll("\\*\\*|\\|\\||\\*", "");
        return plain.length() < 85;
    }

    private enum ParaType {
        HOOK, BODY, CLOSER
    }

    // ═══════════════════════════════════════════════════════════════════
    // HEIGHT CALCULATION
    // ═══════════════════════════════════════════════════════════════════

    private int calculateContentHeight() {
        int height = CONTAINER_CHROME + 12; // Chrome + top/bottom spacing

        for (int i = 0; i < paragraphs.length; i++) {
            ParaType type = classifyParagraph(i);

            // Strip markup for accurate char count
            String plain = paragraphs[i].replaceAll("\\*\\*|\\|\\||\\*", "");
            int lines = Math.max(1, (plain.length() + CHARS_PER_LINE - 1) / CHARS_PER_LINE);

            int lineHeight = switch (type) {
                case HOOK -> HOOK_LINE_HEIGHT;
                case CLOSER -> CLOSER_LINE_HEIGHT;
                default -> BODY_LINE_HEIGHT;
            };

            height += lines * lineHeight;

            // Spacing after paragraph
            if (i < paragraphs.length - 1) {
                if (type == ParaType.HOOK) {
                    height += DIVIDER_HEIGHT + 1; // divider line + spacing
                } else {
                    height += PARA_SPACING;
                }
            }
        }

        return height;
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Splits content on double newlines, trims empty paragraphs.
     */
    private static String[] parseParagraphs(String content) {
        String[] raw = content.split("\n\n");
        java.util.List<String> result = new java.util.ArrayList<>();
        for (String p : raw) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result.toArray(new String[0]);
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }
}
