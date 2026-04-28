# Volcano Biome — "Infernal Wasteland"

Craggy volcanic caldera with lava moat. No living faction — geological progression (fossils, hotsprings, crystal deposits) inhabited by fire creatures. Three-zone FieldFunction: Cooled / Active / Infernal.

**Last updated:** April 20, 2026

---

## Terrain

| Parameter | Value |
|-----------|-------|
| `surface_materials` | `Rock_Volcanic` |
| `sub_material` | `Rock_Basalt` |
| `deep_material` | `Rock_Volcanic` |
| `spawn_platform` | `Rock_Basalt` |
| `noise_amplitude` | 0.25 |
| `noise_scale` | 25 |
| `noise_octaves` | 3 |
| `noise_persistence` | 0.4 |
| `boundary_type` | `textured` (craggy 3D noise walls) |
| `boundary_transition` | 0.10 (cliff-like) |
| `boundary_density` | 6 |
| `boundary_height` | 25 (tall walls) |
| `boundary_noise_scale` | 15 |
| `boundary_noise_amp` | 0.4 |
| `signature_type` | `lava_moat` (Fluid_Lava, Y -10 to -2) |
| `wall_strata` | Basalt (82-85), Volcanic (72-75) |
| `screen_effect` | `ScreenEffects/Fire.png` (#ffffff0d) |

## Atmosphere

| Parameter | Value |
|-----------|-------|
| `environment` | `Env_Zone4_Burning_Sands` |
| `fog_distance` | [-40, 60] (oppressive) |
| `fog_color` | `#4a1010` (deep red) |
| `color_filter` | `#ff8060` (fire tint) |
| `particle_id` | `Ash` (#abb3b4) |

## Three-Zone System (FieldFunction)

Zone seed: `volcano_zones`, scale: 80.

| Zone | Noise Range | Character |
|------|------------|-----------|
| **Cooled** | [-1.0, -0.2] | Burnt trees survive, lavathorn, volcanic/basalt rocks |
| **Active** | [-0.2, 0.3] | Basalt hexagon columns, spiked volcanic, dead lavathorn |
| **Infernal** | [0.3, 1.0] | Volcanic spikes, crystal deposits (geodes), stalactites |

## WorldGen Prop Layers (9)

| Layer | Key Props | Special |
|-------|-----------|---------|
| `volcanic_formations` | Zone-based: Volcanic, Basalt, Hexagon, Spiked | FieldFunction |
| `scorched_vegetation` | Zone-based: Burnt trees, Lavathorn, Twisted_Wood/Fire | FieldFunction |
| `undergrowth` | Dead_Lavathorn, Arid, Arid_Red | sink_into_ground |
| `ground_rocks` | Basalt/Small, Volcanic/Spiked/Small, Shale/Small | Dense floor detail |
| `crystal_deposits` | R35+: Zone-based Stalactites/Floor, Geodes Pink/Purple/Blue/Cyan | FieldFunction |
| `volcanic_arches` | R45+: Savannah arches | Fixed spacing |
| `basalt_columns` | R55+: Basalt/Hexagon, Basalt/Large | Fixed spacing |
| `obsidian_landmarks` | R70+: Volcanic/Spiked/Large, Volcanic/Large | Rare |
| `fire_accents` | R90+: Twisted_Wood/Fire | Rare dramatic |

**Ground cover:** Grass_Sharp, Sharp_Wild — extremely sparse (97%/92%/80% skip).

## Runtime Structures — RealmStructurePlacer (6 entries)

No faction — geological features only:

| Path | Density | Min Radius |
|------|---------|-----------|
| Hotsprings/Desert | 1.5 | R35 |
| Fossils/Small | 2.0 | R35 |
| Fossils/Large/Normal | 1.0 | R55 |
| Fossils/Large/Ruined | 0.8 | R55 |
| Fossils/Gigantic/Normal | 0.5 | R90 |
| Fossils/Gigantic/Ruined | 0.5 | R100 |

## Boss Camps — BossStructurePlacer (3 tiers)

Geological lair (no faction camps):

| Tier | Center Pool | Satellites |
|------|------------|-----------|
| Small (0–55) | Hotsprings, Fossils/Small | Volcanic/Large, Basalt/Hexagon, Volcanic/Spiked |
| Medium (55–90) | Fossils/Large Normal/Ruined, Hotsprings | Fossils/Small, Hotsprings, Volcanic/Spiked, Basalt/Hexagon, Stalactites/Floor |
| Large (90+) | Fossils/Gigantic Normal/Ruined, Fossils/Large | Fossils/Small+Large, Hotsprings, Volcanic/Spiked, Basalt/Hexagon |

## Mob Pool

| Mob | Weight | Role |
|-----|--------|------|
| Skeleton_Burnt_Knight | 20% | Fire melee |
| Skeleton_Burnt_Gunner | 20% | Fire ranged |
| Skeleton_Burnt_Wizard | 15% | Fire magic |
| Golem_Firesteel | 15% | Molten guardian |
| Emberwulf | 15% | Fire wolf |
| Toad_Rhino_Magma | 10% | Lava toad |
| Slug_Magma | 5% | Fire slug swarm |
| **Boss: Golem_Crystal_Flame** | — | Molten crystal golem |

## Confirmed Dead

- `Dragon_Fire` — no attacks/animations in current build. Golem_Crystal_Flame is the volcano boss.
- `PondFiller` prop type — biome fails to register. Lava pools deferred.
