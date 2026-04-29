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

Armor reduces physical damage with a formula that has natural diminishing returns. It's highly effective against small hits but progressively weaker against massive ones. Full immunity is never reachable.

---

## The Formula

```
armorReduction = armor / (armor + 10 × incomingDamage)
armorReduction = min(armorReduction, 0.90)    // capped at 90%
finalPhysDamage = physDamage × (1 - armorReduction)
```

Your reduction depends on BOTH your armor AND the size of the incoming hit. A fixed armor value performs differently depending on what's hitting you.

### Reduction Table

| Your Armor | vs 50 Damage | vs 200 Damage | vs 500 Damage |
|-----------:|-------------:|--------------:|--------------:|
| 100 | 17% reduced | 5% reduced | 2% reduced |
| 500 | 50% reduced | 20% reduced | 9% reduced |
| 1000 | 67% reduced | 33% reduced | 17% reduced |
| 2000 | 80% reduced | 50% reduced | 29% reduced |
| 5000 | **90% (capped)** | 71% reduced | 50% reduced |

> [!IMPORTANT]
> Armor reduction is **capped at 90%**. No matter how much you stack, at least 10% of physical damage always gets through.

---

## Physical Resistance

After armor reduces the hit, a second independent layer applies : Physical Resistance.

```
physDamage = postArmorDamage × (1 - min(physicalResistance, 75) / 100)
```

Physical Resistance is a flat percentage reduction, **capped at 75%**. It comes from gear modifiers and works independently from armor.

**Combined example :**
- You have 1000 armor, 20% Physical Resistance
- Incoming hit : 200 physical damage
- Armor reduction : 1000 / (1000 + 10 x 200) = 33% => 200 x 0.67 = 134 damage
- Physical Resistance : 134 x (1 - 0.20) = **107 final damage**

Both layers together reduced 200 incoming damage to 107 - a 46.5% total reduction.

---

## Where Armor Comes From

Your total armor comes from multiple sources :

1. **Gear implicits** - Plate armor has inherent armor values per slot
2. **Gear modifiers** - "+X Armor" suffixes on your equipment
3. **[Earth](attributes#earth) attribute** - Flat armor per point
4. **Armor %** - Percentage modifiers multiply your total

---

## When Armor Matters Most

Armor shines against many small hits and struggles against single massive ones. This creates natural build tradeoffs :

| Scenario | How Armor Performs |
|----------|-------------------|
| Swarms of weak mobs | Very high reduction per hit, you're nearly immune |
| Boss single hits | Helps but won't solve everything on its own |
| *DoT* damage ([Burn](burn-fire-dot), [Poison](poison-void-stacking-dot)) | **Doesn't help** - armor only reduces hit damage |

> [!TIP]
> Against content with big single hits (bosses, high-tier realms), don't rely on armor alone. Combine it with max health ([Earth](attributes#earth)), evasion ([Wind](attributes#wind)), or blocking for a multi-layered defense. The formula naturally encourages this by being weaker against large hits.

---

## Armor vs Evasion

Two fundamentally different defense philosophies :

| | Armor ([Earth](attributes#earth)) | Evasion ([Wind](attributes#wind)) |
|-|--------------|----------------|
| What it does | Reduces damage taken | Avoids the hit entirely |
| Consistency | Always reduces every hit | Probabilistic, all-or-nothing |
| vs small hits | Very effective | Each dodge saves a hit |
| vs big hits | Less effective (formula) | Either dodge it (100% saved) or don't (0% saved) |
| Ailments | **Doesn't prevent them** | **Dodged attacks can't apply ailments** |

> [!NOTE]
> That last point is critical. Armor reduces the damage of a fire hit, but you still get burned. Evasion means the hit never connects, so no burn either. Against ailment-heavy enemies, evasion can be stronger than armor.

---

## Related Pages

- [Evasion & Dodge](evasion-dodge) - Alternative defense : avoid damage entirely instead of reducing it
- [Elemental Resistances](elemental-resistances) - Per-element damage reduction, capped at 75%
- [Earth Attribute](attributes#earth) - Primary source of flat Armor from attributes
- [Blocking](blocking) - Last line of defense when evasion fails
