package io.github.larsonix.trailoforbis.commands.tooadmin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;

/**
 * Shared inventory helpers for admin commands that inspect or modify the held item.
 */
public final class HeldItemHelper {

    private HeldItemHelper() {}

    @Nullable
    public static ItemStack getHeldItem(PlayerRef player, Store<EntityStore> store, Ref<EntityStore> ref) {
        Player playerEntity = store.getComponent(ref, Player.getComponentType());
        if (playerEntity == null) {
            return null;
        }
        Inventory inventory = playerEntity.getInventory();
        if (inventory == null) {
            return null;
        }
        return inventory.getItemInHand();
    }

    public static void setHeldItem(PlayerRef player, Store<EntityStore> store, Ref<EntityStore> ref, ItemStack newItem) {
        Player playerEntity = store.getComponent(ref, Player.getComponentType());
        if (playerEntity == null) {
            return;
        }
        Inventory inventory = playerEntity.getInventory();
        if (inventory == null) {
            return;
        }
        short activeSlot = inventory.getActiveHotbarSlot();
        inventory.getHotbar().setItemStackForSlot(activeSlot, newItem);
    }
}
