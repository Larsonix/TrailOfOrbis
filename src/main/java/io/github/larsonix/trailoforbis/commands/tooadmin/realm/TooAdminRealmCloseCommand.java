package io.github.larsonix.trailoforbis.commands.tooadmin.realm;

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
import io.github.larsonix.trailoforbis.commands.admin.AdminCommandHelper;
import io.github.larsonix.trailoforbis.maps.RealmsManager;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Force closes a realm by ID.
 *
 * <p>Usage: /tooadmin realm close &lt;realmId&gt;
 */
public final class TooAdminRealmCloseCommand extends AbstractPlayerCommand {

    private final TrailOfOrbis plugin;
    private final RequiredArg<String> realmIdArg;

    public TooAdminRealmCloseCommand(TrailOfOrbis plugin) {
        super("close", "Force close a realm");
        this.plugin = plugin;

        realmIdArg = this.withRequiredArg("realmId", "Realm ID (partial match supported)", ArgTypes.STRING);
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

        String realmIdStr = realmIdArg.get(context);

        // Find realm by partial ID
        UUID realmId = findRealmByPartialId(realmIdStr, realmsManager);
        if (realmId == null) {
            sender.sendMessage(Message.empty()
                .insert(Message.raw("Realm not found : ").color(MessageColors.ERROR))
                .insert(Message.raw(realmIdStr).color(MessageColors.WHITE)));
            sender.sendMessage(Message.raw("Use /tooadmin realm list to see active realms.").color(MessageColors.GRAY));
            return;
        }

        sender.sendMessage(Message.raw("Force closing realm...").color(MessageColors.GRAY));

        realmsManager.forceCloseRealm(realmId);

        sender.sendMessage(Message.empty()
            .insert(Message.raw("[TooAdmin] ").color(MessageColors.GOLD))
            .insert(Message.raw("Realm closed !").color(MessageColors.SUCCESS)));

        AdminCommandHelper.logAdminAction(plugin, sender, "REALM CLOSE",
            sender.getUsername(), realmIdStr, 0);
    }

    private UUID findRealmByPartialId(String partialId, RealmsManager realmsManager) {
        // Try exact match first
        try {
            UUID exactId = UUID.fromString(partialId);
            if (realmsManager.getRealm(exactId).isPresent()) {
                return exactId;
            }
        } catch (IllegalArgumentException ignored) {
            // Not a valid UUID, try partial match
        }

        // Try partial match
        String searchLower = partialId.toLowerCase();
        for (RealmInstance realm : realmsManager.getActiveRealms()) {
            if (realm.getRealmId().toString().toLowerCase().startsWith(searchLower)) {
                return realm.getRealmId();
            }
        }

        return null;
    }
}
