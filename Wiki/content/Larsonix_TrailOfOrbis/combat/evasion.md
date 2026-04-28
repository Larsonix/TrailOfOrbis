---
name: Evasion & Dodge
title: Evasion & Dodge
description: The full avoidance chain - dodge, evasion vs accuracy, active block, passive block
author: Larsonix
sort-index: 5
order: 46
published: true
---

# Evasion & Dodge

Evasion is fundamentally different from armor. Instead of reducing damage, evasion **avoids it entirely** - no damage, no ailments, no knockback. Trail of Orbis uses a Path of Exile-inspired formula where your evasion rating competes against the attacker's accuracy.

---

## The Avoidance Chain

When an attack targets you, 4 avoidance checks run in order. If any succeeds, the attack is mitigated :

| Order | Check | What Happens | Source |
|-------|-------|-------------|--------|
| 1 | **Dodge** | Attack fully avoided (flat % chance) | Gear modifiers |
| 2 | **Evasion** | Attack fully avoided (Accuracy vs Evasion) | [Wind](attributes#wind) attribute + gear |
| 3 | **Active Block** | Damage reduced (requires shield) | Shield + [Earth](attributes#earth) attribute |
| 4 | **Passive Block** | Damage reduced (random proc) | [Earth](attributes#earth) attribute |

> [!NOTE]
> Dodge and evasion checks come BEFORE blocking. If you dodge, the block check never runs. If you fail dodge AND evasion, block is your last chance to reduce the hit.

---

## Dodge (Flat Chance)

```
if random(0-100) < dodgeChance:
  attack fully avoided
```

Dodge is a simple percentage roll from gear modifiers. It's checked first and gives you guaranteed avoidance independent of attacker stats.

---

## Evasion vs Accuracy (PoE-Inspired Formula)

```
scaledEvasion = (evasion × evasionScalingFactor) ^ evasionExponent
hitChance = hitChanceConstant × accuracy / (accuracy + scaledEvasion)
hitChance = clamp(hitChance, minHitChance, maxHitChance)
```

The attacker's accuracy competes against your evasion :

| Formula Constant | Value | Meaning |
|:-----------------|------:|:--------|
| Hit chance constant | 1.25 | Slightly favors the attacker |
| Evasion scaling factor | 0.2 | Reduces raw evasion before calculation |
| Evasion exponent | 0.9 | Diminishing returns at high evasion |
| Minimum hit chance | 5% | Attacks always have at least 5% chance to hit you |
| Maximum hit chance | 100% | No guaranteed dodge from evasion alone |

### Hit Chance Examples

| Your Evasion | Attacker Accuracy | Hit Chance | Effective Dodge Rate |
|-------------:|------------------:|-----------:|--------------------:|
| 0 | Any | 100% | 0% |
| 250 | 100 | 93% | 7% |
| 500 | 100 | 77% | 23% |
| 1000 | 100 | 57% | 43% |
| 2000 | 100 | 39% | 61% |
| 1000 | 200 | 79% | 21% |
| 2000 | 200 | 60% | 40% |

> [!IMPORTANT]
> Evasion has **diminishing returns** (exponent 0.9). Doubling your evasion doesn't double your dodge rate. But it always helps - there's no point where more evasion stops mattering.

---

## Avoidance Outcomes

When you dodge or block, you get floating text but no screen flash - so you always know you avoided the hit.

| Check | Combat Text | Effect |
|-------|------------|--------|
| Dodge | "Dodged" | Attack fully avoided, no damage, no ailments |
| Evasion | "Dodged" | Attack fully avoided, no damage, no ailments |
| Active Block | "Blocked" | Damage reduced, stamina consumed |
| Passive Block | "Blocked" | Damage reduced, no stamina cost |

---

## Evasion vs Armor - When to Choose

| Scenario | Armor Better | Evasion Better |
|----------|-------------|----------------|
| Many small hits | High reduction per hit | Each dodge saves a hit |
| One massive hit | Reduces damage by % | Either dodge (100% saved) or eat it (0% saved) |
| Ailment-heavy enemies | Doesn't prevent ailments | Dodge prevents ailment application |
| Predictable damage | Consistent reduction | Inconsistent but occasionally saves your life |

> [!TIP]
> Evasion's biggest hidden advantage : **dodged attacks can't apply ailments**. If a fire mob's attack misses you, you don't get burned. Armor reduces the damage but the [Burn](burn-fire-dot) still applies. Against ailment-heavy content, evasion is often stronger than armor.

---

## Wind Attribute and Evasion

[Wind](attributes#wind) is your primary evasion stat source. It grants both evasion (defense) and accuracy (offense), so Wind builds dodge enemy attacks while making sure their own attacks connect against evasive enemies.

See [Attributes](attributes) for exact scaling values.

---

## Related Pages

- [Armor & Physical Defense](armor-physical-defense) - Alternative defense : reduce damage instead of avoiding it
- [Wind Attribute](attributes#wind) - Primary source of Evasion and Accuracy from attributes
- [Blocking](blocking) - Next check in the avoidance chain after evasion
- [Attack Speed](attack-speed) - Faster attacks mean more evasion rolls against you
