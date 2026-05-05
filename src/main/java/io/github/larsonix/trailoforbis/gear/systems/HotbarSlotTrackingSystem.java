package io.github.larsonix.trailoforbis.gear.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.api.services.AttributeService;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.combat.attackspeed.AnimationSpeedSyncManager;
import io.github.larsonix.trailoforbis.database.repository.PlayerDataRepository;
import io.github.larsonix.trailoforbis.gear.GearManager;
import io.github.larsonix.trailoforbis.gear.GearService;
import io.github.larsonix.trailoforbis.gear.sync.ItemSyncCoordinator;
import io.github.larsonix.trailoforbis.systems.StatsApplicationSystem;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * ECS tick system that tracks active slot changes and triggers stat recalculation.
 *
 * <p>Monitors both hotbar and utility (offhand) active slots every tick.
 * When a change is detected, triggers a full stat recalculation.
 *
 * <p><b>Why tick-based instead of event-based?</b><br>
 * Hytale fires {@code SwitchActiveSlotEvent} via synchronous {@code store.invoke()}
 * <i>before</i> updating the inventory's active slot field. Any event handler that
 * reads {@code inventory.getUtilityItem()} during that event will get the OLD item.
 * A tick-based system always reads state <i>after</i> the packet handler has updated it,
 * guaranteeing correct data.
 *
 * <p>Hotbar slot changes never fire {@code SwitchActiveSlotEvent} at all (they go
 * through {@code ChangeActiveSlotInteraction}), so tick-based tracking is the only
 * way to detect them.
 *
 * @see WeaponSlotChangeSystem (does NOT handle utility — defers to this system)
 */
public class HotbarSlotTrackingSystem extends EntityTickingSystem<EntityStore> {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final ComponentType<EntityStore, PlayerRef> playerRefType;
    private final ComponentType<EntityStore, Player> playerType;

    /**
     * Cache of last known hotbar slot per player UUID.
     * Using ConcurrentHashMap for thread safety.
     */
    private final Map<UUID, Byte> lastKnownSlots = new ConcurrentHashMap<>();

    /**
     * Cache of last known utility (offhand) slot per player UUID.
     */
    private final Map<UUID, Byte> lastKnownUtilitySlots = new ConcurrentHashMap<>();

    /** Cached service reference — resolved once on first tick, never changes */
    @Nullable private AttributeService cachedAttributeService;


    public HotbarSlotTrackingSystem() {
        this.playerRefType = PlayerRef.getComponentType();
        this.playerType = Player.getComponentType();
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        // Only process entities with both PlayerRef and Player components
        return Query.and(playerRefType, playerType);
    }

    @Override
    public boolean isParallel(int archetypeChunkSize, int taskCount) {
        // Run sequentially to avoid race conditions with stat recalculation
        return false;
    }

    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        PlayerRef playerRef = archetypeChunk.getComponent(index, playerRefType);
        Player player = archetypeChunk.getComponent(index, playerType);

        if (playerRef == null || player == null) {
            return;
        }

        UUID uuid = playerRef.getUuid();
        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return;
        }

        // Track hotbar slot changes
        byte currentHotbar = inventory.getActiveHotbarSlot();
        Byte lastHotbar = lastKnownSlots.get(uuid);
        boolean hotbarChanged = false;

        if (lastHotbar == null) {
            lastKnownSlots.put(uuid, currentHotbar);
        } else if (lastHotbar != currentHotbar) {
            lastKnownSlots.put(uuid, currentHotbar);
            hotbarChanged = true;
            LOGGER.at(Level.FINE).log("Hotbar slot change detected for player %s: slot %d -> %d",
                    uuid, lastHotbar, currentHotbar);
        }

        // Track utility (offhand) slot changes
        byte currentUtility = inventory.getActiveUtilitySlot();
        Byte lastUtility = lastKnownUtilitySlots.get(uuid);
        boolean utilityChanged = false;

        if (lastUtility == null) {
            lastKnownUtilitySlots.put(uuid, currentUtility);
        } else if (lastUtility != currentUtility) {
            lastKnownUtilitySlots.put(uuid, currentUtility);
            utilityChanged = true;
            LOGGER.at(Level.FINE).log("Utility slot change detected for player %s: slot %d -> %d",
                    uuid, lastUtility, currentUtility);
        }

        if (hotbarChanged || utilityChanged) {
            triggerRecalculation(playerRef, store, archetypeChunk, index, uuid);
        }
    }

    /**
     * Triggers stat recalculation and applies updated stats to ECS.
     */
    private void triggerRecalculation(
            @Nonnull PlayerRef playerRef,
            @Nonnull Store<EntityStore> store,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            int index,
            @Nonnull UUID uuid
    ) {
        // Lazy-init cached service — resolves once, never changes
        if (cachedAttributeService == null) {
            cachedAttributeService = ServiceRegistry.get(AttributeService.class).orElse(null);
            if (cachedAttributeService == null) {
                return;
            }
        }

        // Recalculate stats (will pick up new weapon/utility stats).
        // recalculateStats() internally applies to ECS via its callback when changed.
        ComputedStats newStats = cachedAttributeService.recalculateStats(uuid);
        if (newStats == null) {
            LOGGER.at(Level.WARNING).log("Failed to recalculate stats for %s after slot change", uuid);
            return;
        }

        // Sync animation speed to match new attack speed stat
        ServiceRegistry.get(AnimationSpeedSyncManager.class)
                .ifPresent(m -> m.syncAnimationSpeed(uuid));

        // Mark gear tooltips dirty so requirement colors update for the new active item.
        // Note: If the player is in the Skill Sanctum, ItemSyncCoordinator suppresses the flush
        // automatically — no need for reactive visual recovery on sanctum nodes.
        ServiceRegistry.get(GearService.class).ifPresent(svc -> {
            if (svc instanceof GearManager mgr) {
                ItemSyncCoordinator coordinator = mgr.getSyncCoordinator();
                if (coordinator != null) {
                    coordinator.markEquipmentDirty(uuid);
                }
            }
        });

        LOGGER.at(Level.FINE).log("Recalculated stats for %s after slot change", uuid);
    }

    // scheduleVisualRefreshIfInSanctum() REMOVED — sanctum suppression in ItemSyncCoordinator
    // prevents UpdateItems flushes from firing while the player is in the sanctum.

    /**
     * Cleans up tracking data when a player disconnects.
     * Should be called from the player disconnect handler.
     *
     * @param uuid The player's UUID
     */
    public void onPlayerDisconnect(@Nonnull UUID uuid) {
        lastKnownSlots.remove(uuid);
        lastKnownUtilitySlots.remove(uuid);
    }

    /**
     * Gets the number of players currently being tracked.
     * Useful for debugging.
     *
     * @return Number of tracked players
     */
    public int getTrackedPlayerCount() {
        return lastKnownSlots.size();
    }
}
