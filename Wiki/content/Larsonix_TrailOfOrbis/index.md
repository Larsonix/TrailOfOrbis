---
name: Welcome
title: Welcome to Trail of Orbis
description: A deep RPG overhaul mod for Hytale - attributes, combat, gear, realms, and more
author: Larsonix
sort-index: -100
order: 1
published: true
sub-topics:
  - getting-started
  - design-philosophy
  - commands
  - faq
---

AI has been and is my main and sole tool for creating projects the past 3-5 years. If you're not fine with it, I apologise.

![Trail of Orbis](https://wiki.hytalemodding.dev/storage/mods/icons/e6d65dd0-3f80-47aa-ba66-1ff41af30e19.png)

> **Pre-Release** - ModJam Build

## What is Trail of Orbis ?

Trail of Orbis brings the depth of ARPGs like **Path of Exile** and **Last Epoch** into Hytale. Deep itemization, build-defining skill trees, and a multi-stage damage pipeline - combined with procedural dungeons, roguelite map crafting, and a level cap of **1,000,000**. The goal is to mix-and-match the best of ARPGs, Roguelites, Dungeon Crawlers, and Infinite Progression into one package, while staying as true to Hytale's combat, world, and lore as possible.

There are no classes. Your build is the sum of everything : the Gear Modifiers you roll and craft, the Skill Tree nodes you allocate across 485 options, the weapon type you wield, the Stones you invest into your equipment, and the Attribute points you spread across 6 Elements. Every system feeds every other. And the core loop - **Realms** - are procedural instanced dungeons you enter from consumable maps that drop from your very first kills.

---

## The Core Loop

> **Craft Gear → Kill Mobs → Get Loot, Stones & Maps → Upgrade Gateways → Enter Realms → Get Better Loot → Push Deeper → Repeat**

You craft starter gear, kill mobs near spawn, and maps start dropping almost immediately (12% per kill). Every Portal Device is an [Ancient Gateway](ancient-gateways) with a tier that limits which map levels it can channel. Mine ores in the overworld, upgrade your gateways, and unlock harder Realms. The overworld feeds the Realms. Realms are where the real action is : instanced arenas with timed objectives, scaled mobs, and stacked rewards. Every run feeds the next.

---

## By the Numbers

| System | Count |
|:-------|------:|
| Elemental Attributes | 6 |
| Attribute-derived stats | 30 (5 per element, zero overlap) |
| Computed stat fields | 246 across 4 categories |
| Skill Tree nodes | 485 (14 arms × 32 + 36 bridge + 1 origin) |
| Gear Modifier definitions | 101 (prefixes + suffixes) |
| Equipment types | 42 (18 weapons, 20 armor, shield, 2 unknown) |
| Stone types | 25 across 7 categories |
| Mob classes | 5 (Passive, Minor, Hostile, Elite, Boss) |
| Mob stat types | 52 (Dirichlet distribution) |
| Realm biomes | 14 (13 combat + utility) |
| Realm Modifiers | 13 (7 prefix/difficulty + 6 suffix/reward) |
| Gateway tiers | 7 (Copper to Adamantite) |
| Max level | 1,000,000 |

---

## Core Systems

### Progression
- **[Leveling](leveling-experience)** - Kill mobs to earn XP. Each level gives you +1 Attribute Point and +1 Skill Point. Effort-based scaling up to level 1,000,000.
- **[The 6 Elements](attributes)** - Fire, Water, Lightning, Earth, Wind, and Void. Each grants 5 unique stats per point. 30 attribute-derived stats, **246 computed stat fields** total, zero overlap.
- **[Skill Tree](skill-tree)** - 485 nodes across 15 regions you explore physically in a 3D instance. Walk up to glowing orbs, press F to allocate. 6 elemental arms, 8 octant hybrid arms, 12 bridge paths. Free respec anytime.
- **[Realms](procedural-dungeons)** - The core game loop. Consumable maps open portals to procedural instanced dungeons. Maps drop at **12% per kill from level 1**. 14 biome types, 4 sizes, 13 Map Modifiers. Difficulty and rewards scale together.
- **[Ancient Gateways](ancient-gateways)** - Every Portal Device is a tiered gateway that limits which map levels it can channel. Mine overworld ores to upgrade through 7 tiers : Copper (max level 10) to Adamantite (unlimited). The bridge between vanilla exploration and Realm progression.

### Combat
- **[Damage Pipeline](combat-system)** - 11-stage processing from Base Damage to final hit : Base, Flat, Elemental, Conversion, % Physical, % Elemental, % More, Conditionals, Crit, Defenses, and True Damage.
- **[Ailments](status-effects)** - 4 elemental status effects : Burn (Fire *DoT*), Freeze (Water slow), Shock (Lightning damage amp), and Poison (Void stacking *DoT*).
- **[Evasion & Blocking](evasion-dodge)** - Avoidance system : Dodge Chance, Evasion vs Accuracy formula, active Shield Blocking, and passive Block procs.

### Equipment
- **[Gear System](equipment-system)** - 42 equipment types across 7 rarities from Common to Unique. 101 Modifier definitions across Prefixes and Suffixes. Quality 1-101. **52 mob stat types** generated via Dirichlet distribution. Craft your first gear, then improve with drops.
- **[Stones](consumable-currency)** - 25 consumable currency items across 7 categories for rerolling, enhancing, removing, and locking Gear Modifiers.
- **[Gems](gem-system)** - *Work in Progress.* Active and Support Gems with tag-based socket linking. Socket them into Gear, modify abilities with Supports.
- **[Loot](loot-system)** - 8% base Gear drop rate, 5% Stone drop rate, and **12% Realm Map drop rate** from mobs. Rarity distribution follows a 4x geometric drop weight curve.

### Strategies
- **[Overworld vs Realms](overworld-vs-realms)** - Why Realms are the core loop : stronger elites, IIQ/IIR bonuses, completion rewards. The incentive structure explained.
- **[Stones](consumable-currency)** - 25 consumable Stones for rerolling, upgrading, locking, and corrupting Gear and Maps.

---

## Quick Reference

| Element | Stats (per point) |
|:--------|:------------------|
| [**Fire**](attributes#fire) | +0.4% Physical Damage, +0.3% Charged Attack Damage, +0.6% Crit Multiplier, +0.4% [Burn](burn-fire-dot) Damage, +0.1% Ignite Chance |
| [**Water**](attributes#water) | +0.5% Spell Damage, +1.5 Max Mana, +2.0 Energy Shield, +0.15/s Mana Regen, +0.1% [Freeze](freeze-water-slow) Chance |
| [**Lightning**](attributes#lightning) | +0.3% Attack Speed, +0.15% Move Speed, +0.1% Crit Chance, +0.1/s Stamina Regen, +0.1% [Shock](shock-lightning-damage-amp) Chance |
| [**Earth**](attributes#earth) | +0.5% Max Health, +5.0 Armor, +0.2/s Health Regen, +0.2% Passive Block Chance, +0.3% Knockback Resistance |
| [**Wind**](attributes#wind) | +5.0 Evasion, +3.0 Accuracy, +0.5% Projectile Damage, +0.15% Jump Force, +0.3% Projectile Speed |
| [**Void**](attributes#void) | +0.1% Life Steal, +0.05% True Damage, +0.3% *DoT* Damage, +0.5 Mana on Kill, +0.3% Effect Duration |

| Rarity | Drop Weight | Stat Multiplier |
|:-------|------------:|----------------:|
| [Common](gear-rarities#common) | 64 | 0.3x |
| [Uncommon](gear-rarities#uncommon) | 16 | 0.5x |
| [Rare](gear-rarities#rare) | 4 | 0.8x |
| [Epic](gear-rarities#epic) | 1 | 1.2x |
| [Legendary](gear-rarities#legendary) | 0.25 | 1.7x |
| [Mythic](gear-rarities#mythic) | 0.0625 | 2.3x |
| [Unique](gear-rarities#unique) | 0.016 | 2.8x |

| Realm Size | Mob Count | Time Limit |
|:-----------|----------:|:-----------|
| Small | 15 | 5 min |
| Medium | 25 | 10 min |
| Large | 40 | 15 min |
| Massive | 70 | 20 min |

---

## Your Journey

| Phase | Level | Focus | Key Systems |
|:------|:-----:|:------|:------------|
| **Discovery** | 1-10 | Craft starter gear, kill nearby mobs, enter first Realms through Copper Gateways | [Getting Started](getting-started), [Leveling](leveling-experience), [Ancient Gateways](ancient-gateways) |
| **Building** | 10-50 | Push into the overworld, mine Iron/Gold/Cobalt, upgrade gateways, run harder maps | [Skill Tree](skill-tree), [Ancient Gateways](ancient-gateways), [Realm Sizes](realm-sizes) |
| **Optimizing** | 50-100 | Upgrade to Thorium/Mithril gateways, craft gear with Stones, push larger Realms | [Stones](consumable-currency), [Realm Modifiers](realm-modifiers) |
| **Mastery** | 100+ | Adamantite Gateway (unlimited), hunt rare drops, perfect builds | [Map Crafting](map-crafting), [Overworld vs Realms](overworld-vs-realms) |

---

## Where to Start

| I want to... | Start with |
|-------------|-----------|
| **Learn the basics** | [Getting Started](getting-started) → [The 6 Elements](attributes) → [Leveling](leveling-experience) |
| **Understand combat** | [Damage Pipeline](the-11-step-damage-pipeline) → [Critical Strikes](critical-strikes) → [Ailments](status-effects) |
| **Optimize my gear** | [Equipment System](equipment-system) → [Quality](quality-system) → [Stones](consumable-currency) |
| **Push harder content** | [Ancient Gateways](ancient-gateways) → [Overworld vs Realms](overworld-vs-realms) → [Realm Modifiers](realm-modifiers) → [Map Crafting](map-crafting) |

---

## Design Philosophy

> [!NOTE]
> ARPG depth meets Hytale's world. Path of Exile's itemization and damage pipeline, Last Epoch's accessible build crafting, roguelite map preparation, dungeon crawler clearing, and infinite progression - blended together while respecting Hytale's combat, lore, and feel.

**No classes, just choices** - Your build is defined by Gear Modifiers, Skill Tree nodes, weapon type, Stone investments, and Attribute distribution across 6 Elements. Every system contributes. No class selection screen - just choices that compound.

**Risk and reward everywhere** - 7 difficulty prefixes make Realms harder. 6 reward suffixes make the loot better. Harder maps = better drops. Always.

**Realms are the loop** - Not a late unlock. Maps drop from your first kills. The core gameplay is running Realms, improving your gear with what drops, and pushing harder maps. The overworld is your base.

**Infinite progression** - Soft cap at level 100, hard cap at **1,000,000**. Every level gives +1 Attribute Point and +1 Skill Point. The loop never ends.

---

## Getting Help

New to Trail of Orbis ? Start with **[Getting Started](getting-started)** for a walkthrough of your first steps.

Looking for a specific system ? Use the sidebar or the **Where to Start** table above to navigate by intent.

Need the command list ? See **[Commands](commands)** for every player and admin command.

Have questions ? Check the **[FAQ](faq-troubleshooting)** for common issues and answers.
