package io.github.larsonix.trailoforbis.stones;

import javax.annotation.Nonnull;

/**
 * Common interface for modifiers that can appear on items.
 *
 * <p>Implemented by both gear modifiers and realm map modifiers,
 * enabling shared stone functionality (rerolling, locking, etc.).
 *
 * <p>Example implementations:
 * <ul>
 *   <li>{@code GearModifier} - Prefixes and suffixes on gear</li>
 *   <li>{@code RealmModifier} - Modifiers on realm maps</li>
 * </ul>
 *
 * @see ModifiableItem
 * @see io.github.larsonix.trailoforbis.maps.modifiers.RealmModifier
 */
public interface ItemModifier {

    /**
     * Gets the unique identifier for this modifier.
     *
     * <p>Used for serialization and lookup.
     *
     * @return The modifier ID
     */
    @Nonnull
    String id();

    /**
     * Gets the display name for this modifier.
     *
     * <p>Used in tooltips and UI.
     *
     * @return The display name
     */
    @Nonnull
    String displayName();

    /**
     * Gets the numeric value of this modifier.
     *
     * <p>Interpretation depends on the modifier type.
     * For example, "+20% damage" would have value 20.
     *
     * @return The modifier value
     */
    double getValue();

    /**
     * Checks if this modifier is locked.
     *
     * <p>Locked modifiers cannot be rerolled by stones
     * (e.g., Divine Orb won't affect locked modifiers).
     *
     * @return true if locked
     */
    boolean isLocked();

    /**
     * Creates a copy of this modifier with the locked state changed.
     *
     * @param locked The new locked state
     * @return A new modifier instance with updated locked state
     */
    @Nonnull
    ItemModifier withLocked(boolean locked);

    /**
     * Creates a copy of this modifier with a new value.
     *
     * <p>Used by rerolling stones (Divine Orb, Blessed Orb).
     *
     * @param newValue The new value
     * @return A new modifier instance with updated value
     */
    @Nonnull
    ItemModifier withValue(double newValue);

    /**
     * Formats this modifier for display in a tooltip.
     *
     * <p>Should return a human-readable string like
     * "+20% increased Physical Damage" or "Monsters deal 40% increased Damage".
     *
     * @return Formatted tooltip string
     */
    @Nonnull
    String formatForTooltip();

    /**
     * Gets a short type label for display (e.g., "PREFIX", "SUFFIX", "MOD").
     *
     * <p>Default returns "MOD" for generic modifiers.
     * Gear modifiers override to return the modifier type name.
     *
     * @return Short type label
     */
    @Nonnull
    default String typeLabel() {
        return "MOD";
    }

    /**
     * Checks if this modifier uses percentage values.
     *
     * <p>Default returns false. Gear modifiers override based on stat type.
     *
     * @return true if values should be displayed with % suffix
     */
    default boolean isPercent() {
        return false;
    }
}
