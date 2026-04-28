package io.github.larsonix.trailoforbis.gear.conversion;

import io.github.larsonix.trailoforbis.gear.model.ArmorMaterial;
import io.github.larsonix.trailoforbis.gear.model.EquipmentType;
import io.github.larsonix.trailoforbis.gear.model.EquipmentType.ArmorSlot;
import io.github.larsonix.trailoforbis.gear.model.WeaponType;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Resolves the {@link EquipmentType} from Hytale item IDs.
 *
 * <p>This class parses item IDs to determine:
 * <ul>
 *   <li>Weapon subtype (sword, dagger, staff, etc.)</li>
 *   <li>Armor material (cloth, leather, plate, etc.)</li>
 *   <li>Armor slot (head, chest, legs, hands)</li>
 * </ul>
 *
 * <p>Item ID patterns:
 * <ul>
 *   <li><b>Weapons</b>: {@code Weapon_{Type}_{Material}} (e.g., "Weapon_Sword_Iron")</li>
 *   <li><b>Armor</b>: {@code Armor_{Material}[_Variant]_{Slot}} (e.g., "Armor_Leather_Light_Chest")</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * EquipmentTypeResolver resolver = new EquipmentTypeResolver();
 * EquipmentType type = resolver.resolve("Weapon_Daggers_Steel", "weapon");
 * // Returns EquipmentType.DAGGER
 *
 * type = resolver.resolve("Armor_Iron_Chest", "chest");
 * // Returns EquipmentType.PLATE_CHEST
 * }</pre>
 *
 * @see EquipmentType
 * @see WeaponType
 * @see ArmorMaterial
 */
public final class EquipmentTypeResolver {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public EquipmentTypeResolver() {
        // Stateless - could be a singleton, but keeping it simple
    }

    /**
     * @return the equipment type, or null if not resolvable
     */
    @Nullable
    public EquipmentType resolve(
            @Nullable ItemStack itemStack,
            @Nullable ItemClassifier.Classification classification
    ) {
        if (itemStack == null || classification == null) {
            return null;
        }

        if (!classification.isConvertible()) {
            return null;
        }

        String itemId = itemStack.getItemId();
        String slot = classification.slot();

        return resolve(itemId, slot);
    }

    /**
     * Resolves equipment type from item ID and slot.
     *
     * @param itemId e.g., "Weapon_Sword_Iron"
     * @param slot "weapon", "head", "chest", "legs", or "hands"
     * @return null if not resolvable
     */
    @Nullable
    public EquipmentType resolve(@Nullable String itemId, @Nullable String slot) {
        if (itemId == null || itemId.isEmpty() || slot == null) {
            return null;
        }

        // Check if it's a weapon
        if ("weapon".equalsIgnoreCase(slot)) {
            return resolveWeapon(itemId);
        }

        // Check if it's armor
        Optional<ArmorSlot> armorSlot = ArmorSlot.fromSlotName(slot);
        if (armorSlot.isPresent()) {
            return resolveArmor(itemId, armorSlot.get());
        }

        // Check if it's a shield (offhand)
        if ("shield".equalsIgnoreCase(slot) || itemId.toLowerCase().contains("shield")) {
            return EquipmentType.SHIELD;
        }

        LOGGER.atFine().log("Could not resolve equipment type for itemId=%s, slot=%s", itemId, slot);
        return null;
    }

    /**
     * Resolves a weapon type from item ID.
     */
    @Nonnull
    private EquipmentType resolveWeapon(@Nonnull String itemId) {
        // Shield is categorized under weapons in some contexts
        if (itemId.toLowerCase().contains("shield")) {
            return EquipmentType.SHIELD;
        }

        Optional<WeaponType> weaponType = WeaponType.fromItemId(itemId);
        if (weaponType.isPresent()) {
            EquipmentType result = EquipmentType.resolve(weaponType.get(), null, null);
            LOGGER.atFine().log("Resolved weapon %s → %s → %s",
                    itemId, weaponType.get(), result);
            return result;
        }

        LOGGER.atFine().log("Unknown weapon type for %s, using UNKNOWN_WEAPON", itemId);
        return EquipmentType.UNKNOWN_WEAPON;
    }

    /**
     * Resolves armor type from item ID and slot.
     */
    @Nonnull
    private EquipmentType resolveArmor(@Nonnull String itemId, @Nonnull ArmorSlot slot) {
        Optional<ArmorMaterial> material = ArmorMaterial.fromItemId(itemId);
        if (material.isPresent()) {
            EquipmentType result = EquipmentType.resolve(null, material.get(), slot);
            LOGGER.atFine().log("Resolved armor %s → %s + %s → %s",
                    itemId, material.get(), slot, result);
            return result;
        }

        LOGGER.atFine().log("Unknown armor material for %s, using SPECIAL_%s", itemId, slot);
        return EquipmentType.resolve(null, ArmorMaterial.SPECIAL, slot);
    }

    /**
     * @return the equipment type, or null if not resolvable
     */
    @Nullable
    public EquipmentType resolveWithClassifier(
            @Nullable ItemStack itemStack,
            @Nonnull ItemClassifier classifier
    ) {
        if (itemStack == null) {
            return null;
        }

        ItemClassifier.Classification classification = classifier.classify(itemStack);
        return resolve(itemStack, classification);
    }
}
