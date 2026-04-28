package io.github.larsonix.trailoforbis.combat.attackspeed;

/**
 * Configuration for the animation speed sync system.
 *
 * <p>Controls how the player's {@code attackSpeedPercent} stat translates into
 * a visual animation speed multiplier sent via {@code UpdateItemPlayerAnimations} packets.
 *
 * <p><b>Formula:</b>
 * <pre>
 * multiplier = 1.0 + (attackSpeedPercent * animationSpeedScale / 100.0)
 * multiplier = clamp(multiplier, animationMinSpeed, animationMaxSpeed)
 * </pre>
 *
 * <p>With the default scale of 0.5, a player with +100% attack speed gets 1.5× animation
 * speed — fast enough to be noticeable without looking broken.
 *
 * @param enabled Whether the animation speed sync system is active.
 * @param animationSpeedScale Dampening factor. 0.5 means +100% stat → 1.5× visual speed.
 * @param animationMinSpeed Floor for animation speed multiplier (prevents frozen-looking attacks).
 * @param animationMaxSpeed Ceiling for animation speed multiplier (prevents visually broken animations).
 */
public record AnimationSpeedSyncConfig(
        boolean enabled,
        float animationSpeedScale,
        float animationMinSpeed,
        float animationMaxSpeed
) {

    /** Default configuration with sensible dampening values. */
    public static final AnimationSpeedSyncConfig DEFAULT = new AnimationSpeedSyncConfig(
            true, 0.5f, 0.5f, 3.0f
    );

    /**
     * Calculates the animation speed multiplier for a given attack speed stat.
     *
     * @param attackSpeedPercent The player's attack speed stat (e.g. 100.0 for +100%)
     * @return The clamped animation speed multiplier (e.g. 1.5 for +100% with scale 0.5)
     */
    public float calculateMultiplier(float attackSpeedPercent) {
        float multiplier = 1.0f + (attackSpeedPercent * animationSpeedScale / 100.0f);
        return Math.max(animationMinSpeed, Math.min(animationMaxSpeed, multiplier));
    }
}
