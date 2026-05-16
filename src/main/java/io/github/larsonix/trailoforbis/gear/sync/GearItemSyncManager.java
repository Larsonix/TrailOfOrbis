package io.github.larsonix.trailoforbis.gear.sync;

import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.attributes.AttributeManager;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig;
import io.github.larsonix.trailoforbis.gear.core.GearCoreModule;
import io.github.larsonix.trailoforbis.gear.item.CustomItemData;
import io.github.larsonix.trailoforbis.gear.item.CustomItemDefinitionBuilder;
import io.github.larsonix.trailoforbis.gear.item.CustomItemSyncService;
import io.github.larsonix.trailoforbis.gear.item.ItemDefinitionBuilder;
import io.github.larsonix.trailoforbis.gear.item.ItemRegistryService;
import io.github.larsonix.trailoforbis.gear.item.ItemSyncConfig;
import io.github.larsonix.trailoforbis.gear.item.ItemSyncService;
import io.github.larsonix.trailoforbis.gear.item.ItemWorldSyncService;
import io.github.larsonix.trailoforbis.gear.item.TranslationSyncService;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.notification.PickupNotificationService;
import io.github.larsonix.trailoforbis.gear.util.GearUtils;
import io.github.larsonix.trailoforbis.leveling.api.LevelingService;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.items.RealmMapUtils;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Manages all item synchronization infrastructure for the gear system.
 *
 * <p>Owns: item definition building, translation sync, item sync services (gear + custom),
 * world-level sync, sync coordinator, equipment broadcast, and pickup notifications.
 *
 * <p>Also owns reconnect support (ensureItemsRegistered) and tooltip resync methods.
 *
 * <p>Owned by {@link io.github.larsonix.trailoforbis.gear.GearManager} (facade).
 */
public final class GearItemSyncManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Item sync
    private ItemDefinitionBuilder itemDefinitionBuilder;
    private TranslationSyncService translationSyncService;
    private ItemSyncService itemSyncService;

    // Custom item sync (maps, stones)
    private CustomItemDefinitionBuilder customItemDefinitionBuilder;
    private CustomItemSyncService customItemSyncService;

    // World-level item sync
    private ItemWorldSyncService itemWorldSyncService;

    // Sync coordinator
    private ItemSyncCoordinator syncCoordinator;

    // Equipment definition broadcast
    private io.github.larsonix.trailoforbis.gear.systems.EquipmentDefinitionBroadcastSystem equipmentBroadcastSystem;

    // Pickup notifications
    private PickupNotificationService pickupNotificationService;

    // Dependencies (kept for resync methods)
    private ItemRegistryService itemRegistryService;

    /**
     * Initializes all item sync components.
     *
     * @param core the core module (provides formatters for definitions)
     * @param modifierConfig modifier config (for ItemDefinitionBuilder)
     * @param itemRegistryService shared item registry
     * @param attributeManager attribute manager (for stats version provider)
     * @param plugin plugin instance (for event registration)
     */
    public void initialize(
            @Nonnull GearCoreModule core,
            @Nonnull ModifierConfig modifierConfig,
            @Nonnull ItemRegistryService itemRegistryService,
            @Nonnull AttributeManager attributeManager,
            @Nonnull TrailOfOrbis plugin) {

        this.itemRegistryService = itemRegistryService;

        // Item definition builder
        itemDefinitionBuilder = new ItemDefinitionBuilder(
                modifierConfig, core.getRichTooltipFormatter(), core.getItemNameFormatter(),
                core.getItemDisplayNameService());
        translationSyncService = new TranslationSyncService();
        itemSyncService = new ItemSyncService(
                itemDefinitionBuilder, translationSyncService, ItemSyncConfig.defaults());

        // Wire requirement context so tooltips update when requirement met/unmet status flips.
        // This replaces the old statsVersion approach — only items whose requirements actually
        // changed get re-synced, not all 50+ items on every stat change.
        itemSyncService.setRequirementContext(
                playerId -> {
                    int level = ServiceRegistry.get(LevelingService.class)
                            .map(ls -> ls.getLevel(playerId))
                            .orElse(1);
                    Map<io.github.larsonix.trailoforbis.attributes.AttributeType, Integer> attrs =
                            attributeManager.getPlayerAttributes(playerId);
                    return new ItemSyncService.RequirementContext(level, attrs);
                },
                core.getRequirementCalculator()
        );

        // Register tooltip refresh callback on AttributeManager
        attributeManager.setTooltipRefreshCallback(playerId -> {
            if (syncCoordinator != null) {
                syncCoordinator.markStatsDirty(playerId);
            }
        });
        LOGGER.at(Level.INFO).log("Registered tooltip refresh callback - gear requirements will update on stat changes");

        // Custom item sync
        customItemDefinitionBuilder = new CustomItemDefinitionBuilder(core.getItemDisplayNameService());
        customItemSyncService = new CustomItemSyncService(
                customItemDefinitionBuilder, translationSyncService, itemRegistryService);

        // Sync coordinator
        syncCoordinator = new ItemSyncCoordinator(itemSyncService);

        // Suppress sync during world transitions
        plugin.getEventRegistry().registerGlobal(
            com.hypixel.hytale.event.EventPriority.NORMAL,
            com.hypixel.hytale.server.core.event.events.player.DrainPlayerFromWorldEvent.class,
            event -> {
                try {
                    var uuidComp = event.getHolder().getComponent(
                        com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType());
                    if (uuidComp != null) {
                        syncCoordinator.suppressPlayer(uuidComp.getUuid());
                    }
                } catch (Exception e) {
                    // Holder may be invalid during edge cases — suppress silently
                }
            });

        // Pickup notification service (no longer needs sync services — ImmediateItemSyncHandler handles sync)
        pickupNotificationService = new PickupNotificationService();

        // World-level item sync
        itemWorldSyncService = new ItemWorldSyncService(
            itemSyncService, customItemSyncService, itemRegistryService);

        // Register level-up listener for tooltip resync
        ServiceRegistry.get(LevelingService.class).ifPresent(levelingSvc -> {
            levelingSvc.registerLevelUpListener((playerId, newLevel, oldLevel, totalXp) -> {
                if (syncCoordinator != null) {
                    syncCoordinator.markStatsDirty(playerId);
                }
            });
            LOGGER.at(Level.INFO).log("Registered gear tooltip resync listener for level-ups");
        });

        LOGGER.at(Level.INFO).log("GearItemSyncManager initialized");
    }

    /**
     * Shuts down sync infrastructure.
     */
    public void shutdown() {
        if (syncCoordinator != null) {
            syncCoordinator.shutdown();
            syncCoordinator = null;
        }
        if (itemWorldSyncService != null) {
            itemWorldSyncService.shutdown();
            itemWorldSyncService = null;
        }
        if (equipmentBroadcastSystem != null) {
            equipmentBroadcastSystem.shutdown();
            equipmentBroadcastSystem = null;
        }
        itemDefinitionBuilder = null;
        translationSyncService = null;
        itemSyncService = null;
        customItemDefinitionBuilder = null;
        customItemSyncService = null;
        pickupNotificationService = null;
        itemRegistryService = null;
    }

    // =========================================================================
    // RECONNECT SUPPORT
    // =========================================================================

    /**
     * Ensures all gear items in the collection are registered in the server's asset map.
     *
     * <p>Call this on player reconnect, before syncing items to clients.
     */
    public int ensureItemsRegistered(@Nonnull Collection<ItemStack> items) {
        Objects.requireNonNull(items, "items cannot be null");

        if (itemRegistryService == null || !itemRegistryService.isInitialized()) {
            LOGGER.at(Level.WARNING).log("ItemRegistryService not available for item registration");
            return 0;
        }

        int registered = 0;
        for (ItemStack item : items) {
            if (item == null || item.isEmpty()) {
                continue;
            }

            Optional<GearData> gearDataOpt = GearUtils.readGearData(item);
            if (gearDataOpt.isEmpty() || !gearDataOpt.get().hasInstanceId()) {
                continue;
            }

            GearData gearData = gearDataOpt.get();
            String customId = gearData.getItemId();

            if (itemRegistryService.isRegistered(customId)) {
                // Correct the reskin ResourceType using actual RPG rarity
                // (retro-inject at startup uses vanilla quality fallback)
                itemRegistryService.reinjectReskinResourceType(customId, gearData.rarity());
                continue;
            }

            // Try multiple sources for base item ID
            String baseItemId = GearUtils.getBaseItemId(item);
            if (baseItemId == null) {
                baseItemId = itemRegistryService.getBaseItemId(customId);
            }
            if (baseItemId == null) {
                LOGGER.at(Level.WARNING).log(
                    "Cannot re-register %s - no base item ID in metadata or registry cache",
                    customId);
                continue;
            }

            Item baseItem = Item.getAssetMap().getAsset(baseItemId);
            if (baseItem == null || baseItem == Item.UNKNOWN) {
                LOGGER.at(Level.WARNING).log(
                    "Cannot re-register %s - base item '%s' not in Hytale asset map",
                    customId, baseItemId);
                continue;
            }

            itemRegistryService.createAndRegisterSync(baseItem, customId, gearData.rarity());
            registered++;
            LOGGER.at(Level.FINE).log("Re-registered custom item: %s (base: %s)", customId, baseItemId);
        }

        return registered;
    }

    /**
     * Ensures all custom items (stones, maps) are registered in the server's asset map.
     */
    public int ensureCustomItemsRegistered(@Nonnull Collection<ItemStack> items) {
        Objects.requireNonNull(items, "items cannot be null");

        if (itemRegistryService == null || !itemRegistryService.isInitialized()) {
            LOGGER.at(Level.WARNING).log("ItemRegistryService not available for custom item registration");
            return 0;
        }

        int registered = 0;
        for (ItemStack item : items) {
            if (item == null || item.isEmpty()) {
                continue;
            }
            if (RealmMapUtils.isMapAnyMethod(item)) {
                if (registerMapIfNeeded(item)) {
                    registered++;
                }
            }
        }
        return registered;
    }

    private boolean registerMapIfNeeded(@Nonnull ItemStack item) {
        Optional<RealmMapData> mapDataOpt = RealmMapUtils.readMapDataWithFallback(item);
        if (mapDataOpt.isEmpty()) {
            return false;
        }

        RealmMapData mapData = mapDataOpt.get();
        if (!mapData.hasInstanceId()) {
            return false;
        }

        String customId = mapData.getItemId();
        if (itemRegistryService.isRegistered(customId)) {
            return false;
        }

        String baseItemId = mapData.getBaseItemId();
        Item baseItem = Item.getAssetMap().getAsset(baseItemId);
        if (baseItem == null || baseItem == Item.UNKNOWN) {
            LOGGER.at(Level.WARNING).log(
                "Cannot register map %s - base item '%s' not in Hytale asset map",
                customId, baseItemId);
            return false;
        }

        itemRegistryService.createAndRegisterSync(baseItem, customId);
        LOGGER.at(Level.FINE).log("Registered map: %s (base: %s, biome: %s)",
            customId, baseItemId, mapData.biome().name());
        return true;
    }

    // =========================================================================
    // TOOLTIP RESYNC
    // =========================================================================

    /**
     * Marks all gear dirty for a player so tooltips are rebuilt on next flush.
     */
    public void resyncPlayerGear(@Nonnull UUID playerId) {
        Objects.requireNonNull(playerId, "playerId cannot be null");
        if (syncCoordinator != null) {
            syncCoordinator.markStatsDirty(playerId);
        }
    }

    /**
     * Resyncs a custom item (map, stone) after modification.
     */
    public boolean resyncCustomItem(@Nonnull PlayerRef playerRef, @Nonnull CustomItemData customData) {
        Objects.requireNonNull(playerRef, "playerRef cannot be null");
        Objects.requireNonNull(customData, "customData cannot be null");

        if (customItemSyncService == null) {
            LOGGER.at(Level.WARNING).log("CustomItemSyncService not available for resync");
            return false;
        }

        boolean resynced = customItemSyncService.invalidateAndResync(playerRef, customData);
        if (resynced) {
            LOGGER.at(Level.FINE).log("Resynced custom item %s for player %s",
                customData.getItemId(), playerRef.getUuid());
        }
        return resynced;
    }

    /**
     * Resyncs a gear item after modification.
     */
    public boolean resyncGearItem(
            @Nonnull PlayerRef playerRef,
            @Nonnull ItemStack itemStack,
            @Nonnull GearData gearData) {
        Objects.requireNonNull(playerRef, "playerRef cannot be null");
        Objects.requireNonNull(itemStack, "itemStack cannot be null");
        Objects.requireNonNull(gearData, "gearData cannot be null");

        if (itemSyncService == null || !itemSyncService.isEnabled()) {
            LOGGER.at(Level.WARNING).log("ItemSyncService not available for resync");
            return false;
        }

        if (!gearData.hasInstanceId()) {
            LOGGER.at(Level.FINE).log("Gear has no instanceId, cannot resync");
            return false;
        }

        String itemId = gearData.getItemId();
        itemSyncService.getPlayerCache().removeItem(playerRef.getUuid(), itemId);

        if (gearData.instanceId() != null) {
            translationSyncService.unregisterTranslation(
                playerRef.getUuid(), gearData.instanceId().toCompactString());
        }

        boolean resynced = itemSyncService.syncItem(playerRef, itemStack, gearData);
        if (resynced) {
            LOGGER.at(Level.FINE).log("Resynced gear item %s for player %s",
                itemId, playerRef.getUuid());
        }
        return resynced;
    }

    // =========================================================================
    // ACCESSORS
    // =========================================================================

    @Nonnull
    public ItemDefinitionBuilder getItemDefinitionBuilder() {
        return itemDefinitionBuilder;
    }

    @Nonnull
    public TranslationSyncService getTranslationSyncService() {
        return translationSyncService;
    }

    @Nonnull
    public ItemSyncService getItemSyncService() {
        return itemSyncService;
    }

    @Nonnull
    public CustomItemSyncService getCustomItemSyncService() {
        return customItemSyncService;
    }

    @Nullable
    public ItemSyncCoordinator getSyncCoordinator() {
        return syncCoordinator;
    }

    @Nonnull
    public ItemWorldSyncService getItemWorldSyncService() {
        return itemWorldSyncService;
    }

    public void setEquipmentBroadcastSystem(
            @Nonnull io.github.larsonix.trailoforbis.gear.systems.EquipmentDefinitionBroadcastSystem system) {
        this.equipmentBroadcastSystem = system;
    }

    @Nullable
    public io.github.larsonix.trailoforbis.gear.systems.EquipmentDefinitionBroadcastSystem getEquipmentBroadcastSystem() {
        return equipmentBroadcastSystem;
    }

    @Nonnull
    public PickupNotificationService getPickupNotificationService() {
        return pickupNotificationService;
    }
}
