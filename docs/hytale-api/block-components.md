# Hytale Block Components (ChunkStore ECS)

Use this skill when working with block-level ECS data — attaching persistent state to blocks, reading/writing block components, changing block states, and using world-level resource singletons. This covers the `ChunkStore` side of Hytale's ECS, as opposed to the `EntityStore` used for entities/players.

> **Related skills:** For EntityStore ECS (entities, players), see `docs/hytale-api/ecs.md`. For block interactions, see `docs/hytale-api/interactions.md`. For spawning entities, see `docs/hytale-api/spawning-entities.md`.

---

## Quick Reference

| Task | Approach |
|------|----------|
| Register a block component | `getChunkStoreRegistry().registerComponent(Class, "Name", CODEC)` in `setup()` |
| Register a world resource | `getChunkStoreRegistry().registerResource(Class, "Name", CODEC)` in `setup()` |
| Register a ChunkStore system | `getChunkStoreRegistry().registerSystem(new MySystem())` in `setup()` |
| Get a block ref from position | `world.getChunk(...).getBlockComponentEntity(x, y, z)` |
| Get position from block ref | `BlockStateInfo` + `BlockChunk` + `ChunkUtil` |
| Read a block component | `store.getComponent(blockRef, MyComponent.getComponentType())` |
| Access a world resource | `commandBuffer.getResource(MyResource.getResourceType())` |
| Change block visual state | `BlockType.getBlockKeyForState("Active")` → `chunk.setBlock(...)` |
| Send event to a block | `store.invoke(blockRef, new MyEvent())` |

---

## Concepts

### EntityStore vs ChunkStore

Hytale has two parallel ECS stores:

| Store | Manages | Components On | Registered Via |
|-------|---------|---------------|----------------|
| `EntityStore` | Entities (players, NPCs, projectiles) | Entity refs | `getEntityStoreRegistry()` |
| `ChunkStore` | Blocks (block entities) | Block refs | `getChunkStoreRegistry()` |

Both use the same `Component<T>`, `Ref<T>`, `Store<T>`, `ComponentType<T>`, and `BuilderCodec<T>` patterns. The type parameter `T` is either `EntityStore` or `ChunkStore`.

### Block Entities

A "block entity" is a block that has ECS components attached. Defined in the item's JSON:

```json
{
  "BlockType": {
    "BlockEntity": {
      "Components": {
        "MyComponent": {
          "Field1": "value",
          "Field2": 42
        }
      }
    }
  }
}
```

When the block is placed, Hytale creates a `Ref<ChunkStore>` for it and deserializes the components from JSON using their `BuilderCodec`.

---

## Registering Block Components

### Component Class

Block components implement `Component<ChunkStore>`:

```java
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

public class MyBlockComponent implements Component<ChunkStore> {

    public static final BuilderCodec<MyBlockComponent> CODEC = BuilderCodec.builder(MyBlockComponent.class, MyBlockComponent::new)
        .append(new KeyedCodec<>("Range", Codec.DOUBLE), (self, val) -> self.range = val, self -> self.range)
        .add()
        .append(new KeyedCodec<>("Active", Codec.BOOLEAN), (self, val) -> self.active = val, self -> self.active)
        .add()
        .build();

    static ComponentType<ChunkStore, MyBlockComponent> componentType;

    public double range = 5.0;
    public boolean active = false;

    public static ComponentType<ChunkStore, MyBlockComponent> getComponentType() {
        return componentType;
    }

    public static void setComponentType(ComponentType<ChunkStore, MyBlockComponent> c) {
        componentType = c;
    }

    @Override
    public Component<ChunkStore> clone() {
        MyBlockComponent copy = new MyBlockComponent();
        copy.range = this.range;
        copy.active = this.active;
        return copy;
    }
}
```

### Registration

In your plugin's `setup()`:

```java
@Override
protected void setup() {
    MyBlockComponent.setComponentType(
        this.getChunkStoreRegistry().registerComponent(
            MyBlockComponent.class,
            "MyBlockComponent",      // Must match JSON key in BlockEntity.Components
            MyBlockComponent.CODEC
        )
    );
}
```

### In Item JSON

Reference the component name (matches the registration string):

```json
{
  "BlockType": {
    "BlockEntity": {
      "Components": {
        "MyBlockComponent": {
          "Range": 10.0,
          "Active": false
        }
      }
    }
  }
}
```

---

## Accessing Block Components

### Getting a Block Ref from World Position

```java
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

public static Ref<ChunkStore> getBlockRef(World world, Vector3i pos) {
    WorldChunk chunk = world.getChunk(ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
    if (chunk == null) return null;
    return chunk.getBlockComponentEntity(pos.x, pos.y, pos.z);
}
```

### Getting Position from a Block Ref

```java
import com.hypixel.hytale.server.core.modules.block.BlockModule.BlockStateInfo;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;

public static Vector3i getPosForBlock(Ref<ChunkStore> ref, ComponentAccessor<ChunkStore> store) {
    BlockStateInfo blockstate = store.getComponent(ref, BlockStateInfo.getComponentType());
    if (blockstate == null) return null;

    BlockChunk blockchunk = store.getComponent(blockstate.getChunkRef(), BlockChunk.getComponentType());
    int x = ChunkUtil.worldCoordFromLocalCoord(blockchunk.getX(), ChunkUtil.xFromBlockInColumn(blockstate.getIndex()));
    int y = ChunkUtil.yFromBlockInColumn(blockstate.getIndex());
    int z = ChunkUtil.worldCoordFromLocalCoord(blockchunk.getZ(), ChunkUtil.zFromBlockInColumn(blockstate.getIndex()));
    return new Vector3i(x, y, z);
}
```

### Reading/Writing Component Data

```java
// Get the ChunkStore
Store<ChunkStore> store = world.getChunkStore().getStore();

// Get block ref
Ref<ChunkStore> blockRef = getBlockRef(world, blockPos);

// Read component
MyBlockComponent comp = store.getComponent(blockRef, MyBlockComponent.getComponentType());
if (comp != null) {
    double range = comp.range;
    comp.active = true;  // Direct mutation — persisted automatically via codec
}
```

**Important**: Unlike EntityStore where you sometimes need `replaceComponent()`, ChunkStore components can be mutated directly because the component reference stays valid.

---

## World-Level Resources

Resources are **per-world singletons** stored in the ChunkStore. They persist across server restarts.

### Resource Class

```java
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;

public class MyWorldData implements Resource<ChunkStore> {

    public static final BuilderCodec<MyWorldData> CODEC = BuilderCodec.builder(MyWorldData.class, MyWorldData::new)
        .append(new KeyedCodec<>("Active", Codec.BOOLEAN), (r, v) -> r.active = v, r -> r.active)
        .add()
        .build();

    static ResourceType<ChunkStore, MyWorldData> resourceType;

    public boolean active = false;

    public static ResourceType<ChunkStore, MyWorldData> getResourceType() { return resourceType; }
    public static void setResourceType(ResourceType<ChunkStore, MyWorldData> r) { resourceType = r; }

    @Override
    public Resource<ChunkStore> clone() {
        return new MyWorldData();
    }
}
```

### Registration

```java
MyWorldData.setResourceType(
    this.getChunkStoreRegistry().registerResource(
        MyWorldData.class, "MyWorldData", MyWorldData.CODEC
    )
);
```

### Access

```java
// From a CommandBuffer (inside a system)
MyWorldData data = commandBuffer.getResource(MyWorldData.getResourceType());

// From a Store
MyWorldData data = store.getResource(MyWorldData.getResourceType());
```

---

## Block State Changes

Hytale blocks can have multiple visual states defined in their item JSON. Each state can override `DrawType`, `Material`, `HitboxType`, and `Interactions`.

### Defining States in Item JSON

```json
{
  "BlockType": {
    "State": {
      "Definitions": {
        "Normal": {
          "Interactions": {
            "Use": {
              "Interactions": [{ "Type": "OpenCustomUI", "Page": { "Id": "MyBlock" } }]
            }
          }
        },
        "Active": {
          "DrawType": "Empty",
          "HitboxType": "Block_Flat",
          "Material": "Empty"
        }
      }
    },
    "Material": "Solid",
    "DrawType": "Model"
  }
}
```

### Changing State Programmatically

```java
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;

public static boolean setStateForBlock(String newState, World world, Vector3i pos) {
    WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
    if (chunk == null) return false;

    BlockType current = chunk.getBlockType(pos);
    if (current == null) return false;

    // Get the block key for the target state (e.g., "MyBlock:Active")
    String newBlockKey = current.getBlockKeyForState(newState);
    if (newBlockKey == null) return false;

    // Resolve block ID and type
    int newBlockId = BlockType.getAssetMap().getIndex(newBlockKey);
    BlockType newBlockType = BlockType.getAssetMap().getAsset(newBlockId);

    // Preserve rotation during state change
    int rotation = world.getBlockRotationIndex(pos.x, pos.y, pos.z);

    // Apply the state change (198 = block update flags)
    chunk.setBlock(pos.getX(), pos.getY(), pos.getZ(), newBlockId, newBlockType, rotation, 0, 198);
    return true;
}
```

### Reading Current State

```java
public static String getStateForBlock(Vector3i pos, World world) {
    WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
    if (chunk == null) return "default";

    BlockType current = chunk.getBlockType(pos);
    if (current == null) return "default";

    String state = current.getStateForBlock(current);
    return state != null ? state : "default";
}
```

---

## ChunkStore Systems

Register systems that operate on block entities:

```java
// In setup()
this.getChunkStoreRegistry().registerSystem(new MyBlockSystem());
```

### Ticking System (runs every frame)

```java
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;

public class MyBlockSystem extends EntityTickingSystem<ChunkStore> {
    @Override
    public void tick(float v, int i, ArchetypeChunk<ChunkStore> chunk,
                     Store<ChunkStore> store, CommandBuffer<ChunkStore> commandBuffer) {
        MyBlockComponent comp = chunk.getComponent(i, MyBlockComponent.getComponentType());
        Vector3i pos = getPosForBlock(chunk.getReferenceTo(i), commandBuffer);
        // ... logic
    }

    @Override
    public Query<ChunkStore> getQuery() {
        return MyBlockComponent.getComponentType();
    }
}
```

### Event System (reacts to EcsEvents)

```java
import com.hypixel.hytale.component.system.EntityEventSystem;

public class MySignalHandler extends EntityEventSystem<ChunkStore, SignalEvent> {
    public MySignalHandler() { super(SignalEvent.class); }

    @Override
    public void handle(int i, ArchetypeChunk<ChunkStore> chunk,
                       Store<ChunkStore> store, CommandBuffer<ChunkStore> commandBuffer,
                       SignalEvent event) {
        // Handle signal on this block entity
    }

    @Override
    public Query<ChunkStore> getQuery() {
        return Query.and(new Query[]{ MyBlockComponent.getComponentType(), SignalSender.getComponentType() });
    }
}
```

### Ref System (reacts to entity add/remove)

```java
import com.hypixel.hytale.component.system.RefSystem;

public class MyBlockTracker extends RefSystem<ChunkStore> {
    @Override
    public void onEntityAdded(Ref<ChunkStore> ref, AddReason reason,
                               Store<ChunkStore> store, CommandBuffer<ChunkStore> commandBuffer) {
        // Block placed — register it
    }

    @Override
    public void onEntityRemove(Ref<ChunkStore> ref, RemoveReason reason,
                                Store<ChunkStore> store, CommandBuffer<ChunkStore> commandBuffer) {
        // Block broken — unregister it
    }

    @Override
    public Query<ChunkStore> getQuery() {
        return MyBlockComponent.getComponentType();
    }
}
```

### Global Ticking System (no entity query)

```java
import com.hypixel.hytale.component.system.tick.TickingSystem;

public class MyGlobalSystem extends TickingSystem<ChunkStore> {
    @Override
    public void tick(float v, int i, Store<ChunkStore> store) {
        // Runs once per tick, no entity iteration
        MyWorldData data = store.getResource(MyWorldData.getResourceType());
    }
}
```

---

## Sending Events to Block Entities

Dispatch an `EcsEvent` to a specific block ref:

```java
// Target a specific block
store.invoke(blockRef, new MyEvent());

// The event will be handled by any EntityEventSystem<ChunkStore, MyEvent>
// whose query matches the target block's archetype
```

---

## Block Rotation

Blocks support rotational variants (`VariantRotation: "NESW"` in JSON):

```java
// Read rotation index (0-3 for NESW)
int rot = world.getBlockRotationIndex(pos.x, pos.y, pos.z);

// Apply rotation to a local offset
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
Vector3i globalOffset = RotationTuple.get(rot).rotate(localOffset.toVector3d()).toVector3i();
globalOffset.add(blockPosition);  // Convert to world coordinates
```

---

## Key Imports

```java
// ChunkStore ECS
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

// Block utilities
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.modules.block.BlockModule.BlockStateInfo;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;

// Codec
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
```

---

## Reference

- Source: Verified against Hytale server source and community mod implementations
- Related: [ECS Systems Guide](https://hytalemodding.dev/en/docs/guides/ecs/systems)
- Related: `docs/hytale-api/ecs.md` (EntityStore ECS)
- Related: `docs/hytale-api/interactions.md` (block interactions)

> **TrailOfOrbis project notes:** We currently use EntityStore exclusively. ChunkStore components are available for future block-based features (portal blocks, switches, realm beacons).
