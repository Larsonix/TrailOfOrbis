package io.github.larsonix.trailoforbis.skilltree.conditional;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Callback interface for visual feedback when conditional effects activate/deactivate.
 *
 * <p>Implemented by {@link ConditionalVisualEffectManager} to show/hide per-node
 * status effect icons on the player's HUD when skill tree conditional buffs are active.
 */
public interface ConditionalVisualCallback {

    /**
     * Called when a conditional effect is activated or refreshed.
     *
     * @param playerId        The player's UUID
     * @param nodeId          The skill node ID (maps to a unique registered effect)
     * @param durationSeconds Effect duration in seconds (for countdown ring), or 0 for persistent
     */
    void onActivate(@Nonnull UUID playerId, @Nonnull String nodeId, float durationSeconds);

    /**
     * Called when a conditional effect is deactivated (expired, consumed, or manually removed).
     *
     * @param playerId The player's UUID
     * @param nodeId   The skill node ID
     */
    void onDeactivate(@Nonnull UUID playerId, @Nonnull String nodeId);
}
