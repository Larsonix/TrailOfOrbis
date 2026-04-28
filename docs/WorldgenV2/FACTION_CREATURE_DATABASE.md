# Hytale Faction & Creature Master Database

Complete inventory of every race, faction, creature, and their structures/props. Use this to plan biome assignments without overlap.

**Last updated:** April 20, 2026

---

## Spawning Rules

| Category | Spawnable? | Examples |
|----------|-----------|---------|
| `Creature/*` | **YES** | Spider, Bear, Raptor, Scorpion, all wildlife |
| `Intelligent/Aggressive/*` | **YES** | Trork, Goblin, Outlander, Skeleton, Scarak |
| `Undead/*` | **YES** | Ghoul, Wraith, Zombie, Shadow_Knight, Risen |
| `Elemental/*` | **YES** | Golem_Crystal_*, Golem_Firesteel, Spirit_* |
| `Void/*` | **YES** | Crawler_Void, Spectre_Void, Eye_Void |
| `Avian/*` | **YES** | Bat, Crow, Hawk, Pterodactyl |
| `Aquatic/*` | **YES** | Piranha, Shark, Crab, Jellyfish |
| `Intelligent/Neutral/*` | **DEAD** | Feran, Kweebec, Bramblekin, Tuluk |
| `Intelligent/Passive/*` | **DEAD** | Slothian, Klops, Quest_Master, Temple_* |
| `Boss/*` | **DEAD** | Dragon_Fire, Dragon_Frost (no AI/animations) |
| `Hedera` | **DEAD** | Classified Aggressive but returns null |

---

## Part 1: Hostile Factions

### Trork — Forest Faction

**Biome:** Forest (exclusive)
**Boss:** Trork_Chieftain
**Structures:** ~51 prefabs (Tent, Bonfire, Fireplace, Trap, Warning, Watchtower T1-3, Store T2-3, Warehouse, Misc/Large, Resource/Lumber, Resource/Rock, Burrow, Encampment/Hunter, Encampment/Lumber, Encampment/Quarry, Encampment/Castle)

| Role | Style | Assigned | Notes |
|------|-------|----------|-------|
| Trork_Brawler | Heavy melee | Forest | |
| Trork_Warrior | Balanced melee | Forest | +Patrol variant |
| Trork_Guard | Standard melee | Forest | |
| Trork_Hunter | Ranged bow | Forest | |
| Trork_Shaman | Magic/heals | Forest | |
| Trork_Mauler | Slow hard-hitter | Forest | |
| Trork_Chieftain | **BOSS** | Forest | |
| Trork_Sentry | Perimeter guard | *Unassigned* | +Patrol |
| Trork_Doctor_Witch | Witch doctor | *Unassigned* | |
| Trork_Unarmed | Unarmed | *Unassigned* | |
| Wolf_Trork_Hunter | Wolf companion | *Unassigned* | Spawns with Hunter |
| Wolf_Trork_Shaman | Wolf companion | *Unassigned* | Spawns with Shaman |

---

### Goblin — Mountains Faction

**Biome:** Mountains (exclusive)
**Boss:** Goblin_Duke (3-phase: Phase 1 → Phase 2 → Phase 3 Fast/Slow)
**Structures:** 0 surface prefabs — Goblins use cave/mine dungeon structures (Goblin_Lair_*, all RUNTIME ONLY with PrefabSpawnerBlocks, 60+ dungeon modules)

| Role | Style | Assigned | Notes |
|------|-------|----------|-------|
| Goblin_Scrapper | Basic melee | Mountains | +Patrol |
| Goblin_Lobber | Ranged projectiles | Mountains | +Patrol |
| Goblin_Miner | Pickaxe fighter | Mountains | +Patrol |
| Goblin_Thief | Stealth attacker | Mountains | +Patrol |
| Goblin_Ogre | Heavy brute | Mountains | |
| Goblin_Duke | **BOSS** Phase 1 | Mountains | 3-phase fight |
| Goblin_Scavenger | Scavenger base | *Unassigned* | +Battleaxe, +Sword variants |
| Goblin_Hermit | Hermit | *Unassigned* | |

---

### Outlander — Tundra + Corrupted Faction

**Biomes:** Tundra (base roles) + Corrupted (cult roles)
**Boss:** None dedicated (Tundra uses Yeti, Corrupted uses Risen_Knight)
**Structures:** 74 prefabs — ICE/SNOW themed (Houses T0-3, Forts T1-3, Towers T1-3, Camps T1-3, Gates T1-3, Spikes, Totems, Braziers, Boats, Ice_Caves, Misc)

| Role | Style | Assigned | Notes |
|------|-------|----------|-------|
| Outlander_Marauder | Melee raider | Tundra | |
| Outlander_Cultist | Dark magic | Corrupted | |
| Outlander_Priest | Dark healer | Corrupted | |
| Outlander_Sorcerer | Dark ranged magic | Corrupted | |
| Outlander_Peon | Basic worker | *Unassigned* | |
| Outlander_Hunter | Ranged hunter | *Unassigned* | |
| Outlander_Stalker | Stealth tracker | *Unassigned* | |
| Outlander_Berserker | Rage melee | *Unassigned* | |
| Outlander_Brute | Heavy tank | *Unassigned* | |
| Wolf_Outlander_Priest | Wolf companion | — | Spawns with Priest |
| Wolf_Outlander_Sorcerer | Wolf companion | — | Spawns with Sorcerer |

---

### Scarak — Reserved Insectoid Faction

**Biome:** Reserved for future Scarak biome (Broodmother temp in Beach)
**Boss:** Scarak_Broodmother + Dungeon_Scarak_Broodmother
**Structures:** 326 hive prefabs (Corridors, Tiers 1-3 rooms, Boss chambers, Surface entrance, Drops)

| Role | Style | Assigned | Notes |
|------|-------|----------|-------|
| Scarak_Fighter | Melee insectoid | *Reserved* | +Patrol, +Royal_Guard |
| Scarak_Defender | Tank insectoid | *Reserved* | +Patrol |
| Scarak_Seeker | Scout insectoid | *Reserved* | +Patrol |
| Scarak_Louse | Swarm insectoid | *Reserved* | |
| Scarak_Broodmother | **BOSS** queen | Beach (temp) | |
| Dungeon_Scarak_* | 9 dungeon variants | — | |

---

## Part 2: Neutral Factions (CANNOT SPAWN — structures only)

### Feran — Jungle Structures

**42 prefabs:** Chieftain T1-3, Corners T1, Straight T1, Hut T2, Wall T2, Entrance T2, Base T3, Huts T3, Walls T3, Portals_Oasis (14 types)
**6 NPC roles** — ALL return null from spawnEntity()

### Kweebec — Friendly NPC Villages

**337 prefabs** (largest faction): Oak (153), Autumn (135), Redwood (28), Swamp (20) — Gardens, Houses, Shops, Guard_Towers, Lampposts, Wells
**12 NPC roles** — ALL unspawnable

### Slothian — Beach Monuments

**18 prefabs:** Houses, Temple, Tower, Gate, Camps (most marked "Disabled")
**1 NPC role** — Unspawnable (Intelligent/Passive)

### Yeti — Tundra Camps

**4 prefabs:** Camps only
**1 NPC role** — Yeti is `Creature/Mythic` = **SPAWNABLE** (boss in Tundra)

---

## Part 3: Undead

### Base Skeleton (9 roles × 3 variants = 27 files) — ALL UNASSIGNED

| Role | Style | Notes |
|------|-------|-------|
| Skeleton | Basic | |
| Skeleton_Fighter | Melee | |
| Skeleton_Knight | Heavy melee | |
| Skeleton_Soldier | Standard melee | |
| Skeleton_Scout | Fast melee | |
| Skeleton_Archer | Ranged bow | |
| Skeleton_Ranger | Ranged | |
| Skeleton_Mage | Magic | |
| Skeleton_Archmage | Heavy magic | |

### Skeleton_Frost (8 roles) — Tundra

4 assigned (Knight, Scout, Archer, Mage), 4 unassigned (Fighter, Soldier, Ranger, Archmage)

### Skeleton_Burnt (8 roles) — Desert + Volcano

3 assigned both (Knight, Gunner, Wizard), 1 boss (Praetorian = Desert), 4 unassigned (Archer, Alchemist, Lancer, Soldier)

### Skeleton_Sand (8 roles) — Desert

2 assigned (Archer, Mage), 6 unassigned (Guard, Assassin, Ranger, Scout, Soldier, Archmage)

### Skeleton_Pirate (3 roles) — Beach

All 3 assigned (Captain, Striker, Gunner)

### Skeleton_Incandescent (4 roles) — ALL UNASSIGNED

Fighter, Footman, Mage, Head — no biome uses these. Possible future biome identity.

### Zombie (7 roles) — ALL UNASSIGNED

Zombie, Zombie_Burnt, Zombie_Frost, Zombie_Sand, Zombie_Aberrant, Zombie_Aberrant_Big, Zombie_Aberrant_Small

### Generic Undead

| Role | Assigned | Notes |
|------|----------|-------|
| Ghoul | Swamp, Corrupted | Fast melee |
| Wraith | Swamp | Ghostly ranged |
| Wraith_Lantern | Swamp (**BOSS**) | Spectral lord |
| Shadow_Knight | Corrupted | Dark warrior |
| Werewolf | Corrupted | Transformed beast |
| Risen_Knight | Corrupted (**BOSS**) | Armored undead |
| Risen_Gunner | *Unassigned* | Ranged undead |
| Hound_Bleached | *Unassigned* | Undead dog |

---

## Part 4: Elementals

### Golems

| Role | Assigned | Notes |
|------|----------|-------|
| Golem_Crystal_Earth | Caverns | Earth elemental |
| Golem_Crystal_Flame | Volcano (**BOSS**) | Fire elemental |
| Golem_Crystal_Frost | *Unassigned* | Ice elemental |
| Golem_Crystal_Sand | *Unassigned* | Sand elemental |
| Golem_Crystal_Thunder | *Unassigned* | Thunder elemental |
| Golem_Firesteel | Volcano | Molten guardian |
| Golem_Guardian_Void | Void (regular + **BOSS**) | Void colossus |

### Spirits (ALL UNASSIGNED)

| Role | Element | Suggested |
|------|---------|-----------|
| Spirit_Ember | Fire | Volcano |
| Spirit_Frost | Ice | Tundra |
| Spirit_Root | Nature | Forest/Jungle |
| Spirit_Thunder | Thunder | Mountains |

---

## Part 5: Creatures (Wildlife)

### Mammals — Predators

| Role | Assigned | Exclusive? | Notes |
|------|----------|-----------|-------|
| Bear_Grizzly | Mountains | No | Heavy tank |
| Bear_Polar | Tundra | **Yes** | Arctic |
| Wolf_Black | Jungle | **Yes** | Dark pack |
| Wolf_White | Tundra | **Yes** | Arctic pack |
| Hyena | *Unassigned* | — | Pack scavenger |
| Fox | *Unassigned* | — | Small predator |
| Leopard_Snow | *Unassigned* | — | Arctic stalker |
| Tiger_Sabertooth | *Unassigned* | — | Prehistoric |

### Mammals — Herbivores (ALL UNASSIGNED)

Deer_Stag, Deer_Doe, Moose_Bull, Moose_Cow, Antelope, Armadillo, Mosshorn, Mosshorn_Plain

### Reptiles

| Role | Assigned | Exclusive? | Notes |
|------|----------|-----------|-------|
| Crocodile | Jungle, Swamp, Beach | No | Ambush (3 biomes) |
| Raptor_Cave | Jungle, Caverns | No | Pack dino (2 biomes) |
| Rex_Cave | Jungle (**BOSS**), Caverns (boss) | No | Apex dino (overlap!) |
| Toad_Rhino | Jungle, Swamp | No | Massive amphibian |
| Toad_Rhino_Magma | Volcano | **Yes** | Lava toad |
| Lizard_Sand | Desert | **Yes** | Desert reptile |
| Tortoise | *Unassigned* | — | Slow armored |

### Vermin

| Role | Assigned | Exclusive? | Notes |
|------|----------|-----------|-------|
| Spider | Jungle | No | Web trapper |
| Spider_Cave | Caverns | **Yes** | Cave variant |
| Snake_Cobra | Jungle, Beach | No | Venomous |
| Snake_Marsh | Jungle, Swamp | No | Swamp venom |
| Snake_Rattle | *Unassigned* | — | Rattlesnake |
| Scorpion | Desert, Beach | No | Desert predator |
| Rat | Caverns | **Yes** | Basic vermin |
| Molerat | Caverns | **Yes** | Burrowing |
| Slug_Magma | Volcano | **Yes** | Fire slug |
| Snail_Frost | *Unassigned* | — | Ice snail |
| Snail_Magma | *Unassigned* | — | Fire snail |
| Larva_Silk | *Unassigned* | — | Silk larva — **UNVERIFIED** |

### Mythic

| Role | Assigned | Exclusive? | Notes |
|------|----------|-----------|-------|
| Snapdragon | Jungle | **Yes** | Massive plant beast |
| Fen_Stalker | Jungle, Swamp | No | Horror |
| Yeti | Tundra (**BOSS**) | **Yes** | Ice giant |
| Emberwulf | Volcano | **Yes** | Fire wolf |
| Cactee | Desert | **Yes** | Animated cactus |
| Hatworm | *Unassigned* | — | Worm creature — **UNVERIFIED** |
| Trillodon | *Unassigned* | — | Prehistoric — **UNVERIFIED** |
| Spark_Living | *Unassigned* | — | Living spark — **UNVERIFIED** |

### Critters (7 — passive behavior but spawnable)

Frog_Blue, Frog_Green, Frog_Orange, Gecko, Mouse, Meerkat, Squirrel

### Avian (20 — all spawnable, NONE assigned to biomes)

**Aerial:** Bat, Bat_Ice, Bluebird, Crow, Finch_Green, Flamingo, Owl_Brown, Owl_Snow, Parrot, Penguin, Raven, Sparrow, Woodpecker
**Fowl:** Duck, Pigeon
**Raptor:** Archaeopteryx, Hawk, Pterodactyl, Tetrabird, Vulture

### Aquatic (30 — all spawnable, NONE assigned to biomes)

**Freshwater (10):** Bluegill, Catfish, Frostgill, Minnow, Pike, Piranha, Piranha_Black, Salmon, Snapjaw, Trout_Rainbow
**Marine (14):** Clownfish, Crab, Jellyfish (6 colors), Lobster, Pufferfish, Tang (4 types)
**Abyssal (6):** Eel_Moray, Shark_Hammerhead, Shellfish_Lava, Trilobite, Trilobite_Black, Whale_Humpback

### Livestock (18 species + babies + tamed variants = 72 total)

Bison, Boar, Bunny, Camel, Chicken, Chicken_Desert, Cow, Goat, Horse, Mouflon, Pig, Pig_Wild, Rabbit, Ram, Sheep, Skrill, Turkey, Warthog

---

## Part 6: Current Biome Identity Summary

| Biome | Faction | Exclusive Mobs | Shared Mobs | Boss | Total |
|-------|---------|---------------|-------------|------|-------|
| **Forest** | Trork | 6 Trork roles | — | Trork_Chieftain | 7 |
| **Mountains** | Goblin | 5 Goblin roles | Bear_Grizzly | Goblin_Duke (3-phase) | 6 |
| **Tundra** | Outlander | Bear_Polar, Wolf_White, 4 Frost Skeletons | Outlander_Marauder | Yeti | 7 |
| **Desert** | None | Lizard_Sand, Cactee | Skeleton_Burnt×3, Skeleton_Sand×2, Scorpion | Praetorian | 9 |
| **Volcano** | None | Emberwulf, Toad_Rhino_Magma, Slug_Magma, Golem_Firesteel | Skeleton_Burnt×3 | Crystal_Flame | 7 |
| **Jungle** | Feran (struct only) | Wolf_Black, Snapdragon | Spider, Crocodile, Raptor_Cave, Snake×2, Fen_Stalker, Toad_Rhino | Rex_Cave | 10 |
| **Swamp** | None | — | Ghoul, Wraith, Crocodile, Snake_Marsh, Fen_Stalker, Toad_Rhino | Wraith_Lantern | 7 |
| **Beach** | None | Skeleton_Pirate×3 | Crocodile, Snake_Cobra, Scorpion | Scarak_Broodmother | 7 |
| **Caverns** | None | Spider_Cave, Molerat, Rat | Raptor_Cave, Golem_Crystal_Earth | ??? | 6 |
| **Void** | None | ALL 5 Void creatures + Golem_Guardian_Void | — | Guardian_Void | 6 |
| **Corrupted** | Outlander (cult) | Shadow_Knight, Werewolf | Outlander×3, Ghoul | Risen_Knight | 6 |

---

## Part 7: Biome Overlap Map

Mobs appearing in 2+ biomes — shows where identity blurs:

| Mob | Biomes | Impact |
|-----|--------|--------|
| Skeleton_Burnt_Knight/Gunner/Wizard | Desert, Volcano | Intended (fire undead shared) |
| Crocodile | Jungle, Swamp, Beach | 3-way — consider reducing |
| Snake_Cobra | Jungle, Beach | Minor overlap |
| Snake_Marsh | Jungle, Swamp | Thematic (swamp snake) |
| Fen_Stalker | Jungle, Swamp | Thematic (swamp creature) |
| Toad_Rhino | Jungle, Swamp | Thematic (amphibian) |
| Scorpion | Desert, Beach | Minor (arid themes) |
| Ghoul | Swamp, Corrupted | Thematic (undead) |
| Raptor_Cave | Jungle, Caverns | Overlap — name says "Cave" |
| Rex_Cave | Jungle, Caverns | **Major overlap** — same boss in 2 biomes |
| Golem_Crystal_Earth | Caverns (regular + boss?) | No overlap but dual-use |

---

## Part 8: Unverified Mobs (Creature/* but never tested)

These exist in the database, are categorized as spawnable types, but have **never been spawned in any working biome**. May fail due to missing AI/animations.

| Role | Category | Suggested For | Risk |
|------|----------|--------------|------|
| Trillodon | Creature/Mythic | Caverns | **UNTESTED** — prehistoric burrower |
| Hatworm | Creature/Mythic | Caverns/Corrupted | **UNTESTED** — cave worm |
| Larva_Silk | Creature/Vermin | Caverns/Jungle | **UNTESTED** — silk larva |
| Spark_Living | Creature/Mythic | Void/Corrupted | **UNTESTED** — living spark |
| Tiger_Sabertooth | Creature/Mammal | Jungle/Caverns | **UNTESTED** — prehistoric |
| Tortoise | Creature/Reptile | Beach/Desert | **UNTESTED** |
| Fox | Creature/Mammal | Forest/Temperate | **UNTESTED** |
| Hyena | Creature/Mammal | Plains | **UNTESTED** |
| Snake_Rattle | Creature/Vermin | Desert | **UNTESTED** |
| Snail_Frost | Creature/Vermin | Tundra | **UNTESTED** |
| Snail_Magma | Creature/Vermin | Volcano | **UNTESTED** |
| Leopard_Snow | Creature/Mammal | Tundra | **UNTESTED** |
| Bat | Avian/Aerial | Caverns | **UNTESTED** |
| Bat_Ice | Avian/Aerial | Tundra | **UNTESTED** |
| All base Skeleton_* (9 roles) | Undead/Skeleton | Caverns? | Probably work but never used |
| All Zombie_* (7 roles) | Undead/Zombie | Corrupted? | Probably work but never used |
| Skeleton_Incandescent_* (4) | Undead/Skeleton | New biome? | **UNTESTED** |
| Risen_Gunner | Undead | Corrupted? | Probably works |
| Hound_Bleached | Undead | Corrupted? | Probably works |
