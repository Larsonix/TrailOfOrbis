# Desert Biome — "Scorched Ruins"

Arid wasteland with ancient sandstone ruins. No living faction — fire undead and desert creatures inhabit crumbling monuments. The first biome to use the three-system architecture.

**Last updated:** April 20, 2026

---

## Terrain

| Parameter | Value |
|-----------|-------|
| `surface_materials` | `Soil_Sand_White`, `Soil_Sand_Red` (FieldFunction zones) |
| `sub_material` | `Rock_Sandstone` |
| `deep_material` | `Rock_Sandstone` |
| `noise_amplitude` | 0.50 (dramatic dunes — OK because scale=80 spreads them gently) |
| `noise_scale` | 80 |
| `noise_octaves` | 2 |
| `boundary_type` | `rising` |
| `boundary_transition` | 0.40 |
| `signature_type` | `sunken_basin` (amphitheater depression) |
| `path_material` | `Rock_Sandstone` (ancient caravan routes) |
| `wall_strata` | Sand_Red (70-72), Dirt (65-67), Stone (58-60) |

## Atmosphere

| Parameter | Value |
|-----------|-------|
| `environment` | `Env_Zone2_Deserts` |
| `fog_distance` | [40, 120] |
| `fog_color` | `#d8b878` (golden) |
| `color_filter` | `#f0d8a0` (warm tint) |
| `particle_id` | `Dust_Cave_Flies` (#d4b088 — sand dust) |

## WorldGen Prop Layers (7 natural)

| Layer | Key Props | Special |
|-------|-----------|---------|
| `vegetation` | Cacti (Flat/Full), Boab, Dry, Burnt trees | FieldFunction zones (cacti zone / dry woodland) |
| `rock_formations` | Sandstone Large/Small, Red, Quartzite | |
| `dry_scrub` | Bush/Arid, Arid_Red, Dead_Lavathorn | sink_into_ground |
| `desert_floor` | Driftwood/Dry, Sandstone/Small, Chalk, Shale | |
| `desert_arches` | Desert (9), Desert_Red (9), Savannah (5) arches | Fixed spacing |
| `desert_pillars` | Rock_Stone/Ash, Plains, Shale/Bare | Fixed spacing |
| **grass** | Grass_Sharp, Sharp_Wild | Very sparse (97%/85%/55% skip) |

## Runtime Structures — RealmStructurePlacer (17 entries)

| Tier | Radius | Structures |
|------|--------|-----------|
| Early | R35+ | Fossils/Small (×2), Hotsprings/Desert (×0.8), Sandstone/Tent (×2) |
| Mid | R45+ | Sandstone Camps (Grass/Red/White) |
| Advanced | R55+ | Fossils/Large (×2 types), Wells + Drywood |
| Deep | R70+ | Zone2 Outpost (×0.8), Sandstone/Normal, Oasis/Rock_Camp |
| End | R100+ | Fossils/Gigantic (×2 types), Treasure_Rooms/Sandstone |

## Boss Camps — BossStructurePlacer (3 tiers)

| Tier | Center Pool | Satellites |
|------|------------|-----------|
| Small (0–55) | Sandstone Tent/Normal | Sandstone Camps (3 colors) |
| Medium (55–90) | Zone2 Outpost, Oasis/Rock_Camp, Tent | Camps, Wells, Drywood |
| Large (90+) | Zone2 T1/T2 Outpost, Oasis/Rock_Camp | Full sandstone catalogue |

## Mob Pool

| Mob | Weight | Role |
|-----|--------|------|
| Skeleton_Burnt_Knight | 20% | Fire melee |
| Skeleton_Burnt_Gunner | 15% | Fire ranged |
| Skeleton_Sand_Archer | 15% | Desert ranged |
| Scorpion | 15% | Desert predator |
| Skeleton_Sand_Mage | 10% | Desert magic |
| Lizard_Sand | 10% | Swift reptile |
| Cactee | 8% | Animated cactus creature |
| Skeleton_Burnt_Wizard | 7% | Fire magic |
| **Boss: Skeleton_Burnt_Praetorian** | — | Fire summoner |
