# Hytale Item Tooltip System Research

> **Deep Dive**: Item display, tooltips, names, formatting, coloring, and customization
> **Date**: 2026-01-23
> **Purpose**: Understand how to implement RPG gear tooltips with stats

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Tooltip Architecture](#tooltip-architecture)
3. [Item Quality System](#item-quality-system)
4. [Item Names and Descriptions](#item-names-and-descriptions)
5. [Text Formatting and Colors](#text-formatting-and-colors)
6. [ItemStack Metadata System](#itemstack-metadata-system)
7. [Message API](#message-api)
8. [Implementation Options for RPG Tooltips](#implementation-options-for-rpg-tooltips)
9. [Key Files Reference](#key-files-reference)

---

## Executive Summary

### Key Findings

| Aspect | Finding | Impact on RPG System |
|--------|---------|---------------------|
| **Tooltip Rendering** | Quality-based with texture overlays | Can use quality system for rarity colors |
| **Item Names** | Translation keys only, NO per-item custom names | Cannot set "Fiery Iron Sword" directly |
| **Text Colors** | RGB per quality tier, single color for all text | Rarity color applies to whole tooltip |
| **Rich Text** | FormattedMessage supports bold/italic/color | May work for chat/UI, unclear for tooltips |
| **Metadata** | BSON storage for arbitrary key-value pairs | Can store RPG data, but client ignores it |
| **Custom Tooltips** | No evident API for adding tooltip lines | Major limitation - needs workaround |

### Critical Limitation

**Hytale does NOT support custom tooltip lines per-item.** The tooltip system is:
- Quality-driven (texture + color from ItemQuality)
- Translation-key-based (name/description are i18n keys, not raw text)
- Asset-defined (tooltip content comes from Item asset, not ItemStack)

### Potential Workarounds

1. **Custom Quality per Rarity** - Create RPG quality assets with rarity colors
2. **Custom UI Page** - `/gear info` opens detailed stats page (already implemented)
3. **Chat Messages** - Send formatted stats on hover/pickup via Message API
4. **Client Mod** - Would require client-side modification (not viable for vanilla)

---

## Tooltip Architecture

### How Tooltips Work

Hytale's tooltip system is **asset-driven**, not runtime-customizable:

```
Item Asset (JSON)
    ├── qualityId → ItemQuality Asset
    │       ├── itemTooltipTexture (background image)
    │       ├── itemTooltipArrowTexture (arrow decoration)
    │       ├── textColor (RGB for all text)
    │       └── localizationKey ("Rare", "Legendary", etc.)
    │
    └── translationProperties
            ├── name → "server.items.Weapon_Sword_Iron.name"
            └── description → "server.items.Weapon_Sword_Iron.description"
```

### Tooltip Components

From `ItemQuality.java` (protocol):

```java
public class ItemQuality {
    public int qualityValue;                    // Sort order
    public String itemTooltipTexture;           // Background texture path
    public String itemTooltipArrowTexture;      // Arrow/pointer texture
    public String slotTexture;                  // Inventory slot background
    public String blockSlotTexture;             // Slot for block items
    public String specialSlotTexture;           // Consumable slot texture
    public Color textColor;                     // Text color (RGB)
    public String localizationKey;              // Quality label translation key
    public boolean visibleQualityLabel;         // Show quality name
    public boolean renderSpecialSlot;           // Use special texture
    public boolean hideFromSearch;              // Hide from creative menu
    public ItemEntityConfig itemEntityConfig;   // Drop particle effects
}
```

### Tooltip Rendering Flow

1. Client receives `ItemBase` packet with `qualityIndex`
2. Client looks up `ItemQuality` from cached quality map
3. Client renders tooltip using:
   - `itemTooltipTexture` as background
   - `textColor` for all text
   - Resolved translation strings from `translationProperties`
   - Quality label if `visibleQualityLabel` is true

---

## Item Quality System

### Quality Assets Location

```
/Assets/Server/Item/Qualities/
├── Default.json      (gray, #c9d2dd)
├── Uncommon.json     (light blue, #4fa1d0)
├── Rare.json         (blue, #2770b7)
├── Epic.json         (purple, expected)
├── Legendary.json    (gold, #bb8a2c)
└── Mythic.json       (if exists)
```

### Example Quality Definition

**Rare.json**:
```json
{
  "QualityValue": 3,
  "ItemTooltipTexture": "UI/ItemQualities/Tooltips/ItemTooltipRare.png",
  "ItemTooltipArrowTexture": "UI/ItemQualities/Tooltips/ItemTooltipRareArrow.png",
  "SlotTexture": "UI/ItemQualities/Slots/SlotRare.png",
  "BlockSlotTexture": "UI/ItemQualities/Slots/SlotRare.png",
  "SpecialSlotTexture": "UI/ItemQualities/Slots/SlotRare.png",
  "TextColor": "#2770b7",
  "LocalizationKey": "server.general.qualities.Rare",
  "VisibleQualityLabel": true,
  "RenderSpecialSlot": true,
  "ItemEntityConfig": {
    "ParticleSystemId": "Drop_Rare"
  }
}
```

### Quality Visual Effects

Each quality provides:

| Property | Effect |
|----------|--------|
| `TextColor` | Colors item name and description text |
| `SlotTexture` | Custom inventory slot background |
| `ItemTooltipTexture` | Tooltip background appearance |
| `ParticleSystemId` | Particle effect when item is dropped |
| `LocalizationKey` | Quality label shown in tooltip |

### Using Qualities for RPG Rarity

We can map our `GearRarity` enum to Hytale qualities:

| GearRarity | Hytale Quality | Color |
|------------|----------------|-------|
| COMMON | Default | #c9d2dd (gray) |
| UNCOMMON | Uncommon | #4fa1d0 (light blue) |
| RARE | Rare | #2770b7 (blue) |
| EPIC | Epic | #9b59b6 (purple) |
| LEGENDARY | Legendary | #bb8a2c (gold) |
| MYTHIC | Mythic | #e74c3c (red) or custom |

**Problem**: Qualities are asset-level, not per-ItemStack. All Iron Swords would need the same quality unless we create variant items.

---

## Item Names and Descriptions

### Translation System

Item names are **localization keys**, not direct text:

From `Item.java`:
```java
@Nonnull
public String getTranslationKey() {
    if (this.translationProperties != null) {
        String nameTranslation = this.translationProperties.getName();
        if (nameTranslation != null) {
            return nameTranslation;
        }
    }
    return "server.items." + this.id + ".name";
}

@Nonnull
public String getDescriptionTranslationKey() {
    if (this.translationProperties != null) {
        String descTranslation = this.translationProperties.getDescription();
        if (descTranslation != null) {
            return descTranslation;
        }
    }
    return "server.items." + this.id + ".description";
}
```

### ItemTranslationProperties

```java
public class ItemTranslationProperties {
    @Nullable private String name;        // Translation key for name
    @Nullable private String description; // Translation key for description
}
```

### Localization File Example

Translations are resolved client-side from language files:

```yaml
# en_US.yml (hypothetical)
server.items.Weapon_Sword_Iron.name: "Iron Sword"
server.items.Weapon_Sword_Iron.description: "A sturdy iron blade."
server.general.qualities.Rare: "Rare"
server.general.qualities.Legendary: "Legendary"
```

### Limitation: No Per-Item Custom Names

**The translation key is fixed per item type.** There is no `ItemStack.withDisplayName("Custom Name")` method.

All "Weapon_Sword_Iron" items will always display as "Iron Sword" regardless of RPG stats.

---

## Text Formatting and Colors

### Color System

Hytale uses RGB colors with multiple input formats:

**Color Classes:**
```java
// Basic RGB (3 bytes)
public class Color {
    public byte red;
    public byte green;
    public byte blue;
}

// RGBA with transparency (4 bytes)
public class ColorAlpha {
    public byte alpha;
    public byte red;
    public byte green;
    public byte blue;
}
```

**Color Parsing Formats** (from `ColorParseUtil.java`):
- Hex: `#RGB`, `#RRGGBB`
- Hex with alpha: `#RGBA`, `#RRGGBBAA`
- RGB function: `rgb(255, 128, 0)`
- RGBA function: `rgba(255, 128, 0, 0.5)`
- RGBA hex hybrid: `rgba(#FF8000, 0.5)`

### FormattedMessage System

Hytale has a rich text system for messages:

```java
public class FormattedMessage {
    @Nullable public String rawText;           // Plain text content
    @Nullable public String messageId;         // i18n message ID
    @Nullable public FormattedMessage[] children;  // Nested messages
    @Nullable public Map<String, ParamValue> params;  // Parameters
    @Nullable public String color;             // Text color (hex)
    @Nonnull public MaybeBool bold;            // Bold styling
    @Nonnull public MaybeBool italic;          // Italic styling
    @Nonnull public MaybeBool monospace;       // Monospace font
    @Nonnull public MaybeBool underlined;      // Underline
    @Nullable public String link;              // Hyperlink URL
    public boolean markupEnabled;              // Enable markup
}
```

**MaybeBool Enum:**
- `Null` - Inherit from parent
- `False` - Explicitly disabled
- `True` - Explicitly enabled

### Message Builder API

Server-side message construction:

```java
// Plain text
Message msg = Message.raw("Hello World");

// Styled text
Message styled = Message.raw("Critical Hit!")
    .color("#FF0000")
    .bold(true);

// i18n with parameters
Message damage = Message.translation("combat.damage.dealt")
    .param("amount", 150)
    .param("type", "fire");

// Nested composition
Message complex = Message.raw("")
    .insert(Message.raw("Rare ").color("#2770b7").bold(true))
    .insert(Message.raw("Iron Sword"));
```

### Combat Text Styling

For damage numbers and combat feedback:

```java
public class CombatTextUIComponent {
    private float fontSize = 68.0f;              // Font size in pixels
    private Color textColor = white;             // Text color
    private float duration;                      // Display time (0.1-10.0s)
    private float hitAngleModifierStrength;      // Animation effect
    private RangeVector2f randomPositionOffsetRange;  // Position variance
    private CombatTextUIComponentAnimationEvent[] animationEvents;
}
```

---

## ItemStack Metadata System

### Metadata Storage

ItemStack supports arbitrary BSON metadata:

```java
public class ItemStack {
    protected String itemId;
    protected int quantity;
    protected double durability;
    protected double maxDurability;
    @Nullable protected BsonDocument metadata;  // Custom data storage
}
```

### Metadata Methods

```java
// Write metadata
public <T> ItemStack withMetadata(
    @Nonnull String key,
    @Nonnull Codec<T> codec,
    @Nullable T data
)

// Read metadata
public <T> T getFromMetadataOrNull(
    @Nonnull String key,
    @Nonnull Codec<T> codec
)

// Read with default
public <T> T getFromMetadataOrDefault(
    @Nonnull String key,
    @Nonnull BuilderCodec<T> codec
)
```

### Known Metadata Keys

Only one documented key exists:
```java
public static class Metadata {
    public static final String BLOCK_STATE = "BlockState";
}
```

### Network Transmission

Metadata is serialized to JSON for network:

```java
public class ItemWithAllMetadata {
    public String itemId;
    public int quantity;
    public double durability;
    public double maxDurability;
    public boolean overrideDroppedItemAnimation;
    public String metadata;  // JSON-encoded BsonDocument
}
```

### Metadata Limitation

**The client receives metadata but does NOT use it for display.**

Metadata is purely for server-side logic. There's no mechanism for metadata to affect:
- Tooltip content
- Item name
- Item description
- Text color

---

## Message API

### Sending Messages to Players

The Message API can send formatted text:

```java
// Get player and send message
player.sendMessage(Message.raw("Your sword has +15% damage!")
    .color("#FF6600")
    .bold(true));

// Action bar message (above hotbar)
player.sendActionBar(Message.translation("gear.equipped")
    .param("item", "Fiery Iron Sword")
    .param("level", 25));
```

### Parameter Formatting

Message parameters support formatting:

```
{name}                    - Simple substitution
{count, number, integer}  - Format as integer
{amount, number, decimal} - Format as decimal
{text, upper}             - Uppercase
{text, lower}             - Lowercase
{count, plural, one {1 item} other {# items}}  - Pluralization
```

### Potential Uses for RPG

We could use messages to show gear info:

```java
// On item pickup
Message pickup = Message.raw("")
    .insert(Message.raw("LEGENDARY ").color("#bb8a2c").bold(true))
    .insert(Message.raw("Iron Sword (Lv25)").color("#bb8a2c"))
    .insert(Message.raw("\n+15% Physical Damage").color("#00FF00"))
    .insert(Message.raw("\n+8 Strength").color("#AAAAFF"));

player.sendMessage(pickup);
```

---

## Implementation Options for RPG Tooltips

### Option 1: Custom Item Qualities (Recommended)

Create custom ItemQuality assets for each rarity:

**Pros:**
- Native quality colors and textures
- Drop particles per rarity
- Quality label in tooltip

**Cons:**
- Can't show stats in tooltip
- Quality is per-item-type, not per-stack
- Requires asset files in mod

**Implementation:**
1. Create quality JSONs for COMMON/UNCOMMON/RARE/EPIC/LEGENDARY/MYTHIC
2. Reference qualities in item definitions
3. Map GearRarity to quality index

### Option 2: Custom UI Page (Already Implemented)

Use `/gear info` command or inspect UI:

**Pros:**
- Full control over display
- Can show all stats, modifiers, requirements
- Already partially implemented

**Cons:**
- Requires extra user action
- Not visible at hover time

**Enhancement:**
- Add keybind for quick gear inspect
- Show mini-stats on hover via custom UI overlay

### Option 3: Chat Messages on Events

Send formatted messages on pickup/equip:

**Pros:**
- Full text formatting support
- Can show detailed stats

**Cons:**
- Chat can get spammy
- Message disappears from chat history

**Implementation:**
```java
// On item pickup
player.sendMessage(formatGearPickupMessage(gearData));

// On hover (if possible)
player.sendActionBar(formatGearSummary(gearData));
```

### Option 4: Hybrid Approach (Best)

Combine multiple methods:

1. **Quality System** - Rarity colors and particles
2. **Action Bar** - Brief summary on hover/equip
3. **UI Page** - Full details on demand
4. **Chat** - Detailed stats on pickup (configurable)

---

## Key Files Reference

### Core Item System

| File | Purpose |
|------|---------|
| `com/hypixel/hytale/server/core/inventory/ItemStack.java` | ItemStack with metadata |
| `com/hypixel/hytale/server/core/asset/type/item/config/Item.java` | Item asset definition |
| `com/hypixel/hytale/server/core/asset/type/item/config/ItemQuality.java` | Quality asset (server) |
| `com/hypixel/hytale/protocol/ItemQuality.java` | Quality protocol packet |
| `com/hypixel/hytale/protocol/ItemBase.java` | Item data packet |
| `com/hypixel/hytale/protocol/ItemWithAllMetadata.java` | Stack data packet |

### Text and Formatting

| File | Purpose |
|------|---------|
| `com/hypixel/hytale/protocol/FormattedMessage.java` | Rich text structure |
| `com/hypixel/hytale/server/core/Message.java` | Message builder API |
| `com/hypixel/hytale/protocol/Color.java` | RGB color type |
| `com/hypixel/hytale/server/core/asset/util/ColorParseUtil.java` | Color parsing utilities |

### Translation

| File | Purpose |
|------|---------|
| `com/hypixel/hytale/server/core/asset/type/item/config/ItemTranslationProperties.java` | Name/description keys |
| `com/hypixel/hytale/protocol/ItemTranslationProperties.java` | Translation packet |

### UI Components

| File | Purpose |
|------|---------|
| `com/hypixel/hytale/server/core/ui/ItemGridSlot.java` | Inventory slot UI |
| `com/hypixel/hytale/server/core/modules/entityui/asset/CombatTextUIComponent.java` | Damage text styling |

### Asset Locations

| Path | Contents |
|------|----------|
| `Assets/Server/Item/Qualities/` | Quality JSON definitions |
| `Assets/Common/UI/ItemQualities/Tooltips/` | Tooltip textures |
| `Assets/Common/UI/ItemQualities/Slots/` | Slot textures |
| `Assets/Server/Item/Items/` | Item definitions |

---

## Conclusions

### What We CAN Do

1. **Use quality system for rarity colors** - Create custom qualities for RPG rarities
2. **Store RPG data in metadata** - Already implemented, works server-side
3. **Send formatted chat/action bar messages** - Show stats on pickup/equip
4. **Build custom UI pages** - Full stat display on demand
5. **Use drop particles** - Quality-based particle effects on loot drops

### What We CANNOT Do (Without Client Mod)

1. **Custom tooltip lines** - Tooltips are asset-defined, not runtime
2. **Per-stack custom names** - Names are translation keys
3. **Inline stat display in tooltip** - No API for additional tooltip content
4. **Different quality per same item type** - Quality is asset-level

### Recommended Implementation

1. **Phase 1**: Map GearRarity to ItemQuality for colors/particles
2. **Phase 2**: Send action bar summary on equip ("Legendary Iron Sword Lv25")
3. **Phase 3**: Chat message with full stats on pickup (configurable)
4. **Phase 4**: Enhance `/gear info` UI page for detailed inspection
5. **Phase 5**: Consider keybind for quick gear inspect overlay

---

## Next Steps

1. Research how to register custom ItemQuality assets from a mod
2. Implement action bar messages for gear equip
3. Add chat notifications for gear pickup
4. Enhance GearCommands with better stat display
5. Explore UI overlay options for hover inspection
