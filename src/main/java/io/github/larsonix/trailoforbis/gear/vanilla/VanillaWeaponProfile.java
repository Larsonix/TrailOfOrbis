package io.github.larsonix.trailoforbis.gear.vanilla;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig.FamilyAttackProfile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

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
 * <h2>Lookup Strategy</h2>
 *
 * <p>At runtime, vanilla damage values from Hytale's combat events may differ slightly
 * from the values enumerated at startup (due to float precision, stat modifier
 * interactions, or DamageCalculator computation differences). To handle this,
 * {@link #getAttackTypeMultiplier} uses a <b>nearest-match</b> lookup with tolerance
 * instead of exact {@code Float} equality in a HashMap (which was the previous,
 * fragile approach). Sorted parallel arrays enable O(log n) binary search.
 */
public final class VanillaWeaponProfile {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /**
     * Maximum allowed difference between a runtime damage value and an enumerated
     * damage value for a match. 0.5 is generous enough to absorb float precision
     * and stat modifier drift while being small enough to avoid cross-attack
     * confusion (weapon attacks are typically ≥2.0 apart).
     */
    private static final float MATCH_TOLERANCE = 0.5f;

    private final String itemId;
    private final String weaponFamily;
    private final List<VanillaAttackInfo> attacks;
    private final float minDamage;
    private final float maxDamage;
    private final float referenceDamage;
    private final Map<Float, Float> damageToEffectiveness;

    // Sorted parallel arrays for nearest-match lookup (built from damageToEffectiveness)
    private final float[] sortedDamageKeys;
    private final float[] sortedMultiplierValues;

    public VanillaWeaponProfile(
            String itemId,
            String weaponFamily,
            List<VanillaAttackInfo> attacks,
            float minDamage,
            float maxDamage,
            float referenceDamage,
            Map<Float, Float> damageToEffectiveness
    ) {
        this.itemId = itemId;
        this.weaponFamily = weaponFamily;
        this.attacks = attacks;
        this.minDamage = minDamage;
        this.maxDamage = maxDamage;
        this.referenceDamage = referenceDamage;
        this.damageToEffectiveness = damageToEffectiveness;

        // Build sorted parallel arrays for nearest-match binary search
        if (damageToEffectiveness.isEmpty()) {
            this.sortedDamageKeys = new float[0];
            this.sortedMultiplierValues = new float[0];
        } else {
            float[] keys = new float[damageToEffectiveness.size()];
            int i = 0;
            for (float key : damageToEffectiveness.keySet()) {
                keys[i++] = key;
            }
            Arrays.sort(keys);

            float[] values = new float[keys.length];
            for (int j = 0; j < keys.length; j++) {
                values[j] = damageToEffectiveness.get(keys[j]);
            }
            this.sortedDamageKeys = keys;
            this.sortedMultiplierValues = values;
        }
    }

    // Accessors (replaces record component accessors)
    public String itemId() { return itemId; }
    public String weaponFamily() { return weaponFamily; }
    public List<VanillaAttackInfo> attacks() { return attacks; }
    public float minDamage() { return minDamage; }
    public float maxDamage() { return maxDamage; }
    public float referenceDamage() { return referenceDamage; }
    public Map<Float, Float> damageToEffectiveness() { return damageToEffectiveness; }
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
     * <p>Uses a nearest-match lookup against sorted damage breakpoints with
     * {@link #MATCH_TOLERANCE} tolerance. This is robust against float precision
     * drift between the DamageCalculator.baseDamageRaw values read at startup and
     * the actual damage.getAmount() values received at runtime.
     *
     * <p>Falls back to {@code vanillaDamage / referenceDamage} only for truly
     * unknown damage values (e.g., new attack types not present during discovery).
     *
     * @param vanillaDamage The vanilla damage amount from the attack
     * @return The attack type multiplier
     */
    public float getAttackTypeMultiplier(float vanillaDamage) {
        if (sortedDamageKeys.length == 0) {
            return referenceDamage > 0 ? vanillaDamage / referenceDamage : 1.0f;
        }

        // Binary search for insertion point in sorted damage keys
        int pos = Arrays.binarySearch(sortedDamageKeys, vanillaDamage);
        if (pos >= 0) {
            // Exact match
            return sortedMultiplierValues[pos];
        }

        // No exact match — find the nearest value within tolerance.
        // binarySearch returns -(insertionPoint) - 1 on miss
        int insertIdx = -pos - 1;

        float bestDist = Float.MAX_VALUE;
        int bestIdx = -1;

        // Check the entry at insertIdx (first value >= vanillaDamage)
        if (insertIdx < sortedDamageKeys.length) {
            float dist = Math.abs(sortedDamageKeys[insertIdx] - vanillaDamage);
            if (dist < bestDist) {
                bestDist = dist;
                bestIdx = insertIdx;
            }
        }
        // Check the entry at insertIdx - 1 (last value < vanillaDamage)
        if (insertIdx > 0) {
            float dist = Math.abs(sortedDamageKeys[insertIdx - 1] - vanillaDamage);
            if (dist < bestDist) {
                bestDist = dist;
                bestIdx = insertIdx - 1;
            }
        }

        if (bestIdx >= 0 && bestDist <= MATCH_TOLERANCE) {
            return sortedMultiplierValues[bestIdx];
        }

        // Truly unknown damage value — fall back to raw ratio
        if (referenceDamage <= 0) {
            return 1.0f;
        }
        float fallback = vanillaDamage / referenceDamage;
        LOGGER.at(Level.FINE).log(
                "[WeaponProfile] No match for %s: vanillaDmg=%.2f (nearest=%.2f, dist=%.2f > tol=%.1f) → fallback=%.2fx. Enumerated: %s",
                itemId, vanillaDamage,
                bestIdx >= 0 ? sortedDamageKeys[bestIdx] : -1f,
                bestDist, MATCH_TOLERANCE, fallback,
                Arrays.toString(sortedDamageKeys));
        return fallback;
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
