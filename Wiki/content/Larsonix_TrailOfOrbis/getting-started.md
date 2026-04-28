---
name: Getting Started
title: Getting Started
description: Your first steps in Trail of Orbis - from spawn to your first Realm
author: Larsonix
sort-index: -90
order: 2
published: true
---

# Getting Started

Trail of Orbis doesn't hold your hand. There's no tutorial quest or guided walkthrough. You spawn, you explore, and you discover systems as you go. This page is the walkthrough the game doesn't give you.

---

## Your Starting State

When you first join a server running Trail of Orbis, here's what you have :

| Stat | Value |
|------|-------|
| Level | 1 |
| Attribute Points | 1 (unallocated) |
| Skill Points | 1 (unallocated) |
| Gear | None |
| XP | 0 |

You'll see an **XP Bar** appear below your hotbar. It shows your current level and progress toward the next one. This is the only HUD Trail of Orbis adds on join - everything else you access through commands.

---

## Step 1 : Craft Your First Gear

You start with no gear, but you're not helpless. Use Hytale's vanilla crafting to build your first weapons and armor at a workbench. Craft a sword, some basic armor, and you're ready to fight.

> [!TIP]
> Vanilla Hytale items you craft are automatically converted into RPG Gear with Modifiers and Quality. A crafted copper sword becomes a leveled RPG weapon.

---

## Step 2 : Kill Your First Mobs

Mobs near spawn are low level. The overworld scales with **distance from spawn** : the farther you go, the harder mobs get. Stay close at first.

Every mob you kill has three independent chances to drop loot :
- **8% chance** to drop a piece of Gear (weapon, armor, or shield)
- **5% chance** to drop a Stone (consumable crafting currency)
- **12% chance** to drop a Realm Map (your ticket to the core game loop)

XP comes exclusively from mob kills. The amount depends on the mob's level, stat pool, and classification :

| Mob Class | XP Multiplier |
|-----------|---------------|
| Passive | 0.1x |
| Minor | 0.5x |
| Hostile | 1.0x |
| Elite | 1.5x |
| Boss | 5.0x |

---

## Step 3 : Spend Your First Attribute Point

You start with 1 unallocated Attribute Point. Open the stats UI with `/stats`, then click the **Attributes** button. Or go directly with `/attr`.

Respec is free anytime with `/too attr reset`.

> [!TIP]
> You only need to learn one command. Every UI page has navigation buttons to every other page. Type `/stats` once and click your way through everything.

You'll see the 6 Elements. Each one grants 5 unique stats per point - 30 total stats across all elements with zero overlap. Pick the one that fits how you want to play :

| Element | What It Does | Good First Pick If... |
|---------|-------------|----------------------|
| **Fire** | Physical Damage, Crit Multiplier, Burn | You want to hit harder |
| **Water** | Spell Damage, Mana, Energy Shield | You plan to use magic weapons |
| **Lightning** | Attack Speed, Move Speed, Crit Chance | You want to attack and move faster |
| **Earth** | Max Health, Armor, Block Chance | You want to survive longer |
| **Wind** | Evasion, Accuracy, Projectile Damage | You use ranged weapons |
| **Void** | Life Steal, True Damage, *DoT* | You want to sustain through damage |

> [!NOTE]
> There are no classes. Your build is defined entirely by how you distribute Attribute Points across the 6 elements. You can respec for free at any time with `/too attr reset`.

---

## Step 4 : Spend Your First Skill Point

You also start with 1 Skill Point. The Skill Tree is a 485-node web across 15 regions : a Core origin, 6 elemental arms, 8 octant hybrid arms (Havoc, Juggernaut, Striker, Warden, Warlock, Lich, Tempest, Sentinel), and 12 bridge paths connecting adjacent elements.

**Enter the Skill Sanctum** - a 3D world where the Skill Tree is laid out physically :
```
/skilltree
```

**Or allocate directly via command :**
```
/too skilltree allocate <node_id>
```

At Level 1 you can only reach the entry nodes of each arm. Pick the arm matching your chosen Element.

> [!TIP]
> You get 1 Attribute Point and 1 Skill Point per level. Respec is always free - experiment freely.

---

## Step 5 : Level Up

Killing mobs earns XP. XP comes only from mob kills - there are no quests, no crafting XP, no passive gains. The leveling curve is effort-based, scaling all the way to the maximum level of 1,000,000.

Each level gives you :
- +1 Attribute Point
- +1 Skill Point

> [!WARNING]
> Death costs **50% of your progress XP toward the next level** at Level 21 and above. You can never lose a level. Below Level 20, you're protected - no XP loss on death.

---

## Step 6 : Understand Your Gear

Once Gear drops, inspect it. Every piece has :

- **Rarity** - 7 tiers from Common to Unique. Higher Rarity means a higher stat multiplier and more Modifiers.
- **Quality** - A number from 1 to 101. Quality multiplies all Modifier values : `multiplier = 0.5 + quality / 100`. Q50 is baseline (1.0x). Q101 is perfect (1.51x).
- **Modifiers** - 101 definitions across Prefixes (offense) and Suffixes (defense). The number of slots depends on Rarity.
- **Implicit** - Weapons have guaranteed Base Damage. Armor has guaranteed defense based on material type.

| Rarity | Stat Multiplier | Drop Weight |
|--------|-----------------|-------------|
| Common | 0.3x | 64 |
| Uncommon | 0.5x | 16 |
| Rare | 0.8x | 4 |
| Epic | 1.2x | 1 |
| Legendary | 1.7x | 0.25 |
| Mythic | 2.3x | 0.0625 |
| Unique | 2.8x | 0.016 |

The drop weights follow a 4x geometric progression - each rarity tier is 4 times rarer than the one below it.

> [!IMPORTANT]
> There are 42 equipment types in the game : 18 weapons, 20 armor pieces (5 materials across 4 slots), a shield, and 2 unclassified types.

---

## Step 7 : Enter Your First Realm

Realm Maps drop at **12% per kill** from the very first mob you fight. You don't need to be a high level - maps are designed to flow early. This is the core game loop.

| Realm Size | Mob Count | Time Limit |
|------------|-----------|------------|
| Small | 15 mobs | 5 min |
| Medium | 25 mobs | 10 min |
| Large | 40 mobs | 15 min |
| Massive | 70 mobs | 20 min |

To open a Realm, you need a Portal Device. A ring of **8 portals** is placed at world spawn - you don't need to craft one. Every Portal Device is an [Ancient Gateway](ancient-gateways) with a tier that limits which map levels it can channel. All portals start at **Copper** (Tier 0, max level 10) - enough for your first maps.

Hold a Realm Map and press F on a portal. If the map's level is within the gateway's cap, the Realm opens. Walk through and kill all mobs before the timer runs out to earn victory rewards.

As you level up and push deeper into the overworld, you'll find harder ores : Iron, Gold, Cobalt, and beyond. Bring them to any portal empty-handed and press F to upgrade its tier - unlocking higher-level Realm Maps. See [Ancient Gateways](ancient-gateways) for the full tier table and upgrade costs.

> [!TIP]
> Start with Small Realms. 15 mobs in 5 minutes is very manageable with crafted gear. Realm mobs drop gear, stones, and more maps - the loop feeds itself.

---

## Essential Commands

You only need to remember **one command** to access everything. Every UI page has navigation buttons linking to every other page - open any page and click your way through the rest.

| Command | Opens |
|---------|-------|
| `/stats` | Character Stats overview |
| `/attr` | Attribute allocation |
| `/skilltree` | Skill Sanctum (3D world) |
| `/sanctum` | Skill Sanctum (3D world) |

A few commands don't have UI pages and must be typed :

| Command | What It Does |
|---------|-------------|
| `/too combat detail` | Toggle detailed damage numbers |
| `/too realm info` | View current Realm details |
| `/too realm exit` | Emergency exit from a Realm |

For the full command list, see [Commands](commands).

---

## The Gameplay Loop

The core loop starts almost immediately :

| Step | What You Do | What You Get |
|------|------------|-------------|
| **Craft gear** | Build starter weapons and armor at a workbench | Equipped for your first fights |
| **Kill mobs** | Fight enemies near spawn | XP + Gear drops + Stone drops + **Realm Maps** |
| **Enter Realms** | Activate maps for instanced dungeons | Harder content, better rewards, more maps |
| **Level up** | Earn XP from kills (overworld and Realms) | +1 Attribute Point, +1 Skill Point |
| **Allocate points** | Invest in Elements and Skill Tree | Stronger stats, new passives |
| **Upgrade Gear** | Use Stones to improve drops | Better Modifiers, higher Quality |
| **Push farther** | Explore deeper into the overworld, run harder maps | Stronger mobs, better loot |

The overworld is your base. Mobs scale with distance from spawn, so pushing outward requires investment in your build. But the main action is in Realms : maps drop frequently (12% per kill), Realm mobs are more rewarding than overworld mobs, and completion bonuses stack on top. Every Realm run feeds the next one. The cycle never ends.
