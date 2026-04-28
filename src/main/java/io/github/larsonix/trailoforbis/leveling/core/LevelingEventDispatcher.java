package io.github.larsonix.trailoforbis.leveling.core;

import com.hypixel.hytale.logger.HytaleLogger;
import java.util.logging.Level;
import io.github.larsonix.trailoforbis.leveling.api.LevelingEvents;
import io.github.larsonix.trailoforbis.leveling.xp.XpSource;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe dispatcher for leveling events.
 *
 * <p>Uses {@link CopyOnWriteArrayList} for thread-safe iteration during event dispatch.
 * This is optimized for frequent reads (event dispatch) and infrequent writes (listener registration).
 *
 * <p>All events are dispatched synchronously on the calling thread.
 */
public class LevelingEventDispatcher {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final List<LevelingEvents.LevelUpListener> levelUpListeners = new CopyOnWriteArrayList<>();
    private final List<LevelingEvents.LevelDownListener> levelDownListeners = new CopyOnWriteArrayList<>();
    private final List<LevelingEvents.XpGainListener> xpGainListeners = new CopyOnWriteArrayList<>();
    private final List<LevelingEvents.XpLossListener> xpLossListeners = new CopyOnWriteArrayList<>();

    // ═══════════════════════════════════════════════════════════════════
    // REGISTRATION
    // ═══════════════════════════════════════════════════════════════════

    public void registerLevelUpListener(@Nonnull LevelingEvents.LevelUpListener listener) {
        if (listener != null && !levelUpListeners.contains(listener)) {
            levelUpListeners.add(listener);
        }
    }

    public void registerLevelDownListener(@Nonnull LevelingEvents.LevelDownListener listener) {
        if (listener != null && !levelDownListeners.contains(listener)) {
            levelDownListeners.add(listener);
        }
    }

    public void registerXpGainListener(@Nonnull LevelingEvents.XpGainListener listener) {
        if (listener != null && !xpGainListeners.contains(listener)) {
            xpGainListeners.add(listener);
        }
    }

    public void registerXpLossListener(@Nonnull LevelingEvents.XpLossListener listener) {
        if (listener != null && !xpLossListeners.contains(listener)) {
            xpLossListeners.add(listener);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // UNREGISTRATION
    // ═══════════════════════════════════════════════════════════════════

    public void unregisterLevelUpListener(@Nonnull LevelingEvents.LevelUpListener listener) {
        if (listener != null) {
            levelUpListeners.remove(listener);
        }
    }

    public void unregisterLevelDownListener(@Nonnull LevelingEvents.LevelDownListener listener) {
        if (listener != null) {
            levelDownListeners.remove(listener);
        }
    }

    public void unregisterXpGainListener(@Nonnull LevelingEvents.XpGainListener listener) {
        if (listener != null) {
            xpGainListeners.remove(listener);
        }
    }

    public void unregisterXpLossListener(@Nonnull LevelingEvents.XpLossListener listener) {
        if (listener != null) {
            xpLossListeners.remove(listener);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // EVENT DISPATCH
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Dispatches a level-up event to all registered listeners.
     *
     * @param playerId The player's UUID
     * @param newLevel The new level
     * @param oldLevel The previous level
     * @param totalXp The player's current total XP
     */
    public void dispatchLevelUp(@Nonnull UUID playerId, int newLevel, int oldLevel, long totalXp) {
        for (LevelingEvents.LevelUpListener listener : levelUpListeners) {
            try {
                listener.onLevelUp(playerId, newLevel, oldLevel, totalXp);
            } catch (Exception e) {
                LOGGER.at(Level.SEVERE).withCause(e).log("Error in LevelUpListener");
            }
        }
    }

    /**
     * Dispatches a level-down event to all registered listeners.
     *
     * @param playerId The player's UUID
     * @param newLevel The new level
     * @param oldLevel The previous level
     * @param totalXp The player's current total XP
     */
    public void dispatchLevelDown(@Nonnull UUID playerId, int newLevel, int oldLevel, long totalXp) {
        for (LevelingEvents.LevelDownListener listener : levelDownListeners) {
            try {
                listener.onLevelDown(playerId, newLevel, oldLevel, totalXp);
            } catch (Exception e) {
                LOGGER.at(Level.SEVERE).withCause(e).log("Error in LevelDownListener");
            }
        }
    }

    /**
     * Dispatches an XP gain event to all registered listeners.
     *
     * @param playerId The player's UUID
     * @param amount The amount of XP gained
     * @param source The source of the XP gain
     * @param totalXp The player's new total XP
     */
    public void dispatchXpGain(@Nonnull UUID playerId, long amount, @Nonnull XpSource source, long totalXp) {
        for (LevelingEvents.XpGainListener listener : xpGainListeners) {
            try {
                listener.onXpGain(playerId, amount, source, totalXp);
            } catch (Exception e) {
                LOGGER.at(Level.SEVERE).withCause(e).log("Error in XpGainListener");
            }
        }
    }

    /**
     * Dispatches an XP loss event to all registered listeners.
     *
     * @param playerId The player's UUID
     * @param amount The amount of XP lost
     * @param source The source of the XP loss
     * @param totalXp The player's new total XP
     */
    public void dispatchXpLoss(@Nonnull UUID playerId, long amount, @Nonnull XpSource source, long totalXp) {
        for (LevelingEvents.XpLossListener listener : xpLossListeners) {
            try {
                listener.onXpLoss(playerId, amount, source, totalXp);
            } catch (Exception e) {
                LOGGER.at(Level.SEVERE).withCause(e).log("Error in XpLossListener");
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // UTILITIES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Clears all registered listeners.
     *
     * <p>Called during plugin shutdown.
     */
    public void clearAll() {
        levelUpListeners.clear();
        levelDownListeners.clear();
        xpGainListeners.clear();
        xpLossListeners.clear();
    }

    /**
     * Returns true if any listeners are registered.
     *
     * @return true if at least one listener is registered
     */
    public boolean hasListeners() {
        return !levelUpListeners.isEmpty()
            || !levelDownListeners.isEmpty()
            || !xpGainListeners.isEmpty()
            || !xpLossListeners.isEmpty();
    }
}
