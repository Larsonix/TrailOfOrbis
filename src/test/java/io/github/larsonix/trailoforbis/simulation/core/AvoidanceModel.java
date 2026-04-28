package io.github.larsonix.trailoforbis.simulation.core;

import io.github.larsonix.trailoforbis.attributes.ComputedStats;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Replicates the exact avoidance checks from AvoidanceProcessor.
 *
 * <p>Order of checks (matches AvoidanceProcessor lines 222-276):
 * <ol>
 *   <li>Flat dodge chance: {@code random(0,100) < dodgeChance}</li>
 *   <li>Evasion vs accuracy: {@code random(0,1) > hitChance}</li>
 *   <li>Passive block: {@code random(0,100) < passiveBlockChance} → 100% damage negation</li>
 * </ol>
 *
 * <p>Active block (shield) is skipped — requires stamina state from Hytale ECS.
 * Parry is skipped — not implemented in AvoidanceProcessor (config exists but unused).
 *
 * <p>Evasion formula (from AvoidanceProcessor.calculateHitChance, lines 352-401):
 * <pre>
 *   scaledEvasion = (evasion × evasionScalingFactor) ^ evasionExponent
 *   hitChance = clamp((hitChanceConstant × accuracy) / (accuracy + scaledEvasion), min, max)
 * </pre>
 *
 * <p>Config values verified from config.yml → combat → evasion section.
 */
public final class AvoidanceModel {

    // Evasion config (from config.yml, verified)
    private final float minHitChance;
    private final float maxHitChance;
    private final float evasionScalingFactor;
    private final float evasionExponent;
    private final float hitChanceConstant;

    /**
     * Creates with default config values (from deployed config.yml).
     */
    public AvoidanceModel() {
        this(0.05f, 1.0f, 0.2f, 0.9f, 1.25f);
    }

    /**
     * Creates with custom evasion config values.
     */
    public AvoidanceModel(float minHitChance, float maxHitChance,
                           float evasionScalingFactor, float evasionExponent,
                           float hitChanceConstant) {
        this.minHitChance = minHitChance;
        this.maxHitChance = maxHitChance;
        this.evasionScalingFactor = evasionScalingFactor;
        this.evasionExponent = evasionExponent;
        this.hitChanceConstant = hitChanceConstant;
    }

    /**
     * Result of an avoidance check.
     */
    public enum AvoidanceResult {
        /** Attack connects — proceed to damage calculation. */
        HIT,
        /** Dodged via flat dodge chance. */
        DODGED,
        /** Evaded via evasion-vs-accuracy formula. */
        EVADED,
        /** Passively blocked — 100% damage negation. */
        BLOCKED
    }

    /**
     * Runs the full avoidance chain for one attack.
     * Matches AvoidanceProcessor.checkAvoidanceDetailed() phase order exactly.
     *
     * @param defenderStats The defender's ComputedStats
     * @param attackerStats The attacker's ComputedStats (for accuracy)
     * @return Whether the attack was avoided and how
     */
    public AvoidanceResult check(ComputedStats defenderStats, ComputedStats attackerStats) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        // Check 1: Flat dodge chance (AvoidanceProcessor line 285-295)
        float dodgeChance = defenderStats.getDodgeChance();
        if (dodgeChance > 0 && rng.nextFloat() * 100f < dodgeChance) {
            return AvoidanceResult.DODGED;
        }

        // Check 2: Evasion vs accuracy (AvoidanceProcessor line 304-311)
        float evasion = defenderStats.getEvasion();
        float accuracy = attackerStats.getAccuracy();
        if (evasion > 0) {
            float chanceToHit = calculateHitChance(accuracy, evasion);
            if (rng.nextFloat() > chanceToHit) {
                return AvoidanceResult.EVADED;
            }
        }

        // Check 3: Passive block (AvoidanceProcessor line 320-330)
        // Passive block = 100% damage negation (not reduction)
        float passiveBlockChance = defenderStats.getPassiveBlockChance();
        if (passiveBlockChance > 0 && rng.nextFloat() * 100f < passiveBlockChance) {
            return AvoidanceResult.BLOCKED;
        }

        // All checks passed — attack connects
        return AvoidanceResult.HIT;
    }

    /**
     * PoE-style hit chance formula.
     * Matches AvoidanceProcessor.calculateHitChance() lines 352-401 exactly.
     *
     * @param accuracy Attacker's accuracy rating
     * @param evasion  Defender's evasion rating
     * @return Probability the attack hits (0.05 to 1.0)
     */
    public float calculateHitChance(float accuracy, float evasion) {
        if (accuracy <= 0) return minHitChance;
        if (evasion <= 0) return maxHitChance;

        double scaledEvasion = Math.pow(evasion * evasionScalingFactor, evasionExponent);
        double uncapped = (hitChanceConstant * accuracy) / (accuracy + scaledEvasion);

        return (float) Math.min(maxHitChance, Math.max(minHitChance, uncapped));
    }
}
