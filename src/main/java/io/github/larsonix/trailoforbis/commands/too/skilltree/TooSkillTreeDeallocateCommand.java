package io.github.larsonix.trailoforbis.commands.too.skilltree;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import io.github.larsonix.trailoforbis.commands.base.OpenPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.api.services.SkillTreeMapService;
import io.github.larsonix.trailoforbis.api.services.SkillTreeService;
import io.github.larsonix.trailoforbis.skilltree.SkillTreeManager;
import io.github.larsonix.trailoforbis.skilltree.config.SkillNode;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Deallocates a skill node.
 *
 * <p>Usage: /too skilltree deallocate &lt;nodeId&gt;
 */
public final class TooSkillTreeDeallocateCommand extends OpenPlayerCommand {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @SuppressWarnings("unused")
    private final TrailOfOrbis plugin;
    private final RequiredArg<String> nodeIdArg;

    public TooSkillTreeDeallocateCommand(TrailOfOrbis plugin) {
        super("deallocate", "Deallocate a skill node");
        this.addAliases("refund", "remove");
        this.plugin = plugin;

        nodeIdArg = this.withRequiredArg("nodeId", "Node ID to deallocate", ArgTypes.STRING);
    }

    @Override
    protected void execute(
        @Nonnull CommandContext context,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef player,
        @Nonnull World world
    ) {
        UUID playerId = player.getUuid();
        String nodeId = nodeIdArg.get(context);

        // Check if skill tree system is enabled
        Optional<SkillTreeService> serviceOpt = ServiceRegistry.get(SkillTreeService.class);
        if (serviceOpt.isEmpty()) {
            player.sendMessage(Message.raw("Skill tree system is not enabled.").color(MessageColors.ERROR));
            return;
        }

        SkillTreeService service = serviceOpt.get();

        if (!service.canDeallocate(playerId, nodeId)) {
            if (SkillTreeManager.ORIGIN_NODE_ID.equals(nodeId)) {
                player.sendMessage(Message.raw("Cannot deallocate origin node - it is the skill tree root.").color(MessageColors.ERROR));
            } else if (!service.getAllocatedNodes(playerId).contains(nodeId)) {
                player.sendMessage(Message.raw("Node not allocated : " + nodeId).color(MessageColors.ERROR));
            } else {
                player.sendMessage(Message.raw("Cannot deallocate : would orphan other nodes.").color(MessageColors.ERROR));
            }
            return;
        }

        if (service.deallocateNode(playerId, nodeId)) {
            Optional<SkillNode> node = service.getNode(nodeId);
            String name = node.map(SkillNode::getName).orElse(nodeId);
            int refunded = node.map(SkillNode::getCost).orElse(1);
            int remaining = service.getAvailablePoints(playerId);

            player.sendMessage(Message.empty()
                .insert(Message.raw("Deallocated : ").color(MessageColors.WARNING))
                .insert(Message.raw(name).color(MessageColors.GOLD))
                .insert(Message.raw(" (+" + refunded + " point" + (refunded != 1 ? "s" : "") + ", " + remaining + " total)").color(MessageColors.GRAY)));

            // Refresh map if viewing
            refreshMapIfViewing(player, playerId);
        } else {
            player.sendMessage(Message.raw("Failed to deallocate node.").color(MessageColors.ERROR));
        }
    }

    private void refreshMapIfViewing(PlayerRef player, UUID playerId) {
        Optional<SkillTreeMapService> mapServiceOpt = ServiceRegistry.get(SkillTreeMapService.class);
        if (mapServiceOpt.isEmpty()) {
            LOGGER.at(Level.FINE).log("SkillTreeMapService not available, skipping map refresh for %s", playerId);
            return;
        }

        SkillTreeMapService mapService = mapServiceOpt.get();
        if (mapService.isInMapMode(playerId)) {
            mapService.refreshMap(player);
        }
    }
}
