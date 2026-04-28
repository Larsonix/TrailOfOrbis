package io.github.larsonix.trailoforbis.mobs.stats;

import javax.annotation.Nonnull;

/**
 * Per-stat configuration that controls how a pool share is converted into a final stat value.
 *
 * <p>Applies a conversion factor to the raw pool share, adds a base value, and clamps
 * the result within configured min/max bounds.
 *
 * @see MobStatType
 */
public class StatConfig {
    private double factor = 1.0;
    private double minValue = 0.0;
    private double maxValue = Double.MAX_VALUE;
    private double baseValue = 0.0;
    private double alphaWeight = 1.0;

    public StatConfig() {
    }

    public StatConfig(double factor, double minValue, double maxValue, double baseValue, double alphaWeight) {
        this.factor = factor;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.baseValue = baseValue;
        this.alphaWeight = alphaWeight;
    }

    public static StatConfig fromMobStatType(@Nonnull MobStatType type) {
        return new StatConfig(1.0, type.minValue, type.maxValue, type.baseValue, type.alphaWeight);
    }

    public double getFactor() {
        return factor;
    }

    public void setFactor(double factor) {
        this.factor = factor;
    }

    public double getMinValue() {
        return minValue;
    }

    public void setMinValue(double minValue) {
        this.minValue = minValue;
    }

    public double getMaxValue() {
        return maxValue;
    }

    public void setMaxValue(double maxValue) {
        this.maxValue = maxValue;
    }

    public double getBaseValue() {
        return baseValue;
    }

    public void setBaseValue(double baseValue) {
        this.baseValue = baseValue;
    }

    public double getAlphaWeight() {
        return alphaWeight;
    }

    public void setAlphaWeight(double alphaWeight) {
        this.alphaWeight = alphaWeight;
    }

    public double applyFactor(double poolShare) {
        return poolShare * factor;
    }

    public double clamp(double value) {
        return Math.max(minValue, Math.min(maxValue, value));
    }

    public double finalize(double poolShare) {
        return clamp(applyFactor(poolShare) + baseValue);
    }

    public static final StatConfig DEFAULT = new StatConfig(1.0, 0.0, Double.MAX_VALUE, 0.0, 1.0);
}
