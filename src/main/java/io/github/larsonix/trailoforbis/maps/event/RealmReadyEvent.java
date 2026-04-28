package io.github.larsonix.trailoforbis.maps.event;

import com.hypixel.hytale.server.core.universe.world.World;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;

import javax.annotation.Nonnull;

/**
 * Event fired when a realm is ready for players to enter.
 *
 * <p>At this point:
 * <ul>
 *   <li>The world has been fully loaded</li>
 *   <li>The portal can be opened</li>
 *   <li>Players can teleport in</li>
 * </ul>
 *
 * <p>Use this event to:
 * <ul>
 *   <li>Open the entry portal</li>
 *   <li>Notify players that the realm is ready</li>
 *   <li>Start entry timers</li>
 * </ul>
 */
public class RealmReadyEvent extends RealmEvent {

    private final World world;

    /**
     * Creates a new realm ready event.
     *
     * @param realm The realm instance
     * @param world The loaded Hytale world
     */
    public RealmReadyEvent(@Nonnull RealmInstance realm, @Nonnull World world) {
        super(realm);
        this.world = world;
    }

    /**
     * @return The loaded Hytale world for this realm
     */
    @Nonnull
    public World getWorld() {
        return world;
    }
}
