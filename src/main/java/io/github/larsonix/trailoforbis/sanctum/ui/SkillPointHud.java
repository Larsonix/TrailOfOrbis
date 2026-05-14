package io.github.larsonix.trailoforbis.sanctum.ui;

import au.ellie.hyui.builders.HudBuilder;
import au.ellie.hyui.builders.HyUIHud;
import au.ellie.hyui.builders.HyUIStyle;
import au.ellie.hyui.builders.LabelBuilder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.skilltree.SkillTreeManager;
import io.github.larsonix.trailoforbis.skilltree.model.SkillTreeData;
import io.github.larsonix.trailoforbis.ui.RPGStyles;
import io.github.larsonix.trailoforbis.ui.hud.HudRefreshHelper;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Builds the persistent skill point allocation HUD shown in the Skill Sanctum.
 *
 * <p>Displays a decorated container at the bottom-left of the screen with
 * four labels spaced evenly (space-evenly pattern via flex spacers):
 * <pre>
 *    Unallocated : #     Allocated : #     Total : #     Refunds : #
 * </pre>
 *
 * <p>The refund label dynamically changes color: yellow when points are available,
 * red when depleted (0 remaining).
 *
 * <p>Updates are event-driven: {@link SkillPointHudManager} calls
 * {@link HudRefreshHelper#safeRefreshWithToggle} when nodes are allocated,
 * deallocated, or respecced. No periodic timer — all visual updates go through
 * the centralized safe refresh path to prevent stale {@code Set} command crashes.
 *
 * @see SkillPointHudManager
 */
public class SkillPointHud {

    // ═══════════════════════════════════════════════════════════════════
    // CONSTANTS - LAYOUT
    // ═══════════════════════════════════════════════════════════════════

    private static final int CONTAINER_WIDTH = 460;
    private static final int CONTAINER_HEIGHT = 70;
    private static final int BOTTOM_MARGIN = 70;
    private static final int LEFT_MARGIN = 25;
    private static final float LABEL_FONT_SIZE = 13.0f;

    // ═══════════════════════════════════════════════════════════════════
    // ELEMENT IDS
    // ═══════════════════════════════════════════════════════════════════

    private static final String ID_UNALLOCATED = "sp-unallocated";
    private static final String ID_ALLOCATED = "sp-allocated";
    private static final String ID_TOTAL = "sp-total";
    private static final String ID_REFUNDS = "sp-refunds";

    // ═══════════════════════════════════════════════════════════════════
    // CONSTANTS - COLORS
    // ═══════════════════════════════════════════════════════════════════

    /** Yellow/gold — refund points available (limited resource, use wisely). */
    private static final String COLOR_REFUND_AVAILABLE = RPGStyles.TEXT_WARNING;
    /** Red — refund points depleted (can't deallocate). */
    private static final String COLOR_REFUND_DEPLETED = RPGStyles.NEGATIVE;

    // ═══════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates and shows the skill point HUD for a player in the sanctum.
     *
     * <p>The refresh callback reads current skill tree data and only pushes
     * updates when values actually change, avoiding redundant rerender packets.
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
        int refunds = data.getSkillRefundPoints();

        // Cached state to skip redundant updates on no-change ticks.
        int[] displayUnallocated = { unallocated };
        int[] displayAllocated = { allocated };
        int[] displayTotal = { total };
        int[] displayRefunds = { refunds };

        String html = buildHtml(unallocated, allocated, total, refunds);

        HyUIHud hud = HudBuilder.hudForPlayer(player)
            .fromHtml(html)
            .onRefresh(h -> {
                SkillTreeData current = skillTreeManager.getSkillTreeData(playerId);
                int curUnallocated = current.getSkillPoints();
                int curAllocated = skillTreeManager.calculateFullRespecCost(playerId);
                int curTotal = curUnallocated + curAllocated;
                int curRefunds = current.getSkillRefundPoints();

                boolean unallocatedChanged = curUnallocated != displayUnallocated[0];
                boolean allocatedChanged = curAllocated != displayAllocated[0];
                boolean totalChanged = curTotal != displayTotal[0];
                boolean refundsChanged = curRefunds != displayRefunds[0];

                if (!unallocatedChanged && !allocatedChanged && !totalChanged && !refundsChanged) {
                    return;
                }

                if (unallocatedChanged) {
                    displayUnallocated[0] = curUnallocated;
                    h.getById(ID_UNALLOCATED, LabelBuilder.class).ifPresent(label ->
                        label.withText("Unallocated : " + curUnallocated));
                }

                if (allocatedChanged) {
                    displayAllocated[0] = curAllocated;
                    h.getById(ID_ALLOCATED, LabelBuilder.class).ifPresent(label ->
                        label.withText("Allocated : " + curAllocated));
                }

                if (totalChanged) {
                    displayTotal[0] = curTotal;
                    h.getById(ID_TOTAL, LabelBuilder.class).ifPresent(label ->
                        label.withText("Total : " + curTotal));
                }

                if (refundsChanged) {
                    displayRefunds[0] = curRefunds;
                    h.getById(ID_REFUNDS, LabelBuilder.class).ifPresent(label -> {
                        label.withText("Refunds : " + curRefunds);
                        // Dynamic color: yellow when available, red when depleted.
                        // Must include fontSize — withStyle() replaces the entire style object,
                        // so omitting fontSize would lose the font-size: 13 from the HTML template.
                        String color = curRefunds > 0 ? COLOR_REFUND_AVAILABLE : COLOR_REFUND_DEPLETED;
                        label.withStyle(new HyUIStyle()
                            .setTextColor(color)
                            .setFontSize(LABEL_FONT_SIZE)
                            .setRenderBold(true));
                    });
                }
            })
            .show();

        // Deterministic name — prevents MCHUD accumulation across world transitions
        hud.name = "too-skill-points";
        return hud;
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
     *
     * <p>Four labels evenly spaced: Unallocated (green), Allocated (cyan),
     * Total (white), Refunds (yellow/red depending on availability).
     */
    private static String buildHtml(int unallocated, int allocated, int total, int refunds) {
        String refundColor = refunds > 0 ? COLOR_REFUND_AVAILABLE : COLOR_REFUND_DEPLETED;
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
                                <p id="%s" style="font-size: 13; font-weight: bold; color: %s;">Refunds : %d</p>
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
                ID_TOTAL, RPGStyles.TEXT_PRIMARY, total,
                ID_REFUNDS, refundColor, refunds);
    }

    private SkillPointHud() {}
}
