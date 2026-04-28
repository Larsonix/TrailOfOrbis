package io.github.larsonix.trailoforbis.combat.projectile;

/**
 * Configuration for the projectile stats system.
 *
 * <p>This system modifies arrow/thrown weapon physics based on player stats:
 * <ul>
 *   <li>{@code projectileSpeedPercent} - Modifies projectile velocity</li>
 *   <li>{@code projectileGravityPercent} - Modifies projectile gravity (negative = floats longer)</li>
 * </ul>
 *
 * <p>Loaded from the {@code projectile:} section in config.yml.
 */
public class ProjectileConfig {

    /** Whether the projectile stats system is enabled. */
    private boolean enabled = true;

    /** Maximum speed bonus percentage (e.g., 200 = +200% = 3x speed cap). */
    private float maxSpeedBonus = 200.0f;

    /** Minimum speed bonus percentage (e.g., -50 = 0.5x speed floor). */
    private float minSpeedBonus = -50.0f;

    /** Maximum gravity bonus percentage (e.g., 100 = +100% = 2x gravity, falls faster). */
    private float maxGravityBonus = 100.0f;

    /** Minimum gravity bonus percentage (e.g., -90 = 0.1x gravity, floats longer). */
    private float minGravityBonus = -90.0f;

    // ==================== Getters ====================

    public boolean isEnabled() {
        return enabled;
    }

    public float getMaxSpeedBonus() {
        return maxSpeedBonus;
    }

    public float getMinSpeedBonus() {
        return minSpeedBonus;
    }

    public float getMaxGravityBonus() {
        return maxGravityBonus;
    }

    public float getMinGravityBonus() {
        return minGravityBonus;
    }

    // ==================== Setters ====================

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setMaxSpeedBonus(float maxSpeedBonus) {
        this.maxSpeedBonus = maxSpeedBonus;
    }

    public void setMinSpeedBonus(float minSpeedBonus) {
        this.minSpeedBonus = minSpeedBonus;
    }

    public void setMaxGravityBonus(float maxGravityBonus) {
        this.maxGravityBonus = maxGravityBonus;
    }

    public void setMinGravityBonus(float minGravityBonus) {
        this.minGravityBonus = minGravityBonus;
    }

    // ==================== Utility Methods ====================

    /**
     * Clamps a speed modifier value within configured bounds.
     *
     * @param speedPercent The raw speed percentage from player stats
     * @return Clamped speed multiplier (e.g., 1.0 = no change, 2.0 = 2x speed)
     */
    public float clampSpeedMultiplier(float speedPercent) {
        float clamped = Math.max(minSpeedBonus, Math.min(maxSpeedBonus, speedPercent));
        return 1.0f + (clamped / 100.0f);
    }

    /**
     * Clamps a gravity modifier value within configured bounds.
     *
     * @param gravityPercent The raw gravity percentage from player stats
     * @return Clamped gravity multiplier (e.g., 1.0 = no change, 0.5 = half gravity)
     */
    public float clampGravityMultiplier(float gravityPercent) {
        float clamped = Math.max(minGravityBonus, Math.min(maxGravityBonus, gravityPercent));
        return 1.0f + (clamped / 100.0f);
    }

    /**
     * Validates the configuration values.
     *
     * @throws IllegalStateException if configuration is invalid
     */
    public void validate() {
        if (minSpeedBonus > maxSpeedBonus) {
            throw new IllegalStateException("projectile.minSpeedBonus cannot be greater than maxSpeedBonus");
        }
        if (minGravityBonus > maxGravityBonus) {
            throw new IllegalStateException("projectile.minGravityBonus cannot be greater than maxGravityBonus");
        }
        // Ensure we don't allow negative final multipliers for speed (would reverse direction)
        if (minSpeedBonus < -100.0f) {
            throw new IllegalStateException("projectile.minSpeedBonus cannot be less than -100 (would reverse projectile)");
        }
    }

    @Override
    public String toString() {
        return String.format(
            "ProjectileConfig{enabled=%s, speed=[%.1f%%, %.1f%%], gravity=[%.1f%%, %.1f%%]}",
            enabled, minSpeedBonus, maxSpeedBonus, minGravityBonus, maxGravityBonus
        );
    }
}
