package io.github.larsonix.trailoforbis.maps.ui;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.protocol.packets.worldmap.HeightDeltaIconComponent;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarkerComponent;
import com.hypixel.hytale.protocol.packets.worldmap.TintComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MapMarkerBuilder;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MarkersCollector;
import io.github.larsonix.trailoforbis.maps.spawning.RealmMobSpawner;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Provides minimap markers showing live mob positions inside a realm.
 *
 * <p>Registered as a {@link WorldMapManager.MarkerProvider} for the realm's world.
 * Positions are snapshot-cached via {@link #refreshPositions} (called by the realm's
 * tick loop) so the marker update path never touches the entity store directly.
 */
public class RealmMobMarkerProvider implements WorldMapManager.MarkerProvider {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Red-tinted marker color for hostile mobs */
    private static final Color MOB_COLOR = new Color((byte) -1, (byte) 60, (byte) 60);
    private static final ComponentType<EntityStore, TransformComponent> TRANSFORM_TYPE =
            TransformComponent.getComponentType();

    /** Y-delta threshold: mobs within ±this many blocks show as dots instead of arrows */
    private static final int HEIGHT_THRESHOLD = 5;

    /** Markers beyond this horizontal distance are not shown at all */
    private static final double MAX_VISIBLE_DISTANCE = 100.0;

    /**
     * Distance band thresholds and their corresponding image suffixes.
     * Each tier's PNGs encode both reduced size AND reduced alpha:
     *   0–25 blocks → full size,  80% alpha ("_close")
     *  25–50 blocks → 80% size,  55% alpha ("_mid")
     *  50–75 blocks → 65% size,  35% alpha ("_far")
     *  75–100 blocks → 50% size, 20% alpha ("_distant")
     */
    private static final double[] DISTANCE_THRESHOLDS = {25.0, 50.0, 75.0, 100.0};
    private static final String[] TIER_SUFFIXES = {"_close", "_mid", "_far", "_distant"};

    private final UUID realmId;
    private final RealmMobSpawner mobSpawner;

    /** Snapshot of alive mob positions — volatile swap for thread-safe reads by the map renderer */
    private volatile List<Vector3d> cachedPositions = Collections.emptyList();

    public RealmMobMarkerProvider(@Nonnull UUID realmId, @Nonnull RealmMobSpawner mobSpawner) {
        this.realmId = realmId;
        this.mobSpawner = mobSpawner;
    }

    // ═══════════════════════════════════════════════════════════════════
    // POSITION REFRESH (called from realm tick)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Snapshots current positions of all alive realm mobs.
     * Called periodically by the realm's tick loop — NOT from the map renderer thread.
     */
    public void refreshPositions(@Nonnull World world) {
        Set<Ref<EntityStore>> aliveRefs = mobSpawner.getAliveEntityRefs(realmId);
        ArrayList<Vector3d> positions = new ArrayList<>(aliveRefs.size());
        Store<EntityStore> store = world.getEntityStore().getStore();

        for (Ref<EntityStore> ref : aliveRefs) {
            if (!ref.isValid()) continue;
            try {
                TransformComponent transform = store.getComponent(ref, TRANSFORM_TYPE);
                if (transform == null) continue;
                positions.add(transform.getPosition().clone());
            } catch (Exception ignored) {
                // Entity may have been removed between getAliveEntityRefs and getComponent
            }
        }

        cachedPositions = Collections.unmodifiableList(positions);
    }

    // ═══════════════════════════════════════════════════════════════════
    // MARKER PROVIDER (called by WorldMapManager)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Emits a marker for each cached mob position with:
     * <ul>
     *   <li><b>Height-relative icons</b>: dot (within ±5 Y), up arrow (mob above), down arrow (mob below)</li>
     *   <li><b>Distance-based size+opacity</b>: farther mobs get smaller and more transparent markers (80%→20% alpha, 100%→50% size)</li>
     *   <li><b>100-block cutoff</b>: mobs beyond 100 horizontal blocks are hidden entirely</li>
     * </ul>
     *
     * <p>Uses {@code addIgnoreViewDistance} so mob markers are always visible on the minimap
     * regardless of zoom level — players need to see where mobs are across the arena.
     */
    @Override
    public void update(@Nonnull World world, @Nonnull Player player, @Nonnull MarkersCollector collector) {
        List<Vector3d> positions = cachedPositions;
        if (positions.isEmpty()) return;

        // Get the player's current position for distance + height calculations.
        // PlayerRef.getTransform() is safe from the WorldMap thread — vanilla
        // PlayerIconMarkerProvider uses the same pattern.
        PlayerRef playerRef = Universe.get().getPlayer(player.getUuid());
        if (playerRef == null) return;
        Vector3d playerPos = playerRef.getTransform().getPosition();

        for (int i = 0; i < positions.size(); i++) {
            Vector3d mobPos = positions.get(i);

            // Horizontal (XZ) distance only — vertical component is handled by HeightDeltaIconComponent
            double dx = mobPos.getX() - playerPos.getX();
            double dz = mobPos.getZ() - playerPos.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);

            if (distance > MAX_VISIBLE_DISTANCE) continue;

            String suffix = getTierSuffix(distance);

            Transform markerTransform = new Transform(mobPos, new Vector3f(0.0f, 0.0f, 0.0f));
            // Tier suffix in ID forces immediate update when distance band changes —
            // MapMarkerTracker only compares name/yaw/position, not markerImage.
            MapMarker marker = new MapMarkerBuilder("realm_mob_" + i + suffix, "MobMarkerDot" + suffix + ".png", markerTransform)
                    .withComponent((MapMarkerComponent) new TintComponent(MOB_COLOR))
                    .withComponent((MapMarkerComponent) new HeightDeltaIconComponent(
                            HEIGHT_THRESHOLD, "MobMarkerUp" + suffix + ".png",
                            HEIGHT_THRESHOLD, "MobMarkerDown" + suffix + ".png"))
                    .build();
            collector.addIgnoreViewDistance(marker);
        }
    }

    /**
     * Returns the image filename suffix for the given horizontal distance.
     * Maps distance bands to pre-baked size+opacity tiers.
     */
    private static String getTierSuffix(double distance) {
        for (int i = 0; i < DISTANCE_THRESHOLDS.length; i++) {
            if (distance < DISTANCE_THRESHOLDS[i]) return TIER_SUFFIXES[i];
        }
        return TIER_SUFFIXES[TIER_SUFFIXES.length - 1];
    }
}
