package io.github.larsonix.trailoforbis.loot.container;

import javax.annotation.Nonnull;

/**
 * Defines container quality tiers that affect loot generation.
 *
 * <p>Different container types (regular chests, dungeon chests, boss rewards)
 * have different loot quality expectations. This enum defines the base tier,
 * while actual multipliers come from config.
 *
 * <h2>Tier Hierarchy</h2>
 * <ul>
 *   <li><b>BASIC</b>: Standard world chests, barrels, crates</li>
 *   <li><b>DUNGEON</b>: Found in dungeons, caves, hidden areas</li>
 *   <li><b>BOSS</b>: Rewards from boss encounters, raid chests</li>
 *   <li><b>SPECIAL</b>: Quest rewards, unique containers</li>
 * </ul>
 *
 * @see ContainerTierClassifier
 * @see ContainerLootConfig.TierConfig
 */
public enum ContainerTier {

    /**
     * Basic containers - standard world storage.
     *
     * <p>Includes regular chests, barrels, crates, and other
     * common storage blocks found in the overworld.
     */
    BASIC("basic", 1.0, 0.0),

    /**
     * Dungeon containers - found in dangerous areas.
     *
     * <p>Includes dungeon chests, treasure chests, hidden caches,
     * and other containers in cave systems or dungeons.
     */
    DUNGEON("dungeon", 1.5, 0.15),

    /**
     * Boss containers - rewards from major encounters.
     *
     * <p>Includes boss loot chests, raid reward containers,
     * legendary artifact containers, and altar rewards.
     */
    BOSS("boss", 2.0, 0.30),

    /**
     * Special containers - quest-related or unique.
     *
     * <p>Includes quest reward containers, artifact containers,
     * and other unique storage with special contents.
     */
    SPECIAL("special", 1.75, 0.25);

    private final String configKey;
    private final double defaultLootMultiplier;
    private final double defaultRarityBonus;

    ContainerTier(@Nonnull String configKey, double defaultLootMultiplier, double defaultRarityBonus) {
        this.configKey = configKey;
        this.defaultLootMultiplier = defaultLootMultiplier;
        this.defaultRarityBonus = defaultRarityBonus;
    }

    /**
     * Gets the config key for this tier.
     *
     * <p>Used to look up tier-specific settings in container-loot.yml.
     *
     * @return e.g., "basic", "dungeon"
     */
    @Nonnull
    public String getConfigKey() {
        return configKey;
    }

    /**
     * Gets the default loot multiplier for this tier.
     *
     * <p>This is used as a fallback if config doesn't specify a multiplier.
     * Higher multipliers mean more/better loot.
     *
     * @return 1.0 = baseline
     */
    public double getDefaultLootMultiplier() {
        return defaultLootMultiplier;
    }

    /**
     * Gets the default rarity bonus for this tier.
     *
     * <p>This bonus is added to the rarity roll, making rarer items
     * more likely to appear.
     *
     * @return 0.0-1.0 range, 0.15 = +15% rarity
     */
    public double getDefaultRarityBonus() {
        return defaultRarityBonus;
    }

    /**
     * Finds a tier by its config key.
     *
     * @param configKey The config key to search for
     * @return The matching tier, or BASIC if not found
     */
    @Nonnull
    public static ContainerTier fromConfigKey(@Nonnull String configKey) {
        for (ContainerTier tier : values()) {
            if (tier.configKey.equalsIgnoreCase(configKey)) {
                return tier;
            }
        }
        return BASIC;
    }
}
