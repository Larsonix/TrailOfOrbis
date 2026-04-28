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
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.api.services.AttributeService;
import io.github.larsonix.trailoforbis.api.services.SkillTreeService;
import io.github.larsonix.trailoforbis.stones.StoneType;
import io.github.larsonix.trailoforbis.stones.StoneUtils;
import io.github.larsonix.trailoforbis.ui.RPGStyles;
import com.hypixel.hytale.server.core.Message;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
    private static final long REFUND_DEBOUNCE_MS = 500;

    /** Tracks last refund stone consumption time per player+stoneType for deduplication. */
    private final ConcurrentHashMap<String, Long> lastRefundTime = new ConcurrentHashMap<>();

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

        // Determine how many to consume: full stack if crouching, 1 otherwise
        int consumeCount = 1;
        if (isCrouching) {
            ItemStack heldItem = inventory.getHotbar().getItemStack(activeSlot);
            if (heldItem != null && !heldItem.isEmpty()) {
                consumeCount = heldItem.getQuantity();
            }
        }

        // Consume stones from the active slot
        inventory.getHotbar().removeItemStackFromSlot(activeSlot, consumeCount);

        java.util.UUID playerId = playerRef.getUuid();
        final int consumed = consumeCount;

        if (stoneType == StoneType.ORB_OF_UNLEARNING) {
            ServiceRegistry.get(SkillTreeService.class).ifPresent(service -> {
                var data = service.getSkillTreeData(playerId);
                if (data != null) {
                    var updated = data.toBuilder()
                        .skillRefundPoints(data.getSkillRefundPoints() + consumed)
                        .build();
                    plugin.getSkillTreeManager().saveData(updated);

                    playerRef.sendMessage(
                        Message.raw("[").color(RPGStyles.TITLE_GOLD)
                            .insert(Message.raw(stoneType.getDisplayName()).color(stoneType.getHexColor()))
                            .insert(Message.raw("] ").color(RPGStyles.TITLE_GOLD))
                            .insert(Message.raw("+" + consumed + " Skill Refund Point" + (consumed > 1 ? "s" : "")
                                + " (" + (data.getSkillRefundPoints() + consumed) + " total)").color(RPGStyles.POSITIVE)));
                }
            });
        } else if (stoneType == StoneType.ORB_OF_REALIGNMENT) {
            ServiceRegistry.get(AttributeService.class).ifPresent(service -> {
                service.modifyAttributeRefundPoints(playerId, consumed);
                int newTotal = service.getAttributeRefundPoints(playerId);

                playerRef.sendMessage(
                    Message.raw("[").color(RPGStyles.TITLE_GOLD)
                        .insert(Message.raw(stoneType.getDisplayName()).color(stoneType.getHexColor()))
                        .insert(Message.raw("] ").color(RPGStyles.TITLE_GOLD))
                        .insert(Message.raw("+" + consumed + " Attribute Refund Point" + (consumed > 1 ? "s" : "")
                            + " (" + newTotal + " total)").color(RPGStyles.POSITIVE)));
            });
        }

        LOGGER.atInfo().log("Player %s consumed %dx %s (crouching: %b)",
            playerRef.getUsername(), consumed, stoneType.getDisplayName(), isCrouching);
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

        // Refund stones: consume and grant points immediately, no picker UI
        // Debounce: Hytale calls tryCreate() twice per interaction — skip duplicate
        if (stoneType.isRefundStone()) {
            long now = System.currentTimeMillis();
            String debounceKey = playerRef.getUuid().toString() + ":" + stoneType.name();
            Long lastTime = lastRefundTime.put(debounceKey, now);
            if (lastTime != null && (now - lastTime) < REFUND_DEBOUNCE_MS) {
                LOGGER.atFine().log("Debounced duplicate refund stone use for %s", playerRef.getUsername());
                return null;
            }
            boolean isCrouching = false;
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
                LOGGER.atWarning().withCause(e).log("Failed to read crouching state, defaulting to single consume");
            }
            handleRefundStone(playerComponent, playerRef, stoneType, isCrouching);
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

        // Open the HyUI page directly — get store from the entity ref
        Store<EntityStore> store = ref.getStore();
        StonePickerPage page = new StonePickerPage(
            plugin, playerRef, stoneType, itemInHand, activeSlot,
            ContainerType.HOTBAR, applicationService
        );
        page.open(store);

        // Return null — HyUI handles the page, not the engine's native page system
        return null;
    }
}
