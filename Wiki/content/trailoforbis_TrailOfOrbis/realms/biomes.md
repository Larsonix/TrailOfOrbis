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

<div class="too-biome-table">
  <div class="too-biome-header">
    <span>Biome</span>
    <span>Hazards</span>
    <span>High Difficulty</span>
    <span>Theme</span>
  </div>
  <a class="too-biome-row" href="#forest">
    <div class="too-biome-cell">
      <img class="too-biome-icon" src="https://wiki.hytalemodding.dev/storage/mods/019da288-1a74-71ad-bb01-6e0ae3d015c6/files/aeb8933f-6886-4c83-8c93-4f37abd6b777" alt="Forest">
      <span class="too-biome-name">Forest</span>
    </div>
    <span>No</span>
    <span>No</span>
    <span>Trork Tribal Territory</span>
  </a>
  <a class="too-biome-row" href="#desert">
    <div class="too-biome-cell">
      <img class="too-biome-icon" src="https://wiki.hytalemodding.dev/storage/mods/019da288-1a74-71ad-bb01-6e0ae3d015c6/files/03df9491-348c-46a5-a9ba-c0a4340bff01" alt="Desert">
      <span class="too-biome-name">Desert</span>
    </div>
    <span>No</span>
    <span>No</span>
    <span>Scorched Ruins</span>
  </a>
  <a class="too-biome-row" href="#tundra">
    <div class="too-biome-cell">
      <img class="too-biome-icon" src="https://wiki.hytalemodding.dev/storage/mods/019da288-1a74-71ad-bb01-6e0ae3d015c6/files/ecff1abb-8477-41fc-9662-480ffe0445fb" alt="Tundra">
      <span class="too-biome-name">Tundra</span>
    </div>
    <span>No</span>
    <span>No</span>
    <span>Outlander Frontier</span>
  </a>
  <a class="too-biome-row" href="#beach">
    <div class="too-biome-cell">
      <img class="too-biome-icon" src="https://wiki.hytalemodding.dev/storage/mods/019da288-1a74-71ad-bb01-6e0ae3d015c6/files/44478b88-37f8-4de9-b9bf-2b1be459da5e" alt="Beach">
      <span class="too-biome-name">Beach</span>
    </div>
    <span>No</span>
    <span>No</span>
    <span>Pirate Cove</span>
  </a>
  <a class="too-biome-row" href="#jungle">
    <div class="too-biome-cell">
      <span class="too-biome-name">Jungle</span>
    </div>
    <span>No</span>
    <span>No</span>
    <span>The Living Wilds</span>
  </a>
  <a class="too-biome-row" href="#mountains">
    <div class="too-biome-cell">
      <span class="too-biome-name">Mountains</span>
    </div>
    <span>No</span>
    <span>No</span>
    <span>Goblin Stronghold</span>
  </a>
  <a class="too-biome-row" href="#caverns">
    <div class="too-biome-cell">
      <img class="too-biome-icon" src="https://wiki.hytalemodding.dev/storage/mods/019da288-1a74-71ad-bb01-6e0ae3d015c6/files/3ebd9437-eac5-4d4f-b219-b916790a5c45" alt="Caverns">
      <span class="too-biome-name">Caverns</span>
    </div>
    <span>No</span>
    <span>No</span>
    <span>The Scarak Hive</span>
  </a>
  <a class="too-biome-row" href="#frozen-crypts">
    <div class="too-biome-cell">
      <img class="too-biome-icon" src="https://wiki.hytalemodding.dev/storage/mods/019da288-1a74-71ad-bb01-6e0ae3d015c6/files/dd23b3a8-1f6c-415d-bdec-63f4cb58210b" alt="Frozen Crypts">
      <span class="too-biome-name">Frozen Crypts</span>
    </div>
    <span>No</span>
    <span>No</span>
    <span>Underground Ice Tomb</span>
  </a>
  <a class="too-biome-row" href="#sand-tombs">
    <div class="too-biome-cell">
      <img class="too-biome-icon" src="https://wiki.hytalemodding.dev/storage/mods/019da288-1a74-71ad-bb01-6e0ae3d015c6/files/9e95e90f-52e2-4161-9715-0dce31b38f45" alt="Sand Tombs">
      <span class="too-biome-name">Sand Tombs</span>
    </div>
    <span>No</span>
    <span>No</span>
    <span>Underground Sandstone Pyramid</span>
  </a>
  <a class="too-biome-row too-biome-hazard" href="#swamp">
    <div class="too-biome-cell">
      <img class="too-biome-icon" src="https://wiki.hytalemodding.dev/storage/mods/019da288-1a74-71ad-bb01-6e0ae3d015c6/files/88c4deaf-2bc5-4833-a7c5-6835b3399669" alt="Swamp">
      <span class="too-biome-name">Swamp</span>
    </div>
    <span><strong>Yes</strong></span>
    <span>No</span>
    <span>Fetid Marshlands</span>
  </a>
  <a class="too-biome-row too-biome-high" href="#volcano">
    <div class="too-biome-cell">
      <img class="too-biome-icon" src="https://wiki.hytalemodding.dev/storage/mods/019da288-1a74-71ad-bb01-6e0ae3d015c6/files/0830e55b-08b3-44f5-9e3d-6bd8e08d8484" alt="Volcano">
      <span class="too-biome-name"><strong>Volcano</strong></span>
    </div>
    <span><strong>Yes</strong></span>
    <span><strong>Yes</strong></span>
    <span>Infernal Wasteland</span>
  </a>
  <a class="too-biome-row too-biome-high" href="#void">
    <div class="too-biome-cell">
      <span class="too-biome-name"><strong>Void</strong></span>
    </div>
    <span><strong>Yes</strong></span>
    <span><strong>Yes</strong></span>
    <span>Eldritch Dimension</span>
  </a>
  <a class="too-biome-row too-biome-high" href="#corrupted">
    <div class="too-biome-cell">
      <span class="too-biome-name"><strong>Corrupted</strong></span>
    </div>
    <span><strong>Yes</strong></span>
    <span><strong>Yes</strong></span>
    <span>Dark Cult Territory</span>
  </a>
  <div class="too-biome-row too-biome-utility">
    <div class="too-biome-cell">
      <span class="too-biome-name">Skill Sanctum</span>
    </div>
    <span>No</span>
    <span>No</span>
    <span>Utility (no combat)</span>
  </div>
</div>

---

## Biome Selection

When a Realm Map drops, it rolls a biome. The `randomNonCorrupted()` method excludes both **Corrupted** and **Skill Sanctum** from random selection. These biomes are obtained through other means.

Use **Alterverse Key** (Stone) to change a map's biome randomly. The reroll also uses `randomNonCorrupted()`, so you cannot roll into Corrupted or Skill Sanctum through this method.

---

## Mob Pools

Each biome has two pools : **regular** (standard enemies, weighted random selection) and **boss** (spawned at designated points or by chance).

Elite is **not** a separate pool. Any mob can roll elite status at spawn time. The elite roll happens independently of the mob pool selection. Base elite chance is **5%**, scaling to **25%** max with realm level.

---

## Forest

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
