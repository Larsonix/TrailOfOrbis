#!/usr/bin/env python3
"""Generate unique colored assets for 8 octant skill tree branches.

Creates 40 PNGs (via ImageMagick) + 120 JSONs for the 8 octant arms:
  - 24 essence PNGs (8 octants × 3 light levels, 32×32)
  - 8 crystal PNGs (1 per octant, 64×64)
  - 8 gem PNGs (1 per octant, 32×64)
  - 24 essence JSONs (8 octants × 3 light levels)
  - 72 crystal JSONs (8 octants × 3 sizes × 3 light levels)
  - 24 gem JSONs (8 octants × 3 light levels)

Usage:
    python3 scripts/generate-octant-assets.py [--dry-run]
"""

import json
import os
import subprocess
import sys
from pathlib import Path

# ═══════════════════════════════════════════════════════════════════
# PATHS
# ═══════════════════════════════════════════════════════════════════

PROJECT_ROOT = Path(__file__).resolve().parent.parent
ASSET_ROOT = PROJECT_ROOT / "src" / "main" / "resources" / "hytale-assets"
VANILLA_ROOT = Path("/home/larsonix/work/Hytale-Decompiled-Full-Game/Assets")

# Source texture directories
ESSENCE_SRC_DIR = ASSET_ROOT / "Common" / "Resources" / "Ingredients" / "Essence_Textures_Dim"
CRYSTAL_SRC_DIR = VANILLA_ROOT / "Common" / "Resources" / "Crystals" / "Crystal_Big_Textures"
GEM_SRC_DIR = VANILLA_ROOT / "Common" / "Resources" / "Ores" / "Gem_Textures"

# Output directories (within our asset pack)
ESSENCE_PNG_OUT = ASSET_ROOT / "Common" / "Resources" / "Ingredients" / "Essence_Textures_Dim"
CRYSTAL_PNG_OUT = ASSET_ROOT / "Common" / "Resources" / "Crystals" / "Crystal_Big_Textures"
GEM_PNG_OUT = ASSET_ROOT / "Common" / "Resources" / "Ores" / "Gem_Textures"
ESSENCE_JSON_OUT = ASSET_ROOT / "Server" / "Item" / "Items" / "SkillTree" / "Essence"
CRYSTAL_JSON_OUT = ASSET_ROOT / "Server" / "Item" / "Items" / "SkillTree" / "Crystal"
GEM_JSON_OUT = ASSET_ROOT / "Server" / "Item" / "Items" / "SkillTree" / "Gem"

SIZES = ["Small", "Medium", "Large"]
LIGHT_LEVELS = [0, 50, 100]

# ═══════════════════════════════════════════════════════════════════
# OCTANT DATA TABLE
# ═══════════════════════════════════════════════════════════════════

OCTANTS = [
    {
        "name": "Havoc",
        "hex": "#FF4422",
        "source_essence": "Fire",
        "source_crystal": "Red",
        "source_gem": "Ruby",
        "lantern": "Lantern_Red",
        "essence_icon": "Icons/ItemsGenerated/Ingredient_Fire_Essence.png",
        "crystal_icon_color": "Red",
        "gem_icon": "Icons/ItemsGenerated/Rock_Gem_Ruby.png",
    },
    {
        "name": "Juggernaut",
        "hex": "#CC6633",
        "source_essence": "Fire",
        "source_crystal": "Red",
        "source_gem": "Ruby",
        "lantern": "Lantern_Red",
        "essence_icon": "Icons/ItemsGenerated/Ingredient_Fire_Essence.png",
        "crystal_icon_color": "Red",
        "gem_icon": "Icons/ItemsGenerated/Rock_Gem_Ruby.png",
    },
    {
        "name": "Striker",
        "hex": "#FFAA22",
        "source_essence": "Lightning",
        "source_crystal": "Yellow",
        "source_gem": "Topaz",
        "lantern": "Lantern_Yellow",
        "essence_icon": "Icons/ItemsGenerated/Ingredient_Lightning_Essence.png",
        "crystal_icon_color": "Yellow",
        "gem_icon": "Icons/ItemsGenerated/Rock_Gem_Topaz.png",
    },
    {
        "name": "Warden",
        "hex": "#88AA44",
        "source_essence": "Life",
        "source_crystal": "Green",
        "source_gem": "Emerald",
        "lantern": "Lantern_Green",
        "essence_icon": "Icons/ItemsGenerated/Ingredient_Life_Essence.png",
        "crystal_icon_color": "Green",
        "gem_icon": "Icons/ItemsGenerated/Rock_Gem_Emerald.png",
    },
    {
        "name": "Warlock",
        "hex": "#9944CC",
        "source_essence": "Void",
        "source_crystal": "Purple",
        "source_gem": "Voidstone",
        "lantern": "Lantern_Purple",
        "essence_icon": "Icons/ItemsGenerated/Ingredient_Void_Essence.png",
        "crystal_icon_color": "Purple",
        "gem_icon": "Icons/ItemsGenerated/Rock_Gem_Voidstone.png",
    },
    {
        "name": "Lich",
        "hex": "#6677AA",
        "source_essence": "Water",
        "source_crystal": "Water",
        "source_gem": "Sapphire",
        "lantern": "Lantern_Blue",
        "essence_icon": "Icons/ItemsGenerated/Ingredient_Water_Essence.png",
        "crystal_icon_color": "Blue",
        "gem_icon": "Icons/ItemsGenerated/Rock_Gem_Sapphire.png",
    },
    {
        "name": "Tempest",
        "hex": "#44BBAA",
        "source_essence": "Ice",
        "source_crystal": "Blue",
        "source_gem": "Zephyr",
        "lantern": "Lantern_Cyan",
        "essence_icon": "Icons/ItemsGenerated/Ingredient_Ice_Essence.png",
        "crystal_icon_color": "Cyan",
        "gem_icon": "Icons/ItemsGenerated/Rock_Gem_Zephyr.png",
    },
    {
        "name": "Sentinel",
        "hex": "#77AA88",
        "source_essence": "Life",
        "source_crystal": "Green",
        "source_gem": "Emerald",
        "lantern": "Lantern_Green",
        "essence_icon": "Icons/ItemsGenerated/Ingredient_Life_Essence.png",
        "crystal_icon_color": "Green",
        "gem_icon": "Icons/ItemsGenerated/Rock_Gem_Emerald.png",
    },
]

# ═══════════════════════════════════════════════════════════════════
# COLOR UTILITIES
# ═══════════════════════════════════════════════════════════════════


def hex_to_rgb(hex_str):
    """Convert '#RRGGBB' to (r, g, b) tuple."""
    h = hex_str.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16))


def rgb_to_hex_upper(r, g, b):
    """Convert (r, g, b) to '#RRGGBB' uppercase."""
    return "#{:02X}{:02X}{:02X}".format(
        max(0, min(255, round(r))),
        max(0, min(255, round(g))),
        max(0, min(255, round(b))),
    )


def rgb_to_hex_lower(r, g, b):
    """Convert (r, g, b) to '#rrggbb' lowercase."""
    return "#{:02x}{:02x}{:02x}".format(
        max(0, min(255, round(r))),
        max(0, min(255, round(g))),
        max(0, min(255, round(b))),
    )


def rgb_to_3char(r, g, b):
    """Convert RGB to 3-char hex '#abc' by rounding each channel to nearest nibble."""
    return "#{:x}{:x}{:x}".format(
        max(0, min(15, round(r / 17))),
        max(0, min(15, round(g / 17))),
        max(0, min(15, round(b / 17))),
    )


def expand_3char(s):
    """Expand '#abc' to (0xaa, 0xbb, 0xcc)."""
    return (int(s[1], 16) * 17, int(s[2], 16) * 17, int(s[3], 16) * 17)


def compute_colors(hex_str):
    """Derive all needed colors from an octant's theme hex."""
    r, g, b = hex_to_rgb(hex_str)

    # --- Essence light colors ---
    essence_light100 = rgb_to_hex_upper(r, g, b)
    essence_light50 = rgb_to_hex_upper(r // 2, g // 2, b // 2)

    # --- Crystal colors ---
    crystal_particle = rgb_to_hex_lower(r, g, b)

    # Crystal Light100 per size (3-char shorthand)
    # Small = dimmer glow, Large = brighter glow
    crystal_light100 = {}
    crystal_light50 = {}
    for size, scale in [("Small", 0.70), ("Medium", 0.80), ("Large", 0.95)]:
        sr, sg, sb = int(r * scale), int(g * scale), int(b * scale)
        c3 = rgb_to_3char(sr, sg, sb)
        crystal_light100[size] = c3
        # Light50 = half brightness of Light100 expanded
        er, eg, eb = expand_3char(c3)
        crystal_light50[size] = rgb_to_hex_lower(er // 2, eg // 2, eb // 2)

    # --- Gem colors ---
    gem_particle = rgb_to_hex_lower(r, g, b)

    # Gem Light100 (3-char): dimmer glow with Radius: 0
    sr, sg, sb = int(r * 0.65), int(g * 0.65), int(b * 0.65)
    gem_light100 = rgb_to_3char(sr, sg, sb)

    # Gem Light50 (6-char): half of Light100
    er, eg, eb = expand_3char(gem_light100)
    gem_light50 = rgb_to_hex_lower(er // 2, eg // 2, eb // 2)

    # Gem spark particles: bright pastel version of theme
    gem_spark = rgb_to_hex_lower(
        min(255, r // 2 + 128),
        min(255, g // 2 + 128),
        min(255, b // 2 + 128),
    )

    return {
        "essence_light100": essence_light100,
        "essence_light50": essence_light50,
        "crystal_particle": crystal_particle,
        "crystal_light100": crystal_light100,
        "crystal_light50": crystal_light50,
        "gem_particle": gem_particle,
        "gem_light100": gem_light100,
        "gem_light50": gem_light50,
        "gem_spark": gem_spark,
    }


# ═══════════════════════════════════════════════════════════════════
# PNG GENERATION (ImageMagick)
# ═══════════════════════════════════════════════════════════════════


def run_convert(args, dry_run):
    """Run an ImageMagick convert command."""
    cmd = ["convert"] + args
    if dry_run:
        print(f"  [dry-run] {' '.join(cmd)}")
        return True
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        print(f"  ERROR: {' '.join(cmd)}")
        print(f"  stderr: {result.stderr.strip()}")
        return False
    return True


def generate_essence_pngs(octant, dry_run):
    """Generate 3 essence PNGs per octant (Light0, Light50, Light100)."""
    name = octant["name"]
    source = ESSENCE_SRC_DIR / f"{octant['source_essence']}_Essence_Texture_Light100.png"

    if not source.exists():
        print(f"  WARNING: Source essence texture missing: {source}")
        return 0

    out_100 = ESSENCE_PNG_OUT / f"{name}_Essence_Texture_Light100.png"
    out_50 = ESSENCE_PNG_OUT / f"{name}_Essence_Texture_Light50.png"
    out_0 = ESSENCE_PNG_OUT / f"{name}_Essence_Texture_Light0.png"

    count = 0

    # Light100: grayscale source × solid color (Multiply blend preserves luminance detail)
    if run_convert(
        [str(source), "-grayscale", "Brightness",
         "(", "+clone", "-fill", octant["hex"], "-colorize", "100", ")",
         "-compose", "Multiply", "-composite", str(out_100)],
        dry_run,
    ):
        count += 1

    # Light50: dim Light100 to 50% brightness
    if run_convert(
        [str(out_100), "-modulate", "50,100,100", str(out_50)],
        dry_run,
    ):
        count += 1

    # Light0: dim Light100 to 15% brightness
    if run_convert(
        [str(out_100), "-modulate", "15,100,100", str(out_0)],
        dry_run,
    ):
        count += 1

    return count


def generate_crystal_png(octant, dry_run):
    """Generate 1 crystal PNG per octant (tinted from source crystal)."""
    name = octant["name"]
    source = CRYSTAL_SRC_DIR / f"{octant['source_crystal']}.png"

    if not source.exists():
        print(f"  WARNING: Source crystal texture missing: {source}")
        return 0

    CRYSTAL_PNG_OUT.mkdir(parents=True, exist_ok=True)
    out = CRYSTAL_PNG_OUT / f"{name}.png"

    if run_convert(
        [str(source), "-grayscale", "Brightness",
         "(", "+clone", "-fill", octant["hex"], "-colorize", "100", ")",
         "-compose", "Multiply", "-composite", str(out)],
        dry_run,
    ):
        return 1
    return 0


def generate_gem_png(octant, dry_run):
    """Generate 1 gem PNG per octant (tinted from source gem)."""
    name = octant["name"]
    source = GEM_SRC_DIR / f"{octant['source_gem']}.png"

    if not source.exists():
        print(f"  WARNING: Source gem texture missing: {source}")
        return 0

    GEM_PNG_OUT.mkdir(parents=True, exist_ok=True)
    out = GEM_PNG_OUT / f"{name}.png"

    if run_convert(
        [str(source), "-grayscale", "Brightness",
         "(", "+clone", "-fill", octant["hex"], "-colorize", "100", ")",
         "-compose", "Multiply", "-composite", str(out)],
        dry_run,
    ):
        return 1
    return 0


# ═══════════════════════════════════════════════════════════════════
# JSON GENERATION
# ═══════════════════════════════════════════════════════════════════


def write_json(path, data, dry_run):
    """Write a JSON file with 2-space indentation."""
    if dry_run:
        print(f"  [dry-run] Would write: {path.relative_to(PROJECT_ROOT)}")
        return True
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")
    return True


def make_essence_json(octant, light_level, colors):
    """Build essence JSON dict for a given octant and light level."""
    name = octant["name"]
    base_name = f"Ingredient_{name}_Essence_Light{light_level}"

    # Light field varies by level
    if light_level == 0:
        light = None
    elif light_level == 50:
        light = {"Color": colors["essence_light50"], "Radius": 6}
    else:
        light = {"Color": colors["essence_light100"], "Radius": 6}

    return {
        "TranslationProperties": {
            "Name": f"server.items.{base_name}.name",
            "Description": f"server.items.{base_name}.description",
        },
        "Icon": octant["essence_icon"],
        "Categories": ["Items.Ingredients"],
        "Model": "Resources/Ingredients/Essence.blockymodel",
        "Texture": f"Resources/Ingredients/Essence_Textures_Dim/{name}_Essence_Texture_Light{light_level}.png",
        "PlayerAnimationsId": "Item",
        "IconProperties": {
            "Scale": 0.6,
            "Rotation": [0, 0, 0],
            "Translation": [0, -13],
        },
        "Tags": {"Type": ["Ingredient"]},
        "ItemEntity": {"ParticleSystemId": None, "ShowItemParticles": False},
        "Scale": 0.8,
        "Light": light,
        "ItemSoundSetId": "ISS_Items_Gems",
        "DropOnDeath": True,
    }


def make_crystal_json(octant, size, light_level, colors):
    """Build crystal JSON dict for a given octant, size, and light level."""
    name = octant["name"]
    base_name = f"Rock_Crystal_{name}_{size}_Light{light_level}"
    icon_color = octant["crystal_icon_color"]

    # Size-specific model
    model_map = {
        "Small": "Resources/Crystals/Crystal_Small.blockymodel",
        "Medium": "Resources/Crystals/Crystal_Medium.blockymodel",
        "Large": "Resources/Crystals/Crystal_Big.blockymodel",
    }

    # Light field varies by level
    if light_level == 0:
        light = None
    elif light_level == 50:
        light = {"Color": colors["crystal_light50"][size]}
    else:
        light = {"Color": colors["crystal_light100"][size]}

    # Build BlockType
    block_type = {
        "Material": "Solid",
        "DrawType": "Model",
        "Opacity": "Transparent",
        "CustomModel": model_map[size],
        "CustomModelTexture": [
            {
                "Texture": f"Resources/Crystals/Crystal_Big_Textures/{name}.png",
                "Weight": 1,
            }
        ],
    }

    # HitboxType: Small = Plant_Medium, Medium = Plant_Large, Large = none
    if size == "Small":
        block_type["HitboxType"] = "Plant_Medium"
    elif size == "Medium":
        block_type["HitboxType"] = "Plant_Large"

    block_type.update({
        "Flags": {},
        "VariantRotation": "DoublePipe",
        "RandomRotation": "YawStep1",
        "BlockParticleSetId": "Crystal",
        "ParticleColor": colors["crystal_particle"],
        "BlockSoundSetId": "Crystal",
        "Light": light,
        "Support": {"Down": [{"FaceType": "Full"}]},
    })

    result = {
        "TranslationProperties": {
            "Name": f"server.items.{base_name}.name"
        },
        "Icon": f"Icons/ItemsGenerated/Rock_Crystal_{icon_color}_{size}.png",
        "Categories": ["Blocks.Rocks"],
        "PlayerAnimationsId": "Block",
        "Interactions": {"Secondary": octant["lantern"]},
        "BlockType": block_type,
    }

    # IconProperties only for Large
    if size == "Large":
        result["IconProperties"] = {
            "Rotation": [22.5, 45, 22.5],
            "Scale": 0.5,
            "Translation": [0, -18],
        }

    result["Tags"] = {"Type": ["Rock"], "Family": ["Crystal"]}

    return result


def make_gem_json(octant, light_level, colors):
    """Build gem JSON dict for a given octant and light level."""
    name = octant["name"]
    base_name = f"Rock_Gem_{name}_Light{light_level}"

    # Light field varies by level
    if light_level == 0:
        light = None
    elif light_level == 50:
        light = {"Color": colors["gem_light50"], "Radius": 0}
    else:
        light = {"Color": colors["gem_light100"], "Radius": 0}

    return {
        "TranslationProperties": {
            "Name": f"server.items.{base_name}.name"
        },
        "Icon": octant["gem_icon"],
        "Parent": "Rock_Gem_Emerald",
        "BlockType": {
            "CustomModelTexture": [
                {
                    "Texture": f"Resources/Ores/Gem_Textures/{name}.png",
                    "Weight": 1,
                }
            ],
            "ParticleColor": colors["gem_particle"],
            "Light": light,
            "Particles": [
                {
                    "Color": colors["gem_spark"],
                    "SystemId": "Block_Gem_Sparks",
                }
            ],
        },
    }


# ═══════════════════════════════════════════════════════════════════
# MAIN
# ═══════════════════════════════════════════════════════════════════


def main():
    dry_run = "--dry-run" in sys.argv

    if dry_run:
        print("=== DRY RUN — no files will be written ===\n")

    # Verify ImageMagick is available
    try:
        subprocess.run(
            ["convert", "--version"], capture_output=True, check=True
        )
    except (subprocess.CalledProcessError, FileNotFoundError):
        print("ERROR: ImageMagick 'convert' command not found.")
        print("Install with: sudo apt install imagemagick")
        sys.exit(1)

    # Ensure output directories exist
    if not dry_run:
        CRYSTAL_PNG_OUT.mkdir(parents=True, exist_ok=True)
        GEM_PNG_OUT.mkdir(parents=True, exist_ok=True)

    png_count = 0
    json_count = 0
    errors = []

    for octant in OCTANTS:
        name = octant["name"]
        colors = compute_colors(octant["hex"])
        print(f"\n{'═' * 60}")
        print(f"  {name} ({octant['hex']})")
        print(f"{'═' * 60}")

        # --- PNG generation ---
        print(f"  PNGs:")

        # Essences (3 per octant)
        n = generate_essence_pngs(octant, dry_run)
        png_count += n
        if n == 3:
            print(f"    ✓ 3 essence textures")
        else:
            errors.append(f"{name}: only {n}/3 essence PNGs generated")

        # Crystal (1 per octant)
        n = generate_crystal_png(octant, dry_run)
        png_count += n
        if n == 1:
            print(f"    ✓ 1 crystal texture")
        else:
            errors.append(f"{name}: crystal PNG failed")

        # Gem (1 per octant)
        n = generate_gem_png(octant, dry_run)
        png_count += n
        if n == 1:
            print(f"    ✓ 1 gem texture")
        else:
            errors.append(f"{name}: gem PNG failed")

        # --- JSON generation ---
        print(f"  JSONs:")

        # Essence JSONs (3 per octant)
        for ll in LIGHT_LEVELS:
            data = make_essence_json(octant, ll, colors)
            path = ESSENCE_JSON_OUT / f"Ingredient_{name}_Essence_Light{ll}.json"
            if write_json(path, data, dry_run):
                json_count += 1
        print(f"    ✓ 3 essence items")

        # Crystal JSONs (9 per octant: 3 sizes × 3 light levels)
        for size in SIZES:
            for ll in LIGHT_LEVELS:
                data = make_crystal_json(octant, size, ll, colors)
                path = CRYSTAL_JSON_OUT / f"Rock_Crystal_{name}_{size}_Light{ll}.json"
                if write_json(path, data, dry_run):
                    json_count += 1
        print(f"    ✓ 9 crystal items (3 sizes × 3 lights)")

        # Gem JSONs (3 per octant)
        for ll in LIGHT_LEVELS:
            data = make_gem_json(octant, ll, colors)
            path = GEM_JSON_OUT / f"Rock_Gem_{name}_Light{ll}.json"
            if write_json(path, data, dry_run):
                json_count += 1
        print(f"    ✓ 3 gem items")

        # Print computed colors
        print(f"  Colors:")
        print(f"    Essence L100: {colors['essence_light100']}")
        print(f"    Essence L50:  {colors['essence_light50']}")
        print(f"    Crystal particle: {colors['crystal_particle']}")
        print(f"    Crystal L100: S={colors['crystal_light100']['Small']}"
              f" M={colors['crystal_light100']['Medium']}"
              f" L={colors['crystal_light100']['Large']}")
        print(f"    Gem L100: {colors['gem_light100']}")
        print(f"    Gem spark: {colors['gem_spark']}")

    # --- Summary ---
    print(f"\n{'═' * 60}")
    print(f"  SUMMARY")
    print(f"{'═' * 60}")
    print(f"  PNGs generated: {png_count} (expected 40)")
    print(f"  JSONs generated: {json_count} (expected 120)")

    if errors:
        print(f"\n  ERRORS ({len(errors)}):")
        for e in errors:
            print(f"    ✗ {e}")
        sys.exit(1)
    else:
        print(f"\n  ✓ All assets generated successfully!")
        if dry_run:
            print(f"\n  Re-run without --dry-run to write files.")


if __name__ == "__main__":
    main()
