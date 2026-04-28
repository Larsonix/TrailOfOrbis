package io.github.larsonix.trailoforbis.maps.config;

import io.github.larsonix.trailoforbis.maps.core.RealmBiomeType;
import io.github.larsonix.trailoforbis.maps.core.RealmLayoutSize;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * Configuration settings for the Realms system.
 *
 * <p>Loaded from {@code config/realms.yml}.
 *
 * <p>Contains:
 * <ul>
 *   <li>General settings (enabled, debug, etc.)</li>
 *   <li>Instance settings (timeout, grace period, max concurrent)</li>
 *   <li>Drop rates and loot settings</li>
 *   <li>Per-biome and per-size overrides</li>
 * </ul>
 */
public class RealmsConfig {

    // ═══════════════════════════════════════════════════════════════════
    // GENERAL SETTINGS
    // ═══════════════════════════════════════════════════════════════════

    private boolean enabled = true;
    private boolean debugMode = false;
    private int maxLevel = 1_000_000;
    private int startingLevel = 1;
    private String mapItemId = "hytale:realm_map";

    // ═══════════════════════════════════════════════════════════════════
    // INSTANCE SETTINGS
    // ═══════════════════════════════════════════════════════════════════

    private int maxConcurrentInstances = 100;
    private int instanceTimeoutSeconds = 600;
    private int completionGracePeriodSeconds = 60;
    private int emptyInstanceTimeoutSeconds = 30;
    private boolean allowReentry = true;
    private DeathPolicy deathPolicy = DeathPolicy.KICK_ON_DEATH;
    private int maxDeaths = 3; // Only used when deathPolicy is LIMITED_LIVES

    // ═══════════════════════════════════════════════════════════════════
    // DROP SETTINGS
    // ═══════════════════════════════════════════════════════════════════

    private double baseMapDropChance = 0.01; // 1% base
    private double mapDropChancePerLevel = 0.0001; // +0.01% per level
    private double bossMapDropMultiplier = 5.0;
    private double eliteMapDropMultiplier = 2.0;
    private double overworldMapDropMultiplier = 0.15; // Overworld drops are 15% of realm rate

    // ═══════════════════════════════════════════════════════════════════
    // STONE DROP SETTINGS
    // ═══════════════════════════════════════════════════════════════════

    private double baseStoneDropChance = 0.01; // 1% base
    private double stoneDropChancePerLevel = 0.00005;
    private double bossStoneDropMultiplier = 10.0;
    private double eliteStoneDropMultiplier = 3.0;

    // ═══════════════════════════════════════════════════════════════════
    // REWARD MULTIPLIERS
    // ═══════════════════════════════════════════════════════════════════

    private double baseXpMultiplier = 1.5;
    private double baseLootMultiplier = 1.2;
    private double difficultyRewardScaling = 0.1; // +10% rewards per difficulty point
    private double difficultyXpScaling = 0.002;   // XP bonus per difficulty point
    private double baseRealmIiqBonus = 100.0;     // +100% = double drop rate in realms

    // ═══════════════════════════════════════════════════════════════════
    // MULTIPLIER CLAMPS (0 = no limit)
    // ═══════════════════════════════════════════════════════════════════

    private double xpMultiplierMin = 0;     // 0 = no minimum
    private double xpMultiplierMax = 0;     // 0 = no maximum
    private double iiqMultiplierMin = 0;    // 0 = no minimum
    private double iiqMultiplierMax = 0;    // 0 = no maximum
    private double iirMultiplierMin = 0;    // 0 = no minimum
    private double iirMultiplierMax = 0;    // 0 = no maximum

    // ═══════════════════════════════════════════════════════════════════
    // BIOME SETTINGS
    // ═══════════════════════════════════════════════════════════════════

    // Exclude utility biomes (SKILL_SANCTUM) from droppable maps by default
    private Set<RealmBiomeType> enabledBiomes = Arrays.stream(RealmBiomeType.values())
        .filter(b -> !b.isUtilityBiome())
        .collect(java.util.stream.Collectors.toCollection(HashSet::new));
    private Map<RealmBiomeType, BiomeSettings> biomeOverrides = new HashMap<>();

    // ═══════════════════════════════════════════════════════════════════
    // SIZE SETTINGS
    // ═══════════════════════════════════════════════════════════════════

    private Set<RealmLayoutSize> enabledSizes = new HashSet<>(Arrays.asList(RealmLayoutSize.values()));
    private Map<RealmLayoutSize, SizeSettings> sizeOverrides = new HashMap<>();
    private SizeScalingConfig sizeScalingConfig = new SizeScalingConfig();
    private ArenaScalingConfig arenaScalingConfig = new ArenaScalingConfig();

    // ═══════════════════════════════════════════════════════════════════
    // TEMPLATE SETTINGS
    // ═══════════════════════════════════════════════════════════════════

    private Map<String, String> templateOverrides = new HashMap<>();

    // ═══════════════════════════════════════════════════════════════════
    // VICTORY SETTINGS
    // ═══════════════════════════════════════════════════════════════════

    /** Emote ID to play on realm completion (empty = disabled). */
    private String victoryEmoteId = "";

    // ═══════════════════════════════════════════════════════════════════
    // VICTORY REWARDS
    // ═══════════════════════════════════════════════════════════════════

    private io.github.larsonix.trailoforbis.maps.reward.VictoryRewardConfig victoryRewardConfig =
        new io.github.larsonix.trailoforbis.maps.reward.VictoryRewardConfig();

    // ═══════════════════════════════════════════════════════════════════
    // SPAWN GATEWAY
    // ═══════════════════════════════════════════════════════════════════

    private SpawnGatewayConfig spawnGatewayConfig = new SpawnGatewayConfig();

    // ═══════════════════════════════════════════════════════════════════
    // GETTERS - GENERAL
    // ═══════════════════════════════════════════════════════════════════

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public int getStartingLevel() {
        return startingLevel;
    }

    /**
     * Gets the item identifier for realm maps.
     *
     * <p>This is the base item type that realm map data is written to.
     * Format: "namespace:item_id" (e.g., "hytale:realm_map").
     *
     * @return The item identifier for realm maps
     */
    @Nonnull
    public String getMapItemId() {
        return mapItemId;
    }

    // ═══════════════════════════════════════════════════════════════════
    // GETTERS - INSTANCE
    // ═══════════════════════════════════════════════════════════════════

    public int getMaxConcurrentInstances() {
        return maxConcurrentInstances;
    }

    public int getInstanceTimeoutSeconds() {
        return instanceTimeoutSeconds;
    }

    public int getCompletionGracePeriodSeconds() {
        return completionGracePeriodSeconds;
    }

    public int getEmptyInstanceTimeoutSeconds() {
        return emptyInstanceTimeoutSeconds;
    }

    public boolean isAllowReentry() {
        return allowReentry;
    }

    @Nonnull
    public DeathPolicy getDeathPolicy() {
        return deathPolicy;
    }

    public int getMaxDeaths() {
        return maxDeaths;
    }

    // ═══════════════════════════════════════════════════════════════════
    // GETTERS - DROP SETTINGS
    // ═══════════════════════════════════════════════════════════════════

    public double getBaseMapDropChance() {
        return baseMapDropChance;
    }

    public double getMapDropChancePerLevel() {
        return mapDropChancePerLevel;
    }

    public double getBossMapDropMultiplier() {
        return bossMapDropMultiplier;
    }

    public double getEliteMapDropMultiplier() {
        return eliteMapDropMultiplier;
    }

    public double getOverworldMapDropMultiplier() {
        return overworldMapDropMultiplier;
    }

    public double getBaseStoneDropChance() {
        return baseStoneDropChance;
    }

    public double getStoneDropChancePerLevel() {
        return stoneDropChancePerLevel;
    }

    public double getBossStoneDropMultiplier() {
        return bossStoneDropMultiplier;
    }

    public double getEliteStoneDropMultiplier() {
        return eliteStoneDropMultiplier;
    }

    // ═══════════════════════════════════════════════════════════════════
    // GETTERS - REWARD MULTIPLIERS
    // ═══════════════════════════════════════════════════════════════════

    public double getBaseXpMultiplier() {
        return baseXpMultiplier;
    }

    public double getBaseLootMultiplier() {
        return baseLootMultiplier;
    }

    public double getDifficultyRewardScaling() {
        return difficultyRewardScaling;
    }

    public double getDifficultyXpScaling() {
        return difficultyXpScaling;
    }

    /**
     * Gets the base IIQ bonus applied to all realm mobs.
     *
     * <p>This bonus is applied to gear drop chance calculations for any mob
     * killed inside a realm, regardless of map modifiers. It stacks additively
     * with IIQ bonuses from map modifiers.
     *
     * <p>A value of 100 means +100% drop chance (effectively doubles drops).
     *
     * @return Base IIQ bonus percentage (default 100.0 = +100%)
     */
    public double getBaseRealmIiqBonus() {
        return baseRealmIiqBonus;
    }

    // ═══════════════════════════════════════════════════════════════════
    // GETTERS - MULTIPLIER CLAMPS
    // ═══════════════════════════════════════════════════════════════════

    public double getXpMultiplierMin() {
        return xpMultiplierMin;
    }

    public double getXpMultiplierMax() {
        return xpMultiplierMax;
    }

    public double getIiqMultiplierMin() {
        return iiqMultiplierMin;
    }

    public double getIiqMultiplierMax() {
        return iiqMultiplierMax;
    }

    public double getIirMultiplierMin() {
        return iirMultiplierMin;
    }

    public double getIirMultiplierMax() {
        return iirMultiplierMax;
    }

    // ═══════════════════════════════════════════════════════════════════
    // GETTERS - BIOME/SIZE
    // ═══════════════════════════════════════════════════════════════════

    @Nonnull
    public Set<RealmBiomeType> getEnabledBiomes() {
        return Collections.unmodifiableSet(enabledBiomes);
    }

    public boolean isBiomeEnabled(@Nonnull RealmBiomeType biome) {
        return enabledBiomes.contains(biome);
    }

    @Nonnull
    public BiomeSettings getBiomeSettings(@Nonnull RealmBiomeType biome) {
        return biomeOverrides.getOrDefault(biome, BiomeSettings.DEFAULT);
    }

    @Nonnull
    public Set<RealmLayoutSize> getEnabledSizes() {
        return Collections.unmodifiableSet(enabledSizes);
    }

    public boolean isSizeEnabled(@Nonnull RealmLayoutSize size) {
        return enabledSizes.contains(size);
    }

    @Nonnull
    public SizeSettings getSizeSettings(@Nonnull RealmLayoutSize size) {
        return sizeOverrides.getOrDefault(size, SizeSettings.DEFAULT);
    }

    /**
     * Gets the size scaling configuration for level-gated map sizes.
     *
     * @return The size scaling config
     */
    @Nonnull
    public SizeScalingConfig getSizeScalingConfig() {
        return sizeScalingConfig;
    }

    // ═══════════════════════════════════════════════════════════════════
    // GETTERS - TEMPLATE OVERRIDES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets template name overrides for biome/size combinations.
     *
     * @return Map of key (BIOME_SIZE) to template name
     */
    @Nonnull
    public Map<String, String> templateOverrides() {
        return Collections.unmodifiableMap(templateOverrides);
    }

    // ═══════════════════════════════════════════════════════════════════
    // GETTERS - VICTORY REWARDS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the victory reward configuration.
     *
     * @return The victory reward config
     */
    @Nonnull
    public io.github.larsonix.trailoforbis.maps.reward.VictoryRewardConfig getVictoryRewardConfig() {
        return victoryRewardConfig;
    }

    /**
     * Gets the emote ID to play on realm completion.
     *
     * <p>Empty string means disabled (no emote). Use the {@code /emote}
     * command in-game to discover available emote IDs.
     *
     * @return The emote ID, or empty string if disabled
     */
    @Nonnull
    public String getVictoryEmoteId() {
        return victoryEmoteId;
    }

    /**
     * Gets the spawn gateway configuration.
     *
     * <p>Spawn gateways place a ring of portals around world spawn to help
     * new players discover the realm map system.
     *
     * @return The spawn gateway config
     */
    @Nonnull
    public SpawnGatewayConfig getSpawnGatewayConfig() {
        return spawnGatewayConfig;
    }

    // ═══════════════════════════════════════════════════════════════════
    // SETTERS (for config loading)
    // ═══════════════════════════════════════════════════════════════════

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
    }

    public void setMaxLevel(int maxLevel) {
        this.maxLevel = maxLevel;
    }

    public void setStartingLevel(int startingLevel) {
        this.startingLevel = startingLevel;
    }

    public void setMapItemId(@Nonnull String mapItemId) {
        this.mapItemId = mapItemId;
    }

    public void setMaxConcurrentInstances(int maxConcurrentInstances) {
        this.maxConcurrentInstances = maxConcurrentInstances;
    }

    public void setInstanceTimeoutSeconds(int instanceTimeoutSeconds) {
        this.instanceTimeoutSeconds = instanceTimeoutSeconds;
    }

    public void setCompletionGracePeriodSeconds(int completionGracePeriodSeconds) {
        this.completionGracePeriodSeconds = completionGracePeriodSeconds;
    }

    public void setEmptyInstanceTimeoutSeconds(int emptyInstanceTimeoutSeconds) {
        this.emptyInstanceTimeoutSeconds = emptyInstanceTimeoutSeconds;
    }

    public void setAllowReentry(boolean allowReentry) {
        this.allowReentry = allowReentry;
    }

    public void setDeathPolicy(@Nonnull DeathPolicy deathPolicy) {
        java.util.Objects.requireNonNull(deathPolicy, "deathPolicy cannot be null");
        this.deathPolicy = deathPolicy;
    }

    public void setMaxDeaths(int maxDeaths) {
        this.maxDeaths = maxDeaths;
    }

    public void setBaseMapDropChance(double baseMapDropChance) {
        this.baseMapDropChance = baseMapDropChance;
    }

    public void setMapDropChancePerLevel(double mapDropChancePerLevel) {
        this.mapDropChancePerLevel = mapDropChancePerLevel;
    }

    public void setBossMapDropMultiplier(double bossMapDropMultiplier) {
        this.bossMapDropMultiplier = bossMapDropMultiplier;
    }

    public void setEliteMapDropMultiplier(double eliteMapDropMultiplier) {
        this.eliteMapDropMultiplier = eliteMapDropMultiplier;
    }

    public void setOverworldMapDropMultiplier(double overworldMapDropMultiplier) {
        this.overworldMapDropMultiplier = overworldMapDropMultiplier;
    }

    public void setBaseStoneDropChance(double baseStoneDropChance) {
        this.baseStoneDropChance = baseStoneDropChance;
    }

    public void setStoneDropChancePerLevel(double stoneDropChancePerLevel) {
        this.stoneDropChancePerLevel = stoneDropChancePerLevel;
    }

    public void setBossStoneDropMultiplier(double bossStoneDropMultiplier) {
        this.bossStoneDropMultiplier = bossStoneDropMultiplier;
    }

    public void setEliteStoneDropMultiplier(double eliteStoneDropMultiplier) {
        this.eliteStoneDropMultiplier = eliteStoneDropMultiplier;
    }

    public void setBaseXpMultiplier(double baseXpMultiplier) {
        this.baseXpMultiplier = baseXpMultiplier;
    }

    public void setBaseLootMultiplier(double baseLootMultiplier) {
        this.baseLootMultiplier = baseLootMultiplier;
    }

    public void setDifficultyRewardScaling(double difficultyRewardScaling) {
        this.difficultyRewardScaling = difficultyRewardScaling;
    }

    public void setDifficultyXpScaling(double difficultyXpScaling) {
        this.difficultyXpScaling = difficultyXpScaling;
    }

    public void setBaseRealmIiqBonus(double baseRealmIiqBonus) {
        this.baseRealmIiqBonus = baseRealmIiqBonus;
    }

    public void setXpMultiplierMin(double xpMultiplierMin) {
        this.xpMultiplierMin = xpMultiplierMin;
    }

    public void setXpMultiplierMax(double xpMultiplierMax) {
        this.xpMultiplierMax = xpMultiplierMax;
    }

    public void setIiqMultiplierMin(double iiqMultiplierMin) {
        this.iiqMultiplierMin = iiqMultiplierMin;
    }

    public void setIiqMultiplierMax(double iiqMultiplierMax) {
        this.iiqMultiplierMax = iiqMultiplierMax;
    }

    public void setIirMultiplierMin(double iirMultiplierMin) {
        this.iirMultiplierMin = iirMultiplierMin;
    }

    public void setIirMultiplierMax(double iirMultiplierMax) {
        this.iirMultiplierMax = iirMultiplierMax;
    }

    public void setEnabledBiomes(@Nonnull Set<RealmBiomeType> enabledBiomes) {
        this.enabledBiomes = new HashSet<>(enabledBiomes);
    }

    public void setBiomeOverride(@Nonnull RealmBiomeType biome, @Nonnull BiomeSettings settings) {
        this.biomeOverrides.put(biome, settings);
    }

    public void setEnabledSizes(@Nonnull Set<RealmLayoutSize> enabledSizes) {
        this.enabledSizes = new HashSet<>(enabledSizes);
    }

    public void setSizeOverride(@Nonnull RealmLayoutSize size, @Nonnull SizeSettings settings) {
        this.sizeOverrides.put(size, settings);
    }

    public void setSizeScalingConfig(@Nonnull SizeScalingConfig sizeScalingConfig) {
        this.sizeScalingConfig = Objects.requireNonNull(sizeScalingConfig, "sizeScalingConfig cannot be null");
    }

    @Nonnull
    public ArenaScalingConfig getArenaScalingConfig() {
        return arenaScalingConfig;
    }

    public void setArenaScalingConfig(@Nonnull ArenaScalingConfig arenaScalingConfig) {
        this.arenaScalingConfig = Objects.requireNonNull(arenaScalingConfig, "arenaScalingConfig cannot be null");
    }

    public void setTemplateOverrides(@Nonnull Map<String, String> templateOverrides) {
        this.templateOverrides = new HashMap<>(templateOverrides);
    }

    public void setTemplateOverride(@Nonnull String key, @Nonnull String templateName) {
        this.templateOverrides.put(key, templateName);
    }

    public void setVictoryRewardConfig(@Nonnull io.github.larsonix.trailoforbis.maps.reward.VictoryRewardConfig victoryRewardConfig) {
        this.victoryRewardConfig = java.util.Objects.requireNonNull(victoryRewardConfig, "victoryRewardConfig cannot be null");
    }

    public void setSpawnGatewayConfig(@Nonnull SpawnGatewayConfig spawnGatewayConfig) {
        this.spawnGatewayConfig = java.util.Objects.requireNonNull(spawnGatewayConfig, "spawnGatewayConfig cannot be null");
    }

    public void setVictoryEmoteId(@Nonnull String victoryEmoteId) {
        this.victoryEmoteId = java.util.Objects.requireNonNull(victoryEmoteId, "victoryEmoteId cannot be null");
    }

    // ═══════════════════════════════════════════════════════════════════
    // NESTED CLASSES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Per-biome configuration overrides.
     */
    public static class BiomeSettings {
        public static final BiomeSettings DEFAULT = new BiomeSettings();

        private double difficultyMultiplier = 1.0;
        private double xpMultiplier = 1.0;
        private double lootMultiplier = 1.0;
        private int minLevel = 1;

        public double getDifficultyMultiplier() {
            return difficultyMultiplier;
        }

        public void setDifficultyMultiplier(double difficultyMultiplier) {
            this.difficultyMultiplier = difficultyMultiplier;
        }

        public double getXpMultiplier() {
            return xpMultiplier;
        }

        public void setXpMultiplier(double xpMultiplier) {
            this.xpMultiplier = xpMultiplier;
        }

        public double getLootMultiplier() {
            return lootMultiplier;
        }

        public void setLootMultiplier(double lootMultiplier) {
            this.lootMultiplier = lootMultiplier;
        }

        public int getMinLevel() {
            return minLevel;
        }

        public void setMinLevel(int minLevel) {
            this.minLevel = minLevel;
        }
    }

    /**
     * Per-size configuration overrides.
     */
    public static class SizeSettings {
        public static final SizeSettings DEFAULT = new SizeSettings();

        private double rewardMultiplier = 1.0;
        private int minPlayerCount = 1;
        private int maxPlayerCount = 6;

        public double getRewardMultiplier() {
            return rewardMultiplier;
        }

        public void setRewardMultiplier(double rewardMultiplier) {
            this.rewardMultiplier = rewardMultiplier;
        }

        public int getMinPlayerCount() {
            return minPlayerCount;
        }

        public void setMinPlayerCount(int minPlayerCount) {
            this.minPlayerCount = minPlayerCount;
        }

        public int getMaxPlayerCount() {
            return maxPlayerCount;
        }

        public void setMaxPlayerCount(int maxPlayerCount) {
            this.maxPlayerCount = maxPlayerCount;
        }
    }

    /**
     * Configuration for level-gated map size rolling.
     *
     * <p>Controls which map sizes can drop at each player level. Each size has a minimum
     * level requirement and a base weight. Below the minimum level, the size has zero
     * weight. Above it, weight ramps up progressively so that just-unlocked sizes are
     * rare and become more common as the player levels further.
     *
     * <p>Ramp formula: {@code weight = baseWeight * min(1.0, (level - minLevel) / rampLevels + 0.1)}
     */
    public static class SizeScalingConfig {
        private final Map<RealmLayoutSize, Integer> minLevels = new EnumMap<>(RealmLayoutSize.class);
        private final Map<RealmLayoutSize, Double> baseWeights = new EnumMap<>(RealmLayoutSize.class);
        private int rampLevels = 10;

        public SizeScalingConfig() {
            minLevels.put(RealmLayoutSize.SMALL, 1);
            minLevels.put(RealmLayoutSize.MEDIUM, 10);
            minLevels.put(RealmLayoutSize.LARGE, 25);
            minLevels.put(RealmLayoutSize.MASSIVE, 50);

            baseWeights.put(RealmLayoutSize.SMALL, 40.0);
            baseWeights.put(RealmLayoutSize.MEDIUM, 35.0);
            baseWeights.put(RealmLayoutSize.LARGE, 20.0);
            baseWeights.put(RealmLayoutSize.MASSIVE, 5.0);
        }

        public int getMinLevel(@Nonnull RealmLayoutSize size) {
            return minLevels.getOrDefault(size, 1);
        }

        public void setMinLevel(@Nonnull RealmLayoutSize size, int level) {
            minLevels.put(size, level);
        }

        public double getBaseWeight(@Nonnull RealmLayoutSize size) {
            return baseWeights.getOrDefault(size, 10.0);
        }

        public void setBaseWeight(@Nonnull RealmLayoutSize size, double weight) {
            baseWeights.put(size, weight);
        }

        public int getRampLevels() {
            return rampLevels;
        }

        public void setRampLevels(int rampLevels) {
            this.rampLevels = Math.max(1, rampLevels);
        }

        /**
         * Calculates the effective weight for a size at a given player level.
         *
         * @param size The realm layout size
         * @param level The player level
         * @return The effective weight (0 if locked, ramped value otherwise)
         */
        public double calculateWeight(@Nonnull RealmLayoutSize size, int level) {
            int minLevel = getMinLevel(size);
            double baseWeight = getBaseWeight(size);

            if (level < minLevel) {
                return 0.0;
            }

            double rampFactor = Math.min(1.0,
                (double) (level - minLevel) / rampLevels + 0.1);
            return baseWeight * rampFactor;
        }
    }

    /**
     * Configuration for spawn gateway portals placed around world spawn.
     *
     * <p>Spawn gateways are a ring of Portal_Device blocks placed around the
     * world spawn point to help new players discover the realm map system.
     * Each portal has a vertical particle beam effect for visibility.
     */
    public static class SpawnGatewayConfig {
        /** Default instance with all settings at defaults. */
        public static final SpawnGatewayConfig DEFAULT = new SpawnGatewayConfig();

        /** Whether spawn gateways are enabled. */
        private boolean enabled = true;

        /** Radius of the portal ring from spawn center (in blocks). */
        private int ringRadius = 40;

        /** Number of portals in the ring (evenly spaced). */
        private int portalCount = 8;

        /** Block type for the portal. */
        private String portalBlockType = "Portal_Device";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getRingRadius() {
            return ringRadius;
        }

        public void setRingRadius(int ringRadius) {
            this.ringRadius = ringRadius;
        }

        public int getPortalCount() {
            return portalCount;
        }

        public void setPortalCount(int portalCount) {
            this.portalCount = portalCount;
        }

        public String getPortalBlockType() {
            return portalBlockType;
        }

        public void setPortalBlockType(String portalBlockType) {
            this.portalBlockType = portalBlockType;
        }
    }

    /**
     * Policy for handling player deaths in realms.
     */
    public enum DeathPolicy {
        /**
         * Player respawns at the realm spawn point (default).
         * No special penalty on death.
         */
        RESPAWN_IN_REALM,

        /**
         * Player is removed from the realm on death.
         * Immediately kicked back to the overworld.
         */
        KICK_ON_DEATH,

        /**
         * Player has a limited number of lives (configured by maxDeaths).
         * After exceeding the limit, player is kicked from the realm.
         */
        LIMITED_LIVES,

        /**
         * No death penalty at all.
         * Same as RESPAWN_IN_REALM but semantically indicates soft mode.
         */
        SOFTCORE
    }

    // ═══════════════════════════════════════════════════════════════════
    // ARENA SCALING CONFIG
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Configuration for dynamic arena radius and degressive timer.
     *
     * <p>Arena radius scales with base mob count to maintain a target density.
     * Timer uses a degressive formula: more mobs = more time, but with diminishing returns.
     */
    public static class ArenaScalingConfig {

        private int targetBlocksPerMob = 100;
        private int timerBaseSeconds = 120;
        private int timerSecondsPerMob = 8;
        private int timerSoftCap = 50;
        private int timerMinimumSeconds = 60;

        public int getTargetBlocksPerMob() { return targetBlocksPerMob; }
        public void setTargetBlocksPerMob(int v) { this.targetBlocksPerMob = v; }

        public int getTimerBaseSeconds() { return timerBaseSeconds; }
        public void setTimerBaseSeconds(int v) { this.timerBaseSeconds = v; }

        public int getTimerSecondsPerMob() { return timerSecondsPerMob; }
        public void setTimerSecondsPerMob(int v) { this.timerSecondsPerMob = v; }

        public int getTimerSoftCap() { return timerSoftCap; }
        public void setTimerSoftCap(int v) { this.timerSoftCap = v; }

        public int getTimerMinimumSeconds() { return timerMinimumSeconds; }
        public void setTimerMinimumSeconds(int v) { this.timerMinimumSeconds = v; }

        public int getMinRadius(@Nonnull RealmLayoutSize size) {
            return size.getMinRadius();
        }

        public int getMaxRadius(@Nonnull RealmLayoutSize size) {
            return size.getMaxRadius();
        }
    }
}
