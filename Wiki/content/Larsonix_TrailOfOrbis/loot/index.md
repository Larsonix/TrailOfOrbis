---
name: Loot Overview
title: Loot System
description: How gear, stones, and maps drop - base chances, rarity weights, containers, and filtering
author: Larsonix
sort-index: 0
order: 67
published: true
sub-topics:
  - drop-mechanics
  - container-loot
  - level-blending
  - loot-filter
---

# Loot System

Every mob you kill and every container you open has a chance to drop RPG gear, consumable stones, and Realm Maps. The system uses independent rolls for each drop type with configurable base chances.

| | |
|:--|:--|
| **Gear Drop Chance** | 8% per kill |
| **Stone Drop Chance** | 5% per kill |
| **Map Drop Chance** | 12% per kill |
| **Rarity Progression** | 4x geometric (each tier 4x rarer) |
| **Container Tiers** | 4 (Basic, Dungeon, Boss, Special) |
| **Level Blending** | 30% pull toward player level, ±5 cap |
| **Unique Drop Rate** | ~1 in 5,300 gear drops |

> [!NOTE]
> Your very first gear comes from **vanilla crafting** at a workbench. Drops improve your gear over time. Realm Maps drop at 12% per kill from level 1 with no gate - feeding the core Realm loop almost immediately.

---

## Base Drop Chances

| Drop Type | Base Chance per Kill | What Drops |
|-----------|---------------------|-----------|
| **Gear** | 8% | Weapon, armor, or off-hand with random rarity, quality, and modifiers |
| **Stones** | 5% | Consumable stones |
| **Realm Maps** | 12% | Consumable maps that open portals to procedural Realm dungeons |

These are independent rolls. A single kill can drop gear, a stone, and a map simultaneously, any combination, or nothing.

---

## Rarity Weights

When gear drops, its rarity is rolled from this weighted table :

| Rarity | Weight | Approximate Chance |
|--------|--------|-------------------|
| Common | 64 | ~75.0% |
| Uncommon | 16 | ~18.75% |
| Rare | 4 | ~4.69% |
| Epic | 1 | ~1.17% |
| [Legendary](gear-rarities#legendary) | 0.25 | ~0.29% |
| Mythic | 0.0625 | ~0.073% |
| Unique | 0.016 | ~0.018% |

The total weight is 85.3285, making [Common](gear-rarities#common) gear the vast majority of drops. Rarity bonuses from various sources shift the distribution toward higher tiers.

---

## Equipment Slots

When gear drops, it can be one of 6 equipment slots :

- Weapon
- Head
- Chest
- Legs
- Hands
- Off-Hand

The gear pool is dynamically discovered via `DynamicLootRegistry`, which auto-discovers available items at runtime. A static config fallback exists via `LootItemsConfig`.

---

## Container Loot

Containers (chests, barrels, dungeon containers) have their own loot system with 4 tiers. See [Container Loot](container-loot) for details.

| Tier | Loot Multiplier | Rarity Bonus |
|------|----------------|-------------|
| Basic | 1.0x | +0.0 |
| Dungeon | 1.5x | +0.15 |
| Boss | 2.0x | +0.30 |
| Special | 1.75x | +0.25 |

Containers produce gear, stones, and maps.

---

## Further Reading

| Topic | Page |
|-------|------|
| Exact drop formulas | [Drop Mechanics](drop-mechanics) |
| Container tiers and scaling | [Container Loot](container-loot) |
| Quality multiplier formula | [Quality System](quality-system) |
| How item levels are calculated | [Level Blending](level-blending) |
| Filtering unwanted drops | [Loot Filter](loot-filter) |
