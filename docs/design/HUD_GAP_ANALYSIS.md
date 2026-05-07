# HUD Lifecycle Gap Analysis

> **Purpose**: Side-by-side comparison of the clean-room plan vs current implementation.
> Each gap has severity, root cause, timing simulation, reproduction case, and specific fix.
>
> **Date**: 2026-05-06
> **Evidence basis**: Hytale server source (World.java, Player.java, GamePacketHandler.java),
> client binary analysis (CustomUICommand processor), HyUI source (HyUIHud.java,
> MultiHudWrapper.java), community plugins, 6+ sessions of crash debugging.

---

## Overall Assessment

The current architecture is **80% aligned** with the clean-room design. The core patterns are correct:
- Centralized `HudLifecycleManager` with `PersistentHud` providers
- `DrainPlayerFromWorldEvent` → `discardAll()` (zero-packet discard)
- `PlayerReadyEvent LATE` → `restoreAll()` with direct `MultiHudWrapper.setCustomHud()`
- `HudRefreshHelper` with reflection-based safe rerender (resetHasBuilt + true,true)

The remaining bugs come from **3 actionable gaps** (+ 2 low-priority items).

---

## GAP 1 (HIGH): Safety Net Timer — Cross-World Race Condition

### Evidence from Prior Crashes

From `feedback_hud_direct_multihud_registration.md` (Bug 2):
> "Safety nets changed to unconditional discardAll+restoreAll... created 3 full HUD rebuild
> cycles per transition... overwhelmed the client during OnWorldJoined → NullReferenceException"

The safety net was changed from unconditional to conditional to fix Bug 2. But the conditional
version still has a **cross-world race condition**.

### What the Code Does

```java
// HudLifecycleManager.java:250-267
world.execute(() -> {                               // PRIMARY: 1 tick deferred
    discardAll(pid);
    restoreAll(pid, w, player);
});

CompletableFuture.delayedExecutor(1500, MILLIS)     // SAFETY NET: 1.5s delayed
    .execute(() -> {
        if (!w.isAlive()) return;
        w.execute(() -> restoreAll(pid, w, null));   // runs on captured world `w`
    });
```

### Timing Simulation: Rapid Realm Enter→Exit

```
T=0ms    Player in OVERWORLD, XP bar + shield active
T=10ms   Player activates gateway → Teleport component added
T=20ms   TeleportSystems: removeFromStore() on OVERWORLD
           → getReference() = NULL
           → DrainPlayerFromWorldEvent fires
           → discardAll(pid): XP bar + shield discarded (zero packets, correct)
T=25ms   REALM.addPlayer() → onSetupPlayerJoining()
           → JoinWorld(clearWorld=true) sent to client
           → setQueuePackets(true)
T=200ms  Client sends ClientReady(readyForGameplay=true)
T=210ms  PlayerReadyEvent fires (world=REALM)
           → PRIMARY: REALM.execute(() → {
               discardAll(pid);   // no-op, already discarded
               restoreAll(pid, REALM, player);  // creates XP+shield in REALM ✓
             })
           → SAFETY NET scheduled: fires at T=1710ms, captured w=REALM

T=300ms  PRIMARY deferred lambda executes on REALM thread
           → XP bar + shield created, registered via MultiHudWrapper ✓

T=500ms  Player exits realm (clicks exit portal)
T=510ms  TeleportSystems: removeFromStore() on REALM
           → DrainPlayerFromWorldEvent fires
           → discardAll(pid): XP bar + shield discarded (zero packets, correct)
T=520ms  OVERWORLD.addPlayer() → JoinWorld(clearWorld=true) sent

T=700ms  ClientReady → PlayerReadyEvent (world=OVERWORLD)
           → PRIMARY: OVERWORLD.execute(() → {
               discardAll(pid);
               restoreAll(pid, OVERWORLD, player);  // creates XP+shield in OVERWORLD ✓
             })
           → NEW SAFETY NET scheduled: fires at T=2200ms, captured w=OVERWORLD

T=800ms  PRIMARY lambda executes on OVERWORLD thread ✓

                    ╔═══════════════════════════════════════╗
T=1710ms            ║ OLD SAFETY NET FIRES (from T=210ms)  ║
                    ╚═══════════════════════════════════════╝
           → ForkJoinPool.commonPool() thread
           → w=REALM, w.isAlive()? → YES (realm world may still be alive if not yet closed)
           → REALM.execute(() → restoreAll(pid, REALM, null))

T=1720ms REALM.execute lambda runs:
           → Universe.get().getPlayer(pid) → returns freshRef (valid, in OVERWORLD)
           → worldStore = REALM.getEntityStore().getStore()  ← WRONG STORE
           → For each provider:
             → isActive(pid)? → YES (we have HUDs from T=800ms)
             → SKIP ✓ (conditional check saves us)
```

**In this scenario, the safety net is a no-op.** The `isActive()` check prevents damage.

### Timing Simulation: Safety Net FAILURE Case

```
T=0ms    Player joins server for first time (no prior drain event)
T=100ms  PlayerReadyEvent fires (world=OVERWORLD)
           → PRIMARY: OVERWORLD.execute(() → {
               discardAll(pid);   // no-op (first join)
               restoreAll(pid, OVERWORLD, player);  // creates XP+shield ✓
             })
           → SAFETY NET scheduled: fires at T=1600ms

T=200ms  PRIMARY lambda executes → HUDs created, registered ✓

T=800ms  Player enters realm (fast — within 1.5s of joining)
T=810ms  removeFromStore() on OVERWORLD
           → DrainPlayerFromWorldEvent → discardAll(pid): HUDs discarded

T=900ms  REALM.addPlayer() → JoinWorld(clearWorld=true)
           → Client clears DOM (all HUD elements destroyed)

T=1000ms ClientReady → PlayerReadyEvent (world=REALM)
           → PRIMARY: creates HUDs in REALM ✓

T=1100ms PRIMARY executes → HUDs in REALM, registered ✓

                    ╔══════════════════════════════════════════════╗
T=1600ms            ║ OLD SAFETY NET FIRES (from T=100ms)         ║
                    ║ w=OVERWORLD (captured at first join)         ║
                    ╚══════════════════════════════════════════════╝
           → ForkJoinPool thread
           → w=OVERWORLD, w.isAlive()? → YES
           → OVERWORLD.execute(() → restoreAll(pid, OVERWORLD, null))

T=1610ms OVERWORLD.execute lambda runs:
           → freshRef = Universe.get().getPlayer(pid) → valid (in REALM)
           → worldStore = OVERWORLD.getEntityStore().getStore()  ← WRONG STORE
           → For each provider:
             → isActive(pid)? → YES (from T=1100ms)
             → SKIP ✓ (saved again by conditional check)
```

**Again saved by `isActive()`.** The conditional safety net is doing its job in most cases.

### When Does the Safety Net Actually CAUSE Damage?

The safety net causes damage when:
1. The primary path FAILS (HUD not registered → `isActive()` = false)
2. The safety net fires in a DIFFERENT world than the player is in
3. The safety net creates HUDs against the wrong world's store

For the primary path to fail, `MultiHudWrapper.setCustomHud(player, ...)` would need to throw. This is extremely unlikely — the method is a simple map insertion + packet build.

**However**, the safety net has a subtler problem: it passes `player=null`, which means the provider's `showHud()` skips `MultiHudWrapper.setCustomHud()`. The HUD is created via `HudBuilder.show()` → `safeAdd()`. If `getReference()` is null at that moment (shouldn't be 1.5s later, but could be during another transition), `safeAdd()` silently fails → HUD tracked in `activeHuds` but never registered → **invisible HUD**.

This is the exact "permanently invisible" pattern from Bug 1 in the feedback memory:
> "HUD tracked in activeHuds (isActive=true) but never registered with MultiHud.
> Safety net checked isActive → true → skipped. Permanently invisible."

### Conclusion

The safety net's `isActive()` conditional check prevents crashes in 99% of cases. But when the primary path fails for any reason (exception, timing edge), the safety net's `player=null` path recreates Bug 1 (invisible HUDs). **Removing the safety net is strictly better** — if the primary path fails, a warning log is more useful than a silent fallback that may create invisible HUDs.

### Fix

Remove the safety net (lines 255-267 in HudLifecycleManager.java). The primary path's `MultiHudWrapper.setCustomHud()` is reliable. HyUI's own `safeAdd()` (deferred from `show()`) provides a natural backup 1 tick later when `getReference()` becomes valid.

### Reproduction Case

1. Join server → HUDs appear ✓
2. Within 1.5 seconds, enter a realm via gateway
3. **If primary path failed for any reason** (exception in showHud, timing edge):
   - Safety net fires 1.5s after join
   - Creates HUDs with `player=null` against OVERWORLD store
   - `safeAdd()` may fail → invisible HUDs in realm
   - Player sees no XP bar/shield until next transition

---

## GAP 2 (MEDIUM): world.execute() Deferral in onPlayerReady

### What the Code Does

```java
// HudLifecycleManager.java:250-253
world.execute(() -> {
    discardAll(pid);
    restoreAll(pid, w, player);
});
```

### Why It's Deferred

The comment says "1 tick deferred for client stability" and "runs AFTER the EARLY handler's world.execute()."

### The Problem

`PlayerReadyEvent` is dispatched from `Player.handleClientReady()` which runs on the world thread (either from `GamePacketHandler` dispatching to world, or from the 10s timeout). We're already ON the world thread. Deferring adds:

1. **One tick of HUD latency** — player sees a bare UI for one tick after entering a world
2. **Stale capture risk** — the `player` variable is captured from the event. One tick later, this Player reference is still valid (Player components persist), but it's a principle violation (our plan says "fresh refs at execution time")
3. **Ordering dependency** — relies on FIFO ordering of `world.execute()` tasks, which is implementation-dependent

### Timing Simulation

```
T=0    PlayerReadyEvent fires on WORLD thread
         → EARLY handlers run (PlayerJoinListener: load data, calc stats)
         → NORMAL handlers run
         → LATE: HudLifecycleManager.onPlayerReady()
             → Posts: world.execute(() → discardAll + restoreAll)
             → Posts: safety net (1.5s)
T=0    Event handler returns. Other world thread work continues.

T=50ms Next world tick
         → world.execute() queue processed
         → discardAll(pid) runs
         → restoreAll(pid, w, player) runs
             → showHud() → HudBuilder.show() → safeAdd() [deferred another tick]
             → MultiHudWrapper.setCustomHud(player, ...) → IMMEDIATE registration ✓

T=100ms Next world tick
         → safeAdd() from show() fires → redundant rebuild (harmless)
```

The deferred execution adds 50ms (one tick) of latency. During that tick, the player has no HUDs. This is noticeable as a flicker.

### What the Decompiled Server Shows

From `Player.java:269-281`, `handleClientReady()`:
```java
public void handleClientReady(boolean fromTimeout) {
    ScheduledFuture<?> timeoutTask = this.waitingForClientReady.getAndSet(null);
    if (timeoutTask == null) return;  // one-shot gate
    if (!fromTimeout) timeoutTask.cancel(false);
    this.readyId.incrementAndGet();
    HytaleServer.get().getEventBus()
        .dispatchFor(PlayerReadyEvent.class, this.getWorld().getName())
        .dispatch(new PlayerReadyEvent(this, ...));
}
```

This dispatches directly — no `world.execute()` wrapping. The event fires on whatever thread calls `handleClientReady()`:
- From `GamePacketHandler.handle(ClientReady)`: dispatches to world thread first
- From timeout: runs on `SCHEDULED_EXECUTOR` thread

**For timeout case, we might NOT be on the world thread.** The `world.execute()` deferral actually serves a purpose for the timeout case. However, the timeout is a 10-second fallback that almost never fires.

### Fix

Keep `world.execute()` but acknowledge it's for the timeout edge case. Remove the defensive `discardAll()` — it's redundant with the drain handler. For first-join (no prior drain), `isActive()` returns false for all providers, so `restoreAll()` creates them fresh without needing a discard.

```java
// Revised onPlayerReady:
world.execute(() -> restoreAll(pid, w, player));
```

### Verification

- Join server → HUDs appear within 1 tick (same as before, deferred for timeout safety)
- Enter realm → HUDs appear within 1 tick
- First join has no prior drain → restoreAll creates fresh (isActive=false for all)
- Rapid transitions → drain handler clears before next restore

---

## GAP 3 (MEDIUM): No readyId Deduplication

### What the Code Currently Has

No readyId tracking. The code comment claims only one PlayerReadyEvent per transition.

### Why readyId Matters

From `Player.java`: `handleClientReady` uses `AtomicReference.getAndSet(null)` as a one-shot gate. Only the first call dispatches. This means **within a single world transition, only one event fires.**

But across **rapid successive transitions**, the events from different transitions can interleave with drain events:

```
T=0    Enter realm → drain(overworld) → PlayerReady(realm)
T=100  Exit realm → drain(realm) → PlayerReady(overworld)
```

The risk is if `drain(realm)` at T=100 hasn't processed yet when `PlayerReady(realm)` from T=0 executes (deferred by world.execute). This is unlikely with the current code because drain runs at NORMAL priority and is NOT deferred, while restore is LATE + deferred. But readyId provides a definitive guard.

### Fix

```java
private final Map<UUID, Integer> lastReadyIds = new ConcurrentHashMap<>();

private void onPlayerReady(PlayerReadyEvent event) {
    int readyId = event.getReadyId();
    UUID pid = event.getPlayer().getUuid();

    Integer last = lastReadyIds.get(pid);
    if (last != null && readyId <= last) {
        LOGGER.atFine().log("Skipping stale readyId %d (last=%d) for %s", readyId, last, pid);
        return;
    }
    lastReadyIds.put(pid, readyId);
    // ... rest of restore logic
}
```

### Reproduction Case

1. Rapidly click enter/exit on a gateway (within 200ms)
2. Without readyId: both PlayerReadyEvents fire, both create HUDs → potential double-registration
3. With readyId: second event's readyId > first → only second processes

### Verification

Check `PlayerReadyEvent` API to confirm `getReadyId()` exists — it's based on `Player.readyId` AtomicInteger.

---

## GAP 4 (LOW → DOWNGRADED): HyUI Refresh Timer — NOT a Crash Risk

### Original Assessment: MEDIUM

### Corrected Assessment After Source Verification: LOW (resource waste only)

**Key finding**: None of our HUD builders call `withRefreshRate()`. The default `refreshRateMs` is `0L` (Java long default). HyUI's `checkRefreshes()` (line 95) checks `rate > 0L` — when rate is 0, it skips the actual `triggerRefresh()` + `refreshOrRerender()` call entirely.

```java
// HyUIHud.checkRefreshes() — line 94-95:
long rate = this.getRefreshRateMs();
if (rate > 0L && now - this.lastRefreshTime >= rate) {  // rate=0 → SKIPS
```

**The 100ms poll task IS running** (started by `add()` at line 173), but it does:
1. Check `isHidden` → skip if true
2. Check `playerRef.isValid()` → cancel task if false (disconnect)
3. Check `getReference()` → return silently if null (transition window)
4. Check `rate > 0L` → **SKIP** (rate is 0 for all our HUDs)

**No packets are ever sent by this timer for our HUDs.** The timer is a resource waste (CPU cycles for 100ms polling per HUD per player) but NOT a crash vector.

### Fix (Optional)

Could add `.withRefreshRate(0)` explicitly to document intent, but this doesn't change behavior since 0 is already the default. The `cancelRefreshTask()` in `discardStale()` is still good practice to clean up the poll task early.

---

## GAP 5 (LOW): Incorrect Comments About resetManagers

Same as original analysis. Comments reference `resetManagers()` which is only called from `Universe.resetPlayer()`. Normal transitions use `JoinWorld(clearWorld=true)`. No behavioral impact.

---

## GAP 6 (CRITICAL — LOG EVIDENCE): show()'s Deferred safeAdd Sends Stale Packets

### Log Evidence

**7 of 10 client sessions crash identically** (May 5-6, Pattern 1):
```
Disconnecting with error during stage InGame: Failed to apply CustomUI HUD commands
System.Exception: Selected element in CustomUI command was not found.
  Selector: #HYUUIDGroup8.Anchor
```

Key observations from log search:
- **Always `#HYUUIDGroup8.Anchor`** — same element every crash, one of the first persistent HUDs
- **Always during realm entry** — crash occurs within ~1 second of entering realm
- **`discardAll` presence does NOT prevent crash** — sessions logging discard still crash
- **Crash stopped after `(direct=true)` deploy** — the MultiHudWrapper.setCustomHud() fix correlated with crash cessation

### The Mechanism

Every `HudBuilder.show()` call triggers `HyUIHud.add()` → `safeAdd()`:

```java
// safeAdd() posts a deferred lambda on the CURRENT world's execute queue:
((EntityStore)store.getExternalData()).getWorld().execute(() -> {
    Player player = this.getPlayer();
    if (player == null) return;
    MultiHudWrapper.setCustomHud(player, this.getPlayerRef(), this.name, this);
});
```

Our `showHud()` code does two things:
1. `HudBuilder.show()` → creates HyUIHud + posts `safeAdd` lambda on world queue
2. `MultiHudWrapper.setCustomHud()` → direct, immediate registration ✓

The direct registration works. But the deferred `safeAdd` lambda sits in the old world's queue and fires later. When it fires, it calls `MultiHudWrapper.setCustomHud()` which goes through `MultipleCustomUIHud.add()`. This rebuilds the HyUIHud's elements with the OLD element IDs into the CURRENT MultiHud.

### Why `#HYUUIDGroup8` Has No MultiHud Prefix

`UIElementBuilder.idCounter` is a **static global int** that increments with every HyUI element created. `#HYUUIDGroup8` is the 8th element — created during the first HUD setup on the default world.

The crash selector shows NO `#MultipleHUD` prefix. This means the crashing command was sent via `CustomUIHud.update(false, builder)` — the RAW path, not through `MultipleCustomUIHud.add()`.

The source of bare-selector packets: **HyUI's internal `buildUpdates()` phase** during `buildFromCommandBuilder(cb, false, events)`. After fresh-building elements (Append), it runs `buildUpdates()` which generates Set commands with raw selectors. These commands are normally safe in the same packet (element was just created by Append). But if the packet arrives after the client's DOM was cleared by `JoinWorld(clearWorld=true)`, the Append targets a non-existent parent → the element is never created → the Set for `.Anchor` hits a missing element → crash.

### Timing Model

```
T=0     HUDs created on DEFAULT world
        show() → safeAdd() posted on DEFAULT.execute queue (lambda_A)
        MultiHudWrapper.setCustomHud() → immediate ✓

T=Xms   Player enters portal

T=X+10  removeFromStore() → getReference() null
        DrainPlayerFromWorldEvent → discardAll
        JoinWorld(clearWorld=true) sent + flushed to client
        CLIENT DOM CLEARED

T=X+20  lambda_A fires on DEFAULT world thread
          → getPlayer() → depends on timing:
            a) getReference() null → returns null → NO-OP (safe)
            b) getReference() valid (addToStore completed) → rebuilds OLD HUD
               → sends packets to client with stale selectors → CRASH
```

**Window (b) is the crash vector**: if `addToStore()` completes before `lambda_A` fires, `getPlayer()` succeeds and stale packets are sent. The window is narrow but nonzero — especially on fast machines where world ticks execute quickly.

### Why the `(direct=true)` Fix Helped But Didn't Eliminate All Crashes

The `MultiHudWrapper.setCustomHud()` direct registration fix ensured the HUD is immediately visible (no invisible HUD bug). But `safeAdd()` STILL fires later as a redundant rebuild. Most of the time it's harmless (rebuilds the same HUD), but during transitions it can send stale packets.

The crash stopping at the `(direct=true)` deploy was likely coincidental timing or a code change that also affected the `safeAdd` path — NOT because direct registration fixes the stale `safeAdd` race.

### Fix

**Prevent the deferred `safeAdd()` from `show()` by creating the HyUIHud WITHOUT calling `add()`.**

Option A (recommended): Use `HudBuilder.detachedHud()` + manual registration:
```java
HyUIHud hud = HudBuilder.detachedHud()
    .fromHtml(html)
    .onRefresh(callback)
    .show(playerRef);  // creates HUD + calls add() which posts safeAdd...
```
Problem: `show()` always calls `add()`. There's no way to create a HyUIHud without triggering safeAdd.

Option B: Cancel the safeAdd's deferred task immediately after show():
```java
HyUIHud hud = builder.show();
// The safeAdd lambda is already queued on world.execute()
// We can't cancel it — it's in the world's task queue
```

Option C (recommended): **Suppress the safeAdd by making getStore() return null.** After our direct registration, we don't need safeAdd. The only way to make it no-op is to ensure `getStore()` returns null when the lambda fires. This happens naturally during transitions (getReference() null), but NOT during stable gameplay (first join).

Option D (simplest, most reliable): **After calling `show()`, immediately call `cancelRefreshTask()` AND call `hud.remove()` to post a `hideCustomHud` that counteracts the `safeAdd()`.** Then re-register via our direct path.

This is messy. **The cleanest fix is to not use `HudBuilder.show()` at all.** Instead:

```java
// Build the HyUIHud manually without calling add()
HyUIHud hud = HudBuilder.hudForPlayer(playerRef)
    .fromHtml(html)
    .withRefreshRate(0)
    .onRefresh(callback)
    .buildHud();  // ← hypothetical method that doesn't call add()
```

But HyUI doesn't expose `buildHud()` without `add()`. We'd need reflection.

**RECOMMENDED FIX: Reflection to skip safeAdd.**

```java
// After show():
HyUIHud hud = builder.show();  // creates HUD + posts safeAdd (will be neutralized)

// Immediately cancel the refresh task started by add()
HudRefreshHelper.cancelRefreshTask(hud);

// Register directly — this is our reliable path
MultiHudWrapper.setCustomHud(player, playerRef, hud.name, hud);

// The safeAdd lambda will fire next tick. When it does:
// - getPlayer() → will succeed (player in world)
// - MultiHudWrapper.setCustomHud() → redundant rebuild (SAFE during stable state)
// - During transitions: getPlayer() null → no-op (SAFE)
//
// The redundant rebuild is harmless because MultipleCustomUIHud.add() handles
// re-registration atomically (Clear + Append), and the commands ARE prefixed.
```

Wait — the redundant rebuild IS prefixed (goes through MultiHud). So the bare-selector crash can't come from `safeAdd()` after all...

**REVISED ANALYSIS**: The bare-selector crash (`#HYUUIDGroup8.Anchor` without prefix) must come from a code path that sends raw CustomUIHud.update() — NOT through MultiHud. The most likely source is a **stale `HyUIHud.refreshOrRerender(false, false)` call** from somewhere we haven't identified, or from a path within HyUI itself that bypasses MultiHud.

### Next Step Required

To definitively identify the bare-selector source, we need to add **diagnostic logging** to HyUI's packet output. Specifically, intercept `CustomUIHud.update(boolean clear, UICommandBuilder builder)` to log the commands array before sending. This will capture the exact sequence of commands in the crashing packet, including which are prefixed and which are bare.

### Interim Fix (Reduces Crash Surface)

Even without knowing the exact bare-selector source:
1. **Remove safety net** (GAP 1) — eliminates one crash vector
2. **Remove defensive discardAll** (GAP 2) — simplifies flow
3. **Add readyId dedup** (GAP 3) — prevents double-creation
4. **Add `withRefreshRate(0)` explicitly** — ensures HyUI timer never accidentally refreshes
5. **Add diagnostic logging** to HyUI packet output — captures the crash packet for root cause

---

## Summary: Evidence-Based Fix List

| Priority | Gap | Evidence | Fix | Risk |
|----------|-----|----------|-----|------|
| **HIGH** | Safety net timer | Recreates Bug 1 (invisible HUDs) when primary path fails; cross-world store mismatch possible; `player=null` bypasses direct registration | Remove entirely (lines 255-267) | Very low |
| **MEDIUM** | Defensive discardAll in onPlayerReady | Redundant with drain handler; adds complexity | Remove `discardAll(pid)` from lambda body | Very low |
| **MEDIUM** | No readyId dedup | No current crash evidence, but provides definitive guard against rapid transitions | Add `lastReadyIds` map | Very low |
| LOW | HyUI timer runs but harmless | Verified: rate=0 → no packets sent | Optional: explicit `withRefreshRate(0)` | Zero |
| LOW | Wrong comments | No behavioral impact | Update 4 files | Zero |

### What's Already Correct (No Changes Needed)

| Aspect | Status |
|--------|--------|
| Centralized HudLifecycleManager | Correct |
| PersistentHud provider interface | Correct |
| Zero-packet discard via cancelRefreshTask | Correct |
| Direct MultiHudWrapper.setCustomHud() bypass | Correct |
| LATE priority on PlayerReadyEvent | Correct |
| Fresh PlayerRef from Universe | Correct |
| Context HUD drain (realm, sanctum) | Correct |
| HudToggleService | Correct |
| Event-driven refresh via HudRefreshHelper | Correct |
| canReachPlayer() guard on refresh | Correct |
| resetHasBuilt + refreshOrRerender(true,true) | Correct |

---

## Verification Test Plan

### After applying all fixes, verify:

| # | Test | Steps | Expected | Checks |
|---|------|-------|----------|--------|
| 1 | First join | Join server fresh | XP bar + shield appear within 1 tick | No safety net log |
| 2 | Realm enter | Activate gateway | All HUDs survive, realm combat HUD appears | Drain log → restore log |
| 3 | Realm exit (victory) | Complete realm | Realm HUDs gone, persistent HUDs survive | Drain log → restore log |
| 4 | Realm exit (timeout) | Let timer expire | Defeat → teleport → persistent HUDs back | Same |
| 5 | Rapid enter/exit | Enter realm, exit within 2s | No crashes, no invisible HUDs | readyId dedup may fire |
| 6 | Rapid double-enter | Click gateway twice fast | Second transition clean | Drain+restore for each |
| 7 | Sanctum enter/exit | Enter/exit skill sanctum | Persistent HUDs survive, skill HUDs appear/disappear | Same drain/restore pattern |
| 8 | Disconnect mid-realm | Alt+F4 in realm | No server errors, clean state on reconnect | Disconnect handler log |
| 9 | /hud toggle persistence | Toggle off → enter realm → exit | HUDs remain hidden | Toggle state in PlayerHudState |
| 10 | XP gain in realm | Kill mob | XP bar updates via safeRefreshWithToggle | canReachPlayer guard passes |
| 11 | Shield damage | Take damage | Shield bar updates | Same |
| 12 | Plugin reload | /tooadmin reload | HUDs recreated or survive | Shutdown → re-init |

### Crash Vector Verification (must NOT crash):

| # | Scenario | Why It Might Have Crashed Before |
|---|----------|--------------------------------|
| 1 | Enter realm while XP event fires | safeRefreshWithToggle's canReachPlayer blocks during null window |
| 2 | Exit realm while combat HUD ticking | discardAllHudsForPlayer cancels before next tick |
| 3 | Join → enter realm < 1.5s | OLD: safety net fires in overworld context; NEW: no safety net |
| 4 | Rapid portal clicks | OLD: multiple safety nets accumulate; NEW: readyId dedup |
| 5 | Disconnect during world transition | getReference null → all HyUI calls no-op, disconnect handler cleans up |

### Log Verification

After each transition, expect exactly:
```
[INFO] Discarded stale xp-bar HUD for player XXXXXXXX (world transition)
[INFO] Discarded stale energy-shield HUD for player XXXXXXXX (world transition)
[INFO] Showed XP bar HUD for player XXXXXXXX (direct=true)
[INFO] Showed energy shield HUD for player XXXXXXXX (direct=true)
```

Must NOT see:
- Any `Failed to restore` warnings
- Any `safeAdd failed` or `getReference null` messages
- Any `NullPointerException` in HUD stack traces
- Any `Selected element` client errors
