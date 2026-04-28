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
import io.github.larsonix.trailoforbis.skilltree.config.SkillNode;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Admin command to force-allocate ALL skill nodes for a player.
 *
 * <p>Usage: /tooadmin skilltree allocateall &lt;player&gt;
 *
 * <p>Bypasses point check and adjacency requirement for all nodes.
 */
public final class TooAdminSkillTreeAllocateAllCommand extends AbstractPlayerCommand {

    private final TrailOfOrbis plugin;
    private final RequiredArg<String> targetArg;

    public TooAdminSkillTreeAllocateAllCommand(TrailOfOrbis plugin) {
        super("allocateall", "Force allocate all skill nodes");
        this.addAliases("allocall", "allall", "aa");
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

        // Get all nodes and currently allocated nodes
        Collection<SkillNode> allNodes = skillTreeService.getAllNodes();
        Set<String> alreadyAllocated = skillTreeService.getAllocatedNodes(uuid);

        if (allNodes.isEmpty()) {
            sender.sendMessage(Message.empty()
                .insert(Message.raw("[TooAdmin] ").color(MessageColors.GOLD))
                .insert(Message.raw("No skill nodes defined in config !").color(MessageColors.ERROR)));
            return;
        }

        // Allocate all unallocated nodes
        int allocatedCount = 0;
        int skippedCount = 0;

        for (SkillNode node : allNodes) {
            String nodeId = node.getId();
            if (alreadyAllocated.contains(nodeId)) {
                skippedCount++;
                continue;
            }

            boolean success = skillTreeService.adminAllocateNode(uuid, nodeId);
            if (success) {
                allocatedCount++;
            }
        }

        // Report results
        if (allocatedCount == 0 && skippedCount > 0) {
            sender.sendMessage(Message.empty()
                .insert(Message.raw("[TooAdmin] ").color(MessageColors.GOLD))
                .insert(Message.raw("All ").color(MessageColors.WARNING))
                .insert(Message.raw(String.valueOf(skippedCount)).color(MessageColors.WHITE))
                .insert(Message.raw(" nodes already allocated for ").color(MessageColors.WARNING))
                .insert(Message.raw(targetName).color(MessageColors.WHITE)));
        } else {
            sender.sendMessage(Message.empty()
                .insert(Message.raw("[TooAdmin] ").color(MessageColors.GOLD))
                .insert(Message.raw("Force-allocated ").color(MessageColors.SUCCESS))
                .insert(Message.raw(String.valueOf(allocatedCount)).color(MessageColors.WHITE))
                .insert(Message.raw(" nodes for ").color(MessageColors.SUCCESS))
                .insert(Message.raw(targetName).color(MessageColors.WHITE))
                .insert(Message.raw(" (").color(MessageColors.GRAY))
                .insert(Message.raw(String.valueOf(skippedCount)).color(MessageColors.WHITE))
                .insert(Message.raw(" already allocated)").color(MessageColors.GRAY)));

            AdminCommandHelper.logAdminAction(plugin, sender, "SKILLTREE ALLOCALL", targetName,
                "allocated " + allocatedCount + " nodes (total: " + allNodes.size() + ")");
        }
    }
}
