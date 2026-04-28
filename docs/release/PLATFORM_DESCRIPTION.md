# Platform Description

One description for all Markdown platforms. Copy everything between the `---` markers below and paste into CurseForge, ModTale, Modifold, Thunderstore.

Images go in each platform's **gallery/carousel**, not in the description. See gallery order at the bottom.

For BuiltByBit (BBCode), use `BUILTBYBIT_DESCRIPTION.bbcode` instead.

---
PASTE START
---

*Trail of Orbis is in pre-release. This is the ModJam build. Things may change. Join the [Hytale Modding Discord](https://discord.gg/hytalemodding) for feedback and updates.*

*AI has been and is my main and sole tool for creating this project. If you're not fine with it, I apologise.*

---

## Recommended Mods

Trail of Orbis works standalone. These mods are fully compatible and tested :

- **[Hexcode](https://www.curseforge.com/hytale/mods/hexcode)** - Replaces base Hytale magic with a full spell-crafting system. When installed, Trail of Orbis detects it automatically : RPG damage pipeline, gear tooltips, and magic nodes in the Skill Tree all adapt. If you want magic builds, this is it. *Incompatible with PartyPro (HUD conflict).*
- **[The Armory](https://www.curseforge.com/hytale/mods/the-armory)** - Over 1000 new cosmetic weapons and armors. Trail of Orbis includes built-in transmogrification at the Builder's Workbench - change the look of your RPG gear to any skin while keeping all stats.
- **[Loot4Everyone](https://www.curseforge.com/hytale/mods/loot4everyone)** - Better shared container loot for multiplayer. Trail of Orbis already includes distance-based shared loot for containers, but Loot4Everyone is a better implementation.
- **[PartyPro](https://www.curseforge.com/hytale/mods/partypro)** - Proper party system with group features. Trail of Orbis includes distance-based shared XP, but PartyPro adds invites, UI, and more. *Incompatible with Hexcode (HUD conflict).*

---

## What is Trail of Orbis ?

You may know that moment in RPGs where you finally understand how your build works - how the damage conversion feeds the ailments, how the tree connects to the gear, how the map modifiers change what drops ? That moment where the systems click and you realize you're not just playing a game, you're solving a build ?

That's what Trail of Orbis brings to Hytale.

There are no classes. Your build is the sum of everything : the Gear Modifiers you roll and craft, the 485 Skill Tree nodes you allocate by walking through a 3D sanctum, the weapon type you wield, the Stones you invest into your equipment, and the Attribute points you spread across 6 Elements. Every system feeds every other.

And the core loop starts immediately.

> **Craft Gear -> Kill Mobs -> Get Loot, Stones & Maps -> Upgrade Gateways -> Enter Realms -> Get Better Loot -> Push Deeper -> Repeat**

---

## Realms

You pick up a Realm Map from a mob drop. It has a biome, a size, maybe some modifiers rolled on it. You walk up to a Portal Device in the overworld - every single one is an Ancient Gateway with a tier from Copper to Adamantite. Activate it with the map, a portal opens, step through.

You're in. Timer starts. Mobs are already there. Kill everything before the timer runs out and you get completion rewards on top of whatever dropped during the fight. Die, and you lose the map. Harder modifiers on the map mean harder mobs, but also better loot. Always.

- **14 biomes** - Forest, Desert, Beach, Tundra, Volcano, Jungle, Caverns, and more. Each with its own terrain, mobs, and feel.
- **4 sizes** - Small (15 mobs, 5 min) to Massive (70 mobs, 20 min). Bigger = more loot.
- **13 Map Modifiers** - 7 difficulty Prefixes make Realms harder. 6 reward Suffixes make loot better.
- **7 Gateway tiers** - Copper (max level 10) to Adamantite (unlimited). Mine overworld ores to upgrade. The overworld feeds the Realms.

---

## The 6 Elements

Fire, Water, Lightning, Earth, Wind, and Void. Each grants 5 unique stats per point. 30 total, zero overlap.

| **Element** | **What it gives you** |
|:--------:|:------------------|
| **Fire** | Physical Damage, Charged Attack Damage, Crit Multiplier, Burn Damage, Ignite Chance |
| **Water** | Spell Damage, Max Mana, Energy Shield, Mana Regen, Freeze Chance |
| **Lightning** | Attack Speed, Move Speed, Crit Chance, Stamina Regen, Shock Chance |
| **Earth** | Max Health, Armor, Health Regen, Passive Block Chance, Knockback Resistance |
| **Wind** | Evasion, Accuracy, Projectile Damage, Jump Force, Projectile Speed |
| **Void** | Life Steal, True Damage, DoT Damage, Mana on Kill, Effect Duration |

Fire has zero defense. Earth has zero offense. Void can only survive by dealing damage. Every allocation is a tradeoff, and you get one point per level.

---

## Skill Tree

485 nodes across 15 regions. You don't browse a menu - you enter the Skill Sanctum, a 3D instance with glowing orbs floating in space connected by beams of colored light. Walk up to a node, press F to allocate. Free respec, anytime, no cost.

6 elemental arms, 8 hybrid arms connecting adjacent elements, 12 bridge paths, and build-defining keystones at the edges.

If you install Hexcode, the magic nodes in the tree are fully replaced with Hexcode-integrated versions automatically.

---

## Combat

Every hit passes through an 11-stage damage pipeline :

> **Base -> Flat -> Elemental -> Conversion -> % Physical -> % Elemental -> % More -> Conditionals -> Crit -> Defenses -> True Damage**

Nothing is hidden. `/too combat detail` shows the full breakdown per hit. If you understand the pipeline, you can optimize your build way beyond what intuition suggests.

**4 Ailments** - Burn (Fire DoT), Freeze (Water slow), Shock (Lightning damage amp), Poison (Void stacking DoT). Each scales with your stats.

**Avoidance** - Dodge Chance, Evasion vs Accuracy formula, active Shield Blocking, passive Block procs. Getting hit is optional if you build for it.

**Death Recap** - Every death shows exactly what killed you, how much damage, what type, from where. You learn what to fix.

---

## Gear

42 equipment types across 7 rarities (Common through Unique). Each piece rolls random Modifiers from a pool of 101 definitions, Prefixes and Suffixes. Quality ranges from 1 to 101, multiplying every stat on the item.

You don't find perfect gear. You **craft** it. 25 Stones across 7 categories let you reroll, enhance, remove, lock, and corrupt Gear Modifiers. A common sword with the right Stone investment can outperform a legendary drop with bad rolls.

**Loot Filter** - Configure exactly which items you see. Filter by Rarity, Type, Quality. Turn off what you don't need.

---

## Mob Scaling

5 mob classes : Passive, Minor, Hostile, Elite, Boss. Elites spawn dynamically in Realms. Each mob's stats are generated via Dirichlet distribution across 52 stat types, no two mobs feel the same. One Elite might be a tank that barely moves. Another might be a glass cannon that kills you in two hits. You learn to read them.

---

## Getting Started

1. Grab a weapon. Kill things near spawn. You'll get XP, gear drops, stone drops, and Realm Maps.
2. Open your stats page to allocate Attribute Points across the 6 Elements.
3. Find a Portal Device in the overworld - every one is an Ancient Gateway.
4. Use a Realm Map on it. Portal opens. Step through.
5. Kill everything before the timer runs out. Collect your loot.
6. Use Stones to improve your gear. Push harder maps.

---

## Installation

Place the JAR in your server's `mods/` directory. The asset pack is bundled.

Players just join - Hytale mods are server-side. No client setup.

## Documentation

Full wiki : https://wiki.hytalemodding.dev/en/docs/Larsonix_TrailOfOrbis

Also accessible in-game via the Voile documentation system.

---

## Bug Reports - Feature asking

- If it's related to a crash, please send Server logs & Client logs
- If it's a feature request or an optimisation idea I'm open to talking about anything and everything
- I know it doesn't feel proper yet, but I wanted all the core mechanics in and fully working without bugs before building anything around it
- Keep in mind this project is created with the goal of sticking to vanilla Hytale's Lore & Story

---

## TODO

A lot of things are still to come :
- Skills once we have Inventory access
- Progression system
- Dungeons
- Bosses
- Enhancing the Skill Tree
- Adding stats
- Balance fully done up to level 100 000 (1 000 currently)
- QOL and UX upgrades
- And a lot more...

---

## Credits

- **LadyPaladra** - All visual assets : textures, models. Also creator of [The Armory](https://www.curseforge.com/hytale/mods/the-armory)
- **tiptox** - Logo

**Code** : LGPL-3.0 | **Assets** : CC-BY-NC-SA-4.0

---
PASTE END
---

## Short Summary (~250 chars)

For the "Summary" field on CurseForge / ModTale / Modifold :

> RPG overhaul inspired by Path of Exile and Last Epoch. 6 elements, 485-node skill tree, procedural Realm dungeons, 101 gear modifiers, 25 crafting stones, and an 11-stage damage pipeline. No classes, your build is your choices.

---

## Gallery Upload Order

Upload all 16 screenshots to each platform's gallery in this order. Set #1 as the featured/primary image.

1. `SkilltreeScreenshot.png` - **Featured image.** 3D Skill Sanctum with colored nodes.
2. `Volcano.png` - Volcano Realm biome, lava, mobs fighting.
3. `ElementsAttributesUI.png` - 6 Elements allocation screen.
4. `EpicSwordTooltip.png` - Epic gear with rolled modifiers and requirements.
5. `Beach.png` - Beach Realm, ocean and islands.
6. `AncientGatewayUpgradeUI.png` - Gateway upgrade interface.
7. `Jungle.png` - Jungle Realm, dense vegetation.
8. `OffensiveStatsUI.png` - Full offensive stats breakdown.
9. `RaremapTooltip.png` - Rare Realm Map with modifiers.
10. `Caverns.png` - Cavern Realm, underground.
11. `Realm-Entrance-UI.png` - Entering a Realm.
12. `SkillTreeNodeHUD.png` - Allocating a Skill Tree node.
13. `LootFilterUI.png` - Loot filter configuration.
14. `StonesCreativeInventoryFullList.png` - All 25 Stones.
15. `HexcodeStaffRPGLegendaryTooltip.png` - Hexcode Legendary Staff with RPG integration.
16. `UncommonHelmetTooltip.png` - Uncommon helmet tooltip.
