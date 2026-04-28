package io.github.larsonix.trailoforbis.gear.generation;

import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig.QualityConfig;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig.QualityDropDistribution;

import java.util.Objects;
import java.util.Random;

/**
 * Determines gear quality using the configured distribution.
 *
 * <p>Quality follows a distribution with defined brackets:
 * <ul>
 *   <li>Poor (1-25%)</li>
 *   <li>Below Average (26-49%)</li>
 *   <li>Normal (exactly 50%)</li>
 *   <li>Above Average (51-75%)</li>
 *   <li>Excellent (76-100%)</li>
 *   <li>Perfect (101% - drop only)</li>
 * </ul>
 *
 * <p>This class is thread-safe if the provided Random is thread-safe.
 */
public final class QualityRoller {

    private final QualityConfig config;
    private final Random random;

    // Pre-calculated cumulative probabilities
    private final double cumulativePoor;
    private final double cumulativeBelowAverage;
    private final double cumulativeNormal;
    private final double cumulativeAboveAverage;
    private final double cumulativeExcellent;
    // Perfect is the remainder (1.0 - cumulativeExcellent)

    /**
     * Creates a QualityRoller with the given config and random source.
     *
     * @param config The quality configuration
     * @param random The random number generator
     */
    public QualityRoller(QualityConfig config, Random random) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.random = Objects.requireNonNull(random, "random cannot be null");

        // Pre-calculate cumulative probabilities
        QualityDropDistribution dist = config.dropDistribution();
        this.cumulativePoor = dist.poor();
        this.cumulativeBelowAverage = cumulativePoor + dist.belowAverage();
        this.cumulativeNormal = cumulativeBelowAverage + dist.normal();
        this.cumulativeAboveAverage = cumulativeNormal + dist.aboveAverage();
        this.cumulativeExcellent = cumulativeAboveAverage + dist.excellent();
        // Perfect is everything after cumulativeExcellent
    }

    /**
     * Creates a QualityRoller with a new Random instance.
     */
    public QualityRoller(QualityConfig config) {
        this(config, new Random());
    }

    /**
     * Rolls a quality value.
     *
     * <p>The returned value is in the range [min, perfect] where:
     * <ul>
     *   <li>1-25 = Poor</li>
     *   <li>26-49 = Below Average</li>
     *   <li>50 = Normal</li>
     *   <li>51-75 = Above Average</li>
     *   <li>76-100 = Excellent</li>
     *   <li>101 = Perfect (rare drop)</li>
     * </ul>
     *
     * @return The rolled quality value
     */
    public int roll() {
        double roll = random.nextDouble();

        if (roll < cumulativePoor) {
            // Poor: 1-25
            return rollInRange(config.min(), 25);
        } else if (roll < cumulativeBelowAverage) {
            // Below Average: 26-49
            return rollInRange(26, 49);
        } else if (roll < cumulativeNormal) {
            // Normal: exactly 50
            return config.baseline();
        } else if (roll < cumulativeAboveAverage) {
            // Above Average: 51-75
            return rollInRange(51, 75);
        } else if (roll < cumulativeExcellent) {
            // Excellent: 76-100
            return rollInRange(76, config.max());
        } else {
            // Perfect: 101
            return config.perfect();
        }
    }

    /**
     * Rolls a quality value that is never perfect.
     *
     * <p>Used for crafted items or stone-rerolled items where
     * perfect quality is not allowed.
     *
     * @return The rolled quality value (1-100, never 101)
     */
    public int rollNonPerfect() {
        double roll = random.nextDouble();

        // Renormalize probabilities excluding perfect
        double totalNonPerfect = cumulativeExcellent;

        double normalized = roll * totalNonPerfect;

        if (normalized < cumulativePoor) {
            return rollInRange(config.min(), 25);
        } else if (normalized < cumulativeBelowAverage) {
            return rollInRange(26, 49);
        } else if (normalized < cumulativeNormal) {
            return config.baseline();
        } else if (normalized < cumulativeAboveAverage) {
            return rollInRange(51, 75);
        } else {
            return rollInRange(76, config.max());
        }
    }

    /**
     * Rolls a value uniformly within a range (inclusive).
     */
    private int rollInRange(int min, int max) {
        if (min == max) {
            return min;
        }
        return min + random.nextInt(max - min + 1);
    }

    /**
     * Gets the probability of rolling a quality in a specific bracket.
     *
     * @param bracket The quality bracket name
     * @return Probability between 0 and 1
     */
    public double getBracketProbability(String bracket) {
        QualityDropDistribution dist = config.dropDistribution();
        return switch (bracket.toLowerCase()) {
            case "poor" -> dist.poor();
            case "below_average", "belowaverage" -> dist.belowAverage();
            case "normal" -> dist.normal();
            case "above_average", "aboveaverage" -> dist.aboveAverage();
            case "excellent" -> dist.excellent();
            case "perfect" -> dist.perfect();
            default -> 0;
        };
    }

    /**
     * Gets the bracket name for a quality value.
     *
     * @param quality The quality value
     * @return The bracket name
     */
    public String getBracketName(int quality) {
        if (quality >= config.perfect()) {
            return "Perfect";
        } else if (quality >= 76) {
            return "Excellent";
        } else if (quality >= 51) {
            return "Above Average";
        } else if (quality == 50) {
            return "Normal";
        } else if (quality >= 26) {
            return "Below Average";
        } else {
            return "Poor";
        }
    }

    /**
     * Gets the quality configuration.
     */
    public QualityConfig getConfig() {
        return config;
    }
}
