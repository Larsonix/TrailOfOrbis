---
name: Sizes
title: Realm Sizes
description: Small, Medium, Large, Massive - arena radius, mob count, timeout, bosses, and reward multipliers
author: Larsonix
sort-index: 1
order: 102
published: true
---

# Realm Sizes

4 sizes determine the scale of each Realm : arena radius, mob count, timeout duration, guaranteed bosses, and reward multiplier.

---

## Size Comparison

| Size | Arena Radius | Base Monsters | Timeout | Guaranteed Bosses | Reward Mult |
|:-----|-------------:|--------------:|:--------|------------------:|------------:|
| **Small** | 25 blocks | 15 | 300s (5 min) | 0 | 1.0x |
| **Medium** | 49 blocks | 25 | 600s (10 min) | 1 | 1.5x |
| **Large** | 100 blocks | 40 | 900s (15 min) | 1 | 2.5x |
| **Massive** | 200 blocks | 70 | 1200s (20 min) | 2 | 4.0x |

---

## Monster Count Scaling

The base monster count scales with the Realm's level :

```

actualMonsters = baseCount * (1 + level * 0.02)

```

| Size | Base | Level 25 | Level 50 | Level 100 |
|:-----|-----:|---------:|---------:|----------:|
| Small | 15 | 22 | 30 | 45 |
| Medium | 25 | 37 | 50 | 75 |
| Large | 40 | 60 | 80 | 120 |
| Massive | 70 | 105 | 140 | 210 |

Higher-level Realms have significantly more mobs. This scales both the challenge and the loot : more mobs mean more individual drop chances on top of the victory reward multiplier.

---

## Dynamic Arena Sizing

The arena radius scales dynamically based on mob count :

```

radius = 25 + 101 * sqrt((mobs - 15) / 485)

```

This ensures the arena grows proportionally with the number of mobs, preventing overcrowding in high-level Realms while keeping density consistent.

---

## Layout Shapes

Each Realm Map also rolls a shape that determines the arena geometry :

| Shape | Description |
|-------|-------------|
| **Circular** | Equal distance from center to edge in all directions. Good for kiting mobs in circles. |
| **Rectangular** | Has corners. Allows corner-trapping strategies. |
| **Irregular** | Varied edges, unpredictable flow. Requires adaptation. |

Shape is set at map creation and cannot be changed with Stones.

---

## Guaranteed Bosses

| Size | Bosses | Impact |
|------|--------|--------|
| Small | 0 | Pure trash mob clear, no guaranteed boss encounters |
| Medium | 1 | One guaranteed boss spawn |
| Large | 1 | One guaranteed boss, plus significantly more total mobs |
| Massive | **2** | Two guaranteed bosses, massive loot potential |

Boss mobs spawn at designated boss spawn points within the arena. They're drawn from the biome's boss mob pool.

---

## Choosing Your Size

| Situation | Best Size | Why |
|-----------|-----------|-----|
| Quick session | Small | 5-minute timeout, 15 base mobs |
| Solo balanced run | Medium | 1 guaranteed boss, manageable mob count |
| Serious loot farming | Large | 2.5x reward multiplier, 1 boss |
| Group play | Massive | 4.0x rewards, 2 bosses, but 70+ base mobs in 20 minutes |
| First Realm ever | Small | Low stakes, fast reset, perfect for early levels |

> [!IMPORTANT]
> Massive Realms with 70+ base mobs (scaling to 140+ at level 50) and a 20-minute timer are designed for groups. Solo players can attempt them but may struggle with the timer at higher levels.

---

## Related Pages

- [Realm Biomes](realm-biomes) - Environmental hazards and difficulty by biome
- [Realm Rewards](realm-rewards) - How size affects victory reward multiplier
- [Realm Modifiers](realm-modifiers) - Reduced Time modifier cuts your timeout
