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
 * Toggles detailed combat damage breakdown display in chat.
 *
 * <p>Usage:
 * <ul>
 *   <li>/too combat detail on - Enable detailed breakdown</li>
 *   <li>/too combat detail off - Disable detailed breakdown</li>
 *   <li>/too combat detail - Toggle current state</li>
 * </ul>
 *
 * <p>When enabled, after each attack you deal, a detailed breakdown
 * will be shown in chat including base damage, modifiers, armor reduction,
 * elemental damage, and final total.
 */
public final class TooCombatDetailCommand extends OpenPlayerCommand {

    private final TrailOfOrbis plugin;
    private final OptionalArg<String> stateArg;

    public TooCombatDetailCommand(TrailOfOrbis plugin) {
        super("detail", "Toggle detailed damage breakdown in chat");
        this.addAliases("breakdown", "verbose");
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
                player.sendMessage(Message.raw("Usage : /too combat detail [on|off]").color(MessageColors.WARNING));
                return;
            }
        } else {
            // Toggle current state
            newState = !plugin.isCombatDetailEnabled(player.getUuid());
        }

        plugin.setCombatDetailEnabled(player.getUuid(), newState);

        if (newState) {
            player.sendMessage(Message.raw("Combat detail mode ")
                .color(MessageColors.GRAY)
                .insert(Message.raw("enabled").color(MessageColors.SUCCESS))
                .insert(Message.raw(". You will see damage breakdowns after each attack.").color(MessageColors.GRAY)));
        } else {
            player.sendMessage(Message.raw("Combat detail mode ")
                .color(MessageColors.GRAY)
                .insert(Message.raw("disabled").color(MessageColors.WARNING))
                .insert(Message.raw(".").color(MessageColors.GRAY)));
        }
    }
}
