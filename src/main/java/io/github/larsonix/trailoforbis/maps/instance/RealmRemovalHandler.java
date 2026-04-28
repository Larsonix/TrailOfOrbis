package io.github.larsonix.trailoforbis.maps.instance;

import com.hypixel.hytale.builtin.instances.InstancesPlugin;
import com.hypixel.hytale.builtin.instances.config.InstanceWorldConfig;
import com.hypixel.hytale.builtin.instances.removal.RemovalCondition;
import com.hypixel.hytale.builtin.instances.removal.WorldEmptyCondition;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import io.github.larsonix.trailoforbis.maps.core.RealmState;
import io.github.larsonix.trailoforbis.maps.event.RealmClosedEvent;
import io.github.larsonix.trailoforbis.maps.event.RealmPlayerExitedEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Handles cleanup and removal of realm instances.
 *
 * <p>This class manages:
 * <ul>
 *   <li>Tracking realms that need to be closed</li>
 *   <li>Handling grace periods after completion</li>
 *   <li>Coordinating player evacuation</li>
 *   <li>Cleaning up world resources</li>
 * </ul>
 *
 * @see RealmInstance
 * @see RealmRemovalCondition
 */
public class RealmRemovalHandler {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /**
     * Realms pending removal with their scheduled removal time.
     */
    private final Map<UUID, ScheduledRemoval> pendingRemovals = new ConcurrentHashMap<>();

    /**
     * Reference to teleport handler for evacuation.
     */
    private final RealmTeleportHandler teleportHandler;

    /**
     * Callback when a realm is closed.
     */
    @Nullable
    private Consumer<RealmClosedEvent> onRealmClosed;

    /**
     * Creates a new removal handler.
     *
     * @param teleportHandler The teleport handler for evacuating players
     */
    public RealmRemovalHandler(@Nonnull RealmTeleportHandler teleportHandler) {
        this.teleportHandler = Objects.requireNonNull(teleportHandler);
    }

    /**
     * Sets the callback for when a realm is closed.
     *
     * @param callback The callback
     */
    public void setOnRealmClosed(@Nullable Consumer<RealmClosedEvent> callback) {
        this.onRealmClosed = callback;
    }

    /**
     * Schedules a realm for removal after a grace period.
     *
     * @param realm The realm to remove
     * @param reason The reason for removal
     * @param gracePeriod The grace period before removal
     */
    public void scheduleRemoval(
            @Nonnull RealmInstance realm,
            @Nonnull RealmInstance.CompletionReason reason,
            @Nonnull Duration gracePeriod) {

        UUID realmId = realm.getRealmId();
        Instant removeAt = Instant.now().plus(gracePeriod);

        pendingRemovals.put(realmId, new ScheduledRemoval(realm, reason, removeAt));

        LOGGER.atInfo().log("Scheduled realm %s for removal at %s (reason: %s)",
            realmId, removeAt, reason);
    }

    /**
     * Immediately closes a realm without grace period.
     *
     * @param realm The realm to close
     * @param reason The reason for closure
     * @return A future that completes when the realm is closed
     */
    @Nonnull
    public CompletableFuture<Void> closeImmediately(
            @Nonnull RealmInstance realm,
            @Nonnull RealmInstance.CompletionReason reason) {

        UUID realmId = realm.getRealmId();

        // Remove from pending if scheduled
        pendingRemovals.remove(realmId);

        // Force state to CLOSING
        realm.forceClose(reason);

        // Evacuate all players
        return teleportHandler.evacuateRealm(realm, toExitReason(reason))
            .thenCompose(evacuatedCount -> {
                LOGGER.atInfo().log("Evacuated %d players from realm %s", evacuatedCount, realmId);

                // Fire close event
                RealmClosedEvent event = new RealmClosedEvent(realm, reason);
                if (onRealmClosed != null) {
                    try {
                        onRealmClosed.accept(event);
                    } catch (Exception e) {
                        LOGGER.atWarning().withCause(e).log("Error in realm closed callback");
                    }
                }

                // Remove the world
                return removeWorld(realm);
            });
    }

    /**
     * Processes pending removals, closing realms whose grace period has expired.
     *
     * <p>Should be called periodically (e.g., every second).
     *
     * @return Number of realms closed
     */
    public int processPendingRemovals() {
        Instant now = Instant.now();
        List<ScheduledRemoval> toRemove = new ArrayList<>();

        for (ScheduledRemoval scheduled : pendingRemovals.values()) {
            if (now.isAfter(scheduled.removeAt())) {
                toRemove.add(scheduled);
            }
        }

        for (ScheduledRemoval scheduled : toRemove) {
            pendingRemovals.remove(scheduled.realm().getRealmId());
            closeImmediately(scheduled.realm(), scheduled.reason());
        }

        return toRemove.size();
    }

    /**
     * Cancels a scheduled removal.
     *
     * @param realmId The realm ID to cancel removal for
     * @return true if a scheduled removal was cancelled
     */
    public boolean cancelRemoval(@Nonnull UUID realmId) {
        ScheduledRemoval removed = pendingRemovals.remove(realmId);
        if (removed != null) {
            LOGGER.atInfo().log("Cancelled scheduled removal for realm %s", realmId);
            return true;
        }
        return false;
    }

    /**
     * Checks if a realm is scheduled for removal.
     *
     * @param realmId The realm ID
     * @return true if scheduled for removal
     */
    public boolean isScheduledForRemoval(@Nonnull UUID realmId) {
        return pendingRemovals.containsKey(realmId);
    }

    /**
     * Gets the scheduled removal time for a realm.
     *
     * @param realmId The realm ID
     * @return The removal time, or empty if not scheduled
     */
    @Nonnull
    public Optional<Instant> getScheduledRemovalTime(@Nonnull UUID realmId) {
        ScheduledRemoval scheduled = pendingRemovals.get(realmId);
        return scheduled != null ? Optional.of(scheduled.removeAt()) : Optional.empty();
    }

    /**
     * Gets the number of realms pending removal.
     *
     * @return Pending removal count
     */
    public int getPendingRemovalCount() {
        return pendingRemovals.size();
    }

    /**
     * Closes all tracked realms immediately.
     *
     * <p>Used during shutdown.
     *
     * @return A future that completes when all realms are closed
     */
    @Nonnull
    public CompletableFuture<Integer> closeAll() {
        List<ScheduledRemoval> all = new ArrayList<>(pendingRemovals.values());
        pendingRemovals.clear();

        if (all.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }

        CompletableFuture<?>[] futures = all.stream()
            .map(s -> closeImmediately(s.realm(), RealmInstance.CompletionReason.FORCE_CLOSED))
            .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(futures)
            .thenApply(v -> all.size());
    }

    /**
     * Removes the backing world for a realm.
     */
    @Nonnull
    private CompletableFuture<Void> removeWorld(@Nonnull RealmInstance realm) {
        World world = realm.getWorld();
        if (world == null || !world.isAlive()) {
            return CompletableFuture.completedFuture(null);
        }

        String worldName = realm.getWorldName();
        if (worldName == null) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            try {
                // Use InstancesPlugin safe removal
                InstancesPlugin.safeRemoveInstance(world);
                LOGGER.atInfo().log("Removed world %s for realm %s", worldName, realm.getRealmId());
            } catch (Exception e) {
                LOGGER.atSevere().withCause(e).log("Failed to remove world %s", worldName);
            }
        });
    }

    /**
     * Configures removal conditions for a realm world.
     *
     * <p>Note: We don't set custom removal conditions because our RealmRemovalCondition
     * codec isn't registered with Hytale's codec system. Instead, we rely on:
     * <ul>
     *   <li>Built-in removal conditions from instance.bson (WorldEmpty, Timeout)</li>
     *   <li>Our own RealmRemovalHandler for scheduled closures</li>
     * </ul>
     *
     * @param realm The realm instance
     * @param gracePeriodSeconds Grace period in seconds (stored for reference)
     */
    public void configureWorldRemoval(@Nonnull RealmInstance realm, double gracePeriodSeconds) {
        // Don't set custom removal conditions - use built-in ones from instance.bson
        // and our own RealmRemovalHandler scheduler instead.
        // Setting a custom RealmRemovalCondition would cause codec errors when saving.
        LOGGER.atFine().log("Realm %s configured with grace period %.1fs (handled by RealmRemovalHandler)",
            realm.getRealmId(), gracePeriodSeconds);
    }

    /**
     * Converts completion reason to exit reason.
     */
    private RealmPlayerExitedEvent.ExitReason toExitReason(RealmInstance.CompletionReason reason) {
        return switch (reason) {
            case COMPLETED -> RealmPlayerExitedEvent.ExitReason.COMPLETION;
            case TIMEOUT -> RealmPlayerExitedEvent.ExitReason.TIMEOUT;
            case ABANDONED -> RealmPlayerExitedEvent.ExitReason.OTHER;
            case FORCE_CLOSED, ERROR -> RealmPlayerExitedEvent.ExitReason.FORCE_CLOSED;
        };
    }

    /**
     * Record for tracking scheduled removals.
     */
    private record ScheduledRemoval(
        RealmInstance realm,
        RealmInstance.CompletionReason reason,
        Instant removeAt
    ) {}
}
