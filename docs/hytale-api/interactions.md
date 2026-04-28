# Hytale Interactions Reference

This document covers both the **Java plugin API** for creating custom interactions (SimpleInstantInteraction, InteractionContext, OpenCustomUI) and the comprehensive **JSON field reference** for all built-in interaction types (flow control, cooldowns, combos, charging, blocks, items, entities, stats, effects, farming).

> **Sources:** Plugin API section verified against Hytale server source and community mod implementations. Built-in interaction type reference by Stephen Baynham (cannibalvox) via HytaleModding.dev.
>
> **Related docs:** For item states and metadata, see `docs/hytale-api/item-states.md`. For block components, see `docs/hytale-api/block-components.md`. For HyUI page building, see `docs/HyUI/`. For entity effects, see `docs/hytale-api/entity-effects.md`.

---

## Quick Reference

| Task | Approach |
|------|----------|
| Register custom interaction | `getCodecRegistry(Interaction.CODEC).register("Name", Class, CODEC)` |
| Register block config UI | `getCodecRegistry(OpenCustomUIInteraction.PAGE_CODEC).register("Name", Class, CODEC)` |
| Get targeted block | `TargetUtil.getTargetBlock(entity, maxDistance, commandBuffer)` |
| Get held item | `interactionContext.getHeldItem()` |
| Get held item container | `interactionContext.getHeldItemContainer()` + `getHeldItemSlot()` |
| Get target block position | `interactionContext.getTargetBlock()` |
| Get World from interaction | `((EntityStore) commandBuffer.getExternalData()).getWorld()` |
| Chain interactions in JSON | Use `Next` / `Failed` fields on SimpleInteraction subtypes |
| Branch on click vs hold | Use `FirstClick` with `Click` / `Hold` fields |
| Fork parallel chains | Use `Parallel` with array of RootInteractions |
| Repeat an interaction | Use `Repeat` with `ForkInteractions` + count (-1 for infinite) |
| Add cooldown to ability | Configure `InteractionCooldown` on RootInteraction or use `TriggerCooldown` |
| Build combo chain | Use `Chaining` with shared `ChainId` |
| Charge-up mechanic | Use `Charging` with `Next` map (Float thresholds) |
| Deal damage via JSON | Use `DamageEntity` with `DamageCalculator` |
| Block/shield mechanic | Use `Wielding` with `DamageModifiers` / `AngledWielding` |
| Modify player inventory | Use `ModifyInventory` (NOT `AddItem` due to bugs) |
| Check/spend entity stats | Use `StatsCondition` then `ChangeStat` |
| Apply status effect | Use `ApplyEffect` with `EffectId` |

---

## Plugin API -- Custom Interactions

### Overview

Custom interactions are triggered by item or block use (primary/secondary click). They extend `SimpleInstantInteraction` and are registered against a codec name that's referenced in item/block JSON.

### Creating an Interaction

```java
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;

public class MyInteraction extends SimpleInstantInteraction {

    public static final BuilderCodec<MyInteraction> CODEC =
        BuilderCodec.builder(MyInteraction.class, MyInteraction::new).build();

    @Override
    protected void firstRun(InteractionType interactionType,
                             InteractionContext interactionContext,
                             CooldownHandler cooldownHandler) {
        // Your interaction logic here
    }
}
```

### Registration

In your plugin's `setup()`:

```java
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;

@Override
protected void setup() {
    this.getCodecRegistry(Interaction.CODEC)
        .register("MyInteraction", MyInteraction.class, MyInteraction.CODEC);
}
```

### In Item JSON

Reference the interaction by its registered name:

```json
{
  "Interactions": {
    "Secondary": {
      "Interactions": [
        {
          "Type": "MyInteraction"
        }
      ]
    }
  }
}
```

For block interactions, nest under `State.Definitions`:

```json
{
  "BlockType": {
    "State": {
      "Definitions": {
        "Normal": {
          "Interactions": {
            "Use": {
              "Interactions": [
                {
                  "Type": "MyInteraction"
                }
              ]
            }
          }
        }
      }
    }
  }
}
```

---

## InteractionContext API

The `InteractionContext` provides access to the interacting player's state:

| Method | Returns | Description |
|--------|---------|-------------|
| `getEntity()` | `Ref<EntityStore>` | The player entity ref |
| `getHeldItem()` | `ItemStack` | Read-only copy of held item |
| `getHeldItemContainer()` | `ItemContainer` | Mutable container (for item changes) |
| `getHeldItemSlot()` | `short` | Slot index of held item |
| `getTargetBlock()` | `BlockPosition` | Block being targeted (for block interactions) |
| `getCommandBuffer()` | `CommandBuffer<EntityStore>` | Entity store command buffer |

### Getting World from Context

```java
World world = ((EntityStore) interactionContext.getCommandBuffer().getExternalData()).getWorld();
```

### Modifying Held Item

`ItemStack` is immutable. To modify the held item:

```java
ItemContainer container = interactionContext.getHeldItemContainer();
short slot = interactionContext.getHeldItemSlot();
ItemStack item = container.getItemStack(slot);

if (item != null) {
    item = item.withState("NewState");
    container.setItemStackForSlot(slot, item);
}
```

---

## TargetUtil -- Block Raycasting

`TargetUtil.getTargetBlock()` performs a raycast from the player's view to find the targeted block:

```java
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.math.vector.Vector3i;

Vector3i blockPos = TargetUtil.getTargetBlock(
    interactionContext.getEntity(),   // Player entity ref
    10.0,                              // Max distance in blocks
    interactionContext.getCommandBuffer()
);

// Returns null if no block is targeted within range
if (blockPos != null) {
    // Use blockPos
}
```

---

## OpenCustomUI -- Block Config Pages

The `OpenCustomUI` interaction type opens a HyUI page when a player interacts with a block. This is ideal for configurable blocks.

### Registration

```java
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;

// Check if HyUI is available
PluginBase hyui = PluginManager.get().getPlugin(PluginIdentifier.fromString("Ellie:HyUI"));
if (hyui != null && hyui.isEnabled()) {
    this.getCodecRegistry(OpenCustomUIInteraction.PAGE_CODEC)
        .register("MyBlockUI", MyBlockUI.class,
            BuilderCodec.builder(MyBlockUI.class, MyBlockUI::new).build());
}
```

### In Block JSON

```json
{
  "BlockType": {
    "State": {
      "Definitions": {
        "Normal": {
          "Interactions": {
            "Use": {
              "Interactions": [
                {
                  "Type": "OpenCustomUI",
                  "Page": {
                    "Id": "MyBlockUI"
                  }
                }
              ]
            }
          }
        }
      }
    }
  }
}
```

### CustomPageSupplier Implementation

```java
import au.ellie.hyui.builders.PageBuilder;
import au.ellie.hyui.builders.NumberFieldBuilder;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction.CustomPageSupplier;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class MyBlockUI implements CustomPageSupplier {

    @Override
    public CustomUIPage tryCreate(Ref<EntityStore> ref,
                                   ComponentAccessor<EntityStore> accessor,
                                   PlayerRef playerRef,
                                   InteractionContext interactionContext) {

        // 1. Get target block position
        BlockPosition pos = interactionContext.getTargetBlock();
        if (pos == null) return null;
        Vector3i blockPos = new Vector3i(pos.x, pos.y, pos.z);

        // 2. Get World and block ref
        World world = ((EntityStore) accessor.getExternalData()).getWorld();
        Ref<ChunkStore> blockRef = BlockHelper.getBlockRef(world, blockPos);
        if (blockRef == null) return null;

        // 3. Read block component
        Store<ChunkStore> store = world.getChunkStore().getStore();
        MyBlockComponent comp = store.getComponent(blockRef, MyBlockComponent.getComponentType());
        if (comp == null) return null;

        // 4. Build page from HTML template
        PageBuilder page = PageBuilder.pageForPlayer(playerRef).loadHtml("MyBlock.html");

        // 5. Bind UI fields to component data
        page.getById("range", NumberFieldBuilder.class).ifPresent(field -> {
            field.withValue(comp.range);
            field.addEventListener(CustomUIEventBindingType.ValueChanged, val -> comp.range = val);
        });

        // 6. Open and return the page
        return page.open(((EntityStore) accessor.getExternalData()).getStore());
    }
}
```

### HyUI Builder Types for Config Pages

| Builder | HTML Element | Value Type | Use Case |
|---------|-------------|------------|----------|
| `NumberFieldBuilder` | `<input type="number">` | `Double` | Numeric config (range, delay) |
| `TextFieldBuilder` | `<input type="text">` | `String` | Text config (name, message) |
| `CheckBoxBuilder` | `<input type="checkbox">` | `Boolean` | Toggle config |
| `DropdownBoxBuilder` | `<select>` | `String` | Enum selection |

### Dropdown with Enum Values

```java
page.getById("mode", DropdownBoxBuilder.class).ifPresent(dropdown -> {
    // Populate options from enum
    for (MyEnum value : MyEnum.values()) {
        dropdown.addEntry(value.name(), value.name());
    }
    // Set current value
    dropdown.withValue(component.mode.name());

    // Listen for changes
    dropdown.addEventListener(CustomUIEventBindingType.ValueChanged, val -> {
        for (MyEnum value : MyEnum.values()) {
            if (value.name().equalsIgnoreCase(val)) {
                component.mode = value;
                break;
            }
        }
    });
});
```

### HTML Template (placed in `Common/UI/Custom/`)

```html
<div class="decorated-container"
     data-hyui-title="My Block Config"
     style="anchor-height: 200; anchor-width: 500">
    <div class="row" style="layout-mode: Left; anchor-height: 100">
        <label style="vertical-align: Center">Range: </label>
        <input id="range" type="number" step="0.1"
               data-hyui-max-decimal-places="2"
               style="flex-weight: 1; anchor-left: 10; anchor-right: 10; anchor-min-width: 50" />
    </div>
</div>
```

---

## Built-in Interaction Type Reference

This section documents all built-in interaction types available in Hytale's JSON interaction system. Every interaction in an item or block's `Interactions` JSON is part of an **interaction chain** -- a sequence of interactions that execute one after another based on success/failure branching.

### Common Enums

These enums are referenced throughout the interaction system. They are defined here once.

**InteractionTarget Values**

Used by interactions that can target different entities in the chain:

- `User` -- The entity whose actions caused this interaction chain to execute. Usually the same as Owner.
- `Owner` -- The entity upon whom this interaction chain is executing.
- `Target` -- The entity target of this chain, if any. Mutable; most commonly the entity the User was targeting when the chain began.

**InteractionType Values**

- `Primary`
- `Secondary`
- `Ability1`
- `Ability2`
- `Ability3`
- `Use`
- `Pick`
- `Pickup`
- `CollisionEnter`
- `CollisionLeave`
- `Collision`
- `EntityStatEffect`
- `SwapTo`
- `SwapFrom`
- `Death`
- `Wielding`
- `ProjectileSpawn`
- `ProjectileHit`
- `ProjectileMiss`
- `ProjectileBounce`
- `Held`
- `HeldOffhand`
- `Equipped`
- `Dodge`
- `GameModeSwap`

**GameMode Values**

- `Creative`
- `Adventure`

---

### Base Types

#### Interaction (base fields)

All interactions of every type inherit from Interaction and have these fields.

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| ViewDistance | `Double` (Default: 96.0) | No | Distance in blocks at which other players can see effects from this interaction. |
| Effects | `InteractionEffects` | No | Sound, animations, and particle effects triggered when this interaction begins. |
| HorizontalSpeedMultiplier | `Float` (Default: 1.0) | No | Multiplier applied to the User entity's movement speed while executing. |
| RunTime | `Float` | No | If provided, this interaction continues executing for at least this long before the chain moves on. |
| CancelOnItemChange | `Boolean` (Default: true) | No | If true, cancelled when the User entity's held item changes (hotbar slot change or slot contents change). |
| Rules | `InteractionRules` | Yes | Adds limitations and cancellation conditions to this interaction. |
| Settings | `Map` (Key: `GameMode`, Value: `InteractionSettings`) | No | Per-GameMode settings for the interaction. |
| Camera | `InteractionCameraSettings` | No | Camera motion keyframes for cutscenes and reveals. |

**InteractionSettings Fields**

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| AllowSkipOnClick | `Boolean` (Default: false) | No | If true, the user can skip this interaction by clicking shortly after it starts. |

**InteractionCameraSettings Fields**

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| FirstPerson | `Array` of `InteractionCamera` | No | Camera keyframes for first-person mode. **Keyframe times must be in ascending order.** |
| ThirdPerson | `Array` of `InteractionCamera` | No | Camera keyframes for third-person mode. **Keyframe times must be in ascending order.** |

**InteractionCamera Fields**

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| Time | `Float` (Default: 0.1) | No | Seconds after interaction begins that the camera arrives at this keyframe. **Cannot be <= 0.** |
| Position | `Vector3` | Yes | Camera location at this keyframe. |
| Rotation | `Direction` | Yes | Camera direction at this keyframe. |

#### InteractionEffects

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| Particles | `Array` of `ModelParticle` | No | Particle systems for third-person mode. |
| FirstPersonParticles | `Array` of `ModelParticle` | No | Particle systems for first-person mode. |
| WorldSoundEventId | `Asset` (`SoundEvent`) | No | Sound played in the world at User's location. **Must be mono (single-channel).** |
| LocalSoundEventId | `Asset` (`SoundEvent`) | No | 2D sound played for the User entity only. Ignored for non-players. |
| Trails | `Array` of `ModelTrail` | No | Trail effects on the User entity. |
| WaitForAnimationToFinish | `Boolean` (Default: false) | No | If true, interaction continues for at least the animation duration. |
| ItemPlayerAnimationsId | `Asset` (`ItemPlayerAnimations`) | No | Animation set used by the User entity while executing. |
| ItemAnimationId | `String` | No | Animation to trigger on start. **Not validated on server start.** |
| ClearAnimationOnFinish | `Boolean` (Default: false) | No | If true, animations are halted when the interaction completes. |
| ClearSoundEventOnFinish | `Boolean` (Default: false) | No | If true, sounds are halted when the interaction completes. |
| CameraEffect | `Asset` (`CameraEffect`) | No | Applied while the interaction executes. |
| MovementEffects | `MovementEffects` | No | Applied to the User entity while executing. |
| StartDelay | `Float` (Default: 0.0) | No | Seconds to wait before triggering effects. |

#### InteractionRules

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| BlockedBy | `Array` of `InteractionType` (Default: based on chain's type) | No | This RootInteraction cannot execute while chains of listed types are running. **Only works on RootInteraction, not nested Interactions.** |
| Blocking | `Array` of `InteractionType` | No | Chains of listed types cannot execute while this interaction runs. |
| InterruptedBy | `Array` of `InteractionType` | No | Chains of listed types will cancel this chain when they execute. **Only works on RootInteraction.** |
| Interrupting | `Array` of `InteractionType` | No | Chains of listed types are cancelled when this interaction begins. |
| BlockedByBypass | `String` | No | This RootInteraction cannot be blocked by chains whose RootInteraction has this asset tag. |
| BlockingBypass | `String` | No | This interaction will not block RootInteractions with this asset tag. |
| InterruptedByBypass | `String` | No | This RootInteraction cannot be cancelled by RootInteractions with this asset tag. |
| InterruptingBypass | `String` | No | This interaction will not cancel chains whose RootInteraction has this asset tag. |

#### SimpleInteraction

Most interaction types below inherit from SimpleInteraction. Its purpose is to facilitate chaining by branching based on success or failure. It is valid to create an interaction asset of type `"Simple"` for using interaction base fields with no additional functionality.

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| Next | `Asset` (`Interaction`) | No | Executed after this interaction finishes, if it did not fail. |
| Failed | `Asset` (`Interaction`) | No | Executed after this interaction finishes, if it failed. |

> Types that do NOT inherit SimpleInteraction are noted in their documentation.

---

### Flow Control

#### Condition

Fails if any provided field does not match the current state.

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| RequiredGameMode | `GameMode` | No | Fails if current GameMode does not match. |
| Jumping | `Boolean` | No | Fails if player's jumping state does not match. |
| Swimming | `Boolean` | No | Fails if player's swimming state does not match. |
| Crouching | `Boolean` | No | Fails if player's crouching state does not match. |
| Running | `Boolean` | No | Fails if player's running state does not match. |
| Flying | `Boolean` | No | Fails if player's flying state does not match. |

#### FirstClick

**Does NOT inherit SimpleInteraction** -- no Next or Failed fields.

Branches based on whether the input was a quick tap or a longer press. If the interaction was not initiated by a key press, the Click path is followed (but relying on this is risky).

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| Click | `Asset` (`Interaction`) | No | Executed if the input was a quick tap. |
| Hold | `Asset` (`Interaction`) | No | Executed if the input was a longer hold. |

> Implementation detail: internally marked as failed when the Hold path is followed.

#### Interrupt

Cancels one or more running interaction chains on an entity **and all forked chains** those chains have spawned. Cancelled chains are marked as Failed.

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| Entity | `InteractionTarget` (Default: User) | Yes | The entity owning the target chains. |
| InterruptTypes | `Array` of `InteractionType` | No | Only interrupt chains of these types. If null, all types. **An empty array interrupts NO types.** |
| RequiredTag | `String` | No | Only cancel chains whose RootInteraction has this asset tag. |
| ExcludedTag | `String` | No | Only cancel chains whose RootInteraction does NOT have this asset tag. |

#### Parallel

**Does NOT inherit SimpleInteraction** -- no Next or Failed fields.

Forks all children except the first into their own chains, then executes the first RootInteraction in the current chain. No failure conditions; indifferent to forked chain outcomes.

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| Interactions | `AssetArray` of `RootInteraction` | Yes | Must contain at least two RootInteractions. |

#### Repeat

Forks a RootInteraction and waits for completion. If it fails, this interaction fails. Otherwise, it forks again, up to the specified count.

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| ForkInteractions | `Asset` (`RootInteraction`) | Yes | The RootInteraction to fork repeatedly. |
| Repeat | `Integer` (Default: 1) | No | Total fork count. -1 for infinite (until cancelled or failure). **0 is not valid.** |

#### Replace (InteractionVars)

**Does NOT inherit SimpleInteraction** -- no Next or Failed fields.

Uses the InteractionVars system on items. Looks up a named var on the owning item; if found, executes its RootInteraction. Otherwise executes the default or fails.

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| Var | `String` | Yes | The InteractionVar name to look up. |
| DefaultValue | `Asset` (`RootInteraction`) | No | Fallback if the item lacks the named var. If omitted, interaction fails. |
| DefaultOk | `Boolean` (Default: false) | No | If false, logs an angry message when the item lacks the var. No functional effect. |

#### RunRootInteraction

Immediately executes the provided RootInteraction as part of this chain. No failure conditions.

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| RootInteraction | `Asset` (`RootInteraction`) | Yes | The RootInteraction to execute. |

#### Selector

Executes a Selector to find entities and/or blocks, forking an interaction chain for each. Does not wait for forked chains. Each entity/block only forks one chain over the life of this interaction. The Selector runs at least once and may run repeatedly over the interaction lifetime (use `RunTime` or `WaitForAnimationToFinish` for sweep selectors).

Entities are ignored if they have no NetworkId, are dead, or are invulnerable (exception: Creative Mode players marked to receive hits).

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| Selector | `Selector` | Yes | Locates blocks and/or entities in an area. |
| HitEntity | `Asset` (`RootInteraction`) | No | Forked for entities not matching any HitEntityRules. |
| HitEntityRules | `Array` of `HitEntity` | No | Custom rules to fork alternative interactions for matching entities. |
| HitBlock | `Asset` (`RootInteraction`) | No | Forked for located blocks. |
| FailOn | `FailOnType` (Default: `Neither`) | No | When this interaction fails. |
| IgnoreOwner | `Boolean` (Default: true) | No | If true, the Selector will not locate the User entity. |

**HitEntity Fields**

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| Matchers | `Array` of `EntityMatcher` | Yes | All matchers must succeed for an entity to match. |
| Next | `Asset` (`RootInteraction`) | Yes | Forked if the entity matches all rules. |

**EntityMatcher Fields**

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| Type | `String` | Yes | The matcher type. |
| Invert | `Boolean` (Default: false) | No | If true, the entity must FAIL this rule to match. |

**EntityMatcher Types:**

- **Player** -- Matches only if the entity is a player.
- **Vulnerable** -- Matches only if the entity is not invulnerable.

**FailOnType Values:**

- `Neither`
- `Entity`
- `Block`
- `Either`

#### Serial

**Does NOT inherit SimpleInteraction** -- no Next or Failed fields.

Executes a list of interactions sequentially. No failure conditions.

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| Interactions | `AssetArray` of `Interaction` | Yes | Interactions to execute in order. |

---

### Cooldowns

RootInteractions have a cooldown system for resource management and preventing input spam. By default, every RootInteraction has a cooldown timer keyed to its name. Input-triggered chains default to a 0.35s cooldown; otherwise no cooldown is activated.

**Key concepts:**
- **Cooldown** activates each time a RootInteraction triggers, even with remaining charges
- **Charges** build up over time; a RootInteraction needs at least one charge (if configured) to activate
- Cooldown configuration is **shared** between RootInteractions with the same cooldown id -- only share ids between identically-configured interactions

#### CooldownCondition

Fails if the specified cooldown is active for the User entity. **Only works for players** -- always succeeds for NPCs. Requires both: cooldown expired AND at least one charge filled.

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| Id | `String` | Yes | The cooldown id to check. |

#### IncrementCooldown

Modifies an active cooldown on the User entity. Despite its name, can also reduce cooldowns. No effect if the cooldown is not active. No special failure conditions.

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| Id | `String` | No | Cooldown id. If omitted, uses the current chain's RootInteraction cooldown id. |
| Time | `Float` (Default: 0.0) | No | Amount to increase remaining cooldown by. Can be negative. Clamped to [0, max]. |
| ChargeTime | `Float` (Default: 0.0) | No | Amount to increase remaining charge time by. Can be negative. **Does not work on abilities with max 1 charge.** |
| Charge | `Integer` (Default: 0) | No | Amount to increase charge count by. Can be negative. Clamped to [0, max]. |
| InterruptRecharge | `Boolean` (Default: false) | No | If true and `Charge` is non-zero, resets remaining charge time to max after changing charge count. |

#### ResetCooldown

Triggers a cooldown on the User entity with refilled charges but immediately-active cooldown. Configuration resolution: your `Cooldown` field overrides active cooldown values, which override RootInteraction defaults.

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| Cooldown | `InteractionCooldown` | No | Cooldown configuration. |

#### TriggerCooldown

Like ResetCooldown, but deducts one charge instead of refilling all charges.

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| Cooldown | `InteractionCooldown` | No | Cooldown configuration. |

#### InteractionCooldown Fields

Used by ResetCooldown and TriggerCooldown:

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| Id | `String` | No | Cooldown id. If omitted, uses the RootInteraction's cooldown id. |
| Cooldown | `Float` (Default: 0.0) | No | Duration of the cooldown in seconds. |
| Charges | `Array` of `Float` | No | List of charge build times in seconds. Array length = max charges. |
| InterruptRecharge | `Boolean` (Default: false) | No | If true, charge time resets when charge count changes. |
| ClickBypass | `Boolean` (Default: false) | No | If true, RootInteractions keyed to this cooldown can still activate when on cooldown if triggered by a distinct key/button press. |

---

### Combo Chains

A rich combo chaining system where successive inputs produce different attacks and special moves.

#### Chaining

**Does NOT inherit SimpleInteraction** -- no Failed field. Its Next field works differently (see below).

Each execution with the same `ChainId` advances through the `Next` list. Progress is based on ChainId, not the interaction itself -- two different Chaining interactions can share a combo chain (e.g., dual-wield daggers sharing a light-light-heavy combo).

**Flags:** Named flags can be activated on a ChainId via ChainFlag. When a flag is active, its interaction runs instead of the current `Next` entry (but `Next` list position still advances). Only one flag can be active at a time.

**Reset:** Progress resets (and flags clear) when CancelChain runs or when `ChainingAllowance` time elapses without the ChainId being triggered.

All effects apply to User entity only. Non-players can use chaining but flags do not work for them. No special failure conditions.

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| ChainId | `String` | No | Key shared across Chaining interactions. Auto-generated if omitted. |
| ChainingAllowance | `Double` (Default: 0.0) | No | Seconds since last ChainId trigger before all state resets. |
| Next | `AssetArray` of `Interaction` | Yes | List of interactions to cycle through. |
| Flags | `Map` (Key: `String`, Value: `Interaction`) | No | Flag name to interaction. Overrides `Next` when active. |

#### CancelChain

Resets all progress and flag data for the provided ChainId. No special failure conditions.

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| ChainId | `String` | Yes | The ChainId to reset. |

#### ChainFlag

Activates a flag for the provided ChainId. Overwrites any existing flag. **No effect for non-players.** No special failure conditions.

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| ChainId | `String` | Yes | The ChainId to set the flag on. |
| Flag | `String` | Yes | The flag name to raise. |

---

### Charging

#### Charging

**Does NOT inherit SimpleInteraction** -- Next works differently (see below). Adds its own Failed field.

Delays while the input button is held. When released (or forced to end), the chain continues based on hold duration. Only works properly with client-triggered chains.

The `Next` field is a map from `Float` thresholds (seconds) to Interactions. On release, the largest threshold below the actual hold time determines which interaction runs.

**Two modes:**
- `AllowIndefiniteHold: true` -- charges until input is released (bow behavior)
- `AllowIndefiniteHold: false` -- ends automatically at the largest threshold and immediately executes that interaction (food consumption behavior)

**Cooldown pitfall with `AllowIndefiniteHold: false`:** If charge duration exceeds 0.35s, the default cooldown expires before the chain finishes, causing immediate re-trigger on held input. Fix: add a `Simple` interaction with `RunTime: 0.35` at the end, or configure a longer RootInteraction cooldown.

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| FailOnDamage | `Boolean` (Default: false) | No | Fails immediately if User takes damage. |
| CancelOnOtherClick | `Boolean` (Default: true) | No | Fails immediately on another button/key press. |
| Forks | `Map` (Key: `InteractionType`, Value: `RootInteraction`) | No | Input of listed types during charge forks the mapped RootInteraction instead of usual handling. **Fires even if CancelOnOtherClick is true.** |
| Failed | `Asset` (`Interaction`) | No | Executed if this interaction failed. |
| AllowIndefiniteHold | `Boolean` (Default: false) | No | If true, charges until input release. If false, ends at largest `Next` threshold. |
| DisplayProgress | `Boolean` (Default: true) | No | If true, shows a progress bar below the cursor. |
| Next | `Map` (Key: `Float`, Value: `Interaction`) | No | Duration thresholds to interaction branches. |
| MouseSensitivityAdjustmentTarget | `Float` (Default: 1.0) | No | Mouse sensitivity multiplier gradually applied while charging. |
| MouseSensitivityAdjustmentDuration | `Float` (Default: 1.0) | No | Seconds until `MouseSensitivityAdjustmentTarget` is fully applied. |
| Delay | `ChargingDelay` | No | Pushback on charge progress when User takes damage. |

**ChargingDelay Fields**

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| MinHealth | `Float` (Default: 0.0) | No | Damage as % of max health (0.0-1.0). Hits below this deal no pushback; hits at this deal `MinDelay` pushback. |
| MaxHealth | `Float` (Default: 0.0) | No | Damage as % of max health (0.0-1.0). Hits at or above this deal `MaxDelay` pushback. |
| MinDelay | `Float` (Default: 0.0) | No | Pushback seconds at `MinHealth` damage. Scales linearly to `MaxDelay`. |
| MaxDelay | `Float` (Default: 0.0) | No | Pushback seconds at `MaxHealth` damage. |
| MaxTotalDelay | `Float` (Default: 0.0) | No | Maximum total pushback across the entire charge, regardless of hit count. |

#### Wielding

Inherits from **Charging** (not SimpleInteraction), but Next and Failed work like SimpleInteraction. Does NOT inherit all Charging fields -- see the field list below.

Drives blocking behavior. While executing, attacks against the Owner entity conditionally trigger effects. Always holds indefinitely until input release or failure.

**Angled wielding:** `AngledWielding` allows damage/knockback modifiers to apply only from certain angles relative to the player. `Angle` sets the center direction (0 = front), `AngleDistance` sets the cone width. Modifiers from both base `DamageModifiers` and `AngledWielding.DamageModifiers` can stack for the same DamageCause.

Block effects (`BlockedEffects`, `BlockedInteractions`, `StaminaCost`) trigger when damage matches `DamageModifiers` OR matches `AngledWielding.DamageModifiers` from within the cone.

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| FailOnDamage | `Boolean` (Default: false) | No | Fails immediately if User takes damage. |
| CancelOnOtherClick | `Boolean` (Default: true) | No | Fails immediately on another input. |
| Forks | `Map` (Key: `InteractionType`, Value: `RootInteraction`) | No | Forks on input of listed types. Fires even if CancelOnOtherClick is true (after which this chain is cancelled). |
| Next | `Asset` (`Interaction`) | No | Executed if this interaction did not fail. |
| Failed | `Asset` (`Interaction`) | No | Executed if this interaction failed. |
| DamageModifiers | `Map` (Key: `DamageCause`, Value: `Float`) | No | Damage multipliers applied regardless of attack angle. |
| KnockbackModifiers | `Map` (Key: `DamageCause`, Value: `Float`) | No | Knockback multipliers applied regardless of attack angle. |
| AngledWielding | `AngledWielding` | No | Angle-restricted damage/knockback modifiers. |
| StaminaCost | `StaminaCost` | No | Stamina deducted when blocking matching damage. |
| BlockedEffects | `DamageEffects` | No | Effects applied when blocking matching damage. See [DamageEffects](#damageeffects-fields). |
| BlockedInteractions | `Asset` (`RootInteraction`) | No | Launched as a **brand new** (not forked) chain with Owner as both Owner and User, attacker as Target. |

**AngledWielding Fields**

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| Angle | `Float` (Default: 0.0) | No | Center angle in degrees relative to Owner. 0 = front. |
| AngleDistance | `Float` (Default: 0.0) | No | Width of the protection cone in degrees. |
| DamageModifiers | `Map` (Key: `DamageCause`, Value: `Float`) | No | Damage multipliers for attacks from within the cone. |
| KnockbackModifiers | `Map` (Key: `DamageCause`, Value: `Float`) | No | Knockback multipliers for attacks from within the cone. |

**StaminaCost Fields**

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| CostType | `CostType` (Default: `MaxHealthPercentage`) | No | `Damage`: cost = `rawDamage / Value`. `MaxHealthPercentage`: cost = `rawDamage / (MaxHealth * Value)`. |
| Value | `Float` (Default: 0.04) | No | The divisor in the cost formula. |

**CostType Values:** `MaxHealthPercentage`, `Damage`

---

### Shared Sub-Types

These types are used by multiple interaction types throughout the system.

#### DamageEffects Fields

Used by DamageEntity, Wielding (BlockedEffects), and AngledDamage.

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| ModelParticles | `Array` of `ModelParticle` | No | Particles in first-person view. |
| WorldParticles | `Array` of `WorldParticle` | No | Particles in third-person view. |
| LocalSoundEventId | `Asset` (`SoundEvent`) | No | 2D sound for the attacking player. |
| WorldSoundEventId | `Asset` (`SoundEvent`) | No | World sound at the target entity's location. |
| PlayerSoundEventId | `Asset` (`SoundEvent`) | No | 2D sound for the target player. |
| ViewDistance | `Double` (Default: 75.0) | No | Distance at which effects play for other players. |
| Knockback | `Knockback` | No | Knockback properties. See [Knockback](#knockback-fields). |
| CameraEffect | `Asset` (`CameraEffect`) | No | Camera effect for the target player. |
| StaminaDrainMultiplier | `Float` (Default: 1.0) | No | Multiplier applied to StaminaCost when blocking. |

#### Knockback Fields

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| Type | `String` | No | Knockback type: `Force`, `Point`, or `Directional`. See sub-types below. |
| Force | `Double` (Default: 0.0) | No | Force amount applied to the entity. |
| Duration | `Double` (Default: 0.0) | No | If 0, single impulse. If > 0, continuous force over this duration. |
| VelocityType | `ChangeVelocityType` (Default: `Add`) | No | `Add` to current velocity or `Set` velocity to knockback force. |
| VelocityConfig | `VelocityConfig` | No | Friction characteristics during knockback. |

**Knockback Type: Force** -- applies knockback along the provided direction.

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| Direction | `Vector3` (Default: Up) | No | Direction relative to the attack direction. |

**Knockback Type: Point** -- applies knockback laterally on XZ plane, plus Y velocity.

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| OffsetX | `Integer` (Default: 0) | No | Left/right offset perpendicular to attack direction. |
| OffsetZ | `Integer` (Default: 0) | No | Forward/backward offset along attack direction. |
| RotateY | `Integer` (Default: 0) | No | Rotation around Y axis (0 = default attack direction). |
| VelocityY | `Float` (Default: 0.0) | No | Vertical velocity added after direction/force calculation. |

**Knockback Type: Directional** -- applies knockback along attack direction, replacing vertical component.

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| RelativeX | `Float` (Default: 0.0) | No | Additional lateral force (multiplied by knockback strength). |
| RelativeZ | `Float` (Default: 0.0) | No | Additional forward force (multiplied by knockback strength). |
| VelocityY | `Float` (Default: 0.0) | No | Replaces vertical velocity. **Not multiplied by knockback strength.** |

#### VelocityConfig Fields

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| GroundResistance | `Float` (Default: 0.82) | No | Min friction on ground. 1.0 = no friction, 0.0 = total friction. |
| GroundResistanceMax | `Float` (Default: 0.0) | No | Max friction on ground. |
| AirResistance | `Float` (Default: 0.96) | No | Min friction in air. |
| AirResistanceMax | `Float` (Default: 0.0) | No | Max friction in air. |
| Threshold | `Float` (Default: 1.0) | No | Speed at which max friction is applied. Friction scales from min to max as speed goes from 0 to threshold. |
| Style | `VelocityThresholdStyle` (Default: `Linear`) | No | Friction curve shape. |

**ChangeVelocityType Values:** `Add`, `Set`

**VelocityThresholdStyle Values:** `Linear`, `Exp`

---

### Blocks

Interactions for reasoning about and modifying blocks. For client-initiated chains, the **block target** is the block under the player's cursor, and the client sends which **face** is targeted.

For NPC-simulated input, block-related interactions use the top face where relevant.

#### BlockCondition

Fails if the current target block does not match all matchers.

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| UserLatestTarget | `Boolean` (Default: false) | No | If true, refreshes target block from client cursor before executing. |
| Matchers | `Array` of `BlockMatcher` | Yes | Conditions the target block must satisfy. **Bug: technically not required, but omitting is dangerous.** **An empty array always fails.** |

**BlockMatcher Fields**

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| Block | `BlockIdMatcher` | No | Block identity conditions. |
| Face | `BlockFace` | No | Required block face (if not `None`). |
| StaticFace | `Boolean` (Default: false) | No | If false, face condition adjusts for block rotation. |

**BlockIdMatcher Fields**

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| Id | `Asset` (`BlockType`) | No | Required block type. |
| State | `String` | No | Required block state name. |
| Tag | `String` | No | Required asset tag on the block type. |

**BlockFace Values:** `Up`, `Down`, `North`, `South`, `East`, `West`, `None`

#### DestroyCondition

Fails if the User entity cannot destroy the current target block.

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| UserLatestTarget | `Boolean` (Default: false) | No | Refreshes target block from cursor. |

#### PlacementCountCondition

Checks the world count of a block type. **The block type must have the TrackedPlacement BlockEntity, otherwise the count is always zero.**

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| Block | `String` | Yes | The BlockType to count. |
| Value | `Integer` (Default: 0) | No | The threshold to compare against. |
| LessThan | `Boolean` (Default: true) | No | If true, fails when count >= Value. If false, fails when count <= Value. |

#### BreakBlock

Attempts to break the target block as the User entity (must be a player). Results depend on world settings and player state.

Fails if: no block target, `Harvest=true` and block isn't harvestable, or world settings prevent the operation. Generally does NOT fail otherwise (even if the block wasn't broken).

**Note:** With `Harvest=false` in Survival, the player executes a damage operation. The held item impacts results in unintuitive ways even when `Tool` is specified.

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| UserLatestTarget | `Boolean` (Default: false) | No | Refreshes target block from cursor. |
| Harvest | `Boolean` (Default: false) | No | If true, harvests the block and deposits drops in inventory. Generally always succeeds. |
| Tool | `String` | No | Tool type for harvest operations in Survival. **Mostly doesn't work -- held item is used for most calculations regardless.** |
| MatchTool | `Boolean` (Default: false) | No | If true (with Harvest + Survival + Tool), prevents breaking unless Tool is valid for the block. Does not fail the interaction. |

#### ChangeBlock

Changes the target block from one type to another based on a mapping. Fails if the current block type is not a key in `Changes`.

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| UserLatestTarget | `Boolean` (Default: false) | No | Refreshes target block from cursor. |
| Changes | `Map` (Key: `BlockType`, Value: `BlockType`) | No | Block type transitions. |
| WorldSoundEventId | `Asset` (`SoundEvent`) | No | Played at block position on successful change. |
| RequireNotBroken | `Boolean` (Default: false) | No | If true, fails when held item has 0 durability. |

#### ChangeState

Changes the target block's state based on a mapping. Fails if the current state is not a key in `Changes`. Use `"default"` to reference or set the null state.

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| UserLatestTarget | `Boolean` (Default: false) | No | Refreshes target block from cursor. |
| Changes | `Map` (Key: `String`, Value: `String`) | No | State name transitions. |
| UpdateBlockState | `Boolean` (Default: false) | No | If true, performs full BlockEntity refresh and notifies nearby players. |

#### CycleBlockGroup

Cycles the target block to the next type in its BlockGroup. User must be a player. Fails if the block cannot be changed. Succeeds even if the block is the only entry. Unlike most block interactions, **fails if world settings prevent block breaking.**

**Always reduces held item durability by one hit on change.** Block changes even if the held item is broken.

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| UserLatestTarget | `Boolean` (Default: false) | No | Refreshes target block from cursor. |

#### DestroyBlock

Destroys the target block without dropping anything. Succeeds if the target block exists. No failure conditions. **No additional fields.**

#### PickBlock

Equivalent to middle-click in Creative. In Survival, moves the block's item to active hotbar slot if in inventory. **Performed entirely on the client.** Cannot be used by non-players.

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| UserLatestTarget | `Boolean` (Default: false) | No | Refreshes target block from cursor. |

#### PlaceBlock

Like right-clicking with a block. Can be executed by any LivingEntity. Follows standard block placement rules.

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| BlockTypeToPlace | `Asset` (`BlockType`) | No | Block to place. If omitted, uses held item. |
| RemoveItemInHand | `Boolean` (Default: true) | No | If true (non-Creative), held item must match the block and one quantity is removed. If false, held item is ignored. |
| AllowDragPlacement | `Boolean` (Default: true) | No | If true and player-triggered, holding input + moving mouse places multiple blocks. Chain pauses until input release. |

#### RunOnBlockTypes

Searches blocks within a radius of the User entity. Forks an interaction chain for each matching block. Waits for all forked chains to complete. Fails if no blocks match or all forked chains fail.

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| Range | `Integer` | Yes | Spherical radius to search. Must be >= 1. |
| BlockSets | `AssetArray` of `BlockSet` | No | Block matchers. Only matching blocks get forked chains. |
| MaxCount | `Integer` | Yes | Maximum blocks to find. Excess matches are arbitrary. |
| Interactions | `Asset` (`RootInteraction`) | No (but effectively yes) | Forked for each matching block. Validates without it, but every execution fails. |

#### UseBlock

Executes the target block's interaction for this chain's interaction type. Wrapped in `UseBlockEvent.Pre` and `UseBlockEvent.Post` events (Pre cancellation is respected). Fails if the target block has no interaction for this type. **No additional fields.**

---

### Items

Interactions for modifying entity inventories. There is no "item target" concept -- these interact with the held item or inventory at a high level.

#### AddItem

Adds items to the User entity's inventory (prefers hotbar). No effect on non-LivingEntities. No failure states.

**BUG:** Fails validation on startup when referencing items from the same mod. Also fails silently if the User has no block target. **Use ModifyInventory instead.**

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| UserLatestTarget | `Boolean` (Default: false) | No | Refreshes target block from cursor. |
| ItemId | `Asset` (`Item`) | Yes | Item to add. |
| Quantity | `Integer` (Default: 0) | No | Quantity to add. |

#### CheckUniqueItemUsage

Checks if this interaction has run before for this User + held item combination. Succeeds on first use, fails on subsequent uses. Fails if User is not a player. Records the item id on success. **No additional fields.**

#### ChangeActiveSlot

**Does NOT inherit SimpleInteraction** -- no Next or Failed fields. `CancelOnItemChange` is always false for this interaction.

Sets the active hotbar slot. If no `TargetSlot` is specified, uses the chain's target slot (usually the already-active slot). Forks a SwapTo chain for the selected slot.

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| TargetSlot | `Integer` | No | Slot index to switch to. |

#### EquipItem

Equips the User's held item into the appropriate armor slot. Fails if the item is armor but cannot be equipped. Succeeds (no action) if the entity cannot equip armor or item isn't armor. **No additional fields.**

#### IncreaseBackpackCapacity

Increases the User's backpack capacity. **Always removes one quantity from held item.** No effect on non-players. No failure conditions even with no held item.

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| Capacity | `Integer` (Default: 1) | No | Additional capacity. **Must be 1-32767.** |

#### ModifyInventory

Full inventory modification for the User entity (must be a player; no action but no failure for non-players).

**Execution order:** ItemToRemove -> AdjustHeldItemQuantity -> ItemToAdd -> AdjustHeldItemDurability

Removals are **atomic** (all or nothing) and **required** (failure stops further actions). Additions drop excess items on the ground.

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| RequiredGameMode | `GameMode` | No | Only execute if player is in this GameMode. Does not fail on mismatch. |
| ItemToRemove | `ItemStack` | No | Removes matching item in specified quantity. Fails if insufficient. |
| AdjustHeldItemQuantity | `Integer` (Default: 0) | No | Modifies held item quantity. Negative = remove, positive = add. No held item = no effect (no fail). Insufficient quantity = fail. Excess dropped. |
| ItemToAdd | `ItemStack` | No | Adds item to inventory. Excess dropped. |
| AdjustHeldItemDurability | `Double` (Default: 0.0) | No | Adjusts held item durability. Clamped to [0, max]. Invalid values do not fail. No held item = no fail. |
| BrokenItem | `String` | No | Item id to replace held item with when durability hits 0. Use `"Empty"` for empty hand. **Not validated on startup.** Replacement failure = interaction failure. |
| NotifyOnBreak | `Boolean` (Default: false) | No | If true and item breaks, plays break sound and sends chat message. |
| NotifyOnBreakMessage | `String` | No | Translation key override for break notification. Supports `{itemName}` template. See `server.general.repair.itemBroken_Hoe` for example. |

**ItemStack Fields** (used in ModifyInventory and elsewhere)

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| Id | `Asset` (`Item`) | Yes | The item asset id. |
| Quantity | `Integer` (Default: 1) | No | Stack count. **Must be > 0.** |
| Durability | `Double` (Default: 0.0) | No | Item durability. **Cannot be negative.** |
| MaxDurability | `Double` (Default: 0.0) | No | Maximum durability. **Cannot be negative.** |
| Metadata | `Bson Document` | No | BSON metadata applied to the item stack. |
| OverrideDroppedItemAnimation | `Boolean` (Default: false) | No | Purpose unknown. |

#### PickupItem

Picks up the User's current target entity (world item) into inventory. Fails if: not a player, no target entity, or target isn't a world item. Does NOT fail if the player simply cannot pick it up. **No additional fields.**

#### RefillContainer

Fluid refill system (e.g., watering can). Fails if User is not a player. Raycasts from player head along look direction up to held item's interaction range or first solid block. Finds nearest matching fluid block, converts the held item to the fluid's item state, and optionally transforms the fluid block.

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| States | `Map` (Key: `String`, Value: `RefillState`) | Yes | Item state names mapped to refill configurations. |

**RefillState Fields**

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| AllowedFluids | `Array` of `String` | Yes | Fluid types that trigger this refill. |
| Durability | `Double` (Default: -1.0) | No | New durability on state change (negative = full). On same-state refill, added to current durability. |
| TransformFluid | `String` | No | Fluid id to transform the targeted fluid block to. |

---

### Entities

#### DamageEntity

**Does NOT inherit SimpleInteraction** -- adds its own Next, Failed, and Blocked fields.

Causes the User entity to damage the Target entity. Supports directional damage (`AngledDamage`) and body part targeting (`TargetedDamage`).

**Priority order:** TargetedDamage > first matching AngledDamage > base fields. Unspecified fields fall through to lower priority (e.g., DamageCalculator from TargetedDamage, DamageEffects from AngledDamage, Next from base).

**RunTime behavior:** DamageEntity always ends after its first tick regardless of RunTime/WaitForAnimationToFinish. However, `DamageCalculatorType: Dps` uses RunTime to calculate damage amount (RunTime 0 = no damage).

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| DamageCalculator | `DamageCalculator` | No | Base damage configuration. |
| DamageEffects | `DamageEffects` | No | Base effects on damage. See [DamageEffects](#damageeffects-fields). |
| Next | `Asset` (`Interaction`) | No | Executed if damage was dealt and none was blocked. |
| AngledDamage | `Array` of `AngledDamage` | No | Angle-dependent overrides. |
| TargetedDamage | `Map` (Key: `String`, Value: `TargetedDamage`) | Yes | Body part overrides. Must be provided but can be empty. Only known body part: `"Head"`. |
| EntityStatsOnHit | `Array` of `EntityStatOnHit` | No | Stat reductions on hit (with diminishing returns across multi-target abilities). |
| Failed | `Asset` (`Interaction`) | No | Executed if no damage was applied or all was cancelled. |
| Blocked | `Asset` (`Interaction`) | No | Executed if any damage was blocked by the target. |

**DamageCalculator Fields**

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| Type | `DamageCalculatorType` (Default: `Absolute`) | No | `Absolute` or `Dps`. |
| Class | `DamageClass` (Default: `Unknown`) | No | Attack class. Some weapon systems apply equipment modifiers based on this. |
| BaseDamage | `Map` (Key: `DamageCause`, Value: `Float`) | No | Damage per DamageCause. |
| SequentialModifierStep | `Float` (Default: 0.0) | No | 0.0-1.0. Each additional hit from the same ability reduces damage by this percentage. Shared across Selector forks. |
| SequentialModifierMinimum | `Float` (Default: 0.0) | No | 0.0-1.0. Floor for SequentialModifierStep reduction. |
| RandomPercentageModifier | `Float` (Default: 0.0) | No | Random +/- this amount * base damage added to base. |

**DamageCalculatorType Values:** `Absolute`, `Dps`

**DamageClass Values:** `Unknown`, `Light`, `Charged`, `Signature`

**AngledDamage Fields**

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| AngleDistance | `Float` (Default: 0.0) | No | Width in degrees of the matching arc around the Target. |
| Angle | `Float` (Default: 0.0) | No | Center angle in degrees around the Target. |
| DamageCalculator | `DamageCalculator` | No | Overrides base DamageCalculator if attack is from within the arc. |
| DamageEffects | `DamageEffects` | No | Overrides base DamageEffects. |
| Next | `Asset` (`Interaction`) | No | Overrides base Next. |

**TargetedDamage Fields**

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| DamageCalculator | `DamageCalculator` | No | Overrides both base and AngledDamage DamageCalculator. |
| DamageEffects | `DamageEffects` | No | Overrides both base and AngledDamage DamageEffects. |
| Next | `Asset` (`Interaction`) | No | Overrides both base and AngledDamage Next. |

**EntityStatOnHit Fields**

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| EntityStatId | `Asset` (`EntityStatType`) | Yes | Stat to affect. |
| Amount | `Float` (Default: 0.0) | No | Amount per hit. Use negative to reduce stats. |
| MultipliersPerEntitiesHit | `Array` of `Float` (Default: `[1.0, 0.6, 0.4, 0.2, 0.1]`) | No | Diminishing returns per hit. |
| MultiplierPerExtraEntityHit | `Float` (Default: 0.05) | No | Multiplier for hits beyond the array length. |

#### Projectile

Spawns a projectile at the User entity's eye position traveling in their look direction.

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| Config | `Asset` (`ProjectileConfig`) | No | Projectile configuration asset. |

> **Note:** `LaunchProjectile` is a deprecated version of this. Use `Projectile` instead.

#### RemoveEntity

Despawns the specified entity. No effect on players. No failure conditions.

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| Entity | `InteractionTarget` (Default: User) | No | The entity to despawn. |

#### SendMessage

Sends a message to the Owner entity. For players, appears in chat. For non-players, written to server logs.

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| Message | `String` | No | Text message. If provided, `Key` is ignored. |
| Key | `String` | No | Translation key for the message. Used if `Message` is not provided. |

> **Note:** `UseEntity` exists but is not in use by Hypixel and has unusual qualities. Do not use it.

---

### Stats

#### ChangeStat

Applies raw stat changes to the User entity. No special failure conditions.

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| StatModifiers | `Map` (Key: `EntityStatType`, Value: `Float`) | Yes | Stats to modify and values to apply. |
| ValueType | `ValueType` (Default: `Absolute`) | No | `Absolute` for flat values, `Percent` for percentage of (max - min). |
| Behaviour | `ChangeStatBehaviour` (Default: `Add`) | No | `Add` as delta or `Set` to fixed value. |

**ValueType Values:** `Absolute`, `Percent`

**ChangeStatBehaviour Values:** `Add`, `Set`

#### ChangeStatWithModifier

Like ChangeStat, but applies armor interaction modifier bonuses/penalties before applying.

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| StatModifiers | `Map` (Key: `EntityStatType`, Value: `Float`) | Yes | Stats to modify. |
| ValueType | `ValueType` (Default: `Absolute`) | No | Flat or percentage. |
| Behaviour | `ChangeStatBehaviour` (Default: `Add`) | No | Delta or fixed. |
| InteractionModifierId | `InteractionModifierId` | Yes | Armor modifier to apply. |

**InteractionModifierId Values:** `Dodge`

#### StatsCondition

Fails if the User entity cannot afford the specified stat costs or lacks the stats.

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| Costs | `Map` (Key: `EntityStatType`, Value: `Float`) | Yes | Required stat levels. |
| ValueType | `ValueType` (Default: `Absolute`) | No | Flat or percentage of (max - min). |
| LessThan | `Boolean` (Default: false) | No | If true, fails unless stats are at or below specified levels. If false, must be at or above. |
| Lenient | `Boolean` (Default: false) | No | If true + LessThan + min stat < 0, player can afford if current value > 0. |

#### StatsConditionWithModifier

Like StatsCondition, but applies armor interaction modifier bonuses/penalties before comparing.

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| Costs | `Map` (Key: `EntityStatType`, Value: `Float`) | Yes | Required stat levels. |
| ValueType | `ValueType` (Default: `Absolute`) | No | Flat or percentage. |
| LessThan | `Boolean` (Default: false) | No | Direction of comparison. |
| Lenient | `Boolean` (Default: false) | No | Lenient check for negative minimums. |
| InteractionModifierId | `InteractionModifierId` | Yes | Armor modifier to apply. |

---

### EntityEffects

EntityEffects are buff/debuff effects applied to LivingEntities (movement speed changes, periodic damage, etc.).

#### EffectCondition

Fails if the specified entity's EntityEffects do not match the provided conditions.

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| Entity | `InteractionTarget` (Default: User) | Yes | Entity to examine. |
| Match | `Match` (Default: `All`) | No | `All`: fails if any listed effect is missing. `None`: fails if any listed effect is present. |
| EntityEffectIds | `AssetArray` of `EntityEffect` | Yes | Effects to check. |

**Match Values:** `All`, `None`

#### ApplyEffect

Applies an EntityEffect to the specified entity. No special failure conditions.

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| Entity | `InteractionTarget` (Default: User) | Yes | Entity to apply to. |
| EffectId | `Asset` (`EntityEffect`) | Yes | The effect to apply. |

#### ClearEntityEffect

Removes an EntityEffect from the specified entity. No special failure conditions. Does not fail if the entity lacks the effect.

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| Entity | `InteractionTarget` (Default: User) | Yes | Entity to remove from. |
| EffectId | `Asset` (`EntityEffect`) | Yes | The effect to remove. |

---

### Farming

Interactions for the in-game farming system.

#### ChangeFarmingStage

Modifies the farming stage of the target block. Can increase, decrease, or set the stage. Optionally changes the StageSet. Fails if the stage change process fails, except that setting the stage to the existing stage succeeds (no-op).

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| UserLatestTarget | `Boolean` (Default: false) | No | Refreshes target block from cursor. |
| StageSet | `String` | No | Target StageSet. If omitted, uses current. |
| Increase | `Integer` | No | Increase stage by this amount. Clamped to nearest valid stage. |
| Decrease | `Integer` | No | Used if `Increase` is not provided. Clamped to nearest valid stage. |
| Stage | `Integer` (Default: -1) | No | Used if neither Increase/Decrease provided. Negative = final stage. Clamped to nearest valid stage. |

#### FertilizeSoil

Fertilizes the target block if it is unfertilized tilled soil or a farming block on unfertilized tilled soil. Fails otherwise.

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| UserLatestTarget | `Boolean` (Default: false) | No | Refreshes target block from cursor. |
| RefreshModifiers | `Array` of `String` | No | **Does nothing.** |

#### HarvestCrop

Harvests the target farming block and adds drops to User's inventory. Block is destroyed or set to post-harvest stage per farming config. **Gives full harvest drops regardless of current growth stage.** No special failure conditions (no-op if target isn't a farming block or gathering is disabled).

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| UserLatestTarget | `Boolean` (Default: false) | No | Refreshes target block from cursor. |

#### UseWateringCan

Waters tilled soil (or soil under a farming block) for the specified duration. Fails if the target is not tilled soil.

| Field Name | Type | Required? | Notes |
|------------|------|-----------|-------|
| UserLatestTarget | `Boolean` (Default: false) | No | Refreshes target block from cursor. |
| Duration | `Integer` (Default: 0) | No | Watering duration in seconds. |
| RefreshModifiers | `Array` of `String` | No | **Does nothing.** |

---

## Key Imports

```java
// Interactions
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;

// TargetUtil
import com.hypixel.hytale.server.core.util.TargetUtil;

// OpenCustomUI
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction.CustomPageSupplier;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;

// Plugin check
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.plugin.PluginManager;

// Codec
import com.hypixel.hytale.codec.builder.BuilderCodec;
```

---

## Reference

- **Plugin API source:** Verified against Hytale server source and community mod implementations
- **JSON interaction reference:** Stephen Baynham (cannibalvox) via [HytaleModding.dev](https://hytalemodding.dev)
- **Related:** `docs/hytale-api/item-states.md` (item state changes in interactions)
- **Related:** `docs/hytale-api/block-components.md` (accessing block data from UIs)
- **Related:** `docs/hytale-api/entity-effects.md` (EntityEffect assets)
- **Related:** `docs/hytale-api/player-stats.md` (EntityStatType assets)
- **Related:** `docs/hytale-api/damage-indicators.md` (DamageCause definitions)
- **Related:** `docs/HyUI/` (building UI pages)

> **TrailOfOrbis project notes:** We use `CustomPageSupplier` via `StonePickerPageSupplier` already. The `OpenCustomUI` block interaction pattern is new -- could be used for configurable realm blocks or sanctum nodes. The `DamageEntity` reference is particularly relevant for understanding how vanilla weapons calculate damage and apply knockback, which our combat module overrides via the Java API.
