# Structure Placement Architecture

How structures are placed in realm arenas, why faction structures MUST be runtime, and how the collision system works.

**Last updated:** April 20, 2026

---

## The Problem

Hytale's WorldGen V2 prop system places prefabs on a per-layer grid. Each layer is independent — **no cross-layer collision detection exists**. Props from Layer A have zero awareness of props from Layer B. The engine reads terrain blocks only (`materialReadSpace`), never blocks placed by other prop layers.

This is fine for natural vegetation (overlapping trees and ferns look organic). It's terrible for faction structures (overlapping buildings look broken).

---

## The Solution: Two-System Architecture

| System | What Goes Here | When | Collision |
|--------|---------------|------|-----------|
| **WorldGen Props** (biome JSON) | Natural environment: trees, rocks, flowers, vines, mushrooms, arches, pillars, ground cover | Terrain generation | None |
| **Runtime Placement** (Java) | ALL faction structures, monuments, ruins, boss camps | After chunks load, before mobs | Full collision system |

**Rule: Faction structures are NEVER in WorldGen prop layers.** This applies to ALL biomes:
- Forest → Trork structures via RealmStructurePlacer
- Tundra → Outlander structures via RealmStructurePlacer
- Jungle → Feran structures via RealmStructurePlacer
- Desert → Sandstone monuments via RealmStructurePlacer (never had WorldGen factions)

---

## Runtime Placement Systems

### RealmStructurePlacer — Individual Structures

Scatters individual prefabs throughout the arena. Used for faction buildings, monuments, ruins, fossils, hotsprings.

**Placement flow:**
```
For each StructureEntry in the biome pool:
  Calculate count = density × arenaRadius / 100
  For each structure to place (up to 12 attempts):
    1. Random XZ on uniform circular disk
    2. Check spawn exclusion (20 blocks from player)
    3. Check min separation from local placements (15 blocks)
    4. Find ground: TerrainUtils.findStructureGroundLevel()
       → 3×3 grid sample at ±4 blocks, use LOWEST Y
    5. Load prefab, calculate footprint from actual bounds
    6. Check StructureBoundsRegistry.overlaps() → skip if any collision
    7. Check TerrainUtils.isAreaOccupied() → skip if WorldGen props block
    8. PrefabUtil.paste(force=false) → preserves surrounding blocks
    9. StructureBoundsRegistry.register() → with 2-block safety margin
    10. expandSpawnerBlocks() → recursively paste child prefabs
```

**Key parameters:**
- `StructureEntry(dirPath, countPer100Blocks, minRadius)` — density-based, radius-gated
- `WALL_MARGIN = 0.85` — keep structures 15% away from arena walls
- `SPAWN_EXCLUSION_RADIUS = 12` — clear zone around player spawn
- `MIN_SEPARATION = 15` — minimum distance between structures
- `MAX_ATTEMPTS = 12` — retries per structure

### BossStructurePlacer — Compound Boss Camps

Procedurally generates compound camps at boss spawn positions: center piece + satellite structures + Bresenham dirt paths.

**Placement flow:**
```
1. Find matching CampTier for arena radius
2. Place CENTER piece:
   a. Try original position first, then up to 5 random offsets (±8 blocks)
   b. Full overlap checks via pastePrefabFromDirectory(realmId, terrainMaterials)
   c. Actual prefab bounds (not hardcoded)
   d. Fallback: unchecked paste at original position (boss MUST have a camp)
3. Place SATELLITES (random count in tier range):
   a. Random angle + distance from center
   b. Full overlap checks
   c. Register bounds
4. Draw dirt PATHS from each satellite to center (Bresenham star topology)
```

**CampTier fields:** `(minRadius, maxRadius, centerPool, satellitePool, minSat, maxSat, minDist, maxDist)`

**Special case — Jungle:** No BOSS_CAMPS entry. Rex_Cave boss spawns in the open jungle without any camp structure.

---

## Collision System: StructureBoundsRegistry

Shared between RealmStructurePlacer and BossStructurePlacer. Tracks 2D footprints (XZ plane) of all placed structures per realm.

### Safety Margin

Every registration adds `MARGIN = 2` blocks to each side:
```java
register(realmId, minX, minZ, maxX, maxZ)
// Actually stores: (minX-2, minZ-2, maxX+2, maxZ+2)
```

This guarantees **minimum 4 blocks** between any two structures (2 from each side).

### Footprint Calculation

Calculated from actual prefab bounds, not hardcoded:
```java
IPrefabBuffer accessor = buffer.newAccess();
int halfW = Math.max(Math.abs(accessor.getMinX()), Math.abs(accessor.getMaxX()));
int halfD = Math.max(Math.abs(accessor.getMinZ()), Math.abs(accessor.getMaxZ()));
// Check: overlaps(realmId, x-halfW, z-halfD, x+halfW, z+halfD)
```

### Overlap Check Order

1. **StructureBoundsRegistry.overlaps()** — checks against ALL previously placed runtime structures
2. **TerrainUtils.isAreaOccupied()** — scans for WorldGen prop blocks (trees, rocks) at ground level. Area is "occupied" if >30% of sampled positions have non-terrain solid blocks.

Both must pass before placement proceeds.

---

## Ground Finding: Multi-Point Sampling

`TerrainUtils.findStructureGroundLevel()` prevents floating structures on rugged terrain.

**Problem:** Single-point ground scan finds a terrain peak. Structure placed at peak height floats above nearby valleys.

**Solution:** Sample 9 points (3×3 grid) at `±footprintRadius` blocks from center. Return the **lowest** Y across all samples. Structure sits at the lowest nearby terrain — slight clipping into peaks is far less visible than floating above valleys.

```java
public static int findStructureGroundLevel(World world, int centerX, int centerZ,
                                            int maxY, Set<String> terrainMaterials,
                                            int footprintRadius) {
    int lowestY = findGroundLevel(world, centerX, centerZ, maxY, terrainMaterials);
    int[] offsets = {-footprintRadius, 0, footprintRadius};
    for (int dx : offsets) {
        for (int dz : offsets) {
            if (dx == 0 && dz == 0) continue;
            int y = findGroundLevel(world, centerX + dx, centerZ + dz, maxY, terrainMaterials);
            if (y < lowestY) lowestY = y;
        }
    }
    return lowestY;
}
```

**Radius values:** RealmStructurePlacer uses 4, BossStructurePlacer center uses 5.

### Ground Finding Details

`TerrainUtils.findGroundLevel()` scans top-down from `maxScanY` (BaseY + 30):
- Only blocks in `biome.getTerrainMaterials()` count as ground (tree trunks, mushrooms skipped)
- Requires 2-block air clearance above (blocks at Y+1 and Y+2 must be non-solid)
- If clearance fails (tree trunk above terrain), continues scanning down
- Fallback Y = 65 if no terrain found

---

## Mob Spawn Position Finding

`RealmMobSpawner.spawnMobsAtPoints()` uses a similar but more aggressive retry system:

```
For each mob to spawn:
  For posAttempt = 0 to 7:    ← 8 completely different random positions
    Generate random XZ via uniform disk sampling
    Find ground level
    If clearance OK → use this position, break
    Else try 10 local offsets (±1 to ±5 blocks in X then Z)
    If offset found → use it, break
    Else → try next random position
  If all 8 positions fail → skip mob (logged at FINE level)
```

**Why 8 positions:** Dense biomes (jungle) have vegetation clusters spanning 5-10 blocks. Local offsets (±5) can't escape a cluster. A new random position across the arena likely lands in a different cluster. 8 attempts with 66% per-attempt failure rate → 0.66^8 = 4% total failure.

---

## WorldGen Scanner Behavior (For Reference)

WorldGen props use `ColumnLinear` scanner with `Floor` pattern:
- `MaxY` parameter limits how high above BaseHeight to search
- Trees: `MaxY=60` (can be on hills)
- Structures: `MaxY=8` (stay near ground level — off boundary walls)
- Scanner reads from `materialReadSpace` (terrain only) — cannot see blocks from other prop layers
- `TopDownOrder=true` — scans from BaseY+MaxY downward, returns first match
- Floor pattern matches: air at position + terrain material below

**This is why WorldGen can't prevent overlap:** each layer's scanner only sees terrain, never other layers' blocks.

---

## PrefabUtil.paste() Modes

| Parameter | `false` (default) | `true` |
|-----------|-------------------|--------|
| `force` | Uses `placeBlock()` — prefab solid blocks place, air blocks don't overwrite surroundings | Uses `setBlock()` — overwrites EVERYTHING including placing air over existing blocks |
| Effect | Structures integrate naturally with existing terrain/props | Rectangular clear-cuts around structures |
| Use case | ALL runtime structure placement | Only when intentionally bulldozing an area |

**Always use `force=false`** for RealmStructurePlacer and BossStructurePlacer.

---

## Key Files

| File | Purpose |
|------|---------|
| `src/.../maps/spawning/RealmStructurePlacer.java` | Individual structure scatter |
| `src/.../maps/spawning/BossStructurePlacer.java` | Compound boss camps |
| `src/.../maps/spawning/StructureBoundsRegistry.java` | 2D AABB collision tracking |
| `src/.../util/TerrainUtils.java` | Ground finding, clearance checks, area occupancy |
| `src/.../maps/spawning/RealmMobSpawner.java` | Mob spawn position finding |
| `scripts/generate-realm-biomes.py` | WorldGen biome generation (natural props only) |
