package io.github.larsonix.trailoforbis.combat;

import io.github.larsonix.trailoforbis.compat.HytaleAPICompat;
import io.github.larsonix.trailoforbis.elemental.ElementType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Defines the different types of damage in the RPG system.
 *
 * <p>Each damage type has:
 * <ul>
 *   <li>Normal and critical damage cause IDs (for asset lookup)</li>
 *   <li>Associated colors (for display)</li>
 *   <li>Cached cause indexes (lazy-loaded from asset map)</li>
 * </ul>
 *
 * <p>The damage cause determines the {@code DamageTextColor} sent to the client,
 * which affects the floating damage number display.
 *
 * <p><b>Adding New Damage Types:</b>
 * <ol>
 *   <li>Add enum constant with cause IDs and colors</li>
 *   <li>Create corresponding JSON assets in {@code assets/trailoforbis/Damage/Cause/}</li>
 * </ol>
 */
public enum DamageType {

    /** Physical damage (melee, projectiles). Normal: white, Crit: red */
    PHYSICAL("Rpg_Physical", "Rpg_Physical_Crit", "#FFFFFF", "#FF4444"),

    /** Magic damage (spells, abilities). Normal: blue, Crit: purple */
    MAGIC("Rpg_Magic", "Rpg_Magic_Crit", "#44AAFF", "#FF44FF"),

    /** Fire elemental damage. Normal: orange, Crit: bright red */
    FIRE("Rpg_Fire", "Rpg_Fire_Crit", "#FF6600", "#FF2200"),

    /** Water elemental damage. Normal: light blue, Crit: bright cyan */
    WATER("Rpg_Water", "Rpg_Water_Crit", "#44CCFF", "#88EEFF"),

    /** Lightning elemental damage. Normal: yellow, Crit: bright yellow */
    LIGHTNING("Rpg_Lightning", "Rpg_Lightning_Crit", "#FFEE00", "#FFFFAA"),

    /** Earth elemental damage. Normal: brown, Crit: bright brown */
    EARTH("Rpg_Earth", "Rpg_Earth_Crit", "#CC8833", "#FFAA44"),

    /** Wind elemental damage. Normal: light green, Crit: bright green */
    WIND("Rpg_Wind", "Rpg_Wind_Crit", "#88FF88", "#CCFFCC"),

    /** Void elemental damage. Normal: purple, Crit: bright purple */
    VOID("Rpg_Void", "Rpg_Void_Crit", "#AA44FF", "#DD88FF");

    private final String normalCauseId;
    private final String critCauseId;
    private final String normalColor;
    private final String critColor;

    // Cached indexes (lazy-loaded)
    private volatile int normalCauseIndex = Integer.MIN_VALUE;
    private volatile int critCauseIndex = Integer.MIN_VALUE;

    /**
     * Creates a damage type with associated cause IDs and colors.
     *
     * @param normalCauseId Asset ID for normal damage cause
     * @param critCauseId Asset ID for critical damage cause
     * @param normalColor Hex color for normal damage (e.g., "#FFFFFF")
     * @param critColor Hex color for critical damage (e.g., "#FF4444")
     */
    DamageType(
        @Nonnull String normalCauseId,
        @Nonnull String critCauseId,
        @Nonnull String normalColor,
        @Nonnull String critColor
    ) {
        this.normalCauseId = normalCauseId;
        this.critCauseId = critCauseId;
        this.normalColor = normalColor;
        this.critColor = critColor;
    }

    /**
     * Gets the damage cause ID for this type.
     *
     * @param critical Whether this is a critical hit
     * @return The damage cause asset ID
     */
    @Nonnull
    public String getCauseId(boolean critical) {
        return critical ? critCauseId : normalCauseId;
    }

    /**
     * Gets the display color for this damage type.
     *
     * @param critical Whether this is a critical hit
     * @return Hex color string (e.g., "#FF4444")
     */
    @Nonnull
    public String getColor(boolean critical) {
        return critical ? critColor : normalColor;
    }

    /**
     * Gets the damage cause index from the asset map.
     *
     * <p>Uses {@link HytaleAPICompat} for safe asset lookup with caching.
     * Returns {@code Integer.MIN_VALUE} if the asset is not registered.
     *
     * @param critical Whether this is a critical hit
     * @return The damage cause index, or {@code Integer.MIN_VALUE} if not found
     */
    public int getCauseIndex(boolean critical) {
        if (critical) {
            if (critCauseIndex == Integer.MIN_VALUE) {
                critCauseIndex = HytaleAPICompat.getDamageCauseIndex(critCauseId);
            }
            return critCauseIndex;
        } else {
            if (normalCauseIndex == Integer.MIN_VALUE) {
                normalCauseIndex = HytaleAPICompat.getDamageCauseIndex(normalCauseId);
            }
            return normalCauseIndex;
        }
    }

    /**
     * Checks if the damage cause assets are registered.
     *
     * @return true if both normal and crit cause assets are available
     */
    public boolean isRegistered() {
        return getCauseIndex(false) != Integer.MIN_VALUE
            && getCauseIndex(true) != Integer.MIN_VALUE;
    }

    @Nonnull
    public String getNormalCauseId() {
        return normalCauseId;
    }

    @Nonnull
    public String getCritCauseId() {
        return critCauseId;
    }

    @Nonnull
    public String getNormalColor() {
        return normalColor;
    }

    @Nonnull
    public String getCritColor() {
        return critColor;
    }

    /**
     * Maps an {@link ElementType} to its corresponding DamageType.
     *
     * @param element The element type, or null for physical
     * @return The matching elemental DamageType, or {@link #PHYSICAL} if null
     */
    @Nonnull
    public static DamageType fromElement(@Nullable ElementType element) {
        if (element == null) {
            return PHYSICAL;
        }
        return switch (element) {
            case FIRE -> FIRE;
            case WATER -> WATER;
            case LIGHTNING -> LIGHTNING;
            case EARTH -> EARTH;
            case WIND -> WIND;
            case VOID -> VOID;
        };
    }
}
