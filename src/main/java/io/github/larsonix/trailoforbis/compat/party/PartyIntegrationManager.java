package io.github.larsonix.trailoforbis.compat.party;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import io.github.larsonix.trailoforbis.leveling.api.LevelingService;
import io.github.larsonix.trailoforbis.leveling.xp.XpSource;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Manages the lifecycle and operations of party mod integration.
 *
 * <p>Handles detection (with retry), XP distribution, anti-boosting level
 * lookup, and coordinates all party-aware subsystems. Uses {@link PartyBridge}
 * abstraction — starts with {@link NoOpPartyBridge} and hot-swaps to
 * {@link PartyProReflectionBridge} when PartyPro is detected.
 */
public class PartyIntegrationManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final PartyConfig config;
    private final LevelingService levelingService;
    private volatile PartyBridge bridge;
    private volatile PartyHudSync hudSync;
    private volatile ScheduledFuture<?> retryTask;

    public PartyIntegrationManager(@Nonnull PartyConfig config,
                                    @Nonnull LevelingService levelingService) {
        this.config = config;
        this.levelingService = levelingService;
        this.bridge = new NoOpPartyBridge();
    }

    // ═══════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Initializes party integration. Attempts to detect PartyPro immediately,
     * then schedules retries if not found (PartyPro may load after us).
     */
    public void initialize() {
        if (!config.isEnabled()) {
            LOGGER.at(Level.INFO).log("Party integration disabled in config");
            return;
        }

        if (tryDetectPartyPro()) {
            onDetected();
        } else {
            scheduleRetries(config.getDetection().getMaxRetries(), config.getDetection().getRetryDelayMs());
        }
    }

    /**
     * Shuts down party integration. Unregisters event proxy, clears HUD text.
     */
    public void shutdown() {
        if (retryTask != null) {
            retryTask.cancel(false);
            retryTask = null;
        }

        if (hudSync != null) {
            hudSync.shutdown();
            hudSync = null;
        }

        if (bridge instanceof PartyProReflectionBridge ppBridge) {
            ppBridge.unregisterEventProxy();
        }

        bridge = new NoOpPartyBridge();
        LOGGER.at(Level.INFO).log("Party integration shut down");
    }

    // ═══════════════════════════════════════════════════════════════════
    // DETECTION
    // ═══════════════════════════════════════════════════════════════════

    private boolean tryDetectPartyPro() {
        try {
            var ppBridge = new PartyProReflectionBridge(config.getApiClass());
            this.bridge = ppBridge;
            LOGGER.at(Level.INFO).log("PartyPro detected and bridge initialized");
            return true;
        } catch (Exception e) {
            LOGGER.at(Level.FINE).log("PartyPro not available: %s", e.getMessage());
            return false;
        }
    }

    private void scheduleRetries(int retriesLeft, long delayMs) {
        if (retriesLeft <= 0) {
            LOGGER.at(Level.INFO).log("Party mod not found after all retries — running in solo mode");
            return;
        }

        LOGGER.at(Level.INFO).log("Party mod not ready, retrying in %dms (%d retries left)",
            delayMs, retriesLeft);

        retryTask = HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            if (tryDetectPartyPro()) {
                onDetected();
            } else {
                scheduleRetries(retriesLeft - 1, delayMs);
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    private void onDetected() {
        retryTask = null;

        // Register event proxy
        if (bridge instanceof PartyProReflectionBridge ppBridge) {
            ppBridge.registerEventProxy();
        }

        // Initialize HUD sync
        if (config.getHud().isEnabled()) {
            hudSync = new PartyHudSync(bridge, levelingService, config.getHud());
            bridge.registerEventListener(hudSync);
            LOGGER.at(Level.INFO).log("Party HUD sync enabled (format: %s)", config.getHud().getLevelFormat());
        }

        LOGGER.at(Level.INFO).log("Party integration fully active — XP sharing: %s, PvP protection: %s, Realm co-op: %s",
            config.getXpSharing().isEnabled() ? "ON" : "OFF",
            config.getPvpProtection().isEnabled() ? "ON" : "OFF",
            config.getRealmCoop().isEnabled() ? "ON" : "OFF");
    }

    // ═══════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════

    /** Gets the active party bridge (never null). */
    @Nonnull
    public PartyBridge getBridge() {
        return bridge;
    }

    /** Gets the party config. */
    @Nonnull
    public PartyConfig getConfig() {
        return config;
    }

    /** Whether a party mod is currently detected and operational. */
    public boolean isPartyModAvailable() {
        return bridge.isAvailable();
    }

    // ═══════════════════════════════════════════════════════════════════
    // XP DISTRIBUTION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Distributes XP to the killer and their party members.
     *
     * <p>If party sharing is disabled or the killer is solo, grants full XP
     * to the killer directly. Otherwise distributes according to the configured
     * mode (equal split or killer bonus).
     *
     * @param killerUuid The player who got the kill
     * @param xp The calculated XP amount (after gap penalty + bonuses)
     * @return true if XP was distributed to a party (false = solo grant)
     */
    public boolean distributeXp(@Nonnull UUID killerUuid, long xp) {
        if (!config.getXpSharing().isEnabled() || !bridge.isInParty(killerUuid)) {
            // Solo — grant directly
            levelingService.addXp(killerUuid, xp, XpSource.MOB_KILL);
            return false;
        }

        List<UUID> onlineMembers = bridge.getOnlinePartyMembers(killerUuid);
        if (onlineMembers.size() <= 1) {
            // Solo or only member online
            levelingService.addXp(killerUuid, xp, XpSource.MOB_KILL);
            return false;
        }

        PartyConfig.XpSharingConfig sharingConfig = config.getXpSharing();
        double multiplier = sharingConfig.getMultiplierForSize(onlineMembers.size());
        long totalPool = Math.round(xp * multiplier);

        if ("killer_bonus".equalsIgnoreCase(sharingConfig.getMode())) {
            // Killer gets full XP, others get bonus share
            levelingService.addXp(killerUuid, xp, XpSource.MOB_KILL);
            long bonusPerMember = Math.max(1, totalPool / onlineMembers.size());
            for (UUID memberId : onlineMembers) {
                if (!memberId.equals(killerUuid)) {
                    levelingService.addXp(memberId, bonusPerMember, XpSource.PARTY_SHARE);
                }
            }
        } else {
            // Equal split (default)
            long xpPerMember = Math.max(1, totalPool / onlineMembers.size());
            for (UUID memberId : onlineMembers) {
                XpSource source = memberId.equals(killerUuid) ? XpSource.MOB_KILL : XpSource.PARTY_SHARE;
                levelingService.addXp(memberId, xpPerMember, source);
            }
        }

        LOGGER.at(Level.FINE).log("[PartyXP] Distributed %d XP (×%.2f pool) to %d members from killer %s",
            xp, multiplier, onlineMembers.size(), killerUuid.toString().substring(0, 8));

        return true;
    }

    // ═══════════════════════════════════════════════════════════════════
    // ANTI-BOOSTING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the highest level among online party members.
     *
     * <p>Used by the level-gap penalty to prevent high-level players from
     * boosting low-level alts through party XP sharing.
     *
     * @param playerId Any player in the party
     * @return The highest level, or the player's own level if not in a party
     */
    public int getHighestPartyMemberLevel(@Nonnull UUID playerId) {
        if (!config.getAntiBoosting().isEnabled() || !config.getAntiBoosting().isUsePartyMaxLevel()) {
            return levelingService.getLevel(playerId);
        }

        List<UUID> members = bridge.getOnlinePartyMembers(playerId);
        int maxLevel = 0;
        for (UUID memberId : members) {
            int level = levelingService.getLevel(memberId);
            if (level > maxLevel) {
                maxLevel = level;
            }
        }
        return Math.max(maxLevel, levelingService.getLevel(playerId));
    }

    // ═══════════════════════════════════════════════════════════════════
    // PVP PROTECTION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Checks if damage between two players should be blocked by party PvP protection.
     *
     * @param attackerUuid The attacker's UUID
     * @param defenderUuid The defender's UUID
     * @return true if damage should be cancelled
     */
    public boolean shouldBlockPvp(@Nonnull UUID attackerUuid, @Nonnull UUID defenderUuid) {
        if (!config.getPvpProtection().isEnabled() || !config.getPvpProtection().isBlockFriendlyFire()) {
            return false;
        }
        if (!bridge.areInSameParty(attackerUuid, defenderUuid)) {
            return false;
        }
        // Same party — block unless PvP is explicitly enabled
        return !bridge.isPvpEnabledInParty(attackerUuid);
    }

    // ═══════════════════════════════════════════════════════════════════
    // HUD
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Updates the HUD level display for a player.
     * Called on level up and player ready events.
     */
    public void updateHudLevel(@Nonnull UUID playerId, int level) {
        if (hudSync != null) {
            hudSync.updateLevel(playerId, level);
        }
    }

    /**
     * Clears HUD data for a player (on disconnect).
     */
    public void clearHud(@Nonnull UUID playerId) {
        if (hudSync != null) {
            hudSync.clearPlayer(playerId);
        }
    }
}
