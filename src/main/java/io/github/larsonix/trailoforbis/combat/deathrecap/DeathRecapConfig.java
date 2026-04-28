package io.github.larsonix.trailoforbis.combat.deathrecap;

import javax.annotation.Nonnull;

/**
 * Configuration for the death recap system.
 *
 * <p>Controls whether and how death recaps are displayed to players when they die.
 * Supports both full detailed breakdown and compact single-line modes.
 */
public class DeathRecapConfig {

    private boolean enabled = true;
    private String displayMode = "full";
    private boolean showDamageBreakdown = true;
    private boolean showDefensiveStats = true;
    private boolean showElementalDamage = true;
    private int damageChainLength = 5;
    private boolean showDamageChain = true;
    private boolean killFeedEnabled = true;
    private boolean killFeedContextual = true;

    // ==================== Getters ====================

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @return "full" for detailed multi-line breakdown, "compact" for single line
     */
    @Nonnull
    public String getDisplayMode() {
        return displayMode;
    }

    public boolean isShowDamageBreakdown() {
        return showDamageBreakdown;
    }

    public boolean isShowDefensiveStats() {
        return showDefensiveStats;
    }

    public boolean isShowElementalDamage() {
        return showElementalDamage;
    }

    public int getDamageChainLength() {
        return damageChainLength;
    }

    public boolean isShowDamageChain() {
        return showDamageChain;
    }

    public boolean isKillFeedEnabled() {
        return killFeedEnabled;
    }

    public boolean isKillFeedContextual() {
        return killFeedContextual;
    }

    public boolean isFullMode() {
        return "full".equalsIgnoreCase(displayMode);
    }

    public boolean isCompactMode() {
        return "compact".equalsIgnoreCase(displayMode);
    }

    // ==================== Setters (camelCase) ====================

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setDisplayMode(String displayMode) {
        this.displayMode = displayMode;
    }

    public void setShowDamageBreakdown(boolean showDamageBreakdown) {
        this.showDamageBreakdown = showDamageBreakdown;
    }

    public void setShowDefensiveStats(boolean showDefensiveStats) {
        this.showDefensiveStats = showDefensiveStats;
    }

    public void setShowElementalDamage(boolean showElementalDamage) {
        this.showElementalDamage = showElementalDamage;
    }

    public void setDamageChainLength(int damageChainLength) {
        this.damageChainLength = damageChainLength;
    }

    public void setShowDamageChain(boolean showDamageChain) {
        this.showDamageChain = showDamageChain;
    }

    public void setKillFeedEnabled(boolean killFeedEnabled) {
        this.killFeedEnabled = killFeedEnabled;
    }

    public void setKillFeedContextual(boolean killFeedContextual) {
        this.killFeedContextual = killFeedContextual;
    }

    // ==================== YAML snake_case Setters ====================

    public void setDisplay_mode(String displayMode) {
        this.displayMode = displayMode;
    }

    public void setShow_damage_breakdown(boolean showDamageBreakdown) {
        this.showDamageBreakdown = showDamageBreakdown;
    }

    public void setShow_defensive_stats(boolean showDefensiveStats) {
        this.showDefensiveStats = showDefensiveStats;
    }

    public void setShow_elemental_damage(boolean showElementalDamage) {
        this.showElementalDamage = showElementalDamage;
    }

    public void setDamage_chain_length(int damageChainLength) {
        this.damageChainLength = damageChainLength;
    }

    public void setShow_damage_chain(boolean showDamageChain) {
        this.showDamageChain = showDamageChain;
    }

    public void setKill_feed_enabled(boolean killFeedEnabled) {
        this.killFeedEnabled = killFeedEnabled;
    }

    public void setKill_feed_contextual(boolean killFeedContextual) {
        this.killFeedContextual = killFeedContextual;
    }

    // ==================== Validation ====================

    /**
     * Validates the configuration values.
     *
     * @throws ConfigValidationException if validation fails
     */
    public void validate() throws ConfigValidationException {
        if (displayMode == null || displayMode.isBlank()) {
            throw new ConfigValidationException("display_mode cannot be empty");
        }

        if (!displayMode.equalsIgnoreCase("full") && !displayMode.equalsIgnoreCase("compact")) {
            throw new ConfigValidationException("display_mode must be 'full' or 'compact', got: " + displayMode);
        }

        if (damageChainLength < 1 || damageChainLength > 20) {
            throw new ConfigValidationException("damage_chain_length must be between 1 and 20, got: " + damageChainLength);
        }
    }

    /**
     * Exception thrown when configuration validation fails.
     */
    public static class ConfigValidationException extends Exception {
        public ConfigValidationException(String message) {
            super(message);
        }
    }
}
