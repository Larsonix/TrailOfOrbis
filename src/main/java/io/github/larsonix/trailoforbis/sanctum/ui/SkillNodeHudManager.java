package io.github.larsonix.trailoforbis.sanctum.ui;

import au.ellie.hyui.builders.HyUIHud;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages skill node detail HUDs for players in the Skill Sanctum.
 *
 * <p>This manager ensures:
 * <ul>
 *   <li>Only one skill node detail HUD is shown per player at a time</li>
 *   <li>When a new node is clicked, the previous HUD is replaced</li>
 *   <li>Clicking the same node again toggles (closes) the HUD</li>
 *   <li>HUDs are properly cleaned up when players leave the sanctum</li>
 *   <li>Thread-safe operations for concurrent access</li>
 * </ul>
 *
 * <p>This prevents HUD stacking issues and ensures compatibility with other mods
 * that may also use HyUI's multi-HUD system.
 */
public class SkillNodeHudManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /**
     * Cooldown in milliseconds between HUD opens for the same player.
     * Prevents crashes from multi-hit swings hitting several nodes in one tick,
     * which would cause rapid add/hide/remove CustomUI command bursts.
     */
    private static final long HUD_OPEN_COOLDOWN_MS = 300;

    /**
     * Active HUD instances per player UUID.
     * Using ConcurrentHashMap for thread-safe access from world threads.
     */
    private final Map<UUID, HyUIHud> activeHuds = new ConcurrentHashMap<>();

    /**
     * Tracks which node ID is currently displayed per player.
     * Used for toggle behavior (clicking same node closes HUD).
     */
    private final Map<UUID, String> activeNodeIds = new ConcurrentHashMap<>();

    /**
     * Tracks when each player's HUD was last opened.
     * Used to enforce the cooldown between rapid HUD opens.
     */
    private final Map<UUID, Long> lastOpenTimes = new ConcurrentHashMap<>();

    // ═══════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Checks if a player is on HUD-open cooldown.
     * Returns true if a HUD was opened less than {@link #HUD_OPEN_COOLDOWN_MS} ago,
     * preventing rapid open/close cycles from multi-hit swings.
     *
     * @param playerUuid The player's UUID
     * @return true if opening a new HUD should be skipped
     */
    public boolean isOnCooldown(@Nonnull UUID playerUuid) {
        Long lastOpen = lastOpenTimes.get(playerUuid);
        if (lastOpen == null) {
            return false;
        }
        return (System.currentTimeMillis() - lastOpen) < HUD_OPEN_COOLDOWN_MS;
    }

    /**
     * Registers a newly shown HUD for a player.
     * If a previous HUD exists, it will be removed first.
     *
     * @param playerUuid The player's UUID
     * @param nodeId     The node ID being displayed
     * @param hud        The HyUIHud instance (returned from HudBuilder.show())
     */
    public void registerHud(@Nonnull UUID playerUuid, @Nonnull String nodeId, @Nonnull HyUIHud hud) {
        // Remove any existing HUD first
        removeHud(playerUuid);

        // Register the new one and record open time for cooldown
        activeHuds.put(playerUuid, hud);
        activeNodeIds.put(playerUuid, nodeId);
        lastOpenTimes.put(playerUuid, System.currentTimeMillis());
        LOGGER.atFine().log("Registered skill node HUD for player %s, node=%s",
            playerUuid.toString().substring(0, 8), nodeId);
    }

    /**
     * Removes and closes the active HUD for a player.
     *
     * @param playerUuid The player's UUID
     * @return true if a HUD was removed, false if none existed
     */
    public boolean removeHud(@Nonnull UUID playerUuid) {
        activeNodeIds.remove(playerUuid);
        lastOpenTimes.remove(playerUuid);
        HyUIHud existing = activeHuds.remove(playerUuid);
        if (existing != null) {
            try {
                // CRITICAL: hide() before remove() for immediate visual update
                // Without this, HUD remains visible when player is teleported out of sanctum
                existing.hide();
                existing.remove();
                LOGGER.atFine().log("Removed skill node HUD for player %s",
                    playerUuid.toString().substring(0, 8));
                return true;
            } catch (Exception e) {
                // HUD may already be removed (player disconnected, etc.)
                LOGGER.atFine().log("Could not remove HUD (may already be gone): %s", e.getMessage());
            }
        }
        return false;
    }

    /**
     * Checks if a player has an active skill node detail HUD.
     *
     * @param playerUuid The player's UUID
     * @return true if an active HUD exists
     */
    public boolean hasActiveHud(@Nonnull UUID playerUuid) {
        return activeHuds.containsKey(playerUuid);
    }

    /**
     * Gets the active HUD for a player, if any.
     *
     * @param playerUuid The player's UUID
     * @return The active HyUIHud, or null if none
     */
    @Nullable
    public HyUIHud getActiveHud(@Nonnull UUID playerUuid) {
        return activeHuds.get(playerUuid);
    }

    /**
     * Gets the node ID currently displayed for a player, if any.
     *
     * @param playerUuid The player's UUID
     * @return The node ID, or null if no HUD is active
     */
    @Nullable
    public String getActiveNodeId(@Nonnull UUID playerUuid) {
        return activeNodeIds.get(playerUuid);
    }

    /**
     * Discards a stale HUD during world transitions WITHOUT sending packets.
     *
     * <p>During world transitions, {@code Player.resetManagers()} already sends
     * {@code CustomHud(clear=true)} which destroys all HyUI elements on the client.
     * This method only removes from tracking maps and cancels refresh tasks
     * (via {@code remove()}) without calling {@code hide()} which would crash.
     *
     * @param playerUuid The player's UUID
     */
    public void discardStaleHud(@Nonnull UUID playerUuid) {
        activeNodeIds.remove(playerUuid);
        lastOpenTimes.remove(playerUuid);
        HyUIHud existing = activeHuds.remove(playerUuid);
        if (existing != null) {
            try {
                existing.remove();
                LOGGER.atFine().log("Discarded stale skill node HUD for player %s (world transition)",
                    playerUuid.toString().substring(0, 8));
            } catch (Exception e) {
                LOGGER.atFine().log("Failed to discard stale skill node HUD for player %s: %s",
                    playerUuid.toString().substring(0, 8), e.getMessage());
            }
        }
    }

    /**
     * Removes all HUDs for all players.
     * Called during plugin shutdown.
     */
    public void removeAllHuds() {
        LOGGER.atInfo().log("Removing all skill node HUDs (%d active)", activeHuds.size());
        for (UUID playerUuid : activeHuds.keySet()) {
            removeHud(playerUuid);
        }
        activeHuds.clear();
        activeNodeIds.clear();
        lastOpenTimes.clear();
    }

    /**
     * Gets the number of active HUDs.
     *
     * @return The count of active HUDs
     */
    public int getActiveHudCount() {
        return activeHuds.size();
    }
}
