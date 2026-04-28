package io.github.larsonix.trailoforbis.maps.event;

import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/**
 * Event fired when a realm is fully closed and being removed.
 *
 * <p>At this point:
 * <ul>
 *   <li>All players have been teleported out</li>
 *   <li>The world is being unloaded</li>
 *   <li>Resources are being cleaned up</li>
 * </ul>
 *
 * <p>Use this event to:
 * <ul>
 *   <li>Clean up any tracking data</li>
 *   <li>Log final statistics</li>
 *   <li>Remove realm from registries</li>
 *   <li>Clear cached data</li>
 * </ul>
 */
public class RealmClosedEvent extends RealmEvent {

    private final RealmInstance.CompletionReason reason;
    private final Duration totalDuration;
    private final Set<UUID> allParticipants;
    private final float completionProgress;

    /**
     * Creates a new realm closed event.
     *
     * @param realm The closed realm instance
     * @param reason The reason for closure
     */
    public RealmClosedEvent(
            @Nonnull RealmInstance realm,
            @Nonnull RealmInstance.CompletionReason reason) {
        super(realm);
        this.reason = reason;

        // Capture final statistics
        this.totalDuration = realm.getElapsedTime();
        this.allParticipants = realm.getAllParticipants();
        this.completionProgress = realm.getProgress();
    }

    /**
     * @return The reason the realm was closed
     */
    @Nonnull
    public RealmInstance.CompletionReason getReason() {
        return reason;
    }

    /**
     * @return true if the realm was successfully completed
     */
    public boolean wasSuccessful() {
        return reason == RealmInstance.CompletionReason.COMPLETED;
    }

    /**
     * @return true if the realm timed out
     */
    public boolean wasTimedOut() {
        return reason == RealmInstance.CompletionReason.TIMEOUT;
    }

    /**
     * @return true if the realm was abandoned (all players left)
     */
    public boolean wasAbandoned() {
        return reason == RealmInstance.CompletionReason.ABANDONED;
    }

    /**
     * @return The total duration from creation to close
     */
    @Nonnull
    public Duration getTotalDuration() {
        return totalDuration;
    }

    /**
     * @return All players who participated at any point
     */
    @Nonnull
    public Set<UUID> getAllParticipants() {
        return allParticipants;
    }

    /**
     * @return Total number of unique participants
     */
    public int getTotalParticipantCount() {
        return allParticipants.size();
    }

    /**
     * @return The completion progress when closed (0 to 1)
     */
    public float getCompletionProgress() {
        return completionProgress;
    }

    /**
     * @return The completion progress as a percentage
     */
    public int getCompletionPercentage() {
        return Math.round(completionProgress * 100);
    }
}
