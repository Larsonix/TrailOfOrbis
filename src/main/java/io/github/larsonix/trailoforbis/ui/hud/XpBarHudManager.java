package io.github.larsonix.trailoforbis.ui.hud;

import au.ellie.hyui.builders.HyUIHud;
import au.ellie.hyui.utils.MultiHudWrapper;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.leveling.api.LevelingEvents;
import io.github.larsonix.trailoforbis.leveling.api.LevelingService;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// HUD toggle service for /hud command visibility toggling

/**
 * Manages the persistent XP bar HUD lifecycle for all players.
 *
 * <p>Unlike realm HUDs which are context-specific, the XP bar is shown to
 * every player as soon as they join and remains visible until disconnect.
 *
 * <h2>Thread Safety</h2>
 * <p>Uses ConcurrentHashMap for the HUD tracking map. HUD creation and
 * removal are performed on the world thread via {@code world.execute()}.
 *
 * <h2>Full Rerender on XP Change</h2>
 * <p>Uses {@link HudRefreshHelper} for safe atomic rerenders (Clear+Append)
 * instead of diff-based Set commands that can crash clients with stale element IDs.
 *
 * @see XpBarHud
 */
public class XpBarHudManager implements PersistentHud {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // ═══════════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════════

    /** Active XP bar HUDs keyed by player UUID. */
    private final Map<UUID, HyUIHud> activeHuds = new ConcurrentHashMap<>();

    /** The leveling service for XP/level queries. */
    private final LevelingService levelingService;

    /** HUD toggle service — hides new/refreshed HUDs when player has toggled off. */
    @Nullable private HudToggleService hudToggleService;

    /** Registered XP gain listener (stored for cleanup). */
    @Nullable
    private LevelingEvents.XpGainListener xpGainListener;

    /** Registered level-up listener (stored for cleanup). */
    @Nullable
    private LevelingEvents.LevelUpListener levelUpListener;

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a new XP bar HUD manager.
     *
     * @param levelingService The leveling service for XP/level data
     */
    public XpBarHudManager(@Nonnull LevelingService levelingService) {
        this.levelingService = levelingService;
    }

    public void setHudToggleService(@Nullable HudToggleService service) {
        this.hudToggleService = service;
    }

    // ═══════════════════════════════════════════════════════════════════
    // SHOW / REMOVE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Shows the XP bar HUD for a player.
     *
     * <p>Must be called from the world thread (inside {@code world.execute()}).
     *
     * @param playerId  The player's UUID
     * @param playerRef The player reference
     * @param store     The entity store
     * @param player    The Player component for direct MultiHud registration (null for safety-net paths)
     */
    public void showHud(@Nonnull UUID playerId, @Nonnull PlayerRef playerRef,
                        @Nonnull Store<EntityStore> store, @Nullable Player player) {
        // Discard any existing HUD — NOT hide()+remove() which sends Set commands
        // to elements that may have been cleared by JoinWorld(clearWorld=true) during world transition.
        discardStaleHud(playerId);

        try {
            HyUIHud hud = XpBarHud.create(playerRef, store, levelingService);
            activeHuds.put(playerId, hud);

            // Direct MultiHud registration — bypasses HyUI safeAdd()'s internal
            // getReference() check which returns null during early world transitions.
            // The event Player is guaranteed valid at PlayerReadyEvent time.
            // HyUI's deferred safeAdd() still runs next tick as a harmless redundant rebuild.
            if (player != null) {
                MultiHudWrapper.setCustomHud(player, playerRef, hud.name, hud);
            }

            if (hudToggleService != null) hudToggleService.applyToggleState(playerId, hud);
            LOGGER.atInfo().log("Showed XP bar HUD for player %s (direct=%b)",
                playerId.toString().substring(0, 8), player != null);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to show XP bar HUD for player %s",
                playerId.toString().substring(0, 8));
        }
    }

    /**
     * Shows the XP bar HUD without direct MultiHud registration.
     * Used by non-restore callers where safeAdd() timing is acceptable.
     */
    public void showHud(@Nonnull UUID playerId, @Nonnull PlayerRef playerRef,
                        @Nonnull Store<EntityStore> store) {
        showHud(playerId, playerRef, store, null);
    }

    /**
     * Removes the XP bar HUD for a player.
     *
     * <p>Safe to call from any thread — uses hide + remove pattern
     * for immediate visual update before removal.
     *
     * @param playerId The player's UUID
     */
    public void removeHud(@Nonnull UUID playerId) {
        removeHudSync(playerId);
    }

    /**
     * Removes all active XP bar HUDs. Called during plugin shutdown.
     */
    public void removeAllHuds() {
        int count = activeHuds.size();
        if (count > 0) {
            LOGGER.atInfo().log("Removing all XP bar HUDs (%d total)", count);
            for (UUID playerId : Set.copyOf(activeHuds.keySet())) {
                removeHudSync(playerId);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // EVENT LISTENERS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Registers XP and level-up listeners on the leveling service.
     *
     * <p>When XP is gained or a level-up occurs, the corresponding player's
     * HUD is immediately refreshed via full rerender, pushing an update
     * packet without waiting for any polling interval.
     *
     * @param service The leveling service to register on
     */
    public void registerEventListeners(@Nonnull LevelingService service) {
        xpGainListener = (playerId, amount, source, totalXp) -> notifyXpChanged(playerId);
        levelUpListener = (playerId, newLevel, oldLevel, totalXp) -> notifyXpChanged(playerId);

        service.registerXpGainListener(xpGainListener);
        service.registerLevelUpListener(levelUpListener);
        LOGGER.atInfo().log("Registered XP bar event listeners on leveling service");
    }

    /**
     * Unregisters previously registered listeners from the leveling service.
     *
     * @param service The leveling service to unregister from
     */
    public void unregisterEventListeners(@Nonnull LevelingService service) {
        if (xpGainListener != null) {
            service.unregisterXpGainListener(xpGainListener);
            xpGainListener = null;
        }
        if (levelUpListener != null) {
            service.unregisterLevelUpListener(levelUpListener);
            levelUpListener = null;
        }
        LOGGER.atInfo().log("Unregistered XP bar event listeners");
    }

    /**
     * Triggers an immediate HUD refresh for a player after XP or level change.
     *
     * <p>Uses {@link HudRefreshHelper#safeRefreshWithToggle} for atomic Clear+Append.
     *
     * @param playerId The player's UUID
     */
    private void notifyXpChanged(@Nonnull UUID playerId) {
        HyUIHud hud = activeHuds.get(playerId);
        if (hud == null) {
            return;
        }

        try {
            HudRefreshHelper.safeRefreshWithToggle(hud, playerId, hudToggleService);
        } catch (Exception e) {
            LOGGER.atFine().withCause(e).log(
                "Failed to trigger XP bar refresh for player %s", playerId.toString().substring(0, 8));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // INTERNAL
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Removes a HUD synchronously using the hide + remove pattern.
     *
     * <p><b>Do NOT use during world transitions!</b> Use {@link #discardStaleHud(UUID)}
     * instead. During world transitions, {@code JoinWorld(clearWorld=true)} has already
     * sent {@code CustomHud(clear=true)} to the client, destroying all HyUI elements.
     * Calling {@code hide()} sends {@code Set} commands referencing those cleared
     * elements, crashing the client.
     */
    private void removeHudSync(@Nonnull UUID playerId) {
        HyUIHud hud = activeHuds.remove(playerId);
        if (hud == null) {
            return;
        }

        try {
            hud.remove();
            LOGGER.atFine().log("Removed XP bar HUD for player %s", playerId.toString().substring(0, 8));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to remove XP bar HUD for player %s",
                playerId.toString().substring(0, 8));
        }
    }

    /**
     * Discards a stale HUD during world transitions WITHOUT sending packets.
     *
     * <p>Cancels the refresh task directly via reflection. HyUI's {@code hud.remove()}
     * early-returns when {@code getStore()} returns null during transitions, skipping
     * both {@code hideCustomHud} and {@code refreshTask.cancel()}. No MHUD cleanup
     * needed: {@code JoinWorld(clearWorld=true)} clears the wrapper, and {@code restoreAll()}
     * creates a fresh one.
     *
     * @param playerId The player's UUID
     */
    public void discardStaleHud(@Nonnull UUID playerId) {
        HyUIHud hud = activeHuds.remove(playerId);
        if (hud == null) {
            return;
        }

        HudRefreshHelper.cancelRefreshTask(hud);
        LOGGER.atFine().log("Discarded stale XP bar HUD for player %s (world transition)",
            playerId.toString().substring(0, 8));
    }

    // ═══════════════════════════════════════════════════════════════════
    // QUERIES
    // ═══════════════════════════════════════════════════════════════════

    /** Gets the active HUD for a player, or null. */
    @Nullable
    public HyUIHud getHud(@Nonnull UUID playerId) {
        return activeHuds.get(playerId);
    }

    /** Checks if a player has an active XP bar HUD. */
    public boolean hasHud(@Nonnull UUID playerId) {
        return activeHuds.containsKey(playerId);
    }

    /** Gets the count of active XP bar HUDs. */
    public int getActiveCount() {
        return activeHuds.size();
    }

    // ═══════════════════════════════════════════════════════════════════
    // PersistentHud INTERFACE
    // ═══════════════════════════════════════════════════════════════════

    @Nonnull
    @Override
    public String hudName() {
        return "xp-bar";
    }

    @Override
    public void discardStale(@Nonnull UUID playerId) {
        discardStaleHud(playerId);
    }

    @Override
    public boolean isActive(@Nonnull UUID playerId) {
        return hasHud(playerId);
    }

    @Override
    public void restore(@Nonnull UUID playerId, @Nonnull PlayerRef playerRef,
                        @Nonnull Store<EntityStore> store) {
        showHud(playerId, playerRef, store, null);
    }

    @Override
    public void restore(@Nonnull UUID playerId, @Nonnull PlayerRef playerRef,
                        @Nonnull Store<EntityStore> store, @Nullable Player player) {
        showHud(playerId, playerRef, store, player);
    }

    @Override
    public void removeOnDisconnect(@Nonnull UUID playerId) {
        removeHud(playerId);
    }

    @Override
    public void shutdown() {
        removeAllHuds();
    }
}
