# Hytale ItemQuality System Reference

This document describes how Hytale's ItemQuality system works and how to create custom qualities.

## Overview

ItemQuality (internally called "rarity" in vanilla) controls:
- Item name color in inventory/tooltip
- Tooltip background texture
- Inventory slot border texture
- Drop particle effects (glowing drops)
- Quality label visibility (e.g., "[LEGENDARY]")

## Vanilla Qualities

| Quality | QualityValue | TextColor | Purpose |
|---------|--------------|-----------|---------|
| Junk | 0 | #5d5d5d | Trash items |
| Common | 1 | #c9d2dd | Basic items (default) |
| Uncommon | 2 | #3e9049 | Slightly better items |
| Rare | 3 | #2770b7 | Good items |
| Epic | 4 | #8b339e | Great items |
| Legendary | 5 | #bb8a2c | Best vanilla items |
| Technical | 8 | (hidden) | Internal dev items |
| Tool | 9 | (hidden) | Tools in creative mode |
| Debug/Developer/Template | 10 | (hidden) | Development items |

**Note:** There is no vanilla "Mythic" or "Unique" quality - these are custom additions.

## Quality JSON Structure

Qualities are defined in `Server/Item/Qualities/{Name}.json`:

```json
{
  "QualityValue": 5,
  "ItemTooltipTexture": "UI/ItemQualities/Tooltips/ItemTooltipLegendary.png",
  "ItemTooltipArrowTexture": "UI/ItemQualities/Tooltips/ItemTooltipLegendaryArrow.png",
  "SlotTexture": "UI/ItemQualities/Slots/SlotLegendary.png",
  "BlockSlotTexture": "UI/ItemQualities/Slots/SlotLegendary.png",
  "SpecialSlotTexture": "UI/ItemQualities/Slots/SlotLegendary.png",
  "TextColor": "#bb8a2c",
  "LocalizationKey": "server.general.qualities.Legendary",
  "VisibleQualityLabel": true,
  "RenderSpecialSlot": true,
  "ItemEntityConfig": {
    "ParticleSystemId": "Drop_Legendary"
  }
}
```

### Property Reference

| Property | Type | Description |
|----------|------|-------------|
| `QualityValue` | int | Ordering value (higher = better). Affects sorting. |
| `TextColor` | hex | Item name color in inventory and tooltips |
| `LocalizationKey` | string | Translation key for quality name (e.g., "Legendary") |
| `VisibleQualityLabel` | bool | Show quality badge in tooltip (e.g., "[LEGENDARY]") |
| `ItemTooltipTexture` | path | Background texture for item tooltip |
| `ItemTooltipArrowTexture` | path | Arrow/pointer texture for tooltip |
| `SlotTexture` | path | Inventory slot background (normal items) |
| `BlockSlotTexture` | path | Slot background for block items |
| `SpecialSlotTexture` | path | Slot background for consumables/usables |
| `RenderSpecialSlot` | bool | Use SpecialSlotTexture for applicable items |
| `ItemEntityConfig` | object | Drop VFX configuration |
| `HideFromSearch` | bool | Hide from creative mode library search |

## Creating Custom Qualities

### 1. Create the Quality JSON

Place in your mod's `Server/Item/Qualities/` folder:

```json
// Server/Item/Qualities/Mythic.json
{
  "QualityValue": 6,
  "ItemTooltipTexture": "UI/ItemQualities/Tooltips/ItemTooltipMythic.png",
  "ItemTooltipArrowTexture": "UI/ItemQualities/Tooltips/ItemTooltipMythicArrow.png",
  "SlotTexture": "UI/ItemQualities/Slots/SlotMythic.png",
  "BlockSlotTexture": "UI/ItemQualities/Slots/SlotMythic.png",
  "SpecialSlotTexture": "UI/ItemQualities/Slots/SlotMythic.png",
  "TextColor": "#ff4500",
  "LocalizationKey": "server.general.qualities.Mythic",
  "VisibleQualityLabel": true,
  "RenderSpecialSlot": true,
  "ItemEntityConfig": {
    "ParticleSystemId": "Drop_Legendary",
    "ParticleColor": "#ff4500"
  }
}
```

### 2. Reusing Vanilla Textures

You can reuse vanilla textures by referencing them directly. All texture paths are relative to `Common/`:

- `UI/ItemQualities/Tooltips/ItemTooltip{Quality}.png`
- `UI/ItemQualities/Tooltips/ItemTooltip{Quality}Arrow.png`
- `UI/ItemQualities/Slots/Slot{Quality}.png`

### 3. ParticleColor Override

When reusing a vanilla particle system (like `Drop_Legendary`), you can override the particle color:

```json
"ItemEntityConfig": {
  "ParticleSystemId": "Drop_Legendary",
  "ParticleColor": "#ff4500"
}
```

## Drop VFX System (Advanced)

For fully custom drop effects, you need three components:

### 1. Particle System

`Server/Particles/{Name}.json` - Defines the particle emission (not typically customized).

### 2. Drop Effect

`Server/Entity/Effects/Drop/Drop_{Quality}.json`:

```json
{
  "ParticleSystemId": "Drop_Legendary",
  "ModelVFXId": "Drop_Legendary",
  "EntityBottomTint": "#bb8a2c"  // Ground glow color
}
```

### 3. Model VFX

`Server/Entity/ModelVFX/Drop_{Quality}.json`:

```json
{
  "HighlightColor": "#bb8a2c",
  "HighlightThickness": 2.0,
  "UseBloomOnHighlight": true
}
```

## TrailOfOrbis Custom Qualities

Our plugin adds two custom qualities:

### Mythic (QualityValue: 6)

- **Color**: #ff4500 (red-orange)
- **Purpose**: Highest tier of droppable gear
- **Textures**: Custom red-orange hue-shifted from Legendary (slot, tooltip, arrow)
- **Particles**: Legendary with red-orange color override

### Unique (QualityValue: 7)

- **Color**: #af6025 (PoE-style burnt orange)
- **Purpose**: Special quest items with unique modifiers
- **Textures**: Custom burnt-orange hue-shifted from Legendary (slot, tooltip, arrow)
- **Particles**: Legendary with burnt-orange color override
- **Note**: Cannot be upgraded to via upgrade stones

## Mapping GearRarity to Hytale Quality

Our `GearRarity` enum maps to Hytale qualities via `getHytaleQualityId()`:

| GearRarity | Hytale Quality ID |
|------------|-------------------|
| COMMON | "Common" |
| UNCOMMON | "Uncommon" |
| RARE | "Rare" |
| EPIC | "Epic" |
| LEGENDARY | "Legendary" |
| MYTHIC | "Mythic" (custom) |
| UNIQUE | "Unique" (custom) |

## Troubleshooting

### "Quality not found" / Falls back to Common

- Check that your quality JSON is in the correct path: `Server/Item/Qualities/{Name}.json`
- Verify the JSON is valid (no trailing commas, proper formatting)
- Quality name must match exactly (case-sensitive): "Mythic" not "mythic"

### Items show wrong color / no drop glow

- Verify `TextColor` is a valid hex color with `#` prefix
- Check `ItemEntityConfig.ParticleSystemId` references a valid particle system
- Ensure `VisibleQualityLabel` is set appropriately

### Custom textures not loading

- Textures must be in `Common/UI/ItemQualities/` directory
- Use relative paths from Common folder
- Texture files need @2x variants for high-DPI displays

## Quick Reference

```bash
# Vanilla quality files (for reference)
/home/larsonix/work/Hytale-Decompiled-Full-Game/Assets/Server/Item/Qualities/

# Our custom qualities
/mnt/c/.../mods/TrailOfOrbis_Realms/Server/Item/Qualities/Mythic.json
/mnt/c/.../mods/TrailOfOrbis_Realms/Server/Item/Qualities/Unique.json

# Quality schema (for field validation)
/home/larsonix/work/Hytale-Decompiled-Full-Game/Assets/Schema/ItemQuality.json
```
