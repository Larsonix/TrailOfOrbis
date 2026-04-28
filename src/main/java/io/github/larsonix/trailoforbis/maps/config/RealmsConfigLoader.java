package io.github.larsonix.trailoforbis.maps.config;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.maps.core.RealmBiomeType;
import io.github.larsonix.trailoforbis.maps.core.RealmLayoutSize;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifierType;
import org.yaml.snakeyaml.Yaml;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;

/**
 * Loads realm configuration from YAML files.
 *
 * <p>Loads the following configs:
 * <ul>
 *   <li>{@code realms.yml} - Main realm settings</li>
 *   <li>{@code realm-modifiers.yml} - Modifier generation settings</li>
 *   <li>{@code realm-mobs.yml} - Mob pool configurations</li>
 * </ul>
 */
public class RealmsConfigLoader {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Path configDirectory;
    private final Yaml yaml;

    private RealmsConfig realmsConfig;
    private RealmModifierConfig modifierConfig;
    private MobPoolConfig mobPoolConfig;

    /**
     * Creates a new config loader.
     *
     * @param configDirectory The directory containing config files
     */
    public RealmsConfigLoader(@Nonnull Path configDirectory) {
        this.configDirectory = Objects.requireNonNull(configDirectory, "configDirectory cannot be null");
        this.yaml = new Yaml();
    }

    // ═══════════════════════════════════════════════════════════════════
    // LOAD ALL
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Loads all realm configuration files.
     *
     * <p>Creates default configs if files don't exist.
     */
    public void loadAll() {
        LOGGER.at(Level.INFO).log("Loading realm configurations...");

        realmsConfig = loadRealmsConfig();
        modifierConfig = loadModifierConfig();
        mobPoolConfig = loadMobPoolConfig();

        LOGGER.at(Level.INFO).log("Realm configurations loaded successfully");
    }

    // ═══════════════════════════════════════════════════════════════════
    // REALMS CONFIG
    // ═══════════════════════════════════════════════════════════════════

    private RealmsConfig loadRealmsConfig() {
        RealmsConfig config = new RealmsConfig();
        Path path = configDirectory.resolve("realms.yml");

        Map<String, Object> data = loadYaml(path);
        if (data == null) {
            LOGGER.at(Level.INFO).log("Using default realms.yml config");
            return config;
        }

        // General settings
        config.setEnabled(getBoolean(data, "enabled", true));
        config.setDebugMode(getBoolean(data, "debug-mode", false));
        config.setMaxLevel(getInt(data, "max-level", 1_000_000));
        config.setStartingLevel(getInt(data, "starting-level", 1));

        // Item settings
        Map<String, Object> items = getMap(data, "items");
        if (items != null) {
            // Support both old and new config format
            String mapBaseItem = getString(items, "map-base-item", null);
            if (mapBaseItem == null) {
                mapBaseItem = getString(items, "map-item-id", "Objective_Treasure_Map");
            }
            config.setMapItemId(mapBaseItem);

        }

        // Instance settings
        Map<String, Object> instance = getMap(data, "instance");
        if (instance != null) {
            config.setMaxConcurrentInstances(getInt(instance, "max-concurrent", 100));
            config.setInstanceTimeoutSeconds(getInt(instance, "timeout-seconds", 600));
            config.setCompletionGracePeriodSeconds(getInt(instance, "grace-period-seconds", 60));
            config.setEmptyInstanceTimeoutSeconds(getInt(instance, "empty-timeout-seconds", 30));
            config.setAllowReentry(getBoolean(instance, "allow-reentry", true));

            // Death policy
            String deathPolicyStr = getString(instance, "death-policy", "KICK_ON_DEATH");
            try {
                config.setDeathPolicy(RealmsConfig.DeathPolicy.valueOf(deathPolicyStr.toUpperCase()));
            } catch (IllegalArgumentException e) {
                LOGGER.at(Level.WARNING).log("Invalid death-policy '%s', using KICK_ON_DEATH", deathPolicyStr);
                config.setDeathPolicy(RealmsConfig.DeathPolicy.KICK_ON_DEATH);
            }

            // Max deaths (for LIMITED_LIVES policy)
            config.setMaxDeaths(getInt(instance, "max-deaths", 3));
        }

        // Drop settings
        Map<String, Object> drops = getMap(data, "drops");
        if (drops != null) {
            Map<String, Object> mapDrops = getMap(drops, "maps");
            if (mapDrops != null) {
                config.setBaseMapDropChance(getDouble(mapDrops, "base-chance", 0.02));
                config.setMapDropChancePerLevel(getDouble(mapDrops, "per-level", 0.0001));
                config.setBossMapDropMultiplier(getDouble(mapDrops, "boss-multiplier", 5.0));
                config.setEliteMapDropMultiplier(getDouble(mapDrops, "elite-multiplier", 2.0));
                config.setOverworldMapDropMultiplier(getDouble(mapDrops, "overworld-multiplier", 0.15));
            }

            Map<String, Object> stoneDrops = getMap(drops, "stones");
            if (stoneDrops != null) {
                config.setBaseStoneDropChance(getDouble(stoneDrops, "base-chance", 0.01));
                config.setStoneDropChancePerLevel(getDouble(stoneDrops, "per-level", 0.00005));
                config.setBossStoneDropMultiplier(getDouble(stoneDrops, "boss-multiplier", 10.0));
                config.setEliteStoneDropMultiplier(getDouble(stoneDrops, "elite-multiplier", 3.0));
            }
        }

        // Reward multipliers
        Map<String, Object> rewards = getMap(data, "rewards");
        if (rewards != null) {
            config.setBaseXpMultiplier(getDouble(rewards, "base-xp-multiplier", 1.5));
            config.setBaseLootMultiplier(getDouble(rewards, "base-loot-multiplier", 1.2));
            config.setDifficultyRewardScaling(getDouble(rewards, "difficulty-scaling", 0.1));
            config.setDifficultyXpScaling(getDouble(rewards, "difficulty-xp-scaling", 0.002));
            config.setBaseRealmIiqBonus(getDouble(rewards, "base-realm-iiq-bonus", 100.0));
        }

        // Multiplier clamps
        Map<String, Object> clamps = getMap(data, "multiplier-clamps");
        if (clamps != null) {
            Map<String, Object> xpClamp = getMap(clamps, "xp");
            if (xpClamp != null) {
                config.setXpMultiplierMin(getDouble(xpClamp, "min", 0.0));
                config.setXpMultiplierMax(getDouble(xpClamp, "max", 0.0));
            }
        }

        // Enabled biomes
        List<String> enabledBiomes = getStringList(data, "enabled-biomes");
        if (enabledBiomes != null && !enabledBiomes.isEmpty()) {
            Set<RealmBiomeType> biomes = new HashSet<>();
            for (String name : enabledBiomes) {
                try {
                    biomes.add(RealmBiomeType.fromString(name));
                } catch (IllegalArgumentException e) {
                    LOGGER.at(Level.WARNING).log("Unknown biome in config: %s", name);
                }
            }
            config.setEnabledBiomes(biomes);
        }

        // Enabled sizes
        List<String> enabledSizes = getStringList(data, "enabled-sizes");
        if (enabledSizes != null && !enabledSizes.isEmpty()) {
            Set<RealmLayoutSize> sizes = new HashSet<>();
            for (String name : enabledSizes) {
                try {
                    sizes.add(RealmLayoutSize.fromString(name));
                } catch (IllegalArgumentException e) {
                    LOGGER.at(Level.WARNING).log("Unknown size in config: %s", name);
                }
            }
            config.setEnabledSizes(sizes);
        }

        // Biome overrides
        Map<String, Object> biomeOverrides = getMap(data, "biome-overrides");
        if (biomeOverrides != null) {
            for (Map.Entry<String, Object> entry : biomeOverrides.entrySet()) {
                try {
                    RealmBiomeType biome = RealmBiomeType.fromString(entry.getKey());
                    @SuppressWarnings("unchecked")
                    Map<String, Object> settings = (Map<String, Object>) entry.getValue();
                    RealmsConfig.BiomeSettings biomeSettings = new RealmsConfig.BiomeSettings();
                    biomeSettings.setDifficultyMultiplier(getDouble(settings, "difficulty-multiplier", 1.0));
                    biomeSettings.setXpMultiplier(getDouble(settings, "xp-multiplier", 1.0));
                    biomeSettings.setLootMultiplier(getDouble(settings, "loot-multiplier", 1.0));
                    biomeSettings.setMinLevel(getInt(settings, "min-level", 1));
                    config.setBiomeOverride(biome, biomeSettings);
                } catch (Exception e) {
                    LOGGER.at(Level.WARNING).log("Invalid biome override: %s", entry.getKey());
                }
            }
        }

        // Size scaling (level-gated map sizes)
        Map<String, Object> sizeScaling = getMap(data, "size-scaling");
        if (sizeScaling != null) {
            config.setSizeScalingConfig(loadSizeScalingConfig(sizeScaling));
        }

        // Victory settings
        Map<String, Object> victory = getMap(data, "victory");
        if (victory != null) {
            config.setVictoryEmoteId(getString(victory, "emote-id", ""));
        }

        // Victory rewards
        Map<String, Object> victoryRewards = getMap(data, "victory-rewards");
        if (victoryRewards != null) {
            config.setVictoryRewardConfig(loadVictoryRewardConfig(victoryRewards));
        }

        // Spawn gateway
        Map<String, Object> spawnGateway = getMap(data, "spawn-gateway");
        if (spawnGateway != null) {
            config.setSpawnGatewayConfig(loadSpawnGatewayConfig(spawnGateway));
        }

        // Arena scaling (dynamic radius + degressive timer)
        Map<String, Object> arenaScaling = getMap(data, "arena-scaling");
        if (arenaScaling != null) {
            config.setArenaScalingConfig(loadArenaScalingConfig(arenaScaling));
        }

        return config;
    }

    // ═══════════════════════════════════════════════════════════════════
    // SIZE SCALING CONFIG
    // ═══════════════════════════════════════════════════════════════════

    private RealmsConfig.SizeScalingConfig loadSizeScalingConfig(Map<String, Object> data) {
        RealmsConfig.SizeScalingConfig config = new RealmsConfig.SizeScalingConfig();

        // Min levels per size
        Map<String, Object> minLevels = getMap(data, "min-levels");
        if (minLevels != null) {
            for (RealmLayoutSize size : RealmLayoutSize.values()) {
                String key = size.name().toLowerCase();
                int level = getInt(minLevels, key, config.getMinLevel(size));
                config.setMinLevel(size, level);
            }
        }

        // Base weights per size
        Map<String, Object> baseWeights = getMap(data, "base-weights");
        if (baseWeights != null) {
            for (RealmLayoutSize size : RealmLayoutSize.values()) {
                String key = size.name().toLowerCase();
                double weight = getDouble(baseWeights, key, config.getBaseWeight(size));
                config.setBaseWeight(size, weight);
            }
        }

        // Ramp levels
        config.setRampLevels(getInt(data, "ramp-levels", config.getRampLevels()));

        LOGGER.at(Level.FINE).log("Loaded size scaling config: rampLevels=%d", config.getRampLevels());

        return config;
    }

    // ═══════════════════════════════════════════════════════════════════
    // SPAWN GATEWAY CONFIG
    // ═══════════════════════════════════════════════════════════════════

    private RealmsConfig.ArenaScalingConfig loadArenaScalingConfig(Map<String, Object> data) {
        RealmsConfig.ArenaScalingConfig config = new RealmsConfig.ArenaScalingConfig();

        config.setTargetBlocksPerMob(getInt(data, "target-blocks-per-mob", 100));
        config.setTimerBaseSeconds(getInt(data, "timer-base-seconds", 120));
        config.setTimerSecondsPerMob(getInt(data, "timer-seconds-per-mob", 8));
        config.setTimerSoftCap(getInt(data, "timer-soft-cap", 50));
        config.setTimerMinimumSeconds(getInt(data, "timer-minimum-seconds", 60));

        return config;
    }

    private RealmsConfig.SpawnGatewayConfig loadSpawnGatewayConfig(Map<String, Object> data) {
        RealmsConfig.SpawnGatewayConfig config = new RealmsConfig.SpawnGatewayConfig();

        config.setEnabled(getBoolean(data, "enabled", true));
        config.setRingRadius(getInt(data, "ring-radius", 40));
        config.setPortalCount(getInt(data, "portal-count", 8));

        String portalBlockType = getString(data, "portal-block-type", "Portal_Device");
        if (portalBlockType != null) {
            config.setPortalBlockType(portalBlockType);
        }

        LOGGER.at(Level.FINE).log("Loaded spawn gateway config: enabled=%b, radius=%d, portals=%d",
            config.isEnabled(), config.getRingRadius(), config.getPortalCount());

        return config;
    }

    // ═══════════════════════════════════════════════════════════════════
    // VICTORY REWARD CONFIG
    // ═══════════════════════════════════════════════════════════════════

    private io.github.larsonix.trailoforbis.maps.reward.VictoryRewardConfig loadVictoryRewardConfig(
            Map<String, Object> data) {
        io.github.larsonix.trailoforbis.maps.reward.VictoryRewardConfig config =
            new io.github.larsonix.trailoforbis.maps.reward.VictoryRewardConfig();

        // Size rewards
        Map<String, Object> sizeRewardsData = getMap(data, "size-rewards");
        if (sizeRewardsData != null) {
            for (RealmLayoutSize size : RealmLayoutSize.values()) {
                String key = size.name().toLowerCase();
                Map<String, Object> sizeData = getMap(sizeRewardsData, key);
                if (sizeData != null) {
                    io.github.larsonix.trailoforbis.maps.reward.VictoryRewardConfig.SizeRewards rewards =
                        new io.github.larsonix.trailoforbis.maps.reward.VictoryRewardConfig.SizeRewards();
                    rewards.setTotalItems(getInt(sizeData, "total-items", 0));
                    rewards.setBonusIir(getDouble(sizeData, "bonus-iir", 0.0));
                    rewards.setBonusIiq(getDouble(sizeData, "bonus-iiq", 0.0));
                    config.setSizeRewards(size, rewards);
                }
            }
        }

        // Level settings
        config.setMapLevelVariance(getInt(data, "map-level-variance", 1));
        config.setGearLevelMinOffset(getInt(data, "gear-level-min-offset", -3));
        config.setGearLevelMaxOffset(getInt(data, "gear-level-max-offset", 0));
        config.setMaxBonusPerType(getInt(data, "max-bonus-per-type", 2));

        return config;
    }

    // ═══════════════════════════════════════════════════════════════════
    // MODIFIER CONFIG
    // ═══════════════════════════════════════════════════════════════════

    private RealmModifierConfig loadModifierConfig() {
        RealmModifierConfig config = new RealmModifierConfig();
        Path path = configDirectory.resolve("realm-modifiers.yml");

        Map<String, Object> data = loadYaml(path);
        if (data == null) {
            LOGGER.at(Level.INFO).log("Using default realm-modifiers.yml config");
            return config;
        }

        // Modifier counts by rarity (separate prefix/suffix ranges)
        Map<String, Object> counts = getMap(data, "modifier-counts");
        if (counts != null) {
            for (GearRarity rarity : GearRarity.values()) {
                Map<String, Object> rarityData = getMap(counts, rarity.name().toLowerCase());
                if (rarityData != null) {
                    // Total modifier cap
                    if (rarityData.containsKey("max_modifiers")) {
                        config.setMaxModifiers(rarity, getInt(rarityData, "max_modifiers", 1));
                    }
                    List<Integer> prefixRange = getIntList(rarityData, "prefix_range");
                    if (prefixRange != null && prefixRange.size() >= 2) {
                        config.setPrefixCountRange(rarity, prefixRange.get(0), prefixRange.get(1));
                    }
                    List<Integer> suffixRange = getIntList(rarityData, "suffix_range");
                    if (suffixRange != null && suffixRange.size() >= 2) {
                        config.setSuffixCountRange(rarity, suffixRange.get(0), suffixRange.get(1));
                    }
                }
            }
        }

        // Prefix weights (difficulty modifiers)
        Map<String, Object> prefixWeights = getMap(data, "prefix-weights");
        if (prefixWeights != null) {
            loadWeightsFromMap(config, prefixWeights);
        }

        // Suffix weights (reward modifiers)
        Map<String, Object> suffixWeights = getMap(data, "suffix-weights");
        if (suffixWeights != null) {
            loadWeightsFromMap(config, suffixWeights);
        }

        // Legacy: flat modifier-weights section (fallback for old configs)
        Map<String, Object> modifierWeights = getMap(data, "modifier-weights");
        if (modifierWeights != null) {
            loadWeightsFromMap(config, modifierWeights);
        }

        // Per-modifier settings (supports both legacy min/max and new base_min/base_max/scale_per_level)
        Map<String, Object> modifierSettings = getMap(data, "modifier-settings");
        if (modifierSettings != null) {
            for (RealmModifierType type : RealmModifierType.values()) {
                String key = type.name().toLowerCase().replace('_', '-');
                Map<String, Object> settings = getMap(modifierSettings, key);
                if (settings != null) {
                    RealmModifierConfig.ModifierSettings ms = RealmModifierConfig.ModifierSettings.fromType(type);

                    // Support new base_min/base_max/scale_per_level format
                    if (settings.containsKey("base_min") || settings.containsKey("base_max")) {
                        ms.setBaseMin(getInt(settings, "base_min", type.getMinValue()));
                        ms.setBaseMax(getInt(settings, "base_max", type.getMaxValue()));
                        ms.setScalePerLevel(getDouble(settings, "scale_per_level", 0.0));
                    } else {
                        // Legacy flat min/max format (binary modifiers and backward compat)
                        ms.setBaseMin(getInt(settings, "min", type.getMinValue()));
                        ms.setBaseMax(getInt(settings, "max", type.getMaxValue()));
                        ms.setScalePerLevel(0.0);
                    }

                    ms.setEnabled(getBoolean(settings, "enabled", true));

                    List<String> incompatible = getStringList(settings, "incompatible-with");
                    if (incompatible != null) {
                        for (String name : incompatible) {
                            try {
                                ms.addIncompatible(RealmModifierType.fromString(name.toUpperCase().replace('-', '_')));
                            } catch (IllegalArgumentException e) {
                                LOGGER.at(Level.WARNING).log("Unknown modifier in incompatible list: %s", name);
                            }
                        }
                    }

                    config.setModifierSettings(type, ms);
                }
            }
        }

        return config;
    }

    // ═══════════════════════════════════════════════════════════════════
    // MOB POOL CONFIG
    // ═══════════════════════════════════════════════════════════════════

    private MobPoolConfig loadMobPoolConfig() {
        MobPoolConfig config = new MobPoolConfig();
        Path path = configDirectory.resolve("realm-mobs.yml");

        Map<String, Object> data = loadYaml(path);
        if (data == null) {
            LOGGER.at(Level.INFO).log("Using default realm-mobs.yml config");
            return config;
        }

        // Spawn settings
        Map<String, Object> spawn = getMap(data, "spawn-settings");
        if (spawn != null) {
            config.setBaseEliteChance(getDouble(spawn, "base-elite-chance", 0.05));
            config.setEliteChancePerLevel(getDouble(spawn, "elite-chance-per-level", 0.0001));
            config.setMaxEliteChance(getDouble(spawn, "max-elite-chance", 0.25));
            config.setBaseBossChance(getDouble(spawn, "base-boss-chance", 0.03));
            config.setBossChancePerLevel(getDouble(spawn, "boss-chance-per-level", 0.0005));
            config.setMaxBossChance(getDouble(spawn, "max-boss-chance", 0.10));
            config.setEliteScale((float) getDouble(spawn, "elite-scale", 1.15));
            config.setBossScale((float) getDouble(spawn, "boss-scale", 1.35));
            config.setMinSpawnDistance(getInt(spawn, "min-distance", 20));
            config.setMaxSpawnDistance(getInt(spawn, "max-distance", 50));
            config.setSpawnBatchSize(getInt(spawn, "batch-size", 5));
            config.setSpawnIntervalTicks(getInt(spawn, "interval-ticks", 100));
            config.setSpawnExclusionRadius(getDouble(spawn, "spawn-exclusion-radius", 20.0));
            config.setSpawnCountMultiplier((float) getDouble(spawn, "spawn-count-multiplier", 2.0));
            config.setMinionLevelFraction((float) getDouble(spawn, "minion-level-fraction", 0.75));
        }

        // Biome pools
        // Note: Elite is now a spawn-time modifier, not a separate pool.
        // The "elite" key in YAML is ignored - those mobs should be in "regular".
        Map<String, Object> biomePools = getMap(data, "biome-pools");
        if (biomePools != null) {
            for (RealmBiomeType biome : RealmBiomeType.values()) {
                String key = biome.name().toLowerCase();
                Map<String, Object> poolData = getMap(biomePools, key);
                if (poolData != null) {
                    MobPoolConfig.BiomeMobPool pool = new MobPoolConfig.BiomeMobPool();

                    loadMobList(poolData, "regular", pool::addRegularMob);
                    loadMobList(poolData, "boss", pool::addBossMob);

                    config.setMobPool(biome, pool);
                }
            }
        }

        return config;
    }

    private void loadMobList(Map<String, Object> data, String key, java.util.function.BiConsumer<String, Integer> adder) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> mobs = (List<Map<String, Object>>) data.get(key);
        if (mobs != null) {
            for (Map<String, Object> mob : mobs) {
                String id = getString(mob, "id", null);
                int weight = getInt(mob, "weight", 10);
                if (id != null) {
                    adder.accept(id, weight);
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ACCESSORS
    // ═══════════════════════════════════════════════════════════════════

    @Nonnull
    public RealmsConfig getRealmsConfig() {
        if (realmsConfig == null) {
            realmsConfig = new RealmsConfig();
        }
        return realmsConfig;
    }

    @Nonnull
    public RealmModifierConfig getModifierConfig() {
        if (modifierConfig == null) {
            modifierConfig = new RealmModifierConfig();
        }
        return modifierConfig;
    }

    @Nonnull
    public MobPoolConfig getMobPoolConfig() {
        if (mobPoolConfig == null) {
            mobPoolConfig = new MobPoolConfig();
        }
        return mobPoolConfig;
    }

    // ═══════════════════════════════════════════════════════════════════
    // YAML UTILITIES
    // ═══════════════════════════════════════════════════════════════════

    private Map<String, Object> loadYaml(Path path) {
        if (!Files.exists(path)) {
            return null;
        }

        try (InputStream is = Files.newInputStream(path)) {
            Object loaded = yaml.load(is);
            if (loaded instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) loaded;
                return result;
            }
            return null;
        } catch (IOException e) {
            LOGGER.at(Level.WARNING).withCause(e).log("Failed to load config: %s", path);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return null;
    }

    private String getString(Map<String, Object> data, String key, String defaultValue) {
        Object value = data.get(key);
        if (value instanceof String) {
            return (String) value;
        }
        return defaultValue;
    }

    private boolean getBoolean(Map<String, Object> data, String key, boolean defaultValue) {
        Object value = data.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }

    private int getInt(Map<String, Object> data, String key, int defaultValue) {
        Object value = data.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    private double getDouble(Map<String, Object> data, String key, double defaultValue) {
        Object value = data.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    private List<String> getStringList(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof String) {
                    result.add((String) item);
                }
            }
            return result;
        }
        return null;
    }

    @Nullable
    private List<Integer> getIntList(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value instanceof List<?> list) {
            List<Integer> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Number num) {
                    result.add(num.intValue());
                }
            }
            return result;
        }
        return null;
    }

    private void loadWeightsFromMap(RealmModifierConfig config, Map<String, Object> weightsMap) {
        for (RealmModifierType type : RealmModifierType.values()) {
            String key = type.name().toLowerCase().replace('_', '-');
            if (weightsMap.containsKey(key)) {
                config.setModifierWeight(type, getDouble(weightsMap, key, 10.0));
            }
        }
    }

}
