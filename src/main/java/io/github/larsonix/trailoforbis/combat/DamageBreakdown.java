package io.github.larsonix.trailoforbis.combat;

import io.github.larsonix.trailoforbis.elemental.ElementType;

import javax.annotation.Nonnull;
import java.util.EnumMap;

/**
 * Result of a complete RPG damage calculation with full breakdown.
 *
 * <p>This record contains all the information needed for:
 * <ul>
 *   <li>Applying damage to the defender</li>
 *   <li>Displaying damage indicators</li>
 *   <li>Recording death recap snapshots</li>
 *   <li>Logging and debugging</li>
 * </ul>
 *
 * <p>All damage values are final (after all calculations and defenses).
 *
 * @param physicalDamage Physical damage after armor and physical resistance
 * @param elementalDamage Per-element damage after resistances
 * @param trueDamage True damage (bypasses all defenses)
 * @param totalDamage Sum of all damage types
 * @param preDefenseDamage Total damage before defenses (physical + elemental + true, before armor/resist)
 * @param preDefenseElemental Per-element damage before resistance (for accurate combat log display)
 * @param wasCritical Whether this attack was a critical hit
 * @param critMultiplier The critical multiplier applied (1.0 if no crit)
 * @param wasDodged Whether the attack was dodged (evaded)
 * @param wasBlocked Whether the attack was blocked
 * @param wasParried Whether the attack was parried
 * @param wasMissed Whether the attack missed (accuracy check failed)
 * @param armorReduction Percentage of physical damage reduced by armor
 * @param resistanceReductions Per-element resistance reduction percentages
 * @param shieldAbsorbed Amount of damage absorbed by energy shield
 * @param damageType The primary damage type (for indicator coloring)
 * @param attackType The attack type (melee, projectile, area)
 */
public record DamageBreakdown(
    float physicalDamage,
    @Nonnull EnumMap<ElementType, Float> elementalDamage,
    float trueDamage,
    float totalDamage,

    // Pre-defense values (for accurate combat log display)
    float preDefenseDamage,
    @Nonnull EnumMap<ElementType, Float> preDefenseElemental,

    // Calculation flags
    boolean wasCritical,
    float critMultiplier,
    boolean wasDodged,
    boolean wasBlocked,
    boolean wasParried,
    boolean wasMissed,

    // Defense breakdown (for death recap)
    float armorReduction,
    @Nonnull EnumMap<ElementType, Float> resistanceReductions,
    float shieldAbsorbed,

    // Type information
    @Nonnull DamageType damageType,
    @Nonnull AttackType attackType
) {

    /**
     * Creates a simple DamageBreakdown with just total damage and type.
     *
     * <p>Useful for environmental damage or other simple damage cases where
     * a full breakdown isn't needed.
     *
     * @param damage The total damage amount
     * @param damageType The damage type
     * @return A simple breakdown with the specified damage
     */
    @Nonnull
    public static DamageBreakdown simple(float damage, @Nonnull DamageType damageType) {
        return new DamageBreakdown(
            damageType == DamageType.PHYSICAL ? damage : 0f,
            new EnumMap<>(ElementType.class),
            0f,
            damage,
            damage, // preDefenseDamage = same as total for simple cases
            new EnumMap<>(ElementType.class), // preDefenseElemental
            false,
            1.0f,
            false,
            false,
            false,
            false,
            0f,
            new EnumMap<>(ElementType.class),
            0f,
            damageType,
            AttackType.UNKNOWN
        );
    }

    /**
     * Creates a DamageBreakdown for a fully dodged/blocked/missed attack.
     *
     * @param reason The avoidance reason (WAS_DODGED, WAS_BLOCKED, WAS_PARRIED, WAS_MISSED)
     * @param attackType The attack type
     * @return A breakdown with zero damage and the appropriate flag set
     */
    @Nonnull
    public static DamageBreakdown avoided(@Nonnull AvoidanceReason reason, @Nonnull AttackType attackType) {
        return new DamageBreakdown(
            0f,
            new EnumMap<>(ElementType.class),
            0f,
            0f,
            0f, // preDefenseDamage
            new EnumMap<>(ElementType.class), // preDefenseElemental
            false,
            1.0f,
            reason == AvoidanceReason.DODGED,
            reason == AvoidanceReason.BLOCKED,
            reason == AvoidanceReason.PARRIED,
            reason == AvoidanceReason.MISSED,
            0f,
            new EnumMap<>(ElementType.class),
            0f,
            DamageType.PHYSICAL,
            attackType
        );
    }

    /**
     * @return true if dodged, blocked, parried, or missed
     */
    public boolean wasAvoided() {
        return wasDodged || wasBlocked || wasParried || wasMissed;
    }

    /**
     * @return true if totalDamage > 0 and attack wasn't avoided
     */
    public boolean hasDamage() {
        return totalDamage > 0 && !wasAvoided();
    }

    /**
     * @return Damage for that element (0 if none)
     */
    public float getElementalDamage(@Nonnull ElementType element) {
        return elementalDamage.getOrDefault(element, 0f);
    }

    /**
     * Gets the pre-defense elemental damage for a specific element.
     *
     * <p>This is the elemental damage BEFORE resistance was applied,
     * useful for accurate combat log display.
     *
     * @param element The element type
     * @return Pre-defense damage for that element (0 if none)
     */
    public float getPreDefenseElemental(@Nonnull ElementType element) {
        return preDefenseElemental.getOrDefault(element, 0f);
    }

    /**
     * @return Resistance reduction percentage for this element (0 if none)
     */
    public float getResistanceReduction(@Nonnull ElementType element) {
        return resistanceReductions.getOrDefault(element, 0f);
    }

    public float getTotalElementalDamage() {
        float total = 0f;
        for (float dmg : elementalDamage.values()) {
            total += dmg;
        }
        return total;
    }

    /**
     * @return true if any element has damage > 0
     */
    public boolean hasElementalDamage() {
        for (float dmg : elementalDamage.values()) {
            if (dmg > 0) return true;
        }
        return false;
    }

    /**
     * @return The element with highest damage, or FIRE as default fallback
     */
    @Nonnull
    public ElementType getPrimaryElement() {
        ElementType primary = null;
        float maxDamage = 0f;

        for (var entry : elementalDamage.entrySet()) {
            if (entry.getValue() > maxDamage) {
                maxDamage = entry.getValue();
                primary = entry.getKey();
            }
        }

        return primary != null ? primary : ElementType.FIRE; // Default fallback
    }

    /**
     * Creates a copy with the dodged flag set.
     *
     * @return New breakdown with wasDodged=true and zero damage
     */
    @Nonnull
    public DamageBreakdown withDodged() {
        return new DamageBreakdown(
            0f, new EnumMap<>(ElementType.class), 0f, 0f,
            0f, new EnumMap<>(ElementType.class), // preDefense values
            this.wasCritical, this.critMultiplier,
            true, false, false, false,
            0f, new EnumMap<>(ElementType.class), 0f,
            this.damageType, this.attackType
        );
    }

    /**
     * Creates a copy with the blocked flag set.
     *
     * @return New breakdown with wasBlocked=true and zero damage
     */
    @Nonnull
    public DamageBreakdown withBlocked() {
        return new DamageBreakdown(
            0f, new EnumMap<>(ElementType.class), 0f, 0f,
            0f, new EnumMap<>(ElementType.class), // preDefense values
            this.wasCritical, this.critMultiplier,
            false, true, false, false,
            0f, new EnumMap<>(ElementType.class), 0f,
            this.damageType, this.attackType
        );
    }

    /**
     * Creates a copy with the parried flag set and reduced damage.
     *
     * @param damageReductionMultiplier Multiplier for damage reduction (e.g., 0.5 for 50% damage)
     * @return New breakdown with wasParried=true and reduced damage
     */
    @Nonnull
    public DamageBreakdown withParried(float damageReductionMultiplier) {
        EnumMap<ElementType, Float> reducedElemental = new EnumMap<>(ElementType.class);
        for (var entry : elementalDamage.entrySet()) {
            reducedElemental.put(entry.getKey(), entry.getValue() * damageReductionMultiplier);
        }

        float newPhys = physicalDamage * damageReductionMultiplier;
        float newTrue = trueDamage * damageReductionMultiplier;
        float newTotal = totalDamage * damageReductionMultiplier;

        return new DamageBreakdown(
            newPhys, reducedElemental, newTrue, newTotal,
            this.preDefenseDamage, this.preDefenseElemental, // preserve preDefense values
            this.wasCritical, this.critMultiplier,
            false, false, true, false,
            this.armorReduction, this.resistanceReductions, this.shieldAbsorbed,
            this.damageType, this.attackType
        );
    }

    /**
     * Creates a copy with the missed flag set.
     *
     * @return New breakdown with wasMissed=true and zero damage
     */
    @Nonnull
    public DamageBreakdown withMissed() {
        return new DamageBreakdown(
            0f, new EnumMap<>(ElementType.class), 0f, 0f,
            0f, new EnumMap<>(ElementType.class), // preDefense values
            this.wasCritical, this.critMultiplier,
            false, false, false, true,
            0f, new EnumMap<>(ElementType.class), 0f,
            this.damageType, this.attackType
        );
    }

    /**
     * Creates a copy with updated shield absorption.
     *
     * @param absorbed Amount absorbed by shield
     * @param newTotal New total damage after absorption
     * @return New breakdown with shield absorption recorded
     */
    @Nonnull
    public DamageBreakdown withShieldAbsorbed(float absorbed, float newTotal) {
        return new DamageBreakdown(
            this.physicalDamage, this.elementalDamage, this.trueDamage, newTotal,
            this.preDefenseDamage, this.preDefenseElemental, // preserve preDefense values
            this.wasCritical, this.critMultiplier,
            this.wasDodged, this.wasBlocked, this.wasParried, this.wasMissed,
            this.armorReduction, this.resistanceReductions, absorbed,
            this.damageType, this.attackType
        );
    }

    @Override
    public String toString() {
        if (wasAvoided()) {
            String avoidType = wasDodged ? "DODGED" : wasBlocked ? "BLOCKED" : wasParried ? "PARRIED" : "MISSED";
            return String.format("DamageBreakdown{%s, attackType=%s}", avoidType, attackType);
        }

        StringBuilder sb = new StringBuilder("DamageBreakdown{");
        sb.append(String.format("total=%.1f", totalDamage));

        if (physicalDamage > 0) {
            sb.append(String.format(", phys=%.1f", physicalDamage));
        }

        for (var entry : elementalDamage.entrySet()) {
            if (entry.getValue() > 0) {
                sb.append(String.format(", %s=%.1f", entry.getKey().name().toLowerCase(), entry.getValue()));
            }
        }

        if (trueDamage > 0) {
            sb.append(String.format(", true=%.1f", trueDamage));
        }

        if (wasCritical) {
            sb.append(String.format(", CRIT(x%.2f)", critMultiplier));
        }

        if (armorReduction > 0) {
            sb.append(String.format(", armor=-%.0f%%", armorReduction));
        }

        if (shieldAbsorbed > 0) {
            sb.append(String.format(", shield=%.1f", shieldAbsorbed));
        }

        sb.append(String.format(", type=%s, attack=%s}", damageType, attackType));
        return sb.toString();
    }

    /**
     * Reasons for attack avoidance.
     */
    public enum AvoidanceReason {
        DODGED,
        BLOCKED,
        PARRIED,
        MISSED
    }

    /**
     * Builder for creating DamageBreakdown instances.
     */
    public static class Builder {
        private float physicalDamage;
        private final EnumMap<ElementType, Float> elementalDamage = new EnumMap<>(ElementType.class);
        private float trueDamage;
        private float preDefenseDamage;
        private final EnumMap<ElementType, Float> preDefenseElemental = new EnumMap<>(ElementType.class);
        private boolean wasCritical;
        private float critMultiplier = 1.0f;
        private boolean wasDodged;
        private boolean wasBlocked;
        private boolean wasParried;
        private boolean wasMissed;
        private float armorReduction;
        private final EnumMap<ElementType, Float> resistanceReductions = new EnumMap<>(ElementType.class);
        private float shieldAbsorbed;
        private DamageType damageType = DamageType.PHYSICAL;
        private AttackType attackType = AttackType.UNKNOWN;

        public Builder() {
            // Initialize elemental maps with zeros
            for (ElementType type : ElementType.values()) {
                elementalDamage.put(type, 0f);
                preDefenseElemental.put(type, 0f);
                resistanceReductions.put(type, 0f);
            }
        }

        public Builder physicalDamage(float value) {
            this.physicalDamage = value;
            return this;
        }

        public Builder elementalDamage(@Nonnull ElementType element, float value) {
            this.elementalDamage.put(element, value);
            return this;
        }

        public Builder elementalDamage(@Nonnull EnumMap<ElementType, Float> values) {
            this.elementalDamage.putAll(values);
            return this;
        }

        public Builder trueDamage(float value) {
            this.trueDamage = value;
            return this;
        }

        public Builder preDefenseDamage(float value) {
            this.preDefenseDamage = value;
            return this;
        }

        public Builder preDefenseElemental(@Nonnull ElementType element, float value) {
            this.preDefenseElemental.put(element, value);
            return this;
        }

        public Builder preDefenseElemental(@Nonnull EnumMap<ElementType, Float> values) {
            this.preDefenseElemental.putAll(values);
            return this;
        }

        public Builder wasCritical(boolean value) {
            this.wasCritical = value;
            return this;
        }

        public Builder critMultiplier(float value) {
            this.critMultiplier = value;
            return this;
        }

        public Builder wasDodged(boolean value) {
            this.wasDodged = value;
            return this;
        }

        public Builder wasBlocked(boolean value) {
            this.wasBlocked = value;
            return this;
        }

        public Builder wasParried(boolean value) {
            this.wasParried = value;
            return this;
        }

        public Builder wasMissed(boolean value) {
            this.wasMissed = value;
            return this;
        }

        public Builder armorReduction(float value) {
            this.armorReduction = value;
            return this;
        }

        public Builder resistanceReduction(@Nonnull ElementType element, float value) {
            this.resistanceReductions.put(element, value);
            return this;
        }

        public Builder shieldAbsorbed(float value) {
            this.shieldAbsorbed = value;
            return this;
        }

        public Builder damageType(@Nonnull DamageType value) {
            this.damageType = value;
            return this;
        }

        public Builder attackType(@Nonnull AttackType value) {
            this.attackType = value;
            return this;
        }

        /**
         * Builds the DamageBreakdown, calculating total damage automatically.
         */
        @Nonnull
        public DamageBreakdown build() {
            float total = physicalDamage + trueDamage;
            for (float elemDmg : elementalDamage.values()) {
                total += elemDmg;
            }

            return new DamageBreakdown(
                physicalDamage,
                new EnumMap<>(elementalDamage),
                trueDamage,
                total,
                preDefenseDamage,
                new EnumMap<>(preDefenseElemental),
                wasCritical,
                critMultiplier,
                wasDodged,
                wasBlocked,
                wasParried,
                wasMissed,
                armorReduction,
                new EnumMap<>(resistanceReductions),
                shieldAbsorbed,
                damageType,
                attackType
            );
        }
    }

    /**
     * Creates a new builder for DamageBreakdown.
     */
    @Nonnull
    public static Builder builder() {
        return new Builder();
    }
}
