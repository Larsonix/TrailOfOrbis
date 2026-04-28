package io.github.larsonix.trailoforbis.gear.loot;

import io.github.larsonix.trailoforbis.gear.loot.LootGenerator.EquipmentSlot;

import java.util.*;

/**
 * Configuration for dynamic loot item discovery.
 *
 * <p>This replaces the static loot-items.yml configuration with a system
 * that automatically discovers weapons and armor from Hytale's asset registry.
 *
 * <p>Key configuration options:
 * <ul>
 *   <li><b>enabled</b>: Whether dynamic discovery is enabled</li>
 *   <li><b>include_weapons/armor/shields</b>: What item types to discover</li>
 *   <li><b>blacklist</b>: Items/mods to exclude from drops</li>
 *   <li><b>slot_weights</b>: Relative drop rates per equipment slot</li>
 * </ul>
 *
 * <p>This class is designed for SnakeYAML deserialization from loot-discovery.yml.
 */
public class LootDiscoveryConfig {

    // =========================================================================
    // DISCOVERY SETTINGS
    // =========================================================================

    private boolean enabled = true;
    private boolean include_weapons = true;
    private boolean include_armor = true;
    private boolean include_shields = true;

    // =========================================================================
    // BLACKLIST
    // =========================================================================

    private Blacklist blacklist = new Blacklist();

    // =========================================================================
    // SLOT WEIGHTS
    // =========================================================================

    private Map<String, Integer> slot_weights = new HashMap<>();

    // =========================================================================
    // CATEGORY WEIGHTS
    // =========================================================================

    private Map<String, Double> weapon_category_weights = new LinkedHashMap<>();
    private Map<String, Double> armor_category_weights = new LinkedHashMap<>();

    // =========================================================================
    // SOUND SET OVERRIDES
    // =========================================================================

    private Map<String, String> sound_set_overrides = new LinkedHashMap<>();

    /**
     * Default constructor for YAML deserialization.
     */
    public LootDiscoveryConfig() {
        initializeDefaults();
    }

    /**
     * Initializes default values.
     */
    private void initializeDefaults() {
        // Default slot weights
        if (slot_weights.isEmpty()) {
            slot_weights.put("weapon", 30);
            slot_weights.put("head", 10);
            slot_weights.put("chest", 20);
            slot_weights.put("legs", 15);
            slot_weights.put("hands", 15);
            slot_weights.put("off_hand", 10);
        }

        // Default blacklist
        if (blacklist == null) {
            blacklist = new Blacklist();
        }

        // Default weapon category weights (all equal)
        if (weapon_category_weights.isEmpty()) {
            for (String type : List.of(
                    "SWORD", "DAGGER", "AXE", "MACE", "CLAWS", "CLUB",
                    "LONGSWORD", "BATTLEAXE", "SPEAR",
                    "SHORTBOW", "CROSSBOW", "BLOWGUN",
                    "STAFF", "WAND", "SPELLBOOK",
                    "UNKNOWN")) {
                weapon_category_weights.put(type, 1.0);
            }
        }

        // Default armor category weights (all equal)
        if (armor_category_weights.isEmpty()) {
            for (String mat : List.of("CLOTH", "LEATHER", "PLATE", "WOOD", "SPECIAL")) {
                armor_category_weights.put(mat, 1.0);
            }
        }
    }

    // =========================================================================
    // GETTERS
    // =========================================================================

    /**
     * Whether dynamic discovery is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Whether to include weapons (item.getWeapon() != null).
     */
    public boolean isIncludeWeapons() {
        return include_weapons;
    }

    /**
     * Whether to include armor (item.getArmor() != null).
     */
    public boolean isIncludeArmor() {
        return include_armor;
    }

    /**
     * Whether to include shields.
     */
    public boolean isIncludeShields() {
        return include_shields;
    }

    /**
     * Gets the item blacklist patterns.
     */
    public List<String> getBlacklistItems() {
        return blacklist != null ? blacklist.getItems() : List.of();
    }

    /**
     * Gets the mod blacklist.
     */
    public List<String> getBlacklistMods() {
        return blacklist != null ? blacklist.getMods() : List.of();
    }

    /**
     * Gets the blacklist configuration object.
     */
    public Blacklist getBlacklist() {
        return blacklist;
    }

    /**
     * Gets the slot weights map (raw string keys).
     */
    public Map<String, Integer> getSlot_weights() {
        return slot_weights;
    }

    /**
     * Gets slot weights as EquipmentSlot enum keys.
     */
    public Map<EquipmentSlot, Integer> getSlotWeights() {
        Map<EquipmentSlot, Integer> result = new EnumMap<>(EquipmentSlot.class);

        for (Map.Entry<String, Integer> entry : slot_weights.entrySet()) {
            try {
                EquipmentSlot slot = EquipmentSlot.valueOf(entry.getKey().toUpperCase());
                result.put(slot, entry.getValue());
            } catch (IllegalArgumentException e) {
                // Unknown slot name, skip
            }
        }

        return result;
    }

    /**
     * Gets the weight for a specific slot.
     *
     * @param slot The equipment slot
     * @return The weight, or 0 if not configured
     */
    public int getWeightForSlot(EquipmentSlot slot) {
        String key = slot.name().toLowerCase();
        return slot_weights.getOrDefault(key, 0);
    }

    /**
     * Gets the total weight of all slots.
     */
    public int getTotalWeight() {
        return slot_weights.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * Gets weapon category weights (defensive copy).
     */
    public Map<String, Double> getWeaponCategoryWeights() {
        return new LinkedHashMap<>(weapon_category_weights);
    }

    /**
     * Gets armor category weights (defensive copy).
     */
    public Map<String, Double> getArmorCategoryWeights() {
        return new LinkedHashMap<>(armor_category_weights);
    }

    /**
     * Gets sound set overrides for armor material classification.
     *
     * <p>Maps ItemSoundSet IDs (lower-cased) to ArmorMaterial names.
     * Used by {@link DynamicLootRegistry} to classify armor from mods
     * that define custom sound sets not covered by the default mapping.
     *
     * @return Map of sound set ID → material name, or null if not configured
     */
    public Map<String, String> getSoundSetOverrides() {
        if (sound_set_overrides == null || sound_set_overrides.isEmpty()) {
            return null;
        }
        // Lower-case all keys for case-insensitive matching
        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : sound_set_overrides.entrySet()) {
            normalized.put(entry.getKey().toLowerCase(), entry.getValue());
        }
        return normalized;
    }

    // =========================================================================
    // SETTERS (for YAML deserialization)
    // =========================================================================

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setInclude_weapons(boolean include_weapons) {
        this.include_weapons = include_weapons;
    }

    public void setInclude_armor(boolean include_armor) {
        this.include_armor = include_armor;
    }

    public void setInclude_shields(boolean include_shields) {
        this.include_shields = include_shields;
    }

    public void setWeapon_category_weights(Map<String, Double> weapon_category_weights) {
        this.weapon_category_weights = weapon_category_weights;
    }

    public void setArmor_category_weights(Map<String, Double> armor_category_weights) {
        this.armor_category_weights = armor_category_weights;
    }

    public void setBlacklist(Blacklist blacklist) {
        this.blacklist = blacklist;
    }

    public void setSlot_weights(Map<String, Integer> slot_weights) {
        this.slot_weights = slot_weights;
    }

    public void setSound_set_overrides(Map<String, String> sound_set_overrides) {
        this.sound_set_overrides = sound_set_overrides;
    }

    // =========================================================================
    // VALIDATION
    // =========================================================================

    /**
     * Validates the configuration.
     *
     * @throws ConfigValidationException if invalid
     */
    public void validate() throws ConfigValidationException {
        // Check slot weights
        for (Map.Entry<String, Integer> entry : slot_weights.entrySet()) {
            if (entry.getValue() < 0) {
                throw new ConfigValidationException("Slot weight cannot be negative: " + entry.getKey());
            }
        }

        // Check category weights
        for (Map.Entry<String, Double> entry : weapon_category_weights.entrySet()) {
            if (entry.getValue() < 0) {
                throw new ConfigValidationException("Weapon category weight cannot be negative: " + entry.getKey());
            }
        }
        for (Map.Entry<String, Double> entry : armor_category_weights.entrySet()) {
            if (entry.getValue() < 0) {
                throw new ConfigValidationException("Armor category weight cannot be negative: " + entry.getKey());
            }
        }
    }

    /**
     * Exception thrown when config validation fails.
     */
    public static class ConfigValidationException extends Exception {
        public ConfigValidationException(String message) {
            super(message);
        }
    }

    // =========================================================================
    // BLACKLIST INNER CLASS
    // =========================================================================

    /**
     * Blacklist configuration for excluding items and mods.
     */
    public static class Blacklist {
        private List<String> items = new ArrayList<>();
        private List<String> mods = new ArrayList<>();

        public Blacklist() {
            initializeDefaults();
        }

        private void initializeDefaults() {
            if (items.isEmpty()) {
                // Default exclusions
                items.add("rpg_*");            // RPG plugin custom items (must not re-enter loot pool)
                items.add("Tool_*");           // All tools
                items.add("*_Debug_*");        // Debug items
                items.add("*_Template_*");     // Template items
            }
        }

        public List<String> getItems() {
            return items != null ? items : List.of();
        }

        public void setItems(List<String> items) {
            this.items = items;
        }

        public List<String> getMods() {
            return mods != null ? mods : List.of();
        }

        public void setMods(List<String> mods) {
            this.mods = mods;
        }
    }

    // =========================================================================
    // FACTORY METHODS
    // =========================================================================

    /**
     * Creates a default configuration.
     */
    public static LootDiscoveryConfig createDefaults() {
        return new LootDiscoveryConfig();
    }
}
