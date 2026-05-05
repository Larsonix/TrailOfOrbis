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
import io.github.larsonix.trailoforbis.attributes.debug.DebugStatRegistry;
import io.github.larsonix.trailoforbis.commands.admin.AdminCommandHelper;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.UUID;

/**
 * Adds a debug stat bonus on top of the pipeline-computed value.
 *
 * <p>Usage: /tooadmin stats add &lt;player&gt; &lt;stat&gt; &lt;value&gt;
 */
public final class TooAdminStatsAddCommand extends AbstractPlayerCommand {
    private final TrailOfOrbis plugin;
    private final RequiredArg<String> targetArg;
    private final RequiredArg<String> statArg;
    private final RequiredArg<String> valueArg;

    public TooAdminStatsAddCommand(TrailOfOrbis plugin) {
        super("add", "Add a bonus to a stat");
        this.plugin = plugin;
        targetArg = this.withRequiredArg("player", "Target player name", ArgTypes.STRING);
        statArg = this.withRequiredArg("stat", "Stat name (e.g. armor, evasion)", ArgTypes.STRING);
        valueArg = this.withRequiredArg("value", "Bonus value (can be negative)", ArgTypes.STRING);
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
        String valueStr = valueArg.get(context);

        float value;
        try {
            value = Float.parseFloat(valueStr);
        } catch (NumberFormatException e) {
            sender.sendMessage(Message.raw("Invalid number : " + valueStr).color(MessageColors.ERROR));
            return;
        }
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            sender.sendMessage(Message.raw("Value cannot be NaN or Infinity !").color(MessageColors.ERROR));
            return;
        }

        if (!DebugStatRegistry.exists(statName)) {
            sender.sendMessage(Message.raw("Unknown stat : " + statName + ". Use /tooadmin stats list").color(MessageColors.ERROR));
            return;
        }

        Optional<UUID> targetUuid = StatsCommandHelper.resolvePlayerUuid(targetName, plugin);
        if (targetUuid.isEmpty()) {
            sender.sendMessage(Message.raw("Player not found : " + targetName).color(MessageColors.ERROR));
            return;
        }

        DebugStatOverrideProvider provider = plugin.getDebugStatOverrideProvider();
        provider.setOverride(targetUuid.get(), statName, value, DebugStatOverrideProvider.Mode.ADD);

        StatsCommandHelper.recalculateAndNotify(targetUuid.get(), plugin);

        String sign = value >= 0 ? "+" : "";
        sender.sendMessage(Message.empty()
            .insert(Message.raw("[Stats] ").color(MessageColors.GOLD))
            .insert(Message.raw("ADD ").color(MessageColors.SUCCESS))
            .insert(Message.raw(targetName).color(MessageColors.WHITE))
            .insert(Message.raw("'s ").color(MessageColors.GRAY))
            .insert(Message.raw(statName).color(MessageColors.INFO))
            .insert(Message.raw(" " + sign).color(MessageColors.GRAY))
            .insert(Message.raw(String.format("%.2f", value)).color(MessageColors.SUCCESS)));

        AdminCommandHelper.logAdminAction(plugin, sender, "STATS ADD", targetName, statName + " " + sign + value);
    }
}
