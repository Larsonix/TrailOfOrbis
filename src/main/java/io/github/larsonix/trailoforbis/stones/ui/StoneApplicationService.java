package io.github.larsonix.trailoforbis.stones.ui;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

import io.github.larsonix.trailoforbis.stones.ModifiableItem;
import io.github.larsonix.trailoforbis.stones.ModifiableItemIO;
import io.github.larsonix.trailoforbis.stones.StoneActionRegistry;
import io.github.larsonix.trailoforbis.stones.StoneActionResult;
import io.github.larsonix.trailoforbis.stones.StoneType;
import io.github.larsonix.trailoforbis.stones.StoneUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Service that handles the atomic operation of applying a stone to an item.
 *
 * <p>This centralizes all stone application logic for reuse by the picker UI.
 *
 * <p>The service:
 * <ul>
 *   <li>Validates the stone still exists in the expected slot</li>
 *   <li>Validates the target item still exists in the expected slot</li>
 *   <li>Executes the stone action via StoneActionRegistry</li>
 *   <li>Updates the target item with the modified data</li>
 *   <li>Consumes the stone if the action succeeded</li>
 * </ul>
 *
 * @see StonePickerPage
 */
public class StoneApplicationService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final StoneActionRegistry actionRegistry;

    /**
     * Creates a new stone application service.
     *
     * @param actionRegistry The registry containing stone action implementations
     */
    public StoneApplicationService(@Nonnull StoneActionRegistry actionRegistry) {
        this.actionRegistry = Objects.requireNonNull(actionRegistry, "actionRegistry cannot be null");
    }

    /**
     * Result of applying a stone.
     *
     * @param success Whether the application succeeded
     * @param message Human-readable result message (legacy, from action)
     * @param updatedTargetItem The updated item stack (null on failure)
     * @param modifiedData The modified item data after stone application (null on failure)
     */
    public record ApplicationResult(
        boolean success,
        @Nonnull String message,
        @Nullable ItemStack updatedTargetItem,
        @Nullable ModifiableItem modifiedData
    ) {
        /**
         * Creates a failure result.
         *
         * @param message The failure message
         * @return Failure result
         */
        public static ApplicationResult failure(@Nonnull String message) {
            return new ApplicationResult(false, message, null, null);
        }

        /**
         * Creates a success result.
         *
         * @param message The success message
         * @param updatedItem The updated item stack
         * @param modifiedData The modified item data
         * @return Success result
         */
        public static ApplicationResult success(
                @Nonnull String message,
                @Nonnull ItemStack updatedItem,
                @Nullable ModifiableItem modifiedData) {
            return new ApplicationResult(true, message, updatedItem, modifiedData);
        }
    }

    /**
     * Applies a stone to a target item, consuming the stone and updating the target.
     *
     * <p>This method performs atomic validation and application:
     * <ol>
     *   <li>Validates the stone still exists in the specified slot</li>
     *   <li>Validates the target item still exists in the specified slot</li>
     *   <li>Executes the stone action</li>
     *   <li>Updates the target item with modified data</li>
     *   <li>Consumes the stone (if the action succeeded)</li>
     * </ol>
     *
     * @param inventory The player's inventory
     * @param stoneType The type of stone being used
     * @param stoneSlot The slot containing the stone
     * @param stoneContainer Which container the stone is in
     * @param targetItem The target ItemStack
     * @param targetData The target's ModifiableItem data
     * @param targetSlot The slot containing the target
     * @param targetContainer Which container the target is in
     * @return Result of the application
     */
    @Nonnull
    public ApplicationResult apply(
            @Nonnull Inventory inventory,
            @Nonnull StoneType stoneType,
            short stoneSlot,
            @Nonnull ContainerType stoneContainer,
            @Nonnull ItemStack targetItem,
            @Nonnull ModifiableItem targetData,
            short targetSlot,
            @Nonnull ContainerType targetContainer) {

        try {
            // 1. Validate stone still exists in slot
            ItemContainer stoneContainerObj = stoneContainer.getContainer(inventory);
            if (stoneContainerObj == null) {
                return ApplicationResult.failure("Cannot access stone container !");
            }

            ItemStack currentStone = stoneContainerObj.getItemStack(stoneSlot);
            if (currentStone == null || currentStone.isEmpty()) {
                return ApplicationResult.failure("Stone no longer in inventory !");
            }

            if (!StoneUtils.isStone(currentStone)) {
                return ApplicationResult.failure("Item is no longer a valid stone !");
            }

            // 2. Validate target still exists in slot
            ItemContainer targetContainerObj = targetContainer.getContainer(inventory);
            if (targetContainerObj == null) {
                return ApplicationResult.failure("Cannot access target container !");
            }

            ItemStack currentTarget = targetContainerObj.getItemStack(targetSlot);
            if (currentTarget == null || currentTarget.isEmpty()) {
                return ApplicationResult.failure("Target item no longer in inventory !");
            }

            // 3. Execute the stone action
            Random random = ThreadLocalRandom.current();
            StoneActionResult result = actionRegistry.execute(stoneType, targetData, random);

            if (!result.success()) {
                return ApplicationResult.failure(result.message());
            }

            // 4. Update the target item
            ItemStack updatedTarget = writeModifiedItem(currentTarget, result.modifiedItem());
            if (updatedTarget == null) {
                return ApplicationResult.failure("Failed to update target item !");
            }

            targetContainerObj.setItemStackForSlot(targetSlot, updatedTarget);

            // 5. Consume the stone (if applicable)
            if (result.stoneConsumed()) {
                stoneContainerObj.removeItemStackFromSlot(stoneSlot, 1);
                LOGGER.atFine().log("Consumed stone from %s slot %d", stoneContainer, stoneSlot);
            }

            LOGGER.atInfo().log("Successfully applied %s to item in %s slot %d",
                stoneType.getDisplayName(), targetContainer, targetSlot);

            return ApplicationResult.success(result.message(), updatedTarget, result.modifiedItem());

        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to apply stone %s", stoneType);
            return ApplicationResult.failure("An error occurred !");
        }
    }

    /**
     * Writes modified item data back to an ItemStack.
     *
     * @param original The original ItemStack
     * @param modified The modified item data
     * @return Updated ItemStack, or null if update failed
     */
    @Nullable
    private ItemStack writeModifiedItem(@Nonnull ItemStack original, @Nullable ModifiableItem modified) {
        return ModifiableItemIO.writeBack(original, modified);
    }

    /**
     * Gets the action registry used by this service.
     *
     * @return The stone action registry
     */
    @Nonnull
    public StoneActionRegistry getActionRegistry() {
        return actionRegistry;
    }
}
