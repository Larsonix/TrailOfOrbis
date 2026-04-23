---
name: Gear Overview
title: Equipment System
description: 42 equipment types across weapons, armor, and shields - how gear defines your combat power in Trail of Orbis
author: Larsonix
sort-index: 0
order: 60
published: true
sub-topics:
  - rarities
  - quality
  - modifiers
  - implicits
  - requirements
  - tooltips
---

# Equipment System

Gear is the backbone of your combat power. Your first gear comes from **vanilla crafting** at a workbench - crafted items are automatically converted into RPG Gear with Modifiers and Quality. From there, mob drops and Stone crafting improve your equipment over time. Trail of Orbis has **42 equipment types** spanning melee weapons, ranged weapons, magic implements, thrown weapons, 5 armor materials, and shields.

| | |
|:--|:--|
| **Equipment Types** | 42 (18 weapons, 20 armor, shield, 2 fallbacks) |
| **Rarity Tiers** | 7 (Common → Unique, 4x geometric) |
| **Quality Range** | 1-101 (multiplier : 0.51x to 1.51x) |
| **Modifier Definitions** | 101 (Prefixes = offense, Suffixes = defense) |
| **Max Modifiers** | 6 (on Mythic / Unique) |
| **Armor Materials** | 5 (Plate, Leather, Cloth, Wood, Special) |

---

## Equipment Slots

Hytale gives you **4 armor slots** plus weapons and shields. There's no feet/boots slot.

| Slot | What It Does | Power Share |
|------|-------------|------------|
| **Weapon** | Primary damage source, carries weapon implicit | 30% |
| **Chest** | Largest armor piece, highest defensive stats | 20% |
| **Legs** | Includes movement stats (no feet slot exists) | 23% |
| **Head** | Helmet slot | 12% |
| **Hands** | Gloves, hybrid offense/defense | 8% |
| **Shield** (offhand) | Block chance, optional slot | 7% |

> [!IMPORTANT]
> Hytale has **no feet/boots slot**. Movement-related stats go on the Legs slot instead. That's why Legs has a higher power share (23%) than you might expect.

---

## Weapon Types (18 + 2 Fallbacks)

| Category | Count | Weapons |
|----------|-------|---------|
| **Melee One-Handed** | 6 | Sword, Dagger, Axe, Mace, Claws, Club |
| **Melee Two-Handed** | 3 | Longsword, Battleaxe, Spear |
| **Ranged** | 3 | Shortbow, Crossbow, Blowgun |
| **Thrown** | 3 | Bomb, Dart, Kunai |
| **Magic** | 3 | Staff, Wand, Spellbook |
| **Offhand** | 1 | Shield |

Two fallback types (`Unknown Weapon`, `Unknown Armor`) exist for items that don't match any recognized type.

Weapons use one of two implicit damage types :
- **physical_damage** for melee, ranged, and thrown weapons
- **spell_damage** for magic weapons (Staff, Wand, Spellbook)

---

## Armor Materials (5)

Each material gives a different implicit defense stat and favors a different playstyle :

| Material | Stat Focus | Implicit Defense | Best For |
|----------|-----------|-----------------|----------|
| **Plate** | Defense, health, armor | Armor | Physical damage reduction |
| **Leather** | Agility, evasion, speed | Evasion | Dodge chance |
| **Cloth** | Magic, mana, spell | Energy Shield | Damage absorption |
| **Wood** | Hybrid physical/nature | Max Health | Raw HP pool |
| **Special** | Full modifier access | Armor (default) | Unique/quest items |

Each material has 4 slots : Head, Chest, Legs, and Hands. That makes 20 armor types total plus the Shield offhand.

---

## The 3 Layers of Item Power

Every piece of gear has 3 independent power axes :

1. **Rarity** determines how many modifiers you can have (1 to 6) and your stat multiplier (0.3x to 2.8x)
2. **Quality** multiplies all modifier values, ranging from near-halved at Q1 to 1.51x at Q101
3. **Modifiers** are the specific stats on the item, drawn from 101 distinct definitions

These roll independently. A [Rare](gear-rarities#rare) item with perfect quality can outperform a [Legendary](gear-rarities#legendary) with poor quality on specific stats. See [Rarities](rarities), [Quality](quality), and [Modifiers](modifiers) for the full details.

---

## Gem Sockets

Every weapon has gem sockets :
- **1 Active gem slot** (Slot 0) for an ability gem
- **Multiple Support gem slots** (Slots 1+) that modify the active gem

Support gems must be tag-compatible with the socketed active gem. See the [Gems](gem-system) section for the full gem system.

---

## Gear Data

Internally, every piece of RPG gear tracks :

| Field | Description |
|-------|-------------|
| `instanceId` | Unique identifier for this specific item |
| `level` | Item level (1+), scales implicits and modifier values |
| `rarity` | [Common](gear-rarities#common) through [Unique](gear-rarities#unique) |
| `quality` | 1 to 101, multiplies modifier values |
| `prefixes` | List of offensive modifiers |
| `suffixes` | List of defensive modifiers |
| `corrupted` | Whether the item has been corrupted |
| `weaponImplicit` | Base damage range (weapons only) |
| `armorImplicit` | Base defense value (armor only) |
| `baseItemId` | The underlying Hytale item type |
| `activeGem` | Socketed active gem (if any) |
| `supportGems` | List of socketed support gems |
| `supportSlotCount` | Number of available support gem slots |
