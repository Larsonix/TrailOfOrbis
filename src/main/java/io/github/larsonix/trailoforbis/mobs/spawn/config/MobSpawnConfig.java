package io.github.larsonix.trailoforbis.mobs.spawn.config;

import io.github.larsonix.trailoforbis.mobs.classification.RPGMobClass;

import javax.annotation.Nonnull;
import java.util.EnumMap;
import java.util.Map;

/**
 * Configuration for the mob spawn system.
 *
 * <p>Controls spawn rates by mob class and player level, replacing
 * the previous spawn multiplier system with a more configurable approach.
 */
public class MobSpawnConfig {

    private boolean enabled = true;
    private Map<String, Double> classMultipliers = createDefaultClassMultipliers();
    private LevelScalingConfig levelScaling = new LevelScalingConfig();
    private SpawnCapsConfig spawnCaps = new SpawnCapsConfig();
    private String preset = "CUSTOM";

    // Transient cached enum map (built from classMultipliers)
    private transient EnumMap<RPGMobClass, Double> classMultiplierCache;

    public MobSpawnConfig() {
    }

    /**
     * Validates configuration values.
     *
     * @throws ConfigValidationException if any value is invalid
     */
    public void validate() throws ConfigValidationException {
        if (levelScaling == null) {
            levelScaling = new LevelScalingConfig();
        }
        levelScaling.validate();

        if (spawnCaps == null) {
            spawnCaps = new SpawnCapsConfig();
        }
        spawnCaps.validate();

        // Apply preset if not CUSTOM
        if (preset != null && !preset.equalsIgnoreCase("CUSTOM")) {
            applyPreset(preset);
        }

        // Build cache
        buildClassMultiplierCache();
    }

    /**
     * Applies a difficulty preset, overriding individual settings.
     */
    private void applyPreset(String presetName) {
        switch (presetName.toUpperCase()) {
            case "EASY" -> {
                classMultipliers.put("MONSTER", 1.0);
                classMultipliers.put("ELITE", 0.5);
                classMultipliers.put("LEGENDARY", 0.3);
                classMultipliers.put("MYTHIC", 0.1);
                levelScaling.setLevelPerIncrement(100);
                levelScaling.setIncrementBonus(0.1);
            }
            case "NORMAL" -> {
                classMultipliers.put("MONSTER", 1.5);
                classMultipliers.put("ELITE", 0.8);
                classMultipliers.put("LEGENDARY", 0.5);
                classMultipliers.put("MYTHIC", 0.2);
                levelScaling.setLevelPerIncrement(50);
                levelScaling.setIncrementBonus(0.2);
            }
            case "HARD" -> {
                classMultipliers.put("MONSTER", 2.0);
                classMultipliers.put("ELITE", 1.5);
                classMultipliers.put("LEGENDARY", 1.0);
                classMultipliers.put("MYTHIC", 0.5);
                levelScaling.setLevelPerIncrement(25);
                levelScaling.setIncrementBonus(0.3);
            }
            case "NIGHTMARE" -> {
                classMultipliers.put("MONSTER", 3.0);
                classMultipliers.put("ELITE", 2.5);
                classMultipliers.put("LEGENDARY", 2.0);
                classMultipliers.put("MYTHIC", 1.5);
                levelScaling.setLevelPerIncrement(10);
                levelScaling.setIncrementBonus(0.5);
            }
        }
    }

    private void buildClassMultiplierCache() {
        classMultiplierCache = new EnumMap<>(RPGMobClass.class);
        for (RPGMobClass mobClass : RPGMobClass.values()) {
            String key = mobClass.name();
            double multiplier = classMultipliers.getOrDefault(key, 1.0);
            classMultiplierCache.put(mobClass, multiplier);
        }
    }

    private static Map<String, Double> createDefaultClassMultipliers() {
        Map<String, Double> defaults = new java.util.HashMap<>();
        defaults.put("AMBIENT", 1.0);
        defaults.put("LIVESTOCK", 1.0);
        defaults.put("BEAST", 1.2);
        defaults.put("MONSTER", 1.5);
        defaults.put("ELITE", 0.8);
        defaults.put("LEGENDARY", 0.5);
        defaults.put("MYTHIC", 0.2);
        return defaults;
    }

    // Getters

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Gets the spawn multiplier for a mob class.
     *
     * @param mobClass The mob class
     * @return Spawn multiplier (1.0 = normal, >1 = more spawns, <1 = fewer)
     */
    public double getClassMultiplier(@Nonnull RPGMobClass mobClass) {
        if (classMultiplierCache == null) {
            buildClassMultiplierCache();
        }
        return classMultiplierCache.getOrDefault(mobClass, 1.0);
    }

    @Nonnull
    public Map<String, Double> getClassMultipliers() {
        return classMultipliers;
    }

    @Nonnull
    public LevelScalingConfig getLevelScaling() {
        return levelScaling;
    }

    @Nonnull
    public SpawnCapsConfig getSpawnCaps() {
        return spawnCaps;
    }

    @Nonnull
    public String getPreset() {
        return preset;
    }

    // Setters (snake_case for YAML compatibility)

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setClass_multipliers(Map<String, Double> classMultipliers) {
        this.classMultipliers = classMultipliers;
        this.classMultiplierCache = null; // Invalidate cache
    }

    public void setClassMultipliers(Map<String, Double> classMultipliers) {
        setClass_multipliers(classMultipliers);
    }

    public void setLevel_scaling(LevelScalingConfig levelScaling) {
        this.levelScaling = levelScaling;
    }

    public void setLevelScaling(LevelScalingConfig levelScaling) {
        setLevel_scaling(levelScaling);
    }

    public void setSpawn_caps(SpawnCapsConfig spawnCaps) {
        this.spawnCaps = spawnCaps;
    }

    public void setSpawnCaps(SpawnCapsConfig spawnCaps) {
        setSpawn_caps(spawnCaps);
    }

    public void setPreset(String preset) {
        this.preset = preset;
    }

    /**
     * Exception thrown when configuration validation fails.
     */
    public static class ConfigValidationException extends RuntimeException {
        public ConfigValidationException(String message) {
            super(message);
        }
    }

    /**
     * Level-based spawn scaling configuration.
     */
    public static class LevelScalingConfig {
        private boolean enabled = true;
        private double detectionRadius = 256.0;
        private int levelPerIncrement = 50;
        private double incrementBonus = 0.2;
        private double maxLevelMultiplier = 5.0;
        private boolean hostileOnly = true;
        private String formula = "logarithmic";

        // Cached squared radius
        private transient double detectionRadiusSquared = -1;

        public void validate() throws ConfigValidationException {
            if (detectionRadius <= 0) {
                throw new ConfigValidationException(
                    "level_scaling.detection_radius must be positive, got: " + detectionRadius);
            }
            if (levelPerIncrement <= 0) {
                throw new ConfigValidationException(
                    "level_scaling.level_per_increment must be positive, got: " + levelPerIncrement);
            }
            if (incrementBonus < 0) {
                throw new ConfigValidationException(
                    "level_scaling.increment_bonus cannot be negative, got: " + incrementBonus);
            }
            if (maxLevelMultiplier < 1.0) {
                throw new ConfigValidationException(
                    "level_scaling.max_level_multiplier must be >= 1.0, got: " + maxLevelMultiplier);
            }
            detectionRadiusSquared = detectionRadius * detectionRadius;
        }

        // Getters

        public boolean isEnabled() {
            return enabled;
        }

        public double getDetectionRadius() {
            return detectionRadius;
        }

        public double getDetectionRadiusSquared() {
            if (detectionRadiusSquared < 0) {
                detectionRadiusSquared = detectionRadius * detectionRadius;
            }
            return detectionRadiusSquared;
        }

        public int getLevelPerIncrement() {
            return levelPerIncrement;
        }

        public double getIncrementBonus() {
            return incrementBonus;
        }

        public double getMaxLevelMultiplier() {
            return maxLevelMultiplier;
        }

        public boolean isHostileOnly() {
            return hostileOnly;
        }

        public String getFormula() {
            return formula;
        }

        /**
         * Checks if logarithmic formula should be used.
         *
         * @return true if formula is "logarithmic" (case-insensitive)
         */
        public boolean isLogarithmicFormula() {
            return "logarithmic".equalsIgnoreCase(formula);
        }

        // Setters (snake_case for YAML compatibility)

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public void setDetection_radius(double detectionRadius) {
            this.detectionRadius = detectionRadius;
            this.detectionRadiusSquared = -1;
        }

        public void setDetectionRadius(double detectionRadius) {
            setDetection_radius(detectionRadius);
        }

        public void setLevel_per_increment(int levelPerIncrement) {
            this.levelPerIncrement = levelPerIncrement;
        }

        public void setLevelPerIncrement(int levelPerIncrement) {
            setLevel_per_increment(levelPerIncrement);
        }

        public void setIncrement_bonus(double incrementBonus) {
            this.incrementBonus = incrementBonus;
        }

        public void setIncrementBonus(double incrementBonus) {
            setIncrement_bonus(incrementBonus);
        }

        public void setMax_level_multiplier(double maxLevelMultiplier) {
            this.maxLevelMultiplier = maxLevelMultiplier;
        }

        public void setMaxLevelMultiplier(double maxLevelMultiplier) {
            setMax_level_multiplier(maxLevelMultiplier);
        }

        public void setHostile_only(boolean hostileOnly) {
            this.hostileOnly = hostileOnly;
        }

        public void setHostileOnly(boolean hostileOnly) {
            setHostile_only(hostileOnly);
        }

        public void setFormula(String formula) {
            this.formula = formula;
        }
    }

    /**
     * Spawn caps configuration.
     */
    public static class SpawnCapsConfig {
        private int maxHostilePerChunk = 20;
        private double capStartDistance = 100.0;

        public void validate() throws ConfigValidationException {
            if (maxHostilePerChunk < 0) {
                throw new ConfigValidationException(
                    "spawn_caps.max_hostile_per_chunk cannot be negative, got: " + maxHostilePerChunk);
            }
            if (capStartDistance < 0) {
                throw new ConfigValidationException(
                    "spawn_caps.cap_start_distance cannot be negative, got: " + capStartDistance);
            }
        }

        // Getters

        public int getMaxHostilePerChunk() {
            return maxHostilePerChunk;
        }

        public double getCapStartDistance() {
            return capStartDistance;
        }

        // Setters (snake_case for YAML compatibility)

        public void setMax_hostile_per_chunk(int maxHostilePerChunk) {
            this.maxHostilePerChunk = maxHostilePerChunk;
        }

        public void setMaxHostilePerChunk(int maxHostilePerChunk) {
            setMax_hostile_per_chunk(maxHostilePerChunk);
        }

        public void setCap_start_distance(double capStartDistance) {
            this.capStartDistance = capStartDistance;
        }

        public void setCapStartDistance(double capStartDistance) {
            setCap_start_distance(capStartDistance);
        }
    }
}
