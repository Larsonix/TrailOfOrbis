package io.github.larsonix.trailoforbis.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.api.services.UIService;
import io.github.larsonix.trailoforbis.stones.StoneType;
import io.github.larsonix.trailoforbis.stones.ui.ContainerType;
import io.github.larsonix.trailoforbis.stones.ui.StoneApplicationService;
import io.github.larsonix.trailoforbis.stones.ui.StonePickerPage;
import io.github.larsonix.trailoforbis.ui.attributes.AttributePage;
import io.github.larsonix.trailoforbis.ui.stats.StatsPage;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages UI pages for players.
 *
 * <p>Uses HyUI for all pages (PageBuilder with HYUIML).
 */
public class UIManager implements UIService {
    private final TrailOfOrbis plugin;
    private final Map<UUID, String> activePages = new ConcurrentHashMap<>();

    public UIManager(TrailOfOrbis plugin) {
        this.plugin = plugin;
    }

    /**
     * Opens the attribute allocation page for a player.
     *
     * @param player The player to open the page for
     * @param store The entity store
     * @param ref The entity reference (unused - kept for interface compatibility)
     */
    @Override
    public void openAttributePage(
        @Nonnull PlayerRef player,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref
    ) {
        UUID playerId = player.getUuid();

        // Close any existing page
        closePage(playerId);

        // Create and open new HyUI-based page
        AttributePage page = new AttributePage(plugin, player);
        page.open(store);
        trackOpenPage(playerId, "attributes");
    }

    /**
     * Opens the character stats page for a player.
     *
     * @param player The player to open the page for
     * @param store The entity store
     * @param ref The entity reference (unused - kept for interface compatibility)
     */
    @Override
    public void openStatsPage(
        @Nonnull PlayerRef player,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref
    ) {
        UUID playerId = player.getUuid();

        // Close any existing page
        closePage(playerId);

        // Create and open new HyUI-based page
        StatsPage page = new StatsPage(plugin, player);
        page.open(store);
        trackOpenPage(playerId, "stats");
    }

    // ═══════════════════════════════════════════════════════════════════
    // LOOT FILTER PAGE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Opens the loot filter management page for a player.
     *
     * @param player The player to open the page for
     * @param store The entity store
     */
    @Override
    public void openLootFilterPage(
            @Nonnull PlayerRef player,
            @Nonnull Store<EntityStore> store) {
        UUID playerId = player.getUuid();
        closePage(playerId);

        if (!io.github.larsonix.trailoforbis.lootfilter.bridge.VuetaleIntegration.isInitialized()) {
            plugin.getLogger().atWarning().log("Cannot open loot filter page - Vuetale not initialized");
            return;
        }

        com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) return;

        io.github.larsonix.trailoforbis.lootfilter.bridge.VuetaleIntegration.openLootFilterPage(
                player, ref, store);
        trackOpenPage(playerId, "loot_filter");
    }

    // ═══════════════════════════════════════════════════════════════════
    // STONE PICKER PAGES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Opens the stone picker page for a player.
     *
     * @param player The player to open the page for
     * @param store The entity store
     * @param ref The entity reference (unused - kept for interface compatibility)
     * @param stoneType The type of stone being used
     * @param stoneItem The stone item stack
     * @param stoneSlot The slot containing the stone
     * @param stoneContainer Which container the stone is in
     */
    public void openStonePicker(
            @Nonnull PlayerRef player,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull StoneType stoneType,
            @Nonnull ItemStack stoneItem,
            short stoneSlot,
            @Nonnull ContainerType stoneContainer) {

        UUID playerId = player.getUuid();
        closePage(playerId);

        StoneApplicationService applicationService = plugin.getStoneApplicationService();
        if (applicationService == null) {
            plugin.getLogger().atWarning().log("Cannot open stone picker - StoneApplicationService not available");
            return;
        }

        StonePickerPage page = new StonePickerPage(
            plugin, player, stoneType, stoneItem, stoneSlot, stoneContainer, applicationService
        );
        page.open(store);
        trackOpenPage(playerId, "stone_picker");
    }

    // ═══════════════════════════════════════════════════════════════════
    // PAGE LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Closes any active page for the player.
     */
    @Override
    public void closePage(@Nonnull UUID playerId) {
        activePages.remove(playerId);
    }

    /**
     * Tracks that a player has an open page.
     */
    public void trackOpenPage(@Nonnull UUID playerId, @Nonnull String pageType) {
        activePages.put(playerId, pageType);
    }

    /**
     * Gets the type of page currently open for a player.
     */
    @Override
    public String getOpenPageType(@Nonnull UUID playerId) {
        return activePages.get(playerId);
    }

    /**
     * Cleans up when player disconnects.
     */
    @Override
    public void onPlayerDisconnect(@Nonnull UUID playerId) {
        closePage(playerId);
    }

    /**
     * Shuts down the UI manager, clearing all active page tracking.
     */
    public void shutdown() {
        activePages.clear();
    }

    /**
     * Gets the plugin instance.
     */
    @Nonnull
    public TrailOfOrbis getPlugin() {
        return plugin;
    }
}
