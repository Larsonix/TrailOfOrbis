# Hytale World Map System

The World Map system provides an in-game map with terrain chunks, markers (icons with labels, context menus, and components), and configurable settings. Plugins can add custom marker providers, send raw map packets, and even hijack the map UI for non-map purposes (like our skill tree overlay).

There are two distinct approaches to displaying content on the map:

1. **Marker Providers** (server-managed): Register a `MarkerProvider` with `WorldMapManager`. The engine calls `update()` every tick and automatically handles add/remove diffs. Best for world-relative markers that should appear on the compass and map.
2. **Direct Packet Spoofing** (plugin-managed): Send `UpdateWorldMap`, `UpdateWorldMapSettings`, and `ClearWorldMap` packets directly via `writeNoCache()`. Best for custom overlays that replace the normal map (like our skill tree).

## Quick Reference

| Class | Package | Purpose |
|-------|---------|---------|
| `MapMarker` | `protocol.packets.worldmap` | Data structure for a single map marker |
| `MapMarkerBuilder` | `server.core.universe.world.worldmap.markers` | Fluent builder for `MapMarker` |
| `MapMarkerComponent` | `protocol.packets.worldmap` | Abstract base for marker components |
| `TintComponent` | `protocol.packets.worldmap` | Colors a marker icon |
| `HeightDeltaIconComponent` | `protocol.packets.worldmap` | Shows up/down arrows when player is above/below marker |
| `PlayerMarkerComponent` | `protocol.packets.worldmap` | Associates a marker with a player UUID |
| `PlacedByMarkerComponent` | `protocol.packets.worldmap` | Shows "placed by" attribution on a marker |
| `ContextMenuItem` | `protocol.packets.worldmap` | Right-click menu option on a marker |
| `MarkersCollector` | `server.core.universe.world.worldmap.markers` | Interface for adding markers (with or without view distance check) |
| `WorldMapManager` | `server.core.universe.world.worldmap` | Per-world manager with marker providers and image cache |
| `WorldMapTracker` | `server.core.universe.world` | Per-player tracker for loaded chunks and sent markers |
| `MapChunk` | `protocol.packets.worldmap` | Map tile with chunk coordinates and image data |
| `MapImage` | `protocol.packets.worldmap` | Pixel data (width, height, `int[]` ARGB) |
| `UpdateWorldMap` | `protocol.packets.worldmap` | Packet 241: sends chunks and/or marker add/remove |
| `UpdateWorldMapSettings` | `protocol.packets.worldmap` | Packet 240: configures map behavior |
| `ClearWorldMap` | `protocol.packets.worldmap` | Packet 242: resets client map state |
| `UpdateWorldMapVisible` | `protocol.packets.worldmap` | Packet 243: **client-to-server only** |
| `SetPage(Page.Map, true)` | `protocol.packets.interface_` | Opens the map UI page |
| `BiomeData` | `protocol.packets.worldmap` | Biome color/name data sent with settings |

## Map Markers

### MapMarker Fields

```java
public class MapMarker {
    @Nonnull  public String id = "";                    // Unique identifier
    @Nullable public FormattedMessage name;             // Hover tooltip (translated)
    @Nullable public String customName;                 // Hover tooltip (raw string)
    @Nonnull  public String markerImage = "";           // Icon filename (e.g., "Portal.png")
    @Nonnull  public Transform transform = new Transform(); // World position + orientation
    @Nullable public ContextMenuItem[] contextMenuItems;    // Right-click menu options
    @Nullable public MapMarkerComponent[] components;       // Tint, height delta, etc.
}
```

- `id` -- Unique string identifier. The engine uses this to diff markers between ticks. Markers with the same `id` are treated as updates, not new additions. Use a stable, unique prefix (e.g., `"Portal"`, `"Player-<uuid>"`, `"node_<nodeId>"`).
- `name` vs `customName` -- Both set the hover tooltip text. `name` is a `FormattedMessage` (supports localization via `Message.translation()`). `customName` is a raw string. Only one should be set.
- `markerImage` -- Filename of the icon rendered on the map. See [Known Marker Icons](#known-marker-icons) below.
- `transform` -- World position. **Note:** There are TWO `Transform` classes. `MapMarkerBuilder` takes `com.hypixel.hytale.math.vector.Transform` (server-side). Raw `MapMarker` uses `com.hypixel.hytale.protocol.Transform` (packet-side). The builder handles the conversion via `PositionUtil.toTransformPacket()`.

### MapMarkerBuilder (Recommended)

The builder is the standard way to create markers in a `MarkerProvider`:

```java
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MapMarkerBuilder;

MapMarker marker = new MapMarkerBuilder("Portal", "Portal.png", transform)
    .withName(Message.translation("server.portals.exit.marker"))    // localized
    .withCustomName("Lv12 Forest")                                   // or raw string
    .withComponent(new TintComponent(new Color((byte)0xFF, (byte)0xA5, (byte)0x00)))
    .withComponent(new HeightDeltaIconComponent(12, "PlayerAbove.png", 12, "PlayerBelow.png"))
    .withContextMenuItem(new ContextMenuItem("View Info", "/mycommand info"))
    .build();
```

Constructor: `MapMarkerBuilder(String id, String image, Transform transform)` -- takes the server-side `math.vector.Transform`.

### Creating Markers Directly (Packet Spoofing)

When sending markers via raw `UpdateWorldMap` packets (not through a provider), construct `MapMarker` directly:

```java
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.protocol.Transform;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.Direction;

MapMarker marker = new MapMarker();
marker.id = "node_strength_1";
marker.markerImage = "Portal.png";

FormattedMessage nameMsg = new FormattedMessage();
nameMsg.rawText = "Strength +5";
marker.name = nameMsg;

// Protocol Transform (not math.vector.Transform)
marker.transform = new Transform(
    new Position(playerX + offsetX, 0.0, playerZ + offsetZ),
    new Direction(0.0f, 0.0f, 0.0f)
);
```

### Known Marker Icons

These icon filenames are used by Hytale's built-in providers:

| Icon | Used By | Purpose |
|------|---------|---------|
| `Spawn.png` | `SpawnMarkerProvider` | World spawn point |
| `Death.png` | `DeathMarkerProvider` | Player death location |
| `Home.png` | `RespawnMarkerProvider` | Respawn/bed location |
| `Player.png` | `OtherPlayersMarkerProvider` | Other player positions |
| `Portal.png` | `PortalMarkerProvider` | Portal exit points |
| `Warp.png` | `TeleportPlugin` | Warp points |
| `Prefab.png` | `PrefabEditSession`, worldgen | Points of interest / prefabs |
| `User1.png` | `WorldMapManager` | Default user-placed marker |
| `PlayerAbove.png` | `OtherPlayersMarkerProvider` | Height delta: player is above marker |
| `PlayerBelow.png` | `OtherPlayersMarkerProvider` | Height delta: player is below marker |

## Marker Providers

### WorldMapManager.MarkerProvider Interface

```java
public interface MarkerProvider {
    void update(@Nonnull World world, @Nonnull Player player, @Nonnull MarkersCollector collector);
}
```

The engine calls `update()` on every registered provider each tick (via `MapMarkerTracker.updatePointsOfInterest()`). Providers emit markers via the `MarkersCollector`, which handles diffing against what was previously sent to the client.

### Registering a Provider

```java
World world = player.getWorld();
WorldMapManager mapManager = world.getWorldMapManager();

// Register with a unique key
mapManager.addMarkerProvider("realmPortal", new RealmPortalMarkerProvider(mapData));
```

**Key:** The string key must be unique per world. Registering with the same key replaces the previous provider. There is no `removeMarkerProvider()` -- markers from removed providers stop appearing automatically because they are no longer emitted in `update()`, and the tracker removes stale markers.

### Built-in Providers

The `WorldMapManager` constructor registers seven default providers:

| Key | Provider | Description |
|-----|----------|-------------|
| `"spawn"` | `SpawnMarkerProvider` | World spawn point |
| `"playerIcons"` | `OtherPlayersMarkerProvider` | Other players in the world |
| `"death"` | `DeathMarkerProvider` | Player death markers |
| `"respawn"` | `RespawnMarkerProvider` | Respawn point (bed) |
| `"personal"` | `PersonalMarkersProvider` | Player's personal map markers |
| `"shared"` | `SharedMarkersProvider` | Shared user-placed markers |
| `"poi"` | `POIMarkerProvider` | Points of interest (worldgen) |

### MarkersCollector

```java
public interface MarkersCollector {
    void add(MapMarker marker);              // Only sent if within view distance
    void addIgnoreViewDistance(MapMarker marker); // Always sent regardless of distance
    boolean isInViewDistance(double x, double z);
}
```

- `add()` -- Checks if the marker position is within the player's current map view radius before sending.
- `addIgnoreViewDistance()` -- Always sends the marker. **Use this for important markers** (portals, objectives) that should always appear on the compass and map.
- `isInViewDistance()` -- Useful for pre-filtering expensive marker generation.

### Our Usage: RealmPortalMarkerProvider

```java
public class RealmPortalMarkerProvider implements WorldMapManager.MarkerProvider {

    @Override
    public void update(@Nonnull World world, @Nonnull Player player, @Nonnull MarkersCollector collector) {
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
}
```

## Map Marker Components

`MapMarkerComponent` is the abstract base class. There are four concrete subtypes, identified by a type ID during serialization:

| Type ID | Class | Purpose |
|---------|-------|---------|
| 0 | `PlayerMarkerComponent` | Associates marker with a player UUID |
| 1 | `PlacedByMarkerComponent` | Shows "placed by" name and player UUID |
| 2 | `HeightDeltaIconComponent` | Elevation arrows above/below |
| 3 | `TintComponent` | Colors the marker icon |

### TintComponent

Tints the marker icon with a color overlay. Takes a `Color(byte r, byte g, byte b)`:

```java
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.protocol.packets.worldmap.TintComponent;

new TintComponent(new Color((byte)0xFF, (byte)0xA5, (byte)0x00))  // orange tint
```

### HeightDeltaIconComponent

Shows a secondary icon when the player is significantly above or below the marker's Y position:

```java
import com.hypixel.hytale.protocol.packets.worldmap.HeightDeltaIconComponent;

new HeightDeltaIconComponent(
    12,                  // upDelta: blocks above before showing upImage
    "PlayerAbove.png",   // icon when player is above marker
    12,                  // downDelta: blocks below before showing downImage
    "PlayerBelow.png"    // icon when player is below marker
)
```

The delta values are in blocks. When the vertical distance exceeds the threshold, the secondary icon replaces or augments the primary marker icon on the map.

### PlayerMarkerComponent

Associates a marker with a specific player's UUID. Used by `OtherPlayersMarkerProvider` to link player markers to their identities:

```java
import com.hypixel.hytale.protocol.packets.worldmap.PlayerMarkerComponent;

new PlayerMarkerComponent(playerUuid)
```

### PlacedByMarkerComponent

Shows attribution for user-placed markers (who placed them):

```java
import com.hypixel.hytale.protocol.packets.worldmap.PlacedByMarkerComponent;

new PlacedByMarkerComponent(nameFormattedMessage, creatorUuid)
```

## Context Menu Items

Right-click a marker on the map to show a context menu. Each `ContextMenuItem` has a display name and a command that is executed when clicked:

```java
import com.hypixel.hytale.protocol.packets.worldmap.ContextMenuItem;

new ContextMenuItem("View Realm Info", "/too realm info")
```

- `name` -- Display text in the right-click menu.
- `command` -- Server command executed when the option is clicked. Must start with `/`.

**Note on our usage:** `RealmPortalMarkerProvider` uses context menu items, but `SkillTreeMapRenderer` found that context menus are not reliably supported on spoofed markers (markers sent via raw `UpdateWorldMap` packets without a provider). All built-in Hytale providers pass `null` for context menu items on markers created via the standard provider flow -- the context menu infrastructure appears to be handled by the `MapMarkerTracker` diffing system.

## Map Packets

### UpdateWorldMapSettings (Packet 240)

Configures the map UI behavior. Sent to individual players via `writeNoCache()` or to all players via `WorldMapTracker.sendSettings()`.

```java
public class UpdateWorldMapSettings implements Packet, ToClientPacket {
    public boolean enabled = true;                        // Whether the map is active
    @Nullable public Map<Short, BiomeData> biomeDataMap;  // Biome color/name mappings
    public boolean allowTeleportToCoordinates;             // Allow coordinate teleport (right-click)
    public boolean allowTeleportToMarkers;                 // Allow marker teleport
    public boolean allowShowOnMapToggle;                   // Allow toggling player visibility
    public boolean allowCompassTrackingToggle;             // Allow compass tracking toggle
    public boolean allowCreatingMapMarkers;                // Allow user-placed markers
    public boolean allowRemovingOtherPlayersMarkers;       // Allow deleting others' markers
    public float defaultScale = 32.0f;                    // Default zoom level
    public float minScale = 2.0f;                         // Minimum zoom (most zoomed in)
    public float maxScale = 256.0f;                       // Maximum zoom (most zoomed out)
}
```

**Scale values:** The scale is a float where higher = more zoomed out. Hytale clamps to `[minScale, maxScale]`. The defaults (2.0 -- 256.0) represent a wide zoom range. For custom overlays, use tighter ranges (e.g., `minScale=2.0, maxScale=32.0`).

**Teleport permissions:** Even when `allowTeleportToCoordinates`/`allowTeleportToMarkers` are set `true` in the packet, `WorldMapTracker.sendSettings()` additionally checks:
- Player is NOT in `GameMode.Adventure`
- Player has `hytale.world_map.teleport.coordinate` / `hytale.world_map.teleport.marker` permission

```java
// Send settings directly
UpdateWorldMapSettings settings = new UpdateWorldMapSettings();
settings.enabled = true;
settings.defaultScale = 4.0f;
settings.minScale = 2.0f;
settings.maxScale = 32.0f;
settings.allowTeleportToCoordinates = false;
settings.allowTeleportToMarkers = false;
playerRef.getPacketHandler().writeNoCache(settings);

// Or re-send world defaults
tracker.sendSettings(world);
```

### UpdateWorldMap (Packet 241)

The main data packet. Sends map tile images and marker updates. **Compressed** on the wire.

```java
public class UpdateWorldMap implements Packet, ToClientPacket {
    @Nullable public MapChunk[] chunks;           // Map tile images to add/update
    @Nullable public MapMarker[] addedMarkers;    // Markers to add or update
    @Nullable public String[] removedMarkers;     // Marker IDs to remove
}
```

All three fields are nullable. You can send markers only, chunks only, or both:

```java
// Markers only (our skill tree approach)
UpdateWorldMap packet = new UpdateWorldMap();
packet.chunks = null;
packet.addedMarkers = markers;
packet.removedMarkers = null;
playerRef.getPacketHandler().writeNoCache(packet);

// Chunks only (normal map rendering)
UpdateWorldMap packet = new UpdateWorldMap(chunkArray, null, null);
player.getPlayerConnection().write(packet);

// Remove specific markers
UpdateWorldMap packet = new UpdateWorldMap();
packet.removedMarkers = new String[] { "marker_1", "marker_2" };
playerRef.getPacketHandler().writeNoCache(packet);
```

**Size limit:** The engine enforces a maximum packet frame size of `MAX_FRAME = 2,621,440` bytes. The `WorldMapTracker` splits large chunk updates into multiple packets to stay under this limit.

### ClearWorldMap (Packet 242)

Resets all map state on the client (clears all chunks and markers):

```java
import com.hypixel.hytale.protocol.packets.worldmap.ClearWorldMap;

player.getPlayerConnection().write(new ClearWorldMap());
```

This is an empty packet (no fields). Sent automatically by `WorldMapTracker.clear()` and `setViewRadiusOverride()`.

### UpdateWorldMapVisible (Packet 243) -- Client-to-Server Only

**This is NOT a server-to-client packet.** The client sends this to notify the server when the player opens or closes the map UI. Do not try to send it from the server.

```java
public class UpdateWorldMapVisible implements Packet, ToServerPacket {
    public boolean visible;  // true when map is opened, false when closed
}
```

### Teleport Packets (Client-to-Server)

- `TeleportToWorldMapMarker` (Packet 244): Client requests teleport to a marker by ID.
- `TeleportToWorldMapPosition` (Packet 245): Client requests teleport to map coordinates (x, y integers).

These are received by the server and handled by `WorldMapManager`. The teleport only executes if the relevant `allowTeleportTo*` setting is enabled and the player has permission.

### Opening/Closing the Map UI

Use `SetPage` to programmatically open or close the map:

```java
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.protocol.packets.interface_.SetPage;

// Open map
playerRef.getPacketHandler().writeNoCache(new SetPage(Page.Map, true));

// Close map (return to no page)
playerRef.getPacketHandler().writeNoCache(new SetPage(Page.None, true));
```

## Map Rendering (MapChunk / MapImage)

### MapChunk

A map tile at a specific chunk coordinate, containing an optional image:

```java
public class MapChunk {
    public int chunkX;            // Chunk X coordinate
    public int chunkZ;            // Chunk Z coordinate
    @Nullable public MapImage image;  // Pixel data (null = unload this chunk)
}
```

Sending a `MapChunk` with `image = null` tells the client to unload that tile.

### MapImage

Raw pixel data for a map tile:

```java
public class MapImage {
    public int width;             // Image width in pixels
    public int height;            // Image height in pixels
    @Nullable public int[] data;  // ARGB pixel array (row-major, length = width * height)
}
```

The default image size is determined by `WorldMapSettings.imageScale`: `size = floor(32 * imageScale)`. With the default `imageScale = 0.5`, each chunk image is 16x16 pixels. With `imageScale = 1.0`, images are 32x32.

```java
// Create a chunk with custom pixel data
MapChunk chunk = new MapChunk();
chunk.chunkX = 0;
chunk.chunkZ = 0;
chunk.image = new MapImage();
chunk.image.width = 32;
chunk.image.height = 32;
chunk.image.data = new int[32 * 32];
Arrays.fill(chunk.image.data, 0xFF1A1A2E);  // Dark blue background
```

**Pixel format:** Each `int` in the data array is an ARGB color value (alpha in the high byte).

## WorldMapManager (Server-Side)

The per-world map manager lives at `world.getWorldMapManager()`. It:

- Holds the `MarkerProvider` registry
- Caches generated `MapImage` tiles
- Manages the `IWorldMap` generator (produces terrain images from worldgen data)
- Ticks `WorldMapTracker` for each player in the world

### Key Methods

```java
WorldMapManager mapManager = world.getWorldMapManager();

// Register a custom marker provider
mapManager.addMarkerProvider("myKey", myProvider);

// Get the world map settings
WorldMapSettings settings = mapManager.getWorldMapSettings();

// Check if the map is enabled
boolean enabled = mapManager.isWorldMapEnabled();

// Force settings re-send to all players
mapManager.sendSettings();

// Get/generate a map image for a chunk (async)
CompletableFuture<MapImage> future = mapManager.getImageAsync(chunkX, chunkZ);

// Clear all cached images
mapManager.clearImages();
```

### Compass vs Map

The `WorldMapManager` tick loop has two independent subsystems:

1. **Compass markers** -- Updated via `MapMarkerTracker.updatePointsOfInterest()` when `world.isCompassUpdating()` is `true`. This drives both the compass HUD and map markers.
2. **Map chunks** -- Updated via `WorldMapTracker.updateWorldMap()` when `isWorldMapEnabled()` is `true`. This loads/unloads terrain tile images.

**Key insight:** `world.setCompassUpdating(false)` stops ALL marker providers from running. This is how our `SkillTreeMapManager` prevents vanilla markers from interfering with the skill tree overlay.

## WorldMapTracker (Per-Player)

Each `Player` has a `WorldMapTracker` accessible via `player.getWorldMapTracker()`. It tracks which chunks and markers have been sent to the client.

### Key Methods

```java
WorldMapTracker tracker = player.getWorldMapTracker();

// Send current world's map settings to this player
tracker.sendSettings(world);

// Clear all sent chunks and markers, sends ClearWorldMap packet
tracker.clear();

// Override the view radius (null to restore default)
// NOTE: This also calls clear() internally
tracker.setViewRadiusOverride(0);        // Prevents any chunk loading
tracker.setViewRadiusOverride(null);     // Restores normal behavior

// Get the effective view radius
int radius = tracker.getEffectiveViewRadius(world);

// Filter which players appear on this player's map
tracker.setPlayerMapFilter(otherPlayer -> shouldHide(otherPlayer));
```

### View Radius Override

`setViewRadiusOverride(Integer)` is the primary mechanism for controlling what a player sees on the map:

- `setViewRadiusOverride(0)` -- Prevents the tracker from loading ANY terrain chunks. Also calls `clear()` to remove existing chunks. Use this before sending custom map content.
- `setViewRadiusOverride(null)` -- Removes the override, restoring the world's default view radius. Also calls `clear()`.

## Packet Spoofing Pattern (Skill Tree Overlay)

Our `SkillTreeMapManager` demonstrates the full pattern for hijacking the map UI:

### Opening a Custom Map Overlay

```java
public void openMap(PlayerRef player) {
    Player playerEntity = /* ... resolve ... */;
    World world = playerEntity.getWorld();
    WorldMapTracker tracker = playerEntity.getWorldMapTracker();

    // 1. Stop all vanilla marker providers (world-level)
    world.setCompassUpdating(false);

    // 2. Stop chunk loading (player-level, also clears existing map)
    tracker.setViewRadiusOverride(0);

    // 3. Send custom settings
    UpdateWorldMapSettings settings = new UpdateWorldMapSettings();
    settings.enabled = true;
    settings.defaultScale = 4.0f;
    settings.minScale = 2.0f;
    settings.maxScale = 32.0f;
    settings.allowTeleportToCoordinates = false;
    settings.allowTeleportToMarkers = false;
    player.getPacketHandler().writeNoCache(settings);

    // 4. Send custom markers (positioned relative to player)
    MapMarker[] markers = generateCustomMarkers(playerX, playerZ);
    UpdateWorldMap mapUpdate = new UpdateWorldMap();
    mapUpdate.addedMarkers = markers;
    player.getPacketHandler().writeNoCache(mapUpdate);

    // 5. Open the map UI
    player.getPacketHandler().writeNoCache(new SetPage(Page.Map, true));
}
```

### Closing and Restoring

```java
public void closeMap(PlayerRef player) {
    // 1. Close map UI
    player.getPacketHandler().writeNoCache(new SetPage(Page.None, true));

    // 2. Restore view radius (removes override, calls clear())
    tracker.setViewRadiusOverride(null);

    // 3. Restore compass updating (re-enables marker providers)
    world.setCompassUpdating(true);

    // 4. Re-sync world settings
    tracker.sendSettings(world);
}
```

### Multiplayer Safety

`world.setCompassUpdating()` is world-level -- it affects ALL players. If multiple players can have the overlay open simultaneously, use reference counting:

```java
// Track open count per world
private final Map<World, AtomicInteger> worldOpenCount = new ConcurrentHashMap<>();
private final Map<World, Boolean> originalCompassState = new ConcurrentHashMap<>();

// On open:
int count = worldOpenCount.computeIfAbsent(world, k -> new AtomicInteger(0)).incrementAndGet();
if (count == 1) {
    originalCompassState.put(world, world.isCompassUpdating());
    world.setCompassUpdating(false);
}

// On close:
int remaining = counter.decrementAndGet();
if (remaining <= 0) {
    worldOpenCount.remove(world);
    Boolean original = originalCompassState.remove(world);
    world.setCompassUpdating(original != null ? original : true);
}
```

## BiomeData

Biome color and name information sent with `UpdateWorldMapSettings`:

```java
public class BiomeData {
    public int zoneId;             // Zone identifier
    @Nullable public String zoneName;    // Zone display name
    @Nullable public String biomeName;   // Biome display name
    public int biomeColor;         // ARGB color for this biome on the map
}
```

The `biomeDataMap` field on `UpdateWorldMapSettings` maps biome IDs (`Short`) to `BiomeData`. This controls how biomes are colored on the map.

## Key Imports

```java
// Packet classes
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.protocol.packets.worldmap.MapChunk;
import com.hypixel.hytale.protocol.packets.worldmap.MapImage;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarkerComponent;
import com.hypixel.hytale.protocol.packets.worldmap.TintComponent;
import com.hypixel.hytale.protocol.packets.worldmap.HeightDeltaIconComponent;
import com.hypixel.hytale.protocol.packets.worldmap.PlayerMarkerComponent;
import com.hypixel.hytale.protocol.packets.worldmap.PlacedByMarkerComponent;
import com.hypixel.hytale.protocol.packets.worldmap.ContextMenuItem;
import com.hypixel.hytale.protocol.packets.worldmap.UpdateWorldMap;
import com.hypixel.hytale.protocol.packets.worldmap.UpdateWorldMapSettings;
import com.hypixel.hytale.protocol.packets.worldmap.ClearWorldMap;
import com.hypixel.hytale.protocol.packets.worldmap.BiomeData;

// Server-side classes
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapSettings;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MapMarkerBuilder;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MarkersCollector;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MapMarkerTracker;

// Supporting types
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.protocol.FormattedMessage;
import com.hypixel.hytale.protocol.Transform;       // packet-side transform
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.math.vector.Transform;    // server-side transform (MapMarkerBuilder)
import com.hypixel.hytale.server.core.Message;      // for MapMarkerBuilder.withName()
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.protocol.packets.interface_.SetPage;
```

## WorldMapConfig (New in 2026.03.26)

Configuration for world map view radius, split into server-wide and per-world settings.

**Classes:**
- `ServerWorldMapConfig` (`com.hypixel.hytale.server.core.config`): Server defaults (min=1, max=512 chunks)
- `WorldWorldMapConfig` (`com.hypixel.hytale.server.core.config`): Per-world override (min=3, max=32 chunks)
- `WorldMapConfig` (`com.hypixel.hytale.server.core.asset.type.gameplay`): Abstract base

**Methods:** `getViewRadiusMin()`, `getViewRadiusMax()`, `setViewRadiusMin(int)`, `setViewRadiusMax(int)`

---

## Edge Cases and Gotchas

### Two Transform Classes

`MapMarkerBuilder` accepts `com.hypixel.hytale.math.vector.Transform` (server-side). Raw `MapMarker.transform` is `com.hypixel.hytale.protocol.Transform` (packet-side). The builder converts via `PositionUtil.toTransformPacket()`. When constructing `MapMarker` directly, you must use the protocol `Transform`:

```java
import com.hypixel.hytale.protocol.Transform;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.Direction;

marker.transform = new Transform(
    new Position(x, y, z),
    new Direction(yaw, pitch, roll)
);
```

### UpdateWorldMapVisible is Client-to-Server

`UpdateWorldMapVisible` (Packet 243) implements `ToServerPacket`, NOT `ToClientPacket`. The client sends it to notify the server when the player opens/closes the map. You cannot use it to force-show the map from the server -- use `SetPage(Page.Map, true)` instead.

### writeNoCache() vs write()

The engine's `WorldMapTracker` uses `write()` for normal map updates, which goes through the packet cache. For spoofed packets (custom overlay), use `writeNoCache()` to bypass the cache and ensure immediate delivery:

```java
playerRef.getPacketHandler().writeNoCache(packet);  // Custom overlays
player.getPlayerConnection().write(packet);          // Normal engine path
```

### Marker Diffing and Small Movements

The `MapMarkerTracker` only resends a marker when something meaningful changes:
- Name or custom name changed
- Yaw changed by > 0.05 (or > 0.001 during the 10-second "small movements" window)
- Position moved more than 5 blocks (or > 0.1 blocks during small movements)

This means rapidly updating marker positions with tiny changes may not be reflected on the client until the small movements timer fires (every 10 seconds).

### setViewRadiusOverride() Calls clear()

`WorldMapTracker.setViewRadiusOverride()` **always** calls `clear()` internally, which sends a `ClearWorldMap` packet. This means you do not need to manually send `ClearWorldMap` before overriding the view radius.

### Compass Updating is World-Level

`world.setCompassUpdating(false)` stops marker providers for ALL players in that world, not just one. Always use reference counting in multiplayer scenarios (see the pattern in the Packet Spoofing section above).

### Markers Positioned Relative to Player

When spoofing markers via raw packets, the map is always centered on the player's position. To make markers appear at specific map locations, position them relative to the player's world coordinates:

```java
double markerWorldX = playerX + offsetX;
double markerWorldZ = playerZ + offsetZ;
```

### No removeMarkerProvider()

There is no `WorldMapManager.removeMarkerProvider()` method. The provider registry is `Map<String, MarkerProvider>` -- you can only add or replace providers. When a provider is no longer needed, its markers automatically disappear because `update()` stops emitting them, and the `MapMarkerTracker` removes stale entries on the next diff cycle.

## Reference

| Source File | Description |
|------------|-------------|
| `src/main/java/.../maps/instance/RealmPortalMarkerProvider.java` | Our MarkerProvider implementation |
| `src/main/java/.../skilltree/map/SkillTreeMapManager.java` | Packet spoofing for custom map overlay |
| `src/main/java/.../skilltree/map/SkillTreeMapRenderer.java` | Generating MapChunks and MapMarkers |
| Decompiled: `protocol/packets/worldmap/` | All worldmap packet classes |
| Decompiled: `server/core/universe/world/worldmap/` | WorldMapManager, WorldMapSettings |
| Decompiled: `server/core/universe/world/WorldMapTracker.java` | Per-player map tracking |
| Decompiled: `server/core/universe/world/worldmap/markers/` | MarkerProvider, MapMarkerBuilder, collector |
| Decompiled: `server/core/universe/world/worldmap/markers/providers/` | Built-in provider implementations |
