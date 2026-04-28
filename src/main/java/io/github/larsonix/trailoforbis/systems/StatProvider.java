package io.github.larsonix.trailoforbis.systems;

import io.github.larsonix.trailoforbis.attributes.BaseStats;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Interface for retrieving base stats for a player.
 * Allows abstraction of the Hytale server API for testing.
 */
public interface StatProvider {
    /**
     * Gets the base stats for the specified player.
     * Should return defaults if player is not found or unavailable.
     *
     * @param playerId The player's UUID
     * @return The base stats (never null)
     */
    @Nonnull
    BaseStats getBaseStats(@Nonnull UUID playerId);
}
