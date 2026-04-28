package io.github.larsonix.trailoforbis.gear.reskin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.window.WindowType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ItemContainerWindow;
import com.hypixel.hytale.server.core.entity.entities.player.windows.Window;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.InventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import io.github.larsonix.trailoforbis.gear.instance.GearInstanceId;
import io.github.larsonix.trailoforbis.gear.instance.GearInstanceIdGenerator;
import io.github.larsonix.trailoforbis.gear.item.ItemRegistryService;
import io.github.larsonix.trailoforbis.gear.item.ItemSyncService;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.gear.util.GearUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Preserves RPG gear data through Builder's Workbench skin changes.
 *
 * <p>When a player uses the Builder's Workbench to change an RPG gear's skin,
 * the vanilla crafting system destroys the input and creates a new vanilla item.
 * This handler caches the RPG data from the input and re-applies it to the output.
 *
 * <h2>Flow</h2>
 * <ol>
 *   <li>Player places RPG gear in workbench input slot → item leaves inventory</li>
 *   <li>{@code InventoryChangeEvent} fires → we check the workbench input for RPG data → cache it</li>
 *   <li>Player selects skin variant and crafts → vanilla output enters inventory</li>
 *   <li>{@code InventoryChangeEvent} fires → we find vanilla gear → apply cached RPG data</li>
 * </ol>
 *
 * <h2>Safety</h2>
 * <ul>
 *   <li>Only caches when a StructuralCrafting window is open (no false positives)</li>
 *   <li>Cache has 60-second TTL and is cleared on disconnect</li>
 *   <li>Recursion guard prevents infinite loops from slot replacement</li>
 * </ul>
 */
public final class ReskinDataPreserver {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final long CACHE_TTL_MS = 60_000L;

    private final ItemRegistryService itemRegistry;
    private final ItemSyncService itemSyncService;
    private final ConcurrentHashMap<UUID, CachedReskinData> cache = new ConcurrentHashMap<>();
    private final Set<UUID> processing = ConcurrentHashMap.newKeySet();

    public ReskinDataPreserver(@Nonnull ItemRegistryService itemRegistry,
                               @Nonnull ItemSyncService itemSyncService) {
        this.itemRegistry = Objects.requireNonNull(itemRegistry);
        this.itemSyncService = Objects.requireNonNull(itemSyncService);
    }

    /**
     * InventoryChangeEvent handler — registered via {@code InventoryChangeEventSystem.addHandler()}.
     */
    public void onInventoryChange(@Nonnull Player player, @Nonnull InventoryChangeEvent event) {
        // Get player UUID
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

        // Recursion guard — skip events caused by our own slot replacement
        if (processing.contains(playerId)) {
            return;
        }

        // Check if player has a StructuralCrafting window open
        if (!hasStructuralCraftingWindow(player)) {
            return;
        }

        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return;
        }

        // CACHE PHASE: Check the workbench input slot for RPG gear
        cacheFromWorkbenchInput(player, playerId);

        // APPLY PHASE: Check if a vanilla gear item appeared in inventory
        applyToVanillaOutput(player, playerRef, playerId, inventory);
    }

    /**
     * Clears the cache for a disconnected player.
     */
    public void onPlayerDisconnect(@Nonnull UUID playerId) {
        cache.remove(playerId);
    }

    /**
     * Checks the workbench input slot and caches RPG data if present.
     */
    private void cacheFromWorkbenchInput(@Nonnull Player player, @Nonnull UUID playerId) {
        Window structuralWindow = findStructuralCraftingWindow(player);
        if (structuralWindow == null) {
            return;
        }

        // Access the workbench's combined container (input is slot 0)
        if (!(structuralWindow instanceof ItemContainerWindow containerWindow)) {
            return;
        }

        ItemContainer windowContainer = containerWindow.getItemContainer();
        ItemStack inputItem = windowContainer.getItemStack((short) 0);

        if (ItemStack.isEmpty(inputItem) || !GearUtils.isRpgGear(inputItem)) {
            return;
        }

        Optional<GearData> gearDataOpt = GearUtils.getGearData(inputItem);
        if (gearDataOpt.isEmpty()) {
            return;
        }

        GearData gearData = gearDataOpt.get();
        GearRarity rarity = gearData.rarity();

        cache.put(playerId, new CachedReskinData(
                gearData,
                rarity,
                rarity.getAllowedSkinQualities(),
                System.currentTimeMillis()
        ));

        LOGGER.atFine().log("Cached reskin data for player %s: %s rarity=%s",
                playerId.toString().substring(0, 8),
                gearData.baseItemId(), rarity);
    }

    /**
     * Scans the player's inventory for unclaimed vanilla gear and applies cached RPG data.
     */
    private void applyToVanillaOutput(@Nonnull Player player, @Nonnull PlayerRef playerRef,
                                       @Nonnull UUID playerId, @Nonnull Inventory inventory) {
        CachedReskinData cached = cache.get(playerId);
        if (cached == null || cached.isExpired()) {
            if (cached != null) {
                cache.remove(playerId);
            }
            return;
        }

        // Scan inventory containers for a vanilla weapon/armor that matches
        ItemContainer[] containers = {
                inventory.getHotbar(),
                inventory.getArmor(),
                inventory.getStorage(),
                inventory.getBackpack(),
                inventory.getUtility()
        };

        for (ItemContainer container : containers) {
            if (container == null) {
                continue;
            }
            short capacity = container.getCapacity();
            for (short slot = 0; slot < capacity; slot++) {
                ItemStack itemStack = container.getItemStack(slot);
                if (ItemStack.isEmpty(itemStack)) {
                    continue;
                }

                // Skip RPG gear — we only want vanilla items
                if (GearUtils.isRpgGear(itemStack)) {
                    continue;
                }

                // Check if this vanilla item is a weapon or armor
                Item item = Item.getAssetMap().getAsset(itemStack.getItemId());
                if (item == null || item == Item.UNKNOWN) {
                    continue;
                }
                if (item.getWeapon() == null && item.getArmor() == null) {
                    continue;
                }

                // Check quality matches allowed skin qualities
                String qualityId = resolveQualityId(item);
                if (qualityId == null || !cached.allowedQualities.contains(qualityId)) {
                    continue;
                }

                // Match found — apply RPG data to this item
                applyReskinData(player, playerRef, playerId, container, slot, itemStack, cached);
                return; // One match per event cycle
            }
        }
    }

    /**
     * Applies the cached RPG data to a vanilla item, replacing it with a new RPG item.
     */
    private void applyReskinData(@Nonnull Player player, @Nonnull PlayerRef playerRef,
                                  @Nonnull UUID playerId,
                                  @Nonnull ItemContainer container, short slot,
                                  @Nonnull ItemStack vanillaItem,
                                  @Nonnull CachedReskinData cached) {
        String newBaseItemId = vanillaItem.getItemId();

        // Generate new instance ID for the reskinned item
        GearInstanceId newInstanceId = GearInstanceIdGenerator.generate();

        // Create updated GearData with new skin and instance ID
        GearData newGearData = cached.gearData
                .withBaseItemId(newBaseItemId)
                .withInstanceId(newInstanceId);

        // Get the new base item asset
        Item newBaseItem = Item.getAssetMap().getAsset(newBaseItemId);
        if (newBaseItem == null || newBaseItem == Item.UNKNOWN) {
            LOGGER.atWarning().log("Base item not found for reskin: %s", newBaseItemId);
            return;
        }

        // Register the custom item definition (clone new base item with custom ID)
        String customItemId = newInstanceId.toItemId();
        try {
            itemRegistry.createAndRegisterSync(newBaseItem, customItemId);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log(
                    "Failed to register custom item for reskin: %s", customItemId);
            return;
        }

        // Create the RPG ItemStack
        ItemStack rpgItemStack = new ItemStack(customItemId, 1);
        rpgItemStack = GearUtils.setGearData(rpgItemStack, newGearData);

        // Replace the vanilla item in the inventory slot (deferred to avoid recursion)
        processing.add(playerId);
        final ItemStack finalRpgItem = rpgItemStack;
        final short finalSlot = slot;

        if (player.getWorld() != null) {
            player.getWorld().execute(() -> {
                try {
                    container.setItemStackForSlot(finalSlot, finalRpgItem);

                    // Sync the new item definition to the player
                    itemSyncService.syncItem(playerRef, finalRpgItem, newGearData);

                    LOGGER.atInfo().log("Reskinned RPG gear for player %s: %s → %s (rarity=%s)",
                            playerId.toString().substring(0, 8),
                            cached.gearData.baseItemId(), newBaseItemId,
                            cached.rarity);
                } catch (Exception e) {
                    LOGGER.atWarning().withCause(e).log(
                            "Failed to apply reskin for player %s", playerId);
                } finally {
                    processing.remove(playerId);
                    cache.remove(playerId);
                }
            });
        } else {
            processing.remove(playerId);
            cache.remove(playerId);
        }
    }

    /**
     * Checks if any open window is a StructuralCrafting window.
     */
    private static boolean hasStructuralCraftingWindow(@Nonnull Player player) {
        List<Window> windows = player.getWindowManager().getWindows();
        for (Window w : windows) {
            if (w.getType() == WindowType.StructuralCrafting) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds the StructuralCrafting window, if open.
     */
    @Nullable
    private static Window findStructuralCraftingWindow(@Nonnull Player player) {
        List<Window> windows = player.getWindowManager().getWindows();
        for (Window w : windows) {
            if (w.getType() == WindowType.StructuralCrafting) {
                return w;
            }
        }
        return null;
    }

    /**
     * Resolves the vanilla quality ID for an item.
     */
    @Nullable
    private static String resolveQualityId(@Nonnull Item item) {
        int qualityIndex = item.getQualityIndex();
        var quality = com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality
                .getAssetMap().getAsset(qualityIndex);
        return quality != null ? quality.getId() : null;
    }

    /**
     * Cached RPG data for a player's pending reskin operation.
     */
    private record CachedReskinData(
            @Nonnull GearData gearData,
            @Nonnull GearRarity rarity,
            @Nonnull Set<String> allowedQualities,
            long cachedAtMs
    ) {
        boolean isExpired() {
            return System.currentTimeMillis() - cachedAtMs > CACHE_TTL_MS;
        }
    }
}
