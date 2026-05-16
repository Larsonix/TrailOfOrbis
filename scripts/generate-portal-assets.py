#!/usr/bin/env python3
"""
Generates ALL portal visual assets for the realm biome system.

For each biome, produces:
  - 1 block JSON — biome-colored portal block variant
  - 1 portal particle system (10 spawners, full MagicPortal equivalent)
  - 1 stones particle system (4 spawners, full MagicPortal_Stones equivalent)
  - 14 particle spawners — all recolored from vanilla templates
  - 1 rarity glow particle system + spawner (shared, neutral white)

Approach: Load vanilla spawner JSONs as templates, hue-shift all colors
to the biome's target hue, write out biome-specific files.

Run from project root:
    python scripts/generate-portal-assets.py
"""

import colorsys
import copy
import json
import os
import re
import sys
from pathlib import Path

# =====================================================================
# PATHS
# =====================================================================

VANILLA_PORTAL_SPAWNERS = Path("../Assets/Server/Particles/Spell/Portal/Spawners/MagicPortal")
VANILLA_PORTAL_SYSTEM = Path("../Assets/Server/Particles/Spell/Portal/MagicPortal.particlesystem")
VANILLA_STONES_SYSTEM = Path("../Assets/Server/Particles/Spell/Portal/MagicPortal_Stones.particlesystem")

ASSETS_ROOT = Path("src/main/resources/hytale-assets")
BLOCK_DIR = ASSETS_ROOT / "Server" / "Item" / "Items" / "Portal"
PARTICLE_DIR = ASSETS_ROOT / "Server" / "Particles" / "Realm" / "Portals"
SPAWNER_DIR = PARTICLE_DIR / "Spawners"
DROP_DIR = ASSETS_ROOT / "Server" / "Item" / "DropLists"

# The 10 spawners used by MagicPortal.particlesystem
PORTAL_SPAWNER_NAMES = [
    "MagicPortal_Spawn", "MagicPortal_Flash", "MagicPortal_CenterCircle",
    "MagicPortal_Waves", "MagicPortal_SmokeAlpha", "MagicPortal_Sparks",
    "MagicPortal_SparksRunes", "MagicPortal_Center", "MagicPortal_CenterGlow",
    # MagicPortal_SmokeMiddle removed — its Portal_Wind.png texture renders as
    # flickering semi-transparent squares when overlaid on the biome preview image
]

# The 4 spawners used by MagicPortal_Stones.particlesystem
STONES_SPAWNER_NAMES = [
    "MagicPortal_StonesSpawn", "MagicPortal_StonesCircles",
    "MagicPortal_StonesCircles2", "MagicPortal_StonesGlow"
]

# Size of a single preview frame (vanilla uses 128x128)
PREVIEW_FRAME_SIZE = 128


def detect_sprite_sheet_frames(texture_path, frame_size):
    """Detect frame count from sprite sheet texture dimensions.
    Returns 1 for single-frame textures or missing files."""
    if not texture_path.exists():
        return 1
    try:
        from PIL import Image
        img = Image.open(texture_path)
        w, h = img.size
        if w <= frame_size:
            return 1
        # Frames laid out left-to-right in a single row
        return w // frame_size
    except Exception:
        return 1


# =====================================================================
# BIOME DEFINITIONS
# =====================================================================

BIOMES = [
    {"key": "Forest",        "hue": 120, "sat_mult": 0.65, "val_mult": 0.90, "light_hex": "#3a3", "sound": "SFX_Portal_Neutral"},
    {"key": "Desert",        "hue": 45,  "sat_mult": 0.65, "val_mult": 0.95, "light_hex": "#ec2", "sound": "SFX_Portal_Neutral"},
    {"key": "Volcano",       "hue": 0,   "sat_mult": 0.70, "val_mult": 0.85, "light_hex": "#c33", "sound": "SFX_Env_Emit_Fluid_Lava"},
    {"key": "Tundra",        "hue": 195, "sat_mult": 0.45, "val_mult": 1.05, "light_hex": "#aef", "sound": "SFX_Portal_Neutral"},
    {"key": "Swamp",         "hue": 150, "sat_mult": 0.50, "val_mult": 0.65, "light_hex": "#3a5", "sound": "SFX_Frog_Croak"},
    {"key": "Beach",         "hue": 35,  "sat_mult": 0.60, "val_mult": 1.00, "light_hex": "#fb6", "sound": "SFX_Emit_Lake_Water"},
    {"key": "Jungle",        "hue": 130, "sat_mult": 0.65, "val_mult": 0.75, "light_hex": "#0b2", "sound": "SFX_Portal_Neutral"},
    {"key": "Caverns",       "hue": 220, "sat_mult": 0.45, "val_mult": 0.75, "light_hex": "#569", "sound": "SFX_Gem_Emit_Loop"},
    {"key": "Frozen_Crypts", "hue": 200, "sat_mult": 0.55, "val_mult": 0.95, "light_hex": "#5ae", "sound": "SFX_Gem_Emit_Loop"},
    {"key": "Sand_Tombs",    "hue": 30,  "sat_mult": 0.60, "val_mult": 0.90, "light_hex": "#ca6", "sound": "SFX_Portal_Neutral"},
    {"key": "Void",          "hue": 275, "sat_mult": 0.70, "val_mult": 0.85, "light_hex": "#a2f", "sound": "SFX_Portal_Void"},
]

# Vanilla portal colors average hue is ~200 (cyan-blue range)
VANILLA_AVG_HUE = 200

# =====================================================================
# COLOR REMAPPING
# =====================================================================

HEX_PATTERN = re.compile(r'^#([0-9a-fA-F]{6})$')

def parse_hex(hex_str):
    """Parse #RRGGBB to (r, g, b) floats 0-1."""
    m = HEX_PATTERN.match(hex_str)
    if not m:
        return None
    val = int(m.group(1), 16)
    return ((val >> 16) & 0xFF) / 255.0, ((val >> 8) & 0xFF) / 255.0, (val & 0xFF) / 255.0

def to_hex(r, g, b):
    """Convert (r, g, b) floats 0-1 to #RRGGBB."""
    return "#{:02x}{:02x}{:02x}".format(
        max(0, min(255, int(r * 255))),
        max(0, min(255, int(g * 255))),
        max(0, min(255, int(b * 255))))

def remap_color(hex_str, target_hue_deg, sat_mult, val_mult):
    """Hue-shift a hex color from vanilla blue/cyan to the target biome hue."""
    rgb = parse_hex(hex_str)
    if rgb is None:
        return hex_str  # Not a valid hex color, return as-is

    h, s, v = colorsys.rgb_to_hsv(*rgb)

    # Shift hue: vanilla range is ~180-240deg (0.5-0.67 in 0-1 space)
    # Map to target hue
    hue_shift = (target_hue_deg / 360.0) - (VANILLA_AVG_HUE / 360.0)
    new_h = (h + hue_shift) % 1.0

    # Adjust saturation and value
    new_s = min(1.0, max(0.0, s * sat_mult))
    new_v = min(1.0, max(0.0, v * val_mult))

    r, g, b = colorsys.hsv_to_rgb(new_h, new_s, new_v)
    return to_hex(r, g, b)


def remap_colors_in_json(obj, target_hue, sat_mult, val_mult):
    """Recursively find and remap all Color hex strings in a JSON structure."""
    if isinstance(obj, dict):
        result = {}
        for k, v in obj.items():
            if k == "Color" and isinstance(v, str) and v.startswith("#"):
                result[k] = remap_color(v, target_hue, sat_mult, val_mult)
            else:
                result[k] = remap_colors_in_json(v, target_hue, sat_mult, val_mult)
        return result
    elif isinstance(obj, list):
        return [remap_colors_in_json(item, target_hue, sat_mult, val_mult) for item in obj]
    return obj


def rename_spawner_ids(obj, old_prefix, new_prefix):
    """Recursively rename SpawnerId references from old to new prefix."""
    if isinstance(obj, dict):
        result = {}
        for k, v in obj.items():
            if k == "SpawnerId" and isinstance(v, str) and v.startswith(old_prefix):
                result[k] = v.replace(old_prefix, new_prefix, 1)
            else:
                result[k] = rename_spawner_ids(v, old_prefix, new_prefix)
        return result
    elif isinstance(obj, list):
        return [rename_spawner_ids(item, old_prefix, new_prefix) for item in obj]
    return obj


# =====================================================================
# GENERATION: PARTICLE SPAWNERS
# =====================================================================

def generate_spawners(biome):
    """Generate all 14 biome-colored spawner files from vanilla templates."""
    key = biome["key"]
    hue = biome["hue"]
    sat = biome["sat_mult"]
    val = biome["val_mult"]
    prefix = f"Realm_Portal_{key}"
    stones_prefix = f"Realm_Stones_{key}"
    biome_dir = SPAWNER_DIR / key
    biome_dir.mkdir(parents=True, exist_ok=True)

    count = 0

    # Portal spawners (10)
    for name in PORTAL_SPAWNER_NAMES:
        src_path = VANILLA_PORTAL_SPAWNERS / f"{name}.particlespawner"
        if not src_path.exists():
            print(f"  WARNING: Vanilla spawner not found: {src_path}")
            continue

        with open(src_path, "r", encoding="utf-8") as f:
            template = json.load(f)

        recolored = remap_colors_in_json(template, hue, sat, val)
        new_name = name.replace("MagicPortal", prefix)
        out_path = biome_dir / f"{new_name}.particlespawner"
        write_json(out_path, recolored)
        count += 1

    # Stones spawners (4)
    for name in STONES_SPAWNER_NAMES:
        src_path = VANILLA_PORTAL_SPAWNERS / f"{name}.particlespawner"
        if not src_path.exists():
            print(f"  WARNING: Vanilla spawner not found: {src_path}")
            continue

        with open(src_path, "r", encoding="utf-8") as f:
            template = json.load(f)

        recolored = remap_colors_in_json(template, hue, sat, val)
        new_name = name.replace("MagicPortal_Stones", stones_prefix)
        out_path = biome_dir / f"{new_name}.particlespawner"
        write_json(out_path, recolored)
        count += 1

    # Preview spawner (1) — biome landscape billboard, same as vanilla CreativeWorld_Taiga
    # Supports sprite sheet animation: if the texture is wider than PREVIEW_FRAME_SIZE,
    # auto-detects frame count and generates Animation keyframes for flipbook playback.
    VANILLA_PREVIEW_SPAWNER = Path("../Assets/Server/Particles/Spell/Portal/Spawners/CreativeWorld_Taiga.particlespawner")
    if VANILLA_PREVIEW_SPAWNER.exists():
        with open(VANILLA_PREVIEW_SPAWNER, "r", encoding="utf-8") as f:
            preview_template = json.load(f)

        texture_rel = f"Particles/Textures/Realm/Realm_Preview_{key}.png"
        preview_template["Particle"]["Texture"] = texture_rel
        preview_template.pop("$Comment", None)

        # Auto-detect sprite sheet: check if the actual texture file exists and is wider than one frame
        texture_path = ASSETS_ROOT / "Common" / texture_rel
        frame_count = detect_sprite_sheet_frames(texture_path, PREVIEW_FRAME_SIZE)

        if frame_count > 1:
            # Sprite sheet detected — configure animated playback
            # Keep vanilla timing values (MaxConcurrent, LifeSpan, SpawnRate) for
            # the same dynamic shimmering feel as vanilla portal previews
            preview_template["Particle"]["FrameSize"] = {
                "Width": PREVIEW_FRAME_SIZE, "Height": PREVIEW_FRAME_SIZE
            }

            # Generate evenly-spaced keyframes: 0% = frame 0, 100% = last frame
            anim = preview_template["Particle"]["Animation"]
            # Clear existing FrameIndex keyframes, keep opacity animation
            for pct_key in list(anim.keys()):
                if pct_key.isdigit() and "FrameIndex" in anim[pct_key]:
                    del anim[pct_key]["FrameIndex"]

            # Add frame keyframes evenly distributed across the lifetime
            for i in range(frame_count):
                pct = str(int(i * 100 / max(frame_count - 1, 1)))
                if pct not in anim:
                    anim[pct] = {}
                anim[pct]["FrameIndex"] = {"Min": i, "Max": i}

            print(f"    [{key}] sprite sheet: {frame_count} frames detected")
        else:
            # Single frame — keep FrameSize matching full texture (vanilla behavior)
            preview_template["Particle"]["FrameSize"] = {
                "Width": PREVIEW_FRAME_SIZE, "Height": PREVIEW_FRAME_SIZE
            }

        preview_name = f"Realm_Preview_{key}"
        out_path = biome_dir / f"{preview_name}.particlespawner"
        write_json(out_path, preview_template)
        count += 1

    return count


# =====================================================================
# GENERATION: PARTICLE SYSTEMS
# =====================================================================

def generate_portal_system(biome):
    """Generate the main portal particle system (9 spawners + preview).
    SmokeMiddle is excluded — its flat horizontal texture causes flickering
    square artifacts when layered over the biome preview image."""
    key = biome["key"]
    prefix = f"Realm_Portal_{key}"

    with open(VANILLA_PORTAL_SYSTEM, "r", encoding="utf-8") as f:
        template = json.load(f)

    # Rename all SpawnerId references
    system = rename_spawner_ids(template, "MagicPortal", prefix)

    # Remove SmokeMiddle from the system spawner list (matches renamed ID)
    system["Spawners"] = [
        s for s in system["Spawners"]
        if "SmokeMiddle" not in s.get("SpawnerId", "")
    ]

    # Add the biome preview spawner (like vanilla CreativeWorld_Taiga)
    system["Spawners"].append({
        "SpawnerId": f"Realm_Preview_{key}",
        "PositionOffset": {"X": 0, "Z": 0.15}
    })

    out_path = PARTICLE_DIR / f"{prefix}.particlesystem"
    write_json(out_path, system)


def generate_stones_system(biome):
    """Generate the stones particle system (4 spawners)."""
    key = biome["key"]
    stones_prefix = f"Realm_Stones_{key}"

    with open(VANILLA_STONES_SYSTEM, "r", encoding="utf-8") as f:
        template = json.load(f)

    # Rename SpawnerId references
    system = rename_spawner_ids(template, "MagicPortal_Stones", stones_prefix)

    out_path = PARTICLE_DIR / f"{stones_prefix}.particlesystem"
    write_json(out_path, system)


def generate_rarity_aura():
    """Generate the 3-layer rarity aura particle system.

    Replaces the old single-spawner Realm_Rarity_Glow with three distinct layers:
      - Smoke: visible rising smoke ring around portal (primary rarity visual)
      - Core: bright center glow pulse at portal position
      - Sparkle: tiny bright flashes for Epic+ maps

    All spawners use white base color (#ffffff) for clean runtime Color tinting
    via ParticleUtil.spawnParticleEffect(). Each spawner has a short LifeSpan
    (0.5s) so instances self-terminate before the next 500ms Java tick —
    prevents the particle leak bug where immortal spawner instances accumulated.
    """
    spawner_dir = SPAWNER_DIR / "Shared"
    spawner_dir.mkdir(parents=True, exist_ok=True)

    # Clean up old single-spawner files
    old_files = [
        spawner_dir / "Realm_Rarity_Glow.particlespawner",
        PARTICLE_DIR / "Realm_Rarity_Glow.particlesystem",
    ]
    for old in old_files:
        if old.exists():
            old.unlink()
            print(f"    Removed old file: {old.name}")

    # ── Layer 1: Smoke Ring ──────────────────────────────────────────
    # The primary visual — colored smoke drifting upward in a circle.
    # Spawned at ring positions around the portal by Java code.
    smoke_spawner = {
        "RenderMode": "BlendAdd",
        "ParticleRotationInfluence": "Billboard",
        "MaxConcurrentParticles": 8,
        "ParticleLifeSpan": {"Min": 1.2, "Max": 2.0},
        "SpawnRate": {"Min": 1.0, "Max": 1.0},
        "LinearFiltering": True,
        "LightInfluence": 0,
        "LifeSpan": 0.5,
        "InitialVelocity": {
            "Speed": {"Min": 0.2, "Max": 0.5},
            "Pitch": {"Min": 75, "Max": 105},
            "Yaw": {"Min": -180, "Max": 180}
        },
        "EmitOffset": {
            "Y": {"Min": -0.2, "Max": 0.2}
        },
        "Particle": {
            "ScaleRatioConstraint": "OneToOne",
            "Animation": {
                "0": {"Opacity": 0.0},
                "20": {"Opacity": 0.5},
                "65": {"Opacity": 0.35},
                "100": {"Opacity": 0.0}
            },
            "InitialAnimationFrame": {
                "Scale": {"X": {"Min": 0.15, "Max": 0.30}},
                "Opacity": 0.5,
                "Color": "#ffffff",
                "Rotation": {"Z": {"Min": -180, "Max": 180}}
            },
            "Texture": "Particles/Textures/Basic/Glow.png"
        },
        "SpawnBurst": True,
        "ParticleRotateWithSpawner": False
    }
    write_json(spawner_dir / "Realm_Rarity_Smoke.particlespawner", smoke_spawner)
    write_json(PARTICLE_DIR / "Realm_Rarity_Smoke.particlesystem", {
        "Spawners": [{"SpawnerId": "Realm_Rarity_Smoke"}],
        "CullDistance": 50.0,
        "IsImportant": True
    })

    # ── Layer 2: Core Glow ───────────────────────────────────────────
    # Bright center pulse at the portal position. Spawned once per tick
    # at the portal center by Java code (not at ring positions).
    core_spawner = {
        "RenderMode": "BlendAdd",
        "ParticleRotationInfluence": "Billboard",
        "MaxConcurrentParticles": 2,
        "ParticleLifeSpan": {"Min": 0.6, "Max": 1.0},
        "SpawnRate": {"Min": 1.0, "Max": 1.0},
        "LinearFiltering": True,
        "LightInfluence": 0,
        "LifeSpan": 0.5,
        "InitialVelocity": {
            "Speed": {"Min": 0.0, "Max": 0.1},
            "Pitch": {"Min": 80, "Max": 100},
            "Yaw": {"Min": -180, "Max": 180}
        },
        "Particle": {
            "ScaleRatioConstraint": "OneToOne",
            "Animation": {
                "0": {"Opacity": 0.0},
                "15": {"Opacity": 0.6},
                "70": {"Opacity": 0.3},
                "100": {"Opacity": 0.0}
            },
            "InitialAnimationFrame": {
                "Scale": {"X": {"Min": 0.25, "Max": 0.40}},
                "Opacity": 0.6,
                "Color": "#ffffff"
            },
            "Texture": "Particles/Textures/Basic/Glow.png"
        },
        "SpawnBurst": True,
        "ParticleRotateWithSpawner": False
    }
    write_json(spawner_dir / "Realm_Rarity_Core.particlespawner", core_spawner)
    write_json(PARTICLE_DIR / "Realm_Rarity_Core.particlesystem", {
        "Spawners": [{"SpawnerId": "Realm_Rarity_Core"}],
        "CullDistance": 50.0,
        "IsImportant": True
    })

    # ── Layer 3: Sparkle Accents ─────────────────────────────────────
    # Tiny bright flashes that burst outward. Only spawned for Epic+
    # maps by Java code. Adds visual intensity for high-rarity portals.
    sparkle_spawner = {
        "RenderMode": "BlendAdd",
        "ParticleRotationInfluence": "Billboard",
        "MaxConcurrentParticles": 3,
        "ParticleLifeSpan": {"Min": 0.3, "Max": 0.6},
        "SpawnRate": {"Min": 1.0, "Max": 1.0},
        "LinearFiltering": True,
        "LightInfluence": 0,
        "LifeSpan": 0.5,
        "InitialVelocity": {
            "Speed": {"Min": 0.5, "Max": 1.2},
            "Pitch": {"Min": 40, "Max": 140},
            "Yaw": {"Min": -180, "Max": 180}
        },
        "Particle": {
            "ScaleRatioConstraint": "OneToOne",
            "Animation": {
                "0": {"Opacity": 0.0},
                "15": {"Opacity": 1.0},
                "60": {"Opacity": 0.5},
                "100": {"Opacity": 0.0}
            },
            "InitialAnimationFrame": {
                "Scale": {"X": {"Min": 0.03, "Max": 0.06}},
                "Opacity": 1.0,
                "Color": "#ffffff",
                "Rotation": {"Z": {"Min": -180, "Max": 180}}
            },
            "Texture": "Particles/Textures/Basic/Glow.png"
        },
        "SpawnBurst": True,
        "ParticleRotateWithSpawner": False
    }
    write_json(spawner_dir / "Realm_Rarity_Sparkle.particlespawner", sparkle_spawner)
    write_json(PARTICLE_DIR / "Realm_Rarity_Sparkle.particlesystem", {
        "Spawners": [{"SpawnerId": "Realm_Rarity_Sparkle"}],
        "CullDistance": 50.0,
        "IsImportant": True
    })


# =====================================================================
# GENERATION: BLOCK JSON
# =====================================================================

def make_block_json(biome):
    """Generate a biome portal block JSON."""
    key = biome["key"]
    light = biome["light_hex"]
    sound = biome["sound"]
    portal_system = f"Realm_Portal_{key}"
    stones_system = f"Realm_Stones_{key}"
    texture = f"Blocks/Miscellaneous/Realm_Portal_{key}.png"

    return {
        "TranslationProperties": {
            "Name": "server.items.Portal_Device.name",
            "Description": "server.items.Portal_Device.description"
        },
        "Icon": "Icons/ItemsGenerated/Portal_Device.png",
        "Categories": ["Blocks.Portals"],
        "BlockType": {
            "DrawType": "Model",
            "Material": "Solid",
            "Opacity": "Transparent",
            "CustomModel": "Blocks/Miscellaneous/Platform_MagicInactive.blockymodel",
            "CustomModelTexture": [{"Texture": texture, "Weight": 1}],
            "BlockEntity": {
                "Components": {
                    "Portal": {
                        "Config": {
                            "OnState": "Active",
                            "SpawningState": "Spawning",
                            "ReturnBlockType": "Portal_Return"
                        }
                    }
                }
            },
            "HitboxType": "Pad_Portal",
            "BlockParticleSetId": "Stone",
            "BlockSoundSetId": "Stone",
            "VariantRotation": "NESW",
            "Flags": {"IsUsable": True},
            "Gathering": {
                "Breaking": {
                    "GatherType": "Unbreakable",
                    "DropList": "Drop_Realm_Portal_Revert"
                }
            },
            "State": {
                "Definitions": {
                    "Spawning": {
                        "InteractionSoundEventId": "SFX_Portal_Neutral_Open",
                        "Gathering": {"Breaking": {"GatherType": "Unbreakable"}},
                        "Light": {"Radius": 0, "Color": light},
                        "Particles": [
                            {"SystemId": "Portal_Going_Through_Blue", "PositionOffset": {"Y": 1}}
                        ],
                        "CustomModelAnimation": "Blocks/Miscellaneous/Platform_Magic_Activate.blockyanim"
                    },
                    "Active": {
                        "AmbientSoundEventId": sound,
                        "Gathering": {"Breaking": {"GatherType": "Unbreakable"}},
                        "Light": {"Radius": 0, "Color": light},
                        "Particles": [
                            {
                                "SystemId": portal_system,
                                "PositionOffset": {"Y": 2.0},
                                "Scale": 0.8
                            },
                            {
                                "SystemId": stones_system,
                                "TargetEntityPart": "Self",
                                "TargetNodeName": "Debris"
                            },
                            {
                                "SystemId": stones_system,
                                "TargetEntityPart": "Self",
                                "TargetNodeName": "Debris6"
                            }
                        ],
                        "Interactions": {
                            "CollisionEnter": {
                                "Interactions": [{
                                    "Type": "Portal",
                                    "Next": {
                                        "Type": "Simple",
                                        "Effects": {
                                            "LocalSoundEventId": "SFX_Portal_Neutral_Teleport_Local"
                                        }
                                    }
                                }]
                            }
                        },
                        "CustomModel": "Blocks/Miscellaneous/Platform_MagicInactive.blockymodel",
                        "CustomModelAnimation": "Blocks/Miscellaneous/Platform_Magic_Idle.blockyanim"
                    }
                }
            },
            "Interactions": {
                "Use": {
                    "Interactions": [{
                        "Type": "OpenCustomUI",
                        "Page": {
                            "Id": "PortalDevice",
                            "Config": {
                                "OnState": "Active",
                                "SpawningState": "Spawning",
                                "ReturnBlockType": "Portal_Return"
                            }
                        }
                    }]
                }
            },
            "Light": {"Color": light, "Radius": 2}
        },
        "PlayerAnimationsId": "Block",
        "Tags": {"Type": ["Portal"]},
        "Quality": "Rare",
        "MaxStack": 1,
        "ItemLevel": 50,
        "IconProperties": {
            "Scale": 0.35,
            "Rotation": [22.5, 45.0, 22.5],
            "Translation": [0.0, -13.5]
        },
        "ItemSoundSetId": "ISS_Blocks_Stone"
    }


# =====================================================================
# UTILITIES
# =====================================================================

def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
    return path


# =====================================================================
# MAIN
# =====================================================================

def generate_all():
    total = {"blocks": 0, "portal_systems": 0, "stones_systems": 0, "spawners": 0}

    # Verify vanilla templates exist
    if not VANILLA_PORTAL_SPAWNERS.exists():
        print(f"ERROR: Vanilla spawner directory not found: {VANILLA_PORTAL_SPAWNERS}")
        sys.exit(1)

    for biome in BIOMES:
        key = biome["key"]
        prefix = f"Realm_Portal_{key}"

        # Block JSON
        write_json(BLOCK_DIR / f"{prefix}.json", make_block_json(biome))
        total["blocks"] += 1

        # Spawners (14 per biome)
        spawner_count = generate_spawners(biome)
        total["spawners"] += spawner_count

        # Portal particle system (10 spawners)
        generate_portal_system(biome)
        total["portal_systems"] += 1

        # Stones particle system (4 spawners)
        generate_stones_system(biome)
        total["stones_systems"] += 1

        print(f"  [{key}] block + {spawner_count} spawners + 2 systems")

    # Rarity aura (shared — 3 layers: smoke, core, sparkle)
    generate_rarity_aura()
    print(f"  [shared] rarity aura: 3 spawners + 3 systems")

    # Drop list (shared)
    drop_list = {
        "DropEntries": [
            {"ItemId": "Portal_Device", "MinQuantity": 1, "MaxQuantity": 1, "Chance": 1.0}
        ]
    }
    write_json(DROP_DIR / "Drop_Realm_Portal_Revert.json", drop_list)
    print(f"  [shared] drop list")

    print(f"\nGenerated: {total['blocks']} blocks, "
          f"{total['portal_systems']} portal systems, "
          f"{total['stones_systems']} stones systems, "
          f"{total['spawners']} spawners, "
          f"3 rarity aura layers")


if __name__ == "__main__":
    if not Path("build.gradle.kts").exists():
        print("ERROR: Run from project root")
        sys.exit(1)

    print("Generating realm portal assets (full vanilla equivalent)...")
    generate_all()
    print("Done!")
