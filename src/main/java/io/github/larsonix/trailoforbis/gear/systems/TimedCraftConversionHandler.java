package io.github.larsonix.trailoforbis.gear.systems;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.window.WindowType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.Window;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.InventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ActionType;
import com.hypixel.hytale.server.core.inventory.transaction.SlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.Transaction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import io.github.larsonix.trailoforbis.gear.conversion.MaterialTierMapper;
import io.github.larsonix.trailoforbis.gear.conversion.VanillaConversionConfig;
import io.github.larsonix.trailoforbis.gear.conversion.VanillaItemConverter;
import io.github.larsonix.trailoforbis.gear.item.ItemSyncService;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.util.GearUtils;
import io.github.larsonix.trailoforbis.mobs.calculator.DistanceBonusCalculator;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Converts timed craft output from workbenches to RPG gear via InventoryChangeEvent.
 *
 * <p>Hytale has two crafting code paths:
 * <ul>
 *   <li>{@code craftItem()} — instant, fires CraftRecipeEvent.Post → handled by {@link CraftingConversionSystem}</li>
 *   <li>{@code queueCraft()} — timed with progress bar, does NOT fire CraftRecipeEvent → handled here</li>
 * </ul>
 *
 * <h2>How it works</h2>
 * Uses the {@link InventoryChangeEvent}'s {@link Transaction} data to identify exactly
 * which item was added and to which slot. When a timed craft completes, Hytale's
 * {@code CraftingManager.giveOutput()} adds items via {@code addItemStacks()}, which
 * produces an ADD transaction. This handler reads the transaction to find the exact
 * new item and converts it in-place. No snapshots or baseline tracking needed.
 *
 * <h2>Covered crafting surfaces</h2>
 * <ul>
 *   <li>{@code BasicCrafting} — standard workbenches (timed recipes)</li>
 *   <li>{@code DiagramCrafting} — specialized diagram benches</li>
 *   <li>{@code Processing} — furnaces/smelters (output taken from bench slot)</li>
 * </ul>
 *
 * <h2>Excluded</h2>
 * <ul>
 *   <li>{@code StructuralCrafting} — Builder's Workbench, handled by
 *       {@link io.github.larsonix.trailoforbis.gear.reskin.ReskinDataPreserver}</li>
 *   <li>{@code PocketCrafting} — field crafting, always instant, handled by CraftingConversionSystem</li>
 * </ul>
 *
 * <h2>Zero false positives</h2>
 * <ul>
 *   <li>Items already converted to RPG gear (by CraftingConversionSystem for instant crafts)
 *       are detected by {@code GearUtils.isRpgGear()} and skipped — no double conversion.</li>
 *   <li>When a crafting window is open, the player cannot pick up ground items or interact
 *       with the world — the only source of new items is the bench itself.</li>
 * </ul>
 */
public final class TimedCraftConversionHandler {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Window types that produce timed craft output. */
    private static final Set<WindowType> TIMED_CRAFT_WINDOWS = Set.of(
            WindowType.BasicCrafting,
            WindowType.DiagramCrafting,
            WindowType.Processing
    );

    private final VanillaItemConverter converter;
    private final DistanceBonusCalculator distanceCalculator;
    private final VanillaConversionConfig config;
    @javax.annotation.Nullable private final ItemSyncService itemSyncService;

    /** Recursion guard — prevents feedback loop from our own slot replacements. */
    private final Set<UUID> processing = ConcurrentHashMap.newKeySet();

    public TimedCraftConversionHandler(
            @Nonnull VanillaItemConverter converter,
            @Nonnull DistanceBonusCalculator distanceCalculator,
            @Nonnull VanillaConversionConfig config) {
        this(converter, distanceCalculator, config, null);
    }

    public TimedCraftConversionHandler(
            @Nonnull VanillaItemConverter converter,
            @Nonnull DistanceBonusCalculator distanceCalculator,
            @Nonnull VanillaConversionConfig config,
            @javax.annotation.Nullable ItemSyncService itemSyncService) {
        this.converter = converter;
        this.distanceCalculator = distanceCalculator;
        this.config = config;
        this.itemSyncService = itemSyncService;
    }

    /**
     * InventoryChangeEvent handler. Registered via InventoryChangeEventSystem.addHandler().
     */
    public void onInventoryChange(@Nonnull Player player, @Nonnull InventoryChangeEvent event) {
        if (!converter.isSourceEnabled(VanillaItemConverter.AcquisitionSource.CRAFTING)) return;

        com.hypixel.hytale.component.Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) return;
        PlayerRef playerRef = ref.getStore().getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) return;
        UUID playerId = playerRef.getUuid();

        if (processing.contains(playerId)) return;
        if (!hasTimedCraftWindow(player)) return;

        // Read the transaction to find exactly what was added and where
        Transaction transaction = event.getTransaction();
        if (transaction == null) return;
        if (!(transaction instanceof SlotTransaction slotTx)) return;
        if (!slotTx.succeeded()) return;
        if (slotTx.getAction() != ActionType.ADD && slotTx.getAction() != ActionType.SET) return;

        // Get the item that appeared in the slot after the change
        ItemStack slotAfter = slotTx.getSlotAfter();
        if (ItemStack.isEmpty(slotAfter)) return;
        if (GearUtils.isRpgGear(slotAfter)) return;

        // Check if it's a convertible weapon/armor
        String itemId = slotAfter.getItemId();
        Item item = Item.getAssetMap().getAsset(itemId);
        if (item == null || item == Item.UNKNOWN) return;
        if (item.getWeapon() == null && item.getArmor() == null) return;
        if (item.getMaxStack() > 1) return;

        // Compute level from material distance
        int gearLevel = computeMaterialBasedLevel(itemId);

        Optional<ItemStack> converted = converter.convertCrafted(slotAfter, gearLevel);
        if (converted.isEmpty()) return;

        // Find the exact slot in the player's inventory containers and replace
        short txSlot = slotTx.getSlot();
        ItemContainer changedContainer = event.getItemContainer();

        if (changedContainer == null) return;

        final ItemStack rpgItem = converted.get();

        processing.add(playerId);
        if (player.getWorld() != null) {
            // Capture playerRef for pre-sync inside deferred block
            final PlayerRef capturedRef = playerRef;
            player.getWorld().execute(() -> {
                try {
                    // Pre-sync: send definition BEFORE placing in inventory.
                    // Hytale sends UpdatePlayerInventory on the next tick after
                    // setItemStackForSlot(). The client must know the item ID first.
                    if (itemSyncService != null) {
                        Optional<GearData> gd = GearUtils.readGearData(rpgItem);
                        if (gd.isPresent() && gd.get().hasInstanceId()) {
                            itemSyncService.syncItem(capturedRef, rpgItem, gd.get());
                        }
                    }
                    changedContainer.setItemStackForSlot(txSlot, rpgItem);
                    LOGGER.atInfo().log("Timed craft: %s → RPG Lv%d for %s (slot %d)",
                            itemId, gearLevel,
                            playerId.toString().substring(0, 8), txSlot);
                } catch (Exception e) {
                    LOGGER.atWarning().withCause(e).log(
                            "Failed timed craft conversion for %s", playerId);
                } finally {
                    processing.remove(playerId);
                }
            });
        } else {
            processing.remove(playerId);
        }
    }

    /**
     * Computes gear level from material distance range using the shared mob scaling formula.
     */
    private int computeMaterialBasedLevel(@Nonnull String itemId) {
        MaterialTierMapper mapper = converter.getMaterialMapper();
        VanillaConversionConfig.DistanceRange distRange = mapper.getDistanceRange(itemId);

        int minLevel = Math.max(1, distanceCalculator.estimateLevelFromDistance(distRange.getMin()));
        int maxLevel = Math.max(minLevel, distanceCalculator.estimateLevelFromDistance(distRange.getMax()));

        int gearLevel = (minLevel == maxLevel)
                ? minLevel
                : ThreadLocalRandom.current().nextInt(minLevel, maxLevel + 1);

        return Math.max(1, (int)(gearLevel * config.getCraftingLevelMultiplier()));
    }

    /**
     * Checks if the player has any timed crafting window open.
     */
    private static boolean hasTimedCraftWindow(@Nonnull Player player) {
        List<Window> windows = player.getWindowManager().getWindows();
        for (Window w : windows) {
            if (TIMED_CRAFT_WINDOWS.contains(w.getType())) {
                return true;
            }
        }
        return false;
    }
}
