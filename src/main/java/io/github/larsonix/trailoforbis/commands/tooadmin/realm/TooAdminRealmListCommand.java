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
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.Collection;

/**
 * Lists all active realm instances.
 *
 * <p>Usage: /tooadmin realm list
 */
public class TooAdminRealmListCommand extends AbstractPlayerCommand {

    private final TrailOfOrbis plugin;

    public TooAdminRealmListCommand(TrailOfOrbis plugin) {
        super("list", "List active realm instances");
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

        Collection<RealmInstance> realms = realmsManager.getActiveRealms();

        sender.sendMessage(Message.empty()
            .insert(Message.raw("[TooAdmin] ").color(MessageColors.GOLD))
            .insert(Message.raw("Active Realms : ").color(MessageColors.WHITE))
            .insert(Message.raw(String.valueOf(realms.size())).color(MessageColors.SUCCESS)));

        if (realms.isEmpty()) {
            sender.sendMessage(Message.raw("  No active realms.").color(MessageColors.GRAY));
            return;
        }

        for (RealmInstance realm : realms) {
            String shortId = realm.getRealmId().toString().substring(0, 8);
            RealmMapData mapData = realm.getMapData();

            sender.sendMessage(Message.empty()
                .insert(Message.raw("  [").color(MessageColors.GRAY))
                .insert(Message.raw(shortId).color(MessageColors.WHITE))
                .insert(Message.raw("] ").color(MessageColors.GRAY))
                .insert(Message.raw(mapData.biome().getDisplayName()).color(MessageColors.WHITE))
                .insert(Message.raw(" ").color(MessageColors.GRAY))
                .insert(Message.raw(mapData.size().getDisplayName()).color(MessageColors.WHITE))
                .insert(Message.raw(" L").color(MessageColors.GRAY))
                .insert(Message.raw(String.valueOf(mapData.level())).color(MessageColors.WHITE))
                .insert(Message.raw(" | ").color(MessageColors.GRAY))
                .insert(Message.raw(realm.getState().name()).color(getStateColor(realm)))
                .insert(Message.raw(" | Players : ").color(MessageColors.GRAY))
                .insert(Message.raw(String.valueOf(realm.getPlayerCount())).color(MessageColors.WHITE)));
        }
    }

    private String getStateColor(RealmInstance realm) {
        return switch (realm.getState()) {
            case CREATING -> MessageColors.GRAY;
            case READY -> MessageColors.SUCCESS;
            case ACTIVE -> MessageColors.WHITE;
            case ENDING -> MessageColors.GOLD;
            case CLOSING -> MessageColors.ERROR;
        };
    }
}
