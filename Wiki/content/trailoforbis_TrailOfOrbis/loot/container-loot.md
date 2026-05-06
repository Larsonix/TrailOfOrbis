---
name: Container Loot
title: Container Loot
description: 4 container tiers - Basic, Dungeon, Boss, Special - with loot multipliers and rarity bonuses
author: Larsonix
sort-index: 1
order: 122
published: true
---

# Container Loot

Chests, barrels, and dungeon containers have their own loot system separate from mob drops. Containers are classified into 4 tiers, each with different multipliers for loot quantity and rarity.

---

## Container Tiers

<div class="too-loot-table">
  <div class="too-loot-header">
    <span>Tier</span>
    <span>Loot Multiplier</span>
    <span>Rarity Bonus</span>
    <span>Example Containers</span>
  </div>
  <div class="too-loot-row">
    <span class="too-rarity-badge too-r-common">Basic</span>
    <span>1.0x</span>
    <span>+0.0</span>
    <span>Regular chests, barrels, crates</span>
  </div>
  <div class="too-loot-row">
    <span class="too-rarity-badge too-r-uncommon">Dungeon</span>
    <span>1.5x</span>
    <span>+0.15</span>
    <span>Dungeon chests, cave containers</span>
  </div>
  <div class="too-loot-row">
    <span class="too-rarity-badge too-r-rare">Boss</span>
    <span>2.0x</span>
    <span>+0.30</span>
    <span>Boss loot, raid rewards, altars</span>
  </div>
  <div class="too-loot-row">
    <span class="too-rarity-badge too-r-epic">Special</span>
    <span>1.75x</span>
    <span>+0.25</span>
    <span>Quest rewards, artifact containers</span>
  </div>
</div>

---

## What Containers Produce

All container tiers can produce 3 types of loot :

| Loot Type | Description |
|-----------|-------------|
| **Gear** | Equipment with rarity, quality, and modifiers |
| **Stones** | Consumable stones for gear and map modification |
| **Maps** | Realm Maps for the core Realm loop |

The loot multiplier affects the quantity of each drop type. The rarity bonus shifts gear drops toward higher rarities.

---

## Tier Details

### Basic (1.0x)
Standard containers found throughout the world. Baseline loot with no rarity bonus. Regular chests, barrels, crates, and storage containers.

### Dungeon (1.5x)
Found in dungeon environments and hidden areas. 50% more loot than Basic with a +0.15 rarity bonus, making [Uncommon](gear-rarities#uncommon) and [Rare](gear-rarities#rare) drops more likely.

### Boss (2.0x)
The highest loot multiplier. Boss containers, raid rewards, legendary containers, and altars produce double the loot of Basic with a significant +0.30 rarity bonus.

### Special (1.75x)
Quest rewards, artifact containers, and other special sources. 75% more loot than Basic with a +0.25 rarity bonus. Slightly less than Boss tier but still substantially better than Basic.

---

## Tier Comparison

| Property | [Basic](container-loot#basic) | [Dungeon](container-loot#dungeon) | [Boss](container-loot#boss) | [Special](container-loot#special) |
|:---------|------:|--------:|-----:|--------:|
| Loot Multiplier | **1.0x** | **1.5x** | **2.0x** | **1.75x** |
| Rarity Bonus | +0.0 | +0.15 | +0.30 | +0.25 |

> [!TIP]
> Boss containers are the most rewarding single-container loot source. Double the quantity and the highest rarity bonus. Prioritize them when exploring.

---

## Related Pages

- [Drop Mechanics](drop-mechanics) - Base drop chances from mob kills
- [Realm Rewards](realm-rewards) - Boss containers inside Realms
- [Loot System](loot-system) - Full loot overview
