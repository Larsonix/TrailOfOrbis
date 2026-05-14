# Server Deadlock: Mob Modifier System (May 10, 2026)

## Problem

When the mob modifier system's `isEnabled()` returns true, the server deadlocks. This manifests as:
- During initial connection: server hangs at `AssetRegistryLoader.sendAssets()`, client never finishes loading
- During runtime: server freezes entirely the moment `isEnabled()` flips to true

The deadlock involves MobScalingSystem (world thread) and the Hytale asset/network infrastructure. The exact internal mechanism is unknown — no errors, no exceptions, no logs. The server simply stops.

## The single controlling variable

`MobModifierManager.isEnabled()` returning true vs false. Every test confirms this is the ONLY variable that matters.

## Complete test history

### Phase 1: Identifying the break (bisect)

| Commit | Distance from HEAD | Result |
|--------|-------------------|--------|
| v1.0.7 release (GitHub JAR) | 62 | WORKS |
| v1.0.7 rebuilt from tag (different binary hash) | 62 | WORKS |
| b02b481 (midpoint) | 31 | WORKS |
| 1aecb9b | 16 | WORKS |
| 759fcf5 (pre-modifier system) | 12 | WORKS |
| c8082b1 to 74eeaf8 (modifier commits) | 11-5 | CRASH (StatBonus EnumMap bug) |
| 207b639 (first post-modifier commit that doesn't crash) | 4 | HANGS |
| HEAD | 0 | HANGS |

Break is the monster modifier system (commits 5-11 from HEAD).

### Phase 2: Isolating the trigger

| Test | isEnabled() | Result |
|------|-------------|--------|
| `enabled: false` in YAML config | false | WORKS |
| Entire Phase 6.6 replaced with log message | false | WORKS |
| Constructor only, no initialize() | false (config=null) | WORKS |
| No-op init: `initialized=true; return true;` (config=null) | false (config=null) | WORKS |
| `config = configManager.getMobModifierConfig(); initialized=true;` | true | HANGS |
| `config = new MobModifierConfig(); initialized=true;` (fresh object) | true | HANGS |
| `forceEnabled=true` flag (config=null, no init) | true | HANGS |
| Full init, isEnabled() hardcoded to return false | false | WORKS |
| Full init with loadAssets() disabled | true | HANGS |
| Full init with all class preloading | true | HANGS |
| Full init with VFX path fixes | true | HANGS |
| JSON assets instead of loadAssets() | true | HANGS |
| JSON assets, enabled:false in config | false | WORKS |

### Phase 3: Deferred activation attempts

| Test | Result |
|------|--------|
| 3-second deferred init via CompletableFuture | HANGS (init completes before client connects) |
| Deferred activation: isEnabled()=false until first PlayerReadyEvent | Client connects, then server freezes the moment activate() runs |
| 10-second delayed activation after first PlayerReadyEvent | Client connects, server freezes 10s later when activate() runs |

**Conclusion: deferred activation does NOT work.** The deadlock occurs WHENEVER `isEnabled()` becomes true, not just during boot or connection. The server freezes at the moment of activation regardless of timing.

## What was ruled OUT

1. **JAR binary hash**: Rebuilt v1.0.7 from tag (different hash) → works
2. **Class files in JAR**: Injected modifier .class files into working JAR → works
3. **`EntityEffect.getAssetStore().loadAssets()`**: Disabled it → still deadlocks when isEnabled=true
4. **JSON assets instead of loadAssets()**: Same deadlock
5. **Config files in JAR**: Excluded all config/ from JAR → still deadlocks
6. **`IncludesAssetPack` flag**: Set to false → still deadlocks
7. **`ServiceRegistry.register()`**: Skipped → still deadlocks
8. **`ModifierType.values()` class loading**: Proven NOT the cause (no-op with forceEnabled works)
9. **Invalid ModelVFX paths**: Fixed all paths → still deadlocks
10. **Class-loading deadlock**: Pre-loaded ALL modifier classes → still deadlocks
11. **Server-side asset counts**: Identical between working and broken
12. **Component registration**: MobModifierComponent always registered in setup()
13. **Timing/deferral**: 3s, 10s, PlayerReadyEvent — all deadlock when activated

## What IS known

1. **The deadlock is between the world thread and Hytale's asset/network infrastructure.** When `isEnabled()=true`, MobScalingSystem's code on the world thread somehow conflicts with the network/asset threads.

2. **The modifier code paths don't need to EXECUTE to cause the deadlock.** For non-elite mobs, the modifier blocks are skipped. But their mere REACHABILITY (isEnabled=true makes the if-condition evaluate differently) causes the deadlock.

3. **Both published elite mob mods (RPGMobs, Endless Elite Mobs) do NOT embed modifier logic in their scaling systems.** They use separate ECS systems for abilities/effects, completely decoupled from mob stat scaling.

4. **The server sends identical asset data regardless of isEnabled().** Asset counts are the same. The deadlock is not about WHAT is sent but about thread contention during sending.

## Where the hang/freeze happens (server log evidence)

### During initial connection (isEnabled=true at boot):
```
Send Common Assets took 248ms
[2 minutes of SILENCE]
Stage timeout at stage 'setup:send-assets' after 2min
```
Server stuck in `AssetRegistryLoader.sendAssets()` after sending up to ItemReticles.

### During runtime (isEnabled flips to true via deferred activation):
```
MobModifierManager activated (first player connected)
[server immediately frozen — no more log output ever]
```

### Client log (confirms server-side):
```
Received AssetType ItemReticles at 7784ms
Finished handling AssetType ItemReticles took 0ms
[AssetUpdate] Assets: Finished in 13ms.
[then nothing — waiting for more packets that never arrive]
```

## Root cause (CONFIRMED — May 10, 2026)

**`MobModifierTickSystem.registerRuntimeEffects()` called `EntityEffect.getAssetStore().loadAssets()` from within `TickingSystem.tick()` on the world thread.** This was the ONLY `loadAssets()` call in the entire codebase made during a world tick — all other 7 call sites happen during plugin initialization.

When `isEnabled()` was false, the tick system exited early and never reached `registerRuntimeEffects()`. When `isEnabled()` became true, the very first tick called `registerRuntimeEffects()` → `loadAssets()`, deadlocking the world thread with Hytale's asset/network infrastructure.

The JIT hypothesis was wrong. The debugging session disabled `MobModifierEffectRegistry.initialize()`'s `loadAssets()` call (which runs safely during init), but the SECOND `loadAssets()` call — the one inside the tick system — was never identified during the 3-hour session.

## Fix applied

Moved all 4 runtime effect registrations (enrage, frost aura slow, frozen slow, pack leader speed) from `MobModifierTickSystem.registerRuntimeEffects()` to `MobModifierEffectRegistry.initialize()` (Phase 6.6). Removed the deferred activation workaround (`activated` flag + 10s timer). Simplified `isEnabled()`.

## Lesson learned

**Never call `loadAssets()` from within a world tick.** All asset store mutations must happen during plugin initialization. This applies to `EntityEffect.getAssetStore()`, `Interaction.getAssetStore()`, `CraftingRecipe.getAssetStore()`, etc.

## Files involved

| File | Role |
|------|------|
| `MobModifierEffectRegistry.java` | Now registers ALL effects during init (per-modifier + runtime) |
| `MobModifierTickSystem.java` | **Was the problem** — lazy `registerRuntimeEffects()` removed |
| `MobModifierManager.java` | `activated` field and `activate()` removed, `isEnabled()` simplified |
| `PlayerJoinListener.java` | Deferred activation workaround removed |
| `MobScalingSystem.java` | No changes needed — modifier code paths were never the issue |
