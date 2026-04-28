package io.github.larsonix.trailoforbis.gear.model;

/**
 * Distinguishes prefix modifiers from suffix modifiers.
 *
 * <p>Prefixes are typically offensive (damage, penetration, attack speed).
 * Suffixes are typically defensive or utility (health, armor, resistances, movement).
 *
 * <p>This distinction affects:
 * <ul>
 *   <li>Which modifier pool is used during generation</li>
 *   <li>Display order in tooltips (prefixes before item name, suffixes after)</li>
 *   <li>Which stones can manipulate them (Prefix Stone vs Suffix Stone)</li>
 * </ul>
 */
public enum ModifierType {

    /**
     * Prefix modifiers appear before the item name.
     * Example: "Sharp Iron Sword" - "Sharp" is the prefix.
     * Typically offensive stats.
     */
    PREFIX("prefix"),

    /**
     * Suffix modifiers appear after the item name.
     * Example: "Iron Sword of the Whale" - "of the Whale" is the suffix.
     * Typically defensive or utility stats.
     */
    SUFFIX("suffix");

    private final String configKey;

    ModifierType(String configKey) {
        this.configKey = configKey;
    }

    /**
     * Returns the key used in configuration files.
     * Used when loading modifier definitions from gear-modifiers.yml.
     *
     * @return "prefix" or "suffix"
     */
    public String getConfigKey() {
        return configKey;
    }

    /**
     * Parse from config key string.
     *
     * @param key The config key ("prefix" or "suffix")
     * @return The corresponding ModifierType
     * @throws NullPointerException if key is null
     * @throws IllegalArgumentException if key is not recognized
     */
    public static ModifierType fromConfigKey(String key) {
        java.util.Objects.requireNonNull(key, "Config key cannot be null");
        for (ModifierType type : values()) {
            if (type.configKey.equalsIgnoreCase(key)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown modifier type: " + key);
    }
}
