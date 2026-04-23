---
name: Level Blending
title: Level Blending
description: How drop levels are pulled toward your level - the formula that keeps gear relevant
author: Larsonix
sort-index: 3
order: 124
published: true
---

# Level Blending

Level blending prevents the "wrong level" problem. Without it, killing a Level 5 mob at Level 80 would drop Level 5 gear. Useless. Blending pulls drop levels toward yours so gear stays relevant regardless of what you're fighting.

---

## The Formula

```

blendedLevel = sourceLevel + clamp((playerLevel - sourceLevel) * 0.3, -5, +5)
finalLevel = blendedLevel + random(+/-2)

```

| Parameter | Value | Meaning |
|-----------|-------|---------|
| Pull factor | 0.3 | Closes 30% of the gap between mob level and your level |
| Max offset | +/-5 | Blending can shift the level by at most 5 |
| Variance | +/-2 | Random spread after blending |

---

## How It Works

Blending closes 30% of the gap between the mob's level and yours, capped at +/-5 levels.

### Example 1 : High-level player, low-level mob
```

Player Level 58 kills Level 23 mob:
  gap = 58 - 23 = 35
  pull = 35 * 0.3 = 10.5 → clamped to +5
  blended = 23 + 5 = 28
  final = 28 +/- 2 → Level 26-30 gear

```

Without blending : Level 23 gear (useless). With blending : Level 26-30 gear (still low, but better).

### Example 2 : Low-level player, high-level mob
```

Player Level 10 kills Level 23 mob:
  gap = 10 - 23 = -13
  pull = -13 * 0.3 = -3.9 → stays at -3.9 (within +/-5)
  blended = 23 + (-3.9) = ~19
  final = 19 +/- 2 → Level 17-21 gear

```

Blending pulls DOWN slightly. Gear is still higher than your level but not absurdly so.

### Example 3 : Level-appropriate mob
```

Player Level 50 kills Level 50 mob:
  gap = 50 - 50 = 0
  pull = 0 * 0.3 = 0
  blended = 50
  final = 50 +/- 2 → Level 48-52 gear

```

When fighting mobs at your level, blending has no effect. Only the +/-2 variance applies.

---

## Where Blending Applies

| Source | Blended ? |
|--------|----------|
| Mob gear drops | Yes |
| Mob stone drops | Yes |
| Container loot | Yes |
| Realm Map drops | Yes |
| Victory rewards | Yes |

> [!NOTE]
> Blending only affects the **level** of dropped items. It does not change drop chance, rarity, quality, or modifiers. Those are rolled independently.

---

## Why Not Full Pull ?

The pull factor is intentionally low (0.3) with a +/-5 cap. This preserves the incentive to fight level-appropriate content :

- Killing Level 100 mobs at Level 100 gives Level 100 drops (no blending needed)
- Killing Level 50 mobs at Level 100 gives Level 55 drops at best (still low)
- Killing Level 100 mobs at Level 50 gives Level 95 drops at best (rewarding, but you must survive)

Blending smooths out rough edges, but it doesn't replace fighting mobs at your level.

---

## Related Pages

- [Drop Mechanics](drop-mechanics) - Base drop chances that level blending applies to
- [Realm Sizes](realm-sizes) - In Realms, all drops match the map level (no blending)
- [Loot System](loot-system) - Full loot system overview
