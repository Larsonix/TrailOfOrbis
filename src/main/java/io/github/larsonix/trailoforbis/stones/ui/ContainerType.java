package io.github.larsonix.trailoforbis.stones.ui;

import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Represents the type of inventory container an item is stored in.
 *
 * <p>Used by the stone picker UI to track where items are located
 * so they can be properly updated after stone application.
 *
 * @see CompatibleItemScanner
 * @see StoneApplicationService
 */
public enum ContainerType {
    /**
     * The player's hotbar (main quick-access slots).
     */
    HOTBAR,

    /**
     * The player's main storage inventory.
     */
    STORAGE,

    /**
     * The player's utility slot (off-hand).
     */
    UTILITY,

    /**
     * The player's armor slots (head, chest, legs, hands).
     */
    ARMOR;

    /**
     * Gets the ItemContainer from an Inventory for this container type.
     *
     * @param inventory The player's inventory
     * @return The corresponding container, or null if not available
     */
    @Nullable
    public ItemContainer getContainer(@Nonnull Inventory inventory) {
        return switch (this) {
            case HOTBAR -> inventory.getHotbar();
            case STORAGE -> inventory.getStorage();
            case UTILITY -> inventory.getUtility();
            case ARMOR -> inventory.getArmor();
        };
    }

    /**
     * Gets a display name for this container type.
     *
     * @return Human-readable name
     */
    @Nonnull
    public String getDisplayName() {
        return switch (this) {
            case HOTBAR -> "Hotbar";
            case STORAGE -> "Inventory";
            case UTILITY -> "Utility";
            case ARMOR -> "Armor";
        };
    }
}
