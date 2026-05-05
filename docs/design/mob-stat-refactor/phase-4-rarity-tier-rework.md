# Phase 4: Mob Stat System — Rarity Tier Rework

## What You're Working On

Trail of Orbis is a deep ARPG mod for Hytale with a PoE/Last Epoch-inspired combat system. The mob stat system has been refactored across 4 phases.

**Phase 1 (complete)**: Fixed 10 bugs — clean baseline established.

**Phase 2 (complete)**: Core stat model reworked — Template + Noise distribution (keeping existing pool math), 3-layer resistance profiles, 8 archetypes, `toComputedStats()` rewritten. Simulation validates kill times match baseline.

**Phase 3 (complete)**: Ailment integration — HP-based threshold + tier effectiveness. Freeze/Shock magnitude scales with mob HP. Burn/Poison DPS reduced on higher tiers. Element-specific thresholds. Undead Poison immunity.

**This is Phase 4: Rarity Tier Rework** — the final phase. Applying the new rarity multiplier table, wiring IIQ/IIR/XP to the rarity config, and verifying kill-time targets. After this phase, the mob stat system refactor is complete.

Read `docs/design/MobStatSystem.md` Section 7 (Rarity Tiers) for the complete design.

## The Problem

The current rarity system has issues:

1. **Elite multiplier** (`withMultiplier(1.5)`) only scales absolute stats (HP, damage, armor, healthRegen, trueDamage) but not elemental damage, accuracy, or evasion. Elemental-focused elites are barely stronger.

2. **Boss damage at 3.0×** is too high. Baseline simulation shows: level 50 Boss does 172 DPS against 100 HP = player dead in 0.6 seconds. Every ARPG we researched (PoE, LE, GD) keeps boss damage near elite levels and pumps HP instead.

3. **IIQ/IIR multipliers** are hardcoded in loot code, not driven by a rarity config.

4. **No realm-specific overrides** — realm elites and bosses should be tougher than overworld.

## The Solution

### New Rarity Multiplier Table

Three tiers. Each multiplier applies to the FINAL stat after pool × archetype × noise:

| Stat | Normal | Elite | Boss |
|------|--------|-------|------|
| **HP** | 1.0× | 2.5× | 8.0× |
| **Damage** (physical + elemental) | 1.0× | 1.3× | 1.5× |
| **Armor** | 1.0× | 1.5× | 2.0× |
| **Evasion** | 1.0× | 1.3× | 1.0× |
| **Move Speed** | 1.0× | 1.1× | 1.0× |
| **Accuracy** | 1.0× | 1.2× | 1.2× |
| **Health Regen** | 1.0× | 2.5× | 8.0× |
| **XP** | 1.0× | 3.0× | 10.0× |
| **IIQ** | 1.0× | 3.0× | 10.0× |
| **IIR** | 1.0× | 2.0× | 8.0× |
| **Ailment Threshold Mult** | 1.0 | 0.8 | 0.5 |
| **Ailment Effectiveness** | 1.0 | 0.7 | 0.4 |

### Design Rationale

**Boss damage is 1.5× (down from current 3.0×)**. This is the most important change. Boss difficulty comes from:
- **8× HP** — long fight, testing sustain and pattern-learning
- **2× armor** — harder to burst down
- **40% ailment effectiveness** — ailments reduced (from Phase 3)
- Hand-designed attack patterns — difficulty from mechanics

Players should die to boss *mechanics*, not stat-check one-shots. This follows:
- **PoE**: Map boss damage bonus is 0% at all tiers. Only HP scales.
- **Last Epoch**: ADR compresses DPS range. Boss damage not scaled.
- **Grim Dawn**: Boss difficulty from resistance profiles + mechanics.

**Boss evasion is 1.0×** (not scaled). Bosses should be hittable.

**Health regen scales with HP** (same multiplier). A boss with 8× HP should regen proportionally, otherwise regen is irrelevant for high-tier mobs.

**Stats that do NOT scale with rarity**: critChance, critMultiplier, armorPenetration, lifeSteal, trueDamage, knockbackResistance, elemental resistances, elemental penetration. Rarity makes a mob tougher, not a fundamentally different combatant.

### Realm-Specific Overrides

| Context | Elite HP | Boss HP |
|---------|----------|---------|
| Overworld | 2.5× | 8.0× |
| Realm | 3.0× | 12.0× |

Only HP changes in realms. Damage stays the same — challenge is duration, not lethality.

### Kill-Time Targets (Simulation-Verified)

Using real baseline numbers (level 50, PURE_FIRE build):

| Tier | Current TTK | Expected New TTK | Notes |
|------|-------------|------------------|-------|
| Normal | 1.7s | ~1.7s | Unchanged |
| Elite | 2.6s (1.5× HP) | ~4.3s (2.5× HP) | Longer, more threatening |
| Boss | 5.2s (3.0× HP) | ~13.7s (8.0× HP) | Much longer, but survivable |

Current boss: 0.6s TTD (dead instantly). New boss: damage drops from 3.0× to 1.5×, TTD should roughly double to ~1.2s — still dangerous but not instant. The 8× HP means the fight lasts 14 seconds, giving time for pattern learning.

## What You Need To Do

### 1. Create `RarityTier` Enum/Record

If Phase 2 didn't create this (check `mob-rarity.yml` config loader), create it:

```java
public enum RarityTier {
    NORMAL(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 0.8),
    ELITE(2.5, 1.3, 1.5, 1.3, 1.1, 1.2, 3.0, 3.0, 2.0, 0.7, 0.8),
    BOSS(8.0, 1.5, 2.0, 1.0, 1.0, 1.2, 10.0, 10.0, 8.0, 0.4, 0.5);
    // hp, damage, armor, evasion, speed, accuracy, xp, iiq, iir, ailmentEff, ailmentThreshold
}
```

Better: make it config-driven from `mob-rarity.yml` so values can be tuned without code changes.

### 2. Replace `MobStats.withMultiplier(double)`

Create `withRarityMultiplier(RarityTier)` that applies the full table:

- HP × tier.hp
- physicalDamage × tier.damage
- armor × tier.armor
- evasion × tier.evasion
- moveSpeed adjusted: `moveSpeed × tier.speed` (multiplicative on the multiplier)
- accuracy × tier.accuracy
- healthRegen × tier.hp (scales with HP)
- ailmentThresholdMultiplier × tier.ailmentThreshold
- ailmentEffectiveness × tier.ailmentEffectiveness
- elementalStats.withDamageMult(tier.damage) — elemental damage also scales

**NOT scaled**: critChance, critMultiplier, armorPenetration, lifeSteal, trueDamage, knockbackResistance, elemental resistances, elemental penetration.

Delete the old `withMultiplier(double)` after migration.

### 3. Wire Rarity Into MobStatFactory

The rarity multiplier is the LAST multiplicative layer:

```
1. Pool generation (level-based)
2. Pool distribution (archetype ratios)
3. Stat conversion (pool × factor = stat value)
4. Resistance profile applied
5. Noise applied
6. Rarity multiplier applied  ← HERE
```

### 4. Wire IIQ/IIR Into Loot System

**Files to check**:
- `gear/loot/LootCalculator.java`
- `gear/loot/LootGenerator.java`
- `gear/loot/LootListener.java`
- `gear/loot/RealmLootContext.java`

The loot system currently has hardcoded multipliers. Replace with values from the killed mob's rarity tier:

| Tier | Old IIQ | New IIQ | Old IIR | New IIR |
|------|---------|---------|---------|---------|
| Normal | 1.0× | 1.0× | 1.0× | 1.0× |
| Elite | 1.5× | 3.0× | 1.25× | 2.0× |
| Boss | 2.0× | 10.0× | 1.5× | 8.0× |

New values are higher because bosses now have 8× HP (longer fights), so reward-per-time should stay attractive.

### 5. Wire XP Into Leveling System

**Files to check**:
- `leveling/xp/MobStatsXpCalculator.java`
- `leveling/xp/XpCalculator.java`
- `leveling/systems/XpGainSystem.java`

Replace hardcoded XP tier multipliers:

| Tier | Old XP | New XP |
|------|--------|--------|
| Normal | 1.0× | 1.0× |
| Elite | 1.5× (OW) / 3.0× (realm) | 3.0× (everywhere) |
| Boss | 5.0× | 10.0× |

### 6. Realm-Specific Override Application

When spawning realm mobs, apply realm HP overrides from `mob-rarity.yml`:

```java
RarityTier tier = isRealm ? baseRarity.withRealmOverride(realmConfig) : baseRarity;
stats = stats.withRarityMultiplier(tier);
```

Check `realm-mobs.yml` — it currently has `elite-scale: 1.20` and `boss-scale: 1.50`. These should be replaced by the realm overrides in `mob-rarity.yml`. Remove the old fields if redundant.

### 7. Clean Up Old Multiplier Code

After wiring the new system, remove all traces of the old approach:

- `MobStats.withMultiplier(double)` — replaced by `withRarityMultiplier(RarityTier)`
- Any hardcoded `1.5` near elite contexts
- Any hardcoded `3.0` near boss contexts
- Old IIQ/IIR hardcoded values in loot code
- Old XP tier multipliers in XP code
- `elite-scale`/`boss-scale` in `realm-mobs.yml` if now in `mob-rarity.yml`

Grep for: `withMultiplier`, `eliteScale`, `bossScale`, `elite_scale`, `boss_scale`, `ELITE_MULT`, `BOSS_MULT` across the codebase.

### 8. Update Simulation

Update `ComprehensiveScenario` to use new rarity multipliers. The simulation's `MobHpFormula` and classification constants need to match the new table. The mob progression CSV should show the new HP/damage values per tier.

## Guardrails

1. Boss damage must be 1.5× — never higher. If boss fights feel too easy, increase HP, not damage
2. Crit chance/multiplier, armor penetration, life steal, true damage, knockback resistance, and elemental resistances/penetration are NEVER scaled by rarity
3. Realm overrides only affect HP — damage, armor, etc. stay the same
4. IIQ/IIR changes must flow to actual loot rolls — verify via simulation or in-game testing
5. XP changes must flow to actual XP awards — verify via simulation
6. The old `withMultiplier(double)` must be fully deleted — no dual systems
7. `realm-mobs.yml` should not have redundant `elite-scale`/`boss-scale` if replaced by `mob-rarity.yml`
8. Health regen uses the HP multiplier (not damage multiplier) — regen should scale proportionally with HP pool

## Verification

1. `./gradlew clean build` passes
2. `./gradlew simulate` runs — check combat_matrix.csv and mob_progression.csv
3. **Normal mob TTK**: ~1.7-3.1s at level 50 (unchanged from baseline)
4. **Elite mob TTK**: ~4-6s at level 50 (up from 2.6s due to 2.5× HP vs old 1.5×)
5. **Boss mob TTK**: ~10-15s at level 50 (up from 5.2s due to 8× HP vs old 3×)
6. **Boss TTD**: >2s at level 50 (up from 0.6s — damage dropped from 3.0× to 1.5×)
7. **Player survival vs Boss**: most builds should survive >3 seconds (was 0.6s)
8. Boss evasion is 1.0× — zero evasion rate in combat_matrix for BOSS rows
9. Loot from Elites at 3.0× quantity, 2.0× rarity in simulation/CSV
10. XP from Elites at 3.0×, Bosses at 10.0× in xp_economy.csv
11. No references remain to `withMultiplier(double)` in codebase
12. No hardcoded `3.0` boss damage multipliers remain
13. `balance_flags.csv` — CRITICAL flags should decrease (fewer DIES_TO_BOSS flags due to lower boss damage)
14. Realm Boss HP is 12.0× (not 8.0×) when realm context is active

## After This Phase

The mob stat system refactor is complete. Run `./gradlew simulate` one final time and compare the full output against `build/simulation-output/baseline-pre-refactor/`. Every mob in the game now:

- Has 33 meaningful stats (zero dead stats)
- Gets a deterministic resistance profile from element/faction detection (universal compatibility)
- Gets an archetype from role analysis (auto-assigned, config-overridable)
- Has HP-based ailment thresholds with tier-appropriate effectiveness
- Has clean rarity scaling — elites are threatening, bosses are epic without one-shotting
- Flows through the same 11-step damage pipeline as players

All values are config-driven from `mob-archetypes.yml`, `mob-resistances.yml`, `mob-rarity.yml`. No hardcoded magic numbers. The simulation is the test suite. The system is designed for expansion (new archetypes, new factions, new elements) without code changes.
