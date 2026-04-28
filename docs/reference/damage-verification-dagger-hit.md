# Damage Calculation Verification: Onyxium Daggers Signature Attack

**Timestamp**: 2026-02-09 23:19:05
**Weapon**: Weapon_Daggers_Onyxium (Level 20)
**Attack Type**: Signature Attack (MELEE)
**Target**: Training Dummy (no armor/resistances)

---

## 1. Player Attributes

| Attribute | Points | Effect |
|-----------|--------|--------|
| **STR** | 5 | +1.0 flat phys, +1.0% phys dmg |
| **DEX** | 0 | +0% crit multiplier |
| **INT** | 5 | (not relevant for physical) |
| **VIT** | 2 | (not relevant for damage) |
| **LUCK** | 15 | +1.5% crit chance |

### Derived Stats from Attributes

| Stat | Formula | Expected | Logged | ✓ |
|------|---------|----------|--------|---|
| Crit Chance | 5% base + (15 × 0.1%) | 6.5% | 6.5% | ✅ |
| Crit Multiplier | 150% base + (0 × 1%) | 150% (x1.50) | x1.50 | ✅ |
| Flat Physical | 5 × 0.2 | 1.0 | 1.0 | ✅ |
| Physical % | 5 × 0.2% | 1.0% | 1.0% | ✅ |

---

## 2. Weapon Stats

### Implicit Damage (Base Weapon Damage)

**From Debug Log:**
```
WeaponImplicit[physical_damage: 7.9 (range: 5.9-11.8, 34%)]
Weapon implicit damage: 4.1 (rolled: 7.9, quality: 0.52x)
```

| Property | Value | Verification |
|----------|-------|--------------|
| Level | 20 | - |
| Weapon Type | Dagger | Base range at L1: 2.0-4.0 |
| Scaling Multiplier | 2.96× | `1.0 + 0.0049 × 400 = 2.96` |
| Scaled Range | 5.9 - 11.8 | `2.0×2.96 = 5.92`, `4.0×2.96 = 11.84` ✅ |
| Rolled Value | 7.9 | 34th percentile within range ✅ |
| Quality | 52% (0.52×) | Low quality roll |
| **Final Base Damage** | **4.1** | `7.9 × 0.52 = 4.108` ✅ |

### Weapon Modifiers

| Modifier | Expected | Logged | ✓ |
|----------|----------|--------|---|
| +8.5% Melee Damage | melee%=8.5 | Melee: %=8.5 | ✅ |
| +6.4 Cold Damage | cold=6.4 | Cold: 6.4+0.0% | ✅ |

**Note:** The "8 phys dmg" mentioned is the rolled implicit (7.9 ≈ 8), not a flat modifier.

---

## 3. Damage Calculation Step-by-Step

### Input Values

| Source | Stat | Value |
|--------|------|-------|
| Weapon Implicit | Base Damage | 4.1 |
| STR (5 pts) | Flat Physical | +1.0 |
| STR (5 pts) | Physical Damage % | +1.0% |
| Weapon Mod | Melee Damage % | +8.5% |
| Weapon Mod | Flat Cold | +6.4 |
| Vanilla Attack | Signature Damage | 31.0 |
| Config | Fallback Reference | 20.0 |

### Calculation Trace

```
Step 1: Base + Flat Damage
───────────────────────────
  base        = 4.1  (weapon implicit after quality)
  + flatPhys  = 1.0  (from 5 STR)
  + flatMelee = 0.0  (none equipped)
  ─────────────────
  Result      = 5.1 ✅

Step 2: % Increased (Additive)
──────────────────────────────
  physDmg%  = 1.0%  (from 5 STR)
  + melee%  = 8.5%  (from weapon)
  + dmg%    = 0.0%  (none)
  ─────────────────
  Total     = 9.5%

  5.1 × (1 + 9.5/100) = 5.1 × 1.095 = 5.5845 ≈ 5.6 ✅

Step 3: % More (Multiplicative)
───────────────────────────────
  allDmg%    = 0.0 → ×1.0
  multiplier = 0.0 → ×1.0

  5.6 × 1.0 × 1.0 = 5.6 ✅

Step 3b: Attack Type Multiplier
───────────────────────────────
  vanillaDamage = 31.0 (signature attack)
  reference     = 20.0 (fallback_reference_damage)
  attackMult    = 31.0 / 20.0 = 1.55×

  5.6 × 1.55 = 8.68 ≈ 8.7 ✅

Step 4: Critical Roll
─────────────────────
  critChance = 6.5%
  roll       = (missed)

  8.7 × 1.0 = 8.7 ✅

Step 5: Conversion
──────────────────
  (no conversion stats)
  Physical remains: 8.7

Step 6: Flat Elemental
──────────────────────
  + Cold = 6.4

  Elemental added: 6.4 ✅

Step 9: Defenses
────────────────
  Defender Armor: 0.0
  All Resists: 0.0%

  Physical: 8.7 → 8.7 (no reduction)
  Cold: 6.4 → 6.4 (no reduction)
```

### Final Damage

```
Physical:   8.7
+ Cold:     6.4
───────────────
TOTAL:     15.1 ✅
```

**Logged Result:** `FINAL DAMAGE: 15.1 (PHYSICAL)` ✅

---

## 4. Summary: All Calculations Correct ✅

| Component | Expected | Actual | Status |
|-----------|----------|--------|--------|
| Crit Chance | 6.5% | 6.5% | ✅ |
| Crit Multiplier | 1.50× | 1.50× | ✅ |
| Flat Physical | 1.0 | 1.0 | ✅ |
| Physical % | 1.0% | 1.0% | ✅ |
| Weapon Base | 4.1 | 4.1 | ✅ |
| Melee % | 8.5% | 8.5% | ✅ |
| Cold Flat | 6.4 | 6.4 | ✅ |
| Attack Mult | 1.55× | 1.55× | ✅ |
| Step 1 Result | 5.1 | 5.1 | ✅ |
| Step 2 Result | 5.6 | 5.6 | ✅ |
| Step 3b Result | 8.7 | 8.7 | ✅ |
| Final Damage | 15.1 | 15.1 | ✅ |

---

## 5. Why Damage Seems Low

The final damage of **15.1** may seem low because:

1. **Low Quality Roll (52%)**: The weapon has poor quality, reducing base damage from 7.9 to 4.1
2. **Low Implicit Roll (34th percentile)**: Rolled 7.9 in a 5.9-11.8 range
3. **Low STR**: Only 5 points = +1.0 flat physical
4. **No % More multipliers**: allDmg% and damageMultiplier are both 0

### If Quality Was 100%:
- Base damage: 7.9 (not 4.1)
- Step 1: 7.9 + 1.0 = 8.9
- Step 2: 8.9 × 1.095 = 9.75
- Step 3b: 9.75 × 1.55 = 15.1
- Final: 15.1 + 6.4 = **21.5** (+42% more!)

### If Max Roll (11.8) + 100% Quality:
- Final would be approximately **29.1** damage
