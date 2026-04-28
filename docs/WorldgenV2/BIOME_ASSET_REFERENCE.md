# Biome Asset Reference — Complete Game Data

Everything available for biome creation. Full NPC roster, prefab categories, block materials, and biome assignments. For raw data searches, use Grep on the `db/` databases (see bottom of this file).

**Last updated:** April 20, 2026

---

## Table of Contents

- [NPC Roster (All Spawnable)](#npc-roster-all-spawnable)
- [NPC Roster (Neutral — Cannot Spawn)](#npc-roster-neutral--cannot-spawn)
- [NPC Roster (Passive/Wildlife)](#npc-roster-passivewildlife)
- [Current Biome Mob Assignments](#current-biome-mob-assignments)
- [Prefab Categories (WorldGen Safe)](#prefab-categories-worldgen-safe)
- [Prefab Categories (Runtime Only)](#prefab-categories-runtime-only)
- [NPC Faction Structures](#npc-faction-structures)
- [Terrain Materials](#terrain-materials)
- [Ground Cover Blocks](#ground-cover-blocks)
- [Environment Types](#environment-types)
- [Raw Database Search Guide](#raw-database-search-guide)

---

## NPC Roster (All Spawnable)

These NPCs can be spawned via `NPCPlugin.spawnEntity()`. Categories: `Creature/*`, `Intelligent/Aggressive/*`, `Undead/*`, `Elemental/*`, `Void/*`, `Boss/*`.

### Hostile Factions (Intelligent/Aggressive)

#### Trork — Forest faction (12 roles)

| Role ID | Notes | Biome |
|---------|-------|-------|
| `Trork_Brawler` | Heavy melee brute | Forest |
| `Trork_Warrior` | Balanced melee fighter | Forest |
| `Trork_Guard` | Standard melee soldier | Forest |
| `Trork_Hunter` | Ranged bow attacks | Forest |
| `Trork_Shaman` | Magic/heals support | Forest |
| `Trork_Mauler` | Slow, hard-hitting | Forest |
| `Trork_Sentry` | Perimeter guard | — |
| `Trork_Doctor_Witch` | Witch doctor | — |
| `Trork_Unarmed` | Unarmed variant | — |
| `Trork_Chieftain` | **BOSS** — faction leader | Forest |
| `Wolf_Trork_Hunter` | Wolf companion (Hunter) | — |
| `Wolf_Trork_Shaman` | Wolf companion (Shaman) | — |

#### Goblin — Mountains faction (11 roles)

| Role ID | Notes | Biome |
|---------|-------|-------|
| `Goblin_Scrapper` | Basic melee fighter | Mountains |
| `Goblin_Lobber` | Ranged projectiles | Mountains |
| `Goblin_Miner` | Pickaxe fighter | Mountains |
| `Goblin_Thief` | Stealth attacker | Mountains |
| `Goblin_Scavenger` | Scavenger (base) | — |
| `Goblin_Scavenger_Battleaxe` | Battleaxe variant | — |
| `Goblin_Scavenger_Sword` | Sword variant | — |
| `Goblin_Ogre` | Heavy brute | Mountains |
| `Goblin_Hermit` | Hermit variant | — |
| `Goblin_Duke` | **BOSS** — multi-phase (3 phases) | Mountains |
| `Edible_Goblin_Scrapper` | Edible variant | — |

#### Outlander — Tundra faction (11 roles)

| Role ID | Notes | Biome |
|---------|-------|-------|
| `Outlander_Marauder` | Melee raider | Tundra |
| `Outlander_Hunter` | Ranged hunter | — |
| `Outlander_Stalker` | Stealth tracker | — |
| `Outlander_Berserker` | Rage melee DPS | — |
| `Outlander_Brute` | Heavy tank | — |
| `Outlander_Cultist` | Dark magic | Corrupted |
| `Outlander_Priest` | Dark healer | Corrupted |
| `Outlander_Sorcerer` | Dark ranged magic | Corrupted |
| `Outlander_Peon` | Basic worker | — |
| `Wolf_Outlander_Priest` | Wolf companion | — |
| `Wolf_Outlander_Sorcerer` | Wolf companion | — |

#### Scarak — Reserved for future Scarak biome (7 roles)

| Role ID | Notes | Biome |
|---------|-------|-------|
| `Scarak_Fighter` | Melee insectoid | **Reserved** |
| `Scarak_Defender` | Tank insectoid | **Reserved** |
| `Scarak_Seeker` | Scout insectoid | **Reserved** |
| `Scarak_Louse` | Swarm insectoid | **Reserved** |
| `Scarak_Fighter_Royal_Guard` | Elite guard | **Reserved** |
| `Scarak_Broodmother` | **BOSS** — queen | Beach (temp) |
| `Dungeon_Scarak_*` (9 variants) | Dungeon-specific | — |

#### Other Aggressive

| Role ID | Category | Notes | Biome |
|---------|----------|-------|-------|
| `Hedera` | Intelligent/Aggressive | Giant plant boss — spawnEntity returns null (possible prefab conflict) | **DEAD** |

### Undead (145 roles)

#### Generic Undead (11 roles)

| Role ID | Notes | Biome |
|---------|-------|-------|
| `Ghoul` | Fast melee undead | Swamp |
| `Wraith` | Ghostly ranged | Swamp |
| `Wraith_Lantern` | **BOSS** — spectral lord | Swamp |
| `Shadow_Knight` | Dark warrior | Corrupted |
| `Werewolf` | Transformed beast | Corrupted |
| `Risen_Knight` | Armored undead | — |
| `Risen_Gunner` | Ranged undead | — |
| `Hound_Bleached` | Undead dog | — |
| `Chicken_Undead` | Undead chicken | — |
| `Cow_Undead` | Undead cow | — |
| `Pig_Undead` | Undead pig | — |

#### Skeleton — Base (12 roles, each has base + Patrol + Wander variants)

| Role ID | Combat Style |
|---------|-------------|
| `Skeleton_Fighter` | Melee |
| `Skeleton_Knight` | Heavy melee |
| `Skeleton_Soldier` | Standard melee |
| `Skeleton_Scout` | Fast melee |
| `Skeleton_Archer` | Ranged bow |
| `Skeleton_Ranger` | Ranged |
| `Skeleton_Mage` | Magic |
| `Skeleton_Archmage` | Heavy magic |
| `Skeleton` | Basic skeleton |

#### Skeleton_Frost — Tundra themed (12 roles × 3 variants)

| Role ID | Notes | Biome |
|---------|-------|-------|
| `Skeleton_Frost_Knight` | Ice melee | Tundra |
| `Skeleton_Frost_Scout` | Ice fast | Tundra |
| `Skeleton_Frost_Archer` | Ice ranged | Tundra |
| `Skeleton_Frost_Mage` | Ice magic | Tundra |
| `Skeleton_Frost_Fighter` | Ice melee | — |
| `Skeleton_Frost_Soldier` | Ice standard | — |
| `Skeleton_Frost_Ranger` | Ice ranged | — |
| `Skeleton_Frost_Archmage` | Ice heavy magic | — |

#### Skeleton_Burnt — Desert/Volcano themed (10 roles × 3 variants)

| Role ID | Notes | Biome |
|---------|-------|-------|
| `Skeleton_Burnt_Knight` | Fire melee | Desert, Volcano |
| `Skeleton_Burnt_Gunner` | Fire ranged | Desert, Volcano |
| `Skeleton_Burnt_Wizard` | Fire magic | Desert, Volcano |
| `Skeleton_Burnt_Praetorian` | **BOSS** — summoner | Desert |
| `Skeleton_Burnt_Archer` | Fire ranged | — |
| `Skeleton_Burnt_Alchemist` | Fire alchemy | — |
| `Skeleton_Burnt_Lancer` | Fire spear | — |
| `Skeleton_Burnt_Soldier` | Fire standard | — |

#### Skeleton_Sand — Desert themed (8 roles × 3 variants)

| Role ID | Notes | Biome |
|---------|-------|-------|
| `Skeleton_Sand_Archer` | Desert ranged | Desert |
| `Skeleton_Sand_Mage` | Desert magic | Desert |
| `Skeleton_Sand_Guard` | Desert melee | — |
| `Skeleton_Sand_Assassin` | Desert stealth | — |
| `Skeleton_Sand_Ranger` | Desert ranged | — |
| `Skeleton_Sand_Scout` | Desert fast | — |
| `Skeleton_Sand_Soldier` | Desert standard | — |
| `Skeleton_Sand_Archmage` | Desert heavy magic | — |

#### Skeleton_Pirate — Beach themed (3 roles × 3 variants)

| Role ID | Notes | Biome |
|---------|-------|-------|
| `Skeleton_Pirate_Captain` | Undead captain | Beach |
| `Skeleton_Pirate_Striker` | Cutlass melee | Beach |
| `Skeleton_Pirate_Gunner` | Pistol/rifle ranged | Beach |

#### Skeleton_Incandescent (4 roles × 3 variants)

| Role ID | Notes |
|---------|-------|
| `Skeleton_Incandescent_Fighter` | Incandescent melee |
| `Skeleton_Incandescent_Footman` | Incandescent standard |
| `Skeleton_Incandescent_Mage` | Incandescent magic |
| `Skeleton_Incandescent_Head` | Head variant |

#### Zombie (7 roles)

| Role ID | Notes | Biome |
|---------|-------|-------|
| `Zombie` | Standard zombie | — |
| `Zombie_Burnt` | Fire zombie | — |
| `Zombie_Frost` | Ice zombie | — |
| `Zombie_Sand` | Sand zombie | — |
| `Zombie_Aberrant` | Mutated zombie | — |
| `Zombie_Aberrant_Big` | Large mutant | — |
| `Zombie_Aberrant_Small` | Small mutant | — |

### Creatures (120 roles)

#### Mammals — Predators (16 roles)

| Role ID | Subcategory | Notes | Biome |
|---------|-------------|-------|-------|
| `Bear_Grizzly` | Mammal | Heavy tank | Mountains |
| `Bear_Polar` | Mammal | Arctic tank | Tundra |
| `Wolf_Black` | Mammal | Dark pack hunter | **Jungle** |
| `Wolf_White` | Mammal | Arctic pack | Tundra |
| `Hyena` | Mammal | Pack scavenger | — |
| `Fox` | Mammal | Small predator | — |
| `Leopard_Snow` | Mammal | Arctic stalker | — |
| `Tiger_Sabertooth` | Mammal | Prehistoric predator | — |
| `Deer_Stag` | Mammal | Defensive (antlers) | — |
| `Deer_Doe` | Mammal | Prey | — |
| `Moose_Bull` | Mammal | Large, aggressive | — |
| `Moose_Cow` | Mammal | Prey | — |
| `Antelope` | Mammal | Fast prey | — |
| `Armadillo` | Mammal | Armored | — |
| `Mosshorn` | Mammal | Large herbivore | — |
| `Mosshorn_Plain` | Mammal | Plains variant | — |

#### Reptiles (7 roles)

| Role ID | Notes | Biome |
|---------|-------|-------|
| `Crocodile` | Ambush predator | **Jungle**, Swamp, Beach |
| `Raptor_Cave` | Pack dinosaur | **Jungle**, Caverns |
| `Rex_Cave` | **BOSS** — apex dinosaur | **Jungle** |
| `Toad_Rhino` | Massive amphibian | **Jungle**, Swamp |
| `Toad_Rhino_Magma` | Lava toad | Volcano |
| `Lizard_Sand` | Desert reptile | Desert |
| `Tortoise` | Slow, armored | — |

#### Vermin (12 roles)

| Role ID | Notes | Biome |
|---------|-------|-------|
| `Spider` | Web trapper | **Jungle**, Desert |
| `Spider_Cave` | Cave variant | Caverns |
| `Snake_Cobra` | Venomous | **Jungle**, Beach |
| `Snake_Marsh` | Swamp venom | **Jungle**, Swamp |
| `Snake_Rattle` | Rattlesnake | — |
| `Scorpion` | Desert predator | Desert, Beach |
| `Rat` | Basic vermin | Caverns |
| `Molerat` | Burrowing vermin | Caverns |
| `Slug_Magma` | Fire slug | Volcano |
| `Snail_Frost` | Ice snail | — |
| `Snail_Magma` | Fire snail | — |
| `Larva_Silk` | Silk larva | — |

#### Mythic (8 roles)

| Role ID | Notes | Biome |
|---------|-------|-------|
| `Snapdragon` | Massive plant beast | **Jungle** |
| `Fen_Stalker` | Swamp/jungle horror | **Jungle**, Swamp |
| `Yeti` | **BOSS** — ice giant | Tundra |
| `Emberwulf` | Fire wolf | Volcano |
| `Cactee` | Animated cactus | Desert |
| `Hatworm` | Worm creature | — |
| `Trillodon` | Prehistoric | — |
| `Spark_Living` | Living spark | — |

#### Critters (7 roles — mostly passive)

| Role ID | Notes |
|---------|-------|
| `Frog_Blue`, `Frog_Green`, `Frog_Orange` | Small frogs |
| `Gecko` | Small lizard |
| `Mouse` | Tiny rodent |
| `Meerkat` | Desert critter |
| `Squirrel` | Forest critter |

#### Livestock (22 roles + 22 tamed variants)

Bison, Boar, Bunny, Camel, Chicken, Chicken_Desert, Cow, Goat, Horse, Mouflon, Pig, Pig_Wild, Rabbit, Ram, Sheep, Skrill, Turkey, Warthog — each with a baby/calf variant and Tamed_ version.

### Elemental (12 roles)

| Role ID | Subcategory | Notes | Biome |
|---------|-------------|-------|-------|
| `Golem_Crystal_Earth` | Golem | Earth elemental | Caverns |
| `Golem_Crystal_Flame` | Golem | **BOSS** — fire elemental | Volcano |
| `Golem_Crystal_Frost` | Golem | Ice elemental | — |
| `Golem_Crystal_Sand` | Golem | Sand elemental | — |
| `Golem_Crystal_Thunder` | Golem | Thunder elemental | — |
| `Golem_Firesteel` | Golem | Molten guardian | Volcano |
| `Golem_Guardian_Void` | Golem | **BOSS** — void colossus | Void |
| `Spirit_Ember` | Spirit | Fire spirit | — |
| `Spirit_Frost` | Spirit | Ice spirit | — |
| `Spirit_Root` | Spirit | Nature spirit | — |
| `Spirit_Thunder` | Spirit | Thunder spirit | — |

### Void (5 roles)

| Role ID | Notes | Biome |
|---------|-------|-------|
| `Crawler_Void` | Void beast | Void |
| `Spectre_Void` | Ghost attacker | Void |
| `Eye_Void` | Floating eye | Void |
| `Spawn_Void` | Small void spawn | Void |
| `Larva_Void` | Void larva | Void |

### Bosses (2 roles)

| Role ID | Notes | Biome |
|---------|-------|-------|
| `Dragon_Fire` | Fire dragon — **confirmed dead** (no attacks/animations) | **DEAD** |
| `Dragon_Frost` | Ice dragon — **confirmed dead** (no assets) | **DEAD** |

### Aquatic (30 roles)

Freshwater: Bluegill, Catfish, Frostgill, Minnow, Pike, Piranha, Piranha_Black, Salmon, Snapjaw, Trout_Rainbow
Marine: Clownfish, Crab, Jellyfish (Blue/Cyan/Green/Red/Yellow/Man_Of_War), Lobster, Pufferfish, Tang (Blue/Chevron/Lemon_Peel/Sailfin)
Abyssal: Eel_Moray, Shark_Hammerhead, Shellfish_Lava, Trilobite, Trilobite_Black, Whale_Humpback

### Avian (20 roles)

Aerial: Bat, Bat_Ice, Bluebird, Crow, Finch_Green, Flamingo, Owl_Brown, Owl_Snow, Parrot, Penguin, Raven, Sparrow, Woodpecker
Fowl: Duck, Pigeon
Raptor: Archaeopteryx, Hawk, Pterodactyl, Tetrabird, Vulture

---

## NPC Roster (Neutral — Cannot Spawn)

**`NPCPlugin.spawnEntity()` returns null for ALL of these.** Hard engine limitation. Structures work via `PrefabUtil.paste()`.

| Role ID | Category | Notes |
|---------|----------|-------|
| `Feran_Burrower` | Intelligent/Neutral/Feran | Ambush fighter |
| `Feran_Civilian` | Intelligent/Neutral/Feran | Defensive |
| `Feran_Cub` | Intelligent/Neutral/Feran | Juvenile |
| `Feran_Longtooth` | Intelligent/Neutral/Feran | Melee + ranged |
| `Feran_Sharptooth` | Intelligent/Neutral/Feran | Elite fighter |
| `Feran_Windwalker` | Intelligent/Neutral/Feran | Wind attacks |
| `Bramblekin` | Intelligent/Neutral | Vine creature |
| `Bramblekin_Shaman` | Intelligent/Neutral | Plant druid |
| `Kweebec_Elder` | Intelligent/Neutral/Kweebec | Elder NPC |
| `Kweebec_Merchant` | Intelligent/Neutral/Kweebec | Merchant |
| `Kweebec_Prisoner` | Intelligent/Neutral/Kweebec | Prisoner |
| `Kweebec_Razorleaf` | Intelligent/Neutral/Kweebec | Hostile-looking but neutral |
| `Kweebec_Rootling` | Intelligent/Neutral/Kweebec | Small plant |
| `Kweebec_Sapling` | Intelligent/Neutral/Kweebec | Young plant |
| `Kweebec_Seedling` | Intelligent/Neutral/Kweebec | Tiny plant |
| `Kweebec_Sproutling` | Intelligent/Neutral/Kweebec | Sprout |
| `Tuluk_Fisherman` | Intelligent/Neutral/Tuluk | Fisherman NPC |

### Passive NPCs

| Role ID | Category | Notes |
|---------|----------|-------|
| `Klops_Gentleman` | Intelligent/Passive | Friendly NPC |
| `Klops_Merchant` | Intelligent/Passive | Shop NPC |
| `Klops_Miner` | Intelligent/Passive | Mining NPC |
| `Quest_Master` | Intelligent/Passive | Quest giver |
| `Slothian` | Intelligent/Passive | Beach NPC |
| `Temple_*` (30+ roles) | Intelligent/Passive/Temple | Temple-specific passive versions of Feran, Kweebec, wildlife |
| `Horse_Skeleton` | Undead | Skeleton mount |
| `Horse_Skeleton_Armored` | Undead | Armored skeleton mount |

---

## Current Biome Mob Assignments

| Biome | Regular Mobs | Boss | Theme |
|-------|-------------|------|-------|
| **Forest** | Trork_Brawler, Warrior, Guard, Hunter, Shaman, Mauler | Trork_Chieftain | Trork raiders |
| **Desert** | Skeleton_Burnt_Knight/Gunner/Wizard, Skeleton_Sand_Archer/Mage, Scorpion, Lizard_Sand, Cactee | Skeleton_Burnt_Praetorian | Fire undead + desert creatures |
| **Volcano** | Golem_Firesteel, Skeleton_Burnt_Knight/Gunner/Wizard, Emberwulf, Toad_Rhino_Magma, Slug_Magma | Golem_Crystal_Flame | Fire elementals |
| **Tundra** | Bear_Polar, Skeleton_Frost_Knight/Scout/Archer/Mage, Wolf_White | Yeti | Ice undead + arctic |
| **Jungle** | Spider, Crocodile, Wolf_Black, Raptor_Cave, Snapdragon, Snake_Cobra, Fen_Stalker, Toad_Rhino, Snake_Marsh | Rex_Cave | Predators + dinos |
| **Swamp** | Fen_Stalker, Ghoul, Wraith, Crocodile, Snake_Marsh, Toad_Rhino | Wraith_Lantern | Undead + swamp |
| **Mountains** | Goblin_Ogre, Scrapper, Lobber, Thief, Miner, Bear_Grizzly | Goblin_Duke | Goblin miners |
| **Beach** | Skeleton_Pirate_Captain/Striker/Gunner, Crocodile, Snake_Cobra, Scorpion | Scarak_Broodmother | Pirates |
| **Caverns** | Golem_Crystal_Earth, Spider_Cave, Raptor_Cave, Goblin_Miner, Molerat, Rat | Rex_Cave | Underground |
| **Void** | Golem_Guardian_Void, Crawler_Void, Spectre_Void, Eye_Void, Spawn_Void, Larva_Void | Golem_Guardian_Void | Void creatures |
| **Corrupted** | Shadow_Knight, Werewolf, Outlander_Sorcerer/Cultist/Priest, Ghoul | Risen_Knight | Dark cult |

### Unassigned Mobs (available for future biomes)

| Role ID | Type | Potential Biome |
|---------|------|----------------|
| `Hyena` | Pack predator | Savannah/Plains |
| `Fox` | Small predator | Any temperate |
| `Leopard_Snow` | Arctic stalker | Tundra alt |
| `Tiger_Sabertooth` | Prehistoric predator | Jungle/Plains |
| `Snake_Rattle` | Rattlesnake | Desert alt |
| `Tortoise` | Slow armored | Beach/Desert |
| `Hatworm` | Worm creature | Caverns/Corrupted |
| `Trillodon` | Prehistoric | Caverns/Jungle |
| `Spark_Living` | Living spark | Void/Corrupted |
| `Snail_Frost` | Ice snail | Tundra alt |
| `Larva_Silk` | Silk larva | Jungle/Caverns |
| `Golem_Crystal_Frost` | Ice golem | Tundra alt |
| `Golem_Crystal_Sand` | Sand golem | Desert alt |
| `Golem_Crystal_Thunder` | Thunder golem | Mountains alt |
| `Spirit_Ember/Frost/Root/Thunder` | Elemental spirits | Various |
| `Skeleton_Incandescent_*` | Incandescent undead | New biome? |
| `Zombie_Aberrant_*` | Mutant zombies | Corrupted alt |
| `Outlander_Hunter/Stalker/Berserker/Brute` | Outlander variants | Not used in any biome |

---

## Prefab Categories (WorldGen Safe)

These categories contain NO PrefabSpawnerBlocks and are proven safe for WorldGen V2 props.

### Trees (27+ types, all stages safe)

`Trees/Ash`, `Trees/Aspen`, `Trees/Bamboo`, `Trees/Banyan`, `Trees/Beech`, `Trees/Birch`, `Trees/Boab`, `Trees/Burnt`, `Trees/Camphor`, `Trees/Cedar`, `Trees/Dry`, `Trees/Fir`, `Trees/Fir_Snow`, `Trees/Jungle`, `Trees/Jungle_Crystal`, `Trees/Jungle_Ferns`, `Trees/Jungle_Island1-2`, `Trees/Logs/*/Moss`, `Trees/Maple`, `Trees/Oak`, `Trees/Oak_Moss`, `Trees/Palm`, `Trees/Palm_Green`, `Trees/Petrified`, `Trees/Poisoned`, `Trees/Willow`, `Trees/Wisteria`

**Stumps:** `Trees/Oak_Stumps`, `Trees/Maple_Stumps`, `Trees/Autumn_Stumps`

### Plants (all safe)

`Plants/Bush/Arid`, `Arid_Red`, `Brambles`, `Cliff`, `Dead_Lavathorn`, `Green`, `Jungle`, `Lush`, `Winter`
`Plants/Cacti/Flat/Stage_0`, `Full/Stage_1-3`
`Plants/Driftwood/Cedar`, `Dry`
`Plants/Jungle/Ferns/Large`, `Small`, `Island`
`Plants/Jungle/Flowers/Auburn`, `Blue`, `Orange`, `Pink`, `Purple`, `Red`
`Plants/Jungle/Coral/Glow`
`Plants/Mushroom_Large/Green`, `Purple`, `Yellow` (`Stage_1`, `Stage_3`)
`Plants/Mushroom_Rings`
`Plants/Twisted_Wood/Ash`, `Fire`, `Poisoned`
`Plants/Vines/Green`, `Green_Hanging`

### Rock Formations (all safe)

**Rocks:** Stone, Grass, Sandstone, Sandstone/Red, Sandstone/White, Volcanic, Volcanic/Spiked, Basalt, Basalt/Hexagon, Basalt/Snowy, Basalt/Tundra, Shale, Shale/Snowy, Shale/Spikey, Slate, Slate/Spikes, Quartzite, Quartzite/Moss_Large, Quartzite/Moss_Small, Calcite, Chalk, Frozenstone, Frozenstone/Snowy, Jungle — all with Small/Large variants

**Arches:** Aspen, Birch, Cedar, Desert (9), Desert_Red (9), Fir, Flower (10), Forest (20), Gorge, Gully, Hedera, Redwood, Sandstone, Savannah (5), Scrub, Smooth, Snowy, Swamp, Swamp_Poisoned, Tundra

**Pillars:** Rock_Stone/Ash (15), Rock_Stone/Plains (15), Rock_Stone/Oak (19), Shale/Bare/Small|Medium|Large, Snowy, Jungle (3)

**Stalactites:** Basalt/Floor, Basalt/Ceiling, Basalt_Crystal_Red/Ceiling

**Ice:** Ice_Formations/Icebergs

**Geodes:** Blue, Cyan, Green, Pink, Purple, White

### NPC Individual Structures (WorldGen safe — no PrefabSpawnerBlocks)

**Trork:** Tent, Bonfire, Fireplace, Trap, Warning, Warehouse, Burrow, Watchtower T1-3, Store T2-3, Misc/Large, Resource/Lumber/*, Resource/Rock/Stone
**Outlander:** Houses T0-3, Forts T1-3, Towers T1-3, Gates T2-3, Spikes, Totems, Braziers, Misc
**Feran:** Corners T1, Straight/Normal T1, Straight/Entrances T1, Chieftain T1-3, Hut T2, Wall T2, Entrance T2, Base T3, Huts T3, Walls T3
**Yeti:** Camps
**Slothian:** Camps, Houses

---

## Prefab Categories (Runtime Only — RealmStructurePlacer)

These contain PrefabSpawnerBlocks. Will crash WorldGen V2 → void world. Use `PrefabUtil.paste()` at runtime only.

**Monuments/Incidental:** Sandstone/*, Shale/Ruins/*, Shipwrecks/*, Quartzite/Ruins, Slothian/Land/*, Slothian/Biome/*, Treasure_Rooms/*
**Monuments/Encounter:** Zone2/Tier1/Outpost, Zone2/Tier2/Outpost
**Monuments/Unique:** Temple/Portal/*, Outlander_Temple/*, Mage_Towers/Quartzite/*
**Rock_Formations/Fossils:** Small, Large/Normal, Large/Ruined, Gigantic/Normal, Gigantic/Ruined
**Rock_Formations/Hotsprings:** Desert
**Npc/*/Encampment:** ALL compound layouts (Castle, Lumber, Quarry, etc.)
**Npc/*/Layout:** ALL layout prefabs
**Npc/Outlander/Boats:** Large (needs waterlog removal)
**Npc/Outlander/Camps/Tier3:** Base, Walls, Center (compound)

---

## NPC Faction Structures

| Faction | Prefab Count | Structure Types | Biome |
|---------|-------------|-----------------|-------|
| **Trork** | ~51 | Bonfire, Tent, Watchtower T1-3, Store, Warehouse, Misc, Resource, Encampment | Forest |
| **Outlander** | 68 | Houses T0-3, Forts T1-3, Towers T1-3, Camps T1-3, Gates, Spikes, Totems, Braziers, Boats, Misc | Tundra |
| **Feran** | 43 | Chieftain T1-3, Corners, Straight, Hut T2, Wall T2, Entrance T2, Base T3, Huts T3, Walls T3, Portals_Oasis | Jungle |
| **Kweebec** | 330 | Houses, Shops, Guard_Towers, Gardens, Wells, Lampposts, Seats (Autumn/Oak/Redwood/Swamp variants) | Friendly NPC |
| **Scarak** | 173 | Hive corridors, rooms, drops, egg chambers T1-3, Surface entrances T1-3 | **Reserved** |
| **Slothian** | 4 | Camps, Houses | Beach (monuments) |
| **Yeti** | 4 | Camps | Tundra |

---

## Terrain Materials

### Surface Blocks

| Block ID | Used In |
|----------|---------|
| `Soil_Grass_Full` | Forest, Jungle |
| `Soil_Grass` | — |
| `Soil_Mud` | Jungle (secondary), Swamp |
| `Soil_Dirt` | (sub-material) |
| `Soil_Sand_White` | Desert, Beach |
| `Soil_Sand_Red` | Desert (secondary), Beach (secondary) |
| `Soil_Snow` | Tundra |
| `Soil_Leaves` | — |
| `Rock_Stone` | Mountains, Caverns, Void |
| `Rock_Volcanic` | Volcano, Corrupted |
| `Rock_Basalt` | (sub-material) |
| `Rock_Sandstone` | (sub-material, path) |
| `Rock_Ice` | Tundra (sub + path) |
| `Rock_Shale` | — |
| `Rock_Slate` | — |

### Fluid Types

| Block ID | Used In |
|----------|---------|
| `Fluid_Lava` | Volcano (lava_moat) |
| `Water_Source` | Tundra (frozen_lake), Swamp (waterlogged) |

---

## Ground Cover Blocks (Column Props)

| Block ID | Biome Usage |
|----------|-------------|
| `Plant_Grass_Lush_Short` | Forest, Jungle |
| `Plant_Grass_Lush_Tall` | Forest |
| `Plant_Grass_Sharp` | Forest, Desert, Tundra, Volcano |
| `Plant_Grass_Sharp_Wild` | Forest, Desert, Tundra |
| `Plant_Grass_Jungle` | **Jungle** |
| `Plant_Grass_Jungle_Short` | **Jungle** |
| `Plant_Grass_Jungle_Tall` | **Jungle** |
| `Plant_Fern_Jungle` | **Jungle** |
| `Plant_Flower_Common_Red` | Forest |
| `Plant_Flower_Common_Yellow` | Forest |
| `Plant_Flower_Common_Blue` | Forest |
| `Plant_Flower_Common_White` | Forest |
| `Plant_Flower_Orchid_Purple` | **Jungle** |
| `Plant_Flower_Tall_Pink` | **Jungle** |
| `Plant_Flower_Tall_Purple` | **Jungle** |

### Unused Ground Cover Blocks (available)

`Plant_Grass_Wet`, `Plant_Grass_Wet_Overgrown`, `Plant_Grass_Sharp_Overgrown`, `Plant_Fern`, `Plant_Fern_Forest`, `Plant_Fern_Tall`, `Plant_Fern_Wet_Big`, `Plant_Bush_Jungle`, `Plant_Bush_Lush`, `Plant_Bush_Wet`, `Plant_Moss_Block_Green`, `Plant_Moss_Green_Dark`, `Plant_Moss_Rug_Lime`, `Plant_Vine`, `Plant_Vine_Wall`, `Plant_Vine_Rug`, `Plant_Reeds_Marsh`, `Plant_Reeds_Wet`, `Plant_Flower_Water_Green`

---

## Environment Types

| Environment ID | Used In | Character |
|---------------|---------|-----------|
| `Env_Zone1_Plains` | Forest, Jungle, Beach, Swamp | Green temperate |
| `Env_Zone2_Deserts` | Desert | Hot, warm |
| `Env_Zone3_Tundra` | Tundra | Cold, snowy |
| `Env_Zone3_Overground` | Mountains | Cold, not snowy |
| `Env_Zone4_Burning_Sands` | Volcano | Dark, fiery |
| `Zone1_Underground` | Caverns | Dark underground |
| `Env_Default_Void` | Void, Corrupted | Purple cosmic |

---

## Raw Database Search Guide

The full databases are at `db/` in the project root. Each file is grep-optimized — one entry per line.

| Database | File | Entries | Format |
|----------|------|---------|--------|
| **NPCs** | `db/NPCS.tsv` | 940 | `ROLE_ID \| CATEGORY \| SUBCATEGORY \| ASSET_PATH` |
| **Prefabs** | `db/PREFABS.tsv` | 7,757 | `PREFAB_ID \| CATEGORY \| SUBCATEGORY \| ASSET_PATH` |
| **Items** | `db/ITEMS.tsv` | 2,958 | `ITEM_ID \| CATEGORY \| SUBCATEGORY \| ASSET_PATH` |
| **Drops** | `db/DROPS.tsv` | 672 | `DROP_TABLE_ID \| CATEGORY \| ASSET_PATH` |
| **Spawns** | `db/SPAWNS.tsv` | 417 | `SPAWN_CONFIG_ID \| ASSET_PATH` |

### Search Examples

```bash
# Find all mobs in a faction
Grep: pattern="Trork" path="db/NPCS.tsv"

# Find all jungle-related prefabs
Grep: pattern="Jungle" path="db/PREFABS.tsv"

# Find all arch types
Grep: pattern="Arches" path="db/PREFABS.tsv"

# Find what a creature drops
Grep: pattern="Bear" path="db/DROPS.tsv"

# Find all weapons of a material
Grep: pattern="Weapon.*Iron" path="db/ITEMS.tsv"

# Find all NPC spawn configs for a biome
Grep: pattern="Zone1" path="db/SPAWNS.tsv"
```

For the routing guide and category summaries, see `.claude/docs/HYTALE_GAME_DATA.md`.
