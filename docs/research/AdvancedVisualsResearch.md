# Advanced Visuals Research

Research into Hytale's particle systems, model tinting, visual effects, and rendering customization for the RPG Gear System.

---

## Executive Summary

**Question**: Can we add visual polish to RPG items (particles, glows, tints)?

**Answer**: **YES** - Hytale has comprehensive visual effect systems:
- **Particle Systems** - Spawn colored/scaled particles at positions or attached to models
- **Entity Tinting** - Bottom/top color gradients on entities via `ApplicationEffects`
- **Model Gradients** - Per-model tinting via `gradientSet`/`gradientId`
- **Item Drop Effects** - Quality-based particles and VFX via `ItemEntityConfig`
- **Equipment Particles** - Attach particles to entity parts via `ModelParticle`
- **Inventory Slots** - Quality-based background textures via `ItemQuality.slotTexture`

---

## 1. Particle System Architecture

### 1.1 Core Particle Classes

#### ModelParticle (Protocol)
**Location**: `com/hypixel/hytale/protocol/ModelParticle.java`

Particles attached to entity models:

```java
public class ModelParticle {
    @Nullable public String systemId;           // Particle system asset ID
    public float scale;                         // Scale multiplier
    @Nullable public Color color;               // RGB color tint
    @Nonnull public EntityPart targetEntityPart; // Self, Entity, PrimaryItem, SecondaryItem
    @Nullable public String targetNodeName;     // Model bone/node name
    @Nullable public Vector3f positionOffset;   // Local position offset
    @Nullable public Direction rotationOffset;  // Rotation offset (yaw, pitch, roll)
    public boolean detachedFromModel;           // Spawn in world space vs follow model
}
```

#### EntityPart Enum
```java
public enum EntityPart {
    Self(0),          // The entity itself
    Entity(1),        // Target entity
    PrimaryItem(2),   // Held weapon/tool
    SecondaryItem(3)  // Offhand item
}
```

#### ModelTrail (Protocol)
**Location**: `com/hypixel/hytale/protocol/ModelTrail.java`

Trail effects for weapons/movements:

```java
public class ModelTrail {
    @Nullable public String trailId;            // Trail asset ID
    @Nonnull public EntityPart targetEntityPart;
    @Nullable public String targetNodeName;     // Model bone attachment
    @Nullable public Vector3f positionOffset;
    @Nullable public Direction rotationOffset;
    public boolean fixedRotation;               // Lock rotation
}
```

### 1.2 Spawning Particles

#### SpawnParticleSystem Packet
**Location**: `com/hypixel/hytale/protocol/packets/world/SpawnParticleSystem.java`

- **Packet ID**: 152
- Spawns particles at a world position

```java
public class SpawnParticleSystem implements Packet {
    public static final int PACKET_ID = 152;

    @Nullable public String particleSystemId;   // Particle system asset
    @Nullable public Position position;         // World position (x, y, z)
    @Nullable public Direction rotation;        // Rotation (yaw, pitch, roll)
    public float scale;                         // Scale multiplier
    @Nullable public Color color;               // RGB color tint
}
```

#### ParticleUtil (Server Utility)
**Location**: `com/hypixel/hytale/server/core/universe/world/ParticleUtil.java`

Programmatic particle spawning:

```java
// Basic spawn at position
ParticleUtil.spawnParticleEffect(
    "Drop_Legendary",      // Particle system ID
    position,              // Vector3d position
    componentAccessor
);

// Full control spawn
ParticleUtil.spawnParticleEffect(
    "Fire_Burst",          // Particle system ID
    x, y, z,               // Position
    yaw, pitch, roll,      // Rotation
    1.5f,                  // Scale
    new Color(255, 128, 0), // Color tint (orange)
    sourceRef,             // Source entity (excluded)
    playerRefs,            // Target players
    componentAccessor
);
```

**Key Methods**:
| Method | Parameters | Description |
|--------|------------|-------------|
| `spawnParticleEffect(String, Vector3d, ...)` | systemId, position | Basic spawn |
| `spawnParticleEffect(..., float, float, float, ...)` | + yaw, pitch, roll | With rotation |
| `spawnParticleEffect(..., float, Color, ...)` | + scale, color | Full customization |
| `spawnParticleEffect(WorldParticle, ...)` | WorldParticle config | From asset |

---

## 2. Entity Tinting and Visual Effects

### 2.1 ApplicationEffects
**Location**: `com/hypixel/hytale/protocol/ApplicationEffects.java`

Visual effects applied to entities (e.g., status effects, buffs):

```java
public class ApplicationEffects {
    // Entity Tinting
    @Nullable public Color entityBottomTint;    // Bottom gradient color
    @Nullable public Color entityTopTint;       // Top gradient color

    // Particles
    @Nullable public ModelParticle[] particles;           // 3rd person particles
    @Nullable public ModelParticle[] firstPersonParticles; // 1st person particles

    // Effects
    @Nullable public String modelVFXId;         // Model VFX effect reference
    @Nullable public String screenEffect;       // Screen post-process effect
    @Nullable public String entityAnimationId;  // Animation override

    // Gameplay
    public float horizontalSpeedMultiplier;
    @Nullable public MovementEffects movementEffects;
    @Nullable public AbilityEffects abilityEffects;

    // Sound
    public int soundEventIndexLocal;
    public int soundEventIndexWorld;
}
```

### 2.2 Entity Effect Definition (JSON)
**Location**: `Assets/Server/Entity/Effects/Drop/Drop_Rare.json`

```json
{
  "ApplicationEffects": {
    "Particles": [
      {
        "SystemId": "Drop_Rare"
      }
    ],
    "ModelVFXId": "Drop_Rare",
    "EntityBottomTint": "#5875de"
  },
  "OverlapBehavior": "Extend",
  "Infinite": true
}
```

### 2.3 Quality Drop Effects

| Quality | Particle System | ModelVFX | Bottom Tint |
|---------|-----------------|----------|-------------|
| Rare | `Drop_Rare` | `Drop_Rare` | `#5875de` (Blue) |
| Epic | `Drop_Epic` | `Drop_Epic` | (Purple) |
| Legendary | `Drop_Legendary` | `Drop_Legendary` | `#887758` (Gold) |

---

## 3. Model Tinting and Gradients

### 3.1 Model Class
**Location**: `com/hypixel/hytale/protocol/Model.java`

```java
public class Model {
    @Nullable public String assetId;            // Model asset reference
    @Nullable public String path;               // Model file path
    @Nullable public String texture;            // Texture file path
    @Nullable public String gradientSet;        // Gradient set for tinting
    @Nullable public String gradientId;         // Specific gradient ID
    public float scale;                         // Model scale
    @Nullable public ModelParticle[] particles; // Attached particles
    @Nullable public ModelTrail[] trails;       // Attached trails
    @Nullable public ColorLight light;          // Light emission
    @Nullable public ModelAttachment[] attachments; // Armor/cosmetics
    // ...
}
```

### 3.2 ColorLight
**Location**: `com/hypixel/hytale/protocol/ColorLight.java`

Light emission from models:

```java
public class ColorLight {
    public byte radius;  // Light radius (0-255)
    public byte red;     // Red component
    public byte green;   // Green component
    public byte blue;    // Blue component
}
```

### 3.3 ModelAttachment
**Location**: `com/hypixel/hytale/protocol/ModelAttachment.java`

Attachments with independent tinting:

```java
public class ModelAttachment {
    @Nullable public String model;        // Attachment model
    @Nullable public String texture;      // Attachment texture
    @Nullable public String gradientSet;  // Gradient set for tinting
    @Nullable public String gradientId;   // Specific gradient
    public double weight;                 // Random selection weight
}
```

---

## 4. Item Drop Visual Effects

### 4.1 ItemEntityConfig
**Location**: `com/hypixel/hytale/protocol/ItemEntityConfig.java`

Controls dropped item appearance:

```java
public class ItemEntityConfig {
    @Nullable public String particleSystemId;  // Particle effect for drop
    @Nullable public Color particleColor;      // Particle color tint
    public boolean showItemParticles;          // Enable/disable particles
}
```

### 4.2 Quality-Based Drop Effects

From `Assets/Server/Item/Qualities/*.json`:

```json
// Legendary.json
{
  "QualityValue": 5,
  "TextColor": "#bb8a2c",
  "ItemEntityConfig": {
    "ParticleSystemId": "Drop_Legendary"
  },
  // ... tooltip, slot textures
}
```

| Quality | ParticleSystemId | Effect |
|---------|------------------|--------|
| Junk | - | No particles |
| Common | - | No particles |
| Uncommon | `Drop_Uncommon` | Green glow |
| Rare | `Drop_Rare` | Blue glow |
| Epic | `Drop_Epic` | Purple glow |
| Legendary | `Drop_Legendary` | Gold glow + sparkles |

---

## 5. Item Appearance Conditions

### 5.1 ItemAppearanceCondition
**Location**: `com/hypixel/hytale/protocol/ItemAppearanceCondition.java`

Conditional visual changes based on item state (durability, charge, etc.):

```java
public class ItemAppearanceCondition {
    // Visual Overrides
    @Nullable public ModelParticle[] particles;           // 3rd person particles
    @Nullable public ModelParticle[] firstPersonParticles; // 1st person
    @Nullable public String model;                        // Model override
    @Nullable public String texture;                      // Texture override
    @Nullable public String modelVFXId;                   // VFX effect

    // Condition
    @Nullable public FloatRange condition;                // Value range (0.0-1.0)
    @Nonnull public ValueType conditionValueType;         // Percent or Absolute

    // Sound
    public int localSoundEventId;
    public int worldSoundEventId;
}
```

### 5.2 Use Cases

**Low Durability Warning**:
```json
{
  "Condition": { "Min": 0.0, "Max": 0.25 },
  "ConditionValueType": "Percent",
  "Particles": [{ "SystemId": "Damage_Smoke" }],
  "ModelVFXId": "Item_Damaged"
}
```

**Enchanted Weapon Glow**:
```json
{
  "Condition": { "Min": 1.0, "Max": 1.0 },
  "ConditionValueType": "Absolute",
  "Particles": [{ "SystemId": "Enchant_Fire", "Color": "#FF4400" }],
  "ModelVFXId": "Enchant_Glow"
}
```

---

## 6. Inventory Slot Rendering

### 6.1 ItemQuality Slot Textures
**Location**: `com/hypixel/hytale/protocol/ItemQuality.java`

Each quality defines slot appearance:

```java
public class ItemQuality {
    @Nullable public String slotTexture;          // Normal slot background
    @Nullable public String blockSlotTexture;     // Block item slot
    @Nullable public String specialSlotTexture;   // Consumable/utility slot
    // ...
}
```

### 6.2 Slot Texture Assets

**Location**: `Assets/Common/UI/ItemQualities/Slots/`

| Quality | Slot Texture |
|---------|--------------|
| Common | `SlotCommon.png` |
| Uncommon | `SlotUncommon.png` |
| Rare | `SlotRare.png` |
| Epic | `SlotEpic.png` |
| Legendary | `SlotLegendary.png` |

### 6.3 ItemGridSlot Styling
**Location**: `com/hypixel/hytale/server/core/ui/ItemGridSlot.java`

```java
public class ItemGridSlot {
    private ItemStack itemStack;
    private Value<PatchStyle> background;   // Background styling
    private Value<PatchStyle> overlay;      // Overlay effects
    private Value<PatchStyle> icon;         // Icon styling
    private boolean skipItemQualityBackground; // Override quality background
    // ...
}
```

---

## 7. Color System

### 7.1 Color Class
**Location**: `com/hypixel/hytale/protocol/Color.java`

```java
public class Color {
    public byte red;   // 0-255
    public byte green; // 0-255
    public byte blue;  // 0-255
}
```

### 7.2 Creating Colors

```java
// RGB constructor
Color fireColor = new Color((byte)255, (byte)128, (byte)0);

// From hex (utility)
Color rareBlue = ColorParseUtil.parseColor("#5875de");
```

### 7.3 RPG Rarity Colors

| Rarity | Hex | RGB | Usage |
|--------|-----|-----|-------|
| Common | `#c9d2dd` | 201, 210, 221 | Gray text/particles |
| Uncommon | `#3e9049` | 62, 144, 73 | Green glow |
| Rare | `#2770b7` | 39, 112, 183 | Blue glow |
| Epic | `#8b339e` | 139, 51, 158 | Purple glow |
| Legendary | `#bb8a2c` | 187, 138, 44 | Gold glow |
| Mythic (custom) | `#ff4500` | 255, 69, 0 | Orange-red glow |

---

## 8. RPG Integration Strategies

### 8.1 Enchanted Weapon Effects

Attach particles to equipped weapons:

```java
// Create particle effect for fire enchant
ModelParticle fireParticle = new ModelParticle();
fireParticle.systemId = "Enchant_Fire";
fireParticle.scale = 0.5f;
fireParticle.color = new Color((byte)255, (byte)100, (byte)0);
fireParticle.targetEntityPart = EntityPart.PrimaryItem;
fireParticle.targetNodeName = "blade_tip";  // Weapon model node
fireParticle.detachedFromModel = false;
```

### 8.2 Rarity-Based Drop Glow

Configure item drops with quality-specific effects:

```java
// When spawning RPG item drop
ItemEntityConfig config = new ItemEntityConfig();
config.particleSystemId = getRarityParticleSystem(rarity);
config.particleColor = getRarityColor(rarity);
config.showItemParticles = rarity.ordinal() >= Rarity.UNCOMMON.ordinal();
```

### 8.3 Buff/Debuff Visual Indicators

Apply entity tints for status effects:

```java
// Poison debuff - green tint
ApplicationEffects poisonEffect = new ApplicationEffects();
poisonEffect.entityBottomTint = new Color((byte)50, (byte)180, (byte)50);
poisonEffect.entityTopTint = new Color((byte)30, (byte)100, (byte)30);
poisonEffect.particles = new ModelParticle[] {
    createParticle("Poison_Bubbles", EntityPart.Self)
};
```

### 8.4 Custom Quality Definitions

Create RPG-specific quality tiers:

```json
// Assets/Server/Item/Qualities/Mythic.json
{
  "QualityValue": 10,
  "TextColor": "#ff4500",
  "LocalizationKey": "server.rpg.qualities.Mythic",
  "ItemTooltipTexture": "UI/ItemQualities/Tooltips/ItemTooltipMythic.png",
  "SlotTexture": "UI/ItemQualities/Slots/SlotMythic.png",
  "VisibleQualityLabel": true,
  "ItemEntityConfig": {
    "ParticleSystemId": "Drop_Mythic"
  }
}
```

### 8.5 Programmatic Particle Spawning

Spawn particles for combat/loot events:

```java
// Critical hit effect
ParticleUtil.spawnParticleEffect(
    "Critical_Hit",
    targetPosition,
    0f, 0f, 0f,           // rotation
    2.0f,                  // scale
    new Color(255, 215, 0), // gold color
    null,                  // no source exclusion
    nearbyPlayers,
    componentAccessor
);

// Legendary drop announcement
ParticleUtil.spawnParticleEffect(
    "Drop_Legendary",
    dropPosition,
    componentAccessor
);
```

---

## 9. Key Findings Summary

| Question | Answer |
|----------|--------|
| Can we tint/recolor item models? | ✅ YES - `Model.gradientSet/gradientId`, `ApplicationEffects.entityTint` |
| How does the particle system work? | `ParticleUtil.spawnParticleEffect()`, `SpawnParticleSystem` packet (ID 152) |
| Can we add effects to dropped items? | ✅ YES - `ItemEntityConfig.particleSystemId` per quality |
| Can we modify inventory slot rendering? | ✅ YES - `ItemQuality.slotTexture`, `ItemGridSlot.background/overlay` |
| Can we attach effects to equipment? | ✅ YES - `ModelParticle` with `targetEntityPart`, `ItemAppearanceCondition` |

---

## 10. Visual Effect Capabilities

### 10.1 Server-Controlled Effects

| Effect Type | Class/Method | Server Control |
|-------------|--------------|----------------|
| World particles | `SpawnParticleSystem` packet | ✅ Full |
| Entity particles | `ApplicationEffects.particles` | ✅ Full |
| Entity tinting | `ApplicationEffects.entityTint` | ✅ Full |
| Item drop glow | `ItemEntityConfig` | ✅ Full |
| Model VFX | `ApplicationEffects.modelVFXId` | ✅ Full |
| Screen effects | `ApplicationEffects.screenEffect` | ✅ Full |

### 10.2 Asset-Defined Effects

| Effect Type | Asset Location | Customizable |
|-------------|----------------|--------------|
| Particle systems | `Assets/*/Particle/` | ⚠️ Requires asset files |
| Model VFX | `Assets/*/ModelVFX/` | ⚠️ Requires asset files |
| Slot textures | `Assets/Common/UI/ItemQualities/Slots/` | ⚠️ Requires asset files |
| Tooltip textures | `Assets/Common/UI/ItemQualities/Tooltips/` | ⚠️ Requires asset files |

### 10.3 Limitations

1. **Custom particle systems** require creating asset files
2. **Custom VFX shaders** are client-side only
3. **Model gradients** reference pre-defined gradient sets
4. **Slot textures** must be pre-loaded assets

---

## 11. Implementation Recommendations

### 11.1 For RPG Item Effects

1. **Use existing particle systems** from vanilla Hytale
2. **Apply color tints** to customize appearance
3. **Scale particles** for visual intensity
4. **Attach to EntityPart.PrimaryItem** for weapon effects

### 11.2 For Loot Drops

1. **Map RPG rarities** to Hytale quality tiers
2. **Use quality's ItemEntityConfig** for automatic glow
3. **Spawn additional particles** via `ParticleUtil` for special items

### 11.3 For Status Effects

1. **Use ApplicationEffects** for buff/debuff visuals
2. **Combine tinting + particles** for clear feedback
3. **Set appropriate duration** for temporary effects

---

## 12. File References

| File | Purpose |
|------|---------|
| `ModelParticle.java` | Particle attachment to models |
| `ModelTrail.java` | Trail effect attachment |
| `SpawnParticleSystem.java` | World particle spawning packet |
| `ParticleUtil.java` | Server-side particle utility |
| `ApplicationEffects.java` | Entity visual effects bundle |
| `ItemEntityConfig.java` | Dropped item particle config |
| `ItemAppearanceCondition.java` | Conditional item visuals |
| `Model.java` | Model with gradients/particles/trails |
| `ColorLight.java` | Light emission from models |
| `Color.java` | RGB color structure |
| `ItemQuality.java` | Quality slot textures |
| `ItemGridSlot.java` | UI slot styling |
| `Assets/Server/Entity/Effects/Drop/` | Drop effect definitions |
| `Assets/Server/Item/Qualities/` | Quality configurations |

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-01-23 | Initial research complete |
