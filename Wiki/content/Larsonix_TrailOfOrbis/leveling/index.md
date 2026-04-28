---
name: Leveling Overview
title: Leveling & Experience
description: How XP, levels, and progression work in Trail of Orbis
author: Larsonix
sort-index: 0
order: 30
published: true
sub-topics:
  - xp-formula
  - death-penalty
---

# Leveling & Experience

Every mob you kill earns XP. Accumulate enough and you level up, gaining **+1 Attribute Point** and **+1 Skill Point** each time. The level cap is **1,000,000**, but early levels come fast and later levels require real commitment.

| | |
|:--|:--|
| **Max Level** | 1,000,000 |
| **XP Source** | Mob kills only |
| **Per Level** | +1 Attribute Point, +1 Skill Point |
| **Death Penalty** | 50% progress XP (Level 21+) |
| **Protection** | No penalty below Level 21 |
| **Curve Type** | Effort-based (sub-linear power law) |

---

## How It Works

1. **Kill a mob** and earn XP based on its level, stat pool, classification, and distance from spawn
2. **Fill the XP bar** and level up when you hit the threshold
3. **Receive points** : +1 Attribute Point and +1 Skill Point per level
4. **Spend points** : allocate via `/stats` and navigate from there

Your current level and XP progress are always visible in the **XP bar HUD** below your hotbar. XP gain notifications appear as floating text when you kill mobs.

> [!NOTE]
> Mob kills are currently the only XP source. No quests, no crafting XP, no passive gains.

---

## XP Per Kill

The XP you earn from a kill depends on several factors :

```
xp = ceil((mobLevel x 5.0 + statPool x 0.1) x tierMult x 1.2 x (1 + distanceLevel x 0.028))
```

| Factor | What It Means | Value |
|:-------|:--------------|:------|
| **Mob Level** | Higher-level mobs give more base XP | x5.0 per level |
| **Stat Pool** | Stronger mobs (more total stats) give bonus XP | 10% of pool |
| **Tier Multiplier** | Elites and Bosses multiply XP | Elite x1.5, Boss x5.0 |
| **Global Multiplier** | Applied to all XP gains | x1.2 |
| **Distance Bonus** | Further from spawn = more XP | +2.8% per distance level |

### Tier Multipliers

| Tier | XP Multiplier |
|:-----|:-------------:|
| Boss | 5.0x |
| Elite | 1.5x |
| Normal | 1.0x |

### Quick Examples

| Mob | Base Calc | Tier | Result |
|:----|:----------|:----:|-------:|
| Level 10 Hostile (150 pool) | (10x5 + 150x0.1) x 1.0 x 1.2 = 78 | Normal | ~78 XP |
| Level 50 Elite (800 pool) | (50x5 + 800x0.1) x 1.5 x 1.2 = 594 | Elite | ~594 XP |
| Level 100 Boss (2000 pool) | (100x5 + 2000x0.1) x 5.0 x 1.2 = 4,200 | Boss | ~4,200 XP |

For the full formula breakdown with worked examples, see [XP Formula](xp-formula-deep-dive).

---

## Leveling Speed

Trail of Orbis uses an **effort-based** leveling formula. Instead of a fixed XP table, the system targets a specific number of mob kills per level :

| Level | Approximate Kills to Level Up |
|------:|------------------------------:|
| 1 | ~3 mobs |
| 10 | ~21 mobs |
| 50 | ~93 mobs |
| 100 | ~150 mobs |

Early levels are fast. You'll hit level 2 after just 3 kills. By level 100, each level takes roughly 150 mob kills. The system self-corrects : if mob XP rewards change, the kills-per-level target stays roughly the same.

> [!TIP]
> The effort formula prevents accidental power-leveling. Fighting higher-level mobs gives more XP per kill, but the system adjusts requirements to maintain the intended pace.

---

## Level-Up Rewards

Every level grants :

- **+1 Attribute Point** to distribute across [Fire](attributes#fire), [Water](attributes#water), [Lightning](attributes#lightning), [Earth](attributes#earth), [Wind](attributes#wind), [Void](attributes#void)
- **+1 Skill Point** to spend in the 485-node Skill Tree

---

## Death Penalty

> [!WARNING]
> Players above level 20 lose **50% of their progress XP within the current level** on death. You can never lose a level, only progress toward the next one. Players at level 20 or below are fully protected.

For full details, see [Death Penalty](death-penalty).

---

## Further Reading

- **[XP Formula](xp-formula-deep-dive)** exact formulas, effort-based curve math, worked examples
- **[Death Penalty](death-penalty)** how XP loss works, protection threshold, strategies
