package io.github.larsonix.trailoforbis.combat.attackspeed.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-weapon-type attack speed profile loaded from {@code weapon-profiles.yml}.
 *
 * <p>Controls how the player's attack speed stats translate into actual speed
 * changes for a specific weapon type. Different weapons respond differently
 * to the same stat investment:
 * <ul>
 *   <li>Daggers benefit more from raw attack speed (+1.2× weight)</li>
 *   <li>Battleaxes benefit more from cooldown recovery (+1.3× weight)</li>
 *   <li>Crossbows get NO chain acceleration (ammo desync) but DO get cooldown accel</li>
 * </ul>
 *
 * <p>SnakeYAML-compatible POJO — requires no-arg constructor and setters.
 */
public class WeaponSpeedProfile {

    private List<String> weaponTypes = new ArrayList<>();
    private List<String> animationSets = new ArrayList<>();
    private String category = "melee";
    private int comboStages = 1;
    private int comboResetMs = 0;
    private float baseComboPercent = 1.0f;
    private Map<String, Float> statWeights = new LinkedHashMap<>();
    private SpeedLimits speedLimits = new SpeedLimits();

    /**
     * Returns the stat weight for the given stat key, or the default if not configured.
     *
     * @param statKey Stat key (e.g. "attackSpeedPercent", "cooldownRecoveryPercent")
     * @param defaultWeight Default weight if not specified in config
     * @return The weight multiplier (e.g. 1.0 = normal, 0.7 = 70% benefit, 1.3 = 130%)
     */
    public float getStatWeight(String statKey, float defaultWeight) {
        Float weight = statWeights.get(statKey);
        return weight != null ? weight : defaultWeight;
    }

    // ---- Getters / Setters for SnakeYAML ----

    public List<String> getWeaponTypes() {
        return weaponTypes;
    }

    public void setWeaponTypes(List<String> weaponTypes) {
        this.weaponTypes = weaponTypes != null ? weaponTypes : new ArrayList<>();
    }

    public List<String> getAnimationSets() {
        return animationSets;
    }

    public void setAnimationSets(List<String> animationSets) {
        this.animationSets = animationSets != null ? animationSets : new ArrayList<>();
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category != null ? category : "melee";
    }

    public int getComboStages() {
        return comboStages;
    }

    public void setComboStages(int comboStages) {
        this.comboStages = Math.max(1, comboStages);
    }

    public int getComboResetMs() {
        return comboResetMs;
    }

    public void setComboResetMs(int comboResetMs) {
        this.comboResetMs = Math.max(0, comboResetMs);
    }

    public float getBaseComboPercent() {
        return baseComboPercent;
    }

    public void setBaseComboPercent(float baseComboPercent) {
        this.baseComboPercent = baseComboPercent;
    }

    public Map<String, Float> getStatWeights() {
        return statWeights;
    }

    public void setStatWeights(Map<String, Float> statWeights) {
        this.statWeights = statWeights != null ? statWeights : new LinkedHashMap<>();
    }

    public SpeedLimits getSpeedLimits() {
        return speedLimits;
    }

    public void setSpeedLimits(SpeedLimits speedLimits) {
        this.speedLimits = speedLimits != null ? speedLimits : new SpeedLimits();
    }

    /**
     * Nested speed limit configuration per weapon type.
     */
    public static class SpeedLimits {
        private float minMultiplier = 0.5f;
        private float maxMultiplier = 3.0f;
        private float animationSpeedScale = 0.5f;

        public float getMinMultiplier() {
            return minMultiplier;
        }

        public void setMinMultiplier(float minMultiplier) {
            this.minMultiplier = minMultiplier;
        }

        public float getMaxMultiplier() {
            return maxMultiplier;
        }

        public void setMaxMultiplier(float maxMultiplier) {
            this.maxMultiplier = maxMultiplier;
        }

        public float getAnimationSpeedScale() {
            return animationSpeedScale;
        }

        public void setAnimationSpeedScale(float animationSpeedScale) {
            this.animationSpeedScale = animationSpeedScale;
        }
    }

    /**
     * Creates the default profile used when no weapon-specific profile matches.
     */
    public static WeaponSpeedProfile createDefault() {
        WeaponSpeedProfile profile = new WeaponSpeedProfile();
        profile.weaponTypes = List.of("UNKNOWN");
        profile.category = "melee";
        profile.comboStages = 1;
        profile.statWeights.put("attackSpeedPercent", 1.0f);
        profile.statWeights.put("cooldownRecoveryPercent", 1.0f);
        profile.speedLimits.minMultiplier = 0.5f;
        profile.speedLimits.maxMultiplier = 2.0f;
        profile.speedLimits.animationSpeedScale = 0.5f;
        return profile;
    }
}
