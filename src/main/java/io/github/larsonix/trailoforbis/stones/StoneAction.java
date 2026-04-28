package io.github.larsonix.trailoforbis.stones;

import javax.annotation.Nonnull;
import java.util.Random;

/**
 * Interface for stone action implementations.
 *
 * <p>Each stone type has a corresponding action that defines what happens
 * when the stone is used on an item.
 *
 * <p>Implementations should:
 * <ul>
 *   <li>Validate the item can be modified</li>
 *   <li>Apply the stone's effect immutably</li>
 *   <li>Return an appropriate result</li>
 * </ul>
 *
 * @see StoneType
 * @see StoneActionResult
 * @see StoneActionRegistry
 */
@FunctionalInterface
public interface StoneAction {

    /**
     * Executes this stone action on an item.
     *
     * @param item The item to modify
     * @param random Random source for any randomization
     * @return The result of the action
     */
    @Nonnull
    StoneActionResult execute(@Nonnull ModifiableItem item, @Nonnull Random random);

    /**
     * Checks if this action can be applied to the given item.
     *
     * <p>Default implementation returns true. Override to add validation.
     *
     * @param item The item to check
     * @return true if the action can be applied
     */
    default boolean canApply(@Nonnull ModifiableItem item) {
        return true;
    }

    /**
     * Gets a description of why this action cannot be applied.
     *
     * <p>Only called when canApply returns false.
     *
     * @param item The item that was rejected
     * @return Explanation message
     */
    @Nonnull
    default String getCannotApplyReason(@Nonnull ModifiableItem item) {
        return "Cannot use on this item.";
    }
}
