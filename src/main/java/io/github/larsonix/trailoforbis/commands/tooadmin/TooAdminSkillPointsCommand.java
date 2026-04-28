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
import io.github.larsonix.trailoforbis.api.services.SkillTreeService;
import io.github.larsonix.trailoforbis.commands.admin.AdminCommandHelper;
import io.github.larsonix.trailoforbis.skilltree.model.SkillTreeData;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.UUID;

/**
 * Admin command to manage player skill tree points.
 *
 * <p>Usage: /tooadmin skillpoints &lt;add|remove|set&gt; &lt;player&gt; &lt;amount&gt;
 */
public final class TooAdminSkillPointsCommand extends AbstractPlayerCommand {
    private final TrailOfOrbis plugin;
    private final RequiredArg<String> operationArg;
    private final RequiredArg<String> targetArg;
    private final RequiredArg<Integer> amountArg;

    public TooAdminSkillPointsCommand(TrailOfOrbis plugin) {
        super("skillpoints", "Manage player skill tree points");
        this.addAliases("sp");
        this.plugin = plugin;

        operationArg = this.withRequiredArg("operation", "add/remove/set", ArgTypes.STRING);
        targetArg = this.withRequiredArg("player", "Target player name", ArgTypes.STRING);
        amountArg = this.withRequiredArg("amount", "Point amount", ArgTypes.INTEGER);
    }

    @Override
    protected void execute(
        @Nonnull CommandContext context,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef sender,
        @Nonnull World world
    ) {
        String operation = operationArg.get(context);
        String targetName = targetArg.get(context);
        Integer amount = amountArg.get(context);

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
        int currentPoints = data.getSkillPoints();
        boolean success = false;

        switch (operation.toLowerCase()) {
            case "add" -> {
                if (amount < 0) {
                    sender.sendMessage(Message.raw("Amount must be positive !").color(MessageColors.ERROR));
                    return;
                }
                skillTreeService.grantSkillPoints(uuid, amount);
                success = true;
            }
            case "remove" -> {
                if (amount < 0) {
                    sender.sendMessage(Message.raw("Amount must be positive !").color(MessageColors.ERROR));
                    return;
                }
                if (amount > currentPoints) {
                    sender.sendMessage(Message.raw("Cannot remove " + amount + " points - player only has " +
                        currentPoints + " !").color(MessageColors.ERROR));
                    return;
                }
                success = skillTreeService.removeSkillPoints(uuid, amount);
            }
            case "set" -> {
                if (amount < 0) {
                    sender.sendMessage(Message.raw("Amount must be >= 0 !").color(MessageColors.ERROR));
                    return;
                }
                skillTreeService.setSkillPoints(uuid, amount);
                success = true;
            }
            default -> {
                sender.sendMessage(Message.raw("Invalid operation! Use : add, remove, set").color(MessageColors.ERROR));
                return;
            }
        }

        if (success) {
            int newPoints = skillTreeService.getAvailablePoints(uuid);
            sender.sendMessage(Message.empty()
                .insert(Message.raw("[TooAdmin] ").color(MessageColors.GOLD))
                .insert(Message.raw("Set ").color(MessageColors.SUCCESS))
                .insert(Message.raw(targetName).color(MessageColors.WHITE))
                .insert(Message.raw("'s skill points to ").color(MessageColors.SUCCESS))
                .insert(Message.raw(String.valueOf(newPoints)).color(MessageColors.WHITE)));

            AdminCommandHelper.logAdminAction(plugin, sender, "SKILLPOINTS " + operation.toUpperCase(), targetName, operation, amount);
        } else {
            sender.sendMessage(Message.raw("Operation failed !").color(MessageColors.ERROR));
        }
    }
}
