package io.github.larsonix.trailoforbis.config;

import com.hypixel.hytale.logger.HytaleLogger;

import io.github.larsonix.trailoforbis.api.services.ConfigService;
import io.github.larsonix.trailoforbis.combat.deathrecap.DeathRecapConfig;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig;
import io.github.larsonix.trailoforbis.gear.config.GearConfigLoader;
import io.github.larsonix.trailoforbis.gear.config.GearConfigLoader.ConfigurationException;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig;
import io.github.larsonix.trailoforbis.gear.conversion.VanillaConversionConfig;
import io.github.larsonix.trailoforbis.gear.loot.LootDiscoveryConfig;
import io.github.larsonix.trailoforbis.gear.loot.LootItemsConfig;
import io.github.larsonix.trailoforbis.gear.tooltip.TooltipConfig;
import io.github.larsonix.trailoforbis.loot.container.ContainerLootConfig;
import io.github.larsonix.trailoforbis.leveling.config.LevelingConfig;
import io.github.larsonix.trailoforbis.mobs.MobScalingConfig;
import io.github.larsonix.trailoforbis.mobs.classification.EntityDiscoveryConfig;
import io.github.larsonix.trailoforbis.mobs.classification.MobClassificationConfig;
import io.github.larsonix.trailoforbis.mobs.elemental.MobElementConfig;
import io.github.larsonix.trailoforbis.mobs.spawn.config.MobSpawnConfig;
import io.github.larsonix.trailoforbis.mobs.stats.MobStatPoolConfig;
import io.github.larsonix.trailoforbis.combat.indicators.color.CombatTextColorConfig;
import io.github.larsonix.trailoforbis.sanctum.config.SkillSanctumConfig;
import io.github.larsonix.trailoforbis.ui.inventory.InventoryDetectionConfig;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;

/**
 * Manages all configuration files for the plugin.
 *
 * This class handles loading, saving, and reloading of configuration files.
 * It creates default configurations if they don't exist and provides hot-reload support.
 */
public class ConfigManager implements ConfigService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Path configDir;

    public Path getConfigDir() {
        return configDir;
    }

    private RPGConfig rpgConfig;
    private MobClassificationConfig mobClassificationConfig;
    private EntityDiscoveryConfig entityDiscoveryConfig;
    private MobStatPoolConfig mobStatPoolConfig;
    private MobScalingConfig mobScalingConfig;
    private MobSpawnConfig mobSpawnConfig;
    private MobElementConfig mobElementConfig;
    private LevelingConfig levelingConfig;
    private DeathRecapConfig deathRecapConfig;
    private InventoryDetectionConfig inventoryDetectionConfig;
    private io.github.larsonix.trailoforbis.lootfilter.config.LootFilterConfig lootFilterConfig;
    private CombatTextColorConfig combatTextColorConfig;
    private SkillSanctumConfig skillSanctumConfig;
    private io.github.larsonix.trailoforbis.compat.party.PartyConfig partyConfig;
    private io.github.larsonix.trailoforbis.compat.HexcodeItemConfig hexcodeItemConfig;
    private io.github.larsonix.trailoforbis.compat.HexcodeSpellConfig hexcodeSpellConfig;

    // Gear configuration
    private GearBalanceConfig gearBalanceConfig;
    private ModifierConfig modifierConfig;
    private LootItemsConfig lootItemsConfig;
    private LootDiscoveryConfig lootDiscoveryConfig;
    private TooltipConfig tooltipConfig;
    private VanillaConversionConfig vanillaConversionConfig;
    private ContainerLootConfig containerLootConfig;
    private final GearConfigLoader gearConfigLoader;

    /** @param dataFolder typically mods/TrailOfOrbis/ */
    public ConfigManager(Path dataFolder) {
        // Load configs from config/ subfolder to avoid old config files in root folder
        this.configDir = dataFolder.resolve("config");
        this.gearConfigLoader = new GearConfigLoader(dataFolder.resolve("config"));
    }

    /** For testing with pre-loaded config. */
    public ConfigManager(Path dataFolder, RPGConfig rpgConfig) {
        this.configDir = dataFolder;
        this.rpgConfig = rpgConfig;
        this.mobClassificationConfig = new MobClassificationConfig(); // Default for testing
        this.entityDiscoveryConfig = EntityDiscoveryConfig.createDefaults(); // Default for testing
        this.mobStatPoolConfig = MobStatPoolConfig.createDefaults(); // Default for testing
        this.mobScalingConfig = new MobScalingConfig(); // Default for testing
        this.mobElementConfig = MobElementConfig.createDefaults(); // Default for testing
        this.levelingConfig = new LevelingConfig(); // Default for testing
        this.deathRecapConfig = new DeathRecapConfig(); // Default for testing
        this.gearConfigLoader = new GearConfigLoader(dataFolder);
    }

    /**
     * Loads all configuration files.
     * Creates default configs if they don't exist.
     *
     * @return true if all configs loaded successfully
     */
    @Override
    public boolean loadConfigs() {
        try {
            // Create config directory if it doesn't exist
            Files.createDirectories(configDir);

            // Load main config
            rpgConfig = loadConfig("config.yml", RPGConfig.class, new RPGConfig());

            // Validate configuration values
            rpgConfig.validate();
            LOGGER.at(Level.INFO).log("Configuration validated successfully");

            // Load mob classification config
            mobClassificationConfig = loadConfig("mob-classification.yml", MobClassificationConfig.class, new MobClassificationConfig());

            // Log mob classification config summary
            LOGGER.at(Level.INFO).log("Mob Classification loaded: %d elites, %d bosses",
                mobClassificationConfig.getElites().size(),
                mobClassificationConfig.getBosses().size());
            LOGGER.at(Level.FINE).log("Mob Classification XP multipliers: BOSS=%.1f, ELITE=%.1f, HOSTILE=%.1f",
                mobClassificationConfig.getXpMultiplier(io.github.larsonix.trailoforbis.mobs.classification.RPGMobClass.BOSS),
                mobClassificationConfig.getXpMultiplier(io.github.larsonix.trailoforbis.mobs.classification.RPGMobClass.ELITE),
                mobClassificationConfig.getXpMultiplier(io.github.larsonix.trailoforbis.mobs.classification.RPGMobClass.HOSTILE));

            // Load entity discovery config
            entityDiscoveryConfig = loadConfig("entity-discovery.yml", EntityDiscoveryConfig.class, EntityDiscoveryConfig.createDefaults());

            // Validate entity discovery config
            entityDiscoveryConfig.validate();

            // Log entity discovery config summary
            LOGGER.at(Level.INFO).log("Entity Discovery loaded: enabled=%s, name_patterns=%s, group_patterns=%s",
                entityDiscoveryConfig.getDiscovery().isEnabled() ? "yes" : "no",
                entityDiscoveryConfig.getDiscovery().isDetect_by_name() ? "enabled" : "disabled",
                entityDiscoveryConfig.getDiscovery().isDetect_by_group() ? "enabled" : "disabled");
            LOGGER.at(Level.FINE).log("Entity Discovery: %d boss patterns, %d elite patterns, %d overrides",
                entityDiscoveryConfig.getDetection_patterns().getBoss().size(),
                entityDiscoveryConfig.getDetection_patterns().getElite().size(),
                entityDiscoveryConfig.getOverrides().getBosses().size() + entityDiscoveryConfig.getOverrides().getElites().size());

            // Load mob stat pool config
            mobStatPoolConfig = loadConfig("mob-stat-pool.yml", MobStatPoolConfig.class, MobStatPoolConfig.createDefaults());

            // Validate mob stat pool config
            mobStatPoolConfig.validate();

            // Log mob stat pool config summary
            LOGGER.at(Level.INFO).log("Mob Stat Pool loaded: %d stats configured, %d archetypes",
                mobStatPoolConfig.getStatConfigs().size(),
                mobStatPoolConfig.getAllArchetypes().size());
            LOGGER.at(Level.FINE).log("Mob Stat Pool: points_per_level=%.1f, distance_bonus=%.3f, boss_multiplier=%.2f",
                mobStatPoolConfig.getPointsPerLevel(),
                mobStatPoolConfig.getDistanceBonusPerBlock(),
                mobStatPoolConfig.getBossPoolMultiplier());

            // Load mob scaling config
            mobScalingConfig = loadConfig("mob-scaling.yml", MobScalingConfig.class, new MobScalingConfig());

            // Validate mob scaling config
            mobScalingConfig.validate();

            // Log mob scaling config summary
            LOGGER.at(Level.INFO).log("Mob Scaling loaded: safe_zone=%s, spawn_multiplier=%s, dynamic_refresh=%s",
                mobScalingConfig.isEnabled() ? "enabled" : "disabled",
                mobScalingConfig.getSpawnMultiplier().isEnabled() ? "enabled" : "disabled",
                mobScalingConfig.getDynamicRefresh().isEnabled() ? "enabled" : "disabled");
            LOGGER.at(Level.FINE).log("Mob Scaling: safe_zone_radius=%.0f, pool_per_block=%.2f, group_multiplier=%.1f",
                mobScalingConfig.getSafeZone().getRadius(),
                mobScalingConfig.getDistanceScaling().getPoolPerBlock(),
                mobScalingConfig.getPlayerDetection().getGroupMultiplier());

            // Load mob spawn config
            mobSpawnConfig = loadConfig("mob-spawn.yml", MobSpawnConfig.class, new MobSpawnConfig());

            // Validate mob spawn config
            mobSpawnConfig.validate();

            // Log mob spawn config summary
            LOGGER.at(Level.INFO).log("Mob Spawn loaded: enabled=%s, preset=%s, level_scaling=%s",
                mobSpawnConfig.isEnabled() ? "yes" : "no",
                mobSpawnConfig.getPreset(),
                mobSpawnConfig.getLevelScaling().isEnabled() ? "enabled" : "disabled");
            LOGGER.at(Level.FINE).log("Mob Spawn: monster=%.1fx, elite=%.1fx, level_per_inc=%d, inc_bonus=%.1f",
                mobSpawnConfig.getClassMultipliers().getOrDefault("MONSTER", 1.0),
                mobSpawnConfig.getClassMultipliers().getOrDefault("ELITE", 1.0),
                mobSpawnConfig.getLevelScaling().getLevelPerIncrement(),
                mobSpawnConfig.getLevelScaling().getIncrementBonus());

            // Load mob element config
            mobElementConfig = loadConfig("mob-elements.yml", MobElementConfig.class, MobElementConfig.createDefaults());

            // Log mob element config summary
            LOGGER.at(Level.INFO).log("Mob Elements loaded: %d group mappings, %d keyword categories",
                mobElementConfig.getGroup_elements().size(),
                mobElementConfig.getKeywords().size());

            // Load leveling config
            levelingConfig = loadConfig("leveling.yml", LevelingConfig.class, new LevelingConfig());

            // Validate leveling config
            levelingConfig.validate();

            // Log leveling config summary
            LOGGER.at(Level.INFO).log("Leveling loaded: enabled=%s, max_level=%d, xp_loss=%s",
                levelingConfig.isEnabled() ? "yes" : "no",
                levelingConfig.getFormula().getMaxLevel(),
                levelingConfig.getXpLoss().isEnabled() ? "enabled" : "disabled");
            LOGGER.at(Level.FINE).log("Leveling: base_xp=%.0f, exponent=%.2f, xp_per_mob_level=%.1f",
                levelingConfig.getFormula().getBaseXp(),
                levelingConfig.getFormula().getExponent(),
                levelingConfig.getXpGain().getXpPerMobLevel());

            // Load death recap config
            deathRecapConfig = loadConfig("death-recap.yml", DeathRecapConfig.class, new DeathRecapConfig());

            // Validate death recap config
            deathRecapConfig.validate();

            // Log death recap config summary
            LOGGER.at(Level.INFO).log("Death Recap loaded: enabled=%s, mode=%s",
                deathRecapConfig.isEnabled() ? "yes" : "no",
                deathRecapConfig.getDisplayMode());

            // Load loot filter config
            lootFilterConfig = loadConfig("loot-filter.yml",
                    io.github.larsonix.trailoforbis.lootfilter.config.LootFilterConfig.class,
                    new io.github.larsonix.trailoforbis.lootfilter.config.LootFilterConfig());
            LOGGER.at(Level.INFO).log("Loot Filter loaded: enabled=%s, presets=%d",
                lootFilterConfig.isEnabled() ? "yes" : "no",
                lootFilterConfig.getPresets().size());

            // Load inventory detection config
            inventoryDetectionConfig = loadInventoryDetectionConfig();

            // Log inventory detection config summary
            LOGGER.at(Level.INFO).log("Inventory Detection loaded: enabled=%s, debug=%s",
                inventoryDetectionConfig.isEnabled() ? "yes" : "no",
                inventoryDetectionConfig.isDebug() ? "yes" : "no");

            // Load combat text color config
            combatTextColorConfig = loadCombatTextColorConfig();
            LOGGER.at(Level.INFO).log("Combat Text Color loaded: enabled=%s, profiles=%d",
                combatTextColorConfig.isEnabled() ? "yes" : "no",
                combatTextColorConfig.getProfiles().size());

            // Load skill sanctum config
            skillSanctumConfig = loadConfig("skill-sanctum.yml", SkillSanctumConfig.class, new SkillSanctumConfig());
            skillSanctumConfig.validate();
            LOGGER.at(Level.INFO).log("Skill Sanctum loaded: enabled=%s, duration=%dms, fillRate=%d/tick, margin=%dms",
                skillSanctumConfig.isEnabled() ? "yes" : "no",
                skillSanctumConfig.getConnections().getBeamDurationMs(),
                skillSanctumConfig.getConnections().getInitialFillRate(),
                skillSanctumConfig.getConnections().getRefreshMarginMs());

            // Load party integration config
            partyConfig = loadConfig("party.yml",
                io.github.larsonix.trailoforbis.compat.party.PartyConfig.class,
                new io.github.larsonix.trailoforbis.compat.party.PartyConfig());
            LOGGER.at(Level.INFO).log("Party config loaded: enabled=%s", partyConfig.isEnabled() ? "yes" : "no");

            // Load Hexcode item integration config
            hexcodeItemConfig = loadConfig("hexcode-items.yml",
                io.github.larsonix.trailoforbis.compat.HexcodeItemConfig.class,
                new io.github.larsonix.trailoforbis.compat.HexcodeItemConfig());
            hexcodeItemConfig.validate();
            LOGGER.at(Level.INFO).log("Hexcode items config loaded: enabled=%s, staffs=%d, books=%d",
                hexcodeItemConfig.isEnabled() ? "yes" : "no",
                hexcodeItemConfig.getStaffItemIds().size(),
                hexcodeItemConfig.getBookItemIds().size());

            // Load Hexcode spell damage integration config
            hexcodeSpellConfig = loadConfig("hexcode-spells.yml",
                io.github.larsonix.trailoforbis.compat.HexcodeSpellConfig.class,
                new io.github.larsonix.trailoforbis.compat.HexcodeSpellConfig());
            hexcodeSpellConfig.validate();
            LOGGER.at(Level.INFO).log("Hexcode spells config loaded: enabled=%s, mappings=%d",
                hexcodeSpellConfig.isEnabled() ? "yes" : "no",
                hexcodeSpellConfig.getDamage_type_map().size());

            return true;
        } catch (RPGConfig.ConfigValidationException e) {
            LOGGER.at(Level.SEVERE).log("Configuration validation failed: %s", e.getMessage());
            return false;
        } catch (EntityDiscoveryConfig.ConfigValidationException e) {
            LOGGER.at(Level.SEVERE).log("Entity Discovery config validation failed: %s", e.getMessage());
            return false;
        } catch (MobStatPoolConfig.ConfigValidationException e) {
            LOGGER.at(Level.SEVERE).log("Mob Stat Pool config validation failed: %s", e.getMessage());
            return false;
        } catch (MobScalingConfig.ConfigValidationException e) {
            LOGGER.at(Level.SEVERE).log("Mob Scaling config validation failed: %s", e.getMessage());
            return false;
        } catch (MobSpawnConfig.ConfigValidationException e) {
            LOGGER.at(Level.SEVERE).log("Mob Spawn config validation failed: %s", e.getMessage());
            return false;
        } catch (LevelingConfig.ConfigValidationException e) {
            LOGGER.at(Level.SEVERE).log("Leveling config validation failed: %s", e.getMessage());
            return false;
        } catch (DeathRecapConfig.ConfigValidationException e) {
            LOGGER.at(Level.SEVERE).log("Death Recap config validation failed: %s", e.getMessage());
            return false;
        } catch (SkillSanctumConfig.ConfigValidationException e) {
            LOGGER.at(Level.SEVERE).log("Skill Sanctum config validation failed: %s", e.getMessage());
            return false;
        } catch (Exception e) {
            LOGGER.at(Level.SEVERE).withCause(e).log("Error loading configuration: %s", e.getMessage());
            return false;
        }
    }

    /** Hot-reload all configs without server restart. */
    @Override
    public boolean reloadConfigs() {
        LOGGER.at(Level.INFO).log("Reloading configurations...");
        return loadConfigs();
    }

    /** @param defaultConfig used if file doesn't exist or parse fails */
    private <T> T loadConfig(String filename, Class<T> configClass, T defaultConfig) throws IOException {
        Path configPath = configDir.resolve(filename);

        // Create default config if it doesn't exist
        if (!Files.exists(configPath)) {
            LOGGER.at(Level.INFO).log("Creating default config: %s", filename);
            saveDefaultConfig(filename, defaultConfig);
            return defaultConfig;
        }

        // Load existing config
        LoaderOptions loaderOptions = new LoaderOptions();
        Constructor constructor = new Constructor(configClass, loaderOptions);
        Yaml yaml = new Yaml(constructor);
        try (InputStream input = Files.newInputStream(configPath)) {
            T loaded = yaml.loadAs(input, configClass);
            if (loaded == null) {
                LOGGER.at(Level.SEVERE).log("Warning: Config file %s is empty, using defaults", filename);
                return defaultConfig;
            }
            return loaded;
        } catch (Exception e) {
            LOGGER.at(Level.SEVERE).log("Error parsing config file %s: %s", filename, e.getMessage());
            LOGGER.at(Level.SEVERE).log("Using default configuration");
            return defaultConfig;
        }
    }

    /**
     * Saves default configuration to file by copying the bundled template from resources.
     * This preserves comments and formatting from the original config.yml.
     */
    private <T> void saveDefaultConfig(String filename, T config) throws IOException {
        Path configPath = configDir.resolve(filename);

        // Try to copy from bundled resources first (preserves comments)
        try (InputStream resourceStream = getClass().getClassLoader()
                .getResourceAsStream("config/" + filename)) {
            if (resourceStream != null) {
                Files.copy(resourceStream, configPath);
                return;
            }
        }

        // Fallback: programmatically dump (no comments, but functional)
        Yaml yaml = new Yaml();
        try (Writer writer = Files.newBufferedWriter(configPath)) {
            yaml.dump(config, writer);
        }
    }

    public RPGConfig getConfig() {
        return rpgConfig;
    }

    @Override
    public RPGConfig getRPGConfig() {
        return rpgConfig;
    }

    public MobClassificationConfig getMobClassificationConfig() {
        return mobClassificationConfig;
    }

    /**
     * Controls dynamic NPC discovery and classification,
     * providing automatic mod compatibility for enemy/boss detection.
     */
    public EntityDiscoveryConfig getEntityDiscoveryConfig() {
        return entityDiscoveryConfig;
    }

    public MobStatPoolConfig getMobStatPoolConfig() {
        return mobStatPoolConfig;
    }

    public MobScalingConfig getMobScalingConfig() {
        return mobScalingConfig;
    }

    public LevelingConfig getLevelingConfig() {
        return levelingConfig;
    }

    public MobSpawnConfig getMobSpawnConfig() {
        return mobSpawnConfig;
    }

    /**
     * Controls how mobs gain elemental damage types based on NPCGroup membership
     * (e.g., Void -> VOID) and role name keywords (e.g., "fire_mage" -> FIRE).
     */
    public MobElementConfig getMobElementConfig() {
        return mobElementConfig;
    }

    public DeathRecapConfig getDeathRecapConfig() {
        return deathRecapConfig;
    }

    public InventoryDetectionConfig getInventoryDetectionConfig() {
        return inventoryDetectionConfig;
    }

    public io.github.larsonix.trailoforbis.lootfilter.config.LootFilterConfig getLootFilterConfig() {
        return lootFilterConfig;
    }

    /** Uses raw YAML map loading since InventoryDetectionConfig uses fromYaml(). */
    @SuppressWarnings("unchecked")
    private InventoryDetectionConfig loadInventoryDetectionConfig() {
        Path configPath = configDir.resolve("inventory-detection.yml");

        // Copy default if not present
        copyDefaultIfMissing("inventory-detection.yml");

        try {
            if (Files.exists(configPath)) {
                Yaml yaml = new Yaml();
                try (InputStream input = Files.newInputStream(configPath)) {
                    Object loaded = yaml.load(input);
                    if (loaded instanceof java.util.Map) {
                        return InventoryDetectionConfig.fromYaml((java.util.Map<String, Object>) loaded);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e).log("Failed to load inventory-detection.yml, using defaults");
        }

        return InventoryDetectionConfig.defaults();
    }

    public CombatTextColorConfig getCombatTextColorConfig() {
        return combatTextColorConfig != null ? combatTextColorConfig : CombatTextColorConfig.createDefault();
    }

    public SkillSanctumConfig getSkillSanctumConfig() {
        return skillSanctumConfig != null ? skillSanctumConfig : new SkillSanctumConfig();
    }

    public io.github.larsonix.trailoforbis.compat.party.PartyConfig getPartyConfig() {
        return partyConfig != null ? partyConfig : new io.github.larsonix.trailoforbis.compat.party.PartyConfig();
    }

    public io.github.larsonix.trailoforbis.compat.HexcodeItemConfig getHexcodeItemConfig() {
        return hexcodeItemConfig != null ? hexcodeItemConfig : new io.github.larsonix.trailoforbis.compat.HexcodeItemConfig();
    }

    public io.github.larsonix.trailoforbis.compat.HexcodeSpellConfig getHexcodeSpellConfig() {
        return hexcodeSpellConfig != null ? hexcodeSpellConfig : new io.github.larsonix.trailoforbis.compat.HexcodeSpellConfig();
    }

    /**
     * Loads the combat text color configuration.
     *
     * <p>Uses raw YAML map loading since CombatTextColorConfig uses a custom
     * {@code fromYaml()} parsing pattern for nested element/avoidance profiles.
     *
     * @return The loaded config, or a disabled default if loading fails
     */
    @SuppressWarnings("unchecked")
    private CombatTextColorConfig loadCombatTextColorConfig() {
        Path configPath = configDir.resolve("combat-text.yml");

        copyDefaultIfMissing("combat-text.yml");

        try {
            if (Files.exists(configPath)) {
                Yaml yaml = new Yaml();
                try (InputStream input = Files.newInputStream(configPath)) {
                    Object loaded = yaml.load(input);
                    if (loaded instanceof java.util.Map) {
                        return CombatTextColorConfig.fromYaml((java.util.Map<String, Object>) loaded);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e).log("Failed to load combat-text.yml, using defaults");
        }

        return CombatTextColorConfig.createDefault();
    }

    // =========================================================================
    // GEAR CONFIGURATION
    // =========================================================================

    /**
     * Loads gear configuration files.
     *
     * <p>Called during plugin initialization after main config is loaded.
     *
     * @throws ConfigurationException if loading fails
     */
    public void loadGearConfigs() {
        // Copy defaults if not present
        copyDefaultIfMissing("gear-balance.yml");
        copyDefaultIfMissing("gear-modifiers.yml");
        copyDefaultIfMissing("loot-items.yml");
        copyDefaultIfMissing("loot-discovery.yml");
        copyDefaultIfMissing("tooltip.yml");
        copyDefaultIfMissing("vanilla-conversion.yml");

        // Load configs using the gear config loader
        Path balancePath = configDir.resolve("gear-balance.yml");
        Path modifiersPath = configDir.resolve("gear-modifiers.yml");
        Path lootItemsPath = configDir.resolve("loot-items.yml");
        Path lootDiscoveryPath = configDir.resolve("loot-discovery.yml");
        Path tooltipPath = configDir.resolve("tooltip.yml");
        Path vanillaConversionPath = configDir.resolve("vanilla-conversion.yml");

        if (Files.exists(balancePath)) {
            this.gearBalanceConfig = gearConfigLoader.loadBalanceConfig(balancePath);
        } else {
            this.gearBalanceConfig = gearConfigLoader.loadDefaultBalanceConfig();
        }

        if (Files.exists(modifiersPath)) {
            this.modifierConfig = gearConfigLoader.loadModifierConfig(modifiersPath);
        } else {
            this.modifierConfig = gearConfigLoader.loadDefaultModifierConfig();
        }

        // Load loot items config (legacy, kept for backward compatibility)
        this.lootItemsConfig = loadLootItemsConfig(lootItemsPath);

        // Load loot discovery config (new dynamic system)
        this.lootDiscoveryConfig = loadLootDiscoveryConfig(lootDiscoveryPath);

        // Load tooltip config
        if (Files.exists(tooltipPath)) {
            this.tooltipConfig = gearConfigLoader.loadTooltipConfig(tooltipPath);
        } else {
            this.tooltipConfig = TooltipConfig.defaults();
        }

        // Load vanilla conversion config
        this.vanillaConversionConfig = loadVanillaConversionConfig(vanillaConversionPath);

        LOGGER.at(Level.INFO).log("Gear configs loaded successfully");
    }

    private VanillaConversionConfig loadVanillaConversionConfig(Path path) {
        try {
            if (Files.exists(path)) {
                LoaderOptions loaderOptions = new LoaderOptions();
                Yaml yaml = new Yaml(new Constructor(VanillaConversionConfig.class, loaderOptions));
                try (InputStream input = Files.newInputStream(path)) {
                    VanillaConversionConfig loaded = yaml.loadAs(input, VanillaConversionConfig.class);
                    if (loaded != null) {
                        loaded.validate();
                        LOGGER.at(Level.INFO).log("Vanilla conversion config loaded: enabled=%s, sources=[mob=%s, chest=%s, craft=%s]",
                                loaded.isEnabled() ? "yes" : "no",
                                loaded.getSources().isMobDrops() ? "yes" : "no",
                                loaded.getSources().isChestLoot() ? "yes" : "no",
                                loaded.getSources().isCrafting() ? "yes" : "no");
                        return loaded;
                    }
                }
            }
        } catch (VanillaConversionConfig.ConfigValidationException e) {
            LOGGER.at(Level.SEVERE).log("Vanilla conversion config validation failed: %s", e.getMessage());
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e).log("Failed to load vanilla-conversion.yml, using defaults");
        }

        return new VanillaConversionConfig();
    }

    private LootItemsConfig loadLootItemsConfig(Path path) {
        try {
            if (Files.exists(path)) {
                LoaderOptions loaderOptions = new LoaderOptions();
                Yaml yaml = new Yaml(new Constructor(LootItemsConfig.class, loaderOptions));
                try (InputStream input = Files.newInputStream(path)) {
                    LootItemsConfig loaded = yaml.loadAs(input, LootItemsConfig.class);
                    if (loaded != null) {
                        loaded.validate();
                        LOGGER.at(Level.INFO).log("Loot items config loaded: %d slots configured",
                                loaded.getSlots().size());
                        return loaded;
                    }
                }
            }
        } catch (LootItemsConfig.ConfigValidationException e) {
            LOGGER.at(Level.SEVERE).log("Loot items config validation failed: %s", e.getMessage());
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e).log("Failed to load loot-items.yml, using defaults");
        }

        return LootItemsConfig.createDefaults();
    }

    private LootDiscoveryConfig loadLootDiscoveryConfig(Path path) {
        try {
            if (Files.exists(path)) {
                LoaderOptions loaderOptions = new LoaderOptions();
                Yaml yaml = new Yaml(new Constructor(LootDiscoveryConfig.class, loaderOptions));
                try (InputStream input = Files.newInputStream(path)) {
                    LootDiscoveryConfig loaded = yaml.loadAs(input, LootDiscoveryConfig.class);
                    if (loaded != null) {
                        loaded.validate();
                        LOGGER.at(Level.INFO).log("Loot discovery config loaded: enabled=%s, blacklist=%d items, %d mods",
                                loaded.isEnabled() ? "yes" : "no",
                                loaded.getBlacklistItems().size(),
                                loaded.getBlacklistMods().size());
                        return loaded;
                    }
                }
            }
        } catch (LootDiscoveryConfig.ConfigValidationException e) {
            LOGGER.at(Level.SEVERE).log("Loot discovery config validation failed: %s", e.getMessage());
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e).log("Failed to load loot-discovery.yml, using defaults");
        }

        return LootDiscoveryConfig.createDefaults();
    }

    /** Called by /rpg reload command. */
    public void reloadGearConfigs() {
        loadGearConfigs();
        // Notify listeners of config change
        // (Implementation depends on existing event system)
    }

    /** @throws IllegalStateException if gear configs not loaded */
    public GearBalanceConfig getGearBalanceConfig() {
        if (gearBalanceConfig == null) {
            throw new IllegalStateException("Gear balance config not loaded");
        }
        return gearBalanceConfig;
    }

    /** @throws IllegalStateException if gear configs not loaded */
    public ModifierConfig getModifierConfig() {
        if (modifierConfig == null) {
            throw new IllegalStateException("Modifier config not loaded");
        }
        return modifierConfig;
    }

    /**
     * @throws IllegalStateException if gear configs not loaded
     * @deprecated Use getLootDiscoveryConfig() for the new dynamic system
     */
    @Deprecated
    public LootItemsConfig getLootItemsConfig() {
        if (lootItemsConfig == null) {
            throw new IllegalStateException("Loot items config not loaded");
        }
        return lootItemsConfig;
    }

    /**
     * Dynamic item discovery from Hytale's asset registry, replacing static loot-items.yml.
     *
     * @throws IllegalStateException if gear configs not loaded
     */
    public LootDiscoveryConfig getLootDiscoveryConfig() {
        if (lootDiscoveryConfig == null) {
            throw new IllegalStateException("Loot discovery config not loaded");
        }
        return lootDiscoveryConfig;
    }

    /** @throws IllegalStateException if gear configs not loaded */
    public TooltipConfig getTooltipConfig() {
        if (tooltipConfig == null) {
            throw new IllegalStateException("Tooltip config not loaded");
        }
        return tooltipConfig;
    }

    /** @throws IllegalStateException if gear configs not loaded */
    public VanillaConversionConfig getVanillaConversionConfig() {
        if (vanillaConversionConfig == null) {
            throw new IllegalStateException("Vanilla conversion config not loaded");
        }
        return vanillaConversionConfig;
    }

    /** Lazy-loads container loot config. Controls vanilla weapon/armor replacement in containers. */
    public ContainerLootConfig getContainerLootConfig() {
        if (containerLootConfig == null) {
            // Lazy-load container loot config
            try {
                copyDefaultIfMissing("container-loot.yml");
                containerLootConfig = loadConfig(
                    "container-loot.yml",
                    ContainerLootConfig.class,
                    ContainerLootConfig.createDefaults()
                );
            } catch (IOException e) {
                LOGGER.at(Level.WARNING).withCause(e).log("Failed to load container-loot.yml, using defaults");
                containerLootConfig = ContainerLootConfig.createDefaults();
            }
        }
        return containerLootConfig;
    }

    private void copyDefaultIfMissing(String filename) {
        Path targetPath = configDir.resolve(filename);
        if (!Files.exists(targetPath)) {
            try (InputStream is = getClass().getClassLoader()
                    .getResourceAsStream("config/" + filename)) {
                if (is == null) {
                    LOGGER.at(Level.WARNING).log("Default resource not found: config/%s", filename);
                    return;
                }
                Files.createDirectories(targetPath.getParent());
                Files.copy(is, targetPath);
                LOGGER.at(Level.INFO).log("Created default config: %s", filename);
            } catch (IOException e) {
                LOGGER.at(Level.SEVERE).withCause(e).log("Failed to copy default config: %s", filename);
            }
        }
    }
}