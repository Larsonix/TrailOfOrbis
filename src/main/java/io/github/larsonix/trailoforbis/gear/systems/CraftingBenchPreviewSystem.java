package io.github.larsonix.trailoforbis.gear.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.window.WindowType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.Window;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.inventory.InventoryChangeEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.gear.GearManager;
import io.github.larsonix.trailoforbis.gear.GearService;
import io.github.larsonix.trailoforbis.gear.tooltip.CraftingPreviewService;
import io.github.larsonix.trailoforbis.leveling.api.LevelingService;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Hybrid system that activates crafting preview tooltips using two triggers:
 *
 * <h2>1. UseBlockEvent.Pre (ECS — definitions before page)</h2>
 * <p>Fires when a player clicks a bench block, BEFORE the bench page opens.
 * {@code UseBlockInteraction.doInteraction()} dispatches Pre synchronously,
 * then calls {@code context.execute(rootInteraction)} which sends page/window
 * packets via {@code PageManager.setPageWithWindows()}. By handling Pre,
 * our {@code UpdateItems} + {@code UpdateTranslations} packets enter the
 * output buffer BEFORE the page packets — so the client has our modified
 * definitions when it renders recipe outputs.
 *
 * <p>Using Post instead causes BasicCrafting benches (Workbench) to render
 * recipes with vanilla definitions because the page packet arrives first.
 * DiagramCrafting benches (Armory) were unaffected because they render
 * recipes lazily on category click.
 *
 * <h2>2. InventoryChangeEvent (handler — close callback + safety)</h2>
 * <p>Fires when the player first interacts with items inside the bench.
 * The window now exists — we register {@code window.registerCloseEvent()}
 * to restore vanilla definitions when the bench closes.
 *
 * <p>This two-phase approach is necessary because Hytale's
 * {@code pageManager.setPageWithWindows()} defers window creation via the
 * command buffer — the window isn't materialized until after the ECS tick
 * completes.
 *
 * @see CraftingPreviewService
 */
public class CraftingBenchPreviewSystem extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Block type ID prefix for all crafting bench blocks. */
    private static final String BENCH_BLOCK_PREFIX = "Bench_";

    /** Window types that display craftable weapon/armor recipes. */
    private static final Set<WindowType> CRAFTING_WINDOW_TYPES = EnumSet.of(
            WindowType.BasicCrafting,
            WindowType.DiagramCrafting,
            WindowType.Processing,
            WindowType.StructuralCrafting,
            WindowType.PocketCrafting
    );

    public CraftingBenchPreviewSystem() {
        super(UseBlockEvent.Pre.class);
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(Player.getComponentType(), PlayerRef.getComponentType());
    }

    // ═══════════════════════════════════════════════════════════════════
    // PHASE 1: UseBlockEvent.Pre — definitions before bench page
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull UseBlockEvent.Pre event) {

        // Skip if another handler already cancelled (e.g. container interceptor)
        if (event.isCancelled()) return;

        // Filter: only bench blocks
        String blockId = event.getBlockType().getId();
        if (blockId == null || !blockId.startsWith(BENCH_BLOCK_PREFIX)) return;

        Player player = chunk.getComponent(index, Player.getComponentType());
        PlayerRef playerRef = chunk.getComponent(index, PlayerRef.getComponentType());
        if (player == null || playerRef == null) return;

        UUID playerId = playerRef.getUuid();

        CraftingPreviewService previewService = getCraftingPreviewService();
        if (previewService == null || !previewService.isInitialized()) return;

        // Already has an active session — skip
        if (previewService.isActiveBenchSession(playerId)) return;

        int playerLevel = ServiceRegistry.get(LevelingService.class)
                .map(ls -> ls.getLevel(playerId))
                .orElse(1);

        // Send definitions + translations in Pre — packets enter the output buffer
        // BEFORE the page/window packets from context.execute(rootInteraction),
        // ensuring the client has our definitions when it renders recipe outputs.
        boolean sent = previewService.onBenchOpen(playerId, playerRef, playerLevel);
        if (sent) {
            LOGGER.atFine().log("Crafting preview sent for %s on bench open (block=%s)",
                    playerId.toString().substring(0, 8), blockId);
        }
        // Close callback is registered in Phase 2 (InventoryChangeEvent)
        // when the window becomes available.
    }

    // ═══════════════════════════════════════════════════════════════════
    // PHASE 2: InventoryChangeEvent — close callback + safety cleanup
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Called by {@code InventoryChangeEventSystem} on every inventory change.
     * Handles two responsibilities:
     * <ol>
     *   <li>Registers the window close callback if the session was opened by Phase 1
     *       but the callback hasn't been registered yet (window now exists).</li>
     *   <li>Safety cleanup: if no crafting window is open but a session exists,
     *       restores vanilla definitions (close event didn't fire).</li>
     * </ol>
     */
    public void onInventoryChange(@Nonnull Player player, @Nonnull InventoryChangeEvent event) {
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) return;

        Store<EntityStore> store = ref.getStore();
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) return;

        UUID playerId = playerRef.getUuid();

        CraftingPreviewService previewService = getCraftingPreviewService();
        if (previewService == null || !previewService.isInitialized()) return;

        Window craftingWindow = findCraftingWindow(player);

        if (craftingWindow != null) {
            // Window is open — register close callback if not done yet
            if (previewService.needsCloseCallback(playerId)) {
                craftingWindow.registerCloseEvent(closeEvent ->
                        previewService.onBenchClose(playerId, playerRef));
                previewService.markCloseCallbackRegistered(playerId);

                LOGGER.atFine().log("Close callback registered for %s (window=%s)",
                        playerId.toString().substring(0, 8), craftingWindow.getType());
            }

            // If session wasn't created by Phase 1 (e.g., PocketCrafting which
            // may not go through UseBlockEvent), create it now
            if (!previewService.isActiveBenchSession(playerId)) {
                int playerLevel = ServiceRegistry.get(LevelingService.class)
                        .map(ls -> ls.getLevel(playerId))
                        .orElse(1);

                boolean sent = previewService.onBenchOpen(playerId, playerRef, playerLevel);
                if (sent) {
                    craftingWindow.registerCloseEvent(closeEvent ->
                            previewService.onBenchClose(playerId, playerRef));
                    previewService.markCloseCallbackRegistered(playerId);
                }
            }
        } else {
            // No crafting window — safety cleanup if we had an active session
            if (previewService.isActiveBenchSession(playerId)) {
                previewService.onBenchClose(playerId, playerRef);
                LOGGER.atFine().log("Safety cleanup: bench session closed for %s (no window)",
                        playerId.toString().substring(0, 8));
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // UTILITIES
    // ═══════════════════════════════════════════════════════════════════

    @Nullable
    private static Window findCraftingWindow(@Nonnull Player player) {
        List<Window> windows = player.getWindowManager().getWindows();
        for (Window w : windows) {
            if (CRAFTING_WINDOW_TYPES.contains(w.getType())) {
                return w;
            }
        }
        return null;
    }

    @Nullable
    private static CraftingPreviewService getCraftingPreviewService() {
        return ServiceRegistry.get(GearService.class)
                .filter(svc -> svc instanceof GearManager)
                .map(svc -> ((GearManager) svc).getCraftingPreviewService())
                .orElse(null);
    }
}
