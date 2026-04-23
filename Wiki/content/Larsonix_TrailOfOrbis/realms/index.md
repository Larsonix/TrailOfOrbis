---
name: Realms Overview
title: Procedural Dungeons
description: Consumable maps, portal lifecycle, 14 biomes, 4 sizes, 13 modifiers - the core game loop
author: Larsonix
sort-index: 0
order: 100
published: true
sub-topics:
  - overworld-vs-realms
  - biomes
  - sizes
  - modifiers
  - rewards
  - map-crafting
---

# Realms - Procedural Dungeons

Realms are the core game loop. You open consumable Realm Maps to create portals into procedural instanced arenas with scaled mobs, timed objectives, and loot rewards. Maps drop at **12% per kill from level 1** - you'll enter your first Realm almost immediately. Every Realm is unique : biome, size, level, shape, and modifiers are all determined by the map.

| | |
|:--|:--|
| **Biomes** | 14 (13 combat + 1 utility) |
| **Sizes** | 4 (Small 15 mobs → Massive 70 mobs) |
| **Map Modifiers** | 13 (7 difficulty + 6 reward) |
| **Reward Multiplier** | 1.0x (Small) to 4.0x (Massive) |
| **Max IIQ from Compass** | +20% (4 uses) |
| **Shapes** | 3 (Circular, Rectangular, Irregular) |

---

## The Core Loop

| Step | What Happens |
|------|-------------|
| **1. Get a Map** | Realm Maps drop from mobs and containers |
| **2. Prepare it** | Use Stones to add modifiers, boost quality, change biome (optional) |
| **3. Activate** | Use the map to open a portal |
| **4. Enter** | Walk through the portal into a fresh instance |
| **5. Clear it** | Kill all mobs before the timer runs out |
| **6. Victory** | Earn rewards : Gear, Stones, more Maps |
| **7. Repeat** | Use your new Maps and Stones to push harder |

---

## Realm Properties

Every Realm Map has these properties :

| Property | Description |
|----------|-------------|
| **Level** | Mob levels inside the Realm (1 to 1,000,000) |
| **Rarity** | [Common](gear-rarities#common) through [Unique](gear-rarities#unique), determines max modifier count |
| **Quality** | 1-101, affects modifier value scaling |
| **Biome** | 14 biome types with varying hazards and difficulty |
| **Size** | Small, Medium, Large, or Massive, determines arena radius, mob count, timeout |
| **Shape** | Circular, Rectangular, or Irregular, affects arena geometry |
| **Prefixes** | Difficulty modifiers that make the Realm harder |
| **Suffixes** | Reward modifiers that improve drops |
| **Fortune's Compass Bonus** | 0-20% Item Quantity bonus from Stone preparation |
| **Corrupted** | Whether the map has been corrupted |
| **Identified** | Whether the map's properties are visible |

---

## Quick Reference

| Size | Arena Radius | Base Mobs | Timeout | Guaranteed Bosses | Reward Mult |
|:-----|-------------:|----------:|:--------|------------------:|------------:|
| Small | 25 | 15 | 5 min | 0 | 1.0x |
| Medium | 49 | 25 | 10 min | 1 | 1.5x |
| Large | 100 | 40 | 15 min | 1 | 2.5x |
| Massive | 200 | 70 | 20 min | 2 | 4.0x |

Mob count scales with level : `baseCount * (1 + level * 0.02)`

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

- **Prefixes** increase difficulty (monster damage, health, speed, attack speed, reduced time, reduced healing, no regeneration)
- **Suffixes** increase rewards (item quantity, item rarity, XP bonus, stone drops, extra monsters, elite chance)

> [!TIP]
> Use [Stones](consumable-currency) to customize maps before running them. Add Fortune's Compass for up to +20% Item Quantity. Change the biome with Alterverse Key. A well-prepared map yields dramatically better rewards.

---

## Quality on Maps

Map quality ranges from 1 to 101 and affects modifier value scaling through the quality multiplier :

```

qualityMultiplier = 0.5 + quality / 100.0

```

| Quality | Multiplier | Effect |
|--------:|-----------:|:-------|
| 1 | 0.51x | Modifier values at ~half strength |
| 50 | 1.00x | Baseline modifier values |
| 101 | 1.51x | Modifier values at ~150% strength |

High quality amplifies both prefix (difficulty) and suffix (reward) modifiers equally.

---

## Entering a Realm

1. Hold a Realm Map in your hand
2. Activate the map to open a portal
3. Walk through the portal
4. You're teleported to a new instance with the Realm's properties
5. Kill all mobs before the timer runs out

The map is consumed on activation. It cannot be reused.

For details on each property, see the individual pages : [Biomes](realm-biomes), [Sizes](realm-sizes), [Modifiers](realm-modifiers), [Rewards](realm-rewards), [Map Crafting](map-crafting).
