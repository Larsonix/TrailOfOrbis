package io.github.larsonix.trailoforbis.maps.event;

import com.hypixel.hytale.event.IEvent;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.UUID;

/**
 * Base class for all realm-related events.
 *
 * <p>All realm events provide access to the realm instance and its ID.
 * Events implement {@link IEvent} with String keys for event registration.
 *
 * @see RealmCreatedEvent
 * @see RealmReadyEvent
 * @see RealmActivatedEvent
 * @see RealmPlayerEnteredEvent
 * @see RealmPlayerExitedEvent
 * @see RealmCompletedEvent
 * @see RealmClosedEvent
 */
public abstract class RealmEvent implements IEvent<String> {

    private final RealmInstance realm;

    /**
     * Creates a new realm event.
     *
     * @param realm The realm instance
     */
    protected RealmEvent(@Nonnull RealmInstance realm) {
        this.realm = Objects.requireNonNull(realm, "Realm cannot be null");
    }

    /**
     * @return The realm instance this event is about
     */
    @Nonnull
    public RealmInstance getRealm() {
        return realm;
    }

    /**
     * @return The unique ID of the realm
     */
    @Nonnull
    public UUID getRealmId() {
        return realm.getRealmId();
    }

    /**
     * @return The realm's current level
     */
    public int getLevel() {
        return realm.getLevel();
    }
}
