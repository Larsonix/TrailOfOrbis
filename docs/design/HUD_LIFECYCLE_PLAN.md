# HUD Lifecycle Architecture — Clean-Room Design

> **Purpose**: Ground-truth plan for HUD lifecycle management in Trail of Orbis, designed from first principles by reading HyUI source and Hytale server code.
>
> **Date**: 2026-05-06
> **Status**: PLAN — ready for comparison against current implementation

---

## Table of Contents

1. [Ground Truth: What Actually Happens](#1-ground-truth-what-actually-happens)
2. [HyUI API Safety Matrix](#2-hyui-api-safety-matrix)
3. [Our HUD Inventory](#3-our-hud-inventory)
4. [Architecture Design](#4-architecture-design)
5. [Detailed Lifecycle Flows](#5-detailed-lifecycle-flows)
6. [Implementation Specification](#6-implementation-specification)
7. [Failure Mode Analysis](#7-failure-mode-analysis)
8. [Verification Checklist](#8-verification-checklist)

---

## 1. Ground Truth: What Actually Happens

### 1.1 World Transition Sequence (Normal Cross-World Teleport)

This is the standard path when a player enters/exits a realm or sanctum.

```
CURRENT WORLD (world thread)
  │
  ├─ TeleportSystems detects Teleport component with targetWorld != currentWorld
  ├─ playerRef.removeFromStore()
  │    → entity field set to null
  │    → holder preserved
  │    → getReference() NOW RETURNS NULL
  │    → isValid() still returns true (holder exists)
  │
  ├─ targetWorld.addPlayer(playerRef, transform)
  │
TARGET WORLD (async chain)
  │
  ├─ AddPlayerToWorldEvent dispatched
  ├─ onSetupPlayerJoining():
  │    ├─ LegacyEntityTrackerSystems.clear()
  │    ├─ ChunkTracker.clear()
  │    ├─ JoinWorld packet sent (clearWorld=true, fadeInOut=true)  ← CLIENT CLEARS WORLD + UI
  │    └─ setQueuePackets(true)  ← ALL subsequent packets QUEUED, not flushed
  │
  ├─ [async] Chunk loading begins
  ├─ [async] Client receives JoinWorld(clearWorld=true)
  │    └─ Client clears its entire world state including UI DOM
  │
  ├─ Client sends ClientReady(readyForChunks=true, readyForGameplay=false)  ← FIRST
  │    └─ Completes clientReadyForChunksFuture
  ├─ Client sends ClientReady(readyForGameplay=true)  ← SECOND
  │    └─ Dispatches PlayerReadyEvent (with incrementing readyId)
  │
  ├─ onFinishPlayerJoining():
  │    ├─ Sends ViewRadius, SetEntitySeed, SetClientId, etc.
  │    ├─ playerRef.addToStore(store)  ← getReference() VALID AGAIN
  │    └─ setQueuePackets(false) → all queued packets FLUSH
  │
  └─ Player is fully in the new world
```

**Critical observations:**
- `resetManagers()` is **NOT called** during normal world transitions. It only exists in `Universe.resetPlayer()` (the `/player reset` command path).
- The `JoinWorld(clearWorld=true)` packet tells the client to clear its world. The client destroys its UI DOM as part of this.
- There is a **packet queue** active between `onSetupPlayerJoining` and `onFinishPlayerJoining`. Packets written during this window are queued and flushed together after `addToStore()`.
- `getReference()` is null from `removeFromStore()` until `addToStore()` — a window of potentially **seconds** (includes chunk loading + network round-trips).

### 1.2 World Drain Sequence (Realm Close / World Shutdown)

```
CURRENT WORLD (world thread)
  │
  ├─ world.drainPlayersTo(fallbackWorld)
  ├─ For each player:
  │    ├─ playerRef.removeFromStore()  ← getReference() null
  │    ├─ DrainPlayerFromWorldEvent dispatched (with holder + fallback world)
  │    └─ fallbackWorld.addPlayer(playerRef, transform)  ← same as above
  │
  └─ (same JoinWorld + addPlayer flow as Section 1.1)
```

**Key difference**: `DrainPlayerFromWorldEvent` fires AFTER `removeFromStore()` but BEFORE the player joins the new world. This is the only mod-hookable event in the drain path.

### 1.3 What the Client Does with JoinWorld(clearWorld=true) — CLIENT SOURCE CONFIRMED

**Source**: Client binary analysis of `HytaleClient.exe`.
**Key function**: CustomUICommand processor.

The client processes CustomUICommands via a `switch(commandType)` with 7 cases (0-6):

```
Case 0 (Append):     Resolve selector → find parent element → append children
Case 1 (AppendInline): Parse inline HTML → append to selector target
Case 2 (InsertBefore):  Resolve selector → insert before target
Case 3 (InsertBeforeInline): Parse inline HTML → insert before target
Case 4 (Remove):     Resolve selector → remove element from parent's child list
Case 5 (Set):        Resolve selector → parse CSS/attribute data → apply to element
Case 6 (Clear):      Resolve selector → remove all children
```

**CRITICAL CLIENT BEHAVIOR — confirmed from client analysis:**

For **every command type**, the client first resolves the selector to a DOM element. If the selector doesn't match:

| Command | Missing Selector | Client Behavior |
|---------|-----------------|-----------------|
| Append (0) | `local_48 == 0` | Logs "Could not find document [selector] for Custom UI [type] command. Selector: " → **CRASHES** (subroutine does not return) |
| AppendInline (1) | `local_48 == 0` | Same crash path |
| InsertBefore (2) | `local_48 == 0` | Logs "CustomUI [type] command needs a selected element" → **CRASHES** |
| InsertBeforeInline (3) | `local_48 == 0` | Same crash path |
| Remove (4) | `local_48 == 0` | **CRASHES** immediately (no log message, direct throw) |
| Set (5) | `local_48 == 0` | **CRASHES** immediately (no log message, direct throw) |
| Clear (6) | Same pattern expected |

Additionally, for Append/AppendInline (cases 0-1), if the selected element **exists but doesn't accept children**:
- Logs "CustomUI [type] command's selected element doesn't accept children. Selector: [selector]" → **CRASHES**

**The client has ZERO graceful error handling for missing selectors. Every single command type crashes hard.**

This means after `JoinWorld(clearWorld=true)` destroys the client DOM:
- `hide()` sends `Set` (type 5) → selector not found → **CRASH**
- `unhide()` sends `Set` (type 5) → selector not found → **CRASH**
- `remove()` sends `Remove` (type 4) → selector not found → **CRASH**
- `refreshOrRerender(false, *)` sends `Set` (type 5) → selector not found → **CRASH**
- `refreshOrRerender(true, *)` sends `Append` via `safeAdd()` rebuild → appends to `#MultipleHUD` which may or may not exist → **CRASH if MultiHud container was cleared**

The ONLY safe operations after DOM clear:
- `CustomHud(clear=true, commands)` → client clears its DOM first (idempotent), then processes fresh Append commands against a known-clean state
- Sending nothing at all (zero packets)

### 1.4 PlayerRef.getReference() Null Window

```java
// PlayerRef internals:
public Ref<EntityStore> getReference() {
    if (this.entity != null && this.entity.isValid()) return this.entity;
    return null;
}
```

| Phase | entity | holder | isValid() | getReference() |
|-------|--------|--------|-----------|----------------|
| In world normally | ref | null | true | **valid ref** |
| After removeFromStore() | null | holder | true | **null** |
| During addPlayer async chain | null | holder | true | **null** |
| After addToStore() | new ref | null | true | **valid ref** |
| Disconnected | null | null | false | **null** |

**HyUI methods that call getReference() and silently fail when null:**
- `safeAdd()` — does nothing (HUD not registered with MultiHud)
- `remove()` — does nothing (HUD not removed, but refresh task IS cancelled due to a bug)
- `addUnsafe()` — does nothing
- `removeUnsafe()` — does nothing (refresh task NOT cancelled — different bug)
- `checkRefreshes()` — returns silently (refresh skipped, task continues)

### 1.5 Dual ClientReady Packets

The client sends **two** separate `ClientReady` packets per world transition:

1. `ClientReady(readyForChunks=true, readyForGameplay=false)` — "I've cleared the world, ready for chunks"
2. `ClientReady(readyForGameplay=true)` — "Chunks loaded, ready for gameplay"

Only the second dispatches `PlayerReadyEvent`. There's also a 10-second timeout fallback.

The `readyId` (AtomicInteger) increments on each `handleClientReady`, providing deduplication: listeners can compare the `readyId` from the event against a stored value.

### 1.6 Packet Queueing Window

Between `onSetupPlayerJoining()` and `onFinishPlayerJoining()`:
- `setQueuePackets(true)` is active
- All packets written via `writeNoCache()` are **queued, not flushed**
- They flush together after `addToStore()` completes

**Implication**: If we create and show a HUD during the queued window (e.g., from a `PlayerReadyEvent` listener that fires before `onFinishPlayerJoining`), the packets will be queued and flush together with the engine's setup packets. The HUD will appear, but timing is engine-controlled.

---

## 2. HyUI API Safety Matrix

### 2.1 Methods and Their Safety During World Transitions

| Method | What It Sends | Safe During Transition? | Notes |
|--------|---------------|------------------------|-------|
| `HudBuilder.show()` | Deferred `safeAdd()` | ⚠️ SILENTLY FAILS | `getReference()` null → `safeAdd()` no-ops |
| `HyUIHud.add()` | `safeAdd()` + starts refresh timer | ⚠️ SILENTLY FAILS | Same as show(), but refresh timer DOES start |
| `HyUIHud.addUnsafe()` | Direct `getPlayer()` → `setCustomHud()` | ⚠️ SILENTLY FAILS | Same null problem, no deferral |
| `HyUIHud.hide()` | `Set` visibility commands | ❌ CLIENT CRASH | References destroyed DOM elements |
| `HyUIHud.hideUnsafe()` | `Set` visibility commands | ❌ CLIENT CRASH | Same |
| `HyUIHud.unhide()` | `Set` visibility commands | ❌ CLIENT CRASH | Same |
| `HyUIHud.remove()` | Deferred `hideCustomHud()` | ⚠️ PARTIAL | Deferred call no-ops, but refresh task cancelled |
| `HyUIHud.removeUnsafe()` | Direct `hideCustomHud()` | ⚠️ PARTIAL | No-ops if null, refresh NOT cancelled |
| `HyUIHud.refreshOrRerender(false, *)` | `Set` diff commands | ❌ CLIENT CRASH | References destroyed DOM elements |
| `HyUIHud.refreshOrRerender(true, false)` | `safeAdd()` full rebuild | ⚠️ SILENTLY FAILS | Same null problem |
| `HyUIHud.refreshOrRerender(true, true)` | Direct full rebuild | ⚠️ SILENTLY FAILS | Same null problem |
| `MultiHudWrapper.setCustomHud(player, ref, name, hud)` | Build + register via player | ✅ SAFE if Player is available | Bypasses getReference(), uses Player directly |
| `MultiHudWrapper.hideCustomHud(player, ref, name)` | Remove from MultiHud | ✅ SAFE if Player is available | Same bypass |

### 2.2 The Only Reliable Registration Method

`MultiHudWrapper.setCustomHud(Player player, PlayerRef playerRef, String name, HyUIHud hud)`

This is the **only** method that can reliably register a HUD during the transition window, because it takes a `Player` component directly instead of resolving it through `getReference()`. The `Player` component is available:
- From `DrainPlayerFromWorldEvent` (via holder)
- From `PlayerReadyEvent` (by resolving through the event's holder or from the world's store after `addToStore()`)
- From any ECS system context (via commandBuffer)

### 2.3 HyUI Refresh Timer Behavior

- Static single-thread `ScheduledExecutorService` shared by ALL HyUIHud instances
- Polls every **100ms** regardless of configured refresh rate
- Runs `triggerRefresh()` callback **on the scheduler thread** (NOT world thread)
- Calls `refreshOrRerender(true, false)` which posts to world thread via `safeAdd()`
- During transitions: `getReference()` null → `safeAdd()` silently fails → refresh skipped
- Refresh timer continues running, will auto-recover when `getReference()` becomes valid again
- Timer self-cancels only when `playerRef.isValid()` returns false (full disconnect)

### 2.4 HyUI Bugs We Must Work Around

1. **`remove()` cancels refresh even when `getStore()` is null** — the deferred cleanup doesn't post, but `refreshTask.cancel()` runs unconditionally
2. **`removeUnsafe()` doesn't cancel refresh when `getPlayer()` is null** — refresh task leaks
3. **`isHidden` flag desyncs on double-call** — `this.isHidden = !this.isHidden` flips regardless of target state
4. **No synchronization** between scheduler thread and world thread for shared mutable state
5. **First MultiHud registration sends clear=true** — when `MultipleCustomUIHud` is first created, `HudManager.setCustomHud()` sends `CustomHud(clear=true)`, wiping any pre-existing custom HUD

---

## 3. Our HUD Inventory

### 3.1 Classification

| # | HUD | Category | Refresh | Survives Transition? |
|---|-----|----------|---------|---------------------|
| 1 | XP Bar | PERSISTENT | Event-driven (XP gain, level up) | Yes — must be restored |
| 2 | Energy Shield (Health+Shield) | PERSISTENT | Event-driven (health/shield change) | Yes — must be restored |
| 3 | Combat Feedback Ghost | PERSISTENT (infra) | Lazy position update | Yes — entity re-created |
| 4 | Realm Combat HUD | CONTEXT | World-thread tick (~1s) | No — exists only in realm |
| 5 | Realm Victory HUD | CONTEXT | Static (no refresh) | No — exists only in realm |
| 6 | Realm Defeat HUD | CONTEXT | Static (no refresh) | No — exists only in realm |
| 7 | Skill Points HUD | CONTEXT | Event-driven (alloc/dealloc) | No — exists only in sanctum |
| 8 | Skill Node Detail HUD | CONTEXT | Static (rebuilt per click) | No — exists only in sanctum |

### 3.2 Lifecycle Requirements

**PERSISTENT HUDs** (XP Bar, Energy Shield, Combat Ghost):
- Must be visible in ALL worlds at ALL times
- Must survive world transitions (realm enter/exit, sanctum enter/exit)
- Must be restored after the client clears its DOM (JoinWorld clearWorld=true)
- Must handle disconnect cleanup
- Must handle plugin shutdown cleanup
- Must support toggle (player can hide/show via command)

**CONTEXT HUDs** (Realm Combat/Victory/Defeat, Skill Points, Skill Node Detail):
- Only exist in specific worlds
- Destroyed when player leaves that world
- No restoration needed — created fresh when entering the context
- Must be cleaned up on drain to avoid stale references

---

## 4. Architecture Design

### 4.1 Core Principle: Don't Fight the Engine

The key insight from researching other Hytale plugins: **none of them handle world transitions explicitly, and most of them work fine.** Why?

Because the engine naturally handles the transition:
1. `JoinWorld(clearWorld=true)` clears the client DOM
2. `setQueuePackets(true)` buffers all server packets
3. After `addToStore()`, packets flush and the player is ready

**The correct approach is not to fight this sequence but to work WITH it:**
- Don't try to send packets during the transition window
- Don't try to preserve HUD state across the DOM clear
- Simply **recreate** persistent HUDs after the player is fully in the new world
- Let context HUDs die naturally when their world is left

### 4.2 The Two-Phase Model

```
Phase 1: DISCARD (on world exit)
  → Cancel refresh timers
  → Drop all internal tracking references
  → Send ZERO packets (the DOM is about to be cleared anyway)

Phase 2: RESTORE (on world entry, AFTER addToStore)
  → Create fresh HUD instances
  → Register via MultiHudWrapper.setCustomHud() with real Player
  → Start refresh timers
```

**Why this is better than hide/remove/restore:**
- Zero crash vectors: no Set/Remove commands to a cleared DOM
- Zero silent failures: no dependency on `getReference()` during the null window
- Zero timing races: restoration happens after the player is fully in the store
- Simpler mental model: HUDs are ephemeral objects, recreated as needed

### 4.3 Architecture Diagram

```
┌─────────────────────────────────────────────────────┐
│                   HudCoordinator                     │
│  (single entry point for ALL HUD lifecycle events)   │
│                                                       │
│  Listens to:                                          │
│    • PlayerReadyEvent (LATE priority)                 │
│    • DrainPlayerFromWorldEvent                        │
│    • PlayerDisconnectEvent                            │
│    • Plugin onDisable                                 │
│                                                       │
│  Owns:                                                │
│    • Map<UUID, PlayerHudState>                        │
│    • List<HudProvider> providers                      │
│                                                       │
│  PlayerHudState:                                      │
│    • int lastReadyId (dedup dual ClientReady)         │
│    • boolean hudToggleEnabled                         │
│    • Set<String> activeHudNames                       │
│    • World currentWorld                               │
│                                                       │
├─────────────────────────────────────────────────────┤
│                                                       │
│  HudProvider interface:                               │
│    + getName(): String                                │
│    + getCategory(): PERSISTENT | CONTEXT              │
│    + shouldShow(uuid, world): boolean                 │
│    + create(uuid, playerRef, player, store): HyUIHud  │
│    + discard(uuid): void  [cancel timers, drop refs]  │
│    + onDisconnect(uuid): void                         │
│    + onShutdown(): void                               │
│                                                       │
│  Implementations:                                     │
│    • XpBarProvider                                     │
│    • EnergyShieldProvider                              │
│    • CombatGhostProvider                               │
│    • RealmCombatHudProvider                             │
│    • RealmVictoryHudProvider                            │
│    • RealmDefeatHudProvider                             │
│    • SkillPointHudProvider                              │
│    • SkillNodeDetailHudProvider                         │
│                                                       │
└─────────────────────────────────────────────────────┘
```

### 4.4 Why a Single Coordinator

Currently, each HUD manager independently listens for events and manages its own lifecycle. This creates:
- Multiple independent drain handlers that can race
- Multiple independent restore handlers with different timing
- No centralized deduplication for dual ClientReady
- No centralized toggle state management
- Debugging requires tracing through N independent managers

A single coordinator:
- One drain handler, one restore handler, deterministic ordering
- One dedup check using `readyId`
- One toggle state map
- One place to add logging for lifecycle debugging
- Providers are simple: they just create/discard HUDs when told

---

## 5. Detailed Lifecycle Flows

### 5.1 Player Joins Server (First World)

```
PlayerReadyEvent (LATE priority)
  │
  ├─ HudCoordinator.onPlayerReady(event)
  │    ├─ Get or create PlayerHudState for UUID
  │    ├─ Check readyId: if <= lastReadyId, SKIP (dedup)
  │    ├─ Update lastReadyId
  │    ├─ Resolve Player from event holder or world store
  │    ├─ Resolve World from player
  │    │
  │    ├─ For each HudProvider:
  │    │    ├─ if provider.shouldShow(uuid, world):
  │    │    │    ├─ HyUIHud hud = provider.create(uuid, playerRef, player, store)
  │    │    │    ├─ MultiHudWrapper.setCustomHud(player, playerRef, hud.name, hud)
  │    │    │    ├─ Record hud.name in activeHudNames
  │    │    │    └─ Apply toggle state if needed
  │    │    └─ else: skip
  │    │
  │    └─ Log: "Restored N HUDs for player X in world Y"
```

### 5.2 Player Transitions Between Worlds (Realm Enter/Exit)

```
DrainPlayerFromWorldEvent (fires AFTER removeFromStore, BEFORE addPlayer)
  │
  ├─ HudCoordinator.onDrain(event)
  │    ├─ Get PlayerHudState for UUID
  │    ├─ For each active HUD name:
  │    │    ├─ Find the provider
  │    │    ├─ provider.discard(uuid)
  │    │    │    ├─ Cancel any scheduled refresh task
  │    │    │    ├─ Remove from internal tracking map
  │    │    │    └─ DO NOT call hide(), remove(), or send any packets
  │    │    └─ Remove from activeHudNames
  │    │
  │    └─ Log: "Discarded N HUDs for player X (draining from world Y)"
  │
  ... (JoinWorld sent, client clears DOM, chunks load, ClientReady sent) ...
  │
PlayerReadyEvent (LATE priority)
  │
  └─ (same flow as 5.1 — creates fresh HUDs for the new world)
```

**Why LATE priority on PlayerReadyEvent**: We want to run AFTER other systems have set up their world state (realm modifiers, sanctum layout, etc.) so that when we create context HUDs, the data they need is ready.

**Why we do NOT need a safety net timer**: The `PlayerReadyEvent` fires reliably — Hytale has a 10-second timeout fallback if the client never sends `ClientReady`. If the event fires and `addToStore()` hasn't completed yet (packets are still queued), our HUD creation packets will be queued and flushed together with the engine's setup packets. This is actually ideal — the HUDs appear in the same frame as the world.

### 5.3 Player Disconnects

```
PlayerDisconnectEvent
  │
  ├─ HudCoordinator.onDisconnect(event)
  │    ├─ Get PlayerHudState for UUID
  │    ├─ For each active HUD name:
  │    │    ├─ provider.onDisconnect(uuid)
  │    │    │    ├─ Cancel refresh task
  │    │    │    └─ Cleanup internal state
  │    │    └─ (no packets needed — client is disconnecting)
  │    │
  │    ├─ Remove PlayerHudState from coordinator map
  │    └─ Log: "Cleaned up HUDs for disconnecting player X"
```

### 5.4 Plugin Shutdown

```
TrailOfOrbis.onDisable()
  │
  ├─ hudCoordinator.shutdown()
  │    ├─ For each provider:
  │    │    └─ provider.onShutdown()
  │    │         └─ Clear all internal maps, cancel all tasks
  │    └─ Clear coordinator state
```

### 5.5 Context HUD Lifecycle (e.g., Realm Combat HUD)

Context HUDs have an additional trigger: they can be created/replaced mid-session within their world, not just on world entry.

```
Player enters realm → PlayerReadyEvent → RealmCombatHudProvider.shouldShow() returns true
  → create() builds the combat HUD with realm data

Kill threshold reached → RealmHudManager.showVictoryHud(uuid)
  → RealmCombatHudProvider.discard(uuid)  [removes combat HUD]
  → RealmVictoryHudProvider creates and registers victory HUD via coordinator

Player exits realm → DrainPlayerFromWorldEvent
  → All realm HUD providers discard their HUDs
  → PlayerReadyEvent in overworld
  → shouldShow() returns false for realm providers → no realm HUDs created
  → shouldShow() returns true for persistent providers → XP bar + shield restored
```

### 5.6 HUD Toggle Flow

```
Player runs /hud toggle
  │
  ├─ HudCoordinator.toggleHuds(uuid)
  │    ├─ Flip hudToggleEnabled in PlayerHudState
  │    ├─ For each active HUD:
  │    │    ├─ If toggling OFF: refreshOrRerender to set root visibility false
  │    │    │    (this is SAFE — player is in a stable world, DOM exists)
  │    │    └─ If toggling ON: refreshOrRerender to set root visibility true
  │    └─ Return new toggle state
```

**Note**: Toggle uses `refreshOrRerender` which sends `Set` commands. This is safe because the player is in a stable world state, not mid-transition. The discard flow (which runs during transitions) does NOT call hide/unhide.

### 5.7 Event-Driven Refresh (XP Gain, Shield Change, etc.)

```
XP gain event fires
  │
  ├─ XpBarProvider.onXpChanged(uuid)
  │    ├─ Check if HUD is active for this player (internal map)
  │    ├─ If yes: trigger HyUI's refreshOrRerender(true, false) [full rebuild via safeAdd]
  │    │    └─ This is safe: player is in world, getReference() valid
  │    └─ If no: ignore (player might be mid-transition or HUD was toggled off)
```

### 5.8 Tick-Driven Refresh (Realm Combat Timer)

```
Realm tick loop (world thread)
  │
  ├─ RealmCombatHudProvider.tick(realmPlayers)
  │    ├─ For each player in the realm with an active combat HUD:
  │    │    └─ hud.refreshOrRerender(true, false)  [full rebuild via safeAdd]
  │    │         └─ Safe: player is in the realm world, in the store
```

---

## 6. Implementation Specification

### 6.1 HudCoordinator

```java
public class HudCoordinator {
    private final Map<UUID, PlayerHudState> states = new ConcurrentHashMap<>();
    private final List<HudProvider> providers = new ArrayList<>();

    // Registration
    public void register(HudProvider provider);

    // Event handlers (registered in onEnable)
    void onPlayerReady(PlayerReadyEvent event);      // LATE priority
    void onDrain(DrainPlayerFromWorldEvent event);
    void onDisconnect(PlayerDisconnectEvent event);

    // Mid-session operations (called by other systems)
    public void showHud(UUID playerId, String providerName);
    public void discardHud(UUID playerId, String providerName);
    public void replaceHud(UUID playerId, String oldProvider, String newProvider);
    public void toggleAll(UUID playerId);

    // Lifecycle
    public void shutdown();
}
```

### 6.2 HudProvider Interface

```java
public interface HudProvider {
    /** Unique name for this provider (used as MultiHud identifier) */
    String getName();

    /** PERSISTENT = survives world transitions, CONTEXT = world-specific */
    HudCategory getCategory();

    /** Whether this HUD should be shown for this player in this world */
    boolean shouldShow(UUID playerId, World world);

    /**
     * Create and return a fresh HUD instance.
     * Called on the world thread with a valid Player reference.
     * The coordinator handles MultiHudWrapper registration.
     */
    HyUIHud create(UUID playerId, PlayerRef playerRef, Player player, Store<EntityStore> store);

    /**
     * Discard internal state for this player. Cancel timers, clear maps.
     * MUST NOT send any packets (no hide, no remove, no refresh).
     */
    void discard(UUID playerId);

    /** Called on disconnect. Cancel timers, clear maps. */
    void onDisconnect(UUID playerId);

    /** Called on plugin shutdown. Clear everything. */
    void onShutdown();
}
```

### 6.3 PlayerHudState

```java
public class PlayerHudState {
    private final UUID playerId;
    private int lastReadyId = -1;
    private boolean hudToggleEnabled = true;
    private final Map<String, HyUIHud> activeHuds = new LinkedHashMap<>();

    boolean checkAndUpdateReadyId(int readyId) {
        if (readyId <= lastReadyId) return false; // dedup
        lastReadyId = readyId;
        return true;
    }
}
```

### 6.4 Player Resolution Strategy

The coordinator must resolve a `Player` component to pass to `MultiHudWrapper.setCustomHud()`. During `PlayerReadyEvent`, there are two cases:

**Case A: `addToStore()` has completed (getReference() valid)**
```java
Ref<EntityStore> ref = playerRef.getReference();
Player player = store.getComponent(ref, Player.getComponentType());
```

**Case B: `addToStore()` has NOT yet completed (packets queued)**
This can happen if `PlayerReadyEvent` fires before `onFinishPlayerJoining` completes. In this case, packets are queued and will flush when `addToStore()` completes.

```java
// Use the holder to get the Player component
Holder<EntityStore> holder = /* from event or playerRef internal */;
Player player = holder.getComponent(Player.getComponentType());
```

**Recommended approach**: Use `Universe.get().getPlayer(uuid)` which returns the `PlayerRef`, then resolve Player. If `getReference()` is null, use LATE event priority to ensure `addToStore()` has happened. If it still fails (edge case), schedule a single retry on `world.execute()`.

### 6.5 Discard Rules (Zero-Packet Discard)

When discarding, providers MUST:
1. Cancel any `ScheduledFuture` refresh task: `refreshTask.cancel(false)`
2. Remove from internal tracking maps: `activeHuds.remove(uuid)`
3. Null out the `HyUIHud` reference so GC can collect it

Providers MUST NOT:
1. Call `hud.hide()` — sends Set to cleared DOM → crash
2. Call `hud.unhide()` — same
3. Call `hud.remove()` — `getReference()` null → no-ops anyway, but the implicit refresh cancel has a potential NPE bug
4. Call `hud.removeUnsafe()` — `getPlayer()` null → no-ops, but refresh task NOT cancelled
5. Call `hud.refreshOrRerender(false, *)` — sends Set to cleared DOM → crash
6. Call any method that sends packets

**Why we can just drop the reference**: The client's DOM is about to be cleared (or already cleared) by `JoinWorld(clearWorld=true)`. There's nothing to clean up on the client side. The HyUI `HyUIHud` object's refresh task is the only server-side resource that needs cleanup, and we handle that by cancelling the `ScheduledFuture` directly.

**How to access the refresh task**: HyUI stores it as `this.refreshTask` (a `ScheduledFuture<?>`). Since this is a private field, we have two options:
- **Option A**: Reflection to access `refreshTask` and cancel it. Fragile but direct.
- **Option B**: Call `hud.remove()` which cancels the task (it does `this.refreshTask.cancel(false)` unconditionally, even when `getStore()` returns null). The deferred world.execute() will no-op. The refresh cancel is the side effect we want.
- **Option C**: Don't use HyUI's refresh timer at all. Manage our own refresh scheduling.

**Recommendation: Option C** — manage our own refresh. HyUI's refresh has threading issues (callback runs on scheduler thread, not world thread), the 100ms poll is wasteful, and the `safeAdd()` path can silently fail. Instead:
- For event-driven HUDs (XP bar, shield): call `refreshOrRerender(true, false)` directly when the event fires (we know the player is in a stable world)
- For tick-driven HUDs (realm combat): call `refreshOrRerender(true, false)` from the world-thread tick loop
- For static HUDs (victory, defeat, skill node): no refresh needed
- Set `withRefreshRate(0)` on all HudBuilders to disable HyUI's internal timer entirely

### 6.6 MultiHudWrapper Registration Sequence

```java
// In HudCoordinator.restoreHuds(), on the world thread, after addToStore():

HyUIHud hud = provider.create(playerId, playerRef, player, store);
// create() builds the HUD via HudBuilder but does NOT call show() or add()

// Register directly, bypassing safeAdd/addUnsafe:
MultiHudWrapper.setCustomHud(player, playerRef, hud.name, hud);

// Track it:
state.activeHuds.put(provider.getName(), hud);
```

**Why `create()` must NOT call `show()`/`add()`**:
- `show()` calls `add()` which calls `safeAdd()` which depends on `getReference()`
- We bypass this entirely with direct `MultiHudWrapper.setCustomHud()`
- The HudBuilder should use `.show()` only as the final step in `create()`, as it returns the `HyUIHud` object. Then the coordinator does the actual registration.

Actually, re-reading HyUI source: `HudBuilder.show()` does three things:
1. Creates the `HyUIHud` object
2. Calls `lastHud.add()` (which calls `safeAdd()` + starts refresh timer)
3. Returns the `HyUIHud`

We want step 1 but NOT step 2. Options:
- Use `HudBuilder.detachedHud()` (creates a HyUIHud without a PlayerRef, won't auto-show)
- Call `show()` but immediately cancel the refresh task and ignore the `safeAdd()` (it will fail silently during transitions, succeed during stable state — either way we override with our own registration)
- Use reflection to create HyUIHud without calling add()

**Recommendation**: Let `show()` run normally. During transitions, `safeAdd()` silently fails (getReference null), which is fine — we override with `MultiHudWrapper.setCustomHud()`. During stable state (context HUDs mid-session), `safeAdd()` succeeds normally. Set `withRefreshRate(0)` to prevent the timer from starting. This way the code path is the same regardless of when create() is called.

```java
// In provider.create():
HyUIHud hud = HudBuilder.hudForPlayer(playerRef)
    .fromHtml(buildHtml())
    .withRefreshRate(0)        // disable HyUI's internal refresh
    .onRefresh(this::refresh)  // still used for manual refreshOrRerender calls
    .show();                   // creates object + safeAdd (may silently fail = OK)
return hud;
```

Then in the coordinator:
```java
HyUIHud hud = provider.create(playerId, playerRef, player, store);
// Override registration with direct MultiHud:
MultiHudWrapper.setCustomHud(player, playerRef, hud.name, hud);
```

---

## 7. Failure Mode Analysis

### 7.1 "HUD doesn't appear after world transition"

**Root cause (with current approach)**: `safeAdd()` silently fails because `getReference()` is null during the transition window. The HUD is created but never registered with the MultiHud system.

**Fix in this plan**: Direct `MultiHudWrapper.setCustomHud()` with the real Player object, bypassing `getReference()`.

**Remaining risk**: If `PlayerReadyEvent` fires before `addToStore()` and the Player component can't be resolved from the holder. **Mitigation**: Use LATE event priority. If that's insufficient, retry once on `world.execute()`.

### 7.2 "Client crash during world transition"

**Root cause**: Sending `Set`/`Remove` commands (via `hide()`, `remove()`, `refreshOrRerender(false,*)`) to DOM elements that were destroyed by `JoinWorld(clearWorld=true)`.

**Fix in this plan**: Zero-packet discard. The `discard()` method sends no packets at all. The only server-side resource to clean up is the refresh timer, and we don't use HyUI's timer.

**Remaining risk**: A stale refresh timer fires during the transition window. **Mitigation**: `withRefreshRate(0)` disables HyUI's timer. Our own event-driven refreshes only fire when the player is in a stable world state.

### 7.3 "Packet storm during world transition"

**Root cause**: Unconditional safety nets that destroy and recreate all HUDs on every event, even when the primary path succeeded. Each cycle sends ~6 packets per HUD.

**Fix in this plan**: No safety nets. The design has exactly ONE creation path (PlayerReadyEvent) and ONE destruction path (DrainPlayerFromWorldEvent). Deduplication via `readyId` prevents double-creation from dual ClientReady packets.

### 7.4 "HUD refresh races with world transition"

**Root cause**: HyUI's scheduler thread fires a refresh callback while the player is mid-transition. The callback tries to update a HUD whose DOM no longer exists.

**Fix in this plan**: Don't use HyUI's refresh timer. Event-driven refreshes check `state.activeHuds.containsKey()` before refreshing. Tick-driven refreshes only iterate players currently in the world.

### 7.5 "Duplicate HUD after transition"

**Root cause**: Dual `ClientReady` packets → `PlayerReadyEvent` fires twice → HUDs created twice.

**Fix in this plan**: `checkAndUpdateReadyId()` in `PlayerHudState` rejects stale `readyId` values.

### 7.6 "HUD toggle doesn't persist across transitions"

**Root cause**: Toggle state stored per-HUD instance, lost when HUD is recreated.

**Fix in this plan**: Toggle state stored in `PlayerHudState` (per-player, not per-HUD). Applied after creation in the restore flow.

### 7.7 "Context HUD lingers after leaving world"

**Root cause**: Context HUD's refresh timer continues to fire after the player leaves the world, or the HUD reference leaks.

**Fix in this plan**: `discard()` is called for ALL active HUDs during drain, including context HUDs. No timers to leak. The client DOM is cleared by the engine.

### 7.8 "HUD appears for a frame then disappears"

**Root cause**: MultiHud first-registration sends `CustomHud(clear=true)`, wiping a HUD that was just created by another provider.

**Fix in this plan**: The coordinator creates ALL HUDs in sequence, and the MultiHud system adds them all within a single update cycle. The first `setCustomHud()` call creates the `MultipleCustomUIHud` and shows it (clear=true), then subsequent calls add to it (AppendInline, no clear). As long as all providers are invoked in the same restore pass, this is safe.

**Ordering rule**: When restoring, process providers in the same order every time. The first provider's HUD triggers the MultiHud initialization; subsequent ones are additive.

---

## 8. Verification Checklist

### 8.1 Test Scenarios

| # | Scenario | Expected | How to Verify |
|---|----------|----------|---------------|
| 1 | Player joins server | XP bar + shield appear | Visual check |
| 2 | Player enters realm via gateway | XP bar + shield persist, realm combat HUD appears | Visual check |
| 3 | Player exits realm (victory portal) | Realm HUDs gone, XP bar + shield persist | Visual check |
| 4 | Player exits realm (timeout) | Defeat HUD shown, then XP bar + shield in overworld | Visual check |
| 5 | Realm closes with players inside | Players teleported out, all HUDs correct in overworld | Visual check |
| 6 | Player enters skill sanctum | XP bar + shield persist, skill point HUD appears | Visual check |
| 7 | Player exits skill sanctum | Skill HUDs gone, XP bar + shield persist | Visual check |
| 8 | Player disconnects mid-realm | No errors in log, clean state on reconnect | Server log |
| 9 | Player rapidly enters/exits realm | No crashes, no duplicate HUDs, no leaked timers | Stress test |
| 10 | /hud toggle in overworld | HUDs hide/show | Visual check |
| 11 | /hud toggle, enter realm, exit realm | Toggle state preserved, HUDs remain hidden | Visual check |
| 12 | Two players, one enters realm | Other player's HUDs unaffected | Visual check |
| 13 | XP gain during realm | XP bar updates | Kill mob, check bar |
| 14 | Take damage in realm | Shield bar updates, combat HUD timer ticks | Visual check |
| 15 | Plugin reload (/tooadmin reload) | HUDs survive or are recreated | Visual check |

### 8.2 Crash Vector Verification

For each of these, verify no client crash occurs:

- [ ] Enter realm while HUD is refreshing
- [ ] Exit realm while HUD is refreshing
- [ ] Enter sanctum while realm combat HUD is ticking
- [ ] Disconnect mid-world-transition
- [ ] Enter realm → immediate /hud toggle → exit realm
- [ ] Two rapid realm entries (double-portal)
- [ ] Realm close during combat (drain while tick running)

### 8.3 Log Verification

After each transition, server log should show:
```
[HudCoordinator] Discarded N HUDs for player X (draining from world Y)
[HudCoordinator] Restored N HUDs for player X in world Z (readyId=M)
```

No warnings about:
- `getReference() returned null`
- `safeAdd failed`
- `Selected element not found`
- Any NPE in HUD-related code

---

## Appendix A: Vendor Mod Patterns (Summary)

12 mods analyzed. Key findings:
- **0/12 mods listen for DrainPlayerFromWorldEvent**
- **0/12 mods call hide() before remove()**
- **0/12 mods handle the getReference() null window**
- Most rely on ECS systems that naturally stop processing when the player leaves a store
- EndlessLeveling is the only mod that uses `PlayerReadyEvent` to restore HUDs (with a 300ms delay hack)
- BetterMap and MajorDungeons use `EmptyHud` (empty CustomUIHud) instead of `null` to clear HUDs, because `setCustomHud(ref, null)` crashes the client
- The community consensus: HUDs are ephemeral, recreated as needed, no persistence across transitions

## Appendix B: HyUI Source Bugs

1. `remove()` line 154: `this.refreshTask.cancel(false)` runs even when `getStore()` returned null (potential NPE if task never started)
2. `removeUnsafe()`: returns before cancelling refresh task when `getPlayer()` is null → task leak
3. `setVisibilityOnFirstElement()`: `this.isHidden = !this.isHidden` desyncs on double-call
4. `checkRefreshes()`: reads `isHidden`, `refreshRateMs`, elements without synchronization across threads
5. Static `ScheduledExecutorService` instances never shut down
6. `triggerRefresh()` callback runs on scheduler thread, not world thread

## Appendix C: Key Decompiled Server References

| File | What It Shows |
|------|--------------|
| `World.java:820-840` | `onSetupPlayerJoining()` — JoinWorld packet, setQueuePackets |
| `World.java:783-818` | `onFinishPlayerJoining()` — addToStore, packet flush |
| `World.java:843-853` | `drainPlayersTo()` — removeFromStore, DrainPlayerFromWorldEvent |
| `Universe.java:730-760` | `resetPlayer()` — the ONLY caller of resetManagers |
| `Player.java:336-350` | `resetManagers()` — resetHud sends CustomHud(clear=true) |
| `Player.java:269-281` | `handleClientReady()` — dispatches PlayerReadyEvent |
| `GamePacketHandler.java:594-615` | ClientReady handling — dual packet, readyId |
| `PlayerRef.java` | `getReference()` — returns null when entity is null |
| `HudManager.java` | `setCustomHud()` — show() always sends clear=true |
| `CustomUIHud.java` | `update()` — writeNoCache to packet handler |
| `PlayerHudManagerSystems.java` | `InitializeSystem.onEntityAdded()` — sends visible HUD components on addToStore |
