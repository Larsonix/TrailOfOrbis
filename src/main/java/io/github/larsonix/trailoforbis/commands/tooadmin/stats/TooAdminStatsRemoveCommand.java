package io.github.larsonix.trailoforbis.commands.tooadmin.stats;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.attributes.debug.DebugStatOverrideProvider;
import io.github.larsonix.trailoforbis.commands.admin.AdminCommandHelper;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.UUID;

/**
 * Removes a specific stat override.
 *
 * <p>Usage: /tooadmin stats remove &lt;player&gt; &lt;stat&gt;
 */
public final class TooAdminStatsRemoveCommand extends AbstractPlayerCommand {
    private final TrailOfOrbis plugin;
    private final RequiredArg<String> targetArg;
    private final RequiredArg<String> statArg;

    public TooAdminStatsRemoveCommand(TrailOfOrbis plugin) {
        super("remove", "Remove a stat override");
        this.plugin = plugin;
        targetArg = this.withRequiredArg("player", "Target player name", ArgTypes.STRING);
        statArg = this.withRequiredArg("stat", "Stat name to remove override for", ArgTypes.STRING);
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
        String statName = statArg.get(context);

        Optional<UUID> targetUuid = StatsCommandHelper.resolvePlayerUuid(targetName, plugin);
        if (targetUuid.isEmpty()) {
            sender.sendMessage(Message.raw("Player not found : " + targetName).color(MessageColors.ERROR));
            return;
        }

        DebugStatOverrideProvider provider = plugin.getDebugStatOverrideProvider();
        int removedCount = provider.removeOverride(targetUuid.get(), statName);

        if (removedCount > 0) {
            StatsCommandHelper.recalculateAndNotify(targetUuid.get(), plugin);

            sender.sendMessage(Message.empty()
                .insert(Message.raw("[Stats] ").color(MessageColors.GOLD))
                .insert(Message.raw("Removed " + removedCount + " override(s) for ").color(MessageColors.SUCCESS))
                .insert(Message.raw(statName).color(MessageColors.INFO))
                .insert(Message.raw(" on ").color(MessageColors.GRAY))
                .insert(Message.raw(targetName).color(MessageColors.WHITE)));

            AdminCommandHelper.logAdminAction(plugin, sender, "STATS REMOVE", targetName, statName);
        } else {
            sender.sendMessage(Message.empty()
                .insert(Message.raw("[Stats] ").color(MessageColors.GOLD))
                .insert(Message.raw("No override found for ").color(MessageColors.WARNING))
                .insert(Message.raw(statName).color(MessageColors.INFO))
                .insert(Message.raw(" on ").color(MessageColors.GRAY))
                .insert(Message.raw(targetName).color(MessageColors.WHITE)));
        }
    }
}
