# World, Blocks, Instances, Portals, Map & World Gen

Definitive API reference for Hytale world operations, block manipulation, instance lifecycle, portal system, spatial queries, prefabs, world map, and world generation.

**Verified against:** Hytale server source, Loot4Everyone, and community mod implementations.

---

## World Operations

### Getting Worlds

```java
// All worlds (returns Map<UUID, World>)
Map<UUID, World> worlds = Universe.get().getWorlds();

// Specific world by UUID
World world = Universe.get().getWorld(worldUuid);
```

### World Properties

```java
world.isAlive();         // boolean - is world still valid
world.getName();         // String - world name
world.getPlayerRefs();   // player entity references in this world
world.getPlayers();      // player objects in this world
```

### World-Thread Dispatch

All world mutations must happen on the world thread. `World` implements `Executor`, so it works directly with `CompletableFuture`.

```java
// Direct dispatch
world.execute(() -> {
    // runs on world thread
    world.setBlock(x, y, z, blockTypeId);
});

// CompletableFuture integration (World is an Executor)
CompletableFuture.supplyAsync(() -> computeSomething())
    .thenAcceptAsync(result -> {
        // runs on world thread
        world.setBlock(x, y, z, result.blockId());
    }, world);
```

### Block Operations on World

```java
// Read
BlockType type = world.getBlockType(x, y, z);
int rotIdx     = world.getBlockRotationIndex(x, y, z);

// Write
world.setBlock(x, y, z, blockTypeId);

// Destroy (with break flags)
world.breakBlock(x, y, z, flags);

// Block state (cast to ItemContainerState for containers)
BlockState state = world.getState(x, y, z, true);
if (state instanceof ItemContainerState container) {
    // access container contents
}
```

### Chunk Access

```java
CompletableFuture<WorldChunk> future = world.getChunkAsync(chunkIndex); // async
WorldChunk chunk = world.getChunkIfInMemory(chunkIndex); // sync, null if unavailable
WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);   // sync, null if unavailable
```

### World Resources

```java
// World config
WorldConfig config = world.getWorldConfig();

// World map manager
WorldMapManager mapMgr = world.getWorldMapManager();

// Entity and chunk stores
world.getEntityStore();
world.getChunkStore();

// World time
WorldTimeResource timeRes = store.getResource(WorldTimeResource.getResourceType());
Instant gameTime = timeRes.getGameTime();
```

---

## BlockType API

### Lookup

```java
// By string ID
BlockType type = BlockType.fromString("Block_Stone");

// Via asset map
BlockType type = BlockType.getAssetMap().getAsset("Block_Stone");
int index      = BlockType.getAssetMap().getIndex("Block_Stone");

// Air / empty
BlockType air = BlockType.EMPTY;
```

### Properties

```java
type.getId();             // String - block identifier
type.getMaterial();        // BlockMaterial
type.getOpacity();        // opacity value
type.getDrawType();       // how the block renders
type.getCustomModel();    // custom model if any
type.getInteractions();   // interaction definitions
type.getInteractionHint();// F-key prompt text
type.getBlockEntity();    // associated block entity type
```

### Block Gathering (Drops)

```java
BlockGathering gathering = type.getGathering();
if (gathering != null) {
    var breaking = gathering.getBreaking();
    // breaking → drop type, item, quantity
}
```

### Block Center & State Machine

```java
Vec3d center = new Vec3d();
type.getBlockCenter(rotationIndex, center); // center for rotated block
type.getStateForBlock();                    // get state definition
type.getBlockKeyForState(blockState);       // reverse lookup
```

---

## Chunk Operations

### Coordinate Utilities

```java
long chunkIndex = ChunkUtil.indexChunkFromBlock(blockX, blockZ); // block → chunk index
long chunkIndex = ChunkUtil.indexChunk(chunkX, chunkZ);          // chunk coords → index
int chunkCoord  = ChunkUtil.chunkCoordinate(blockX);             // block → chunk coord
```

### WorldChunk API

```java
// Read
BlockType type = chunk.getBlockType(x, y, z);
BlockState state = chunk.getState(x, y, z);
int height = chunk.getHeight(x, z);    // heightmap
int tint   = chunk.getTint(x, z);      // biome tint

// Block component access
Ref<ChunkStore> ref = chunk.getBlockComponentEntity(x, y, z);
var holder = chunk.getBlockComponentHolder(x, y, z); // mutable

// Write
chunk.setBlock(x, y, z, blockId);
chunk.setTicking(x, y, z, true);  // enable tick updates
chunk.markNeedsSaving();

// Filler blocks (sub-block data)
int filler = chunk.getFiller(x, y, z);
int fx = FillerBlockUtil.unpackX(filler);
int fy = FillerBlockUtil.unpackY(filler);
int fz = FillerBlockUtil.unpackZ(filler);
```

### Block State Registry

```java
// Register custom block states
BlockStateRegistry.registerBlockState(/* ... */);

// Tickable block states implement TickableBlockState
public class MyBlockState implements TickableBlockState {
    // tick logic here
}
```

---

## Spatial Queries

### Spatial Resource Types

```java
// Get spatial resource types from EntityModule
var entitySpatial = EntityModule.get().getEntitySpatialResourceType();
var playerSpatial = EntityModule.get().getPlayerSpatialResourceType();
var itemSpatial   = EntityModule.get().getItemSpatialResourceType();
```

### Sphere & Box Collection

```java
// Sphere query
var spatialRes = store.getResource(entitySpatial);
List<Ref<?>> results = SpatialResource.getThreadLocalReferenceList();
spatialRes.getSpatialStructure().collect(center, radius, results);

// AABB query
spatialRes.getSpatialStructure().collectBox(min, max, results);
```

### Convenience Methods (TargetUtil)

```java
// All entities in sphere
List<Ref<?>> nearby = TargetUtil.getAllEntitiesInSphere(center, radius, store);

// Raycast to location (block or entity hit point)
TargetUtil.getTargetLocation(playerRef, range, store);

// Raycast to block
TargetUtil.getTargetBlock(playerRef, distance, callback);

// Raycast to entity
TargetUtil.getTargetEntity(playerRef, callback);

// Get player look direction (eye position + facing)
Transform look = TargetUtil.getLook(playerRef, store);
```

---

## Collision

### Static Collision Test

```java
CollisionResult result = new CollisionResult();
CollisionModule.findCollisions(boundingBox, position, velocity, result, store);
// result contains collision data (hit normal, penetration, etc.)
```

---

## Instance System

Instances are ephemeral worlds spawned from templates. Core lifecycle: spawn, teleport players in, run gameplay, exit players, destroy.

### Spawning an Instance

```java
CompletableFuture<World> future = InstancesPlugin.get().spawnInstance(
    instanceId,       // String - matches Server/Instances/<id>/
    originWorld,      // World - the world spawning came from
    returnTransform   // Transform - where players return on exit
);

future.thenAcceptAsync(instanceWorld -> {
    // instance is ready - configure and teleport players
}, originWorld);
```

### Teleporting Players

```java
InstancesPlugin.teleportPlayerToInstance(
    playerRef,        // Ref - player entity reference
    accessor,         // ComponentAccessor - component access
    instanceWorld,    // World - target instance world
    spawnTransform    // Transform - where to place the player
);
```

### Exiting an Instance

```java
// Returns player to their return transform
InstancesPlugin.exitInstance(playerRef, accessor);
```

### WorldConfig for Instances

```java
WorldConfig config = instanceWorld.getWorldConfig();

// Ephemeral behavior
config.setDeleteOnUniverseStart(true);  // clean on server restart
config.setDeleteOnRemove(true);         // clean when world removed

// Spawn providers
config.setSpawnProvider(new IndividualSpawnProvider(transform)); // per-player spawn
config.setSpawnProvider(new GlobalSpawnProvider());              // shared spawn

// Disable vanilla spawning
config.setSpawningNPC(false);
config.setIsSpawnMarkersEnabled(false);

// Check if a world is an instance
InstanceWorldConfig iwc = InstanceWorldConfig.get(config); // null if not instance
```

### Instance Template Structure

Instance templates live at `Server/Instances/<id>/`:

```
Server/Instances/realm_forest/
    instance.bson       # World data (terrain, placed blocks)
```

---

## Portal System

Portals are timed entry points that teleport players into instances.

### Portal Type Lookup

```java
// Get portal type definition
PortalType portalType = PortalType.getAssetMap().getAsset("Realm_Default");
String instanceId = portalType.getInstanceId();
```

### PortalWorld Resource

The `PortalWorld` resource manages an active portal within a world.

```java
// Get PortalWorld from a world's entity store
PortalWorld portalWorld = world.getEntityStore()
    .getStore()
    .getResource(PortalWorld.getResourceType());

// Initialize a portal
portalWorld.init(
    portalType,                  // PortalType
    durationSeconds,             // double - how long portal stays open
    PortalRemovalCondition.NONE, // when to auto-remove
    null                         // optional callback
);

// Configure
portalWorld.setSpawnPoint(transform);
portalWorld.setRemainingSeconds(world, 120.0);
```

### Safe Spawn Finding

```java
// Built-in spawn finder that avoids solid blocks
List<Vector3d> candidates = List.of(pos1, pos2, pos3);
Transform safe = PortalSpawnFinder.computeSpawnTransform(world, candidates);
```

### Chunk Pre-Loading Pattern

Before teleporting or placing structures, pre-load chunks to avoid operating on unloaded terrain:

```java
// Pre-load 3x3 chunk grid around target position
int cx = ChunkUtil.chunkCoordinate(targetX);
int cz = ChunkUtil.chunkCoordinate(targetZ);

List<CompletableFuture<WorldChunk>> futures = new ArrayList<>();
for (int dx = -1; dx <= 1; dx++) {
    for (int dz = -1; dz <= 1; dz++) {
        long idx = ChunkUtil.indexChunk(cx + dx, cz + dz);
        futures.add(world.getChunkAsync(idx));
    }
}

CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
    .thenRunAsync(() -> {
        // All chunks loaded - safe to operate
    }, world);
```

---

## Prefabs

Prefabs are saved block structures that can be loaded and pasted into worlds.

### Load & Paste

```java
// Load prefab from file
var buffer = PrefabBufferUtil.loadBuffer(path);
IPrefabBuffer access = buffer.newAccess();

// Paste into world
PrefabUtil.paste(buffer, world, position, rotation, /* ... */);

// Remove (undo paste)
PrefabUtil.remove(buffer, world, target, yaw, force, random, 0, 1.0);
```

### Snapshot & BlockSelection

```java
// Serialize/deserialize selections
BsonDocument doc = SelectionPrefabSerializer.serialize(selection);
BlockSelection selection = SelectionPrefabSerializer.deserialize(doc);

// BlockSelection operations
selection.place();                      // place blocks into world
selection.rotate(Axis.Y, degrees);      // rotate around Y axis
selection.cloneSelection();             // deep copy
selection.forEachBlock((x, y, z, type) -> { /* iterate */ });
```

---

## Spawn Suppression

Suppress vanilla mob spawning in a radius by creating an entity with `SpawnSuppressionComponent`:

```java
// Create entity with TransformComponent + SpawnSuppressionComponent
// The component's radius parameter controls the suppression area
// Useful for arenas, boss rooms, safe zones
```

This is the native Hytale mechanism -- no event cancellation needed.

---

## World Map

### WorldMapManager

```java
WorldMapManager mapMgr = world.getWorldMapManager();

// Add custom marker provider
mapMgr.addMarkerProvider("my_markers", myProvider);
```

### Custom Map Markers

```java
MapMarkerBuilder marker = new MapMarkerBuilder(
    markerId,     // unique ID
    icon,         // marker icon
    transform     // world position
);
marker.withName(Message.of("My Marker"));
MapMarker built = marker.build();
```

### Custom Map Provider & Anchor UI

```java
// Register custom world map provider
IWorldMapProvider.CODEC.register(/* priority, name, class, codec */);
// Implement IWorldMap interface for custom map rendering

// Inject custom content into the native map page
AnchorActionModule.get().register("MapServerContent", (builder, context) -> {
    // add custom UI elements
});
```

---

## World Generation

### Custom World Gen Provider

```java
// Register a custom worldgen provider (replaces or augments vanilla generation)
IWorldGenProvider.CODEC.register(
    Priority.NORMAL,
    "my_worldgen",
    MyWorldGenProvider.class,
    myCodec
);
```

### Chunk Generation

```java
// ChunkGenerator interface
CompletableFuture<GeneratedChunk> future = generator.generate(
    seed,           // long
    chunkIndex,     // long
    chunkX,         // int
    chunkZ,         // int
    stillNeeded     // what generation stages remain
);

// Set blocks in generated chunk
generatedChunk.setBlock(x, y, z, blockId, stateA, stateB);
```

### Biome Lookup

```java
// Get biome at world coordinates
var biomeResult = generator.getZoneBiomeResultAt(seed, worldX, worldZ);
```

---

## Common Patterns

### Instance Lifecycle (Spawn, Configure, Teleport, Exit)

```java
// Spawn → configure → pre-load chunks → teleport in
InstancesPlugin.get().spawnInstance("realm_forest", originWorld, returnTransform)
    .thenAcceptAsync(iw -> {
        WorldConfig cfg = iw.getWorldConfig();
        cfg.setDeleteOnUniverseStart(true);
        cfg.setDeleteOnRemove(true);
        cfg.setSpawningNPC(false);

        // Pre-load 3x3 chunks, then teleport (see Chunk Pre-Loading Pattern above)
        preLoadChunks(iw, spawnPos).thenRunAsync(() ->
            InstancesPlugin.teleportPlayerToInstance(
                playerRef, accessor, iw, spawnTransform), iw);
    }, originWorld);

// Exit: always hide() + remove() HUDs before world change
world.execute(() -> {
    hud.hide(); hud.remove();
    InstancesPlugin.exitInstance(playerRef, accessor);
});
```

### Block Raycast + Modify

```java
TargetUtil.getTargetBlock(playerRef, 10.0, (bx, by, bz) -> {
    if (!world.getBlockType(bx, by, bz).equals(BlockType.EMPTY))
        world.setBlock(bx, by, bz, newBlockTypeId);
});
```

---

## Key Gotchas

| Issue | Cause | Fix |
|-------|-------|-----|
| Block operations silently fail | Not on world thread | Wrap in `world.execute()` |
| Chunk is null | Not loaded yet | Use `getChunkAsync()` and wait for future |
| Instance spawn future never completes | Instance ID doesn't match template folder name | Check `Server/Instances/<id>/` exists |
| Player sees old world after teleport | HUD not cleaned up before world change | `hud.hide()` then `hud.remove()` before teleport |
| Spatial query returns stale data | Using wrong store | Get store from the correct world's entity store |
| `setBlock` ignored in unloaded chunk | Chunk not in memory | Pre-load with `getChunkAsync` first |
| Portal expires instantly | `durationSeconds` too low or `setRemainingSeconds` not called | Set reasonable duration after `init()` |
| Vanilla mobs spawn in instance | Forgot to disable | `config.setSpawningNPC(false)` + `setIsSpawnMarkersEnabled(false)` |

---

## API Quick Reference

| Operation | Method |
|-----------|--------|
| Get all worlds | `Universe.get().getWorlds()` |
| Get world by UUID | `Universe.get().getWorld(uuid)` |
| Run on world thread | `world.execute(runnable)` |
| Read block | `world.getBlockType(x, y, z)` |
| Write block | `world.setBlock(x, y, z, id)` |
| Break block | `world.breakBlock(x, y, z, flags)` |
| Block state | `world.getState(x, y, z, true)` |
| Async chunk | `world.getChunkAsync(index)` |
| Block → chunk index | `ChunkUtil.indexChunkFromBlock(x, z)` |
| Heightmap | `chunk.getHeight(x, z)` |
| Entities in sphere | `TargetUtil.getAllEntitiesInSphere(c, r, store)` |
| Raycast block | `TargetUtil.getTargetBlock(ref, dist, cb)` |
| Raycast entity | `TargetUtil.getTargetEntity(ref, cb)` |
| Player look | `TargetUtil.getLook(ref, store)` |
| Collision test | `CollisionModule.findCollisions(...)` |
| Spawn instance | `InstancesPlugin.get().spawnInstance(id, world, transform)` |
| Teleport to instance | `InstancesPlugin.teleportPlayerToInstance(ref, acc, world, t)` |
| Exit instance | `InstancesPlugin.exitInstance(ref, accessor)` |
| Portal init | `portalWorld.init(type, duration, condition, null)` |
| Safe spawn | `PortalSpawnFinder.computeSpawnTransform(world, candidates)` |
| Load prefab | `PrefabBufferUtil.loadBuffer(path)` |
| Paste prefab | `PrefabUtil.paste(buffer, world, pos, rot, ...)` |
| Map marker | `new MapMarkerBuilder(id, icon, transform).build()` |
| Register worldgen | `IWorldGenProvider.CODEC.register(...)` |
| Generate chunk | `generator.generate(seed, idx, x, z, needed)` |
| Biome at coord | `generator.getZoneBiomeResultAt(seed, x, z)` |
| World time | `store.getResource(WorldTimeResource.getResourceType()).getGameTime()` |
