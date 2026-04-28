# Player Stats & Combat Calculations Reference

This document covers player attribute system, stat calculation, and combat formulas.

## 1. Base Player Stats

### Starting Stats (`BaseStats.java`)

| Stat | Value | Notes |
|------|-------|-------|
| Max HP | 100 | Modified by attributes |
| Max Mana | 0 | Gained from FIRE |
| Max Stamina | 10 | For actions |
| Crit Chance | 5% | Base chance |
| Crit Multiplier | 150% | 1.5× damage |
| Base Accuracy | 10 | vs Evasion |
| Armor | 0 | From gear + EARTH |

### Starting Resources
- **1 Attribute Point** at level 1
- **+1 Attribute Point** per level
- **1 Skill Tree Point** at level 1
- **+1 Skill Tree Point** per level

---

## 2. Attribute System

### Element Grants (`config.yml`)

| Element | Primary Benefits | Penalty |
|---------|------------------|---------|
| **FIRE** | +0.5% phys dmg, +0.8% spell dmg, +1% crit mult, +2 mana | -0.5 HP |
| **WATER** | +1.5 HP, +0.3 HP/s regen, +3 barrier, +0.2 mana/s | -0.2% move speed |
| **LIGHTNING** | +0.5% atk speed, +0.3% move, +0.15% crit chance, +0.3% spell | None |
| **EARTH** | +8 armor, +1 HP, +0.3% block, +1.5 thorns | None |
| **WIND** | +8 evasion, +0.25% move, +5 accuracy, +0.8% proj dmg | None |
| **VOID** | +0.15% life steal, +1% low-life dmg, +0.5% spell dmg | -0.3% max HP |

### Build Examples

**Level 50 FIRE (Glass Cannon):**
```
HP: 100 - (50 × 0.5) = 75 HP
Phys Damage: +25%
Spell Damage: +40%
Crit Multiplier: 150% + 50% = 200%
Mana: +100
```

**Level 50 EARTH (Tank):**
```
HP: 100 + (50 × 1) = 150 HP
Armor: 50 × 8 = 400
Block Chance: 50 × 0.3% = 15%
Thorns: 50 × 1.5 = 75
```

**Level 50 VOID:**
```
HP: 100 × (1 - 50 × 0.003) = 85 HP
Life Steal: 50 × 0.15% = 7.5%
Low-Life Damage: 50 × 1% = +50%
Spell Damage: +25%
```

---

## 3. Damage Calculation Order

### Attack Flow (`RPGDamageCalculator.java`)

```
1. Base damage (weapon implicit — unified across all weapon types, scales additively with level)
2. + Flat physical (from attributes/gear)
3. + Flat elemental (from gear)
4. Damage conversion (phys → elemental, cap 100%)
5. × % Increased (sum all: physDmg% + meleeDmg% + dmg%)
6. × Elemental modifiers (per-element %)
7. × % More multipliers (multiplicative chain)
8. × Conditional (realm dmg, execute, vs frozen, low life)
9. × Crit multiplier (if rolled crit)
10. Apply defenses (armor for phys, resistance per element)
11. + True damage (bypasses all defenses)
```

### Damage Type Flags
- `isPhysical` - Physical damage (reduced by armor)
- `isElemental` - Elemental damage (reduced by resistance)
- `isMelee` - Close range attack
- `isRanged` - Distance attack
- `isProjectile` - Projectile attack
- `isSpell` - Magic attack

---

## 4. Armor Formula (PoE-Style)

### Formula
```java
// Physical damage reduction
reduction = armor / (armor + 10 × damage)
reduction = min(reduction, 0.90)  // Cap at 90%
```

### Armor Effectiveness Table

| Armor | vs 10 dmg | vs 50 dmg | vs 100 dmg | vs 200 dmg |
|-------|-----------|-----------|------------|------------|
| 100 | 50% | 17% | 9% | 5% |
| 500 | 83% | 50% | 33% | 20% |
| 1000 | 91% | 67% | 50% | 33% |
| 2000 | 95% | 80% | 67% | 50% |

**Key Insight**: Armor is more effective vs small hits. Big hits go through.

### Armor Penetration
```java
// Penetration reduces effective armor (floor at 50%)
effectiveArmor = armor × max(1.0 - penetration, 0.5);
```

Even 100% penetration only halves armor, never bypasses completely.

---

## 5. Elemental Resistance

### Formula
```java
// Per-element damage reduction
reduction = resistance / 100.0;
finalDamage = baseDamage × (1.0 - reduction);
```

### Resistance Cap
- Hard cap: 90% (never immune)
- Penetration reduces resistance before cap check

### Penetration
```java
// Penetration subtracts from resistance
effectiveResistance = max(resistance - penetration, 0);
```

---

## 6. Critical Hits

### Crit Chance Roll
```java
// Base 5% + gear + attributes + buffs
totalCritChance = baseCrit + bonusCrit;
isCrit = random() < (totalCritChance / 100.0);
```

### Crit Damage
```java
// Base 150% + gear + attributes
critMultiplier = baseCritMult + bonusCritMult;
finalDamage = preCritDamage × (critMultiplier / 100.0);
```

---

## 7. Evasion & Accuracy (PoE-Style)

### Formula
```java
// From AvoidanceProcessor.calculateHitChance()
// Configurable PoE-style formula with diminishing returns

scaledEvasion = (evasion × scalingFactor) ^ exponent;
hitChance = (hitChanceConstant × accuracy) / (accuracy + scaledEvasion);
hitChance = clamp(hitChance, minHitChance, maxHitChance);

// Evasion roll
isEvaded = random() > hitChance;
```

### Configurable Parameters (via `config.yml` → `evasion`)

| Parameter | Default | Effect |
|-----------|---------|--------|
| `scalingFactor` | 0.2 | Scales raw evasion before exponent |
| `exponent` | 0.9 | Diminishing returns curve (<1.0 = diminishing) |
| `hitChanceConstant` | 1.0 | Multiplier on accuracy numerator |
| `minHitChance` | 0.05 | Floor: 5% minimum hit chance |
| `maxHitChance` | 0.95 | Cap: 95% maximum hit chance |

### Example Values (with defaults)

| Accuracy | vs 100 Evasion | vs 500 Evasion |
|----------|----------------|----------------|
| 100 | ~87% hit | ~56% hit |
| 200 | ~93% hit | ~70% hit |
| 500 | ~97% hit | ~84% hit |

---

## 8. Block & Parry

### Active Block (Shield Held)
```java
// Active block — requires shield held and detected via DamageDataComponent.getCurrentWielding()
isActivelyBlocking = wielding != null;  // player is holding block input with shield
if (isActivelyBlocking) {
    // Stamina consumed per block (see StaminaCost formula below)
    // Damage reduced by wielding's DamageModifiers multiplier
    finalDamage × damageModifier;  // typically 0.0-0.3
}
```

### Passive Block (Random Proc)
```java
// Passive block — random proc from passiveBlockChance stat (no shield required)
isPassivelyBlocked = random() < (passiveBlockChance / 100.0);
if (isPassivelyBlocked) finalDamage = 0;
```

### Parry
```java
// Parry reduces damage by 50% and reflects partial damage back to attacker
isParried = random() < (parryChance / 100.0);
if (isParried) {
    finalDamage × 0.5;
    reflectedDamage = originalDamage × reflectMultiplier;  // applied to attacker
}
```

---

## 9. Life Steal

### Formula
```java
// Heal based on damage dealt (post-mitigation on target)
healAmount = damageDealt × (lifeSteal / 100.0);
player.heal(healAmount);
```

### Life Steal Cap
- No hard cap, but diminishing returns on healing
- Effective against many small hits

---

## 10. Low-Life Mechanics (VOID)

### Low-Life Threshold
```java
// Player is "low life" when below 35% HP
isLowLife = currentHP / maxHP < 0.35;
```

### Low-Life Damage Bonus
```java
// VOID attribute grants +1% damage per point when low life
if (isLowLife) {
    damageMultiplier = 1.0 + (voidPoints × 0.01);
}
```

---

## 11. Barrier (WATER)

### Barrier Mechanics
```java
// Barrier absorbs damage before HP
// +3 barrier per WATER point
barrier = waterPoints × 3;

// Damage absorption
if (barrier > 0) {
    absorbed = min(damage, barrier);
    barrier -= absorbed;
    damage -= absorbed;
}
```

---

## 12. Thorns (EARTH)

### Thorns Formula
```java
// Reflect damage to melee attackers
// +1.5 thorns per EARTH point
thornsDamage = earthPoints × 1.5;

// On being hit by melee
if (isMeleeAttack) {
    attacker.damage(thornsDamage);  // True damage
}
```

---

## 13. Combat Stats Summary

### Offensive Stats
| Stat | Source | Effect |
|------|--------|--------|
| Physical Damage | Weapon + Gear + FIRE | Base attack damage |
| Elemental Damage | Gear + Attributes | Converted/added damage |
| % Increased | Gear + FIRE | Additive bonus |
| % More | Gear + Skills | Multiplicative bonus |
| Crit Chance | Base + Gear + LIGHTNING | Chance for crit |
| Crit Multiplier | Base + Gear + FIRE | Crit damage bonus |
| Attack Speed | Gear + LIGHTNING | Attacks per second |
| Accuracy | Base + Gear + WIND | Hit chance |

### Defensive Stats
| Stat | Source | Effect |
|------|--------|--------|
| Max HP | Base + WATER + EARTH - FIRE - VOID | Total health pool |
| Armor | Gear + EARTH | Phys damage reduction |
| Elemental Resistance | Gear | Element damage reduction |
| Evasion | Gear + WIND | Dodge chance |
| Block Chance | Gear + EARTH | Block with shield |
| Parry Chance | Gear | Reduce damage on parry |
| HP Regen | WATER | HP per second |
| Barrier | WATER | Damage absorption |

### Sustain Stats
| Stat | Source | Effect |
|------|--------|--------|
| Life Steal | Gear + VOID | Heal on hit |
| Mana Regen | WATER | Mana per second |
| HP Regen | WATER | HP per second |

---

## 14. Player vs Mob Balance Target

### Design Goal
- Player should 4-5 shot same-level mob
- Mob should 6-10 shot same-level player

### Example: Level 50 Combat

**Player (Epic gear, 50 EARTH):**
```
HP: 150
Armor: 400
Weapon damage: 100-150
```

**Level 50 Mob:**
```
HP: ~167 (with exponential scaling)
Damage: ~14 (weighted formula)
Armor: ~5
```

**Player → Mob:**
```
Damage: ~125 average
Mob armor reduction: 5/(5+1250) = 0.4%
Damage dealt: ~124
Hits to kill: 167/124 = ~1.3 hits (slight overkill)
```

**Mob → Player:**
```
Damage: ~14
Player armor reduction: 400/(400+140) = 74%
Damage taken: 14 × 0.26 = ~4
Hits to kill: 150/4 = ~38 hits
```

**Note**: These numbers show the EARTH tank is very survivable. Glass cannon builds (FIRE/VOID) trade HP for damage.

---

## 15. Key Code Locations

| Component | File | Purpose |
|-----------|------|---------|
| Base stats | `BaseStats.java` | Starting values |
| Attribute grants | `AttributeGrants.java` | Per-point bonuses |
| Damage calculator | `RPGDamageCalculator.java` | Full damage pipeline |
| Armor formula | `ArmorCalculator.java` | Physical reduction |
| Crit handling | `CriticalHitCalculator.java` | Crit rolls |
| Evasion | `EvasionCalculator.java` | Hit/evade rolls |
| Computed stats | `ComputedStats.java` | Final player stats |

---

## 16. Formula Quick Reference

```
# Armor Reduction
reduction = armor / (armor + 10 × damage)

# Elemental Reduction
reduction = resistance / 100

# Hit Chance (vs Evasion)
hitChance = accuracy / (accuracy + evasion/4)

# Critical Damage
critDamage = damage × (critMultiplier / 100)

# Life Steal
heal = damageDealt × (lifeSteal / 100)

# Low-Life Bonus (VOID)
damageBonus = voidPoints × 1% (when HP < 35%)

# Thorns (EARTH)
thornsDamage = earthPoints × 1.5 (vs melee)

# Barrier (WATER)
barrier = waterPoints × 3
```
