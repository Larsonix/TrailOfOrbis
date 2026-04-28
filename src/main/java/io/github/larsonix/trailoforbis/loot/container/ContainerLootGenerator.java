package io.github.larsonix.trailoforbis.loot.container;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import io.github.larsonix.trailoforbis.gear.generation.GearGenerator;
import io.github.larsonix.trailoforbis.gear.generation.RarityRoller;
import io.github.larsonix.trailoforbis.gear.loot.DropLevelBlender;
import io.github.larsonix.trailoforbis.gear.loot.DynamicLootRegistry;
import io.github.larsonix.trailoforbis.gear.loot.LootGenerator;
import io.github.larsonix.trailoforbis.gear.loot.RarityBonusCalculator;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.maps.items.RealmMapGenerator;
import io.github.larsonix.trailoforbis.stones.StoneType;
import io.github.larsonix.trailoforbis.stones.StoneUtils;

import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.maps.RealmsManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates RPG loot items for container replacement.
 *
 * <p>Produces:
 * <ul>
 *   <li><b>Gear</b>: Weapons and armor via {@link LootGenerator}</li>
 *   <li><b>Stones</b>: Currency stones via {@link StoneUtils}</li>
 *   <li><b>Maps</b>: Realm maps via {@link RealmMapGenerator}</li>
 * </ul>
 *
 * <h2>Rarity System</h2>
 * <p>Uses the unified {@link GearRarity} system for consistent drop weighting.
 * Rarity is influenced by:
 * <ul>
 *   <li>Player level (higher level = better rarity chances)</li>
 *   <li>Container tier (boss chests have rarity bonus)</li>
 *   <li>Config-driven weights per player level range</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is thread-safe when using the default constructor
 * (uses ThreadLocalRandom internally).
 *
 * @see ContainerLootReplacer
 * @see ContainerLootConfig
 */
public final class ContainerLootGenerator {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Default base item ID for realm maps */
    private static final String DEFAULT_MAP_BASE_ITEM = "Misc_Scroll";

    private final ContainerLootConfig config;
    private final LootGenerator lootGenerator;
    @Nullable
    private final RealmMapGenerator mapGenerator;
    private final ContainerTierClassifier tierClassifier;
    private final DropLevelBlender dropLevelBlender;
    @Nullable
    private final RarityBonusCalculator rarityBonusCalculator;
    private final Random random;

    /**
     * Creates a new container loot generator.
     *
     * @param config                  The container loot configuration
     * @param lootGenerator           The gear loot generator
     * @param mapGenerator            The realm map generator (nullable - map drops disabled if null)
     * @param tierClassifier          The container tier classifier
     * @param dropLevelBlender        The drop level blender
     * @param rarityBonusCalculator   Player WIND→rarity calculator (nullable for tests)
     */
    public ContainerLootGenerator(
            @Nonnull ContainerLootConfig config,
            @Nonnull LootGenerator lootGenerator,
            @Nullable RealmMapGenerator mapGenerator,
            @Nonnull ContainerTierClassifier tierClassifier,
            @Nonnull DropLevelBlender dropLevelBlender,
            @Nullable RarityBonusCalculator rarityBonusCalculator) {
        this(config, lootGenerator, mapGenerator, tierClassifier, dropLevelBlender, rarityBonusCalculator, ThreadLocalRandom.current());
    }

    /**
     * Creates a new container loot generator with custom random.
     *
     * @param config                  The container loot configuration
     * @param lootGenerator           The gear loot generator
     * @param mapGenerator            The realm map generator (nullable - map drops disabled if null)
     * @param tierClassifier          The container tier classifier
     * @param dropLevelBlender        The drop level blender
     * @param rarityBonusCalculator   Player WIND→rarity calculator (nullable for tests)
     * @param random                  The random number generator
     */
    public ContainerLootGenerator(
            @Nonnull ContainerLootConfig config,
            @Nonnull LootGenerator lootGenerator,
            @Nullable RealmMapGenerator mapGenerator,
            @Nonnull ContainerTierClassifier tierClassifier,
            @Nonnull DropLevelBlender dropLevelBlender,
            @Nullable RarityBonusCalculator rarityBonusCalculator,
            @Nonnull Random random) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.lootGenerator = Objects.requireNonNull(lootGenerator, "lootGenerator cannot be null");
        this.mapGenerator = mapGenerator;  // Nullable - map drops disabled if RealmsManager not available
        this.tierClassifier = Objects.requireNonNull(tierClassifier, "tierClassifier cannot be null");
        this.dropLevelBlender = Objects.requireNonNull(dropLevelBlender, "dropLevelBlender cannot be null");
        this.rarityBonusCalculator = rarityBonusCalculator;  // Nullable for tests
        this.random = Objects.requireNonNull(random, "random cannot be null");
    }

    // =========================================================================
    // MAIN GENERATION
    // =========================================================================

    /**
     * Generates all loot items for a container.
     *
     * @param playerLevel The opening player's level
     * @param tier        The container tier
     * @param playerId    The opening player's UUID (for rarity bonus calculation)
     * @return List of generated items (gear, stones, maps)
     */
    @Nonnull
    public List<ItemStack> generateLoot(int playerLevel, @Nonnull ContainerTier tier, @Nonnull UUID playerId) {
        List<ItemStack> loot = new ArrayList<>();

        // Calculate player's WIND-based rarity bonus once for all sub-generators
        double playerRarityBonus = (rarityBonusCalculator != null)
            ? rarityBonusCalculator.calculatePlayerBonus(playerId) : 0.0;

        // Generate gear
        List<ItemStack> gear = generateGear(playerLevel, tier, playerRarityBonus);
        loot.addAll(gear);

        // Generate stones
        List<ItemStack> stones = generateStones(playerLevel, tier, playerRarityBonus);
        loot.addAll(stones);

        // Generate maps
        List<ItemStack> maps = generateMaps(playerLevel, tier, playerRarityBonus);
        loot.addAll(maps);

        if (config.getAdvanced().isDebugLogging()) {
            LOGGER.atInfo().log("Generated loot for tier %s (playerBonus=%.1f%%): %d gear, %d stones, %d maps",
                tier, playerRarityBonus, gear.size(), stones.size(), maps.size());
        }

        return loot;
    }

    // =========================================================================
    // TARGET-COUNT GENERATION (Total Replacement Mode)
    // =========================================================================

    /**
     * Generates RPG loot to fill a specific number of slots.
     *
     * <p>Unlike {@link #generateLoot}, which generates each type independently,
     * this method works from a total slot budget:
     * <ol>
     *   <li>Roll stones — each hit claims a slot from the budget</li>
     *   <li>Roll maps — each hit claims a slot from the budget</li>
     *   <li>Fill ALL remaining slots with gear</li>
     * </ol>
     *
     * <p>This ensures the container is fully populated with RPG items and
     * the total count is predictable.
     *
     * @param playerLevel The opening player's level
     * @param tier        The container tier
     * @param playerId    The opening player's UUID (for rarity bonus)
     * @param targetSlots Total number of items to generate (already capped at container capacity)
     * @return List of generated items (gear, stones, maps), size &lt;= targetSlots
     */
    @Nonnull
    public List<ItemStack> generateLootForSlots(int playerLevel, @Nonnull ContainerTier tier,
                                                 @Nonnull UUID playerId, int targetSlots) {
        if (targetSlots <= 0) {
            return List.of();
        }

        double playerRarityBonus = (rarityBonusCalculator != null)
            ? rarityBonusCalculator.calculatePlayerBonus(playerId) : 0.0;

        List<ItemStack> loot = new ArrayList<>(targetSlots);
        int slotsRemaining = targetSlots;

        // Phase 1: Roll stones (claim slots from budget)
        List<ItemStack> stones = generateStones(playerLevel, tier, playerRarityBonus);
        for (ItemStack stone : stones) {
            if (slotsRemaining <= 1) break; // Reserve at least 1 slot for gear
            loot.add(stone);
            slotsRemaining--;
        }

        // Phase 2: Roll maps (claim slots from budget)
        List<ItemStack> maps = generateMaps(playerLevel, tier, playerRarityBonus);
        for (ItemStack map : maps) {
            if (slotsRemaining <= 1) break; // Reserve at least 1 slot for gear
            loot.add(map);
            slotsRemaining--;
        }

        // Phase 3: Fill ALL remaining slots with gear
        double rarityBonus = tierClassifier.getRarityBonus(tier) + playerRarityBonus;
        boolean guaranteeRare = tierClassifier.isGuaranteedRareOrBetter(tier);

        for (int i = 0; i < slotsRemaining; i++) {
            int gearLevel;
            if (dropLevelBlender.getConfig().enabled()) {
                gearLevel = dropLevelBlender.calculate(playerLevel, playerLevel, random);
            } else {
                int[] gearLevelRange = config.getLootScaling().getGearLevelRange(playerLevel);
                int minLevel = gearLevelRange[0];
                int maxLevel = gearLevelRange[1];
                gearLevel = minLevel == maxLevel ? minLevel : minLevel + random.nextInt(maxLevel - minLevel + 1);
                gearLevel = Math.max(1, gearLevel);
            }

            GearRarity rarity = rollGearRarity(playerLevel, rarityBonus, guaranteeRare && i == 0);
            ItemStack gearItem = generateSingleGear(gearLevel, rarity, rarityBonus);
            if (gearItem != null) {
                loot.add(gearItem);
            }
        }

        if (config.getAdvanced().isDebugLogging()) {
            int gearCount = loot.size() - stones.size() - maps.size();
            LOGGER.atInfo().log("Generated %d items for %d target slots (tier %s): %d gear, %d stones, %d maps",
                loot.size(), targetSlots, tier, gearCount,
                Math.min(stones.size(), targetSlots), Math.min(maps.size(), targetSlots));
        }

        return loot;
    }

    // =========================================================================
    // GEAR GENERATION
    // =========================================================================

    /**
     * Generates gear items for a container.
     *
     * @param playerLevel      The opening player's level
     * @param tier             The container tier
     * @param playerRarityBonus Player's WIND-based rarity bonus
     * @return List of generated gear items
     */
    @Nonnull
    private List<ItemStack> generateGear(int playerLevel, @Nonnull ContainerTier tier, double playerRarityBonus) {
        ContainerLootConfig.TierConfig tierConfig = tierClassifier.getTierConfig(tier);
        int[] gearDropRange = tierClassifier.getGearDropRange(tier);
        int minDrops = gearDropRange[0];
        int maxDrops = gearDropRange[1];

        // Roll number of drops
        int dropCount = minDrops == maxDrops ? minDrops : minDrops + random.nextInt(maxDrops - minDrops + 1);
        if (dropCount <= 0) {
            return List.of();
        }

        List<ItemStack> gear = new ArrayList<>(dropCount);

        double rarityBonus = tierClassifier.getRarityBonus(tier) + playerRarityBonus;
        boolean guaranteeRare = tierClassifier.isGuaranteedRareOrBetter(tier);

        for (int i = 0; i < dropCount; i++) {
            int gearLevel;
            if (dropLevelBlender.getConfig().enabled()) {
                // Source = player level (gap=0), blender adds ±variance
                gearLevel = dropLevelBlender.calculate(playerLevel, playerLevel, random);
            } else {
                // Original bracket-based range
                int[] gearLevelRange = config.getLootScaling().getGearLevelRange(playerLevel);
                int minLevel = gearLevelRange[0];
                int maxLevel = gearLevelRange[1];
                gearLevel = minLevel == maxLevel ? minLevel : minLevel + random.nextInt(maxLevel - minLevel + 1);
                gearLevel = Math.max(1, gearLevel);
            }

            // Roll rarity
            GearRarity rarity = rollGearRarity(playerLevel, rarityBonus, guaranteeRare && i == 0);

            // Generate gear item
            ItemStack gearItem = generateSingleGear(gearLevel, rarity, rarityBonus);
            if (gearItem != null) {
                gear.add(gearItem);
            }
        }

        return gear;
    }

    /**
     * Generates a single gear item.
     *
     * @param gearLevel   The gear level
     * @param rarity      The target rarity
     * @param rarityBonus The rarity bonus (for fallback)
     * @return Generated gear, or null on failure
     */
    @Nullable
    private ItemStack generateSingleGear(int gearLevel, @Nonnull GearRarity rarity, double rarityBonus) {
        try {
            return lootGenerator.drop()
                .level(gearLevel)
                .rarity(rarity)
                .build();
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to generate gear at level %d", gearLevel);
            return null;
        }
    }

    /**
     * Rolls gear rarity based on player level and bonuses.
     *
     * @param playerLevel    The player's level
     * @param rarityBonus    Bonus from container tier
     * @param guaranteeRare  Force at least rare quality
     * @return The rolled rarity
     */
    @Nonnull
    private GearRarity rollGearRarity(int playerLevel, double rarityBonus, boolean guaranteeRare) {
        Map<String, Integer> weights = config.getLootScaling().getRarityWeights(playerLevel);
        GearRarity rarity = rollRarityFromWeights(weights, rarityBonus);

        // Guarantee rare or better for first drop in boss containers
        if (guaranteeRare && rarity.ordinal() < GearRarity.RARE.ordinal()) {
            rarity = GearRarity.RARE;
        }

        return rarity;
    }

    // =========================================================================
    // STONE GENERATION
    // =========================================================================

    /**
     * Generates stone items for a container.
     *
     * @param playerLevel       The opening player's level
     * @param tier              The container tier
     * @param playerRarityBonus Player's WIND-based rarity bonus
     * @return List of generated stone items
     */
    @Nonnull
    private List<ItemStack> generateStones(int playerLevel, @Nonnull ContainerTier tier, double playerRarityBonus) {
        ContainerLootConfig.StoneDrops stoneConfig = config.getStoneDrops();

        if (!stoneConfig.isEnabled()) {
            return List.of();
        }

        double baseChance = stoneConfig.getBaseChance();
        double tierMultiplier = tierClassifier.getStoneChanceMultiplier(tier);
        double effectiveChance = baseChance * tierMultiplier;

        List<ItemStack> stones = new ArrayList<>();
        int maxStones = stoneConfig.getMaxPerContainer();

        for (int i = 0; i < maxStones; i++) {
            if (random.nextDouble() < effectiveChance) {
                ItemStack stone = generateSingleStone(playerRarityBonus);
                if (stone != null) {
                    stones.add(stone);
                }
            }
        }

        return stones;
    }

    /**
     * Generates a single stone item using native Hytale item IDs.
     *
     * @param playerRarityBonus Player's WIND-based rarity bonus
     * @return Generated stone, or null on failure
     */
    @Nullable
    private ItemStack generateSingleStone(double playerRarityBonus) {
        try {
            // Roll rarity with player bonus
            GearRarity rarity = rollStoneRarity(playerRarityBonus);

            // Get available stones for this rarity
            List<StoneType> availableStones = StoneType.getByRarity(rarity);
            if (availableStones.isEmpty()) {
                // Fall back to any rarity with stones
                for (GearRarity fallback : GearRarity.values()) {
                    availableStones = StoneType.getByRarity(fallback);
                    if (!availableStones.isEmpty()) {
                        break;
                    }
                }
            }

            if (availableStones.isEmpty()) {
                return null;
            }

            // Pick random stone type
            StoneType stoneType = availableStones.get(random.nextInt(availableStones.size()));

            // Create native stone item
            return StoneUtils.createStoneItem(stoneType);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to generate stone");
            return null;
        }
    }

    /**
     * Rolls stone rarity based on config weights and player bonus.
     *
     * @param playerRarityBonus Player's WIND-based rarity bonus
     * @return The rolled rarity
     */
    @Nonnull
    private GearRarity rollStoneRarity(double playerRarityBonus) {
        Map<String, Integer> weights = config.getStoneDrops().getRarityWeights();
        return rollRarityFromWeights(weights, playerRarityBonus);
    }

    // =========================================================================
    // MAP GENERATION
    // =========================================================================

    /**
     * Generates map items for a container.
     *
     * <p>Uses lazy resolution for the map generator - if not provided at construction,
     * attempts to get it from RealmsManager at generation time. This resolves the
     * circular dependency between GearManager and RealmsManager.
     *
     * @param playerLevel       The opening player's level
     * @param tier              The container tier
     * @param playerRarityBonus Player's WIND-based rarity bonus
     * @return List of generated map items
     */
    @Nonnull
    private List<ItemStack> generateMaps(int playerLevel, @Nonnull ContainerTier tier, double playerRarityBonus) {
        // Lazily resolve mapGenerator from RealmsManager if not provided at construction
        RealmMapGenerator generator = resolveMapGenerator();
        if (generator == null) {
            return List.of();
        }

        ContainerLootConfig.MapDrops mapConfig = config.getMapDrops();

        if (!mapConfig.isEnabled()) {
            return List.of();
        }

        double baseChance = mapConfig.getBaseChance();
        double tierMultiplier = tierClassifier.getMapChanceMultiplier(tier);
        double effectiveChance = baseChance * tierMultiplier;

        List<ItemStack> maps = new ArrayList<>();
        int maxMaps = mapConfig.getMaxPerContainer();

        for (int i = 0; i < maxMaps; i++) {
            if (random.nextDouble() < effectiveChance) {
                ItemStack map = generateSingleMap(playerLevel, generator, playerRarityBonus);
                if (map != null) {
                    maps.add(map);
                }
            }
        }

        return maps;
    }

    /**
     * Generates a single map item.
     *
     * @param playerLevel       The player's level
     * @param generator         The map generator to use
     * @param playerRarityBonus Player's WIND-based rarity bonus
     * @return Generated map, or null on failure
     */
    @Nullable
    private ItemStack generateSingleMap(int playerLevel, @Nonnull RealmMapGenerator generator, double playerRarityBonus) {
        try {
            int mapLevel;
            if (dropLevelBlender.getConfig().enabled()) {
                // Source = player level (gap=0), blender adds ±variance
                mapLevel = dropLevelBlender.calculate(playerLevel, playerLevel, random);
            } else {
                // Original offset-based calculation
                ContainerLootConfig.MapDrops mapConfig = config.getMapDrops();
                int minOffset = mapConfig.getLevelOffsetMin();
                int maxOffset = mapConfig.getLevelOffsetMax();
                int offset = minOffset == maxOffset ? minOffset : minOffset + random.nextInt(maxOffset - minOffset + 1);
                mapLevel = Math.max(1, playerLevel + offset);
            }

            // Create base item and generate map with player rarity bonus
            ItemStack baseItem = new ItemStack(DEFAULT_MAP_BASE_ITEM, 1);
            return generator.generateItem(baseItem, mapLevel, playerRarityBonus);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to generate map for level %d", playerLevel);
            return null;
        }
    }

    // =========================================================================
    // UTILITY
    // =========================================================================

    /**
     * Resolves the map generator, using lazy lookup from RealmsManager if needed.
     *
     * <p>This method resolves the circular dependency between GearManager and
     * RealmsManager. At construction time, RealmsManager may not be initialized
     * yet, so we defer the lookup to generation time.
     *
     * @return The map generator, or null if unavailable
     */
    @Nullable
    private RealmMapGenerator resolveMapGenerator() {
        // Use injected generator if available
        if (mapGenerator != null) {
            return mapGenerator;
        }

        // Lazy lookup from RealmsManager (resolves circular dependency)
        try {
            RealmsManager realmsManager = TrailOfOrbis.getInstance().getRealmsManager();
            if (realmsManager != null) {
                return realmsManager.getMapGenerator();
            }
        } catch (Exception e) {
            // RealmsManager not yet available or error - map drops disabled
            if (config.getAdvanced().isDebugLogging()) {
                LOGGER.atFine().log("RealmsManager not available for map generation");
            }
        }

        return null;
    }

    /**
     * Rolls a rarity from weighted map.
     *
     * @param weights     Map of rarity name to weight
     * @param rarityBonus Bonus to shift toward higher rarities
     * @return The rolled rarity
     */
    @Nonnull
    private GearRarity rollRarityFromWeights(@Nonnull Map<String, Integer> weights, double rarityBonus) {
        // Calculate total weight with bonus adjustment
        GearRarity[] rarities = GearRarity.values();
        double[] adjustedWeights = new double[rarities.length];
        double totalWeight = 0;

        for (int i = 0; i < rarities.length; i++) {
            GearRarity rarity = rarities[i];
            String key = rarity.name().toLowerCase();
            int baseWeight = weights.getOrDefault(key, 0);

            // Apply rarity bonus (higher rarities get boosted)
            double positionFactor = (double) i / (rarities.length - 1);
            double adjustment = 1.0 + (positionFactor * 2 - 1) * rarityBonus;
            adjustment = Math.max(0.1, adjustment);

            adjustedWeights[i] = baseWeight * adjustment;
            totalWeight += adjustedWeights[i];
        }

        if (totalWeight <= 0) {
            return GearRarity.COMMON;
        }

        // Roll
        double roll = random.nextDouble() * totalWeight;
        double cumulative = 0;

        for (int i = 0; i < rarities.length; i++) {
            cumulative += adjustedWeights[i];
            if (roll < cumulative) {
                return rarities[i];
            }
        }

        return GearRarity.COMMON;
    }

    // =========================================================================
    // ACCESSORS
    // =========================================================================

    /**
     * Gets the loot generator.
     */
    @Nonnull
    public LootGenerator getLootGenerator() {
        return lootGenerator;
    }

    /**
     * Gets the map generator.
     */
    @Nonnull
    public RealmMapGenerator getMapGenerator() {
        return mapGenerator;
    }

    /**
     * Gets the config.
     */
    @Nonnull
    public ContainerLootConfig getConfig() {
        return config;
    }
}
