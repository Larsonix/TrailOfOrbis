---
id: skill-tree-overview
name: Skill Tree Overview
title: Skill Tree
description: 485 nodes across 15 regions - a 3D skill tree you physically walk through in the Skill Sanctum
author: Larsonix
sort-index: 0
order: 25
published: true
sub-topics:
  - arms
  - node-types
  - allocation
---

# Skill Tree

Your Skill Tree has **485 nodes** spread across **15 regions** in 3D space, all radiating from the Origin at the center. You explore it physically inside a per-player instance called the **Skill Sanctum** - walk up to nodes, press F to allocate. No menus, no flat UI.

| | |
|:--|:--|
| **Total Nodes** | 485 |
| **Regions** | 15 (1 Core + 6 Elemental + 8 Octant) |
| **Bridge Paths** | 12 (connecting adjacent elements) |
| **Node Types** | 6 (Basic, Notable, Keystone, Origin, Entry, Synergy) |
| **Respec Cost** | Free, no cooldown |
| **Allocation** | Walk to orbs in 3D, press F |

---

## Structure

| Component | Count | Purpose |
|:----------|------:|:--------|
| Core | 1 node | Central origin, connects all arms |
| Elemental Arms | 6 × 32 = 192 | Main progression paths (one per element) |
| Octant Arms | 8 × 32 = 256 | Diagonal hybrid regions bridging adjacent elements |
| Bridge Paths | 12 × 3 = 36 | 3-node paths connecting pairs of elemental arms |
| **Total** | **485** | 1 + 192 + 256 + 36 |

---

## Entering the Skill Sanctum

```
/skilltree       - Toggle enter/exit
/sanctum         - Toggle enter/exit
```

When you enter, a personal instance is created and all 485 nodes spawn as physical glowing orbs. Walk to a node, press **F** to allocate. Respec anytime with `/too skilltree respec` - free, no cost.

---

## What You See Inside

| Node Type | Orb Size (blocks) | Light Radius (blocks) |
|:----------|------------------:|----------------------:|
| **Basic** | 0.5 | 3.0 |
| **Notable** | 0.7 | 5.0 |
| **Keystone** | 1.0 | 8.0 |
| **Origin** | 1.2 | 10.0 |

| State | Light Intensity | Appearance |
|:------|----------------:|:-----------|
| **LOCKED** | 0.2 (20%) | Very dim, barely visible |
| **AVAILABLE** | 0.6 (60%) | Medium brightness, pulsing |
| **ALLOCATED** | 1.0 (100%) | Full brightness |

Available nodes pulse on a **2.0-second cycle**. When you allocate, an explosion burst of particles confirms it.

---

## Navigation

| To reach... | Go... |
|-------------|-------|
| [Fire](attributes#fire) arm | East (+X) |
| [Water](attributes#water) arm | West (-X) |
| [Lightning](attributes#lightning) arm | South (+Z) |
| [Earth](attributes#earth) arm | North (-Z) |
| [Void](attributes#void) arm | Up (+Y) |
| [Wind](attributes#wind) arm | Down (-Y) |

> [!TIP]
> The Origin orb is the largest (1.2 blocks) and brightest. It's always visible and serves as your landmark. All arms radiate outward from it.

---

## Node Types at a Glance

| Type | Cost | Role |
|:-----|-----:|:-----|
| **Basic** | 1 point | Small passives, stat bonuses |
| **Notable** | 2 points | Multi-stat combos, cluster payoffs |
| **Keystone** | 3 points | Build-defining with tradeoffs/drawbacks |
| **Origin** | 0 (auto) | Center hub, always allocated |
| **Entry** | 1 point | Gateway nodes connecting Origin to each arm |
| **Synergy** | varies | Bonuses that scale based on your other allocations |

See [Node Types](node-types) for full details. For arm details and archetypes, see [Arms & Regions](arms-regions). For allocation mechanics, see [Allocation](allocating-skill-points).
