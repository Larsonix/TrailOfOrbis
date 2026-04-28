package io.github.larsonix.trailoforbis.maps.reward;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.leveling.xp.XpSource;
import io.github.larsonix.trailoforbis.maps.config.RealmsConfig;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance.CompletionReason;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Service for distributing rewards to players after realm completion.
 *
 * <p>The reward service handles:
 * <ul>
 *   <li>Calculating rewards for each participant</li>
 *   <li>Applying XP bonuses via the leveling system</li>
 *   <li>Setting loot multipliers for item drops</li>
 *   <li>Sending completion summary messages</li>
 *   <li>Recording completion statistics</li>
 * </ul>
 *
 * <p>Rewards are distributed asynchronously to avoid blocking the main thread.
 *
 * @see RealmRewardCalculator
 * @see RealmRewardResult
 */
public class RealmRewardService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final TrailOfOrbis plugin;
    private final RealmRewardCalculator calculator;
    private final RealmsConfig config;
    private final VictoryRewardGenerator victoryRewardGenerator;
    private final VictoryRewardDistributor victoryRewardDistributor;
    @Nullable
    private final RewardChestManager rewardChestManager;

    /**
     * Creates a new reward service.
     *
     * @param plugin The TrailOfOrbis plugin instance
     * @param config The realms configuration
     * @param victoryRewardGenerator The generator for victory rewards (may be null)
     * @param victoryRewardDistributor The distributor for victory rewards (may be null)
     * @param rewardChestManager The reward chest manager for physical chest delivery (may be null)
     */
    public RealmRewardService(
            @Nonnull TrailOfOrbis plugin,
            @Nonnull RealmsConfig config,
            @Nullable VictoryRewardGenerator victoryRewardGenerator,
            @Nullable VictoryRewardDistributor victoryRewardDistributor,
            @Nullable RewardChestManager rewardChestManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.calculator = new RealmRewardCalculator(config);
        this.victoryRewardGenerator = victoryRewardGenerator;
        this.victoryRewardDistributor = victoryRewardDistributor;
        this.rewardChestManager = rewardChestManager;
    }

    /**
     * Creates a new reward service without victory item generation.
     *
     * <p>This constructor is for cases where item generation is not needed (e.g., testing).
     *
     * @param plugin The TrailOfOrbis plugin instance
     * @param config The realms configuration
     */
    public RealmRewardService(@Nonnull TrailOfOrbis plugin, @Nonnull RealmsConfig config) {
        this(plugin, config, null, null, null);
    }

    /**
     * Distributes rewards to all participants in a completed realm.
     *
     * @param realm The completed realm instance
     * @return A future that completes when all rewards have been distributed
     */
    @Nonnull
    public CompletableFuture<Map<UUID, RealmRewardResult>> distributeRewards(@Nonnull RealmInstance realm) {
        Objects.requireNonNull(realm, "realm cannot be null");

        CompletionReason reason = realm.getCompletionReason();
        if (reason == null) {
            LOGGER.atWarning().log("Cannot distribute rewards - realm has no completion reason");
            return CompletableFuture.completedFuture(Map.of());
        }

        Set<UUID> participants = realm.getAllParticipants();
        if (participants.isEmpty()) {
            LOGGER.atWarning().log("Cannot distribute rewards - no participants");
            return CompletableFuture.completedFuture(Map.of());
        }

        Map<UUID, RealmRewardResult> results = new HashMap<>();

        // Calculate rewards for each participant
        for (UUID playerId : participants) {
            RealmRewardResult result = calculator.calculate(realm, playerId);
            results.put(playerId, result);

            // Apply rewards to player
            applyRewards(playerId, result, realm);
        }

        // Log summary
        LOGGER.atInfo().log("Distributed rewards for realm %s: %d participants, reason=%s",
            realm.getRealmId().toString().substring(0, 8),
            participants.size(),
            reason);

        return CompletableFuture.completedFuture(results);
    }

    /**
     * Applies rewards to a specific player.
     *
     * <p>This method:
     * <ol>
     *   <li>Awards bonus XP based on completion</li>
     *   <li>Generates victory items (maps, gear, stones) based on size</li>
     *   <li>Distributes generated items to player inventory</li>
     * </ol>
     *
     * @param playerId The player UUID
     * @param result The calculated rewards
     * @param realm The completed realm
     */
    private void applyRewards(
            @Nonnull UUID playerId,
            @Nonnull RealmRewardResult result,
            @Nonnull RealmInstance realm) {

        if (!result.hasRewards()) {
            LOGGER.atFine().log("Player %s has no rewards to apply",
                playerId.toString().substring(0, 8));
            return;
        }

        // Award bonus XP based on completion
        awardCompletionXp(playerId, result, realm);

        // Generate and distribute victory items (maps, gear, stones)
        generateAndDistributeVictoryItems(playerId, result, realm);

        LOGGER.atFine().log("Applied rewards for player %s: %s",
            playerId.toString().substring(0, 8),
            result);
    }

    /**
     * Generates and distributes victory items to a player.
     *
     * <p>Victory items include:
     * <ul>
     *   <li>Maps - new realm maps for the next adventure</li>
     *   <li>Gear - equipment based on completed level</li>
     *   <li>Stones - modifier stones based on rarity rolls</li>
     * </ul>
     *
     * <p>The quantity of items is determined by the realm size tier:
     * <ul>
     *   <li>SMALL: 2 random items</li>
     *   <li>MEDIUM: 3 random items</li>
     *   <li>LARGE: 3 random items + 25% IIR bonus</li>
     *   <li>MASSIVE: 3 random items + 50% IIR + 10% IIQ bonus</li>
     * </ul>
     *
     * <p>Each item is randomly chosen from: map, stone, or gear.
     * IIQ (Increased Item Quantity) provides bonus roll chances for extra items.
     *
     * @param playerId The player UUID
     * @param result The calculated reward multipliers
     * @param realm The completed realm
     */
    private void generateAndDistributeVictoryItems(
            @Nonnull UUID playerId,
            @Nonnull RealmRewardResult result,
            @Nonnull RealmInstance realm) {

        // Check if victory reward system is configured
        // Generator is required; distributor is optional when chest manager is available
        if (victoryRewardGenerator == null || (victoryRewardDistributor == null && rewardChestManager == null)) {
            LOGGER.atFine().log("Victory reward system not configured - skipping item generation for %s",
                playerId.toString().substring(0, 8));
            return;
        }

        // Get player reference for inventory access
        PlayerRef playerRef = Universe.get().getPlayer(playerId);
        if (playerRef == null) {
            LOGGER.atWarning().log("Cannot distribute victory rewards - player %s not online",
                playerId.toString().substring(0, 8));
            return;
        }

        // Generate victory rewards
        VictoryRewardGenerator.VictoryRewards rewards = victoryRewardGenerator.generate(
            realm, playerId, result);

        if (!rewards.hasRewards()) {
            LOGGER.atFine().log("No victory items generated for player %s",
                playerId.toString().substring(0, 8));
            return;
        }

        // Store rewards for the physical chest if the chest manager is available
        if (rewardChestManager != null) {
            rewardChestManager.storeRewards(realm.getRealmId(), playerId, rewards);
            LOGGER.atInfo().log("Victory rewards for %s: stored %d items for reward chest",
                playerId.toString().substring(0, 8),
                rewards.totalCount());
            return;
        }

        // Fallback: distribute directly to player inventory
        if (victoryRewardDistributor != null) {
            VictoryRewardDistributor.DistributionResult distribution =
                victoryRewardDistributor.distribute(playerRef, rewards);

            LOGGER.atInfo().log("Victory rewards for %s: generated %d items, delivered %d, overflow %d",
                playerId.toString().substring(0, 8),
                rewards.totalCount(),
                distribution.delivered(),
                distribution.overflow());
        }
    }

    /**
     * Awards bonus XP for completing the realm.
     *
     * @param playerId The player UUID
     * @param result The reward result
     * @param realm The completed realm
     */
    private void awardCompletionXp(
            @Nonnull UUID playerId,
            @Nonnull RealmRewardResult result,
            @Nonnull RealmInstance realm) {

        // Calculate base completion XP from realm level and size
        int baseXp = calculateBaseCompletionXp(realm);

        // Apply multiplier
        int finalXp = (int) (baseXp * result.xpMultiplier());

        if (finalXp <= 0) {
            return;
        }

        // Award XP through leveling manager
        if (plugin.getLevelingManager() != null) {
            plugin.getLevelingManager().addXp(playerId, finalXp, XpSource.REALM_COMPLETION);
        }

        LOGGER.atFine().log("Awarded %d XP to player %s for realm completion",
            finalXp, playerId.toString().substring(0, 8));
    }

    /**
     * Calculates base completion XP for a realm.
     *
     * @param realm The completed realm
     * @return Base XP amount
     */
    private int calculateBaseCompletionXp(@Nonnull RealmInstance realm) {
        int level = realm.getMapData().level();
        int sizeBonus = (int) (realm.getMapData().size().getMonsterMultiplier() * 100);

        // Base XP scales with level and size
        // Level 1 small = ~100 XP
        // Level 100 large = ~15000 XP
        return (50 + level * 5) * sizeBonus / 100;
    }

    /**
     * Gets the reward calculator.
     *
     * @return The reward calculator
     */
    @Nonnull
    public RealmRewardCalculator getCalculator() {
        return calculator;
    }

    /**
     * Gets the victory reward generator.
     *
     * @return The generator, or null if not configured
     */
    @Nullable
    public VictoryRewardGenerator getVictoryRewardGenerator() {
        return victoryRewardGenerator;
    }

    /**
     * Gets the victory reward distributor.
     *
     * @return The distributor, or null if not configured
     */
    @Nullable
    public VictoryRewardDistributor getVictoryRewardDistributor() {
        return victoryRewardDistributor;
    }
}
