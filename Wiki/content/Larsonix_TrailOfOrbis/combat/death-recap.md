---
name: Death Recap
title: Death Recap
description: What the death screen tracks, accuracy levels, and how to read the combat log
author: Larsonix
sort-index: 8
order: 49
published: true
---

# Death Recap

When you die, Trail of Orbis records detailed information about the killing blow. The death recap breaks down exactly what happened so you can learn from each death and adjust your build.

---

## What Gets Tracked

Each combat snapshot records the following per hit :

| Data | Description |
|------|-------------|
| Timestamp | When the hit occurred |
| Attacker name | Name of the mob or player that hit you |
| Attacker type | Classification (normal, elite, boss) |
| Attacker level | Level of the attacker |
| Attacker class | Mob classification category |
| Base damage | Raw damage before scaling |
| Critical strike info | Whether the hit was a crit, and the multiplier used |
| Armor values | Your armor and the computed reduction percentage |
| Elemental damage | Per-element damage dealt |
| Elemental resistance | Your resistance per element against that hit |
| Defender health before | Your HP before the hit |
| Defender health after | Your HP after the hit (0 on the killing blow) |
| Avoidance flags | Whether dodge, evasion, or block was attempted/succeeded |

---

## Accuracy Levels

Not all values in your death recap are equally precise. The system marks each value with an accuracy level :

| Accuracy | Meaning | Examples |
|----------|---------|---------|
| **EXACT** | Recorded directly from the calculation | Damage dealt, crit status, crit multiplier, armor values |
| **APPROX** | Reverse-calculated or estimated | Some breakdown values computed after the fact |
| **NOT_TRACKED** | Data not available for this hit | Some granular breakdowns not recorded |

> [!NOTE]
> Core values like total damage, whether the hit was a crit, and your armor reduction are always EXACT. More granular breakdowns (like the contribution of individual modifiers) may be APPROX or NOT_TRACKED depending on the calculation path.

---

## Reading the Death Recap

The death recap answers 3 critical questions :

### 1. What damage type killed you ?

Check the elemental damage breakdown. If most damage is **physical** (white), invest in armor ([Earth](attributes#earth) attribute, plate gear). If it's a specific **element** (fire, lightning, etc.), get resistance for that element from gear suffixes.

### 2. Did you get one-shot or worn down ?

Compare your health before the killing blow to the damage dealt. If the killing blow exceeded your max health, you need more **max health** ([Earth](attributes#earth) attribute) or damage avoidance. If you were already low, the issue is sustain - consider **life leech** ([Void](attributes#void)) or **block heal**.

### 3. Did your defenses work ?

The recap shows your armor reduction percentage. If armor only reduced 5% of the damage, the hit was too large for your current armor value. If your avoidance flags show no dodges or blocks, your evasion or block chance may be too low against that enemy's accuracy.

---

## Combat Detail Mode

For live combat analysis (not just deaths), toggle detailed breakdowns in chat :

```
/too combat detail
```

This shows per-hit calculation details as they happen :
- Damage numbers with type breakdown
- Crit status and multiplier
- Armor reduction applied
- Resistance reduction applied
- Avoidance results (miss, dodge, block, parry)

> [!TIP]
> Combat detail mode is your most powerful theorycrafting tool. Turn it on while testing a new build against target dummies or easy content to see exactly how your stats translate into real damage. Turn it off for serious content to keep chat clean.

---

## Using Death Recap to Improve

| What the Recap Shows | What to Do |
|---------------------|-----------|
| High physical damage, low armor reduction | Stack more armor ([Earth](attributes#earth) attribute, plate gear) |
| High elemental damage, 0% resistance | Add resistance gear for that element |
| Crit killed you (CRIT flag) | You can't prevent crits directly, but more max HP lets you survive them |
| Multiple small hits wore you down | Evasion ([Wind](attributes#wind)) or life sustain ([Void](attributes#void) leech) |
| Single massive hit one-shot | More max health, energy shield, or avoidance |
| Avoidance flags all failed | Higher evasion, dodge chance, or block chance needed |

> [!IMPORTANT]
> Every death teaches you something about your build's weaknesses. "Killed by a Fire Elite with 0% Fire Resistance" has an obvious fix. "Killed by a Lv80 Boss with 90% armor reduction, 400 damage dealt" tells you armor alone isn't enough at that tier - add health, evasion, or blocking.

---

## Related Pages

- [Damage Pipeline](the-11-step-damage-pipeline) - Understanding the pipeline helps you interpret death recap breakdowns
- [Armor & Physical Defense](armor-physical-defense) - If recap shows low armor reduction, invest here
- [Elemental Resistances](elemental-resistances) - If recap shows high elemental damage, add the appropriate resistance
- [Death Penalty](death-penalty) - XP consequences of dying
