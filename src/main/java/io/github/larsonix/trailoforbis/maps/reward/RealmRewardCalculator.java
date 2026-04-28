package io.github.larsonix.trailoforbis.maps.reward;

import io.github.larsonix.trailoforbis.maps.config.RealmsConfig;
import io.github.larsonix.trailoforbis.maps.core.RealmCompletionTracker;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance.CompletionReason;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifier;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifierType;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.UUID;

/**
 * Calculates rewards for players completing a realm.
 *
 * <p>The reward calculation takes into account:
 * <ul>
 *   <li><b>Completion status:</b> Full rewards for COMPLETED, partial for TIMEOUT</li>
 *   <li><b>Realm modifiers:</b> IIQ, IIR, XP bonuses from map modifiers</li>
 *   <li><b>Difficulty scaling:</b> Higher difficulty = better rewards</li>
 *   <li><b>Player contribution:</b> Kill count and damage dealt</li>
 *   <li><b>Performance score:</b> Efficiency, deaths, etc.</li>
 * </ul>
 *
 * <h2>Multiplier Stacking</h2>
 * <p>Multipliers stack additively within a category, then multiply together:
 * <pre>
 * Base XP = config.baseXpMultiplier
 * + IIQ modifier bonus
 * + Difficulty bonus
 * × Completion bonus (1.0 for complete, 0.5 for timeout)
 * × Contribution factor (your kills / total kills)
 * </pre>
 *
 * @see RealmRewardResult
 * @see RealmRewardService
 */
public class RealmRewardCalculator {

    private final RealmsConfig config;

    /**
     * Minimum contribution percentage to receive rewards.
     */
    private static final double MIN_CONTRIBUTION_PERCENT = 1.0;

    /**
     * Multiplier applied to rewards when realm times out (partial completion).
     */
    private static final double TIMEOUT_PENALTY_MULTIPLIER = 0.5;

    /**
     * Creates a new reward calculator.
     *
     * @param config The realms configuration
     */
    public RealmRewardCalculator(@Nonnull RealmsConfig config) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
    }

    /**
     * Calculates rewards for a player in a completed realm.
     *
     * @param realm The completed realm instance
     * @param playerId The player UUID
     * @return The calculated reward result
     */
    @Nonnull
    public RealmRewardResult calculate(
            @Nonnull RealmInstance realm,
            @Nonnull UUID playerId) {

        Objects.requireNonNull(realm, "realm cannot be null");
        Objects.requireNonNull(playerId, "playerId cannot be null");

        // Check completion status
        CompletionReason reason = realm.getCompletionReason();
        if (reason == null || reason == CompletionReason.ERROR || reason == CompletionReason.ABANDONED) {
            return RealmRewardResult.failed(playerId);
        }

        // Get completion tracker for player stats
        RealmCompletionTracker tracker = realm.getCompletionTracker();
        RealmMapData mapData = realm.getMapData();

        // Calculate player contribution
        int playerKills = tracker.getPlayerKills(playerId);
        int totalKills = tracker.getKilledByPlayers();
        double contributionPercent = totalKills > 0
            ? (playerKills * 100.0 / totalKills)
            : 0.0;

        // No rewards if contribution too low (prevents AFK abuse)
        if (contributionPercent < MIN_CONTRIBUTION_PERCENT && totalKills > 10) {
            return RealmRewardResult.failed(playerId);
        }

        // Calculate performance score from tracker
        int performanceScore = calculatePerformanceScore(tracker, playerId, totalKills);

        // Base multipliers from config
        double xpMultiplier = config.getBaseXpMultiplier();
        double lootMultiplier = config.getBaseLootMultiplier();
        double iiqBonus = mapData.getTotalItemQuantity() / 100.0;
        double iirBonus = 0.0;
        double stoneBonus = 0.0;
        double xpBonus = 0.0;

        // Add modifier bonuses
        for (RealmModifier modifier : mapData.modifiers()) {
            double value = modifier.value() / 100.0; // Convert percentage to decimal

            switch (modifier.type()) {
                case ITEM_RARITY -> iirBonus += value;
                case EXPERIENCE_BONUS -> xpBonus += value;
                case STONE_DROP_BONUS -> stoneBonus += value;
                default -> { } // Other modifiers don't affect rewards directly
            }
        }

        // Add difficulty scaling bonus
        int difficulty = mapData.getDifficultyRating();
        double difficultyBonus = difficulty * config.getDifficultyRewardScaling();

        // Calculate final multipliers
        double finalXpMultiplier = (xpMultiplier + xpBonus + difficultyBonus);
        double finalIiqMultiplier = (lootMultiplier + iiqBonus + difficultyBonus);
        double finalIirMultiplier = (lootMultiplier + iirBonus + difficultyBonus);
        double finalStoneMultiplier = (1.0 + stoneBonus + difficultyBonus);
        double finalMapMultiplier = (1.0 + difficultyBonus);

        // Apply completion penalty if timed out
        if (reason == CompletionReason.TIMEOUT) {
            finalXpMultiplier *= TIMEOUT_PENALTY_MULTIPLIER;
            finalIiqMultiplier *= TIMEOUT_PENALTY_MULTIPLIER;
            finalIirMultiplier *= TIMEOUT_PENALTY_MULTIPLIER;
            finalStoneMultiplier *= TIMEOUT_PENALTY_MULTIPLIER;
            finalMapMultiplier *= TIMEOUT_PENALTY_MULTIPLIER;
        }

        // Scale by contribution (for multiplayer fairness)
        double contributionScale = calculateContributionScale(contributionPercent, realm.getAllParticipants().size());

        return new RealmRewardResult(
            playerId,
            finalXpMultiplier * contributionScale,
            finalIiqMultiplier * contributionScale,
            finalIirMultiplier * contributionScale,
            finalStoneMultiplier * contributionScale,
            finalMapMultiplier * contributionScale,
            performanceScore,
            contributionPercent
        );
    }

    /**
     * Calculates a performance score for a player.
     *
     * @param tracker The completion tracker
     * @param playerId The player UUID
     * @param totalKills Total kills in the realm
     * @return Performance score (0-100)
     */
    private int calculatePerformanceScore(
            @Nonnull RealmCompletionTracker tracker,
            @Nonnull UUID playerId,
            int totalKills) {

        int playerKills = tracker.getPlayerKills(playerId);
        int playerDamage = tracker.getPlayerDamage(playerId);

        // Base score from kills (40 points max)
        double killScore = totalKills > 0
            ? Math.min(40, (playerKills * 40.0 / totalKills) * 2)
            : 20;

        // Bonus for damage dealt (30 points max based on arbitrary threshold)
        double damageScore = Math.min(30, playerDamage / 1000.0);

        // Participation bonus (30 points for being present)
        double participationScore = 30;

        return (int) Math.min(100, killScore + damageScore + participationScore);
    }

    /**
     * Calculates contribution scaling factor.
     *
     * <p>In multiplayer, each player's rewards are scaled based on their
     * contribution while ensuring a minimum reward for participating.
     *
     * @param contributionPercent Player's contribution percentage
     * @param playerCount Number of players in the realm
     * @return Scaling factor (0.5 to 1.0 typically)
     */
    private double calculateContributionScale(double contributionPercent, int playerCount) {
        if (playerCount <= 1) {
            return 1.0; // Solo player gets full rewards
        }

        // Expected contribution if evenly split
        double expectedContrib = 100.0 / playerCount;

        // Scale based on contribution vs expected
        // Players who do more get bonus, players who do less get reduced
        double ratio = contributionPercent / expectedContrib;

        // Clamp to reasonable range (0.5 to 1.5)
        return Math.max(0.5, Math.min(1.5, ratio));
    }
}
