package io.github.larsonix.trailoforbis.leveling.api;

import io.github.larsonix.trailoforbis.leveling.xp.XpSource;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Event listener interfaces for the leveling system.
 *
 * <p>Register listeners via {@link LevelingService}:
 * <pre>
 * levelingService.registerLevelUpListener((playerId, newLevel, oldLevel, xp) -> {
 *     int levelsGained = newLevel - oldLevel;
 *     grantSkillPoints(playerId, levelsGained * pointsPerLevel);
 * });
 * </pre>
 *
 * <p>All callbacks are invoked synchronously on the main thread.
 */
public final class LevelingEvents {

    private LevelingEvents() {
        // Static class - prevent instantiation
    }

    /**
     * Listener for level-up events.
     *
     * <p>Called when a player's level increases (through XP gain or admin command).
     */
    @FunctionalInterface
    public interface LevelUpListener {
        /**
         * Called when a player levels up.
         *
         * @param playerId The player's UUID
         * @param newLevel The new level
         * @param oldLevel The previous level
         * @param totalXp The player's current total XP
         */
        void onLevelUp(@Nonnull UUID playerId, int newLevel, int oldLevel, long totalXp);
    }

    /**
     * Listener for level-down events.
     *
     * <p>Called when a player's level decreases (through XP loss or admin command).
     */
    @FunctionalInterface
    public interface LevelDownListener {
        /**
         * Called when a player levels down.
         *
         * @param playerId The player's UUID
         * @param newLevel The new level
         * @param oldLevel The previous level
         * @param totalXp The player's current total XP
         */
        void onLevelDown(@Nonnull UUID playerId, int newLevel, int oldLevel, long totalXp);
    }

    /**
     * Listener for XP gain events.
     *
     * <p>Called whenever XP is added to a player.
     */
    @FunctionalInterface
    public interface XpGainListener {
        /**
         * Called when a player gains XP.
         *
         * @param playerId The player's UUID
         * @param amount The amount of XP gained
         * @param source The source of the XP gain
         * @param totalXp The player's new total XP
         */
        void onXpGain(@Nonnull UUID playerId, long amount, @Nonnull XpSource source, long totalXp);
    }

    /**
     * Listener for XP loss events.
     *
     * <p>Called whenever XP is removed from a player.
     */
    @FunctionalInterface
    public interface XpLossListener {
        /**
         * Called when a player loses XP.
         *
         * @param playerId The player's UUID
         * @param amount The amount of XP lost
         * @param source The source of the XP loss
         * @param totalXp The player's new total XP
         */
        void onXpLoss(@Nonnull UUID playerId, long amount, @Nonnull XpSource source, long totalXp);
    }
}
