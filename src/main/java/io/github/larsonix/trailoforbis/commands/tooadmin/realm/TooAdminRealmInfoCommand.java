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

/**
 * Shows info about the player's current realm.
 *
 * <p>Usage: /tooadmin realm info
 */
public class TooAdminRealmInfoCommand extends AbstractPlayerCommand {

    private final TrailOfOrbis plugin;

    public TooAdminRealmInfoCommand(TrailOfOrbis plugin) {
        super("info", "Show current realm info");
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

        var realmOpt = realmsManager.getPlayerRealm(sender.getUuid());

        if (realmOpt.isEmpty()) {
            sender.sendMessage(Message.raw("You are not in a realm.").color(MessageColors.GRAY));
            return;
        }

        RealmInstance realm = realmOpt.get();
        RealmMapData mapData = realm.getMapData();

        sender.sendMessage(Message.empty()
            .insert(Message.raw("[TooAdmin] ").color(MessageColors.GOLD))
            .insert(Message.raw("Current Realm Info :").color(MessageColors.WHITE)));

        sender.sendMessage(Message.empty()
            .insert(Message.raw("  ID : ").color(MessageColors.GRAY))
            .insert(Message.raw(realm.getRealmId().toString()).color(MessageColors.WHITE)));

        sender.sendMessage(Message.empty()
            .insert(Message.raw("  Biome : ").color(MessageColors.GRAY))
            .insert(Message.raw(mapData.biome().getDisplayName()).color(MessageColors.WHITE)));

        sender.sendMessage(Message.empty()
            .insert(Message.raw("  Size : ").color(MessageColors.GRAY))
            .insert(Message.raw(mapData.size().getDisplayName()).color(MessageColors.WHITE))
            .insert(Message.raw(" (").color(MessageColors.GRAY))
            .insert(Message.raw(mapData.size().getArenaDiameter() + "x" + mapData.size().getArenaDiameter()).color(MessageColors.WHITE))
            .insert(Message.raw(" blocks)").color(MessageColors.GRAY)));

        sender.sendMessage(Message.empty()
            .insert(Message.raw("  Level : ").color(MessageColors.GRAY))
            .insert(Message.raw(String.valueOf(mapData.level())).color(MessageColors.WHITE)));

        sender.sendMessage(Message.empty()
            .insert(Message.raw("  State : ").color(MessageColors.GRAY))
            .insert(Message.raw(realm.getState().name()).color(getStateColor(realm))));

        sender.sendMessage(Message.empty()
            .insert(Message.raw("  Players : ").color(MessageColors.GRAY))
            .insert(Message.raw(String.valueOf(realm.getPlayerCount())).color(MessageColors.WHITE)));

        sender.sendMessage(Message.empty()
            .insert(Message.raw("  Monster Count : ").color(MessageColors.GRAY))
            .insert(Message.raw(String.valueOf(mapData.calculateMonsterCount())).color(MessageColors.WHITE)));

        sender.sendMessage(Message.empty()
            .insert(Message.raw("  Timeout : ").color(MessageColors.GRAY))
            .insert(Message.raw(mapData.getTimeoutSeconds() + "s").color(MessageColors.WHITE)));

        if (!mapData.modifiers().isEmpty()) {
            sender.sendMessage(Message.empty()
                .insert(Message.raw("  Modifiers : ").color(MessageColors.GRAY))
                .insert(Message.raw(String.valueOf(mapData.modifiers().size())).color(MessageColors.WHITE)));
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
