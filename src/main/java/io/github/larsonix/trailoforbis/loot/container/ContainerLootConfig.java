package io.github.larsonix.trailoforbis.loot.container;

import io.github.larsonix.trailoforbis.gear.model.GearRarity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * Configuration model for the container loot replacement system.
 *
 * <p>Loaded from {@code container-loot.yml}, this configuration controls:
 * <ul>
 *   <li>Which container types to process (treasure chests, regular containers)</li>
 *   <li>Loot scaling based on player level</li>
 *   <li>Container tier classification and bonuses</li>
 *   <li>Stone and map drop chances</li>
 *   <li>Item removal rules</li>
 * </ul>
 */
public final class ContainerLootConfig {

    // =========================================================================
    // FIELDS
    // =========================================================================

    private boolean enabled = true;
    private String scope = "all_worlds";
    private boolean clearAllVanilla = true;
    private Sources sources = new Sources();
    private LootScaling lootScaling = new LootScaling();
    private Map<String, TierConfig> containerTiers = new LinkedHashMap<>();
    private StoneDrops stoneDrops = new StoneDrops();
    private MapDrops mapDrops = new MapDrops();
    private ItemRemoval itemRemoval = new ItemRemoval();
    private Advanced advanced = new Advanced();

    // =========================================================================
    // GETTERS
    // =========================================================================

    public boolean isEnabled() {
        return enabled;
    }

    @Nonnull
    public String getScope() {
        return scope;
    }

    @Nonnull
    public ReplacementScope getReplacementScope() {
        return ReplacementScope.fromConfigKey(scope);
    }

    public boolean isClearAllVanilla() {
        return clearAllVanilla;
    }

    @Nonnull
    public Sources getSources() {
        return sources;
    }

    @Nonnull
    public LootScaling getLootScaling() {
        return lootScaling;
    }

    @Nonnull
    public Map<String, TierConfig> getContainerTiers() {
        return containerTiers;
    }

    @Nonnull
    public StoneDrops getStoneDrops() {
        return stoneDrops;
    }

    @Nonnull
    public MapDrops getMapDrops() {
        return mapDrops;
    }

    @Nonnull
    public ItemRemoval getItemRemoval() {
        return itemRemoval;
    }

    @Nonnull
    public Advanced getAdvanced() {
        return advanced;
    }

    // =========================================================================
    // SETTERS (for YAML deserialization)
    // =========================================================================

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setScope(String scope) {
        this.scope = scope != null ? scope : "all_worlds";
    }

    public void setClear_all_vanilla(boolean clearAllVanilla) {
        this.clearAllVanilla = clearAllVanilla;
    }

    public void setSources(Sources sources) {
        this.sources = sources != null ? sources : new Sources();
    }

    public void setLoot_scaling(LootScaling lootScaling) {
        this.lootScaling = lootScaling != null ? lootScaling : new LootScaling();
    }

    public void setContainer_tiers(Map<String, TierConfig> containerTiers) {
        this.containerTiers = containerTiers != null ? containerTiers : new LinkedHashMap<>();
    }

    public void setStone_drops(StoneDrops stoneDrops) {
        this.stoneDrops = stoneDrops != null ? stoneDrops : new StoneDrops();
    }

    public void setMap_drops(MapDrops mapDrops) {
        this.mapDrops = mapDrops != null ? mapDrops : new MapDrops();
    }

    public void setItem_removal(ItemRemoval itemRemoval) {
        this.itemRemoval = itemRemoval != null ? itemRemoval : new ItemRemoval();
    }

    public void setAdvanced(Advanced advanced) {
        this.advanced = advanced != null ? advanced : new Advanced();
    }

    // =========================================================================
    // SOURCES
    // =========================================================================

    /**
     * Controls which container sources to process.
     */
    public static final class Sources {
        private boolean treasureChests = true;
        private boolean regularContainers = true;

        public boolean isTreasureChests() {
            return treasureChests;
        }

        public boolean isRegularContainers() {
            return regularContainers;
        }

        public void setTreasure_chests(boolean treasureChests) {
            this.treasureChests = treasureChests;
        }

        public void setRegular_containers(boolean regularContainers) {
            this.regularContainers = regularContainers;
        }
    }

    // =========================================================================
    // LOOT SCALING
    // =========================================================================

    /**
     * Loot scaling configuration based on player level.
     */
    public static final class LootScaling {
        private List<LevelRange> levelRanges = new ArrayList<>();
        private int[] defaultGearLevelRange = {1, 5};
        private Map<String, Integer> defaultRarityWeights = new LinkedHashMap<>();

        public LootScaling() {
            // Default rarity weights
            defaultRarityWeights.put("common", 70);
            defaultRarityWeights.put("uncommon", 25);
            defaultRarityWeights.put("rare", 5);
            defaultRarityWeights.put("epic", 0);
            defaultRarityWeights.put("legendary", 0);
            defaultRarityWeights.put("mythic", 0);
        }

        @Nonnull
        public List<LevelRange> getLevelRanges() {
            return levelRanges;
        }

        public int[] getDefaultGearLevelRange() {
            return defaultGearLevelRange;
        }

        @Nonnull
        public Map<String, Integer> getDefaultRarityWeights() {
            return defaultRarityWeights;
        }

        public void setLevel_ranges(List<LevelRange> levelRanges) {
            this.levelRanges = levelRanges != null ? levelRanges : new ArrayList<>();
        }

        public void setDefault_gear_level_range(List<Integer> range) {
            if (range != null && range.size() >= 2) {
                this.defaultGearLevelRange = new int[]{range.get(0), range.get(1)};
            }
        }

        public void setDefault_rarity_weights(Map<String, Integer> weights) {
            this.defaultRarityWeights = weights != null ? weights : new LinkedHashMap<>();
        }

        /**
         * Finds the level range configuration for a player level.
         *
         * @param playerLevel The player's level
         * @return The matching level range, or null if none matches
         */
        @Nullable
        public LevelRange findLevelRange(int playerLevel) {
            for (LevelRange range : levelRanges) {
                if (playerLevel >= range.getPlayerLevelMin() && playerLevel <= range.getPlayerLevelMax()) {
                    return range;
                }
            }
            return null;
        }

        /**
         * Gets the gear level range for a player level.
         *
         * @param playerLevel The player's level
         * @return [minLevel, maxLevel] for gear generation
         */
        public int[] getGearLevelRange(int playerLevel) {
            LevelRange range = findLevelRange(playerLevel);
            if (range != null) {
                return range.getGearLevelRange();
            }
            return defaultGearLevelRange;
        }

        /**
         * Gets the rarity weights for a player level.
         *
         * @param playerLevel The player's level
         * @return Map of rarity name to weight
         */
        @Nonnull
        public Map<String, Integer> getRarityWeights(int playerLevel) {
            LevelRange range = findLevelRange(playerLevel);
            if (range != null) {
                return range.getRarityWeights();
            }
            return defaultRarityWeights;
        }
    }

    /**
     * A level range configuration for loot scaling.
     */
    public static final class LevelRange {
        private int[] playerLevelRange = {1, 10};
        private int[] gearLevelRange = {1, 5};
        private Map<String, Integer> rarityWeights = new LinkedHashMap<>();

        public int getPlayerLevelMin() {
            return playerLevelRange.length > 0 ? playerLevelRange[0] : 1;
        }

        public int getPlayerLevelMax() {
            return playerLevelRange.length > 1 ? playerLevelRange[1] : 10;
        }

        public int[] getGearLevelRange() {
            return gearLevelRange;
        }

        public int getGearLevelMin() {
            return gearLevelRange.length > 0 ? gearLevelRange[0] : 1;
        }

        public int getGearLevelMax() {
            return gearLevelRange.length > 1 ? gearLevelRange[1] : 5;
        }

        @Nonnull
        public Map<String, Integer> getRarityWeights() {
            return rarityWeights;
        }

        public void setPlayer_level_range(List<Integer> range) {
            if (range != null && range.size() >= 2) {
                this.playerLevelRange = new int[]{range.get(0), range.get(1)};
            }
        }

        public void setGear_level_range(List<Integer> range) {
            if (range != null && range.size() >= 2) {
                this.gearLevelRange = new int[]{range.get(0), range.get(1)};
            }
        }

        public void setRarity_weights(Map<String, Integer> weights) {
            this.rarityWeights = weights != null ? weights : new LinkedHashMap<>();
        }
    }

    // =========================================================================
    // TIER CONFIG
    // =========================================================================

    /**
     * Configuration for a container tier.
     */
    public static final class TierConfig {
        private List<String> patterns = new ArrayList<>();
        private double lootMultiplier = 1.0;
        private double rarityBonus = 0.0;
        private double mapChanceMultiplier = 1.0;
        private double stoneChanceMultiplier = 1.0;
        private int minGearDrops = 0;
        private int maxGearDrops = 2;
        private int minItems = 3;
        private int maxItems = 5;
        private boolean guaranteedRareOrBetter = false;

        @Nonnull
        public List<String> getPatterns() {
            return patterns;
        }

        public double getLootMultiplier() {
            return lootMultiplier;
        }

        public double getRarityBonus() {
            return rarityBonus;
        }

        public double getMapChanceMultiplier() {
            return mapChanceMultiplier;
        }

        public double getStoneChanceMultiplier() {
            return stoneChanceMultiplier;
        }

        public int getMinGearDrops() {
            return minGearDrops;
        }

        public int getMaxGearDrops() {
            return maxGearDrops;
        }

        public int getMinItems() {
            return minItems;
        }

        public int getMaxItems() {
            return maxItems;
        }

        public boolean isGuaranteedRareOrBetter() {
            return guaranteedRareOrBetter;
        }

        public void setPatterns(List<String> patterns) {
            this.patterns = patterns != null ? patterns : new ArrayList<>();
        }

        public void setLoot_multiplier(double lootMultiplier) {
            this.lootMultiplier = lootMultiplier;
        }

        public void setRarity_bonus(double rarityBonus) {
            this.rarityBonus = rarityBonus;
        }

        public void setMap_chance_multiplier(double mapChanceMultiplier) {
            this.mapChanceMultiplier = mapChanceMultiplier;
        }

        public void setStone_chance_multiplier(double stoneChanceMultiplier) {
            this.stoneChanceMultiplier = stoneChanceMultiplier;
        }

        public void setMin_gear_drops(int minGearDrops) {
            this.minGearDrops = minGearDrops;
        }

        public void setMax_gear_drops(int maxGearDrops) {
            this.maxGearDrops = maxGearDrops;
        }

        public void setMin_items(int minItems) {
            this.minItems = minItems;
        }

        public void setMax_items(int maxItems) {
            this.maxItems = maxItems;
        }

        public void setGuaranteed_rare_or_better(boolean guaranteedRareOrBetter) {
            this.guaranteedRareOrBetter = guaranteedRareOrBetter;
        }
    }

    // =========================================================================
    // STONE DROPS
    // =========================================================================

    /**
     * Stone drop configuration.
     */
    public static final class StoneDrops {
        private boolean enabled = true;
        private double baseChance = 0.15;
        private int maxPerContainer = 2;
        private Map<String, Integer> rarityWeights = new LinkedHashMap<>();

        public StoneDrops() {
            rarityWeights.put("common", 40);
            rarityWeights.put("uncommon", 35);
            rarityWeights.put("rare", 18);
            rarityWeights.put("epic", 6);
            rarityWeights.put("legendary", 1);
            rarityWeights.put("mythic", 0);
        }

        public boolean isEnabled() {
            return enabled;
        }

        public double getBaseChance() {
            return baseChance;
        }

        public int getMaxPerContainer() {
            return maxPerContainer;
        }

        @Nonnull
        public Map<String, Integer> getRarityWeights() {
            return rarityWeights;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public void setBase_chance(double baseChance) {
            this.baseChance = baseChance;
        }

        public void setMax_per_container(int maxPerContainer) {
            this.maxPerContainer = maxPerContainer;
        }

        public void setRarity_weights(Map<String, Integer> rarityWeights) {
            this.rarityWeights = rarityWeights != null ? rarityWeights : new LinkedHashMap<>();
        }
    }

    // =========================================================================
    // MAP DROPS
    // =========================================================================

    /**
     * Realm map drop configuration.
     */
    public static final class MapDrops {
        private boolean enabled = true;
        private double baseChance = 0.05;
        private int maxPerContainer = 1;
        private int[] levelOffsetRange = {-5, 5};

        public boolean isEnabled() {
            return enabled;
        }

        public double getBaseChance() {
            return baseChance;
        }

        public int getMaxPerContainer() {
            return maxPerContainer;
        }

        public int[] getLevelOffsetRange() {
            return levelOffsetRange;
        }

        public int getLevelOffsetMin() {
            return levelOffsetRange.length > 0 ? levelOffsetRange[0] : -5;
        }

        public int getLevelOffsetMax() {
            return levelOffsetRange.length > 1 ? levelOffsetRange[1] : 5;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public void setBase_chance(double baseChance) {
            this.baseChance = baseChance;
        }

        public void setMax_per_container(int maxPerContainer) {
            this.maxPerContainer = maxPerContainer;
        }

        public void setLevel_offset_range(List<Integer> range) {
            if (range != null && range.size() >= 2) {
                this.levelOffsetRange = new int[]{range.get(0), range.get(1)};
            }
        }
    }

    // =========================================================================
    // ITEM REMOVAL
    // =========================================================================

    /**
     * Item removal rules for container processing.
     */
    public static final class ItemRemoval {
        private boolean removeWeapons = true;
        private boolean removeArmor = true;
        private boolean preserveMaterials = true;
        private boolean preserveConsumables = true;
        private boolean preserveTools = true;
        private List<String> preservedItems = new ArrayList<>();
        private List<String> removedItems = new ArrayList<>();

        public boolean isRemoveWeapons() {
            return removeWeapons;
        }

        public boolean isRemoveArmor() {
            return removeArmor;
        }

        public boolean isPreserveMaterials() {
            return preserveMaterials;
        }

        public boolean isPreserveConsumables() {
            return preserveConsumables;
        }

        public boolean isPreserveTools() {
            return preserveTools;
        }

        @Nonnull
        public List<String> getPreservedItems() {
            return preservedItems;
        }

        @Nonnull
        public List<String> getRemovedItems() {
            return removedItems;
        }

        public void setRemove_weapons(boolean removeWeapons) {
            this.removeWeapons = removeWeapons;
        }

        public void setRemove_armor(boolean removeArmor) {
            this.removeArmor = removeArmor;
        }

        public void setPreserve_materials(boolean preserveMaterials) {
            this.preserveMaterials = preserveMaterials;
        }

        public void setPreserve_consumables(boolean preserveConsumables) {
            this.preserveConsumables = preserveConsumables;
        }

        public void setPreserve_tools(boolean preserveTools) {
            this.preserveTools = preserveTools;
        }

        public void setPreserved_items(List<String> preservedItems) {
            this.preservedItems = preservedItems != null ? preservedItems : new ArrayList<>();
        }

        public void setRemoved_items(List<String> removedItems) {
            this.removedItems = removedItems != null ? removedItems : new ArrayList<>();
        }
    }

    // =========================================================================
    // ADVANCED
    // =========================================================================

    /**
     * Advanced configuration options.
     */
    public static final class Advanced {
        private long containerMemoryDurationMs = 3600000; // 1 hour
        private long cleanupIntervalMs = 300000; // 5 minutes
        private boolean skipCreativeMode = true;
        private boolean debugLogging = false;

        public long getContainerMemoryDurationMs() {
            return containerMemoryDurationMs;
        }

        public long getCleanupIntervalMs() {
            return cleanupIntervalMs;
        }

        public boolean isSkipCreativeMode() {
            return skipCreativeMode;
        }

        public boolean isDebugLogging() {
            return debugLogging;
        }

        public void setContainer_memory_duration_ms(long containerMemoryDurationMs) {
            this.containerMemoryDurationMs = containerMemoryDurationMs;
        }

        public void setCleanup_interval_ms(long cleanupIntervalMs) {
            this.cleanupIntervalMs = cleanupIntervalMs;
        }

        public void setSkip_creative_mode(boolean skipCreativeMode) {
            this.skipCreativeMode = skipCreativeMode;
        }

        public void setDebug_logging(boolean debugLogging) {
            this.debugLogging = debugLogging;
        }
    }

    // =========================================================================
    // REPLACEMENT SCOPE
    // =========================================================================

    /**
     * Controls where the container loot replacement system operates.
     *
     * <ul>
     *   <li>{@link #REALM_ONLY} — Only replace loot in realm instance worlds</li>
     *   <li>{@link #ALL_WORLDS} — Replace loot in all worlds (overworld + realms)</li>
     *   <li>{@link #DISABLED} — Do not replace loot anywhere</li>
     * </ul>
     */
    public enum ReplacementScope {
        REALM_ONLY("realm_only"),
        ALL_WORLDS("all_worlds"),
        DISABLED("disabled");

        private final String configKey;

        ReplacementScope(@Nonnull String configKey) {
            this.configKey = configKey;
        }

        @Nonnull
        public String getConfigKey() {
            return configKey;
        }

        @Nonnull
        public static ReplacementScope fromConfigKey(@Nullable String key) {
            if (key == null || key.isEmpty()) {
                return ALL_WORLDS;
            }
            for (ReplacementScope scope : values()) {
                if (scope.configKey.equalsIgnoreCase(key)) {
                    return scope;
                }
            }
            return REALM_ONLY;
        }
    }

    // =========================================================================
    // FACTORY
    // =========================================================================

    /**
     * Creates a default configuration with sensible defaults.
     *
     * @return A new default configuration
     */
    @Nonnull
    public static ContainerLootConfig createDefaults() {
        return new ContainerLootConfig();
    }
}
