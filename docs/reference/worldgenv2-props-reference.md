# WorldGenV2 Props System — Complete Reference

Source: Verified against Hytale server source and community mod implementations

## Prop Types

### Prefab (✅ PROVEN)

Places saved .bson structures from directories.

```json
{
  "Type": "Prefab",
  "WeightedPrefabPaths": [
    { "Path": "Trees/Oak/Stage_3", "Weight": 4 },
    { "Path": "Trees/Beech/Stage_3", "Weight": 2 }
  ],
  "LegacyPath": false,
  "LoadEntities": false,
  "Directionality": {
    "Type": "Random", "Seed": "dir",
    "Pattern": { "Type": "Floor", "Origin": {"Type":"BlockSet","BlockSet":{"Inclusive":true,"Materials":[{"Solid":"Empty"}]}}, "Floor": {"Type":"BlockSet","BlockSet":{"Inclusive":true,"Materials":[{"Solid":"Soil_Grass_Full"}]}} }
  },
  "Scanner": { "Type": "ColumnLinear", "MaxY": 60, "MinY": -20, "RelativeToPosition": false, "BaseHeightName": "Base", "TopDownOrder": true, "ResultCap": 1 },
  "MoldingDirection": "None",
  "MoldingChildren": false
}
```

Path resolution: `{AssetPack}/Server/Prefabs/{Path}/` — loads ALL .prefab.json in dir.

### Column (📋 Schema from decompilation)

Vertical stack of blocks at a single XZ position. Required as child of Cluster.

```json
{
  "Type": "Column",
  "ColumnBlocks": [
    { "Y": 0, "Material": { "Solid": "Plant_Grass_Lush_Short" } },
    { "Y": 1, "Material": { "Solid": "Plant_Crop_Mushroom_Cap_Red" } }
  ],
  "BlockMask": { "Inclusive": true, "Materials": [{ "Solid": "Soil_Grass" }] },
  "Directionality": { "Type": "Static", "Rotation": 0, "Pattern": { "Type": "Floor", ... } },
  "Scanner": { "Type": "ColumnLinear", "MaxY": 30, "MinY": -20 }
}
```

### Cluster (📋 Proven in community mods)

Scatters Column props around a center point with distance falloff curve.

```json
{
  "Type": "Cluster",
  "Range": 10,
  "Seed": "cluster_a",
  "DistanceCurve": {
    "Type": "Manual",
    "Points": [
      { "In": 9, "Out": 0.005 },
      { "In": 10, "Out": 0 }
    ]
  },
  "WeightedProps": [
    {
      "Weight": 1.0,
      "ColumnProp": {
        "Type": "Column",
        "ColumnBlocks": [{ "Y": 0, "Material": { "Solid": "Rock_Stone" } }],
        "Directionality": { "Type": "Static", "Rotation": 0 },
        "Scanner": { "Type": "ColumnLinear", "MaxY": 30, "MinY": -20 }
      }
    }
  ],
  "Pattern": { "Type": "Floor", ... },
  "Scanner": { "Type": "ColumnLinear", ... }
}
```

**Constraint**: Children MUST be Column props (1×1 read/write bounds).

### Union (📋 Proven in community mods)

Combines multiple props at same placement position. Example use: Prefab tree + Cluster scrap metal.

```json
{
  "Type": "Union",
  "Props": [
    { "Type": "Prefab", "WeightedPrefabPaths": [{ "Path": "Trees/Gear/Stage_0", "Weight": 60 }], ... },
    { "Type": "Cluster", "Range": 10, "Seed": "scatter", "WeightedProps": [...] }
  ]
}
```

### Box (📋 Schema from decompilation)

Fills a rectangular volume with a material.

```json
{
  "Type": "Box",
  "Range": { "X": 2, "Y": 2, "Z": 2 },
  "Material": { "Solid": "Soil_Dirt" },
  "Pattern": { "Type": "Floor", ... },
  "Scanner": { "Type": "ColumnLinear", "MaxY": 320, "MinY": 0, "TopDownOrder": true, "ResultCap": 1 }
}
```

Use cases: Dirt paths, stone patches, terrain accents.

### PondFiller (📋 Schema from decompilation)

Auto-fills terrain depressions with fluid.

```json
{
  "Type": "PondFiller",
  "FillMaterial": { "Type": "Constant", "Material": { "Fluid": "Water" } },
  "BarrierBlockSet": { "Inclusive": true, "Materials": [{ "Solid": "Rock_Stone" }] },
  "Bounds": { "PointA": { "X": -10, "Y": -10, "Z": -10 }, "PointB": { "X": 10, "Y": 10, "Z": 10 } }
}
```

### Weighted (📋 Schema from decompilation)

Picks ONE prop from weighted list (mutually exclusive).

```json
{
  "Type": "Weighted",
  "Seed": "variation",
  "Entries": [
    { "Weight": 60.0, "Prop": { "Type": "Column", "ColumnBlocks": [...] } },
    { "Weight": 40.0, "Prop": { "Type": "Prefab", "WeightedPrefabPaths": [...] } }
  ]
}
```

### Density (📋 Schema from decompilation)

Places blocks based on 3D density field values.

```json
{
  "Type": "Density",
  "Density": { "Type": "SimplexNoise3D", "Scale": 50, "Seed": "ores", "Octaves": 3 },
  "Material": { "Type": "Constant", "Material": { "Solid": "Rock_Ore_Iron" } },
  "Bounds": { "PointA": { "X": -50, "Y": -50, "Z": -50 }, "PointB": { "X": 50, "Y": 50, "Z": 50 } },
  "PlacementMask": { "Inclusive": true, "Materials": [{ "Solid": "Rock_Stone" }] }
}
```

### DensitySelector (📋 Schema from decompilation)

Maps density ranges to different props (like FieldFunction but at prop level).

```json
{
  "Type": "DensitySelector",
  "Density": { "Type": "SimplexNoise2D", "Scale": 80, "Seed": "zones" },
  "Delimiters": [
    { "Range": { "Min": 0.0, "Max": 0.33 }, "Prop": { "Type": "Column", ... } },
    { "Range": { "Min": 0.33, "Max": 0.66 }, "Prop": { "Type": "Prefab", ... } },
    { "Range": { "Min": 0.66, "Max": 1.0 }, "Prop": { "Type": "Column", ... } }
  ]
}
```

---

## Assignment Types

### Constant (✅ PROVEN)
```json
{ "Type": "Constant", "Prop": { "Type": "Prefab", ... } }
```

### FieldFunction (✅ Proven in community mods)

Zone-based prop assignment using density delimiters.

```json
{
  "Type": "FieldFunction",
  "FieldFunction": { "Type": "SimplexNoise2D", "Scale": 80, "Seed": "zones", "Octaves": 1 },
  "Delimiters": [
    { "Min": 0.7, "Max": 0.85, "Assignments": { "Type": "Constant", "Prop": { ... } } },
    { "Min": 0.85, "Max": 1.0, "Assignments": { "Type": "Weighted", "WeightedAssignments": [...] } }
  ]
}
```

**Delimiter fields use `Min`/`Max`** (NOT `From`/`To` like MaterialProvider).

### Weighted Assignment
```json
{
  "Type": "Weighted",
  "SkipChance": 0,
  "Seed": "A",
  "WeightedAssignments": [
    { "Weight": 70, "Assignments": { "Type": "Constant", "Prop": { ... } } },
    { "Weight": 30, "Assignments": { "Type": "Constant", "Prop": { ... } } }
  ]
}
```

### Imported Assignment
```json
{ "Type": "Imported", "Name": "MyExportedAssignment" }
```
References an assignment with `"ExportAs": "MyExportedAssignment"` defined elsewhere.

---

## Custom Prefab Pipeline

### Creating Custom Prefabs

1. In Hytale: `/editprefab new <world_name>` — creates editing world
2. Build your structure (lantern post, ruins, bridge, etc.)
3. Use Selection Brush to select the area
4. `/prefab save <prefab_name>` — saves as `.prefab.json`

### Prefab File Format (.prefab.json)

```json
{
  "version": 8,
  "blockIdVersion": 11,
  "anchorX": 0, "anchorY": 0, "anchorZ": 0,
  "blocks": [
    { "x": 0, "y": 0, "z": 0, "name": "Rock_Stone" },
    { "x": 0, "y": 1, "z": 0, "name": "Rock_Stone" },
    { "x": 0, "y": 2, "z": 0, "name": "Decoration_Lantern" }
  ],
  "entities": [],
  "fluids": []
}
```

### Storage Location

```
{AssetPack}/Server/Prefabs/{category}/{subcategory}/{name}.prefab.json
```

Example for TrailOfOrbis:
```
src/main/resources/hytale-assets/Server/Prefabs/
├── Forest/
│   ├── Lantern_Post.prefab.json
│   └── Stone_Wall_Ruin.prefab.json
├── Desert/
│   └── Sand_Pillar.prefab.json
└── Caverns/
    └── Crystal_Formation.prefab.json
```

### Referencing in Biome JSON

```json
{ "Path": "Forest/Lantern_Post", "Weight": 1 }
```

Or reference entire directory (loads all prefabs in it):
```json
{ "Path": "Forest", "Weight": 1 }
```

### Key Rules

- `LegacyPath: false` always (modern path: `Server/Prefabs/`)
- Path is a DIRECTORY search key, not a file path
- Asset packs searched in reverse order (newest wins)
- `LoadEntities: true` spawns any entities stored in the prefab
- `anchorX/Y/Z` defines rotation origin — wrong anchor = misaligned placement
- Molding (`MoldingDirection: "Up"/"Down"`) adjusts prefab Y to terrain surface

---

## Scanner Types

| Type | Floor Placement | Ceiling Placement |
|------|----------------|-------------------|
| ColumnLinear | `TopDownOrder: true, BaseHeightName: "Base"` | `TopDownOrder: false, BaseHeightName: "Ceiling"` |
| Linear | `Axis: "Y", AscendingOrder: false, Scanner: {Type: "Origin"}` | Same with appropriate Range |

## Pattern Types

| Type | Use | Fields |
|------|-----|--------|
| Floor | Floor-placed props | `Floor: BlockSet, Origin: BlockSet(Empty)` |
| Ceiling | Ceiling-hung props | `Ceiling: BlockSet, Origin: BlockSet(Empty)` |
| BlockType | Single material match | `Material: {Solid: "block_name"}` |
| BlockSet | Multi-material match | `BlockSet: {Inclusive: true, Materials: [...]}` |
| And | Multiple conditions | Combines patterns |
| Offset | Check at offset | Offset position check |

---

## Performance Notes

- Column: Cheapest prop type (single block stack)
- Prefab: Moderate (loads .bson, places blocks)
- Cluster: ~10× more expensive than Column (multiple scatter calculations)
- Density: ~10× more expensive (evaluates 3D field per block)
- Union: Cost = sum of all child prop costs
