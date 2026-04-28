# Hytale API Verification Report

This document verifies the Realms system architecture against actual decompiled Hytale APIs.

---

## Executive Summary

| Area | Status | Notes |
|------|--------|-------|
| **Instance Management** | ✅ VERIFIED | Full API available via `InstancesPlugin` |
| **Player Teleportation** | ✅ VERIFIED | ECS-based `Teleport` component system |
| **World Generation** | ⚠️ PARTIAL | Template-based only, not fully procedural |
| **Portal Mechanics** | ⚠️ DIFFERENT | Block interaction, NOT collision-based |
| **Entity Spawning** | ✅ VERIFIED | `CommandBuffer.addEntity()` pattern |
| **World Boundaries** | ⚠️ PARTIAL | Worldgen bounds, no runtime enforcement |
| **Auto-Cleanup** | ✅ VERIFIED | `RemovalCondition` system |

---

## 1. Instance Management

### Documentation Assumption
```java
// We assumed:
InstancesPlugin.spawnInstance(name, worldConfig)
```

### Actual API (VERIFIED ✅)

**Class:** `com.hypixel.hytale.builtin.instances.InstancesPlugin`

```java
// Primary methods (ACTUAL):
public CompletableFuture<World> spawnInstance(
    @Nonnull String name,           // Instance template name
    @Nonnull World forWorld,        // Current world (for return point UUID)
    @Nonnull Transform returnPoint  // Where player returns on exit
)

public CompletableFuture<World> spawnInstance(
    @Nonnull String name,
    @Nullable String worldName,     // Optional: explicit world name
    @Nonnull World forWorld,
    @Nonnull Transform returnPoint
)
```

### Key Differences
1. **Template-based:** Instances must be pre-defined as asset templates at `Assets/Server/Instances/{name}/`
2. **Return point required:** Must specify where players return on exit
3. **File copying:** Instance files are copied to `worlds/instance-{name}-{uuid}/`
4. **Naming convention:** Generated name is `instance-{safeName}-{randomUUID}`

### ⚠️ IMPACT ON DESIGN
Our procedural generation design needs adjustment:
- **Cannot generate terrain at runtime** with pure code
- **Must create instance templates** for each biome type
- Templates can have **parameterized generation** via WorldGen V2 assets

---

## 2. Player Teleportation

### Documentation Assumption
```java
// We assumed:
InstancesPlugin.teleportPlayerToInstance(player, world, position)
```

### Actual API (VERIFIED ✅)

**Class:** `com.hypixel.hytale.builtin.instances.InstancesPlugin`

```java
// Teleport to LOADING instance (world not yet ready)
public static void teleportPlayerToLoadingInstance(
    @Nonnull Ref<EntityStore> entityRef,
    @Nonnull ComponentAccessor<EntityStore> componentAccessor,
    @Nonnull CompletableFuture<World> worldFuture,
    @Nullable Transform overrideReturn
)

// Teleport to EXISTING instance (world already loaded)
public static void teleportPlayerToInstance(
    @Nonnull Ref<EntityStore> playerRef,
    @Nonnull ComponentAccessor<EntityStore> componentAccessor,
    @Nonnull World targetWorld,
    @Nullable Transform overrideReturn
)

// Exit instance (return to original world)
public static void exitInstance(
    @Nonnull Ref<EntityStore> targetRef,
    @Nonnull ComponentAccessor<EntityStore> componentAccessor
)
```

### ECS Teleport Component
**Class:** `com.hypixel.hytale.server.core.modules.entity.teleport.Teleport`

```java
// For programmatic teleportation within code:
public Teleport(@Nullable World world, @Nonnull Vector3d position, @Nonnull Vector3f rotation)
public Teleport(@Nonnull Vector3d position, @Nonnull Vector3f rotation)  // Same world

// Builder pattern
public Teleport withHeadRotation(@Nonnull Vector3f headRotation)
public Teleport withResetRoll()
public Teleport withoutVelocityReset()
```

### ✅ MATCHES DESIGN
The teleportation API matches our architecture. Key notes:
- Use `teleportPlayerToLoadingInstance()` when spawning new instance
- Use `exitInstance()` for automatic return handling
- Return points tracked via `InstanceEntityConfig`

---

## 3. World Generation

### Documentation Assumption
We assumed we could generate terrain procedurally at runtime using density fields.

### Actual API (PARTIAL ⚠️)

**World generation is ASSET-DRIVEN, not runtime code:**

1. **IWorldGen Interface**
```java
public interface IWorldGen {
    CompletableFuture<GeneratedChunk> generate(
        int chunkX, long seed, int chunkZ, int dimension, LongPredicate stillNeeded
    );
    Transform[] getSpawnPoints(int seed);
}
```

2. **Density System** - Defined via JSON assets, not Java code:
```
Assets/Server/HytaleGenerator/
├── Density/          # Density node trees (JSON)
├── Biomes/           # Biome definitions (JSON)
├── MaterialProviders/ # Block placement rules (JSON)
├── WorldStructures/  # Top-level configs (JSON)
└── Props/            # Object placement (JSON)
```

3. **Key Classes:**
- `Density` (abstract) - Base for terrain math
- `SimplexNoise2dDensity`, `CylinderDensity`, `SphereDensity`, etc.
- `BiomeType` - Interface for biome definitions
- `MaterialProvider` - Block placement logic
- `NStagedChunkGenerator` - Pipeline executor

### ⚠️ MAJOR IMPACT ON DESIGN

**Original Plan:** Generate unique arena terrain per-realm in code
**Reality:** Must define **instance templates as JSON assets**

**Solutions:**
1. **Pre-define biome templates** in asset packs (recommended)
2. **Parameterize templates** using seed + config variables
3. **Use asset packing** to bundle realm templates with plugin

### Revised Approach

```
Assets/Server/Instances/
├── realm_forest_small/
│   ├── instance.bson        # WorldConfig with seed param
│   └── worldgen/            # Custom worldgen assets
├── realm_forest_medium/
├── realm_desert_small/
├── realm_volcano_large/
└── ...
```

Each template uses **parameterized density fields** for variety:
- Seed controls noise patterns
- Size parameters control arena dimensions
- Same biome, infinite variations

---

## 4. Portal Mechanics

### Documentation Assumption
We assumed portals would detect player entry via collision/proximity.

### Actual API (DIFFERENT ⚠️)

**Portals use BLOCK INTERACTION (right-click), not collision:**

**Class:** `com.hypixel.hytale.builtin.portals.interactions.EnterPortalInteraction`

```java
// Portal entry is triggered by interactWithBlock():
protected void interactWithBlock(
    @Nonnull World world,
    @Nonnull CommandBuffer<EntityStore> commandBuffer,
    @Nonnull InteractionType type,
    @Nonnull InteractionContext context,
    @Nullable ItemStack itemInHand,
    @Nonnull Vector3i targetBlock,
    @Nonnull CooldownHandler cooldownHandler
) {
    // Validate minimum time in world (3 seconds)
    // Get PortalDevice from block
    // Check destination world exists
    // Teleport player
}
```

### Portal Components

```java
// PortalDevice - Block component storing portal state
public class PortalDevice implements Component<ChunkStore> {
    private PortalDeviceConfig config;
    private String baseBlockTypeKey;
    private UUID destinationWorldUuid;

    public World getDestinationWorld()
    public void setDestinationWorld(World world)
}

// PortalDeviceConfig - Portal configuration
public class PortalDeviceConfig {
    private String onState = "Active";
    private String spawningState = "Spawning";
    private String offState = "default";
    private String returnBlock;
}
```

### ⚠️ IMPACT ON DESIGN

**Options for realm entry:**

1. **Use existing portal system** (recommended)
   - Create portal block when map is activated
   - Player right-clicks to enter
   - Consistent with Hytale's design

2. **Create custom interaction**
   - Implement our own interaction handler
   - Can use collision if we create a custom system

3. **Use TeleportInstanceInteraction**
   - Built-in interaction that spawns + teleports to instance
   - Can be attached to any block

### Recommended Design Change

```
Map activation flow (REVISED):
1. Player uses Realm Map item
2. System spawns instance (async)
3. Create portal BLOCK at player location (not entity)
4. Player right-clicks portal to enter
5. Portal block has PortalDevice component pointing to instance
```

---

## 5. Entity Spawning

### Documentation Assumption
```java
// We assumed:
world.spawnEntity(mobType, position)
```

### Actual API (VERIFIED ✅)

**Pattern:** Create Holder → Add Components → Add to Store

```java
// 1. Get store from world
Store<EntityStore> store = world.getEntityStore().getStore();

// 2. Create entity holder
Holder<EntityStore> holder = store.getRegistry().newHolder();

// 3. Add required components
holder.addComponent(NetworkId.getComponentType(),
    new NetworkId(store.getExternalData().takeNextNetworkId()));

holder.addComponent(TransformComponent.getComponentType(),
    new TransformComponent(position, rotation));

// 4. Add to world
Ref<EntityStore> ref = store.addEntity(holder, AddReason.SPAWN);

// OR via CommandBuffer (in systems):
Ref<EntityStore> ref = commandBuffer.addEntity(holder, AddReason.SPAWN);
```

### NPC Spawning Helper

```java
// NPCPlugin provides convenience method:
Pair<Ref<EntityStore>, INonPlayerCharacter> result =
    NPCPlugin.get().spawnNPC(
        store,              // Entity store
        npcType,            // Role/template name
        groupType,          // Optional flock group
        position,           // Vector3d
        rotation            // Vector3f
    );
```

### ✅ MATCHES DESIGN
Entity spawning works as expected. Key components needed:
- `TransformComponent` - Position and rotation
- `NetworkId` - Network synchronization
- `UUIDComponent` - Persistence (auto-generated if missing)

---

## 6. World Boundaries

### Documentation Assumption
We assumed built-in boundary enforcement for arena walls.

### Actual API (PARTIAL ⚠️)

**WorldBounds exists but is for WORLDGEN, not runtime enforcement:**

```java
public class WorldBounds extends ChunkBounds implements IWorldBounds {
    protected int minY, maxY;
    // Used for chunk generation boundaries
}
```

### ⚠️ NO BUILT-IN RUNTIME BOUNDARY ENFORCEMENT

Players can walk beyond worldgen bounds if chunks exist.

### Solutions for Arena Boundaries

1. **Physical walls** (recommended)
   - Generate actual block walls in arena template
   - Use indestructible blocks (bedrock, barrier)

2. **Teleport enforcement**
   - Custom system that teleports players back when crossing boundary
   - Check position each tick, apply `Teleport` component if outside

3. **Void below**
   - Arena template with void/death below platform
   - Natural boundary enforcement

### Recommended Design

Arena templates should include **physical boundary walls**:
```
┌─────────────────────────────┐
│ ██████████████████████████ │ ← Barrier block walls (height 15+)
│ █                        █ │
│ █    PLAYABLE ARENA      █ │
│ █                        █ │
│ █                        █ │
│ ██████████████████████████ │
└─────────────────────────────┘
```

---

## 7. Auto-Cleanup / Instance Removal

### Documentation Assumption
```java
// We assumed:
realm.setIdleTimeout(90)  // seconds
realm.setDeleteOnEmpty(true)
```

### Actual API (VERIFIED ✅)

**RemovalCondition System:**

```java
// Interface
public interface RemovalCondition {
    boolean shouldRemoveWorld(Store<ChunkStore> store);
}

// Built-in conditions:
public class WorldEmptyCondition implements RemovalCondition {
    private double timeoutSeconds = 300.0;  // 5 min default
    // Triggers when world had players, now empty, timeout elapsed
}

public class IdleTimeoutCondition implements RemovalCondition {
    private double timeoutSeconds = 300.0;  // 5 min default
    // Triggers when no players for timeout period
}

public class TimeoutCondition implements RemovalCondition {
    private double timeoutSeconds = 300.0;  // 5 min default
    // Triggers after absolute time from creation
}
```

**Configuration:**

```java
InstanceWorldConfig config = InstanceWorldConfig.ensureAndGet(worldConfig);
config.setRemovalConditions(
    new WorldEmptyCondition(90.0),      // 90 sec after last player leaves
    new TimeoutCondition(600.0)         // 10 min absolute max
);
```

**Manual cleanup trigger:**

```java
// Mark for removal when empty (doesn't delete immediately)
InstancesPlugin.safeRemoveInstance(worldName);

// Force removal (may teleport players out first)
Universe.get().removeWorld(worldName);
```

### ✅ MATCHES DESIGN
The removal system is more flexible than we assumed. We can:
- Combine multiple conditions (AND logic)
- Create custom conditions
- Set absolute time limits + empty-world timeouts

---

## 8. Required Design Changes

### 8.1 Instance Creation (MAJOR CHANGE)

**Before:** Procedural code-based generation
**After:** Template-based with parameterization

```
// Create instance templates as assets:
Assets/Server/Instances/
├── realm_forest/
│   ├── instance.bson           # Base config
│   └── worldgen/
│       ├── ChunkGenerator.json # References parameterized density
│       └── Density/
│           └── arena.json      # Uses ${arena_radius}, ${seed}
├── realm_desert/
├── realm_volcano/
└── ...
```

### 8.2 Portal System (MEDIUM CHANGE)

**Before:** Custom collision-based portal entity
**After:** Block-based portal using Hytale's system

```java
// On map activation:
1. Spawn instance async
2. Wait for instance ready
3. Place portal BLOCK at player location
4. Set PortalDevice.destinationWorld to instance
5. Player right-clicks to enter
```

### 8.3 Boundary Enforcement (MINOR CHANGE)

**Before:** Assumed API boundary enforcement
**After:** Physical walls in templates + optional teleport system

### 8.4 Entity Spawning (NO CHANGE)

Our design matches the actual API.

---

## 9. Revised Architecture

### Package Structure (Updated)

```
io.github.larsonix.trailoforbis.maps/
├── RealmsManager.java              # Main coordinator
├── api/
│   └── RealmsService.java          # Public API
├── config/
│   ├── RealmsConfig.java           # Main config
│   └── RealmTemplateRegistry.java  # NEW: Maps biome+size to template
├── core/
│   ├── RealmInstance.java          # Active realm state
│   ├── RealmMapData.java           # Map item data
│   └── RealmState.java             # Lifecycle enum
├── instance/
│   ├── RealmInstanceFactory.java   # UPDATED: Uses InstancesPlugin
│   ├── RealmPortalManager.java     # UPDATED: Block-based portals
│   └── RealmRemovalHandler.java    # Uses RemovalCondition system
├── spawning/
│   ├── RealmMobSpawner.java        # Uses CommandBuffer.addEntity()
│   └── RealmSpawnCalculator.java   # Post-gen spawn point finder
└── listeners/
    ├── RealmMapUseListener.java    # Map activation
    └── RealmPortalInteraction.java # NEW: Custom portal interaction
```

### Instance Creation Flow (Updated)

```
┌─────────────────────────────────────────────────────────────────┐
│                 REVISED INSTANCE CREATION FLOW                   │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. Player uses Realm Map                                       │
│       │                                                         │
│       ▼                                                         │
│  2. Select template from registry                               │
│     RealmTemplateRegistry.getTemplate(biome, size)              │
│     → Returns "realm_forest_medium"                             │
│       │                                                         │
│       ▼                                                         │
│  3. Configure removal conditions                                │
│     RemovalCondition[] = {                                      │
│         new WorldEmptyCondition(config.idleTimeout),            │
│         new TimeoutCondition(config.maxDuration)                │
│     }                                                           │
│       │                                                         │
│       ▼                                                         │
│  4. Spawn instance (InstancesPlugin)                            │
│     InstancesPlugin.get().spawnInstance(                        │
│         templateName,                                           │
│         currentWorld,                                           │
│         returnTransform                                         │
│     )                                                           │
│       │                                                         │
│       ▼                                                         │
│  5. Wait for instance ready                                     │
│       │                                                         │
│       ▼                                                         │
│  6. Place portal BLOCK at player location                       │
│     PortalDevice.setDestinationWorld(instance)                  │
│       │                                                         │
│       ▼                                                         │
│  7. Spawn monsters at calculated positions                      │
│     CommandBuffer.addEntity(holder, AddReason.SPAWN)            │
│       │                                                         │
│       ▼                                                         │
│  8. Player right-clicks portal to enter                         │
│     EnterPortalInteraction handles teleport                     │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 10. Asset Template Requirements

### Required Instance Templates

We need to create the following asset templates:

| Template Name | Biome | Size | Arena Dimensions |
|---------------|-------|------|------------------|
| `realm_forest_small` | Forest | Small | 50x50 |
| `realm_forest_medium` | Forest | Medium | 100x100 |
| `realm_forest_large` | Forest | Large | 200x200 |
| `realm_desert_small` | Desert | Small | 50x50 |
| `realm_desert_medium` | Desert | Medium | 100x100 |
| `realm_desert_large` | Desert | Large | 200x200 |
| `realm_volcano_small` | Volcano | Small | 50x50 |
| `realm_volcano_medium` | Volcano | Medium | 100x100 |
| `realm_volcano_large` | Volcano | Large | 200x200 |
| ... | ... | ... | ... |

**Total:** 11 biomes × 4 sizes = **44 templates**

Each template includes:
- `instance.bson` - WorldConfig with removal conditions
- `worldgen/` - Custom biome/terrain for arena
- Pre-placed boundary walls
- Spawn point definitions

### Template Parameterization

Templates can use **seed-based variation**:
```json
// In density asset:
{
  "type": "SimplexNoise2d",
  "seed": "${world_seed}",      // Varies per instance
  "frequency": 0.02,
  "amplitude": 8.0
}
```

This gives infinite visual variety while using fixed templates.

---

## 11. Implementation Priority

### Phase 1: Core Infrastructure
1. ✅ RealmMapData and item system
2. ⬜ RealmTemplateRegistry (maps biome+size to template name)
3. ⬜ Basic instance factory using InstancesPlugin
4. ⬜ Portal block creation system

### Phase 2: Template Creation
1. ⬜ Create 1 test template (forest_medium)
2. ⬜ Validate worldgen parameterization
3. ⬜ Create remaining 43 templates (can be batched)

### Phase 3: Gameplay Systems
1. ⬜ Monster spawning with CommandBuffer
2. ⬜ Completion tracking
3. ⬜ Reward distribution
4. ⬜ Timer/timeout handling

### Phase 4: Polish
1. ⬜ UI integration
2. ⬜ Stone system integration
3. ⬜ Analytics/logging

---

## 12. Open Questions

1. **Template bundling:** How do we package instance templates with the plugin JAR?
2. **Asset hot-reload:** Can we modify templates without server restart?
3. **Performance:** What's the overhead of file-copying per instance spawn?
4. **Custom portal blocks:** Do we need to register a new block type or can we use existing?

---

## Conclusion

The Hytale API is **well-suited** for the Realms system with some adjustments:

- ✅ Instance management is robust and matches our needs
- ✅ Teleportation works as expected
- ✅ Entity spawning is straightforward
- ✅ Auto-cleanup is more flexible than expected
- ⚠️ World generation requires template assets, not runtime code
- ⚠️ Portals are block-based interactions, not collision

**Primary design change:** Shift from procedural code generation to parameterized template assets. This is actually more maintainable and performant, though it requires upfront asset creation work.
