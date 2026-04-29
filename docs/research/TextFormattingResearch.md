# Text Formatting System Research

Research into Hytale's text formatting, color, and styling systems for the RPG Gear System.

---

## Executive Summary

**Question**: Does Hytale support colored/styled text?

**Answer**: **YES** - Hytale has a comprehensive rich text formatting system via `FormattedMessage` and `Message` classes supporting:
- RGB/RGBA hex color codes (#RGB, #RRGGBB, #RGBA, #RRGGBBAA)
- CSS-style color functions: `rgb(r,g,b)`, `rgba(r,g,b,a)`
- Text styles: **bold**, *italic*, `monospace`, <u>underlined</u>
- Nested formatted messages with children
- Parameter substitution with `{key}` syntax
- Hyperlinks
- Quality-based automatic text coloring for items

---

## 1. Core Text Formatting Classes

### 1.1 FormattedMessage (Protocol Layer)

**Location**: `com/hypixel/hytale/protocol/FormattedMessage.java`

The core data structure for rich text, sent over the network to clients.

```java
public class FormattedMessage {
    @Nullable public String rawText;           // Plain text content
    @Nullable public String messageId;         // i18n translation key
    @Nullable public FormattedMessage[] children; // Nested messages
    @Nullable public Map<String, ParamValue> params;  // {key} parameters
    @Nullable public Map<String, FormattedMessage> messageParams; // Nested message params
    @Nullable public String color;             // Hex color string (e.g., "#FF0000")
    @Nonnull public MaybeBool bold = MaybeBool.Null;
    @Nonnull public MaybeBool italic = MaybeBool.Null;
    @Nonnull public MaybeBool monospace = MaybeBool.Null;
    @Nonnull public MaybeBool underlined = MaybeBool.Null;
    @Nullable public String link;              // Hyperlink URL
    public boolean markupEnabled;
}
```

**Key Points**:
- `color` is a **String** field containing hex color codes
- Styles use `MaybeBool` enum (Null/False/True) for tri-state inheritance
- Children allow nested formatted segments with different styles
- Parameters support dynamic text substitution

### 1.2 Message (Server-Side Wrapper)

**Location**: `com/hypixel/hytale/server/core/Message.java`

High-level fluent API for building formatted messages.

```java
// Create styled messages
Message.raw("Hello World")
    .color("#FF0000")    // Red text
    .bold(true)          // Bold
    .italic(true);       // Italic

// Use translations with parameters
Message.translation("server.items.sword.name")
    .param("damage", 50)
    .color("#FFD700");

// Nested messages with different colors
Message.raw("")
    .insert(Message.raw("Common ").color("#c9d2dd"))
    .insert(Message.raw("Rare ").color("#2770b7"))
    .insert(Message.raw("Legendary").color("#bb8a2c"));
```

**Key Methods**:
| Method | Description |
|--------|-------------|
| `Message.raw(String)` | Create message with literal text |
| `Message.translation(String)` | Create message with i18n key |
| `.color(String hex)` | Set text color (hex string) |
| `.color(Color)` | Set text color (Color object) |
| `.bold(boolean)` | Enable/disable bold |
| `.italic(boolean)` | Enable/disable italic |
| `.monospace(boolean)` | Enable/disable monospace font |
| `.param(String, Object)` | Add parameter substitution |
| `.insert(Message)` | Append child message |
| `.link(String)` | Add hyperlink |

### 1.3 MaybeBool Enum

**Location**: `com/hypixel/hytale/protocol/MaybeBool.java`

Tri-state boolean for style inheritance:

```java
public enum MaybeBool {
    Null(0),  // Inherit from parent
    False(1), // Explicitly disabled
    True(2);  // Explicitly enabled
}
```

---

## 2. Color System

### 2.1 Color Classes

| Class | Fields | Size | Usage |
|-------|--------|------|-------|
| `Color` | red, green, blue (bytes) | 3 bytes | General RGB colors |
| `ColorAlpha` | alpha, red, green, blue (bytes) | 4 bytes | RGBA with transparency |
| `ColorLight` | radius, red, green, blue (bytes) | 4 bytes | Light emission colors |

### 2.2 Supported Color Formats

**Location**: `com/hypixel/hytale/server/core/asset/util/ColorParseUtil.java`

| Format | Pattern | Example |
|--------|---------|---------|
| Hex RGB (3-digit) | `#RGB` | `#F00` (red) |
| Hex RGB (6-digit) | `#RRGGBB` | `#FF0000` (red) |
| Hex RGBA (4-digit) | `#RGBA` | `#F00F` (red, full alpha) |
| Hex RGBA (8-digit) | `#RRGGBBAA` | `#FF0000FF` (red, full alpha) |
| CSS rgb() | `rgb(R,G,B)` | `rgb(255,0,0)` |
| CSS rgba() | `rgba(R,G,B,A)` | `rgba(255,0,0,1.0)` |
| Hybrid | `rgba(#RGB,A)` | `rgba(#F00,0.5)` |

**ColorParseUtil Key Methods**:
```java
// Parsing
Color parseColor(String stringValue);
ColorAlpha parseColorAlpha(String stringValue);
int hexStringToRGBInt(String color);

// Conversion
String colorToHexString(Color color);     // Returns "#RRGGBB"
String colorToHexAlphaString(ColorAlpha); // Returns "#RRGGBBAA"
String colorToHex(java.awt.Color color);  // From AWT Color
```

---

## 3. Item Quality Colors

### 3.1 Quality Definition Structure

**Location**: `Assets/Server/Item/Qualities/*.json`

Each quality defines visual styling including text color:

```json
{
  "QualityValue": 3,
  "TextColor": "#2770b7",
  "LocalizationKey": "server.general.qualities.Rare",
  "ItemTooltipTexture": "UI/ItemQualities/Tooltips/ItemTooltipRare.png",
  "ItemTooltipArrowTexture": "UI/ItemQualities/Tooltips/ItemTooltipRareArrow.png",
  "SlotTexture": "UI/ItemQualities/Slots/SlotRare.png",
  "VisibleQualityLabel": true,
  "ItemEntityConfig": {
    "ParticleSystemId": "Drop_Rare"
  }
}
```

### 3.2 Default Quality Colors

| Quality | Value | TextColor | Visual |
|---------|-------|-----------|--------|
| Junk | 0 | `#c9d2dd` | Gray |
| Common | 1 | `#c9d2dd` | Gray |
| Uncommon | 2 | `#3e9049` | Green |
| Rare | 3 | `#2770b7` | Blue |
| Epic | 4 | `#8b339e` | Purple |
| Legendary | 5 | `#bb8a2c` | Gold/Orange |
| Default | -1 | `#c9d2dd` | Gray |

### 3.3 ItemQuality Class

**Location**: `com/hypixel/hytale/server/core/asset/type/item/config/ItemQuality.java`

```java
public class ItemQuality {
    protected String id;
    protected int qualityValue;
    protected Color textColor;              // Text color for item names
    protected String localizationKey;       // Quality name translation
    protected boolean visibleQualityLabel;  // Show quality in tooltip
    protected String itemTooltipTexture;    // Quality-specific tooltip frame
    protected String slotTexture;           // Inventory slot background
    protected ItemEntityConfig itemEntityConfig; // Drop particle effects
    // ...
}
```

---

## 4. Sending Messages to Players

### 4.1 PlayerRef.sendMessage()

**Location**: `com/hypixel/hytale/server/core/universe/PlayerRef.java:275`

```java
@Override
public void sendMessage(@Nonnull Message message) {
    this.packetHandler.writeNoCache(new ServerMessage(ChatType.Chat, message.getFormattedMessage()));
}
```

### 4.2 ServerMessage Packet

**Location**: `com/hypixel/hytale/protocol/packets/interface_/ServerMessage.java`

- **Packet ID**: 210
- **Fields**: `ChatType type`, `FormattedMessage message`
- Sends formatted text to the client's chat

### 4.3 Usage Example

```java
// Send colored message to player
PlayerRef player = ...;
player.sendMessage(
    Message.raw("You found a ")
        .insert(Message.raw("Legendary Sword").color("#bb8a2c").bold(true))
        .insert(Message.raw("!"))
);

// With translation and parameters
player.sendMessage(
    Message.translation("rpg.item.found")
        .param("item", Message.raw("Fire Staff").color("#8b339e"))
        .param("damage", 150)
);
```

---

## 5. Combat Text System

### 5.1 CombatTextUIComponent

**Location**: `com/hypixel/hytale/server/core/modules/entityui/asset/CombatTextUIComponent.java`

Configurable floating damage/healing numbers:

```java
public class CombatTextUIComponent extends EntityUIComponent {
    private float fontSize = 68.0f;
    private Color textColor = DEFAULT_TEXT_COLOR;  // RGB color
    private float duration;
    private RangeVector2f randomPositionOffsetRange;
    private float viewportMargin;
    private CombatTextUIComponentAnimationEvent[] animationEvents;
}
```

**Features**:
- Configurable font size and color
- Random position offset for visual variety
- Animation events for scale, position, opacity
- Hit angle modifier for directional offset

---

## 6. Text Parameter Formatting

### 6.1 Parameter Substitution

**Location**: `com/hypixel/hytale/server/core/util/MessageUtil.java`

Messages support `{key}` parameter substitution with formatting options:

```
{playerName}                    // Simple substitution
{damage, number, integer}       // Integer formatting
{amount, number, decimal}       // Decimal formatting
{count, plural, one {item} other {items}}  // Pluralization
{name, upper}                   // Uppercase
{name, lower}                   // Lowercase
```

### 6.2 ParamValue Types

| Type | Class | Description |
|------|-------|-------------|
| String | `StringParamValue` | Text values |
| Boolean | `BoolParamValue` | true/false |
| Double | `DoubleParamValue` | Floating point |
| Integer | `IntParamValue` | Whole numbers |
| Long | `LongParamValue` | Large integers |

---

## 7. Item Names and Display

### 7.1 Item Translation Properties

**Location**: `com/hypixel/hytale/server/core/asset/type/item/config/ItemTranslationProperties.java`

```java
public class ItemTranslationProperties {
    @Nullable public String name;        // Custom name translation key
    @Nullable public String description; // Custom description key
}
```

Default keys if not specified:
- Name: `server.items.{itemId}.name`
- Description: `server.items.{itemId}.description`

### 7.2 Item Display Flow

1. **Server** loads Item with `translationProperties` and `qualityId`
2. **ItemQuality** provides `textColor` for the quality tier
3. **Translation** resolves `messageId` to localized text
4. **FormattedMessage** wraps text with quality color
5. **Client** renders colored item name in UI/tooltips

### 7.3 Custom Item Names via Metadata

Items can potentially have custom display names via `ItemStack.metadata` (BsonDocument):
- Store custom name/color in metadata
- Read metadata when displaying item
- Override default translation with custom FormattedMessage

---

## 8. RPG System Integration

### 8.1 Rarity-Based Item Names

Use quality colors for RPG rarity tiers:

```java
// RPGItem with colored name based on rarity
public Message getColoredName(ItemStack item, Rarity rarity) {
    String color = switch (rarity) {
        case COMMON -> "#c9d2dd";
        case UNCOMMON -> "#3e9049";
        case RARE -> "#2770b7";
        case EPIC -> "#8b339e";
        case LEGENDARY -> "#bb8a2c";
    };
    return Message.translation(item.getItem().getTranslationKey())
        .color(color);
}
```

### 8.2 Stat Display with Colors

```java
// Show item stats with color coding
Message statLine = Message.raw("")
    .insert(Message.raw("+50 ").color("#3e9049"))  // Green for positive
    .insert(Message.raw("Attack Power"));

Message negativeStat = Message.raw("")
    .insert(Message.raw("-10 ").color("#FF4444"))  // Red for negative
    .insert(Message.raw("Speed"));
```

### 8.3 RPG Loot Announcements

```java
// Announce rare loot drop
player.sendMessage(
    Message.raw("")
        .insert(Message.raw("[LOOT] ").color("#FFD700").bold(true))
        .insert(Message.raw("You found: "))
        .insert(Message.raw("Blade of the Phoenix").color("#bb8a2c").bold(true))
        .insert(Message.raw(" (+"))
        .insert(Message.raw("125").color("#3e9049"))
        .insert(Message.raw(" Attack)"))
);
```

### 8.4 Level Requirements

```java
// Show level requirement with color
int playerLevel = 15;
int requiredLevel = 20;

String levelColor = playerLevel >= requiredLevel ? "#3e9049" : "#FF4444";
Message requirement = Message.raw("Required Level: ")
    .insert(Message.raw(String.valueOf(requiredLevel)).color(levelColor));
```

---

## 9. Key Findings Summary

| Question | Answer |
|----------|--------|
| Does Hytale support colored text? | **YES** - Full RGB/RGBA hex color support |
| Are there color codes like Minecraft? | **NO** - Uses hex strings, not `§` codes |
| Can item names have custom colors? | **YES** - Via ItemQuality.textColor or custom Message |
| How is text rendered in UI? | FormattedMessage sent via ServerMessage packet |
| Can we create custom rarity colors? | **YES** - Define custom ItemQuality or use Message.color() |
| Is there rich text formatting? | **YES** - Bold, italic, monospace, underline, links |
| Can we use nested colors? | **YES** - Via FormattedMessage.children array |

---

## 10. Implementation Recommendations

### 10.1 For RPG Item Names

1. **Use existing ItemQuality system** - Map RPG rarities to Hytale qualities
2. **Create custom qualities** - Define new quality JSON files for RPG tiers
3. **Override display via metadata** - Store formatted name in item metadata

### 10.2 For Chat/UI Messages

1. **Use Message.raw()** for simple colored text
2. **Use Message.translation()** for i18n support
3. **Chain .color().bold()** for styled text
4. **Use .insert()** for multi-colored segments

### 10.3 For Combat Feedback

1. **CombatTextUIComponent** - Configure damage number colors
2. **Custom damage colors** by type (fire=orange, ice=blue, etc.)
3. **Critical hits** - Different color/size for crits

---

## 11. File References

| File | Purpose |
|------|---------|
| `FormattedMessage.java` | Core rich text protocol class |
| `Message.java` | Server-side message builder |
| `ColorParseUtil.java` | Color parsing utilities |
| `Color.java`, `ColorAlpha.java` | Color data structures |
| `ItemQuality.java` | Quality-based item styling |
| `CombatTextUIComponent.java` | Floating combat text |
| `MessageUtil.java` | Text formatting utilities |
| `ServerMessage.java` | Chat message packet |
| `PlayerRef.java` | Player message sending |
| `Assets/Server/Item/Qualities/*.json` | Quality definitions |

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-01-23 | Initial research complete |
