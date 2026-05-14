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
 * <p>This is the sole system for weapon/utility change detection. WeaponSlotChangeSystem
 * was deleted — it read inventory before Hytale updated the active slot field.
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
     * Cache of last known weapon item definition ID per player UUID.
     * Tracks the actual item identity (rpg_gear_* or vanilla ID), not just slot index.
     * This catches weapon changes within the same slot (e.g., picking up a new sword
     * into the same hotbar slot).
     */
    private final Map<UUID, String> lastKnownWeaponIds = new ConcurrentHashMap<>();

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

        // Track weapon IDENTITY changes (catches item-within-slot swaps)
        // Use getActiveHotbarItem() — getItemInHand() returns tool item when _usingToolsItem is set
        com.hypixel.hytale.server.core.inventory.ItemStack currentWeapon = inventory.getActiveHotbarItem();
        String currentWeaponId = (currentWeapon != null && currentWeapon.getItem() != null)
            ? currentWeapon.getItem().getId() : null;
        String lastWeaponId = lastKnownWeaponIds.get(uuid);
        boolean weaponIdentityChanged = false;

        if (lastWeaponId == null && currentWeaponId != null) {
            lastKnownWeaponIds.put(uuid, currentWeaponId);
        } else if (lastWeaponId != null && !lastWeaponId.equals(currentWeaponId)) {
            if (currentWeaponId != null) {
                lastKnownWeaponIds.put(uuid, currentWeaponId);
            } else {
                lastKnownWeaponIds.remove(uuid);
            }
            weaponIdentityChanged = true;
            LOGGER.at(Level.FINE).log("Weapon identity change detected for player %s: %s -> %s",
                    uuid, lastWeaponId, currentWeaponId);
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

        if (hotbarChanged || utilityChanged || weaponIdentityChanged) {
            triggerRecalculation(playerRef, store, archetypeChunk, index, uuid,
                    weaponIdentityChanged, currentWeaponId);
        }
    }

    /** Window (nanos) within which a recent recalculation makes ours redundant. ~50ms = 1.5 ticks at 30 TPS. */
    private static final long RECALC_DEDUP_WINDOW_NANOS = 50_000_000L;

    /**
     * Triggers stat recalculation and applies updated stats to ECS.
     *
     * <p>If {@code EquipmentChangeListener} already recalculated stats within the same
     * tick window (detected via epoch), we skip the expensive pipeline — BUT only when
     * the weapon identity hasn't changed. During rapid hotbar scrolling, the
     * EquipmentChangeListener may have recalculated with an older weapon (race condition).
     * When weaponIdentityChanged is true, we ALWAYS recalculate to ensure the cached
     * ComputedStats reflect the actual current weapon.
     *
     * @param weaponIdentityChanged true if the weapon in the active hotbar slot changed
     *                              identity (different item, not just slot index)
     * @param currentWeaponId the current weapon's item definition ID (for post-recalc verification)
     */
    private void triggerRecalculation(
            @Nonnull PlayerRef playerRef,
            @Nonnull Store<EntityStore> store,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            int index,
            @Nonnull UUID uuid,
            boolean weaponIdentityChanged,
            @Nullable String currentWeaponId
    ) {
        // Lazy-init cached service — resolves once, never changes
        if (cachedAttributeService == null) {
            cachedAttributeService = ServiceRegistry.get(AttributeService.class).orElse(null);
            if (cachedAttributeService == null) {
                return;
            }
        }

        // Dedup optimization: skip recalculation if EquipmentChangeListener already ran
        // within the same tick window. HOWEVER, if the weapon identity changed, we MUST
        // always recalculate. The EquipmentChangeListener fires on container content changes
        // (item add/remove), not slot switches. During rapid scrolling, its recalculation
        // may have read an older weapon — the player has already scrolled past it by now.
        boolean alreadyRecalculated = !weaponIdentityChanged
                && cachedAttributeService.wasRecentlyRecalculated(uuid, RECALC_DEDUP_WINDOW_NANOS);

        if (!alreadyRecalculated) {
            ComputedStats newStats = cachedAttributeService.recalculateStats(uuid);
            if (newStats == null) {
                LOGGER.at(Level.WARNING).log("Failed to recalculate stats for %s after slot change", uuid);
                return;
            }

            // Post-recalc verification: ensure the weapon in ComputedStats matches the
            // live hotbar weapon. If they differ (race with another change during recalc),
            // recalculate again immediately. This is the self-healing mechanism.
            if (currentWeaponId != null) {
                String cachedWeaponId = newStats.getWeaponRawItemId();
                if (!currentWeaponId.equals(cachedWeaponId)) {
                    LOGGER.at(Level.INFO).log(
                            "Post-recalc weapon mismatch for %s: live=%s cached=%s — re-recalculating",
                            uuid, currentWeaponId, cachedWeaponId);
                    newStats = cachedAttributeService.recalculateStats(uuid);
                    if (newStats == null) {
                        LOGGER.at(Level.WARNING).log("Re-recalculation failed for %s", uuid);
                    }
                }
            }

            // Sync animation speed only when WE recalculated
            ServiceRegistry.get(AnimationSpeedSyncManager.class)
                    .ifPresent(m -> m.syncAnimationSpeed(uuid));
        } else {
            LOGGER.at(Level.FINE).log("Skipping redundant recalculation for %s — EquipmentChangeListener handled it", uuid);
        }

        // ALWAYS mark gear tooltips dirty — even when recalculation was skipped, the
        // active weapon identity changed and tooltip content (requirement colors, active
        // weapon highlight) needs updating on the next coordinator flush.
        ServiceRegistry.get(GearService.class).ifPresent(svc -> {
            if (svc instanceof GearManager mgr) {
                ItemSyncCoordinator coordinator = mgr.getSyncCoordinator();
                if (coordinator != null) {
                    coordinator.markEquipmentDirty(uuid);
                }
            }
        });

        LOGGER.at(Level.FINE).log("%s stats for %s after slot change",
                alreadyRecalculated ? "Skipped redundant recalc — marked dirty" : "Recalculated",
                uuid);
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
        lastKnownWeaponIds.remove(uuid);
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
