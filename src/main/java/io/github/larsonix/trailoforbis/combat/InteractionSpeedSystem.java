package io.github.larsonix.trailoforbis.combat;

/**
 * Attack speed formula calculations for weapon cooldowns.
 *
 * <p><b>Formula:</b>
 * <pre>
 * scaledCooldown = baseCooldown / (1 + attackSpeedPercent / 100)
 * </pre>
 *
 * <p><b>Note:</b> This class now only contains the formula logic. The actual
 * cooldown modification is handled by {@link io.github.larsonix.trailoforbis.combat.attackspeed.AttackSpeedEventHandler},
 * which hooks {@code PlayerMouseButtonEvent} at LATE priority to modify cooldowns
 * after vanilla creates them.
 *
 * <p>The old approach (calling {@code applyInteractionSpeed()} from StatsApplicationSystem)
 * didn't work because Hytale creates cooldowns lazily - they don't exist until
 * the player actually attacks.
 */
public final class InteractionSpeedSystem {

    /** Minimum cooldown cap to prevent near-instant actions */
    static final float MIN_COOLDOWN = 0.05f;

    /** Maximum attack speed percentage (caps at 500% to prevent exploits) */
    static final float MAX_ATTACK_SPEED_PERCENT = 500.0f;

    private InteractionSpeedSystem() {
        // Utility class
    }

    /**
     * Calculates the scaled cooldown for a given base cooldown and speed percentage.
     *
     * <p><b>Formula:</b> {@code baseCooldown / (1 + speedPercent / 100)}
     *
     * <p>Visible for testing and used by AttackSpeedEventHandler.
     *
     * @param baseCooldown The base cooldown in seconds
     * @param speedPercent The speed percentage bonus (can be negative for penalty)
     * @param maxSpeedPercent The maximum allowed speed percentage
     * @return The scaled cooldown, clamped to {@link #MIN_COOLDOWN}
     */
    public static float calculateScaledCooldown(float baseCooldown, float speedPercent, float maxSpeedPercent) {
        float clampedSpeed = Math.min(speedPercent, maxSpeedPercent);
        float speedMultiplier = 1.0f + (clampedSpeed / 100.0f);
        // Prevent division by zero or negative multiplier
        if (speedMultiplier <= 0.0f) {
            speedMultiplier = 0.01f;
        }
        float scaled = baseCooldown / speedMultiplier;
        return Math.max(scaled, MIN_COOLDOWN);
    }

    /**
     * Calculates the scaled cooldown using the default max speed cap.
     *
     * <p>Visible for testing. Uses {@link #MAX_ATTACK_SPEED_PERCENT} as the cap.
     *
     * @param baseCooldown The base cooldown in seconds
     * @param speedPercent The speed percentage bonus
     * @return The scaled cooldown, clamped to {@link #MIN_COOLDOWN}
     */
    static float calculateScaledCooldown(float baseCooldown, float speedPercent) {
        return calculateScaledCooldown(baseCooldown, speedPercent, MAX_ATTACK_SPEED_PERCENT);
    }
}
