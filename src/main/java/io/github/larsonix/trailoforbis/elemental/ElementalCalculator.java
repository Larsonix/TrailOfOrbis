package io.github.larsonix.trailoforbis.elemental;

import javax.annotation.Nonnull;

/**
 * Calculates elemental damage using PoE-style formulas.
 *
 * <h2>Damage Formula</h2>
 * <pre>
 * Final = (Base + Flat) × (1 + Percent/100) × (1 + Multiplier/100)
 * </pre>
 *
 * <h2>Resistance Formula (with Penetration)</h2>
 * <pre>
 * EffectiveResist = max(0, min(Resistance, 75) - Penetration)
 * DamageAfterResist = Damage × (1 - EffectiveResist / 100)
 * </pre>
 *
 * <p>Penetration reduces the target's effective resistance. Unlike PoE1 where
 * penetration can push resistance negative, we use PoE2's approach where
 * penetration can only reduce resistance down to 0%.
 *
 * <h2>Example</h2>
 * <ul>
 *   <li>Base fire damage: 50</li>
 *   <li>Flat fire bonus: +20</li>
 *   <li>Fire percent: +50%</li>
 *   <li>Fire multiplier: +30%</li>
 *   <li>Target fire resistance: 60%</li>
 *   <li>Attacker fire penetration: 20%</li>
 * </ul>
 * <pre>
 * Damage = (50 + 20) × 1.5 × 1.3 = 136.5
 * EffectiveResist = max(0, 60 - 20) = 40%
 * After Resist = 136.5 × (1 - 0.4) = 81.9
 * </pre>
 */
public final class ElementalCalculator {

    /**
     * Maximum resistance cap (75%).
     * Prevents immunity while still rewarding resistance investment.
     */
    public static final double RESISTANCE_CAP = 75.0;

    /**
     * Minimum resistance (allows up to -100% for 2x damage taken).
     */
    public static final double RESISTANCE_FLOOR = -100.0;

    // Private constructor - utility class
    private ElementalCalculator() {
    }

    /**
     * Calculates elemental damage using PoE-style formula.
     *
     * <p>Formula: {@code (Base + Flat) × (1 + Percent/100) × (1 + Multiplier/100)}
     *
     * @param baseDamage Base damage before any modifiers (can be 0)
     * @param stats      Elemental stats of the attacker
     * @param element    The element type to calculate
     * @return Final calculated damage (minimum 0)
     */
    public static double calculateDamage(
            double baseDamage,
            @Nonnull ElementalStats stats,
            @Nonnull ElementType element) {

        double flat = stats.getFlatDamage(element);
        double percent = stats.getPercentDamage(element);
        double multiplier = stats.getMultiplierDamage(element);

        // PoE formula: (Base + Flat) × (1 + Percent/100) × (1 + Multiplier/100)
        double damage = (baseDamage + flat);
        damage *= (1.0 + percent / 100.0);
        damage *= (1.0 + multiplier / 100.0);

        return Math.max(0, damage);
    }

    /**
     * Calculates elemental damage with just flat damage (no base).
     *
     * <p>Convenience method for mobs that only have flat elemental damage.
     *
     * @param stats   Elemental stats of the attacker
     * @param element The element type to calculate
     * @return Final calculated damage (minimum 0)
     */
    public static double calculateDamage(
            @Nonnull ElementalStats stats,
            @Nonnull ElementType element) {
        return calculateDamage(0, stats, element);
    }

    /**
     * Applies resistance to incoming elemental damage.
     *
     * <p>Formula: {@code damage × (1 - min(max(resistance, -100), 75) / 100)}
     *
     * <p>Resistance is capped at 75% to prevent immunity.
     * Negative resistance (up to -100%) increases damage taken.
     *
     * @param damage     Incoming elemental damage
     * @param resistance Target's resistance to this element (percentage)
     * @return Damage after resistance reduction
     */
    public static double applyResistance(double damage, double resistance) {
        return applyResistance(damage, resistance, 0);
    }

    /**
     * Applies resistance to incoming elemental damage with penetration.
     *
     * <p>Penetration reduces the target's effective resistance. Following PoE2's
     * approach, penetration can only reduce resistance down to 0% (not negative).
     *
     * <p>Formula:
     * <pre>
     * effectiveResist = max(0, min(resistance, 75) - penetration)
     * damage × (1 - effectiveResist / 100)
     * </pre>
     *
     * <p>Note: If the target has negative resistance (debuffed), penetration
     * is not applied - the target already takes increased damage.
     *
     * @param damage      Incoming elemental damage
     * @param resistance  Target's resistance to this element (percentage)
     * @param penetration Attacker's penetration for this element (percentage points)
     * @return Damage after resistance reduction
     */
    public static double applyResistance(double damage, double resistance, double penetration) {
        // First, cap resistance at 75% (but allow negative from debuffs)
        double cappedResist = Math.min(resistance, RESISTANCE_CAP);

        // Apply penetration only to positive resistance
        // Penetration reduces effective resistance but floors at 0 (PoE2 style)
        double effectiveResist;
        if (cappedResist > 0 && penetration > 0) {
            effectiveResist = Math.max(0, cappedResist - penetration);
        } else {
            // For negative resistance, don't apply penetration
            // But still clamp to the floor
            effectiveResist = Math.max(RESISTANCE_FLOOR, cappedResist);
        }

        // Apply: damage × (1 - resist/100)
        return damage * (1.0 - effectiveResist / 100.0);
    }

    /**
     * Full damage calculation: attacker stats → target resistance.
     *
     * <p>Combines damage calculation and resistance application in one call.
     * Uses attacker's penetration to reduce target's effective resistance.
     *
     * @param baseDamage    Base damage (can be 0 for pure elemental)
     * @param attackerStats Attacker's elemental stats (includes penetration)
     * @param targetStats   Target's elemental stats (for resistance)
     * @param element       The element type
     * @return Final damage after all calculations
     */
    public static double calculateFinalDamage(
            double baseDamage,
            @Nonnull ElementalStats attackerStats,
            @Nonnull ElementalStats targetStats,
            @Nonnull ElementType element) {

        double calculated = calculateDamage(baseDamage, attackerStats, element);
        double resistance = targetStats.getResistance(element);
        double penetration = attackerStats.getPenetration(element);
        return applyResistance(calculated, resistance, penetration);
    }

    /**
     * Full damage calculation with no base damage.
     *
     * <p>Convenience overload for pure elemental damage.
     *
     * @param attackerStats Attacker's elemental stats
     * @param targetStats   Target's elemental stats (for resistance)
     * @param element       The element type
     * @return Final damage after all calculations
     */
    public static double calculateFinalDamage(
            @Nonnull ElementalStats attackerStats,
            @Nonnull ElementalStats targetStats,
            @Nonnull ElementType element) {
        return calculateFinalDamage(0, attackerStats, targetStats, element);
    }

    /**
     * Calculates total elemental damage across all elements.
     *
     * @param attackerStats Attacker's elemental stats
     * @param targetStats   Target's elemental stats
     * @return Sum of all elemental damage after resistances
     */
    public static double calculateTotalElementalDamage(
            @Nonnull ElementalStats attackerStats,
            @Nonnull ElementalStats targetStats) {

        double total = 0;
        for (ElementType element : ElementType.values()) {
            // Only calculate if attacker has damage for this element
            if (attackerStats.getFlatDamage(element) > 0) {
                total += calculateFinalDamage(attackerStats, targetStats, element);
            }
        }
        return total;
    }

    /**
     * Gets the effective resistance after capping.
     *
     * @param resistance Raw resistance value
     * @return Effective resistance (clamped between -100% and 75%)
     */
    public static double getEffectiveResistance(double resistance) {
        return getEffectiveResistance(resistance, 0);
    }

    /**
     * Gets the effective resistance after capping and penetration.
     *
     * @param resistance  Raw resistance value
     * @param penetration Penetration value (reduces resistance)
     * @return Effective resistance after penetration (min 0% if penetration applied)
     */
    public static double getEffectiveResistance(double resistance, double penetration) {
        double cappedResist = Math.min(resistance, RESISTANCE_CAP);

        if (cappedResist > 0 && penetration > 0) {
            return Math.max(0, cappedResist - penetration);
        } else {
            return Math.max(RESISTANCE_FLOOR, cappedResist);
        }
    }

    /**
     * Calculates the damage multiplier from resistance.
     *
     * <p>Useful for displaying "takes X% more/less damage".
     *
     * @param resistance Resistance percentage
     * @return Damage multiplier (e.g., 0.4 for 60% resistance, 2.0 for -100% resistance)
     */
    public static double getResistanceMultiplier(double resistance) {
        double effective = getEffectiveResistance(resistance);
        return 1.0 - effective / 100.0;
    }

    /**
     * Checks if a resistance value would be capped.
     *
     * @param resistance Resistance percentage
     * @return true if resistance >= 75%
     */
    public static boolean isResistanceCapped(double resistance) {
        return resistance >= RESISTANCE_CAP;
    }

    /**
     * Formats damage breakdown for debug logging.
     *
     * @param baseDamage    Base damage
     * @param attackerStats Attacker's stats
     * @param targetStats   Target's stats
     * @param element       Element type
     * @return Formatted string with calculation details
     */
    @Nonnull
    public static String formatDamageBreakdown(
            double baseDamage,
            @Nonnull ElementalStats attackerStats,
            @Nonnull ElementalStats targetStats,
            @Nonnull ElementType element) {

        double flat = attackerStats.getFlatDamage(element);
        double percent = attackerStats.getPercentDamage(element);
        double mult = attackerStats.getMultiplierDamage(element);
        double resist = targetStats.getResistance(element);
        double penetration = attackerStats.getPenetration(element);
        double effectiveResist = getEffectiveResistance(resist, penetration);

        double rawDamage = calculateDamage(baseDamage, attackerStats, element);
        double finalDamage = applyResistance(rawDamage, resist, penetration);

        if (penetration > 0) {
            return String.format(
                "%s: (%.1f + %.1f) × %.2f × %.2f = %.1f → %.0f%% resist - %.0f%% pen = %.0f%% → %.1f",
                element.getDisplayName(),
                baseDamage, flat,
                1.0 + percent / 100.0,
                1.0 + mult / 100.0,
                rawDamage,
                resist,
                penetration,
                effectiveResist,
                finalDamage
            );
        } else {
            return String.format(
                "%s: (%.1f + %.1f) × %.2f × %.2f = %.1f → %.0f%% resist → %.1f",
                element.getDisplayName(),
                baseDamage, flat,
                1.0 + percent / 100.0,
                1.0 + mult / 100.0,
                rawDamage,
                effectiveResist,
                finalDamage
            );
        }
    }
}
