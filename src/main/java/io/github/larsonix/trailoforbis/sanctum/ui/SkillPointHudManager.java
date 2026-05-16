package io.github.larsonix.trailoforbis.sanctum.ui;

import au.ellie.hyui.builders.HyUIHud;
import au.ellie.hyui.utils.MultiHudWrapper;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.skilltree.SkillTreeManager;
import io.github.larsonix.trailoforbis.skilltree.api.SkillTreeEvents;
import io.github.larsonix.trailoforbis.ui.hud.HudRefreshHelper;
import io.github.larsonix.trailoforbis.ui.hud.HudToggleService;

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

    @Nullable private HudToggleService hudToggleService;

    /** Registered event listeners (stored for cleanup). */
    @Nullable
    private SkillTreeEvents.NodeAllocatedListener nodeAllocatedListener;
    @Nullable
    private SkillTreeEvents.NodeDeallocatedListener nodeDeallocatedListener;
    @Nullable
    private SkillTreeEvents.RespecListener respecListener;
    @Nullable
    private SkillTreeEvents.RefundPointsChangedListener refundPointsChangedListener;

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

    public void setHudToggleService(@Nullable HudToggleService service) {
        this.hudToggleService = service;
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
            if (hudToggleService != null) hudToggleService.applyToggleState(playerId, hud);
            LOGGER.atInfo().log("Showed skill point HUD for player %s", playerId.toString().substring(0, 8));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to show skill point HUD for player %s",
                playerId.toString().substring(0, 8));
        }
    }

    /**
     * Shows the skill point HUD synchronously on the world thread (no nested world.execute).
     *
     * <p>Used by {@link io.github.larsonix.trailoforbis.ui.hud.HudHealthChecker} when
     * the caller is already on the sanctum world thread and the player is fully loaded.
     * Uses direct {@code MultiHudWrapper.setCustomHud()} + {@code resetHasBuilt()} to
     * guarantee synchronous registration, bypassing {@code safeAdd()}'s
     * {@code getReference()} null check that can fail during transitions.
     *
     * @param playerId  The player's UUID
     * @param playerRef The player reference (fresh, resolved at call time)
     * @param player    The Player component (resolved from entity store)
     * @param store     The entity store
     */
    public void showHudDirect(@Nonnull UUID playerId, @Nonnull PlayerRef playerRef,
                              @Nonnull Player player, @Nonnull Store<EntityStore> store) {
        discardStaleHud(playerId);

        try {
            HyUIHud hud = SkillPointHud.create(playerRef, store, skillTreeManager);
            MultiHudWrapper.setCustomHud(player, playerRef, hud.name, hud);
            HudRefreshHelper.resetHasBuilt(hud);
            activeHuds.put(playerId, hud);
            if (hudToggleService != null) hudToggleService.applyToggleState(playerId, hud);
            LOGGER.atInfo().log("Showed skill point HUD (direct) for player %s",
                playerId.toString().substring(0, 8));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to show skill point HUD (direct) for player %s",
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

        refundPointsChangedListener = (playerId, newRefundPoints) ->
            notifyPointsChanged(playerId);

        skillTreeManager.registerNodeAllocatedListener(nodeAllocatedListener);
        skillTreeManager.registerNodeDeallocatedListener(nodeDeallocatedListener);
        skillTreeManager.registerRespecListener(respecListener);
        skillTreeManager.registerRefundPointsChangedListener(refundPointsChangedListener);
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
        if (refundPointsChangedListener != null) {
            skillTreeManager.unregisterRefundPointsChangedListener(refundPointsChangedListener);
            refundPointsChangedListener = null;
        }
        LOGGER.atInfo().log("Unregistered skill point HUD event listeners");
    }

    // ═══════════════════════════════════════════════════════════════════
    // INTERNAL
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Triggers an immediate HUD refresh for a player after skill point change.
     *
     * <p>Uses {@link HudRefreshHelper#safeRefreshWithToggle} for atomic Clear+Append.
     *
     * @param playerId The player's UUID
     */
    private void notifyPointsChanged(@Nonnull UUID playerId) {
        HyUIHud hud = activeHuds.get(playerId);
        if (hud != null) {
            try {
                HudRefreshHelper.safeRefreshWithToggle(hud, playerId, hudToggleService);
            } catch (Exception e) {
                LOGGER.atFine().withCause(e).log(
                    "Failed to trigger skill point HUD refresh for player %s",
                    playerId.toString().substring(0, 8));
            }
        }
    }

    /** Gets the active HUD for a player, or null. */
    @Nullable
    public HyUIHud getHud(@Nonnull UUID playerId) {
        return activeHuds.get(playerId);
    }

    /**
     * Discards a stale HUD during world transitions WITHOUT sending packets.
     *
     * <p>During world transitions, {@code JoinWorld(clearWorld=true)} clears all
     * client-side HyUI elements. This method only removes from tracking map and
     * cancels refresh tasks via {@link HudRefreshHelper#cancelRefreshTask} —
     * the canonical pattern matching {@code XpBarHudManager} and
     * {@code RealmHudManager.discardHud()}.
     *
     * <p>No explicit MCHUD removal needed: the deterministic name "too-skill-points"
     * ensures the next {@code showHud()} replaces the orphaned entry.
     *
     * @param playerId The player's UUID
     */
    public void discardStaleHud(@Nonnull UUID playerId) {
        HyUIHud hud = activeHuds.remove(playerId);
        if (hud == null) {
            return;
        }

        HudRefreshHelper.cancelRefreshTask(hud);
        LOGGER.atFine().log("Discarded stale skill point HUD for player %s (world transition)",
            playerId.toString().substring(0, 8));
    }

    /**
     * Removes a HUD synchronously.
     */
    private void removeHudSync(@Nonnull UUID playerId) {
        HyUIHud hud = activeHuds.remove(playerId);
        if (hud == null) {
            return;
        }

        try {
            hud.remove();
            LOGGER.atFine().log("Removed skill point HUD for player %s", playerId.toString().substring(0, 8));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to remove skill point HUD for player %s",
                playerId.toString().substring(0, 8));
        }
    }
}
