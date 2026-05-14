package io.github.larsonix.trailoforbis.maps.persistence;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import io.github.larsonix.trailoforbis.database.DataManager;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistence hooks for realm lifecycle events.
 *
 * <p>Records realm activity to the database for crash recovery:
 * <ul>
 *   <li>On realm create → insert into rpg_active_realms</li>
 *   <li>On state change → update rpg_active_realms.state</li>
 *   <li>On player enter → insert into rpg_realm_players (with return coords)</li>
 *   <li>On player exit → delete from rpg_realm_players</li>
 *   <li>On realm close → delete from rpg_active_realms</li>
 * </ul>
 *
 * <p>On startup, orphaned players (rows remaining in rpg_realm_players) are
 * detected and returned to their origin world on next login.
 */
public class RealmPersistenceModule {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final RealmStateRepository repository;

    /**
     * Orphaned players detected on startup, keyed by player UUID.
     * Consumed on PlayerReadyEvent — entry removed after teleport.
     */
    private final Map<UUID, RealmStateRepository.OrphanedPlayerState> orphanedPlayers =
            new ConcurrentHashMap<>();

    public RealmPersistenceModule(@Nonnull DataManager dataManager) {
        this.repository = new RealmStateRepository(dataManager);
    }

    // ═══════════════════════════════════════════════════════════════════
    // STARTUP RECOVERY
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Loads orphaned players from the database. Called synchronously during
     * RealmsManager initialization, before players can connect.
     *
     * @return Number of orphaned players detected
     */
    public int loadOrphanedPlayers() {
        List<RealmStateRepository.OrphanedPlayerState> orphans = repository.loadOrphanedPlayers();
        for (var orphan : orphans) {
            orphanedPlayers.put(orphan.playerId(), orphan);
        }

        if (!orphans.isEmpty()) {
            LOGGER.atInfo().log("Detected %d orphaned realm players from previous session — will return on login",
                orphans.size());
        }

        // Clear the DB tables — the orphaned player map is now the source of truth
        repository.clearAll();

        return orphans.size();
    }

    /**
     * Checks if a player is orphaned (was in a realm during last crash).
     * Called on PlayerReadyEvent.
     *
     * @param playerId The player UUID
     * @return The orphan state, or null if not orphaned
     */
    @Nullable
    public RealmStateRepository.OrphanedPlayerState consumeOrphan(@Nonnull UUID playerId) {
        return orphanedPlayers.remove(playerId);
    }

    /**
     * Returns true if there are any orphaned players still waiting for login.
     */
    public boolean hasOrphanedPlayers() {
        return !orphanedPlayers.isEmpty();
    }

    // ═══════════════════════════════════════════════════════════════════
    // LIFECYCLE HOOKS (called by RealmsManager)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Records a realm as active in the database.
     */
    public void onRealmCreated(@Nonnull RealmInstance realm) {
        repository.insertRealm(
            realm.getRealmId(),
            realm.getOwnerId(),
            realm.getWorld() != null ? realm.getWorld().getName() : "unknown",
            realm.getBiome().name(),
            realm.getLevel(),
            realm.getMapData().size().name(),
            realm.getState().name(),
            Timestamp.from(Instant.now()),
            (int) realm.getTimeout().toSeconds(),
            realm.getCompletionTracker().getTotalMonsters()
        );
    }

    /**
     * Records a player entering a realm with their return coordinates.
     *
     * @param playerId The player UUID
     * @param realmId The realm ID
     * @param returnWorldId The world UUID to return to
     * @param returnPos The position to return to
     */
    public void onPlayerEnteredRealm(
            @Nonnull UUID playerId, @Nonnull UUID realmId,
            @Nonnull UUID returnWorldId, @Nonnull Vector3d returnPos) {
        repository.insertPlayer(playerId, realmId, returnWorldId,
            returnPos.getX(), returnPos.getY(), returnPos.getZ());
    }

    /**
     * Removes a player from realm tracking.
     */
    public void onPlayerExitedRealm(@Nonnull UUID playerId) {
        repository.removePlayer(playerId);
    }

    /**
     * Removes a realm record from the database.
     */
    public void onRealmClosed(@Nonnull UUID realmId) {
        repository.deleteRealm(realmId);
    }

    /**
     * Persists current state of all active realms before shutdown.
     * Called during orderly shutdown (not crash — crash recovery uses existing rows).
     */
    public void persistAndClearAll() {
        // On orderly shutdown, clear all tracking — players will be teleported out
        // by the normal shutdown sequence before this is called.
        repository.clearAll();
    }
}
