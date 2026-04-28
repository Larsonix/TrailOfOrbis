package io.github.larsonix.trailoforbis.attributes;

import io.github.larsonix.trailoforbis.database.models.PlayerData;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Enumeration of player attributes in the RPG system.
 *
 * <p>The elemental attribute system replaces traditional RPG attributes with
 * 6 elements that each represent a distinct playstyle archetype:
 * <ul>
 *   <li>FIRE: physical damage, charged attack damage, crit multiplier, burn damage, ignite chance</li>
 *   <li>WATER: spell damage, mana, energy shield, mana regen, freeze chance</li>
 *   <li>LIGHTNING: attack speed, move speed, crit chance, stamina regen, shock chance</li>
 *   <li>EARTH: max health %, armor, health regen, block chance, knockback resistance</li>
 *   <li>WIND: evasion, accuracy, projectile damage, jump force, projectile speed</li>
 *   <li>VOID: life steal, % hit as true damage, DoT damage, mana on kill, effect duration</li>
 * </ul>
 *
 * <p>Supports case-insensitive lookup:
 * <pre>
 * AttributeType.fromString("FIRE")      // returns FIRE
 * AttributeType.fromString("fire")      // returns FIRE
 * AttributeType.fromString("INVALID")   // returns null
 * </pre>
 */
public enum AttributeType {
    FIRE("Fire", "#FF7755", "Physical damage, charged attack damage, crit multiplier, burn damage, ignite chance"),
    WATER("Water", "#55CCEE", "Spell damage, mana, energy shield, mana regen, freeze chance"),
    LIGHTNING("Lightning", "#FFEE55", "Attack speed, move speed, crit chance, stamina regen, shock chance"),
    EARTH("Earth", "#DDAA55", "Max health %, armor, health regen, block chance, knockback resistance"),
    WIND("Wind", "#77DD77", "Evasion, accuracy, projectile damage, jump force, projectile speed"),
    VOID("Void", "#BB77DD", "Life steal, % hit as true damage, DoT damage, mana on kill, effect duration");

    private static final Map<String, AttributeType> LOOKUP = new HashMap<>();

    static {
        for (AttributeType type : values()) {
            // Full name
            LOOKUP.put(type.name().toUpperCase(), type);
            LOOKUP.put(type.displayName.toUpperCase(), type);
        }
    }

    private final String displayName;
    private final String color;
    private final String description;

    AttributeType(String displayName, String color, String description) {
        this.displayName = displayName;
        this.color = color;
        this.description = description;
    }

    /**
     * @return Human-readable display name (e.g., "Fire")
     */
    @Nonnull
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets the hex color code for this attribute.
     *
     * <p>Use with Hytale's Message API: {@code Message.raw(type.getDisplayName()).color(type.getHexColor())}
     *
     * @return Hex color code (e.g., "#FF4444" for red)
     */
    @Nonnull
    public String getHexColor() {
        return color;
    }

    /** Gets the description of what this attribute does. */
    @Nonnull
    public String getDescription() {
        return description;
    }

    /**
     * Parses an attribute type from a string (case-insensitive).
     *
     * <p>Supports full names:
     * <ul>
     *   <li>FIRE, Fire, fire → FIRE</li>
     *   <li>WATER, Water, water → WATER</li>
     *   <li>LIGHTNING, Lightning, lightning → LIGHTNING</li>
     *   <li>EARTH, Earth, earth → EARTH</li>
     *   <li>WIND, Wind, wind → WIND</li>
     *   <li>VOID, Void, void → VOID</li>
     * </ul>
     *
     * @return The matching AttributeType, or null if not found
     */
    @Nullable
    public static AttributeType fromString(@Nullable String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return LOOKUP.get(name.toUpperCase().trim());
    }

    // ==================== PlayerData Accessors ====================

    /** Gets the value of this attribute from player data. */
    public int getValue(@Nonnull PlayerData data) {
        return switch (this) {
            case FIRE -> data.getFire();
            case WATER -> data.getWater();
            case LIGHTNING -> data.getLightning();
            case EARTH -> data.getEarth();
            case WIND -> data.getWind();
            case VOID -> data.getVoidAttr();
        };
    }

    /** Returns new PlayerData with this attribute set to the specified value. */
    @Nonnull
    public PlayerData withValue(@Nonnull PlayerData data, int value) {
        return switch (this) {
            case FIRE -> data.withFire(value);
            case WATER -> data.withWater(value);
            case LIGHTNING -> data.withLightning(value);
            case EARTH -> data.withEarth(value);
            case WIND -> data.withWind(value);
            case VOID -> data.withVoidAttr(value);
        };
    }

    /** Returns new PlayerData with this attribute incremented by 1. */
    @Nonnull
    public PlayerData increment(@Nonnull PlayerData data) {
        return withValue(data, getValue(data) + 1);
    }
}
