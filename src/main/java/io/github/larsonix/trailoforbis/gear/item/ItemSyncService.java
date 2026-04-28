package io.github.larsonix.trailoforbis.gear.item;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.ItemBase;
import com.hypixel.hytale.protocol.UpdateType;
import com.hypixel.hytale.protocol.packets.assets.UpdateItems;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.util.GearUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.LongSupplier;
import java.util.function.ToLongFunction;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for synchronizing custom item definitions to players.
 *
 * <p>Manages the lifecycle of dynamic item definitions:
 * <ul>
 *   <li>Initializes player caches on connect</li>
 *   <li>Syncs item definitions when gear is picked up or equipped</li>
 *   <li>Registers translations before sending item definitions</li>
 *   <li>Batches updates for network efficiency</li>
 *   <li>Cleans up player data on disconnect</li>
 * </ul>
 *
 * <h2>Architecture</h2>
 * <pre>
 * GearPickupListener / GearEquipmentListener
 *              ↓
 *       ItemSyncService.syncItem()
 *              ↓
 *       TranslationSyncService.registerTranslations()  ← NEW
 *              ↓
 *       ItemDefinitionBuilder.build()
 *              ↓
 *       PlayerItemCache.needsUpdate()
 *              ↓
 *       UpdateTranslations packet → Player  ← First
 *       UpdateItems packet → Player         ← Then
 * </pre>
 *
 * <h2>Thread Safety</h2>
 * <p>All public methods are thread-safe.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Initialize service
 * TranslationSyncService translationService = new TranslationSyncService();
 * ItemSyncService service = new ItemSyncService(definitionBuilder, translationService, config);
 *
 * // On player connect
 * service.onPlayerConnect(playerId);
 *
 * // When gear is picked up or modified
 * service.syncItem(playerRef, itemStack, gearData);
 *
 * // On player disconnect
 * service.onPlayerDisconnect(playerId);
 * }</pre>
 */
public final class ItemSyncService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final ItemDefinitionBuilder definitionBuilder;
    private final TranslationSyncService translationService;
    private final PlayerItemCache playerCache;
    private final ItemSyncConfig config;

    /**
     * Provider for player stats version.
     *
     * <p>When set, the stats version is included in definition hashes, ensuring
     * that tooltip updates are triggered when player stats change.
     */
    private volatile ToLongFunction<UUID> statsVersionProvider;

    /**
     * Creates an ItemSyncService with the specified configuration.
     *
     * @param definitionBuilder Builder for item definitions
     * @param translationService Service for dynamic translation registration
     * @param config Sync configuration
     */
    public ItemSyncService(
            @Nonnull ItemDefinitionBuilder definitionBuilder,
            @Nonnull TranslationSyncService translationService,
            @Nonnull ItemSyncConfig config) {
        this.definitionBuilder = Objects.requireNonNull(definitionBuilder, "definitionBuilder cannot be null");
        this.translationService = Objects.requireNonNull(translationService, "translationService cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.playerCache = new PlayerItemCache();
    }

    /**
     * Creates an ItemSyncService with default configuration.
     *
     * @param definitionBuilder Builder for item definitions
     * @param translationService Service for dynamic translation registration
     */
    public ItemSyncService(
            @Nonnull ItemDefinitionBuilder definitionBuilder,
            @Nonnull TranslationSyncService translationService) {
        this(definitionBuilder, translationService, ItemSyncConfig.defaults());
    }

    // =========================================================================
    // PLAYER LIFECYCLE
    // =========================================================================

    /**
     * Initializes sync state for a player.
     *
     * <p>Should be called when a player connects, before any gear sync.
     *
     * @param playerId The player's UUID
     */
    public void onPlayerConnect(@Nonnull UUID playerId) {
        Objects.requireNonNull(playerId, "playerId cannot be null");

        if (!config.enabled()) {
            return;
        }

        playerCache.initPlayer(playerId);
        translationService.onPlayerConnect(playerId);

        LOGGER.atInfo().log("Initialized item sync for player %s", playerId);
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

        playerCache.removePlayer(playerId);
        translationService.onPlayerDisconnect(playerId);

        LOGGER.atInfo().log("Cleaned up item sync for player %s", playerId);
    }

    // =========================================================================
    // SINGLE ITEM SYNC
    // =========================================================================

    /**
     * Synchronizes a single gear item to a player.
     *
     * <p>Registers translations first, then sends the item definition via
     * UpdateItems packet if the player doesn't already have the current version.
     *
     * <p>Packet order (critical):
     * <ol>
     *   <li>UpdateTranslations - register translation keys with text</li>
     *   <li>UpdateItems - register item definition with translation keys</li>
     * </ol>
     *
     * @param playerRef The player reference
     * @param itemStack The item stack
     * @param gearData The gear data (must have instanceId)
     * @return true if an update was sent, false if skipped
     */
    public boolean syncItem(
            @Nonnull PlayerRef playerRef,
            @Nonnull ItemStack itemStack,
            @Nonnull GearData gearData) {
        Objects.requireNonNull(playerRef, "playerRef cannot be null");
        Objects.requireNonNull(itemStack, "itemStack cannot be null");
        Objects.requireNonNull(gearData, "gearData cannot be null");

        if (!config.enabled()) {
            return false;
        }

        UUID playerId = playerRef.getUuid();

        // Verify gear has instance ID
        if (!gearData.hasInstanceId()) {
            LOGGER.atFine().log("Skipping sync for gear without instanceId");
            return false;
        }

        String itemId = gearData.getItemId();
        String instanceId = gearData.instanceId().toCompactString();

        // Get stats version for accurate hash (detects when player stats change)
        long statsVersion = getStatsVersion(playerId);

        // Compute hash for change detection (includes stats version)
        int definitionHash = definitionBuilder.computeDefinitionHash(gearData, playerId, statsVersion);

        // Check if update needed
        if (!playerCache.needsUpdate(playerId, itemId, definitionHash)) {
            LOGGER.atFine().log("Skipping sync for %s - already up to date", itemId);
            return false;
        }

        // Step 1: Register/update translations FIRST (before item definition)
        // When stats change, requirement colors in tooltip change, so we always rebuild
        // and re-register translations when the hash indicates an update is needed.
        // First unregister old translation to ensure fresh content is sent.
        translationService.unregisterTranslation(playerId, instanceId);

        ItemDefinitionBuilder.TranslationContent content =
                definitionBuilder.buildTranslationContent(itemStack, gearData, playerId);
        if (content != null) {
            translationService.registerTranslations(
                    playerRef, instanceId, content.name(), content.description());
        } else {
            LOGGER.atWarning().log("Failed to build translation content for %s", itemId);
        }

        // Step 2: Build and send the item definition (uses translation keys)
        ItemBase definition = definitionBuilder.build(itemStack, gearData, playerId);
        if (definition == null) {
            LOGGER.atWarning().log("Failed to build definition for %s", itemId);
            return false;
        }

        // Send to player
        sendDefinition(playerRef, itemId, definition);

        // Mark as sent
        playerCache.markSent(playerId, itemId, definitionHash);

        LOGGER.atFine().log("Synced item %s to player %s", itemId, playerId);
        return true;
    }

    /**
     * Synchronizes a gear item, reading GearData from the ItemStack metadata.
     *
     * @param playerRef The player reference
     * @param itemStack The item stack (must have gear metadata)
     * @return true if an update was sent, false if skipped or no gear data
     */
    public boolean syncItem(@Nonnull PlayerRef playerRef, @Nonnull ItemStack itemStack) {
        Objects.requireNonNull(playerRef, "playerRef cannot be null");
        Objects.requireNonNull(itemStack, "itemStack cannot be null");

        Optional<GearData> gearDataOpt = GearUtils.readGearData(itemStack);
        if (gearDataOpt.isEmpty()) {
            return false;
        }

        return syncItem(playerRef, itemStack, gearDataOpt.get());
    }

    // =========================================================================
    // BATCH SYNC
    // =========================================================================

    /**
     * Synchronizes all gear items in an inventory to a player.
     *
     * <p>Registers all translations first, then sends item definitions in batches.
     *
     * @param playerRef The player reference
     * @param items Collection of item stacks to sync
     * @return Number of items synced
     */
    public int syncAllItems(
            @Nonnull PlayerRef playerRef,
            @Nonnull Collection<ItemStack> items) {
        Objects.requireNonNull(playerRef, "playerRef cannot be null");
        Objects.requireNonNull(items, "items cannot be null");

        if (!config.enabled() || items.isEmpty()) {
            return 0;
        }

        UUID playerId = playerRef.getUuid();
        Map<String, ItemBase> definitionsToSend = new LinkedHashMap<>();
        Map<String, TranslationSyncService.TranslationEntry> translationsToRegister = new LinkedHashMap<>();

        // Get stats version once for all items (same player)
        long statsVersion = getStatsVersion(playerId);

        for (ItemStack itemStack : items) {
            if (itemStack == null || itemStack.isEmpty()) {
                continue;
            }

            Optional<GearData> gearDataOpt = GearUtils.readGearData(itemStack);
            if (gearDataOpt.isEmpty() || !gearDataOpt.get().hasInstanceId()) {
                continue;
            }

            GearData gearData = gearDataOpt.get();
            String itemId = gearData.getItemId();
            String instanceId = gearData.instanceId().toCompactString();
            int definitionHash = definitionBuilder.computeDefinitionHash(gearData, playerId, statsVersion);

            if (!playerCache.needsUpdate(playerId, itemId, definitionHash)) {
                continue;
            }

            // Always rebuild and collect translations (requirement colors depend on stats)
            // Unregister first to ensure fresh content
            translationService.unregisterTranslation(playerId, instanceId);

            ItemDefinitionBuilder.TranslationContent content =
                    definitionBuilder.buildTranslationContent(itemStack, gearData, playerId);
            if (content != null) {
                translationsToRegister.put(instanceId,
                        new TranslationSyncService.TranslationEntry(content.name(), content.description()));
            }

            // Build definition
            ItemBase definition = definitionBuilder.build(itemStack, gearData, playerId);
            if (definition != null) {
                definitionsToSend.put(itemId, definition);
                playerCache.markSent(playerId, itemId, definitionHash);
            }
        }

        if (definitionsToSend.isEmpty()) {
            return 0;
        }

        // Step 1: Register all translations FIRST (batch)
        if (!translationsToRegister.isEmpty()) {
            translationService.registerTranslationsBatch(playerRef, translationsToRegister);
        }

        // Step 2: Send item definitions in batches
        sendDefinitionsBatched(playerRef, definitionsToSend);

        LOGGER.atInfo().log("Batch synced %d items to player %s",
            definitionsToSend.size(), playerId);

        return definitionsToSend.size();
    }
    // ITEM REMOVAL
    // =========================================================================

    /**
     * Notifies that a player no longer has a gear item.
     *
     * <p>Removes the item from cache and translation tracking.
     * Does NOT send a remove packet (the base game handles item removal separately).
     *
     * @param playerId The player's UUID
     * @param itemId The item ID to remove from cache
     */
    public void onItemRemoved(@Nonnull UUID playerId, @Nonnull String itemId) {
        Objects.requireNonNull(playerId, "playerId cannot be null");
        Objects.requireNonNull(itemId, "itemId cannot be null");

        playerCache.removeItem(playerId, itemId);

        // Unregister translation using compact instance ID (matches registration format)
        // Note: itemId is "rpg_gear_xxx_yyy", we need to extract the compact "xxx_yyy" part
        if (itemId.startsWith("rpg_gear_")) {
            String instanceId = itemId.substring("rpg_gear_".length());
            translationService.unregisterTranslation(playerId, instanceId);
        }
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

        // DEBUG: Log what translation keys are in the gear definition
        if (definition.translationProperties != null) {
            LOGGER.atFine().log("[DEBUG-GEAR] UpdateItems for %s: nameKey='%s', descKey='%s'",
                itemId,
                definition.translationProperties.name,
                definition.translationProperties.description);
        } else {
            LOGGER.atWarning().log("[DEBUG-GEAR] UpdateItems for %s: translationProperties is NULL!", itemId);
        }

        try {
            playerRef.getPacketHandler().writeNoCache(packet);
            LOGGER.atFine().log("[DEBUG-GEAR] Sent UpdateItems packet for %s", itemId);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log(
                "Failed to send UpdateItems packet to player %s",
                playerRef.getUuid()
            );
        }
    }

    /**
     * Sends item definitions in batches.
     */
    private void sendDefinitionsBatched(
            @Nonnull PlayerRef playerRef,
            @Nonnull Map<String, ItemBase> definitions) {

        int maxPerPacket = config.maxItemsPerPacket();
        Map<String, ItemBase> batch = new HashMap<>();

        for (Map.Entry<String, ItemBase> entry : definitions.entrySet()) {
            batch.put(entry.getKey(), entry.getValue());

            if (batch.size() >= maxPerPacket) {
                sendBatch(playerRef, batch);
                batch.clear();
            }
        }

        // Send remaining
        if (!batch.isEmpty()) {
            sendBatch(playerRef, batch);
        }
    }

    /**
     * Sends a batch of item definitions.
     */
    private void sendBatch(
            @Nonnull PlayerRef playerRef,
            @Nonnull Map<String, ItemBase> batch) {

        UpdateItems packet = new UpdateItems();
        packet.type = UpdateType.AddOrUpdate;
        packet.items = new HashMap<>(batch);
        packet.updateModels = false;
        packet.updateIcons = false;

        try {
            playerRef.getPacketHandler().writeNoCache(packet);
            LOGGER.atFine().log("Sent batch of %d item definitions", batch.size());
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log(
                "Failed to send batched UpdateItems packet"
            );
        }
    }

    // =========================================================================
    // STATS VERSION INTEGRATION
    // =========================================================================

    /**
     * Sets the stats version provider.
     *
     * <p>When set, the stats version is included in definition hash computations,
     * ensuring that tooltip updates are triggered when player stats change
     * (level up, allocate attribute points, skill tree changes, etc.).
     *
     * @param provider Function that returns the current stats version for a player
     */
    public void setStatsVersionProvider(@Nullable ToLongFunction<UUID> provider) {
        this.statsVersionProvider = provider;
        if (provider != null) {
            LOGGER.atInfo().log("Stats version provider set - tooltips will update on stat changes");
        }
    }

    /**
     * Gets the current stats version for a player.
     *
     * @param playerId The player's UUID
     * @return The stats version, or 0 if no provider is set
     */
    private long getStatsVersion(@Nonnull UUID playerId) {
        ToLongFunction<UUID> provider = this.statsVersionProvider;
        return provider != null ? provider.applyAsLong(playerId) : 0L;
    }

    // =========================================================================
    // ACCESSORS
    // =========================================================================

    /**
     * Whether this service is enabled and ready to sync items.
     */
    public boolean isEnabled() {
        return config.enabled();
    }

    /**
     * Gets the player item cache (hash-based change detection).
     */
    @Nonnull
    public PlayerItemCache getPlayerCache() {
        return playerCache;
    }

    /**
     * Gets the translation sync service.
     */
    @Nonnull
    public TranslationSyncService getTranslationService() {
        return translationService;
    }
}
