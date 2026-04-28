package io.github.larsonix.trailoforbis.skilltree.calculation;

import io.github.larsonix.trailoforbis.skilltree.model.ModifierType;
import io.github.larsonix.trailoforbis.skilltree.model.StatModifier;
import io.github.larsonix.trailoforbis.skilltree.model.StatType;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * Collects and organizes modifiers by stat and type for efficient calculation.
 *
 * <p>This class aggregates all modifiers from various sources (skill tree, equipment, buffs)
 * into a structure optimized for the PoE-style calculation formula:
 * Final = (Base + Flat) × (1 + Sum(Percent)/100) × Product(1 + Multiplier/100)
 */
public class AggregatedModifiers {
    // Map: StatType -> ModifierType -> List of values
    private final Map<StatType, Map<ModifierType, List<Float>>> modifiers;

    private AggregatedModifiers(Map<StatType, Map<ModifierType, List<Float>>> modifiers) {
        this.modifiers = modifiers;
    }

    /**
     * Gets the sum of all FLAT modifiers for a stat.
     *
     * @param stat The stat type to query
     * @return The sum of all flat modifiers (0 if none)
     */
    public float getFlatSum(@Nonnull StatType stat) {
        return getModifierSum(stat, ModifierType.FLAT);
    }

    /**
     * Gets the sum of all PERCENT modifiers for a stat.
     * These are additive with each other before being applied.
     *
     * @param stat The stat type to query
     * @return The sum of all percent modifiers (0 if none)
     */
    public float getPercentSum(@Nonnull StatType stat) {
        return getModifierSum(stat, ModifierType.PERCENT);
    }

    /**
     * Gets all MULTIPLIER values for a stat.
     * Each multiplier is applied individually (multiplicative stacking).
     *
     * @param stat The stat type to query
     * @return List of multiplier values (empty list if none)
     */
    @Nonnull
    public List<Float> getMultipliers(@Nonnull StatType stat) {
        Map<ModifierType, List<Float>> byType = modifiers.get(stat);
        if (byType == null) return Collections.emptyList();
        List<Float> mults = byType.get(ModifierType.MULTIPLIER);
        return mults != null ? Collections.unmodifiableList(mults) : Collections.emptyList();
    }

    /**
     * Checks if there are any modifiers for a given stat.
     *
     * @param stat The stat type to check
     * @return true if any modifiers exist for this stat
     */
    public boolean hasModifiers(@Nonnull StatType stat) {
        Map<ModifierType, List<Float>> byType = modifiers.get(stat);
        if (byType == null) return false;
        return byType.values().stream().anyMatch(list -> !list.isEmpty());
    }

    private float getModifierSum(StatType stat, ModifierType type) {
        Map<ModifierType, List<Float>> byType = modifiers.get(stat);
        if (byType == null) return 0f;
        List<Float> values = byType.get(type);
        if (values == null || values.isEmpty()) return 0f;
        return (float) values.stream().mapToDouble(Float::doubleValue).sum();
    }

    /**
     * Creates a new builder for aggregating modifiers.
     *
     * @return A new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for creating AggregatedModifiers instances.
     */
    public static class Builder {
        private final Map<StatType, Map<ModifierType, List<Float>>> modifiers = new EnumMap<>(StatType.class);

        /**
         * Adds a single modifier.
         *
         * @param modifier The modifier to add
         * @return This builder for chaining
         */
        public Builder addModifier(@Nonnull StatModifier modifier) {
            modifiers
                .computeIfAbsent(modifier.getStat(), k -> new EnumMap<>(ModifierType.class))
                .computeIfAbsent(modifier.getType(), k -> new ArrayList<>())
                .add(modifier.getValue());
            return this;
        }

        /**
         * Adds multiple modifiers.
         *
         * @param mods Collection of modifiers to add
         * @return This builder for chaining
         */
        public Builder addAllModifiers(@Nonnull Collection<StatModifier> mods) {
            mods.forEach(this::addModifier);
            return this;
        }

        /**
         * Builds the AggregatedModifiers instance.
         *
         * @return A new immutable AggregatedModifiers instance
         */
        public AggregatedModifiers build() {
            return new AggregatedModifiers(modifiers);
        }
    }
}
