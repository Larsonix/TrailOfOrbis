package io.github.larsonix.trailoforbis.gear.loot;

import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig.DistanceScalingConfig;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig.LootConfig;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig.MobBonusConfig;

import java.util.Map;
import java.util.Objects;

/**
 * Provides convenient access to loot configuration values.
 *
 * <p>This class wraps the nested config structure from
 * {@link GearBalanceConfig.LootConfig} and provides default values
 * for missing configuration.
 *
 * <p>Note: This class is named LootSettings to avoid confusion with
 * the nested record {@code GearBalanceConfig.LootConfig} which holds
 * the raw configuration values.
 */
public final class LootSettings {

    // Default values
    private static final double DEFAULT_BASE_DROP_CHANCE = 0.05;         // 5%
    private static final double DEFAULT_LUCK_TO_RARITY = 0.5;            // +0.5% per LUCK
    private static final int DEFAULT_BLOCKS_PER_PERCENT = 100;
    private static final double DEFAULT_MAX_DISTANCE_BONUS = 50.0;
    private static final double DEFAULT_ELITE_QUANTITY_BONUS = 0.5;
    private static final double DEFAULT_ELITE_RARITY_BONUS = 0.25;
    private static final double DEFAULT_BOSS_QUANTITY_BONUS = 1.0;
    private static final double DEFAULT_BOSS_RARITY_BONUS = 0.5;

    private final double baseDropChance;
    private final double luckToRarityPercent;
    private final boolean distanceScalingEnabled;
    private final int blocksPerPercent;
    private final double maxDistanceBonus;
    private final Map<MobType, MobBonus> mobBonuses;

    /**
     * Mob types for bonus lookup.
     */
    public enum MobType {
        NORMAL,
        ELITE,
        BOSS
    }

    /**
     * Bonus values for a mob type.
     */
    public record MobBonus(double quantityBonus, double rarityBonus) {
        public static final MobBonus NONE = new MobBonus(0, 0);
    }

    /**
     * Creates LootSettings from balance config.
     *
     * @param balanceConfig The gear balance configuration
     * @throws NullPointerException if balanceConfig is null
     */
    public LootSettings(GearBalanceConfig balanceConfig) {
        Objects.requireNonNull(balanceConfig, "balanceConfig cannot be null");

        LootConfig loot = balanceConfig.loot();
        if (loot != null) {
            this.baseDropChance = loot.baseDropChance();
            this.luckToRarityPercent = loot.luckToRarityPercent();

            DistanceScalingConfig distance = loot.distanceScaling();
            if (distance != null) {
                this.distanceScalingEnabled = distance.enabled();
                this.blocksPerPercent = distance.blocksPerPercent();
                this.maxDistanceBonus = distance.maxBonus();
            } else {
                this.distanceScalingEnabled = true;
                this.blocksPerPercent = DEFAULT_BLOCKS_PER_PERCENT;
                this.maxDistanceBonus = DEFAULT_MAX_DISTANCE_BONUS;
            }

            this.mobBonuses = buildMobBonuses(loot);
        } else {
            // Use all defaults
            this.baseDropChance = DEFAULT_BASE_DROP_CHANCE;
            this.luckToRarityPercent = DEFAULT_LUCK_TO_RARITY;
            this.distanceScalingEnabled = true;
            this.blocksPerPercent = DEFAULT_BLOCKS_PER_PERCENT;
            this.maxDistanceBonus = DEFAULT_MAX_DISTANCE_BONUS;
            this.mobBonuses = buildDefaultMobBonuses();
        }
    }

    /**
     * Creates LootSettings with explicit values (for testing).
     */
    public LootSettings(
            double baseDropChance,
            double luckToRarityPercent,
            boolean distanceScalingEnabled,
            int blocksPerPercent,
            double maxDistanceBonus,
            Map<MobType, MobBonus> mobBonuses
    ) {
        this.baseDropChance = baseDropChance;
        this.luckToRarityPercent = luckToRarityPercent;
        this.distanceScalingEnabled = distanceScalingEnabled;
        this.blocksPerPercent = blocksPerPercent;
        this.maxDistanceBonus = maxDistanceBonus;
        this.mobBonuses = Map.copyOf(mobBonuses);
    }

    private Map<MobType, MobBonus> buildMobBonuses(LootConfig loot) {
        MobBonusConfig normalConfig = loot.mobBonus("normal");
        MobBonusConfig eliteConfig = loot.mobBonus("elite");
        MobBonusConfig bossConfig = loot.mobBonus("boss");

        return Map.of(
            MobType.NORMAL, extractMobBonus(normalConfig, MobBonus.NONE),
            MobType.ELITE, extractMobBonus(eliteConfig,
                new MobBonus(DEFAULT_ELITE_QUANTITY_BONUS, DEFAULT_ELITE_RARITY_BONUS)),
            MobType.BOSS, extractMobBonus(bossConfig,
                new MobBonus(DEFAULT_BOSS_QUANTITY_BONUS, DEFAULT_BOSS_RARITY_BONUS))
        );
    }

    private MobBonus extractMobBonus(MobBonusConfig config, MobBonus defaultBonus) {
        if (config == null || config == MobBonusConfig.NONE) {
            return defaultBonus;
        }
        return new MobBonus(config.quantityBonus(), config.rarityBonus());
    }

    private Map<MobType, MobBonus> buildDefaultMobBonuses() {
        return Map.of(
            MobType.NORMAL, MobBonus.NONE,
            MobType.ELITE, new MobBonus(DEFAULT_ELITE_QUANTITY_BONUS, DEFAULT_ELITE_RARITY_BONUS),
            MobType.BOSS, new MobBonus(DEFAULT_BOSS_QUANTITY_BONUS, DEFAULT_BOSS_RARITY_BONUS)
        );
    }

    // =========================================================================
    // GETTERS
    // =========================================================================

    /**
     * Gets the base chance for gear to drop (0.0-1.0).
     *
     * @return Base drop chance
     */
    public double getBaseDropChance() {
        return baseDropChance;
    }

    /**
     * Gets the rarity percent bonus per LUCK point.
     *
     * @return Luck to rarity percent conversion factor
     */
    public double getLuckToRarityPercent() {
        return luckToRarityPercent;
    }

    /**
     * Returns true if distance from spawn affects rarity.
     *
     * @return true if distance scaling is enabled
     */
    public boolean isDistanceScalingEnabled() {
        return distanceScalingEnabled;
    }

    /**
     * Gets how many blocks from spawn equals +1% rarity.
     *
     * @return Blocks per percent of rarity bonus
     */
    public int getBlocksPerPercent() {
        return blocksPerPercent;
    }

    /**
     * Gets the maximum rarity bonus from distance.
     *
     * @return Maximum distance-based rarity bonus
     */
    public double getMaxDistanceBonus() {
        return maxDistanceBonus;
    }

    /**
     * Gets the bonus values for a mob type.
     *
     * @param type The mob type
     * @return The mob bonus configuration
     */
    public MobBonus getMobBonus(MobType type) {
        return mobBonuses.getOrDefault(type, MobBonus.NONE);
    }

    /**
     * Gets the drop quantity multiplier for a mob type.
     * Returns 1.0 + bonus (e.g., elite returns 1.5 for +50% bonus).
     *
     * @param type The mob type
     * @return Quantity multiplier (1.0 = no bonus)
     */
    public double getQuantityMultiplier(MobType type) {
        return 1.0 + getMobBonus(type).quantityBonus();
    }

    /**
     * Gets the rarity bonus percent for a mob type.
     *
     * @param type The mob type
     * @return Rarity bonus as percentage (e.g., 25.0 for +25%)
     */
    public double getRarityBonus(MobType type) {
        return getMobBonus(type).rarityBonus() * 100;  // Convert to percent
    }
}
