# Realm Biome Creation Guide

**READ THIS ENTIRE FILE before creating or modifying any realm biome.** This is the authoritative reference for how biomes work in Trail of Orbis. Every completed biome (Forest, Desert, Tundra) was built following these patterns exactly.

## The Three-System Architecture

A biome is NOT just a JSON file with props. Every complete biome uses **three placement systems working together**. Missing any system = empty, lifeless biome.

| System | Where | When | What Goes Here | Example |
|--------|-------|------|----------------|---------|
| **WorldGen Props** | `generate-realm-biomes.py` → biome JSON | Terrain generation | Simple prefabs: trees, plants, rocks, arches, pillars, individual NPC structures, stalactites | Forest: 32 unique paths, 16 layers |
| **RealmStructurePlacer** | `RealmStructurePlacer.java` STRUCTURE_POOLS | After chunks load, before mobs | Complex prefabs with PrefabSpawnerBlocks: Monuments, Fossils, Hotsprings, compound camps | Desert: 17 entries |
| **BossStructurePlacer** | `BossStructurePlacer.java` BOSS_CAMPS | During boss mob spawn | Compound boss camps: center + satellites + dirt paths | Forest: 3 tiers (Trork camps) |

**Critical rule:** Any prefab with internal PrefabSpawnerBlock references **WILL CRASH WorldGen** → void world. These MUST go in RealmStructurePlacer or BossStructurePlacer (runtime `PrefabUtil.paste()`), NEVER in WorldGen Props.

## What Makes a Complete Biome

### Minimum Checklist

- [ ] **BiomeSpec** in `generate-realm-biomes.py` with ALL fields configured
- [ ] **8+ WorldGen prop layers** with 20+ unique prefab paths
- [ ] **FieldFunction zones** on primary vegetation layer (organic variety)
- [ ] **Landmark features** — arches, pillars, or equivalent unique formations
- [ ] **NPC/faction structures** (WorldGen individual pieces, tier-gated by radius)
- [ ] **Ground cover** (grass_blocks Column props)
- [ ] **Tier gating** — progressive content via min_arena_radius
- [ ] **RealmStructurePlacer entries** for complex prefabs (if biome has monuments/ruins)
- [ ] **BossStructurePlacer entries** for boss camps (center + satellites)
- [ ] **Atmosphere** — fog, particles, tint colors, screen effect, color filter
- [ ] **Signature terrain** — unique density feature (lava_moat, frozen_lake, etc.)
- [ ] **Wall strata** — visible geological bands in boundary walls
- [ ] **Path material** — terrain trails (optional but adds richness)

### Reference: Completed Biome Stats

| Biome | WorldGen Layers | Unique Paths | Arches/Pillars | Runtime Structures | Boss Camps |
|-------|-----------------|-------------|----------------|-------------------|------------|
| **Forest** | 9 (natural only) | 28 | Forest Arches + Stone Pillars | 21 RealmStructurePlacer (Trork R35-R100+) | 3 tiers (Trork) |
| **Desert** | 7 | 25 | 3 Arch types + 4 Pillar types | 17 RealmStructurePlacer (Monuments) | 3 tiers (Sandstone) |
| **Tundra** | 8 (natural only) | 20 | Snowy+Tundra Arches + Pillars | 23 RealmStructurePlacer (Outlander+Yeti R35-R100+ & ruins) | 3 tiers (Outlander) |
| **Jungle** | 13 (natural only) | 40+ | Flower+Hedera Arches + Jungle Pillars | 26 RealmStructurePlacer (Feran R35-R100+ & Slothian ruins) | None (Rex spawns open) |

**Architecture note (April 2026):** ALL faction structures (Trork, Outlander, Feran) are placed via RealmStructurePlacer at runtime, NOT in WorldGen prop layers. WorldGen has no cross-layer collision detection — structures from different prop layers overlap freely. Runtime placement uses StructureBoundsRegistry with 2-block safety margins, multi-point ground sampling, and 12 retry attempts. See individual biome docs for details.

## The Generation Script

**File:** `scripts/generate-realm-biomes.py`

```bash
python scripts/generate-realm-biomes.py                    # Generate ALL biomes
python scripts/generate-realm-biomes.py --biome VOLCANO    # One biome only
python scripts/generate-realm-biomes.py --dry-run           # Preview only
```

Generates per biome: 15 biome JSONs (R25-R130) + 15 WorldStructures + 15 instances + environment + weather.

### BiomeSpec Fields (Complete Reference)

| Field | Purpose | Example |
|-------|---------|---------|
| `name` | Display name | "Forest" |
| `biome_prefix` | Asset name prefix | "Realm_Forest" |
| `surface_materials` | Surface blocks (1-2, FieldFunction if 2+) | ["Soil_Grass_Full"] or ["Rock_Volcanic", "Rock_Basalt"] |
| `sub_material` | Below surface | "Soil_Dirt" |
| `deep_material` | Deep underground | "Rock_Stone" |
| `spawn_platform_material` | Center spawn area block | "Soil_Dirt" |
| `environment` | Vanilla environment name | "Env_Zone1_Plains" |
| `tint_colors` | 2-4 hex colors for DensityDelimited tinting | ["#228B22", "#1E7B1E", "#2D9B2D"] |
| `noise_amplitude` | Terrain height variation (0.10-0.50) | 0.20 |
| `noise_scale` | Terrain feature size in blocks | 30 |
| `noise_octaves` | Detail levels (2-3) | 2 |
| `noise_persistence` | Detail amplitude falloff | 0.5 |
| `boundary_type` | Wall style: "rising", "textured", "floating" | "rising" |
| `boundary_transition` | Wall slope fraction (0.1=cliff, 0.5=gentle) | 0.30 |
| `boundary_density` | Wall solidity (3-8, must overpower floor's -1) | 5 |
| `boundary_height` | Wall height in blocks | 10 |
| `boundary_noise_scale` | 3D noise for textured walls (0=off) | 15 |
| `boundary_noise_amp` | 3D noise amplitude for textured walls | 0.4 |
| `signature_type` | Unique terrain feature | "elevated_center", "lava_moat", "frozen_lake", "ceiling", "sunken_basin", "tidal_shelf", "fractured", "floating" |
| `fluid_material` | Fluid for signature (lava/water) | "Fluid_Lava" |
| `fluid_bottom_y` / `fluid_top_y` | Fluid Y range relative to Base | -10, -2 |
| `path_material` | Noise-based terrain trails | "Rock_Sandstone" |
| `path_noise_scale` / `path_width` | Trail spacing and width | 35, 0.10 |
| `wall_strata` | Geological bands in walls | [{"top_y": 72, "bottom_y": 70, "material": "Soil_Sand_Red"}] |
| `grass_blocks` | Column prop ground cover | [{"block": "Plant_Grass_Lush_Short", "weight": 30}] |
| `grass_skip_sparse/moderate/dense` | Per-zone skip chances | 0.92, 0.60, 0.20 |
| `snow_noise_scale` / `snow_noise_max` | Variable-depth surface layer | 30, 4 |
| `fog_distance` | [near, far] fog planes | [50, 100] |
| `fog_color` / `fog_density` | Fog appearance | "#a8c8a0", 0.5 |
| `particle_id` / `particle_color` | Ambient particles | "Ash", "#abb3b4" |
| `screen_effect` / `screen_effect_color` | Full-screen overlay | "ScreenEffects/Fire.png", "#ffffff0d" |
| `color_filter` | Color tint overlay | "#ff8060" |

### PropLayer Fields (Complete Reference)

| Field | Purpose | Default |
|-------|---------|---------|
| `name` | Layer identifier (used in seeds) | required |
| `prefab_paths` | Default prefab list (when no zones) | required |
| `mesh_scale_x/z` | Grid spacing in blocks (BASE, scales with radius) | required |
| `skip_chance` | 0-1 chance to skip valid positions | required |
| `jitter` | Position randomness (0-1) | 0.4 |
| `occurrence_max` | Max density (higher = more props) | 0.25 |
| `min_arena_radius` | Only include when radius >= this | 0 |
| `max_arena_radius` | Only include when radius <= this | 999 |
| `scale_with_radius` | False = fixed spacing (structures get MORE on bigger maps) | True |
| `scanner_max_y` | Max Y above Base to search. 60 for trees, **8 for structures** | 60 |
| `sink_into_ground` | Place AT ground block (bushes rooted in terrain) | False |
| `placement` | "floor", "ceiling", or "wall" | "floor" |
| `zones` | FieldFunction zone list (PropZone objects) | [] |
| `zone_noise_scale` | SimplexNoise2D scale for zone boundaries | 60 |
| `zone_seed` | Noise seed for zones (SHARE across layers for coherent zones) | "zones" |
| `prop_type` | "prefab" or "pondfiller" | "prefab" |
| `pond_fill_material` | PondFiller: block to fill depressions | "" |
| `pond_barrier_materials` | PondFiller: blocks forming depression walls | [] |
| `pond_bounding_xz/y_down/y_up` | PondFiller: bounding box half-sizes | 8, 6, 3 |

## Prefab Path Safety Rules

### VOID CRASH: One Bad Path = Entire Biome Fails

WorldGen's PrefabProp uses `Files.list()` (NO recursion) to find `.prefab.json` files. If a directory has:
- **Only subdirectories** (no direct .lpf/.prefab.json files) → empty pool → crash → void world
- **PrefabSpawnerBlock references** inside any prefab → crash → void world

One bad path in ANY prop layer crashes the ENTIRE biome for ALL radius tiers.

### Safe Categories for WorldGen Props

These categories are proven safe (no PrefabSpawnerBlocks):
- `Trees/*` — ALL tree types/stages
- `Plants/*` — ALL bush, cacti, driftwood, fern, mushroom, twisted wood types
- `Rock_Formations/Rocks/*` — ALL rock types (Volcanic, Basalt, Sandstone, Geodes, etc.)
- `Rock_Formations/Arches/*` — ALL arch types
- `Rock_Formations/Pillars/*` — ONLY leaf directories with direct files (see traps below)
- `Rock_Formations/Stalactites/*` — Floor and Ceiling variants
- `Rock_Formations/Ice_Formations/*` — Icebergs etc.
- `Npc/*/` — ONLY individual structures (Tent, Bonfire, Watchtower, Houses, Spikes, etc.)

### UNSAFE for WorldGen (MUST use RealmStructurePlacer)

- `Monuments/*` — ALL types (camps, wells, outposts, temples, treasure rooms)
- `Rock_Formations/Fossils/*` — ALL sizes (Small through Gigantic)
- `Rock_Formations/Hotsprings/*` — ALL types
- `Npc/*/Encampment/*` — ALL compound layouts (Castle, Lumber, Quarry, etc.)
- `Npc/*/Camps/Tier3/*` — Compound tier 3 camps
- `Dungeon/*`, `Cave/*` — Complex underground structures

### Known Subdirectory Traps (Empty Pool → Crash)

These paths have ONLY subdirectories, NO direct files:
- `Rock_Formations/Pillars/Shale/Bare` → use `Bare/Small`, `Bare/Medium`, `Bare/Large`
- `Rock_Formations/Pillars/Shale/Grass` → same structure
- `Rock_Formations/Pillars/Shale/Plains` → same structure

### Verified Safe Prefab Paths (Proven in Working Biomes)

**Trees:** Oak, Birch, Ash, Beech, Maple, Cedar, Fir, Fir_Snow, Boab, Burnt, Dry, Palm, Bamboo, Banyan, Camphor, Willow, Wisteria, Petrified (Stage_1 through Stage_5 varies)

**Plants:** Bush/Cliff, Bush/Green, Bush/Lush, Bush/Winter, Bush/Dead_Lavathorn, Bush/Arid, Bush/Arid_Red, Bush/Brambles, Cacti/Flat/Stage_0, Cacti/Full/Stage_1-3, Driftwood/Dry, Driftwood/Cedar, Jungle/Ferns/Small, Jungle/Ferns/Large, Mushroom_Large/Green|Purple|Yellow/Stage_1|3, Mushroom_Rings, Twisted_Wood/Fire, Twisted_Wood/Ash

**Rocks:** Stone/Small|Large, Grass/Small, Sandstone/Small|Large, Sandstone/Red/Small, Volcanic/Large, Volcanic/Spiked/Large|Small, Basalt/Large|Small|Hexagon|Snowy|Tundra, Shale/Small|Large|Snowy|Spikey, Slate/Small|Large, Quartzite/Small|Large, Calcite/Small, Chalk/Small, Frozenstone/Small|Snowy, Geodes/Pink|Purple|Blue|Cyan|Green|White

**Arches:** Forest, Desert (9), Desert_Red (9), Savannah (5), Snowy, Tundra

**Pillars:** Rock_Stone/Ash (15), Rock_Stone/Plains (15), Rock_Stone/Oak (19), Shale/Bare/Small|Medium|Large (1 each), Snowy

**Stalactites:** Basalt/Floor, Basalt/Ceiling

**NPC Individual (no PrefabSpawnerBlocks):**
- Trork: Tent, Bonfire, Fireplace, Trap, Warning, Warehouse, Burrow, Tier_1-3/Watchtower, Tier_2-3/Store, Misc/Large, Resource/Lumber/*, Resource/Rock/Stone
- Outlander: Houses/Tier0-3, Forts/Tier1-3, Towers/Tier1-3, Gates/Tier3, Spikes, Totems, Braziers, Misc, Boats/Large
- Yeti: Camps

## Structure Placement Patterns

### RealmStructurePlacer — Runtime Complex Prefabs

**File:** `src/main/java/io/github/larsonix/trailoforbis/maps/spawning/RealmStructurePlacer.java`

Add entries to the `STRUCTURE_POOLS` static initializer:

```java
map.put(RealmBiomeType.YOUR_BIOME, List.of(
    // StructureEntry(dirPath, countPer100Blocks, minRadius)
    // countPer100Blocks: density — structures per 100-block arena radius
    // minRadius: only place when arena radius >= this
    new StructureEntry("Monuments/Incidental/Category/Type", 2.0, 35),
    new StructureEntry("Rock_Formations/Fossils/Size", 1.0, 55),
    // For water-designed prefabs on land:
    new StructureEntry("Monuments/Incidental/Shipwrecks/Cold", 0.5, 55, true), // removeWaterlog=true
));
```

Placement: uniform circular disk, 85% wall margin, 12-block spawn exclusion, 15-block minimum separation, 8 attempts per structure. Runs AFTER chunk loading, BEFORE mob spawning.

### BossStructurePlacer — Compound Boss Camps

**File:** `src/main/java/io/github/larsonix/trailoforbis/maps/spawning/BossStructurePlacer.java`

Add entries to the `BOSS_CAMPS` static initializer:

```java
map.put(RealmBiomeType.YOUR_BIOME, List.of(
    // CampTier(minRadius, maxRadius, centerPool, satellitePool, minSat, maxSat, minDist, maxDist)
    new CampTier(0, 55,
        List.of("center/path/1", "center/path/2"),
        List.of("satellite/path/1", "satellite/path/2"),
        1, 2, 6, 10),     // small: 1-2 satellites, 6-10 blocks from center
    new CampTier(55, 90,
        List.of("bigger/center/1"),
        List.of("satellite/1", "satellite/2", "satellite/3"),
        2, 4, 10, 18),    // medium: 2-4 satellites
    new CampTier(90, 999,
        List.of("biggest/center/1"),
        List.of("all/satellites"),
        4, 7, 12, 22)     // large: 4-7 satellites
));
```

Center and satellite pools are DIRECTORY paths — a random file from the directory is picked. BossStructurePlacer also generates Bresenham dirt paths connecting satellites to center.

**Note:** BossStructurePlacer uses `PrefabUtil.paste()` which bypasses PrefabLoader validation. Encampment/Layout prefabs (Castle, Lumber, Quarry) work here but NOT in WorldGen props.

## Deployment Procedure

```bash
# 1. Generate biome files
python scripts/generate-realm-biomes.py --biome YOUR_BIOME

# 2. Build (compiles Java + processes resources to build/)
./gradlew clean build

# 3. Deploy (JAR + asset pack + configs to server)
bash scripts/deploy.sh

# 4. RESTART the Hytale server
# Asset changes only take effect after full server restart
```

**Critical:** The asset pack deploys from `build/resources/main/hytale-assets` → `mods/TrailOfOrbis_Realms`. If biome files are in `src/` but NOT in `build/`, they won't deploy. Always `clean build` before `deploy.sh`.

## Testing Procedure

1. Restart server after deploy
2. Enter a realm of the biome type
3. Verify:
   - [ ] NOT void (terrain generates)
   - [ ] Spawn point is on solid ground (not in air/lava)
   - [ ] Surface material correct
   - [ ] Walls visible at arena edge
   - [ ] Trees/vegetation present and varied
   - [ ] Rocks/formations present
   - [ ] Faction structures present (if applicable, check R45+ arenas)
   - [ ] Arches/pillars visible as landmarks
   - [ ] Ground cover (grass/flowers) visible
   - [ ] Atmosphere correct (fog, particles, tint)
   - [ ] Lava/water features working (if applicable)
   - [ ] Boss spawns with boss camp structure
4. Test at different radii: R25 (minimal), R50 (standard), R100 (full features)
5. Check server logs for: `prefab pool contains empty list` (= bad prefab path → fix immediately)

## Common Failures

| Symptom | Cause | Fix |
|---------|-------|-----|
| **Void world** (no terrain) | Bad prefab path (empty dir or PrefabSpawnerBlocks) | Check ALL prefab paths against safe list. Remove suspect paths. Redeploy. |
| **Props only on walls** | scanner_max_y too high (finds wall terrain before floor) | Use scanner_max_y=8 for structures, 60 for trees |
| **No props at all** | Floor pattern doesn't match surface material | Ensure Floor BlockSet includes ALL surface_materials |
| **Maze terrain** | Wall density too low (1-2) | boundary_density must be 3-8 |
| **Biome looks empty** | Too few layers, missing systems | Check against completed biome stats table above |
| **PondFiller untested** | New prop type, may crash | Test in isolation, keep disabled until confirmed working |
| **Structures visible but no trees** | Scale mismatch or zone coverage | Compare mesh_scale and zone ranges against working biomes |

## Design Principles

1. **Density creates immersion** — Forest has mesh_scale=5 for trees (very dense). Don't be sparse.
2. **Zones create variety** — FieldFunction with shared zone_seed across layers means cooled/active/infernal areas are coherent.
3. **Tier gating creates progression** — R35 shows first structures, R70 shows settlements, R100 shows fortresses. Each arena size tells a different story.
4. **Three systems create richness** — WorldGen for the landscape, RealmStructurePlacer for ruins/monuments, BossStructurePlacer for boss encounters. ALL THREE are required for a complete biome.
5. **Fixed spacing for structures** — `scale_with_radius=False` means bigger arenas get MORE structures, not the same count spread thinner.
6. **Scanner_max_y=8 for structures** — Prevents placement on elevated boundary walls.
