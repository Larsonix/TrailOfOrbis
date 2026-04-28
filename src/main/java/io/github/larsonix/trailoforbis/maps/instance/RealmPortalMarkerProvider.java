package io.github.larsonix.trailoforbis.maps.instance;

import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.protocol.packets.worldmap.ContextMenuItem;
import com.hypixel.hytale.protocol.packets.worldmap.HeightDeltaIconComponent;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.protocol.packets.worldmap.TintComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.spawn.ISpawnProvider;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MapMarkerBuilder;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MarkersCollector;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import javax.annotation.Nonnull;

/**
 * Sends a "Portal" map marker at the realm's spawn/exit point.
 * Appears on both the world map and the compass HUD, always visible
 * regardless of player distance.
 *
 * <p>The marker is tinted by rarity color, shows height-delta arrows when
 * the player is above/below the portal, and offers a right-click "View Realm
 * Info" context menu option.</p>
 *
 * <p>Modeled after Hytale's built-in {@code PortalMarkerProvider} and
 * {@code OtherPlayersMarkerProvider}.</p>
 */
public class RealmPortalMarkerProvider implements WorldMapManager.MarkerProvider {

    private static final int HEIGHT_DELTA_BLOCKS = 12;

    private final GearRarity rarity;
    private final String markerLabel;

    public RealmPortalMarkerProvider(RealmMapData mapData) {
        this.rarity = mapData.rarity();
        this.markerLabel = "Lv" + mapData.level() + " " + mapData.biome().getDisplayName();
    }

    @Override
    public void update(@Nonnull World world, @Nonnull Player player, @Nonnull MarkersCollector collector) {
        ISpawnProvider spawnProvider = world.getWorldConfig().getSpawnProvider();
        if (spawnProvider == null) {
            return;
        }

        @SuppressWarnings("deprecation")
        Transform[] spawnPoints = spawnProvider.getSpawnPoints();
        if (spawnPoints == null || spawnPoints.length == 0) {
            return;
        }

        Transform portalPoint = spawnPoints[0];
        MapMarker marker = new MapMarkerBuilder("Portal", "Portal.png", portalPoint)
            .withCustomName(markerLabel)
            .withComponent(new TintComponent(rarityToColor(rarity)))
            .withComponent(new HeightDeltaIconComponent(
                HEIGHT_DELTA_BLOCKS, "PlayerAbove.png",
                HEIGHT_DELTA_BLOCKS, "PlayerBelow.png"))
            .withContextMenuItem(new ContextMenuItem("View Realm Info", "/too realm info"))
            .build();
        collector.addIgnoreViewDistance(marker);
    }

    private static Color rarityToColor(GearRarity rarity) {
        String hex = rarity.getHexColor();
        return new Color(
            (byte) Integer.parseInt(hex.substring(1, 3), 16),
            (byte) Integer.parseInt(hex.substring(3, 5), 16),
            (byte) Integer.parseInt(hex.substring(5, 7), 16));
    }
}
