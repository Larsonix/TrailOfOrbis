package io.github.larsonix.trailoforbis.maps.event;

import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;

import javax.annotation.Nonnull;

/**
 * Event fired when a realm transitions to ACTIVE state (combat begins).
 *
 * <p>At this point:
 * <ul>
 *   <li>At least one player has entered</li>
 *   <li>Monsters have spawned or are spawning</li>
 *   <li>The timer has started</li>
 *   <li>Combat is active</li>
 * </ul>
 *
 * <p>Use this event to:
 * <ul>
 *   <li>Start spawn systems</li>
 *   <li>Begin tracking progress</li>
 *   <li>Show combat UI to players</li>
 * </ul>
 */
public class RealmActivatedEvent extends RealmEvent {

    private final int totalMonsters;

    /**
     * Creates a new realm activated event.
     *
     * @param realm The realm instance
     * @param totalMonsters The total number of monsters spawned
     */
    public RealmActivatedEvent(@Nonnull RealmInstance realm, int totalMonsters) {
        super(realm);
        this.totalMonsters = totalMonsters;
    }

    /**
     * @return The total number of monsters in this realm
     */
    public int getTotalMonsters() {
        return totalMonsters;
    }

    /**
     * @return The timeout in seconds for this realm
     */
    public int getTimeoutSeconds() {
        return (int) getRealm().getTimeout().getSeconds();
    }
}
