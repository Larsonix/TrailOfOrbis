package io.github.larsonix.trailoforbis.commands.too.skilltree;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import io.github.larsonix.trailoforbis.commands.base.OpenPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.api.services.SkillTreeService;
import io.github.larsonix.trailoforbis.skilltree.config.SkillNode;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.UUID;

/**
 * Lists all allocated skill nodes.
 *
 * <p>Usage: /too skilltree list
 */
public final class TooSkillTreeListCommand extends OpenPlayerCommand {
    @SuppressWarnings("unused")
    private final TrailOfOrbis plugin;

    public TooSkillTreeListCommand(TrailOfOrbis plugin) {
        super("list", "List your allocated nodes");
        this.addAliases("allocated", "nodes");
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
        UUID playerId = player.getUuid();

        // Check if skill tree system is enabled
        Optional<SkillTreeService> serviceOpt = ServiceRegistry.get(SkillTreeService.class);
        if (serviceOpt.isEmpty()) {
            player.sendMessage(Message.raw("Skill tree system is not enabled.").color(MessageColors.ERROR));
            return;
        }

        SkillTreeService service = serviceOpt.get();
        var allocatedNodes = service.getAllocatedNodes(playerId);

        if (allocatedNodes.isEmpty()) {
            player.sendMessage(Message.raw("You have no allocated nodes.").color(MessageColors.WARNING));
            return;
        }

        player.sendMessage(Message.raw("=== Allocated Nodes (" + allocatedNodes.size() + ") ===").color(MessageColors.GOLD));

        for (String nodeId : allocatedNodes) {
            Optional<SkillNode> nodeOpt = service.getNode(nodeId);
            String name = nodeOpt.map(SkillNode::getName).orElse(nodeId);
            String tier = nodeOpt.map(n -> "T" + n.getTier()).orElse("");

            player.sendMessage(Message.empty()
                .insert(Message.raw("  " + name).color(MessageColors.WHITE))
                .insert(Message.raw(" (" + nodeId + ") ").color(MessageColors.GRAY))
                .insert(Message.raw(tier).color(MessageColors.INFO)));
        }
    }
}
