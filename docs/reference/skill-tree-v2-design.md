# Skill Tree v2 — 14-Branch Design Document

> **Purpose**: Defines stat tables, node effects, and branch themes for the expanded 14-branch skill tree.
> Drives Phase 3 (elemental arm stat refactor) and Phase 4 (octant branch implementation).
> **Status**: Draft — review and iterate before YAML implementation.

---

## Table of Contents

1. [Overview & Design Philosophy](#1-overview--design-philosophy)
2. [Universal Arm Structure](#2-universal-arm-structure)
3. [Elemental Arms (6)](#3-elemental-arms)
4. [Octant Arms (8)](#4-octant-arms)
5. [NEW STAT Proposals](#5-new-stat-proposals)
6. [3D Positioning Principles](#6-3d-positioning-principles)
7. [Bridge Entry Map](#7-bridge-entry-map)
8. [Verification Checklist](#8-verification-checklist)

---

## 1. Overview & Design Philosophy

### The 14-Point Star

The skill tree is a 3D galaxy with 14 arms radiating from a central origin:

- **6 elemental arms** — axis-aligned, connected directly to origin
- **8 octant arms** — diagonal directions, connected only via existing bridge midpoints (not to origin)

All 14 arms share an identical internal structure. The difference is in their stats and themes.

### Identity Stat Rule

Each elemental arm has 5 **identity stats** — unique to that element, zero overlap between arms.

| Element | Fantasy | Identity Stats |
|---------|---------|---------------|
| **Fire** | Glass Cannon | Physical Damage %, Charged Attack Damage %, Crit Multiplier, Burn Damage %, Ignite Chance |
| **Water** | Arcane Mage | Spell Damage %, Max Mana, Energy Shield, Mana Regen, Freeze Chance |
| **Lightning** | Storm Blitz | Attack Speed %, Move Speed %, Crit Chance, Stamina Regen, Shock Chance |
| **Earth** | Iron Fortress | Max HP %, Armor, HP Regen, Block Chance, KB Resistance |
| **Wind** | Ghost Ranger | Evasion, Accuracy, Projectile Damage %, Jump Force %, Projectile Speed % |
| **Void** | Life Devourer | Life Steal %, True Damage %, DoT Damage %, Mana on Kill, Status Effect Duration % |

**Basic nodes** (tier 1-3) grant ONLY their arm's identity stats. Zero stat leakage between arms.

**Notables** (tier 4) unlock broader variants: penetration, conversion, conditional damage, multiplicative bonuses — stats that are skill-tree-exclusive (gear doesn't provide them).

**Keystones** (tier 6) are build-defining and may use any stat that fits the arm's theme.

### Two Ways Per Elemental Arm

Each elemental arm's 4 clusters divide into two thematic "ways":

1. **Elemental Damage Way** (2 clusters) — themed around the element's damage type, ailment, and scaling
2. **Attribute Stats Way** (2 clusters) — themed around the element's combat playstyle identity

Both ways use identity stats at the basic tier. The difference is in the notables — damage way notables grant elemental damage/penetration/ailment stats, while stats way notables grant broader combat mechanics.

### Octant Stat Access

Each octant arm draws from its 3 neighboring elements' identity stats (15 stats total). Basic nodes use only these 15 stats. Notables blend 2-3 neighbors' stats. Keystones may introduce `[NEW STAT]` mechanics.

### Balance Rules

- Octant nodes have the **same power per-point** as elemental nodes
- The reward for investing in an octant is unique cross-element stat access, not bigger numbers
- Every keystone (all 28) must have a meaningful drawback
- The skill tree complements gear: tree provides multiplicative/conditional/unique mechanics, gear provides flat/additive stats

### Skill Tree vs Gear: Division of Power

| Mechanic | Skill Tree | Gear |
|----------|-----------|------|
| Flat damage/defense | Minimal | Primary source |
| % Increased | Moderate | Moderate |
| % More (multiplicative) | **Exclusive** | Never |
| Per-element penetration | **Primary** (5 of 6) | Fire only |
| Damage conversion | **Primary** | 6 infusion suffixes |
| Conditional damage | **Primary** | Some suffixes |
| Mana-as-damage, mana shield | **Exclusive** | Never |
| Enemy debuffs | **Exclusive** | Never |

---

## 2. Universal Arm Structure

### Node Layout (32 nodes per arm)

```
Origin ─── Entry ─┬─ [Cluster 1] ─── [Cluster 3]
                   │                        │
                   │              [Syn1] [Syn3]
                   │                   \ /
                   │               [Syn Hub]
                   │                   / \
                   │              [Syn2] [Syn4]
                   │                        │
                   └─ [Cluster 2] ─── [Cluster 4]
                                            │
                                    [KS1]  [KS2]
```

- **Entry**: 1 node, connects to origin (elemental) or 3 bridge_2 nodes (octant)
- **Clusters**: 4 × 6 nodes = 24 (5 basic + 1 notable per cluster, diamond pattern)
- **Synergy**: 4 nodes + 1 hub = 5
- **Keystones**: 2 terminal nodes
- **Total per arm**: 32 nodes

### Cluster Diamond Pattern (6 nodes)

```
[_1] → [_2] → [_branch] → [_notable]
                  ├─ [_3]
                  └─ [_4]
```

Clusters 1-2 are "inner" (closer to entry), clusters 3-4 are "outer" (further from entry). Inner cluster notables connect to outer cluster entries. Outer cluster notables connect to synergy nodes.

### Node Point Costs

| Type | Cost | Tier | Count per Arm |
|------|------|------|---------------|
| Basic | 1 | 1-3 | 20 |
| Notable | 2 | 4 | 4 |
| Synergy | 2 | 5 | 4 |
| Synergy Hub | 2 | 5 | 1 |
| Keystone | 3 | 6 | 2 |

### Node ID Convention

Pattern: `{arm}_{cluster}_{suffix}` for cluster nodes, `{arm}_synergy_{n}` for synergy, `{arm}_keystone_{n}` for keystones.

Examples: `fire_ignition_1`, `fire_ignition_notable`, `fire_synergy_1`, `fire_keystone_1`, `havoc_carnage_1`, `havoc_entry`.

### Total Node Counts

| Component | Count |
|-----------|-------|
| Origin | 1 |
| Elemental arms (6 × 32) | 192 |
| Octant arms (8 × 32) | 256 |
| Bridge nodes (12 × 3) | 36 |
| **Total** | **485** |

### New Region Enum Values

8 new entries in `SkillTreeRegion.java`:

| Region | Display Name | Color (proposed) |
|--------|-------------|------------------|
| `HAVOC` | Havoc | `#FF4422` (deep red-orange) |
| `JUGGERNAUT` | Juggernaut | `#CC6633` (bronze) |
| `STRIKER` | Striker | `#FFAA22` (amber) |
| `WARDEN` | Warden | `#88AA44` (olive) |
| `WARLOCK` | Warlock | `#9944CC` (violet) |
| `LICH` | Lich | `#6677AA` (slate blue) |
| `TEMPEST` | Tempest | `#44BBAA` (teal) |
| `SENTINEL` | Sentinel | `#77AA88` (sage green) |

---

## 3. Elemental Arms

### 3.1 FIRE — Glass Cannon

**Identity Stats** (basic nodes use only these):

| Stat | StatType Enum | Attr Per-Point |
|------|--------------|----------------|
| Physical Damage % | `PHYSICAL_DAMAGE_PERCENT` | 0.4/pt |
| Charged Attack Damage % | `CHARGED_ATTACK_DAMAGE_PERCENT` | 0.3/pt |
| Crit Multiplier | `CRITICAL_MULTIPLIER` | 0.6/pt |
| Burn Damage % | `BURN_DAMAGE_PERCENT` | 0.4/pt |
| Ignite Chance | `IGNITE_CHANCE` | 0.1/pt |

#### Clusters

| # | Name | Way | Basic Stats | Notable |
|---|------|-----|-------------|---------|
| 1 | **Ignition** | Damage | Phys Dmg%, Crit Mult | **Searing Strikes** — +10% Fire Damage%, +5% Physical Damage% |
| 2 | **Pyre** | Damage | Burn Dmg%, Ignite Chance | **Pyromaniac** — +15% Burn Duration%, +8% DoT Damage% |
| 3 | **Eruption** | Stats | Charged Atk%, Phys Dmg% | **Executioner** — +15% Execute Damage%, +5% Crit Multiplier |
| 4 | **Inferno** | Stats | Crit Mult, Charged Atk% | **Blazing Fury** — +10% Fire Penetration, +10% Charged Atk Dmg% |

#### Synergy Nodes

| # | Name | Type | Per Count | Bonus | Cap |
|---|------|------|-----------|-------|-----|
| 1 | Strength in Numbers | `ELEMENTAL_COUNT` (FIRE) | 3 | +2% Physical Damage | 20% |
| 2 | Infernal Mastery | `ELEMENTAL_COUNT` (FIRE) | 3 | +3% Fire Damage | 30% |
| 3 | Burning Resolve | `ELEMENTAL_COUNT` (FIRE) | 3 | +0.5% Crit Multiplier | 5% |
| 4 | Charged Momentum | `ELEMENTAL_COUNT` (FIRE) | 3 | +1% Charged Atk Dmg | 10% |

**Synergy Hub**: "Heart of Fire" — +5% Fire Damage, +5% Physical Damage

#### Keystones

**KS1: Inferno Master**
- +40% Physical to Fire Conversion (`PHYSICAL_TO_FIRE_CONVERSION`)
- +15% Fire Damage Multiplier (`FIRE_DAMAGE_MULTIPLIER`)
- **Drawback**: -20% Max HP%

**KS2: Berserker**
- +30% Damage Multiplier (`DAMAGE_MULTIPLIER`)
- +5% Life Steal (`LIFE_STEAL`)
- **Drawback**: +20% Damage Taken (`DAMAGE_TAKEN_PERCENT`)

---

### 3.2 WATER — Arcane Mage

**Identity Stats**:

| Stat | StatType Enum | Attr Per-Point |
|------|--------------|----------------|
| Spell Damage % | `SPELL_DAMAGE_PERCENT` | 0.5/pt |
| Max Mana | `MAX_MANA` | 1.5/pt |
| Energy Shield | `ENERGY_SHIELD` | 2.0/pt |
| Mana Regen | `MANA_REGEN` | 0.15/pt |
| Freeze Chance | `FREEZE_CHANCE` | 0.1/pt |

#### Clusters

| # | Name | Way | Basic Stats | Notable |
|---|------|-----|-------------|---------|
| 1 | **Torrent** | Damage | Spell Dmg%, Max Mana | **Arcane Surge** — +8% Water Damage%, +5% Spell Damage% |
| 2 | **Glacier** | Damage | Freeze Chance, Energy Shield | **Permafrost** — +10% Freeze Chance, +15% Damage vs Frozen% |
| 3 | **Depths** | Stats | Mana Regen, Max Mana | **Wellspring** — +5% Max Mana%, +5 Mana Regen, +10 Energy Shield |
| 4 | **Confluence** | Stats | Spell Dmg%, Energy Shield | **Shatter** — +10 Water Penetration, +5% Spell Damage% |

#### Synergy Nodes

| # | Name | Type | Per Count | Bonus | Cap |
|---|------|------|-----------|-------|-----|
| 1 | Arcane Accumulation | `ELEMENTAL_COUNT` (WATER) | 3 | +2% Spell Damage | 20% |
| 2 | Tidal Force | `ELEMENTAL_COUNT` (WATER) | 3 | +3% Water Damage | 30% |
| 3 | Frost Intensification | `ELEMENTAL_COUNT` (WATER) | 3 | +1% Freeze Chance | 10% |
| 4 | Barrier Growth | `ELEMENTAL_COUNT` (WATER) | 3 | +2 Energy Shield | 20 |

**Synergy Hub**: "Heart of Water" — +5% Water Damage, +5% Spell Damage

#### Keystones

**KS1: Glacial Mastery**
- +10% Freeze Chance (`FREEZE_CHANCE`)
- +30% Damage vs Frozen (`DAMAGE_VS_FROZEN_PERCENT`)
- +10% Water Damage Multiplier (`WATER_DAMAGE_MULTIPLIER`)
- **Drawback**: -15% Non-Crit Damage (`NON_CRIT_DAMAGE_PERCENT`)

**KS2: Arcane Reservoir**
- +20% Damage from Mana (`DAMAGE_FROM_MANA_PERCENT`)
- +20% Max Mana (`MAX_MANA_PERCENT`)
- **Drawback**: +25% Mana Cost (`MANA_COST_PERCENT`)

---

### 3.3 LIGHTNING — Storm Blitz

**Identity Stats**:

| Stat | StatType Enum | Attr Per-Point |
|------|--------------|----------------|
| Attack Speed % | `ATTACK_SPEED_PERCENT` | 0.3/pt |
| Move Speed % | `MOVEMENT_SPEED_PERCENT` | 0.15/pt |
| Crit Chance | `CRITICAL_CHANCE` | 0.1/pt |
| Stamina Regen | `STAMINA_REGEN` | 0.1/pt |
| Shock Chance | `SHOCK_CHANCE` | 0.1/pt |

#### Clusters

| # | Name | Way | Basic Stats | Notable |
|---|------|-----|-------------|---------|
| 1 | **Surge** | Stats | Atk Speed%, Crit Chance | **Lightning Reflexes** — +8% Attack Speed%, +5% Crit Chance |
| 2 | **Tempest** | Damage | Shock Chance, Move Speed% | **Static Field** — +10% Shock Chance, +15% Damage vs Shocked% |
| 3 | **Arc** | Stats | Stamina Regen, Atk Speed% | **Quickening** — +5 Stamina Regen, +8% Atk Speed%, +5% Move Speed% |
| 4 | **Conduit** | Damage | Crit Chance, Move Speed% | **Chain Strike** — +10 Lightning Penetration, +5% Crit Chance |

#### Synergy Nodes

| # | Name | Type | Per Count | Bonus | Cap |
|---|------|------|-----------|-------|-----|
| 1 | Storm Buildup | `ELEMENTAL_COUNT` (LIGHTNING) | 3 | +1% Attack Speed | 10% |
| 2 | Voltage Surge | `ELEMENTAL_COUNT` (LIGHTNING) | 3 | +3% Lightning Damage | 30% |
| 3 | Chain Reaction | `ELEMENTAL_COUNT` (LIGHTNING) | 3 | +0.5% Crit Chance | 5% |
| 4 | Static Charge | `ELEMENTAL_COUNT` (LIGHTNING) | 3 | +1% Shock Chance | 10% |

**Synergy Hub**: "Heart of Lightning" — +5% Lightning Damage, +5% Attack Speed

#### Keystones

**KS1: Thundergod**
- +25% Damage vs Shocked (`DAMAGE_VS_SHOCKED_PERCENT`)
- +15% Shock Chance (`SHOCK_CHANCE`)
- +15% Lightning Damage Multiplier (`LIGHTNING_DAMAGE_MULTIPLIER`)
- **Drawback**: -25% Armor (`ARMOR`)

**KS2: Overcharge**
- +15% Damage to Mana Conversion (`DAMAGE_TO_MANA_CONVERSION`)
- +20% Attack Speed (`ATTACK_SPEED_PERCENT`)
- **Drawback**: -15% Max HP, -10% Move Speed

---

### 3.4 EARTH — Iron Fortress

**Identity Stats**:

| Stat | StatType Enum | Attr Per-Point |
|------|--------------|----------------|
| Max HP % | `MAX_HEALTH_PERCENT` | 0.5/pt |
| Armor | `ARMOR` | 5.0/pt |
| HP Regen | `HEALTH_REGEN` | 0.2/pt |
| Block Chance | `PASSIVE_BLOCK_CHANCE` | 0.2/pt |
| KB Resistance | `KNOCKBACK_RESISTANCE` | 0.3/pt |

#### Clusters

| # | Name | Way | Basic Stats | Notable |
|---|------|-----|-------------|---------|
| 1 | **Bastion** | Stats | Max HP%, Armor | **Ironhide** — +10% Max HP%, +10 Armor, +5% Physical Resistance |
| 2 | **Bedrock** | Stats | HP Regen, KB Resistance | **Unbreakable** — +5% HP Regen, +10% KB Resistance, +5% Health Recovery% |
| 3 | **Rampart** | Damage | Armor, Block Chance | **Stoneguard** — +5% Block Chance, +10 Armor, +5% Shield Effectiveness |
| 4 | **Bulwark** | Damage | Max HP%, KB Resistance | **Mountain's Resolve** — +8% Max HP%, +5% Block Damage Reduction, +5% Earth Damage |

#### Synergy Nodes

| # | Name | Type | Per Count | Bonus | Cap |
|---|------|------|-----------|-------|-----|
| 1 | Bedrock Foundation | `ELEMENTAL_COUNT` (EARTH) | 3 | +2% Max HP | 20% |
| 2 | Mountain's Might | `ELEMENTAL_COUNT` (EARTH) | 3 | +3% Earth Damage | 30% |
| 3 | Shield Attunement | `ELEMENTAL_COUNT` (EARTH) | 3 | +1% Block Chance | 10% |
| 4 | Enduring Fortitude | `ELEMENTAL_COUNT` (EARTH) | 3 | +5 Armor | 50 |

**Synergy Hub**: "Heart of Earth" — +5% Earth Damage, +5% Max HP

#### Keystones

**KS1: Living Fortress**
- +15% Armor (`ARMOR_PERCENT`)
- +10% Block Chance (`PASSIVE_BLOCK_CHANCE`)
- +5% Block Heal (`BLOCK_HEAL_PERCENT`)
- **Drawback**: -20% Damage (`DAMAGE_PERCENT`), -15% Attack Speed

**KS2: Tectonic Bulwark**
- +25% Max HP (`MAX_HEALTH_PERCENT`)
- +10% Physical Resistance (`PHYSICAL_RESISTANCE`)
- +20% Thorns Damage (`THORNS_DAMAGE_PERCENT`)
- **Drawback**: -25% Move Speed, -20% Evasion

---

### 3.5 VOID — Life Devourer

**Identity Stats**:

| Stat | StatType Enum | Attr Per-Point |
|------|--------------|----------------|
| Life Steal % | `LIFE_STEAL` | 0.1/pt |
| True Damage % | `PERCENT_HIT_AS_TRUE_DAMAGE` | 0.05/pt |
| DoT Damage % | `DOT_DAMAGE_PERCENT` | 0.3/pt |
| Mana on Kill | `MANA_ON_KILL` | 0.5/pt |
| Status Effect Duration % | `STATUS_EFFECT_DURATION` | 0.3/pt |

#### Clusters

| # | Name | Way | Basic Stats | Notable |
|---|------|-----|-------------|---------|
| 1 | **Blight** | Stats | Life Steal%, DoT Dmg% | **Sanguine Drain** — +3% Life Steal, +8% DoT Damage%, +3% Life Leech |
| 2 | **Entropy** | Damage | Status Duration%, Mana on Kill | **Lingering Torment** — +10% Status Duration%, +5% Enemy Resistance Reduction |
| 3 | **Shadow** | Stats | True Dmg%, Life Steal% | **Essence Harvest** — +5% True Damage%, +3% Life Leech, +2 Mana on Kill |
| 4 | **Abyss** | Damage | DoT Dmg%, Mana on Kill | **Void Corruption** — +10% DoT Damage%, +10 Void Penetration |

#### Synergy Nodes

| # | Name | Type | Per Count | Bonus | Cap |
|---|------|------|-----------|-------|-----|
| 1 | Dark Accumulation | `ELEMENTAL_COUNT` (VOID) | 3 | +1% Life Steal | 10% |
| 2 | Void Resonance | `ELEMENTAL_COUNT` (VOID) | 3 | +3% Void Damage | 30% |
| 3 | Soul Harvest | `ELEMENTAL_COUNT` (VOID) | 3 | +2% DoT Damage | 20% |
| 4 | Entropy Growth | `ELEMENTAL_COUNT` (VOID) | 3 | +1% Status Duration | 10% |

**Synergy Hub**: "Heart of Void" — +5% Void Damage, +5% DoT Damage

#### Keystones

**KS1: Void Walker**
- +30% Damage to Void Conversion (`DAMAGE_TO_VOID_CONVERSION`)
- +15% Void Damage Multiplier (`VOID_DAMAGE_MULTIPLIER`)
- **Drawback**: -20% Max HP%, -5% Elemental Resistance (all)

**KS2: Parasitic Link**
- +10% Life Leech (`LIFE_LEECH`)
- +15% True Damage (`PERCENT_HIT_AS_TRUE_DAMAGE`)
- **Drawback**: -10% Damage when above 50% HP, +15% Damage Taken

---

### 3.6 WIND — Ghost Ranger

**Identity Stats**:

| Stat | StatType Enum | Attr Per-Point |
|------|--------------|----------------|
| Evasion | `EVASION` | 5.0/pt |
| Accuracy | `ACCURACY` | 3.0/pt |
| Projectile Damage % | `PROJECTILE_DAMAGE_PERCENT` | 0.5/pt |
| Jump Force % | `JUMP_FORCE_PERCENT` | 0.15/pt |
| Projectile Speed % | `PROJECTILE_SPEED_PERCENT` | 0.3/pt |

#### Clusters

| # | Name | Way | Basic Stats | Notable |
|---|------|-----|-------------|---------|
| 1 | **Gale** | Stats | Evasion, Proj Dmg% | **Wind Archer** — +10% Proj Damage%, +10 Evasion, +5% Wind Damage |
| 2 | **Zephyr** | Damage | Accuracy, Proj Speed% | **Eagle Eye** — +10 Accuracy, +10% Proj Speed%, +5% Accuracy% |
| 3 | **Drift** | Stats | Jump Force%, Evasion | **Skybound** — +5% Jump Force%, +10 Evasion, +5% Move Speed% |
| 4 | **Marksman** | Damage | Proj Dmg%, Accuracy | **Sharpshooter** — +8% Proj Damage%, +10 Wind Penetration |

#### Synergy Nodes

| # | Name | Type | Per Count | Bonus | Cap |
|---|------|------|-----------|-------|-----|
| 1 | Wind Convergence | `ELEMENTAL_COUNT` (WIND) | 3 | +2% Projectile Damage | 20% |
| 2 | Gale Force | `ELEMENTAL_COUNT` (WIND) | 3 | +3% Wind Damage | 30% |
| 3 | Eye Training | `ELEMENTAL_COUNT` (WIND) | 3 | +5 Accuracy | 50 |
| 4 | Updraft | `ELEMENTAL_COUNT` (WIND) | 3 | +5 Evasion | 50 |

**Synergy Hub**: "Heart of Wind" — +5% Wind Damage, +5% Projectile Damage

#### Keystones

**KS1: Phantom**
- +25% Evasion (`EVASION_PERCENT`)
- +15% Projectile Damage (`PROJECTILE_DAMAGE_PERCENT`)
- ON_EVADE: +10% Move Speed for 3s (`conditional: ON_EVADE, REFRESH`)
- **Drawback**: -30% Armor, -20% Max HP

**KS2: Sky Piercer**
- +20% Projectile Damage (`PROJECTILE_DAMAGE_PERCENT`)
- +20% Projectile Speed (`PROJECTILE_SPEED_PERCENT`)
- +10 Spell Penetration (`SPELL_PENETRATION`)
- **Drawback**: -25% Melee Damage, -15% Block Chance

---

## 4. Octant Arms

Octant arms sit at the 8 diagonal corners of the 3D skill tree, each at the intersection of 3 elemental arms. Their basic nodes draw from the 15 identity stats of their 3 neighbors. Their synergies use `BRANCH_COUNT` (counting allocated nodes in their own branch), rewarding deep investment in the octant's unique identity. Each octant has 4 completely unique synergy stats with zero overlap across octants.

### Octant Stat Distribution Strategy

Each octant's 4 clusters distribute the 15 available stats (5 per neighbor):

- **Cluster 1** (inner): Primary neighbor's offensive stats — the element most central to the octant's fantasy
- **Cluster 2**: Secondary neighbor's utility/defensive stats
- **Cluster 3**: Tertiary + primary blend
- **Cluster 4** (outer): Signature tri-element blend — the octant's unique identity

### 4.1 HAVOC — Offensive Chaos

**Neighbors**: Fire + Void + Lightning
**Bridges**: `bridge_fire_void_2`, `bridge_fire_lightning_2`, `bridge_lightning_void_2`
**3D Direction**: (+1, +1, +1) / √3

**Available Stats** (15 from 3 neighbors):
- **Fire**: Physical Damage %, Charged Attack Damage %, Crit Multiplier, Burn Damage %, Ignite Chance
- **Void**: Life Steal %, True Damage %, DoT Damage %, Mana on Kill, Status Effect Duration %
- **Lightning**: Attack Speed %, Move Speed %, Crit Chance, Stamina Regen, Shock Chance

#### Clusters

| # | Name | Source | Basic Stats | Notable |
|---|------|--------|-------------|---------|
| 1 | **Carnage** | Fire+Lightning | Crit Multiplier, Physical Dmg%, Ignite Chance | **Killing Spree** — ON_KILL: +15% Atk Speed, +10% Crit Chance for 4s (REFRESH) |
| 2 | **Frenzy** | Lightning | Attack Speed%, Crit Chance, Stamina Regen | **Storm of Blades** — +8% Atk Speed, +10% Crit Mult, +5% Phys Dmg |
| 3 | **Ruin** | Void+Fire | DoT Damage%, Life Steal%, Burn Dmg% | **Death's Touch** — +10% DoT Damage, +5% True Damage, +5% Status Duration |
| 4 | **Mayhem** | Tri-blend | True Damage%, Crit Mult, Shock Chance | **Bloodbath** — +15% Execute Damage%, +3% Life Steal |

#### Synergy Nodes

| # | Name | Type | Per Count | Bonus | Cap |
|---|------|------|-----------|-------|-----|
| 1 | Chaos Resonance | `BRANCH_COUNT` | 3 | +2% Crit Multiplier | 20% |
| 2 | Shattered Defenses | `BRANCH_COUNT` | 3 | +1% Armor Penetration | 10% |
| 3 | Bloodlust | `BRANCH_COUNT` | 3 | +2% Execute Damage | 20% |
| 4 | Rampage Buildup | `BRANCH_COUNT` | 3 | +1% True Damage | 10% |

**Synergy Hub**: "Nexus of Havoc" — +3% Fire Damage, +3% Void Damage, +3% Lightning Damage

#### Keystones

**KS1: Rampage** (Amplifier)
- ON_KILL: +8% Attack Speed, +4% Crit Multiplier, +2% DoT Damage for 6s (STACK, max 5)
- `conditional: ON_KILL, duration: 6.0, stacking: STACK, max_stacks: 5`
- **Drawback**: -15% Max HP%

**KS2: Chain Detonation** `[NEW STAT]`
- `DETONATE_DOT_ON_CRIT`: Crits on DoT-affected targets deal remaining DoT damage instantly as burst damage
- +10% Crit Chance
- **Drawback**: -30% DoT Duration, -10% Status Effect Duration

---

### 4.2 JUGGERNAUT — Unkillable Bruiser

**Neighbors**: Fire + Void + Earth
**Bridges**: `bridge_fire_void_2`, `bridge_earth_fire_2`, `bridge_earth_void_2`
**3D Direction**: (+1, +1, -1) / √3

**Available Stats**:
- **Fire**: Physical Damage %, Charged Attack Damage %, Crit Multiplier, Burn Damage %, Ignite Chance
- **Void**: Life Steal %, True Damage %, DoT Damage %, Mana on Kill, Status Effect Duration %
- **Earth**: Max HP %, Armor, HP Regen, Block Chance, KB Resistance

#### Clusters

| # | Name | Source | Basic Stats | Notable |
|---|------|--------|-------------|---------|
| 1 | **Conquest** | Fire+Earth | Physical Dmg%, Max HP%, Armor | **Iron Reaver** — +10% Physical Dmg, +5% Max HP, +5 Armor |
| 2 | **Dominion** | Earth | Block Chance, HP Regen, KB Resistance | **Blood Guard** — +5% Block Chance, +3% Life Steal, +3% HP Regen |
| 3 | **Bloodforge** | Fire+Void | Burn Dmg%, Life Steal%, Crit Mult | **Relentless** — +10% Charged Atk Dmg, +5% True Damage |
| 4 | **Tyrant** | Tri-blend | Charged Atk%, True Dmg%, Max HP% | **Indomitable** — +8% Max HP, +5 Armor, +5% Burn Damage |

#### Synergy Nodes

| # | Name | Type | Per Count | Bonus | Cap |
|---|------|------|-----------|-------|-----|
| 1 | Unbreakable Wall | `BRANCH_COUNT` | 3 | +2% Max Health | 20% |
| 2 | Blood Pact | `BRANCH_COUNT` | 3 | +1% Life Steal | 10% |
| 3 | Iron Thorns | `BRANCH_COUNT` | 3 | +2% Thorns Damage | 20% |
| 4 | Battle Recovery | `BRANCH_COUNT` | 3 | +0.2 Health Regen | 2.0 |

**Synergy Hub**: "Nexus of Juggernaut" — +3% Fire Damage, +3% Void Damage, +3% Earth Damage

#### Keystones

**KS1: Blood Fortress** (Amplifier)
- +10% Block Chance (`PASSIVE_BLOCK_CHANCE`)
- While above 75% HP: +10% Life Steal (`conditional: HIGH_LIFE, threshold: 0.75`)
- `conditional: HIGH_LIFE, effects: [{stat: LIFE_STEAL, value: 10.0}]`
- **Drawback**: -25% Attack Speed

**KS2: Colossus** `[NEW STAT]`
- `HP_SCALING_DAMAGE`: +0.5% Physical Damage per 100 Max HP
- +10% Max HP
- **Drawback**: -25% Move Speed, -50% Evasion

---

### 4.3 STRIKER — Speed Assassin

**Neighbors**: Fire + Wind + Lightning
**Bridges**: `bridge_fire_lightning_2`, `bridge_fire_wind_2`, `bridge_lightning_wind_2`
**3D Direction**: (+1, -1, +1) / √3

**Available Stats**:
- **Fire**: Physical Damage %, Charged Attack Damage %, Crit Multiplier, Burn Damage %, Ignite Chance
- **Wind**: Evasion, Accuracy, Projectile Damage %, Jump Force %, Projectile Speed %
- **Lightning**: Attack Speed %, Move Speed %, Crit Chance, Stamina Regen, Shock Chance

#### Clusters

| # | Name | Source | Basic Stats | Notable |
|---|------|--------|-------------|---------|
| 1 | **Quicksilver** | Lightning | Attack Speed%, Crit Chance, Move Speed% | **Quick Draw** — +8% Atk Speed, +5% Proj Dmg, +5% Move Speed |
| 2 | **Precision** | Wind+Fire | Accuracy, Evasion, Crit Mult | **Vital Strike** — +10% Crit Mult, +5% Crit Chance, +5 Accuracy |
| 3 | **Ambush** | Fire+Lightning | Physical Dmg%, Ignite Chance, Atk Speed% | **Relentless Assault** — +5% Atk Speed, +5% Physical Dmg, +5% Charged Atk Dmg |
| 4 | **Flurry** | Tri-blend | Proj Dmg%, Crit Chance, Evasion | **Feint** — ON_EVADE: +15% Crit Chance for 3s (REFRESH). +10 Evasion |

#### Synergy Nodes

| # | Name | Type | Per Count | Bonus | Cap |
|---|------|------|-----------|-------|-----|
| 1 | Quicksilver Reflexes | `BRANCH_COUNT` | 3 | +1% Attack Speed | 10% |
| 2 | Precision Training | `BRANCH_COUNT` | 3 | +0.5% Crit Chance | 5% |
| 3 | Blitz Protocol | `BRANCH_COUNT` | 3 | +2% Movement Speed | 20% |
| 4 | Shadow Step | `BRANCH_COUNT` | 3 | +1% Dodge Chance | 10% |

**Synergy Hub**: "Nexus of Striker" — +3% Fire Damage, +3% Wind Damage, +3% Lightning Damage

#### Keystones

**KS1: Blade Dance** (Amplifier)
- ON_EVADE: next attack within 1.5s gains +95% Crit Chance (near-guaranteed crit), +20% Crit Multiplier
- `conditional: ON_EVADE, duration: 1.5, stacking: REFRESH`
- +5% Evasion%
- **Drawback**: -25% Armor, -15% Max HP

**KS2: Momentum** `[NEW STAT]`
- `CONSECUTIVE_HIT_BONUS`: Each consecutive hit within 2s grants +3% damage (STACK, max 10 = +30%)
- +5% Attack Speed
- **Drawback**: -20% Max HP%

---

### 4.4 WARDEN — Physical Ranger

**Neighbors**: Fire + Wind + Earth
**Bridges**: `bridge_earth_fire_2`, `bridge_fire_wind_2`, `bridge_earth_wind_2`
**3D Direction**: (+1, -1, -1) / √3

**Available Stats**:
- **Fire**: Physical Damage %, Charged Attack Damage %, Crit Multiplier, Burn Damage %, Ignite Chance
- **Wind**: Evasion, Accuracy, Projectile Damage %, Jump Force %, Projectile Speed %
- **Earth**: Max HP %, Armor, HP Regen, Block Chance, KB Resistance

#### Clusters

| # | Name | Source | Basic Stats | Notable |
|---|------|--------|-------------|---------|
| 1 | **Garrison** | Earth | Armor, Block Chance, Max HP% | **Shield Wall** — +8% Block Chance, +5% Max HP, +5% KB Resistance |
| 2 | **Outrider** | Wind | Proj Dmg%, Accuracy, Proj Speed% | **Iron Rain** — +10% Proj Dmg, +5 Armor, +5% Proj Speed |
| 3 | **Ironclad** | Fire+Earth | Physical Dmg%, Armor, Crit Mult | **Power Shot** — +10% Charged Atk Dmg, +5% Crit Mult, +5 Accuracy |
| 4 | **Palisade** | Tri-blend | Block Chance, Proj Dmg%, Charged Atk% | **Fortified Position** — ON_BLOCK: +10% Proj Dmg for 3s (REFRESH). +5% Block Chance |

#### Synergy Nodes

| # | Name | Type | Per Count | Bonus | Cap |
|---|------|------|-----------|-------|-----|
| 1 | Fortified Range | `BRANCH_COUNT` | 3 | +2% Projectile Damage | 20% |
| 2 | Power Draw | `BRANCH_COUNT` | 3 | +2% Charged Attack Damage | 20% |
| 3 | Eagle Eye | `BRANCH_COUNT` | 3 | +5 Accuracy | 50 |
| 4 | Immovable Stance | `BRANCH_COUNT` | 3 | +1% Knockback Resistance | 10% |

**Synergy Hub**: "Nexus of Warden" — +3% Fire Damage, +3% Wind Damage, +3% Earth Damage

#### Keystones

**KS1: Earthen Volley** (Amplifier)
- Projectile hits grant +2% Armor for 5s (STACK, max 10 = +20%)
- `conditional: ON_PROJECTILE_HIT, duration: 5.0, stacking: STACK, max_stacks: 10`
- +15% Projectile Damage
- **Drawback**: -20% Spell Damage, -15% Move Speed

**KS2: Stalwart Counter** `[NEW STAT]`
- `BLOCK_COUNTER_DAMAGE`: Successful blocks deal 150% of blocked damage as Physical to attacker
- +10% Block Chance
- **Drawback**: -20% Spell Damage, -20% Attack Speed

---

### 4.5 WARLOCK — Dark Caster

**Neighbors**: Water + Void + Lightning
**Bridges**: `bridge_lightning_water_2`, `bridge_lightning_void_2`, `bridge_water_void_2`
**3D Direction**: (-1, +1, +1) / √3

**Available Stats**:
- **Water**: Spell Damage %, Max Mana, Energy Shield, Mana Regen, Freeze Chance
- **Void**: Life Steal %, True Damage %, DoT Damage %, Mana on Kill, Status Effect Duration %
- **Lightning**: Attack Speed %, Move Speed %, Crit Chance, Stamina Regen, Shock Chance

#### Clusters

| # | Name | Source | Basic Stats | Notable |
|---|------|--------|-------------|---------|
| 1 | **Hex** | Water+Void | Spell Dmg%, DoT Dmg%, Mana on Kill | **Dark Arcana** — +10% Spell Dmg, +5% DoT Dmg, +2 Mana on Kill |
| 2 | **Ritual** | Water | Max Mana, Mana Regen, Energy Shield | **Mind Drain** — +5 Mana Regen, +3% Life Steal, +5 Energy Shield |
| 3 | **Malice** | Void+Lightning | True Dmg%, Crit Chance, Shock Chance | **Eldritch Blast** — +5% True Dmg, +5% Crit Chance, +5% Shock Chance |
| 4 | **Damnation** | Tri-blend | Spell Dmg%, Status Duration%, Crit Chance | **Cursed Knowledge** — +8% Spell Dmg, +5% Status Duration, +5 Max Mana |

#### Synergy Nodes

| # | Name | Type | Per Count | Bonus | Cap |
|---|------|------|-----------|-------|-----|
| 1 | Arcane Corruption | `BRANCH_COUNT` | 3 | +2% Spell Damage | 20% |
| 2 | Eldritch Power | `BRANCH_COUNT` | 3 | +3 Max Mana | 30 |
| 3 | Hex Mastery | `BRANCH_COUNT` | 3 | +1% Status Effect Chance | 10% |
| 4 | Mind Siphon | `BRANCH_COUNT` | 3 | +1% Mana Leech | 10% |

**Synergy Hub**: "Nexus of Warlock" — +3% Water Damage, +3% Void Damage, +3% Lightning Damage

#### Keystones

**KS1: Soul Siphon** (Amplifier)
- +5% Life Leech (`LIFE_LEECH`) — applies to all damage including spells
- +5% Mana Leech (`MANA_LEECH`)
- +10% Crit Chance (`CRITICAL_CHANCE`)
- **Drawback**: -20% Max HP, -15% Armor

**KS2: Arcane Overload** `[NEW STAT]`
- `SPELL_ECHO_CHANCE`: X% chance that spell/magic damage repeats for 50% as bonus Void damage
- +10% Spell Damage
- **Drawback**: -10% Max Mana

---

### 4.6 LICH — Tanky Dark Mage

**Neighbors**: Water + Void + Earth
**Bridges**: `bridge_water_earth_2`, `bridge_water_void_2`, `bridge_earth_void_2`
**3D Direction**: (-1, +1, -1) / √3

**Available Stats**:
- **Water**: Spell Damage %, Max Mana, Energy Shield, Mana Regen, Freeze Chance
- **Void**: Life Steal %, True Damage %, DoT Damage %, Mana on Kill, Status Effect Duration %
- **Earth**: Max HP %, Armor, HP Regen, Block Chance, KB Resistance

#### Clusters

| # | Name | Source | Basic Stats | Notable |
|---|------|--------|-------------|---------|
| 1 | **Grasp** | Water+Void | Spell Dmg%, DoT Dmg%, Energy Shield | **Death's Embrace** — +8% DoT Dmg, +5% Spell Dmg, +5 Energy Shield |
| 2 | **Crypt** | Earth | Max HP%, Armor, HP Regen | **Necrotic Armor** — +5% Max HP, +5 Armor, +3% HP Regen |
| 3 | **Requiem** | Void+Earth | Life Steal%, Block Chance, Status Duration% | **Soul Anchor** — +3% Life Steal, +5% Block Chance, +5% Status Duration |
| 4 | **Decay** | Tri-blend | DoT Dmg%, Max HP%, Mana Regen | **Withering Presence** — +8% DoT Dmg, +5% Max HP, +2 Mana on Kill |

#### Synergy Nodes

| # | Name | Type | Per Count | Bonus | Cap |
|---|------|------|-----------|-------|-----|
| 1 | Necrotic Bond | `BRANCH_COUNT` | 3 | +5 Energy Shield | 50 |
| 2 | Plague Growth | `BRANCH_COUNT` | 3 | +2% DoT Damage | 20% |
| 3 | Soul Barrier | `BRANCH_COUNT` | 3 | +1% Mana as Damage Buffer | 10% |
| 4 | Lingering Torment | `BRANCH_COUNT` | 3 | +1% Status Effect Duration | 10% |

**Synergy Hub**: "Nexus of Lich" — +3% Water Damage, +3% Void Damage, +3% Earth Damage

#### Keystones

**KS1: Plague Resilience** (Amplifier)
- +3% Elemental Resistance per unique ailment on enemies you've inflicted (max 5 = +15%)
- `conditional: ON_INFLICT_STATUS, stacking: STACK, max_stacks: 5`
- `effects: [{stat: ELEMENTAL_RESISTANCE, value: 3.0}]`
- +15% Max HP
- **Drawback**: -20% Attack Speed, -10% Crit Chance

**KS2: Undying Shell** `[NEW STAT]`
- `SHIELD_REGEN_ON_DOT`: DoTs you inflict restore Energy Shield equal to 5% of their damage per tick
- +20% Energy Shield
- **Drawback**: -20% Evasion, -15% Move Speed

---

### 4.7 TEMPEST — Mobile Mage

**Neighbors**: Water + Wind + Lightning
**Bridges**: `bridge_lightning_water_2`, `bridge_lightning_wind_2`, `bridge_water_wind_2`
**3D Direction**: (-1, -1, +1) / √3

**Available Stats**:
- **Water**: Spell Damage %, Max Mana, Energy Shield, Mana Regen, Freeze Chance
- **Wind**: Evasion, Accuracy, Projectile Damage %, Jump Force %, Projectile Speed %
- **Lightning**: Attack Speed %, Move Speed %, Crit Chance, Stamina Regen, Shock Chance

#### Clusters

| # | Name | Source | Basic Stats | Notable |
|---|------|--------|-------------|---------|
| 1 | **Squall** | Water+Lightning | Spell Dmg%, Atk Speed%, Crit Chance | **Swift Cast** — +5% Atk Speed, +5% Crit Chance, +5% Move Speed |
| 2 | **Tailwind** | Wind | Proj Dmg%, Proj Speed%, Move Speed% | **Arcane Gust** — +8% Spell Dmg, +5% Proj Dmg, +5% Proj Speed |
| 3 | **Cyclone** | Lightning+Wind | Move Speed%, Evasion, Shock Chance | **Windborne** — +10 Evasion, +5% Move Speed, +5% Shock Chance |
| 4 | **Maelstrom** | Tri-blend | Spell Dmg%, Proj Dmg%, Atk Speed% | **Eye of the Storm** — +8% Spell Dmg, +5% Atk Speed, +5 Accuracy |

#### Synergy Nodes

| # | Name | Type | Per Count | Bonus | Cap |
|---|------|------|-----------|-------|-----|
| 1 | Storm Surge | `BRANCH_COUNT` | 3 | +1% All Damage | 10% |
| 2 | Static Buildup | `BRANCH_COUNT` | 3 | +1% Shock Chance | 10% |
| 3 | Arcane Flow | `BRANCH_COUNT` | 3 | +2 Mana Regen | 20 |
| 4 | Wind Runner | `BRANCH_COUNT` | 3 | +5 Stamina Regen | 50 |

**Synergy Hub**: "Nexus of Tempest" — +3% Water Damage, +3% Wind Damage, +3% Lightning Damage

#### Keystones

**KS1: Arcane Velocity** `[NEW STAT]`
- `ATK_SPEED_TO_SPELL_POWER`: Spell Damage +1% per 2% Attack Speed bonus
- +10% Attack Speed
- **Drawback**: -20% Armor, -10% Block Chance

**KS2: Storm Runner** `[NEW STAT]`
- `SPEED_TO_SPELL_POWER`: 30% of total Movement Speed bonus is added as Spell Damage %
- +10% Move Speed
- **Drawback**: -25% Max HP%

---

### 4.8 SENTINEL — Defensive Support

**Neighbors**: Water + Wind + Earth
**Bridges**: `bridge_water_earth_2`, `bridge_water_wind_2`, `bridge_earth_wind_2`
**3D Direction**: (-1, -1, -1) / √3

**Available Stats**:
- **Water**: Spell Damage %, Max Mana, Energy Shield, Mana Regen, Freeze Chance
- **Wind**: Evasion, Accuracy, Projectile Damage %, Jump Force %, Projectile Speed %
- **Earth**: Max HP %, Armor, HP Regen, Block Chance, KB Resistance

#### Clusters

| # | Name | Source | Basic Stats | Notable |
|---|------|--------|-------------|---------|
| 1 | **Aegis** | Earth+Water | Block Chance, Energy Shield, Max HP% | **Guardian's Ward** — +5% Block Chance, +10 Energy Shield, +5% Max HP |
| 2 | **Vigilance** | Wind | Evasion, Accuracy, Proj Speed% | **Keen Senses** — +10 Evasion, +10 Accuracy, +5% Proj Speed |
| 3 | **Restoration** | Water+Earth | HP Regen, Mana Regen, Armor | **Restorative Aura** — +3% HP Regen, +3 Mana Regen, +5 Armor |
| 4 | **Haven** | Tri-blend | Block Chance, Evasion, Max HP% | **Stalwart Defense** — +5% Block Chance, +8 Evasion, +3% Max HP |

#### Synergy Nodes

| # | Name | Type | Per Count | Bonus | Cap |
|---|------|------|-----------|-------|-----|
| 1 | Guardian's Resolve | `BRANCH_COUNT` | 3 | +1% Block Chance | 10% |
| 2 | Watchful Eye | `BRANCH_COUNT` | 3 | +5 Evasion | 50 |
| 3 | Warding Aura | `BRANCH_COUNT` | 3 | +1% Elemental Resistance | 10% |
| 4 | Stalwart Focus | `BRANCH_COUNT` | 3 | +1% Crit Nullify Chance | 10% |

**Synergy Hub**: "Nexus of Sentinel" — +3% Water Damage, +3% Wind Damage, +3% Earth Damage

#### Keystones

**KS1: Fortress Aura** `[NEW STAT]`
- `EVASION_TO_ARMOR`: Convert X% of Evasion into bonus Armor (Tier 1 derivation — computed in StatsCombiner)
- +15% Block Chance
- **Drawback**: -20% Damage Multiplier

**KS2: Adaptive Guard** `[NEW STAT]`
- `IMMUNITY_ON_AILMENT`: When hit by an ailment (burn/freeze/shock/poison), gain 80% resistance to that element for 5s
- +10% HP Regen
- **Drawback**: -15% All Elemental Damage

---

## 5. NEW STAT Proposals

Every stat below is required for an octant keystone. Each entry specifies the StatType enum name, the source keystone, combat pipeline integration point, and formula.

### 5.1 `DETONATE_DOT_ON_CRIT`

**Source**: Havoc KS2 (Chain Detonation)
**Description**: Critical hits against DoT-affected targets instantly deal all remaining DoT damage as burst damage, then clear the DoTs.

**Combat Integration**:
- Hook into `RPGDamageCalculator` after the crit roll (step 9 in damage pipeline)
- If the hit is a crit AND the target has active DoTs AND this stat > 0:
  1. Sum remaining damage across all active DoTs on target
  2. Deal that sum as instant Void damage (bypasses armor, subject to resistance)
  3. Clear all active DoTs from target
- This stat is binary (presence = enabled), but could use the value as a % chance for partial implementation

**Formula**: `burstDamage = sum(remainingDoTDamage for each active DoT)`
**Complexity**: Medium — requires access to ailment tracking per-entity

---

### 5.2 `HP_SCALING_DAMAGE`

**Source**: Juggernaut KS2 (Colossus)
**Description**: Grants bonus Physical Damage % proportional to Max HP.

**Combat Integration**:
- Hook into `ComputedStats` final computation, after all Max HP sources are resolved
- Add `(finalMaxHP / 100) * statValue` to `physicalDamagePercent`

**Formula**: `bonusPhysDmgPercent = maxHP / 100 * statValue`
**Example**: 300 Max HP × 0.5 stat value = +1.5% Physical Damage. At 1000 HP = +5%.
**Complexity**: Low — pure stat computation, no event hooks

---

### 5.3 `CONSECUTIVE_HIT_BONUS`

**Source**: Striker KS2 (Momentum)
**Description**: Each consecutive hit within a time window grants stacking damage bonus. Timer resets on each hit. Stacks fall off entirely when the window expires.

**Combat Integration**:
- New tracking component on player entity: `consecutiveHitCount`, `lastHitTimestamp`
- On dealing damage: if `(now - lastHitTimestamp) < window`, increment count (up to max_stacks); else reset to 1
- Apply `count * statValue` as additive damage % in step 5 of damage pipeline

**Formula**: `bonusDamagePercent = min(consecutiveHits * statValue, statValue * maxStacks)`
**Parameters**: `window = 2.0s`, `maxStacks = 10` (hardcoded or from stat value)
**Example**: 5 consecutive hits × 3% = +15% damage on 6th hit
**Complexity**: Medium — requires per-player hit tracking component

---

### 5.4 `BLOCK_COUNTER_DAMAGE`

**Source**: Warden KS2 (Stalwart Counter)
**Description**: When the player successfully blocks an attack, deal a percentage of the blocked damage back to the attacker as Physical damage.

**Combat Integration**:
- Hook into block handler (after `isBlocked` check in damage pipeline)
- On successful block: `counterDamage = originalDamage * (statValue / 100)`
- Deal `counterDamage` as Physical damage to the attacker (true damage variant, ignoring attacker's block/evasion)

**Formula**: `counterDamage = blockedDamage * statValue / 100`
**Example**: Block a 50 damage hit with 150% counter = deal 75 Physical damage back
**Complexity**: Low — simple hook in existing block handler

---

### 5.5 `SPELL_ECHO_CHANCE`

**Source**: Warlock KS2 (Arcane Overload)
**Description**: Each spell/magic damage hit has an X% chance to repeat as 50% bonus Void damage. The echo triggers after the main damage calculation and is added to the total damage dealt.

**Combat Integration**:
- **Tier 2**: Stored in `OffensiveStats.spellEchoChance`, consumed by `RPGDamageSystem`
- Hook location: after DETONATE_DOT_ON_CRIT in damage pipeline (Phase 5)
- Only triggers on `DamageType.MAGIC` damage
- On proc: add `rpgDamage * 0.5` to total damage

**Formula**: `echoDamage = rpgDamage * 0.5` (when RNG roll succeeds)
**Trigger**: `ThreadLocalRandom.current().nextFloat() * 100 < spellEchoChance`
**Example**: 15% chance, 200 magic damage → on proc, +100 bonus damage = 300 total
**Complexity**: Low — simple RNG check + flat damage add, same pattern as DETONATE_DOT_ON_CRIT

---

### 5.6 `SHIELD_REGEN_ON_DOT`

**Source**: Lich KS2 (Undying Shell)
**Description**: DoT effects the player has inflicted restore the player's Energy Shield proportional to the DoT tick damage.

**Combat Integration**:
- Hook into DoT tick handler (where ailment damage is applied per tick)
- For each DoT tick on an enemy inflicted by this player:
  - Restore `tickDamage * (statValue / 100)` to player's Energy Shield
- Energy Shield cannot exceed its maximum

**Formula**: `shieldRestored = dotTickDamage * statValue / 100`
**Example**: Burn tick deals 20 damage × 5% = restore 1 Energy Shield per tick. With multiple DoTs on multiple enemies, this adds up.
**Complexity**: Medium — requires tracking DoT ownership back to source player

---

### 5.7 `SPEED_TO_SPELL_POWER`

**Source**: Tempest KS2 (Storm Runner)
**Description**: Converts a percentage of the player's total Movement Speed bonus into Spell Damage %.

**Combat Integration**:
- Hook into `ComputedStats` final computation, after all movement speed sources are resolved
- `bonusSpellDmg = totalMoveSpeedBonus * (statValue / 100)`
- Must use the BONUS portion only (not base speed), to prevent double-dipping

**Formula**: `bonusSpellDmgPercent = moveSpeedBonusPercent * statValue / 100`
**Example**: 40% move speed bonus × 30% conversion = +12% Spell Damage
**Complexity**: Low — pure stat computation in ComputedStats

---

### 5.8 `ATK_SPEED_TO_SPELL_POWER`

**Source**: Tempest KS1 (Arcane Velocity)
**Description**: Converts a portion of Attack Speed bonus into Spell Damage %. Every 2% Attack Speed grants 1% Spell Damage.

**Combat Integration**:
- Same hook as `SPEED_TO_SPELL_POWER` — in `ComputedStats` final computation
- `bonusSpellDmg = totalAtkSpeedBonus * (statValue / 100)`
- The keystone stat value represents the conversion ratio (e.g., 50 = 1% spell per 2% atk speed)

**Formula**: `bonusSpellDmgPercent = atkSpeedBonusPercent * statValue / 100`
**Example**: 30% attack speed bonus × 50% conversion = +15% Spell Damage
**Complexity**: Low — pure stat computation in ComputedStats

---

### 5.9 `IMMUNITY_ON_AILMENT`

**Source**: Sentinel KS2 (Adaptive Guard)
**Description**: When the player is hit by an ailment (burn, freeze, shock, poison), gain temporary resistance to the corresponding element.

**Combat Integration**:
- Hook into ailment application handler (where ailments are applied to the player)
- On ailment applied to player: apply a timed buff granting `statValue%` resistance to the ailment's element
- Duration: 5s (hardcoded or from a sub-field)
- Each element's resistance is tracked independently — can have multiple active
- New ailments refresh the duration for that element

**Formula**: `tempResistance = statValue` (added as flat resistance for the duration)
**Element Mapping**: Burn → Fire Resistance, Freeze → Water Resistance, Shock → Lightning Resistance, Poison → Void Resistance
**Example**: Hit by freeze → gain +80% Water Resistance for 5s
**Complexity**: Medium — requires conditional buff system (already exists via ConditionalConfig)

---

### 5.10 `EVASION_TO_ARMOR`

**Source**: Sentinel KS1 (Fortress Aura)
**Description**: Convert X% of the player's Evasion into bonus Armor. The conversion is non-destructive — the original evasion value is preserved.

**Combat Integration**:
- **Tier 1**: Derived in `StatsCombiner.combine()` as a post-processing step
- After all standard modifiers are applied, reads the post-processed evasion value
- Adds `evasion * (statValue / 100)` as bonus armor
- No combat hooks needed — purely a stat derivation

**Formula**: `bonusArmor = evasion * statValue / 100`
**Example**: 300 Evasion × 30% = 90 bonus Armor (added to existing armor)
**Complexity**: Low — same pattern as HP_SCALING_DAMAGE and SPEED_TO_SPELL_POWER (Tier 1 derivations)

---

### New Conditional Triggers Required

Two existing trigger types may need extensions:

| Trigger | Status | Description |
|---------|--------|-------------|
| `HIGH_LIFE` | **New** | Active while HP is above a threshold (default 75%). Complement to existing `LOW_LIFE` (below 35%). |
| `ON_PROJECTILE_HIT` | **New** | Fires when the player deals damage with a projectile attack. Subset of a hypothetical ON_HIT. |

---

## 6. 3D Positioning Principles

### Direction Vectors

Elemental arms extend along the 6 axis-aligned directions. Octant arms extend along the 8 diagonal directions (cube corners):

| Arm | Direction (x, y, z) | Normalized | World Meaning |
|-----|---------------------|------------|---------------|
| **Fire** | (+1, 0, 0) | (1, 0, 0) | East |
| **Water** | (-1, 0, 0) | (-1, 0, 0) | West |
| **Lightning** | (0, 0, +1) | (0, 0, 1) | South |
| **Earth** | (0, 0, -1) | (0, 0, -1) | North |
| **Void** | (0, +1, 0) | (0, 1, 0) | Up |
| **Wind** | (0, -1, 0) | (0, -1, 0) | Down |
| **Havoc** | (+1, +1, +1) | (0.577, 0.577, 0.577) | Up-East-South |
| **Juggernaut** | (+1, +1, -1) | (0.577, 0.577, -0.577) | Up-East-North |
| **Striker** | (+1, -1, +1) | (0.577, -0.577, 0.577) | Down-East-South |
| **Warden** | (+1, -1, -1) | (0.577, -0.577, -0.577) | Down-East-North |
| **Warlock** | (-1, +1, +1) | (-0.577, 0.577, 0.577) | Up-West-South |
| **Lich** | (-1, +1, -1) | (-0.577, 0.577, -0.577) | Up-West-North |
| **Tempest** | (-1, -1, +1) | (-0.577, -0.577, 0.577) | Down-West-South |
| **Sentinel** | (-1, -1, -1) | (-0.577, -0.577, -0.577) | Down-West-North |

### Starting Offset

Elemental arms start at `entryFromOrigin = 70` layout units from origin. Octant arms start at **50% of elemental arm length** from origin:

- Elemental arm extent: entry (70) to keystones (~400) layout units
- Octant arm entry: `~200` layout units from origin along diagonal direction
- This places octant entries approximately at the same distance as bridge midpoints

### Arm Length and Cluster Spacing

Octant arms use the **same internal spacing** as elemental arms:

| Parameter | Value | Notes |
|-----------|-------|-------|
| `clusterStart` | 100 | From arm entry to first cluster |
| `clusterSpacing` | 95 | Between cluster centers |
| `nodeSpacing` | 30 | Between nodes within cluster |
| `branchOffset` | 25 | Perpendicular alternation |
| `synergyExtension` | 30 | Beyond last cluster notable |

### Perpendicular Axes

For diagonal directions, `LayoutMath.Direction3D.getPerpendicularDirections()` computes two orthogonal vectors automatically. For reference:

| Primary Direction | Perpendicular 1 | Perpendicular 2 |
|-------------------|-----------------|-----------------|
| (1, 1, 1) / √3 | (-1, 0, 1) / √2 | (-1, 2, -1) / √6 |
| (1, 1, -1) / √3 | (1, 0, 1) / √2 | (1, -2, -1) / √6 |
| etc. | (computed via cross products) | |

The exact perpendicular directions are implementation details handled by `LayoutMath`. The design doc only needs to specify the primary direction vector — the layout generator handles the rest.

### Implementation Notes

1. Add 8 new `Direction3D` entries in `ProceduralLayoutConfig` for octant directions
2. Add `octantStartOffset` parameter (default 200) to `ProceduralLayoutConfig`
3. In `ProceduralLayoutGenerator.generateLayout()`, add octant arm generation loop after elemental arms
4. Octant entry nodes connect to `bridge_X_Y_2` nodes instead of origin — handle in connection resolution

---

## 7. Bridge Entry Map

### Connection Table

Each octant arm's entry node connects to the middle node (`bridge_X_Y_2`) of 3 existing bridge paths. The bridge_2 nodes gain additional connections to octant entries.

| Octant | Bridge 1 | Bridge 2 | Bridge 3 |
|--------|----------|----------|----------|
| **Havoc** | `bridge_fire_void_2` | `bridge_fire_lightning_2` | `bridge_lightning_void_2` |
| **Juggernaut** | `bridge_fire_void_2` | `bridge_earth_fire_2` | `bridge_earth_void_2` |
| **Striker** | `bridge_fire_lightning_2` | `bridge_fire_wind_2` | `bridge_lightning_wind_2` |
| **Warden** | `bridge_earth_fire_2` | `bridge_fire_wind_2` | `bridge_earth_wind_2` |
| **Warlock** | `bridge_lightning_water_2` | `bridge_lightning_void_2` | `bridge_water_void_2` |
| **Lich** | `bridge_water_earth_2` | `bridge_water_void_2` | `bridge_earth_void_2` |
| **Tempest** | `bridge_lightning_water_2` | `bridge_lightning_wind_2` | `bridge_water_wind_2` |
| **Sentinel** | `bridge_water_earth_2` | `bridge_water_wind_2` | `bridge_earth_wind_2` |

### Adjacency Verification

Each of the 12 bridge paths appears in exactly 2 octants:

| Bridge Path | Octant A | Octant B |
|-------------|----------|----------|
| `bridge_fire_void` | Havoc | Juggernaut |
| `bridge_fire_lightning` | Havoc | Striker |
| `bridge_fire_wind` | Striker | Warden |
| `bridge_earth_fire` | Juggernaut | Warden |
| `bridge_lightning_void` | Havoc | Warlock |
| `bridge_water_void` | Warlock | Lich |
| `bridge_earth_void` | Juggernaut | Lich |
| `bridge_lightning_water` | Warlock | Tempest |
| `bridge_lightning_wind` | Striker | Tempest |
| `bridge_water_earth` | Lich | Sentinel |
| `bridge_water_wind` | Tempest | Sentinel |
| `bridge_earth_wind` | Warden | Sentinel |

**Checksum**: 12 bridges × 2 = 24 appearances = 8 octants × 3 bridges ✓

### Bridge Node Connection Changes

Each `bridge_X_Y_2` node currently has 2 connections (`bridge_X_Y_1` and `bridge_X_Y_3`). After adding octant entries, each gains 2 additional connections (one per octant that uses it), going from 2 to 4 connections.

Example for `bridge_fire_lightning_2`:
- Current: `bridge_fire_lightning_1`, `bridge_fire_lightning_3`
- After: `bridge_fire_lightning_1`, `bridge_fire_lightning_3`, `havoc_entry`, `striker_entry`

---

## 8. Verification Checklist

### Identity Stat Compliance
- [ ] Every elemental arm basic node uses ONLY stats from its element's 5 identity stats
- [ ] No elemental basic node uses a stat from another element's identity set
- [ ] Every octant arm basic node uses ONLY stats from its 3 neighbors' identity sets (15 stats total)
- [ ] No octant basic node uses a stat from a non-adjacent element

### Structural Completeness
- [ ] All 6 elemental arms have: 4 cluster tables + 4 notables + 2 keystones + 4 synergies + 1 hub
- [ ] All 8 octant arms have: 4 cluster tables + 4 notables + 2 keystones + 4 synergies + 1 hub
- [ ] All 8 octants specify their 3 bridge entry points
- [ ] All 28 keystones have meaningful drawbacks

### Stat Traceability
- [ ] Every stat in this doc traces to an existing `StatType` enum value OR is marked `[NEW STAT]`
- [ ] All `[NEW STAT]` entries have: enum name, description, combat integration point, formula
- [ ] No duplicate stat assignments between basic node tiers across elemental arms

### Balance Sanity
- [ ] Octant basic node power matches elemental basic node power (same stat values per point)
- [ ] Octant synergies (BRANCH_COUNT per 3) produce similar total bonuses to elemental synergies (ELEMENTAL_COUNT per 3) at comparable investment levels
- [ ] Keystone drawbacks are proportional to keystone power
- [ ] No keystone has purely upside with a trivial drawback

### 3D Layout
- [ ] All 8 octant direction vectors are correct cube corners: (±1, ±1, ±1) / √3
- [ ] Each octant direction matches its 3 neighbor elements' axis signs
- [ ] Starting offset places octant entries near bridge midpoints

---

*End of design document. This is a living document — iterate with the game designer before YAML implementation.*
