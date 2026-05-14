---
id: realm-modifiers
name: Modifiers
title: Realm Modifiers
description: 29 map modifiers - 18 difficulty prefixes and 11 reward suffixes with quality scaling
author: Larsonix
sort-index: 2
order: 103
published: true
---

# Realm Modifiers

Realm Maps roll modifiers just like gear. Prefixes increase difficulty, suffixes increase rewards. Higher-rarity maps can roll more modifiers, creating a risk-reward tradeoff.

---

## Modifier Counts by Rarity

| Map Rarity | Max Total Modifiers | Prefix Range | Suffix Range |
|:----------:|--------------------:|:------------:|:------------:|
| [Common](gear-rarities#common) | 1 | 0-1 | 0-1 |
| [Uncommon](gear-rarities#uncommon) | 2 | 0-2 | 0-2 |
| [Rare](gear-rarities#rare) | 3 | 0-3 | 0-3 |
| [Epic](gear-rarities#epic) | 4 | 1-3 | 1-3 |
| [Legendary](gear-rarities#legendary) | 5 | 2-3 | 2-3 |
| [Mythic](gear-rarities#mythic) / [Unique](gear-rarities#unique) | 6 | 3 | 3 |

Epic+ maps are guaranteed at least 1 prefix and 1 suffix. Mythic maps always have the maximum 3 of each.

---

## Prefix Modifiers - Difficulty (18)

Prefixes make the Realm harder. Each prefix has a **difficulty weight** (1-3) that contributes to the map's overall difficulty rating.

### Monster Stats

| Modifier | Effect | Range | Difficulty Weight |
|:---------|:-------|:------|------------------:|
| **Monster Damage** | Monsters deal more damage | **+10-100%** | 1 |
| **Monster Life** | Monsters have more health | **+10-150%** | 1 |
| **Monster Speed** | Monsters move faster | **+5-40%** | 1 |
| **Monster Attack Speed** | Monsters attack faster | **+10-50%** | 1 |
| **Monster Armor** | Monsters have more physical resistance | **+15-100%** | 1 |
| **Monster Regeneration** | Monsters regenerate HP per second | **+1-5%** max HP/sec | 2 |

### Elemental Damage

Each element adds bonus damage of that type to all monster attacks. Build elemental resistance to counter them.

| Modifier | Effect | Range | Difficulty Weight |
|:---------|:-------|:------|------------------:|
| **Extra Fire Damage** | Monsters deal bonus Fire damage | **+10-60%** | 1 |
| **Extra Water Damage** | Monsters deal bonus Water damage | **+10-60%** | 1 |
| **Extra Lightning Damage** | Monsters deal bonus Lightning damage | **+10-60%** | 1 |
| **Extra Earth Damage** | Monsters deal bonus Earth damage | **+10-60%** | 1 |
| **Extra Wind Damage** | Monsters deal bonus Wind damage | **+10-60%** | 1 |
| **Extra Void Damage** | Monsters deal bonus Void damage | **+10-60%** | 1 |

### Player Debuffs

| Modifier | Effect | Range | Difficulty Weight |
|:---------|:-------|:------|------------------:|
| **Damage Taken** | You take more damage from all sources | **+8-50%** | 2 |
| **Healing Reduction** | Your healing effectiveness is reduced | **+20-60%** | 1 |
| **No Regeneration** | HP and mana regeneration is disabled | **Binary (on/off)** | 3 |
| **Block Reduction** | Your block damage reduction is less effective | **+15-80%** | 1 |
| **Time Reduction** | The realm timer is shorter | **+10-40%** | 2 |
| **Ailment-Immune Monsters** | Monsters cannot be burned, frozen, shocked, or poisoned | **Binary (on/off)** | 2 |

> [!WARNING]
> **No Regeneration** is the most dangerous prefix (weight 3). It completely disables passive HP and mana regeneration. You must rely on life steal, potions, or kill-based recovery to sustain.

> [!NOTE]
> **No Regeneration** and **Healing Reduction** cannot appear on the same map.
> **Time Reduction** and **Bonus Time** cannot appear on the same map.

---

## Suffix Modifiers - Reward (11)

Suffixes increase your rewards. They do not contribute to the difficulty rating.

### Drop Bonuses

| Modifier | Effect | Range |
|:---------|:-------|:------|
| **Item Quantity** | More items drop overall (IIQ) | **+5-50%** |
| **Item Rarity** | Items drop at higher rarities (IIR) | **+5-40%** |
| **Gear Quality** | Gear drops with higher quality rolls | **+5 to +40** flat |
| **Loot Level** | Gear drops at higher levels than the map | **+2 to +15** flat |
| **Stone Drop Rate** | Stones drop more frequently | **+5-30%** |
| **Stone Rarity Upgrade** | All stones drop one rarity tier higher | **Binary (on/off)** |

### Spawning Bonuses

| Modifier | Effect | Range |
|:---------|:-------|:------|
| **Monster Count** | More monsters spawn (more kills = more loot) | **+10-50%** |
| **Elite Chance** | Higher chance for elite monster spawns | **+5-30%** |

### Other Bonuses

| Modifier | Effect | Range |
|:---------|:-------|:------|
| **Bonus Experience** | More XP gained from kills | **+10-50%** |
| **Bonus Map Chance** | Chance to receive a bonus realm map on completion | **+3-25%** |
| **Bonus Time** | The realm timer is longer | **+10-50%** |

> [!TIP]
> **Item Quantity** and **Item Rarity** are the most impactful reward modifiers. IIQ means more drops total. IIR means those drops roll higher rarity. Both stack with Fortune's Compass bonus and other loot bonuses.

> [!TIP]
> **Gear Quality** and **Loot Level** are different systems. Quality affects how strong a gear piece's stats are (1-101 scale). Loot Level makes gear drop as if the map were higher level, affecting base stat values and requirements. They stack independently for maximum gear power.

---

## Quality Scaling

Modifier values are scaled by map quality through the quality multiplier :

```
qualityMultiplier = 0.5 + quality / 100.0
```

| Quality | Multiplier | Effect on Modifiers |
|--------:|-----------:|:--------------------|
| 1 | **0.51x** | Modifier values at roughly half strength |
| 50 | **1.00x** | Baseline modifier values |
| 75 | **1.25x** | 25% stronger modifiers |
| 101 | **1.51x** | Modifier values at roughly 150% strength |

Quality affects **both** prefix and suffix modifiers equally. A high-quality map has stronger difficulty modifiers but also stronger reward modifiers.

---

## Difficulty Rating

The difficulty rating of a map comes from **prefix modifiers only**. Each prefix has a difficulty weight (1-3), and the sum of all prefix weights determines the star rating displayed on the map. Suffix modifiers do not contribute to the difficulty rating, even though Monster Count and Elite Chance increase actual combat difficulty.

---

## Reading a Map Tooltip

A Realm Map's tooltip displays all its properties. Prefixes appear in red, suffixes in gold :

```
[EPIC] Level 75
Quality : Excellent (82%)
Volcano (Large)

+82% Monster Damage          (red)
+98% Monster Life             (red)
+33% Extra Fire Damage        (red)
--------
+41% Item Quantity            (gold)
+12 Gear Quality              (gold)

Item Quantity: +41% + 15%
```

This example shows an [Epic](gear-rarities#epic) map with 3 prefixes and 2 suffixes. The quality of 82 means a **1.32x** multiplier on all modifier values. Note that Gear Quality displays as a flat `+12` without a percent sign, since it's a flat bonus.

---

## Modifier Strategy

| Your Goal | Preferred Modifiers |
|-----------|-------------------|
| Safe clear | Avoid Damage Taken, No Regeneration |
| Maximum loot | Stack Item Quantity + Item Rarity + Gear Quality |
| Fast XP | Bonus Experience + Monster Count |
| Stone farming | Stone Drop Rate + Stone Rarity Upgrade |
| High-level gear | Loot Level + Gear Quality |
| Map sustain | Bonus Map Chance |
| Challenge | Accept all prefixes, the difficulty makes victory more rewarding |

> [!NOTE]
> Use **Gaia's Calibration** (Stone) to reroll all modifier values if you get unfavorable rolls. Use **Alterverse Shard** to reroll all unlocked modifiers entirely.

---

## Related Pages

- [Map Crafting](map-crafting) - How to add, reroll, and manage modifiers on maps
- [Quality System](quality-system) - Map quality scales modifier values
- [Realm Rewards](realm-rewards) - How suffixes improve your loot
- [Overworld vs Realms](overworld-vs-realms) - Why Realms with modifiers are more rewarding
