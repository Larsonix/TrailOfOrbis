# Caverns Biome — "The Crystal Depths"

Enclosed underground cave system with three FieldFunction zones (Crystal / Stone / Fungal). First biome to use ceiling terrain, SimplexNoise3D volumetric features, and ceiling prop placement. Goblin miners + predatory cave fauna + geological titan boss.

**Last updated:** April 20, 2026

---

## Three New WorldGenV2 Features

### Feature 1: Cave Ceiling Density (New Terrain Architecture)

Every other biome is "floor + walls + open sky." Caverns adds a **dome-shaped ceiling** via a second BaseHeight CurveMapper referencing `BaseHeight: Ceiling` (Y=110). The ceiling slopes from center (highest) to edges (lower), creating enclosed cathedral chambers with a natural dome shape. SimplexNoise2D (±2 blocks) adds organic ceiling bumps.

**Density sum has 4 inputs** (not the usual 3):
1. Floor (BaseHeight "Base" CurveMapper) — standard
2. Walls (Axis boundary) — standard
3. **Ceiling** (BaseHeight "Ceiling" CurveMapper + dome slope + noise) — **NEW**
4. **3D volumetric** (SimplexNoise3D Normalizer) — **NEW**

### Feature 2: SimplexNoise3D Volumetric Carving

Unlike the `fractured` signature (subtractive only — carves pits), Caverns uses **bidirectional** 3D noise:
- Where 3D noise is positive → adds solid → creates **natural pillars** connecting floor to ceiling
- Where 3D noise is negative → subtracts → carves **alcoves** into walls and creates irregular surfaces

Parameters: ScaleXZ=20, ScaleY=15, amplitude ±0.35. This produces volumetric cave features impossible with 2D noise (which only creates height variation).

### Feature 3: Ceiling Pattern Prop Placement

Three prop layers use `placement: "ceiling"` — the first biome to activate this:
- `ceiling_stalactites` — zone-based stone/crystal formations hanging from the roof
- `crystal_ceiling` — Crystal zone (A) blue/purple/red hanging crystal formations
- `cave_roots` — rare hanging root systems from the surface above

Uses `Ceiling` pattern type (empty at position + solid above) with `TopDownOrder=false` scanner scanning upward from below Ceiling BaseHeight.

---

## Terrain

| Parameter | Value | Notes |
|-----------|-------|-------|
| `surface_materials` | `Rock_Stone`, `Rock_Shale` | FieldFunction zones create material patches |
| `sub_material` | `Rock_Slate` | |
| `deep_material` | `Rock_Stone` | |
| `spawn_platform` | `Rock_Sandstone` | Must not be in surface_materials |
| `noise_amplitude` | 0.15 | Gentle floor (cave floor is relatively flat) |
| `noise_scale` | 35 | |
| `noise_octaves` | 2 | |
| `boundary_type` | `textured` | Craggy 3D noise walls |
| `boundary_transition` | 0.15 | Steep cave walls |
| `boundary_density` | 6 | |
| `boundary_height` | 25 | Tall walls connecting floor to ceiling |
| `boundary_noise_scale` | 12 | Tight 3D noise for craggy surface |
| `boundary_noise_amp` | 0.5 | Aggressive irregularity |
| `signature_type` | `ceiling` | Enclosed cave architecture |
| `ceiling_y` | 110 | Base ceiling height (dome center) |
| `ceiling_edge_y` | 90 | Ceiling at arena edge (dome slopes down) |
| `cave_3d_noise_scale_xz` | 20 | 3D noise XZ period (pillar spacing) |
| `cave_3d_noise_scale_y` | 15 | 3D noise Y period |
| `cave_3d_noise_amp` | 0.35 | Bidirectional amplitude (pillars + alcoves) |

### Wall Strata

| Y Range | Material | Visual |
|---------|----------|--------|
| 78–80 | `Rock_Shale` | Dark shale band |
| 69–72 | `Rock_Slate` | Slate layer |
| 57–60 | `Rock_Basalt` | Deep basalt foundation |

---

## Atmosphere

| Parameter | Value |
|-----------|-------|
| `environment` | `Zone1_Underground` |
| `fog_distance` | [15, 45] — extremely close, oppressive darkness |
| `fog_color` | `#0a0a1a` — near-black with blue tint |
| `fog_density` | 0.85 |
| `particle_id` | `Dust_Cave_Flies` |
| `particle_color` | `#3060c0` — bioluminescent blue fireflies |
| `tint_colors` | `#1a2a4a`, `#0a1a30`, `#2a3a5a` |

---

## Three-Zone System (FieldFunction)

Zone seed: `"cavern_zones"`, scale: 80

| Zone | Noise Range | Character | Floor Props | Ceiling Props |
|------|------------|-----------|-------------|---------------|
| **A: Crystal Cavern** | [-1.0, -0.2] | Glowing blue/purple/cyan crystals, geodes | Basalt/Floor stalactites, Geodes Blue/Purple/Cyan/Pink | Basalt_Crystal_Blue/Purple/Red Ceiling |
| **B: Stone Depths** | [-0.2, 0.3] | Raw geological cave, neutral tones | Basalt/Shale/Hexagon formations, large rocks | Basalt/Stone Ceiling stalactites |
| **C: Fungal Garden** | [0.3, 1.0] | Bioluminescent mushroom grove | Mushroom_Large Green/Purple/Yellow | Basalt stalactites + Red crystal |

---

## WorldGen Prop Layers (12 total — 7 floor + 3 ceiling + 2 landmarks)

### Floor Props

| # | Layer | Props | Mesh Scale | Skip | Special |
|---|-------|-------|-----------|------|---------|
| 1 | `stalagmites` | Zone-based (Crystal/Stone/Mushroom floor formations) | 8×8 | 0.25 | FieldFunction zones |
| 2 | `crystal_deposits` | Zone A only: Geodes Blue/Cyan/Purple/Pink | 12×12 | 0.35 | Crystal zone |
| 3 | `mushroom_groves` | Zone C only: Mushroom_Large Green/Purple/Yellow | 10×10 | 0.30 | Fungal zone |
| 4 | `cave_rocks` | Quartzite, Quartzite/Moss, Shale, Slate, Calcite, Basalt | 10×10 | 0.35 | All zones |
| 5 | `quartzite_pillars` | Quartzite/Large, Quartzite/Moss_Large | 35×35 | 0.5 | Fixed, rare |
| 6 | `cave_floor_detail` | Stone/Small, Slate/Small, Calcite/Small, Shale/Small | 7×7 | 0.45 | Dense scatter |
| 7 | `mushroom_rings` | Mushroom_Rings | 40×40 | 0.5 | Fixed, rare magical |

### Ceiling Props (NEW — First biome to use ceiling placement)

| # | Layer | Props | Mesh Scale | Skip | Special |
|---|-------|-------|-----------|------|---------|
| 8 | `ceiling_stalactites` | Zone-based: Basalt/Stone/Crystal Ceiling | 7×7 | 0.20 | `placement: "ceiling"`, zones |
| 9 | `crystal_ceiling` | Zone A only: Basalt_Crystal_Blue/Purple/Red Ceiling | 10×10 | 0.35 | Crystal zone ceiling |
| 10 | `cave_roots` | Basalt/Ceiling, Stone/Ceiling (rare hanging formations) | 25×25 | 0.4 | Fixed spacing |

### Landmarks

| # | Layer | Props | Mesh Scale | Skip | Special |
|---|-------|-------|-----------|------|---------|
| 11 | `calcite_pillars` | Quartzite/Large, Quartzite/Moss_Large, Basalt/Large | 45×45 | 0.5 | Fixed, dramatic |
| 12 | **grass** | Moss_Block_Green, Moss_Green_Dark, Moss_Rug_Lime, Fern, Fern_Wet_Big | Column | — | FieldFunction density |

---

## Runtime Structures — RealmStructurePlacer (8 entries)

No faction structures (Goblins have no individual WorldGen-safe buildings). Geological features only:

| Path | Density | Min Radius | Description |
|------|---------|-----------|-------------|
| `Rock_Formations/Fossils/Small` | 2.5 | R35 | Small embedded fossils |
| `Rock_Formations/Hotsprings/Desert` | 1.0 | R35 | Geothermal vent |
| `Rock_Formations/Fossils/Large/Normal` | 1.5 | R55 | Large fossil formation |
| `Rock_Formations/Fossils/Large/Ruined` | 1.0 | R55 | Crumbling large fossil |
| `Rock_Formations/Fossils/Large/Normal` | 0.8 | R70 | Additional large fossil |
| `Rock_Formations/Hotsprings/Desert` | 0.8 | R70 | Deep geothermal |
| `Rock_Formations/Fossils/Gigantic/Normal` | 0.5 | R90 | Massive ancient fossil |
| `Rock_Formations/Fossils/Gigantic/Ruined` | 0.4 | R100 | Epic crumbling giant |

---

## Boss Camps — BossStructurePlacer (3 tiers)

Crystal geological lair (no faction camps):

| Tier | Center Pool | Satellites |
|------|------------|-----------|
| Small (0–55) | Fossils/Small, Hotsprings/Desert | Quartzite/Large, Basalt/Large, Stone/Large |
| Medium (55–90) | Fossils/Large Normal/Ruined, Hotsprings | Fossils/Small, Quartzite/Large, Basalt/Large, Hotsprings |
| Large (90+) | Fossils/Gigantic Normal/Ruined, Fossils/Large | Fossils/Small, Fossils/Large, Quartzite/Large, Hotsprings |

---

## Mob Pool (8 types + 1 boss)

| Mob ID | Weight | Combat Role | Thematic Fit |
|--------|--------|-------------|--------------|
| `Spider_Cave` | 20% | **Ambush trapper** — webs from ceiling, drops on player | Cave apex predator |
| `Raptor_Cave` | 18% | **Pack flanker** — fast, multiple angles in dark | Underground dinosaur |
| `Goblin_Miner` | 15% | **Pickaxe fighter** — faction presence | Mining the crystals |
| `Goblin_Scrapper` | 12% | **Basic melee** — faction grunt | Goblin workforce |
| `Goblin_Lobber` | 12% | **Ranged projectile** — bombs in tight spaces | Cave bombardier |
| `Molerat` | 10% | **Burrower** — emerges from ground | Cave native |
| `Rat` | 8% | **Swarm** — low damage, many | Vermin infestation |
| `Bat` | 5% | **Aerial** — flies in cave space | Cave ambience + annoyance |
| **Boss: Golem_Crystal_Earth** | — | **Earth elemental** — geological titan, IS the cave | Crystal colossus |

---

## Ground Cover (Column props)

| Block | Weight | Description |
|-------|--------|-------------|
| `Plant_Moss_Block_Green` | 30 | Standard cave moss |
| `Plant_Moss_Green_Dark` | 25 | Dark moss (deeper areas) |
| `Plant_Moss_Rug_Lime` | 15 | Bright lime patches (bioluminescent) |
| `Plant_Fern` | 10 | Cave ferns |
| `Plant_Fern_Wet_Big` | 5 | Wet fern near moisture |

**Density zones:** sparse=0.90 skip, moderate=0.55 skip, dense=0.20 skip

---

## Unique Features (First Used in Caverns)

| Feature | What's New | Technical |
|---------|-----------|-----------|
| **Cave ceiling** | Enclosed dome-shaped roof | `signature_type="ceiling"` + BaseHeight "Ceiling" density |
| **3D volumetric noise** | Natural pillars + alcoves | `SimplexNoise3D` bidirectional (±0.35 amp) |
| **Ceiling props** | Stalactites hanging from roof | `placement="ceiling"` + Ceiling pattern + upward scanner |
| **Underground atmosphere** | Near-total darkness + bioluminescence | Zone1_Underground + fog [15,45] + blue particles |
| **Moss ground cover** | Cave-specific organic floor | Plant_Moss_* blocks (never used in other biomes) |
| **Goblin faction mobs** | First goblin biome | Goblin_Miner + Scrapper + Lobber |
| **No faction structures** | Geological-only placement | Goblins have no safe individual structures |

---

## Architecture Decisions

### Why Golem_Crystal_Earth as Boss (Not Rex_Cave)

Rex_Cave is already the confirmed Jungle boss. Reusing it would blur biome identity. Golem_Crystal_Earth is an earth elemental that literally IS the cave coming alive — a crystal-encrusted geological titan. This creates a unique encounter (earth/crystal magic, ground pound, summon stalagmites) vs Jungle's purely physical Rex fight.

### Why No Faction Structures

Goblins don't have individual WorldGen-safe structures like Trork (Tent, Bonfire, Watchtower) or Outlander (Houses, Forts, Towers). All Goblin structures are compound dungeon layouts (`Goblin_Lair_*`, `Crypt_*`, `Slate_System_*`) that use PrefabSpawnerBlocks — guaranteed crash in WorldGen. The Goblin "presence" is purely through mob spawns (3 types: Miner, Scrapper, Lobber).

### Why ceiling_y=110 and ceiling_edge_y=90

- BaseHeight "Base" is at Y=64 (floor level)
- ceiling_y=110 means center ceiling is 46 blocks above floor (spacious cathedral)
- ceiling_edge_y=90 means edge ceiling is 26 blocks above floor (the dome slopes down)
- 3D noise (±0.35 amp ≈ ±7 blocks) creates local variation within this range
- Result: 19–53 blocks of vertical play space depending on location

### Why cave_3d_noise_amp=0.35

- Too low (0.1-0.2): barely visible bumps, no pillar formation
- Too high (0.5+): cave becomes impassable, props can't find ground
- 0.35: creates ~7-block pillars and alcoves — dramatic but navigable
- Combined with ceiling, pillars connect floor to ceiling where noise aligns

---

## Deployment Checklist

```bash
# 1. Regenerate biome files
python scripts/generate-realm-biomes.py --biome Caverns

# 2. Build (compiles Java + processes resources)
./gradlew clean build

# 3. Deploy (JAR + asset pack + configs to server)
bash scripts/deploy.sh

# 4. Restart Hytale server
```

## Testing Checklist

- [ ] Terrain generates (NOT void)
- [ ] **Ceiling visible** — enclosed cave, no open sky
- [ ] **Natural pillars** — solid columns connecting floor to ceiling (3D noise)
- [ ] **Stalactites on ceiling** — formations hanging from roof (ceiling props)
- [ ] Three zones visible (crystal blue / stone neutral / mushroom green)
- [ ] Dense floor formations (stalagmites, rocks)
- [ ] Mushroom groves in fungal zone
- [ ] Crystal geodes in crystal zone
- [ ] Moss ground cover everywhere
- [ ] Fossils appear on R35+ arenas (runtime placed)
- [ ] Fossil boss camp near Golem spawn
- [ ] Goblins spawn (Miner, Scrapper, Lobber)
- [ ] All 8 regular mob types spawn
- [ ] Golem_Crystal_Earth boss spawns
- [ ] Atmosphere correct (near-black fog, blue particles, claustrophobic)
- [ ] Wall strata visible in boundary (Shale/Slate/Basalt bands)
- [ ] Player spawn on solid ground (not in wall/pillar)
- [ ] No prop overlap issues (floor props don't spawn on pillars via scanner_max_y=8)
