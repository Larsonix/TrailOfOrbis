package io.github.larsonix.trailoforbis.stones.ui;

import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;

import io.github.larsonix.trailoforbis.gear.item.ItemDisplayNameService;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.util.GearUtils;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.items.RealmMapUtils;
import io.github.larsonix.trailoforbis.stones.ItemTargetType;
import io.github.larsonix.trailoforbis.stones.ModifiableItem;
import io.github.larsonix.trailoforbis.stones.StoneAction;
import io.github.larsonix.trailoforbis.stones.StoneActionRegistry;
import io.github.larsonix.trailoforbis.stones.StoneType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Scans a player's inventory to find items compatible with a given stone.
 *
 * <p>Checks all container types (hotbar, storage, utility, armor) and filters
 * items based on the stone's target type and compatibility rules.
 *
 * @see ContainerType
 * @see StonePickerPage
 */
public class CompatibleItemScanner {

    private final ItemDisplayNameService displayNameService;

    /**
     * Creates a new CompatibleItemScanner with the given display name service.
     *
     * @param displayNameService The service for generating item display names
     */
    public CompatibleItemScanner(@Nonnull ItemDisplayNameService displayNameService) {
        this.displayNameService = Objects.requireNonNull(displayNameService, "displayNameService cannot be null");
    }

    /**
     * Record holding a scanned compatible item with its location.
     *
     * @param itemStack The original ItemStack
     * @param data The extracted ModifiableItem data
     * @param slot The slot index within the container
     * @param container Which container the item is in
     * @param displayName Human-readable name for UI display
     * @param translationKey Hytale translation key for localized name
     */
    public record ScannedItem(
        @Nonnull ItemStack itemStack,
        @Nonnull ModifiableItem data,
        short slot,
        @Nonnull ContainerType container,
        @Nonnull String displayName,
        @Nonnull String translationKey
    ) {
        /**
         * Checks if this is a realm map item.
         *
         * @return true if the data targets maps
         */
        public boolean isMap() {
            return data.itemTargetType() == ItemTargetType.MAP_ONLY;
        }

        /**
         * Checks if this is a gear item.
         *
         * @return true if the data targets gear
         */
        public boolean isGear() {
            return data.itemTargetType() == ItemTargetType.GEAR_ONLY;
        }

        /**
         * Gets the item type label for display.
         *
         * @return "MAP" or "GEAR"
         */
        @Nonnull
        public String getTypeLabel() {
            return isMap() ? "MAP" : "GEAR";
        }
    }

    /**
     * Scans the player's inventory for items compatible with the given stone.
     *
     * <p>Searches hotbar, storage, utility, and armor containers in that order.
     * Uses the registry to apply action-specific compatibility checks.
     *
     * @param inventory The player's inventory
     * @param stoneType The type of stone to check compatibility for
     * @param registry The stone action registry for action-specific validation
     * @return List of compatible items with their locations
     */
    @Nonnull
    public List<ScannedItem> scan(
            @Nonnull Inventory inventory,
            @Nonnull StoneType stoneType,
            @Nonnull StoneActionRegistry registry) {
        List<ScannedItem> items = new ArrayList<>();
        StoneAction action = registry.getAction(stoneType);

        // Scan each container type: armor first, then hotbar, storage, utility
        scanContainer(inventory.getArmor(), ContainerType.ARMOR, stoneType, action, items);
        scanContainer(inventory.getHotbar(), ContainerType.HOTBAR, stoneType, action, items);
        scanContainer(inventory.getStorage(), ContainerType.STORAGE, stoneType, action, items);
        scanContainer(inventory.getUtility(), ContainerType.UTILITY, stoneType, action, items);

        return items;
    }

    /**
     * Scans the player's inventory for items compatible with the given stone.
     *
     * <p>Searches armor, hotbar, storage, and utility containers in that order.
     *
     * <p><b>Note:</b> This overload uses only basic stone type checks (target type + corruption).
     * For full action-specific filtering (e.g., rarity requirements, modifier count),
     * use {@link #scan(Inventory, StoneType, StoneActionRegistry)} instead.
     *
     * @param inventory The player's inventory
     * @param stoneType The type of stone to check compatibility for
     * @return List of compatible items with their locations
     * @deprecated Use {@link #scan(Inventory, StoneType, StoneActionRegistry)} for proper filtering
     */
    @Deprecated
    @Nonnull
    public List<ScannedItem> scan(@Nonnull Inventory inventory, @Nonnull StoneType stoneType) {
        List<ScannedItem> items = new ArrayList<>();

        // Scan each container type: armor first (no action-specific filtering)
        scanContainer(inventory.getArmor(), ContainerType.ARMOR, stoneType, null, items);
        scanContainer(inventory.getHotbar(), ContainerType.HOTBAR, stoneType, null, items);
        scanContainer(inventory.getStorage(), ContainerType.STORAGE, stoneType, null, items);
        scanContainer(inventory.getUtility(), ContainerType.UTILITY, stoneType, null, items);

        return items;
    }

    /**
     * Scans a single container for compatible items.
     *
     * @param container The container to scan
     * @param type The container type (for location tracking)
     * @param stoneType The stone type for basic compatibility checks
     * @param action The stone action for action-specific validation (may be null)
     * @param items The list to add compatible items to
     */
    private void scanContainer(
            @Nullable ItemContainer container,
            @Nonnull ContainerType type,
            @Nonnull StoneType stoneType,
            @Nullable StoneAction action,
            @Nonnull List<ScannedItem> items) {

        if (container == null) {
            return;
        }

        short capacity = container.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack item = container.getItemStack(slot);
            if (item == null || item.isEmpty()) {
                continue;
            }

            ModifiableItem modifiable = extractModifiableItem(item);
            if (modifiable == null) {
                continue;
            }

            // Check basic stone type compatibility (target type + corruption)
            if (!stoneType.canUseOn(modifiable)) {
                continue;
            }

            // Check action-specific validation if registry was provided
            if (action != null && !action.canApply(modifiable)) {
                continue;
            }

            String displayName = displayNameService.getDisplayName(modifiable, item);
            String translationKey = getTranslationKey(item);
            items.add(new ScannedItem(item, modifiable, slot, type, displayName, translationKey));
        }
    }

    /**
     * Extracts a ModifiableItem from an ItemStack.
     *
     * <p>Currently supports:
     * <ul>
     *   <li>Realm Maps ({@link RealmMapData})</li>
     *   <li>Gear Items ({@link GearData})</li>
     * </ul>
     *
     * @param itemStack The item to extract from
     * @return The modifiable item data, or null if not supported
     */
    @Nullable
    private ModifiableItem extractModifiableItem(@Nonnull ItemStack itemStack) {
        // Try realm map first
        Optional<RealmMapData> mapOpt = RealmMapUtils.readMapData(itemStack);
        if (mapOpt.isPresent()) {
            return mapOpt.get();
        }

        // Try gear
        Optional<GearData> gearOpt = GearUtils.readGearData(itemStack);
        if (gearOpt.isPresent()) {
            // Verify the base Hytale item exists in the asset map. If it doesn't,
            // ItemDefinitionBuilder.build() returned null and the client was never
            // sent this item's UpdateItems definition. Including an undefined item
            // in an item-grid crashes the client (NullReferenceException in the UI
            // layout tree when it tries to render an item with no definition).
            Item baseItem = GearUtils.getBaseItem(itemStack);
            if (baseItem == Item.UNKNOWN) {
                return null;
            }
            return gearOpt.get();
        }

        return null;
    }

    /**
     * Gets the translation key for an item.
     *
     * <p>Uses the Item asset's translation key for proper localized display.
     *
     * @return translation key for Message.translation()
     */
    @Nonnull
    private String getTranslationKey(@Nonnull ItemStack item) {
        Item itemAsset = item.getItem();
        if (itemAsset != null) {
            return itemAsset.getTranslationKey();
        }
        // Fallback to constructed key from item ID
        String itemId = item.getItemId();
        if (itemId != null && !itemId.isEmpty()) {
            return "server.items." + itemId + ".name";
        }
        return "server.items.unknown.name";
    }
}
