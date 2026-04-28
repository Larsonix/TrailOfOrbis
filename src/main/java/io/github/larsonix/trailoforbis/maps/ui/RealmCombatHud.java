package io.github.larsonix.trailoforbis.maps.ui;

import au.ellie.hyui.builders.HudBuilder;
import au.ellie.hyui.builders.HyUIHud;
import au.ellie.hyui.builders.LabelBuilder;
import au.ellie.hyui.builders.ProgressBarBuilder;
import au.ellie.hyui.builders.HyUIStyle;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.gear.tooltip.TooltipStyles;
import io.github.larsonix.trailoforbis.maps.core.RealmCompletionTracker;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;
import io.github.larsonix.trailoforbis.ui.RPGStyles;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifier;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifierType;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.util.List;

/**
 * Builds the combat HUD displayed during realm combat.
 *
 * <p>Mirrors the full realm map tooltip layout — rarity, quality, biome/size,
 * modifiers with section headers, loot summary — combined with live combat
 * progress (timer + kill counter). The player sees everything they saw on the
 * gateway UI, plus real-time progress, in a single top-right panel.
 *
 * <h2>Layout</h2>
 * <pre>
 * ┌─────────────────────────────────────────┐
 * │  Legendary Forest Realm - Lv.50         │  title (rarity name baked in)
 * ├─────────────────────────────────────────┤
 * │  [LEGENDARY]   Excellent (92%)          │  rarity badge + quality
 * │  Medium                                 │  size
 * │  ─────────────────────────              │
 * │  Time Remaining :            12:45      │  dynamic (urgency-colored)
 * │  Monsters Slain :            47 / 52    │  dynamic
 * │  [████████████████████░░░░]             │  progress bar
 * │  ─────────────────────────              │
 * │  Challenges                             │  section header (red)
 * │    +35% Monster Health                  │
 * │    +20% Monster Damage                  │
 * │  Bonuses                                │  section header (gold)
 * │    +25% Item Quantity                   │
 * │    +15% Item Rarity                     │
 * │  ─────────────────────────              │
 * │  IIQ: +40%  IIR: +15%  XP: +10%       │  loot summary
 * │  Fortune's Compass: +12%               │  if present
 * │  Corrupted                              │  if applicable
 * └─────────────────────────────────────────┘
 * </pre>
 *
 * @see RealmHudManager
 * @see RealmMapSummonPage
 */
public class RealmCombatHud {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // ═══════════════════════════════════════════════════════════════════
    // COLORS — consistent with RealmMapTooltipBuilder + RealmMapSummonPage
    // ═══════════════════════════════════════════════════════════════════

    private static final String COLOR_CHALLENGE = "#FF6666";     // Red — difficulty mods (prefixes)
    private static final String COLOR_BONUS = "#FFD700";         // Gold — reward mods (suffixes)
    private static final String COLOR_COMPASS = "#7CF2A7";       // Teal — Fortune's Compass bonus
    private static final String COLOR_BIOME = "#88CCFF";         // Light blue — biome name
    private static final String COLOR_SIZE = "#AAFFAA";          // Light green — size name
    private static final String COLOR_CORRUPTED = "#FF4444";     // Bright red — corrupted notice
    private static final String COLOR_SECTION_HEADER = "#96A9BE"; // Blue-gray — section headers
    private static final String COLOR_DIVIDER = "#444444";       // Dark gray — horizontal dividers
    private static final String COLOR_SEPARATOR = "#333333";     // Darker gray — modifier zone separator

    // Timer urgency colors
    private static final String COLOR_TIMER_NORMAL = RPGStyles.TEXT_PRIMARY;   // White (>60s)
    private static final String COLOR_TIMER_WARNING = RPGStyles.TEXT_ORANGE;   // Orange (30-60s)
    private static final String COLOR_TIMER_CRITICAL = RPGStyles.NEGATIVE;     // Bright red (<30s)
    private static final String COLOR_TIMER_CRITICAL_PULSE = "#AA2222";        // Dark red (pulse)

    // Timer urgency thresholds (seconds)
    private static final long TIMER_WARNING_THRESHOLD = 60;
    private static final long TIMER_CRITICAL_THRESHOLD = 30;

    // ═══════════════════════════════════════════════════════════════════
    // LAYOUT
    // ═══════════════════════════════════════════════════════════════════

    private static final int HUD_WIDTH = 300;
    private static final int PROGRESS_BAR_HEIGHT = 8;
    private static final int REFRESH_RATE_MS = 1000;

    // Font sizes — hierarchy for information density
    private static final int FONT_RARITY_BADGE = 12;
    private static final int FONT_INFO = 11;
    private static final int FONT_COMBAT = 12;
    private static final int FONT_MODIFIER = 10;
    private static final int FONT_SECTION_HEADER = 9;
    private static final int FONT_SUMMARY = 10;

    // ═══════════════════════════════════════════════════════════════════
    // ELEMENT IDS (for refresh updates)
    // ═══════════════════════════════════════════════════════════════════

    private static final String ID_TIMER_LABEL = "timer-label";
    private static final String ID_TIMER_CAPTION = "timer-caption";
    private static final String ID_PROGRESS_LABEL = "progress-label";
    private static final String ID_PROGRESS_BAR_FILL = "progress-bar-fill";

    // ═══════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates and shows a combat HUD for a player with live refresh.
     *
     * @param player The player to show the HUD to
     * @param realm  The realm instance
     * @param store  The entity store
     * @return The created HUD instance
     */
    @Nonnull
    public static HyUIHud create(
            @Nonnull PlayerRef player,
            @Nonnull RealmInstance realm,
            @Nonnull Store<EntityStore> store) {

        String html = buildHtml(realm);

        return HudBuilder.hudForPlayer(player)
            .fromHtml(html)
            // NO withRefreshRate() — HyUI's ScheduledExecutorService runs on its own
            // thread and queues world.execute() tasks that race with world transitions.
            // Refresh is driven by RealmHudManager.tickCombatHud() on the world thread.
            .onRefresh(hud -> refreshHud(hud, realm))
            .show();
    }

    // ═══════════════════════════════════════════════════════════════════
    // REFRESH CALLBACK (dynamic elements only)
    // ═══════════════════════════════════════════════════════════════════

    private static void refreshHud(@Nonnull HyUIHud hud, @Nonnull RealmInstance realm) {
        if (realm.isClosing() || realm.isEnding()) {
            return;
        }

        RealmCompletionTracker tracker = realm.getCompletionTracker();
        Duration remaining = realm.getRemainingTime();
        long seconds = remaining.toSeconds();

        // Timer with urgency color
        String timeStr = formatTime(seconds);
        String timerColor = getTimerColor(seconds);
        hud.getById(ID_TIMER_LABEL, LabelBuilder.class).ifPresent(label ->
            label.withText(timeStr)
                 .withStyle(new HyUIStyle().setTextColor(timerColor)));

        String captionColor = (seconds <= TIMER_WARNING_THRESHOLD)
                ? timerColor : RPGStyles.TEXT_GRAY;
        hud.getById(ID_TIMER_CAPTION, LabelBuilder.class).ifPresent(label ->
            label.withStyle(new HyUIStyle().setTextColor(captionColor)));

        // Kill progress
        int killed = tracker.getKilledByPlayers();
        int required = tracker.getRequiredKills();
        float progressValue = required > 0 ? Math.min(1.0f, (float) killed / required) : 0f;

        hud.getById(ID_PROGRESS_LABEL, LabelBuilder.class).ifPresent(label ->
            label.withText(killed + " / " + required));

        hud.getById(ID_PROGRESS_BAR_FILL, ProgressBarBuilder.class).ifPresent(bar ->
            bar.withValue(progressValue));
    }

    // ═══════════════════════════════════════════════════════════════════
    // HTML GENERATION
    // ═══════════════════════════════════════════════════════════════════

    private static String buildHtml(@Nonnull RealmInstance realm) {
        RealmMapData mapData = realm.getMapData();
        RealmCompletionTracker tracker = realm.getCompletionTracker();

        // Dynamic combat values (initial state)
        int killed = tracker.getKilledByPlayers();
        int required = tracker.getRequiredKills();
        float progressValue = required > 0 ? Math.min(1.0f, (float) killed / required) : 0f;
        String timeStr = formatTime(realm.getRemainingTime().toSeconds());

        // Map identity
        String rarityName = capitalize(mapData.rarity().name());
        String biomeName = mapData.biome().getDisplayName();
        int level = mapData.level();
        String title = rarityName + " " + biomeName + " Realm - Lv." + level;

        // Quality
        String qualityName = TooltipStyles.getQualityName(mapData.quality());
        String qualityColor = TooltipStyles.getQualityColor(mapData.quality());
        String rarityColor = TooltipStyles.getRarityColor(mapData.rarity());

        // Modifiers
        List<RealmModifier> prefixes = mapData.prefixes();
        List<RealmModifier> suffixes = mapData.suffixes();
        double qualityMult = mapData.qualityMultiplier();

        // Loot summary values (quality-adjusted)
        int totalIiq = mapData.getTotalItemQuantity();
        int totalIir = mapData.getTotalItemRarity();
        int totalXp = mapData.getTotalExperienceBonus();
        int totalElite = mapData.getEliteChanceBonus();

        StringBuilder html = new StringBuilder();

        // ── Outer container ──────────────────────────────────────────
        html.append("""
            <div style="anchor-right: 10; anchor-top: 365; anchor-width: %d; layout-mode: Top;">
                <div class="decorated-container" data-hyui-title="%s" style="anchor-horizontal: 0;">
                    <div class="container-contents" style="layout-mode: Top;">
            """.formatted(HUD_WIDTH, escapeHtml(title)));

        // ── Section 1: Map identity ──────────────────────────────────
        // Rarity badge + Quality on one line
        html.append("""
                        <div style="layout-mode: Left; anchor-horizontal: 0; margin-top: -4;">
                            <p style="font-size: %d; color: %s;">[%s]</p>
                            <div style="flex-weight: 1;"></div>
                            <p style="font-size: %d; color: %s;">%s (%d%%)</p>
                        </div>
            """.formatted(
                FONT_RARITY_BADGE, rarityColor, escapeHtml(rarityName.toUpperCase()),
                FONT_INFO, qualityColor, escapeHtml(qualityName), mapData.quality()));

        // Size
        html.append("""
                        <div style="layout-mode: Left; anchor-horizontal: 0; margin-top: 2;">
                            <p style="font-size: %d; color: %s;">%s</p>
                        </div>
            """.formatted(FONT_INFO, COLOR_SIZE, escapeHtml(mapData.size().getDisplayName())));

        // ── Divider ──────────────────────────────────────────────────
        appendDivider(html, 8);

        // ── Section 2: Combat progress (dynamic) ─────────────────────
        // Timer
        html.append("""
                        <div style="layout-mode: Left; anchor-horizontal: 0; margin-top: 4;">
                            <p id="%s" style="font-size: %d; color: %s;">Time Remaining :</p>
                            <div style="flex-weight: 1;"></div>
                            <p id="%s" style="font-size: %d; color: %s;">%s</p>
                        </div>
            """.formatted(
                ID_TIMER_CAPTION, FONT_COMBAT, RPGStyles.TEXT_GRAY,
                ID_TIMER_LABEL, FONT_COMBAT, RPGStyles.TEXT_PRIMARY, timeStr));

        // Progress
        html.append("""
                        <div style="layout-mode: Left; anchor-horizontal: 0; margin-top: 6;">
                            <p style="font-size: %d; color: %s;">Monsters Slain :</p>
                            <div style="flex-weight: 1;"></div>
                            <p id="%s" style="font-size: %d; color: %s;">%d / %d</p>
                        </div>
            """.formatted(
                FONT_COMBAT, RPGStyles.TEXT_GRAY,
                ID_PROGRESS_LABEL, FONT_COMBAT, RPGStyles.TEXT_PRIMARY, killed, required));

        // Progress bar
        html.append("""
                        <progress id="%s" value="%.2f"
                                  style="anchor-height: %d; anchor-horizontal: 0; margin-top: 4;">
                        </progress>
            """.formatted(ID_PROGRESS_BAR_FILL, progressValue, PROGRESS_BAR_HEIGHT));

        // ── Section 3: Modifiers ─────────────────────────────────────
        if (!prefixes.isEmpty() || !suffixes.isEmpty()) {
            appendDivider(html, 8);

            // Challenges (prefixes — red)
            if (!prefixes.isEmpty()) {
                html.append("""
                        <p style="font-size: %d; color: %s; margin-top: 4;">Challenges</p>
                """.formatted(FONT_SECTION_HEADER, COLOR_SECTION_HEADER));

                for (RealmModifier mod : prefixes) {
                    String modText = formatModifierForHud(mod, qualityMult);
                    html.append("""
                        <p style="font-size: %d; color: %s; margin-top: 2;">  %s</p>
                    """.formatted(FONT_MODIFIER, COLOR_CHALLENGE, escapeHtml(modText)));
                }
            }

            // Separator between challenge and bonus zones
            if (!prefixes.isEmpty() && !suffixes.isEmpty()) {
                html.append("""
                        <div style="anchor-height: 1; anchor-horizontal: 0; background-color: %s; margin-top: 4;"></div>
                """.formatted(COLOR_SEPARATOR));
            }

            // Bonuses (suffixes — gold)
            if (!suffixes.isEmpty()) {
                html.append("""
                        <p style="font-size: %d; color: %s; margin-top: 4;">Bonuses</p>
                """.formatted(FONT_SECTION_HEADER, COLOR_SECTION_HEADER));

                for (RealmModifier mod : suffixes) {
                    String modText = formatModifierForHud(mod, qualityMult);
                    html.append("""
                        <p style="font-size: %d; color: %s; margin-top: 2;">  %s</p>
                    """.formatted(FONT_MODIFIER, COLOR_BONUS, escapeHtml(modText)));
                }
            }
        }

        // ── Section 4: Loot summary ──────────────────────────────────
        boolean hasLootStats = totalIiq > 0 || totalIir > 0 || totalXp > 0 || totalElite > 0;
        if (hasLootStats || mapData.fortunesCompassBonus() > 0 || mapData.corrupted()) {
            appendDivider(html, 8);

            // Compact stat summary — only non-zero values
            if (hasLootStats) {
                html.append("""
                        <div style="layout-mode: Left; anchor-horizontal: 0; margin-top: 4;">
                """);

                boolean first = true;
                if (totalIiq > 0) {
                    appendSummaryStat(html, "IIQ", totalIiq, COLOR_BONUS, first);
                    first = false;
                }
                if (totalIir > 0) {
                    appendSummaryStat(html, "IIR", totalIir, COLOR_BONUS, first);
                    first = false;
                }
                if (totalXp > 0) {
                    appendSummaryStat(html, "XP", totalXp, RPGStyles.POSITIVE, first);
                    first = false;
                }
                if (totalElite > 0) {
                    appendSummaryStat(html, "Elite", totalElite, COLOR_CHALLENGE, first);
                }

                html.append("""
                        </div>
                """);
            }

            // Fortune's Compass bonus
            if (mapData.fortunesCompassBonus() > 0) {
                html.append("""
                        <div style="layout-mode: Left; anchor-horizontal: 0; margin-top: 2;">
                            <p style="font-size: %d; color: %s;">Fortune's Compass: +%d%%</p>
                        </div>
                """.formatted(FONT_SUMMARY, COLOR_COMPASS, mapData.fortunesCompassBonus()));
            }

            // Corrupted notice
            if (mapData.corrupted()) {
                html.append("""
                        <div style="layout-mode: Left; anchor-horizontal: 0; margin-top: 4;">
                            <p style="font-size: %d; color: %s;">Corrupted</p>
                        </div>
                """.formatted(FONT_SUMMARY, COLOR_CORRUPTED));
            }
        }

        // ── Close containers ─────────────────────────────────────────
        html.append("""
                    </div>
                </div>
            </div>
            """);

        return html.toString();
    }

    // ═══════════════════════════════════════════════════════════════════
    // HTML HELPERS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Appends a horizontal divider line.
     */
    private static void appendDivider(@Nonnull StringBuilder html, int marginTop) {
        html.append("""
                        <div style="anchor-height: 1; anchor-horizontal: 0; background-color: %s; margin-top: %d;"></div>
            """.formatted(COLOR_DIVIDER, marginTop));
    }

    /**
     * Appends a single summary stat ("IIQ: +40%") to a Left-layout row.
     * Adds a spacer before non-first stats for visual separation.
     */
    private static void appendSummaryStat(
            @Nonnull StringBuilder html,
            @Nonnull String label,
            int value,
            @Nonnull String color,
            boolean isFirst) {

        if (!isFirst) {
            // Spacer between stats
            html.append("""
                            <p style="font-size: %d; color: %s;">   </p>
                """.formatted(FONT_SUMMARY, COLOR_DIVIDER));
        }

        html.append("""
                            <p style="font-size: %d; color: %s;">%s: +%d%%</p>
            """.formatted(FONT_SUMMARY, color, label, value));
    }

    // ═══════════════════════════════════════════════════════════════════
    // FORMATTING HELPERS
    // ═══════════════════════════════════════════════════════════════════

    private static String getTimerColor(long seconds) {
        if (seconds > TIMER_WARNING_THRESHOLD) {
            return COLOR_TIMER_NORMAL;
        }
        if (seconds > TIMER_CRITICAL_THRESHOLD) {
            return COLOR_TIMER_WARNING;
        }
        return (seconds % 2 == 0) ? COLOR_TIMER_CRITICAL : COLOR_TIMER_CRITICAL_PULSE;
    }

    private static String formatTime(long seconds) {
        if (seconds < 0) seconds = 0;
        long minutes = seconds / 60;
        long secs = seconds % 60;
        return String.format("%d:%02d", minutes, secs);
    }

    private static String formatModifierForHud(@Nonnull RealmModifier mod, double qualityMult) {
        RealmModifierType type = mod.type();
        if (type.isBinary()) {
            return type.getDisplayName();
        }
        int adjusted = (int) Math.round(mod.value() * qualityMult);
        return "+" + adjusted + "% " + type.getDisplayName();
    }

    private static String capitalize(@Nonnull String text) {
        if (text.isEmpty()) return text;
        return text.charAt(0) + text.substring(1).toLowerCase();
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }

    private RealmCombatHud() {}
}
