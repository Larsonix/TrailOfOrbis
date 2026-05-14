package io.github.larsonix.trailoforbis.maps.ui;

import com.hypixel.hytale.builtin.portals.components.PortalDeviceConfig;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.maps.RealmsManager;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifier;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Custom UI page shown when pressing F on an Ancient Gateway with an active realm portal.
 * Reuses the vanilla {@code PortalDeviceSummon.ui} template (same look as {@link RealmMapSummonPage})
 * but replaces the "Summon Portal" button with a "Close Portal" button for the realm owner,
 * or a read-only info display for non-owners.
 *
 * <p>This prevents the exploit where players could re-open realms infinitely from consumed map data.
 *
 * @see RealmMapSummonPage
 */
public class RealmActivePortalPage extends InteractiveCustomUIPage<RealmActivePortalPage.Data> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Colors matching RealmMapSummonPage
    private static final String COLOR_DIFFICULTY = "#FF6666";
    private static final String COLOR_REWARD = "#FFD700";
    private static final String COLOR_QUALITY = "#AAFFAA";
    private static final String COLOR_MUTED = "#778292";
    private static final String COLOR_TEXT = "#dee2ef";

    // Pill badge colors
    private static final String PILL_BIOME_COLOR = "#2a6496";
    private static final String PILL_SIZE_COLOR = "#3a7a3a";

    private final PortalDeviceConfig portalConfig;
    private final RealmInstance realm;
    private final boolean isOwner;

    public RealmActivePortalPage(
            @Nonnull PlayerRef playerRef,
            @Nonnull PortalDeviceConfig portalConfig,
            @Nonnull RealmInstance realm,
            boolean isOwner) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, Data.CODEC);
        this.portalConfig = portalConfig;
        this.realm = realm;
        this.isOwner = isOwner;
    }

    // =========================================================================
    // BUILD — Populate the native template with realm info (read-only view)
    // =========================================================================

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder cmd,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {

        RealmMapData mapData = realm.getMapData();

        cmd.append("Pages/PortalDeviceSummon.ui");

        // Title: same format as RealmMapSummonPage
        String title = mapData.identified()
                ? capitalize(mapData.rarity().name()) + " " + mapData.biome().getDisplayName() + " Realm"
                : capitalize(mapData.rarity().name()) + " Realm (Unidentified)";
        cmd.set("#Title0.TextSpans", Message.raw(title).color(mapData.rarity().getHexColor()));

        // Time remaining (not total — shows live remaining time)
        Duration remaining = realm.getRemainingTime();
        long minutes = remaining.toMinutes();
        long seconds = remaining.toSecondsPart();
        String timeText = String.format("%d:%02d remaining", minutes, seconds);
        cmd.set("#ExplorationTimeText.TextSpans",
                Message.raw(timeText).color(COLOR_TEXT));

        // No void invasion
        cmd.set("#BreachTimeBullet.Visible", false);

        // Quality multiplier for modifier display
        double qualityMult = mapData.qualityMultiplier();

        // Difficulty modifiers (prefixes)
        if (mapData.identified()) {
            List<RealmModifier> prefixes = mapData.prefixes();
            cmd.set("#Objectives.Visible", !prefixes.isEmpty());
            for (int i = 0; i < prefixes.size(); i++) {
                cmd.append("#ObjectivesList", "Pages/Portals/BulletPoint.ui");
                cmd.set("#ObjectivesList[" + i + "] #Label.TextSpans",
                        Message.raw(formatModifier(prefixes.get(i), qualityMult)).color(COLOR_DIFFICULTY));
            }

            // Reward modifiers (suffixes)
            List<RealmModifier> suffixes = mapData.suffixes();
            cmd.set("#Tips.Visible", !suffixes.isEmpty());
            for (int i = 0; i < suffixes.size(); i++) {
                cmd.append("#TipsList", "Pages/Portals/BulletPoint.ui");
                cmd.set("#TipsList[" + i + "] #Label.TextSpans",
                        Message.raw(formatModifier(suffixes.get(i), qualityMult)).color(COLOR_REWARD));
            }
        } else {
            cmd.set("#Objectives.Visible", false);
            cmd.set("#Tips.Visible", false);
        }

        // Pill badges: Rarity + Biome + Size
        addPill(cmd, 0, capitalize(mapData.rarity().name()), mapData.rarity().getHexColor());
        if (mapData.identified()) {
            addPill(cmd, 1, mapData.biome().getDisplayName(), PILL_BIOME_COLOR);
            addPill(cmd, 2, mapData.size().getDisplayName(), PILL_SIZE_COLOR);
        } else {
            addPill(cmd, 1, "???", PILL_BIOME_COLOR);
            addPill(cmd, 2, "???", PILL_SIZE_COLOR);
        }

        // Flavor text: Level + Quality + State + Players
        Message flavor = Message.empty()
                .insert(Message.raw("Level " + mapData.level()).color(COLOR_TEXT))
                .insert(Message.raw("  |  ").color(COLOR_MUTED))
                .insert(Message.raw("Quality: " + mapData.quality() + "%").color(COLOR_QUALITY))
                .insert(Message.raw("  |  ").color(COLOR_MUTED))
                .insert(Message.raw(realm.getState().getDisplayName()).color(COLOR_TEXT))
                .insert(Message.raw("  |  ").color(COLOR_MUTED))
                .insert(Message.raw("Players: " + realm.getPlayerCount()).color(COLOR_TEXT));
        cmd.set("#FlavorLabel.TextSpans", flavor);

        // Replace #Summon section: clear vanilla content, append our .ui template
        cmd.clear("#Summon");

        if (isOwner) {
            cmd.append("#Summon", "RPG/Pages/RealmPortalClose.ui");
            events.addEventBinding(CustomUIEventBindingType.Activating, "#ClosePortalBtn",
                    EventData.of("Action", "ClosePortalActivated"), false);
        } else {
            cmd.append("#Summon", "RPG/Pages/RealmPortalInfo.ui");
        }
    }

    private void addPill(UICommandBuilder cmd, int index, String label, String color) {
        String child = "#Pills[" + index + "]";
        cmd.append("#Pills", "Pages/Portals/Pill.ui");
        cmd.set(child + ".Background.Color", color);
        cmd.set(child + " #Label.TextSpans", Message.raw(label));
    }

    // =========================================================================
    // HANDLE EVENTS — Close Portal button click
    // =========================================================================

    @Override
    public void handleDataEvent(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull Data data) {

        if (!"ClosePortalActivated".equals(data.action)) {
            return;
        }

        // Security: verify the player is actually the owner (defense-in-depth,
        // even though non-owners don't get the event binding)
        if (!isOwner) {
            LOGGER.atWarning().log("Non-owner %s attempted to close realm %s via forged event",
                playerRef.getUuid(), realm.getRealmId().toString().substring(0, 8));
            return;
        }

        // Close the UI
        Player playerComponent = store.getComponent(ref, Player.getComponentType());
        if (playerComponent != null) {
            playerComponent.getPageManager().setPage(ref, store, Page.None);
        }

        RealmsManager realmsManager = getRealmsManager();
        if (realmsManager == null) {
            sendError("Realms system is not available !");
            return;
        }

        UUID realmId = realm.getRealmId();

        // Re-check realm still exists (could have closed by timeout/victory)
        var realmOpt = realmsManager.getRealm(realmId);
        if (realmOpt.isEmpty()) {
            sendInfo("This realm has already closed.");
            return;
        }

        LOGGER.atInfo().log("Player %s closing realm %s via portal UI",
            playerRef.getUuid(), realmId.toString().substring(0, 8));

        realmsManager.forceCloseRealm(realmId);
        sendSuccess("Portal closed. The realm has been shut down.");
    }

    // =========================================================================
    // UTILITIES
    // =========================================================================

    private RealmsManager getRealmsManager() {
        TrailOfOrbis plugin = TrailOfOrbis.getInstanceOrNull();
        return plugin != null ? plugin.getRealmsManager() : null;
    }

    private void sendError(String message) {
        playerRef.sendMessage(
            Message.raw("[Realms] ").color(MessageColors.DARK_PURPLE)
                .insert(Message.raw(message).color(MessageColors.ERROR)));
    }

    private void sendSuccess(String message) {
        playerRef.sendMessage(
            Message.raw("[Realms] ").color(MessageColors.DARK_PURPLE)
                .insert(Message.raw(message).color(MessageColors.SUCCESS)));
    }

    private void sendInfo(String message) {
        playerRef.sendMessage(
            Message.raw("[Realms] ").color(MessageColors.DARK_PURPLE)
                .insert(Message.raw(message).color(MessageColors.INFO)));
    }

    private static String formatModifier(RealmModifier mod, double qualityMult) {
        String text = mod.type().formatValue(mod.value(), qualityMult);
        if (mod.locked()) {
            text += " [Locked]";
        }
        return text;
    }

    private static String capitalize(String enumName) {
        if (enumName == null || enumName.isEmpty()) return enumName;
        return enumName.charAt(0) + enumName.substring(1).toLowerCase();
    }

    // =========================================================================
    // DATA — Event data from UI button clicks
    // =========================================================================

    protected static class Data {
        public static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (d, s) -> d.action = s, d -> d.action)
                .add()
                .build();

        String action;
    }
}
