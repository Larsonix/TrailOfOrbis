package io.github.larsonix.trailoforbis.commands.too.skilltree;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.api.services.SkillTreeMapService;

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Shared helpers for skill tree commands.
 */
public final class SkillTreeCommandHelper {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private SkillTreeCommandHelper() {}

    public static void refreshMapIfViewing(PlayerRef player, UUID playerId) {
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
