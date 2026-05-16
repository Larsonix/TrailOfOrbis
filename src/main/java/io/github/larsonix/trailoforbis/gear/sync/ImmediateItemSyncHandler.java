package io.github.larsonix.trailoforbis.gear.sync;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.SlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.Transaction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.gear.item.CustomItemData;
import io.github.larsonix.trailoforbis.gear.item.CustomItemInstanceId;
import io.github.larsonix.trailoforbis.gear.item.CustomItemSyncService;
import io.github.larsonix.trailoforbis.gear.item.ItemSyncService;
import io.github.larsonix.trailoforbis.gear.model.GearData;
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
import java.util.UUID;

/**
 * Immediate, zero-delay item definition sync on every inventory change.
 *
 * <h2>Problem</h2>
 * <p>Hytale's inventory change pipeline is tick-based: {@code setItemStackForSlot()} enqueues
 * an {@code ItemContainerChangeEvent} in a {@code ConcurrentLinkedQueue}. On the next ECS tick,
 * {@code InventorySystems.InventoryChangeEventSystem} polls the queue and dispatches
 * {@code InventoryChangeEvent}. A separate system then sends {@code UpdatePlayerInventory}
 * in the same tick cycle. If our {@code UpdateItems} packet (item definition) hasn't been sent
 * by that point, the client receives an inventory referencing an unknown item ID → displays "?".
 *
 * <h2>Solution</h2>
 * <p>This handler is registered as the <b>first</b> handler on
 * {@code InventoryChangeEventSystem} (via {@code addFirstHandler}). For every inventory change
 * event — regardless of container type — it scans the changed container for RPG items whose
 * definitions haven't been sent to this player yet, and syncs them <b>immediately</b>.
 *
 * <p>The sync is idempotent: {@code PlayerItemCache.needsUpdate()} returns false for items
 * already sent with the same definition hash, so repeated scans cost only hash lookups.
 *
 * <h2>Scope</h2>
 * <p>Handles all custom item types:
 * <ul>
 *   <li>RPG Gear (weapons, armor with instance IDs)</li>
 *   <li>Realm Maps (custom item definitions)</li>
 *   <li>Gems (custom item definitions)</li>
 * </ul>
 *
 * <p>Stones are native Hytale items and need no custom sync.
 *
 * <h2>Thread Safety</h2>
 * <p>Runs on the ECS tick thread (world thread). All sync service calls are thread-safe.
 *
 * @see ItemSyncCoordinator For debounced stat-dependent sync (requirement colors, etc.)
 * @see ItemSyncService For the actual packet sending
 */
public final class ImmediateItemSyncHandler {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final ItemSyncService itemSyncService;
    @Nullable private final CustomItemSyncService customItemSyncService;
    @Nullable private final ItemSyncCoordinator coordinator;

    public ImmediateItemSyncHandler(
            @Nonnull ItemSyncService itemSyncService,
            @Nullable CustomItemSyncService customItemSyncService,
            @Nullable ItemSyncCoordinator coordinator) {
        this.itemSyncService = Objects.requireNonNull(itemSyncService, "itemSyncService");
        this.customItemSyncService = customItemSyncService;
        this.coordinator = coordinator;
    }

    /**
     * Handles an inventory change event by immediately syncing any unsynced RPG items.
     *
     * <p>Uses the event's transaction data to sync only changed slots rather than
     * scanning the entire container. Falls back to full scan if no transaction data
     * is available (edge cases like programmatic bulk adds).
     *
     * <p>Registered via {@code InventoryChangeEventSystem.addHandler()} so it runs
     * before notification handlers, ensuring {@code UpdateItems} is queued in the same
     * tick as the inventory change — before Hytale's sync system sends
     * {@code UpdatePlayerInventory}.
     */
    public void onInventoryChange(@Nonnull Player player, @Nonnull InventoryChangeEvent event) {
        // Resolve PlayerRef from ECS
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        UUID playerId = playerRef.getUuid();

        // During world transitions, the client is still processing JoinWorldPacket.
        // Sending UpdateItems now causes NullReferenceException on the client.
        // The coordinator's post-PlayerReady flush handles sync after transition.
        if (coordinator != null && coordinator.isPlayerSuppressed(playerId)) {
            return;
        }

        ItemContainer container = event.getItemContainer();
        if (container == null) {
            return;
        }

        // Use transaction data to target only changed slots (avoids O(capacity) scan)
        Transaction transaction = event.getTransaction();
        if (transaction != null) {
            syncFromTransaction(playerRef, container, transaction);
        } else {
            // Fallback: no transaction data (edge case). Full scan with hash dedup.
            syncUnsyncedItems(playerRef, container);
        }
    }

    /**
     * Syncs only items from the transaction's changed slots.
     * Much cheaper than scanning all 36+ slots for the common case.
     */
    private void syncFromTransaction(@Nonnull PlayerRef playerRef,
                                     @Nonnull ItemContainer container,
                                     @Nonnull Transaction transaction) {
        int synced = 0;

        if (transaction instanceof ItemStackTransaction ist) {
            for (ItemStackSlotTransaction slotTx : ist.getSlotTransactions()) {
                if (syncSlotIfNeeded(playerRef, container, slotTx)) {
                    synced++;
                }
            }
        } else if (transaction instanceof SlotTransaction slotTx) {
            if (syncSlotIfNeeded(playerRef, container, slotTx)) {
                synced++;
            }
        }

        if (synced > 0) {
            LOGGER.atFine().log("[ImmediateSync] Synced %d item definition(s) for player %s",
                    synced, playerRef.getUuid().toString().substring(0, 8));
        }
    }

    /**
     * Syncs a single slot's item if it needs an update.
     *
     * @return true if a sync was sent
     */
    private boolean syncSlotIfNeeded(@Nonnull PlayerRef playerRef,
                                     @Nonnull ItemContainer container,
                                     @Nonnull SlotTransaction slotTx) {
        if (!slotTx.succeeded()) {
            return false;
        }

        // Only care about items ADDED (after is non-empty)
        ItemStack after = slotTx.getSlotAfter();
        if (after == null || after.isEmpty()) {
            return false;
        }

        // Verify the item is still in the container (loot filter may have ejected it)
        ItemStack current = container.getItemStack(slotTx.getSlot());
        if (current == null || current.isEmpty()) {
            return false;
        }

        // Sync the item (hash dedup inside prevents duplicate packets)
        if (trySyncGear(playerRef, current)) return true;
        if (trySyncMap(playerRef, current)) return true;
        return trySyncGem(playerRef, current);
    }

    /**
     * Fallback: scans all slots in a container for unsynced RPG items.
     * Used only when transaction data is unavailable.
     */
    private void syncUnsyncedItems(@Nonnull PlayerRef playerRef, @Nonnull ItemContainer container) {
        short capacity = container.getCapacity();
        int synced = 0;

        for (short slot = 0; slot < capacity; slot++) {
            ItemStack item = container.getItemStack(slot);
            if (item == null || item.isEmpty()) {
                continue;
            }

            if (trySyncGear(playerRef, item)) {
                synced++;
                continue;
            }

            if (trySyncMap(playerRef, item)) {
                synced++;
                continue;
            }

            if (trySyncGem(playerRef, item)) {
                synced++;
            }
        }

        if (synced > 0) {
            LOGGER.atFine().log("[ImmediateSync] Synced %d item definition(s) for player %s (full scan fallback)",
                    synced, playerRef.getUuid().toString().substring(0, 8));
        }
    }

    /**
     * Attempts to sync a gear item. Returns true if a sync was sent.
     */
    private boolean trySyncGear(@Nonnull PlayerRef playerRef, @Nonnull ItemStack item) {
        Optional<GearData> gearOpt = GearUtils.readGearData(item);
        if (gearOpt.isEmpty()) {
            return false;
        }
        GearData gearData = gearOpt.get();
        if (!gearData.hasInstanceId()) {
            return false;
        }
        // syncItem() checks PlayerItemCache.needsUpdate() internally — no-op if already synced
        return itemSyncService.syncItem(playerRef, item, gearData);
    }

    /**
     * Attempts to sync a realm map item. Returns true if a sync was sent.
     */
    private boolean trySyncMap(@Nonnull PlayerRef playerRef, @Nonnull ItemStack item) {
        if (customItemSyncService == null) {
            return false;
        }
        Optional<RealmMapData> mapOpt = RealmMapUtils.readMapDataWithFallback(item);
        if (mapOpt.isEmpty() || !mapOpt.get().hasInstanceId()) {
            return false;
        }
        return customItemSyncService.syncItem(playerRef, mapOpt.get());
    }

    /**
     * Attempts to sync a gem item. Returns true if a sync was sent.
     */
    private boolean trySyncGem(@Nonnull PlayerRef playerRef, @Nonnull ItemStack item) {
        if (customItemSyncService == null) {
            return false;
        }
        Optional<GemData> gemOpt = GemUtils.readGemData(item);
        if (gemOpt.isEmpty()) {
            return false;
        }
        Optional<GemManager> gemManagerOpt = ServiceRegistry.get(GemManager.class);
        if (gemManagerOpt.isEmpty() || gemManagerOpt.get().getRegistry() == null) {
            return false;
        }
        Optional<GemDefinition> defOpt = gemManagerOpt.get().getRegistry()
                .getDefinition(gemOpt.get().gemId());
        CustomItemInstanceId instanceId = CustomItemInstanceId.fromItemId(item.getItemId());
        if (defOpt.isEmpty() || instanceId == null) {
            return false;
        }
        return customItemSyncService.syncItem(playerRef,
                new GemItemData(instanceId, defOpt.get(), gemOpt.get()));
    }
}
