package io.github.larsonix.trailoforbis.ui.hud;

import au.ellie.hyui.builders.HudBuilder;
import au.ellie.hyui.builders.HyUIHud;
import au.ellie.hyui.builders.LabelBuilder;
import au.ellie.hyui.builders.ProgressBarBuilder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.leveling.api.LevelingService;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Builds the persistent XP bar HUD displayed above the hotbar.
 *
 * <p>Shows a thin progress bar representing XP progress toward the next level,
 * with a small level label. Positioned bottom-center above the hotbar to
 * complement Hytale's native health and stamina bars.
 *
 * <p>Updates are event-driven: the {@link XpBarHudManager} calls
 * {@code hud.triggerRefresh()} when XP or level changes, pushing a single
 * immediate packet. A slow failsafe refresh (every {@value SYNC_INTERVAL_MS}ms)
 * catches admin commands that bypass the event system.
 *
 * @see XpBarHudManager
 */
public class XpBarHud {

    // ═══════════════════════════════════════════════════════════════════
    // CONSTANTS - LAYOUT
    // ═══════════════════════════════════════════════════════════════════

    private static final int HUD_WIDTH = 700;
    private static final int BAR_HEIGHT = 10;
    private static final int BOTTOM_OFFSET = 5;

    // ═══════════════════════════════════════════════════════════════════
    // CONSTANTS - SYNC
    // ═══════════════════════════════════════════════════════════════════

    /** Failsafe sync interval for admin commands that bypass XP events. */
    private static final int SYNC_INTERVAL_MS = 5000;

    // ═══════════════════════════════════════════════════════════════════
    // CONSTANTS - COLORS
    // ═══════════════════════════════════════════════════════════════════

    private static final String COLOR_LEVEL_TEXT = "#FFFFFF";

    // ═══════════════════════════════════════════════════════════════════
    // ELEMENT IDS
    // ═══════════════════════════════════════════════════════════════════

    private static final String ID_LEVEL_LABEL = "xp-level-label";
    private static final String ID_PROGRESS_BAR = "xp-progress-bar";

    // ═══════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates and shows the XP bar HUD for a player.
     *
     * <p>The refresh callback snaps directly to the current XP values — no
     * interpolation. It only pushes updates when the level or progress actually
     * changed, avoiding redundant packets on failsafe ticks.
     *
     * @param player          The player to show the HUD to
     * @param store           The entity store
     * @param levelingService The leveling service for XP/level queries
     * @return The created HUD instance
     */
    @Nonnull
    public static HyUIHud create(
            @Nonnull PlayerRef player,
            @Nonnull Store<EntityStore> store,
            @Nonnull LevelingService levelingService) {

        UUID playerId = player.getUuid();
        int level = levelingService.getLevel(playerId);
        float progress = levelingService.getLevelProgress(playerId);

        // Cached state to skip redundant updates on no-change ticks.
        float[] displayProgress = { progress };
        int[] displayLevel = { level };

        String html = buildHtml(level, progress);

        return HudBuilder.hudForPlayer(player)
            .fromHtml(html)
            // NO withRefreshRate() — HyUI's ScheduledExecutorService runs on its own
            // thread and queues world.execute() tasks that race with world transitions.
            // cancel(false) in remove() doesn't prevent an already-running check from
            // queuing a stale safeAdd(), which then sends Set commands referencing
            // elements cleared by resetManagers() → client crash.
            // All updates are event-driven via XpBarHudManager.notifyXpChanged().
            .onRefresh(hud -> {
                int currentLevel = levelingService.getLevel(playerId);
                float currentProgress = levelingService.getLevelProgress(playerId);

                boolean levelChanged = currentLevel != displayLevel[0];
                boolean progressChanged = currentProgress != displayProgress[0];

                if (!levelChanged && !progressChanged) {
                    return;
                }

                if (levelChanged) {
                    displayLevel[0] = currentLevel;
                    hud.getById(ID_LEVEL_LABEL, LabelBuilder.class).ifPresent(label ->
                        label.withText("Lv." + currentLevel));
                }

                if (progressChanged || levelChanged) {
                    displayProgress[0] = currentProgress;
                    hud.getById(ID_PROGRESS_BAR, ProgressBarBuilder.class).ifPresent(bar ->
                        bar.withValue(currentProgress));
                }
            })
            .show();
    }

    // ═══════════════════════════════════════════════════════════════════
    // HTML GENERATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Builds the HYUIML for the XP bar HUD.
     */
    private static String buildHtml(int level, float progress) {
        // Root fills entire screen, layout-mode: Bottom stacks content from bottom edge.
        // Inner row has margin-bottom to push above the hotbar, layout-mode: Center
        // to horizontally center the fixed-width bar.
        // The bar container uses layout-mode: Full so the label overlays the progress bar.
        return """
            <div style="anchor-horizontal: 0; anchor-vertical: 0; layout-mode: Bottom;">
                <div style="anchor-horizontal: 0; layout-mode: Center; margin-bottom: %d;">
                    <div style="anchor-width: %d; anchor-height: %d; layout-mode: Full;">
                        <progress id="%s" value="%.2f" max="1.0"
                                  style="anchor-horizontal: 0; anchor-vertical: 0;">
                        </progress>
                        <div style="anchor-horizontal: 0; anchor-vertical: 0; layout-mode: MiddleCenter;">
                            <p id="%s" style="font-size: 11; color: %s;">Lv.%d</p>
                        </div>
                    </div>
                </div>
            </div>
            """.formatted(
                BOTTOM_OFFSET,
                HUD_WIDTH, BAR_HEIGHT,
                ID_PROGRESS_BAR, progress,
                ID_LEVEL_LABEL, COLOR_LEVEL_TEXT, level);
    }

    private XpBarHud() {}
}
