# Ailment & DOT System Reference

**Last verified**: April 30, 2026 (v1.0.3)
**Source of truth**: Code, not this document. If this doc contradicts code, the code wins.

---

## Architecture Overview

The ailment system uses a **dual-layer architecture**:

1. **AilmentTracker** (UUID → EntityAilmentState): In-memory map. Single source of truth for ailment state. Read by combat systems (shock amp, freeze slow, conditional multipliers, DOT burst, combat logs).
2. **ECS Components** (RpgBurnComponent, RpgPoisonComponent): Attached to entities with active DOTs. Drive the tick systems that fire damage events. Only exist for damage-dealing ailments.

Why dual? The tracker provides O(1) cross-system reads. The ECS components provide O(affected entities) tick iteration. Both are updated at two clean sync points: **apply** and **expire**.

```
Hit lands with elemental damage
  │
  ▼
CombatAilmentApplicator.tryApplyAilments()
  ├─ AilmentCalculator rolls chance, computes magnitude/duration
  ├─ Writes to AilmentTracker (for readers)
  ├─ Adds ECS component via CommandBuffer (for tick systems)
  ├─ AilmentEffectManager applies visual effects
  └─ AilmentImmunityTracker grants immunity window (if stat present)

Every tick:
  ├─ RpgBurnTickSystem queries RpgBurnComponent entities
  │   └─ Fires Damage event → RPGDamageSystem.handleDOTDamage()
  ├─ RpgPoisonTickSystem queries RpgPoisonComponent entities
  │   └─ Fires Damage event ��� RPGDamageSystem.handleDOTDamage()
  └─ AilmentTickSystem queries EntityStatMap entities
      └─ Ticks Freeze/Shock duration only (no damage)

Readers (every damage event):
  ├─ DamageModifierProcessor reads shock amp % from tracker
  ├─ ConditionalMultiplierCalculator reads hasAilment(FREEZE/SHOCK)
  └─ RPGDamageSystem reads remaining DOT for DETONATE_DOT_ON_CRIT
```

---

## Ailment Types

### BURN (Fire)

| Property | Value |
|----------|-------|
| Element | FIRE |
| Duration | 4.0s (base) |
| Max Stacks | 1 (refreshes) |
| Base Chance | 10% |
| Damage Ratio | 0.5 (50% of fire damage dealt) |
| Reapplication | Takes stronger DPS, refreshes duration |

**DPS Formula**:
```
baseDps = (fireDamageDealt × 0.5 + burnDamage) / duration
finalDps = baseDps × (1 + burnDamagePercent/100) × (1 + dotDamagePercent/100)
```

### FREEZE (Water)

| Property | Value |
|----------|-------|
| Element | WATER |
| Duration | 3.0s (base) |
| Max Stacks | 1 (refreshes) |
| Base Chance | 10% |
| Magnitude | Slow % (range: 5-30%) |

**Slow Formula**:
```
slowPercent = min(30, max(5, (waterDamageDealt / targetMaxHealth) × 100))
```

### SHOCK (Lightning)

| Property | Value |
|----------|-------|
| Element | LIGHTNING |
| Duration | 2.0s (base) |
| Max Stacks | 1 (refreshes) |
| Base Chance | 10% |
| Magnitude | Damage increase % (range: 5-50%) |

**Amplification Formula**:
```
baseAmp = min(50, max(5, (lightningDamageDealt / targetMaxHealth) × 100))
finalAmp = min(50, baseAmp �� (1 + shockDamagePercent/100))
```

### POISON (Void)

| Property | Value |
|----------|-------|
| Element | VOID |
| Duration | 5.0s per stack (base) |
| Max Stacks | 10 (independent) |
| Base Chance | 10% |
| Damage Ratio | 0.3 (30% per stack) |

**Per-Stack DPS Formula**:
```
baseDps = (voidDamageDealt × 0.3 + poisonDamage) / duration
finalDps = baseDps × (1 + dotDamagePercent/100)
```

---

## Application Pipeline

### Step 1: Chance Calculation
```
applicationChance = ailmentType.baseChance + attackerStats.statusEffectChance
```

### Step 2: Immunity Check
If defender has `immunityOnAilment > 0`:
- Check `AilmentImmunityTracker.isImmune(defenderUuid, element)`
- If immune → skip this ailment
- Default immunity window: 5 seconds per element

### Step 3: Duration Calculation
```
finalDuration = baseDuration × (1 + attackerStats.statusEffectDuration / 100)
```

### Step 4: Magnitude Calculation
Formulas per type (see above). Attacker stats applied at this stage are baked into the ailment — they persist for the full duration even if attacker's stats change.

### Step 5: Write to Tracker + ECS
- `AilmentTracker.applyAilment(defenderUuid, ailmentState)` — for readers
- `commandBuffer.addComponent(defenderRef, RpgBurnComponent.TYPE, component)` — for DOT tick (Burn/Poison only)

### Step 6: Visual Effects
- `AilmentEffectManager.applyAilmentVisual()` — applies native Hytale EntityEffect (fire tint, ice particles, etc.)

### Step 7: Immunity Grant
If defender has `immunityOnAilment > 0`:
- `AilmentImmunityTracker.grantImmunity(defenderUuid, element)` — 5s window

---

## DOT Damage Pipeline

When `RpgBurnTickSystem` or `RpgPoisonTickSystem` fires damage:

1. Compute `damage = DPS × dt`
2. Resolve source entity (player who applied the DOT) via UUID → `Damage.EntitySource`
3. Create `Damage` event with custom `DamageCause` (`Rpg_Burn_Dot` or `Rpg_Poison_Dot`)
4. Fire via `DamageSystems.executeDamage(targetRef, commandBuffer, damage)`

This enters `RPGDamageSystem.handleDOTDamage()` which:

1. Applies defender elemental resistance + armor via `RPGDamageCalculator.calculateDOT()`
2. Applies shock amplification if target is shocked: `rpgDamage *= (1 + shockAmp/100)`
3. Sends floating damage indicators (element-colored)
4. Processes SHIELD_REGEN_ON_DOT: `shieldRestore = dotDamage �� (shieldRegenOnDot / 100)`
5. Applies health reduction (lethal → death detection + kill attribution, non-lethal → cancel + manual subtract)

**Kill attribution**: The `Damage` event carries `EntitySource(applicatorRef)`. When a DOT kill happens, `XpGainSystem`, `LootListener`, and `RealmMobDeathListener` all find the `EntitySource` and credit the player.

---

## Stat Interactions

### Offensive (Attacker Stats — Applied at Ailment Creation)

| Stat | Effect | Source |
|------|--------|--------|
| `statusEffectChance` | +% to ailment application chance | Gear, Skill Tree |
| `statusEffectDuration` | +% to ailment duration | Void attribute, Gear, Skill Tree |
| `burnDamage` | Flat DPS added to Burn | Fire attribute, Gear, Skill Tree |
| `poisonDamage` | Flat DPS added to Poison (per stack) | Gear, Skill Tree |
| `burnDamagePercent` | ×multiplier on Burn DPS | Fire attribute (+0.4%/point) |
| `shockDamagePercent` | ×multiplier on Shock magnitude | Gear, Skill Tree |
| `dotDamagePercent` | ×multiplier on ALL DoT (Burn + Poison) | Void attribute (+0.3%/point) |

### Defensive (Defender Stats — Applied at Damage Time)

| Stat | Effect | Source |
|------|--------|--------|
| Elemental resistance | Reduces DOT damage per tick | Gear, Skill Tree, Attributes |
| Armor | Reduces DOT damage per tick | Earth attribute, Gear |
| `shieldRegenOnDot` | % of YOUR DoT damage dealt → restores YOUR energy shield | Gear, Skill Tree |
| `immunityOnAilment` | Grants temp element immunity window after ailment applied | Gear, Skill Tree |

### Combat Modifiers (Read per Hit from Tracker)

| Stat | What reads it | From |
|------|--------------|------|
| Shock damage increase % | `DamageModifierProcessor` | `AilmentTracker.getShockDamageIncreasePercent()` |
| Freeze active | `ConditionalMultiplierCalculator` | `AilmentTracker.hasAilment(FREEZE)` |
| Shock active | `ConditionalMultiplierCalculator` | `AilmentTracker.hasAilment(SHOCK)` |
| Remaining DOT damage | `RPGDamageSystem` (DETONATE_DOT_ON_CRIT) | `AilmentTracker.getRemainingDotDamage()` |

---

## ECS Components

### RpgBurnComponent
- **Query target**: `RpgBurnTickSystem`
- **Fields**: `dps`, `remainingDuration`, `sourceUuid`
- **Refresh behavior**: `refresh(newDps, newDuration, newSource)` — takes stronger DPS, longer duration
- **Lifecycle**: Added by `CombatAilmentApplicator.addDotComponent()`, removed by tick system on expiry or by DETONATE_DOT_ON_CRIT

### RpgPoisonComponent
- **Query target**: `RpgPoisonTickSystem`
- **Fields**: `List<PoisonStack>` (each with `dps`, `remainingDuration`, `sourceUuid`)
- **Stack behavior**: `addStack()` up to max (10), `tickStacks(dt)` removes expired
- **Lifecycle**: Added on first poison, removed when last stack expires or detonated

---

## DamageCause Assets

| Asset | File | Color | Durability | Stamina |
|-------|------|-------|------------|---------|
| `Rpg_Burn_Dot` | `Server/Entity/Damage/Rpg_Burn_Dot.json` | #CC5500 | No | No |
| `Rpg_Poison_Dot` | `Server/Entity/Damage/Rpg_Poison_Dot.json` | #8822AA | No | No |

---

## Configuration (ailments.yml)

```yaml
enabled: true
tick_rate_seconds: 0.25

burn:
  base_chance: 10.0
  base_duration: 4.0
  damage_ratio: 0.5

freeze:
  base_chance: 10.0
  base_duration: 3.0
  max_slow_percent: 30.0
  min_magnitude: 5.0

shock:
  base_chance: 10.0
  base_duration: 2.0
  max_damage_increase: 50.0
  min_magnitude: 5.0

poison:
  base_chance: 10.0
  base_duration: 5.0
  damage_ratio: 0.3
  max_stacks: 10
```

---

## File Map

| File | Role |
|------|------|
| `ailments/AilmentType.java` | Enum: 4 ailment definitions |
| `ailments/AilmentState.java` | Immutable record: single ailment instance |
| `ailments/EntityAilmentState.java` | Per-entity container: single ailments + poison stacks |
| `ailments/AilmentTracker.java` | UUID ��� EntityAilmentState map (readers' source of truth) |
| `ailments/AilmentCalculator.java` | Pure math: chance, duration, magnitude |
| `ailments/AilmentTickSystem.java` | ECS tick: Freeze/Shock duration only |
| `ailments/AilmentEffectManager.java` | Visual effects via native EntityEffect |
| `ailments/AilmentImmunityTracker.java` | Per-element temp immunity windows |
| `ailments/config/AilmentConfig.java` | YAML config loader |
| `ailments/component/RpgBurnComponent.java` | ECS component: burn DOT state |
| `ailments/component/RpgPoisonComponent.java` | ECS component: poison DOT stacks |
| `ailments/component/RpgBurnTickSystem.java` | ECS tick: burn damage events |
| `ailments/component/RpgPoisonTickSystem.java` | ECS tick: poison damage events |
| `combat/ailments/CombatAilmentApplicator.java` | Application during combat |
| `combat/detection/DamageTypeClassifier.java` | DOT cause detection + element mapping |
| `combat/RPGDamageSystem.java` | handleDOTDamage: defense scaling + shock amp + shield regen |
| `combat/modifiers/DamageModifierProcessor.java` | Shock amplification reader |
| `combat/modifiers/ConditionalMultiplierCalculator.java` | Freeze/Shock conditional bonuses |
