package io.github.larsonix.trailoforbis.ui.hud;

import au.ellie.hyui.builders.HyUIHud;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.leveling.api.LevelingEvents;
import io.github.larsonix.trailoforbis.leveling.api.LevelingService;
import io.github.larsonix.trailoforbis.maps.ui.RealmHudManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
 * <p>Uses {@link RealmHudManager#resetHasBuilt(HyUIHud)} +
 * {@code refreshOrRerender(true, true)} to force full Append-based rerender
 * instead of diff-based Set commands. This prevents client crashes when the
 * MultipleHUD mod rebuilds the DOM and invalidates auto-generated element IDs.
 *
 * @see XpBarHud
 */
public class XpBarHudManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // ═══════════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════════

    /** Active XP bar HUDs keyed by player UUID. */
    private final Map<UUID, HyUIHud> activeHuds = new ConcurrentHashMap<>();

    /** The leveling service for XP/level queries. */
    private final LevelingService levelingService;

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
     */
    public void showHud(@Nonnull UUID playerId, @Nonnull PlayerRef playerRef,
                        @Nonnull Store<EntityStore> store) {
        // Discard any existing HUD — NOT hide()+remove() which sends Set commands
        // to elements that may have been cleared by resetManagers() during world transition.
        discardStaleHud(playerId);

        try {
            HyUIHud hud = XpBarHud.create(playerRef, store, levelingService);
            activeHuds.put(playerId, hud);
            LOGGER.atInfo().log("Showed XP bar HUD for player %s", playerId.toString().substring(0, 8));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to show XP bar HUD for player %s",
                playerId.toString().substring(0, 8));
        }
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
     * <p>Uses full rerender ({@code resetHasBuilt} + {@code refreshOrRerender(true, true)})
     * instead of diff-based Set commands. This prevents the client crash where
     * MultipleHUD rebuilds the DOM and invalidates auto-generated element IDs.
     *
     * @param playerId The player's UUID
     */
    private void notifyXpChanged(@Nonnull UUID playerId) {
        HyUIHud hud = activeHuds.get(playerId);
        if (hud == null) {
            return;
        }

        try {
            hud.triggerRefresh();

            // Full rerender: reset hasBuilt → forces Append instead of Set
            if (RealmHudManager.resetHasBuilt(hud)) {
                hud.refreshOrRerender(true, true);
            } else {
                // Fallback: diff-based (may crash if DOM was rebuilt by MultipleHUD)
                hud.refreshOrRerender(false, false);
            }
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
     * instead. During world transitions, {@code Player.resetManagers()} has already
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
            hud.hide();
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
     * @param playerId The player's UUID
     */
    public void discardStaleHud(@Nonnull UUID playerId) {
        HyUIHud hud = activeHuds.remove(playerId);
        if (hud == null) {
            return;
        }

        try {
            hud.remove();
            LOGGER.atFine().log("Discarded stale XP bar HUD for player %s (world transition)",
                playerId.toString().substring(0, 8));
        } catch (Exception e) {
            LOGGER.atFine().log("Failed to discard stale XP bar HUD for player %s: %s",
                playerId.toString().substring(0, 8), e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // QUERIES
    // ═══════════════════════════════════════════════════════════════════

    /** Checks if a player has an active XP bar HUD. */
    public boolean hasHud(@Nonnull UUID playerId) {
        return activeHuds.containsKey(playerId);
    }

    /** Gets the count of active XP bar HUDs. */
    public int getActiveCount() {
        return activeHuds.size();
    }
}
