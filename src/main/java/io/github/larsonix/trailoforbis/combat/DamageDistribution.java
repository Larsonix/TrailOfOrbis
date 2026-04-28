package io.github.larsonix.trailoforbis.combat;

import io.github.larsonix.trailoforbis.elemental.ElementType;

import javax.annotation.Nonnull;
import java.util.EnumMap;
import java.util.Map;

/**
 * Tracks damage split across types during RPG damage calculation.
 *
 * <p>This class maintains separate buckets for physical, elemental (per type),
 * and true damage throughout the calculation pipeline. It supports:
 * <ul>
 *   <li>Physical to elemental conversion (capped at 100% total)</li>
 *   <li>Adding flat elemental damage</li>
 *   <li>Applying global multipliers (including crit)</li>
 *   <li>Per-element multipliers</li>
 * </ul>
 *
 * <p>The calculation order follows Path of Exile style:
 * <ol>
 *   <li>Set base physical damage</li>
 *   <li>Add flat physical damage bonuses</li>
 *   <li>Add flat elemental damage (early, benefits from modifiers + crit)</li>
 *   <li>Apply damage conversion (physical → elemental)</li>
 *   <li>Apply % increased to physical</li>
 *   <li>Apply elemental % modifiers (includes converted damage)</li>
 *   <li>Apply % more (multiplicative, to all damage)</li>
 *   <li>Apply conditional multipliers (realm damage, execute, etc.)</li>
 *   <li>Apply critical strike (to ALL damage types)</li>
 *   <li>Apply defenses (armor, resistances)</li>
 *   <li>Add true damage (bypasses defenses)</li>
 * </ol>
 *
 * <p><b>Key insight:</b> Critical strike is applied AFTER all scaling so it
 * multiplies both physical AND elemental damage equally.
 *
 * <p>Thread-safety: This class is NOT thread-safe. Each calculation should
 * use a fresh instance.
 */
public class DamageDistribution {

    /** Physical damage (before/after conversion) */
    private float physical;

    /** Elemental damage per type */
    private final EnumMap<ElementType, Float> elemental;

    /** True damage (bypasses all defenses) */
    private float trueDamage;

    public DamageDistribution() {
        this.elemental = new EnumMap<>(ElementType.class);
        for (ElementType type : ElementType.values()) {
            this.elemental.put(type, 0f);
        }
    }

    // ==================== Getters ====================

    /**
     * @return Physical damage (may be reduced after conversion)
     */
    public float getPhysical() {
        return physical;
    }

    public float getElemental(@Nonnull ElementType element) {
        return elemental.getOrDefault(element, 0f);
    }

    /**
     * @return Copy of elemental damage map
     */
    @Nonnull
    public Map<ElementType, Float> getElementalMap() {
        return new EnumMap<>(elemental);
    }

    /**
     * @return True damage (bypasses defenses)
     */
    public float getTrueDamage() {
        return trueDamage;
    }

    public float getTotal() {
        float total = physical + trueDamage;
        for (float elemDmg : elemental.values()) {
            total += elemDmg;
        }
        return total;
    }

    public float getTotalElemental() {
        float total = 0f;
        for (float elemDmg : elemental.values()) {
            total += elemDmg;
        }
        return total;
    }

    // ==================== Setters ====================

    public void setPhysical(float physical) {
        this.physical = Math.max(0f, physical);
    }

    public void setElemental(@Nonnull ElementType element, float damage) {
        elemental.put(element, Math.max(0f, damage));
    }

    public void setTrueDamage(float trueDamage) {
        this.trueDamage = Math.max(0f, trueDamage);
    }

    // ==================== Adders ====================

    /**
     * @param amount Amount to add (can be negative)
     */
    public void addPhysical(float amount) {
        this.physical = Math.max(0f, this.physical + amount);
    }

    public void addElemental(@Nonnull ElementType element, float amount) {
        float current = elemental.getOrDefault(element, 0f);
        elemental.put(element, Math.max(0f, current + amount));
    }

    public void addTrueDamage(float amount) {
        this.trueDamage = Math.max(0f, this.trueDamage + amount);
    }

    // ==================== Conversion ====================

    /**
     * Converts a percentage of physical damage to elemental damage.
     *
     * <p>The converted portion is removed from physical and added to the
     * specified element. Total conversion across all elements is capped at 100%.
     *
     * <p>Example: 50% fire conversion on 100 physical:
     * <ul>
     *   <li>Physical: 100 → 50</li>
     *   <li>Fire: 0 → 50</li>
     * </ul>
     *
     * @param element The element to convert to
     * @param percent Percentage to convert (0-100, will be scaled if total > 100%)
     * @param scaleFactor Scale factor if total conversion exceeds 100% (pass 1.0 if not scaling)
     * @return The amount of damage converted
     */
    public float convertPhysical(@Nonnull ElementType element, float percent, float scaleFactor) {
        if (percent <= 0 || physical <= 0) {
            return 0f;
        }

        float effectivePercent = percent * scaleFactor;
        float convertedAmount = physical * effectivePercent / 100f;

        // Remove from physical, add to elemental
        this.physical -= convertedAmount;
        addElemental(element, convertedAmount);

        return convertedAmount;
    }

    /**
     * Converts physical damage to elemental with automatic 100% cap handling.
     *
     * @param element The element to convert to
     * @param percent Percentage to convert (0-100)
     * @return The amount of damage converted
     */
    public float convertPhysical(@Nonnull ElementType element, float percent) {
        return convertPhysical(element, percent, 1.0f);
    }

    // ==================== Multipliers ====================

    /**
     * Applies a global multiplier to ALL damage types.
     *
     * <p>Used for critical strikes, % more modifiers, etc.
     *
     * @param multiplier The multiplier to apply (e.g., 1.5 for 50% more)
     */
    public void applyMultiplier(float multiplier) {
        if (multiplier <= 0 || Float.isNaN(multiplier) || Float.isInfinite(multiplier)) {
            this.physical = 0;
            this.trueDamage = 0;
            for (ElementType type : ElementType.values()) {
                elemental.put(type, 0f);
            }
            return;
        }

        this.physical *= multiplier;
        this.trueDamage *= multiplier;
        for (ElementType type : ElementType.values()) {
            float current = elemental.getOrDefault(type, 0f);
            elemental.put(type, current * multiplier);
        }
    }

    public void applyPhysicalMultiplier(float multiplier) {
        this.physical = Math.max(0f, this.physical * multiplier);
    }

    public void applyElementalMultiplier(@Nonnull ElementType element, float multiplier) {
        float current = elemental.getOrDefault(element, 0f);
        elemental.put(element, Math.max(0f, current * multiplier));
    }

    public void applyAllElementalMultiplier(float multiplier) {
        for (ElementType type : ElementType.values()) {
            float current = elemental.getOrDefault(type, 0f);
            elemental.put(type, Math.max(0f, current * multiplier));
        }
    }

    // ==================== Percentage Increases ====================

    /**
     * Applies a percentage increase to physical damage.
     *
     * <p>Formula: {@code physical * (1 + percent / 100)}
     *
     * @param percent The percentage increase (e.g., 50 for +50%)
     */
    public void applyPhysicalPercentIncrease(float percent) {
        this.physical *= (1f + percent / 100f);
    }

    public void applyElementalPercentIncrease(@Nonnull ElementType element, float percent) {
        float current = elemental.getOrDefault(element, 0f);
        elemental.put(element, current * (1f + percent / 100f));
    }

    // ==================== Utility ====================

    public boolean hasDamage() {
        return getTotal() > 0;
    }

    public boolean hasElementalDamage() {
        for (float dmg : elemental.values()) {
            if (dmg > 0) return true;
        }
        return false;
    }

    /**
     * Gets the primary damage type based on the dominant damage source.
     *
     * <p>If the highest elemental damage exceeds physical damage, returns
     * the corresponding element-specific {@link DamageType}. Otherwise
     * returns {@link DamageType#PHYSICAL}.
     *
     * @return The DamageType matching the dominant damage source
     */
    @Nonnull
    public DamageType getPrimaryDamageType() {
        float maxElemental = 0f;
        ElementType maxElement = null;

        for (ElementType type : ElementType.values()) {
            float dmg = elemental.getOrDefault(type, 0f);
            if (dmg > maxElemental) {
                maxElemental = dmg;
                maxElement = type;
            }
        }

        // Physical wins if higher than any single elemental
        if (physical >= maxElemental || maxElement == null) {
            return DamageType.PHYSICAL;
        }

        return DamageType.fromElement(maxElement);
    }

    public void reset() {
        this.physical = 0f;
        this.trueDamage = 0f;
        for (ElementType type : ElementType.values()) {
            elemental.put(type, 0f);
        }
    }

    /**
     * Creates a deep copy of this distribution.
     */
    @Nonnull
    public DamageDistribution copy() {
        DamageDistribution copy = new DamageDistribution();
        copy.physical = this.physical;
        copy.trueDamage = this.trueDamage;
        for (ElementType type : ElementType.values()) {
            copy.elemental.put(type, this.elemental.getOrDefault(type, 0f));
        }
        return copy;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("DamageDistribution{phys=");
        sb.append(String.format("%.1f", physical));

        for (ElementType type : ElementType.values()) {
            float dmg = elemental.getOrDefault(type, 0f);
            if (dmg > 0) {
                sb.append(", ").append(type.name().toLowerCase()).append("=").append(String.format("%.1f", dmg));
            }
        }

        if (trueDamage > 0) {
            sb.append(", true=").append(String.format("%.1f", trueDamage));
        }

        sb.append(", total=").append(String.format("%.1f", getTotal())).append("}");
        return sb.toString();
    }
}
