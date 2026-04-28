package io.github.larsonix.trailoforbis.combat.attackspeed;

/**
 * Configuration for the event-driven attack speed system.
 *
 * <p>The attack speed system modifies weapon cooldowns based on the player's
 * attackSpeedPercent stat. Higher attack speed means faster attacks.
 *
 * <p><b>Formula:</b>
 * <pre>
 * scaledCooldown = baseCooldown / (1 + attackSpeedPercent / 100)
 * </pre>
 *
 * @param maxBonus Maximum attack speed bonus percentage (default: 500%).
 *                 Higher values are clamped to this cap.
 * @param minBonus Minimum attack speed (can be negative for penalty).
 *                 Default: -75% (attacks at most 4× slower).
 * @param minCooldown Minimum cooldown in seconds (default: 0.05 = 50ms).
 *                    Prevents near-instant attacks even with extreme speed.
 * @param enabled Whether the attack speed system is active.
 */
public record AttackSpeedConfig(
        double maxBonus,
        double minBonus,
        float minCooldown,
        boolean enabled
) {

    /** Default configuration with sensible values */
    public static final AttackSpeedConfig DEFAULT = new AttackSpeedConfig(
            500.0,   // Max +500% attack speed
            -75.0,   // Min -75% attack speed (4× slower)
            0.05f,   // 50ms minimum cooldown
            true     // Enabled by default
    );

    /**
     * Clamps an attack speed value to the configured bounds.
     *
     * @param attackSpeed The raw attack speed percentage
     * @return The clamped attack speed within [minBonus, maxBonus]
     */
    public float clampAttackSpeed(float attackSpeed) {
        return (float) Math.max(minBonus, Math.min(maxBonus, attackSpeed));
    }

    /**
     * Creates an AttackSpeedConfig from parsed YAML values.
     *
     * @param enabled Whether attack speed is enabled
     * @param maxBonus Maximum attack speed bonus (%)
     * @param minBonus Minimum attack speed penalty (%)
     * @param minCooldown Minimum cooldown in seconds
     * @return Configured AttackSpeedConfig
     */
    public static AttackSpeedConfig of(boolean enabled, double maxBonus, double minBonus, float minCooldown) {
        return new AttackSpeedConfig(maxBonus, minBonus, minCooldown, enabled);
    }
}
