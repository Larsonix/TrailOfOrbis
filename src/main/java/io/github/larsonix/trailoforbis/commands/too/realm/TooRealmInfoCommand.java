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
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;

/**
 * Shows current realm information.
 *
 * <p>Usage: /too realm info
 */
public final class TooRealmInfoCommand extends OpenPlayerCommand {

    private final TrailOfOrbis plugin;

    public TooRealmInfoCommand(TrailOfOrbis plugin) {
        super("info", "Show current realm info");
        this.addAliases("status");
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

        var realmOpt = realmsManager.getPlayerRealm(player.getUuid());

        if (realmOpt.isEmpty()) {
            player.sendMessage(Message.raw("You are not in a realm.").color(MessageColors.WARNING));
            return;
        }

        RealmInstance realm = realmOpt.get();
        RealmMapData mapData = realm.getMapData();

        player.sendMessage(Message.empty()
            .insert(Message.raw("=== Current Realm ===").color(MessageColors.GOLD)));

        player.sendMessage(Message.empty()
            .insert(Message.raw("  Biome : ").color(MessageColors.GRAY))
            .insert(Message.raw(mapData.biome().getDisplayName()).color(MessageColors.WHITE)));

        player.sendMessage(Message.empty()
            .insert(Message.raw("  Size : ").color(MessageColors.GRAY))
            .insert(Message.raw(mapData.size().getDisplayName()).color(MessageColors.WHITE))
            .insert(Message.raw(" (").color(MessageColors.GRAY))
            .insert(Message.raw(mapData.size().getArenaDiameter() + "x" + mapData.size().getArenaDiameter()).color(MessageColors.WHITE))
            .insert(Message.raw(" blocks)").color(MessageColors.GRAY)));

        player.sendMessage(Message.empty()
            .insert(Message.raw("  Level : ").color(MessageColors.GRAY))
            .insert(Message.raw(String.valueOf(mapData.level())).color(MessageColors.WHITE)));

        player.sendMessage(Message.empty()
            .insert(Message.raw("  State : ").color(MessageColors.GRAY))
            .insert(Message.raw(realm.getState().name()).color(getStateColor(realm))));

        player.sendMessage(Message.empty()
            .insert(Message.raw("  Players : ").color(MessageColors.GRAY))
            .insert(Message.raw(String.valueOf(realm.getPlayerCount())).color(MessageColors.WHITE)));

        if (!mapData.modifiers().isEmpty()) {
            player.sendMessage(Message.empty()
                .insert(Message.raw("  Modifiers : ").color(MessageColors.GRAY))
                .insert(Message.raw(String.valueOf(mapData.modifiers().size()) + " active").color(MessageColors.INFO)));
        }

        player.sendMessage(Message.raw("Use /too realm exit to leave.").color(MessageColors.GRAY));
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
