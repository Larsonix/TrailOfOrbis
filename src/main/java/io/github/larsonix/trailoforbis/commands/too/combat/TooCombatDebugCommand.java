package io.github.larsonix.trailoforbis.commands.too.combat;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import io.github.larsonix.trailoforbis.commands.base.OpenPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;

/**
 * Toggles full combat debug dump in chat on every hit.
 *
 * <p>When enabled, each attack displays a comprehensive context dump including
 * player attributes, skill tree nodes, equipped gear, computed stats, and the
 * full damage breakdown. Designed for diagnosing combat pipeline issues.
 *
 * <p>Usage: {@code /too combat debug [on|off]}
 */
public final class TooCombatDebugCommand extends OpenPlayerCommand {

    private final TrailOfOrbis plugin;
    private final OptionalArg<String> stateArg;

    public TooCombatDebugCommand(TrailOfOrbis plugin) {
        super("debug", "Toggle full combat context dump on hit");
        this.plugin = plugin;
        stateArg = this.withOptionalArg("state", "on/off (or omit to toggle)", ArgTypes.STRING);
    }

    @Override
    protected void execute(
        @Nonnull CommandContext context,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef player,
        @Nonnull World world
    ) {
        String state = stateArg.get(context);

        boolean newState;
        if (state != null) {
            String arg = state.toLowerCase();
            if (arg.equals("on") || arg.equals("true") || arg.equals("enable") || arg.equals("1")) {
                newState = true;
            } else if (arg.equals("off") || arg.equals("false") || arg.equals("disable") || arg.equals("0")) {
                newState = false;
            } else {
                player.sendMessage(Message.raw("Usage: /too combat debug [on|off]").color(MessageColors.WARNING));
                return;
            }
        } else {
            newState = !plugin.isCombatDebugEnabled(player.getUuid());
        }

        plugin.setCombatDebugEnabled(player.getUuid(), newState);

        if (newState) {
            player.sendMessage(Message.raw("Combat debug mode ")
                .color(MessageColors.GRAY)
                .insert(Message.raw("enabled").color(MessageColors.SUCCESS))
                .insert(Message.raw(". Full context dump on every hit.").color(MessageColors.GRAY)));
        } else {
            player.sendMessage(Message.raw("Combat debug mode ")
                .color(MessageColors.GRAY)
                .insert(Message.raw("disabled").color(MessageColors.WARNING))
                .insert(Message.raw(".").color(MessageColors.GRAY)));
        }
    }
}
