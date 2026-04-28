package io.github.larsonix.trailoforbis.maps.ui;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarkerComponent;
import com.hypixel.hytale.protocol.packets.worldmap.TintComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
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
     * Emits a red-tinted map marker for each cached mob position.
     * Uses {@code addIgnoreViewDistance} so mob markers are always visible on the minimap
     * regardless of zoom level — players need to see where mobs are across the arena.
     */
    @Override
    public void update(@Nonnull World world, @Nonnull Player player, @Nonnull MarkersCollector collector) {
        List<Vector3d> positions = cachedPositions;
        for (int i = 0; i < positions.size(); i++) {
            Vector3d pos = positions.get(i);
            Transform markerTransform = new Transform(pos, new Vector3f(0.0f, 0.0f, 0.0f));
            MapMarker marker = new MapMarkerBuilder("realm_mob_" + i, "MobMarker.png", markerTransform)
                    .withComponent((MapMarkerComponent) new TintComponent(MOB_COLOR))
                    .build();
            collector.addIgnoreViewDistance(marker);
        }
    }
}
