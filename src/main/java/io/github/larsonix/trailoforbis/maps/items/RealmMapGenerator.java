package io.github.larsonix.trailoforbis.maps.items;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.maps.config.RealmsConfig;
import io.github.larsonix.trailoforbis.maps.config.RealmModifierConfig;
import io.github.larsonix.trailoforbis.maps.core.RealmBiomeType;
import io.github.larsonix.trailoforbis.maps.core.RealmLayoutShape;
import io.github.larsonix.trailoforbis.maps.core.RealmLayoutSize;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifier;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifierRoller;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifierRoller.RollResult;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates random Realm Maps with procedural properties.
 *
 * <p>This is the main entry point for realm map generation. It creates
 * {@link RealmMapData} with randomized:
 * <ul>
 *   <li>Rarity (weighted roll with optional bonus)</li>
 *   <li>Quality (1-100, with rare 101 "perfect" quality)</li>
 *   <li>Biome (from enabled biomes in config)</li>
 *   <li>Size (weighted selection from enabled sizes)</li>
 *   <li>Shape (random selection)</li>
 *   <li>Modifiers (rolled based on rarity)</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 *
 * <p>This class's thread safety depends on the provided Random instance:
 * <ul>
 *   <li><b>Default constructor</b>: Uses {@link ThreadLocalRandom} and is
 *       fully thread-safe for concurrent generation from multiple threads</li>
 *   <li><b>Custom Random</b>: Thread-safe only if the provided Random is
 *       thread-safe (e.g., {@link ThreadLocalRandom}, or synchronized access)</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * RealmMapGenerator generator = new RealmMapGenerator(realmsConfig, modifierConfig);
 *
 * // Generate a map at level 50 with random rarity
 * RealmMapData map = generator.generate(50);
 *
 * // Generate a map with specific rarity
 * RealmMapData epicMap = generator.generate(100, GearRarity.EPIC);
 *
 * // Generate and apply to an ItemStack
 * ItemStack mapItem = generator.generateItem(baseItem, 50, 0.5);
 * }</pre>
 *
 * @see RealmMapData
 * @see RealmMapUtils
 */
public final class RealmMapGenerator {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // =========================================================================
    // RARITY WEIGHTS (base weights for rolling)
    // =========================================================================

    private static final Map<GearRarity, Double> BASE_RARITY_WEIGHTS;
    static {
        Map<GearRarity, Double> weights = new EnumMap<>(GearRarity.class);
        weights.put(GearRarity.COMMON, 50.0);
        weights.put(GearRarity.UNCOMMON, 30.0);
        weights.put(GearRarity.RARE, 14.0);
        weights.put(GearRarity.EPIC, 5.0);
        weights.put(GearRarity.LEGENDARY, 0.9);
        weights.put(GearRarity.MYTHIC, 0.1);
        BASE_RARITY_WEIGHTS = Collections.unmodifiableMap(weights);
    }

    /** Chance for perfect quality (101) out of 10000 */
    private static final int PERFECT_QUALITY_CHANCE = 1; // 0.01%

    // =========================================================================
    // FIELDS
    // =========================================================================

    private final RealmsConfig realmsConfig;
    private final RealmModifierRoller modifierRoller;
    private final Random random;

    // Cached enabled values for performance
    private final List<RealmBiomeType> enabledBiomes;
    private final List<RealmLayoutSize> enabledSizes;

    // =========================================================================
    // CONSTRUCTORS
    // =========================================================================

    /**
     * Creates a RealmMapGenerator with custom random source.
     *
     * @param realmsConfig The realms configuration
     * @param modifierConfig The modifier configuration
     * @param random The random number generator
     */
    public RealmMapGenerator(
            @Nonnull RealmsConfig realmsConfig,
            @Nonnull RealmModifierConfig modifierConfig,
            @Nonnull Random random) {
        this.realmsConfig = Objects.requireNonNull(realmsConfig, "realmsConfig cannot be null");
        this.modifierRoller = new RealmModifierRoller(
            Objects.requireNonNull(modifierConfig, "modifierConfig cannot be null"));
        this.random = Objects.requireNonNull(random, "random cannot be null");

        // Cache enabled biomes and sizes
        this.enabledBiomes = new ArrayList<>(realmsConfig.getEnabledBiomes());
        this.enabledSizes = new ArrayList<>(realmsConfig.getEnabledSizes());

        // Validate at least one biome and size is enabled
        if (enabledBiomes.isEmpty()) {
            LOGGER.atWarning().log("No biomes enabled in config, using all combat biomes");
            // Only add combat biomes, exclude utility biomes like SKILL_SANCTUM
            for (RealmBiomeType biome : RealmBiomeType.values()) {
                if (!biome.isUtilityBiome()) {
                    this.enabledBiomes.add(biome);
                }
            }
        }
        if (enabledSizes.isEmpty()) {
            LOGGER.atWarning().log("No sizes enabled in config, using all sizes");
            this.enabledSizes.addAll(Arrays.asList(RealmLayoutSize.values()));
        }
    }

    /**
     * Creates a RealmMapGenerator with ThreadLocalRandom for thread safety.
     *
     * <p>This is the recommended constructor for server environments where
     * map generation may occur concurrently from multiple threads.
     *
     * @param realmsConfig The realms configuration
     * @param modifierConfig The modifier configuration
     */
    public RealmMapGenerator(
            @Nonnull RealmsConfig realmsConfig,
            @Nonnull RealmModifierConfig modifierConfig) {
        this(realmsConfig, modifierConfig, ThreadLocalRandom.current());
    }

    // =========================================================================
    // MAIN GENERATION METHODS
    // =========================================================================

    /**
     * Generates a realm map with random rarity.
     *
     * @param level The map level (1-10000)
     * @return Generated RealmMapData
     */
    @Nonnull
    public RealmMapData generate(int level) {
        return generate(level, 0.0);
    }

    /**
     * Generates a realm map with rarity bonus.
     *
     * @param level The map level (1-10000)
     * @param rarityBonus Bonus affecting rarity roll (0.0 = no bonus, 1.0 = 100% bonus)
     * @return Generated RealmMapData
     */
    @Nonnull
    public RealmMapData generate(int level, double rarityBonus) {
        GearRarity rarity = rollRarity(rarityBonus);
        return generate(level, rarity);
    }

    /**
     * Generates a realm map with specific rarity.
     *
     * @param level The map level (1-10000)
     * @param rarity The forced rarity
     * @return Generated RealmMapData
     */
    @Nonnull
    public RealmMapData generate(int level, @Nonnull GearRarity rarity) {
        Objects.requireNonNull(rarity, "rarity cannot be null");

        // Clamp level
        int clampedLevel = Math.max(1, Math.min(level, realmsConfig.getMaxLevel()));

        // Roll other properties
        int quality = rollQuality();
        RealmBiomeType biome = rollBiome(clampedLevel);
        RealmLayoutSize size = rollSize(clampedLevel);
        RealmLayoutShape shape = RealmLayoutShape.random(random);

        // Roll modifiers (split into prefixes and suffixes, scaled by map level)
        RollResult modifiers = modifierRoller.rollModifiersSplit(rarity, random, clampedLevel);

        // Build the map data
        RealmMapData mapData = RealmMapData.builder()
            .level(clampedLevel)
            .rarity(rarity)
            .quality(quality)
            .biome(biome)
            .size(size)
            .shape(shape)
            .prefixes(modifiers.prefixes())
            .suffixes(modifiers.suffixes())
            .corrupted(false)
            .identified(false) // Maps start unidentified
            .build();

        LOGGER.atFine().log("Generated realm map: level=%d, rarity=%s, biome=%s, size=%s, quality=%d, prefixes=%d, suffixes=%d",
            clampedLevel, rarity, biome, size, quality, modifiers.prefixes().size(), modifiers.suffixes().size());

        return mapData;
    }

    /**
     * Generates a realm map with full customization.
     *
     * @param level The map level
     * @param rarity The rarity
     * @param biome The biome (null for random)
     * @param size The size (null for random)
     * @return Generated RealmMapData
     */
    @Nonnull
    public RealmMapData generate(
            int level,
            @Nonnull GearRarity rarity,
            @Nullable RealmBiomeType biome,
            @Nullable RealmLayoutSize size) {

        Objects.requireNonNull(rarity, "rarity cannot be null");

        int clampedLevel = Math.max(1, Math.min(level, realmsConfig.getMaxLevel()));
        int quality = rollQuality();
        RealmBiomeType finalBiome = biome != null ? biome : rollBiome(clampedLevel);
        RealmLayoutSize finalSize = size != null ? size : rollSize(clampedLevel);
        RealmLayoutShape shape = RealmLayoutShape.random(random);

        RollResult modifiers = modifierRoller.rollModifiersSplit(rarity, random, clampedLevel);

        return RealmMapData.builder()
            .level(clampedLevel)
            .rarity(rarity)
            .quality(quality)
            .biome(finalBiome)
            .size(finalSize)
            .shape(shape)
            .prefixes(modifiers.prefixes())
            .suffixes(modifiers.suffixes())
            .corrupted(false)
            .identified(false)
            .build();
    }

    // =========================================================================
    // ITEMSTACK GENERATION
    // =========================================================================

    /**
     * Generates a realm map and applies it to an ItemStack.
     *
     * @param baseItem The base item to apply map data to
     * @param level The map level
     * @return New ItemStack with map data applied
     */
    @Nonnull
    public ItemStack generateItem(@Nonnull ItemStack baseItem, int level) {
        return generateItem(baseItem, level, 0.0);
    }

    /**
     * Generates a realm map with rarity bonus and applies it to an ItemStack.
     *
     * @param baseItem The base item to apply map data to
     * @param level The map level
     * @param rarityBonus Bonus affecting rarity roll
     * @return New ItemStack with map data applied
     */
    @Nonnull
    public ItemStack generateItem(@Nonnull ItemStack baseItem, int level, double rarityBonus) {
        Objects.requireNonNull(baseItem, "baseItem cannot be null");

        RealmMapData mapData = generate(level, rarityBonus);
        return RealmMapUtils.writeMapData(baseItem, mapData);
    }

    /**
     * Generates a realm map with specific rarity and applies it to an ItemStack.
     *
     * @param baseItem The base item to apply map data to
     * @param level The map level
     * @param rarity The forced rarity
     * @return New ItemStack with map data applied
     */
    @Nonnull
    public ItemStack generateItem(@Nonnull ItemStack baseItem, int level, @Nonnull GearRarity rarity) {
        Objects.requireNonNull(baseItem, "baseItem cannot be null");
        Objects.requireNonNull(rarity, "rarity cannot be null");

        RealmMapData mapData = generate(level, rarity);
        return RealmMapUtils.writeMapData(baseItem, mapData);
    }

    // =========================================================================
    // ROLLING METHODS
    // =========================================================================

    /**
     * Rolls a rarity using weighted selection.
     *
     * @param rarityBonus Bonus affecting the roll (shifts toward rarer)
     * @return The rolled rarity
     */
    @Nonnull
    public GearRarity rollRarity(double rarityBonus) {
        // Clamp bonus
        rarityBonus = Math.max(0, Math.min(10, rarityBonus));

        // Calculate adjusted weights
        Map<GearRarity, Double> adjustedWeights = calculateAdjustedWeights(rarityBonus);

        // Calculate total weight
        double totalWeight = adjustedWeights.values().stream()
            .mapToDouble(Double::doubleValue)
            .sum();

        // Roll
        double roll = random.nextDouble() * totalWeight;
        double cumulative = 0;

        for (GearRarity rarity : GearRarity.values()) {
            cumulative += adjustedWeights.getOrDefault(rarity, 0.0);
            if (roll < cumulative) {
                return rarity;
            }
        }

        // Fallback (shouldn't happen)
        return GearRarity.COMMON;
    }

    /**
     * Calculates adjusted weights based on rarity bonus.
     *
     * <p>The bonus reduces weight of common items and increases
     * weight of rare items proportionally.
     */
    private Map<GearRarity, Double> calculateAdjustedWeights(double bonus) {
        Map<GearRarity, Double> adjusted = new EnumMap<>(GearRarity.class);
        GearRarity[] rarities = GearRarity.values();

        for (int i = 0; i < rarities.length; i++) {
            GearRarity rarity = rarities[i];
            double baseWeight = BASE_RARITY_WEIGHTS.getOrDefault(rarity, 1.0);

            // Position factor: 0 for COMMON, 1 for MYTHIC
            double positionFactor = (double) i / (rarities.length - 1);

            // Bonus effect: reduces common, increases rare
            // At bonus=1.0: COMMON weight halved, MYTHIC doubled
            double adjustment = 1.0 + (positionFactor * 2 - 1) * bonus;
            adjustment = Math.max(0.1, adjustment); // Ensure positive weight

            adjusted.put(rarity, baseWeight * adjustment);
        }

        return adjusted;
    }

    /**
     * Rolls quality value (1-101).
     *
     * <p>Quality 101 is "perfect" quality with very low chance.
     *
     * @return Quality value
     */
    public int rollQuality() {
        // Check for perfect quality (0.01% chance)
        if (random.nextInt(10000) < PERFECT_QUALITY_CHANCE) {
            return 101;
        }

        // Normal quality: 1-100 with slight bias toward middle values
        // Using triangular-ish distribution
        int roll1 = random.nextInt(100) + 1;
        int roll2 = random.nextInt(100) + 1;

        // Average of two rolls creates bell curve
        return (roll1 + roll2) / 2;
    }

    /**
     * Rolls a biome from enabled biomes.
     *
     * <p>Respects minimum level requirements from biome settings.
     *
     * @param level The map level (for minimum level filtering)
     * @return The rolled biome
     */
    @Nonnull
    public RealmBiomeType rollBiome(int level) {
        // Filter biomes by minimum level
        List<RealmBiomeType> eligible = new ArrayList<>();
        for (RealmBiomeType biome : enabledBiomes) {
            int minLevel = realmsConfig.getBiomeSettings(biome).getMinLevel();
            if (level >= minLevel) {
                eligible.add(biome);
            }
        }

        // If no biomes are eligible, use all enabled biomes
        if (eligible.isEmpty()) {
            eligible = enabledBiomes;
        }

        // Random selection from eligible
        return eligible.get(random.nextInt(eligible.size()));
    }

    /**
     * Rolls a size from enabled sizes with level-gated weighted probability.
     *
     * <p>Each size has a minimum level requirement. Below that level, the size has zero weight.
     * Above it, weight ramps up from 10% to 100% of base weight over {@code rampLevels} levels.
     *
     * @param level The player level for filtering eligible sizes
     * @return The rolled size
     */
    @Nonnull
    public RealmLayoutSize rollSize(int level) {
        RealmsConfig.SizeScalingConfig scaling = realmsConfig.getSizeScalingConfig();

        double totalWeight = 0;
        double[] weights = new double[enabledSizes.size()];

        for (int i = 0; i < enabledSizes.size(); i++) {
            weights[i] = scaling.calculateWeight(enabledSizes.get(i), level);
            totalWeight += weights[i];
        }

        // Fallback: if nothing is eligible, return SMALL
        if (totalWeight <= 0) {
            return RealmLayoutSize.SMALL;
        }

        // Weighted random selection
        double roll = random.nextDouble() * totalWeight;
        double cumulative = 0;
        for (int i = 0; i < enabledSizes.size(); i++) {
            cumulative += weights[i];
            if (roll < cumulative) {
                return enabledSizes.get(i);
            }
        }

        return enabledSizes.get(enabledSizes.size() - 1);
    }

    // =========================================================================
    // BUILDER
    // =========================================================================

    /**
     * Creates a builder for customized map generation.
     *
     * @return A new generation builder
     */
    @Nonnull
    public GenerationBuilder builder() {
        return new GenerationBuilder(this);
    }

    /**
     * Builder for customized realm map generation.
     *
     * <p>Allows fine-grained control over generated properties.
     */
    public static final class GenerationBuilder {
        private final RealmMapGenerator generator;

        private Integer level;
        private GearRarity rarity;
        private Integer quality;
        private RealmBiomeType biome;
        private RealmLayoutSize size;
        private RealmLayoutShape shape;
        private List<RealmModifier> prefixes;
        private List<RealmModifier> suffixes;
        private Double rarityBonus;
        private Boolean identified;
        private Boolean corrupted;

        GenerationBuilder(RealmMapGenerator generator) {
            this.generator = generator;
        }

        /**
         * Sets the map level (required).
         */
        @Nonnull
        public GenerationBuilder level(int level) {
            this.level = level;
            return this;
        }

        /**
         * Sets the rarity (overrides rarity bonus).
         */
        @Nonnull
        public GenerationBuilder rarity(@Nonnull GearRarity rarity) {
            this.rarity = rarity;
            return this;
        }

        /**
         * Sets the rarity bonus (ignored if rarity is set).
         */
        @Nonnull
        public GenerationBuilder rarityBonus(double bonus) {
            this.rarityBonus = bonus;
            return this;
        }

        /**
         * Sets the quality (1-101).
         */
        @Nonnull
        public GenerationBuilder quality(int quality) {
            this.quality = quality;
            return this;
        }

        /**
         * Sets the biome.
         */
        @Nonnull
        public GenerationBuilder biome(@Nonnull RealmBiomeType biome) {
            this.biome = biome;
            return this;
        }

        /**
         * Sets the size.
         */
        @Nonnull
        public GenerationBuilder size(@Nonnull RealmLayoutSize size) {
            this.size = size;
            return this;
        }

        /**
         * Sets the shape.
         */
        @Nonnull
        public GenerationBuilder shape(@Nonnull RealmLayoutShape shape) {
            this.shape = shape;
            return this;
        }

        /**
         * Forces specific prefix modifiers (bypasses rolling).
         */
        @Nonnull
        public GenerationBuilder prefixes(@Nonnull List<RealmModifier> prefixes) {
            this.prefixes = new ArrayList<>(prefixes);
            return this;
        }

        /**
         * Forces specific suffix modifiers (bypasses rolling).
         */
        @Nonnull
        public GenerationBuilder suffixes(@Nonnull List<RealmModifier> suffixes) {
            this.suffixes = new ArrayList<>(suffixes);
            return this;
        }

        /**
         * Forces specific modifiers (bypasses rolling), auto-splitting into prefixes/suffixes.
         *
         * @deprecated Use {@link #prefixes(List)} and {@link #suffixes(List)} instead
         */
        @Deprecated
        @Nonnull
        public GenerationBuilder modifiers(@Nonnull List<RealmModifier> modifiers) {
            this.prefixes = new ArrayList<>();
            this.suffixes = new ArrayList<>();
            for (RealmModifier mod : modifiers) {
                if (mod.isPrefix()) {
                    this.prefixes.add(mod);
                } else {
                    this.suffixes.add(mod);
                }
            }
            return this;
        }

        /**
         * Sets whether the map is identified.
         */
        @Nonnull
        public GenerationBuilder identified(boolean identified) {
            this.identified = identified;
            return this;
        }

        /**
         * Sets whether the map is corrupted.
         */
        @Nonnull
        public GenerationBuilder corrupted(boolean corrupted) {
            this.corrupted = corrupted;
            return this;
        }

        /**
         * Builds the RealmMapData.
         *
         * @return The generated map data
         * @throws IllegalStateException if level is not set
         */
        @Nonnull
        public RealmMapData build() {
            if (level == null) {
                throw new IllegalStateException("level is required");
            }

            int clampedLevel = Math.max(1, Math.min(level, generator.realmsConfig.getMaxLevel()));

            // Determine rarity
            GearRarity finalRarity = rarity != null
                ? rarity
                : generator.rollRarity(rarityBonus != null ? rarityBonus : 0.0);

            // Determine other properties
            int finalQuality = quality != null ? quality : generator.rollQuality();
            RealmBiomeType finalBiome = biome != null ? biome : generator.rollBiome(clampedLevel);
            RealmLayoutSize finalSize = size != null ? size : generator.rollSize(clampedLevel);
            RealmLayoutShape finalShape = shape != null ? shape : RealmLayoutShape.random(generator.random);

            // Determine modifiers
            List<RealmModifier> finalPrefixes;
            List<RealmModifier> finalSuffixes;

            if (prefixes != null || suffixes != null) {
                finalPrefixes = prefixes != null ? prefixes : List.of();
                finalSuffixes = suffixes != null ? suffixes : List.of();
            } else {
                RollResult rolled = generator.modifierRoller.rollModifiersSplit(finalRarity, generator.random, clampedLevel);
                finalPrefixes = rolled.prefixes();
                finalSuffixes = rolled.suffixes();
            }

            return RealmMapData.builder()
                .level(clampedLevel)
                .rarity(finalRarity)
                .quality(finalQuality)
                .biome(finalBiome)
                .size(finalSize)
                .shape(finalShape)
                .prefixes(finalPrefixes)
                .suffixes(finalSuffixes)
                .corrupted(corrupted != null ? corrupted : false)
                .identified(identified != null ? identified : false)
                .build();
        }

        /**
         * Builds and applies to an ItemStack.
         *
         * @param baseItem The base item
         * @return ItemStack with map data applied
         */
        @Nonnull
        public ItemStack build(@Nonnull ItemStack baseItem) {
            Objects.requireNonNull(baseItem, "baseItem cannot be null");
            return RealmMapUtils.writeMapData(baseItem, build());
        }
    }

    // =========================================================================
    // ACCESSORS
    // =========================================================================

    /**
     * Gets the realms configuration.
     */
    @Nonnull
    public RealmsConfig getRealmsConfig() {
        return realmsConfig;
    }

    /**
     * Gets the modifier roller.
     */
    @Nonnull
    public RealmModifierRoller getModifierRoller() {
        return modifierRoller;
    }

    /**
     * Gets the list of enabled biomes.
     */
    @Nonnull
    public List<RealmBiomeType> getEnabledBiomes() {
        return Collections.unmodifiableList(enabledBiomes);
    }

    /**
     * Gets the list of enabled sizes.
     */
    @Nonnull
    public List<RealmLayoutSize> getEnabledSizes() {
        return Collections.unmodifiableList(enabledSizes);
    }
}
