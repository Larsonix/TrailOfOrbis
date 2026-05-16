package io.github.larsonix.trailoforbis.combat.attackspeed.api;

import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Public API for external mods to temporarily modify a player's attack speed.
 *
 * <p>This API is designed for Hexcode spell buffs, realm modifiers, and any
 * other system that needs temporary speed changes without modifying stats.
 *
 * <p><b>Usage from Hexcode or other mods:</b>
 * <pre>
 * // Get the API from ServiceRegistry
 * AttackSpeedApi api = ServiceRegistry.require(AttackSpeedApi.class);
 *
 * // Apply a +50% speed buff for 10 seconds
 * api.pushOverride(playerUuid, 1.5f, 10_000L, "hex_haste");
 *
 * // Apply a -30% slow debuff for 5 seconds
 * api.pushOverride(playerUuid, 0.7f, 5_000L, "hex_slow");
 *
 * // Remove an active override early
 * api.clearOverride(playerUuid);
 * </pre>
 *
 * <p><b>Semantics:</b>
 * <ul>
 *   <li>One override per player (last push wins)</li>
 *   <li>Overrides are TTL-based — auto-expire after the specified duration</li>
 *   <li>Applied as a multiplier ON TOP of stat-derived speed (not replacing stats)</li>
 *   <li>Cleaned up automatically on player disconnect</li>
 * </ul>
 *
 * <p><b>Thread safety:</b> All methods are safe to call from any thread.
 * The underlying storage is a {@link ConcurrentHashMap}.
 */
public final class AttackSpeedApi {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Map<UUID, AttackSpeedOverride> overrides = new ConcurrentHashMap<>();

    /**
     * Pushes a temporary attack speed override for a player.
     *
     * <p>If an override already exists for this player, it is replaced.
     * The override multiplier is applied on top of stat-derived speed:
     * {@code finalSpeed = statSpeed × overrideMultiplier}.
     *
     * @param playerId Player UUID
     * @param multiplier Speed multiplier (1.5 = +50% faster, 0.7 = -30% slower).
     *                   Clamped to [0.1, 5.0].
     * @param durationMs Duration in milliseconds. Clamped to [100, 300000].
     * @param source Human-readable label for debugging (e.g., "hex_haste")
     */
    public void pushOverride(@Nonnull UUID playerId, float multiplier, long durationMs, @Nullable String source) {
        AttackSpeedOverride override = AttackSpeedOverride.create(multiplier, durationMs, source);
        overrides.put(playerId, override);
        LOGGER.atFine().log("Attack speed override pushed for %s: %.2fx for %dms (source: %s)",
                playerId.toString().substring(0, 8), override.multiplier(), durationMs,
                source != null ? source : "unknown");
    }

    /**
     * Removes any active override for a player.
     *
     * @param playerId Player UUID
     */
    public void clearOverride(@Nonnull UUID playerId) {
        overrides.remove(playerId);
    }

    /**
     * Returns the active (non-expired) override for a player, or null.
     *
     * <p>Automatically cleans up expired entries on access (lazy expiration).
     *
     * @param playerId Player UUID
     * @return The active override, or null if none or expired
     */
    @Nullable
    public AttackSpeedOverride getActive(@Nonnull UUID playerId) {
        AttackSpeedOverride override = overrides.get(playerId);
        if (override == null) {
            return null;
        }
        if (override.isExpired()) {
            overrides.remove(playerId, override);
            return null;
        }
        return override;
    }

    /**
     * Returns the active override multiplier, or 1.0 if no override is active.
     *
     * @param playerId Player UUID
     * @return The multiplier (1.0 = no override)
     */
    public float getActiveMultiplier(@Nonnull UUID playerId) {
        AttackSpeedOverride override = getActive(playerId);
        return override != null ? override.multiplier() : 1.0f;
    }

    /**
     * Clears all state for a disconnected player.
     */
    public void onPlayerDisconnect(@Nonnull UUID playerId) {
        overrides.remove(playerId);
    }

    /**
     * Clears all overrides. Called during plugin shutdown.
     */
    public void shutdown() {
        overrides.clear();
    }
}
