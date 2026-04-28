package io.github.larsonix.trailoforbis.skilltree.config;

/**
 * Settings for skill tree progression and costs.
 * Referenced from RPGConfig.
 */
public class SkillTreeSettings {
    private int pointsPerLevel = 1;
    private int startingPoints = 0;
    private int respecCostPerTier = 100;
    private int fullRespecBaseCost = 1000;
    private int freeRespecs = 3;
    private boolean enabled = true;

    public int getPointsPerLevel() {
        return pointsPerLevel;
    }

    public void setPointsPerLevel(int pointsPerLevel) {
        this.pointsPerLevel = pointsPerLevel;
    }

    public int getStartingPoints() {
        return startingPoints;
    }

    public void setStartingPoints(int startingPoints) {
        this.startingPoints = startingPoints;
    }

    public int getRespecCostPerTier() {
        return respecCostPerTier;
    }

    public void setRespecCostPerTier(int respecCostPerTier) {
        this.respecCostPerTier = respecCostPerTier;
    }

    public int getFullRespecBaseCost() {
        return fullRespecBaseCost;
    }

    public void setFullRespecBaseCost(int fullRespecBaseCost) {
        this.fullRespecBaseCost = fullRespecBaseCost;
    }

    public int getFreeRespecs() {
        return freeRespecs;
    }

    public void setFreeRespecs(int freeRespecs) {
        this.freeRespecs = freeRespecs;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
