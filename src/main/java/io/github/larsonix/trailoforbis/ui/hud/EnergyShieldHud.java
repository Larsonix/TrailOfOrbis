package io.github.larsonix.trailoforbis.ui.hud;

import au.ellie.hyui.builders.GroupBuilder;
import au.ellie.hyui.builders.HudBuilder;
import au.ellie.hyui.builders.HyUIAnchor;
import au.ellie.hyui.builders.HyUIHud;
import au.ellie.hyui.builders.HyUIPatchStyle;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import io.github.larsonix.trailoforbis.combat.EnergyShieldTracker;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Combined health + energy shield bar HUD.
 *
 * <p>Replaces the native health bar with a custom bar that shows both
 * health (red) and energy shield (cyan, semi-transparent overlay).
 *
 * <p>Uses div-based fills instead of HyUI {@code <progress>} elements
 * to avoid HyUI's opaque grey wrapper background. Background textures
 * and icons are applied via {@link GroupBuilder#withBackground} on the
 * first refresh tick (~50ms after creation).
 *
 * @see EnergyShieldHudManager
 */
public class EnergyShieldHud {

    // Layout — vanilla Health.ui values
    // Bottom = ContainerMargin(36) + HotbarHeight(78) + 6 = 120
    // HotbarWidthHud = 74*9 + 4*9 = 702
    private static final int BAR_WIDTH = 318;
    private static final int BAR_HEIGHT = 12;
    private static final int ICON_SIZE = 24;
    private static final int BOTTOM_OFFSET = 120;
    private static final int HOTBAR_WIDTH = 702;

    // Textures
    private static final String TEX_BAR_BG = "RPG/Huds/ShieldBar/HealthBarBackground.png";
    private static final String TEX_HEALTH_FILL = "RPG/Huds/ShieldBar/HealthBarFill.png";
    private static final String TEX_SHIELD_FILL = "RPG/Huds/ShieldBar/ShieldBarFill.png";
    private static final String TEX_HEALTH_ICON = "RPG/Huds/ShieldBar/HealthIcon.png";
    private static final String TEX_SHIELD_ICON = "RPG/Huds/ShieldBar/ShieldIcon.png";

    // Element IDs
    private static final String ID_ICON = "rpg-bar-icon";
    private static final String ID_BAR_BG = "rpg-bar-bg";
    private static final String ID_HEALTH_FILL = "rpg-health-fill";
    private static final String ID_SHIELD_FILL = "rpg-shield-fill";

    @Nonnull
    public static HyUIHud create(
            @Nonnull PlayerRef player,
            @Nonnull EnergyShieldTracker tracker,
            @Nonnull MaxShieldProvider maxShieldProvider,
            @Nonnull HealthRatioProvider healthRatioProvider) {

        UUID playerId = player.getUuid();

        float[] displayHealth = { healthRatioProvider.getHealthRatio(playerId) };
        float[] displayShield = { computeShieldRatio(tracker, maxShieldProvider, playerId) };
        boolean[] displayHasShield = { displayShield[0] > 0 };
        boolean[] texturesApplied = { false };

        int initHealthW = Math.round(BAR_WIDTH * displayHealth[0]);
        int initShieldW = Math.round(BAR_WIDTH * displayShield[0]);

        String html = buildHtml(initHealthW, initShieldW);

        HyUIHud hud = HudBuilder.hudForPlayer(player)
            .fromHtml(html)
            .onRefresh(h -> {
                float newHealth = healthRatioProvider.getHealthRatio(playerId);
                float newShield = computeShieldRatio(tracker, maxShieldProvider, playerId);
                boolean newHasShield = newShield > 0;

                // Apply textures on first refresh (~50ms after creation)
                if (!texturesApplied[0]) {
                    texturesApplied[0] = true;
                    h.getById(ID_BAR_BG, GroupBuilder.class).ifPresent(g ->
                        g.withBackground(TEX_BAR_BG));
                    h.getById(ID_HEALTH_FILL, GroupBuilder.class).ifPresent(g ->
                        g.withBackground(TEX_HEALTH_FILL));
                    h.getById(ID_SHIELD_FILL, GroupBuilder.class).ifPresent(g ->
                        g.withBackground(TEX_SHIELD_FILL));
                    h.getById(ID_ICON, GroupBuilder.class).ifPresent(g ->
                        g.withBackground(newHasShield ? TEX_SHIELD_ICON : TEX_HEALTH_ICON));
                }

                boolean healthChanged = Float.compare(newHealth, displayHealth[0]) != 0;
                boolean shieldChanged = Float.compare(newShield, displayShield[0]) != 0;
                boolean iconChanged = newHasShield != displayHasShield[0];

                if (!healthChanged && !shieldChanged && !iconChanged) {
                    return;
                }

                if (healthChanged) {
                    displayHealth[0] = newHealth;
                    int w = Math.max(0, Math.min(BAR_WIDTH, Math.round(BAR_WIDTH * newHealth)));
                    h.getById(ID_HEALTH_FILL, GroupBuilder.class).ifPresent(g ->
                        g.withAnchor(new HyUIAnchor().setLeft(0).setTop(0).setWidth(w).setHeight(BAR_HEIGHT)));
                }

                if (shieldChanged) {
                    displayShield[0] = newShield;
                    int w = Math.max(0, Math.min(BAR_WIDTH, Math.round(BAR_WIDTH * newShield)));
                    h.getById(ID_SHIELD_FILL, GroupBuilder.class).ifPresent(g ->
                        g.withAnchor(new HyUIAnchor().setLeft(0).setTop(0).setWidth(w).setHeight(BAR_HEIGHT)));
                }

                if (iconChanged) {
                    displayHasShield[0] = newHasShield;
                    h.getById(ID_ICON, GroupBuilder.class).ifPresent(g ->
                        g.withBackground(newHasShield ? TEX_SHIELD_ICON : TEX_HEALTH_ICON));
                }
            })
            .show();

        // Deterministic name — prevents MCHUD accumulation across world transitions
        hud.name = "too-energy-shield";
        return hud;
    }

    private static String buildHtml(int healthWidth, int shieldWidth) {
        // Mirrors vanilla Health.ui: Bottom-anchored 702-wide container → Left layout → icon + bar
        // Initial render uses placeholder colors; real textures applied on first refresh (~50ms)
        return """
            <div style="anchor-horizontal: 0; anchor-vertical: 0; layout-mode: Bottom;">
                <div style="layout-mode: Bottom; anchor-bottom: %d; anchor-width: %d; anchor-height: %d;">
                    <div style="layout-mode: Left;">
                        <div id="%s" style="anchor-left: 0; anchor-width: %d; anchor-height: %d; anchor-right: 4; anchor-top: -8;"></div>
                        <div id="%s" style="anchor-left: 0; anchor-width: %d; anchor-height: %d; layout-mode: Full;">
                            <div id="%s" style="anchor-left: 0; anchor-top: 0; anchor-width: %d; anchor-height: %d;"></div>
                            <div id="%s" style="anchor-left: 0; anchor-top: 0; anchor-width: %d; anchor-height: %d;"></div>
                        </div>
                    </div>
                </div>
            </div>
            """.formatted(
                BOTTOM_OFFSET, HOTBAR_WIDTH, BAR_HEIGHT,
                ID_ICON, ICON_SIZE, ICON_SIZE,
                ID_BAR_BG, BAR_WIDTH, BAR_HEIGHT,
                ID_HEALTH_FILL, healthWidth, BAR_HEIGHT,
                ID_SHIELD_FILL, shieldWidth, BAR_HEIGHT);
    }

    static float computeShieldRatio(EnergyShieldTracker tracker,
                                     MaxShieldProvider maxShieldProvider, UUID playerId) {
        float maxShield = maxShieldProvider.getMaxShield(playerId);
        if (maxShield <= 0) return 0f;
        EnergyShieldTracker.ShieldState state = tracker.getState(playerId);
        float current = (state == null) ? maxShield : Math.min(state.currentShield(), maxShield);
        return current / maxShield;
    }

    @FunctionalInterface
    public interface MaxShieldProvider {
        float getMaxShield(UUID playerId);
    }

    @FunctionalInterface
    public interface HealthRatioProvider {
        float getHealthRatio(UUID playerId);
    }

    private EnergyShieldHud() {}
}
