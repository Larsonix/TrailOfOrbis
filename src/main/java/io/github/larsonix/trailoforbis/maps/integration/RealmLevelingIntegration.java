package io.github.larsonix.trailoforbis.maps.integration;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.leveling.xp.XpSource;
import io.github.larsonix.trailoforbis.maps.RealmsManager;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifier;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifierType;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Integrates realm modifiers with the leveling/XP system.
 *
 * <p>This class provides XP multipliers from realm modifiers when players
 * gain XP inside realms.
 *
 * <h2>Integration Points</h2>
 * <ul>
 *   <li>{@link #getXpMultiplier(UUID, XpSource)} - Called by XpGainSystem before applying XP</li>
 *   <li>{@link #isRealmXpSource(XpSource)} - Checks if XP source is realm-related</li>
 * </ul>
 *
 * <h2>Multiplier Sources</h2>
 * <ul>
 *   <li>{@link RealmModifierType#EXPERIENCE_BONUS} - Direct XP bonus modifier</li>
 *   <li>Difficulty bonus - Higher difficulty realms give more XP</li>
 * </ul>
 *
 * <h2>Usage in XpGainSystem</h2>
 * <pre>{@code
 * // Before awarding XP:
 * double multiplier = realmLevelingIntegration.getXpMultiplier(playerId, source);
 * int finalXp = (int) (baseXp * multiplier);
 * levelingManager.addXp(playerId, finalXp, source);
 * }</pre>
 *
 * @see io.github.larsonix.trailoforbis.leveling.systems.XpGainSystem
 * @see XpSource
 */
public class RealmLevelingIntegration {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final RealmsManager realmsManager;

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a new realm leveling integration.
     *
     * @param realmsManager The realms manager for checking active realms
     */
    public RealmLevelingIntegration(@Nonnull RealmsManager realmsManager) {
        this.realmsManager = Objects.requireNonNull(realmsManager, "realmsManager cannot be null");
    }

    // ═══════════════════════════════════════════════════════════════════
    // CONFIG ACCESS
    // ═══════════════════════════════════════════════════════════════════

    private double getDifficultyXpScaling() {
        return realmsManager.getConfig().getDifficultyXpScaling();
    }

    private double getMinMultiplier() {
        return realmsManager.getConfig().getXpMultiplierMin();
    }

    private double getMaxMultiplier() {
        return realmsManager.getConfig().getXpMultiplierMax();
    }

    // ═══════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the XP multiplier for a player based on realm modifiers.
     *
     * <p>Returns 1.0 (no bonus) if:
     * <ul>
     *   <li>Player is not in a realm</li>
     *   <li>XP source is not eligible for realm bonuses</li>
     * </ul>
     *
     * @param playerId The player UUID
     * @param source The source of XP gain
     * @return XP multiplier (1.0 = no bonus, 1.5 = +50%, etc.)
     */
    public double getXpMultiplier(@Nonnull UUID playerId, @Nonnull XpSource source) {
        Objects.requireNonNull(playerId, "playerId cannot be null");
        Objects.requireNonNull(source, "source cannot be null");

        // Only apply to eligible sources
        if (!isEligibleForRealmBonus(source)) {
            return 1.0;
        }

        // Check if player is in a realm
        Optional<RealmInstance> realmOpt = realmsManager.getPlayerRealm(playerId);
        if (realmOpt.isEmpty()) {
            return 1.0;
        }

        RealmInstance realm = realmOpt.get();
        return calculateMultiplier(realm);
    }

    /**
     * Gets the XP multiplier for a player without checking XP source.
     *
     * <p>Use this when you've already verified the source is eligible.
     *
     * @param playerId The player UUID
     * @return XP multiplier, or 1.0 if not in realm
     */
    public double getXpMultiplier(@Nonnull UUID playerId) {
        Optional<RealmInstance> realmOpt = realmsManager.getPlayerRealm(playerId);
        if (realmOpt.isEmpty()) {
            return 1.0;
        }
        return calculateMultiplier(realmOpt.get());
    }

    /**
     * Checks if an XP source is realm-related.
     *
     * @param source The XP source
     * @return true if source is REALM_KILL or REALM_COMPLETION
     */
    public boolean isRealmXpSource(@Nonnull XpSource source) {
        return source == XpSource.REALM_KILL || source == XpSource.REALM_COMPLETION;
    }

    /**
     * Checks if an XP source is eligible for realm bonuses.
     *
     * <p>Eligible sources:
     * <ul>
     *   <li>{@link XpSource#MOB_KILL} - When inside a realm</li>
     *   <li>{@link XpSource#REALM_KILL} - Always</li>
     *   <li>{@link XpSource#REALM_COMPLETION} - Already includes bonus</li>
     * </ul>
     *
     * @param source The XP source
     * @return true if source is eligible for realm XP bonuses
     */
    public boolean isEligibleForRealmBonus(@Nonnull XpSource source) {
        return switch (source) {
            case MOB_KILL, REALM_KILL -> true;
            // REALM_COMPLETION already has bonus baked in via RealmRewardCalculator
            case REALM_COMPLETION -> false;
            // Other sources don't get realm bonuses
            default -> false;
        };
    }

    /**
     * Checks if a player is currently in a realm.
     *
     * @param playerId The player UUID
     * @return true if player is in an active realm
     */
    public boolean isInRealm(@Nonnull UUID playerId) {
        return realmsManager.getPlayerRealm(playerId).isPresent();
    }

    /**
     * Gets the experience bonus percentage for the player's current realm.
     *
     * @param playerId The player UUID
     * @return Experience bonus percentage (e.g., 25 = +25%), or 0 if not in realm
     */
    public int getExperienceBonusPercent(@Nonnull UUID playerId) {
        Optional<RealmInstance> realmOpt = realmsManager.getPlayerRealm(playerId);
        if (realmOpt.isEmpty()) {
            return 0;
        }

        RealmInstance realm = realmOpt.get();
        return getModifierExperienceBonus(realm);
    }

    // ═══════════════════════════════════════════════════════════════════
    // INTERNAL CALCULATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Calculates the XP multiplier from realm modifiers and difficulty.
     */
    private double calculateMultiplier(@Nonnull RealmInstance realm) {
        double multiplier = 1.0;

        // Add XP bonus from modifiers
        int xpBonusPercent = getModifierExperienceBonus(realm);
        multiplier += xpBonusPercent / 100.0;

        // Add difficulty bonus
        int difficulty = realm.getMapData().getDifficultyRating();
        double difficultyBonus = difficulty * getDifficultyXpScaling();
        multiplier += difficultyBonus;

        // Apply configurable clamping (0 = no limit)
        double min = getMinMultiplier();
        double max = getMaxMultiplier();
        if (min > 0) {
            multiplier = Math.max(min, multiplier);
        }
        if (max > 0) {
            multiplier = Math.min(max, multiplier);
        }

        LOGGER.atFine().log("Realm XP multiplier: xpBonus=%d%%, difficulty=%d (%.1f%%), final=%.2fx",
            xpBonusPercent, difficulty, difficultyBonus * 100, multiplier);

        return multiplier;
    }

    /**
     * Gets the experience bonus from EXPERIENCE_BONUS modifiers.
     */
    private int getModifierExperienceBonus(@Nonnull RealmInstance realm) {
        return realm.getMapData().modifiers().stream()
            .filter(m -> m.type() == RealmModifierType.EXPERIENCE_BONUS)
            .mapToInt(RealmModifier::value)
            .sum();
    }

    // ═══════════════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets debug information for a player's XP bonuses.
     *
     * @param playerId The player UUID
     * @return Debug string
     */
    @Nonnull
    public String getDebugInfo(@Nonnull UUID playerId) {
        Optional<RealmInstance> realmOpt = realmsManager.getPlayerRealm(playerId);
        if (realmOpt.isEmpty()) {
            return String.format("XpBonuses[player=%s, inRealm=false, multiplier=1.0x]",
                playerId.toString().substring(0, 8));
        }

        RealmInstance realm = realmOpt.get();
        int xpBonus = getModifierExperienceBonus(realm);
        int difficulty = realm.getMapData().getDifficultyRating();
        double multiplier = calculateMultiplier(realm);

        return String.format(
            "XpBonuses[player=%s, realm=%s, xpBonus=%d%%, difficulty=%d, multiplier=%.2fx]",
            playerId.toString().substring(0, 8),
            realm.getRealmId().toString().substring(0, 8),
            xpBonus, difficulty, multiplier
        );
    }
}
