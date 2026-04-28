package io.github.larsonix.trailoforbis.maps.listeners;

import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import io.github.larsonix.trailoforbis.maps.RealmsManager;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Listens for player disconnects and realm exit requests.
 *
 * <p>When a player disconnects while inside a realm, this listener:
 * <ol>
 *   <li>Records their exit in the realm instance</li>
 *   <li>Saves their progress (if enabled)</li>
 *   <li>Removes them from the realm tracking</li>
 *   <li>Checks if the realm should be closed (empty realm)</li>
 * </ol>
 *
 * <p>Uses {@link PlayerDisconnectEvent} to handle ungraceful exits.
 * Graceful exits (using exit portal or commands) are handled separately
 * by {@link RealmsManager#exitRealm(UUID)}.
 */
public class RealmExitListener {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Colors for messages (from shared MessageColors)
    private static final String COLOR_INFO = "#AAAAFF";
    private static final String COLOR_PREFIX = MessageColors.DARK_PURPLE;

    private final RealmsManager realmsManager;

    /**
     * Creates a new realm exit listener.
     *
     * @param realmsManager The realms manager
     */
    public RealmExitListener(@Nonnull RealmsManager realmsManager) {
        this.realmsManager = Objects.requireNonNull(realmsManager, "realmsManager cannot be null");
    }

    /**
     * Registers this listener with the event registry.
     *
     * @param eventRegistry The event registry
     */
    public void register(@Nonnull EventRegistry eventRegistry) {
        // PlayerDisconnectEvent: EARLY - Handle before other systems cleanup
        eventRegistry.register(
            EventPriority.EARLY,
            PlayerDisconnectEvent.class,
            event -> onPlayerDisconnect(event)
        );

        LOGGER.atInfo().log("RealmExitListener registered");
    }

    /**
     * Handles player disconnect events.
     *
     * <p>If the player was in a realm when they disconnected,
     * this handles their ungraceful exit.
     *
     * @param event The disconnect event
     */
    private void onPlayerDisconnect(@Nonnull PlayerDisconnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        if (playerRef == null) {
            return;
        }

        UUID playerId = playerRef.getUuid();

        // Check if player was in a realm
        Optional<RealmInstance> realmOpt = realmsManager.getPlayerRealm(playerId);
        if (realmOpt.isEmpty()) {
            // Player wasn't in a realm, nothing to do
            return;
        }

        RealmInstance realm = realmOpt.get();
        UUID realmId = realm.getRealmId();

        LOGGER.atInfo().log("Player %s disconnected while in realm %s",
                playerId.toString().substring(0, 8),
                realmId.toString().substring(0, 8));

        // Handle the ungraceful exit
        handleDisconnectExit(playerId, realm);
    }

    /**
     * Handles a player's disconnect exit from a realm.
     *
     * <p>This is called when a player disconnects while inside a realm,
     * as opposed to using an exit portal or command.
     *
     * <p>IMPORTANT: We use {@code handlePlayerDisconnect()} instead of {@code exitRealm()}
     * because when a player disconnects, Hytale is already removing them from the world.
     * Trying to teleport them would fail with "Invalid entity reference" since their
     * entity ref is already invalidated. We just need to do cleanup (HUD removal,
     * tracking updates, empty realm handling).
     *
     * @param playerId The player's UUID
     * @param realm The realm they were in
     */
    private void handleDisconnectExit(@Nonnull UUID playerId, @Nonnull RealmInstance realm) {
        UUID realmId = realm.getRealmId();

        // Use handlePlayerDisconnect which does cleanup WITHOUT trying to teleport
        // (exitRealm would try to teleport, which fails for disconnecting players)
        realmsManager.handlePlayerDisconnect(playerId);

        LOGGER.atInfo().log("Player %s disconnect cleanup completed for realm %s",
                playerId.toString().substring(0, 8),
                realmId.toString().substring(0, 8));

        // Check if realm is now empty
        checkRealmEmpty(realm);
    }

    /**
     * Checks if a realm is empty and should be closed.
     *
     * @param realm The realm to check
     */
    private void checkRealmEmpty(@Nonnull RealmInstance realm) {
        if (realm.getPlayerCount() <= 0) {
            LOGGER.atInfo().log("Realm %s is now empty, scheduling for closure",
                    realm.getRealmId().toString().substring(0, 8));

            // The realm manager's maintenance system will handle closing empty realms
            // after the configured grace period. We just log here.
        }
    }

    /**
     * Sends an informational message to a player.
     *
     * @param playerRef The player
     * @param message The message
     */
    @SuppressWarnings("unused")
    private void sendInfo(@Nonnull PlayerRef playerRef, @Nonnull String message) {
        Message msg = Message.raw("[Realms] ").color(COLOR_PREFIX)
            .insert(Message.raw(message).color(COLOR_INFO));
        playerRef.sendMessage(msg);
    }

    // ═══════════════════════════════════════════════════════════════════
    // ACCESSORS (for testing)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the realms manager.
     *
     * @return The realms manager
     */
    public RealmsManager getRealmsManager() {
        return realmsManager;
    }
}
