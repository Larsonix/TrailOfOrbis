# Inventory Open/Close Detection in Hytale

The single most frustrating gap in the Hytale server API: there is **no event for detecting when a player opens or closes their inventory**.

This document tracks everything we know about the problem, what we've tried, what works, and what the 2026.03.26 update changed.

> **Last updated:** 2026-03-26 (Hytale version 2026.03.26-89796e57b)

---

## What Exists (and What Doesn't)

### What We Want
A server-side way to know when a player opens their inventory screen (presses Tab/I), so we can show contextual HUDs (stat overlays, equipment comparison, etc.).

### What Hytale Provides

| Feature | Available? | Details |
|---------|:----------:|---------|
| Event: "player opened inventory" | **NO** | Does not exist. No `InventoryOpenEvent`, no callback, no hook. |
| Event: "player closed inventory" | **Partial** | `Window.WindowCloseEvent` exists but only fires for server-opened windows (crafting benches, chests), NOT for the client-side inventory screen. |
| Packet: `OpenWindow` (ID 200) | **Server→Client only** | Server sends this to open crafting/container windows. NOT sent when player opens their own inventory (that's client-side). |
| Packet: `ClientOpenWindow` (ID 204) | **Client→Server** | Client sends this for crafting benches. Does NOT fire for the inventory screen. |
| Packet: `CloseWindow` (ID 202) | **Both** | Fires when a server-opened window closes. NOT for client inventory. |
| `WindowManager.getWindows()` | **YES** | Returns currently open server windows. Empty when only client inventory is open. |
| `InventoryComponent` (new in 2026.03.26) | **NO** | Tracks item state only. `consumeIsDirty()` fires on item changes, not on open/close. Has zero UI awareness. |
| `InventoryChangeEvent` (new in 2026.03.26) | **NO** | Fires on item transactions only (move, add, remove). Not on UI state changes. |

### Why It's Hard

The player's inventory screen is **entirely client-side**. The client opens it, renders it, and closes it without telling the server. The server only learns about inventory when items actually move.

---

## Our Current Detection (Packet Heuristics)

**File:** `src/.../ui/inventory/InventoryDetectionManager.java`

Since Hytale gives us nothing, we infer inventory state from behavioral signals:

### Detection Signals

1. **Camera freeze** — When a player opens inventory, their mouse look stops (camera orientation in `ClientMovement` packets becomes constant)
2. **UI clicks** — `MouseInteraction` packets arrive with no `worldInteraction` (clicking inside UI, not in the 3D world)
3. **No movement** — Player stops moving when in inventory

### State Machine

```
INACTIVE → OPTIMISTIC_SHOW → CONFIRMED → INACTIVE
          (2 frozen packets)  (5 frozen + UI click)  (camera moves)
```

### Limitations

- **False positives**: Player stands still and looks at one spot → detected as "inventory open"
- **Latency**: Takes 2-5 packets (~100-250ms) to detect open, so HUD appears with slight delay
- **No distinction**: Can't tell if player opened inventory vs crafting bench vs settings menu

---

## What 2026.03.26 Changed

### New: `InventoryComponent` (ECS component)

```java
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
```

Sub-types: `Hotbar`, `Storage`, `Armor`, `Tool`, `Utility`, `Backpack`

**Useful methods:**
- `consumeIsDirty()` — Check if items changed since last check
- `getChangeEvents()` — Queue of `ItemContainerChangeEvent` (item add/remove/move)
- `markDirty()` — Force sync to client

**NOT useful for detection:** These only fire when items move, not when the UI opens.

### New: `InventoryChangeEvent` (ECS event)

```java
import com.hypixel.hytale.server.core.inventory.InventoryChangeEvent;
```

Fires when inventory contents change. Carries: `componentType`, `inventory`, `itemContainer`, `transaction`.

**Registration pattern** (changed from IEvent to ECS event):
```java
// OLD (before 2026.03.26):
eventRegistry.registerGlobal(EventPriority.NORMAL, LivingEntityInventoryChangeEvent.class, handler);

// NEW (2026.03.26+):
// Use EntityEventSystem — see InventoryChangeEventSystem.java in our codebase
```

### New: `hasEffect()` on EffectControllerComponent

Not inventory-related, but useful: can now query if an entity has an active EntityEffect natively. See `docs/hytale-api/entity-effects.md`.

---

## Possible Improvements

### Option 1: Add OpenWindow/CloseWindow packet sniffing (Recommended)

While the client inventory doesn't trigger `OpenWindow`, crafting benches and chests DO. We could augment our detection:

```java
// Detect crafting bench / chest / container opens
packetAdapter.register(OpenWindow.class, packet -> {
    // Player opened a server window (crafting, chest, etc.)
    onWindowOpened(player, packet.getWindowType());
});

packetAdapter.register(CloseWindow.class, packet -> {
    // Player closed a server window
    onWindowClosed(player, packet.getWindowId());
});
```

This wouldn't help with the base inventory screen but would give us reliable detection for container interactions.

### Option 2: Hook Window.WindowCloseEvent

Server-opened windows support close events:

```java
window.registerCloseEvent(closeEvent -> {
    // This window was closed
});
```

Only works for windows the server opened (crafting benches, chests), not the client inventory.

### Option 3: Keep packet heuristics for base inventory

Our current camera-freeze detection remains the best option for the base inventory screen. No Hytale update has provided an alternative.

---

## Summary

| Detection Target | Best Method | Reliability |
|-----------------|------------|:-----------:|
| Player inventory screen | Camera-freeze heuristic | ~85% |
| Crafting bench / chest | `OpenWindow` packet | 100% |
| Container close | `CloseWindow` packet or `WindowCloseEvent` | 100% |
| Item changes | `InventoryChangeEvent` (ECS) | 100% |
| Specific item slot | `InventoryComponent.getChangeEvents()` | 100% |

---

## Related Docs

- `docs/hytale-api/inventory.md` — Inventory management API
- `docs/hytale-api/events.md` — Event system (including new ECS events)
- `docs/hytale-api/player-input.md` — Packet interception
