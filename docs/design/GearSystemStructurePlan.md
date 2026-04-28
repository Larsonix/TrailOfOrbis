# Gear System Structure Plan

High-level architectural plan for integrating the RPG Gear System into the existing TrailOfOrbis plugin.

**Goal**: Define the core structure only - no implementation details. This is the skeleton that will be expanded.

> **Implementation**: See [ROADMAP.md](../plan/ROADMAP.md) for the phased implementation plan.

---

## Package Structure

```
io.github.larsonix.trailoforbis/
├── gear/                              # NEW: Gear system root
│   ├── api/                           # Service interfaces
│   │   └── GearService.java           # Public API for gear operations
│   │
│   ├── core/                          # Core manager and orchestration
│   │   └── GearManager.java           # Main manager (implements GearService)
│   │
│   ├── model/                         # Data structures (immutable)
│   │   ├── GearData.java              # RPG data attached to items
│   │   ├── GearModifier.java          # Single modifier (prefix/suffix)
│   │   ├── GearRarity.java            # Enum: Common→Mythic
│   │   └── ModifierType.java          # Enum: Prefix/Suffix
│   │
│   ├── generation/                    # Gear creation
│   │   ├── GearGenerator.java         # Generates random gear
│   │   ├── ModifierPool.java          # Pool of available modifiers
│   │   └── RarityRoller.java          # Rarity probability rolls
│   │
│   ├── codec/                         # Serialization
│   │   └── GearCodecs.java            # BSON codecs for GearData, GearModifier
│   │
│   ├── equipment/                     # Equip/unequip handling
│   │   ├── EquipmentValidator.java    # Level requirement checks
│   │   └── EquipmentListener.java     # Event listeners for equip blocking
│   │
│   ├── stats/                         # Stat application from gear
│   │   ├── GearStatCalculator.java    # Calculate total gear bonuses
│   │   └── GearStatApplier.java       # Apply gear stats to player
│   │
│   ├── loot/                          # Drop generation
│   │   ├── LootGenerator.java         # Orchestrates drop creation
│   │   └── LootListener.java          # Death event listeners
│   │
│   ├── crafting/                      # Crafting integration
│   │   └── CraftingListener.java      # Pre/Post craft event handlers
│   │
│   ├── quality/                       # ItemQuality integration
│   │   └── QualityMapper.java         # Maps GearRarity → Hytale ItemQuality
│   │
│   ├── config/                        # Gear-specific configuration
│   │   ├── GearConfig.java            # Main gear config POJO
│   │   ├── ModifierConfig.java        # Modifier definitions
│   │   └── LootTableConfig.java       # Mob → loot table mappings
│   │
│   └── util/                          # Gear utilities
│       └── GearUtils.java             # Helper methods (metadata read/write)
```

---

## Core Interfaces

### GearService (Public API)

```java
package io.github.larsonix.trailoforbis.gear.api;

public interface GearService {

    // === Reading Gear Data ===

    Optional<GearData> getGearData(ItemStack item);

    boolean isRpgGear(ItemStack item);

    // === Creating Gear ===

    ItemStack generateGear(String baseItemId, int itemLevel, GearRarity rarity);

    ItemStack generateRandomGear(String baseItemId, int itemLevel);

    // === Validation ===

    boolean canEquip(UUID playerId, ItemStack item);

    int getRequiredLevel(ItemStack item);

    // === Stat Calculation ===

    Map<String, Double> calculateGearBonuses(UUID playerId);
}
```

---

## Manager Structure

### GearManager

```java
package io.github.larsonix.trailoforbis.gear.core;

public class GearManager implements GearService {

    // === Dependencies (injected via constructor) ===
    private final ConfigManager configManager;
    private final LevelingService levelingService;

    // === Sub-components (created internally) ===
    private GearGenerator gearGenerator;
    private GearStatCalculator statCalculator;
    private EquipmentValidator equipmentValidator;
    private QualityMapper qualityMapper;
    private LootGenerator lootGenerator;

    // === Lifecycle ===
    public void initialize();   // Called in start(), after config loaded
    public void shutdown();     // Called in plugin shutdown

    // === GearService implementation ===
    // (delegates to sub-components)
}
```

---

## Data Models

### GearData (Immutable)

```java
package io.github.larsonix.trailoforbis.gear.model;

public record GearData(
    int level,
    GearRarity rarity,
    int quality,              // 1-101
    List<GearModifier> prefixes,
    List<GearModifier> suffixes,
    boolean isUnique,
    @Nullable String uniqueId
) {
    // Builder pattern for construction
    public static Builder builder();

    // Convenience methods
    public List<GearModifier> allModifiers();
    public double qualityMultiplier();  // quality / 50.0
}
```

### GearModifier (Immutable)

```java
package io.github.larsonix.trailoforbis.gear.model;

public record GearModifier(
    String id,                // e.g., "sharp", "of_strength"
    ModifierType type,        // PREFIX or SUFFIX
    String statId,            // e.g., "physical_damage", "strength"
    double value,             // The rolled value (continuous scaling, no tiers)
    double minValue,          // For display: "5-15 damage"
    double maxValue
) {}
```

**Note**: Modifiers use continuous scaling based on item level, not discrete tiers. The value scales directly with `base + (itemLevel × scalePerLevel)`.

### GearRarity (Enum)

```java
package io.github.larsonix.trailoforbis.gear.model;

public enum GearRarity {
    COMMON(1, 1, "#c9d2dd", "Common"),
    UNCOMMON(2, 2, "#3e9049", "Uncommon"),
    RARE(3, 3, "#2770b7", "Rare"),
    EPIC(4, 4, "#8b339e", "Epic"),
    LEGENDARY(5, 4, "#bb8a2c", "Legendary"),
    MYTHIC(6, 4, "#ff4500", "Mythic");

    private final int tier;
    private final int maxModifiers;
    private final String hexColor;
    private final String hytaleQualityId;

    // getters...
}
```

---

## Configuration Structure

**Note**: Durability and repair are handled by vanilla Hytale systems. We only configure RPG-specific mechanics.

### gear-balance.yml

Master balance configuration. See [GearSystemMechanics.md](./GearSystemMechanics.md) for full template.

```yaml
# Core balance values
power_scaling:
  gear_power_ratio: 0.5       # Gear adds 50% of attribute power
  slot_weights: { weapon: 0.30, chest: 0.20, ... }

# Rarity system
rarity:
  common: { stat_multiplier: 0.4, max_modifiers: 1, drop_weight: 50 }
  # ... (see GearSystemMechanics.md for full config)

# Quality system
quality:
  baseline: 50                # Quality 50 = 1.0x multiplier
  perfect_drop_chance: 0.005  # 0.5% for 101%

# Attribute requirements
attribute_requirements:
  level_to_base_ratio: 0.5    # Level 50 item → base 25 requirement
  rarity_multipliers: { common: 0.1, mythic: 1.0, ... }

# Loot bonuses
loot:
  luck_to_rarity_percent: 0.5
```

### gear-modifiers.yml

Modifier definitions using continuous scaling (no tiers).

```yaml
prefixes:
  sharp:
    display_name: "Sharp"
    stat: physical_damage
    stat_type: flat
    base_min: 1.0             # Base range at level 1
    base_max: 3.0
    scale_per_level: 0.2      # +0.2 per item level
    weight: 100               # Drop weight (higher = more common)
    required_attribute: STR

  blazing:
    display_name: "Blazing"
    stat: fire_damage
    stat_type: flat
    base_min: 1.0
    base_max: 2.5
    scale_per_level: 0.15
    weight: 50
    required_attribute: INT

suffixes:
  of_the_whale:
    display_name: "of the Whale"
    stat: max_health
    stat_type: flat
    base_min: 5.0
    base_max: 15.0
    scale_per_level: 1.0
    weight: 100
    required_attribute: VIT
```

### gear-loot-tables.yml

```yaml
loot-tables:
  default:
    drop-chance: 0.15
    gear-types: [weapon, armor]

  bosses:
    legendary_wolf:
      drop-chance: 1.0
      rarity-override: rare+   # At least rare
      unique-chance: 0.1
      unique-pool: [shadowfang, moonblade]
```

---

## Plugin Integration Points

### In TrailOfOrbis.java

```java
// === Fields ===
private GearManager gearManager;

// === In setup() ===
// Register event listeners (no config needed)
EquipmentListener.register(getEventRegistry());
LootListener.register(getEventRegistry());
CraftingListener.register(getEventRegistry());

// === In start() ===
// After ConfigManager.loadConfigs()
this.gearManager = new GearManager(configManager, levelingManager);
this.gearManager.initialize();

// Register service
ServiceRegistry.register(GearService.class, gearManager);

// === In shutdown() ===
if (gearManager != null) {
    gearManager.shutdown();
}
```

### In ConfigManager.java

```java
// === New fields ===
private GearBalanceConfig gearBalanceConfig;
private ModifierConfig modifierConfig;
private LootTableConfig lootTableConfig;

// === In loadConfigs() ===
gearBalanceConfig = loadConfig("gear-balance.yml", GearBalanceConfig.class);
modifierConfig = loadConfig("gear-modifiers.yml", ModifierConfig.class);
lootTableConfig = loadConfig("gear-loot-tables.yml", LootTableConfig.class);

// === Getters ===
public GearBalanceConfig getGearBalanceConfig();
public ModifierConfig getModifierConfig();
public LootTableConfig getLootTableConfig();
```

---

## Event Wiring

### EquipmentListener

```java
public class EquipmentListener {

    public static void register(EventRegistry registry) {
        // Block weapon/utility equip if level too low
        registry.register(SwitchActiveSlotEvent.class, EquipmentListener::onSlotSwitch);

        // Feedback when equip blocked
        // NOTE (Updated 2026-03-26): LivingEntityInventoryChangeEvent was replaced by
        // InventoryChangeEvent (ECS event). Use EntityEventSystem registration instead
        // of EventRegistry.register(). See InventoryChangeEventSystem for the pattern.
        // registry.register(InventoryChangeEvent.class, ...) // OLD - won't work
        // Use: getEntityStoreRegistry().registerSystem(new InventoryChangeEventSystem());
    }

    private static void onSlotSwitch(SwitchActiveSlotEvent event) {
        // Delegate to GearService.canEquip()
        // Cancel if false, send message
    }
}
```

### LootListener

```java
public class LootListener {

    public static void register(EventRegistry registry) {
        // Intercept mob death for custom loot
        // (Hook into death systems or DropItemEvent)
    }

    // Delegate to LootGenerator for actual generation
}
```

### CraftingListener

```java
public class CraftingListener {

    public static void register(EventRegistry registry) {
        registry.register(CraftRecipeEvent.Pre.class, CraftingListener::onPreCraft);
        registry.register(CraftRecipeEvent.Post.class, CraftingListener::onPostCraft);
    }

    private static void onPreCraft(CraftRecipeEvent.Pre event) {
        // Check level requirements for RPG recipes
        // Cancel if player can't craft
    }

    private static void onPostCraft(CraftRecipeEvent.Post event) {
        // Replace output with custom RPG gear
        // Apply crafting quality cap (50%)
    }
}
```

---

## Stat Integration

### With AttributeManager

The gear system provides bonuses that feed into the existing stat calculation:

```java
// In AttributeManager or AttributeCalculator
public ComputedStats recalculateStats(UUID playerId) {
    // 1. Get base attributes (STR, DEX, etc.)
    PlayerData data = repository.get(playerId);

    // 2. Get skill tree bonuses
    Map<String, Double> skillBonuses = skillTreeService.getStatBonuses(playerId);

    // 3. Get gear bonuses (NEW)
    Map<String, Double> gearBonuses = gearService.calculateGearBonuses(playerId);

    // 4. Combine all sources
    return calculator.compute(data, skillBonuses, gearBonuses);
}
```

### GearStatCalculator

```java
public class GearStatCalculator {

    public Map<String, Double> calculateBonuses(Inventory inventory) {
        Map<String, Double> bonuses = new HashMap<>();

        // Sum all equipped gear modifiers
        for (ItemStack item : getEquippedItems(inventory)) {
            Optional<GearData> gearData = gearUtils.getGearData(item);
            if (gearData.isEmpty()) continue;

            for (GearModifier mod : gearData.get().allModifiers()) {
                double value = mod.value() * gearData.get().qualityMultiplier();
                bonuses.merge(mod.statId(), value, Double::sum);
            }
        }

        return bonuses;
    }
}
```

---

## Codec Registration

### In GearManager.initialize()

```java
public void initialize() {
    // Register BSON codecs for gear data
    GearCodecs.registerAll();

    // Initialize sub-components
    this.qualityMapper = new QualityMapper();
    this.gearGenerator = new GearGenerator(configManager.getModifierConfig());
    this.statCalculator = new GearStatCalculator();
    this.equipmentValidator = new EquipmentValidator(levelingService);
    this.lootGenerator = new LootGenerator(gearGenerator, configManager.getLootTableConfig());
}
```

---

## Metadata Keys

```java
public final class GearMetadataKeys {
    public static final String PREFIX = "RPG:";

    public static final String LEVEL = PREFIX + "Level";
    public static final String RARITY = PREFIX + "Rarity";
    public static final String QUALITY = PREFIX + "Quality";
    public static final String PREFIXES = PREFIX + "Prefixes";
    public static final String SUFFIXES = PREFIX + "Suffixes";
    public static final String IS_UNIQUE = PREFIX + "IsUnique";
    public static final String UNIQUE_ID = PREFIX + "UniqueId";
    // Note: Durability uses native ItemStack.maxDurability, not custom metadata

    private GearMetadataKeys() {}
}
```

---

## Initialization Order

```
1. ConfigManager loads gear-config.yml, gear-modifiers.yml, gear-loot-tables.yml
2. GearManager created with dependencies (ConfigManager, LevelingService)
3. GearManager.initialize() called:
   a. Register codecs
   b. Create QualityMapper
   c. Create GearGenerator (needs ModifierConfig)
   d. Create GearStatCalculator
   e. Create EquipmentValidator (needs LevelingService)
   f. Create LootGenerator (needs GearGenerator, LootTableConfig)
4. Register GearService in ServiceRegistry
5. Event listeners already registered in setup() - they use ServiceRegistry.require()
```

---

## Dependencies Graph

```
                    ConfigManager
                    /     |      \
                   /      |       \
      GearBalanceConfig  ModifierConfig  LootTableConfig
                 \        |         /
                  \       |        /
                   \      |       /
                    GearManager ←───── LevelingService
                   /    |    \    \
                  /     |     \    \
    GearGenerator  QualityMapper  EquipmentValidator  LootGenerator
         |              |              |                   |
    ModifierPool   (uses rarity    (uses level        (uses GearGenerator
                    enum mapping)   checks)            + LootTableConfig)
```

---

## File Checklist

### New Files to Create

**Packages:**
- [ ] `gear/api/`
- [ ] `gear/core/`
- [ ] `gear/model/`
- [ ] `gear/generation/`
- [ ] `gear/codec/`
- [ ] `gear/equipment/`
- [ ] `gear/stats/`
- [ ] `gear/loot/`
- [ ] `gear/crafting/`
- [ ] `gear/quality/`
- [ ] `gear/config/`
- [ ] `gear/util/`

**Core Classes:**
- [ ] `GearService.java` - API interface
- [ ] `GearManager.java` - Main manager
- [ ] `GearData.java` - Item data record
- [ ] `GearModifier.java` - Modifier record
- [ ] `GearRarity.java` - Rarity enum
- [ ] `ModifierType.java` - Prefix/Suffix enum
- [ ] `GearMetadataKeys.java` - Constant keys

**Configs:**
- [ ] `GearBalanceConfig.java` - Balance/power scaling POJO
- [ ] `ModifierConfig.java` - Modifier definitions POJO
- [ ] `LootTableConfig.java` - Loot tables POJO
- [ ] `gear-balance.yml` - Balance configuration
- [ ] `gear-modifiers.yml` - Modifier definitions
- [ ] `gear-loot-tables.yml` - Loot tables

**Generators:**
- [ ] `GearGenerator.java`
- [ ] `ModifierPool.java`
- [ ] `RarityRoller.java`
- [ ] `LootGenerator.java`

**Integration:**
- [ ] `GearCodecs.java`
- [ ] `GearUtils.java`
- [ ] `QualityMapper.java`
- [ ] `GearStatCalculator.java`
- [ ] `GearStatApplier.java`
- [ ] `EquipmentValidator.java`

**Listeners:**
- [ ] `EquipmentListener.java`
- [ ] `LootListener.java`
- [ ] `CraftingListener.java`

**Modifications to Existing:**
- [ ] `TrailOfOrbis.java` - Add GearManager init
- [ ] `ConfigManager.java` - Add gear config loading
- [ ] `AttributeCalculator.java` - Add gear bonuses to calculation

---

## Next Steps (After Structure)

Once the skeleton is in place:

1. **Phase 1**: Implement data models (GearData, GearModifier, GearRarity)
2. **Phase 2**: Implement codecs and GearUtils (read/write metadata)
3. **Phase 3**: Implement EquipmentValidator + EquipmentListener
4. **Phase 4**: Implement GearGenerator basics
5. **Phase 5**: Implement stat calculation
6. **Phase 6**: Implement loot generation
7. **Phase 7**: Implement crafting restrictions
8. **Phase 8**: Polish and testing

---

## Design Principles Applied

1. **Follow existing patterns**: Manager pattern, Service interfaces, Repository pattern
2. **Dependency injection**: All dependencies via constructor
3. **Immutable data models**: Records for GearData, GearModifier
4. **Separation of concerns**: Each class has one responsibility
5. **ServiceRegistry integration**: GearService registered for decoupled access
6. **Event-driven**: Listeners registered in setup(), use ServiceRegistry.require()
7. **Config-driven**: All values in YAML, validated on load
8. **Fail-fast**: Invalid config = plugin disable
9. **Thread-safe**: Consider per-player locking if caching gear calculations

---

*This document defines STRUCTURE only. Implementation details will be added as each component is built.*

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-01-23 | Initial structure plan |
| 1.1 | 2026-01-23 | **Consistency fixes**: Removed `tier` field from GearModifier (continuous scaling, no tiers). Aligned config file names (`gear-balance.yml` instead of `gear-config.yml`). Updated config POJOs. Clarified durability uses vanilla native fields. |
