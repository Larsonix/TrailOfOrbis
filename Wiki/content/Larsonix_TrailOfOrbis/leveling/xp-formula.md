---
name: XP Formula
title: XP Formula - Deep Dive
description: Exact formulas for XP gain, effort-based leveling curve, mob XP calculation, and worked examples
author: Larsonix
sort-index: 0
order: 31
published: true
---

# XP Formula

XP per kill scales with mob level, stat pool, and tier. The leveling curve is effort-based : ~3 kills at Level 1, ~150 kills at Level 100, scaling smoothly via a sub-linear power law. If you just want the quick version, see the [Leveling Overview](leveling-experience).

---

## Mob XP : The Full Formula

Every mob kill awards XP calculated as :

```
xp = ceil((mobLevel x 5.0 + statPool x 0.1) x tierMult x 1.2 x (1 + distanceLevel x 0.028))
```

### Breaking It Down

| Component | Formula | Purpose |
|-----------|---------|---------|
| **Base XP** | `mobLevel x 5.0` | Linear scaling with mob level |
| **Pool Bonus** | `statPool x 0.1` | Rewards killing statistically stronger mobs |
| **Tier Multiplier** | Boss = 5.0x, Elite = 1.5x | Higher-tier mobs are worth more |
| **Global Multiplier** | x1.2 | Server-wide XP scaling factor |
| **Distance Bonus** | `1 + distanceLevel x 0.028` | +2.8% XP per distance level from spawn |

The constants :

| Constant | Value |
|----------|-------|
| `xpPerMobLevel` | 5.0 |
| `poolMultiplier` | 0.1 |
| `GLOBAL_XP_MULTIPLIER` | 1.2 |
| `DISTANCE_XP_PER_LEVEL` | 0.028 |

---

## Tier Multipliers

The tier multiplier depends on the mob's classification :

| Classification | Tier Multiplier |
|----------------|-----------------|
| Boss | 5.0x |
| Elite | 1.5x |
| All others | 1.0x |

> [!NOTE]
> The tier multiplier comes from `mob-classification.yml` via `classificationConfig.getXpMultiplier()`. Boss = 5.0x XP, Elite = 1.5x XP. The stat multiplier (Boss = 3.0x, Elite = 1.5x) is separate and affects combat stats, not XP.

---

## Worked Examples

| Mob | Level | Pool | Tier | Distance | Base + Pool | x Tier x 1.2 x Distance | **Result** |
|:----|------:|-----:|:----:|---------:|:------------|:-------------------------|----------:|
| Hostile at spawn | 10 | 150 | 1.0x | 0 | 50 + 15 = 65 | 65 x 1.0 x 1.2 x 1.0 | **78 XP** |
| Elite far out | 50 | 800 | 1.5x | 20 | 250 + 80 = 330 | 330 x 1.5 x 1.2 x 1.56 | **927 XP** |
| Boss deep | 100 | 2000 | 5.0x | 50 | 500 + 200 = 700 | 700 x 5.0 x 1.2 x 2.4 | **10,080 XP** |
| Passive critter | 5 | 30 | 1.0x | 0 | 25 + 3 = 28 | 28 x 1.0 x 1.2 x 1.0 | **34 XP** |
> [!NOTE]
> Passive mobs have a 0.1x XP class multiplier applied at the classification level (see [Mob System](mob-system)), but in the core XP formula shown here, the tier multiplier for non-elite, non-boss mobs is 1.0x. The classification multiplier is applied separately.

---

## The Effort-Based Leveling Curve

Trail of Orbis doesn't use a static XP table. Instead, it calculates XP requirements based on how many mobs you *should* need to kill at each level.

### The Formula

```
exponent = ln(targetMobs / baseMobs) / ln(targetLevel)
mobsPerLevel(L) = baseMobs x max(1, L)^exponent
```

### Default Configuration

| Parameter | Value | Meaning |
|:----------|------:|:--------|
| `baseMobs` | 3 | Kills needed at level 1 |
| `targetMobs` | 150 | Kills needed at the target level |
| `targetLevel` | 100 | Reference point for the curve |

### Calculated Exponent

```
exponent = ln(150 / 3) / ln(100)
         = ln(50) / ln(100)
         = 3.912 / 4.605
         ≈ 0.849
```

This exponent (~0.849) creates a sub-linear power curve. Effort grows steadily but never exponentially.

### Kills-Per-Level Reference

| Level | mobsPerLevel | Feel |
|------:|-------------:|:-----|
| 1 | ~3 | Instant, gets you into the system |
| 10 | ~21 | Quick, still exploring |
| 50 | ~93 | Serious, choosing content carefully |
| 100 | ~150 | Endgame, each level is a session goal |

### Why Effort-Based ?

> [!TIP]
> The effort formula is **self-correcting**. If you fight higher-level mobs (more XP per kill), you need fewer kills to reach the target. If mob XP rates change via config, the kills-per-level target automatically adjusts. This prevents a config change from accidentally making levels trivial or impossible.

With a static XP table, doubling mob XP would halve the grind, potentially breaking progression. The effort formula keeps the *experience* consistent even when numbers change.

---

## Legacy Exponential Formula

An older exponential formula also exists in the codebase :

```
cumulative_XP = baseXp x (level - 1)^exponent
```

| Parameter | Value |
|-----------|-------|
| `baseXp` | 50 |
| `exponent` | 2.2 |

This is the legacy formula. The effort-based formula described above is the default.

---

## XP Beyond Level 100

The curve continues smoothly beyond level 100. There's no hard cap : the max level is 1,000,000. The same power-law formula applies, so each subsequent level requires slightly more kills than the last. Higher-level mobs give proportionally more XP (the `mobLevel x 5.0` term scales linearly), partially offsetting the increased requirements.
