#!/usr/bin/env python3
"""
Generates compass mob marker PNGs for Trail of Orbis.

Produces 12 files (3 shapes x 4 distance tiers) with combined size+opacity scaling:
  - MobMarkerUp_<tier>.png     (upward arrow — from MobMarker.png, resized)
  - MobMarkerDown_<tier>.png   (downward arrow — MobMarker.png rotated 180, resized)
  - MobMarkerDot_<tier>.png    (filled circle — proportional to arrow, resized)

Each tier encodes BOTH a smaller pixel size AND reduced alpha, so farther mobs
appear as progressively smaller and more transparent markers on the compass.
Hytale renders markers at native PNG pixel dimensions (no runtime scale API).
"""

import glob
import os
import sys

try:
    from PIL import Image, ImageDraw
except ImportError:
    print("Pillow required: pip install Pillow")
    sys.exit(1)

MARKERS_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "src", "main", "resources", "hytale-assets",
    "Common", "UI", "WorldMap", "MapMarkers"
)

SOURCE_IMAGE = os.path.join(MARKERS_DIR, "MobMarker.png")

# Distance tiers: (suffix, size_scale, alpha_multiplier)
# Each tier maps to a 25-block distance band (0-25, 25-50, 50-75, 75-100).
# Max opacity capped at 80% so markers always feel like overlays, not solid UI.
TIERS = [
    ("_close",   1.00, 0.80),   # 0-25 blocks:  full size,  80% alpha
    ("_mid",     0.80, 0.55),   # 25-50 blocks:  80% size,  55% alpha
    ("_far",     0.65, 0.35),   # 50-75 blocks:  65% size,  35% alpha
    ("_distant", 0.50, 0.20),   # 75-100 blocks: 50% size,  20% alpha
]

# Minimum pixel dimension — anything smaller becomes invisible on the compass
MIN_DIMENSION = 3


def compute_dimensions(src_w, src_h, scale):
    """Compute target dimensions with minimum floor and integer rounding."""
    tw = max(MIN_DIMENSION, round(src_w * scale))
    th = max(MIN_DIMENSION, round(src_h * scale))
    return tw, th


def resize_with_quality(img, target_w, target_h):
    """Resize an RGBA image using LANCZOS for high-quality downscaling."""
    if img.size == (target_w, target_h):
        return img.copy()
    return img.resize((target_w, target_h), Image.LANCZOS)


def apply_opacity(img, alpha_mult):
    """Scale the alpha channel of an RGBA image."""
    if alpha_mult >= 1.0:
        return img.copy()
    r, g, b, a = img.split()
    a = a.point(lambda x: int(x * alpha_mult))
    return Image.merge("RGBA", (r, g, b, a))


def create_dot(width, height):
    """Create a small filled circle matching the arrow's visual weight.

    Rendered at 4x then downscaled with LANCZOS for clean anti-aliasing.
    Uses ~55% of the smaller dimension as diameter for a subtle, proportional dot.
    """
    scale = 4
    sw, sh = width * scale, height * scale

    hi_res = Image.new("RGBA", (sw, sh), (0, 0, 0, 0))
    draw = ImageDraw.Draw(hi_res)

    diameter = int(min(sw, sh) * 0.55)
    cx, cy = sw // 2, sh // 2
    r = diameter // 2
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(255, 255, 255, 255))

    return hi_res.resize((width, height), Image.LANCZOS)


def cleanup_old_files():
    """Remove previously generated MobMarker variant PNGs (not the source)."""
    patterns = ["MobMarkerUp*", "MobMarkerDown*", "MobMarkerDot*"]
    removed = 0
    for pattern in patterns:
        for filepath in glob.glob(os.path.join(MARKERS_DIR, pattern)):
            os.remove(filepath)
            removed += 1
    if removed > 0:
        print(f"Cleaned up {removed} old variant PNGs.\n")


def main():
    if not os.path.exists(SOURCE_IMAGE):
        print(f"ERROR: Source image not found: {SOURCE_IMAGE}")
        print("MobMarker.png must exist as the base arrow asset.")
        sys.exit(1)

    source = Image.open(SOURCE_IMAGE).convert("RGBA")
    src_w, src_h = source.size
    print(f"Source: MobMarker.png ({src_w}x{src_h})")
    print(f"Output: {MARKERS_DIR}\n")

    cleanup_old_files()

    total = 0
    for suffix, size_scale, alpha_mult in TIERS:
        tw, th = compute_dimensions(src_w, src_h, size_scale)
        print(f"Tier '{suffix}' — {tw}x{th} ({size_scale:.0%} size), {alpha_mult:.0%} alpha:")

        # Up arrow — resized source
        up_scaled = resize_with_quality(source, tw, th)
        up_final = apply_opacity(up_scaled, alpha_mult)
        up_path = os.path.join(MARKERS_DIR, f"MobMarkerUp{suffix}.png")
        up_final.save(up_path, "PNG")
        print(f"  MobMarkerUp{suffix}.png ({tw}x{th})")
        total += 1

        # Down arrow — source rotated 180 then resized
        down_rotated = source.rotate(180, resample=Image.BICUBIC, expand=False)
        down_scaled = resize_with_quality(down_rotated, tw, th)
        down_final = apply_opacity(down_scaled, alpha_mult)
        down_path = os.path.join(MARKERS_DIR, f"MobMarkerDown{suffix}.png")
        down_final.save(down_path, "PNG")
        print(f"  MobMarkerDown{suffix}.png ({tw}x{th})")
        total += 1

        # Dot — proportional circle at tier dimensions
        dot = create_dot(tw, th)
        dot_final = apply_opacity(dot, alpha_mult)
        dot_path = os.path.join(MARKERS_DIR, f"MobMarkerDot{suffix}.png")
        dot_final.save(dot_path, "PNG")
        print(f"  MobMarkerDot{suffix}.png ({tw}x{th})")
        total += 1

        print()

    print(f"Done — {total} PNGs generated.")


if __name__ == "__main__":
    main()
