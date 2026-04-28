# Hytale GameplayConfig System

Use this skill when dynamically changing world gameplay rules — enabling/disabling block interactions, configuring death penalties, or setting respawn behavior. GameplayConfigs are JSON files that override the world's default rules at runtime.

> **Related skills:** For instance management, see `docs/hytale-api/instances.md`. For player death handling, see `docs/hytale-api/player-death.md`.

---

## Quick Reference

| Task | Approach |
|------|----------|
| Set gameplay rules for a world | `world.getWorldConfig().setGameplayConfig("ConfigName")` |
| Define a gameplay config | Create JSON file in `Server/GameplayConfigs/` |
| Disable block placement | `"World": { "AllowBlockPlacement": false }` |
| Make death exit instance | `"Death": { "RespawnController": { "Type": "ExitInstance" } }` |
| Configure item loss on death | `"Death": { "ItemsLossMode": "Configured", "ItemsAmountLossPercentage": 50.0 }` |

---

## Overview

A GameplayConfig is a JSON file in your mod's `Server/GameplayConfigs/` directory that defines world rules:
- Block breaking, gathering, and placement permissions
- Death penalties (item loss, durability loss)
- Respawn behavior (normal respawn vs. exit instance)

Configs inherit from a parent (typically `"Default"`) and override specific fields.

---

## JSON Structure

```json
{
  "Parent": "Default",
  "World": {
    "AllowBlockBreaking": false,
    "AllowBlockGathering": false,
    "AllowBlockPlacement": false
  },
  "Death": {
    "ItemsLossMode": "Configured",
    "ItemsAmountLossPercentage": 100.0,
    "ItemsDurabilityLossPercentage": 100.0,
    "RespawnController": {
      "Type": "ExitInstance"
    }
  }
}
```

### World Section

| Field | Type | Description |
|-------|------|-------------|
| `AllowBlockBreaking` | boolean | Whether players can break blocks |
| `AllowBlockGathering` | boolean | Whether players can gather/harvest blocks |
| `AllowBlockPlacement` | boolean | Whether players can place blocks |

### Death Section

| Field | Type | Description |
|-------|------|-------------|
| `LoseItems` | boolean | Simple toggle — lose all items on death (true/false) |
| `ItemsLossMode` | string | `"Configured"` enables percentage-based loss |
| `ItemsAmountLossPercentage` | float | Percentage of item stacks lost (0-100) |
| `ItemsDurabilityLossPercentage` | float | Percentage of durability lost (0-100) |
| `RespawnController.Type` | string | Respawn mode: `"ExitInstance"` teleports player out of instance |

**Note**: `LoseItems` and `ItemsLossMode` are mutually exclusive approaches. Use `LoseItems: true/false` for simple on/off, or `ItemsLossMode: "Configured"` with percentages for fine-grained control.

---

## Applying at Runtime

```java
import com.hypixel.hytale.server.core.universe.world.World;

// Set gameplay config by name (matches filename without .json)
world.getWorldConfig().setGameplayConfig("NoPlacement_Roguelike");
```

The config name must match a JSON file in any mod's `Server/GameplayConfigs/` directory (without the `.json` extension).

---

## Example Configs

### Dungeon — No Building, No Risk

```json
{
  "Parent": "Default",
  "World": {
    "AllowBlockBreaking": false,
    "AllowBlockGathering": false,
    "AllowBlockPlacement": false
  },
  "Death": {
    "LoseItems": false,
    "RespawnController": {
      "Type": "ExitInstance"
    }
  }
}
```

### Roguelike — No Building, Full Penalty

```json
{
  "Parent": "Default",
  "World": {
    "AllowBlockBreaking": false,
    "AllowBlockGathering": false,
    "AllowBlockPlacement": false
  },
  "Death": {
    "ItemsLossMode": "Configured",
    "ItemsAmountLossPercentage": 100.0,
    "ItemsDurabilityLossPercentage": 100.0,
    "RespawnController": {
      "Type": "ExitInstance"
    }
  }
}
```

### Creative — Full Building, Standard Death

```json
{
  "Parent": "Default",
  "World": {
    "AllowBlockBreaking": true,
    "AllowBlockGathering": true,
    "AllowBlockPlacement": true
  },
  "Death": {
    "LoseItems": true,
    "RespawnController": {
      "Type": "ExitInstance"
    }
  }
}
```

---

## File Placement

```
YourMod/
└── Server/
    └── GameplayConfigs/
        ├── MyConfig_Easy.json
        └── MyConfig_Hard.json
```

Reference as `"MyConfig_Easy"` and `"MyConfig_Hard"` in code.

---

## Key Imports

```java
import com.hypixel.hytale.server.core.universe.world.World;
// world.getWorldConfig().setGameplayConfig("ConfigName");
```

---

## Reference

- Source: Verified against Hytale server source and community mod implementations
- Related: `docs/hytale-api/instances.md` (instance system)
- Related: `docs/hytale-api/player-death.md` (death handling)

> **TrailOfOrbis project notes:** We could use GameplayConfigs in realm instances to disable block placement and configure death-exits-instance behavior, replacing manual event handling.
