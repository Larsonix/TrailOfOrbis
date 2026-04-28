package io.github.larsonix.trailoforbis.maps.listeners;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.maps.RealmsManager;
import io.github.larsonix.trailoforbis.maps.components.RealmPlayerComponent;
import io.github.larsonix.trailoforbis.maps.config.RealmsConfig;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * ECS system that handles player deaths within realms.
 *
 * <p>Extends {@link DeathSystems.OnDeathSystem} to react when any entity dies.
 * Filters to only process players that have a {@link RealmPlayerComponent}.
 *
 * <p>When a player dies in a realm:
 * <ul>
 *   <li>Records the death in their statistics</li>
 *   <li>Optionally applies realm-specific death penalties</li>
 *   <li>Checks if the realm should fail (if no players left)</li>
 *   <li>Handles respawn logic based on realm configuration</li>
 * </ul>
 *
 * <p>Note: This system doesn't prevent vanilla death behavior (item drops, etc.).
 * It layers realm-specific logic on top.
 *
 * @see RealmPlayerComponent
 * @see RealmMobDeathListener
 */
public class RealmPlayerDeathListener extends DeathSystems.OnDeathSystem {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final TrailOfOrbis plugin;

    // Component types for querying
    private final ComponentType<EntityStore, Player> playerType;
    private final ComponentType<EntityStore, PlayerRef> playerRefType;
    private final ComponentType<EntityStore, RealmPlayerComponent> realmPlayerType;

    /**
     * Creates a new realm player death listener.
     *
     * @param plugin The TrailOfOrbis plugin instance
     */
    public RealmPlayerDeathListener(@Nonnull TrailOfOrbis plugin) {
        this.plugin = plugin;
        this.playerType = Player.getComponentType();
        this.playerRefType = PlayerRef.getComponentType();
        this.realmPlayerType = RealmPlayerComponent.getComponentType();
    }

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        // Match ALL entity deaths - we filter to players in onComponentAdded
        return Archetype.empty();
    }

    @Override
    public void onComponentAdded(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull DeathComponent deathComponent,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        // Skip if not a player
        Player player = store.getComponent(ref, playerType);
        if (player == null) {
            return;
        }

        // Skip if player not in a realm
        RealmPlayerComponent realmPlayer = store.getComponent(ref, realmPlayerType);
        if (realmPlayer == null || realmPlayer.getRealmId() == null) {
            return;
        }

        // Get player UUID
        PlayerRef playerRef = store.getComponent(ref, playerRefType);
        if (playerRef == null) {
            return;
        }

        UUID playerId = playerRef.getUuid();
        UUID realmId = realmPlayer.getRealmId();

        LOGGER.atInfo().log("Player %s died in realm %s",
                playerId.toString().substring(0, 8),
                realmId.toString().substring(0, 8));

        // Get realm instance
        RealmsManager manager = plugin.getRealmsManager();
        if (manager == null) {
            LOGGER.atWarning().log("RealmsManager not available - skipping death handling");
            return;
        }

        Optional<RealmInstance> realmOpt = manager.getRealm(realmId);
        if (realmOpt.isEmpty()) {
            LOGGER.atFine().log("Realm %s no longer exists - skipping death handling",
                realmId.toString().substring(0, 8));
            return;
        }

        RealmInstance realm = realmOpt.get();

        // Handle the death
        handlePlayerDeath(playerId, playerRef, realm, realmPlayer);
    }

    /**
     * Handles a player's death in a realm.
     *
     * @param playerId The player's UUID
     * @param playerRef The player reference (for messaging)
     * @param realm The realm instance
     * @param realmPlayer The player's realm component
     */
    private void handlePlayerDeath(
            @Nonnull UUID playerId,
            @Nonnull PlayerRef playerRef,
            @Nonnull RealmInstance realm,
            @Nonnull RealmPlayerComponent realmPlayer) {

        // Increment death count and mark as dead
        realmPlayer.incrementDeathCount();
        realmPlayer.markDead();
        realm.markPlayerDead(playerId);

        int deathCount = realmPlayer.getDeathCount();

        LOGGER.atInfo().log("Player %s death count in realm: %d",
                playerId.toString().substring(0, 8), deathCount);

        // Get config and apply death policy
        RealmsManager manager = plugin.getRealmsManager();
        RealmsConfig config = manager.getConfig();
        RealmsConfig.DeathPolicy policy = config.getDeathPolicy();

        switch (policy) {
            case KICK_ON_DEATH -> {
                // ExitInstance respawn controller in Realm_Default GameplayConfig handles the exit
                // when the player clicks Respawn. We just log and send a message here.
                LOGGER.atInfo().log("Death policy KICK_ON_DEATH: player %s will exit realm on respawn",
                        playerId.toString().substring(0, 8));
                sendMessage(playerRef, MessageColors.ERROR, "You have died and been removed from the realm !");
            }
            case LIMITED_LIVES -> {
                // Check if player exceeded max deaths
                int maxDeaths = config.getMaxDeaths();
                if (deathCount >= maxDeaths) {
                    LOGGER.atInfo().log("Player %s exceeded max deaths (%d/%d) - will exit realm on respawn",
                            playerId.toString().substring(0, 8), deathCount, maxDeaths);
                    sendMessage(playerRef, MessageColors.ERROR,
                            String.format("You have used all %d lives and been removed from the realm !", maxDeaths));
                    // ExitInstance respawn controller handles the exit when player clicks Respawn
                } else {
                    int livesRemaining = maxDeaths - deathCount;
                    sendMessage(playerRef, "#FFAA00",
                            String.format("You died! %d %s remaining.",
                                    livesRemaining, livesRemaining == 1 ? "life" : "lives"));
                    // Player will respawn - mark as alive after respawn
                    // Note: A separate respawn listener should call realmPlayer.markAlive() and realm.markPlayerAlive()
                }
            }
            case RESPAWN_IN_REALM, SOFTCORE -> {
                // No penalty - player respawns normally
                // The respawn listener will handle marking player alive again
                LOGGER.atFine().log("Death policy %s: allowing normal respawn for player %s",
                        policy, playerId.toString().substring(0, 8));
            }
        }

        // Check if realm should fail (all players dead)
        checkRealmFailure(realm, manager);
    }

    /**
     * Sends a colored message to a player.
     */
    private void sendMessage(@Nonnull PlayerRef playerRef, @Nonnull String color, @Nonnull String text) {
        Message msg = Message.raw("[Realms] ").color(MessageColors.DARK_PURPLE)
                .insert(Message.raw(text).color(color));
        playerRef.sendMessage(msg);
    }

    /**
     * Checks realm state after a player death.
     *
     * <p>Priority order:
     * <ol>
     *   <li>If objectives are complete (all mobs killed), trigger victory</li>
     *   <li>Otherwise, let the main realm timer handle failure</li>
     * </ol>
     *
     * <p>With re-entry allowed, "all players dead" or "realm empty" is NOT a failure
     * condition. The realm timer continues counting down even when empty, and players
     * can re-enter through the portal. Only when the main timer expires does the realm
     * actually fail with a TIMEOUT reason.
     *
     * @param realm The realm to check
     * @param manager The realms manager (for triggering completion)
     */
    private void checkRealmFailure(@Nonnull RealmInstance realm, @Nonnull RealmsManager manager) {
        // PRIORITY 1: Check if objectives are complete (it's a WIN!)
        if (realm.getCompletionTracker().getRemainingMonsters() == 0) {
            LOGGER.atInfo().log("Objectives complete in realm %s - triggering completion (not failure)",
                realm.getRealmId().toString().substring(0, 8));
            // Only trigger if still in ACTIVE state (avoid double-trigger)
            if (realm.isActive()) {
                manager.triggerCompletion(realm);
            }
            return;
        }

        // With re-entry allowed: "all players dead" or "realm empty" is NOT a failure condition
        // The realm timer continues counting down even when empty
        // RealmsManager.checkRealmTimeouts() handles timeout failure

        if (realm.isEmpty()) {
            LOGGER.atInfo().log("Realm %s is now empty - timer continues (remaining: %s)",
                realm.getRealmId().toString().substring(0, 8),
                formatDuration(realm.getRemainingTime()));
        } else {
            LOGGER.atFine().log("Players in realm %s: %d alive, %d dead - realm continues",
                realm.getRealmId().toString().substring(0, 8),
                realm.getAlivePlayerCount(),
                realm.getCurrentPlayers().size() - realm.getAlivePlayerCount());
        }

        // DO NOT call forceClose(ABANDONED) - let the main timer handle failure
    }

    /**
     * Formats a duration as MM:SS for logging.
     */
    private String formatDuration(Duration d) {
        long minutes = d.toMinutes();
        long seconds = d.toSecondsPart();
        return String.format("%d:%02d", minutes, seconds);
    }

    // ═══════════════════════════════════════════════════════════════════
    // ACCESSORS (for testing)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the plugin instance.
     *
     * @return The plugin
     */
    public TrailOfOrbis getPlugin() {
        return plugin;
    }
}
