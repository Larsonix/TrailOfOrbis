package io.github.larsonix.trailoforbis.gems.socket;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.gear.GearManager;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.util.GearUtils;
import io.github.larsonix.trailoforbis.gems.GemManager;
import io.github.larsonix.trailoforbis.gems.item.GemItemFactory;
import io.github.larsonix.trailoforbis.gems.model.GemData;
import io.github.larsonix.trailoforbis.gems.util.GemUtils;
import io.github.larsonix.trailoforbis.stones.ModifiableItemIO;
import java.util.ArrayList;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class GemSocketService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private final GemSocketValidator validator = new GemSocketValidator();

    public boolean socketActiveGem(@Nonnull PlayerRef playerRef, @Nonnull Inventory inventory, @Nonnull ItemContainer gearContainer, short gearSlot, @Nonnull GemData gemToSocket) {
        ItemStack gearItem = gearContainer.getItemStack(gearSlot);
        if (gearItem == null || gearItem.isEmpty()) {
            return false;
        }
        Optional<GearData> gearOpt = GearUtils.getGearData(gearItem);
        if (gearOpt.isEmpty()) {
            return false;
        }
        GearData gearData = gearOpt.get();
        GemSocketValidator.ValidationResult result = this.validator.canSocketActive(gearData, gemToSocket);
        if (!result.allowed()) {
            playerRef.sendMessage(Message.raw(result.reason()).color("#FF5555"));
            return false;
        }
        FoundGem found = this.findGemInInventory(inventory, gemToSocket);
        if (found == null) {
            LOGGER.atWarning().log("Gem not found in inventory: %s", gemToSocket.gemId());
            return false;
        }
        if (gearData.hasActiveGem()) {
            this.returnGemToInventory(inventory, gearData.activeGem());
        }
        GearData updatedData = gearData.withActiveGem(gemToSocket);
        ItemStack updatedItem = GearUtils.writeGearData(gearItem, updatedData);
        gearContainer.setItemStackForSlot(gearSlot, updatedItem);
        found.container().removeItemStackFromSlot(found.slot(), 1);
        this.resyncGear(playerRef, updatedItem, gearData);
        LOGGER.atFine().log("Socketed active gem %s into gear at slot %d", gemToSocket.gemId(), gearSlot);
        return true;
    }

    public boolean socketSupportGem(@Nonnull PlayerRef playerRef, @Nonnull Inventory inventory, @Nonnull ItemContainer gearContainer, short gearSlot, @Nonnull GemData gemToSocket) {
        ItemStack gearItem = gearContainer.getItemStack(gearSlot);
        if (gearItem == null || gearItem.isEmpty()) {
            return false;
        }
        Optional<GearData> gearOpt = GearUtils.getGearData(gearItem);
        if (gearOpt.isEmpty()) {
            return false;
        }
        GearData gearData = gearOpt.get();
        GemSocketValidator.ValidationResult result = this.validator.canSocketSupport(inventory, gearItem, gearData, gemToSocket);
        if (!result.allowed()) {
            playerRef.sendMessage(Message.raw(result.reason()).color("#FF5555"));
            return false;
        }
        FoundGem found = this.findGemInInventory(inventory, gemToSocket);
        if (found == null) {
            LOGGER.atWarning().log("Support gem not found in inventory: %s", gemToSocket.gemId());
            return false;
        }
        GearData updatedData = gearData.withAddedSupportGem(gemToSocket);
        ItemStack updatedItem = GearUtils.writeGearData(gearItem, updatedData);
        gearContainer.setItemStackForSlot(gearSlot, updatedItem);
        found.container().removeItemStackFromSlot(found.slot(), 1);
        this.resyncGear(playerRef, updatedItem, gearData);
        LOGGER.atFine().log("Socketed support gem %s into gear at slot %d", gemToSocket.gemId(), gearSlot);
        return true;
    }

    public boolean unsocketGem(@Nonnull PlayerRef playerRef, @Nonnull Inventory inventory, @Nonnull ItemContainer gearContainer, short gearSlot, int gemSlotIndex) {
        GearData updatedData;
        GemData removedGem;
        ItemStack gearItem = gearContainer.getItemStack(gearSlot);
        if (gearItem == null || gearItem.isEmpty()) {
            return false;
        }
        Optional<GearData> gearOpt = GearUtils.getGearData(gearItem);
        if (gearOpt.isEmpty()) {
            return false;
        }
        GearData gearData = gearOpt.get();
        if (gemSlotIndex == 0) {
            if (!gearData.hasActiveGem()) {
                return false;
            }
            removedGem = gearData.activeGem();
            updatedData = gearData.withActiveGem(null);
        } else {
            int supportIndex = gemSlotIndex - 1;
            if (supportIndex >= gearData.supportGems().size()) {
                return false;
            }
            removedGem = gearData.supportGems().get(supportIndex);
            ArrayList<GemData> newSupports = new ArrayList<>(gearData.supportGems());
            newSupports.remove(supportIndex);
            updatedData = gearData.withSupportGems(newSupports);
        }
        this.returnGemToInventory(inventory, removedGem);
        ItemStack updatedItem = GearUtils.writeGearData(gearItem, updatedData);
        gearContainer.setItemStackForSlot(gearSlot, updatedItem);
        this.resyncGear(playerRef, updatedItem, gearData);
        LOGGER.atFine().log("Unsocketed gem %s from gear at slot %d", removedGem.gemId(), gearSlot);
        return true;
    }

    @Nullable
    public FoundGem findGemInInventory(@Nonnull Inventory inventory, @Nonnull GemData target) {
        FoundGem found = this.scanContainer(inventory.getHotbar(), target);
        if (found != null) {
            return found;
        }
        found = this.scanContainer(inventory.getStorage(), target);
        if (found != null) {
            return found;
        }
        ItemContainer backpack = inventory.getBackpack();
        if (backpack != null && (found = this.scanContainer(backpack, target)) != null) {
            return found;
        }
        return null;
    }

    private FoundGem scanContainer(@Nonnull ItemContainer container, @Nonnull GemData target) {
        for (short i = 0; i < container.getCapacity(); i = (short)(i + 1)) {
            ItemStack item = container.getItemStack(i);
            if (item == null || item.isEmpty() || !GemUtils.isGem(item)) continue;
            Optional<GemData> gemOpt = GemUtils.readGemData(item);
            if (!gemOpt.isPresent()) continue;
            GemData gem = gemOpt.get();
            if (!gem.gemId().equals(target.gemId()) || gem.level() != target.level() || gem.quality() != target.quality()) continue;
            return new FoundGem(container, i, item);
        }
        return null;
    }

    private void returnGemToInventory(@Nonnull Inventory inventory, @Nonnull GemData gemData) {
        Optional<GemManager> gemManagerOpt = ServiceRegistry.get(GemManager.class);
        if (gemManagerOpt.isEmpty() || gemManagerOpt.get().getItemFactory() == null) {
            LOGGER.atWarning().log("Cannot return gem to inventory - GemManager not available");
            return;
        }
        GemItemFactory factory = gemManagerOpt.get().getItemFactory();
        GemItemFactory.GemItemResult result = factory.createGemItem(gemData.gemId(), gemData.level(), gemData.quality());
        if (result == null) {
            LOGGER.atWarning().log("Failed to create gem item for return: %s", gemData.gemId());
            return;
        }
        ItemStackTransaction transaction = inventory.getHotbar().addItemStack(result.itemStack());
        if (!transaction.succeeded()) {
            transaction = inventory.getBackpack().addItemStack(result.itemStack());
        }
    }

    private void resyncGear(@Nonnull PlayerRef playerRef, @Nonnull ItemStack updatedItem, @Nonnull GearData originalData) {
        Optional<GearManager> gearManagerOpt = ServiceRegistry.get(GearManager.class);
        if (gearManagerOpt.isPresent() && gearManagerOpt.get().isInitialized()) {
            ModifiableItemIO.resync(playerRef, updatedItem, originalData, gearManagerOpt.get());
        }
    }

    public record FoundGem(@Nonnull ItemContainer container, short slot, @Nonnull ItemStack itemStack) {
    }
}
