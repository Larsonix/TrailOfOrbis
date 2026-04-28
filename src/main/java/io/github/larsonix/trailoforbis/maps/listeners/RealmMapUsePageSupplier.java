package io.github.larsonix.trailoforbis.maps.listeners;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.maps.RealmsManager;
import io.github.larsonix.trailoforbis.maps.items.RealmMapUtils;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Page supplier for realm map item interactions.
 *
 * <p>This supplier is invoked by Hytale's interaction system when a player
 * right-clicks (secondary interaction) on a realm map item that has the
 * "RPG_RealmMap_Secondary" interaction configured.
 *
 * <p>Since realm maps must be used on Portal_Device blocks (like fragments in
 * vanilla), this supplier just shows an instruction message telling the player
 * to use the map on an Ancient Gateway.
 *
 * @see RealmMapUtils
 * @see RealmPortalDevicePageSupplier
 */
public class RealmMapUsePageSupplier implements OpenCustomUIInteraction.CustomPageSupplier {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Colors for messages (from shared MessageColors)
    private static final String COLOR_ERROR = MessageColors.ERROR;
    private static final String COLOR_PREFIX = MessageColors.DARK_PURPLE;

    private final TrailOfOrbis plugin;

    /**
     * Creates a new realm map use page supplier.
     *
     * @param plugin The main plugin instance
     */
    public RealmMapUsePageSupplier(@Nonnull TrailOfOrbis plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
    }

    /**
     * Handles realm map right-click interaction.
     *
     * <p>This method is called by OpenCustomUIInteraction when the player
     * right-clicks a realm map item. Since realm maps must be used on
     * Portal_Device blocks, this shows an instruction message.
     *
     * @param ref The entity reference (player)
     * @param componentAccessor Accessor for entity components
     * @param playerRef The player reference
     * @param context The interaction context
     * @return Always null (no UI page needed)
     */
    @Nullable
    @Override
    public CustomUIPage tryCreate(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor,
            @Nonnull PlayerRef playerRef,
            @Nonnull InteractionContext context) {

        LOGGER.atFine().log("[RealmMapUsePageSupplier] tryCreate called for player %s",
            playerRef.getUuid());

        // Get RealmsManager
        RealmsManager realmsManager = plugin.getRealmsManager();
        if (realmsManager == null) {
            LOGGER.atWarning().log("Cannot use realm map - RealmsManager not initialized");
            sendError(playerRef, "Realms system is not available !");
            return null;
        }

        // Get player component
        Player playerComponent = componentAccessor.getComponent(ref, Player.getComponentType());
        if (playerComponent == null) {
            LOGGER.atWarning().log("Cannot use realm map - Player component not found");
            return null;
        }

        // Get the held item context from the interaction
        ItemContext itemContext = context.createHeldItemContext();
        if (itemContext == null) {
            LOGGER.atWarning().log("Cannot use realm map - No held item context");
            return null;
        }

        // Get the actual ItemStack
        ItemStack itemInHand = itemContext.getItemStack();
        if (itemInHand == null || itemInHand.isEmpty()) {
            LOGGER.atFine().log("Cannot use realm map - No item in hand");
            return null;
        }

        // Verify it's a realm map
        if (!RealmMapUtils.isRealmMap(itemInHand)) {
            LOGGER.atFine().log("Cannot use realm map - Item is not a realm map: %s",
                itemInHand.getItemId());
            return null;
        }

        LOGGER.atFine().log("[RealmMapUsePageSupplier] Realm map detected, showing instructions...");

        // Tell the player to use the map on a Portal_Device instead
        sendError(playerRef, "Use this on an Ancient Gateway to activate the portal !");

        // Return null - no UI page needed
        return null;
    }

    /**
     * Sends an error message to a player.
     */
    private void sendError(@Nonnull PlayerRef playerRef, @Nonnull String message) {
        Message msg = Message.raw("[Realms] ").color(COLOR_PREFIX)
            .insert(Message.raw(message).color(COLOR_ERROR));
        playerRef.sendMessage(msg);
    }
}
