package io.github.larsonix.trailoforbis.gear.notification;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;

import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.gear.item.CustomItemData;
import io.github.larsonix.trailoforbis.gear.item.CustomItemInstanceId;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.gear.tooltip.TooltipStyles;
import io.github.larsonix.trailoforbis.gear.util.GearUtils;
import io.github.larsonix.trailoforbis.gems.GemManager;
import io.github.larsonix.trailoforbis.gems.item.GemItemData;
import io.github.larsonix.trailoforbis.gems.model.GemData;
import io.github.larsonix.trailoforbis.gems.model.GemDefinition;
import io.github.larsonix.trailoforbis.gems.util.GemUtils;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.items.RealmMapUtils;
import io.github.larsonix.trailoforbis.stones.StoneType;
import io.github.larsonix.trailoforbis.stones.StoneUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Sole notification channel for all item pickups.
 *
 * <p>Hytale's native {@code notifyPickupItem()} is disabled globally (via GameplayConfig
 * reflection in {@code TrailOfOrbis.suppressNativePickupToasts()}). This service replaces
 * it, sending appropriate toasts for every item type:
 * <ul>
 *   <li>RPG Gear (all rarities) — enhanced rarity toast (color, level, quality, 3D model)</li>
 *   <li>Realm Maps (all rarities) — enhanced rarity toast (color, level, quality, 3D model)</li>
 *   <li>Stones (all rarities) — rarity-colored name toast (color, 3D model)</li>
 *   <li>Gems / Vanilla — simple "Picked up X" toast</li>
 * </ul>
 *
 * <p>Runs at handler position 4 in the InventoryChangeEvent chain, AFTER:
 * <ol>
 *   <li>LootFilterInventoryHandler (position 0) — ejects filtered items from the slot</li>
 *   <li>ImmediateItemSyncHandler (position 2) — syncs ItemBase definitions to client</li>
 * </ol>
 *
 * <p>Because the loot filter runs first, filtered items are already gone from the
 * container by the time {@link io.github.larsonix.trailoforbis.gear.listener.UnifiedPickupListener}
 * checks the slot. If the slot is empty, {@code handlePickup()} is never called — no toast.
 *
 * @see ItemSyncService
 * @see CustomItemSyncService
 */
public final class PickupNotificationService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public PickupNotificationService() {
    }

    // =========================================================================
    // PUBLIC API - Unified Pickup Handling
    // =========================================================================

    /**
     * Handles a pickup event for any item type.
     * Auto-detects type and routes to the appropriate handler.
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
            RealmMapData mapData = mapOpt.get();
            handleCustomItemPickup(player, mapData);

            // Enhanced toast: rarity-colored name + level/quality subtitle
            String mapCompactId = mapData.instanceId() != null
                    ? mapData.instanceId().toCompactString() : null;
            sendRPGPickupToast(player, itemStack, mapData.rarity(),
                    mapData.level(), mapData.quality(), mapCompactId);

            // Guide milestone: map with 2+ modifiers
            TrailOfOrbis rpgMap = TrailOfOrbis.getInstanceOrNull();
            if (rpgMap != null && rpgMap.getGuideManager() != null) {
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
            sendSimplePickupToast(player, itemStack);
            return;
        }

        // Stones — trigger guide, then rarity-colored toast
        if (StoneUtils.isStone(itemStack)) {
            TrailOfOrbis rpgStone = TrailOfOrbis.getInstanceOrNull();
            if (rpgStone != null && rpgStone.getGuideManager() != null) {
                rpgStone.getGuideManager().tryShow(player.getUuid(), io.github.larsonix.trailoforbis.guide.GuideMilestone.FIRST_STONE);
            }

            Optional<StoneType> stoneTypeOpt = StoneUtils.readStoneType(itemStack);
            if (stoneTypeOpt.isPresent()) {
                sendStonePickupToast(player, itemStack, stoneTypeOpt.get());
                return;
            }
        }

        // Simple toast for all remaining items (vanilla)
        // Replaces Hytale's disabled native notifyPickupItem() toast
        sendSimplePickupToast(player, itemStack);
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
     *   <li>Sends enhanced toast notification with rarity-colored name + level/quality for ALL rarities</li>
     *   <li>Triggers guide milestones</li>
     * </ol>
     *
     * <p>The notification is sent AFTER ImmediateItemSyncHandler (handler position 2) has
     * already synced the ItemBase definition (with qualityIndex) via UpdateItems. This ensures
     * the client has the definition cached when it renders the notification, so the quality-
     * colored background appears correctly. Hytale's native notifyPickupItem() fires too
     * early (before our sync), which is why we send our own.
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

        // Item definition sync is handled by ImmediateItemSyncHandler (handler position 2)
        // which runs before this handler (position 4) in the InventoryChangeEvent chain.

        // Toast: enhanced for ALL gear (rarity-colored name + level/quality subtitle)
        sendEnhancedPickupToast(player, item, gearData);

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
     * @param player The player who picked up the item
     * @param customData The custom item's data
     */
    public void handleCustomItemPickup(
            @Nonnull PlayerRef player,
            @Nonnull CustomItemData customData) {
        Objects.requireNonNull(player, "player cannot be null");
        Objects.requireNonNull(customData, "customData cannot be null");

        // Item definition sync is handled by ImmediateItemSyncHandler (handler position 2).
    }

    // =========================================================================
    // TOAST NOTIFICATIONS
    // =========================================================================

    /**
     * Enhanced toast for gear: delegates to the shared RPG pickup toast.
     */
    private void sendEnhancedPickupToast(@Nonnull PlayerRef player, @Nonnull ItemStack item,
                                         @Nonnull GearData gearData) {
        String compactId = gearData.hasInstanceId()
                ? gearData.instanceId().toCompactString() : null;
        sendRPGPickupToast(player, item, gearData.rarity(),
                gearData.level(), gearData.quality(), compactId);
    }

    /**
     * Shared enhanced pickup toast for any RPG item (gear, maps).
     *
     * <p>Shows:
     * <ul>
     *   <li>Title: item name in rarity color (resolved via translation key if available)</li>
     *   <li>Subtitle: "Lv : X - Quality : Y%" in white</li>
     *   <li>3D item model with quality-colored background</li>
     * </ul>
     *
     * @param player The player to send the toast to
     * @param item The item stack (for 3D model and fallback name)
     * @param rarity The item's rarity (determines title color)
     * @param level The item's level
     * @param quality The item's quality percentage
     * @param compactInstanceId Compact instance ID for translation key lookup (nullable)
     */
    private void sendRPGPickupToast(@Nonnull PlayerRef player, @Nonnull ItemStack item,
                                     @Nonnull GearRarity rarity, int level, int quality,
                                     @Nullable String compactInstanceId) {
        String rarityColor = TooltipStyles.getRarityColor(rarity);

        // Title: item name resolved client-side from our registered translation
        Message title;
        if (compactInstanceId != null) {
            String nameKey = io.github.larsonix.trailoforbis.gear.item.TranslationSyncService.getNameKey(compactInstanceId);
            title = Message.translation(nameKey).color(rarityColor);
        } else {
            // Fallback: base item translation key
            title = Message.translation(item.getItem().getTranslationKey()).color(rarityColor);
        }

        Message subtitle = Message.raw("")
                .insert(Message.raw("Lv : ").bold(true).color("#FFFFFF"))
                .insert(Message.raw(String.valueOf(level)).color("#FFFFFF"))
                .insert(Message.raw(" - ").color("#FFFFFF"))
                .insert(Message.raw("Quality : " + quality + "%").bold(true).color("#FFFFFF"));

        try {
            NotificationUtil.sendNotification(
                    player.getPacketHandler(),
                    title,
                    subtitle,
                    item.toPacket(),
                    NotificationStyle.Default
            );
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e).log("Failed to send RPG pickup toast");
        }
    }

    /**
     * Stone pickup toast: stone name in rarity color + 3D item model.
     *
     * <p>Mirrors the gear toast pattern but without a subtitle (stones have
     * no level/quality). Uses the stone's rarity color from {@link StoneType#getHexColor()}.
     */
    private void sendStonePickupToast(@Nonnull PlayerRef player, @Nonnull ItemStack itemStack,
                                       @Nonnull StoneType stoneType) {
        try {
            String rarityColor = stoneType.getHexColor();
            Message title = Message.translation(itemStack.getItem().getTranslationKey())
                    .color(rarityColor);

            NotificationUtil.sendNotification(
                    player.getPacketHandler(),
                    title,
                    null,
                    itemStack.toPacket(),
                    NotificationStyle.Default
            );
        } catch (Exception e) {
            LOGGER.at(Level.FINE).withCause(e).log("Failed to send stone pickup toast");
        }
    }

    /**
     * Simple "Picked up [item]" toast — exact replica of Hytale's native
     * {@code Player.notifyPickupItem()} notification.
     *
     * <p>Used for gems and vanilla items. Produces the same visual
     * the player would see from the native system: item name from translation
     * key, no subtitle, 3D item model, Default style.
     */
    private void sendSimplePickupToast(@Nonnull PlayerRef player, @Nonnull ItemStack itemStack) {
        try {
            com.hypixel.hytale.server.core.asset.type.item.config.Item item = itemStack.getItem();
            if (item == null) return;

            Message itemName = Message.translation(item.getTranslationKey());
            NotificationUtil.sendNotification(
                    player.getPacketHandler(),
                    Message.translation("server.general.pickedUpItem").param("item", itemName),
                    null,
                    itemStack.toPacket()
            );
        } catch (Exception e) {
            // Non-critical — the item is still in inventory regardless
            LOGGER.at(Level.FINE).withCause(e).log("Failed to send simple pickup toast");
        }
    }

}
