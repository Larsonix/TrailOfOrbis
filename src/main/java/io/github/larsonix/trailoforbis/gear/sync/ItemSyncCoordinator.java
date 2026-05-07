package io.github.larsonix.trailoforbis.gear.sync;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;

import io.github.larsonix.trailoforbis.gear.GearManager;
import io.github.larsonix.trailoforbis.gear.item.ItemSyncService;
import io.github.larsonix.trailoforbis.gear.tooltip.CraftingPreviewService;
import io.github.larsonix.trailoforbis.gear.util.GearUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Central coordinator for all item synchronization.
 *
 * <p>Replaces scattered immediate-sync calls with a <b>dirty-flag + coalesced flush</b>
 * system. Instead of syncing items the moment any event fires, callers mark items dirty.
 * A scheduled flush (100ms delay) coalesces all pending changes into a single minimal
 * packet burst.
 *
 * <h2>Three Levels of Dirtiness</h2>
 * <ol>
 *   <li><b>Per-item</b> ({@code dirtyGearItemIds}): A specific item changed (stone applied,
 *       pickup). Only that item syncs.</li>
 *   <li><b>All-gear</b> ({@code allGearDirty}): Stats changed. Flush evaluates ALL gear
 *       hashes — only items whose hash actually differs get sent.</li>
 *   <li><b>Suppressed</b> ({@code suppressedPlayers}): Mid-world-transition. Dirty marks
 *       accumulate but no flush fires until unsuppressed.</li>
 * </ol>
 *
 * <h2>Key Insight: Preserve the Hash Cache</h2>
 * <p>The existing hash dedup in {@code PlayerItemCache.needsUpdate()} already skips unchanged
 * items. The critical change is <b>never clearing the cache</b> during re-syncs — letting the
 * hash comparison naturally filter out items whose tooltip didn't actually change. This alone
 * eliminates ~60% of redundant syncs.
 *
 * <h2>World Transition Suppression</h2>
 * <p>During world transitions ({@code DrainPlayerFromWorldEvent} → new world join), all
 * dirty-marking continues but no flush fires. When the player is ready in the new world,
 * a single flush processes everything accumulated during the transition.
 *
 * @see ItemSyncService
 */
public class ItemSyncCoordinator {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // ═══════════════════════════════════════════════════════════════════
    // CONSTANTS
    // ═══════════════════════════════════════════════════════════════════

    /** Flush delay for normal dirty events (equipment change, stat change). 3 ticks at 30 TPS. */
    private static final long FLUSH_DELAY_MS = 100;

    /** Flush delay after world join. Slightly longer to let stats settle. 6 ticks at 30 TPS. */
    private static final long JOIN_FLUSH_DELAY_MS = 200;

    // ═══════════════════════════════════════════════════════════════════
    // DEPENDENCIES
    // ═══════════════════════════════════════════════════════════════════

    private final ItemSyncService itemSyncService;
    @Nullable private CraftingPreviewService craftingPreviewService;

    // ═══════════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════════

    /** Per-player dirty tracking and references. */
    private final Map<UUID, PlayerSyncState> playerStates = new ConcurrentHashMap<>();

    /** Scheduled flush tasks per player — at most one pending per player. */
    private final Map<UUID, ScheduledFuture<?>> pendingFlushes = new ConcurrentHashMap<>();

    /** Players currently mid-world-transition. Dirty marks accumulate but no flush fires. */
    private final Set<UUID> suppressedPlayers = ConcurrentHashMap.newKeySet();

    /** Players in the Skill Sanctum. Flushes suppressed to prevent node visual corruption. */
    private final Set<UUID> sanctumPlayers = ConcurrentHashMap.newKeySet();

    /**
     * Callback invoked when a sanctum player's equipment changes (hotbar scroll, etc.).
     * Hytale's {@code invalidateEquipmentNetwork()} corrupts nearby item entity visuals
     * (scale, model, light) on the client. This callback triggers an immediate resync
     * of sanctum node visual components, correcting the corruption in the same tick.
     */
    @Nullable private Consumer<UUID> sanctumResyncCallback;

    /** Single daemon thread for scheduling delayed flushes. */
    private final ScheduledExecutorService flushScheduler;

    /** Shutdown flag. */
    private volatile boolean shuttingDown = false;

    // ═══════════════════════════════════════════════════════════════════
    // INNER CLASS
    // ═════════════════════════════════════════════════════════════���═════

    /**
     * Per-player sync state. Tracks which items are dirty and holds references
     * needed for flushing (player ref, world, inventory access).
     */
    private static final class PlayerSyncState {
        /** Specific gear item IDs that need resync. */
        final Set<String> dirtyGearItemIds = ConcurrentHashMap.newKeySet();

        /** When true, ALL gear items need hash re-evaluation (stats changed). */
        volatile boolean allGearDirty = false;

        /** Player references for packet sending. Updated on each world join. */
        volatile PlayerRef playerRef;
        volatile Player player;
        volatile World world;

        /** True after RPGItemPreSyncSystem has run the initial mandatory sync. */
        volatile boolean preSyncComplete = false;

        /**
         * True while a flush is executing on the world thread.
         * <p>
         * Prevents the sync-equipment-stats feedback loop: our UpdateItems packet
         * triggers Hytale equipment events which call markStatsDirty, scheduling
         * another flush. With this guard, feedback dirty-marks accumulate in the
         * state but don't schedule new flushes. The next real player action
         * (equipment swap, level up, realm entry) triggers a normal flush that
         * picks up any accumulated state.
         */
        volatile boolean currentlyFlushing = false;

        /** When true, crafting preview translations need re-send (level changed while bench open). */
        volatile boolean craftingPreviewDirty = false;

        boolean hasPendingDirty() {
            return allGearDirty || !dirtyGearItemIds.isEmpty() || craftingPreviewDirty;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════════

    public ItemSyncCoordinator(@Nonnull ItemSyncService itemSyncService) {
        this.itemSyncService = Objects.requireNonNull(itemSyncService);
        this.flushScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ItemSyncCoordinator-Flush");
            t.setDaemon(true);
            return t;
        });
        LOGGER.atInfo().log("ItemSyncCoordinator initialized");
    }

    public void setCraftingPreviewService(@Nullable CraftingPreviewService service) {
        this.craftingPreviewService = service;
    }

    // ═══════════════════════════════════════════════════════════════════
    // DIRTY-MARKING API (called by event handlers — no packets sent)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Mark a specific gear item as needing resync.
     * Used when a single item changes (stone applied, modifier changed).
     */
    public void markItemDirty(@Nonnull UUID playerId, @Nonnull String itemId) {
        PlayerSyncState state = playerStates.get(playerId);
        if (state == null) return;

        state.dirtyGearItemIds.add(itemId);
        scheduleFlush(playerId);
    }

    /**
     * Mark all gear as potentially dirty because player stats changed.
     * <p>
     * The flush will re-sync ALL gear items because the statsVersion component
     * of the definition hash changes with every stat recalculation. This is a
     * known trade-off: true per-item selectivity would require content-level
     * comparison instead of statsVersion-based hashing. The win here is
     * coalescing: multiple stat changes within 100ms produce ONE sync batch.
     */
    public void markStatsDirty(@Nonnull UUID playerId) {
        PlayerSyncState state = playerStates.get(playerId);
        if (state == null) return;

        state.allGearDirty = true;
        scheduleFlush(playerId);

        LOGGER.atFine().log("[DIAG] markStatsDirty called for %s (caller: %s)",
            playerId.toString().substring(0, 8),
            Thread.currentThread().getStackTrace().length > 3
                ? Thread.currentThread().getStackTrace()[2].getClassName() + "." + Thread.currentThread().getStackTrace()[2].getMethodName()
                : "unknown");
    }

    /**
     * Mark equipped items as dirty after an equipment change.
     * <p>
     * Convenience method — marks all-gear-dirty since equipment changes
     * trigger stat recalculation which changes tooltips on other items too.
     */
    public void markEquipmentDirty(@Nonnull UUID playerId) {
        markStatsDirty(playerId);

        // Reactive sanctum resync: Hytale's invalidateEquipmentNetwork() (from hotbar
        // scroll) corrupts nearby item entity visuals (scale, model, light). The callback
        // triggers an immediate resync of sanctum node components in the same tick,
        // so the entity tracker sends corrected values before the client renders corruption.
        if (sanctumPlayers.contains(playerId) && sanctumResyncCallback != null) {
            sanctumResyncCallback.accept(playerId);
        }
    }

    /**
     * Sets the callback invoked when a sanctum player's equipment changes.
     * Called by {@code SkillSanctumManager} during initialization.
     */
    public void setSanctumResyncCallback(@Nullable Consumer<UUID> callback) {
        this.sanctumResyncCallback = callback;
    }

    /**
     * Mark crafting preview as needing a translation re-send.
     * Used when the player levels up while a crafting bench is open.
     * The next flush will update translations with the new level.
     */
    public void markCraftingPreviewDirty(@Nonnull UUID playerId) {
        PlayerSyncState state = playerStates.get(playerId);
        if (state == null) return;
        state.craftingPreviewDirty = true;
        scheduleFlush(playerId);
    }

    // ═══════════════════════════════════════════════════════════════════
    // WORLD TRANSITION SUPPRESSION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Check if a player is currently suppressed (mid-world-transition).
     * <p>
     * Used by {@code RPGItemPreSyncSystem} to skip sending UpdateItems packets
     * during world transitions. Sending packets while the client is processing
     * JoinWorldPacket causes a NullReferenceException crash.
     */
    public boolean isPlayerSuppressed(@Nonnull UUID playerId) {
        return suppressedPlayers.contains(playerId) || sanctumPlayers.contains(playerId);
    }

    /**
     * Suppress all flushes for a player during world transition.
     * <p>
     * Call on {@code DrainPlayerFromWorldEvent}. Dirty marks continue to accumulate
     * but no flush fires until {@link #onPlayerReady} is called in the new world.
     */
    public void suppressPlayer(@Nonnull UUID playerId) {
        suppressedPlayers.add(playerId);

        // Cancel any pending flush — it would fail anyway (world invalid)
        ScheduledFuture<?> pending = pendingFlushes.remove(playerId);
        if (pending != null) {
            pending.cancel(false);
        }

        // Clear server-side caches. During world transitions the CLIENT's item definition
        // and translation registries are wiped (JoinWorld reset). Without clearing our
        // tracking, the post-transition flush thinks items were "already sent" (hash match)
        // and skips them — leaving RPG items with no definitions on the client.
        itemSyncService.getPlayerCache().clearPlayerItems(playerId);
        itemSyncService.getTranslationService().clearPlayerTranslations(playerId);

        // Clear crafting preview bench session — client wipes all definitions on JoinWorld.
        // No packets sent (client is transitioning, would crash on stale selectors).
        if (craftingPreviewService != null) {
            craftingPreviewService.onWorldTransition(playerId);
        }

        LOGGER.atFine().log("Suppressed sync for player %s (world transition, caches cleared)",
            playerId.toString().substring(0, 8));
    }

    /**
     * Safety unsuppress for early portal suppression. If the player never stepped
     * through the portal (walked away), this restores normal sync. Marks all gear
     * dirty and schedules a catch-up flush.
     */
    public void safetyUnsuppress(@Nonnull UUID playerId) {
        if (!suppressedPlayers.remove(playerId)) return;
        PlayerSyncState state = playerStates.get(playerId);
        if (state != null) state.allGearDirty = true;
        scheduleFlush(playerId);
    }

    // ═══════════════════════════════════════════════════════════════════
    // SANCTUM SUPPRESSION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Suppress flushes while in the Skill Sanctum. Dirty marks accumulate;
     * one flush on exit catches up all tooltip changes.
     * <p>
     * A periodic keep-alive in {@code SkillSanctumInstance} handles any
     * non-UpdateItems corruption from Hytale's engine as a safety net.
     */
    public void suppressForSanctum(@Nonnull UUID playerId) {
        sanctumPlayers.add(playerId);
        ScheduledFuture<?> pending = pendingFlushes.remove(playerId);
        if (pending != null) pending.cancel(false);
    }

    /**
     * Unsuppress on sanctum exit. Marks all gear dirty and schedules a catch-up flush.
     */
    public void unsuppressFromSanctum(@Nonnull UUID playerId) {
        if (!sanctumPlayers.remove(playerId)) return;
        PlayerSyncState state = playerStates.get(playerId);
        if (state != null) state.allGearDirty = true;
        scheduleFlush(playerId);
    }

    // ═══════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Called by {@code RPGItemPreSyncSystem} after the mandatory pre-inventory sync.
     * <p>
     * Marks the initial sync as complete so the coordinator knows the player's
     * items are already synced. The subsequent {@link #onPlayerReady} flush will
     * only resync items whose hash changed (requirement colors after stat application).
     */
    public void notifyPreSyncComplete(@Nonnull UUID playerId) {
        PlayerSyncState state = playerStates.computeIfAbsent(playerId, k -> new PlayerSyncState());
        state.preSyncComplete = true;
        LOGGER.atFine().log("Pre-sync complete for player %s", playerId.toString().substring(0, 8));
    }

    /**
     * Called when the player is fully ready in a world (after stats are applied).
     * <p>
     * This is the SINGLE entry point for post-join sync. It:
     * <ol>
     *   <li>Updates player references for the new world</li>
     *   <li>Marks all gear dirty (stats just changed → tooltip colors need refresh)</li>
     *   <li>Unsuppresses the player (if suppressed during world transition)</li>
     *   <li>Schedules a flush with a slightly longer delay (stats settling)</li>
     * </ol>
     */
    public void onPlayerReady(
            @Nonnull UUID playerId,
            @Nonnull PlayerRef playerRef,
            @Nonnull Player player,
            @Nonnull World world) {

        PlayerSyncState state = playerStates.computeIfAbsent(playerId, k -> new PlayerSyncState());
        state.playerRef = playerRef;
        state.player = player;
        state.world = world;

        // Stats were just applied — requirement colors in tooltips may have changed.
        // Custom items (maps, gems) are already covered by RPGItemPreSyncSystem's
        // pre-inventory sync and don't need coordinator involvement.
        state.allGearDirty = true;

        // DO NOT unsuppress immediately or even on next tick. The player must remain
        // suppressed until well after RPGItemPreSyncSystem.onEntityAdded() has fired
        // (during addToStore in onFinishPlayerJoining). The ECS system fires at
        // unpredictable times relative to world.execute() tasks due to CompletableFuture
        // chain ordering. If unsuppressed too early, the system sends UpdateItems packets
        // while the client is still processing JoinWorld → NullReferenceException.
        //
        // Instead: schedule a TIMER-based unsuppress + flush that bypasses the
        // scheduleFlush() suppression check. This guarantees the player stays suppressed
        // for the full JOIN_FLUSH_DELAY_MS, then unsuppresses and flushes in one atomic step.
        ScheduledFuture<?> existing = pendingFlushes.get(playerId);
        if (existing != null && !existing.isDone()) {
            existing.cancel(false);
        }

        ScheduledFuture<?> task = flushScheduler.schedule(() -> {
            // Skip flush if player entered sanctum during the delay
            if (sanctumPlayers.contains(playerId)) {
                suppressedPlayers.remove(playerId);
                return;
            }

            suppressedPlayers.remove(playerId);
            pendingFlushes.remove(playerId);

            // Dispatch RPG gear flush to the world thread.
            // Crafting preview is now window-scoped (sent on bench open via
            // CraftingBenchPreviewSystem) — no longer sent on join.
            World w = state.world;
            if (w != null && w.isAlive()) {
                w.execute(() -> {
                    try {
                        flushOnWorldThread(playerId, state);
                    } catch (Exception e) {
                        LOGGER.atWarning().withCause(e).log(
                            "Post-join sync failed for player %s", playerId.toString().substring(0, 8));
                    }
                });
            }

            LOGGER.atFine().log("Player %s ready — unsuppressed + flushed after join delay",
                playerId.toString().substring(0, 8));
        }, JOIN_FLUSH_DELAY_MS, TimeUnit.MILLISECONDS);
        pendingFlushes.put(playerId, task);
    }

    /**
     * Clean up all state for a disconnecting player.
     */
    public void onPlayerDisconnect(@Nonnull UUID playerId) {
        playerStates.remove(playerId);
        suppressedPlayers.remove(playerId);
        sanctumPlayers.remove(playerId);

        ScheduledFuture<?> pending = pendingFlushes.remove(playerId);
        if (pending != null) {
            pending.cancel(false);
        }

        // Clean up crafting preview state (no packets — client is gone)
        if (craftingPreviewService != null) {
            craftingPreviewService.onPlayerDisconnect(playerId);
        }

        LOGGER.atFine().log("Cleaned up coordinator state for player %s",
            playerId.toString().substring(0, 8));
    }

    /**
     * Graceful shutdown. Cancels all pending flushes and shuts down the scheduler.
     */
    public void shutdown() {
        shuttingDown = true;

        // Cancel all pending flushes
        for (ScheduledFuture<?> task : pendingFlushes.values()) {
            task.cancel(false);
        }
        pendingFlushes.clear();
        playerStates.clear();
        suppressedPlayers.clear();
        sanctumPlayers.clear();

        flushScheduler.shutdown();
        try {
            if (!flushScheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                flushScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            flushScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        LOGGER.atInfo().log("ItemSyncCoordinator shut down");
    }

    // ═══════════════════════════════════════════════════════════════════
    // FLUSH SCHEDULING (internal)
    // ═══════════════════════════════════════════════════════════════════

    private void scheduleFlush(@Nonnull UUID playerId) {
        scheduleFlush(playerId, FLUSH_DELAY_MS);
    }

    private void scheduleFlush(@Nonnull UUID playerId, long delayMs) {
        if (shuttingDown || suppressedPlayers.contains(playerId) || sanctumPlayers.contains(playerId)) {
            return;
        }

        // Guard against sync-equipment-stats feedback loop.
        // When a flush sends UpdateItems, Hytale fires equipment events synchronously
        // on the same world thread, which call markEquipmentDirty/markStatsDirty.
        // Without this guard, one flush cascades into 11+ cycles (observed in production,
        // 1903 client warnings, NullReferenceException crash on realm transition).
        // Dirty marks during the flush accumulate in the state — they're echoes of our
        // own sync, not real player actions. The next real trigger picks them up.
        PlayerSyncState state = playerStates.get(playerId);
        if (state != null && state.currentlyFlushing) {
            return;
        }

        // Cancel existing pending flush — the new one subsumes it
        ScheduledFuture<?> existing = pendingFlushes.get(playerId);
        if (existing != null && !existing.isDone()) {
            existing.cancel(false);
        }

        ScheduledFuture<?> task = flushScheduler.schedule(
            () -> executeFlush(playerId),
            delayMs, TimeUnit.MILLISECONDS
        );
        pendingFlushes.put(playerId, task);
    }

    /**
     * Dispatches the flush to the world thread. Called by the scheduler thread.
     */
    private void executeFlush(@Nonnull UUID playerId) {
        if (shuttingDown) return;

        pendingFlushes.remove(playerId);

        PlayerSyncState state = playerStates.get(playerId);
        if (state == null) return;

        World world = state.world;
        if (world == null || !world.isAlive()) return;

        // Dispatch to world thread — ECS access and packet sending require it
        world.execute(() -> {
            try {
                flushOnWorldThread(playerId, state);
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Flush failed for player %s",
                    playerId.toString().substring(0, 8));
            }
        });
    }

    /**
     * Performs the actual sync flush on the world thread.
     * <p>
     * Snapshots dirty state, clears it, then syncs only items whose hash
     * actually changed. The hash dedup in {@code ItemSyncService.syncAllItems()}
     * handles the filtering — items with unchanged hashes produce zero packets.
     */
    private void flushOnWorldThread(@Nonnull UUID playerId, @Nonnull PlayerSyncState state) {
        // Raise the flushing guard BEFORE any sync operations.
        // syncAllItems() → updateItems() triggers Hytale equipment events synchronously
        // on this same world thread. Those events call markStatsDirty → scheduleFlush,
        // which checks this flag and returns early — breaking the feedback loop.
        state.currentlyFlushing = true;
        try {
            // Snapshot and clear dirty state
            boolean gearDirty = state.allGearDirty;
            state.allGearDirty = false;

            Set<String> dirtyGear = Set.copyOf(state.dirtyGearItemIds);
            state.dirtyGearItemIds.clear();

            // Nothing dirty after snapshot? Skip.
            if (!gearDirty && dirtyGear.isEmpty()) {
                return;
            }

            PlayerRef playerRef = state.playerRef;
            Player player = state.player;
            if (playerRef == null || player == null) return;

            // Validate entity is still alive
            var ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) return;

            @SuppressWarnings("deprecation")
            Inventory inventory = player.getInventory();
            if (inventory == null) return;

            List<ItemStack> allItems = GearManager.collectAllInventoryItems(inventory);
            int totalSynced = 0;

            // ── Gear sync ──
            if (gearDirty) {
                // Stats changed — evaluate ALL gear. Hash dedup skips unchanged items.
                totalSynced += itemSyncService.syncAllItems(playerRef, allItems);
            } else if (!dirtyGear.isEmpty()) {
                // Only specific items changed — filter to dirty set
                List<ItemStack> dirtyItems = new ArrayList<>();
                for (ItemStack item : allItems) {
                    Optional<io.github.larsonix.trailoforbis.gear.model.GearData> gearData =
                        GearUtils.readGearData(item);
                    if (gearData.isPresent() && dirtyGear.contains(gearData.get().getItemId())) {
                        dirtyItems.add(item);
                    }
                }
                if (!dirtyItems.isEmpty()) {
                    totalSynced += itemSyncService.syncAllItems(playerRef, dirtyItems);
                }
            }

            if (totalSynced > 0) {
                LOGGER.atFine().log("Flushed %d gear items for player %s [%s]",
                    totalSynced, playerId.toString().substring(0, 8),
                    gearDirty ? "ALL (stats changed)" : dirtyGear.size() + " specific");
            }

            // ── Crafting preview sync (level-up while bench open) ──
            if (state.craftingPreviewDirty && craftingPreviewService != null
                    && craftingPreviewService.isInitialized()
                    && craftingPreviewService.isActiveBenchSession(playerId)
                    && !craftingPreviewService.isReskinActive(playerId)) {
                state.craftingPreviewDirty = false;
                if (playerRef != null && playerRef.isValid()) {
                    int level = io.github.larsonix.trailoforbis.api.ServiceRegistry
                            .get(io.github.larsonix.trailoforbis.leveling.api.LevelingService.class)
                            .map(ls -> ls.getLevel(playerId))
                            .orElse(1);
                    craftingPreviewService.updateTranslationsForActiveSession(playerId, playerRef, level);
                }
            } else if (state.craftingPreviewDirty) {
                state.craftingPreviewDirty = false; // Clear without sending (no bench open or reskin active)
            }
        } finally {
            // ALWAYS clear — even on exception — so the player isn't permanently blocked.
            state.currentlyFlushing = false;
        }
    }
}
