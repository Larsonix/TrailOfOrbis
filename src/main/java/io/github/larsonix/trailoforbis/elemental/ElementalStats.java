package io.github.larsonix.trailoforbis.elemental;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Container for all elemental damage and resistance stats.
 *
 * <p>Used by both players ({@code ComputedStats}) and mobs ({@code MobStats}).
 * Supports the PoE-style damage formula with flat, percent, and multiplier bonuses.
 *
 * <p>Formula: {@code (Base + Flat) × (1 + Percent/100) × (1 + Multiplier/100)}
 *
 * <p>Thread-safe: This class is mutable but designed for single-threaded access
 * during entity processing. For concurrent access, external synchronization is needed.
 */
public class ElementalStats {

    // Flat damage per element (base elemental damage)
    private final Map<ElementType, Double> flatDamage;

    // Percent damage bonus per element (additive with other percent bonuses)
    private final Map<ElementType, Double> percentDamage;

    // Multiplier damage bonus per element (multiplicative "more" damage)
    private final Map<ElementType, Double> multiplierDamage;

    // Resistance per element (damage reduction %)
    private final Map<ElementType, Double> resistance;

    // Penetration per element (reduces target's effective resistance)
    private final Map<ElementType, Double> penetration;

    /**
     * Creates a new ElementalStats with all values initialized to 0.
     */
    public ElementalStats() {
        this.flatDamage = new EnumMap<>(ElementType.class);
        this.percentDamage = new EnumMap<>(ElementType.class);
        this.multiplierDamage = new EnumMap<>(ElementType.class);
        this.resistance = new EnumMap<>(ElementType.class);
        this.penetration = new EnumMap<>(ElementType.class);

        // Initialize all elements to 0
        for (ElementType type : ElementType.values()) {
            flatDamage.put(type, 0.0);
            percentDamage.put(type, 0.0);
            multiplierDamage.put(type, 0.0);
            resistance.put(type, 0.0);
            penetration.put(type, 0.0);
        }
    }


    // ==================== Getters ====================

    /**
     * Gets the flat (base) damage for an element.
     *
     * @param type The element type
     * @return Flat damage value
     */
    public double getFlatDamage(@Nonnull ElementType type) {
        return flatDamage.getOrDefault(type, 0.0);
    }

    /**
     * Gets the percent damage bonus for an element.
     *
     * @param type The element type
     * @return Percent bonus (e.g., 50.0 for +50%)
     */
    public double getPercentDamage(@Nonnull ElementType type) {
        return percentDamage.getOrDefault(type, 0.0);
    }

    /**
     * Gets the multiplier damage bonus for an element.
     *
     * @param type The element type
     * @return Multiplier bonus (e.g., 30.0 for ×1.30 more damage)
     */
    public double getMultiplierDamage(@Nonnull ElementType type) {
        return multiplierDamage.getOrDefault(type, 0.0);
    }

    /**
     * Gets the resistance for an element.
     *
     * @param type The element type
     * @return Resistance percentage (capped at 75% in calculations)
     */
    public double getResistance(@Nonnull ElementType type) {
        return resistance.getOrDefault(type, 0.0);
    }

    /**
     * Gets the penetration for an element.
     *
     * <p>Penetration reduces the target's effective resistance. For example,
     * 20 fire penetration against a target with 50% fire resistance results
     * in the target having an effective 30% fire resistance.
     *
     * @param type The element type
     * @return Penetration value (percentage points)
     */
    public double getPenetration(@Nonnull ElementType type) {
        return penetration.getOrDefault(type, 0.0);
    }

    // ==================== Setters ====================

    /**
     * Sets the flat (base) damage for an element.
     *
     * @param type  The element type
     * @param value Flat damage value
     */
    public void setFlatDamage(@Nonnull ElementType type, double value) {
        flatDamage.put(type, value);
    }

    /**
     * Sets the percent damage bonus for an element.
     *
     * @param type  The element type
     * @param value Percent bonus (e.g., 50.0 for +50%)
     */
    public void setPercentDamage(@Nonnull ElementType type, double value) {
        percentDamage.put(type, value);
    }

    /**
     * Sets the multiplier damage bonus for an element.
     *
     * @param type  The element type
     * @param value Multiplier bonus (e.g., 30.0 for ×1.30)
     */
    public void setMultiplierDamage(@Nonnull ElementType type, double value) {
        multiplierDamage.put(type, value);
    }

    /**
     * Sets the resistance for an element.
     *
     * @param type  The element type
     * @param value Resistance percentage
     */
    public void setResistance(@Nonnull ElementType type, double value) {
        resistance.put(type, value);
    }

    /**
     * Sets the penetration for an element.
     *
     * @param type  The element type
     * @param value Penetration value (percentage points)
     */
    public void setPenetration(@Nonnull ElementType type, double value) {
        penetration.put(type, value);
    }

    // ==================== Adders (for stacking bonuses) ====================

    /**
     * Adds to the flat damage for an element.
     *
     * @param type  The element type
     * @param value Amount to add
     */
    public void addFlatDamage(@Nonnull ElementType type, double value) {
        flatDamage.merge(type, value, Double::sum);
    }

    /**
     * Adds to the percent damage bonus for an element.
     *
     * @param type  The element type
     * @param value Amount to add
     */
    public void addPercentDamage(@Nonnull ElementType type, double value) {
        percentDamage.merge(type, value, Double::sum);
    }

    /**
     * Adds to the multiplier damage bonus for an element.
     *
     * @param type  The element type
     * @param value Amount to add
     */
    public void addMultiplierDamage(@Nonnull ElementType type, double value) {
        multiplierDamage.merge(type, value, Double::sum);
    }

    /**
     * Adds to the resistance for an element.
     *
     * @param type  The element type
     * @param value Amount to add
     */
    public void addResistance(@Nonnull ElementType type, double value) {
        resistance.merge(type, value, Double::sum);
    }

    /**
     * Adds to the penetration for an element.
     *
     * @param type  The element type
     * @param value Amount to add
     */
    public void addPenetration(@Nonnull ElementType type, double value) {
        penetration.merge(type, value, Double::sum);
    }

    // ==================== Utility Methods ====================

    /**
     * Checks if this entity deals any elemental damage.
     *
     * @return true if any element has flat damage > 0
     */
    public boolean hasAnyElementalDamage() {
        for (ElementType type : ElementType.values()) {
            if (flatDamage.getOrDefault(type, 0.0) > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets the primary element (highest flat damage).
     *
     * @return The element with highest flat damage, or null if none
     */
    @Nullable
    public ElementType getPrimaryElement() {
        ElementType primary = null;
        double maxDamage = 0;

        for (ElementType type : ElementType.values()) {
            double dmg = flatDamage.getOrDefault(type, 0.0);
            if (dmg > maxDamage) {
                maxDamage = dmg;
                primary = type;
            }
        }

        return primary;
    }

    /**
     * Gets the total flat elemental damage across all elements.
     *
     * @return Sum of all flat elemental damage
     */
    public double getTotalFlatDamage() {
        double total = 0;
        for (ElementType type : ElementType.values()) {
            total += flatDamage.getOrDefault(type, 0.0);
        }
        return total;
    }

    /**
     * Gets the average resistance across all elements.
     *
     * @return Average resistance percentage
     */
    public double getAverageResistance() {
        double total = 0;
        for (ElementType type : ElementType.values()) {
            total += resistance.getOrDefault(type, 0.0);
        }
        return total / ElementType.values().length;
    }

    /**
     * Creates a copy of this ElementalStats.
     *
     * @return A new ElementalStats with the same values
     */
    @Nonnull
    public ElementalStats copy() {
        ElementalStats copy = new ElementalStats();
        for (ElementType type : ElementType.values()) {
            copy.setFlatDamage(type, this.getFlatDamage(type));
            copy.setPercentDamage(type, this.getPercentDamage(type));
            copy.setMultiplierDamage(type, this.getMultiplierDamage(type));
            copy.setResistance(type, this.getResistance(type));
            copy.setPenetration(type, this.getPenetration(type));
        }
        return copy;
    }

    /**
     * Resets all values to 0.
     */
    public void reset() {
        for (ElementType type : ElementType.values()) {
            flatDamage.put(type, 0.0);
            percentDamage.put(type, 0.0);
            multiplierDamage.put(type, 0.0);
            resistance.put(type, 0.0);
            penetration.put(type, 0.0);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ElementalStats that = (ElementalStats) o;
        return Objects.equals(flatDamage, that.flatDamage)
            && Objects.equals(percentDamage, that.percentDamage)
            && Objects.equals(multiplierDamage, that.multiplierDamage)
            && Objects.equals(resistance, that.resistance)
            && Objects.equals(penetration, that.penetration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(flatDamage, percentDamage, multiplierDamage, resistance, penetration);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ElementalStats{");
        boolean first = true;

        for (ElementType type : ElementType.values()) {
            double flat = getFlatDamage(type);
            double resist = getResistance(type);

            if (flat > 0 || resist > 0) {
                if (!first) sb.append(", ");
                first = false;

                sb.append(type.name()).append("=[");
                if (flat > 0) {
                    sb.append("dmg=").append(String.format("%.1f", flat));
                    double pct = getPercentDamage(type);
                    double mult = getMultiplierDamage(type);
                    if (pct > 0) sb.append("+").append(String.format("%.0f%%", pct));
                    if (mult > 0) sb.append("×").append(String.format("%.0f%%", mult));
                }
                if (resist > 0) {
                    if (flat > 0) sb.append(", ");
                    sb.append("resist=").append(String.format("%.0f%%", resist));
                }
                sb.append("]");
            }
        }

        sb.append("}");
        return sb.toString();
    }
}
