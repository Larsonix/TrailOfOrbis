package io.github.larsonix.trailoforbis.armor;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemArmor;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.gear.util.GearUtils;
import io.github.larsonix.trailoforbis.util.PlayerWorldCache;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Utility class for reading total armor values from player equipment.
 *
 * <p>This class reads armor values from equipped items using vanilla Hytale's
 * {@link ItemArmor#getBaseDamageResistance()} values. The total equipment armor
 * is then combined with VIT-based armor bonuses for the final armor value.
 *
 * <p>Usage:
 * <pre>
 * float equipmentArmor = EquipmentArmorReader.getTotalEquipmentArmor(inventory);
 * float totalArmor = equipmentArmor + (vit * vitGrants.getArmor());
 * </pre>
 */
public final class EquipmentArmorReader {

    private EquipmentArmorReader() {
        // Utility class - no instantiation
    }

    /**
     * Sums base damage resistance from all equipped armor pieces.
     *
     * @return 0 if inventory or armor is null
     */
    public static float getTotalEquipmentArmor(@Nullable Inventory inventory) {
        if (inventory == null) {
            return 0f;
        }

        ItemContainer armorContainer = inventory.getArmor();
        if (armorContainer == null) {
            return 0f;
        }

        float total = 0f;
        short capacity = armorContainer.getCapacity();

        for (short slot = 0; slot < capacity; slot++) {
            ItemStack itemStack = armorContainer.getItemStack(slot);
            if (itemStack == null || itemStack.isEmpty()) {
                continue;
            }

            // Use GearUtils.getBaseItem() to get the real item for custom gear IDs
            Item item = GearUtils.getBaseItem(itemStack);
            if (item == null || item == Item.UNKNOWN) {
                continue;
            }

            ItemArmor itemArmor = item.getArmor();
            if (itemArmor != null) {
                total += (float) itemArmor.getBaseDamageResistance();
            }
        }

        return total;
    }

    /** Variant with a scaling multiplier for balancing. */
    public static float getTotalEquipmentArmor(@Nullable Inventory inventory, float multiplier) {
        return getTotalEquipmentArmor(inventory) * multiplier;
    }

    /** @return 0 if not armor or null */
    public static float getArmorValue(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return 0f;
        }

        // Use GearUtils.getBaseItem() to get the real item for custom gear IDs
        Item item = GearUtils.getBaseItem(itemStack);
        if (item == null || item == Item.UNKNOWN) {
            return 0f;
        }

        ItemArmor itemArmor = item.getArmor();
        if (itemArmor != null) {
            return (float) itemArmor.getBaseDamageResistance();
        }

        return 0f;
    }

    /**
     * Looks up the player across all worlds and reads their equipped armor.
     *
     * @return 0 if player not found
     */
    public static float getTotalEquipmentArmorByUUID(@Nonnull UUID playerId) {
        try {
            PlayerRef ref = PlayerWorldCache.findPlayerRef(playerId);
            if (ref != null) {
                Ref<EntityStore> entityRef = ref.getReference();
                if (entityRef != null && entityRef.isValid()) {
                    Player player = entityRef.getStore().getComponent(entityRef, Player.getComponentType());
                    if (player != null) {
                        Inventory inventory = player.getInventory();
                        return getTotalEquipmentArmor(inventory);
                    }
                }
            }
        } catch (Exception ignored) {
            // Cache or entity might not be initialized
        }
        return 0f;
    }

    /** Variant with a scaling multiplier. */
    public static float getTotalEquipmentArmorByUUID(@Nonnull UUID playerId, float multiplier) {
        return getTotalEquipmentArmorByUUID(playerId) * multiplier;
    }
}
