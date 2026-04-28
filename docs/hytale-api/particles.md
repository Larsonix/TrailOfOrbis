# Hytale Particles

Use this doc when spawning particle effects in the world or attaching particles to entities. Covers both the `ParticleUtil` helper (world-space particles) and the `SpawnModelParticles` packet (entity-attached particles).

---

## Quick Reference

| Task | Approach |
|------|----------|
| Spawn particles at a position | `ParticleUtil.spawnParticleEffect(name, position, accessor)` |
| Spawn with custom color/scale | `ParticleUtil.spawnParticleEffect(name, pos, yaw, pitch, roll, scale, color, playerRefs, accessor)` |
| Spawn only for specific players | Pass explicit `List<Ref<EntityStore>> playerRefs` |
| Attach particles to an entity | `SpawnModelParticles` packet with `ModelParticle[]` |
| Find particle asset IDs | See [Vanilla Asset Catalog](../reference/vanilla-asset-catalog.md) |

---

## Required Imports

```java
// World-space particles (preferred)
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;

// Entity-attached particles (packet-based)
import com.hypixel.hytale.protocol.packets.entities.SpawnModelParticles;
import com.hypixel.hytale.protocol.ModelParticle;
import com.hypixel.hytale.protocol.EntityPart;

// Shared types
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.protocol.WorldParticle;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.Vector3f;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
```

---

## Spawn Particles at World Position

`ParticleUtil` is a static utility that creates `SpawnParticleSystem` packets and sends them to nearby players. All methods are thread-safe when called from the world thread.

### Simplest Overload (Auto-Discovery)

Automatically finds players within `DEFAULT_PARTICLE_DISTANCE` (75 blocks):

```java
ParticleUtil.spawnParticleEffect(
    "Effects/Impact_Slash",  // particle system ID
    hitPosition,              // Vector3d
    componentAccessor         // ComponentAccessor<EntityStore>
);
```

### With Explicit Player List

Send particles only to specific players (e.g., realm participants):

```java
ParticleUtil.spawnParticleEffect(
    "Effects/Impact_Slash",
    hitPosition,
    playerRefs,          // List<Ref<EntityStore>>
    componentAccessor
);
```

### With Rotation

```java
ParticleUtil.spawnParticleEffect(
    "Effects/Impact_Slash",
    hitPosition,
    new Vector3f(yaw, pitch, roll),  // rotation as Vector3f
    playerRefs,
    componentAccessor
);
```

### With Scale and Color Tint

Reuse a vanilla particle with different scale and color:

```java
ParticleUtil.spawnParticleEffect(
    "Effects/Impact_Slash",
    hitPosition,
    0f, 0f, 0f,                      // yaw, pitch, roll
    2.0f,                              // scale (2x size)
    new Color((byte)255, (byte)0, (byte)0),  // red tint
    playerRefs,
    componentAccessor
);
```

### With Source Entity (Excluded from Recipients)

Pass a `sourceRef` to exclude an entity from receiving its own particles (e.g., the attacker doesn't see their own hit marker):

```java
ParticleUtil.spawnParticleEffect(
    "Effects/Impact_Slash",
    hitPosition,
    sourceRef,           // Ref<EntityStore> — excluded from packet
    playerRefs,
    componentAccessor
);
```

### With WorldParticle Config Object

`WorldParticle` bundles a particle system ID with offset/rotation/color:

```java
WorldParticle wp = new WorldParticle(
    "Effects/Fire_Burst",   // systemId
    1.5f,                    // scale
    new Color((byte)255, (byte)128, (byte)0),  // orange tint
    new Vector3f(0, 0.5f, 0),  // positionOffset (half block up)
    null                     // rotationOffset
);

ParticleUtil.spawnParticleEffect(wp, worldPosition, playerRefs, componentAccessor);
```

### Batch Spawn (Multiple Particle Systems)

Spawn several particle systems at once:

```java
WorldParticle[] particles = new WorldParticle[] {
    new WorldParticle("Effects/Fire_Burst", 1.0f, null, null, null),
    new WorldParticle("Effects/Smoke_Puff", 0.8f, null, new Vector3f(0, 1f, 0), null)
};

ParticleUtil.spawnParticleEffects(particles, position, null, playerRefs, componentAccessor);
```

---

## All ParticleUtil Overloads

| # | Parameters | Notes |
|---|-----------|-------|
| 1 | `(String, Vector3d, ComponentAccessor)` | Auto-discovers players within 75 blocks |
| 2 | `(String, Vector3d, List<Ref>, ComponentAccessor)` | Explicit player list |
| 3 | `(String, Vector3d, Ref source, List<Ref>, ComponentAccessor)` | Excludes source from recipients |
| 4 | `(String, Vector3d, Vector3f rotation, List<Ref>, ComponentAccessor)` | With rotation |
| 5 | `(String, Vector3d, Vector3f rotation, Ref source, List<Ref>, ComponentAccessor)` | Rotation + source |
| 6 | `(String, Vector3d, float yaw/pitch/roll, Ref source, List<Ref>, ComponentAccessor)` | Individual rotation floats |
| 7 | `(String, Vector3d, float yaw/pitch/roll, float scale, Color, List<Ref>, ComponentAccessor)` | Full control |
| 8 | `(WorldParticle, Vector3d, List<Ref>, ComponentAccessor)` | Config object |
| 9 | `(WorldParticle, Vector3d, Ref source, List<Ref>, ComponentAccessor)` | Config + source |
| 10 | `(WorldParticle[], Vector3d, Ref source, List<Ref>, ComponentAccessor)` | Batch spawn |
| 11 | `(WorldParticle, Vector3d, float yaw/pitch/roll, Ref source, List<Ref>, ComponentAccessor)` | Config + rotation |
| 12 | `(String, double x/y/z, List<Ref>, ComponentAccessor)` | Raw doubles |
| 13 | `(String, double x/y/z, Ref source, List<Ref>, ComponentAccessor)` | Raw doubles + source |
| 14 | `(String, double x/y/z, float yaw/pitch/roll, Ref source, List<Ref>, ComponentAccessor)` | Raw doubles + rotation |
| 15 | `(String, double x/y/z, float yaw/pitch/roll, float scale, Color, Ref source, List<Ref>, ComponentAccessor)` | Full internal method |

**Default values** when not specified: `scale = 1.0f`, `rotation = (0,0,0)`, `color = null` (no tint), `sourceRef = null` (no exclusion).

---

## Spawn Particles on Entity

Entity-attached particles follow the entity's model. Use the `SpawnModelParticles` packet directly.

### Basic Entity Particles

```java
import com.hypixel.hytale.server.core.entity.NetworkId;

// Get entity's network ID (required by client for tracking)
int entityNetworkId = componentAccessor.getComponent(entityRef, NetworkId.getComponentType()).getId();

// Create model particle config
ModelParticle particle = new ModelParticle(
    "Effect_Fire",           // systemId — particle system asset ID
    1.0f,                     // scale
    null,                     // color (null = default)
    EntityPart.Entity,        // targetEntityPart
    null,                     // targetNodeName (null = entity root)
    null,                     // positionOffset
    null,                     // rotationOffset
    false                     // detachedFromModel (false = follows entity)
);

// Create and send packet
SpawnModelParticles packet = new SpawnModelParticles(
    entityNetworkId,
    new ModelParticle[] { particle }
);

// Send to nearby players
for (Ref<EntityStore> playerRef : playerRefs) {
    PlayerRef player = componentAccessor.getComponent(playerRef, PlayerRef.getComponentType());
    if (player != null) {
        player.getPacketHandler().writeNoCache(packet);
    }
}
```

### Particles on Specific Body Part

```java
ModelParticle headParticle = new ModelParticle(
    "Stunned",               // systemId
    1.0f,                     // scale
    null,                     // color
    EntityPart.Entity,        // targetEntityPart
    "Head",                   // targetNodeName — attaches to Head bone
    new Vector3f(0, 0.5f, 0), // positionOffset — half block above head
    null,                     // rotationOffset
    false                     // detachedFromModel
);
```

### Detached Particles (World-Space at Entity Position)

```java
ModelParticle detached = new ModelParticle(
    "Explosion_Small",
    2.0f,
    new Color((byte)255, (byte)100, (byte)0),
    EntityPart.Self,
    null,
    null,
    null,
    true    // detachedFromModel — spawns at entity position but doesn't follow
);
```

---

## EntityPart Reference

| Value | Description |
|-------|-------------|
| `EntityPart.Self` | The entity itself (root) |
| `EntityPart.Entity` | The entity model (use with `targetNodeName` for specific bones) |
| `EntityPart.PrimaryItem` | The entity's primary held item |
| `EntityPart.SecondaryItem` | The entity's secondary/off-hand item |

**Common `targetNodeName` values** (bone names vary by model):
- `"Head"` — Entity head bone
- `"Pelvis"` — Entity pelvis/hip bone
- `"RightHand"` / `"LeftHand"` — Hand bones
- `null` — Root of the target entity part

---

## Color Tinting

The `Color` class uses signed bytes (`-128` to `127` maps to `0` to `255`):

```java
// Red
new Color((byte)255, (byte)0, (byte)0)

// Ice blue
new Color((byte)100, (byte)200, (byte)255)

// Gold
new Color((byte)255, (byte)215, (byte)0)

// No tint (use null, not black)
null
```

When `color` is `null`, the particle renders with its default colors. When a color is provided, it acts as a multiplicative tint — the particle's texture colors are multiplied by the tint.

---

## Finding Particle Asset IDs

Particle system IDs are relative paths from the `Server/Particles/` directory without the `.particlesystem` extension.

**Examples:**
- File: `Server/Particles/Effects/Impact_Slash.particlesystem` → ID: `"Effects/Impact_Slash"`
- File: `Server/Particles/Effect_Fire.particlesystem` → ID: `"Effect_Fire"`
- File: `Server/Particles/Potions/Potion_Health_Heal.particlesystem` → ID: `"Potions/Potion_Health_Heal"`

See the [Vanilla Asset Catalog](../reference/vanilla-asset-catalog.md) for a complete list of all particle system IDs grouped by category.

---

## Common Use Cases

### Hit Marker at Position

```java
// On damage dealt, spawn impact particles at hit location
ParticleUtil.spawnParticleEffect(
    "Effects/Impact_Slash",
    damagePosition,
    componentAccessor
);
```

### Elemental Hit with Color Tint

```java
// Fire hit — reuse generic impact with orange tint
ParticleUtil.spawnParticleEffect(
    "Effects/Impact_Slash",
    hitPosition,
    0f, 0f, 0f,
    1.5f,
    new Color((byte)255, (byte)128, (byte)0),  // orange
    playerRefs,
    componentAccessor
);
```

### Persistent Effect on Mob (via Entity Effect JSON)

For long-running particle effects on entities, prefer defining them in an Entity Effect JSON rather than sending packets manually. See [Entity Effects](entity-effects.md) — the `ApplicationEffects.Particles` field attaches `ModelParticle` configs that persist for the effect's duration.

### Environmental Ambience

```java
// Spawn ambient particles at random positions in a region
for (int i = 0; i < 5; i++) {
    Vector3d pos = new Vector3d(
        centerX + random.nextDouble() * 10 - 5,
        centerY + random.nextDouble() * 3,
        centerZ + random.nextDouble() * 10 - 5
    );
    ParticleUtil.spawnParticleEffect("Ambient/Firefly", pos, componentAccessor);
}
```

---

## Best Practices

1. **Send to nearby players only** — Use the auto-discovery overload or pass a filtered player list. The default discovery radius is `ParticleUtil.DEFAULT_PARTICLE_DISTANCE` = 75 blocks.
2. **World thread** — All `ParticleUtil` calls must be on the world thread. If calling from an async context, wrap in `world.execute(() -> { ... })`.
3. **Entity particles need valid NetworkId** — The client validates `entityId` against tracked entities. Use the real `NetworkId` component value, not an arbitrary number.
4. **Prefer entity effects for persistent visuals** — `SpawnModelParticles` is fire-and-forget. For effects that should persist (burn aura, frozen overlay), use the Entity Effect system's `ApplicationEffects.Particles` instead.
5. **Color tint is multiplicative** — A white tint (`255,255,255`) has no effect. A red tint (`255,0,0`) removes green and blue channels. Use `null` for default colors.
6. **Scale affects visibility distance** — Larger particles are visible from further away. Keep scale reasonable (0.5–3.0) for gameplay particles.

---

## Server-Side Asset Class

There is also a server-side `ModelParticle` asset class (different from the protocol packet class) with codec support:

```java
import com.hypixel.hytale.server.core.asset.type.model.config.ModelParticle;

// Asset-level ModelParticle (used in JSON configs)
// Has a BuilderCodec for deserialization from effect/entity JSONs
// Convert to protocol packet for network sending:
com.hypixel.hytale.protocol.ModelParticle packetParticle = assetModelParticle.toPacket();
```

The asset class also provides a `scale(float)` method that multiplies scale and position offset in-place (useful for size-variant entities).

---

## ParticleSystem JSON Schema

Particle systems are defined as `.particlesystem` JSON files under `Server/Particles/`. The authoritative schema is at `Assets/Schema/ParticleSystem.json`. Below are the key fields.

### Top-Level Fields

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `Parent` | string | — | Inherit properties from another ParticleSystem by name. Child fields override parent fields. |
| `Spawners` | array | (required) | Array of `ParticleSpawnerGroup` entries (min 1). Each defines one particle emitter within the system. |
| `LifeSpan` | number | `0.0` | Total lifetime of the particle system in seconds. `0` = infinite (lives until explicitly stopped). |
| `CullDistance` | number | `0.0` | Distance in blocks beyond which the particle system is culled (not rendered). `0` = use engine default. |
| `BoundingRadius` | number | `0.0` | Bounding sphere radius for visibility culling. |
| `IsImportant` | boolean | `false` | When `true`, the system is prioritized and less likely to be culled under particle budget pressure. |
| `Tags` | object | — | Key-value tag map for asset classification (see [Tag System](tag-system.md)). |

### ParticleSpawnerGroup Fields

Each entry in the `Spawners` array is a `ParticleSpawnerGroup` with these fields:

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `SpawnerId` | string | — | References a client-side particle spawner definition (the actual visual/behavior). |
| `PositionOffset` | `{X, Y, Z}` | `(0,0,0)` | Positional offset from the system origin. |
| `RotationOffset` | `{Yaw, Pitch, Roll}` | `(0,0,0)` | Rotational offset for the spawner. |
| `FixedRotation` | boolean | `false` | When `true`, the spawner rotation is fixed in world space (doesn't follow entity rotation). |
| `SpawnRate` | `{Min, Max}` | — | Particles per second. A random value between Min and Max is used each spawn cycle. |
| `LifeSpan` | number | — | Override lifetime for this spawner's particles (seconds). |
| `StartDelay` | number | — | Delay in seconds before this spawner begins emitting. |
| `WaveDelay` | number | — | Delay between spawn waves. |
| `TotalSpawners` | integer | `1` | Total number of particles this spawner will emit over its lifetime. |
| `MaxConcurrent` | integer | `0` | Maximum particles alive at once from this spawner. `0` = unlimited. |
| `InitialVelocity` | number | — | Initial velocity magnitude for spawned particles. |
| `EmitOffset` | number | — | Emission shape offset. |
| `Attractors` | array | — | Attractor definitions that pull particles toward a point. |

### Example: Vanilla Particle System

From `Server/Particles/Bow_Charging.particlesystem`:

```json
{
  "$Comment": "Particle delays tuned to appear at max charge (1.2s).",
  "Spawners": [
    {
      "SpawnerId": "Bow_Charging_Circles",
      "StartDelay": 0.75,
      "SpawnRate": { "Min": 2.0, "Max": 2.0 },
      "MaxConcurrent": 4,
      "TotalSpawners": 100
    },
    {
      "SpawnerId": "Bow_Charging_Sparks",
      "StartDelay": 0.95,
      "MaxConcurrent": 4,
      "SpawnRate": { "Min": 2.0, "Max": 2.0 },
      "TotalSpawners": 100
    }
  ],
  "BoundingRadius": 10
}
```

**Notes:**
- `SpawnerId` references a client-side spawner asset that defines the visual appearance (texture, shape, animation). The `.particlesystem` file only controls timing, positioning, and spawner orchestration.
- Use `Parent` for inheritance chains (e.g., a fire variant that reuses a base effect's structure with different spawner IDs).
- The `$Comment`, `$Title`, `$Author`, `$TODO` fields are editor-only metadata — ignored at runtime.

For the full schema including all nested type definitions, see `Assets/Schema/ParticleSystem.json` and `Assets/Schema/common.json#/definitions/ParticleSpawnerGroup`.

---

## Related APIs

- [Entity Effects](entity-effects.md) — Persistent particles via `ApplicationEffects.Particles`
- [Sounds](sounds.md) — Audio playback (often paired with particles)
- [Vanilla Asset Catalog](../reference/vanilla-asset-catalog.md) — All particle system IDs
