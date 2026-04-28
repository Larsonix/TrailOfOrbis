package io.github.larsonix.trailoforbis.lootfilter.model;

/**
 * Categories for grouping modifiers in the filter UI's modifier picker.
 *
 * <p>Finer-grained than {@code StatCategory} (8 vs 6 categories) because
 * "Offense" alone would contain 50+ modifiers, making the picker unusable.
 */
public enum ModifierFilterCategory {
    DAMAGE("Damage", "#f44336"),
    CRITICAL("Critical", "#ff7043"),
    PENETRATION("Penetration", "#e91e63"),
    AILMENT("Ailment", "#ab47bc"),
    DEFENSE("Defense", "#2196f3"),
    RESOURCES("Resources", "#4caf50"),
    MOVEMENT("Movement", "#ff9800"),
    UTILITY("Utility", "#78909c");

    private final String displayName;
    private final String color;

    ModifierFilterCategory(String displayName, String color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColor() {
        return color;
    }
}
