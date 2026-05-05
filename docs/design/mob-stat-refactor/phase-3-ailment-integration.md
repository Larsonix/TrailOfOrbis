# Phase 3: Mob Stat System — Ailment Integration

## What You're Working On

Trail of Orbis is a deep ARPG mod for Hytale with a PoE/Last Epoch-inspired combat system. The mob stat system is being refactored across 4 phases.

**Phase 1 (complete)**: Fixed 10 bugs — dead stats removed, penetration forwarding fixed, spawn config fixed, dead code deleted.

**Phase 2 (complete)**: Core stat model reworked — new `MobStats` with 33 stats, Template + Noise distribution replacing Dirichlet (keeping existing pool math and weighted HP/damage formulas), 3-layer resistance profiles, 8 archetypes with auto-assignment, `toComputedStats()` rewritten to forward all 6 elements. Simulation validates kill times match baseline.

**This is Phase 3: Ailment Integration** — connecting the ailment system to the new mob stats. `MobStats` now has `ailmentThresholdMultiplier` and `ailmentEffectiveness` fields (added in Phase 2), and `toComputedStats()` forwards them. But the ailment calculation code doesn't read these fields yet.

Read `docs/design/MobStatSystem.md` Section 8 (Ailment Interaction) for the complete design.

## The Problem

As of v1.0.3, mobs CAN receive ailments (Burn, Freeze, Shock, Poison). But they have **zero defensive stats against them**:

- No ailment threshold — every hit with 10%+ base chance applies full magnitude regardless of mob HP or tier
- No effectiveness reduction — a boss mob is affected by Freeze and Shock at full strength
- No element-specific ailment resistance — a fire mob burns just as easily as a water mob
- Freeze trivializes every mob equally regardless of level
- Poison stacks melt bosses the same as trash

Baseline simulation confirms: avoidance rates are 0% across the board, no ailment scaling exists.

## The Solution: HP-Based Threshold + Tier Modifier

Two orthogonal controls inspired by PoE (threshold) and Last Epoch (effectiveness):

### Ailment Threshold

```
ailmentThreshold = maxHealth × ailmentThresholdMultiplier × (1 + elementAilmentBonus)
```

- `maxHealth` — the mob's final HP after all multipliers (archetype, rarity, weighted formula)
- `ailmentThresholdMultiplier` — from rarity tier: Normal 1.0, Elite 0.8, Boss 0.5
- `elementAilmentBonus` — from resistance profile: fire mobs get +50% burn threshold, etc.

**Why Boss threshold is 0.5 (not 1.0)**: Boss HP is 2.5-8× normal. If threshold equaled full HP, ailments would be nearly useless. The 0.5 multiplier compensates, keeping ailments relevant but requiring investment.

### Ailment Effectiveness

| Tier | Effectiveness | Effect |
|------|--------------|--------|
| Normal | 1.0 (100%) | Full ailment power |
| Elite | 0.7 (70%) | Noticeably reduced |
| Boss | 0.4 (40%) | Significantly reduced but still meaningful |

### How Each Ailment Is Affected

**Freeze (movement + action speed slow)**:
```
rawSlow = clamp(hitDamage / ailmentThreshold × 30%, 5%, 30%)
effectiveSlow = rawSlow × ailmentEffectiveness
finalSlow = max(effectiveSlow, 5%)  // minimum floor after effectiveness
```

**Shock (damage amplification on target)**:
```
rawAmp = clamp(hitDamage / ailmentThreshold × 50%, 5%, 50%)
effectiveAmp = rawAmp × ailmentEffectiveness
finalAmp = max(effectiveAmp, 5%)
```

**Burn (fire DoT)**:
```
dps = (hitDamage × 0.5 + flatBurnDamage) / duration × burnDamagePercent × dotDamagePercent
effectiveDPS = dps × ailmentEffectiveness
```

**Poison (stacking void DoT)**:
```
dpsPerStack = (hitDamage × 0.3 + flatPoisonDamage) / duration × dotDamagePercent
effectiveDPS = dpsPerStack × ailmentEffectiveness
```

### Worked Examples (Using Real Baseline Numbers)

**Freeze on a Normal mob at level 50** (mob has ~153 HP after weighted formula):
```
threshold = 153 × 1.0 = 153
Player hits for 35 damage:
rawSlow = (35 / 153) × 30% = 6.9% slow
effective = 6.9% × 1.0 = 6.9% slow
```
Noticeable freeze on normal mobs with moderate damage.

**Freeze on an Elite mob at level 50** (mob has ~229 HP, 1.5× classification):
```
threshold = 229 × 0.8 = 183
rawSlow = (35 / 183) × 30% = 5.7% slow
effective = 5.7% × 0.7 = 4.0% → clamped to 5% minimum
```
Barely noticeable. Need higher hit damage or dedicated investment.

**Freeze on a Boss mob at level 50** (mob has ~458 HP, 3.0× classification):
```
threshold = 458 × 0.5 = 229
rawSlow = (35 / 229) × 30% = 4.6% slow
effective = 4.6% × 0.4 = 1.8% → clamped to 5% minimum
```
Minimum floor. Boss freezing requires massive single-hit damage or serious status investment.

**Burn on a Boss** (same level, player hits for 35 fire damage):
```
dps = (35 × 0.5) / 4.0 = 4.375 DPS base
effectiveDPS = 4.375 × 0.4 = 1.75 DPS
```
Boss takes 40% of normal Burn damage. DoT builds work but can't trivially melt.

### Element-Specific Ailment Thresholds

From the resistance profile (configured in `mob-resistances.yml`):

| Profile | Ailment Bonus | Effect |
|---------|---------------|--------|
| FIRE mobs | `burn_threshold: 50` | +50% burn threshold (harder to burn) |
| WATER mobs | `freeze_threshold: 50` | +50% freeze threshold |
| LIGHTNING mobs | `shock_threshold: 50` | +50% shock threshold |
| VOID mobs | `poison_threshold: 50` | +50% poison threshold |
| Undead | `poison: immune` | Poison ailment blocked entirely |
| Feran (faction) | `freeze_threshold: 30` | +30% freeze threshold (warm-blooded) |
| Scarak (faction) | `shock_threshold: 30` | +30% shock threshold (chitin) |

### Undead Poison Immunity

Undead are immune to the Poison **ailment** (no stacks applied), but still take Void **damage**. The ailment application is blocked before the chance roll, not the damage step.

## What You Need To Modify

### 1. `AilmentCalculator.java`

The core ailment math class. Currently stateless, pure math.

**Changes**:
- Add defender stats parameter to magnitude calculations (or extract threshold/effectiveness)
- For Freeze and Shock: replace `targetMaxHealth` denominator with `ailmentThreshold`:
  ```
  ailmentThreshold = targetMaxHealth × defenderStats.getAilmentThresholdMultiplier()
                     × (1 + getElementAilmentBonus(ailmentType, defenderStats))
  ```
- For all ailment magnitudes: multiply final value by `defenderStats.getAilmentEffectiveness()`
- Apply minimum floor (5%) AFTER effectiveness multiplication
- Chance calculation is UNCHANGED — threshold affects magnitude, not chance

### 2. `ComputedStats` / `ComputedStats.Builder`

Phase 2 should have added `ailmentThresholdMultiplier` and `ailmentEffectiveness`. Verify they exist and add if missing:

- `ailmentThresholdMultiplier` (float, default 1.0)
- `ailmentEffectiveness` (float, default 1.0)
- `burnThresholdBonus` (float, default 0.0) — from resistance profile
- `freezeThresholdBonus` (float, default 0.0)
- `shockThresholdBonus` (float, default 0.0)
- `poisonThresholdBonus` (float, default 0.0)
- `poisonImmune` (boolean, default false) — for Undead

These are populated in `MobStats.toComputedStats()` from the resistance profile data.

### 3. `CombatAilmentApplicator.java`

Bridge between damage pipeline and ailment application.

**Changes**: Pass defender ComputedStats to AilmentCalculator. Check `poisonImmune` flag before Poison application — if true, skip ailment entirely (damage still applies via normal pipeline).

### 4. Verification: Tick Systems

`AilmentTickSystem`, `RpgBurnTickSystem`, `RpgPoisonTickSystem`, `MobSpeedEffectManager` — verify that effectiveness is baked into the magnitude at **calculation time** (in `AilmentCalculator`), NOT applied at tick time. The `AilmentState` stored in `AilmentTracker` should contain the already-adjusted magnitude.

If these systems apply their own multipliers, the effectiveness would be double-applied. Check and confirm.

### 5. Simulation Update

Add ailment analysis to `ComprehensiveScenario`:
- New CSV columns or separate file: Freeze magnitude and Burn DPS on Normal/Elite/Boss at each level
- Flag if any build produces >20% Freeze slow on a Boss (potential trivialization)
- Flag if total Burn DPS exceeds 10% of Boss HP per second

## Key Design Decisions

**Why threshold affects magnitude, not chance?**
PoE's approach: ailments always apply if you have enough chance, but their *strength* depends on the hit relative to the target's HP. This rewards investment in status chance without creating hard immunity walls.

**Why minimum 5% floor after effectiveness?**
Without a floor, bosses would receive 0.5-2% effects that are literally imperceptible. The 5% floor means every successful ailment application does *something* — it's weak, but the player sees it work. This encourages investment rather than creating a "don't even try" feeling.

**Why Burn/Poison use effectiveness on DPS, not threshold?**
Damaging ailments already scale from hit damage — their DPS naturally tracks content difficulty. The effectiveness multiplier on DPS is simpler and more intuitive: Boss takes 40% of normal Burn damage. No threshold calculation needed for DoT magnitude.

**Why element-specific thresholds?**
A fire elemental should resist being burned. Intuitive for players and creates learning: "don't burn fire mobs, freeze them instead."

## Guardrails

1. Effectiveness applied at **calculation time** (in `AilmentCalculator`), NOT at tick time — stored magnitude already adjusted
2. Ailment **chance** is unchanged — threshold affects magnitude only
3. Minimum floor (5%) applied AFTER effectiveness — `max(effective, 5%)`
4. Player ailment stats (`burnThreshold`, `freezeThreshold`, `shockThreshold` in player ComputedStats) continue to work exactly as before — this phase only changes MOB defender thresholds
5. DO NOT change how ailments work on player defenders
6. `AilmentTracker.cleanup(uuid)` on mob death/despawn still works (memory leak prevention)
7. Earth and Wind elements have NO associated ailment (`AilmentType.forElement()` returns null) — this is by design, not a bug

## Verification

1. `./gradlew clean build` passes
2. `./gradlew simulate` runs — check new ailment columns
3. Normal mob Freeze: 5-25% slow range depending on hit damage
4. Elite mob Freeze: reduced (~70% of Normal effectiveness)
5. Boss mob Freeze: greatly reduced (40% effectiveness, often hits 5% floor)
6. Fire-element mob has +50% burn threshold (harder to burn)
7. Undead mob cannot receive Poison stacks (check logs or add test)
8. Burn DPS on Boss is exactly 40% of what it would be on Normal with same HP
9. Player ailment calculations completely unchanged
10. No CRITICAL balance flags in simulation output related to ailments
