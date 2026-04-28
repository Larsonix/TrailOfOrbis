package io.github.larsonix.trailoforbis.elemental;

import javax.annotation.Nonnull;

/**
 * Elemental damage types in the RPG system.
 *
 * <p>Based on Hytale's canonical 6-element lore system.
 * Each element has independent damage scaling and resistance.
 * Elements are used for both player and mob damage calculations.
 *
 * <p>Formula: {@code (Base + Flat) × (1 + Percent/100) × (1 + Multiplier/100)}
 * <p>Resistance: {@code damage × (1 - min(resistance, 75) / 100)}
 */
public enum ElementType {
    /**
     * Fire damage - burns and deals damage over time.
     * Color: Red (§c)
     */
    FIRE("Fire", "§c", 'F'),

    /**
     * Water damage - Gaia's element. Manifests as ice/frost in combat.
     * Color: Aqua (§b)
     */
    WATER("Water", "§b", 'W'),

    /**
     * Lightning damage - shocks and chains.
     * Color: Yellow (§e)
     */
    LIGHTNING("Lightning", "§e", 'L'),

    /**
     * Earth damage - Gaia's element. Seismic force and stone.
     * Color: Brown (§6) - earthy, mountainous
     */
    EARTH("Earth", "§6", 'E'),

    /**
     * Wind damage - Gaia's element. Air currents and cutting gales.
     * Color: Light Green (§a) - pale, airy
     * Note: 'A' for Air since 'W' is taken by Water.
     */
    WIND("Wind", "§a", 'A'),

    /**
     * Void damage - Varyn's element. Corruption and entropy.
     * Color: Purple (§5)
     */
    VOID("Void", "§5", 'V');

    private final String displayName;
    private final String colorCode;
    private final char shortCode;

    ElementType(@Nonnull String displayName, @Nonnull String colorCode, char shortCode) {
        this.displayName = displayName;
        this.colorCode = colorCode;
        this.shortCode = shortCode;
    }

    /**
     * Gets the display name for this element.
     *
     * @return Human-readable name (e.g., "Fire", "Lightning")
     */
    @Nonnull
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets the Minecraft-style color code for this element.
     *
     * @return Color code (e.g., "§c" for red)
     */
    @Nonnull
    public String getColorCode() {
        return colorCode;
    }

    /**
     * Gets the short code for compact display.
     *
     * @return Single character (F, C, L, X)
     */
    public char getShortCode() {
        return shortCode;
    }

    /**
     * Gets the formatted short indicator with color.
     *
     * @return Colored indicator like "§c[F]"
     */
    @Nonnull
    public String getColoredIndicator() {
        return colorCode + "[" + shortCode + "]";
    }

    /**
     * Gets the formatted display name with color.
     *
     * @return Colored name like "§cFire"
     */
    @Nonnull
    public String getColoredName() {
        return colorCode + displayName;
    }
}
