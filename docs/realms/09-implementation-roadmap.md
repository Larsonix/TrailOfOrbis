# Implementation Roadmap v2.0

> **Complete implementation guide for the Realms system.**
> Covers all components from documentation 01-08 with full dependency mapping.

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Master File List](#2-master-file-list)
3. [Phase 0: Prerequisites & Assets](#3-phase-0-prerequisites--assets)
4. [Phase 1: Foundation](#4-phase-1-foundation)
5. [Phase 2: Stone System Integration](#5-phase-2-stone-system-integration)
6. [Phase 3: Instance Management](#6-phase-3-instance-management)
7. [Phase 4: Gameplay Systems](#7-phase-4-gameplay-systems)
8. [Phase 5: Map Item & Drops](#8-phase-5-map-item--drops)
9. [Phase 6: UI & Polish](#9-phase-6-ui--polish)
10. [Phase 7: Database & Analytics](#10-phase-7-database--analytics)
11. [Phase 8: Testing & Validation](#11-phase-8-testing--validation)
12. [Dependency Graph](#12-dependency-graph)
13. [Risk Register](#13-risk-register)

---

## 1. Executive Summary

### Scope
Complete implementation of the Realms system including:
- Realm Map items with modifiers
- Instance creation and lifecycle management
- Monster spawning and completion tracking
- Reward distribution with IIQ/IIR bonuses
- Stone crafting system for maps
- UI, tooltips, and progress display
- Database persistence and analytics

### Metrics

| Metric | Value |
|--------|-------|
| **Total Files** | 67 Java + 6 YAML + 44 Asset Templates |
| **Estimated Hours** | 120-150 hours |
| **Phases** | 8 (including Phase 0) |
| **Testing Milestones** | 8 |

### Documentation Coverage

| Doc | Title | Status |
|-----|-------|--------|
| 01 | Overview | ✅ Fully covered |
| 02 | Realm Maps | ✅ Fully covered |
| 03 | Modifiers | ✅ Fully covered |
| 04 | World Generation | ✅ Fully covered (template-based) |
| 05 | Mob Spawning | ✅ Fully covered |
| 06 | Currency/Stones | ✅ Fully covered |
| 07 | Architecture | ✅ Fully covered |
| 08 | API Verification | ✅ Incorporated |

---

## 2. Master File List

### 2.1 Java Source Files (67 files)

```
src/main/java/io/github/larsonix/trailoforbis/

├── stones/                                    # PHASE 2: Shared stone interfaces
│   ├── ModifiableItem.java                    # [2.1] Sealed interface
│   ├── ModifiableItemBuilder.java             # [2.2] Sealed builder interface
│   ├── ItemModifier.java                      # [2.3] Common modifier interface
│   ├── ItemTargetType.java                    # [2.4] GEAR_ONLY, MAP_ONLY, BOTH
│   ├── StoneType.java                         # [2.5] All stone definitions
│   ├── StoneActions.java                      # [2.6] Stone effect implementations
│   ├── StoneService.java                      # [2.7] Public stone API
│   ├── StoneResult.java                       # [2.8] Sealed result type
│   └── StoneDropListener.java                 # [2.9] Stone drops from mobs
│
├── gear/model/                                # PHASE 2: Gear modifications
│   ├── GearData.java                          # [2.10] Add: implements ModifiableItem
│   └── GearModifier.java                      # [2.11] Add: implements ItemModifier
│
└── maps/                                      # PHASES 1, 3-7: Realms system
    │
    ├── RealmsManager.java                     # [3.10] Main coordinator
    │
    ├── api/                                   # Public API
    │   ├── RealmsService.java                 # [3.11] Service interface
    │   └── events/                            # [3.12] Event classes
    │       ├── RealmEvent.java                # Base event class
    │       ├── RealmCreatedEvent.java
    │       ├── RealmReadyEvent.java
    │       ├── RealmEnteredEvent.java
    │       ├── RealmExitedEvent.java
    │       ├── RealmCompletedEvent.java
    │       └── RealmClosedEvent.java
    │
    ├── config/                                # Configuration
    │   ├── RealmsConfig.java                  # [1.8] Main configuration POJO
    │   ├── RealmsConfigLoader.java            # [1.9] YAML loader
    │   ├── ModifierConfig.java                # [1.10] Modifier pool config
    │   └── MobPoolConfig.java                 # [1.11] Mob pool config
    │
    ├── core/                                  # Core data structures
    │   ├── RealmBiomeType.java                # [1.1] Biome enum
    │   ├── RealmLayoutSize.java               # [1.2] Size enum
    │   ├── RealmLayoutShape.java              # [1.3] Shape enum
    │   ├── RealmState.java                    # [1.4] Lifecycle state enum
    │   ├── RealmMapData.java                  # [1.7] Map item data record
    │   ├── RealmInstance.java                 # [3.5] Active realm state
    │   └── RealmCompletionTracker.java        # [4.3] Kill/progress tracking
    │
    ├── modifiers/                             # Modifier system
    │   ├── RealmModifierType.java             # [1.5] Modifier type enum
    │   ├── RealmModifier.java                 # [1.6] Modifier record
    │   ├── RealmModifierPool.java             # [4.1] Weighted selection
    │   └── RealmModifierRoller.java           # [4.2] Value rolling logic
    │
    ├── templates/                             # Template management
    │   ├── RealmTemplateRegistry.java         # [3.1] Biome→template mapping
    │   ├── RealmTemplate.java                 # [3.2] Template metadata
    │   └── MonsterSpawnPoint.java             # [3.3] Spawn point data
    │
    ├── instance/                              # Instance lifecycle
    │   ├── RealmInstanceFactory.java          # [3.4] Creates instances
    │   ├── RealmPortalManager.java            # [3.6] Portal block handling
    │   ├── RealmTeleportHandler.java          # [3.7] Player teleportation
    │   ├── RealmRemovalHandler.java           # [3.8] Cleanup logic
    │   └── RealmRemovalCondition.java         # [3.9] Custom removal condition
    │
    ├── components/                            # ECS Components
    │   ├── RealmMobComponent.java             # [4.4] Marks mobs in realm
    │   ├── RealmPlayerComponent.java          # [4.5] Marks players in realm
    │   └── RealmMapComponent.java             # [5.1] Item component for maps
    │
    ├── spawning/                              # Monster spawning
    │   ├── RealmMobSpawner.java               # [4.6] Spawns monsters
    │   ├── RealmMobPool.java                  # [4.7] Biome mob pools
    │   ├── RealmMobCalculator.java            # [4.8] Count calculations
    │   ├── BiomeMobPool.java                  # [4.9] Per-biome pool
    │   └── WeightedMob.java                   # [4.10] Weighted mob entry
    │
    ├── systems/                               # ECS Systems
    │   ├── RealmTimerSystem.java              # [4.11] Timeout handling
    │   ├── RealmProgressSystem.java           # [6.3] Progress updates
    │   └── RealmModifierSystem.java           # [4.12] Apply modifiers to mobs
    │
    ├── listeners/                             # Event listeners
    │   ├── RealmMobDeathListener.java         # [4.13] Track kills
    │   ├── RealmMapDropListener.java          # [5.2] Map drops from mobs
    │   ├── RealmMapUseListener.java           # [5.3] Map item activation
    │   ├── RealmEntryListener.java            # [5.4] Portal entry
    │   ├── RealmExitListener.java             # [5.5] Exit/disconnect
    │   └── RealmPlayerDeathListener.java      # [5.6] Player death handling
    │
    ├── rewards/                               # Reward system
    │   ├── RealmRewardCalculator.java         # [4.14] Calculate rewards
    │   ├── RealmRewardDistributor.java        # [4.15] Distribute to players
    │   └── RealmLootIntegration.java          # [4.16] IIQ/IIR hooks
    │
    ├── items/                                 # Map item handling
    │   ├── RealmMapItem.java                  # [5.7] Item type definition
    │   ├── RealmMapGenerator.java             # [5.8] Generate new maps
    │   └── RealmMapUtils.java                 # [5.9] ItemStack read/write
    │
    ├── stones/                                # Map-specific stones
    │   └── MapStoneActions.java               # [5.10] Cartographer, Horizon, Explorer
    │
    ├── ui/                                    # User interface
    │   ├── RealmProgressUI.java               # [6.1] In-realm progress display
    │   ├── RealmMapTooltipBuilder.java        # [6.2] Map item tooltips
    │   └── RealmCompletionUI.java             # [6.4] Completion screen
    │
    ├── integration/                           # System integrations
    │   ├── RealmLevelingIntegration.java      # [4.17] XP bonus application
    │   ├── RealmScalingIntegration.java       # [4.18] Fixed mob levels
    │   └── RealmLootIntegration.java          # [4.16] Loot bonuses (same as rewards)
    │
    └── database/                              # Persistence
        ├── RealmCompletionDAO.java            # [7.1] Completion records
        ├── RealmStatisticsDAO.java            # [7.2] Aggregate stats
        └── RealmDatabaseSchema.java           # [7.3] Table definitions
```

### 2.2 Configuration Files (6 files)

```
src/main/resources/config/
├── realms.yml                                 # [1.12] Main realm configuration
├── realm-modifiers.yml                        # [1.13] Modifier pool definitions
├── realm-mobs.yml                             # [1.14] Mob pool per biome
├── realm-templates.yml                        # [1.15] Template overrides
├── realm-rewards.yml                          # [1.16] Reward configuration
└── realm-stones.yml                           # [1.17] Stone drop rates
```

### 2.3 Asset Templates (44 templates)

```
Assets/Server/Instances/
├── realm_forest_small/
│   ├── instance.bson
│   └── worldgen/
├── realm_forest_medium/
├── realm_forest_large/
├── realm_forest_massive/
├── realm_desert_small/
├── realm_desert_medium/
├── realm_desert_large/
├── realm_desert_massive/
├── realm_volcano_small/
│   ... (11 biomes × 4 sizes = 44 templates)
└── realm_corrupted_massive/
```

### 2.4 Test Files (18 files)

```
src/test/java/io/github/larsonix/trailoforbis/maps/
├── core/
│   ├── RealmMapDataTest.java
│   ├── RealmInstanceTest.java
│   └── RealmCompletionTrackerTest.java
├── modifiers/
│   ├── RealmModifierPoolTest.java
│   └── RealmModifierRollerTest.java
├── spawning/
│   ├── RealmMobCalculatorTest.java
│   └── RealmMobPoolTest.java
├── rewards/
│   └── RealmRewardCalculatorTest.java
├── items/
│   ├── RealmMapGeneratorTest.java
│   └── RealmMapUtilsTest.java
├── config/
│   └── RealmsConfigLoaderTest.java
└── integration/
    ├── RealmLifecycleIntegrationTest.java
    ├── RealmCompletionIntegrationTest.java
    └── RealmStoneIntegrationTest.java
```

---

## 3. Phase 0: Prerequisites & Assets

> **Goal:** Prepare shared interfaces and create asset templates before coding.

### 0.1 Verify Existing Dependencies

**Check these exist and understand their APIs:**

| File | Package | Required For |
|------|---------|--------------|
| `GearRarity.java` | `gear/model/` | Rarity tiers for maps |
| `GearData.java` | `gear/model/` | Will implement ModifiableItem |
| `GearModifier.java` | `gear/model/` | Will implement ItemModifier |
| `ConfigManager.java` | `config/` | Loading YAML configs |
| `DataManager.java` | `database/` | Database operations |
| `LevelingManager.java` | `leveling/` | XP integration |
| `MobScalingManager.java` | `mobs/` | Mob level scaling |
| `TrailOfOrbis.java` | root | Plugin lifecycle |

**Action:** Read each file, note public APIs and extension points.

---

### 0.2 Design Asset Template Structure

**Create template specification document:**

```
realm_{biome}_{size}/
├── instance.bson                 # WorldConfig
│   ├── uuid: auto-generated
│   ├── displayName: "Realm - {Biome} ({Size})"
│   ├── seed: parameterized
│   ├── deleteOnRemove: true
│   ├── isTicking: true
│   ├── isPvpEnabled: true
│   ├── isSpawningNPC: false      # We spawn manually
│   └── pluginConfig:
│       └── InstanceWorldConfig:
│           ├── removalConditions: []  # Set at runtime
│           └── preventReconnection: false
│
└── worldgen/
    ├── ChunkGenerator.json       # Points to arena generator
    ├── Density/
    │   └── arena_terrain.json    # Flat arena with optional features
    ├── Biomes/
    │   └── arena_biome.json      # Biome-specific materials
    ├── MaterialProviders/
    │   └── arena_materials.json  # Floor, walls materials
    └── Props/
        └── arena_props.json      # Decorative objects
```

**Arena Layout:**
```
     ┌──────────── BOUNDARY WALL (Barrier blocks, height 20) ────────────┐
     │                                                                    │
     │    ┌─────────────────────────────────────────────────────────┐    │
     │    │                                                         │    │
     │    │                    PLAYABLE ARENA                       │    │
     │    │                                                         │    │
     │    │              (Flat floor with biome features)           │    │
     │    │                                                         │    │
     │    │    S = Spawn points (pre-defined in template)           │    │
     │    │    P = Player spawn (center)                            │    │
     │    │    E = Exit portal location                             │    │
     │    │                                                         │    │
     │    └─────────────────────────────────────────────────────────┘    │
     │                                                                    │
     └────────────────────────────────────────────────────────────────────┘
                              (Void below floor)
```

---

### 0.3 Create First Test Template

**Steps:**
1. Create `Assets/Server/Instances/realm_forest_medium/` directory
2. Create `instance.bson` with base configuration
3. Create worldgen assets for flat arena with forest floor
4. Add barrier walls around perimeter
5. Define spawn points in template metadata
6. Test loading in Hytale server

**Validation Criteria:**
- [ ] Instance spawns without errors
- [ ] Player can be teleported in
- [ ] Arena has correct dimensions (100×100 for medium)
- [ ] Walls prevent escape
- [ ] Floor uses correct biome materials

---

### 0.4 Document Template Creation Process

**Create:** `docs/10-template-creation-guide.md`

Contents:
- Step-by-step template creation
- WorldGen JSON schema reference
- Biome-specific material mappings
- Automation scripts for batch creation

---

### Phase 0 Deliverables

| ID | Deliverable | Type | Est. Hours |
|----|-------------|------|------------|
| 0.1 | Dependency audit document | Doc | 2h |
| 0.2 | Template specification | Doc | 3h |
| 0.3 | `realm_forest_medium` template | Asset | 6h |
| 0.4 | Template creation guide | Doc | 4h |
| **Total** | | | **15h** |

### Phase 0 Exit Criteria

- [ ] All existing dependencies documented
- [ ] Template structure finalized
- [ ] One working template validated in-game
- [ ] Template creation process documented

---

## 4. Phase 1: Foundation

> **Goal:** Create all core data structures with no external dependencies.

### 1.1 RealmBiomeType

**File:** `maps/core/RealmBiomeType.java`

```java
public enum RealmBiomeType {
    FOREST("Forest", "realm_forest", 0x228B22),
    DESERT("Desert", "realm_desert", 0xEDC9AF),
    VOLCANO("Volcano", "realm_volcano", 0x8B0000),
    TUNDRA("Tundra", "realm_tundra", 0xE0FFFF),
    SWAMP("Swamp", "realm_swamp", 0x2F4F4F),
    MOUNTAINS("Mountains", "realm_mountains", 0x708090),
    BEACH("Beach", "realm_beach", 0xFAF0E6),
    JUNGLE("Jungle", "realm_jungle", 0x006400),
    CAVERNS("Caverns", "realm_caverns", 0x36454F),
    VOID("Void", "realm_void", 0x1a1a2e),
    CORRUPTED("Corrupted", "realm_corrupted", 0x4B0082);

    private final String displayName;
    private final String templatePrefix;
    private final int themeColor;

    // Constructor, getters
    // Codec for serialization
    public static final Codec<RealmBiomeType> CODEC = ...;
}
```

**Dependencies:** None
**Est. Hours:** 1h

---

### 1.2 RealmLayoutSize

**File:** `maps/core/RealmLayoutSize.java`

```java
public enum RealmLayoutSize {
    SMALL("Small", 50, 30, 0.75f, 0),
    MEDIUM("Medium", 100, 50, 1.0f, 1),
    LARGE("Large", 200, 80, 1.5f, 2),
    MASSIVE("Massive", 400, 120, 2.5f, 3);

    private final String displayName;
    private final int arenaRadius;           // Blocks
    private final int baseMonsterCount;      // Before multipliers
    private final float monsterMultiplier;   // Applied to base
    private final int guaranteedBosses;      // Minimum boss spawns

    // Constructor, getters, Codec
}
```

**Dependencies:** None
**Est. Hours:** 1h

---

### 1.3 RealmLayoutShape

**File:** `maps/core/RealmLayoutShape.java`

```java
public enum RealmLayoutShape {
    CIRCULAR("Circular"),
    RECTANGULAR("Rectangular"),
    IRREGULAR("Irregular");

    private final String displayName;
    // Constructor, getters, Codec
}
```

**Dependencies:** None
**Est. Hours:** 0.5h

---

### 1.4 RealmState

**File:** `maps/core/RealmState.java`

```java
public enum RealmState {
    CREATING("Creating", false, false),      // Instance spawning
    READY("Ready", true, false),             // Awaiting player entry
    ACTIVE("Active", true, true),            // Players inside, combat
    ENDING("Ending", true, false),           // Completed, grace period
    CLOSING("Closing", false, false);        // Being removed

    private final String displayName;
    private final boolean allowsEntry;       // Can players enter?
    private final boolean isCombatActive;    // Are mobs fighting?

    // Constructor, getters
}
```

**Dependencies:** None
**Est. Hours:** 0.5h

---

### 1.5 RealmModifierType

**File:** `maps/modifiers/RealmModifierType.java`

```java
public enum RealmModifierType {
    // === REWARD MODIFIERS (positive for player) ===
    ITEM_QUANTITY("Item Quantity", "+%d%% Item Quantity", Category.REWARD, 0),
    ITEM_RARITY("Item Rarity", "+%d%% Item Rarity", Category.REWARD, 0),
    EXPERIENCE_BONUS("Experience", "+%d%% Experience", Category.REWARD, 0),
    STONE_DROP_BONUS("Stone Drops", "+%d%% Stone Drop Chance", Category.REWARD, 0),

    // === MONSTER MODIFIERS (increases difficulty) ===
    MONSTER_DAMAGE("Monster Damage", "Monsters deal %d%% increased damage", Category.MONSTER, 1),
    MONSTER_HEALTH("Monster Health", "Monsters have %d%% increased health", Category.MONSTER, 1),
    MONSTER_SPEED("Monster Speed", "Monsters have %d%% increased speed", Category.MONSTER, 1),
    MONSTER_ATTACK_SPEED("Attack Speed", "Monsters attack %d%% faster", Category.MONSTER, 1),
    EXTRA_MONSTERS("Extra Monsters", "%d%% increased monster count", Category.MONSTER, 2),
    ELITE_CHANCE("Elite Monsters", "+%d%% chance for elite spawns", Category.MONSTER, 2),

    // === ENVIRONMENT MODIFIERS ===
    REDUCED_VISIBILITY("Darkness", "Reduced visibility", Category.ENVIRONMENT, 1),
    DAMAGE_OVER_TIME("Toxic Air", "Take %d damage per second", Category.ENVIRONMENT, 2),
    REDUCED_HEALING("Cursed Ground", "%d%% reduced healing effectiveness", Category.ENVIRONMENT, 1),
    NO_REGENERATION("Bleeding", "Life regeneration disabled", Category.ENVIRONMENT, 3),

    // === SPECIAL/CORRUPTION MODIFIERS ===
    TEMPORAL_CHAINS("Temporal Chains", "Cannot use movement abilities", Category.SPECIAL, 2),
    REFLECT_DAMAGE("Thorns", "Monsters reflect %d%% of damage", Category.SPECIAL, 2),
    LIFE_LEECH("Vampiric", "Monsters heal for %d%% of damage dealt", Category.SPECIAL, 2),
    ENRAGE_ON_LOW("Enrage", "Monsters enrage below %d%% health", Category.SPECIAL, 1);

    private final String displayName;
    private final String formatPattern;
    private final Category category;
    private final int difficultyWeight;      // Contribution to difficulty rating

    public enum Category { REWARD, MONSTER, ENVIRONMENT, SPECIAL }

    // formatValue(double value) method
    // Codec
}
```

**Dependencies:** None
**Est. Hours:** 2h

---

### 1.6 RealmModifier

**File:** `maps/modifiers/RealmModifier.java`

```java
public record RealmModifier(
    String id,
    String displayName,
    RealmModifierType type,
    double value,
    int difficultyContribution,
    boolean locked
) implements ItemModifier {

    // Convenience constructor (locked = false)
    public RealmModifier(String id, String displayName, RealmModifierType type,
                         double value, int difficultyContribution) {
        this(id, displayName, type, value, difficultyContribution, false);
    }

    // === ItemModifier implementation ===

    @Override
    public boolean isLocked() { return locked; }

    @Override
    public ItemModifier withLocked(boolean locked) {
        return new RealmModifier(id, displayName, type, value, difficultyContribution, locked);
    }

    @Override
    public ItemModifier withValue(double newValue) {
        return new RealmModifier(id, displayName, type, newValue, difficultyContribution, locked);
    }

    // formatForTooltip()
    // Codec
}
```

**Dependencies:**
- `stones/ItemModifier.java` (interface, created in Phase 2)
- 1.5 RealmModifierType

**Est. Hours:** 2h

---

### 1.7 RealmMapData

**File:** `maps/core/RealmMapData.java`

```java
public record RealmMapData(
    UUID mapId,
    int level,
    GearRarity rarity,
    int quality,
    RealmBiomeType biomeType,
    RealmLayoutShape layoutShape,
    RealmLayoutSize layoutSize,
    List<RealmModifier> modifiers,
    int mapQuantityBonus,
    boolean corrupted
) implements ModifiableItem {

    // === CONSTANTS ===
    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 10000;
    public static final int MIN_QUALITY = 1;
    public static final int MAX_QUALITY = 100;
    public static final int PERFECT_QUALITY = 101;
    public static final int MAX_MAP_QUANTITY_BONUS = 20;

    // === COMPACT CONSTRUCTOR (validation) ===
    public RealmMapData {
        Objects.requireNonNull(mapId, "Map ID cannot be null");
        // ... all validation from doc 06
        modifiers = List.copyOf(modifiers);
    }

    // === ModifiableItem IMPLEMENTATION ===
    @Override public int level() { return level; }
    @Override public GearRarity rarity() { return rarity; }
    @Override public int quality() { return quality; }
    @Override public boolean isCorrupted() { return corrupted; }
    @Override public List<? extends ItemModifier> modifiers() { return modifiers; }
    @Override public boolean hasUnlockedModifiers() { ... }
    @Override public int unlockedModifierCount() { ... }
    @Override public int mapQuantityBonus() { return mapQuantityBonus; }
    @Override public ModifiableItemBuilder toModifiableBuilder() { return toBuilder(); }

    // === COMPUTED PROPERTIES ===
    public float totalItemQuantity() { ... }
    public float totalItemRarity() { ... }
    public float totalXpBonus() { ... }
    public int difficultyRating() { ... }

    // === BUILDER ===
    public Builder toBuilder() { ... }
    public static Builder builder() { return new Builder(); }

    public static final class Builder implements ModifiableItemBuilder {
        // All fields with defaults
        // All ModifiableItemBuilder methods
        // Map-specific builder methods
        public RealmMapData build() { ... }
    }

    // === CODEC ===
    public static final Codec<RealmMapData> CODEC = ...;
}
```

**Dependencies:**
- `gear/model/GearRarity.java` (existing)
- `stones/ModifiableItem.java` (interface, created in Phase 2)
- `stones/ModifiableItemBuilder.java` (interface, created in Phase 2)
- 1.5 RealmModifierType
- 1.6 RealmModifier
- 1.1-1.3 Enums

**Est. Hours:** 5h

---

### 1.8-1.11 Configuration Classes

**Files:**
- `maps/config/RealmsConfig.java`
- `maps/config/RealmsConfigLoader.java`
- `maps/config/ModifierConfig.java`
- `maps/config/MobPoolConfig.java`

```java
// RealmsConfig.java
public record RealmsConfig(
    boolean enabled,
    InstanceConfig instance,
    DropConfig drops,
    ModifierConfig modifiers,
    MobPoolConfig mobPools,
    RewardConfig rewards
) {
    public record InstanceConfig(
        int idleTimeoutSeconds,
        int maxDurationSeconds,
        int completionGraceSeconds,
        int portalTimeoutSeconds
    ) {}

    public record DropConfig(
        double baseChance,
        double levelScaling,
        double bossMultiplier,
        Map<GearRarity, Double> rarityWeights
    ) {}

    // ... other nested configs
}
```

**Dependencies:**
- `config/ConfigManager.java` (existing)
- 1.1-1.5 Enums

**Est. Hours:** 4h

---

### 1.12-1.17 Configuration YAML Files

**Files:**
- `realms.yml` - Main configuration
- `realm-modifiers.yml` - Modifier pool definitions
- `realm-mobs.yml` - Mob pools per biome
- `realm-templates.yml` - Template overrides
- `realm-rewards.yml` - Reward configuration
- `realm-stones.yml` - Stone drop rates

**See detailed schemas in separate section below.**

**Est. Hours:** 4h

---

### Phase 1 Deliverables

| ID | File | Type | Dependencies | Est. Hours |
|----|------|------|--------------|------------|
| 1.1 | RealmBiomeType.java | Enum | None | 1h |
| 1.2 | RealmLayoutSize.java | Enum | None | 1h |
| 1.3 | RealmLayoutShape.java | Enum | None | 0.5h |
| 1.4 | RealmState.java | Enum | None | 0.5h |
| 1.5 | RealmModifierType.java | Enum | None | 2h |
| 1.6 | RealmModifier.java | Record | 1.5, ItemModifier | 2h |
| 1.7 | RealmMapData.java | Record | 1.1-1.6, ModifiableItem | 5h |
| 1.8 | RealmsConfig.java | POJO | 1.1-1.5 | 2h |
| 1.9 | RealmsConfigLoader.java | Loader | 1.8 | 1h |
| 1.10 | ModifierConfig.java | POJO | 1.5 | 0.5h |
| 1.11 | MobPoolConfig.java | POJO | 1.1 | 0.5h |
| 1.12 | realms.yml | YAML | - | 1h |
| 1.13 | realm-modifiers.yml | YAML | - | 1h |
| 1.14 | realm-mobs.yml | YAML | - | 1h |
| 1.15 | realm-templates.yml | YAML | - | 0.5h |
| 1.16 | realm-rewards.yml | YAML | - | 0.5h |
| 1.17 | realm-stones.yml | YAML | - | 0.5h |
| **Total** | | | | **20h** |

### Phase 1 Exit Criteria

- [ ] All enums compile and have Codecs
- [ ] RealmMapData builds and validates correctly
- [ ] Configuration loads from YAML without errors
- [ ] Unit tests pass for RealmMapData validation

---

## 5. Phase 2: Stone System Integration

> **Goal:** Create shared stone interfaces and integrate with existing gear system.

### 2.1-2.8 Stone Package (Shared)

**Files:**
- `stones/ModifiableItem.java` - Sealed interface
- `stones/ModifiableItemBuilder.java` - Sealed builder interface
- `stones/ItemModifier.java` - Common modifier interface
- `stones/ItemTargetType.java` - Target type enum
- `stones/StoneType.java` - All stone definitions
- `stones/StoneActions.java` - Effect implementations
- `stones/StoneService.java` - Public API
- `stones/StoneResult.java` - Sealed result type

**Implementation:** Follow documentation 06-currency.md exactly.

**Dependencies:**
- `gear/model/GearRarity.java` (existing)

**Est. Hours:** 12h total

---

### 2.9 StoneDropListener

**File:** `stones/StoneDropListener.java`

```java
public class StoneDropListener {
    // Handles stone drops from mob kills
    // Uses realm IIQ/IIR bonuses when in realm
}
```

**Dependencies:**
- 2.5 StoneType
- RealmsManager (for realm bonuses)

**Est. Hours:** 2h

---

### 2.10-2.11 Gear Modifications

**Files:**
- `gear/model/GearData.java` - Add `implements ModifiableItem`, `corrupted` field
- `gear/model/GearModifier.java` - Add `implements ItemModifier`, `locked` field

**Changes documented in 06-currency.md.**

**Est. Hours:** 3h

---

### Phase 2 Deliverables

| ID | File | Type | Dependencies | Est. Hours |
|----|------|------|--------------|------------|
| 2.1 | ModifiableItem.java | Interface | None | 2h |
| 2.2 | ModifiableItemBuilder.java | Interface | None | 1h |
| 2.3 | ItemModifier.java | Interface | None | 1h |
| 2.4 | ItemTargetType.java | Enum | None | 0.5h |
| 2.5 | StoneType.java | Enum | 2.1-2.4 | 3h |
| 2.6 | StoneActions.java | Utility | 2.1-2.5 | 4h |
| 2.7 | StoneService.java | Service | 2.5, 2.6 | 2h |
| 2.8 | StoneResult.java | Sealed | None | 0.5h |
| 2.9 | StoneDropListener.java | Listener | 2.5 | 2h |
| 2.10 | GearData.java (modify) | Record | 2.1, 2.2 | 2h |
| 2.11 | GearModifier.java (modify) | Record | 2.3 | 1h |
| **Total** | | | | **19h** |

### Phase 2 Exit Criteria

- [ ] ModifiableItem sealed interface compiles
- [ ] GearData and RealmMapData both implement ModifiableItem
- [ ] StoneService can apply stones to both gear and maps
- [ ] All stone actions work correctly
- [ ] Unit tests pass for stone operations

---

## 6. Phase 3: Instance Management

> **Goal:** Implement instance creation, portals, and teleportation using Hytale APIs.

### 3.1 RealmTemplateRegistry

**File:** `maps/templates/RealmTemplateRegistry.java`

```java
public class RealmTemplateRegistry {
    private final Map<String, String> templateMap = new HashMap<>();
    private final Map<String, RealmTemplate> templateMetadata = new HashMap<>();

    public void loadFromConfig(RealmsConfig config) { ... }
    public String getTemplateName(RealmBiomeType biome, RealmLayoutSize size) { ... }
    public boolean templateExists(String templateName) { ... }
    public RealmTemplate getTemplateMetadata(String templateName) { ... }
}
```

**Dependencies:**
- 1.1 RealmBiomeType
- 1.2 RealmLayoutSize
- 1.8 RealmsConfig
- Hytale: `InstancesPlugin.doesInstanceAssetExist()`

**Est. Hours:** 2h

---

### 3.2 RealmTemplate

**File:** `maps/templates/RealmTemplate.java`

```java
public record RealmTemplate(
    String templateName,
    RealmBiomeType biome,
    RealmLayoutSize size,
    int estimatedMonsters,
    List<MonsterSpawnPoint> spawnPoints,
    Transform playerSpawn,
    Transform exitPortalLocation
) { }
```

**Dependencies:**
- 1.1, 1.2 Enums
- 3.3 MonsterSpawnPoint

**Est. Hours:** 1h

---

### 3.3 MonsterSpawnPoint

**File:** `maps/templates/MonsterSpawnPoint.java`

```java
public record MonsterSpawnPoint(
    Vector3d position,
    float radius,
    SpawnType type,          // NORMAL, ELITE, BOSS, PACK
    int maxCount
) {
    public enum SpawnType { NORMAL, ELITE, BOSS, PACK }
}
```

**Dependencies:** None (uses Hytale Vector3d)

**Est. Hours:** 0.5h

---

### 3.4 RealmInstanceFactory

**File:** `maps/instance/RealmInstanceFactory.java`

```java
public class RealmInstanceFactory {
    private final RealmTemplateRegistry templateRegistry;
    private final RealmsConfig config;
    private final RealmMobSpawner mobSpawner;

    public CompletableFuture<RealmInstance> createRealm(
            RealmMapData mapData,
            UUID openerId,
            World sourceWorld,
            Transform returnPoint) {

        // 1. Get template name
        String templateName = templateRegistry.getTemplateName(
            mapData.biomeType(), mapData.layoutSize());

        // 2. Spawn instance via InstancesPlugin
        return InstancesPlugin.get()
            .spawnInstance(templateName, sourceWorld, returnPoint)
            .thenApply(world -> {
                // 3. Configure removal conditions
                configureRemovalConditions(world);

                // 4. Create RealmInstance
                RealmInstance realm = new RealmInstance(
                    mapData.mapId(), world, mapData, openerId);

                // 5. Spawn monsters
                int totalMonsters = mobSpawner.spawnMonstersForRealm(
                    world, mapData, templateRegistry.getTemplateMetadata(templateName));

                // 6. Initialize completion tracker
                realm.initializeTracker(totalMonsters);

                return realm;
            });
    }

    private void configureRemovalConditions(World world) {
        InstanceWorldConfig instanceConfig =
            InstanceWorldConfig.ensureAndGet(world.getWorldConfig());

        instanceConfig.setRemovalConditions(
            new WorldEmptyCondition(config.instance().idleTimeoutSeconds()),
            new TimeoutCondition(config.instance().maxDurationSeconds())
        );
    }
}
```

**Dependencies:**
- 3.1 RealmTemplateRegistry
- 1.7 RealmMapData
- 1.8 RealmsConfig
- 4.6 RealmMobSpawner
- Hytale: `InstancesPlugin`, `InstanceWorldConfig`, `RemovalCondition`

**Est. Hours:** 5h

---

### 3.5 RealmInstance

**File:** `maps/core/RealmInstance.java`

```java
public class RealmInstance {
    private final UUID mapId;
    private final World world;
    private final RealmMapData mapData;
    private final UUID openerId;
    private final Instant createdAt;
    private final Set<UUID> playersInRealm = ConcurrentHashMap.newKeySet();

    private volatile RealmState state = RealmState.CREATING;
    private volatile Instant expiresAt;
    private volatile RealmCompletionTracker completionTracker;

    // Constructor
    // State transitions
    // Player tracking
    // Time remaining calculations
}
```

**Dependencies:**
- 1.4 RealmState
- 1.7 RealmMapData
- 4.3 RealmCompletionTracker
- Hytale: `World`

**Est. Hours:** 4h

---

### 3.6 RealmPortalManager

**File:** `maps/instance/RealmPortalManager.java`

```java
public class RealmPortalManager {

    public void createPortalForRealm(World sourceWorld, Vector3i position,
                                      RealmInstance realm) {
        // Set block to portal type
        // Configure PortalDevice component with destination
    }

    public void removePortal(World world, Vector3i position) {
        // Remove portal block
    }

    public Optional<RealmInstance> getRealmForPortal(World world, Vector3i position) {
        // Look up realm from portal device
    }
}
```

**Dependencies:**
- 3.5 RealmInstance
- Hytale: `PortalDevice`, `BlockModule`

**Est. Hours:** 3h

---

### 3.7 RealmTeleportHandler

**File:** `maps/instance/RealmTeleportHandler.java`

```java
public class RealmTeleportHandler {

    public void teleportToRealm(PlayerRef player, RealmInstance realm) {
        InstancesPlugin.teleportPlayerToInstance(
            player.getRef(),
            player.getStore(),
            realm.getWorld(),
            null
        );
    }

    public void teleportFromRealm(PlayerRef player) {
        InstancesPlugin.exitInstance(player.getRef(), player.getStore());
    }

    public void teleportToLoadingRealm(PlayerRef player,
                                        CompletableFuture<World> worldFuture) {
        InstancesPlugin.teleportPlayerToLoadingInstance(
            player.getRef(),
            player.getStore(),
            worldFuture,
            null
        );
    }
}
```

**Dependencies:**
- 3.5 RealmInstance
- Hytale: `InstancesPlugin`, `PlayerRef`

**Est. Hours:** 2h

---

### 3.8 RealmRemovalHandler

**File:** `maps/instance/RealmRemovalHandler.java`

```java
public class RealmRemovalHandler {

    public void triggerRemoval(RealmInstance realm) {
        InstancesPlugin.safeRemoveInstance(realm.getWorld().getName());
    }

    public void forceRemoval(RealmInstance realm) {
        Universe.get().removeWorld(realm.getWorld().getName());
    }
}
```

**Dependencies:**
- 3.5 RealmInstance
- Hytale: `InstancesPlugin`, `Universe`

**Est. Hours:** 1h

---

### 3.9 RealmRemovalCondition (Optional)

**File:** `maps/instance/RealmRemovalCondition.java`

```java
public class RealmRemovalCondition implements RemovalCondition {
    private final UUID realmId;
    private final RealmsManager realmsManager;

    @Override
    public boolean shouldRemoveWorld(Store<ChunkStore> store) {
        // Custom logic: check if realm is completed and grace period elapsed
        RealmInstance realm = realmsManager.getRealmById(realmId).orElse(null);
        if (realm == null) return true;
        return realm.getState() == RealmState.CLOSING;
    }
}
```

**Dependencies:**
- 3.5 RealmInstance
- 3.10 RealmsManager
- Hytale: `RemovalCondition`

**Est. Hours:** 1h

---

### 3.10 RealmsManager

**File:** `maps/RealmsManager.java`

```java
public class RealmsManager implements RealmsService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final TrailOfOrbis plugin;
    private final RealmsConfig config;
    private final RealmTemplateRegistry templateRegistry;
    private final RealmInstanceFactory instanceFactory;
    private final RealmPortalManager portalManager;
    private final RealmTeleportHandler teleportHandler;
    private final RealmRemovalHandler removalHandler;

    private final Map<UUID, RealmInstance> activeRealms = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerToRealm = new ConcurrentHashMap<>();

    // === LIFECYCLE ===
    public void setup() { ... }      // Register ECS components
    public void start() { ... }      // Load configs, templates
    public void shutdown() { ... }   // Close all realms

    // === PUBLIC API (RealmsService) ===
    public CompletableFuture<RealmInstance> createRealm(RealmMapData mapData, PlayerRef opener) { ... }
    public void enterRealm(PlayerRef player, RealmInstance realm) { ... }
    public void exitRealm(PlayerRef player) { ... }
    public void triggerCompletion(RealmInstance realm) { ... }
    public Optional<RealmInstance> getRealmById(UUID mapId) { ... }
    public Optional<RealmInstance> getPlayerRealm(UUID playerId) { ... }
    public boolean isPlayerInRealm(UUID playerId) { ... }

    // === INTERNAL ===
    private void registerListeners() { ... }
    private void registerComponents() { ... }
    private void fireEvent(RealmEvent event) { ... }
}
```

**Dependencies:**
- All 3.1-3.9 components
- 1.7 RealmMapData
- 1.8 RealmsConfig
- 3.11 RealmsService
- 3.12 Events
- `TrailOfOrbis.java` (main plugin)

**Est. Hours:** 8h

---

### 3.11 RealmsService

**File:** `maps/api/RealmsService.java`

```java
public interface RealmsService {
    CompletableFuture<RealmInstance> createRealm(RealmMapData mapData, PlayerRef opener);
    void enterRealm(PlayerRef player, RealmInstance realm);
    void exitRealm(PlayerRef player);
    void triggerCompletion(RealmInstance realm);
    Optional<RealmInstance> getRealmById(UUID mapId);
    Optional<RealmInstance> getPlayerRealm(UUID playerId);
    boolean isPlayerInRealm(UUID playerId);
}
```

**Dependencies:** None (interface)

**Est. Hours:** 0.5h

---

### 3.12 Events

**Files:** `maps/api/events/`
- `RealmEvent.java` (base)
- `RealmCreatedEvent.java`
- `RealmReadyEvent.java`
- `RealmEnteredEvent.java`
- `RealmExitedEvent.java`
- `RealmCompletedEvent.java`
- `RealmClosedEvent.java`

```java
public abstract class RealmEvent {
    private final RealmInstance realm;
    private final Instant timestamp;
    // ...
}

public class RealmCreatedEvent extends RealmEvent {
    private final UUID openerId;
    // ...
}
// ... etc
```

**Dependencies:**
- 3.5 RealmInstance

**Est. Hours:** 2h

---

### Phase 3 Deliverables

| ID | File | Type | Dependencies | Est. Hours |
|----|------|------|--------------|------------|
| 3.1 | RealmTemplateRegistry.java | Registry | 1.1, 1.2, 1.8 | 2h |
| 3.2 | RealmTemplate.java | Record | 3.3 | 1h |
| 3.3 | MonsterSpawnPoint.java | Record | None | 0.5h |
| 3.4 | RealmInstanceFactory.java | Factory | 3.1, 3.5, 4.6 | 5h |
| 3.5 | RealmInstance.java | Class | 1.4, 1.7, 4.3 | 4h |
| 3.6 | RealmPortalManager.java | Manager | 3.5 | 3h |
| 3.7 | RealmTeleportHandler.java | Handler | 3.5 | 2h |
| 3.8 | RealmRemovalHandler.java | Handler | 3.5 | 1h |
| 3.9 | RealmRemovalCondition.java | Condition | 3.5, 3.10 | 1h |
| 3.10 | RealmsManager.java | Manager | 3.1-3.9, 3.11, 3.12 | 8h |
| 3.11 | RealmsService.java | Interface | None | 0.5h |
| 3.12 | Events (7 files) | Events | 3.5 | 2h |
| **Total** | | | | **30h** |

### Phase 3 Exit Criteria

- [ ] RealmsManager initializes without errors
- [ ] Instance spawns from template
- [ ] Portal block created successfully
- [ ] Player can teleport in and out
- [ ] Instance auto-removes when empty
- [ ] Events fire correctly

---

## 7. Phase 4: Gameplay Systems

> **Goal:** Implement mob spawning, completion tracking, rewards, and system integrations.

### 4.1-4.2 Modifier Pool & Roller

**Files:**
- `maps/modifiers/RealmModifierPool.java`
- `maps/modifiers/RealmModifierRoller.java`

```java
public class RealmModifierPool {
    private final Map<RealmModifierType, ModifierDefinition> definitions;

    public RealmModifier rollModifier(int level, GearRarity rarity, Random random) { ... }
    public List<RealmModifier> rollModifiers(int count, int level, GearRarity rarity, Random random) { ... }
    public double rerollValue(RealmModifier modifier, int level, GearRarity rarity, Random random) { ... }
    public RealmModifier rollCorruptedModifier(int level, Random random) { ... }
}

public class RealmModifierRoller {
    public double rollValue(ModifierDefinition def, int level, GearRarity rarity, Random random) {
        // Apply level floor
        // Apply rarity bonus
        // Roll within range
    }
}
```

**Dependencies:**
- 1.5 RealmModifierType
- 1.6 RealmModifier
- 1.10 ModifierConfig

**Est. Hours:** 4h

---

### 4.3 RealmCompletionTracker

**File:** `maps/core/RealmCompletionTracker.java`

```java
public class RealmCompletionTracker {
    private final UUID realmId;
    private final AtomicInteger totalMonsters;
    private final AtomicInteger remainingMonsters;
    private final AtomicInteger killedByPlayers;
    private final Set<UUID> participatingPlayers = ConcurrentHashMap.newKeySet();
    private final Instant startTime;
    private volatile boolean completed;

    public void onMonsterKilled(UUID killerId) { ... }
    public float getCompletionProgress() { ... }
    public int getRemainingMonsters() { ... }
    public boolean isCompleted() { ... }
    public Duration getElapsedTime() { ... }
}
```

**Dependencies:** None

**Est. Hours:** 2h

---

### 4.4-4.5 ECS Components

**Files:**
- `maps/components/RealmMobComponent.java`
- `maps/components/RealmPlayerComponent.java`

```java
public record RealmMobComponent(
    UUID realmId,
    int level,
    MobClassification classification    // NORMAL, ELITE, BOSS
) implements Component<EntityStore> {
    public static ComponentType<EntityStore, RealmMobComponent> TYPE;
    public static final Codec<RealmMobComponent> CODEC = ...;

    public enum MobClassification { NORMAL, ELITE, BOSS }
}

public record RealmPlayerComponent(
    UUID realmId,
    Instant enteredAt
) implements Component<EntityStore> {
    public static ComponentType<EntityStore, RealmPlayerComponent> TYPE;
    public static final Codec<RealmPlayerComponent> CODEC = ...;
}
```

**Dependencies:**
- Hytale: `Component`, `ComponentType`, `Codec`

**Est. Hours:** 2h

---

### 4.6-4.10 Mob Spawning System

**Files:**
- `maps/spawning/RealmMobSpawner.java`
- `maps/spawning/RealmMobPool.java`
- `maps/spawning/RealmMobCalculator.java`
- `maps/spawning/BiomeMobPool.java`
- `maps/spawning/WeightedMob.java`

**Implementation follows 05-mob-spawning.md exactly.**

**Dependencies:**
- 1.7 RealmMapData
- 3.2 RealmTemplate
- 4.4 RealmMobComponent
- 1.11 MobPoolConfig
- Hytale: `CommandBuffer`, `Store`, `Holder`

**Est. Hours:** 8h

---

### 4.11-4.12 ECS Systems

**Files:**
- `maps/systems/RealmTimerSystem.java`
- `maps/systems/RealmModifierSystem.java`

```java
public class RealmTimerSystem extends TickingSystem<ChunkStore> {
    @Override
    public void tick(float dt, int systemIndex, Store<ChunkStore> store) {
        // Check realm timeouts
        // Trigger completion grace period end
        // Fire expiration events
    }
}

public class RealmModifierSystem extends EntityTickingSystem<EntityStore> {
    @Override
    public void tick(float dt, int index, ArchetypeChunk chunk, Store store, CommandBuffer cmd) {
        // Apply environmental modifiers (DoT, reduced healing, etc.)
    }
}
```

**Dependencies:**
- 3.10 RealmsManager
- 3.5 RealmInstance
- 4.5 RealmPlayerComponent
- Hytale: `TickingSystem`, `EntityTickingSystem`

**Est. Hours:** 4h

---

### 4.13 RealmMobDeathListener

**File:** `maps/listeners/RealmMobDeathListener.java`

```java
public class RealmMobDeathListener extends DeathSystems.OnDeathSystem {
    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.of(RealmMobComponent.TYPE);
    }

    @Override
    public void onComponentAdded(Ref<EntityStore> ref, DeathComponent death,
                                  Store<EntityStore> store, CommandBuffer<EntityStore> cmd) {
        // Get RealmMobComponent
        // Update completion tracker
        // Award XP with realm bonus
        // Calculate loot with IIQ/IIR
    }
}
```

**Dependencies:**
- 4.4 RealmMobComponent
- 4.3 RealmCompletionTracker
- 3.10 RealmsManager
- 4.16 RealmLootIntegration
- 4.17 RealmLevelingIntegration
- Hytale: `DeathSystems.OnDeathSystem`

**Est. Hours:** 4h

---

### 4.14-4.16 Reward System

**Files:**
- `maps/rewards/RealmRewardCalculator.java`
- `maps/rewards/RealmRewardDistributor.java`
- `maps/rewards/RealmLootIntegration.java`

```java
public class RealmRewardCalculator {
    public RealmRewards calculateCompletionRewards(RealmInstance realm) {
        // Base rewards from rarity/level
        // Bonus from completion time
        // Apply IIQ/IIR from map modifiers
        // Calculate XP bonus
    }
}

public class RealmRewardDistributor {
    public void distributeRewards(RealmInstance realm, RealmRewards rewards) {
        // Split among participating players
        // Award items, XP, currency
        // Fire events
    }
}

public class RealmLootIntegration {
    public float getIIQBonus(UUID playerId) {
        // Get bonus from player's current realm
    }

    public float getIIRBonus(UUID playerId) {
        // Get bonus from player's current realm
    }
}
```

**Dependencies:**
- 3.5 RealmInstance
- 1.7 RealmMapData
- 4.3 RealmCompletionTracker
- `leveling/LevelingManager.java` (existing)

**Est. Hours:** 6h

---

### 4.17-4.18 System Integrations

**Files:**
- `maps/integration/RealmLevelingIntegration.java`
- `maps/integration/RealmScalingIntegration.java`

```java
public class RealmLevelingIntegration {
    private final LevelingManager levelingManager;
    private final RealmsManager realmsManager;

    public void awardXpWithRealmBonus(UUID playerId, int baseXp) {
        Optional<RealmInstance> realm = realmsManager.getPlayerRealm(playerId);
        float bonus = realm.map(r -> r.getMapData().totalXpBonus()).orElse(0f);
        int finalXp = Math.round(baseXp * (1 + bonus));
        levelingManager.addXp(playerId, finalXp, XpSource.REALM_KILL);
    }
}

public class RealmScalingIntegration {
    // Hook into MobScalingManager for fixed-level mobs in realms
}
```

**Dependencies:**
- 3.10 RealmsManager
- `leveling/LevelingManager.java` (existing)
- `mobs/MobScalingManager.java` (existing)

**Est. Hours:** 3h

---

### Phase 4 Deliverables

| ID | File | Type | Dependencies | Est. Hours |
|----|------|------|--------------|------------|
| 4.1 | RealmModifierPool.java | Pool | 1.5, 1.6, 1.10 | 3h |
| 4.2 | RealmModifierRoller.java | Utility | 4.1 | 1h |
| 4.3 | RealmCompletionTracker.java | Tracker | None | 2h |
| 4.4 | RealmMobComponent.java | Component | Hytale | 1h |
| 4.5 | RealmPlayerComponent.java | Component | Hytale | 1h |
| 4.6 | RealmMobSpawner.java | Spawner | 1.7, 3.2, 4.4 | 4h |
| 4.7 | RealmMobPool.java | Pool | 1.11 | 2h |
| 4.8 | RealmMobCalculator.java | Calculator | 1.7 | 1h |
| 4.9 | BiomeMobPool.java | Pool | 4.10 | 0.5h |
| 4.10 | WeightedMob.java | Record | None | 0.5h |
| 4.11 | RealmTimerSystem.java | System | 3.5, 3.10 | 2h |
| 4.12 | RealmModifierSystem.java | System | 4.5 | 2h |
| 4.13 | RealmMobDeathListener.java | Listener | 4.3, 4.4 | 4h |
| 4.14 | RealmRewardCalculator.java | Calculator | 3.5 | 2h |
| 4.15 | RealmRewardDistributor.java | Distributor | 4.14 | 2h |
| 4.16 | RealmLootIntegration.java | Integration | 3.10 | 2h |
| 4.17 | RealmLevelingIntegration.java | Integration | 3.10 | 1.5h |
| 4.18 | RealmScalingIntegration.java | Integration | 3.10 | 1.5h |
| **Total** | | | | **33h** |

### Phase 4 Exit Criteria

- [ ] Monsters spawn at correct positions
- [ ] Mob count matches calculations
- [ ] Deaths are tracked correctly
- [ ] Completion triggers at 0 remaining
- [ ] XP awarded with realm bonus
- [ ] Loot drops with IIQ/IIR applied
- [ ] Timer system fires events correctly

---

## 8. Phase 5: Map Item & Drops

> **Goal:** Implement map items, drops, activation, and stone actions.

### 5.1 RealmMapComponent

**File:** `maps/components/RealmMapComponent.java`

```java
public record RealmMapComponent(
    RealmMapData mapData
) implements Component<EntityStore> {
    public static ComponentType<EntityStore, RealmMapComponent> TYPE;
    public static final Codec<RealmMapComponent> CODEC = ...;
}
```

**Dependencies:**
- 1.7 RealmMapData

**Est. Hours:** 1h

---

### 5.2 RealmMapDropListener

**File:** `maps/listeners/RealmMapDropListener.java`

```java
public class RealmMapDropListener {
    private final RealmsConfig config;
    private final RealmMapGenerator mapGenerator;

    public void onMobDeath(Ref<EntityStore> mob, UUID killerId, int mobLevel) {
        // Calculate drop chance
        // Roll for drop
        // Generate map with appropriate level/rarity
        // Spawn item entity
    }
}
```

**Dependencies:**
- 1.8 RealmsConfig
- 5.8 RealmMapGenerator
- 4.16 RealmLootIntegration

**Est. Hours:** 3h

---

### 5.3 RealmMapUseListener

**File:** `maps/listeners/RealmMapUseListener.java`

```java
public class RealmMapUseListener {
    private final RealmsManager realmsManager;

    public void onItemUse(PlayerRef player, ItemStack item) {
        // Extract RealmMapComponent
        // Validate player can activate
        // Create realm
        // Consume map item
        // Create portal
    }
}
```

**Dependencies:**
- 5.1 RealmMapComponent
- 3.10 RealmsManager

**Est. Hours:** 3h

---

### 5.4-5.6 Entry/Exit Listeners

**Files:**
- `maps/listeners/RealmEntryListener.java`
- `maps/listeners/RealmExitListener.java`
- `maps/listeners/RealmPlayerDeathListener.java`

```java
public class RealmEntryListener {
    // Handle player entering via portal
    // Add RealmPlayerComponent
    // Update realm player tracking
    // Fire RealmEnteredEvent
}

public class RealmExitListener {
    // Handle player exit/disconnect
    // Remove RealmPlayerComponent
    // Update realm player tracking
    // Fire RealmExitedEvent
    // Check if realm should close
}

public class RealmPlayerDeathListener {
    // Handle player death in realm
    // Option: respawn in realm or exit
}
```

**Dependencies:**
- 3.10 RealmsManager
- 4.5 RealmPlayerComponent
- 3.12 Events

**Est. Hours:** 4h

---

### 5.7 RealmMapItem

**File:** `maps/items/RealmMapItem.java`

```java
public class RealmMapItem {
    // Item type registration
    // Tooltip provider
    // Use handler registration
}
```

**Dependencies:**
- 5.1 RealmMapComponent
- Hytale: Item registration API

**Est. Hours:** 2h

---

### 5.8 RealmMapGenerator

**File:** `maps/items/RealmMapGenerator.java`

```java
public class RealmMapGenerator {
    private final RealmModifierPool modifierPool;
    private final RealmsConfig config;

    public RealmMapData generateMap(int level, @Nullable GearRarity forcedRarity) {
        // Roll rarity if not forced
        // Select random biome
        // Select random size
        // Roll modifiers based on rarity
        // Roll quality
        // Create RealmMapData
    }
}
```

**Dependencies:**
- 1.7 RealmMapData
- 4.1 RealmModifierPool
- 1.8 RealmsConfig

**Est. Hours:** 3h

---

### 5.9 RealmMapUtils

**File:** `maps/items/RealmMapUtils.java`

```java
public final class RealmMapUtils {
    public static Optional<RealmMapData> readMapData(ItemStack item) { ... }
    public static ItemStack setMapData(ItemStack item, RealmMapData data) { ... }
    public static ItemStack createMapItem(RealmMapData data) { ... }
}
```

**Dependencies:**
- 1.7 RealmMapData
- 5.1 RealmMapComponent
- Hytale: `ItemStack`

**Est. Hours:** 2h

---

### 5.10 MapStoneActions

**File:** `maps/stones/MapStoneActions.java`

```java
public final class MapStoneActions {

    // Cartographer's Stone: +5% IIQ (max 20%)
    public static RealmMapData applyCartographer(RealmMapData map) {
        int newBonus = Math.min(map.mapQuantityBonus() + 5, 20);
        return map.toBuilder().mapQuantityBonus(newBonus).build();
    }

    // Horizon Stone: Change biome
    public static RealmMapData applyHorizon(RealmMapData map, Random random) {
        RealmBiomeType[] biomes = RealmBiomeType.values();
        RealmBiomeType newBiome;
        do {
            newBiome = biomes[random.nextInt(biomes.length)];
        } while (newBiome == map.biomeType());
        return map.toBuilder().biomeType(newBiome).build();
    }

    // Explorer's Stone: Level ±3
    public static RealmMapData applyExplorer(RealmMapData map, Random random) {
        int delta = random.nextInt(-3, 4);
        int newLevel = Math.clamp(map.level() + delta, 1, 10000);
        return map.toBuilder().level(newLevel).build();
    }
}
```

**Dependencies:**
- 1.7 RealmMapData
- 1.1 RealmBiomeType

**Est. Hours:** 2h

---

### Phase 5 Deliverables

| ID | File | Type | Dependencies | Est. Hours |
|----|------|------|--------------|------------|
| 5.1 | RealmMapComponent.java | Component | 1.7 | 1h |
| 5.2 | RealmMapDropListener.java | Listener | 5.8 | 3h |
| 5.3 | RealmMapUseListener.java | Listener | 3.10, 5.1 | 3h |
| 5.4 | RealmEntryListener.java | Listener | 3.10, 4.5 | 1.5h |
| 5.5 | RealmExitListener.java | Listener | 3.10, 4.5 | 1.5h |
| 5.6 | RealmPlayerDeathListener.java | Listener | 3.10 | 1h |
| 5.7 | RealmMapItem.java | Item | 5.1 | 2h |
| 5.8 | RealmMapGenerator.java | Generator | 1.7, 4.1 | 3h |
| 5.9 | RealmMapUtils.java | Utility | 1.7, 5.1 | 2h |
| 5.10 | MapStoneActions.java | Actions | 1.7 | 2h |
| **Total** | | | | **20h** |

### Phase 5 Exit Criteria

- [ ] Maps drop from mobs
- [ ] Map item shows correct tooltip
- [ ] Right-click activates map
- [ ] Portal spawns after activation
- [ ] Map is consumed on use
- [ ] Stone actions work on maps

---

## 9. Phase 6: UI & Polish

> **Goal:** Implement user interfaces and visual feedback.

### 6.1 RealmProgressUI

**File:** `maps/ui/RealmProgressUI.java`

```java
public class RealmProgressUI {
    public void showProgress(PlayerRef player, RealmCompletionTracker tracker) {
        // Display progress bar
        // Show monsters remaining
        // Show time remaining
    }

    public void hideProgress(PlayerRef player) { ... }
}
```

**Dependencies:**
- 4.3 RealmCompletionTracker
- Hytale UI API

**Est. Hours:** 4h

---

### 6.2 RealmMapTooltipBuilder

**File:** `maps/ui/RealmMapTooltipBuilder.java`

```java
public class RealmMapTooltipBuilder {
    public List<Component> buildTooltip(RealmMapData mapData) {
        // Header: Realm Map
        // Level, Biome, Size
        // Rarity color
        // Modifiers list
        // Difficulty rating (stars)
        // IIQ/IIR totals
    }
}
```

**Dependencies:**
- 1.7 RealmMapData
- `gear/tooltip/` (existing tooltip system)

**Est. Hours:** 3h

---

### 6.3 RealmProgressSystem

**File:** `maps/systems/RealmProgressSystem.java`

```java
public class RealmProgressSystem extends EntityTickingSystem<EntityStore> {
    private static final float UPDATE_INTERVAL = 1.0f;

    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.of(PlayerRef.getComponentType(), RealmPlayerComponent.TYPE);
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk chunk, Store store, CommandBuffer cmd) {
        // Update progress UI for each player in realm
    }
}
```

**Dependencies:**
- 4.5 RealmPlayerComponent
- 6.1 RealmProgressUI
- 3.10 RealmsManager

**Est. Hours:** 2h

---

### 6.4 RealmCompletionUI

**File:** `maps/ui/RealmCompletionUI.java`

```java
public class RealmCompletionUI {
    public void showCompletionScreen(PlayerRef player, RealmRewards rewards) {
        // Display completion summary
        // Show rewards earned
        // Show statistics (time, kills, etc.)
    }
}
```

**Dependencies:**
- 4.14 RealmRewardCalculator
- Hytale UI API

**Est. Hours:** 3h

---

### Phase 6 Deliverables

| ID | File | Type | Dependencies | Est. Hours |
|----|------|------|--------------|------------|
| 6.1 | RealmProgressUI.java | UI | 4.3 | 4h |
| 6.2 | RealmMapTooltipBuilder.java | Builder | 1.7 | 3h |
| 6.3 | RealmProgressSystem.java | System | 4.5, 6.1 | 2h |
| 6.4 | RealmCompletionUI.java | UI | 4.14 | 3h |
| **Total** | | | | **12h** |

### Phase 6 Exit Criteria

- [ ] Progress bar shows during realm
- [ ] Progress updates every second
- [ ] Tooltip shows all map properties
- [ ] Completion screen displays rewards
- [ ] All UI elements styled consistently

---

## 10. Phase 7: Database & Analytics

> **Goal:** Implement persistence for completion records and statistics.

### 7.1 RealmCompletionDAO

**File:** `maps/database/RealmCompletionDAO.java`

```java
public class RealmCompletionDAO {
    public void saveCompletion(RealmCompletionRecord record) { ... }
    public List<RealmCompletionRecord> getPlayerCompletions(UUID playerId, int limit) { ... }
    public int getTotalCompletions(UUID playerId) { ... }
}
```

**Dependencies:**
- 7.3 RealmDatabaseSchema
- `database/DataManager.java` (existing)

**Est. Hours:** 3h

---

### 7.2 RealmStatisticsDAO

**File:** `maps/database/RealmStatisticsDAO.java`

```java
public class RealmStatisticsDAO {
    public void updatePlayerStats(UUID playerId, RealmStatsDelta delta) { ... }
    public RealmPlayerStats getPlayerStats(UUID playerId) { ... }
}
```

**Dependencies:**
- 7.3 RealmDatabaseSchema
- `database/DataManager.java` (existing)

**Est. Hours:** 2h

---

### 7.3 RealmDatabaseSchema

**File:** `maps/database/RealmDatabaseSchema.java`

```java
public final class RealmDatabaseSchema {
    public static final String CREATE_COMPLETIONS_TABLE = """
        CREATE TABLE IF NOT EXISTS rpg_realm_completions (
            id BIGINT PRIMARY KEY AUTO_INCREMENT,
            map_id VARCHAR(36) NOT NULL,
            player_id VARCHAR(36) NOT NULL,
            completed_at TIMESTAMP NOT NULL,
            map_level INT NOT NULL,
            map_rarity VARCHAR(20) NOT NULL,
            biome_type VARCHAR(30) NOT NULL,
            monsters_killed INT NOT NULL,
            total_monsters INT NOT NULL,
            time_taken_seconds INT NOT NULL,
            completion_type VARCHAR(20) NOT NULL,
            modifiers TEXT,
            INDEX idx_player (player_id),
            INDEX idx_completed_at (completed_at)
        )
        """;

    public static final String CREATE_STATS_TABLE = """
        CREATE TABLE IF NOT EXISTS rpg_realm_player_stats (
            player_id VARCHAR(36) PRIMARY KEY,
            total_realms_entered INT DEFAULT 0,
            total_realms_completed INT DEFAULT 0,
            total_monsters_killed BIGINT DEFAULT 0,
            total_time_in_realms_seconds BIGINT DEFAULT 0,
            highest_level_completed INT DEFAULT 0,
            last_realm_at TIMESTAMP NULL,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
        """;
}
```

**Dependencies:** None

**Est. Hours:** 1h

---

### Phase 7 Deliverables

| ID | File | Type | Dependencies | Est. Hours |
|----|------|------|--------------|------------|
| 7.1 | RealmCompletionDAO.java | DAO | 7.3 | 3h |
| 7.2 | RealmStatisticsDAO.java | DAO | 7.3 | 2h |
| 7.3 | RealmDatabaseSchema.java | Schema | None | 1h |
| **Total** | | | | **6h** |

### Phase 7 Exit Criteria

- [ ] Tables created on startup
- [ ] Completions saved to database
- [ ] Statistics updated correctly
- [ ] Queries return correct data
- [ ] No SQL injection vulnerabilities

---

## 11. Phase 8: Testing & Validation

> **Goal:** Comprehensive testing of all systems.

### 8.1 Unit Tests

| Test File | Coverage |
|-----------|----------|
| `RealmMapDataTest.java` | Validation, builders, computed properties |
| `RealmInstanceTest.java` | State transitions, player tracking |
| `RealmCompletionTrackerTest.java` | Kill counting, progress calculation |
| `RealmModifierPoolTest.java` | Weighted selection, value ranges |
| `RealmModifierRollerTest.java` | Level floors, rarity bonuses |
| `RealmMobCalculatorTest.java` | Monster count formulas |
| `RealmMobPoolTest.java` | Mob selection, weights |
| `RealmRewardCalculatorTest.java` | Reward formulas |
| `RealmMapGeneratorTest.java` | Map generation distributions |
| `RealmMapUtilsTest.java` | Serialization roundtrip |
| `RealmsConfigLoaderTest.java` | YAML parsing |

**Est. Hours:** 10h

---

### 8.2 Integration Tests

| Test File | Coverage |
|-----------|----------|
| `RealmLifecycleIntegrationTest.java` | Full lifecycle: create → enter → exit → close |
| `RealmCompletionIntegrationTest.java` | Kill all mobs → completion → rewards |
| `RealmStoneIntegrationTest.java` | Stone application on maps |

**Est. Hours:** 6h

---

### 8.3 Manual Testing Checklist

**Milestone 1: Instance Creation**
- [ ] Template loads without errors
- [ ] Instance spawns with correct config
- [ ] Removal conditions applied
- [ ] Auto-cleanup after timeout

**Milestone 2: Player Flow**
- [ ] Map activates on use
- [ ] Portal appears at player
- [ ] Player enters via right-click
- [ ] Player can exit
- [ ] Disconnect handling works

**Milestone 3: Combat**
- [ ] Monsters spawn at correct positions
- [ ] Monster levels match map level
- [ ] Modifiers affect monsters
- [ ] Deaths tracked correctly
- [ ] Progress UI updates

**Milestone 4: Completion**
- [ ] Completion triggers at 0 remaining
- [ ] Rewards distributed correctly
- [ ] XP bonus applied
- [ ] Completion UI shows
- [ ] Database record created

**Milestone 5: Maps & Stones**
- [ ] Maps drop from mobs
- [ ] Drop rates match config
- [ ] Tooltip shows all info
- [ ] Stones work on maps
- [ ] Cartographer adds IIQ
- [ ] Horizon changes biome
- [ ] Explorer adjusts level

**Milestone 6: Edge Cases**
- [ ] Multiple players in realm
- [ ] Player death in realm
- [ ] Server restart with active realms
- [ ] Timeout with players inside
- [ ] Rapid entry/exit

**Est. Hours:** 8h

---

### Phase 8 Deliverables

| ID | Task | Type | Est. Hours |
|----|------|------|------------|
| 8.1 | Unit tests (11 files) | Tests | 10h |
| 8.2 | Integration tests (3 files) | Tests | 6h |
| 8.3 | Manual testing | QA | 8h |
| **Total** | | | **24h** |

---

## 12. Dependency Graph

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                              COMPLETE DEPENDENCY GRAPH                                   │
└─────────────────────────────────────────────────────────────────────────────────────────┘

PHASE 0                  PHASE 1                    PHASE 2
═══════                  ═══════                    ═══════
                         ┌─────────────┐
                         │ 1.1 Biome   │───────────────────────────────────────┐
                         │ 1.2 Size    │───────────────────────────────────────┤
Templates ◄──────────────│ 1.3 Shape   │                                       │
    │                    │ 1.4 State   │                                       │
    │                    └──────┬──────┘                                       │
    │                           │                                              │
    │                    ┌──────▼──────┐            ┌─────────────────────┐    │
    │                    │ 1.5 ModType │            │ 2.1 ModifiableItem  │    │
    │                    └──────┬──────┘            │ 2.2 ModItemBuilder  │    │
    │                           │                   │ 2.3 ItemModifier    │    │
    │                    ┌──────▼──────┐            │ 2.4 ItemTargetType  │    │
    │                    │ 1.6 Modifier│◄───────────│ 2.5 StoneType       │    │
    │                    └──────┬──────┘            │ 2.6 StoneActions    │    │
    │                           │                   │ 2.7 StoneService    │    │
    │                    ┌──────▼──────┐            │ 2.8 StoneResult     │    │
    │                    │ 1.7 MapData │◄───────────┴─────────────────────┘    │
    │                    └──────┬──────┘                                       │
    │                           │                                              │
    │                    ┌──────▼──────┐                                       │
    └────────────────────│ 1.8-1.17    │                                       │
                         │ Config      │                                       │
                         └──────┬──────┘                                       │
                                │                                              │
────────────────────────────────┼──────────────────────────────────────────────┼──────────
                                │                                              │
PHASE 3                         │                                              │
═══════                         ▼                                              │
                         ┌─────────────┐                                       │
                         │ 3.1 Registry│◄──────────────────────────────────────┘
                         │ 3.2 Template│
                         │ 3.3 SpawnPt │
                         └──────┬──────┘
                                │
                         ┌──────▼──────┐
                         │ 3.4 Factory │◄─────────────────────────────┐
                         └──────┬──────┘                              │
                                │                                     │
                         ┌──────▼──────┐                              │
                         │ 3.5 Instance│◄────────────────────┐        │
                         └──────┬──────┘                     │        │
                                │                            │        │
           ┌────────────────────┼────────────────────┐       │        │
           │                    │                    │       │        │
    ┌──────▼──────┐      ┌──────▼──────┐      ┌──────▼──────┐│        │
    │ 3.6 Portal  │      │ 3.7 Teleport│      │ 3.8 Removal ││        │
    └──────┬──────┘      └──────┬──────┘      └──────┬──────┘│        │
           │                    │                    │       │        │
           └────────────────────┼────────────────────┘       │        │
                                │                            │        │
                         ┌──────▼──────┐                     │        │
                         │3.10 Manager │◄────────────────────┘        │
                         │3.11 Service │                              │
                         │3.12 Events  │                              │
                         └──────┬──────┘                              │
                                │                                     │
────────────────────────────────┼─────────────────────────────────────┼──────────
                                │                                     │
PHASE 4                         ▼                                     │
═══════                  ┌─────────────┐                              │
                         │ 4.1-4.2     │                              │
                         │ ModifierPool│                              │
                         └──────┬──────┘                              │
                                │                                     │
                         ┌──────▼──────┐      ┌─────────────┐         │
                         │ 4.3 Tracker │      │ 4.4-4.5     │         │
                         └──────┬──────┘      │ Components  │         │
                                │             └──────┬──────┘         │
                                │                    │                │
                         ┌──────▼──────┐      ┌──────▼──────┐         │
                         │ 4.6-4.10    │◄─────│             │         │
                         │ MobSpawning │      │             │─────────┘
                         └──────┬──────┘      │             │
                                │             │             │
                         ┌──────▼──────┐      │             │
                         │ 4.11-4.12   │      │             │
                         │ Systems     │      │             │
                         └──────┬──────┘      │             │
                                │             │             │
                         ┌──────▼──────┐      │             │
                         │ 4.13 Death  │◄─────┘             │
                         └──────┬──────┘                    │
                                │                           │
                         ┌──────▼──────┐                    │
                         │ 4.14-4.18   │                    │
                         │ Rewards     │                    │
                         └──────┬──────┘                    │
                                │                           │
────────────────────────────────┼───────────────────────────┼────────────────────
                                │                           │
PHASE 5                         ▼                           │
═══════                  ┌─────────────┐                    │
                         │ 5.1 MapComp │◄───────────────────┘
                         └──────┬──────┘
                                │
           ┌────────────────────┼────────────────────┐
           │                    │                    │
    ┌──────▼──────┐      ┌──────▼──────┐      ┌──────▼──────┐
    │ 5.2 Drop    │      │ 5.3 Use     │      │ 5.7 Item    │
    └─────────────┘      └─────────────┘      └─────────────┘
           │                    │                    │
    ┌──────▼──────┐      ┌──────▼──────┐      ┌──────▼──────┐
    │ 5.8 Gen     │      │ 5.4-5.6     │      │ 5.9 Utils   │
    └─────────────┘      │ Listeners   │      └─────────────┘
                         └─────────────┘             │
                                │                    │
                         ┌──────▼──────┐             │
                         │ 5.10 Stones │◄────────────┘
                         └──────┬──────┘
                                │
────────────────────────────────┼────────────────────────────────────────────────
                                │
PHASE 6                         ▼
═══════                  ┌─────────────┐
                         │ 6.1-6.4     │
                         │ UI          │
                         └──────┬──────┘
                                │
────────────────────────────────┼────────────────────────────────────────────────
                                │
PHASE 7                         ▼
═══════                  ┌─────────────┐
                         │ 7.1-7.3     │
                         │ Database    │
                         └─────────────┘
```

---

## 13. Risk Register

| ID | Risk | Probability | Impact | Mitigation |
|----|------|-------------|--------|------------|
| R1 | Template creation takes longer than expected | High | High | Start with 6 templates, automate batch creation |
| R2 | Hytale API changes in updates | Medium | High | Abstract Hytale calls behind interfaces |
| R3 | Portal block system doesn't work as expected | Medium | Medium | Have fallback: custom interaction handler |
| R4 | Performance issues with many active realms | Low | High | Implement realm pooling, monitor metrics |
| R5 | World generation assets are complex | Medium | Medium | Document thoroughly, create tooling |
| R6 | Integration conflicts with existing systems | Low | Medium | Careful interface design, feature flags |

---

## Summary

| Phase | Description | Files | Hours |
|-------|-------------|-------|-------|
| **0** | Prerequisites & Assets | 4 docs, 1 template | 15h |
| **1** | Foundation | 17 files | 20h |
| **2** | Stone System | 11 files | 19h |
| **3** | Instance Management | 14 files | 30h |
| **4** | Gameplay Systems | 18 files | 33h |
| **5** | Map Item & Drops | 10 files | 20h |
| **6** | UI & Polish | 4 files | 12h |
| **7** | Database | 3 files | 6h |
| **8** | Testing | 14 tests | 24h |
| **TOTAL** | | **91 files** | **179h** |

---

## Next Steps

1. ✅ Documentation complete (01-09)
2. ⬜ **Start Phase 0:** Create first test template
3. ⬜ **Start Phase 1:** Implement core enums and RealmMapData
4. ⬜ Parallel: Design remaining 43 templates

---

*Document version: 2.0*
*Last updated: Based on API verification from 08-api-verification.md*
