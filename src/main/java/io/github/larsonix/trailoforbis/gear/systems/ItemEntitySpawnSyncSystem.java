package io.github.larsonix.trailoforbis.gear.systems;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.gear.GearManager;
import io.github.larsonix.trailoforbis.gear.GearService;
import io.github.larsonix.trailoforbis.gear.item.ItemWorldSyncService;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.util.GearUtils;
import io.github.larsonix.trailoforbis.maps.items.RealmMapUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;

/**
 * ECS system that syncs custom item definitions to nearby players when items spawn.
 *
 * <p>This system solves the pickup notification display issue where Hytale's
 * built-in {@code notifyPickupItem()} (Player.java:398) fires IMMEDIATELY when
 * a player picks up an item, BEFORE our late-priority listeners can sync the
 * custom item definition.
 *
 * <h2>Problem Flow (Without This System)</h2>
 * <pre>
 * Player picks up item → notifyPickupItem() fires → Shows raw key (NO DEFINITION!)
 *        ↓ (later)
 * GearPickupListener runs → Syncs definition (TOO LATE!)
 * </pre>
 *
 * <h2>Solution Flow (With This System)</h2>
 * <pre>
 * Item entity spawns in world
 *        ↓
 * ItemEntitySpawnSyncSystem.onEntityAdded()
 *        ↓
 * Sync definition to ALL nearby players
 *        ↓
 * Player picks up item → notifyPickupItem() fires → Shows correctly!
 * </pre>
 *
 * <h2>What This Catches</h2>
 * <ul>
 *   <li>Items dropped by mobs (loot) - though LootListener also handles this</li>
 *   <li>Items dropped by players manually</li>
 *   <li>Items from containers (chests, etc.)</li>
 *   <li>Items spawned by admin commands</li>
 *   <li>Any other source of item entity creation</li>
 * </ul>
 *
 * @see ItemWorldSyncService For the centralized sync logic
 * @see io.github.larsonix.trailoforbis.gear.loot.LootListener For mob death drops
 */
public class ItemEntitySpawnSyncSystem extends RefSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Query<EntityStore> query;

    /** Cached reference — resolved once on first use, never changes. */
    @Nullable
    private volatile ItemWorldSyncService cachedWorldSyncService;

    /**
     * Creates a new ItemEntitySpawnSyncSystem.
     */
    public ItemEntitySpawnSyncSystem() {
        // Query for entities with both ItemComponent and TransformComponent
        this.query = Archetype.of(
            ItemComponent.getComponentType(),
            TransformComponent.getComponentType()
        );
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return this.query;
    }

    @Override
    public void onEntityAdded(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull AddReason reason,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        // Get item component
        ItemComponent itemComp = commandBuffer.getComponent(ref, ItemComponent.getComponentType());
        if (itemComp == null) {
            return;
        }

        ItemStack itemStack = itemComp.getItemStack();
        if (itemStack == null || itemStack.isEmpty()) {
            return;
        }

        // Get ItemWorldSyncService (cached — resolved once, never changes)
        ItemWorldSyncService worldSyncService = getCachedWorldSyncService();
        if (worldSyncService == null) {
            return;
        }

        // Fast check: if this item was recently batch-synced by LootListener,
        // skip all work (no deserialization, no range check).
        String itemId = itemStack.getItemId();
        if (itemId != null && worldSyncService.wasRecentlyBatchSynced(itemId)) {
            return;
        }

        // Fast prefix check before expensive GearData deserialization
        if (!isCustomItem(itemStack)) {
            return;
        }

        // Get position from transform
        TransformComponent transform = commandBuffer.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            return;
        }

        Vector3d position = transform.getPosition();

        // Sync to all nearby players
        int synced = worldSyncService.syncItemToNearbyPlayers(store, position, itemStack);

        if (synced > 0) {
            LOGGER.atFine().log("[ItemSpawnSync] Pre-synced %s to %d player(s) at (%.0f, %.0f, %.0f)",
                itemId, synced, position.x, position.y, position.z);
        }
    }

    @Override
    public void onEntityRemove(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull RemoveReason reason,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        // No cleanup needed when item entities are removed
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    /**
     * Checks if an item is a custom RPG item (gear, stone, or map).
     * Uses fast prefix check for gear to avoid unnecessary deserialization.
     */
    private boolean isCustomItem(@Nonnull ItemStack item) {
        String itemId = item.getItemId();

        // Fast prefix check for gear — avoids GearData deserialization for non-gear items
        if (itemId != null && itemId.startsWith("rpg_gear_")) {
            Optional<GearData> gearOpt = GearUtils.readGearData(item);
            if (gearOpt.isPresent() && gearOpt.get().hasInstanceId()) {
                return true;
            }
        }

        // Check realm map (using isMapAnyMethod for reconnect safety)
        if (RealmMapUtils.isMapAnyMethod(item)) {
            var mapOpt = RealmMapUtils.readMapDataWithFallback(item);
            if (mapOpt.isPresent() && mapOpt.get().hasInstanceId()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Gets the ItemWorldSyncService, caching the result after first resolution.
     */
    @Nullable
    private ItemWorldSyncService getCachedWorldSyncService() {
        ItemWorldSyncService cached = cachedWorldSyncService;
        if (cached != null) return cached;

        Optional<GearService> gearServiceOpt = ServiceRegistry.get(GearService.class);
        if (gearServiceOpt.isEmpty()) return null;

        GearService gearService = gearServiceOpt.get();
        if (!(gearService instanceof GearManager gearManager)) return null;

        cached = gearManager.getItemWorldSyncService();
        cachedWorldSyncService = cached;
        return cached;
    }
}
