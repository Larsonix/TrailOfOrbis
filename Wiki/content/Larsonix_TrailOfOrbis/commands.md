---
name: Commands
title: Commands
description: Complete command reference for Trail of Orbis - shortcuts, player commands, and admin tools
author: Larsonix
sort-index: -80
order: 4
published: true
---

# Commands

Trail of Orbis uses two main command prefixes : `/too` for player commands and `/tooadmin` for server administration. Shortcut commands give you quick access to the most common actions.

---

## You Only Need One Command

Every UI page in Trail of Orbis has **navigation buttons** that link to every other page. Type `/stats` and you'll see buttons for Attributes, Skill Tree, and more. No need to type another command.

**Start anywhere, navigate everywhere.**

> [!TIP]
> `/stats` is the best starting point. It shows your full character overview and has buttons to reach both the Attribute page and the Skill Sanctum.

---

## Shortcut Commands

Quick-access commands that bypass the `/too` prefix :

| Command | Action |
|---------|--------|
| `/stats` | Opens the character stats UI |
| `/attr` | Opens the Attribute allocation UI |
| `/skilltree` | Enters or exits the Skill Sanctum (toggle) |
| `/sanctum` | Enters or exits the Skill Sanctum (toggle) |

---

## Player Commands

All player commands use `/too` as the root. Aliases : `/trailoforbis`, `/orbis`.

### Stats

| Command | Description |
|---------|-------------|
| `/too stats` | Opens your complete character stats page with all 153 computed stat fields, Gear bonuses, and Attribute breakdown |

---

### Attributes

Manage your 6 elemental Attribute points.

| Command | Description |
|---------|-------------|
| `/too attr view` | Open the Attribute allocation UI |
| `/too attr allocate <element>` | Spend 1 point on an Element |
| `/too attr unallocate <element> [amount]` | Refund points from an Element (all if no amount given) |
| `/too attr reset` | Reset ALL Attributes to zero, refund all points |

**Element names :** `fire`, `water`, `lightning`, `earth`, `wind`, `void`

**Examples :**
```

/too attr allocate fire          → Spend 1 point on Fire
/too attr unallocate wind 5      → Refund 5 points from Wind
/too attr reset                  → Full respec, all points returned

```

> [!TIP]
> `/attr` opens the UI directly. Use `/too attr allocate` for precise command-line allocation.

---

### Skill Tree

Manage your passive Skill Tree nodes (485 nodes across 15 regions).

| Command | Description |
|---------|-------------|
| `/too skilltree view` | Open the Skill Tree UI/map |
| `/too skilltree allocate <node>` | Allocate a skill point to a node |
| `/too skilltree deallocate <node>` | Remove a skill point from a node |
| `/too skilltree respec` | Full Skill Tree reset, all points returned |
| `/too skilltree info <node>` | Show detailed info about a specific node |
| `/too skilltree list` | List all your currently allocated nodes |

**Examples :**
```

/too skilltree allocate fire_entry     → Allocate the Fire arm entry node
/too skilltree info fire_notable_1     → View details of a notable node
/too skilltree respec                  → Reset entire tree

```

---

### Skill Sanctum

Enter and exit the 3D Skill Tree world.

| Command | Description |
|---------|-------------|
| `/too sanctum enter` | Enter the Skill Sanctum instance |
| `/too sanctum exit` | Exit back to the overworld |

> [!NOTE]
> The shortcut `/skilltree` toggles between enter and exit automatically.

---

### Realms

Manage your Realm dungeon sessions.

| Command | Description |
|---------|-------------|
| `/too realm info` | Display info about your current Realm (biome, level, Modifiers, time remaining) |
| `/too realm exit` | Emergency exit, forfeits victory rewards |

> [!WARNING]
> `/too realm exit` is a safety command for when you're stuck, not the normal way to leave. Completing a Realm (killing all mobs) or timing out exits you automatically with proper rewards.

---

### Combat

Toggle combat display settings.

| Command | Description |
|---------|-------------|
| `/too combat detail` | Toggle detailed damage breakdown |
| `/too combat detail on` | Enable damage detail display |
| `/too combat detail off` | Disable damage detail display |

When enabled, you'll see a full breakdown of each hit in chat : base damage, pipeline stages applied, crit calculations, and final damage dealt.

---

## Admin Commands

> [!CAUTION]
> Admin commands require the `too.admin` permission. These modify player data, spawn items, and control server systems. Use with care.

Root command : `/tooadmin` (alias : `/tooa`).

### Player Inspection & Management

| Command | Description |
|---------|-------------|
| `/tooadmin inspect <player>` | View a player's full RPG profile |
| `/tooadmin reload` | Reload all configuration files |
| `/tooadmin testcolor` | Test color rendering |

---

### Level & Points

| Command | Description |
|---------|-------------|
| `/tooadmin xp <op> <player> <amount>` | Set, add, or remove player XP |
| `/tooadmin level <op> <player> <amount>` | Set, add, or remove player levels |
| `/tooadmin points <op> <player> <amount>` | Set, add, or remove unallocated Attribute points |
| `/tooadmin attr <op> <player> <element> <amount>` | Set, add, or remove Attribute values directly |
| `/tooadmin skillpoints <op> <player> <amount>` | Set, add, or remove skill points |
| `/tooadmin reset <player>` | Full player reset (requires confirmation) |
| `/tooadmin resetconfirm <player>` | Confirm the reset |

**Operations (`<op>`) :** `add`, `remove`, `set`

---

### Item Generation

| Command | Description |
|---------|-------------|
| `/tooadmin give gear <level> [rarity] [quality]` | Generate RPG Gear on your held item |
| `/tooadmin give map <level> [rarity] [quality]` | Give yourself a Realm Map |
| `/tooadmin give stone <type>` | Give yourself a specific Stone (22 types available) |
| `/tooadmin give gem <type>` | Give yourself a skill gem |

**Examples :**
```

/tooadmin give gear 50 legendary 90     → Lv50 Legendary Gear, Q90
/tooadmin give map 100 epic             → Lv100 Epic Realm Map
/tooadmin give stone WARDENS_SEAL       → Give a Warden's Seal

```

---

### Gear Inspection & Editing

| Command | Description |
|---------|-------------|
| `/tooadmin gear info` | Show full RPG data of held Gear |
| `/tooadmin gear setlevel <level>` | Change held Gear's item level |
| `/tooadmin gear setrarity <rarity>` | Change held Gear's Rarity |
| `/tooadmin gear setquality <quality>` | Change held Gear's Quality (1-101) |
| `/tooadmin gear copy` | Copy held Gear data to clipboard |
| `/tooadmin gear paste` | Paste clipboard data onto held Gear |
| `/tooadmin gear reload` | Reload Gear definitions from config |

---

### Realm Administration

| Command | Description |
|---------|-------------|
| `/tooadmin realm open <biome> <size> <level>` | Open a new Realm instance |
| `/tooadmin realm list` | List all active Realm instances |
| `/tooadmin realm info [realmId]` | Show details of current or specific Realm |
| `/tooadmin realm exit [player]` | Force-exit a player from their Realm |
| `/tooadmin realm close <realmId>` | Close a Realm instance |

---

### Skill Tree Administration

| Command | Description |
|---------|-------------|
| `/tooadmin skilltree inspect <player>` | View a player's Skill Tree state |
| `/tooadmin skilltree reset <player>` | Reset a player's entire Skill Tree |
| `/tooadmin skilltree allocate <player> <node>` | Allocate a node for a player |
| `/tooadmin skilltree allocateall <player>` | Allocate ALL nodes for a player |
| `/tooadmin skilltree deallocate <player> <node>` | Deallocate a node for a player |

---

### Sanctum Administration

| Command | Description |
|---------|-------------|
| `/tooadmin sanctum editlayout <id> <json>` | Edit a Sanctum layout |
| `/tooadmin sanctum exportlayout` | Export current Sanctum layout to file |
| `/tooadmin sanctum generatetemplate` | Generate a new Sanctum template |
| `/tooadmin sanctum testline` | Test beam rendering |

---

### Stone, Map & Entity Tools

| Command | Description |
|---------|-------------|
| `/tooadmin stone info` | Show held Stone details |
| `/tooadmin stone status` | Check Stone application status |
| `/tooadmin stone copy` / `paste` | Copy/paste Stone data |
| `/tooadmin map info` | Show held Map details |
| `/tooadmin map copy` / `paste` | Copy/paste Map data |
| `/tooadmin entity stats` | Show entity discovery statistics |
| `/tooadmin entity rescan` | Rescan all entity types |
| `/tooadmin entity classify` | Classify the targeted entity |
| `/tooadmin loot stats` | Show loot discovery statistics |
| `/tooadmin loot rescan` | Rescan loot tables |
