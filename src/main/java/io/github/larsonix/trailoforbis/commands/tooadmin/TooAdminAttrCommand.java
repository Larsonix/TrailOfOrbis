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
import io.github.larsonix.trailoforbis.attributes.AttributeType;
import io.github.larsonix.trailoforbis.commands.admin.AdminCommandHelper;
import io.github.larsonix.trailoforbis.database.models.PlayerData;
import io.github.larsonix.trailoforbis.database.repository.PlayerDataRepository;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.UUID;

/**
 * Admin command to manage player attribute values.
 *
 * <p>Usage: /tooadmin attr &lt;add|remove|set&gt; &lt;player&gt; &lt;str|dex|int|vit|lck&gt; &lt;amount&gt;
 */
public final class TooAdminAttrCommand extends AbstractPlayerCommand {
    private final TrailOfOrbis plugin;
    private final RequiredArg<String> operationArg;
    private final RequiredArg<String> targetArg;
    private final RequiredArg<String> attrArg;
    private final RequiredArg<Integer> amountArg;

    public TooAdminAttrCommand(TrailOfOrbis plugin) {
        super("attr", "Manage player attributes");
        this.addAliases("attribute");
        this.plugin = plugin;

        operationArg = this.withRequiredArg("operation", "add/remove/set", ArgTypes.STRING);
        targetArg = this.withRequiredArg("player", "Target player name", ArgTypes.STRING);
        attrArg = this.withRequiredArg("attribute", "str/dex/int/vit/lck", ArgTypes.STRING);
        amountArg = this.withRequiredArg("amount", "Attribute amount", ArgTypes.INTEGER);
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
        String attrName = attrArg.get(context);
        Integer amount = amountArg.get(context);

        // Parse attribute type
        AttributeType attrType = AttributeType.fromString(attrName);
        if (attrType == null) {
            sender.sendMessage(Message.raw("Invalid attribute! Use : str, dex, int, vit, lck").color(MessageColors.ERROR));
            return;
        }

        AttributeService service = ServiceRegistry.get(AttributeService.class).orElse(null);
        if (service == null) {
            sender.sendMessage(Message.raw("AttributeService not available !").color(MessageColors.ERROR));
            return;
        }

        PlayerDataRepository repo = service.getPlayerDataRepository();
        Optional<PlayerData> targetData = AdminCommandHelper.resolvePlayer(targetName, repo);

        if (targetData.isEmpty()) {
            sender.sendMessage(Message.raw("Player not found : " + targetName).color(MessageColors.ERROR));
            return;
        }

        UUID targetUuid = targetData.get().getUuid();
        int currentValue = AdminCommandHelper.getAttributeValue(targetData.get(), attrType);
        boolean success;

        switch (operation.toLowerCase()) {
            case "add" -> {
                if (amount < 0) {
                    sender.sendMessage(Message.raw("Amount must be positive !").color(MessageColors.ERROR));
                    return;
                }
                success = service.modifyAttribute(targetUuid, attrType, amount);
            }
            case "remove" -> {
                if (amount < 0) {
                    sender.sendMessage(Message.raw("Amount must be positive !").color(MessageColors.ERROR));
                    return;
                }
                if (amount > currentValue) {
                    sender.sendMessage(Message.raw("Cannot remove " + amount + " - " + attrType.getDisplayName() +
                        " only has " + currentValue + " !").color(MessageColors.ERROR));
                    return;
                }
                success = service.modifyAttribute(targetUuid, attrType, -amount);
            }
            case "set" -> {
                if (amount < 0) {
                    sender.sendMessage(Message.raw("Amount must be >= 0 !").color(MessageColors.ERROR));
                    return;
                }
                success = service.setAttribute(targetUuid, attrType, amount);
            }
            default -> {
                sender.sendMessage(Message.raw("Invalid operation! Use : add, remove, set").color(MessageColors.ERROR));
                return;
            }
        }

        if (success) {
            // Recalculate stats after attribute change (triggers ECS callback internally)
            service.recalculateStats(targetUuid);

            int newValue = service.getPlayerDataRepository().get(targetUuid)
                .map(d -> AdminCommandHelper.getAttributeValue(d, attrType)).orElse(0);

            sender.sendMessage(Message.empty()
                .insert(Message.raw("[TooAdmin] ").color(MessageColors.GOLD))
                .insert(Message.raw("Set ").color(MessageColors.SUCCESS))
                .insert(Message.raw(targetName).color(MessageColors.WHITE))
                .insert(Message.raw("'s ").color(MessageColors.SUCCESS))
                .insert(Message.raw(attrType.getDisplayName()).color(attrType.getHexColor()))
                .insert(Message.raw(" to ").color(MessageColors.SUCCESS))
                .insert(Message.raw(String.valueOf(newValue)).color(MessageColors.WHITE)));

            AdminCommandHelper.logAdminAction(plugin, sender, "ATTRIBUTE " + attrType.name(), targetName, operation, amount);
        } else {
            sender.sendMessage(Message.raw("Operation failed !").color(MessageColors.ERROR));
        }
    }
}
