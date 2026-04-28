package io.github.larsonix.trailoforbis.gear.config;

import io.github.larsonix.trailoforbis.gear.model.ArmorMaterial;
import io.github.larsonix.trailoforbis.gear.model.EquipmentType;
import io.github.larsonix.trailoforbis.gear.model.EquipmentType.ArmorSlot;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.gear.model.WeaponType;
import io.github.larsonix.trailoforbis.util.LevelScaling;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration for gear balance values loaded from gear-balance.yml.
 *
 * <p>This class is immutable after construction. All collections are
 * defensively copied and wrapped in unmodifiable views.
 *
 * <p>Use {@link GearConfigLoader} to create validated instances.
 */
public final class GearBalanceConfig {

    // =========================================================================
    // POWER SCALING
    // =========================================================================

    private final double gearPowerRatio;
    private final Map<String, Double> slotWeights;
    private final double levelScalingFactor;

    // =========================================================================
    // RARITY CONFIGS
    // =========================================================================

    private final Map<GearRarity, RarityConfig> rarityConfigs;

    // =========================================================================
    // QUALITY
    // =========================================================================

    private final QualityConfig quality;

    // =========================================================================
    // ATTRIBUTE REQUIREMENTS
    // =========================================================================

    private final AttributeRequirementsConfig attributeRequirements;

    // =========================================================================
    // MODIFIER SCALING
    // =========================================================================

    private final ModifierScalingConfig modifierScaling;

    // =========================================================================
    // LOOT
    // =========================================================================

    private final LootConfig loot;

    // =========================================================================
    // STONE DROPS
    // =========================================================================

    private final StoneDropConfig stoneDrops;

    // =========================================================================
    // EXPONENTIAL SCALING
    // =========================================================================

    private final ExponentialScalingConfig exponentialScaling;

    // =========================================================================
    // IMPLICIT DAMAGE
    // =========================================================================

    private final ImplicitDamageConfig implicitDamage;

    // =========================================================================
    // IMPLICIT DEFENSE
    // =========================================================================

    private final ImplicitDefenseConfig implicitDefense;

    // =========================================================================
    // LEVEL BLENDING
    // =========================================================================

    private final LevelBlendingConfig levelBlending;

    // =========================================================================
    // VANILLA WEAPON PROFILES
    // =========================================================================

    private final VanillaWeaponProfilesConfig vanillaWeaponProfiles;

    // =========================================================================
    // CONSTRUCTOR (package-private, use GearConfigLoader)
    // =========================================================================

    GearBalanceConfig(
            double gearPowerRatio,
            Map<String, Double> slotWeights,
            double levelScalingFactor,
            Map<GearRarity, RarityConfig> rarityConfigs,
            QualityConfig quality,
            AttributeRequirementsConfig attributeRequirements,
            ModifierScalingConfig modifierScaling,
            LootConfig loot,
            StoneDropConfig stoneDrops,
            ExponentialScalingConfig exponentialScaling,
            ImplicitDamageConfig implicitDamage,
            ImplicitDefenseConfig implicitDefense,
            VanillaWeaponProfilesConfig vanillaWeaponProfiles,
            LevelBlendingConfig levelBlending
    ) {
        this.gearPowerRatio = gearPowerRatio;
        this.slotWeights = Map.copyOf(slotWeights);
        this.levelScalingFactor = levelScalingFactor;
        this.rarityConfigs = Collections.unmodifiableMap(new EnumMap<>(rarityConfigs));
        this.quality = quality;
        this.attributeRequirements = attributeRequirements;
        this.modifierScaling = modifierScaling;
        this.loot = loot;
        this.stoneDrops = stoneDrops;
        this.exponentialScaling = exponentialScaling;
        this.implicitDamage = implicitDamage;
        this.implicitDefense = implicitDefense;
        this.vanillaWeaponProfiles = vanillaWeaponProfiles;
        this.levelBlending = levelBlending;
    }

    // =========================================================================
    // GETTERS
    // =========================================================================

    /**
     * Returns the gear power ratio.
     *
     * <p>A full Mythic set at item_level = player_level adds this fraction
     * of attribute-based stats. Default: 0.5 (gear adds half as much as attributes).
     */
    public double gearPowerRatio() {
        return gearPowerRatio;
    }

    /**
     * Returns the slot weight map.
     *
     * <p>Keys are slot names (weapon, chest, legs, head, hands, feet, shield).
     * Values are the fraction of total gear power that slot provides.
     * All values should sum to 1.0.
     */
    public Map<String, Double> slotWeights() {
        return slotWeights;
    }

    /**
     * Returns the weight for a specific slot.
     *
     * @param slot case-insensitive
     * @return 0.0 if slot not configured
     */
    public double slotWeight(String slot) {
        return slotWeights.getOrDefault(slot.toLowerCase(), 0.0);
    }

    /**
     * Returns the level scaling factor.
     *
     * <p>Stats grow by this factor per item level.
     * Formula: stat_value = base_value × (1 + item_level × levelScalingFactor)
     */
    public double levelScalingFactor() {
        return levelScalingFactor;
    }

    /**
     * @throws IllegalArgumentException if rarity not found
     */
    public RarityConfig rarityConfig(GearRarity rarity) {
        RarityConfig config = rarityConfigs.get(rarity);
        if (config == null) {
            throw new IllegalArgumentException("No config for rarity: " + rarity);
        }
        return config;
    }

    public Map<GearRarity, RarityConfig> rarityConfigs() {
        return rarityConfigs;
    }

    public QualityConfig quality() {
        return quality;
    }

    public AttributeRequirementsConfig attributeRequirements() {
        return attributeRequirements;
    }

    public ModifierScalingConfig modifierScaling() {
        return modifierScaling;
    }

    public LootConfig loot() {
        return loot;
    }

    public StoneDropConfig stoneDrops() {
        return stoneDrops;
    }

    /**
     * Returns the exponential scaling configuration.
     *
     * <p>Controls how gear stats grow exponentially with item level for
     * dramatic power progression. When disabled, returns DISABLED config
     * which has a calculateMultiplier() that always returns 1.0.
     */
    public ExponentialScalingConfig exponentialScaling() {
        return exponentialScaling;
    }

    /**
     * Returns the implicit damage configuration.
     *
     * <p>Controls base weapon damage that scales with item level.
     * Implicits are quality-independent and have a rollable range.
     */
    public ImplicitDamageConfig implicitDamage() {
        return implicitDamage;
    }

    /**
     * Returns the implicit defense configuration.
     *
     * <p>Controls base armor defense stats that scale with item level.
     * Each armor material type grants a different defense identity.
     */
    public ImplicitDefenseConfig implicitDefense() {
        return implicitDefense;
    }

    /**
     * Returns the level blending configuration.
     *
     * <p>Controls how drop levels are pulled toward the player's level.
     * When disabled, returns DISABLED config which passes through source level unchanged.
     */
    public LevelBlendingConfig levelBlending() {
        return levelBlending;
    }

    /**
     * Returns the vanilla weapon profiles configuration.
     *
     * <p>Controls automatic discovery and use of vanilla weapon attack patterns
     * for attack effectiveness calculation. When enabled, uses geometric mean
     * of each weapon's attack damage range as the reference point.
     */
    public VanillaWeaponProfilesConfig vanillaWeaponProfiles() {
        return vanillaWeaponProfiles;
    }

    // =========================================================================
    // NESTED CONFIGURATION RECORDS
    // =========================================================================

    public record RarityConfig(
            double statMultiplier,
            int maxModifiers,
            int minPrefixes,
            int maxPrefixes,
            int minSuffixes,
            int maxSuffixes,
            double dropWeight,
            double minRollPercentile  // Only used for Mythic
    ) {
        public RarityConfig {
            if (statMultiplier <= 0) {
                throw new IllegalArgumentException("statMultiplier must be positive");
            }
            if (maxModifiers < 1 || maxModifiers > 6) {
                throw new IllegalArgumentException("maxModifiers must be 1-6");
            }
            if (minPrefixes < 0 || maxPrefixes > 3 || minPrefixes > maxPrefixes) {
                throw new IllegalArgumentException("Invalid prefix range");
            }
            if (minSuffixes < 0 || maxSuffixes > 3 || minSuffixes > maxSuffixes) {
                throw new IllegalArgumentException("Invalid suffix range");
            }
            if (dropWeight < 0) {
                throw new IllegalArgumentException("dropWeight cannot be negative");
            }
            if (minRollPercentile < 0 || minRollPercentile > 1) {
                throw new IllegalArgumentException("minRollPercentile must be 0-1");
            }
        }

        /**
         * Convenience constructor for non-Mythic rarities (no min roll percentile).
         */
        public RarityConfig(
                double statMultiplier,
                int maxModifiers,
                int minPrefixes,
                int maxPrefixes,
                int minSuffixes,
                int maxSuffixes,
                double dropWeight
        ) {
            this(statMultiplier, maxModifiers, minPrefixes, maxPrefixes,
                 minSuffixes, maxSuffixes, dropWeight, 0.0);
        }
    }

    public record QualityConfig(
            int baseline,
            int min,
            int max,
            int perfect,
            QualityDropDistribution dropDistribution
    ) {
        public QualityConfig {
            if (baseline <= 0) {
                throw new IllegalArgumentException("baseline must be positive");
            }
            if (min < 1 || min >= max) {
                throw new IllegalArgumentException("Invalid min/max range");
            }
            if (perfect <= max) {
                throw new IllegalArgumentException("perfect must be > max");
            }
            Objects.requireNonNull(dropDistribution, "dropDistribution cannot be null");
        }

        public double multiplier(int quality) {
            return 0.5 + (double) quality / (baseline * 2);
        }
    }

    public record QualityDropDistribution(
            double poor,           // 1-25%
            double belowAverage,   // 26-49%
            double normal,         // Exactly 50%
            double aboveAverage,   // 51-75%
            double excellent,      // 76-100%
            double perfect         // 101%
    ) {
        public QualityDropDistribution {
            double sum = poor + belowAverage + normal + aboveAverage + excellent + perfect;
            if (Math.abs(sum - 1.0) > 0.001) {
                throw new IllegalArgumentException(
                    "Quality distribution must sum to 1.0, got: " + sum);
            }
            if (poor < 0 || belowAverage < 0 || normal < 0 ||
                aboveAverage < 0 || excellent < 0 || perfect < 0) {
                throw new IllegalArgumentException("All probabilities must be non-negative");
            }
        }
    }

    public record AttributeRequirementsConfig(
            double levelToBaseRatio,
            int minItemLevelForRequirements,
            Map<GearRarity, Double> rarityMultipliers
    ) {
        public AttributeRequirementsConfig {
            if (levelToBaseRatio <= 0 || levelToBaseRatio > 1) {
                throw new IllegalArgumentException(
                    "levelToBaseRatio must be in (0, 1], got: " + levelToBaseRatio);
            }
            if (minItemLevelForRequirements < 0) {
                throw new IllegalArgumentException(
                    "minItemLevelForRequirements cannot be negative");
            }
            rarityMultipliers = Collections.unmodifiableMap(new EnumMap<>(rarityMultipliers));

            // Verify all rarities have multipliers
            for (GearRarity rarity : GearRarity.values()) {
                if (!rarityMultipliers.containsKey(rarity)) {
                    throw new IllegalArgumentException(
                        "Missing rarity multiplier for: " + rarity);
                }
                double mult = rarityMultipliers.get(rarity);
                if (mult < 0 || mult > 1) {
                    throw new IllegalArgumentException(
                        "Rarity multiplier must be [0, 1], got " + mult + " for " + rarity);
                }
            }
        }

        public Map<GearRarity, Double> rarityMultipliers() {
            return rarityMultipliers;
        }

        /**
         * Calculates the attribute requirement for given item level and rarity.
         *
         * @return The required attribute value, or 0 if below minimum level
         */
        public int calculateRequirement(int itemLevel, GearRarity rarity) {
            if (itemLevel < minItemLevelForRequirements) {
                return 0;
            }
            double base = itemLevel * levelToBaseRatio;
            double multiplier = rarityMultipliers.get(rarity);
            return (int) Math.floor(base * multiplier);
        }
    }

    public record ModifierScalingConfig(
            double globalScalePerLevel,
            double rollVariance,
            Map<String, Integer> weightCategories
    ) {
        public ModifierScalingConfig {
            if (globalScalePerLevel < 0) {
                throw new IllegalArgumentException("globalScalePerLevel cannot be negative");
            }
            if (rollVariance < 0 || rollVariance > 1) {
                throw new IllegalArgumentException("rollVariance must be [0, 1]");
            }
            weightCategories = Map.copyOf(weightCategories);
        }

        /**
         * Gets the weight for a category name.
         *
         * @return The weight, or 50 (common) as default
         */
        public int categoryWeight(String category) {
            return weightCategories.getOrDefault(category.toLowerCase(), 50);
        }
    }

    public record LootConfig(
            double baseDropChance,
            double luckToRarityPercent,
            DistanceScalingConfig distanceScaling,
            Map<String, MobBonusConfig> mobBonuses
    ) {
        public LootConfig {
            if (baseDropChance < 0 || baseDropChance > 1) {
                throw new IllegalArgumentException("baseDropChance must be between 0 and 1");
            }
            if (luckToRarityPercent < 0) {
                throw new IllegalArgumentException("luckToRarityPercent cannot be negative");
            }
            Objects.requireNonNull(distanceScaling, "distanceScaling cannot be null");
            mobBonuses = Map.copyOf(mobBonuses);
        }

        /**
         * Gets the mob bonus config for a mob type.
         *
         * @return The config, or the "normal" config if not found
         */
        public MobBonusConfig mobBonus(String mobType) {
            return mobBonuses.getOrDefault(mobType.toLowerCase(),
                mobBonuses.getOrDefault("normal", MobBonusConfig.NONE));
        }
    }

    public record DistanceScalingConfig(
            boolean enabled,
            int blocksPerPercent,
            double maxBonus
    ) {
        public DistanceScalingConfig {
            if (blocksPerPercent <= 0) {
                throw new IllegalArgumentException("blocksPerPercent must be positive");
            }
            if (maxBonus < 0) {
                throw new IllegalArgumentException("maxBonus cannot be negative");
            }
        }

        public double calculateBonus(double distanceFromSpawn) {
            if (!enabled) return 0;
            double bonus = distanceFromSpawn / blocksPerPercent;
            return Math.min(bonus, maxBonus);
        }
    }

    public record MobBonusConfig(
            double quantityBonus,
            double rarityBonus
    ) {
        public static final MobBonusConfig NONE = new MobBonusConfig(0, 0);

        public MobBonusConfig {
            if (quantityBonus < -1) {
                throw new IllegalArgumentException(
                    "quantityBonus cannot be less than -1 (would mean negative drops)");
            }
            if (rarityBonus < -1) {
                throw new IllegalArgumentException(
                    "rarityBonus cannot be less than -1");
            }
        }
    }

    public record StoneDropConfig(
            double baseDropChance,
            Map<String, Integer> stoneWeights
    ) {
        public StoneDropConfig {
            if (baseDropChance < 0 || baseDropChance > 1) {
                throw new IllegalArgumentException(
                    "baseDropChance must be [0, 1], got: " + baseDropChance);
            }
            stoneWeights = Map.copyOf(stoneWeights);

            for (Map.Entry<String, Integer> entry : stoneWeights.entrySet()) {
                if (entry.getValue() < 0) {
                    throw new IllegalArgumentException(
                        "Stone weight cannot be negative: " + entry.getKey());
                }
            }
        }

        /**
         * Gets the weight for a stone type.
         *
         * @return The weight, or 0 if not found
         */
        public int stoneWeight(String stoneId) {
            return stoneWeights.getOrDefault(stoneId.toLowerCase(), 0);
        }

        public int totalWeight() {
            return stoneWeights.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    /**
     * Configuration for gear stat exponential scaling.
     *
     * <p><b>NOTE:</b> The fields below (exponent, referenceLevel, referenceMultiplier,
     * softCapLevel, softCapDivisor) are <b>not active</b>. They are remnants from before
     * the scaling system was centralized into {@link LevelScaling}. The
     * {@link #calculateMultiplier(int)} method delegates entirely to
     * {@link LevelScaling#getMultiplier(int)}, which is configured globally via
     * {@code config.yml → scaling} section.
     *
     * <p>Only {@link #enabled} is used. All other fields load and validate but have
     * no effect on gameplay. They are retained for potential future per-system
     * override capability.
     *
     * @param enabled Whether exponential scaling is active (only field that matters)
     * @param exponent NOT ACTIVE — retained for future per-system override
     * @param referenceLevel NOT ACTIVE — retained for future per-system override
     * @param referenceMultiplier NOT ACTIVE — retained for future per-system override
     * @param baseMultiplier NOT ACTIVE — retained for future per-system override
     * @param softCapLevel NOT ACTIVE — retained for future per-system override
     * @param softCapDivisor NOT ACTIVE — retained for future per-system override
     *
     * @see LevelScaling
     */
    public record ExponentialScalingConfig(
            boolean enabled,
            double exponent,
            int referenceLevel,
            double referenceMultiplier,
            double baseMultiplier,
            int softCapLevel,
            double softCapDivisor
    ) {
        /** Default config with exponential disabled (backward compatible). */
        public static final ExponentialScalingConfig DISABLED =
            new ExponentialScalingConfig(false, 2.0, 100, 50.0, 1.0, 100, 2.0);

        public ExponentialScalingConfig {
            if (exponent <= 0) {
                throw new IllegalArgumentException("exponent must be positive, got: " + exponent);
            }
            if (referenceLevel <= 0) {
                throw new IllegalArgumentException("referenceLevel must be positive, got: " + referenceLevel);
            }
            if (referenceMultiplier < baseMultiplier) {
                throw new IllegalArgumentException(
                    "referenceMultiplier must be >= baseMultiplier: " + referenceMultiplier + " < " + baseMultiplier);
            }
            if (baseMultiplier <= 0) {
                throw new IllegalArgumentException("baseMultiplier must be positive, got: " + baseMultiplier);
            }
            if (softCapLevel <= 0) {
                throw new IllegalArgumentException("softCapLevel must be positive, got: " + softCapLevel);
            }
            if (softCapDivisor <= 0) {
                throw new IllegalArgumentException("softCapDivisor must be positive, got: " + softCapDivisor);
            }
        }

        /**
         * Returns the scaling multiplier for a given item level.
         *
         * <p>Delegates to {@link LevelScaling#getMultiplier(int)} — the centralized
         * curve configured in {@code config.yml → scaling}. The per-system fields
         * on this record (exponent, referenceMultiplier, etc.) are not used.
         *
         * @param level The item level
         * @return The scaling multiplier (~1.5× at level 100 with default config)
         */
        public double calculateMultiplier(int level) {
            if (!enabled || level <= 0) {
                return 1.0;
            }
            return LevelScaling.getMultiplier(level);
        }
    }

    /**
     * Configuration for weapon implicit damage.
     *
     * <p>All weapons share a single base damage range at level 1. Growth is
     * driven by {@link LevelScaling#getBonusPercent(int)} with a configurable
     * scale factor. Two-handed weapons get a flat multiplier on top.
     *
     * <p>Formula:
     * <pre>
     * bonus = LevelScaling.getBonusPercent(level) / 100.0 × scaleFactor
     * min = baseMin + bonus
     * max = baseMax + bonus
     * if twoHanded: min *= twoHandedMultiplier, max *= twoHandedMultiplier
     * </pre>
     *
     * @param enabled Whether implicit damage is active for weapon generation
     * @param baseMin Minimum damage at level 1 (one-handed)
     * @param baseMax Maximum damage at level 1 (one-handed)
     * @param scaleFactor How much LevelScaling bonus amplifies implicit damage
     * @param twoHandedMultiplier Multiplier applied to two-handed weapon ranges
     */
    public record ImplicitDamageConfig(
            boolean enabled,
            double baseMin,
            double baseMax,
            double scaleFactor,
            double twoHandedMultiplier
    ) {
        /** Default config with implicit damage disabled. */
        public static final ImplicitDamageConfig DISABLED =
            new ImplicitDamageConfig(false, 1.0, 5.0, 55.0, 2.0);

        /**
         * Calculates the scaled damage range for a weapon at a given level.
         *
         * @param weaponType The weapon type (used to determine 2H multiplier)
         * @param itemLevel The weapon's item level
         * @return The scaled damage range
         */
        public WeaponBaseRange calculateRange(WeaponType weaponType, int itemLevel) {
            double bonus = LevelScaling.getBonusPercent(itemLevel) / 100.0 * scaleFactor;
            double min = baseMin + bonus;
            double max = baseMax + bonus;

            if (weaponType.isTwoHanded()) {
                return new WeaponBaseRange(min * twoHandedMultiplier, max * twoHandedMultiplier);
            }
            return new WeaponBaseRange(min, max);
        }
    }

    /**
     * Configuration for armor implicit defense stats.
     *
     * <p>Each armor material type grants a different defense identity:
     * <ul>
     *   <li>PLATE → armor (physical damage reduction)</li>
     *   <li>LEATHER → evasion (dodge chance)</li>
     *   <li>CLOTH → energy_shield (absorb layer)</li>
     *   <li>WOOD → max_health (vitality/nature)</li>
     *   <li>SPECIAL → armor (default defense)</li>
     * </ul>
     *
     * <p>Shields have a separate config for block_chance.
     *
     * <p>Slot multipliers scale the implicit value by body slot (chest is baseline).
     *
     * @param enabled Whether implicit defense is active for armor generation
     * @param materials Per-material defense config (stat type, base range, scale factor)
     * @param slotMultipliers Per-slot scaling multipliers (chest=1.0, head=0.6, etc.)
     * @param shield Separate config for shield block_chance
     */
    public record ImplicitDefenseConfig(
            boolean enabled,
            Map<ArmorMaterial, MaterialDefenseConfig> materials,
            Map<ArmorSlot, Double> slotMultipliers,
            MaterialDefenseConfig shield
    ) {
        /** Default config with implicit defense disabled. */
        public static final ImplicitDefenseConfig DISABLED =
            new ImplicitDefenseConfig(false, Map.of(), Map.of(), new MaterialDefenseConfig("block_chance", 5.0, 15.0, 30.0));

        /**
         * Gets the defense config for a specific armor material.
         *
         * @return The config, or null if not configured
         */
        public MaterialDefenseConfig materialConfig(ArmorMaterial material) {
            return materials.get(material);
        }

        /**
         * Gets the slot multiplier for a specific armor slot.
         *
         * @return The multiplier, or 1.0 if not configured
         */
        public double slotMultiplier(ArmorSlot slot) {
            return slotMultipliers.getOrDefault(slot, 1.0);
        }

        /**
         * Calculates the scaled defense range for armor at a given level.
         *
         * @param material The armor material
         * @param slot The armor slot
         * @param itemLevel The armor's item level
         * @return The scaled defense range, or null if material not configured
         */
        public DefenseBaseRange calculateRange(ArmorMaterial material, ArmorSlot slot, int itemLevel) {
            MaterialDefenseConfig config = materials.get(material);
            if (config == null) {
                return null;
            }
            double bonus = LevelScaling.getBonusPercent(itemLevel) / 100.0 * config.scaleFactor();
            double slotMult = slotMultiplier(slot);
            double min = (config.baseMin() + bonus) * slotMult;
            double max = (config.baseMax() + bonus) * slotMult;
            return new DefenseBaseRange(config.stat(), min, max);
        }

        /**
         * Calculates the scaled defense range for a shield at a given level.
         *
         * @param itemLevel The shield's item level
         * @return The scaled defense range
         */
        public DefenseBaseRange calculateShieldRange(int itemLevel) {
            double bonus = LevelScaling.getBonusPercent(itemLevel) / 100.0 * shield.scaleFactor();
            double min = shield.baseMin() + bonus;
            double max = shield.baseMax() + bonus;
            return new DefenseBaseRange(shield.stat(), min, max);
        }
    }

    /**
     * Configuration for a single armor material's defense implicit.
     *
     * @param stat The defense stat type (e.g., "armor", "evasion", "energy_shield")
     * @param baseMin Minimum defense at level 1
     * @param baseMax Maximum defense at level 1
     * @param scaleFactor How much LevelScaling bonus amplifies defense
     */
    public record MaterialDefenseConfig(
            String stat,
            double baseMin,
            double baseMax,
            double scaleFactor
    ) {
        public MaterialDefenseConfig {
            Objects.requireNonNull(stat, "stat cannot be null");
            if (baseMin < 0) {
                throw new IllegalArgumentException("baseMin cannot be negative: " + baseMin);
            }
            if (baseMax < baseMin) {
                throw new IllegalArgumentException("baseMax cannot be less than baseMin: " + baseMax + " < " + baseMin);
            }
            if (scaleFactor < 0) {
                throw new IllegalArgumentException("scaleFactor cannot be negative: " + scaleFactor);
            }
        }
    }

    /**
     * Calculated defense range for an armor piece.
     *
     * @param stat The defense stat type
     * @param min Minimum scaled defense value
     * @param max Maximum scaled defense value
     */
    public record DefenseBaseRange(String stat, double min, double max) {
        public DefenseBaseRange {
            Objects.requireNonNull(stat, "stat cannot be null");
            if (min < 0) {
                throw new IllegalArgumentException("min cannot be negative: " + min);
            }
            if (max < min) {
                throw new IllegalArgumentException("max cannot be less than min: " + max + " < " + min);
            }
        }
    }

    /**
     * Base damage range for a weapon type at level 1.
     *
     * @param min Minimum base damage
     * @param max Maximum base damage
     */
    public record WeaponBaseRange(double min, double max) {
        public WeaponBaseRange {
            if (min < 0) {
                throw new IllegalArgumentException("min cannot be negative: " + min);
            }
            if (max < min) {
                throw new IllegalArgumentException("max cannot be less than min: " + max + " < " + min);
            }
        }

        /**
         * Calculates the damage range scaled by a multiplier.
         *
         * @param multiplier The scaling multiplier (from exponential scaling)
         * @return A new range with scaled values
         */
        public WeaponBaseRange scaled(double multiplier) {
            return new WeaponBaseRange(min * multiplier, max * multiplier);
        }
    }

    /**
     * Ordered attack multiplier profile for a weapon family.
     *
     * <p>Non-backstab attacks are sorted by damage and mapped to {@code normalMultipliers}
     * via linear interpolation. Backstab attacks receive a fixed multiplier.
     *
     * @param normalMultipliers Ordered weakest→strongest multipliers for normal attacks
     * @param backstabMultiplier Fixed multiplier for all backstab attacks
     */
    public record FamilyAttackProfile(
            List<Double> normalMultipliers,
            double backstabMultiplier
    ) {
        /** Default profile: 6-point curve + 3.0× backstab. */
        public static final FamilyAttackProfile DEFAULT =
            new FamilyAttackProfile(List.of(0.5, 0.65, 0.8, 1.0, 1.5, 2.0), 3.0);

        public FamilyAttackProfile {
            Objects.requireNonNull(normalMultipliers, "normalMultipliers cannot be null");
            if (normalMultipliers.isEmpty()) {
                throw new IllegalArgumentException("normalMultipliers must not be empty");
            }
            normalMultipliers = List.copyOf(normalMultipliers);
            if (backstabMultiplier <= 0) {
                throw new IllegalArgumentException(
                    "backstabMultiplier must be positive, got: " + backstabMultiplier);
            }
        }
    }

    /**
     * Configuration for vanilla weapon attack profile discovery.
     *
     * <p>At server startup, the plugin discovers all vanilla weapon attacks and
     * assigns effectiveness multipliers from config-defined family profiles.
     * Two-tier lookup: ID-based name first (e.g., "Claws"), then tag-based
     * (e.g., "Dagger"), then default profile.
     */
    public record VanillaWeaponProfilesConfig(
            boolean enabled,
            double fallbackReferenceDamage,
            double minMultiplier,
            double maxMultiplier,
            FamilyAttackProfile defaultProfile,
            Map<String, FamilyAttackProfile> familyProfiles
    ) {
        /** Default config with profiles enabled. */
        public static final VanillaWeaponProfilesConfig DEFAULT =
            new VanillaWeaponProfilesConfig(true, 20.0, 0.3, 3.0,
                FamilyAttackProfile.DEFAULT, Map.of());

        /** Config with profiles disabled (uses legacy config-based calculation). */
        public static final VanillaWeaponProfilesConfig DISABLED =
            new VanillaWeaponProfilesConfig(false, 20.0, 0.3, 3.0,
                FamilyAttackProfile.DEFAULT, Map.of());

        public VanillaWeaponProfilesConfig {
            if (fallbackReferenceDamage <= 0) {
                throw new IllegalArgumentException(
                    "fallbackReferenceDamage must be positive, got: " + fallbackReferenceDamage);
            }
            if (minMultiplier < 0) {
                throw new IllegalArgumentException(
                    "minMultiplier cannot be negative, got: " + minMultiplier);
            }
            if (maxMultiplier < minMultiplier) {
                throw new IllegalArgumentException(
                    "maxMultiplier cannot be less than minMultiplier");
            }
            Objects.requireNonNull(defaultProfile, "defaultProfile cannot be null");
            Objects.requireNonNull(familyProfiles, "familyProfiles cannot be null");
            familyProfiles = Map.copyOf(familyProfiles);
        }

        /** Clamps an attack type multiplier to configured bounds. */
        public float clampMultiplier(float raw) {
            return (float) Math.max(minMultiplier, Math.min(maxMultiplier, raw));
        }

        /** Gets the profile for a family, falling back to defaultProfile. */
        public FamilyAttackProfile getFamilyProfile(String family) {
            return familyProfiles.getOrDefault(family, defaultProfile);
        }
    }

    /**
     * Configuration for player-level blending on drop levels.
     *
     * <p>Pulls drop levels toward the player's level so that gear remains
     * useful regardless of the level gap between player and source:
     * <pre>
     * blendedLevel = sourceLevel + clamp((playerLevel - sourceLevel) × pullFactor, -maxOffset, +maxOffset)
     * finalLevel   = blendedLevel + random(±variance)
     * </pre>
     *
     * @param enabled Whether level blending is active
     * @param pullFactor Fraction of the gap to close (0.0 = no pull, 1.0 = full pull)
     * @param maxOffset Maximum absolute offset from source level due to blending
     * @param variance Random variance applied after blending (±N)
     */
    public record LevelBlendingConfig(
            boolean enabled,
            double pullFactor,
            int maxOffset,
            int variance
    ) {
        /** Default config with blending disabled. */
        public static final LevelBlendingConfig DISABLED =
            new LevelBlendingConfig(false, 0.3, 5, 2);

        public LevelBlendingConfig {
            if (pullFactor < 0 || pullFactor > 1) {
                throw new IllegalArgumentException(
                    "pullFactor must be [0, 1], got: " + pullFactor);
            }
            if (maxOffset < 0) {
                throw new IllegalArgumentException(
                    "maxOffset must be non-negative, got: " + maxOffset);
            }
            if (variance < 0) {
                throw new IllegalArgumentException(
                    "variance must be non-negative, got: " + variance);
            }
        }
    }
}
