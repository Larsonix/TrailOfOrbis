---
name: Drop Mechanics
title: Drop Mechanics
description: Exact base chances for gear and stone drops, rarity weight table, and equipment slot pool
author: Larsonix
sort-index: 0
order: 121
published: true
---

# Drop Mechanics

8% gear drop chance, 5% stone drop chance, and **12% Realm Map drop chance** per kill, all rolled independently. Maps drop from level 1 with no gate. Rarity follows a 4x geometric progression : each tier is 4x rarer than the last. What changes with level and content is the rarity and quality of what drops, not whether something drops.

---

## Gear Drop Roll

Every mob kill triggers an independent gear drop check :

```
dropChance = 0.08 (8%)
if random(0-1) < dropChance → gear drops
```

The 8% base chance is flat. What changes with level and bonuses is the *rarity* and *quality* of what drops, not whether something drops at all.

---

## Stone Drop Roll

```
stoneDropChance = 0.05 (5%)
if random(0-1) < stoneDropChance → stone drops
```

The 5% base chance is also flat. Stones and gear are rolled independently, so a single kill can produce both.

---

## Realm Map Drop Roll

```
mapDropChance = 0.12 (12%) + (mobLevel × 0.0001)
if random(0-1) < mapDropChance → map drops
```

Maps have the highest base drop chance of any loot type. The chance increases slightly with mob level. Elite mobs have a 2x multiplier, bosses have a 5x multiplier. Maps drop from **level 1 with no gate** - this is the entry point to the core Realm loop.

| Mob Type | Map Drop Chance (Level 1) |
|----------|--------------------------|
| [Normal](elites-bosses#normal) | **~12%** |
| [Elite](elites-bosses#elite) | **~24%** |
| [Boss](elites-bosses#boss) | **~60%** |

---

## Rarity Weight Table

When gear drops, its rarity is rolled from this weighted distribution :

<div class="too-loot-table">
  <div class="too-loot-header">
    <span>Rarity</span>
    <span>Weight</span>
    <span>Chance</span>
  </div>
  <div class="too-loot-row">
    <span class="too-rarity-badge too-r-common">Common</span>
    <span>64</span>
    <span class="too-chance too-chance-high">~75.0%</span>
  </div>
  <div class="too-loot-row">
    <span class="too-rarity-badge too-r-uncommon">Uncommon</span>
    <span>16</span>
    <span class="too-chance too-chance-low">~18.75%</span>
  </div>
  <div class="too-loot-row">
    <span class="too-rarity-badge too-r-rare">Rare</span>
    <span>4</span>
    <span class="too-chance too-chance-rare">~4.69%</span>
  </div>
  <div class="too-loot-row">
    <span class="too-rarity-badge too-r-epic">Epic</span>
    <span>1</span>
    <span class="too-chance too-chance-rare">~1.17%</span>
  </div>
  <div class="too-loot-row">
    <span class="too-rarity-badge too-r-legendary">Legendary</span>
    <span>0.25</span>
    <span class="too-chance too-chance-rare">~0.29%</span>
  </div>
  <div class="too-loot-row">
    <span class="too-rarity-badge too-r-mythic">Mythic</span>
    <span>0.0625</span>
    <span class="too-chance too-chance-rare">~0.073%</span>
  </div>
  <div class="too-loot-row">
    <span class="too-rarity-badge too-r-unique">Unique</span>
    <span>0.016</span>
    <span class="too-chance too-chance-rare">~0.018%</span>
  </div>
</div>

### What This Means in Practice

Out of 1,000 gear drops :
- ~750 will be Common
- ~188 will be Uncommon
- ~47 will be Rare
- ~12 will be Epic
- ~3 will be Legendary
- ~1 might be Mythic
- Unique drops are extraordinarily rare (~1 in 5,300)

---

## Equipment Slot Pool

When gear drops, the slot is selected from these possibilities :

- **Weapon**
- **Head**
- **Chest**
- **Legs**
- **Hands**
- **Off-Hand**

The gear pool is populated through `DynamicLootRegistry`, which automatically discovers available items at runtime. A static fallback exists via `LootItemsConfig` for manually configured items.

---

## Combined Drop Math

| Event | Probability |
|:------|------------:|
| Gear drops from a kill | **8%** |
| Stone drops from a kill | **5%** |
| Map drops from a kill | **12%** |
| Nothing drops | **~77.3%** |
| At least one item drops | **~22.7%** |

> [!NOTE]
> All three rolls are completely independent. A single kill can produce gear, a stone, AND a map simultaneously.

---

## Realm Loot Bonuses

The base drop rates above apply everywhere, but **Realms add IIQ and IIR bonuses** from map modifiers and Fortune's Compass. These stack on top of mob-type bonuses (Elite +50% quantity, Boss +100% quantity).

See [Overworld vs Realms](overworld-vs-realms) for the full loot comparison.
