# Mob Scaling Calculations Reference

This document provides a complete reference for how mob stats are calculated in TrailOfOrbis. Use this when working on balance, debugging mob stats, or understanding the scaling system.

## Overview: The Two Scaling Systems

Mobs use **two separate scaling systems** that must stay synchronized:

| System | Purpose | Applied In |
|--------|---------|------------|
| **Stat Pool** | Determines damage, armor, crit, etc. | `MobStatGenerator.java` |
| **Weighted HP** | Determines max health | `MobScalingSystem.java` |

Both systems use the same `LevelScaling.getMultiplier()` for exponential scaling.

---

## 1. Stat Pool Calculation (`MobStatGenerator.java`)

### Formula

```
scalingFactor = calculateScalingFactor(mobLevel)  // Progressive scaling
expMultiplier = LevelScaling.getMultiplier(mobLevel)  // Exponential scaling
levelPool = mobLevel × pointsPerLevel × scalingFactor
totalPool = (levelPool + distanceBonus) × expMultiplier
```

### Config Values (`mob-stat-pool.yml`)

| Config Key | Default | Purpose |
|------------|---------|---------|
| `points_per_level` | 20.0 | Base pool points per mob level |
| `distance_bonus_per_block` | 0.5 | Extra pool per block from origin |
| `progressive_scaling.enabled` | true | Enable early-game protection |
| `progressive_scaling.soft_cap_level` | 30 | Level at which progressive scaling caps |
| `progressive_scaling.min_scaling_factor` | 0.1 | Minimum scaling at level 1 |

### Progressive Scaling Formula

```java
// From MobStatPoolConfig.calculateScalingFactor()
if (mobLevel >= softCapLevel) return 1.0;
return minFactor + (1.0 - minFactor) × (mobLevel / softCapLevel);
```

**Example Values (default config):**
| Level | Progressive Scale | Explanation |
|-------|-------------------|-------------|
| 1 | 0.130 (13.0%) | 0.1 + 0.9 × (1/30) |
| 5 | 0.250 (25.0%) | 0.1 + 0.9 × (5/30) |
| 10 | 0.400 (40.0%) | 0.1 + 0.9 × (10/30) |
| 20 | 0.700 (70.0%) | 0.1 + 0.9 × (20/30) |
| 30+ | 1.0 (100%) | Capped |

### Exponential Scaling Formula

```java
// From LevelScaling.getMultiplier()
// Formula: 1.0 + 7.35 × log₁₀(level)^2.75 / 100
public static double getMultiplier(int level) {
    if (level <= 1) return 1.0;
    double logLevel = Math.log10(level);
    return 1.0 + 7.35 * Math.pow(logLevel, 2.75) / 100.0;
}
```

**Example Values:**
| Level | Exp Multiplier | Explanation |
|-------|----------------|-------------|
| 1 | 1.0× | Baseline |
| 20 | ~1.15× | +15% power |
| 50 | ~1.36× | +36% power |
| 100 | ~1.50× | +50% power |
| 1000 | ~2.50× | +150% power |

### Stat Distribution (Dirichlet)

The total pool is distributed across 53 stats using Dirichlet distribution:
- Each stat has an `alpha_weight` (default 1.0)
- Higher weight = more pool allocation
- `dirichlet_precision` (default 0.5) controls variance

### Stat Conversion Factors

Pool shares are converted to actual stats:
```
stat_value = base_value + (pool_share × factor)
stat_value = clamp(stat_value, min_value, max_value)
```

**Key Stats (`mob-stat-pool.yml`):**
| Stat | Factor | Base | Min | Max |
|------|--------|------|-----|-----|
| `max_health` | 0.88 | 100 | 1 | 100000 |
| `physical_damage` | 0.15 | 10 | 1 | 10000 |
| `armor` | 0.282 | 0 | 0 | 10000 |
| `critical_chance` | 0.11 | 5 | 0 | 75 |
| `critical_multiplier` | 0.44 | 150 | 100 | 500 |
| `move_speed` | 0.0022 | 1.0 | 0.3 | 3.0 |

---

## 2. Weighted HP Formula (`MobScalingSystem.java`)

### Why Weighted HP?

The weighted formula prevents vanilla HP from stacking with RPG HP. Instead:
- Vanilla HP is **replaced** by RPG HP
- Vanilla HP acts as a **weight** (via square root)
- High-HP vanilla mobs get proportionally more HP, but compressed

### Formula

```java
// Line 508-538 in MobScalingSystem.applyStatModifiers()

// Get vanilla HP from role
int vanillaHP = component.getVanillaHP();  // e.g., 100

// Get RPG target HP from stat pool
double rpgTargetHP = stats.maxHealth();  // e.g., 150

// Progressive scaling (0.15 → 1.0)
double progressiveScale = manager.getStatPoolConfig().calculateScalingFactor(mobLevel);

// Exponential scaling (1.0 → 1.5+ based on level)
double expMultiplier = manager.getConfig().getExponentialScaling().calculateMultiplier(mobLevel);

// Boss multiplier for floor value
double bossMultiplier = (classification == RPGMobClass.BOSS) ? 2.5 : 1.0;

// Weight: compresses vanilla range via square root
double weight = Math.sqrt((double) vanillaHP / 100.0);

// Effective RPG: floor prevents 0 HP at low levels
double effectiveRpg = Math.max(rpgTargetHP × progressiveScale × expMultiplier, 10.0 × bossMultiplier);

// Final HP
double finalHP = effectiveRpg × weight;

// Calculate multiplier to transform vanilla → final
float healthMultiplier = (float) (finalHP / vanillaHP);
```

### Weight Examples

| Vanilla HP | Weight | Effect |
|------------|--------|--------|
| 40 | 0.63 | Below baseline (smaller mob) |
| 100 | 1.0 | Baseline |
| 400 | 2.0 | 2× HP (not 4×!) |
| 1000 | 3.16 | Compressed high-HP boss |

### HP Floor

The `10.0 × bossMultiplier` floor ensures:
- Normal mobs: minimum 10 HP
- Boss mobs: minimum 25 HP (10 × 2.5)

This prevents level 1 mobs from having near-zero HP while keeping them killable in the early game.

---

## 3. Weighted Damage Formula (`RPGDamageSystem.java`)

### Formula (for mob → player damage)

```java
// Line 918-936 in RPGDamageSystem.applyMobDamageScaling()

// Get vanilla damage from attack
float vanillaDamage = damage.getAmount();

// Get RPG target damage from mob stats
double rpgTargetDmg = mobStats.physicalDamage();

// Progressive scaling
double progressiveScale = manager.getStatPoolConfig().calculateScalingFactor(mobLevel);

// Class multiplier (BOSS=2.5, ELITE=1.5, etc.)
double classMultiplier = getClassMultiplier(classification);

// Weight: compresses vanilla damage range
// Reference: 10 damage = weight 1.0
float effectiveVanilla = Math.max(vanillaDamage, 5f);
double weight = Math.sqrt(effectiveVanilla / 10.0);

// Effective RPG with floor
double minDamage = 5.0 × classMultiplier;
double effectiveRpg = Math.max(rpgTargetDmg × progressiveScale × classMultiplier, minDamage);

// Final damage
double finalDamage = effectiveRpg × weight;
```

### Damage Weight Examples

| Vanilla Damage | Weight | Effect |
|----------------|--------|--------|
| 5 | 0.71 | Light attack |
| 10 | 1.0 | Baseline |
| 40 | 2.0 | Heavy attack |
| 100 | 3.16 | Boss slam |

---

## 4. Complete Calculation Example

### Level 50 Hostile Mob (Vanilla 100 HP, 10 damage)

**Step 1: Pool Generation**
```
scalingFactor = 1.0 (level 50 > soft cap 30)
expMultiplier = LevelScaling.getMultiplier(50) ≈ 1.36
levelPool = 50 × 20 × 1.0 = 1000
distanceBonus = 0 (at spawn)
totalPool = (1000 + 0) × 1.36 = 1360 points
```

**Step 2: Pool Distribution**
```
With 52 stats and Dirichlet precision 0.5:
HP share ≈ 1360 / 52 × randomVariance ≈ 26 points
Damage share ≈ 1360 / 52 × randomVariance ≈ 26 points
```

**Step 3: Stat Conversion**
```
rpgTargetHP = 100 + (26 × 0.88) = 122.9 HP
rpgTargetDmg = 10 + (26 × 0.15) = 13.9 damage
```

**Step 4: Weighted HP Formula**
```
progressiveScale = 1.0
expMultiplier = 1.36
weight = √(100/100) = 1.0
effectiveRpg = max(122.9 × 1.0 × 1.36, 50) = 167.1
finalHP = 167.1 × 1.0 = 167 HP
```

**Step 5: Weighted Damage Formula**
```
progressiveScale = 1.0
classMultiplier = 1.0 (HOSTILE)
weight = √(10/10) = 1.0
effectiveRpg = max(13.9 × 1.0 × 1.0, 5) = 13.9
finalDamage = 13.9 × 1.0 = ~14 damage
```

**Final Stats:**
- HP: ~167 (was ~117 before exponential fix)
- Damage: ~14 (was ~13 before exponential fix)

---

## 5. Classification Multipliers

### From `mob-classification.yml`

| Classification | HP Mult | DMG Mult | XP Mult |
|----------------|---------|----------|---------|
| PASSIVE | 0.1 | 0.1 | 0.1 |
| HOSTILE | 1.0 | 1.0 | 1.0 |
| MINOR | 1.0 | 1.0 | 0.5 |
| ELITE | 1.5 | 1.5 | 1.5 |
| BOSS | 3.0 | 3.0 | 5.0 |

---

## 6. Key Code Locations

| Component | File | Key Lines |
|-----------|------|-----------|
| Pool generation | `MobStatGenerator.java` | 22-35 |
| Stat conversion | `MobStatGenerator.java` | 44-109 |
| Progressive scaling | `MobStatPoolConfig.java` | 671-683 |
| Exponential scaling | `LevelScaling.java` | 59-77 |
| Weighted HP formula | `MobScalingSystem.java` | 508-565 |
| Weighted damage formula | `RPGDamageSystem.java` | 918-936 |
| Config loading | `ConfigManager.java` | mob-stat-pool.yml |

---

## 7. Debugging Tips

### Check Mob Stats In-Game
```
/rpgadmin mob info
```

### Check Calculation Values
Enable FINE logging for `MobScalingSystem`:
```
[MobScaling] HP formula: vanilla=100, rpgTarget=123, progScale=1.00, expMult=1.36, weight=1.00, final=167, mult=1.67x
```

### Common Issues

| Symptom | Likely Cause |
|---------|--------------|
| Mobs too weak | Exponential scaling not applied |
| Mobs too strong at level 1 | Progressive scaling disabled |
| Boss HP too low | Floor multiplier not applied |
| Damage not scaling | Weighted formula using wrong vanillaDamage |

---

## 8. Balance Tuning

### Make Mobs Tankier
- Increase `stats.max_health.factor` (currently 0.88)
- Increase `points_per_level` (currently 20)

### Make Mobs Hit Harder
- Increase `stats.physical_damage.factor` (currently 0.15)
- Adjust class multipliers in `mob-classification.yml`

### Adjust Early Game Difficulty
- Change `progressive_scaling.min_scaling_factor` (currently 0.15)
- Change `progressive_scaling.soft_cap_level` (currently 30)

### Adjust Late Game Scaling
- Modify `LevelScaling.java` coefficient (currently 7.35) or exponent (currently 2.75)
- These affect both mobs AND gear - keep them in sync!

---

## 9. Formula Quick Reference

```
# Pool Generation
totalPool = (mobLevel × pointsPerLevel × progressiveScale + distanceBonus) × expMultiplier

# Progressive Scale (level < 30)
progressiveScale = 0.1 + 0.9 × (level / 30)

# Exponential Multiplier
expMultiplier = 1.0 + 7.35 × log₁₀(level)^2.75 / 100

# Weighted HP
finalHP = max(rpgTargetHP × progressiveScale × expMultiplier, 50 × bossMult) × √(vanillaHP / 100)

# Weighted Damage
finalDmg = max(rpgTargetDmg × progressiveScale × classMult, 5 × classMult) × √(vanillaDmg / 10)
```
