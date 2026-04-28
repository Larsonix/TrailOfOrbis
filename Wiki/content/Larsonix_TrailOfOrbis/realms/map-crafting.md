---
name: Map Crafting
title: Map Crafting
description: Using stones to prepare realm maps before running them - Fortune's Compass, Alterverse Key, quality, and strategies
author: Larsonix
sort-index: 5
order: 106
published: true
---

# Map Crafting

Realm Maps are items you can modify with [Stones](consumable-currency) before activation. Preparing a map is one of the most effective ways to boost rewards and control difficulty.

---

## Stones That Work on Maps

| Stone | Effect on Maps |
|-------|---------------|
| **Fortune's Compass** | +5% Item Quantity per use (stacks to max 20%) |
| **Alterverse Key** | Changes biome randomly (excludes Corrupted and Skill Sanctum) |
| **Cartographer's Polish** | Improves quality (max 100) |
| **Orbisian Blessing** | Rerolls quality (1-100) |
| **Spark of Potential** | Upgrades [Common](gear-rarities#common) to [Uncommon](gear-rarities#uncommon) (+1 modifier slot) |
| **Core of Ascension** | Upgrades [Uncommon](gear-rarities#uncommon) to [Rare](gear-rarities#rare) (+1 modifier slot) |
| **Heart of Legends** | Upgrades [Rare](gear-rarities#rare) to [Epic](gear-rarities#epic) (+1 modifier slot) |
| **Gaia's Calibration** | Rerolls all modifier values (keeps the same modifiers) |
| **Alterverse Shard** | Rerolls all unlocked modifiers entirely |
| **Gaia's Gift** | Adds one modifier |

---

## Fortune's Compass - The Essential Prep

Fortune's Compass adds +5% Item Quantity per use, stacking up to a maximum of **+20%** (4 uses). This bonus is stored directly on the map as `fortunesCompassBonus` (range 0-20) and stacks with Item Quantity suffix modifiers.

```
Total IIQ = fortunesCompassBonus + itemQuantitySuffixValue
```

> [!TIP]
> 4 Fortune's Compass uses (+20% IIQ) on every map you run is the single most consistent way to increase your loot. The cost is minimal compared to the cumulative benefit over hundreds of maps.

---

## Quality Manipulation

Map quality affects the quality multiplier :

```
qualityMultiplier = 0.5 + quality / 100.0
```

Two stones affect quality :

| Stone | Effect |
|-------|--------|
| **Cartographer's Polish** | Increases quality toward 100 (incremental) |
| **Orbisian Blessing** | Rerolls quality randomly between 1 and 100 |

Cartographer's Polish is the safer option for maps already at decent quality. Orbisian Blessing is a gamble. It could improve a Q20 map to Q85, or drop a Q70 map to Q15.

---

## Rarity Upgrades

Upgrading a map's rarity increases its maximum modifier count :

| Upgrade Path | Stone Required | Result |
|-------------|----------------|--------|
| [Common](gear-rarities#common) → [Uncommon](gear-rarities#uncommon) | Spark of Potential | Max 2 modifiers |
| [Uncommon](gear-rarities#uncommon) → [Rare](gear-rarities#rare) | Core of Ascension | Max 3 modifiers |
| [Rare](gear-rarities#rare) → [Epic](gear-rarities#epic) | Heart of Legends | Max 4 modifiers |
| [Epic](gear-rarities#epic) → [Legendary](gear-rarities#legendary) | Crown of Transcendence | Max 5 modifiers |

Each rarity upgrade adds one additional modifier slot. More modifier slots means more potential suffix (reward) modifiers.

---

## Biome Rerolling

**Alterverse Key** changes the map's biome to a random different biome. The reroll uses `randomNonCorrupted()`, which excludes both **Corrupted** and **Skill Sanctum** from the possible results. You can reroll multiple times to target a specific biome.

---

## Recommended Map Prep Workflows

### Basic Prep (Cheap)

1. **Fortune's Compass** x4 → +20% Item Quantity

*Cost : 4 stones. Best bang for your buck.*

### Standard Prep (Moderate)

1. **Fortune's Compass** x4 → +20% IIQ
2. Upgrade rarity if [Common](gear-rarities#common) or [Uncommon](gear-rarities#uncommon) → more modifier slots
3. **Orbisian Blessing** or **Cartographer's Polish** → improve quality

### Full Prep (Expensive)

1. **Fortune's Compass** x4 → +20% IIQ
2. Upgrade rarity to [Legendary](gear-rarities#legendary) (if lower)
3. **Alterverse Key** → target desired biome
4. **Gaia's Calibration** → optimize modifier values
5. **Cartographer's Polish** → push quality high

---

## What You Can and Cannot Change

| Property | Changeable | How |
|----------|-----------|-----|
| Biome | Yes | Alterverse Key |
| Quality | Yes | Orbisian Blessing, Cartographer's Polish |
| Rarity | Yes (upgrade only) | Spark of Potential, Core of Ascension, Heart of Legends, Crown of Transcendence |
| Modifiers | Yes | Gaia's Calibration (reroll values), Alterverse Shard (reroll modifiers), Gaia's Gift (add) |
| Fortune's Compass Bonus | Yes | Fortune's Compass (up to +20%) |
| Level | Yes (plus or minus 3) | Threshold Stone |
| **Size** | **No** | Fixed at drop time |
| **Shape** | **No** | Fixed at drop time |

> [!IMPORTANT]
> **Size and shape cannot be changed.** A Small map stays Small. Level can be adjusted plus or minus 3 via Threshold Stone. If you want Massive maps or specific shapes, you need to find them through drops.

---

## Related Pages

- [Consumable Currency](consumable-currency) - All 25 stone types and their targets
- [Realm Modifiers](realm-modifiers) - What modifiers do to difficulty and rewards
- [Overworld vs Realms](overworld-vs-realms) - Why map preparation matters
