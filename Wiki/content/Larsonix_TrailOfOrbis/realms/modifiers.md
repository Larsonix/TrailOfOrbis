---
name: Modifiers
title: Realm Modifiers
description: 13 map modifiers - 7 difficulty prefixes and 6 reward suffixes with quality scaling
author: Larsonix
sort-index: 2
order: 103
published: true
---

# Realm Modifiers

Realm Maps roll modifiers just like gear. Prefixes increase difficulty, suffixes increase rewards. Higher-rarity maps can roll more modifiers, creating a risk-reward tradeoff.

---

## Modifier Counts by Rarity

| Map Rarity | Max Total Modifiers |
|:----------:|--------------------:|
| [Common](gear-rarities#common) | 1 |
| [Uncommon](gear-rarities#uncommon) | 2 |
| [Rare](gear-rarities#rare) | 3 |
| [Epic](gear-rarities#epic) | 4 |
| [Legendary](gear-rarities#legendary) | 5 |
| [Mythic](gear-rarities#mythic) / [Unique](gear-rarities#unique) | 6 |

---

## Prefix Modifiers - Difficulty (7)

Prefixes make the Realm harder. Only prefix modifiers contribute to the map's difficulty rating.

| Modifier | Range | Weight |
|:---------|:------|-------:|
| **Monster Damage** | +10-100% | 1 |
| **Monster Life** | +10-150% | 1 |
| **Monster Speed** | +5-40% | 1 |
| **Monster Attack Speed** | +10-50% | 1 |
| **Reduced Time** | -10-40% timeout | 2 |
| **Reduced Healing** | -20-60% healing | 1 |
| **No Regeneration** | Binary (on/off) | 3 |

> [!WARNING]
> **No Regeneration** is the most dangerous prefix. It completely disables your passive HP and mana regeneration. You must rely on life steal, potions, or kill-based recovery to sustain. Its weight of 3 makes it the most commonly rolled prefix.

**Reduced Time** has a weight of 2, making it the second most common prefix. It directly cuts into your timeout, which is especially punishing on Massive Realms where the timer is already tight.

---

## Suffix Modifiers - Reward (6)

Suffixes increase your rewards. All suffix modifiers have a weight of 0.

| Modifier | Range | Weight |
|:---------|:------|-------:|
| **Item Quantity** | +5-50% IIQ | 0 |
| **Item Rarity** | +5-40% IIR | 0 |
| **Experience** | +10-50% XP | 0 |
| **Stone Drops** | +5-30% stones | 0 |
| **Monster Count** | +10-50% count | 0 |
| **Elite Chance** | +5-30% elite spawn | 0 |

> [!TIP]
> **Item Quantity** and **Item Rarity** are the most impactful reward modifiers. IIQ means more drops total. IIR means those drops roll higher rarity. Both stack with Fortune's Compass bonus and other loot bonuses.

---

## Quality Scaling

Modifier values are scaled by map quality through the quality multiplier :

```
qualityMultiplier = 0.5 + quality / 100.0
```

| Quality | Multiplier | Effect on Modifiers |
|--------:|-----------:|:--------------------|
| 1 | 0.51x | Modifier values at roughly half strength |
| 50 | 1.00x | Baseline modifier values |
| 75 | 1.25x | 25% stronger modifiers |
| 101 | 1.51x | Modifier values at roughly 150% strength |

Quality affects **both** prefix and suffix modifiers equally. A high-quality map has stronger difficulty modifiers but also stronger reward modifiers.

---

## Difficulty Rating

The difficulty rating of a map comes from **prefix modifiers only**. Suffix modifiers do not contribute to the difficulty rating, even though Monster Count and Elite Chance increase the actual combat difficulty.

---

## Reading a Map Tooltip

A Realm Map's tooltip displays all its properties :

```
Realm Map - Volcano (Large)
Level 75 | Epic Quality 82
===============================
[PREFIX] Monster Damage +10-100%
[PREFIX] Monster Life +10-150%
[SUFFIX] Item Quantity +5-50%
[SUFFIX] Elite Chance +5-30%
===============================
```

This example shows an [Epic](gear-rarities#epic) map (4 max modifiers) with 2 prefixes and 2 suffixes. The quality of 82 means a 1.32x multiplier on all modifier values.

---

## Modifier Strategy

| Your Goal | Preferred Modifiers |
|-----------|-------------------|
| Safe clear | Avoid Monster Damage, No Regeneration |
| Maximum loot | Stack Item Quantity + Item Rarity suffixes |
| Fast XP | Experience + Monster Count |
| Stone farming | Stone Drops |
| Challenge | Accept all prefixes, the difficulty makes victory more rewarding |

> [!NOTE]
> Use **Gaia's Calibration** (Stone) to reroll all modifier values if you get unfavorable rolls. Use **Alterverse Shard** to reroll all unlocked modifiers entirely.

---

## Related Pages

- [Map Crafting](map-crafting) - How to add, reroll, and manage modifiers on maps
- [Quality System](quality-system) - Map quality scales modifier values
- [Realm Rewards](realm-rewards) - How suffixes improve your loot
- [Overworld vs Realms](overworld-vs-realms) - Why Realms with modifiers are more rewarding
