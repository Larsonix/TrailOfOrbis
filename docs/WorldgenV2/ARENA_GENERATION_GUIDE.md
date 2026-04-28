# Arena Generation Guide - Lessons Learned

This document captures critical learnings from building circular arena biomes for the Realms system.

## Critical Gotchas

### 1. Distance vs Axis Node - HORIZONTAL DISTANCE

**Problem**: The `Distance` node calculates **3D distance from origin (0,0,0)**, not horizontal distance.

At spawn point (0, 64, 0):
- `Distance` node returns: √(0² + 64² + 0²) = **64 blocks** (WRONG for arena radius!)
- `Axis` node with Y-axis returns: √(0² + 0²) = **0 blocks** (CORRECT!)

**Solution**: Use `Axis` node with vertical axis for horizontal distance:

```json
{
  "Type": "Axis",
  "Skip": false,
  "Axis": {
    "X": 0,
    "Y": 1,
    "Z": 0
  },
  "Curve": {
    "Type": "Manual",
    "Points": [
      { "In": 0, "Out": 0 },
      { "In": 60, "Out": 0 },
      { "In": 70, "Out": 2 },
      { "In": 500, "Out": 2 }
    ]
  }
}
```

### 2. BaseHeight Curve - Surface Level

**Problem**: Using wrong curve points causes spawn inside solid terrain.

**Wrong** (surface at borderline density 0, noise can flip it solid):
```json
"Points": [
  { "In": 1, "Out": -1 },
  { "In": -1, "Out": 1 }
]
```

**Correct** (surface guaranteed air at BaseHeight level):
```json
"Points": [
  { "In": 0, "Out": -1 },
  { "In": -1, "Out": 1 }
]
```

At Y = BaseHeight (distance 0): density = -1 (guaranteed AIR)
At Y = BaseHeight - 1 (distance -1): density = +1 (SOLID)

### 3. Manual Curve Behavior

From documentation:
> "The function is constant before the first point and after the last point."

Points are sorted by `In` value internally. For `In: -1 → +1, In: 0 → -1`:
- Below -1: constant +1 (solid)
- Between -1 and 0: linear interpolation
- Above 0: constant -1 (air)

---

## Working Arena Density Pattern

### Flat Arena with Circular Walls

```json
{
  "Type": "Sum",
  "Skip": false,
  "Inputs": [
    {
      "Type": "CurveMapper",
      "Skip": false,
      "Curve": {
        "Type": "Manual",
        "Points": [
          { "In": 0, "Out": -1 },
          { "In": -1, "Out": 1 }
        ]
      },
      "Inputs": [{
        "Type": "BaseHeight",
        "Skip": false,
        "BaseHeightName": "Base",
        "Distance": true
      }]
    },
    {
      "Type": "Axis",
      "Skip": false,
      "Axis": { "X": 0, "Y": 1, "Z": 0 },
      "Curve": {
        "Type": "Manual",
        "Points": [
          { "In": 0, "Out": 0 },
          { "In": 60, "Out": 0 },
          { "In": 70, "Out": 2 },
          { "In": 500, "Out": 2 }
        ]
      }
    }
  ]
}
```

**Result**:
- Radius 0-60: Flat floor at BaseHeight (Y=64)
- Radius 60-70: Walls transition zone
- Radius 70+: Solid walls extending upward

### Adding Terrain Variation (Hills)

Add a third input to Sum for noise-based terrain:

```json
{
  "Type": "Normalizer",
  "Skip": false,
  "FromMin": -1,
  "FromMax": 1,
  "ToMin": -0.25,
  "ToMax": 0.25,
  "Inputs": [{
    "Type": "SimplexNoise2D",
    "Skip": false,
    "Lacunarity": 2,
    "Persistence": 0.5,
    "Octaves": 2,
    "Scale": 25,
    "Seed": "TerrainHills",
    "CacheSize": 16
  }]
}
```

**Amplitude guide**:
- ±0.15 = ~3 block variation (subtle)
- ±0.25 = ~5 block variation (moderate)
- ±0.5 = ~10 block variation (dramatic)

---

## Density Value Reference

| Value | Meaning |
|-------|---------|
| Positive (> 0) | Solid terrain (blocks placed) |
| Negative (< 0) | Empty space (air) |
| Zero (= 0) | Surface boundary |

---

## WorldStructure BaseHeights

Our arenas use these BaseHeights (defined in WorldStructure files):

| Name | Y Level | Purpose |
|------|---------|---------|
| Base | 64 | Main terrain surface / spawn level |
| Water | 64 | Water level (same as base for arenas) |
| Bedrock | 0 | Bottom of world |

---

## Testing Checklist

Before deploying arena biomes:

1. [ ] Spawn point (0, 64, 0) is in AIR
2. [ ] Walls are visible at expected radius
3. [ ] Walls don't intersect spawn area
4. [ ] Surface material is correct
5. [ ] No floating blocks or holes

---

## File Locations

- **Biomes**: `/Server/HytaleGenerator/Biomes/Realm_Arena_*.json`
- **WorldStructures**: `/Server/HytaleGenerator/WorldStructures/Realm_*.json`
- **Instances**: `/Server/Instances/realm_*/instance.bson`
