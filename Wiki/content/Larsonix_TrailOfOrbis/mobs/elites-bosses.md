---
name: Elites & Bosses
title: Elites & Bosses
description: Elite and Boss mob tiers - XP multipliers, stat multipliers, how they differ, and where they spawn
author: Larsonix
sort-index: 1
order: 112
published: true
---

# Elites & Bosses

Two special mob tiers sit above standard Hostile mobs. They're the most challenging and most rewarding encounters you'll find.

---

## Overview Comparison

| Property | Hostile | Elite | Boss |
|:---------|:-------:|:-----:|:----:|
| **XP Multiplier** | 1.0x | 1.5x | 5.0x |
| **Stat Multiplier** | 1.0x | 1.5x | 3.0x |
| **Tier Mult (XP formula)** | 1.0x | 1.5x | 5.0x |
| **Combat Relevance** | Yes | Yes | Yes |
| **Is Hostile** | Yes | Yes | Yes |

> [!IMPORTANT]
> Bosses have both a **5.0x XP class multiplier** and a **3.0x stat multiplier**. They hit 3x harder than normal mobs AND grant 5x the base XP.

---

## Elite Mobs

Elites aren't specific enemy types. They're **any hostile mob** elevated to Elite status. They represent the unpredictable mid-tier threat that keeps exploration exciting.

### What Makes Them Different

| Stat | Elite vs Hostile |
|------|-----------------|
| XP Multiplier | 1.5x (50% more XP) |
| Stat Multiplier | 1.5x (50% stronger across all stats) |

Elites get 50% more total stats than a standard Hostile of the same level. Combined with the Dirichlet distribution (see [Scaling](mob-stat-scaling)), an Elite that rolled high in damage stats can be genuinely dangerous.

### XP Value

Using the mob XP formula with an Elite at level 50 with a 600 stat pool at spawn :

```
xp = ceil((50 x 5.0 + 600 x 0.1) x 1.5 x 1.2 x (1 + 0 x 0.028))
   = ceil((250 + 60) x 1.5 x 1.2)
   = ceil(310 x 1.8)
   = ceil(558.0)
   = 558 XP
```

Compare to a standard Hostile at the same level and pool :

```
xp = ceil((250 + 60) x 1.0 x 1.2)
   = ceil(310 x 1.2)
   = ceil(372.0)
   = 372 XP
```

> [!TIP]
> Elites give 50% more XP than standard Hostiles. They're worth seeking out. The extra difficulty is proportionally rewarded.

---

## Boss Mobs

Bosses are the apex predators of Trail of Orbis. Major encounters with dramatic stat advantages and massive XP rewards.

### What Makes Them Different

| Stat | Boss vs Hostile |
|------|-----------------|
| XP Multiplier | 5.0x (5x more XP) |
| Stat Multiplier | 3.0x (triple stats across the board) |

With a 3.0x stat multiplier, a Boss has 3x the health, 3x the damage, and 3x every other stat compared to a standard Hostile of the same level. These fights demand preparation.

### XP Value

Using the mob XP formula with a Boss at level 100 with a 2000 stat pool at spawn :

```
xp = ceil((100 x 5.0 + 2000 x 0.1) x 5.0 x 1.2 x (1 + 0 x 0.028))
   = ceil((500 + 200) x 5.0 x 1.2)
   = ceil(700 x 6.0)
   = ceil(4200.0)
   = 4,200 XP
```

Compare to a standard Hostile at the same level and pool :

```
xp = ceil((500 + 200) x 1.0 x 1.2)
   = ceil(700 x 1.2)
   = ceil(840.0)
   = 840 XP
```

The Boss gives **3x the XP** of a standard Hostile (via the tier multiplier in the XP formula), on top of its 5.0x classification XP multiplier applied elsewhere.

---

## Where Elites and Bosses Spawn

### Overworld

Both Elites and Bosses can appear in the overworld. The classification system assigns mob tiers based on the priority chain described in the [Mob System overview]() :

1. Role override (explicit config)
2. Group patterns (NPCGroup name matching)
3. Name patterns (role name matching)
4. Direct group membership
5. Attitude fallback

Bosses are typically assigned via role overrides or name pattern matches to specific entity types (dragons, yeti, etc.). Elites can emerge from any hostile mob through the classification system.

### Realms

Realm instances provide controlled encounters where mob levels match the map level. Both Elites and Bosses can appear in Realms, scaled to the map's difficulty.

> [!NOTE]
> In Realms, all mobs match the map's level. A Boss in a level 100 Realm has its full 3.0x stat multiplier applied at level 100. That's a serious fight demanding proper gear and build investment.

---

## Identifying Elites and Bosses

In combat, you can spot Elites and Bosses by :

- Their **classification tag** in damage indicators and death recap
- **Higher damage numbers**. You'll notice the difference immediately
- **Longer fights** from significantly more HP via the stat multiplier

The death recap shows `[Elite]` or `[Boss]` tags next to the attacker name, so you understand why a particular fight was harder than expected.

---

## Strategic Considerations

> [!WARNING]
> **Bosses with high Dirichlet rolls in damage stats are exceptionally dangerous.** A Boss already has 3.0x stats. If the Dirichlet distribution also concentrated its pool into Physical Damage and Critical Chance, the resulting burst damage can one-shot unprepared players.

**Preparing for Elite encounters :**
- Keep diverse elemental resistances. You don't know what an Elite will specialize in
- Have enough raw health to survive a critical hit spike
- Elites are a natural part of exploration ; treat every hostile encounter as potentially Elite

**Preparing for Boss encounters :**
- Study the death recap after any failed attempt to identify the dominant damage type
- Stack the appropriate elemental resistance alongside general defenses
- [Earth](attributes#earth) attribute investment (Armor, Max Health) provides the broadest Boss survivability
- Consider the distance bonus. A Boss far from spawn has both higher stats and higher rewards

---

## Related Pages

- [Mob Stat Scaling](mob-stat-scaling) - Dirichlet distribution creates unique stat profiles
- [Overworld vs Realms](overworld-vs-realms) - Elites are 2.0x stats / 3.0x XP in Realms
- [Death Recap](death-recap) - Shows [Elite] and [Boss] tags on the killing blow
- [XP Formula](xp-formula-deep-dive) - Boss tier gives 5.0x XP multiplier
