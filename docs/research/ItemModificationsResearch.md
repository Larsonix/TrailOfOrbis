# Gear System Design Document

This document outlines the design for a comprehensive gear/equipment system inspired by Path of Exile and similar ARPGs. The system overrides Hytale's default armor/weapon mechanics to add depth through levels, stats, quality, and rarity.

**Design Philosophy**: Leverage vanilla Hytale assets, items, and visual systems as much as possible. Avoid reinventing what the base game already provides.

---

## Table of Contents

1. [Overview](#overview)
2. [Rarity System](#rarity-system)
3. [Quality System](#quality-system)
4. [Modifier System (Stats)](#modifier-system-stats)
5. [Item Level System](#item-level-system)
6. [Durability System](#durability-system)
7. [Unique Items](#unique-items)
8. [Reroll Stones](#reroll-stones)
9. [Crafting Restrictions](#crafting-restrictions)
10. [Drop Mechanics](#drop-mechanics)
11. [Visual Presentation](#visual-presentation)
12. [Implementation Considerations](#implementation-considerations)

---

## Overview

Every piece of equipment (armor and weapons) in the game will have the following properties:

| Property | Description |
|----------|-------------|
| **Level** | Required player level to equip; determines base stats |
| **Rarity** | Tier determining modifier count and visual distinction |
| **Quality** | 1-101% multiplier affecting all stat values |
| **Modifiers** | Prefixes and suffixes that grant specific stats |
| **Durability** | Degradation over use, affected by quality and rarity |

---

## Rarity System

Rarity determines the potential power ceiling of an item through modifier slots and provides visual distinction.

### Rarity Tiers

| Rarity | Drop Chance | Color | Max Modifiers |
|--------|-------------|-------|---------------|
| Common | 50% | White/Gray | 1 |
| Uncommon | 30% | Green | 2 |
| Rare | 15% | Blue | 3 |
| Epic | 4% | Purple | 4 |
| Legendary | 0.9% | Orange | 4 + enhanced rolls |
| Mythic | 0.1% | Red/Gold | 4 + guaranteed high rolls |

### Rarity Effects

- **Common**: Basic gear, 1 random modifier (prefix or suffix)
- **Uncommon**: 1-2 modifiers
- **Rare**: 2-3 modifiers (at least 1 prefix and 1 suffix)
- **Epic**: 3-4 modifiers (2 prefixes, 2 suffixes max)
- **Legendary**: Always 4 modifiers with increased roll ranges (+20% to modifier value ranges)
- **Mythic**: Always 4 modifiers with top-tier rolls (top 25% of possible values guaranteed)

### Rarity and Durability

Higher rarity items have increased base durability:

| Rarity | Durability Multiplier |
|--------|----------------------|
| Common | 1.0x |
| Uncommon | 1.2x |
| Rare | 1.5x |
| Epic | 2.0x |
| Legendary | 3.0x |
| Mythic | 5.0x |

---

## Quality System

Quality is a percentage multiplier that affects all stat values on an item.

### Quality Ranges

| Quality Range | Description | Acquisition |
|---------------|-------------|-------------|
| 1-49% | Below average | Drops, Crafting |
| 50% | Normal/Average | Drops, Crafting |
| 51-100% | Above average | Drops only |
| 101% | Perfect | Drops only (ultra-rare) |

### Quality Mechanics

- **Base calculation**: All modifier values are multiplied by `quality / 50`
  - 1% quality = 0.02x stats (terrible)
  - 50% quality = 1.0x stats (normal)
  - 100% quality = 2.0x stats (double)
  - 101% quality = 2.4x stats (20% bonus over perfect)

### Quality and Durability

Quality directly affects maximum durability:

```
Max Durability = Base Durability × Rarity Multiplier × (quality / 50)
```

A 100% quality Epic item has 4x the durability of a 50% quality Common item.

### Quality Distribution on Drop

```
Quality Roll Distribution:
- 1-25%:   15% chance (poor quality)
- 26-49%:  25% chance (below average)
- 50%:     10% chance (exactly average)
- 51-75%:  30% chance (above average)
- 76-100%: 19.5% chance (high quality)
- 101%:    0.5% chance (perfect - drop only)
```

*Note: Distribution is weighted toward middle values with rare extremes.*

---

## Modifier System (Stats)

Modifiers are the primary source of stats on gear. Each item can have **prefixes** and **suffixes**, drawn from pools specific to the item type.

### Modifier Structure

- **Prefixes**: Up to 2 per item (from prefix pool)
- **Suffixes**: Up to 2 per item (from suffix pool)
- **Total**: Maximum 4 modifiers per item
- **Minimum**: Items can drop with as few as 1 modifier

### Modifier Pools (Examples)

Each gear slot and weapon type has its own prefix/suffix pools.

#### Weapon Prefixes (Examples)
| Modifier | Value Range | Description | Vanilla Icon |
|----------|-------------|-------------|--------------|
| Sharp | +5 to +50 | Flat physical damage | Sword icon |
| Blazing | +3 to +30 | Flat fire damage | Fire icon |
| Frozen | +3 to +30 | Flat ice damage | Snowflake icon |
| Vicious | +5% to +25% | Critical strike chance | Crosshair icon |
| Heavy | +10% to +50% | Physical damage % | Weight icon |

#### Weapon Suffixes (Examples)
| Modifier | Value Range | Description | Vanilla Icon |
|----------|-------------|-------------|--------------|
| of Strength | +5 to +30 | Strength attribute | Muscle icon |
| of Speed | +5% to +20% | Attack speed | Lightning icon |
| of Leeching | +1% to +5% | Life steal | Heart + drop icon |
| of Precision | +10 to +50 | Accuracy rating | Target icon |
| of the Hunt | +5% to +15% | Damage vs beasts | Paw icon |

#### Armor Prefixes (Examples)
| Modifier | Value Range | Description | Vanilla Icon |
|----------|-------------|-------------|--------------|
| Sturdy | +20 to +200 | Flat armor | Shield icon |
| Reinforced | +10% to +40% | Armor % | Shield icon |
| Healthy | +20 to +150 | Max health | Heart icon |
| Vigorous | +5% to +20% | Max health % | Heart icon |
| Resistant | +5% to +20% | All elemental resistance | Element icon |

#### Armor Suffixes (Examples)
| Modifier | Value Range | Description | Vanilla Icon |
|----------|-------------|-------------|--------------|
| of Vitality | +5 to +30 | Vitality attribute | Heart icon |
| of the Titan | +5 to +30 | Strength attribute | Muscle icon |
| of Regeneration | +1 to +10 | Health regen per second | Heart + arrow icon |
| of Warding | +5% to +15% | Magic resistance | Magic shield icon |
| of Evasion | +5% to +20% | Dodge chance | Feather icon |

### Modifier Roll Mechanics

1. Determine number of modifiers based on rarity (can be fewer than max)
2. Randomly select which are prefixes vs suffixes (respecting 2/2 max)
3. Roll each modifier from the appropriate pool (no duplicates)
4. Roll value within the modifier's range
5. Apply quality multiplier to final values

---

## Item Level System

Item level determines base stats and modifier tier access.

### Level Determination on Drop

- **Solo player**: Item level = Player level ± 2 (random variance)
- **Group**: Item level = Average party level ± 2
- **Minimum**: Item level cannot go below 1
- **Maximum**: Capped at zone/mob level if lower than player

### Equip Requirements

**Strict level gating**: Players cannot equip gear with an item level higher than their current level.

| Player Level | Can Equip |
|--------------|-----------|
| 10 | Item Level 1-10 |
| 25 | Item Level 1-25 |
| 50 | Item Level 1-50 |

*No "within X levels" buffer - must meet or exceed item level.*

### Level Effects

| Aspect | Effect |
|--------|--------|
| Base Stats | Higher level = higher base armor/damage values |
| Equip Requirement | Player level must be ≥ item level |
| Modifier Tiers | Higher item level unlocks better modifier tiers |

### Modifier Tier Scaling (Example)

```
Modifier: "Sharp" (Flat Physical Damage)
- Tier 1 (Level 1-10):   +5 to +15
- Tier 2 (Level 11-20):  +12 to +25
- Tier 3 (Level 21-30):  +20 to +35
- Tier 4 (Level 31-40):  +30 to +45
- Tier 5 (Level 41-50):  +40 to +50
```

---

## Durability System

Gear degrades with use and requires repair to maintain effectiveness.

### Durability Mechanics

- Each item has a **current durability** and **max durability**
- Durability decreases through combat (dealing/taking damage)
- At 0 durability, item provides no stats (broken)
- Broken items remain equipped but are non-functional

### Durability Calculation

```
Max Durability = Base Durability × Rarity Multiplier × Quality Multiplier

Where:
- Base Durability: Defined per item type (weapons degrade faster than armor)
- Rarity Multiplier: 1.0x (Common) to 5.0x (Mythic)
- Quality Multiplier: quality / 50 (0.02x to 2.4x)
```

### Durability Loss

| Action | Durability Loss |
|--------|-----------------|
| Dealing damage (weapon) | -1 per hit |
| Taking damage (armor) | -1 per hit received |
| Death | -10% of max durability on all equipped gear |

### Repair

- Repairs done at vanilla repair stations/anvils
- Repair cost scales with item level, rarity, and damage amount
- Uses vanilla repair materials where applicable

---

## Unique Items

Unique items are special named items with completely custom modifiers that cannot appear on regular gear.

### Unique Characteristics

- **Fixed name and appearance** (e.g., "Shadowfang", "Crown of the Fallen King")
- **Unique modifiers**: Effects that only exist on that specific item
- **Can break normal rules**: May have more than 4 modifiers, special mechanics
- **No quality variance**: Uniques always drop at a fixed quality (typically 100%)
- **Rarity independent**: Uniques are their own category, not part of the normal rarity system

### Unique Modifier Examples

| Unique Item | Special Modifier |
|-------------|------------------|
| Shadowfang (Dagger) | "Attacks from behind deal 50% more damage" |
| Crown of the Fallen King | "Gain 1% of damage dealt as health" |
| Boots of the Windwalker | "Cannot be slowed below 80% movement speed" |
| Heartseeker Bow | "Critical strikes always deal maximum damage" |

### Unique Drop Mechanics

- Uniques have their own drop table separate from normal gear
- Specific uniques may drop only from specific bosses/areas
- Global unique drop chance: ~0.5% per eligible drop (configurable)

---

## Reroll Stones

Special consumable items that allow players to modify existing gear properties. These reuse vanilla unused/underutilized item assets as "stones" with new functionality.

### Stone Types

| Stone | Effect | Source |
|-------|--------|--------|
| **Quality Stone** | Rerolls quality (1-100%, never 101%) | Drops, crafting |
| **Modifier Stone** | Rerolls all modifiers (keeps count) | Drops only |
| **Rarity Stone** | Rerolls rarity (can go up or down) | Rare drops only |
| **Chaos Stone** | Rerolls everything except base type | Very rare drops |

### Stone Mechanics

- Stones are consumed on use
- Cannot target Unique items
- Results are random within normal constraints
- **Quality Stone**: Rerolls 1-100% (101% remains drop-exclusive)
- **Modifier Stone**: Rerolls modifier types and values, keeps prefix/suffix count
- **Rarity Stone**: Full rarity reroll with standard drop weights
- **Chaos Stone**: Complete reroll of level, quality, rarity, and modifiers

### Reroll Workbench

A new crafting station (or extension of existing vanilla workbench) for using stones:

- Place gear + stone → Confirm → Receive modified gear
- Preview not available (gambling element)
- Some stones may require additional materials

### Stone Acquisition

| Stone | Primary Source |
|-------|----------------|
| Quality Stone | Common mob drops, basic crafting |
| Modifier Stone | Elite mobs, dungeon chests |
| Rarity Stone | Boss drops only |
| Chaos Stone | Raid bosses, world events |

---

## Crafting Restrictions

Crafting provides a controlled way to obtain gear but with significant limitations.

### Crafting Limits

| Property | Crafting Limit |
|----------|----------------|
| **Rarity** | Common or Uncommon only |
| **Quality** | Maximum 50% (cannot exceed normal) |
| **Modifiers** | 1-2 based on rarity |
| **Level** | Based on crafting station tier / recipe |

### Rationale

- Encourages monster hunting for high-end gear
- Crafting serves as a baseline/stopgap, not best-in-slot source
- Rare+ gear remains exclusive to drops
- Quality above 50% is drop-exclusive reward
- Reroll stones provide upgrade path for crafted gear

---

## Drop Mechanics

### Drop Flow

```
1. Enemy dies → Loot roll triggered
2. Roll: Does gear drop? (based on mob type, level, modifiers)
3. Roll: Rarity (50/30/15/4/0.9/0.1 distribution)
4. Roll: Quality (weighted distribution, 101% ultra-rare)
5. Determine item level (player/party level ± variance)
6. Select gear slot/type (based on mob loot table)
7. Roll modifier count (1 to max based on rarity)
8. Roll each modifier (pool → value)
9. Apply quality multiplier to all values
10. Calculate durability from rarity + quality
11. Generate item with all properties
```

### Party Loot

- Item level based on average party level
- Each player rolls independently for drops (instanced loot)
- Or: Single drop with need/greed system (configurable)

---

## Visual Presentation

### Design Philosophy

Maximize use of vanilla Hytale assets for visual consistency and reduced development overhead.

### Rarity Color Scheme

Each rarity has an associated color used across all visual indicators:

| Rarity | Color | Hex Code |
|--------|-------|----------|
| Common | White/Gray | `#FFFFFF` / `#AAAAAA` |
| Uncommon | Green | `#1EFF00` |
| Rare | Blue | `#0070FF` |
| Epic | Purple | `#A335EE` |
| Legendary | Orange | `#FF8000` |
| Mythic | Red/Gold | `#FF0000` / `#FFD700` |

### Rarity Visual Indicators

Multiple visual systems should communicate item rarity to players. We aim to implement as many as the API allows.

#### 1. Item Model Tinting

**Goal**: The actual item model/texture is tinted to reflect its rarity. A bronze armor piece would appear greenish if Uncommon, bluish if Rare, etc.

| Rarity | Visual Result |
|--------|---------------|
| Common | Original appearance (no tint) |
| Uncommon | Bronze armor → greenish-bronze |
| Rare | Bronze armor → bluish-bronze |
| Epic | Bronze armor → purple-bronze |
| Legendary | Bronze armor → orange/golden-bronze |
| Mythic | Bronze armor → red/crimson-bronze |

**Implementation Options**:

| Approach | Description |
|----------|-------------|
| Model Color Tint | Apply color multiplier to item model rendering |
| Texture Swap | Generate pre-tinted texture variants per rarity |
| Shader Tint | Custom shader applying color blend |
| Material Override | Change material color properties |

**Important**: Use ~50% transparency/blend so original item details (textures, shading, metallic look) remain visible. Should look like "green-tinted bronze armor", not "solid green armor".

> **⚠️ NEEDS RESEARCH**: Requires investigation into Hytale's rendering API - how item models are rendered, whether color tints/multipliers are supported, shader access, etc.

#### 2. Tooltip Border/Frame Color

**Goal**: When hovering over an item, the tooltip frame/border is colored to match rarity.

| Rarity | Tooltip Appearance |
|--------|-------------------|
| Common | Default/gray border |
| Uncommon | Green border/frame |
| Rare | Blue border/frame |
| Epic | Purple border/frame |
| Legendary | Orange border/frame (possibly glowing) |
| Mythic | Red/gold border/frame (possibly animated) |

> **⚠️ NEEDS RESEARCH**: Requires investigation into Hytale's tooltip API - whether custom tooltip styling is supported, border color control, frame textures, etc.

#### 3. Item Name Color & Prefix

**Goal**: Item name displays with rarity color and optional rarity tag prefix.

**Format Options**:
```
Option A: "[Rare] Iron Sword of Strength"     (with [Rare] in blue)
Option B: "Iron Sword of Strength"            (entire name in blue)
Option C: "[Rare] Iron Sword of Strength"     (tag in blue, name in white)
```

| Rarity | Display Example |
|--------|-----------------|
| Common | Iron Sword (white/gray text) |
| Uncommon | [Uncommon] Iron Sword (green text) |
| Rare | [Rare] Iron Sword (blue text) |
| Epic | [Epic] Iron Sword (purple text) |
| Legendary | [Legendary] Iron Sword (orange text) |
| Mythic | [Mythic] Iron Sword (red/gold text) |

> **⚠️ NEEDS RESEARCH**: Requires investigation into Hytale's text formatting - color codes, rich text support in item names/tooltips, etc.

#### 4. Inventory Slot Background/Highlight

**Goal**: Items in inventory display with a colored background or highlight behind them based on rarity.

| Rarity | Inventory Slot Appearance |
|--------|--------------------------|
| Common | Default slot background |
| Uncommon | Subtle green glow/tint behind item |
| Rare | Subtle blue glow/tint behind item |
| Epic | Purple glow/tint behind item |
| Legendary | Orange glow, possibly pulsing |
| Mythic | Red/gold glow, possibly animated |

> **⚠️ NEEDS RESEARCH**: Requires investigation into Hytale's inventory UI API - slot customization, background rendering, overlay support, etc.

#### 5. Ground Item Effects

**Goal**: When items are dropped on the ground, visual effects indicate rarity.

| Rarity | Ground Appearance |
|--------|-------------------|
| Common | Normal dropped item |
| Uncommon | Faint green glow/particles |
| Rare | Blue glow/particles |
| Epic | Purple glow/particles, light beam |
| Legendary | Orange glow, light beam, particles |
| Mythic | Red/gold glow, prominent light beam, special particles |

> **⚠️ NEEDS RESEARCH**: Requires investigation into Hytale's particle system, dropped item rendering, light/glow effects API, etc.

#### 6. Equipment Worn Effects

**Goal**: When wearing high-rarity gear, subtle visual effects are visible on the player model.

| Rarity | Worn Appearance |
|--------|-----------------|
| Common - Rare | No additional effects |
| Epic | Faint purple shimmer on armor |
| Legendary | Orange/golden glow effect |
| Mythic | Red/gold aura, particle effects |

> **⚠️ NEEDS RESEARCH**: Requires investigation into Hytale's player model rendering, equipment effects, aura/particle attachment, etc.

### Visual Indicators Summary

| Indicator | Priority | API Dependency |
|-----------|----------|----------------|
| Item Name Color & Prefix | High | Text formatting API |
| Tooltip Border Color | High | Tooltip API |
| Inventory Slot Highlight | Medium | Inventory UI API |
| Item Model Tinting | Medium | Rendering/shader API |
| Ground Item Effects | Low | Particle/effects API |
| Equipment Worn Effects | Low | Player model/effects API |

**Implementation Strategy**: Start with highest priority indicators that are most likely to be supported (text coloring, basic UI changes), then add more advanced visual effects as API capabilities are confirmed.

### Stat Icons

Reuse vanilla Hytale icons wherever possible for stat display:

| Stat Type | Vanilla Icon to Use |
|-----------|---------------------|
| Health / Max Health | Heart icon |
| Armor | Shield icon |
| Damage | Sword/weapon icon |
| Fire damage | Fire/flame icon |
| Ice damage | Snowflake icon |
| Speed | Lightning bolt icon |
| Strength | Muscle/fist icon |
| Regeneration | Heart with arrow icon |

### Tooltip Display

**Current Status**: Tooltip API expected soon but may have limitations initially.

#### Target Format (when API available)
```
[Rarity Color] Item Name
Item Level: 25 | Quality: 78%
─────────────────────────
[Heart Icon] +150 Max Health
[Shield Icon] +45 Armor
[Sword Icon] +28 Physical Damage
[Lightning Icon] +12% Attack Speed
─────────────────────────
Durability: 847/1000
Requires Level 22
```

#### Fallback (if tooltip API limited)
- Display stats in item lore/description text
- Use text-based icons: ❤ ⚔ 🛡 where supported
- Or prefix text: [Health] +150, [Armor] +45

### Item Name Generation

Generated from modifiers and base type:
```
[Prefix 1] [Prefix 2] [Base Item] [Suffix 1] [Suffix 2]

Examples:
- "Sharp Iron Sword of Strength"
- "Blazing Frozen Steel Axe of Speed"
- "Sturdy Leather Armor of Vitality"
```

---

## Implementation Considerations

### Vanilla-First Approach

| System | Vanilla Integration |
|--------|---------------------|
| Base items | Use existing Hytale weapons/armor as base types |
| Icons | Reuse game's stat/effect icons |
| Workbenches | Extend or reskin existing crafting stations |
| Repair | Integrate with vanilla repair mechanics |
| Stone items | Repurpose unused vanilla item assets |
| Visual effects | Use existing particle/glow systems |

### Data Storage

Each item needs to store:
```yaml
item:
  base_type: "iron_sword"        # Vanilla Hytale item
  item_level: 25
  rarity: "epic"
  quality: 78
  durability:
    current: 847
    max: 1000
  modifiers:
    prefixes:
      - type: "sharp"
        tier: 3
        value: 28
      - type: "blazing"
        tier: 2
        value: 15
    suffixes:
      - type: "of_strength"
        tier: 3
        value: 22
      - type: "of_speed"
        tier: 2
        value: 12
```

### Configuration Files Needed

- `gear-rarities.yml` - Rarity definitions, colors, and drop weights
- `gear-quality.yml` - Quality distribution and formulas
- `gear-durability.yml` - Base durability per item type, loss rates
- `modifiers/prefixes.yml` - All prefix definitions by item type
- `modifiers/suffixes.yml` - All suffix definitions by item type
- `uniques.yml` - Unique item definitions
- `reroll-stones.yml` - Stone definitions and recipes
- `loot-tables.yml` - Mob-specific drop tables

### Integration Points

- **AttributeManager**: Apply gear modifiers to player stats
- **CombatManager**: Factor in weapon modifiers for damage, handle durability loss
- **LevelingManager**: Gate equipping by level requirement
- **MobScalingManager**: Scale drop rates/quality with mob level
- **UI System**: Custom tooltips and gear inspection panels (when API available)

### Technical Notes & Research Required

Many features in this design depend on Hytale's modding API capabilities. The following areas require investigation before implementation:

#### High Priority Research

| Area | Questions to Answer |
|------|---------------------|
| **Item Data Storage** | How does Hytale store custom item data? NBT-like system? Custom properties API? |
| **Text Formatting** | Does Hytale support colored text in item names/tooltips? Rich text? Color codes? |
| **Tooltip API** | Can we customize tooltip content? Border colors? Layout? Expected soon but may have limitations. |
| **Inventory UI** | Can we modify slot backgrounds? Add overlays? Custom rendering per slot? |

#### Medium Priority Research

| Area | Questions to Answer |
|------|---------------------|
| **Item Rendering** | Can we tint/recolor item models? Shader access? Material color override? |
| **Dropped Items** | Can we add particles/glow to items on the ground? Per-item effects? |
| **Crafting System** | How do we hook into crafting? Can we intercept/modify crafted items? |
| **Equipment Events** | Events for equip/unequip? Can we block equipping (for level requirements)? |

#### Lower Priority Research

| Area | Questions to Answer |
|------|---------------------|
| **Player Model Effects** | Can we attach particles/auras to worn equipment? |
| **Durability Integration** | Does Hytale have native durability? Can we hook into it or must we replace it? |
| **Repair Stations** | How do vanilla repair mechanics work? Can we extend them? |
| **Loot Tables** | How does Hytale handle mob drops? Can we intercept/override? |

#### Fallback Strategies

For each system, we should have fallbacks if the ideal approach isn't possible:

| System | Ideal | Fallback |
|--------|-------|----------|
| Rarity display | Colored name + border + model tint | Just text prefix "[Rare]" |
| Tooltips | Rich custom tooltips with icons | Plain text description/lore |
| Visual tinting | Real-time model tint | Pre-generated texture variants or none |
| Inventory highlights | Colored slot backgrounds | No visual distinction in inventory |
| Ground effects | Particles + glow | No ground effects, rely on pickup tooltip |

---

## Decisions Log

| Topic | Decision | Rationale |
|-------|----------|-----------|
| Sockets/Gems | Not implementing | Complexity; reroll stones provide similar customization |
| Set Bonuses | Not for now | May revisit later |
| Enchanting | No | Reroll stones cover this use case |
| Trading | Not our concern | Server/vanilla handles this |
| Transmog | Not implementing | Other mods cover this |
| Visual style | Vanilla-first | Consistency, less work, better integration |

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 0.1 | 2026-01-23 | Initial design document |
| 0.2 | 2026-01-23 | Added durability, reroll stones, visual presentation, vanilla-first philosophy |
| 0.3 | 2026-01-23 | Expanded visual indicators (6 types), added research requirements, fallback strategies |
