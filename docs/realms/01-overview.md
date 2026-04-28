# Realms System Overview

## Concept

The Realms system brings POE-style mapping to Hytale. Players obtain **Realm Maps** from monster drops, then use them to create temporary **Realms** - instanced dimensions filled with monsters matching the map's level. Realms are "completable" by killing all spawned monsters within a time limit.

## Design Goals

1. **Replayable Endgame** - Provide infinitely repeatable content that scales with player level
2. **Player Agency** - Allow customization through modifiers and currency crafting
3. **Risk vs Reward** - More dangerous modifiers yield better rewards
4. **Progression Sink** - Give players meaningful use for accumulated currency
5. **Build Testing** - Controlled environment to test builds against known encounters

## Core Loop

```
┌─────────────────────────────────────────────────────────────────┐
│                        REALM LOOP                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. ACQUIRE                                                     │
│     ├── Mob kills drop Realm Maps (level-scaled)                │
│     └── Maps have random base modifiers                         │
│                                                                 │
│  2. PREPARE (Optional)                                          │
│     ├── Use currency to reroll modifiers                        │
│     ├── Add more modifiers for risk/reward                      │
│     └── Upgrade map tier                                        │
│                                                                 │
│  3. ACTIVATE                                                    │
│     ├── Player uses map item                                    │
│     ├── Portal spawns at player location                        │
│     ├── Realm instance created from template                    │
│     └── Monsters pre-spawned at designated points               │
│                                                                 │
│  4. CHALLENGE                                                   │
│     ├── Enter portal → teleport to Realm                        │
│     ├── Timer starts (configurable duration)                    │
│     ├── Kill counter tracks remaining monsters                  │
│     └── Modifiers apply to all entities                         │
│                                                                 │
│  5. COMPLETE                                                    │
│     ├── SUCCESS: All monsters killed → bonus rewards            │
│     ├── TIMEOUT: Time expires → partial rewards                 │
│     ├── DEATH: (based on modifiers) → possible penalties        │
│     └── EXIT: Players teleported home                           │
│                                                                 │
│  6. REWARDS                                                     │
│     ├── Base XP and loot from monster kills                     │
│     ├── Completion bonus (based on modifiers)                   │
│     ├── Increased map drops inside Realms                       │
│     └── Special Realm-only drops                                │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Integration with Existing Systems

### Leveling System
- Realm Maps drop at levels relative to the player's level
- Monster levels in Realms match the map's level
- XP gained in Realms uses standard XP formulas with modifier bonuses

### Mob Scaling System
- Realm monsters use `MobScalingManager` for stat calculation
- Base level is fixed (map level) instead of dynamic
- Modifiers can add stat multipliers on top

### Loot System
- Standard `LootListener` handles drops inside Realms
- Modifier-based IIQ/IIR bonuses applied
- Realm completion triggers bonus loot table

### Attribute System
- Player attributes function normally in Realms
- Some modifiers may interact with specific attributes

## Realm States

```
         ┌──────────────────────────────────────────────┐
         │                  REALM LIFECYCLE             │
         └──────────────────────────────────────────────┘

┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐
│ CREATED │───→│ SPAWNING│───→│ WAITING │───→│ ACTIVE  │───→│ ENDING  │
└─────────┘    └─────────┘    └─────────┘    └─────────┘    └─────────┘
     │              │              │              │              │
     │              │              │              │              │
   Portal       Monsters       Portal          Timer          Cleanup
   placed       placed        ready           running        rewards
                                                              teleport
```

| State | Description | Duration |
|-------|-------------|----------|
| CREATED | Realm instance created, world loaded | ~1-2 seconds |
| SPAWNING | Monsters being placed at spawn points | ~1-3 seconds |
| WAITING | Ready for player entry | Until player enters |
| ACTIVE | Timer running, combat enabled | Configurable (default 6 min) |
| ENDING | Completion or timeout, rewards distributed | ~5 seconds |

## Failure Conditions

1. **Timeout** - Timer expires before completion → partial rewards
2. **Death** (conditional) - If "death penalty" modifier active → lose some loot
3. **Abandon** - All players leave before completion → no completion bonus
4. **Disconnect** - Player disconnects → auto-exit, preserves progress

## Configuration Options

Key tunable parameters (see `realms.yml`):

```yaml
realms:
  # Base duration for Realms (seconds)
  base-duration: 360  # 6 minutes

  # Drop chance for Realm Maps from normal mobs
  base-drop-chance: 0.02  # 2%

  # Drop chance from elite/boss mobs
  elite-drop-chance: 0.10  # 10%
  boss-drop-chance: 0.50   # 50%

  # Level range for map drops relative to player
  level-range:
    min: -3
    max: +3

  # Maximum modifiers per map by tier
  max-modifiers:
    common: 2
    magic: 4
    rare: 6

  # Portal timeout when no players inside
  idle-timeout: 90  # seconds
```

## Future Considerations

These features are out of scope for the initial implementation but should be considered in architecture:

- **Party Support** - Multiple players in same Realm
- **Unique Realm Maps** - Special boss encounters
- **Realm Fragments** - Combine fragments to create maps
- **Realm Atlas** - Track completion stats
- **Bonus Objectives** - Secondary objectives for extra rewards
- **Realm Mods from Bosses** - Bosses drop modifier orbs
