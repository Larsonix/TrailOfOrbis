# Mountains Biome — "The Goblin Mines"

**Status:** Specification complete — ready for implementation
**Last updated:** April 21, 2026

---

## Overview

An underground goblin mining complex carved into solid stone. Players enter a labyrinth of corridors, junctions, and rooms — the operational heart of a goblin faction stronghold. Unlike every other biome where structures are placed ON terrain, Mountains structures CARVE INTO terrain, creating the playable space itself.

**Identity:** The only biome where the labyrinth IS the arena. Tight corridors, ambush corners, and a boss throne room at the end of a guaranteed main path. Goblins are an EXCLUSIVE faction — they appear in no other biome.

**Difficulty:** Mid-tier. Tight spaces make ranged enemies (Goblin_Lobber) more dangerous, while ambush mobs (Goblin_Thief) exploit blind corners. Bear_Grizzly serves as a rare powerful creature encounter in larger chambers.

---

## Differentiation from Caverns

Both are underground ceiling biomes, but fundamentally different:

| Aspect | Caverns (Scarak Hive) | Mountains (Goblin Mines) |
|--------|----------------------|--------------------------|
| **Feel** | Natural crystal cave | Man-made goblin mine |
| **Floor** | Organic, uneven (3D noise creates pillars/alcoves) | Flat, leveled (mined corridors) |
| **Ceiling** | High dome (Y=110, 45-block clearance) | Low mine shaft (Y=95, 30-block clearance) |
| **Walls** | Organic — 3D noise carves natural shapes | Structured — labyrinth pieces carve corridors |
| **Lighting** | Bioluminescent (crystals, mushrooms, cyan/blue) | Torch-lit (warm amber, dusty brown) |
| **Space** | Open cavern with scattered props | Corridors and rooms (directed navigation) |
| **Navigation** | Open exploration, any direction | Maze-like — main spine + side branches |
| **Structures** | Geological only (fossils, hotsprings) | Goblin infrastructure (houses, city, mine corridors) |
| **Faction** | Scarak (insectoid hive) | Goblin (mining operation) |
| **Boss** | Scarak_Broodmother (hive queen) | Goblin_Duke (3-phase throne room fight) |
| **WorldGen Props** | 12 layers (floor + ceiling + landmarks) | Sparse — most detail comes from labyrinth prefabs |
| **3D Noise** | YES (amp=0.35, creates pillars/alcoves) | NO (mine is structured, not organic) |

---

## Architecture — Three-System Adaptation

Mountains adapts the three-system architecture with a fundamental change: **System 2 is replaced by the labyrinth carver.**

| System | Standard Biomes | Mountains |
|--------|----------------|-----------|
| **1. WorldGen Props** | Landscape + natural props (trees, rocks) | Solid terrain mass + ceiling + spawn clearing + boundary walls. Minimal props (wall stalactites, moss) |
| **2. RealmStructurePlacer** | Scatter individual structures on terrain | **RealmLabyrinthPlacer** — carves Goblin_Lair corridors/rooms from solid terrain with `force=true` |
| **3. BossStructurePlacer** | Compound boss camps (center + satellites) | **Integrated into labyrinth** — boss room is the final spine piece (Goblin_Lair_Room_Boss_001). No separate BossStructurePlacer needed |

### System 1: WorldGen Terrain

Creates the solid stone mass that the labyrinth will be carved from.

**Key difference from Caverns:** No 3D volumetric noise. The mine interior is structured, not organic. The solid mass is uniform — all detail comes from the labyrinth prefabs.

**Terrain shape:**
- Solid stone mass filling the arena volume
- Ceiling dome (Y=95 center, Y=80 edge) — lower and more oppressive than Caverns
- Textured boundary walls (3D noise for craggy mine face surface)
- Small spawn clearing at arena center — open area ~8 block radius where players orient before entering corridors
- Nearly flat floor base (noise_amplitude=0.05) — mines are leveled

**What WorldGen props CAN do:**
- Stalactites on exposed ceiling surfaces (visible in spawn clearing and where corridors intersect walls)
- Geological bands in boundary walls (wall_strata)
- Moss/ground cover on exposed floor (spawn clearing area)
- The labyrinth is carved AFTER WorldGen, so props inside the solid mass are invisible and harmless

### System 2: RealmLabyrinthPlacer (NEW)

Replaces RealmStructurePlacer for this biome. Full design in `LABYRINTH_GENERATION_SYSTEM.md`.

**Summary:**
1. Phase 1 — **Main Spine** (guaranteed): Entry → Corridors → Junctions → Boss Room
2. Phase 2 — **Side Branches** (probabilistic): Extra rooms, houses, dead ends, treasure
3. Phase 3 — **Paste All** (`force=true`): Air blocks carve corridors, solid blocks create walls/decor
4. Phase 4 — **Validate**: Minimum piece count, boss room placed, retry on failure

**Piece library:** 97 Goblin_Lair prefabs (48 city + 43 mine + 6 entrances).

### System 3: Boss Encounter

Goblin_Duke spawns in `Goblin_Lair_Room_Boss_001` — the final piece of the labyrinth main spine. The boss room is guaranteed by Phase 1 (spine generation). No separate BossStructurePlacer compound needed because:
- The boss room IS a labyrinth piece (pasted during spine generation)
- Side corridors branching from the boss room entrance create natural "approach" feel
- The boss room prefab already has internal decoration via PrefabSpawnerBlocks

---

## Terrain Specification (BiomeSpec)

```python
BiomeSpec(
    name="Mountains",
    biome_prefix="Realm_Mountains",

    # ═══ MATERIALS ═══
    # Rock_Stone primary, Rock_Basalt secondary — two-material FieldFunction zones
    # Creates visual variety in corridor walls (stone areas vs basalt areas)
    surface_materials=["Rock_Stone", "Rock_Basalt"],
    sub_material="Rock_Slate",
    deep_material="Rock_Stone",
    spawn_platform_material="Rock_Sandstone",  # Distinct spawn area floor

    # ═══ ATMOSPHERE ═══
    # Underground mine — dusty amber fog, warm torchlight feel
    # Tighter fog than Caverns (mine corridors, not cathedral)
    environment="Zone1_Underground",
    fog_distance=[10, 35],             # Very tight — corridors, not open cave
    fog_color="#1a1510",               # Dusty brown (mine dust)
    sky_top_color="#0a0808ff",         # Near-black (underground)
    sky_bottom_color="#1a1210ff",      # Warm dark brown
    fog_density=0.80,
    particle_id="Dust_Cave_Flies",     # Mine dust motes
    particle_color="#a08040",          # Amber/gold dust (torchlight reflection)
    tint_colors=["#3a2a1a", "#2a1a0a", "#4a3a2a"],  # Warm brown mine tones

    # ���══ TERRAIN NOISE ═══
    # Nearly flat — mines are leveled by goblin workers
    # Low amplitude prevents uneven corridors after carving
    noise_amplitude=0.05,
    noise_scale=40,
    noise_octaves=1,                   # Minimal detail (artificial, not natural)

    # ═══ BOUNDARY ═══
    # Textured mine walls — 3D noise for craggy rock face
    # Higher density than Caverns (thicker mine walls)
    boundary_type="textured",
    boundary_transition=0.12,          # Sharp transition (mine wall, not hillside)
    boundary_density=7,                # Very solid (must contain labyrinth)
    boundary_height=30,                # Tall walls (floor to ceiling)
    boundary_noise_scale=10,           # Smaller scale = rougher, more "mined" look
    boundary_noise_amp=0.45,           # Moderate texture

    # ═══ CEILING ═══
    # Lower than Caverns — claustrophobic mine shafts, not cathedral cave
    signature_type="ceiling",
    ceiling_y=95,                      # 30 blocks above base (vs Caverns 45)
    ceiling_edge_y=80,                 # Dome drops to 15 blocks at edges

    # ═══ NO 3D VOLUMETRIC NOISE ═══
    # Mines are structured/artificial — no organic pillars or alcoves
    # All interior detail comes from labyrinth prefabs
    cave_3d_noise_scale_xz=0,
    cave_3d_noise_scale_y=0,
    cave_3d_noise_amp=0.0,

    # ═══ MATERIAL LAYERING ═══
    surface_thickness=1,
    sub_thickness=2,
    mat_noise_scale=50,
    mat_noise_seed="mine_mats",

    # ═══ WALL STRATA ��══
    # Geological bands visible in boundary walls and exposed mine faces
    # Tells a story of what the goblins are mining through
    wall_strata=[
        {"top_y": 88, "bottom_y": 86, "material": "Rock_Basalt"},    # Upper basalt seam
        {"top_y": 80, "bottom_y": 77, "material": "Rock_Shale"},     # Middle shale band
        {"top_y": 72, "bottom_y": 69, "material": "Rock_Slate"},     # Lower slate deposit
        {"top_y": 62, "bottom_y": 60, "material": "Rock_Sandstone"}, # Deep sandstone
    ],

    # ═══ GROUND COVER ��══
    # Sparse mine moss — only visible in spawn clearing and exposed surfaces
    # Much sparser than Caverns (this is a worked mine, not natural cave)
    grass_blocks=[
        {"block": "Plant_Moss_Block_Green", "weight": 20},
        {"block": "Plant_Moss_Green_Dark", "weight": 15},
        {"block": "Plant_Fern", "weight": 5},
    ],
    grass_noise_scale=25,
    grass_seed="mine_moss",
    grass_skip_sparse=0.95,            # Very sparse (mined floor, not natural)
    grass_skip_moderate=0.80,
    grass_skip_dense=0.50,

    # ═══ PROP LAYERS ═══
    # Sparse — most interior detail comes from labyrinth prefabs
    prop_layers=[...],  # See "WorldGen Prop Layers" section below
)
```

---

## WorldGen Prop Layers

Sparse by design — the labyrinth prefabs provide most visual detail. These props decorate the exposed terrain surfaces: spawn clearing, boundary walls, and ceiling.

### Floor Props (Spawn Clearing + Exposed Surfaces)

#### 1. `mine_rocks` — Stone scatter on exposed floor
```python
PropLayer("mine_rocks", [
    {"Path": "Rock_Formations/Rocks/Stone/Small", "Weight": 4},
    {"Path": "Rock_Formations/Rocks/Basalt/Small", "Weight": 3},
    {"Path": "Rock_Formations/Rocks/Shale/Small", "Weight": 3},
    {"Path": "Rock_Formations/Rocks/Slate/Small", "Weight": 2},
], mesh_scale_x=9, mesh_scale_z=9, skip_chance=0.40, occurrence_max=0.25)
```

#### 2. `mineral_deposits` — Geode and crystal nodes (mine resources)
Zone-based: Crystal veins in basalt areas, plain rock in stone areas.
```python
PropLayer("mineral_deposits", [], mesh_scale_x=14, mesh_scale_z=14, skip_chance=0.40,
          occurrence_max=0.20, zones=[
    PropZone(-1.0, -0.1, [
        {"Path": "Rock_Formations/Rocks/Geodes/White", "Weight": 3},
        {"Path": "Rock_Formations/Rocks/Geodes/Cyan", "Weight": 2},
        {"Path": "Rock_Formations/Rocks/Calcite/Small", "Weight": 3},
    ]),
    PropZone(-0.1, 1.0, [
        {"Path": "Rock_Formations/Rocks/Quartzite/Small", "Weight": 3},
        {"Path": "Rock_Formations/Rocks/Quartzite/Large", "Weight": 1},
        {"Path": "Rock_Formations/Rocks/Basalt/Large", "Weight": 2},
    ]),
], zone_noise_scale=60, zone_seed="mine_zones")
```

#### 3. `stalagmites_floor` — Floor formations (natural cave remnants the goblins mined around)
```python
PropLayer("stalagmites_floor", [
    {"Path": "Rock_Formations/Stalactites/Basalt/Floor", "Weight": 4},
    {"Path": "Rock_Formations/Rocks/Basalt/Hexagon", "Weight": 2},
    {"Path": "Rock_Formations/Rocks/Shale/Large", "Weight": 1},
], mesh_scale_x=18, mesh_scale_z=18, skip_chance=0.45, occurrence_max=0.15,
   scale_with_radius=False, scanner_max_y=8)
```

#### 4. `mine_floor_detail` — Dense small scatter filling gaps
```python
PropLayer("mine_floor_detail", [
    {"Path": "Rock_Formations/Rocks/Stone/Small", "Weight": 3},
    {"Path": "Rock_Formations/Rocks/Slate/Small", "Weight": 2},
    {"Path": "Rock_Formations/Rocks/Shale/Small", "Weight": 2},
    {"Path": "Rock_Formations/Rocks/Calcite/Small", "Weight": 1},
], mesh_scale_x=7, mesh_scale_z=7, skip_chance=0.45, occurrence_max=0.20)
```

### Ceiling Props

#### 5. `mine_stalactites` — Ceiling formations (natural remnants in mine roof)
Zone-based: crystal stalactites in mineral-rich areas, plain basalt elsewhere.
```python
PropLayer("mine_stalactites", [], mesh_scale_x=8, mesh_scale_z=8, skip_chance=0.25,
          occurrence_max=0.30, placement="ceiling", zones=[
    PropZone(-1.0, -0.1, [
        {"Path": "Rock_Formations/Stalactites/Basalt_Crystal_Blue/Ceiling", "Weight": 3},
        {"Path": "Rock_Formations/Stalactites/Basalt_Crystal_Purple/Ceiling", "Weight": 2},
        {"Path": "Rock_Formations/Stalactites/Basalt/Ceiling", "Weight": 3},
    ]),
    PropZone(-0.1, 1.0, [
        {"Path": "Rock_Formations/Stalactites/Basalt/Ceiling", "Weight": 5},
        {"Path": "Rock_Formations/Stalactites/Stone/Ceiling", "Weight": 3},
    ]),
], zone_noise_scale=60, zone_seed="mine_zones")
```

#### 6. `crystal_ceiling` — Rare crystal clusters (visible in large rooms)
```python
PropLayer("crystal_ceiling", [], mesh_scale_x=14, mesh_scale_z=14, skip_chance=0.40,
          occurrence_max=0.20, placement="ceiling", zones=[
    PropZone(-1.0, -0.1, [
        {"Path": "Rock_Formations/Stalactites/Basalt_Crystal_Blue/Ceiling", "Weight": 4},
        {"Path": "Rock_Formations/Stalactites/Basalt_Crystal_Purple/Ceiling", "Weight": 3},
        {"Path": "Rock_Formations/Stalactites/Basalt_Crystal_Red/Ceiling", "Weight": 2},
    ]),
], zone_noise_scale=60, zone_seed="mine_zones")
```

### Landmark Props

#### 7. `basalt_columns` — Rare hexagonal columns (mine support pillars)
```python
PropLayer("basalt_columns", [
    {"Path": "Rock_Formations/Rocks/Basalt/Hexagon", "Weight": 3},
    {"Path": "Rock_Formations/Rocks/Basalt/Large", "Weight": 1},
], mesh_scale_x=40, mesh_scale_z=40, skip_chance=0.5, occurrence_max=0.06,
   scale_with_radius=False, scanner_max_y=8)
```

**Total: 7 WorldGen prop layers** — deliberately sparse compared to Caverns (12) or Forest (9). The labyrinth prefabs provide the majority of visual detail.

---

## Labyrinth Specification

### Piece Selection

**Design decision: Mixed system.** Prefabs_Mine for ALL corridors and connectors (modular, predictable sizing). Prefabs_Goblin for major rooms only (richer encounter spaces). This gives us procedural reliability from Mine pieces with handcrafted quality from Goblin rooms at key moments.

**Corridors & Connectors — Prefabs_Mine** (~42 pieces):
- 5 corridor lengths × 5 variants = 25 straight corridors (reliable variety)
- End (5) + End_Secret (5) = 10 dead-end terminators
- T (3) + Shift (3) + Side (3) = 9 junction/branching pieces
- Transitions (2) = mine ↔ goblin city connectors

**Major Chambers — Prefabs_Goblin** (selected pieces only):
- `Room_Boss_001` — Boss encounter (spine terminus, guaranteed)
- `Room_Beast_001/002` — Mini-boss encounter rooms (spine, radius-gated)
- `Room_Large1/Large2_001-003` — Open combat chambers (spine, 4 variants)
- `Room_Boss_Entrance_001` — Pre-boss transition (spine, before boss)
- `Houses/Basalt_1-4, Stone_1-4` — Residential side rooms (branches, decoration via selective spawner expansion)
- `Cap_Dead_End_001` — Corridor terminators (branches)
- `Cap_Hidden_Treasure_001` — Reward rooms at branch endpoints
- `Mushrooms/Small_001` — Environmental dressing (branches)

**PrefabSpawnerBlock handling:** Selective expansion (Option 2 from LABYRINTH_GENERATION_SYSTEM.md). Expand spawners matching `Houses/*` or `Mushrooms/*` paths. Strip all corridor/room/junction spawner blocks after paste — the labyrinth placer controls layout, not vanilla recursion.

**Entry piece:** `Mine_Transition_Goblin_Stairs_001` connecting spawn clearing to mine network. If unavailable or too large, use `Corridor/Short/Short_001` as fallback.

### Generation Parameters (Scaled by Arena Radius)

| Parameter | R35 | R50 | R70 | R100 | R130+ |
|-----------|-----|-----|-----|------|-------|
| **Spine length** | 3-4 | 5-6 | 7-9 | 10-13 | 14-18 |
| **Side branch depth** | 1 | 1-2 | 2 | 2-3 | 3 |
| **Junction frequency** | Every 2nd | Every 2nd | Every 2nd | Every 3rd | Every 3rd |
| **Target total pieces** | 8-12 | 15-22 | 25-35 | 40-55 | 60-80 |
| **Branch chance** | 0.6 | 0.7 | 0.7 | 0.8 | 0.8 |
| **Beast rooms** | 0 | 0-1 | 1 | 1-2 | 2-3 |
| **Treasure caps** | 0-1 | 1 | 1-2 | 2-3 | 3-5 |

### Spine Structure (Phase 1 — Guaranteed)

```
Spawn Clearing (WorldGen open area, ~8 block radius)
    │
    ▼
Entry Transition (mine entrance)
    │
    ▼
Corridor ──► Junction ──► Corridor ──► [Room_Large1 or Beast] ──► Corridor
                │                                                      │
                ▼                                                      ▼
          Side Branch                                        Boss Entrance
          (Phase 2)                                                │
                                                                   ▼
                                                          Room_Boss_001
                                                         (Goblin_Duke)
```

### Side Branch Structure (Phase 2 — Probabilistic)

Each junction on the spine can grow 1-2 side branches:
```
Junction
    │
    ├──► Corridor ──► Dead_End  (simple terminator)
    │
    └���─► Corridor ──► Houses ──► Dead_End  (residential wing)
              │
              └──► Corridor ──► Hidden_Treasure  (reward room)
```

### Failure Handling

| Failure | Detection | Response |
|---------|-----------|----------|
| Piece extends beyond arena | Bounding box vs arena radius (×0.85 margin) | Skip branch, continue others |
| Piece overlaps existing piece >50% | Bounding box intersection check | Skip branch, continue others |
| Total pieces < minimum threshold | Post-generation count | Regenerate with new seed (max 3 attempts) |
| Boss room not placed | Flag check after Phase 1 | Regenerate (spine failure = total failure) |
| All 3 regeneration attempts fail | Attempt counter | Fall back to open-chamber mode (no labyrinth, just boss in center — last resort) |

---

## Mob Pool (Confirmed in realm-mobs.yml)

| Role | ID | Weight | Style |
|------|----|--------|-------|
| Melee DPS | `Goblin_Scrapper` | 25 | Basic fighter — grunt swarms in corridors |
| Ranged | `Goblin_Lobber` | 20 | Projectiles — dangerous in narrow corridors |
| Tank | `Goblin_Ogre` | 15 | Heavy brute — blocks corridor paths |
| Stealth | `Goblin_Thief` | 15 | Ambush attacker — exploits blind corners |
| Worker | `Goblin_Miner` | 15 | Pickaxe fighter — mining worker-combatant |
| Creature | `Bear_Grizzly` | 10 | Mountain beast — rare in larger chambers |
| **Boss** | `Goblin_Duke` | 10 | 3-phase fight (Phase 1→2→3 Fast/Slow) |

### Mob Spawn Adaptation

Standard RealmMobSpawner assumes open terrain. Labyrinth needs adaptation:

**Spawn position finding:**
- During labyrinth generation (Phase 3), record the center position of each placed piece
- These become the **valid spawn position pool** for RealmMobSpawner
- Mobs spawn at recorded positions (guaranteed air + solid floor)
- Larger rooms get more spawn slots (room center + offsets)

**Mob leash:**
- Standard leash radius (20 blocks) works in corridors — mobs patrol back and forth
- Mobs cannot path through solid walls (labyrinth walls contain them naturally)
- Boss mob leash centered on boss room center

**Spawn distribution:**
- Early corridors (near spawn): Lower density, weaker mobs
- Deep corridors (near boss): Higher density, more elites
- Side branches: Treasure rooms have guardian mobs (1-2 per room)

---

## Atmosphere

### Environment
- **Base:** `Zone1_Underground` (same as Caverns, Frozen Crypts)
- **Character:** Dusty, warm amber — torchlit mine vs Caverns' cold blue crystal cave

### Fog
- **Distance:** [10, 35] — tighter than Caverns [15, 45]. Mine corridors limit sight
- **Color:** `#1a1510` — dusty brown (mine dust in torchlight)
- **Density:** 0.80 — thick but not oppressive

### Particles
- **System:** `Dust_Cave_Flies`
- **Color:** `#a08040` — amber/gold dust motes (reflecting torchlight)

### Colors
- **Tint:** `["#3a2a1a", "#2a1a0a", "#4a3a2a"]` — warm brown mine tones
- **Sky:** Near-black top (`#0a0808`), warm dark brown bottom (`#1a1210`)
- **No color filter** — the fog and tint do enough
- **No screen effect** — clean visibility for combat in tight spaces

### Comparison

| Parameter | Caverns | Mountains |
|-----------|---------|-----------|
| Fog distance | [15, 45] | [10, 35] |
| Fog color | #0a0a1a (cold blue-black) | #1a1510 (warm brown) |
| Particles | #3060c0 (blue) | #a08040 (amber) |
| Tints | Blues/purples | Browns/ambers |
| Feel | Cold, alien, vast | Warm, dusty, cramped |

---

## Ground Cover

**Minimal** — most floor is inside prefabs. Ground cover only appears on WorldGen-exposed surfaces (spawn clearing, boundary walls).

```python
grass_blocks = [
    {"block": "Plant_Moss_Block_Green", "weight": 20},   # Standard cave moss
    {"block": "Plant_Moss_Green_Dark", "weight": 15},     # Dark moss
    {"block": "Plant_Fern", "weight": 5},                 # Rare fern
]
grass_skip_sparse  = 0.95  # Extremely sparse in open areas
grass_skip_moderate = 0.80  # Sparse in moderate zones
grass_skip_dense   = 0.50  # Only moderate in "dense" zones
```

Deliberately much sparser than Caverns (which has lush bioluminescent moss). This is a worked mine — goblins clear vegetation for their operations.

**Note:** `Plant_Fern` is available but unverified in current biomes (Caverns uses only moss variants). If it causes issues during testing, replace with `Plant_Moss_Rug_Lime` (verified in Caverns).

---

## Wall Geological Strata

Four visible bands in boundary walls, telling the geological story of the mine:

```python
wall_strata = [
    {"top_y": 88, "bottom_y": 86, "material": "Rock_Basalt"},     # Upper basalt seam
    {"top_y": 80, "bottom_y": 77, "material": "Rock_Shale"},      # Middle shale band
    {"top_y": 72, "bottom_y": 69, "material": "Rock_Slate"},      # Lower slate deposit
    {"top_y": 62, "bottom_y": 60, "material": "Rock_Sandstone"},  # Deep sandstone
]
```

---

## Implementation Order

### Phase A: Terrain (WorldGen)
1. Update BiomeSpec in `generate-realm-biomes.py` with the spec above
2. Generate biome JSONs (`python scripts/generate-realm-biomes.py --biome MOUNTAINS`)
3. Deploy + test: solid terrain mass with ceiling, boundary walls visible, spawn clearing
4. Verify: NOT void, ceiling present, fog/particles correct

### Phase B: Labyrinth Placer (NEW)
1. Create `RealmLabyrinthPlacer.java` with piece metadata + recursive generation
2. Integrate into realm instance lifecycle (after terrain gen, before mob spawn)
3. Test with Mine corridor pieces first (simplest, most modular)
4. Add Goblin room pieces + boss room
5. Test piece connections, rotation, overlap handling
6. **Critical test:** `force=true` paste into solid terrain — verify air blocks carve correctly

### Phase C: Mob Spawning Adaptation
1. Labyrinth placer outputs list of valid spawn positions
2. RealmMobSpawner reads spawn positions for MOUNTAINS biome (instead of random terrain scanning)
3. Test mob spawning inside corridors — verify pathfinding, leash, no stuck mobs

### Phase D: Boss Integration
1. Verify `Goblin_Lair_Room_Boss_001` pastes correctly with `force=true`
2. Goblin_Duke spawns at boss room center
3. Test 3-phase boss fight in enclosed room

### Phase E: Polish
1. Selective PrefabSpawnerBlock expansion (houses, mushrooms, decorative children)
2. Compass marker guidance (points toward boss room)
3. Test at multiple arena sizes (R35, R70, R100)
4. Performance profiling (labyrinth generation time)

---

## Testing Checklist

### Terrain (Phase A)
- [ ] Solid terrain generates (NOT void)
- [ ] Spawn point on solid ground (Rock_Sandstone platform)
- [ ] Ceiling visible at Y=95 center
- [ ] Ceiling dome drops at edges (Y=80)
- [ ] Textured boundary walls visible
- [ ] Wall strata bands visible (4 bands)
- [ ] Fog/particles correct (amber dust, warm brown fog)
- [ ] Ground cover visible in spawn clearing (sparse moss)
- [ ] Ceiling stalactites present
- [ ] Mineral deposits visible (geodes, quartzite)

### Labyrinth (Phase B)
- [ ] Corridors carved from solid terrain (air blocks replace stone)
- [ ] Player can walk through corridors
- [ ] Junctions connect correctly (no gaps, no floating blocks)
- [ ] Rotation propagation correct (corridors face right directions)
- [ ] No corridors extending beyond arena boundary
- [ ] Boss room reachable from spawn via main spine
- [ ] Side branches generate from junctions
- [ ] Dead ends terminate properly (Cap prefabs)
- [ ] Minimum piece count met at all radius tiers
- [ ] R35: compact mine (8-12 pieces)
- [ ] R100: sprawling complex (40-55 pieces)
- [ ] Regeneration works on failure (max 3 attempts)

### Mobs (Phase C)
- [ ] Goblins spawn inside corridors (not in solid stone)
- [ ] Mobs patrol corridors correctly (no stuck mobs)
- [ ] Mob leash keeps them in reasonable range
- [ ] Bear_Grizzly spawns in larger rooms only
- [ ] Elite/boss chance scaling works normally

### Boss (Phase D)
- [ ] Goblin_Duke spawns in boss room
- [ ] 3-phase fight works in enclosed space
- [ ] Boss compass marker points to boss room
- [ ] Boss drops/rewards trigger normally

### Integration
- [ ] Test at R35, R50, R70, R100, R130
- [ ] Performance: labyrinth generation < 2 seconds
- [ ] No server crashes during generation
- [ ] Player can enter realm, navigate labyrinth, kill boss, exit
- [ ] Death/respawn works correctly (respawn at realm spawn, not inside solid stone)

---

## Goblin Lair Piece Paths (Quick Reference)

### Mine Corridors (Prefabs_Mine — Primary)
```
Dungeon/Goblin_Lair/Prefabs_Mine/Corridor/End/End_001-005
Dungeon/Goblin_Lair/Prefabs_Mine/Corridor/End_Secret/End_Secret_001-005
Dungeon/Goblin_Lair/Prefabs_Mine/Corridor/Long/Long_001-005
Dungeon/Goblin_Lair/Prefabs_Mine/Corridor/Medium/Medium_001-005
Dungeon/Goblin_Lair/Prefabs_Mine/Corridor/Short/Short_001-005
Dungeon/Goblin_Lair/Prefabs_Mine/Corridor/Shift/Shift_001-003
Dungeon/Goblin_Lair/Prefabs_Mine/Corridor/Side/Side_001-003
Dungeon/Goblin_Lair/Prefabs_Mine/Corridor/T/T_001-003
Dungeon/Goblin_Lair/Prefabs_Mine/Room/End/End_001-002
Dungeon/Goblin_Lair/Prefabs_Mine/Room/T/T_001-002
Dungeon/Goblin_Lair/Prefabs_Mine/Room/Y/Y_001-002
```

### City Rooms (Prefabs_Goblin — Major Chambers)
```
Dungeon/Goblin_Lair/Prefabs_Goblin/Room/Beast/Beast_001-002
Dungeon/Goblin_Lair/Prefabs_Goblin/Room/Boss/Boss_001
Dungeon/Goblin_Lair/Prefabs_Goblin/Room/Boss_Entrance/Boss_Entrance_001
Dungeon/Goblin_Lair/Prefabs_Goblin/Room/Large1/Large1_001
Dungeon/Goblin_Lair/Prefabs_Goblin/Room/Large2/Large2_001-003
Dungeon/Goblin_Lair/Prefabs_Goblin/Houses/Basalt_1-4
Dungeon/Goblin_Lair/Prefabs_Goblin/Houses/Stone_1-4
Dungeon/Goblin_Lair/Prefabs_Goblin/Cap/Dead_End_001
Dungeon/Goblin_Lair/Prefabs_Goblin/Cap/Hidden_Treasure_001
Dungeon/Goblin_Lair/Prefabs_Goblin/Mushrooms/Small_001
```

### Connectors
```
Dungeon/Goblin_Lair/Prefabs_Goblin/Corner/Corner_001-002
Dungeon/Goblin_Lair/Prefabs_Goblin/Corner/Corner_Long_001
Dungeon/Goblin_Lair/Prefabs_Goblin/T-Junction/T-Junction_001-003
Dungeon/Goblin_Lair/Prefabs_Goblin/Stairs/Stairs_U_Turn_001
Dungeon/Goblin_Lair/Prefabs_Goblin/Straight/Length_7-24/*
```

---

## Related Files

| File | Purpose |
|------|---------|
| `docs/WorldgenV2/LABYRINTH_GENERATION_SYSTEM.md` | Vanilla CaveGenerator analysis + RealmLabyrinthPlacer design |
| `docs/WorldgenV2/BIOME_MOUNTAINS_PLAN.md` | Original planning document (superseded by this spec) |
| `docs/WorldgenV2/BIOME_CAVERNS.md` | Reference for ceiling biome implementation |
| `docs/WorldgenV2/REALM_BIOME_CREATION_GUIDE.md` | Three-system architecture reference |
| `scripts/generate-realm-biomes.py` | Biome JSON generator (Mountains BiomeSpec at line 1383) |
| `src/.../maps/core/RealmBiomeType.java` | MOUNTAINS enum entry |
| `src/main/resources/config/realm-mobs.yml` | Mob pool (lines 297-320) |
| `docs/WorldgenV2/FACTION_CREATURE_DATABASE.md` | Goblin faction details |
| `docs/reference/confirmed-dead-approaches.md` | What NOT to try |
