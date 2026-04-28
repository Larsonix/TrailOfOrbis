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
 * Builds the defeat banner HUD displayed when a realm times out.
 *
 * <p>The HUD shows:
 * <ul>
 *   <li>Defeat title ("Time's Up !")</li>
 *   <li>Stats: time survived, kill count</li>
 *   <li>Teleport countdown message</li>
 * </ul>
 *
 * <p>Positioned center-screen using vanilla Hytale UI styling.
 * The HUD is static (no refresh) — the player is teleported out
 * after 10 seconds by {@code RealmTimerSystem}.
 *
 * @see RealmHudManager
 * @see RealmVictoryHud
 */
public class RealmDefeatHud {

    private static final String COLOR_RED = "#ff4444";
    private static final String COLOR_ORANGE = "#ff8844";

    private static final int HUD_WIDTH = 320;
    private static final int DEFEAT_PHASE_SECONDS = 10;

    /**
     * Creates and shows a static defeat HUD for a player.
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

    /**
     * Gets the defeat phase duration in seconds.
     */
    public static int getDefeatPhaseSeconds() {
        return DEFEAT_PHASE_SECONDS;
    }

    private static String buildHtml(
            @Nonnull RealmInstance realm,
            @Nonnull UUID playerId) {

        RealmCompletionTracker tracker = realm.getCompletionTracker();
        long elapsedSeconds = tracker.getElapsedSeconds();
        int playerKills = tracker.getPlayerKills(playerId);

        String timeStr = formatTime(elapsedSeconds);

        StringBuilder html = new StringBuilder();

        html.append("""
            <div style="anchor-right: 10; anchor-top: 50; layout-mode: Top;">
                <div class="decorated-container" data-hyui-title="Time's Up !" style="anchor-width: %d;">
                    <div class="container-contents" style="layout-mode: Top;">
            """.formatted(HUD_WIDTH));

        // Time survived
        html.append("""
                            <div style="layout-mode: Left; anchor-horizontal: 0; margin-top: 4;">
                                <p style="font-size: 13; color: %s;">Time Survived :</p>
                                <div style="flex-weight: 1;"></div>
                                <p style="font-size: 13; color: %s;">%s</p>
                            </div>
            """.formatted(RPGStyles.TEXT_GRAY, RPGStyles.TEXT_PRIMARY, timeStr));

        // Kills
        html.append("""
                            <div style="layout-mode: Left; anchor-horizontal: 0; margin-top: 6;">
                                <p style="font-size: 13; color: %s;">Your Kills :</p>
                                <div style="flex-weight: 1;"></div>
                                <p style="font-size: 13; color: %s;">%d</p>
                            </div>
            """.formatted(RPGStyles.TEXT_GRAY, RPGStyles.TEXT_PRIMARY, playerKills));

        // Divider
        html.append("""
                            <div style="anchor-height: 1; anchor-horizontal: 0; background-color: #444444; margin-top: 12;"></div>
            """);

        // Defeat message
        html.append("""
                            <div style="layout-mode: Center; anchor-horizontal: 0; margin-top: 10;">
                                <p style="font-size: 13; color: %s;">The realm has overwhelmed you !</p>
                            </div>
            """.formatted(COLOR_RED));

        // Teleport countdown
        html.append("""
                            <div style="layout-mode: Center; anchor-horizontal: 0; margin-top: 6;">
                                <p style="font-size: 12; color: %s;">Teleporting out in %d seconds...</p>
                            </div>
            """.formatted(COLOR_ORANGE, DEFEAT_PHASE_SECONDS));

        html.append("""
                    </div>
                </div>
            </div>
            """);

        return html.toString();
    }

    private static String formatTime(long seconds) {
        long minutes = seconds / 60;
        long secs = seconds % 60;
        return String.format("%d:%02d", minutes, secs);
    }

    private RealmDefeatHud() {}
}
