# Entity Classification System

> Technical documentation for the dynamic NPC discovery and classification system.

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Classification Algorithm](#classification-algorithm)
- [Configuration](#configuration)
- [API Usage](#api-usage)
- [Extending for Custom Mods](#extending-for-custom-mods)
- [Debugging](#debugging)
- [Historical Notes](#historical-notes)

---

## Overview

The Entity Classification System automatically discovers all NPC roles registered with Hytale's `NPCPlugin` and classifies them into RPG categories. This enables:

- **Automatic mod compatibility**: New mods work without configuration changes
- **O(1) classification lookup**: After discovery, lookups are instant
- **Configurable detection**: Patterns and overrides for fine-tuning
- **Statistics tracking**: Admin commands show discovery results

### Classification Types

| `RPGMobClass` | Purpose | Gives XP | Scaled by Level |
|---------------|---------|----------|-----------------|
| `BOSS` | World/dungeon bosses | Yes (5x multiplier) | Yes |
| `ELITE` | Mini-bosses, captains | Yes (2x multiplier) | Yes |
| `HOSTILE` | Regular enemies | Yes (1x multiplier) | Yes |
| `PASSIVE` | Wildlife, NPCs, critters | No | No |

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        TrailOfOrbis Plugin                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────────┐    ┌──────────────────────────────┐   │
│  │ EntityDiscoveryConfig│───▶│    DynamicEntityRegistry     │   │
│  │   (YAML config)     │    │                              │   │
│  └─────────────────────┘    │  • discoverRoles()           │   │
│                             │  • getClassification(role)    │   │
│  ┌─────────────────────┐    │  • getStatistics()           │   │
│  │  TagLookupProvider  │───▶│                              │   │
│  │ (Group membership)  │    └──────────────────────────────┘   │
│  └─────────────────────┘              │                        │
│                                       ▼                        │
│                             ┌──────────────────┐               │
│                             │  Role Cache      │               │
│                             │  (ConcurrentMap) │               │
│                             └──────────────────┘               │
│                                       │                        │
│                                       ▼                        │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                    Consumers                            │   │
│  │  • MobScalingManager (level-based stat scaling)         │   │
│  │  • XPManager (kill XP rewards)                          │   │
│  │  • CombatManager (damage calculations)                  │   │
│  │  • RealmMobSpawner (realm mob selection)                │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

### Key Classes

| Class | Responsibility |
|-------|----------------|
| `DynamicEntityRegistry` | Discovery, classification, caching |
| `EntityDiscoveryConfig` | YAML configuration model |
| `DiscoveredRole` | Immutable data record for a discovered role |
| `RPGMobClass` | Enum defining classification types |
| `TagLookupProvider` | Interface for Hytale group membership checks |
| `HytaleTagLookupProvider` | Production implementation using `TagSetPlugin` |

---

## Classification Algorithm

Discovery runs once at server startup (on `AllNPCsLoadedEvent`) and classifies each role:

```java
for each role in NPCPlugin.getRoleTemplateNames():
    1. Check blacklist → skip if matched
    2. Get all NPCGroup memberships
    3. Classify using priority:
       a. Config overrides (bosses/elites/passive lists)
       b. Group patterns (e.g., "*/Bosses")
       c. Name patterns (e.g., "*_Boss")
       d. Direct group membership (Trork → HOSTILE)
       e. Fallback → PASSIVE
    4. Cache result for O(1) lookup
```

### Group Membership Detection

The system enumerates all NPCGroups and checks membership using `TagSetPlugin`:

```java
Set<String> groups = new HashSet<>();
for (entry : NPCGroup.getAssetMap().getAssetMap().entrySet()) {
    int groupIndex = assetMap.getIndex(groupName);
    if (TagSetPlugin.get(NPCGroup.class).tagInSet(groupIndex, roleIndex)) {
        groups.add(groupName);
    }
}
```

### Default Group Classifications

**Hostile Groups** (combat enemies):
```java
Set.of("Trork", "Goblin", "Skeleton", "Zombie", "Void", "Vermin",
       "Predators", "PredatorsBig", "Scarak", "Outlander", "Undead")
```

**Passive Groups** (non-combat):
```java
Set.of("Livestock", "Prey", "PreyBig", "Critters", "Birds", "Aquatic")
```

---

## Configuration

### entity-discovery.yml

```yaml
# Master toggle
discovery:
  enabled: true
  detect_by_name: true   # Use name pattern matching
  detect_by_group: true  # Use group pattern matching

# Role name patterns (wildcards: * and ?)
detection_patterns:
  boss:
    - "*_Boss"
    - "*Dragon*"
    - "*Lord*"
  elite:
    - "*_Captain*"
    - "*_Chieftain*"
    - "*Berserker*"

# NPCGroup name patterns
group_patterns:
  boss:
    - "*/Bosses"
    - "*/WorldBosses"
  elite:
    - "*/Elites"
    - "*/Veterans"

# Direct NPCGroup names for hostile/passive classification
classification_groups:
  hostile: []  # Empty = use defaults
  passive: []  # Empty = use defaults

# Explicit role overrides (highest priority)
overrides:
  bosses:
    - "dragon_fire"
    - "scarak_broodmother"
  elites:
    - "trork_chieftain"
    - "skeleton_pirate_captain"
  passive: []

# Exclude from discovery
blacklist:
  roles:
    - "*_Template_*"
    - "*_Debug_*"
  mods: []
```

---

## API Usage

### Getting Classification

```java
// Get the registry from the plugin
DynamicEntityRegistry registry = plugin.getDynamicEntityRegistry();

// O(1) lookup by role name (case-insensitive)
RPGMobClass mobClass = registry.getClassification("trork_warrior");
// Returns: RPGMobClass.HOSTILE

// Get full discovered role info
DiscoveredRole role = registry.getDiscoveredRole("dragon_fire");
// role.classification() → BOSS
// role.detectionMethod() → CONFIG_OVERRIDE
// role.memberGroups() → Set["Trork", "LivingWorld/Aggressive"]
// role.modSource() → "Hytale:Hytale"
```

### Getting Statistics

```java
DynamicEntityRegistry.DiscoveryStatistics stats = registry.getStatistics();

// Total roles discovered
int total = stats.totalDiscovered();  // e.g., 734

// Count by classification
Map<RPGMobClass, Integer> byClass = stats.countByClass();
// { BOSS=12, ELITE=21, HOSTILE=105, PASSIVE=596 }

// Count by detection method
Map<DetectionMethod, Integer> byMethod = stats.countByMethod();
// { CONFIG_OVERRIDE=21, NAME_PATTERN=12, GROUP_MEMBERSHIP=105, ... }

// Formatted string for display
String formatted = stats.format();
```

### Filtering Roles

```java
// Get all bosses
List<DiscoveredRole> bosses = registry.getRolesByClass(RPGMobClass.BOSS);

// Check if role exists
boolean exists = registry.hasRole("custom_mob");
```

---

## Extending for Custom Mods

### Adding a New Mod's Mobs

If a mod uses custom NPCGroup names, add them to `entity-discovery.yml`:

```yaml
classification_groups:
  hostile:
    - "MyMod_Demons"
    - "MyMod_Bandits"
    - "MyMod_Undead"
  passive:
    - "MyMod_Villagers"
    - "MyMod_Merchants"
```

### Overriding Specific Roles

For roles that pattern matching gets wrong:

```yaml
overrides:
  bosses:
    - "mymod_giant_spider"  # Force to BOSS
  passive:
    - "mymod_friendly_orc"  # Force to PASSIVE (even though "orc" might match hostile)
```

### Blacklisting a Mod

To completely exclude a mod's roles from RPG mechanics:

```yaml
blacklist:
  mods:
    - "CreativeTools"
    - "AdminNPCs"
```

---

## Debugging

### Admin Commands

```bash
# Show discovery statistics
/rpgadmin entity stats

# Output:
# === Entity Discovery Stats ===
# Discovered 734 NPC roles
#
# By Classification:
#   BOSS: 12 roles
#   ELITE: 21 roles
#   HOSTILE: 105 roles
#   PASSIVE: 596 roles
#
# By Detection Method:
#   CONFIG_OVERRIDE: 21
#   NAME_PATTERN: 12
#   GROUP_MEMBERSHIP: 105
#   ATTITUDE_FALLBACK: 596
#
# By Source:
#   Hytale:Hytale: 734 roles
```

### Server Logs

At startup, look for:
```
[INFO] [DynamicEntityRegistry] Discovering 734 NPC roles...
[INFO] [DynamicEntityRegistry] Entity discovery complete: 734 roles (BOSS=12, ELITE=21, HOSTILE=105, PASSIVE=596)
```

### Common Issues

| Symptom | Cause | Solution |
|---------|-------|----------|
| 0 HOSTILE roles | Group lookup failing | Check `classification_groups` config |
| Wrong classification | Pattern too broad | Add to `overrides` list |
| Mod roles not found | Discovery runs too early | Ensure mod loads before TrailOfOrbis |
| High PASSIVE count | Many ambient creatures | This is normal - critters are passive |

---

## Historical Notes

### The LivingWorld Meta-Group Bug (Fixed Feb 2026)

The original implementation used Hytale's `LivingWorld/*` meta-groups:

```java
// OLD CODE - BROKEN
if (tagLookupProvider.hasTag("LivingWorld/Aggressive", roleIndex)) {
    return HOSTILE;
}
if (tagLookupProvider.hasTag("LivingWorld/Neutral", roleIndex)) {
    return HOSTILE;  // BUG: Neutral = farm animals, not hostile!
}
```

**Problems:**

1. **Logic Error**: `LivingWorld/Neutral` contains `Livestock`, `Prey`, `PreyBig` - these are passive farm animals (cows, sheep, rabbits), not hostile enemies.

2. **Silent Lookup Failure**: The `TagSetPlugin.hasTag()` call for meta-groups always returned `false`, causing all mobs to fall through to PASSIVE.

**The Fix:**

Instead of relying on meta-group lookups, we now:
1. Enumerate all NPCGroup memberships for each role
2. Check if any membership matches known hostile/passive group names
3. Use direct group names (`Trork`, `Goblin`) instead of meta-groups (`LivingWorld/Aggressive`)

This approach is more reliable because `NPCGroup.getAssetMap()` and `tagInSet()` work correctly for individual groups.

---

## Performance

- **Discovery Time**: ~50-100ms for 700+ roles
- **Lookup Time**: O(1) via `ConcurrentHashMap`
- **Memory**: ~500KB for full role cache
- **Thread Safety**: All public methods are thread-safe

---

## Related Documentation

- [Mob Scaling System](./research/MobScalingResearch.md)
- [XP and Leveling](./plan/phase-leveling.md)
- [Realm Mob Spawning](../src/main/java/io/github/larsonix/trailoforbis/maps/docs/05-mob-spawning.md)
