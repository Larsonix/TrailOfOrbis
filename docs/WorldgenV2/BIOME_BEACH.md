# Beach Biome — "The Sunken Corsair"

A graduated tropical shoreline that tells the story of a sunken pirate civilization. The terrain slopes from a raised dry center down into a lowered coral shelf at the edges. Coral-encrusted Ocean City ruins emerge from the sand alongside wrecked ships. Skeleton pirates and marine creatures defend their drowned domain.

**Last updated:** April 21, 2026

---

## Identity

Once a thriving corsair port allied with the Slothian people, swallowed by rising seas. The pirates died but never left. Their bones patrol coral-crusted ruins, defending treasure they can no longer spend.

**Signature:** `tidal_shelf` — graduated 3-zone shoreline on a floating island surrounded by ocean.

**Primary Innovation:** First biome to combine **floating boundary** (Void pattern) + **fluid fill** (Swamp/Tundra pattern) = island in the ocean. No visible walls — open ocean in every direction. Undulating coral reef on the ocean floor with bioluminescent props via `placement="ocean_floor"` mode.

---

## Terrain

| Parameter | Value | Notes |
|-----------|-------|-------|
| `surface_materials` | `Soil_Sand_White`, `Soil_Sand_Red` | FieldFunction zones create white/red sand patches |
| `sub_material` | `Rock_Sandstone` | Compacted sand below surface |
| `deep_material` | `Rock_Stone` | Bedrock foundation |
| `spawn_platform` | `Soil_Dirt` | Must not be in surface_materials |
| `noise_amplitude` | 0.12 | Gentlest of any biome — beaches are flat |
| `noise_scale` | 50 | Broad gentle undulations (wide dunes) |
| `noise_octaves` | 1 | Single octave — smooth, no sharp features |
| `boundary_type` | `floating` | Terrain drops into ocean at edges — no visible walls |
| `boundary_transition` | 0.10 | Sharp island cliff edge |
| `boundary_density` | -4 | Negative = terrain drops (proven by Void biome) |
| `boundary_height` | 0 | No wall above water line |
| `fluid_material` | `Water_Source` | Ocean fills the void created by floating boundary |
| `fluid_bottom_y` | -30 | Water from Y=34 (30 blocks deep) |
| `fluid_top_y` | -1 | Water surface at Y=63 (1 block below island) |
| `signature_type` | `tidal_shelf` | Graduated 3-zone shoreline |
| `path_material` | `Rock_Sandstone` | Packed sand trails (old pirate routes) |

### Tidal Shelf — Graduated 3-Zone Shoreline

The CurveMapper creates 3 distinct elevation bands:

| Zone | Radius | Density Offset | Elevation Drop | Character |
|------|--------|---------------|----------------|-----------|
| **Dry Shore** | 0–40% | 0 | None (flat) | Palm groves, driftwood, pirate camps |
| **Wet Transition** | 40–70% | -0.08 | ~1.5 blocks lower | Tide pools, debris, sparse palms |
| **Coral Shelf** | 70–100% | -0.18 | ~3 blocks lower | Coral formations, shipwrecks |

### Wall Strata (4 layers — coastal geology)

Tightest band spacing of any biome — visible coastal cliff cross-section.

| Y Range | Material | Visual |
|---------|----------|--------|
| 72–73 | `Soil_Sand_White` | Top white sand |
| 69–71 | `Soil_Sand_Red` | Red iron-oxide band |
| 65–67 | `Rock_Sandstone` | Compacted ancient shore |
| 59–62 | `Rock_Stone` | Deep stone foundation |

---

## Atmosphere

| Parameter | Value | Notes |
|-----------|-------|-------|
| `environment` | `Env_Zone1_Plains` | Temperate — warm, not desert-hot |
| `fog_distance` | [80, 180] | **Furthest/clearest of ANY biome** — tropical clarity |
| `fog_color` | `#e0d0b0` | Warm golden sand haze |
| `fog_density` | 0.35 | **Lightest of any biome** — clear tropical air |
| `sky_top_color` | `#5cb8e8ff` | Vivid tropical blue |
| `sky_bottom_color` | `#f0e4c8ff` | Warm sandy horizon glow |
| `color_filter` | `#f8e8c0` | Golden tint over everything |
| `tint_colors` | `#F5DEB3`, `#E8D1A0`, `#FFF0C8`, `#DEC89A` | 4 warm sand tints |
| `particle_id` | (empty) | No particles — clean, bright, clear |
| `water_tint` | `#40a0c0` | Tropical turquoise |

**Design note:** The atmosphere is the BRIGHTEST and CLEAREST of any biome. Every other biome restricts visibility (fog, particles, tint). Beach does the opposite — maximum visibility, maximum color saturation. The danger is what you SEE coming, not what you can't see.

---

## Three-Zone System (FieldFunction)

All zone-based layers share `zone_seed="corsair_zones"` with `zone_noise_scale=80`.

| Zone | Noise Range | Character | Trees | Rocks |
|------|------------|-----------|-------|-------|
| **A: Dry Shore** | [-1.0, -0.15] | White sand, palm groves, driftwood | Palm_Green Stage 2–3 (**EXCLUSIVE**), Palm Stage 2–3 | Sandstone/White/Small, Chalk/Small |
| **B: Wet Transition** | [-0.15, 0.40] | Dark sand, tide pools, debris | Palm Stage 1–2 (sparse) | Calcite/Small, Grass/Small |
| **C: Coral Shelf** | [0.40, 1.0] | Coral, wrecks, submerged ruins | None (no trees in water zone) | Quartzite/Small, Quartzite/Moss_Small |

---

## WorldGen Prop Layers (11 total)

### Natural Vegetation (all arena sizes)

| # | Layer | Props | Scale | Skip | Special |
|---|-------|-------|-------|------|---------|
| 1 | `palm_trees` | Zone A: Palm_Green/Stage_2-3 + Palm/Stage_2-3. Zone B: Palm/Stage_1-2 sparse | 8x8 | 0.25 | FieldFunction zones |
| 2 | `driftwood` | Zone A: Driftwood/Redwood. Zone B: Redwood + Dry | 10x10 | 0.30 | FieldFunction zones |
| 3 | `coastal_bushes` | Bush/Cliff, Bush/Arid | 9x9 | 0.45 | sink_into_ground |
| 4 | `beach_rocks` | Zone A: Sandstone/White, Chalk. Zone B: Calcite, Grass. Zone C: Quartzite, Quartzite/Moss | 10x10 | 0.35 | FieldFunction zones |
| 5 | `beach_scatter` | Chalk/Small, Driftwood/Redwood, Calcite/Small | 6x6 | 0.35 | Dense ground detail |
| 6 | `coral_outcrops` | Zone C: Geodes Blue/Cyan/Green/White | 14x14 | 0.40 | Coral shelf zone only |

### Landmarks

| # | Layer | Props | Scale | Skip | Special |
|---|-------|-------|-------|------|---------|
| 7 | `coastal_arches` | Arches/Sandstone (**EXCLUSIVE** — 12 prefabs) | 50x50 | 0.50 | Fixed spacing |
| 8 | `beach_pillars` | Pillars/Rock_Stone/Plains, Shale/Bare/Small+Medium | 45x45 | 0.50 | Fixed spacing |
| 9 | `fallen_logs` | Driftwood/Redwood (**EXCLUSIVE**) | 15x15 | 0.40 | Fixed spacing |
| 10 | `tide_detail` | Bush/Cliff, Chalk/Small, Calcite/Small | 7x7 | 0.45 | Dense scatter |

### Ground Cover (Column props)

| Block | Weight | Description |
|-------|--------|-------------|
| `Plant_Grass_Sharp` | 15 | Wiry coastal grass |
| `Plant_Grass_Sharp_Wild` | 10 | Wild beach grass |

**Density zones:** sparse=0.95 skip, moderate=0.80 skip, dense=0.50 skip — **sparsest ground cover of any biome**. Beaches have almost no grass.

---

## Runtime Structures — RealmStructurePlacer (14 entries)

All City_Oceans and Shipwreck entries use `removeWaterlog=true`.

### Coral City Ruins (EXCLUSIVE — 62 prefabs total)

| Path | Density | Min Radius |
|------|---------|-----------|
| `City_Oceans/Buildings_Small/Coral` | 2.5 | R35 |
| `City_Oceans/Buildings_Small/Normal` | 1.5 | R45 |
| `City_Oceans/Buildings_Medium/Coral` | 1.5 | R55 |
| `City_Oceans/Buildings_Medium/Normal` | 1.0 | R70 |
| `City_Oceans/Buildings_Capital` | 0.3 | R100 |
| `City_Oceans/Buildings_Tower` | 0.3 | R100 |

### Shipwrecks (EXCLUSIVE — Cold is Tundra's)

| Path | Density | Min Radius |
|------|---------|-----------|
| `Shipwrecks/Tropical` | 1.5 | R35 |
| `Shipwrecks/Temperate` | 1.0 | R55 |
| `Ocean/Shipwrecks` | 0.5 | R70 |

### Beach Structures (EXCLUSIVE)

| Path | Density | Min Radius |
|------|---------|-----------|
| `Grasslands/Camps/Beach` | 2.0 | R35 |
| `Grasslands/Houses/Beach` | 1.0 | R45 |
| `Slothian/Water/Raft` | 0.8 | R45 |

### Geological

| Path | Density | Min Radius |
|------|---------|-----------|
| `Fossils/Small` | 1.5 | R35 |
| `Fossils/Large/Normal` | 0.5 | R70 |

---

## Boss Camps — BossStructurePlacer (3 tiers)

| Tier | Radius | Center Pool | Satellite Pool | Sats | Distance |
|------|--------|------------|----------------|------|----------|
| Small | 0–55 | Shipwrecks/Tropical, Camps/Beach | Buildings_Small/Coral, Fossils/Small, Sandstone/Large | 1–2 | 6–10 |
| Medium | 55–90 | Shipwrecks/Temperate, Buildings_Medium/Coral | Shipwrecks/Tropical, Buildings_Small/Coral, Camps/Beach, Fossils/Small | 2–4 | 10–18 |
| Large | 90+ | Buildings_Capital, Buildings_Tower, Shipwrecks/Temperate | Buildings_Medium/Coral, Buildings_Small/Coral, Shipwrecks/Tropical, Camps/Beach, Ocean/Shipwrecks, Fossils/Small | 4–7 | 12–22 |

---

## Mob Pool (ZERO overlap with any other biome)

| Mob | Weight | Role | Status |
|-----|--------|------|--------|
| Skeleton_Pirate_Striker | 25% | Cutlass melee DPS | Confirmed |
| Skeleton_Pirate_Gunner | 25% | Ranged pistol/rifle | Confirmed |
| Skeleton_Pirate_Captain | 15% | Heavy tank captain | Confirmed |
| Crab | 15% | Marine pincer bruiser | **UNTESTED** |
| Lobster | 10% | Heavy marine crusher | **UNTESTED** |
| Tortoise | 10% | Armored slow tank | **UNTESTED** |
| **Boss: Skeleton_Pirate_Captain** | — | Pirate lord (scaled-up captain) | Confirmed |

### Marine Creature Testing Strategy

Crab, Lobster, and Tortoise are ALL UNTESTED. They may be passive (removed by RealmPassiveNPCRemover) or unable to pathfind on land. The 3 pirate skeleton types form the guaranteed core (65% weight).

**Fallback if ALL marine creatures fail:** Increase pirates to 30/30/20, add `Skeleton` (base) at 20%.

---

## Unique Features (First Used in Beach)

| Feature | What's New |
|---------|-----------|
| **Floating + fluid** | First biome to combine floating boundary with water fill = island in the ocean |
| **Tidal Shelf terrain** | Graduated 3-zone shoreline on a floating island |
| **Undulating ocean floor** | SimplexNoise2D (±3 blocks) on dedicated BaseHeight CurveMapper creates reef mounds/trenches |
| **`placement="ocean_floor"`** | New prop placement mode: BlockSet matching sub/deep materials, MinY=-40, bypasses Empty Origin check |
| **Underwater coral reef** | 5 dense layers on the ocean floor (coral carpet, reef scatter, formations, mushroom coral, crystal accents) |
| **Palm_Green trees** | First and only use (15 prefabs) |
| **Driftwood/Redwood** | First and only use (10 prefabs) |
| **Arches/Sandstone** | First and only use (12 prefabs) |
| **City_Oceans structures** | First use of 62 coral city buildings |
| **Tropical/Temperate shipwrecks** | First use of warm-climate shipwrecks |
| **Marine creature mobs** | First use of Crab, Lobster, Tortoise |
| **Maximum visibility atmosphere** | Brightest/clearest — design through clarity |
| **Sparsest ground cover** | Intentional minimalism (beaches don't have grass) |

---

## Architecture Decisions

### Why Skeleton_Pirate_Captain as Boss (Not Scarak_Broodmother)

Scarak_Broodmother was a placeholder. It's the Caverns boss (Scarak hive theme). A pirate captain commanding pirate troops on pirate ruins is thematically perfect. Same pattern as Trork_Chieftain (Forest) and Golem_Guardian_Void (Void) — the faction leader as boss.

### Why ZERO Creature Overlap

The old pool had Crocodile (Jungle/Swamp), Snake_Cobra (Jungle), Scorpion (Desert). The redesigned pool achieves complete mob exclusivity — every mob you fight on Beach is something you fight ONLY on Beach.

### Why City_Oceans Buildings (Not Sandstone Camps)

Desert owns Sandstone camps. Using them on Beach would make it feel like "Desert with water." City_Oceans provides 62 unique coral-encrusted ocean buildings that have NEVER been used in any biome.

### Why noise_amplitude=0.12 (Lowest of Any Biome)

Beaches are flat. Combined with noise_scale=50 and octaves=1, this creates broad gentle undulations — the feel of rolling dunes. The tidal shelf signature handles elevation interest.

### Why No Fluid in Tidal Shelf

Adding water fluid would block mob pathfinding and make boss fights happen in water (untested, likely buggy). The "submerged" feeling comes from terrain depression + wet-looking props (coral, quartzite/moss rocks).

---

## Deployment

```bash
python scripts/generate-realm-biomes.py --biome BEACH
./gradlew clean build
bash scripts/deploy.sh
```

## Testing Checklist

- [ ] NOT void (terrain generates at all radius tiers)
- [ ] Tidal shelf gradient visible (3-zone slope)
- [ ] Palm_Green trees present (exclusive)
- [ ] Driftwood/Redwood present (exclusive)
- [ ] Sandstone arches visible (exclusive)
- [ ] Coral City ruins appear R35+ (waterlog removed)
- [ ] Shipwrecks appear R35+ (waterlog removed)
- [ ] Wall strata visible (4-layer coastal geology)
- [ ] All 3 Skeleton_Pirate types spawn
- [ ] Test marine creatures: Crab, Lobster, Tortoise
- [ ] Boss spawns with compound shipwreck camp
- [ ] Atmosphere correct (bright, clear, golden, max visibility)
- [ ] Ground cover very sparse (intentional)
- [ ] Server logs clean

---

## Related Files

- `scripts/generate-realm-biomes.py` — BiomeSpec definition
- `src/.../maps/spawning/RealmStructurePlacer.java` — BEACH structure pool
- `src/.../maps/spawning/BossStructurePlacer.java` — BEACH boss camp tiers
- `src/main/resources/config/realm-mobs.yml` — mob pool configuration
- `src/.../maps/core/RealmBiomeType.java` — BEACH enum (no changes needed)
- `docs/WorldgenV2/BIOME_ASSET_REFERENCE.md` — full asset inventory
- `docs/WorldgenV2/FACTION_CREATURE_DATABASE.md` — mob/structure database
