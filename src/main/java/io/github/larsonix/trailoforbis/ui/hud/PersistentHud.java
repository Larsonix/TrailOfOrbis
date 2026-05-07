package io.github.larsonix.trailoforbis.ui.hud;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Contract for HUDs that persist across world transitions.
 *
 * <p>Persistent HUDs (XP bar, energy shield, future additions) are always visible
 * to players regardless of which world they're in. When a player transitions between
 * worlds, Hytale's {@code JoinWorld(clearWorld=true)} clears all client-side HyUI elements.
 * The {@link HudLifecycleManager} uses this interface to reliably discard stale
 * server state and restore HUDs after every transition.
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>{@link #discardStale(UUID)} — DrainPlayerFromWorldEvent (before leaving old world)</li>
 *   <li>{@link #restore(UUID, PlayerRef, Ref, Store)} — PlayerReadyEvent (after arriving in new world)</li>
 *   <li>{@link #removeOnDisconnect(UUID)} — PlayerDisconnectEvent (player leaving server)</li>
 *   <li>{@link #shutdown()} — Plugin disable (server shutting down)</li>
 * </ol>
 *
 * <h2>Implementation Contract</h2>
 * <ul>
 *   <li>{@code restore()} must be idempotent ��� safe to call when HUD already exists</li>
 *   <li>{@code restore()} must handle its own conditions (e.g., skip creative mode)</li>
 *   <li>{@code discardStale()} must NEVER send packets (no hide() — only remove() to cancel refresh)</li>
 *   <li>All methods must be safe to call from the world thread</li>
 * </ul>
 */
public interface PersistentHud {

    /**
     * Human-readable name for logging (e.g., "xp-bar", "energy-shield").
     */
    @Nonnull
    String hudName();

    /**
     * Discards the stale HUD during a world transition WITHOUT sending packets.
     *
     * <p>Called from {@code DrainPlayerFromWorldEvent}. At this point,
     * {@code JoinWorld(clearWorld=true)} will clear all client-side HyUI elements.
     * Implementations must only remove from
     * tracking maps and cancel refresh tasks — never call {@code hide()} which
     * would send Set commands to cleared elements (crash).
     */
    void discardStale(@Nonnull UUID playerId);

    /**
     * Checks whether this HUD is currently active for the player.
     *
     * <p>Used by the lifecycle manager to skip restoration when already showing.
     */
    boolean isActive(@Nonnull UUID playerId);

    /**
     * Attempts to restore (create) the HUD for a player after a world transition.
     *
     * <p>Called with fresh refs resolved at execution time. Implementations should
     * handle their own preconditions (e.g., checking game mode via
     * {@code playerRef.getReference()} internally). If the HUD already exists,
     * implementations should discard and recreate (idempotent).
     *
     * @param playerId  The player's UUID
     * @param playerRef Fresh player reference (resolved from Universe at call time)
     * @param store     The world's entity store
     */
    void restore(@Nonnull UUID playerId, @Nonnull PlayerRef playerRef,
                 @Nonnull Store<EntityStore> store);

    /**
     * Attempts to restore the HUD with a known-valid Player component.
     *
     * <p>Called from the primary restore path where the Player is obtained directly
     * from {@code PlayerReadyEvent.getPlayer()} — guaranteed valid at event time.
     * This bypasses HyUI's internal {@code playerRef.getReference()} resolution
     * which can return null during early world transitions, causing silent
     * registration failures.
     *
     * <p>The default implementation delegates to the 3-arg {@code restore()} for
     * backward compatibility (e.g., CombatFeedbackGhostManager which doesn't
     * need direct MultiHud registration).
     *
     * @param playerId  The player's UUID
     * @param playerRef Fresh player reference
     * @param store     The world's entity store
     * @param player    The Player component from the event, or null for safety-net paths
     */
    default void restore(@Nonnull UUID playerId, @Nonnull PlayerRef playerRef,
                         @Nonnull Store<EntityStore> store, @Nullable Player player) {
        restore(playerId, playerRef, store);
    }

    /**
     * Removes the HUD on player disconnect. May send packets (hide + remove)
     * since the client is still connected at this point.
     */
    void removeOnDisconnect(@Nonnull UUID playerId);

    /**
     * Removes all active HUDs during plugin shutdown.
     */
    void shutdown();
}
