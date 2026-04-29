# Tooltip System Research

Research into Hytale's item tooltip display, customization, and stat rendering systems.

---

## Executive Summary

**Question**: How do item tooltips work and can we customize them?

**Answer**: Hytale uses a **quality-based tooltip system** where:
- `ItemQuality` controls tooltip texture, text color, and slot appearance
- `ItemTranslationProperties` provides name and description via i18n keys
- `ItemGridSlot` allows UI-level name/description override
- `ItemGridInfoDisplayMode` controls tooltip placement (Tooltip/Adjacent/None)
- Stat modifiers from `ItemWeapon`/`ItemArmor` are automatically displayed
- Custom data can be stored in `ItemWithAllMetadata.metadata` for RPG extensions

**Customization Options**:
- ✅ Custom tooltip textures per quality tier
- ✅ Custom text colors per quality
- ✅ Override item name/description in UI
- ✅ Custom stat modifiers displayed automatically
- ⚠️ Custom tooltip layouts require client-side modifications
- ⚠️ No server API for arbitrary tooltip content injection

---

## 1. Core Tooltip Architecture

### 1.1 System Overview

```
┌─────────────────────────────────────────────────────────────┐
│                     Tooltip Display Flow                     │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ItemBase ──────► ItemQuality ──────► Tooltip Texture       │
│     │                  │                                     │
│     │                  └──► textColor ──► Name Color         │
│     │                                                        │
│     ├──► translationProperties ──► Name/Description Text    │
│     │                                                        │
│     ├──► weapon/armor ──► statModifiers ──► Stat Display    │
│     │                                                        │
│     └──► qualityIndex ──► Quality Label (if visible)        │
│                                                              │
│  ItemWithAllMetadata.metadata ──► Custom RPG Data           │
│                                                              │
│  ItemGridSlot ──► name/description override ──► UI Display  │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 Key Data Flow

1. **Server** loads `Item` asset with `qualityId`, `translationProperties`
2. **Server** sends `ItemBase` to client with all display properties
3. **Client** looks up `ItemQuality` by `qualityIndex`
4. **Client** uses quality's `itemTooltipTexture`, `textColor` for rendering
5. **Client** resolves translation keys to localized text
6. **Client** displays stat modifiers from `weapon`/`armor` data

---

## 2. ItemQuality - Tooltip Styling

### 2.1 Protocol Class

**Location**: `com/hypixel/hytale/protocol/ItemQuality.java`

```java
public class ItemQuality {
    @Nullable public String id;                    // Quality identifier
    @Nullable public String itemTooltipTexture;    // Tooltip background PNG
    @Nullable public String itemTooltipArrowTexture; // Tooltip arrow PNG
    @Nullable public String slotTexture;           // Inventory slot background
    @Nullable public String blockSlotTexture;      // Block item slot texture
    @Nullable public String specialSlotTexture;    // Consumable/utility slot
    @Nullable public Color textColor;              // Item name text color
    @Nullable public String localizationKey;       // Quality name i18n key
    public boolean visibleQualityLabel;            // Show quality in tooltip
    public boolean renderSpecialSlot;              // Special slot rendering
    public boolean hideFromSearch;                 // Hide from creative search
}
```

### 2.2 Quality JSON Configuration

**Location**: `Assets/Server/Item/Qualities/*.json`

Example: `Rare.json`
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

### 2.3 Default Quality Tooltips

| Quality | TextColor | Tooltip Texture | Arrow Texture |
|---------|-----------|-----------------|---------------|
| Junk | `#c9d2dd` | ItemTooltipJunk.png | ItemTooltipJunkArrow.png |
| Common | `#c9d2dd` | ItemTooltipCommon.png | ItemTooltipCommonArrow.png |
| Uncommon | `#3e9049` | ItemTooltipUncommon.png | ItemTooltipUncommonArrow.png |
| Rare | `#2770b7` | ItemTooltipRare.png | ItemTooltipRareArrow.png |
| Epic | `#8b339e` | ItemTooltipEpic.png | ItemTooltipEpicArrow.png |
| Legendary | `#bb8a2c` | ItemTooltipLegendary.png | ItemTooltipLegendaryArrow.png |
| Technical | - | ItemTooltipTechnical.png | ItemTooltipTechnicalArrow.png |
| Default | `#c9d2dd` | ItemTooltipDefault.png | ItemTooltipDefaultArrow.png |

### 2.4 Tooltip Texture Assets

**Location**: `Assets/Common/UI/ItemQualities/Tooltips/`

Files follow the pattern:
- `ItemTooltip{Quality}@2x.png` - Tooltip background (9-slice)
- `ItemTooltip{Quality}Arrow@2x.png` - Tooltip pointer arrow

---

## 3. Item Name and Description

### 3.1 ItemTranslationProperties

**Location**: `com/hypixel/hytale/protocol/ItemTranslationProperties.java`

```java
public class ItemTranslationProperties {
    @Nullable public String name;        // Translation key for name
    @Nullable public String description; // Translation key for description
}
```

**Default Keys** (from Item.java):
- Name: `server.items.{assetId}.name`
- Description: `server.items.{assetId}.description`

### 3.2 Translation Example

**Item Definition** (`Container_Bucket.json`):
```json
{
  "TranslationProperties": {
    "Name": "server.items.Container_Bucket.name",
    "Description": "server.items.Container_Bucket.description"
  },
  "Quality": "Common"
}
```

**Language File** (`en-US/server.lang`):
```
server.items.Container_Bucket.name=Bucket
server.items.Container_Bucket.description=A sturdy metal bucket.
```

---

## 4. ItemGridSlot - UI Display Override

### 4.1 Class Definition

**Location**: `com/hypixel/hytale/server/core/ui/ItemGridSlot.java`

```java
public class ItemGridSlot {
    private ItemStack itemStack;                   // The item to display
    private Value<PatchStyle> background;          // Background styling
    private Value<PatchStyle> overlay;             // Overlay styling
    private Value<PatchStyle> icon;                // Icon styling
    private boolean isItemIncompatible;            // Incompatibility flag
    private String name;                           // Custom display name
    private String description;                    // Custom description
    private boolean skipItemQualityBackground;     // Skip quality background
    private boolean isActivatable;                 // Activatable item flag
    private boolean isItemUncraftable;             // Uncraftable flag
}
```

### 4.2 Custom Name/Description

`ItemGridSlot` allows overriding the default item name and description in UI:

```java
ItemGridSlot slot = new ItemGridSlot(itemStack)
    .setName("server.rpg.item.custom_sword.name")  // Custom translation key
    .setDescription("server.rpg.custom_description") // Custom description
    .setSkipItemQualityBackground(false);
```

**Key Points**:
- `name` and `description` can be translation keys or literal strings
- Overrides `ItemTranslationProperties` from the base item
- Useful for showing RPG stats in description

---

## 5. Display Mode Control

### 5.1 ItemGridInfoDisplayMode

**Location**: `com/hypixel/hytale/protocol/ItemGridInfoDisplayMode.java`

```java
public enum ItemGridInfoDisplayMode {
    Tooltip(0),   // Standard hover tooltip
    Adjacent(1),  // Display info adjacent to item
    None(2);      // No info display
}
```

### 5.2 Usage

Controls how item information appears when hovering:
- **Tooltip**: Traditional popup tooltip near cursor
- **Adjacent**: Info panel beside the item slot
- **None**: No hover information

---

## 6. Stat Modifier Display

### 6.1 Modifier Structure

**Location**: `com/hypixel/hytale/protocol/Modifier.java`

```java
public class Modifier {
    @Nonnull public ModifierTarget target = ModifierTarget.Min;
    @Nonnull public CalculationType calculationType = CalculationType.Additive;
    public float amount;
}
```

### 6.2 CalculationType

```java
public enum CalculationType {
    Additive(0),      // +X flat bonus
    Multiplicative(1) // +X% percentage bonus
}
```

### 6.3 Weapon Stats (ItemWeapon)

**Location**: `com/hypixel/hytale/protocol/ItemWeapon.java`

```java
public class ItemWeapon {
    @Nullable public int[] entityStatsToClear;
    @Nullable public Map<Integer, Modifier[]> statModifiers;
    public boolean renderDualWielded;
}
```

**Stat Modifiers Map**:
- Key: `Integer` (EntityStat index)
- Value: `Modifier[]` (array of modifiers for that stat)

### 6.4 Armor Stats (ItemArmor)

**Location**: `com/hypixel/hytale/protocol/ItemArmor.java`

```java
public class ItemArmor {
    @Nonnull public ItemArmorSlot armorSlot;
    @Nullable public Cosmetic[] cosmeticsToHide;
    @Nullable public Map<Integer, Modifier[]> statModifiers;
    public double baseDamageResistance;
    @Nullable public Map<String, Modifier[]> damageResistance;
    @Nullable public Map<String, Modifier[]> damageEnhancement;
    @Nullable public Map<String, Modifier[]> damageClassEnhancement;
}
```

**Key Fields**:
| Field | Type | Purpose |
|-------|------|---------|
| `statModifiers` | `Map<Integer, Modifier[]>` | EntityStat bonuses |
| `baseDamageResistance` | `double` | Base armor value |
| `damageResistance` | `Map<String, Modifier[]>` | Per-damage-type resistance |
| `damageEnhancement` | `Map<String, Modifier[]>` | Outgoing damage bonus |

---

## 7. Item Metadata for Custom RPG Stats

### 7.1 ItemWithAllMetadata

**Location**: `com/hypixel/hytale/protocol/ItemWithAllMetadata.java`

```java
public class ItemWithAllMetadata {
    @Nonnull public String itemId = "";
    public int quantity;
    public double durability;
    public double maxDurability;
    public boolean overrideDroppedItemAnimation;
    @Nullable public String metadata;  // JSON string for custom data
}
```

### 7.2 Storing RPG Stats in Metadata

The `metadata` field is a JSON string that can store arbitrary data:

```java
// Server-side: Store RPG stats in metadata
BsonDocument metadata = new BsonDocument();
metadata.append("rpg_attack", new BsonInt32(150));
metadata.append("rpg_crit_chance", new BsonDouble(0.15));
metadata.append("rpg_rarity", new BsonString("Legendary"));
metadata.append("rpg_enchantments", new BsonArray(...));

ItemStack rpgItem = baseItem.withMetadata(metadata);
```

### 7.3 Custom Tooltip Content via Metadata

**Approach for RPG Stats**:

1. Store RPG stats in `ItemStack.metadata`
2. Read metadata when building UI display
3. Override `ItemGridSlot.description` with formatted stats
4. Use `Message` API for colored stat text

```java
// Building tooltip description from metadata
String description = buildRPGDescription(itemStack.getMetadata());

ItemGridSlot slot = new ItemGridSlot(itemStack)
    .setDescription(description);
```

---

## 8. Window System and UI Packets

### 8.1 OpenWindow Packet

**Location**: `com/hypixel/hytale/protocol/packets/window/OpenWindow.java`

```java
public class OpenWindow implements Packet {
    public static final int PACKET_ID = 200;

    public int id;
    @Nonnull public WindowType windowType;
    @Nullable public String windowData;       // JSON configuration
    @Nullable public InventorySection inventory;
    @Nullable public ExtraResources extraResources;
}
```

### 8.2 WindowType Enum

```java
public enum WindowType {
    Container(0),
    PocketCrafting(1),
    BasicCrafting(2),
    DiagramCrafting(3),
    StructuralCrafting(4),
    Processing(5),
    Memories(6)
}
```

### 8.3 Inventory Update

**Location**: `com/hypixel/hytale/protocol/packets/inventory/UpdatePlayerInventory.java`

- **Packet ID**: 170
- Sends all inventory sections with `ItemWithAllMetadata` for each slot
- Client updates tooltip display based on received data

---

## 9. Customization Capabilities

### 9.1 What CAN Be Customized

| Feature | Method | Server-Side? |
|---------|--------|--------------|
| Tooltip frame texture | Custom ItemQuality JSON | ✅ Yes |
| Item name text color | ItemQuality.textColor | ✅ Yes |
| Slot background texture | ItemQuality.slotTexture | ✅ Yes |
| Item name text | ItemTranslationProperties | ✅ Yes |
| Item description | ItemTranslationProperties | ✅ Yes |
| UI name override | ItemGridSlot.name | ✅ Yes |
| UI description override | ItemGridSlot.description | ✅ Yes |
| Stat modifiers display | ItemWeapon/ItemArmor.statModifiers | ✅ Yes |
| Custom RPG data | ItemStack.metadata | ✅ Yes |

### 9.2 What CANNOT Be Easily Customized

| Feature | Limitation |
|---------|------------|
| Arbitrary tooltip layout | Client renders fixed layout |
| Additional tooltip sections | No server API for custom sections |
| Dynamic tooltip formatting | Limited to description override |
| Tooltip positioning | Client-controlled |

### 9.3 Workarounds for Limitations

**Custom Stat Display in Description**:
```java
// Format RPG stats as description text
String desc = String.format(
    "Attack: +%d\\nCrit Chance: %.0f%%\\nLevel Required: %d",
    attackBonus, critChance * 100, levelReq
);
ItemGridSlot slot = new ItemGridSlot(item).setDescription(desc);
```

**Using Translation Keys for Dynamic Content**:
```java
// Translation key with parameters
// server.lang: "server.rpg.sword.desc={damage} Attack | {crit}% Crit"
slot.setDescription("server.rpg.sword.desc");
// Parameters resolved by client translation system
```

---

## 10. RPG Integration Strategies

### 10.1 Strategy 1: Quality-Based Rarities

Map RPG rarities to Hytale qualities:

| RPG Rarity | Hytale Quality | Color |
|------------|----------------|-------|
| Common | Common | `#c9d2dd` |
| Magic | Uncommon | `#3e9049` |
| Rare | Rare | `#2770b7` |
| Epic | Epic | `#8b339e` |
| Legendary | Legendary | `#bb8a2c` |

**Pros**: Native support, automatic tooltip styling
**Cons**: Limited to 6 tiers

### 10.2 Strategy 2: Custom Qualities

Create new quality JSON files for RPG tiers:

```json
// Assets/Server/Item/Qualities/Mythic.json
{
  "QualityValue": 10,
  "TextColor": "#ff4500",
  "LocalizationKey": "server.rpg.qualities.Mythic",
  "ItemTooltipTexture": "UI/ItemQualities/Tooltips/ItemTooltipMythic.png",
  "VisibleQualityLabel": true
}
```

**Pros**: Full customization of colors and textures
**Cons**: Requires custom tooltip texture assets

### 10.3 Strategy 3: Metadata + Description Override

Store all RPG stats in metadata, format as description:

```java
public String buildTooltipDescription(ItemStack item) {
    BsonDocument meta = item.getMetadata();
    if (meta == null) return null;

    StringBuilder sb = new StringBuilder();

    // Damage range
    int minDmg = meta.getInt32("rpg_min_damage", 0);
    int maxDmg = meta.getInt32("rpg_max_damage", 0);
    sb.append(String.format("Damage: %d-%d\\n", minDmg, maxDmg));

    // Stats
    if (meta.containsKey("rpg_strength")) {
        sb.append(String.format("+%d Strength\\n", meta.getInt32("rpg_strength")));
    }

    // Level requirement
    int reqLevel = meta.getInt32("rpg_required_level", 0);
    sb.append(String.format("\\nRequires Level %d", reqLevel));

    return sb.toString();
}
```

**Pros**: Maximum flexibility, all data server-controlled
**Cons**: Plain text only, no rich formatting in tooltips

---

## 11. Key Findings Summary

| Question | Answer |
|----------|--------|
| How do tooltips work? | Quality-based textures + translation keys + stat modifiers |
| Can we customize tooltip content? | ✅ Yes, via ItemGridSlot.name/description override |
| Can we change tooltip border/frame? | ✅ Yes, via custom ItemQuality with custom textures |
| Is there a tooltip API? | ⚠️ Limited - ItemGridSlot for name/desc, ItemQuality for styling |
| How is tooltip layout structured? | Fixed client-side layout, server sends data |
| Can we show RPG stats? | ✅ Yes, via statModifiers (native) or description override (custom) |

---

## 12. Implementation Recommendations

### 12.1 For RPG Item Stats

1. **Use native statModifiers** for stats that map to EntityStats
2. **Store RPG-specific stats** in `ItemStack.metadata`
3. **Build description strings** that format RPG stats for tooltip
4. **Use ItemQuality** for rarity-based tooltip styling

### 12.2 For Custom Tooltips

1. **Create custom quality definitions** for RPG rarity tiers
2. **Design tooltip textures** matching RPG aesthetic
3. **Format description text** to show all relevant stats
4. **Consider translation keys** for localization support

### 12.3 Example Implementation

```java
// RPG item with custom tooltip
public ItemGridSlot createRPGItemSlot(ItemStack item) {
    BsonDocument meta = item.getMetadata();

    // Build custom name with rarity prefix
    String rarity = meta.getString("rpg_rarity", "Common");
    String baseName = item.getItem().getTranslationKey();

    // Build description with all stats
    String desc = formatRPGStats(meta);

    return new ItemGridSlot(item)
        .setName(baseName)  // Could add rarity prefix
        .setDescription(desc)
        .setSkipItemQualityBackground(false);
}

private String formatRPGStats(BsonDocument meta) {
    List<String> lines = new ArrayList<>();

    // Weapon damage
    if (meta.containsKey("rpg_min_damage")) {
        lines.add(String.format("Damage: %d-%d",
            meta.getInt32("rpg_min_damage"),
            meta.getInt32("rpg_max_damage")));
    }

    // Stat bonuses
    for (String stat : Arrays.asList("Strength", "Vitality", "Agility")) {
        String key = "rpg_" + stat.toLowerCase();
        if (meta.containsKey(key)) {
            int val = meta.getInt32(key);
            lines.add(String.format("+%d %s", val, stat));
        }
    }

    // Level requirement
    if (meta.containsKey("rpg_required_level")) {
        lines.add("");
        lines.add("Requires Level " + meta.getInt32("rpg_required_level"));
    }

    return String.join("\\n", lines);
}
```

---

## 13. File References

| File | Purpose |
|------|---------|
| `ItemQuality.java` (protocol) | Tooltip texture/color configuration |
| `ItemQuality.java` (server) | Server-side quality definitions |
| `ItemTranslationProperties.java` | Name/description i18n keys |
| `ItemGridSlot.java` | UI slot with override capabilities |
| `ItemGridInfoDisplayMode.java` | Tooltip display mode enum |
| `ItemBase.java` | Complete item data including quality |
| `ItemWithAllMetadata.java` | Item packet with metadata |
| `ItemWeapon.java` / `ItemArmor.java` | Stat modifiers |
| `Modifier.java` | Stat modifier structure |
| `OpenWindow.java` | Window/UI packets |
| `Assets/Server/Item/Qualities/*.json` | Quality definitions |
| `Assets/Common/UI/ItemQualities/Tooltips/*` | Tooltip textures |

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-01-23 | Initial research complete |
