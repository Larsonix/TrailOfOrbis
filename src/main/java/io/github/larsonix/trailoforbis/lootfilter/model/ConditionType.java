package io.github.larsonix.trailoforbis.lootfilter.model;

/**
 * Discriminator for {@link FilterCondition} sealed types.
 *
 * <p>Used as the JSON {@code "type"} field when serializing filter conditions.
 */
public enum ConditionType {
    MIN_RARITY("Minimum Rarity"),
    MAX_RARITY("Maximum Rarity"),
    EQUIPMENT_SLOT("Equipment Slot"),
    WEAPON_TYPE("Weapon Type"),
    ARMOR_IMPLICIT("Armor Implicit"),
    ITEM_LEVEL_RANGE("Item Level Range"),
    QUALITY_RANGE("Quality Range"),
    REQUIRED_MODIFIERS("Required Modifiers"),
    MODIFIER_VALUE_RANGE("Modifier Value Range"),
    MIN_MODIFIER_COUNT("Minimum Modifier Count"),
    IMPLICIT_CONDITION("Weapon Implicit"),
    CORRUPTION_STATE("Corruption State"),
    MAP_BIOME("Map Biome"),
    MAP_SIZE("Map Size"),
    MAP_MODIFIER("Map Modifier");

    private final String displayName;

    ConditionType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
