# Realm Maps

Realm Maps are consumable items that create portals to temporary Realms. This document covers the map item structure, properties, and drop mechanics.

## Map Item Structure

A Realm Map is a special item with embedded NBT-like data:

```
┌────────────────────────────────────────────────────────┐
│              REALM MAP ITEM                            │
├────────────────────────────────────────────────────────┤
│  Base Properties:                                      │
│  ├── mapId: UUID (unique identifier)                   │
│  ├── level: int (1-100)                                │
│  ├── tier: enum (COMMON → MYTHIC, matches GearRarity)  │
│  ├── templateId: String (realm template reference)     │
│  └── layoutId: String (map layout reference)           │
│                                                        │
│  Visual Properties:                                    │
│  ├── biomeType: enum (DESERT, FOREST, VOLCANIC, etc.)  │
│  ├── layoutShape: enum (CIRCLE, SQUARE, HEX, etc.)     │
│  └── layoutSize: enum (SMALL, MEDIUM, LARGE)           │
│                                                        │
│  Modifiers:                                            │
│  └── modifiers: List<RealmModifier>                    │
│       ├── [0] +25% Monster Damage (DAMAGE_MODIFIER)    │
│       ├── [1] +15% Experience (XP_MODIFIER)            │
│       └── [2] Monsters deal Fire damage (FIRE_ONLY)    │
│                                                        │
│  Computed Stats (from modifiers):                      │
│  ├── totalItemQuantity: float (IIQ bonus)              │
│  ├── totalItemRarity: float (IIR bonus)                │
│  ├── totalXpBonus: float                               │
│  └── difficultyRating: int (sum of difficulty)         │
└────────────────────────────────────────────────────────┘
```

## Map Tiers

Maps use the same rarity tiers as gear (`GearRarity`), determining modifier count and drop rates:

| Tier | Color | Hex | Max Modifiers | Drop Weight | Description |
|------|-------|-----|---------------|-------------|-------------|
| COMMON | Gray | #C9D2DD | 1 | 50.0 | Basic realm, minimal challenge |
| UNCOMMON | Green | #3E9049 | 2 | 30.0 | Slight difficulty increase |
| RARE | Blue | #2770B7 | 3 | 15.0 | Moderate challenge and rewards |
| EPIC | Purple | #8B339E | 4 | 4.0 | Significant difficulty spike |
| LEGENDARY | Gold | #BB8A2C | 5 | 0.9 | Extreme challenge, great rewards |
| MYTHIC | Red-Orange | #FF4500 | 6 | 0.1 | Ultimate difficulty, best rewards |

## Biome Types

Each map has a biome type that determines the visual theme and monster pool:

```java
public enum RealmBiomeType {
    // Zone 1 - Emerald Grove themed
    FOREST("Forest", "Verdant woods with natural creatures"),
    CAVE("Cave", "Dark underground tunnels"),
    SWAMP("Swamp", "Murky wetlands with poison hazards"),

    // Zone 2 - Howling Sands themed
    DESERT("Desert", "Scorching dunes and ancient ruins"),
    CANYON("Canyon", "Rocky formations and ambush points"),
    OASIS("Oasis", "Hidden paradise with fierce guardians"),

    // Zone 3 - Borea themed
    TUNDRA("Tundra", "Frozen wasteland with cold damage"),
    ICE_CAVE("Ice Cave", "Crystalline caverns"),
    VOLCANO("Volcano", "Molten environment with fire hazards"),

    // Special
    VOID("Void", "Otherworldly dimension"),
    CORRUPTED("Corrupted", "Twisted reality");
}
```

## Layout Shapes

The layout determines the playable area's shape:

```java
public enum RealmLayoutShape {
    CIRCLE("Circle", "Circular arena, equal distance to edges"),
    SQUARE("Square", "Grid-based area, clear corners"),
    RECTANGLE("Rectangle", "Elongated area, corridor-like"),
    HEXAGON("Hexagon", "Six-sided area, tactical positioning"),
    CROSS("Cross", "Four arms from center, chokepoints"),
    RING("Ring", "Donut shape, hollow center"),
    IRREGULAR("Irregular", "Organic shape, unpredictable");
}
```

## Layout Sizes

| Size | Approximate Area | Monster Count (base) | Typical Duration |
|------|------------------|----------------------|------------------|
| SMALL | 50x50 blocks | 20-30 | 2-3 minutes |
| MEDIUM | 100x100 blocks | 40-60 | 4-5 minutes |
| LARGE | 200x200 blocks | 80-120 | 6-8 minutes |
| MASSIVE | 400x400 blocks | 150-200 | 10-12 minutes |

## Map Level Scaling

Maps drop at levels relative to the killing player:

```java
// Level range from config
int minLevel = playerLevel + config.getLevelRange().getMin();  // e.g., player 50 → 47
int maxLevel = playerLevel + config.getLevelRange().getMax();  // e.g., player 50 → 53

// Random level within range
int mapLevel = random.nextInt(minLevel, maxLevel + 1);

// Clamped to valid range
mapLevel = Math.clamp(mapLevel, 1, MAX_LEVEL);
```

## Drop Mechanics

### Drop Chance Calculation

```java
float baseChance = config.getBaseDropChance();  // 2%

// Mob type multipliers
if (mobClassification == MobClassification.BOSS) {
    baseChance = config.getBossDropChance();    // 50%
} else if (mobClassification == MobClassification.ELITE) {
    baseChance = config.getEliteDropChance();   // 10%
}

// Player IIQ bonus from gear/attributes
float iiqBonus = playerStats.getItemQuantity();
float finalChance = baseChance * (1 + iiqBonus);

// Roll for drop
if (random.nextFloat() < finalChance) {
    dropRealmMap(killer, mobLevel);
}
```

### Tier Selection

Realm maps reuse `GearRarity` for consistency with the gear system:

```java
float roll = random.nextFloat() * totalWeight;  // totalWeight ≈ 100
float cumulative = 0;

for (GearRarity rarity : GearRarity.values()) {
    cumulative += rarity.getDropWeight();
    if (roll < cumulative) {
        return rarity;
    }
}
return GearRarity.COMMON;  // fallback
```

This means realm maps follow the same rarity distribution as gear drops, providing a consistent player experience.

### Initial Modifier Generation

When a map drops, it receives random modifiers based on rarity:

```java
// Max modifiers by rarity: COMMON=1, UNCOMMON=2, RARE=3, EPIC=4, LEGENDARY=5, MYTHIC=6
int maxMods = rarity.ordinal() + 1;
int modifierCount = random.nextInt(1, maxMods + 1);

List<RealmModifier> modifiers = new ArrayList<>();
for (int i = 0; i < modifierCount; i++) {
    RealmModifier modifier = modifierPool.rollModifier(mapLevel, rarity);
    modifiers.add(modifier);
}
```

## Item Display

Maps should display their properties clearly in the UI, using rarity colors from `GearRarity`:

```
╔════════════════════════════════════════╗
║  [EPIC] Desert Realm Map               ║  ← Purple (#8B339E)
║  Level 52                              ║
╠════════════════════════════════════════╣
║  Layout: Large Square                  ║
║  Biome: Desert                         ║
║  Monsters: 80-100                      ║
╠════════════════════════════════════════╣
║  MODIFIERS:                            ║
║  • Monsters deal +25% damage           ║
║  • Monsters have +50% Fire Resistance  ║
║  • +30% Experience gained              ║
║  • +15% Item Quantity                  ║
╠════════════════════════════════════════╣
║  Difficulty Rating: ★★★☆☆              ║
║  Est. Duration: 6-8 minutes            ║
╠════════════════════════════════════════╣
║  [Right-Click to Open Portal]          ║
╚════════════════════════════════════════╝
```

**Rarity color reference:**
- COMMON: Gray (#C9D2DD)
- UNCOMMON: Green (#3E9049)
- RARE: Blue (#2770B7)
- EPIC: Purple (#8B339E)
- LEGENDARY: Gold (#BB8A2C)
- MYTHIC: Red-Orange (#FF4500)

## Data Serialization

Maps are stored using Hytale's component system, reusing `GearRarity` for the rarity field:

```java
public class RealmMapComponent implements Component {
    public static final Codec<RealmMapComponent> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.STRING.fieldOf("mapId").forGetter(c -> c.mapId.toString()),
            Codec.INT.fieldOf("level").forGetter(c -> c.level),
            GearRarity.CODEC.fieldOf("rarity").forGetter(c -> c.rarity),  // Reuses GearRarity
            Codec.STRING.fieldOf("templateId").forGetter(c -> c.templateId),
            Codec.STRING.fieldOf("layoutId").forGetter(c -> c.layoutId),
            RealmBiomeType.CODEC.fieldOf("biomeType").forGetter(c -> c.biomeType),
            RealmLayoutShape.CODEC.fieldOf("layoutShape").forGetter(c -> c.layoutShape),
            RealmLayoutSize.CODEC.fieldOf("layoutSize").forGetter(c -> c.layoutSize),
            RealmModifier.CODEC.listOf().fieldOf("modifiers").forGetter(c -> c.modifiers)
        ).apply(instance, RealmMapComponent::new)
    );
}
```

## Map Identification

Each map has a unique UUID that:
- Tracks the specific map instance
- Prevents duplication exploits
- Links to realm instance when created
- Enables analytics/logging

```java
// On map creation
UUID mapId = UUID.randomUUID();

// On realm creation
RealmInstance realm = realmsManager.createRealm(mapId, player);
// mapId is now bound to this realm instance
```
