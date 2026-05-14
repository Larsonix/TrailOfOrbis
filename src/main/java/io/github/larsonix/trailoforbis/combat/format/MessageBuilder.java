package io.github.larsonix.trailoforbis.combat.format;

import com.hypixel.hytale.server.core.Message;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;

/**
 * Fluent builder for multi-line, multi-colored chat messages.
 *
 * <p>Wraps the verbose {@code m = m.insert(Message.raw(...).color(...))} pattern
 * used throughout combat feedback formatting. All methods return {@code this}
 * for chaining.
 */
public final class MessageBuilder {

    private Message message = Message.empty();

    /** Appends text followed by a newline, in the given color. */
    @Nonnull
    public MessageBuilder line(@Nonnull String text, @Nonnull String color) {
        message = message.insert(Message.raw(text + "\n").color(color));
        return this;
    }

    /** Appends text without a newline, in the given color. */
    @Nonnull
    public MessageBuilder text(@Nonnull String text, @Nonnull String color) {
        message = message.insert(Message.raw(text).color(color));
        return this;
    }

    /** Appends a frame header: blank line + ======== TITLE ========. */
    @Nonnull
    public MessageBuilder header(@Nonnull String title, @Nonnull String color) {
        message = message.insert(Message.raw("\n").color(MessageColors.GRAY));
        message = message.insert(Message.raw("======== " + title + " ========\n").color(color));
        return this;
    }

    /** Appends a frame footer: =============================. */
    @Nonnull
    public MessageBuilder footer(@Nonnull String color) {
        message = message.insert(Message.raw("=============================\n").color(color));
        return this;
    }

    /** Appends a gold section header: blank line + -- Title --. */
    @Nonnull
    public MessageBuilder section(@Nonnull String title) {
        message = message.insert(Message.raw("\n-- " + title + " --\n").color(MessageColors.GOLD));
        return this;
    }

    /** Appends a gray separator line: --------. */
    @Nonnull
    public MessageBuilder separator() {
        message = message.insert(Message.raw("--------\n").color(MessageColors.GRAY));
        return this;
    }

    /** Appends an already-built Message child. */
    @Nonnull
    public MessageBuilder append(@Nonnull Message child) {
        message = message.insert(child);
        return this;
    }

    /** Appends a newline in gray (visual spacer). */
    @Nonnull
    public MessageBuilder blank() {
        message = message.insert(Message.raw("\n").color(MessageColors.GRAY));
        return this;
    }

    /** Returns the assembled Message. */
    @Nonnull
    public Message build() {
        return message;
    }
}
