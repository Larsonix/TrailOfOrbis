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
 * Clears all debug stat overrides for a player.
 *
 * <p>Usage: /tooadmin stats clear &lt;player&gt;
 */
public final class TooAdminStatsClearCommand extends AbstractPlayerCommand {
    private final TrailOfOrbis plugin;
    private final RequiredArg<String> targetArg;

    public TooAdminStatsClearCommand(TrailOfOrbis plugin) {
        super("clear", "Clear all stat overrides for a player");
        this.plugin = plugin;
        targetArg = this.withRequiredArg("player", "Target player name", ArgTypes.STRING);
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

        Optional<UUID> targetUuid = StatsCommandHelper.resolvePlayerUuid(targetName, plugin);
        if (targetUuid.isEmpty()) {
            sender.sendMessage(Message.raw("Player not found : " + targetName).color(MessageColors.ERROR));
            return;
        }

        DebugStatOverrideProvider provider = plugin.getDebugStatOverrideProvider();
        int cleared = provider.clearOverrides(targetUuid.get());

        StatsCommandHelper.recalculateAndNotify(targetUuid.get(), plugin);

        sender.sendMessage(Message.empty()
            .insert(Message.raw("[Stats] ").color(MessageColors.GOLD))
            .insert(Message.raw("Cleared ").color(MessageColors.SUCCESS))
            .insert(Message.raw(String.valueOf(cleared)).color(MessageColors.WHITE))
            .insert(Message.raw(" override(s) for ").color(MessageColors.GRAY))
            .insert(Message.raw(targetName).color(MessageColors.WHITE)));

        AdminCommandHelper.logAdminAction(plugin, sender, "STATS CLEAR", targetName, cleared + " overrides cleared");
    }
}
