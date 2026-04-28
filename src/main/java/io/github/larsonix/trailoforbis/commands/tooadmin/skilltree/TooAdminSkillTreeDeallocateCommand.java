package io.github.larsonix.trailoforbis.commands.tooadmin.skilltree;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
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
import io.github.larsonix.trailoforbis.skilltree.SkillTreeManager;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.UUID;

/**
 * Admin command to force-deallocate a skill node for a player.
 *
 * <p>Usage: /tooadmin skilltree deallocate &lt;player&gt; &lt;node&gt; [refund]
 *
 * <p>Bypasses connectivity check. Optional refund parameter (default: true).
 */
public final class TooAdminSkillTreeDeallocateCommand extends AbstractPlayerCommand {

    private final TrailOfOrbis plugin;
    private final RequiredArg<String> targetArg;
    private final RequiredArg<String> nodeIdArg;
    private final OptionalArg<Boolean> refundArg;

    public TooAdminSkillTreeDeallocateCommand(TrailOfOrbis plugin) {
        super("deallocate", "Force deallocate skill node");
        this.addAliases("dealloc");
        this.plugin = plugin;

        targetArg = this.withRequiredArg("player", "Target player name", ArgTypes.STRING);
        nodeIdArg = this.withRequiredArg("node", "Skill node ID", ArgTypes.STRING);
        refundArg = this.withOptionalArg("refund", "Refund point (true/false)", ArgTypes.BOOLEAN);
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
        Boolean refundValue = refundArg.get(context);
        boolean refund = refundValue != null ? refundValue : true;

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

        // Origin node cannot be deallocated - even by admins
        if (SkillTreeManager.ORIGIN_NODE_ID.equals(nodeId)) {
            sender.sendMessage(Message.empty()
                .insert(Message.raw("[TooAdmin] ").color(MessageColors.GOLD))
                .insert(Message.raw("Cannot deallocate origin node - it is the immutable skill tree root.").color(MessageColors.ERROR)));
            return;
        }

        // Check if node is actually allocated
        if (!skillTreeService.getAllocatedNodes(uuid).contains(nodeId)) {
            sender.sendMessage(Message.empty()
                .insert(Message.raw("[TooAdmin] ").color(MessageColors.GOLD))
                .insert(Message.raw("Node ").color(MessageColors.WARNING))
                .insert(Message.raw(nodeId).color(MessageColors.WHITE))
                .insert(Message.raw(" is not allocated for ").color(MessageColors.WARNING))
                .insert(Message.raw(targetName).color(MessageColors.WHITE)));
            return;
        }

        // Force deallocate node (bypasses connectivity check)
        boolean success = skillTreeService.adminDeallocateNode(uuid, nodeId, refund);

        if (success) {
            String refundText = refund ? " (point refunded)" : " (no refund)";
            sender.sendMessage(Message.empty()
                .insert(Message.raw("[TooAdmin] ").color(MessageColors.GOLD))
                .insert(Message.raw("Force-deallocated node ").color(MessageColors.SUCCESS))
                .insert(Message.raw(nodeId).color(MessageColors.WHITE))
                .insert(Message.raw(" for ").color(MessageColors.SUCCESS))
                .insert(Message.raw(targetName).color(MessageColors.WHITE))
                .insert(Message.raw(refundText).color(MessageColors.GRAY)));

            AdminCommandHelper.logAdminAction(plugin, sender, "SKILLTREE DEALLOC", targetName,
                "deallocated " + nodeId + (refund ? " with refund" : " without refund"));
        } else {
            sender.sendMessage(Message.raw("Operation failed !").color(MessageColors.ERROR));
        }
    }
}
