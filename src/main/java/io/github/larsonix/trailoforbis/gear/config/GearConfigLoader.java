package io.github.larsonix.trailoforbis.gear.config;

import io.github.larsonix.trailoforbis.attributes.AttributeType;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig.*;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig.*;
import io.github.larsonix.trailoforbis.gear.model.ArmorMaterial;
import io.github.larsonix.trailoforbis.gear.model.EquipmentType;
import io.github.larsonix.trailoforbis.gear.model.EquipmentType.ArmorSlot;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.gear.tooltip.TooltipConfig;

import com.hypixel.hytale.logger.HytaleLogger;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;

/**
 * Loads and validates gear configuration from YAML files.
 *
 * <p>This class handles:
 * <ul>
 *   <li>Loading gear-balance.yml and gear-modifiers.yml</li>
 *   <li>Parsing YAML into strongly-typed config objects</li>
 *   <li>Validating all values and relationships</li>
 *   <li>Providing clear error messages for invalid configs</li>
 * </ul>
 *
 * <p>Fail-fast behavior: Any validation error throws {@link ConfigurationException}.
 */
public final class GearConfigLoader {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final String BALANCE_CONFIG_FILE = "gear-balance.yml";
    private static final String MODIFIER_CONFIG_FILE = "gear-modifiers.yml";
    private static final String TOOLTIP_CONFIG_FILE = "tooltip.yml";
    private static final String EQUIPMENT_STATS_CONFIG_FILE = "equipment-stats.yml";

    private final Yaml yaml = new Yaml();
    private final Path configDirectory;

    // Cached configs (populated by loadAll())
    private GearBalanceConfig balanceConfig;
    private ModifierConfig modifierConfig;
    private TooltipConfig tooltipConfig;
    private EquipmentStatConfig equipmentStatConfig;

    public GearConfigLoader() {
        this(Path.of("config"));
    }

    public GearConfigLoader(Path configDirectory) {
        this.configDirectory = Objects.requireNonNull(configDirectory);
    }

    // =========================================================================
    // CONVENIENCE METHODS (used by GearManager)
    // =========================================================================

    /**
     * Loads all gear configuration files.
     *
     * <p>Loads both gear-balance.yml and gear-modifiers.yml from the config
     * directory, or from classpath resources if files don't exist.
     *
     * @throws ConfigurationException if any config fails to load
     */
    public void loadAll() {
        Path balancePath = configDirectory.resolve(BALANCE_CONFIG_FILE);
        Path modifierPath = configDirectory.resolve(MODIFIER_CONFIG_FILE);
        Path tooltipPath = configDirectory.resolve(TOOLTIP_CONFIG_FILE);
        Path equipmentStatsPath = configDirectory.resolve(EQUIPMENT_STATS_CONFIG_FILE);

        // Load balance config
        if (Files.exists(balancePath)) {
            this.balanceConfig = loadBalanceConfig(balancePath);
        } else {
            LOGGER.at(Level.INFO).log("Using default gear-balance.yml from resources");
            this.balanceConfig = loadDefaultBalanceConfig();
        }

        // Load modifier config
        if (Files.exists(modifierPath)) {
            this.modifierConfig = loadModifierConfig(modifierPath);
        } else {
            LOGGER.at(Level.INFO).log("Using default gear-modifiers.yml from resources");
            this.modifierConfig = loadDefaultModifierConfig();
        }

        // Load tooltip config
        if (Files.exists(tooltipPath)) {
            this.tooltipConfig = loadTooltipConfig(tooltipPath);
        } else {
            LOGGER.at(Level.INFO).log("Using default tooltip.yml from resources");
            this.tooltipConfig = loadDefaultTooltipConfig();
        }

        // Load equipment stats config
        if (Files.exists(equipmentStatsPath)) {
            this.equipmentStatConfig = loadEquipmentStatConfig(equipmentStatsPath);
        } else {
            LOGGER.at(Level.INFO).log("Using default equipment-stats.yml from resources");
            this.equipmentStatConfig = loadDefaultEquipmentStatConfig();
        }

        // Perform cross-file validation
        validateCrossReferences();

        LOGGER.at(Level.INFO).log("All gear configs loaded successfully");
    }

    /**
     * @throws IllegalStateException if loadAll() hasn't been called
     */
    public GearBalanceConfig getBalanceConfig() {
        if (balanceConfig == null) {
            throw new IllegalStateException("Balance config not loaded. Call loadAll() first.");
        }
        return balanceConfig;
    }

    /**
     * @throws IllegalStateException if loadAll() hasn't been called
     */
    public ModifierConfig getModifierConfig() {
        if (modifierConfig == null) {
            throw new IllegalStateException("Modifier config not loaded. Call loadAll() first.");
        }
        return modifierConfig;
    }

    /**
     * @throws IllegalStateException if loadAll() hasn't been called
     */
    public TooltipConfig getTooltipConfig() {
        if (tooltipConfig == null) {
            throw new IllegalStateException("Tooltip config not loaded. Call loadAll() first.");
        }
        return tooltipConfig;
    }

    /**
     * @throws IllegalStateException if loadAll() hasn't been called
     */
    public EquipmentStatConfig getEquipmentStatConfig() {
        if (equipmentStatConfig == null) {
            throw new IllegalStateException("Equipment stat config not loaded. Call loadAll() first.");
        }
        return equipmentStatConfig;
    }

    /**
     * Validates cross-references between balance and modifier configs.
     *
     * <p>Performs the following validation checks:
     * <ul>
     *   <li>Modifier slot restrictions reference valid slots from balance config</li>
     *   <li>No duplicate modifier IDs between prefixes and suffixes</li>
     *   <li>Required attributes are valid AttributeType values</li>
     *   <li>Weight categories referenced in modifiers exist in balance config</li>
     * </ul>
     *
     * @throws ConfigurationException if any cross-reference validation fails
     */
    private void validateCrossReferences() {
        List<String> errors = new ArrayList<>();
        Set<String> validSlots = balanceConfig.slotWeights().keySet();

        // Check for duplicate modifier IDs between prefixes and suffixes
        Set<String> prefixIds = modifierConfig.prefixes().keySet();
        Set<String> suffixIds = modifierConfig.suffixes().keySet();
        Set<String> duplicates = new HashSet<>(prefixIds);
        duplicates.retainAll(suffixIds);
        if (!duplicates.isEmpty()) {
            errors.add("Duplicate modifier IDs found in both prefixes and suffixes: " + duplicates);
        }

        // Validate prefix slot restrictions
        for (var modifier : modifierConfig.prefixList()) {
            validateModifierSlots(modifier, validSlots, "prefix", errors);
            if (modifier.requiredAttribute() != null) {
                LOGGER.at(Level.FINE).log("Prefix %s requires attribute %s",
                        modifier.id(), modifier.requiredAttribute());
            }
        }

        // Validate suffix slot restrictions
        for (var modifier : modifierConfig.suffixList()) {
            validateModifierSlots(modifier, validSlots, "suffix", errors);
            if (modifier.requiredAttribute() != null) {
                LOGGER.at(Level.FINE).log("Suffix %s requires attribute %s",
                        modifier.id(), modifier.requiredAttribute());
            }
        }

        // Validate that at least some modifiers exist for each slot
        for (String slot : validSlots) {
            if (modifierConfig.prefixesForSlot(slot).isEmpty() &&
                modifierConfig.suffixesForSlot(slot).isEmpty()) {
                LOGGER.at(Level.WARNING).log("No modifiers available for slot '%s' - gear in this slot will have no modifiers", slot);
            }
        }

        // Throw if any errors found
        if (!errors.isEmpty()) {
            throw new ConfigurationException(
                "Cross-reference validation failed:\n  - " + String.join("\n  - ", errors));
        }

        LOGGER.at(Level.FINE).log("Cross-reference validation passed");
    }

    private void validateModifierSlots(
            ModifierDefinition modifier,
            Set<String> validSlots,
            String type,
            List<String> errors
    ) {
        if (modifier.allowedSlots() == null) {
            return; // null means all slots allowed
        }
        for (String slot : modifier.allowedSlots()) {
            if (!validSlots.contains(slot)) {
                errors.add(String.format(
                    "%s '%s' references invalid slot '%s' (valid: %s)",
                    type, modifier.id(), slot, validSlots));
            }
        }
    }

    // =========================================================================
    // FILE-BASED LOADING
    // =========================================================================

    /**
     * Loads the gear balance configuration from file.
     *
     * @throws ConfigurationException if loading or validation fails
     */
    public GearBalanceConfig loadBalanceConfig(Path configPath) {
        LOGGER.at(Level.INFO).log("Loading gear balance config from: %s", configPath);

        try (InputStream is = Files.newInputStream(configPath)) {
            Map<String, Object> data = yaml.load(is);
            if (data == null) {
                throw new ConfigurationException("Config file is empty: " + configPath);
            }
            return parseBalanceConfig(data, configPath.toString());
        } catch (IOException e) {
            throw new ConfigurationException("Failed to read config file: " + configPath, e);
        } catch (ConfigurationException e) {
            throw e;
        } catch (Exception e) {
            throw new ConfigurationException("Failed to parse config: " + configPath, e);
        }
    }

    /**
     * Loads the modifier configuration from file.
     *
     * @throws ConfigurationException if loading or validation fails
     */
    public ModifierConfig loadModifierConfig(Path configPath) {
        LOGGER.at(Level.INFO).log("Loading modifier config from: %s", configPath);

        try (InputStream is = Files.newInputStream(configPath)) {
            Map<String, Object> data = yaml.load(is);
            if (data == null) {
                throw new ConfigurationException("Config file is empty: " + configPath);
            }
            return parseModifierConfig(data, configPath.toString());
        } catch (IOException e) {
            throw new ConfigurationException("Failed to read config file: " + configPath, e);
        } catch (ConfigurationException e) {
            throw e;
        } catch (Exception e) {
            throw new ConfigurationException("Failed to parse config: " + configPath, e);
        }
    }

    public GearBalanceConfig loadDefaultBalanceConfig() {
        try (InputStream is = getClass().getResourceAsStream("/config/gear-balance.yml")) {
            if (is == null) {
                throw new ConfigurationException("Default gear-balance.yml not found in resources");
            }
            Map<String, Object> data = yaml.load(is);
            if (data == null) {
                throw new ConfigurationException("Default gear-balance.yml is empty");
            }
            return parseBalanceConfig(data, "default:gear-balance.yml");
        } catch (IOException e) {
            throw new ConfigurationException("Failed to read default config", e);
        }
    }

    public ModifierConfig loadDefaultModifierConfig() {
        try (InputStream is = getClass().getResourceAsStream("/config/gear-modifiers.yml")) {
            if (is == null) {
                throw new ConfigurationException("Default gear-modifiers.yml not found in resources");
            }
            Map<String, Object> data = yaml.load(is);
            if (data == null) {
                throw new ConfigurationException("Default gear-modifiers.yml is empty");
            }
            return parseModifierConfig(data, "default:gear-modifiers.yml");
        } catch (IOException e) {
            throw new ConfigurationException("Failed to read default config", e);
        }
    }

    /**
     * Loads the tooltip configuration from file.
     *
     * @throws ConfigurationException if loading or validation fails
     */
    public TooltipConfig loadTooltipConfig(Path configPath) {
        LOGGER.at(Level.INFO).log("Loading tooltip config from: %s", configPath);

        try (InputStream is = Files.newInputStream(configPath)) {
            Map<String, Object> data = yaml.load(is);
            if (data == null) {
                LOGGER.at(Level.WARNING).log("Tooltip config is empty, using defaults");
                return TooltipConfig.defaults();
            }
            return TooltipConfig.fromYaml(data);
        } catch (IOException e) {
            throw new ConfigurationException("Failed to read config file: " + configPath, e);
        } catch (Exception e) {
            throw new ConfigurationException("Failed to parse config: " + configPath, e);
        }
    }

    public TooltipConfig loadDefaultTooltipConfig() {
        try (InputStream is = getClass().getResourceAsStream("/config/tooltip.yml")) {
            if (is == null) {
                LOGGER.at(Level.INFO).log("Default tooltip.yml not found, using built-in defaults");
                return TooltipConfig.defaults();
            }
            Map<String, Object> data = yaml.load(is);
            if (data == null) {
                return TooltipConfig.defaults();
            }
            return TooltipConfig.fromYaml(data);
        } catch (IOException e) {
            LOGGER.at(Level.WARNING).log("Failed to read default tooltip config, using built-in defaults");
            return TooltipConfig.defaults();
        }
    }

    /**
     * Loads the equipment stat configuration from file.
     *
     * @throws ConfigurationException if loading or validation fails
     */
    public EquipmentStatConfig loadEquipmentStatConfig(Path configPath) {
        LOGGER.at(Level.INFO).log("Loading equipment stat config from: %s", configPath);

        try (InputStream is = Files.newInputStream(configPath)) {
            Map<String, Object> data = yaml.load(is);
            if (data == null) {
                LOGGER.at(Level.WARNING).log("Equipment stat config is empty, using unrestricted");
                return EquipmentStatConfig.unrestricted();
            }
            return parseEquipmentStatConfig(data, configPath.toString());
        } catch (IOException e) {
            throw new ConfigurationException("Failed to read config file: " + configPath, e);
        } catch (ConfigurationException e) {
            throw e;
        } catch (Exception e) {
            throw new ConfigurationException("Failed to parse config: " + configPath, e);
        }
    }

    public EquipmentStatConfig loadDefaultEquipmentStatConfig() {
        try (InputStream is = getClass().getResourceAsStream("/config/equipment-stats.yml")) {
            if (is == null) {
                LOGGER.at(Level.INFO).log("Default equipment-stats.yml not found, using unrestricted");
                return EquipmentStatConfig.unrestricted();
            }
            Map<String, Object> data = yaml.load(is);
            if (data == null) {
                return EquipmentStatConfig.unrestricted();
            }
            return parseEquipmentStatConfig(data, "default:equipment-stats.yml");
        } catch (IOException e) {
            LOGGER.at(Level.WARNING).log("Failed to read default equipment stat config, using unrestricted");
            return EquipmentStatConfig.unrestricted();
        }
    }

    // =========================================================================
    // BALANCE CONFIG PARSING
    // =========================================================================

    @SuppressWarnings("unchecked")
    private GearBalanceConfig parseBalanceConfig(Map<String, Object> data, String source) {
        ConfigContext ctx = new ConfigContext(source);

        // Power scaling
        Map<String, Object> powerScaling = ctx.getMap(data, "power_scaling");
        double gearPowerRatio = ctx.getDouble(powerScaling, "gear_power_ratio");
        Map<String, Double> slotWeights = ctx.getDoubleMap(powerScaling, "slot_weights");
        double levelScalingFactor = ctx.getDouble(powerScaling, "level_scaling_factor");

        // Validate slot weights sum to ~1.0
        double weightSum = slotWeights.values().stream().mapToDouble(Double::doubleValue).sum();
        if (Math.abs(weightSum - 1.0) > 0.01) {
            ctx.warn("slot_weights sum to %.3f, expected 1.0", weightSum);
        }

        // Rarity configs
        Map<String, Object> raritySection = ctx.getMap(data, "rarity");
        Map<GearRarity, RarityConfig> rarityConfigs = new EnumMap<>(GearRarity.class);

        for (GearRarity rarity : GearRarity.values()) {
            String key = rarity.name().toLowerCase();
            Map<String, Object> rarityData = ctx.getMap(raritySection, key);
            rarityConfigs.put(rarity, parseRarityConfig(rarityData, rarity, ctx));
        }

        // Quality config
        Map<String, Object> qualitySection = ctx.getMap(data, "quality");
        QualityConfig quality = parseQualityConfig(qualitySection, ctx);

        // Attribute requirements
        Map<String, Object> attrReqSection = ctx.getMap(data, "attribute_requirements");
        AttributeRequirementsConfig attrReq = parseAttributeRequirements(attrReqSection, ctx);

        // Modifier scaling
        Map<String, Object> modScalingSection = ctx.getMap(data, "modifier_scaling");
        ModifierScalingConfig modScaling = parseModifierScaling(modScalingSection, ctx);

        // Loot
        Map<String, Object> lootSection = ctx.getMap(data, "loot");
        LootConfig loot = parseLootConfig(lootSection, ctx);

        // Stone drops
        Map<String, Object> stoneSection = ctx.getMap(data, "stone_drops");
        StoneDropConfig stoneDrops = parseStoneDropConfig(stoneSection, ctx);

        // Exponential scaling (optional, defaults to disabled for backward compat)
        ExponentialScalingConfig expScaling = ExponentialScalingConfig.DISABLED;
        if (data.containsKey("exponential_scaling")) {
            Map<String, Object> expData = ctx.getMap(data, "exponential_scaling");
            expScaling = parseExponentialScaling(expData, ctx);
        }

        // Implicit damage (optional, defaults to disabled for backward compat)
        ImplicitDamageConfig implicitDamage = ImplicitDamageConfig.DISABLED;
        if (data.containsKey("implicit_damage")) {
            Map<String, Object> implicitData = ctx.getMap(data, "implicit_damage");
            implicitDamage = parseImplicitDamageConfig(implicitData, ctx);
        }

        // Implicit defense (optional, defaults to disabled)
        ImplicitDefenseConfig implicitDefense = ImplicitDefenseConfig.DISABLED;
        if (data.containsKey("implicit_defense")) {
            Map<String, Object> defenseData = ctx.getMap(data, "implicit_defense");
            implicitDefense = parseImplicitDefenseConfig(defenseData, ctx);
        }

        // Vanilla weapon profiles (optional, defaults to enabled)
        VanillaWeaponProfilesConfig vanillaProfiles = VanillaWeaponProfilesConfig.DEFAULT;
        if (data.containsKey("vanilla_weapon_profiles")) {
            Map<String, Object> profilesData = ctx.getMap(data, "vanilla_weapon_profiles");
            vanillaProfiles = parseVanillaWeaponProfilesConfig(profilesData, ctx);
        }

        // Level blending (optional, defaults to disabled)
        LevelBlendingConfig levelBlending = LevelBlendingConfig.DISABLED;
        if (data.containsKey("level_blending")) {
            Map<String, Object> blendData = ctx.getMap(data, "level_blending");
            levelBlending = parseLevelBlendingConfig(blendData, ctx);
        }

        LOGGER.at(Level.INFO).log("Loaded gear balance config: %d rarities, %d slot weights, exp_scaling=%s, implicit_dmg=%s, implicit_def=%s, vanilla_profiles=%s, level_blending=%s",
                rarityConfigs.size(), slotWeights.size(),
                expScaling.enabled() ? "ON" : "OFF",
                implicitDamage.enabled() ? "ON" : "OFF",
                implicitDefense.enabled() ? "ON" : "OFF",
                vanillaProfiles.enabled() ? "ON" : "OFF",
                levelBlending.enabled() ? "ON" : "OFF");

        return new GearBalanceConfig(
                gearPowerRatio,
                slotWeights,
                levelScalingFactor,
                rarityConfigs,
                quality,
                attrReq,
                modScaling,
                loot,
                stoneDrops,
                expScaling,
                implicitDamage,
                implicitDefense,
                vanillaProfiles,
                levelBlending
        );
    }

    private ExponentialScalingConfig parseExponentialScaling(
            Map<String, Object> data,
            ConfigContext ctx
    ) {
        boolean enabled = ctx.getBoolean(data, "enabled");
        double exponent = ctx.getDouble(data, "exponent");
        int referenceLevel = ctx.getInt(data, "reference_level");
        double referenceMultiplier = ctx.getDouble(data, "reference_multiplier");
        double baseMultiplier = ctx.getDouble(data, "base_multiplier");
        int softCapLevel = ctx.getInt(data, "soft_cap_level");
        double softCapDivisor = ctx.getDouble(data, "soft_cap_divisor");

        return new ExponentialScalingConfig(
                enabled,
                exponent,
                referenceLevel,
                referenceMultiplier,
                baseMultiplier,
                softCapLevel,
                softCapDivisor
        );
    }

    private LevelBlendingConfig parseLevelBlendingConfig(
            Map<String, Object> data,
            ConfigContext ctx
    ) {
        boolean enabled = ctx.getBoolean(data, "enabled");
        double pullFactor = ctx.getDouble(data, "pull_factor");
        int maxOffset = ctx.getInt(data, "max_offset");
        int variance = ctx.getInt(data, "variance");

        return new LevelBlendingConfig(enabled, pullFactor, maxOffset, variance);
    }

    private ImplicitDamageConfig parseImplicitDamageConfig(
            Map<String, Object> data,
            ConfigContext ctx
    ) {
        boolean enabled = ctx.getBoolean(data, "enabled");
        double baseMin = ctx.getDouble(data, "base_min");
        double baseMax = ctx.getDouble(data, "base_max");
        double scaleFactor = ctx.getDouble(data, "scale_factor");
        double twoHandedMultiplier = ctx.getDouble(data, "two_handed_multiplier");

        LOGGER.at(Level.FINE).log(
                "Parsed implicit damage config: enabled=%s, base=%.1f-%.1f, scale=%.1f, 2H=%.1fx",
                enabled, baseMin, baseMax, scaleFactor, twoHandedMultiplier);

        return new ImplicitDamageConfig(enabled, baseMin, baseMax, scaleFactor, twoHandedMultiplier);
    }

    @SuppressWarnings("unchecked")
    private ImplicitDefenseConfig parseImplicitDefenseConfig(
            Map<String, Object> data,
            ConfigContext ctx
    ) {
        boolean enabled = ctx.getBoolean(data, "enabled");

        // Parse materials map
        Map<String, Object> materialsData = ctx.getMap(data, "materials");
        Map<ArmorMaterial, MaterialDefenseConfig> materials = new EnumMap<>(ArmorMaterial.class);

        for (Map.Entry<String, Object> entry : materialsData.entrySet()) {
            String materialKey = entry.getKey().toUpperCase();
            ArmorMaterial material;
            try {
                material = ArmorMaterial.valueOf(materialKey);
            } catch (IllegalArgumentException e) {
                throw new ConfigurationException(
                    "Unknown armor material in implicit_defense.materials: " + entry.getKey());
            }
            Map<String, Object> matData = (Map<String, Object>) entry.getValue();
            materials.put(material, parseMaterialDefenseConfig(matData, ctx));
        }

        // Parse slot multipliers
        Map<String, Object> slotsData = ctx.getMap(data, "slot_multipliers");
        Map<ArmorSlot, Double> slotMultipliers = new EnumMap<>(ArmorSlot.class);
        for (Map.Entry<String, Object> entry : slotsData.entrySet()) {
            ArmorSlot slot = ArmorSlot.fromSlotName(entry.getKey())
                .orElseThrow(() -> new ConfigurationException(
                    "Unknown armor slot in implicit_defense.slot_multipliers: " + entry.getKey()));
            slotMultipliers.put(slot, ctx.toDouble(entry.getValue(), "slot_multipliers." + entry.getKey()));
        }

        // Parse shield config
        Map<String, Object> shieldData = ctx.getMap(data, "shield");
        MaterialDefenseConfig shield = parseMaterialDefenseConfig(shieldData, ctx);

        LOGGER.at(Level.FINE).log(
                "Parsed implicit defense config: enabled=%s, %d materials, %d slot multipliers, shield=%s",
                enabled, materials.size(), slotMultipliers.size(), shield.stat());

        return new ImplicitDefenseConfig(enabled, materials, slotMultipliers, shield);
    }

    private MaterialDefenseConfig parseMaterialDefenseConfig(
            Map<String, Object> data,
            ConfigContext ctx
    ) {
        String stat = ctx.getString(data, "stat");
        double baseMin = ctx.getDouble(data, "base_min");
        double baseMax = ctx.getDouble(data, "base_max");
        double scaleFactor = ctx.getDouble(data, "scale_factor");
        return new MaterialDefenseConfig(stat, baseMin, baseMax, scaleFactor);
    }

    private VanillaWeaponProfilesConfig parseVanillaWeaponProfilesConfig(
            Map<String, Object> data,
            ConfigContext ctx
    ) {
        boolean enabled = ctx.getBoolean(data, "enabled");
        double fallbackRef = ctx.getDouble(data, "fallback_reference_damage");
        double minMult = ctx.getDoubleOrDefault(data, "min_multiplier", 0.3);
        double maxMult = ctx.getDoubleOrDefault(data, "max_multiplier", 3.0);

        // Parse default family profile (optional, falls back to FamilyAttackProfile.DEFAULT)
        FamilyAttackProfile defaultProfile = FamilyAttackProfile.DEFAULT;
        if (data.containsKey("default_profile")) {
            defaultProfile = parseFamilyAttackProfile(ctx.getMap(data, "default_profile"), ctx);
        }

        // Parse per-family profiles (optional)
        Map<String, FamilyAttackProfile> familyProfiles = new HashMap<>();
        if (data.containsKey("family_profiles")) {
            Map<String, Object> familiesData = ctx.getMap(data, "family_profiles");
            for (Map.Entry<String, Object> entry : familiesData.entrySet()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> profileData = (Map<String, Object>) entry.getValue();
                familyProfiles.put(entry.getKey(), parseFamilyAttackProfile(profileData, ctx));
            }
        }

        LOGGER.at(Level.FINE).log(
                "Parsed vanilla weapon profiles config: enabled=%s, fallback_ref=%.1f, clamp=[%.2f, %.2f], %d family profiles",
                enabled, fallbackRef, minMult, maxMult, familyProfiles.size());

        return new VanillaWeaponProfilesConfig(
                enabled, fallbackRef, minMult, maxMult, defaultProfile, familyProfiles);
    }

    private FamilyAttackProfile parseFamilyAttackProfile(
            Map<String, Object> data,
            ConfigContext ctx
    ) {
        double backstabMult = ctx.getDouble(data, "backstab_multiplier");
        List<Double> normalMults = ctx.getDoubleList(data, "normal_multipliers");
        return new FamilyAttackProfile(normalMults, backstabMult);
    }

    private RarityConfig parseRarityConfig(
            Map<String, Object> data,
            GearRarity rarity,
            ConfigContext ctx
    ) {
        double statMultiplier = ctx.getDouble(data, "stat_multiplier");
        int maxModifiers = ctx.getInt(data, "max_modifiers");

        List<Integer> prefixRange = ctx.getIntList(data, "prefix_range");
        List<Integer> suffixRange = ctx.getIntList(data, "suffix_range");

        if (prefixRange.size() != 2 || suffixRange.size() != 2) {
            throw new ConfigurationException(
                "prefix_range and suffix_range must be [min, max] arrays for " + rarity);
        }

        double dropWeight = ctx.getDouble(data, "drop_weight");
        double minRollPercentile = ctx.getDoubleOrDefault(data, "min_roll_percentile", 0.0);

        int minPrefixes = prefixRange.get(0);
        int maxPrefixes = prefixRange.get(1);
        int minSuffixes = suffixRange.get(0);
        int maxSuffixes = suffixRange.get(1);

        // Validate that max_modifiers is achievable with the configured ranges
        int maxAchievable = maxPrefixes + maxSuffixes;
        if (maxAchievable < maxModifiers) {
            LOGGER.at(Level.WARNING).log(
                    "Rarity %s: max_modifiers=%d but max achievable is %d (maxPrefixes=%d + maxSuffixes=%d). "
                            + "Items will have fewer modifiers than expected.",
                    rarity, maxModifiers, maxAchievable, maxPrefixes, maxSuffixes);
        }

        // Validate that the distribution algorithm can find a valid range
        // The algorithm needs: minPrefixes <= (maxModifiers - maxSuffixes) and maxPrefixes >= (maxModifiers - minSuffixes)
        int minValidPrefixes = Math.max(minPrefixes, maxModifiers - maxSuffixes);
        int maxValidPrefixes = Math.min(maxPrefixes, maxModifiers - minSuffixes);
        if (minValidPrefixes > maxValidPrefixes) {
            LOGGER.at(Level.WARNING).log(
                    "Rarity %s: prefix/suffix ranges cannot satisfy max_modifiers=%d. "
                            + "prefix_range=[%d,%d], suffix_range=[%d,%d]. Distribution will be clamped.",
                    rarity, maxModifiers, minPrefixes, maxPrefixes, minSuffixes, maxSuffixes);
        }

        return new RarityConfig(
                statMultiplier,
                maxModifiers,
                minPrefixes,
                maxPrefixes,
                minSuffixes,
                maxSuffixes,
                dropWeight,
                minRollPercentile
        );
    }

    private QualityConfig parseQualityConfig(Map<String, Object> data, ConfigContext ctx) {
        int baseline = ctx.getInt(data, "baseline");
        int min = ctx.getInt(data, "min");
        int max = ctx.getInt(data, "max");
        int perfect = ctx.getInt(data, "perfect");

        Map<String, Object> distData = ctx.getMap(data, "drop_distribution");
        QualityDropDistribution dist = new QualityDropDistribution(
                ctx.getDouble(distData, "poor"),
                ctx.getDouble(distData, "below_average"),
                ctx.getDouble(distData, "normal"),
                ctx.getDouble(distData, "above_average"),
                ctx.getDouble(distData, "excellent"),
                ctx.getDouble(distData, "perfect")
        );

        return new QualityConfig(baseline, min, max, perfect, dist);
    }

    private AttributeRequirementsConfig parseAttributeRequirements(
            Map<String, Object> data,
            ConfigContext ctx
    ) {
        double levelToBaseRatio = ctx.getDouble(data, "level_to_base_ratio");
        int minLevel = ctx.getInt(data, "min_item_level_for_requirements");

        Map<String, Object> multipliersData = ctx.getMap(data, "rarity_multipliers");
        Map<GearRarity, Double> multipliers = new EnumMap<>(GearRarity.class);

        for (GearRarity rarity : GearRarity.values()) {
            String key = rarity.name().toLowerCase();
            double mult = ctx.getDouble(multipliersData, key);
            multipliers.put(rarity, mult);
        }

        return new AttributeRequirementsConfig(levelToBaseRatio, minLevel, multipliers);
    }

    private ModifierScalingConfig parseModifierScaling(
            Map<String, Object> data,
            ConfigContext ctx
    ) {
        double globalScale = ctx.getDouble(data, "global_scale_per_level");
        double rollVariance = ctx.getDouble(data, "roll_variance");

        Map<String, Object> categoriesData = ctx.getMap(data, "weight_categories");
        Map<String, Integer> categories = new HashMap<>();

        for (Map.Entry<String, Object> entry : categoriesData.entrySet()) {
            categories.put(entry.getKey().toLowerCase(),
                    ctx.toInt(entry.getValue(), "weight_categories." + entry.getKey()));
        }

        return new ModifierScalingConfig(globalScale, rollVariance, categories);
    }

    private LootConfig parseLootConfig(Map<String, Object> data, ConfigContext ctx) {
        double baseDropChance = ctx.getDouble(data, "base_drop_chance");
        double luckToRarity = ctx.getDouble(data, "wind_to_rarity_percent");

        Map<String, Object> distData = ctx.getMap(data, "distance_scaling");
        DistanceScalingConfig distScaling = new DistanceScalingConfig(
                ctx.getBoolean(distData, "enabled"),
                ctx.getInt(distData, "blocks_per_percent"),
                ctx.getDouble(distData, "max_bonus")
        );

        Map<String, Object> mobData = ctx.getMap(data, "mob_bonuses");
        Map<String, MobBonusConfig> mobBonuses = new HashMap<>();

        for (Map.Entry<String, Object> entry : mobData.entrySet()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> bonusData = (Map<String, Object>) entry.getValue();
            mobBonuses.put(entry.getKey().toLowerCase(), new MobBonusConfig(
                    ctx.getDouble(bonusData, "quantity_bonus"),
                    ctx.getDouble(bonusData, "rarity_bonus")
            ));
        }

        return new LootConfig(baseDropChance, luckToRarity, distScaling, mobBonuses);
    }

    private StoneDropConfig parseStoneDropConfig(Map<String, Object> data, ConfigContext ctx) {
        double baseChance = ctx.getDouble(data, "base_drop_chance");

        Map<String, Object> weightsData = ctx.getMap(data, "stone_weights");
        Map<String, Integer> weights = new HashMap<>();

        for (Map.Entry<String, Object> entry : weightsData.entrySet()) {
            weights.put(entry.getKey().toLowerCase(),
                    ctx.toInt(entry.getValue(), "stone_weights." + entry.getKey()));
        }

        return new StoneDropConfig(baseChance, weights);
    }

    // =========================================================================
    // MODIFIER CONFIG PARSING
    // =========================================================================

    @SuppressWarnings("unchecked")
    private ModifierConfig parseModifierConfig(Map<String, Object> data, String source) {
        ConfigContext ctx = new ConfigContext(source);

        Map<String, Object> prefixesData = ctx.getMap(data, "prefixes");
        Map<String, Object> suffixesData = ctx.getMap(data, "suffixes");

        Map<String, ModifierDefinition> prefixes = new HashMap<>();
        Map<String, ModifierDefinition> suffixes = new HashMap<>();

        // Parse prefixes
        for (Map.Entry<String, Object> entry : prefixesData.entrySet()) {
            String id = entry.getKey().toLowerCase();
            Map<String, Object> modData = (Map<String, Object>) entry.getValue();
            prefixes.put(id, parseModifierDefinition(id, modData, ctx));
        }

        // Parse suffixes
        for (Map.Entry<String, Object> entry : suffixesData.entrySet()) {
            String id = entry.getKey().toLowerCase();
            Map<String, Object> modData = (Map<String, Object>) entry.getValue();
            suffixes.put(id, parseModifierDefinition(id, modData, ctx));
        }

        LOGGER.at(Level.INFO).log("Loaded modifier config: %d prefixes, %d suffixes",
                prefixes.size(), suffixes.size());

        return new ModifierConfig(prefixes, suffixes);
    }

    private ModifierDefinition parseModifierDefinition(
            String id,
            Map<String, Object> data,
            ConfigContext ctx
    ) {
        String displayName = ctx.getString(data, "display_name");
        String stat = ctx.getString(data, "stat");
        StatType statType = StatType.fromString(ctx.getString(data, "stat_type"));
        double baseMin = ctx.getDouble(data, "base_min");
        double baseMax = ctx.getDouble(data, "base_max");
        double scalePerLevel = ctx.getDouble(data, "scale_per_level");
        int weight = ctx.getInt(data, "weight");

        // Optional: required attribute
        AttributeType requiredAttr = null;
        if (data.containsKey("required_attribute")) {
            String attrName = ctx.getString(data, "required_attribute");
            requiredAttr = AttributeType.fromString(attrName);
            if (requiredAttr == null) {
                throw new ConfigurationException(
                    "Invalid required_attribute '" + attrName + "' for modifier " + id);
            }
        }

        // Optional: allowed slots
        Set<String> allowedSlots = null;
        if (data.containsKey("allowed_slots")) {
            List<String> slotList = ctx.getStringList(data, "allowed_slots");
            allowedSlots = new HashSet<>(slotList);
        }

        return new ModifierDefinition(
                id,
                displayName,
                stat,
                statType,
                baseMin,
                baseMax,
                scalePerLevel,
                weight,
                requiredAttr,
                allowedSlots
        );
    }

    // =========================================================================
    // EQUIPMENT STAT CONFIG PARSING
    // =========================================================================

    @SuppressWarnings("unchecked")
    private EquipmentStatConfig parseEquipmentStatConfig(Map<String, Object> data, String source) {
        EquipmentStatConfig.Builder builder = EquipmentStatConfig.builder();

        // Parse weapons section
        if (data.containsKey("weapons")) {
            Map<String, Object> weaponsSection = (Map<String, Object>) data.get("weapons");
            parseWeaponProfiles(weaponsSection, builder);
        }

        // Parse armor materials section
        Map<String, Set<String>> materialSuffixes = new HashMap<>();
        if (data.containsKey("armor_materials")) {
            Map<String, Object> materialsSection = (Map<String, Object>) data.get("armor_materials");
            materialSuffixes = parseArmorMaterialSuffixes(materialsSection);
        }

        // Parse armor slots section
        Map<String, Set<String>> slotSuffixes = new HashMap<>();
        if (data.containsKey("armor_slots")) {
            Map<String, Object> slotsSection = (Map<String, Object>) data.get("armor_slots");
            slotSuffixes = parseArmorSlotSuffixes(slotsSection);
        }

        // Combine material + slot suffixes into armor equipment types
        buildArmorProfiles(builder, materialSuffixes, slotSuffixes);

        EquipmentStatConfig config = builder.build();
        LOGGER.at(Level.INFO).log("Loaded equipment stat config: %d equipment type profiles", config.size());
        return config;
    }

    @SuppressWarnings("unchecked")
    private void parseWeaponProfiles(Map<String, Object> weaponsSection, EquipmentStatConfig.Builder builder) {
        for (Map.Entry<String, Object> entry : weaponsSection.entrySet()) {
            String weaponKey = entry.getKey().toLowerCase();
            Map<String, Object> weaponData = (Map<String, Object>) entry.getValue();

            Set<String> prefixes = parseStringListToSet(weaponData.get("prefixes"));
            Set<String> suffixes = parseStringListToSet(weaponData.get("suffixes"));

            // Map weapon key to EquipmentType
            EquipmentType equipmentType = mapWeaponKeyToType(weaponKey);
            if (equipmentType != null) {
                builder.addProfile(equipmentType, prefixes, suffixes);
                LOGGER.at(Level.FINE).log("Loaded weapon profile: %s → %d prefixes, %d suffixes",
                        equipmentType, prefixes.size(), suffixes.size());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Set<String>> parseArmorMaterialSuffixes(Map<String, Object> materialsSection) {
        Map<String, Set<String>> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : materialsSection.entrySet()) {
            String materialKey = entry.getKey().toLowerCase();
            Map<String, Object> materialData = (Map<String, Object>) entry.getValue();

            Set<String> baseSuffixes = parseStringListToSet(materialData.get("base_suffixes"));
            result.put(materialKey, baseSuffixes);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Set<String>> parseArmorSlotSuffixes(Map<String, Object> slotsSection) {
        Map<String, Set<String>> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : slotsSection.entrySet()) {
            String slotKey = entry.getKey().toLowerCase();
            Map<String, Object> slotData = (Map<String, Object>) entry.getValue();

            Set<String> additionalSuffixes = parseStringListToSet(slotData.get("additional_suffixes"));
            result.put(slotKey, additionalSuffixes);
        }
        return result;
    }

    private void buildArmorProfiles(
            EquipmentStatConfig.Builder builder,
            Map<String, Set<String>> materialSuffixes,
            Map<String, Set<String>> slotSuffixes
    ) {
        // For each material/slot combination, create combined profile
        String[] materials = {"cloth", "leather", "plate", "wood", "special"};
        String[] slots = {"head", "chest", "legs", "hands"};

        for (String material : materials) {
            Set<String> baseSuffixes = materialSuffixes.getOrDefault(material, Set.of());

            for (String slot : slots) {
                Set<String> additionalSuffixes = slotSuffixes.getOrDefault(slot, Set.of());

                // Combine base + additional suffixes
                Set<String> combinedSuffixes = new HashSet<>(baseSuffixes);
                combinedSuffixes.addAll(additionalSuffixes);

                // Armor has no prefixes (no offensive stats)
                Set<String> prefixes = Set.of();

                // Map to EquipmentType
                EquipmentType equipmentType = mapArmorKeyToType(material, slot);
                if (equipmentType != null) {
                    builder.addProfile(equipmentType, prefixes, combinedSuffixes);
                    LOGGER.at(Level.FINE).log("Loaded armor profile: %s → %d suffixes",
                            equipmentType, combinedSuffixes.size());
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Set<String> parseStringListToSet(Object listObj) {
        if (listObj == null) {
            return Set.of();
        }
        if (!(listObj instanceof List)) {
            return Set.of();
        }
        List<?> list = (List<?>) listObj;
        Set<String> result = new HashSet<>();
        for (Object item : list) {
            result.add(item.toString().toLowerCase());
        }
        return result;
    }

    private EquipmentType mapWeaponKeyToType(String key) {
        return switch (key) {
            case "sword" -> EquipmentType.SWORD;
            case "dagger" -> EquipmentType.DAGGER;
            case "axe" -> EquipmentType.AXE;
            case "mace" -> EquipmentType.MACE;
            case "claws" -> EquipmentType.CLAWS;
            case "club" -> EquipmentType.CLUB;
            case "longsword" -> EquipmentType.LONGSWORD;
            case "battleaxe" -> EquipmentType.BATTLEAXE;
            case "spear" -> EquipmentType.SPEAR;
            case "shortbow" -> EquipmentType.SHORTBOW;
            case "crossbow" -> EquipmentType.CROSSBOW;
            case "blowgun" -> EquipmentType.BLOWGUN;
            case "bomb" -> EquipmentType.BOMB;
            case "dart" -> EquipmentType.DART;
            case "kunai" -> EquipmentType.KUNAI;
            case "staff" -> EquipmentType.STAFF;
            case "wand" -> EquipmentType.WAND;
            case "spellbook" -> EquipmentType.SPELLBOOK;
            case "shield" -> EquipmentType.SHIELD;
            default -> {
                LOGGER.at(Level.WARNING).log("Unknown weapon key in config: %s", key);
                yield null;
            }
        };
    }

    private EquipmentType mapArmorKeyToType(String material, String slot) {
        String key = material.toUpperCase() + "_" + slot.toUpperCase();
        try {
            return EquipmentType.valueOf(key);
        } catch (IllegalArgumentException e) {
            LOGGER.at(Level.WARNING).log("Unknown armor type: %s", key);
            return null;
        }
    }

    // =========================================================================
    // PARSING HELPERS
    // =========================================================================

    /**
     * Context for parsing with error tracking.
     */
    private static class ConfigContext {
        private final String source;

        ConfigContext(String source) {
            this.source = source;
        }

        void warn(String format, Object... args) {
            LOGGER.at(Level.WARNING).log("[%s] " + format, prepend(source, args));
        }

        private Object[] prepend(Object first, Object[] rest) {
            Object[] result = new Object[rest.length + 1];
            result[0] = first;
            System.arraycopy(rest, 0, result, 1, rest.length);
            return result;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> getMap(Map<String, Object> parent, String key) {
            Object value = parent.get(key);
            if (value == null) {
                throw new ConfigurationException("Missing required section: " + key);
            }
            if (!(value instanceof Map)) {
                throw new ConfigurationException(
                    "Expected map for " + key + ", got: " + value.getClass().getSimpleName());
            }
            return (Map<String, Object>) value;
        }

        String getString(Map<String, Object> parent, String key) {
            Object value = parent.get(key);
            if (value == null) {
                throw new ConfigurationException("Missing required field: " + key);
            }
            return value.toString();
        }

        double getDouble(Map<String, Object> parent, String key) {
            Object value = parent.get(key);
            if (value == null) {
                throw new ConfigurationException("Missing required field: " + key);
            }
            return toDouble(value, key);
        }

        double getDoubleOrDefault(Map<String, Object> parent, String key, double defaultValue) {
            Object value = parent.get(key);
            if (value == null) {
                return defaultValue;
            }
            return toDouble(value, key);
        }

        int getInt(Map<String, Object> parent, String key) {
            Object value = parent.get(key);
            if (value == null) {
                throw new ConfigurationException("Missing required field: " + key);
            }
            return toInt(value, key);
        }

        boolean getBoolean(Map<String, Object> parent, String key) {
            Object value = parent.get(key);
            if (value == null) {
                throw new ConfigurationException("Missing required field: " + key);
            }
            if (value instanceof Boolean b) {
                return b;
            }
            throw new ConfigurationException("Expected boolean for " + key + ", got: " + value);
        }

        @SuppressWarnings("unchecked")
        Map<String, Double> getDoubleMap(Map<String, Object> parent, String key) {
            Map<String, Object> raw = getMap(parent, key);
            Map<String, Double> result = new HashMap<>();
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                result.put(entry.getKey().toLowerCase(),
                        toDouble(entry.getValue(), key + "." + entry.getKey()));
            }
            return result;
        }

        @SuppressWarnings("unchecked")
        List<Integer> getIntList(Map<String, Object> parent, String key) {
            Object value = parent.get(key);
            if (value == null) {
                throw new ConfigurationException("Missing required field: " + key);
            }
            if (!(value instanceof List)) {
                throw new ConfigurationException(
                    "Expected list for " + key + ", got: " + value.getClass().getSimpleName());
            }
            List<?> raw = (List<?>) value;
            List<Integer> result = new ArrayList<>();
            for (int i = 0; i < raw.size(); i++) {
                result.add(toInt(raw.get(i), key + "[" + i + "]"));
            }
            return result;
        }

        @SuppressWarnings("unchecked")
        List<Double> getDoubleList(Map<String, Object> parent, String key) {
            Object value = parent.get(key);
            if (value == null) {
                throw new ConfigurationException("Missing required field: " + key);
            }
            if (!(value instanceof List)) {
                throw new ConfigurationException(
                    "Expected list for " + key + ", got: " + value.getClass().getSimpleName());
            }
            List<?> raw = (List<?>) value;
            List<Double> result = new ArrayList<>();
            for (int i = 0; i < raw.size(); i++) {
                result.add(toDouble(raw.get(i), key + "[" + i + "]"));
            }
            return result;
        }

        @SuppressWarnings("unchecked")
        List<String> getStringList(Map<String, Object> parent, String key) {
            Object value = parent.get(key);
            if (value == null) {
                throw new ConfigurationException("Missing required field: " + key);
            }
            if (!(value instanceof List)) {
                throw new ConfigurationException(
                    "Expected list for " + key + ", got: " + value.getClass().getSimpleName());
            }
            List<?> raw = (List<?>) value;
            List<String> result = new ArrayList<>();
            for (Object item : raw) {
                result.add(item.toString());
            }
            return result;
        }

        double toDouble(Object value, String context) {
            if (value instanceof Number n) {
                return n.doubleValue();
            }
            try {
                return Double.parseDouble(value.toString());
            } catch (NumberFormatException e) {
                throw new ConfigurationException(
                    "Expected number for " + context + ", got: " + value);
            }
        }

        int toInt(Object value, String context) {
            if (value instanceof Number n) {
                return n.intValue();
            }
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException e) {
                throw new ConfigurationException(
                    "Expected integer for " + context + ", got: " + value);
            }
        }
    }

    // =========================================================================
    // EXCEPTION
    // =========================================================================

    public static class ConfigurationException extends RuntimeException {
        public ConfigurationException(String message) {
            super(message);
        }

        public ConfigurationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
