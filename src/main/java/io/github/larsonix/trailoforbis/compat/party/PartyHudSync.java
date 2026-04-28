package io.github.larsonix.trailoforbis.compat.party;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.leveling.api.LevelingService;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Synchronizes player level display with PartyPro's custom HUD text fields.
 *
 * <p>When a player levels up or joins, their level is shown in the party HUD
 * so all party members can see each other's levels.
 */
public class PartyHudSync implements PartyChangeListener {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final PartyBridge bridge;
    private final LevelingService levelingService;
    private final PartyConfig.HudConfig config;

    public PartyHudSync(@Nonnull PartyBridge bridge,
                         @Nonnull LevelingService levelingService,
                         @Nonnull PartyConfig.HudConfig config) {
        this.bridge = bridge;
        this.levelingService = levelingService;
        this.config = config;
    }

    /**
     * Updates the level display for a player in the party HUD.
     */
    public void updateLevel(@Nonnull UUID playerId, int level) {
        if (!bridge.isAvailable()) return;

        String formatted = config.getLevelFormat().replace("{level}", String.valueOf(level));
        if (config.getLevelSlot() == 1) {
            bridge.setCustomText(playerId, formatted, null);
        } else {
            bridge.setCustomText(playerId, null, formatted);
        }
    }

    /**
     * Clears the HUD data for a disconnecting player.
     */
    public void clearPlayer(@Nonnull UUID playerId) {
        if (!bridge.isAvailable()) return;
        bridge.clearCustomText(playerId);
    }

    /**
     * Shuts down the HUD sync.
     */
    public void shutdown() {
        // No persistent state to clean up
    }

    // ═══════════════════════════════════════════════════════════════════
    // PartyChangeListener — react to party changes
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
    public void onPartyDisbanded(UUID partyId, List<UUID> formerMembers) {
        for (UUID memberId : formerMembers) {
            clearPlayer(memberId);
        }
    }
}
