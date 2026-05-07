package io.github.larsonix.trailoforbis.combat.effects;

import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.api.services.SkillTreeService;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Base class for keystone combat effects that activate when a specific skill tree node is allocated.
 *
 * <p>Instead of requiring a new ComputedStats field for each keystone, this base class checks
 * if the player has allocated the keystone node ID. This avoids boilerplate for effects that
 * don't need a stat value — just presence/absence detection.
 *
 * <p>Subclasses provide their keystone node ID via {@link #getKeystoneNodeId()}.
 */
public abstract class KeystoneCombatEffect implements CombatEffect {

    private final String keystoneNodeId;

    protected KeystoneCombatEffect(@Nonnull String keystoneNodeId) {
        this.keystoneNodeId = keystoneNodeId;
    }

    @Override
    public boolean isActive(@Nonnull UUID playerId, @Nonnull ComputedStats stats) {
        return ServiceRegistry.get(SkillTreeService.class)
            .map(svc -> svc.getAllocatedNodes(playerId).contains(keystoneNodeId))
            .orElse(false);
    }

    /**
     * Returns the skill tree node ID of the keystone this effect is associated with.
     */
    @Nonnull
    public String getKeystoneNodeId() {
        return keystoneNodeId;
    }
}
