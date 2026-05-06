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

<figure class="too-figure">
  <img src="https://wiki.hytalemodding.dev/storage/mods/019da288-1a74-71ad-bb01-6e0ae3d015c6/files/51f7b15a-75d8-456b-960b-ae3d7b0bdad0" alt="Epic sword tooltip showing rarity color, modifiers, and stat multiplier">
</figure>

An Epic-rarity sword with its purple name, stat multiplier, and four modifier slots filled.

<div class="too-figure-end"></div>

---

## The 7 Tiers

<div class="too-rarity-table">
  <div class="too-rarity-header">
    <span>Rarity</span><span>Max Mods</span><span>Max Prefix</span><span>Max Suffix</span><span>Stat Mult</span><span>Durability</span><span>Drop Weight</span><span>Approx. Chance</span>
  </div>
  <div class="too-rarity-row too-r-common">
    <span class="too-rarity-name"><a href="gear-rarities#common">Common</a></span>
    <span>1</span>
    <span>1</span>
    <span>1</span>
    <span class="too-mult">0.3x</span>
    <span class="too-mult">1.0x</span>
    <span>64.0</span>
    <span class="too-chance too-chance-max">75.0%</span>
  </div>
  <div class="too-rarity-row too-r-uncommon">
    <span class="too-rarity-name"><a href="gear-rarities#uncommon">Uncommon</a></span>
    <span>2</span>
    <span>1</span>
    <span>2</span>
    <span class="too-mult">0.5x</span>
    <span class="too-mult">1.2x</span>
    <span>16.0</span>
    <span class="too-chance too-chance-mid">18.75%</span>
  </div>
  <div class="too-rarity-row too-r-rare">
    <span class="too-rarity-name"><a href="gear-rarities#rare">Rare</a></span>
    <span>3</span>
    <span>2</span>
    <span>2</span>
    <span class="too-mult">0.8x</span>
    <span class="too-mult">1.5x</span>
    <span>4.0</span>
    <span class="too-chance too-chance-low">4.69%</span>
  </div>
  <div class="too-rarity-row too-r-epic">
    <span class="too-rarity-name"><a href="gear-rarities#epic">Epic</a></span>
    <span>4</span>
    <span>2</span>
    <span>2</span>
    <span class="too-mult">1.2x</span>
    <span class="too-mult">2.0x</span>
    <span>1.0</span>
    <span class="too-chance too-chance-rare">1.17%</span>
  </div>
  <div class="too-rarity-row too-r-legendary">
    <span class="too-rarity-name"><a href="gear-rarities#legendary">Legendary</a></span>
    <span>5</span>
    <span>2</span>
    <span>3</span>
    <span class="too-mult">1.7x</span>
    <span class="too-mult">3.0x</span>
    <span>0.25</span>
    <span class="too-chance too-chance-rare">0.29%</span>
  </div>
  <div class="too-rarity-row too-r-mythic">
    <span class="too-rarity-name"><a href="gear-rarities#mythic">Mythic</a></span>
    <span>6</span>
    <span>3</span>
    <span>3</span>
    <span class="too-mult">2.3x</span>
    <span class="too-mult">5.0x</span>
    <span>0.0625</span>
    <span class="too-chance too-chance-rare">0.073%</span>
  </div>
  <div class="too-rarity-row too-r-unique">
    <span class="too-rarity-name"><a href="gear-rarities#unique">Unique</a></span>
    <span>6</span>
    <span>3</span>
    <span>3</span>
    <span class="too-mult">2.8x</span>
    <span class="too-mult">5.0x</span>
    <span>0.016</span>
    <span class="too-chance too-chance-rare">0.018%</span>
  </div>
</div>

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
| [Common](gear-rarities#common) | 1 | 1 | 1 |
| [Uncommon](gear-rarities#uncommon) | 1 | 2 | 2 |
| [Rare](gear-rarities#rare) | 2 | 2 | 3 |
| [Epic](gear-rarities#epic) | 2 | 2 | 4 |
| [Legendary](gear-rarities#legendary) | 2 | 3 | 5 |
| [Mythic](gear-rarities#mythic) | 3 | 3 | 6 |
| [Unique](gear-rarities#unique) | 3 | 3 | 6 |

Mythic and Unique items always roll the maximum 3 prefixes + 3 suffixes = 6 total modifiers.

---

## Mythic vs Unique

Both Mythic and Unique share the same modifier cap (6) and durability multiplier (5.0x), but they differ in raw power :

| Property | [Mythic](gear-rarities#mythic) | [Unique](gear-rarities#unique) |
|:---------|:------:|:------:|
| Stat Multiplier | **2.3x** | **2.8x** |
| Drop Weight | 0.0625 | 0.016 |
| Approx. Drop Chance | **0.073%** | **0.018%** |
| Relative Rarity | Baseline | ~3.9x rarer than [Mythic](gear-rarities#mythic) |

A Unique item's 2.8x multiplier means every modifier is **22% stronger** than the same modifier on a Mythic item. Combined with identical modifier slots, a Unique is a strict upgrade in raw stat power.

> [!IMPORTANT]
> **Mythic and Unique items can't be upgraded to.** Legendary is the highest rarity you can reach through the upgrade path (via Crown of Transcendence). Mythic and Unique must drop naturally.

---

## Rarity Colors

Each rarity has a distinct color used in tooltips, item names, and UI badges :

| Rarity | Hex Color | Appearance |
|--------|-----------|------------|
| [Common](gear-rarities#common) | `#C9D2DD` | Silver-gray |
| [Uncommon](gear-rarities#uncommon) | `#3E9049` | Green |
| [Rare](gear-rarities#rare) | `#2770B7` | Blue |
| [Epic](gear-rarities#epic) | `#8B339E` | Purple |
| [Legendary](gear-rarities#legendary) | `#BB8A2C` | Gold |
| [Mythic](gear-rarities#mythic) | `#FF4500` | Orange-red |
| [Unique](gear-rarities#unique) | `#AF6025` | Copper-brown |

---

## Related Pages

- [Quality System](quality-system) - The other power axis independent of rarity
- [Drop Mechanics](drop-mechanics) - How rarity weights determine what drops
- [Stones](consumable-currency) - Upgrade path from Common to Legendary
- [Gear Modifiers](modifiers) - Rarity determines how many modifier slots you have

