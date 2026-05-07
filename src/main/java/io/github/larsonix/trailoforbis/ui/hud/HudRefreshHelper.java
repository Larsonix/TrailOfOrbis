package io.github.larsonix.trailoforbis.ui.hud;

import au.ellie.hyui.builders.HyUIHud;
import au.ellie.hyui.builders.UIElementBuilder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

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

    // ═══════════════════════════════════════════════════════════════════
    // REFLECTION — access delegate.getElements() for safe visibility
    // ═══════════════════════════════════════════════════════════════════

    private static final Method GET_ELEMENTS_METHOD;
    private static final boolean VISIBILITY_AVAILABLE;

    // ═══════════════════════════════════════════════════════════════════
    // REFLECTION — cancel refresh task without hud.remove()
    // ═══════════════════════════════════════════════════════════════════

    private static final Field REFRESH_TASK_FIELD;
    private static final boolean REFRESH_CANCEL_AVAILABLE;

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

        // Element visibility — delegate.getElements() for safe hide/unhide
        // hide()/unhide() send raw Set commands with stale selectors → client crash.
        // Instead, we set visibility on the root element and do a full rerender.
        Method gem = null;
        if (REFLECTION_AVAILABLE) {
            try {
                gem = df.getType().getMethod("getElements");
            } catch (Exception e) {
                LOGGER.atWarning().log("HyUI getElements reflection failed: %s", e.getMessage());
            }
        }
        GET_ELEMENTS_METHOD = gem;
        VISIBILITY_AVAILABLE = (gem != null);

        if (VISIBILITY_AVAILABLE) {
            LOGGER.atInfo().log("HUD safe visibility ready via delegate.getElements()");
        } else {
            LOGGER.atWarning().log("HUD safe visibility unavailable — " +
                "/hud toggle will use fallback (skip rather than crash)");
        }

        // Refresh task cancellation — HyUI's hud.remove() early-returns when
        // getStore() returns null during world transitions, skipping refreshTask.cancel().
        // Direct field access lets us cancel the orphaned task reliably.
        Field rt = null;
        try {
            rt = HyUIHud.class.getDeclaredField("refreshTask");
            rt.setAccessible(true);
        } catch (Exception e) {
            // Fallback: scan for ScheduledFuture field on HyUIHud
            try {
                for (Field f : HyUIHud.class.getDeclaredFields()) {
                    if (ScheduledFuture.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        rt = f;
                        break;
                    }
                }
            } catch (Exception ignored) {}
        }
        REFRESH_TASK_FIELD = rt;
        REFRESH_CANCEL_AVAILABLE = (rt != null);

        if (REFRESH_CANCEL_AVAILABLE) {
            LOGGER.atInfo().log("HUD refresh task cancellation ready (field=%s)",
                rt.getName());
        } else {
            LOGGER.atWarning().log("HUD refresh task cancellation unavailable — " +
                "orphaned tasks may accumulate during world transitions");
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
     * <p>Skips the refresh entirely if the player's entity reference is
     * unreachable (transient during world transitions). This prevents mutating
     * HUD state when the rerender can't be delivered, which would leave the
     * HUD invisible.
     *
     * @param hud The HUD to refresh
     */
    public static void safeRefresh(@Nonnull HyUIHud hud) {
        if (!canReachPlayer(hud)) {
            return;
        }
        hud.triggerRefresh();
        if (resetHasBuilt(hud)) {
            hud.refreshOrRerender(true, true);
        }
    }

    /**
     * Safely refreshes a HUD with toggle visibility baked into the rerender.
     *
     * <p>Visibility is set on the root element BEFORE the rerender, so the
     * Clear+Append packet contains the correct visibility state. This avoids
     * a separate {@code hide()} call which would send raw {@code Set} commands
     * with stale selectors — the root cause of client crashes.
     *
     * <p>Skips the refresh entirely if the player's entity reference is
     * unreachable (transient during world transitions).
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
        if (!canReachPlayer(hud)) {
            return false;
        }
        hud.triggerRefresh();
        if (resetHasBuilt(hud)) {
            // Bake visibility BEFORE rerender — atomic, no separate hide() packet.
            // hide()/unhide() send raw Set commands with stale #HYUUIDxxx selectors
            // that bypass MultiHud's prefixing → client crash.
            if (toggleService != null && toggleService.isHidden(playerId)) {
                setFirstElementVisibility(hud, false);
            }
            hud.refreshOrRerender(true, true);
            return true;
        }
        return false;
    }

    /**
     * Safely sets visibility on a HUD via full rerender.
     *
     * <p>This is the safe replacement for {@code hud.hide()} and {@code hud.unhide()}.
     * Those methods send raw diff-based {@code Set} commands with auto-generated
     * {@code #HYUUIDxxx} selectors that bypass MultiHud's prefixing. When any HUD
     * has been added/removed/rerendered via MultiHud, those selectors are stale and
     * the client crashes: {@code "Selected element in CustomUI command was not found"}.
     *
     * <p>Instead, this method sets visibility on the root element and performs a full
     * rerender through MultiHud (Clear+Append with properly prefixed selectors).
     *
     * @param hud     The HUD to show or hide
     * @param visible {@code true} to show, {@code false} to hide
     * @return {@code true} if the operation succeeded
     */
    public static boolean safeSetVisibility(@Nonnull HyUIHud hud, boolean visible) {
        if (!canReachPlayer(hud)) {
            return false;
        }
        if (!setFirstElementVisibility(hud, visible)) {
            return false;
        }
        if (resetHasBuilt(hud)) {
            hud.refreshOrRerender(true, true);
            return true;
        }
        return false;
    }

    /**
     * Cancels a HUD's refresh task without calling {@code hud.remove()}.
     *
     * <p>HyUI's {@code remove()} early-returns when {@code getStore()} returns null
     * during world transitions — both the deferred {@code hideCustomHud} and
     * {@code refreshTask.cancel()} are skipped. This leaves orphaned 100ms timer
     * tasks that accumulate across transitions (one per HUD per transition).
     *
     * <p>This method accesses the {@code refreshTask} field directly via reflection,
     * reliably cancelling it regardless of entity store state.
     *
     * @param hud The HUD whose refresh task should be cancelled
     * @return {@code true} if the task was cancelled or was already inactive
     */
    public static boolean cancelRefreshTask(@Nonnull HyUIHud hud) {
        if (!REFRESH_CANCEL_AVAILABLE) {
            return false;
        }
        try {
            ScheduledFuture<?> task = (ScheduledFuture<?>) REFRESH_TASK_FIELD.get(hud);
            if (task != null && !task.isCancelled()) {
                task.cancel(false);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
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

    /**
     * Checks whether the HUD's player can be reached for packet delivery.
     *
     * <p>During world transitions, {@code playerRef.getReference()} returns null
     * transiently. Attempting a refresh in this state either silently fails
     * (leaving the HUD invisible) or sends stale packets. Skip entirely and
     * let the next successful call or HudLifecycleManager restore handle it.
     */
    private static boolean canReachPlayer(@Nonnull HyUIHud hud) {
        PlayerRef playerRef = hud.getPlayerRef();
        if (!playerRef.isValid()) {
            return false;
        }
        Ref<?> entityRef = playerRef.getReference();
        return entityRef != null && entityRef.isValid();
    }

    /**
     * Sets visibility on the first (root) element of a HUD via reflection.
     *
     * <p>Accesses {@code delegate.getElements()} and calls {@code withVisible()}
     * on the first element. This must be followed by a full rerender to take effect.
     *
     * @return {@code true} if visibility was set successfully
     */
    @SuppressWarnings("unchecked")
    private static boolean setFirstElementVisibility(@Nonnull HyUIHud hud, boolean visible) {
        if (!VISIBILITY_AVAILABLE) {
            return false;
        }
        try {
            Object delegate = DELEGATE_FIELD.get(hud);
            List<UIElementBuilder<?>> elements =
                (List<UIElementBuilder<?>>) GET_ELEMENTS_METHOD.invoke(delegate);
            if (elements.isEmpty()) {
                return false;
            }
            elements.get(0).withVisible(visible);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
