package io.github.larsonix.trailoforbis.gear.conversion;

import io.github.larsonix.trailoforbis.gear.model.ArmorMaterial;
import io.github.larsonix.trailoforbis.gear.model.EquipmentType;
import io.github.larsonix.trailoforbis.gear.model.EquipmentType.ArmorSlot;
import io.github.larsonix.trailoforbis.gear.model.WeaponType;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.ItemArmorSlot;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemArmor;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemWeapon;
import com.hypixel.hytale.server.core.asset.type.itemsound.config.ItemSoundSet;
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
     * <p>Resolution uses two layers:
     * <ol>
     *   <li><b>Name matching</b>: Fast keyword-based detection from item ID</li>
     *   <li><b>Hytale asset API</b>: Definitive lookup via Item.getWeapon()/getArmor()
     *       when name matching fails. Works for ALL items regardless of naming convention.</li>
     * </ol>
     *
     * @param itemId e.g., "Weapon_Sword_Iron" or "CyclopsHelmPurple"
     * @param slot "weapon", "head", "chest", "legs", or "hands" (hint from caller)
     * @return The equipment type, or null if truly unresolvable
     */
    @Nullable
    public EquipmentType resolve(@Nullable String itemId, @Nullable String slot) {
        if (itemId == null || itemId.isEmpty() || slot == null) {
            return null;
        }

        // Layer 1: Name-based resolution (fast, handles standard naming conventions)
        EquipmentType fromName = resolveFromName(itemId, slot);
        if (fromName != null && fromName != EquipmentType.UNKNOWN_WEAPON && fromName != EquipmentType.UNKNOWN_ARMOR) {
            return fromName;
        }

        // Layer 2: Hytale asset API (definitive, handles ALL items including modded)
        EquipmentType fromAsset = resolveFromHytaleAsset(itemId);
        if (fromAsset != null) {
            return fromAsset;
        }

        // Return whatever name-based got (may be UNKNOWN_WEAPON/UNKNOWN_ARMOR)
        return fromName;
    }

    /**
     * Fast name-based resolution from item ID patterns.
     */
    @Nullable
    private EquipmentType resolveFromName(@Nonnull String itemId, @Nonnull String slot) {
        // Try weapon type from item ID FIRST — weapons in offhand slots (spellbooks)
        // must be classified by their weapon type, not by the slot string.
        Optional<WeaponType> weaponType = WeaponType.fromItemId(itemId);
        if (weaponType.isPresent()) {
            EquipmentType result = EquipmentType.resolve(weaponType.get(), null, null);
            LOGGER.atFine().log("Resolved weapon %s → %s → %s (slot=%s)",
                    itemId, weaponType.get(), result, slot);
            return result;
        }

        // Check if it's a weapon slot (for items not matching any WeaponType)
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

        return null;
    }

    /**
     * Definitive resolution using Hytale's Item asset system.
     * Reads the actual item definition to determine weapon/armor status and slot.
     * Works for ALL items regardless of naming convention (modded items included).
     */
    @Nullable
    private EquipmentType resolveFromHytaleAsset(@Nonnull String itemId) {
        try {
            Item item = Item.getAssetMap().getAsset(itemId);
            if (item == null || item == Item.UNKNOWN) {
                return null;
            }

            // Check weapon
            ItemWeapon weapon = item.getWeapon();
            if (weapon != null) {
                // It's definitively a weapon — try to get specific type from name
                Optional<WeaponType> weaponType = WeaponType.fromItemId(itemId);
                if (weaponType.isPresent() && weaponType.get() != WeaponType.UNKNOWN) {
                    return EquipmentType.resolve(weaponType.get(), null, null);
                }
                // Can't determine specific weapon type — return UNKNOWN_WEAPON
                // (but at least we KNOW it's a weapon, not armor)
                return EquipmentType.UNKNOWN_WEAPON;
            }

            // Check armor
            ItemArmor armor = item.getArmor();
            if (armor != null) {
                ArmorSlot slot = hytaleSlotToArmorSlot(armor.getArmorSlot());
                ArmorMaterial material = resolveArmorMaterial(item, itemId);
                EquipmentType result = EquipmentType.resolve(null, material, slot);
                LOGGER.atFine().log("Asset-resolved armor %s → %s + %s → %s",
                        itemId, material, slot, result);
                return result;
            }

            // Check shield (weapon with shield-like properties in offhand)
            if (itemId.toLowerCase().contains("shield")) {
                return EquipmentType.SHIELD;
            }

        } catch (Exception e) {
            LOGGER.atFine().log("Asset resolution failed for %s: %s", itemId, e.getMessage());
        }

        return null;
    }

    /**
     * Converts Hytale's ItemArmorSlot enum to our ArmorSlot.
     */
    @Nonnull
    private ArmorSlot hytaleSlotToArmorSlot(@Nonnull ItemArmorSlot hytaleSlot) {
        return switch (hytaleSlot) {
            case Head -> ArmorSlot.HEAD;
            case Chest -> ArmorSlot.CHEST;
            case Legs -> ArmorSlot.LEGS;
            case Hands -> ArmorSlot.HANDS;
        };
    }

    /**
     * Resolves armor material using ItemSoundSet first, then name fallback.
     * Same approach as DynamicLootRegistry.classifyArmorMaterial.
     */
    @Nonnull
    private ArmorMaterial resolveArmorMaterial(@Nonnull Item item, @Nonnull String itemId) {
        try {
            int soundSetIndex = item.getItemSoundSetIndex();
            ItemSoundSet soundSet = ItemSoundSet.getAssetMap().getAsset(soundSetIndex);
            if (soundSet != null) {
                Optional<ArmorMaterial> fromSoundSet = ArmorMaterial.fromSoundSetId(soundSet.getId());
                if (fromSoundSet.isPresent()) {
                    return fromSoundSet.get();
                }
            }
        } catch (Exception e) {
            LOGGER.atFine().log("SoundSet lookup failed for %s: %s", itemId, e.getMessage());
        }

        // Fallback to name-based
        return ArmorMaterial.fromItemIdOrSpecial(itemId);
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
