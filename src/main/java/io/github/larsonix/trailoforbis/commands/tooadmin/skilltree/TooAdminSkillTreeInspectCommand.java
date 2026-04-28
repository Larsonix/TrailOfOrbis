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
import java.util.Set;
import java.util.UUID;

/**
 * Admin command to inspect a player's skill tree state.
 *
 * <p>Usage: /tooadmin skilltree inspect &lt;player&gt;
 */
public final class TooAdminSkillTreeInspectCommand extends AbstractPlayerCommand {

    private final TrailOfOrbis plugin;
    private final RequiredArg<String> targetArg;

    public TooAdminSkillTreeInspectCommand(TrailOfOrbis plugin) {
        super("inspect", "Inspect player skill tree");
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
        SkillTreeData data = skillTreeService.getSkillTreeData(uuid);

        // Header
        sender.sendMessage(Message.empty()
            .insert(Message.raw("=== ").color(MessageColors.GOLD))
            .insert(Message.raw(targetName).color(MessageColors.WHITE))
            .insert(Message.raw(" Skill Tree ===").color(MessageColors.GOLD)));

        // UUID
        sender.sendMessage(Message.empty()
            .insert(Message.raw("UUID : ").color(MessageColors.GRAY))
            .insert(Message.raw(uuid.toString()).color(MessageColors.WHITE)));

        // Skill Points
        sender.sendMessage(Message.empty()
            .insert(Message.raw("Skill Points : ").color(MessageColors.GRAY))
            .insert(Message.raw(String.valueOf(data.getSkillPoints())).color(MessageColors.SUCCESS)));

        // Total Points Earned
        sender.sendMessage(Message.empty()
            .insert(Message.raw("Total Earned : ").color(MessageColors.GRAY))
            .insert(Message.raw(String.valueOf(data.getTotalPointsEarned())).color(MessageColors.WHITE)));

        // Allocated Nodes
        Set<String> allocatedNodes = data.getAllocatedNodes();
        sender.sendMessage(Message.empty()
            .insert(Message.raw("Nodes Allocated : ").color(MessageColors.GRAY))
            .insert(Message.raw(String.valueOf(allocatedNodes.size())).color(MessageColors.INFO)));

        // List allocated nodes (if any)
        if (!allocatedNodes.isEmpty()) {
            String nodeList = String.join(", ", allocatedNodes);
            if (nodeList.length() > 80) {
                nodeList = nodeList.substring(0, 77) + "...";
            }
            sender.sendMessage(Message.empty()
                .insert(Message.raw("  Nodes : ").color(MessageColors.GRAY))
                .insert(Message.raw(nodeList).color(MessageColors.WHITE)));
        }

        // Respecs
        int freeRespecsRemaining = skillTreeService.getFreeRespecsRemaining(uuid);
        sender.sendMessage(Message.empty()
            .insert(Message.raw("Respecs Used : ").color(MessageColors.GRAY))
            .insert(Message.raw(String.valueOf(data.getRespecs())).color(MessageColors.WARNING))
            .insert(Message.raw(" (").color(MessageColors.GRAY))
            .insert(Message.raw(String.valueOf(freeRespecsRemaining)).color(MessageColors.SUCCESS))
            .insert(Message.raw(" free remaining)").color(MessageColors.GRAY)));

        // Timestamps
        sender.sendMessage(Message.empty()
            .insert(Message.raw("Created : ").color(MessageColors.GRAY))
            .insert(Message.raw(AdminCommandHelper.DATE_FORMAT.format(data.getCreatedAt())).color(MessageColors.WHITE)));

        sender.sendMessage(Message.empty()
            .insert(Message.raw("Last Modified : ").color(MessageColors.GRAY))
            .insert(Message.raw(AdminCommandHelper.DATE_FORMAT.format(data.getLastModified())).color(MessageColors.WHITE)));

        // Footer
        sender.sendMessage(Message.raw("--- End Skill Tree ---").color(MessageColors.GOLD));
    }
}
