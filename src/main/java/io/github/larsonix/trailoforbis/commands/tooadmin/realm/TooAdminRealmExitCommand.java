package io.github.larsonix.trailoforbis.commands.tooadmin.realm;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.maps.RealmsManager;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;

/**
 * Exits the current realm.
 *
 * <p>Usage: /tooadmin realm exit
 */
public class TooAdminRealmExitCommand extends AbstractPlayerCommand {

    private final TrailOfOrbis plugin;

    public TooAdminRealmExitCommand(TrailOfOrbis plugin) {
        super("exit", "Exit current realm");
        this.plugin = plugin;
    }

    @Override
    protected void execute(
        @Nonnull CommandContext context,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef sender,
        @Nonnull World world
    ) {
        RealmsManager realmsManager = plugin.getRealmsManager();
        if (realmsManager == null) {
            sender.sendMessage(Message.raw("Realms system is not enabled !").color(MessageColors.ERROR));
            return;
        }

        if (!realmsManager.isPlayerInRealm(sender.getUuid())) {
            sender.sendMessage(Message.raw("You are not in a realm.").color(MessageColors.GRAY));
            return;
        }

        sender.sendMessage(Message.raw("Exiting realm...").color(MessageColors.GRAY));

        realmsManager.exitRealm(sender.getUuid())
            .thenAccept(success -> {
                if (success) {
                    sender.sendMessage(Message.empty()
                        .insert(Message.raw("[TooAdmin] ").color(MessageColors.GOLD))
                        .insert(Message.raw("Exited realm !").color(MessageColors.SUCCESS)));
                } else {
                    sender.sendMessage(Message.empty()
                        .insert(Message.raw("[TooAdmin] ").color(MessageColors.GOLD))
                        .insert(Message.raw("Failed to exit realm.").color(MessageColors.ERROR)));
                }
            });
    }
}
