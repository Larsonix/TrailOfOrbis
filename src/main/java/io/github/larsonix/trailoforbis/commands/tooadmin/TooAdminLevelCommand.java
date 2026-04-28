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
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.UUID;

/**
 * Admin command to manage player levels.
 *
 * <p>Usage: /tooadmin level &lt;add|remove|set|check&gt; &lt;player&gt; &lt;amount&gt;
 */
public final class TooAdminLevelCommand extends AbstractPlayerCommand {
    private final TrailOfOrbis plugin;
    private final RequiredArg<String> operationArg;
    private final RequiredArg<String> targetArg;
    private final RequiredArg<Integer> amountArg;

    public TooAdminLevelCommand(TrailOfOrbis plugin) {
        super("level", "Manage player levels");
        this.plugin = plugin;

        operationArg = this.withRequiredArg("operation", "add/remove/set/check", ArgTypes.STRING);
        targetArg = this.withRequiredArg("player", "Target player name", ArgTypes.STRING);
        amountArg = this.withRequiredArg("amount", "Level amount (not needed for check)", ArgTypes.INTEGER);
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
        int currentLevel = levelingService.getLevel(targetUuid);
        long currentXp = levelingService.getXp(targetUuid);
        int maxLevel = levelingService.getMaxLevel();

        switch (operation.toLowerCase()) {
            case "check" -> {
                long xpToNext = levelingService.getXpToNextLevel(targetUuid);
                float progress = levelingService.getLevelProgress(targetUuid);
                int progressPercent = Math.round(progress * 100);

                sender.sendMessage(Message.empty()
                    .insert(Message.raw("[TooAdmin] ").color(MessageColors.GOLD))
                    .insert(Message.raw(targetName).color(MessageColors.WHITE))
                    .insert(Message.raw("'s Level Info:").color(MessageColors.GRAY)));

                sender.sendMessage(Message.empty()
                    .insert(Message.raw("  Level : ").color(MessageColors.GRAY))
                    .insert(Message.raw(String.valueOf(currentLevel)).color(MessageColors.WHITE))
                    .insert(Message.raw(" / " + maxLevel).color(MessageColors.GRAY)));

                sender.sendMessage(Message.empty()
                    .insert(Message.raw("  XP : ").color(MessageColors.GRAY))
                    .insert(Message.raw(String.valueOf(currentXp)).color(MessageColors.WHITE)));

                if (currentLevel < maxLevel) {
                    sender.sendMessage(Message.empty()
                        .insert(Message.raw("  XP to next : ").color(MessageColors.GRAY))
                        .insert(Message.raw(String.valueOf(xpToNext)).color(MessageColors.WHITE))
                        .insert(Message.raw(" (" + progressPercent + "%)").color(MessageColors.GRAY)));
                } else {
                    sender.sendMessage(Message.empty()
                        .insert(Message.raw("  ").color(MessageColors.GRAY))
                        .insert(Message.raw("MAX LEVEL").color(MessageColors.GOLD)));
                }
                return;
            }
            case "add" -> {
                if (amount == null || amount < 1) {
                    sender.sendMessage(Message.raw("Amount must be >= 1 !").color(MessageColors.ERROR));
                    return;
                }
                int newLevel = currentLevel + amount;
                levelingService.setLevel(targetUuid, newLevel);
            }
            case "remove" -> {
                if (amount == null || amount < 1) {
                    sender.sendMessage(Message.raw("Amount must be >= 1 !").color(MessageColors.ERROR));
                    return;
                }
                int newLevel = Math.max(currentLevel - amount, 1);
                if (newLevel == currentLevel && currentLevel == 1) {
                    sender.sendMessage(Message.raw("Player is already at level 1 !").color(MessageColors.ERROR));
                    return;
                }
                levelingService.setLevel(targetUuid, newLevel);
            }
            case "set" -> {
                if (amount == null || amount < 1) {
                    sender.sendMessage(Message.raw("Level must be >= 1 !").color(MessageColors.ERROR));
                    return;
                }
                levelingService.setLevel(targetUuid, amount);
            }
            default -> {
                sender.sendMessage(Message.raw("Invalid operation! Use : add, remove, set, check").color(MessageColors.ERROR));
                return;
            }
        }

        int newLevel = levelingService.getLevel(targetUuid);
        long newXp = levelingService.getXp(targetUuid);

        sender.sendMessage(Message.empty()
            .insert(Message.raw("[TooAdmin] ").color(MessageColors.GOLD))
            .insert(Message.raw("Set ").color(MessageColors.SUCCESS))
            .insert(Message.raw(targetName).color(MessageColors.WHITE))
            .insert(Message.raw("'s level to ").color(MessageColors.SUCCESS))
            .insert(Message.raw(String.valueOf(newLevel)).color(MessageColors.WHITE))
            .insert(Message.raw(" (XP : " + newXp + ")").color(MessageColors.GRAY)));

        if (newLevel != currentLevel) {
            sender.sendMessage(Message.empty()
                .insert(Message.raw("  Level changed : ").color(MessageColors.GRAY))
                .insert(Message.raw(String.valueOf(currentLevel)).color(MessageColors.WHITE))
                .insert(Message.raw(" -> ").color(MessageColors.GRAY))
                .insert(Message.raw(String.valueOf(newLevel)).color(MessageColors.WHITE)));
        }

        AdminCommandHelper.logAdminAction(plugin, sender, "LEVEL " + operation.toUpperCase(), targetName, operation, amount != null ? amount : 0);
    }
}
