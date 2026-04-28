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
import io.github.larsonix.trailoforbis.database.repository.PlayerDataRepository;
import io.github.larsonix.trailoforbis.systems.StatsApplicationSystem;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * ECS tick system that tracks hotbar slot changes and triggers stat recalculation.
 *
 * <p>Unlike utility slot changes which fire {@code SwitchActiveSlotEvent}, hotbar
 * slot changes (via mouse wheel or number keys) go through the Interaction system
 * and don't fire any event. This system monitors the active hotbar slot each tick
 * and triggers recalculation when it changes.
 *
 * <p>The system maintains a cache of each player's last known hotbar slot to detect
 * changes. When a change is detected, it recalculates stats and applies them.
 *
 * @see WeaponSlotChangeSystem for utility slot changes (event-based)
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

        byte currentSlot = inventory.getActiveHotbarSlot();
        Byte lastSlot = lastKnownSlots.get(uuid);

        // First time seeing this player - just record the slot
        if (lastSlot == null) {
            lastKnownSlots.put(uuid, currentSlot);
            return;
        }

        // No change - nothing to do
        if (lastSlot == currentSlot) {
            return;
        }

        // Slot changed! Update cache and trigger recalculation
        lastKnownSlots.put(uuid, currentSlot);

        LOGGER.at(Level.FINE).log("Hotbar slot change detected for player %s: slot %d -> %d",
                uuid, lastSlot, currentSlot);

        // Trigger stat recalculation
        triggerRecalculation(playerRef, store, archetypeChunk, index, uuid);
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

        // Recalculate stats (will pick up new weapon stats).
        // recalculateStats() internally applies to ECS via its callback when changed.
        ComputedStats newStats = cachedAttributeService.recalculateStats(uuid);
        if (newStats == null) {
            LOGGER.at(Level.WARNING).log("Failed to recalculate stats for %s after hotbar slot change", uuid);
            return;
        }

        LOGGER.at(Level.FINE).log("Recalculated stats for %s after hotbar slot change", uuid);
    }

    /**
     * Cleans up tracking data when a player disconnects.
     * Should be called from the player disconnect handler.
     *
     * @param uuid The player's UUID
     */
    public void onPlayerDisconnect(@Nonnull UUID uuid) {
        lastKnownSlots.remove(uuid);
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
