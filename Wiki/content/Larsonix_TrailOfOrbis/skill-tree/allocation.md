---
name: Allocation
title: Allocating Skill Points
description: How allocation works - adjacency, BFS pathing, skill point costs, respec, synergy and conditional systems
author: Larsonix
sort-index: 2
order: 93
published: true
---

# Allocating Skill Points

You earn Skill Points as you level. Allocation is free, respec is free, and there's no penalty for experimenting.

---

## How to Allocate

### In the Skill Sanctum (Recommended)

```

/skilltree       - Toggle enter/exit
/sanctum         - Toggle enter/exit

```

Walk to the node you want, press **F** to interact. The Skill Sanctum gives you spatial understanding of the tree. You can see where bridges connect, how far keystones are, and plan your route visually.

### Via Commands

```

/too skilltree allocate <node_id>       - Allocate a point to a node
/too skilltree deallocate <node_id>     - Remove a point from a node
/too skilltree respec                   - Reset ALL allocations
/too skilltree list                     - Show all allocated nodes
/too skilltree info <node_id>           - View details of a node

```

---

## Allocation Rules

| Rule | Details |
|------|---------|
| **Adjacency required** | You can only allocate a node if it's adjacent to an already-allocated node |
| **BFS from Origin** | The system uses breadth-first search from the Origin to determine reachability |
| **Skill Point costs** | Basic : 1 point, Notable : 2 points, Keystone : 3 points |
| **Origin is permanent** | The Origin cannot be deallocated |
| **Stat recalculation** | Every allocation or deallocation triggers a full stat recalculation |

---

## Respec

```

/too skilltree respec

```

Full reset. Clears **all** nodes except the Origin. Refunds ALL `totalPointsEarned` as `skillPoints`. No cost, no cooldown, no limit.

> [!TIP]
> Free respec means you should experiment constantly. Try keystones, test octant arm paths, swap between pure and hybrid builds. There's no wrong answer because you can always undo it.

---

## The Conditional System

Certain nodes grant **conditional bonuses** : effects that only activate when a specific trigger fires. These are powerful situational buffs that reward active play.

### 15 Conditional Triggers

#### Timed Triggers (duration-based)

These activate on an event and last for a set duration :

| Trigger | Duration | Activates When |
|---------|----------|---------------|
| **ON_KILL** | 4 seconds | You kill an enemy |
| **ON_CRIT** | 3 seconds | You land a critical strike |
| **WHEN_HIT** | 2 seconds | You take damage |
| **ON_SKILL_USE** | 4 seconds | You use a skill |
| **ON_BLOCK** | 3 seconds | You block an attack |
| **ON_EVADE** | 3 seconds | You evade an attack |
| **ON_INFLICT_STATUS** | 5 seconds | You inflict a status ailment |

#### Persistent Triggers (threshold-based)

These stay active as long as the condition is met :

| Trigger | Condition |
|---------|-----------|
| **LOW_LIFE** | Below 35% HP |
| **FULL_LIFE** | At 100% HP |
| **FULL_MANA** | At 100% Mana |
| **LOW_MANA** | Below 35% Mana |
| **WHILE_MOVING** | Your character is in motion |
| **WHILE_STATIONARY** | Your character is standing still |
| **WHILE_BUFFED** | Any buff is active |

---

### 6 Stacking Behaviors

When a timed conditional is re-triggered, its stacking behavior determines what happens :

| Behavior | Effect |
|----------|--------|
| **REFRESH** | Reset the duration timer |
| **STACK** | Add a stack (up to a max stack count) |
| **NO_REFRESH** | Cannot re-trigger while currently active |
| **CONSUME_ON_HIT** | Effect is consumed (removed) when you are hit |
| **CONSUME_ON_SKILL** | Effect is consumed when you use a skill |
| **EXTEND_DURATION** | Each trigger adds more time to the existing duration |

---

## Related Pages

- [Arms & Regions](arms-regions) - The 15 regions you can invest in
- [Node Types](node-types) - What each node type costs and does
- [Skill Tree](skill-tree) - Overview with sanctum navigation directions
