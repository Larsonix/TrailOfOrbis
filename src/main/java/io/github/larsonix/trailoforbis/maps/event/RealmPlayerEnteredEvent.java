package io.github.larsonix.trailoforbis.maps.event;

import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Event fired when a player enters a realm.
 *
 * <p>This event fires after the player has been teleported into the realm world.
 *
 * <p>Use this event to:
 * <ul>
 *   <li>Apply realm-specific buffs/debuffs</li>
 *   <li>Show realm UI to the player</li>
 *   <li>Track participation statistics</li>
 *   <li>Transition to ACTIVE state if first player</li>
 * </ul>
 */
public class RealmPlayerEnteredEvent extends RealmEvent {

    private final UUID playerId;
    private final boolean isFirstPlayer;

    /**
     * Creates a new player entered event.
     *
     * @param realm The realm instance
     * @param playerId The UUID of the player who entered
     * @param isFirstPlayer Whether this is the first player to enter
     */
    public RealmPlayerEnteredEvent(
            @Nonnull RealmInstance realm,
            @Nonnull UUID playerId,
            boolean isFirstPlayer) {
        super(realm);
        this.playerId = playerId;
        this.isFirstPlayer = isFirstPlayer;
    }

    /**
     * @return The UUID of the player who entered
     */
    @Nonnull
    public UUID getPlayerId() {
        return playerId;
    }

    /**
     * @return true if this is the first player to enter the realm
     */
    public boolean isFirstPlayer() {
        return isFirstPlayer;
    }

    /**
     * @return The current number of players in the realm
     */
    public int getPlayerCount() {
        return getRealm().getPlayerCount();
    }
}
