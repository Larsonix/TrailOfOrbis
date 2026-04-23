---
name: Biomes
title: Realm Biomes
description: All realm biomes - mob pools, spawn weights, environmental hazards, and difficulty tiers
author: Larsonix
sort-index: 0
order: 101
published: true
---

# Realm Biomes

14 biome types define the environment of each Realm. 10 standard biomes, 3 high difficulty biomes with environmental hazards, and 1 utility biome.

| Biome | Hazards | High Difficulty | Theme |
|:------|:-------:|:---------------:|:------|
| [Forest](#forest) | No | No | Trork Tribal Territory |
| [Desert](#desert) | No | No | Scorched Ruins |
| [Tundra](#tundra) | No | No | Outlander Frontier |
| [Beach](#beach) | No | No | Pirate Cove |
| [Jungle](#jungle) | No | No | The Living Wilds |
| [Mountains](#mountains) | No | No | Goblin Stronghold |
| [Caverns](#caverns) | No | No | The Scarak Hive |
| [Frozen Crypts](#frozen-crypts) | No | No | Underground Ice Tomb |
| [Sand Tombs](#sand-tombs) | No | No | Underground Sandstone Pyramid |
| [Swamp](#swamp) | **Yes** | No | Fetid Marshlands |
| [**Volcano**](#volcano) | **Yes** | **Yes** | Infernal Wasteland |
| [**Void**](#void) | **Yes** | **Yes** | Eldritch Dimension |
| [**Corrupted**](#corrupted) | **Yes** | **Yes** | Dark Cult Territory |
| Skill Sanctum | No | No | Utility (no combat) |

---

## Biome Selection

When a Realm Map drops, it rolls a biome. The `randomNonCorrupted()` method excludes both **Corrupted** and **Skill Sanctum** from random selection. These biomes are obtained through other means.

Use **Alterverse Key** (Stone) to change a map's biome randomly. The reroll also uses `randomNonCorrupted()`, so you cannot roll into Corrupted or Skill Sanctum through this method.

---

## Mob Pools

Each biome has two pools : **regular** (standard enemies, weighted random selection) and **boss** (spawned at designated points or by chance).

Elite is **not** a separate pool. Any mob can roll elite status at spawn time. The elite roll happens independently of the mob pool selection. Base elite chance is 5%, scaling to 25% max with realm level.

---

## Forest

![Forest](https://raw.githubusercontent.com/Larsonix/TrailOfOrbis/main/Wiki/content/Larsonix_TrailOfOrbis/images/biomes/forest.png)

Trork Tribal Territory. Open clearings and temperate terrain.

| Mob | Weight |
|:----|-------:|
| Trork Warrior | 25 |
| Trork Brawler | 20 |
| Trork Guard | 20 |
| Trork Hunter | 20 |
| Trork Shaman | 10 |
| Trork Mauler | 5 |

**Boss :** Trork Chieftain

---

## Desert

![Desert](https://raw.githubusercontent.com/Larsonix/TrailOfOrbis/main/Wiki/content/Larsonix_TrailOfOrbis/images/biomes/desert.png)

Scorched Ruins. Fire undead and desert creatures.

| Mob | Weight |
|:----|-------:|
| Skeleton Burnt Knight | 22 |
| Scorpion | 18 |
| Skeleton Burnt Gunner | 18 |
| Skeleton Burnt Wizard | 15 |
| Cactee | 15 |
| Lizard Sand | 12 |

**Boss :** Skeleton Burnt Praetorian

---

## Tundra

![Tundra](https://raw.githubusercontent.com/Larsonix/TrailOfOrbis/main/Wiki/content/Larsonix_TrailOfOrbis/images/biomes/tundra.png)

Outlander Frontier. Ice-tribe raiders and arctic wildlife.

| Mob | Weight |
|:----|-------:|
| Outlander Marauder | 20 |
| Outlander Hunter | 18 |
| Outlander Berserker | 17 |
| Outlander Brute | 15 |
| Outlander Stalker | 12 |
| Bear Polar | 10 |
| Wolf White | 8 |

**Boss :** Yeti

---

## Beach

![Beach](https://raw.githubusercontent.com/Larsonix/TrailOfOrbis/main/Wiki/content/Larsonix_TrailOfOrbis/images/biomes/beach.png)

Pirate Cove. Undead pirates and coastal predators.

| Mob | Weight |
|:----|-------:|
| Skeleton Pirate Striker | 25 |
| Skeleton Pirate Gunner | 25 |
| Skeleton Pirate Captain | 15 |
| Crocodile | 15 |
| Snake Cobra | 10 |
| Scorpion | 10 |

**Boss :** Scarak Broodmother

---

## Jungle

![Jungle](https://raw.githubusercontent.com/Larsonix/TrailOfOrbis/main/Wiki/content/Larsonix_TrailOfOrbis/images/biomes/jungle.png)

The Living Wilds. Apex predators and dangerous beasts.

| Mob | Weight |
|:----|-------:|
| Spider | 18 |
| Crocodile | 15 |
| Wolf Black | 15 |
| Raptor Cave | 14 |
| Snapdragon | 10 |
| Snake Cobra | 10 |
| Fen Stalker | 8 |
| Toad Rhino | 5 |
| Snake Marsh | 5 |

**Boss :** Rex Cave

---

## Mountains

> [!NOTE]
> This biome's terrain is not yet finalized for WorldGenV2. Mob pool is configured and ready.

Goblin Stronghold. Mining operations and defenses.

| Mob | Weight |
|:----|-------:|
| Goblin Scrapper | 25 |
| Goblin Lobber | 20 |
| Goblin Ogre | 15 |
| Goblin Thief | 15 |
| Goblin Miner | 15 |
| Bear Grizzly | 10 |

**Boss :** Goblin Duke

---

## Caverns

![Caverns](https://raw.githubusercontent.com/Larsonix/TrailOfOrbis/main/Wiki/content/Larsonix_TrailOfOrbis/images/biomes/caverns.png)

The Scarak Hive. Insectoid colony infesting the crystal caves.

| Mob | Weight |
|:----|-------:|
| Scarak Fighter | 25 |
| Scarak Defender | 20 |
| Scarak Seeker | 20 |
| Scarak Louse | 20 |
| Scarak Fighter Royal Guard | 15 |

**Boss :** Scarak Broodmother

---

## Frozen Crypts

![Frozen Crypts](https://raw.githubusercontent.com/Larsonix/TrailOfOrbis/main/Wiki/content/Larsonix_TrailOfOrbis/images/biomes/frozenCrypts.png)

Underground Ice Tomb. Ancient frost warriors frozen in time. All 8 Frost Skeleton roles are exclusive to this biome.

| Mob | Weight |
|:----|-------:|
| Skeleton Frost Knight | 16 |
| Skeleton Frost Fighter | 14 |
| Skeleton Frost Soldier | 13 |
| Skeleton Frost Scout | 13 |
| Skeleton Frost Archer | 13 |
| Skeleton Frost Ranger | 11 |
| Skeleton Frost Mage | 10 |
| Skeleton Frost Archmage | 10 |

**Boss :** Golem Crystal Frost

---

## Sand Tombs

![Sand Tombs](https://raw.githubusercontent.com/Larsonix/TrailOfOrbis/main/Wiki/content/Larsonix_TrailOfOrbis/images/biomes/sandTombs.png)

Underground Sandstone Pyramid. Ancient tomb guardians. All 8 Sand Skeleton roles are exclusive to this biome.

| Mob | Weight |
|:----|-------:|
| Skeleton Sand Archer | 15 |
| Skeleton Sand Guard | 14 |
| Skeleton Sand Assassin | 13 |
| Skeleton Sand Ranger | 13 |
| Skeleton Sand Scout | 12 |
| Skeleton Sand Soldier | 12 |
| Skeleton Sand Mage | 11 |
| Skeleton Sand Archmage | 10 |

**Boss :** Golem Crystal Sand

---

## Swamp

![Swamp](https://raw.githubusercontent.com/Larsonix/TrailOfOrbis/main/Wiki/content/Larsonix_TrailOfOrbis/images/biomes/swamp.png)

Fetid Marshlands. Undead and swamp horrors. **Environmental hazards.**

| Mob | Weight |
|:----|-------:|
| Fen Stalker | 20 |
| Ghoul | 20 |
| Wraith | 20 |
| Crocodile | 15 |
| Snake Marsh | 15 |
| Toad Rhino | 10 |

**Boss :** Wraith Lantern

---

## Volcano

![Volcano](https://raw.githubusercontent.com/Larsonix/TrailOfOrbis/main/Wiki/content/Larsonix_TrailOfOrbis/images/biomes/volcano.png)

Infernal Wasteland. Fire undead, elemental creatures, and incandescent risen. **Environmental hazards. High difficulty.**

| Mob | Weight |
|:----|-------:|
| Skeleton Burnt Knight | 16 |
| Skeleton Burnt Gunner | 14 |
| Golem Firesteel | 12 |
| Skeleton Burnt Wizard | 12 |
| Skeleton Incandescent Fighter | 12 |
| Emberwulf | 12 |
| Skeleton Incandescent Mage | 8 |
| Toad Rhino Magma | 8 |
| Slug Magma | 6 |

**Boss :** Golem Crystal Flame

---

## Void

> [!NOTE]
> This biome's terrain is not yet finalized for WorldGenV2. Mob pool is configured and ready.

Eldritch Dimension. Pure void corruption. **Environmental hazards. High difficulty.**

| Mob | Weight |
|:----|-------:|
| Crawler Void | 25 |
| Spectre Void | 25 |
| Eye Void | 15 |
| Spawn Void | 15 |
| Golem Guardian Void | 10 |
| Larva Void | 10 |

**Boss :** Golem Guardian Void

---

## Corrupted

> [!NOTE]
> This biome's terrain is not yet finalized for WorldGenV2. Mob pool is configured and ready.

Dark Cult Territory. Dark magic users and transformed creatures. **Environmental hazards. High difficulty.**

| Mob | Weight |
|:----|-------:|
| Werewolf | 20 |
| Outlander Sorcerer | 20 |
| Outlander Cultist | 20 |
| Shadow Knight | 15 |
| Outlander Priest | 15 |
| Ghoul | 10 |

**Boss :** Risen Knight

---

## Related Pages

- [Realm Sizes](realm-sizes) - Arena radius, mob count, and time limits per size
- [Realm Modifiers](realm-modifiers) - Difficulty and reward modifiers on maps
- [Map Crafting](map-crafting) - Use Alterverse Key to change biomes
- [Mob System](mob-system) - How mobs are classified and scaled inside Realms
