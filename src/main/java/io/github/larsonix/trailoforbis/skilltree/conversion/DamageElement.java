package io.github.larsonix.trailoforbis.skilltree.conversion;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Represents damage elements for the conversion system.
 *
 * <p>This enum is separate from ElementType to keep the conversion system
 * self-contained and to include PHYSICAL which is not in ElementType.
 *
 * <p>Conversion chains follow PoE rules:
 * <ul>
 *   <li>Physical → Any Element → Void</li>
 *   <li>Conversions are applied in order of source element "priority"</li>
 *   <li>Element cannot convert backwards (Fire cannot convert to Physical)</li>
 * </ul>
 */
public enum DamageElement {
    /**
     * Physical damage - can convert to any elemental type.
     */
    PHYSICAL(0, "Physical"),

    /**
     * Fire damage - can convert to Void.
     */
    FIRE(1, "Fire"),

    /**
     * Water damage - Gaia's element. Manifests as ice/frost in combat.
     * Can convert to Fire or Void.
     */
    WATER(2, "Water"),

    /**
     * Lightning damage - can convert to Water, Fire, or Void.
     */
    LIGHTNING(3, "Lightning"),

    /**
     * Void damage - Varyn's element. Final element, cannot convert further.
     */
    VOID(4, "Void");

    private final int priority;
    private final String displayName;

    DamageElement(int priority, @Nonnull String displayName) {
        this.priority = priority;
        this.displayName = displayName;
    }

    /**
     * Gets the conversion priority. Lower numbers convert first.
     *
     * @return The priority value
     */
    public int getPriority() {
        return priority;
    }

    /**
     * Gets the display name for UI.
     *
     * @return The display name
     */
    @Nonnull
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Checks if this element can convert to the target element.
     *
     * <p>Conversion rules (PoE-style):
     * <ul>
     *   <li>Cannot convert to self</li>
     *   <li>Cannot convert backwards (higher priority to lower)</li>
     *   <li>Void cannot convert to anything</li>
     * </ul>
     *
     * @param target The target element
     * @return true if conversion is allowed
     */
    public boolean canConvertTo(@Nonnull DamageElement target) {
        // Cannot convert to self
        if (this == target) return false;
        // Cannot convert if we have higher priority (backwards)
        if (this.priority > target.priority) return false;
        // Void cannot convert to anything
        if (this == VOID) return false;
        return true;
    }

    /**
     * Parses a string to a DamageElement.
     *
     * @param value The string value (case-insensitive)
     * @return The matching element, or null if not found
     */
    @Nullable
    public static DamageElement fromString(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String upper = value.trim().toUpperCase();
        // Handle common aliases
        if ("ICE".equals(upper) || "COLD".equals(upper)) return WATER;
        if ("CHAOS".equals(upper)) return VOID;
        try {
            return valueOf(upper);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
