package io.github.larsonix.trailoforbis.combat.attackspeed;

/**
 * Resolved attack speed multipliers for a single player on a single tick.
 *
 * <p>Produced by {@link AttackSpeedResolver} from the player's stats and
 * their held weapon's {@link io.github.larsonix.trailoforbis.combat.attackspeed.config.WeaponSpeedProfile}.
 * Each multiplier controls a different aspect of attack timing:
 *
 * <ul>
 *   <li>{@code chainMultiplier} — server-side chain acceleration via
 *       {@code InteractionEntry.setTimestamp()}. Makes the actual swing faster.</li>
 *   <li>{@code cooldownMultiplier} — inter-attack cooldown acceleration via
 *       {@code CooldownHandler.tick()}. Makes the gap between attacks shorter.</li>
 *   <li>{@code animationMultiplier} — client-side visual speed via
 *       {@code UpdateItemPlayerAnimations} packets. Makes it look right.</li>
 * </ul>
 *
 * <p>All values are ≥ profile's {@code minMultiplier} and ≤ {@code maxMultiplier}.
 * A value of 1.0 means no speed change.
 *
 * @param chainMultiplier Server-side chain speed (1.0 = normal, 1.5 = 50% faster swings)
 * @param cooldownMultiplier Cooldown depletion speed (1.0 = normal, 1.5 = 50% faster cooldown)
 * @param animationMultiplier Client visual animation speed (1.0 = normal, 1.3 = 30% faster visuals)
 */
public record AttackSpeedSnapshot(
        float chainMultiplier,
        float cooldownMultiplier,
        float animationMultiplier
) {

    /** No speed change — all multipliers at 1.0. */
    public static final AttackSpeedSnapshot IDENTITY = new AttackSpeedSnapshot(1.0f, 1.0f, 1.0f);

    /**
     * @return true if all multipliers are effectively vanilla (no speed change needed)
     */
    public boolean isIdentity() {
        return Math.abs(chainMultiplier - 1.0f) < 0.001f
                && Math.abs(cooldownMultiplier - 1.0f) < 0.001f
                && Math.abs(animationMultiplier - 1.0f) < 0.001f;
    }
}
