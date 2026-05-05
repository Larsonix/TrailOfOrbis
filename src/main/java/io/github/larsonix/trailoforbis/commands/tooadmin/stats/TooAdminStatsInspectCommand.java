package io.github.larsonix.trailoforbis.commands.tooadmin.stats;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.api.services.AttributeService;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.attributes.debug.DebugStatOverrideProvider;
import io.github.larsonix.trailoforbis.attributes.debug.DebugStatRegistry;
import io.github.larsonix.trailoforbis.attributes.debug.DebugStatRegistry.StatEntry;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Full computed stats dump for a player, organized by category.
 * Shows override indicators for stats that have debug overrides active.
 *
 * <p>Usage: /tooadmin stats inspect &lt;player&gt; [category]
 */
public final class TooAdminStatsInspectCommand extends AbstractPlayerCommand {
    private final TrailOfOrbis plugin;
    private final RequiredArg<String> targetArg;
    private final OptionalArg<String> categoryArg;

    private static final Map<String, String> CATEGORY_COLORS = Map.of(
        "resource", MessageColors.SUCCESS,
        "offensive", MessageColors.ERROR,
        "defensive", MessageColors.INFO,
        "movement", MessageColors.ORANGE,
        "elemental", MessageColors.PURPLE,
        "utility", MessageColors.WARNING
    );

    public TooAdminStatsInspectCommand(TrailOfOrbis plugin) {
        super("inspect", "Full computed stats dump");
        this.plugin = plugin;
        targetArg = this.withRequiredArg("player", "Target player name", ArgTypes.STRING);
        categoryArg = this.withOptionalArg("category", "Filter by category", ArgTypes.STRING);
    }

    @Override
    protected void execute(
        @Nonnull CommandContext context,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef sender,
        @Nonnull World world
    ) {
        String targetName = targetArg.get(context);
        String categoryFilter = categoryArg.get(context);

        Optional<UUID> targetUuid = StatsCommandHelper.resolvePlayerUuid(targetName, plugin);
        if (targetUuid.isEmpty()) {
            sender.sendMessage(Message.raw("Player not found : " + targetName).color(MessageColors.ERROR));
            return;
        }

        AttributeService attrService = ServiceRegistry.get(AttributeService.class).orElse(null);
        if (attrService == null) {
            sender.sendMessage(Message.raw("AttributeService not available !").color(MessageColors.ERROR));
            return;
        }

        ComputedStats stats = attrService.getStats(targetUuid.get());
        if (stats == null) {
            sender.sendMessage(Message.raw("No computed stats for " + targetName).color(MessageColors.ERROR));
            return;
        }

        // Get active overrides for highlighting (grouped by stat name)
        DebugStatOverrideProvider provider = plugin.getDebugStatOverrideProvider();
        Map<String, List<DebugStatOverrideProvider.Override>> overrides = provider.getOverridesByStatName(targetUuid.get());

        // Header
        sender.sendMessage(Message.empty()
            .insert(Message.raw("=== ").color(MessageColors.GOLD))
            .insert(Message.raw(targetName).color(MessageColors.WHITE))
            .insert(Message.raw(" Computed Stats ===").color(MessageColors.GOLD)));

        if (!overrides.isEmpty()) {
            sender.sendMessage(Message.raw("  [!] = debug override active").color(MessageColors.WARNING));
        }

        // Show stats by category
        for (String category : DebugStatRegistry.categories()) {
            if (categoryFilter != null && !categoryFilter.isEmpty()
                && !category.equalsIgnoreCase(categoryFilter)) {
                continue;
            }

            List<StatEntry> entries = DebugStatRegistry.byCategory(category);
            String catColor = CATEGORY_COLORS.getOrDefault(category, MessageColors.WHITE);

            // Only show categories that have non-zero values (or overrides)
            boolean hasNonZero = entries.stream().anyMatch(e -> {
                float val = e.getter().apply(stats);
                return val != 0f || overrides.containsKey(e.name());
            });

            if (!hasNonZero && categoryFilter == null) continue;

            sender.sendMessage(Message.empty()
                .insert(Message.raw("--- ").color(MessageColors.GRAY))
                .insert(Message.raw(category.toUpperCase()).color(catColor))
                .insert(Message.raw(" ---").color(MessageColors.GRAY)));

            for (StatEntry entry : entries) {
                float value = entry.getter().apply(stats);
                boolean hasOverride = overrides.containsKey(entry.name());

                // Skip zero values unless showing specific category or has override
                if (value == 0f && !hasOverride && categoryFilter == null) continue;

                String valueColor = hasOverride ? MessageColors.WARNING : MessageColors.WHITE;

                // Format value: show as integer if it's a whole number, otherwise 2 decimal places
                String formattedValue = (value == Math.floor(value) && !Float.isInfinite(value))
                    ? String.format("%.0f", value)
                    : String.format("%.2f", value);

                Message msg = Message.empty()
                    .insert(Message.raw("  " + entry.name() + " : ").color(MessageColors.GRAY))
                    .insert(Message.raw(formattedValue).color(valueColor));

                if (hasOverride) {
                    // Show all overrides (SET, ADD, or both) for this stat
                    StringBuilder ovrInfo = new StringBuilder(" [!");
                    for (DebugStatOverrideProvider.Override ovr : overrides.get(entry.name())) {
                        ovrInfo.append(" ").append(ovr.mode()).append("=").append(String.format("%.2f", ovr.value()));
                    }
                    ovrInfo.append("]");
                    msg.insert(Message.raw(ovrInfo.toString()).color(MessageColors.WARNING));
                }

                sender.sendMessage(msg);
            }
        }

        sender.sendMessage(Message.raw("=== End Stats ===").color(MessageColors.GOLD));
    }
}
