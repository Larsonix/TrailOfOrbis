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
import io.github.larsonix.trailoforbis.skilltree.config.SkillNode;
import io.github.larsonix.trailoforbis.skilltree.model.StatModifier;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Allocates a skill node.
 *
 * <p>Usage: /too skilltree allocate &lt;nodeId&gt;
 */
public final class TooSkillTreeAllocateCommand extends OpenPlayerCommand {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @SuppressWarnings("unused")
    private final TrailOfOrbis plugin;
    private final RequiredArg<String> nodeIdArg;

    public TooSkillTreeAllocateCommand(TrailOfOrbis plugin) {
        super("allocate", "Allocate a skill node");
        this.addAliases("learn", "add");
        this.plugin = plugin;

        nodeIdArg = this.withRequiredArg("nodeId", "Node ID to allocate", ArgTypes.STRING);
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

        if (!service.canAllocate(playerId, nodeId)) {
            // Check specific failure reasons
            Optional<SkillNode> nodeOpt = service.getNode(nodeId);
            if (nodeOpt.isEmpty()) {
                player.sendMessage(Message.raw("Node not found : " + nodeId).color(MessageColors.ERROR));
            } else if (service.getAllocatedNodes(playerId).contains(nodeId)) {
                player.sendMessage(Message.raw("Node already allocated !").color(MessageColors.ERROR));
            } else {
                int available = service.getAvailablePoints(playerId);
                int cost = nodeOpt.get().getCost();
                if (available <= 0) {
                    player.sendMessage(Message.raw("You don't have any skill points !").color(MessageColors.ERROR));
                } else if (available < cost) {
                    player.sendMessage(Message.empty()
                        .insert(Message.raw("Not enough skill points! ").color(MessageColors.ERROR))
                        .insert(Message.raw("Need " + cost).color(MessageColors.WARNING))
                        .insert(Message.raw(", have ").color(MessageColors.GRAY))
                        .insert(Message.raw(String.valueOf(available)).color(MessageColors.WHITE)));
                } else {
                    player.sendMessage(Message.raw("Cannot allocate : node must be connected to your tree.").color(MessageColors.ERROR));
                }
            }
            return;
        }

        if (service.allocateNode(playerId, nodeId)) {
            Optional<SkillNode> node = service.getNode(nodeId);
            String name = node.map(SkillNode::getName).orElse(nodeId);
            int remaining = service.getAvailablePoints(playerId);

            player.sendMessage(Message.empty()
                .insert(Message.raw("Allocated : ").color(MessageColors.SUCCESS))
                .insert(Message.raw(name).color(MessageColors.GOLD))
                .insert(Message.raw(" (" + remaining + " points remaining)").color(MessageColors.GRAY)));

            // Show node bonuses
            node.ifPresent(n -> {
                if (!n.getModifiers().isEmpty()) {
                    StringBuilder bonuses = new StringBuilder();
                    for (StatModifier mod : n.getModifiers()) {
                        if (!bonuses.isEmpty()) bonuses.append(", ");
                        bonuses.append(mod.toString());
                    }
                    player.sendMessage(Message.raw("Bonuses : " + bonuses).color(MessageColors.INFO));
                }
            });

            // Refresh map if viewing
            refreshMapIfViewing(player, playerId);
        } else {
            player.sendMessage(Message.raw("Failed to allocate node.").color(MessageColors.ERROR));
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
