# Tundra Biome — "Frozen Wastes"

Frozen landscape with Outlander frost-tribe and Yeti occupation. Variable-depth snow drifts (NoiseThickness), frozen lake signature, and ice-themed structures.

**Last updated:** April 20, 2026

---

## Terrain

| Parameter | Value |
|-----------|-------|
| `surface_materials` | `Soil_Snow` |
| `sub_material` | `Rock_Ice` |
| `deep_material` | `Rock_Stone` |
| `noise_amplitude` | 0.15 (gentle rolling snowfields) |
| `noise_scale` | 40 |
| `noise_octaves` | 2 |
| `boundary_type` | `rising` |
| `boundary_transition` | 0.20 (steeper walls) |
| `boundary_density` | 5 |
| `boundary_height` | 15 |
| `signature_type` | `frozen_lake` (Water_Source, Y -6 to -2) |
| `path_material` | `Rock_Ice` (frozen trails) |
| `wall_strata` | Snow (70-72), Ice (64-67), Stone (55-58) |
| `snow_noise_scale` | 30 (NoiseThickness: 1–4 block variable snow depth) |

## Atmosphere

| Parameter | Value |
|-----------|-------|
| `environment` | `Env_Zone3_Tundra` |
| `fog_distance` | [30, 90] |
| `fog_color` | `#c0d8f0` (icy blue) |
| `color_filter` | `#c8e0f8` (cold tint) |
| `particle_id` | `Dust_Cave_Flies` (#d0e0f0 — snowflakes) |

## WorldGen Prop Layers (8 natural)

| Layer | Key Props | Special |
|-------|-----------|---------|
| `trees` | Fir_Snow Stage 1–3 | FieldFunction zones (wind clearings → sparse → dense) |
| `icebergs` | Ice_Formations/Icebergs | Rare landmark, fixed spacing |
| `ice_rocks` | Frozenstone, Frozenstone/Snowy, Basalt/Snowy, Basalt/Tundra, Shale/Snowy | |
| `ice_crystals` | Geodes/White, Cyan, Blue | Decorative |
| `winter_ground` | Bush/Winter, Frozenstone/Small, Calcite/Small | sink_into_ground |
| `tundra_arches` | Arches/Snowy, Arches/Tundra | Fixed spacing |
| `tundra_pillars` | Pillars/Snowy | Fixed spacing |
| **grass** | Grass_Sharp, Sharp_Wild | Very sparse (96%/82%/50% skip) |

## Runtime Structures — RealmStructurePlacer (23 entries)

| Tier | Radius | Structures |
|------|--------|-----------|
| Ruins | R35+ | Shale/Ruins/Frozen (×1.5) |
| Ruins | R55+ | Shipwrecks/Cold (×0.5, waterlog removal) |
| Ruins | R70+ | Temple/Portal/Snowlands (×0.3) |
| Ruins | R100+ | Outlander_Temple monuments (×0.3 each, 2 types) |
| Boundary | R35+ | Spikes (×2.5), Totems (×0.8) |
| Outposts | R45+ | Braziers (×1.0), Houses/T0 (×2.0), Yeti/Camps (×1.5), Misc (×1.0) |
| Perimeter | R55+ | Towers/T1 (×1.0), Forts/T1 (×0.8) |
| Settlement | R70+ | Houses T1-2, Forts/T2, Towers/T2, Boats/Large (waterlog removal) |
| Stronghold | R100+ | Houses/T3 (×0.8), Forts/T3 (×0.5), Towers/T3 (×0.8), Gates/T3 (×0.3) |

## Boss Camps — BossStructurePlacer (3 tiers)

| Tier | Center Pool | Satellites |
|------|------------|-----------|
| Small (0–55) | Yeti/Camps, Houses/T0 | Spikes, Braziers, Totems |
| Medium (55–90) | Forts/T1-2, Yeti/Camps | Houses T0-1, Towers/T1, Spikes, Braziers, Misc |
| Large (90+) | Camps/T3/Base, Forts/T3, Houses/T3 | Houses T1-2, Towers T1-2, Forts/T1, Spikes, Braziers, Misc, Totems |

## Mob Pool

| Mob | Weight | Role |
|-----|--------|------|
| Skeleton_Frost_Knight | 25% | Ice melee |
| Skeleton_Frost_Archer | 20% | Frost arrows |
| Bear_Polar | 15% | Arctic tank |
| Skeleton_Frost_Scout | 15% | Fast attacker |
| Skeleton_Frost_Mage | 15% | Ice spells |
| Wolf_White | 10% | Pack hunter |
| **Boss: Yeti** | — | Towering ice giant |

## Unique Features

- **NoiseThickness snow** — variable-depth snow layer (1–4 blocks) creating organic wind-sculpted drifts
- **Frozen lake** signature — central water body
- **Waterlog removal** on Shipwrecks/Cold and Boats/Large (water-designed prefabs placed on land/ice)
- **Outlanders are ICE-themed** — structures spawn snow, ice, and boats (confirmed in-game April 2026)
