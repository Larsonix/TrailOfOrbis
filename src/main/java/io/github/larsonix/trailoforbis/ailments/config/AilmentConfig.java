package io.github.larsonix.trailoforbis.ailments.config;

import javax.annotation.Nonnull;

/**
 * Configuration for the ailment system.
 *
 * <p>Controls ailment parameters including base chances, durations,
 * damage ratios, and caps for each ailment type.
 *
 * <p>Loaded from {@code config/ailments.yml} via SnakeYAML.
 */
public class AilmentConfig {

    private boolean enabled = true;
    private float tickRateSeconds = 0.25f;

    // Burn configuration
    private BurnConfig burn = new BurnConfig();

    // Freeze configuration
    private FreezeConfig freeze = new FreezeConfig();

    // Shock configuration
    private ShockConfig shock = new ShockConfig();

    // Poison configuration
    private PoisonConfig poison = new PoisonConfig();

    // ==================== Getters ====================

    /**
     * Whether the ailment system is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Tick rate for DoT processing in seconds.
     * Lower = more frequent ticks = smoother DoT but more CPU.
     */
    public float getTickRateSeconds() {
        return tickRateSeconds;
    }

    @Nonnull
    public BurnConfig getBurn() {
        return burn;
    }

    @Nonnull
    public FreezeConfig getFreeze() {
        return freeze;
    }

    @Nonnull
    public ShockConfig getShock() {
        return shock;
    }

    @Nonnull
    public PoisonConfig getPoison() {
        return poison;
    }

    // ==================== Setters (camelCase) ====================

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setTickRateSeconds(float tickRateSeconds) {
        this.tickRateSeconds = tickRateSeconds;
    }

    public void setBurn(BurnConfig burn) {
        this.burn = burn != null ? burn : new BurnConfig();
    }

    public void setFreeze(FreezeConfig freeze) {
        this.freeze = freeze != null ? freeze : new FreezeConfig();
    }

    public void setShock(ShockConfig shock) {
        this.shock = shock != null ? shock : new ShockConfig();
    }

    public void setPoison(PoisonConfig poison) {
        this.poison = poison != null ? poison : new PoisonConfig();
    }

    // ==================== YAML snake_case Setters ====================

    public void setTick_rate_seconds(float tickRateSeconds) {
        this.tickRateSeconds = tickRateSeconds;
    }

    // ==================== Validation ====================

    /**
     * @throws ConfigValidationException if validation fails
     */
    public void validate() throws ConfigValidationException {
        if (tickRateSeconds <= 0 || tickRateSeconds > 5.0f) {
            throw new ConfigValidationException("tick_rate_seconds must be between 0 and 5, got: " + tickRateSeconds);
        }

        burn.validate();
        freeze.validate();
        shock.validate();
        poison.validate();
    }

    /**
     * Exception thrown when configuration validation fails.
     */
    public static class ConfigValidationException extends Exception {
        public ConfigValidationException(String message) {
            super(message);
        }
    }

    // ==================== Nested Config Classes ====================

    /**
     * Burn (Fire) ailment configuration.
     */
    public static class BurnConfig {
        private float baseChance = 10.0f;
        private float baseDuration = 4.0f;
        private float damageRatio = 0.5f;

        public float getBaseChance() {
            return baseChance;
        }

        public float getBaseDuration() {
            return baseDuration;
        }

        public float getDamageRatio() {
            return damageRatio;
        }

        public void setBaseChance(float baseChance) {
            this.baseChance = baseChance;
        }

        public void setBaseDuration(float baseDuration) {
            this.baseDuration = baseDuration;
        }

        public void setDamageRatio(float damageRatio) {
            this.damageRatio = damageRatio;
        }

        // YAML snake_case setters
        public void setBase_chance(float baseChance) {
            this.baseChance = baseChance;
        }

        public void setBase_duration(float baseDuration) {
            this.baseDuration = baseDuration;
        }

        public void setDamage_ratio(float damageRatio) {
            this.damageRatio = damageRatio;
        }

        public void validate() throws ConfigValidationException {
            if (baseChance < 0) {
                throw new ConfigValidationException("burn.base_chance cannot be negative");
            }
            if (baseDuration <= 0) {
                throw new ConfigValidationException("burn.base_duration must be positive");
            }
            if (damageRatio < 0 || damageRatio > 2.0f) {
                throw new ConfigValidationException("burn.damage_ratio must be between 0 and 2");
            }
        }
    }

    /**
     * Freeze (Cold) ailment configuration.
     */
    public static class FreezeConfig {
        private float baseChance = 10.0f;
        private float baseDuration = 3.0f;
        private float maxSlowPercent = 30.0f;
        private float minMagnitude = 5.0f;

        public float getBaseChance() {
            return baseChance;
        }

        public float getBaseDuration() {
            return baseDuration;
        }

        public float getMaxSlowPercent() {
            return maxSlowPercent;
        }

        public float getMinMagnitude() {
            return minMagnitude;
        }

        public void setBaseChance(float baseChance) {
            this.baseChance = baseChance;
        }

        public void setBaseDuration(float baseDuration) {
            this.baseDuration = baseDuration;
        }

        public void setMaxSlowPercent(float maxSlowPercent) {
            this.maxSlowPercent = maxSlowPercent;
        }

        public void setMinMagnitude(float minMagnitude) {
            this.minMagnitude = minMagnitude;
        }

        // YAML snake_case setters
        public void setBase_chance(float baseChance) {
            this.baseChance = baseChance;
        }

        public void setBase_duration(float baseDuration) {
            this.baseDuration = baseDuration;
        }

        public void setMax_slow_percent(float maxSlowPercent) {
            this.maxSlowPercent = maxSlowPercent;
        }

        public void setMin_magnitude(float minMagnitude) {
            this.minMagnitude = minMagnitude;
        }

        public void validate() throws ConfigValidationException {
            if (baseChance < 0) {
                throw new ConfigValidationException("freeze.base_chance cannot be negative");
            }
            if (baseDuration <= 0) {
                throw new ConfigValidationException("freeze.base_duration must be positive");
            }
            if (maxSlowPercent <= 0 || maxSlowPercent > 100) {
                throw new ConfigValidationException("freeze.max_slow_percent must be between 0 and 100");
            }
            if (minMagnitude < 0 || minMagnitude > maxSlowPercent) {
                throw new ConfigValidationException("freeze.min_magnitude must be between 0 and max_slow_percent");
            }
        }
    }

    /**
     * Shock (Lightning) ailment configuration.
     */
    public static class ShockConfig {
        private float baseChance = 10.0f;
        private float baseDuration = 2.0f;
        private float maxDamageIncrease = 50.0f;
        private float minMagnitude = 5.0f;

        public float getBaseChance() {
            return baseChance;
        }

        public float getBaseDuration() {
            return baseDuration;
        }

        public float getMaxDamageIncrease() {
            return maxDamageIncrease;
        }

        public float getMinMagnitude() {
            return minMagnitude;
        }

        public void setBaseChance(float baseChance) {
            this.baseChance = baseChance;
        }

        public void setBaseDuration(float baseDuration) {
            this.baseDuration = baseDuration;
        }

        public void setMaxDamageIncrease(float maxDamageIncrease) {
            this.maxDamageIncrease = maxDamageIncrease;
        }

        public void setMinMagnitude(float minMagnitude) {
            this.minMagnitude = minMagnitude;
        }

        // YAML snake_case setters
        public void setBase_chance(float baseChance) {
            this.baseChance = baseChance;
        }

        public void setBase_duration(float baseDuration) {
            this.baseDuration = baseDuration;
        }

        public void setMax_damage_increase(float maxDamageIncrease) {
            this.maxDamageIncrease = maxDamageIncrease;
        }

        public void setMin_magnitude(float minMagnitude) {
            this.minMagnitude = minMagnitude;
        }

        public void validate() throws ConfigValidationException {
            if (baseChance < 0) {
                throw new ConfigValidationException("shock.base_chance cannot be negative");
            }
            if (baseDuration <= 0) {
                throw new ConfigValidationException("shock.base_duration must be positive");
            }
            if (maxDamageIncrease <= 0 || maxDamageIncrease > 200) {
                throw new ConfigValidationException("shock.max_damage_increase must be between 0 and 200");
            }
            if (minMagnitude < 0 || minMagnitude > maxDamageIncrease) {
                throw new ConfigValidationException("shock.min_magnitude must be between 0 and max_damage_increase");
            }
        }
    }

    /**
     * Poison (Chaos) ailment configuration.
     */
    public static class PoisonConfig {
        private float baseChance = 10.0f;
        private float baseDuration = 5.0f;
        private float damageRatio = 0.3f;
        private int maxStacks = 10;

        public float getBaseChance() {
            return baseChance;
        }

        public float getBaseDuration() {
            return baseDuration;
        }

        public float getDamageRatio() {
            return damageRatio;
        }

        public int getMaxStacks() {
            return maxStacks;
        }

        public void setBaseChance(float baseChance) {
            this.baseChance = baseChance;
        }

        public void setBaseDuration(float baseDuration) {
            this.baseDuration = baseDuration;
        }

        public void setDamageRatio(float damageRatio) {
            this.damageRatio = damageRatio;
        }

        public void setMaxStacks(int maxStacks) {
            this.maxStacks = maxStacks;
        }

        // YAML snake_case setters
        public void setBase_chance(float baseChance) {
            this.baseChance = baseChance;
        }

        public void setBase_duration(float baseDuration) {
            this.baseDuration = baseDuration;
        }

        public void setDamage_ratio(float damageRatio) {
            this.damageRatio = damageRatio;
        }

        public void setMax_stacks(int maxStacks) {
            this.maxStacks = maxStacks;
        }

        public void validate() throws ConfigValidationException {
            if (baseChance < 0) {
                throw new ConfigValidationException("poison.base_chance cannot be negative");
            }
            if (baseDuration <= 0) {
                throw new ConfigValidationException("poison.base_duration must be positive");
            }
            if (damageRatio < 0 || damageRatio > 2.0f) {
                throw new ConfigValidationException("poison.damage_ratio must be between 0 and 2");
            }
            if (maxStacks < 1 || maxStacks > 100) {
                throw new ConfigValidationException("poison.max_stacks must be between 1 and 100");
            }
        }
    }
}
