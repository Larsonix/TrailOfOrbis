# Stat Pipeline Reference

**Last verified**: May 2, 2026 (v1.0.3 — stat pipeline refactor)
**Source of truth**: Code, not this document. If this doc contradicts code, the code wins.

---

## Pipeline Overview

A player's stats are computed in 7 stages, always in this order:

```
1. ATTRIBUTE ALLOCATION     → 6 elemental points
2. BASE STAT CALCULATION    → AttributeCalculator: points × grants = base stats
3. SKILL TREE MODIFICATION  → StatsCombiner: flat → base fields, percent → accumulators
4. GEAR BONUS APPLICATION   → GearStatApplier: flat → base fields, percent → accumulators
5. CONDITIONAL MODIFIERS    → ConditionalTriggerSystem: on-kill, on-crit, threshold bonuses
6. RESOURCE CONSOLIDATION   → PoE "increased" formula: base × (1 + totalPercent/100), applied ONCE
7. ECS APPLICATION          → StatsApplicationCallback: write to Hytale entity components
```

**Key principle**: All "increased %" sources (attributes, skill tree, gear, conditionals) are
SUMMED into percent accumulator fields, then applied as a single multiplication in Stage 6.
This prevents multiplicative compounding between sources (PoE's "increased" vs "more" model).

**Entry point**: `AttributeManager.recalculateStats(UUID playerId)`

**Triggers**: Level up, attribute allocation, skill tree change, gear change, conditional trigger

---

## Stage 1: Attribute Allocation

Players allocate points into 6 elemental attributes. +1 point per level.

| Element | Theme | Key Grants per Point |
|---------|-------|---------------------|
| **FIRE** | Glass cannon | +0.4% phys dmg, +0.3% charged atk, +0.6% crit mult, +0.4% burn dmg |
| **WATER** | Arcane mage | +0.5% spell dmg, +1.5 mana, +2.0 energy shield, +0.15 mana/s |
| **LIGHTNING** | Storm blitz | +0.3% atk speed, +0.15% move speed, +0.1% crit chance, +0.1 stamina/s |
| **EARTH** | Iron fortress | +0.5% max HP, +5.0 armor, +0.2 HP/s, +0.2% block chance, +0.3% KB resist |
| **WIND** | Ghost ranger | +5.0 evasion, +3.0 accuracy, +0.5% proj dmg, +0.15% jump force |
| **VOID** | Life devourer | +0.1% life steal, +0.05% true dmg, +0.3% DOT dmg, +0.5 mana on kill |

**Location**: `AttributeCalculator.calculateStats()` reads grants from `config.yml`

---

## Stage 2: Base Stat Calculation

`AttributeCalculator` converts 6 point values into ~285 stat fields.

**Formula**: `stat = element_points × grant_value_per_point`

Each element maps to a disjoint set of stats — no overlap between elements.

**Output**: `ComputedStats` containing 5 sub-objects:

| Sub-object | Field Count | Examples |
|------------|-------------|---------|
| `ResourceStats` | ~40 | maxHealth, maxMana, maxStamina, healthRegen, manaRegen |
| `OffensiveStats` | ~134 | physicalDamage, spellDamage, critChance, critMultiplier, attackSpeed, burnDamage, statusEffectChance, all elemental conversions |
| `DefensiveStats` | ~60 | armor, evasion, energyShield, blockChance, knockbackResistance, elemental resistances, ailment thresholds |
| `MovementStats` | ~16 | movementSpeedPercent, jumpForceBonus, sprintSpeedBonus, climbSpeedBonus |
| `ElementalStats` | 30 (5 per element × 6) | flatDamage, percentDamage, multiplierDamage, resistance, penetration |

**Total**: ~285 stat fields

---

## Stage 3: Skill Tree Modification

`StatsCombiner.combine(baseStats, aggregatedModifiers)` applies skill tree node bonuses.

**Formula** (PoE-style, per stat):
```
Final = (Base + Sum(Flat)) × (1 + Sum(Percent) / 100) × Product(1 + Multiplier / 100)
```

Three modifier types:
- **FLAT**: Added to base before scaling (e.g., +50 max health)
- **PERCENT**: Summed then applied as single multiplier (e.g., +15% + +10% = ×1.25)
- **MULTIPLIER**: Each applied independently as chain multiplier (e.g., ×1.1 × ×1.2 = ×1.32)

**Coverage**: 150+ stat types defined in `StatType.java`, all processable by the combiner.

---

## Stage 4: Resource Percent Consolidation

`ComputedStats.consolidateResourcePercents()` folds percent modifiers into actual values.

```
maxHealth = maxHealth × (1 + maxHealthPercent / 100)
maxMana = maxMana × (1 + maxManaPercent / 100)
maxStamina = maxStamina × (1 + maxStaminaPercent / 100)
```

**Why here**: Must happen AFTER skill tree (which may add percent bonuses) but BEFORE gear (so gear flat bonuses layer on top of percent-scaled resources).

---

## Stage 5: Gear Bonus Application

`GearBonusProvider.applyGearBonuses(playerId, stats)` reads equipped items and applies modifiers.

**Flow**:
1. `GearStatCalculator.calculateBonuses()` iterates armor + weapon + utility containers
2. Extracts gear modifiers → `Map<String, Double>` for flat and percent
3. Applies quality multiplier to values
4. `GearStatApplier.apply()` writes to `ComputedStats`:
   - Sets weapon base damage (replaces vanilla)
   - Applies flat bonuses (additive)
   - Applies percent bonuses (multiplicative on accumulated value)

**Mapping**: `StatMapping.java` contains ~270 stat ID registrations mapping gear modifier IDs (snake_case strings like `"max_health"`, `"crit_chance"`) to `ComputedStats` setter calls.

---

## Stage 6: Conditional Modifiers

`ConditionalTriggerSystem.getActiveModifiers(playerId)` returns currently active conditional bonuses from skill tree.

Applied using the same `StatsCombiner` as Stage 3.

Examples: +20% damage for 5s after a kill, +15% crit chance when below 30% HP.

---

## Stage 7: ECS Application

`StatsApplicationCallback.applyStatsToEntity(playerId)` writes final `ComputedStats` to the Hytale entity.

What it does:
- Sets `EntityStatMap` values (HP max, mana max, stamina max, etc.)
- Sets `MovementSettings` (speed, jump, climb)
- Applies RPG stat modifiers with key `"rpg_attribute_bonus"`

**Optimization**: If `ComputedStats.equals()` returns true (stats unchanged), the entire ECS application is skipped. A per-player version counter (`AtomicLong`) is only incremented on actual changes.

---

## Stat Flow for Combat

When damage is calculated, `RPGDamageSystem` reads stats at multiple points:

```
RPGDamageSystem.handle()
  │
  ├─ Phase 1: Base damage
  │   └─ Attacker: weaponBaseDamage, physicalDamage, spellDamage, elemental flat damage
  │
  ├─ Phase 2: Avoidance
  │   └─ Defender: evasion, dodgeChance, blockChance, blockDamageReduction, parryChance
  │   └─ Attacker: accuracy
  │
  ├─ Phase 3: Defense scaling
  │   └─ Defender: armor (PoE formula), elemental resistances
  │   └─ Attacker: armorPenetration, elemental penetration
  │
  ├─ Phase 4: Ailment application
  │   └─ Attacker: statusEffectChance, statusEffectDuration, burnDamage, poisonDamage
  │   └─ Attacker: burnDamagePercent, shockDamagePercent, dotDamagePercent
  │   └─ Defender: immunityOnAilment, ailment thresholds
  │
  ├─ Phase 5: Post-calc modifications
  │   └─ Defender shock amp (from AilmentTracker)
  │   └─ Attacker: detonateDotOnCrit
  │   └─ Defender: critNullifyChance
  │
  ├─ Phase 6: Conditional multipliers
  │   └─ Attacker: damageVsFrozenPercent, damageVsShockedPercent, damageAtLowLife
  │
  └─ Phase 7: Recovery
      └─ Attacker: lifeSteal, manaLeech, shieldRegenOnDot
```

---

## Stat Flow for DOT Ticks

DOT damage takes a simplified path:

```
RpgBurnTickSystem / RpgPoisonTickSystem
  │
  ├─ Compute: DPS × dt (from ECS component, pre-calculated at application)
  │
  └─ Fire DamageSystems.executeDamage()
      │
      └─ RPGDamageSystem.handleDOTDamage()
          ├─ Defender: armor, elemental resistance (calculateDOT)
          ├─ Defender: shock amplification (from AilmentTracker)
          └─ Attacker: shieldRegenOnDot (from ComputedStats)
```

Key difference: DOT ticks don't recalculate attacker offensive stats per tick. The DPS was baked in at application time (Stage 4 of combat). Only defender-side scaling happens per tick.

---

## Recalculation Triggers

| Event | Method | What Recalculates |
|-------|--------|-------------------|
| Level up | `LevelingEventDispatcher` → `recalculateStats()` | Full pipeline |
| Attribute allocation | `allocateAttribute()` → `recalculateStats()` | Full pipeline |
| Skill tree node | `SkillTreeManager` → `recalculateStats()` | Full pipeline |
| Gear equipped/changed | `GearEquipmentListener` → `recalculateStats()` | Full pipeline |
| Conditional trigger | `ConditionalTriggerSystem` → `recalculateStats()` | Full pipeline |
| Admin command | `/tooadmin attr` | Full pipeline |

---

## Thread Safety

- `AttributeManager` uses per-player `ReentrantLock` — concurrent players recalculate independently
- `ComputedStats` is rebuilt fresh each recalculation (no mutation of live stats)
- ECS application happens on world thread via `world.execute()`
- Stats version check prevents redundant ECS writes

---

## Vanilla Stat Suppression

Hytale's own equipment stat system (armor HP bonuses, weapon damage, utility stats) runs independently. `VanillaEquipmentStatSuppressor` removes all vanilla equipment modifier keys from `EntityStatMap` every tick AFTER Hytale's `Recalculate` system runs. This ensures only RPG stats affect the player.

Additionally, `ItemRegistryService.neutralizeVanillaStats()` replaces the `ItemArmor`, `ItemWeapon`, and `ItemUtility` on server-side custom Items with stat-free copies at registration time.

See `docs/reference/vanilla-stat-suppression.md` (if exists) or the `feedback_vanilla_stat_suppression.md` memory file for details.

---

## Key Files

| File | Role |
|------|------|
| `attributes/AttributeCalculator.java` | Stage 2: attributes → base stats |
| `attributes/AttributeManager.java` | Orchestrator: triggers all 7 stages |
| `attributes/ComputedStats.java` | The stat container (~285 fields) |
| `attributes/stats/OffensiveStats.java` | 134 offensive stat fields |
| `attributes/stats/DefensiveStats.java` | 60 defensive stat fields |
| `attributes/stats/ResourceStats.java` | 40 resource stat fields |
| `attributes/stats/MovementStats.java` | 16 movement stat fields |
| `attributes/StatsApplicationCallback.java` | Stage 7: write to ECS |
| `skilltree/calculation/StatsCombiner.java` | Stage 3: PoE-style modifier math |
| `gear/stats/GearBonusProvider.java` | Stage 5: gear stat application |
| `gear/stats/GearStatCalculator.java` | Stage 5: gear modifier extraction |
| `gear/stats/GearStatApplier.java` | Stage 5: writes gear stats to ComputedStats |
| `gear/stats/StatMapping.java` | Stage 5: 270+ gear stat ID → setter mappings |
| `skilltree/conditional/ConditionalTriggerSystem.java` | Stage 6: conditional bonuses |
| `systems/VanillaEquipmentStatSuppressor.java` | Removes vanilla equipment modifiers |
| `combat/RPGDamageCalculator.java` | Consumes stats during damage calculation |
| `combat/RPGDamageSystem.java` | 8-phase damage pipeline using stats |
| `config/RPGConfig.java` | Attribute grant values (per-element) |
