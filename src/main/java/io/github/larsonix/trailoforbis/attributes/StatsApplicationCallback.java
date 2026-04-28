package io.github.larsonix.trailoforbis.attributes;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Callback for applying calculated stats to the ECS.
 *
 * <p>This bridges stat calculation (AttributeManager) and ECS application (StatsApplicationSystem).
 * When registered with AttributeManager, the callback is invoked after every stat recalculation,
 * ensuring that computed stats are always pushed to Hytale's Entity Component System.
 *
 * <p><b>Why this exists:</b> AttributeManager handles the data layer (stat calculation),
 * but doesn't have access to ECS components. StatsApplicationSystem handles the ECS layer
 * but isn't directly called from AttributeManager. This callback bridges the two.
 *
 * <p><b>Thread Safety:</b> The callback implementation should handle thread safety
 * (typically by using world.execute() for ECS operations).
 */
@FunctionalInterface
public interface StatsApplicationCallback {

    /**
     * Applies the recalculated stats to the player's entity.
     *
     * <p>Called by AttributeManager after each successful stat recalculation.
     * The implementation should:
     * <ol>
     *   <li>Resolve the player's current world and entity reference</li>
     *   <li>Get the computed stats from AttributeManager</li>
     *   <li>Apply stats to ECS via StatsApplicationSystem (on world thread)</li>
     * </ol>
     *
     * <p>If the player is offline or their entity is not valid, the implementation
     * should gracefully return without throwing.
     *
     * @param playerId The UUID of the player whose stats were recalculated
     */
    void applyStatsToEntity(@Nonnull UUID playerId);
}
