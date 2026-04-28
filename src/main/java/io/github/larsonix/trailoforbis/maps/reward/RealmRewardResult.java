package io.github.larsonix.trailoforbis.maps.reward;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents the calculated rewards for a player from a realm completion.
 *
 * <p>This record contains all the computed bonuses based on:
 * <ul>
 *   <li>Base rewards from config</li>
 *   <li>Realm modifiers (IIQ, IIR, XP bonus)</li>
 *   <li>Player contribution score</li>
 *   <li>Difficulty multiplier</li>
 * </ul>
 *
 * <p>All multipliers are expressed as decimal values where:
 * <ul>
 *   <li>1.0 = base value (100%)</li>
 *   <li>1.5 = 150% (50% bonus)</li>
 *   <li>2.0 = 200% (100% bonus)</li>
 * </ul>
 *
 * @param playerId The player receiving the rewards
 * @param xpMultiplier Total XP multiplier to apply to kills
 * @param itemQuantityMultiplier Total IIQ multiplier for drop chance
 * @param itemRarityMultiplier Total IIR multiplier for rarity rolls
 * @param stoneDropMultiplier Total stone drop chance multiplier
 * @param mapDropMultiplier Total map drop chance multiplier
 * @param performanceScore Player's performance score (0-100)
 * @param contributionPercent Player's contribution percentage (0-100)
 *
 * @see RealmRewardCalculator
 */
public record RealmRewardResult(
    @Nonnull UUID playerId,
    double xpMultiplier,
    double itemQuantityMultiplier,
    double itemRarityMultiplier,
    double stoneDropMultiplier,
    double mapDropMultiplier,
    int performanceScore,
    double contributionPercent
) {
    /**
     * Creates a new reward result.
     *
     * @throws NullPointerException if playerId is null
     */
    public RealmRewardResult {
        Objects.requireNonNull(playerId, "playerId cannot be null");
    }

    /**
     * Creates a default reward result with base multipliers.
     *
     * @param playerId The player UUID
     * @return A reward result with all multipliers at 1.0
     */
    @Nonnull
    public static RealmRewardResult base(@Nonnull UUID playerId) {
        return new RealmRewardResult(
            playerId,
            1.0,  // xpMultiplier
            1.0,  // itemQuantityMultiplier
            1.0,  // itemRarityMultiplier
            1.0,  // stoneDropMultiplier
            1.0,  // mapDropMultiplier
            50,   // performanceScore (average)
            100.0 // contributionPercent (solo player)
        );
    }

    /**
     * Creates an empty (failed) reward result with zero multipliers.
     *
     * @param playerId The player UUID
     * @return A reward result representing no rewards
     */
    @Nonnull
    public static RealmRewardResult failed(@Nonnull UUID playerId) {
        return new RealmRewardResult(
            playerId,
            0.0,  // xpMultiplier
            0.0,  // itemQuantityMultiplier
            0.0,  // itemRarityMultiplier
            0.0,  // stoneDropMultiplier
            0.0,  // mapDropMultiplier
            0,    // performanceScore
            0.0   // contributionPercent
        );
    }

    /**
     * Checks if this reward result grants any rewards.
     *
     * @return true if at least one multiplier is greater than 0
     */
    public boolean hasRewards() {
        return xpMultiplier > 0 || itemQuantityMultiplier > 0 || itemRarityMultiplier > 0;
    }

    /**
     * Gets the total effective "luck" bonus for loot calculations.
     * Combines IIQ and IIR into a single value.
     *
     * @return Combined luck bonus (1.0 = base)
     */
    public double getEffectiveLuck() {
        // Weight IIR slightly more since rarity is more impactful
        return (itemQuantityMultiplier * 0.4) + (itemRarityMultiplier * 0.6);
    }

    @Override
    public String toString() {
        return String.format(
            "RealmRewardResult{player=%s, xp=%.1fx, iiq=%.1fx, iir=%.1fx, perf=%d, contrib=%.0f%%}",
            playerId.toString().substring(0, 8),
            xpMultiplier,
            itemQuantityMultiplier,
            itemRarityMultiplier,
            performanceScore,
            contributionPercent
        );
    }
}
