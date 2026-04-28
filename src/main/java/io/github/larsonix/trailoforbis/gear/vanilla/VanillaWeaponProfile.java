package io.github.larsonix.trailoforbis.gear.vanilla;

import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig.FamilyAttackProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Complete attack profile for a vanilla weapon.
 *
 * <p>Contains all enumerated attacks with their damage values and pre-computed
 * effectiveness multipliers. Multipliers are derived from a config-defined
 * {@link FamilyAttackProfile} so that all weapons in the same family share
 * identical attack multipliers regardless of individual vanilla damage values.
 *
 * <h2>How Multipliers Are Assigned</h2>
 *
 * <ol>
 *   <li>Attacks are classified as backstab or normal based on {@code isBackstab}</li>
 *   <li>Backstab attacks receive the family's fixed {@code backstabMultiplier}</li>
 *   <li>Normal attacks are sorted by damage ascending and mapped to the family's
 *       {@code normalMultipliers} list via linear interpolation</li>
 * </ol>
 *
 * <p>This guarantees that two daggers (e.g., Iron vs Crude) with different vanilla
 * damage spreads produce identical multipliers for the same attack type.
 *
 * @param itemId The Hytale item ID (e.g., "Weapon_Daggers_Iron")
 * @param weaponFamily The weapon family from Tags (e.g., "Daggers")
 * @param attacks All enumerated attacks for this weapon
 * @param minDamage The lowest damage value across all attacks
 * @param maxDamage The highest damage value across all attacks
 * @param referenceDamage The geometric mean √(min × max) used by fallback path
 * @param damageToEffectiveness Map of vanilla damage values to effectiveness multipliers
 */
public record VanillaWeaponProfile(
        String itemId,
        String weaponFamily,
        List<VanillaAttackInfo> attacks,
        float minDamage,
        float maxDamage,
        float referenceDamage,
        Map<Float, Float> damageToEffectiveness
) {
    /**
     * Creates a profile from a list of attacks and a family attack profile.
     *
     * <p>Separates backstab vs normal attacks, sorts normals by damage,
     * and maps them to the config-defined multiplier curve via interpolation.
     *
     * @param itemId The Hytale item ID
     * @param weaponFamily The weapon family
     * @param attacks List of all attacks
     * @param familyProfile The config-defined multiplier profile for this family
     * @return A fully computed weapon profile
     */
    public static VanillaWeaponProfile create(
            String itemId,
            String weaponFamily,
            List<VanillaAttackInfo> attacks,
            FamilyAttackProfile familyProfile
    ) {
        if (attacks.isEmpty()) {
            return new VanillaWeaponProfile(
                    itemId,
                    weaponFamily,
                    List.of(),
                    0f,
                    0f,
                    1f,  // Avoid division by zero in fallback path
                    Map.of()
            );
        }

        // Separate backstab vs normal attacks
        List<VanillaAttackInfo> normals = new ArrayList<>();
        List<VanillaAttackInfo> backstabs = new ArrayList<>();
        for (VanillaAttackInfo attack : attacks) {
            if (attack.isBackstab()) {
                backstabs.add(attack);
            } else {
                normals.add(attack);
            }
        }

        // Sort normals by damage ascending
        normals.sort((a, b) -> Float.compare(a.damage(), b.damage()));

        // Interpolate multipliers from config curve
        List<Double> interpolated = interpolateMultipliers(
                familyProfile.normalMultipliers(), normals.size());

        // Build effectiveness map
        Map<Float, Float> effectiveness = new HashMap<>();
        for (int i = 0; i < normals.size(); i++) {
            effectiveness.put(normals.get(i).damage(), interpolated.get(i).floatValue());
        }
        for (VanillaAttackInfo backstab : backstabs) {
            effectiveness.put(backstab.damage(), (float) familyProfile.backstabMultiplier());
        }

        // Compute min/max and geometric mean (for fallback path in RPGDamageSystem)
        float min = Float.MAX_VALUE;
        float max = Float.MIN_VALUE;
        for (VanillaAttackInfo attack : attacks) {
            if (attack.damage() < min) min = attack.damage();
            if (attack.damage() > max) max = attack.damage();
        }
        if (min <= 0) min = 0.1f;
        if (max <= min) max = min;
        float reference = (float) Math.sqrt(min * max);

        return new VanillaWeaponProfile(
                itemId,
                weaponFamily,
                List.copyOf(attacks),
                min,
                max,
                reference,
                Collections.unmodifiableMap(effectiveness)
        );
    }

    /**
     * Interpolates N target values from M config entries using linear interpolation.
     *
     * <p>Maps target indices evenly onto the config array's [0, M-1] range:
     * <ul>
     *   <li>N=0 → empty list</li>
     *   <li>N=1 → midpoint of config array</li>
     *   <li>M=1 → broadcast single value</li>
     *   <li>Otherwise: endpoints preserved, smooth interpolation between</li>
     * </ul>
     *
     * @param config The config-defined multiplier entries (M values)
     * @param targetCount The number of target values to produce (N)
     * @return List of N interpolated values
     */
    static List<Double> interpolateMultipliers(List<Double> config, int targetCount) {
        int m = config.size();

        if (targetCount == 0) {
            return List.of();
        }
        if (targetCount == 1) {
            // Single target gets midpoint of config array
            double pos = (m - 1) / 2.0;
            int lo = (int) pos;
            int hi = Math.min(lo + 1, m - 1);
            double frac = pos - lo;
            return List.of(config.get(lo) * (1 - frac) + config.get(hi) * frac);
        }
        if (m == 1) {
            // Single config value broadcast to all targets
            return Collections.nCopies(targetCount, config.getFirst());
        }

        List<Double> result = new ArrayList<>(targetCount);
        for (int i = 0; i < targetCount; i++) {
            double t = (double) i / (targetCount - 1);        // 0.0 → 1.0
            double pos = t * (m - 1);                          // 0.0 → M-1
            int lo = (int) pos;
            int hi = Math.min(lo + 1, m - 1);
            double frac = pos - lo;
            result.add(config.get(lo) * (1 - frac) + config.get(hi) * frac);
        }
        return result;
    }

    /**
     * Gets the attack type multiplier for a vanilla damage value.
     *
     * <p>First checks for an exact match in the pre-computed map.
     * Falls back to calculating damage / referenceDamage for unknown values.
     *
     * @param vanillaDamage The vanilla damage amount from the attack
     * @return The attack type multiplier
     */
    public float getAttackTypeMultiplier(float vanillaDamage) {
        Float exact = damageToEffectiveness.get(vanillaDamage);
        if (exact != null) {
            return exact;
        }

        // Fall back to calculation for unknown damage values
        if (referenceDamage <= 0) {
            return 1.0f;
        }
        return vanillaDamage / referenceDamage;
    }

    /**
     * Checks if this profile has any attacks.
     */
    public boolean hasAttacks() {
        return !attacks.isEmpty();
    }

    /**
     * Gets the effectiveness range as a human-readable string.
     *
     * <p>Reads actual min/max from the damageToEffectiveness values.
     *
     * @return String like "0.50× - 3.50×"
     */
    public String getEffectivenessRangeString() {
        if (damageToEffectiveness.isEmpty()) {
            return "N/A";
        }
        float minEff = Float.MAX_VALUE;
        float maxEff = Float.MIN_VALUE;
        for (float eff : damageToEffectiveness.values()) {
            if (eff < minEff) minEff = eff;
            if (eff > maxEff) maxEff = eff;
        }
        return String.format("%.2f× - %.2f×", minEff, maxEff);
    }
}
