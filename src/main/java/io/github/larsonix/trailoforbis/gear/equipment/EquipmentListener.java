package io.github.larsonix.trailoforbis.gear.equipment;

import io.github.larsonix.trailoforbis.gear.equipment.EquipmentValidator.ValidationResult;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.util.EquipmentSectionIds;
import io.github.larsonix.trailoforbis.gear.util.GearUtils;
import io.github.larsonix.trailoforbis.gems.model.GemData;
import io.github.larsonix.trailoforbis.gems.socket.GemSocketPage;
import io.github.larsonix.trailoforbis.gems.util.GemUtils;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.protocol.ItemArmorSlot;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemArmor;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterActionType;
import com.hypixel.hytale.server.core.inventory.container.filter.SlotFilter;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Event handlers for equipment validation.
 *
 * <p>Intercepts equipment attempts and blocks them if requirements aren't met.
 * Provides feedback to the player on failure.
 *
 * <p>Interception points:
 * <ul>
 *   <li>{@code SwitchActiveSlotEvent} - For weapons and utilities</li>
 *   <li>Custom {@code SlotFilter} - For armor (requires inventory hook)</li>
 *   <li>{@code LivingEntityInventoryChangeEvent} - For reactive validation</li>
 * </ul>
 */
public final class EquipmentListener {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Inventory section IDs
    private static final int SECTION_HOTBAR = EquipmentSectionIds.HOTBAR;    // Weapons
    private static final int SECTION_ARMOR = EquipmentSectionIds.ARMOR;      // Armor
    private static final int SECTION_UTILITY = EquipmentSectionIds.UTILITY;  // Utilities

    private final EquipmentValidator validator;
    private final EquipmentFeedback feedback;

    /**
     * Creates an EquipmentListener.
     *
     * @param validator The equipment validator
     * @param feedback The feedback provider for player messages
     */
    public EquipmentListener(EquipmentValidator validator, EquipmentFeedback feedback) {
        this.validator = Objects.requireNonNull(validator);
        this.feedback = Objects.requireNonNull(feedback);
    }

    // =========================================================================
    // REGISTRATION
    // =========================================================================

    /**
     * Registers all equipment validation event handlers.
     *
     * <p>This method registers:
     * <ul>
     *   <li>Global inventory change listener for reactive validation</li>
     *   <li>Armor slot filters are set up via {@link #setupArmorValidation} on player join</li>
     * </ul>
     *
     * <p>Note: SwitchActiveSlotEvent is an ECS event requiring special handling.
     * Weapon/utility validation is done via {@link #validateSlotSwitch} called
     * from ECS handlers or slot filters.
     *
     * @param registry The event registry
     */
    public void register(EventRegistry registry) {
        // Note: Inventory change listener is now registered via InventoryChangeEventSystem.
        // Call addHandler() on the system instead of registerGlobal().
        LOGGER.at(Level.INFO).log("Equipment validation system initialized");
    }

    // =========================================================================
    // WEAPON/UTILITY VALIDATION
    // =========================================================================

    /**
     * Validates a slot switch and returns whether it should be allowed.
     *
     * <p>Note: SwitchActiveSlotEvent is an ECS event that doesn't provide direct
     * entity access. This method is designed to be called with the player context
     * from the appropriate ECS handler.
     *
     * @param player The player switching slots
     * @param sectionId The inventory section ID
     * @param newSlot The slot being switched to
     * @return true if the switch should be allowed, false to block
     */
    public boolean validateSlotSwitch(Player player, int sectionId, byte newSlot) {
        // Only care about hotbar (weapons) and utility
        if (sectionId != SECTION_HOTBAR && sectionId != SECTION_UTILITY) {
            return true;  // Allow switches in other sections
        }

        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return true;
        }

        ItemContainer container = getContainerForSection(inventory, sectionId);
        if (container == null) {
            return true;
        }

        if (newSlot < 0 || newSlot >= container.getCapacity()) {
            return true;  // Invalid slot, let other systems handle
        }

        ItemStack item = container.getItemStack(newSlot);
        if (item == null || item.isEmpty()) {
            return true;  // Switching to empty slot is always fine
        }

        // Get player info via ECS component lookup (future-proof pattern)
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            return true;
        }
        Store<EntityStore> store = ref.getStore();
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return true;
        }

        // Check if it's RPG gear with requirements
        UUID playerId = playerRef.getUuid();
        ValidationResult result = validator.checkRequirements(playerId, item);

        if (!result.canEquip()) {
            // Send feedback to player
            String slotType = sectionId == SECTION_HOTBAR ? "weapon" : "utility";
            feedback.sendRequirementsNotMet(playerRef, slotType, result);

            LOGGER.at(Level.FINE).log("Blocked %s equip for player %s (requirements not met)",
                    slotType, playerId);
            return false;  // BLOCK
        }

        return true;  // ALLOW
    }

    private ItemContainer getContainerForSection(Inventory inventory, int sectionId) {
        return switch (sectionId) {
            case SECTION_HOTBAR -> inventory.getHotbar();
            case SECTION_UTILITY -> inventory.getUtility();
            case SECTION_ARMOR -> inventory.getArmor();
            default -> null;
        };
    }

    // =========================================================================
    // ARMOR VALIDATION (SlotFilter)
    // =========================================================================

    // Armor slot mapping: slot index to expected armor type
    // Based on Hytale's ItemArmorSlot enum: Head(0), Chest(1), Hands(2), Legs(3)
    private static final ItemArmorSlot[] ARMOR_SLOT_MAPPING = {
        ItemArmorSlot.Head,   // slot 0
        ItemArmorSlot.Chest,  // slot 1
        ItemArmorSlot.Hands,  // slot 2
        ItemArmorSlot.Legs    // slot 3
    };

    /**
     * Sets up armor slot validation for a player's inventory.
     *
     * <p>Creates composite filters that include both:
     * <ul>
     *   <li>Vanilla armor type validation (helmet in helmet slot, etc.)</li>
     *   <li>RPG attribute requirement validation</li>
     * </ul>
     *
     * <p>Call this when a player joins or inventory is created.
     *
     * @param inventory The player's inventory
     * @param playerRef The player's reference
     */
    public void setupArmorValidation(Inventory inventory, PlayerRef playerRef) {
        ItemContainer armorContainer = inventory.getArmor();
        UUID playerId = playerRef.getUuid();

        // Install composite filters that include both vanilla and RPG validation
        for (short slot = 0; slot < armorContainer.getCapacity(); slot++) {
            // Get the expected armor slot type for this slot
            ItemArmorSlot expectedSlotType = slot < ARMOR_SLOT_MAPPING.length
                    ? ARMOR_SLOT_MAPPING[slot]
                    : null;

            // Create a composite filter that checks both vanilla and RPG requirements
            SlotFilter composedFilter = createCompositeArmorFilter(playerId, playerRef, expectedSlotType);

            // Set the composed filter
            armorContainer.setSlotFilter(FilterActionType.ADD, slot, composedFilter);
        }

        LOGGER.at(Level.FINE).log("Armor validation filters installed for player %s (includes vanilla + RPG validation)", playerId);
    }

    /**
     * Sets up gem detection filters on the hotbar.
     *
     * <p>When a gem is dropped onto a hotbar slot containing RPG gear,
     * the filter intercepts the drop, blocks it, and opens the gem socketing page.
     *
     * @param inventory The player's inventory
     * @param playerRef The player's reference
     */
    public void setupHotbarGemDetection(Inventory inventory, PlayerRef playerRef) {
        ItemContainer hotbar = inventory.getHotbar();
        SlotFilter gemFilter = createHotbarGemFilter(playerRef);
        for (short slot = 0; slot < hotbar.getCapacity(); slot++) {
            hotbar.setSlotFilter(FilterActionType.ADD, slot, gemFilter);
        }
        LOGGER.at(Level.FINE).log("Hotbar gem detection filters installed for player %s", playerRef.getUuid());
    }

    /**
     * Creates a SlotFilter that detects gem-on-gear drops in the hotbar.
     *
     * <p>If the incoming item is a gem and the current slot holds RPG gear,
     * the filter blocks the drop and dispatches the gem socketing page.
     *
     * @param playerRef The player reference for UI dispatch
     * @return A SlotFilter for gem detection
     */
    private SlotFilter createHotbarGemFilter(PlayerRef playerRef) {
        return (actionType, container, slot, itemStack) -> {
            if (itemStack == null || itemStack.isEmpty()) {
                return true;
            }

            // Only intercept gem items
            if (!GemUtils.isGem(itemStack)) {
                return true;
            }

            // Check if the current slot contains RPG gear
            ItemStack currentItem = container.getItemStack(slot);
            if (currentItem != null && !currentItem.isEmpty() && GearUtils.isRpgGear(currentItem)) {
                // Gem dropped on gear — open socketing page
                dispatchSocketingPage(playerRef, currentItem, itemStack, slot, container);
                return false; // Block the gem from entering the slot
            }

            return true; // Allow gem placement in empty/non-gear slots
        };
    }

    /**
     * Opens the gem socketing page for a gem-on-gear interaction.
     *
     * <p>Reads gem data from the gem item, resolves the gear data from the target,
     * and opens the socketing UI on the world thread.
     *
     * @param playerRef The player reference
     * @param targetGear The gear item in the target slot
     * @param gemItem The gem being dropped
     * @param targetSlot The slot index of the target gear
     * @param gearContainer The container holding the gear
     */
    private void dispatchSocketingPage(PlayerRef playerRef, ItemStack targetGear,
            ItemStack gemItem, short targetSlot, ItemContainer gearContainer) {
        GemUtils.readGemData(gemItem).ifPresent(gemData -> {
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                return;
            }
            Store<EntityStore> store = ref.getStore();
            World world = store.getExternalData().getWorld();
            world.execute(() ->
                GearUtils.getGearData(targetGear).ifPresent(gearData ->
                    new GemSocketPage(playerRef, gearData, gemData, targetSlot, gearContainer)
                        .open(store)
                )
            );
        });
    }

    /**
     * Creates a composite filter that validates both vanilla armor type and RPG requirements.
     *
     * @param playerId The player's UUID
     * @param playerRef The player reference for feedback
     * @param expectedSlotType The expected armor slot type (null for any)
     * @return A composite SlotFilter
     */
    private SlotFilter createCompositeArmorFilter(UUID playerId, PlayerRef playerRef, ItemArmorSlot expectedSlotType) {
        return (actionType, container, slot, itemStack) -> {
            // Empty slot is always allowed
            if (itemStack == null || itemStack.isEmpty()) {
                return true;
            }

            // 0. Check for gem-on-gear interaction
            if (GemUtils.isGem(itemStack)) {
                ItemStack currentGear = container.getItemStack(slot);
                if (currentGear != null && !currentGear.isEmpty() && GearUtils.isRpgGear(currentGear)) {
                    dispatchSocketingPage(playerRef, currentGear, itemStack, slot, container);
                }
                return false; // Gems never go into armor slots
            }

            // 1. Check vanilla armor type (helmet in helmet slot, etc.)
            if (expectedSlotType != null) {
                // Use GearUtils.getBaseItem() to get the real item for custom gear IDs
                Item item = GearUtils.getBaseItem(itemStack);
                if (item != null) {
                    ItemArmor armor = item.getArmor();
                    if (armor == null) {
                        return false; // Not armor — weapons/tools/items don't go in armor slots
                    }
                    if (armor.getArmorSlot() != expectedSlotType) {
                        return false; // Wrong armor type for this slot
                    }
                }
            }

            // 2. Check RPG attribute requirements
            if (!validator.canEquip(playerId, itemStack)) {
                // Send feedback to player
                if (playerRef != null) {
                    ValidationResult result = validator.checkRequirements(playerId, itemStack);
                    feedback.sendRequirementsNotMet(playerRef, "armor", result);
                }
                return false; // BLOCK - requirements not met
            }

            return true; // ALLOW
        };
    }

    // =========================================================================
    // REACTIVE VALIDATION (Attribute Changes)
    // =========================================================================

    /**
     * Handles inventory changes for reactive validation.
     *
     * <p>This is used after attribute changes (respec, debuff) to
     * check if equipped items still meet requirements.
     */
    void onInventoryChange(Player player, InventoryChangeEvent event) {
        // This event is informational only (not cancellable)
        // We use it to trigger re-validation after attribute changes

        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return;
        }

        // Only care about armor container changes
        if (event.getItemContainer() != inventory.getArmor()) {
            return;
        }

        // Re-validation happens via AttributeChangeListener
        // (see onAttributeChange below)
    }

    /**
     * Called when player attributes change.
     *
     * <p>Checks all equipped gear and auto-unequips items that
     * no longer meet requirements.
     *
     * @param player The player whose attributes changed
     */
    public void onAttributeChange(Player player) {
        // Get PlayerRef via ECS component lookup (future-proof pattern)
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        UUID playerId = playerRef.getUuid();
        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return;
        }

        // Check armor
        checkAndUnequipInvalidArmor(player, inventory, playerRef);

        // Check weapon (can't force unequip, but warn)
        checkActiveWeapon(player, inventory, playerRef);

        // Check utility
        checkActiveUtility(player, inventory, playerRef);
    }

    private void checkAndUnequipInvalidArmor(Player player, Inventory inventory, PlayerRef playerRef) {
        UUID playerId = playerRef.getUuid();
        ItemContainer armorContainer = inventory.getArmor();
        ItemContainer storage = inventory.getStorage();

        for (short slot = 0; slot < armorContainer.getCapacity(); slot++) {
            ItemStack item = armorContainer.getItemStack(slot);
            if (item == null || item.isEmpty()) {
                continue;
            }

            if (!validator.canEquip(playerId, item)) {
                // Move to inventory or drop
                LOGGER.at(Level.INFO).log("Player %s armor slot %d no longer valid, unequipping",
                        playerId, slot);

                // Move to main storage (automatically finds available space)
                armorContainer.moveItemStackFromSlot(slot, storage);

                feedback.sendAutoUnequipped(playerRef, "armor");
            }
        }
    }

    private void checkActiveWeapon(Player player, Inventory inventory, PlayerRef playerRef) {
        ItemStack weapon = inventory.getItemInHand();
        if (weapon == null || weapon.isEmpty()) {
            return;
        }

        UUID playerId = playerRef.getUuid();
        if (!validator.canEquip(playerId, weapon)) {
            // Can't force unequip weapon, but warn the player
            feedback.sendWarningWeaponInvalid(playerRef);

            // Optionally: disable the weapon's bonuses
            // (handled in stat calculation phase)
        }
    }

    private void checkActiveUtility(Player player, Inventory inventory, PlayerRef playerRef) {
        ItemStack utility = inventory.getUtilityItem();
        if (utility == null || utility.isEmpty()) {
            return;
        }

        UUID playerId = playerRef.getUuid();
        if (!validator.canEquip(playerId, utility)) {
            feedback.sendWarningUtilityInvalid(playerRef);
        }
    }

    // =========================================================================
    // CLEANUP
    // =========================================================================

    /**
     * Removes armor validation filters when player leaves.
     */
    public void removeArmorValidation(Inventory inventory) {
        // Reset to default filters
        // May need to call ItemContainerUtil.trySetArmorFilters()
    }
}
