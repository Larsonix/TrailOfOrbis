# Entity Classification System

This document explains how TrailOfOrbis classifies NPCs/mobs for RPG mechanics (XP, scaling, combat stats).

## Overview

The `DynamicEntityRegistry` automatically discovers and classifies all NPC roles at server startup. This provides automatic mod compatibility - any mod that adds NPCs using Hytale's standard role system will be detected and classified without configuration changes.

## Classification Types

| RPGMobClass | Description | XP? | Scaling? |
|-------------|-------------|-----|----------|
| `BOSS` | World bosses, dungeon bosses | Yes (high) | Yes |
| `ELITE` | Mini-bosses, captains, veterans | Yes (medium) | Yes |
| `HOSTILE` | Regular combat enemies | Yes | Yes |
| `PASSIVE` | Non-combat creatures, NPCs | No | No |

## Detection Priority

Classification uses this priority order (highest to lowest):

1. **Config Overrides** (`entity-discovery.yml` → `overrides`)
   - Explicit boss/elite/passive lists for specific role names
   - Example: `dragon_fire` → BOSS

2. **Group Patterns** (`entity-discovery.yml` → `group_patterns`)
   - NPCGroup names matching wildcard patterns
   - Example: `*/Bosses` pattern matches `Trork/Bosses`

3. **Name Patterns** (`entity-discovery.yml` → `detection_patterns`)
   - Role names matching wildcard patterns
   - Example: `*_Boss` pattern matches `trork_boss`

4. **Direct Group Membership** (automatic)
   - Checks if role belongs to known hostile/passive groups
   - See "Group-Based Classification" below

5. **Fallback** → PASSIVE
   - Unclassified roles default to PASSIVE (no XP, no scaling)

## Group-Based Classification

The system checks each role's NPCGroup memberships against known hostile/passive groups.

### Default Hostile Groups
These indicate combat enemies that give XP:
```
Trork, Goblin, Skeleton, Zombie, Void, Vermin,
Predators, PredatorsBig, Scarak, Outlander, Undead
```

### Default Passive Groups
These indicate non-combat creatures:
```
Livestock, Prey, PreyBig, Critters, Birds, Aquatic
```

### Why Direct Groups Instead of LivingWorld Meta-Groups?

**Historical Bug (Fixed Feb 2026):**

The original implementation tried to use Hytale's `LivingWorld/*` meta-groups:
- `LivingWorld/Aggressive` - meant to contain all hostile groups
- `LivingWorld/Neutral` - meant to contain neutral creatures
- `LivingWorld/Passive` - meant to contain ambient wildlife

**Two bugs were present:**

1. **Logic Error**: `LivingWorld/Neutral` was mapped to `HOSTILE`, but it actually contains passive farm animals (Livestock, Prey, PreyBig).

2. **Lookup Failure**: The `TagSetPlugin.hasTag("LivingWorld/Aggressive", roleIndex)` call silently returned `false` even for valid hostile mobs, likely due to how meta-group assets are loaded.

**The Fix**: Instead of using meta-group lookups, we now:
1. Enumerate all direct group memberships via `NPCGroup.getAssetMap()`
2. Check if any membership matches our known hostile/passive group lists
3. Make the lists configurable for custom mod support

## Configuration Reference

### entity-discovery.yml

```yaml
# Direct group names for classification (most reliable method)
classification_groups:
  # Groups that indicate hostile mobs
  hostile:
    - "Trork"
    - "Goblin"
    # ... add custom mod groups here

  # Groups that indicate passive mobs
  passive:
    - "Livestock"
    - "Critters"
    # ... add custom mod groups here

# Pattern-based detection
detection_patterns:
  boss:
    - "*_Boss"
    - "*Dragon*"
  elite:
    - "*_Captain*"
    - "*_Elite*"

# Explicit overrides (highest priority)
overrides:
  bosses:
    - "dragon_fire"
  elites:
    - "trork_chieftain"
  passive:
    - "friendly_npc"
```

## Key Classes

| Class | Purpose |
|-------|---------|
| `DynamicEntityRegistry` | Main discovery and classification logic |
| `EntityDiscoveryConfig` | YAML config model |
| `DiscoveredRole` | Immutable record for discovered role data |
| `RPGMobClass` | Enum: BOSS, ELITE, HOSTILE, PASSIVE |
| `TagLookupProvider` | Interface for group membership checks |

## Admin Commands

```
/rpgadmin entity stats     - Show discovery statistics
/rpgadmin entity lookup <role>  - Look up specific role classification
```

## Troubleshooting

### "0 HOSTILE roles discovered"

**Symptom**: Entity discovery shows many PASSIVE but 0 HOSTILE.

**Cause**: Group membership lookup is failing.

**Fix**: Ensure `classification_groups.hostile` in config includes the correct group names, or verify the defaults are loading.

### Custom mod mobs not classified correctly

**Solution**: Add the mod's group names to `classification_groups.hostile` or `classification_groups.passive` in `entity-discovery.yml`.

### Specific mob needs different classification

**Solution**: Add the role name to `overrides.bosses`, `overrides.elites`, or `overrides.passive`.

## Hytale Group Reference

Based on decompiled game data, Hytale organizes NPCs into these groups:

**Enemy Factions:**
- `Trork` - Trork warriors, shamans, etc.
- `Goblin` - Goblins, ogres, etc.
- `Skeleton` - Skeleton variants
- `Zombie` - Zombies, husks
- `Void` - Void creatures
- `Scarak` - Scarak bugs
- `Outlander` - Outlander humans
- `Undead` - Undead creatures

**Wild Creatures:**
- `Predators` - Wolves, bears, hostile animals
- `PredatorsBig` - Large predators
- `Vermin` - Rats, spiders, hostile vermin

**Passive Creatures:**
- `Livestock` - Farm animals (cows, pigs, chickens)
- `Prey` - Small prey animals (rabbits, deer)
- `PreyBig` - Large prey animals
- `Critters` - Ambient creatures (bugs, frogs)
- `Birds` - Flying birds
- `Aquatic` - Fish, sea creatures

## File Locations

```
src/main/java/io/github/larsonix/trailoforbis/mobs/classification/
├── DynamicEntityRegistry.java      # Main discovery logic
├── EntityDiscoveryConfig.java      # Config model
├── DiscoveredRole.java             # Role data record
├── RPGMobClass.java                # Classification enum
└── provider/
    └── TagLookupProvider.java      # Group lookup interface

src/main/resources/config/
└── entity-discovery.yml            # Configuration file
```
