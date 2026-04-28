package io.github.larsonix.trailoforbis.maps.event;

import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Event fired when a player exits a realm.
 *
 * <p>This event fires after the player has been teleported out of the realm world.
 *
 * <p>Use this event to:
 * <ul>
 *   <li>Remove realm-specific buffs/debuffs</li>
 *   <li>Hide realm UI</li>
 *   <li>Check if realm should close (last player)</li>
 *   <li>Award participation rewards</li>
 * </ul>
 */
public class RealmPlayerExitedEvent extends RealmEvent {

    /**
     * Reasons for leaving a realm.
     */
    public enum ExitReason {
        /** Player used the exit portal */
        PORTAL,
        /** Player disconnected */
        DISCONNECT,
        /** Player died and was respawned outside */
        DEATH,
        /** Realm completed */
        COMPLETION,
        /** Realm timed out */
        TIMEOUT,
        /** Realm was force closed */
        FORCE_CLOSED,
        /** Other/unknown reason */
        OTHER
    }

    private final UUID playerId;
    private final ExitReason reason;
    private final boolean isLastPlayer;

    /**
     * Creates a new player exited event.
     *
     * @param realm The realm instance
     * @param playerId The UUID of the player who exited
     * @param reason The reason for exiting
     * @param isLastPlayer Whether this was the last player in the realm
     */
    public RealmPlayerExitedEvent(
            @Nonnull RealmInstance realm,
            @Nonnull UUID playerId,
            @Nonnull ExitReason reason,
            boolean isLastPlayer) {
        super(realm);
        this.playerId = playerId;
        this.reason = reason;
        this.isLastPlayer = isLastPlayer;
    }

    /**
     * @return The UUID of the player who exited
     */
    @Nonnull
    public UUID getPlayerId() {
        return playerId;
    }

    /**
     * @return The reason for exiting
     */
    @Nonnull
    public ExitReason getReason() {
        return reason;
    }

    /**
     * @return true if this was the last player in the realm
     */
    public boolean isLastPlayer() {
        return isLastPlayer;
    }

    /**
     * @return The remaining number of players in the realm
     */
    public int getRemainingPlayerCount() {
        return getRealm().getPlayerCount();
    }
}
