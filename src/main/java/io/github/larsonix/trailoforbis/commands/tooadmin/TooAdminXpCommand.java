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
import io.github.larsonix.trailoforbis.commands.admin.AdminCommandHelper;
import io.github.larsonix.trailoforbis.database.models.PlayerData;
import io.github.larsonix.trailoforbis.database.repository.PlayerDataRepository;
import io.github.larsonix.trailoforbis.leveling.api.LevelingService;
import io.github.larsonix.trailoforbis.leveling.xp.XpSource;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.UUID;

/**
 * Admin command to manage player XP.
 *
 * <p>Usage: /tooadmin xp &lt;add|remove|set&gt; &lt;player&gt; &lt;amount&gt;
 */
public final class TooAdminXpCommand extends AbstractPlayerCommand {
    private final TrailOfOrbis plugin;
    private final RequiredArg<String> operationArg;
    private final RequiredArg<String> targetArg;
    private final RequiredArg<Integer> amountArg;

    public TooAdminXpCommand(TrailOfOrbis plugin) {
        super("xp", "Manage player XP");
        this.plugin = plugin;

        operationArg = this.withRequiredArg("operation", "add/remove/set", ArgTypes.STRING);
        targetArg = this.withRequiredArg("player", "Target player name", ArgTypes.STRING);
        amountArg = this.withRequiredArg("amount", "XP amount", ArgTypes.INTEGER);
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

        LevelingService levelingService = ServiceRegistry.get(LevelingService.class).orElse(null);
        if (levelingService == null) {
            sender.sendMessage(Message.raw("LevelingService not available !").color(MessageColors.ERROR));
            return;
        }

        AttributeService attributeService = ServiceRegistry.get(AttributeService.class).orElse(null);
        if (attributeService == null) {
            sender.sendMessage(Message.raw("AttributeService not available !").color(MessageColors.ERROR));
            return;
        }

        PlayerDataRepository repo = attributeService.getPlayerDataRepository();
        Optional<PlayerData> targetData = repo.getByUsername(targetName);

        if (targetData.isEmpty()) {
            sender.sendMessage(Message.raw("Player not found : " + targetName).color(MessageColors.ERROR));
            return;
        }

        UUID targetUuid = targetData.get().getUuid();
        long currentXp = levelingService.getXp(targetUuid);
        int oldLevel = levelingService.getLevel(targetUuid);

        switch (operation.toLowerCase()) {
            case "add" -> {
                if (amount < 0) {
                    sender.sendMessage(Message.raw("Amount must be positive !").color(MessageColors.ERROR));
                    return;
                }
                levelingService.addXp(targetUuid, amount, XpSource.ADMIN_COMMAND);
            }
            case "remove" -> {
                if (amount < 0) {
                    sender.sendMessage(Message.raw("Amount must be positive !").color(MessageColors.ERROR));
                    return;
                }
                if (amount > currentXp) {
                    sender.sendMessage(Message.raw("Cannot remove " + amount + " XP - player only has " +
                        currentXp + " !").color(MessageColors.ERROR));
                    return;
                }
                levelingService.removeXp(targetUuid, amount, XpSource.ADMIN_COMMAND);
            }
            case "set" -> {
                if (amount < 0) {
                    sender.sendMessage(Message.raw("Amount must be >= 0 !").color(MessageColors.ERROR));
                    return;
                }
                levelingService.setXp(targetUuid, amount);
            }
            default -> {
                sender.sendMessage(Message.raw("Invalid operation! Use : add, remove, set").color(MessageColors.ERROR));
                return;
            }
        }

        long newXp = levelingService.getXp(targetUuid);
        int newLevel = levelingService.getLevel(targetUuid);

        sender.sendMessage(Message.empty()
            .insert(Message.raw("[TooAdmin] ").color(MessageColors.GOLD))
            .insert(Message.raw("Set ").color(MessageColors.SUCCESS))
            .insert(Message.raw(targetName).color(MessageColors.WHITE))
            .insert(Message.raw("'s XP to ").color(MessageColors.SUCCESS))
            .insert(Message.raw(String.valueOf(newXp)).color(MessageColors.WHITE))
            .insert(Message.raw(" (Level " + newLevel + ")").color(MessageColors.GRAY)));

        if (newLevel != oldLevel) {
            sender.sendMessage(Message.empty()
                .insert(Message.raw("  Level changed : ").color(MessageColors.GRAY))
                .insert(Message.raw(String.valueOf(oldLevel)).color(MessageColors.WHITE))
                .insert(Message.raw(" -> ").color(MessageColors.GRAY))
                .insert(Message.raw(String.valueOf(newLevel)).color(MessageColors.WHITE)));
        }

        AdminCommandHelper.logAdminAction(plugin, sender, "XP " + operation.toUpperCase(), targetName, operation, amount);
    }
}
