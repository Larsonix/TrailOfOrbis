# Armor Formula Redesign (C2)

**Issue**: At level 12 with ~35 armor, a backstab dealing ~100 damage gives only 2.7% reduction.
**Root cause**: PoE formula `Armor / (Armor + 10 * Damage)` is hit-size-dependent. Our armor values (27-954) are tiny compared to PoE (2,000-50,000+), but the formula's behavior against big hits makes armor feel useless for non-tank builds.

---

## 1. Problem Analysis

### The PoE Formula Trap

Our formula: `Reduction = Armor / (Armor + 10 * Damage)`

This creates **wildly inconsistent** reduction depending on the incoming hit size:

| Level | Build | Armor | vs 10 dmg | vs 50 dmg | vs 100 dmg |
|-------|-------|-------|-----------|-----------|------------|
| 12 | Non-tank | 35 | 25.9% | 6.5% | **3.4%** |
| 50 | Tank | 554 | 84.7% | 52.6% | 35.6% |
| 100 | Tank | 954 | 90.0% | 65.6% | 48.8% |

The same 35 armor gives 26% against a basic swing but **3%** against a backstab. Players see the 3% and conclude armor is useless. They're right.

### Why PoE Gets Away With It

PoE armor works because:
- **Values are enormous**: endgame players have 5,000-50,000+ armor
- **The divisor is 5** (not 10): `Armor / (Armor + 5 * Damage)` — PoE 2 uses 10-12
- **Armor is explicitly "better vs small hits"** — the community understands this
- **Other defenses exist for big hits**: guard skills, endurance charges, fortify

Our game has none of these. Our armor values max at ~1,000 and players expect armor to defend them.

### Simulation Confirms the Death Valley

The simulation shows massive EHP swings between levels because mob damage is noise-generated. Mob damage at level 75 can be 10 or 31 — armor gives 88% or 70% respectively. The formula makes armor a lottery, not a stat.

---

## 2. Three Approaches Evaluated

### Approach A: Scale Values UP (keep PoE formula)

Multiply all armor sources by 5-10x to match PoE's scale.

**Verdict: Rejected.**
- Requires rebalancing ALL armor sources (attribute grants, gear implicits, gear modifiers, skill tree nodes)
- Still produces inconsistent reduction vs different hit sizes
- Non-tank builds still get ~3-5% vs big hits even with 5x multiplier
- Doesn't solve the core design problem — just inflates numbers

### Approach B: Level-Scaled Formula (D4/WoW model) -- RECOMMENDED

Replace damage in the formula with attacker level:

```
Reduction = Armor / (Armor + k * AttackerLevel + c)
```

Where:
- `k = 9.0` — level scaling constant
- `c = 50.0` — base constant (floor for low-level behavior)
- Cap at 90%

**Verdict: Recommended.**
- Armor gives **consistent reduction regardless of hit size**
- Natural difficulty scaling (higher-level mobs reduce your armor effectiveness)
- Tank investment always gives predictable returns
- Minimal code change (one formula + pass attacker level)
- Clean interaction with physical resistance (no redundant hit-size mechanics)

### Approach C: Health-Pool Formula (Last Epoch model)

```
Reduction = Armor / (MaxHP + Armor)
```

**Verdict: Rejected.**
- Punishes tank builds that stack BOTH HP and armor — our Earth attribute grants both
- Makes health and armor anti-synergistic (more HP = less armor effectiveness)
- Glass cannon at level 100 gets 81% reduction — nearly as much as tank (85%)
- Fundamentally incompatible with our attribute design

---

## 3. Recommended Formula: Level-Scaled Armor

### The Formula

```
EffectiveArmor = Armor * max(minEffectiveness, 1 - ArmorPen/100)
Reduction = min(maxReduction, EffectiveArmor / (EffectiveArmor + levelScale * AttackerLevel + baseConstant))
FinalDamage = Damage * (1 - Reduction)
```

### Parameters

| Parameter | Value | Purpose |
|-----------|-------|---------|
| `levelScale` | 9.0 | How much each attacker level increases armor demand |
| `baseConstant` | 50.0 | Floor constant — ensures meaningful scaling at level 1 |
| `maxReduction` | 0.90 | Hard cap (90%), prevents immunity |
| `minEffectiveness` | 0.50 | Armor pen floor (unchanged) |

### Design Rationale

**Why k=9?** At level 100, `k*100 + c = 950`. A max tank (954 armor) gets `954/1904 = 50.1%` reduction — exactly the sweet spot where armor is strong but not dominant. Physical resistance (separate layer) handles the remaining mitigation gap.

**Why c=50?** Without a base constant, level 1 mobs create a denominator of just 9, making any armor extremely powerful. `c=50` ensures at level 1 the denominator is 59, giving 27 armor → 31% reduction (reasonable for a tank).

**Why attacker level (not player level)?**
- Armor naturally weakens against higher-level mobs (realm difficulty scaling)
- Players feel powerful vs lower-level content
- In realms, mob levels are set by tier — creates organic gear checks
- Player level would give constant reduction regardless of content difficulty

---

## 4. Worked Examples

### Tank Build (PURE_EARTH) vs Same-Level Mobs

| Level | Armor | Denominator | **New Reduction** | Current (vs Hostile) | Current (vs Boss) |
|-------|-------|-------------|-------------------|---------------------|-------------------|
| 1 | 27 | 86 | **31.6%** | 21.4% | 8.3% |
| 10 | 117 | 257 | **45.6%** | 54.0% | 28.1% |
| 25 | 274 | 549 | **49.9%** | 66.9% | 40.2% |
| 50 | 554 | 1054 | **52.5%** | 61.0% | 34.3% |
| 75 | 728 | 1453 | **50.1%** | 87.5% | 70.1% |
| 100 | 954 | 1904 | **50.1%** | 82.2% | 60.7% |

**Key insight**: Tank armor reduction is now a **consistent 45-53%** across all levels, vs the current wild swings of 34-88%.

### Non-Tank Build (PURE_FIRE) vs Same-Level Mobs

| Level | Armor | Denominator | **New Reduction** | Current (vs Hostile) | Current (vs Boss) |
|-------|-------|-------------|-------------------|---------------------|-------------------|
| 1 | 24 | 83 | **28.9%** | 19.2% | 7.4% |
| 10 | 40 | 180 | **22.2%** | 28.7% | 11.8% |
| 25 | 122 | 397 | **30.7%** | 47.4% | 23.1% |
| 50 | 228 | 728 | **31.3%** | 39.2% | 17.7% |
| 75 | 281 | 1006 | **27.9%** | 73.1% | 47.5% |
| 100 | 420 | 1370 | **30.7%** | 67.1% | 40.4% |

**Key insight**: Non-tank gets a reliable ~28-31%. Against big hits (boss/backstab) this is dramatically better than current (7-18%).

### The Playtest Scenario (Level 12, ~35 Armor)

| Scenario | Current Formula | New Formula |
|----------|----------------|-------------|
| vs 10 dmg basic attack | 25.9% | **18.1%** |
| vs 50 dmg charged attack | 6.5% | **18.1%** |
| vs 100 dmg backstab | **3.4%** | **18.1%** |
| vs 150 dmg boss slam | 2.3% | **18.1%** |

The problem hit was 2.7-3.4% reduction. New formula gives **18.1%** — consistent and meaningful.

### Fighting Out-of-Level Mobs (Tank at Level 50, 554 Armor)

| Mob Level | Reduction | Feel |
|-----------|-----------|------|
| 25 (-25) | 57.4% | "I'm overleveled, these are easy" |
| 40 (-10) | 55.3% | Comfortable |
| 50 (same) | 52.5% | Balanced — armor doing its job |
| 60 (+10) | 48.4% | Starting to feel harder |
| 75 (+25) | 43.3% | These hurt more — gear up! |
| 100 (+50) | 36.6% | Way overleveled content, genuine danger |

---

## 5. Interaction With Physical Resistance

Physical resistance is a **separate multiplicative layer** (flat %, 75% cap):

```
FinalPhysDmg = Damage * (1 - ArmorReduction) * (1 - PhysResist/100)
```

### Combined Reduction at Level 100

| Build | Armor Red. | Phys Resist | Combined | Damage Taken |
|-------|-----------|-------------|----------|--------------|
| Full Tank (Earth+gear) | 50% | 25% | 62.5% | 37.5% |
| Tank (moderate Earth) | 40% | 15% | 49.0% | 51.0% |
| Balanced | 30% | 10% | 37.0% | 63.0% |
| Glass (no Earth) | 20% | 5% | 24.0% | 76.0% |

These numbers feel right:
- Full tank takes ~38% of physical damage — strong but not invincible
- Glass cannon takes ~76% — squishy but not one-shot
- Clear reward for defensive investment
- Room for blocking, evasion, and energy shield to add further layers

---

## 6. Config Changes

### config.yml — Armor Section

```yaml
# BEFORE:
armor:
  includeEquipmentArmor: true
  equipmentArmorMultiplier: 1.0
  maxReduction: 0.90
  formulaDivisor: 10.0
  armorPenetrationFloor: 0.5

# AFTER:
armor:
  includeEquipmentArmor: true
  equipmentArmorMultiplier: 1.0
  maxReduction: 0.90
  # Level-scaled formula: Armor / (Armor + levelScale * AttackerLevel + baseConstant)
  # Replaces old PoE formula: Armor / (Armor + formulaDivisor * Damage)
  levelScale: 9.0           # How much each attacker level increases armor demand
  baseConstant: 50.0         # Floor constant (behavior at level 0)
  armorPenetrationFloor: 0.5 # Unchanged
```

### No Changes to Armor Sources

Armor values from gear, attributes, and skill tree remain unchanged:
- Earth attribute: 3.5 armor per point (config.yml)
- Gear implicit plate: base_min 5, base_max 12, scale_factor 80 (gear-balance.yml)
- Gear modifier "of the Fortress": base_min 5, base_max 15 (gear-modifiers.yml)
- Skill tree nodes: various flat values (skill-tree.yml)

This is a formula-only change. No rebalancing of armor sources required.

---

## 7. Code Changes

### CombatCalculator.java (lines 20-109, 315-365)

1. Replace `armorFormulaDivisor` field with `armorLevelScale` and `armorBaseConstant`
2. Update constructors and setters
3. Change the reduction formula to use attacker level instead of damage

```java
// OLD (line 349):
float reduction = effectiveArmor / (effectiveArmor + armorFormulaDivisor * damage);

// NEW:
float reduction = effectiveArmor / (effectiveArmor + armorLevelScale * attackerLevel + armorBaseConstant);
```

4. Add `attackerLevel` parameter to `calculateDefenderReduction()`:

```java
// OLD:
public ArmorResult calculateDefenderReduction(float damage, ComputedStats defenderStats, float armorPenetration)

// NEW:
public ArmorResult calculateDefenderReduction(float damage, ComputedStats defenderStats, float armorPenetration, int attackerLevel)
```

### RPGDamageCalculator.java

Pass the attacker's level to `calculateDefenderReduction()`. The attacker level is available from `MobScalingComponent` (realm mobs) or `MobStats` (mob scaling system).

### ConfigManager.java

Read `levelScale` and `baseConstant` instead of `formulaDivisor`.

### ComprehensiveScenario.java (simulation)

Update defense_layers calculation from:
```java
armorReduction = Math.min(0.9f, armor / (armor + mobDmg * 10f));
```
to:
```java
armorReduction = Math.min(0.9f, armor / (armor + 9f * level + 50f));
```

---

## 8. Sensitivity Analysis

### If 50% feels too high for tanks, lower k:

| k | Tank@100 | Glass@100 | Character |
|---|----------|-----------|-----------|
| 8 | 52.9% | 33.1% | Slightly more generous |
| **9** | **50.1%** | **30.7%** | **Recommended** |
| 10 | 47.6% | 28.6% | Slightly stingier |
| 12 | 43.3% | 25.9% | Much stingier (PoE 2 equivalent) |

### If level 1 gives too much reduction, raise c:

| c | Tank@Lv1 | Glass@Lv1 | Effect |
|---|----------|-----------|--------|
| 30 | 41.1% | 37.9% | Generous early game |
| **50** | **31.6%** | **28.9%** | **Recommended** |
| 75 | 24.5% | 22.1% | Stingier early |
| 100 | 19.8% | 17.9% | Very stingy early (PoE-like) |

---

## 9. Tooltip / Display Implications

The stat sheet can now display a **single consistent number**:

```
Armor: 554 (53% reduction vs Lv50)
```

This replaces the old confusing display where reduction varied by hit size. The reference level can be the player's own level or the current realm tier.

---

## 10. Verification Plan

1. Update the simulation formula in `ComprehensiveScenario.java`
2. Run `./gradlew simulate`
3. Verify defense_layers.csv shows consistent 45-53% for tank and 28-31% for non-tank
4. Check that EHP multiplier no longer has death valley swings
5. In-game playtest at level 12: get hit by backstab, verify ~18% reduction (not 2.7%)

---

## Decision Required

**Recommended**: Approach B with k=9, c=50 (level-scaled formula).

Before implementing, validate with simulation. If the numbers feel right in the simulation output, implement the code changes. The formula change is isolated to `CombatCalculator.calculateDefenderReduction()` — minimal blast radius.
