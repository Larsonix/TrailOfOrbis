# Hytale Animation Control

Comprehensive reference for controlling player animations from server plugins — speed modification, animation replacement, timing manipulation, charge mechanics, interaction chains, and packet sync.

> **Related docs:** For item interactions and custom items, see `docs/hytale-api/items.md`. For ECS systems, see `docs/hytale-api/ecs.md`. For events, see `docs/hytale-api/events.md`.
> **Verified against:** Hytale Server `2026.02.19-1a311a592`. Re-verify after Hytale updates by checking decompiled source against the `[CONFIRMED-SOURCE]` annotations.

---

## Quick Reference

| Task | Approach |
|------|----------|
| Play animation on entity | `AnimationUtils.playAnimation(ref, slot, animSetId, animId, accessor)` |
| Stop animation on entity | `AnimationUtils.stopAnimation(ref, slot, accessor)` |
| Speed up melee attacks for one player | Clone `ItemPlayerAnimations` packet → patch `ItemAnimation.speed` → send `UpdateItemPlayerAnimations` |
| Speed up bow charge for one player | Same as melee — patch `ShootCharging` animation speed |
| Restore vanilla animations | Send `Remove` then `AddOrUpdate` with original baseline |
| Cancel an active attack | `interactionManager.cancelChains(chain)` |
| Start a specific attack chain | `interactionManager.tryStartChain(ref, cb, type, ctx, root)` |
| Check if player is attacking | `interactionManager.getChains()` → filter by type and state |
| Read held item's animation set ID | `item.getPlayerAnimationsId()` → returns `"Sword"`, `"Bow"`, etc. |
| Detect bow charge progress | Read `InteractionSyncData.chargeValue`: -1.0 = charging, -2.0 = cancelled, >0 = elapsed seconds |

---

## Required Imports

```java
// Protocol — packets and data structures
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionSyncData;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.ItemAnimation;
import com.hypixel.hytale.protocol.ItemPlayerAnimations;        // protocol packet version
import com.hypixel.hytale.protocol.UpdateType;
import com.hypixel.hytale.protocol.packets.assets.UpdateItemPlayerAnimations;
import com.hypixel.hytale.protocol.packets.entities.PlayAnimation;

// Server — entity and interaction classes
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.entity.InteractionChain;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.InteractionEntry;
import com.hypixel.hytale.server.core.entity.InteractionManager;

// Server — config asset (different class, same name as protocol)
import com.hypixel.hytale.server.core.asset.type.itemanimation.config.ItemPlayerAnimations;  // config version

// Server — interaction config
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;

// Server — ECS components
import com.hypixel.hytale.server.core.modules.entity.component.ActiveAnimationComponent;

// Server — networking
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.entity.entities.Player;

// Server — interaction system (for ECS ordering)
import com.hypixel.hytale.server.core.modules.interaction.system.InteractionSystems;

// Server — time (world-level time dilation)
import com.hypixel.hytale.server.core.modules.time.TimeResource;

// Protocol — additional types
import com.hypixel.hytale.protocol.InteractionCooldown;

// fastutil (bundled in Hytale server)
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
```

> **Disambiguation:** `ItemPlayerAnimations` exists as **two** classes with the same name. The **config** version (`server.core.asset.type.itemanimation.config`) is the server-side asset loaded from JSON. The **protocol** version (`com.hypixel.hytale.protocol`) is the packet payload sent to clients. Use `configVersion.toPacket()` to convert.

---

## Core Concepts

### Animation Slots

Hytale supports 5 simultaneous animation layers, allowing a player to walk while attacking while emoting:

| Slot | Value | Purpose | Priority |
|------|-------|---------|----------|
| `Movement` | 0 | Walk, run, sprint, swim, climb | Lowest |
| `Status` | 1 | Idle, crouch | |
| `Action` | 2 | Attack, use item, interact | |
| `Face` | 3 | Facial expressions (frown, rage, angry) | |
| `Emote` | 4 | Player emotes | Highest |

**[CONFIRMED-SOURCE]** — Verified from `AnimationSlot.java` enum.

### Animation vs Interaction

These are two separate systems that work together:

- **Animation** = visual only. Model keyframes (`.blockyanim` files) played via `AnimationUtils` or triggered by interactions. Controlled by `ItemAnimation.speed` for playback rate.
- **Interaction** = gameplay logic. Damage windows, charging, stamina, hit detection, chaining. Controlled by `InteractionChain.timeShift` and `InteractionSyncData.progress`. Automatically triggers associated animations.

**Key implication:** Speeding up an animation without speeding up the interaction creates visual desync — the animation finishes but the damage hasn't landed yet. For attack speed, you must modify both.

### Three Approaches to Speed Modification

| Approach | Scope | Visual Sync | Complexity | Status |
|----------|-------|-------------|------------|--------|
| **Client packet override** (Tier 2) | Per-player, per-weapon | Client matches server | Medium | **[CONFIRMED]** — primary approach |
| **Server-side time shift** (Tier 1) | Per-chain | Client may desync | High | **[CONFIRMED]** — disabled by default |
| **Hard asset patching** (Tier 3) | All players, global | Perfect (native) | Low | **[CONFIRMED]** — disabled by default |

---

## ItemAnimation Fields

Every animation entry in an `ItemPlayerAnimations` set has these fields, verified from the protocol class.

> **`ItemPlayerAnimations` also contains:** `wiggleWeights` (WiggleWeights — 10 sub-fields for weapon sway/wobble), `camera` (CameraSettings — per-animation camera config), `pullbackConfig` (ItemPullbackConfiguration — first-person arm pullback near obstacles), and `useFirstPersonOverride` (boolean). These are secondary to attack speed but relevant for weapon feel customization.

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `thirdPerson` | String? | null | Path to third-person `.blockyanim` file |
| `thirdPersonMoving` | String? | null | Third-person animation while player is moving |
| `thirdPersonFace` | String? | null | Facial expression animation during this action |
| `firstPerson` | String? | null | Path to first-person `.blockyanim` file |
| `firstPersonOverride` | String? | null | Override first-person animation (used when `useFirstPersonOverride` is true on the parent set) |
| `keepPreviousFirstPersonAnimation` | boolean | false | Don't interrupt the current first-person animation (used for jumps/falls) |
| `speed` | float | 0.0 | **Animation playback speed multiplier.** 1.0 = normal, 2.0 = double speed, 0.5 = half speed. When `0.0` (Java default), the client treats it as "no override" = 1.0x playback. Community implementations normalize `<= 0.0` to `1.0` before applying multipliers. Most vanilla animations set explicit `Speed` values in their JSON. |
| `blendingDuration` | float | 0.2 | Blend time in seconds when transitioning to this animation |
| `looping` | boolean | false | Whether the animation loops (true for idle/walk/guard, false for attacks) |
| `clipsGeometry` | boolean | false | Whether the animation can clip through terrain/blocks |

**[CONFIRMED-SOURCE]** — All 10 fields verified from `com.hypixel.hytale.protocol.ItemAnimation`.

The `speed` field is the primary target for attack speed modification. Patching this in the protocol packet and sending it to a specific player changes their animation playback rate without affecting other players.

---

## Weapon Animation Sets

38 animation set definitions exist at `Assets/Server/Item/Animations/`. Each set defines movement animations (shared across most weapons) plus weapon-specific combat animations.

### Combat Animations by Category

**Melee — Sword** (19 combat anims):

| Animation | Type | Notes |
|-----------|------|-------|
| `SwingLeft`, `SwingRight`, `SwingDown` | Basic combo | Standard 3-hit combo chain |
| `SwingDownStrong` | Heavy attack | Charged variant of SwingDown |
| `Stab` | Thrust | Forward stab |
| `SwingLeftCharging` / `SwingLeftCharged` | Charged | Hold → release pattern |
| `StabDashCharging` / `StabDashCharged` | Charged dash | Lunge forward with stab |
| `SwingLeftSpinJumpCharging` / `SwingLeftSpinJumpCharged` | Aerial spin | Jump spin attack |
| `SpinLeftStabCharging` / `SpinLeftStabCharged` | Spin combo | Spin into stab |
| `SpinRightCharging` / `SpinRightCharged` | Spin | Right spin attack |
| `SpinRightStabDashCharging` / `SpinRightStabDashCharged` | Spin dash | Spin into dash stab |
| `Guard`, `GuardBash` | Defensive | Block and shield bash |

**Melee — Daggers** (38 combat anims — the richest set):

| Animation | Type |
|-----------|------|
| `SwingLeft`, `SwingRight` | Single-hand swings |
| `SwingLeftRight`, `SwingRightLeft` | Dual-wield combo swings |
| `StabLeft`, `StabRight` | Single-hand stabs |
| `StabLeftRight`, `StabRightLeft` | Alternating dual stabs |
| `StabLeftRightHold`, `StabLeftRightHoldCharged` | Held stab combo |
| `StabDoubleCharging`, `StabDoubleSpinCharging`, `StabDoubleCharged` | Charged double stab |
| `LungeDoubleCharging`, `LungeDoubleSpinCharging`, `LungeDoubleCharged` | Charged lunge |
| `FlurryStabDoubleCharging`, `StabLeftRightHoldSpinCharging` | Flurry attacks |
| `PounceStabCharging`, `Pounce`, `PounceLeap`, `PounceStab`, `PounceSweep` | Pounce system |
| `RazorstrikeCharging`, `RazorstrikeSlash`, `RazorstrikeSweep`, `RazorstrikeLunge` | Razorblade combo |
| `KickTornadoRight` | Kick |
| `Backflip` | Evasion |
| `DashForward`, `DashForward2`, `DashBackward`, `DashBackward2`, `DashLeft`, `DashRight` | Dodge dashes (2 forward/backward variants) |
| `Throw` | Thrown attack |
| `Guard`, `GuardBash` | Defensive |

**Ranged — Bow** (14 combat anims):

| Animation | Speed | Type |
|-----------|-------|------|
| `Shoot` | 1.0 | Quick shot (no charge) |
| `ShootCharging` | 0.667 | Charge draw animation (hold to charge) |
| `ShootCharged` | 1.0 | Charged release (BlendingDuration: 0) |
| `SwingRight` | 1.0 | Melee bash with bow |
| `SwingLeft` | 1.0 | Melee bash (uses Shield's Swing_Left animation path) |
| `DashForward`, `DashForward2` | 1.0 | Forward dodge (2 variants, borrowed from Daggers) |
| `DashBackward`, `DashBackward2` | 1.0 | Backward dodge (2 variants, borrowed from Daggers) |
| `DashLeft`, `DashRight` | 1.0 | Side dodges (borrowed from Daggers) |
| `Slide` | 1.0 | Sliding evasion |
| `Guard`, `GuardBash` | — | Defensive |

> **Correction:** The bow uses `Shoot/ShootCharging/ShootCharged`, NOT "Draw/Hold/Shoot". Verified from `Bow.json`. The bow also has a full dodge/dash system — 6 dash animations borrowed from the Daggers animation paths plus a Slide animation.

**Ranged — Crossbow** (9 combat anims, inherits from Handgun):

| Animation | Speed | Type |
|-----------|-------|------|
| `Shoot` | 3.0 | Quick fire |
| `Reload` | 3.34 | Reload cycle (looping) |
| `ReloadCharged` | 2.0 | Charged reload tier 1 |
| `ReloadCharged2` | 1.34 | Charged reload tier 2 |
| `ReloadCharged3` | 1.0 | Charged reload tier 3 |
| `ReloadCharged4` | 0.8 | Charged reload tier 4 |
| `ReloadCharged5` | 0.67 | Charged reload tier 5 (slowest) |
| `ReloadReady` | 1.25 | Reload complete |
| `ShootCharged` | 3.0 | Overcharged shot |

**Magic — Staff** (5 combat anims):

| Animation | Type |
|-----------|------|
| `CastSummonCharging` | Charge-up casting animation |
| `CastSummonCharged` | Release charged cast |
| `CastSummon` | Instant cast |
| `SwingLeft`, `SwingRight` | Melee bash with staff |

**Magic — Wand** (3 combat anims):

| Animation | Type |
|-----------|------|
| `CastLeft` | Quick cast |
| `CastLeftCharging` | Charge-up cast |
| `CastLeftCharged` | Release charged cast |

**Defensive — Shield** (4 combat anims):

| Animation | Type |
|-----------|------|
| `SwingLeft` | Shield bash attack |
| `Guard` | Block (looping) |
| `GuardBash` | Guard counter-attack |
| `GuardHurt` | Taking damage while blocking |

**Tools — Pickaxe** (1 combat anim, inherits movement from parent):

| Animation | Type |
|-----------|------|
| `Mine` | Mining swing |

### All 38 Animation Set IDs

**Melee:** Sword, Longsword, Axe, Battleaxe, Spear, Mace, Club, Club_Flail, Dagger, Daggers, Daggers_Claw, Daggers_Push, Gloves

**Ranged:** Bow, Shortbow, Crossbow, Crossbow_Heavy, Handgun, Rifle, Throwing_Knife

**Magic:** Staff, Wand, Spellbook

**Defensive:** Shield

**Tools:** Pickaxe, Hatchet, Shovel, Hoe, Shears, Sickle

**Utility:** Torch, Fire_Stick, Stick, Watering_Can, Block, Item, Default, Machinima_Camera

### Animation Set Inheritance

14 of the 38 animation sets inherit from a parent set. The child set overrides specific animations while inheriting the rest (movement, idle, etc.) from its parent. **[CONFIRMED-SOURCE]** — Verified from `Assets/Server/Item/Animations/*.json` `Parent` fields.

| Set | Parent |
|-----|--------|
| Axe, Club_Flail, Dagger, Hatchet, Hoe, Pickaxe, Sickle, Wand | Sword |
| Mace, Spear | Battleaxe |
| Crossbow | Handgun |
| Shears | Dagger |
| Daggers_Claw | Daggers_Push |
| Spellbook | Item |

> **Key note:** `toPacket()` returns the **fully resolved** animation set (parent + child merged). Speed patching via Tier 2 operates on these resolved animations — no need to handle inheritance manually. Patching `"Sword"` does NOT propagate to `"Axe"` — each set must be patched individually if needed.

### Querying Animation Sets

```java
// Get animation set config by ID [CONFIRMED]
var configAssetMap = com.hypixel.hytale.server.core.asset.type.itemanimation.config
    .ItemPlayerAnimations.getAssetMap();
var swordConfig = configAssetMap.getAsset("Sword");

// Get all animation entry names for a weapon
var protocolPacket = swordConfig.toPacket();  // config → protocol conversion (cached)
for (Map.Entry<String, ItemAnimation> entry : protocolPacket.animations.entrySet()) {
    LOGGER.atInfo().log("Animation: %s, speed: %.2f, looping: %b",
        entry.getKey(), entry.getValue().speed, entry.getValue().looping);
}

// Get an item's animation set ID
String animSetId = item.getPlayerAnimationsId();  // returns "Sword", "Bow", etc.
```

---

## Playing Animations Directly

### AnimationUtils (Simplest API)

`AnimationUtils` is a server-side utility that sends `PlayAnimation` packets to all players who can see the target entity. **[CONFIRMED-SOURCE]**

```java
// Play a specific animation on an entity
AnimationUtils.playAnimation(
    entityRef,                    // Ref<EntityStore>
    AnimationSlot.Action,         // Which slot to use
    "Sword",                      // ItemPlayerAnimations ID (nullable)
    "SwingRight",                 // Animation name (nullable)
    false,                        // sendToSelf: include the entity itself?
    componentAccessor             // ComponentAccessor<EntityStore>
);

// Convenience overload — sendToSelf defaults to false
AnimationUtils.playAnimation(entityRef, AnimationSlot.Action, "SwingRight", componentAccessor);

// Play with ItemPlayerAnimations config object
AnimationUtils.playAnimation(entityRef, AnimationSlot.Action, swordConfig, "SwingRight", componentAccessor);

// Stop animation on a slot
AnimationUtils.stopAnimation(entityRef, AnimationSlot.Action, componentAccessor);
```

**Behavior notes:**
- For non-Action slots, validates that the animation exists in the entity's Model before sending
- For `AnimationSlot.Action`, skips validation (always sends)
- Missing animations are logged at WARNING level, rate-limited to 1/minute
- `stopAnimation()` sends a `PlayAnimation` packet with null animation ID
- `playAnimation` uses `writeNoCache()` (immediate send); `stopAnimation` uses `write()` (may coalesce)

**`sendToSelf` parameter:** All overloads have a `sendToSelf` variant (defaults to `false` when omitted). When `false`, the owning player is excluded from the broadcast — they don't see the animation on their own entity. Set `true` when playing animations on player entities that the player should see in third-person. **[CONFIRMED-SOURCE]**

```java
// Play animation visible to the player themselves (e.g., NPC performing emote = false, player death = true)
AnimationUtils.playAnimation(playerRef, AnimationSlot.Action, "Sword", "SwingRight", true, accessor);

// Stop with sendToSelf
AnimationUtils.stopAnimation(playerRef, AnimationSlot.Action, true, accessor);
```

### PlayAnimation Packet (Direct)

For more control, construct the packet directly: **[CONFIRMED-SOURCE]**

```java
PlayAnimation packet = new PlayAnimation(
    networkId.getId(),   // entity network ID (int)
    "Sword",             // itemAnimationsId (nullable)
    "SwingRight",        // animationId (nullable)
    AnimationSlot.Action // animation slot
);
player.getPlayerConnection().write(packet);
```

---

## Interaction System

Interactions are the gameplay layer that triggers animations, processes damage, handles charging, and manages combo chains. Understanding this is essential for attack speed modification.

### InteractionManager

An ECS component on every entity with interactions. **[CONFIRMED]**

```java
// Access from ECS chunk
InteractionManager manager = chunk.getComponent(index, interactionManagerComponentType);

// Get all active chains
var chains = manager.getChains();  // Int2ObjectMap<InteractionChain> (keyed by chain ID)

// Cancel a specific chain
manager.cancelChains(chain);

// Start a new chain [CONFIRMED]
RootInteraction root = RootInteraction.getRootInteractionOrUnknown("Root_Weapon_Sword_Primary");
InteractionContext ctx = InteractionContext.forInteraction(manager, entityRef, InteractionType.Primary, accessor);
manager.tryStartChain(entityRef, commandBuffer, InteractionType.Primary, ctx, root);

// Set global time shift for an interaction type
manager.setGlobalTimeShift(InteractionType.Primary, 0.5f);

// Read current time shift
float shift = manager.getGlobalTimeShift(InteractionType.Primary);
```

**Additional API:** **[CONFIRMED-SOURCE]**

| Return | Method | Description |
|--------|--------|-------------|
| `boolean` | `canRun(InteractionType, RootInteraction)` | Pre-check if a chain can start (rules + cooldown) without starting it. Delegates to the overload below with `equipSlot = -1` |
| `boolean` | `canRun(InteractionType, short equipSlot, RootInteraction)` | Same pre-check with explicit equip slot (`-1` = any slot) |
| `void` | `startChain(Ref, CommandBuffer, InteractionType, InteractionContext, RootInteraction)` | Start chain **unconditionally** — no rule/cooldown check (unlike `tryStartChain`) |
| `InteractionChain` | `initChain(InteractionType, InteractionContext, RootInteraction, boolean)` | Create chain without executing. Configure it, then call `executeChain()` |
| `void` | `executeChain(Ref, CommandBuffer, InteractionChain)` | Execute a previously initialized chain |
| `void` | `queueExecuteChain(InteractionChain)` | Defer chain execution to next tick |
| `void` | `clear()` | Cancel ALL active chains and clear start queue (teleport/death cleanup) |
| `void` | `tryRunHeldInteraction(Ref, CommandBuffer, InteractionType)` | Trigger held item's interaction (resolves root interaction automatically) |
| `<T> T` | `forEachInteraction(TriFunction<InteractionChain, Interaction, T, T>, T)` | Fold over all active chains and their current `Interaction` objects |

> **Constants:** `MAX_REACH_DISTANCE = 8.0` (maximum interaction reach distance).
>
> **Return type of `getChains()`:** `Int2ObjectMap<InteractionChain>` (fastutil), keyed by chain ID. Iterate with `.values()`:
> ```java
> for (InteractionChain chain : manager.getChains().values()) { ... }
> ```
>
> **`tryStartChain` vs `startChain`:** `tryStartChain` checks interrupt/block rules and cooldowns — returns `false` if blocked. `startChain` skips all checks. Use `initChain` + `executeChain` when you need to configure the chain between creation and execution (e.g., set `onCompletion` callback).

### InteractionChain

Represents one active interaction sequence (e.g., a sword combo). **[CONFIRMED]**

```java
InteractionChain chain = chains.get(0);

// Timing control
float timeShift = chain.getTimeShift();    // Current timing offset
chain.setTimeShift(1.5f);                  // Set new offset

// State inspection
InteractionState state = chain.getServerState();  // Finished, NotFinished, Failed, etc.
InteractionType type = chain.getType();            // Primary, Secondary, etc.
int chainId = chain.getChainId();                  // Unique identifier
RootInteraction root = chain.getRootInteraction(); // Which root interaction

// Force client resync after server-side modifications
chain.flagDesync();
```

**Additional API:** **[CONFIRMED-SOURCE]**

| Return | Method | Description |
|--------|--------|-------------|
| `float` | `getTimeInSeconds()` | How long the chain has been running (seconds) |
| `void` | `setServerState(InteractionState)` | Force chain to a specific state (e.g., `Failed`) |
| `InteractionContext` | `getContext()` | Access the chain's `InteractionContext` |
| `void` | `setOnCompletion(Runnable)` | Callback when chain finishes (cleanup, UI updates) |
| `Long2ObjectMap<InteractionChain>` | `getForkedChains()` | All concurrent forked sub-chains |
| `int` | `getOperationCounter()` | Position within the active `RootInteraction`'s operation list. Resets when entering nested root interactions |
| `int` | `getOperationIndex()` | Monotonically increasing index into the chain's `InteractionEntry` list. Used as key for `getInteraction(index)` |
| `long` | `getTimestamp()` / `setTimestamp(long)` | Raw nanosecond timestamp |
| `InteractionEntry` | `getOrCreateInteractionEntry(int)` | Create entry if not exists (vs `getInteraction` which returns null) |
| `boolean` | `isDesynced()` | Read desync flag (without consuming it) |

### InteractionEntry

A single step within a chain. **[CONFIRMED]**

```java
InteractionEntry entry = chain.getInteraction(operationIndex);

// Timestamp manipulation — shift backward to make attack appear faster
entry.setTimestamp(timestamp, extraSeconds);  // shifts by extraSeconds * 1e9 nanoseconds

// Store time shift in meta for Hytale's internal use
entry.getMetaStore().putMetaObject(Interaction.TIME_SHIFT, targetShift);
```

**Additional API:** **[CONFIRMED-SOURCE]**

| Return | Method | Description |
|--------|--------|-------------|
| `float` | `getTimeInSeconds(long nanoTime)` | Elapsed seconds since entry started (pass current nano time) |
| `long` | `getTimestamp()` | Raw nanosecond timestamp |
| `InteractionSyncData` | `getServerState()` | Always server-authoritative state (vs `getState()` which may return simulation state) |
| `InteractionSyncData` | `getClientState()` | Client-reported state (nullable — null before first client sync) |
| `InteractionSyncData` | `getSimulationState()` | Simulation state (lazily created from server state) |
| `void` | `flagDesync()` | Force client resync (also available on `InteractionChain`) |
| `int` | `getIndex()` | Interaction slot index within the chain |

### InteractionContext

Execution context for creating and managing chains. **[CONFIRMED]**

```java
InteractionContext ctx = InteractionContext.forInteraction(manager, entityRef, type, accessor);

// Clone context for forking
InteractionContext forked = ctx.duplicate();

// Set time shift — propagates to chain AND global manager
ctx.setTimeShift(0.5f);

// Get held item info
var heldItem = ctx.getHeldItem();
```

> **Important:** `setTimeShift()` on `InteractionContext` propagates to both the chain AND calls `interactionManager.setGlobalTimeShift()`. Use `chain.setTimeShift()` directly if you only want to affect one chain.

**Additional API:** **[CONFIRMED-SOURCE]**

```java
// Entity references (can differ for proxy interactions)
Ref<EntityStore> runner = ctx.getEntity();         // Entity the interaction runs FOR
Ref<EntityStore> owner = ctx.getOwningEntity();    // Entity that OWNS the interaction
// For most cases these are the same. They differ with forProxyEntity().

// Fork a concurrent interaction (e.g., shield bash while blocking)
InteractionChain fork = ctx.fork(forkedCtx, rootInteraction, requiresClient);
// Overload with explicit type:
InteractionChain fork = ctx.fork(InteractionType.Secondary, forkedCtx, root, false);

// Push a new root interaction mid-chain
ctx.execute(nextRootInteraction);

// Read interaction targets from meta store
Ref<EntityStore> target = ctx.getTargetEntity();   // nullable
BlockPosition block = ctx.getTargetBlock();         // nullable

// Resolve which root interaction an entity would use for a type
String rootId = ctx.getRootInteractionId(InteractionType.Primary);

// Access ECS command buffer
CommandBuffer<EntityStore> cb = ctx.getCommandBuffer();
```

> **Key distinction:** `getEntity()` returns `runningForEntity`, NOT `owningEntity`. For proxy interactions (one entity executing on behalf of another), these differ. Most code should use `getEntity()`.

---

## InteractionSyncData

Protocol data synced between server and client for each interaction. All 25 fields listed below. **[CONFIRMED-SOURCE]**

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `state` | InteractionState | Finished | Current state: Finished, Skip, ItemChanged, Failed, NotFinished |
| `progress` | float | 0 | **Elapsed time in seconds** (NOT 0-1 normalized). How long the current interaction has been running. |
| `operationCounter` | int | 0 | Current operation index within the chain |
| `rootInteraction` | int | 0 | Root interaction asset ID |
| `totalForks` | int | 0 | Number of forked sub-chains spawned by this interaction |
| `entityId` | int | 0 | Target entity network ID for this interaction |
| `enteredRootInteraction` | int | MIN_VALUE | Which root interaction was entered (MIN_VALUE = none) |
| `blockPosition` | BlockPosition? | null | Block being interacted with (mining, placing) |
| `blockFace` | BlockFace | None | Which face of the target block |
| `blockRotation` | BlockRotation? | null | Block rotation context for placement |
| `placedBlockId` | int | MIN_VALUE | Block type placed (MIN_VALUE = none) |
| `chargeValue` | float | -1.0 | **Charge progress** (see below) |
| `forkCounts` | Map\<InteractionType, Integer\>? | null | Active fork counts per interaction type |
| `chainingIndex` | int | -1 | Current chaining position within a combo (-1 = not chaining) |
| `flagIndex` | int | -1 | Flag/label index for operation jumps (-1 = none) |
| `hitEntities` | SelectedHitEntity[]? | null | Entities hit during this interaction |
| `attackerPos` | Position? | null | Attacker position at time of interaction |
| `attackerRot` | Direction? | null | Attacker rotation at time of interaction |
| `raycastHit` | Position? | null | World position where the hit raycast landed |
| `raycastDistance` | float | 0.0 | Distance the raycast traveled before hitting |
| `raycastNormal` | Vector3f? | null | Surface normal at the raycast hit point |
| `movementDirection` | MovementDirection | None | Player movement during interaction |
| `applyForceState` | ApplyForceState | Waiting | Force application state (knockback/launch) |
| `nextLabel` | int | 0 | Next operation label to jump to |
| `generatedUUID` | UUID? | null | Unique identifier for this interaction instance |

### chargeValue Semantics

The `chargeValue` field is the **native charge progress mechanism** for bow draw, crossbow reload, and any `ChargingInteraction`. **[CONFIRMED-SOURCE]** from reading `ChargingInteraction.java`:

| Value | Meaning |
|-------|---------|
| `-1.0` | **Still charging** (button held down). The `ChargingInteraction` returns `NotFinished`. |
| `-2.0` | **Cancelled** (e.g., interrupted by damage if `failOnDamage` is true). The interaction returns `Finished` without triggering follow-ups. |
| `> 0` | **Released after N seconds**. The value is elapsed charge time in seconds. Used to select which follow-up interaction to run based on charge thresholds. |

**How bow charge works internally:**
1. Player holds Primary → `ChargingInteraction.tick0()` checks `chargeValue`
2. While `-1.0` → state = NotFinished (keep waiting)
3. Player releases → client sets `chargeValue` to elapsed seconds
4. `ChargingInteraction.jumpToChargeValue()` picks the correct follow-up from `next` thresholds
5. Example: charge thresholds `{0.5: "Shoot", 1.5: "ShootCharged"}` → short hold = weak shot, long hold = charged shot

**No community mod uses this field.** It was discovered by reading the `ChargingInteraction` source directly. Potential use: reading charge progress in real-time for UI or modifying charge speed.

### progress Field Clarification

`InteractionSyncData.progress` is **elapsed time in seconds**, NOT a 0-1 normalized value. Can be modified directly:

```java
// Advance interaction progress (make it think more time has passed)
data.progress = Math.max(0.0F, Math.min(0.9995F, progress + extraProgress));
chain.flagDesync();  // Force client resync
```

**[CONFIRMED]** — Verified in community mod implementations, though typically disabled by default.

---

## Attack Speed Modification

### Tier 2: Client Packet Override (Recommended)

The primary approach. Clone the animation assets, patch `ItemAnimation.speed` on attack animations, and send the modified packet to a specific player. **[CONFIRMED]**

```java
// 1. Load original animation config
var configAssetMap = com.hypixel.hytale.server.core.asset.type.itemanimation.config
    .ItemPlayerAnimations.getAssetMap();
var config = configAssetMap.getAsset("Sword");  // Config version

// 2. Convert to protocol packet and clone (deep copy)
//    CRITICAL: toPacket() caches its result via SoftReference. Without clone(),
//    you'd mutate the cached packet, silently corrupting all future toPacket() calls
//    for this animation set until GC clears the soft reference.
com.hypixel.hytale.protocol.ItemPlayerAnimations packet = config.toPacket();
com.hypixel.hytale.protocol.ItemPlayerAnimations clone = packet.clone();

// 3. Patch attack animation speeds
float speedMultiplier = 1.5f;  // 50% faster
for (Map.Entry<String, ItemAnimation> entry : clone.animations.entrySet()) {
    String name = entry.getKey().toLowerCase();
    // Only patch attack animations, not movement/idle
    if (name.contains("swing") || name.contains("stab") || name.contains("slash")
        || name.contains("spin") || name.contains("strike") || name.contains("bash")
        || name.contains("shoot") || name.contains("cast") || name.contains("attack")
        || name.contains("combo") || name.contains("charged") || name.contains("charging")) {
        ItemAnimation anim = entry.getValue();
        float original = anim.speed > 0 ? anim.speed : 1.0f;
        anim.speed = Math.max(0.5f, Math.min(original * speedMultiplier, 3.0f));
    }
}

// 4. Send modified animations to ONE player
PacketHandler connection = player.getPlayerConnection();
connection.write(new UpdateItemPlayerAnimations(UpdateType.AddOrUpdate, Map.of("Sword", clone)));
```

### UpdateType Reference

The `UpdateItemPlayerAnimations` packet uses `UpdateType` to control how the client applies the payload:

| Value | Name | When Used |
|-------|------|-----------|
| 0 | `Init` | Sent automatically on world join with ALL animation sets (the baseline) |
| 1 | `AddOrUpdate` | Add new or update existing animation sets for a player |
| 2 | `Remove` | Remove animation set overrides, reverting to `Init` baseline |

For dynamic per-player speed modification, use `AddOrUpdate` (apply) and `Remove` + `AddOrUpdate` (restore). `Init` is sent by Hytale's `ItemPlayerAnimationsPacketGenerator` during world join — you never send it manually.

> **Compression:** `UpdateItemPlayerAnimations` has `IS_COMPRESSED = true` (packet ID 52). Large animation payloads are compressed automatically by Hytale's protocol layer. **[CONFIRMED-SOURCE]** — `UpdateItemPlayerAnimations.java:22`.

### Restoring Vanilla Animations

Always restore when the override ends (item swap, teleport, buff expiry). **[CONFIRMED]**

```java
// CRITICAL: Send Remove THEN AddOrUpdate for idempotent restoration
var baseline = config.toPacket().clone();
connection.write(new UpdateItemPlayerAnimations(UpdateType.Remove, Map.of("Sword", baseline)));
connection.write(new UpdateItemPlayerAnimations(UpdateType.AddOrUpdate, Map.of("Sword", baseline)));
```

### Failsafe Restore Pattern

Community implementations use overlapping restore mechanisms because a single `Remove + AddOrUpdate` can fail if the player changes worlds: **[CONFIRMED]**

1. **Burst restore** — When override ends, send 10 restore pairs (120ms apart)
2. **Periodic baseline** — Every 2500ms within 3500ms of last override, resend baseline
3. **Selection change** — On item swap (350ms cooldown), restore previous item's animations

```java
// Burst restore pattern (run on tick, not blocking)
private int burstRemaining = 10;
private long lastBurstMs = 0;

void tickBurstRestore(PacketHandler connection, Map<String, ItemPlayerAnimations> baseline) {
    if (burstRemaining > 0 && System.currentTimeMillis() - lastBurstMs >= 120) {
        connection.write(new UpdateItemPlayerAnimations(UpdateType.Remove, baseline));
        connection.write(new UpdateItemPlayerAnimations(UpdateType.AddOrUpdate, baseline));
        burstRemaining--;
        lastBurstMs = System.currentTimeMillis();
    }
}
```

### Tier 1: Server-Side Time Shift (Advanced)

Modify interaction timing directly on the server. Faster but causes visual desync unless combined with Tier 2. **[CONFIRMED]** — disabled by default in community implementations.

```java
// Speed up an active chain
InteractionChain chain = getActiveAttackChain(interactionManager);
if (chain != null) {
    float targetShift = 0.5f;  // Shift timing forward

    // 1. Set chain-wide time shift
    chain.setTimeShift(targetShift);

    // 2. Shift entry timestamp backward (makes it appear to have started earlier)
    InteractionEntry entry = chain.getInteraction(chain.getOperationIndex());
    entry.setTimestamp(timestamp, extraSeconds);

    // 3. Store in meta for Hytale's internal systems
    entry.getMetaStore().putMetaObject(Interaction.TIME_SHIFT, targetShift);

    // 4. Force resync to client
    chain.flagDesync();
}
```

### Tier 3: Global Asset Patching

Patch animation JSON files on disk and hot-reload. Affects all players globally. **[CONFIRMED]** — disabled by default.

```java
// Hot-reload patched animation files
var assetStore = com.hypixel.hytale.server.core.asset.type.itemanimation.config
    .ItemPlayerAnimations.getAssetStore();
assetStore.loadAssetsFromPaths("custom override", List.of(patchedJsonPath));

// Restore originals on shutdown
assetStore.loadAssetsFromPaths("restore", List.of(originalJsonPath));
```

### Attack Animation Detection

Pattern for identifying which animations to speed up (as opposed to idle/walk/swim that should stay normal): **[CONFIRMED]**

```java
boolean isAttackAnimation(String name, ItemAnimation anim) {
    String lower = name.toLowerCase();
    // Skip non-combat animations
    if (lower.contains("idle") || lower.contains("walk") || lower.contains("run")
        || lower.contains("sprint") || lower.contains("jump") || lower.contains("fall")
        || lower.contains("swim") || lower.contains("equip") || lower.contains("hold")
        || lower.contains("block") || lower.contains("climb") || lower.contains("fly")
        || lower.contains("fluid") || lower.contains("interact") || lower.contains("mantle")
        || lower.contains("slide") || lower.contains("crouch")) {
        return false;
    }
    // Match combat animations by name
    if (lower.contains("swing") || lower.contains("stab") || lower.contains("slash")
        || lower.contains("spin") || lower.contains("strike") || lower.contains("bash")
        || lower.contains("charged") || lower.contains("charging") || lower.contains("attack")
        || lower.contains("combo") || lower.contains("shoot") || lower.contains("cast")
        || lower.contains("lunge") || lower.contains("pounce") || lower.contains("flurry")
        || lower.contains("razor") || lower.contains("kick") || lower.contains("throw")
        || lower.contains("guard") || lower.contains("mine") || lower.contains("reload")
        || lower.contains("dash") || lower.contains("backflip")) {
        return true;
    }
    // Match by animation path
    String path = anim.thirdPerson != null ? anim.thirdPerson.toLowerCase() : "";
    return path.contains("/attacks/") || path.contains("/attack/");
}
```

---

## Charge Rate & Ranged Weapons

### ChargingInteraction Mechanics

`ChargingInteraction` is the server-side interaction type for bow draw, crossbow reload, and any hold-to-charge weapon. **[CONFIRMED-SOURCE]** — read from decompiled `ChargingInteraction.java`.

**Key fields:**
- `allowIndefiniteHold` (boolean, default false) — Can the player hold forever, or auto-complete at `highestChargeValue`?
- `displayProgress` (boolean, default true) — Show charge bar on client HUD
- `failOnDamage` (boolean, default false) — Cancel charge if hit (sets chargeValue to -2.0)
- `cancelOnOtherClick` (boolean, default true) — Cancel if other input pressed
- `mouseSensitivityAdjustmentTarget` (float, default 1.0, range [0.0–1.0]) — Mouse sensitivity during charge (0.5 = zoomed in)
- `mouseSensitivityAdjustmentDuration` (float, default 1.0) — Seconds to interpolate from 1.0 to target sensitivity
- `next` (`Float2ObjectMap<String>`) — Charge-time thresholds → follow-up interaction IDs
- `failed` (String, nullable) — Interaction to run on failure (e.g., damage-interrupted). Compiled as the label after all `next` branches.
- `forks` (`Map<InteractionType, String>`) — Parallel interactions during charge (e.g., shield bash while blocking). Fork runs concurrently — does NOT cancel the current interaction.
- `chargingDelay` (`ChargingDelay`, nullable) — Damage delays charge progress. Sub-fields:

| ChargingDelay Field | Type | Default | Description |
|---------------------|------|---------|-------------|
| `minDelay` | float | 0.0 | Smallest delay (seconds) at `minHealth` threshold |
| `maxDelay` | float | 0.0 | Largest delay (seconds) at `maxHealth` threshold |
| `maxTotalDelay` | float | 0.0 | Cumulative cap before further delay stops applying |
| `minHealth` | float | 0.0 | Health % below which delay is not applied |
| `maxHealth` | float | 0.0 | Health % above which delay is capped at maxDelay |

**Computed fields (derived after deserialization):**
- `highestChargeValue` (float) — Largest key in `next` map. Used as natural charge cap when `allowIndefiniteHold` is false.
- `sortedKeys` (float[]) — Sorted keys from `next` for threshold matching.

**Inherited from `Interaction` (affects behavior during charge):**
- `horizontalSpeedMultiplier` (float, default 1.0) — Movement speed during charge
- `cancelOnItemChange` (boolean, default true) — Cancel if held item changes mid-charge
- `runTime` (float, default 0.0) — Max runtime in seconds
- `viewDistance` (double, default 96.0) — Distance at which other players see charge effects
- `effects` (InteractionEffects) — Visual/audio effects during charge
- `rules` (InteractionRules) — Conditions for when the charge interaction can run

**Charge resolution flow:**
1. Server calls `tick0()` every tick during charge
2. Reads `clientData.chargeValue` from `InteractionSyncData`
3. If `-1.0` → still charging → return NotFinished
4. If `-2.0` → cancelled → return Finished (no follow-up)
5. If `> 0` → `jumpToChargeValue(chargeValue)` picks closest threshold from `next` map and jumps to that follow-up interaction

### Modifying Charge Rate

**Approach 1: Animation speed** — Speed up the `ShootCharging` animation via Tier 2 packet override. This makes the visual charge animation play faster. **[CONFIRMED]** for melee; **[THEORETICAL]** for ranged.

```java
// Speed up bow charge animation
var bowConfig = configAssetMap.getAsset("Bow");
var clone = bowConfig.toPacket().clone();
ItemAnimation charging = clone.animations.get("ShootCharging");
if (charging != null) {
    charging.speed = charging.speed * 1.5f;  // 50% faster charge visual
}
connection.write(new UpdateItemPlayerAnimations(UpdateType.AddOrUpdate, Map.of("Bow", clone)));
```

> **Note:** This only speeds up the visual animation. The actual charge time is controlled by the client's input hold duration (`chargeValue` = elapsed seconds). To actually make the charge faster (reach the threshold sooner), you'd need to modify the interaction's `next` thresholds, which requires asset patching (Tier 3).

**Approach 2: Threshold modification** — **[THEORETICAL]** — Patching the interaction JSON's `Next` thresholds to lower values would mean less hold time for a charged shot. Requires Tier 3 hot-reload via `RootInteraction.getAssetStore().loadAssetsFromPaths()` (if such a method exists — unverified).

---

## InteractionType Reference

Full enum with all 25 values. **[CONFIRMED-SOURCE]**

| Value | Name | Typical Use |
|-------|------|-------------|
| 0 | `Primary` | Left-click attack, primary action |
| 1 | `Secondary` | Right-click guard/block, secondary action |
| 2 | `Ability1` | First ability hotkey |
| 3 | `Ability2` | Second ability hotkey |
| 4 | `Ability3` | Third ability hotkey |
| 5 | `Use` | Generic use action |
| 6 | `Pick` | Pick up item from world |
| 7 | `Pickup` | Auto-pickup trigger |
| 8 | `CollisionEnter` | Entity collision start |
| 9 | `CollisionLeave` | Entity collision end |
| 10 | `Collision` | Ongoing collision |
| 11 | `EntityStatEffect` | Stat effect application |
| 12 | `SwapTo` | Swapping to this item |
| 13 | `SwapFrom` | Swapping away from this item |
| 14 | `Death` | Entity death interaction |
| 15 | `Wielding` | Passive wielding animation |
| 16 | `ProjectileSpawn` | Projectile created |
| 17 | `ProjectileHit` | Projectile hit entity/block |
| 18 | `ProjectileMiss` | Projectile missed |
| 19 | `ProjectileBounce` | Projectile bounced |
| 20 | `Held` | Item held in main hand (passive) |
| 21 | `HeldOffhand` | Item held in off-hand (passive) |
| 22 | `Equipped` | Item equipped in armor slot (passive) |
| 23 | `Dodge` | Dodge/evade action |
| 24 | `GameModeSwap` | Game mode change |

---

## InteractionCooldown Reference

Root interactions can have cooldowns that prevent re-use for a duration after activation. **[CONFIRMED-SOURCE]**

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `id` | String | (required) | Cooldown group ID — interactions sharing an ID share a cooldown |
| `cooldown` | float | 0.0 | Base cooldown duration in seconds (must be >= 0) |
| `chargeTimes` | float[] | null | Charge times for multi-charge abilities (e.g., `[0.5, 1.0]` = 2 charges at different recharge rates) |
| `skipCooldownReset` | boolean | false | Don't reset cooldown timer when chain starts |
| `interruptRecharge` | boolean | false | Interrupted attack refunds some cooldown |
| `clickBypass` | boolean | false | Click queuing bypasses cooldown check |

```java
// Read cooldown config from a root interaction
RootInteraction root = RootInteraction.getRootInteractionOrUnknown("Root_Weapon_Sword_Primary");
InteractionCooldown cooldown = root.getCooldown();  // nullable — null means no cooldown
if (cooldown != null) {
    LOGGER.atInfo().log("Cooldown: %.2fs, charges: %s", cooldown.cooldown,
        cooldown.chargeTimes != null ? Arrays.toString(cooldown.chargeTimes) : "none");
}
```

---

## ECS System Ordering for Attack Speed

To modify interactions mid-tick (e.g., time shift, chain cancellation), register your ECS system to run **after** the vanilla interaction tick system. **[CONFIRMED]**

```java
import com.hypixel.hytale.server.core.modules.interaction.system.InteractionSystems;

// In your plugin's ECS system registration:
@SystemDependency(order = SystemDependency.Order.AFTER,
                  value = InteractionSystems.TickInteractionManagerSystem.class)
public class AttackSpeedSystem extends EntityTickingSystem<EntityStore> {
    @Override
    public void tick(ArchetypeChunk<EntityStore> chunk, int index,
                     Ref<EntityStore> ref, CommandBuffer<EntityStore> cb, float dt) {
        InteractionManager manager = chunk.getComponent(index, interactionManagerType);
        // Modify chains here — they've already been ticked by vanilla
    }
}
```

`InteractionSystems.TickInteractionManagerSystem` is the vanilla system that calls `InteractionManager.tick()` each frame. Running AFTER it means all chains have advanced for this tick — you can inspect their state, modify timing, or cancel them.

---

## Recipes

### Recipe 1: Speed Up Melee Attacks for One Player

**[CONFIRMED]** — Core mechanism verified in community mod implementations.

```java
import com.hypixel.hytale.protocol.ItemAnimation;
import com.hypixel.hytale.protocol.ItemPlayerAnimations;
import com.hypixel.hytale.protocol.UpdateType;
import com.hypixel.hytale.protocol.packets.assets.UpdateItemPlayerAnimations;
import com.hypixel.hytale.server.core.io.PacketHandler;

void speedUpMelee(Player player, String animSetId, float speedMultiplier) {
    var config = com.hypixel.hytale.server.core.asset.type.itemanimation.config
        .ItemPlayerAnimations.getAssetMap().getAsset(animSetId);
    if (config == null) return;

    ItemPlayerAnimations clone = config.toPacket().clone();
    for (var entry : clone.animations.entrySet()) {
        if (isAttackAnimation(entry.getKey(), entry.getValue())) {
            ItemAnimation anim = entry.getValue();
            anim.speed = Math.max(0.5f, (anim.speed > 0 ? anim.speed : 1.0f) * speedMultiplier);
        }
    }

    player.getPlayerConnection().write(
        new UpdateItemPlayerAnimations(UpdateType.AddOrUpdate, Map.of(animSetId, clone))
    );
}
```

### Recipe 2: Speed Up Bow Draw for One Player

**[THEORETICAL]** — Uses same mechanism as Recipe 1, targeting bow animations.

```java
void speedUpBowDraw(Player player, float speedMultiplier) {
    var config = com.hypixel.hytale.server.core.asset.type.itemanimation.config
        .ItemPlayerAnimations.getAssetMap().getAsset("Bow");
    if (config == null) return;

    ItemPlayerAnimations clone = config.toPacket().clone();
    // Target charge animation specifically
    ItemAnimation charging = clone.animations.get("ShootCharging");
    if (charging != null) {
        charging.speed = Math.max(0.3f, (charging.speed > 0 ? charging.speed : 0.667f) * speedMultiplier);
    }

    player.getPlayerConnection().write(
        new UpdateItemPlayerAnimations(UpdateType.AddOrUpdate, Map.of("Bow", clone))
    );
}
```

### Recipe 3: Restore All Animations to Vanilla

**[CONFIRMED]** — Defensive restore pattern.

```java
void restoreAnimations(Player player, List<String> animSetIds) {
    var configMap = com.hypixel.hytale.server.core.asset.type.itemanimation.config
        .ItemPlayerAnimations.getAssetMap();
    PacketHandler conn = player.getPlayerConnection();

    Map<String, ItemPlayerAnimations> baseline = new HashMap<>();
    for (String id : animSetIds) {
        var config = configMap.getAsset(id);
        if (config != null) {
            baseline.put(id, config.toPacket().clone());
        }
    }

    if (!baseline.isEmpty()) {
        conn.write(new UpdateItemPlayerAnimations(UpdateType.Remove, baseline));
        conn.write(new UpdateItemPlayerAnimations(UpdateType.AddOrUpdate, baseline));
    }
}
```

### Recipe 4: Cancel an In-Progress Attack Chain

**[CONFIRMED]** — Verified in community mod implementations.

```java
void cancelAttack(InteractionManager manager) {
    for (InteractionChain chain : manager.getChains().values()) {
        if (chain.getType() == InteractionType.Primary
            && chain.getServerState() == InteractionState.NotFinished) {
            manager.cancelChains(chain);
            break;
        }
    }
}
```

### Recipe 5: Start a Specific Attack Chain Programmatically

**[CONFIRMED]** — Verified in community mod implementations.

```java
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;

void startAttackChain(InteractionManager manager, Ref<EntityStore> ref,
                      CommandBuffer<EntityStore> cb, String rootInteractionId) {
    RootInteraction root = RootInteraction.getRootInteractionOrUnknown(rootInteractionId);
    InteractionContext ctx = InteractionContext.forInteraction(
        manager, ref, InteractionType.Primary, /* accessor */);
    manager.tryStartChain(ref, cb, InteractionType.Primary, ctx, root);
}
```

### Recipe 6: Read What Animation a Held Item Uses

**[CONFIRMED-SOURCE]**

```java
import com.hypixel.hytale.server.core.asset.type.item.config.Item;

void logItemAnimations(String itemId) {
    Item item = Item.getAssetMap().getAsset(itemId);
    if (item == null || item == Item.UNKNOWN) return;

    String animSetId = item.getPlayerAnimationsId();
    LOGGER.atInfo().log("Item %s uses animation set: %s", itemId, animSetId);

    var config = com.hypixel.hytale.server.core.asset.type.itemanimation.config
        .ItemPlayerAnimations.getAssetMap().getAsset(animSetId);
    if (config != null) {
        for (String animName : config.toPacket().animations.keySet()) {
            LOGGER.atInfo().log("  Animation: %s", animName);
        }
    }
}
```

### Recipe 7: Check If a Player Is Currently Attacking

**[CONFIRMED]** — Verified in community mod implementations.

```java
boolean isPlayerAttacking(InteractionManager manager) {
    for (InteractionChain chain : manager.getChains().values()) {
        InteractionType type = chain.getType();
        if ((type == InteractionType.Primary || type == InteractionType.Secondary)
            && chain.getServerState() == InteractionState.NotFinished) {
            return true;
        }
    }
    return false;
}
```

### Recipe 8: Apply Temporary Speed Buff with TTL

**[THEORETICAL]** — Combines Tier 2 with a scheduled restore task.

```java
void applyTemporarySpeedBuff(Player player, String animSetId, float multiplier, long durationMs) {
    speedUpMelee(player, animSetId, multiplier);

    UUID playerId = /* get player UUID */;
    world.getScheduler().scheduleDelayed(() -> {
        // Burst restore: send 10 restore pairs
        restoreAnimations(player, List.of(animSetId));
    }, durationMs, TimeUnit.MILLISECONDS);
}
```

### Recipe 9: Detect Bow Charge Progress

**[THEORETICAL]** — Based on ChargingInteraction source analysis.

```java
void checkChargeState(InteractionManager manager) {
    for (InteractionChain chain : manager.getChains().values()) {
        if (chain.getType() == InteractionType.Primary
            && chain.getServerState() == InteractionState.NotFinished) {
            InteractionEntry entry = chain.getInteraction(chain.getOperationIndex());
            InteractionSyncData data = entry.getState();

            if (data.chargeValue == -1.0f) {
                LOGGER.atInfo().log("Player is charging (holding)");
            } else if (data.chargeValue == -2.0f) {
                LOGGER.atInfo().log("Charge was cancelled");
            } else if (data.chargeValue > 0) {
                LOGGER.atInfo().log("Released after %.2f seconds", data.chargeValue);
            }
        }
    }
}
```

### Recipe 10: Play a Custom Animation on an Entity

**[CONFIRMED-SOURCE]** — Using `AnimationUtils`.

```java
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.protocol.AnimationSlot;

// Play a death animation on an NPC
AnimationUtils.playAnimation(
    npcRef,                       // entity ref
    AnimationSlot.Action,         // action slot (highest priority for combat)
    "Sword",                      // use sword animation set
    "SwingDown",                  // specific animation
    true,                         // sendToSelf (NPC sees its own animation)
    componentAccessor
);

// Play an emote
AnimationUtils.playAnimation(npcRef, AnimationSlot.Emote, "some_emote_id", componentAccessor);

// Stop all action animations on entity
AnimationUtils.stopAnimation(npcRef, AnimationSlot.Action, componentAccessor);
```

---

## Confirmed vs Theoretical

| API | Status | Evidence |
|-----|--------|----------|
| `UpdateItemPlayerAnimations` packet for per-player animation speed | **[CONFIRMED]** | Used in production by community mods (Tier 2) |
| `ItemAnimation.speed` patching | **[CONFIRMED]** | Core mechanism verified in community mods |
| `InteractionChain.setTimeShift()` | **[CONFIRMED]** | Used in community mod Tier 1 (disabled by default) |
| `InteractionSyncData.progress` manipulation | **[CONFIRMED]** | Used in community mod Tier 1 (disabled by default) |
| `AssetStore.loadAssetsFromPaths()` hot-reload | **[CONFIRMED]** | Used in community mod Tier 3 |
| `InteractionManager.cancelChains()` | **[CONFIRMED]** | Used in community mod full takeover mode |
| `InteractionManager.tryStartChain()` | **[CONFIRMED]** | Used in community mod full takeover mode |
| Failsafe burst restore (10 packets, 120ms apart) | **[CONFIRMED]** | Verified production pattern |
| `AnimationUtils.playAnimation()` | **[CONFIRMED-SOURCE]** | Verified from decompiled source |
| `PlayAnimation` packet | **[CONFIRMED-SOURCE]** | Verified from decompiled source |
| `ActiveAnimationComponent` ECS component | **[CONFIRMED-SOURCE]** | Verified from decompiled source |
| `InteractionSyncData.chargeValue` for charge progress | **[CONFIRMED-SOURCE]** | Verified from `ChargingInteraction.java` |
| `ChargingInteraction.next` threshold map | **[CONFIRMED-SOURCE]** | Verified from decompiled source |
| Bow charge speed via animation speed override | **[THEORETICAL]** | Should work (same mechanism as melee) but untested |
| Charge threshold modification via hot-reload | **[THEORETICAL]** | Requires RootInteraction asset store reload — unverified |
| `InteractionContext.setTimeShift()` propagation | **[CONFIRMED-SOURCE]** | Verified it calls both chain.setTimeShift and manager.setGlobalTimeShift |
| `ActiveAnimationComponent` read/write per slot | **[CONFIRMED-SOURCE]** | Verified from decompiled source — `getComponentType()`, `setPlayingAnimation()` |
| `InteractionManager.startChain()` (unconditional) | **[CONFIRMED-SOURCE]** | Verified from decompiled source — skips rule check |
| `InteractionManager.canRun()` pre-check | **[CONFIRMED-SOURCE]** | Verified from decompiled source |
| `InteractionManager.clear()` cancel-all | **[CONFIRMED-SOURCE]** | Verified from decompiled source |
| `InteractionManager.forEachInteraction()` fold | **[CONFIRMED-SOURCE]** | Verified from decompiled source |
| `InteractionChain.getTimeInSeconds()` | **[CONFIRMED-SOURCE]** | Verified from decompiled source |
| `InteractionChain.setOnCompletion()` callback | **[CONFIRMED-SOURCE]** | Verified from decompiled source |
| `InteractionContext.fork()` concurrent chains | **[CONFIRMED-SOURCE]** | Verified from decompiled source — 3 overloads |
| `InteractionContext.getEntity()` vs `getOwningEntity()` | **[CONFIRMED-SOURCE]** | Verified — proxy interaction distinction |
| `InteractionCooldown` fields | **[CONFIRMED-SOURCE]** | Verified from protocol class |
| `ChargingInteraction.failed` branch | **[CONFIRMED-SOURCE]** | Verified from decompiled source — compiled as label after `next` branches |
| `ChargingDelay` sub-fields | **[CONFIRMED-SOURCE]** | Verified from decompiled source — 5 fields |
| ECS ordering after `TickInteractionManagerSystem` | **[CONFIRMED]** | Community mods use `@SystemDependency(Order.AFTER)` |
| `TimeResource.getTimeDilationModifier()` | **[CONFIRMED-SOURCE]** | Verified — `InteractionManager.tick()` multiplies by this |
| `toPacket()` SoftReference caching | **[CONFIRMED-SOURCE]** | Verified — clone() is mandatory to avoid cache corruption |
| `AnimationUtils` overloads | **[CONFIRMED-SOURCE]** | Verified — 5 `playAnimation` + 2 `stopAnimation` = 7 total. Play: `(ref, slot, animId, accessor)`, `(ref, slot, animId, sendToSelf, accessor)`, `(ref, slot, animSetId, animId, accessor)`, `(ref, slot, animSetId, animId, sendToSelf, accessor)`, `(ref, slot, configObj, animId, accessor)`. Stop: `(ref, slot, accessor)`, `(ref, slot, sendToSelf, accessor)` |

---

## ActiveAnimationComponent (ECS)

Server-side ECS component that tracks which animation is currently playing on each `AnimationSlot` for an entity. Useful for reading what animation is active without intercepting packets. **[CONFIRMED-SOURCE]**

```java
import com.hypixel.hytale.server.core.modules.entity.component.ActiveAnimationComponent;
import com.hypixel.hytale.protocol.AnimationSlot;
```

### API

| Return | Method | Description |
|--------|--------|-------------|
| `static ComponentType<EntityStore, ActiveAnimationComponent>` | `getComponentType()` | Get the ECS component type (delegates to `EntityModule.get().getActiveAnimationComponentType()`) |
| `String[]` | `getActiveAnimations()` | Returns the **raw internal array** (not a copy) of 5 animation IDs indexed by `AnimationSlot` ordinal: [Movement, Status, Action, Face, Emote]. Null entries = no animation on that slot. **Do not mutate** — use `setPlayingAnimation()` instead. |
| `void` | `setPlayingAnimation(AnimationSlot slot, @Nullable String animation)` | Set the current animation for a slot. Equality-checks before writing but does **not** set `isNetworkOutdated` internally — dirty flagging is managed externally by the system that drives animation sync. Does **not** broadcast packets — use `AnimationUtils` for that. |
| `boolean` | `consumeNetworkOutdated()` | Returns true if any slot changed since last check, then resets the dirty flag. Used internally by the networking system to know when to sync. |

### Usage: Read Current Animation State

```java
// Get component type for ECS queries
var activeAnimType = ActiveAnimationComponent.getComponentType();

// Read from an entity's ECS chunk
ActiveAnimationComponent comp = chunk.getComponent(index, activeAnimType);
String[] active = comp.getActiveAnimations();

// Check what's playing on each slot
String movementAnim = active[AnimationSlot.Movement.ordinal()];  // e.g., "Run"
String actionAnim = active[AnimationSlot.Action.ordinal()];      // e.g., "SwingRight" or null
String emoteAnim = active[AnimationSlot.Emote.ordinal()];        // e.g., null

if (actionAnim != null) {
    LOGGER.atInfo().log("Entity is playing action animation: %s", actionAnim);
}
```

### Usage: Check If Entity Is Mid-Attack

```java
// Combine with InteractionManager for full picture:
// - ActiveAnimationComponent tells you WHAT animation is playing (visual state)
// - InteractionManager tells you WHAT interaction is running (gameplay state)
// Both are needed because animations and interactions are separate systems

ActiveAnimationComponent comp = chunk.getComponent(index, activeAnimType);
String action = comp.getActiveAnimations()[AnimationSlot.Action.ordinal()];
boolean hasActionAnim = action != null;
```

> **Note:** `setPlayingAnimation()` only updates the server-side tracking. It does NOT send packets to clients. To visually change an animation on clients, use `AnimationUtils.playAnimation()` which both broadcasts the packet and updates this component internally.

---

## Emote System (New in 2026.03.26)

Players can trigger emotes (animations with icons). The system uses three protocol types:

**Packets:**
- `PlayEmote` (Client -> Server, ID 360): Player requests emote by `emoteId` string
- `UpdateEmotes` (Server -> Client, ID 361): Server sends emote registry (Init/Update/Remove)

**Protocol type:**
- `ProtocolEmote`: Data structure with `id`, `name`, `animation` (animation asset), `icon` (image asset), `isLooping`

**Potential use:** Trigger emotes programmatically for celebrations (level-up, skill allocation, realm completion). Listen for `PlayEmote` packets to detect player emote usage.

---

## Common Issues

### Animation Plays But Damage Doesn't Apply

Animation speed (visual) and interaction timing (gameplay) are separate. Speeding up only the animation via Tier 2 makes it look faster, but damage still lands at vanilla timing. For true attack speed: use Tier 1 (time shift) + Tier 2 (visual sync) together.

### Speed Override Gets Stuck After Teleport

The `UpdateItemPlayerAnimations` packet may not reach the client before a world change. Always use the failsafe restore pattern: burst 10 restore pairs on override end, periodic baseline reassert within 3.5s window.

### Item Swap Causes Animation Glitch

When a player switches weapons, the previous item's modified animations may linger. Listen for item selection changes and restore the previous weapon's baseline animations with a 350ms cooldown.

### World Time Dilation Affects Interaction Timing

`InteractionManager.tick()` multiplies delta time by `TimeResource.getTimeDilationModifier()`. If the world is in slow-motion (e.g., boss cinematic, debug mode), ALL interaction timing scales with it. Attack speed modifications must account for this or they'll stack incorrectly — a 2x speed buff in a 0.5x world yields 1x effective speed, not 2x.

```java
// Read world time dilation
TimeResource timeRes = world.getResource(TimeResource.class);
float dilation = timeRes.getTimeDilationModifier();  // 1.0 = normal, 0.5 = half speed
```

### chargeValue Manipulation Doesn't Affect Charge Speed

The `chargeValue` field is **read-only from the server's perspective** — it's set by the client when the button is released. You cannot make the client charge faster by modifying `chargeValue` on the server. To affect charge speed visually, modify the `ShootCharging` animation speed. To affect charge thresholds, modify the interaction asset's `Next` map.

### Tier 1 Time Shift Causes Visual Desync

When using `chain.setTimeShift()` or `data.progress` manipulation, the server thinks the attack is further along than the client's animation shows. Combine with Tier 2 (matching animation speed override) to keep visuals in sync. Community implementations disable Tier 1 by default for this reason.
