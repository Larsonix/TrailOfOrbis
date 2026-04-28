package io.github.larsonix.trailoforbis.stones;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import io.github.larsonix.trailoforbis.gear.GearManager;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.util.GearUtils;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.items.RealmMapUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Utility for writing modified item data back to ItemStacks and resyncing to clients.
 *
 * <p>Uses {@link ItemTargetType} switch dispatch instead of instanceof checks.
 * Adding a new {@code ModifiableItem} implementation requires adding a case here
 * (compiler enforces exhaustiveness via the enum switch).
 */
public final class ModifiableItemIO {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private ModifiableItemIO() {}

    /**
     * Writes modified item data back to an ItemStack.
     *
     * @param original The original ItemStack
     * @param modified The modified item data
     * @return Updated ItemStack, or null if update failed
     */
    @Nullable
    public static ItemStack writeBack(@Nonnull ItemStack original, @Nullable ModifiableItem modified) {
        if (modified == null) {
            return null;
        }

        return switch (modified.itemTargetType()) {
            case MAP_ONLY -> {
                if (modified instanceof RealmMapData mapData) {
                    yield RealmMapUtils.writeMapData(original, mapData);
                }
                LOGGER.atWarning().log("MAP_ONLY item is not RealmMapData: %s", modified.getClass().getName());
                yield null;
            }
            case GEAR_ONLY -> {
                if (modified instanceof GearData gearData) {
                    yield GearUtils.writeGearData(original, gearData);
                }
                LOGGER.atWarning().log("GEAR_ONLY item is not GearData: %s", modified.getClass().getName());
                yield null;
            }
            case BOTH -> {
                LOGGER.atWarning().log("Unexpected BOTH target type for ModifiableItem: %s", modified.getClass().getName());
                yield null;
            }
        };
    }

    /**
     * Resyncs a modified item to the client after stone application.
     *
     * @param player The player to resync for
     * @param updatedItem The updated ItemStack
     * @param originalData The original item data (used for type dispatch)
     * @param gearManager The gear manager for resync operations
     */
    public static void resync(
            @Nonnull PlayerRef player,
            @Nullable ItemStack updatedItem,
            @Nonnull ModifiableItem originalData,
            @Nonnull GearManager gearManager) {

        if (updatedItem == null) {
            return;
        }

        if (!gearManager.isInitialized()) {
            LOGGER.atWarning().log("GearManager not available for item resync");
            return;
        }

        switch (originalData.itemTargetType()) {
            case MAP_ONLY -> RealmMapUtils.readMapData(updatedItem).ifPresent(updatedMapData -> {
                gearManager.resyncCustomItem(player, updatedMapData);
                LOGGER.atFine().log("Resynced map after stone application");
            });
            case GEAR_ONLY -> GearUtils.readGearData(updatedItem).ifPresent(updatedGearData -> {
                gearManager.resyncGearItem(player, updatedItem, updatedGearData);
                LOGGER.atFine().log("Resynced gear after stone application");
            });
            case BOTH -> LOGGER.atWarning().log("Unexpected BOTH target type for resync: %s",
                originalData.getClass().getName());
        }
    }
}
