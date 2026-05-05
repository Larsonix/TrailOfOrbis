# Phase 2: Mob Stat System — Stat Model Rework

## What You're Working On

Trail of Orbis is a deep ARPG mod for Hytale with a PoE/Last Epoch-inspired 11-step damage pipeline, 6 elemental types, 4 ailments, and a 200+ player stat system. We're refactoring the mob stat system across 4 phases.

**Phase 1 (completed)** fixed 10 bugs: dead stats removed from pool config (21 stats deleted), Earth/Wind penetration forwarding fixed, spawn manager config override fixed, dead code deleted. The codebase is clean.

**This is Phase 2: Stat Model Rework** — the core architectural refactor. You're replacing the stat distribution and adding resistance profiles + archetypes, while KEEPING the existing scaling math that already produces correct kill times.

Read `docs/design/MobStatSystem.md` **in full** before starting — it is the definitive design document. Read `docs/design/mob-stat-refactor/EXECUTION_PLAN.md` for the step-by-step execution tracker. Read `docs/reference/mob-scaling-calculations.md` and `docs/reference/gear-scaling-calculations.md` for the existing formulas you must preserve.

## The Problem You're Solving

After Phase 1 removed dead stats, ~31 remain in the Dirichlet pool. The fundamental problem persists: the Dirichlet spreads budget across too many categories, producing mobs that are statistically average and tactically invisible. No mob has strong defining traits. A Trork warrior and a Scarak mage feel identical in combat. There are no resistance profiles, no archetype differentiation, and no ailment thresholds.

## Critical Constraint: KEEP the Existing Scaling Math

**The current pool + weighted HP/damage system already produces correct kill times.** Simulation-verified baseline (saved in `build/simulation-output/baseline-pre-refactor/`):

- Level 50 normal mob: ~153 HP, player hits for ~35-89 damage → TTK 1.7-3.1 seconds (matches 3-5 hit target)
- Level 1 normal mob: ~12 HP → TTK 2.3 seconds
- Level 100 normal mob: ~450 HP, player hits for ~90 damage → TTK ~5 seconds

**DO NOT create new base stat table formulas.** The existing system uses:

1. **Stat Pool Generation** (`MobStatGenerator` → will become `MobStatFactory`): `totalPool = (level × pointsPerLevel × progressiveScale + distanceBonus) × expMultiplier`
2. **Weighted HP Formula** (`MobScalingSystem`): `finalHP = max(rpgTargetHP × progressiveScale × expMult, floor) × sqrt(vanillaHP / 100)`
3. **Weighted Damage Formula** (`RPGDamageSystem`): `finalDmg = max(rpgTargetDmg × progressiveScale × classMult, floor) × sqrt(vanillaDmg / 10)`
4. **Shared `LevelScaling.getMultiplier()`**: Same curve for gear AND mobs — keeps combat balanced across levels

What changes is how pool points are **distributed** across stats (Dirichlet → Template + Noise), and what **new layers** are added on top (archetypes, resistance profiles, ailment thresholds).

## The Solution Architecture

### 1. New MobStats Record (33 Stats)

Replace the current `MobStats` record. Remove `blockChance`, `parryChance` (dead). Rename `dodgeChance` → `evasion`. Add `ailmentThresholdMultiplier`, `ailmentEffectiveness`.

**Core (5)**: maxHealth, physicalDamage, armor, accuracy, moveSpeed
**Offensive (5)**: critChance, critMultiplier, armorPenetration, lifeSteal, trueDamage
**Defensive (2)**: evasion, knockbackResistance
**Elemental (18)**: 6× resistance, 6× flatDamage, 6× penetration (in ElementalStats)
**Sustain (1)**: healthRegen
**Ailment (2)**: ailmentThresholdMultiplier (default 1.0), ailmentEffectiveness (default 1.0)

See `docs/design/MobStatSystem.md` Section 4 for the complete table.

### 2. Template + Noise Distribution (Replaces Dirichlet)

The total pool budget at each level stays identical. What changes is how pool points are allocated:

**Old (Dirichlet)**: Pool distributed randomly across all stats using alpha weights. High variance, no guarantees. Each stat gets a random share.

**New (Template)**: Pool distributed deterministically using fixed ratios per archetype. Each archetype defines what percentage of the pool goes to HP, damage, armor, etc.

```
For a Warrior archetype:
  HP share     = totalPool × 0.35
  Damage share = totalPool × 0.25
  Armor share  = totalPool × 0.15
  Accuracy     = totalPool × 0.10
  Crit         = totalPool × 0.05
  Other        = totalPool × 0.10

For a Brute archetype:
  HP share     = totalPool × 0.45  (tankier)
  Damage share = totalPool × 0.20
  Armor share  = totalPool × 0.20  (more armor)
  ...
```

The stat conversion factors (share × factor = stat value) from `mob-stat-pool.yml` are REUSED. They already translate pool points into real stat values (e.g., `max_health.factor = 0.88`). We're changing HOW pool points are allocated, not how they're converted.

**Noise** is Gaussian, ±15% max, applied per-stat AFTER archetype allocation. Does NOT affect resistances or ailment fields.

### 3. Three-Layer Resistance Profile Resolution

Universal mod compatibility via 3-layer resolution:

```
Layer 1: Element-Based Profile (from MobElementResolver detection)
    ↓ overridden by
Layer 2: Faction Profile (from NPC group detection)
    ↓ overridden by
Layer 3: Config Override (per-role-name in YAML)
```

**Layer 1** — Mob's detected element determines default resistances. Physical = neutral. Fire = resists fire, weak to water. Works for ALL mobs including modded/unknown.

**Layer 2** — Known Hytale factions (Trork, Outlander, Feran, Kweebec, Scarak, Void, Undead) get thematic overrides. Faction REPLACES element profile.

**Layer 3** — Explicit per-role-name overrides for edge cases.

**Fallback**: Unknown mobs with no keywords/faction get PHYSICAL profile (0% all resistances). Safe, predictable.

Negative resistances (weaknesses like -20%) already work in our pipeline — `ElementalCalculator.getEffectiveResistance()` passes negative values through correctly, and the Death Recap formatter already shows "vulnerable" text.

See Section 5 for complete tables and resolution logic.

### 4. Archetype Auto-Assignment

Validated against 78 actual Hytale NPC IDs:

```
1. Config override (role_name → archetype)
2. NPCGroup detection (animals → Beast archetype)
3. Role name keywords:
   - "brute", "ogre", "mauler", "brawler", "berserker", "marauder", "aberrant" → Brute
   - "warrior", "fighter", "scrapper", "soldier", "striker" → Warrior
   - "archer", "ranger", "hunter", "gunner", "lobber", "scout" → Ranger
   - "mage", "archmage", "wizard", "shaman", "sorcerer", "priest", "cultist" → Caster
   - "assassin", "stalker", "thief", "seeker" → Assassin
   - "guard", "defender", "sentry", "knight" → Tank
4. Fallback → Warrior
```

8 archetypes total: Warrior, Brute, Ranger, Caster, Assassin, Tank, Support, Beast. See Section 6 for all multiplier tables.

## What You Need To Build

### New Classes

1. **`ResistanceProfile`** (`mobs.profile`) — immutable record: 6 elemental resistances, optional physical resistance, ailment threshold bonuses map, poison immunity flag. Include `NEUTRAL` constant.

2. **`ResistanceProfileResolver`** (`mobs.profile`) — 3-layer resolution. Takes role name, NPC groups, detected element. Returns `ResistanceProfile`. Reads `mob-resistances.yml`.

3. **`MobArchetype`** (`mobs.archetype`) — enum with multiplier values for HP/damage/armor/evasion/speed/crit/armorPen/lifeSteal/knockbackResist + pool allocation ratios.

4. **`ArchetypeResolver`** (`mobs.archetype`) — keyword + NPCGroup + config lookup. Returns `MobArchetype`. Reads `mob-archetypes.yml`.

5. **`MobStatFactory`** (`mobs.stats`) — replaces `MobStatGenerator`. Uses EXISTING pool math + archetype-based allocation + resistance profiles + noise. Main entry point for stat generation.

### Modified Classes

6. **`MobStats`** — update record: remove `blockChance`/`parryChance`, rename `dodgeChance` → `evasion`, add `ailmentThresholdMultiplier`/`ailmentEffectiveness`. Rewrite `toComputedStats()`. Add `withRarityMultiplier(RarityTier)`.

7. **`MobScalingManager`** — create resolvers and factory during init. Wire `MobStatFactory` instead of `MobStatGenerator`.

8. **`MobScalingComponent`** — update CODEC for new fields. Backward compat: old data → `MobStats.UNSCALED` + regenerate.

9. **`RPGDamageCalculator`** — **DO NOT TOUCH**. The `isMobStats()` skip is correct. Verify mob elemental damage flows at Step 2.

10. **`CombatStatsResolver`** — verify `toComputedStats()` changes flow correctly.

### New Config Files

11. **`mob-archetypes.yml`** — 8 archetypes with multipliers, pool allocation ratios, keyword mappings, group mappings, overrides.

12. **`mob-resistances.yml`** — element profiles (7), faction profiles (7), per-role overrides, global settings.

13. **`mob-rarity.yml`** — Normal/Elite/Boss multipliers, realm overrides, elite spawn config.

### Updated Simulation

14. **`MobStatFactory`** used by simulation — update `ComprehensiveScenario`, `PowerCurveScenario`, `MobHpFormula`, `MobStatDebug` to use the new factory. The simulation should produce A/B comparison CSVs showing old Dirichlet vs new Template + Noise.

### Deleted (ONLY after simulation validates)

15. **`MobStatGenerator`** — old Dirichlet generator
16. **`mob-stat-pool.yml`** — old pool config (stat conversion factors may need to be preserved in the new factory or a shared config)

## Key Design Decisions and Why

**Why keep existing scaling math instead of new formulas?**
The proposed base stat table formulas (`baseHP = 100 × (1 + level × 0.15)^1.8`) produced values 37× too high compared to what actual player damage can handle. A level 50 mob with 6,300 HP would take 180 hits to kill. The current system produces ~153 HP at level 50 → 5 hits to kill. The existing math is battle-tested and shares `LevelScaling.getMultiplier()` with gear — changing it would break both systems.

**Why Template + Noise instead of pure Dirichlet?**
Dirichlet spreads budget across all categories, producing mobs that are average at everything. Template guarantees recognizable archetype identity. ±15% Gaussian noise adds variety without destroying identity. Every ARPG we researched uses deterministic base profiles, none uses random-everything.

**Why element-first resistance instead of faction-first?**
Faction profiles only work for known factions. Element detection via `MobElementResolver` works for any mob. Universal compatibility.

**Why KEEP the isMobStats() skip?**
Mob physical damage enters the pipeline via Hytale's vanilla damage event — `MobScalingSystem` modifies attack damage via `EntityStatMap` modifiers. The damage event already contains our scaled damage. Step 1 adds player's `flatPhysicalDamage` on top — skipping this for mobs prevents double-counting. This is the standard community pattern. Mob elemental damage (new, not in vanilla) correctly flows in at Step 2.

**Why 8 archetypes (not 7)?**
The NPC audit found ~20 animal mobs (Wolf, Bear, Spider, etc.) with no role keywords. The Beast archetype gives them sensible stats (moderate all-rounder, slightly fast) via NPCGroup detection, without requiring per-mob overrides.

## Guardrails

1. `toComputedStats()` must loop over `ElementType.values()` for penetration, resistance, AND flat damage — never individual element calls
2. `ResistanceProfileResolver.resolve()` must ALWAYS return a valid profile — NEUTRAL fallback, no nulls
3. `MobStatFactory` must clamp all outputs to `max(0, value)` except resistances (can be negative to -25%)
4. Noise does NOT affect resistances or ailment fields
5. **DO NOT remove `isMobStats()` skip** — removing it double-counts mob physical damage
6. `toComputedStats()` must NOT forward `physicalDamage` as `flatPhysicalDamage` — it comes via vanilla event
7. `MobScalingComponent` CODEC backward-compatible — old data → `MobStats.UNSCALED` + regenerate
8. Do not touch `AilmentCalculator` — Phase 3 handles ailment integration
9. Do not change rarity multiplier values — Phase 4 handles that
10. KEEP stat conversion factors from `mob-stat-pool.yml` — the pool-to-stat math must produce the same magnitude values

## Verification

1. `./gradlew clean build` passes
2. `./gradlew simulate` runs — compare against `build/simulation-output/baseline-pre-refactor/`
3. Normal mob TTK at level 50 is within 30% of baseline (~1.7-3.1 seconds)
4. No level bracket has TTK deviation >50% from baseline
5. A fire-element mob has fire resistance +40%, water resistance -20%
6. A Trork mob (NPCGroup) gets earth resistance +35%, NOT fire profile
7. An unknown mod mob gets Warrior archetype and PHYSICAL (neutral) profile
8. `RPGDamageCalculator` still has `isMobStats()` skip — untouched
9. Mob elemental damage flows into pipeline at Step 2
10. `balance_flags.csv` has no new CRITICAL flags compared to baseline
