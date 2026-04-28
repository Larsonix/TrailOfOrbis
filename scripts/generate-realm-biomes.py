#!/usr/bin/env python3
"""Generate WorldGenV2 biome and WorldStructure JSON files for all realm arenas.

Produces 45 biome JSONs + 45 WorldStructure JSONs with:
- Circular arena walls (Axis node) or square fallback (XValue/ZValue)
- Terrain variation via SimplexNoise2D
- FieldFunction material providers — noise-based surface material zones
- DensityDelimited tinting — 2-3 tint color variants per biome
- BlockSet floor patterns — props match any surface material
- Biome-specific props with Occurrence density filtering
- Correct per-biome environments

Techniques sourced from Hytale server analysis and community mod implementations.

Usage:
    python scripts/generate-realm-biomes.py                    # Generate all
    python scripts/generate-realm-biomes.py --biome FOREST     # One biome family
    python scripts/generate-realm-biomes.py --dry-run          # Preview only
    python scripts/generate-realm-biomes.py --wall-mode square # Square fallback
"""

import argparse
import json
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional

# ═══════════════════════════════════════════════════════════════════
# PATHS
# ═══════════════════════════════════════════════════════════════════

PROJECT_ROOT = Path(__file__).resolve().parent.parent
ASSET_ROOT = PROJECT_ROOT / "src" / "main" / "resources" / "hytale-assets"
BIOME_ROOT = ASSET_ROOT / "Server" / "HytaleGenerator" / "Biomes"
WORLDSTRUCT_ROOT = ASSET_ROOT / "Server" / "HytaleGenerator" / "WorldStructures"
INSTANCE_ROOT = ASSET_ROOT / "Server" / "Instances"

# ═══════════════════════════════════════════════════════════════════
# DATA CLASSES
# ═══════════════════════════════════════════════════════════════════

@dataclass
class PropZone:
    """A zone within a FieldFunction assignment — maps noise range to specific props."""
    min_val: float                   # Noise range lower bound (inclusive)
    max_val: float                   # Noise range upper bound (exclusive)
    prefab_paths: list[dict]         # [{"Path": "...", "Weight": N}, ...] for this zone


@dataclass
class PropLayer:
    """A single layer of props to place in the arena."""
    name: str
    prefab_paths: list[dict]         # [{"Path": "...", "Weight": N}, ...] (default/uniform)
    mesh_scale_x: int
    mesh_scale_z: int
    skip_chance: float               # 0-1, chance to skip a valid position
    jitter: float = 0.4
    min_radius: int = 15
    load_entities: bool = False
    placement: str = "floor"         # "floor", "ceiling", or "wall" — wall requires adjacent solid block
    occurrence_max: float = 0.25     # Max occurrence density (higher = more props placed)
    # Arena size filtering — layer only included when arena radius is in range.
    # Enables size-dependent progression (e.g., structures only on larger maps).
    min_arena_radius: int = 0        # Only include when arena radius >= this (0 = always)
    max_arena_radius: int = 999      # Only include when arena radius <= this (999 = always)
    # Scale control — structures should NOT scale with radius (you want MORE on bigger maps,
    # not the same density). Natural props (trees, bushes) scale to maintain consistent density.
    scale_with_radius: bool = True   # False = fixed mesh spacing regardless of arena size
    # Scanner height limit — max Y above base height to search for ground.
    # Lower values prevent placement on elevated wall terrain and hilltops.
    # Trees: 60 (can be on hills). Structures: 8 (stay near ground level).
    scanner_max_y: int = 60
    # Sink props 1 block into ground — uses BlockSet pattern (at solid ground level)
    # instead of Floor pattern (at air above ground). DontReplace BlockMask preserves
    # the terrain block underneath. Visual: bush appears rooted, not floating.
    sink_into_ground: bool = False
    # Zone-based placement (FieldFunction assignments) — if zones is non-empty,
    # uses FieldFunction instead of Constant. Proven pattern.
    zones: list[PropZone] = field(default_factory=list)
    zone_noise_scale: int = 60       # SimplexNoise2D scale for zone boundaries
    zone_seed: str = "zones"         # Seed for zone noise
    # PondFiller: auto-fills terrain depressions with fluid (lava pools, water ponds).
    # When prop_type="pondfiller", generates a PondFiller prop instead of Prefab.
    # Scans a bounding volume around each position for depressions, fills them.
    # Performance note: keep bounding_size moderate (6-10) to avoid expensive scans.
    prop_type: str = "prefab"                                    # "prefab", "pondfiller", or "cluster"
    pond_fill_material: str = ""                                 # Block filling depressions (e.g., "Lava_Source")
    pond_barrier_materials: list[str] = field(default_factory=list)  # Blocks forming depression walls
    pond_bounding_xz: int = 8                                    # Bounding box ±X/Z half-size
    pond_bounding_y_down: int = 6                                # Bounding box depth below position
    pond_bounding_y_up: int = 3                                  # Bounding box height above position
    # ClusterProp: scatters single-block Column props in organic groups around a center.
    # Children MUST be 1x1 Column props (validated at build time).
    # Each entry: {"block": "Plant_Grass_Lush_Short", "weight": 2}
    cluster_range: int = 8                                       # Cluster radius in blocks
    cluster_column_blocks: list[dict] = field(default_factory=list)  # Column block entries
    cluster_seed: str = "cluster"                                # Seed for cluster RNG
    cluster_distance_curve: list[dict] = field(default_factory=lambda: [
        {"distance": 0, "density": 0.8},
        {"distance": 4, "density": 0.2},
        {"distance": 8, "density": 0.0},
    ])


@dataclass
class BiomeSpec:
    """Complete specification for one biome type."""
    name: str
    biome_prefix: str
    # Materials — primary + variants for FieldFunction zones
    surface_materials: list[str]     # 2-3 surface block variants
    sub_material: str
    deep_material: str
    # Atmosphere — custom per-biome environment + weather
    environment: str                 # Vanilla env name (fallback)
    tint_colors: list[str]           # 2-3 hex tint variants
    # Terrain noise — defaults to SimplexNoise2D (all biomes except Swamp).
    # Set terrain_noise_type="cellnoise" for CellNoise2D (Voronoi cellular patterns).
    noise_amplitude: float           # 0.10-0.50
    noise_scale: int
    noise_octaves: int = 2
    noise_persistence: float = 0.5
    noise_seed: str = "terrain"
    # CellNoise2D terrain — organic cellular patterns (islands/channels, honeycomb, etc.)
    # Only used when terrain_noise_type="cellnoise". Reuses noise_scale for ScaleX/Z,
    # noise_octaves for Octaves, noise_seed for Seed.
    terrain_noise_type: str = "simplex"      # "simplex" or "cellnoise"
    cellnoise_cell_type: str = "Distance2Div"  # CellValue, Distance, Distance2Sub, Distance2Div, etc.
    cellnoise_jitter: float = 0.6            # Cell irregularity (0=grid, 1+=chaotic)
    cellnoise_invert: bool = False           # Invert Normalizer output (swap islands/channels)
    # Boundary shape — per-biome unique boundary design
    #   "rising"   = terrain rises into wall/hillside (most biomes)
    #   "floating" = terrain drops into void at edge (Void)
    #   "textured" = steep wall with 3D noise for craggy surface (Volcano, Mountains, Caverns)
    boundary_type: str = "rising"
    boundary_transition: float = 0.30  # Fraction of radius for transition zone (0.1=cliff, 0.5=gentle)
    boundary_density: float = 2.0      # Wall density magnitude (+N for walls, -N for void drop)
    boundary_height: int = 15          # Wall height above ground in blocks (rising/textured only)
    boundary_noise_scale: int = 0      # SimplexNoise3D scale for textured walls (0=off)
    boundary_noise_amp: float = 0.0    # 3D noise amplitude (0.3-0.6 typical)
    # Material layering
    surface_thickness: int = 1
    sub_thickness: int = 1
    # Material variation noise
    mat_noise_scale: int = 80
    mat_noise_seed: str = "mats"
    # Terrain paths — thin strips of a different material (e.g., Soil_Dirt through grass)
    # created via a narrow FieldFunction band where SimplexNoise2D crosses a threshold.
    # The noise topology naturally forms organic winding trail networks.
    path_material: str = ""          # Material for trails (empty = no paths)
    path_noise_scale: int = 40       # Controls path spacing (~40 blocks between trails)
    path_width: float = 0.15         # Noise band half-width (0.15 = ~15% coverage)
    path_seed: str = "paths"         # Noise seed
    # Tint noise
    tint_noise_scale: int = 100
    tint_noise_seed: str = "tints"
    # Water tint — per-biome color override for water appearance.
    # Goes in Environment JSON as "WaterTint". Verified from community mods.
    water_tint: str = ""             # Hex color (e.g., "#3d5c3a" for murky swamp green)
    # Ground cover — grass blades and flowers as Column props (single-block placement).
    # Uses FieldFunction density zones for organic sparse/moderate/dense coverage.
    # Each entry: {"block": "Plant_Grass_Lush_Short", "weight": 3}
    grass_blocks: list[dict] = field(default_factory=list)
    grass_noise_scale: int = 30
    grass_seed: str = "grass"
    # Props
    prop_layers: list[PropLayer] = field(default_factory=list)
    # Spawn zone: material at center that Props Pattern won't match → no props at spawn.
    # Must NOT be in surface_materials. Axis works in MaterialProvider (terrain pipeline).
    spawn_platform_material: str = "Soil_Dirt"
    # Fog/atmosphere (matches proven Weather JSON structure)
    fog_distance: list[int] = field(default_factory=lambda: [60, 120])
    fog_color: str = "#c8daf0"
    sky_top_color: str = "#87ceebff"
    sky_bottom_color: str = "#d4e8f0ff"
    fog_density: float = 0.6
    color_filter: str = ""
    # Particles: SystemId from vanilla (Dust_Cave_Flies, Ash, etc.)
    particle_id: str = ""                # Empty = no particles
    particle_color: str = "#ffffff"      # Particle tint
    # Screen effect: full-screen texture overlay (fire glow, void tint)
    screen_effect: str = ""              # Path like "ScreenEffects/Fire.png"
    screen_effect_color: str = ""        # Hex color + alpha for the overlay
    # Signature terrain features — one unique structural element per biome
    signature_type: str = "none"        # elevated_center, tidal_shelf, elevated_plateau,
                                        # lava_moat, frozen_lake, waterlogged, ceiling,
                                        # fractured, sunken_basin
    fluid_material: str = ""            # "Lava_Source" or "Water_Source"
    fluid_bottom_y: int = 0             # Relative to Base
    fluid_top_y: int = 0                # Relative to Base
    base_y_override: int = 0            # Override Base height (0=use default FLOOR_Y)
    ceiling_y: int = 0                  # Ceiling DecimalConstant Y (0=no ceiling)
    ceiling_edge_y: int = 80             # Y of ceiling at arena edge (dome drops from ceiling_y to this)
    fracture_noise_scale_xz: int = 0    # SimplexNoise3D XZ scale (0=off)
    fracture_noise_scale_y: int = 0     # SimplexNoise3D Y scale
    fracture_max_depth: float = 0.0     # Normalizer ToMax (negative=pits)
    # Dune stretching — non-uniform noise scaling via Scale density node.
    # X > 1.0 stretches features along X axis (elongated ridges).
    # Z < 1.0 compresses along Z (sharper cross-section). 1.0 = unchanged.
    noise_stretch_x: float = 1.0
    noise_stretch_z: float = 1.0
    # Wall geological strata — visible bands of different materials in arena walls.
    # Each entry: {"top_y": int, "bottom_y": int, "material": str}
    # Inserted as SimpleHorizontal in the material Queue between SpaceAndDepth
    # and deep Constant. Only visible where walls expose sub-surface layers.
    wall_strata: list[dict] = field(default_factory=list)
    # Ground cover density — per-biome skip chances for sparse/moderate/dense zones.
    # Higher values = less vegetation. Desert wants much sparser than forest.
    grass_skip_sparse: float = 0.92
    grass_skip_moderate: float = 0.60
    grass_skip_dense: float = 0.20
    # Variable-depth surface layer via NoiseThickness — snow drifts, uneven surfaces.
    # When snow_noise_scale > 0, replaces ConstantThickness with NoiseThickness for
    # the surface material layer. Noise maps to [1, snow_noise_max] block thickness.
    # 0 = disabled (use ConstantThickness as normal).
    snow_noise_scale: int = 0
    snow_noise_max: int = 0
    snow_noise_seed: str = "snow_depth"
    # 3D volumetric cave features — SimplexNoise3D that adds AND subtracts density,
    # creating natural pillars (where positive) and carving alcoves (where negative).
    # Unlike "fractured" which only subtracts (pits), this is bidirectional — producing
    # solid columns connecting floor to ceiling AND hollowed pockets in walls.
    # 0 = disabled. Typical values: scale_xz=18-25, scale_y=12-18, amp=0.3-0.5.
    cave_3d_noise_scale_xz: int = 0
    cave_3d_noise_scale_y: int = 0
    cave_3d_noise_amp: float = 0.0
    cave_3d_seed: str = "cave_3d"
    # Arena type: three terrain generation paradigms.
    #   "open"       — Standard open arena: floor surface + boundary walls, air above (most biomes)
    #   "enclosed"   — Cave arena: floor + ceiling dome, air between, walls (Caverns, Frozen Crypts)
    #                  Same density as "open" but with ceiling signature — no special routing needed.
    #   "solid_mass" — Solid block: entire volume is stone from Y=0 to ceiling. No air pocket.
    #                  Runtime systems (RealmLabyrinthPlacer) carve corridors. Separate density function.
    #                  Requires ceiling_y > 0. Creates spawn clearing at center.
    arena_type: str = "open"


@dataclass
class SizeSpec:
    """Arena size parameters."""
    name: str
    suffix: str       # "_Small", "", "_Large", "_Massive"
    radius: int


# ═══════════════════════════════════════════════════════════════════
# RADIUS TIERS — replaces old fixed sizes
# ═══════════════════════════════════════════════════════════════════

# Arena radius is computed dynamically by Java: mob count → radius (12 to 126).
# We generate biome files at discrete radius tiers. Java picks the nearest tier
# (rounding UP) when spawning a realm instance. The terrain walls are always
# a few blocks beyond the arena edge — no more 140-block terrain for 12-block arenas.
#
# Naming: Realm_Forest_R15, Realm_Forest_R20, ..., Realm_Forest_R130
# Java selects: computeArenaRadius() → next tier → "Realm_{biome}_R{tier}"

RADIUS_TIERS = [35, 40, 45, 50, 55, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150]

SIZES = [SizeSpec(f"R{r}", f"_R{r}", r) for r in RADIUS_TIERS]

# ═══════════════════════════════════════════════════════════════════
# BIOME DEFINITIONS
# ═══════════════════════════════════════════════════════════════════

BIOMES: list[BiomeSpec] = [
    # ─── RISING BARRIER biomes (terrain slopes up into natural hillside) ───
    BiomeSpec(
        name="Forest",
        biome_prefix="Realm_Forest",
        surface_materials=["Soil_Grass_Full"],
        sub_material="Soil_Dirt", deep_material="Rock_Stone",
        environment="Env_Zone1_Plains",
        fog_distance=[50, 100], fog_color="#a8c8a0", sky_top_color="#87ceebff", sky_bottom_color="#d4e8d0ff", fog_density=0.5,
        particle_id="Dust_Cave_Flies", particle_color="#90c090",
        tint_colors=["#228B22", "#1E7B1E", "#2D9B2D"],
        noise_amplitude=0.20, noise_scale=30, noise_octaves=2,
        boundary_type="rising", boundary_transition=0.30, boundary_density=5, boundary_height=10,
        signature_type="elevated_center",
        # Ground cover — grass blades + wildflowers
        grass_blocks=[
            {"block": "Plant_Grass_Lush_Short", "weight": 30},
            {"block": "Plant_Grass_Lush_Tall", "weight": 20},
            {"block": "Plant_Grass_Sharp", "weight": 15},
            {"block": "Plant_Grass_Sharp_Wild", "weight": 10},
            {"block": "Plant_Flower_Common_Red", "weight": 1},
            {"block": "Plant_Flower_Common_Yellow", "weight": 1},
            {"block": "Plant_Flower_Common_Blue", "weight": 1},
            {"block": "Plant_Flower_Common_White", "weight": 1},
        ],
        grass_noise_scale=30, grass_seed="forest_grass",

        prop_layers=[
            # ═══════════════════════════════════════════════════════════
            # NATURAL LANDSCAPE (all arena sizes)
            # ═══════════════════════════════════════════════════════════

            # ─── TREES: Zone-based (FieldFunction) ───
            # Noise creates organic clearings (-1 to -0.3 = no trees),
            # transition zones (-0.3 to 0.3 = young/smaller trees),
            # and dense forest patches (0.3 to 1.0 = mature trees).
            PropLayer("trees", [], mesh_scale_x=5, mesh_scale_z=5, skip_chance=0.15,
                      occurrence_max=0.35, zones=[
                PropZone(-0.3, 0.3, [
                    {"Path": "Trees/Birch/Stage_2", "Weight": 3},
                    {"Path": "Trees/Ash/Stage_2", "Weight": 2},
                    {"Path": "Trees/Maple/Stage_3", "Weight": 1},
                ]),
                PropZone(0.3, 1.0, [
                    {"Path": "Trees/Oak/Stage_3", "Weight": 4},
                    {"Path": "Trees/Oak/Stage_4", "Weight": 3},
                    {"Path": "Trees/Beech/Stage_3", "Weight": 2},
                    {"Path": "Trees/Maple/Stage_3", "Weight": 2},
                    {"Path": "Trees/Cedar/Stage_3", "Weight": 1},
                    {"Path": "Trees/Ash/Stage_3", "Weight": 1},
                ]),
            ], zone_noise_scale=60, zone_seed="forest_zones"),
            # ─── UNDERGROWTH (bush scatter — sunk 1 block into ground) ───
            PropLayer("undergrowth", [
                {"Path": "Plants/Bush/Cliff", "Weight": 1},
            ], mesh_scale_x=6, mesh_scale_z=6, skip_chance=0.3,
               scanner_max_y=8, sink_into_ground=True),
            # ─── GROUND COVER (ferns + small bushes — sunk 1 block into ground) ───
            PropLayer("ground_cover", [
                {"Path": "Plants/Jungle/Ferns/Small", "Weight": 4},
                {"Path": "Plants/Bush/Green", "Weight": 3},
                {"Path": "Plants/Bush/Lush", "Weight": 3},
            ], mesh_scale_x=7, mesh_scale_z=7, skip_chance=0.4, occurrence_max=0.20,
               scanner_max_y=8, sink_into_ground=True),
            # ─── ROCKS (moderate scatter) ───
            PropLayer("rocks", [
                {"Path": "Rock_Formations/Rocks/Stone/Small", "Weight": 3},
                {"Path": "Rock_Formations/Rocks/Grass/Small", "Weight": 3},
                {"Path": "Rock_Formations/Rocks/Stone/Large", "Weight": 1},
                {"Path": "Rock_Formations/Rocks/Slate/Small", "Weight": 1},
            ], mesh_scale_x=12, mesh_scale_z=12, skip_chance=0.5),
            # ─── FOREST FLOOR (mushrooms + ground cover) ───
            PropLayer("forest_floor", [
                {"Path": "Plants/Mushroom_Large/Green/Stage_1", "Weight": 3},
                {"Path": "Plants/Mushroom_Large/Green/Stage_3", "Weight": 1},
                {"Path": "Rock_Formations/Rocks/Grass/Small", "Weight": 3},
            ], mesh_scale_x=6, mesh_scale_z=6, skip_chance=0.4, occurrence_max=0.30),
            # ─── FALLEN LOGS & STUMPS (woodland debris) ───
            PropLayer("fallen_logs", [
                {"Path": "Trees/Oak_Stumps", "Weight": 3},
                {"Path": "Trees/Maple_Stumps", "Weight": 2},
                {"Path": "Trees/Autumn_Stumps", "Weight": 1},
            ], mesh_scale_x=15, mesh_scale_z=15, skip_chance=0.4, occurrence_max=0.15,
               scale_with_radius=False, scanner_max_y=8),
            # ─── MUSHROOM RINGS (rare magical clearings) ───
            PropLayer("mushroom_rings", [
                {"Path": "Plants/Mushroom_Rings", "Weight": 1},
            ], mesh_scale_x=40, mesh_scale_z=40, skip_chance=0.5, occurrence_max=0.05,
               scale_with_radius=False, scanner_max_y=8),
            # ─── ROCK ARCHES & PILLARS (rare landmark features) ───
            PropLayer("rock_landmarks", [
                {"Path": "Rock_Formations/Arches/Forest", "Weight": 2},
                {"Path": "Rock_Formations/Pillars/Rock_Stone/Oak", "Weight": 1},
                {"Path": "Rock_Formations/Pillars/Rock_Stone/Birch", "Weight": 1},
            ], mesh_scale_x=50, mesh_scale_z=50, skip_chance=0.5, occurrence_max=0.05,
               scale_with_radius=False, scanner_max_y=8),

            # NOTE: ALL Trork faction structures are placed at runtime by
            # RealmStructurePlacer, NOT in WorldGen prop layers. WorldGen has NO
            # cross-layer collision detection — structures from different prop layers
            # overlap freely. RealmStructurePlacer uses StructureBoundsRegistry with
            # safety margins, guaranteeing no overlaps and minimum spacing.
            # Compound Trork Layouts → BossStructurePlacer with spawner expansion.
        ],
    ),
    BiomeSpec(
        name="Desert",
        biome_prefix="Realm_Desert",
        surface_materials=["Soil_Sand_White", "Soil_Sand_Red"],
        sub_material="Rock_Sandstone", deep_material="Rock_Sandstone",
        environment="Env_Zone2_Deserts",
        fog_distance=[40, 120], fog_color="#d8b878", sky_top_color="#c89850ff", sky_bottom_color="#e8c880ff", fog_density=0.5,
        particle_id="Dust_Cave_Flies", particle_color="#d4b088",
        color_filter="#f0d8a0",
        tint_colors=["#EDC9AF", "#D4B896", "#E0BC9C"],
        noise_amplitude=0.50, noise_scale=80, noise_octaves=2,
        boundary_type="rising", boundary_transition=0.40, boundary_density=4, boundary_height=8,
        sub_thickness=2,
        # Sunken basin — gentle central depression for natural amphitheater combat feel
        signature_type="sunken_basin",
        # Ancient caravan routes — sandstone paths winding through the dunes
                # Geological strata — visible layered bands in arena walls and cliff faces
        wall_strata=[
            {"top_y": 72, "bottom_y": 70, "material": "Soil_Sand_Red"},
            {"top_y": 67, "bottom_y": 65, "material": "Soil_Dirt"},
            {"top_y": 60, "bottom_y": 58, "material": "Rock_Stone"},
        ],
        # Desert ground cover — sparse dead grass tufts poking through sand
        grass_blocks=[
            {"block": "Plant_Grass_Sharp", "weight": 5},
            {"block": "Plant_Grass_Sharp_Wild", "weight": 3},
        ],
        grass_noise_scale=40, grass_seed="desert_scrub",
        grass_skip_sparse=0.97, grass_skip_moderate=0.85, grass_skip_dense=0.55,

        prop_layers=[
            # ═══════════════════════════════════════════════════════════
            # NATURAL LANDSCAPE (all arena sizes)
            # ═══════════════════════════════════════════════════════════

            # ─── VEGETATION: Zone-based (FieldFunction) ───
            # Noise creates organic zones: open dunes (-1 to -0.2 = nothing),
            # scattered cacti (-0.2 to 0.4), dry woodland patches (0.4 to 1.0).
            PropLayer("vegetation", [], mesh_scale_x=7, mesh_scale_z=7, skip_chance=0.3,
                      occurrence_max=0.30, zones=[
                PropZone(-0.2, 0.4, [
                    {"Path": "Plants/Cacti/Full/Stage_1", "Weight": 4},
                    {"Path": "Plants/Cacti/Full/Stage_2", "Weight": 3},
                    {"Path": "Plants/Cacti/Full/Stage_3", "Weight": 1},
                    {"Path": "Plants/Cacti/Flat/Stage_0", "Weight": 3},
                ]),
                PropZone(0.4, 1.0, [
                    {"Path": "Trees/Boab/Stage_2", "Weight": 3},
                    {"Path": "Trees/Dry/Stage_1", "Weight": 4},
                    {"Path": "Trees/Burnt/Stage_1", "Weight": 1},
                ]),
            ], zone_noise_scale=50, zone_seed="desert_zones"),
            # ─── SANDSTONE FORMATIONS (prominent rock outcrops) ───
            PropLayer("rock_formations", [
                {"Path": "Rock_Formations/Rocks/Sandstone/Large", "Weight": 3},
                {"Path": "Rock_Formations/Rocks/Sandstone/Small", "Weight": 4},
                {"Path": "Rock_Formations/Rocks/Sandstone/Red/Small", "Weight": 3},
                {"Path": "Rock_Formations/Rocks/Quartzite/Large", "Weight": 1},
                {"Path": "Rock_Formations/Rocks/Quartzite/Small", "Weight": 2},
            ], mesh_scale_x=10, mesh_scale_z=10, skip_chance=0.4, occurrence_max=0.25),
            # ─── DRY SCRUB (arid bushes — sunk 1 block into ground) ───
            PropLayer("dry_scrub", [
                {"Path": "Plants/Bush/Arid", "Weight": 4},
                {"Path": "Plants/Bush/Arid_Red", "Weight": 3},
                {"Path": "Plants/Bush/Dead_Lavathorn", "Weight": 1},
            ], mesh_scale_x=8, mesh_scale_z=8, skip_chance=0.5, occurrence_max=0.20,
               scanner_max_y=8, sink_into_ground=True),
            # ─── DESERT FLOOR (driftwood + small stones) ───
            PropLayer("desert_floor", [
                {"Path": "Plants/Driftwood/Dry", "Weight": 3},
                {"Path": "Rock_Formations/Rocks/Sandstone/Small", "Weight": 3},
                {"Path": "Rock_Formations/Rocks/Chalk/Small", "Weight": 2},
                {"Path": "Rock_Formations/Rocks/Shale/Small", "Weight": 1},
            ], mesh_scale_x=7, mesh_scale_z=7, skip_chance=0.5, occurrence_max=0.20),
            # ─── DESERT ARCHES (native desert rock formations) ───
            PropLayer("desert_arches", [
                {"Path": "Rock_Formations/Arches/Desert", "Weight": 3},
                {"Path": "Rock_Formations/Arches/Desert_Red", "Weight": 2},
                {"Path": "Rock_Formations/Arches/Savannah", "Weight": 1},
            ], mesh_scale_x=50, mesh_scale_z=50, skip_chance=0.5, occurrence_max=0.05,
               scale_with_radius=False, scanner_max_y=8),
            # ─── DESERT PILLARS (hoodoo-like stone columns) ───
            # Shale/Bare has size subdirs (Small/Medium/Large), not direct files.
            # Use specific size paths or only Rock_Stone variants which have direct files.
            PropLayer("desert_pillars", [
                {"Path": "Rock_Formations/Pillars/Rock_Stone/Ash", "Weight": 3},
                {"Path": "Rock_Formations/Pillars/Rock_Stone/Plains", "Weight": 2},
                {"Path": "Rock_Formations/Pillars/Shale/Bare/Small", "Weight": 1},
                {"Path": "Rock_Formations/Pillars/Shale/Bare/Medium", "Weight": 1},
            ], mesh_scale_x=40, mesh_scale_z=40, skip_chance=0.5, occurrence_max=0.08,
               scale_with_radius=False, scanner_max_y=8),

            # NOTE: Complex structures (Monuments, Fossils, Hotsprings) are placed
            # at runtime by RealmStructurePlacer, NOT in WorldGen prop layers.
            # These vanilla prefabs contain PrefabSpawnerBlock child references
            # that crash WorldGen's PrefabLoader ("prefab pool contains empty list").
            # PrefabUtil.paste() bypasses this validation — proven safe at runtime.
            # See RealmStructurePlacer.java STRUCTURE_POOLS for the full list.
        ],
    ),
    BiomeSpec(
        name="Tundra",
        biome_prefix="Realm_Tundra",
        surface_materials=["Soil_Snow"],
        sub_material="Rock_Ice", deep_material="Rock_Stone",
        environment="Env_Zone3_Tundra",
        fog_distance=[30, 90], fog_color="#c0d8f0", sky_top_color="#8ab8d8ff", sky_bottom_color="#d0e8ffff", fog_density=0.6,
        particle_id="Dust_Cave_Flies", particle_color="#d0e0f0",
        color_filter="#c8e0f8",
        tint_colors=["#B0E0E6", "#9DD0D8", "#C4EAF0"],
        noise_amplitude=0.15, noise_scale=40, noise_octaves=2,
        boundary_type="rising", boundary_transition=0.20, boundary_density=5, boundary_height=15,
        signature_type="frozen_lake", fluid_material="Water_Source", fluid_bottom_y=-6, fluid_top_y=-2,
        # Variable-depth snow — NoiseThickness creates organic wind-sculpted drifts.
        # 1 block on exposed ridges, up to 4 blocks in sheltered valleys.
        snow_noise_scale=30, snow_noise_max=4, snow_noise_seed="snow_depth",
        # Frozen ice trails winding through snow
                # Permafrost strata — snow/ice/stone bands visible in cliff faces
        wall_strata=[
            {"top_y": 72, "bottom_y": 70, "material": "Soil_Snow"},
            {"top_y": 67, "bottom_y": 64, "material": "Rock_Ice"},
            {"top_y": 58, "bottom_y": 55, "material": "Rock_Stone"},
        ],
        # Winter ground cover — sparse frozen bush tufts
        grass_blocks=[
            {"block": "Plant_Grass_Sharp", "weight": 3},
            {"block": "Plant_Grass_Sharp_Wild", "weight": 2},
        ],
        grass_noise_scale=35, grass_seed="tundra_scrub",
        grass_skip_sparse=0.96, grass_skip_moderate=0.82, grass_skip_dense=0.50,

        prop_layers=[
            # ═══════════════════════════════════════════════════════════
            # NATURAL LANDSCAPE (all arena sizes)
            # ═══════════════════════════════════════════════════════════

            # ─── TREES: Zone-based Fir_Snow (FieldFunction) ───
            # Wind-exposed clearings (-1 to -0.3 = no trees),
            # sparse young firs (-0.3 to 0.3), dense mature forest (0.3 to 1.0).
            # Fir_Snow prefabs are proper snow-covered firs, not generic green.
            PropLayer("trees", [], mesh_scale_x=5, mesh_scale_z=5, skip_chance=0.2,
                      occurrence_max=0.35, zones=[
                PropZone(-0.3, 0.3, [
                    {"Path": "Trees/Fir_Snow/Stage_1", "Weight": 5},
                    {"Path": "Trees/Fir_Snow/Stage_2", "Weight": 2},
                ]),
                PropZone(0.3, 1.0, [
                    {"Path": "Trees/Fir_Snow/Stage_2", "Weight": 3},
                    {"Path": "Trees/Fir_Snow/Stage_3", "Weight": 5},
                ]),
            ], zone_noise_scale=55, zone_seed="tundra_zones"),
            # ─── ICEBERGS (unique Tundra landmark — near frozen lake) ───
            PropLayer("icebergs", [
                {"Path": "Rock_Formations/Ice_Formations/Icebergs", "Weight": 1},
            ], mesh_scale_x=50, mesh_scale_z=50, skip_chance=0.5, occurrence_max=0.05,
               scale_with_radius=False, scanner_max_y=8),
            # ─── ICE ROCKS (frozenstone + snowy basalt) ───
            PropLayer("ice_rocks", [
                {"Path": "Rock_Formations/Rocks/Frozenstone/Small", "Weight": 3},
                {"Path": "Rock_Formations/Rocks/Frozenstone/Snowy", "Weight": 3},
                {"Path": "Rock_Formations/Rocks/Basalt/Snowy", "Weight": 2},
                {"Path": "Rock_Formations/Rocks/Basalt/Tundra", "Weight": 2},
                {"Path": "Rock_Formations/Rocks/Shale/Snowy", "Weight": 1},
            ], mesh_scale_x=10, mesh_scale_z=10, skip_chance=0.4, occurrence_max=0.25),
            # ─── ICE CRYSTALS (geodes — signature decorative) ───
            PropLayer("ice_crystals", [
                {"Path": "Rock_Formations/Rocks/Geodes/White", "Weight": 3},
                {"Path": "Rock_Formations/Rocks/Geodes/Cyan", "Weight": 2},
                {"Path": "Rock_Formations/Rocks/Geodes/Blue", "Weight": 1},
            ], mesh_scale_x=18, mesh_scale_z=18, skip_chance=0.5, occurrence_max=0.15),
            # ─── WINTER GROUND (bushes sunk into snow + rocks) ───
            PropLayer("winter_ground", [
                {"Path": "Plants/Bush/Winter", "Weight": 4},
                {"Path": "Rock_Formations/Rocks/Frozenstone/Small", "Weight": 2},
                {"Path": "Rock_Formations/Rocks/Calcite/Small", "Weight": 1},
            ], mesh_scale_x=8, mesh_scale_z=8, skip_chance=0.5, occurrence_max=0.20,
               scanner_max_y=8, sink_into_ground=True),
            # ─── TUNDRA ARCHES (snowy + tundra rock arches — rare landmarks) ───
            PropLayer("tundra_arches", [
                {"Path": "Rock_Formations/Arches/Snowy", "Weight": 3},
                {"Path": "Rock_Formations/Arches/Tundra", "Weight": 3},
            ], mesh_scale_x=50, mesh_scale_z=50, skip_chance=0.5, occurrence_max=0.05,
               scale_with_radius=False, scanner_max_y=8),
            # ─── TUNDRA PILLARS (snowy stone columns) ───
            PropLayer("tundra_pillars", [
                {"Path": "Rock_Formations/Pillars/Snowy", "Weight": 1},
            ], mesh_scale_x=40, mesh_scale_z=40, skip_chance=0.5, occurrence_max=0.08,
               scale_with_radius=False, scanner_max_y=8),

            # NOTE: ALL Outlander/Yeti faction structures are placed at runtime by
            # RealmStructurePlacer, NOT in WorldGen prop layers. Same architecture as
            # Forest (Trork) and Jungle (Feran). WorldGen is for natural environment
            # only — runtime placement gets full collision prevention via
            # StructureBoundsRegistry. Compound Outlander layouts → BossStructurePlacer.
        ],
    ),
    # ─── SWAMP: The Rotting Marsh — CellNoise2D island terrain (FIRST USE) ───
    # New WorldGenV2 mechanic: CellNoise2D (Voronoi cellular noise) replaces SimplexNoise2D
    # for terrain. Distance2Div creates wells at cell centers and ridges at boundaries.
    # With cellnoise_invert=True, the Normalizer flips this: cell centers become raised
    # islands, cell boundaries become depressed channels filled by waterlogged fluid.
    # Also debuts: path_material (organic mud trails) and ClusterProp (reed clusters).
    BiomeSpec(
        name="Swamp",
        biome_prefix="Realm_Swamp",
        surface_materials=["Soil_Mud", "Soil_Dirt", "Soil_Grass"],
        sub_material="Soil_Mud", deep_material="Soil_Mud",  # Entire mass is mud (no stone peek-through)
        spawn_platform_material="Rock_Stone",  # Solid spawn area above water
        environment="Env_Zone1_Plains",
        fog_distance=[-20, 45], fog_color="#2a3a20", sky_top_color="#3a4a30ff", sky_bottom_color="#1a2a10ff", fog_density=0.85,
        particle_id="Dust_Cave_Flies", particle_color="#405030",
        tint_colors=["#2E4E2E", "#1F3F1F", "#3A5A3A", "#2A4A1A"],
        # ═══ CellNoise2D terrain — FIRST BIOME TO USE VORONOI NOISE ═══
        # Distance2Div output [-1, 0]: cell centers=-1, boundaries=0.
        # cellnoise_invert=True flips it: centers→islands (+amp), boundaries→channels (-amp).
        # Scale=40 creates ~40-block islands. Jitter=0.6 for organic irregularity.
        # amplitude=0.15 * 15 = ±2.25 blocks → islands rise ~2 blocks above water,
        # channels dip ~2 blocks into the fluid band (Y=59-63). Perfect interaction.
        noise_amplitude=0.15, noise_scale=40, noise_octaves=2,
        terrain_noise_type="cellnoise",
        cellnoise_cell_type="Distance2Div",
        cellnoise_jitter=0.6,
        cellnoise_invert=True,
        boundary_type="rising", boundary_transition=0.50, boundary_density=3, boundary_height=5,
        signature_type="waterlogged", fluid_material="Water_Source", fluid_bottom_y=-5, fluid_top_y=0,
        water_tint="#3d5c3a",  # Murky greenish-brown swamp water
        # Geological bands in the low boundary walls
        wall_strata=[
            {"top_y": 68, "bottom_y": 66, "material": "Soil_Grass"},
            {"top_y": 62, "bottom_y": 60, "material": "Rock_Stone"},
        ],
        # ═══ path_material — FIRST BIOME TO USE MUD TRAILS ═══
        # Organic winding trails of worn earth through the marsh. 2-octave SimplexNoise2D
        # creates branching networks. Visible on Mud and Grass zones, blends into Dirt zones.
        path_material="Soil_Dirt",
        path_noise_scale=35, path_width=0.12, path_seed="swamp_trails",
        # Dense swamp ground cover — marsh-specific plants (from BIOME_ASSET_REFERENCE.md)
        grass_blocks=[
            {"block": "Plant_Reeds_Marsh", "weight": 20},
            {"block": "Plant_Grass_Wet", "weight": 18},
            {"block": "Plant_Reeds_Wet", "weight": 15},
            {"block": "Plant_Grass_Wet_Overgrown", "weight": 12},
            {"block": "Plant_Fern_Wet_Big", "weight": 10},
            {"block": "Plant_Bush_Wet", "weight": 8},
            {"block": "Plant_Flower_Water_Green", "weight": 5},
        ],
        grass_noise_scale=20, grass_seed="swamp_moss",
        grass_skip_sparse=0.80, grass_skip_moderate=0.40, grass_skip_dense=0.10,

        prop_layers=[
            # ═══════════════════════════════════════════════════════════
            # FLOOR — 3-zone FieldFunction: Dead Marsh / Fungal Glow / Living Canopy
            # zone_seed="swamp_zones" shared across all zone layers for coherent areas
            # ═══════════════════════════════════════════════════════════

            # ─── SWAMP TREES: Zone-based primary canopy (MAXIMUM Ash diversity) ───
            # 18 unique Ash directory paths, 60+ prefabs. Trees are the DOMINANT feature.
            # mesh_scale=7 + skip=0.15 = very dense tree canopy (denser than Forest's mesh=10)
            # Zone A (Dead Marsh): twisted/dead ash — skeletal corrupted silhouettes
            # Zone B (Fungal Glow): dead ash + swamp ash — dead hosts, some living
            # Zone C (Living Canopy): deeproot/moss/stumps + largest swamp ash — ancient roots
            PropLayer("swamp_trees", [], mesh_scale_x=7, mesh_scale_z=7, skip_chance=0.15,
                      occurrence_max=0.35, zones=[
                PropZone(-1.0, -0.2, [
                    {"Path": "Trees/Ash_twisted", "Weight": 5},
                    {"Path": "Trees/Ash_twisted_Large", "Weight": 4},
                    {"Path": "Trees/Ash_twisted_Giant", "Weight": 3},
                    {"Path": "Trees/Ash_swamp_dead/Stage_3", "Weight": 3},
                    {"Path": "Trees/Ash_Dead/Stage_3", "Weight": 3},
                    {"Path": "Trees/Ash_Dead/Stage_2", "Weight": 2},
                    {"Path": "Trees/Poisoned/Stage_3", "Weight": 2},
                    {"Path": "Trees/Poisoned/Stage_4", "Weight": 1},
                ]),
                PropZone(-0.2, 0.3, [
                    {"Path": "Trees/Ash_Dead/Stage_3", "Weight": 4},
                    {"Path": "Trees/Ash_Dead/Stage_2", "Weight": 3},
                    {"Path": "Trees/Ash_swamp/Stage_3", "Weight": 4},
                    {"Path": "Trees/Ash_swamp/Stage_2", "Weight": 3},
                    {"Path": "Trees/Ash_swamp/Stage_4", "Weight": 2},
                    {"Path": "Trees/Ash/Stage_1", "Weight": 2},
                    {"Path": "Trees/Poisoned/Stage_1", "Weight": 1},
                ]),
                PropZone(0.3, 1.0, [
                    {"Path": "Trees/Ash/Deeproot_Boulders", "Weight": 5},
                    {"Path": "Trees/Ash/Deeproot_Roots", "Weight": 3},
                    {"Path": "Trees/Ash_Moss/Stage_3", "Weight": 3},
                    {"Path": "Trees/Ash_swamp/Stage_4", "Weight": 4},
                    {"Path": "Trees/Ash_swamp/Stage_3", "Weight": 3},
                    {"Path": "Trees/Ash/Stumps", "Weight": 2},
                    {"Path": "Trees/Ash/Stage_0", "Weight": 1},
                ]),
            ], zone_noise_scale=70, zone_seed="swamp_zones"),

            # ─── FALLEN LOGS (all zones — ash logs with moss/mushrooms on forest floor) ───
            PropLayer("fallen_logs", [
                {"Path": "Trees/Logs/Ash/Moss", "Weight": 4},
                {"Path": "Trees/Logs/Ash/Mushroom_Cap_Brown", "Weight": 2},
                {"Path": "Trees/Logs/Ash/Mushroom_Cap_Red", "Weight": 2},
                {"Path": "Trees/Logs/Ash/Mushroom_Cap_White", "Weight": 1},
                {"Path": "Trees/Logs/Ash/Mushroom_Common_Brown", "Weight": 1},
            ], mesh_scale_x=15, mesh_scale_z=15, skip_chance=0.35, occurrence_max=0.15,
               scale_with_radius=False),

            # ─── FUNGAL UNDERGROWTH (zone B only — bioluminescent mushroom garden) ───
            # Sparser than trees (mesh=14) — mushrooms are accents, not the main feature
            PropLayer("fungal_glow", [], mesh_scale_x=14, mesh_scale_z=14, skip_chance=0.35,
                      occurrence_max=0.20, zones=[
                PropZone(-0.2, 0.3, [
                    {"Path": "Plants/Mushroom_Large/Green/Stage_3", "Weight": 4},
                    {"Path": "Plants/Mushroom_Large/Purple/Stage_3", "Weight": 3},
                    {"Path": "Plants/Mushroom_Large/Yellow/Stage_3", "Weight": 2},
                    {"Path": "Plants/Mushroom_Large/Green/Stage_1", "Weight": 3},
                    {"Path": "Plants/Mushroom_Large/Purple/Stage_1", "Weight": 2},
                ]),
            ], zone_noise_scale=70, zone_seed="swamp_zones"),

            # ─── MUSHROOM FORMATIONS (zone B — rock-based mushroom structures, rare) ───
            PropLayer("mushroom_rocks", [], mesh_scale_x=25, mesh_scale_z=25, skip_chance=0.50,
                      occurrence_max=0.10, zones=[
                PropZone(-0.2, 0.3, [
                    {"Path": "Rock_Formations/Mushrooms/Rock/Large", "Weight": 2},
                    {"Path": "Rock_Formations/Mushrooms/Rock/Small", "Weight": 3},
                    {"Path": "Rock_Formations/Mushrooms/Rock/Tall", "Weight": 1},
                    {"Path": "Rock_Formations/Mushrooms/Shelf", "Weight": 2},
                    {"Path": "Rock_Formations/Mushrooms/Pool", "Weight": 1},
                ]),
            ], zone_noise_scale=70, zone_seed="swamp_zones"),

            # ─── DEAD WOOD SCATTER (zone A — decay and rot) ───
            PropLayer("dead_wood", [], mesh_scale_x=12, mesh_scale_z=12, skip_chance=0.30,
                      occurrence_max=0.25, zones=[
                PropZone(-1.0, -0.2, [
                    {"Path": "Plants/Driftwood/Dry", "Weight": 4},
                    {"Path": "Plants/Driftwood/Cedar", "Weight": 3},
                    {"Path": "Plants/Twisted_Wood/Poisoned", "Weight": 2},
                    {"Path": "Plants/Twisted_Wood/Fire", "Weight": 1},
                ]),
            ], zone_noise_scale=70, zone_seed="swamp_zones"),

            # ─── HANGING VINES (zone C only — cascading from living canopy) ───
            PropLayer("hanging_vines", [], mesh_scale_x=8, mesh_scale_z=8, skip_chance=0.30,
                      occurrence_max=0.25, zones=[
                PropZone(0.3, 1.0, [
                    {"Path": "Plants/Vines/Green_Hanging", "Weight": 5},
                    {"Path": "Plants/Vines/Green", "Weight": 3},
                    {"Path": "Plants/Vines/Bush_Hanging", "Weight": 2},
                ]),
            ], zone_noise_scale=70, zone_seed="swamp_zones"),

            # ─── UNDERGROWTH (all zones — swamp floor vegetation sunk into ground) ───
            # NOTE: Seaweed prefabs removed — they're tall vertical structures designed
            # for underwater placement, creating waterlogged columns in open air.
            PropLayer("undergrowth", [
                {"Path": "Plants/Twisted_Wood/Poisoned", "Weight": 3},
                {"Path": "Plants/Twisted_Wood/Ash", "Weight": 3},
                {"Path": "Plants/Bush/Dead_Hanging", "Weight": 2},
                {"Path": "Plants/Driftwood/Dry", "Weight": 3},
                {"Path": "Plants/Driftwood/Cedar", "Weight": 2},
            ], mesh_scale_x=7, mesh_scale_z=7, skip_chance=0.30, occurrence_max=0.25,
               sink_into_ground=True),

            # ─── SWAMP FLOOR DETAIL (all zones — dense small scatter) ───
            PropLayer("swamp_floor", [
                {"Path": "Rock_Formations/Rocks/Grass/Small", "Weight": 3},
                {"Path": "Rock_Formations/Rocks/Shale/Small", "Weight": 2},
                {"Path": "Rock_Formations/Rocks/Slate/Small", "Weight": 1},
                {"Path": "Plants/Driftwood/Dry", "Weight": 2},
            ], mesh_scale_x=7, mesh_scale_z=7, skip_chance=0.40, occurrence_max=0.20),

            # ─── SWAMP ARCHES (landmarks — zone-based for thematic coherence) ───
            PropLayer("swamp_arches", [], mesh_scale_x=50, mesh_scale_z=50, skip_chance=0.5,
                      occurrence_max=0.05, scale_with_radius=False, scanner_max_y=8, zones=[
                PropZone(-1.0, -0.2, [
                    {"Path": "Rock_Formations/Arches/Swamp_Poisoned/Large", "Weight": 2},
                    {"Path": "Rock_Formations/Arches/Swamp_Poisoned/Small", "Weight": 3},
                ]),
                PropZone(0.3, 1.0, [
                    {"Path": "Rock_Formations/Arches/Swamp/Large", "Weight": 2},
                    {"Path": "Rock_Formations/Arches/Swamp/Small", "Weight": 3},
                ]),
            ], zone_noise_scale=70, zone_seed="swamp_zones"),

            # ─── SWAMP PILLARS (rare towering moss-covered sentinels) ───
            PropLayer("swamp_pillars", [
                {"Path": "Rock_Formations/Pillars/Rock_Stone/Swamp", "Weight": 1},
            ], mesh_scale_x=45, mesh_scale_z=45, skip_chance=0.5, occurrence_max=0.05,
               scale_with_radius=False, scanner_max_y=8),

            # ─── MUSHROOM RINGS (rare magical clearings) ───
            PropLayer("mushroom_rings", [
                {"Path": "Plants/Mushroom_Rings", "Weight": 1},
            ], mesh_scale_x=40, mesh_scale_z=40, skip_chance=0.5, occurrence_max=0.05,
               scale_with_radius=False, scanner_max_y=8),

            # ─── SWAMP SCATTER (dense ground-level debris and small vegetation) ───
            PropLayer("swamp_scatter", [
                {"Path": "Plants/Driftwood/Dry", "Weight": 4},
                {"Path": "Plants/Driftwood/Cedar", "Weight": 3},
                {"Path": "Plants/Twisted_Wood/Ash", "Weight": 2},
                {"Path": "Rock_Formations/Rocks/Grass/Small", "Weight": 2},
            ], mesh_scale_x=6, mesh_scale_z=6, skip_chance=0.25, occurrence_max=0.30,
               sink_into_ground=True),

            # NOTE: Kweebec/Swamp faction structures (Old_Farms, Houses, Guards, Camps)
            # and Monuments/Encounter/Zone4/Tier4/Village/Swamp are placed via
            # RealmStructurePlacer at runtime. All 20 prefabs verified as leaf directories
            # with no PrefabSpawnerBlocks — same Npc/ architecture as Trork and Outlander.
            # Boss camps use Kweebec ruins + mushroom formations via BossStructurePlacer.
        ],
    ),
    # ─── BEACH: "The Sunken Corsair" ───
    # A graduated tropical shoreline that tells the story of a sunken pirate civilization.
    # Terrain slopes from raised dry center to lowered coral shelf at edges (tidal_shelf).
    # Three FieldFunction zones (Dry Shore → Wet Transition → Coral Shelf) create distinct
    # elevation bands with completely unique props:
    #   Zone A [-1.0, -0.15] = Dry Shore — Palm_Green groves, Driftwood/Redwood, white sand
    #   Zone B [-0.15, 0.40] = Wet Transition — sparse palms, calcite tide pools, debris
    #   Zone C [0.40, 1.0]   = Coral Shelf — geode outcrops, quartzite, no trees
    # Exclusive assets: Palm_Green (15 prefabs), Driftwood/Redwood (10), Arches/Sandstone (12).
    # Runtime: 62 City_Oceans coral buildings + 6 warm shipwrecks + beach camps.
    # Brightest atmosphere of any biome — design through clarity, not obscurity.
    BiomeSpec(
        name="Beach",
        biome_prefix="Realm_Beach",
        surface_materials=["Soil_Sand_White", "Soil_Sand_Red"],
        sub_material="Rock_Sandstone", deep_material="Rock_Stone",
        environment="Env_Zone1_Plains",
        fog_distance=[80, 180], fog_color="#e0d0b0", sky_top_color="#5cb8e8ff", sky_bottom_color="#f0e4c8ff", fog_density=0.35,
        color_filter="#f8e8c0",
        water_tint="#40a0c0",
        tint_colors=["#F5DEB3", "#E8D1A0", "#FFF0C8", "#DEC89A"],
        noise_amplitude=0.12, noise_scale=50, noise_octaves=1,
        # FLOATING boundary — terrain drops into ocean at edges (no visible walls).
        # First biome to combine floating boundary + fluid fill = island in the ocean.
        boundary_type="floating", boundary_transition=0.10, boundary_density=-4, boundary_height=0,
        signature_type="tidal_shelf",
        # Ocean water fills the void created by floating boundary
        fluid_material="Water_Source", fluid_bottom_y=-30, fluid_top_y=-1,
        path_material="Rock_Sandstone", path_noise_scale=40, path_width=0.10, path_seed="corsair_trails",
        mat_noise_scale=60, mat_noise_seed="beach_sand",
        # No wall_strata — floating boundary has no walls to show geology in
        wall_strata=[],
        # Sparse beach grass — sparsest ground cover of any biome
        grass_blocks=[
            {"block": "Plant_Grass_Sharp", "weight": 15},
            {"block": "Plant_Grass_Sharp_Wild", "weight": 10},
        ],
        grass_noise_scale=35, grass_seed="beach_grass",
        grass_skip_sparse=0.95, grass_skip_moderate=0.80, grass_skip_dense=0.50,

        prop_layers=[
            # ═══════════════════════════════════════════════════════════
            # NATURAL LANDSCAPE (all arena sizes)
            # ═══════════════════════════════════════════════════════════

            # ─── PALM TREES: Zone-based (FieldFunction) ───
            # Zone A (Dry Shore): Dense Palm_Green groves (EXCLUSIVE — never used anywhere)
            # Zone B (Wet Transition): Sparse regular palms at waterline
            # Zone C (Coral Shelf): No trees — submerged terrain
            PropLayer("palm_trees", [], mesh_scale_x=5, mesh_scale_z=5, skip_chance=0.15,
                      occurrence_max=0.40, zones=[
                # Zone A: Dense tropical palm grove — ALL variants, heavy on mature trees
                PropZone(-1.0, -0.15, [
                    {"Path": "Trees/Palm_Green/Stage_3", "Weight": 5},
                    {"Path": "Trees/Palm_Green/Stage_2", "Weight": 4},
                    {"Path": "Trees/Palm/Stage_3", "Weight": 4},
                    {"Path": "Trees/Palm/Stage_2", "Weight": 3},
                    {"Path": "Trees/Palm_Green/Stage_1", "Weight": 2},
                    {"Path": "Trees/Palm/Stage_1", "Weight": 2},
                ]),
                # Zone B: Transition — smaller palms thinning toward water
                PropZone(-0.15, 0.40, [
                    {"Path": "Trees/Palm/Stage_2", "Weight": 4},
                    {"Path": "Trees/Palm/Stage_1", "Weight": 3},
                    {"Path": "Trees/Palm_Green/Stage_2", "Weight": 3},
                    {"Path": "Trees/Palm_Green/Stage_1", "Weight": 2},
                ]),
            ], zone_noise_scale=80, zone_seed="corsair_zones"),

            # ─── DRIFTWOOD: Zone-based (FieldFunction) ───
            # Driftwood/Redwood is EXCLUSIVE (10 variants, zero other biome usage).
            # Weathered redwood beams washed ashore — Beach's signature ground scatter.
            PropLayer("driftwood", [], mesh_scale_x=10, mesh_scale_z=10, skip_chance=0.30,
                      occurrence_max=0.25, zones=[
                PropZone(-1.0, -0.15, [
                    {"Path": "Plants/Driftwood/Redwood", "Weight": 5},
                    {"Path": "Plants/Driftwood/Dry", "Weight": 2},
                ]),
                PropZone(-0.15, 0.40, [
                    {"Path": "Plants/Driftwood/Redwood", "Weight": 4},
                    {"Path": "Plants/Driftwood/Dry", "Weight": 3},
                ]),
            ], zone_noise_scale=80, zone_seed="corsair_zones"),

            # ─── COASTAL BUSHES (sunk into ground — rooted in sand) ───
            PropLayer("coastal_bushes", [
                {"Path": "Plants/Bush/Cliff", "Weight": 2},
                {"Path": "Plants/Bush/Arid", "Weight": 1},
            ], mesh_scale_x=9, mesh_scale_z=9, skip_chance=0.45, occurrence_max=0.20,
               scanner_max_y=8, sink_into_ground=True),

            # ─── BEACH ROCKS: Zone-based (FieldFunction) ───
            # Zone A: White sand + chalk. Zone B: Tide pool rocks. Zone C: Coral-encrusted quartzite.
            PropLayer("beach_rocks", [], mesh_scale_x=10, mesh_scale_z=10, skip_chance=0.35,
                      occurrence_max=0.25, zones=[
                PropZone(-1.0, -0.15, [
                    {"Path": "Rock_Formations/Rocks/Sandstone/White/Small", "Weight": 4},
                    {"Path": "Rock_Formations/Rocks/Chalk/Small", "Weight": 3},
                    {"Path": "Rock_Formations/Rocks/Sandstone/Small", "Weight": 2},
                ]),
                PropZone(-0.15, 0.40, [
                    {"Path": "Rock_Formations/Rocks/Calcite/Small", "Weight": 4},
                    {"Path": "Rock_Formations/Rocks/Grass/Small", "Weight": 3},
                    {"Path": "Rock_Formations/Rocks/Sandstone/Small", "Weight": 2},
                ]),
                PropZone(0.40, 1.0, [
                    {"Path": "Rock_Formations/Rocks/Quartzite/Small", "Weight": 4},
                    {"Path": "Rock_Formations/Rocks/Quartzite/Moss_Small", "Weight": 3},
                ]),
            ], zone_noise_scale=80, zone_seed="corsair_zones"),

            # ─── BEACH SCATTER (dense small ground detail) ───
            PropLayer("beach_scatter", [
                {"Path": "Rock_Formations/Rocks/Chalk/Small", "Weight": 3},
                {"Path": "Plants/Driftwood/Redwood", "Weight": 3},
                {"Path": "Rock_Formations/Rocks/Calcite/Small", "Weight": 2},
            ], mesh_scale_x=6, mesh_scale_z=6, skip_chance=0.35, occurrence_max=0.25),

            # ─── CORAL OUTCROPS (Zone C only — shelf zone geodes) ───
            PropLayer("coral_outcrops", [], mesh_scale_x=14, mesh_scale_z=14, skip_chance=0.40,
                      occurrence_max=0.20, zones=[
                PropZone(0.40, 1.0, [
                    {"Path": "Rock_Formations/Rocks/Geodes/Blue", "Weight": 3},
                    {"Path": "Rock_Formations/Rocks/Geodes/Cyan", "Weight": 3},
                    {"Path": "Rock_Formations/Rocks/Geodes/Green", "Weight": 2},
                    {"Path": "Rock_Formations/Rocks/Geodes/White", "Weight": 2},
                ]),
            ], zone_noise_scale=80, zone_seed="corsair_zones"),

            # ─── SANDSTONE ARCHES (EXCLUSIVE — 12 prefabs, never used anywhere) ───
            # Natural coastal rock formations — sea-eroded sandstone arches.
            PropLayer("coastal_arches", [
                {"Path": "Rock_Formations/Arches/Sandstone", "Weight": 1},
            ], mesh_scale_x=50, mesh_scale_z=50, skip_chance=0.50, occurrence_max=0.05,
               scale_with_radius=False, scanner_max_y=8),

            # ─── BEACH PILLARS (rare rocky outcrops as landmarks) ───
            PropLayer("beach_pillars", [
                {"Path": "Rock_Formations/Pillars/Rock_Stone/Plains", "Weight": 2},
                {"Path": "Rock_Formations/Pillars/Shale/Bare/Small", "Weight": 1},
                {"Path": "Rock_Formations/Pillars/Shale/Bare/Medium", "Weight": 1},
            ], mesh_scale_x=45, mesh_scale_z=45, skip_chance=0.50, occurrence_max=0.05,
               scale_with_radius=False, scanner_max_y=8),

            # ─── FALLEN DRIFTWOOD LOGS (EXCLUSIVE — Redwood debris) ───
            PropLayer("fallen_logs", [
                {"Path": "Plants/Driftwood/Redwood", "Weight": 1},
            ], mesh_scale_x=15, mesh_scale_z=15, skip_chance=0.40, occurrence_max=0.15,
               scale_with_radius=False, scanner_max_y=8),

            # ─── TIDE DETAIL (dense small scatter — shells, pebbles, bush) ───
            PropLayer("tide_detail", [
                {"Path": "Plants/Bush/Cliff", "Weight": 2},
                {"Path": "Rock_Formations/Rocks/Chalk/Small", "Weight": 2},
                {"Path": "Rock_Formations/Rocks/Calcite/Small", "Weight": 1},
            ], mesh_scale_x=7, mesh_scale_z=7, skip_chance=0.45, occurrence_max=0.20),

            # NOTE: ALL faction/complex structures are placed at runtime by
            # RealmStructurePlacer (62 City_Oceans coral buildings, 6 shipwrecks,
            # beach camps) and BossStructurePlacer (shipwreck + coral boss camps).
            # WorldGen has NO cross-layer collision detection.

            # ═══════════════════════════════════════════════════════════
            # UNDERWATER CORAL REEF (5 dense layers on the ocean floor)
            # placement="ocean_floor" on ALL: matches sub/deep materials,
            # scans deep (MinY=-40), bypasses Empty Origin check.
            # The ocean floor undulates via SimplexNoise2D (±3 blocks)
            # creating natural reef mounds, valleys, and ridges.
            # ═══════════════════════════════════════════════════════════

            # ─── CORAL CARPET (dense small bioluminescent scatter) ───
            # Glowing coral + small crystals create the base glow layer.
            # mesh_scale=4 = extremely dense (every 4 blocks checked).
            PropLayer("coral_carpet", [
                {"Path": "Plants/Jungle/Coral/Glow", "Weight": 5},
                {"Path": "Rock_Formations/Rocks/Geodes/Cyan", "Weight": 3},
                {"Path": "Rock_Formations/Rocks/Geodes/Green", "Weight": 3},
                {"Path": "Rock_Formations/Rocks/Geodes/Blue", "Weight": 2},
                {"Path": "Rock_Formations/Rocks/Geodes/Pink", "Weight": 1},
            ], mesh_scale_x=4, mesh_scale_z=4, skip_chance=0.10, occurrence_max=0.35,
               placement="ocean_floor", scanner_max_y=60),

            # ─── REEF ROCK SCATTER (dense mid-layer — coral-encrusted stone) ───
            # Mossy quartzite, chalk, calcite — looks like encrusted reef rock.
            PropLayer("reef_scatter", [
                {"Path": "Rock_Formations/Rocks/Quartzite/Moss_Small", "Weight": 4},
                {"Path": "Rock_Formations/Rocks/Calcite/Small", "Weight": 3},
                {"Path": "Rock_Formations/Rocks/Chalk/Small", "Weight": 3},
                {"Path": "Rock_Formations/Rocks/Quartzite/Small", "Weight": 2},
            ], mesh_scale_x=5, mesh_scale_z=5, skip_chance=0.15, occurrence_max=0.30,
               placement="ocean_floor", scanner_max_y=60),

            # ─── REEF FORMATIONS (large dramatic underwater landmarks) ───
            # Big quartzite boulders + floor stalagmites = underwater pillars and mounds.
            PropLayer("reef_formations", [
                {"Path": "Rock_Formations/Rocks/Quartzite/Large", "Weight": 3},
                {"Path": "Rock_Formations/Rocks/Quartzite/Moss_Large", "Weight": 3},
                {"Path": "Rock_Formations/Stalactites/Basalt/Floor", "Weight": 2},
                {"Path": "Rock_Formations/Rocks/Sandstone/Large", "Weight": 1},
            ], mesh_scale_x=12, mesh_scale_z=12, skip_chance=0.30, occurrence_max=0.15,
               placement="ocean_floor", scanner_max_y=60),

            # ─── DEEP CORAL CLUSTERS (dense colorful reef formations) ───
            # Additional coral layer for reef density — glowing coral + chalk + calcite.
            PropLayer("deep_coral", [
                {"Path": "Plants/Jungle/Coral/Glow", "Weight": 5},
                {"Path": "Rock_Formations/Rocks/Chalk/Small", "Weight": 3},
                {"Path": "Rock_Formations/Rocks/Calcite/Small", "Weight": 3},
                {"Path": "Rock_Formations/Rocks/Geodes/White", "Weight": 2},
                {"Path": "Rock_Formations/Rocks/Geodes/Pink", "Weight": 2},
            ], mesh_scale_x=5, mesh_scale_z=5, skip_chance=0.15, occurrence_max=0.30,
               placement="ocean_floor", scanner_max_y=60),

            # ─── CRYSTAL ACCENTS (bright geode highlights across reef) ───
            # All geode colors for maximum chromatic variety on the seabed.
            PropLayer("crystal_accents", [
                {"Path": "Rock_Formations/Rocks/Geodes/Cyan", "Weight": 3},
                {"Path": "Rock_Formations/Rocks/Geodes/Green", "Weight": 3},
                {"Path": "Rock_Formations/Rocks/Geodes/Blue", "Weight": 2},
                {"Path": "Rock_Formations/Rocks/Geodes/Pink", "Weight": 2},
                {"Path": "Rock_Formations/Rocks/Geodes/White", "Weight": 2},
                {"Path": "Rock_Formations/Rocks/Geodes/Purple", "Weight": 1},
            ], mesh_scale_x=6, mesh_scale_z=6, skip_chance=0.20, occurrence_max=0.25,
               placement="ocean_floor", scanner_max_y=60),
        ],
    ),
    # ─── JUNGLE: "The Feran Wilds" ───
    # Dense, suffocating multi-layer jungle with three distinct FieldFunction zones:
    #   Zone A [-1.0, -0.2] = Bamboo Thicket — dense bamboo stands, ferns, earthy floor
    #   Zone B [-0.2, 0.4]  = Ancient Canopy — massive Banyan/Wisteria/Oak, flowers, mushrooms
    #   Zone C [0.4, 1.0]   = Bioluminescent Grove — geodes, glowing coral, purple mushrooms
    # Feran beast-tribe as hostile faction (parallels Trork→Forest, Outlander→Tundra).
    # Signature: elevated_center (sacred Feran mound). Unique features: mossy fallen logs,
    # Flower/Hedera arches, vine prefabs, jungle-specific rocks/pillars, native jungle grass.
    BiomeSpec(
        name="Jungle",
        biome_prefix="Realm_Jungle",
        surface_materials=["Soil_Grass_Full", "Soil_Mud"],
        sub_material="Soil_Mud", deep_material="Rock_Stone",
        spawn_platform_material="Rock_Stone",  # Soil_Mud is in surface_materials
        environment="Env_Zone1_Plains",
        fog_distance=[-35, 60], fog_color="#1e4a1a", sky_top_color="#2a5a20ff", sky_bottom_color="#0a2a05ff", fog_density=0.75,
        particle_id="Dust_Cave_Flies", particle_color="#30a020",
        tint_colors=["#006400", "#004000", "#007800", "#005500"],
        noise_amplitude=0.30, noise_scale=20, noise_octaves=3, noise_persistence=0.40,
        boundary_type="rising", boundary_transition=0.25, boundary_density=5, boundary_height=15,
        # Sacred Feran mound — central elevated area where chieftain's camp sits
        signature_type="elevated_center",
        # Beaten earth trails — Feran patrol routes worn into the jungle floor
                # Geological strata — exposed roots, mud, and rock in arena walls
        wall_strata=[
            {"top_y": 73, "bottom_y": 71, "material": "Soil_Mud"},
            {"top_y": 66, "bottom_y": 63, "material": "Rock_Basalt"},
            {"top_y": 57, "bottom_y": 55, "material": "Rock_Stone"},
        ],
        # Dense jungle ground cover — native jungle grass + ferns + rare orchids
        grass_blocks=[
            {"block": "Plant_Grass_Jungle", "weight": 25},
            {"block": "Plant_Grass_Jungle_Short", "weight": 20},
            {"block": "Plant_Grass_Jungle_Tall", "weight": 15},
            {"block": "Plant_Fern_Jungle", "weight": 10},
            {"block": "Plant_Grass_Lush_Short", "weight": 10},
            {"block": "Plant_Flower_Orchid_Purple", "weight": 2},
            {"block": "Plant_Flower_Tall_Pink", "weight": 1},
            {"block": "Plant_Flower_Tall_Purple", "weight": 1},
        ],
        grass_noise_scale=25, grass_seed="jungle_floor",
        # Jungle is the DENSEST biome — ground cover everywhere
        grass_skip_sparse=0.85, grass_skip_moderate=0.40, grass_skip_dense=0.10,
        # Material variation noise for Soil_Grass_Full / Soil_Mud surface zones
        mat_noise_scale=60, mat_noise_seed="jungle_terrain",

        prop_layers=[
            # ═══════════════════════════════════════════════════════════
            # PRIMARY CANOPY — Zone-based (FieldFunction)
            # Three zones create distinct jungle regions:
            #   Bamboo Thicket (A): dense vertical bamboo stands
            #   Ancient Canopy (B): massive spreading trees
            #   Bioluminescent Grove (C): geode-studded clearings
            # ═══════════════════════════════════════════════════════════

            # ─── TREES: Zone-varied canopy (all sizes) ───
            PropLayer("trees", [], mesh_scale_x=5, mesh_scale_z=5, skip_chance=0.15,
                      occurrence_max=0.35, zones=[
                # Zone A: Bamboo Thicket — dense vertical stands
                PropZone(-1.0, -0.2, [
                    {"Path": "Trees/Bamboo/Stage_3", "Weight": 5},
                    {"Path": "Trees/Bamboo/Stage_2", "Weight": 3},
                    {"Path": "Trees/Bamboo/Stage_1", "Weight": 2},
                ]),
                # Zone B: Ancient Canopy — massive spreading trees
                PropZone(-0.2, 0.4, [
                    {"Path": "Trees/Banyan/Stage_3", "Weight": 4},
                    {"Path": "Trees/Wisteria/Stage_3", "Weight": 3},
                    {"Path": "Trees/Wisteria/Stage_2", "Weight": 2},
                    {"Path": "Trees/Oak/Stage_4", "Weight": 3},
                    {"Path": "Trees/Camphor/Stage_3", "Weight": 2},
                ]),
                # Zone C: Bioluminescent Grove — sparser canopy, light reaches floor
                PropZone(0.4, 1.0, [
                    {"Path": "Trees/Wisteria/Stage_1", "Weight": 3},
                    {"Path": "Trees/Willow/Stage_2", "Weight": 2},
                    {"Path": "Trees/Palm/Stage_3", "Weight": 2},
                ]),
            ], zone_noise_scale=70, zone_seed="jungle_zones"),

            # ─── UNDERSTORY TREES: Mid-layer smaller trees (all sizes) ───
            PropLayer("understory_trees", [
                {"Path": "Trees/Bamboo/Stage_0", "Weight": 3},
                {"Path": "Trees/Wisteria/Stage_1", "Weight": 2},
                {"Path": "Trees/Banyan/Stage_1", "Weight": 2},
                {"Path": "Trees/Palm/Stage_2", "Weight": 1},
            ], mesh_scale_x=7, mesh_scale_z=7, skip_chance=0.3, occurrence_max=0.25),

            # ─── UNDERGROWTH: Bushes sunk into ground (all sizes) ───
            PropLayer("undergrowth", [
                {"Path": "Plants/Bush/Jungle", "Weight": 4},
                {"Path": "Plants/Bush/Lush", "Weight": 3},
                {"Path": "Plants/Bush/Green", "Weight": 2},
                {"Path": "Plants/Bush/Brambles", "Weight": 1},
            ], mesh_scale_x=6, mesh_scale_z=6, skip_chance=0.25, occurrence_max=0.25,
               scanner_max_y=8, sink_into_ground=True),

            # ─── FERN CARPET: Dense fern layer (all sizes) ───
            PropLayer("fern_carpet", [
                {"Path": "Plants/Jungle/Ferns/Large", "Weight": 4},
                {"Path": "Plants/Jungle/Ferns/Small", "Weight": 5},
                {"Path": "Plants/Jungle/Ferns/Island", "Weight": 2},
            ], mesh_scale_x=5, mesh_scale_z=5, skip_chance=0.25, occurrence_max=0.30,
               scanner_max_y=8, sink_into_ground=True),

            # ─── JUNGLE FLOWERS: Zone-based color clusters (all sizes) ───
            # Zone B: Rich tropical flowers in the ancient canopy understory
            # Zone C: Glowing coral and crystal accents in bioluminescent groves
            PropLayer("jungle_flowers", [], mesh_scale_x=8, mesh_scale_z=8, skip_chance=0.4,
                      occurrence_max=0.20, zones=[
                PropZone(-0.2, 0.4, [
                    {"Path": "Plants/Jungle/Flowers/Auburn", "Weight": 2},
                    {"Path": "Plants/Jungle/Flowers/Blue", "Weight": 2},
                    {"Path": "Plants/Jungle/Flowers/Orange", "Weight": 2},
                    {"Path": "Plants/Jungle/Flowers/Pink", "Weight": 2},
                    {"Path": "Plants/Jungle/Flowers/Purple", "Weight": 2},
                    {"Path": "Plants/Jungle/Flowers/Red", "Weight": 2},
                ]),
                PropZone(0.4, 1.0, [
                    {"Path": "Plants/Jungle/Coral/Glow", "Weight": 4},
                    {"Path": "Plants/Jungle/Flowers/Blue", "Weight": 2},
                    {"Path": "Plants/Jungle/Flowers/Purple", "Weight": 2},
                ]),
            ], zone_noise_scale=70, zone_seed="jungle_zones"),

            # ─── MOSSY FALLEN LOGS: Rotting logs on jungle floor (NEW — never used) ───
            # These ancient logs add vertical variety to the flat jungle floor.
            # Mushroom-cap variants create natural fungal ecosystems on dead wood.
            PropLayer("mossy_logs", [
                {"Path": "Trees/Logs/Oak/Moss", "Weight": 4},
                {"Path": "Trees/Logs/Beech/Moss", "Weight": 3},
                {"Path": "Trees/Logs/Birch/Moss", "Weight": 2},
                {"Path": "Trees/Logs/Aspen/Moss", "Weight": 2},
                {"Path": "Trees/Logs/Oak/Mushroom_Cap_Brown", "Weight": 2},
                {"Path": "Trees/Logs/Oak/Mushroom_Cap_Red", "Weight": 1},
            ], mesh_scale_x=14, mesh_scale_z=14, skip_chance=0.35, occurrence_max=0.18,
               scale_with_radius=False, scanner_max_y=8),

            # ─── JUNGLE ROCKS: Zone-based formations (all sizes) ───
            PropLayer("jungle_rocks", [], mesh_scale_x=12, mesh_scale_z=12, skip_chance=0.4,
                      occurrence_max=0.25, zones=[
                # Zone A: Earthy rocks in bamboo thickets
                PropZone(-1.0, -0.2, [
                    {"Path": "Rock_Formations/Rocks/Grass/Small", "Weight": 3},
                    {"Path": "Rock_Formations/Rocks/Stone/Small", "Weight": 2},
                    {"Path": "Rock_Formations/Rocks/Quartzite/Small", "Weight": 1},
                ]),
                # Zone B: Jungle-specific moss-covered formations
                PropZone(-0.2, 0.4, [
                    {"Path": "Rock_Formations/Rocks/Jungle", "Weight": 5},
                    {"Path": "Rock_Formations/Rocks/Grass/Small", "Weight": 2},
                    {"Path": "Rock_Formations/Rocks/Quartzite/Large", "Weight": 1},
                ]),
                # Zone C: Crystal geodes and mystical stones
                PropZone(0.4, 1.0, [
                    {"Path": "Rock_Formations/Rocks/Geodes/Green", "Weight": 3},
                    {"Path": "Rock_Formations/Rocks/Geodes/Purple", "Weight": 3},
                    {"Path": "Rock_Formations/Rocks/Geodes/Blue", "Weight": 2},
                    {"Path": "Rock_Formations/Rocks/Geodes/Cyan", "Weight": 2},
                    {"Path": "Rock_Formations/Rocks/Geodes/Pink", "Weight": 1},
                ]),
            ], zone_noise_scale=70, zone_seed="jungle_zones"),

            # ─── MUSHROOM GROVES: Large fungal formations (all sizes) ───
            PropLayer("mushroom_groves", [
                {"Path": "Plants/Mushroom_Large/Green/Stage_1", "Weight": 3},
                {"Path": "Plants/Mushroom_Large/Green/Stage_3", "Weight": 2},
                {"Path": "Plants/Mushroom_Large/Purple/Stage_1", "Weight": 2},
                {"Path": "Plants/Mushroom_Large/Purple/Stage_3", "Weight": 1},
                {"Path": "Plants/Mushroom_Large/Yellow/Stage_1", "Weight": 2},
                {"Path": "Plants/Mushroom_Large/Yellow/Stage_3", "Weight": 1},
            ], mesh_scale_x=12, mesh_scale_z=12, skip_chance=0.4, occurrence_max=0.20),

            # ─── VINE TANGLES: Hanging vine prefabs (NEW — never used in any biome) ───
            PropLayer("vines", [
                {"Path": "Plants/Vines/Green", "Weight": 4},
                {"Path": "Plants/Vines/Green_Hanging", "Weight": 3},
            ], mesh_scale_x=10, mesh_scale_z=10, skip_chance=0.4, occurrence_max=0.18),

            # ─── MUSHROOM RINGS: Rare magical clearings (all sizes) ───
            PropLayer("mushroom_rings", [
                {"Path": "Plants/Mushroom_Rings", "Weight": 1},
            ], mesh_scale_x=40, mesh_scale_z=40, skip_chance=0.5, occurrence_max=0.05,
               scale_with_radius=False, scanner_max_y=8),

            # ═══════════════════════════════════════════════════════════
            # LANDMARK FEATURES — Rare but dramatic
            # ═══════════════════════════════════════════════════════════

            # ─── JUNGLE ARCHES: Flower + Hedera arches (NEW — never used!) ───
            # Flower arches: organic stone with flowering vegetation (10 prefabs)
            # Hedera arches: ivy/vine-covered stone arches (jungle atmosphere)
            PropLayer("jungle_arches", [
                {"Path": "Rock_Formations/Arches/Flower", "Weight": 3},
                {"Path": "Rock_Formations/Arches/Hedera", "Weight": 3},
                {"Path": "Rock_Formations/Arches/Forest", "Weight": 1},
            ], mesh_scale_x=50, mesh_scale_z=50, skip_chance=0.5, occurrence_max=0.05,
               scale_with_radius=False, scanner_max_y=8),

            # ─── JUNGLE PILLARS: Dedicated jungle rock columns (NEW) ───
            PropLayer("jungle_pillars", [
                {"Path": "Rock_Formations/Pillars/Jungle", "Weight": 1},
            ], mesh_scale_x=40, mesh_scale_z=40, skip_chance=0.5, occurrence_max=0.08,
               scale_with_radius=False, scanner_max_y=8),

            # NOTE: ALL faction structures (Feran) are placed at runtime by
            # RealmStructurePlacer, NOT in WorldGen prop layers. This is the CORRECT
            # architecture because:
            #   1. WorldGen has NO cross-layer collision detection — structures from
            #      different prop layers can overlap each other freely
            #   2. RealmStructurePlacer uses StructureBoundsRegistry with safety margins,
            #      guaranteeing no overlaps and minimum spacing between ALL structures
            #   3. Runtime placement uses multi-point ground sampling for proper Y positioning
            # Same pattern as Desert (Monuments via RealmStructurePlacer, not WorldGen).
            # Complex Feran Layouts → BossStructurePlacer with spawner expansion.
        ],
    ),

    # ─── TEXTURED BARRIER biomes (steep wall with 3D noise for craggy surface) ───
    # ─── VOLCANO: "The Volcanic Caldera" ───
    # Unique WorldGen V2 feature: PondFiller (auto-fills terrain depressions with lava).
    # Zone system: Cooled (burnt trees) / Active (basalt columns) / Infernal (crystals, lava).
    # Terrain: Craggy (octaves=3, persistence=0.4), textured 3D-noise walls, lava moat below.
    # Player experience: First endgame gate (Level 50+, 1.5x difficulty). Hostile, dramatic.
    # No NPC faction — follows Desert model (geological progression + RealmStructurePlacer).
    BiomeSpec(
        name="Volcano",
        biome_prefix="Realm_Volcano",
        surface_materials=["Rock_Volcanic"],
        sub_material="Rock_Basalt", deep_material="Rock_Volcanic",
        spawn_platform_material="Rock_Basalt",
        environment="Env_Zone4_Burning_Sands",
        fog_distance=[-40, 60], fog_color="#4a1010", sky_top_color="#2a0808ff", sky_bottom_color="#6a2020ff", fog_density=0.8, color_filter="#ff8060",
        particle_id="Ash", particle_color="#abb3b4",
        screen_effect="ScreenEffects/Fire.png", screen_effect_color="#ffffff0d",
        tint_colors=["#4A0808", "#8B0000", "#6B1010", "#A52020"],
        noise_amplitude=0.25, noise_scale=25, noise_octaves=3, noise_persistence=0.4,
        boundary_type="textured", boundary_transition=0.10, boundary_density=6, boundary_height=25,
        boundary_noise_scale=15, boundary_noise_amp=0.4,
        signature_type="lava_moat", fluid_material="Lava_Source", fluid_bottom_y=-10, fluid_top_y=-2,
        surface_thickness=1, sub_thickness=2,
        mat_noise_scale=60, mat_noise_seed="volcanic_surface",
        # Wall geological strata — visible bands in craggy textured boundary walls
        wall_strata=[
            {"top_y": 85, "bottom_y": 82, "material": "Rock_Basalt"},
            {"top_y": 75, "bottom_y": 72, "material": "Rock_Volcanic"},
        ],
        # Ground cover — sparse scorched vegetation poking through volcanic rock
        grass_blocks=[
            {"block": "Plant_Grass_Sharp", "weight": 5},
            {"block": "Plant_Grass_Sharp_Wild", "weight": 2},
        ],
        grass_noise_scale=35, grass_seed="volcanic_ash",
        grass_skip_sparse=0.97, grass_skip_moderate=0.92, grass_skip_dense=0.80,

        prop_layers=[
            # ═══════════════════════════════════════════════════════════
            # VOLCANIC LANDSCAPE — Zone-based terrain features (all sizes)
            # Zone noise seed "volcano_zones" creates 3 organic regions:
            #   [-1, -0.2] = Cooled Zone (trees survive, vegetation)
            #   [-0.2, 0.3] = Active Zone (basalt columns, geological)
            #   [0.3, 1.0]  = Infernal Zone (crystals, lava proximity)
            # ═══════════════════════════════════════════════════════════

            # ─── MAJOR ROCK FORMATIONS: Zone-varied (all sizes) ───
            PropLayer("volcanic_formations", [], mesh_scale_x=12, mesh_scale_z=12, skip_chance=0.25,
                      occurrence_max=0.28, zones=[
                PropZone(-1.0, -0.2, [
                    {"Path": "Rock_Formations/Rocks/Volcanic/Large", "Weight": 3},
                    {"Path": "Rock_Formations/Rocks/Basalt/Large", "Weight": 2},
                    {"Path": "Rock_Formations/Rocks/Basalt/Hexagon", "Weight": 2},
                ]),
                PropZone(-0.2, 0.3, [
                    {"Path": "Rock_Formations/Rocks/Basalt/Hexagon", "Weight": 4},
                    {"Path": "Rock_Formations/Rocks/Volcanic/Spiked/Large", "Weight": 2},
                    {"Path": "Rock_Formations/Rocks/Volcanic/Large", "Weight": 2},
                ]),
                PropZone(0.3, 1.0, [
                    {"Path": "Rock_Formations/Rocks/Volcanic/Spiked/Large", "Weight": 3},
                    {"Path": "Rock_Formations/Rocks/Volcanic/Spiked/Small", "Weight": 2},
                    {"Path": "Rock_Formations/Rocks/Volcanic/Large", "Weight": 1},
                ]),
            ], zone_noise_scale=80, zone_seed="volcano_zones"),

            # ─── SCORCHED VEGETATION: Zone-based (all sizes) ───
            # Cooled zone: burnt trees + lavathorn. Hot zone: sparse lavathorn only.
            PropLayer("scorched_vegetation", [], mesh_scale_x=8, mesh_scale_z=8, skip_chance=0.25,
                      occurrence_max=0.30, zones=[
                PropZone(-1.0, 0.0, [
                    {"Path": "Trees/Burnt/Stage_1", "Weight": 3},
                    {"Path": "Trees/Burnt/Stage_2", "Weight": 2},
                    {"Path": "Plants/Bush/Dead_Lavathorn", "Weight": 4},
                    {"Path": "Plants/Twisted_Wood/Fire", "Weight": 1},
                ]),
                PropZone(0.0, 0.6, [
                    {"Path": "Plants/Bush/Dead_Lavathorn", "Weight": 3},
                    {"Path": "Plants/Bush/Arid", "Weight": 2},
                    {"Path": "Plants/Bush/Arid_Red", "Weight": 1},
                ]),
            ], zone_noise_scale=80, zone_seed="volcano_zones"),

            # ─── UNDERGROWTH: Bushes sunk into ground (all sizes) ───
            # Matches Desert/Tundra pattern: mesh=8, skip=0.5, occurrence=0.20
            PropLayer("undergrowth", [
                {"Path": "Plants/Bush/Dead_Lavathorn", "Weight": 4},
                {"Path": "Plants/Bush/Arid", "Weight": 3},
                {"Path": "Plants/Bush/Arid_Red", "Weight": 1},
            ], mesh_scale_x=8, mesh_scale_z=8, skip_chance=0.5, occurrence_max=0.20,
               scanner_max_y=8, sink_into_ground=True),

            # ─── GROUND DETAIL: Dense small rocks filling the floor (all sizes) ───
            PropLayer("ground_rocks", [
                {"Path": "Rock_Formations/Rocks/Basalt/Small", "Weight": 3},
                {"Path": "Rock_Formations/Rocks/Volcanic/Spiked/Small", "Weight": 2},
                {"Path": "Rock_Formations/Rocks/Shale/Small", "Weight": 1},
            ], mesh_scale_x=8, mesh_scale_z=8, skip_chance=0.35, occurrence_max=0.28),

            # ═══════════════════════════════════════════════════════════
            # CRYSTAL DEPOSITS (R35+ — mineral formations in hot zones)
            # ═══════════════════════════════════════════════════════════
            PropLayer("crystal_deposits", [], mesh_scale_x=14, mesh_scale_z=14, skip_chance=0.35,
                      occurrence_max=0.20, min_arena_radius=35, zones=[
                PropZone(-0.3, 0.3, [
                    {"Path": "Rock_Formations/Stalactites/Basalt/Floor", "Weight": 3},
                    {"Path": "Rock_Formations/Rocks/Geodes/Pink", "Weight": 2},
                ]),
                PropZone(0.3, 1.0, [
                    {"Path": "Rock_Formations/Rocks/Geodes/Pink", "Weight": 3},
                    {"Path": "Rock_Formations/Rocks/Geodes/Purple", "Weight": 3},
                    {"Path": "Rock_Formations/Rocks/Geodes/Blue", "Weight": 2},
                    {"Path": "Rock_Formations/Rocks/Geodes/Cyan", "Weight": 1},
                    {"Path": "Rock_Formations/Stalactites/Basalt/Floor", "Weight": 2},
                ]),
            ], zone_noise_scale=80, zone_seed="volcano_zones"),

            # ═══════════════════════════════════════════════════════════
            # VOLCANIC ARCHES (R45+ — Savannah arches as volcanic landmarks)
            # Hot climate arches fit the scorched volcanic landscape.
            # ═══════════════════════════════════════════════════════════
            PropLayer("volcanic_arches", [
                {"Path": "Rock_Formations/Arches/Savannah", "Weight": 1},
            ], mesh_scale_x=50, mesh_scale_z=50, skip_chance=0.4,
               occurrence_max=0.06, min_arena_radius=45,
               scale_with_radius=False, scanner_max_y=8),

            # PondFiller REMOVED — server 2026.03.26 rejects biomes containing
            # PondFiller prop type (entire biome fails to register as asset).
            # See docs/reference/confirmed-dead-approaches.md

            # ═══════════════════════════════════════════════════════════
            # BASALT COLUMNS (R55+ — dramatic hexagonal landmarks)
            # Fixed spacing — bigger arenas get MORE columns.
            # ═══════════════════════════════════════════════════════════
            PropLayer("basalt_columns", [
                {"Path": "Rock_Formations/Rocks/Basalt/Hexagon", "Weight": 4},
                {"Path": "Rock_Formations/Rocks/Basalt/Large", "Weight": 1},
            ], mesh_scale_x=30, mesh_scale_z=30, skip_chance=0.40,
               occurrence_max=0.12, min_arena_radius=55,
               scale_with_radius=False, scanner_max_y=8),

            # ═══════════════════════════════════════════════════════════
            # OBSIDIAN LANDMARKS (R70+ — rare dramatic volcanic spikes)
            # ═══════════════════════════════════════════════════════════
            PropLayer("obsidian_landmarks", [
                {"Path": "Rock_Formations/Rocks/Volcanic/Spiked/Large", "Weight": 2},
                {"Path": "Rock_Formations/Rocks/Volcanic/Large", "Weight": 1},
            ], mesh_scale_x=40, mesh_scale_z=40, skip_chance=0.5,
               occurrence_max=0.08, min_arena_radius=70,
               scale_with_radius=False, scanner_max_y=8),

            # ═══════════════════════════════════════════════════════════
            # FIRE ACCENTS (R90+ — Twisted Wood/Fire dramatic pieces)
            # ═══════════════════════════════════════════════════════════
            PropLayer("fire_accents", [
                {"Path": "Plants/Twisted_Wood/Fire", "Weight": 1},
            ], mesh_scale_x=35, mesh_scale_z=35, skip_chance=0.50,
               occurrence_max=0.10, min_arena_radius=90,
               scale_with_radius=False, scanner_max_y=8),
        ],
    ),
    # ─── MOUNTAINS: "The Goblin Mines" — Labyrinth carved from solid stone ───
    # UNIQUE BIOME: The only realm where structures CARVE INTO terrain instead of
    # being placed ON terrain. WorldGen creates a solid stone mass with ceiling.
    # RealmLabyrinthPlacer (runtime) carves Goblin_Lair corridors with force=true.
    # WorldGen props here are sparse — only decorate exposed surfaces (spawn clearing,
    # boundary walls, ceiling). The labyrinth prefabs provide interior detail.
    #
    # Differentiates from Caverns: artificial mine (flat floor, no 3D noise, warm amber)
    # vs natural crystal cave (organic 3D noise, bioluminescent blue).
    # Goblin faction is EXCLUSIVE to Mountains — appears in no other biome.
    BiomeSpec(
        name="Mountains",
        biome_prefix="Realm_Mountains",
        # ═══ MATERIALS ═══
        # Rock_Stone primary, Rock_Basalt secondary — two-material FieldFunction zones
        # Creates visual variety in corridor walls (stone areas vs basalt areas)
        surface_materials=["Rock_Stone", "Rock_Basalt"],
        sub_material="Rock_Slate", deep_material="Rock_Stone",
        spawn_platform_material="Rock_Sandstone",  # Distinct spawn area floor
        # ═══ ATMOSPHERE ═══
        # Underground mine — dusty amber fog, warm torchlight feel
        # Much tighter fog than Caverns (mine corridors, not cathedral cave)
        environment="Zone1_Underground",
        fog_distance=[10, 35], fog_color="#1a1510", sky_top_color="#0a0808ff", sky_bottom_color="#1a1210ff", fog_density=0.80,
        particle_id="Dust_Cave_Flies", particle_color="#a08040",  # Amber mine dust
        tint_colors=["#3a2a1a", "#2a1a0a", "#4a3a2a"],  # Warm brown mine tones
        # ═══ TERRAIN NOISE ═══
        # Nearly flat — mines are leveled by goblin workers
        # Low amplitude prevents uneven floors after labyrinth carving
        noise_amplitude=0.05, noise_scale=40, noise_octaves=1,
        # ═══ BOUNDARY ═══
        # Textured mine walls — 3D noise for craggy rock face
        # Denser than Caverns (must contain labyrinth solidly)
        boundary_type="textured", boundary_transition=0.12, boundary_density=7, boundary_height=30,
        boundary_noise_scale=10, boundary_noise_amp=0.45,  # Smaller scale = rougher "mined" look
        # ═══ CEILING ═══
        # Lower than Caverns (95 vs 110) — claustrophobic mine shafts
        # No 3D volumetric noise — mines are structured, not organic
        # THIRD TERRAIN PARADIGM: Solid mass — entire volume is stone, corridors carved at runtime.
        # Unlike "open" (floor + air + walls) or "enclosed" (floor + air + ceiling + walls),
        # this fills the volume from Y=0 to ceiling_y with solid terrain. A small spawn clearing
        # at the center provides the initial open space. Everything else is carved by
        # RealmLabyrinthPlacer at runtime using force=true paste.
        arena_type="solid_mass",
        signature_type="ceiling", ceiling_y=95, ceiling_edge_y=80,
        # cave_3d_noise intentionally 0 — all interior detail from labyrinth prefabs
        surface_thickness=1, sub_thickness=2,
        mat_noise_scale=50, mat_noise_seed="mine_mats",
        # ═══ WALL STRATA ═══
        # Geological bands visible in boundary walls — tells the mining story
        wall_strata=[
            {"top_y": 88, "bottom_y": 86, "material": "Rock_Basalt"},     # Upper basalt seam
            {"top_y": 80, "bottom_y": 77, "material": "Rock_Shale"},      # Middle shale band
            {"top_y": 72, "bottom_y": 69, "material": "Rock_Slate"},      # Lower slate deposit
            {"top_y": 62, "bottom_y": 60, "material": "Rock_Sandstone"},  # Deep sandstone
        ],
        # ═══ GROUND COVER ═══
        # Sparse mine moss — only visible in spawn clearing and exposed surfaces
        # Much sparser than Caverns (this is a worked mine, not natural cave)
        grass_blocks=[
            {"block": "Plant_Moss_Block_Green", "weight": 20},
            {"block": "Plant_Moss_Green_Dark", "weight": 15},
            {"block": "Plant_Fern", "weight": 5},  # Unverified — fallback: Plant_Moss_Rug_Lime
        ],
        grass_noise_scale=25, grass_seed="mine_moss",
        grass_skip_sparse=0.95, grass_skip_moderate=0.80, grass_skip_dense=0.50,

        prop_layers=[
            # ═══════════════════════════════════════════════════════════
            # FLOOR PROPS — Spawn clearing + exposed surfaces
            # Sparse by design: labyrinth prefabs provide interior detail.
            # These only decorate WorldGen-exposed terrain (spawn area,
            # boundary walls, ceiling), NOT the carved corridors.
            # ═══════════════════════════════════════════════════════════

            # ─── MINE ROCKS: Stone scatter on exposed floor ───
            PropLayer("mine_rocks", [
                {"Path": "Rock_Formations/Rocks/Stone/Small", "Weight": 4},
                {"Path": "Rock_Formations/Rocks/Basalt/Small", "Weight": 3},
                {"Path": "Rock_Formations/Rocks/Shale/Small", "Weight": 3},
                {"Path": "Rock_Formations/Rocks/Slate/Small", "Weight": 2},
            ], mesh_scale_x=9, mesh_scale_z=9, skip_chance=0.40, occurrence_max=0.25),

            # ─── MINERAL DEPOSITS: Zone-based geodes and crystal nodes ───
            # Crystal veins in basalt zones, plain rock in stone zones
            # These are what the goblins are mining for
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
            ], zone_noise_scale=60, zone_seed="mine_zones"),

            # ─── STALAGMITES: Natural cave remnants goblins mined around ───
            PropLayer("stalagmites_floor", [
                {"Path": "Rock_Formations/Stalactites/Basalt/Floor", "Weight": 4},
                {"Path": "Rock_Formations/Rocks/Basalt/Hexagon", "Weight": 2},
                {"Path": "Rock_Formations/Rocks/Shale/Large", "Weight": 1},
            ], mesh_scale_x=18, mesh_scale_z=18, skip_chance=0.45, occurrence_max=0.15,
               scale_with_radius=False, scanner_max_y=8),

            # ─── MINE FLOOR DETAIL: Dense small scatter filling gaps ───
            PropLayer("mine_floor_detail", [
                {"Path": "Rock_Formations/Rocks/Stone/Small", "Weight": 3},
                {"Path": "Rock_Formations/Rocks/Slate/Small", "Weight": 2},
                {"Path": "Rock_Formations/Rocks/Shale/Small", "Weight": 2},
                {"Path": "Rock_Formations/Rocks/Calcite/Small", "Weight": 1},
            ], mesh_scale_x=7, mesh_scale_z=7, skip_chance=0.45, occurrence_max=0.20),

            # ═══════════════════════════════════════════════════════════
            # CEILING PROPS — Mine roof formations
            # Uses Ceiling pattern + upward ColumnLinear scanner
            # (same proven pattern as Caverns ceiling props)
            # ═══════════════════════════════════════════════════════════

            # ─── MINE STALACTITES: Zone-based ceiling formations ───
            # Crystal stalactites in mineral-rich basalt zones, plain basalt elsewhere
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
            ], zone_noise_scale=60, zone_seed="mine_zones"),

            # ─── CRYSTAL CEILING: Rare crystal clusters in mineral zones ───
            PropLayer("crystal_ceiling", [], mesh_scale_x=14, mesh_scale_z=14, skip_chance=0.40,
                      occurrence_max=0.20, placement="ceiling", zones=[
                PropZone(-1.0, -0.1, [
                    {"Path": "Rock_Formations/Stalactites/Basalt_Crystal_Blue/Ceiling", "Weight": 4},
                    {"Path": "Rock_Formations/Stalactites/Basalt_Crystal_Purple/Ceiling", "Weight": 3},
                    {"Path": "Rock_Formations/Stalactites/Basalt_Crystal_Red/Ceiling", "Weight": 2},
                ]),
            ], zone_noise_scale=60, zone_seed="mine_zones"),

            # ═══════════════════════════════════════════════════════════
            # LANDMARKS — Rare hexagonal basalt columns (natural mine supports)
            # ═══════════════════════════════════════════════════════════
            PropLayer("basalt_columns", [
                {"Path": "Rock_Formations/Rocks/Basalt/Hexagon", "Weight": 3},
                {"Path": "Rock_Formations/Rocks/Basalt/Large", "Weight": 1},
            ], mesh_scale_x=40, mesh_scale_z=40, skip_chance=0.5, occurrence_max=0.06,
               scale_with_radius=False, scanner_max_y=8),

            # NOTE: ALL Goblin Lair structures (corridors, rooms, houses, boss room)
            # are placed at runtime by RealmLabyrinthPlacer with force=true, carving
            # corridors from this solid terrain. WorldGen has NO knowledge of the
            # labyrinth — it just creates the stone mass that gets carved into.
            # PrefabSpawnerBlock expansion is selective: only Houses/* and Mushrooms/*
            # paths are expanded. All corridor/room spawner blocks are stripped.
            # See docs/WorldgenV2/LABYRINTH_GENERATION_SYSTEM.md for full design.
        ],
    ),
    BiomeSpec(
        name="Caverns",
        biome_prefix="Realm_Caverns",
        surface_materials=["Rock_Stone", "Rock_Shale", "Rock_Basalt"],
        sub_material="Rock_Stone", deep_material="Rock_Stone",
        spawn_platform_material="Rock_Sandstone",
        environment="Zone1_Underground",
        fog_distance=[15, 45], fog_color="#0a0a1a", sky_top_color="#050510ff", sky_bottom_color="#0a0a20ff", fog_density=0.85,
        particle_id="Dust_Cave_Flies", particle_color="#3060c0",
        tint_colors=["#1a2a4a", "#0a1a30", "#2a3a5a"],
        noise_amplitude=0.15, noise_scale=35, noise_octaves=2,
        boundary_type="textured", boundary_transition=0.15, boundary_density=6, boundary_height=35,
        boundary_noise_scale=12, boundary_noise_amp=0.5,
        # ═══ THREE NEW WORLDGENV2 FEATURES ═══
        # Feature 1: Cave ceiling — dome-shaped roof creating enclosed chamber
        signature_type="ceiling", ceiling_y=110, ceiling_edge_y=90,
        # Feature 2: SimplexNoise3D volumetric — pillars (positive) + alcoves (negative)
        cave_3d_noise_scale_xz=20, cave_3d_noise_scale_y=15,
        cave_3d_noise_amp=0.35, cave_3d_seed="cavern_pillars",
        # Feature 3: Ceiling prop placement — stalactites, crystals, mushrooms hanging from roof
        # (activated via placement="ceiling" on prop layers below)
        surface_thickness=1, sub_thickness=2,
        mat_noise_scale=60, mat_noise_seed="cave_mats",
        # Geological strata — dark bands visible in boundary walls
        wall_strata=[
            {"top_y": 80, "bottom_y": 78, "material": "Rock_Shale"},
            {"top_y": 72, "bottom_y": 69, "material": "Rock_Slate"},
            {"top_y": 60, "bottom_y": 57, "material": "Rock_Basalt"},
        ],
        # Cave moss ground cover — organic patches of bioluminescent growth
        grass_blocks=[
            {"block": "Plant_Moss_Block_Green", "weight": 30},
            {"block": "Plant_Moss_Green_Dark", "weight": 25},
            {"block": "Plant_Moss_Rug_Lime", "weight": 15},
            {"block": "Plant_Fern", "weight": 10},
            {"block": "Plant_Fern_Wet_Big", "weight": 5},
        ],
        grass_noise_scale=25, grass_seed="cave_moss",
        grass_skip_sparse=0.90, grass_skip_moderate=0.55, grass_skip_dense=0.20,

        prop_layers=[
            # ═══════════════════════════════════════════════════════════
            # FLOOR PROPS — Natural geological & organic cave environment
            # ═══════════════════════════════════════════════════════════

            # ─── STALAGMITES: Zone-based (FieldFunction) ───
            # Zone A (Crystal): blue/purple crystal formations
            # Zone B (Stone): basalt/shale stone columns
            # Zone C (Fungal): mushroom stalagmites
            PropLayer("stalagmites", [], mesh_scale_x=8, mesh_scale_z=8, skip_chance=0.25,
                      occurrence_max=0.30, zones=[
                PropZone(-1.0, -0.2, [
                    {"Path": "Rock_Formations/Stalactites/Basalt/Floor", "Weight": 4},
                    {"Path": "Rock_Formations/Rocks/Geodes/Blue", "Weight": 3},
                    {"Path": "Rock_Formations/Rocks/Geodes/Purple", "Weight": 2},
                    {"Path": "Rock_Formations/Rocks/Geodes/Cyan", "Weight": 2},
                ]),
                PropZone(-0.2, 0.3, [
                    {"Path": "Rock_Formations/Stalactites/Basalt/Floor", "Weight": 4},
                    {"Path": "Rock_Formations/Rocks/Shale/Large", "Weight": 3},
                    {"Path": "Rock_Formations/Rocks/Basalt/Large", "Weight": 2},
                    {"Path": "Rock_Formations/Rocks/Basalt/Hexagon", "Weight": 2},
                ]),
                PropZone(0.3, 1.0, [
                    {"Path": "Plants/Mushroom_Large/Green/Stage_1", "Weight": 3},
                    {"Path": "Plants/Mushroom_Large/Green/Stage_3", "Weight": 2},
                    {"Path": "Plants/Mushroom_Large/Purple/Stage_1", "Weight": 2},
                    {"Path": "Plants/Mushroom_Large/Purple/Stage_3", "Weight": 1},
                    {"Path": "Plants/Mushroom_Large/Yellow/Stage_1", "Weight": 2},
                ]),
            ], zone_noise_scale=80, zone_seed="cavern_zones"),

            # ─── CRYSTAL DEPOSITS (zone A only — glowing crystal nodes) ───
            PropLayer("crystal_deposits", [], mesh_scale_x=12, mesh_scale_z=12, skip_chance=0.35,
                      occurrence_max=0.25, zones=[
                PropZone(-1.0, -0.2, [
                    {"Path": "Rock_Formations/Rocks/Geodes/Blue", "Weight": 3},
                    {"Path": "Rock_Formations/Rocks/Geodes/Cyan", "Weight": 3},
                    {"Path": "Rock_Formations/Rocks/Geodes/Purple", "Weight": 2},
                    {"Path": "Rock_Formations/Rocks/Geodes/Pink", "Weight": 1},
                ]),
            ], zone_noise_scale=80, zone_seed="cavern_zones"),

            # ─── LARGE MUSHROOM GROVES (zone C only — bioluminescent garden) ───
            PropLayer("mushroom_groves", [], mesh_scale_x=10, mesh_scale_z=10, skip_chance=0.30,
                      occurrence_max=0.25, zones=[
                PropZone(0.3, 1.0, [
                    {"Path": "Plants/Mushroom_Large/Green/Stage_3", "Weight": 3},
                    {"Path": "Plants/Mushroom_Large/Purple/Stage_3", "Weight": 3},
                    {"Path": "Plants/Mushroom_Large/Yellow/Stage_3", "Weight": 2},
                    {"Path": "Plants/Mushroom_Large/Green/Stage_1", "Weight": 2},
                    {"Path": "Plants/Mushroom_Large/Purple/Stage_1", "Weight": 1},
                ]),
            ], zone_noise_scale=80, zone_seed="cavern_zones"),

            # ─── CAVE ROCKS (all zones — floor scatter) ───
            PropLayer("cave_rocks", [
                {"Path": "Rock_Formations/Rocks/Quartzite/Small", "Weight": 3},
                {"Path": "Rock_Formations/Rocks/Quartzite/Moss_Small", "Weight": 2},
                {"Path": "Rock_Formations/Rocks/Shale/Small", "Weight": 3},
                {"Path": "Rock_Formations/Rocks/Slate/Small", "Weight": 2},
                {"Path": "Rock_Formations/Rocks/Calcite/Small", "Weight": 2},
                {"Path": "Rock_Formations/Rocks/Basalt/Small", "Weight": 2},
            ], mesh_scale_x=10, mesh_scale_z=10, skip_chance=0.35, occurrence_max=0.25),

            # ─── QUARTZITE PILLARS (rare tall natural columns) ───
            PropLayer("quartzite_pillars", [
                {"Path": "Rock_Formations/Rocks/Quartzite/Large", "Weight": 3},
                {"Path": "Rock_Formations/Rocks/Quartzite/Moss_Large", "Weight": 2},
            ], mesh_scale_x=35, mesh_scale_z=35, skip_chance=0.5, occurrence_max=0.08,
               scale_with_radius=False, scanner_max_y=8),

            # ─── CAVE FLOOR DETAIL (dense small scatter) ───
            PropLayer("cave_floor_detail", [
                {"Path": "Rock_Formations/Rocks/Stone/Small", "Weight": 3},
                {"Path": "Rock_Formations/Rocks/Slate/Small", "Weight": 2},
                {"Path": "Rock_Formations/Rocks/Calcite/Small", "Weight": 2},
                {"Path": "Rock_Formations/Rocks/Shale/Small", "Weight": 1},
            ], mesh_scale_x=7, mesh_scale_z=7, skip_chance=0.45, occurrence_max=0.20),

            # ─── MUSHROOM RINGS (rare magical clearings — shared with Forest) ───
            PropLayer("mushroom_rings", [
                {"Path": "Plants/Mushroom_Rings", "Weight": 1},
            ], mesh_scale_x=40, mesh_scale_z=40, skip_chance=0.5, occurrence_max=0.05,
               scale_with_radius=False, scanner_max_y=8),

            # ═══════════════════════════════════════════════════════════
            # CEILING PROPS — Feature 3: First biome to use ceiling placement
            # Uses Ceiling pattern (solid above + empty at position) with
            # ColumnLinear scanner scanning UPWARD (TopDownOrder=false).
            # ═══════════════════════════════════════════════════════════

            # ─── CEILING STALACTITES: Zone-based (matches floor zones) ───
            PropLayer("ceiling_stalactites", [], mesh_scale_x=7, mesh_scale_z=7, skip_chance=0.20,
                      occurrence_max=0.35, placement="ceiling", zones=[
                PropZone(-1.0, -0.2, [
                    {"Path": "Rock_Formations/Stalactites/Basalt/Ceiling", "Weight": 3},
                    {"Path": "Rock_Formations/Stalactites/Basalt_Crystal_Blue/Ceiling", "Weight": 3},
                    {"Path": "Rock_Formations/Stalactites/Basalt_Crystal_Purple/Ceiling", "Weight": 2},
                ]),
                PropZone(-0.2, 0.3, [
                    {"Path": "Rock_Formations/Stalactites/Basalt/Ceiling", "Weight": 5},
                    {"Path": "Rock_Formations/Stalactites/Stone/Ceiling", "Weight": 3},
                ]),
                PropZone(0.3, 1.0, [
                    {"Path": "Rock_Formations/Stalactites/Basalt/Ceiling", "Weight": 3},
                    {"Path": "Rock_Formations/Stalactites/Basalt_Crystal_Red/Ceiling", "Weight": 1},
                ]),
            ], zone_noise_scale=80, zone_seed="cavern_zones"),

            # ─── CRYSTAL CEILING (zone A — hanging crystal formations) ───
            PropLayer("crystal_ceiling", [], mesh_scale_x=10, mesh_scale_z=10, skip_chance=0.35,
                      occurrence_max=0.25, placement="ceiling", zones=[
                PropZone(-1.0, -0.2, [
                    {"Path": "Rock_Formations/Stalactites/Basalt_Crystal_Blue/Ceiling", "Weight": 4},
                    {"Path": "Rock_Formations/Stalactites/Basalt_Crystal_Purple/Ceiling", "Weight": 3},
                    {"Path": "Rock_Formations/Stalactites/Basalt_Crystal_Red/Ceiling", "Weight": 2},
                ]),
            ], zone_noise_scale=80, zone_seed="cavern_zones"),

            # ─── CAVE ROOTS (rare — tree roots hanging from surface above) ───
            PropLayer("cave_roots", [
                {"Path": "Rock_Formations/Stalactites/Basalt/Ceiling", "Weight": 3},
                {"Path": "Rock_Formations/Stalactites/Stone/Ceiling", "Weight": 2},
            ], mesh_scale_x=25, mesh_scale_z=25, skip_chance=0.4, occurrence_max=0.12,
               placement="ceiling", scale_with_radius=False),

            # NOTE: Wall vegetation handled by the grass system (generate_grass_layer)
            # with extended MaxY=35 for ceiling biomes — covers wall slope surfaces.

            # ═══════════════════════════════════════════════════════════
            # LANDMARK FEATURES
            # ═══════════════════════════════════════════════════════════

            # ─── CALCITE TALL PILLARS (dramatic natural columns) ───
            PropLayer("calcite_pillars", [
                {"Path": "Rock_Formations/Rocks/Quartzite/Large", "Weight": 2},
                {"Path": "Rock_Formations/Rocks/Quartzite/Moss_Large", "Weight": 2},
                {"Path": "Rock_Formations/Rocks/Basalt/Large", "Weight": 1},
            ], mesh_scale_x=45, mesh_scale_z=45, skip_chance=0.5, occurrence_max=0.05,
               scale_with_radius=False, scanner_max_y=8),

            # NOTE: Goblin faction has no individual WorldGen-safe structures (all are
            # compound dungeon layouts with PrefabSpawnerBlocks). Caverns uses geological
            # features (Fossils, Hotsprings) via RealmStructurePlacer at runtime instead.
            # Boss lair uses fossil formations via BossStructurePlacer.
        ],
    ),

    # ─── FROZEN CRYPTS: Underground ice tomb (Frost Skeleton exclusive) ───
    BiomeSpec(
        name="Frozen_Crypts",
        biome_prefix="Realm_Frozen_Crypts",
        surface_materials=["Rock_Ice"],
        sub_material="Rock_Ice", deep_material="Rock_Ice",
        spawn_platform_material="Rock_Sandstone",
        environment="Zone1_Underground",
        fog_distance=[10, 40], fog_color="#0a1530", sky_top_color="#040810ff", sky_bottom_color="#0a1a30ff", fog_density=0.90,
        particle_id="Dust_Cave_Flies", particle_color="#80c0ff",
        tint_colors=["#1a3050", "#0a2040", "#2a4060"],
        noise_amplitude=0.08, noise_scale=40, noise_octaves=2,
        boundary_type="textured", boundary_transition=0.12, boundary_density=7, boundary_height=30,
        boundary_noise_scale=10, boundary_noise_amp=0.35,
        # Enclosed frozen tomb — low ceiling (claustrophobic crypt corridors)
        signature_type="ceiling", ceiling_y=100, ceiling_edge_y=85,
        surface_thickness=1, sub_thickness=2,
        # Frozen strata in walls
        wall_strata=[
            {"top_y": 78, "bottom_y": 76, "material": "Rock_Ice"},
            {"top_y": 70, "bottom_y": 67, "material": "Rock_Stone"},
        ],
        # Sparse frost ground cover — icy tufts barely clinging to frozen stone
        grass_blocks=[
            {"block": "Plant_Grass_Sharp", "weight": 3},
            {"block": "Plant_Grass_Sharp_Wild", "weight": 2},
        ],
        grass_noise_scale=30, grass_seed="crypt_frost",
        grass_skip_sparse=0.95, grass_skip_moderate=0.80, grass_skip_dense=0.50,

        prop_layers=[
            # ═══════════════════════════════════════════════════════════
            # FLOOR — 3-zone FieldFunction: Crystal Grotto / Frozen Ruins / Glacial Formation
            # zone_seed="crypt_zones" shared across all zone layers for coherent areas
            # ═══════════════════════════════════════════════════════════

            # ─── ICE FORMATIONS: Zone-based primary layer ───
            # Zone A (Crystal Grotto): blue crystals + ice stalagmites + ethereal glow
            # Zone B (Frozen Ruins): ice walls, crevasses, frozenstone rubble
            # Zone C (Glacial Formation): glacier pillars, icebergs, massive raw ice
            PropLayer("ice_formations", [], mesh_scale_x=8, mesh_scale_z=8, skip_chance=0.25,
                      occurrence_max=0.30, zones=[
                PropZone(-1.0, -0.2, [
                    {"Path": "Cave/Stalagmites/Rock_Ice/Floor", "Weight": 4},
                    {"Path": "Rock_Formations/Ice_Formations/Stalactites/Floor", "Weight": 3},
                    {"Path": "Cave/Stalagmites/Rock_Basalt_Crystal/Blue/Floor", "Weight": 3},
                    {"Path": "Cave/Stalagmites/Rock_Basalt_Crystal/Water/Floor", "Weight": 2},
                ]),
                PropZone(-0.2, 0.3, [
                    {"Path": "Rock_Formations/Ice_Formations/Wall/Short", "Weight": 3},
                    {"Path": "Rock_Formations/Ice_Formations/Wall/Medium", "Weight": 2},
                    {"Path": "Rock_Formations/Ice_Formations/Crevasse_Thin", "Weight": 3},
                    {"Path": "Rock_Formations/Rocks/Frozenstone/Snowy", "Weight": 3},
                ]),
                PropZone(0.3, 1.0, [
                    {"Path": "Rock_Formations/Ice_Formations/Glacier/Pillar", "Weight": 3},
                    {"Path": "Cave/Stalagmites/Rock_Ice/Floor", "Weight": 4},
                    {"Path": "Rock_Formations/Ice_Formations/Stalactites/Floor", "Weight": 2},
                    {"Path": "Rock_Formations/Ice_Formations/Iceberg", "Weight": 1},
                ]),
            ], zone_noise_scale=70, zone_seed="crypt_zones"),

            # ─── CRYSTAL DEPOSITS (zone A only — concentrated blue/cyan glow nodes) ───
            PropLayer("crystal_deposits", [], mesh_scale_x=10, mesh_scale_z=10, skip_chance=0.30,
                      occurrence_max=0.25, zones=[
                PropZone(-1.0, -0.2, [
                    {"Path": "Cave/Stalagmites/Rock_Basalt_Crystal/Water/Floor", "Weight": 4},
                    {"Path": "Cave/Stalagmites/Rock_Basalt_Crystal/White/Floor", "Weight": 3},
                    {"Path": "Cave/Stalagmites/Rock_Clay_Crystal/Blue/Floor", "Weight": 3},
                    {"Path": "Cave/Stalagmites/Rock_Clay_Crystal/Water/Floor", "Weight": 2},
                ]),
            ], zone_noise_scale=70, zone_seed="crypt_zones"),

            # ─── FROZEN DEBRIS (zone B only — ice wall fragments and crevasses) ───
            PropLayer("frozen_debris", [], mesh_scale_x=12, mesh_scale_z=12, skip_chance=0.35,
                      occurrence_max=0.20, zones=[
                PropZone(-0.2, 0.3, [
                    {"Path": "Rock_Formations/Ice_Formations/Crevasse", "Weight": 2},
                    {"Path": "Rock_Formations/Ice_Formations/Crevasse_Thin", "Weight": 3},
                    {"Path": "Rock_Formations/Ice_Formations/Wall/Short", "Weight": 2},
                    {"Path": "Rock_Formations/Rocks/Frozenstone/Small", "Weight": 3},
                ]),
            ], zone_noise_scale=70, zone_seed="crypt_zones"),

            # ─── ICE ROCKS (all zones — scattered geological detail) ───
            PropLayer("ice_rocks", [
                {"Path": "Rock_Formations/Rocks/Frozenstone/Small", "Weight": 4},
                {"Path": "Rock_Formations/Rocks/Frozenstone/Snowy", "Weight": 3},
                {"Path": "Rock_Formations/Rocks/Basalt/Snowy", "Weight": 2},
                {"Path": "Rock_Formations/Rocks/Calcite/Small", "Weight": 2},
            ], mesh_scale_x=9, mesh_scale_z=9, skip_chance=0.35, occurrence_max=0.25),

            # ─── ICE FLOOR DETAIL (all zones — dense small scatter) ───
            PropLayer("ice_floor_detail", [
                {"Path": "Rock_Formations/Rocks/Frozenstone/Small", "Weight": 3},
                {"Path": "Rock_Formations/Rocks/Calcite/Small", "Weight": 2},
                {"Path": "Rock_Formations/Rocks/Basalt/Snowy", "Weight": 1},
            ], mesh_scale_x=7, mesh_scale_z=7, skip_chance=0.40, occurrence_max=0.20),

            # ─── GLACIER LANDMARKS (rare massive ice anchors — visual reference points) ───
            PropLayer("glacier_landmarks", [
                {"Path": "Rock_Formations/Ice_Formations/Glacier/Pillar", "Weight": 3},
                {"Path": "Rock_Formations/Ice_Formations/Iceberg", "Weight": 2},
                {"Path": "Rock_Formations/Ice_Formations/Wall/Tall", "Weight": 1},
            ], mesh_scale_x=50, mesh_scale_z=50, skip_chance=0.5, occurrence_max=0.05,
               scale_with_radius=False, scanner_max_y=8),

            # ═══════════════════════════════════════════════════════════
            # CEILING — Zone-based hanging ice (matches floor zones for coherence)
            # ═══════════════════════════════════════════════════════════

            # ─── ICE CEILING: Zone-based stalactites ───
            PropLayer("ice_ceiling", [], mesh_scale_x=7, mesh_scale_z=7, skip_chance=0.20,
                      occurrence_max=0.35, placement="ceiling", zones=[
                PropZone(-1.0, -0.2, [
                    {"Path": "Cave/Stalagmites/Rock_Basalt_Crystal/Blue/Ceiling", "Weight": 4},
                    {"Path": "Cave/Stalagmites/Rock_Basalt_Crystal/Water/Ceiling", "Weight": 3},
                    {"Path": "Cave/Stalagmites/Rock_Ice/Ceiling", "Weight": 2},
                ]),
                PropZone(-0.2, 0.3, [
                    {"Path": "Cave/Stalagmites/Rock_Ice/Ceiling", "Weight": 5},
                    {"Path": "Rock_Formations/Ice_Formations/Stalactites/Ceiling", "Weight": 3},
                ]),
                PropZone(0.3, 1.0, [
                    {"Path": "Cave/Stalagmites/Rock_Ice/Ceiling", "Weight": 4},
                    {"Path": "Rock_Formations/Ice_Formations/Glacier/Ceiling", "Weight": 3},
                    {"Path": "Rock_Formations/Ice_Formations/Stalactites/Ceiling", "Weight": 2},
                ]),
            ], zone_noise_scale=70, zone_seed="crypt_zones"),

            # ─── CRYSTAL CEILING (zone A — concentrated hanging crystal glow) ───
            PropLayer("crystal_ceiling", [], mesh_scale_x=10, mesh_scale_z=10, skip_chance=0.30,
                      occurrence_max=0.25, placement="ceiling", zones=[
                PropZone(-1.0, -0.2, [
                    {"Path": "Cave/Stalagmites/Rock_Basalt_Crystal/Blue/Ceiling", "Weight": 4},
                    {"Path": "Cave/Stalagmites/Rock_Basalt_Crystal/Water/Ceiling", "Weight": 3},
                    {"Path": "Cave/Stalagmites/Rock_Basalt_Crystal/White/Ceiling", "Weight": 2},
                ]),
            ], zone_noise_scale=70, zone_seed="crypt_zones"),

            # ─── GEODE CLUSTERS (rare floating cyan geodes — R55+ only) ───
            PropLayer("geode_ceiling", [
                {"Path": "Geode_Floating/Crystal/Large/Cyan/Roof", "Weight": 1},
            ], mesh_scale_x=30, mesh_scale_z=30, skip_chance=0.5, occurrence_max=0.08,
               placement="ceiling", scale_with_radius=False, min_arena_radius=55),

            # NOTE: Frozen Crypts uses self-contained structures (Monuments/Encounter/Shale/Frozen,
            # Monuments/Incidental/Shale/Ruins/Frozen) via RealmStructurePlacer at runtime.
            # Cave/Nodes/Rock_Shale were removed — they are corridor-carving prefabs designed
            # for embedded-in-rock placement, not open arena environments.
        ],
    ),

    # ─── SAND TOMBS: Underground sandstone pyramid (Sand Skeleton exclusive) ───
    BiomeSpec(
        name="Sand_Tombs",
        biome_prefix="Realm_Sand_Tombs",
        surface_materials=["Rock_Sandstone"],
        sub_material="Rock_Sandstone", deep_material="Rock_Sandstone",
        spawn_platform_material="Rock_Stone",
        environment="Zone1_Underground",
        fog_distance=[12, 45], fog_color="#1a1008", sky_top_color="#0a0804ff", sky_bottom_color="#2a1a10ff", fog_density=0.85,
        particle_id="Dust_Cave_Flies", particle_color="#c0a060",
        tint_colors=["#8a6a30", "#6a5020", "#a07840"],
        noise_amplitude=0.08, noise_scale=45, noise_octaves=2,
        boundary_type="textured", boundary_transition=0.10, boundary_density=7, boundary_height=25,
        boundary_noise_scale=8, boundary_noise_amp=0.3,
        # Enclosed sandstone tomb — low tight ceiling (pyramid interior)
        signature_type="ceiling", ceiling_y=95, ceiling_edge_y=82,
        surface_thickness=2, sub_thickness=3,
        # Sandstone strata in walls
        wall_strata=[
            {"top_y": 75, "bottom_y": 73, "material": "Soil_Sand_White"},
            {"top_y": 68, "bottom_y": 65, "material": "Rock_Sandstone"},
        ],
        # Sparse ancient scrub — remnants of life sealed in the pyramid for millennia
        grass_blocks=[
            {"block": "Plant_Grass_Sharp", "weight": 2},
            {"block": "Plant_Grass_Sharp_Wild", "weight": 1},
        ],
        grass_noise_scale=35, grass_seed="tomb_scrub",
        grass_skip_sparse=0.97, grass_skip_moderate=0.85, grass_skip_dense=0.55,

        prop_layers=[
            # ═══════════════════════════════════════════════════════════
            # FLOOR — 3-zone FieldFunction: Temple Ruins / Crystal Chamber / Burial Grounds
            # zone_seed="tomb_zones" shared across all zone layers for coherent areas
            # ═══════════════════════════════════════════════════════════

            # ─── TOMB FORMATIONS: Zone-based primary layer ───
            # Zone A (Temple Ruins): sandstone pillars, ancient columns, architectural remnants
            # Zone B (Crystal Chamber): red/pink crystals (warm ancient glow), stalagmites
            # Zone C (Burial Grounds): dense sandstone rubble, chalk/shale debris
            PropLayer("tomb_formations", [], mesh_scale_x=8, mesh_scale_z=8, skip_chance=0.25,
                      occurrence_max=0.30, zones=[
                PropZone(-1.0, -0.2, [
                    {"Path": "Rock_Formations/Rocks/Sandstone/Pillars/Medium", "Weight": 4},
                    {"Path": "Rock_Formations/Rocks/Sandstone/Pillars/Sandstone", "Weight": 3},
                    {"Path": "Cave/Stalagmites/Rock_Sandstone_Large/Floor", "Weight": 2},
                    {"Path": "Rock_Formations/Rocks/Sandstone/Large_Tall", "Weight": 1},
                ]),
                PropZone(-0.2, 0.3, [
                    {"Path": "Cave/Stalagmites/Rock_Basalt_Crystal/Red/Floor", "Weight": 4},
                    {"Path": "Cave/Stalagmites/Rock_Basalt_Crystal/Pink/Floor", "Weight": 3},
                    {"Path": "Cave/Stalagmites/Rock_Sandstone/Floor", "Weight": 3},
                    {"Path": "Cave/Stalagmites/Rock_Sandstone_Large/Floor", "Weight": 1},
                ]),
                PropZone(0.3, 1.0, [
                    {"Path": "Cave/Stalagmites/Rock_Sandstone/Floor", "Weight": 4},
                    {"Path": "Rock_Formations/Rocks/Sandstone/Small", "Weight": 4},
                    {"Path": "Rock_Formations/Rocks/Sandstone/Red/Small", "Weight": 2},
                    {"Path": "Rock_Formations/Rocks/Chalk/Small", "Weight": 2},
                ]),
            ], zone_noise_scale=70, zone_seed="tomb_zones"),

            # ─── CRYSTAL DEPOSITS (zone B only — warm red/pink glow concentrations) ───
            PropLayer("crystal_deposits", [], mesh_scale_x=10, mesh_scale_z=10, skip_chance=0.30,
                      occurrence_max=0.25, zones=[
                PropZone(-0.2, 0.3, [
                    {"Path": "Cave/Stalagmites/Rock_Basalt_Crystal/Red/Floor", "Weight": 4},
                    {"Path": "Cave/Stalagmites/Rock_Basalt_Crystal/Pink/Floor", "Weight": 3},
                    {"Path": "Cave/Stalagmites/Rock_Clay_Crystal/Red/Floor", "Weight": 3},
                    {"Path": "Cave/Stalagmites/Rock_Clay_Crystal/Pink/Floor", "Weight": 2},
                ]),
            ], zone_noise_scale=70, zone_seed="tomb_zones"),

            # ─── TEMPLE PILLARS (zone A only — dense architectural columns) ───
            PropLayer("temple_pillars", [], mesh_scale_x=12, mesh_scale_z=12, skip_chance=0.35,
                      occurrence_max=0.20, zones=[
                PropZone(-1.0, -0.2, [
                    {"Path": "Rock_Formations/Rocks/Sandstone/Pillars/Medium", "Weight": 4},
                    {"Path": "Rock_Formations/Rocks/Sandstone/Pillars/Sandstone", "Weight": 3},
                    {"Path": "Rock_Formations/Rocks/Sandstone/Large", "Weight": 2},
                ]),
            ], zone_noise_scale=70, zone_seed="tomb_zones"),

            # ─── TOMB ROCKS (all zones — scattered debris and rubble) ───
            PropLayer("tomb_rocks", [
                {"Path": "Rock_Formations/Rocks/Sandstone/Small", "Weight": 4},
                {"Path": "Rock_Formations/Rocks/Sandstone/Large", "Weight": 2},
                {"Path": "Rock_Formations/Rocks/Sandstone/Red/Small", "Weight": 2},
                {"Path": "Rock_Formations/Rocks/Chalk/Small", "Weight": 2},
            ], mesh_scale_x=9, mesh_scale_z=9, skip_chance=0.35, occurrence_max=0.25),

            # ─── TOMB FLOOR DETAIL (all zones — dense small scatter) ───
            PropLayer("tomb_floor_detail", [
                {"Path": "Rock_Formations/Rocks/Sandstone/Small", "Weight": 3},
                {"Path": "Rock_Formations/Rocks/Chalk/Small", "Weight": 2},
                {"Path": "Rock_Formations/Rocks/Shale/Small", "Weight": 1},
            ], mesh_scale_x=7, mesh_scale_z=7, skip_chance=0.40, occurrence_max=0.20),

            # ─── PILLAR LANDMARKS (rare dramatic tall columns — temple pillars) ───
            PropLayer("pillar_landmarks", [
                {"Path": "Rock_Formations/Rocks/Sandstone/Pillars/Large", "Weight": 3},
                {"Path": "Rock_Formations/Rocks/Sandstone/Large_Tall", "Weight": 2},
            ], mesh_scale_x=45, mesh_scale_z=45, skip_chance=0.5, occurrence_max=0.05,
               scale_with_radius=False, scanner_max_y=8),

            # ═══════════════════════════════════════════════════════════
            # CEILING — Zone-based hanging formations (matches floor zones)
            # ═══════════════════════════════════════════════════════════

            # ─── TOMB CEILING: Zone-based stalactites ───
            PropLayer("tomb_ceiling", [], mesh_scale_x=7, mesh_scale_z=7, skip_chance=0.20,
                      occurrence_max=0.30, placement="ceiling", zones=[
                PropZone(-1.0, -0.2, [
                    {"Path": "Cave/Stalagmites/Rock_Sandstone/Ceiling", "Weight": 5},
                    {"Path": "Cave/Stalagmites/Rock_Sandstone_Large/Ceiling", "Weight": 3},
                ]),
                PropZone(-0.2, 0.3, [
                    {"Path": "Cave/Stalagmites/Rock_Basalt_Crystal/Red/Ceiling", "Weight": 4},
                    {"Path": "Cave/Stalagmites/Rock_Basalt_Crystal/Pink/Ceiling", "Weight": 3},
                    {"Path": "Cave/Stalagmites/Rock_Sandstone/Ceiling", "Weight": 2},
                ]),
                PropZone(0.3, 1.0, [
                    {"Path": "Cave/Stalagmites/Rock_Sandstone/Ceiling", "Weight": 5},
                    {"Path": "Cave/Stalagmites/Rock_Sandstone_Large/Ceiling", "Weight": 2},
                ]),
            ], zone_noise_scale=70, zone_seed="tomb_zones"),

            # ─── CRYSTAL CEILING (zone B — warm red/pink crystal glow from above) ───
            PropLayer("crystal_ceiling", [], mesh_scale_x=10, mesh_scale_z=10, skip_chance=0.30,
                      occurrence_max=0.25, placement="ceiling", zones=[
                PropZone(-0.2, 0.3, [
                    {"Path": "Cave/Stalagmites/Rock_Basalt_Crystal/Red/Ceiling", "Weight": 4},
                    {"Path": "Cave/Stalagmites/Rock_Basalt_Crystal/Pink/Ceiling", "Weight": 3},
                ]),
            ], zone_noise_scale=70, zone_seed="tomb_zones"),

            # ─── CAVE FORMATIONS (rare large ceiling features) ───
            PropLayer("cave_formations", [
                {"Path": "Cave/Formations/Rock_Sandstone/Ceiling", "Weight": 1},
            ], mesh_scale_x=30, mesh_scale_z=30, skip_chance=0.4, occurrence_max=0.10,
               placement="ceiling", scale_with_radius=False, min_arena_radius=45),

            # NOTE: Sand Tombs uses self-contained structures (Monuments/Incidental/Sandstone,
            # Treasure_Rooms, Wells) via RealmStructurePlacer at runtime.
            # Cave/Nodes/Rock_Sandstone were removed — corridor-carving prefabs designed
            # for embedded-in-rock placement, not open arena environments.
        ],
    ),

    BiomeSpec(
        name="Corrupted",
        biome_prefix="Realm_Corrupted",
        surface_materials=["Rock_Volcanic"],
        sub_material="Rock_Volcanic", deep_material="Rock_Volcanic",
        spawn_platform_material="Rock_Basalt",
        environment="Env_Default_Void",
        fog_distance=[-30, 50], fog_color="#200040", sky_top_color="#100020ff", sky_bottom_color="#300060ff", fog_density=0.85, color_filter="#8040c0",
        particle_id="Ash", particle_color="#8040a0",
        screen_effect="ScreenEffects/Fire.png", screen_effect_color="#4020600d",
        tint_colors=["#4B0082", "#380060", "#5E10A4"],
        noise_amplitude=0.40, noise_scale=15, noise_octaves=3, noise_persistence=0.45,
        boundary_type="textured", boundary_transition=0.20, boundary_density=5, boundary_height=15,
        boundary_noise_scale=8, boundary_noise_amp=0.6,
        signature_type="fractured", fracture_noise_scale_xz=12, fracture_noise_scale_y=8, fracture_max_depth=-0.8,

        prop_layers=[
            PropLayer("dead_trees", [
                {"Path": "Trees/Burnt/Stage_1", "Weight": 3},
                {"Path": "Trees/Burnt/Stage_2", "Weight": 2},
                {"Path": "Trees/Petrified/Stage_2", "Weight": 2},
                {"Path": "Plants/Twisted_Wood/Poisoned", "Weight": 2},
                {"Path": "Plants/Twisted_Wood/Ash", "Weight": 1},
            ], mesh_scale_x=14, mesh_scale_z=14, skip_chance=0.4),
            PropLayer("corrupted_rocks", [
                {"Path": "Rock_Formations/Rocks/Volcanic/Large", "Weight": 2},
                {"Path": "Rock_Formations/Rocks/Volcanic/Spiked/Small", "Weight": 2},
                {"Path": "Rock_Formations/Rocks/Shale/Spikey/Small", "Weight": 2},
                {"Path": "Rock_Formations/Rocks/Slate/Spikes/Small", "Weight": 2},
                {"Path": "Rock_Formations/Rocks/Slate/Spikes/Large", "Weight": 1},
            ], mesh_scale_x=14, mesh_scale_z=14, skip_chance=0.3),
            PropLayer("void_crystals", [
                {"Path": "Rock_Formations/Rocks/Geodes/Purple", "Weight": 4},
                {"Path": "Rock_Formations/Rocks/Geodes/Blue", "Weight": 2},
                {"Path": "Rock_Formations/Rocks/Geodes/Cyan", "Weight": 1},
                {"Path": "Rock_Formations/Rocks/Geodes/Pink", "Weight": 1},
            ], mesh_scale_x=16, mesh_scale_z=16, skip_chance=0.4),
            PropLayer("corruption_detail", [
                {"Path": "Plants/Bush/Dead_Lavathorn", "Weight": 2},
                {"Path": "Rock_Formations/Rocks/Shale/Small", "Weight": 2},
                {"Path": "Rock_Formations/Rocks/Basalt/Hexagon", "Weight": 1},
                {"Path": "Rock_Formations/Rocks/Basalt/Small", "Weight": 1},
            ], mesh_scale_x=9, mesh_scale_z=9, skip_chance=0.4),
        ],
    ),

    # ─── FLOATING ISLAND biome (terrain drops into void at edge) ───
    BiomeSpec(
        name="Void",
        biome_prefix="Realm_Void",
        surface_materials=["Rock_Stone"],
        sub_material="Rock_Stone", deep_material="Rock_Stone",
        spawn_platform_material="Rock_Sandstone",
        environment="Env_Default_Void",
        fog_distance=[-40, 30], fog_color="#0a0020", sky_top_color="#000010ff", sky_bottom_color="#0a0030ff", fog_density=0.95,
        particle_id="Dust_Cave_Flies", particle_color="#4020a0",
        tint_colors=["#1A0A2E", "#0F0520", "#25103C"],
        noise_amplitude=0.35, noise_scale=20, noise_octaves=3, noise_persistence=0.4,
        boundary_type="floating", boundary_transition=0.10, boundary_density=-5, boundary_height=0,
        boundary_noise_scale=8, boundary_noise_amp=0.3,
        surface_thickness=1,
        prop_layers=[
            PropLayer("crystal_clusters", [
                {"Path": "Rock_Formations/Rocks/Geodes/Purple", "Weight": 4},
                {"Path": "Rock_Formations/Rocks/Geodes/Blue", "Weight": 3},
                {"Path": "Rock_Formations/Rocks/Geodes/Cyan", "Weight": 2},
                {"Path": "Rock_Formations/Rocks/Geodes/White", "Weight": 1},
            ], mesh_scale_x=12, mesh_scale_z=12, skip_chance=0.3),
            PropLayer("void_pillars", [
                {"Path": "Rock_Formations/Rocks/Basalt/Hexagon", "Weight": 3},
                {"Path": "Rock_Formations/Rocks/Basalt/Large", "Weight": 2},
                {"Path": "Rock_Formations/Rocks/Quartzite/Large", "Weight": 1},
            ], mesh_scale_x=16, mesh_scale_z=16, skip_chance=0.5),
            PropLayer("void_spikes", [
                {"Path": "Rock_Formations/Rocks/Slate/Spikes/Small", "Weight": 3},
                {"Path": "Rock_Formations/Rocks/Slate/Spikes/Large", "Weight": 2},
                {"Path": "Rock_Formations/Rocks/Slate/Small", "Weight": 1},
            ], mesh_scale_x=14, mesh_scale_z=14, skip_chance=0.4),
            PropLayer("void_detail", [
                {"Path": "Rock_Formations/Rocks/Geodes/Pink", "Weight": 2},
                {"Path": "Rock_Formations/Rocks/Geodes/Green", "Weight": 1},
                {"Path": "Rock_Formations/Rocks/Calcite/Small", "Weight": 1},
            ], mesh_scale_x=10, mesh_scale_z=10, skip_chance=0.5),
        ],
    ),
]

ARENA_BASIC = BiomeSpec(
    name="Arena_Basic",
    biome_prefix="Realm_Arena_Basic",
    surface_materials=["Soil_Grass_Full"],
    sub_material="Soil_Dirt", deep_material="Rock_Stone",
    environment="Env_Zone1_Plains",
    tint_colors=["#3CB371"],
    noise_amplitude=0.0, noise_scale=30,
    boundary_type="rising", boundary_transition=0.15, boundary_density=5,
)


# ═══════════════════════════════════════════════════════════════════
# GENERATION: DENSITY TREE
# ═══════════════════════════════════════════════════════════════════

FLOOR_Y = 64


def _terrain_noise_node(biome: BiomeSpec) -> dict:
    """Terrain noise node — SimplexNoise2D (default) or CellNoise2D (Voronoi).

    CellNoise2D creates cellular patterns instead of smooth gradients:
    - Distance2Div: output [-1, 0]. Cell centers=-1, boundaries=0.
    - With cellnoise_invert=True, CurveMapper maps centers→islands, boundaries→channels.
    Verified from decompiled FastNoiseLite.java (distance0/distance1 - 1.0).
    """
    if biome.terrain_noise_type == "cellnoise":
        return {
            "Type": "CellNoise2D",
            "Skip": False,
            "ScaleX": biome.noise_scale,
            "ScaleZ": biome.noise_scale,
            "Jitter": biome.cellnoise_jitter,
            "Octaves": biome.noise_octaves,
            "Seed": biome.noise_seed,
            "CellType": biome.cellnoise_cell_type,
        }
    return {
        "Type": "SimplexNoise2D",
        "Skip": False,
        "Lacunarity": 2,
        "Persistence": biome.noise_persistence,
        "Octaves": biome.noise_octaves,
        "Scale": biome.noise_scale,
        "Seed": biome.noise_seed,
    }


def _generate_signature_density_inputs(biome: BiomeSpec, size: SizeSpec) -> list:
    """Generate additional density Sum inputs for biome signature terrain features.

    Each signature type returns 0-1 extra density nodes that get added to the
    outer Sum in generate_density_circular(). These create structural terrain
    elements unique to each biome.
    """
    inputs = []
    r = size.radius

    if biome.signature_type == "elevated_center":
        # Forest: gentle hill at center (+3 blocks), fades to 0 at radius 30
        fade_radius = min(30, r // 2)
        inputs.append({
            "Type": "CurveMapper", "Skip": False,
            "Curve": {
                "Type": "Manual",
                "Points": [
                    {"In": 0, "Out": 0.15},
                    {"In": fade_radius // 2, "Out": 0.10},
                    {"In": fade_radius, "Out": 0},
                    {"In": r, "Out": 0},
                ]
            },
            "Inputs": [{
                "Type": "Axis", "Skip": False,
                "Axis": {"X": 0, "Y": 1, "Z": 0},
            }]
        })

    elif biome.signature_type == "tidal_shelf":
        # Beach Island: graduated shoreline from dry center to ocean drop.
        # Combined with floating boundary (-4 density at edge), the tidal shelf
        # creates a seamless transition: dry island → wet shore → coral shelf → deep ocean.
        # At 85%+ radius: tidal_shelf(-0.20) + floating(-4) = deep underwater.
        dry_end = int(r * 0.40)      # Dry shore ends at 40% radius
        wet_end = int(r * 0.65)      # Wet transition ends at 65% radius
        shelf_end = int(r * 0.85)    # Coral shelf ends at 85% radius
        inputs.append({
            "Type": "CurveMapper", "Skip": False,
            "Curve": {
                "Type": "Manual",
                "Points": [
                    {"In": 0, "Out": 0},              # Center: flat dry island
                    {"In": dry_end, "Out": 0},         # Dry shore: palms, driftwood
                    {"In": wet_end, "Out": -0.06},     # Wet transition: tide pools (~1 block lower)
                    {"In": shelf_end, "Out": -0.15},   # Coral shelf: shallow water (~2.5 blocks lower)
                    {"In": r - 5, "Out": -0.20},       # Shore edge: merges with floating drop
                    {"In": r, "Out": -0.20},            # Hold at boundary
                ]
            },
            "Inputs": [{
                "Type": "Axis", "Skip": False,
                "Axis": {"X": 0, "Y": 1, "Z": 0},
            }]
        })

        # OCEAN FLOOR: A dedicated BaseHeight CurveMapper that creates solid terrain
        # at depth EVERYWHERE — including beyond the island where floating boundary
        # normally kills all terrain.
        #
        # UNDULATING SEABED: SimplexNoise2D (±3 blocks) added to the BaseHeight input
        # creates underwater hills, valleys, and ridges. Where noise is +3, seabed is
        # 3 blocks higher (shallow reef mound). Where -3, it's deeper (ocean trench).
        # This makes the ocean floor visually interesting, not a flat slab.
        #
        # Density math beyond wall (boundary=-1.5):
        #   Main floor at In=-20: +1.0
        #   Boundary: -1.5
        #   Ocean floor at In=-20: +3.0
        #   Sum: +2.5 → SOLID seabed
        #
        # Ocean floor surface at In≈-12 (± noise):
        #   floor(+0.5) + boundary(-1.5) + ocean(+1.0) = 0 → surface Y≈52 ± 3 blocks
        inputs.append({
            "Type": "CurveMapper", "Skip": False,
            "Curve": {
                "Type": "Manual",
                "Points": [
                    {"In": -30, "Out": 4.0},    # 30 blocks below base: very solid seabed
                    {"In": -20, "Out": 3.0},    # 20 blocks below: solid seabed
                    {"In": -12, "Out": 1.0},    # 12 blocks below: ocean floor surface zone
                    {"In": -5, "Out": 0},        # 5 blocks below: no contribution (island zone)
                    {"In": 0, "Out": 0},          # At base: no contribution
                    {"In": 10, "Out": 0},         # Above: no contribution
                ]
            },
            "Inputs": [{
                "Type": "Sum", "Skip": False,
                "Inputs": [
                    {
                        "Type": "BaseHeight", "Skip": False,
                        "BaseHeightName": "Base", "Distance": True
                    },
                    {
                        "Type": "Normalizer", "Skip": False,
                        "FromMin": -1, "FromMax": 1,
                        "ToMin": -3.0, "ToMax": 3.0,
                        "Inputs": [{
                            "Type": "SimplexNoise2D", "Skip": False,
                            "ScaleX": 25, "ScaleZ": 25,
                            "Octaves": 2, "Persistence": 0.5,
                            "Seed": "ocean_floor_terrain"
                        }]
                    }
                ]
            }]
        })

    elif biome.signature_type == "ceiling":
        # Caverns: DOME-SHAPED ceiling that slopes down from center to edges.
        # Uses Sum(BaseHeight_distance, Axis_dome_offset, Ceiling_noise) inside CurveMapper.
        #
        # The Axis dome offset adds an increasing value from center to edge.
        # This makes the CurveMapper "think" we're HIGHER than we are at the edges,
        # triggering solid blocks at a LOWER Y = ceiling slopes down.
        #
        # At center (Axis=0): offset=0 → ceiling at ceiling_y (highest point)
        # At edge (Axis=R): offset=+dome_drop → ceiling at ceiling_y - dome_drop (lowest)
        #
        # Ceiling noise (SimplexNoise2D ±2 blocks) adds organic bumps.
        dome_drop = biome.ceiling_y - biome.ceiling_edge_y  # Dome slopes from ceiling_y to ceiling_edge_y
        inputs.append({
            "Type": "CurveMapper", "Skip": False,
            "Curve": {
                "Type": "Manual",
                "Points": [
                    {"In": -5, "Out": 0},
                    {"In": 0, "Out": 0},
                    {"In": 1, "Out": 10},
                    {"In": 30, "Out": 10},
                ]
            },
            "Inputs": [{
                "Type": "Sum", "Skip": False,
                "Inputs": [
                    {
                        "Type": "BaseHeight", "Skip": False,
                        "BaseHeightName": "Ceiling", "Distance": True
                    },
                    {
                        "Type": "Axis", "Skip": False,
                        "Axis": {"X": 0, "Y": 1, "Z": 0},
                        "Curve": {
                            "Type": "Manual",
                            "Points": [
                                {"In": 0, "Out": 0},
                                {"In": r // 2, "Out": dome_drop // 2},
                                {"In": r, "Out": dome_drop},
                            ]
                        }
                    },
                    {
                        "Type": "Normalizer", "Skip": False,
                        "FromMin": -1, "FromMax": 1,
                        "ToMin": -2, "ToMax": 2,
                        "Inputs": [{
                            "Type": "SimplexNoise2D", "Skip": False,
                            "Lacunarity": 2, "Persistence": 0.5,
                            "Octaves": 2, "Scale": 15,
                            "Seed": "ceiling_bumps",
                        }]
                    }
                ]
            }]
        })

    elif biome.signature_type == "sunken_basin":
        # Desert: gentle depression at center (-1.5 blocks), rising to normal at edges.
        # Creates a natural amphitheater — players in the center are slightly below
        # surrounding dunes. Enemies approach from slightly elevated positions.
        fade_radius = min(40, r // 2)
        inputs.append({
            "Type": "CurveMapper", "Skip": False,
            "Curve": {
                "Type": "Manual",
                "Points": [
                    {"In": 0, "Out": -0.10},
                    {"In": fade_radius // 2, "Out": -0.06},
                    {"In": fade_radius, "Out": 0},
                    {"In": r, "Out": 0},
                ]
            },
            "Inputs": [{
                "Type": "Axis", "Skip": False,
                "Axis": {"X": 0, "Y": 1, "Z": 0},
            }]
        })

    elif biome.signature_type == "fractured":
        # Corrupted: SimplexNoise3D subtracts density, carving random pits into the floor.
        # Normalizer maps noise from [-1,1] to [0, -0.8] — only subtracts, never adds.
        inputs.append({
            "Type": "Normalizer", "Skip": False,
            "FromMin": -1, "FromMax": 1,
            "ToMin": 0, "ToMax": biome.fracture_max_depth,
            "Inputs": [{
                "Type": "SimplexNoise3D", "Skip": False,
                "Lacunarity": 2, "Persistence": 0.5, "Octaves": 2,
                "ScaleXZ": biome.fracture_noise_scale_xz,
                "ScaleY": biome.fracture_noise_scale_y,
                "Seed": "fracture",
            }]
        })

    # ─── 3D VOLUMETRIC CAVE FEATURES (bidirectional — pillars + alcoves) ───
    # Unlike "fractured" (subtractive only), this adds solid where 3D noise is positive
    # (creating natural pillars connecting floor to ceiling) and subtracts where negative
    # (carving alcoves and irregular surfaces into walls). This is fundamentally different
    # from 2D terrain variation — it produces volumetric features impossible with height maps.
    if biome.cave_3d_noise_scale_xz > 0:
        inputs.append({
            "Type": "Normalizer", "Skip": False,
            "FromMin": -1, "FromMax": 1,
            "ToMin": -biome.cave_3d_noise_amp, "ToMax": biome.cave_3d_noise_amp,
            "Inputs": [{
                "Type": "SimplexNoise3D", "Skip": False,
                "Lacunarity": 2, "Persistence": 0.45, "Octaves": 2,
                "ScaleXZ": biome.cave_3d_noise_scale_xz,
                "ScaleY": biome.cave_3d_noise_scale_y,
                "Seed": biome.cave_3d_seed,
            }]
        })

    return inputs


def _build_boundary_node(biome: BiomeSpec, size: SizeSpec) -> dict:
    """Build the boundary density node based on biome's boundary_type.

    Three boundary types:
    - "rising":   Terrain slopes up into wall/hillside
    - "floating": Terrain drops into void at edge
    - "textured": Steep wall with 3D noise for craggy surface

    Wall CHARACTER (transition width, density, texture) is biome-specific.
    Wall POSITION is driven by the tier radius.
    Decay to void uses FIXED offsets (not multipliers) so wall thickness
    is consistent regardless of arena size.
    """
    r = size.radius
    # Transition width — biome-specific wall character.
    # Cap at 20 blocks and scale relative to radius, but never less than 3.
    t = min(20, max(3, int(r * biome.boundary_transition)))
    d = biome.boundary_density

    # 5-block buffer between arena edge and wall start.
    # Mobs spawn 0 to r (arena). r to r+5 is open ground (no mobs, no wall).
    # Wall transition begins at r+5.
    ARENA_WALL_BUFFER = 5
    wall_start = r + ARENA_WALL_BUFFER
    solid_end = wall_start + 10

    # Wall ends with a CLIFF into void (density d → -2 in 1 block).
    # Previously this was a gradual 10-block slope, which created a descending
    # outer surface where structures could spawn (Y came back down into
    # scanner_max_y=8 range). The cliff prevents any valid Floor positions
    # on the outer side of the wall.
    #
    # EXCEPTION: Ocean island biomes (floating + fluid) use a gentler beyond-wall
    # curve (-1.5 over 15 blocks instead of -2 in 1 block). This allows the natural
    # floor density at depth to overcome the boundary and create an ocean floor
    # for underwater props (corals, seaweed, reef rocks). Without this, the -2 cliff
    # kills terrain at ALL depths → no seabed → underwater props have nowhere to place.
    if biome.fluid_material and d < 0:
        # Ocean island: gradual rise after wall allows natural ocean floor at depth.
        # At 15 blocks beyond wall: boundary=-1.5, floor at Y=44 outputs ~+2 → net +0.5 = solid seabed.
        beyond_points = [
            {"In": solid_end, "Out": d},
            {"In": solid_end + 15, "Out": -1.5},
        ]
    else:
        # Standard: sharp cliff into void (1 block from d to -2)
        beyond_points = [
            {"In": solid_end, "Out": d},
            {"In": solid_end + 1, "Out": -2},
        ]

    axis_node = {
        "Type": "Axis", "Skip": False,
        "Axis": {"X": 0, "Y": 1, "Z": 0},
        "Curve": {
            "Type": "Manual",
            "Points": [
                {"In": 0, "Out": 0},
                {"In": wall_start - t, "Out": 0},
                {"In": wall_start, "Out": d},
                *beyond_points,
            ]
        }
    }

    return axis_node


def generate_density_circular(biome: BiomeSpec, size: SizeSpec) -> dict:
    """Density tree using Max(floor, boundary) — the PROVEN pattern.

    Structure: Max(floor_with_noise, boundary)
    - Floor: Sum(BaseHeight_CurveMapper, terrain_noise) — ground surface with hills
    - Boundary: Min(height_cap, Axis) — wall capped at specific height

    WHY Max instead of Sum:
    At ground level, floor outputs -1 (air) and boundary outputs +1 (solid wall).
    Sum(-1, +1) = 0 — right at the boundary, noise creates maze artifacts.
    Max(-1, +1) = +1 — wall clearly wins, no ambiguity.

    This is the exact pattern from the original working biomes (git commit f3378e9).
    The arena guide's Sum pattern only works with a very steep 1-block floor transition,
    but ours uses a wide 20-block transition for terrain hills.
    """
    # Floor curve: goes increasingly negative at higher altitudes.
    # This serves two purposes:
    # 1. Creates the ground surface at BaseHeight (distance=0)
    # 2. Naturally limits wall height — at boundary_height above ground,
    #    floor outputs -boundary_density, cancelling the wall's positive density.
    #
    # The wall density and floor curve work together:
    #   At ground (dist=0): floor=-1, wall=+d → Sum = d-1 (positive = solid wall)
    #   At height h (dist=h): floor=-d, wall=+d → Sum = 0 (surface = wall top)
    #   Above h: floor < -d → Sum < 0 (air above wall)
    h = biome.boundary_height if biome.boundary_height > 0 else 15
    d = abs(biome.boundary_density)

    # Terrain noise: added to the BaseHeight DISTANCE input (before CurveMapper).
    # This shifts the Y-coordinate lookup by ±N blocks at each XZ position,
    # moving the actual surface up and down = visible hills.
    #
    # CRITICAL: noise must be scaled to BLOCK DISTANCE, not density units.
    # The amplitude represents how many blocks the surface shifts.
    # ±3 blocks = gentle hills (Forest), ±10 blocks = dramatic (Mountains).
    #
    # Architecture: CurveMapper(Sum(BaseHeight, Noise_in_blocks))
    # NOT: Sum(CurveMapper(BaseHeight), Noise_in_density)  ← old broken approach
    if biome.noise_amplitude > 0:
        # Convert amplitude from density scale to block scale.
        # The old amplitude values (0.10-0.50) were density units.
        # As block distances: multiply by 15 to get reasonable hill heights.
        # Forest 0.20 * 15 = ±3 blocks, Mountains 0.50 * 15 = ±7.5 blocks
        block_amplitude = biome.noise_amplitude * 15

        # CellNoise2D Distance2Div outputs [-1, 0] instead of [-1, 1].
        # Normalizer requires min < max (validated at NormalizerDensity.java:19).
        # To invert: Normalizer scales to [-amp, +amp], then Inverter flips the sign.
        # Result: cell centers (input -1) → +amp → islands; boundaries (input 0) → -amp → channels.
        if biome.terrain_noise_type == "cellnoise":
            from_min, from_max = -1, 0  # CellNoise2D Distance2Div output range
        else:
            from_min, from_max = -1, 1  # SimplexNoise2D output range

        noise_scaled = {
            "Type": "Normalizer", "Skip": False,
            "FromMin": from_min, "FromMax": from_max,
            "ToMin": -block_amplitude, "ToMax": block_amplitude,
            "Inputs": [_terrain_noise_node(biome)]
        }

        # Invert if requested (Inverter multiplies by -1, flipping terrain topology)
        if biome.cellnoise_invert:
            noise_scaled = {
                "Type": "Inverter", "Skip": False,
                "Inputs": [noise_scaled]
            }

        base_height_input = {
            "Type": "Sum", "Skip": False,
            "Inputs": [
                {
                    "Type": "BaseHeight", "Skip": False,
                    "BaseHeightName": "Base", "Distance": True
                },
                noise_scaled
            ]
        }
    else:
        base_height_input = {
            "Type": "BaseHeight", "Skip": False,
            "BaseHeightName": "Base", "Distance": True
        }

    floor_node = {
        "Type": "CurveMapper", "Skip": False,
        "Curve": {
            "Type": "Manual",
            "Points": [
                {"In": h + 10, "Out": -(d + 2)},   # Well above wall: very negative
                {"In": h, "Out": -d},                 # At wall top: cancels wall density
                {"In": 0, "Out": -1},                 # At ground: standard air
                {"In": -1, "Out": 0},                 # Just below: transition
                {"In": -10, "Out": 0.5},              # Below: mostly solid
                {"In": -20, "Out": 1},                # Deep: solid
            ]
        },
        "Inputs": [base_height_input]
    }

    # Boundary node (biome-specific: rising, textured, or floating)
    boundary_node = _build_boundary_node(biome, size)

    # Sum(floor, boundary): wall density adds to floor density.
    # Inside arena: floor varies, boundary=0 → terrain shape only
    # At boundary: floor=-1, boundary ramps up → terrain rises (slope!)
    # Outside arena: floor=-1 at ground + boundary=+5 → Sum=+4 (solid wall)
    # Above wall: floor=-5 at wall height + boundary=+5 → Sum=0 (wall stops)
    # The floor curve goes increasingly negative at higher altitudes,
    # which naturally limits how high the wall extends.
    # Collect all density inputs
    sum_inputs = [floor_node, boundary_node]

    # Add signature terrain features (elevated center, ceiling, fractures, etc.)
    sum_inputs.extend(_generate_signature_density_inputs(biome, size))

    return {
        "Type": "DAOTerrain",
        "Density": {
            "Type": "Sum", "Skip": False,
            "Inputs": sum_inputs
        }
    }


def generate_density_square(biome: BiomeSpec, size: SizeSpec) -> dict:
    """Fallback: XValue/ZValue square walls with noise."""
    r = size.radius
    t = size.wall_transition

    floor_input: dict = {
        "Type": "CurveMapper", "Skip": False,
        "Curve": {"Type": "Manual", "Points": [
            {"In": 0, "Out": 1}, {"In": FLOOR_Y - 10, "Out": 0.5},
            {"In": FLOOR_Y, "Out": 0}, {"In": FLOOR_Y + 1, "Out": -1},
        ]},
        "Inputs": [{"Type": "YValue", "Skip": False}]
    }

    if biome.noise_amplitude > 0:
        floor_input = {
            "Type": "Sum", "Skip": False,
            "Inputs": [
                floor_input,
                {
                    "Type": "Normalizer", "Skip": False,
                    "FromMin": -1, "FromMax": 1,
                    "ToMin": -biome.noise_amplitude, "ToMax": biome.noise_amplitude,
                    "Inputs": [_terrain_noise_node(biome)]
                }
            ]
        }

    def wall_curve(axis: str) -> dict:
        return {
            "Type": "CurveMapper", "Skip": False,
            "Curve": {"Type": "Manual", "Points": [
                {"In": -(r + 50), "Out": 1}, {"In": -r, "Out": 1},
                {"In": -(r - t), "Out": -1}, {"In": r - t, "Out": -1},
                {"In": r, "Out": 1}, {"In": r + 50, "Out": 1},
            ]},
            "Inputs": [{"Type": axis, "Skip": False}]
        }

    return {
        "Type": "DAOTerrain",
        "Density": {
            "Type": "Max", "Skip": False,
            "Inputs": [
                floor_input,
                {"Type": "Min", "Skip": False, "Inputs": [
                    {"Type": "CurveMapper", "Skip": False,
                     "Curve": {"Type": "Manual", "Points": [
                         {"In": 0, "Out": 1}, {"In": FLOOR_Y + 20, "Out": 1},
                         {"In": FLOOR_Y + 21, "Out": -1},
                     ]},
                     "Inputs": [{"Type": "YValue", "Skip": False}]},
                    {"Type": "Max", "Skip": False,
                     "Inputs": [wall_curve("XValue"), wall_curve("ZValue")]}
                ]}
            ]
        }
    }


# ═══════════════════════════════════════════════════════════════════
# DENSITY: SOLID MASS (third paradigm — labyrinth/dungeon biomes)
# ═══════════════════════════════════════════════════════════════════

def generate_density_solid_mass(biome: BiomeSpec, size: SizeSpec) -> dict:
    """Third terrain paradigm: solid stone mass with ceiling dome and spawn clearing.

    Uses BaseHeight("Ceiling", Distance=True) as the reference surface — the PROVEN
    pattern from Caverns ceiling dome, but with INVERTED output: solid below ceiling,
    air above. YValue CurveMapper does NOT work for filling (tested and failed),
    but BaseHeight distance does (same pipeline as all working biomes).

    The dome Axis offset (lower ceiling at edges) and ceiling noise bumps are
    integrated directly into the fill node's input — no separate ceiling dome needed.
    """
    r = size.radius
    cy = biome.ceiling_y
    if cy <= 0:
        cy = 95

    dome_drop = cy - biome.ceiling_edge_y  # How many blocks ceiling drops at edges

    # ─── 1. SOLID FILL: BaseHeight("Ceiling") with dome shape ───
    # Uses the Ceiling DecimalConstant (Y=95) from WorldStructure.
    # Distance < 0 = below ceiling = SOLID. Distance > 0 = above = AIR.
    # Dome offset (Axis) pushes effective ceiling lower at edges.
    # Noise adds organic bumps to ceiling surface.
    #
    # This is the INVERSE of Caverns' ceiling dome node:
    #   Caverns:    below=0 (no contribution), above=+10 (solid cap)
    #   Solid mass: below=+1 (solid fill), above=-2 (air)
    fill_input = {
        "Type": "Sum", "Skip": False,
        "Inputs": [
            {
                "Type": "BaseHeight", "Skip": False,
                "BaseHeightName": "Ceiling", "Distance": True
            },
            # Dome offset: ceiling slopes from center (offset=0) to edge (offset=+dome_drop).
            # Adding dome_drop to distance makes the engine think we're HIGHER → triggers
            # air at a LOWER Y = ceiling drops at edges.
            {
                "Type": "Axis", "Skip": False,
                "Axis": {"X": 0, "Y": 1, "Z": 0},
                "Curve": {
                    "Type": "Manual",
                    "Points": [
                        {"In": 0, "Out": 0},
                        {"In": r // 2, "Out": dome_drop // 2},
                        {"In": r, "Out": dome_drop},
                    ]
                }
            },
            # Ceiling noise: organic bumps ±2 blocks
            {
                "Type": "Normalizer", "Skip": False,
                "FromMin": -1, "FromMax": 1,
                "ToMin": -2, "ToMax": 2,
                "Inputs": [{
                    "Type": "SimplexNoise2D", "Skip": False,
                    "Lacunarity": 2, "Persistence": 0.5,
                    "Octaves": 2, "Scale": 15,
                    "Seed": "ceiling_bumps",
                }]
            }
        ]
    }

    solid_fill = {
        "Type": "CurveMapper", "Skip": False,
        "Curve": {
            "Type": "Manual",
            "Points": [
                {"In": -50, "Out": 1},    # 50 blocks below ceiling: solid
                {"In": -1, "Out": 1},      # 1 block below ceiling: solid
                {"In": 0, "Out": 0},        # At ceiling surface: transition
                {"In": 1, "Out": -2},       # 1 block above: air
                {"In": 50, "Out": -2},      # Well above: air
            ]
        },
        "Inputs": [fill_input]
    }

    # ─── 2. BOUNDARY: arena walls (same system as all biomes) ───
    boundary_node = _build_boundary_node(biome, size)

    # ─── 3. SPAWN CLEARING: small air pocket at center ───
    # Axis CurveMapper: -3 at center (R < 8), fades to 0 by R=15.
    # Overcomes solid_fill's +1: Sum(-3, +1) = -2 → air.
    spawn_clearing = {
        "Type": "CurveMapper", "Skip": False,
        "Curve": {
            "Type": "Manual",
            "Points": [
                {"In": 0, "Out": -3},
                {"In": 8, "Out": -3},
                {"In": 15, "Out": 0},
                {"In": 20, "Out": 0},
            ]
        },
        "Inputs": [{
            "Type": "Axis", "Skip": False,
            "Axis": {"X": 0, "Y": 1, "Z": 0},
        }]
    }

    # ─── ASSEMBLE ───
    # No spawn clearing in density — carving is done at runtime by RealmLabyrinthPlacer
    # (using setBlock(Empty) which is proven to work). Density just needs to be solid.
    return {
        "Type": "DAOTerrain",
        "Density": {
            "Type": "Sum", "Skip": False,
            "Inputs": [solid_fill, boundary_node]
        }
    }


# ═══════════════════════════════════════════════════════════════════
# GENERATION: MATERIAL PROVIDER (FieldFunction zones)
# ═══════════════════════════════════════════════════════════════════

def _generate_empty_queue(biome: BiomeSpec) -> dict:
    """Generate the Empty material queue with proper FLUID placement.

    Verified from community biome JSON: fluid in the Empty queue uses
    the "Fluid" key (not "Solid") in the Material object. This tells the
    MaterialProvider to place actual fluid, not solid water/lava blocks.

    Format: {"Fluid": "Water_Source"} or {"Fluid": "Lava_Source"}
    Confirmed fluid asset IDs from decompiled Fluid.java: Water_Source, Lava_Source.

    Uses absolute Y values for BottomY/TopY (computed from base height + relative offsets).
    """
    queue = []

    if biome.fluid_material:
        base_y = biome.base_y_override if biome.base_y_override > 0 else FLOOR_Y
        abs_bottom = base_y + biome.fluid_bottom_y
        abs_top = base_y + biome.fluid_top_y
        queue.append({
            "Type": "SimpleHorizontal",
            "BottomY": abs_bottom,
            "TopY": abs_top,
            "Material": {
                "Type": "Constant",
                "Material": {"Fluid": biome.fluid_material}
            }
        })

    # Always end with Empty fallback (no fluid outside the range)
    queue.append({"Type": "Constant", "Material": {"Solid": "Empty"}})

    return {"Type": "Queue", "Queue": queue}


def generate_material_provider(biome: BiomeSpec) -> dict:
    """FieldFunction-based material provider with noise-driven surface zones and optional paths."""
    mats = biome.surface_materials
    max_depth = biome.surface_thickness + biome.sub_thickness + 1

    # Build base surface layer — FieldFunction if multiple materials, Constant if one
    if len(mats) >= 2:
        # Split noise range evenly across materials
        step = 2.0 / len(mats)  # total range is -1 to +1 = 2
        delimiters = []
        for i, mat in enumerate(mats):
            delimiters.append({
                "From": -1.0 + i * step,
                "To": -1.0 + (i + 1) * step,
                "Material": {
                    "Type": "Constant",
                    "Material": {"Solid": mat}
                }
            })

        base_surface = {
            "Type": "FieldFunction",
            "FieldFunction": {
                "Type": "SimplexNoise2D", "Skip": False,
                "Lacunarity": 2, "Persistence": 0.3, "Octaves": 1,
                "Scale": biome.mat_noise_scale, "Seed": biome.mat_noise_seed,
            },
            "Delimiters": delimiters,
        }
    else:
        base_surface = {
            "Type": "Constant",
            "Material": {"Solid": mats[0]}
        }

    # If biome has path_material, wrap in a Queue that checks the path noise FIRST.
    # Where path noise is in the narrow band → dirt path. Elsewhere → normal surface.
    # The noise topology creates organic winding trail networks through the terrain.
    if biome.path_material:
        pw = biome.path_width
        surface_provider = {
            "Type": "FieldFunction",
            "FieldFunction": {
                "Type": "SimplexNoise2D", "Skip": False,
                "Lacunarity": 2, "Persistence": 0.4, "Octaves": 2,
                "Scale": biome.path_noise_scale, "Seed": biome.path_seed,
            },
            "Delimiters": [
                {
                    "From": -1.0, "To": round(-pw, 4),
                    "Material": base_surface,
                },
                {
                    "From": round(-pw, 4), "To": round(pw, 4),
                    "Material": {
                        "Type": "Constant",
                        "Material": {"Solid": biome.path_material}
                    },
                },
                {
                    "From": round(pw, 4), "To": 1.0,
                    "Material": base_surface,
                },
            ]
        }
    else:
        surface_provider = base_surface

    # Build layer stack — surface layer uses NoiseThickness if snow_noise_scale > 0.
    # NoiseThickness creates variable-depth surface (snow drifts: 1 block on ridges,
    # up to snow_noise_max blocks in sheltered areas). Otherwise ConstantThickness.
    if biome.snow_noise_scale > 0 and biome.snow_noise_max > 0:
        surface_layer = {
            "Type": "NoiseThickness",
            "Material": surface_provider,
            "ThicknessFunctionXZ": {
                "Type": "Normalizer", "Skip": False,
                "FromMin": -1, "FromMax": 1,
                "ToMin": 1, "ToMax": biome.snow_noise_max,
                "Inputs": [{
                    "Type": "SimplexNoise2D", "Skip": False,
                    "Lacunarity": 2, "Persistence": 0.5, "Octaves": 2,
                    "Scale": biome.snow_noise_scale, "Seed": biome.snow_noise_seed,
                }]
            }
        }
    else:
        surface_layer = {
            "Type": "ConstantThickness",
            "Thickness": biome.surface_thickness,
            "Material": surface_provider,
        }

    layers = [surface_layer]

    if biome.sub_material != biome.deep_material:
        layers.append({
            "Type": "ConstantThickness",
            "Thickness": biome.sub_thickness,
            "Material": {"Type": "Constant", "Material": {"Solid": biome.sub_material}},
        })

    # Build the solid material Queue: SpaceAndDepth (floor) → SpaceAndDepth (ceiling) →
    # strata bands → deep fill. The Queue evaluates top-to-bottom, first match wins.
    # Floor SpaceAndDepth claims surface blocks from above; Ceiling SpaceAndDepth claims
    # the underside of any ceiling (visible when looking up). Both use the same layers
    # so the ceiling material matches the floor material for visual coherence.
    solid_queue = [
        {
            "Type": "SpaceAndDepth",
            "LayerContext": "DEPTH_INTO_FLOOR",
            "MaxExpectedDepth": max_depth,
            "Layers": layers,
        },
    ]

    # Ceiling material layers — mirrors the floor for biomes with ceilings.
    # DEPTH_INTO_CEILING applies layers from the ceiling surface UPWARD into solid.
    # Layer 1 = the block visible from below (ceiling face). Verified from decompiled
    # source: SpaceAndDepthMaterialProvider uses context.depthIntoCeiling (ordinal 1).
    if biome.ceiling_y > 0:
        solid_queue.append({
            "Type": "SpaceAndDepth",
            "LayerContext": "DEPTH_INTO_CEILING",
            "MaxExpectedDepth": max_depth,
            "Layers": layers,
        })

    for stratum in biome.wall_strata:
        solid_queue.append({
            "Type": "SimpleHorizontal",
            "TopY": stratum["top_y"],
            "BottomY": stratum["bottom_y"],
            "Material": {
                "Type": "Constant",
                "Material": {"Solid": stratum["material"]}
            }
        })

    solid_queue.append({"Type": "Constant", "Material": {"Solid": biome.deep_material}})

    return {
        "Type": "Solidity",
        "Solid": {"Type": "Queue", "Queue": solid_queue},
        "Empty": _generate_empty_queue(biome)
    }


# ═══════════════════════════════════════════════════════════════════
# GENERATION: TINT PROVIDER (DensityDelimited)
# ═══════════════════════════════════════════════════════════════════

def generate_tint_provider(biome: BiomeSpec) -> dict:
    """DensityDelimited tinting if 2+ colors, Constant if one."""
    colors = biome.tint_colors
    if len(colors) <= 1:
        return {"Type": "Constant", "Color": colors[0]}

    step = 2.0 / len(colors)
    delimiters = []
    for i, color in enumerate(colors):
        lo = round(-1.0 + i * step, 4)
        hi = round(-1.0 + (i + 1) * step, 4)
        delimiters.append({
            "Tint": {"Type": "Constant", "Color": color},
            "Range": {"MinInclusive": lo, "MaxExclusive": hi},
        })

    return {
        "Type": "DensityDelimited",
        "Density": {
            "Type": "SimplexNoise2D", "Skip": False,
            "Lacunarity": 5, "Persistence": 0.2, "Octaves": 2,
            "Scale": biome.tint_noise_scale, "Seed": biome.tint_noise_seed,
        },
        "Delimiters": delimiters,
    }


# ═══════════════════════════════════════════════════════════════════
# GENERATION: PROPS (with BlockSet floor/ceiling pattern)
# ═══════════════════════════════════════════════════════════════════

def _floor_pattern_blockset(surface_materials: list[str]) -> dict:
    """Floor pattern matching any of the biome's surface materials via BlockSet.

    Matches proven community structure:
    - Origin: BlockSet with Inclusive=true, Materials=[Empty]
    - Floor: BlockSet with Inclusive=true, Materials=[all surface mats]
    - Skip=false on all pattern nodes (Skip=true disables them)
    """
    return {
        "Type": "Floor", "Skip": False,
        "Origin": {
            "Type": "BlockSet", "Skip": False,
            "BlockSet": {
                "Inclusive": True,
                "Materials": [{"Solid": "Empty"}],
            }
        },
        "Floor": {
            "Type": "BlockSet", "Skip": False,
            "BlockSet": {
                "Inclusive": True,
                "Materials": [{"Solid": m} for m in surface_materials],
            }
        },
    }


def _ceiling_pattern_blockset(surface_materials: list[str]) -> dict:
    """Ceiling pattern for hanging props (stalactites, crystals).

    Mirrors Floor pattern but inverted:
    - Origin: Empty block (position where prop hangs)
    - Ceiling: Solid block above (what it hangs from)
    """
    return {
        "Type": "Ceiling", "Skip": False,
        "Origin": {
            "Type": "BlockSet", "Skip": False,
            "BlockSet": {
                "Inclusive": True,
                "Materials": [{"Solid": "Empty"}],
            }
        },
        "Ceiling": {
            "Type": "BlockSet", "Skip": False,
            "BlockSet": {
                "Inclusive": True,
                "Materials": [{"Solid": m} for m in surface_materials],
            }
        },
    }


def _build_prefab_prop(layer: PropLayer, biome: BiomeSpec, prefab_paths: list[dict],
                       pattern: dict, scanner: dict) -> dict:
    """Build a Prefab prop node with the given paths, pattern, and scanner.

    If layer.sink_into_ground is True, adds a DontReplace BlockMask for terrain
    materials so the prop doesn't destroy the ground block it sits inside.
    """
    prefab = {
        "Type": "Prefab", "Skip": False,
        "WeightedPrefabPaths": prefab_paths,
        "LegacyPath": False,
        "LoadEntities": layer.load_entities,
        "Directionality": {
            "Type": "Random",
            "Seed": f"{layer.name}_dir",
            "Pattern": pattern,
        },
        "Scanner": scanner,
        "MoldingDirection": "None", "MoldingChildren": False,
    }

    # When sinking into ground, prevent the prefab from destroying terrain blocks.
    # The bush's blocks only write into air spaces; ground stays intact underneath.
    if layer.sink_into_ground:
        prefab["BlockMask"] = {
            "DontReplace": {
                "Inclusive": True,
                "Materials": [{"Solid": m} for m in biome.surface_materials]
                             + [{"Solid": biome.sub_material}, {"Solid": biome.deep_material}],
            }
        }

    return prefab


def _wall_pattern_blockset(surface_materials: list[str]) -> dict:
    """Wall pattern: only place props adjacent to tree trunks and rocks.

    Proven schema from decompiled WallPatternAsset.java:
    - Origin: Empty (the prop position itself must be air)
    - Wall: BlockSet of solid materials (what the adjacent block must be)
    - Directions: all 4 cardinal (N, S, E, W)
    - RequireAllDirections: false (any ONE adjacent solid is enough)

    NOTE: surface_materials (soil/grass) are intentionally EXCLUDED.
    Including them causes brambles to spawn on 1-block terrain steps
    that look like flat ground — the Wall pattern matches soil at the
    same Y level when the ground is slightly uneven.
    """
    return {
        "Type": "Wall", "Skip": False,
        "Origin": {
            "Type": "BlockSet", "Skip": False,
            "BlockSet": {
                "Inclusive": True,
                "Materials": [{"Solid": "Empty"}],
            }
        },
        "Wall": {
            "Type": "BlockSet", "Skip": False,
            "BlockSet": {
                "Inclusive": True,
                "Materials": [
                    # Tree trunks
                    {"Solid": "Wood_Oak"}, {"Solid": "Wood_Birch"}, {"Solid": "Wood_Cedar"},
                    {"Solid": "Wood_Ash"}, {"Solid": "Wood_Maple"}, {"Solid": "Wood_Beech"},
                    # Rock formations
                    {"Solid": "Rock_Stone"}, {"Solid": "Rock_Sandstone"}, {"Solid": "Rock_Volcanic"},
                    {"Solid": "Rock_Basalt"}, {"Solid": "Rock_Slate"}, {"Solid": "Rock_Shale"},
                ],
            }
        },
        "Directions": ["N", "S", "E", "W"],
        "RequireAllDirections": False,
    }


def generate_prop_layer(layer: PropLayer, biome: BiomeSpec, size: SizeSpec) -> dict:
    """Generate a single prop layer.

    Supports two assignment modes:
    - Constant (default): Same prop set everywhere. Used when layer.zones is empty.
    - FieldFunction (zone-based): Different props in different noise zones.
      Used when layer.zones is non-empty.
      Delimiter fields use Min/Max (NOT From/To like MaterialProvider).

    Key rules learned from working mods:
    - NEVER use Axis node in Props pipeline (only works in Terrain density)
    - Occurrence uses SimplexNoise2D or Normalizer for density, NOT spatial nodes
    - Scanner: RelativeToPosition=false + BaseHeightName
    - MoldingDirection="None" (capital N, not "NONE")
    """
    if layer.scale_with_radius:
        scale_factor = max(1.0, size.radius / 49.0)
        scaled_x = max(5, int(layer.mesh_scale_x * scale_factor))
        scaled_z = max(5, int(layer.mesh_scale_z * scale_factor))
    else:
        # Fixed spacing: structures get MORE instances on bigger arenas, not same density
        scaled_x = layer.mesh_scale_x
        scaled_z = layer.mesh_scale_z

    if layer.placement == "ceiling":
        pattern = _ceiling_pattern_blockset(biome.surface_materials)
        scanner = {
            "Type": "ColumnLinear", "Skip": False,
            "MaxY": 5, "MinY": -40,
            "RelativeToPosition": False,
            "BaseHeightName": "Ceiling",
            "TopDownOrder": False, "ResultCap": 1,
        }
    elif layer.placement == "wall":
        # Wall pattern: spawn adjacent to a solid block (terrain wall).
        # Scanner scans BOTTOM-UP (TopDownOrder=False). MaxY uses scanner_max_y from layer.
        # For ceiling biomes: ResultCap=4 allows MULTIPLE placements per column vertically
        # (one near floor, one mid-wall, one upper-wall). Without this, only the lowest
        # wall position is found and upper walls stay bare.
        # For surface biomes: keep MaxY=5, ResultCap=1 to avoid false positives.
        wall_max_y = layer.scanner_max_y if biome.ceiling_y > 0 else 5
        wall_result_cap = 4 if biome.ceiling_y > 0 else 1
        pattern = _wall_pattern_blockset(biome.surface_materials)
        scanner = {
            "Type": "ColumnLinear", "Skip": False,
            "MaxY": wall_max_y, "MinY": -2,
            "RelativeToPosition": False,
            "BaseHeightName": "Base",
            "TopDownOrder": False, "ResultCap": wall_result_cap,
        }
    elif layer.placement == "ocean_floor":
        # Ocean floor mode: place on deep terrain below water.
        # Standard Floor pattern fails underwater because:
        #   1. Origin requires {"Solid": "Empty"} but water positions have fluid
        #   2. Floor checks surface_materials but ocean floor is sub/deep material
        # Solution: BlockSet pattern matching sub_material + deep_material directly.
        # Same approach as sink_into_ground but targets subsurface materials and
        # scans much deeper (MinY=-40 to reach ocean floor ~20-30 blocks below base).
        ocean_materials = list({biome.sub_material, biome.deep_material})
        pattern = {
            "Type": "BlockSet", "Skip": False,
            "BlockSet": {
                "Inclusive": True,
                "Materials": [{"Solid": m} for m in ocean_materials],
            }
        }
        scanner = {
            "Type": "ColumnLinear", "Skip": False,
            "MaxY": layer.scanner_max_y, "MinY": -40,
            "RelativeToPosition": False,
            "BaseHeightName": "Base",
            "TopDownOrder": True, "ResultCap": 1,
        }
    elif layer.sink_into_ground:
        # Sink mode: place AT the solid ground block (1 block lower than normal).
        # Uses BlockSet pattern matching surface materials at the Origin position itself,
        # instead of Floor pattern (which requires Empty at Origin + surface below).
        # DontReplace BlockMask on the Prefab prevents terrain destruction.
        pattern = {
            "Type": "BlockSet", "Skip": False,
            "BlockSet": {
                "Inclusive": True,
                "Materials": [{"Solid": m} for m in biome.surface_materials],
            }
        }
        scanner = {
            "Type": "ColumnLinear", "Skip": False,
            "MaxY": layer.scanner_max_y, "MinY": -20,
            "RelativeToPosition": False,
            "BaseHeightName": "Base",
            "TopDownOrder": True, "ResultCap": 1,
        }
    else:
        pattern = _floor_pattern_blockset(biome.surface_materials)
        scanner = {
            "Type": "ColumnLinear", "Skip": False,
            "MaxY": layer.scanner_max_y, "MinY": -20,
            "RelativeToPosition": False,
            "BaseHeightName": "Base",
            "TopDownOrder": True, "ResultCap": 1,
        }

    # Build assignment — PondFiller, FieldFunction (zone-based), or Constant (uniform)
    if layer.prop_type == "pondfiller":
        # PondFiller: auto-fills terrain depressions with fluid.
        # Pattern/Scanner are direct children (no Directionality wrapper).
        # BoundingMin/Max defines the search volume for depressions.
        # BarrierBlockSet defines what blocks form the depression walls.
        pond_prop = {
            "Type": "PondFiller",
            "BoundingMin": {
                "X": float(-layer.pond_bounding_xz),
                "Y": float(-layer.pond_bounding_y_down),
                "Z": float(-layer.pond_bounding_xz),
            },
            "BoundingMax": {
                "X": float(layer.pond_bounding_xz),
                "Y": float(layer.pond_bounding_y_up),
                "Z": float(layer.pond_bounding_xz),
            },
            "BarrierBlockSet": {
                "Inclusive": True,
                "Materials": [{"Solid": m} for m in layer.pond_barrier_materials],
            },
            "FillMaterial": {
                "Type": "Constant",
                "Material": {"Solid": layer.pond_fill_material},
            },
            "Pattern": pattern,
            "Scanner": scanner,
        }
        assignment = {"Type": "Constant", "Skip": False, "Prop": pond_prop}
    elif layer.prop_type == "cluster":
        # ClusterProp: organic groups of single-block Column props around a center point.
        # Children must be 1x1 Column props (validated at build time).
        # DistanceCurve controls radial density falloff; WeightedProps picks random columns.
        cluster_prop = {
            "Type": "Cluster",
            "Skip": False,
            "Range": layer.cluster_range,
            "Seed": layer.cluster_seed,
            "DistanceCurve": {
                "Type": "Manual",
                "Points": [
                    {"In": pt["distance"], "Out": pt["density"]}
                    for pt in layer.cluster_distance_curve
                ]
            },
            "WeightedProps": [
                {
                    "Weight": entry.get("weight", 1),
                    "ColumnProp": {
                        "Type": "Column", "Skip": False,
                        "ColumnBlocks": [
                            {"Y": 0, "Material": {"Solid": entry["block"]}}
                        ],
                        "Directionality": {
                            "Type": "Static", "Rotation": 0,
                            "Pattern": pattern,
                        },
                        "Scanner": {
                            "Type": "ColumnLinear", "Skip": False,
                            "MaxY": 5, "MinY": -5,
                            "RelativeToPosition": True,
                            "BaseHeightName": "Base",
                            "TopDownOrder": True, "ResultCap": 1,
                        }
                    }
                }
                for entry in layer.cluster_column_blocks
            ],
            "Pattern": pattern,
            "Scanner": scanner,
        }
        assignment = {"Type": "Constant", "Skip": False, "Prop": cluster_prop}
    elif layer.zones:
        # FieldFunction assignment: different props per noise zone.
        # Each zone maps a noise range to a different set of prefab paths.
        # Noise values outside any zone → EmptyProp (no prop placed = clearings).
        delimiters = []
        for zone in layer.zones:
            zone_prop = _build_prefab_prop(layer, biome, zone.prefab_paths, pattern, scanner)
            delimiters.append({
                "Min": zone.min_val,
                "Max": zone.max_val,
                "Assignments": {
                    "Type": "Constant", "Skip": False,
                    "Prop": zone_prop,
                }
            })

        assignment = {
            "Type": "FieldFunction", "Skip": False,
            "FieldFunction": {
                "Type": "SimplexNoise2D", "Skip": False,
                "Lacunarity": 2, "Persistence": 0.5, "Octaves": 1,
                "Scale": layer.zone_noise_scale, "Seed": layer.zone_seed,
            },
            "Delimiters": delimiters,
        }
    else:
        # Constant assignment: same prop set everywhere (existing behavior)
        prop = _build_prefab_prop(layer, biome, layer.prefab_paths, pattern, scanner)
        assignment = {
            "Type": "Constant", "Skip": False,
            "Prop": prop,
        }

    # Occurrence density — configurable per layer via occurrence_max
    occurrence_value = min(layer.occurrence_max, max(0.05, (1.0 - layer.skip_chance) * 0.35))

    return {
        "Skip": False, "Runtime": 0,
        "Positions": {
            "Type": "Occurrence", "Skip": False,
            "Seed": f"{layer.name}_occ",
            "FieldFunction": {
                "Type": "Normalizer", "Skip": False,
                "FromMin": -1, "FromMax": 1,
                "ToMin": 0.0, "ToMax": occurrence_value,
                "Inputs": [{
                    "Type": "SimplexNoise2D", "Skip": False,
                    "Lacunarity": 2, "Persistence": 0.5, "Octaves": 1,
                    "Scale": max(20, scaled_x * 2),
                    "Seed": f"{layer.name}_density",
                }]
            },
            "Positions": {
                "Type": "Mesh2D", "Skip": False,
                "PointsY": 0,
                "PointGenerator": {
                    "Type": "Mesh",
                    "ScaleX": scaled_x, "ScaleY": scaled_x, "ScaleZ": scaled_z,
                    "Jitter": layer.jitter, "Seed": f"{layer.name}_{size.name.lower()}",
                }
            }
        },
        "Assignments": assignment,
    }


def generate_props(biome: BiomeSpec, size: SizeSpec, wall_mode: str) -> list:
    """Generate Props array for a biome, filtering layers by arena radius.

    Layers with min_arena_radius/max_arena_radius constraints are only included
    when the current size's radius falls within their range. This enables
    progressive content: small arenas get wilderness, large arenas get settlements.
    """
    if not biome.prop_layers:
        return []

    # Filter layers by arena radius — enables size-dependent progression
    active_layers = [
        layer for layer in biome.prop_layers
        if layer.min_arena_radius <= size.radius <= layer.max_arena_radius
    ]

    # Props use SimplexNoise2D occurrence (not Axis), so they work for both wall modes
    return [generate_prop_layer(layer, biome, size) for layer in active_layers]


# ═══════════════════════════════════════════════════════════════════
# GENERATION: GRASS / GROUND COVER (Column props)
# ═══════════════════════════════════════════════════════════════════

def generate_grass_layer(biome: BiomeSpec) -> Optional[dict]:
    """Generate a grass/flower ground cover layer using Column props.

    Uses the vanilla Hytale pattern:
    - Column props place single blocks (grass blades, flowers)
    - Mesh2D with ScaleX=1 covers every block position
    - FieldFunction creates 3 density zones (sparse/moderate/dense)
    - Weighted assignment with SkipChance varies coverage per zone
    - Floor pattern ensures placement only on terrain surfaces

    Returns None if biome has no grass_blocks defined.
    """
    if not biome.grass_blocks:
        return None

    # Build weighted Column props for each grass/flower type
    weighted_entries = []
    for entry in biome.grass_blocks:
        block_id = entry["block"]
        weight = entry.get("weight", 1)
        # For ceiling biomes, extend scanner to cover wall slopes (MaxY=35).
        # TopDownOrder=True + ResultCap=1 = finds HIGHEST valid surface first.
        # Near walls: that's the slope surface. In center: that's the floor.
        grass_max_y = 35 if biome.ceiling_y > 0 else 8
        column_prop = {
            "Type": "Column", "Skip": False,
            "ColumnBlocks": [
                {"Y": 0, "Material": {"Solid": block_id}}
            ],
            "Directionality": {
                "Type": "Static",
                "Rotation": 0,
                "Pattern": _floor_pattern_blockset(biome.surface_materials),
            },
            "Scanner": {
                "Type": "ColumnLinear", "Skip": False,
                "MaxY": grass_max_y, "MinY": -5,
                "RelativeToPosition": False,
                "BaseHeightName": "Base",
                "TopDownOrder": True, "ResultCap": 1,
            },
        }
        weighted_entries.append({
            "Weight": weight,
            "Assignments": {
                "Type": "Constant", "Skip": False,
                "Prop": column_prop,
            }
        })

    # 3 density zones: sparse (noise < -0.2), moderate (-0.2 to 0.4), dense (> 0.4).
    # Skip chances are per-biome: Forest uses 92%/60%/20%, Desert uses 97%/85%/55%.
    assignment = {
        "Type": "FieldFunction", "Skip": False,
        "FieldFunction": {
            "Type": "SimplexNoise2D", "Skip": False,
            "Lacunarity": 2, "Persistence": 0.4, "Octaves": 1,
            "Scale": biome.grass_noise_scale, "Seed": biome.grass_seed,
        },
        "Delimiters": [
            {
                "Min": -1.0, "Max": -0.2,
                "Assignments": {
                    "Type": "Weighted", "Skip": False,
                    "Seed": f"{biome.grass_seed}_sparse",
                    "SkipChance": biome.grass_skip_sparse,
                    "WeightedAssignments": weighted_entries,
                },
            },
            {
                "Min": -0.2, "Max": 0.4,
                "Assignments": {
                    "Type": "Weighted", "Skip": False,
                    "Seed": f"{biome.grass_seed}_moderate",
                    "SkipChance": biome.grass_skip_moderate,
                    "WeightedAssignments": weighted_entries,
                },
            },
            {
                "Min": 0.4, "Max": 1.0,
                "Assignments": {
                    "Type": "Weighted", "Skip": False,
                    "Seed": f"{biome.grass_seed}_dense",
                    "SkipChance": biome.grass_skip_dense,
                    "WeightedAssignments": weighted_entries,
                },
            },
        ],
    }

    return {
        "Skip": False, "Runtime": 1,
        "Positions": {
            "Type": "Mesh2D", "Skip": False,
            "PointsY": 0,
            "PointGenerator": {
                "Type": "Mesh",
                "ScaleX": 1, "ScaleY": 1, "ScaleZ": 1,
                "Jitter": 0.0,
                "Seed": f"{biome.grass_seed}_mesh",
            }
        },
        "Assignments": assignment,
    }


def generate_ceiling_grass_layer(biome: BiomeSpec) -> Optional[dict]:
    """Generate a ceiling moss/vegetation layer using Column props.

    Mirrors floor grass but uses Ceiling pattern + upward scanner.
    Only generated for biomes with ceilings (ceiling_y > 0) and grass_blocks defined.
    Uses the same blocks as the floor (moss grows on both surfaces in caves).
    Sparser than floor (ceiling_skip multiplied by 1.3 — less coverage above).
    """
    if not biome.grass_blocks or biome.ceiling_y == 0:
        return None

    # Build weighted Column props — same blocks, Ceiling pattern
    weighted_entries = []
    for entry in biome.grass_blocks:
        block_id = entry["block"]
        weight = entry.get("weight", 1)
        column_prop = {
            "Type": "Column", "Skip": False,
            "ColumnBlocks": [
                {"Y": 0, "Material": {"Solid": block_id}}
            ],
            "Directionality": {
                "Type": "Static",
                "Rotation": 0,
                "Pattern": _ceiling_pattern_blockset(biome.surface_materials),
            },
            "Scanner": {
                "Type": "ColumnLinear", "Skip": False,
                "MaxY": 5, "MinY": -55,
                "RelativeToPosition": False,
                "BaseHeightName": "Ceiling",
                "TopDownOrder": False, "ResultCap": 1,
            },
        }
        weighted_entries.append({
            "Weight": weight,
            "Assignments": {
                "Type": "Constant", "Skip": False,
                "Prop": column_prop,
            }
        })

    # Sparser than floor — multiply skip chances (cap at 0.98)
    sparse_skip = min(0.98, biome.grass_skip_sparse * 1.2)
    moderate_skip = min(0.95, biome.grass_skip_moderate * 1.3)
    dense_skip = min(0.90, biome.grass_skip_dense * 1.5)

    assignment = {
        "Type": "FieldFunction", "Skip": False,
        "FieldFunction": {
            "Type": "SimplexNoise2D", "Skip": False,
            "Lacunarity": 2, "Persistence": 0.4, "Octaves": 1,
            "Scale": biome.grass_noise_scale, "Seed": f"{biome.grass_seed}_ceiling",
        },
        "Delimiters": [
            {
                "Min": -1.0, "Max": -0.2,
                "Assignments": {
                    "Type": "Weighted", "Skip": False,
                    "Seed": f"{biome.grass_seed}_ceil_sparse",
                    "SkipChance": sparse_skip,
                    "WeightedAssignments": weighted_entries,
                },
            },
            {
                "Min": -0.2, "Max": 0.4,
                "Assignments": {
                    "Type": "Weighted", "Skip": False,
                    "Seed": f"{biome.grass_seed}_ceil_mod",
                    "SkipChance": moderate_skip,
                    "WeightedAssignments": weighted_entries,
                },
            },
            {
                "Min": 0.4, "Max": 1.0,
                "Assignments": {
                    "Type": "Weighted", "Skip": False,
                    "Seed": f"{biome.grass_seed}_ceil_dense",
                    "SkipChance": dense_skip,
                    "WeightedAssignments": weighted_entries,
                },
            },
        ],
    }

    return {
        "Skip": False, "Runtime": 1,
        "Positions": {
            "Type": "Mesh2D", "Skip": False,
            "PointsY": 0,
            "PointGenerator": {
                "Type": "Mesh",
                "ScaleX": 1, "ScaleY": 1, "ScaleZ": 1,
                "Jitter": 0.0,
                "Seed": f"{biome.grass_seed}_ceil_mesh",
            }
        },
        "Assignments": assignment,
    }



def generate_wall_grass_layer(biome: BiomeSpec) -> Optional[dict]:
    """Generate wall vegetation using Column props with Wall pattern.

    Places vine and moss blocks ON cave walls (air adjacent to solid terrain).
    Uses Plant_Vine_Wall (designed for vertical surfaces) + moss blocks.
    Only for ceiling biomes where walls are tall enough to decorate.

    Key: mesh_scale=2 (dense grid needed for Wall pattern 1-block adjacency),
    ResultCap=4 (multiple heights per column), skip_chance=0.80 (compensate density).
    """
    if biome.ceiling_y == 0:
        return None

    wall_blocks = [
        {"block": "Plant_Vine_Wall", "weight": 30},
        {"block": "Plant_Moss_Block_Green", "weight": 25},
        {"block": "Plant_Moss_Green_Dark", "weight": 20},
        {"block": "Plant_Vine", "weight": 15},
        {"block": "Plant_Moss_Rug_Lime", "weight": 10},
    ]

    weighted_entries = []
    for entry in wall_blocks:
        block_id = entry["block"]
        weight = entry["weight"]
        column_prop = {
            "Type": "Column", "Skip": False,
            "ColumnBlocks": [
                {"Y": 0, "Material": {"Solid": block_id}}
            ],
            "Directionality": {
                "Type": "Static",
                "Rotation": 0,
                "Pattern": _wall_pattern_blockset(biome.surface_materials),
            },
            "Scanner": {
                "Type": "ColumnLinear", "Skip": False,
                "MaxY": 30, "MinY": -2,
                "RelativeToPosition": False,
                "BaseHeightName": "Base",
                "TopDownOrder": False, "ResultCap": 4,
            },
        }
        weighted_entries.append({
            "Weight": weight,
            "Assignments": {
                "Type": "Constant", "Skip": False,
                "Prop": column_prop,
            }
        })

    assignment = {
        "Type": "Weighted", "Skip": False,
        "Seed": "wall_veg",
        "SkipChance": 0.80,
        "WeightedAssignments": weighted_entries,
    }

    return {
        "Skip": False, "Runtime": 1,
        "Positions": {
            "Type": "Mesh2D", "Skip": False,
            "PointsY": 0,
            "PointGenerator": {
                "Type": "Mesh",
                "ScaleX": 2, "ScaleY": 2, "ScaleZ": 2,
                "Jitter": 0.0,
                "Seed": "wall_veg_mesh",
            }
        },
        "Assignments": assignment,
    }


# =================================================================
# GENERATION: BIOME + WORLD STRUCTURE
# =================================================================

def generate_biome(biome: BiomeSpec, size: SizeSpec, wall_mode: str) -> dict:
    """Assemble complete biome JSON."""
    biome_name = f"{biome.biome_prefix}{size.suffix}"
    size_label = f" ({size.name})" if size.suffix else ""

    # Three terrain paradigms: solid_mass for dungeon biomes, circular/square for open arenas.
    if biome.arena_type == "solid_mass":
        terrain = generate_density_solid_mass(biome, size)
    elif wall_mode == "circular":
        terrain = generate_density_circular(biome, size)
    else:
        terrain = generate_density_square(biome, size)

    result = {
        "$Title": f"[ROOT] Biome - Realm {biome.name} Arena{size_label}",
        "Name": biome_name,
        "Terrain": terrain,
        "MaterialProvider": generate_material_provider(biome),
    }

    props = generate_props(biome, size, wall_mode)

    # Add grass/flower ground cover layer (Column props)
    grass_layer = generate_grass_layer(biome)
    if grass_layer:
        props.append(grass_layer)

    # Add ceiling moss/vegetation for biomes with ceilings
    ceiling_grass_layer = generate_ceiling_grass_layer(biome)
    if ceiling_grass_layer:
        props.append(ceiling_grass_layer)

    # NOTE: Wall vegetation is handled via PropLayer("wall_ledge_moss") in BiomeSpec
    # with scanner_max_y=30 and Floor pattern — catches craggy boundary ledges naturally.
    # The generate_wall_grass_layer() function (Wall pattern) is deprecated — places on floor.

    if props:
        result["Props"] = props

    # Use custom environment for fog/atmosphere (Realm_Forest_Env, etc.)
    env_name = f"Realm_{biome.name}_Env"
    result["EnvironmentProvider"] = {"Type": "Constant", "Environment": env_name}
    result["TintProvider"] = generate_tint_provider(biome)

    return result


def generate_environment(biome: BiomeSpec) -> dict:
    """Generate a custom Environment JSON with weather forecasts for all 24 hours."""
    weather_name = f"Realm_{biome.name}_Weather"
    forecasts = {}
    for hour in range(24):
        forecasts[str(hour)] = [{"WeatherId": weather_name, "Weight": 1}]
    env: dict = {
        "WeatherForecasts": forecasts,
        "SpawnDensity": 1.0
    }
    if biome.water_tint:
        env["WaterTint"] = biome.water_tint
    return env


def generate_weather(biome: BiomeSpec) -> dict:
    """Generate a custom Weather JSON with biome-specific fog, sky, particles, and screen effects.

    Structure matches working weather format:
    - FogDistance: [near, far] — negative near values for enclosed biomes
    - Particle: SystemId + Color for floating atmospheric particles
    - ScreenEffect + ScreenEffectColors: full-screen texture overlay
    - FogDensities/FogHeightFalloffs: single entry (not 24 identical entries)
    """
    weather: dict = {
        "FogDistance": biome.fog_distance,
        "FogColors": [{"Hour": 0, "Color": biome.fog_color}],
        "FogDensities": [{"Hour": 0, "Value": biome.fog_density}],
        "FogHeightFalloffs": [{"Hour": 0, "Value": 1}],
        "SkyTopColors": [{"Hour": 0, "Color": biome.sky_top_color}],
        "SkyBottomColors": [{"Hour": 0, "Color": biome.sky_bottom_color}],
        "SkySunsetColors": [{"Hour": 0, "Color": biome.sky_bottom_color}],
        "SunlightColors": None,
        "SunColors": None,
    }

    # Particles — floating atmospheric effect (proven pattern)
    if biome.particle_id:
        weather["Particle"] = {
            "SystemId": biome.particle_id,
            "Scale": 1,
            "Color": biome.particle_color,
        }

    # Screen effect — full-screen texture overlay (proven pattern)
    if biome.screen_effect:
        weather["ScreenEffect"] = biome.screen_effect
        if biome.screen_effect_color:
            weather["ScreenEffectColors"] = [{"Hour": 0, "Color": biome.screen_effect_color}]
    else:
        weather["ScreenEffect"] = None

    # Color filter — post-processing tint
    if biome.color_filter:
        weather["ColorFilters"] = [{"Hour": 0, "Color": biome.color_filter}]

    return weather


def generate_world_structure(biome_name: str, biome: Optional[BiomeSpec] = None, base_y: int = FLOOR_Y) -> dict:
    """WorldStructure JSON with Framework DecimalConstants for BaseHeight support.

    Supports per-biome overrides:
    - Mountains: base_y_override raises the Base height (elevated plateau)
    - Caverns: ceiling_y adds a Ceiling DecimalConstant (enclosed cave)
    """
    actual_base_y = base_y
    if biome and biome.base_y_override > 0:
        actual_base_y = biome.base_y_override

    entries = [
        {"Name": "Base", "Value": actual_base_y},
        {"Name": "Water", "Value": actual_base_y},
        {"Name": "Bedrock", "Value": 0},
    ]

    if biome and biome.ceiling_y > 0:
        entries.append({"Name": "Ceiling", "Value": biome.ceiling_y})

    return {
        "Type": "NoiseRange",
        "Biomes": [],
        "DefaultBiome": biome_name,
        "DefaultTransitionDistance": 32,
        "MaxBiomeEdgeDistance": 32,
        "Framework": [
            {
                "Type": "DecimalConstants",
                "Entries": entries
            }
        ],
        "Density": {"Type": "Constant", "Skip": False, "Value": 0}
    }


def generate_instance_bson(world_structure_name: str, biome_display: str) -> dict:
    """Generate an instance.bson file for InstancesPlugin.

    CRITICAL: Hytale checks for 'instance.bson' (NOT config.json).
    WorldGen must use Type=HytaleGenerator + WorldStructure (NOT Type=Hytale + Name).
    UUID must be exactly 16 bytes (random, not derived from name).

    Proven format from fix-realm-uuids.py and community implementations.
    """
    import base64
    import uuid as uuid_mod
    # Random 16-byte UUID (Hytale codec requires exactly 16 bytes)
    uuid_bytes = uuid_mod.uuid4().bytes
    uuid_b64 = base64.b64encode(uuid_bytes).decode('ascii')

    return {
        "Version": 4,
        "UUID": {"$binary": uuid_b64, "$type": "04"},
        "Seed": 12345,
        "SpawnProvider": {
            "Id": "Global",
            "SpawnPoint": {"X": 0.0, "Y": 65.0, "Z": 0.0, "Pitch": 0.0, "Yaw": 0.0, "Roll": 0.0}
        },
        "WorldGen": {
            "Type": "HytaleGenerator",
            "WorldStructure": world_structure_name
        },
        "WorldMap": {"Type": "Disabled"},
        "ChunkStorage": {"Type": "Hytale"},
        "ChunkConfig": {},
        "IsTicking": True,
        "IsBlockTicking": True,
        "IsPvpEnabled": True,
        "IsFallDamageEnabled": True,
        "IsGameTimePaused": False,
        "GameTime": "0001-01-01T12:00:00.000Z",
        "RequiredPlugins": {},
        "GameMode": "Adventure",
        "IsSpawningNPC": False,
        "IsSpawnMarkersEnabled": False,
        "IsAllNPCFrozen": False,
        "IsCompassUpdating": False,
        "IsSavingPlayers": False,
        "IsSavingChunks": False,
        "SaveNewChunks": False,
        "IsUnloadingChunks": True,
        "IsObjectiveMarkersEnabled": False,
        "DeleteOnUniverseStart": False,
        "DeleteOnRemove": True,
        "ResourceStorage": {"Type": "Hytale"},
        "Plugin": {
            "Instance": {
                "RemovalConditions": [
                    {"Type": "WorldEmpty", "GracePeriod": 30.0},
                    {"Type": "Timeout", "Duration": 1800.0}
                ],
                "PreventReconnection": False,
                "Discovery": {
                    "TitleKey": f"{biome_display} Realm",
                    "SubtitleKey": "Combat Zone",
                    "Display": True,
                    "AlwaysDisplay": False,
                    "Icon": "Realm_Arena.png",
                    "Major": True,
                    "Duration": 3.0,
                    "FadeInDuration": 1.0,
                    "FadeOutDuration": 1.0
                }
            }
        }
    }


# ═══════════════════════════════════════════════════════════════════
# FILE OUTPUT
# ═══════════════════════════════════════════════════════════════════

def write_json(path: Path, data: dict, dry_run: bool) -> None:
    if dry_run:
        print(f"  [DRY RUN] Would write: {path}")
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print(f"  Written: {path.relative_to(PROJECT_ROOT)}")


def generate_all(biome_filter: Optional[str], wall_mode: str, dry_run: bool) -> None:
    biomes_to_generate = BIOMES + [ARENA_BASIC]

    if biome_filter:
        biomes_to_generate = [
            b for b in biomes_to_generate if b.name.upper() == biome_filter.upper()
        ]
        if not biomes_to_generate:
            print(f"ERROR: Unknown biome '{biome_filter}'")
            print(f"Available: {', '.join(b.name for b in BIOMES)}, Arena_Basic")
            sys.exit(1)

    total_biomes = total_structures = total_envs = total_instances = 0
    env_root = ASSET_ROOT / "Server" / "Environments"
    weather_root = ASSET_ROOT / "Server" / "Weathers"
    generated_envs: set[str] = set()

    for biome in biomes_to_generate:
        # Arena_Basic gets one mid-range tier (R50). All real biomes get all tiers.
        sizes = [SizeSpec("R50", "_R50", 50)] if biome.name == "Arena_Basic" else SIZES

        print(f"\n{'='*60}")
        print(f"  Biome: {biome.name} ({len(sizes)} tiers)")
        print(f"{'='*60}")

        # Generate environment + weather once per biome type (shared across sizes)
        env_name = f"Realm_{biome.name}_Env"
        weather_name = f"Realm_{biome.name}_Weather"
        if env_name not in generated_envs:
            env_data = generate_environment(biome)
            write_json(env_root / f"{env_name}.json", env_data, dry_run)
            weather_data = generate_weather(biome)
            write_json(weather_root / f"{weather_name}.json", weather_data, dry_run)
            generated_envs.add(env_name)
            total_envs += 1

        for size in sizes:
            biome_name = f"{biome.biome_prefix}{size.suffix}"

            # Biome JSON (terrain density, materials, props)
            biome_data = generate_biome(biome, size, wall_mode)
            biome_path = BIOME_ROOT / biome_name / f"{biome_name}.json"
            write_json(biome_path, biome_data, dry_run)
            total_biomes += 1

            # WorldStructure JSON (DecimalConstants, biome reference)
            ws_name = "Realm_Basic" if biome.name == "Arena_Basic" else biome_name
            ws_data = generate_world_structure(biome_name, biome=biome)
            ws_path = WORLDSTRUCT_ROOT / f"{ws_name}.json"
            write_json(ws_path, ws_data, dry_run)
            total_structures += 1

            # Instance template instance.bson (required for InstancesPlugin.spawnInstance).
            # Hytale checks for instance.bson specifically (NOT config.json).
            # Instance directory MUST be lowercase (Hytale convention).
            instance_data = generate_instance_bson(ws_name, biome.name)
            instance_dir_name = biome_name.lower()
            instance_path = INSTANCE_ROOT / instance_dir_name / "instance.bson"
            write_json(instance_path, instance_data, dry_run)
            total_instances += 1

    print(f"\n{'='*60}")
    action = "Would generate" if dry_run else "Generated"
    print(f"  {action}: {total_biomes} biomes, {total_structures} WorldStructures, {total_instances} instances, {total_envs * 2} env/weather")
    print(f"  Wall mode: {wall_mode}")
    print(f"{'='*60}")


def main():
    parser = argparse.ArgumentParser(
        description="Generate WorldGenV2 realm biome and WorldStructure JSON files"
    )
    parser.add_argument("--biome", type=str, default=None,
                        help="Generate only one biome family (e.g., FOREST)")
    parser.add_argument("--wall-mode", type=str, choices=["circular", "square"],
                        default="circular", help="Wall shape mode")
    parser.add_argument("--dry-run", action="store_true",
                        help="Preview what would be generated")
    args = parser.parse_args()
    generate_all(args.biome, args.wall_mode, args.dry_run)


if __name__ == "__main__":
    main()
