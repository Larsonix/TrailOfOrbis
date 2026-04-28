package io.github.larsonix.trailoforbis.gear.stats;

import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig.StatType;
import io.github.larsonix.trailoforbis.gear.stats.GearStatCalculator.GearBonuses;

import com.hypixel.hytale.logger.HytaleLogger;

import java.util.Map;
import java.util.Objects;

/**
 * Applies gear stat bonuses to ComputedStats.
 *
 * <p>Application order:
 * <ol>
 *   <li>Flat bonuses (additive)</li>
 *   <li>Percent bonuses (multiplicative)</li>
 * </ol>
 *
 * <p>This ensures percent bonuses scale off base + flat, not just base.
 */
public final class GearStatApplier {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /**
     * Applies gear bonuses to ComputedStats.
     *
     * @param stats The stats to modify (will be mutated)
     * @param bonuses The gear bonuses to apply
     */
    public void apply(ComputedStats stats, GearBonuses bonuses) {
        Objects.requireNonNull(stats, "stats cannot be null");
        Objects.requireNonNull(bonuses, "bonuses cannot be null");

        // Always apply weapon base damage (even if 0.0 for unarmed/unequippable)
        // This replaces vanilla weapon damage completely
        stats.setWeaponBaseDamage(bonuses.weaponBaseDamage());

        // Store weapon item ID for attack effectiveness lookup
        stats.setWeaponItemId(bonuses.weaponItemId());

        // Store RPG gear flag - critical for damage path selection
        // When true, damage system uses RPG path even if weaponBaseDamage is 0
        stats.setHoldingRpgGear(bonuses.isHoldingRpgGear());

        if (bonuses.isEmpty()) {
            LOGGER.atFine().log("Applied weapon base damage: %.1f, itemId: %s, isRpgGear: %s (no modifier bonuses)",
                    bonuses.weaponBaseDamage(), bonuses.weaponItemId(), bonuses.isHoldingRpgGear());
            return;
        }

        // 1. Apply flat bonuses first
        applyFlatBonuses(stats, bonuses.flatBonuses());

        // 2. Then apply percent bonuses
        applyPercentBonuses(stats, bonuses.percentBonuses());

        LOGGER.atFine().log("Applied gear bonuses: %d flat, %d percent, weapon base: %.1f, itemId: %s, isRpgGear: %s",
                bonuses.flatBonuses().size(), bonuses.percentBonuses().size(),
                bonuses.weaponBaseDamage(), bonuses.weaponItemId(), bonuses.isHoldingRpgGear());
    }

    /**
     * Applies flat bonuses.
     */
    private void applyFlatBonuses(ComputedStats stats, Map<String, Double> flatBonuses) {
        for (Map.Entry<String, Double> entry : flatBonuses.entrySet()) {
            String statId = entry.getKey();
            double value = entry.getValue();

            StatMapping.apply(stats, statId, value, StatType.FLAT);
        }
    }

    /**
     * Applies percent bonuses.
     */
    private void applyPercentBonuses(ComputedStats stats, Map<String, Double> percentBonuses) {
        for (Map.Entry<String, Double> entry : percentBonuses.entrySet()) {
            String statId = entry.getKey();
            double value = entry.getValue();

            StatMapping.apply(stats, statId, value, StatType.PERCENT);
        }
    }

    /**
     * Creates a summary of applied bonuses for logging/debugging.
     */
    public String createSummary(GearBonuses bonuses) {
        if (bonuses.isEmpty()) {
            return "No gear bonuses";
        }

        StringBuilder sb = new StringBuilder("Gear Bonuses:\n");

        if (!bonuses.flatBonuses().isEmpty()) {
            sb.append("  Flat:\n");
            for (Map.Entry<String, Double> entry : bonuses.flatBonuses().entrySet()) {
                sb.append("    ").append(entry.getKey()).append(": +")
                  .append(String.format("%.1f", entry.getValue())).append("\n");
            }
        }

        if (!bonuses.percentBonuses().isEmpty()) {
            sb.append("  Percent:\n");
            for (Map.Entry<String, Double> entry : bonuses.percentBonuses().entrySet()) {
                sb.append("    ").append(entry.getKey()).append(": +")
                  .append(String.format("%.1f%%", entry.getValue())).append("\n");
            }
        }

        return sb.toString();
    }
}
