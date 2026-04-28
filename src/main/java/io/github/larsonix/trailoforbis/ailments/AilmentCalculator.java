package io.github.larsonix.trailoforbis.ailments;

import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.elemental.ElementType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Random;
import java.util.UUID;

/**
 * Pure calculation logic for ailment application and magnitude.
 *
 * <p>This class contains no Hytale dependencies - only math. It calculates:
 * <ul>
 *   <li>Application chance based on base chance + statusEffectChance</li>
 *   <li>Duration with statusEffectDuration bonus</li>
 *   <li>Magnitude (DPS, slow%, +dmg%) based on hit damage and stats</li>
 * </ul>
 *
 * <p><b>PoE2-Style Application:</b> Elemental damage has a % chance to apply
 * the corresponding ailment. Higher statusEffectChance = more reliable application.
 *
 * <p><b>Thread Safety:</b> This class is stateless except for the Random instance.
 * Use separate instances per thread or synchronize externally if sharing.
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * AilmentCalculator calc = new AilmentCalculator();
 * AilmentApplicationResult result = calc.tryApplyAilment(
 *     ElementType.FIRE, hitDamage, attackerStats, defenderMaxHealth, attackerUuid
 * );
 * if (result.applied()) {
 *     tracker.applyAilment(targetUuid, result.ailmentState());
 * }
 * }</pre>
 */
public class AilmentCalculator {

    /** Random for chance rolls */
    private final Random random;

    /** Minimum magnitude for Freeze/Shock to prevent trivial effects */
    private static final float MIN_DEBUFF_MAGNITUDE = 5.0f;

    public AilmentCalculator() {
        this.random = new Random();
    }

    /**
     * Creates a new ailment calculator with a specified random (for testing).
     */
    public AilmentCalculator(@Nonnull Random random) {
        this.random = random;
    }

    /**
     * Attempts to apply an ailment based on elemental damage.
     *
     * <p>This method:
     * <ol>
     *   <li>Maps element to ailment type</li>
     *   <li>Calculates application chance</li>
     *   <li>Rolls for application</li>
     *   <li>If successful, calculates duration and magnitude</li>
     * </ol>
     *
     * @param attackerStats     Attacker's computed stats (for statusEffectChance, etc.)
     * @param defenderMaxHealth Defender's maximum health (for magnitude scaling)
     * @param attackerUuid      UUID of the attacker (for source tracking)
     */
    @Nonnull
    public AilmentApplicationResult tryApplyAilment(
            @Nonnull ElementType element,
            float elementalDamage,
            @Nonnull ComputedStats attackerStats,
            float defenderMaxHealth,
            @Nonnull UUID attackerUuid
    ) {
        // Map element to ailment
        AilmentType ailmentType = AilmentType.forElement(element);
        if (ailmentType == null) {
            return AilmentApplicationResult.notApplied();
        }

        // Calculate application chance
        float chance = calculateApplicationChance(ailmentType, attackerStats);

        // Roll for application
        float roll = random.nextFloat() * 100f;
        if (roll >= chance) {
            return AilmentApplicationResult.notApplied(chance, roll);
        }

        // Calculate duration with bonuses
        float duration = calculateDuration(ailmentType, attackerStats);

        // Calculate magnitude based on ailment type
        float magnitude = calculateMagnitude(ailmentType, elementalDamage, attackerStats, defenderMaxHealth, duration);

        // Create the ailment state
        AilmentState state = createAilmentState(ailmentType, magnitude, duration, attackerUuid);

        return AilmentApplicationResult.applied(state, chance, roll);
    }

    /**
     * Calculates the application chance for an ailment.
     *
     * <p>Formula: {@code baseChance + statusEffectChance}
     *
     * @return Application chance percentage (uncapped)
     */
    public float calculateApplicationChance(@Nonnull AilmentType ailmentType, @Nonnull ComputedStats attackerStats) {
        float baseChance = ailmentType.getBaseChance();
        float statusEffectChance = attackerStats.getStatusEffectChance();
        return baseChance + statusEffectChance;
    }

    /**
     * Calculates the duration for an ailment with bonuses.
     *
     * <p>Formula: {@code baseDuration × (1 + statusEffectDuration / 100)}
     *
     * @return Duration in seconds
     */
    public float calculateDuration(@Nonnull AilmentType ailmentType, @Nonnull ComputedStats attackerStats) {
        float baseDuration = ailmentType.getDefaultDuration();
        float durationBonus = attackerStats.getStatusEffectDuration();
        return baseDuration * (1f + durationBonus / 100f);
    }

    /**
     * Calculates the magnitude for an ailment.
     *
     * <p>Magnitude meaning varies by type:
     * <ul>
     *   <li>BURN: DPS = (hitDamage × damageRatio + burnDamage) / duration</li>
     *   <li>FREEZE: slow% = min(30, hitDamage / maxHealth × 100), minimum 5%</li>
     *   <li>SHOCK: +dmg% = min(50, hitDamage / maxHealth × 100), minimum 5%</li>
     *   <li>POISON: DPS = (hitDamage × damageRatio + poisonDamage) / duration</li>
     * </ul>
     *
     * @param duration          Calculated duration (for DPS conversion)
     * @return Magnitude value appropriate for the ailment type
     */
    public float calculateMagnitude(
            @Nonnull AilmentType ailmentType,
            float hitDamage,
            @Nonnull ComputedStats attackerStats,
            float defenderMaxHealth,
            float duration
    ) {
        return switch (ailmentType) {
            case BURN -> calculateBurnDps(hitDamage, attackerStats.getBurnDamage(), duration, ailmentType.getBaseDamageRatio());
            case FREEZE -> calculateFreezeSlowPercent(hitDamage, defenderMaxHealth);
            case SHOCK -> calculateShockDamageIncrease(hitDamage, defenderMaxHealth);
            case POISON -> calculatePoisonDps(hitDamage, attackerStats.getPoisonDamage(), duration, ailmentType.getBaseDamageRatio());
        };
    }

    /**
     * Calculates Burn DPS.
     *
     * <p>Formula: {@code (hitDamage × damageRatio + burnDamage) / duration}
     */
    public float calculateBurnDps(float hitDamage, float burnDamage, float duration, float damageRatio) {
        if (duration <= 0) {
            return 0f;
        }
        float totalDamage = hitDamage * damageRatio + burnDamage;
        return totalDamage / duration;
    }

    /**
     * Calculates Freeze slow percentage.
     *
     * <p>Formula: {@code min(30, max(5, hitDamage / maxHealth × 100))}
     *
     * @return Slow percentage (5-30)
     */
    public float calculateFreezeSlowPercent(float hitDamage, float maxHealth) {
        if (maxHealth <= 0) {
            return MIN_DEBUFF_MAGNITUDE;
        }
        float rawPercent = (hitDamage / maxHealth) * 100f;
        return Math.min(30f, Math.max(MIN_DEBUFF_MAGNITUDE, rawPercent));
    }

    /**
     * Calculates Shock damage increase percentage.
     *
     * <p>Formula: {@code min(50, max(5, hitDamage / maxHealth × 100))}
     *
     * @return Damage increase percentage (5-50)
     */
    public float calculateShockDamageIncrease(float hitDamage, float maxHealth) {
        if (maxHealth <= 0) {
            return MIN_DEBUFF_MAGNITUDE;
        }
        float rawPercent = (hitDamage / maxHealth) * 100f;
        return Math.min(50f, Math.max(MIN_DEBUFF_MAGNITUDE, rawPercent));
    }

    /**
     * Calculates Poison DPS (per stack).
     *
     * <p>Formula: {@code (hitDamage × damageRatio + poisonDamage) / duration}
     */
    public float calculatePoisonDps(float hitDamage, float poisonDamage, float duration, float damageRatio) {
        if (duration <= 0) {
            return 0f;
        }
        float totalDamage = hitDamage * damageRatio + poisonDamage;
        return totalDamage / duration;
    }

    /** Creates an ailment state using the appropriate factory method. */
    @Nonnull
    private AilmentState createAilmentState(
            @Nonnull AilmentType type,
            float magnitude,
            float duration,
            @Nonnull UUID sourceUuid
    ) {
        return switch (type) {
            case BURN -> AilmentState.burn(magnitude, duration, sourceUuid);
            case FREEZE -> AilmentState.freeze(magnitude, duration, sourceUuid);
            case SHOCK -> AilmentState.shock(magnitude, duration, sourceUuid);
            case POISON -> AilmentState.poison(magnitude, duration, sourceUuid);
        };
    }

    /**
     * Checks if an ailment should be resisted based on defender thresholds.
     *
     * <p>Thresholds represent a damage threshold below which the ailment
     * has reduced chance to apply. If hit damage is below threshold,
     * the effective chance is reduced proportionally.
     *
     * @return Chance multiplier (0.0 to 1.0)
     */
    public float calculateThresholdMultiplier(
            @Nonnull AilmentType ailmentType,
            float hitDamage,
            @Nonnull ComputedStats defenderStats
    ) {
        float threshold = switch (ailmentType) {
            case BURN -> defenderStats.getBurnThreshold();
            case FREEZE -> defenderStats.getFreezeThreshold();
            case SHOCK -> defenderStats.getShockThreshold();
            case POISON -> 0f; // Poison has no threshold (always applies if rolled)
        };

        if (threshold <= 0) {
            return 1.0f; // No threshold, full chance
        }

        // If damage is above threshold, full chance
        if (hitDamage >= threshold) {
            return 1.0f;
        }

        // Below threshold: chance is proportional to damage/threshold
        return hitDamage / threshold;
    }

    /**
     * Advanced application attempt that considers defender thresholds.
     *
     * <p>Use this method when the defender has ailment resistance stats.
     *
     * @param defenderStats     Defender's computed stats (for thresholds)
     */
    @Nonnull
    public AilmentApplicationResult tryApplyAilmentWithThreshold(
            @Nonnull ElementType element,
            float elementalDamage,
            @Nonnull ComputedStats attackerStats,
            @Nonnull ComputedStats defenderStats,
            float defenderMaxHealth,
            @Nonnull UUID attackerUuid
    ) {
        // Map element to ailment
        AilmentType ailmentType = AilmentType.forElement(element);
        if (ailmentType == null) {
            return AilmentApplicationResult.notApplied();
        }

        // Calculate base application chance
        float baseChance = calculateApplicationChance(ailmentType, attackerStats);

        // Apply threshold reduction
        float thresholdMult = calculateThresholdMultiplier(ailmentType, elementalDamage, defenderStats);
        float effectiveChance = baseChance * thresholdMult;

        // Roll for application
        float roll = random.nextFloat() * 100f;
        if (roll >= effectiveChance) {
            return AilmentApplicationResult.notApplied(effectiveChance, roll);
        }

        // Calculate duration and magnitude
        float duration = calculateDuration(ailmentType, attackerStats);
        float magnitude = calculateMagnitude(ailmentType, elementalDamage, attackerStats, defenderMaxHealth, duration);

        // Create the ailment state
        AilmentState state = createAilmentState(ailmentType, magnitude, duration, attackerUuid);

        return AilmentApplicationResult.applied(state, effectiveChance, roll);
    }

    /**
     * Result of an ailment application attempt.
     *
     * @param ailmentState      The ailment state if applied, null otherwise
     */
    public record AilmentApplicationResult(
            boolean applied,
            @Nullable AilmentState ailmentState,
            float applicationChance,
            float roll
    ) {
        /** Creates a result indicating the ailment was not applied. */
        @Nonnull
        public static AilmentApplicationResult notApplied() {
            return new AilmentApplicationResult(false, null, 0f, 0f);
        }

        /** Creates a result indicating the ailment was not applied (with chance/roll detail). */
        @Nonnull
        public static AilmentApplicationResult notApplied(float chance, float roll) {
            return new AilmentApplicationResult(false, null, chance, roll);
        }

        /** Creates a result indicating the ailment was applied. */
        @Nonnull
        public static AilmentApplicationResult applied(@Nonnull AilmentState state, float chance, float roll) {
            return new AilmentApplicationResult(true, state, chance, roll);
        }
    }
}
