#!/usr/bin/env python3
"""
Fix Notable (Crystal) and Keystone (Gem) skill tree node lighting.

Problem: All Crystal and Gem light-level variants reference the same
full-brightness texture, so LOCKED/AVAILABLE/ALLOCATED states look identical.
Basic (Essence) nodes work correctly because they have separate dimmed textures.

This script:
1. Generates dimmed texture variants for Crystal and Gem models
2. Updates JSON item definitions with correct texture paths + light emission

Brightness mapping (matches Essence pattern):
  Light0   = 15% brightness, no light emission   (LOCKED)
  Light50  = 50% brightness, medium emission      (AVAILABLE)
  Light100 = 100% brightness, full emission        (ALLOCATED)
"""

import json
from pathlib import Path
from PIL import Image, ImageEnhance

PROJECT_ROOT = Path(__file__).resolve().parent.parent
ASSETS = PROJECT_ROOT / "src" / "main" / "resources" / "hytale-assets"
COMMON = ASSETS / "Common" / "Resources"
ITEMS = ASSETS / "Server" / "Item" / "Items" / "SkillTree"

REGIONS = [
    "Core", "Fire", "Water", "Lightning", "Earth", "Wind", "Void",
    "Havoc", "Juggernaut", "Striker", "Warden", "Warlock", "Lich",
    "Tempest", "Sentinel",
]

# Region theme colors from SkillTreeRegion.java
REGION_COLORS = {
    "Core":       "#EFE3CF",
    "Fire":       "#FF7881",
    "Water":      "#00CBFF",
    "Lightning":  "#F1A900",
    "Earth":      "#7BD23B",
    "Void":       "#DE8DFF",
    "Wind":       "#00DEBC",
    "Havoc":      "#DE77AB",
    "Juggernaut": "#FEA8F6",
    "Striker":    "#FFAE74",
    "Warden":     "#A4A528",
    "Warlock":    "#9E8EEF",
    "Lich":       "#94CBFF",
    "Tempest":    "#00B5CB",
    "Sentinel":   "#87E496",
}

# Brightness multipliers per light level
BRIGHTNESS = {
    "Light0":   0.15,   # Locked: barely visible
    "Light50":  0.50,   # Available: moderate glow
    "Light100": 1.00,   # Allocated: full brightness
}

# Light emission radii — scaled by node importance
# Notable (Crystal): medium nodes → medium radii
CRYSTAL_RADII = {"Light0": 0, "Light50": 4, "Light100": 8}
# Keystone (Gem): largest nodes → largest radii
GEM_RADII     = {"Light0": 0, "Light50": 5, "Light100": 10}

# Source and destination texture directories
CRYSTAL_SRC = COMMON / "Crystals" / "Crystal_Big_Textures"
CRYSTAL_DIM = COMMON / "Crystals" / "Crystal_Big_Textures_Dim"
GEM_SRC     = COMMON / "Ores" / "Gem_Textures"
GEM_DIM     = COMMON / "Ores" / "Gem_Textures_Dim"


def dim_texture(src: Path, dst: Path, factor: float):
    """Create a brightness-adjusted copy of a texture, preserving alpha."""
    img = Image.open(src).convert("RGBA")

    if factor >= 1.0:
        img.save(dst)
        return

    # Dim RGB channels only, keep alpha intact
    r, g, b, a = img.split()
    rgb = Image.merge("RGB", (r, g, b))
    dimmed = ImageEnhance.Brightness(rgb).enhance(factor)
    dr, dg, db = dimmed.split()
    Image.merge("RGBA", (dr, dg, db, a)).save(dst)


def generate_textures(src_dir: Path, dim_dir: Path, label: str):
    """Generate Light0/50/100 texture variants for all regions."""
    dim_dir.mkdir(parents=True, exist_ok=True)
    count = 0

    for region in REGIONS:
        src = src_dir / f"{region}.png"
        if not src.exists():
            print(f"  WARN: {src.name} not found, skipping")
            continue

        for level, factor in BRIGHTNESS.items():
            dst = dim_dir / f"{region}_{level}.png"
            dim_texture(src, dst, factor)
            count += 1

    print(f"  Generated {count} {label} textures in {dim_dir.name}/")


def update_json_item(json_path: Path, texture_rel: str, light_color: str, radius: int):
    """Update a single JSON item definition with correct texture and light."""
    with open(json_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    data["Texture"] = texture_rel

    if radius == 0:
        data["Light"] = None  # No emission for locked nodes (matches Essence pattern)
    else:
        data["Light"] = {"Color": light_color, "Radius": radius}

    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def update_items(item_dir: Path, dim_texture_prefix: str, radii: dict, pattern_fn, label: str):
    """Update JSON items for all regions and light levels."""
    count = 0

    for region in REGIONS:
        color = REGION_COLORS[region]

        for level in ["Light0", "Light50", "Light100"]:
            filename = pattern_fn(region, level)
            json_path = item_dir / filename

            if not json_path.exists():
                print(f"  WARN: {filename} not found, skipping")
                continue

            texture = f"{dim_texture_prefix}/{region}_{level}.png"
            radius = radii[level]
            update_json_item(json_path, texture, color, radius)
            count += 1

    print(f"  Updated {count} {label} JSON items")


def main():
    print("=== Skill Tree Node Lighting Fix ===\n")

    # Step 1: Generate dimmed textures
    print("Generating Crystal (Notable) textures...")
    generate_textures(CRYSTAL_SRC, CRYSTAL_DIM, "Crystal")

    print("Generating Gem (Keystone) textures...")
    generate_textures(GEM_SRC, GEM_DIM, "Gem")

    # Step 2: Update JSON item definitions
    print("\nUpdating Crystal (Notable) items...")
    update_items(
        ITEMS / "Crystal",
        "Resources/Crystals/Crystal_Big_Textures_Dim",
        CRYSTAL_RADII,
        lambda r, l: f"Rock_Crystal_{r}_Medium_{l}.json",
        "Crystal",
    )

    print("Updating Gem (Keystone) items...")
    update_items(
        ITEMS / "Gem",
        "Resources/Ores/Gem_Textures_Dim",
        GEM_RADII,
        lambda r, l: f"Rock_Gem_{r}_{l}.json",
        "Gem",
    )

    print("\nDone! Deploy asset pack to see changes in-game.")


if __name__ == "__main__":
    main()
