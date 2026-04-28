# Hytale Modding Master Reference

> Built from: HytaleServer.jar (5,474 classes) + 39 decompiled/analyzed mods (3,223 Java + 3,281 JSON + 118 UI templates)
> Generated: 2026-04-05 | Hytale build: `2026.03.26-89796e57b`

## How to Use This Reference

Each section below links to a focused domain file (300-600 lines). Load only the file you need.
The decompiled Hytale server source and community reference implementations have the full code when you need to go deeper.

---

## I Want To...

### React to Game Events
| Task | Go To |
|------|-------|
| Intercept/modify damage before it lands | [combat-damage.md §Damage Pipeline](combat-damage.md) |
| React when an entity dies | [combat-damage.md §Death System](combat-damage.md) |
| Handle player connect/disconnect/ready | [infrastructure.md §Events Catalog](infrastructure.md) |
| React when entity spawns or is removed | [ecs-systems.md §RefSystem / HolderSystem](ecs-systems.md) |
| React when a component is added/changed/removed | [ecs-systems.md §RefChangeSystem](ecs-systems.md) |
| Handle block place/break/use | [infrastructure.md §Block Events](infrastructure.md) |
| Handle item drop/pickup/craft | [infrastructure.md §Item Events](infrastructure.md) |
| Handle mouse clicks / player interaction | [infrastructure.md §Input Events](infrastructure.md) |
| Fire custom ECS events | [ecs-systems.md §Custom Events](ecs-systems.md) |
| React when assets load/unload | [infrastructure.md §Asset Events](infrastructure.md) |

### Build an ECS System
| Task | Go To |
|------|-------|
| Choose the right system base class (decision tree) | [ecs-systems.md §Decision Tree](ecs-systems.md) |
| Run logic per entity every frame | [ecs-systems.md §EntityTickingSystem](ecs-systems.md) |
| Run logic per entity at an interval (1s, 2s, etc.) | [ecs-systems.md §DelayedEntitySystem](ecs-systems.md) |
| Hook into the damage pipeline | [ecs-systems.md §DamageEventSystem](ecs-systems.md) |
| Handle typed ECS events (block, craft, drop) | [ecs-systems.md §EntityEventSystem](ecs-systems.md) |
| Auto-attach components when entities spawn | [ecs-systems.md §HolderSystem](ecs-systems.md) |
| Control system execution order | [ecs-systems.md §System Dependencies](ecs-systems.md) |
| Register transient (non-persisted) components | [ecs-systems.md §Component Registration](ecs-systems.md) |

### Modify Player State
| Task | Go To |
|------|-------|
| Change health/mana/stamina | [entity-player.md §Stats & Modifiers](entity-player.md) |
| Add/remove named stat modifiers | [entity-player.md §Stats & Modifiers](entity-player.md) |
| Register custom stat types | [entity-player.md §Custom Stats](entity-player.md) |
| Modify movement speed, jump force, flight | [entity-player.md §Movement](entity-player.md) |
| Apply velocity/knockback/launch | [entity-player.md §Movement](entity-player.md) |
| Teleport player (same world) | [entity-player.md §Teleportation](entity-player.md) |
| Teleport player to/from instance | [world-instances.md §Instance System](world-instances.md) |
| Hide player from others | [entity-player.md §Visibility](entity-player.md) |
| Apply visual effect to entity | [entity-player.md §Entity Effects](entity-player.md) |
| Set entity nameplate text | [entity-player.md §Nameplates](entity-player.md) |
| Kill an entity programmatically | [combat-damage.md §Programmatic Kill](combat-damage.md) |

### Work with Items & Inventory
| Task | Go To |
|------|-------|
| Create an ItemStack | [items-inventory.md §ItemStack API](items-inventory.md) |
| Attach custom metadata to items (BSON) | [items-inventory.md §Item Metadata](items-inventory.md) |
| Switch item state/mode (weapon stances) | [items-inventory.md §Item States](items-inventory.md) |
| Give items to a player | [items-inventory.md §Transactions](items-inventory.md) |
| Remove items from inventory | [items-inventory.md §ItemContainer Operations](items-inventory.md) |
| Read/modify dropped item entities | [items-inventory.md §Dropped Items](items-inventory.md) |
| Look up item definitions at runtime | [items-inventory.md §Item Asset API](items-inventory.md) |
| Register items at runtime | [items-inventory.md §Runtime Registration](items-inventory.md) |
| Modify drop tables at runtime | [items-inventory.md §Item Drop Lists](items-inventory.md) |
| Remove/modify crafting recipes at runtime | [items-inventory.md §Crafting](items-inventory.md) |

### Show UI to Players
| Task | Go To |
|------|-------|
| Open a full-screen page (shop, skill tree) | [ui-system.md §InteractiveCustomUIPage](ui-system.md) |
| Show a persistent HUD (boss bar, XP bar) | [ui-system.md §CustomUIHud](ui-system.md) |
| Inject UI into native game pages (map) | [ui-system.md §AnchorActionModule](ui-system.md) |
| Build UI content dynamically | [ui-system.md §UICommandBuilder](ui-system.md) |
| Handle button clicks and input events | [ui-system.md §UIEventBuilder](ui-system.md) |
| Write .ui template files | [ui-system.md §Native .ui Format](ui-system.md) |
| Send toast notifications | [infrastructure.md §Notifications](infrastructure.md) |
| Show full-screen event titles | [infrastructure.md §Event Titles](infrastructure.md) |

### Work with the World
| Task | Go To |
|------|-------|
| Read/set blocks | [world-instances.md §World Operations](world-instances.md) |
| Query block type, material, gather type | [world-instances.md §BlockType API](world-instances.md) |
| Access block component entities | [world-instances.md §Chunk Operations](world-instances.md) |
| Register custom block states | [world-instances.md §Block States](world-instances.md) |
| Find entities in area (sphere/AABB) | [world-instances.md §Spatial Queries](world-instances.md) |
| Raycast from player (what are they looking at) | [world-instances.md §Spatial Queries](world-instances.md) |
| Check collision without moving | [world-instances.md §Collision](world-instances.md) |
| Get world time (day/night) | [world-instances.md §World Operations](world-instances.md) |

### Manage Instances & Portals
| Task | Go To |
|------|-------|
| Spawn a new instance world | [world-instances.md §Instance System](world-instances.md) |
| Teleport player into/out of instance | [world-instances.md §Instance System](world-instances.md) |
| Configure portal lifecycle | [world-instances.md §Portal System](world-instances.md) |
| Find safe spawn locations | [world-instances.md §Portal System](world-instances.md) |
| Place/snapshot/restore prefabs | [world-instances.md §Prefabs](world-instances.md) |
| Suppress mob spawns in an area | [world-instances.md §Spawn Suppression](world-instances.md) |

### Manage NPCs
| Task | Go To |
|------|-------|
| Spawn an NPC by role | [npcs.md §Spawning](npcs.md) |
| Register custom NPC actions | [npcs.md §Custom Actions](npcs.md) |
| Play NPC animations | [npcs.md §Animations](npcs.md) |
| Mount/dismount system | [npcs.md §Mounts](npcs.md) |
| Create entities from scratch | [npcs.md §Entity Creation](npcs.md) |

### Combat & Interactions
| Task | Go To |
|------|-------|
| Create custom interactions (item use) | [combat-damage.md §Custom Interactions](combat-damage.md) |
| Detect/modify attack chains (attack speed) | [combat-damage.md §Interaction Chains](combat-damage.md) |
| Spawn projectiles | [combat-damage.md §Projectiles](combat-damage.md) |
| Apply damage programmatically | [combat-damage.md §Damage Pipeline](combat-damage.md) |
| Tag damage with custom metadata | [combat-damage.md §Damage Metadata](combat-damage.md) |

### Infrastructure
| Task | Go To |
|------|-------|
| Register commands | [infrastructure.md §Commands](infrastructure.md) |
| Send/intercept packets | [infrastructure.md §Packets](infrastructure.md) |
| Play sounds (2D/3D) | [infrastructure.md §Sound](infrastructure.md) |
| Spawn particles | [infrastructure.md §Particles](infrastructure.md) |
| Control camera | [infrastructure.md §Camera](infrastructure.md) |
| Schedule delayed/periodic tasks | [infrastructure.md §Scheduling](infrastructure.md) |
| Define codecs for serialization | [infrastructure.md §Codecs](infrastructure.md) |
| Register custom asset stores | [infrastructure.md §Assets](infrastructure.md) |
| Hot-reload assets at runtime | [infrastructure.md §Assets](infrastructure.md) |
| Manage config files | [infrastructure.md §Config](infrastructure.md) |
| Add dynamic entity lighting | [infrastructure.md §Dynamic Light](infrastructure.md) |

### Write JSON Assets
| Task | Go To |
|------|-------|
| Define items (weapons, armor, tools, consumables) | [json-schemas.md §Item Definitions](json-schemas.md) |
| Define drop tables (loot) | [json-schemas.md §Drop Tables](json-schemas.md) |
| Define crafting recipes | [json-schemas.md §Recipes](json-schemas.md) |
| Define NPC roles and behaviors | [json-schemas.md §NPC Roles](json-schemas.md) |
| Define entity effects (buffs, debuffs) | [json-schemas.md §Entity Effects](json-schemas.md) |
| Define interactions (combat abilities) | [json-schemas.md §Interactions](json-schemas.md) |
| Define projectile configs | [json-schemas.md §Projectiles](json-schemas.md) |
| Patch existing assets (mod-compatible) | [json-schemas.md §Patch System](json-schemas.md) |

### Find Working Examples
| Task | Go To |
|------|-------|
| Which mod does what I need? | [mod-examples.md](mod-examples.md) |

---

## Reference Files

| File | Domain | Lines |
|------|--------|-------|
| [ecs-systems.md](ecs-systems.md) | 10 ECS system types, queries, dependencies, components | ~500 |
| [combat-damage.md](combat-damage.md) | Damage pipeline, death, interactions, projectiles | ~500 |
| [entity-player.md](entity-player.md) | Stats, movement, teleport, effects, visibility | ~500 |
| [items-inventory.md](items-inventory.md) | ItemStack, metadata, inventory, transactions | ~500 |
| [ui-system.md](ui-system.md) | .ui format, pages, HUDs, anchors, builders | ~600 |
| [world-instances.md](world-instances.md) | World ops, blocks, instances, portals, map, worldgen | ~600 |
| [npcs.md](npcs.md) | NPC spawning, AI actions, mounts, animations | ~400 |
| [infrastructure.md](infrastructure.md) | Events, commands, packets, sound, camera, codecs, assets | ~600 |
| [json-schemas.md](json-schemas.md) | All JSON asset format schemas | ~600 |
| [mod-examples.md](mod-examples.md) | Mod → pattern cross-reference | ~400 |

## Source Material

| Source | Location | Content |
|--------|----------|---------|
| HytaleServer decompiled | `../APIReference/decompiled-full/` | 5,474 Hytale core classes |
| Community mods | Community reference implementations | 3,223 Java + 3,281 JSON + 118 UI |
