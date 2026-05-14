package io.github.larsonix.trailoforbis.mobs.modifiers;

import io.github.larsonix.trailoforbis.elemental.ElementType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Immutable stat bonus definition for a mob modifier.
 *
 * <p>Each modifier carries a StatBonus describing what stat changes
 * it applies to the mob. Values are multiplicative bonuses (0.50 = +50%).
 * Element damage is a map of element to bonus ratio (0.30 = +30% of base as element).
 */
public record StatBonus(
    double armorPercent,
    double maxHpPercent,
    double damagePercent,
    double speedPercent,
    double knockbackResist,
    double evasionChance,
    double elementalResistPercent,
    @Nonnull Map<ElementType, Double> elementDamagePercent,
    double ailmentChanceBonus,
    double reflectPercent
) {

    public static final StatBonus EMPTY = new StatBonus(0, 0, 0, 0, 0, 0, 0, Map.of(), 0, 0);

    public StatBonus {
        if (elementDamagePercent == null || elementDamagePercent.isEmpty()) {
            elementDamagePercent = Map.of();
        } else {
            elementDamagePercent = Collections.unmodifiableMap(new EnumMap<>(elementDamagePercent));
        }
    }

    // Builder-style factory methods for concise enum construction

    public static StatBonus armor(double pct) {
        return new StatBonus(pct, 0, 0, 0, 0, 0, 0, Map.of(), 0, 0);
    }

    public static StatBonus maxHp(double pct) {
        return new StatBonus(0, pct, 0, 0, 0, 0, 0, Map.of(), 0, 0);
    }

    public static StatBonus damage(double pct) {
        return new StatBonus(0, 0, pct, 0, 0, 0, 0, Map.of(), 0, 0);
    }

    public static StatBonus speed(double pct) {
        return new StatBonus(0, 0, 0, pct, 0, 0, 0, Map.of(), 0, 0);
    }

    public static StatBonus knockback(double resist) {
        return new StatBonus(0, 0, 0, 0, resist, 0, 0, Map.of(), 0, 0);
    }

    public static StatBonus evasion(double chance) {
        return new StatBonus(0, 0, 0, 0, 0, chance, 0, Map.of(), 0, 0);
    }

    public static StatBonus elementalResist(double pct) {
        return new StatBonus(0, 0, 0, 0, 0, 0, pct, Map.of(), 0, 0);
    }

    public static StatBonus reflect(double pct) {
        return new StatBonus(0, 0, 0, 0, 0, 0, 0, Map.of(), 0, pct);
    }

    public static StatBonus elementDamage(@Nonnull ElementType element, double pct, double ailmentBonus) {
        Map<ElementType, Double> map = new EnumMap<>(ElementType.class);
        map.put(element, pct);
        return new StatBonus(0, 0, 0, 0, 0, 0, 0, map, ailmentBonus, 0);
    }

    /**
     * Returns true if this bonus has any non-zero stat effect.
     */
    public boolean hasEffect() {
        return armorPercent != 0 || maxHpPercent != 0 || damagePercent != 0
            || speedPercent != 0 || knockbackResist != 0 || evasionChance != 0
            || elementalResistPercent != 0 || !elementDamagePercent.isEmpty()
            || ailmentChanceBonus != 0 || reflectPercent != 0;
    }

    /**
     * Returns the elemental damage bonus for a specific element, or 0.
     */
    public double getElementDamage(@Nullable ElementType element) {
        if (element == null) return 0;
        return elementDamagePercent.getOrDefault(element, 0.0);
    }
}
