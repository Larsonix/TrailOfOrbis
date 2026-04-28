# Hytale Reputation Skill

Use this skill when working with faction reputation in Hytale plugins. This covers the `ReputationPlugin` API for managing reputation values, ranks, attitudes, and storage modes.

> **Related skills:** For shop systems that can gate items behind reputation, see `docs/hytale-api/shop.md`. For NPC behavior templates that react to attitude, see `docs/hytale-api/npc-templates.md`. For the ECS component model, see `docs/hytale-api/ecs.md`.

---

## Quick Reference

| Task | Approach |
|------|----------|
| Get a player's reputation with an NPC | `ReputationPlugin.get().getReputationValue(store, playerRef, npcRef)` |
| Get a player's reputation with a faction | `ReputationPlugin.get().getReputationValue(store, playerRef, groupId)` |
| Change reputation (per-player mode) | `ReputationPlugin.get().changeReputation(player, groupId, value, accessor)` |
| Change reputation (per-world mode) | `ReputationPlugin.get().changeReputation(world, groupId, value)` |
| Get current rank for an NPC | `ReputationPlugin.get().getReputationRank(store, playerRef, npcRef)` |
| Get attitude toward a player | `ReputationPlugin.get().getAttitude(store, playerRef, npcRef)` |
| Convert raw value to rank | `ReputationPlugin.get().getReputationRankFromValue(value)` |
| Gate shop items by reputation | Use `ChoiceRequirement` type `"Reputation"` in shop JSON |

---

## Key Classes

### Imports

```java
// Plugin entry point
import com.hypixel.hytale.builtin.adventure.reputation.ReputationPlugin;

// Reputation assets
import com.hypixel.hytale.builtin.adventure.reputation.assets.ReputationRank;
import com.hypixel.hytale.builtin.adventure.reputation.assets.ReputationGroup;

// Attitude enum
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;

// Config (storage mode)
import com.hypixel.hytale.builtin.adventure.reputation.ReputationGameplayConfig;

// ECS fundamentals
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
```

---

## Attitude Enum

The `Attitude` enum represents how an NPC faction feels about a player:

| Value | Description |
|-------|-------------|
| `IGNORE` | "is ignoring the target" — No interaction |
| `HOSTILE` | "is hostile towards the target" — Attacks on sight |
| `NEUTRAL` | "is neutral towards the target" — Default stance |
| `FRIENDLY` | "is friendly towards the target" — Positive interactions |
| `REVERED` | "reveres the target" — Highest standing |

Each `ReputationRank` maps to an `Attitude`. NPCs use this to determine AI behavior (attack, ignore, offer quests, etc.).

---

## Storage Modes

Reputation can be stored in two ways, configured per-world via `GameplayConfig`:

| Mode | Enum Value | Description |
|------|------------|-------------|
| **Per-Player** | `ReputationStorageType.PerPlayer` | Each player has independent reputation with each faction. Stored in `PlayerConfigData`. **Default.** |
| **Per-World** | `ReputationStorageType.PerWorld` | All players share a single reputation value per faction. Stored in `ReputationDataResource` (world-level ECS resource). |

### Configuring Storage Mode

Set via `GameplayConfig` JSON in your world/instance config:

```json
{
  "PluginConfig": {
    "Reputation": {
      "ReputationStorage": "PerPlayer"
    }
  }
}
```

### Checking Storage Mode in Code

```java
World world = store.getExternalData().getWorld();
ReputationGameplayConfig config = ReputationGameplayConfig.getOrDefault(world.getGameplayConfig());
ReputationGameplayConfig.ReputationStorageType mode = config.getReputationStorageType();
```

---

## ReputationPlugin API

`ReputationPlugin` is the singleton accessed via `ReputationPlugin.get()`. All methods automatically respect the world's configured storage mode.

### Changing Reputation

| Method | Returns | Description |
|--------|---------|-------------|
| `changeReputation(Player, Ref<EntityStore> npcRef, int value, ComponentAccessor)` | `int` | Change reputation for a player toward the NPC's faction. Returns new value. |
| `changeReputation(Player, String groupId, int value, ComponentAccessor)` | `int` | Change reputation for a player toward a specific faction by ID. Returns new value. |
| `changeReputation(World, String groupId, int value)` | `int` | Change world-shared reputation (PerWorld mode only). Returns new value or `-1` if wrong mode. |

**Return values:**
- Returns the **new** reputation value after the change
- Returns `Integer.MIN_VALUE` if the NPC has no reputation group or the group ID doesn't exist
- Returns `-1` if calling world-mode method in per-player mode

**Clamping:** Values are automatically clamped to `[minReputationValue, maxReputationValue - 1]` based on the loaded `ReputationRank` assets.

### Reading Reputation

| Method | Returns | Description |
|--------|---------|-------------|
| `getReputationValue(Store, Ref playerRef, Ref npcRef)` | `int` | Player's reputation via NPC's faction (PerPlayer) |
| `getReputationValue(Store, Ref playerRef, String groupId)` | `int` | Player's reputation with a faction (PerPlayer) |
| `getReputationValue(Store, Ref npcRef)` | `int` | World reputation via NPC's faction (PerWorld) |
| `getReputationValue(Store, String groupId)` | `int` | World reputation with a faction (PerWorld) |

**Default:** If a player/world has no stored reputation for a group, returns `ReputationGroup.getInitialReputationValue()`.

**Sentinel:** Returns `Integer.MIN_VALUE` if the NPC has no `ReputationGroupComponent` or the group ID doesn't exist.

### Reading Rank and Attitude

| Method | Returns | Description |
|--------|---------|-------------|
| `getReputationRank(Store, Ref playerRef, Ref npcRef)` | `ReputationRank` or `null` | Rank for player vs NPC's faction |
| `getReputationRank(Store, Ref playerRef, String groupId)` | `ReputationRank` or `null` | Rank for player vs faction |
| `getReputationRankFromValue(int value)` | `ReputationRank` or `null` | Convert raw value to rank |
| `getReputationRank(Store, Ref npcRef)` | `ReputationRank` or `null` | World-mode rank via NPC |
| `getAttitude(Store, Ref playerRef, Ref npcRef)` | `Attitude` or `null` | How the NPC's faction sees the player |
| `getAttitude(Store, Ref npcRef)` | `Attitude` or `null` | World-mode attitude |

---

## ReputationRank Asset

Ranks define named tiers of reputation with value ranges and attitudes. Loaded from `NPC/Reputation/Ranks/`.

### JSON Format

```json
{
  "MinValue": -100,
  "MaxValue": -50,
  "Attitude": "HOSTILE"
}
```

### Properties

| Property | Type | Description |
|----------|------|-------------|
| `MinValue` | `int` | Minimum reputation value (inclusive) for this rank |
| `MaxValue` | `int` | Maximum reputation value (exclusive) for this rank |
| `Attitude` | `Attitude` | The attitude mapped to this rank |

### Key Methods

| Method | Returns | Description |
|--------|---------|-------------|
| `getId()` | `String` | Rank ID (from filename) |
| `getMinValue()` | `int` | Inclusive lower bound |
| `getMaxValue()` | `int` | Exclusive upper bound |
| `getAttitude()` | `Attitude` | Mapped attitude |
| `containsValue(int)` | `boolean` | Whether `value >= min && value < max` |

### Validation

- `MinValue` must be strictly less than `MaxValue`
- Ranks are sorted by `MinValue` at startup
- Warnings are logged for gaps between ranks (e.g., rank A max=50, rank B min=55 → gap 50-55)
- Warnings are logged for overlapping ranges

### Accessing the Asset Store

```java
DefaultAssetMap<String, ReputationRank> ranks = ReputationRank.getAssetMap();
ReputationRank hatedRank = ranks.getAsset("Hated");
```

---

## ReputationGroup Asset

Groups define which NPC factions share reputation. Loaded from `NPC/Reputation/Groups/`.

### JSON Format

```json
{
  "NPCGroups": ["Trork", "Trork_Warriors"],
  "InitialReputationValue": 0
}
```

### Properties

| Property | Type | Description |
|----------|------|-------------|
| `NPCGroups` | `String[]` | NPC group names that belong to this reputation group |
| `InitialReputationValue` | `int` | Starting reputation value for new players/worlds |

### Key Methods

| Method | Returns | Description |
|--------|---------|-------------|
| `getId()` | `String` | Group ID (from filename) |
| `getNpcGroups()` | `String[]` | Associated NPC group names |
| `getInitialReputationValue()` | `int` | Default starting value |

---

## ReputationGroupComponent

An ECS component attached to NPC entities that links them to a reputation group. Registered by `ReputationPlugin` during setup.

```java
// Get the component type
ComponentType<EntityStore, ReputationGroupComponent> type =
    ReputationPlugin.get().getReputationGroupComponentType();

// Read from an NPC entity
ReputationGroupComponent repComp = store.getComponent(npcRef, type);
String groupId = repComp.getReputationGroupId();
```

---

## Code Examples

### Changing Reputation on Mob Kill

```java
public void onMobKilled(Player player, Ref<EntityStore> npcRef, ComponentAccessor<EntityStore> accessor) {
    ReputationPlugin rep = ReputationPlugin.get();

    // Decrease reputation with the killed mob's faction
    int newValue = rep.changeReputation(player, npcRef, -10, accessor);
    if (newValue == Integer.MIN_VALUE) {
        // NPC has no reputation group — nothing changed
        return;
    }

    // Check new rank
    ReputationRank rank = rep.getReputationRankFromValue(newValue);
    if (rank != null && rank.getAttitude() == Attitude.HOSTILE) {
        PlayerRef playerRef = accessor.getComponent(
            player.getReference(), PlayerRef.getComponentType());
        playerRef.sendMessage(Message.raw("The " + /* faction name */ " now despise you!"));
    }
}
```

### Checking Reputation Before Granting Access

```java
public boolean canAccessVendor(Store<EntityStore> store, Ref<EntityStore> playerRef, String factionGroupId) {
    ReputationPlugin rep = ReputationPlugin.get();
    Attitude attitude = null;

    ReputationRank rank = rep.getReputationRank(store, playerRef, factionGroupId);
    if (rank != null) {
        attitude = rank.getAttitude();
    }

    // Only FRIENDLY or REVERED players can use this vendor
    return attitude == Attitude.FRIENDLY || attitude == Attitude.REVERED;
}
```

### Reading Attitude for NPC Dialogue Branching

```java
public String getDialogueKey(Store<EntityStore> store, Ref<EntityStore> playerRef, Ref<EntityStore> npcRef) {
    Attitude attitude = ReputationPlugin.get().getAttitude(store, playerRef, npcRef);
    if (attitude == null) {
        return "dialogue.default";
    }

    return switch (attitude) {
        case HOSTILE -> "dialogue.hostile";
        case IGNORE -> "dialogue.ignore";
        case NEUTRAL -> "dialogue.neutral";
        case FRIENDLY -> "dialogue.friendly";
        case REVERED -> "dialogue.revered";
    };
}
```

---

## Best Practices

1. **Check for `Integer.MIN_VALUE`**: This is the sentinel value returned when a reputation group doesn't exist or an NPC has no `ReputationGroupComponent`. Always check before using the returned value.

2. **Storage mode matters**: Always consider which mode the world uses. Per-player methods return `Integer.MIN_VALUE` in per-world mode and vice versa. The `changeReputation(Player, groupId, value, accessor)` overload handles both modes transparently.

3. **Ranks must cover the value range**: If there are gaps between rank value ranges, some reputation values won't map to any rank (`getReputationRankFromValue` returns `null`). Ensure your rank assets cover the full expected range.

4. **Initial value placement**: Set `InitialReputationValue` in `ReputationGroup` to place new players at the desired starting rank (e.g., 0 for neutral).

5. **Reputation is per-group, not per-NPC**: All NPCs in the same `ReputationGroup` share reputation. Killing one Trork affects standing with all Trorks in that group.

6. **Shop integration**: The `ReputationPlugin` registers a `"Reputation"` `ChoiceRequirement` type. This allows shop items to be gated by reputation rank in JSON shop assets.

---

## Integration Notes (TrailOfOrbis)

Potential uses for TrailOfOrbis:

- **Faction standings**: Track player reputation with various NPC factions, affecting vendor access and NPC hostility
- **Reputation-gated vendors**: Use the built-in `ChoiceRequirement` "Reputation" type to gate shop items
- **Kill-based reputation changes**: Decrease reputation when players kill faction members, increase on quest completion
- **Realm factions**: Use per-world mode in realm instances for shared party reputation
- **Attitude-based NPC behavior**: Drive NPC AI dialogue and combat behavior based on reputation ranks

---

## Resources

- Reputation ranks are loaded from `NPC/Reputation/Ranks/` in your asset pack
- Reputation groups are loaded from `NPC/Reputation/Groups/` in your asset pack
- Storage mode is set via `GameplayConfig.PluginConfig.Reputation.ReputationStorage`
