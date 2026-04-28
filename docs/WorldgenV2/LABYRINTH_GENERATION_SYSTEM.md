# Labyrinth Generation System — Vanilla Analysis & Realm Adaptation

**Status:** Research complete — ready for implementation design
**Last updated:** April 21, 2026

---

## 1. Why This Document Exists

The Mountains biome uses Goblin Lair structures — 97 dungeon modules (corridors, rooms, junctions, caps) designed to form a **labyrinth carved from solid stone**. This is fundamentally different from every other realm biome:

| Other Biomes | Mountains |
|-------------|-----------|
| Open terrain, structures placed ON it | Solid terrain, corridors carved INTO it |
| `force=false` — air in prefab is skipped | `force=true` — air in prefab overwrites stone |
| Structures are landmarks in a landscape | The labyrinth IS the playable space |
| RealmStructurePlacer (scatter) | New system: recursive tree placement |

This document captures the vanilla CaveGenerator system (from decompilation) and identifies how to adapt it for realm instances.

---

## 2. Vanilla CaveGenerator — The Recursive Tree Algorithm

### 2.1 Overview

Hytale generates dungeons as a **depth-first recursive tree** of connected prefab pieces. There are no cycles (it's a tree, not a graph) — every corridor leads forward or to a dead end, never back to a previous junction.

```
Entry (Entrance)
├── Corridor (Straight_Length_14)
│   ├── T-Junction
│   │   ├── Corridor → Room (Beast_001) → Cap (Dead_End)
│   │   └── Corridor → Stairs → Corridor → Room (Large2) → Cap (Hidden_Treasure)
│   └── Corner → Corridor → Room (Boss_001)
└── Side Branch
    └── Corridor → Houses (Basalt_1) → Cap (Dead_End)
```

### 2.2 Core Data Structures

**CaveType** — Master config for one dungeon type:
- `entryNodeType` — Root piece where generation starts
- `depth` — Max recursion depth (typically 2-4 for dungeon)
- `biomeMask` — Which biomes allow this cave
- `blockMask` — Which blocks can be replaced (BlockMaskCondition)
- `heightCondition` — Y-level restrictions
- `fluidLevel` — Optional water/lava fill

**CaveNodeType** — Definition for one piece type (e.g., "Straight_Length_14"):
- `shapeGenerator` — Geometry type. For Goblin Lair: always `PREFAB`
- `children[]` — Array of `CaveNodeChildEntry` defining connections
- `fillings` — Block types to fill geometry (for non-prefab shapes)
- `prefabContainer` — Decorations inside this room
- `childrenCountBounds` — Max children this node can spawn

**CaveNodeChildEntry** — Connection blueprint (how pieces attach):
- `types` — Weighted map of allowed child CaveNodeTypes
- `anchor` — Normalized connection point on parent (0-1 range, snapped to block center)
- `offset` — Additional translation after anchor resolution
- `rotation` — Allowed PrefabRotations (0°, 90°, 180°, 270°)
- `repeat` — How many times to spawn this child (range)
- `chance` — Probability of spawning (0.0-1.0)
- `childrenLimit` — Max depth for branches from this child
- `yawModifier` — YawAdd/YawSet for orientation
- `pitchModifier` — PitchAdd/PitchSet for vertical angle
- `yawMode` — How to combine parent + child yaw (NODE/SUM/PREFAB)

**CaveNode** — Runtime instance during generation:
- `caveNodeType` — Reference to type definition
- `shape` — Generated PrefabCaveNodeShape with world position + bounds
- `yaw`, `pitch` — Current orientation
- `seedOffset` — Unique seed for this instance

### 2.3 The Algorithm (CaveGenerator.java)

```
generate(seed, caveType, x, y, z):
    cave = new Cave()
    random = new Random(seed)

    // 1. Create entry node
    entryType = caveType.getEntryNodeType()
    entryShape = generateShape(entryType, origin, yaw=random, pitch=random)
    entryNode = new CaveNode(entryType, entryShape)
    cave.addNode(entryNode)

    // 2. Recurse
    startDepth = caveType.getStartDepth().getValue(random)  // e.g., 2-4
    continueNode(cave, entryNode, startDepth, random)

    // 3. Compile (sort by priority for overlap resolution)
    cave.compile()
    return cave

continueNode(cave, parent, depth, random):
    if depth <= 0: return

    generatedChildren = 0
    childrenCount = parent.type.getChildrenCountBounds()?.getValue(random) ?? MAX_INT

    for childEntry in parent.type.getChildren():
        if generatedChildren >= childrenCount: return

        repeatCount = childEntry.getRepeat().getValue(random)
        for i in 0..repeatCount:
            if random.nextFloat() > childEntry.getChance(): continue  // Skip by chance

            // Pick child type from weighted map
            childType = childEntry.getTypes().getRandomValue(random)

            // Calculate child origin
            origin = getChildOrigin(parent, childEntry)

            // Apply yaw/pitch modifiers
            yaw = childEntry.yawModifier.apply(parent.yaw)
            pitch = childEntry.pitchModifier.apply(parent.pitch)

            // Generate child shape
            childShape = generateShape(childType, origin, yaw, pitch)

            // Validate height conditions (both start AND end point)
            if !isMatchingHeight(origin, childType.heightCondition): continue
            if !isMatchingHeight(childShape.end, childType.heightCondition): continue

            // Validate biome at endpoint
            if !checkBiomeMask(childShape.end): continue

            // Success — add node
            childNode = new CaveNode(childType, childShape, yaw, pitch)
            cave.addNode(childNode)
            generatedChildren++

            // Recurse with decremented depth
            nextDepth = depth - 1
            if childEntry.childrenLimit != null:
                nextDepth = min(nextDepth, childEntry.childrenLimit.getValue(random))
            continueNode(cave, childNode, nextDepth, random)
```

### 2.4 Anchor & Connection System

When a child piece connects to a parent:

```
1. Get anchor vector (normalized 0-1 in parent's bounding box)
   anchor = childEntry.getAnchor()  // e.g., (0.5, 0.5, 1.0) = center of far wall

2. If parent is a PREFAB shape, rotate anchor by parent's rotation
   anchor -= (0.5, 0.5, 0.5)       // Center to origin
   parentRotation.rotate(anchor)     // Apply 0°/90°/180°/270°
   anchor += (0.5, 0.5, 0.5)       // Back to 0-1 range

3. Snap anchor to parent shape bounds
   snappedAnchor = parentShape.getAnchor(anchor)
   // For PrefabCaveNodeShape: maps to actual prefab bounding box
   // floor(x) + 0.5 = snap to block center

4. Get child offset and rotate by parent rotation
   offset = childEntry.getOffset()
   parentRotation.rotate(offset)

5. Child origin = parent.end + snappedAnchor + offset
```

### 2.5 Rotation Propagation

Rotation accumulates through the tree:

```java
// Child rotation = parent rotation + child entry rotation
PrefabRotation childRotation = childEntry.getRotation(random);  // pick from allowed list
if (parent.shape instanceof PrefabCaveNodeShape) {
    PrefabRotation parentRotation = parentShape.getPrefabRotation();
    childRotation = childRotation.add(parentRotation);  // accumulate
}

// Child offset is ALSO rotated by parent
parentRotation.rotate(offset);
```

**YawMode** controls how yaw combines:
- `NODE` — Use parent's yaw directly (aligned corridors)
- `SUM` — Add parent prefab yaw to current (accumulated turns)
- `PREFAB` — Override with parent prefab yaw only

---

## 3. Block Replacement: How Carving Works

### 3.1 force=true vs force=false

In `PrefabUtil.paste()`:

| Parameter | Air blocks in prefab | Solid blocks in prefab |
|-----------|---------------------|----------------------|
| `force=false` | **Skipped** (don't overwrite terrain) | Only place if destination is air |
| `force=true` | **Replace terrain with air** (CARVING) | Replace terrain with the block |

**For labyrinth generation, we need `force=true`** — the air blocks in corridor prefabs are what create the walkable space.

### 3.2 BlockMaskCondition (Fine-Grained Control)

Vanilla caves use a BlockMaskCondition for more precise control than just force=true/false:

```java
boolean shouldReplace = blockMask.eval(
    currentBlock,    // What's already in the world
    currentFluid,    // Fluid at that position
    prefabBlock,     // What the prefab wants to place
    prefabFluid      // Fluid from prefab
);
```

Each prefab block can have per-block override masks:
- Default mask: replace stone/dirt/grass (terrain materials)
- Specific mask: e.g., "this torch block only places if destination is air"

### 3.3 Priority System (Overlap Resolution)

When multiple nodes overlap, priority determines which blocks survive:

| Priority | Source | Meaning |
|----------|--------|---------|
| 5 | Cave covers (stalactites, floor decals) | Written first, can be overwritten |
| 6 | Cave fill (node geometry) | Standard cave blocks |
| 8 | Prefab blocks | Written last, overwrites everything |

Higher priority = placed later = wins conflicts. **Prefab rooms (priority 8) always overwrite procedural cave geometry (priority 6).**

---

## 4. Goblin Lair Piece Inventory (97 Total)

### 4.1 Prefabs_Goblin — City Dungeon (48 pieces)

**Corridors (Straight)** — 12 variants by length:
| Length | Variants | Notes |
|--------|----------|-------|
| 7 | 1 | Short connector |
| 9 | 2 | Standard |
| 11 | 3 | Medium |
| 14 | 2 | Long |
| 24 | 4 | Major thoroughfare |

**Corridors with Stairs** — 4 variants:
- Length_11_Stairs, Length_14_Stairs, Length_24_Stairs (×2)
- Vertical transitions between levels

**Directional Pieces:**
| Type | Count | Purpose |
|------|-------|---------|
| Corner | 3 | 90° turns (2 standard + 1 long) |
| T-Junction | 3 | 3-way splits |
| Stairs_U_Turn | 1 | Multi-level reversal |

**Rooms** — 9 total:
| Room | Count | Purpose |
|------|-------|---------|
| Beast | 2 | Creature encounter arenas |
| Boss | 1 | Final boss arena (Goblin_Duke) |
| Boss_Entrance | 1 | Pre-boss transition |
| Large1 | 1 | Spacious chamber |
| Large2 | 3 | Big open spaces |

**Goblin City** — 4 multi-part compound:
- Center structure + Bottom/Middle/Top tiers
- The largest compound room — multi-story goblin settlement

**Caps (Dead Ends)** — 2:
- Dead_End — Simple corridor terminator
- Hidden_Treasure — Secret reward room

**Houses** — 8:
- Basalt_1 through Basalt_4 (dark stone theme)
- Stone_1 through Stone_4 (gray stone theme)

**Decorative** — 1:
- Mushrooms_Small — Environmental dressing

### 4.2 Prefabs_Mine — Mine System (43 pieces)

More modular than Goblin City. Each type has multiple length/shape variants.

**Corridors** — 31 total:
| Type | Count | Purpose |
|------|-------|---------|
| End | 5 | Dead-end corridors |
| End_Secret | 5 | Hidden dead-ends (treasure) |
| Long | 5 | Extended passages |
| Medium | 5 | Standard passages |
| Short | 5 | Quick connectors |
| Shift | 3 | Offset corridors (direction change) |
| Side | 3 | Branch corridors |
| T | 3 | T-junction connectors |

**Rooms** — 6:
| Type | Count | Purpose |
|------|-------|---------|
| End | 2 | Dead-end chambers |
| T | 2 | T-shaped rooms |
| Y | 2 | Y-shaped junctions |

**Transitions** — 2:
- Goblin_Lair_Entrance → connects mine to library
- Goblin_Stairs → multi-level stair connection

### 4.3 Entrances — 6 total

**Library Entrance** (3): Layout, Stairs_Long, Stairs_Short
**Abandoned Village Entrance** (3): Layout, Parts_Pathways, Stairs

### 4.4 Safety Classification

**ALL 97 Goblin_Lair prefabs contain PrefabSpawnerBlocks** (confirmed in FACTION_CREATURE_DATABASE.md). They are RUNTIME ONLY — never WorldGen props.

The PrefabSpawnerBlocks are how pieces reference their children in vanilla. For our labyrinth placer, we handle connections ourselves (anchor system), so we need to:
1. Paste each piece individually with `force=true`
2. Expand spawner blocks afterward (houses/decorations inside rooms)
3. OR strip spawner blocks and handle all placement algorithmically

---

## 5. Failure Modes & Risk Analysis

### 5.1 Corridor Overlap (Pieces Carving Into Each Other)

**Risk:** Two branches of the tree carve through the same stone, creating unintended merged spaces.

**Vanilla behavior:** Allowed. Priority system resolves block conflicts. Overlapping corridors create larger open areas at intersections — this is a feature, not a bug in natural caves.

**For our realm:** This is actually acceptable for mine aesthetics (collapsed tunnels, breakthrough points). But we should:
- Track placed piece bounding boxes (like StructureBoundsRegistry)
- Skip placement if overlap exceeds a threshold (e.g., >50% of piece volume already carved)
- OR: allow overlap but ensure mob spawning doesn't double-count the space

**Mitigation:** Bounding box overlap check before each paste. If child piece overlaps >50% with existing pieces → skip that branch. The labyrinth still generates from other branches.

### 5.2 Arena Boundary Violation

**Risk:** A corridor branch extends beyond the arena radius, into the solid boundary walls or void.

**Vanilla behavior:** No explicit XZ bounds checking. Caves can cross chunk boundaries freely.

**For our realm:** Critical failure — pieces outside the arena are invisible/unreachable.

**Mitigation:** Before placing each child piece:
1. Calculate piece bounding box at proposed position
2. Check all 4 corners + center against arena radius (with WALL_MARGIN = 0.85)
3. If ANY corner exceeds boundary → skip this branch
4. Remaining branches continue normally

### 5.3 Dead Recursion (Empty Labyrinth)

**Risk:** High skip rates (boundary checks + overlap checks + chance rolls) kill most branches, leaving a trivially small labyrinth.

**Vanilla behavior:** No retry. If a branch dies, it's dead.

**For our realm:** Unacceptable — players need enough space for combat + exploration.

**Mitigation:**
1. **Minimum piece count:** After generation, count placed pieces. If < threshold (e.g., 8 for small arenas, 15 for large), regenerate with different seed
2. **Guaranteed main path:** Force the first N pieces to always succeed (no chance rolls, no overlap skip). Only side branches are probabilistic
3. **Arena-aware depth:** Scale recursion depth with arena radius. R35 = depth 2 (compact mine), R100 = depth 5 (sprawling complex)

### 5.4 Boss Room Unreachable

**Risk:** Boss room placed in a branch that gets truncated, or boss room piece fails placement.

**Vanilla behavior:** Boss room is just another weighted child type. If it doesn't spawn, there's no boss room.

**For our realm:** The boss MUST have an arena.

**Mitigation:** Two-phase generation:
1. **Phase 1 — Main spine:** Generate a guaranteed linear path from entry → corridors → boss room. No branching, no chance rolls. This is the critical path
2. **Phase 2 — Side branches:** From junctions along the spine, grow probabilistic branches (extra rooms, dead ends, treasure rooms, houses). These can fail without consequence

### 5.5 Mob Spawn Inside Corridors

**Risk:** Current RealmMobSpawner assumes open terrain — finds ground level by scanning down from sky. Inside a labyrinth, "ground" is inside solid stone.

**For our realm:** Need adapted spawn logic:
- Scan for air blocks with solid floor (not sky-down, but cave-aware)
- OR: record valid spawn positions during labyrinth generation (each room/corridor center)
- Mob leash radius must respect corridor width (no leashing mobs through walls)

### 5.6 Player Navigation

**Risk:** Pure random tree can create confusing, frustrating layouts. No landmarks, no sense of direction.

**For our realm:**
- Boss room compass marker (already exists) helps with direction
- Main spine provides a clear "forward" path
- Side branches are optional exploration/reward
- Consider: different torch colors or material hints along the main path

### 5.7 PrefabSpawnerBlock Interaction

**Risk:** Goblin Lair pieces contain PrefabSpawnerBlocks that reference OTHER Goblin Lair pieces. If we paste with `expandSpawnerBlocks()`, the vanilla recursion could spawn corridors we didn't plan for — creating uncontrolled growth.

**Mitigation options:**
1. **Don't expand spawner blocks** — Place pieces ourselves, ignore internal spawner references. Only expand spawners for DECORATIVE children (houses, mushrooms, torches)
2. **Selective expansion** — Expand only spawners whose paths match a whitelist (Houses, Mushrooms, decorations). Skip spawners referencing corridors/rooms/junctions
3. **Strip spawners pre-paste** — After pasting, scan for spawner blocks and remove them without expanding. Replace with appropriate decorative content

**Decision: Option 2 (selective expansion).** We control the labyrinth layout algorithmically, but let vanilla decoration spawners fire for interior detail. Whitelist for expansion: `Houses/*`, `Mushrooms/*`. All other spawner paths (corridors, rooms, junctions) are stripped after paste.

---

## 6. Adaptation Design: RealmLabyrinthPlacer

### 6.1 Architecture Overview

A new placement system alongside RealmStructurePlacer and BossStructurePlacer:

**Lifecycle integration point:** Called from `RealmInstance` after WorldGen terrain is ready (chunks loaded) and before `RealmMobSpawner.spawnInitialMobs()`. Specifically:
1. `RealmInstance.onChunksReady()` triggers structure placement
2. For MOUNTAINS biome: call `RealmLabyrinthPlacer.generate()` instead of `RealmStructurePlacer.placeStructures()` + `BossStructurePlacer.placeBossCamp()`
3. Output: `LabyrinthResult` containing `List<Vector3i> spawnPositions` + `Vector3i bossRoomCenter` + `int totalPieces`
4. `RealmMobSpawner` reads `spawnPositions` from `LabyrinthResult` for MOUNTAINS (bypasses terrain scanning)

```java
// Integration signature
public record LabyrinthResult(
    List<Vector3i> spawnPositions,   // Valid mob spawn points (air + solid floor)
    Vector3i bossRoomCenter,         // Boss spawn location
    int totalPiecesPlaced,           // For logging/validation
    boolean success                  // false = fell back to open chamber
) {}
```

```
RealmLabyrinthPlacer
├── Called after terrain generation, before mob spawning
├── Input: arena center, arena radius, biome type, random seed
├── Output: LabyrinthResult (spawn positions + boss room + piece count)
│
├── Phase 1: Generate Main Spine (guaranteed)
│   Entry → Corridor → Junction → Corridor → ... → Boss Room
│
├── Phase 2: Grow Side Branches (probabilistic)
│   From each junction on spine → corridors → rooms → caps
│
├── Phase 3: Paste All Pieces (force=true)
│   For each piece in generation order:
│   ├── PrefabUtil.paste(force=true)
│   ├── Selective expandSpawnerBlocks() (decorations only)
│   └── Record spawn positions (room centers, corridor midpoints)
│
└── Phase 4: Validate
    ├── Check minimum piece count
    ├── Verify boss room was placed
    └── If failed: regenerate with new seed (max 3 attempts)
```

### 6.2 Piece Connection Data

Each piece type needs metadata defining its connection points:

```java
record LabyrinthPiece(
    String prefabPath,           // e.g., "Dungeon/Goblin_Lair/Prefabs_Goblin/Straight/Length_14/01"
    PieceCategory category,      // CORRIDOR, JUNCTION, ROOM, CAP, HOUSE, BOSS
    Vector3i[] connectionPoints, // Local-space anchors where other pieces can attach
    int[] connectionFacings,     // Which direction each connection faces (N/S/E/W)
    Vector3i bounds,             // Bounding box size
    boolean isTerminal           // true = dead end (caps, boss room)
) {}
```

**Decision: Dynamic extraction at startup.** On server boot, RealmLabyrinthPlacer loads each Goblin_Lair prefab once, scans for:
1. **Bounding box** — from `IPrefabBuffer.getMinX/MaxX/MinY/MaxY/MinZ/MaxZ()`
2. **Connection points** — scan for blocks with `PrefabSpawnerBlock` component. Their positions ARE the connection anchors. Their stored path tells us what CATEGORY of child they expect (corridor vs decoration)
3. **Connection facings** — inferred from spawner block position relative to piece center (edge of bounding box = exit direction)
4. **Terminal flag** — pieces with 0 or 1 connection points are terminals (caps, dead ends)

This is the same approach RealmStructurePlacer uses for footprint calculation — proven pattern. The one-time scan cost is negligible (~100ms for 97 prefabs).

**Fallback:** If dynamic extraction fails for a piece, exclude it from the pool and log a warning. The placer works with whatever pieces load successfully.

### 6.3 Generation Parameters (Scaled by Arena Radius)

| Parameter | R35 (Small) | R60 (Medium) | R100 (Large) |
|-----------|-------------|--------------|---------------|
| Spine length | 3-4 pieces | 5-7 pieces | 8-12 pieces |
| Side branch depth | 1 | 2 | 3 |
| Junction frequency | Every 2nd piece | Every 2nd piece | Every 3rd piece |
| Total pieces (target) | 8-12 | 15-25 | 30-50 |
| Boss room distance | End of spine | End of spine | End of spine |

### 6.4 Terrain Requirements

The labyrinth needs SOLID terrain to carve into:

```python
# BiomeSpec for Mountains (Interior Mine)
signature_type = "ceiling"
ceiling_y = 95            # Lower than Caverns (110) — claustrophobic mine
ceiling_edge_y = 80       # Dome drops at edges
cave_3d_noise = NONE      # No volumetric carving — mine is structured
noise_amplitude = 0.05    # Nearly flat floor (man-made leveled)
boundary_density = 7      # Very solid walls
```

The terrain generator creates a solid stone mass with ceiling. The labyrinth placer then carves corridors and rooms out of it. Any uncarved space remains solid wall.

---

## 7. Open Questions (Require Testing)

1. **PrefabSpawnerBlock positions in Goblin Lair pieces** — Where exactly are the connection points? Need to load prefabs and scan, or test in-game
2. **Piece dimensions** — What are the actual bounding box sizes of each piece type? Critical for overlap detection and arena fitting
3. **Which spawner blocks are decoration vs corridor references?** — Need to categorize to implement selective expansion
4. **Stairs — do they work with force=true?** — Stair pieces change Y level. Does the carving work vertically through solid terrain?
5. **Prefabs_Mine vs Prefabs_Goblin** — Which system (or both?) for realm mines? Mine system is more modular (better for procedural), Goblin system has richer rooms

---

## 8. Related Files

| File | Purpose |
|------|---------|
| `docs/WorldgenV2/BIOME_MOUNTAINS_PLAN.md` | Original design options |
| `docs/WorldgenV2/BIOME_CAVERNS.md` | Reference for ceiling biome |
| `docs/WorldgenV2/REALM_BIOME_CREATION_GUIDE.md` | Three-system architecture |
| `docs/reference/confirmed-dead-approaches.md` | Known failures to avoid |
