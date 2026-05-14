package io.github.larsonix.trailoforbis.stones.ui;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.api.services.AttributeService;
import io.github.larsonix.trailoforbis.api.services.SkillTreeService;
import io.github.larsonix.trailoforbis.gear.GearManager;
import io.github.larsonix.trailoforbis.gear.item.ItemDisplayNameService;
import io.github.larsonix.trailoforbis.stones.ModifiableItem;
import io.github.larsonix.trailoforbis.stones.ModifiableItemIO;
import io.github.larsonix.trailoforbis.stones.StoneActionRegistry;
import io.github.larsonix.trailoforbis.stones.StoneActionResult;
import io.github.larsonix.trailoforbis.stones.StoneType;
import io.github.larsonix.trailoforbis.stones.StoneUtils;

import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.util.NotificationUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Page supplier for the Stone Picker UI.
 *
 * <p>This supplier is invoked by Hytale's interaction system when a player
 * right-clicks (secondary interaction) on a stone item that has the
 * "RPG_StonePicker" page configured in its interactions.
 *
 * <p>Since StonePickerPage uses HyUI (not native CustomUIPage), this supplier
 * opens the page directly and returns {@code null} so the engine skips
 * its own {@code pageManager.openCustomPage()} call.
 *
 * <p>The flow:
 * <ol>
 *   <li>Player right-clicks stone</li>
 *   <li>Client sends MouseInteraction packet (because item has Secondary interaction)</li>
 *   <li>Server processes OpenCustomUI interaction</li>
 *   <li>OpenCustomUIInteraction calls this supplier's tryCreate()</li>
 *   <li>We validate the item, open HyUI page, and return null</li>
 * </ol>
 *
 * @see StonePickerPage
 * @see OpenCustomUIInteraction.CustomPageSupplier
 */
public class StonePickerPageSupplier implements OpenCustomUIInteraction.CustomPageSupplier {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /**
     * Debounce window to prevent Hytale's duplicate tryCreate() calls from double-consuming.
     * Hytale calls tryCreate() twice per single right-click interaction (observed in logs).
     * The two calls arrive within the same millisecond, so 500ms is generous. The key includes
     * stone type so that rapidly switching between different refund stones is not blocked.
     */
    private static final long BULK_ACTION_DEBOUNCE_MS = 500;

    /** Tracks last bulk action time per player+stoneType for deduplication. */
    private final ConcurrentHashMap<String, Long> lastBulkActionTime = new ConcurrentHashMap<>();

    /** Priority order for bulk identify: hotbar first, then storage, armor, utility. */
    private static final List<ContainerType> IDENTIFY_PRIORITY = List.of(
        ContainerType.HOTBAR, ContainerType.STORAGE, ContainerType.ARMOR, ContainerType.UTILITY
    );

    private final TrailOfOrbis plugin;

    /**
     * Creates a new stone picker page supplier.
     *
     * @param plugin The main plugin instance
     */
    public StonePickerPageSupplier(@Nonnull TrailOfOrbis plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
    }

    /**
     * Handles a refund stone: consumes from the active hotbar slot and grants
     * refund points to the appropriate system (skill tree or attributes).
     *
     * <p>If the player is crouching, consumes the entire stack and grants
     * that many refund points at once. Otherwise, consumes 1.
     *
     * @param player The player component
     * @param playerRef The player reference
     * @param stoneType The refund stone type
     * @param isCrouching Whether the player is crouching (bulk consume)
     */
    private void handleRefundStone(
            @Nonnull Player player,
            @Nonnull PlayerRef playerRef,
            @Nonnull StoneType stoneType,
            boolean isCrouching) {

        Inventory inventory = player.getInventory();
        if (inventory == null) return;

        byte activeSlot = inventory.getActiveHotbarSlot();
        if (activeSlot == Inventory.INACTIVE_SLOT_INDEX) return;

        // Re-verify the slot still contains the expected stone type.
        // This method runs deferred via world.execute() — the player could have
        // switched hotbar slots between tryCreate() and this callback.
        ItemStack heldItem = inventory.getHotbar().getItemStack(activeSlot);
        if (heldItem == null || heldItem.isEmpty() || !StoneUtils.isStone(heldItem)) return;
        Optional<StoneType> currentType = StoneUtils.readStoneType(heldItem);
        if (currentType.isEmpty() || currentType.get() != stoneType) return;

        // Determine how many to consume: full stack if crouching, 1 otherwise
        int consumeCount = 1;
        if (isCrouching) {
            consumeCount = heldItem.getQuantity();
        }

        // Consume stones from the active slot
        inventory.getHotbar().removeItemStackFromSlot(activeSlot, consumeCount);

        UUID playerId = playerRef.getUuid();
        final int consumed = consumeCount;

        if (stoneType == StoneType.ORB_OF_UNLEARNING) {
            ServiceRegistry.get(SkillTreeService.class).ifPresent(service -> {
                var data = service.getSkillTreeData(playerId);
                if (data != null) {
                    int newRefundTotal = data.getSkillRefundPoints() + consumed;
                    var updated = data.toBuilder()
                        .skillRefundPoints(newRefundTotal)
                        .build();
                    plugin.getSkillTreeManager().saveData(updated);

                    // Notify listeners (e.g., SkillPointHud) so the display updates immediately
                    service.notifyRefundPointsChanged(playerId, newRefundTotal);

                    NotificationUtil.sendNotification(
                        playerRef.getPacketHandler(),
                        Message.raw(stoneType.getDisplayName()).color(stoneType.getHexColor()).bold(true),
                        Message.raw("+" + consumed + " Skill Refund Point" + (consumed > 1 ? "s" : "")
                            + " (" + newRefundTotal + " total)").color("#55FF55"),
                        NotificationStyle.Success);
                }
            });
        } else if (stoneType == StoneType.ORB_OF_REALIGNMENT) {
            ServiceRegistry.get(AttributeService.class).ifPresent(service -> {
                service.modifyAttributeRefundPoints(playerId, consumed);
                int newTotal = service.getAttributeRefundPoints(playerId);

                NotificationUtil.sendNotification(
                    playerRef.getPacketHandler(),
                    Message.raw(stoneType.getDisplayName()).color(stoneType.getHexColor()).bold(true),
                    Message.raw("+" + consumed + " Attribute Refund Point" + (consumed > 1 ? "s" : "")
                        + " (" + newTotal + " total)").color("#55FF55"),
                    NotificationStyle.Success);
            });
        }

        LOGGER.atInfo().log("Player %s consumed %dx %s (crouching: %b)",
            playerRef.getUsername(), consumed, stoneType.getDisplayName(), isCrouching);
    }

    /**
     * Handles bulk identification: identifies as many unidentified items as possible,
     * consuming one scroll per item identified.
     *
     * <p>Scans the player's inventory for unidentified items, processes them in
     * priority order (hotbar first, then storage, armor, utility), and identifies
     * up to {@code min(scrollsInHand, unidentifiedItemCount)} items in one action.
     *
     * @param player The player component
     * @param playerRef The player reference
     * @param stoneType The stone type (LOREKEEPERS_SCROLL)
     */
    private void handleBulkIdentify(
            @Nonnull Player player,
            @Nonnull PlayerRef playerRef,
            @Nonnull StoneType stoneType) {

        Inventory inventory = player.getInventory();
        if (inventory == null) return;

        byte activeSlot = inventory.getActiveHotbarSlot();
        if (activeSlot == Inventory.INACTIVE_SLOT_INDEX) return;

        // Re-verify the slot still contains the expected stone type.
        // This method runs deferred via world.execute() — the player could have
        // switched hotbar slots between tryCreate() and this callback.
        ItemStack heldItem = inventory.getHotbar().getItemStack(activeSlot);
        if (heldItem == null || heldItem.isEmpty() || !StoneUtils.isStone(heldItem)) return;
        Optional<StoneType> currentType = StoneUtils.readStoneType(heldItem);
        if (currentType.isEmpty() || currentType.get() != stoneType) return;

        int scrollsAvailable = heldItem.getQuantity();

        // Get action registry for scanning and execution
        StoneApplicationService applicationService = plugin.getStoneApplicationService();
        if (applicationService == null) return;
        StoneActionRegistry actionRegistry = applicationService.getActionRegistry();

        GearManager gearManager = plugin.getGearManager();
        if (gearManager == null || !gearManager.isInitialized()) return;

        // Scan for unidentified items (canApply filters to !isIdentified())
        ItemDisplayNameService displayNameService = gearManager.getItemDisplayNameService();
        CompatibleItemScanner scanner = new CompatibleItemScanner(displayNameService);
        List<CompatibleItemScanner.ScannedItem> scannedItems =
            scanner.scan(inventory, stoneType, actionRegistry);

        // Sort by priority: Hotbar → Storage → Armor → Utility
        scannedItems.sort(Comparator.comparingInt(
            item -> IDENTIFY_PRIORITY.indexOf(item.container())));

        int toIdentify = Math.min(scrollsAvailable, scannedItems.size());

        if (toIdentify == 0) {
            NotificationUtil.sendNotification(
                playerRef.getPacketHandler(),
                Message.raw(stoneType.getDisplayName()).color(stoneType.getHexColor()).bold(true),
                Message.raw("No unidentified items found").color("#FF5555"),
                NotificationStyle.Warning);
            return;
        }

        // Identify items one by one
        Random random = ThreadLocalRandom.current();
        int successCount = 0;
        List<ItemStack> updatedStacks = new ArrayList<>();
        List<ModifiableItem> originalDatas = new ArrayList<>();

        for (int i = 0; i < toIdentify; i++) {
            CompatibleItemScanner.ScannedItem item = scannedItems.get(i);

            // Get fresh item from container
            ItemContainer container = item.container().getContainer(inventory);
            if (container == null) continue;

            ItemStack currentItem = container.getItemStack(item.slot());
            if (currentItem == null || currentItem.isEmpty()) continue;

            // Execute identify action
            StoneActionResult result = actionRegistry.execute(stoneType, item.data(), random);
            if (!result.success() || result.modifiedItem() == null) {
                LOGGER.atFine().log("Bulk identify: action failed for item in %s slot %d: %s",
                    item.container(), item.slot(), result.message());
                continue;
            }

            // Write back modified data
            ItemStack updatedStack = ModifiableItemIO.writeBack(currentItem, result.modifiedItem());
            if (updatedStack == null) {
                LOGGER.atWarning().log("Bulk identify: writeBack failed for item in %s slot %d",
                    item.container(), item.slot());
                continue;
            }

            container.setItemStackForSlot(item.slot(), updatedStack);
            updatedStacks.add(updatedStack);
            originalDatas.add(item.data());
            successCount++;
        }

        if (successCount == 0) {
            NotificationUtil.sendNotification(
                playerRef.getPacketHandler(),
                Message.raw(stoneType.getDisplayName()).color(stoneType.getHexColor()).bold(true),
                Message.raw("Failed to identify any items").color("#FF5555"),
                NotificationStyle.Warning);
            return;
        }

        // Consume scrolls equal to successful identifications
        inventory.getHotbar().removeItemStackFromSlot(activeSlot, successCount);

        // Notification
        String detail = successCount + " item" + (successCount > 1 ? "s" : "") + " identified"
            + " (" + successCount + " scroll" + (successCount > 1 ? "s" : "") + " used)";
        NotificationUtil.sendNotification(
            playerRef.getPacketHandler(),
            Message.raw(stoneType.getDisplayName()).color(stoneType.getHexColor()).bold(true),
            Message.raw(detail).color("#55FF55"),
            NotificationStyle.Success);

        // Batch resync all modified items (next tick to avoid tooltip flash)
        World world = player.getWorld();
        if (world != null) {
            final List<ItemStack> resyncStacks = List.copyOf(updatedStacks);
            final List<ModifiableItem> resyncDatas = List.copyOf(originalDatas);
            world.execute(() -> {
                for (int i = 0; i < resyncStacks.size(); i++) {
                    ModifiableItemIO.resync(playerRef, resyncStacks.get(i),
                        resyncDatas.get(i), gearManager);
                }
            });
        }

        LOGGER.atInfo().log("Player %s bulk-identified %d items (%d scrolls consumed)",
            playerRef.getUsername(), successCount, successCount);
    }

    /**
     * Validates the interaction and opens the HyUI-based StonePickerPage.
     *
     * <p>Always returns {@code null} because StonePickerPage uses HyUI's
     * {@code PageBuilder.fromHtml()} rather than the native CustomUIPage system.
     * The engine gracefully handles null by skipping its own page open call.
     *
     * @param ref The entity reference (player)
     * @param componentAccessor Accessor for entity components
     * @param playerRef The player reference
     * @param context The interaction context
     * @return Always null — the HyUI page is opened directly
     */
    @Nullable
    @Override
    public CustomUIPage tryCreate(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor,
            @Nonnull PlayerRef playerRef,
            @Nonnull InteractionContext context) {

        LOGGER.atFine().log("[StonePickerPageSupplier] tryCreate called for player %s",
            playerRef.getUuid());

        // Get player component
        Player playerComponent = componentAccessor.getComponent(ref, Player.getComponentType());
        if (playerComponent == null) {
            LOGGER.atWarning().log("Cannot create stone picker - Player component not found");
            return null;
        }

        // Get the held item context from the interaction
        ItemContext itemContext = context.createHeldItemContext();
        if (itemContext == null) {
            LOGGER.atWarning().log("Cannot create stone picker - No held item context");
            return null;
        }

        // Get the actual ItemStack
        ItemStack itemInHand = itemContext.getItemStack();
        if (itemInHand == null || itemInHand.isEmpty()) {
            LOGGER.atFine().log("Cannot create stone picker - No item in hand");
            return null;
        }

        // Verify it's a stone
        if (!StoneUtils.isStone(itemInHand)) {
            LOGGER.atFine().log("Cannot create stone picker - Item is not a stone: %s",
                itemInHand.getItemId());
            return null;
        }

        // Get stone type
        Optional<StoneType> stoneTypeOpt = StoneUtils.readStoneType(itemInHand);
        if (stoneTypeOpt.isEmpty()) {
            LOGGER.atWarning().log("Cannot create stone picker - Invalid stone data");
            return null;
        }

        StoneType stoneType = stoneTypeOpt.get();

        // Detect crouching for bulk-action stones (refund stones + Lorekeeper's Scroll).
        // Read once, shared by both branches below.
        boolean isCrouching = false;
        if (stoneType.isRefundStone() || stoneType == StoneType.LOREKEEPERS_SCROLL) {
            try {
                MovementStatesComponent movementComp = componentAccessor.getComponent(
                    ref, MovementStatesComponent.getComponentType());
                if (movementComp != null) {
                    MovementStates states = movementComp.getMovementStates();
                    if (states != null) {
                        isCrouching = states.crouching;
                    }
                }
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Failed to read crouching state, defaulting to single action");
            }
        }

        // Refund stones: consume and grant points immediately, no picker UI
        // Debounce: Hytale calls tryCreate() twice per interaction — skip duplicate
        if (stoneType.isRefundStone()) {
            long now = System.currentTimeMillis();
            String debounceKey = playerRef.getUuid().toString() + ":" + stoneType.name();
            Long lastTime = lastBulkActionTime.put(debounceKey, now);
            if (lastTime != null && (now - lastTime) < BULK_ACTION_DEBOUNCE_MS) {
                LOGGER.atFine().log("Debounced duplicate refund stone use for %s", playerRef.getUsername());
                return null;
            }
            // Defer inventory mutation to world.execute() — removeItemStackFromSlot()
            // is an EntityStore mutation that causes "Simulation and server tick are
            // not in sync" when called inside the interaction chain's serverTick().
            World world = ref.getStore().getExternalData().getWorld();
            final boolean capturedCrouching = isCrouching;
            world.execute(() -> {
                handleRefundStone(playerComponent, playerRef, stoneType, capturedCrouching);
            });
            return null;
        }

        // Lorekeeper's Scroll: bulk identify when crouching, picker UI when not
        if (stoneType == StoneType.LOREKEEPERS_SCROLL && isCrouching) {
            long now = System.currentTimeMillis();
            String debounceKey = playerRef.getUuid().toString() + ":" + stoneType.name();
            Long lastTime = lastBulkActionTime.put(debounceKey, now);
            if (lastTime != null && (now - lastTime) < BULK_ACTION_DEBOUNCE_MS) {
                LOGGER.atFine().log("Debounced duplicate bulk identify for %s", playerRef.getUsername());
                return null;
            }
            World world = ref.getStore().getExternalData().getWorld();
            world.execute(() -> {
                handleBulkIdentify(playerComponent, playerRef, stoneType);
            });
            return null;
        }

        // Get the slot information
        Inventory inventory = playerComponent.getInventory();
        if (inventory == null) {
            LOGGER.atWarning().log("Cannot create stone picker - Inventory is null");
            return null;
        }

        byte activeSlot = inventory.getActiveHotbarSlot();
        if (activeSlot == Inventory.INACTIVE_SLOT_INDEX) {
            LOGGER.atWarning().log("Cannot create stone picker - No active hotbar slot");
            return null;
        }

        // Get the application service from the plugin
        StoneApplicationService applicationService = plugin.getStoneApplicationService();
        if (applicationService == null) {
            LOGGER.atWarning().log("Cannot create stone picker - StoneApplicationService not initialized");
            return null;
        }

        LOGGER.atInfo().log("Opening HyUI StonePickerPage for %s stone at slot %d",
            stoneType.name(), activeSlot);

        // Defer page.open() to world.execute() — opening a HyUI page inside tryCreate()
        // causes "Simulation and server tick are not in sync" because tryCreate() runs
        // inside the interaction chain tick. page.open() modifies the ECS store which
        // advances the server operation counter without advancing the simulation counter.
        World world = ref.getStore().getExternalData().getWorld();
        world.execute(() -> {
            Store<EntityStore> deferredStore = world.getEntityStore().getStore();
            StonePickerPage page = new StonePickerPage(
                plugin, playerRef, stoneType, itemInHand, activeSlot,
                ContainerType.HOTBAR, applicationService
            );
            page.open(deferredStore);
        });

        // Return null — HyUI handles the page, not the engine's native page system
        return null;
    }
}
