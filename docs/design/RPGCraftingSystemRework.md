# RPG Crafting System Rework

## Status: Design Final (April 2026)

## Problem Statement

When players craft weapons and armor, the mod converts them to RPG gear. But:

1. **Level is wrong**: Uses player's RPG level, not material tier
2. **No preview**: Vanilla tooltips show nothing about RPG stats before crafting
3. **Post-craft flash**: Vanilla item briefly visible before RPG replacement

## Core Principle: One Formula, Two Uses

The mob scaling system converts distance from spawn to level:

```
level = 1 + (distance - scalingStart) / blocksPerLevel
```

Crafting uses the SAME formula. Each material has a distance range (where the ore is found). The formula converts those distances to a level range. Change mob scaling → crafting levels automatically stay in sync.

## Level Calculation

```yaml
# vanilla-conversion.yml
material_distances:
  wood:       { min: 0,    max: 400  }
  copper:     { min: 200,  max: 700  }
  iron:       { min: 700,  max: 1500 }
  steel:      { min: 1000, max: 2000 }
  cobalt:     { min: 2000, max: 3000 }
  mithril:    { min: 2500, max: 4000 }
  adamantite: { min: 3500, max: 5000 }
  # ... (full list in config)

default_distance: { min: 500, max: 1500 }

# Optional: shift crafted gear relative to zone mobs.
# 1.0 = matches zone. 0.9 = slightly weaker than drops.
crafting_level_multiplier: 1.0
```

```java
// At craft time:
MaterialDistance dist = config.getMaterialDistance(material);
int minLevel = distanceCalculator.estimateLevelFromDistance(dist.min());
int maxLevel = distanceCalculator.estimateLevelFromDistance(dist.max());
int gearLevel = ThreadLocalRandom.current().nextInt(minLevel, maxLevel + 1);
gearLevel = Math.max(1, (int)(gearLevel * config.getCraftingLevelMultiplier()));
```

## Tooltip Preview — Implemented (April 2026)

Vanilla weapon/armor tooltips show RPG crafting info: level range, max rarity, modifier count. Vanilla lore and misleading stats (Physical Resistance, etc.) are replaced with a clean, color-coded preview using Hytale's native markup (`<color>`, `<b>`, `<i>`).

### What Players See

Hovering over any vanilla weapon or armor — in crafting bench, inventory, chests, ground:

- **Header**: "Crafts as RPG Gear" (gold, bold)
- **Stats**: Level range, Rarity (colored in rarity color), Mods count, Quality (gray labels, white values)
- **Footer**: Italic gray hint about randomization
- **Vanilla stats stripped**: No more misleading "Physical Resistance: +6%" etc.

### Architecture: Coordinator Integration

**Critical constraint**: RPG items set `variant = true` on their ItemBase. Variant items inherit the base item's description from the client. Changing the base item's `translationProperties.description` causes ALL RPG variants to show the crafting text.

**Solution**: CraftingPreviewService is integrated into `ItemSyncCoordinator.onPlayerReady()`:

1. **Startup**: `CraftingPreviewService.initialize()` builds cached previews:
   - Filter items via `ItemClassifier.isConvertible()` + `isAllowedByConfig()` (same as conversion pipeline)
   - Compute level range from `MaterialTierMapper.getDistanceRange()` + `DistanceBonusCalculator`
   - Get modifier count from `GearBalanceConfig.rarityConfig(maxRarity).maxModifiers()`
   - Clone vanilla ItemBase, change description key to `rpg.crafting.{itemId}.description`, strip vanilla stats
   - Cache definitions + translations

2. **Player join** (via ItemSyncCoordinator's 200ms join flush):
   - Crafting preview sent FIRST → vanilla items get preview text + stripped stats
   - RPG item flush runs IMMEDIATELY after → RPG items override with their own definitions/translations
   - Both in ONE atomic scheduled task — no race conditions

3. **Config reload**: Recompute + resync to all online players

### Confirmed Dead Approaches

- **Standalone timer in PlayerJoinListener**: Races with ItemSyncCoordinator regardless of delay
- **UpdateTranslations-only (no UpdateItems)**: `server.items.*` prefix can't be overridden at runtime
- **DynamicTooltipsLib**: Standalone mod — rejected (players must install extra software)
- **Append to vanilla description**: Vanilla lore is noise, vanilla stats are misleading — replace entirely

### Edge Cases

- **Stackable weapons (arrows, bombs)**: Skip — `MaxStack > 1`
- **Modded items**: Skip — `ItemClassifier.isAllowedByConfig()` respects whitelist/blacklist
- **Unknown materials**: Use `default_distance` range
- **RPG variant conflict**: Solved by coordinator ordering (crafting preview before RPG flush)

## Conversion Pipeline

Existing flow stays. Changes in bold:

1. `CraftRecipeEvent.Post` fires (immediate crafts) / `InventoryChangeEvent` (queued crafts)
2. System finds vanilla weapon/armor in inventory
3. **Computes level from material distance range + shared formula** (was: player level)
4. Rolls rarity (capped by material), quality, modifiers
5. Creates RPG gear, replaces in inventory
6. **Deferred 1 tick via `world.execute()`** (was: immediate, causing visible flash)

## Files to Modify

| File | Change |
|------|--------|
| `vanilla-conversion.yml` | Add `material_distances`, `crafting_level_multiplier` |
| `VanillaConversionConfig.java` | Parse `material_distances` map + multiplier |
| `CraftingConversionSystem.java` | Use distance-based level range instead of player level |
| `MaterialTierMapper.java` | Add `getDistanceRange(itemId)` method |

## New Files

| File | Purpose |
|------|---------|
| `gear/tooltip/CraftingPreviewService.java` | Builds RPG descriptions for vanilla weapons/armor, sends overridden definitions on player join |

## Implementation Order

1. Config: Add `material_distances` + parsing in `VanillaConversionConfig`
2. `MaterialTierMapper.getDistanceRange()` — extracts material, looks up distance range
3. `CraftingConversionSystem` — use distance range + shared formula for level
4. `CraftingPreviewService` — override vanilla tooltips on player join
5. 1-tick defer in conversion for clean swap
6. Tune distance values against actual ore placement

## No External Dependencies

Everything uses our existing systems:
- `ItemDefinitionBuilder` for overriding item definitions
- `TranslationSyncService` for custom description text
- `RichTooltipFormatter` for converted RPG gear tooltips (already working)
- `MaterialTierMapper` for material extraction + rarity caps (already working)
- `DistanceBonusCalculator.estimateLevelFromDistance()` for the shared formula (already working)
