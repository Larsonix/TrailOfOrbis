# Jungle Biome — "The Feran Wilds"

Complete technical reference for the jungle realm biome. Covers terrain, props, structures, mobs, atmosphere, and architecture decisions.

**Last updated:** April 20, 2026

---

## Identity

A suffocating, multi-layered jungle with three distinct FieldFunction zones. Ancient Feran beast-tribe ruins stand as environmental storytelling — their civilization consumed by the living jungle. The creatures that destroyed them now rule.

**Signature:** `elevated_center` — sacred Feran mound at the heart of the arena.

---

## Terrain

| Parameter | Value | Notes |
|-----------|-------|-------|
| `surface_materials` | `Soil_Grass_Full`, `Soil_Mud` | FieldFunction zones create muddy/grassy terrain patches |
| `sub_material` | `Soil_Mud` | |
| `deep_material` | `Rock_Stone` | |
| `spawn_platform` | `Rock_Stone` | Must not be in surface_materials |
| `noise_amplitude` | 0.30 | Reduced from 0.50 — prevents structures floating on rugged peaks |
| `noise_scale` | 20 | Feature spacing in blocks |
| `noise_octaves` | 3 | Detail layers |
| `noise_persistence` | 0.40 | Softened high-frequency bumps |
| `boundary_type` | `rising` | Natural hillside walls |
| `boundary_transition` | 0.25 | Moderately steep |
| `boundary_density` | 5 | |
| `boundary_height` | 15 | |
| `path_material` | `Soil_Dirt` | Beaten earth trails (Feran patrol routes) |
| `path_noise_scale` | 35 | |
| `path_width` | 0.12 | |

### Wall Strata

| Y Range | Material | Visual |
|---------|----------|--------|
| 71–73 | `Soil_Mud` | Exposed root/mud layer |
| 63–66 | `Rock_Basalt` | Dark rock band |
| 55–57 | `Rock_Stone` | Deep stone foundation |

---

## Atmosphere

| Parameter | Value |
|-----------|-------|
| `environment` | `Env_Zone1_Plains` |
| `fog_distance` | [-35, 60] — very close, claustrophobic |
| `fog_color` | `#1e4a1a` — deep saturated green |
| `fog_density` | 0.75 |
| `sky_top_color` | `#2a5a20ff` — dark canopy overhead |
| `sky_bottom_color` | `#0a2a05ff` — nearly black at horizon |
| `particle_id` | `Dust_Cave_Flies` |
| `particle_color` | `#30a020` — green insects |
| `tint_colors` | `#006400`, `#004000`, `#007800`, `#005500` |

---

## Three-Zone System (FieldFunction)

All zone-based layers share `zone_seed="jungle_zones"` with `zone_noise_scale=70` for coherent transitions.

| Zone | Noise Range | Character | Trees | Rocks | Flowers |
|------|------------|-----------|-------|-------|---------|
| **A: Bamboo Thicket** | [-1.0, -0.2] | Dense vertical bamboo stands, earthy floor | Bamboo Stage 1–3 | Grass/Small, Stone/Small, Quartzite/Small | — |
| **B: Ancient Canopy** | [-0.2, 0.4] | Massive spreading trees, rich understory | Banyan 3, Wisteria 2–3, Oak 4, Camphor 3 | Jungle rocks (6 variants), Grass/Small, Quartzite/Large | 6 flower colors (Auburn, Blue, Orange, Pink, Purple, Red) |
| **C: Bioluminescent Grove** | [0.4, 1.0] | Sparse canopy, crystal geodes, glowing coral | Wisteria 1, Willow 2, Palm 3 | Geodes (Green, Purple, Blue, Cyan, Pink) | Coral/Glow, Blue flowers, Purple flowers |

---

## WorldGen Prop Layers (13 at R130)

All layers are natural environment only. Faction structures are placed at runtime via RealmStructurePlacer.

### Natural Vegetation (all arena sizes)

| # | Layer | Props | Mesh Scale | Skip | Max Occ | Special |
|---|-------|-------|-----------|------|---------|---------|
| 1 | `trees` | Zone-based (see table above) | 5×5 | 0.15 | 0.35 | FieldFunction zones |
| 2 | `understory_trees` | Bamboo/0, Wisteria/1, Banyan/1, Palm/2 | 7×7 | 0.3 | 0.25 | Mid-layer |
| 3 | `undergrowth` | Bush/Jungle, Bush/Lush, Bush/Green, Bush/Brambles | 6×6 | 0.25 | 0.25 | sink_into_ground, maxY=8 |
| 4 | `fern_carpet` | Ferns/Large, Ferns/Small, Ferns/Island | 5×5 | 0.25 | 0.30 | sink_into_ground, maxY=8 |
| 5 | `jungle_flowers` | Zone-based flowers + coral (see table above) | 8×8 | 0.4 | 0.20 | FieldFunction zones |
| 6 | `mossy_logs` | Logs/Oak/Moss, Beech/Moss, Birch/Moss, Aspen/Moss, Oak/Mushroom_Cap_Brown, Oak/Mushroom_Cap_Red | 14×14 | 0.35 | 0.18 | **NEW prop category**, fixed spacing, maxY=8 |
| 7 | `jungle_rocks` | Zone-based rocks (see table above) | 12×12 | 0.4 | 0.25 | FieldFunction zones |
| 8 | `mushroom_groves` | Mushroom_Large Green/Purple/Yellow Stage 1+3 | 12×12 | 0.4 | 0.20 | |
| 9 | `vines` | Vines/Green, Vines/Green_Hanging | 10×10 | 0.4 | 0.18 | **First biome to use vine prefabs** |
| 10 | `mushroom_rings` | Mushroom_Rings | 40×40 | 0.5 | 0.05 | Rare, fixed spacing |

### Landmarks (all arena sizes)

| # | Layer | Props | Mesh Scale | Skip | Max Occ | Special |
|---|-------|-------|-----------|------|---------|---------|
| 11 | `jungle_arches` | Arches/Flower (10 prefabs!), Arches/Hedera, Arches/Forest | 50×50 | 0.5 | 0.05 | **First use of Flower + Hedera arches** |
| 12 | `jungle_pillars` | Pillars/Jungle (3 variants) | 40×40 | 0.5 | 0.08 | **First use of jungle pillars** |

### Ground Cover (Column prop — grass layer)

| Block | Weight | Description |
|-------|--------|-------------|
| `Plant_Grass_Jungle` | 25 | Native jungle grass |
| `Plant_Grass_Jungle_Short` | 20 | Short variant |
| `Plant_Grass_Jungle_Tall` | 15 | Tall variant |
| `Plant_Fern_Jungle` | 10 | Jungle fern |
| `Plant_Grass_Lush_Short` | 10 | Lush grass accent |
| `Plant_Flower_Orchid_Purple` | 2 | Rare orchid |
| `Plant_Flower_Tall_Pink` | 1 | Rare tall flower |
| `Plant_Flower_Tall_Purple` | 1 | Rare tall flower |

**Density zones:** sparse=0.85 skip, moderate=0.40 skip, dense=0.10 skip — the DENSEST ground cover of any biome.

---

## Runtime Structures — RealmStructurePlacer (26 entries)

All faction AND monument structures are placed at runtime for proper collision prevention via `StructureBoundsRegistry`. This is the correct architecture — WorldGen has no cross-layer collision detection.

### Ancient Slothian Ruins (12 entries)

| Path | Density | Min Radius | Description |
|------|---------|-----------|-------------|
| `Monuments/Incidental/Slothian/Land/Shrine` | 1.5 | R35 | Ancient jungle shrine |
| `Monuments/Incidental/Slothian/Land/Well` | 1.0 | R35 | Overgrown well |
| `Monuments/Incidental/Slothian/Land/Hunting` | 1.5 | R45 | Abandoned hunting camp |
| `Monuments/Incidental/Slothian/Land/Merchant` | 0.8 | R45 | Ruined market stall |
| `Monuments/Incidental/Slothian/Land/Temple` | 0.8 | R55 | Ancient temple |
| `Monuments/Incidental/Slothian/Biome/Trees/Banyan` | 1.0 | R55 | Decorated sacred banyan |
| `Monuments/Incidental/Slothian/Biome/Trees/Jungle` | 0.8 | R55 | Sacred grove |
| `Monuments/Incidental/Slothian/Biome/Rock_Pillar` | 1.0 | R70 | Carved standing stone |
| `Monuments/Incidental/Slothian/Biome/Trees/Crystal` | 0.5 | R70 | Mystical crystal tree |
| `Monuments/Incidental/Quartzite/Ruins` | 0.5 | R70 | Pre-Feran stonework ruins |
| `Monuments/Unique/Mage_Towers/Quartzite/Tier_2` | 0.3 | R90 | Ancient mage tower |
| `Monuments/Unique/Mage_Towers/Quartzite/Tier_3` | 0.2 | R100 | Grand mage tower |

### Feran Faction Structures (14 entries)

Progressive territory discovery — same radius-gating pattern as Trork (Forest) and Outlander (Tundra).

| Tier | Path | Density | Min Radius | Description |
|------|------|---------|-----------|-------------|
| **Boundary** | `Npc/Feran/Tier1/Corners` | 2.5 | R35 | Wall corner markers (6 variants) |
| **Boundary** | `Npc/Feran/Tier1/Straight/Entrances` | 0.8 | R35 | Camp entrance gate |
| **Scout** | `Npc/Feran/Tier1/Chieftain` | 0.8 | R45 | Small chieftain hut |
| **Scout** | `Npc/Feran/Tier1/Straight/Normal` | 1.5 | R45 | Wall segments (2 variants) |
| **Perimeter** | `Npc/Feran/Tier2/Wall` | 1.5 | R55 | Defensive walls (4 variants) |
| **Perimeter** | `Npc/Feran/Tier2/Entrance` | 0.8 | R55 | Tier 2 entrance |
| **Village** | `Npc/Feran/Tier2/Hut` | 2.0 | R70 | Village huts (5 variants) |
| **Village** | `Npc/Feran/Tier2/Chieftain` | 0.3 | R70 | Village chieftain hall |
| **Fortress** | `Npc/Feran/Tier3/Base` | 0.5 | R100 | Fortified platform |
| **Fortress** | `Npc/Feran/Tier3/Chieftain` | 0.3 | R100 | Elite chieftain structure |
| **Fortress** | `Npc/Feran/Tier3/Huts` | 0.8 | R100 | Elite huts |
| **Fortress** | `Npc/Feran/Tier3/Walls` | 0.8 | R100 | Fortress walls |

**Note:** Feran NPCs CANNOT be spawned via NPCPlugin (neutral faction — `spawnEntity()` returns null). Structures are environmental storytelling only. See `docs/reference/confirmed-dead-approaches.md`.

---

## Boss Structure

**None.** The jungle boss (Rex_Cave) is a massive dinosaur that spawns in the open jungle. It's too large for faction camps and doesn't belong inside a structure. No JUNGLE entry exists in `BossStructurePlacer.BOSS_CAMPS`.

---

## Mob Pool (10 types + 1 boss)

All mob IDs are confirmed spawnable via `NPCPlugin.spawnEntity()`. Only `Creature/*` and `Intelligent/Aggressive/*` categories work — `Intelligent/Neutral/*` returns null.

### Regular Mobs

| Mob ID | Weight | Category | NPC Path | Combat Role |
|--------|--------|----------|----------|-------------|
| `Spider` | 18% | Creature/Vermin | Creature/Vermin/Spider.json | **Trapper** — web ambush from canopy, slows player |
| `Crocodile` | 15% | Creature/Reptile | Creature/Reptile/Crocodile.json | **Assassin** — burst charge from undergrowth |
| `Wolf_Black` | 15% | Creature/Mammal | Creature/Mammal/Wolf_Black.json | **Pack flanker** — multiple angles, fast (exclusive) |
| `Raptor_Cave` | 14% | Creature/Reptile | Creature/Reptile/Raptor_Cave.json | **Harasser** — hit-and-run, relentless packs |
| `Snapdragon` | 10% | Creature/Mythic | Creature/Mythic/Snapdragon.json | **Plant tank** — massive, devastating (exclusive) |
| `Snake_Cobra` | 10% | Creature/Vermin | Creature/Vermin/Snake_Cobra.json | **DoT** — venomous, poison stacking |
| `Fen_Stalker` | 8% | Creature/Mythic | Creature/Mythic/Fen_Stalker.json | **Mythic horror** — stalks through vegetation |
| `Toad_Rhino` | 5% | Creature/Reptile | Creature/Reptile/Toad_Rhino.json | **Brute** — massive charge and stomp |
| `Snake_Marsh` | 5% | Creature/Vermin | Creature/Vermin/Snake_Marsh.json | **Swamp venom** — second snake variety |

### Boss

| Mob ID | Category | NPC Path | Combat Role |
|--------|----------|----------|-------------|
| `Rex_Cave` | Creature/Reptile | Creature/Reptile/Rex_Cave.json | **Apex predator** — massive dinosaur, devastating bite. Spawns in open jungle (no camp structure). |

### Exclusive Mobs (not used in any other biome)

- `Wolf_Black` — only in Jungle
- `Snapdragon` — only in Jungle (boss-tier as regular mob)

### Confirmed Dead Mob Types (DO NOT USE)

These pass `getIndex()` but `spawnEntity()` returns null — neutral faction limitation:

| Mob ID | Category | Why Dead |
|--------|----------|---------|
| `Feran_Longtooth` | Intelligent/Neutral/Feran | Neutral faction |
| `Feran_Sharptooth` | Intelligent/Neutral/Feran | Neutral faction |
| `Feran_Windwalker` | Intelligent/Neutral/Feran | Neutral faction |
| `Feran_Burrower` | Intelligent/Neutral/Feran | Neutral faction |
| `Feran_Cub` | Intelligent/Neutral/Feran | Neutral faction |
| `Bramblekin` | Intelligent/Neutral | Neutral faction |
| `Bramblekin_Shaman` | Intelligent/Neutral | Neutral faction |
| `Kweebec_Razorleaf` | Intelligent/Neutral/Kweebec | Neutral faction |
| `Hedera` | Intelligent/Aggressive | Passes getIndex but spawnEntity returns null — possible conflict with prefab-spawned instances (`RealmPassiveNPCRemover` removes it) |

**Rule:** Only mobs under `Creature/*` or `Intelligent/Aggressive/*` (Trork, Goblin, Outlander, Skeleton, etc.) are spawnable. All `Intelligent/Neutral/*` and `Intelligent/Passive/*` are dead.

---

## Unique Features (First Used in Jungle)

| Feature | What's New | Prefab Paths |
|---------|-----------|-------------|
| **Mossy Fallen Logs** | New prop category — rotting logs on jungle floor | `Trees/Logs/Oak/Moss`, `Beech/Moss`, `Birch/Moss`, `Aspen/Moss`, `Oak/Mushroom_Cap_Brown`, `Oak/Mushroom_Cap_Red` |
| **Flower Arches** | New arch type — organic stone with flowering vegetation | `Rock_Formations/Arches/Flower` (10 prefabs) |
| **Hedera Arches** | New arch type — ivy/vine-covered stone arches | `Rock_Formations/Arches/Hedera` |
| **Vine Prefabs** | First biome to place vine prefabs | `Plants/Vines/Green`, `Plants/Vines/Green_Hanging` |
| **Jungle Rocks** | Dedicated jungle rock formations | `Rock_Formations/Rocks/Jungle` (6 variants) |
| **Jungle Pillars** | Dedicated jungle stone pillars | `Rock_Formations/Pillars/Jungle` (3 variants) |
| **Native Jungle Grass** | Jungle-specific ground cover blocks | `Plant_Grass_Jungle`, `Plant_Grass_Jungle_Short`, `Plant_Grass_Jungle_Tall`, `Plant_Fern_Jungle` |
| **Dual Surface Materials** | FieldFunction terrain zones (grass/mud) | `Soil_Grass_Full` + `Soil_Mud` via MaterialProvider |
| **Open Boss Spawn** | Boss spawns without camp structure | No BOSS_CAMPS entry — Rex roams free |

---

## Architecture Decisions

### Why Faction Structures Are Runtime (Not WorldGen)

WorldGen V2's prop layers place independently — no cross-layer collision detection. Two structure layers can place prefabs at the same XZ coordinate. Runtime placement via `RealmStructurePlacer` uses `StructureBoundsRegistry` with:
- 2-block safety margin on all registrations
- `TerrainUtils.isAreaOccupied()` block scanning
- `TerrainUtils.findStructureGroundLevel()` multi-point ground sampling (3×3 grid)
- 12 retry attempts per structure

This pattern applies to ALL biomes (Forest/Trork, Tundra/Outlander, Jungle/Feran).

### Why No BossStructurePlacer Entry

Rex_Cave is a massive dinosaur. Placing it inside a Feran camp (center + satellites + dirt paths) would:
1. Look ridiculous — a dinosaur inside a wooden hut
2. Clip through structures due to its size
3. Contradict the narrative — Rex is a wild apex predator, not a faction creature

The Feran structures serve as environmental discovery. The Rex is the jungle's natural apex.

### Why Neutral NPCs Can't Spawn

Hytale's `NPCPlugin.spawnEntity()` contains internal validation that rejects spawning for NPCs classified under `Intelligent/Neutral/*`. The role files exist, `getIndex()` returns valid indices, but `spawnEntity()` returns null. This is a hard engine limitation — no API workaround exists. All Feran, Kweebec, Bramblekin, and similar "friendly" NPCs are affected. Only `Creature/*` and `Intelligent/Aggressive/*` spawn successfully.

### Why Terrain Amplitude Is 0.30 (Not 0.50)

Original jungle had `noise_amplitude=0.50` with `noise_scale=15` and `octaves=3` — creating ~7-block height swings over 15 blocks. Structures placed on local peaks floated visibly above nearby valleys. Reduced to 0.30 amplitude + scale 20 + persistence 0.40. Still rugged and interesting, but structures sit naturally. For comparison: Forest=0.20, Desert=0.50 (but scale=80 so spread gently).

---

## Deployment Checklist

```bash
# 1. Regenerate biome files
python scripts/generate-realm-biomes.py --biome JUNGLE

# 2. Build (compiles Java + processes resources)
./gradlew clean build

# 3. Deploy (JAR + asset pack + configs to server)
bash scripts/deploy.sh

# 4. Restart Hytale server
```

## Testing Checklist

- [ ] Terrain generates (NOT void)
- [ ] Three vegetation zones visible (bamboo / canopy / crystal grove)
- [ ] Dense ground cover everywhere (jungle grass)
- [ ] Mossy fallen logs on floor
- [ ] Flower and Hedera arches visible as landmarks
- [ ] Vine prefabs present
- [ ] Feran structures appear on R45+ arenas (runtime placed)
- [ ] Slothian shrines/temples appear (runtime placed)
- [ ] No structure overlap (check bounds registry in logs)
- [ ] Structures grounded properly (multi-point sampling)
- [ ] Rex boss spawns in open jungle (no camp)
- [ ] All 9 regular mob types spawn (check logs for "Spawn returned null")
- [ ] Mob count reaches target (check "Initial wave complete" log)
- [ ] Atmosphere correct (dense green fog, close visibility, insects)
- [ ] Mud terrain patches visible (FieldFunction material zones)
- [ ] Beaten earth trails visible (path_material)
- [ ] Wall strata visible in arena boundary
