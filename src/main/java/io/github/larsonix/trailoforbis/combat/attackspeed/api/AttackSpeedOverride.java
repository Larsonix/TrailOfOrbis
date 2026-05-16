package io.github.larsonix.trailoforbis.combat.attackspeed.api;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A temporary attack speed override applied on top of stat-derived multipliers.
 *
 * <p>Used by Hexcode spell buffs (e.g., "Haste" hex: +50% for 10s) and other
 * mods that need to temporarily modify a player's attack speed without
 * modifying their stats.
 *
 * <p>The override multiplier is applied AFTER the stat-based resolver:
 * <pre>
 * finalChainMult = statChainMult × overrideMultiplier
 * finalCdMult    = statCdMult × overrideMultiplier
 * finalAnimMult  = statAnimMult × overrideMultiplier
 * </pre>
 *
 * @param multiplier Speed multiplier (1.5 = 50% faster, 0.7 = 30% slower)
 * @param expiresAtMs Wall-clock expiry time (System.currentTimeMillis)
 * @param source Human-readable source label for debugging (e.g., "hex_haste", "realm_modifier")
 */
public record AttackSpeedOverride(
        float multiplier,
        long expiresAtMs,
        @Nullable String source
) {

    /** Minimum override multiplier (prevents frozen attacks). */
    public static final float MIN_MULTIPLIER = 0.1f;
    /** Maximum override multiplier (prevents visual/gameplay breakage). */
    public static final float MAX_MULTIPLIER = 5.0f;
    /** Minimum TTL in milliseconds. */
    public static final long MIN_TTL_MS = 100L;
    /** Maximum TTL in milliseconds (5 minutes). */
    public static final long MAX_TTL_MS = 300_000L;
    /** Default TTL when not specified (10 seconds). */
    public static final long DEFAULT_TTL_MS = 10_000L;

    /**
     * Creates a sanitized override with clamped values.
     *
     * @param multiplier Speed multiplier (clamped to [0.1, 5.0])
     * @param ttlMs Time-to-live in milliseconds (clamped to [100, 300000])
     * @param source Debug label
     * @return New override with sanitized values
     */
    @Nonnull
    public static AttackSpeedOverride create(float multiplier, long ttlMs, @Nullable String source) {
        float safeMultiplier = Float.isFinite(multiplier) && multiplier > 0
                ? Math.max(MIN_MULTIPLIER, Math.min(MAX_MULTIPLIER, multiplier))
                : 1.0f;
        long safeTtl = ttlMs > 0
                ? Math.max(MIN_TTL_MS, Math.min(MAX_TTL_MS, ttlMs))
                : DEFAULT_TTL_MS;
        long expiresAt = System.currentTimeMillis() + safeTtl;
        return new AttackSpeedOverride(safeMultiplier, expiresAt, source);
    }

    /**
     * @return true if this override has expired
     */
    public boolean isExpired() {
        return System.currentTimeMillis() >= expiresAtMs;
    }
}
