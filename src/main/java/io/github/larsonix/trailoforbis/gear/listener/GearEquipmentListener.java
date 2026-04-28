package io.github.larsonix.trailoforbis.gear.listener;

import io.github.larsonix.trailoforbis.gear.item.ItemSyncService;
import io.github.larsonix.trailoforbis.gear.stats.GearStatApplier;
import io.github.larsonix.trailoforbis.gear.stats.GearStatCalculator;
import io.github.larsonix.trailoforbis.gear.sync.ItemSyncCoordinator;
import io.github.larsonix.trailoforbis.gear.util.EquipmentSectionIds;
import io.github.larsonix.trailoforbis.gear.util.GearUtils;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Listens for equipment changes and triggers stat recalculation.
 *
 * <p>This listener monitors changes to:
 * <ul>
 *   <li>Armor slots</li>
 *   <li>Weapon (active hotbar slot)</li>
 *   <li>Utility slot</li>
 * </ul>
 *
 * <p>When equipment changes, it triggers {@link GearStatApplier} to
 * recalculate and apply the player's gear bonuses.
 *
 * <p>Implements debouncing to avoid excessive recalculations when
 * multiple changes happen in quick succession (e.g., equipping a full set).
 */
public final class GearEquipmentListener {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /**
     * Debounce delay in milliseconds.
     * Multiple equipment changes within this window are batched.
     */
    private static final long DEBOUNCE_DELAY_MS = 100;

    // Equipment section IDs
    private static final int SECTION_HOTBAR = EquipmentSectionIds.HOTBAR;
    private static final int SECTION_ARMOR = EquipmentSectionIds.ARMOR;
    private static final int SECTION_UTILITY = EquipmentSectionIds.UTILITY;

    private final GearStatCalculator statCalculator;
    private final GearStatApplier statApplier;
    private final ItemSyncService itemSyncService;
    private final ItemSyncCoordinator syncCoordinator;

    // Scheduler for debounced recalculations
    private final ScheduledExecutorService scheduler;

    // Debounce tracking: player UUID -> scheduled task
    private final Map<UUID, ScheduledFuture<?>> pendingTasks = new ConcurrentHashMap<>();

    // Player references for scheduled callbacks
    private final Map<UUID, Player> playerReferences = new ConcurrentHashMap<>();

    // World references for dispatching back to the correct thread
    private final Map<UUID, World> playerWorlds = new ConcurrentHashMap<>();

    // Flag to enable/disable debouncing
    private volatile boolean debounceEnabled = true;

    // Flag to track if listener is shutting down
    private volatile boolean shuttingDown = false;

    /**
     * Creates a GearEquipmentListener.
     *
     * @param statCalculator The stat calculator
     * @param statApplier The stat applier
     */
    public GearEquipmentListener(
            @Nonnull GearStatCalculator statCalculator,
            @Nonnull GearStatApplier statApplier) {
        this(statCalculator, statApplier, null, null);
    }

    /**
     * Creates a GearEquipmentListener with item sync support.
     *
     * @param statCalculator The stat calculator
     * @param statApplier The stat applier
     * @param itemSyncService The item sync service (may be null)
     */
    public GearEquipmentListener(
            @Nonnull GearStatCalculator statCalculator,
            @Nonnull GearStatApplier statApplier,
            @Nullable ItemSyncService itemSyncService) {
        this(statCalculator, statApplier, itemSyncService, null);
    }

    /**
     * Creates a GearEquipmentListener with sync coordinator support.
     *
     * @param statCalculator  The stat calculator
     * @param statApplier     The stat applier
     * @param itemSyncService The item sync service (may be null, unused when coordinator present)
     * @param syncCoordinator The sync coordinator (may be null for backward compat)
     */
    public GearEquipmentListener(
            @Nonnull GearStatCalculator statCalculator,
            @Nonnull GearStatApplier statApplier,
            @Nullable ItemSyncService itemSyncService,
            @Nullable ItemSyncCoordinator syncCoordinator) {
        this.statCalculator = Objects.requireNonNull(statCalculator, "statCalculator cannot be null");
        this.statApplier = Objects.requireNonNull(statApplier, "statApplier cannot be null");
        this.itemSyncService = itemSyncService;
        this.syncCoordinator = syncCoordinator;

        // Create a single-threaded scheduler for debounced recalculations
        // Using daemon thread so it doesn't prevent JVM shutdown
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "GearEquipment-Debounce");
            t.setDaemon(true);
            return t;
        });
    }

    // =========================================================================
    // REGISTRATION
    // =========================================================================

    /**
     * Registers the equipment change listener.
     *
     * <p>Call this from the main plugin class.
     *
     * @param registry The event registry
     */
    public void register(@Nonnull EventRegistry registry) {
        Objects.requireNonNull(registry, "registry cannot be null");

        // Note: Inventory change listener is now registered via InventoryChangeEventSystem.
        // Call addHandler() on the system instead of registerGlobal().
        LOGGER.at(Level.INFO).log("GearEquipmentListener registered");
    }

    // =========================================================================
    // EVENT HANDLING
    // =========================================================================

    /**
     * Handles inventory change events.
     */
    public void onInventoryChange(@Nonnull Player player, @Nonnull InventoryChangeEvent event) {
        // Check if this is an equipment-related container
        if (!isEquipmentContainer(player, event.getItemContainer())) {
            return;
        }

        // Get PlayerRef via ECS component lookup (future-proof pattern)
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        // Capture World reference for thread-safe dispatch
        World world = store.getExternalData().getWorld();

        UUID playerId = playerRef.getUuid();

        LOGGER.atFine().log("Equipment change detected for player %s", playerId);

        // Trigger stat recalculation (with debouncing)
        if (debounceEnabled) {
            scheduleRecalculation(playerId, player, world);
        } else {
            executeRecalculation(playerId, player);
        }
    }

    /**
     * Checks if a container is an equipment container.
     */
    private boolean isEquipmentContainer(Player player, ItemContainer changedContainer) {
        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return false;
        }

        // Check if it's armor, hotbar, or utility
        return changedContainer == inventory.getArmor()
                || changedContainer == inventory.getHotbar()
                || changedContainer == inventory.getUtility();
    }

    // =========================================================================
    // STAT RECALCULATION
    // =========================================================================

    /**
     * Schedules a stat recalculation with debouncing.
     *
     * <p>When multiple equipment changes occur within {@link #DEBOUNCE_DELAY_MS},
     * only one recalculation is performed after the delay expires. This prevents
     * excessive recalculations when equipping a full armor set.
     *
     * @param playerId The player's UUID
     * @param player The player entity
     * @param world The world for thread dispatch (required for ECS component access)
     */
    private void scheduleRecalculation(UUID playerId, Player player, World world) {
        if (shuttingDown) {
            return;
        }

        // Store player and world references for callback
        playerReferences.put(playerId, player);
        playerWorlds.put(playerId, world);

        // Cancel any existing pending task for this player
        ScheduledFuture<?> existingTask = pendingTasks.get(playerId);
        if (existingTask != null && !existingTask.isDone()) {
            existingTask.cancel(false);
            LOGGER.atFine().log("Cancelled existing recalculation task for player %s", playerId);
        }

        // Schedule new task after debounce delay
        ScheduledFuture<?> newTask = scheduler.schedule(
                () -> executeScheduledRecalculation(playerId),
                DEBOUNCE_DELAY_MS,
                TimeUnit.MILLISECONDS
        );

        pendingTasks.put(playerId, newTask);
        LOGGER.atFine().log("Scheduled recalculation for player %s in %dms", playerId, DEBOUNCE_DELAY_MS);
    }

    /**
     * Executes a scheduled recalculation.
     *
     * <p>Called by the scheduler after the debounce delay expires.
     * Dispatches to the World thread to ensure thread-safe ECS component access.
     */
    private void executeScheduledRecalculation(UUID playerId) {
        if (shuttingDown) {
            return;
        }

        // Get and remove player/world references
        Player player = playerReferences.remove(playerId);
        World world = playerWorlds.remove(playerId);
        pendingTasks.remove(playerId);

        if (player == null || world == null) {
            LOGGER.atFine().log("Player %s no longer available for recalculation", playerId);
            return;
        }

        // CRITICAL FIX: Dispatch to World thread for ECS component access
        // Store.getComponent() has thread assertions that require the World thread.
        // Without this dispatch, executeRecalculation() throws IllegalStateException.
        world.execute(() -> executeRecalculation(playerId, player));
    }

    /**
     * Executes stat recalculation for a player.
     */
    void executeRecalculation(UUID playerId, Player player) {
        try {
            Inventory inventory = player.getInventory();
            if (inventory == null) {
                return;
            }

            // Calculate bonuses
            GearStatCalculator.GearBonuses bonuses = statCalculator.calculateBonuses(playerId, inventory);

            // Note: Actual application requires ComputedStats from the player
            // This is handled by the GearStatApplier when the player's stats are recalculated
            LOGGER.atFine().log("Recalculated gear stats for player %s: %d flat, %d percent bonuses",
                    playerId, bonuses.flatBonuses().size(), bonuses.percentBonuses().size());

            // Mark equipment dirty — coordinator coalesces into one flush
            if (syncCoordinator != null) {
                syncCoordinator.markEquipmentDirty(playerId);
            } else if (itemSyncService != null) {
                // Fallback: direct sync if no coordinator (backward compat)
                Ref<EntityStore> ref = player.getReference();
                if (ref != null && ref.isValid()) {
                    Store<EntityStore> store = ref.getStore();
                    PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
                    if (playerRef != null) {
                        syncEquippedItems(playerRef, inventory);
                    }
                }
            }


        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to recalculate gear stats for player %s", playerId);
        }
    }

    /**
     * Syncs equipped gear items to the player.
     *
     * <p>Ensures the player has up-to-date item definitions for all equipped gear.
     */
    private void syncEquippedItems(@Nonnull PlayerRef playerRef, @Nonnull Inventory inventory) {
        try {
            // Collect all equipment items
            List<ItemStack> equippedItems = new ArrayList<>();

            // Armor slots
            if (inventory.getArmor() != null) {
                for (short i = 0; i < inventory.getArmor().getCapacity(); i++) {
                    ItemStack item = inventory.getArmor().getItemStack(i);
                    if (item != null && GearUtils.isRpgGear(item)) {
                        equippedItems.add(item);
                    }
                }
            }

            // Active hotbar item
            ItemStack activeItem = inventory.getActiveHotbarItem();
            if (activeItem != null && GearUtils.isRpgGear(activeItem)) {
                equippedItems.add(activeItem);
            }

            // Utility slot
            if (inventory.getUtility() != null) {
                for (short i = 0; i < inventory.getUtility().getCapacity(); i++) {
                    ItemStack item = inventory.getUtility().getItemStack(i);
                    if (item != null && GearUtils.isRpgGear(item)) {
                        equippedItems.add(item);
                    }
                }
            }

            // Sync all equipped items
            if (!equippedItems.isEmpty()) {
                int synced = itemSyncService.syncAllItems(playerRef, equippedItems);
                if (synced > 0) {
                    LOGGER.atFine().log("Synced %d equipped gear items to player %s",
                            synced, playerRef.getUuid());
                }
            }
        } catch (Exception e) {
            LOGGER.atFine().withCause(e).log("Failed to sync equipped items");
        }
    }

    /**
     * Forces immediate stat recalculation for a player.
     *
     * <p>Use this after programmatic equipment changes or attribute respec.
     *
     * @param playerId The player's UUID
     * @param player The player entity
     */
    public void forceRecalculation(@Nonnull UUID playerId, @Nonnull Player player) {
        Objects.requireNonNull(playerId, "playerId cannot be null");
        Objects.requireNonNull(player, "player cannot be null");

        executeRecalculation(playerId, player);
    }

    /**
     * Forces recalculation using PlayerRef.
     *
     * @param playerRef The player reference
     * @param player The player entity
     */
    public void forceRecalculation(@Nonnull PlayerRef playerRef, @Nonnull Player player) {
        Objects.requireNonNull(playerRef, "playerRef cannot be null");
        forceRecalculation(playerRef.getUuid(), player);
    }

    // =========================================================================
    // CONFIGURATION
    // =========================================================================

    /**
     * Enables or disables debouncing.
     *
     * <p>Disabling debouncing causes immediate recalculation on every change.
     *
     * @param enabled true to enable debouncing, false to disable
     */
    public void setDebounceEnabled(boolean enabled) {
        this.debounceEnabled = enabled;
    }

    /**
     * Checks if debouncing is enabled.
     *
     * @return true if debouncing is enabled
     */
    public boolean isDebounceEnabled() {
        return debounceEnabled;
    }

    // =========================================================================
    // CLEANUP
    // =========================================================================

    /**
     * Shuts down the listener and clears all pending recalculations.
     *
     * <p>Call during plugin shutdown. This will:
     * <ul>
     *   <li>Cancel all pending tasks</li>
     *   <li>Shut down the scheduler</li>
     *   <li>Clear all state maps</li>
     * </ul>
     */
    public void shutdown() {
        shuttingDown = true;

        // Cancel all pending tasks
        for (ScheduledFuture<?> task : pendingTasks.values()) {
            task.cancel(false);
        }
        pendingTasks.clear();
        playerReferences.clear();
        playerWorlds.clear();

        // Shut down scheduler gracefully
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        LOGGER.at(Level.INFO).log("GearEquipmentListener shut down");
    }

    /**
     * Clears all pending recalculations.
     *
     * <p>Call during shutdown.
     *
     * @deprecated Use {@link #shutdown()} instead for proper cleanup
     */
    @Deprecated
    public void clearPending() {
        // Cancel all pending tasks
        for (ScheduledFuture<?> task : pendingTasks.values()) {
            task.cancel(false);
        }
        pendingTasks.clear();
        playerReferences.clear();
        playerWorlds.clear();
    }

    /**
     * Removes a player from pending recalculations.
     *
     * <p>Call when a player disconnects.
     *
     * @param playerId The player's UUID
     */
    public void removePlayer(@Nonnull UUID playerId) {
        ScheduledFuture<?> task = pendingTasks.remove(playerId);
        if (task != null) {
            task.cancel(false);
        }
        playerReferences.remove(playerId);
        playerWorlds.remove(playerId);
    }
}
