# Hytale Biome Patterns Reference

This document captures advanced worldgen patterns extracted from Hytale's official biome assets. Use this as a reference when creating custom biomes for realm arenas or other procedural content.

## File Locations

- **Official Biomes**: `/Assets/Server/HytaleGenerator/Biomes/{Zone}/`
- **Assignments (reusable)**: `/Assets/Server/HytaleGenerator/Assignments/{Zone}/`
- **Prefabs**: `/Assets/Server/Prefabs/`

---

## Biome JSON Structure

```json
{
  "Name": "BiomeName",
  "Terrain": { /* Density functions for terrain shape */ },
  "MaterialProvider": { /* Block types for solid/empty */ },
  "EnvironmentProvider": { /* Sky, fog, lighting */ },
  "TintProvider": { /* Grass/foliage color */ },
  "Props": [ /* Array of prop spawn configurations */ ]
}
```

---

## Props System

### Basic Structure
Each prop entry has:
```json
{
  "Skip": false,
  "Runtime": 0,  // 0 = terrain phase, 1 = decoration phase
  "Positions": { /* Where to check for spawns */ },
  "Assignments": { /* What to spawn */ }
}
```

---

## Position Providers

### 1. Mesh2D (Grid-based)
Basic grid with jitter for natural distribution:
```json
{
  "Type": "Mesh2D",
  "Skip": false,
  "PointsY": 256,  // Y level to start scanning (use 256 for realms at high Y)
  "PointGenerator": {
    "Type": "Mesh",
    "Jitter": 0.4,      // 0-1, randomness in position
    "ScaleX": 20,       // Spacing between points
    "ScaleY": 20,
    "ScaleZ": 20,
    "Seed": "UniqueString"
  }
}
```

### 2. Occurrence (Density-controlled)
Wraps another position provider with probability:
```json
{
  "Type": "Occurrence",
  "Skip": false,
  "Seed": "A",
  "FieldFunction": {
    "Type": "Constant",
    "Value": 0.2  // 20% spawn chance
  },
  "Positions": { /* Inner Mesh2D or other */ }
}
```

### 3. FieldFunction Positions (Zone filtering)
Filter positions based on noise zones:
```json
{
  "Type": "FieldFunction",
  "Skip": false,
  "FieldFunction": {
    "Type": "SimplexNoise2D",
    "Lacunarity": 2,
    "Persistence": 0.5,
    "Octaves": 1,
    "Scale": 200,
    "Seed": "ZoneNoise"
  },
  "Delimiters": [{
    "Min": -1,
    "Max": -0.3  // Only spawn where noise is in this range
  }],
  "Positions": { /* Inner positions */ }
}
```

---

## Assignment Types

### 1. Constant (Single prop type)
```json
{
  "Type": "Constant",
  "Prop": { /* Prop definition */ }
}
```

### 2. Weighted (Random selection)
```json
{
  "Type": "Weighted",
  "Seed": "AssignSeed",
  "SkipChance": 0.4,  // 40% chance to skip entirely
  "WeightedAssignments": [
    { "Weight": 70, "Assignments": { /* ... */ } },
    { "Weight": 30, "Assignments": { /* ... */ } }
  ]
}
```

### 3. FieldFunction Assignments (Zone-based variety)
Different props in different noise zones:
```json
{
  "Type": "FieldFunction",
  "ExportAs": "ExportName",
  "FieldFunction": {
    "Type": "SimplexNoise2D",
    "Scale": 80,
    "Seed": "ZoneSeed"
  },
  "Delimiters": [
    {
      "Min": 0.7,
      "Max": 0.85,
      "Assignments": { /* Small trees zone */ }
    },
    {
      "Min": 0.85,
      "Max": 1,
      "Assignments": { /* Large trees zone */ }
    }
  ]
}
```

### 4. Imported (Reuse from file)
```json
{
  "Type": "Imported",
  "Name": "Plains1_Oak_Trees"
}
```

---

## Prop Types

### 1. Prefab (Structure from file)
```json
{
  "Type": "Prefab",
  "Skip": false,
  "WeightedPrefabPaths": [
    { "Path": "Trees/Oak/Stage_3", "Weight": 60 },
    { "Path": "Trees/Oak/Stage_4", "Weight": 40 }
  ],
  "LegacyPath": false,
  "LoadEntities": true,  // Load entities like harvestable berries
  "Directionality": { /* Rotation/placement rules */ },
  "Scanner": { /* How to find valid position */ },
  "BlockMask": { /* Optional: blocks to not replace */ },
  "MoldingDirection": "None",
  "MoldingChildren": false
}
```

### 2. Union (Multiple props together)
Spawn tree + debris around it:
```json
{
  "Type": "Union",
  "Skip": false,
  "Props": [
    {
      "Type": "Prefab",
      "WeightedPrefabPaths": [{ "Path": "Trees/Oak/Stage_3", "Weight": 100 }],
      /* ... */
    },
    {
      "Type": "Cluster",
      "Range": 10,
      /* ... scatter sticks/leaves around */
    }
  ]
}
```

### 3. Cluster (Scattered items around point)
```json
{
  "Type": "Cluster",
  "Skip": false,
  "Range": 10,  // Max distance from center
  "Seed": "ClusterSeed",
  "DistanceCurve": {
    "Type": "Manual",
    "Points": [
      { "In": 0, "Out": 0.8 },   // High chance at center
      { "In": 2, "Out": 0.8 },   // Still high at 2 blocks
      { "In": 3, "Out": 0 }      // Zero chance at 3+ blocks
    ]
  },
  "WeightedProps": [
    {
      "Weight": 1,
      "ColumnProp": { /* Column prop definition */ }
    }
  ],
  "Pattern": { /* Floor pattern for cluster items */ },
  "Scanner": { /* Scanner for cluster items */ }
}
```

### 4. Column (Single-block placement)
For simple decorations:
```json
{
  "Type": "Column",
  "Skip": false,
  "ColumnBlocks": [
    {
      "Y": 0,  // Relative Y offset
      "Material": { "Solid": "Plant_Bush_Green" }
    }
  ],
  "Directionality": { /* ... */ },
  "Scanner": { /* ... */ }
}
```

---

## Directionality (Rotation)

### Random Rotation
```json
{
  "Type": "Random",
  "Seed": "RotationSeed",
  "Pattern": { /* Placement pattern */ }
}
```

### Static Rotation
```json
{
  "Type": "Static",
  "Rotation": 0,  // 0, 90, 180, 270
  "Pattern": { /* Placement pattern */ }
}
```

---

## Patterns (Placement Rules)

### 1. Floor Pattern (Most common)
Place on top of solid block:
```json
{
  "Type": "Floor",
  "Skip": false,
  "Origin": {
    "Type": "BlockType",
    "Skip": false,
    "Material": { "Solid": "Empty" }  // Current pos must be empty
  },
  "Floor": {
    "Type": "BlockSet",
    "Skip": false,
    "BlockSet": {
      "Inclusive": true,
      "Materials": [
        { "Solid": "Soil_Grass" },
        { "Solid": "Soil_Grass_Sunny" },
        { "Solid": "Soil_Grass_Full" },
        { "Solid": "Soil_Pathway" },
        { "Solid": "Soil_Leaves" }
      ]
    }
  }
}
```

### 2. Ceiling Pattern
Place under solid block (for hanging things):
```json
{
  "Type": "Ceiling",
  "Skip": false,
  "Origin": {
    "Type": "BlockType",
    "Material": { "Solid": "Empty" }
  },
  "Ceiling": {
    "Type": "BlockSet",
    "BlockSet": {
      "Inclusive": true,
      "Materials": [{ "Solid": "Rock_Stone" }]
    }
  }
}
```

### 3. And Pattern (Multiple conditions)
```json
{
  "Type": "And",
  "Skip": false,
  "Patterns": [
    { /* Pattern 1 */ },
    { /* Pattern 2 */ }
  ]
}
```

### 4. Offset Pattern (Check nearby)
```json
{
  "Type": "Offset",
  "Skip": false,
  "Pattern": { /* Inner pattern to check */ },
  "Offset": { "X": 0, "Y": -1, "Z": 0 }  // Check 1 block below
}
```

### 5. BlockType Pattern (Single block check)
```json
{
  "Type": "BlockType",
  "Skip": false,
  "Material": { "Solid": "Empty" }
}
```

### 6. BlockSet Pattern (Multiple block check)
```json
{
  "Type": "BlockSet",
  "Skip": false,
  "BlockSet": {
    "Inclusive": true,  // true = whitelist, false = blacklist
    "Materials": [
      { "Solid": "Soil_Grass" },
      { "Solid": "Soil_Dirt" }
    ]
  }
}
```

### 7. Imported Pattern (Reuse)
```json
{
  "Type": "Imported",
  "Skip": false,
  "Name": "Plains1_OakPattern_Floor"
}
```

---

## Scanners (Find valid Y position)

### ColumnLinear Scanner
```json
{
  "Type": "ColumnLinear",
  "Skip": false,
  "MaxY": 60,
  "MinY": 0,
  "RelativeToPosition": false,  // false = relative to BaseHeight
  "BaseHeightName": "Base",
  "TopDownOrder": true,  // true = scan from top, false = from bottom
  "ResultCap": 1  // Max results to return
}
```

**Guidelines:**
- Trees/large: `MaxY: 60, MinY: 0, RelativeToPosition: false`
- Small items near point: `MaxY: 5, MinY: -5, RelativeToPosition: true`
- For realms at Y=256, use `PointsY: 256` in Mesh2D

---

## BlockMask (Protect blocks)

Prevent prefab from overwriting certain blocks:
```json
{
  "BlockMask": {
    "DontReplace": {
      "Inclusive": false,  // false = don't replace anything EXCEPT these
      "Materials": [{ "Solid": "Empty" }]
    }
  }
}
```

---

## Density Functions (Terrain)

### Common Types
- `Sum` - Add densities together
- `Min` - Take minimum
- `Max` - Take maximum
- `Mix` - Blend with interpolation
- `Multiplier` - Multiply densities
- `Normalizer` - Remap value range
- `CurveMapper` - Apply curve to input
- `Inverter` - Flip sign
- `Abs` - Absolute value
- `Pow` - Raise to power

### Noise Types
- `SimplexNoise2D` - 2D noise for terrain
- `SimplexNoise3D` - 3D noise for caves
- `CellNoise2D` - Voronoi/cellular noise
- `PositionsCellNoise` - Distance to positions

### Utility
- `Constant` - Fixed value
- `Axis` - Value based on X/Y/Z position
- `BaseHeight` - Distance from base height
- `Cache` - Cache expensive calculations
- `YOverride` - Force Y to specific value
- `Imported` - Load from another file
- `Rotator` - Rotate coordinate space
- `Scale` - Scale coordinate space

---

## Common Prefab Paths

### Trees
- `Trees/Oak/Stage_0` through `Stage_5`
- `Trees/Beech/Stage_0` through `Stage_4`
- `Trees/Birch/Stage_0` through `Stage_4`
- `Trees/Aspen/Stage_0` through `Stage_3`
- `Trees/Wisteria/Stage_1` through `Stage_3`
- `Trees/Burnt/Stage_1` through `Stage_3`
- `Trees/Mushroom` (giant mushroom trees)

### Plants
- `Plants/Mushroom_Large/Green/Stage_1`, `Stage_3`
- `Plants/Mushroom_Large/Purple/Stage_1`, `Stage_3`
- `Plants/Mushroom_Large/Yellow/Stage_1`, `Stage_3`
- `Plants/Cacti/Full/Stage_0` through `Stage_3`
- `Plants/Cacti/Flat/Stage_0`, `Stage_1`
- `Plants/Bush/Green`, `Arid`, `Dead_Lavathorn`

### Rocks
- `Rock_Formations/Rocks/Stone/Small`, `Large`
- `Rock_Formations/Rocks/Grass/Small`
- `Rock_Formations/Rocks/Sandstone/Small`, `Large`
- `Rock_Formations/Rocks/Volcanic/Large`, `Lava_Lakes_Small`

### Materials (for Column props)
- `Plant_Bush_Green`
- `Wood_Sticks`
- `Plant_Crop_Mushroom_Cap_Red`
- Various soil/grass types

---

## Environment Providers

Common environments:
- `Zone1_Plains` - Green temperate
- `Env_Zone2_Deserts` - Desert sky/fog
- `Env_Zone3_Taiga` - Cold/snowy
- `Env_Zone4_Wastes` - Volcanic/dark

---

## Best Practices

1. **Use unique Seeds** for each prop layer to avoid patterns
2. **Layer props** from large to small (trees → bushes → flowers)
3. **Use Runtime=0** for terrain-affecting props, **Runtime=1** for decorations
4. **Multiple floor materials** for more natural placement
5. **Union + Cluster** for organic groupings (tree + fallen sticks)
6. **FieldFunction Delimiters** for biome-within-biome variation
7. **Export reusable patterns** with `ExportAs` for consistency
8. **Appropriate scanner ranges** based on prop size
9. **PointsY=256** for realms generated at high Y levels
10. **TopDownOrder=true** for most surface props

---

## Example: Complete Forest Prop Layer

```json
{
  "Skip": false,
  "Runtime": 0,
  "Positions": {
    "Type": "Occurrence",
    "Seed": "TreeOccurrence",
    "FieldFunction": { "Type": "Constant", "Value": 0.3 },
    "Positions": {
      "Type": "Mesh2D",
      "PointsY": 256,
      "PointGenerator": {
        "Type": "Mesh",
        "Jitter": 0.4,
        "ScaleX": 15,
        "ScaleY": 15,
        "ScaleZ": 15,
        "Seed": "TreeMesh"
      }
    }
  },
  "Assignments": {
    "Type": "FieldFunction",
    "FieldFunction": {
      "Type": "SimplexNoise2D",
      "Scale": 60,
      "Seed": "TreeZones"
    },
    "Delimiters": [
      {
        "Min": 0.5, "Max": 0.8,
        "Assignments": {
          "Type": "Constant",
          "Prop": {
            "Type": "Union",
            "Props": [
              {
                "Type": "Prefab",
                "WeightedPrefabPaths": [
                  { "Path": "Trees/Oak/Stage_2", "Weight": 60 },
                  { "Path": "Trees/Beech/Stage_2", "Weight": 40 }
                ],
                "Directionality": { "Type": "Random", "Seed": "A", "Pattern": { /* Floor */ } },
                "Scanner": { "Type": "ColumnLinear", "MaxY": 60, "MinY": 0, "BaseHeightName": "Base", "TopDownOrder": true, "ResultCap": 1 }
              },
              {
                "Type": "Cluster",
                "Range": 8,
                "DistanceCurve": { "Type": "Manual", "Points": [{ "In": 6, "Out": 0.1 }, { "In": 8, "Out": 0 }] },
                "WeightedProps": [{ "Weight": 1, "ColumnProp": { "Type": "Column", "ColumnBlocks": [{ "Y": 0, "Material": { "Solid": "Wood_Sticks" } }] } }]
              }
            ]
          }
        }
      },
      {
        "Min": 0.8, "Max": 1,
        "Assignments": { /* Large trees */ }
      }
    ]
  }
}
```
