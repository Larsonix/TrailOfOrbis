package io.github.larsonix.trailoforbis.gear.conversion;

import io.github.larsonix.trailoforbis.gear.model.GearRarity;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration for the vanilla item conversion system.
 *
 * <p>Controls how vanilla Hytale weapons and armor are converted to RPG gear
 * when acquired through mob drops, chest loot, or crafting.
 *
 * <p>Loaded from vanilla-conversion.yml with snake_case keys.
 */
public class VanillaConversionConfig {

    private boolean enabled = true;
    private SourcesConfig sources = new SourcesConfig();
    private List<String> whitelist = new ArrayList<>();
    private List<String> blacklist = new ArrayList<>();
    private Map<String, String> materialTiers = new HashMap<>();
    private String defaultMaxRarity = "RARE";
    private LevelCalculationConfig levelCalculation = new LevelCalculationConfig();
    private RarityBonusConfig rarityBonus = new RarityBonusConfig();
    private Map<String, DistanceRange> materialDistances = new HashMap<>();
    private DistanceRange defaultDistance = new DistanceRange(500, 1500);
    private double craftingLevelMultiplier = 1.0;

    // ==================== Getters and Setters ====================

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public SourcesConfig getSources() {
        return sources;
    }

    public void setSources(SourcesConfig sources) {
        this.sources = sources;
    }

    public List<String> getWhitelist() {
        return whitelist;
    }

    public void setWhitelist(List<String> whitelist) {
        this.whitelist = whitelist != null ? whitelist : new ArrayList<>();
    }

    public List<String> getBlacklist() {
        return blacklist;
    }

    public void setBlacklist(List<String> blacklist) {
        this.blacklist = blacklist != null ? blacklist : new ArrayList<>();
    }

    public Map<String, String> getMaterialTiers() {
        return materialTiers;
    }

    public void setMaterialTiers(Map<String, String> materialTiers) {
        this.materialTiers = materialTiers != null ? materialTiers : new HashMap<>();
    }

    // YAML snake_case setter
    public void setMaterial_tiers(Map<String, String> materialTiers) {
        setMaterialTiers(materialTiers);
    }

    public String getDefaultMaxRarity() {
        return defaultMaxRarity;
    }

    public void setDefaultMaxRarity(String defaultMaxRarity) {
        this.defaultMaxRarity = defaultMaxRarity;
    }

    // YAML snake_case setter
    public void setDefault_max_rarity(String defaultMaxRarity) {
        setDefaultMaxRarity(defaultMaxRarity);
    }

    /**
     * Gets the default max rarity as a GearRarity enum.
     */
    @Nonnull
    public GearRarity getDefaultMaxRarityEnum() {
        try {
            return GearRarity.fromString(defaultMaxRarity);
        } catch (IllegalArgumentException e) {
            return GearRarity.RARE;
        }
    }

    /**
     * Gets the max rarity for a specific material.
     *
     * @return the max rarity, or default if not found
     */
    @Nonnull
    public GearRarity getMaxRarityForMaterial(String material) {
        String rarityStr = materialTiers.get(material.toLowerCase());
        if (rarityStr == null) {
            return getDefaultMaxRarityEnum();
        }
        try {
            return GearRarity.fromString(rarityStr);
        } catch (IllegalArgumentException e) {
            return getDefaultMaxRarityEnum();
        }
    }

    public LevelCalculationConfig getLevelCalculation() {
        return levelCalculation;
    }

    public void setLevelCalculation(LevelCalculationConfig levelCalculation) {
        this.levelCalculation = levelCalculation;
    }

    // YAML snake_case setter
    public void setLevel_calculation(LevelCalculationConfig levelCalculation) {
        setLevelCalculation(levelCalculation);
    }

    public RarityBonusConfig getRarityBonus() {
        return rarityBonus;
    }

    public void setRarityBonus(RarityBonusConfig rarityBonus) {
        this.rarityBonus = rarityBonus;
    }

    // YAML snake_case setter
    public void setRarity_bonus(RarityBonusConfig rarityBonus) {
        setRarityBonus(rarityBonus);
    }

    // ==================== Material Distances ====================

    public Map<String, DistanceRange> getMaterialDistances() {
        return materialDistances;
    }

    public void setMaterialDistances(Map<String, DistanceRange> materialDistances) {
        this.materialDistances = materialDistances != null ? materialDistances : new HashMap<>();
    }

    public void setMaterial_distances(Map<String, DistanceRange> materialDistances) {
        setMaterialDistances(materialDistances);
    }

    public DistanceRange getDefaultDistance() {
        return defaultDistance;
    }

    public void setDefaultDistance(DistanceRange defaultDistance) {
        this.defaultDistance = defaultDistance != null ? defaultDistance : new DistanceRange(500, 1500);
    }

    public void setDefault_distance(DistanceRange defaultDistance) {
        setDefaultDistance(defaultDistance);
    }

    public double getCraftingLevelMultiplier() {
        return craftingLevelMultiplier;
    }

    public void setCraftingLevelMultiplier(double craftingLevelMultiplier) {
        this.craftingLevelMultiplier = craftingLevelMultiplier;
    }

    public void setCrafting_level_multiplier(double craftingLevelMultiplier) {
        setCraftingLevelMultiplier(craftingLevelMultiplier);
    }

    /**
     * Gets the distance range for a material. Falls back to default if not configured.
     */
    @Nonnull
    public DistanceRange getDistanceForMaterial(@Nonnull String material) {
        DistanceRange range = materialDistances.get(material.toLowerCase());
        return range != null ? range : defaultDistance;
    }

    // ==================== Validation ====================

    /**
     * @throws ConfigValidationException if validation fails
     */
    public void validate() throws ConfigValidationException {
        if (levelCalculation.minLevel < 1) {
            throw new ConfigValidationException("level_calculation.min_level must be >= 1");
        }
        if (levelCalculation.maxLevel < levelCalculation.minLevel) {
            throw new ConfigValidationException("level_calculation.max_level must be >= min_level");
        }
        // Validate material tier rarity values
        for (Map.Entry<String, String> entry : materialTiers.entrySet()) {
            try {
                GearRarity.fromString(entry.getValue());
            } catch (IllegalArgumentException e) {
                throw new ConfigValidationException(
                    "Invalid rarity '" + entry.getValue() + "' for material '" + entry.getKey() + "'");
            }
        }
        // Validate default rarity
        try {
            GearRarity.fromString(defaultMaxRarity);
        } catch (IllegalArgumentException e) {
            throw new ConfigValidationException("Invalid default_max_rarity: " + defaultMaxRarity);
        }
    }

    public static class ConfigValidationException extends Exception {
        public ConfigValidationException(String message) {
            super(message);
        }
    }

    // ==================== Diagnostics ====================

    /**
     * Returns a diagnostic summary of the configuration.
     */
    @Nonnull
    public String getDiagnostics() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== VanillaConversionConfig ===\n");
        sb.append("enabled: ").append(enabled).append("\n");
        sb.append("\n[Sources]\n");
        sb.append("  mob_drops: ").append(sources.mobDrops).append("\n");
        sb.append("  chest_loot: ").append(sources.chestLoot).append("\n");
        sb.append("  crafting: ").append(sources.crafting).append("\n");
        sb.append("\n[Filtering]\n");
        sb.append("  whitelist: ").append(whitelist.isEmpty() ? "(all items)" : whitelist).append("\n");
        sb.append("  blacklist: ").append(blacklist.isEmpty() ? "(none)" : blacklist).append("\n");
        sb.append("\n[Material Tiers]\n");
        for (Map.Entry<String, String> entry : materialTiers.entrySet()) {
            sb.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        sb.append("  default: ").append(defaultMaxRarity).append("\n");
        sb.append("\n[Level Calculation]\n");
        sb.append("  min_level: ").append(levelCalculation.minLevel).append("\n");
        sb.append("  max_level: ").append(levelCalculation.maxLevel).append("\n");
        sb.append("  crafting_level_offset: ").append(levelCalculation.craftingLevelOffset).append("\n");
        sb.append("\n[Rarity Bonus]\n");
        sb.append("  mob_drops: ").append(rarityBonus.mobDrops).append("\n");
        sb.append("  chest_loot: ").append(rarityBonus.chestLoot).append("\n");
        sb.append("  crafting: ").append(rarityBonus.crafting).append("\n");
        return sb.toString();
    }

    // ==================== Nested Config Classes ====================

    public static class SourcesConfig {
        private boolean mobDrops = true;
        private boolean chestLoot = true;
        private boolean crafting = true;

        public boolean isMobDrops() {
            return mobDrops;
        }

        public void setMobDrops(boolean mobDrops) {
            this.mobDrops = mobDrops;
        }

        // YAML snake_case setter
        public void setMob_drops(boolean mobDrops) {
            this.mobDrops = mobDrops;
        }

        public boolean isChestLoot() {
            return chestLoot;
        }

        public void setChestLoot(boolean chestLoot) {
            this.chestLoot = chestLoot;
        }

        // YAML snake_case setter
        public void setChest_loot(boolean chestLoot) {
            this.chestLoot = chestLoot;
        }

        public boolean isCrafting() {
            return crafting;
        }

        public void setCrafting(boolean crafting) {
            this.crafting = crafting;
        }
    }

    public static class LevelCalculationConfig {
        private int minLevel = 1;
        private int maxLevel = 1_000_000;
        private int craftingLevelOffset = 0;

        public int getMinLevel() {
            return minLevel;
        }

        public void setMinLevel(int minLevel) {
            this.minLevel = minLevel;
        }

        // YAML snake_case setter
        public void setMin_level(int minLevel) {
            this.minLevel = minLevel;
        }

        public int getMaxLevel() {
            return maxLevel;
        }

        public void setMaxLevel(int maxLevel) {
            this.maxLevel = maxLevel;
        }

        // YAML snake_case setter
        public void setMax_level(int maxLevel) {
            this.maxLevel = maxLevel;
        }

        public int getCraftingLevelOffset() {
            return craftingLevelOffset;
        }

        public void setCraftingLevelOffset(int craftingLevelOffset) {
            this.craftingLevelOffset = craftingLevelOffset;
        }

        // YAML snake_case setter
        public void setCrafting_level_offset(int craftingLevelOffset) {
            this.craftingLevelOffset = craftingLevelOffset;
        }

        /**
         * Clamps a level to the valid range.
         */
        public int clampLevel(int level) {
            return Math.max(minLevel, Math.min(maxLevel, level));
        }
    }

    public static class RarityBonusConfig {
        private double mobDrops = 0.0;
        private double chestLoot = 0.5;
        private double crafting = 0.0;

        public double getMobDrops() {
            return mobDrops;
        }

        public void setMobDrops(double mobDrops) {
            this.mobDrops = mobDrops;
        }

        // YAML snake_case setter
        public void setMob_drops(double mobDrops) {
            this.mobDrops = mobDrops;
        }

        public double getChestLoot() {
            return chestLoot;
        }

        public void setChestLoot(double chestLoot) {
            this.chestLoot = chestLoot;
        }

        // YAML snake_case setter
        public void setChest_loot(double chestLoot) {
            this.chestLoot = chestLoot;
        }

        public double getCrafting() {
            return crafting;
        }

        public void setCrafting(double crafting) {
            this.crafting = crafting;
        }
    }

    /**
     * Distance range (blocks from spawn) where a material is found.
     * Used to compute gear level via the shared mob scaling formula.
     */
    public static class DistanceRange {
        private int min = 500;
        private int max = 1500;

        public DistanceRange() {}

        public DistanceRange(int min, int max) {
            this.min = min;
            this.max = max;
        }

        public int getMin() { return min; }
        public void setMin(int min) { this.min = min; }
        public int getMax() { return max; }
        public void setMax(int max) { this.max = max; }
    }
}
