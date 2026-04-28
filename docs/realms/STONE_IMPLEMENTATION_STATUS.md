# Stone System - Hytale Naming Scheme

This document defines the final stone system with Hytale-themed names, replacing the temporary POE names.

**Last Updated:** 2026-01-26

---

## Complete Stone List (21 Stones)

### Reroll Stones (4)

| Enum | Display Name | Effect | Maps | Gear |
|------|--------------|--------|------|------|
| `GAIAS_CALIBRATION` | Gaia's Calibration | Rerolls **ALL** modifier values within their ranges | ✅ | ⏳ |
| `EMBER_OF_TUNING` | Ember of Tuning | Rerolls **ONE** random modifier value | ✅ | ⏳ |
| `ALTERVERSE_SHARD` | Alterverse Shard | Rerolls **ALL** modifier types completely | ✅ | ⏳ |
| `ORBISIAN_BLESSING` | Orbisian Blessing | Rerolls item quality (1-100) | ✅ | ⏳ |

### Enhancement Stones (5)

| Enum | Display Name | Effect | Maps | Gear |
|------|--------------|--------|------|------|
| `GAIAS_GIFT` | Gaia's Gift | Adds one new random modifier | ✅ | ⏳ |
| `SPARK_OF_POTENTIAL` | Spark of Potential | Common → Uncommon + adds 1 modifier | ✅ | ⏳ |
| `CORE_OF_ASCENSION` | Core of Ascension | Uncommon → Rare + adds 1 modifier | ✅ | ⏳ |
| `HEART_OF_LEGENDS` | Heart of Legends | Rare → Epic + adds 1 modifier | ✅ | ⏳ |
| `CARTOGRAPHERS_POLISH` | Cartographer's Polish | Improves map quality (+1-5%, max 100) | ✅ | N/A |

### Removal Stones (3)

| Enum | Display Name | Effect | Maps | Gear |
|------|--------------|--------|------|------|
| `PURGING_EMBER` | Purging Ember | Removes **ALL** unlocked modifiers | ✅ | ⏳ |
| `EROSION_SHARD` | Erosion Shard | Removes **ONE** random unlocked modifier | ✅ | ⏳ |
| `TRANSMUTATION_CRYSTAL` | Transmutation Crystal | Removes one + adds one (atomic swap) | ✅ | ⏳ |

### Lock Stones (2)

| Enum | Display Name | Effect | Maps | Gear |
|------|--------------|--------|------|------|
| `WARDENS_SEAL` | Warden's Seal | Locks a random modifier (protected from rerolls) | ✅ | ⏳ |
| `WARDENS_KEY` | Warden's Key | Unlocks a random locked modifier | ✅ | ⏳ |

### Level Stones (3)

| Enum | Display Name | Effect | Maps | Gear |
|------|--------------|--------|------|------|
| `FORTUNES_COMPASS` | Fortune's Compass | Adds +5% Item Quantity (map only, max 20%) | ✅ | N/A |
| `ALTERVERSE_KEY` | Alterverse Key | Changes map biome type randomly | ✅ | N/A |
| `THRESHOLD_STONE` | Threshold Stone | Rerolls level ±3 (map level OR gear level requirement) | ✅ | ⏳ |

### Corruption Stone (1)

| Enum | Display Name | Effect | Maps | Gear |
|------|--------------|--------|------|------|
| `VARYNS_TOUCH` | Varyn's Touch | Corrupts with random outcome (35% nothing, 25% reroll, 25% add corruption mod, 15% upgrade rarity) | ✅ | ⏳ |

### Special Stones (3)

| Enum | Display Name | Effect | Maps | Gear |
|------|--------------|--------|------|------|
| `GAIAS_PERFECTION` | Gaia's Perfection | Sets quality to 101 (perfect) | ✅ | ⏳ |
| `LOREKEEPERS_SCROLL` | Lorekeeper's Scroll | Identifies an unidentified item | ✅ | ⏳ |
| `GENESIS_STONE` | Genesis Stone | Adds 1-2 modifiers to item with none | ✅ | ⏳ |

---

## Naming Theme Reference

| Theme | Stones | Lore Connection |
|-------|--------|-----------------|
| **Gaia** | Calibration, Gift, Perfection | Goddess of creation - enhances and perfects |
| **Varyn** | Touch | Antagonist - source of corruption |
| **Alterverse** | Shard, Key | Parallel dimensions - chaos and change |
| **Orbis** | Blessing | The world itself - quality of existence |
| **Warden** | Seal, Key | Guardians - protection and locks |
| **Ember/Shard** | Tuning, Erosion | Fragments - smaller/single effects |
| **Heart/Core/Spark** | Legends, Ascension, Potential | Essence - rarity tiers |
| **Fortune** | Compass | Luck and treasure - Item Quantity |
| **Threshold** | Stone | Boundaries - level adjustment |
| **Transmutation** | Crystal | Alchemy - exchange and transformation |

---

## Migration: POE → Hytale Names

| Old POE Name | New Hytale Name | Notes |
|--------------|-----------------|-------|
| `DIVINE_ORB` | `GAIAS_CALIBRATION` | Same effect |
| `CHAOS_ORB` | `ALTERVERSE_SHARD` | Same effect |
| `BLESSED_ORB` | `ORBISIAN_BLESSING` | Same effect |
| `EXALTED_ORB` | `GAIAS_GIFT` | Same effect |
| `ORB_OF_AUGMENTATION` | *removed* | Split into 3 tier-specific stones |
| `GLASSBLOWERS_BAUBLE` | `CARTOGRAPHERS_POLISH` | Same effect |
| `ORB_OF_SCOURING` | `PURGING_EMBER` | Same effect |
| `ORB_OF_ANNULMENT` | `EROSION_SHARD` | Same effect |
| `ETERNAL_LOCK` | `WARDENS_SEAL` | Same effect |
| `ETERNAL_KEY` | `WARDENS_KEY` | Same effect |
| `VAAL_ORB` | `VARYNS_TOUCH` | Same effect |
| `SACRED_ORB` | `GAIAS_PERFECTION` | Same effect |
| `SCROLL_OF_WISDOM` | `LOREKEEPERS_SCROLL` | Same effect |
| `ORB_OF_TRANSMUTATION` | `GENESIS_STONE` | Same effect |

---

## Implementation Status Summary

| Status | Count | Description |
|--------|-------|-------------|
| ✅ Maps working | **21** | All stones fully implemented for realm maps |
| ⏳ Gear pending | **18** | Awaiting GearData integration |
| N/A Not applicable | **3** | Map-only stones (Cartographer's Polish, Fortune's Compass, Alterverse Key) |
| **Total** | **21** | Complete stone system |

---

## Stone Rarity Distribution

| Rarity | Stones |
|--------|--------|
| **Common** | Cartographer's Polish, Lorekeeper's Scroll, Genesis Stone, Spark of Potential |
| **Uncommon** | Alterverse Shard, Orbisian Blessing, Purging Ember, Warden's Key, Ember of Tuning, Core of Ascension, Fortune's Compass, Threshold Stone |
| **Rare** | Gaia's Calibration, Erosion Shard, Warden's Seal, Varyn's Touch, Heart of Legends, Transmutation Crystal |
| **Epic** | Gaia's Gift, Gaia's Perfection, Alterverse Key |

---

## Threshold Stone (Special Case)

The `THRESHOLD_STONE` works on **both** maps and gear:

**On Maps:**
- Rerolls the map's level within ±3 of current level
- Example: Level 50 map → rerolls to 47-53

**On Gear:**
- Rerolls the item's level requirement within ±3
- Example: Level 30 requirement → rerolls to 27-33
- Cannot go below level 1

This makes it the only stone (besides universal ones) that explicitly targets both item types with the same thematic effect.

---

## Fortune's Compass (Implementation Notes)

Fortune's Compass adds Item Quantity via **modifier** rather than a direct property:
- Adds a `ITEM_QUANTITY` modifier with value 5
- Stacks with existing IQ modifiers from drops
- Maximum total IQ from Fortune's Compass usage: 20%
- Quality-based IQ (from map quality) is separate and additional

---

## File References

- **StoneType.java** - Enum definitions with all 21 stones
- **StoneActionRegistry.java** - Action implementations for all stones
- **StoneAction.java** - Action interface
- **StoneActionResult.java** - Result record
- **StoneUtils.java** - ItemStack serialization
- **StoneDropListener.java** - Drop system (ECS system for mob death loot)
- **StonePickerPageSupplier.java** - Native interaction handler for right-click use
- **StoneActionRegistryTest.java** - Comprehensive tests for all stones
