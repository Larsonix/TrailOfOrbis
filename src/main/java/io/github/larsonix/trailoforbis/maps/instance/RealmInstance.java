package io.github.larsonix.trailoforbis.maps.instance;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import io.github.larsonix.trailoforbis.maps.core.*;
import io.github.larsonix.trailoforbis.maps.spawning.RealmLabyrinthPlacer;
import io.github.larsonix.trailoforbis.maps.templates.RealmTemplate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Represents an active realm instance.
 *
 * <p>A RealmInstance encapsulates:
 * <ul>
 *   <li>The realm map data (biome, modifiers, level, etc.)</li>
 *   <li>The backing Hytale world instance</li>
 *   <li>State machine for lifecycle management</li>
 *   <li>Player session tracking</li>
 *   <li>Completion progress tracking</li>
 * </ul>
 *
 * <h2>Lifecycle</h2>
 * <pre>
 * CREATING → READY → ACTIVE → ENDING → CLOSING
 *     ↓         ↓       ↓        ↓
 *     └─────────┴───────┴────────┴──────→ CLOSING (on error/force close)
 * </pre>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is designed for concurrent access. State transitions and
 * player tracking use atomic operations.
 *
 * @see RealmState
 * @see RealmMapData
 * @see RealmCompletionTracker
 */
public class RealmInstance {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // ═══════════════════════════════════════════════════════════════════
    // IDENTITY
    // ═══════════════════════════════════════════════════════════════════

    /** Unique identifier for this realm instance. */
    private final UUID realmId;

    /** The map data defining this realm's properties. */
    private final RealmMapData mapData;

    /** The template used to create this instance. */
    private final RealmTemplate template;

    /** The UUID of the player who opened this realm. */
    private final UUID ownerId;

    // ═══════════════════════════════════════════════════════════════════
    // WORLD REFERENCE
    // ═══════════════════════════════════════════════════════════════════

    /** The backing Hytale world (null until world is loaded). */
    private volatile World world;

    /** The world name/key used by InstancesPlugin. */
    private volatile String worldName;

    /** Labyrinth generation result (MOUNTAINS biome only, null for other biomes). */
    @Nullable
    private volatile RealmLabyrinthPlacer.LabyrinthResult labyrinthResult;

    // ═══════════════════════════════════════════════════════════════════
    // STATE MACHINE
    // ═══════════════════════════════════════════════════════════════════

    /** Current state of the realm. */
    private final AtomicReference<RealmState> state = new AtomicReference<>(RealmState.CREATING);

    /** Listeners for state changes. */
    private final List<Consumer<StateTransition>> stateListeners = new ArrayList<>();

    // ═══════════════════════════════════════════════════════════════════
    // TIMING
    // ═══════════════════════════════════════════════════════════════════

    /** When this instance was created. */
    private final Instant createdAt;

    /** When the realm became READY. */
    private volatile Instant readyAt;

    /** When combat started (ACTIVE state). */
    private volatile Instant startedAt;

    /** When the realm ended (ENDING/CLOSING state). */
    private volatile Instant endedAt;

    /** Timeout duration for this realm. */
    private final Duration timeout;

    // ═══════════════════════════════════════════════════════════════════
    // PLAYER TRACKING
    // ═══════════════════════════════════════════════════════════════════

    /** Players currently in this realm. */
    private final Set<UUID> currentPlayers = ConcurrentHashMap.newKeySet();

    /** Players who have ever been in this realm. */
    private final Set<UUID> allParticipants = ConcurrentHashMap.newKeySet();

    /** Maximum players that were in the realm simultaneously. */
    private volatile int peakPlayerCount = 0;

    // ═══════════════════════════════════════════════════════════════════
    // COMPLETION TRACKING
    // ═══════════════════════════════════════════════════════════════════

    /** Tracks monster kills and completion progress. */
    private final RealmCompletionTracker completionTracker;

    /** How the realm ended (null if not ended). */
    private volatile CompletionReason completionReason;

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a new realm instance.
     *
     * @param realmId Unique identifier
     * @param mapData The realm map data
     * @param template The template to instantiate
     * @param ownerId The player who opened this realm
     */
    public RealmInstance(
            @Nonnull UUID realmId,
            @Nonnull RealmMapData mapData,
            @Nonnull RealmTemplate template,
            @Nonnull UUID ownerId) {
        this.realmId = Objects.requireNonNull(realmId, "Realm ID cannot be null");
        this.mapData = Objects.requireNonNull(mapData, "Map data cannot be null");
        this.template = Objects.requireNonNull(template, "Template cannot be null");
        this.ownerId = Objects.requireNonNull(ownerId, "Owner ID cannot be null");

        this.createdAt = Instant.now();
        this.timeout = Duration.ofSeconds(mapData.computeTimeoutSeconds());

        // Initialize completion tracker
        int estimatedMonsters = mapData.calculateMonsterCount();
        this.completionTracker = new RealmCompletionTracker(realmId, estimatedMonsters);

        // Per-realm creation - use FINE level
        LOGGER.atFine().log("[RealmInstance] Created realm %s with estimated %d monsters",
            realmId.toString().substring(0, 8), estimatedMonsters);
    }

    // ═══════════════════════════════════════════════════════════════════
    // STATE MACHINE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the current state.
     *
     * @return Current realm state
     */
    @Nonnull
    public RealmState getState() {
        return state.get();
    }

    /**
     * Attempts to transition to a new state.
     *
     * @param newState The target state
     * @return true if transition was successful
     */
    public boolean transitionTo(@Nonnull RealmState newState) {
        RealmState current = state.get();

        if (!current.canTransitionTo(newState)) {
            LOGGER.atWarning().log("Cannot transition realm %s from %s to %s",
                realmId, current, newState);
            return false;
        }

        if (!state.compareAndSet(current, newState)) {
            // Another thread changed state, retry
            return transitionTo(newState);
        }

        // Update timing
        updateTimingForState(newState);

        // Notify listeners
        StateTransition transition = new StateTransition(current, newState, Instant.now());
        notifyStateListeners(transition);

        LOGGER.atInfo().log("Realm %s transitioned: %s → %s", realmId, current, newState);
        return true;
    }

    /**
     * Forces transition to CLOSING state regardless of current state.
     *
     * @param reason The reason for closing
     */
    public void forceClose(@Nonnull CompletionReason reason) {
        RealmState current = state.get();
        if (current == RealmState.CLOSING) {
            return; // Already closing
        }

        this.completionReason = reason;
        state.set(RealmState.CLOSING);
        endedAt = Instant.now();

        StateTransition transition = new StateTransition(current, RealmState.CLOSING, endedAt);
        notifyStateListeners(transition);

        LOGGER.atInfo().log("Realm %s force closed: %s (from %s)", realmId, reason, current);
    }

    /**
     * Updates timing fields based on new state.
     */
    private void updateTimingForState(RealmState newState) {
        switch (newState) {
            case READY -> readyAt = Instant.now();
            case ACTIVE -> {
                startedAt = Instant.now();
                completionTracker.start();
            }
            case ENDING, CLOSING -> endedAt = Instant.now();
        }
    }

    /**
     * Adds a state change listener.
     *
     * @param listener The listener to add
     */
    public void addStateListener(@Nonnull Consumer<StateTransition> listener) {
        synchronized (stateListeners) {
            stateListeners.add(listener);
        }
    }

    /**
     * Removes a state change listener.
     *
     * @param listener The listener to remove
     */
    public void removeStateListener(@Nonnull Consumer<StateTransition> listener) {
        synchronized (stateListeners) {
            stateListeners.remove(listener);
        }
    }

    /**
     * Notifies all state listeners.
     */
    private void notifyStateListeners(StateTransition transition) {
        List<Consumer<StateTransition>> listeners;
        synchronized (stateListeners) {
            listeners = new ArrayList<>(stateListeners);
        }
        for (Consumer<StateTransition> listener : listeners) {
            try {
                listener.accept(transition);
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("State listener threw exception");
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // STATE QUERIES
    // ═══════════════════════════════════════════════════════════════════

    /** @return true if the realm is being created */
    public boolean isCreating() {
        return state.get() == RealmState.CREATING;
    }

    /** @return true if the realm is ready for players */
    public boolean isReady() {
        return state.get() == RealmState.READY;
    }

    /** @return true if combat is active */
    public boolean isActive() {
        return state.get() == RealmState.ACTIVE;
    }

    /** @return true if the realm is ending (grace period) */
    public boolean isEnding() {
        return state.get() == RealmState.ENDING;
    }

    /** @return true if the realm is closing/closed */
    public boolean isClosing() {
        return state.get() == RealmState.CLOSING;
    }

    /** @return true if the realm allows player entry */
    public boolean allowsEntry() {
        return state.get().allowsEntry();
    }

    /** @return true if combat should be processed */
    public boolean isCombatActive() {
        return state.get().isCombatActive();
    }

    /** @return true if the realm is in a terminal state */
    public boolean isTerminal() {
        return state.get().isTerminal();
    }

    // ═══════════════════════════════════════════════════════════════════
    // WORLD MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Sets the backing world after instance creation.
     *
     * @param world The Hytale world
     * @param worldName The world name/key
     */
    public void setWorld(@Nonnull World world, @Nonnull String worldName) {
        this.world = world;
        this.worldName = worldName;
    }

    /**
     * @return The backing world, or null if not yet created
     */
    @Nullable
    public World getWorld() {
        return world;
    }

    /**
     * @return The world name, or null if not yet created
     */
    @Nullable
    public String getWorldName() {
        return worldName;
    }

    /**
     * @return true if the world is loaded and valid
     */
    public boolean hasWorld() {
        World w = world;
        return w != null && w.isAlive();
    }

    // ═══════════════════════════════════════════════════════════════════
    // PLAYER MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Called when a player enters the realm.
     *
     * @param playerId The player UUID
     * @return true if the player was added, false if already present
     */
    public boolean onPlayerEnter(@Nonnull UUID playerId) {
        if (!allowsEntry()) {
            LOGGER.atWarning().log("Player %s tried to enter realm %s in state %s",
                playerId, realmId, state.get());
            return false;
        }

        boolean added = currentPlayers.add(playerId);
        if (added) {
            allParticipants.add(playerId);
            completionTracker.addParticipant(playerId);

            int current = currentPlayers.size();
            if (current > peakPlayerCount) {
                peakPlayerCount = current;
            }

            LOGGER.atInfo().log("Player %s entered realm %s (%d players)",
                playerId, realmId, current);
        }
        return added;
    }

    /**
     * Called when a player leaves the realm.
     *
     * @param playerId The player UUID
     * @return true if the player was removed
     */
    public boolean onPlayerLeave(@Nonnull UUID playerId) {
        boolean removed = currentPlayers.remove(playerId);
        if (removed) {
            LOGGER.atInfo().log("Player %s left realm %s (%d players remaining)",
                playerId, realmId, currentPlayers.size());
        }
        return removed;
    }

    /**
     * @return Set of currently present players
     */
    @Nonnull
    public Set<UUID> getCurrentPlayers() {
        return Collections.unmodifiableSet(currentPlayers);
    }

    /**
     * @return Set of all players who have participated
     */
    @Nonnull
    public Set<UUID> getAllParticipants() {
        return Collections.unmodifiableSet(allParticipants);
    }

    /**
     * @return Current player count
     */
    public int getPlayerCount() {
        return currentPlayers.size();
    }

    /**
     * @return Peak player count
     */
    public int getPeakPlayerCount() {
        return peakPlayerCount;
    }

    /**
     * @return true if there are no players in the realm
     */
    public boolean isEmpty() {
        return currentPlayers.isEmpty();
    }

    /**
     * Checks if a specific player is in the realm.
     *
     * @param playerId The player UUID
     * @return true if the player is currently in the realm
     */
    public boolean hasPlayer(@Nonnull UUID playerId) {
        return currentPlayers.contains(playerId);
    }

    // ═══════════════════════════════════════════════════════════════════
    // ALIVE PLAYER TRACKING
    // ═══════════════════════════════════════════════════════════════════

    /** Players currently dead (waiting to respawn or kicked). */
    private final Set<UUID> deadPlayers = ConcurrentHashMap.newKeySet();

    /**
     * Marks a player as dead.
     *
     * @param playerId The player UUID
     */
    public void markPlayerDead(@Nonnull UUID playerId) {
        deadPlayers.add(playerId);
    }

    /**
     * Marks a player as alive (after respawn).
     *
     * @param playerId The player UUID
     */
    public void markPlayerAlive(@Nonnull UUID playerId) {
        deadPlayers.remove(playerId);
    }

    /**
     * Checks if a player is currently dead.
     *
     * @param playerId The player UUID
     * @return true if the player is dead
     */
    public boolean isPlayerDead(@Nonnull UUID playerId) {
        return deadPlayers.contains(playerId);
    }

    /**
     * Gets the count of alive players (present and not dead).
     *
     * @return Number of alive players
     */
    public int getAlivePlayerCount() {
        int alive = 0;
        for (UUID playerId : currentPlayers) {
            if (!deadPlayers.contains(playerId)) {
                alive++;
            }
        }
        return alive;
    }

    /**
     * Checks if all present players are dead.
     *
     * @return true if all players in the realm are dead
     */
    public boolean areAllPlayersDead() {
        if (currentPlayers.isEmpty()) {
            return true; // No players = effectively all dead
        }
        return getAlivePlayerCount() == 0;
    }

    // ═══════════════════════════════════════════════════════════════════
    // COMPLETION & PROGRESS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * @return The completion tracker
     */
    @Nonnull
    public RealmCompletionTracker getCompletionTracker() {
        return completionTracker;
    }

    /**
     * @return Completion progress (0 to 1)
     */
    public float getProgress() {
        return completionTracker.getCompletionProgress();
    }

    /**
     * @return Completion percentage (0 to 100)
     */
    public int getProgressPercentage() {
        return completionTracker.getCompletionPercentage();
    }

    /**
     * @return true if all monsters have been killed
     */
    public boolean isObjectiveComplete() {
        return completionTracker.isCompleted();
    }

    /**
     * Records a mob kill and updates completion progress.
     *
     * @param killerId The UUID of the player who killed the mob, or null if no player
     * @return true if the realm is now complete after this kill
     */
    public boolean recordMobKill(@Nullable UUID killerId) {
        if (killerId != null) {
            return completionTracker.onMonsterKilled(killerId, 1, false, false);
        } else {
            return completionTracker.onMonsterKilledNoKiller(1);
        }
    }

    /**
     * Records a mob kill with additional info about mob type.
     *
     * @param killerId The UUID of the player who killed the mob, or null if no player
     * @param isElite Whether the mob was an elite
     * @param isBoss Whether the mob was a boss
     * @return true if the realm is now complete after this kill
     */
    public boolean recordMobKill(@Nullable UUID killerId, boolean isElite, boolean isBoss) {
        if (killerId != null) {
            return completionTracker.onMonsterKilled(killerId, 1, isElite, isBoss);
        } else {
            return completionTracker.onMonsterKilledNoKiller(1);
        }
    }

    /**
     * @return The reason the realm completed, or null if not completed
     */
    @Nullable
    public CompletionReason getCompletionReason() {
        return completionReason;
    }

    /**
     * Sets the completion reason if not already set.
     *
     * @param reason The completion reason
     */
    public void setCompletionReason(@Nonnull CompletionReason reason) {
        if (this.completionReason == null) {
            this.completionReason = reason;
        }
    }

    /**
     * Marks the realm as successfully completed.
     */
    public void markCompleted() {
        this.completionReason = CompletionReason.COMPLETED;
        completionTracker.markCompleted();
        transitionTo(RealmState.ENDING);
    }

    /**
     * Marks the realm as timed out.
     */
    public void markTimedOut() {
        this.completionReason = CompletionReason.TIMEOUT;
        completionTracker.markTimedOut();
        transitionTo(RealmState.ENDING);
    }

    // ═══════════════════════════════════════════════════════════════════
    // TIMING INFORMATION
    // ═══════════════════════════════════════════════════════════════════

    /** @return When this instance was created */
    @Nonnull
    public Instant getCreatedAt() {
        return createdAt;
    }

    /** @return When the realm became ready (null if not yet) */
    @Nullable
    public Instant getReadyAt() {
        return readyAt;
    }

    /** @return When combat started (null if not yet) */
    @Nullable
    public Instant getStartedAt() {
        return startedAt;
    }

    /** @return When the realm ended (null if not yet) */
    @Nullable
    public Instant getEndedAt() {
        return endedAt;
    }

    /** @return The timeout duration */
    @Nonnull
    public Duration getTimeout() {
        return timeout;
    }

    /**
     * Gets the elapsed time since combat started.
     *
     * @return Elapsed duration, or zero if not started
     */
    @Nonnull
    public Duration getElapsedTime() {
        if (startedAt == null) {
            return Duration.ZERO;
        }
        Instant end = endedAt != null ? endedAt : Instant.now();
        return Duration.between(startedAt, end);
    }

    /**
     * Gets the remaining time before timeout.
     *
     * @return Remaining duration, or zero if timed out
     */
    @Nonnull
    public Duration getRemainingTime() {
        Duration elapsed = getElapsedTime();
        Duration remaining = timeout.minus(elapsed);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    /**
     * @return true if the timeout has been exceeded
     */
    public boolean isTimedOut() {
        return startedAt != null && getElapsedTime().compareTo(timeout) >= 0;
    }

    // ═══════════════════════════════════════════════════════════════════
    // IDENTITY & DATA GETTERS
    // ═══════════════════════════════════════════════════════════════════

    /** @return The unique realm ID */
    @Nonnull
    public UUID getRealmId() {
        return realmId;
    }

    /** @return The map data */
    @Nonnull
    public RealmMapData getMapData() {
        return mapData;
    }

    /** @return The labyrinth result (MOUNTAINS only, null for other biomes) */
    @Nullable
    public RealmLabyrinthPlacer.LabyrinthResult getLabyrinthResult() {
        return labyrinthResult;
    }

    /** Set the labyrinth generation result (called by RealmsManager for MOUNTAINS biome). */
    public void setLabyrinthResult(@Nullable RealmLabyrinthPlacer.LabyrinthResult result) {
        this.labyrinthResult = result;
    }

    /** @return The template */
    @Nonnull
    public RealmTemplate getTemplate() {
        return template;
    }

    /** @return The owner player ID */
    @Nonnull
    public UUID getOwnerId() {
        return ownerId;
    }

    /** @return The realm level */
    public int getLevel() {
        return mapData.level();
    }

    /** @return The biome type */
    @Nonnull
    public RealmBiomeType getBiome() {
        return mapData.biome();
    }

    /** @return The layout size */
    @Nonnull
    public RealmLayoutSize getSize() {
        return mapData.size();
    }

    // ═══════════════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RealmInstance that = (RealmInstance) o;
        return realmId.equals(that.realmId);
    }

    @Override
    public int hashCode() {
        return realmId.hashCode();
    }

    @Override
    public String toString() {
        return String.format("RealmInstance[id=%s, state=%s, biome=%s, level=%d, players=%d]",
            realmId.toString().substring(0, 8),
            state.get(),
            mapData.biome(),
            mapData.level(),
            getPlayerCount()
        );
    }

    // ═══════════════════════════════════════════════════════════════════
    // NESTED TYPES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Reasons for realm completion/closure.
     */
    public enum CompletionReason {
        /** All objectives completed successfully */
        COMPLETED,
        /** Time limit exceeded */
        TIMEOUT,
        /** All players left */
        ABANDONED,
        /** Manually closed by admin/system */
        FORCE_CLOSED,
        /** Error during realm execution */
        ERROR
    }

    /**
     * Record of a state transition.
     */
    public record StateTransition(
        @Nonnull RealmState fromState,
        @Nonnull RealmState toState,
        @Nonnull Instant timestamp
    ) {
        public StateTransition {
            Objects.requireNonNull(fromState);
            Objects.requireNonNull(toState);
            Objects.requireNonNull(timestamp);
        }
    }
}
