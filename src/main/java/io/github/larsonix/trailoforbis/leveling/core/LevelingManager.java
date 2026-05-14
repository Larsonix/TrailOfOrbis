package io.github.larsonix.trailoforbis.leveling.core;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.leveling.api.LevelingEvents;
import io.github.larsonix.trailoforbis.leveling.api.LevelingService;
import io.github.larsonix.trailoforbis.leveling.config.LevelingConfig;
import io.github.larsonix.trailoforbis.leveling.formula.LevelFormula;
import io.github.larsonix.trailoforbis.leveling.repository.LevelingRepository;
import io.github.larsonix.trailoforbis.leveling.xp.XpSource;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

/**
 * Main implementation of the leveling service.
 *
 * <p>Manages XP/level operations with thread-safe per-player locking.
 * Uses the repository for persistence and the formula for level calculations.
 *
 * <p>Thread safety:
 * <ul>
 *   <li>Per-player locks prevent race conditions on XP modifications</li>
 *   <li>Event dispatcher uses CopyOnWriteArrayList for safe iteration</li>
 *   <li>Repository uses ConcurrentHashMap for cache</li>
 * </ul>
 */
public class LevelingManager implements LevelingService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final LevelingRepository repository;
    private final LevelFormula formula;
    private final @Nullable LevelFormula previousFormula;
    private final LevelingConfig config;
    private final LevelingEventDispatcher eventDispatcher;

    // Per-player locks to prevent race conditions
    private final Map<UUID, ReentrantLock> playerLocks = new ConcurrentHashMap<>();

    // Players whose XP was adjusted during level protection (for notifications)
    private final Set<UUID> adjustedPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * Creates a new LevelingManager.
     *
     * @param repository      The leveling data repository
     * @param formula         The current XP-to-level formula
     * @param previousFormula The previous formula for one-time bootstrap migration (nullable)
     * @param config          The leveling configuration
     */
    public LevelingManager(
        @Nonnull LevelingRepository repository,
        @Nonnull LevelFormula formula,
        @Nullable LevelFormula previousFormula,
        @Nonnull LevelingConfig config
    ) {
        this.repository = repository;
        this.formula = formula;
        this.previousFormula = previousFormula;
        this.config = config;
        this.eventDispatcher = new LevelingEventDispatcher();
    }

    // ═══════════════════════════════════════════════════════════════════
    // PLAYER LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════

    /** Loads or creates player data. Call on player join. Runs level protection migration. */
    @Nonnull
    public PlayerLevelData loadPlayer(@Nonnull UUID playerId) {
        ReentrantLock lock = getPlayerLock(playerId);
        lock.lock();
        try {
            PlayerLevelData data = repository.loadOrCreate(playerId);
            return protectLevel(data);
        } finally {
            lock.unlock();
        }
    }

    /** Unloads player data from cache. Call on player quit. */
    public void unloadPlayer(@Nonnull UUID playerId) {
        // Save synchronously before evicting to prevent data loss on quick reconnect.
        // Async save creates a race condition: if the player reconnects before the
        // executor processes the write, loadFromDatabase() reads stale XP and the
        // player's level regresses while keeping points granted at the higher level.
        // A single UPDATE is fast enough even with 50+ concurrent disconnects.
        PlayerLevelData data = repository.get(playerId);
        if (data != null) {
            repository.saveSync(data);
        }
        repository.evict(playerId);
        playerLocks.remove(playerId);
        adjustedPlayers.remove(playerId);
    }

    // ═══════════════════════════════════════════════════════════════════
    // XP OPERATIONS (LevelingService interface)
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public long getXp(@Nonnull UUID playerId) {
        PlayerLevelData data = repository.get(playerId);
        return data != null ? data.xp() : 0;
    }

    @Override
    public void addXp(@Nonnull UUID playerId, long amount, @Nonnull XpSource source) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must be positive: " + amount);
        }
        if (amount == 0) {
            return;
        }

        ReentrantLock lock = getPlayerLock(playerId);
        lock.lock();
        try {
            PlayerLevelData data = repository.getOrDefault(playerId);
            int oldLevel = formula.getLevelForXp(data.xp());

            // Update XP and stamp level
            PlayerLevelData newData = data.withXpDelta(amount);
            int newLevel = formula.getLevelForXp(newData.xp());
            newData = newData.withStoredLevel(newLevel);
            repository.save(newData);

            // Fire XP gain event
            eventDispatcher.dispatchXpGain(playerId, amount, source, newData.xp());

            // Fire level-up event if level increased
            if (newLevel > oldLevel) {
                eventDispatcher.dispatchLevelUp(playerId, newLevel, oldLevel, newData.xp());
            }

        } finally {
            lock.unlock();
        }
    }

    @Override
    public void removeXp(@Nonnull UUID playerId, long amount, @Nonnull XpSource source) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must be positive: " + amount);
        }
        if (amount == 0) {
            return;
        }

        ReentrantLock lock = getPlayerLock(playerId);
        lock.lock();
        try {
            PlayerLevelData data = repository.getOrDefault(playerId);
            int oldLevel = formula.getLevelForXp(data.xp());

            // Calculate actual loss (can't go below 0)
            long actualLoss = Math.min(amount, data.xp());

            // Defense-in-depth: death penalty can never drop a player below their current level
            if (source.isLossSource()) {
                long levelFloor = formula.getXpForLevel(oldLevel);
                long maxLoss = Math.max(0, data.xp() - levelFloor);
                actualLoss = Math.min(actualLoss, maxLoss);
            }

            if (actualLoss == 0) {
                return;
            }

            // Update XP and stamp level
            PlayerLevelData newData = data.withXpDelta(-actualLoss);
            int newLevel = formula.getLevelForXp(newData.xp());
            newData = newData.withStoredLevel(newLevel);
            repository.save(newData);

            // Fire XP loss event
            eventDispatcher.dispatchXpLoss(playerId, actualLoss, source, newData.xp());

            // Fire level-down event if level decreased
            if (newLevel < oldLevel) {
                eventDispatcher.dispatchLevelDown(playerId, newLevel, oldLevel, newData.xp());
            }

        } finally {
            lock.unlock();
        }
    }

    @Override
    public void setXp(@Nonnull UUID playerId, long amount) {
        ReentrantLock lock = getPlayerLock(playerId);
        lock.lock();
        try {
            PlayerLevelData data = repository.getOrDefault(playerId);
            int oldLevel = formula.getLevelForXp(data.xp());

            // Update XP (withXp clamps to >= 0) and stamp level
            PlayerLevelData newData = data.withXp(amount);
            int newLevel = formula.getLevelForXp(newData.xp());
            newData = newData.withStoredLevel(newLevel);
            repository.save(newData);

            // Fire level change events if needed
            if (newLevel > oldLevel) {
                eventDispatcher.dispatchLevelUp(playerId, newLevel, oldLevel, newData.xp());
            } else if (newLevel < oldLevel) {
                eventDispatcher.dispatchLevelDown(playerId, newLevel, oldLevel, newData.xp());
            }

        } finally {
            lock.unlock();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // LEVEL OPERATIONS (LevelingService interface)
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public int getLevel(@Nonnull UUID playerId) {
        long xp = getXp(playerId);
        return formula.getLevelForXp(xp);
    }

    @Override
    public void setLevel(@Nonnull UUID playerId, int level) {
        // Clamp level to valid range
        int clampedLevel = Math.max(1, Math.min(level, formula.getMaxLevel()));
        long xpForLevel = formula.getXpForLevel(clampedLevel);
        setXp(playerId, xpForLevel);
    }

    // ═══════════════════════════════════════════════════════════════════
    // FORMULA QUERIES (LevelingService interface)
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public long getXpForLevel(int level) {
        return formula.getXpForLevel(level);
    }

    @Override
    public long getXpToNextLevel(@Nonnull UUID playerId) {
        long xp = getXp(playerId);
        return formula.getXpToNextLevel(xp);
    }

    @Override
    public float getLevelProgress(@Nonnull UUID playerId) {
        long xp = getXp(playerId);
        return formula.getLevelProgress(xp);
    }

    @Override
    public int getMaxLevel() {
        return formula.getMaxLevel();
    }

    // ═══════════════════════════════════════════════════════════════════
    // EVENT REGISTRATION (LevelingService interface)
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public void registerLevelUpListener(@Nonnull LevelingEvents.LevelUpListener listener) {
        eventDispatcher.registerLevelUpListener(listener);
    }

    @Override
    public void registerLevelDownListener(@Nonnull LevelingEvents.LevelDownListener listener) {
        eventDispatcher.registerLevelDownListener(listener);
    }

    @Override
    public void registerXpGainListener(@Nonnull LevelingEvents.XpGainListener listener) {
        eventDispatcher.registerXpGainListener(listener);
    }

    @Override
    public void registerXpLossListener(@Nonnull LevelingEvents.XpLossListener listener) {
        eventDispatcher.registerXpLossListener(listener);
    }

    @Override
    public void unregisterLevelUpListener(@Nonnull LevelingEvents.LevelUpListener listener) {
        eventDispatcher.unregisterLevelUpListener(listener);
    }

    @Override
    public void unregisterLevelDownListener(@Nonnull LevelingEvents.LevelDownListener listener) {
        eventDispatcher.unregisterLevelDownListener(listener);
    }

    @Override
    public void unregisterXpGainListener(@Nonnull LevelingEvents.XpGainListener listener) {
        eventDispatcher.unregisterXpGainListener(listener);
    }

    @Override
    public void unregisterXpLossListener(@Nonnull LevelingEvents.XpLossListener listener) {
        eventDispatcher.unregisterXpLossListener(listener);
    }

    // ═══════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Saves all cached player data to the database.
     *
     * <p>This is a synchronous operation that blocks until all data is saved.
     * Call this before shutdown.
     */
    public void saveAll() {
        repository.saveAll();
        LOGGER.at(Level.INFO).log("All leveling data saved");
    }

    /**
     * Shuts down the leveling manager.
     *
     * <p>Clears event listeners and shuts down the repository.
     */
    public void shutdown() {
        eventDispatcher.clearAll();
        repository.shutdown();
        playerLocks.clear();
        adjustedPlayers.clear();
        LOGGER.at(Level.INFO).log("LevelingManager shutdown complete");
    }

    /**
     * Checks whether this player's XP was adjusted during level protection on load.
     *
     * <p>Returns true at most once per player per session (clears the flag).
     * Used by PlayerJoinListener to show a one-time notification.
     *
     * @param playerId The player's UUID
     * @return true if XP was bumped to preserve their level
     */
    public boolean wasXpAdjusted(@Nonnull UUID playerId) {
        return adjustedPlayers.remove(playerId);
    }

    /** Gets the repository for direct access (advanced use). */
    @Nonnull
    public LevelingRepository getRepository() {
        return repository;
    }

    /** Gets the formula for direct access (advanced use). */
    @Nonnull
    public LevelFormula getFormula() {
        return formula;
    }

    /** Gets the configuration. */
    @Nonnull
    public LevelingConfig getConfig() {
        return config;
    }

    // ═══════════════════════════════════════════════════════════════════
    // PRIVATE METHODS
    // ═══════════════════════════════════════════════════════════════════

    @Nonnull
    private ReentrantLock getPlayerLock(@Nonnull UUID playerId) {
        return playerLocks.computeIfAbsent(playerId, k -> new ReentrantLock());
    }

    /**
     * Protects a player's level from XP curve changes.
     *
     * <p>Two layers of protection:
     * <ol>
     *   <li><b>Bootstrap</b>: If {@code storedLevel} is null (pre-migration player),
     *       uses {@code previousFormula} to compute what their level WAS. If the old
     *       level exceeds the new level, bumps XP to preserve it.</li>
     *   <li><b>Ongoing</b>: If {@code storedLevel > currentLevel}, bumps XP to match
     *       the stored level. Handles all future curve changes automatically.</li>
     * </ol>
     *
     * <p>Always stamps the current derived level on the data before returning.
     *
     * @param data The loaded player data
     * @return The (possibly adjusted) player data with level stamped
     */
    @Nonnull
    private PlayerLevelData protectLevel(@Nonnull PlayerLevelData data) {
        int currentLevel = formula.getLevelForXp(data.xp());

        if (data.storedLevel() == null) {
            // Pre-migration player — use old formula to detect level loss
            if (previousFormula != null && data.xp() > 0) {
                int oldLevel = Math.min(previousFormula.getLevelForXp(data.xp()), formula.getMaxLevel());
                if (oldLevel > currentLevel) {
                    long requiredXp = formula.getXpForLevel(oldLevel);
                    LOGGER.atInfo().log(
                        "XP curve migration: %s was level %d (old curve), derived %d (new curve). "
                        + "Bumping XP %d -> %d to preserve level.",
                        data.uuid(), oldLevel, currentLevel, data.xp(), requiredXp);
                    data = data.withXp(requiredXp).withStoredLevel(oldLevel);
                    repository.save(data);
                    adjustedPlayers.add(data.uuid());
                    return data;
                }
            }
            // No adjustment needed — just stamp the current level
            data = data.withStoredLevel(currentLevel);
            repository.save(data);
            return data;
        }

        // Clamp stored level to current maxLevel (handles maxLevel reductions between versions)
        int storedLevel = data.storedLevel();
        int clampedLevel = Math.min(storedLevel, formula.getMaxLevel());
        boolean wasClamped = clampedLevel != storedLevel;

        // Ongoing protection: stored level > current derived level
        if (clampedLevel > currentLevel) {
            long requiredXp = formula.getXpForLevel(clampedLevel);
            LOGGER.atInfo().log(
                "XP curve protection: %s stored level %d > derived level %d. "
                + "Bumping XP %d -> %d.",
                data.uuid(), clampedLevel, currentLevel, data.xp(), requiredXp);
            data = data.withXp(requiredXp).withStoredLevel(clampedLevel);
            repository.save(data);
            adjustedPlayers.add(data.uuid());
            return data;
        }

        // Level increased naturally (e.g., curve got easier) or storedLevel was clamped — update
        if (currentLevel > clampedLevel || wasClamped) {
            data = data.withStoredLevel(currentLevel);
            repository.save(data);
        }

        return data;
    }
}
