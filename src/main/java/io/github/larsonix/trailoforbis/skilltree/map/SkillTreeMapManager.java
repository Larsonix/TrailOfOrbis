package io.github.larsonix.trailoforbis.skilltree.map;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.protocol.packets.interface_.SetPage;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.protocol.packets.worldmap.UpdateWorldMap;
import com.hypixel.hytale.protocol.packets.worldmap.UpdateWorldMapSettings;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.math.vector.Vector3d;
import io.github.larsonix.trailoforbis.api.services.SkillTreeMapService;
import io.github.larsonix.trailoforbis.api.services.SkillTreeService;
import io.github.larsonix.trailoforbis.config.ConfigManager;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implementation of SkillTreeMapService using packet spoofing.
 *
 * <p>Sends custom map packets to display the skill tree without
 * teleporting the player or modifying the actual world map.
 *
 * <p>Uses verified Hytale API patterns:
 * <ul>
 *   <li>writeNoCache() for packet sending (bypasses packet cache)</li>
 *   <li>ClearWorldMap (242) to reset map state</li>
 *   <li>UpdateWorldMapSettings (240) to configure display</li>
 *   <li>UpdateWorldMap (241) to send chunks and markers</li>
 *   <li>SetPage(Page.Map, true) (216) to open the map UI</li>
 * </ul>
 *
 * <p>Note: UpdateWorldMapVisible (243) is CLIENT-TO-SERVER only - the client sends
 * it to notify the server when the player manually opens/closes the map.
 */
public class SkillTreeMapManager implements SkillTreeMapService {

    private final SkillTreeService skillTreeService;
    private final SkillTreeMapRenderer renderer;
    private final Set<UUID> playersInMapMode = ConcurrentHashMap.newKeySet();

    // Track players with skill tree open per world (for multiplayer safety)
    private final Map<World, AtomicInteger> worldOpenCount = new ConcurrentHashMap<>();
    // Store original compass state per world (to restore on close)
    private final Map<World, Boolean> originalCompassState = new ConcurrentHashMap<>();

    /**
     * Creates a new SkillTreeMapManager.
     *
     * @param skillTreeService Service for skill tree data
     * @param configManager Config manager for settings
     */
    public SkillTreeMapManager(
        @Nonnull SkillTreeService skillTreeService,
        @Nonnull ConfigManager configManager
    ) {
        this.skillTreeService = Objects.requireNonNull(skillTreeService);
        this.renderer = new SkillTreeMapRenderer(skillTreeService, configManager);
    }

    @Override
    public void openMap(@Nonnull PlayerRef player) {
        Objects.requireNonNull(player, "player cannot be null");
        UUID uuid = player.getUuid();

        // Track player in map mode
        playersInMapMode.add(uuid);

        Ref<EntityStore> ref = player.getReference();
        double playerX = 0, playerZ = 0;

        if (ref != null && ref.isValid()) {
            Store<EntityStore> store = ref.getStore();
            Player playerEntity = store.getComponent(ref, Player.getComponentType());
            if (playerEntity != null) {
                World world = playerEntity.getWorld();
                WorldMapTracker tracker = playerEntity.getWorldMapTracker();

                // Step 1: CRITICAL - Disable world-level compass updating (stops ALL vanilla markers)
                // This is world-level, so we track open count for multiplayer safety
                if (world != null) {
                    int count = worldOpenCount
                        .computeIfAbsent(world, k -> new AtomicInteger(0))
                        .incrementAndGet();

                    // First player opening skill tree - save and disable compass
                    if (count == 1) {
                        originalCompassState.put(world, world.isCompassUpdating());
                        world.setCompassUpdating(false);  // Stops ALL marker providers
                    }
                }

                // Step 2: Disable chunk loading via view radius override
                // setViewRadiusOverride(0) prevents WorldMapTracker from sending ANY chunks
                // Note: This also calls clear() internally
                tracker.setViewRadiusOverride(0);

                // Step 3: Get player position for relative marker positioning
                TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
                if (transform != null) {
                    Vector3d pos = transform.getPosition();
                    playerX = pos.getX();
                    playerZ = pos.getZ();
                }
            }
        }

        // Step 4: Configure map settings
        UpdateWorldMapSettings settings = createMapSettings();
        player.getPacketHandler().writeNoCache(settings);

        // Step 5: Generate and send skill tree content (positioned relative to player)
        MapMarker[] markers = renderer.generateMarkers(uuid, playerX, playerZ);

        UpdateWorldMap mapUpdate = new UpdateWorldMap();
        mapUpdate.chunks = null;  // Don't send chunks
        mapUpdate.addedMarkers = markers;
        mapUpdate.removedMarkers = null;
        player.getPacketHandler().writeNoCache(mapUpdate);

        // Step 6: Open the map UI
        player.getPacketHandler().writeNoCache(new SetPage(Page.Map, true));
    }

    @Override
    public void closeMap(@Nonnull PlayerRef player) {
        Objects.requireNonNull(player, "player cannot be null");
        UUID uuid = player.getUuid();

        // Remove from tracking
        playersInMapMode.remove(uuid);

        // Step 1: Close map UI
        player.getPacketHandler().writeNoCache(new SetPage(Page.None, true));

        // Step 2: Restore normal map behavior
        Ref<EntityStore> ref = player.getReference();
        if (ref != null && ref.isValid()) {
            Store<EntityStore> store = ref.getStore();
            Player playerEntity = store.getComponent(ref, Player.getComponentType());
            if (playerEntity != null) {
                World world = playerEntity.getWorld();
                WorldMapTracker tracker = playerEntity.getWorldMapTracker();

                // Step 2a: Restore view radius (enables chunks again)
                // setViewRadiusOverride(null) removes the override and calls clear() internally
                tracker.setViewRadiusOverride(null);

                // Step 2b: Restore world compass state if this is the last player
                if (world != null) {
                    AtomicInteger counter = worldOpenCount.get(world);
                    if (counter != null) {
                        int remaining = counter.decrementAndGet();

                        // Last player closing skill tree - restore compass
                        if (remaining <= 0) {
                            worldOpenCount.remove(world);
                            Boolean original = originalCompassState.remove(world);
                            if (original != null) {
                                world.setCompassUpdating(original);
                            } else {
                                world.setCompassUpdating(true);  // Default to enabled
                            }
                        }
                    }
                }

                // Step 2c: Re-sync map settings to restore normal map behavior
                if (world != null) {
                    tracker.sendSettings(world);
                }
            }
        }
    }

    @Override
    public void toggleMap(@Nonnull PlayerRef player) {
        Objects.requireNonNull(player, "player cannot be null");

        if (isInMapMode(player.getUuid())) {
            closeMap(player);
        } else {
            openMap(player);
        }
    }

    @Override
    public boolean isInMapMode(@Nonnull UUID playerId) {
        Objects.requireNonNull(playerId, "playerId cannot be null");
        return playersInMapMode.contains(playerId);
    }

    @Override
    public void refreshMap(@Nonnull PlayerRef player) {
        Objects.requireNonNull(player, "player cannot be null");
        UUID uuid = player.getUuid();

        // Only refresh if player is actually viewing the map
        if (!isInMapMode(uuid)) {
            return;
        }

        // Get player position for relative marker positioning
        double playerX = 0, playerZ = 0;
        Ref<EntityStore> ref = player.getReference();
        if (ref != null && ref.isValid()) {
            Store<EntityStore> store = ref.getStore();
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            if (transform != null) {
                Vector3d pos = transform.getPosition();
                playerX = pos.getX();
                playerZ = pos.getZ();
            }
        }

        // Regenerate markers only (more efficient than full refresh)
        MapMarker[] markers = renderer.generateMarkers(uuid, playerX, playerZ);

        UpdateWorldMap mapUpdate = new UpdateWorldMap();
        mapUpdate.chunks = null;  // Don't resend chunks
        mapUpdate.addedMarkers = markers;
        mapUpdate.removedMarkers = null;
        player.getPacketHandler().writeNoCache(mapUpdate);
    }

    @Override
    public void onPlayerDisconnect(@Nonnull UUID playerId) {
        Objects.requireNonNull(playerId, "playerId cannot be null");
        playersInMapMode.remove(playerId);
    }

    /**
     * Shuts down the map manager, clearing all tracking state.
     */
    public void shutdown() {
        playersInMapMode.clear();
        worldOpenCount.clear();
        originalCompassState.clear();
    }

    /**
     * Creates map settings for skill tree display.
     *
     * <p>Settings are configured to provide a good viewing experience:
     * <ul>
     *   <li>enabled: true - map is active</li>
     *   <li>defaultScale: 1.0 - 1:1 pixel mapping for crisp display</li>
     *   <li>minScale/maxScale: Allow zoom between 0.5x and 4x</li>
     *   <li>allowTeleportToCoordinates: false - disable coordinate teleport</li>
     *   <li>allowTeleportToMarkers: false - use context menu for allocation instead</li>
     * </ul>
     *
     * <p>Available fields:
     * enabled, biomeDataMap, allowTeleportToCoordinates, allowTeleportToMarkers,
     * defaultScale, minScale, maxScale
     */
    private UpdateWorldMapSettings createMapSettings() {
        UpdateWorldMapSettings settings = new UpdateWorldMapSettings();

        // Enable the map
        settings.enabled = true;

        // Scale settings - MUST be within Hytale's valid range
        // Hytale defaults: defaultScale=32.0, minScale=2.0, maxScale=256.0
        settings.defaultScale = 4.0f;   // Zoomed in for skill tree detail
        settings.minScale = 2.0f;       // Minimum allowed by Hytale
        settings.maxScale = 32.0f;      // Allow zooming out to see full tree

        // Disable teleportation features - skill tree nodes use context menus
        settings.allowTeleportToCoordinates = false;
        settings.allowTeleportToMarkers = false;

        // biomeDataMap not needed for skill tree display
        settings.biomeDataMap = null;

        return settings;
    }
}
