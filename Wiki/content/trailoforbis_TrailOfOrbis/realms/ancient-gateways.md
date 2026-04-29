---
name: Ancient Gateways
title: Ancient Gateways
description: Tiered Portal Devices that gate which Realm Map levels they can channel - the bridge between overworld exploration and Realm progression
author: Larsonix
sort-index: -2
order: 98
published: true
---

# Ancient Gateways

Every Portal Device in the world is an Ancient Gateway. Gateways have a tier that caps the maximum level of Realm Maps they can channel. Bring metal bars and essences from progressively harder overworld zones to upgrade them. The deeper you push into the world, the harder the Realms you unlock.

| | |
|:--|:--|
| **Tiers** | 7 (Copper through Adamantite) |
| **Default** | Tier 0 (Copper Gateway, max level 10) |
| **Final** | Tier 6 (Adamantite Gateway, unlimited) |
| **Storage** | Per-block (every portal has its own tier) |
| **Shared** | Any player can use or upgrade any gateway |
| **Spawn Ring** | 8 portals at world spawn |

---

## The Progression Loop

> **Explore overworld → Mine ores → Upgrade gateway → Run harder Realms → Get gear → Push deeper → Repeat**

You fight level ~8 mobs in Zone 1 to get Iron. Iron upgrades your gateway to level 20. Level 20 Realms are harder than the mining, but the gear and XP from those Realms prepare you for Zone 2. Zone 2 gives Cobalt, which unlocks level 45. The pattern repeats all the way to Adamantite.

---

## Gateway Tiers

| Tier | Name | Max Realm Level | Upgrade Cost |
|:-----|:-----|----------------:|:-------------|
| 0 | Copper Gateway | 10 | *(free - default)* |
| 1 | Iron Gateway | 20 | 15x Iron Bar, 5x Life Essence |
| 2 | Gold Gateway | 30 | 12x Gold Bar, 8x Life Essence |
| 3 | Cobalt Gateway | 45 | 10x Cobalt Bar, 10x Void Essence |
| 4 | Thorium Gateway | 60 | 10x Thorium Bar, 10x Life Essence, 10x Void Essence |
| 5 | Mithril Gateway | 80 | 8x Mithril Bar, 15x Void Essence |
| 6 | Adamantite Gateway | Unlimited | 5x Adamantite Bar, 5x Voidheart |

> [!NOTE]
> Each tier's max level is set above the mob levels in the source zone. You mine Iron from level ~8 mobs, then unlock Realms up to level 20. The Realms are harder than the mining - but the rewards prepare you for the next zone.

---

## How to Upgrade

1. Walk to any Portal Device **empty-handed**
2. Press F to open the Gateway Upgrade UI
3. The UI shows your current tier, the next tier, and required materials
4. Materials display green (have enough) or red (not enough) with exact counts
5. Click **Upgrade** when all materials are satisfied
6. Materials are consumed from your hotbar, storage, and backpack
7. The gateway advances to the next tier

> [!TIP]
> You don't need to carry everything in your hotbar. The upgrade scans your entire inventory - hotbar, storage, and backpack.

---

## Using a Portal Device

What happens when you press F on a Portal Device depends on what you're holding and the portal's state :

| What You're Holding | Portal State | What Happens |
|:--------------------|:------------|:-------------|
| Nothing | Idle | Gateway Upgrade UI |
| Realm Map (level within cap) | Idle | Realm opens normally |
| Realm Map (level too high) | Idle | Error : gateway can't channel maps above its tier cap |
| Fragment Key | Idle | Vanilla Fragment Key flow (unchanged) |
| Any item | Active (Realm) | Realm info page |
| Any item | Active (vanilla) | Vanilla active portal page |

> [!IMPORTANT]
> Fragment Keys work exactly as they do in vanilla Hytale. The gateway system only appears when you interact empty-handed with an idle portal. No vanilla functionality is lost.

---

## Spawn Gateways

A ring of 8 Portal Devices is placed automatically around world spawn (radius 40 blocks). These are available immediately - no crafting required. They start at Tier 0 (Copper) and can be upgraded like any other portal.

Want portals elsewhere ? Craft them at an Arcane Workbench. Crafted portals also start at Tier 0 and upgrade identically.

---

## Where to Find Materials

Overworld ores get harder as you move further from spawn. Mob levels scale at roughly 1 level per 75 blocks from spawn.

| Metal | Zone | Distance from Spawn | Mob Levels |
|:------|:-----|--------------------:|:-----------|
| Copper | Zone 1 (Forest) | 0-500 | 1-8 |
| Iron | Zone 1 (widespread) | 100-800 | 5-14 |
| Gold | Zone 1-2 (deeper caves) | 300-1,200 | 10-17 |
| Cobalt | Zone 2 (Desert / Savanna) | 800-1,500 | 14-21 |
| Thorium | Zone 2-3 transition | 1,200-2,000 | 17-27 |
| Mithril | Zone 3 (Tundra) | 2,000-3,000 | 27-41 |
| Adamantite | Zone 4 (Volcanic) | 3,000+ | 47+ |

Life Essence and Void Essence drop from mobs and containers. Voidheart is a rare drop from high-level Void zone mobs.

> [!TIP]
> The distances and mob levels above are approximate. Use them as a guide for how far you need to push before the next tier's materials become available.

---

## Per-Block, Not Per-Player

Each Portal Device in the world stores its own tier. If someone upgrades a gateway, everyone benefits. Unregistered portals default to Tier 0. You don't need to track which portals you've upgraded - walk up to any portal empty-handed and the UI tells you its current state.

---

## Related Pages

- [Procedural Dungeons](procedural-dungeons) - Full Realm system overview
- [Overworld vs Realms](overworld-vs-realms) - Why Realms are the core loop
- [Map Crafting](map-crafting) - Preparing maps with Stones before running them
- [Realm Sizes](realm-sizes) - Arena dimensions and mob counts
- [Getting Started](getting-started) - First steps including your first gateway
