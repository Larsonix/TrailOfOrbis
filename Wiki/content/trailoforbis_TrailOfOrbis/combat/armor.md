---
name: Armor
title: Armor & Physical Defense
description: How armor reduces physical damage - the formula, 90% cap, and physical resistance
author: Larsonix
sort-index: 3
order: 44
published: true
---

# Armor & Physical Defense

Armor reduces physical damage with a level-scaled formula. Your reduction depends on your armor value and the attacker's level. Higher-level enemies are naturally harder to defend against, rewarding continued armor investment. Full immunity is never reachable.

---

## The Formula

```
armorReduction = armor / (armor + 5 × attackerLevel + 50)
armorReduction = min(armorReduction, 0.90)    // capped at 90%
finalPhysDamage = physDamage × (1 - armorReduction)
```

Your reduction is consistent regardless of hit size - 300 armor always gives the same % reduction against any mob of a given level. Higher-level attackers reduce armor effectiveness naturally.

### Reduction Table (vs same-level mobs)

| Your Armor | vs Lv10 | vs Lv30 | vs Lv50 | vs Lv100 |
|-----------:|--------:|--------:|--------:|---------:|
| 50 | **33%** reduced | **25%** reduced | **14%** reduced | **8%** reduced |
| 150 | **60%** reduced | **50%** reduced | **33%** reduced | **21%** reduced |
| 300 | **75%** reduced | **67%** reduced | **50%** reduced | **35%** reduced |
| 500 | **83%** reduced | **77%** reduced | **63%** reduced | **48%** reduced |

> [!IMPORTANT]
> Armor reduction is **capped at 90%**. No matter how much you stack, at least 10% of physical damage always gets through.

---

## Physical Resistance

After armor reduces the hit, a second independent layer applies : Physical Resistance.

```
physDamage = postArmorDamage × (1 - min(physicalResistance, 75) / 100)
```

Physical Resistance is a flat percentage reduction, **capped at 75%**. It comes from gear modifiers and works independently from armor.

**Combined example (vs Lv50 mob) :**
- You have 300 armor, 20% Physical Resistance
- Incoming hit : 200 physical damage
- Armor reduction : 300 / (300 + 5×50 + 50) = 50% => 200 × 0.50 = 100 damage
- Physical Resistance : 100 × (1 - 0.20) = **80 final damage**

Both layers together reduced 200 incoming damage to 80 — a 60% total reduction.

---

## Where Armor Comes From

Your total armor comes from multiple sources :

1. **Gear implicits** - Plate armor has inherent armor values per slot
2. **Gear modifiers** - "+X Armor" suffixes on your equipment
3. **[Earth](attributes#earth) attribute** - Flat armor per point
4. **Armor %** - Percentage modifiers multiply your total

---

## When Armor Matters Most

Armor gives consistent damage reduction against all physical hits from the same-level enemies. The challenge is keeping your armor high enough as you face tougher opponents.

| Scenario | How Armor Performs |
|----------|-------------------|
| Same-level mobs | Reliable, consistent % reduction on every hit |
| Higher-level mobs | Less effective — you need more armor to maintain the same reduction |
| *DoT* damage ([Burn](burn-fire-dot), [Poison](poison-void-stacking-dot)) | **Doesn't help** — armor only reduces hit damage |

> [!TIP]
> In high-tier realms, enemies hit harder AND your armor is less effective against higher-level attackers. Combine armor with max health ([Earth](attributes#earth)), evasion ([Wind](attributes#wind)), or blocking for multi-layered defense.

---

## Armor vs Evasion

Two fundamentally different defense philosophies :

| | Armor ([Earth](attributes#earth)) | Evasion ([Wind](attributes#wind)) |
|-|--------------|----------------|
| What it does | Reduces damage taken | Avoids the hit entirely |
| Consistency | Always reduces every hit | Probabilistic, all-or-nothing |
| Scaling | Needs investment to keep pace with enemy levels | Scales against mob accuracy |
| Big hits | Reduces consistently, but big hits still hurt | Either dodge it (100% saved) or don't (0% saved) |
| Ailments | **Doesn't prevent them** | **Dodged attacks can't apply ailments** |

> [!NOTE]
> That last point is critical. Armor reduces the damage of a fire hit, but you still get burned. Evasion means the hit never connects, so no burn either. Against ailment-heavy enemies, evasion can be stronger than armor.

---

## Related Pages

- [Evasion & Dodge](evasion-dodge) - Alternative defense : avoid damage entirely instead of reducing it
- [Elemental Resistances](elemental-resistances) - Per-element damage reduction, capped at 75%
- [Earth Attribute](attributes#earth) - Primary source of flat Armor from attributes
- [Blocking](blocking) - Last line of defense when evasion fails
