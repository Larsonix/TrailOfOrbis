package io.github.larsonix.trailoforbis.gear.vanilla;

/**
 * Represents a single attack from a vanilla weapon's interaction data.
 *
 * <p>Each weapon has multiple attacks with different damage values:
 * <ul>
 *   <li>Basic swings (Swing_Left, Swing_Right)</li>
 *   <li>Stabs (Stab_Left, Stab_Right)</li>
 *   <li>Signature moves (Pounce_Stab, Pounce_Sweep)</li>
 *   <li>Backstab variants (with AngledDamage)</li>
 * </ul>
 *
 * <p>This record stores the extracted damage for a single attack type,
 * including whether it's a backstab and the angle requirements for backstabs.
 *
 * @param attackName The attack's InteractionVar key (e.g., "Pounce_Stab_Damage")
 * @param damage The base damage value for this attack
 * @param isBackstab True if this is a backstab variant from AngledDamage
 * @param backstabAngle The required angle in degrees (180° = behind) for backstabs
 * @param backstabDistance The angle tolerance in degrees for backstab detection
 */
public record VanillaAttackInfo(
        String attackName,
        float damage,
        boolean isBackstab,
        float backstabAngle,
        float backstabDistance
) {
    /**
     * Creates a normal (non-backstab) attack info.
     *
     * @param attackName The attack name
     * @param damage The damage value
     * @return A new VanillaAttackInfo for a normal attack
     */
    public static VanillaAttackInfo normal(String attackName, float damage) {
        return new VanillaAttackInfo(attackName, damage, false, 0f, 0f);
    }

    /**
     * Creates a backstab attack info.
     *
     * @param attackName The attack name (will have "_Backstab" appended)
     * @param damage The backstab damage value
     * @param angle The required angle in degrees
     * @param distance The angle tolerance in degrees
     * @return A new VanillaAttackInfo for a backstab attack
     */
    public static VanillaAttackInfo backstab(String attackName, float damage, float angle, float distance) {
        return new VanillaAttackInfo(attackName + "_Backstab", damage, true, angle, distance);
    }
}
