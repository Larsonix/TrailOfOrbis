# Procedural Realm Generation

This document covers how Realms are procedurally generated using Hytale's WorldGen V2 system. Each Realm is a unique, dynamically created arena with infinite variety.

## Overview

Hytale's WorldGen V2 provides powerful procedural generation through:
- **Density Fields** - 3D mathematical functions that define terrain shape
- **Biome System** - Controls terrain, materials, props, and environment
- **Instance System** - Creates isolated, joinable worlds for Realms

We leverage all three to create unique Realm arenas on-demand.

---

## Hytale WorldGen V2 Architecture

### Core Systems

```
┌─────────────────────────────────────────────────────────────────┐
│                  HYTALE WORLDGEN V2 ARCHITECTURE                │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐         │
│  │    ZONE     │    │   BIOME     │    │    CAVE     │         │
│  │   SYSTEM    │───▶│   SYSTEM    │───▶│   SYSTEM    │         │
│  └─────────────┘    └─────────────┘    └─────────────┘         │
│        │                  │                  │                  │
│        │    Large-scale   │   Terrain &     │   Underground    │
│        │    regions       │   environment   │   structures     │
│        │                  │                  │                  │
│        └──────────────────┴──────────────────┘                  │
│                           │                                     │
│                           ▼                                     │
│                  ┌─────────────────┐                            │
│                  │    INSTANCE     │  ← Isolated world          │
│                  │     SYSTEM      │    for each Realm          │
│                  └─────────────────┘                            │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Biome Components

Every Hytale biome has **5 main components** we can customize:

| Component | Purpose | Realm Usage |
|-----------|---------|-------------|
| **Terrain Height Provider** | Mathematical terrain shape | Arena floor, hills, boundaries |
| **Material Provider** | Block types at each position | Surface blocks, walls, water |
| **Object Provider** | Props and structures | Obstacles, decorations, cover |
| **Environment Provider** | Weather, mobs, ambient | Mob spawns, atmosphere |
| **Tint Provider** | Color variations | Biome-specific coloring |

---

## Density Fields - The Foundation

### What Are Density Fields?

Density fields are **3D maps of decimal values** (typically -1 to +1) that define terrain:
- **Positive values** = Solid terrain (blocks)
- **Negative values** = Empty space (air)
- **Zero** = Surface boundary

```
┌─────────────────────────────────────────────────────────────────┐
│  DENSITY FIELD VISUALIZATION (2D slice)                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│    -1.0          0.0          +1.0                              │
│     ◄─────────────┼─────────────►                               │
│                   │                                             │
│   ░░░░░░░░░░░░░░░░│████████████████   ░ = Air (negative)       │
│   ░░░░░░░░░░░░░░░░│████████████████   █ = Solid (positive)     │
│   ░░░░░░░░░░░████████████████████████ │ = Surface (zero)       │
│   ░░░░░░████████████████████████████                            │
│   ░░████████████████████████████████                            │
│   ████████████████████████████████████                          │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Basic Terrain Formula

```
Terrain = Base Shape + Noise Variation + Height Curve
```

### Available Density Nodes

**Geometric Shapes** (for arena boundaries):
| Node | Description | Realm Use |
|------|-------------|-----------|
| `Sphere` | Spherical density field | Dome arenas |
| `Cylinder` | Cylindrical field | Circular arenas with walls |
| `Cube/Cuboid` | Box-shaped field | Square/rectangular arenas |
| `Plane` | Distance from flat plane | Floor generation |
| `Shell` | Hollow region around origin | Ring-shaped arenas |

**Noise Sources** (for terrain variation):
| Node | Description | Realm Use |
|------|-------------|-----------|
| `SimplexNoise` | Smooth organic noise | Natural terrain |
| `PerlinNoise` | Classic procedural noise | Hills, dunes |
| `CellNoise` | Cellular/Voronoi patterns | Rocky, cracked terrain |
| `FractalNoise` | Layered noise octaves | Detailed variation |

**Manipulation Nodes** (for shaping):
| Node | Description | Realm Use |
|------|-------------|-----------|
| `Scale` | Stretch/contract field | Arena size |
| `Rotate` | Spin around axes | Orientation |
| `Translate` | Shift position | Centering |
| `Clamp` | Constrain to range | Bounded heights |
| `Mix/SmoothMix` | Blend multiple inputs | Transition zones |
| `Sum/Multiply` | Combine fields | Layered terrain |
| `Absolute` | Creates ridges | Mountain ridges |
| `CurveMapping` | Custom value mapping | Height profiles |

---

## Realm Biome Definitions

We create custom biomes for each Realm type. Each biome is a complete definition stored in the asset pack.

### File Structure

```
Server/
├── HytaleGenerator/
│   ├── WorldStructure/
│   │   └── RealmStructures/
│   │       ├── realm_forest.json       # Forest realm generator
│   │       ├── realm_desert.json       # Desert realm generator
│   │       ├── realm_volcano.json      # Volcano realm generator
│   │       ├── realm_tundra.json       # Tundra realm generator
│   │       └── realm_void.json         # Void realm generator
│   │
│   ├── Biomes/
│   │   └── Realms/
│   │       ├── forest_arena.json       # Forest biome definition
│   │       ├── desert_arena.json       # Desert biome definition
│   │       ├── volcano_arena.json      # Volcano biome definition
│   │       └── ...
│   │
│   ├── Density/
│   │   └── Realms/
│   │       ├── arena_shapes/           # Reusable arena shapes
│   │       │   ├── circle_arena.json
│   │       │   ├── square_arena.json
│   │       │   ├── hexagon_arena.json
│   │       │   └── ring_arena.json
│   │       └── terrain_noise/          # Reusable noise patterns
│   │           ├── gentle_hills.json
│   │           ├── rocky_terrain.json
│   │           └── flat_floor.json
│   │
│   └── Props/
│       └── Realms/
│           ├── forest_props.json       # Trees, rocks, bushes
│           ├── desert_props.json       # Cacti, ruins, bones
│           └── volcano_props.json      # Lava pools, obsidian
│
└── Instances/
    └── Realms/
        └── realm_instance_template.bson  # Base instance config
```

### Example: Forest Arena Biome

```json
{
  "id": "rpg:forest_arena",
  "name": "Forest Arena",

  "terrainHeightProvider": {
    "type": "density_composite",
    "inputs": [
      {
        "type": "reference",
        "asset": "rpg:density/realms/arena_shapes/circle_arena",
        "weight": 1.0
      },
      {
        "type": "reference",
        "asset": "rpg:density/realms/terrain_noise/gentle_hills",
        "weight": 0.3
      }
    ],
    "operation": "sum",
    "baseHeight": 64,
    "heightVariation": 8
  },

  "materialProvider": {
    "type": "priority_queue",
    "materials": [
      {
        "condition": "surface",
        "block": "hytale:grass_block"
      },
      {
        "condition": "subsurface",
        "depth": 3,
        "block": "hytale:dirt"
      },
      {
        "condition": "deep",
        "block": "hytale:stone"
      },
      {
        "condition": "boundary_wall",
        "block": "hytale:mossy_stone_brick"
      }
    ]
  },

  "objectProvider": {
    "type": "weighted_placement",
    "objects": [
      {
        "prefab": "hytale:oak_tree",
        "weight": 1.0,
        "density": 0.02,
        "minSpacing": 5,
        "surfaceOnly": true
      },
      {
        "prefab": "hytale:bush",
        "weight": 2.0,
        "density": 0.05,
        "minSpacing": 2,
        "surfaceOnly": true
      },
      {
        "prefab": "hytale:rock_medium",
        "weight": 0.5,
        "density": 0.01,
        "minSpacing": 8,
        "surfaceOnly": true
      }
    ],
    "excludeZones": ["spawn_area", "boss_area"]
  },

  "environmentProvider": {
    "weather": "clear",
    "ambientLight": 1.0,
    "fogDensity": 0.1,
    "mobSpawnRules": {
      "enabled": false
    }
  },

  "tintProvider": {
    "grassTint": "#4a8f3c",
    "foliageTint": "#2d5a1e"
  }
}
```

### Example: Circle Arena Shape (Density Asset)

```json
{
  "id": "rpg:density/realms/arena_shapes/circle_arena",
  "name": "Circle Arena Shape",

  "nodes": [
    {
      "id": "arena_floor",
      "type": "cylinder",
      "radius": "${arena_radius}",
      "height": 1,
      "axis": "Y",
      "position": [0, "${base_height}", 0]
    },
    {
      "id": "arena_walls",
      "type": "shell",
      "innerRadius": "${arena_radius}",
      "outerRadius": "${arena_radius + wall_thickness}",
      "height": "${wall_height}",
      "position": [0, "${base_height}", 0]
    },
    {
      "id": "combined",
      "type": "max",
      "inputs": ["arena_floor", "arena_walls"]
    }
  ],

  "output": "combined",

  "parameters": {
    "arena_radius": 50,
    "base_height": 64,
    "wall_thickness": 3,
    "wall_height": 20
  }
}
```

---

## Realm Biome Types

### Available Biomes

| Biome | Theme | Terrain Style | Hazards | Props |
|-------|-------|---------------|---------|-------|
| **FOREST** | Emerald Grove | Rolling hills, clearings | None | Trees, bushes, rocks |
| **CAVE** | Underground | Flat with stalagmites | Darkness | Crystals, mushrooms |
| **SWAMP** | Murky wetland | Low, waterlogged | Poison pools | Dead trees, vines |
| **DESERT** | Howling Sands | Dunes, flat stretches | Heat (DoT?) | Cacti, ruins, bones |
| **CANYON** | Rocky gorge | Elevated platforms | Fall damage | Rock pillars, bridges |
| **OASIS** | Hidden paradise | Central water feature | None | Palm trees, flowers |
| **TUNDRA** | Frozen waste | Flat snow fields | Cold (slow?) | Ice spikes, snow piles |
| **ICE_CAVE** | Crystal cavern | Slippery floors | Slippery | Ice crystals, frozen pools |
| **VOLCANO** | Molten realm | Rocky with lava | Lava (damage) | Obsidian, fire vents |
| **VOID** | Otherworldly | Floating platforms | Fall into void | Strange crystals |
| **CORRUPTED** | Twisted reality | Chaotic terrain | Random effects | Corrupted growths |

### Biome-Specific Density Configurations

```yaml
# realm-biomes.yml
biomes:
  forest:
    density:
      base_shape: circle_arena
      noise_layer: gentle_hills
      noise_strength: 0.3
      height_variation: 8
    materials:
      surface: grass_block
      subsurface: dirt
      deep: stone
      wall: mossy_stone_brick
    props:
      trees: { density: 0.02, types: [oak, birch] }
      bushes: { density: 0.05 }
      rocks: { density: 0.01 }

  desert:
    density:
      base_shape: square_arena
      noise_layer: dune_noise
      noise_strength: 0.4
      height_variation: 5
    materials:
      surface: sand
      subsurface: sandstone
      deep: sandstone
      wall: sandstone_brick
    props:
      cacti: { density: 0.01 }
      dead_bushes: { density: 0.03 }
      ruins: { density: 0.005, types: [pillar, arch, rubble] }
      bones: { density: 0.02 }

  volcano:
    density:
      base_shape: ring_arena
      noise_layer: rocky_terrain
      noise_strength: 0.5
      height_variation: 12
      lava_pools: true
      lava_level: 60
    materials:
      surface: basite
      subsurface: volcanic_rock
      deep: magma_block
      wall: obsidian
      liquid: lava
    props:
      obsidian_spikes: { density: 0.015 }
      fire_vents: { density: 0.01 }
      lava_rocks: { density: 0.02 }

  void:
    density:
      base_shape: floating_platforms
      noise_layer: chaotic_noise
      noise_strength: 0.6
      platform_count: 5-8
      platform_connections: bridges
    materials:
      surface: end_stone
      subsurface: end_stone
      deep: void  # Empty
      wall: none
    props:
      void_crystals: { density: 0.02 }
      strange_plants: { density: 0.01 }
    special:
      void_fog: true
      reduced_gravity: false
```

---

## Arena Layout Shapes

### Shape Definitions

Each shape is a reusable Density asset that defines the arena boundary.

```
┌─────────────────────────────────────────────────────────────────┐
│  ARENA LAYOUT SHAPES                                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  CIRCLE          SQUARE          HEXAGON         RING           │
│    ████            ██████          ████           ████████      │
│  ████████        ██████████      ████████       ████░░░░████    │
│  ████████        ██████████      ██████████     ████░░░░████    │
│  ████████        ██████████      ██████████     ████░░░░████    │
│    ████            ██████          ████           ████████      │
│                                                                 │
│  CROSS           RECTANGLE       L-SHAPE         IRREGULAR      │
│    ██              ████████        ████           █████         │
│  ██████            ████████        ████           ███████       │
│    ██              ████████        ████████       ████████      │
│    ██              ████████        ████████         ██████      │
│                    ████████                           ███       │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Shape Parameters

```yaml
# Arena shape configurations
shapes:
  circle:
    density_type: cylinder
    parameters:
      radius: "${size}"
      wall_radius: "${size + 3}"
      wall_height: 15
    spawn_distribution: radial

  square:
    density_type: cuboid
    parameters:
      width: "${size}"
      depth: "${size}"
      wall_thickness: 3
      wall_height: 15
    spawn_distribution: grid

  hexagon:
    density_type: hexagonal_prism
    parameters:
      radius: "${size}"
      wall_thickness: 3
      wall_height: 15
    spawn_distribution: hexagonal

  ring:
    density_type: shell
    parameters:
      inner_radius: "${size * 0.4}"
      outer_radius: "${size}"
      wall_height: 15
    spawn_distribution: ring
    special:
      center_hazard: true  # Lava/void in center

  cross:
    density_type: composite
    arms: 4
    parameters:
      arm_length: "${size}"
      arm_width: "${size * 0.3}"
      wall_height: 15
    spawn_distribution: per_arm
```

---

## Instance Creation Flow

### How Realms Are Created

```
┌─────────────────────────────────────────────────────────────────┐
│                  REALM INSTANCE CREATION FLOW                   │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. PLAYER USES REALM MAP                                       │
│     │                                                           │
│     ▼                                                           │
│  2. DETERMINE GENERATION PARAMETERS                             │
│     ├── Biome Type (from map)     → FOREST                      │
│     ├── Layout Shape (from map)   → CIRCLE                      │
│     ├── Layout Size (from map)    → MEDIUM (100 blocks)         │
│     ├── Map Level                 → 50                          │
│     └── Random Seed               → Generated                   │
│     │                                                           │
│     ▼                                                           │
│  3. CREATE INSTANCE CONFIGURATION                               │
│     ├── Select WorldStructure     → realm_forest                │
│     ├── Set biome parameters      → circle_arena + gentle_hills │
│     ├── Configure size            → radius: 50                  │
│     └── Set seed for reproducibility                            │
│     │                                                           │
│     ▼                                                           │
│  4. SPAWN INSTANCE (Hytale API)                                 │
│     │                                                           │
│     │  InstancesPlugin.spawnInstance(                           │
│     │      "realm_" + mapId,                                    │
│     │      worldStructure,                                      │
│     │      instanceConfig                                       │
│     │  )                                                        │
│     │                                                           │
│     ▼                                                           │
│  5. WORLDGEN V2 GENERATES TERRAIN                               │
│     ├── Density field evaluated   → Arena shape created         │
│     ├── Materials applied         → Blocks placed               │
│     ├── Props placed              → Trees, rocks, etc.          │
│     └── Environment set           → Weather, lighting           │
│     │                                                           │
│     ▼                                                           │
│  6. POST-GENERATION SETUP                                       │
│     ├── Calculate spawn positions → Based on density scan       │
│     ├── Spawn monsters            → At calculated positions     │
│     ├── Place portal              → At arena center             │
│     └── Initialize tracker        → Monster count set           │
│     │                                                           │
│     ▼                                                           │
│  7. REALM READY FOR ENTRY                                       │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Instance Configuration

```java
public class RealmInstanceFactory {

    public CompletableFuture<RealmInstance> createRealm(RealmMapData mapData, PlayerRef opener) {
        // 1. Build generation parameters
        RealmGenParams params = RealmGenParams.builder()
            .biome(mapData.getBiomeType())
            .shape(mapData.getLayoutShape())
            .size(mapData.getLayoutSize())
            .level(mapData.getLevel())
            .seed(generateSeed(mapData.getMapId()))
            .build();

        // 2. Select WorldStructure based on biome
        String worldStructure = "rpg:realm_" + params.biome().name().toLowerCase();

        // 3. Create instance config with parameters
        InstanceConfig config = InstanceConfig.builder()
            .worldStructure(worldStructure)
            .parameter("arena_radius", params.size().getDimension() / 2)
            .parameter("base_height", 64)
            .parameter("wall_height", 15)
            .parameter("noise_seed", params.seed())
            .deleteOnEmpty(true)
            .timeout(calculateDuration(mapData))
            .build();

        // 4. Spawn instance via Hytale API
        String instanceName = "realm_" + mapData.getMapId().toString().substring(0, 8);

        return InstancesPlugin.get().spawnInstance(instanceName, config)
            .thenCompose(world -> {
                // 5. Wait for initial chunk generation
                return waitForGeneration(world, params);
            })
            .thenApply(world -> {
                // 6. Post-generation setup
                List<SpawnPosition> spawnPositions = calculateSpawnPositions(world, params);
                spawnMonsters(world, spawnPositions, mapData);

                Vector3 portalPos = calculatePortalPosition(world, params);

                return new RealmInstance(
                    mapData.getMapId(),
                    world,
                    params,
                    mapData,
                    opener.getUuid(),
                    spawnPositions.size()
                );
            });
    }

    private long generateSeed(UUID mapId) {
        // Deterministic seed from map ID for reproducibility
        return mapId.getMostSignificantBits() ^ mapId.getLeastSignificantBits();
    }
}
```

---

## Spawn Position Calculation

Since terrain is procedurally generated, we calculate spawn positions dynamically.

### Position Scanner

```java
public class RealmSpawnCalculator {

    public List<SpawnPosition> calculateSpawnPositions(World world, RealmGenParams params) {
        List<SpawnPosition> positions = new ArrayList<>();

        int targetCount = params.size().getMonsterCount();
        int arenaRadius = params.size().getDimension() / 2;
        Vector3 center = new Vector3(0, 64, 0);  // Arena center

        // Scan for valid positions
        List<Vector3> validPositions = scanForValidPositions(world, center, arenaRadius);

        // Distribute spawn points
        switch (params.shape()) {
            case CIRCLE, HEXAGON -> distributeRadially(validPositions, positions, targetCount);
            case SQUARE, RECTANGLE -> distributeGrid(validPositions, positions, targetCount);
            case RING -> distributeRing(validPositions, positions, targetCount);
            case CROSS -> distributePerArm(validPositions, positions, targetCount);
        }

        // Designate special spawns
        designateEliteSpawns(positions, params);
        designateBossSpawn(positions, params);

        return positions;
    }

    private List<Vector3> scanForValidPositions(World world, Vector3 center, int radius) {
        List<Vector3> valid = new ArrayList<>();

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                // Find ground level
                int groundY = findGroundLevel(world, center.x + x, center.z + z);

                if (groundY > 0) {
                    Vector3 pos = new Vector3(center.x + x, groundY + 1, center.z + z);

                    // Check if valid spawn (not in wall, has headroom, etc.)
                    if (isValidSpawnPosition(world, pos)) {
                        valid.add(pos);
                    }
                }
            }
        }

        return valid;
    }

    private int findGroundLevel(World world, float x, float z) {
        // Scan from top down to find first solid block
        for (int y = 128; y > 0; y--) {
            Block block = world.getBlock((int)x, y, (int)z);
            Block above = world.getBlock((int)x, y + 1, (int)z);

            if (block.isSolid() && !above.isSolid()) {
                return y;
            }
        }
        return -1;  // No valid ground found
    }

    private boolean isValidSpawnPosition(World world, Vector3 pos) {
        // Check 2 blocks of headroom
        Block feet = world.getBlock((int)pos.x, (int)pos.y, (int)pos.z);
        Block head = world.getBlock((int)pos.x, (int)pos.y + 1, (int)pos.z);

        return !feet.isSolid() && !head.isSolid();
    }
}
```

---

## Parameterized Generation

### Size-Based Parameters

The same biome scales based on the `LayoutSize`:

```yaml
# Size scaling parameters
sizes:
  SMALL:
    dimension: 50
    wall_height: 12
    noise_scale: 1.5      # More compressed noise
    prop_density: 1.2     # Denser props in small space
    monster_count: 20-30
    boss_count: 0
    elite_percent: 10%

  MEDIUM:
    dimension: 100
    wall_height: 15
    noise_scale: 1.0      # Base noise scale
    prop_density: 1.0     # Base prop density
    monster_count: 40-60
    boss_count: 1
    elite_percent: 15%

  LARGE:
    dimension: 200
    wall_height: 18
    noise_scale: 0.7      # Stretched noise (larger features)
    prop_density: 0.8     # Sparser props
    monster_count: 80-120
    boss_count: 2
    elite_percent: 15%

  MASSIVE:
    dimension: 400
    wall_height: 25
    noise_scale: 0.5      # Very stretched noise
    prop_density: 0.6     # Sparse props
    monster_count: 150-200
    boss_count: 3
    elite_percent: 20%
```

### Level-Based Adjustments

Higher level maps get subtle terrain enhancements:

```yaml
# Level scaling (using soft cap formula)
level_scaling:
  soft_cap_k: 1000  # Same as modifier scaling

  # Terrain complexity increases with level
  noise_octaves:
    base: 2
    max_bonus: 4
    # octaves = base + (max_bonus * level / (level + K))
    # Level 1000: 2 + (4 * 0.5) = 4 octaves

  # Prop variety increases with level
  prop_variety:
    base_types: 3
    max_bonus_types: 5
    # Level 1000: 3 + (5 * 0.5) = 5.5 → 5 types

  # Environmental intensity
  hazard_intensity:
    base: 0.0
    max: 0.5
    # Level 1000: 0.25 (25% hazard coverage)
```

---

## Configuration

### Main Configuration

```yaml
# realm-generation.yml
generation:
  mode: PROCEDURAL  # Always procedural!

  # Instance settings
  instance:
    name_prefix: "realm_"
    delete_on_empty: true
    empty_timeout_seconds: 90

  # Generation timeouts
  timeouts:
    chunk_generation_ms: 5000
    post_setup_ms: 2000

  # Seed generation
  seeding:
    use_map_id: true  # Deterministic from map UUID
    salt: "TrailOfOrbis_Realms_v1"

# Biome registry
biomes:
  # See biome definitions above

# Shape registry
shapes:
  # See shape definitions above

# Size definitions
sizes:
  # See size definitions above

# Level scaling
level_scaling:
  soft_cap_k: 1000
  # ... as defined above
```

### WorldStructure Example

```json
{
  "id": "rpg:realm_forest",
  "name": "Forest Realm Generator",

  "zonePattern": {
    "type": "single_zone",
    "zone": "rpg:zones/realm_zone"
  },

  "biomePattern": {
    "type": "single_biome",
    "biome": "rpg:biomes/realms/forest_arena"
  },

  "caveGeneration": {
    "enabled": false
  },

  "worldBounds": {
    "type": "cylindrical",
    "radius": "${arena_radius + 10}",
    "minY": 0,
    "maxY": 128
  },

  "spawnPoint": {
    "x": 0,
    "y": "${base_height + 1}",
    "z": 0
  }
}
```

---

## Performance Considerations

### Chunk Generation

- Realms are small (50-400 blocks), so chunk count is limited
- Pre-generate all chunks before allowing player entry
- Use async generation to not block main thread

### Density Evaluation Optimization

From Hytale docs: **Multiplier nodes can short-circuit** when 0 is reached.

```
Optimization tip: Order density operations so cheap checks come first
- Check arena bounds FIRST (fast cylinder check)
- Only evaluate expensive noise if inside bounds
- Can improve performance by ~40%
```

### Caching

```java
// Cache generated realm configurations for identical map parameters
public class RealmConfigCache {
    private final Cache<RealmGenParams, InstanceConfig> cache =
        CacheBuilder.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .build();
}
```

---

## Debugging & Testing

### In-Game Commands

```
/realm preview <biome> <shape> <size>  - Preview generation without monsters
/realm regenerate                       - Regenerate current realm with new seed
/viewport --radius 3                    - Hytale's live-reload for biome changes
```

### Visualization

```
/realm debug density    - Show density field values at cursor
/realm debug spawns     - Highlight calculated spawn positions
/realm debug bounds     - Show arena boundary wireframe
```
