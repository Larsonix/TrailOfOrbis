package io.github.larsonix.trailoforbis.gear.notification;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.gear.item.CustomItemData;
import io.github.larsonix.trailoforbis.gear.item.CustomItemInstanceId;
import io.github.larsonix.trailoforbis.gear.item.CustomItemSyncService;
import io.github.larsonix.trailoforbis.gear.item.ItemDisplayNameService;
import io.github.larsonix.trailoforbis.gear.item.ItemSyncService;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.gear.tooltip.RichTooltipFormatter;
import io.github.larsonix.trailoforbis.gear.tooltip.TooltipStyles;
import io.github.larsonix.trailoforbis.gear.util.GearUtils;
import io.github.larsonix.trailoforbis.gems.GemManager;
import io.github.larsonix.trailoforbis.gems.item.GemItemData;
import io.github.larsonix.trailoforbis.gems.model.GemData;
import io.github.larsonix.trailoforbis.gems.model.GemDefinition;
import io.github.larsonix.trailoforbis.gems.util.GemUtils;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.items.RealmMapUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Unified service for handling all item pickup notifications.
 *
 * <p>Centralizes pickup handling for:
 * <ul>
 *   <li>RPG Gear - Shows level, modifiers, and quality</li>
 *   <li>Realm Maps - Shows level and biome</li>
 *   <li>Stones - Uses standard display name</li>
 * </ul>
 *
 * <h2>Responsibilities</h2>
 * <ol>
 *   <li>Sync item definitions to player (via ItemSyncService/CustomItemSyncService)</li>
 *   <li>Send chat notifications for notable items</li>
 * </ol>
 *
 * <h2>Architecture</h2>
 * <pre>
 * ┌─────────────────────────────────┐
 * │    PickupNotificationService    │
 * │                                 │
 * │  • Unified pickup handling      │
 * │  • ItemDisplayNameService       │
 * │  • Translation coordination     │
 * │  • Chat + Native UI support     │
 * └─────────────────────────────────┘
 *                │
 * ┌──────────────┼──────────────┐
 * ↓              ↓              ↓
 * ┌─────────┐   ┌─────────┐   ┌─────────┐
 * │  Gear   │   │  Maps   │   │ Stones  │
 * └─────────┘   └─────────┘   └─────────┘
 * </pre>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is thread-safe. All dependencies are immutable after construction.
 *
 * @see ItemSyncService
 * @see CustomItemSyncService
 * @see ItemDisplayNameService
 */
public final class PickupNotificationService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /**
     * Minimum rarity for chat notifications.
     * Common items don't trigger chat notifications to reduce spam.
     */
    private static final GearRarity MIN_CHAT_NOTIFICATION_RARITY = GearRarity.UNCOMMON;

    private final ItemDisplayNameService displayNameService;
    private final ItemSyncService gearSyncService;
    private final CustomItemSyncService customItemSyncService;
    private final RichTooltipFormatter tooltipFormatter;

    /**
     * Creates a PickupNotificationService.
     *
     * @param displayNameService Service for generating consistent display names
     * @param gearSyncService Service for syncing gear definitions (may be null)
     * @param customItemSyncService Service for syncing custom item definitions (may be null)
     * @param tooltipFormatter Formatter for modifier formatting in chat notifications
     */
    public PickupNotificationService(
            @Nonnull ItemDisplayNameService displayNameService,
            @Nullable ItemSyncService gearSyncService,
            @Nullable CustomItemSyncService customItemSyncService,
            @Nonnull RichTooltipFormatter tooltipFormatter) {
        this.displayNameService = Objects.requireNonNull(displayNameService, "displayNameService cannot be null");
        this.gearSyncService = gearSyncService;
        this.customItemSyncService = customItemSyncService;
        this.tooltipFormatter = Objects.requireNonNull(tooltipFormatter, "tooltipFormatter cannot be null");
    }

    // =========================================================================
    // PUBLIC API - Unified Pickup Handling
    // =========================================================================

    /**
     * Handles a pickup event for any item type.
     *
     * <p>Auto-detects item type and routes to appropriate handler:
     * <ul>
     *   <li>Gear - Syncs definition and sends chat notification for Uncommon+</li>
     *   <li>Map - Syncs definition and sends chat notification</li>
     *   <li>Stone - Native item, no action needed</li>
     *   <li>Vanilla - No action needed</li>
     * </ul>
     *
     * @param player The player who picked up the item
     * @param itemStack The item that was picked up
     */
    public void handlePickup(@Nonnull PlayerRef player, @Nonnull ItemStack itemStack) {
        Objects.requireNonNull(player, "player cannot be null");
        Objects.requireNonNull(itemStack, "itemStack cannot be null");

        // Try gear first
        Optional<GearData> gearOpt = GearUtils.readGearData(itemStack);
        if (gearOpt.isPresent()) {
            handleGearPickup(player, itemStack, gearOpt.get());
            return;
        }

        // Try map
        Optional<RealmMapData> mapOpt = RealmMapUtils.readMapData(itemStack);
        if (mapOpt.isPresent()) {
            handleCustomItemPickup(player, mapOpt.get());
            // Guide milestone: map with 2+ modifiers
            TrailOfOrbis rpgMap = TrailOfOrbis.getInstanceOrNull();
            if (rpgMap != null && rpgMap.getGuideManager() != null) {
                RealmMapData mapData = mapOpt.get();
                int totalMods = mapData.prefixes().size() + mapData.suffixes().size();
                if (totalMods >= 2) {
                    rpgMap.getGuideManager().tryShow(player.getUuid(), io.github.larsonix.trailoforbis.guide.GuideMilestone.MAP_MODIFIERS);
                }
            }
            return;
        }

        // Try gem
        Optional<GemData> gemDataOpt = GemUtils.readGemData(itemStack);
        if (gemDataOpt.isPresent()) {
            Optional<GemManager> gemManagerOpt = ServiceRegistry.get(GemManager.class);
            if (gemManagerOpt.isPresent()
                    && gemManagerOpt.get().getRegistry() != null) {
                Optional<GemDefinition> defOpt = gemManagerOpt.get().getRegistry()
                        .getDefinition(gemDataOpt.get().gemId());
                CustomItemInstanceId instanceId = CustomItemInstanceId.fromItemId(itemStack.getItemId());
                if (defOpt.isPresent() && instanceId != null) {
                    handleCustomItemPickup(player,
                            new GemItemData(instanceId, defOpt.get(), gemDataOpt.get()));
                }
            }
            return;
        }

        // Stones are native Hytale items — no custom sync needed, but trigger guide
        if (io.github.larsonix.trailoforbis.stones.StoneUtils.isStone(itemStack)) {
            TrailOfOrbis rpgStone = TrailOfOrbis.getInstanceOrNull();
            if (rpgStone != null && rpgStone.getGuideManager() != null) {
                rpgStone.getGuideManager().tryShow(player.getUuid(), io.github.larsonix.trailoforbis.guide.GuideMilestone.FIRST_STONE);
            }
        }

        // Vanilla items need no special handling
    }

    /**
     * Checks if a player's storage inventory is nearly full and triggers the loot filter guide.
     * Called after any item pickup to detect when the player needs filtering.
     *
     * @param player The player to check
     */
    public void checkInventoryFullness(@Nonnull PlayerRef player) {
        TrailOfOrbis rpg = TrailOfOrbis.getInstanceOrNull();
        if (rpg == null || rpg.getGuideManager() == null) return;
        // Only trigger if FIRST_GEAR is already completed (no point suggesting filter before they understand gear)
        if (!rpg.getGuideManager().isCompleted(player.getUuid(), io.github.larsonix.trailoforbis.guide.GuideMilestone.FIRST_GEAR.getId())) return;

        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) return;
        Store<EntityStore> store = ref.getStore();
        com.hypixel.hytale.server.core.entity.entities.Player playerEntity = store.getComponent(ref, com.hypixel.hytale.server.core.entity.entities.Player.getComponentType());
        if (playerEntity == null || playerEntity.getInventory() == null) return;

        com.hypixel.hytale.server.core.inventory.container.ItemContainer storage = playerEntity.getInventory().getStorage();
        if (storage == null) return;

        short capacity = storage.getCapacity();
        if (capacity <= 0) return;

        int occupied = 0;
        for (short i = 0; i < capacity; i++) {
            ItemStack slot = storage.getItemStack(i);
            if (slot != null && !slot.isEmpty()) {
                occupied++;
            }
        }

        if ((double) occupied / capacity >= 0.8) {
            rpg.getGuideManager().tryShow(player.getUuid(), io.github.larsonix.trailoforbis.guide.GuideMilestone.LOOT_FILTER);
        }
    }

    /**
     * Handles gear pickup specifically.
     *
     * <p>Performs:
     * <ol>
     *   <li>Syncs item definition to player (includes translation with proper display name)</li>
     *   <li>Sends chat notification for Uncommon+ items</li>
     * </ol>
     *
     * @param player The player who picked up the gear
     * @param item The gear item stack
     * @param gearData The gear's RPG data
     */
    public void handleGearPickup(
            @Nonnull PlayerRef player,
            @Nonnull ItemStack item,
            @Nonnull GearData gearData) {
        Objects.requireNonNull(player, "player cannot be null");
        Objects.requireNonNull(item, "item cannot be null");
        Objects.requireNonNull(gearData, "gearData cannot be null");

        // 1. Sync item definition (includes translation with display name)
        if (gearSyncService != null && gearData.hasInstanceId()) {
            gearSyncService.syncItem(player, item, gearData);
        }

        // 2. Send chat notification for Uncommon+ items
        if (gearData.rarity().isAtLeast(MIN_CHAT_NOTIFICATION_RARITY)) {
            sendGearChatNotification(player, item, gearData);
        }

        // 3. Guide milestones
        TrailOfOrbis rpg = TrailOfOrbis.getInstanceOrNull();
        if (rpg != null && rpg.getGuideManager() != null) {
            rpg.getGuideManager().tryShow(player.getUuid(), io.github.larsonix.trailoforbis.guide.GuideMilestone.FIRST_GEAR);

            // MC01: Hexcode item detection (hex staff or hex book base item)
            if (io.github.larsonix.trailoforbis.compat.HexcodeCompat.isLoaded() && gearData.baseItemId() != null) {
                String base = gearData.baseItemId();
                if (base.startsWith("Hexstaff_") || base.startsWith("Hexbook_")) {
                    rpg.getGuideManager().tryShow(player.getUuid(), io.github.larsonix.trailoforbis.guide.GuideMilestone.HEXCODE_ITEM);
                }
            }
        }
    }

    /**
     * Handles custom item (map) pickup.
     *
     * <p>Performs:
     * <ol>
     *   <li>Syncs item definition to player (includes translation with proper display name)</li>
     *   <li>Sends chat notification</li>
     * </ol>
     *
     * @param player The player who picked up the item
     * @param customData The custom item's data
     */
    public void handleCustomItemPickup(
            @Nonnull PlayerRef player,
            @Nonnull CustomItemData customData) {
        Objects.requireNonNull(player, "player cannot be null");
        Objects.requireNonNull(customData, "customData cannot be null");

        // 1. Sync item definition (includes translation with display name)
        if (customItemSyncService != null && customData.hasInstanceId()) {
            customItemSyncService.syncItem(player, customData);
        }

    }

    // =========================================================================
    // CHAT NOTIFICATIONS
    // =========================================================================

    /**
     * Sends a chat notification for gear pickup.
     *
     * <p>Format: "[LEGENDARY] Sharp Iron Sword (Lv30, Q75%)"
     * <p>Includes modifier lines for Epic+ items.
     *
     * @param player The player to send the notification to
     * @param item The gear item stack
     * @param gearData The gear's RPG data
     */
    private void sendGearChatNotification(
            @Nonnull PlayerRef player,
            @Nonnull ItemStack item,
            @Nonnull GearData gearData) {
        String displayName = displayNameService.getGearDisplayName(gearData, item);
        GearRarity rarity = gearData.rarity();
        String rarityColor = TooltipStyles.getRarityColor(rarity);

        // Build: [LEGENDARY] Sharp Iron Sword (Lv30, Q75%)
        Message message = Message.raw("")
                .insert(Message.raw("[" + rarity.name() + "] ").color(rarityColor).bold(true))
                .insert(Message.raw(displayName).color(rarityColor))
                .insert(Message.raw(" (Lv" + gearData.level() + ", Q" + gearData.quality() + "%)").color(TooltipStyles.LABEL_GRAY));

        // Add modifier lines for Epic+ items (full stats view)
        if (rarity.isAtLeast(GearRarity.EPIC)) {
            Message modifiers = tooltipFormatter.buildModifiersOnly(gearData);
            if (!modifiers.equals(Message.empty())) {
                message = message.insert(modifiers);
            }
        }

        player.sendMessage(message);
        LOGGER.at(Level.FINE).log("Sent gear pickup notification for %s to %s", displayName, player.getUuid());
    }

}
