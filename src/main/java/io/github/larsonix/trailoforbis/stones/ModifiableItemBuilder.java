package io.github.larsonix.trailoforbis.stones;

import io.github.larsonix.trailoforbis.gear.model.GearRarity;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Builder interface for creating modified copies of items.
 *
 * <p>Used by stone operations to modify items immutably.
 * Each implementation handles the specifics of its item type.
 *
 * <p>Type parameter T is the concrete builder type for method chaining.
 *
 * @param <T> The concrete builder type (for fluent API)
 * @see ModifiableItem
 * @see StoneType
 */
public interface ModifiableItemBuilder<T extends ModifiableItemBuilder<T>> {

    /**
     * Sets the item level.
     *
     * @param level The new level (1-10000)
     * @return This builder for chaining
     */
    @Nonnull
    T level(int level);

    /**
     * Sets the item rarity.
     *
     * @param rarity The new rarity
     * @return This builder for chaining
     */
    @Nonnull
    T rarity(@Nonnull GearRarity rarity);

    /**
     * Sets the item quality.
     *
     * @param quality The new quality (1-101)
     * @return This builder for chaining
     */
    @Nonnull
    T quality(int quality);

    /**
     * Sets whether the item is corrupted.
     *
     * @param corrupted The corruption state
     * @return This builder for chaining
     */
    @Nonnull
    T corrupted(boolean corrupted);

    /**
     * Sets the modifiers on this item.
     *
     * <p>Replaces all existing modifiers.
     *
     * @param modifiers The new modifier list
     * @return This builder for chaining
     */
    @Nonnull
    T modifiers(@Nonnull List<? extends ItemModifier> modifiers);

    /**
     * Adds a modifier to the item.
     *
     * @param modifier The modifier to add
     * @return This builder for chaining
     */
    @Nonnull
    T addModifier(@Nonnull ItemModifier modifier);

    /**
     * Removes a modifier from the item by index.
     *
     * @param index The index of the modifier to remove
     * @return This builder for chaining
     */
    @Nonnull
    T removeModifier(int index);

    /**
     * Clears all modifiers from the item.
     *
     * @return This builder for chaining
     */
    @Nonnull
    T clearModifiers();

    /**
     * Sets the map quantity bonus (for realm maps).
     *
     * <p>This method may be a no-op for gear builders.
     *
     * @param bonus The map quantity bonus (0-20)
     * @return This builder for chaining
     */
    @Nonnull
    T mapQuantityBonus(int bonus);

    /**
     * Builds the modified item.
     *
     * @return The new item instance
     */
    @Nonnull
    ModifiableItem build();
}
