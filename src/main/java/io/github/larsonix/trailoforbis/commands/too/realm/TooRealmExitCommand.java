package io.github.larsonix.trailoforbis.commands.too.realm;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import io.github.larsonix.trailoforbis.commands.base.OpenPlayerCommand;
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
 * <p>Usage: /too realm exit
 */
public final class TooRealmExitCommand extends OpenPlayerCommand {

    private final TrailOfOrbis plugin;

    public TooRealmExitCommand(TrailOfOrbis plugin) {
        super("exit", "Exit the current realm");
        this.addAliases("leave");
        this.plugin = plugin;
    }

    @Override
    protected void execute(
        @Nonnull CommandContext context,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef player,
        @Nonnull World world
    ) {
        RealmsManager realmsManager = plugin.getRealmsManager();
        if (realmsManager == null) {
            player.sendMessage(Message.raw("Realms system is not enabled !").color(MessageColors.ERROR));
            return;
        }

        if (!realmsManager.isPlayerInRealm(player.getUuid())) {
            player.sendMessage(Message.raw("You are not in a realm.").color(MessageColors.WARNING));
            return;
        }

        player.sendMessage(Message.raw("Exiting realm...").color(MessageColors.INFO));

        realmsManager.exitRealm(player.getUuid())
            .thenAccept(success -> {
                if (success) {
                    player.sendMessage(Message.raw("Left the realm.").color(MessageColors.SUCCESS));
                } else {
                    player.sendMessage(Message.raw("Failed to exit realm.").color(MessageColors.ERROR));
                }
            });
    }
}
