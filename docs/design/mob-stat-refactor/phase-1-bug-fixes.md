# Phase 1: Mob Stat System — Bug Fixes

## What You're Working On

Trail of Orbis is a deep ARPG mod for Hytale with a PoE/Last Epoch-inspired combat system. We're preparing a full mob stat system refactor across 4 phases. This is **Phase 1: Bug Fixes** — fixing 10 confirmed bugs in the current mob stat system without changing any design or architecture. The goal is a clean, correct baseline before the refactor begins in Phase 2.

Read `docs/design/MobStatSystem.md` fully before starting — it contains the complete research document with all findings, including the bug list. What follows here is the actionable context you need.

## Why This Matters

The mob stat system has ~36% of its stat budget silently wasted on stats that have zero combat effect. There are also config values being ignored, dead code confusing the codebase, and a penetration bug making Earth/Wind elemental mobs weaker than intended. These bugs will corrupt any refactor if not fixed first.

## The 10 Bugs

### Bug 1: Earth and Wind Penetration Dropped (HIGH)

**File**: `src/main/java/io/github/larsonix/trailoforbis/mobs/model/MobStats.java`

`toComputedStats()` forwards elemental penetration for FIRE, WATER, LIGHTNING, VOID but silently drops EARTH and WIND. The `ElementalStats` object contains all 6, but only 4 are transferred to the `ComputedStats.Builder`.

**Fix**: Forward all 6 elements' penetration in the `toComputedStats()` method. Use a loop over `ElementType.values()` instead of individual calls, so this class of bug cannot recur.

### Bug 2: 7 AI Stats Consume Pool But Are Never Stored (HIGH)

**File**: `src/main/resources/config/mob-stat-pool.yml`

These 7 stats are defined in the Dirichlet pool config with alpha weights and factors, consume pool points during stat generation, but are never stored in `MobStats` and never used in combat:
- `attack_speed`, `attack_range`, `attack_cooldown`
- `aggro_range`, `reaction_delay`
- `charge_time`, `charge_distance`

~13% of every mob's stat budget goes to these phantom stats.

**Fix**: Remove these 7 entries from `mob-stat-pool.yml`. Verify the stat generator (`MobStatGenerator` — find it via the index) doesn't crash when they're absent. The Dirichlet distribution will automatically redistribute their budget to the remaining stats.

### Bug 3: 12 Elemental % Damage Stats Are Dead (HIGH)

**File**: `src/main/resources/config/mob-stat-pool.yml`

6 `elemental_increased_damage_*` stats and 6 `elemental_more_damage_*` stats consume pool points, but the damage pipeline Steps 4-7 are skipped for mobs (the `isMobStats()` check in `RPGDamageCalculator.applyFlatDamage()` returns early, and no % fields are ever set on mob `ComputedStats`). ~23% of stat budget wasted.

**Fix**: Remove all 12 entries from `mob-stat-pool.yml`. Same verification as Bug 2.

### Bug 4: blockChance and parryChance Are Dead Stats (HIGH)

**File**: `src/main/resources/config/mob-stat-pool.yml`

`blockChance` flows to `passiveBlockChance` in `ComputedStats`, but passive block was removed from `AvoidanceProcessor` in the v1.0.2 blocking rework. `parryChance` flows to `ComputedStats.parryChance` but no parry handler exists for mobs (active block requires `DamageDataComponent` which only players have). ~4% of stat budget wasted.

**Fix**: Remove `block_chance` and `parry_chance` from `mob-stat-pool.yml`.

### Bug 5: RPGSpawnManager Ignores Config (MEDIUM)

**File**: `src/main/java/io/github/larsonix/trailoforbis/mobs/spawn/manager/RPGSpawnManager.java`

The constructor hardcodes `spawnOffsetRadius = 250.0` and `spawnOffsetMinDistance = 50.0`. The YAML config has `spawn_offset_radius: 150.0` and `spawn_offset_min_distance: 2.0` in `MobScalingConfig.SpawnMultiplierConfig`. The config values are parsed but never passed to `RPGSpawnManager`.

**Fix**: Read these values from the config passed to `RPGSpawnManager`'s constructor (or from `MobScalingConfig` via the manager). Remove the hardcoded values.

### Bug 6: spawnedPerChunk Never Auto-Clears (MEDIUM)

**File**: `src/main/java/io/github/larsonix/trailoforbis/mobs/spawn/manager/RPGSpawnManager.java`

`spawnedPerChunk` is a `ConcurrentHashMap<?, AtomicInteger>` that accumulates spawn counts per chunk forever. `clearChunkTracking()` exists but is never called automatically. Over time, all chunks that ever received RPG-multiplied spawns will hit their cap and block further spawns permanently.

**Fix**: Add a periodic clear — either on a timer (every 60s is fine) or on chunk unload events if available. The simplest approach is a timer in the manager's initialization that calls `clearChunkTracking()` periodically.

### Bug 7: Stale MobStats Javadoc (LOW)

**File**: `src/main/java/io/github/larsonix/trailoforbis/mobs/model/MobStats.java`

The class Javadoc says "10 stats" distributed in a "40% fixed / 60% random split." The actual record has 17 stats and uses a full Dirichlet distribution. The doc is from an earlier version.

**Fix**: Update the Javadoc to accurately describe the current system (17 stats, Dirichlet distribution, per-archetype alpha weights).

### Bug 8: dirichlet_precision Config Dead (LOW)

**File**: `src/main/resources/config/mob-stat-pool.yml`

The field `dirichlet_precision: 0.5` loads and validates but the comment explicitly says "NOT ACTIVE — loads and validates but has no effect on stat generation."

**Fix**: Remove the field from the config file. Remove any code that reads or validates it (search for `dirichletPrecision` or `dirichlet_precision` in the codebase).

### Bug 9: Deprecated PoolConfig Dead Code (LOW)

**File**: `src/main/java/io/github/larsonix/trailoforbis/mobs/MobScalingConfig.java`

The entire `@Deprecated PoolConfig` inner class (with 16 old stat factors like `healthFactor`, `damageFactor`, etc.) is dead code from a previous stat generation system. It still loads from YAML if present but is never used — actual stat generation uses `MobStatPoolConfig` from `mob-stat-pool.yml`.

**Fix**: Delete the entire `PoolConfig` inner class. Remove the `statPool` field that references it. Remove any YAML keys that fed into it (check `mob-scaling.yml` for a `stat_pool` or `pool` section). Clean up any imports.

### Bug 10: PlayerLevelCalculator 3D vs 2D Distance (LOW)

**File**: `src/main/java/io/github/larsonix/trailoforbis/mobs/calculator/PlayerLevelCalculator.java`

`countNearbyPlayers()` uses 3D distance squared (includes `dy`), but `DistanceBonusCalculator.calculateDistanceFromOrigin()` uses only XZ (2D). The player detection radius is 3D but zone calculation is 2D.

**Fix**: Make both consistent — use XZ distance only (2D) in `countNearbyPlayers()` since Y distance shouldn't affect mob scaling. Remove `dy` from the distance calculation.

## Approach

Work through bugs 1-10 in order (high severity first). For each:
1. Read the file(s) involved
2. Understand the surrounding code
3. Make the minimal fix
4. Verify the fix compiles

After all fixes: `./gradlew clean build` must pass.

## Read First

These files in order — they give you the full picture of what you're fixing:

1. `docs/design/MobStatSystem.md` — Section 1 (Current System Analysis) for full bug details
2. `src/main/java/io/github/larsonix/trailoforbis/mobs/model/MobStats.java` — the stat record with `toComputedStats()`
3. `src/main/resources/config/mob-stat-pool.yml` — the 52-stat Dirichlet config (bugs 2, 3, 4, 8)
4. `src/main/java/io/github/larsonix/trailoforbis/mobs/MobScalingConfig.java` — config class with dead PoolConfig (bug 9)
5. `src/main/java/io/github/larsonix/trailoforbis/mobs/spawn/manager/RPGSpawnManager.java` — spawn manager (bugs 5, 6)
6. `src/main/java/io/github/larsonix/trailoforbis/mobs/calculator/PlayerLevelCalculator.java` — level calculator (bug 10)

## Verification

1. `./gradlew clean build` passes
2. No references remain to deleted stats in Java code (grep for `block_chance`, `parry_chance`, `attack_speed`, `attack_range`, `attack_cooldown`, `aggro_range`, `reaction_delay`, `charge_time`, `charge_distance`, `increased_damage`, `more_damage` in the mobs package)
3. `toComputedStats()` forwards all 6 element types (not just 4)
4. `RPGSpawnManager` reads offset values from config, not hardcoded
5. `MobScalingConfig` has no `@Deprecated` inner classes
6. `mob-stat-pool.yml` has exactly 52 - 21 = 31 stats remaining (7 AI + 12 elemental % + 2 block/parry removed)

## What NOT To Do

- Do NOT change the stat generation algorithm (Dirichlet stays for now — Phase 2 replaces it)
- Do NOT change the damage pipeline (Phase 2 handles that)
- Do NOT add new stats or new fields
- Do NOT refactor MobStats into a new model (Phase 2)
- Do NOT touch ailment code (Phase 3)
- Do NOT change rarity multipliers (Phase 4)

This phase is purely surgical cleanup. Fix what's broken, delete what's dead, leave everything else exactly as-is.
