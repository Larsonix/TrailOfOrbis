package io.github.larsonix.trailoforbis.commands.tooadmin.skilltree;

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
import io.github.larsonix.trailoforbis.api.services.SkillTreeService;
import io.github.larsonix.trailoforbis.commands.admin.AdminCommandHelper;
import io.github.larsonix.trailoforbis.skilltree.model.SkillTreeData;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.UUID;

/**
 * Admin command to force-reset a player's skill tree.
 *
 * <p>Usage: /tooadmin skilltree reset &lt;player&gt;
 *
 * <p>Bypasses free respec limit - admin can always reset.
 */
public final class TooAdminSkillTreeResetCommand extends AbstractPlayerCommand {

    private final TrailOfOrbis plugin;
    private final RequiredArg<String> targetArg;

    public TooAdminSkillTreeResetCommand(TrailOfOrbis plugin) {
        super("reset", "Force reset player skill tree");
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

        SkillTreeService skillTreeService = ServiceRegistry.get(SkillTreeService.class).orElse(null);
        AttributeService attributeService = ServiceRegistry.get(AttributeService.class).orElse(null);

        if (skillTreeService == null) {
            sender.sendMessage(Message.raw("SkillTreeService not available !").color(MessageColors.ERROR));
            return;
        }

        if (attributeService == null) {
            sender.sendMessage(Message.raw("AttributeService not available !").color(MessageColors.ERROR));
            return;
        }

        Optional<UUID> targetUuid = AdminCommandHelper.resolvePlayerUuid(
            targetName, attributeService.getPlayerDataRepository()
        );

        if (targetUuid.isEmpty()) {
            sender.sendMessage(Message.raw("Player not found : " + targetName).color(MessageColors.ERROR));
            return;
        }

        UUID uuid = targetUuid.get();

        // Get data before reset for logging
        SkillTreeData dataBefore = skillTreeService.getSkillTreeData(uuid);
        int nodesBeforeReset = dataBefore.getAllocatedNodes().size();

        // Perform admin reset (bypasses respec limit)
        skillTreeService.adminReset(uuid);

        // Get data after reset
        SkillTreeData dataAfter = skillTreeService.getSkillTreeData(uuid);

        sender.sendMessage(Message.empty()
            .insert(Message.raw("[TooAdmin] ").color(MessageColors.GOLD))
            .insert(Message.raw("Reset ").color(MessageColors.SUCCESS))
            .insert(Message.raw(targetName).color(MessageColors.WHITE))
            .insert(Message.raw("'s skill tree. Refunded ").color(MessageColors.SUCCESS))
            .insert(Message.raw(String.valueOf(nodesBeforeReset)).color(MessageColors.WHITE))
            .insert(Message.raw(" nodes, now has ").color(MessageColors.SUCCESS))
            .insert(Message.raw(String.valueOf(dataAfter.getSkillPoints())).color(MessageColors.WHITE))
            .insert(Message.raw(" points.").color(MessageColors.SUCCESS)));

        AdminCommandHelper.logAdminAction(plugin, sender, "SKILLTREE RESET", targetName,
            "reset " + nodesBeforeReset + " nodes");
    }
}
