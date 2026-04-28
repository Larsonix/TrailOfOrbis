package io.github.larsonix.trailoforbis.maps.event;

import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Event fired when a realm instance is created but not yet ready for players.
 *
 * <p>At this point:
 * <ul>
 *   <li>The realm ID has been assigned</li>
 *   <li>Map data has been set</li>
 *   <li>The world is being created (not yet loaded)</li>
 * </ul>
 *
 * <p>Use this event to:
 * <ul>
 *   <li>Register the realm in tracking systems</li>
 *   <li>Log realm creation for analytics</li>
 *   <li>Prepare UI elements</li>
 * </ul>
 */
public class RealmCreatedEvent extends RealmEvent {

    private final UUID creatorId;

    /**
     * Creates a new realm created event.
     *
     * @param realm The created realm instance
     * @param creatorId The player who created/opened the realm
     */
    public RealmCreatedEvent(@Nonnull RealmInstance realm, @Nonnull UUID creatorId) {
        super(realm);
        this.creatorId = creatorId;
    }

    /**
     * @return The UUID of the player who created this realm
     */
    @Nonnull
    public UUID getCreatorId() {
        return creatorId;
    }
}
