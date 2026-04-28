# Hytale Tag System

This skill documents Hytale's hierarchical tag system and how Hyforged integrates with it for stats, items, blocks, and other assets.

## Overview

Hytale uses a **hierarchical tag system** where tags are defined as a map of categories to value arrays. This creates a rich, queryable tag structure that enables flexible asset lookups.

### Key Concepts

| Concept | Description |
|---------|-------------|
| **Tag Category** | A named group (e.g., `"Type"`, `"Element"`, `"Domain"`) |
| **Tag Value** | Values within a category (e.g., `["fire", "elemental"]`) |
| **Tag Index** | Integer index for O(1) lookups via `AssetRegistry` |
| **Tag Expansion** | Hierarchical tags expand to multiple searchable strings |

---

## JSON Format

Tags in Hytale assets use a **map structure**, not a flat array:

```json
{
  "Tags": {
    "Category1": ["value1", "value2"],
    "Category2": ["value3"]
  }
}
```

### Example: Item Tags
```json
{
  "Id": "hytale:adamantite_axe",
  "Tags": {
    "Type": ["Weapon"],
    "Family": ["Axe"]
  }
}
```

### Example: Stat Tags
```json
{
  "Id": "hyforged:fire-resistance-bps",
  "Tags": {
    "Domain": ["defense"],
    "Type": ["resistance"],
    "Element": ["fire", "elemental"],
    "Modifier": ["percent"],
    "Source": ["derived"]
  }
}
```

---

## Tag Expansion

When tags are loaded, Hytale's `AssetExtraInfo.Data.putTags()` **expands** each entry into multiple searchable tags:

| Input | Expanded Tags |
|-------|---------------|
| `"Domain": ["offense"]` | `Domain`, `offense`, `Domain=offense` |
| `"Element": ["fire", "elemental"]` | `Element`, `fire`, `elemental`, `Element=fire`, `Element=elemental` |

### Expansion Rules

For each entry `"Category": ["val1", "val2", ...]`:
1. The **category key** becomes a tag: `Category`
2. Each **value** becomes a tag: `val1`, `val2`
3. Each **category=value** combination becomes a tag: `Category=val1`, `Category=val2`

This enables flexible querying:
- `hasTag("fire")` - matches any asset with "fire" in ANY category
- `hasTag("Element=fire")` - matches only assets with `"Element": ["fire"]`
- `hasTag("Element")` - matches any asset with an Element category

---

## AssetRegistry API

Hytale's `AssetRegistry` provides the global tag index system:

### Core Methods

```java
// Get existing tag index (returns Integer.MIN_VALUE if not found)
int tagIndex = AssetRegistry.getTagIndex("fire");

// Get or create tag index (creates if not existing)
int tagIndex = AssetRegistry.getOrCreateTagIndex("fire");
```

### Integer Indices

Tags are stored as integer indices for O(1) lookups:

```java
// Fast membership test
IntSet entityTags = entity.getData().getExpandedTagIndexes();
int fireIndex = AssetRegistry.getTagIndex("fire");
if (entityTags.contains(fireIndex)) {
    // Entity has fire tag
}
```

---

## StatDefinitionRegistry Tag API

The Hyforged stat system provides convenience methods for tag queries:

### Basic Tag Methods

```java
StatDefinitionRegistry registry = StatDefinitionRegistry.get();

// Check if any stat has a tag
boolean exists = registry.hasTag("fire");

// Get stats by tag (any expanded tag)
Collection<StatDefinition> stats = registry.getStatsForTag("fire");
Set<Integer> indices = registry.getStatIndicesForTag("fire");
List<StatId> statIds = registry.getStatIdsForTag("fire");
```

### Category-Based Methods (Recommended)

For hierarchical tags, use the explicit category-based API:

```java
// Check if any stat has Type=resistance
boolean exists = registry.hasTagValue("Type", "resistance");

// Get all resistance stats
Collection<StatDefinition> resistances = registry.getStatsForTagValue("Type", "resistance");

// Get fire elemental stats
Set<Integer> fireStats = registry.getStatIndicesForTagValue("Element", "fire");

// Get all ability score stat IDs
List<StatId> abilityScores = registry.getStatIdsForTagValue("Type", "ability-score");
```

### Integer Index Methods (Performance)

For hot paths, use pre-resolved integer indices:

```java
// Resolve once, use many times
int fireTagIndex = registry.getOrCreateTagIndex("Element=fire");

// Fast O(1) lookup
IntSet stats = registry.getStatIndicesForTagIndex(fireTagIndex);
```

---

## Standard Tag Categories

### Stats

| Category | Values | Purpose |
|----------|--------|---------|
| `Domain` | `offense`, `defense`, `resource`, `utility`, `attributes` | Primary functional classification |
| `Element` | `physical`, `fire`, `cold`, `lightning`, `chaos`, `elemental` | Damage/resistance element |
| `Type` | `damage`, `resistance`, `rating`, `ability-score`, `speed`, `critical`, `ailment`, `leech`, `skill-level`, `area`, `resource` | What the stat represents |
| `Modifier` | `flat`, `percent`, `more` | How the stat value applies |
| `Source` | `derived`, `base` | Origin of the stat value |
| `Mechanic` | `attack`, `spell`, `projectile`, `melee`, `ranged`, `minion`, `aura`, `totem`, `trap` | Usage mechanism |
| `Resource` | `health`, `mana`, `stamina`, `rage` | Which resource it affects |
| `Ailment` | `bleed`, `poison`, `ignite`, `chill`, `shock`, `freeze` | Specific ailment type |
| `Weapon` | `sword`, `axe`, `mace`, `dagger`, `bow`, `crossbow`, `staff`, `unarmed` | Weapon type affinity |

### Items (Hytale Native)

| Category | Values | Purpose |
|----------|--------|---------|
| `Type` | `Weapon`, `Armor`, `Tool`, `Consumable`, `Material` | Item classification |
| `Family` | `Sword`, `Axe`, `Helmet`, `Chestplate`, etc. | Item family |
| `Material` | `Wood`, `Stone`, `Iron`, `Gold`, `Adamantite` | Material type |
| `Tier` | `Basic`, `Common`, `Rare`, `Epic`, `Legendary` | Quality tier |

---

## Implementing Tags in New Assets

### 1. Define JSON Schema with Map Codec

```java
// In your asset class
public static final AssetBuilderCodec<String, MyAsset> CODEC = AssetBuilderCodec
    .builder(MyAsset.class, MyAsset::new, ...)
    .appendInherited(
        new KeyedCodec<>("Tags", new MapCodec<>(Codec.STRING_ARRAY, HashMap::new)),
        (asset, value) -> asset.rawTags = value != null ? value : new HashMap<>(),
        asset -> asset.rawTags,
        (asset, parent) -> asset.rawTags = new HashMap<>(parent.rawTags)
    )
    .add()
    .build();

private Map<String, String[]> rawTags = new HashMap<>();
```

### 2. Expand Tags on Load

```java
public Set<String> getExpandedTags() {
    Set<String> expanded = new HashSet<>();
    for (Map.Entry<String, String[]> entry : rawTags.entrySet()) {
        String category = entry.getKey();
        expanded.add(category);
        for (String value : entry.getValue()) {
            expanded.add(value);
            expanded.add(category + "=" + value);
        }
    }
    return expanded;
}
```

### 3. Register Tags with AssetRegistry

```java
// During asset registration
for (String tag : asset.getExpandedTags()) {
    int tagIndex = AssetRegistry.getOrCreateTagIndex(tag);
    tagToAssetIndices.computeIfAbsent(tagIndex, k -> new IntOpenHashSet()).add(assetIndex);
}
```

---

## TagSetPlugin and NPCGroup

Hytale provides a **TagSet** system for grouping assets into named sets with include/exclude logic. The primary built-in implementation is `NPCGroup`, which groups NPC roles (e.g., "all Goblins", "all Predators") for use in AI, spawning, combat targeting, and mod classification.

### Architecture

```
TagSet (interface)                     TagSetPlugin (singleton)
  ├─ getIncludedTagSets()              ├─ get() → static instance
  ├─ getExcludedTagSets()              ├─ get(Class<T>) → TagSetLookup
  ├─ getIncludedTags()                 ├─ registerTagSetType(Class<T>)
  └─ getExcludedTags()                 └─ TagSetLookup
                                            ├─ tagInSet(groupIndex, roleIndex) → boolean
NPCGroup implements TagSet                  └─ getSet(groupIndex) → IntSet
  ├─ IncludeRoles (individual NPC names)
  ├─ ExcludeRoles
  ├─ IncludeGroups (other NPCGroup names)
  └─ ExcludeGroups
```

### Imports

```java
import com.hypixel.hytale.builtin.tagset.TagSetPlugin;
import com.hypixel.hytale.builtin.tagset.TagSet;
import com.hypixel.hytale.builtin.tagset.config.NPCGroup;
```

### TagSetPlugin Singleton API

`TagSetPlugin` is a built-in Hytale plugin that manages all `TagSet` types. It registers `NPCGroup` during `setup()` and provides the lookup infrastructure.

```java
// Get the plugin singleton (null if disabled or not yet initialized)
TagSetPlugin plugin = TagSetPlugin.get();

// Get the flattened lookup table for a specific TagSet type
TagSetPlugin.TagSetLookup lookup = TagSetPlugin.get(NPCGroup.class);
// Throws NullPointerException with "Class is not registered with the TagSet module!"
// if the class was never registered.
```

#### TagSetLookup Methods

| Method | Returns | Description |
|--------|---------|-------------|
| `tagInSet(int tagSet, int tagIndex)` | `boolean` | Checks if `tagIndex` (a role) is a member of `tagSet` (a group). Throws `IllegalArgumentException` if the group index does not exist. |
| `getSet(int tagSet)` | `IntSet` or `null` | Returns the full set of role indices belonging to a group, or `null` if the group does not exist. |

Both methods operate on **integer indices**, not string names. Resolve names to indices via `NPCGroup.getAssetMap()`.

### NPCGroup Class

`NPCGroup` is the concrete `TagSet` implementation for NPC role grouping. Each group is defined as a JSON file under `Server/NPC/Groups/` in any asset pack.

#### Static API

```java
// Get the asset store (registers lazily on first call)
AssetStore<String, NPCGroup, IndexedLookupTableAssetMap<String, NPCGroup>> store =
    NPCGroup.getAssetStore();

// Get the indexed asset map (for index lookups)
IndexedLookupTableAssetMap<String, NPCGroup> assetMap = NPCGroup.getAssetMap();

// Resolve a group name to its integer index
int groupIndex = assetMap.getIndex("Trork");  // returns Integer.MIN_VALUE if not found

// Get a group by index
NPCGroup group = assetMap.getAsset(groupIndex);

// Iterate all registered groups
Map<String, NPCGroup> allGroups = assetMap.getAssetMap();
```

**Index lookups are case-insensitive** (the underlying `Object2IntMap` uses `CaseInsensitiveHashStrategy`).

#### Instance Methods

| Method | Maps to JSON | Description |
|--------|--------------|-------------|
| `getId()` | (derived from file path) | Group name, e.g., `"Trork"` or `"LivingWorld/Aggressive"` |
| `getIncludedTags()` | `IncludeRoles` | NPC role names to include (supports glob patterns) |
| `getExcludedTags()` | `ExcludeRoles` | NPC role names to exclude (supports glob patterns) |
| `getIncludedTagSets()` | `IncludeGroups` | Other NPCGroup names to include (composition) |
| `getExcludedTagSets()` | `ExcludeGroups` | Other NPCGroup names to exclude (set difference) |

Note the naming mismatch: the `TagSet` interface uses generic names (`getIncludedTags`, `getIncludedTagSets`) while NPCGroup's JSON uses domain-specific names (`IncludeRoles`, `IncludeGroups`).

### NPCGroup JSON Structure

Asset path: `Server/NPC/Groups/` (schema: `NPCGroup.json`)

```json
{
  "IncludeRoles": ["Goblin_*"],
  "ExcludeRoles": ["Goblin_Duke*", "Goblin_Ogre"],
  "IncludeGroups": ["Outlander"],
  "ExcludeGroups": ["Outlander_Marauder"]
}
```

| Field | Type | Description |
|-------|------|-------------|
| `IncludeRoles` | `string[]` | NPC role names to include. Supports glob patterns (`*` wildcard). |
| `ExcludeRoles` | `string[]` | NPC role names to exclude. Applied after includes. |
| `IncludeGroups` | `string[]` | Other NPCGroup names whose members are added to this group. |
| `ExcludeGroups` | `string[]` | Other NPCGroup names whose members are removed from this group. |
| `Parent` | `string` | Inherit from another NPCGroup. |
| `Tags` | `map` | Standard tag map (same expansion rules as other assets). |

**Glob pattern support**: `IncludeRoles` and `ExcludeRoles` accept `*` wildcards via `StringUtil.isGlobMatching()`. Examples: `"Goblin_*"` matches all roles starting with `Goblin_`, `"*Chicken*"` matches any role containing `Chicken`.

**Group composition**: Groups can include other groups, enabling hierarchical meta-groups. The `TagSetLookupTable` flattens these at load time, detecting and rejecting cyclic references.

#### Vanilla Group Hierarchy (70 groups)

```
Server/NPC/Groups/
├── Aquatic.json              # Fish, jellyfish, sharks (IncludeRoles)
├── Birds.json                # All bird species (IncludeRoles)
├── Critters.json             # Frogs, geckos, mice (IncludeRoles)
├── Predators.json            # Fox, hyena, toad (IncludeRoles)
├── PredatorsBig.json         # Bear, wolf, yeti (IncludeRoles)
├── Prey.json                 # Small livestock, rabbits (IncludeRoles)
├── PreyBig.json              # Large livestock, deer (IncludeRoles)
├── Undead.json               # Skeleton + Zombie (IncludeGroups)
├── Vermin.json               # Rats, snakes, spiders (IncludeRoles)
├── Void.json                 # Void creatures (IncludeRoles)
├── Creature/                 # 26 groups: Livestock/*, Mammal/*, Mythic/*
├── Intelligent/              # 21 groups: Aggressive/{Goblin,Outlander,Scarak,Trork}
├── LivingWorld/
│   ├── Aggressive.json       # Meta-group: IncludeGroups [Trork, Goblin, Skeleton, ...]
│   ├── Neutral.json          # Meta-group: IncludeGroups [Prey, PreyBig]
│   └── Passive.json          # Meta-group: IncludeGroups [Critters, Birds, Aquatic]
└── Undead/                   # 2 groups: Skeleton, Zombie
```

The `LivingWorld/*` meta-groups compose other groups. For example, `LivingWorld/Aggressive` includes `Trork`, `Goblin`, `Skeleton`, `Void`, `Zombie`, `Vermin`, `Predators`, and `PredatorsBig` via `IncludeGroups`.

### Querying NPCGroup Membership Programmatically

The key operation is checking whether an NPC role belongs to a specific group. This requires resolving both the group name and the role name to integer indices, then querying the flattened lookup table.

```java
/**
 * Check if an NPC role belongs to a group.
 *
 * @param groupName  NPCGroup name (e.g., "Trork", "LivingWorld/Aggressive")
 * @param roleIndex  NPC role index from NPCPlugin.getIndex(roleName)
 * @return true if the role is a member of the group
 */
public boolean isRoleInGroup(String groupName, int roleIndex) {
    if (TagSetPlugin.get() == null) {
        return false; // TagSetPlugin not yet initialized
    }

    int groupIndex = NPCGroup.getAssetMap().getIndex(groupName);
    if (groupIndex < 0) {
        return false; // Group does not exist
    }

    try {
        return TagSetPlugin.get(NPCGroup.class).tagInSet(groupIndex, roleIndex);
    } catch (IllegalArgumentException e) {
        return false; // Group index not in flattened set
    }
}
```

#### Getting All Group Memberships for a Role

```java
/**
 * Find all NPCGroups that contain a given role.
 */
public Set<String> getGroupMemberships(int roleIndex) {
    Set<String> groups = new HashSet<>();
    if (TagSetPlugin.get() == null) return groups;

    var assetMap = NPCGroup.getAssetMap();
    var groupMap = assetMap.getAssetMap();
    if (groupMap == null) return groups;

    for (var entry : groupMap.entrySet()) {
        String groupName = entry.getKey();
        int groupIndex = assetMap.getIndex(groupName);
        if (groupIndex >= 0) {
            try {
                if (TagSetPlugin.get(NPCGroup.class).tagInSet(groupIndex, roleIndex)) {
                    groups.add(groupName);
                }
            } catch (Exception e) {
                // Skip groups that can't be checked
            }
        }
    }
    return groups;
}
```

#### Validating Group Names

Before using group names from config, validate they exist in the registry:

```java
var assetMap = NPCGroup.getAssetMap();
for (String name : configuredGroupNames) {
    if (assetMap.getIndex(name) < 0) {
        LOGGER.atWarning().log("NPCGroup '%s' not found in registry", name);
    }
}
```

### TrailOfOrbis Usage: Mob Classification

Our plugin uses `NPCGroup` to automatically classify NPC roles at startup for RPG stat scaling. The system lives in `mobs/classification/` and works as follows:

1. **`DynamicEntityRegistry.discoverRoles()`** scans all NPC roles via `NPCPlugin.get().getRoleTemplateNames(false)`
2. For each role, **`getGroupMemberships()`** iterates all NPCGroups and calls `TagSetPlugin.get(NPCGroup.class).tagInSet(groupIndex, roleIndex)` to find which groups the role belongs to
3. Groups are matched against configurable classification sets (hostile, passive, minor) to determine the RPG class (`BOSS`, `ELITE`, `HOSTILE`, `MINOR`, `PASSIVE`)
4. The `HytaleTagLookupProvider` wraps the `TagSetPlugin` call behind a `TagLookupProvider` interface for testability

**Key files:**
- `mobs/classification/DynamicEntityRegistry.java` -- Discovery engine and group membership resolution
- `mobs/classification/provider/HytaleTagLookupProvider.java` -- Production TagSetPlugin wrapper
- `mobs/classification/provider/TagLookupProvider.java` -- Interface for test mocking
- `mobs/classification/EntityDiscoveryConfig.java` -- Config with group name lists
- `config/entity-discovery.yml` -- Configurable hostile/passive group names

**Default classification groups:**
- **Hostile**: `Trork`, `Goblin`, `Skeleton`, `Zombie`, `Void`, `Vermin`, `Predators`, `PredatorsBig`, `Scarak`, `Outlander`, `Undead`
- **Passive**: `Prey`, `PreyBig`, `Critters`, `Birds`, `Aquatic`

These defaults mirror Hytale's `LivingWorld/Aggressive` and `LivingWorld/Passive` meta-group compositions, but reference the leaf groups directly to avoid hierarchical path matching issues.

### Edge Cases and Gotchas

- **Group IDs are hierarchical paths**: Groups in subdirectories get path-based IDs like `"Intelligent/Aggressive/Trork/Trork"` or `"LivingWorld/Aggressive"`. When matching by short name, use suffix matching (e.g., check if the ID ends with `"/Trork"` or equals `"Trork"`).
- **`TagSetPlugin.get()` returns null** before the plugin has initialized. Always null-check before use.
- **`tagInSet()` throws** `IllegalArgumentException` if the group index does not exist in the flattened lookup table. Always catch or pre-validate.
- **`getIndex()` returns `Integer.MIN_VALUE`** (not `-1`) when a key is not found. However, `getIndex()` on `IndexedLookupTableAssetMap` returns `Integer.MIN_VALUE`, so check `>= 0` to be safe.
- **Glob patterns in IncludeRoles**: `StringUtil.isGlobMatching()` handles `*` wildcards. A role like `"Pig*"` matches `Pig`, `Pig_Wild`, `Pig_Piglet`, etc.
- **Cyclic references**: The `TagSetLookupTable` detects cycles during flattening and throws `IllegalStateException`. This is handled at startup; your code does not need to worry about it.

---

## Best Practices

### DO

- ✅ Use category-based API (`getStatsForTagValue("Type", "resistance")`) for explicit queries
- ✅ Pre-resolve tag indices for hot paths
- ✅ Use consistent category names across asset types
- ✅ Document your tag categories in the asset schema
- ✅ Use IntSet for efficient tag membership tests

### DON'T

- ❌ Use flat tag arrays (`"Tags": ["fire", "damage"]`) - use hierarchical format
- ❌ Create duplicate tags across different categories with same meaning
- ❌ Store tag strings at runtime - resolve to indices
- ❌ Assume tag order matters - it doesn't

---

## Related Files

- `AssetRegistry` - Hytale's global tag index registry
- `AssetExtraInfo.Data.putTags()` - Tag expansion logic
- `StatDefinitionRegistry` - Stat-specific tag queries
- `StatDefinitionAsset` - JSON codec for stat tags
- `TagSet` (`com.hypixel.hytale.builtin.tagset.TagSet`) - Interface for tag set assets (include/exclude tags and tag sets)
- `TagSetPlugin` (`com.hypixel.hytale.builtin.tagset.TagSetPlugin`) - Singleton managing all TagSet types, provides `TagSetLookup` for membership queries
- `TagSetLookupTable` (`com.hypixel.hytale.builtin.tagset.TagSetLookupTable`) - Flattens hierarchical include/exclude definitions into `Int2ObjectMap<IntSet>` at load time
- `NPCGroup` (`com.hypixel.hytale.builtin.tagset.config.NPCGroup`) - Concrete TagSet implementation for NPC role grouping
- `IndexedLookupTableAssetMap` (`com.hypixel.hytale.assetstore.map.IndexedLookupTableAssetMap`) - Case-insensitive indexed asset map used by NPCGroup
- `StringUtil.isGlobMatching()` - Glob pattern matching for IncludeRoles/ExcludeRoles wildcards
- `Assets/Schema/NPCGroup.json` - Official JSON schema for NPCGroup assets

## ADR Reference

See ADR-0008 in `.memory_bank/ADRs.md` for the decision rationale behind adopting Hytale's tag system.
