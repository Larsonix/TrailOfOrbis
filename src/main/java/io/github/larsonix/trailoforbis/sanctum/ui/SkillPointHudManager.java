package io.github.larsonix.trailoforbis.sanctum.ui;

import au.ellie.hyui.builders.HyUIHud;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.skilltree.SkillTreeManager;
import io.github.larsonix.trailoforbis.skilltree.api.SkillTreeEvents;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the skill point allocation HUD lifecycle for players in the Skill Sanctum.
 *
 * <p>Shows/removes the HUD when players enter/leave the sanctum, and triggers
 * instant refreshes when skill tree events fire (node allocation, deallocation, respec).
 *
 * <h2>Thread Safety</h2>
 * <p>Uses ConcurrentHashMap for the HUD tracking map. HUD creation and removal
 * are performed on the world thread. Event listeners may fire from any thread
 * but only call {@code triggerRefresh()} which is thread-safe.
 *
 * @see SkillPointHud
 */
public class SkillPointHudManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // ═══════════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════════

    /** Active skill point HUDs keyed by player UUID. */
    private final Map<UUID, HyUIHud> activeHuds = new ConcurrentHashMap<>();

    /** The skill tree manager for data queries. */
    private final SkillTreeManager skillTreeManager;

    /** Registered event listeners (stored for cleanup). */
    @Nullable
    private SkillTreeEvents.NodeAllocatedListener nodeAllocatedListener;
    @Nullable
    private SkillTreeEvents.NodeDeallocatedListener nodeDeallocatedListener;
    @Nullable
    private SkillTreeEvents.RespecListener respecListener;

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a new skill point HUD manager.
     *
     * @param skillTreeManager The skill tree manager for point data
     */
    public SkillPointHudManager(@Nonnull SkillTreeManager skillTreeManager) {
        this.skillTreeManager = skillTreeManager;
    }

    // ═══════════════════════════════════════════════════════════════════
    // SHOW / REMOVE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Shows the skill point HUD for a player.
     *
     * <p>Must be called from the world thread (inside {@code world.execute()}).
     *
     * @param playerId  The player's UUID
     * @param playerRef The player reference
     * @param store     The entity store
     */
    public void showHud(@Nonnull UUID playerId, @Nonnull PlayerRef playerRef,
                        @Nonnull Store<EntityStore> store) {
        // Discard any existing HUD — NOT hide()+remove() which sends Set commands
        // to elements that may have been cleared by resetManagers() during world transition.
        discardStaleHud(playerId);

        try {
            HyUIHud hud = SkillPointHud.create(playerRef, store, skillTreeManager);
            activeHuds.put(playerId, hud);
            LOGGER.atInfo().log("Showed skill point HUD for player %s", playerId.toString().substring(0, 8));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to show skill point HUD for player %s",
                playerId.toString().substring(0, 8));
        }
    }

    /**
     * Removes the skill point HUD for a player.
     *
     * <p>Uses hide + remove pattern for immediate visual update before removal.
     *
     * @param playerId The player's UUID
     */
    public void removeHud(@Nonnull UUID playerId) {
        removeHudSync(playerId);
    }

    /**
     * Removes all active skill point HUDs. Called during plugin shutdown.
     */
    public void removeAllHuds() {
        int count = activeHuds.size();
        if (count > 0) {
            LOGGER.atInfo().log("Removing all skill point HUDs (%d total)", count);
            for (UUID playerId : Set.copyOf(activeHuds.keySet())) {
                removeHudSync(playerId);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // EVENT LISTENERS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Registers skill tree event listeners for instant HUD refreshes.
     *
     * <p>When a node is allocated, deallocated, or a respec occurs, the
     * corresponding player's HUD is immediately refreshed via
     * {@code triggerRefresh()}.
     */
    public void registerEventListeners() {
        nodeAllocatedListener = (playerId, node, pointsRemaining, statsBefore, statsAfter) ->
            notifyPointsChanged(playerId);

        nodeDeallocatedListener = (playerId, node, pointsRefunded, pointsRemaining, refundPointsRemaining, statsBefore, statsAfter) ->
            notifyPointsChanged(playerId);

        respecListener = (playerId, nodesCleared, pointsRefunded, freeRespecsRemaining) ->
            notifyPointsChanged(playerId);

        skillTreeManager.registerNodeAllocatedListener(nodeAllocatedListener);
        skillTreeManager.registerNodeDeallocatedListener(nodeDeallocatedListener);
        skillTreeManager.registerRespecListener(respecListener);
        LOGGER.atInfo().log("Registered skill point HUD event listeners");
    }

    /**
     * Unregisters previously registered skill tree event listeners.
     */
    public void unregisterEventListeners() {
        if (nodeAllocatedListener != null) {
            skillTreeManager.unregisterNodeAllocatedListener(nodeAllocatedListener);
            nodeAllocatedListener = null;
        }
        if (nodeDeallocatedListener != null) {
            skillTreeManager.unregisterNodeDeallocatedListener(nodeDeallocatedListener);
            nodeDeallocatedListener = null;
        }
        if (respecListener != null) {
            skillTreeManager.unregisterRespecListener(respecListener);
            respecListener = null;
        }
        LOGGER.atInfo().log("Unregistered skill point HUD event listeners");
    }

    // ═══════════════════════════════════════════════════════════════════
    // INTERNAL
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Triggers an immediate HUD refresh for a player after skill point change.
     *
     * @param playerId The player's UUID
     */
    private void notifyPointsChanged(@Nonnull UUID playerId) {
        HyUIHud hud = activeHuds.get(playerId);
        if (hud != null) {
            try {
                hud.triggerRefresh();                  // Update builder values in memory
                hud.refreshOrRerender(false, false);   // Push diff update to client immediately
            } catch (Exception e) {
                LOGGER.atFine().withCause(e).log(
                    "Failed to trigger skill point HUD refresh for player %s",
                    playerId.toString().substring(0, 8));
            }
        }
    }

    /**
     * Discards a stale HUD during world transitions WITHOUT sending packets.
     *
     * <p>During world transitions, {@code Player.resetManagers()} already sends
     * {@code CustomHud(clear=true)} which destroys all HyUI elements on the client.
     * This method only removes from tracking map and cancels refresh tasks
     * (via {@code remove()} which skips the crash-causing {@code hide()} call).
     *
     * @param playerId The player's UUID
     */
    public void discardStaleHud(@Nonnull UUID playerId) {
        HyUIHud hud = activeHuds.remove(playerId);
        if (hud == null) {
            return;
        }

        try {
            hud.remove();
            LOGGER.atFine().log("Discarded stale skill point HUD for player %s (world transition)",
                playerId.toString().substring(0, 8));
        } catch (Exception e) {
            LOGGER.atFine().log("Failed to discard stale skill point HUD for player %s: %s",
                playerId.toString().substring(0, 8), e.getMessage());
        }
    }

    /**
     * Removes a HUD synchronously using the hide + remove pattern.
     */
    private void removeHudSync(@Nonnull UUID playerId) {
        HyUIHud hud = activeHuds.remove(playerId);
        if (hud == null) {
            return;
        }

        try {
            hud.hide();
            hud.remove();
            LOGGER.atFine().log("Removed skill point HUD for player %s", playerId.toString().substring(0, 8));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to remove skill point HUD for player %s",
                playerId.toString().substring(0, 8));
        }
    }
}
