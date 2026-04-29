---
name: Node Types
title: Node Types
description: 6 node types - Basic, Notable, Keystone, Origin, Entry, and Synergy - costs and mechanics
author: Larsonix
sort-index: 1
order: 92
published: true
---

# Node Types

6 node types serve different roles in your Skill Tree, from basic stat bonuses to build-defining keystones. Each has a distinct Skill Point cost and visual appearance in the Skill Sanctum.

---

## Basic Nodes

| Property | Value |
|----------|-------|
| Cost | 1 Skill Point |
| Notable | No |
| Keystone | No |
| Role | Small passives, stat bonuses |

Basic nodes are the bulk of the tree. Each gives a modest stat bonus (e.g., +health, +damage, +armor). They form the paths through each cluster, connecting entry points to notables and keystones.

Each cluster contains **5 basic nodes** leading to 1 notable.

---

## Notable Nodes

| Property | Value |
|----------|-------|
| Cost | 2 Skill Points |
| Notable | Yes |
| Keystone | No |
| Role | Multi-stat combos, cluster payoffs |

Notables are the reward at the end of a cluster. They give substantially stronger bonuses than basic nodes, typically multi-stat combinations. Each elemental arm's cluster journey of 5 basic nodes leads to 1 notable.

---

## Keystone Nodes

| Property | Value |
|----------|-------|
| Cost | 3 Skill Points |
| Notable | No |
| Keystone | Yes |
| Role | Build-defining effects with tradeoffs or drawbacks |

Keystones are the endgame nodes at the far reaches of each arm. They provide dramatic, build-defining effects, often with significant tradeoffs. These are what make builds unique.

> [!IMPORTANT]
> Keystones require deep investment into an arm to reach, making them a commitment. But respec is free, so you can always try a keystone and revert if it doesn't fit your build.

---

## Origin Node (Start Node)

| Property | Value |
|----------|-------|
| Cost | 0 (always allocated) |
| Start Node | Yes |
| Role | Center hub, connects to all entry points |

The Origin is the center of the tree. Always allocated, cannot be deallocated, and connects to all arm entry points. Every allocation path must trace back to the Origin.

---

## Entry Nodes

| Property | Value |
|----------|-------|
| Tier | 1 |
| Role | Gateway nodes connecting Origin to each arm |

Entry nodes are the first step into any arm. They sit at tier 1 and bridge the gap between the Origin and the arm's cluster structure. Allocating an entry node opens up the entire arm for investment.

---

## Synergy Nodes

| Property | Value |
|----------|-------|
| Tier | 5 |
| Role | Scaling bonuses based on other allocations |
| Config | type, element, perCount, bonus, cap |

Synergy nodes provide bonuses that **scale with your other investments**. They reward deep specialization by amplifying your existing allocations.

### The 5 Synergy Types

| Type | What It Scales With | Example |
|------|-------------------|---------|
| **ELEMENTAL_COUNT** | Nodes allocated in the same element/arm | "Per 3 Fire nodes, +3% Fire Damage" |
| **STAT_COUNT** | Nodes that grant a specific stat | "Per 5 nodes granting crit, +2% Crit Chance" |
| **BRANCH_COUNT** | Nodes in the same branch/region | "Per 4 nodes in this branch, +1% damage" |
| **TIER_COUNT** | Notable or keystone nodes allocated | "Per notable allocated, +5 flat damage" |
| **TOTAL_COUNT** | Total nodes allocated anywhere | "Per 10 total nodes, +1% all damage" |

Each synergy has a `perCount` (how many nodes per bonus tick), a `bonus` (the stat, value, and modifier type), and an optional `cap` (0 means no cap).

---

## Node Visual States

Nodes exist in one of 3 visual states in the Skill Sanctum :

| State | Meaning | Sanctum Appearance |
|-------|---------|-------------------|
| **LOCKED** | No adjacent allocated node, cannot be reached | Dim, barely visible |
| **AVAILABLE** | Reachable from an allocated neighbor, can allocate | Medium brightness, pulsing |
| **ALLOCATED** | Currently invested | Bright, fully lit |

---

## Modifier Types

Nodes grant bonuses using one of 7 modifier types :

| Type | Effect |
|------|--------|
| **FLAT** | Adds a flat value (+50 Health) |
| **PERCENT** | Percentage increase (+10% Damage) |
| **MULTIPLIER** | Multiplicative scaling (x1.15 Damage) |
| **PENETRATION** | Ignores a portion of target's defense |
| **CONVERSION** | Converts damage from one type to another |
| **STATUS_CHANCE** | Increases ailment proc chance |
| **STATUS_DURATION** | Extends ailment duration |

---

## Related Pages

- [Arms & Regions](arms-regions) - The 15 regions and their archetypes
- [Allocating Skill Points](allocating-skill-points) - How to allocate and respec
- [Skill Tree](skill-tree) - Overview with node sizes and visual states
