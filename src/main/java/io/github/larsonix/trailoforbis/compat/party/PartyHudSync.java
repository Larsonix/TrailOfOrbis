package io.github.larsonix.trailoforbis.compat.party;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.leveling.api.LevelingService;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Synchronizes player level and location display with PartyPro's custom HUD text fields.
 *
 * <p>Uses per-slot API methods to prevent clobbering:
 * <ul>
 *   <li>Slot 1: Player level (e.g., "Lv.42")</li>
 *   <li>Slot 2: Player location (e.g., "Overworld", "Desert Lv.60", "Skill Sanctum")</li>
 * </ul>
 */
public class PartyHudSync implements PartyChangeListener {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final PartyBridge bridge;
    private final LevelingService levelingService;
    private final PartyConfig.HudConfig hudConfig;
    private final PartyConfig.LocationConfig locationConfig;

    public PartyHudSync(@Nonnull PartyBridge bridge,
                         @Nonnull LevelingService levelingService,
                         @Nonnull PartyConfig.HudConfig hudConfig,
                         @Nonnull PartyConfig.LocationConfig locationConfig) {
        this.bridge = bridge;
        this.levelingService = levelingService;
        this.hudConfig = hudConfig;
        this.locationConfig = locationConfig;
    }

    // ═══════════════════════════════════════════════════════════════════
    // LEVEL DISPLAY (slot 1)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Updates the level display for a player in the party HUD.
     * Uses per-slot method to preserve the location text in the other slot.
     */
    public void updateLevel(@Nonnull UUID playerId, int level) {
        if (!bridge.isAvailable()) return;

        String formatted = hudConfig.getLevelFormat().replace("{level}", String.valueOf(level));
        if (hudConfig.getLevelSlot() == 1) {
            bridge.setCustomText1(playerId, formatted);
        } else {
            bridge.setCustomText2(playerId, formatted);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // LOCATION DISPLAY (slot 2)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Updates the location display for a player in the party HUD.
     * Uses per-slot method to preserve the level text in the other slot.
     */
    public void updateLocation(@Nonnull UUID playerId, @Nonnull String locationText) {
        if (!bridge.isAvailable() || !locationConfig.isEnabled()) return;

        if (locationConfig.getLocationSlot() == 1) {
            bridge.setCustomText1(playerId, locationText);
        } else {
            bridge.setCustomText2(playerId, locationText);
        }
    }

    /**
     * Sets the player's location to the overworld default.
     */
    public void setOverworld(@Nonnull UUID playerId) {
        updateLocation(playerId, locationConfig.getOverworldText());
    }

    /**
     * Sets the player's location to a realm.
     */
    public void setRealm(@Nonnull UUID playerId, @Nonnull String biomeName, int realmLevel) {
        updateLocation(playerId, locationConfig.formatRealm(biomeName, realmLevel));
    }

    /**
     * Sets the player's location to the skill sanctum.
     */
    public void setSanctum(@Nonnull UUID playerId) {
        updateLocation(playerId, locationConfig.getSanctumText());
    }

    // ═══════════════════════════════════════════════════════════════════
    // CLEANUP
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Clears all HUD data for a disconnecting player.
     */
    public void clearPlayer(@Nonnull UUID playerId) {
        if (!bridge.isAvailable()) return;
        bridge.clearCustomText(playerId);
    }

    public void shutdown() {
        // No persistent state to clean up
    }

    // ═══════════════════════════════════════════════════════════════════
    // PartyChangeListener — react to party membership changes
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public void onMemberJoined(UUID partyId, UUID playerId) {
        // New member joined — show their level in the HUD
        int level = levelingService.getLevel(playerId);
        updateLevel(playerId, level);
    }

    @Override
    public void onMemberLeft(UUID partyId, UUID playerId) {
        clearPlayer(playerId);
    }

    @Override
    public void onPartyDisbanded(UUID partyId) {
        // PartyPro API does not provide the former member list on disband.
        // Each member will have their HUD cleared individually via onMemberLeft
        // or on disconnect via clearPlayer().
    }
}
