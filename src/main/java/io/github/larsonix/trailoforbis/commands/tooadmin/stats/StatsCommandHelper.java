package io.github.larsonix.trailoforbis.commands.tooadmin.stats;

import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.api.services.AttributeService;
import io.github.larsonix.trailoforbis.combat.attackspeed.AnimationSpeedSyncManager;
import io.github.larsonix.trailoforbis.commands.admin.AdminCommandHelper;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.UUID;

/**
 * Shared utilities for debug stat commands.
 */
final class StatsCommandHelper {

    private StatsCommandHelper() {}

    /**
     * Resolves a player's UUID by username.
     */
    @Nonnull
    static Optional<UUID> resolvePlayerUuid(@Nonnull String username, @Nonnull TrailOfOrbis plugin) {
        AttributeService service = ServiceRegistry.get(AttributeService.class).orElse(null);
        if (service == null) return Optional.empty();
        return AdminCommandHelper.resolvePlayerUuid(username, service.getPlayerDataRepository());
    }

    /**
     * Triggers a stat recalculation so overrides are applied immediately,
     * including attack speed animation sync.
     */
    static void recalculateAndNotify(@Nonnull UUID playerId, @Nonnull TrailOfOrbis plugin) {
        ServiceRegistry.get(AttributeService.class).ifPresent(service -> {
            service.recalculateStats(playerId);
        });
        ServiceRegistry.get(AnimationSpeedSyncManager.class)
                .ifPresent(m -> m.syncAnimationSpeed(playerId));
    }
}
