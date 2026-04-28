package io.github.larsonix.trailoforbis.commands.tooadmin;

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
import io.github.larsonix.trailoforbis.guide.GuideManager;
import io.github.larsonix.trailoforbis.guide.GuideMilestone;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Admin command for guide milestone management.
 *
 * <p>Usage:
 * <ul>
 *   <li>{@code /tooadmin guide reset} — reset all milestones for yourself</li>
 *   <li>{@code /tooadmin guide reset <milestone>} — reset a specific milestone</li>
 *   <li>{@code /tooadmin guide trigger <milestone>} — force-show a milestone popup</li>
 *   <li>{@code /tooadmin guide list} — list all milestones and their completion status</li>
 * </ul>
 */
public final class TooAdminGuideCommand extends AbstractPlayerCommand {
    private final TrailOfOrbis plugin;
    private final RequiredArg<String> operationArg;
    private final OptionalArg<String> milestoneArg;

    public TooAdminGuideCommand(TrailOfOrbis plugin) {
        super("guide", "Manage guide milestones");
        this.plugin = plugin;

        operationArg = this.withRequiredArg("operation", "reset/trigger/list", ArgTypes.STRING);
        milestoneArg = this.withOptionalArg("milestone", "Milestone ID (e.g. first_gear, welcome)", ArgTypes.STRING);
    }

    @Override
    protected void execute(
        @Nonnull CommandContext context,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef player,
        @Nonnull World world
    ) {
        GuideManager guide = plugin.getGuideManager();
        if (guide == null) {
            player.sendMessage(Message.raw("Guide system not initialized!").color(MessageColors.ERROR));
            return;
        }

        String op = operationArg.get(context);
        UUID playerId = player.getUuid();

        switch (op.toLowerCase()) {
            case "reset" -> handleReset(context, player, guide, playerId);
            case "trigger" -> handleTrigger(context, player, guide, playerId, store);
            case "list" -> handleList(player, guide, playerId);
            default -> player.sendMessage(Message.raw("Usage: /tooadmin guide <reset|trigger|list> [milestone]")
                .color(MessageColors.WARNING));
        }
    }

    private void handleReset(CommandContext context, PlayerRef player, GuideManager guide, UUID playerId) {
        String milestoneId = milestoneArg.get(context);

        if (milestoneId == null || milestoneId.isEmpty()) {
            // Reset all milestones — reload player state from scratch
            guide.onPlayerDisconnect(playerId);
            // Clear the DB entries by re-loading (they'll be gone after we delete them)
            for (GuideMilestone m : GuideMilestone.values()) {
                guide.resetMilestone(playerId, m.getId());
            }
            guide.onPlayerJoin(playerId);
            player.sendMessage(Message.raw("[Guide] Reset all milestones. Popups will re-trigger.")
                .color(MessageColors.SUCCESS));
        } else {
            GuideMilestone milestone = findMilestone(milestoneId);
            if (milestone == null) {
                player.sendMessage(Message.raw("[Guide] Unknown milestone: " + milestoneId + ". Use /tooadmin guide list")
                    .color(MessageColors.ERROR));
                return;
            }
            guide.resetMilestone(playerId, milestone.getId());
            // Reload state
            guide.onPlayerDisconnect(playerId);
            guide.onPlayerJoin(playerId);
            player.sendMessage(Message.raw("[Guide] Reset milestone: " + milestone.getId())
                .color(MessageColors.SUCCESS));
        }
    }

    private void handleTrigger(CommandContext context, PlayerRef player, GuideManager guide, UUID playerId, Store<EntityStore> store) {
        String milestoneId = milestoneArg.get(context);

        if (milestoneId == null || milestoneId.isEmpty()) {
            player.sendMessage(Message.raw("Usage: /tooadmin guide trigger <milestone_id>")
                .color(MessageColors.WARNING));
            return;
        }

        GuideMilestone milestone = findMilestone(milestoneId);
        if (milestone == null) {
            player.sendMessage(Message.raw("[Guide] Unknown milestone: " + milestoneId)
                .color(MessageColors.ERROR));
            return;
        }

        // Reset first so it can re-trigger
        guide.resetMilestone(playerId, milestone.getId());
        guide.onPlayerDisconnect(playerId);
        guide.onPlayerJoin(playerId);

        // Force show (bypasses combat check)
        guide.tryShow(playerId, milestone);
        player.sendMessage(Message.raw("[Guide] Triggered: " + milestone.getId())
            .color(MessageColors.SUCCESS));
    }

    private void handleList(PlayerRef player, GuideManager guide, UUID playerId) {
        player.sendMessage(Message.raw("=== Guide Milestones ===").color(MessageColors.GOLD));

        for (GuideMilestone m : GuideMilestone.values()) {
            boolean completed = guide.isCompleted(playerId, m.getId());
            String status = completed ? "DONE" : "pending";
            String statusColor = completed ? MessageColors.SUCCESS : MessageColors.WARNING;

            player.sendMessage(Message.empty()
                .insert(Message.raw("  " + m.getId()).color(MessageColors.WHITE))
                .insert(Message.raw(" [" + status + "] ").color(statusColor))
                .insert(Message.raw(m.getTitle()).color(MessageColors.GRAY)));
        }
    }

    private GuideMilestone findMilestone(String id) {
        for (GuideMilestone m : GuideMilestone.values()) {
            if (m.getId().equalsIgnoreCase(id) || m.name().equalsIgnoreCase(id)) {
                return m;
            }
        }
        return null;
    }
}
