package io.github.larsonix.trailoforbis.ui.hud;

import au.ellie.hyui.builders.HyUIHud;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.UUID;

/**
 * Centralized safe HUD refresh utility.
 *
 * <p>HyUI's {@code refreshOrRerender(false, false)} generates diff-based {@code Set}
 * commands targeting auto-generated {@code #HYUUIDGroupNNN} element selectors. These
 * selectors are ephemeral — if the client's DOM is rebuilt (by MultipleHUD, world
 * transitions, or any mod adding/removing a HUD), the selectors become stale and
 * the client crashes: {@code "Selected element in CustomUI command was not found"}.
 *
 * <p>The safe alternative is {@code resetHasBuilt()} + {@code refreshOrRerender(true, true)},
 * which atomically Clears the HUD group and Appends fresh elements — no dependency
 * on stale element IDs. However, {@code resetHasBuilt()} requires reflection into
 * HyUI's private {@code hasBuilt} field.
 *
 * <p>This helper centralizes that reflection, makes it resilient to HyUI library
 * updates, logs its status on startup, and provides a safe fallback when reflection
 * is unavailable (skip visual update rather than crash the client).
 *
 * <h2>Usage</h2>
 * <pre>
 * // Simple refresh (e.g., SkillPointHud)
 * HudRefreshHelper.safeRefresh(hud);
 *
 * // Refresh with toggle state reapplication (e.g., XP bar, shield bar, combat HUD)
 * HudRefreshHelper.safeRefreshWithToggle(hud, playerId, hudToggleService);
 * </pre>
 */
public final class HudRefreshHelper {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // ═══════════════════════════════════════════════════════════════════
    // REFLECTION — reset hasBuilt to force Append instead of Set
    // ═══════════════════════════════════════════════════════════════════

    private static final Field DELEGATE_FIELD;
    private static final Field HAS_BUILT_FIELD;
    private static final boolean REFLECTION_AVAILABLE;

    static {
        Field df = null, hf = null;

        // Primary path: known field names, derive delegate type dynamically.
        // Using df.getType() instead of Class.forName("au.ellie.hyui.builders.HyUInterface")
        // makes this resilient to delegate class renames — only the field names need to match.
        try {
            df = HyUIHud.class.getDeclaredField("delegate");
            df.setAccessible(true);
            hf = df.getType().getDeclaredField("hasBuilt");
            hf.setAccessible(true);
        } catch (Exception e) {
            LOGGER.atWarning().log(
                "HyUI primary reflection failed (%s: %s), attempting field-scan fallback...",
                e.getClass().getSimpleName(), e.getMessage());
            df = null;
            hf = null;

            // Fallback: scan HyUIHud fields for a delegate-like type from the hyui package,
            // then scan that delegate for a boolean field named "hasBuilt" or similar.
            try {
                for (Field f : HyUIHud.class.getDeclaredFields()) {
                    if (!f.getType().getName().startsWith("au.ellie.hyui")) {
                        continue;
                    }
                    f.setAccessible(true);
                    for (Field bf : f.getType().getDeclaredFields()) {
                        if (bf.getType() == boolean.class
                                && bf.getName().toLowerCase().contains("built")) {
                            bf.setAccessible(true);
                            df = f;
                            hf = bf;
                            break;
                        }
                    }
                    if (hf != null) break;
                }
            } catch (Exception fallbackEx) {
                LOGGER.atWarning().log("HyUI field-scan fallback also failed: %s",
                    fallbackEx.getMessage());
            }
        }

        DELEGATE_FIELD = df;
        HAS_BUILT_FIELD = hf;
        REFLECTION_AVAILABLE = (df != null && hf != null);

        if (REFLECTION_AVAILABLE) {
            LOGGER.atInfo().log("HUD refresh reflection ready (delegate=%s, hasBuilt=%s.%s)",
                df.getType().getSimpleName(),
                hf.getDeclaringClass().getSimpleName(), hf.getName());
        } else {
            LOGGER.atSevere().log(
                "HUD REFRESH REFLECTION UNAVAILABLE — all HUD refreshes will skip visual " +
                "updates to prevent client crashes. Combat timer, XP bar, and shield bar " +
                "will be visually stale until the next world transition. " +
                "Check if the HyUI library was updated and field names changed.");
        }
    }

    private HudRefreshHelper() {}

    // ═══════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Safely refreshes a HUD without risking stale {@code Set} commands.
     *
     * <p>When reflection is available: resets {@code hasBuilt}, then performs a
     * full rerender (atomic Clear+Append). When unavailable: runs the refresh
     * callback to keep internal state current, but skips the visual push.
     * Data corrects itself on next fresh HUD creation (world transition).
     *
     * @param hud The HUD to refresh
     */
    public static void safeRefresh(@Nonnull HyUIHud hud) {
        hud.triggerRefresh();
        if (resetHasBuilt(hud)) {
            hud.refreshOrRerender(true, true);
        }
    }

    /**
     * Safely refreshes a HUD, then reapplies toggle visibility state.
     *
     * <p>Full rerenders reset element visibility, so the toggle must be
     * reapplied afterward. When reflection fails, no rerender occurs and
     * the existing visibility state is preserved (no toggle reapplication needed).
     *
     * @param hud           The HUD to refresh
     * @param playerId      The player's UUID
     * @param toggleService The toggle service (nullable — skipped if null)
     * @return {@code true} if a full rerender was performed
     */
    public static boolean safeRefreshWithToggle(
            @Nonnull HyUIHud hud,
            @Nonnull UUID playerId,
            @Nullable HudToggleService toggleService) {
        hud.triggerRefresh();
        if (resetHasBuilt(hud)) {
            hud.refreshOrRerender(true, true);
            if (toggleService != null) {
                toggleService.applyToggleState(playerId, hud);
            }
            return true;
        }
        return false;
    }

    /**
     * @return {@code true} if reflection-based safe HUD refresh is available
     */
    public static boolean isAvailable() {
        return REFLECTION_AVAILABLE;
    }

    // ═══════════════════════════════════════════════════════════════════
    // INTERNAL
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Resets HyUI's internal {@code hasBuilt} flag so the next build generates
     * {@code Append} commands instead of {@code Set} commands.
     *
     * @return {@code true} if the reset succeeded
     */
    private static boolean resetHasBuilt(@Nonnull HyUIHud hud) {
        if (!REFLECTION_AVAILABLE) {
            return false;
        }
        try {
            Object delegate = DELEGATE_FIELD.get(hud);
            HAS_BUILT_FIELD.set(delegate, false);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
