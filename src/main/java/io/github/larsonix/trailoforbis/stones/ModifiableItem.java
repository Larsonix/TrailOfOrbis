package io.github.larsonix.trailoforbis.stones;

import io.github.larsonix.trailoforbis.gear.model.GearRarity;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Sealed interface for items that can be modified by stones.
 *
 * <p>This interface provides a common API for stone operations
 * across different item types (gear and realm maps).
 *
 * <p>Permitted implementations:
 * <ul>
 *   <li>{@code GearData} - RPG gear items</li>
 *   <li>{@code RealmMapData} - Realm map items</li>
 * </ul>
 *
 * <p>Stone operations use this interface to:
 * <ul>
 *   <li>Check if operations are valid (corruption state, modifier count)</li>
 *   <li>Access and modify item properties</li>
 *   <li>Create modified copies via builders</li>
 * </ul>
 *
 * @see ItemModifier
 * @see ModifiableItemBuilder
 * @see StoneType
 */
public interface ModifiableItem {

    /**
     * Gets the item level.
     *
     * <p>Affects modifier value ranges and stone effects.
     *
     * @return 1-10000
     */
    int level();

    /**
     * Gets the item rarity.
     *
     * <p>Determines maximum modifier count and value multipliers.
     *
     */
    @Nonnull
    GearRarity rarity();

    /**
     * Gets the item quality.
     *
     * <p>Quality affects certain bonuses (e.g., IIQ on maps).
     * Range is typically 1-100, with 101 being "perfect".
     *
     */
    int quality();

    /**
     * Checks if this item is corrupted.
     *
     * <p>Corrupted items cannot be modified by most stones.
     *
     * @return true if corrupted
     */
    boolean corrupted();

    /**
     * Gets all modifiers on this item.
     *
     * @return Immutable list of modifiers
     */
    @Nonnull
    List<? extends ItemModifier> modifiers();

    /**
     * Checks if this item has any unlocked modifiers.
     *
     * <p>Used by stones that only affect unlocked modifiers.
     *
     * @return true if at least one modifier is unlocked
     */
    default boolean hasUnlockedModifiers() {
        return modifiers().stream().anyMatch(m -> !m.isLocked());
    }

    /**
     * Gets the count of unlocked modifiers.
     *
     * @return Number of unlocked modifiers
     */
    default int unlockedModifierCount() {
        return (int) modifiers().stream().filter(m -> !m.isLocked()).count();
    }

    /**
     * Gets the count of locked modifiers.
     *
     * @return Number of locked modifiers
     */
    default int lockedModifierCount() {
        return (int) modifiers().stream().filter(ItemModifier::isLocked).count();
    }

    /**
     * Gets the map quantity bonus (for realm maps).
     *
     * <p>This is a map-specific property that doesn't apply to gear.
     * Gear implementations should return 0.
     *
     * @return Map quantity bonus percentage (0-20 for maps, 0 for gear)
     */
    default int mapQuantityBonus() {
        return 0;
    }

    /**
     * Gets the target type for this item (GEAR_ONLY or MAP_ONLY).
     *
     * <p>Used for dispatch and validation without instanceof checks.
     *
     * @return The item's target type
     */
    @Nonnull
    ItemTargetType itemTargetType();

    /**
     * Creates a copy with updated quality.
     *
     * @param newQuality The new quality value
     * @return A new item with updated quality
     */
    @Nonnull
    ModifiableItem withQuality(int newQuality);

    /**
     * Creates a copy with updated level.
     *
     * @param newLevel The new level value
     * @return A new item with updated level
     */
    @Nonnull
    ModifiableItem withLevel(int newLevel);

    /**
     * Creates a copy with updated rarity.
     *
     * @param newRarity The new rarity tier
     * @return A new item with updated rarity
     */
    @Nonnull
    ModifiableItem withRarity(@Nonnull GearRarity newRarity);

    /**
     * Creates a corrupted copy of this item.
     *
     * @return A new corrupted item
     */
    @Nonnull
    ModifiableItem corrupt();

    /**
     * Checks if this item has been identified.
     *
     * <p>Gear is always identified. Maps may need a Lorekeeper's Scroll.
     *
     * @return true if identified (default: true)
     */
    default boolean isIdentified() {
        return true;
    }

    /**
     * Checks if this item has an implicit stat (e.g., weapon implicit damage).
     *
     * @return true if the item has an implicit (default: false)
     */
    default boolean hasImplicit() {
        return false;
    }

    /**
     * Creates a builder pre-populated with this item's data.
     *
     * <p>Used by stones to create modified copies.
     *
     * @return A builder for creating modified copies
     */
    @Nonnull
    ModifiableItemBuilder<?> toModifiableBuilder();

    /**
     * Checks if this item can be modified by stones.
     *
     * <p>Returns false if corrupted (most stones don't work on corrupted items).
     *
     * @return true if stone modifications are allowed
     */
    default boolean canBeModified() {
        return !corrupted();
    }

    /**
     * Gets the maximum number of modifiers this item can have.
     *
     * <p>Based on rarity.
     *
     * @return Maximum modifier count
     */
    default int maxModifiers() {
        return rarity().getMaxModifiers();
    }

    /**
     * Checks if this item can accept more modifiers.
     *
     * @return true if modifier count is below maximum
     */
    default boolean canAddModifier() {
        return modifiers().size() < maxModifiers();
    }
}
