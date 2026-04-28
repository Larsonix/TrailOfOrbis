package io.github.larsonix.trailoforbis.maps.listeners;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.MouseButtonState;
import com.hypixel.hytale.protocol.MouseButtonType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import io.github.larsonix.trailoforbis.maps.RealmsManager;
import io.github.larsonix.trailoforbis.maps.api.RealmsService.ValidationResult;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;
import io.github.larsonix.trailoforbis.maps.items.RealmMapUtils;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Listens for player interactions with realm map items.
 *
 * <p>When a player right-clicks (secondary action) with a realm map,
 * this listener:
 * <ol>
 *   <li>Validates the map data</li>
 *   <li>Opens a new realm instance</li>
 *   <li>Teleports the player into the realm</li>
 *   <li>Consumes the map item</li>
 * </ol>
 */
public class RealmMapUseListener {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Colors for messages (from shared MessageColors)
    private static final String COLOR_ERROR = MessageColors.ERROR;
    private static final String COLOR_SUCCESS = MessageColors.SUCCESS;
    private static final String COLOR_PREFIX = MessageColors.DARK_PURPLE;

    private final RealmsManager realmsManager;

    /**
     * Creates a new realm map use listener.
     *
     * @param realmsManager The realms manager
     */
    public RealmMapUseListener(@Nonnull RealmsManager realmsManager) {
        this.realmsManager = Objects.requireNonNull(realmsManager, "realmsManager cannot be null");
    }

    /**
     * Registers this listener with the event registry.
     *
     * <p>Uses {@code registerGlobal} because {@link PlayerMouseButtonEvent} needs to
     * handle realm map usage in any world.
     *
     * <p>Note: We use PlayerMouseButtonEvent instead of the deprecated PlayerInteractEvent
     * because PlayerInteractEvent is not reliably fired for custom item interactions.
     * PlayerMouseButtonEvent provides the raw mouse input before the interaction system
     * processes it.
     *
     * @param eventRegistry The event registry
     */
    public void register(@Nonnull EventRegistry eventRegistry) {
        // PlayerMouseButtonEvent fires for all mouse button presses
        // We filter for right-click (MouseButtonType.Right) on realm maps
        eventRegistry.registerGlobal(
            EventPriority.NORMAL,
            PlayerMouseButtonEvent.class,
            this::onMouseButton
        );

        LOGGER.atInfo().log("RealmMapUseListener registered (using PlayerMouseButtonEvent)");
    }

    /**
     * Handles mouse button events.
     *
     * <p>PlayerMouseButtonEvent provides raw mouse input. We check for right-clicks
     * and then manually look up the player's held ItemStack from their inventory.
     *
     * @param event The mouse button event
     */
    private void onMouseButton(@Nonnull PlayerMouseButtonEvent event) {
        // DEBUG: Log ALL mouse button events to diagnose right-click issues
        LOGGER.atFine().log("[DEBUG] RealmMapUseListener: MouseButtonEvent received - button=%s, state=%s",
                event.getMouseButton().mouseButtonType,
                event.getMouseButton().state);

        // Only handle right-click press events
        if (event.getMouseButton().mouseButtonType != MouseButtonType.Right) {
            LOGGER.atFine().log("[DEBUG] Ignoring non-right-click: %s",
                    event.getMouseButton().mouseButtonType);
            return;
        }
        if (event.getMouseButton().state != MouseButtonState.Pressed) {
            LOGGER.atFine().log("[DEBUG] Ignoring non-pressed state: %s",
                    event.getMouseButton().state);
            return;
        }

        Player player = event.getPlayer();
        if (player == null) {
            LOGGER.atFine().log("[DEBUG] Player is null in event");
            return;
        }

        LOGGER.atFine().log("[DEBUG] Right-click detected from player, checking inventory...");

        // Get the player's inventory to find the actual ItemStack (with custom metadata)
        Inventory inventory = player.getInventory();
        if (inventory == null) {
            LOGGER.atFine().log("[DEBUG] Player inventory is null");
            return;
        }

        // Get the active hotbar slot
        byte activeSlot = inventory.getActiveHotbarSlot();
        if (activeSlot == Inventory.INACTIVE_SLOT_INDEX) {
            LOGGER.atFine().log("[DEBUG] No active hotbar slot");
            return;
        }

        // Get the ItemStack from the hotbar
        ItemContainer hotbar = inventory.getHotbar();
        if (hotbar == null) {
            LOGGER.atFine().log("[DEBUG] Hotbar container is null");
            return;
        }

        ItemStack itemInHand = hotbar.getItemStack(activeSlot);
        if (itemInHand == null || itemInHand.isEmpty()) {
            LOGGER.atFine().log("[DEBUG] No item in active hotbar slot %d", activeSlot);
            return;
        }

        LOGGER.atFine().log("[DEBUG] Item in hand: %s (slot %d)", itemInHand.getItemId(), activeSlot);

        // Check if it's a realm map
        if (!RealmMapUtils.isRealmMap(itemInHand)) {
            LOGGER.atFine().log("[DEBUG] Item is not a realm map: %s", itemInHand.getItemId());
            return;
        }

        LOGGER.atFine().log("[DEBUG] Realm map detected! Processing right-click action...");

        // Cancel the event to prevent default behavior
        event.setCancelled(true);

        // Get PlayerRef via ECS component lookup (future-proof pattern)
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        // Handle the map use
        handleMapUse(playerRef, player, itemInHand);
    }

    /**
     * Handles using a realm map.
     *
     * @param playerRef The player using the map
     * @param player The player entity
     * @param mapItem The map item being used
     */
    private void handleMapUse(
            @Nonnull PlayerRef playerRef,
            @Nonnull Player player,
            @Nonnull ItemStack mapItem) {

        UUID playerId = playerRef.getUuid();

        // Check if player is already in a realm
        if (realmsManager.isPlayerInRealm(playerId)) {
            sendError(playerRef, "You are already in a realm !");
            return;
        }

        // Read map data
        Optional<RealmMapData> mapDataOpt = RealmMapUtils.readMapData(mapItem);
        if (mapDataOpt.isEmpty()) {
            sendError(playerRef, "Invalid realm map !");
            LOGGER.atWarning().log("Player %s tried to use invalid realm map", playerId);
            return;
        }

        RealmMapData mapData = mapDataOpt.get();

        // Validate the map
        ValidationResult validation = realmsManager.validateMapData(mapData);
        if (!validation.valid()) {
            sendError(playerRef, getValidationMessage(validation));
            LOGGER.atFine().log("Map validation failed for player %s: %s", playerId, validation.errorMessage());
            return;
        }

        // Get player's current world
        UUID worldUuid = playerRef.getWorldUuid();
        if (worldUuid == null) {
            sendError(playerRef, "Cannot determine your current world !");
            return;
        }

        // Get player position for return point
        double returnX = 0, returnY = 64, returnZ = 0;
        Transform transform = playerRef.getTransform();
        if (transform != null) {
            Vector3d pos = transform.getPosition();
            returnX = pos.x;
            returnY = pos.y;
            returnZ = pos.z;
        }

        LOGGER.atInfo().log("Player %s activating realm map: level=%d, biome=%s, rarity=%s",
                playerId, mapData.level(), mapData.biome(), mapData.rarity());

        // Open the realm
        realmsManager.openRealm(mapData, playerId, worldUuid, returnX, returnY, returnZ)
            .thenAccept(realm -> onRealmOpened(playerRef, player, mapItem, realm))
            .exceptionally(ex -> {
                LOGGER.atWarning().withCause(ex).log("Failed to open realm for player %s", playerId);
                sendError(playerRef, "Failed to open realm : " + ex.getMessage());
                return null;
            });
    }

    /**
     * Called when a realm is successfully opened.
     *
     * @param playerRef The player
     * @param player The player entity
     * @param mapItem The map item to consume
     * @param realm The opened realm
     */
    private void onRealmOpened(
            @Nonnull PlayerRef playerRef,
            @Nonnull Player player,
            @Nonnull ItemStack mapItem,
            @Nonnull RealmInstance realm) {

        UUID playerId = playerRef.getUuid();

        LOGGER.atInfo().log("Realm %s opened for player %s, entering...",
                realm.getRealmId(), playerId);

        // Enter the realm
        realmsManager.enterRealm(playerId, realm.getRealmId())
            .thenAccept(success -> {
                if (success) {
                    // Consume the map item on successful entry
                    consumeMapItem(player, mapItem);
                    sendSuccess(playerRef, "Welcome to the realm !");
                    LOGGER.atInfo().log("Player %s entered realm %s",
                            playerId, realm.getRealmId());
                } else {
                    sendError(playerRef, "Failed to enter realm !");
                    // Close the realm since player couldn't enter
                    realmsManager.forceCloseRealm(realm.getRealmId());
                }
            })
            .exceptionally(ex -> {
                LOGGER.atWarning().withCause(ex).log("Failed to enter realm for player %s", playerId);
                sendError(playerRef, "Failed to enter realm !");
                realmsManager.forceCloseRealm(realm.getRealmId());
                return null;
            });
    }

    /**
     * Consumes the map item from the player's inventory.
     *
     * <p>Removes one realm map from the player's hotbar at the active slot.
     *
     * @param player The player
     * @param mapItem The map item to consume
     */
    private void consumeMapItem(@Nonnull Player player, @Nonnull ItemStack mapItem) {
        try {
            Inventory inventory = player.getInventory();
            if (inventory == null) {
                LOGGER.atWarning().log("Cannot consume map: player inventory is null");
                return;
            }

            // Get the active hotbar slot
            byte activeSlot = inventory.getActiveHotbarSlot();
            if (activeSlot == Inventory.INACTIVE_SLOT_INDEX) {
                LOGGER.atWarning().log("Cannot consume map: no active hotbar slot");
                return;
            }

            // Get the hotbar container
            ItemContainer hotbar = inventory.getHotbar();
            if (hotbar == null) {
                LOGGER.atWarning().log("Cannot consume map: hotbar is null");
                return;
            }

            // Remove one item from the active slot
            hotbar.removeItemStackFromSlot(activeSlot, 1);
            LOGGER.atFine().log("Consumed realm map from hotbar slot %d", activeSlot);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to consume realm map");
        }
    }

    /**
     * Gets a user-friendly message for a validation result.
     *
     * @param result The validation result
     * @return The message to display
     */
    private String getValidationMessage(@Nonnull ValidationResult result) {
        if (result.valid()) {
            return "Valid";
        }
        return result.errorMessage() != null ? result.errorMessage() : "Unknown validation error";
    }

    /**
     * Sends an error message to a player.
     *
     * @param playerRef The player
     * @param message The message
     */
    private void sendError(@Nonnull PlayerRef playerRef, @Nonnull String message) {
        Message msg = Message.raw("[Realms] ").color(COLOR_PREFIX)
            .insert(Message.raw(message).color(COLOR_ERROR));
        playerRef.sendMessage(msg);
    }

    /**
     * Sends a success message to a player.
     *
     * @param playerRef The player
     * @param message The message
     */
    private void sendSuccess(@Nonnull PlayerRef playerRef, @Nonnull String message) {
        Message msg = Message.raw("[Realms] ").color(COLOR_PREFIX)
            .insert(Message.raw(message).color(COLOR_SUCCESS));
        playerRef.sendMessage(msg);
    }

    // ═══════════════════════════════════════════════════════════════════
    // ACCESSORS (for testing)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the realms manager.
     *
     * @return The realms manager
     */
    public RealmsManager getRealmsManager() {
        return realmsManager;
    }
}
