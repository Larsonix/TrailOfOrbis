package io.github.larsonix.trailoforbis.sanctum.ui;

import au.ellie.hyui.builders.HudBuilder;
import au.ellie.hyui.builders.HyUIHud;
import au.ellie.hyui.builders.LabelBuilder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.skilltree.SkillTreeManager;
import io.github.larsonix.trailoforbis.skilltree.model.SkillTreeData;
import io.github.larsonix.trailoforbis.ui.RPGStyles;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Builds the persistent skill point allocation HUD shown in the Skill Sanctum.
 *
 * <p>Displays a decorated container at the bottom-left of the screen with
 * three labels spaced evenly (space-evenly pattern via flex spacers):
 * <pre>
 *    Unallocated : #     Allocated : #     Total : #
 * </pre>
 *
 * <p>Updates are event-driven: {@link SkillPointHudManager} calls
 * {@code hud.triggerRefresh()} when nodes are allocated, deallocated, or respecced.
 * A slow failsafe refresh (every {@value SYNC_INTERVAL_MS}ms) catches admin commands
 * that bypass the event system.
 *
 * @see SkillPointHudManager
 */
public class SkillPointHud {

    // ═══════════════════════════════════════════════════════════════════
    // CONSTANTS - SYNC
    // ═══════════════════════════════════════════════════════════════════

    /** Failsafe sync interval for admin commands that bypass skill tree events. */
    private static final int SYNC_INTERVAL_MS = 5000;

    // ═══════════════════════════════════════════════════════════════════
    // CONSTANTS - LAYOUT
    // ═══════════════════════════════════════════════════════════════════

    private static final int CONTAINER_WIDTH = 400;
    private static final int CONTAINER_HEIGHT = 70;
    private static final int BOTTOM_MARGIN = 40;
    private static final int LEFT_MARGIN = 80;

    // ═══════════════════════════════════════════════════════════════════
    // ELEMENT IDS
    // ═══════════════════════════════════════════════════════════════════

    private static final String ID_UNALLOCATED = "sp-unallocated";
    private static final String ID_ALLOCATED = "sp-allocated";
    private static final String ID_TOTAL = "sp-total";

    // ═══════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates and shows the skill point HUD for a player in the sanctum.
     *
     * <p>The refresh callback reads current skill tree data and only pushes
     * updates when values actually change, avoiding redundant packets on
     * failsafe ticks.
     *
     * @param player           The player to show the HUD to
     * @param store            The entity store
     * @param skillTreeManager The skill tree manager for point queries
     * @return The created HUD instance
     */
    @Nonnull
    public static HyUIHud create(
            @Nonnull PlayerRef player,
            @Nonnull Store<EntityStore> store,
            @Nonnull SkillTreeManager skillTreeManager) {

        UUID playerId = player.getUuid();
        SkillTreeData data = skillTreeManager.getSkillTreeData(playerId);

        // Compute allocated from actual node costs (ground truth),
        // not totalPointsEarned - skillPoints which breaks under admin overrides.
        int unallocated = data.getSkillPoints();
        int allocated = skillTreeManager.calculateFullRespecCost(playerId);
        int total = unallocated + allocated;

        // Cached state to skip redundant updates on no-change ticks.
        int[] displayUnallocated = { unallocated };
        int[] displayAllocated = { allocated };
        int[] displayTotal = { total };

        String html = buildHtml(unallocated, allocated, total);

        return HudBuilder.hudForPlayer(player)
            .fromHtml(html)
            .withRefreshRate(SYNC_INTERVAL_MS)
            .onRefresh(hud -> {
                SkillTreeData current = skillTreeManager.getSkillTreeData(playerId);
                int curUnallocated = current.getSkillPoints();
                int curAllocated = skillTreeManager.calculateFullRespecCost(playerId);
                int curTotal = curUnallocated + curAllocated;

                boolean unallocatedChanged = curUnallocated != displayUnallocated[0];
                boolean allocatedChanged = curAllocated != displayAllocated[0];
                boolean totalChanged = curTotal != displayTotal[0];

                if (!unallocatedChanged && !allocatedChanged && !totalChanged) {
                    return;
                }

                if (unallocatedChanged) {
                    displayUnallocated[0] = curUnallocated;
                    hud.getById(ID_UNALLOCATED, LabelBuilder.class).ifPresent(label ->
                        label.withText("Unallocated : " + curUnallocated));
                }

                if (allocatedChanged) {
                    displayAllocated[0] = curAllocated;
                    hud.getById(ID_ALLOCATED, LabelBuilder.class).ifPresent(label ->
                        label.withText("Allocated : " + curAllocated));
                }

                if (totalChanged) {
                    displayTotal[0] = curTotal;
                    hud.getById(ID_TOTAL, LabelBuilder.class).ifPresent(label ->
                        label.withText("Total : " + curTotal));
                }
            })
            .show();
    }

    // ═══════════════════════════════════════════════════════════════════
    // HTML GENERATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Builds the HYUIML for the skill point HUD.
     *
     * <p>Layout: full-screen root with Bottom stacking, inner container positioned
     * at bottom-left via margins. Uses a compact {@code decorated-container} for the
     * vanilla Hytale container background with "Skill Points" title bar.
     */
    private static String buildHtml(int unallocated, int allocated, int total) {
        return """
            <div style="anchor-horizontal: 0; anchor-vertical: 0; layout-mode: Bottom;">
                <div style="layout-mode: Left; margin-bottom: %d; margin-left: %d;">
                    <div class="decorated-container" data-hyui-title="Skill Points"
                         style="anchor-width: %d; anchor-height: %d;">
                        <div class="container-contents">
                            <div style="layout-mode: Left; anchor-horizontal: 0; margin-top: -10;">
                                <div style="flex-weight: 1;"></div>
                                <p id="%s" style="font-size: 13; font-weight: bold; color: %s;">Unallocated : %d</p>
                                <div style="flex-weight: 1;"></div>
                                <p id="%s" style="font-size: 13; font-weight: bold; color: %s;">Allocated : %d</p>
                                <div style="flex-weight: 1;"></div>
                                <p id="%s" style="font-size: 13; font-weight: bold; color: %s;">Total : %d</p>
                                <div style="flex-weight: 1;"></div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
            """.formatted(
                BOTTOM_MARGIN, LEFT_MARGIN,
                CONTAINER_WIDTH, CONTAINER_HEIGHT,
                ID_UNALLOCATED, RPGStyles.POSITIVE, unallocated,
                ID_ALLOCATED, RPGStyles.TEXT_INFO, allocated,
                ID_TOTAL, RPGStyles.TEXT_PRIMARY, total);
    }

    private SkillPointHud() {}
}
