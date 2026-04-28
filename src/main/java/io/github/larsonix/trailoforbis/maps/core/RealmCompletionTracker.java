package io.github.larsonix.trailoforbis.maps.core;

import com.hypixel.hytale.logger.HytaleLogger;
import javax.annotation.Nonnull;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks completion progress for a realm instance.
 *
 * <p>The tracker maintains:
 * <ul>
 *   <li>Total and remaining monster counts</li>
 *   <li>Kill statistics per player</li>
 *   <li>Participation tracking</li>
 *   <li>Timing information</li>
 * </ul>
 *
 * <p>All methods are thread-safe for concurrent access from multiple threads.
 *
 * <p>Usage:
 * <pre>{@code
 * RealmCompletionTracker tracker = new RealmCompletionTracker(realmId, 100);
 * tracker.addParticipant(playerId);
 *
 * // On monster kill
 * tracker.onMonsterKilled(killerId, 1, false);
 *
 * // Check progress
 * float progress = tracker.getCompletionProgress();
 * if (tracker.isCompleted()) {
 *     // Award rewards
 * }
 * }</pre>
 *
 * @see RealmInstance
 */
public class RealmCompletionTracker {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** The ID of the realm this tracker belongs to. */
    private final UUID realmId;

    /** Total number of monsters spawned in the realm. */
    private final AtomicInteger totalMonsters;

    /** Number of kills required for completion (95% of total — allows some stuck/lost mobs). */
    private final AtomicInteger requiredKills;

    /** Fraction of total mobs that must be killed for completion (0.95 = 95%). */
    private static final double COMPLETION_THRESHOLD = 0.95;

    /**
     * Flat buffer subtracted from total before applying the threshold.
     * Prevents low mob counts from rounding up to 100% required kills.
     * At 15 mobs: required = ceil((15-3)*0.95) = 12 instead of 15.
     * At 100 mobs: required = ceil((100-3)*0.95) = 93 instead of 95 — negligible.
     */
    private static final int LOW_COUNT_BUFFER = 3;

    /** Current number of remaining monsters. */
    private final AtomicInteger remainingMonsters;

    /** Number of monsters killed by players (excludes despawns). */
    private final AtomicInteger killedByPlayers;

    /** Number of elite monsters killed. */
    private final AtomicInteger elitesKilled;

    /** Number of boss monsters killed. */
    private final AtomicInteger bossesKilled;

    /** Players who have participated in this realm. */
    private final Set<UUID> participatingPlayers = ConcurrentHashMap.newKeySet();

    /** Kill counts per player. */
    private final Map<UUID, AtomicInteger> playerKillCounts = new ConcurrentHashMap<>();

    /** Damage dealt per player. */
    private final Map<UUID, AtomicInteger> playerDamageDealt = new ConcurrentHashMap<>();

    /** When the realm combat started. */
    private volatile Instant startTime;

    /** When the realm was completed. */
    private volatile Instant completionTime;

    /** Whether the realm has been completed. */
    private final AtomicBoolean completed = new AtomicBoolean(false);

    /** Whether the realm timed out (failed). */
    private final AtomicBoolean timedOut = new AtomicBoolean(false);

    /**
     * Creates a new completion tracker.
     *
     * @param realmId The realm ID
     * @param totalMonsters The total number of monsters to kill
     */
    public RealmCompletionTracker(@Nonnull UUID realmId, int totalMonsters) {
        this.realmId = Objects.requireNonNull(realmId, "Realm ID cannot be null");
        int clamped = Math.max(0, totalMonsters);
        this.totalMonsters = new AtomicInteger(clamped);
        this.requiredKills = new AtomicInteger(calculateRequiredKills(clamped));
        this.remainingMonsters = new AtomicInteger(clamped);
        this.killedByPlayers = new AtomicInteger(0);
        this.elitesKilled = new AtomicInteger(0);
        this.bossesKilled = new AtomicInteger(0);
    }

    // ═══════════════════════════════════════════════════════════════════
    // LIFECYCLE METHODS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Starts the completion timer.
     *
     * <p>Should be called when combat begins (first monster spawned or player enters).
     */
    public void start() {
        if (startTime == null) {
            startTime = Instant.now();
        }
    }

    /**
     * Marks the realm as completed.
     *
     * @return true if this call triggered completion, false if already completed
     */
    public boolean markCompleted() {
        if (completed.compareAndSet(false, true)) {
            completionTime = Instant.now();
            return true;
        }
        return false;
    }

    /**
     * Marks the realm as timed out (failed).
     *
     * @return true if this call triggered timeout, false if already completed/timed out
     */
    public boolean markTimedOut() {
        if (!completed.get() && timedOut.compareAndSet(false, true)) {
            completionTime = Instant.now();
            return true;
        }
        return false;
    }

    // ═══════════════════════════════════════════════════════════════════
    // MONSTER TRACKING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Called when a monster is killed.
     *
     * @param killerId The UUID of the player who got the kill (nullable for non-player kills)
     * @param count Number of monsters killed (usually 1)
     * @param isElite Whether the killed monster was an elite
     * @param isBoss Whether the killed monster was a boss
     * @return true if this kill triggered completion
     */
    public boolean onMonsterKilled(@Nonnull UUID killerId, int count, boolean isElite, boolean isBoss) {
        Objects.requireNonNull(killerId, "Killer ID cannot be null");

        remainingMonsters.addAndGet(-count);
        int totalKilled = killedByPlayers.addAndGet(count);

        // Track elite/boss kills
        if (isElite) {
            elitesKilled.addAndGet(count);
        }
        if (isBoss) {
            bossesKilled.addAndGet(count);
        }

        // Track per-player kills
        playerKillCounts.computeIfAbsent(killerId, k -> new AtomicInteger(0))
            .addAndGet(count);

        // Ensure player is marked as participant
        participatingPlayers.add(killerId);

        // Check for completion — 95% of spawned mobs killed is enough.
        // This prevents stuck/lost mobs from blocking realm completion.
        if (totalKilled >= requiredKills.get() && !completed.get()) {
            return markCompleted();
        }

        return false;
    }

    /**
     * Called when a monster is killed without a specific killer (e.g., environmental).
     *
     * @param count Number of monsters killed
     * @return true if this triggered completion
     */
    public boolean onMonsterKilledNoKiller(int count) {
        remainingMonsters.addAndGet(-count);
        int totalKilled = killedByPlayers.addAndGet(count);

        if (totalKilled >= requiredKills.get() && !completed.get()) {
            return markCompleted();
        }

        return false;
    }

    /**
     * Called when additional monsters are spawned.
     *
     * @param count Number of additional monsters
     */
    public void onMonstersSpawned(int count) {
        int newTotal = totalMonsters.addAndGet(count);
        requiredKills.set(calculateRequiredKills(newTotal));
        remainingMonsters.addAndGet(count);
    }

    /**
     * Sets the total monster count to match actual spawned count.
     *
     * <p>This should be called after initial spawning to synchronize
     * the tracker with the actual number of mobs that were spawned.
     *
     * @param actualCount The actual number of monsters spawned
     */
    public void setTotalMonsters(int actualCount) {
        totalMonsters.set(actualCount);
        int required = calculateRequiredKills(actualCount);
        requiredKills.set(required);
        remainingMonsters.set(actualCount);
        LOGGER.atInfo().log("[RealmTracker] Spawned %d mobs, %d kills required for completion (%.0f%% of %d after buffer)",
            actualCount, required, COMPLETION_THRESHOLD * 100, Math.max(0, actualCount - LOW_COUNT_BUFFER));
    }

    /**
     * Calculates the number of kills required for completion.
     *
     * <p>Subtracts a flat buffer before applying the percentage threshold.
     * This prevents small mob counts from rounding up to 100% required,
     * which would make realms uncompletable if a single mob despawns.
     *
     * @param total The total number of monsters
     * @return The required kill count
     */
    private static int calculateRequiredKills(int total) {
        if (total <= 0) return 0;
        int effectiveTotal = Math.max(1, total - LOW_COUNT_BUFFER);
        return (int) Math.ceil(effectiveTotal * COMPLETION_THRESHOLD);
    }

    /**
     * Records damage dealt by a player.
     *
     * @param playerId The player who dealt damage
     * @param damage The amount of damage dealt
     */
    public void recordDamage(@Nonnull UUID playerId, int damage) {
        playerDamageDealt.computeIfAbsent(playerId, k -> new AtomicInteger(0))
            .addAndGet(damage);
        participatingPlayers.add(playerId);
    }

    // ═══════════════════════════════════════════════════════════════════
    // PARTICIPANT TRACKING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Adds a player as a participant.
     *
     * @param playerId The player UUID
     */
    public void addParticipant(@Nonnull UUID playerId) {
        participatingPlayers.add(playerId);
    }

    /**
     * Checks if a player has participated.
     *
     * @param playerId The player UUID
     * @return true if the player has participated
     */
    public boolean hasParticipated(@Nonnull UUID playerId) {
        return participatingPlayers.contains(playerId);
    }

    /**
     * Gets the set of all participating players.
     *
     * @return Unmodifiable set of participant UUIDs
     */
    @Nonnull
    public Set<UUID> getParticipants() {
        return Collections.unmodifiableSet(participatingPlayers);
    }

    /**
     * Gets the number of participating players.
     *
     * @return Participant count
     */
    public int getParticipantCount() {
        return participatingPlayers.size();
    }

    // ═══════════════════════════════════════════════════════════════════
    // PROGRESS QUERIES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the completion progress as a float from 0 to 1.
     *
     * @return Completion progress (0 = no progress, 1 = complete)
     */
    public float getCompletionProgress() {
        int total = totalMonsters.get();
        if (total == 0) {
            return 1.0f;
        }
        int killed = total - remainingMonsters.get();
        return Math.min(1.0f, (float) killed / total);
    }

    /**
     * Gets the completion progress as a percentage (0-100).
     *
     * @return Completion percentage
     */
    public int getCompletionPercentage() {
        return Math.round(getCompletionProgress() * 100);
    }

    /**
     * @return The total number of monsters in this realm
     */
    public int getTotalMonsters() {
        return totalMonsters.get();
    }

    /**
     * @return The number of kills required for completion (95% of total)
     */
    public int getRequiredKills() {
        return requiredKills.get();
    }

    /**
     * @return The number of remaining monsters
     */
    public int getRemainingMonsters() {
        return Math.max(0, remainingMonsters.get());
    }

    /**
     * @return The number of monsters killed by players
     */
    public int getKilledByPlayers() {
        return killedByPlayers.get();
    }

    /**
     * @return The number of elite monsters killed
     */
    public int getElitesKilled() {
        return elitesKilled.get();
    }

    /**
     * @return The number of boss monsters killed
     */
    public int getBossesKilled() {
        return bossesKilled.get();
    }

    /**
     * @return Whether the realm has been completed
     */
    public boolean isCompleted() {
        return completed.get();
    }

    /**
     * @return Whether the realm timed out
     */
    public boolean isTimedOut() {
        return timedOut.get();
    }

    /**
     * @return Whether the realm is finished (completed or timed out)
     */
    public boolean isFinished() {
        return completed.get() || timedOut.get();
    }

    // ═══════════════════════════════════════════════════════════════════
    // TIMING INFORMATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * @return When combat started, or null if not started
     */
    @Nonnull
    public Optional<Instant> getStartTime() {
        return Optional.ofNullable(startTime);
    }

    /**
     * @return When the realm was completed, or null if not completed
     */
    @Nonnull
    public Optional<Instant> getCompletionTime() {
        return Optional.ofNullable(completionTime);
    }

    /**
     * Gets the elapsed time since combat started.
     *
     * @return Elapsed duration, or zero if not started
     */
    @Nonnull
    public Duration getElapsedTime() {
        if (startTime == null) {
            return Duration.ZERO;
        }
        Instant end = completionTime != null ? completionTime : Instant.now();
        return Duration.between(startTime, end);
    }

    /**
     * Gets the elapsed time in seconds.
     *
     * @return Elapsed seconds
     */
    public long getElapsedSeconds() {
        return getElapsedTime().getSeconds();
    }

    // ═══════════════════════════════════════════════════════════════════
    // PLAYER STATISTICS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the kill count for a specific player.
     *
     * @param playerId The player UUID
     * @return The number of kills
     */
    public int getPlayerKills(@Nonnull UUID playerId) {
        AtomicInteger kills = playerKillCounts.get(playerId);
        return kills != null ? kills.get() : 0;
    }

    /**
     * Gets the damage dealt by a specific player.
     *
     * @param playerId The player UUID
     * @return The total damage dealt
     */
    public int getPlayerDamage(@Nonnull UUID playerId) {
        AtomicInteger damage = playerDamageDealt.get(playerId);
        return damage != null ? damage.get() : 0;
    }

    /**
     * Gets all player kill counts.
     *
     * @return Map of player UUID to kill count
     */
    @Nonnull
    public Map<UUID, Integer> getAllPlayerKills() {
        Map<UUID, Integer> result = new HashMap<>();
        playerKillCounts.forEach((id, count) -> result.put(id, count.get()));
        return result;
    }

    /**
     * Gets the player with the most kills.
     *
     * @return The top killer's UUID, or empty if no kills
     */
    @Nonnull
    public Optional<UUID> getTopKiller() {
        return playerKillCounts.entrySet().stream()
            .max(Comparator.comparingInt(e -> e.getValue().get()))
            .map(Map.Entry::getKey);
    }

    /**
     * Calculates a player's contribution percentage based on kills.
     *
     * @param playerId The player UUID
     * @return Contribution percentage (0-100)
     */
    public float getPlayerContribution(@Nonnull UUID playerId) {
        int totalKills = killedByPlayers.get();
        if (totalKills == 0) {
            // Equal distribution if no kills recorded
            int participants = participatingPlayers.size();
            return participants > 0 ? 100.0f / participants : 0;
        }
        int playerKills = getPlayerKills(playerId);
        return (float) playerKills / totalKills * 100;
    }

    // ═══════════════════════════════════════════════════════════════════
    // UTILITY METHODS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * @return The realm ID this tracker belongs to
     */
    @Nonnull
    public UUID getRealmId() {
        return realmId;
    }

    /**
     * Creates a summary string for debugging.
     *
     * @return Human-readable summary
     */
    @Nonnull
    public String toSummary() {
        return String.format(
            "RealmTracker[%s]: %d/%d killed (%.1f%%), %d participants, elapsed: %ds, status: %s",
            realmId.toString().substring(0, 8),
            getTotalMonsters() - getRemainingMonsters(),
            getTotalMonsters(),
            getCompletionProgress() * 100,
            getParticipantCount(),
            getElapsedSeconds(),
            isCompleted() ? "COMPLETED" : isTimedOut() ? "TIMED_OUT" : "IN_PROGRESS"
        );
    }

    @Override
    public String toString() {
        return toSummary();
    }
}
