---
name: Death Penalty
title: Death Penalty
description: XP loss on death - how it works, when it applies, and how to play around it
author: Larsonix
sort-index: 1
order: 32
published: true
---

# Death Penalty

Death in Trail of Orbis has consequences. But only once you're past the learning phase.

---

## How It Works

When you die, you lose **50% of your progress XP within the current level**.

```
xp_lost = current_progress_xp x 0.50
```

This is the XP you've accumulated *toward* the next level, not your total lifetime XP. You can never drop below the XP threshold of your current level.

| Rule | Details |
|------|---------|
| XP lost | 50% of progress within current level |
| Can you lose a level ? | **No**, you can never drop below your current level |
| Can you lose multiple levels ? | **No**, loss is capped to progress within the current level |
| Minimum XP | Never drops below level 20's XP threshold |

> [!IMPORTANT]
> The penalty applies to **progress XP**, not total XP. If you're level 45 with 2,000 / 5,000 XP toward level 46, you lose 50% of the 2,000 progress, not 50% of your total accumulated XP. Dying can never cause an instant level drop.

---

## Worked Examples

**Level 45, dying at different progress points :**

| Scenario | Progress XP | Penalty (50%) | Remaining | Verdict |
|:---------|------------:|--------------:|----------:|:--------|
| Mid-bar | 2,000 / 5,000 | 1,000 lost | 1,000 XP | Lost progress, kept level |
| Nearly full | 4,900 / 5,000 | 2,450 lost | 2,450 XP | Painful - almost half a level gone |
| Just leveled | 100 / 5,000 | 50 lost | 50 XP | Almost nothing - safest time to take risks |

---

## New Player Protection

> [!TIP]
> **Players at level 20 or below lose NO XP on death.** This gives you time to learn combat, experiment with builds, and understand ailments and defense without being punished for mistakes.

| Level | Death Penalty |
|:-----:|:--------------|
| 1-20 | **None** : die freely, learn the game |
| 21+ | 50% of current level progress lost |

The protection threshold is level 20. At level 20 you're still protected. The penalty activates at level 21 and above.

Additionally, your XP can **never drop below level 20's XP threshold**, regardless of how many times you die. This is a hard floor. Even at level 21, repeated deaths cannot push you back to level 20.

---

## What You Never Lose

The death penalty creates **meaningful stakes** without permanent loss :

- **You never lose durability** : durability doesn't decrease on gear at all
- **You never lose levels** : your character never gets weaker from dying
- **You never lose gear** : equipment is safe
- **You never lose Attribute Points or Skill Points** : progression is permanent
- **You only lose time** : the XP you need to re-earn

Death slows your leveling but never sets you back. It rewards careful play and good build decisions in high-level content, especially in Realms where modifiers can make mobs significantly more dangerous.

---

## Playing Around the Penalty

> [!WARNING]
> The penalty is most painful right before a level-up. If you're at 4,900 / 5,000 XP and die, you lose 2,450 XP, nearly half a level of progress.

**Strategies to minimize impact :**

- **Right after leveling up** is the safest time to take risks. You have little XP to lose
- **Before entering a difficult Realm**, consider whether you're close to leveling. If so, grind a few safe mobs first to level up, then enter the Realm with a fresh XP bar
- **Build defenses** : [Earth](attributes#earth) (Armor, Health), [Wind](attributes#wind) (Evasion), or [Void](attributes#void) (Life Steal) attributes directly reduce how often you die
- **Read the Death Recap** : when you die, the death screen tells you exactly what killed you and what defenses you were lacking. Use it to adjust your build

> [!CAUTION]
> Dying repeatedly in a difficult Realm compounds losses. Each death takes 50% of your *remaining* progress, so two deaths in a row costs 75% total (50% + 50% of the remaining 50%). Plan your runs carefully.

---

## Related Pages

- [Leveling & Experience](leveling-experience) - How XP and levels work
- [Death Recap](death-recap) - Understanding what killed you
- [Earth Attribute](attributes#earth) - Max Health and Armor reduce how often you die
- [Realm Rewards](realm-rewards) - Dying in Realms costs XP but you keep individual mob drops
