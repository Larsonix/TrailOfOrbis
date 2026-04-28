# Forest Biome — "Trork Tribal Territory"

Temperate forest with Trork faction occupation. The most balanced biome — moderate terrain, clear sight lines, organized enemy structures.

**Last updated:** April 20, 2026

---

## Terrain

| Parameter | Value |
|-----------|-------|
| `surface_materials` | `Soil_Grass_Full` |
| `sub_material` | `Soil_Dirt` |
| `deep_material` | `Rock_Stone` |
| `noise_amplitude` | 0.20 (smooth rolling hills) |
| `noise_scale` | 30 |
| `noise_octaves` | 2 |
| `boundary_type` | `rising` |
| `signature_type` | `elevated_center` |

## Atmosphere

| Parameter | Value |
|-----------|-------|
| `environment` | `Env_Zone1_Plains` |
| `fog_distance` | [50, 100] |
| `fog_color` | `#a8c8a0` |
| `particle_id` | `Dust_Cave_Flies` (#90c090) |

## WorldGen Prop Layers (9 natural)

| Layer | Key Props | Special |
|-------|-----------|---------|
| `trees` | Oak 3-4, Beech 3, Maple 3, Cedar 3, Birch 2, Ash 2-3 | FieldFunction zones (clearings → transition → dense) |
| `undergrowth` | Bush/Cliff | sink_into_ground |
| `ground_cover` | Ferns/Small, Bush/Green, Bush/Lush | sink_into_ground |
| `rocks` | Stone, Grass, Slate rocks | |
| `forest_floor` | Mushroom_Large/Green, Grass rocks | |
| `fallen_logs` | Oak/Maple/Autumn stumps | Fixed spacing |
| `mushroom_rings` | Mushroom_Rings | Rare |
| `rock_landmarks` | Forest Arches, Stone Pillars (Oak, Birch) | Fixed spacing |
| **grass** | Lush Short/Tall, Sharp, Wild + 4 flower colors | Column, FieldFunction density |

## Runtime Structures — RealmStructurePlacer (21 entries)

| Tier | Radius | Structures |
|------|--------|-----------|
| Boundary | R35+ | Warning totems (×2.0) |
| Scout | R45+ | Bonfire (×1.5), Fireplace (×1.0), Trap (×1.0), Tent (×1.5), T1 Watchtower (×1.0) |
| Perimeter | R55+ | T1 Watchtower (×1.0), T2 Watchtower (×0.5) |
| Operations | R70+ | Warehouse, T2 Store/Watchtower, Misc/Large, Lumber resources (×3 types), Rock/Stone, Burrow |
| Fortress | R100+ | T3 Watchtower (×0.8), T3 Store (×0.5), Misc/Large (×1.0) |

## Boss Camps — BossStructurePlacer (3 tiers)

| Tier | Radius | Center Pool | Satellites |
|------|--------|------------|-----------|
| Small | 0–55 | Tent, T1 Encampment/Hunter | Bonfire, Fireplace, Trap |
| Medium | 55–90 | T2 Encampment/Lumber, Warehouse, T2 Store | Tent, Bonfire, Fireplace, T1/T2 Watchtower, Trap, Warning |
| Large | 90+ | T3 Encampment/Castle, Misc/Large, T3 Watchtower/Store | Full satellite pool including Burrow |

## Mob Pool

| Mob | Weight | Role |
|-----|--------|------|
| Trork_Warrior | 25% | Balanced melee |
| Trork_Brawler | 20% | Heavy tank |
| Trork_Guard | 20% | Standard soldier |
| Trork_Hunter | 20% | Ranged bow |
| Trork_Shaman | 10% | Magic/heals |
| Trork_Mauler | 5% | Slow hard-hitter |
| **Boss: Trork_Chieftain** | — | Faction leader |
