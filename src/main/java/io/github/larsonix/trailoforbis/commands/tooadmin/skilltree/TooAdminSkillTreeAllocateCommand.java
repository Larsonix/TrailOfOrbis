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
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.UUID;

/**
 * Admin command to force-allocate a skill node for a player.
 *
 * <p>Usage: /tooadmin skilltree allocate &lt;player&gt; &lt;node&gt;
 *
 * <p>Bypasses point check and adjacency requirement.
 */
public final class TooAdminSkillTreeAllocateCommand extends AbstractPlayerCommand {

    private final TrailOfOrbis plugin;
    private final RequiredArg<String> targetArg;
    private final RequiredArg<String> nodeIdArg;

    public TooAdminSkillTreeAllocateCommand(TrailOfOrbis plugin) {
        super("allocate", "Force allocate skill node");
        this.addAliases("alloc");
        this.plugin = plugin;

        targetArg = this.withRequiredArg("player", "Target player name", ArgTypes.STRING);
        nodeIdArg = this.withRequiredArg("node", "Skill node ID", ArgTypes.STRING);
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
        String nodeId = nodeIdArg.get(context);

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

        // Check if node is already allocated
        if (skillTreeService.getAllocatedNodes(uuid).contains(nodeId)) {
            sender.sendMessage(Message.empty()
                .insert(Message.raw("[TooAdmin] ").color(MessageColors.GOLD))
                .insert(Message.raw("Node ").color(MessageColors.WARNING))
                .insert(Message.raw(nodeId).color(MessageColors.WHITE))
                .insert(Message.raw(" is already allocated for ").color(MessageColors.WARNING))
                .insert(Message.raw(targetName).color(MessageColors.WHITE)));
            return;
        }

        // Force allocate node (bypasses point check and adjacency)
        boolean success = skillTreeService.adminAllocateNode(uuid, nodeId);

        if (success) {
            sender.sendMessage(Message.empty()
                .insert(Message.raw("[TooAdmin] ").color(MessageColors.GOLD))
                .insert(Message.raw("Force-allocated node ").color(MessageColors.SUCCESS))
                .insert(Message.raw(nodeId).color(MessageColors.WHITE))
                .insert(Message.raw(" for ").color(MessageColors.SUCCESS))
                .insert(Message.raw(targetName).color(MessageColors.WHITE)));

            AdminCommandHelper.logAdminAction(plugin, sender, "SKILLTREE ALLOC", targetName, "allocated " + nodeId);
        } else {
            sender.sendMessage(Message.empty()
                .insert(Message.raw("[TooAdmin] ").color(MessageColors.GOLD))
                .insert(Message.raw("Failed to allocate - node ").color(MessageColors.ERROR))
                .insert(Message.raw(nodeId).color(MessageColors.WHITE))
                .insert(Message.raw(" does not exist in skill tree config.").color(MessageColors.ERROR)));
        }
    }
}
