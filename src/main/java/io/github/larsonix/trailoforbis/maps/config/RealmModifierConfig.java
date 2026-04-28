package io.github.larsonix.trailoforbis.maps.config;

import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifierType;
import io.github.larsonix.trailoforbis.util.LevelScaling;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * Configuration for realm modifier generation and behavior.
 *
 * <p>Loaded from {@code config/realm-modifiers.yml}.
 *
 * <p>Contains:
 * <ul>
 *   <li>Separate prefix/suffix count ranges per rarity</li>
 *   <li>Per-modifier weights for weighted random selection</li>
 *   <li>Per-modifier value ranges with level scaling</li>
 * </ul>
 */
public class RealmModifierConfig {

    // ═══════════════════════════════════════════════════════════════════
    // MODIFIER COUNTS BY RARITY (separate prefix/suffix)
    // ═══════════════════════════════════════════════════════════════════

    private Map<GearRarity, Integer> maxModifiers = new EnumMap<>(GearRarity.class);
    private Map<GearRarity, ModifierCountRange> prefixCounts = new EnumMap<>(GearRarity.class);
    private Map<GearRarity, ModifierCountRange> suffixCounts = new EnumMap<>(GearRarity.class);

    // ═══════════════════════════════════════════════════════════════════
    // MODIFIER WEIGHTS
    // ═══════════════════════════════════════════════════════════════════

    private Map<RealmModifierType, Double> modifierWeights = new EnumMap<>(RealmModifierType.class);
    private Map<RealmModifierType, ModifierSettings> modifierSettings = new EnumMap<>(RealmModifierType.class);

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════════

    public RealmModifierConfig() {
        initializeDefaults();
    }

    private void initializeDefaults() {
        // Default max modifiers by rarity (total cap)
        maxModifiers.put(GearRarity.COMMON, 1);
        maxModifiers.put(GearRarity.UNCOMMON, 2);
        maxModifiers.put(GearRarity.RARE, 3);
        maxModifiers.put(GearRarity.EPIC, 4);
        maxModifiers.put(GearRarity.LEGENDARY, 5);
        maxModifiers.put(GearRarity.MYTHIC, 6);

        // Default prefix counts by rarity
        prefixCounts.put(GearRarity.COMMON, new ModifierCountRange(0, 1));
        prefixCounts.put(GearRarity.UNCOMMON, new ModifierCountRange(0, 2));
        prefixCounts.put(GearRarity.RARE, new ModifierCountRange(0, 3));
        prefixCounts.put(GearRarity.EPIC, new ModifierCountRange(1, 3));
        prefixCounts.put(GearRarity.LEGENDARY, new ModifierCountRange(2, 3));
        prefixCounts.put(GearRarity.MYTHIC, new ModifierCountRange(3, 3));

        // Default suffix counts by rarity
        suffixCounts.put(GearRarity.COMMON, new ModifierCountRange(0, 1));
        suffixCounts.put(GearRarity.UNCOMMON, new ModifierCountRange(0, 2));
        suffixCounts.put(GearRarity.RARE, new ModifierCountRange(0, 3));
        suffixCounts.put(GearRarity.EPIC, new ModifierCountRange(1, 3));
        suffixCounts.put(GearRarity.LEGENDARY, new ModifierCountRange(2, 3));
        suffixCounts.put(GearRarity.MYTHIC, new ModifierCountRange(3, 3));

        // Default modifier weights (all equal by default)
        for (RealmModifierType type : RealmModifierType.values()) {
            modifierWeights.put(type, 10.0);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // MODIFIER COUNT METHODS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Result of rolling modifier counts, split into prefix and suffix.
     *
     * @param prefixCount Number of prefixes to roll
     * @param suffixCount Number of suffixes to roll
     */
    public record ModifierCounts(int prefixCount, int suffixCount) {
        public int total() { return prefixCount + suffixCount; }
    }

    /**
     * Gets the maximum total modifiers for a rarity.
     *
     * @param rarity The rarity tier
     * @return Maximum total modifier count
     */
    public int getMaxModifiers(@Nonnull GearRarity rarity) {
        return maxModifiers.getOrDefault(rarity, 1);
    }

    /**
     * Gets the prefix count range for a rarity.
     *
     * @param rarity The rarity tier
     * @return Min/max prefix count
     */
    @Nonnull
    public ModifierCountRange getPrefixCountRange(@Nonnull GearRarity rarity) {
        return prefixCounts.getOrDefault(rarity, new ModifierCountRange(0, 1));
    }

    /**
     * Gets the suffix count range for a rarity.
     *
     * @param rarity The rarity tier
     * @return Min/max suffix count
     */
    @Nonnull
    public ModifierCountRange getSuffixCountRange(@Nonnull GearRarity rarity) {
        return suffixCounts.getOrDefault(rarity, new ModifierCountRange(0, 1));
    }

    /**
     * Rolls modifier counts using the three-constraint system.
     *
     * <p>The total always equals {@code maxModifiers} for the rarity.
     * The prefix/suffix split is randomly distributed within each pool's
     * [min, max] range, subject to the total constraint.
     *
     * <p>Algorithm: compute valid prefix range as
     * {@code [max(pMin, M-sMax), min(pMax, M-sMin)]}, pick uniformly,
     * then suffix = M - prefix.
     *
     * @param rarity The rarity tier
     * @param random Random source
     * @return Rolled prefix and suffix counts
     */
    @Nonnull
    public ModifierCounts rollModifierCounts(@Nonnull GearRarity rarity, @Nonnull Random random) {
        int total = getMaxModifiers(rarity);
        ModifierCountRange pRange = getPrefixCountRange(rarity);
        ModifierCountRange sRange = getSuffixCountRange(rarity);

        // Compute valid prefix range: prefix + suffix = total
        int minPrefix = Math.max(pRange.min(), total - sRange.max());
        int maxPrefix = Math.min(pRange.max(), total - sRange.min());

        // Clamp to valid bounds
        minPrefix = Math.max(0, minPrefix);
        maxPrefix = Math.max(minPrefix, maxPrefix);

        int prefixCount = minPrefix + (maxPrefix > minPrefix ? random.nextInt(maxPrefix - minPrefix + 1) : 0);
        int suffixCount = total - prefixCount;

        return new ModifierCounts(prefixCount, suffixCount);
    }

    public void setMaxModifiers(@Nonnull GearRarity rarity, int max) {
        maxModifiers.put(rarity, max);
    }

    public void setPrefixCountRange(@Nonnull GearRarity rarity, int min, int max) {
        prefixCounts.put(rarity, new ModifierCountRange(min, max));
    }

    public void setSuffixCountRange(@Nonnull GearRarity rarity, int min, int max) {
        suffixCounts.put(rarity, new ModifierCountRange(min, max));
    }

    // ═══════════════════════════════════════════════════════════════════
    // WEIGHT METHODS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the selection weight for a modifier type.
     *
     * @param type The modifier type
     * @return Selection weight
     */
    public double getWeight(@Nonnull RealmModifierType type) {
        return modifierWeights.getOrDefault(type, 10.0);
    }

    public void setModifierWeight(@Nonnull RealmModifierType type, double weight) {
        modifierWeights.put(type, weight);
    }

    // ═══════════════════════════════════════════════════════════════════
    // MODIFIER SETTINGS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the settings for a specific modifier type.
     *
     * @param type The modifier type
     * @return Modifier settings, or defaults if not configured
     */
    @Nonnull
    public ModifierSettings getModifierSettings(@Nonnull RealmModifierType type) {
        return modifierSettings.getOrDefault(type, ModifierSettings.fromType(type));
    }

    public void setModifierSettings(@Nonnull RealmModifierType type, @Nonnull ModifierSettings settings) {
        modifierSettings.put(type, settings);
    }

    // ═══════════════════════════════════════════════════════════════════
    // NESTED CLASSES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Range for modifier counts (used for both prefix and suffix counts).
     */
    public record ModifierCountRange(int min, int max) {
        public ModifierCountRange {
            if (min < 0) min = 0;
            if (max < min) max = min;
            if (max > 6) max = 6;
        }
    }

    /**
     * Per-modifier type settings with level-based scaling.
     *
     * <p>Numeric modifiers use {@code baseMin}/{@code baseMax} as the value range
     * at level 1, with {@code scalePerLevel} controlling how much the range grows
     * with map level using the shared {@link LevelScaling} curve.
     *
     * <p>Binary modifiers (where {@code baseMin == baseMax}) skip scaling entirely
     * and always return their fixed value regardless of level.
     *
     * <p>Scaling formula (same as gear modifiers):
     * <pre>
     * effectiveLevel = (LevelScaling.getMultiplier(level) - 1.0) × transitionLevel
     * scaledMin = baseMin + effectiveLevel × scalePerLevel
     * scaledMax = baseMax + effectiveLevel × scalePerLevel
     * value = random in [scaledMin, scaledMax] (clamped to ints, min >= 0)
     * </pre>
     */
    public static class ModifierSettings {
        private int baseMin;
        private int baseMax;
        private double scalePerLevel;
        private boolean enabled = true;
        private Set<RealmModifierType> incompatibleWith = new HashSet<>();

        public static ModifierSettings fromType(RealmModifierType type) {
            ModifierSettings settings = new ModifierSettings();
            settings.baseMin = type.getMinValue();
            settings.baseMax = type.getMaxValue();
            settings.scalePerLevel = 0.0;
            return settings;
        }

        public int getBaseMin() {
            return baseMin;
        }

        public void setBaseMin(int baseMin) {
            this.baseMin = baseMin;
        }

        public int getBaseMax() {
            return baseMax;
        }

        public void setBaseMax(int baseMax) {
            this.baseMax = baseMax;
        }

        public double getScalePerLevel() {
            return scalePerLevel;
        }

        public void setScalePerLevel(double scalePerLevel) {
            this.scalePerLevel = scalePerLevel;
        }

        /**
         * Gets the effective minimum value at a given map level.
         *
         * @param level The map level
         * @return Minimum value at that level (clamped to >= 0)
         */
        public int getMinValue(int level) {
            if (isBinary()) {
                return baseMin;
            }
            double effectiveLevel = (LevelScaling.getMultiplier(level) - 1.0) * LevelScaling.getTransitionLevel();
            return Math.max(0, (int) Math.round(baseMin + effectiveLevel * scalePerLevel));
        }

        /**
         * Gets the effective maximum value at a given map level.
         *
         * @param level The map level
         * @return Maximum value at that level (clamped to >= min)
         */
        public int getMaxValue(int level) {
            if (isBinary()) {
                return baseMax;
            }
            double effectiveLevel = (LevelScaling.getMultiplier(level) - 1.0) * LevelScaling.getTransitionLevel();
            int scaledMax = (int) Math.round(baseMax + effectiveLevel * scalePerLevel);
            return Math.max(getMinValue(level), Math.max(0, scaledMax));
        }

        /**
         * Returns true if this is a binary modifier (fixed value, no scaling).
         *
         * @return true if baseMin equals baseMax
         */
        public boolean isBinary() {
            return baseMin == baseMax;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        @Nonnull
        public Set<RealmModifierType> getIncompatibleWith() {
            return Collections.unmodifiableSet(incompatibleWith);
        }

        public void addIncompatible(@Nonnull RealmModifierType type) {
            incompatibleWith.add(type);
        }

        public boolean isCompatibleWith(@Nonnull RealmModifierType type) {
            return !incompatibleWith.contains(type);
        }

        /**
         * Rolls a random value within the level-scaled range.
         *
         * @param random Random source
         * @param level The map level for scaling
         * @return Random value within [scaledMin, scaledMax]
         */
        public int rollValue(@Nonnull Random random, int level) {
            int min = getMinValue(level);
            int max = getMaxValue(level);
            int range = max - min;
            return min + (range > 0 ? random.nextInt(range + 1) : 0);
        }
    }
}
