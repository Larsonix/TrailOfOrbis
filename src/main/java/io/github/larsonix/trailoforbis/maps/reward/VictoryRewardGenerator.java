package io.github.larsonix.trailoforbis.maps.reward;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.gear.generation.RarityRoller;
import io.github.larsonix.trailoforbis.gear.item.CustomItemInstanceId;
import io.github.larsonix.trailoforbis.gear.loot.DropLevelBlender;
import io.github.larsonix.trailoforbis.gear.loot.LootGenerator;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.leveling.api.LevelingService;
import io.github.larsonix.trailoforbis.maps.core.RealmLayoutSize;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;
import io.github.larsonix.trailoforbis.maps.items.RealmMapGenerator;
import io.github.larsonix.trailoforbis.maps.items.RealmMapUtils;
import io.github.larsonix.trailoforbis.stones.StoneType;
import io.github.larsonix.trailoforbis.stones.StoneUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates victory rewards for players completing a realm.
 *
 * <p>Rewards include maps, gear, and stones based on the completed realm's size.
 * Each item is randomly chosen from: map, stone, or gear (equal weight).
 * IIQ (Increased Item Quantity) provides bonus roll chances for extra items,
 * while IIR (Increased Item Rarity) improves the quality of generated items.
 *
 * <h2>Reward Structure by Size</h2>
 * <table>
 *   <tr><th>Size</th><th>Items</th><th>Bonus IIR</th><th>Bonus IIQ</th></tr>
 *   <tr><td>SMALL</td><td>2</td><td>-</td><td>-</td></tr>
 *   <tr><td>MEDIUM</td><td>3</td><td>-</td><td>-</td></tr>
 *   <tr><td>LARGE</td><td>3</td><td>+25%</td><td>-</td></tr>
 *   <tr><td>MASSIVE</td><td>3</td><td>+50%</td><td>+10%</td></tr>
 * </table>
 *
 * <h2>IIQ Bonus System</h2>
 * <p>Base amounts are guaranteed. IIQ provides a % chance for +1 bonus random
 * item. Each potential bonus is rolled independently.
 *
 * <h2>Thread Safety</h2>
 * <p>This class is thread-safe when using the default constructor (ThreadLocalRandom).
 *
 * @see VictoryRewardConfig
 * @see VictoryRewardDistributor
 */
public final class VictoryRewardGenerator {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // ═══════════════════════════════════════════════════════════════════
    // FIELDS
    // ═══════════════════════════════════════════════════════════════════

    private final VictoryRewardConfig config;
    private final RealmMapGenerator mapGenerator;
    private final LootGenerator lootGenerator;
    private final RarityRoller rarityRoller;
    private final DropLevelBlender dropLevelBlender;
    private final Random random;

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTORS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a VictoryRewardGenerator with default random source.
     *
     * @param config The victory reward configuration
     * @param mapGenerator The map generator
     * @param lootGenerator The loot generator for gear
     * @param rarityRoller The rarity roller for stones
     * @param dropLevelBlender The drop level blender
     */
    public VictoryRewardGenerator(
            @Nonnull VictoryRewardConfig config,
            @Nonnull RealmMapGenerator mapGenerator,
            @Nonnull LootGenerator lootGenerator,
            @Nonnull RarityRoller rarityRoller,
            @Nonnull DropLevelBlender dropLevelBlender) {
        this(config, mapGenerator, lootGenerator, rarityRoller, dropLevelBlender, ThreadLocalRandom.current());
    }

    /**
     * Creates a VictoryRewardGenerator with custom random source (for testing).
     *
     * @param config The victory reward configuration
     * @param mapGenerator The map generator
     * @param lootGenerator The loot generator for gear
     * @param rarityRoller The rarity roller for stones
     * @param dropLevelBlender The drop level blender
     * @param random The random number generator
     */
    public VictoryRewardGenerator(
            @Nonnull VictoryRewardConfig config,
            @Nonnull RealmMapGenerator mapGenerator,
            @Nonnull LootGenerator lootGenerator,
            @Nonnull RarityRoller rarityRoller,
            @Nonnull DropLevelBlender dropLevelBlender,
            @Nonnull Random random) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.mapGenerator = Objects.requireNonNull(mapGenerator, "mapGenerator cannot be null");
        this.lootGenerator = Objects.requireNonNull(lootGenerator, "lootGenerator cannot be null");
        this.rarityRoller = Objects.requireNonNull(rarityRoller, "rarityRoller cannot be null");
        this.dropLevelBlender = Objects.requireNonNull(dropLevelBlender, "dropLevelBlender cannot be null");
        this.random = Objects.requireNonNull(random, "random cannot be null");
    }

    // ═══════════════════════════════════════════════════════════════════
    // GENERATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Result of victory reward generation.
     *
     * @param maps Generated map items
     * @param gear Generated gear items
     * @param stones Generated stone items
     */
    public record VictoryRewards(
        @Nonnull List<ItemStack> maps,
        @Nonnull List<ItemStack> gear,
        @Nonnull List<ItemStack> stones
    ) {
        public VictoryRewards {
            maps = List.copyOf(maps);
            gear = List.copyOf(gear);
            stones = List.copyOf(stones);
        }

        /**
         * Gets the total number of items generated.
         */
        public int totalCount() {
            return maps.size() + gear.size() + stones.size();
        }

        /**
         * Checks if any rewards were generated.
         */
        public boolean hasRewards() {
            return !maps.isEmpty() || !gear.isEmpty() || !stones.isEmpty();
        }

        /**
         * Returns all items as a single list.
         */
        @Nonnull
        public List<ItemStack> allItems() {
            List<ItemStack> all = new ArrayList<>(totalCount());
            all.addAll(maps);
            all.addAll(gear);
            all.addAll(stones);
            return all;
        }

        /**
         * Returns an empty rewards result.
         */
        @Nonnull
        public static VictoryRewards empty() {
            return new VictoryRewards(List.of(), List.of(), List.of());
        }
    }

    /**
     * Item type for random reward selection.
     */
    private enum RewardItemType {
        MAP, STONE, GEAR
    }

    /**
     * Generates victory rewards for a player.
     *
     * <p>Each item slot is randomly assigned to map, stone, or gear (equal weight).
     *
     * @param realm The completed realm
     * @param playerId The player receiving rewards
     * @param rewardResult The calculated reward multipliers from RealmRewardCalculator
     * @return Generated rewards
     */
    @Nonnull
    public VictoryRewards generate(
            @Nonnull RealmInstance realm,
            @Nonnull UUID playerId,
            @Nonnull RealmRewardResult rewardResult) {
        Objects.requireNonNull(realm, "realm cannot be null");
        Objects.requireNonNull(playerId, "playerId cannot be null");
        Objects.requireNonNull(rewardResult, "rewardResult cannot be null");

        if (!rewardResult.hasRewards()) {
            LOGGER.atFine().log("Player %s has no rewards - skipping generation",
                playerId.toString().substring(0, 8));
            return VictoryRewards.empty();
        }

        RealmMapData mapData = realm.getMapData();
        RealmLayoutSize size = mapData.size();
        int completedLevel = mapData.level();

        // Get base rewards for this size
        VictoryRewardConfig.SizeRewards sizeRewards = config.getSizeRewards(size);

        // Calculate IIQ bonus (for extra item rolls)
        // IIQ multiplier is already calculated as 1.x, so subtract 1 to get the bonus portion
        double iiqBonus = Math.max(0, rewardResult.itemQuantityMultiplier() - 1.0);
        double tierIiqBonus = sizeRewards.getBonusIiq() / 100.0; // Convert percentage to decimal
        double totalIiqBonus = iiqBonus + tierIiqBonus;

        // Calculate total IIR bonus (player bonus + tier bonus)
        // IIR multiplier is already calculated as 1.x, so subtract 1 to get the bonus portion
        double iirBonus = Math.max(0, rewardResult.itemRarityMultiplier() - 1.0);
        double tierIirBonus = sizeRewards.getBonusIir() / 100.0; // Convert percentage to decimal
        double totalIirBonus = iirBonus + tierIirBonus;

        LOGGER.atFine().log("Generating victory rewards for player %s: size=%s, level=%d, iiq=%.2f (tier=%.2f), iir=%.2f (tier=%.2f)",
            playerId.toString().substring(0, 8), size, completedLevel, totalIiqBonus, tierIiqBonus, totalIirBonus, tierIirBonus);

        // Look up player level for drop level blending
        int playerLevel = getPlayerLevel(playerId);

        // Calculate total item count (base + IIQ bonus rolls)
        int totalCount = calculateItemCount(sizeRewards.getTotalItems(), totalIiqBonus);

        // Randomly assign each item to a type (equal weight: map, stone, gear)
        RewardItemType[] types = RewardItemType.values();
        int mapCount = 0;
        int stoneCount = 0;
        int gearCount = 0;
        for (int i = 0; i < totalCount; i++) {
            switch (types[random.nextInt(types.length)]) {
                case MAP -> mapCount++;
                case STONE -> stoneCount++;
                case GEAR -> gearCount++;
            }
        }

        // Generate items
        List<ItemStack> maps = generateMaps(completedLevel, playerLevel, mapCount, totalIirBonus);
        List<ItemStack> stones = generateStones(stoneCount, totalIirBonus);
        List<ItemStack> gear = generateGear(completedLevel, playerLevel, gearCount, totalIirBonus);

        LOGGER.atInfo().log("Generated victory rewards for player %s: %d maps, %d stones, %d gear (total=%d)",
            playerId.toString().substring(0, 8), maps.size(), stones.size(), gear.size(), totalCount);

        return new VictoryRewards(maps, gear, stones);
    }

    // ═══════════════════════════════════════════════════════════════════
    // ITEM COUNT CALCULATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Calculates the total item count including IIQ bonus rolls.
     *
     * <p>Base count is guaranteed. Each potential bonus item is rolled independently
     * with success probability equal to IIQ bonus (capped by maxBonusPerType).
     *
     * @param baseCount The base guaranteed count
     * @param iiqBonus The IIQ bonus as decimal (0.5 = 50% chance per bonus)
     * @return Total item count
     */
    int calculateItemCount(int baseCount, double iiqBonus) {
        if (baseCount <= 0) {
            return 0; // Size tier doesn't grant this item type
        }

        int bonusCount = 0;
        int maxBonus = config.getMaxBonusPerType();

        // Roll for each potential bonus item
        for (int i = 0; i < maxBonus; i++) {
            if (random.nextDouble() < iiqBonus) {
                bonusCount++;
            }
        }

        return baseCount + bonusCount;
    }

    // ═══════════════════════════════════════════════════════════════════
    // MAP GENERATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Generates map items.
     *
     * @param completedLevel The level of the completed realm
     * @param playerLevel The player's level (for drop level blending)
     * @param count Number of maps to generate
     * @param rarityBonus IIR bonus as decimal
     * @return List of generated map ItemStacks
     */
    @Nonnull
    private List<ItemStack> generateMaps(int completedLevel, int playerLevel, int count, double rarityBonus) {
        if (count <= 0) {
            return List.of();
        }

        List<ItemStack> maps = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            try {
                int level = dropLevelBlender.getConfig().enabled()
                    ? dropLevelBlender.calculate(completedLevel, playerLevel, random)
                    : config.calculateMapLevel(completedLevel, random);
                RealmMapData mapData = mapGenerator.generate(level, rarityBonus);

                // Generate unique instance ID for custom item registration
                CustomItemInstanceId instanceId = CustomItemInstanceId.Generator.generateMap();

                // Update map data with instance ID
                mapData = mapData.withInstanceId(instanceId);

                // Get the custom item ID (used for the ItemStack)
                String customItemId = instanceId.toItemId();

                // Create ItemStack with the CUSTOM item ID (not the base item)
                ItemStack itemStack = new ItemStack(customItemId, 1);

                // Write map data to item metadata
                ItemStack mapItem = RealmMapUtils.writeMapData(itemStack, mapData);

                if (mapItem != null && !mapItem.isEmpty()) {
                    maps.add(mapItem);
                    LOGGER.atFine().log("Generated victory map: level=%d, rarity=%s, biome=%s, customId=%s",
                        level, mapData.rarity(), mapData.biome(), customItemId);
                }
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Failed to generate victory map");
            }
        }

        return maps;
    }

    // ═══════════════════════════════════════════════════════════════════
    // STONE GENERATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Generates stone items using native Hytale item IDs.
     *
     * @param count Number of stones to generate
     * @param rarityBonus IIR bonus as decimal
     * @return List of generated stone ItemStacks
     */
    @Nonnull
    private List<ItemStack> generateStones(int count, double rarityBonus) {
        if (count <= 0) {
            return List.of();
        }

        List<ItemStack> stones = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            // Roll rarity first
            GearRarity rarity = rarityRoller.roll(rarityBonus);

            // Get stones of that rarity
            List<StoneType> availableStones = StoneType.getByRarity(rarity);
            if (availableStones.isEmpty()) {
                // Fallback to COMMON if rarity has no stones
                availableStones = StoneType.getByRarity(GearRarity.COMMON);
            }

            if (availableStones.isEmpty()) {
                LOGGER.atWarning().log("No stones available for rarity %s or COMMON", rarity);
                continue;
            }

            // Pick a random stone of that rarity
            StoneType stoneType = availableStones.get(random.nextInt(availableStones.size()));

            // Create native stone item
            ItemStack stoneItem = StoneUtils.createStoneItem(stoneType);

            if (!stoneItem.isEmpty()) {
                stones.add(stoneItem);
                LOGGER.atFine().log("Generated victory stone: %s (%s)",
                    stoneType.getDisplayName(), rarity);
            }
        }

        return stones;
    }

    // ═══════════════════════════════════════════════════════════════════
    // GEAR GENERATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Generates gear items.
     *
     * @param completedLevel The level of the completed realm
     * @param playerLevel The player's level (for drop level blending)
     * @param count Number of gear items to generate
     * @param rarityBonus IIR bonus as decimal
     * @return List of generated gear ItemStacks
     */
    @Nonnull
    private List<ItemStack> generateGear(int completedLevel, int playerLevel, int count, double rarityBonus) {
        if (count <= 0) {
            return List.of();
        }

        List<ItemStack> gear = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            int level = dropLevelBlender.getConfig().enabled()
                ? dropLevelBlender.calculate(completedLevel, playerLevel, random)
                : config.calculateGearLevel(completedLevel, random);

            // Convert rarity bonus from decimal to percentage for LootGenerator
            // LootGenerator.drop().rarityBonus() expects percentage (e.g., 50 for 50%)
            double rarityBonusPercent = rarityBonus * 100.0;

            ItemStack gearItem = lootGenerator.drop()
                .level(level)
                .rarityBonus(rarityBonusPercent)
                .build();

            if (gearItem != null && !gearItem.isEmpty()) {
                gear.add(gearItem);
                LOGGER.atFine().log("Generated victory gear: level=%d, item=%s",
                    level, gearItem.getItemId());
            }
        }

        return gear;
    }

    // ═══════════════════════════════════════════════════════════════════
    // PLAYER LEVEL
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the player's level from the leveling service.
     *
     * @param playerId The player's UUID
     * @return The player's level, or 1 if service unavailable
     */
    private int getPlayerLevel(@Nonnull UUID playerId) {
        Optional<LevelingService> serviceOpt = ServiceRegistry.get(LevelingService.class);
        if (serviceOpt.isPresent()) {
            return serviceOpt.get().getLevel(playerId);
        }
        return 1;
    }

    // ═══════════════════════════════════════════════════════════════════
    // ACCESSORS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the victory reward configuration.
     */
    @Nonnull
    public VictoryRewardConfig getConfig() {
        return config;
    }

    /**
     * Gets the map generator.
     */
    @Nonnull
    public RealmMapGenerator getMapGenerator() {
        return mapGenerator;
    }

    /**
     * Gets the loot generator.
     */
    @Nonnull
    public LootGenerator getLootGenerator() {
        return lootGenerator;
    }

    /**
     * Gets the rarity roller.
     */
    @Nonnull
    public RarityRoller getRarityRoller() {
        return rarityRoller;
    }
}
