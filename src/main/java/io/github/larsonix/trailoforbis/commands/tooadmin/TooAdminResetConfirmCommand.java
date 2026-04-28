package io.github.larsonix.trailoforbis.commands.tooadmin;

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
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.api.services.AttributeService;
import io.github.larsonix.trailoforbis.commands.admin.AdminCommandHelper;
import io.github.larsonix.trailoforbis.database.models.PlayerData;
import io.github.larsonix.trailoforbis.database.repository.PlayerDataRepository;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.UUID;

/**
 * Admin command to execute attribute reset for a player.
 *
 * <p>Usage: /tooadmin resetconfirm &lt;player&gt;
 */
public final class TooAdminResetConfirmCommand extends AbstractPlayerCommand {
    private final TrailOfOrbis plugin;
    private final RequiredArg<String> targetArg;

    public TooAdminResetConfirmCommand(TrailOfOrbis plugin) {
        super("resetconfirm", "Execute attribute reset for a player");
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

        AttributeService service = ServiceRegistry.get(AttributeService.class).orElse(null);
        if (service == null) {
            sender.sendMessage(Message.raw("AttributeService not available !").color(MessageColors.ERROR));
            return;
        }

        PlayerDataRepository repo = service.getPlayerDataRepository();
        Optional<PlayerData> targetData = AdminCommandHelper.resolvePlayer(targetName, repo);

        if (targetData.isEmpty()) {
            sender.sendMessage(Message.raw("Player not found : " + targetName).color(MessageColors.ERROR));
            return;
        }

        UUID targetUuid = targetData.get().getUuid();

        // Execute reset
        int refunded = service.resetAllAttributesAdmin(targetUuid);

        if (refunded >= 0) {
            // Recalculate stats after reset (triggers ECS callback internally)
            service.recalculateStats(targetUuid);

            sender.sendMessage(Message.empty()
                .insert(Message.raw("[TooAdmin] ").color(MessageColors.GOLD))
                .insert(Message.raw("Reset all attributes for ").color(MessageColors.SUCCESS))
                .insert(Message.raw(targetName).color(MessageColors.WHITE))
                .insert(Message.raw(". Refunded ").color(MessageColors.SUCCESS))
                .insert(Message.raw(String.valueOf(refunded)).color(MessageColors.WHITE))
                .insert(Message.raw(" points.").color(MessageColors.SUCCESS)));

            AdminCommandHelper.logAdminAction(plugin, sender, "RESET", targetName, "reset", refunded);
        } else {
            sender.sendMessage(Message.raw("Reset failed !").color(MessageColors.ERROR));
        }
    }
}
