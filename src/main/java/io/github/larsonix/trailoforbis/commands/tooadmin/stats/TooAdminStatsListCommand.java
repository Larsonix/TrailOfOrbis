package io.github.larsonix.trailoforbis.commands.tooadmin.stats;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.attributes.debug.DebugStatRegistry;
import io.github.larsonix.trailoforbis.attributes.debug.DebugStatRegistry.StatEntry;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lists available stat names, optionally filtered by category.
 *
 * <p>Usage: /tooadmin stats list [category]
 */
public final class TooAdminStatsListCommand extends AbstractPlayerCommand {
    private final OptionalArg<String> categoryArg;

    private static final Map<String, String> CATEGORY_COLORS = Map.of(
        "resource", MessageColors.SUCCESS,
        "offensive", MessageColors.ERROR,
        "defensive", MessageColors.INFO,
        "movement", MessageColors.ORANGE,
        "elemental", MessageColors.PURPLE,
        "utility", MessageColors.WARNING
    );

    public TooAdminStatsListCommand() {
        super("list", "List available stat names");
        categoryArg = this.withOptionalArg("category", "Filter by category (resource/offensive/defensive/movement/elemental/utility)", ArgTypes.STRING);
    }

    @Override
    protected void execute(
        @Nonnull CommandContext context,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef sender,
        @Nonnull World world
    ) {
        String categoryFilter = categoryArg.get(context);

        if (categoryFilter != null && !categoryFilter.isEmpty()) {
            // Show stats for a specific category
            List<StatEntry> entries = DebugStatRegistry.byCategory(categoryFilter.toLowerCase());
            if (entries.isEmpty()) {
                Set<String> categories = DebugStatRegistry.categories();
                sender.sendMessage(Message.raw("Unknown category : " + categoryFilter + ". Available : " +
                    String.join(", ", categories)).color(MessageColors.ERROR));
                return;
            }

            String color = CATEGORY_COLORS.getOrDefault(categoryFilter.toLowerCase(), MessageColors.WHITE);
            sender.sendMessage(Message.empty()
                .insert(Message.raw("=== ").color(MessageColors.GOLD))
                .insert(Message.raw(categoryFilter.toUpperCase()).color(color))
                .insert(Message.raw(" Stats (" + entries.size() + ") ===").color(MessageColors.GOLD)));

            StringBuilder line = new StringBuilder();
            for (int i = 0; i < entries.size(); i++) {
                if (!line.isEmpty()) line.append(", ");
                line.append(entries.get(i).name());

                // Send in batches to avoid chat overflow
                if ((i + 1) % 8 == 0 || i == entries.size() - 1) {
                    sender.sendMessage(Message.raw("  " + line).color(MessageColors.GRAY));
                    line.setLength(0);
                }
            }
        } else {
            // Show category summary
            sender.sendMessage(Message.empty()
                .insert(Message.raw("=== Debug Stat Registry (").color(MessageColors.GOLD))
                .insert(Message.raw(String.valueOf(DebugStatRegistry.size())).color(MessageColors.WHITE))
                .insert(Message.raw(" stats) ===").color(MessageColors.GOLD)));

            for (String category : DebugStatRegistry.categories()) {
                List<StatEntry> entries = DebugStatRegistry.byCategory(category);
                String color = CATEGORY_COLORS.getOrDefault(category, MessageColors.WHITE);
                sender.sendMessage(Message.empty()
                    .insert(Message.raw("  " + category.toUpperCase()).color(color))
                    .insert(Message.raw(" — " + entries.size() + " stats").color(MessageColors.GRAY)));
            }

            sender.sendMessage(Message.raw("Use /tooadmin stats list <category> to see stat names").color(MessageColors.GRAY));
        }
    }
}
