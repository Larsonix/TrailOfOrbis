# Gear System Mechanics

Detailed mechanics decisions for the RPG Gear System. This document captures design intent and gameplay feel.

**Status**: Design Complete - Ready for Implementation

> **Implementation**: See [ROADMAP.md](../plan/ROADMAP.md) for the phased implementation plan.

---

## Table of Contents

1. [Core Philosophy](#core-philosophy)
2. [Stat System](#stat-system)
3. [Attribute Requirements](#attribute-requirements)
4. [Quality System](#quality-system)
5. [Rarity System](#rarity-system)
6. [Modifiers](#modifiers)
7. [Durability](#durability)
8. [Reroll Stones (Currency)](#reroll-stones-currency)
   - [Rarity Upgrade Stones](#rarity-upgrade-stones)
   - [Modifier Manipulation Stones](#modifier-manipulation-stones)
   - [Value Reroll Stones](#value-reroll-stones-divine-style)
   - [Corruption Stone](#corruption-stone)
   - [Essence System](#essence-system)
   - [Quality Stone](#quality-stone)
   - [Stone Drop System](#stone-drop-system)
9. [Unique Items](#unique-items)
10. [Balance & Configuration System](#balance--configuration-system)
    - [Balance Foundation](#balance-foundation)
    - [Gear Power Ratio](#gear-power-ratio)
    - [Configuration Files](#configuration-files)
    - [Balance Formulas](#balance-formulas)
    - [Example Calculations](#example-calculations)
    - [Tuning Guidelines](#tuning-guidelines)
11. [Open Questions](#open-questions)

---

## Core Philosophy

### Design Principles

1. **Open & Extensible**: The system should accommodate any stat, any effect, any complexity
2. **Level & Rarity Driven**: Item power (stat count, roll ranges) scales with level and rarity
3. **POE-Inspired Depth**: Complex currency system, meaningful itemization choices
4. **Attribute Gating**: Powerful gear requires investment in player attributes

---

## Stat System

### Gear → Stats (Direct Application)

Gear provides **final stats directly**, not attribute points.

```
Gear does NOT give: "+5 Strength" (attribute points)
Gear DOES give:     "+15 Physical Damage", "+100 Health", "+5% Crit"
```

**Rationale**: Keeps gear bonuses clear and readable. Player attributes remain a separate progression system.

### Available Stats

Any stat that exists in the player stat system can appear on gear. The modifier pool and roll ranges are determined by:
- **Item Level**: Higher level = access to better modifier tiers
- **Rarity**: Higher rarity = more modifiers, better roll ranges

### Stat Categories (Examples)

| Category | Example Stats |
|----------|---------------|
| Offensive | Physical Damage, Fire/Ice/Lightning Damage, Crit Chance, Crit Damage, Attack Speed |
| Defensive | Armor, Max Health, Health Regen, Elemental Resistances |
| Utility | Movement Speed, Life Steal, Cooldown Reduction |
| Special | Any custom stat the system supports |

*The system should be open to adding new stats without code changes - config-driven.*

---

## Attribute Requirements

### Concept

Powerful gear requires minimum player attributes to equip. This creates build diversity and prevents twinking.

### Player Attributes

The plugin has **5 attributes**:
- **STR** (Strength)
- **DEX** (Dexterity)
- **VIT** (Vitality)
- **INT** (Intelligence)
- **LUCK**

### How Requirements Are Calculated

Requirements are determined by **three factors**:

1. **Stats Present** → Which attribute(s) are required (presence only, not value)
2. **Item Level** → Base amount needed
3. **Rarity** → Multiplier on the requirement

```
Requirement = BaseFromLevel(itemLevel) × RarityMultiplier
```

**Important**: The *value* of the stat doesn't affect requirements. A +50 Health item and a +500 Health item at the same level and rarity require the same VIT. Only the *presence* of a health stat triggers the VIT requirement.

### Stat → Attribute Mapping

The stats rolled on gear determine which attributes are required. This mapping follows how attributes grant stats in the existing system:

#### STR (Strength) Stats
| Gear Stat | Reasoning |
|-----------|-----------|
| Physical Damage (flat/%) | STR grants physical damage |
| Melee Damage % | STR grants melee damage |
| Max Stamina | STR grants stamina |
| Jump Force Bonus | STR grants jump height |
| Armor Penetration | Physical combat stat |

#### DEX (Dexterity) Stats
| Gear Stat | Reasoning |
|-----------|-----------|
| Crit Multiplier | DEX grants crit multiplier |
| Movement Speed % | DEX grants move speed |
| Sprint/Walk/Run/Crouch Speed | DEX grants movement bonuses |
| Attack Speed % | DEX is the speed attribute |
| Projectile Damage % | DEX grants projectile damage |
| Stamina Regen | DEX grants stamina regen |
| Climb Speed Bonus | DEX grants climb speed |
| Parry Chance | Requires reflexes |

#### VIT (Vitality) Stats
| Gear Stat | Reasoning |
|-----------|-----------|
| Max Health (flat/%) | VIT is primary health attribute |
| Health Regen | VIT grants health regen |
| Armor (flat/%) | VIT grants armor |
| Fall Damage Reduction | VIT grants fall damage reduction |
| Knockback Resistance | VIT grants sturdiness |
| Energy Shield | VIT grants survivability |
| Max Oxygen / Oxygen Regen | VIT grants oxygen |
| Block Chance | Defensive stat |

#### INT (Intelligence) Stats
| Gear Stat | Reasoning |
|-----------|-----------|
| Max Mana (flat/%) | INT grants mana |
| Mana Regen | INT grants mana regen |
| Spell Damage (flat/%) | INT grants spell damage |
| Fire/Cold/Lightning/Chaos Damage | INT grants elemental damage |
| Fire/Cold/Lightning/Chaos Damage % | INT grants elemental scaling |
| Fire/Cold/Lightning/Chaos Multiplier | INT grants elemental power |
| Cast Speed % | INT grants casting speed |
| Area Damage % | Often spell-related |
| Elemental Penetration | INT-based combat |
| Elemental Resistances | Magic defense |

#### LUCK Stats
| Gear Stat | Reasoning |
|-----------|-----------|
| Crit Chance | LUCK grants crit chance |
| Accuracy | LUCK grants accuracy |
| Evasion | LUCK grants evasion |
| Life Steal | Luck-based recovery |
| True Damage | Bypassing defenses = luck |

*If gear has multiple stat types, it requires multiple attributes.*

### Rarity Multipliers

| Rarity | Requirement Multiplier |
|--------|------------------------|
| Common | 0.1x (minimal) |
| Uncommon | 0.25x |
| Rare | 0.5x |
| Epic | 0.75x |
| Legendary | 0.9x |
| Mythic | 1.0x (full) |

### Example Calculation

**Item**: Level 50 Chestplate with +500 Health, +50 Armor

- Health → VIT requirement
- Armor → STR requirement
- Level 50 base: ~25 per attribute (example)

| Rarity | VIT Required | STR Required |
|--------|--------------|--------------|
| Common | 3 | 3 |
| Uncommon | 6 | 6 |
| Rare | 13 | 13 |
| Epic | 19 | 19 |
| Legendary | 23 | 23 |
| Mythic | 25 | 25 |

### Display Format

Requirements shown with comparison to player stats:

```
Requires: 45 Strength (You have: 52 ✓)
Requires: 30 Vitality (You have: 18 ✗)
```

- Green checkmark if met
- Red X if not met
- Cannot equip until all requirements met

### When Requirements Aren't Met

**Unequip Behavior**:
- If wearing gear and attributes drop below requirements (respec, debuff, etc.)
- Item **immediately auto-unequips** to inventory
- Item **cannot be re-equipped** until requirements are met again

**No grace period** - instant enforcement.

---

## Quality System

### Range

- **1-100%**: Normal quality range
- **101%**: "Perfect" quality (drop-only, ultra-rare)

### Effect on Stats

All modifier values are multiplied by `quality / 50`:

| Quality | Multiplier | Effect |
|---------|------------|--------|
| 1% | 0.02x | Terrible |
| 25% | 0.5x | Poor |
| 50% | 1.0x | Normal |
| 75% | 1.5x | Good |
| 100% | 2.0x | Excellent |
| 101% | 2.02x | Perfect |

### Perfect Quality (101%)

- **Name**: Displayed as "Perfect" quality
- **Visual**: Distinct visual indicator (TBD - special particle, glow, or icon)
- **Drop Only**: Cannot be crafted or rerolled to 101%
- **Drop Chance**: ~0.5% of drops

### Quality Distribution on Drop

```
1-25%:   15% chance (poor)
26-49%:  25% chance (below average)
50%:     10% chance (exactly normal)
51-75%:  30% chance (above average)
76-100%: 19.5% chance (excellent)
101%:    0.5% chance (perfect)
```

---

## Rarity System

### Tiers

| Rarity | Max Modifiers | Special Effect |
|--------|---------------|----------------|
| Common | 1 | - |
| Uncommon | 2 | - |
| Rare | 3 | - |
| Epic | 4 | - |
| Legendary | 4 | +20% to modifier value ranges |
| Mythic | 4 | Rolls in top 25% of Legendary range |

### Legendary Mechanics

Legendary items have **extended roll ranges**:

```
Example: "Sharp" modifier normally rolls 10-50 damage

Normal (Common-Epic):  10-50 range
Legendary:             10-60 range (+20% to max)
```

### Mythic Mechanics

Mythic items roll in the **top 25% of the Legendary range**:

```
Example: "Sharp" modifier

Legendary range: 10-60
Mythic rolls:    47-60 (top 25% of 10-60)
```

**Mythic = Guaranteed high-end Legendary rolls**

### Drop Weights

| Rarity | Drop Chance |
|--------|-------------|
| Common | 50% |
| Uncommon | 30% |
| Rare | 15% |
| Epic | 4% |
| Legendary | 0.9% |
| Mythic | 0.1% |

---

## Modifiers

### Structure

Each modifier has:
- **ID**: Unique identifier (e.g., "sharp", "of_the_whale")
- **Type**: Prefix or Suffix
- **Stat**: Which stat it affects
- **Value Range**: Scales with item level (no tiers)
- **Weight**: Rarity of the modifier (higher = more common)

### Prefix vs Suffix Distribution

| Type | Category | Examples |
|------|----------|----------|
| **Prefix** | Offensive stats | Damage, crit, attack speed, penetration |
| **Suffix** | Defensive/utility stats | Health, armor, resistances, speed, regen |

### Modifier Count by Rarity

| Rarity | Prefixes | Suffixes | Total |
|--------|----------|----------|-------|
| Common | 0-1 | 0-1 | 1 |
| Uncommon | 0-1 | 0-2 | 1-2 |
| Rare | 1-2 | 1-2 | 2-3 |
| Epic | 1-2 | 1-2 | 3-4 |
| Legendary | 2 | 2 | 4 |
| Mythic | 2 | 2 | 4 |

*Maximum: 2 prefixes + 2 suffixes = 4 modifiers*

### Scaling System (No Tiers)

**Modifier values scale directly with item level** - no tier system.

```
Value = BaseMin + (ItemLevel × ScalePerLevel)
Range = Value to Value × RangeMultiplier
```

Higher level items naturally roll higher values within the modifier's defined scaling curve.

### Modifier Weighting

Some modifiers are rarer than others:

| Weight | Rarity | Example Modifiers |
|--------|--------|-------------------|
| 100 | Very Common | Physical Damage, Max Health, Armor |
| 50 | Common | Fire/Cold/Lightning Damage, Resistances |
| 25 | Uncommon | Crit Chance, Attack Speed |
| 10 | Rare | Life Steal, Crit Multiplier |
| 5 | Very Rare | Penetration stats, True Damage |

---

## Gear Types

### Armor Categories (Vanilla Mapping)

| Category | Vanilla Material | Weight Class |
|----------|------------------|--------------|
| Light Armor | Bronze | Low armor, high mobility |
| Medium Armor | Iron | Balanced |
| Heavy Armor | Thorium | High armor, lower mobility |

### Gear Slots

Any equippable gear or weapon can have modifiers:

| Category | Slots |
|----------|-------|
| Weapons | All weapon types (swords, axes, bows, staves, etc.) |
| Head | Helmets, hoods, hats |
| Chest | Chestplates, tunics, robes |
| Hands | Gauntlets, gloves, wraps |
| Legs | Greaves, pants, leggings |
| Feet | Boots, shoes |
| Shield | All shields |
| Accessories | Rings, amulets (if available in Hytale) |

---

## Available Stats (Full List)

All stats from the ComputedStats system can appear as modifiers:

### Core Stats (Suffix)

| Stat | Flat | Percent | Notes |
|------|------|---------|-------|
| Max Health | ✅ | ✅ | |
| Max Mana | ✅ | ✅ | |
| Max Stamina | ✅ | ✅ | |
| Max Oxygen | ✅ | - | Niche |
| Energy Shield | ✅ | ✅ | |

### Damage Stats (Prefix)

| Stat | Flat | Percent | Multiplier | Notes |
|------|------|---------|------------|-------|
| Physical Damage | ✅ | ✅ | - | |
| Spell Damage | ✅ | ✅ | - | |
| Fire Damage | ✅ | ✅ | ✅ | |
| Cold Damage | ✅ | ✅ | ✅ | |
| Lightning Damage | ✅ | ✅ | ✅ | |
| Chaos Damage | ✅ | ✅ | ✅ | |
| True Damage | ✅ | - | - | Very rare |

### Attack Type Modifiers (Prefix)

| Stat | Type | Notes |
|------|------|-------|
| Melee Damage % | Percent | |
| Projectile Damage % | Percent | |
| Area Damage % | Percent | |

### Critical Stats (Prefix)

| Stat | Type | Notes |
|------|------|-------|
| Critical Chance | Percent | Rare modifier |
| Critical Multiplier | Percent | Rare modifier |

### Speed Stats (Suffix)

| Stat | Type | Restriction |
|------|------|-------------|
| Movement Speed % | Percent | **Boots only** |
| Attack Speed % | Percent | Weapons primarily |
| Cast Speed % | Percent | Magic weapons/armor |
| Sprint Speed Bonus | Flat | Boots only |
| Walk Speed % | Percent | Boots only |
| Run Speed % | Percent | Boots only |
| Crouch Speed % | Percent | Boots only |
| Climb Speed Bonus | Flat | Gloves/boots |
| Jump Force Bonus | Flat | Boots only |

### Defense Stats (Suffix)

| Stat | Flat | Percent | Notes |
|------|------|---------|-------|
| Armor | ✅ | ✅ | |
| Evasion | ✅ | - | |
| Block Chance | - | ✅ | Shields only |
| Parry Chance | - | ✅ | Weapons only |

### Resistance Stats (Suffix)

| Stat | Type | Notes |
|------|------|-------|
| Fire Resistance | Percent | |
| Cold Resistance | Percent | |
| Lightning Resistance | Percent | |
| Chaos Resistance | Percent | |

### Regeneration Stats (Suffix)

| Stat | Type | Notes |
|------|------|-------|
| Health Regen | Flat | Per second |
| Mana Regen | Flat | Per second |
| Stamina Regen | Flat | Per second |
| Oxygen Regen | Flat | Niche |

### Utility Stats (Mixed)

| Stat | Type | Prefix/Suffix | Notes |
|------|------|---------------|-------|
| Accuracy | Flat | Suffix | |
| Life Steal | Percent | Prefix | Rare, weapons |
| Armor Penetration | Flat | Prefix | Rare |
| Knockback Resistance | Percent | Suffix | |
| Fall Damage Reduction | Percent | Suffix | Boots only |

### Penetration Stats (Prefix - Very Rare)

| Stat | Type | Notes |
|------|------|-------|
| Fire Penetration | Percent | Ignores resistance |
| Cold Penetration | Percent | Ignores resistance |
| Lightning Penetration | Percent | Ignores resistance |
| Chaos Penetration | Percent | Ignores resistance |

---

## Gear-Specific Restrictions

Most modifiers can roll on any gear, but some have restrictions:

| Modifier | Allowed Gear |
|----------|--------------|
| Movement Speed % | Boots only (suffix) |
| Sprint/Walk/Run/Crouch Speed | Boots only |
| Jump Force Bonus | Boots only |
| Climb Speed Bonus | Gloves, Boots |
| Fall Damage Reduction | Boots only |
| Block Chance | Shields only |
| Parry Chance | Weapons only |
| Attack Speed % | Weapons primarily, some gloves |
| Cast Speed % | Magic weapons, Light armor |
| Life Steal | Weapons only |
| Armor Penetration | Weapons only |
| Elemental Penetration | Weapons only |

### Extensibility

The modifier system is **config-driven**:
- Add new modifiers via YAML
- Define any stat as a modifier target
- Set weights, restrictions, and scaling per modifier
- No code changes for new modifiers

---

## Durability

### Vanilla Hytale Handles This

**Important**: Durability mechanics, degradation, repair, and broken item behavior are all handled by vanilla Hytale's native systems. We do NOT need to implement custom durability logic.

From our research (`ItemDataStorageResearch.md`, `CombatSystemResearch.md`):
- `ItemStack` has native `durability` and `maxDurability` fields
- `DamageSystems.DamageArmor` automatically degrades armor on hits
- Broken items have reduced effectiveness via `BrokenPenalties` config
- Repair is handled by vanilla crafting/repair systems

### Our Integration

For RPG gear, we simply:
1. **Set initial max durability** when gear is generated based on rarity
2. **Read durability state** to check if item is broken (for stat application)
3. **Let vanilla handle everything else** (degradation, repair, visuals)

### Rarity Durability Multipliers

When generating gear, we set `maxDurability` based on rarity:

| Rarity | Multiplier | Example (base 100) |
|--------|------------|-------------------|
| Common | 1.0x | 100 durability |
| Uncommon | 1.2x | 120 durability |
| Rare | 1.5x | 150 durability |
| Epic | 2.0x | 200 durability |
| Legendary | 3.0x | 300 durability |
| Mythic | 5.0x | 500 durability |

### Implementation

```java
// When generating gear, set durability using native fields
double baseDurability = item.getItem().getMaxDurability();
double rarityMultiplier = gearRarity.getDurabilityMultiplier();
double maxDura = baseDurability * rarityMultiplier;

ItemStack rpgItem = itemStack.withRestoredDurability(maxDura);
```

---

## Reroll Stones (Currency)

### Philosophy

Inspired by **Path of Exile's currency system** but with our own identity - deep, complex, and meaningful without being a direct copy.

### Design Goals

- Many different stone types with specific effects
- Deterministic vs gambling tradeoffs
- Build around item crafting as endgame
- Stones drop from mobs (affected by Quantity% and Rarity%)
- Not a POE copy - our own twist and creativity

---

### Stone Categories

| Category | Purpose | Exists |
|----------|---------|--------|
| Rarity Upgrade | Upgrade item rarity tier | ✅ Up to Epic only |
| Modifier Manipulation | Add, remove, reroll modifiers | ✅ Medium-complex depth |
| Stripping | Remove all modifiers | ✅ |
| Corruption | High-risk/high-reward, locks item | ✅ |
| Essences | Guarantee specific modifier types | ✅ Via crafting table |
| Value Reroll | Reroll modifier numeric values | ✅ Two tiers |
| Quality Reroll | Reroll quality percentage | ✅ |

---

### Rarity Upgrade Stones

**Rule**: Stones can upgrade rarity **up to Epic only**.
- Legendary items can only be **dropped**, never crafted via stones
- Mythic items can only be **dropped**, never crafted via stones

| Stone | Effect |
|-------|--------|
| Uncommon Upgrade Stone | Common → Uncommon |
| Rare Upgrade Stone | Uncommon → Rare |
| Epic Upgrade Stone | Rare → Epic |

*No Legendary or Mythic upgrade stones exist.*

---

### Modifier Manipulation Stones

A variety of stones for surgical control over modifiers:

| Stone | Effect | Notes |
|-------|--------|-------|
| **Chaos Stone** | Reroll ALL modifiers (keeps modifier count) | Full gamble |
| **Addition Stone** | Add one random modifier (if slot available) | Requires empty mod slot |
| **Removal Stone** | Remove one random modifier | Risk losing good mod |
| **Cleansing Stone** | Remove ALL modifiers (item becomes "blank") | Start over on good base |
| **Prefix Stone** | Reroll only prefix modifiers | Targeted reroll |
| **Suffix Stone** | Reroll only suffix modifiers | Targeted reroll |
| **Swap Stone** | Remove one random mod, add one random mod | Chaos Orb POE2 style |
| **Lock Stone** | Lock one modifier from being changed | Protects a good roll |
| **Tier Stone** | Upgrade one modifier to next tier | If higher tier available |
| **Mirror Stone** | Duplicate one modifier's value to another of same type | Copy a good roll |

*List to be expanded - should feel unique, not POE copy-paste.*

---

### Value Reroll Stones (Divine-Style)

Reroll the numeric values of modifiers without changing which modifiers are present.

| Stone | Effect |
|-------|--------|
| **Lesser Divine Stone** | Reroll values of ONE random modifier |
| **Greater Divine Stone** | Reroll values of ALL modifiers at once |

**Example**:
- Sword has "Sharp" (+15 damage, range was 10-50)
- Use Greater Divine Stone
- "Sharp" rerolls to +42 damage (same modifier, new value)

---

### Corruption Stone

**High-risk, high-reward mechanic** that permanently locks the item.

#### Possible Outcomes (Not POE Copy - Our Twist)

| Outcome | Description | Approx Chance |
|---------|-------------|---------------|
| **Ascend** | Modifier tier upgraded, item gains "Corrupted" tag | ~15% |
| **Empower** | Add a corruption-only implicit modifier | ~15% |
| **Transform** | One modifier becomes a rare corruption-only modifier | ~15% |
| **Stabilize** | Nothing happens, item just becomes corrupted | ~25% |
| **Fracture** | Item loses one random modifier, becomes corrupted | ~15% |
| **Shatter** | Item is destroyed completely | ~10% |
| **Paradox** | Quality inverts (low becomes high, high becomes low) | ~5% |

**Rules**:
- Corrupted items **cannot be modified** by any other stones
- Corrupted items can still be equipped and used normally
- Corruption is a one-way, final decision
- Unique "corrupted-only" modifiers can be very powerful

---

### Essence System

Essences guarantee a specific modifier type when used. Integrated with in-game crystals.

#### How It Works

1. **Crystal Collection**: Players find colored crystals in the world (existing Hytale items)
2. **Essence Table**: Custom crafting station GUI where:
   - Player inserts any gear piece
   - Player inserts essences (crystals with different hues = different stats)
   - Table can store essences **infinitely**
3. **Application**: Using an essence guarantees a modifier of that type

#### Essence Types (Based on Crystal Hues)

| Crystal Hue | Essence Type | Guaranteed Modifier Category |
|-------------|--------------|------------------------------|
| Red | Fire Essence | Fire damage modifier |
| Blue | Ice Essence | Ice damage / cold modifier |
| Yellow | Lightning Essence | Lightning damage modifier |
| Green | Nature Essence | Health / regen modifier |
| White | Pure Essence | Armor / defense modifier |
| Purple | Arcane Essence | Mana / spell modifier |
| Orange | Warrior Essence | Physical damage / STR modifier |
| Cyan | Swift Essence | Speed / DEX modifier |
| Pink | Fortune Essence | Crit / LUCK modifier |

#### Essence Tiers

| Tier | Crystal Rarity | Effect |
|------|----------------|--------|
| Lesser Essence | Common crystal | Guarantees Tier 1-2 modifier |
| Essence | Uncommon crystal | Guarantees Tier 2-3 modifier |
| Greater Essence | Rare crystal | Guarantees Tier 3-4 modifier |
| Perfect Essence | Epic crystal | Guarantees Tier 4-5 modifier |

---

### Quality Stone

Rerolls quality percentage (1-100%).

**Rules**:
- Can never roll 101% (Perfect) - that's drop-only
- Can be used on any rarity
- Quality affects all modifier values via multiplier

---

### Stone Drop System

Stones drop from mobs, affected by:

#### Quantity% (More Drops)

Increases the **number** of items/stones that drop.

| Source | Bonus |
|--------|-------|
| Elite mobs | +50% base |
| Boss mobs | +100% base |
| Zone modifiers | Variable |
| Gear mods (if added) | From equipment |

#### Rarity% (Better Drops)

Increases the **quality/rarity** of drops.

| Source | Bonus |
|--------|-------|
| Distance from spawn | Further = better |
| Mob difficulty | Elite/boss bonus |
| Player LUCK attribute | +X% per LUCK point |
| Gear mods (if added) | From equipment |

**Note**: LUCK attribute affects **Rarity%** but NOT Quantity%.

---

## Unique Items

### Concept

Named items with **special effects that cannot appear on regular gear**.

### Characteristics

- Fixed name and appearance
- Custom modifiers with unique effects
- Quality fixed at 100% (no variance)
- Own rarity category (not Common-Mythic)
- Can break normal modifier rules (more than 4, special mechanics)

---

### Effect Categories (12 Total)

#### Easily Implementable (Categories 1-8)

---

**Category 1: Conditional Stat Bonuses ("While X")**

Effects that check a condition and apply stat bonuses while true.

| Condition | Example Effect |
|-----------|----------------|
| Health Threshold | "+50% damage while below 30% health" |
| Mana Threshold | "+20% cast speed while mana is full" |
| Resource Empty | "+100% crit chance while stamina is empty" |
| Moving | "+15% evasion while moving" |
| Stationary | "+30% armor while standing still" |
| In Air | "+25% damage while airborne" |
| Sprinting | "+10% movement speed while sprinting" |
| In Combat | "+20% attack speed while in combat" |
| Out of Combat | "+5% health regen while out of combat" |
| Full Health | "Cannot be critically hit while at full health" |

*Implementation: Check player state each tick, apply/remove stat modifiers.*

---

**Category 2: On-Hit Effects (Dealing Damage)**

Effects that trigger when you hit an enemy.

| Effect Type | Example |
|-------------|---------|
| Chance to Heal | "10% chance to heal 5% of damage dealt" |
| Life Steal | "3% of damage dealt as health" |
| Mana on Hit | "Gain 5 mana on hit" |
| Apply Debuff | "20% chance to slow enemy by 30% for 3s" |
| Bonus Damage | "Deal 10% of target's current health as bonus" |
| Chain Damage | "10% chance to deal 50% damage to nearby enemy" |
| Stat Steal | "Steal 5% of target's armor for 4s" |

*Implementation: Hook into damage dealt event, apply effect.*

---

**Category 3: On-Kill Effects**

Effects that trigger when you kill an enemy.

| Effect Type | Example |
|-------------|---------|
| Heal on Kill | "Recover 10% of max health on kill" |
| Resource on Kill | "Gain 20 mana on kill" |
| Buff on Kill | "Gain 15% movement speed for 5s on kill" |
| Damage Buff | "Gain 5% damage per kill, max 50%, resets after 10s" |
| Explosion | "Enemies explode for 10% of their max HP on kill" |
| Soul Harvest | "Gain 1 soul per kill, consume 10 for massive buff" |

*Implementation: Hook into death event, apply effect to killer.*

---

**Category 4: On-Take-Damage Effects**

Effects that trigger when you receive damage.

| Effect Type | Example |
|-------------|---------|
| Damage Reflection | "Reflect 20% of physical damage to attacker" |
| Defensive Buff | "Gain 30% armor for 3s when hit" |
| Counter Attack | "25% chance to automatically attack when hit" |
| Damage Absorption | "10% of damage taken is absorbed as mana" |
| Revenge Damage | "Next attack deals +50% damage after being hit" |
| Thorns | "Attackers take 50 physical damage" |

*Implementation: Hook into damage received event, apply effect.*

---

**Category 5: Damage Conversion & Modification**

Effects that change how damage works.

| Effect Type | Example |
|-------------|---------|
| Element Conversion | "50% of physical damage converted to fire" |
| Added Element | "Attacks deal additional 20 fire damage" |
| Damage as Extra | "Gain 15% of physical as extra cold damage" |
| Type Amplification | "Fire damage increased by 30%" |
| Penetration | "Damage penetrates 15% of fire resistance" |
| True Damage | "10% of damage dealt is converted to true damage" |

*Implementation: Modify damage calculation pipeline.*

---

**Category 6: Enemy State Conditionals**

Extra effects based on enemy condition.

| Condition | Example Effect |
|-----------|----------------|
| Burning Enemy | "+30% damage to burning enemies" |
| Frozen Enemy | "Hits against frozen enemies always crit" |
| Low Health Enemy | "+50% damage to enemies below 30% health" |
| Full Health Enemy | "First hit against full health deals double damage" |
| Debuffed Enemy | "+20% damage to slowed enemies" |
| Elite/Boss | "+25% damage to elite enemies" |

*Implementation: Check target state before/during damage calculation.*

---

**Category 7: Positional Effects**

Effects based on positioning.

| Position | Example Effect |
|----------|----------------|
| Behind Target | "+40% damage when attacking from behind" |
| Above Target | "+25% damage when above the enemy" |
| Close Range | "+20% damage within 3 blocks" |
| Long Range | "+15% projectile damage beyond 10 blocks" |
| Elevation | "+10% damage per block of height advantage" |

*Implementation: Compare positions/facing directions during combat.*

---

**Category 8: Resource Trade-offs**

Effects that spend one resource for another benefit.

| Trade-off | Example |
|-----------|---------|
| Health for Damage | "Spend 5% max health to deal 30% more damage" |
| Mana as Shield | "Damage taken from mana before health (1:2 ratio)" |
| Stamina for Speed | "Sprint costs no stamina but drains health" |
| All-in | "Deal 200% damage but take 50% as self-damage" |

*Implementation: Intercept resource usage or damage, redirect.*

---

#### Moderately Complex (Categories 9-12)

---

**Category 9: "Recently" Time-Based Effects**

Effects that check if something happened in the last X seconds (typically 4s).

| Trigger | Example Effect |
|---------|----------------|
| Killed Recently | "+20% damage if you killed in last 4s" |
| Hit Recently | "+30% armor if hit in last 4s" |
| Jumped Recently | "Double damage on next attack after jumping" |
| Used Ability Recently | "+15% cast speed if used ability in last 4s" |
| Crit Recently | "Guaranteed crit if you crit in last 4s" |
| Took Damage Recently | "+50% life regen if damaged in last 4s" |

*Implementation: Track timestamps per event type, check on stat calculation.*

---

**Category 10: Stacking Buffs**

Effects that build up over time or actions.

| Stack Type | Example |
|------------|---------|
| On Hit Stacks | "Gain Fury on hit, +2% damage per stack, max 25" |
| On Kill Stacks | "Gain Rampage on kill, +1% speed per stack" |
| On Take Damage | "Gain Vengeance when hit, consume for burst" |
| Time-Based | "Gain 1 stack per second in combat, +5% damage each" |
| Combo System | "Consecutive hits grant +10% damage, resets on miss" |

*Implementation: Counter per effect, cap, decay/reset timer.*

---

**Category 11: Cooldown-Based Effects**

Effects with internal cooldowns or periodic triggers.

| Type | Example |
|------|---------|
| Periodic Trigger | "Every 5 seconds, gain 10% of max health" |
| Proc Cooldown | "On hit effect can only trigger once per 2s" |
| Charge System | "Store up to 3 charges, each use consumes 1" |
| Burst Window | "Every 30s, next 5s deal double damage" |

*Implementation: Timestamp tracking, timer system.*

---

**Category 12: Aura/Proximity Effects**

Effects that affect nearby entities.

| Type | Example |
|------|---------|
| Damage Aura | "Enemies within 5 blocks take 10 fire damage/sec" |
| Debuff Aura | "Enemies within 3 blocks are slowed by 20%" |
| Buff Aura | "Allies within 10 blocks gain +10% damage" |
| Proximity Scaling | "+5% damage for each enemy within 5 blocks" |

*Implementation: Periodic area scan, apply effects to entities in range.*

---

### Effects to Avoid (For Now)

| Effect Type | Reason |
|-------------|--------|
| Spawn Minions | Requires entity creation, AI, pathfinding |
| Create Projectiles | Requires projectile system integration |
| Custom Animations | Requires client-side assets |
| Transform Player | Complex model/stat replacement |
| Teleportation | Position manipulation edge cases |
| Time Manipulation | Slowmo/freeze effects are complex |
| Copy Enemy Abilities | Requires dynamic skill system |

---

### Technical Architecture

```
UniqueEffect (Interface)
├── ConditionalEffect     → Checks state, returns stat modifier
├── OnHitEffect           → Triggers on dealing damage
├── OnKillEffect          → Triggers on kill
├── OnDamagedEffect       → Triggers on taking damage
├── PeriodicEffect        → Triggers on timer
└── AuraEffect            → Affects nearby entities

EffectContext
├── Player reference
├── Target reference (if applicable)
├── Damage info (if applicable)
├── Timestamp
└── Random seed
```

### State Tracking Required

| State | Storage | Update Frequency |
|-------|---------|------------------|
| Last kill timestamp | Per-player | On kill |
| Last hit timestamp | Per-player | On damage dealt |
| Last damaged timestamp | Per-player | On damage taken |
| Stack counts | Per-player per-effect | On trigger |
| Cooldown timestamps | Per-player per-effect | On trigger |
| Combat state | Per-player | Tick-based |

---

*See [UniqueEffectsDesign.md](./UniqueEffectsDesign.md) for example unique items and detailed implementation notes.*

---

## Balance & Configuration System

### Philosophy

All balance values are **config-driven**. The formulas live in code, but every number comes from YAML configuration. This allows:

- **Runtime tweaking** - Change values, reload, test immediately
- **No recompilation** - Adjust feel without touching code
- **Documented defaults** - Every value has a sensible starting point derived from existing plugin math

---

### Balance Foundation

#### Player Power Budget

The gear system is balanced against the player's attribute-based power. Key facts from the existing plugin:

| Level | Total Attribute Points | Per-Attribute (balanced 5-way) |
|-------|------------------------|--------------------------------|
| 1 | 1 | ~0 each |
| 10 | 10 | 2 each |
| 25 | 25 | 5 each |
| 50 | 50 | 10 each |
| 100 | 100 | 20 each |
| 200 | 200 | 40 each |

**Source**: `config.yml` - `attributes.startingPoints: 1`, `attributes.pointsPerLevel: 1`

#### Attribute → Stat Grants (From Existing Code)

These values from `AttributeCalculator.java` and `config.yml` determine what attributes give:

| Attribute | Stat | Per Point | Notes |
|-----------|------|-----------|-------|
| **STR** | Max Health | +1.0 | Secondary health source |
| **STR** | Physical Damage (flat) | +0.2 | |
| **STR** | Physical Damage % | +0.2% | |
| **STR** | Max Stamina | +0.5 | |
| **DEX** | Crit Multiplier | +1.0% | |
| **DEX** | Movement Speed % | +1.0% | |
| **DEX** | Sprint Speed Bonus | +0.3% | |
| **DEX** | Climb Speed Bonus | +0.5% | |
| **DEX** | Stamina Regen | +0.1/sec | |
| **INT** | Max Mana | +1.0 | |
| **INT** | Spell Damage (flat) | +0.15 | |
| **INT** | Spell Damage % | +0.5% | |
| **INT** | Signature Energy Regen | +0.1/sec | |
| **VIT** | Max Health | +2.0 | Primary health source |
| **VIT** | Health Regen | +0.1/sec | |
| **VIT** | Armor | +5.0 | |
| **VIT** | Max Oxygen | +0.5 | |
| **VIT** | Oxygen Regen | +0.05/sec | |
| **VIT** | Fall Damage Reduction | +0.5% | Capped at 90% |
| **LUCK** | Crit Chance | +0.1% | |
| **LUCK** | Accuracy | +10.0 | |
| **LUCK** | Evasion | +5.0 | |

#### Base Stats (Level 1, No Attributes)

From `BaseStats.java`:

| Stat | Base Value |
|------|------------|
| Max Health | 100 |
| Max Mana | 0 |
| Max Stamina | 10 |
| Max Oxygen | 100 |
| Crit Chance | 5% |
| Crit Multiplier | 150% |
| Accuracy | 10 |

#### Example: Level 50 Balanced Player

With 50 attribute points distributed evenly (10 each):

```
Max Health    = 100 + (STR × 1) + (VIT × 2) = 100 + 10 + 20 = 130 HP
Max Mana      = 0 + (INT × 1) = 10 mana
Armor         = VIT × 5 = 50 armor
Crit Chance   = 5% + (LUCK × 0.1%) = 6%
Crit Mult     = 150% + (DEX × 1%) = 160%
Move Speed    = DEX × 1% = +10%
Phys Damage   = STR × 0.2 = +2 flat, +2%
```

---

### Gear Power Ratio

The core balance question: **How much power should gear add relative to attributes?**

**Default: 50%** - A full set of level-appropriate Mythic gear adds ~50% of what your attributes provide.

This means:
- Attributes remain the primary progression
- Gear is meaningful but doesn't overshadow builds
- Skill tree (which doesn't grant attributes) provides the third power source

#### Power Distribution Example

Level 50 player with 130 HP from attributes:
- 50% gear ratio → gear can add up to ~65 HP total
- Distributed across 5 armor slots + weapon
- Mythic chest (20% of gear budget) → ~13 HP

---

### Configuration Files

#### gear-balance.yml

The master balance configuration file. All numbers are tweakable.

```yaml
# =============================================================================
# GEAR BALANCE CONFIGURATION
# =============================================================================
# This file controls all gear balance values. Change these to adjust game feel.
# After editing, use /rpg reload to apply changes without restart.
# =============================================================================

# -----------------------------------------------------------------------------
# POWER SCALING
# -----------------------------------------------------------------------------
# Controls how much power gear adds relative to player attributes.

power_scaling:
  # At item_level = player_level, a full Mythic set adds this % of attribute stats
  # 0.5 = gear adds half as much total power as your attributes provide
  # 1.0 = gear doubles your attribute-based power
  # Recommended range: 0.3 - 0.7
  gear_power_ratio: 0.5

  # How much of total gear power each slot provides (must sum to 1.0)
  # Weapons deal damage, so they get the largest share
  slot_weights:
    weapon: 0.30      # 30% - highest for damage-dealing
    chest: 0.20       # 20% - largest armor piece
    legs: 0.15        # 15%
    head: 0.12        # 12%
    hands: 0.08       # 8%
    feet: 0.08        # 8%
    shield: 0.07      # 7% - optional slot

  # Item level scaling - how stats grow with item level
  # stat_value = base_value × (1 + item_level × level_scaling_factor)
  level_scaling_factor: 0.02  # +2% per item level

# -----------------------------------------------------------------------------
# RARITY SYSTEM
# -----------------------------------------------------------------------------
# Each rarity tier has different stat power and modifier counts.

rarity:
  common:
    stat_multiplier: 0.4      # 40% of base stats
    max_modifiers: 1
    prefix_range: [0, 1]      # Min-max prefixes
    suffix_range: [0, 1]      # Min-max suffixes
    drop_weight: 50           # Relative drop chance

  uncommon:
    stat_multiplier: 0.6
    max_modifiers: 2
    prefix_range: [0, 1]
    suffix_range: [0, 2]
    drop_weight: 30

  rare:
    stat_multiplier: 0.8
    max_modifiers: 3
    prefix_range: [1, 2]
    suffix_range: [1, 2]
    drop_weight: 15

  epic:
    stat_multiplier: 1.0      # 100% = baseline
    max_modifiers: 4
    prefix_range: [1, 2]
    suffix_range: [1, 2]
    drop_weight: 4

  legendary:
    stat_multiplier: 1.2      # +20% extended range
    max_modifiers: 4
    prefix_range: [2, 2]      # Always 2 prefixes
    suffix_range: [2, 2]      # Always 2 suffixes
    drop_weight: 0.9

  mythic:
    stat_multiplier: 1.2      # Same max as legendary
    max_modifiers: 4
    prefix_range: [2, 2]
    suffix_range: [2, 2]
    min_roll_percentile: 0.75 # Rolls in top 25% of range
    drop_weight: 0.1

# -----------------------------------------------------------------------------
# QUALITY SYSTEM
# -----------------------------------------------------------------------------
# Quality multiplies all modifier values on the item.

quality:
  # Quality 50 = 1.0x multiplier (baseline)
  # Formula: multiplier = quality / baseline
  baseline: 50

  min: 1                      # Minimum quality
  max: 100                    # Maximum quality (normal)
  perfect: 101                # Perfect quality (drop-only)

  # Drop distribution (must sum to 1.0)
  drop_distribution:
    poor: 0.15                # 1-25%
    below_average: 0.25       # 26-49%
    normal: 0.10              # Exactly 50%
    above_average: 0.30       # 51-75%
    excellent: 0.195          # 76-100%
    perfect: 0.005            # 101% (0.5% chance)

  # Note: Durability is handled by vanilla Hytale, not affected by quality

# -----------------------------------------------------------------------------
# ATTRIBUTE REQUIREMENTS
# -----------------------------------------------------------------------------
# Gear requires minimum player attributes to equip.
# Requirement = base_from_level × rarity_multiplier

attribute_requirements:
  # Base requirement formula
  # At item level 50, base = 50 × 0.5 = 25 per required attribute
  level_to_base_ratio: 0.5

  # Minimum level before requirements apply
  min_item_level_for_requirements: 5

  # Rarity multipliers on the base requirement
  # Mythic requires full base, Common requires almost nothing
  rarity_multipliers:
    common: 0.1               # Level 50 Common → 2-3 per attr
    uncommon: 0.25            # Level 50 Uncommon → 6 per attr
    rare: 0.5                 # Level 50 Rare → 12-13 per attr
    epic: 0.75                # Level 50 Epic → 18-19 per attr
    legendary: 0.9            # Level 50 Legendary → 22-23 per attr
    mythic: 1.0               # Level 50 Mythic → 25 per attr

# -----------------------------------------------------------------------------
# MODIFIER SCALING
# -----------------------------------------------------------------------------
# How modifier values scale with item level.

modifier_scaling:
  # Global scaling factor applied to all modifiers
  # Higher = stats grow faster with item level
  global_scale_per_level: 0.02    # +2% per item level

  # Roll variance - how much RNG affects the final value
  # 0.3 = values can be 70%-130% of calculated base
  roll_variance: 0.3

  # Modifier weight categories (for drop pools)
  weight_categories:
    very_common: 100          # Physical Damage, Max Health, Armor
    common: 50                # Elemental Damage, Resistances
    uncommon: 25              # Crit Chance, Attack Speed
    rare: 10                  # Life Steal, Crit Multiplier
    very_rare: 5              # Penetration stats, True Damage

# -----------------------------------------------------------------------------
# DURABILITY
# -----------------------------------------------------------------------------
# NOTE: Durability mechanics (degradation, repair, broken behavior) are handled
# by vanilla Hytale. We only configure initial max durability based on rarity.

durability:
  # Rarity multipliers for initial max durability
  # Applied to the item's base maxDurability when gear is generated
  rarity_multipliers:
    common: 1.0
    uncommon: 1.2
    rare: 1.5
    epic: 2.0
    legendary: 3.0
    mythic: 5.0

# -----------------------------------------------------------------------------
# LUCK & LOOT
# -----------------------------------------------------------------------------

loot:
  # How LUCK attribute affects drop rarity
  # Higher rarity% = better chance for rare items
  luck_to_rarity_percent: 0.5     # +0.5% rarity per LUCK point

  # Distance from spawn affects rarity
  # Further from spawn = better drops
  distance_scaling:
    enabled: true
    blocks_per_percent: 100       # +1% rarity per 100 blocks from spawn
    max_bonus: 50                 # Cap at +50% rarity bonus

  # Mob type bonuses
  mob_bonuses:
    normal:
      quantity_bonus: 0
      rarity_bonus: 0
    elite:
      quantity_bonus: 0.5         # +50% more drops
      rarity_bonus: 0.25          # +25% rarity
    boss:
      quantity_bonus: 1.0         # +100% more drops
      rarity_bonus: 0.5           # +50% rarity

# -----------------------------------------------------------------------------
# STONE DROP RATES
# -----------------------------------------------------------------------------

stone_drops:
  # Base chance for any stone to drop (before quantity bonuses)
  base_drop_chance: 0.05          # 5% base chance

  # Individual stone weights (relative rarity)
  stone_weights:
    # Rarity upgrade stones
    uncommon_upgrade: 100
    rare_upgrade: 50
    epic_upgrade: 20

    # Modifier manipulation
    chaos_stone: 80
    addition_stone: 60
    removal_stone: 60
    cleansing_stone: 40
    prefix_stone: 30
    suffix_stone: 30
    swap_stone: 25
    lock_stone: 15
    tier_stone: 10
    mirror_stone: 5

    # Value reroll
    lesser_divine: 40
    greater_divine: 10

    # Special
    corruption_stone: 8
    quality_stone: 25
```

#### gear-modifiers.yml

Defines all available modifiers and their scaling.

```yaml
# =============================================================================
# GEAR MODIFIERS CONFIGURATION
# =============================================================================
# Defines all modifiers that can roll on gear.
# Each modifier has: type, stat, scaling, weight, and restrictions.
# =============================================================================

# -----------------------------------------------------------------------------
# PREFIX MODIFIERS (Offensive)
# -----------------------------------------------------------------------------

prefixes:
  # --- Physical Damage ---
  sharp:
    display_name: "Sharp"
    stat: physical_damage
    stat_type: flat
    base_min: 1.0
    base_max: 3.0
    scale_per_level: 0.2          # +0.2 per item level
    weight: 100                   # Very common

  heavy:
    display_name: "Heavy"
    stat: physical_damage_percent
    stat_type: percent
    base_min: 1.0
    base_max: 3.0
    scale_per_level: 0.15
    weight: 100

  # --- Elemental Damage ---
  blazing:
    display_name: "Blazing"
    stat: fire_damage
    stat_type: flat
    base_min: 1.0
    base_max: 2.5
    scale_per_level: 0.15
    weight: 50
    required_attribute: INT

  frozen:
    display_name: "Frozen"
    stat: cold_damage
    stat_type: flat
    base_min: 1.0
    base_max: 2.5
    scale_per_level: 0.15
    weight: 50
    required_attribute: INT

  shocking:
    display_name: "Shocking"
    stat: lightning_damage
    stat_type: flat
    base_min: 1.0
    base_max: 2.5
    scale_per_level: 0.15
    weight: 50
    required_attribute: INT

  chaotic:
    display_name: "Chaotic"
    stat: chaos_damage
    stat_type: flat
    base_min: 0.5
    base_max: 2.0
    scale_per_level: 0.12
    weight: 25
    required_attribute: INT

  # --- Critical ---
  precise:
    display_name: "Precise"
    stat: crit_chance
    stat_type: percent
    base_min: 0.5
    base_max: 1.5
    scale_per_level: 0.05
    weight: 25
    required_attribute: LUCK

  deadly:
    display_name: "Deadly"
    stat: crit_multiplier
    stat_type: percent
    base_min: 2.0
    base_max: 5.0
    scale_per_level: 0.2
    weight: 10
    required_attribute: DEX

  # --- Attack Modifiers ---
  swift:
    display_name: "Swift"
    stat: attack_speed_percent
    stat_type: percent
    base_min: 1.0
    base_max: 3.0
    scale_per_level: 0.1
    weight: 25
    allowed_slots: [weapon, hands]
    required_attribute: DEX

  vampiric:
    display_name: "Vampiric"
    stat: life_steal
    stat_type: percent
    base_min: 0.5
    base_max: 1.5
    scale_per_level: 0.03
    weight: 10
    allowed_slots: [weapon]
    required_attribute: LUCK

  # --- Penetration (Very Rare) ---
  piercing:
    display_name: "Piercing"
    stat: armor_penetration
    stat_type: flat
    base_min: 2.0
    base_max: 5.0
    scale_per_level: 0.3
    weight: 5
    allowed_slots: [weapon]
    required_attribute: STR

  searing:
    display_name: "Searing"
    stat: fire_penetration
    stat_type: percent
    base_min: 2.0
    base_max: 5.0
    scale_per_level: 0.15
    weight: 5
    allowed_slots: [weapon]
    required_attribute: INT

# -----------------------------------------------------------------------------
# SUFFIX MODIFIERS (Defensive/Utility)
# -----------------------------------------------------------------------------

suffixes:
  # --- Health ---
  of_the_whale:
    display_name: "of the Whale"
    stat: max_health
    stat_type: flat
    base_min: 5.0
    base_max: 15.0
    scale_per_level: 1.0
    weight: 100
    required_attribute: VIT

  of_vitality:
    display_name: "of Vitality"
    stat: max_health_percent
    stat_type: percent
    base_min: 1.0
    base_max: 3.0
    scale_per_level: 0.1
    weight: 50
    required_attribute: VIT

  # --- Mana ---
  of_the_sage:
    display_name: "of the Sage"
    stat: max_mana
    stat_type: flat
    base_min: 3.0
    base_max: 8.0
    scale_per_level: 0.5
    weight: 50
    required_attribute: INT

  # --- Armor ---
  of_the_fortress:
    display_name: "of the Fortress"
    stat: armor
    stat_type: flat
    base_min: 5.0
    base_max: 15.0
    scale_per_level: 1.0
    weight: 100
    required_attribute: VIT

  of_iron_skin:
    display_name: "of Iron Skin"
    stat: armor_percent
    stat_type: percent
    base_min: 2.0
    base_max: 5.0
    scale_per_level: 0.15
    weight: 50
    required_attribute: VIT

  # --- Resistances ---
  of_the_salamander:
    display_name: "of the Salamander"
    stat: fire_resistance
    stat_type: percent
    base_min: 3.0
    base_max: 8.0
    scale_per_level: 0.2
    weight: 50
    required_attribute: INT

  of_the_yeti:
    display_name: "of the Yeti"
    stat: cold_resistance
    stat_type: percent
    base_min: 3.0
    base_max: 8.0
    scale_per_level: 0.2
    weight: 50
    required_attribute: INT

  of_grounding:
    display_name: "of Grounding"
    stat: lightning_resistance
    stat_type: percent
    base_min: 3.0
    base_max: 8.0
    scale_per_level: 0.2
    weight: 50
    required_attribute: INT

  of_the_void:
    display_name: "of the Void"
    stat: chaos_resistance
    stat_type: percent
    base_min: 2.0
    base_max: 6.0
    scale_per_level: 0.15
    weight: 25
    required_attribute: INT

  # --- Regeneration ---
  of_regeneration:
    display_name: "of Regeneration"
    stat: health_regen
    stat_type: flat
    base_min: 0.2
    base_max: 0.5
    scale_per_level: 0.02
    weight: 50
    required_attribute: VIT

  of_the_arcane:
    display_name: "of the Arcane"
    stat: mana_regen
    stat_type: flat
    base_min: 0.1
    base_max: 0.3
    scale_per_level: 0.015
    weight: 50
    required_attribute: INT

  # --- Movement (Boots Only) ---
  of_speed:
    display_name: "of Speed"
    stat: movement_speed_percent
    stat_type: percent
    base_min: 2.0
    base_max: 5.0
    scale_per_level: 0.1
    weight: 25
    allowed_slots: [feet]
    required_attribute: DEX

  of_the_wind:
    display_name: "of the Wind"
    stat: sprint_speed_bonus
    stat_type: percent
    base_min: 3.0
    base_max: 8.0
    scale_per_level: 0.15
    weight: 25
    allowed_slots: [feet]
    required_attribute: DEX

  of_the_cat:
    display_name: "of the Cat"
    stat: fall_damage_reduction
    stat_type: percent
    base_min: 5.0
    base_max: 15.0
    scale_per_level: 0.3
    weight: 25
    allowed_slots: [feet]
    required_attribute: VIT

  # --- Utility ---
  of_evasion:
    display_name: "of Evasion"
    stat: evasion
    stat_type: flat
    base_min: 3.0
    base_max: 8.0
    scale_per_level: 0.4
    weight: 25
    required_attribute: LUCK

  of_accuracy:
    display_name: "of Accuracy"
    stat: accuracy
    stat_type: flat
    base_min: 5.0
    base_max: 15.0
    scale_per_level: 0.8
    weight: 25
    required_attribute: LUCK

  # --- Shield Only ---
  of_blocking:
    display_name: "of Blocking"
    stat: block_chance
    stat_type: percent
    base_min: 2.0
    base_max: 5.0
    scale_per_level: 0.1
    weight: 50
    allowed_slots: [shield]
    required_attribute: VIT

  # --- Weapon Only ---
  of_parrying:
    display_name: "of Parrying"
    stat: parry_chance
    stat_type: percent
    base_min: 1.0
    base_max: 3.0
    scale_per_level: 0.08
    weight: 25
    allowed_slots: [weapon]
    required_attribute: DEX
```

---

### Balance Formulas

All formulas used by the gear system, with config variable references.

#### Modifier Value Calculation

```
base_value = random(base_min, base_max) + (item_level × scale_per_level)
roll_factor = random(1.0 - roll_variance, 1.0 + roll_variance)
quality_factor = quality / quality.baseline
rarity_factor = rarity.stat_multiplier

final_value = base_value × roll_factor × quality_factor × rarity_factor
```

**Mythic special rule**: `roll_factor` minimum is `rarity.mythic.min_roll_percentile`

#### Attribute Requirement Calculation

```
base_requirement = item_level × attribute_requirements.level_to_base_ratio
rarity_multiplier = attribute_requirements.rarity_multipliers[rarity]

final_requirement = floor(base_requirement × rarity_multiplier)
```

**Example**: Level 50 Epic chest with Health and Armor stats:
- Base = 50 × 0.5 = 25
- Epic multiplier = 0.75
- VIT required = floor(25 × 0.75) = 18
- (Armor also requires VIT, but same attribute = no stacking)

#### Durability Calculation

Vanilla Hytale handles durability mechanics. We only set initial max durability:

```
base_durability = item.getItem().getMaxDurability()  // From vanilla item definition
rarity_mult = durability.rarity_multipliers[rarity]

max_durability = floor(base_durability × rarity_mult)
```

*Note: Degradation, repair, and broken behavior are all vanilla systems.*

#### Drop Rarity Calculation

```
base_weights = rarity.*.drop_weight for each rarity

luck_bonus = player_luck × loot.luck_to_rarity_percent
distance_bonus = min(distance_from_spawn / blocks_per_percent, max_bonus)
mob_bonus = loot.mob_bonuses[mob_type].rarity_bonus

total_rarity_bonus = luck_bonus + distance_bonus + mob_bonus

// Apply bonus by shifting weights toward rarer items
adjusted_weights = apply_rarity_shift(base_weights, total_rarity_bonus)
selected_rarity = weighted_random(adjusted_weights)
```

---

### Example Calculations

#### Example 1: Level 50 Epic Sword

**Config values used:**
- `gear_power_ratio`: 0.5
- `slot_weights.weapon`: 0.30
- `rarity.epic.stat_multiplier`: 1.0
- `quality`: 75 (rolled)

**Modifier rolled**: "Sharp" (physical_damage flat)
- `base_min`: 1.0, `base_max`: 3.0
- `scale_per_level`: 0.2

**Calculation:**
```
base_value = random(1.0, 3.0) + (50 × 0.2) = 2.0 + 10.0 = 12.0
roll_factor = random(0.7, 1.3) = 1.1
quality_factor = 75 / 50 = 1.5
rarity_factor = 1.0

final_physical_damage = 12.0 × 1.1 × 1.5 × 1.0 = 19.8 → +20 Physical Damage
```

**Attribute requirement:**
```
base = 50 × 0.5 = 25
epic_mult = 0.75
STR_required = floor(25 × 0.75) = 18 STR
```

#### Example 2: Level 100 Mythic Chestplate

**Modifiers rolled:**
- Prefix 1: "Heavy" (+Physical Damage %)
- Prefix 2: "Blazing" (+Fire Damage)
- Suffix 1: "of the Whale" (+Max Health)
- Suffix 2: "of the Fortress" (+Armor)

**Quality**: 90 (excellent roll)

**"of the Whale" calculation:**
```
base_value = random(5, 15) + (100 × 1.0) = 10 + 100 = 110
roll_factor = max(0.75, random(0.7, 1.3)) = 0.95  (Mythic minimum)
quality_factor = 90 / 50 = 1.8
rarity_factor = 1.2

final_health = 110 × 0.95 × 1.8 × 1.2 = 225.7 → +226 Max Health
```

**Attribute requirements:**
```
base = 100 × 0.5 = 50
mythic_mult = 1.0

Stats present: Physical (STR), Fire (INT), Health (VIT), Armor (VIT)
Required: 50 STR, 50 INT, 50 VIT
```

---

### Tuning Guidelines

#### If gear feels too weak:
- Increase `power_scaling.gear_power_ratio` (0.5 → 0.7)
- Increase modifier `scale_per_level` values
- Decrease `quality.baseline` (50 → 40)

#### If gear feels too strong:
- Decrease `power_scaling.gear_power_ratio` (0.5 → 0.3)
- Decrease modifier `scale_per_level` values
- Increase `quality.baseline` (50 → 60)

#### If requirements are too harsh:
- Decrease `attribute_requirements.level_to_base_ratio` (0.5 → 0.3)
- Decrease individual `rarity_multipliers`

#### If rare items drop too often:
- Increase `rarity.common.drop_weight`
- Decrease `loot.luck_to_rarity_percent`

#### If items break too quickly:
- Increase `durability.rarity_multipliers` values (higher = more durable)
- Note: Degradation rate is controlled by vanilla Hytale, not configurable here

---

## Open Questions

*Most balance and formula questions have been resolved in the [Balance & Configuration System](#balance--configuration-system) section above. Remaining open questions are implementation-specific.*

### Stone System (Implementation Details)

1. **Stone usage limits** - Currently unlimited. Consider adding:
   - Max uses per item? (e.g., 10 total stone uses)
   - Diminishing returns on repeated stone use?

2. **Lock Stone specifics**
   - How many mods can be locked at once? (Suggest: 1)
   - Does lock persist through other stone uses? (Suggest: Yes, until explicitly unlocked)
   - Can locked mods be removed? (Suggest: No)

3. **Corruption-only modifiers** - Need to design unique mods that only appear through corruption:
   - Suggest: "Corrupted" prefix versions of existing mods with +50% value but a downside
   - Example: "Corrupted Sharp" (+30 Physical Damage, -10% Attack Speed)

### Essence Table (Hytale Integration)

1. **Exact crystal item IDs in vanilla Hytale** - Requires decompiled game research
2. **Essence Table crafting recipe** - Suggest: Rare drop from bosses or craftable from rare materials
3. **GUI layout** - Depends on Hytale UI API capabilities

### Unique Items (Content Creation)

1. **Specific unique item designs** - Mechanics framework exists (see [Unique Items](#unique-items)), need actual item content:
   - Item names, lore, and visual identity
   - Which effects from the 12 categories each unique uses
   - Drop sources (which bosses/activities)

2. **Unique item level requirements** - Suggest: Fixed per item, not scaled

### Repair System

**Handled by vanilla Hytale.** No custom implementation needed. Players repair items using vanilla crafting/repair mechanics.

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 0.1 | 2026-01-23 | Initial mechanics capture from Q&A |
| 0.2 | 2026-01-23 | Added attribute requirements with stat→attribute mapping |
| 0.3 | 2026-01-23 | Full stone/currency system: rarity upgrades, modifier manipulation, corruption, essences, divine stones, quality stones |
| 0.4 | 2026-01-23 | Added Quantity%/Rarity% loot system, LUCK integration |
| 0.5 | 2026-01-23 | Full modifier system: all 50+ stats, gear types (Bronze/Iron/Thorium), prefix/suffix split, weighting, gear restrictions, no-tier scaling |
| 0.6 | 2026-01-23 | Unique items: 12 effect categories, 15+ example uniques, technical architecture (see UniqueEffectsDesign.md) |
| 0.7 | 2026-01-23 | **Balance & Configuration System**: Full config-driven balance with `gear-balance.yml` and `gear-modifiers.yml` templates. Includes balance foundation from existing plugin math, all formulas documented, example calculations, tuning guidelines. Open questions cleaned up. |
| 0.8 | 2026-01-23 | **Consistency fixes**: Clarified durability/repair handled by vanilla Hytale (no custom implementation needed). Removed quality→durability config. Simplified durability config to only rarity multipliers for initial max. |
