# Hytale Native Status Effect System — Definitive Reference

> **Source**: Decompiled Hytale server source (all core classes read in full), 123 vanilla JSON assets cataloged, client UI definitions, 54 vendor mods + 6 external plugins analyzed, our own implementations verified.
> **Date**: 2026-05-16
> **Status**: Final pass — verified against live build, cross-referenced with client data and vendor patterns.

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Server-Side Classes](#2-server-side-classes)
3. [JSON Asset Schema](#3-json-asset-schema)
4. [ApplicationEffects — Visual/Audio Properties](#4-applicationeffects--visualaudio-properties)
5. [StatusEffectIcon — Client HUD Rendering](#5-statuseffecticon--client-hud-rendering)
6. [OverlapBehavior — Stacking Rules](#6-overlapbehavior--stacking-rules)
7. [RemovalBehavior — Expiry Rules](#7-removalbehavior--expiry-rules)
8. [Damage Over Time (DOT)](#8-damage-over-time-dot)
9. [Stat Modifiers](#9-stat-modifiers)
10. [Movement & Ability Control](#10-movement--ability-control)
11. [Model Override](#11-model-override)
12. [Damage Resistance](#12-damage-resistance)
13. [Network Protocol](#13-network-protocol)
14. [Ticking & Lifecycle Internals](#14-ticking--lifecycle-internals)
15. [Programmatic Creation (No JSON)](#15-programmatic-creation-no-json)
16. [Registration & Asset Store](#16-registration--asset-store)
17. [Our Existing Usage](#17-our-existing-usage)
18. [Vanilla Effect Catalog](#18-vanilla-effect-catalog)
19. [Icon Specifications & Client Rendering Details](#19-icon-specifications--client-rendering-details)
20. [Vendor Mod Patterns & Best Practices](#20-vendor-mod-patterns--best-practices)
21. [Integration Surface for New Effects](#21-integration-surface-for-new-effects)
22. [Constraints & Gotchas](#22-constraints--gotchas)

---

## 1. Architecture Overview

```
                         EFFECT LIFECYCLE
                         ================

  Plugin Init                    Runtime                       Client
  ──────────                     ───────                       ──────
  RPGEntityEffect()         effectController.addEffect()     EntityEffectUpdate packet
       │                         │                                │
       ▼                         ▼                                ▼
  EntityEffect.getAssetStore()   ActiveEntityEffect created   StatusEffect.ui renders:
  .loadAssets(packKey, list)     → ticks via LivingEntity-    → 48×48 container
       │                           EffectSystem               → buff/debuff background
       ▼                         → decrements duration        → 34×34 icon PNG
  Effect registered              → fires DOT damage           → circular countdown bar
  in asset map with index        → applies stat modifiers     → arrow indicator (up/down)
                                 → applies visuals            → tooltip on hover
                                 │                            → sound on apply/expire
                                 ▼
                            EffectControllerSystem detects
                            isNetworkOutdated = true
                                 │
                                 ▼
                            EntityEffectUpdate[] consumed
                            → ComponentUpdate(EntityEffects)
                            → EntityViewer.queueUpdate()
                            → SendPackets batches to client
                                 │
                                 ▼
                            effectController.removeEffect()
                            → queues Remove update
                            → client removes icon + plays sound
```

**Key classes** (all `com.hypixel.hytale`):

| Class | Package | Role |
|-------|---------|------|
| `EntityEffect` | `server.core.asset.type.entityeffect.config` | Asset-side definition (JSON-loadable) |
| `EntityEffect` | `protocol` | Network-serializable effect data (sent to client) |
| `EffectControllerComponent` | `server.core.entity.effect` | ECS component — API to add/remove effects on entities |
| `ActiveEntityEffect` | `server.core.entity.effect` | Tracks a live effect instance (duration, DOT timer, state) |
| `ApplicationEffects` | `protocol` + `config` | Visual/audio/movement properties (both protocol and config versions) |
| `EntityEffectUpdate` | `protocol` | Packet sent to client (Add or Remove) |
| `OverlapBehavior` | `protocol` + `config` | Enum: Extend, Overwrite, Ignore |
| `RemovalBehavior` | `config` | Enum: COMPLETE, INFINITE, DURATION |
| `LivingEntityEffectSystem` | `server.core.modules.entity.livingentity` | ECS TickingSystem — ticks all effects, fires DOT, handles expiry |
| `EffectControllerSystem` | `server.core.modules.entity.tracker` (inner class in EntityTrackerSystems) | Network sync — distributes updates to viewers |
| `LivingEntityEffectClearChangesSystem` | `server.core.modules.entity.livingentity` | Clears change delta list after sync |

---

## 2. Server-Side Classes

### EntityEffect (Config — `server.core.asset.type.entityeffect.config`)

The full asset definition loaded from JSON. All fields `protected` (accessible via our `RPGEntityEffect` subclass).

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `id` | String | (constructor) | Unique effect identifier |
| `name` | String | null | Localization key for tooltip (e.g., `"server.effects.burn"`) |
| `locale` | String | null | Death message localization key (e.g., `"burn"`) |
| `applicationEffects` | ApplicationEffects | null | Visual/audio/movement properties |
| `duration` | float | 0.0 | Default duration in seconds |
| `infinite` | boolean | false | If true, effect never expires |
| `debuff` | boolean | false | If true, shown with debuff styling (red frame, down arrow) |
| `statusEffectIcon` | String | null | Path to icon PNG (relative to asset root) |
| `overlapBehavior` | OverlapBehavior | IGNORE | How to handle re-application |
| `removalBehavior` | RemovalBehavior | COMPLETE | What happens on removal |
| `invulnerable` | boolean | false | Makes entity invulnerable while active |
| `deathMessageKey` | String | null | Localization key for DOT death message |
| `damageCalculator` | DamageCalculator | null | DOT damage configuration |
| `damageCalculatorCooldown` | float | 0.0 | Seconds between DOT ticks |
| `damageEffects` | DamageEffects | null | Visuals/sounds when DOT ticks |
| `statModifiers` | Int2ObjectMap<StaticModifier[]> | null | Complex stat modifications (by stat ID) |
| `entityStats` | Int2FloatMap | null | Simple stat modifiers (stat ID → float) |
| `valueType` | ValueType | ABSOLUTE | How stat modifiers are interpreted |
| `statModifierEffects` | DamageEffects | null | Visuals when stats are applied |
| `modelOverride` | ModelOverride | null | Model swap while active |
| `modelChange` | String | null | Simple model asset swap (e.g., "Corgi") |
| `damageResistanceValues` | Map<DamageCause, StaticModifier[]> | null | Damage resistance by cause |
| `worldRemovalSoundEventId` | String | null | Sound played to all when effect ends |
| `localRemovalSoundEventId` | String | null | Sound played to affected player when effect ends |
| `cachedPacket` | SoftReference<protocol.EntityEffect> | null | Cached protocol packet (GC-able) |

**Static API:**
- `EntityEffect.getAssetStore()` — Returns the indexed asset store for registration
- `EntityEffect.getAssetMap()` — Returns the lookup table for runtime queries
- `EntityEffect.getAssetMap().getAsset(id)` — Get effect by string ID
- `EntityEffect.getAssetMap().getIndex(id)` — Get integer index (returns `Integer.MIN_VALUE` if not found)

### ActiveEntityEffect (`server.core.entity.effect`)

Tracks a single active effect instance on an entity.

| Field | Type | Description |
|-------|------|-------------|
| `entityEffectId` | String | Effect definition ID |
| `entityEffectIndex` | int | Cached asset map index for fast lookups |
| `initialDuration` | float | Original duration when applied |
| `remainingDuration` | float | Current seconds remaining (decremented each tick) |
| `infinite` | boolean | Whether this instance never expires |
| `debuff` | boolean | Inherited from definition |
| `statusEffectIcon` | String | Icon path (from definition, sent to client) |
| `invulnerable` | boolean | Whether this instance grants invulnerability |
| `sinceLastDamage` | float | Accumulator for DOT tick timing |
| `hasBeenDamaged` | boolean | Flag for zero-cooldown effects (fire once) |
| `sequentialHits` | DamageCalculatorSystems.Sequence | Combo tracking for sequential damage scaling |

**Constructor overloads:**
```java
// Timed effect
ActiveEntityEffect(id, index, duration, debuff, statusEffectIcon, invulnerable)
  → initialDuration = remainingDuration = duration

// Infinite effect
ActiveEntityEffect(id, index, infinite=true, invulnerable)
  → duration = 1.0f, infinite = true
```

### EffectControllerComponent (`server.core.entity.effect`)

The ECS component that manages all active effects on an entity.

| Field | Type | Description |
|-------|------|-------------|
| `activeEffects` | Int2ObjectMap<ActiveEntityEffect> | Effects by asset index |
| `cachedActiveEffectIndexes` | int[] | Cached array for fast iteration (invalidated on add/remove) |
| `changes` | ObjectList<EntityEffectUpdate> | Delta updates since last network send |
| `isNetworkOutdated` | boolean | Set when changes exist, consumed by EffectControllerSystem |
| `originalModel` | Model | Saved model for restoration after ModelOverride |
| `activeModelChangeEntityEffectIndex` | int | Which effect changed the model (only ONE allowed) |
| `isInvulnerable` | boolean | True if ANY active effect has invulnerable=true |

---

## 3. JSON Asset Schema

**Location**: `Server/Entity/Effects/<Category>/<EffectName>.json`

**Vanilla categories**: `BlockPlacement/`, `Damage/`, `Deployables/`, `Drop/`, `Food/Boost/`, `Food/Buff/`, `Food/Deprecated/`, `Food/Restore/`, `GameMode/`, `Mana/`, `Movement/`, `Npc/`, `Portals/`, `Potion/`, `Projectiles/`, `Stamina/`, `Status/`, `Tests/`, `Weapons/`

**Complete schema** (all fields optional):

```json
{
  "Name": "server.effects.burn",
  "Duration": 5.0,
  "Infinite": false,
  "Debuff": true,
  "Invulnerable": false,
  "OverlapBehavior": "Overwrite",
  "RemovalBehavior": "Complete",
  "StatusEffectIcon": "UI/StatusEffects/Burn.png",
  "Locale": "server.general.deathCause.burn",

  "ApplicationEffects": {
    "EntityTopTint": "#RRGGBB",
    "EntityBottomTint": "#RRGGBB",
    "EntityAnimationId": "animation_id",
    "ScreenEffect": "ScreenEffects/Fire.png",
    "ModelVFXId": "Burn",
    "HorizontalSpeedMultiplier": 1.0,
    "KnockbackMultiplier": 1.0,
    "WorldSoundEventId": "SFX_xxx",
    "LocalSoundEventId": "SFX_xxx",
    "WorldRemovalSoundEventId": "SFX_xxx",
    "LocalRemovalSoundEventId": "SFX_xxx",
    "MouseSensitivityAdjustmentTarget": 0.25,
    "MouseSensitivityAdjustmentDuration": 0.1,
    "Particles": [
      {
        "SystemId": "Effect_Fire",
        "TargetEntityPart": "Entity",
        "TargetNodeName": "Head",
        "Color": "#RRGGBB",
        "Scale": 1.0,
        "PositionOffset": { "X": 0, "Y": 0, "Z": 0 },
        "RotationOffset": {},
        "DetachedFromModel": false
      }
    ],
    "FirstPersonParticles": [ /* same format */ ],
    "MovementEffects": {
      "DisableAll": true,
      "DisableForward": false,
      "DisableBackward": false,
      "DisableLeft": false,
      "DisableRight": false,
      "DisableSprint": false,
      "DisableJump": false,
      "DisableCrouch": false
    },
    "AbilityEffects": {
      "Disabled": ["Primary", "Secondary", "Ability1", "Ability2", "Ability3"]
    }
  },

  "DamageCalculator": {
    "Type": "Absolute",
    "Class": "Unknown",
    "BaseDamage": { "Fire": 10 },
    "RandomPercentageModifier": 0.1,
    "SequentialModifierStep": 0,
    "SequentialModifierMinimum": 0
  },
  "DamageCalculatorCooldown": 1.0,
  "DamageEffects": {
    "WorldSoundEventId": "SFX_xxx",
    "PlayerSoundEventId": "SFX_xxx",
    "ModelParticles": [],
    "WorldParticles": [],
    "Knockback": {},
    "CameraEffect": "CameraEffect_Name",
    "StaminaDrainMultiplier": 1.0
  },

  "StatModifiers": { "Health": 2 },
  "RawStatModifiers": {
    "Health": [
      { "Amount": 30, "CalculationType": "Additive", "Target": "Max" }
    ]
  },
  "ValueType": "Absolute",
  "StatModifierEffects": {
    "WorldParticles": [{ "SystemId": "Potion_Health_Implosion", "PositionOffset": { "Y": 1.0 } }],
    "WorldSoundEventId": "SFX_xxx"
  },

  "DamageResistance": {
    "Fire": [{ "Amount": 1.0, "CalculationType": "Multiplicative" }]
  },

  "ModelChange": "Corgi",
  "ModelOverride": {
    "Model": "VFX/Spells/Roots/Model.blockymodel",
    "Texture": "VFX/Spells/Roots/Model.png",
    "AnimationSets": {
      "Spawn": {
        "Animations": [
          { "Animation": "VFX/Spells/Roots/Spawn.blockyanim", "Looping": false }
        ]
      }
    }
  }
}
```

---

## 4. ApplicationEffects — Visual/Audio Properties

| Field | Type | Description | Example |
|-------|------|-------------|---------|
| `EntityTopTint` | Hex color | Tint color for upper body | `"#cf2302"` (fire red) |
| `EntityBottomTint` | Hex color | Tint color for lower body | `"#100600"` (dark) |
| `EntityAnimationId` | String | Animation to play on entity | `"Hurt"`, `"DashLeft"` |
| `ScreenEffect` | String | Full-screen overlay PNG path | `"ScreenEffects/Fire.png"` |
| `ModelVFXId` | String | VFX effect on entity model | `"Burn"`, `"Freeze"`, `"Creative"` |
| `HorizontalSpeedMultiplier` | float | Movement speed modifier (1.0 = normal) | `0.5` = half speed |
| `KnockbackMultiplier` | float | Knockback received modifier | `0` = immovable |
| `WorldSoundEventId` | String | Sound heard by all nearby | `"SFX_Effect_Burn_World"` |
| `LocalSoundEventId` | String | Sound heard only by target | `"SFX_Effect_Burn_Local"` |
| `WorldRemovalSoundEventId` | String | Sound when effect ends (all) | |
| `LocalRemovalSoundEventId` | String | Sound when effect ends (target) | |
| `MouseSensitivityAdjustmentTarget` | float | Target mouse sensitivity (0-1) | `0.25` = very slow aim |
| `MouseSensitivityAdjustmentDuration` | float | Time to reach target sensitivity | `0.1` = fast transition |
| `Particles` | Array | World-visible particle emitters | See below |
| `FirstPersonParticles` | Array | First-person-only particles | Same format as Particles |
| `MovementEffects` | Object | Movement input restrictions | See §10 |
| `AbilityEffects` | Object | Ability disabling | See §10 |

**Particle entry format:**
```json
{
  "SystemId": "Effect_Fire",
  "TargetEntityPart": "Entity",
  "TargetNodeName": "Head",
  "Color": "#ff0000",
  "Scale": 1.0,
  "PositionOffset": { "X": 0, "Y": 2.5, "Z": 0 },
  "RotationOffset": {},
  "DetachedFromModel": false
}
```
- `SystemId` — Required. References a `.particlesystem` asset.
- `TargetEntityPart` — Required. Where to attach: `"Entity"` (center), `"Self"`, `"LeftHand"`, `"RightHand"`.
- `TargetNodeName` — Optional. Bone name: `"Head"`, `"Pelvis"`, etc.
- `Color` — Optional. Tint color for particles.
- `Scale` — Optional. Size multiplier (default 1.0).
- `PositionOffset` — Optional. Offset from attach point.
- `DetachedFromModel` — Optional. If true, particles spawn in world space (don't follow entity).

**Known vanilla ScreenEffects**: `Fire.png`, `Snow.png`, `Poison.png`, `Poison_T1.png`, `Immune.png`, `Daggers.png`, `Sand.png`, `Water.png`

**Known vanilla ModelVFXIds**: `Burn`, `Freeze`, `Creative`, `Intangible_Dark`, `Drop_Legendary`, `PrototypeBlockPlaceSuccess`

**Known vanilla particle SystemIds**: `Effect_Fire`, `Effect_Snow`, `Effect_Snow_Impact`, `Effect_Poison`, `Stunned`, `Creative_Switch`, `Potion_Health_Heal`, `Potion_Health_Implosion`, `Potion_Morph_Burst`, `Drop_Uncommon`, `Drop_Rare`, `Drop_Epic`, `Drop_Legendary`, `Daggers_Dash_Straight`, `Daggers_Dash_Status`, `Fire_Projectile`

---

## 5. StatusEffectIcon — Client HUD Rendering

### Path Format

- **In JSON/code**: `"UI/StatusEffects/Burn.png"` (relative to asset root, no `Common/` prefix)
- **Physical location in JAR**: `Common/UI/StatusEffects/Burn.png`
- **If null**: No icon shown. Effect still applies all gameplay mechanics — just invisible in status bar.

### Client-Side HUD Structure

The client renders status icons in `StatusEffect.ui` with this hierarchy:

```
StatusEffectsHudContainer (above hotbar)
├─ BuffsContainer (layout: Right) — buffs grouped here
└─ DebuffsContainer (layout: Right) — debuffs grouped here

Each StatusEffect icon (48×48px):
├─ StatusEffectArrows
│  ├─ ArrowBuff (14×9px, top) — visible for buffs (green up-arrow)
│  ├─ ArrowDebuff (14×9px, bottom) — visible for debuffs (red down-arrow)
│  └─ ArrowBuffDisabled (14×9px, top) — visible for disabled buffs
├─ StatusEffectIcon (34×34px, centered)
│  └─ Background = loaded PNG from statusEffectIcon path
├─ CooldownContainer (34×34px)
│  └─ Circular ProgressBar (ring overlay)
│     ├─ Background: BackgroundCooldownDisabled.png (stationary ring)
│     ├─ Fill: LineCooldownDisabled.png (animated, 2px thick)
│     ├─ Direction: Start (fills clockwise from bottom)
│     └─ Value: remainingTime / initialDuration (0→1)
└─ StatusEffectProgressBarsContainer (for stacking indicators)
```

### Visual Styling

| State | Background | Arrow | Frame Color |
|-------|-----------|-------|-------------|
| **Buff** (`debuff: false`) | `BuffBackground.png` | ArrowBuff (up, green) | Neutral/green |
| **Debuff** (`debuff: true`) | `DebuffBackground.png` | ArrowDebuff (down, red) | Red |
| **Disabled** | `DisabledBackground.png` | ArrowBuffDisabled | Grey |

### Countdown Display

- **Infinite effects** (`infinite: true`): No progress bar, no countdown, no expiry sound.
- **Timed effects** (`infinite: false`): Circular ring progress bar animates in real-time. `Value = remainingTime / initialDuration`.
- When `remainingTime ≤ 0`, effect is removed and icon disappears.

### Client Sounds

| Event | Sound File | Volume |
|-------|-----------|--------|
| Buff applied | `Sounds/TrinketBuff.ogg` | -14 dB |
| Debuff applied | `Sounds/TrinketDebuff.ogg` | -14 dB |
| Effect about to expire | `Sounds/TrinketElapsed.ogg` | -14 dB |
| Cooldown completed | `Sounds/TrinketReactivate.ogg` | -14 dB |

### Tooltip

Hovering over an icon shows a tooltip with the effect's `name` field (localization key resolved to display text). Font size: 14pt.

### Maximum Icons

- **Container width**: ~700px
- **Icon + spacing**: 48px + 4px margin = 52px per icon
- **Max visible**: ~13 icons before overflow
- Buffs and debuffs rendered in **separate containers** (both above hotbar)

### Icon Loading

- Client receives `statusEffectIcon` path in `EntityEffectUpdate` packet
- Loads PNG asynchronously from asset pack
- Caches per path during gameplay session
- If PNG is missing → icon fails to render (blank/default)

---

## 6. OverlapBehavior — Stacking Rules

```java
// com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior
public enum OverlapBehavior {
    EXTEND,     // Add duration to existing: remainingDuration += newDuration
    OVERWRITE,  // Replace existing effect completely (implicit switch fallthrough → put())
    IGNORE      // Do nothing if already active (return true)
}
```

**Implementation detail (verified in decompiled code):**
The `addEffect()` method uses a switch on OverlapBehavior:
- `EXTEND`: Explicitly handled — adds duration, queues update, returns.
- `IGNORE`: Explicitly handled — returns true immediately.
- `OVERWRITE`: **Implicit fallthrough** — the switch exits without matching, execution falls to `this.activeEffects.put(index, newEffect)` which replaces the existing entry. This is correct behavior — confirmed used by Hytale's own `PlayerEffectApplyCommand` and `EntityEffectCommand`.

| Behavior | When to use | Vanilla example |
|----------|-------------|-----------------|
| **EXTEND** | Stackable durations (reapply adds time) | `Poison.json`, `Red_Flash.json`, `Mana.json`, `Drop_*.json` |
| **OVERWRITE** | Refreshable effects (reset timer on reapply) | `Burn.json`, Food buffs, Potions, Healing Totem |
| **IGNORE** | One-shot effects (apply once) | Default, Mob modifier tints, infinite auras |

**Our pattern**: We use OVERWRITE for all ailments (burn, freeze, shock, poison) because our RPG system manages stacks independently — the native effect is just the visual layer.

---

## 7. RemovalBehavior — Expiry Rules

```java
// com.hypixel.hytale.server.core.asset.type.entityeffect.config.RemovalBehavior
public enum RemovalBehavior {
    COMPLETE,   // Fully remove effect, queue Remove update, restore model
    INFINITE,   // Convert infinite effect to timed (sets infinite=false, keeps remaining duration)
    DURATION    // Set remainingDuration=0 (removes on next tick)
}
```

**Implementation detail:**
- `COMPLETE`: Removes from `activeEffects` map, resets model if applicable, queues `EffectOp.Remove` update.
- `INFINITE`: Sets `infinite = false` on the ActiveEntityEffect. Duration then counts down normally.
- `DURATION`: Sets `remainingDuration = 0.0f`. Next tick of `LivingEntityEffectSystem` detects `remaining <= 0` and removes it.

---

## 8. Damage Over Time (DOT)

Effects can deal periodic damage without Java code via JSON:

```json
{
  "DamageCalculator": {
    "Type": "Absolute",
    "BaseDamage": { "Fire": 10 },
    "RandomPercentageModifier": 0.1,
    "SequentialModifierStep": 0,
    "SequentialModifierMinimum": 0
  },
  "DamageCalculatorCooldown": 1.0,
  "DamageEffects": {
    "WorldSoundEventId": "SFX_Effect_Burn_World",
    "PlayerSoundEventId": "SFX_Effect_Burn_Local"
  }
}
```

### DOT Tick Logic (from `ActiveEntityEffect.tick()`)

```
if (damageCalculatorCooldown > 0):
    cycles = floor((sinceLastDamage + dt) / damageCalculatorCooldown)
    sinceLastDamage = (sinceLastDamage + dt) % damageCalculatorCooldown
    → fire tickDamage() `cycles` times
else if (!hasBeenDamaged):
    cycles = 1    // Zero cooldown = fire exactly once at application
    hasBeenDamaged = true
```

### DamageCalculator Fields

| Field | Type | Description |
|-------|------|-------------|
| `Type` | enum | `"Absolute"` (flat) or `"Percent"` (% of max health) |
| `Class` | enum | Damage class for modifier application |
| `BaseDamage` | map | Damage amounts keyed by DamageCause name (`"Fire"`, `"Poison"`, etc.) |
| `RandomPercentageModifier` | float | Random variance (±10% for 0.1) |
| `SequentialModifierStep` | float | Damage change per consecutive tick |
| `SequentialModifierMinimum` | float | Minimum damage after sequential reduction |

**Note**: Our RPG system handles ailment damage in Java (via `AilmentCalculator` / `RpgBurnTickSystem` / `RpgPoisonTickSystem`), so we do NOT use native DOT. Our native effects are visual-only.

---

## 9. Stat Modifiers

Effects can modify entity stats automatically while active.

### Simple Format (flat value)
```json
{ "StatModifiers": { "Health": 2, "MaxHealth": 50 } }
```

### Advanced Format (with calculation type)
```json
{
  "RawStatModifiers": {
    "Health": [
      { "Amount": 30, "CalculationType": "Additive", "Target": "Max" }
    ]
  },
  "ValueType": "Percent"
}
```

### Fields

| Field | Value | Description |
|-------|-------|-------------|
| `ValueType` | `"Absolute"` or `"Percent"` | How modifiers are interpreted |
| `CalculationType` | `"Additive"` | Modifier calculation (confirmed in vanilla) |
| `Target` | `"Max"` or `"Current"` | Which stat value to modify |

### Stat Modifier Effects (Visual Feedback)
```json
{
  "StatModifierEffects": {
    "WorldParticles": [{ "SystemId": "Potion_Health_Implosion", "PositionOffset": { "Y": 1.0 } }],
    "WorldSoundEventId": "SFX_Deployable_Totem_Heal_Despawn"
  }
}
```

**Timing**: Stat modifiers don't apply instantly — they fire on the `damageCalculatorCooldown` cadence, same as DOT. Zero cooldown = fire once at application.

**Note**: Our RPG system handles all stat logic in Java. We don't use native stat modifiers on custom effects.

---

## 10. Movement & Ability Control

### Full Immobilization
```json
{ "ApplicationEffects": { "MovementEffects": { "DisableAll": true } } }
```
`DisableAll: true` automatically sets all individual flags (`disableForward`, `disableBackward`, `disableLeft`, `disableRight`, `disableSprint`, `disableJump`, `disableCrouch`).

### Granular Movement Control
```json
{
  "ApplicationEffects": {
    "MovementEffects": {
      "DisableSprint": true,
      "DisableJump": true
    }
  }
}
```
Individual flags: `DisableForward`, `DisableBackward`, `DisableLeft`, `DisableRight`, `DisableSprint`, `DisableJump`, `DisableCrouch`.

### Speed Reduction
```json
{ "ApplicationEffects": { "HorizontalSpeedMultiplier": 0.5 } }
```

### Ability Lockout
```json
{
  "ApplicationEffects": {
    "AbilityEffects": { "Disabled": ["Primary", "Secondary", "Ability1", "Ability2", "Ability3"] }
  }
}
```
Ability types: `Primary` (LMB), `Secondary` (RMB), `Ability1`, `Ability2`, `Ability3`.

### Knockback Immunity
```json
{ "ApplicationEffects": { "KnockbackMultiplier": 0 } }
```

### Mouse Sensitivity Override
```json
{
  "ApplicationEffects": {
    "MouseSensitivityAdjustmentTarget": 0.25,
    "MouseSensitivityAdjustmentDuration": 0.1
  }
}
```
Used by Battleaxe_Downstrike_Jump for recoil camera effect.

---

## 11. Model Override

### Simple Model Change
```json
{ "ModelChange": "Corgi" }
```
Swaps the entity's model to a named model asset. Used by Morph potions (Corgi, Frog, Mouse, Pigeon, Mosshorn).

### Complex Model Override (with animations)
```json
{
  "ModelOverride": {
    "Model": "VFX/Spells/Roots/Model.blockymodel",
    "Texture": "VFX/Spells/Roots/Model.png",
    "AnimationSets": {
      "Spawn": { "Animations": [{ "Animation": "VFX/Spells/Roots/Spawn.blockyanim", "Looping": false }] },
      "Despawn": { "Animations": [{ "Animation": "VFX/Spells/Roots/Despawn.blockyanim", "Looping": false }] }
    }
  }
}
```

**Constraint**: Only ONE model override can be active at a time. If multiple effects try to override, the first one "wins". The original model is saved in `EffectControllerComponent.originalModel` and restored when the overriding effect is removed.

---

## 12. Damage Resistance

Effects can grant damage resistance while active:

```json
{
  "DamageResistance": {
    "Fire": [{ "Amount": 1.0, "CalculationType": "Multiplicative" }]
  }
}
```

- `Amount: 1.0` with `Multiplicative` = 100% damage reduction (immunity)
- Keys are `DamageCause` names
- Applied as `StaticModifier[]` per damage type

---

## 13. Network Protocol

### EntityEffectUpdate Packet

```java
// com.hypixel.hytale.protocol.EntityEffectUpdate
public class EntityEffectUpdate {
    public EffectOp type;           // Add(0) or Remove(1)  — 1 byte
    public int id;                  // Effect asset index    — 4 bytes LE
    public float remainingTime;     // Seconds remaining     — 4 bytes LE
    public boolean infinite;        // Never expires?        — 1 byte
    public boolean debuff;          // Debuff styling?       — 1 byte
    public String statusEffectIcon; // PNG path (nullable)   — VarString
}
```

**Serialization:**
- Fixed block: 11 bytes (type + id + remainingTime + infinite + debuff)
- Null bit field: 1 byte (bit 0 = statusEffectIcon present)
- If `statusEffectIcon` is null, the string field is omitted entirely
- Total size: 12 bytes (no icon) to 12 + string length (with icon)

### Protocol EntityEffect (Definition Packet)

Sent to client on first view of an entity with effects. Contains all static properties:

- Fixed block: 25 bytes (worldRemovalSoundEventIndex, localRemovalSoundEventIndex, duration, infinite, debuff, overlapBehavior, damageCalculatorCooldown, valueType)
- Variable fields (6 nullable): id, name, applicationEffects, modelOverride, statusEffectIcon, statModifiers
- Null bit field: 1 byte (6 bits for nullable fields)

### Sync Pipeline

```
1. EffectControllerComponent.addEffect() / removeEffect()
   → Queues EntityEffectUpdate in `changes` list
   → Sets `isNetworkOutdated = true`

2. EffectControllerSystem.tick() (in QUEUE_UPDATE_GROUP)
   → Detects `consumeNetworkOutdated() == true`
   → Calls `consumeChanges()` to get delta array
   → Wraps in ComponentUpdate(type=EntityEffects, updates=[...])
   → EntityViewer.queueUpdate(entity, componentUpdate)

3. For newly visible entities:
   → Calls `createInitUpdates()` (creates Add for ALL active effects)
   → Sends full state to new viewer

4. LivingEntityEffectClearChangesSystem.tick() (AFTER EffectControllerSystem)
   → Calls clearChanges() to reset delta list

5. SendPackets system
   → Batches all ComponentUpdates into EntityUpdates packet
   → Sends to client
```

---

## 14. Ticking & Lifecycle Internals

### LivingEntityEffectSystem (EntityTickingSystem)

Called every game tick for entities with `EffectControllerComponent`:

```
for each ActiveEntityEffect in activeEffects:
    1. canApplyEffect() check:
       - Burn effect: iterates entity bounding box blocks, returns false if in Fluid_Water
       - All other effects: always returns true

    2. activeEffect.tick(commandBuffer, ref, entityEffect, statMap, dt):
       - DOT damage: calculates cycles from sinceLastDamage accumulator
       - Stat modifiers: applied on same cooldown cadence
       - Duration: if not infinite, remainingDuration -= dt

    3. Expiry check: if (!infinite && remainingDuration <= 0):
       - Remove effect from map
       - Queue Remove update
       - Reset model if applicable

    4. Invulnerability tracking: if any effect has invulnerable=true, entity is invulnerable
```

### Burn Water Check (Hardcoded)

The Burn effect has a special `canApplyEffect()` check in `LivingEntityEffectSystem`:
- Iterates through all blocks in the entity's bounding box
- If ANY block is `Fluid_Water` → Burn effect is suppressed (removed if active, prevented if being applied)
- This is the ONLY hardcoded effect-specific check in the ticking system

### Effect Lifetime Flow

```
Apply:
  addEffect() → creates ActiveEntityEffect → queues Add update → triggers stat recalc

Each Tick:
  LivingEntityEffectSystem.tick() → activeEffect.tick() → decrements duration / fires DOT

Sync:
  EffectControllerSystem.tick() → consumes changes → sends to viewers

Expire:
  remainingDuration <= 0 → remove from map → queue Remove update → restore model → stat recalc

Manual Remove:
  removeEffect() → respects RemovalBehavior → queues Remove update → restore model → stat recalc
```

---

## 15. Programmatic Creation (No JSON)

Our mod creates effects in Java using `RPGEntityEffect` (extends `EntityEffect`):

```java
// Our helper class: io.github.larsonix.trailoforbis.mobs.speed.RPGEntityEffect
RPGEntityEffect effect = new RPGEntityEffect("rpg_my_effect");

// Visual properties via RPGApplicationEffects builder
RPGApplicationEffects appEffects = RPGApplicationEffects.create()
    .withTint(
        RPGApplicationEffects.colorFromHex("#100600"),  // bottom
        RPGApplicationEffects.colorFromHex("#cf2302"))  // top
    .withParticles(RPGApplicationEffects.particle("Effect_Fire"))
    .withScreenEffect("ScreenEffects/Fire.png")
    .withModelVFX("Burn")
    .withSounds("SFX_Effect_Burn_World", "SFX_Effect_Burn_Local")
    .withSpeed(0.7f)       // 30% slow
    .withKnockback(0.0f);  // immovable
appEffects.resolveSoundIndices();  // MUST call after setting sounds

// Configure the effect
effect.setApplicationEffects(appEffects);
effect.setInfinite(false);
effect.setDuration(5.0f);
effect.setOverlapBehavior(OverlapBehavior.OVERWRITE);
effect.setDebuff(true);
effect.setStatusEffectIcon("UI/StatusEffects/MyIcon.png");  // null = no HUD icon
effect.setName("server.effects.myEffect");                   // null = no tooltip
effect.setDeathMessageKey("server.general.deathCause.myEffect"); // null = no death msg
effect.setRemovalBehavior(RemovalBehavior.COMPLETE);
```

**Available setters on RPGEntityEffect**:
- `setApplicationEffects(RPGApplicationEffects)`
- `setInfinite(boolean)`
- `setDuration(float)`
- `setOverlapBehavior(OverlapBehavior)`
- `setName(String)` — localization key for tooltip, null to hide
- `setDebuff(boolean)`
- `setStatusEffectIcon(String)` — PNG path, null for no icon
- `setDeathMessageKey(String)`
- `setRemovalBehavior(RemovalBehavior)`

**Available builder methods on RPGApplicationEffects**:
- `RPGApplicationEffects.create()` — factory (speed=1.0, knockback=1.0)
- `.withTint(Color bottom, Color top)` — entity gradient tint
- `.withParticles(ModelParticle...)` — attached particles
- `.withScreenEffect(String path)` — screen overlay
- `.withModelVFX(String id)` — model VFX
- `.withSounds(String world, String local)` — sound events
- `.withSpeed(float multiplier)` — movement speed
- `.withKnockback(float multiplier)` — knockback received
- `.resolveSoundIndices()` — **MUST call** after setting sounds
- `RPGApplicationEffects.colorFromHex(String hex)` — hex→Color utility
- `RPGApplicationEffects.particle(String systemId)` — ModelParticle factory

---

## 16. Registration & Asset Store

Effects MUST be registered before use. **Registration MUST happen during plugin init, NEVER during a world tick** (deadlocks with asset infrastructure — see `feedback_loadassets_world_tick_deadlock.md`).

```java
// Collect effects during creation
List<EntityEffect> pendingEffects = new ArrayList<>();
pendingEffects.add(burnEffect);
pendingEffects.add(shockEffect);

// Register all at once
EntityEffect.getAssetStore().loadAssets("trailoforbis", pendingEffects);

// Look up index (needed for removeEffect and addInfiniteEffect)
int effectIndex = EntityEffect.getAssetMap().getIndex("rpg_ailment_burn");
if (effectIndex == Integer.MIN_VALUE) {
    // Effect not registered — error!
}
```

**Vendor pattern — volatile caching for repeated lookups:**
```java
private static volatile EntityEffect cachedBurnEffect;

// In handler:
EntityEffect burn = cachedBurnEffect;
if (burn == null) {
    cachedBurnEffect = burn = EntityEffect.getAssetMap().getAsset("rpg_ailment_burn");
}
```

---

## 17. Our Existing Usage

### AilmentEffectManager (`ailments/AilmentEffectManager.java`)
4 ailment types with visual effects:

| Ailment | Effect ID | Icon | Tints | Screen | VFX | Particles | Speed |
|---------|-----------|------|-------|--------|-----|-----------|-------|
| **BURN** | `rpg_ailment_burn` | `UI/StatusEffects/Burn.png` | `#100600`/`#cf2302` | `Fire.png` | `Burn` | `Effect_Fire` | - |
| **SHOCK** | `rpg_ailment_shock` | `Icons/ItemsGenerated/Ingredient_Lightning_Essence.png` | `#1a1200`/`#ffee44` | - | - | - | - |
| **POISON** | `rpg_ailment_poison` | `UI/StatusEffects/Poison.png` | `#000000`/`#008000` | - | - | `Effect_Poison` | - |
| **FREEZE** | `rpg_ailment_freeze_N` | `Icons/ItemsGenerated/Ingredient_Ice_Essence.png` | `#80ecff`/`#da72ff` | `Snow.png` | `Freeze` | `Effect_Snow`, `Effect_Snow_Impact` | 0.70-0.95 |

### MobModifierEffectRegistry (`mobs/modifiers/MobModifierEffectRegistry.java`)
Per-modifier-type infinite visual effects (tints, VFX, speed). All have `statusEffectIcon = null` (internal, no HUD display). Use `OverlapBehavior.IGNORE` (applied once at spawn).

### MobSpeedEffectManager (`mobs/speed/MobSpeedEffectManager.java`)
Speed scaling effects for mob movement — invisible, no icon, no tints.

### ConditionalEffectTracker (`skilltree/conditional/ConditionalEffectTracker.java`)
Skill tree conditional buffs — could potentially show status icons.

### Combat Effects (`combat/effects/impl/`)
- `LifeStreamEffect` — healing over time proc
- `LivingFortressEffect` — damage reduction proc
- Could benefit from status icons to show active state.

---

## 18. Vanilla Effect Catalog

### Summary Statistics
- **Total Effect JSONs**: 123
- **With StatusEffectIcon**: 49 (40%)
- **Unique Icon Paths**: 28
- **Status Effect PNGs**: 17+ (in `Common/UI/StatusEffects/`)
- **Screen Effect PNGs**: 8 (in `Common/ScreenEffects/`)

### Status Ailments (`Server/Entity/Effects/Status/`)

| Effect | Duration | Debuff | DOT | Speed | Movement | Abilities | Icon |
|--------|----------|--------|-----|-------|----------|-----------|------|
| **Burn** | 5s | true | Fire 10/1s | - | - | - | `Burn.png` |
| **Poison** | 16s | true | Poison 10/5s | - | - | - | `Poison.png` |
| **Poison_T1** | 16s | true | Poison 6/5s | - | - | - | (parent: Poison) |
| **Poison_T2** | 16s | true | Poison 12/5s | - | - | - | (parent: Poison) |
| **Freeze** | - | - | - | - | DisableAll | - | - |
| **Slow** | 10s | - | - | 0.5x | - | - | - |
| **Stun** | 10s | - | - | - | DisableAll | Primary, Secondary, Ability1, Ability3 | - |
| **Root** | 10s | - | - | - | DisableAll, KB=0 | - | - |
| **Immune** | 20s | - | - | - | - | - | - |
| **Antidote** | 120s | false | - | - | - | - | `Weapon_Bomb_Potion_Poison.png` |

### Food Effects (45 files)

| Category | Icon Pattern | Duration Range | Overlap |
|----------|-------------|----------------|---------|
| Health Regen (4 tiers) | `Regen/Large\|Medium\|Small\|Tiny.png` | 60-360s | - |
| Health Boost (4 tiers) | `AddHealth/Large\|Medium\|Small\|Tiny.png` | 60-480s | Overwrite |
| Stamina Regen (4 tiers) | `Regen/Large\|Medium\|Small\|Tiny.png` | 60-360s | - |
| Stamina Boost (4 tiers) | `AddStamina/Large\|Medium\|Small\|Tiny.png` | 60s | Overwrite |
| Fruit/Veggie Buffs (3) | `Stamina/Tiny\|Small\|Medium.png` | 45-360s | - |
| HealthRegen Buffs (3) | `HealthRegen.png` | 45-360s | - |
| Meat Buffs (3) | `AddHealth/Tiny\|Small\|Medium.png` | 45-360s | - |
| Instant Heals (4) | none | 0.1s | Overwrite |
| Restore (8) | none | 0.1s | - |

### Potion Effects (15 files)

| Effect | Icon | Duration | Overlap |
|--------|------|----------|---------|
| Health Greater Regen | `Icons/ItemsGenerated/Potion_Health.png` | 5.05s | Overwrite |
| Health Lesser Regen | `Icons/ItemsGenerated/Potion_Health_Small.png` | 5.05s | - |
| Stamina Regen | `UI/StatusEffects/Health_Potion.png` | 15s | Overwrite |
| Stamina Cooldown (debuff) | `Icons/ItemsGenerated/Potion_Stamina.png` | 15s | - |
| Signature Greater Regen | `Icons/ItemsGenerated/Potion_Regen_Mana.png` | 30.05s | - |
| Signature Lesser Regen | `Icons/ItemsGenerated/Potion_Regen_Mana_Small.png` | 30.05s | - |
| Morph (5 types) | `Icons/ItemsGenerated/Potion_Purify.png` | 60s | - |

### Deployable Effects

| Effect | Icon | Duration | Debuff |
|--------|------|----------|--------|
| Healing_Totem_Heal | `AddHealth/Tiny.png` | 1.0s | false |
| Slowness_Totem_Slow | `Icon_Slow.png` | 1.0s | true |

### Stamina System

| Effect | Icon | Infinite | Debuff |
|--------|------|----------|--------|
| Stamina_Broken | `UI/Crosshairs/Reticle_BlockingStateBreak.png` | true | true |

### Drop Rarity Effects (4)
All infinite, OverlapBehavior=Extend, no icon (visual particles only): `Drop_Uncommon`, `Drop_Rare`, `Drop_Epic`, `Drop_Legendary`.

### Combat/Weapon Effects (11)
Invulnerability (Dagger_Dash, Dagger_Pounce), signature moves, battle effects. None have StatusEffectIcon.

### Other
- `Red_Flash` — 0.2s hit feedback, Extend overlap, DURATION removal
- `Portal_Teleport` — 0.5s portal transition
- `Creative` — 0.2s mode switch flash
- `Mana` — 2s mana restore (+12), Extend overlap
- 8 test effects in `Tests/` category

---

## 19. Icon Specifications & Client Rendering Details

### Icon Image Requirements

| Property | Value |
|----------|-------|
| **Resolution** | 512×512 pixels (standard), 78×78 (HealthRegen.png — exception) |
| **Format** | PNG with alpha transparency |
| **Style** | Flat/stylized icons, bold shapes, vibrant colors on transparent background |
| **Location in JAR** | `src/main/resources/hytale-assets/Common/UI/StatusEffects/` |
| **Path in code** | `"UI/StatusEffects/YourIcon.png"` (no `Common/` prefix) |
| **Render size** | 34×34px on screen (downscaled from source) |

### Complete Vanilla Icon Inventory

**Status Effect Icons** (`UI/StatusEffects/`):
```
Burn.png                          (fire/flame icon, orange)
Poison.png                        (skull icon, green)
HealthRegen.png                   (78×78 — exception)
Health_Potion.png                 (potion bottle)
Stamina.png                       (stamina base)
Icon_Slow.png                     (slowness debuff)
AddHealth/Large.png               (health+ large)
AddHealth/Medium.png              (health+ medium)
AddHealth/Small.png               (health+ small)
AddHealth/Tiny.png                (health+ tiny)
AddStamina/Large.png              (stamina+ large)
AddStamina/Medium.png             (stamina+ medium)
AddStamina/Small.png              (stamina+ small)
AddStamina/Tiny.png               (stamina+ tiny)
Regen/Large.png                   (regen large)
Regen/Medium.png                  (regen medium)
Regen/Small.png                   (regen small)
Regen/Tiny.png                    (regen tiny)
Stamina/Large.png                 (stamina buff large)
Stamina/Medium.png                (stamina buff medium)
Stamina/Small.png                 (stamina buff small)
Stamina/Tiny.png                  (stamina buff tiny)
```

**Item-Based Icons** (used via `Icons/ItemsGenerated/` path):
```
Potion_Health.png                 (greater health potion)
Potion_Health_Small.png           (lesser health potion)
Potion_Purify.png                 (morph potion)
Potion_Stamina.png                (stamina cooldown debuff)
Potion_Regen_Mana.png             (greater mana regen)
Potion_Regen_Mana_Small.png       (lesser mana regen)
Weapon_Bomb_Potion_Poison.png     (antidote)
```

**UI Element Icon** (non-standard path):
```
UI/Crosshairs/Reticle_BlockingStateBreak.png  (stamina broken state)
```

**Note**: Icons CAN reference any PNG path in the asset pack, not just `UI/StatusEffects/`. The path just needs to resolve to a valid PNG at runtime.

---

## 20. Vendor Mod Patterns & Best Practices

### Pattern 1: Adapter Utility (Vampirism)
```java
public static boolean applyOrReplace(Ref<EntityStore> targetRef, int effectIndex,
        EntityEffect effect, float duration, Store<EntityStore> store) {
    EffectControllerComponent controller = store.getComponent(targetRef,
        EffectControllerComponent.getComponentType());
    if (controller == null) return false;
    if (duration > 0f) {
        controller.addEffect(targetRef, effectIndex, effect, duration,
            OverlapBehavior.OVERWRITE, store);
    } else {
        controller.addInfiniteEffect(targetRef, effectIndex, effect, store);
    }
    return true;
}
```

### Pattern 2: Volatile Static Caching (EndlessLeveling)
```java
private static volatile EntityEffect cachedBurnEffect;
// In handler:
EntityEffect burn = cachedBurnEffect;
if (burn == null) {
    cachedBurnEffect = burn = EntityEffect.getAssetMap().getAsset("Burn");
}
```

### Pattern 3: ECS Event Listeners (Hylamity)
```java
// Listen to effect additions without polling
public class EffectAddedListener extends EntityEventSystem<EntityStore, EntityEffectEcsEvent.EffectAdded> {
    @Override
    public void handle(int index, ArchetypeChunk<EntityStore> chunk,
            Store<EntityStore> store, CommandBuffer<EntityStore> cmdBuffer,
            EntityEffectEcsEvent.EffectAdded event) {
        // React to any effect being added
    }
}
```

### Pattern 4: Effect + Separate Damage (EndlessLeveling BurnAugment)
Separation of concerns: native effect handles visuals, Java handles scaled damage.
```java
// Apply visual effect
controller.addEffect(targetRef, burnEffect, 1.25f, OverlapBehavior.OVERWRITE, cmdBuffer);
// Apply separate damage with health% scaling
double burnDamage = targetHp.getMax() * ratioThisTick;
Damage burnTick = createAugmentDotDamage(sourceRef, burnDamage);
```
This is exactly what our ailment system does — and it's the correct architecture for RPG-scaled DOTs.

### Pattern 5: Invulnerability Check (Armory)
```java
private boolean isInvulnerableTarget(Ref<EntityStore> targetRef, CommandBuffer<EntityStore> cmdBuffer) {
    EffectControllerComponent ec = cmdBuffer.getComponent(targetRef, EffectControllerComponent.getComponentType());
    return ec != null && ec.isInvulnerable();
}
```

---

## 21. Integration Surface for New Effects

### Systems That Could Use Status Effect Icons

| System | Current State | What Native Icons Would Add |
|--------|--------------|----------------------------|
| **Ailments (Burn)** | Has icon | Done (`UI/StatusEffects/Burn.png`) |
| **Ailments (Poison)** | Has icon | Done (`UI/StatusEffects/Poison.png`) |
| **Ailments (Shock)** | Has icon | Done (`Icons/ItemsGenerated/Ingredient_Lightning_Essence.png`) |
| **Ailments (Freeze)** | Has icon | Done (`Icons/ItemsGenerated/Ingredient_Ice_Essence.png`) |
| **Skill Tree Buffs** | No native effects | Keystones could show active buff icons |
| **Combat Effects** | Various (`LifeStream`, `LivingFortress`) | On-proc buffs with countdown |
| **Realm Modifiers** | No visual | Active realm buffs/debuffs shown on HUD |
| **Potion/Food Buffs** | Not implemented | Timed stat boost icons with countdown |
| **Mob Auras on Players** | Frost/frozen slow applied | Debuff icon for "slowed" state |
| **Energy Shield** | Custom HUD only | Buff icon for "shield active" |
| **Stone Enchantments** | No visual feedback | Applied stones could show buff icon |
| **Gear Set Bonuses** | Not implemented | Permanent buff icons |

### Adding a New Effect — Checklist

1. **Create RPGEntityEffect** in the appropriate Manager's `initialize()`
2. **Set all visual properties** via RPGApplicationEffects builder
3. **Call `resolveSoundIndices()`** if using sounds
4. **Set statusEffectIcon** to a PNG path (or null for invisible)
5. **Set name** to a localization key (for tooltip) or null
6. **Set debuff** appropriately (affects client styling: background, arrow, sound)
7. **Add to pendingEffects** list for batch registration
8. **Register via `loadAssets()`** during plugin init (NEVER during tick)
9. **Apply via `effectController.addEffect()`** at runtime
10. **Remove via `effectController.removeEffect()`** when effect ends
11. **Track active state** in a ConcurrentHashMap<UUID, ...> for cleanup
12. **Clean up on entity death/disconnect** (clear tracking maps)

### PNG Icon Creation

If creating custom icons:
- **Size**: 512×512 pixels (rendered at 34×34 on screen)
- **Format**: PNG with alpha transparency
- **Location**: `src/main/resources/hytale-assets/Common/UI/StatusEffects/`
- **Path in code**: `"UI/StatusEffects/YourIcon.png"`
- **Style**: Match vanilla (bold shapes, vibrant colors, transparent background, no outlines)
- **Must exist at boot**: Hytale AssetStore validates PNGs exist. Missing icon = effect may fail to load.

---

## 22. Constraints & Gotchas

### CRITICAL: loadAssets() Deadlocks World Thread
`EntityEffect.getAssetStore().loadAssets()` called from within `TickingSystem.tick()` will deadlock the world thread with asset infrastructure. Always register during `onEnable` initialization. See `feedback_loadassets_world_tick_deadlock.md`.

### Effect Index Sentinel Value
`EntityEffect.getAssetMap().getIndex(id)` returns `Integer.MIN_VALUE` (not -1) if the effect is not registered. Always check `!= Integer.MIN_VALUE` before using.

### Freeze Requires Per-Percentage Effects
Each distinct `HorizontalSpeedMultiplier` value requires a separate `EntityEffect` instance. You can't change the speed of an already-applied effect — you must remove the old one and add a new one. Our `AilmentEffectManager` caches freeze effects by 5% increments.

### Sound Index Resolution
`RPGApplicationEffects.resolveSoundIndices()` MUST be called after setting sound event IDs and after the asset store is loaded. Otherwise sounds won't play.

### StatusEffectIcon Path Is Relative
The path `"UI/StatusEffects/Burn.png"` is relative to the asset root. In the JAR, the file lives at `Common/UI/StatusEffects/Burn.png`. In JSON/code, write without the `Common/` prefix.

### Hytale AssetStore Requires Icons to Exist
If `statusEffectIcon` points to a missing PNG, the entire item/effect may fail to load. Always verify the PNG exists in your asset pack before referencing it.

### OverlapBehavior.OVERWRITE Works via Implicit Fallthrough
The switch statement in `addEffect()` handles EXTEND and IGNORE explicitly, then falls through to `activeEffects.put()` for OVERWRITE — which correctly replaces the existing effect. This is intentional, not a bug.

### Thread Safety
All `effectController` calls must happen on the world thread (inside `world.execute()` or within an ECS system tick). Our ailment system is called from `CombatAilmentApplicator` which runs inside the damage pipeline (already on world thread).

### Entity Must Have EffectControllerComponent
Not all entities have `EffectControllerComponent`. Always null-check:
```java
EffectControllerComponent ec = accessor.getComponent(ref, EffectControllerComponent.getComponentType());
if (ec == null) return;
```

### Burn Effect Has Hardcoded Water Check
`LivingEntityEffectSystem.canApplyEffect()` has a hardcoded check for the Burn effect — it iterates entity bounding box blocks and suppresses Burn if entity is in `Fluid_Water`. No other effect has this behavior.

### Only One Model Override Active
`EffectControllerComponent` saves `originalModel` when the first ModelOverride effect is applied. Only one model change can be active. If a second effect tries to override, behavior is undefined (first wins).

### Stat Modifiers Fire on DOT Cadence
`StatModifiers` in effects don't apply instantly — they fire on the same `damageCalculatorCooldown` timing as DOT damage. Zero cooldown = fire once at application.

### Client Icon Cache
The client caches loaded icon PNGs per path during a session. Changing a PNG on disk won't take effect until the client restarts or the asset pack is reloaded.

### Drop Effects Use EntityEffect System Too
Drop particle systems (`Drop_Legendary`, etc.) are registered as `EntityEffect`s applied to item entities. They use `Infinite: true` with `OverlapBehavior: Extend`.

### Maximum ~13 Icons Visible
Client HUD has ~700px width for status icons at 52px each. Effects beyond ~13 may overflow. Design accordingly — don't show too many concurrent icons.

### ECS Event Hooks Available
`EntityEffectEcsEvent.EffectAdded` and `EntityEffectEcsEvent.EffectRemoved` events exist (Hylamity pattern). Can be used via `EntityEventSystem` to react to any effect without polling.
