# Mob Stat System — Definitive Design Document

Research completed 2026-05-02. Synthesizes Path of Exile (PoE1/PoE2), Last Epoch, Diablo 4, Grim Dawn, Wolcen (cautionary tale), 50 Hytale community mods, and a complete 25-file deep-read of our current implementation.

**Goal**: The best possible mob stat system for Trail of Orbis — precise, future-proof, fully compatible with modded content, zero dead stats.

---

## Table of Contents

1. [Current System — Problems](#1-current-system--problems)
2. [Genre Research Summary](#2-genre-research-summary)
3. [Design Principles](#3-design-principles)
4. [The Mob Stat Model](#4-the-mob-stat-model)
5. [Resistance Profile System](#5-resistance-profile-system)
6. [Archetype System](#6-archetype-system)
7. [Rarity Tiers: Normal / Elite / Boss](#7-rarity-tiers-normal--elite--boss)
8. [Ailment Interaction](#8-ailment-interaction)
9. [Scaling Curves](#9-scaling-curves)
10. [Stat Distribution: Template + Noise](#10-stat-distribution-template--noise)
11. [Combat Pipeline Integration](#11-combat-pipeline-integration)
12. [Config Structure](#12-config-structure)
13. [Anti-Patterns](#13-anti-patterns)
14. [Migration Plan](#14-migration-plan)
15. [Appendices](#15-appendices)

---

## 1. Current System — Problems

### Architecture

52 stats distributed via Dirichlet across all mobs. 16 archetypes defined in config but never assigned to specific mobs. Stats stored in `MobStats` record (17 fields + nullable `ElementalStats`). ECS component `MobScalingComponent` persists stats per mob.

### Confirmed Bugs

| # | Bug | Impact | Severity |
|---|-----|--------|----------|
| 1 | `toComputedStats()` drops Earth and Wind elemental penetration | Earth/Wind mobs weaker than intended | High |
| 2 | 7 AI stats consume pool but are never stored or used | ~13% stat budget wasted | High |
| 3 | 12 elemental % damage stats consume pool but Steps 4-7 skipped for mobs | ~23% stat budget wasted | High |
| 4 | `blockChance` and `parryChance` dead (passive block removed from AvoidanceProcessor) | ~4% stat budget wasted | High |
| 5 | `RPGSpawnManager` hardcodes spawn offsets (250/50) ignoring config (150/2) | Config values ignored | Medium |
| 6 | `spawnedPerChunk` never auto-clears | Spawn lockout over time | Medium |
| 7 | `MobStats` Javadoc says "10 stats, 40/60 split" — stale | Misleading | Low |
| 8 | `dirichlet_precision` config field loads but has no effect | Dead config | Low |
| 9 | `@Deprecated PoolConfig` dead code in `MobScalingConfig` | Confusing codebase | Low |
| 10 | `PlayerLevelCalculator` 3D vs 2D distance inconsistency | Minor inaccuracy | Low |

### The Core Problem

**~36% of the Dirichlet stat pool is consumed by stats with zero combat effect.** A level 50 mob with pool 800 effectively has ~512 points of real stats. Combined with Dirichlet spreading across 52 categories, individual values are too diluted to create noticeable tactical differences.

### What Works (Keep)

- Progressive scaling (10% power at level 1, full at 50+)
- Elemental assignment by keyword/group/override (MobElementResolver)
- ECS component pattern with codec serialization
- The distance-based overworld level system
- Elite rolling on spawn

---

## 2. Genre Research Summary

### Key Findings by Game

**Path of Exile**: Universal base stat table (5 stats by level) x per-species type multipliers (40-300%) x rarity mods. Ailment threshold = max HP. Boss damage stays flat while HP scales massively. No explicit archetype system — differentiation via per-species multipliers + skill loadouts.

**Last Epoch**: Zero base armor/resistances on all mobs. Universal hidden DR by level (caps ~87-90% at L100). Protection formula couples resistance to HP pool: `mitigation% = protection / (protection + totalHP)`. Boss ADR compresses DPS range. 60% reduced ailment effectiveness on bosses.

**Diablo 4**: Level-matched scaling. 2-4 affixes on elites. 17+ monster families with behavioral identity. **Critical flaw**: no elemental weaknesses on monsters — all damage types functionally equivalent.

**Grim Dawn**: Full player stat sheet on mobs. 10 damage types with faction-specific resistance profiles. 15 hero archetypes (~1/3 with pack-buffing auras). Faction identity through resistance profiles creates genuine build decisions.

**Wolcen (Cautionary)**: Overtuned HP (444K trash, 300 weapon damage). Additive-only scaling. No elemental interaction. Block/summon dominated. Endgame reset destroyed playerbase.

### Universal ARPG Pattern: 4 Layers of Mob Differentiation

1. **Behavior/AI** — what the mob *does* (resurrects allies, charges, buffs pack)
2. **Stat profile** — elemental resistances/damage per faction/element
3. **Rarity tier** — each tier adds *qualitative* new capabilities
4. **Pack composition** — mob synergy within groups

### Stats No ARPG Gives Mobs

Mana/resources, skill points, experience gain, magic find, gear requirements, damage conversions, passive trees.

---

## 3. Design Principles

### P1: Every Stat Must Create a Player Decision

If the player cannot notice a stat in gameplay or it doesn't change what they do, it shouldn't exist on mobs.

### P2: Few Strong Traits > Many Weak Ones

A mob with 40% fire resistance is interesting. A mob with 8% fire, 6% cold, 4% lightning is invisible. Concentrate budget into 2-3 defining traits.

### P3: Element-First Identity, Faction as Overlay

Resistance profiles derive primarily from a mob's **detected element** — fire mobs resist fire, water mobs resist water. This works for ALL mobs including modded/unknown ones. Faction profiles are an optional overlay that adds thematic flavor for known factions. Unknown mobs always get sensible defaults.

### P4: Ailment Threshold Scales With HP

Ailment threshold = max HP (PoE-style). No separate ailment resistance stat. Self-balancing — tankier mobs are naturally harder to ailment.

### P5: Boss Difficulty = HP + Mechanics, Not Damage

Boss damage stays near Elite levels. Boss HP scales massively. Difficulty comes from duration, pattern-learning, and mechanics (PoE: 0% extra boss damage from tiers).

### P6: Mob Stats Are a Strict Subset of Player Stats

Purpose-built for combat interaction. No mana, stamina, skill points, experience gain, damage conversions.

### P7: 100% Effective Stat Budget

Every point allocated must produce a measurable combat effect. Zero dead stats, zero phantom categories.

### P8: Universal Compatibility

Any NPC from any mod gets a valid stat profile. The system must handle: known factions, unknown factions, elementally-typed mobs, physical-only mobs, completely unknown mobs. No mob should ever crash or produce nonsensical stats.

---

## 4. The Mob Stat Model

### Complete Stat List

#### Core Stats (Always Present, Scale With Level)

| # | Stat | Type | Purpose | Source |
|---|------|------|---------|--------|
| 1 | `maxHealth` | double | Survival budget | Base table × archetype × rarity |
| 2 | `physicalDamage` | double | Base attack hit | Base table × archetype × rarity |
| 3 | `armor` | double | Physical DR (PoE formula) | Base table × archetype |
| 4 | `accuracy` | double | Hit chance vs player evasion | Base table |
| 5 | `moveSpeed` | double | Chase/pressure (1.0 = normal) | Archetype |

#### Offensive Stats (From Archetype, Don't Scale With Level)

| # | Stat | Type | Purpose | Cap |
|---|------|------|---------|-----|
| 6 | `critChance` | double | Crit probability | 50% |
| 7 | `critMultiplier` | double | Crit damage multiplier | 300% |
| 8 | `armorPenetration` | double | Bypass player armor | 75% |
| 9 | `lifeSteal` | double | % HP gained on hit | 30% |
| 10 | `trueDamage` | double | Bypasses all defenses | Sparingly used |

#### Defensive Stats (From Profile + Archetype)

| # | Stat | Type | Purpose | Cap |
|---|------|------|---------|-----|
| 11 | `evasion` | double | Avoid player hits (PoE formula) | — |
| 12 | `knockbackResistance` | double | Resist displacement | 100% |

#### Elemental Stats (From Profile)

| # | Stat | Type | Purpose | Cap |
|---|------|------|---------|-----|
| 13-18 | `elementalResistance[6]` | double | Per-element mitigation | 75% |
| 19-24 | `elementalDamage[6]` | double | Per-element flat damage output | — |
| 25-30 | `elementalPenetration[6]` | double | Bypass player elem resist | 50% |

#### Sustain

| # | Stat | Type | Purpose |
|---|------|------|---------|
| 31 | `healthRegen` | double | Per-second flat recovery |

#### Ailment

| # | Stat | Type | Purpose | Default |
|---|------|------|---------|---------|
| 32 | `ailmentThresholdMultiplier` | double | Multiplier on maxHP for threshold | 1.0 |
| 33 | `ailmentEffectiveness` | double | Multiplier on received ailment effects | 1.0 |

**Total: 33 stats.** Down from 52 in the current system. Every single stat has a verified path through the combat pipeline to a gameplay effect the player can observe.

### Removed Stats (Complete List With Rationale)

| Old Stat | Why Removed |
|----------|-------------|
| `blockChance` (passive) | Passive block removed from AvoidanceProcessor. Dead code path. |
| `parryChance` | No combat handler for mobs. Dead code path. |
| `attack_speed` | Never stored in MobStats. Dead pool consumer. |
| `attack_range` | Never stored in MobStats. Dead pool consumer. |
| `attack_cooldown` | Never stored in MobStats. Dead pool consumer. |
| `aggro_range` | Never stored in MobStats. Dead pool consumer. |
| `reaction_delay` | Never stored in MobStats. Dead pool consumer. |
| `charge_time` | Never stored in MobStats. Dead pool consumer. |
| `charge_distance` | Never stored in MobStats. Dead pool consumer. |
| `elemental_increased_damage[6]` | Steps 4-7 skipped for mobs (`isMobStats()`). Dead pipeline path. |
| `elemental_more_damage[6]` | Steps 4-7 skipped for mobs (`isMobStats()`). Dead pipeline path. |
| `dodgeChance` (flat) | Renamed to `evasion`. Same formula, clearer name. |

---

## 5. Resistance Profile System

### The Compatibility Problem

Our mod must handle:
- Known Hytale factions (Trork, Outlander, Feran, Kweebec, Scarak, Void creatures)
- Unknown vanilla mobs (animals, critters, bosses without faction)
- Modded mobs from other plugins (completely unknown)
- Mobs with elemental keywords (fire_mage, frost_giant)
- Mobs with no elemental affinity (generic bandits)

A faction-only system breaks for anything outside the 7 known factions. We need a universal system.

### Solution: 3-Layer Resistance Resolution

```
Layer 1: Element-Based Profile (universal default)
    ↓ overridden by
Layer 2: Faction Profile (optional, for known factions)
    ↓ overridden by
Layer 3: Config Override (per-mob-role explicit override)
```

#### Layer 1: Element-Based Profiles (Universal Default)

Every mob's detected element (from existing `MobElementResolver`) determines a base resistance profile. Mobs with no detected element get the `PHYSICAL` profile.

| Detected Element | Strong Resist | Moderate Resist | Weak Resist | Thematic Logic |
|-----------------|---------------|-----------------|-------------|----------------|
| **PHYSICAL** (default) | — | — | — | No elemental affinity, neutral |
| **FIRE** | Fire (+40%) | Earth (+10%) | Water (-20%), Wind (-10%) | Fire resists fire, vulnerable to water |
| **WATER** | Water (+40%) | Wind (+10%) | Fire (-10%), Lightning (-20%) | Water resists water, vulnerable to lightning |
| **LIGHTNING** | Lightning (+40%) | Wind (+10%) | Earth (-20%), Water (-10%) | Lightning resists lightning, vulnerable to earth |
| **EARTH** | Earth (+40%) | Fire (+10%) | Wind (-20%), Lightning (-10%) | Earth resists earth, vulnerable to wind |
| **WIND** | Wind (+40%) | Water (+10%) | Earth (-10%), Fire (-10%) | Wind resists wind, vulnerable to grounding |
| **VOID** | Void (+50%) | — | All non-void (-10%) | Void is its own dimension |

**Why this works for everything:**
- A modded `fire_dragon` gets Fire profile automatically (keyword "fire" → FIRE element → Fire profile)
- A modded `shadow_assassin` gets Void profile (keyword "shadow" → VOID element → Void profile)
- A completely unknown mob with no elemental keywords gets Physical profile (neutral, no surprises)
- A vanilla Trork with no elemental keywords gets Physical profile (safe default), UNLESS Layer 2 overrides it

**Rock-paper-scissors**: Fire > Earth > Lightning > Water > Fire. Wind and Void are semi-independent. This creates natural counter-play without being punishing — the weak resist is -10 to -20%, not immunity.

#### Layer 2: Faction Profiles (Optional Overlay)

For known Hytale factions, we override the element-based default with thematic faction profiles. Faction detection uses the existing NPC group path system.

| Faction | Detection Method | Resistance Overlay | Rationale |
|---------|-----------------|-------------------|-----------|
| **Trork** | NPCGroup contains "Trork" | Earth +35%, Physical +10%, Lightning -15% | Ground-dwelling warriors |
| **Outlander** | NPCGroup contains "Outlander" | Physical +10%, Void -15% | Human bandits, no affinity |
| **Feran** | NPCGroup contains "Feran" | Wind +30%, Lightning +10%, Fire -20% | Forest beasts, agile |
| **Kweebec** | NPCGroup contains "Kweebec" | Earth +25%, Water +20%, Fire -25%, Void -15% | Nature spirits |
| **Scarak** | NPCGroup contains "Scarak" | Lightning +25%, Fire +10%, Water -20% | Insectoid exoskeleton |
| **Void** | NPCGroup contains "Void" | Void +50%, all others -10% | Pure void entities |
| **Undead** | NPCGroup contains "Undead" or keyword | Void +20%, Earth +15%, Fire -20%, Lightning -15% | Reanimated |

When a faction profile exists, it **replaces** the element-based profile entirely. A Trork fire_mage gets the Trork profile (not the Fire profile), because the faction identity is more specific.

#### Layer 3: Config Override (Highest Priority)

Explicit per-role-name overrides in `mob-resistances.yml`:

```yaml
overrides:
  special_fire_boss:
    fire: 60
    water: -30
    lightning: 0
    earth: 10
    wind: 0
    void: 0
```

This handles edge cases and allows fine-tuning specific mobs without code changes.

#### Resolution Order (Code)

```java
ResistanceProfile resolve(String roleName, List<String> npcGroups, ElementType detectedElement) {
    // Layer 3: Explicit override (highest priority)
    if (overrides.containsKey(roleName)) return overrides.get(roleName);

    // Layer 2: Faction profile
    Faction faction = detectFaction(npcGroups);
    if (faction != null) return factionProfiles.get(faction);

    // Layer 1: Element-based profile (universal default)
    if (detectedElement != null) return elementProfiles.get(detectedElement);

    // Absolute fallback: Physical (neutral)
    return ResistanceProfile.NEUTRAL;
}
```

### Resistance Caps and Penetration

- **Maximum mob resistance**: 75% (same as player cap)
- **Minimum mob resistance**: -25% (negative = weakness, capped to prevent extreme exploitation)
- **Penetration floor**: 0% effective resistance (penetration cannot make effective resist negative)
- **Faction resistance + element resistance do NOT stack** — faction replaces element profile

### Design Constraint: No Immunity

No mob should ever have 100% resistance to any element. Maximum is 75% (with Void faction's +50% Void resistance being the highest single-element value). Every damage type remains viable against every faction — some just require penetration investment.

**Exception**: Undead can be immune to Poison ailment (thematic, expected). They still take Void damage; the ailment application is blocked, not the damage.

---

## 6. Archetype System

### Purpose

Archetypes define a mob's **combat role** — how it fights, not what faction it belongs to. A Trork Brute and a Scarak Brute both fight as brutes (high HP, slow, heavy hits), but with different resistance profiles.

### Archetype List

| Archetype | HP | Damage | Armor | Evasion | Speed | Crit% | CritMult | Identity |
|-----------|-----|--------|-------|---------|-------|-------|----------|----------|
| **Warrior** | 1.0× | 1.0× | 1.0× | 1.0× | 1.0× | 10% | 160% | Balanced baseline |
| **Brute** | 1.4× | 1.2× | 1.3× | 0.5× | 0.85× | 5% | 150% | Slow, tanky, heavy hits |
| **Ranger** | 0.7× | 1.1× | 0.5× | 1.5× | 1.1× | 15% | 170% | Fragile, evasive, ranged |
| **Caster** | 0.6× | 1.3× | 0.3× | 0.8× | 0.9× | 10% | 180% | Glass cannon, elemental |
| **Assassin** | 0.8× | 1.4× | 0.4× | 1.8× | 1.3× | 30% | 250% | High crit, fast, evasive |
| **Tank** | 1.8× | 0.7× | 2.0× | 0.3× | 0.7× | 5% | 130% | Maximum durability |
| **Support** | 0.9× | 0.6× | 0.8× | 1.0× | 1.0× | 5% | 130% | Future: buffs allies |

### Archetype Offensive Emphasis

| Archetype | Primary Damage | Armor Pen | Life Steal | Special |
|-----------|---------------|-----------|------------|---------|
| Warrior | Physical | 5% | 0% | — |
| Brute | Physical | 10% | 5% | Knockback resistance 50% |
| Ranger | Physical + element | 0% | 0% | — |
| Caster | Elemental (from element) | 0% | 0% | Higher elemental damage |
| Assassin | Physical | 20% | 10% | — |
| Tank | Physical | 0% | 0% | Knockback resistance 80% |
| Support | Elemental (from element) | 0% | 0% | — |

### Archetype Auto-Assignment

Fully automated from existing NPC data. Priority order:

```
1. Config override (mob-archetypes.yml: role_name → archetype)
2. NPCGroup-based detection (animals/beasts → Beast archetype)
3. Role name keywords (validated against 78 actual Hytale NPC IDs):
   - "brute", "ogre", "mauler", "brawler", "berserker", "marauder", "aberrant" → Brute
   - "warrior", "fighter", "scrapper", "soldier", "striker" → Warrior
   - "archer", "ranger", "hunter", "gunner", "lobber", "scout" → Ranger
   - "mage", "archmage", "wizard", "shaman", "sorcerer", "priest", "cultist" → Caster
   - "assassin", "stalker", "thief", "seeker" → Assassin
   - "guard", "defender", "sentry", "knight" → Tank
4. Fallback → Warrior (safe baseline)
```

### Beast Archetype (Animals/Creatures)

~20 mobs in our pool are animals (Wolf, Bear, Spider, Crocodile, Raptor, Snake, Scorpion, Hyena, etc.) with no role suffix. These are detected via NPCGroup membership (Prey, PreyBig, Animals, Beasts) and get the Beast archetype:

| Archetype | HP | Damage | Armor | Evasion | Speed | Crit% | CritMult | Identity |
|-----------|-----|--------|-------|---------|-------|-------|----------|----------|
| **Beast** | 1.1× | 1.1× | 0.7× | 1.2× | 1.15× | 8% | 160% | Moderate all-rounder, slightly fast |

Beasts deal physical damage, have moderate HP, are slightly fast and evasive. This matches how animals feel in Hytale — they're not tanks or glass cannons, they're natural predators.

**Compatibility**: Unknown mobs always get Warrior (baseline). Detected animals get Beast. Modded mobs with recognizable keywords get the matching archetype. No crash, no nonsense values.

---

## 7. Rarity Tiers: Normal / Elite / Boss

Three tiers only. Clean, simple, each with clear purpose.

### Tier Multipliers

| Stat | Normal | Elite | Boss |
|------|--------|-------|------|
| **HP** | 1.0× | 2.5× | 8.0× |
| **Damage** | 1.0× | 1.3× | 1.5× |
| **Armor** | 1.0× | 1.5× | 2.0× |
| **Evasion** | 1.0× | 1.3× | 1.0× |
| **Move Speed** | 1.0× | 1.1× | 1.0× |
| **XP** | 1.0× | 3.0× | 10.0× |
| **IIQ** (Item Quantity) | 1.0× | 3.0× | 10.0× |
| **IIR** (Item Rarity) | 1.0× | 2.0× | 8.0× |
| **Ailment Effectiveness** | 100% | 70% | 40% |

### Design Rationale

**Boss damage is only 1.5×** (same as current Elite). Boss difficulty comes from:
- **8× HP** — the fight is long, testing sustain and mechanics
- **40% ailment effectiveness** — ailments work but at reduced power
- **Hand-designed attack patterns** — difficulty from mechanics, not numbers
- **2× armor** — harder to burst down

This follows PoE (0% extra boss damage from map tiers) and Last Epoch (ADR). Players should die to boss *mechanics*, not to unavoidable stat-check one-shots.

**Elite is the "noticeable threat"**: 2.5× HP makes them clearly tougher than normal. 1.3× damage makes their hits sting. 70% ailment effectiveness means ailments still work well but slightly reduced. Players learn to focus elites in packs.

**Boss evasion is 1.0×** (not scaled). Bosses should be hittable — the challenge is surviving their attacks and dealing enough damage over a long fight, not missing your swings.

### Realm-Specific Overrides

| Context | Elite HP | Elite Damage | Boss HP | Boss Damage |
|---------|----------|-------------|---------|-------------|
| Overworld | 2.5× | 1.3× | 8.0× | 1.5× |
| Realm | 3.0× | 1.3× | 12.0× | 1.5× |

Realm elites are slightly tougher (3.0× vs 2.5× HP). Realm bosses are significantly tougher (12× vs 8× HP). Realm is endgame content — it should feel harder.

### Elite Spawn Chance

Keep current system:
```
eliteChance = baseChance + (mobLevel × chancePerLevel)
             = 0.05 + (level × 0.0001)
```
Capped at 25%. At level 50: ~5.5%. At level 100: ~6%.

### Boss Identity

**Boss is a dungeon/realm property, not a mob property.**

The realm defines which NPC type is the boss. The mob itself doesn't know it's a boss — the realm manager applies boss multipliers when spawning it. This keeps mob definitions clean and allows any mob to be a boss in the right context.

---

## 8. Ailment Interaction

### Current Problem

Mobs have zero ailment resistance. Every elemental hit has 10%+ chance to apply. Freeze trivializes all mobs equally. No scaling, no counterplay.

### Proposed System: HP-Based Threshold + Tier Modifier

Two orthogonal controls:

1. **Ailment Threshold** = how much damage is needed for a given ailment magnitude
2. **Ailment Effectiveness** = multiplier on the final ailment effect

```
ailmentThreshold = maxHealth × ailmentThresholdMultiplier
```

| Tier | Threshold Mult | Effectiveness | Combined Effect |
|------|---------------|---------------|-----------------|
| Normal | 1.0 | 100% | Standard — threshold equals max HP |
| Elite | 0.8 | 70% | Lower threshold (compensates for higher HP), reduced effect |
| Boss | 0.5 | 40% | Much lower threshold (so ailments CAN apply), greatly reduced effect |

### Non-Damaging Ailments (Freeze, Shock)

```
rawMagnitude = (hitDamage / ailmentThreshold) × maxEffect
effectiveMagnitude = clamp(rawMagnitude × ailmentEffectiveness, minEffect, maxEffect)
```

**Freeze** (movement + action speed slow):
- `maxEffect` = 30%
- `minEffect` = 5% (if hit is too weak relative to threshold)

**Shock** (damage amplification on target):
- `maxEffect` = 50%
- `minEffect` = 5%

**Worked Example — Freeze on a Boss** (level 50, using real baseline numbers):

Boss HP after weighted formula + 3.0× classification: ~458 HP. With new 8.0× rarity: ~1,222 HP.
Player hits for 35 damage.
```
threshold = 1,222 × 0.5 = 611
rawMagnitude = (35 / 611) × 30% = 1.72% slow
effective = 1.72% × 0.4 = 0.69% slow → clamped to 5% minimum
```
The freeze lands but barely matters. To meaningfully freeze a boss, you need massive single-hit damage or dedicated investment.

**Same hit on a Normal mob** (~153 HP):
```
threshold = 153 × 1.0 = 153
rawMagnitude = (35 / 153) × 30% = 6.9% slow
effective = 6.9% × 1.0 = 6.9% slow
```
Noticeable freeze. Normal mobs are meaningfully affected by ailments.

### Damaging Ailments (Burn, Poison)

Burn and Poison DPS calculations stay unchanged (they already scale from hit damage). The `ailmentEffectiveness` applies to the final DPS:

```
effectiveDPS = calculatedDPS × ailmentEffectiveness
```

Bosses take 40% of normal ailment DoT DPS. DoT builds still work — they just can't trivially melt bosses.

### Element-Specific Ailment Thresholds

On top of the base system, the resistance profile can add per-ailment threshold bonuses:

```
effectiveThreshold = (maxHealth × thresholdMult) × (1 + elementAilmentBonus)
```

| Element Profile | Ailment Bonus | Effect |
|----------------|---------------|--------|
| Fire mobs | +50% Burn threshold | Fire mobs resist being burned (thematic) |
| Water mobs | +50% Freeze threshold | Water/ice mobs resist freezing |
| Lightning mobs | +50% Shock threshold | Lightning mobs resist shock |
| Void mobs | +50% Poison threshold | Void entities resist decay |
| Undead | Immune to Poison ailment | No living tissue (ailment blocked, not damage) |

These are config-driven and follow from the resistance profile, not hardcoded.

### What This System Achieves

1. **Self-balancing**: Tankier mobs are naturally harder to ailment (threshold = HP)
2. **Boss viability**: Reduced threshold (0.5×) means ailments CAN land on bosses, but reduced effectiveness (0.4×) means they don't trivialize
3. **Build relevance**: DoT/ailment builds are rewarded for investment but not dominant against all content
4. **Thematic consistency**: Fire mobs resist burn, water mobs resist freeze — intuitive for players
5. **Universal**: Works for any mob regardless of faction or mod origin

---

## 9. Scaling Curves

### Design Goal

Kill-time targets for a level-appropriate player with level-appropriate gear:
- Normal mob: **3-5 hits**
- Elite mob: **8-15 hits**
- Boss mob: **30-60 seconds**

### KEEP the Existing Scaling System

**The current pool + weighted HP/damage system already produces correct kill times.** Simulation-verified: a level 50 normal mob has ~167 HP, a level 50 player hits for ~35 damage = ~5 hits. This matches our 3-5 hit target perfectly.

The scaling system uses three layers that must stay synchronized:

**1. Stat Pool Generation** (`MobStatGenerator` → `MobStatFactory`):
```
scalingFactor = progressiveScale(level)  // 0.1 at L1, 1.0 at L30+
expMultiplier = LevelScaling.getMultiplier(level)  // 1.0 at L1, ~1.36 at L50, ~1.50 at L100
levelPool = level × pointsPerLevel × scalingFactor
totalPool = (levelPool + distanceBonus) × expMultiplier
```

**2. Weighted HP Formula** (`MobScalingSystem`):
```
weight = sqrt(vanillaHP / 100)  // compresses vanilla range via square root
effectiveRpg = max(rpgTargetHP × progressiveScale × expMultiplier, 10 × bossMult)
finalHP = effectiveRpg × weight
```

**3. Weighted Damage Formula** (`RPGDamageSystem`):
```
weight = sqrt(max(vanillaDamage, 5) / 10)
effectiveRpg = max(rpgTargetDmg × progressiveScale × classMult, 5 × classMult)
finalDamage = effectiveRpg × weight
```

**Critical**: Gear and mobs share the SAME `LevelScaling.getMultiplier()`. This keeps combat balance consistent — if mobs get 36% stronger at level 50, gear is also 36% stronger. Changing LevelScaling affects BOTH systems.

### What Changes in the Refactor

The pool system stays. The weighted formulas stay. What changes is how pool points are **distributed** across stats:

- **Old**: Pure Dirichlet across 52 stats (36% wasted on dead stats, remainder too diluted)
- **New**: Template + Noise across 33 effective stats with archetype multipliers concentrating budget

The total pool budget at each level is identical. The stat conversion factors (pool share × factor = stat value) are retuned for the reduced stat count but validated via simulation to produce the same kill times.

### Archetype Multipliers on Pool Stats

After pool distribution, archetype multipliers are applied:
```
finalHP = poolHP × archetypeMult.hp × rarityMult.hp × (1 + noise)
finalDamage = poolDamage × archetypeMult.damage × rarityMult.damage × (1 + noise)
```

The weighted HP/damage formulas then transform these into final in-game values.

### Level Calculation (Unchanged)

**Overworld**: `level = max(1, floor(distanceFromSpawn / 75))`
**Realms**: `level = mapLevel`

### Simulation Verification Gate

All scaling changes MUST pass `./gradlew simulate` before deployment. The simulation uses the real `RPGDamageCalculator`, real `AttributeCalculator`, real configs, and produces kill-time CSVs. If TTK for normal mobs deviates from 3-5 hits at any level bracket, the scaling is wrong.

---

## 10. Stat Distribution: Template + Noise

### Why Not Pure Dirichlet

The current pure Dirichlet produces mobs that are "porridge" — statistically average across all stats, tactically invisible. Each mob gets thin sprinkles of everything instead of strong defining traits.

### The Template + Noise System

Three deterministic layers + one noise layer:

```
Final Stats = BaseTable(level) × ArchetypeMult × ProfileResistances × (1 + Noise)
```

#### Layer 1: Base Table (Deterministic)

Universal base stats from level. Every mob of the same level starts with the same base.

#### Layer 2: Archetype Modifiers (Deterministic)

Multipliers on HP, damage, armor, evasion, speed, crit. 8 archetypes defined in Section 6.

#### Layer 3: Resistance Profile (Deterministic)

Elemental resistances, weaknesses, and ailment thresholds from Section 5.

#### Layer 4: Noise (Randomized, Small)

Each stat independently varied by ±15%:

```
noise[stat] = random.nextGaussian() × 0.10  // stddev 10%, capped at ±15%
noisedStat = baseStat × clamp(1 + noise, 0.85, 1.15)
```

**Why Gaussian not Dirichlet**: Gaussian noise on individual stats is simpler, doesn't create zero-sum tradeoffs, and the ±15% range keeps mobs recognizable within their archetype. Two Trork Warriors will feel similar but not identical.

**What noise does NOT affect**:
- Elemental resistances (these define identity — shouldn't be random)
- Ailment thresholds (derived from HP, which IS noised)
- Rarity multipliers (these are fixed per tier)

---

## 11. Combat Pipeline Integration

### Current Architecture (Correct — Keep)

Mob damage flows through the pipeline via Hytale's native damage event system:

1. `MobScalingSystem.applyStatModifiers()` modifies the mob's attack damage via Hytale's `EntityStatMap` modifiers — this scales the **vanilla attack damage**
2. When the mob attacks, Hytale fires a damage event with the **already-modified vanilla damage** as the base
3. `RPGDamageSystem` intercepts this event — base damage already includes our scaled physical damage
4. `RPGDamageCalculator` receives the damage — mob physical damage is **already in the event's base**
5. Step 1 (`applyFlatDamage`) is correctly skipped for mobs (`isMobStats()` check) — removing this skip would **double-count** physical damage

**The `isMobStats()` skip is correct architecture, not a hack.** This is the standard community pattern — damage scaling via `EntityStatMap` modifiers + in-flight `DamageEventSystem` adjustments.

### What Changes

Only `toComputedStats()` needs rework. The pipeline itself stays the same.

### Pipeline Steps for Mob Attackers

- Step 1: **Skipped** (physical damage already in vanilla event base) — KEEP THIS
- Step 2: Mob elemental flat damage added here (from `ElementalStats`) — this IS new damage vanilla doesn't know about
- Steps 3-7: Produce zero for mobs (no conversion, no % stats) — correct, they don't need them
- Step 8: Conditional multipliers apply (realm damage bonus, etc.)
- Step 9: Crit roll with mob's critChance and critMultiplier
- Step 10: Defenses (player armor, resistances, evasion)
- Step 11: True damage added

### `toComputedStats()` Rework

Clean, complete mapping. No silent drops. Note: `physicalDamage` is NOT forwarded as `flatPhysicalDamage` (it enters the pipeline via the vanilla damage event, not via Step 1).

```java
public ComputedStats toComputedStats() {
    var builder = new ComputedStats.Builder()
        .mobStats(true)
        // Core — physicalDamage intentionally NOT forwarded here (comes via vanilla event)
        .maxHealth(maxHealth)
        .armor(armor)
        .accuracy(accuracy)
        .movementSpeedPercent(moveSpeed)
        // Offensive
        .criticalChance(critChance)
        .criticalMultiplier(critMultiplier)
        .armorPenetration(armorPenetration)
        .lifeSteal(lifeSteal)
        .trueDamage(trueDamage)
        // Defensive
        .evasion(evasion)
        .knockbackResistance(knockbackResistance)
        // Sustain
        .healthRegen(healthRegen)
        // Ailment
        .ailmentThresholdMultiplier(ailmentThresholdMultiplier)
        .ailmentEffectiveness(ailmentEffectiveness);

    // Forward ALL 6 elements — no silent drops
    if (elementalStats != null) {
        for (ElementType element : ElementType.values()) {
            builder.elementalFlatDamage(element, elementalStats.getFlatDamage(element));
            builder.elementalResistance(element, elementalStats.getResistance(element));
            builder.elementalPenetration(element, elementalStats.getPenetration(element));
        }
    }

    return builder.build();
}
```

### Negative Resistances (Elemental Weaknesses)

Our resistance formula in `ElementalCalculator.getEffectiveResistance()` already handles negative resistance values correctly:
- When `resistance < 0`: passes through as-is (no `max(0, ...)` clamp)
- The `max(0, ...)` clamp only applies when `resistance > 0 AND penetration > 0` (prevents penetration from pushing positive resist below zero)
- The damage formula `damage × (1 - effectiveResist/100)` with `effectiveResist = -20` produces `damage × 1.20` (20% more damage)
- The Death Recap formatter already has "vulnerable" text for negative resistance values

No pipeline changes needed for the weakness system. Negative resistance values in mob profiles Just Work.

### Avoidance Pipeline (Mob as Defender)

Current: mob `dodgeChance` maps to `evasion` slot, goes through PoE evasion formula. This is correct behavior, just needs the rename from `dodgeChance` to `evasion` in MobStats.

Mob active block: not supported (no `DamageDataComponent`). This is correct — mobs don't actively block in Hytale.

### Avoidance Pipeline (Mob as Attacker)

Player defenses work normally against mob attacks:
1. Player dodge chance
2. Player evasion vs mob accuracy
3. Player active block
4. Player armor vs mob armor penetration
5. Player elemental resistances vs mob elemental penetration

All paths verified functional. No changes needed.

---

## 12. Config Structure

### Base Stat Scaling (No Separate Config File)

Base stat scaling uses the EXISTING pool system (`MobStatPoolConfig`) with `LevelScaling.getMultiplier()`. No new config file needed — the pool generation math, conversion factors, progressive scaling, and exponential scaling remain in the existing configs. What changes is the stat DISTRIBUTION within the pool (see `mob-archetypes.yml` below).

**Noise settings** are part of `mob-archetypes.yml`:
```yaml
noise:
  enabled: true
  standard_deviation: 0.10  # 10% stddev
  max_deviation: 0.15       # clamp at ±15%
```

### `mob-archetypes.yml`

```yaml
archetypes:
  warrior:
    hp: 1.0
    damage: 1.0
    armor: 1.0
    evasion: 1.0
    speed: 1.0
    crit_chance: 10
    crit_multiplier: 160
    armor_pen: 5
    life_steal: 0
    knockback_resistance: 0

  brute:
    hp: 1.4
    damage: 1.2
    armor: 1.3
    evasion: 0.5
    speed: 0.85
    crit_chance: 5
    crit_multiplier: 150
    armor_pen: 10
    life_steal: 5
    knockback_resistance: 50

  # ... (all 8 archetypes: warrior, brute, ranger, caster, assassin, tank, support, beast)

# Auto-assignment keywords (validated against 78 real Hytale NPC IDs)
keywords:
  brute: [brute, ogre, mauler, brawler, berserker, marauder, aberrant]
  warrior: [warrior, fighter, scrapper, soldier, striker]
  ranger: [archer, ranger, hunter, gunner, lobber, scout]
  caster: [mage, archmage, wizard, shaman, sorcerer, priest, cultist]
  assassin: [assassin, stalker, thief, seeker]
  tank: [guard, defender, sentry, knight]

# NPCGroup-based detection (animals without role keywords)
group_archetypes:
  beast: [Prey, PreyBig, Animals, Beasts, Critters]

# Explicit overrides (role_name → archetype)
overrides: {}
```

### `mob-resistances.yml`

```yaml
# Layer 1: Element-based profiles (universal)
element_profiles:
  PHYSICAL:
    resistances: { fire: 0, water: 0, lightning: 0, earth: 0, wind: 0, void: 0 }
    ailment_bonuses: {}
  FIRE:
    resistances: { fire: 40, water: -20, lightning: 0, earth: 10, wind: -10, void: 0 }
    ailment_bonuses: { burn_threshold: 50 }
  WATER:
    resistances: { fire: -10, water: 40, lightning: -20, earth: 0, wind: 10, void: 0 }
    ailment_bonuses: { freeze_threshold: 50 }
  LIGHTNING:
    resistances: { fire: 0, water: -10, lightning: 40, earth: -20, wind: 10, void: 0 }
    ailment_bonuses: { shock_threshold: 50 }
  EARTH:
    resistances: { fire: 10, water: 0, lightning: -10, earth: 40, wind: -20, void: 0 }
    ailment_bonuses: {}
  WIND:
    resistances: { fire: -10, water: 10, lightning: 0, earth: -10, wind: 40, void: 0 }
    ailment_bonuses: {}
  VOID:
    resistances: { fire: -10, water: -10, lightning: -10, earth: -10, wind: -10, void: 50 }
    ailment_bonuses: { poison_threshold: 50 }

# Layer 2: Faction profiles (optional overlay, replaces element profile)
faction_profiles:
  trork:
    detection: { groups: [Trork] }
    resistances: { fire: 0, water: 0, lightning: -15, earth: 35, wind: 0, void: 0 }
    physical_resistance: 10
    ailment_bonuses: {}
  outlander:
    detection: { groups: [Outlander] }
    resistances: { fire: 0, water: 0, lightning: 0, earth: 0, wind: 0, void: -15 }
    physical_resistance: 10
    ailment_bonuses: {}
  feran:
    detection: { groups: [Feran] }
    resistances: { fire: -20, water: 0, lightning: 10, earth: 0, wind: 30, void: 0 }
    ailment_bonuses: { freeze_threshold: 30 }
  kweebec:
    detection: { groups: [Kweebec] }
    resistances: { fire: -25, water: 20, lightning: 0, earth: 25, wind: 0, void: -15 }
    ailment_bonuses: {}
  scarak:
    detection: { groups: [Scarak] }
    resistances: { fire: 10, water: -20, lightning: 25, earth: 0, wind: 0, void: 0 }
    ailment_bonuses: { shock_threshold: 30 }
  void:
    detection: { groups: [Void] }
    resistances: { fire: -10, water: -10, lightning: -10, earth: -10, wind: -10, void: 50 }
    ailment_bonuses: { poison_threshold: 50 }
  undead:
    detection: { groups: [Undead], keywords: [undead, skeleton, zombie, wraith, ghost, lich] }
    resistances: { fire: -20, water: 0, lightning: -15, earth: 15, wind: 0, void: 20 }
    ailment_bonuses: { poison: immune }

# Layer 3: Per-role overrides (highest priority)
overrides: {}

# Global settings
settings:
  max_resistance: 75
  min_resistance: -25
  penetration_floor: 0  # pen can't make effective resist negative
```

### `mob-rarity.yml`

```yaml
tiers:
  normal:
    hp: 1.0
    damage: 1.0
    armor: 1.0
    evasion: 1.0
    speed: 1.0
    xp: 1.0
    iiq: 1.0
    iir: 1.0
    ailment_effectiveness: 1.0

  elite:
    hp: 2.5
    damage: 1.3
    armor: 1.5
    evasion: 1.3
    speed: 1.1
    xp: 3.0
    iiq: 3.0
    iir: 2.0
    ailment_effectiveness: 0.7

  boss:
    hp: 8.0
    damage: 1.5
    armor: 2.0
    evasion: 1.0
    speed: 1.0
    xp: 10.0
    iiq: 10.0
    iir: 8.0
    ailment_effectiveness: 0.4

# Realm overrides
realm_overrides:
  elite:
    hp: 3.0
  boss:
    hp: 12.0

# Elite spawn
elite_spawn:
  base_chance: 0.05
  chance_per_level: 0.0001
  max_chance: 0.25
```

---

## 13. Anti-Patterns

### AP1: HP Sponge Without Mechanics (Wolcen)

Wolcen's L217 trash: 444K HP vs 300-400 weapon damage. Pure padding.

**Our guard**: HP:Damage ratio calibrated for 3-5 hit normal kills. Base table values checked against expected player damage at each level.

### AP2: Resistances Without Reduction Tools

If mobs have 40% fire resist but players have no fire penetration, fire builds are permanently 40% weaker.

**Our guard**: Per-element penetration exists in skill tree (Wind arm), gear modifiers, and attributes. Maximum faction resistance is 50%. Moderate penetration investment (~30 attribute points in the counter-element) neutralizes it.

### AP3: Additive-Only Scaling (Wolcen)

All bonuses in one additive pool → diminishing returns → ceiling.

**Our guard**: 11-step pipeline uses multiplicative layers correctly. No changes needed.

### AP4: Untelegraphed One-Shots (D4 Teleporter)

D4's 0.33s Teleporter affix = effectively unavoidable.

**Our guard**: Boss attacks are hand-designed with visual tells. No instant-damage-at-distance patterns.

### AP5: All Mobs Feel the Same

Pure stat scaling without qualitative tier changes.

**Our guard**: Element-based resistance profiles + archetypes create recognizable combat identities. Elites are noticeably different (70% ailment effectiveness, higher stats). Bosses are a different fight entirely.

### AP6: Damage Type Globally Useless

A mandatory-content faction with 100% resist to a damage type = dead builds.

**Our guard**: Max resistance 75%. No immunities (except Undead immune to Poison *ailment*, not Void damage). Every element penetrable.

### AP7: Stats Without Player Counter

Mob "dodge" with no player "accuracy" = random frustration.

**Our guard**:

| Mob Defense | Player Counter |
|-------------|----------------|
| Armor | Armor Penetration (gear, skill tree) |
| Evasion | Accuracy (Wind attribute, gear) |
| Elemental Resistance | Elemental Penetration (per element) |
| Ailment Threshold | Higher hit damage, status effect chance stats |
| Knockback Resistance | (No counter — but not a survival stat) |

---

## 14. Migration Plan

### Phase 1: Bug Fixes (No Design Changes)

Minimal risk. Can be done immediately.

1. Fix `toComputedStats()` to forward Earth and Wind penetration
2. Remove 19 dead stats from Dirichlet pool config
3. Fix RPGSpawnManager hardcoded spawn offsets → read from config
4. Add periodic auto-clearing to `spawnedPerChunk`
5. Delete deprecated `PoolConfig` dead code from `MobScalingConfig`
6. Update stale MobStats Javadoc

### Phase 2: Stat Model Rework

The core refactor. Changes `MobStats`, stat generation, config files.

1. Create new `MobStats` record with 33 stats (Section 4)
2. Create `ResistanceProfileResolver` with 3-layer resolution (Section 5)
3. Create `ArchetypeResolver` with keyword + config lookup (Section 6)
4. Replace Dirichlet stat distribution with Template + Noise (Section 10) — KEEP existing pool/weighted formulas (Section 9)
5. Add `ailmentThresholdMultiplier` and `ailmentEffectiveness` to MobStats
6. Rework `toComputedStats()` — no silent drops (Section 11). KEEP `isMobStats()` skip (prevents double-counting)
7. Update `MobScalingComponent` codec for new stat model
8. Deploy new config files (`mob-archetypes.yml`, `mob-resistances.yml`, `mob-rarity.yml`)
9. Update simulation framework to use new stat factory
10. Run `./gradlew simulate` — verify kill times match 3-5 / 8-15 / 30-60s targets
11. Delete old `mob-stat-pool.yml` only after simulation validates replacement

### Phase 3: Ailment Integration

Connects the ailment system to the new mob stats.

1. Modify `AilmentCalculator` to read `ailmentThresholdMultiplier` and `ailmentEffectiveness` from defender ComputedStats
2. Add element-specific ailment threshold bonuses from resistance profiles
3. Implement Undead Poison immunity (ailment blocked, damage passes through)
4. Update `AilmentTickSystem` to apply effectiveness multiplier to tick damage
5. Update `MobSpeedEffectManager` to respect Freeze effectiveness

### Phase 4: Rarity Tier Rework

Applies the new rarity multipliers.

1. Update rarity multiplier table in mob stat generation
2. Add realm-specific overrides for Elite/Boss HP
3. Ensure loot system reads new IIQ/IIR multipliers
4. Test kill times at each tier match design targets (3-5 / 8-15 / 30-60s)

---

## 15. Appendices

### Appendix A: Genre Comparison Summary

| Dimension | PoE | Last Epoch | Diablo 4 | Grim Dawn | ToO Current | ToO Proposed |
|-----------|-----|------------|----------|-----------|-------------|--------------|
| Mob stat count | ~8 + type-specific | ~5 core | ~6 core | Full player sheet | 52 (19 dead) | 33 effective |
| Resistance system | Per-type/from mods | Zero base | None (single DR) | 10 types, per-faction | 6 elements, random | 6 elements, 3-layer resolution |
| Ailment interaction | Threshold = HP | 60% reduced on bosses | N/A | Per-type resistance | None | Threshold + effectiveness |
| Rarity tiers | 4 tiers | 4 tiers | 2 tiers | 5 tiers | 3 tiers | 3 tiers |
| Boss design | HP wall + mechanics | HP + ADR + mechanics | HP + phases | HP + resistance | HP multiplier only | HP wall + reduced ailments |
| Stat distribution | Table × species mult | Universal DR × level | Level-matched | Per-creature definition | Dirichlet × 52 | Template × archetype × noise |
| Compatibility | N/A (single game) | N/A | N/A | N/A | Faction-dependent | Universal (element fallback) |

### Appendix B: PoE Monster Base Stat Table

| Level | Life | Damage | Evasion | Accuracy |
|-------|------|--------|---------|----------|
| 1 | 15 | 5 | 53 | 14 |
| 10 | 104 | 17 | 244 | 31 |
| 20 | 231 | 32 | 507 | 48 |
| 30 | 443 | 52 | 819 | 64 |
| 40 | 783 | 84 | 1,413 | 92 |
| 50 | 1,629 | 147 | 2,256 | 102 |
| 68 | 5,642 | 374 | 4,681 | 290 |
| 84 | 16,161 | 822 | 8,548 | 538 |
| 100 | 44,831 | 1,758 | 15,084 | 980 |

PoE rarity hidden mods: Magic +148% life/+30% dmg. Rare +390% life/+50% dmg. Unique +698% life/+70% dmg. Boss map tier scaling: up to +2046% life, 0% extra damage.

### Appendix C: PoE2 Monster Base Stat Table

| Level | Life | Damage | Evasion | Accuracy | Armour |
|-------|------|--------|---------|----------|--------|
| 1 | 15 | 9 | 24 | 32 | 5 |
| 40 | 1,253 | 90 | 347 | 391 | 599 |
| 68 | 7,757 | 233 | 708 | 1,251 | 3,451 |
| 80 | 15,609 | 334 | 905 | 1,964 | 6,867 |

PoE2 adds Armour as universal base stat.

### Appendix D: Last Epoch Key Mechanics

- Universal DR: ~87-90% at level 100
- Protection formula: `mitigation% = protection / (protection + totalHP)`
- Boss ADR: 80% more player damage → ~50% faster kill
- Ailment effectiveness on bosses: 40%
- Zero base armor on all monsters
- Champion: 3rd affix from exclusive pool, drops Sealed affix item
- High Health threshold: >65% HP. Low Health: <35% HP.

### Appendix E: Grim Dawn Hero Archetypes

| Archetype | Effect | Pack Impact |
|-----------|--------|-------------|
| Bruiser | Reduced dmg taken, increased dmg dealt, stun | Self only |
| Burning | Bonus fire damage, ring-of-fire AoE | Area denial |
| Charger | Periodic charge at distant targets | Gap closer |
| Corrupted | Aether explosion, aether aura | **Buffs nearby allies** |
| Defender | Damage reduction aura, stun | **Buffs nearby allies** |
| Diseased | Permanent poison aura, poison field | Area denial |
| Electrified | Bonus lightning, lightning nova | Area damage |
| Frozen | Freezing orb, chilling aura | **Debuffs nearby players** |
| Reflective | Periodic damage reflection shield | Punishes DPS |
| Regenerator | HP regen aura | **Buffs nearby allies** |
| Shielded | Periodic immunity, elemental bolt | Self defense |
| Supporter | Damage aura, chain-heal | **Buffs nearby allies** |
| Swift | Speed aura for self and allies | **Buffs nearby allies** |
| Unstoppable | Bonus chaos, CC immunity aura | **Buffs nearby allies** |
| Voidtouched | Chaos blaze, chaos chain lightning | Area damage |

### Appendix F: Wolcen Anti-Patterns

1. HP overtuned: L217 trash = 444K HP, endgame weapons = 300-400 base damage
2. Additive-only scaling: all % bonuses in one pool, diminishing returns
3. No elemental weaknesses on monsters
4. Block (5× effective HP) and Summons (45% DR + decoy) dominated all other defenses
5. Endgame reset deleted years of player investment

### Appendix G: Community Mod Patterns

Key patterns adopted:
- Named modifier keys for EntityStatMap cleanup
- `trueBaseHealthCache` anti-compounding
- Boss = dungeon/realm property, not mob property
- Decoupled HP scaling (persistent modifier) vs damage scaling (in-flight event)
- Augment-as-behavior for elites — not a subclass, a registered behavior set

### Appendix H: Player Counter-Stats for Every Mob Defense

| Mob Stat | Player Counter | Source |
|----------|---------------|--------|
| Armor | Armor Penetration | Gear suffix, Assassin archetype nodes |
| Evasion | Accuracy | Wind attribute (+3.0/pt), gear, skill tree |
| Fire Resistance | Fire Penetration | Gear suffix, Fire skill tree arm |
| Water Resistance | Water Penetration | Gear suffix, Water skill tree arm |
| Lightning Resistance | Lightning Penetration | Gear suffix, Lightning skill tree arm |
| Earth Resistance | Earth Penetration | Gear suffix, Earth skill tree arm (currently bugged) |
| Wind Resistance | Wind Penetration | Gear suffix, Wind skill tree arm (currently bugged) |
| Void Resistance | Void Penetration | Gear suffix, Void skill tree arm |
| Ailment Threshold | Higher hit damage, status chance | Elemental attribute investment, gear |
| Knockback Resistance | (No direct counter) | Displacement is a utility, not a damage mechanic |
| HP (tankiness) | All damage stats | The entire offensive build |
