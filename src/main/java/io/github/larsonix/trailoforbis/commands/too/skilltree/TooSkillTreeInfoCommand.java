package io.github.larsonix.trailoforbis.commands.too.skilltree;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
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
import io.github.larsonix.trailoforbis.api.services.SkillTreeService;
import io.github.larsonix.trailoforbis.skilltree.config.SkillNode;
import io.github.larsonix.trailoforbis.skilltree.model.StatModifier;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.Optional;

/**
 * Shows detailed information about a skill node.
 *
 * <p>Usage: /too skilltree info &lt;nodeId&gt;
 */
public final class TooSkillTreeInfoCommand extends OpenPlayerCommand {
    @SuppressWarnings("unused")
    private final TrailOfOrbis plugin;
    private final RequiredArg<String> nodeIdArg;

    public TooSkillTreeInfoCommand(TrailOfOrbis plugin) {
        super("info", "Show detailed node information");
        this.addAliases("details", "node");
        this.plugin = plugin;

        nodeIdArg = this.withRequiredArg("nodeId", "Node ID to inspect", ArgTypes.STRING);
    }

    @Override
    protected void execute(
        @Nonnull CommandContext context,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef player,
        @Nonnull World world
    ) {
        String nodeId = nodeIdArg.get(context);

        // Check if skill tree system is enabled
        Optional<SkillTreeService> serviceOpt = ServiceRegistry.get(SkillTreeService.class);
        if (serviceOpt.isEmpty()) {
            player.sendMessage(Message.raw("Skill tree system is not enabled.").color(MessageColors.ERROR));
            return;
        }

        SkillTreeService service = serviceOpt.get();

        Optional<SkillNode> nodeOpt = service.getNode(nodeId);
        if (nodeOpt.isEmpty()) {
            player.sendMessage(Message.raw("Node not found : " + nodeId).color(MessageColors.ERROR));
            return;
        }

        SkillNode node = nodeOpt.get();
        String typeLabel = node.isKeystone() ? " [KEYSTONE]" :
                          node.isNotable() ? " [NOTABLE]" :
                          node.isStartNode() ? " [START]" : "";

        int cost = node.getCost();
        player.sendMessage(Message.empty()
            .insert(Message.raw("=== " + node.getName() + " ===\n").color(MessageColors.GOLD))
            .insert(Message.raw(node.getDescription() + "\n").color(MessageColors.WHITE))
            .insert(Message.raw("ID : " + node.getId() + "\n").color(MessageColors.GRAY))
            .insert(Message.raw("Tier : " + node.getTier()).color(MessageColors.GRAY))
            .insert(Message.raw(" | Cost : " + cost + " point" + (cost != 1 ? "s" : "")).color(MessageColors.INFO))
            .insert(Message.raw(typeLabel + "\n").color(MessageColors.BLUE)));

        // Show modifiers
        if (!node.getModifiers().isEmpty()) {
            player.sendMessage(Message.raw("Bonuses :").color(MessageColors.INFO));
            for (StatModifier mod : node.getModifiers()) {
                player.sendMessage(Message.raw("  " + mod.toString()).color(MessageColors.SUCCESS));
            }
        }

        // Show connections
        if (!node.getConnections().isEmpty()) {
            player.sendMessage(Message.raw("Connections : " + String.join(", ", node.getConnections())).color(MessageColors.GRAY));
        }
    }
}
