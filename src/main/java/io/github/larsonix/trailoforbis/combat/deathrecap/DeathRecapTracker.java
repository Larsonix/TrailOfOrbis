package io.github.larsonix.trailoforbis.combat.deathrecap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks combat damage history for the death recap system.
 *
 * <p>Maintains a ring buffer of recent {@link CombatSnapshot}s per player.
 * When a player dies, the full damage chain can be retrieved for display,
 * showing the timeline of hits that led to death.
 *
 * <p>Thread-safe for concurrent access from multiple ECS systems.
 */
public class DeathRecapTracker {

    private final ConcurrentHashMap<UUID, ArrayDeque<CombatSnapshot>> damageHistory = new ConcurrentHashMap<>();
    private final DeathRecapConfig config;
    private final int maxHistorySize;

    /**
     * Creates a new death recap tracker.
     *
     * @param config The death recap configuration
     */
    public DeathRecapTracker(@Nonnull DeathRecapConfig config) {
        this.config = config;
        this.maxHistorySize = config.getDamageChainLength();
    }

    /**
     * Records damage dealt to a player.
     *
     * <p>Adds the snapshot to the front of the player's history ring buffer.
     * Oldest entries are trimmed when the buffer exceeds {@code maxHistorySize}.
     *
     * @param playerId The UUID of the player who received damage
     * @param snapshot The combat snapshot with full damage breakdown
     */
    public void recordDamage(@Nonnull UUID playerId, @Nonnull CombatSnapshot snapshot) {
        if (!config.isEnabled()) {
            return;
        }
        ArrayDeque<CombatSnapshot> history = damageHistory.computeIfAbsent(
            playerId, k -> new ArrayDeque<>(maxHistorySize));
        synchronized (history) {
            history.addFirst(snapshot);
            while (history.size() > maxHistorySize) {
                history.removeLast();
            }
        }
    }

    /**
     * Gets the full damage history for a player who just died.
     *
     * <p>Returns an immutable copy of the damage chain, ordered most-recent first.
     * Does NOT clear the history — call {@link #getKillingBlow} for that.
     *
     * @param playerId The UUID of the player
     * @return Unmodifiable list of recent snapshots (most recent first), or empty list
     */
    @Nonnull
    public List<CombatSnapshot> getDamageHistory(@Nonnull UUID playerId) {
        ArrayDeque<CombatSnapshot> history = damageHistory.get(playerId);
        if (history == null) {
            return Collections.emptyList();
        }
        synchronized (history) {
            return Collections.unmodifiableList(new ArrayList<>(history));
        }
    }

    /**
     * Gets the killing blow for a player who just died.
     *
     * <p>Returns the most recent snapshot and clears the entire history,
     * so it can only be called once per death.
     *
     * @param playerId The UUID of the player who died
     * @return The most recent damage snapshot, or empty if none recorded
     */
    @Nonnull
    public Optional<CombatSnapshot> getKillingBlow(@Nonnull UUID playerId) {
        ArrayDeque<CombatSnapshot> history = damageHistory.remove(playerId);
        if (history == null) {
            return Optional.empty();
        }
        synchronized (history) {
            return Optional.ofNullable(history.peekFirst());
        }
    }

    /**
     * Peeks at the last damage without removing it.
     *
     * @param playerId The UUID of the player
     * @return The most recent damage snapshot, or null if none
     */
    @Nullable
    public CombatSnapshot peekLastDamage(@Nonnull UUID playerId) {
        ArrayDeque<CombatSnapshot> history = damageHistory.get(playerId);
        if (history == null) {
            return null;
        }
        synchronized (history) {
            return history.peekFirst();
        }
    }

    /**
     * Clears combat data for a player.
     *
     * <p>Called when a player disconnects to free memory.
     *
     * @param playerId The UUID of the player
     */
    public void onPlayerDisconnect(@Nonnull UUID playerId) {
        damageHistory.remove(playerId);
    }

    /**
     * Clears all tracked data.
     *
     * <p>Called during plugin shutdown.
     */
    public void clear() {
        damageHistory.clear();
    }

    @Nonnull
    public DeathRecapConfig getConfig() {
        return config;
    }

    public int getTrackedPlayerCount() {
        return damageHistory.size();
    }
}
