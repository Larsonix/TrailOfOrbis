# Damage Indicators (Floating Combat Text)

Hytale's damage indicator system renders floating numbers when entities take damage. This doc covers the vanilla CombatText system, the DamageCause color mechanism, and how our plugin overrides the pipeline.

## EntityUI System Overview

Hytale has exactly **2 EntityUI types**, both defined as `EntityUIComponent` assets:

| Type | Asset | Purpose |
|------|-------|---------|
| `CombatText` | `Server/Entity/UI/CombatText.json` | Floating damage numbers |
| `EntityStat` | `Server/Entity/UI/Healthbar.json` | Health bars above entities |

Both are configured via JSON and rendered client-side. The server sends packets to trigger them.

## CombatText Asset Format

**Location**: `Server/Entity/UI/CombatText.json`

The vanilla CombatText configuration:

```json
{
  "Type": "CombatText",
  "TextColor": "#ffffff",
  "FontSize": 48,
  "Duration": 0.4,
  "RandomPositionOffsetRange": {
    "Min": { "X": 20, "Y": 10 },
    "Max": { "X": 60, "Y": 30 }
  },
  "HitAngleModifierStrength": 2.0,
  "AnimationEvents": [
    {
      "SubType": "Scale",
      "StartAt": 0.0,
      "EndAt": 1.0,
      "StartScaleValue": 1.0,
      "EndScaleValue": 0.5
    },
    {
      "SubType": "Position",
      "StartAt": 0.0,
      "EndAt": 1.0,
      "YOffset": -80
    },
    {
      "SubType": "Opacity",
      "StartAt": 0.5,
      "EndAt": 1.0,
      "StartOpacityValue": 1.0,
      "EndOpacityValue": 0.0
    }
  ]
}
```

### CombatText Fields (from `EntityUIComponent.json` Schema)

| Field | Type | Default | Range | Description |
|-------|------|---------|-------|-------------|
| `Type` | enum | — | `CombatText`, `EntityStat` | Must be `"CombatText"` |
| `TextColor` | hex string | `#ffffff` | — | Default text color (overridable per-DamageCause) |
| `FontSize` | integer | 68 | — | Font size in pixels |
| `Duration` | float | — | 0.1–10.0 | How long text is visible (seconds) |
| `RandomPositionOffsetRange` | object | — | — | Min/Max X,Y random offset for visual variety |
| `ViewportMargin` | integer | — | 0–200 | Margin from screen edges |
| `HitAngleModifierStrength` | float | — | 0–10 | How much numbers fly in hit direction |
| `AnimationEvents` | array | — | — | Animation keyframes (see below) |

### Animation Event Subtypes

| SubType | Fields | Description |
|---------|--------|-------------|
| `Scale` | `StartScaleValue`, `EndScaleValue` | Size change over time |
| `Position` | `XOffset`, `YOffset` | Drift direction (negative Y = upward) |
| `Opacity` | `StartOpacityValue`, `EndOpacityValue` | Fade in/out |

Each animation event has `StartAt` and `EndAt` (0.0–1.0) controlling when it plays within the Duration.

## CombatTextUpdate Protocol

**Class**: `com.hypixel.hytale.protocol.CombatTextUpdate`

The server→client packet that triggers floating combat text.

| Field | Type | Description |
|-------|------|-------------|
| `hitAngleDeg` | float | Direction the number flies (degrees, based on hit angle) |
| `text` | String (nullable) | The text content to display |

### Limitations

- **Plain text only** — no per-message color, size, font, or animation overrides
- **No rich formatting** — no bold, italic, or color tags in the text string
- **Global styling** — all combat text uses CombatText.json settings identically
- A crit hit and a normal hit look the same unless the text content differs

## Vanilla Combat Text Generation

**Class**: `DamageSystems.EntityUIEvents` (lines 827–887 in decompiled source)

The vanilla system converts damage events to combat text:

```java
// Simplified from DamageSystems.EntityUIEvents
float amount = damage.getAmount();
if (amount <= 0.0F) return;  // Skip zero/negative damage

String text = Integer.toString((int) Math.floor(amount));
// Sends CombatTextUpdate with hitAngleDeg from damage direction
```

Key behaviors:
- Converts `damage.getAmount()` to integer string (floors, no decimals)
- **Skips if amount <= 0** — this is the hook our suppressor exploits
- Hit angle is derived from the damage direction vector
- Text goes to nearby players observing the damaged entity

## Red Vignette (Defender Hit Indicator)

**Class**: `DamageSystems.PlayerHitIndicators` (lines 1400–1452)

When a player takes damage, a separate `DamageInfo` packet triggers a red screen flash:

| Field | Type | Description |
|-------|------|-------------|
| `damageCauseId` | int | The DamageCause type |
| `damageTextColor` | String | Color from DamageCause (see below) |
| `hitAngleDeg` | float | Direction indicator on screen |

This is the **defender-side** indicator (red vignette), separate from the **attacker-side** floating numbers.

## DamageTextColor on DamageCause

This is the most powerful customization point for combat text styling.

### How It Works

1. Each `DamageCause` asset (JSON) can define a `DamageTextColor` field (hex string)
2. When damage is dealt, `DamageCause.toPacket()` sends the color to the client
3. The client renders combat text in that color instead of CombatText.json's default white

### Schema Reference (`DamageCause.json`)

| Field | Type | Description |
|-------|------|-------------|
| `Parent` | string | Asset system parent (engine loader) |
| `Inherits` | string/null | Codec-level parent (resolves DurabilityLoss, StaminaLoss, etc.) |
| `DurabilityLoss` | boolean | Whether this damage causes item durability loss |
| `StaminaLoss` | boolean | Whether this damage costs stamina |
| `BypassResistances` | boolean | Ignores all resistance calculations |
| `DamageTextColor` | hex string | **Custom combat text color for this damage type** |

### Vanilla Damage Causes

**Location**: `Assets/Server/Entity/Damage/`

| Cause | DamageTextColor | Parent | Special Flags |
|-------|-----------------|--------|---------------|
| `Physical.json` | — (white) | — | DurabilityLoss, StaminaLoss |
| `Bludgeoning.json` | — | Physical | |
| `Slashing.json` | — | Physical | |
| `Elemental.json` | — | — | Base for Fire/Ice |
| `Fire.json` | — | Elemental | |
| `Ice.json` | — | Elemental | |
| `Poison.json` | **`#00FF00`** (green) | — | **Only vanilla cause with custom color** |
| `Projectile.json` | — | — | DurabilityLoss |
| `Command.json` | — | — | BypassResistances |
| `Environment.json` | — | — | BypassResistances |
| `Fall.json` | — | Environment | |
| `Drowning.json` | — | Environment | |
| `Suffocation.json` | — | Environment | |
| `OutOfWorld.json` | — | Environment | |
| `Environmental.json` | — | — | For hazards (bushes, cactus) |

### Our Custom Damage Causes

Located in `TrailOfOrbis_Realms/Server/Entity/Damage/`:

| File | DamageTextColor | Purpose |
|------|-----------------|---------|
| `Rpg_Physical.json` | `#FFFFFF` (white) | RPG physical damage |
| `Rpg_Physical_Crit.json` | `#FF4444` (red) | RPG physical critical hit |
| `Rpg_Magic.json` | `#44AAFF` (blue) | RPG magic/spell damage |
| `Rpg_Magic_Crit.json` | `#FF44FF` (purple) | RPG magic critical hit |
| `Rpg_Fire.json` | `#FF6600` (orange) | Fire elemental damage |
| `Rpg_Fire_Crit.json` | `#FF2200` (bright red) | Fire critical hit |
| `Rpg_Water.json` | `#44CCFF` (light blue) | Water elemental damage |
| `Rpg_Water_Crit.json` | `#88EEFF` (bright cyan) | Water critical hit |
| `Rpg_Lightning.json` | `#FFEE00` (yellow) | Lightning elemental damage |
| `Rpg_Lightning_Crit.json` | `#FFFFAA` (bright yellow) | Lightning critical hit |
| `Rpg_Earth.json` | `#CC8833` (brown) | Earth elemental damage |
| `Rpg_Earth_Crit.json` | `#FFAA44` (bright brown) | Earth critical hit |
| `Rpg_Wind.json` | `#88FF88` (light green) | Wind elemental damage |
| `Rpg_Wind_Crit.json` | `#CCFFCC` (bright green) | Wind critical hit |
| `Rpg_Void.json` | `#AA44FF` (purple) | Void elemental damage |
| `Rpg_Void_Crit.json` | `#DD88FF` (bright purple) | Void critical hit |

All 16 files use both `"Parent": "Physical"` and `"Inherits": "Physical"` (see "Parent vs Inherits" below).

### "Parent" vs "Inherits" — Two Separate Inheritance Keys

Hytale DamageCause assets use TWO different inheritance mechanisms:

| Key | Mechanism | Purpose | Read by |
|-----|-----------|---------|---------|
| `"Parent"` | Hytale asset system (`hytaleParent`) | Asset registry parent lookup | Engine's generic asset loader |
| `"Inherits"` | DamageCause-specific codec field | Java-level field inheritance (DurabilityLoss, StaminaLoss, etc.) | `DamageCause.java` codec |

**Every vanilla DamageCause that inherits uses BOTH.** If you only set `"Parent"` without `"Inherits"`, the Java codec may not resolve inherited properties like `DurabilityLoss`, `StaminaLoss`, and `DamageTextColor`.

```json
// CORRECT - both keys present (matches vanilla pattern)
{
  "Parent": "Physical",
  "Inherits": "Physical",
  "DurabilityLoss": true,
  "StaminaLoss": true,
  "DamageTextColor": "#FF6600"
}
```

### DamageType → DamageCause Pipeline

The Java enum `DamageType` maps each damage type to a pair of DamageCause assets:

| DamageType | Normal Cause | Crit Cause | Resolution |
|------------|-------------|------------|------------|
| `PHYSICAL` | `Rpg_Physical` | `Rpg_Physical_Crit` | Default for physical-dominant damage |
| `MAGIC` | `Rpg_Magic` | `Rpg_Magic_Crit` | For spell damage |
| `FIRE` | `Rpg_Fire` | `Rpg_Fire_Crit` | When fire is the dominant element |
| `WATER` | `Rpg_Water` | `Rpg_Water_Crit` | When water is the dominant element |
| `LIGHTNING` | `Rpg_Lightning` | `Rpg_Lightning_Crit` | When lightning is the dominant element |
| `EARTH` | `Rpg_Earth` | `Rpg_Earth_Crit` | When earth is the dominant element |
| `WIND` | `Rpg_Wind` | `Rpg_Wind_Crit` | When wind is the dominant element |
| `VOID` | `Rpg_Void` | `Rpg_Void_Crit` | When void is the dominant element |

Resolution: `DamageDistribution.getPrimaryDamageType()` picks the element with the highest damage. If physical damage exceeds all individual elemental values, `PHYSICAL` is used.

### Open Questions

1. **Attacker vs Defender text via DamageCause**: `DamageTextColor` is confirmed sent in the `DamageInfo` packet (defender-side). The `CombatTextUpdate` packet (attacker-side) has **no color field** — only `hitAngleDeg` and `text`. **Resolved by template swap**: Our plugin bypasses this limitation entirely by swapping the EntityUI template on the attacker's viewer before sending `CombatTextUpdate`. The template's `combatTextColor` is used by the client renderer. See "Entity UI Components Protocol Layer" above.

2. **Client-side crit rendering**: **Resolved**: `CombatText.ui` is a minimal centered bold label with no crit-specific logic. Any crit distinction must come from server-side config (`CombatText.json` animation) or `DamageTextColor`. Red text in community mod videos likely comes from a custom `DamageTextColor` on a custom DamageCause, not client-side crit rendering.

3. **Color inheritance**: Does `DamageTextColor` inherit via `"Inherits"` from Parent? We explicitly set `DamageTextColor` on all our custom causes to avoid relying on inheritance. Vanilla `Elemental` has no color set, so child causes like `Fire.json` default to white.

## Client-Side Rendering (from Client Data)

The client renders combat text using `Client/Data/Game/Interface/InGame/EntityUI/CombatText.ui`:

```
Group #Container {
  LayoutMode: Middle;
  Anchor: (Width: 200, Height: 200);
  Padding: (Horizontal: 3, Vertical: 3);

  Label #Text {
    Style: (HorizontalAlignment: Center, VerticalAlignment: Center, RenderBold: true);
  }
}
```

**Key findings**:
- The `.ui` template is purely structural — a centered bold label in a 200x200 container
- No crit-specific rendering, no conditional colors, no animation in the template
- All dynamic behavior (color, size, animation, fade) comes from the `CombatText.json` server asset
- The `DamageTextColor` from `DamageInfo` packets overrides the default color at render time
- This confirms that per-message styling is impossible via the `.ui` template — only via server assets

**Related client files**:
- `ClientData/Game/Interface/InGame/EntityUI/HealthBar.ui` — entity health bar
- `ClientData/Game/Interface/InGame/Tooltips/ItemTooltip.ui` — item tooltip rendering
- `ClientData/Shared/Language/en-US/client.lang` — localization strings including `itemTooltip.damageClass.*`

See `docs/hytale-api/client-data.md` for the full client data reference.

## Entity UI Components Protocol Layer

This section covers the protocol classes that bridge server-side EntityUI assets and client-side rendering. Understanding this layer is essential for runtime customization of combat text (color, size, animation) beyond what static JSON assets allow.

### Architecture Overview

```
Server-side Assets                  Protocol Layer                    Client
─────────────────                  ──────────────                    ──────
EntityUIComponent (asset)          UpdateEntityUIComponents          CombatText.ui
  └─ CombatTextUIComponent           (Packet ID 73)                   (Label)
       ├─ TextColor              ──► Map<int, EntityUIComponent>  ──► Renders with
       ├─ FontSize                   key = template index              template settings
       ├─ Duration                   val = protocol EntityUIComponent
       └─ AnimationEvents

UIComponentList (ECS)              UIComponentsUpdate                Per-entity
  └─ int[] componentIds         ──► int[] components              ──► Which template
       (maps slot → template)       (sent per tracked entity)         this entity uses
```

### UpdateEntityUIComponents Packet

**Class**: `com.hypixel.hytale.protocol.packets.assets.UpdateEntityUIComponents`
**Packet ID**: 73 (compressed)

Server-to-client packet that defines or updates EntityUI template definitions. The client maintains an indexed table of templates; this packet populates it.

| Field | Type | Description |
|-------|------|-------------|
| `type` | `UpdateType` enum | `Init`, `AddOrUpdate`, or `Remove` |
| `maxId` | int | Next available template index (client allocates array to this size) |
| `components` | `Map<Integer, EntityUIComponent>` (nullable) | Template index -> template definition |

**When it's sent:**

| Scenario | UpdateType | Trigger |
|----------|------------|---------|
| Player joins | `Init` | Engine sends all loaded EntityUI assets as part of the asset sync handshake |
| Asset pack reload | `AddOrUpdate` | `LoadedAssetsEvent` triggers `EntityUIComponentPacketGenerator.generateUpdatePacket()` |
| Plugin custom template | `AddOrUpdate` | Plugin sends via `playerRef.getPacketHandler().writeNoCache(...)` |
| Asset removed | `Remove` | Engine generates with empty `EntityUIComponent` placeholders |

**Key detail**: The `maxId` field tells the client the total capacity of the template table. When injecting custom templates at indices beyond the server's asset map, `maxId` must be set to at least `highestCustomIndex + 1` or the client silently ignores templates at higher indices.

```java
// Sending a custom template to a player (indices beyond the asset map)
int customIndex = serverAssetMap.getNextIndex(); // e.g., 2
EntityUIComponent template = vanillaTemplate.clone();
template.combatTextColor = new Color((byte) 0xFF, (byte) 0x44, (byte) 0x44); // red

playerRef.getPacketHandler().writeNoCache(
    new UpdateEntityUIComponents(
        UpdateType.AddOrUpdate,
        customIndex + 1,  // maxId must exceed highest index
        Map.of(customIndex, template)
    )
);
```

### EntityUIComponent (Protocol)

**Class**: `com.hypixel.hytale.protocol.EntityUIComponent`

The wire format for a single EntityUI template. This is a **flat structure** — both `CombatText` and `EntityStat` fields are present, but only the relevant ones are populated based on `type`.

| Field | Type | Null? | Description |
|-------|------|-------|-------------|
| `type` | `EntityUIType` | No | `CombatText` (1) or `EntityStat` (0) |
| `hitboxOffset` | `Vector2f` | Yes | Offset from entity hitbox center |
| `unknown` | boolean | No | Set to `true` for unknown/placeholder components |
| `entityStatIndex` | int | No | Index into EntityStatType asset map (for `EntityStat` type) |
| `combatTextRandomPositionOffsetRange` | `RangeVector2f` | Yes | Min/Max random offset range |
| `combatTextViewportMargin` | float | No | Margin from screen edges (px) |
| `combatTextDuration` | float | No | How long text is visible (seconds) |
| `combatTextHitAngleModifierStrength` | float | No | Hit direction influence (0.0--10.0) |
| `combatTextFontSize` | float | No | Font size in pixels |
| `combatTextColor` | `Color` (3 bytes: R, G, B) | Yes | Text color (null = white) |
| `combatTextAnimationEvents` | `CombatTextEntityUIComponentAnimationEvent[]` | Yes | Animation keyframes |

**EntityUIType enum** (`com.hypixel.hytale.protocol.EntityUIType`):

| Value | Name | Purpose |
|-------|------|---------|
| 0 | `EntityStat` | Health bars above entities |
| 1 | `CombatText` | Floating damage numbers |

**Color class** (`com.hypixel.hytale.protocol.Color`): Fixed 3-byte struct with `red`, `green`, `blue` fields (signed bytes; use `(byte) 0xFF` for 255).

### CombatTextEntityUIComponentAnimationEvent (Protocol)

**Class**: `com.hypixel.hytale.protocol.CombatTextEntityUIComponentAnimationEvent`

Fixed-size (34 bytes) wire format for a single animation keyframe. All fields are always serialized; unused fields for a given type are zeroed.

| Field | Type | Description |
|-------|------|-------------|
| `type` | `CombatTextEntityUIAnimationEventType` | `Scale` (0), `Position` (1), or `Opacity` (2) |
| `startAt` | float | Animation start time (0.0--1.0 fraction of duration) |
| `endAt` | float | Animation end time (0.0--1.0 fraction of duration) |
| `startScale` | float | Starting scale factor (Scale type) |
| `endScale` | float | Ending scale factor (Scale type) |
| `positionOffset` | `Vector2f` (nullable) | Drift direction in pixels (Position type; negative Y = upward) |
| `startOpacity` | float | Starting opacity 0.0--1.0 (Opacity type) |
| `endOpacity` | float | Ending opacity 0.0--1.0 (Opacity type) |

**CombatTextEntityUIAnimationEventType enum** (`com.hypixel.hytale.protocol.CombatTextEntityUIAnimationEventType`):

| Value | Name | Used fields |
|-------|------|-------------|
| 0 | `Scale` | `startScale`, `endScale` |
| 1 | `Position` | `positionOffset` (Vector2f with X, Y) |
| 2 | `Opacity` | `startOpacity`, `endOpacity` |

### Server-Side Asset Classes

The server loads `EntityUIComponent` assets from `Server/Entity/UI/*.json` and converts them to protocol packets via `toPacket()`. The server-side classes mirror the protocol but use Hytale's `BuilderCodec` system for JSON deserialization.

| Server Class | Package | Protocol Counterpart |
|-------------|---------|---------------------|
| `EntityUIComponent` (abstract) | `...entityui.asset` | `EntityUIComponent` (protocol) |
| `CombatTextUIComponent` | `...entityui.asset` | `EntityUIComponent` with `type = CombatText` |
| `EntityStatUIComponent` | `...entityui.asset` | `EntityUIComponent` with `type = EntityStat` |
| `CombatTextUIComponentAnimationEvent` (abstract) | `...entityui.asset` | `CombatTextEntityUIComponentAnimationEvent` |
| `CombatTextUIComponentScaleAnimationEvent` | `...entityui.asset` | Animation with `type = Scale` |
| `CombatTextUIComponentPositionAnimationEvent` | `...entityui.asset` | Animation with `type = Position` |
| `CombatTextUIComponentOpacityAnimationEvent` | `...entityui.asset` | Animation with `type = Opacity` |

**Key method**: `EntityUIComponent.toPacket()` converts a server asset to the protocol representation. Results are cached via `SoftReference` — the same protocol packet is reused until garbage collected.

**Asset map**: `EntityUIComponent.getAssetMap()` returns an `IndexedLookupTableAssetMap<String, EntityUIComponent>` — each loaded asset gets an integer index. This index is what entities reference in their `UIComponentList`.

### UIComponentList (ECS Component)

**Class**: `com.hypixel.hytale.server.core.modules.entityui.UIComponentList`

An ECS component attached to entities that defines which EntityUI templates render on them. Contains an `int[] componentIds` array where each position maps a slot to a template index in the asset map.

```java
// Reading an entity's UI component list
UIComponentList uiList = store.getComponent(entityRef, UIComponentList.getComponentType());
int[] ids = uiList.getComponentIds();
// ids[0] might be 0 (Healthbar), ids[1] might be 1 (CombatText)
```

The `UIComponentList` codec reads from entity JSON:
```json
{
  "Components": ["Healthbar", "CombatText"]
}
```

String names are resolved to integer indices via the asset map during `update()`.

### UIComponentsUpdate (Per-Entity Swap)

**Class**: `com.hypixel.hytale.protocol.UIComponentsUpdate`

A `ComponentUpdate` sent per tracked entity that overrides the entity's `UIComponentList` on a specific viewer. This is the mechanism for showing different combat text templates to different players for the same entity.

| Field | Type | Description |
|-------|------|-------------|
| `components` | `int[]` | New component IDs array (replaces the entity's UIComponentList indices) |

**Usage**: Queue on the attacker's `EntityViewer` for the defender entity:

```java
EntityTrackerSystems.EntityViewer viewer =
    store.getComponent(attackerRef, EntityTrackerSystems.EntityViewer.getComponentType());

// Clone defender's current component IDs and swap CombatText index
int[] modifiedIds = currentIds.clone();
modifiedIds[combatTextSlot] = customTemplateIndex;

// Send to attacker only — other players see the original template
viewer.queueUpdate(defenderRef, new UIComponentsUpdate(modifiedIds));
```

**Critical ordering**: The `UIComponentsUpdate` is queued and flushed on the next entity tracker tick. If you also send a `CombatTextUpdate` (the actual damage number) in the same tick via `viewer.queueUpdate()`, the template swap and the text display are batched together, so the text renders with the swapped template's visual properties.

### EntityUIModule (Registration)

**Class**: `com.hypixel.hytale.server.core.modules.entityui.EntityUIModule`

The core plugin that registers the EntityUI asset system. Key registration during `setup()`:

- Registers `EntityUIComponent` asset store at path `Entity/UI` with `IndexedLookupTableAssetMap`
- Registers codec subtypes: `"CombatText"` -> `CombatTextUIComponent`, `"EntityStat"` -> `EntityStatUIComponent`
- Registers animation event subtypes: `"Scale"`, `"Position"`, `"Opacity"`
- Registers `UIComponentList` as an ECS component type
- Registers `UIComponentSystems` (Setup, Update, Remove) for lifecycle management
- Uses `EntityUIComponentPacketGenerator` to convert assets to packets for the asset sync protocol

### Registering Custom Combat Text Templates (Plugin Pattern)

Plugins can register custom combat text templates by sending `UpdateEntityUIComponents` packets with indices beyond the server's asset map range. This technique works because the client's template table is sized by `maxId`, not by the server's actual asset count.

**Step 1: Find the vanilla CombatText template**

```java
var assetMap = EntityUIComponent.getAssetMap();
int vanillaIndex = -1;
EntityUIComponent vanillaPacket = null;

for (int i = 0; i < assetMap.getNextIndex(); i++) {
    var asset = assetMap.getAsset(i);
    if (asset instanceof CombatTextUIComponent) {
        vanillaIndex = i;
        vanillaPacket = asset.toPacket();
        break;
    }
}
```

**Step 2: Build custom template variants**

```java
// Clone and customize — each variant gets its own client-side index
int nextIndex = assetMap.getNextIndex();

EntityUIComponent fireTemplate = vanillaPacket.clone();
fireTemplate.combatTextColor = new Color((byte) 0xFF, (byte) 0x66, (byte) 0x00);
fireTemplate.combatTextFontSize = 72.0f;  // slightly larger
int fireIndex = nextIndex++;

EntityUIComponent critTemplate = vanillaPacket.clone();
critTemplate.combatTextColor = new Color((byte) 0xFF, (byte) 0x44, (byte) 0x44);
critTemplate.combatTextFontSize = 80.0f;  // larger for crits
critTemplate.combatTextDuration = 0.6f;   // linger longer
int critIndex = nextIndex++;
```

**Step 3: Sync to players on connect**

```java
// Send all custom templates in a single packet
Map<Integer, EntityUIComponent> templates = Map.of(
    fireIndex, fireTemplate,
    critIndex, critTemplate
);

playerRef.getPacketHandler().writeNoCache(
    new UpdateEntityUIComponents(UpdateType.AddOrUpdate, nextIndex, templates)
);
```

**Step 4: Apply per-hit by swapping the entity's template index**

```java
// Before sending CombatTextUpdate, swap the defender's CombatText slot
UIComponentList uiList = store.getComponent(defenderRef, UIComponentList.getComponentType());
int[] ids = uiList.getComponentIds().clone();

// Find the CombatText position and replace with our custom template
for (int i = 0; i < ids.length; i++) {
    if (ids[i] == vanillaIndex) {
        ids[i] = fireIndex;  // or critIndex, based on damage type
        break;
    }
}

// Queue the swap, then the text — both flush on the same tracker tick
viewer.queueUpdate(defenderRef, new UIComponentsUpdate(ids));
viewer.queueUpdate(defenderRef, new CombatTextUpdate(hitAngle, "42"));
```

### Two Approaches for Colored Text

| Approach | Mechanism | Scope | Pros | Cons |
|----------|-----------|-------|------|------|
| **Per-entity swap** | `UIComponentsUpdate` via `viewer.queueUpdate()` | One entity, one viewer | Per-viewer isolation; precise | Requires entity to have `UIComponentList` |
| **Global template overwrite** | `UpdateEntityUIComponents` via `writeNoCache()` | All entities for one player | Always works; no UIComponentList needed | Affects all combat text until reverted |

Our plugin tries per-entity swap first (Approach B) and falls back to global template overwrite (Approach A) when the entity lacks a `UIComponentList`.

## Our Plugin: Combat Indicator Pipeline

### Architecture

```
Damage Event
    │
    ▼
RPGDamageIndicatorSuppressor (ECS system, runs BEFORE vanilla EntityUIEvents)
    │  Sets damage.setAmount(0) for RPG-processed damage
    │  Vanilla EntityUIEvents skips amount <= 0
    │
    ▼
CombatIndicatorService
    ├── sendAttackerCombatText()  → Custom CombatTextUpdate to attacker
    │       └── CombatTextColorManager.applyAndResolve()
    │               ├── CombatTextProfileResolver → picks color profile from breakdown
    │               ├── tryPerEntitySwap()         → UIComponentsUpdate (preferred)
    │               └── applyTemplateOverwriteFallback() → UpdateEntityUIComponents
    ├── sendDefenderIndicator()   → DamageInfo packet (red vignette)
    └── sendDetailedBreakdownChat() → Rich chat messages
                                      └── CombatLogFormatter (color-coded breakdown)

CombatTextTemplateRegistry (init at startup)
    ├── Scans asset map for vanilla CombatText template
    ├── Builds colored variants at indices beyond the asset map
    └── Syncs all templates to each player on connect (PlayerReadyEvent)
```

### RPGDamageIndicatorSuppressor

**File**: `RPGDamageIndicatorSuppressor.java` (113 lines)

- Runs as an ECS system **before** vanilla `DamageSystems.EntityUIEvents`
- Checks for `INDICATORS_SENT` metadata flag on damage events
- If our plugin already handled the indicators, sets `damage.setAmount(0)` so vanilla skips
- This prevents double combat text (our custom + vanilla default)

### CombatIndicatorService

**File**: `CombatIndicatorService.java` (528 lines)

Formats and sends all three indicator types:

| Method | What It Sends | Format |
|--------|---------------|--------|
| `sendAttackerCombatText()` | `CombatTextUpdate` packet | Number, "Miss", "Dodged", "Blocked", "Parried", crit "!" suffix |
| `sendDefenderIndicator()` | `DamageInfo` packet | Red vignette flash on defender screen |
| `sendDetailedBreakdownChat()` | Chat messages | Full damage calculation chain |

### CombatLogFormatter

**File**: `CombatLogFormatter.java` (665 lines)

Generates rich color-coded chat breakdowns when players enable `/rpg combat detail`:

- "DAMAGE DEALT" header (gold) with full calculation chain
- Base → flat bonuses → % increased → elemental → crit → armor → resistance → total
- "DAMAGE TAKEN" header (red) with mitigation breakdown
- Each modifier shows the exact value and percentage

### Current Formatting Capabilities

`CombatTextUpdate` only carries plain text (no color field), but the EntityUI template swap system provides full per-message visual control:

| Feature | Status | Mechanism |
|---------|--------|-----------|
| Per-message color (attacker) | **Working** | Template swap via `UIComponentsUpdate` or `UpdateEntityUIComponents` |
| Defender hit indicator color | **Working** | `DamageTextColor` on custom DamageCause assets (16 files) |
| Elemental colors | **Working** | Per-element `CombatTextProfile` with distinct template index |
| Crit visual distinction | **Working** | Separate crit profiles with brighter colors, larger font, + "!" text suffix |
| Size variation | **Working** | Per-profile `combatTextFontSize` in custom templates |
| Animation variation | **Working** | Per-profile `combatTextAnimationEvents` in custom templates |
| Avoidance styling | **Working** | Dedicated profiles (e.g., "dodged" = gray, smaller font, slow fade) |

**What IS working**:
- Text content is correct (damage numbers, avoidance words, crit indicator)
- Per-hit colored combat text via EntityUI template swap (color, size, animation per damage type)
- Red vignette flash on defender screen
- Hit angle direction (numbers fly in correct direction)
- Chat breakdowns with full color-coding (via `/rpg combat detail`)

## Customization Options

### Asset Pack Override (Global Styling)

Override `Server/Entity/UI/CombatText.json` in your asset pack to change:
- Default text color (affects ALL combat text identically)
- Font size, duration
- Animation behavior (scale, drift, fade)
- Random position offset range

**Limitation**: Cannot differentiate crits from normal hits at the asset level.

### DamageCause Color (Per-Damage-Type)

Add `DamageTextColor` to custom DamageCause JSON files for per-element coloring. This is the only way to get different colors for different damage types without client modding.

### Java Plugin Suppression + Template Swap (Full Control)

Suppress vanilla combat text and send custom `CombatTextUpdate` packets with arbitrary text content. Combine with the EntityUI template swap system (see "Entity UI Components Protocol Layer" above) for full per-message control over text content, color, font size, duration, and animation. This is what our plugin does via `CombatTextColorManager`.

## Key Source Files

### Decompiled Hytale Classes

| File | Package | Purpose |
|------|---------|---------|
| `UpdateEntityUIComponents.java` | `protocol.packets.assets` | Packet ID 73: defines/updates EntityUI templates on client |
| `EntityUIComponent.java` | `protocol` | Wire format for a single template (color, fontSize, animations) |
| `CombatTextEntityUIComponentAnimationEvent.java` | `protocol` | Wire format for animation keyframes (34 bytes) |
| `CombatTextEntityUIAnimationEventType.java` | `protocol` | Enum: `Scale` (0), `Position` (1), `Opacity` (2) |
| `EntityUIType.java` | `protocol` | Enum: `EntityStat` (0), `CombatText` (1) |
| `UIComponentsUpdate.java` | `protocol` | Per-entity component ID swap (`int[]`) |
| `CombatTextUpdate.java` | `protocol` | Floating text packet: hitAngleDeg + text |
| `Color.java` | `protocol` | 3-byte RGB struct |
| `EntityUIComponent.java` | `...entityui.asset` | Server-side asset base class with `toPacket()` |
| `CombatTextUIComponent.java` | `...entityui.asset` | Server asset: CombatText JSON -> protocol conversion |
| `CombatTextUIComponentAnimationEvent.java` | `...entityui.asset` | Animation event base class with `generatePacket()` |
| `UIComponentList.java` | `...entityui` | ECS component: `int[] componentIds` per entity |
| `EntityUIModule.java` | `...entityui` | Core plugin: asset registration + system setup |
| `EntityUIComponentPacketGenerator.java` | `...entityui.asset` | Generates Init/Update/Remove packets from asset map |
| `DamageSystems.java` | Decompiled server | EntityUIEvents (lines 827--887), PlayerHitIndicators (1400--1452) |
| `DamageCause.java` (protocol) | `protocol` | Packet: id + damageTextColor |
| `DamageCause.java` (server) | `...damage` | Asset with `toPacket()` sending damageTextColor |

### Assets and Schemas

| File | Location | Purpose |
|------|----------|---------|
| `CombatText.json` | `Assets/Server/Entity/UI/` | Vanilla combat text config |
| `CombatText.ui` | `ClientData/Game/Interface/InGame/EntityUI/` | Client-side combat text template |
| `EntityUIComponent.json` | `Assets/Schema/` | Schema for CombatText + EntityStat |
| `DamageCause.json` | `Assets/Schema/` | Schema with DamageTextColor field |

### Our Plugin

| File | Package | Purpose |
|------|---------|---------|
| `CombatTextColorManager.java` | `combat.indicators.color` | Orchestrates colored combat text (init, player sync, apply) |
| `CombatTextTemplateRegistry.java` | `combat.indicators.color` | Registers custom templates at client-side indices |
| `CombatTextProfileResolver.java` | `combat.indicators.color` | DamageBreakdown -> profile resolution |
| `CombatTextProfile.java` | `combat.indicators.color` | Immutable record: id, color, fontSize, duration, animations, templateIndex |
| `CombatTextAnimation.java` | `combat.indicators.color` | Record: animation keyframe (SCALE, POSITION, OPACITY) |
| `CombatTextColorConfig.java` | `combat.indicators.color` | YAML config: profiles, enabled flag |
| `RPGDamageIndicatorSuppressor.java` | `combat.indicators` | Vanilla suppression ECS system |
| `CombatIndicatorService.java` | `combat.indicators` | Custom indicator formatting + sending + color integration |
| `CombatLogFormatter.java` | `combat.indicators` | Rich chat damage breakdowns |
| `TooAdminTestColorCommand.java` | `commands.tooadmin` | `/tooadmin testcolor` — test command for color hacks |

## Quick Lookup

```bash
# Find CombatText asset
cat /home/larsonix/work/Hytale-Decompiled-Full-Game/Assets/Server/Entity/UI/CombatText.json

# Find DamageCause schema
cat /home/larsonix/work/Hytale-Decompiled-Full-Game/Assets/Schema/DamageCause.json

# Find all vanilla damage causes
ls /home/larsonix/work/Hytale-Decompiled-Full-Game/Assets/Server/Entity/Damage/

# Find EntityUI protocol classes
grep "^UpdateEntityUIComponents\|^EntityUIComponent\|^CombatTextUpdate\|^UIComponentsUpdate\|^CombatTextEntityUI" /home/larsonix/work/Hytale-Decompiled-Full-Game/.index/CLASS_INDEX.txt

# Find EntityUI server-side asset classes
grep "^CombatTextUIComponent\|^UIComponentList\|^EntityUIModule\|^EntityUIComponentPacketGenerator" /home/larsonix/work/Hytale-Decompiled-Full-Game/.index/CLASS_INDEX.txt

# Find our colored combat text system
grep "CombatText" /home/larsonix/work/trail-of-orbis/.index/CLASS_INDEX.txt

# Read client-side CombatText.ui template
cat /home/larsonix/work/Hytale-Decompiled-Full-Game/ClientData/Game/Interface/InGame/EntityUI/CombatText.ui

# Search client localization for damage-related strings
grep "damage" /home/larsonix/work/Hytale-Decompiled-Full-Game/.index/CLIENT_LANG_INDEX.txt
```
