package io.github.larsonix.trailoforbis.gear.systems;

import com.hypixel.hytale.builtin.crafting.window.FieldCraftingWindow;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.gear.GearManager;
import io.github.larsonix.trailoforbis.gear.GearService;
import io.github.larsonix.trailoforbis.gear.tooltip.CraftingPreviewService;
import io.github.larsonix.trailoforbis.leveling.api.LevelingService;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Wraps Hytale's {@link FieldCraftingWindow} (Pocket Crafting) to inject
 * RPG crafting preview definitions before the window renders on the client.
 *
 * <p>Pocket Crafting is opened by the client sending a {@code ClientOpenWindow}
 * packet — no block is clicked, so {@code UseBlockEvent} never fires and
 * {@link CraftingBenchPreviewSystem}'s Phase 1 is completely bypassed. This
 * wrapper intercepts {@link #onOpen0} (called after {@code init(playerRef)}
 * but before the {@code UpdateWindow} packet is sent) to send our modified
 * item definitions and per-player translations. The client receives them
 * before the window data, ensuring recipe outputs render with RPG tooltips.
 *
 * <p>Registered via {@code Window.CLIENT_REQUESTABLE_WINDOW_TYPES.put()} in
 * {@link io.github.larsonix.trailoforbis.gear.conversion.GearConversionManager}.
 *
 * @see CraftingPreviewService#onBenchOpen(UUID, PlayerRef, int)
 * @see CraftingPreviewService#onBenchClose(UUID, PlayerRef)
 */
public class PocketCraftingPreviewWindow extends FieldCraftingWindow {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @Override
    public boolean onOpen0(Ref<EntityStore> ref, Store<EntityStore> store) {
        boolean result = super.onOpen0(ref, store);
        if (!result) return false;

        PlayerRef playerRef = this.getPlayerRef();
        if (playerRef == null) return true;

        UUID playerId = playerRef.getUuid();

        CraftingPreviewService previewService = getCraftingPreviewService();
        if (previewService == null || !previewService.isInitialized()) return true;
        if (previewService.isActiveBenchSession(playerId)) return true;

        int playerLevel = ServiceRegistry.get(LevelingService.class)
                .map(ls -> ls.getLevel(playerId))
                .orElse(1);

        boolean sent = previewService.onBenchOpen(playerId, playerRef, playerLevel);
        if (sent) {
            // Mark close callback as handled — our onClose0() manages cleanup directly,
            // so Phase 2 (CraftingBenchPreviewSystem.onInventoryChange) won't register
            // a redundant registerCloseEvent callback.
            previewService.markCloseCallbackRegistered(playerId);
            LOGGER.atFine().log("Pocket crafting preview sent for %s (level=%d)",
                    playerId.toString().substring(0, 8), playerLevel);
        }

        return true;
    }

    @Override
    public void onClose0(Ref<EntityStore> ref, ComponentAccessor<EntityStore> componentAccessor) {
        PlayerRef playerRef = this.getPlayerRef();
        if (playerRef != null) {
            UUID playerId = playerRef.getUuid();
            CraftingPreviewService previewService = getCraftingPreviewService();
            if (previewService != null) {
                previewService.onBenchClose(playerId, playerRef);
            }
        }
        super.onClose0(ref, componentAccessor);
    }

    @Nullable
    private static CraftingPreviewService getCraftingPreviewService() {
        return ServiceRegistry.get(GearService.class)
                .filter(svc -> svc instanceof GearManager)
                .map(svc -> ((GearManager) svc).getCraftingPreviewService())
                .orElse(null);
    }
}
