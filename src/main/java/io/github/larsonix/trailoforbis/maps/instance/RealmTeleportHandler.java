package io.github.larsonix.trailoforbis.maps.instance;

import com.hypixel.hytale.builtin.instances.InstancesPlugin;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.maps.event.RealmPlayerEnteredEvent;
import io.github.larsonix.trailoforbis.maps.event.RealmPlayerExitedEvent;
import io.github.larsonix.trailoforbis.maps.templates.RealmTemplate;
import io.github.larsonix.trailoforbis.maps.RealmsManager;
import io.github.larsonix.trailoforbis.maps.ui.RealmHudManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * Handles player teleportation into and out of realm instances.
 *
 * <p>This class wraps Hytale's InstancesPlugin teleportation methods and
 * integrates them with the realm lifecycle events.
 *
 * @see InstancesPlugin
 * @see RealmInstance
 */
public class RealmTeleportHandler {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /**
     * Callback for when a player enters/exits a realm.
     */
    @Nullable
    private BiConsumer<UUID, RealmInstance> onPlayerEnterCallback;

    @Nullable
    private BiConsumer<UUID, RealmInstance> onPlayerExitCallback;

    /**
     * Sets the callback for when a player enters a realm.
     *
     * @param callback The callback function
     */
    public void setOnPlayerEnter(@Nullable BiConsumer<UUID, RealmInstance> callback) {
        this.onPlayerEnterCallback = callback;
    }

    /**
     * Sets the callback for when a player exits a realm.
     *
     * @param callback The callback function
     */
    public void setOnPlayerExit(@Nullable BiConsumer<UUID, RealmInstance> callback) {
        this.onPlayerExitCallback = callback;
    }

    /**
     * Teleports a player into a realm instance.
     *
     * @param playerRef The player to teleport
     * @param realm The target realm
     * @param returnLocation The location to return to when exiting
     * @return A future that completes when teleportation is done
     */
    @Nonnull
    public CompletableFuture<Boolean> teleportIntoRealm(
            @Nonnull PlayerRef playerRef,
            @Nonnull RealmInstance realm,
            @Nonnull Transform returnLocation) {

        Objects.requireNonNull(playerRef, "PlayerRef cannot be null");
        Objects.requireNonNull(realm, "Realm cannot be null");
        Objects.requireNonNull(returnLocation, "Return location cannot be null");

        World realmWorld = realm.getWorld();
        if (realmWorld == null || !realmWorld.isAlive()) {
            LOGGER.atWarning().log("Cannot teleport player %s to realm %s - world not ready",
                playerRef.getUuid(), realm.getRealmId());
            return CompletableFuture.completedFuture(false);
        }

        if (!realm.allowsEntry()) {
            LOGGER.atWarning().log("Cannot teleport player %s to realm %s - entry not allowed in state %s",
                playerRef.getUuid(), realm.getRealmId(), realm.getState());
            return CompletableFuture.completedFuture(false);
        }

        UUID playerId = playerRef.getUuid();
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        Ref<EntityStore> entityRef = playerRef.getReference();
        if (!entityRef.isValid()) {
            LOGGER.atWarning().log("Player %s entity ref is not valid", playerId);
            return CompletableFuture.completedFuture(false);
        }

        // Get current world for return point handling
        UUID worldUuid = playerRef.getWorldUuid();
        World currentWorld = worldUuid != null ? Universe.get().getWorld(worldUuid) : null;
        if (currentWorld == null) {
            LOGGER.atWarning().log("Player %s is not in any world", playerId);
            return CompletableFuture.completedFuture(false);
        }

        currentWorld.execute(() -> {
            try {
                Store<EntityStore> store = currentWorld.getEntityStore().getStore();

                // Use InstancesPlugin's direct teleport for already-existing worlds.
                // CRITICAL: teleportPlayerToLoadingInstance is for NOT-YET-CREATED worlds
                // (takes a CompletableFuture<World>). It queues removeFromStore() then chains
                // addPlayer() on the future. With a pre-completed future, thenCompose runs
                // inline — calling addPlayer() BEFORE removeFromStore() executes. World.addPlayer()
                // throws "Player is already in a world" because the player hasn't been removed yet.
                // teleportPlayerToInstance uses Hytale's Teleport component, which the TeleportSystem
                // processes correctly on the next tick (same pattern as SkillSanctumInstance).
                InstancesPlugin.teleportPlayerToInstance(
                    entityRef,
                    store,
                    realmWorld,
                    returnLocation
                );

                // Mark player as entered (will be tracked by listener)
                boolean isFirst = realm.getPlayerCount() == 0;
                realm.onPlayerEnter(playerId);

                // Notify callback
                if (onPlayerEnterCallback != null) {
                    onPlayerEnterCallback.accept(playerId, realm);
                }

                LOGGER.atInfo().log("Teleported player %s into realm %s (first: %s)",
                    playerId, realm.getRealmId(), isFirst);

                future.complete(true);

            } catch (Exception e) {
                LOGGER.atSevere().withCause(e).log("Failed to teleport player %s to realm %s",
                    playerId, realm.getRealmId());
                future.complete(false);
            }
        });

        return future;
    }

    /**
     * Teleports a player out of a realm instance.
     *
     * @param playerRef The player to teleport
     * @param realm The realm they're leaving
     * @param reason The reason for exiting
     * @return A future that completes when teleportation is done
     */
    @Nonnull
    public CompletableFuture<Boolean> teleportOutOfRealm(
            @Nonnull PlayerRef playerRef,
            @Nonnull RealmInstance realm,
            @Nonnull RealmPlayerExitedEvent.ExitReason reason) {

        Objects.requireNonNull(playerRef, "PlayerRef cannot be null");
        Objects.requireNonNull(realm, "Realm cannot be null");
        Objects.requireNonNull(reason, "Exit reason cannot be null");

        World realmWorld = realm.getWorld();
        if (realmWorld == null || !realmWorld.isAlive()) {
            LOGGER.atWarning().log("Cannot exit player %s from realm %s - world not available",
                playerRef.getUuid(), realm.getRealmId());
            return CompletableFuture.completedFuture(false);
        }

        UUID playerId = playerRef.getUuid();
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        Ref<EntityStore> entityRef = playerRef.getReference();
        if (!entityRef.isValid()) {
            LOGGER.atWarning().log("Player %s entity ref is not valid", playerId);
            return CompletableFuture.completedFuture(false);
        }

        realmWorld.execute(() -> {
            try {
                Store<EntityStore> store = realmWorld.getEntityStore().getStore();

                // CRITICAL: Remove HUDs BEFORE exit to ensure visual update
                // DrainPlayerFromWorldEvent may race with world closure, so we do it explicitly
                // See .claude/rules/hyui-hud-cleanup.md for pattern details
                try {
                    RealmHudManager hudManager = RealmsManager.get().getHudManager();
                    hudManager.removeAllHudsForPlayerSync(playerId);
                } catch (Exception e) {
                    LOGGER.atFine().withCause(e).log("HUD cleanup skipped for %s (manager not available)", playerId);
                }

                // Use InstancesPlugin's exit method
                InstancesPlugin.exitInstance(entityRef, store);

                // Mark player as left
                boolean wasLast = realm.getPlayerCount() == 1;
                realm.onPlayerLeave(playerId);

                // Notify callback
                if (onPlayerExitCallback != null) {
                    onPlayerExitCallback.accept(playerId, realm);
                }

                LOGGER.atInfo().log("Teleported player %s out of realm %s (reason: %s, last: %s)",
                    playerId, realm.getRealmId(), reason, wasLast);

                future.complete(true);

            } catch (Exception e) {
                LOGGER.atSevere().withCause(e).log("Failed to exit player %s from realm %s",
                    playerId, realm.getRealmId());
                future.complete(false);
            }
        });

        return future;
    }

    /**
     * Teleports all players out of a realm.
     *
     * @param realm The realm to evacuate
     * @param reason The reason for evacuation
     * @return A future that completes when all players are out
     */
    @Nonnull
    public CompletableFuture<Integer> evacuateRealm(
            @Nonnull RealmInstance realm,
            @Nonnull RealmPlayerExitedEvent.ExitReason reason) {

        Objects.requireNonNull(realm, "Realm cannot be null");

        var players = realm.getCurrentPlayers();
        if (players.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }

        CompletableFuture<Integer> result = new CompletableFuture<>();
        int[] evacuated = {0};
        int[] pending = {players.size()};

        for (UUID playerId : players) {
            PlayerRef playerRef = Universe.get().getPlayer(playerId);
            if (playerRef == null) {
                pending[0]--;
                // Player already disconnected - still try to clean up HUDs
                try {
                    RealmHudManager hudManager = RealmsManager.get().getHudManager();
                    hudManager.removeAllHudsForPlayerSync(playerId);
                } catch (Exception e) {
                    LOGGER.atFine().withCause(e).log("HUD cleanup skipped for disconnected %s", playerId);
                }
                realm.onPlayerLeave(playerId);
                continue;
            }

            teleportOutOfRealm(playerRef, realm, reason).thenAccept(success -> {
                if (success) {
                    evacuated[0]++;
                }
                pending[0]--;
                if (pending[0] <= 0) {
                    result.complete(evacuated[0]);
                }
            });
        }

        if (pending[0] <= 0) {
            result.complete(evacuated[0]);
        }

        return result;
    }

    /**
     * Gets the spawn point for entering a realm.
     *
     * @param realm The target realm
     * @return The spawn point transform
     */
    @Nonnull
    public Transform getRealmSpawnPoint(@Nonnull RealmInstance realm) {
        RealmTemplate template = realm.getTemplate();
        return template.playerSpawn();
    }

    /**
     * Creates a return transform for a given position.
     *
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @return A Transform for the return point
     */
    @Nonnull
    public static Transform createReturnPoint(double x, double y, double z) {
        return new Transform(
            new Vector3d(x, y, z),
            new Vector3f(0, 0, 0)
        );
    }
}
