#!/usr/bin/env python3
"""
Generates biome-colored portal platform textures by hue-shifting the vanilla
Platform_Magic_Blue2.png to each biome's theme color.

Requires: Pillow (pip install Pillow)

Run from project root:
    python scripts/generate-portal-textures.py
"""

import colorsys
import sys
from pathlib import Path

try:
    from PIL import Image, ImageDraw, ImageEnhance
except ImportError:
    print("ERROR: Pillow not installed. Run: pip install Pillow")
    sys.exit(1)

# ═══════════════════════════════════════════════════════════════════
# CONFIGURATION
# ═══════════════════════════════════════════════════════════════════

# Source texture (vanilla blue portal platform)
SOURCE_TEXTURE = Path("../Assets/Common/Blocks/Miscellaneous/Platform_Magic_Blue2.png")

# Output directory
OUTPUT_DIR = Path("src/main/resources/hytale-assets/Common/Blocks/Miscellaneous")

# Biome target hues (0-360 degrees) and saturation multipliers
# These are calibrated to produce visually distinct portal textures
BIOMES = {
    "Forest":        {"hue": 120, "sat": 0.65, "val": 0.90},   # soft green
    "Desert":        {"hue": 45,  "sat": 0.65, "val": 0.95},   # warm gold
    "Volcano":       {"hue": 0,   "sat": 0.70, "val": 0.85},   # deep red
    "Tundra":        {"hue": 195, "sat": 0.45, "val": 1.05},   # pale ice
    "Swamp":         {"hue": 150, "sat": 0.50, "val": 0.65},   # murky teal
    "Beach":         {"hue": 35,  "sat": 0.60, "val": 1.00},   # warm sand
    "Jungle":        {"hue": 130, "sat": 0.65, "val": 0.75},   # deep green
    "Caverns":       {"hue": 220, "sat": 0.45, "val": 0.75},   # slate gray
    "Frozen_Crypts": {"hue": 200, "sat": 0.55, "val": 0.95},   # cold blue
    "Sand_Tombs":    {"hue": 30,  "sat": 0.60, "val": 0.90},   # amber
    "Void":          {"hue": 275, "sat": 0.70, "val": 0.85},   # deep purple
}


def get_source_avg_hue(img):
    """Calculate the average hue of the source image (ignoring transparent pixels)."""
    pixels = img.convert("RGBA").load()
    w, h = img.size
    hue_sum = 0.0
    count = 0
    for y in range(h):
        for x in range(w):
            r, g, b, a = pixels[x, y]
            if a < 10:
                continue
            h_val, s, v = colorsys.rgb_to_hsv(r / 255.0, g / 255.0, b / 255.0)
            if s > 0.05:  # skip near-gray pixels
                hue_sum += h_val
                count += 1
    return (hue_sum / count) if count > 0 else 0.6  # ~216° blue default


def recolor_image(img, target_hue_deg, sat_mult, val_mult, source_hue_fraction):
    """Shift all pixels from source hue to target hue, adjusting saturation and value."""
    target_hue = target_hue_deg / 360.0
    hue_shift = target_hue - source_hue_fraction

    result = img.convert("RGBA").copy()
    pixels = result.load()
    w, h = result.size

    for y in range(h):
        for x in range(w):
            r, g, b, a = pixels[x, y]
            if a < 2:
                continue

            h_val, s, v = colorsys.rgb_to_hsv(r / 255.0, g / 255.0, b / 255.0)

            # Shift hue
            new_h = (h_val + hue_shift) % 1.0
            # Scale saturation and value
            new_s = min(1.0, max(0.0, s * sat_mult))
            new_v = min(1.0, max(0.0, v * val_mult))

            nr, ng, nb = colorsys.hsv_to_rgb(new_h, new_s, new_v)
            pixels[x, y] = (int(nr * 255), int(ng * 255), int(nb * 255), a)

    return result


def generate_all():
    """Generate all biome portal textures."""
    if not SOURCE_TEXTURE.exists():
        print(f"ERROR: Source texture not found: {SOURCE_TEXTURE}")
        print("  Make sure you're running from the project root")
        sys.exit(1)

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    source = Image.open(SOURCE_TEXTURE).convert("RGBA")
    source_hue = get_source_avg_hue(source)
    print(f"Source avg hue: {source_hue:.3f} ({source_hue * 360:.0f} deg)")

    for key, params in BIOMES.items():
        out_path = OUTPUT_DIR / f"Realm_Portal_{key}.png"
        result = recolor_image(source, params["hue"], params["sat"], params["val"], source_hue)
        result.save(out_path)
        print(f"  [block] {key} hue={params['hue']} -> {out_path.name}")

    print(f"\nGenerated {len(BIOMES)} block textures")

    # ── 3D portal preview textures (128x128, like vanilla WorldTaiga.png) ──
    PREVIEW_DIR = Path("src/main/resources/hytale-assets/Common/Particles/Textures/Realm")
    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)

    BIOME_COLORS_RGB = {
        "Forest": (34, 139, 34), "Desert": (237, 204, 15), "Volcano": (139, 0, 0),
        "Tundra": (180, 220, 240), "Swamp": (47, 79, 79), "Beach": (250, 200, 100),
        "Jungle": (0, 100, 0), "Caverns": (54, 86, 111), "Frozen_Crypts": (68, 136, 204),
        "Sand_Tombs": (196, 160, 96), "Void": (90, 42, 138),
    }

    for key, (r, g, b) in BIOME_COLORS_RGB.items():
        # Skip if a sprite sheet already exists (wider than 128px = has real video frames)
        out_path = PREVIEW_DIR / f"Realm_Preview_{key}.png"
        if out_path.exists():
            existing = Image.open(out_path)
            if existing.size[0] > 128:
                print(f"  [preview] {key} -> SKIPPED (sprite sheet exists: {existing.size[0]}x{existing.size[1]})")
                continue

        img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
        draw = ImageDraw.Draw(img)
        # Circular gradient: bright center fading to dark edges (portal window feel)
        cx, cy = 64, 64
        for radius in range(64, 0, -1):
            t = radius / 64.0
            cr = int(r * t + r * 0.3 * (1 - t))
            cg = int(g * t + g * 0.3 * (1 - t))
            cb = int(b * t + b * 0.3 * (1 - t))
            alpha = int(255 * (1.0 - (radius / 64.0) ** 2))  # soft circle falloff
            draw.ellipse([cx - radius, cy - radius, cx + radius, cy + radius],
                         fill=(cr, cg, cb, alpha))
        out_path = PREVIEW_DIR / f"Realm_Preview_{key}.png"
        img.save(out_path)
        print(f"  [preview] {key} -> {out_path.name}")

    print(f"Generated {len(BIOME_COLORS_RGB)} preview textures")

    # ── UI artwork: biome gradient + stained glass overlay ──
    ARTWORK_DIR = Path("src/main/resources/hytale-assets/Common/UI/Custom/Pages/Portals")
    ARTWORK_DIR.mkdir(parents=True, exist_ok=True)
    STAINED_GLASS = Path("../Assets/Common/UI/Custom/Pages/Portals/DefaultArtwork.png")

    if STAINED_GLASS.exists():
        glass = Image.open(STAINED_GLASS).convert("RGBA")
        W, H = glass.size

        for key, (r, g, b) in BIOME_COLORS_RGB.items():
            # Create biome gradient base
            base = Image.new("RGBA", (W, H))
            base_draw = ImageDraw.Draw(base)
            for y in range(H):
                t = y / H
                cr = int(r * (1.0 - t * 0.7))
                cg = int(g * (1.0 - t * 0.7))
                cb = int(b * (1.0 - t * 0.7))
                base_draw.line([(0, y), (W, y)], fill=(cr, cg, cb, 255))

            # Composite stained glass on top at 60% opacity
            glass_tinted = glass.copy()
            glass_tinted.putalpha(
                glass_tinted.getchannel("A").point(lambda a: int(a * 0.6))
            )
            base.paste(glass_tinted, (0, 0), glass_tinted)

            out_path = ARTWORK_DIR / f"Realm_Artwork_{key}.png"
            base.save(out_path)
            print(f"  [artwork] {key} -> {out_path.name}")

        print(f"Generated {len(BIOME_COLORS_RGB)} UI artworks (gradient + stained glass)")
    else:
        print(f"WARNING: Vanilla stained glass not found at {STAINED_GLASS}, skipping artwork compositing")


if __name__ == "__main__":
    if not Path("build.gradle.kts").exists():
        print("ERROR: Run from project root (where build.gradle.kts is)")
        sys.exit(1)

    print("Generating realm portal textures...")
    generate_all()
    print("Done!")
