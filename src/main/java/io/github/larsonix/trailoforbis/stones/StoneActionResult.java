package io.github.larsonix.trailoforbis.stones;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Result of a stone action execution.
 *
 * <p>Contains the outcome of using a stone on an item, including:
 * <ul>
 *   <li>Success/failure status</li>
 *   <li>The modified item (if successful)</li>
 *   <li>Error message (if failed)</li>
 *   <li>Whether the stone was consumed</li>
 * </ul>
 *
 * @param success Whether the action succeeded
 * @param modifiedItem The modified item, or null if failed
 * @param message Feedback message for the player
 * @param stoneConsumed Whether the stone should be consumed
 * @see StoneAction
 */
public record StoneActionResult(
    boolean success,
    @Nullable ModifiableItem modifiedItem,
    @Nonnull String message,
    boolean stoneConsumed
) {

    // ═══════════════════════════════════════════════════════════════════
    // FACTORY METHODS - SUCCESS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a successful result with the modified item.
     *
     * @param item The modified item
     * @param message Success message
     * @return Success result
     */
    @Nonnull
    public static StoneActionResult success(@Nonnull ModifiableItem item, @Nonnull String message) {
        return new StoneActionResult(true, item, message, true);
    }

    /**
     * Creates a successful result with a default message.
     *
     * @param item The modified item
     * @return Success result
     */
    @Nonnull
    public static StoneActionResult success(@Nonnull ModifiableItem item) {
        return success(item, "Item modified successfully.");
    }

    // ═══════════════════════════════════════════════════════════════════
    // FACTORY METHODS - FAILURE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a failure result.
     *
     * @param message Error message explaining why it failed
     * @return Failure result (stone not consumed)
     */
    @Nonnull
    public static StoneActionResult failure(@Nonnull String message) {
        return new StoneActionResult(false, null, message, false);
    }

    /**
     * Creates a failure because the item is corrupted.
     *
     * @return Corrupted item failure
     */
    @Nonnull
    public static StoneActionResult corruptedItem() {
        return failure("Cannot use on corrupted items.");
    }

    /**
     * Creates a failure because the item type is invalid.
     *
     * @param expectedType The expected item type
     * @return Invalid target failure
     */
    @Nonnull
    public static StoneActionResult invalidTarget(@Nonnull ItemTargetType expectedType) {
        return failure("Can only be used on " + expectedType.getDisplayName().toLowerCase() + ".");
    }

    /**
     * Creates a failure because item has max modifiers.
     *
     * @return Max modifiers failure
     */
    @Nonnull
    public static StoneActionResult maxModifiers() {
        return failure("Item already has the maximum number of modifiers.");
    }

    /**
     * Creates a failure because item has no modifiers.
     *
     * @return No modifiers failure
     */
    @Nonnull
    public static StoneActionResult noModifiers() {
        return failure("Item has no modifiers.");
    }

    /**
     * Creates a failure because item has no unlocked modifiers.
     *
     * @return No unlocked modifiers failure
     */
    @Nonnull
    public static StoneActionResult noUnlockedModifiers() {
        return failure("Item has no unlocked modifiers.");
    }

    /**
     * Creates a failure because item is already identified.
     *
     * @return Already identified failure
     */
    @Nonnull
    public static StoneActionResult alreadyIdentified() {
        return failure("Item is already identified.");
    }

    /**
     * Creates a failure because item is at max quality.
     *
     * @return Max quality failure
     */
    @Nonnull
    public static StoneActionResult maxQuality() {
        return failure("Item is already at maximum quality.");
    }

    /**
     * Creates a failure because item is at max rarity.
     *
     * @return Max rarity failure
     */
    @Nonnull
    public static StoneActionResult maxRarity() {
        return failure("Item is already at maximum rarity.");
    }

    /**
     * Creates a failure because item already has modifiers.
     *
     * @return Has modifiers failure
     */
    @Nonnull
    public static StoneActionResult hasModifiers() {
        return failure("Item already has modifiers.");
    }

    /**
     * Creates a failure for an unspecified reason.
     *
     * @return Generic failure
     */
    @Nonnull
    public static StoneActionResult genericFailure() {
        return failure("Cannot use this stone on this item.");
    }

    // ═══════════════════════════════════════════════════════════════════
    // QUERY METHODS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the modified item as an Optional.
     *
     * @return Optional containing the item, or empty if failed
     */
    @Nonnull
    public Optional<ModifiableItem> getModifiedItem() {
        return Optional.ofNullable(modifiedItem);
    }

    /**
     * Checks if this result is a failure.
     *
     * @return true if failed
     */
    public boolean isFailure() {
        return !success;
    }
}
