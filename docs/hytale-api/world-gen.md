# Hytale World Generation System

Comprehensive reference for Hytale's procedural world generation systems used to create environments, biomes, and structures dynamically as players explore the world.

> **Related skills:** For ECS fundamentals, see `docs/hytale-api/ecs.md`. For persistent data/Codec patterns, see `docs/hytale-api/persistent-data.md`. For entity effects, see `docs/hytale-api/entity-effects.md`.

## Quick Reference

| Task | Approach |
|------|----------|
| Get zone at position | `zoneGen.generate(seed, x, z)` returns `ZoneGeneratorResult` |
| Get zone from result | `zoneResult.getZone()` returns `Zone` |
| Get biome in zone | `zone.biomePatternGenerator().generateBiomeAt(zoneResult, seed, x, z)` |
| Get cave generator | `zone.caveGenerator()` returns `CaveGenerator` (nullable) |
| Get cave types | `caveGen.getCaveTypes()` returns `CaveType[]` |
| Check biome cave mask | `CaveBiomeMaskFlags.canGenerate(flags)` |
| Get border distance | `zoneResult.getBorderDistance()` |
| Fade near border | `customBiome.getFadeContainer().getMaskFactor(result)` |
| Create zone discovery | `new ZoneDiscoveryConfig(...)` |
| Create biome pattern | `new BiomePatternGenerator(points, tileBiomes, customBiomes)` |
| Create cave config | `new CaveGenerator(caveTypes)` |
| Create custom zone | `new Zone(id, name, discovery, caveGen, biomeGen, prefabs)` |

---

## Overview

Hytale's world generation is organized into three interconnected systems:

```
World Generation
├── Zones       — Large-scale regions defining overall world structure
├── Biomes      — Terrain characteristics and environment within zones
└── Caves       — Underground structures and networks within zones
```

These systems are hierarchical: **Zones** contain **Biomes**, and both Zones and Biomes influence **Cave** generation. They work together to produce smooth transitions and coherent regional themes.

---

## Zones

Zones are the largest-scale division in world generation. Each zone defines its own biome patterns, cave configurations, and unique structures (prefabs).

### Zone Lookup and Generation

```java
// Get zone at a world position
ZonePatternGenerator zoneGen = /* from world generator */;
ZoneGeneratorResult zoneResult = zoneGen.generate(seed, x, z);
Zone zone = zoneResult.getZone();
```

### ZoneGeneratorResult

The result object provides:

| Method | Returns | Description |
|--------|---------|-------------|
| `getZone()` | `Zone` | The zone at the queried position |
| `getBorderDistance()` | `double` | Distance to the nearest zone border |

### Zone Class

A `Zone` encapsulates all generation data for a region:

| Property | Type | Description |
|----------|------|-------------|
| ID | `int` | Unique numeric identifier |
| Name | `String` | Internal name (e.g., `"new_custom_zone"`) |
| Discovery | `ZoneDiscoveryConfig` | Player notification on zone entry |
| Cave Generator | `CaveGenerator` | Cave configuration (nullable) |
| Biome Pattern | `BiomePatternGenerator` | Biome layout within the zone |
| Unique Prefabs | — | Unique structures placed in the zone |

### Creating a Custom Zone

```java
// 1. Configure zone discovery (player notification on entry)
ZoneDiscoveryConfig discovery = new ZoneDiscoveryConfig(
    true,                           // Show notification
    "Custom Zone",                  // Display name
    "zone.forest.discover",         // Sound event
    "icons/forest.png",             // Icon
    true,                           // Major zone
    5.0f, 2.0f, 1.5f               // Duration, fade in, fade out
);

// 2. Create biome pattern
IPointGenerator biomePoints;
IWeightedMap<TileBiome> tileBiomes;
CustomBiome[] customBiomes;

BiomePatternGenerator biomeGen = new BiomePatternGenerator(
    biomePoints,
    tileBiomes,
    customBiomes
);

// 3. Create cave configuration
CaveType[] caveTypes; // cave definitions
CaveGenerator caveGen = new CaveGenerator(caveTypes);

// 4. Assemble the zone
Zone customZone = new Zone(
    100,                    // Unique ID
    "new_custom_zone",      // Internal name
    discovery,              // Discovery config
    caveGen,                // Cave generator
    biomeGen,               // Biome pattern
    uniquePrefabs           // Unique structures
);
```

### ZoneDiscoveryConfig

Controls what the player sees when entering a zone:

| Parameter | Type | Description |
|-----------|------|-------------|
| `showNotification` | `boolean` | Whether to display the zone entry notification |
| `displayName` | `String` | Name shown to the player |
| `soundEvent` | `String` | Sound played on zone entry |
| `icon` | `String` | Icon path for the notification |
| `majorZone` | `boolean` | Whether this is a major zone (affects display) |
| `duration` | `float` | How long the notification is visible (seconds) |
| `fadeIn` | `float` | Fade-in duration (seconds) |
| `fadeOut` | `float` | Fade-out duration (seconds) |

---

## Biomes

Biomes define terrain characteristics, vegetation, and environmental properties within a zone. Each zone has its own `BiomePatternGenerator` that determines biome layout.

### Biome Lookup

```java
// Get the biome at a specific position within a zone
Zone zone = zoneResult.getZone();
BiomePatternGenerator biomeGen = zone.biomePatternGenerator();
Biome biome = biomeGen.generateBiomeAt(zoneResult, seed, x, z);
```

### BiomePatternGenerator

Constructs the biome layout from three inputs:

| Parameter | Type | Description |
|-----------|------|-------------|
| `biomePoints` | `IPointGenerator` | Point distribution controlling biome placement |
| `tileBiomes` | `IWeightedMap<TileBiome>` | Weighted map of tile-level biome data |
| `customBiomes` | `CustomBiome[]` | Custom biome definitions with fade/transition settings |

### Biome Properties

Each `Biome` has at minimum:

| Method | Returns | Description |
|--------|---------|-------------|
| `getId()` | `int` | Unique biome identifier |

### CustomBiome Fading

Custom biomes support smooth transitions near zone borders via a fade container:

```java
CustomBiome customBiome = /* ... */;
FadeContainer fade = customBiome.getFadeContainer();
double maskFadeSum = fade.getMaskFadeSum();
double factor = fade.getMaskFactor(zoneResult);
```

---

## Caves

Caves are underground structures generated within zones. Each zone optionally has a `CaveGenerator` with one or more `CaveType` definitions.

### Cave Lookup

```java
Zone zone = /* ... */;
CaveGenerator caveGen = zone.caveGenerator();
if (caveGen != null) {
    // This zone has caves
    CaveType[] types = caveGen.getCaveTypes();
}
```

### CaveType and Biome Masks

Cave types use biome masks to control where caves can generate:

```java
// Cave type with biome restrictions
Int2FlagsCondition biomeMask = caveType.getBiomeMask();
int biomeId = biome.getId();
int flags = biomeMask.eval(biomeId);

// Check if cave can generate in this biome
if (CaveBiomeMaskFlags.canGenerate(flags)) {
    // Generate cave
}
```

| Class | Purpose |
|-------|---------|
| `CaveGenerator` | Holds cave type definitions for a zone |
| `CaveType` | Individual cave definition with shape, biome mask, etc. |
| `Int2FlagsCondition` | Evaluates biome ID → flags for cave placement |
| `CaveBiomeMaskFlags` | Utility to interpret biome mask flags (e.g., `canGenerate`) |

---

## System Integration

### Zone → Biome Integration

Each zone defines its own biome pattern. The biome generator requires the zone result (for border distance and zone context):

```java
Zone zone = zoneGen.generate(seed, x, z).getZone();
BiomePatternGenerator biomeGen = zone.biomePatternGenerator();
Biome biome = biomeGen.generateBiomeAt(zoneResult, seed, x, z);
```

### Zone → Cave Integration

Each zone defines its own cave patterns. Not all zones have caves:

```java
Zone zone = /* ... */;
CaveGenerator caveGen = zone.caveGenerator();
if (caveGen != null) {
    CaveType[] types = caveGen.getCaveTypes();
}
```

### Biome → Cave Integration

Caves use biome masks to restrict generation to specific biomes:

```java
Int2FlagsCondition biomeMask = caveType.getBiomeMask();
int biomeId = biome.getId();
int flags = biomeMask.eval(biomeId);

if (CaveBiomeMaskFlags.canGenerate(flags)) {
    // Cave can generate in this biome
}
```

### Border Transitions

All systems respect zone boundaries. Custom biomes fade near borders for smooth transitions:

```java
ZoneGeneratorResult result = zoneGen.generate(seed, x, z);
double borderDistance = result.getBorderDistance();

// Fade custom biomes near borders
if (borderDistance < customBiome.getFadeContainer().getMaskFadeSum()) {
    double factor = customBiome.getFadeContainer().getMaskFactor(result);
    // Apply fading based on factor (0.0 = fully faded, 1.0 = full strength)
}
```

---

## Key Classes Summary

| Class | Package Area | Purpose |
|-------|-------------|---------|
| `ZonePatternGenerator` | worldgen | Generates zones at world positions |
| `ZoneGeneratorResult` | worldgen | Result of zone lookup (zone + border distance) |
| `Zone` | worldgen | Zone definition (biomes, caves, prefabs, discovery) |
| `ZoneDiscoveryConfig` | worldgen | Player notification on zone entry |
| `BiomePatternGenerator` | worldgen | Biome layout within a zone |
| `Biome` | worldgen | Biome data (ID, properties) |
| `CustomBiome` | worldgen | Custom biome with fade/transition support |
| `TileBiome` | worldgen | Tile-level biome data |
| `IPointGenerator` | worldgen | Point distribution for biome placement |
| `IWeightedMap<T>` | worldgen | Weighted map for biome selection |
| `CaveGenerator` | worldgen | Cave configuration for a zone |
| `CaveType` | worldgen | Individual cave type definition |
| `Int2FlagsCondition` | worldgen | Biome mask evaluator for caves |
| `CaveBiomeMaskFlags` | worldgen | Flag utility for cave biome masks |
| `FadeContainer` | worldgen | Border fade/transition controller |

---

## Common Patterns

### Full World Position → Zone + Biome + Cave Pipeline

```java
// Complete lookup pipeline
ZonePatternGenerator zoneGen = /* from world generator */;
long seed = /* world seed */;
int x = /* world x */;
int z = /* world z */;

// 1. Determine zone
ZoneGeneratorResult zoneResult = zoneGen.generate(seed, x, z);
Zone zone = zoneResult.getZone();

// 2. Determine biome within zone
BiomePatternGenerator biomeGen = zone.biomePatternGenerator();
Biome biome = biomeGen.generateBiomeAt(zoneResult, seed, x, z);

// 3. Check for caves
CaveGenerator caveGen = zone.caveGenerator();
if (caveGen != null) {
    for (CaveType caveType : caveGen.getCaveTypes()) {
        Int2FlagsCondition biomeMask = caveType.getBiomeMask();
        int flags = biomeMask.eval(biome.getId());
        if (CaveBiomeMaskFlags.canGenerate(flags)) {
            // This cave type can generate here
        }
    }
}

// 4. Handle border transitions
double borderDistance = zoneResult.getBorderDistance();
// Use borderDistance for custom blending/fading logic
```

### Zone Entry Notification Setup

```java
// Minimal zone discovery config
ZoneDiscoveryConfig discovery = new ZoneDiscoveryConfig(
    true,                    // Show notification
    "Enchanted Forest",      // Display name
    "zone.forest.discover",  // Sound event
    "icons/forest.png",      // Icon
    true,                    // Major zone
    5.0f, 2.0f, 1.5f        // Duration, fade in, fade out
);
```

---

---

## Data-Driven World Generation (Node/JSON System)

Beyond the Java plugin API, Hytale's world generation is primarily **data-driven** through JSON asset files and an in-game **Node Editor**. Biomes, terrain shapes, materials, and content placement are all defined declaratively.

> **Detailed references** for every node type are in the `references/` subdirectory of this skill.

### Asset Directory Structure

```
Server/HytaleGenerator/
├── WorldStructure/    # Generator files defining which biomes spawn together
├── Biomes/            # Biome assets with content configurations
├── Density/           # Reusable density assets referenced by other generation assets
└── Assignments/       # Reusable prop assignment assets referenced by biomes
```

### World Instance Configuration

Instances are separate worlds that players can join. Each instance has an `Instance.bson` that specifies which generator to use:

```json
{
  "WorldGen": {
    "Type": "HytaleGenerator",
    "WorldStructure": "Basic",
    "playerSpawn": {
      "X": 123, "Y": 480, "Z": 10000,
      "Pitch": 0, "Yaw": 0, "Roll": 0
    }
  }
}
```

Instance configs are stored at `Server/Instances/`. Create custom instances by duplicating the basic config and changing `WorldStructure` to your asset name.

### Previewing World Generation

| Command | Purpose |
|---------|---------|
| `/viewport --radius 5` | Live-reload area around player as you edit |
| `/instances spawn <instance>` | Create a new world with latest changes |
| Fly south | New chunks generate with latest changes |

### Biome Asset Structure

Every biome has 5 root components:

| Component | Purpose |
|-----------|---------|
| **Terrain** | Density function nodes defining the physical terrain shape |
| **Material Provider** | Logic nodes determining which block types make up terrain |
| **Props** | Object nodes placing prefabs, trees, POIs, grass, etc. |
| **Environment Provider** | Logic nodes determining weather, NPC spawns, sounds per coordinate |
| **Tint Provider** | Function nodes determining color codes for grasses, soils, etc. |

Each biome is self-contained — it controls everything within its boundaries except Base Heights.

### Node System Overview

World generation uses a node-based system of composable JSON assets. Each node type has parameters and inputs:

| Node Category | Purpose | Key Types |
|---------------|---------|-----------|
| **Density** | 3D decimal value fields for terrain shape | SimplexNoise2D/3D, PositionsCellNoise, Distance, Ellipsoid, Cube, Cylinder, Sum, Multiplier, Mix, CurveMapper, Scale, Rotator, Warp nodes |
| **Curves** | f(x)=y mappings for value transformation | Manual, DistanceExponential, DistanceS, Ceiling/Floor, SmoothClamp, Clamp, Inverter, Min/Max |
| **Patterns** | Validate world positions by material/structure | BlockType, BlockSet, Floor, Ceiling, Wall, Surface, Gap, Cuboid, And/Or/Not, FieldFunction |
| **Material Providers** | Determine block types at positions | Constant, Solidity, Queue, SimpleHorizontal, Striped, Weighted, FieldFunction, SpaceAndDepth (with Layers and Conditions) |
| **Positions Provider** | Define infinite 3D position fields | Mesh2D, Mesh3D, List, FieldFunction, Occurrence, BaseHeight, Cache |
| **Scanners** | Scan local world areas for valid positions | Origin, ColumnLinear, ColumnRandom, Area |
| **Props** | Localized content placed in the world | Box, Density, Prefab, Column, Cluster, Union, Weighted, Queue, PondFiller |
| **Assignments** | Assign props to positions | Constant, FieldFunction, Sandwich, Weighted |
| **Directionality** | Determine prop placement direction | Static, Random, Pattern-based |
| **Vector Provider** | Define 3D vectors procedurally | Constant, DensityGradient, Cache |
| **Block Mask** | Control which materials can replace others | DontPlace, DontReplace, Advanced rules |

### Core Concept: Density Fields

Terrain generation is built on **density fields** — maps of decimal values (typically -1 to 1) defining terrain shape:
- **Positive values** → solid terrain
- **Negative values** → empty space (air)
- Density is calculated from the sum of all nodes: `f(x) = z + y`

Common terrain formula: **Simplex 2D noise** + **Y-Curve** (height gradient) = basic terrain shape.

Function nodes manipulate density fields: Absolute (ridged shapes), Normalization (rescaling range), Scale (stretch/contract), Rotator (orientation), Warp (distortion), and many more.

### Core Concept: Props Pipeline

Props place procedural content (trees, prefabs, grass) using three connected systems:

```
Positions → Scanner → Pattern → Prop Placement
```

1. **Positions** provide candidate locations (e.g., Mesh2D grid, BaseHeight offsets)
2. **Scanner** searches around each position (e.g., ColumnLinear scans Y range)
3. **Pattern** validates positions (e.g., Floor pattern checks for solid ground below)
4. **Prop** places content at validated positions (e.g., Prefab with Directionality)

### Asset Packs

Asset Packs override or add assets to the base game. Required for world gen customization:

```json
{
  "Group": "My Group",
  "Name": "Pack Example",
  "Version": "1.0.0",
  "Description": "An Example Asset Pack",
  "Authors": [{"Name": "Me", "Email": "", "Url": ""}],
  "Dependencies": {},
  "DisabledByDefault": false,
  "IncludesAssetPack": false,
  "SubPlugins": []
}
```

- Stored in `C:\Users\<user>\AppData\Roaming\Hytale\UserData\Mods`
- Per-world packs stored in `UserData\Saves\<world>\mods`
- Asset Packs exist entirely on the server — only the host needs them
- Assets inside packs override base game assets

### Import/Export System

Most node types support `Imported` and `Exported` nodes for reusability:
- **Exported**: Makes a node tree available by name, with optional `SingleInstance` for shared caching
- **Imported**: References an exported asset by name
- This enables modular, reusable density fields, curves, patterns, etc.

---

## Notes

- The `worldgen` module is a built-in Hytale module. Plugins interact with it through the public API classes listed above.
- Zone IDs must be unique across the world generation configuration.
- `CaveGenerator` is nullable — not all zones have caves.
- Biome masks use a flags-based system (`Int2FlagsCondition`) for efficient cave-biome filtering.
- Border transitions use `FadeContainer` with `getMaskFadeSum()` and `getMaskFactor()` for smooth blending between zones.
- The `--validate-world-gen` server launch flag can be used to validate world generation assets.
- World gen examples can be found in `HytaleGenerator/Biomes/Examples/`.
- The Hytale Node Editor is accessible in-game from the Content Creation menu (Tab key).
- Performance tip: In Multiplier nodes, order inputs with cheapest mask first — the node skips remaining inputs after a 0 value.
- Performance tip: Use Cache/Cache2D nodes on expensive density lookups that are queried multiple times.

> **Source:** [hytalemodding.dev — World Generation System](https://hytalemodding.dev/en/docs/guides/plugin/world-gen)
> **Source:** [HytaleModding/site — Official World Generation Documentation](https://github.com/HytaleModding/site/tree/main/content/docs/en/official-documentation/worldgen)

---

## Reference: biome-editing-guide.md

# Biome Editing Guide

> **Source:** [Official Hytale World Generation Documentation](https://github.com/HytaleModding/site/tree/main/content/docs/en/official-documentation/worldgen)

## Asset Locations

World generation assets are in the `HytaleGenerator` directory within server assets:

```
Server/HytaleGenerator/
├── WorldStructure/   # Generator files defining which biomes spawn together
├── Biomes/           # Biome assets with content configurations
├── Density/          # Reusable density assets referenced by other assets
└── Assignments/      # Prop assignment assets referenced by biome assets
```

## World Instances

Instances are separate worlds players can join via `/instances`. Each has an `Instance.bson` in `Server/Instances/`:

```json
{
  "WorldGen": {
    "Type": "HytaleGenerator",
    "WorldStructure": "Basic",
    "playerSpawn": {
      "X": 123, "Y": 480, "Z": 10000,
      "Pitch": 0, "Yaw": 0, "Roll": 0
    }
  }
}
```

Create custom instances by duplicating the basic Instance config and changing `WorldStructure` to your custom asset name.

## Editing Biomes

The Hytale Node Editor is used for editing world generation assets. Access it in-game from the Content Creation menu (press Tab).

Open biomes via the file menu, navigating to the Biomes directory:
```
Server/HytaleGenerator/Biomes/Basic.json
```

> **Requirement:** You must have an Asset Pack with a Biome.

## Biome Asset Structure

Every biome has a root Biome node that splits into 5 components:

### 1. Terrain
Mathematical function nodes that calculate the physical shape of the biome's terrain. Uses Density nodes.

### 2. Material Provider
Logical nodes determining what block types make up the biome's terrain. Uses Material Provider nodes with conditions and layers.

### 3. Props
Object function nodes that add objects such as prefabs to the terrain. Configure content like trees, POIs, grass, etc. Uses Props, Scanners, Patterns, Positions, and Assignments.

### 4. Environment Provider
Logical nodes determining the environment asset at a given coordinate within the biome. Controls weather, NPC spawns, and ambient sounds.

### 5. Tint Provider
Logical function nodes that determine a color code used by certain material types (typically grasses and soils).

> **Note:** Each biome is self-contained — it controls everything within its boundaries. The only exception is Base Heights.

## Previewing Changes

| Method | Command/Action | Description |
|--------|----------------|-------------|
| Viewport | `/viewport --radius 5` | Live-reloads an area around the player as you edit |
| New Instance | `/instances spawn <instance>` | Creates a new world showing latest changes |
| Fly South | — | New chunks generate with the latest changes |

## Asset Packs

An Asset Pack is a zip or folder with a `manifest.json` inside. All Asset Packs require a manifest, and assets must be in the correct folder structure.

```json
{
  "Group": "My Group",
  "Name": "Pack Example",
  "Version": "1.0.0",
  "Description": "An Example Asset Pack",
  "Authors": [{"Name": "Me", "Email": "", "Url": ""}],
  "Website": "",
  "Dependencies": {},
  "OptionalDependencies": {},
  "LoadBefore": {},
  "DisabledByDefault": false,
  "IncludesAssetPack": false,
  "SubPlugins": []
}
```

### Storage Locations

| Location | Path |
|----------|------|
| Global mods | `C:\Users\<user>\AppData\Roaming\Hytale\UserData\Mods` |
| Per-world | `C:\Users\<user>\AppData\Roaming\Hytale\UserData\Saves\<world>\mods` |

- Asset Packs created in-game with the Asset Editor are stored per-world. Copy to the global Mods folder for use in other worlds.
- Asset Packs exist entirely on the server — only the server/host needs them installed.
- Assets inside packs override base game assets. You can also add new assets.
- Can be downloaded from hosting sites such as CurseForge.


## Reference: curve-types.md

# Curve Types Reference

> **Source:** [Official Hytale World Generation Documentation](https://github.com/HytaleModding/site/tree/main/content/docs/en/official-documentation/worldgen)

Curves map decimal values to other decimal values (f(x) = y). Used throughout world generation for value transformation.

## Base Curves

### Manual
Plot points connected with straight lines. Function is constant before first point and after last point.
- **Parameters:** List of {x, y} points

### DistanceExponential
As input approaches Range, outputs 0.0. At input 0.0, outputs 1.0. Shape controlled by Exponent.
- **Parameters:** `Exponent` (affects shape), `Range` (value after which output is constant 0.0)

### DistanceS
Combines two DistanceExponent curves for an S-shaped profile. At input 0.0, outputs 1.0. At Range, outputs 0.0.
- **Parameters:** `ExponentA` (float > 0 — first half shape), `ExponentB` (float > 0 — second half shape), `Range` (float > 0), `Transition` (optional 0-1, default 1.0 — lower = more sudden transition), `TransitionSmooth` (optional 0-1, default 1.0)

## Limit/Clamp Curves

### Ceiling
Caps the output of a child curve.
- **Parameters:** `Ceiling` (decimal — max output), `Curve` (curve slot)

### Floor
Sets a minimum for the output of a child curve.
- **Parameters:** `Floor` (decimal — min output), `Curve` (curve slot)

### SmoothCeiling
Caps output with smooth approach to the limit.
- **Parameters:** `Ceiling` (decimal), `Range` (decimal ≥ 0 — smoothing amount, start: ¼ of child range), `Curve` (curve slot)

### SmoothFloor
Sets smooth minimum for output.
- **Parameters:** `Floor` (decimal), `Range` (decimal ≥ 0), `Curve` (curve slot)

### SmoothClamp
Limits output within walls with smooth transitions.
- **Parameters:** `WallA` (decimal), `WallB` (decimal), `Range` (decimal ≥ 0), `Curve` (curve slot)

### Clamp
Hard clamp between two walls.
- **Parameters:** `WallA` (decimal), `WallB` (decimal), `Curve` (curve slot)

## Combination Curves

### SmoothMax
Smoothed maximum of two curves.
- **Parameters:** `Range` (decimal ≥ 0 — start: ¼ of child range), `CurveA` (curve slot), `CurveB` (curve slot)

### SmoothMin
Smoothed minimum of two curves.
- **Parameters:** `Range` (decimal ≥ 0), `CurveA` (curve slot), `CurveB` (curve slot)

### Max
Maximum value of all child curves.
- **Parameters:** `Curves` (list of curve slots)

### Min
Minimum value of all child curves.
- **Parameters:** `Curves` (list of curve slots)

### Multiplier
Product of all child curves.
- **Parameters:** `Curves` (list of curve slots)

### Sum
Sum of all child curves.
- **Parameters:** `Curves` (list of curve slots)

## Logic/Transform Curves

### Inverter
Positive → negative and vice versa.
- **Parameters:** `Curve` (curve slot)

### Not
Logical NOT: when child outputs 1 → outputs 0; when 0 → outputs 1; scaled in between.
- **Parameters:** `Curve` (curve slot)

## Import

### Imported
Imports an exported Curve.
- **Parameters:** `Name` (string — the exported Density asset name)


## Reference: density-nodes.md

# Density Nodes Reference

> **Source:** [Official Hytale World Generation Documentation](https://github.com/HytaleModding/site/tree/main/content/docs/en/official-documentation/worldgen)

Density nodes define 3D decimal value fields used to shape terrain. They are composed in trees where outputs feed into inputs.

## Noise Generators

### Constant
Outputs a constant value.
- **Parameters:** `Value` (decimal)
- **Inputs:** 0

### SimplexNoise2D
Outputs [-1, 1] from a 2D simplex noise field varying on x/z plane. Automatically caches per x/z column.
- **Parameters:** `Lacunarity` (float, start: 2.0), `Persistence` (float, start: 0.5), `Scale` (float, start: 50), `Octaves` (int, start: 4), `Seed` (string)
- **Inputs:** 0

### SimplexNoise3D
Outputs [-1, 1] from a 3D simplex noise field varying on x/y/z space.
- **Parameters:** `Lacunarity` (float, start: 2.0), `Persistence` (float, start: 0.5), `ScaleXZ` (float, start: 50), `ScaleY` (float, start: 50), `Octaves` (int, start: 4), `Seed` (string)
- **Inputs:** 0

### PositionsCellNoise
Produces a 2D/3D density field based on distance from a Positions field. Supports advanced cell noise with configurable ReturnTypes.
- **Parameters:** `Positions` (positions slot), `ReturnType` (see below), `DistanceFunction` (Euclidean or Manhattan), `MaxDistance` (float — set slightly over half the distance between position points)
- **Inputs:** 0

**ReturnTypes:**
| Type | Description | Extra Parameters |
|------|-------------|------------------|
| CellValue | Constant density inside each cell sampled from a Density field | `Density` (slot), `DefaultValue` (float) |
| Density | Cell populated with a Density field picked from Delimiters by ChoiceDensity | `Density` (slot), `DefaultValue` (float) |
| Curve | Value defined by a Curve asset based on distance from Positions | `Curve` (curve slot) |
| Distance | Traditional CellNoise distance return | None |
| Distance2 | Traditional CellNoise distance2 return | None |
| Distance2Add | Sum of two nearest distances | None |
| Distance2Sub | Difference of two nearest distances | None |
| Distance2Mul | Product of two nearest distances | None |
| Distance2Div | Division of two nearest distances | None |

**DistanceFunction:** `Euclidean` or `Manhattan` (no parameters)

## Shape Generators

### Distance
Value based on distance from origin {0,0,0}.
- **Parameters:** `Curve` (curve slot — maps distance to density)
- **Inputs:** 0

### Ellipsoid
Deformed sphere with scale, rotation, and spin.
- **Parameters:** `Curve` (curve slot), `Scale` (3D vector slot), `X/Y/Z` (floats), `Spin` (degrees around Y axis)
- **Inputs:** 0
- **Deformation order:** 1. Scale → 2. Align Y axis → 3. Spin

### Cube
Density based on distance from origin axis.
- **Parameters:** `Curve` (curve slot)
- **Inputs:** 0

### Cuboid
Deformed cube with scale, rotation, and spin.
- **Parameters:** `Curve` (curve slot), `Scale` (3D vector slot), `X/Y/Z` (floats), `Spin` (degrees), `NewYAxis` (Point3D slot)
- **Inputs:** 0
- **Deformation order:** 1. Scale → 2. Align Y axis → 3. Spin

### Cylinder
Cylindrical shape with axial and radial curves.
- **Parameters:** `AxialCurve` (curve — density along Y axis), `RadialCurve` (curve — density by distance from Y axis), `Spin` (degrees), `NewYAxis` (Point3D slot)
- **Inputs:** 0

### Axis
Density based on distance from a line through origin.
- **Parameters:** `Axis` (3D vector slot), `X/Y/Z` (floats), `Curve` (curve slot), `IsAnchored` (boolean — uses closest anchor)
- **Inputs:** 0

### Plane
Density based on distance from a user-defined plane through origin.
- **Parameters:** `PlaneNormal` (3D vector slot), `X/Y/Z` (floats), `Curve` (curve slot)
- **Inputs:** 0

### Shell
Density regions of a shell around origin based on direction and distance.
- **Parameters:** `Axis` (3D vector slot), `X/Y/Z` (floats), `Mirror` (boolean), `AngleCurve` (curve slot), `DistanceCurve` (curve slot)
- **Inputs:** 0

## Math Operations

### Sum
Output is the sum of all inputs.
- **Inputs:** [0, ∞)

### Multiplier
Output is the product of all inputs. **Performance tip:** Skips remaining inputs after a 0 value — order with cheapest mask first for optimization (up to ~40% improvement).
- **Inputs:** [0, ∞)

### Max / Min
Greatest/smallest value of all inputs (0 if no inputs).
- **Inputs:** [0, ∞)

### SmoothMax / SmoothMin
Smoothed maximum/minimum between two inputs.
- **Parameters:** `Range` (float, start: 0.2 — greater = more smoothing)
- **Inputs:** 2

### Mix
Mixes two inputs controlled by a third gauge input.
- Gauge ≤ 0.0 → only Density A; Gauge ≥ 1.0 → only Density B; between = proportional mix
- **Inputs:** 3 (Density A, Density B, Gauge)

### MultiMix
Mixes multiple inputs with a Gauge (last input). Keys pin inputs to gauge values.
- **Inputs:** unlimited (last is Gauge)

### Clamp
Ensures output is within [WallA, WallB].
- **Parameters:** `WallA` (float), `WallB` (float)
- **Inputs:** 1

### SmoothClamp
Like Clamp but with smooth transition at limits.
- **Parameters:** `WallA` (float), `WallB` (float), `Range` (float — larger = smoother)
- **Inputs:** 1

### Abs
Absolute value of input.
- **Inputs:** 1

### Sqrt
Square root (modified for negative inputs to always return useful values).
- **Inputs:** 1

### Inverter
Input × -1.
- **Inputs:** 1

### Pow
Input raised to exponent power (modified for negative inputs).
- **Parameters:** `Exponent` (float, start: 2)
- **Inputs:** 1

### CurveMapper
Maps input through a Curve.
- **Parameters:** `Curve` (curve slot)
- **Inputs:** 1

### Normalizer
Rescales input from one range to another.
- **Parameters:** `FromMin`, `FromMax`, `ToMin`, `ToMax` (all floats)
- **Inputs:** 1

## Coordinate & Cache

### XValue / YValue / ZValue
Outputs the local X/Y/Z coordinate.
- **Inputs:** 0

### XOverride / YOverride / ZOverride
Overrides the X/Y/Z coordinate that the input sees.
- **Inputs:** 1

### Cache
Caches input for current coordinates.
- **Parameters:** `Capacity` (int, safe value: 3)
- **Inputs:** 1

## Transform Nodes

### Scale
Stretches/contracts the input density field per axis. Values > 1 stretch, < 1 contract, < 0 flip.
- **Parameters:** `X`, `Y`, `Z` (floats)
- **Inputs:** 1
- **Tip:** Combine with Rotator nodes for non-orthogonal scaling

### Rotator
Aligns input field's Y axis to a new axis and spins.
- **Parameters:** `NewYAxis` (3D vector), `X/Y/Z` (floats), `SpinAngle` (degrees)
- **Inputs:** 1

### Slider
Slides input field in a direction.
- **Parameters:** `SlideX`, `SlideY`, `SlideZ` (floats)
- **Inputs:** 1

### Anchor
Anchors the child field's origin to the contextual Anchor (e.g., cell center from PositionsCellNoise Density ReturnType).
- **Parameters:** `Reverse` (boolean — if true, moves origin back to world origin)
- **Inputs:** 1

## Warp Nodes

### GradientWarp
Warps first input based on gradient of second input. Relatively expensive — minimize use space. Incorporates Cache2D.
- **Parameters:** `SampleRange` (float, recommend 1), `WarpFactor` (float — larger = more warping), `2D` (boolean — uses internal Cache2D), `YFor2D` (float, default 0)
- **Inputs:** 2 (field to warp, warping field)

### FastGradientWarp
Faster implementation using internal simplex noise generator.
- **Parameters:** `WarpScale` (float), `WarpLacunarity` (float), `WarpPersistence` (float), `WarpOctaves` (int), `WarpFactor` (float — max warp distance)
- **Inputs:** 1

### VectorWarp
Warps input along a provided vector. Warp amount = second input value × WarpFactor.
- **Parameters:** `WarpFactor` (float), `WarpVector` (vector slot), `X/Y/Z` (floats)
- **Inputs:** 2 (field to warp, warping intensity field)

### PositionsPinch
Pinches or expands density field around Positions. PinchCurve defines effect shape, MaxDistance defines range.
- **Parameters:** `Positions` (slot), `PinchCurve` (curve slot), `MaxDistance` (float), `NormalizeDistance` (boolean, default true), `HorizontalPinch` (boolean, default false), `PositionsMaxY`, `PositionsMinY` (floats)
- **Inputs:** 1

### PositionsTwist
Twists density field around Positions. TwistCurve output is in degrees (360 = full rotation).
- **Parameters:** `Positions` (slot), `TwistCurve` (curve slot), `TwistAxis` (vector slot), `X/Y/Z` (floats), `MaxDistance` (float), `NormalizeDistance` (boolean, default true)
- **Inputs:** 1

## Context Nodes

### Angle
Angle in degrees between two vectors.
- **Parameters:** `Vector` (3D vector), `VectorProvider` (procedural vector)
- **Inputs:** 0

### DistanceToBiomeEdge
Outputs distance to nearest biome edge in blocks.
- **Inputs:** 0

### Terrain
Outputs interpolated terrain Density. **Only for MaterialProvider nodes — not for Terrain Density nodes.**
- **Inputs:** 0

### BaseHeight
References a BaseHeight from WorldStructure.
- **Parameters:** `BaseHeightName` (string), `Distance` (boolean — false: raw Y coordinate, true: distance from BaseHeight)
- **Inputs:** 0

## Branching Nodes

### Switch
Switches between Density branches based on contextual SwitchState string.
- **Parameters:** `SwitchCases` (list of case slots with `CaseState` string + `Density` slot)
- **Inputs:** 1

### SwitchState
Sets the contextual SwitchState for downstream branches.
- **Parameters:** `SwitchState` (string)
- **Inputs:** 1

## Import/Export

### Imported
Imports an exported Density asset.
- **Parameters:** `Name` (string)

### Exported
Exports a Density field. `SingleInstance` shares the exported tree across all importers (useful for cache optimization).
- **Parameters:** `SingleInstance` (boolean), `Density` (slot)
- **Inputs:** 1
- **Note:** Experimental feature — may cause unexpected behaviors if misused.


## Reference: prop-placement-nodes.md

# Prop Placement Nodes Reference (Props, Positions, Scanners, Assignments, Directionality)

> **Source:** [Official Hytale World Generation Documentation](https://github.com/HytaleModding/site/tree/main/content/docs/en/official-documentation/worldgen)

These node categories work together in a pipeline to place content in the world:

```
Positions (where to look) → Scanner (how to search) → Pattern (what's valid) → Prop (what to place)
                                                                                  ↑
                                                                            Assignments (which prop)
                                                                            Directionality (facing)
```

---

## Props

Localized content that reads and writes to the world.

### Box
Testing/debugging prop that generates a box. Uses Pattern and Scanner.
- **Parameters:** `Range` (3D int vector: X, Y, Z distances), `BoxBlockType` (block type), `Pattern` (Pattern slot), `Scanner` (Scanner slot)

### Density
Generates a prop from a Density field and MaterialProvider. Uses Pattern, Scanner, and BlockMask.
- **Parameters:** `Range` (3D int vector), `PlacementMask` (BlockMask slot), `Pattern` (Pattern slot), `Scanner` (Scanner slot), `Density` (Density slot — shape), `Material` (MaterialProvider slot — block types)

### Prefab
Places a Prefab structure in a suitable spot. Uses Directionality, Scanner, and BlockMask.
- **Parameters:**
  - `WeightedPrefabPaths` — list of weighted entries: `Path` (folder/file), `Weight` (float), `LegacyPath` (boolean — true: `Server/World/Default/Prefabs/`, false: `Server/WorldgenAlt/Prefabs/`)
  - `Directionality` (slot — controls facing)
  - `Scanner` (Scanner slot)
  - `BlockMask` (BlockMask slot)
  - `MoldingDirection` (optional: "UP", "DOWN", or "NONE" default)
  - `MoldingChildren` (optional boolean — children also mold)
  - `MoldingScanner` (optional Scanner slot — use LinearScanner with Local=true, ResultCap=1)
  - `MoldingPattern` (optional Pattern slot — finds surface to mold to)
  - `LoadEntities` (optional boolean — load entities from prefabs)

### Column
Prop contained within a single column.
- **Parameters:** `ColumnBlocks` (list of `BlockType` string + `Y` int relative to origin), `Directionality` (slot), `Scanner` (Scanner slot), `BlockMask` (optional)

### Cluster
Places a cluster of Column Props around an origin. Density of placement varies by distance from origin.
- **Parameters:** `Range` (int — distance from origin, start: 10), `DistanceCurve` (curve — density by distance), `Seed` (string), `Pattern` (optional), `Scanner` (optional), `WeightedProps` (list of `Weight` float + `ColumnProp` slot)
- **Important:** Column Props in a Cluster must use Scanner/Pattern that operate within a single column.

### Union
Places all props in the list at the same position.
- **Parameters:** `Props` (list of prop slots)

### Offset
Offsets child Prop's position.
- **Parameters:** `Offset` (3D int vector), `Prop` (Prop slot)

### Weighted
Picks which Prop to place based on seed and weights.
- **Parameters:** `Entries` (list of `Weight` float + `Prop` slot), `Seed` (string)

### Queue
Places first Prop in queue that can be placed (based on its Scanner/Pattern config).
- **Parameters:** `Queue` (ordered list of Prop slots, first = highest priority)

### PondFiller
Fills terrain depressions with material (e.g., water). **Performance note:** impact depends on bounding box size — optimize per use case.
- **Parameters:** `BoundingMin` (3D point — lowest corner), `BoundingMax` (3D point — greatest corner), `BarrierBlockSet` (BlockSet slot — solid terrain types), `FillMaterial` (MaterialProvider slot — fill blocks), `Pattern` (Pattern slot), `Scanner` (Scanner slot)

### Imported
Imports an exported Prop.
- **Parameters:** `Name` (string)

---

## Positions Provider

Defines infinite 3D position fields used by Props and Density nodes.

### Mesh2D
Generates a mesh of random points on a 2D plane.
- **Parameters:** `PointGenerator` (slot), `PointsY` (vertical Y position)
- **PointGenerator Parameters:** `Jitter` (0-0.5, start: 0.2), `ScaleX`/`ScaleY`/`ScaleZ` (positive decimals), `Seed` (string)

### Mesh3D
Generates a mesh of random points in 3D space.
- **Parameters:** `PointGenerator` (slot)

### List
Static list of positions in world coordinates.
- **Parameters:** `Positions` (list of X, Y, Z integers)

### Anchor
Anchors child Positions to contextual Anchor point.
- **Parameters:** `Reverse` (boolean — reverses back to world origin)

### Sphere
Masks out positions farther than Range from origin.
- **Parameters:** `Range` (decimal — max distance)

### FieldFunction
Masks positions using a Density field and delimiters.
- **Parameters:** `FieldFunction` (Density slot), `Delimiters` (list with `Min`/`Max`), `Positions` (slot)

### Occurrence
Discards positions based on Density field probability.
- Density ≤ 0 → 0% chance kept; Density ≥ 1 → 100% kept; between = proportional
- **Parameters:** `FieldFunction` (Density slot), `Seed` (string), `Positions` (slot)

### Offset
Offsets positions by a vector.
- **Parameters:** `OffsetX`, `OffsetY`, `OffsetZ` (decimals), `Positions` (slot)

### BaseHeight
Vertically offsets positions by BaseHeight amount. Positions outside the Y region are discarded.
- **Parameters:** `BaseHeightName` (string), `MaxYRead` (decimal — exclusive), `MinYRead` (decimal — inclusive)

### Union
Combines all positions into one field.
- **Parameters:** `Positions` (list of slots)

### SimpleHorizontal
Keeps only positions within a Y range.
- **Parameters:** `RangeY` (range), `Positions` (slot)

### Cache
Caches output in 3D sections. Useful for expensive Positions trees queried multiple times.
- **Parameters:** `SectionsSize` (int > 0, start: 32), `CacheSize` (int ≥ 0, start: 50; 0 = no cache), `Positions` (slot)
- **Tip:** Place close to the root of Positions tree.

### Imported
Imports an exported PositionProvider.
- **Parameters:** `Name` (string)

---

## Scanners

Scan local parts of the world for valid positions matching a Pattern.

### Origin
Only scans the origin position.
- No parameters.

### ColumnLinear
Scans a column of blocks linearly (top-down or bottom-up).
- **Parameters:** `MaxY` (int — upper exclusive), `MinY` (int — lower inclusive), `RelativeToPosition` (boolean — relative to scan origin Y instead of world Y:0), `BaseHeightName` (optional string — overrides RelativeToPosition), `TopDownOrder` (boolean), `ResultCap` (positive int — max valid results)

### ColumnRandom
Scans a column randomly with two strategies.
- **Parameters:** `MaxY` (int), `MinY` (int), `Strategy` ("DART_THROW" — random samples, good for many valid positions; or "PICK_VALID" — finds all valid then picks, good for few valid positions), `Seed` (string), `ResultCap` (int), `RelativeToPosition` (boolean), `BaseHeightName` (optional string)

### Area
Scans an expanding area around the origin using a child Scanner.
- **Parameters:** `ScanRange` (int ≥ 0 — distance in blocks, start: 0), `ScanShape` ("CIRCLE" or "SQUARE"), `ResultCap` (int), `ChildScanner` (Scanner slot — applied to each column)

### Imported
Imports an exported Scanner.
- **Parameters:** `Name` (string)

---

## Assignments

Assign Props to each position in a Positions field.

### Constant
Assigns one Prop to all positions.
- **Parameters:** `Prop` (Prop slot)

### FieldFunction
Selects which props to assign based on a Density field and value delimiters.
- **Parameters:** `FieldFunction` (Density slot), `Delimiters` (list with `Min`/`Max` + `Assignments` slot)

### Sandwich
Selects props based on vertical (world Y) position delimiters.
- **Parameters:** `Delimiters` (list with `MinY`/`MaxY` + `Assignments` slot)

### Weighted
Picks props randomly based on weights and seed.
- **Parameters:** `Seed` (string), `SkipChance` (0-1 — chance to skip), `WeightedAssignments` (list of weighted Assignments slots)

### Imported
Imports an exported Assignments.
- **Parameters:** `Name` (string)

---

## Directionality

Determines the direction (rotation) to place a Prop.

### Static
Fixed direction.
- **Parameters:** `Rotation` (int: 0, 90, 180, or 270; default 0), `Pattern` (Pattern slot — locates position, doesn't affect direction)

### Random
Random direction based on seed.
- **Parameters:** `Seed` (string), `Pattern` (Pattern slot)

### Pattern (Directionality Type)
Direction based on environment. Links directions to Pattern assets for each cardinal direction.
- **Parameters:** `InitialDirection` (string: "N", "S", "E", "W" — prop's original facing), `NorthPattern`/`SouthPattern`/`EastPattern`/`WestPattern` (Pattern slots), `Seed` (string — for tie-breaking)

---

## Vector Provider

Defines 3D decimal vectors procedurally.

### Constant
Fixed vector.
- **Parameters:** `Vector` (3D decimal vector)

### DensityGradient
Gradient of a Density field — shows direction/rate of density change.
- **Parameters:** `SampleDistance` (positive decimal, optimal: 1.0), `Density` (Density slot)

### Cache
Caches vector per position. Only use if downstream VectorProvider is expensive and queried multiple times.
- **Parameters:** `SampleDistance` (decimal), `Density` (Density slot)

### Exported
Exports a VectorProvider. SingleInstance shares across all importers.
- **Parameters:** `SingleInstance` (boolean), `VectorProvider` (slot)
- **Note:** Experimental feature.

### Imported
Imports an exported VectorProvider.
- **Parameters:** `Name` (string)


## Reference: terrain-nodes.md

# Terrain Nodes Reference (Patterns, Material Providers, Block Mask)

> **Source:** [Official Hytale World Generation Documentation](https://github.com/HytaleModding/site/tree/main/content/docs/en/official-documentation/worldgen)

---

## Patterns

Patterns validate world locations based on material composition and other criteria. Used by Props and Scanners to find valid positions.

### BlockType
Checks against the block's material.
- **Parameters:** `Material` (block material name)

### BlockSet
Checks if block material belongs to a BlockSet.
- **Parameters:** `BlockSet` (slot)

### Offset
Offsets the child Pattern by a vector.
- **Parameters:** `Pattern` (Pattern slot), `Offset` (3D integer vector)

### Floor
Checks for a floor below the position.
- **Parameters:** `Floor` (Pattern slot — validates block under origin), `Origin` (Pattern slot — validates at origin)

### Ceiling
Checks for a ceiling above the position.
- **Parameters:** `Ceiling` (Pattern slot — validates block above origin), `Origin` (Pattern slot — validates at origin)

### Wall
Checks for a wall next to the position. Supports N/S/E/W directions.
- **Parameters:** `Wall` (Pattern slot — validates adjacent block), `Origin` (Pattern slot), `Directions` (list: "N", "S", "E", "W"), `RequireAllDirections` (boolean)

### Surface
Validates a transition from one set of materials to another (e.g., soil floor in air).
- **Parameters:** `Surface` (Pattern slot), `Medium` (Pattern slot), `SurfaceRadius` (decimal ≥ 0), `MediumRadius` (decimal ≥ 0), `SurfaceGap` (int ≥ 0), `MediumGap` (int ≥ 0), `Facings` (list: "N","S","E","W","U","D"), `RequireAllFacings` (boolean)

### Gap
Validates a space between two anchors (e.g., for bridge placement).
- **Parameters:** `GapSize` (decimal ≥ 0), `AnchorSize` (decimal ≥ 0), `AnchorRoughness` (decimal ≥ 0, start: 1), `DepthDown` (int ≥ 0), `DepthUp` (int ≥ 0), `Angles` (list of degrees, 0=Z axis, 90=X axis), `GapPattern` (Pattern slot), `AnchorPattern` (Pattern slot)

### Cuboid
Defines a cuboid region relative to origin. Validates if all inner positions pass SubPattern.
- **Parameters:** `Min` (3D point — inclusive min), `Max` (3D point — inclusive max), `SubPattern` (Pattern slot)

### And / Or / Not
Logical operators combining patterns.
- **And Parameters:** `Patterns` (list of Pattern slots) — all must validate
- **Or Parameters:** `Patterns` (list of Pattern slots) — at least one must validate
- **Not Parameters:** `Pattern` (single Pattern slot) — validates where nested does not

### FieldFunction
Validates if Density field value at position is within delimiters. **Performance note:** expensive — place last in Pattern hierarchy.
- **Parameters:** `FieldFunction` (Density slot), `Delimiters` (list with `Min`/`Max` decimal bounds)

### Imported
Imports an exported Pattern.
- **Parameters:** `Name` (string)

---

## Material Providers

Determine which block type to place at each position.

### Constant
One constant block type.
- **Parameters:** `BlockType` (string, e.g. "Rock_Stone")

### Solidity
Splits into Solid and Empty terrain blocks.
- **Parameters:** `Solid` (Material Provider slot), `Empty` (Material Provider slot)

### Queue
Priority queue of Material Providers. First slot to provide a block wins.
- **Parameters:** `Queue` (list of Material Provider slots, top = highest priority)

### SimpleHorizontal
Applies child Material Provider on a vertical range. If BaseHeight provided, Y values are relative to it.
- **Parameters:** `TopY` (int), `Top BaseHeight` (string, optional), `BottomY` (int), `Bottom BaseHeight` (string, optional), `Material` (Material Provider slot)

### Striped
Applies Material Provider on horizontal stripes of varying thickness.
- **Parameters:** `Stripes` (list with `TopY`/`BottomY` ints, inclusive), `Material` (Material Provider slot)

### Weighted
Picks Material Provider from weighted list.
- **Parameters:** `Seed` (string), `SkipChance` (0-1 — % of blocks skipped), `WeightedMaterials` (list with `Weight`/`Material` entries)

### FieldFunction
Selects 3D region using a noise function and value delimiters.
- **Parameters:** `FieldFunction` (Density slot — shares seeds with terrain density), `Delimiters` (list with `From`/`To` floats + `Material` slot; higher in list = higher priority)

### SpaceAndDepth
Places layers of blocks on floor or ceiling surfaces. Layers pile like a cake into the surface depth.
- **Parameters:** `LayerContext` (string: "DEPTH_INTO_FLOOR" or "DEPTH_INTO_CEILING"), `MaxExpectedDepth` (int — sum of max thicknesses of all layers), `Condition` (optional Condition slot), `Layers` (list of Layer objects)

#### Condition Types
Conditions check environment validity before applying material.

| Type | Parameters | Description |
|------|-----------|-------------|
| EqualsCondition | `ContextToCheck`, `Value` (int) | Context equals value |
| GreaterThanCondition | `ContextToCheck`, `Threshold` (int) | Context > threshold |
| SmallerThanCondition | `ContextToCheck`, `Threshold` (int) | Context < threshold |
| AndCondition | `Conditions` (list) | All must validate |
| OrCondition | `Conditions` (list) | Any must validate |
| NotCondition | `Condition` (single slot) | Inverts result |
| AlwaysTrueCondition | None | Always validates |

Context values: `SPACE_ABOVE_FLOOR`, `SPACE_BELOW_CEILING`

#### Layer Types

| Type | Parameters | Description |
|------|-----------|-------------|
| ConstantThickness | `Material` (slot), `Thickness` (int ≥ 0) | Same thickness everywhere |
| RangeThickness | `Material` (slot), `RangeMin`/`RangeMax` (int ≥ 0), `Seed` | Random thickness in range |
| WeightedThickness | `Material` (slot), `PossibleThicknesses` (weighted list), `Seed` | Weighted random thickness |
| NoiseThickness | `Material` (slot), `ThicknessFunctionXZ` (Density slot — 2D) | Thickness from noise function |

### Imported
Imports an exported MaterialProvider.
- **Parameters:** `Name` (string)

---

## Block Mask

Controls which materials can replace which other materials when placing content.

- **Source Material:** The material being placed
- **Destination Material:** The material already in the world

### Rules

| Rule | Description |
|------|-------------|
| **DontPlace** | BlockSet — source materials that will not be placed |
| **DontReplace** | BlockSet — destination materials that cannot be replaced (default) |
| **Advanced** | List of override rules with `Source` BlockSet and `CanReplace` BlockSet |

Advanced rules override DontReplace for specific source/destination combinations.

**Parameters:** `DontPlace` (BlockSet slot), `DontReplace` (BlockSet slot), `Advanced` (list of rules)


## Reference: world-gen-concepts.md

# World Generation Concepts

> **Source:** [Official Hytale World Generation Documentation](https://github.com/HytaleModding/site/tree/main/content/docs/en/official-documentation/worldgen)

## Generative Noise

Hytale's terrain generation is founded on **density fields** — maps of decimal values used to define terrain shape. Density fields can be built from sources of procedural noise (such as Simplex and Cellular), contextual data, and processing nodes.

## Density and Solidity

Density generates a range of values, typically between 1 and -1:
- **Positive values** = solid terrain
- **Negative values** = empty space (air)
- Density is calculated from the sum: `f(x) = z + y`

## Function Nodes

All density fields can be manipulated with function nodes provided in the node editor. Important transformations:

### Absolute
Makes all negative values positive, creating ridged shapes on noise fields.

### Normalization
Moves the 0 value to a new position and stretches the field to a new range. Used to control what fraction of the field is solid vs empty.

> **Note:** The Hytale Node Editor does not currently support noise visualization, but examples can be found in `HytaleGenerator/Biomes/Examples/`.

## Noise-Based Terrain

A basic terrain shape is created by combining:
1. **Simplex 2D** — 2-dimensional generative noise field
2. **Y-Curve** — 2-dimensional curve drawn between a height differential

This formula creates most of the terrain in Hytale.

## Material Providers

Materials use logical function nodes to determine which block type is placed at each location. These run on:
- **Solid portions** of the terrain field (terrain blocks)
- **Negative portions** (e.g., filling in a water level)

Key concepts:
- **Solidity** splits into Solid and Empty material providers
- **Queue** creates a priority list of materials (top = highest priority)

> **Note:** The Hytale Node Editor sets node priority based on position in the editor, shown as a number in the top right corner.

## Props

Props generate content in specific limited regions. Hytale provides different Prop types for procedurally placing content in the world.

The prop placement pipeline:

```
Positions → Scanner → Pattern → Content Placement
```

| Component | Purpose |
|-----------|---------|
| **Positions** | Provides candidate locations for scanning |
| **Scanner** | Defines an area/column around each position to search |
| **Pattern** | Validates positions based on world material composition |


