package io.github.larsonix.trailoforbis.gear.item;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.ItemBase;
import com.hypixel.hytale.protocol.UpdateType;
import com.hypixel.hytale.protocol.packets.assets.UpdateItems;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for synchronizing custom item definitions (maps, stones) to players.
 *
 * <p>Manages the lifecycle of dynamic item definitions for non-gear items:
 * <ul>
 *   <li>Initializes player caches on connect</li>
 *   <li>Syncs item definitions when custom items are picked up</li>
 *   <li>Registers translations before sending item definitions</li>
 *   <li>Cleans up player data on disconnect</li>
 * </ul>
 *
 * <h2>Architecture</h2>
 * <pre>
 * CustomItemPickupListener
 *              ↓
 *       CustomItemSyncService.syncItem()
 *              ↓
 *       TranslationSyncService.registerTranslations()  ← First
 *              ↓
 *       CustomItemDefinitionBuilder.build()
 *              ↓
 *       UpdateTranslations packet → Player
 *       UpdateItems packet → Player
 * </pre>
 *
 * <h2>Thread Safety</h2>
 * <p>All public methods are thread-safe.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Initialize service
 * CustomItemSyncService service = new CustomItemSyncService(definitionBuilder, translationService);
 *
 * // On player connect
 * service.onPlayerConnect(playerId);
 *
 * // When custom item is picked up
 * service.syncItem(playerRef, stoneItemData);
 *
 * // On player disconnect
 * service.onPlayerDisconnect(playerId);
 * }</pre>
 */
public final class CustomItemSyncService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final CustomItemDefinitionBuilder definitionBuilder;
    private final TranslationSyncService translationService;

    /**
     * Optional registry service for server-side item registration.
     * When provided, ensures items are registered in Hytale's asset map
     * so server-side operations (drop, move, use) work correctly.
     */
    @Nullable
    private final ItemRegistryService itemRegistryService;

    /**
     * Tracks which items have been synced to each player.
     * Map of player UUID → Set of custom item IDs
     */
    private final Map<UUID, Set<String>> syncedItems = new ConcurrentHashMap<>();

    /**
     * Creates a CustomItemSyncService with the required dependencies.
     *
     * @param definitionBuilder Builder for item definitions
     * @param translationService Service for dynamic translation registration
     */
    public CustomItemSyncService(
            @Nonnull CustomItemDefinitionBuilder definitionBuilder,
            @Nonnull TranslationSyncService translationService) {
        this(definitionBuilder, translationService, null);
    }

    /**
     * Creates a CustomItemSyncService with server-side registration support.
     *
     * @param definitionBuilder Builder for item definitions
     * @param translationService Service for dynamic translation registration
     * @param itemRegistryService Service for server-side asset map registration (may be null)
     */
    public CustomItemSyncService(
            @Nonnull CustomItemDefinitionBuilder definitionBuilder,
            @Nonnull TranslationSyncService translationService,
            @Nullable ItemRegistryService itemRegistryService) {
        this.definitionBuilder = Objects.requireNonNull(definitionBuilder, "definitionBuilder cannot be null");
        this.translationService = Objects.requireNonNull(translationService, "translationService cannot be null");
        this.itemRegistryService = itemRegistryService;
    }

    // =========================================================================
    // PLAYER LIFECYCLE
    // =========================================================================

    /**
     * Initializes sync state for a player.
     *
     * <p>Should be called when a player connects, before any item sync.
     *
     * @param playerId The player's UUID
     */
    public void onPlayerConnect(@Nonnull UUID playerId) {
        Objects.requireNonNull(playerId, "playerId cannot be null");

        // Always create fresh cache to force resync on reconnect
        // Using put() instead of computeIfAbsent() ensures the cache is cleared
        // even if the player reconnected quickly before disconnect cleanup
        syncedItems.put(playerId, ConcurrentHashMap.newKeySet());
        translationService.onPlayerConnect(playerId);

        LOGGER.atFine().log("Initialized custom item sync for player %s", playerId);
    }

    /**
     * Cleans up sync state for a player.
     *
     * <p>Should be called when a player disconnects.
     *
     * @param playerId The player's UUID
     */
    public void onPlayerDisconnect(@Nonnull UUID playerId) {
        Objects.requireNonNull(playerId, "playerId cannot be null");

        syncedItems.remove(playerId);
        // Note: translationService cleanup is handled by ItemSyncService

        LOGGER.atFine().log("Cleaned up custom item sync for player %s", playerId);
    }

    // =========================================================================
    // SINGLE ITEM SYNC
    // =========================================================================

    /**
     * Synchronizes a single custom item to a player.
     *
     * <p>Registers translations first, then sends the item definition via
     * UpdateItems packet if the player doesn't already have it.
     *
     * <p>Packet order (critical):
     * <ol>
     *   <li>UpdateTranslations - register translation keys with text</li>
     *   <li>UpdateItems - register item definition with translation keys</li>
     * </ol>
     *
     * @param playerRef The player reference
     * @param customData The custom item data (must have instanceId)
     * @return true if an update was sent, false if skipped
     */
    public boolean syncItem(
            @Nonnull PlayerRef playerRef,
            @Nonnull CustomItemData customData) {
        Objects.requireNonNull(playerRef, "playerRef cannot be null");
        Objects.requireNonNull(customData, "customData cannot be null");

        UUID playerId = playerRef.getUuid();

        // Verify item has instance ID
        CustomItemInstanceId instanceId = customData.getInstanceId();
        if (instanceId == null) {
            LOGGER.atWarning().log("Custom item sync failed - instanceId is null for %s item. " +
                "Item may appear as unknown to client.", customData.getClass().getSimpleName());
            return false;
        }

        String customItemId = instanceId.toItemId();
        String compactId = instanceId.toCompactString();

        // Check if already synced
        Set<String> playerSynced = syncedItems.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet());
        if (playerSynced.contains(customItemId)) {
            LOGGER.atFine().log("Skipping sync for %s - already synced", customItemId);
            return false;
        }

        // Step 0: Ensure server-side registration (critical for drop/move/use operations)
        // This handles stones/maps that exist in inventory but weren't registered
        // (e.g., from before this fix was added, or after server restart)
        ensureServerSideRegistration(customData, customItemId);

        // Step 1: Register translations FIRST (before item definition)
        // Use compact instance ID for translation keys
        LOGGER.atFine().log("[DEBUG] syncItem called for %s (type=%s)",
            customItemId, customData.getItemType());

        if (!translationService.isRegistered(playerId, compactId)) {
            LOGGER.atFine().log("[DEBUG] Translation NOT registered for %s, building content...", compactId);

            CustomItemDefinitionBuilder.TranslationContent content =
                    definitionBuilder.buildTranslationContent(customData);

            if (content != null) {
                LOGGER.atFine().log("[DEBUG] Translation content built for %s: name='%s', desc length=%d",
                    compactId, content.name(), content.description().length());
                translationService.registerTranslations(
                        playerRef, compactId, content.name(), content.description());
                LOGGER.atFine().log("[DEBUG] Translation registered for %s", compactId);
            } else {
                LOGGER.atWarning().log("[DEBUG] buildTranslationContent returned NULL for %s (type=%s)",
                    compactId, customData.getItemType());
            }
        } else {
            LOGGER.atFine().log("[DEBUG] Translation ALREADY registered for %s, skipping", compactId);
        }

        // Step 2: Build and send the item definition (uses translation keys)
        ItemBase definition = definitionBuilder.build(customData);
        if (definition == null) {
            LOGGER.atWarning().log("Failed to build definition for %s", customItemId);
            return false;
        }

        // Send to player
        sendDefinition(playerRef, customItemId, definition);

        // Mark as synced
        playerSynced.add(customItemId);

        LOGGER.atFine().log("Synced custom item %s to player %s", customItemId, playerId);
        return true;
    }

    /**
     * Ensures the custom item is registered in Hytale's server-side asset map.
     *
     * <p>This is critical for server-side operations like drop, move, and use.
     * Without server-side registration, the server rejects all operations on the item.
     *
     * @param customData The custom item data
     * @param customItemId The custom item ID (e.g., "rpg_stone_123_0")
     */
    private void ensureServerSideRegistration(
            @Nonnull CustomItemData customData,
            @Nonnull String customItemId) {

        if (itemRegistryService == null || !itemRegistryService.isInitialized()) {
            return; // No registry service available
        }

        // Skip if already registered
        if (itemRegistryService.isRegistered(customItemId)) {
            return;
        }

        // Get the base item
        String baseItemId = customData.getBaseItemId();
        Item baseItem = Item.getAssetMap().getAsset(baseItemId);

        if (baseItem == null || baseItem == Item.UNKNOWN) {
            LOGGER.atWarning().log(
                "Cannot register custom item %s - base item %s not found",
                customItemId, baseItemId);
            return;
        }

        // Register in server-side asset map (with DB persistence)
        try {
            itemRegistryService.createAndRegisterSync(baseItem, customItemId);
            LOGGER.atInfo().log("Server-side registered custom item %s (base: %s)",
                customItemId, baseItemId);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log(
                "Failed to register custom item %s", customItemId);
        }
    }

    // =========================================================================
    // BATCH SYNC
    // =========================================================================

    /**
     * Synchronizes all custom items in a collection to a player.
     *
     * <p>Registers all translations first, then sends item definitions.
     *
     * @param playerRef The player reference
     * @param items Collection of custom item data to sync
     * @return Number of items synced
     */
    public int syncAllItems(
            @Nonnull PlayerRef playerRef,
            @Nonnull Collection<CustomItemData> items) {
        Objects.requireNonNull(playerRef, "playerRef cannot be null");
        Objects.requireNonNull(items, "items cannot be null");

        if (items.isEmpty()) {
            return 0;
        }

        UUID playerId = playerRef.getUuid();
        Set<String> playerSynced = syncedItems.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet());

        Map<String, ItemBase> definitionsToSend = new LinkedHashMap<>();
        Map<String, TranslationSyncService.TranslationEntry> translationsToRegister = new LinkedHashMap<>();

        for (CustomItemData customData : items) {
            if (customData == null) {
                continue;
            }

            CustomItemInstanceId instanceId = customData.getInstanceId();
            if (instanceId == null) {
                continue;
            }

            String customItemId = instanceId.toItemId();

            // Skip if already synced
            if (playerSynced.contains(customItemId)) {
                continue;
            }

            // Ensure server-side registration
            ensureServerSideRegistration(customData, customItemId);

            // Collect translations to register (use compact instance ID)
            String compactId = instanceId.toCompactString();
            if (!translationService.isRegistered(playerId, compactId)) {
                CustomItemDefinitionBuilder.TranslationContent content =
                        definitionBuilder.buildTranslationContent(customData);
                if (content != null) {
                    translationsToRegister.put(compactId,
                            new TranslationSyncService.TranslationEntry(content.name(), content.description()));
                }
            }

            // Build definition
            ItemBase definition = definitionBuilder.build(customData);
            if (definition != null) {
                definitionsToSend.put(customItemId, definition);
                playerSynced.add(customItemId);
            }
        }

        if (definitionsToSend.isEmpty()) {
            return 0;
        }

        // Step 1: Register all translations FIRST (batch)
        if (!translationsToRegister.isEmpty()) {
            translationService.registerTranslationsBatch(playerRef, translationsToRegister);
        }

        // Step 2: Send item definitions
        sendDefinitionsBatch(playerRef, definitionsToSend);

        LOGGER.atInfo().log("Batch synced %d custom items to player %s",
            definitionsToSend.size(), playerId);

        return definitionsToSend.size();
    }

    // =========================================================================
    // PACKET SENDING
    // =========================================================================

    /**
     * Sends a single item definition to a player.
     */
    private void sendDefinition(
            @Nonnull PlayerRef playerRef,
            @Nonnull String itemId,
            @Nonnull ItemBase definition) {

        Map<String, ItemBase> items = new HashMap<>();
        items.put(itemId, definition);

        UpdateItems packet = new UpdateItems();
        packet.type = UpdateType.AddOrUpdate;
        packet.items = items;
        packet.updateModels = false;
        packet.updateIcons = false;

        // DEBUG: Log what translation keys are in the definition
        if (definition.translationProperties != null) {
            LOGGER.atFine().log("[DEBUG] UpdateItems for %s: nameKey='%s', descKey='%s'",
                itemId,
                definition.translationProperties.name,
                definition.translationProperties.description);
        } else {
            LOGGER.atWarning().log("[DEBUG] UpdateItems for %s: translationProperties is NULL!", itemId);
        }

        try {
            playerRef.getPacketHandler().writeNoCache(packet);
            LOGGER.atFine().log("[DEBUG] Sent UpdateItems packet for %s", itemId);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log(
                "Failed to send UpdateItems packet to player %s",
                playerRef.getUuid()
            );
        }
    }

    /**
     * Sends item definitions in a batch.
     */
    private void sendDefinitionsBatch(
            @Nonnull PlayerRef playerRef,
            @Nonnull Map<String, ItemBase> definitions) {

        UpdateItems packet = new UpdateItems();
        packet.type = UpdateType.AddOrUpdate;
        packet.items = new HashMap<>(definitions);
        packet.updateModels = false;
        packet.updateIcons = false;

        try {
            playerRef.getPacketHandler().writeNoCache(packet);
            LOGGER.atFine().log("Sent batch of %d custom item definitions", definitions.size());
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log(
                "Failed to send batched UpdateItems packet"
            );
        }
    }

    // =========================================================================
    // STATISTICS
    // =========================================================================

    /**
     * Gets the count of synced items for a player.
     *
     * @param playerId The player's UUID
     * @return Number of synced items
     */
    public int getSyncedCount(@Nonnull UUID playerId) {
        Set<String> synced = syncedItems.get(playerId);
        return synced != null ? synced.size() : 0;
    }

    /**
     * Checks if an item is already synced to a player.
     *
     * @param playerId The player's UUID
     * @param customItemId The custom item ID
     * @return true if already synced
     */
    public boolean isSynced(@Nonnull UUID playerId, @Nonnull String customItemId) {
        Set<String> synced = syncedItems.get(playerId);
        return synced != null && synced.contains(customItemId);
    }

    // =========================================================================
    // ITEM INVALIDATION (for resync after modification)
    // =========================================================================

    /**
     * Invalidates a synced item, allowing it to be resynced.
     *
     * <p>Call this when an item's data has changed (e.g., after stone application)
     * and the client needs to receive the updated item definition.
     *
     * @param playerId The player's UUID
     * @param customItemId The custom item ID to invalidate
     * @return true if the item was invalidated, false if it wasn't synced
     */
    public boolean invalidateItem(@Nonnull UUID playerId, @Nonnull String customItemId) {
        Objects.requireNonNull(playerId, "playerId cannot be null");
        Objects.requireNonNull(customItemId, "customItemId cannot be null");

        Set<String> synced = syncedItems.get(playerId);
        if (synced == null) {
            return false;
        }

        boolean removed = synced.remove(customItemId);
        if (removed) {
            LOGGER.atFine().log("Invalidated custom item %s for player %s", customItemId, playerId);

            // Also invalidate the translation so it gets re-registered with updated content
            // Use compact instance ID (matches registration format from toCompactString())
            // customItemId is like "rpg_map_xxx_yyy", extract "map_xxx_yyy" to match toCompactString()
            int underscorePos = customItemId.indexOf('_');
            if (underscorePos > 0) {
                String compactId = customItemId.substring(underscorePos + 1);
                translationService.unregisterTranslation(playerId, compactId);
            }
        }
        return removed;
    }

    /**
     * Invalidates and immediately resyncs an item to a player.
     *
     * <p>This is a convenience method that combines invalidation and resync.
     * Use after modifying an item's data (e.g., identifying a map, applying a stone).
     *
     * @param playerRef The player reference
     * @param customData The updated custom item data
     * @return true if the item was resynced
     */
    public boolean invalidateAndResync(
            @Nonnull PlayerRef playerRef,
            @Nonnull CustomItemData customData) {
        Objects.requireNonNull(playerRef, "playerRef cannot be null");
        Objects.requireNonNull(customData, "customData cannot be null");

        CustomItemInstanceId instanceId = customData.getInstanceId();
        if (instanceId == null) {
            return false;
        }

        String customItemId = instanceId.toItemId();

        // Invalidate first
        invalidateItem(playerRef.getUuid(), customItemId);

        // Then sync the updated item
        return syncItem(playerRef, customData);
    }

    /**
     * Gets the definition builder.
     */
    @Nonnull
    public CustomItemDefinitionBuilder getDefinitionBuilder() {
        return definitionBuilder;
    }

    @Nonnull
    public TranslationSyncService getTranslationService() {
        return translationService;
    }
}
