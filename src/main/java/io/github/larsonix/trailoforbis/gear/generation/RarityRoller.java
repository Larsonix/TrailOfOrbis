package io.github.larsonix.trailoforbis.gear.generation;

import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig.RarityConfig;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * Determines gear rarity using weighted random selection.
 *
 * <p>Supports rarity bonuses that shift the probability distribution
 * toward rarer items without changing the relative ordering.
 *
 * <p>This class is thread-safe if the provided Random is thread-safe.
 */
public final class RarityRoller {

    private final GearBalanceConfig config;
    private final Random random;

    // Cached base weights for performance
    private final Map<GearRarity, Double> baseWeights;
    private final double totalBaseWeight;

    // Rarity order (lowest to highest)
    // Note: UNIQUE has very low drop weight (0.05 by default) and is meant for special/quest items
    private static final GearRarity[] RARITY_ORDER = {
        GearRarity.COMMON,
        GearRarity.UNCOMMON,
        GearRarity.RARE,
        GearRarity.EPIC,
        GearRarity.LEGENDARY,
        GearRarity.MYTHIC,
        GearRarity.UNIQUE
    };

    /**
     * Creates a RarityRoller with the given config and random source.
     *
     * @param config The gear balance configuration
     * @param random The random number generator
     */
    public RarityRoller(GearBalanceConfig config, Random random) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.random = Objects.requireNonNull(random, "random cannot be null");

        // Cache base weights
        this.baseWeights = new EnumMap<>(GearRarity.class);
        double total = 0;
        for (GearRarity rarity : GearRarity.values()) {
            double weight = config.rarityConfig(rarity).dropWeight();
            baseWeights.put(rarity, weight);
            total += weight;
        }
        this.totalBaseWeight = total;

        // Fail fast if config has no valid weights - indicates misconfiguration
        if (this.totalBaseWeight <= 0) {
            throw new IllegalStateException(
                "Total rarity weight must be positive, got: " + totalBaseWeight +
                ". Check gear-balance.yml rarity dropWeight values.");
        }
    }

    /**
     * Creates a RarityRoller with a new Random instance.
     */
    public RarityRoller(GearBalanceConfig config) {
        this(config, new Random());
    }

    /**
     * Rolls a rarity with no bonus.
     *
     * @return The rolled rarity
     */
    public GearRarity roll() {
        return roll(0.0);
    }

    /**
     * Rolls a rarity with the given rarity bonus.
     *
     * <p>The rarity bonus shifts the probability distribution toward
     * rarer items. A bonus of 0.0 uses the base weights from config.
     * A bonus of 1.0 (100%) roughly doubles the chance of rarer items.
     *
     * <p>Algorithm: The bonus reduces the weight of common items and
     * increases the weight of rare items proportionally.
     *
     * @param rarityBonus The rarity bonus (0.0 = no bonus, 1.0 = 100% bonus)
     * @return The rolled rarity
     */
    public GearRarity roll(double rarityBonus) {
        // Clamp bonus to reasonable range
        rarityBonus = Math.max(0, Math.min(10, rarityBonus));

        // Calculate adjusted weights
        Map<GearRarity, Double> adjustedWeights = calculateAdjustedWeights(rarityBonus);

        // Calculate total adjusted weight
        double totalWeight = adjustedWeights.values().stream()
                .mapToDouble(Double::doubleValue)
                .sum();

        // Roll
        double roll = random.nextDouble() * totalWeight;
        double cumulative = 0;

        for (GearRarity rarity : RARITY_ORDER) {
            cumulative += adjustedWeights.get(rarity);
            if (roll < cumulative) {
                return rarity;
            }
        }

        // Should never reach here, but return highest rarity as fallback
        return GearRarity.UNIQUE;
    }

    /**
     * Calculates adjusted weights based on rarity bonus.
     *
     * <p>The algorithm works by:
     * 1. Calculating a "rarity index" (0 for COMMON, 5 for MYTHIC)
     * 2. Applying a multiplier that increases with rarity index
     * 3. The bonus amplifies this effect
     *
     * <p>Formula: adjustedWeight = baseWeight × (1 + rarityBonus × rarityIndex / 5)
     *
     * <p>This means:
     * - COMMON (index 0): weight unchanged
     * - MYTHIC (index 5): weight multiplied by (1 + rarityBonus)
     *
     * @param rarityBonus The rarity bonus
     * @return Map of adjusted weights per rarity
     */
    Map<GearRarity, Double> calculateAdjustedWeights(double rarityBonus) {
        Map<GearRarity, Double> adjusted = new EnumMap<>(GearRarity.class);

        for (int i = 0; i < RARITY_ORDER.length; i++) {
            GearRarity rarity = RARITY_ORDER[i];
            double baseWeight = baseWeights.get(rarity);

            // Calculate multiplier based on rarity index
            // Common (i=0) gets no bonus, Mythic (i=5) gets full bonus
            double rarityFactor = (double) i / (RARITY_ORDER.length - 1);
            double multiplier = 1.0 + (rarityBonus * rarityFactor);

            adjusted.put(rarity, baseWeight * multiplier);
        }

        return adjusted;
    }

    /**
     * Gets the probability of rolling a specific rarity with given bonus.
     *
     * <p>Useful for testing and debugging.
     *
     * @param rarity The target rarity
     * @param rarityBonus The rarity bonus
     * @return Probability between 0 and 1
     */
    public double getProbability(GearRarity rarity, double rarityBonus) {
        Map<GearRarity, Double> adjusted = calculateAdjustedWeights(rarityBonus);
        double totalWeight = adjusted.values().stream()
                .mapToDouble(Double::doubleValue)
                .sum();
        return adjusted.get(rarity) / totalWeight;
    }

    /**
     * Gets the base weight for a rarity (from config).
     */
    public double getBaseWeight(GearRarity rarity) {
        return baseWeights.get(rarity);
    }

    /**
     * Gets the total base weight across all rarities.
     */
    public double getTotalBaseWeight() {
        return totalBaseWeight;
    }
}
