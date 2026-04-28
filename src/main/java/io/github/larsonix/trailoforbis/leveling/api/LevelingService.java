package io.github.larsonix.trailoforbis.leveling.api;

import io.github.larsonix.trailoforbis.leveling.xp.XpSource;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Service interface for player leveling operations.
 *
 * <p>Provides a clean API for XP/level management, separating the interface
 * from implementation. Access via {@link io.github.larsonix.trailoforbis.api.ServiceRegistry}.
 *
 * <p>Usage:
 * <pre>
 * LevelingService service = ServiceRegistry.require(LevelingService.class);
 *
 * // Query player level
 * int level = service.getLevel(playerId);
 * long xp = service.getXp(playerId);
 *
 * // Grant XP from mob kill
 * service.addXp(playerId, 150, XpSource.MOB_KILL);
 *
 * // Check progress
 * float progress = service.getLevelProgress(playerId); // 0.0-1.0
 * </pre>
 *
 * <p>Thread safety: All implementations must be thread-safe.
 */
public interface LevelingService {

    // ═══════════════════════════════════════════════════════════════════
    // XP OPERATIONS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets a player's current XP.
     *
     * @param playerId The player's UUID
     * @return The player's XP, or 0 if not found
     */
    long getXp(@Nonnull UUID playerId);

    /**
     * Adds XP to a player.
     *
     * <p>Triggers {@link LevelingEvents.XpGainListener} callbacks.
     * May trigger {@link LevelingEvents.LevelUpListener} if level threshold is crossed.
     *
     * @param playerId The player's UUID
     * @param amount The XP to add (must be positive)
     * @param source The source of the XP gain
     * @throws IllegalArgumentException if amount is negative
     */
    void addXp(@Nonnull UUID playerId, long amount, @Nonnull XpSource source);

    /**
     * Removes XP from a player.
     *
     * <p>Triggers {@link LevelingEvents.XpLossListener} callbacks.
     * May trigger {@link LevelingEvents.LevelDownListener} if level threshold is crossed.
     * Will not reduce XP below 0.
     *
     * @param playerId The player's UUID
     * @param amount The XP to remove (must be positive)
     * @param source The source of the XP loss
     * @throws IllegalArgumentException if amount is negative
     */
    void removeXp(@Nonnull UUID playerId, long amount, @Nonnull XpSource source);

    /**
     * Sets a player's XP to an exact value.
     *
     * <p>Bypasses normal XP gain/loss events. Use for admin commands only.
     *
     * @param playerId The player's UUID
     * @param amount The new XP value (will be clamped to >= 0)
     */
    void setXp(@Nonnull UUID playerId, long amount);

    // ═══════════════════════════════════════════════════════════════════
    // LEVEL OPERATIONS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets a player's current level.
     *
     * <p>Level is derived from XP using the configured formula.
     *
     * @param playerId The player's UUID
     * @return The player's level (1+), or 1 if not found
     */
    int getLevel(@Nonnull UUID playerId);

    /**
     * Sets a player's level directly.
     *
     * <p>Adjusts XP to match the new level's threshold.
     * Use for admin commands only.
     *
     * @param playerId The player's UUID
     * @param level The new level (clamped to 1-maxLevel)
     */
    void setLevel(@Nonnull UUID playerId, int level);

    // ═══════════════════════════════════════════════════════════════════
    // FORMULA QUERIES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the total XP required to reach a specific level.
     *
     * @param level The target level (1+)
     * @return The cumulative XP required for that level
     */
    long getXpForLevel(int level);

    /**
     * Gets the XP needed to reach the next level.
     *
     * @param playerId The player's UUID
     * @return XP remaining until next level, or 0 if at max level
     */
    long getXpToNextLevel(@Nonnull UUID playerId);

    /**
     * Gets the progress toward the next level as a percentage.
     *
     * @param playerId The player's UUID
     * @return Progress between 0.0 (just leveled) and 1.0 (about to level)
     */
    float getLevelProgress(@Nonnull UUID playerId);

    /** Gets the maximum level allowed by the formula. */
    int getMaxLevel();

    // ═══════════════════════════════════════════════════════════════════
    // EVENT REGISTRATION
    // ═══════════════════════════════════════════════════════════════════

    void registerLevelUpListener(@Nonnull LevelingEvents.LevelUpListener listener);

    void registerLevelDownListener(@Nonnull LevelingEvents.LevelDownListener listener);

    void registerXpGainListener(@Nonnull LevelingEvents.XpGainListener listener);

    void registerXpLossListener(@Nonnull LevelingEvents.XpLossListener listener);

    void unregisterLevelUpListener(@Nonnull LevelingEvents.LevelUpListener listener);

    void unregisterLevelDownListener(@Nonnull LevelingEvents.LevelDownListener listener);

    void unregisterXpGainListener(@Nonnull LevelingEvents.XpGainListener listener);

    void unregisterXpLossListener(@Nonnull LevelingEvents.XpLossListener listener);
}
