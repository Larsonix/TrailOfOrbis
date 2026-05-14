package io.github.larsonix.trailoforbis.compat.party;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import io.github.larsonix.trailoforbis.leveling.api.LevelingService;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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

        LOGGER.at(Level.FINE).log("Party mod not ready, retrying in %dms (%d retries left)",
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
        if (config.getHud().isEnabled() || config.getLocation().isEnabled()) {
            hudSync = new PartyHudSync(bridge, levelingService, config.getHud(), config.getLocation());
            bridge.registerEventListener(hudSync);
            LOGGER.at(Level.INFO).log("Party HUD sync enabled (level: %s, location: %s)",
                config.getHud().isEnabled() ? config.getHud().getLevelFormat() : "OFF",
                config.getLocation().isEnabled() ? "ON" : "OFF");
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

    // ═══════════════════════════════════════════════════════════════════
    // LOCATION TRACKING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Sets the player's location to a realm biome in the party HUD.
     *
     * @param playerId The player entering the realm
     * @param biomeName The biome display name (e.g., "Desert")
     * @param realmLevel The realm level
     */
    public void updateHudRealm(@Nonnull UUID playerId, @Nonnull String biomeName, int realmLevel) {
        if (hudSync != null) {
            hudSync.setRealm(playerId, biomeName, realmLevel);
        }
    }

    /**
     * Sets the player's location to the skill sanctum in the party HUD.
     */
    public void updateHudSanctum(@Nonnull UUID playerId) {
        if (hudSync != null) {
            hudSync.setSanctum(playerId);
        }
    }

    /**
     * Sets the player's location to the overworld in the party HUD.
     */
    public void updateHudOverworld(@Nonnull UUID playerId) {
        if (hudSync != null) {
            hudSync.setOverworld(playerId);
        }
    }
}
