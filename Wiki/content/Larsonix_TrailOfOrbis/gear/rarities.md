---
name: Rarities
title: Gear Rarities
description: The 7 rarity tiers - drop rates, stat multipliers, modifier caps, durability, and upgrade paths
author: Larsonix
sort-index: 0
order: 66
published: true
---

# Gear Rarities

7 rarity tiers define how powerful a piece of gear can be. Higher rarity means more modifier slots, a higher stat multiplier, better durability, and guaranteed minimum rolls at the top tiers.

---

## The 7 Tiers

| Rarity | Color | Max Mods | Max Prefix | Max Suffix | Stat Mult | Durability Mult | Drop Weight | Approx. Chance |
|:------:|:------|---------:|-----------:|-----------:|----------:|----------------:|------------:|---------------:|
| [**Common**](#common) | #C9D2DD | 1 | 1 | 1 | 0.3x | 1.0x | 64.0 | 75.0% |
| [**Uncommon**](#uncommon) | #3E9049 | 2 | 1 | 2 | 0.5x | 1.2x | 16.0 | 18.75% |
| [**Rare**](#rare) | #2770B7 | 3 | 2 | 2 | 0.8x | 1.5x | 4.0 | 4.69% |
| [**Epic**](#epic) | #8B339E | 4 | 2 | 2 | 1.2x | 2.0x | 1.0 | 1.17% |
| [**Legendary**](#legendary) | #BB8A2C | 5 | 2 | 3 | 1.7x | 3.0x | 0.25 | 0.29% |
| [**Mythic**](#mythic) | #FF4500 | 6 | 3 | 3 | 2.3x | 5.0x | 0.0625 | 0.073% |
| [**Unique**](#unique) | #AF6025 | 6 | 3 | 3 | 2.8x | 5.0x | 0.016 | 0.018% |

---

## Understanding the Numbers

### Stat Multiplier

Your stat multiplier scales every modifier on the item. A "+100 Armor" modifier on a Common (0.3x) item becomes effectively +30 Armor. The same modifier on a Mythic (2.3x) item becomes +230 Armor.

At Mythic tier, every modifier is **7.67x stronger** than the same modifier on Common.

### Drop Weights

Weights follow a clean **4x geometric sequence**. Each tier is exactly 4x rarer than the previous one.

```
Common -> Uncommon:    64.0  / 16.0   = 4x rarer
Uncommon -> Rare:      16.0  / 4.0    = 4x rarer
Rare -> Epic:           4.0  / 1.0    = 4x rarer
Epic -> Legendary:      1.0  / 0.25   = 4x rarer
Legendary -> Mythic:    0.25 / 0.0625 = 4x rarer
Mythic -> Unique:     0.0625 / 0.016  = ~3.9x rarer
```

Total spread : **4,000x from Common to Unique**. For every ~4,000 Common drops, you'll see roughly 1 Unique.

### Durability

Higher rarity items last longer before needing repair. Mythic and Unique items have **5x** the durability of Common items. That means significantly less maintenance at endgame.

---

## Modifier Guarantees

The max prefix and max suffix columns define the ceiling for each rarity :

| Rarity | Max Prefix | Max Suffix | Max Total |
|:------:|-----------:|-----------:|----------:|
| Common | 1 | 1 | 1 |
| Uncommon | 1 | 2 | 2 |
| Rare | 2 | 2 | 3 |
| Epic | 2 | 2 | 4 |
| Legendary | 2 | 3 | 5 |
| Mythic | 3 | 3 | 6 |
| Unique | 3 | 3 | 6 |

Mythic and Unique items always roll the maximum 3 prefixes + 3 suffixes = 6 total modifiers.

---

## Mythic vs Unique

Both Mythic and Unique share the same modifier cap (6) and durability multiplier (5.0x), but they differ in raw power :

| Property | Mythic | Unique |
|:---------|:------:|:------:|
| Stat Multiplier | 2.3x | 2.8x |
| Drop Weight | 0.0625 | 0.016 |
| Approx. Drop Chance | 0.073% | 0.018% |
| Relative Rarity | Baseline | ~3.9x rarer than Mythic |

A Unique item's 2.8x multiplier means every modifier is **22% stronger** than the same modifier on a Mythic item. Combined with identical modifier slots, a Unique is a strict upgrade in raw stat power.

> [!IMPORTANT]
> **Mythic and Unique items can't be upgraded to.** Legendary is the highest rarity you can reach through the upgrade path (via Crown of Transcendence). Mythic and Unique must drop naturally.

---

## Rarity Colors

Each rarity has a distinct color used in tooltips, item names, and UI badges :

| Rarity | Hex Color | Appearance |
|--------|-----------|------------|
| Common | `#C9D2DD` | Silver-gray |
| Uncommon | `#3E9049` | Green |
| Rare | `#2770B7` | Blue |
| Epic | `#8B339E` | Purple |
| Legendary | `#BB8A2C` | Gold |
| Mythic | `#FF4500` | Orange-red |
| Unique | `#AF6025` | Copper-brown |

---

## Related Pages

- [Quality System](quality-system) - The other power axis independent of rarity
- [Drop Mechanics](drop-mechanics) - How rarity weights determine what drops
- [Stones](consumable-currency) - Upgrade path from Common to Legendary
- [Gear Modifiers](modifiers) - Rarity determines how many modifier slots you have

