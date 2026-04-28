package io.github.larsonix.trailoforbis.gear.loot;

import java.util.*;

/**
 * Configuration for loot item pools loaded from loot-items.yml.
 *
 * <p>Defines which base items can drop for each equipment slot and
 * the relative weights for slot selection.
 *
 * <p>This class is designed for SnakeYAML deserialization.
 */
public class LootItemsConfig {

    private Map<String, List<String>> base_items = new HashMap<>();
    private Map<String, Integer> slot_weights = new HashMap<>();

    /**
     * Default constructor for YAML deserialization.
     */
    public LootItemsConfig() {
        // Initialize with defaults
        initializeDefaults();
    }

    /**
     * Initializes default values if config is empty or partially loaded.
     *
     * <p>Item IDs use Hytale's PascalCase format: Category_Type_Material
     * (e.g., Weapon_Sword_Iron, Armor_Iron_Head).
     */
    private void initializeDefaults() {
        // Default base items with correct Hytale item IDs
        if (base_items == null || base_items.isEmpty()) {
            base_items = new HashMap<>();

            // Weapons - swords, axes, maces, bows, staffs, daggers
            base_items.put("weapon", List.of(
                    "Weapon_Sword_Crude",
                    "Weapon_Sword_Copper",
                    "Weapon_Sword_Bronze",
                    "Weapon_Sword_Iron",
                    "Weapon_Axe_Crude",
                    "Weapon_Axe_Copper",
                    "Weapon_Axe_Iron",
                    "Weapon_Mace_Crude",
                    "Weapon_Mace_Copper",
                    "Weapon_Mace_Iron",
                    "Weapon_Shortbow_Crude",
                    "Weapon_Shortbow_Copper",
                    "Weapon_Shortbow_Iron",
                    "Weapon_Staff_Wood",
                    "Weapon_Staff_Iron",
                    "Weapon_Daggers_Crude",
                    "Weapon_Daggers_Copper",
                    "Weapon_Daggers_Iron"
            ));

            // Head armor - helmets and caps
            base_items.put("head", List.of(
                    "Armor_Copper_Head",
                    "Armor_Bronze_Head",
                    "Armor_Iron_Head",
                    "Armor_Leather_Light_Head",
                    "Armor_Cloth_Cotton_Head"
            ));

            // Chest armor - chestplates and tunics
            base_items.put("chest", List.of(
                    "Armor_Copper_Chest",
                    "Armor_Bronze_Chest",
                    "Armor_Iron_Chest",
                    "Armor_Leather_Light_Chest",
                    "Armor_Cloth_Cotton_Chest"
            ));

            // Leg armor - leggings and pants
            base_items.put("legs", List.of(
                    "Armor_Copper_Legs",
                    "Armor_Bronze_Legs",
                    "Armor_Iron_Legs",
                    "Armor_Leather_Light_Legs",
                    "Armor_Cloth_Cotton_Legs"
            ));

            // Hand armor - gloves and gauntlets
            base_items.put("hands", List.of(
                    "Armor_Copper_Hands",
                    "Armor_Bronze_Hands",
                    "Armor_Iron_Hands",
                    "Armor_Leather_Light_Hands",
                    "Armor_Cloth_Cotton_Hands"
            ));

            // Shields and off-hand items
            base_items.put("off_hand", List.of(
                    "Weapon_Shield_Wood",
                    "Weapon_Shield_Copper",
                    "Weapon_Shield_Iron"
            ));
        }

        // Default slot weights
        if (slot_weights == null || slot_weights.isEmpty()) {
            slot_weights = new HashMap<>();
            slot_weights.put("weapon", 30);
            slot_weights.put("head", 10);
            slot_weights.put("chest", 20);
            slot_weights.put("legs", 15);
            slot_weights.put("hands", 15);
            slot_weights.put("off_hand", 10);
        }
    }

    // =========================================================================
    // GETTERS (for YAML and programmatic access)
    // =========================================================================

    /**
     * Gets the base items map.
     *
     * @return Map of slot name to list of item IDs
     */
    public Map<String, List<String>> getBase_items() {
        return base_items;
    }

    /**
     * Gets the slot weights map.
     *
     * @return Map of slot name to weight value
     */
    public Map<String, Integer> getSlot_weights() {
        return slot_weights;
    }

    // =========================================================================
    // SETTERS (for YAML deserialization)
    // =========================================================================

    public void setBase_items(Map<String, List<String>> base_items) {
        this.base_items = base_items;
    }

    public void setSlot_weights(Map<String, Integer> slot_weights) {
        this.slot_weights = slot_weights;
    }

    // =========================================================================
    // CONVENIENCE METHODS
    // =========================================================================

    /**
     * Gets the base items for a specific slot.
     *
     * @param slot The slot name (weapon, head, chest, legs, hands, off_hand)
     * @return List of item IDs, or empty list if slot not configured
     */
    public List<String> getItemsForSlot(String slot) {
        return base_items.getOrDefault(slot.toLowerCase(), List.of());
    }

    /**
     * Gets the weight for a specific slot.
     *
     * @param slot The slot name
     * @return The weight, or 0 if slot not configured
     */
    public int getWeightForSlot(String slot) {
        return slot_weights.getOrDefault(slot.toLowerCase(), 0);
    }

    /**
     * Gets the total weight of all slots.
     *
     * @return Sum of all slot weights
     */
    public int getTotalWeight() {
        return slot_weights.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * Gets all configured slot names.
     *
     * @return Set of slot names
     */
    public Set<String> getSlots() {
        return Collections.unmodifiableSet(base_items.keySet());
    }

    /**
     * Validates the configuration.
     *
     * @throws ConfigValidationException if config is invalid
     */
    public void validate() throws ConfigValidationException {
        if (base_items == null || base_items.isEmpty()) {
            throw new ConfigValidationException("base_items cannot be empty");
        }

        if (slot_weights == null || slot_weights.isEmpty()) {
            throw new ConfigValidationException("slot_weights cannot be empty");
        }

        // Check that all slots in base_items have weights
        for (String slot : base_items.keySet()) {
            if (!slot_weights.containsKey(slot)) {
                throw new ConfigValidationException(
                        "Slot '" + slot + "' has items but no weight configured");
            }
        }

        // Check that all weights are positive
        for (Map.Entry<String, Integer> entry : slot_weights.entrySet()) {
            if (entry.getValue() < 0) {
                throw new ConfigValidationException(
                        "Slot weight cannot be negative: " + entry.getKey());
            }
        }

        // Check that total weight is positive
        if (getTotalWeight() <= 0) {
            throw new ConfigValidationException("Total slot weight must be positive");
        }
    }

    /**
     * Exception thrown when configuration validation fails.
     */
    public static class ConfigValidationException extends Exception {
        public ConfigValidationException(String message) {
            super(message);
        }
    }

    /**
     * Creates a default configuration.
     *
     * @return A new LootItemsConfig with default values
     */
    public static LootItemsConfig createDefaults() {
        return new LootItemsConfig();
    }
}
