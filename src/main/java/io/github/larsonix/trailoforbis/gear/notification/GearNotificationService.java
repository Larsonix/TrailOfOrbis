package io.github.larsonix.trailoforbis.gear.notification;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import io.github.larsonix.trailoforbis.gear.item.ItemDisplayNameService;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.gear.tooltip.ItemNameFormatter;
import io.github.larsonix.trailoforbis.gear.tooltip.RichTooltipFormatter;
import io.github.larsonix.trailoforbis.gear.tooltip.TooltipStyles;
import io.github.larsonix.trailoforbis.gear.util.GearUtils;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.Optional;

/**
 * Service for sending gear-related notifications to players.
 *
 * <p>Handles formatting and sending messages for item pickups (chat with full stats).
 *
 * <p>Uses Hytale's Message API with rich text formatting.
 * Delegates to shared tooltip builders for consistent styling.
 */
public final class GearNotificationService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final RichTooltipFormatter tooltipFormatter;
    private final ItemNameFormatter itemNameFormatter;
    private final ItemDisplayNameService displayNameService;

    /**
     * Creates a GearNotificationService.
     *
     * @param tooltipFormatter The tooltip formatter for modifier formatting
     * @param itemNameFormatter The item name formatter for styled names
     * @param displayNameService The service for generating consistent display names
     */
    public GearNotificationService(
            @Nonnull RichTooltipFormatter tooltipFormatter,
            @Nonnull ItemNameFormatter itemNameFormatter,
            @Nonnull ItemDisplayNameService displayNameService) {
        this.tooltipFormatter = Objects.requireNonNull(tooltipFormatter, "tooltipFormatter cannot be null");
        this.itemNameFormatter = Objects.requireNonNull(itemNameFormatter, "itemNameFormatter cannot be null");
        this.displayNameService = Objects.requireNonNull(displayNameService, "displayNameService cannot be null");
    }

    // =========================================================================
    // PICKUP NOTIFICATIONS (Chat with Stats)
    // =========================================================================

    /**
     * Sends a pickup notification for a gear item.
     *
     * <p>Shows full stats in chat:
     * <pre>
     * [LEGENDARY] Iron Sword (Lv25, Q75%)
     * +15% Physical Damage
     * +8 Strength
     * </pre>
     *
     * @param player The player who picked up the item
     * @param itemStack The picked up item
     */
    public void sendPickupNotification(@Nonnull Player player, @Nonnull ItemStack itemStack) {
        Objects.requireNonNull(player, "player cannot be null");
        Objects.requireNonNull(itemStack, "itemStack cannot be null");

        Optional<GearData> gearDataOpt = GearUtils.readGearData(itemStack);
        if (gearDataOpt.isEmpty()) {
            return; // Not RPG gear, skip notification
        }

        GearData gearData = gearDataOpt.get();
        String itemName = displayNameService.getGearDisplayName(gearData, itemStack);

        Message message = formatPickupMessage(gearData, itemName);
        player.sendMessage(message);

        LOGGER.atFine().log("Sent pickup notification to player for %s %s",
                gearData.rarity(), itemName);
    }

    /**
     * Sends a pickup notification using PlayerRef.
     *
     * @param playerRef The player reference
     * @param itemStack The picked up item
     */
    public void sendPickupNotification(@Nonnull PlayerRef playerRef, @Nonnull ItemStack itemStack) {
        Objects.requireNonNull(playerRef, "playerRef cannot be null");
        Objects.requireNonNull(itemStack, "itemStack cannot be null");

        Optional<GearData> gearDataOpt = GearUtils.readGearData(itemStack);
        if (gearDataOpt.isEmpty()) {
            return;
        }

        GearData gearData = gearDataOpt.get();
        String itemName = displayNameService.getGearDisplayName(gearData, itemStack);

        Message message = formatPickupMessage(gearData, itemName);
        playerRef.sendMessage(message);

        LOGGER.atFine().log("Sent pickup notification via PlayerRef for %s %s",
                gearData.rarity(), itemName);
    }

    /**
     * Formats a pickup notification message with full stats.
     *
     * <p>Delegates modifier formatting to {@link RichTooltipFormatter}.
     */
    private Message formatPickupMessage(@Nonnull GearData gearData, @Nonnull String itemName) {
        GearRarity rarity = gearData.rarity();
        String rarityColor = TooltipStyles.getRarityColor(rarity);

        // Start with header line: [LEGENDARY] Iron Sword (Lv25, Q75%)
        Message message = Message.raw("")
                .insert(Message.raw("[" + rarity.name() + "] ").color(rarityColor).bold(true))
                .insert(Message.raw(itemName).color(rarityColor))
                .insert(Message.raw(" (Lv" + gearData.level() + ", Q" + gearData.quality() + "%)").color(TooltipStyles.LABEL_GRAY));

        // Add modifier lines using shared builder
        Message modifiers = tooltipFormatter.buildModifiersOnly(gearData);
        if (!modifiers.equals(Message.empty())) {
            message = message.insert(modifiers);
        }

        return message;
    }

}
