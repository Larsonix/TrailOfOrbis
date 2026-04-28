package io.github.larsonix.trailoforbis.commands.too.attr;

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
import io.github.larsonix.trailoforbis.api.services.AttributeService;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.systems.StatsApplicationSystem;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Resets all attributes to zero and refunds all points.
 *
 * <p>Usage: /too attr reset
 */
public final class TooAttrResetCommand extends OpenPlayerCommand {
    private final TrailOfOrbis plugin;

    public TooAttrResetCommand(TrailOfOrbis plugin) {
        super("reset", "Reset all your attributes");
        this.addAliases("clear", "respec");
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
        UUID uuid = player.getUuid();
        AttributeService service = ServiceRegistry.require(AttributeService.class);

        // Show cost preview before attempting
        int totalAllocated = ServiceRegistry.require(AttributeService.class)
            .getPlayerDataRepository().get(uuid)
            .map(data -> data.getTotalAllocatedPoints())
            .orElse(0);
        int respecCost = (int) Math.ceil(totalAllocated * 0.5);
        int currentRefundPoints = service.getAttributeRefundPoints(uuid);

        int refunded = service.resetAllAttributes(uuid);

        if (refunded == -1) {
            player.sendMessage(Message.raw("Failed to reset attributes !").color(MessageColors.ERROR));
            return;
        }

        if (refunded == -2) {
            player.sendMessage(Message.empty()
                .insert(Message.raw("Not enough refund points! ").color(MessageColors.ERROR))
                .insert(Message.raw("Need ").color(MessageColors.WARNING))
                .insert(Message.raw(String.valueOf(respecCost)).color(MessageColors.WHITE))
                .insert(Message.raw(" but you have ").color(MessageColors.WARNING))
                .insert(Message.raw(String.valueOf(currentRefundPoints)).color(MessageColors.WHITE))
                .insert(Message.raw(". Use Orbs of Realignment to gain more.").color(MessageColors.WARNING)));
            return;
        }

        if (refunded == 0) {
            player.sendMessage(Message.raw("No points to reset - all attributes are already at 0 !")
                .color(MessageColors.WARNING));
            return;
        }

        // Recalculate and apply ECS stats
        ComputedStats stats = service.recalculateStats(uuid);
        if (stats != null) {
            StatsApplicationSystem.applyAllStatsAndSync(
                player, store, ref, stats,
                service.getPlayerDataRepository(), uuid
            );
        }

        player.sendMessage(Message.empty()
            .insert(Message.raw("Reset all attributes! ").color(MessageColors.SUCCESS))
            .insert(Message.raw(String.valueOf(refunded)).color(MessageColors.WHITE))
            .insert(Message.raw(" points returned (cost ").color(MessageColors.SUCCESS))
            .insert(Message.raw(String.valueOf(respecCost)).color(MessageColors.WHITE))
            .insert(Message.raw(" refund points).").color(MessageColors.SUCCESS)));

        plugin.getLogger().atInfo().log("[AttrReset] %s reset all attributes, refunded %d points, cost %d refund points",
            player.getUsername(), refunded, respecCost);
    }
}
