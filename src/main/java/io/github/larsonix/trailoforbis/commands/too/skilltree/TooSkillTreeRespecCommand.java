package io.github.larsonix.trailoforbis.commands.too.skilltree;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import io.github.larsonix.trailoforbis.commands.base.OpenPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.api.services.SkillTreeMapService;
import io.github.larsonix.trailoforbis.api.services.SkillTreeService;
import io.github.larsonix.trailoforbis.skilltree.model.SkillTreeData;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Performs a full skill tree respec.
 *
 * <p>Usage: /too skilltree respec
 */
public final class TooSkillTreeRespecCommand extends OpenPlayerCommand {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @SuppressWarnings("unused")
    private final TrailOfOrbis plugin;

    public TooSkillTreeRespecCommand(TrailOfOrbis plugin) {
        super("respec", "Full skill tree respec");
        this.addAliases("reset", "clear");
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
        int freeRemaining = service.getFreeRespecsRemaining(playerId);

        if (freeRemaining <= 0) {
            player.sendMessage(Message.raw("No free respecs remaining !").color(MessageColors.ERROR));
            return;
        }

        SkillTreeData dataBefore = service.getSkillTreeData(playerId);
        int nodesRefunded = dataBefore.getAllocatedNodes().size();

        if (nodesRefunded == 0) {
            player.sendMessage(Message.raw("You have no allocated nodes to respec.").color(MessageColors.WARNING));
            return;
        }

        if (service.fullRespec(playerId)) {
            int pointsNow = service.getAvailablePoints(playerId);
            int freeAfter = service.getFreeRespecsRemaining(playerId);

            player.sendMessage(Message.empty()
                .insert(Message.raw("Full respec complete!\n").color(MessageColors.SUCCESS))
                .insert(Message.raw("Refunded " + nodesRefunded + " nodes.\n").color(MessageColors.INFO))
                .insert(Message.raw("Available points : " + pointsNow + "\n").color(MessageColors.WHITE))
                .insert(Message.raw("Free respecs remaining : " + freeAfter).color(MessageColors.WARNING)));

            // Refresh map if viewing
            refreshMapIfViewing(player, playerId);
        } else {
            player.sendMessage(Message.raw("Respec failed.").color(MessageColors.ERROR));
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
