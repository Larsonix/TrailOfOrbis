package io.github.larsonix.trailoforbis.maps.integration;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.maps.RealmsManager;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifier;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifierType;
import io.github.larsonix.trailoforbis.maps.reward.RealmRewardResult;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Integrates realm modifiers with the gear loot system.
 *
 * <p>This class provides IIQ (Increased Item Quantity) and IIR (Increased Item Rarity)
 * bonuses to the loot system when players are inside realms or have recently completed
 * a realm.
 *
 * <h2>Integration Points</h2>
 * <ul>
 *   <li>{@link #getItemQuantityBonus(UUID)} - Called by LootCalculator for drop chance</li>
 *   <li>{@link #getItemRarityBonus(UUID)} - Called by LootCalculator for rarity bonus</li>
 *   <li>{@link #storeCompletionBonuses(UUID, RealmRewardResult)} - Called after realm completion</li>
 * </ul>
 *
 * <h2>Bonus Sources</h2>
 * <ol>
 *   <li><b>Active Realm:</b> If player is inside a realm, use that realm's modifiers</li>
 *   <li><b>Completion Bonus:</b> Temporary bonus stored after realm completion (decays)</li>
 * </ol>
 *
 * <h2>Usage in LootCalculator</h2>
 * <pre>{@code
 * // In LootCalculator.calculateRarityBonus():
 * double realmRarityBonus = realmLootIntegration.getItemRarityBonus(playerId);
 * totalBonus += realmRarityBonus;
 *
 * // In LootCalculator.shouldDropGear():
 * double realmQuantityBonus = realmLootIntegration.getItemQuantityBonus(playerId);
 * finalChance *= (1.0 + realmQuantityBonus / 100.0);
 * }</pre>
 *
 * @see io.github.larsonix.trailoforbis.gear.loot.LootCalculator
 * @see RealmRewardResult
 */
public class RealmLootIntegration {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /**
     * Duration in milliseconds for completion bonuses to persist.
     * After this time, the bonus decays to zero.
     */
    private static final long COMPLETION_BONUS_DURATION_MS = TimeUnit.MINUTES.toMillis(5);

    private final RealmsManager realmsManager;

    /**
     * Temporary bonuses from realm completion.
     * Key: Player UUID
     * Value: Stored bonus data with expiration
     */
    private final Map<UUID, StoredBonus> completionBonuses = new ConcurrentHashMap<>();

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a new realm loot integration.
     *
     * @param realmsManager The realms manager for checking active realms
     */
    public RealmLootIntegration(@Nonnull RealmsManager realmsManager) {
        this.realmsManager = Objects.requireNonNull(realmsManager, "realmsManager cannot be null");
    }

    // ═══════════════════════════════════════════════════════════════════
    // PUBLIC API - Called by LootCalculator
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the Item Quantity bonus for a player.
     *
     * <p>This affects drop chance - higher IIQ = more items drop.
     *
     * @param playerId The player UUID
     * @return IIQ bonus percentage (e.g., 25.0 = +25% quantity)
     */
    public double getItemQuantityBonus(@Nonnull UUID playerId) {
        Objects.requireNonNull(playerId, "playerId cannot be null");

        // Check active realm first
        double activeBonus = getActiveRealmQuantityBonus(playerId);
        if (activeBonus > 0) {
            return activeBonus;
        }

        // Check completion bonus
        return getCompletionQuantityBonus(playerId);
    }

    /**
     * Gets the Item Rarity bonus for a player.
     *
     * <p>This affects rarity rolls - higher IIR = better chances for rare items.
     *
     * @param playerId The player UUID
     * @return IIR bonus percentage (e.g., 30.0 = +30% rarity)
     */
    public double getItemRarityBonus(@Nonnull UUID playerId) {
        Objects.requireNonNull(playerId, "playerId cannot be null");

        // Check active realm first
        double activeBonus = getActiveRealmRarityBonus(playerId);
        if (activeBonus > 0) {
            return activeBonus;
        }

        // Check completion bonus
        return getCompletionRarityBonus(playerId);
    }

    /**
     * Checks if a player has any loot bonuses active.
     *
     * @param playerId The player UUID
     * @return true if player has IIQ or IIR bonuses
     */
    public boolean hasLootBonuses(@Nonnull UUID playerId) {
        return getItemQuantityBonus(playerId) > 0 || getItemRarityBonus(playerId) > 0;
    }

    // ═══════════════════════════════════════════════════════════════════
    // COMPLETION BONUS STORAGE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Stores completion bonuses for a player.
     *
     * <p>Called by {@link io.github.larsonix.trailoforbis.maps.reward.RealmRewardService}
     * after distributing rewards.
     *
     * @param playerId The player UUID
     * @param result The reward result containing multipliers
     */
    public void storeCompletionBonuses(@Nonnull UUID playerId, @Nonnull RealmRewardResult result) {
        Objects.requireNonNull(playerId, "playerId cannot be null");
        Objects.requireNonNull(result, "result cannot be null");

        if (!result.hasRewards()) {
            return;
        }

        // Convert multipliers to percentages
        // Multiplier of 1.25 = 25% bonus
        double iiqPercent = (result.itemQuantityMultiplier() - 1.0) * 100.0;
        double iirPercent = (result.itemRarityMultiplier() - 1.0) * 100.0;

        if (iiqPercent <= 0 && iirPercent <= 0) {
            return;
        }

        StoredBonus bonus = new StoredBonus(
            iiqPercent,
            iirPercent,
            System.currentTimeMillis() + COMPLETION_BONUS_DURATION_MS
        );

        completionBonuses.put(playerId, bonus);

        LOGGER.atFine().log("Stored completion bonuses for player %s: IIQ=%.1f%%, IIR=%.1f%%, expires in %d min",
            playerId.toString().substring(0, 8),
            iiqPercent,
            iirPercent,
            TimeUnit.MILLISECONDS.toMinutes(COMPLETION_BONUS_DURATION_MS));
    }

    /**
     * Clears completion bonuses for a player.
     *
     * @param playerId The player UUID
     */
    public void clearCompletionBonuses(@Nonnull UUID playerId) {
        completionBonuses.remove(playerId);
    }

    /**
     * Cleans up expired completion bonuses.
     *
     * <p>Should be called periodically (e.g., every minute) to prevent memory leaks.
     */
    public void cleanupExpiredBonuses() {
        long now = System.currentTimeMillis();
        completionBonuses.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    // ═══════════════════════════════════════════════════════════════════
    // INTERNAL - Active Realm Bonuses
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets IIQ bonus from player's active realm (if any).
     */
    private double getActiveRealmQuantityBonus(@Nonnull UUID playerId) {
        Optional<RealmInstance> realmOpt = realmsManager.getPlayerRealm(playerId);
        if (realmOpt.isEmpty()) {
            return 0;
        }

        RealmInstance realm = realmOpt.get();
        return realm.getMapData().modifiers().stream()
            .filter(m -> m.type() == RealmModifierType.ITEM_QUANTITY)
            .mapToDouble(RealmModifier::value)
            .sum();
    }

    /**
     * Gets IIR bonus from player's active realm (if any).
     */
    private double getActiveRealmRarityBonus(@Nonnull UUID playerId) {
        Optional<RealmInstance> realmOpt = realmsManager.getPlayerRealm(playerId);
        if (realmOpt.isEmpty()) {
            return 0;
        }

        RealmInstance realm = realmOpt.get();
        return realm.getMapData().modifiers().stream()
            .filter(m -> m.type() == RealmModifierType.ITEM_RARITY)
            .mapToDouble(RealmModifier::value)
            .sum();
    }

    // ═══════════════════════════════════════════════════════════════════
    // INTERNAL - Completion Bonuses
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets IIQ bonus from completion storage.
     */
    private double getCompletionQuantityBonus(@Nonnull UUID playerId) {
        StoredBonus bonus = completionBonuses.get(playerId);
        if (bonus == null || bonus.isExpired()) {
            completionBonuses.remove(playerId);
            return 0;
        }
        return bonus.iiqPercent();
    }

    /**
     * Gets IIR bonus from completion storage.
     */
    private double getCompletionRarityBonus(@Nonnull UUID playerId) {
        StoredBonus bonus = completionBonuses.get(playerId);
        if (bonus == null || bonus.isExpired()) {
            completionBonuses.remove(playerId);
            return 0;
        }
        return bonus.iirPercent();
    }

    // ═══════════════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets debug information for a player's loot bonuses.
     *
     * @param playerId The player UUID
     * @return Debug string
     */
    @Nonnull
    public String getDebugInfo(@Nonnull UUID playerId) {
        double iiq = getItemQuantityBonus(playerId);
        double iir = getItemRarityBonus(playerId);
        boolean inRealm = realmsManager.getPlayerRealm(playerId).isPresent();
        StoredBonus stored = completionBonuses.get(playerId);

        return String.format(
            "LootBonuses[player=%s, IIQ=%.1f%%, IIR=%.1f%%, inRealm=%b, hasStored=%b]",
            playerId.toString().substring(0, 8),
            iiq, iir, inRealm, stored != null && !stored.isExpired()
        );
    }

    // ═══════════════════════════════════════════════════════════════════
    // NESTED CLASSES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Stored completion bonus with expiration.
     */
    private record StoredBonus(
        double iiqPercent,
        double iirPercent,
        long expiresAtMs
    ) {
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAtMs;
        }

        boolean isExpired(long currentTimeMs) {
            return currentTimeMs > expiresAtMs;
        }
    }
}
