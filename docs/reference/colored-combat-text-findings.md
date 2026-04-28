# Colored Combat Text — Research Findings

Last updated: 2026-03-06

## Problem Statement

The `CombatTextUpdate` protocol packet has only two fields:
```java
public class CombatTextUpdate extends ComponentUpdate {
    public float hitAngleDeg;
    public String text = "";
}
```
**No color field.** Attacker floating damage numbers are always white. We want per-element colored damage numbers (fire=orange, lightning=yellow, water=blue, etc.).

### What Already Works (Defender Side)

The **defender** screen flash (red vignette) uses `DamageInfo` packet which carries a `DamageCause` with `damageTextColor`. We have 16 custom DamageCause assets (8 elements x normal/crit) with per-element colors. This is fully working.

The problem is exclusively on the **attacker** side — the floating combat text they see above the target.

## The Two-Channel System

| Channel | Packet | Color Support | Who Sees It |
|---------|--------|---------------|-------------|
| Defender flash | `DamageInfo` | Yes (via `DamageCause.damageTextColor`) | Defender only |
| Attacker float text | `CombatTextUpdate` | **No** | Attacker only |

## Hacks Tested

### Test Command

`/tooadmin testcolor` (alias `/tooa tc`) — fires 4 floating numbers on nearest tracked entity:
- 100 = Baseline white (always white)
- 200 = Rich text markup test
- 300 = EntityUI template swap (red)
- 400 = Ghost entity with colored template (blue)

Source: `src/.../commands/tooadmin/TooAdminTestColorCommand.java`

### Hack 1: EntityUI Template Swap — WORKS

**Mechanism:** Send `UpdateEntityUIComponents` (packet 73) with `UpdateType.AddOrUpdate` to change the global CombatText template's `combatTextColor` field. Send only to the attacker player via `writeNoCache()` (per-player delivery). Then queue `CombatTextUpdate` which renders with the new color.

**Key code pattern:**
```java
// Get the server-side asset map
var serverAssetMap = com.hypixel.hytale.server.core.modules.entityui.asset.EntityUIComponent.getAssetMap();

// Find CombatText template index
for (int i = 0; i < serverAssetMap.getNextIndex(); i++) {
    var asset = serverAssetMap.getAsset(i);
    if (asset instanceof CombatTextUIComponent) {
        combatTextIndex = i;
        originalPacket = asset.toPacket(); // public method, returns protocol EntityUIComponent
        break;
    }
}

// Clone and modify color
EntityUIComponent coloredPacket = originalPacket.clone(); // deep copy
coloredPacket.combatTextColor = new Color((byte)0xFF, (byte)0x44, (byte)0x44); // red

// Send to ONE player only
player.getPacketHandler().writeNoCache(
    new UpdateEntityUIComponents(UpdateType.AddOrUpdate, maxId,
        Map.of(combatTextIndex, coloredPacket)));

// Queue combat text (flushed on next entity tracker tick)
entityViewer.queueUpdate(targetRef, new CombatTextUpdate(0f, "300"));
```

**Critical timing:** `writeNoCache()` sends immediately. `queueUpdate()` adds to entity tracker queue, flushed on next tick. Template change arrives BEFORE combat text — correct ordering.

**Result:** Text "300" appeared red. CONFIRMED WORKING.

### Hack 2: Ghost Entity Pool — WORKS (same mechanism)

**Mechanism:** Spawn an invisible entity (ProjectileComponent shell) at the target's position. Queue CombatTextUpdate on the ghost entity instead of the real target. The ghost can have a different color template... in theory.

**Result:** Text "400" appeared blue. BUT this uses the same global template swap mechanism as Hack 1 — the ghost doesn't get its own independent color.

**Conclusion:** Ghost entities provide a different text *position* but not a different text *color*. The ghost entity approach adds complexity without solving the per-text color problem.

### Hack 3: Rich Text Markup — FAILED

**Mechanism:** Embed inline color tags in the CombatTextUpdate text string.

**Tested:** `<color=#FF6600>200</color>`

**Result:** Client rendered the literal markup as plain white text: `<color=#FF6600>200</color>`. The CombatText Label does NOT parse rich text/XAML markup.

**Conclusion:** Dead end. CombatText.ui uses a simple `Label #Text` with no rich text support.

## Critical Discovery: Global Template = Retroactive Coloring

**The CombatText template color is applied retroactively to ALL visible combat text, not just newly spawned text.**

When we send a red template via `UpdateEntityUIComponents`:
1. ALL currently visible combat text turns red (including previously white text)
2. New combat text spawns red
3. When we revert to white, ALL visible text turns white again

This means the client re-reads the template color every frame for rendering — it does NOT "bake" the color at spawn time.

### Implications for Production

**Positive:**
- Combat text duration is short (0.4s default)
- In fast combat, old text is already fading when new text arrives
- The "last color wins" visual is often acceptable

**Negative:**
- Cannot have two different-colored texts visible simultaneously with this approach
- If fire (orange) and lightning (yellow) damage happen within 0.4s, both texts show the last color set

## Open Question: Per-Text Color

**Can each individual combat text instance have its own color?**

### Approach A: Accept "Last Color Wins"

Don't fight the global template. In practice:
- Swap to element color → queue text → don't revert
- Next damage swaps to ITS color → old text changes but is already fading
- Visual result: most recent element's color dominates, which is actually informative

**Pros:** Simple, zero overhead, already works.
**Cons:** Brief color bleed on overlapping texts.

### Approach B: Multiple CombatText Template Indices

Register additional EntityUI components (one per element color) at new indices in the global template map. Then use `UIComponentsUpdate` (per-entity component update) to tell each entity which CombatText template index to use.

**How it would work:**
1. At plugin load, add templates: index N = fire CombatText (orange), N+1 = lightning (yellow), etc.
2. Before firing combat text on an entity, send `UIComponentsUpdate` to the attacker player, changing the entity's UI component list to reference the fire CombatText index instead of the default one
3. Queue `CombatTextUpdate` — client uses the entity's assigned template

**Open questions:**
- Does `UIComponentsUpdate` per-entity override the global template assignment?
- Can an entity have a DIFFERENT CombatText template index than the default?
- Does the client resolve "which CombatText template" by type or by index?
- `UIComponentList.update()` gives every entity ALL indices — does the client respect per-entity overrides?

**Status:** NOT TESTED. Requires investigation of the `UIComponentsUpdate` ↔ `UIComponentList` relationship.

### Approach C: Per-Player Template Pool with Rapid Cycling

Maintain a pool of pre-colored templates. Before each hit:
1. Swap to the element's color (writeNoCache, immediate)
2. Queue combat text
3. On next tick, the text renders with the correct color
4. Before the NEXT hit, swap to the new element's color

Only issue: overlapping texts in the same 0.4s window get the latest color. Mitigation: the 0.4s CombatText duration is short enough that overlap is rare in practice.

**Status:** VIABLE. This is Approach A with more explicit management.

### Approach D: Custom HUD Overlay

Bypass the native CombatText system entirely. Build a custom HyUI HUD that shows colored damage numbers at screen-space positions corresponding to the damaged entity.

**Pros:** Full control over color, size, font, positioning.
**Cons:** Massive engineering effort, need to replicate the CombatText animation (scale, position, opacity keyframes), need to project world-space entity position to screen-space, doesn't benefit from the client's built-in CombatText viewport clamping.

**Status:** Last resort. Only consider if no protocol-level approach works.

## Key API Reference

### Name Collision Warning

Two classes named `EntityUIComponent` exist:
- `com.hypixel.hytale.protocol.EntityUIComponent` — wire format packet with `combatTextColor` field
- `com.hypixel.hytale.server.core.modules.entityui.asset.EntityUIComponent` — server-side asset with `getAssetMap()` and `toPacket()`

Use fully qualified names or import only one and qualify the other.

### Protocol Classes

| Class | Package | Key Fields/Methods |
|-------|---------|-------------------|
| `CombatTextUpdate` | `protocol` | `float hitAngleDeg`, `String text` (NO color) |
| `EntityUIComponent` | `protocol` | `combatTextColor` (Color), `combatTextFontSize`, `combatTextDuration`, `clone()`, copy constructor |
| `UpdateEntityUIComponents` | `protocol.packets.assets` | `UpdateType type`, `int maxId`, `Map<Integer, EntityUIComponent> components` |
| `UIComponentsUpdate` | `protocol` | `int[] components` (extends ComponentUpdate) |
| `Color` | `protocol` | `Color(byte red, byte green, byte blue)` |
| `UpdateType` | `protocol` | `Init`, `AddOrUpdate`, `Remove` |

### Server-Side Classes

| Class | Package | Key Methods |
|-------|---------|-------------|
| `EntityUIComponent` (asset) | `server.core.modules.entityui.asset` | `getAssetMap()` (static), `toPacket()` (public), `getId()` |
| `CombatTextUIComponent` | `server.core.modules.entityui.asset` | extends asset EntityUIComponent, `generatePacket()` (protected) |
| `UIComponentList` | `server.core.modules.entityui` | `getComponentIds()` returns `int[]`, `update()` fills with sequential indices |
| `EntityUIComponentPacketGenerator` | `server.core.modules.entityui.asset` | Uses `UpdateType.AddOrUpdate` for updates (NOT `Update`) |

### Delay Pattern (No executeLater on World)

Hytale's `World` only has `execute(Runnable)`. For delayed execution:
```java
private static void delayOnWorld(World world, long delayMs, Runnable task) {
    CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS)
        .execute(() -> world.execute(task));
}
```

### Ghost Entity Spawn Pattern

For creating invisible entities that get tracked by the entity tracker:
```java
Holder<EntityStore> holder = store.getRegistry().newHolder();
ProjectileComponent projectile = new ProjectileComponent("Projectile");
holder.putComponent(ProjectileComponent.getComponentType(), projectile);
holder.addComponent(TransformComponent.getComponentType(),
    new TransformComponent(position, new Vector3f(0f, 0f, 0f)));
holder.ensureComponent(UUIDComponent.getComponentType());
if (projectile.getProjectile() == null) {
    projectile.initialize();
}
holder.addComponent(NetworkId.getComponentType(),
    new NetworkId(store.getExternalData().takeNextNetworkId()));
Ref<EntityStore> ghostRef = store.addEntity(holder, AddReason.SPAWN);
```

Ghost needs ~500ms (10 ticks) after spawn to appear in the player's `EntityViewer.visible` set.

## Files Involved

- **Test command:** `src/.../commands/tooadmin/TooAdminTestColorCommand.java`
- **Registration:** `src/.../commands/TooAdminCommand.java` (1 line)
- **Existing indicator service:** `src/.../combat/indicators/CombatIndicatorService.java`
- **Damage types with colors:** `src/.../combat/DamageType.java` (8 elements, each with normalColor/critColor)
- **Custom DamageCause assets:** `src/main/resources/hytale-assets/Server/Entity/Damage/` (16 files)
- **Vanilla CombatText UI:** `ClientData/Game/Interface/InGame/EntityUI/CombatText.ui`
- **Vanilla CombatText config:** `Assets/.../Entity/UI/CombatText.json`

## BREAKTHROUGH: Per-Entity Template System (Approach B — Confirmed Viable)

Follow-up research (2026-03-06) confirmed that the EntityUI system fully supports per-entity
template assignment. The "global template bleed" problem from the initial test is solvable.

### Key Findings

1. **UIComponentList is per-entity**: Each entity has its own `UIComponentList` ECS component
   that holds an `int[] componentIds` array — the list of template indices this entity uses.
   File: `server.core.modules.entityui.UIComponentList` (implements `Component<EntityStore>`).

2. **UIComponentSystems.Update sends per-entity**: The tick system iterates each entity with
   a Visible+UIComponentList, calls `uiComponentList.getComponentIds()`, and queues a
   `UIComponentsUpdate` per entity via the viewer queue. Different entities can have different
   template lists. File: `server.core.modules.entityui.UIComponentSystems` (lines 149-162).

3. **Runtime template registration**: New templates can be added at any time via
   `UpdateEntityUIComponents(UpdateType.AddOrUpdate, maxId, newTemplates)`. The client
   resizes its internal array to `maxId` and accepts the new indices.

4. **Each CombatText template controls ALL visual properties independently**:

| Property | Type | Range | Example |
|----------|------|-------|---------|
| `textColor` | Color | RGB bytes | Fire: #FF4400, Poison: #8B00FF |
| `fontSize` | float | Unrestricted | Fire: 80.0 (large), Poison: 50.0 (small) |
| `duration` | float | 0.1-10.0s | Fire: 0.5s (quick burst), Poison: 2.0s (lingering) |
| `randomPositionOffsetRange` | RangeVector2f | XY bounds | Controls scatter |
| `viewportMargin` | float | 0-200px | Screen-edge clamping |
| `hitAngleModifierStrength` | float | 0.0-10.0 | Melee directional influence |
| Animation: Scale | startScale→endScale | 0.0-1.0 each | Crit: 1.5→0.8 (shrink from burst) |
| Animation: Opacity | startOpacity→endOpacity | 0.0-1.0 each | Poison: 0.8→0.0 (slow fade) |
| Animation: Position | positionOffset (Vector2f) | World-space | Float upward, sideways, etc. |
| Animation: Timing | startAt→endAt | 0.0-1.0 (% of duration) | Per-keyframe timing control |

### Production Architecture

```
Plugin Load:
  1. Register N custom CombatText templates via UpdateEntityUIComponents:
     - Index 10: Physical (white, size 68, 0.4s, default anims)
     - Index 11: Physical Crit (white, size 85, 0.5s, burst scale anim)
     - Index 12: Fire (orange #FF4400, size 72, 0.5s)
     - Index 13: Fire Crit (orange #FF6600, size 90, 0.6s, burst)
     - Index 14: Lightning (yellow #FFEE00, size 68, 0.3s, quick flash)
     - Index 15: Poison (purple #8B00FF, size 55, 1.5s, slow fade)
     - ... (one per element × normal/crit = ~16 templates)
  2. Send to all connected players via writeNoCache on connect

Per Damage Hit:
  1. Determine element type + crit → select template index
  2. Send UIComponentsUpdate to attacker player for the TARGET entity,
     overriding its CombatText template index to the element-specific one
  3. Queue CombatTextUpdate with damage number text
  4. On next tracker tick: text renders with that entity's assigned template
  5. No revert needed — each entity keeps its last-assigned template

Result:
  - Fire mob: orange floating text with quick burst animation
  - Poison tick: small purple text with slow lingering fade
  - Lightning crit: large bright yellow with scale burst
  - Each entity has its own color — no cross-entity bleed!
```

### Why This Eliminates the Bleed

The initial test (`/tooadmin testcolor`) used **global** template swaps — changing the template
definition itself, which retroactively colored ALL visible text. The per-entity approach instead
changes **which template index** an entity references. Entity A points to fire template (orange),
Entity B points to lightning template (yellow) — they coexist because the templates themselves
don't change, only the entity-to-template assignment does.

### Open Questions for Implementation

1. **UIComponentsUpdate delivery method**: Can we send it via `writeNoCache` directly (like
   we do for `UpdateEntityUIComponents`), or must it go through the viewer queue? If queue-only,
   we need to ensure it flushes before the CombatTextUpdate.

2. **Template index allocation**: We need indices above the vanilla ones. Check how many
   vanilla EntityUI components exist at startup to pick safe indices (10+ should be safe).

3. **Per-player template sync**: New players connecting mid-game need the custom templates
   sent during their `PlayerReadyEvent`. Handle reconnection gracefully.

4. **Same-entity rapid hits**: If fire and lightning hit the same mob within 0.4s, the second
   hit changes the entity's template, and the first hit's text retroactively changes color.
   This is the same "last color wins" issue but scoped to ONE entity, which is much less
   noticeable than the global bleed.

## Recommended Next Step

**Approach B (Per-Entity Templates)** is the production path:
1. Build a `CombatTextTemplateManager` that registers element-specific templates on load
2. Modify `CombatIndicatorService` to send per-entity `UIComponentsUpdate` before each hit
3. Test with `/tooadmin testcolor` extended to verify per-entity independence
4. Polish animation profiles per element type for maximum visual feedback
