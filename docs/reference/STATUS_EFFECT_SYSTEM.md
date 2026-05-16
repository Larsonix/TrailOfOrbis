# Hytale Native Status Effect System — Authoritative Reference

> **Source**: Decompiled Hytale server code, 30+ vanilla JSON assets, our own `AilmentEffectManager`/`MobModifierEffectRegistry` implementations, vendor mod patterns.
> **Date**: 2026-05-15
> **Status**: Verified against live build

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Core API — EffectControllerComponent](#2-core-api--effectcontrollercomponent)
3. [Effect Definition — EntityEffect](#3-effect-definition--entityeffect)
4. [JSON Asset Schema](#4-json-asset-schema)
5. [ApplicationEffects — Visual/Audio Properties](#5-applicationeffects--visualaudio-properties)
6. [StatusEffectIcon — HUD Icons](#6-statuseffecticon--hud-icons)
7. [OverlapBehavior — Stacking Rules](#7-overlapbehavior--stacking-rules)
8. [RemovalBehavior — Expiry Rules](#8-removalbehavior--expiry-rules)
9. [Damage Over Time (DOT)](#9-damage-over-time-dot)
10. [Stat Modifiers](#10-stat-modifiers)
11. [Movement & Ability Control](#11-movement--ability-control)
12. [Model Override](#12-model-override)
13. [Network Protocol](#13-network-protocol)
14. [Programmatic Creation (No JSON)](#14-programmatic-creation-no-json)
15. [Registration & Asset Store](#15-registration--asset-store)
16. [Our Existing Usage](#16-our-existing-usage)
17. [Vanilla Effect Catalog](#17-vanilla-effect-catalog)
18. [Icon Specifications](#18-icon-specifications)
19. [Integration Surface for New Effects](#19-integration-surface-for-new-effects)
20. [Constraints & Gotchas](#20-constraints--gotchas)

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
  EntityEffect.getAssetStore()   ActiveEntityEffect created   StatusIcons HUD renders
  .loadAssets(packKey, list)     → ticks duration countdown   icon from statusEffectIcon path
       │                         → applies stat modifiers     → shows countdown if !infinite
       ▼                         → applies visuals            → debuff = red, buff = green
  Effect registered              → deals DOT damage
  in asset map with index        │
                                 ▼
                            effectController.removeEffect()
                                 │
                                 ▼
                            EntityEffectUpdate(Remove) sent
                            → client removes icon
```

**Key classes** (all `com.hypixel.hytale`):

| Class | Package | Role |
|-------|---------|------|
| `EntityEffect` | `server.core.asset.type.entityeffect.config` | Asset-side definition (JSON-loadable, extends protocol version) |
| `EntityEffect` | `protocol` | Network-serializable effect data |
| `EffectControllerComponent` | `server.core.entity.effect` | ECS component — API to add/remove effects on entities |
| `ActiveEntityEffect` | `server.core.entity.effect` | Tracks a live effect instance (duration, state) |
| `ApplicationEffects` | `protocol` | Visual/audio properties (tints, particles, screen, sound, speed) |
| `EntityEffectUpdate` | `protocol` | Packet sent to client (Add or Remove) |
| `OverlapBehavior` | `protocol` | Enum: Extend, Overwrite, Ignore |
| `RemovalBehavior` | `server.core.asset.type.entityeffect.config` | Enum: COMPLETE, INFINITE, DURATION |

---

## 2. Core API — EffectControllerComponent

**How to get it:**
```java
EffectControllerComponent effectController = accessor.getComponent(
    entityRef, EffectControllerComponent.getComponentType()
);
```

**Methods:**

### `addEffect(ref, entityEffect, accessor)` → boolean
Applies effect using the effect's own duration, overlap behavior, and infinite flag.
```java
boolean success = effectController.addEffect(entityRef, effect, accessor);
```

### `addEffect(ref, entityEffect, duration, overlapBehavior, accessor)` → boolean
Applies effect with explicit duration and overlap override. **This is what we use most.**
```java
boolean success = effectController.addEffect(
    entityRef, effect, durationSeconds, OverlapBehavior.OVERWRITE, accessor
);
```

### `addInfiniteEffect(ref, effectIndex, entityEffect, accessor)` → boolean
Applies a permanent effect (no countdown, no expiry).
```java
int effectIndex = EntityEffect.getAssetMap().getIndex(effect.getId());
boolean success = effectController.addInfiniteEffect(
    entityRef, effectIndex, effect, accessor
);
```

### `removeEffect(ref, effectIndex, accessor)` → void
Removes an active effect. Behavior depends on the effect's `RemovalBehavior`.
```java
int effectIndex = EntityEffect.getAssetMap().getIndex(effect.getId());
effectController.removeEffect(entityRef, effectIndex, accessor);
```

### `getActiveEffects()` → List<ActiveEntityEffect>
Returns all currently active effects on the entity.

---

## 3. Effect Definition — EntityEffect

The asset-side `EntityEffect` has these fields (all `protected`, accessible via our `RPGEntityEffect` subclass):

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `id` | String | (from constructor) | Unique effect identifier |
| `name` | String | null | Localization key for tooltip (e.g., `"server.effects.burn"`) |
| `applicationEffects` | ApplicationEffects | null | Visual/audio/movement properties |
| `duration` | float | 0.0 | Default duration in seconds |
| `infinite` | boolean | false | If true, effect never expires |
| `debuff` | boolean | false | If true, shown with debuff styling (red) |
| `statusEffectIcon` | String | null | Path to icon PNG (relative to asset root) |
| `overlapBehavior` | OverlapBehavior | (none) | How to handle re-application |
| `removalBehavior` | RemovalBehavior | COMPLETE | What happens on removal |
| `invulnerable` | boolean | false | Makes entity invulnerable while active |
| `deathMessageKey` | String | null | Localization key for DOT death message |
| `damageCalculator` | DamageCalculator | null | DOT damage configuration |
| `damageCalculatorCooldown` | double | 0.0 | Seconds between DOT ticks |
| `damageEffects` | DamageEffects | null | Visuals/sounds when DOT ticks |
| `statModifiers` | Map<Integer, Float> | null | Stat changes (by stat ID) |
| `statModifierEffects` | DamageEffects | null | Visuals when stats are applied |
| `valueType` | ValueType | ABSOLUTE | How stat modifiers are interpreted |
| `modelOverride` | ModelOverride | null | Model swap while active |
| `entityStats` | Object2FloatMap<String> | null | Stat modifiers by name (resolved post-load) |
| `damageResistanceValues` | Map<DamageCause, StaticModifier[]> | null | Damage resistance by cause |

---

## 4. JSON Asset Schema

**Location**: `Server/Entity/Effects/<Category>/<EffectName>.json`

**Vanilla categories**: `BlockPlacement/`, `Damage/`, `Deployables/`, `Drop/`, `Food/Boost/`, `Food/Buff/`, `GameMode/`, `Mana/`, `Movement/`, `Npc/`, `Portals/`, `Potion/`, `Projectiles/`, `Status/`, `Tests/`, `Weapons/`

**Complete schema** (all fields optional):

```json
{
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
    "Particles": [
      {
        "SystemId": "Effect_Fire",
        "TargetEntityPart": "Entity",
        "TargetNodeName": "Head"
      }
    ],
    "MovementEffects": {
      "DisableAll": true
    },
    "AbilityEffects": {
      "Disabled": ["Primary", "Secondary", "Ability1", "Ability3"]
    }
  },

  "Duration": 5.0,
  "Infinite": false,
  "Debuff": true,
  "Invulnerable": false,

  "OverlapBehavior": "Overwrite",
  "RemovalBehavior": "Complete",

  "StatusEffectIcon": "UI/StatusEffects/Burn.png",
  "Name": "server.effects.burn",

  "StatModifiers": {
    "Health": 2
  },
  "RawStatModifiers": {
    "Health": [
      {
        "Amount": 30,
        "CalculationType": "Additive",
        "Target": "Max"
      }
    ]
  },
  "ValueType": "Absolute",
  "StatModifierEffects": {
    "WorldParticles": [
      { "SystemId": "Potion_Health_Implosion", "PositionOffset": { "Y": 1.0 } }
    ],
    "WorldSoundEventId": "SFX_xxx"
  },

  "DamageCalculator": {
    "BaseDamage": {
      "Fire": 10
    }
  },
  "DamageCalculatorCooldown": 1.0,
  "DamageEffects": {
    "WorldSoundEventId": "SFX_xxx",
    "PlayerSoundEventId": "SFX_xxx"
  },

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
  },

  "Locale": "server.general.deathCause.burn"
}
```

---

## 5. ApplicationEffects — Visual/Audio Properties

These are the visual effects applied to the entity while the effect is active.

| Field | Type | Description | Example |
|-------|------|-------------|---------|
| `EntityTopTint` | Hex color | Tint color for upper body | `"#cf2302"` (fire red) |
| `EntityBottomTint` | Hex color | Tint color for lower body | `"#100600"` (dark) |
| `EntityAnimationId` | String | Animation to play on entity | `"Hurt"` |
| `ScreenEffect` | String | Full-screen overlay PNG path | `"ScreenEffects/Fire.png"` |
| `ModelVFXId` | String | VFX effect on entity model | `"Burn"`, `"Freeze"`, `"Creative"` |
| `HorizontalSpeedMultiplier` | float | Movement speed modifier | `0.5` = half speed |
| `KnockbackMultiplier` | float | Knockback received modifier | `0` = immovable |
| `WorldSoundEventId` | String | Sound heard by all nearby | `"SFX_Effect_Burn_World"` |
| `LocalSoundEventId` | String | Sound heard only by target | `"SFX_Effect_Burn_Local"` |
| `Particles` | Array | Particle emitters on entity | See below |
| `MovementEffects.DisableAll` | boolean | Completely freezes movement | Root, Stun, Freeze |
| `AbilityEffects.Disabled` | String[] | Disables specific abilities | `["Primary", "Secondary"]` |

**Particle entry format:**
```json
{
  "SystemId": "Effect_Fire",
  "TargetEntityPart": "Entity",
  "TargetNodeName": "Head"
}
```
- `SystemId` is required — references a `.particlesystem` asset
- `TargetEntityPart` and `TargetNodeName` are optional — controls where on the entity the particles spawn

**Known vanilla ScreenEffects**: `ScreenEffects/Fire.png`, `ScreenEffects/Snow.png`, `ScreenEffects/Poison.png`, `ScreenEffects/Immune.png`

**Known vanilla ModelVFXIds**: `Burn`, `Freeze`, `Creative`, `Intangible_Dark`, `Drop_Legendary`

**Known vanilla particle SystemIds**: `Effect_Fire`, `Effect_Snow`, `Effect_Snow_Impact`, `Effect_Poison`, `Stunned`, `Creative_Switch`, `Potion_Health_Heal`, `Potion_Health_Implosion`

---

## 6. StatusEffectIcon — HUD Icons

**Path format**: Relative to asset root. Examples:
- `"UI/StatusEffects/Burn.png"`
- `"UI/StatusEffects/Poison.png"`
- `"UI/StatusEffects/AddHealth/Large.png"`
- `"Icons/ItemsGenerated/Potion_Health.png"` (can reference any PNG, not just StatusEffects/)

**Physical location**: `Common/UI/StatusEffects/` in the asset pack

**Rendering**:
- Icons appear in the StatusIcons HUD area (top-right near health/stamina)
- `debuff: true` → red-tinted icon frame
- `debuff: false` → neutral/green icon frame
- `infinite: true` → no countdown shown
- `infinite: false` → countdown timer overlaid on icon
- Hovering shows tooltip with effect `name` (localization key)

**If `statusEffectIcon` is null**: No icon is shown in the HUD. The effect still applies all its gameplay mechanics (tints, speed, stats, etc.) — it's just invisible in the status bar. We use this for internal effects like mob speed scaling.

---

## 7. OverlapBehavior — Stacking Rules

```java
// com.hypixel.hytale.protocol.OverlapBehavior
public enum OverlapBehavior {
    Extend(0),     // Add duration to existing: remainingDuration += newDuration
    Overwrite(1),  // Replace existing effect completely
    Ignore(2)      // Do nothing if already active
}
```

| Behavior | When to use | Vanilla example |
|----------|-------------|-----------------|
| **Extend** | Stackable durations (poison stacking) | `Poison.json`, `Red_Flash.json`, `Mana.json` |
| **Overwrite** | Refreshable effects (re-apply resets timer) | `Burn.json`, `Slow.json`, Food buffs |
| **Ignore** | One-shot effects (apply once, don't refresh) | Mob modifier tints, drop particles |

**Our pattern**: We use OVERWRITE for all ailments (burn, freeze, shock, poison) because our RPG system manages stacks independently — the native effect is just the visual layer.

---

## 8. RemovalBehavior — Expiry Rules

```java
// com.hypixel.hytale.server.core.asset.type.entityeffect.config.RemovalBehavior
public enum RemovalBehavior {
    COMPLETE,   // Fully remove effect and all its visuals
    INFINITE,   // Convert infinite→timed (set infinite=false, keep remaining duration)
    DURATION    // Set remainingDuration=0 but keep effect tracking
}
```

| Behavior | When to use | Vanilla example |
|----------|-------------|-----------------|
| **COMPLETE** | Most effects — clean removal | Default, mob modifiers |
| **DURATION** | Brief flash effects that should linger | `Red_Flash.json`, `Poison.json` |
| **INFINITE** | Converting permanent buffs to timed | Rare use case |

---

## 9. Damage Over Time (DOT)

Effects can deal periodic damage without any Java code:

```json
{
  "DamageCalculator": {
    "BaseDamage": {
      "Fire": 10
    }
  },
  "DamageCalculatorCooldown": 1.0,
  "DamageEffects": {
    "WorldSoundEventId": "SFX_Effect_Burn_World",
    "PlayerSoundEventId": "SFX_Effect_Burn_Local"
  }
}
```

- `BaseDamage` keys are `DamageCause` names: `Fire`, `Poison`, etc.
- `DamageCalculatorCooldown` is seconds between damage ticks
- `DamageEffects` are played each time damage ticks

**Note**: Our RPG system handles ailment damage in Java (via `AilmentCalculator`), so we do NOT use native DOT. Our native effects are visual-only.

---

## 10. Stat Modifiers

Effects can modify entity stats automatically:

**Simple format** (flat value):
```json
{
  "StatModifiers": {
    "Health": 2,
    "MaxHealth": 50
  }
}
```

**Advanced format** (with calculation type):
```json
{
  "RawStatModifiers": {
    "Health": [
      {
        "Amount": 30,
        "CalculationType": "Additive",
        "Target": "Max"
      }
    ]
  },
  "ValueType": "Percent"
}
```

- `ValueType`: `"Absolute"` (flat) or `"Percent"` (percentage)
- `CalculationType`: `"Additive"` (confirmed in vanilla)
- `Target`: `"Max"` or `"Current"` (which stat value to modify)

**Note**: Our RPG system handles all stat logic in Java. We don't use native stat modifiers on custom effects.

---

## 11. Movement & Ability Control

### Full Immobilization (Root/Stun/Freeze)
```json
{
  "ApplicationEffects": {
    "MovementEffects": {
      "DisableAll": true
    }
  }
}
```

### Speed Reduction (Slow)
```json
{
  "ApplicationEffects": {
    "HorizontalSpeedMultiplier": 0.5
  }
}
```

### Ability Lockout (Stun)
```json
{
  "ApplicationEffects": {
    "AbilityEffects": {
      "Disabled": ["Primary", "Secondary", "Ability1", "Ability3"]
    }
  }
}
```

### Knockback Immunity (Root)
```json
{
  "ApplicationEffects": {
    "KnockbackMultiplier": 0
  }
}
```

---

## 12. Model Override

Replaces the entity's visual model while the effect is active (used by Root):

```json
{
  "ModelOverride": {
    "Model": "VFX/Spells/Roots/Model.blockymodel",
    "Texture": "VFX/Spells/Roots/Model.png",
    "AnimationSets": {
      "Spawn": {
        "Animations": [
          { "Animation": "VFX/Spells/Roots/Spawn.blockyanim", "Looping": false }
        ]
      },
      "Despawn": {
        "Animations": [
          { "Animation": "VFX/Spells/Roots/Despawn.blockyanim", "Looping": false }
        ]
      }
    }
  }
}
```

---

## 13. Network Protocol

**Packet**: `EntityEffectUpdate`

```java
// com.hypixel.hytale.protocol.EntityEffectUpdate
public class EntityEffectUpdate {
    public EffectOp type;           // Add(0) or Remove(1)
    public int id;                  // Effect asset index
    public float remainingTime;     // Current countdown
    public boolean infinite;        // Is infinite?
    public boolean debuff;          // Debuff styling?
    public String statusEffectIcon; // PNG path (sent to client)
}
```

- Updates are batched in `EffectControllerComponent.changes` and sent as `UpdateEntityEffects` packet
- Client receives and renders icons immediately
- The `statusEffectIcon` path is sent every time — client loads the PNG from the asset pack

---

## 14. Programmatic Creation (No JSON)

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
    .withSpeed(0.7f);  // 30% slow
appEffects.resolveSoundIndices();  // MUST call after setting sounds

// Configure the effect
effect.setApplicationEffects(appEffects);
effect.setInfinite(false);
effect.setDuration(5.0f);
effect.setOverlapBehavior(OverlapBehavior.OVERWRITE);
effect.setDebuff(true);
effect.setStatusEffectIcon("UI/StatusEffects/MyIcon.png");  // null = no HUD icon
effect.setDeathMessageKey("server.general.deathCause.myEffect");  // null = no death msg
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
- `RPGApplicationEffects.create()` — factory (speed=1.0)
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

## 15. Registration & Asset Store

Effects MUST be registered before use. **Registration MUST happen during plugin init, NEVER during a world tick** (deadlocks with asset infrastructure).

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
    // Effect not registered — error
}
```

**Critical rule**: `loadAssets()` calls from within `TickingSystem.tick()` will deadlock the world thread. All 8 `loadAssets()` calls in our plugin happen during `onEnable` initialization.

---

## 16. Our Existing Usage

### AilmentEffectManager (`ailments/AilmentEffectManager.java`)
4 ailment types with full visual effects:

| Ailment | Effect ID | Icon | Tints | Screen | VFX | Particles | Speed |
|---------|-----------|------|-------|--------|-----|-----------|-------|
| **BURN** | `rpg_ailment_burn` | `UI/StatusEffects/Burn.png` | `#100600`/`#cf2302` | `ScreenEffects/Fire.png` | `Burn` | `Effect_Fire` | - |
| **SHOCK** | `rpg_ailment_shock` | (none) | `#1a1200`/`#ffee44` | - | - | - | - |
| **POISON** | `rpg_ailment_poison` | `UI/StatusEffects/Poison.png` | `#000000`/`#008000` | - | - | `Effect_Poison` | - |
| **FREEZE** | `rpg_ailment_freeze_N` | (none) | `#80ecff`/`#da72ff` | `ScreenEffects/Snow.png` | `Freeze` | `Effect_Snow`, `Effect_Snow_Impact` | 0.70-0.95 |

Note: Shock and Freeze are **missing StatusEffectIcons** — they have no HUD icon.

### MobModifierEffectRegistry (`mobs/modifiers/MobModifierEffectRegistry.java`)
Per-modifier-type infinite visual effects (tints, VFX, speed):
- All effects have `statusEffectIcon = null` (internal, not shown on HUD)
- All effects use `OverlapBehavior.IGNORE` (applied once at spawn)
- Includes runtime behavior effects: enrage, frost aura slow, frozen slow, pack leader speed

### MobSpeedEffectManager (`mobs/speed/MobSpeedEffectManager.java`)
Speed scaling effects for mob movement — invisible, no icon, no tints.

---

## 17. Vanilla Effect Catalog

### Status Ailments (`Server/Entity/Effects/Status/`)

| Effect | Duration | DOT | Speed | Movement | Abilities | Icon |
|--------|----------|-----|-------|----------|-----------|------|
| **Burn** | 5s | Fire 10/1s | - | - | - | `Burn.png` |
| **Poison** | 16s | Poison 10/5s | - | - | - | `Poison.png` |
| **Freeze** | - | - | - | DisableAll | - | - |
| **Slow** | 10s | - | 0.5x | - | - | - |
| **Stun** | 10s | - | - | DisableAll | Primary, Secondary, Ability1, Ability3 | - |
| **Root** | 10s | - | - | DisableAll, KB=0 | - | - |
| **Immune** | 20s | - | - | - | - | - |
| **Antidote** | 120s | - | - | - | - | `Weapon_Bomb_Potion_Poison.png` |

### Drop Effects (`Server/Entity/Effects/Drop/`)

| Effect | Infinite | Particles | VFX | Tint |
|--------|----------|-----------|-----|------|
| **Drop_Uncommon** | true | Drop_Uncommon | - | - |
| **Drop_Rare** | true | Drop_Rare | - | - |
| **Drop_Epic** | true | Drop_Epic | - | - |
| **Drop_Legendary** | true | Drop_Legendary | Drop_Legendary | `#887758` bottom |

### Food Buffs (`Server/Entity/Effects/Food/`)

| Effect | Duration | Stat | Value | Type | Icon |
|--------|----------|------|-------|------|------|
| Health_Boost_Large | 480s | Health.Max | +30 | Additive | `AddHealth/Large.png` |
| Health_Boost_Medium | 480s | Health.Max | +20 | Additive | `AddHealth/Medium.png` |
| Health_Boost_Small | 480s | Health.Max | +10 | Additive | `AddHealth/Small.png` |
| Stamina_Boost_* | 480s | Stamina.Max | +N | Additive | `Stamina/*.png` |

### Other Effects

| Effect | Duration | Purpose |
|--------|----------|---------|
| Red_Flash | 0.2s | Damage hit flash (Extend overlap) |
| Dodge_Invulnerability | 0.25s | i-frames during dodge roll |
| Creative | 0.2s | Creative mode switch flash |
| Healing_Totem_Heal | 1s | Totem AoE heal (Overwrite, +2 HP, icon) |
| Slowness_Totem_Slow | 1s | Totem AoE slow (Overwrite, 0.5x speed, debuff icon) |
| Potion_Health_Greater_Regen | 5.05s | Health potion regen (50% health, particles on pelvis) |
| Mana | 2s | Mana restore (+12 mana, Extend) |

---

## 18. Icon Specifications

**Standard size**: 512x512 pixels (confirmed for all 17 vanilla icons)
**Format**: PNG with alpha transparency
**Style**: Flat/stylized icons with bold shapes, no outlines, vibrant colors on transparent background
**Path convention**: `Common/UI/StatusEffects/<Name>.png` or `Common/UI/StatusEffects/<Category>/<Name>.png`

**Vanilla icon inventory** (17 icons):
```
UI/StatusEffects/Burn.png                    (fire/flame icon, orange)
UI/StatusEffects/Poison.png                  (skull icon, green)
UI/StatusEffects/Health_Potion.png           (potion icon)
UI/StatusEffects/HealthRegen.png             (78x78 — exception to 512x512)
UI/StatusEffects/Stamina.png                 (stamina icon)
UI/StatusEffects/AddHealth/Large.png         (health+ icon, size variant)
UI/StatusEffects/AddHealth/Medium.png
UI/StatusEffects/AddHealth/Small.png
UI/StatusEffects/AddHealth/Tiny.png
UI/StatusEffects/Regen/Large.png             (regen icon, size variant)
UI/StatusEffects/Regen/Medium.png
UI/StatusEffects/Regen/Small.png
UI/StatusEffects/Regen/Tiny.png
UI/StatusEffects/Stamina/Large.png           (stamina icon, size variant)
UI/StatusEffects/Stamina/Medium.png
UI/StatusEffects/Stamina/Small.png
UI/StatusEffects/Stamina/Tiny.png
```

**Note**: Icons CAN reference any PNG path, not just `UI/StatusEffects/`. Vanilla `Antidote.json` uses `"Icons/ItemsGenerated/Weapon_Bomb_Potion_Poison.png"` and `Potion_Health_Greater_Regen.json` uses `"Icons/ItemsGenerated/Potion_Health.png"`.

---

## 19. Integration Surface for New Effects

### Systems That Could Use Status Effect Icons

| System | Current State | What Native Icons Would Add |
|--------|--------------|----------------------------|
| **Ailments (Burn)** | Has icon (`Burn.png`) | Already done |
| **Ailments (Poison)** | Has icon (`Poison.png`) | Already done |
| **Ailments (Shock)** | No icon | Needs: lightning/electric icon |
| **Ailments (Freeze)** | No icon | Needs: snowflake/ice icon |
| **Skill Tree Buffs** | No native effects | Keystones could show active buff icons |
| **Gear Set Bonuses** | No effects system yet | Set bonuses could show permanent icons |
| **Combat Effects** | Various (`LifeStream`, `LivingFortress`) | On-proc buffs with countdown |
| **Realm Modifiers** | No visual | Active realm buffs/debuffs shown on HUD |
| **Potion/Food Buffs** | Not implemented yet | Timed stat boost icons with countdown |
| **Mob Auras on Players** | Frost/frozen slow applied | Could add debuff icon for "slowed" state |
| **Energy Shield** | Custom HUD only | Could add buff icon for "shield active" |
| **Stone Enchantments** | No visual feedback | Applied stones could show buff icon |

### Adding a New Effect — Checklist

1. **Create RPGEntityEffect** in the appropriate Manager's `initialize()`
2. **Set all visual properties** via RPGApplicationEffects builder
3. **Call `resolveSoundIndices()`** if using sounds
4. **Set statusEffectIcon** to a PNG path (or null for invisible)
5. **Add to pendingEffects** list for batch registration
6. **Register via `loadAssets()`** during plugin init (NEVER during tick)
7. **Apply via `effectController.addEffect()`** at runtime
8. **Remove via `effectController.removeEffect()`** when effect ends
9. **Track active state** in a ConcurrentHashMap<UUID, ...> for cleanup
10. **Clean up on entity death/disconnect** (clear tracking maps)

### PNG Icon Creation

If creating custom icons:
- **Size**: 512x512 pixels
- **Format**: PNG with alpha transparency
- **Location**: `src/main/resources/hytale-assets/Common/UI/StatusEffects/`
- **Path in code**: `"UI/StatusEffects/YourIcon.png"`
- **Style**: Match vanilla (bold shapes, vibrant colors, transparent background)

---

## 20. Constraints & Gotchas

### CRITICAL: loadAssets() Deadlocks World Thread
`EntityEffect.getAssetStore().loadAssets()` called from within `TickingSystem.tick()` will deadlock. Always register during `onEnable` initialization. See `feedback_loadassets_world_tick_deadlock.md`.

### Effect Index Can Be MIN_VALUE
Always check `EntityEffect.getAssetMap().getIndex(id) != Integer.MIN_VALUE` before using. If it returns MIN_VALUE, the effect wasn't registered.

### Freeze Requires Per-Percentage Effects
Each distinct `HorizontalSpeedMultiplier` value requires a separate `EntityEffect` instance. You can't change the speed of an already-applied effect — you must remove the old one and add a new one. Our `AilmentEffectManager` caches freeze effects by 5% increments (5, 10, 15, 20, 25, 30).

### Sound Index Resolution
`RPGApplicationEffects.resolveSoundIndices()` MUST be called after setting sound event IDs and after the asset store is loaded. Otherwise sounds won't play.

### StatusEffectIcon Path Is Relative
The path `"UI/StatusEffects/Burn.png"` is relative to the asset root. In the JAR, the file lives at `Common/UI/StatusEffects/Burn.png`. In the JSON, you write `"UI/StatusEffects/Burn.png"` (without the `Common/` prefix).

### Hytale AssetStore Requires Icons to Exist
If `statusEffectIcon` points to a missing PNG, the entire item/effect may fail to load. Always verify the PNG exists in your asset pack before referencing it.

### OverlapBehavior Affects Stacking
Using `Extend` with repeated applications will accumulate duration (100 burns = 500s of burn). Use `Overwrite` when you manage stacks in Java.

### Thread Safety
All `effectController` calls must happen on the world thread (inside `world.execute()` or within an ECS system tick). Our ailment system is called from `CombatAilmentApplicator` which runs inside the damage pipeline (already on world thread).

### Entity Must Have EffectControllerComponent
Not all entities have `EffectControllerComponent`. Always null-check:
```java
EffectControllerComponent ec = accessor.getComponent(ref, EffectControllerComponent.getComponentType());
if (ec == null) return;
```

### Drop Effects Use EntityEffect Too
The drop particle systems (`Drop_Legendary`, etc.) are ALSO registered as `EntityEffect`s — they're applied to item entities when dropped. The `ItemEntityConfig.ParticleSystemId` references the particle system, but there are ALSO `EntityEffect` JSONs for drops that add tints and particles.
