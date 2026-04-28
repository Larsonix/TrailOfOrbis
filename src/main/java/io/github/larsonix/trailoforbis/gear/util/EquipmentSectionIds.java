package io.github.larsonix.trailoforbis.gear.util;

/**
 * Constants for Hytale inventory section IDs.
 *
 * <p>Maps to {@code PlayerInventory.HOTBAR_SECTION_ID}, etc.
 *
 * @see io.github.larsonix.trailoforbis.gear.equipment.EquipmentListener
 */
public final class EquipmentSectionIds {

    /**
     * Hotbar section ID for weapons and tools.
     * Items here are switched via {@code SwitchActiveSlotEvent}.
     */
    public static final int HOTBAR = -1;

    /**
     * Armor section ID for equipment slots.
     * Items here use {@code SlotFilter} for validation.
     */
    public static final int ARMOR = -3;

    /**
     * Utility section ID for utility items.
     * Items here are switched via {@code SwitchActiveSlotEvent}.
     */
    public static final int UTILITY = -5;

    private EquipmentSectionIds() {} // No instantiation

    /**
     * Checks if a section ID is one of the gear-related sections.
     *
     * @param sectionId The section ID to check
     * @return true if this is a hotbar, armor, or utility section
     */
    public static boolean isGearSection(int sectionId) {
        return sectionId == HOTBAR || sectionId == ARMOR || sectionId == UTILITY;
    }

    /**
     * Gets a human-readable name for a section ID.
     *
     * @param sectionId The section ID
     * @return The section name, or "Unknown" if not recognized
     */
    public static String getSectionName(int sectionId) {
        return switch (sectionId) {
            case HOTBAR -> "Hotbar";
            case ARMOR -> "Armor";
            case UTILITY -> "Utility";
            default -> "Unknown (" + sectionId + ")";
        };
    }
}
