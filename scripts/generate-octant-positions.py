#!/usr/bin/env python3
"""
Generate octant arm positions by rotating FIRE arm's hand-tuned positions.

Takes the FIRE arm's 32 node positions from skill-tree-positions.yml,
decomposes them into local (along, perp0, perp1) coordinates relative
to fire_entry, then projects them onto each octant direction's orthonormal
basis to produce identical branch shapes in 3D.
"""

import math
import re
import sys
from pathlib import Path

# ═══════════════════════════════════════════════════════════════════
# CONFIGURATION
# ═══════════════════════════════════════════════════════════════════

BASE_HEIGHT = 65.0
OCTANT_ENTRY_DISTANCE = 200  # layout units
LAYOUT_SCALE = 0.07
RADIAL_EXPANSION = 1.2

# FIRE cluster names → octant cluster names (index 0-3)
FIRE_CLUSTERS = ["fury", "inferno", "bloodlust", "berserker"]

OCTANT_ARMS = {
    "havoc":      {"dir": ( 1,  1,  1), "clusters": ["carnage", "frenzy", "ruin", "mayhem"]},
    "juggernaut": {"dir": ( 1,  1, -1), "clusters": ["conquest", "dominion", "bloodforge", "tyrant"]},
    "striker":    {"dir": ( 1, -1,  1), "clusters": ["quicksilver", "precision", "ambush", "flurry"]},
    "warden":     {"dir": ( 1, -1, -1), "clusters": ["garrison", "outrider", "ironclad", "palisade"]},
    "warlock":    {"dir": (-1,  1,  1), "clusters": ["hex", "ritual", "malice", "damnation"]},
    "lich":       {"dir": (-1,  1, -1), "clusters": ["grasp", "crypt", "requiem", "decay"]},
    "tempest":    {"dir": (-1, -1,  1), "clusters": ["squall", "tailwind", "cyclone", "maelstrom"]},
    "sentinel":   {"dir": (-1, -1, -1), "clusters": ["aegis", "vigilance", "restoration", "haven"]},
}

CLUSTER_SUFFIXES = ["_1", "_2", "_branch", "_3", "_4", "_notable"]

# ═══════════════════════════════════════════════════════════════════
# VECTOR MATH
# ═══════════════════════════════════════════════════════════════════

def magnitude(v):
    return math.sqrt(sum(c*c for c in v))

def normalize(v):
    mag = magnitude(v)
    return tuple(c / mag for c in v)

def dot(a, b):
    return sum(ai * bi for ai, bi in zip(a, b))

def scale_vec(v, s):
    return tuple(c * s for c in v)

def add_vec(a, b):
    return tuple(ai + bi for ai, bi in zip(a, b))

def get_perpendicular_directions(d):
    """Same logic as LayoutMath.Direction3D.getPerpendicularDirections()"""
    x, y, z = d
    if x != 0 and y != 0 and z != 0:
        # Diagonal: perp0 = (-z, 0, x), perp1 = cross(dir, perp0)
        perp0 = (-z, 0, x)
        perp1 = (x*y, -(x*x + z*z), y*z)
        return perp0, perp1
    elif x != 0:
        return (0, 1, 0), (0, 0, 1)
    elif y != 0:
        return (1, 0, 0), (0, 0, 1)
    else:
        return (1, 0, 0), (0, 1, 0)

def get_orthonormal_basis(direction):
    """Returns normalized (dir, perp0, perp1) basis vectors."""
    perp0, perp1 = get_perpendicular_directions(direction)
    return normalize(direction), normalize(perp0), normalize(perp1)

# ═══════════════════════════════════════════════════════════════════
# PARSE FIRE POSITIONS FROM YAML
# ═══════════════════════════════════════════════════════════════════

def parse_positions(yaml_path):
    """Parse node positions from skill-tree-positions.yml"""
    positions = {}
    current_node = None

    with open(yaml_path) as f:
        for line in f:
            line = line.rstrip()
            # Match node ID line: "  fire_entry:"
            m = re.match(r'^  (\w+):$', line)
            if m:
                current_node = m.group(1)
                positions[current_node] = {}
                continue
            # Match coordinate line: "    x: 5.0"
            m = re.match(r'^    ([xyz]): (.+)$', line)
            if m and current_node:
                positions[current_node][m.group(1)] = float(m.group(2))

    return positions

def extract_fire_positions(positions):
    """Extract all FIRE arm nodes and return as dict of {suffix: (x, y, z)}"""
    fire_nodes = {}

    # Entry
    if "fire_entry" in positions:
        p = positions["fire_entry"]
        fire_nodes["entry"] = (p["x"], p["y"], p["z"])

    # Clusters
    for cluster in FIRE_CLUSTERS:
        for suffix in CLUSTER_SUFFIXES:
            node_id = f"fire_{cluster}{suffix}"
            if node_id in positions:
                p = positions[node_id]
                fire_nodes[f"{cluster}{suffix}"] = (p["x"], p["y"], p["z"])

    # Synergy nodes
    for i in range(1, 5):
        node_id = f"fire_synergy_{i}"
        if node_id in positions:
            p = positions[node_id]
            fire_nodes[f"synergy_{i}"] = (p["x"], p["y"], p["z"])

    # Synergy hub
    if "fire_synergy_hub" in positions:
        p = positions["fire_synergy_hub"]
        fire_nodes["synergy_hub"] = (p["x"], p["y"], p["z"])

    # Keystones
    for i in range(1, 3):
        node_id = f"fire_keystone_{i}"
        if node_id in positions:
            p = positions[node_id]
            fire_nodes[f"keystone_{i}"] = (p["x"], p["y"], p["z"])

    return fire_nodes

# ═══════════════════════════════════════════════════════════════════
# ROTATION: FIRE → OCTANT
# ═══════════════════════════════════════════════════════════════════

def compute_octant_entry(direction):
    """Compute octant entry position in world coordinates."""
    mag = magnitude(direction)
    norm_dist = OCTANT_ENTRY_DISTANCE / mag
    layout = tuple(round(c * norm_dist) for c in direction)
    scale = LAYOUT_SCALE * RADIAL_EXPANSION
    world_x = layout[0] * scale
    world_y = BASE_HEIGHT + layout[1] * scale
    world_z = layout[2] * scale
    return (world_x, world_y, world_z)

def rotate_fire_to_octant(fire_nodes, octant_dir, octant_clusters):
    """
    Rotate all FIRE positions into an octant direction.

    1. Compute offsets relative to fire_entry
    2. Decompose into (along, perp0, perp1) in FIRE's axis-aligned basis
    3. Project onto octant's orthonormal basis
    4. Add to octant entry position
    """
    fire_entry = fire_nodes["entry"]

    # FIRE basis is trivially axis-aligned:
    # along = X, perp0 = Y (up/down), perp1 = Z (north/south)

    # Octant basis (normalized)
    oct_dir, oct_p0, oct_p1 = get_orthonormal_basis(octant_dir)

    # Octant entry position
    oct_entry = compute_octant_entry(octant_dir)

    result = {}

    for fire_key, fire_pos in fire_nodes.items():
        # Compute offset from fire_entry
        offset_x = fire_pos[0] - fire_entry[0]  # along arm
        offset_y = fire_pos[1] - fire_entry[1]  # perp0 (up/down)
        offset_z = fire_pos[2] - fire_entry[2]  # perp1 (sideways)

        # Project onto octant basis
        rotated = add_vec(
            add_vec(
                scale_vec(oct_dir, offset_x),
                scale_vec(oct_p0, offset_y)
            ),
            scale_vec(oct_p1, offset_z)
        )

        # Add to octant entry position
        world_pos = add_vec(oct_entry, rotated)

        # Map fire cluster names to octant cluster names
        octant_key = fire_key
        if fire_key == "entry":
            octant_key = "entry"
        else:
            for i, fire_cluster in enumerate(FIRE_CLUSTERS):
                if fire_key.startswith(fire_cluster):
                    suffix = fire_key[len(fire_cluster):]
                    octant_key = octant_clusters[i] + suffix
                    break

        result[octant_key] = world_pos

    return result

# ═══════════════════════════════════════════════════════════════════
# GENERATE YAML OUTPUT
# ═══════════════════════════════════════════════════════════════════

def format_yaml(arm_name, arm_config, positions):
    """Format positions as YAML for one octant arm."""
    dx, dy, dz = arm_config["dir"]
    sign = lambda v: "+" if v > 0 else "-"
    dir_str = f"({sign(dx)}{abs(dx)}, {sign(dy)}{abs(dy)}, {sign(dz)}{abs(dz)})"

    lines = []
    lines.append("")
    lines.append(f"  # ─────────────────────────────────────────────────────────────────────────────")
    lines.append(f"  # {arm_name.upper()} {dir_str}")
    lines.append(f"  # ─────────────────────────────────────────────────────────────────────────────")

    # Order: entry, clusters, synergy, hub, keystones
    ordered_keys = ["entry"]
    for cluster in arm_config["clusters"]:
        for suffix in CLUSTER_SUFFIXES:
            ordered_keys.append(f"{cluster}{suffix}")
    for i in range(1, 5):
        ordered_keys.append(f"synergy_{i}")
    ordered_keys.append("synergy_hub")
    ordered_keys.append("keystone_1")
    ordered_keys.append("keystone_2")

    for key in ordered_keys:
        if key in positions:
            x, y, z = positions[key]
            node_id = f"{arm_name}_{key}"
            lines.append("")
            lines.append(f"  {node_id}:")
            lines.append(f"    x: {x:.1f}")
            lines.append(f"    y: {y:.1f}")
            lines.append(f"    z: {z:.1f}")

    return "\n".join(lines)

def main():
    yaml_path = Path(__file__).parent.parent / "src/main/resources/config/skill-tree-positions.yml"

    print(f"Reading positions from: {yaml_path}", file=sys.stderr)

    # Parse existing positions
    all_positions = parse_positions(yaml_path)

    # Extract FIRE arm
    fire_nodes = extract_fire_positions(all_positions)
    print(f"Found {len(fire_nodes)} FIRE nodes", file=sys.stderr)

    # Generate header
    output_lines = []
    output_lines.append("")
    output_lines.append("  # ═══════════════════════════════════════════════════════════════════════════")
    output_lines.append("  # OCTANT ARMS (8 arms × 32 nodes = 256 nodes)")
    output_lines.append("  # Rotated copies of FIRE arm's hand-tuned positions")
    output_lines.append("  # ═══════════════════════════════════════════════════════════════════════════")

    total_nodes = 0

    for arm_name, arm_config in OCTANT_ARMS.items():
        octant_positions = rotate_fire_to_octant(
            fire_nodes, arm_config["dir"], arm_config["clusters"]
        )
        yaml_section = format_yaml(arm_name, arm_config, octant_positions)
        output_lines.append(yaml_section)
        total_nodes += len(octant_positions)
        print(f"  {arm_name}: {len(octant_positions)} nodes", file=sys.stderr)

    print(f"\nTotal: {total_nodes} octant nodes generated", file=sys.stderr)

    # Output YAML to stdout
    print("\n".join(output_lines))

if __name__ == "__main__":
    main()
