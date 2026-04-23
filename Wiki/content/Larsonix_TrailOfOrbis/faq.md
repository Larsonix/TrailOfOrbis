---
name: FAQ
title: FAQ & Troubleshooting
description: Common questions, issues, and solutions for Trail of Orbis
author: Larsonix
sort-index: -75
order: 5
published: true
---

# FAQ & Troubleshooting

---

## Getting Started

**Q : What do I do first ?**
Craft basic gear at a workbench, then kill mobs near spawn. You'll earn XP, get loot drops, and find Realm Maps (12% drop chance) to enter the core game loop. See [Getting Started](getting-started).

**Q : I spawned with no Gear. Is that normal ?**
Yes. All players start at Level 1 with 1 Attribute Point, 1 Skill Point, and no Gear. Craft your first gear using Hytale's vanilla crafting. Crafted items are automatically converted into RPG Gear with Modifiers and Quality.

**Q : How do I open the stats / Attributes / Skill Tree ?**
Type `/stats`. From there, buttons navigate to every other UI page. You only need one command. See [Commands](commands).

---

## Leveling

**Q : What's the max level ?**
1,000,000. The leveling formula is effort-based and scales indefinitely.

**Q : How do I get XP ?**
Mob kills only. No quests, no crafting XP, no passive gains. The amount depends on mob level, stat pool, and classification.

**Q : How many points do I get per level ?**
1 Attribute Point and 1 Skill Point per level, every level.

**Q : What are mob classifications ?**
5 classes with different XP multipliers : Passive (0.1x), Minor (0.5x), Hostile (1.0x), Elite (1.5x), Boss (5.0x).

---

## Attributes & Skills

**Q : Can I respec my Attributes ?**
Yes, for free, anytime. `/too attr reset` for Attributes or `/too skilltree respec` for the Skill Tree. No cost, no cooldown.

**Q : Which Element should I pick first ?**
Whatever appeals to you. Respec is free. See [Attributes](attributes) for a comparison, or just try one and change later.

**Q : How many stats does each element give ?**
5 unique stats per element, 30 total across all 6 elements with zero overlap.

**Q : How big is the Skill Tree ?**
485 nodes across 15 regions : a Core origin, 6 elemental arms (32 nodes each), 8 octant hybrid arms (32 nodes each), and 12 bridge paths (3 nodes each) connecting adjacent elements.

**Q : What is the Skill Sanctum ?**
A 3D instance world where the Skill Tree exists as physical floating orbs you walk through and interact with. Enter with `/skilltree` or `/too sanctum enter`.

---

## Combat

**Q : I keep dying. What am I doing wrong ?**
Check your Death Recap. It tells you what killed you. Common issues :
- No defensive stats. Invest in [Earth](attributes#earth) (Armor, Health) or [Water](attributes#water) (Energy Shield)
- No resistances. Add resistance Suffixes to your Gear via Stones
- Fighting mobs above your level. Mob scaling matters

**Q : What's the death penalty ?**
50% of your progress XP toward the next level is lost on death, starting at Level 21. You can never lose a level. Players at Level 20 or below are protected with no XP loss. See [Death Penalty](death-penalty).

**Q : How does crit work ?**
Base crit chance is 5%, base crit multiplier is 150%. [Lightning](attributes#lightning) increases crit chance, [Fire](attributes#fire) increases crit multiplier. Gear Modifiers and Skill Tree nodes can boost both.

**Q : What are the 4 ailments ?**
- **[Burn](burn-fire-dot)** ([Fire](attributes#fire)) Damage over time
- **[Freeze](freeze-water-slow)** ([Water](attributes#water)) Slows the target
- **[Shock](shock-lightning-damage-amp)** ([Lightning](attributes#lightning)) Amplifies damage taken
- **[Poison](poison-void-stacking-dot)** ([Void](attributes#void)) Stacking damage over time

**Q : How does the damage pipeline work ?**
Every hit passes through multiple stages in order : Base, Flat, Elemental, Conversion, % Physical, % Elemental, % More, Conditionals, Crit, Defenses, True Damage. Use `/too combat detail` to see the full breakdown per hit. See [Damage Pipeline](the-11-step-damage-pipeline).

---

## Gear

**Q : Items show "Invalid Item" in my hotbar.**
The asset pack is missing from the server. This is a server deployment issue. The admin needs to deploy the asset pack alongside the plugin.

**Q : What do the Rarity colors mean ?**
7 tiers : [Common](gear-rarities#common) (white), Uncommon (green), [Rare](gear-rarities#rare) (blue), [Epic](gear-rarities#epic) (purple), [Legendary](gear-rarities#legendary) (orange), [Mythic](gear-rarities#mythic) (red), [Unique](gear-rarities#unique) (gold). See [Rarities](gear-rarities).

**Q : What is Quality ?**
A number from 1 to 101 that multiplies all Modifier values on a piece of Gear. Formula : `multiplier = 0.5 + quality / 100`. Q1 is 0.51x, Q50 is baseline (1.0x), Q101 is perfect (1.51x).

**Q : How many Gear Modifiers exist ?**
101 Modifier definitions across Prefixes (offense) and Suffixes (defense).

**Q : How many equipment types are there ?**
42 total : 18 weapons, 20 armor pieces (5 materials across 4 armor slots), a shield, and 2 unclassified types.

**Q : Why does my weapon say "[GEAR]" ?**
This tag marks it as RPG-generated equipment with Modifiers, Quality, and Rarity, as opposed to a vanilla Hytale item.

---

## Loot & Drops

**Q : What are the drop rates ?**
8% chance for Gear, 5% chance for a Stone, and 12% chance for a Realm Map, per mob kill. These are independent rolls.

**Q : How does Rarity distribution work ?**
Drop weights follow a 4x geometric progression : Common (64), Uncommon (16), Rare (4), Epic (1), Legendary (0.25), Mythic (0.0625), Unique (0.016).

**Q : What are container tiers ?**
4 tiers : Basic, Dungeon, Boss, Special. Each has different loot tables and drop quality.

---

## Stones & Crafting

**Q : How many Stones are there ?**
25 Stone types across 7 categories.

**Q : I used a Stone and nothing happened. Why ?**
Check that the Stone's target type matches your item (some are Gear Only, some are Map Only). See [Stones](consumable-currency).

---

## Realms

**Q : How do I enter a Realm ?**
Hold a Realm Map and activate it. The map is consumed and a portal opens. Walk through to enter. Maps drop at 12% per kill from level 1 - you don't need to wait. See [Realm Rewards](realm-rewards) for timeout and exit details.

**Q : When can I start running Realms ?**
Almost immediately. Maps drop from your very first mob kills at 12% base chance. Craft some basic gear and you're ready for a Small Realm.

**Q : What sizes are available ?**
4 sizes : Small (15 mobs, 5 min), Medium (25 mobs, 10 min), Large (40 mobs, 15 min), Massive (70 mobs, 20 min).

**Q : How many biomes are there ?**
14 total : 13 combat biomes and 1 utility biome (Skill Sanctum).

**Q : What are Realm Modifiers ?**
13 Modifiers split into 7 prefix modifiers (increase difficulty) and 6 suffix modifiers (increase rewards).

**Q : I'm stuck in a Realm. How do I leave ?**
Type `/too realm exit`. This is an emergency command. It forfeits victory rewards.

**Q : The timer ran out. Did I lose everything ?**
No. You keep all XP and loot from individual mob kills. You only lose the victory bonus rewards.

**Q : Can I enter Realms solo ?**
Yes, all sizes. Massive Realms are designed for groups (70 mobs, 20 min timer) but solo is possible with good Gear and build.

---

## Technical

**Q : How do I see detailed stats ?**
`/stats` opens a breakdown of all 153 computed stat fields across 5 categories.

**Q : How do I see damage numbers in combat ?**
`/too combat detail` toggles the detailed damage breakdown in chat. Shows every stage of the pipeline per hit.

**Q : Is my data saved if the server crashes ?**
Player data (level, Attributes, Skill Tree, preferences) is stored in the database and persists across sessions. Gear in your inventory follows Hytale's normal save system.

**Q : What's the `/too` command ?**
The main command prefix. Aliases : `/trailoforbis`, `/orbis`. Admin prefix is `/tooadmin` (alias `/tooa`). See [Commands](commands) for the full reference.
