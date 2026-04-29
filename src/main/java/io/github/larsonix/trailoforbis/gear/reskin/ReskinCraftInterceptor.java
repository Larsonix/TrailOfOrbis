package io.github.larsonix.trailoforbis.gear.reskin;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.window.WindowType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ItemContainerWindow;
import com.hypixel.hytale.server.core.entity.entities.player.windows.Window;
import com.hypixel.hytale.server.core.event.events.ecs.CraftRecipeEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import io.github.larsonix.trailoforbis.gear.instance.GearInstanceId;
import io.github.larsonix.trailoforbis.gear.instance.GearInstanceIdGenerator;
import io.github.larsonix.trailoforbis.gear.item.ItemRegistryService;
import io.github.larsonix.trailoforbis.gear.item.ItemSyncService;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.util.GearUtils;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Intercepts CraftRecipeEvent.Pre for reskin recipes at the Builder's Workbench.
 *
 * <p>When a player crafts a reskin recipe, this system:
 * <ol>
 *   <li>Cancels the vanilla craft (prevents queueCraft job)</li>
 *   <li>Consumes the RPG item from the workbench input</li>
 *   <li>Creates the reskinned RPG item with the new skin but same stats</li>
 *   <li>Gives it directly to the player's inventory</li>
 * </ol>
 *
 * <p>This is atomic — everything happens in one event handler, no timing issues.
 */
public final class ReskinCraftInterceptor extends EntityEventSystem<EntityStore, CraftRecipeEvent.Pre> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final ReskinDataPreserver dataPreserver;
    private final ItemRegistryService itemRegistry;
    private final ItemSyncService itemSyncService;
    private final ComponentType<EntityStore, PlayerRef> playerRefType;
    private final ComponentType<EntityStore, Player> playerType;
    private final ComponentType<EntityStore, UUIDComponent> uuidType;

    public ReskinCraftInterceptor(
            @Nonnull ReskinDataPreserver dataPreserver,
            @Nonnull ItemRegistryService itemRegistry,
            @Nonnull ItemSyncService itemSyncService) {
        super(CraftRecipeEvent.Pre.class);
        this.dataPreserver = Objects.requireNonNull(dataPreserver);
        this.itemRegistry = Objects.requireNonNull(itemRegistry);
        this.itemSyncService = Objects.requireNonNull(itemSyncService);
        this.playerRefType = PlayerRef.getComponentType();
        this.playerType = Player.getComponentType();
        this.uuidType = UUIDComponent.getComponentType();
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(playerRefType, Query.and(playerType, uuidType));
    }

    @Override
    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull CraftRecipeEvent.Pre event) {
        try {
            handleReskinCraft(index, archetypeChunk, store, event);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Error in reskin craft interceptor");
        }
    }

    private void handleReskinCraft(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CraftRecipeEvent.Pre event) {

        CraftingRecipe recipe = event.getCraftedRecipe();
        if (recipe == null) {
            return;
        }

        // Only intercept reskin recipes
        String recipeId = recipe.getId();
        if (recipeId == null || !recipeId.startsWith(ReskinRecipeGenerator.RECIPE_ID_PREFIX)) {
            return;
        }

        // Get player components
        PlayerRef playerRef = archetypeChunk.getComponent(index, playerRefType);
        Player player = archetypeChunk.getComponent(index, playerType);
        UUIDComponent uuidComponent = archetypeChunk.getComponent(index, uuidType);
        if (playerRef == null || player == null || uuidComponent == null) {
            return;
        }
        UUID playerId = uuidComponent.getUuid();

        // Consume the cached GearData (atomically removes from cache)
        GearData cachedGearData = dataPreserver.consumeCache(playerId);
        if (cachedGearData == null) {
            LOGGER.atWarning().log("Reskin craft for %s but no cached GearData — ignoring",
                    playerId.toString().substring(0, 8));
            event.setCancelled(true);
            return;
        }

        // Get the target skin item ID from the recipe output
        MaterialQuantity primaryOutput = recipe.getPrimaryOutput();
        if (primaryOutput == null || primaryOutput.getItemId() == null) {
            LOGGER.atWarning().log("Reskin recipe %s has no output item", recipeId);
            return;
        }
        String newBaseItemId = primaryOutput.getItemId();

        // Verify the target base item exists
        Item newBaseItem = Item.getAssetMap().getAsset(newBaseItemId);
        if (newBaseItem == null || newBaseItem == Item.UNKNOWN) {
            LOGGER.atWarning().log("Reskin target item not found: %s", newBaseItemId);
            return;
        }

        // Cancel the vanilla craft — we handle everything
        event.setCancelled(true);

        // 1. Consume the input from the workbench
        boolean inputConsumed = consumeWorkbenchInput(player);
        if (!inputConsumed) {
            LOGGER.atWarning().log("Failed to consume workbench input for reskin (player %s)",
                    playerId.toString().substring(0, 8));
            // Still proceed — the item will be returned when the window closes
        }

        // 2. Create the reskinned RPG item
        GearInstanceId newInstanceId = GearInstanceIdGenerator.generate();
        GearData newGearData = cachedGearData
                .withBaseItemId(newBaseItemId)
                .withInstanceId(newInstanceId);

        String customItemId = newInstanceId.toItemId();
        try {
            itemRegistry.createAndRegisterSync(newBaseItem, customItemId);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to register reskin item: %s", customItemId);
            return;
        }

        ItemStack rpgItemStack = new ItemStack(customItemId, 1);
        rpgItemStack = GearUtils.setGearData(rpgItemStack, newGearData);

        // 3. Sync the new item definition to the player BEFORE placing in inventory.
        // Hytale sends UpdatePlayerInventory on the next tick after addItemStacks().
        // The client must have the definition before that packet arrives.
        itemSyncService.syncItem(playerRef, rpgItemStack, newGearData);

        // 4. Give the reskinned RPG item to the player's inventory
        player.getInventory().getCombinedArmorHotbarUtilityStorage().addItemStacks(List.of(rpgItemStack));

        LOGGER.atInfo().log("Reskin completed for %s: %s -> %s (rarity=%s, level=%d)",
                playerId.toString().substring(0, 8),
                cachedGearData.baseItemId(), newBaseItemId,
                cachedGearData.rarity(), cachedGearData.level());
    }

    /**
     * Removes the item from the workbench input slot.
     *
     * @return true if the input was consumed
     */
    private boolean consumeWorkbenchInput(@Nonnull Player player) {
        List<Window> windows = player.getWindowManager().getWindows();
        for (Window w : windows) {
            if (w.getType() != WindowType.StructuralCrafting) {
                continue;
            }
            if (!(w instanceof ItemContainerWindow containerWindow)) {
                continue;
            }
            // Clear slot 0 on the CombinedItemContainer — this is what the
            // WindowManager's change event listener is registered on (line 135 of
            // WindowManager.setWindow0). Clearing on the combined container triggers
            // markWindowChanged → invalidate → updateWindows sends UpdateWindow packet
            // to the client. Using the internal inputContainer directly would bypass
            // this chain and leave the client out of sync.
            ItemContainer windowContainer = containerWindow.getItemContainer();
            ItemStack input = windowContainer.getItemStack((short) 0);
            if (!ItemStack.isEmpty(input)) {
                // Pass filter=false to bypass the workbench's ADD slot filter.
                // The filter rejects null items (no matching recipes), which would
                // cause the set to fail silently and leave the slot visually occupied.
                windowContainer.setItemStackForSlot((short) 0, null, false);
                return true;
            }
        }
        return false;
    }
}
