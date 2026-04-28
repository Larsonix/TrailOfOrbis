package io.github.larsonix.trailoforbis.maps.completion;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;
import io.github.larsonix.trailoforbis.maps.reward.RewardChestManager;
import io.github.larsonix.trailoforbis.maps.ui.RealmHudManager;
import io.github.larsonix.trailoforbis.util.EmoteCelebrationHelper;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrates the victory sequence when a realm is completed.
 *
 * <p>The victory sequence:
 * <ol>
 *   <li>Switches all players from combat HUD to victory HUD</li>
 *   <li>Spawns a victory portal at the realm center</li>
 *   <li>Spawns a reward chest next to the portal</li>
 *   <li>Sends a chat announcement to all players</li>
 * </ol>
 *
 * <p>Players stay in the completed realm indefinitely and leave via the victory
 * portal at their own pace. The realm closes when the last player leaves
 * (handled by {@code RealmsManager.handleEmptyRealm()}).
 *
 * @see RealmHudManager
 * @see VictoryPortalManager
 */
public class RealmVictorySequence {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // ═══════════════════════════════════════════════════════════════════
    // DEPENDENCIES
    // ═══════════════════════════════════════════════════════════════════

    private final RealmHudManager hudManager;
    private final VictoryPortalManager portalManager;
    @Nullable
    private final RewardChestManager rewardChestManager;
    private final EmoteCelebrationHelper emoteHelper;

    /**
     * Tracks ALL players who were shown victory HUDs per realm.
     * This is critical - we need to remove HUDs for all these players at cleanup,
     * not just players still in the realm (some may have left via portal).
     */
    private final Map<UUID, Set<UUID>> victoryPhasePlayers = new ConcurrentHashMap<>();

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a new victory sequence handler.
     *
     * @param hudManager         The HUD manager for player UI
     * @param portalManager      The portal manager for victory portals
     * @param rewardChestManager The reward chest manager (may be null if chest system not active)
     * @param emoteHelper        The emote helper for victory celebrations
     */
    public RealmVictorySequence(
            @Nonnull RealmHudManager hudManager,
            @Nonnull VictoryPortalManager portalManager,
            @Nullable RewardChestManager rewardChestManager,
            @Nonnull EmoteCelebrationHelper emoteHelper) {
        this.hudManager = Objects.requireNonNull(hudManager, "hudManager cannot be null");
        this.portalManager = Objects.requireNonNull(portalManager, "portalManager cannot be null");
        this.rewardChestManager = rewardChestManager;
        this.emoteHelper = Objects.requireNonNull(emoteHelper, "emoteHelper cannot be null");
    }

    // ═══════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Executes the victory sequence for a completed realm.
     *
     * <p>Sets up victory HUDs, portal, reward chest, and chat message.
     * Players can stay indefinitely — the realm closes when the last player
     * leaves via the victory portal or disconnects.
     *
     * @param realm The completed realm instance
     */
    public void execute(@Nonnull RealmInstance realm) {
        Objects.requireNonNull(realm, "realm cannot be null");

        UUID realmId = realm.getRealmId();
        LOGGER.atInfo().log("Starting victory sequence for realm %s",
            realmId.toString().substring(0, 8));

        // 1. Get all current players and track them for HUD cleanup
        Set<UUID> players = realm.getCurrentPlayers();
        if (players.isEmpty()) {
            LOGGER.atFine().log("No players in realm %s - skipping victory sequence",
                realmId.toString().substring(0, 8));
            return;
        }

        // Track all players who enter victory phase - critical for HUD cleanup later
        victoryPhasePlayers.put(realmId, new HashSet<>(players));

        // 2. Switch HUDs for each player (combat -> victory) and trigger emote
        for (UUID playerId : players) {
            PlayerRef playerRef = Universe.get().getPlayer(playerId);
            if (playerRef != null) {
                hudManager.showVictoryHud(playerId, playerRef, realm);

                // Trigger victory emote celebration (if configured)
                if (emoteHelper.isEnabled()) {
                    emoteHelper.playEmote(playerRef);
                }
            }
        }

        // 3. Spawn victory portal at realm center
        portalManager.spawnVictoryPortal(realm)
            .thenAccept(success -> {
                if (success) {
                    LOGGER.atInfo().log("Victory portal spawned for realm %s",
                        realmId.toString().substring(0, 8));
                } else {
                    LOGGER.atWarning().log("Failed to spawn victory portal for realm %s",
                        realmId.toString().substring(0, 8));
                }
            });

        // 3b. Spawn reward chest next to portal
        if (rewardChestManager != null) {
            rewardChestManager.spawnRewardChest(realm)
                .thenAccept(success -> {
                    if (success) {
                        LOGGER.atInfo().log("Reward chest spawned for realm %s",
                            realmId.toString().substring(0, 8));
                    } else {
                        LOGGER.atWarning().log("Failed to spawn reward chest for realm %s",
                            realmId.toString().substring(0, 8));
                    }
                });
        }

        // 4. Send chat announcement
        broadcastVictoryMessage(realm);

        LOGGER.atInfo().log("Victory sequence started for realm %s - players may leave when ready",
            realmId.toString().substring(0, 8));
    }

    /**
     * Cancels an ongoing victory sequence.
     *
     * @param realmId The realm ID to cancel
     */
    public void cancel(@Nonnull UUID realmId) {
        victoryPhasePlayers.remove(realmId);
    }

    /**
     * Cleans up all active victory sequences.
     * Called during shutdown.
     */
    public void shutdown() {
        victoryPhasePlayers.clear();
    }

    // ═══════════════════════════════════════════════════════════════════
    // INTERNAL
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Broadcasts a victory message to all players in the realm.
     */
    private void broadcastVictoryMessage(@Nonnull RealmInstance realm) {
        String exitText = rewardChestManager != null
            ? "Collect your rewards from the chest and use the victory portal to leave when ready."
            : "A victory portal has appeared ! Leave when you are ready.";

        Message msg = Message.empty()
            .insert(Message.raw("[").color(MessageColors.GRAY))
            .insert(Message.raw("Victory").color(MessageColors.GOLD).bold(true))
            .insert(Message.raw("] ").color(MessageColors.GRAY))
            .insert(Message.raw(exitText).color(MessageColors.SUCCESS));

        for (UUID playerId : realm.getCurrentPlayers()) {
            PlayerRef playerRef = Universe.get().getPlayer(playerId);
            if (playerRef != null) {
                playerRef.sendMessage(msg);
            }
        }
    }
}
