package io.github.larsonix.trailoforbis.maps.ui;

import au.ellie.hyui.builders.HudBuilder;
import au.ellie.hyui.builders.HyUIHud;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.maps.core.RealmCompletionTracker;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;
import io.github.larsonix.trailoforbis.ui.RPGStyles;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Builds the victory banner HUD displayed when a realm is completed.
 *
 * <p>The HUD shows:
 * <ul>
 *   <li>Victory title with celebratory styling</li>
 *   <li>Stats: completion time, kill count, performance grade</li>
 *   <li>Portal message — players leave at their own pace</li>
 * </ul>
 *
 * <p>Positioned center-screen using vanilla Hytale UI styling.
 * The HUD is static (no refresh) since there is no countdown.
 *
 * @see RealmHudManager
 */
public class RealmVictoryHud {

    // ═══════════════════════════════════════════════════════════════════
    // CONSTANTS - COLORS (realm-specific; shared colors via RPGStyles)
    // ═══════════════════════════════════════════════════════════════════

    private static final String COLOR_GREEN = "#44ff44";
    private static final String COLOR_GRADE_D = "#ff8844";  // Orange

    // ═══════════════════════════════════════════════════════════════════
    // CONSTANTS - LAYOUT
    // ═══════════════════════════════════════════════════════════════════

    private static final int HUD_WIDTH = 320;

    // ═══════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates and shows a static victory HUD for a player.
     *
     * <p>The HUD is removed by {@link RealmHudManager#removeAllHudsForPlayerSync(UUID)}
     * when the player leaves the realm world (via DrainPlayerFromWorldEvent).
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

        UUID playerId = player.getUuid();
        String html = buildHtml(realm, playerId);

        return HudBuilder.hudForPlayer(player)
            .fromHtml(html)
            .show();
    }

    // ═══════════════════════════════════════════════════════════════════
    // HTML GENERATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Builds the complete HTML for the victory HUD.
     */
    private static String buildHtml(
            @Nonnull RealmInstance realm,
            @Nonnull UUID playerId) {

        RealmCompletionTracker tracker = realm.getCompletionTracker();
        long elapsedSeconds = tracker.getElapsedSeconds();
        int playerKills = tracker.getPlayerKills(playerId);
        int totalKills = tracker.getKilledByPlayers();

        String timeStr = formatTime(elapsedSeconds);

        // Calculate score grade
        String grade = calculateGrade(elapsedSeconds, playerKills, totalKills, realm.getMapData().level());
        String gradeColor = getGradeColor(grade);

        StringBuilder html = new StringBuilder();

        // Position at top-right, 50px from top
        html.append("""
            <div style="anchor-right: 10; anchor-top: 50; layout-mode: Top;">
                <div class="decorated-container" data-hyui-title="Realm Complete !" style="anchor-width: %d;">
                    <div class="container-contents" style="layout-mode: Top;">
            """.formatted(HUD_WIDTH));

        // Stats section
        html.append("""
                            <!-- Time -->
                            <div style="layout-mode: Left; anchor-horizontal: 0; margin-top: 4;">
                                <p style="font-size: 13; color: %s;">Completion Time :</p>
                                <div style="flex-weight: 1;"></div>
                                <p style="font-size: 13; color: %s; font-weight: bold;">%s</p>
                            </div>
            """.formatted(RPGStyles.TEXT_GRAY, RPGStyles.TEXT_PRIMARY, timeStr));

        html.append("""
                            <!-- Kills -->
                            <div style="layout-mode: Left; anchor-horizontal: 0; margin-top: 6;">
                                <p style="font-size: 13; color: %s;">Your Kills :</p>
                                <div style="flex-weight: 1;"></div>
                                <p style="font-size: 13; color: %s; font-weight: bold;">%d</p>
                            </div>
            """.formatted(RPGStyles.TEXT_GRAY, RPGStyles.TEXT_PRIMARY, playerKills));

        html.append("""
                            <!-- Grade -->
                            <div style="layout-mode: Left; anchor-horizontal: 0; margin-top: 6;">
                                <p style="font-size: 13; color: %s;">Performance Grade :</p>
                                <div style="flex-weight: 1;"></div>
                                <p style="font-size: 16; color: %s; font-weight: bold;">%s</p>
                            </div>
            """.formatted(RPGStyles.TEXT_GRAY, gradeColor, grade));

        // Divider
        html.append("""
                            <div style="anchor-height: 1; anchor-horizontal: 0; background-color: #444444; margin-top: 12;"></div>
            """);

        // Portal message
        html.append("""
                            <div style="layout-mode: Center; anchor-horizontal: 0; margin-top: 10;">
                                <p style="font-size: 12; color: %s;">A victory portal has appeared !</p>
                            </div>
            """.formatted(COLOR_GREEN));

        // "Leave when ready" message (replaces countdown)
        html.append("""
                            <div style="layout-mode: Center; anchor-horizontal: 0; margin-top: 6;">
                                <p style="font-size: 12; color: %s;">Use the portal to leave when ready</p>
                            </div>
            """.formatted(COLOR_GREEN));

        // Close containers
        html.append("""
                    </div>
                </div>
            </div>
            """);

        return html.toString();
    }

    // ═══════════════════════════════════════════════════════════════════
    // SCORE CALCULATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Calculates a letter grade based on performance.
     *
     * @param elapsedSeconds Total time to complete
     * @param playerKills    This player's kills
     * @param totalKills     Total kills in realm
     * @param realmLevel     Level of the realm
     * @return Letter grade (S, A+, A, B+, B, C, D)
     */
    static String calculateGrade(long elapsedSeconds, int playerKills, int totalKills, int realmLevel) {
        // Base time thresholds (in seconds)
        // Higher level realms have more lenient thresholds
        int levelBonus = realmLevel * 15;

        int sThreshold = 180 + levelBonus;   // S: < 3 min + level bonus
        int aThreshold = 300 + levelBonus;   // A: < 5 min + level bonus
        int bThreshold = 480 + levelBonus;   // B: < 8 min + level bonus
        int cThreshold = 720 + levelBonus;   // C: < 12 min + level bonus

        // Calculate contribution bonus
        float contribution = totalKills > 0 ? (float) playerKills / totalKills : 0;
        boolean highContribution = contribution >= 0.5f;

        // Determine base grade from time
        String baseGrade;
        if (elapsedSeconds < sThreshold) {
            baseGrade = "S";
        } else if (elapsedSeconds < aThreshold) {
            baseGrade = "A";
        } else if (elapsedSeconds < bThreshold) {
            baseGrade = "B";
        } else if (elapsedSeconds < cThreshold) {
            baseGrade = "C";
        } else {
            baseGrade = "D";
        }

        // Apply contribution bonus
        if (highContribution && !baseGrade.equals("S") && !baseGrade.equals("D")) {
            return baseGrade + "+";
        }

        return baseGrade;
    }

    /**
     * Returns the color for a grade.
     */
    private static String getGradeColor(String grade) {
        return switch (grade.charAt(0)) {
            case 'S' -> RPGStyles.TITLE_GOLD;
            case 'A' -> COLOR_GREEN;
            case 'B' -> RPGStyles.TEXT_INFO;
            case 'C' -> RPGStyles.TEXT_WARNING;
            default -> COLOR_GRADE_D;
        };
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Formats elapsed seconds as mm:ss.
     */
    private static String formatTime(long seconds) {
        long minutes = seconds / 60;
        long secs = seconds % 60;
        return String.format("%d:%02d", minutes, secs);
    }

    private RealmVictoryHud() {}
}
