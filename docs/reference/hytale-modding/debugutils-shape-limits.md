# DebugUtils Shape Rendering — Empirical Limits & Best Practices

> Community research by **Larsonix** (Trail of Orbis) with input from **Riprod**.
> Tested on Hytale build `2026.03.26-89796e57b`, April 2026.

## TL;DR

**There is no hard cap on simultaneous DebugUtils shapes.** The Hytale client will render 500,000+ shapes without dropping any — it just costs GPU time. The practical limit is framerate, not a buffer or packet limit.

| Simultaneous Shapes | FPS (from 144 cap) | Verdict |
|---------------------|---------------------|---------|
| 1,000 | 144 | No impact |
| 10,000 | ~140 | Negligible |
| 50,000 | ~60 | Playable |
| 100,000 | ~15-20 | Heavy but functional |
| 500,000 | ~3 | Slideshow, but all shapes render |

## What We Tested

Three independent variables, each isolated:

1. **Burst capacity** — How many `addLine()` calls in a single tick?
2. **Sustained concurrent rendering** — How many shapes alive at once when sent properly?
3. **Gradual ramp** — At what point do shapes stop appearing?

### Method

Shapes arranged in 10 concentric colored rings so missing shapes are immediately visible. Each ring has a distinct color (Red, Cyan, Gold, Purple, Green, Orange, Blue, Pink, White, Yellow). If ring 7 is intact but ring 8 has gaps, the limit is between those counts.

Shapes are interleaved across rings in send order (`[R0#0, R1#0, ..., R9#0, R0#1, R1#1, ...]`) so all rings receive shapes simultaneously — no inner-ring bias.

### Results

**Burst (single tick):** 10,000 `addLine()` calls in one tick completes in 2-5ms server-side. All shapes render on the client. No packet drops observed up to 10,000/tick.

**Sustained (two-phase fill + maintenance):** Tested at 10K, 50K, 100K, and 500K. **All rings remained intact at every count.** FPS degrades linearly with shape count. No shape eviction, no buffer overflow, no rendering cap.

**Ramp (gradual increase):** Climbed from 0 → 500,000 in steps of 5,000-10,000 every 5 seconds. All rings visible throughout. FPS dropped proportionally.

## The Two-Phase Rendering Pattern

The key to rendering large numbers of shapes without flickering, duplication, or gaps.

### Why naive approaches fail

| Approach | Problem |
|----------|---------|
| `clear()` every tick + burst-send | Clear wipes shapes before new ones arrive → flickering |
| Short duration matching refresh | Any timing jitter causes visible gaps |
| Long duration + uncontrolled sends | Duplicate shapes accumulate → wasted GPU budget |
| Distance-sorted + cursor reset | Moving resets cursor → far shapes never get sent |

### The pattern

```
Phase 1 (Fill):    Send all N shapes as fast as possible
Phase 2 (Maintain): Refresh each shape exactly once per duration cycle
```

**Phase 1 — Fill.** Send shapes at a high rate until every shape has been sent once. The fill rate must be high enough to complete within one shape duration window:

```
fillRate = max(30, ceil(totalShapes / (durationMs / 50)))
```

**Phase 2 — Maintenance.** Auto-calculate the refresh rate so each shape gets refreshed exactly once before it expires:

```
targetCycleTicks = (durationMs - marginMs) / 50
maintenanceRate  = ceil(totalShapes / targetCycleTicks)
```

This produces exactly N shapes on the client at steady state — zero duplicates, zero gaps.

### Java implementation

```java
// Configuration
int beamDurationMs = 5000;   // How long each shape lives on the client
int initialFillRate = 30;    // Shapes/tick during fill (auto-scale for large counts)
int refreshMarginMs = 500;   // Refresh this much before expiry

// Pre-compute
int targetCycleTicks = Math.max(1, (beamDurationMs - refreshMarginMs) / 50);
int maintenanceRate = Math.max(1, (int) Math.ceil((double) totalShapes / targetCycleTicks));

// Auto-scale fill rate for large shape counts
int durationTicks = Math.max(1, beamDurationMs / 50);
int minFillRate = Math.max(1, (int) Math.ceil((double) totalShapes / durationTicks));
int fillRate = Math.max(initialFillRate, minFillRate);

// State
int sendCursor = 0;
boolean fillDone = false;

// Called every tick (50ms)
void tick(World world) {
    int rate = fillDone ? maintenanceRate : fillRate;

    for (int i = 0; i < rate; i++) {
        if (sendCursor >= totalShapes) {
            sendCursor = 0;
            if (!fillDone) {
                fillDone = true;
                break; // switch to maintenance rate next tick
            }
        }

        ShapeData shape = shapes.get(sendCursor);
        sendCursor++;

        DebugUtils.addLine(world,
            shape.start, shape.end, shape.color,
            0.08,                              // thickness
            beamDurationMs / 1000.0f,          // duration in seconds
            DebugUtils.FLAG_NO_WIREFRAME);
    }
}
```

### Key rules

1. **Never call `DebugUtils.clear()` per-tick.** Only on state changes (e.g., respec, mode switch). Shapes auto-expire.
2. **Never reset the cursor** on player movement or re-sorting. Let it cycle through all shapes continuously.
3. **Use long durations (5s+).** Provides margin against timing jitter. A missed refresh is invisible when shapes survive 5+ seconds.
4. **The maintenance rate formula scales automatically.** Works for 200 shapes or 200,000 — no config changes needed.
5. **Fill rate must complete within one duration window.** If `totalShapes / fillRate * 50 > durationMs`, early shapes expire before fill completes and you get ring gaps.

## State Change Pattern: Clear+Burst

The two-phase pattern handles **steady-state** rendering. For **state changes** (anything that modifies shape positions or colors), use the clear+burst pattern instead.

### The problem with incremental overlay

DebugUtils shapes are fire-and-forget. Once sent, they cannot be individually cancelled — they live for their full duration. If a state change moves a shape's endpoint (e.g., a node entity shifts Y position), the old shape persists at the wrong position as a "ghost" for up to `durationMs`. Sending a corrected shape at the new position doesn't help — both old and new shapes are visible simultaneously.

An incremental approach (diff old vs new, send only changed shapes) fails when:
- **Endpoints move** — old shape at old position + new shape at new position = two visible shapes
- **Diff misses position changes** — if only color is diffed, position-shifted shapes aren't detected
- **Deallocation dims a beam** — old bright shape at old position visually dominates the new dim shape

### The solution

On any state change, clear ALL shapes and re-send everything in one tick:

```java
void onStateChange(World world, Set<String> newAllocatedNodes) {
    // 1. Recompute all visuals with the new state
    recomputeVisuals(newAllocatedNodes);

    // 2. Wipe all stale shapes — one packet
    DebugUtils.clear(world);

    // 3. Burst-send every shape in this tick
    float durationSeconds = beamDurationMs / 1000.0f;
    for (ShapeData shape : allShapes) {
        DebugUtils.addLine(world,
            shape.start, shape.end, shape.color,
            0.08, durationSeconds,
            DebugUtils.FLAG_NO_WIREFRAME);
    }

    // 4. Skip fill phase — all shapes are already on the client
    initialFillDone = true;
    sendCursor = 0;
    // Maintenance takes over on the next tick
}
```

### Why this doesn't flicker

`clear()` and `addLine()` in the same tick are queued as sequential network packets. The client processes them in order within the same frame (or across 1-2 frames at worst). A single-frame gap at 144 FPS is 7ms — imperceptible.

This is **not** the same as the confirmed-failure "clear every tick + burst" pattern, which flickers because shapes never survive more than one frame. Here, `clear()` is called **once** on a state change, and shapes then survive for the full `durationMs` under maintenance.

### When to use which pattern

| Scenario | Pattern | Why |
|----------|---------|-----|
| Initial load (player enters world) | Two-phase fill+maintain | Must wait for `CLIENT_READY_DELAY_MS`; gradual fill is fine |
| Single state change (allocate/deallocate) | Clear+burst | Eliminates ghost shapes from position shifts |
| Bulk state change (respec, mode switch) | Clear+burst | Same — all shapes potentially affected |
| Steady-state rendering | Maintenance (round-robin) | Shapes auto-expire; maintenance refreshes them |

### Performance at typical scale

With ~260 skill tree connections:
- Burst: 260 `addLine()` calls = <1ms server-side (research shows 10K calls in 2-5ms)
- Clear: 1 packet
- Total: 261 packets in one tick — trivial
- Rapid clicks (3 allocations/sec): 783 extra packets/sec — well within budget

## Common Myths Debunked

### "The client has a shape buffer that overflows at ~1,500"

**False.** We rendered 500,000 shapes simultaneously. The original observation of shapes disappearing at ~1,500 was caused by duplicate sends from overlapping refresh cycles inflating the GPU cost, not a buffer overflow. The two-phase pattern eliminates duplicates entirely.

### "Sending too many shapes per tick causes packet drops"

**Not observed.** 10,000 `addLine()` calls in a single tick (2-5ms server-side) all rendered on the client. The server queues these as network packets — the client processes them over subsequent frames. No silent drops were observed up to 10K/tick.

### "`DebugUtils.clear()` is needed to manage shape count"

**Not for count management — but essential for state changes.** `clear()` wipes ALL shapes in the world. Using it per-tick causes flickering. But calling it **once** on a state change (then burst-sending new shapes in the same tick) is the correct way to eliminate ghost shapes from position/color shifts. For steady-state count management, use natural expiration via duration + maintenance refresh.

### "Incremental overlay is better than clear+burst for state changes"

**False.** Overlay (sending new shapes to cover old ones) only works when endpoints don't move. If a shape's start/end position shifts on state change (e.g., entity Y offset changes), the old shape persists as a visible ghost at the wrong position for up to `durationMs`. There is no way to cancel individual shapes — `clear()` + burst is the only correct approach for state changes that affect positions.

## API Quick Reference

```java
// Line (most efficient — two vertices)
DebugUtils.addLine(World world, Vector3d start, Vector3d end,
    Vector3f color, double thickness, float durationSeconds, int flags);

// Sphere, Cube, Cylinder, Cone (auto-apply FLAG_FADE)
DebugUtils.addSphere(World world, Vector3d pos, Vector3f color, double scale, float duration);
DebugUtils.addCube(World world, Vector3d pos, Vector3f color, double scale, float duration);
DebugUtils.addCylinder(World world, Vector3d pos, Vector3f color, double scale, float duration);
DebugUtils.addCone(World world, Vector3d pos, Vector3f color, double scale, float duration);

// Disc (supports inner radius for donut shapes)
DebugUtils.addDisc(World world, double x, double y, double z,
    double radius, Vector3f color, float duration, int flags);

// Clear all shapes in a world (use sparingly)
DebugUtils.clear(World world);

// Flags (composable via |)
DebugUtils.FLAG_NONE          // Default: solid + wireframe
DebugUtils.FLAG_NO_WIREFRAME  // Solid only (recommended for dense visuals)
DebugUtils.FLAG_NO_SOLID      // Wireframe only
DebugUtils.FLAG_FADE          // Auto-fade near end of duration
```

**Color format:** `Vector3f` with components 0.0–1.0 (not 0–255).

```java
// From hex
int hex = 0xFF4455;
Vector3f color = new Vector3f(
    ((hex >> 16) & 0xFF) / 255.0f,
    ((hex >> 8) & 0xFF) / 255.0f,
    (hex & 0xFF) / 255.0f
);
```

## Performance Characteristics

Shapes broadcast to **all players in the World**. Each `addLine()` call generates one network packet. At steady state with the two-phase pattern:

```
Packets/second = maintenanceRate × 20 TPS

Example: 1,000 shapes, 5s duration, 500ms margin
  maintenanceRate = ceil(1000 / 90) = 12/tick
  Packets/second = 12 × 20 = 240/s
```

**Server-side cost** is negligible (10,000 calls in 2-5ms). The bottleneck is always client GPU rendering.

## Gotchas

- **Shapes sent during world load are dropped.** Wait ~2.5s after a player enters a world before sending shapes.
- **`DebugUtils.clear()` is world-global.** It wipes ALL debug shapes for ALL players in that world — not just yours.
- **The server doesn't track shapes.** They're fire-and-forget packets. You must manage refresh timing yourself.
- **Shapes are per-world.** If a player teleports to another world, they lose all shapes from the previous world.
- **`FLAG_NO_WIREFRAME` matters for dense visuals.** Without it, each shape renders both solid and wireframe — doubling the draw cost.
- **Shapes cannot be individually cancelled.** They're fire-and-forget. If you need to change a shape's position or color mid-lifetime, you must `clear()` the world and re-send everything. Overlaying a new shape at a different position leaves the old one visible as a ghost.
