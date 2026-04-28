# Gear Scaling Calculations Reference

This document provides a complete reference for how gear stats are calculated in TrailOfOrbis.

## Overview

Gear stats come from multiple sources that multiply together:
1. **Implicit Damage** (weapons only) - Base damage range
2. **Modifiers** (prefix/suffix) - Random stat bonuses
3. **Rarity Multiplier** - Quality tier bonus
4. **Exponential Scaling** - Level-based power curve
5. **Quality Multiplier** - Roll quality (1-101)

---

## 1. Exponential Scaling (`LevelScaling.java`)

### Formula
```java
// Same formula used by both gear AND mobs
bonusPercent = 7.35 × log₁₀(level)^2.75
multiplier = 1.0 + bonusPercent / 100
```

### Key Values
| Level | Multiplier | % Bonus |
|-------|------------|---------|
| 1 | 1.0× | 0% |
| 10 | ~1.08× | ~8% |
| 20 | ~1.15× | ~15% |
| 50 | ~1.36× | ~36% |
| 100 | ~1.50× | ~50% |
| 1000 | ~2.50× | ~150% |

### Code Location
- Utility: `LevelScaling.java`
- Gear config: `GearBalanceConfig.ExponentialScalingConfig`
- Called in: `ModifierPool.java:354`, `ImplicitDamageCalculator.java` (uses `getBonusPercent` for additive growth), `GearModifierRoller.java:558`

---

## 2. Implicit Damage (Weapons)

### Overview

All weapons share a **unified base damage range** (1–5). Weapon type is purely a playstyle choice — it does not affect implicit damage. Growth is **additive** via `LevelScaling.getBonusPercent(level)`, not multiplicative.

**Excluded**: Thrown weapons have no implicit damage.

### Formula
```java
// From ImplicitDamageCalculator.java
bonusPercent = LevelScaling.getBonusPercent(itemLevel);  // 7.35 × log₁₀(level)^2.75
scaledMin = baseMin + bonusPercent / 100.0 * scaleFactor;
scaledMax = baseMax + bonusPercent / 100.0 * scaleFactor;

// 2H weapons get 2× multiplier applied after scaling
if (isTwoHanded) {
    scaledMin *= 2.0;
    scaledMax *= 2.0;
}
```

### Unified Base Values (`gear-balance.yml`)
```yaml
implicit_damage:
  base_min: 1.0
  base_max: 5.0
  scale_factor: 55.0
  two_handed_multiplier: 2.0
```

### Example Values (1H Weapon)
| Level | Bonus% | Min | Max | Implicit |
|-------|--------|-----|-----|----------|
| 1 | 0% | 1.0 | 5.0 | [1-5] Physical Damage |
| 10 | ~7% | ~5.1 | ~9.1 | [5-9] Physical Damage |
| 50 | ~32% | ~18.4 | ~22.4 | [18-22] Physical Damage |
| 100 | ~49% | ~28.2 | ~32.2 | [28-32] Physical Damage |

**2H weapons** get 2× these values (e.g., level 50: [36-44]).

### Key Design Decisions
- **Unified base**: All weapon types (sword, axe, bow, staff, etc.) share the same implicit range. Weapon type is purely a playstyle/animation choice.
- **Additive growth**: Uses `bonusPercent / 100 × scale_factor` added to base, not multiplied. This gives a flatter power curve than the old multiplicative system.
- **Thrown weapons excluded**: Thrown weapons have no implicit damage at all.
- **2H multiplier**: Two-handed weapons deal 2× implicit to compensate for no shield slot.

---

## 3. Rarity System

### Rarity Multipliers (`gear-balance.yml`)

| Rarity | Stat Mult | Jump | Modifiers | Prefix | Suffix | Drop Weight | Drop % |
|--------|-----------|------|-----------|--------|--------|-------------|--------|
| Common | 0.3× | — | 0-1 | 0-1 | 0-1 | 64.0 | 75.0% |
| Uncommon | 0.5× | +0.2 | 0-2 | 0-1 | 0-2 | 16.0 | 18.8% |
| Rare | 0.8× | +0.3 | 0-3 | 0-2 | 0-2 | 4.0 | 4.7% |
| Epic | 1.2× | +0.4 | 1-4 | 1-2 | 1-2 | 1.0 | 1.2% |
| Legendary | 1.7× | +0.5 | 2-5 | 2-2 | 2-3 | 0.25 | 0.29% |
| Mythic | 2.3× | +0.6 | 3-6 | 3 | 3 | 0.0625 | 0.073% |
| Unique | 2.8× | +0.5 | 3-6 | 3 | 3 | 0.016 | 0.018% |

**Total spread: 9.3×** (Unique/Common). Drop weights follow a 4× geometric progression. Accelerating stat jumps ensure each tier upgrade feels proportionally more exciting.

### Special Rarity Properties
- **Mythic**: `min_roll_percentile: 0.75` (top 25% rolls)
- **Unique**: `min_roll_percentile: 0.80` (top 20% rolls)

---

## 4. Modifier Rolling

### Formula
```java
// From GearModifierRoller.java
expMultiplier = balanceConfig.exponentialScaling().calculateMultiplier(itemLevel);
baseValue = modifier.getBaseValue();
scaledValue = baseValue × (1 + itemLevel × levelScalingFactor) × expMultiplier;
finalValue = scaledValue × rarityMultiplier × qualityMultiplier × rollVariance;
```

### Config Values
```yaml
modifier_scaling:
  global_scale_per_level: 0.02    # +2% per level
  roll_variance: 0.3              # ±30% variance
```

### Weight Categories
| Category | Weight | Examples |
|----------|--------|----------|
| Very Common | 100 | Physical Damage, Max Health, Armor |
| Common | 50 | Elemental Damage, Resistances |
| Uncommon | 25 | Crit Chance, Attack Speed |
| Rare | 10 | Life Steal, Crit Multiplier |
| Very Rare | 5 | Penetration, True Damage |

---

## 5. Quality System

### Formula
```java
// Quality 50 = baseline (1.0× multiplier)
qualityMultiplier = 0.5 + quality / 100.0;

// Quality range: 1-101
// Quality 1 = 0.51×
// Quality 25 = 0.75×
// Quality 50 = 1.0×
// Quality 75 = 1.25×
// Quality 100 = 1.5×
// Quality 101 = 1.51× (perfect, drop-only)
```

**Compressed range (~3× spread)**: Quality is meaningful (±50% swing) but cannot override a full rarity tier.

### Drop Distribution
| Range | Chance | Description |
|-------|--------|-------------|
| 1-25 | 15% | Poor |
| 26-49 | 25% | Below Average |
| 50 | 10% | Normal |
| 51-75 | 30% | Above Average |
| 76-100 | 19.5% | Excellent |
| 101 | 0.5% | Perfect |

---

## 6. Complete Gear Example

### Level 50 Epic Sword

**Step 1: Implicit Damage** (1H sword, unified base)
```
baseMin = 1.0, baseMax = 5.0, scaleFactor = 55.0
bonusPercent = 31.6% (level 50)
scaledMin = 1.0 + 0.316 × 55 = 18.4
scaledMax = 5.0 + 0.316 × 55 = 22.4
Implicit: [18-22] Physical Damage
```

**Step 2: Modifier Rolling**
```
Epic = 1-3 prefixes, 1-3 suffixes
Roll: 2 prefixes, 2 suffixes

Prefix 1: "+X Physical Damage"
  baseValue = 10
  scaled = 10 × (1 + 50×0.02) × 1.36 × 1.0 × rollVariance
  scaled = 10 × 2.0 × 1.36 × 1.0 × ~1.15 = ~31 Physical Damage

Prefix 2: "+X% Increased Physical Damage"
  baseValue = 15%
  scaled = 15 × (1 + 50×0.02) × 1.36 × 1.0 × rollVariance
  scaled = 15 × 2.0 × 1.36 × 1.0 × ~1.0 = ~41% Increased Physical Damage
```

**Step 3: Quality Application**
```
Quality roll: 72 (Above Average)
qualityMultiplier = 0.5 + 72/100 = 1.22×

All modifier values × 1.22
+31 Physical → +38 Physical
+41% Increased → +50% Increased
```

**Final Item:**
```
[Epic] Iron Sword of Carnage
Item Level: 50
Quality: 72

[19-23] Physical Damage (implicit)
+38 Physical Damage
+50% Increased Physical Damage
+23% Critical Chance
+147% Critical Multiplier
```

---

## 7. Attribute Requirements

### Formula
```java
// From gear-balance.yml
baseRequirement = itemLevel × level_to_base_ratio;  // 0.17
finalRequirement = baseRequirement × rarityMultiplier;
```

### Rarity Requirement Multipliers
| Rarity | Multiplier | Level 50 Req |
|--------|------------|--------------|
| Common | 0.1 | ~1 per attr |
| Uncommon | 0.25 | ~2 per attr |
| Rare | 0.5 | ~4 per attr |
| Epic | 0.75 | ~6 per attr |
| Legendary | 0.9 | ~8 per attr |
| Mythic | 1.0 | ~9 per attr |
| Unique | 1.0 | ~9 per attr |

---

## 8. Slot Weights

### Power Distribution
| Slot | Weight | Purpose |
|------|--------|---------|
| Weapon | 30% | Primary damage source |
| Chest | 20% | Main armor |
| Legs | 23% | Armor + movement (no feet slot) |
| Head | 12% | Secondary armor |
| Hands | 8% | Minor slot |
| Shield | 7% | Optional defensive |

---

## 9. Key Code Locations

| Component | File | Purpose |
|-----------|------|---------|
| Balance config | `GearBalanceConfig.java` | All gear balance values |
| Implicit damage | `ImplicitDamageCalculator.java` | Weapon base damage |
| Modifier rolling | `GearModifierRoller.java` | Random stat generation |
| Modifier pool | `ModifierPool.java` | Available modifiers per slot |
| Quality | `GearQualityCalculator.java` | Quality determination |
| Level scaling | `LevelScaling.java` | Exponential multiplier |

---

## 10. Formula Quick Reference

```
# Implicit Damage (unified base, additive growth)
implicitDamage = base + LevelScaling.getBonusPercent(level) / 100 × scaleFactor
# 2H weapons: implicitDamage × 2.0

# Modifier Value
modValue = baseValue × (1 + level × 0.02) × expMultiplier × rarityMult × qualityMult × rollVariance

# Quality Multiplier
qualityMult = 0.5 + quality / 100.0

# Exponential Multiplier (shared with mobs!)
expMult = 1.0 + 7.35 × log₁₀(level)^2.75 / 100

# Attribute Requirement
requirement = level × 0.17 × rarityMult
```

---

## 11. Balance Relationship with Mobs

**CRITICAL**: Gear and mobs use the SAME `LevelScaling.getMultiplier()` function!

This ensures:
- Level 50 gear is ~36% stronger than level 1 gear
- Level 50 mobs are ~36% stronger than level 1 mobs
- Combat balance remains consistent across levels

If you change `LevelScaling.java`, it affects BOTH systems!
