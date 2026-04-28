# Hytale Entity Tracking & NetworkId

The entity tracking system manages which entities each player can see and assigns unique integer identifiers (NetworkId) used by the protocol layer. Every entity that needs to be visible to clients must have a `NetworkId` component. The tracker determines per-player visibility based on spatial distance, then sends entity state updates only to players who can see that entity.

TrailOfOrbis uses this system in two key places:
- **Skill Sanctum**: `SkillSanctumConnectionRenderer` reads `NetworkId` from node entities to send `BuilderToolLaserPointer` beam packets.
- **Combat Indicators**: `CombatIndicatorService` reads `EntityTrackerSystems.EntityViewer` to queue `CombatTextUpdate` and `UIComponentsUpdate` on the attacker's viewer for the defender entity.

## Quick Reference

```java
// Read an entity's NetworkId
NetworkId networkId = store.getComponent(ref, NetworkId.getComponentType());
int id = networkId.getId();  // -1 means unassigned

// Assign a NetworkId when spawning an entity via ECS Holder
holder.addComponent(NetworkId.getComponentType(),
    new NetworkId(store.getExternalData().takeNextNetworkId()));

// Look up an entity by NetworkId
Ref<EntityStore> ref = world.getEntityStore().getRefFromNetworkId(networkId);

// Check if a player can see a specific entity
EntityTrackerSystems.EntityViewer viewer =
    store.getComponent(playerRef, EntityTrackerSystems.EntityViewer.getComponentType());
boolean canSee = viewer != null && viewer.visible.contains(targetRef);

// Queue a component update for a visible entity (on the player's viewer)
viewer.queueUpdate(targetRef, new CombatTextUpdate(hitAngle, "42"));
```

## NetworkId Component

`NetworkId` is an ECS `Component<EntityStore>` that holds a single `int id`. It uniquely identifies an entity within a world for protocol purposes (entity update packets, targeted effects, etc.).

```java
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
```

### Structure

```java
public final class NetworkId implements Component<EntityStore> {
    private final int id;

    public static ComponentType<EntityStore, NetworkId> getComponentType();
    public NetworkId(int id);
    public int getId();  // returns -1 if unassigned
}
```

### How Entities Get NetworkIds

NetworkIds are assigned via `EntityStore.takeNextNetworkId()`, which returns a monotonically increasing integer (starting from 1) using an `AtomicInteger` counter. There are two paths:

**1. Legacy entity spawning** (deprecated `Entity` constructor):
```java
// Entity.loadIntoWorld() calls:
this.networkId = world.getEntityStore().takeNextNetworkId();
```

**2. ECS Holder spawning** (current pattern):
```java
Holder<EntityStore> holder = store.getRegistry().newHolder();
holder.addComponent(NetworkId.getComponentType(),
    new NetworkId(store.getExternalData().takeNextNetworkId()));
// ... add other components ...
store.addEntity(holder, AddReason.SPAWNED);
```

All Hytale spawn systems (spawn markers, mounts, suppression zones, builder tools) use the ECS pattern.

### NetworkIdSystem (Automatic Bookkeeping)

`EntityStore.NetworkIdSystem` is a built-in `RefSystem<EntityStore>` that maintains a bidirectional `networkId <-> Ref` mapping inside `EntityStore`:

- **onEntityAdded**: Registers the entity's `NetworkId` in the `networkIdToRef` map. If the ID is already taken (collision from deserialization), it assigns a fresh one via `takeNextNetworkId()`.
- **onEntityRemove**: Removes the entity's ID from the `networkIdToRef` map.

This means you can look up entities by network ID:

```java
// Reverse lookup: NetworkId -> entity Ref
Ref<EntityStore> ref = world.getEntityStore().getRefFromNetworkId(someNetworkId);
```

### Reading NetworkId from an Entity

```java
Store<EntityStore> store = world.getEntityStore().getStore();
NetworkId networkId = store.getComponent(entityRef, NetworkId.getComponentType());
if (networkId != null && networkId.getId() != -1) {
    int id = networkId.getId();
    // Use id for packets
}
```

From our codebase (`SkillSanctumConnectionRenderer`):

```java
private int getEntityNetworkId(
        SkillSanctumInstance instance, Store<EntityStore> store, String nodeId) {
    Ref<EntityStore> ref = instance.getNodeEntity(nodeId);
    if (ref == null || !ref.isValid()) return -1;
    if (ref.getStore() != store) return -1;

    NetworkId networkId = store.getComponent(ref, NetworkId.getComponentType());
    return networkId != null ? networkId.getId() : -1;
}
```

## EntityTrackerSystems

`EntityTrackerSystems` is the core ECS system group that manages entity visibility for each player ("viewer"). It runs every tick as part of the entity store's tick pipeline.

```java
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
```

### Key Inner Types

| Type | Purpose |
|------|---------|
| `EntityViewer` | Component on player entities. Holds `visible` set, `sent` map, `updates` queue, and `viewRadiusBlocks`. |
| `Visible` | Component on entities that are currently seen by at least one viewer. Tracks `visibleTo`, `previousVisibleTo`, `newlyVisibleTo` maps. |
| `EntityUpdate` | Per-entity update container with thread-safe `queueRemove()` and `queueUpdate()` methods. |
| `CollectVisible` | Tick system that spatially collects entities within `viewRadiusBlocks` of each viewer. |
| `AddToVisible` | Tick system that populates each entity's `Visible.visibleTo` from viewer lists. |
| `SendPackets` | Tick system that builds `EntityUpdates` packets from queued updates and sends them to each viewer. |

### System Pipeline (per tick)

1. **ClearEntityViewers** -- Clears each viewer's `visible` set from the previous tick.
2. **CollectVisible** -- Spatial query: collects all entities within `viewRadiusBlocks` of each viewer's position into `visible`.
3. **LegacyHideFromEntity** -- Removes hidden entities (e.g., hidden players) from `visible`.
4. **LegacyLODCull** -- Removes entities that are too small relative to distance (LOD culling).
5. **ClearPreviouslyVisible** -- Swaps `visibleTo`/`previousVisibleTo` on each `Visible` component.
6. **EnsureVisibleComponent** -- Adds `Visible` component to newly visible entities.
7. **AddToVisible** -- Populates `Visible.visibleTo` and `newlyVisibleTo`.
8. **RemoveEmptyVisibleComponent** -- Removes `Visible` from entities no longer seen by anyone.
9. **QUEUE_UPDATE_GROUP** -- Systems that queue component updates (model, equipment, skin, effects, combat text).
10. **SendPackets** -- Serializes queued updates into `EntityUpdates` packets and sends to each viewer.

### EntityViewer Component

`EntityViewer` is the viewer-side component, added to every player entity. Key fields:

```java
public static class EntityViewer implements Component<EntityStore> {
    public int viewRadiusBlocks;                             // View distance in blocks
    public IPacketReceiver packetReceiver;                   // Player's network connection
    public Set<Ref<EntityStore>> visible;                    // Entities visible THIS tick
    public Map<Ref<EntityStore>, EntityUpdate> updates;      // Queued updates to send
    public Object2IntMap<Ref<EntityStore>> sent;             // Entity Ref -> NetworkId mapping for sent entities
    public int lodExcludedCount;                             // LOD-culled entity count this tick
    public int hiddenCount;                                  // Hidden entity count this tick
}
```

The view radius is set from the player's render distance: `viewRadiusBlocks = playerComponent.getViewRadius() * 32` (chunks to blocks).

### Queuing Updates on a Viewer

To send entity-specific data (combat text, UI component swaps, etc.) to a specific player, queue an update on their `EntityViewer`:

```java
EntityTrackerSystems.EntityViewer viewer =
    store.getComponent(attackerRef, EntityTrackerSystems.EntityViewer.getComponentType());

if (viewer != null && viewer.visible.contains(defenderRef)) {
    // Queue a combat text floating number
    viewer.queueUpdate(defenderRef, new CombatTextUpdate(hitAngle, "42"));

    // Queue a UI component swap (e.g., colored combat text template)
    viewer.queueUpdate(defenderRef, new UIComponentsUpdate(modifiedComponentIds));
}
```

The update is batched with other pending updates and sent in the next `SendPackets` tick as part of an `EntityUpdates` packet.

**The `visible.contains()` check is mandatory.** `queueUpdate()` throws `IllegalArgumentException("Entity is not visible!")` if the target entity is not in the viewer's visible set.

### Static Utility Methods

```java
// Force-despawn all tracked entities for a viewer (sends removal packet)
EntityTrackerSystems.despawnAll(viewerRef, store);

// Clear a viewer's sent map (without sending removal packets)
EntityTrackerSystems.clear(viewerRef, store);
```

## Entity Visibility

The server determines which entities each player can see using a spatial query. Every tick:

1. Each player's `EntityViewer` has a `viewRadiusBlocks` (typically `viewRadiusChunks * 32`).
2. `CollectVisible` queries the `NetworkSendableSpatialResource` (a spatial index of all network-sendable entities) around the player's position.
3. Entities within range are added to `EntityViewer.visible`.
4. Post-collection filters remove hidden entities (LOD culling, `isHiddenFromLivingEntity`).

An entity must have both a `NetworkId` component and be registered in the network-sendable spatial index to be visible to players. Standard entity spawn paths handle this automatically.

### View Distance

Default view radius comes from the player's client render distance setting:

```java
// Set by client:
entityViewerComponent.viewRadiusBlocks = playerComponent.getViewRadius() * 32;

// Override via command:
/player viewradius set <chunks>
```

## Sending Entity-Targeted Packets

Several packets require a `NetworkId` to target a specific entity on the client. The client validates that the referenced ID corresponds to an entity it is currently tracking -- packets with unknown IDs are silently discarded.

### BuilderToolLaserPointer

Renders a colored beam between two 3D points, anchored to a network entity. Used by our `SkillSanctumConnectionRenderer` for skill tree connection beams.

```java
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolLaserPointer;

BuilderToolLaserPointer packet = new BuilderToolLaserPointer(
    entityNetworkId,       // int: NetworkId of a tracked entity
    startX, startY, startZ, // float: beam start position
    endX, endY, endZ,       // float: beam end position
    0xFF7755,               // int: packed 0xRRGGBB color
    5000                    // int: duration in milliseconds
);

playerRef.getPacketHandler().write(packet);
```

**Critical**: The `playerNetworkId` field (first parameter) must be a real `NetworkId` from an entity the target player is currently tracking. See the gotchas section below.

**Client beam limit**: The client has a hard limit of ~50 `LaserPointerInstance` objects in its beam pool. Each unique `playerNetworkId` occupies one slot. Our sanctum renderer budgets 24 beams per refresh and sorts by proximity to stay within this limit.

### SpawnModelParticles

Spawns particle effects attached to a specific entity's model.

```java
import com.hypixel.hytale.protocol.packets.entities.SpawnModelParticles;

SpawnModelParticles packet = new SpawnModelParticles(
    entityNetworkId,    // int: NetworkId of the target entity
    modelParticles      // ModelParticle[]: particle definitions
);
```

### CombatTextUpdate (via EntityViewer)

Combat text is not sent as a direct packet -- it is queued as a component update on the attacker's `EntityViewer` for the defender entity. The tracker system batches it into an `EntityUpdates` packet.

```java
// From CombatIndicatorService:
EntityTrackerSystems.EntityViewer entityViewer =
    store.getComponent(attackerRef, EntityTrackerSystems.EntityViewer.getComponentType());

if (entityViewer != null && entityViewer.visible.contains(defenderRef)) {
    CombatTextUpdate combatTextUpdate = new CombatTextUpdate(hitAngle, "42");
    entityViewer.queueUpdate(defenderRef, combatTextUpdate);
}
```

## Key Imports

```java
// NetworkId component
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;

// Tracker system and inner types (EntityViewer, Visible, EntityUpdate)
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;

// Legacy tracker systems (equipment, model, skin updates)
import com.hypixel.hytale.server.core.modules.entity.tracker.LegacyEntityTrackerSystems;

// Entity store (for takeNextNetworkId, getRefFromNetworkId)
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

// ECS core
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.ComponentType;

// Packets that use NetworkId
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolLaserPointer;
import com.hypixel.hytale.protocol.packets.entities.SpawnModelParticles;
import com.hypixel.hytale.protocol.CombatTextUpdate;
```

## Edge Cases & Gotchas

### Client Validates NetworkId Against Tracked Entities

**This is the most important gotcha.** The client maintains its own set of "known" entities (those it received via `EntityUpdates` spawn packets). When a packet references a `playerNetworkId`, the client checks it against this set. If the ID is unknown, the packet is **silently discarded** -- no error, no log, just nothing happens.

This means:
- You cannot use arbitrary IDs (0, 1, 2, ...) for `BuilderToolLaserPointer` beams.
- You must use the actual `NetworkId` from an entity the player is currently tracking.
- The entity must be within the player's view radius and have been sent in a previous `EntityUpdates` packet.

Our `SkillSanctumConnectionRenderer` solves this by reading the `NetworkId` from the beam's endpoint node entities (which are nearby and therefore tracked):

```java
int netId = resolveUniqueNetId(instance, store, visual.nodeIdA, visual.nodeIdB, usedNetIds);
if (netId == -1) {
    skippedNoId++;
    continue;  // Neither endpoint entity has a resolvable NetworkId
}
```

### Delay After World Load (~2.5s)

Packets sent during the client's world loading phase are lost. The client discards entity-targeted packets until it finishes loading the new world's chunk data. Our sanctum renderer waits `CLIENT_READY_DELAY_MS = 2500` milliseconds after the first tick before sending any beams:

```java
private static final long CLIENT_READY_DELAY_MS = 2500;

// In tickProximityRefresh():
if (firstTickTimestamp == 0) {
    firstTickTimestamp = System.currentTimeMillis();
}
if ((System.currentTimeMillis() - firstTickTimestamp) < CLIENT_READY_DELAY_MS) {
    return;  // Client not ready yet
}
```

This applies to any entity-targeted packet, not just laser beams. If you teleport a player to a new world and immediately send `SpawnModelParticles` or `BuilderToolLaserPointer`, the packets will be lost.

### One Beam Per NetworkId

The client stores one `LaserPointerInstance` per `playerNetworkId`. Sending a second `BuilderToolLaserPointer` with the same ID overwrites the first beam. Our renderer uses a `usedNetIds` set to ensure each beam gets a unique ID, falling back to reuse (which overwrites) if both endpoint entities have been claimed.

### NetworkId Is Assigned by the System, Not by You

You provide an initial ID via `new NetworkId(store.getExternalData().takeNextNetworkId())`, but the `EntityStore.NetworkIdSystem` may reassign it if the ID collides (e.g., from deserialized entities). Always read the component's value after the entity is in the store rather than caching the initial value.

### visible.contains() Is Required Before queueUpdate()

`EntityViewer.queueUpdate()` throws `IllegalArgumentException("Entity is not visible!")` if the target entity ref is not in the viewer's `visible` set. Always check:

```java
if (viewer.visible.contains(defenderRef)) {
    viewer.queueUpdate(defenderRef, update);
}
```

### Thread Safety

- `EntityViewer.updates` is a `ConcurrentHashMap` -- safe for parallel update queueing.
- `EntityUpdate` uses `StampedLock` internally for its `removed` and `updates` lists.
- `Visible.addViewerParallel()` uses a `StampedLock` for concurrent writes from multiple viewer ticks.
- `EntityViewer.sent` is a plain `Object2IntOpenHashMap` -- only accessed from the viewer's own tick (single-threaded per viewer).

The tick systems run with `isParallel = true`, meaning multiple viewers can queue updates to the same entity's `Visible` component concurrently. The locking in `addViewerParallel()` and `EntityUpdate` handles this.

### Store Reference Validation

When reading `NetworkId` from an entity, verify the entity's store matches your current store. Cross-store refs are stale after world changes:

```java
if (ref.getStore() != store) return -1;  // Stale ref from a different world
```

## Reference

### Decompiled Source Files

| Class | Location |
|-------|----------|
| `NetworkId` | `com/hypixel/hytale/server/core/modules/entity/tracker/NetworkId.java` |
| `EntityTrackerSystems` | `com/hypixel/hytale/server/core/modules/entity/tracker/EntityTrackerSystems.java` |
| `LegacyEntityTrackerSystems` | `com/hypixel/hytale/server/core/modules/entity/tracker/LegacyEntityTrackerSystems.java` |
| `EntityStore` (NetworkIdSystem) | `com/hypixel/hytale/server/core/universe/world/storage/EntityStore.java` |
| `BuilderToolLaserPointer` | `com/hypixel/hytale/protocol/packets/buildertools/BuilderToolLaserPointer.java` |
| `SpawnModelParticles` | `com/hypixel/hytale/protocol/packets/entities/SpawnModelParticles.java` |

### TrailOfOrbis Usage

| File | Usage |
|------|-------|
| `SkillSanctumConnectionRenderer` | Reads `NetworkId` from node entities for `BuilderToolLaserPointer` beams |
| `SkillSanctumNodeSpawner` | Assigns `NetworkId` when spawning node item entities |
| `SkillSanctumInstance` | Uses `EntityTrackerSystems` for entity management |
| `CombatIndicatorService` | Reads `EntityViewer` to queue `CombatTextUpdate` on attacker for defender entity |
| `CombatTextColorManager` | Reads `EntityViewer` to queue `UIComponentsUpdate` for per-entity template swaps |
| `TooAdminTestColorCommand` | Test command that reads `EntityViewer` and `NetworkId` for combat text experiments |
| `TooAdminSanctumTestLineCommand` | Test command that sends `BuilderToolLaserPointer` packets using `NetworkId` |
