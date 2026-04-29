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

import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.gear.util.GearUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
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

    private final ConcurrentHashMap<UUID, CachedReskinData> cache = new ConcurrentHashMap<>();

    public ReskinDataPreserver() {
    }

    /**
     * InventoryChangeEvent handler — registered via {@code InventoryChangeEventSystem.addHandler()}.
     *
     * <p>Separated into two phases that run in DIFFERENT event cycles:
     * <ul>
     *   <li>CACHE phase: When the workbench input has RPG gear, cache its data</li>
     *   <li>APPLY phase: When a vanilla weapon/armor appears in inventory AND we have
     *       a cache from a PREVIOUS event, apply the cached RPG data</li>
     * </ul>
     *
     * <p>Running both in the same event caused premature matching — the apply phase
     * would find vanilla items already in inventory before the workbench consumed
     * the input.
     */
    public void onInventoryChange(@Nonnull Player player, @Nonnull InventoryChangeEvent event) {
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

        // Check if player has a StructuralCrafting window open
        if (!hasStructuralCraftingWindow(player)) {
            return; // Not a workbench event — silent skip (very frequent)
        }

        // Guide milestone: first time opening Builder's Workbench
        var rpg = io.github.larsonix.trailoforbis.TrailOfOrbis.getInstanceOrNull();
        if (rpg != null && rpg.getGuideManager() != null) {
            rpg.getGuideManager().tryShow(playerId,
                io.github.larsonix.trailoforbis.guide.GuideMilestone.RESKIN_AVAILABLE);
        }

        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return;
        }

        // CACHE PHASE: Check the workbench input for RPG gear to cache.
        // The APPLY phase is handled by ReskinCraftInterceptor via CraftRecipeEvent.Pre.
        cacheFromWorkbenchInput(player, playerId);
    }

    /**
     * Clears the cache for a disconnected player.
     */
    public void onPlayerDisconnect(@Nonnull UUID playerId) {
        cache.remove(playerId);
    }

    /**
     * Consumes and returns the cached GearData for a player, removing it from the cache.
     *
     * <p>Used by {@link ReskinCraftInterceptor} to atomically retrieve and clear the
     * cache during CraftRecipeEvent.Pre handling.
     *
     * @param playerId The player's UUID
     * @return The cached GearData, or null if no cache exists or it expired
     */
    @Nullable
    public GearData consumeCache(@Nonnull UUID playerId) {
        CachedReskinData cached = cache.remove(playerId);
        if (cached == null || cached.isExpired()) {
            return null;
        }
        return cached.gearData();
    }

    /**
     * Checks the workbench input slot and caches RPG data if present.
     */
    private void cacheFromWorkbenchInput(@Nonnull Player player, @Nonnull UUID playerId) {
        Window structuralWindow = findStructuralCraftingWindow(player);
        if (structuralWindow == null) {
            return;
        }

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
                System.currentTimeMillis()
        ));

        LOGGER.atFine().log("Cached reskin data for %s: %s rarity=%s",
                playerId.toString().substring(0, 8),
                gearData.baseItemId(), rarity);
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
     * Cached RPG data for a player's pending reskin operation.
     */
    private record CachedReskinData(
            @Nonnull GearData gearData,
            @Nonnull GearRarity rarity,
            long cachedAtMs
    ) {
        boolean isExpired() {
            return System.currentTimeMillis() - cachedAtMs > CACHE_TTL_MS;
        }
    }
}
