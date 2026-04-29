---
id: stones-overview
name: Stones Overview
title: Consumable Currency
description: 25 consumable stones for modifying gear, maps, and items - the crafting layer
author: Larsonix
sort-index: 0
order: 80
published: true
sub-topics: []
---

# Consumable Currency - Stones

Stones are consumable items that modify your Gear and Realm Maps. There are **25 Stone types**, each consumed on use. Stones are the crafting layer. The difference between "good enough" and "perfect."

| | |
|:--|:--|
| **Stone Types** | 25 |
| **Rarest Stones** | 2 Mythic (Warden's Key, Gaia's Perfection) |
| **Upgrade Cap** | Legendary (Mythic+ must drop naturally) |
| **Base Drop Chance** | 5% per mob kill |
| **Boss Drop Chance** | 50% (5% x 10.0 multiplier) |
| **Works on Corrupted** | Only Varyn's Touch and Lorekeeper's Scroll |

---

## All Stones

| Stone | Rarity | Target | Effect |
|:------|:------:|:------:|:-------|
| [Lorekeeper's Scroll](#lorekeepers-scroll) | Common | Both | Identifies an unidentified item |
| [Orb of Unlearning](#orb-of-unlearning) | Common | Both | Grants 1 skill tree refund point |
| [Orb of Realignment](#orb-of-realignment) | Common | Both | Grants 1 attribute refund point |
| [Spark of Potential](#spark-of-potential) | Uncommon | Both | Upgrades Common → Uncommon + 1 Modifier |
| [Ember of Tuning](#ember-of-tuning) | Uncommon | Both | Rerolls ONE random Modifier value |
| [Orbisian Blessing](#orbisian-blessing) | Uncommon | Both | Rerolls Quality (1-100) |
| [Gaia's Gift](#gaias-gift) | Uncommon | Both | Adds one random Modifier |
| [Cartographer's Polish](#cartographers-polish) | Uncommon | Map Only | Improves map Quality by +5% |
| [Fortune's Compass](#fortunes-compass) | Uncommon | Map Only | +5% Item Quantity (stacks to +20%) |
| [Threshold Stone](#threshold-stone) | Uncommon | Both | Rerolls level within +/-3 |
| [Gaia's Calibration](#gaias-calibration) | Rare | Both | Rerolls ALL Modifier values |
| [Alterverse Shard](#alterverse-shard) | Rare | Both | Rerolls all unlocked Modifier types |
| [Ethereal Calibration](#ethereal-calibration) | Rare | Gear Only | Rerolls weapon implicit damage |
| [Purging Ember](#purging-ember) | Rare | Both | Removes ALL unlocked Modifiers |
| [Erosion Shard](#erosion-shard) | Rare | Both | Removes ONE random unlocked Modifier |
| [Transmutation Crystal](#transmutation-crystal) | Rare | Both | Removes one Modifier, adds a new one |
| [Core of Ascension](#core-of-ascension) | Rare | Both | Upgrades Uncommon → Rare + 1 Modifier |
| [Heart of Legends](#heart-of-legends) | Epic | Both | Upgrades Rare → Epic + 1 Modifier |
| [Alterverse Key](#alterverse-key) | Epic | Map Only | Rerolls the map's biome |
| [Varyn's Touch](#varyns-touch) | Epic | Both | Corrupts the item (permanent, unpredictable) |
| [Genesis Stone](#genesis-stone) | Epic | Both | Fills ALL remaining Modifier slots |
| [Crown of Transcendence](#crown-of-transcendence) | Legendary | Both | Upgrades Epic → Legendary + 1 Modifier |
| [Warden's Seal](#wardens-seal) | Legendary | Both | Locks one random unlocked Modifier |
| [Warden's Key](#wardens-key) | Mythic | Both | Unlocks one random locked Modifier |
| [Gaia's Perfection](#gaias-perfection) | Mythic | Both | Sets Quality to 101 (perfect) |

---

## Core Rules

> [!IMPORTANT]
> **Stones are consumed on use.** Successful or not, the Stone is gone. Plan carefully before using rare Stones.

| Rule | Details |
|------|---------|
| **Consumed on use** | Every Stone is single-use |
| **Corruption lock** | Most Stones **cannot** be used on corrupted items |
| **Target types** | Each Stone targets Gear, Maps, or Both |
| **Rarity matches Gear** | Stone Rarity uses the unified GearRarity system (Common through Mythic) |
| **Item ID format** | Native item IDs follow `RPG_Stone_{PascalCaseName}` |

---

## Quality and Modifier Values

Stone effects on Modifier values are influenced by the item's Quality :

```
Modifier multiplier = 0.5 + (quality / 100)
```

| Quality | Multiplier | Effect |
|--------:|-----------:|:-------|
| 1 | 0.51x | Modifier values near their minimum |
| 50 | 1.00x | Modifier values at baseline |
| 100 | 1.50x | Modifier values at 1.5x |
| 101 (perfect) | 1.51x | Maximum possible multiplier |

Quality dramatically affects the outcome of any Stone that rolls Modifier values. Fix Quality with Orbisian Blessing **before** investing expensive Stones.

---

## Corruption System

**Varyn's Touch** (Epic) corrupts an item permanently. Corrupted items :
- Cannot be modified by most other Stones
- May gain, lose, or change Modifiers unpredictably
- Cannot be uncorrupted

Only two Stones work on corrupted items : **Varyn's Touch** itself (for re-corruption) and **Lorekeeper's Scroll** (for identification).

---

## Upgrade Path

> **[Common](gear-rarities#common)** → [Uncommon](gear-rarities#uncommon) *(Spark of Potential)* → [Rare](gear-rarities#rare) *(Core of Ascension)* → [Epic](gear-rarities#epic) *(Heart of Legends)* → [Legendary](gear-rarities#legendary) *(Crown of Transcendence)* → ??? *(must drop naturally)*

> [!CAUTION]
> The upgrade stops at Legendary. Mythic and Unique must drop naturally.

---

## Lock + Purge Combo

The most powerful crafting sequence uses locks and removal together :

1. Find an item with at least one excellent Modifier
2. **Warden's Seal** → lock the excellent Modifier
3. **Purging Ember** → remove all OTHER Modifiers
4. **Gaia's Gift** x N → add new Modifiers one at a time
5. Bad mod appears ? **Erosion Shard** to remove it
6. Repeat until satisfied

Your locked Modifier survives the entire process.

---

## Drop Sources

### From Mobs

| Mob Type | Base Stone Chance | Effective With Multiplier |
|:---------|------------------:|--------------------------:|
| Normal | 5% | 5% |
| Elite | 5% x 3.0 | 15% |
| Boss | 5% x 10.0 | 50% |

### From Containers

| Setting | Value |
|---------|-------|
| Base chance | 15% per container |
| Max per container | 2 |
| Mythic drops | **Not from containers** - mob kills only |

> [!WARNING]
> Mythic Stones (Warden's Key, Gaia's Perfection) cannot drop from containers. They only come from mob kills. That makes them extremely rare and valuable.

---

## Stone Details

### Lorekeeper's Scroll

| Property | Value |
|----------|-------|
| Rarity | Common |
| Target | Both |
| Corrupted | **Yes - works on corrupted items** |
| Effect | Identifies an unidentified item, revealing its Modifiers |

Some items drop unidentified. You can see the Rarity but not the specific Modifiers. Lorekeeper's Scroll reveals everything. Common Rarity, so it's cheap and plentiful.

---

### Orb of Unlearning

| Property | Value |
|----------|-------|
| Rarity | Common |
| Target | Both |
| Corrupted | Yes |
| Effect | Grants 1 skill tree refund point |

Right-click to consume. Does not open the stone picker. Refund points are used to deallocate skill tree nodes.

---

### Orb of Realignment

| Property | Value |
|----------|-------|
| Rarity | Common |
| Target | Both |
| Corrupted | Yes |
| Effect | Grants 1 attribute refund point |

Right-click to consume. Does not open the stone picker. Refund points are used to remove allocated attribute points.

---

### Spark of Potential

| Property | Value |
|----------|-------|
| Rarity | Uncommon |
| Target | Both |
| Corrupted | No |
| Effect | Upgrades Common → Uncommon + adds 1 Modifier |

Only works on Common items. Upgrades to Uncommon and adds 1 Modifier in one step. The cheapest upgrade Stone.

---

### Ember of Tuning

| Property | Value |
|----------|-------|
| Rarity | Uncommon |
| Target | Both |
| Corrupted | No |
| Effect | Rerolls ONE random Modifier's value |

More targeted than Gaia's Calibration. Only one Modifier is rerolled. Cheaper (Uncommon vs Rare) but less control since you can't choose which Modifier changes.

---

### Orbisian Blessing

| Property | Value |
|----------|-------|
| Rarity | Uncommon |
| Target | Both |
| Corrupted | No |
| Effect | Rerolls item Quality between 1 and 100 |

**Cannot roll 101 (perfect).** That requires Gaia's Perfection or a natural drop. Cheap and spammable (Uncommon). Use freely on items with good Modifiers but poor Quality.

> [!TIP]
> Orbisian Blessing is one of the most cost-effective Stones. A weapon with Q15 (0.65x multiplier) rerolled to Q80 (1.30x) doubles its effective Modifier power for the cost of an Uncommon Stone.

---

### Gaia's Gift

| Property | Value |
|----------|-------|
| Rarity | Uncommon |
| Target | Both |
| Corrupted | No |
| Effect | Adds one new random Modifier |

The item must have an open Modifier slot. If all slots are filled (based on Rarity), Gaia's Gift has no effect.

> [!NOTE]
> The maximum number of Modifiers depends on Rarity. If your item's slots are full, upgrade Rarity first to unlock more slots before using Gaia's Gift.

---

### Cartographer's Polish

| Property | Value |
|----------|-------|
| Rarity | Uncommon |
| Target | **Map Only** |
| Corrupted | No |
| Effect | Improves map Quality by +5% |

Only works on Realm Maps. Incrementally improves Quality by 5% per use. Cannot push past Q100. Q101 (perfect) has a 0.5% natural drop chance or is guaranteed via Gaia's Perfection.

---

### Fortune's Compass

| Property | Value |
|----------|-------|
| Rarity | Uncommon |
| Target | **Map Only** |
| Corrupted | No |
| Effect | +5% Item Quantity |
| Stack limit | Max 20% from Fortune's Compass |

Stackable up to 4 uses on the same map for +20% IQ total. More item quantity means more Gear drops from mobs inside the Realm.

> [!TIP]
> Fortune's Compass is Uncommon. Relatively easy to find. Using 4 on a map before running it is one of the most efficient ways to boost your loot income from Realms.

---

### Threshold Stone

| Property | Value |
|----------|-------|
| Rarity | Uncommon |
| Target | Both |
| Corrupted | No |
| Effect | Rerolls level within +/-3 of current level |

Changes the level by up to +/-3. On maps, this adjusts mob levels and potential drop levels. On Gear, this adjusts the item's level requirement and stat scaling. Cannot go below Level 1.

---

### Gaia's Calibration

| Property | Value |
|----------|-------|
| Rarity | Rare |
| Target | Both |
| Corrupted | No |
| Effect | Rerolls ALL Modifier values within their ranges |

Keeps your Modifier **types** but rerolls their **values**. A "+30 Armor" Modifier might become "+45 Armor" or "+15 Armor". Same stat, different number. The new values are influenced by the item's Quality multiplier (`0.5 + quality/100`).

Use this when your Modifiers are right but the rolls are bad.

---

### Alterverse Shard

| Property | Value |
|----------|-------|
| Rarity | Rare |
| Target | Both |
| Corrupted | No |
| Effect | Removes all unlocked Modifiers, rolls new ones |

Strips every unlocked Modifier and replaces them with completely new random ones. **Locked Modifiers are preserved.** Use this when your Modifier types are wrong, like spell damage on a melee weapon.

> [!WARNING]
> This replaces all unlocked Modifiers. Lock your best mod with a **Warden's Seal** first if you want to keep it.

---

### Ethereal Calibration

| Property | Value |
|----------|-------|
| Rarity | Rare |
| Target | **Gear Only** |
| Corrupted | No |
| Effect | Rerolls the weapon's implicit damage value |

Rerolls the Implicit (base) damage within its range. If your weapon rolled 3 on a 3-7 Implicit range, this can push it up to 7. The only Stone restricted to Gear only.

---

### Purging Ember

| Property | Value |
|----------|-------|
| Rarity | Rare |
| Target | Both |
| Corrupted | No |
| Effect | Removes ALL unlocked Modifiers |

Nuclear option. Strips every unlocked Modifier, leaving only locked ones (from Warden's Seal). Use this to start fresh on an item with good base stats but terrible Modifiers.

> [!WARNING]
> This removes everything that isn't locked. Make sure you have locked what you want to keep **before** using Purging Ember.

---

### Erosion Shard

| Property | Value |
|----------|-------|
| Rarity | Rare |
| Target | Both |
| Corrupted | No |
| Effect | Removes ONE random unlocked Modifier |

More surgical than Purging Ember. Removes exactly one Modifier. The target is random among unlocked Modifiers. Use this when you have mostly good mods but one bad one.

---

### Transmutation Crystal

| Property | Value |
|----------|-------|
| Rarity | Rare |
| Target | Both |
| Corrupted | No |
| Effect | Removes one random unlocked Modifier and immediately adds a new one |

Atomic swap. Removes one and adds one in a single operation. Your total Modifier count stays the same. Useful when you want to change a Modifier without losing a slot or risking an empty item.

---

### Core of Ascension

| Property | Value |
|----------|-------|
| Rarity | Rare |
| Target | Both |
| Corrupted | No |
| Effect | Upgrades Uncommon → Rare + adds 1 Modifier |

Only works on Uncommon items. Upgrades to Rare and adds 1 Modifier in one step.

---

### Heart of Legends

| Property | Value |
|----------|-------|
| Rarity | Epic |
| Target | Both |
| Corrupted | No |
| Effect | Upgrades Rare → Epic + adds 1 Modifier |

Only works on Rare items. Upgrades to Epic and adds 1 Modifier.

---

### Alterverse Key

| Property | Value |
|----------|-------|
| Rarity | Epic |
| Target | **Map Only** |
| Corrupted | No |
| Effect | Rerolls the map's biome |

Rerolls the biome to a random different one. If you have a level-appropriate map but don't want the current biome, use this. The new biome is random. You might need multiple attempts to get the one you want.

---

### Varyn's Touch

| Property | Value |
|----------|-------|
| Rarity | Epic |
| Target | Both |
| Corrupted | **Yes - works on corrupted items** |
| Effect | Corrupts the item with unpredictable effects |

The high-risk, high-reward Stone. Corruption is permanent and irreversible. The effects are unpredictable. Your item may change in unexpected ways.

**Corrupted items cannot be modified by other Stones** (except Varyn's Touch itself and Lorekeeper's Scroll). This is the point of no return.

> [!CAUTION]
> Using Varyn's Touch on a perfect item can brick it or make it transcendent. Only corrupt items you are willing to lose.

---

### Genesis Stone

| Property | Value |
|----------|-------|
| Rarity | Epic |
| Target | Both |
| Corrupted | No |
| Effect | Fills ALL remaining Modifier slots with random Modifiers |

A one-shot version of Gaia's Gift that fills **every** empty Modifier slot at once. If a Rare item has 1 Modifier but room for 3, Genesis Stone adds 2 random Modifiers in one use.

> [!TIP]
> Genesis Stone is most efficient on high-Rarity items with empty slots. An Epic item with only 1 Modifier gets all remaining slots filled from a single Genesis Stone.

---

### Crown of Transcendence

| Property | Value |
|----------|-------|
| Rarity | Legendary |
| Target | Both |
| Corrupted | No |
| Effect | Upgrades Epic → Legendary + adds 1 Modifier |

Only works on Epic items. Upgrades to Legendary and adds 1 Modifier. This is the highest craftable Rarity upgrade.

> [!IMPORTANT]
> **No Stone upgrades Legendary or higher.** Mythic and Unique items must drop naturally.

---

### Warden's Seal

| Property | Value |
|----------|-------|
| Rarity | **Legendary** |
| Target | Both |
| Corrupted | No |
| Effect | Locks one random unlocked Modifier |

Locked Modifiers are protected from :
- **Alterverse Shard** - full type reroll skips locked mods
- **Purging Ember** - remove-all skips locked mods
- **Erosion Shard** - remove-one cannot target locked mods

> [!CAUTION]
> Warden's Seal is **Legendary Rarity**. Extremely rare. And unlocking requires the even rarer Warden's Key (Mythic). Think carefully before locking. You are committing to that Modifier.

---

### Warden's Key

| Property | Value |
|----------|-------|
| Rarity | **Mythic** |
| Target | Both |
| Corrupted | No |
| Effect | Unlocks one random locked Modifier |

Reverses a Warden's Seal. As a Mythic Stone, this is one of the two rarest Stones in the game (alongside Gaia's Perfection). Only drops from mob kills. Never from containers.

---

### Gaia's Perfection

| Property | Value |
|----------|-------|
| Rarity | **Mythic** |
| Target | Both |
| Corrupted | No |
| Effect | Sets Quality to 101 (perfect) |

Guarantees the maximum possible Quality. Quality 101 gives a 1.51x Modifier multiplier. The absolute ceiling. As a Mythic Stone, this is one of the two rarest items in the game.

> [!IMPORTANT]
> Quality 101 **cannot be achieved any other way** except a natural drop or this Stone. Orbisian Blessing maxes at Q100. Gaia's Perfection is the only guaranteed path to Q101.

Save this for your absolute best Gear.

---

## Related Pages

- [Quality System](quality-system) - Quality multiplies all Modifier values
- [Gear Modifiers](modifiers) - What Stones modify on your gear
- [Gear Rarities](gear-rarities) - Rarity determines modifier slot count
- [Map Crafting](map-crafting) - Preparing Realm Maps with Stones
