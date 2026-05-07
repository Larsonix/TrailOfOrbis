package io.github.larsonix.trailoforbis.ui.hud;

import au.ellie.hyui.builders.HyUIHud;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-player HUD visibility toggle state.
 *
 * <p>Hytale's F8 key only toggles vanilla HUD components — custom HUDs
 * ({@link HyUIHud}) are unaffected because F8 is purely client-side with
 * no server event. This service provides a {@code /hud} command toggle
 * that hides/unhides all custom HUDs for a player.
 *
 * <p>Each HUD manager calls {@link #applyToggleState(UUID, HyUIHud)} after
 * creating or re-rendering a HUD. If the player has toggled HUDs off, the
 * HUD is immediately hidden via {@link HyUIHud#hide()}.
 */
public class HudToggleService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Players who have toggled HUDs off. */
    private final Set<UUID> hiddenPlayers = ConcurrentHashMap.newKeySet();

    /**
     * Returns whether a player has custom HUDs toggled off.
     */
    public boolean isHidden(@Nonnull UUID playerId) {
        return hiddenPlayers.contains(playerId);
    }

    /**
     * Toggles the HUD visibility state for a player.
     *
     * @return {@code true} if HUDs are now hidden, {@code false} if now visible
     */
    public boolean toggle(@Nonnull UUID playerId) {
        if (hiddenPlayers.add(playerId)) {
            LOGGER.atFine().log("Player %s toggled HUDs OFF", playerId.toString().substring(0, 8));
            return true;
        } else {
            hiddenPlayers.remove(playerId);
            LOGGER.atFine().log("Player %s toggled HUDs ON", playerId.toString().substring(0, 8));
            return false;
        }
    }

    /**
     * Hides a HUD if the player has toggled HUDs off.
     *
     * <p>Call after {@code hud.show()} (initial creation). Uses safe visibility
     * via full rerender instead of {@code hud.hide()} which sends raw
     * {@code Set} commands with stale selectors.
     *
     * <p><b>Not needed</b> after {@link HudRefreshHelper#safeRefreshWithToggle}
     * — that method bakes visibility into the rerender atomically.
     */
    public void applyToggleState(@Nonnull UUID playerId, @Nonnull HyUIHud hud) {
        if (hiddenPlayers.contains(playerId)) {
            HudRefreshHelper.safeSetVisibility(hud, false);
        }
    }

    /**
     * Cleans up state on player disconnect. HUDs are always visible on join.
     */
    public void onDisconnect(@Nonnull UUID playerId) {
        hiddenPlayers.remove(playerId);
    }
}
