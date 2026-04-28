# ECS System Types

Definitive reference for all 10 known system base classes in Hytale's ECS framework. Verified against Hytale server source and community mod implementations.

All systems operate on `EntityStore` — the per-world entity store that holds all entity components.

---

## Decision Tree: Which System Type Should I Use?

```
Need to react to damage events?
  → DamageEventSystem (intercept/modify/cancel damage in the pipeline)

Need to react to entity death?
  → DeathSystems.OnDeathSystem (fires when DeathComponent is added)

Need to react to a specific ECS event (craft, block use, slot switch, pickup)?
  → EntityEventSystem<EntityStore, T> (typed event handling)

Need per-entity per-frame processing?
  → EntityTickingSystem<EntityStore> (runs getQuery(), iterates matched entities)

Need per-entity processing at a fixed interval (0.2s, 1s, 2s)?
  → DelayedEntitySystem<EntityStore> (same as EntityTickingSystem but with configurable interval)

Need store-level processing (manual chunk iteration)?
  → TickingSystem<EntityStore> (runs every tick, you call store.forEachChunk())

Need to react when entities enter/leave the store (with Holder access)?
  → HolderSystem<EntityStore> (onEntityAdd/onEntityRemoved with Holder)

Need to react when entities enter/leave the store (with Ref + CommandBuffer)?
  → RefSystem<EntityStore> (onEntityAdded/onEntityRemove with Ref + CommandBuffer)

Need to react when a specific component is added/changed/removed?
  → RefChangeSystem<EntityStore, T> (onComponentAdded/onComponentSet/onComponentRemoved)

Need a one-shot delayed execution?
  → DelayedSystem<EntityStore> (fires once after a delay)
```

---

## 1. DamageEventSystem

**Package:** `com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem`

Intercepts the damage pipeline. `getQuery()` determines which **target** entities this system processes. The `Damage` object carries source info, amount, cause, and metadata.

**When to use:** Modifying damage amounts, cancelling damage, suppressing vanilla behavior, custom armor/resistance.

**Key methods:** `handle(index, chunk, store, cb, Damage)`, `getGroup()` (pipeline stage), `shouldProcessEvent(Damage)` (override to process cancelled events), `getDependencies()`.

**Damage pipeline order:**
1. **GatherDamageGroup** — Collect damage info (sources, amounts)
2. **FilterDamageGroup** — Apply reductions (armor, resistance)
3. **InspectDamageGroup** — Read-only inspection (indicators, UI)
4. **ApplyDamage** — Final application to health

**Code example** (TrailOfOrbis — RPGDamageSystem, simplified):

```java
public class RPGDamageSystem extends DamageEventSystem {

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any(); // Process damage to ALL entities
    }

    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(
            // After vanilla gathers damage, before vanilla applies armor
            new SystemGroupDependency<>(Order.AFTER, DamageModule.get().getGatherDamageGroup()),
            new SystemGroupDependency<>(Order.BEFORE, DamageModule.get().getFilterDamageGroup()),
            new SystemDependency<>(Order.BEFORE, DamageSystems.ApplyDamage.class)
        );
    }

    @Override
    public void handle(int index, ArchetypeChunk<EntityStore> chunk,
            Store<EntityStore> store, CommandBuffer<EntityStore> cb, Damage damage) {
        // Cancel vanilla damage, apply RPG-calculated damage directly to health
        damage.setCancelled(true);
        // ... calculate and apply custom damage ...
    }
}
```

**Advanced patterns:**

- Override `shouldProcessEvent(Damage)` returning `true` to process cancelled damage events
- Override `getGroup()` to place in a specific pipeline stage (e.g., `DamageModule.get().getInspectDamageGroup()`)

**Damage metadata:** Use `MetaKey` for passing data between damage systems:

```java
public static final MetaKey<Boolean> WAS_CRITICAL =
    Damage.META_REGISTRY.registerMetaObject(d -> Boolean.FALSE);

// In handle():
damage.setMetaObject(WAS_CRITICAL, true);
Boolean crit = damage.getIfPresentMetaObject(WAS_CRITICAL);
```

**Source:** Verified in TrailOfOrbis and community mods.

---

## 2. DeathSystems.OnDeathSystem

**Package:** `com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems.OnDeathSystem`

Fires when a `DeathComponent` is added to an entity (i.e., the entity dies). This is a specialized `RefChangeSystem` under the hood. The `getQuery()` filters which dying entities you care about.

**When to use:** Loot drops on death, XP awards, death recap, realm completion tracking, stone drops, death penalties.

**Key method:**
- `onComponentAdded(Ref, DeathComponent, Store, CommandBuffer)` — Called when entity dies

```java
public class XpGainSystem extends DeathSystems.OnDeathSystem {

    @Override
    public Query<EntityStore> getQuery() {
        // Match ALL deaths — filter to non-player mobs in handler
        return Archetype.empty();
    }

    @Override
    public void onComponentAdded(Ref<EntityStore> ref, DeathComponent death,
            Store<EntityStore> store, CommandBuffer<EntityStore> cb) {
        // Get death info
        Damage deathInfo = death.getDeathInfo();
        if (deathInfo == null) return;

        // Extract killer from damage source
        if (deathInfo.getSource() instanceof Damage.EntitySource entitySource) {
            Ref<EntityStore> killerRef = entitySource.getRef();
            PlayerRef killer = store.getComponent(killerRef, PlayerRef.getComponentType());
            if (killer != null) {
                // Award XP to killer
            }
        }
    }
}
```

**Query patterns for death systems:**
- `Archetype.empty()` — Match all entity deaths (filter in handler)
- `Player.getComponentType()` — Only player deaths
- `Archetype.of(npcType, realmMobType)` — Only realm mob deaths

**Source:** Verified in TrailOfOrbis and community mods (7 death systems).

---

## 3. EntityEventSystem\<EntityStore, T\>

**Package:** `com.hypixel.hytale.component.system.EntityEventSystem`

Typed ECS event handling. Constructor takes the event class. Fires when the specified event is invoked on matching entities.

**When to use:** Reacting to inventory changes, crafting, block interactions, slot switches, game mode changes, item pickups — any ECS event that fires per-entity.

**Key difference from EventRegistry:** ECS events fire on entities within the store. They cannot be registered via `getEventRegistry().register()` — you must use this system class.

**Constructor:** `super(EventClass.class)` — registers the event type.

```java
public class WeaponSlotChangeSystem
        extends EntityEventSystem<EntityStore, SwitchActiveSlotEvent> {

    public WeaponSlotChangeSystem() {
        super(SwitchActiveSlotEvent.class); // Register event type in constructor
    }

    @Override
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType(); // Only players
    }

    @Override
    public void handle(int index, ArchetypeChunk<EntityStore> chunk,
            Store<EntityStore> store, CommandBuffer<EntityStore> cb,
            SwitchActiveSlotEvent event) {
        PlayerRef playerRef = chunk.getComponent(index, PlayerRef.getComponentType());
        int sectionId = event.getInventorySectionId();
        byte newSlot = event.getNewSlot();
        // Handle slot change...
    }
}
```

**Known ECS event types:**
- `InventoryChangeEvent` — Player inventory modified
- `SwitchActiveSlotEvent` — Hotbar/utility slot switched
- `CraftRecipeEvent.Pre` / `CraftRecipeEvent.Post` — Before/after crafting
- `ChangeGameModeEvent` — Game mode changed
- `UseBlockEvent.Pre` — Block interaction (chests, crafting tables)
- `DamageBlockEvent` — Block damaged
- `DropItemEvent.Drop` — Item dropped
- `PlaceBlockEvent` — Block placed
- `BreakBlockEvent` — Block broken
- `InteractivelyPickupItemEvent` — Player picks up item entity

**Custom ECS events:** Define a class extending `CancellableEcsEvent`, dispatch with `store.invoke(ref, event)`, handle with an `EntityEventSystem` for that type. See [Custom ECS Events](#custom-ecs-events) below.

**Source:** Verified in TrailOfOrbis and community mods (8 event systems).

---

## 4. DelayedEntitySystem\<EntityStore\>

**Package:** `com.hypixel.hytale.component.system.tick.DelayedEntitySystem`

Per-entity tick with a configurable interval. Constructor takes `float seconds`. Same signature as `EntityTickingSystem` but only fires at the specified interval instead of every frame.

**When to use:** Periodic per-entity checks where every-tick granularity is wasteful — buff expiration (every 1s), aura pulse (every 0.5s), status checks (every 2s).

```java
public class BuffExpirationSystem extends DelayedEntitySystem<EntityStore> {

    public BuffExpirationSystem() {
        super(2.0f); // Check every 2 seconds
    }

    @Override
    public Query<EntityStore> getQuery() {
        return BuffComponent.getComponentType();
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk,
            Store<EntityStore> store, CommandBuffer<EntityStore> cb) {
        // dt is the actual elapsed time since last call (~2.0s)
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        BuffComponent buff = store.getComponent(ref, BuffComponent.getComponentType());
        if (buff != null && buff.isExpired()) {
            cb.removeComponent(ref, BuffComponent.getComponentType());
        }
    }
}
```

**Source:** Verified in community mods (intervals: 0.2s, 1s, 2s).

---

## 5. HolderSystem\<EntityStore\>

**Package:** `com.hypixel.hytale.component.system.HolderSystem`

Entity lifecycle with `Holder` — direct component access via `holder.get(Type)`. Fires on entity add/remove.

**When to use:** One-time setup on spawn (stat modifiers, classification, nameplates), cleanup on removal.

**vs RefSystem:** Holder gives direct reads, no CommandBuffer. Use HolderSystem for reads, RefSystem when you need deferred mutations.

```java
public class MobScalingSystem extends HolderSystem<EntityStore> {

    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.of(npcType, statMapType, transformType);
    }

    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(
            // Must run AFTER vanilla sets base health
            new SystemDependency<>(Order.AFTER, BalancingInitialisationSystem.class)
        );
    }

    @Override
    public void onEntityAdd(Holder<EntityStore> holder, AddReason reason,
            Store<EntityStore> store) {
        if (reason != AddReason.SPAWN && reason != AddReason.LOAD) return;

        NPCEntity npc = holder.get(npcType);
        EntityStatMap statMap = holder.get(statMapType);
        TransformComponent transform = holder.get(transformType);

        // Apply stat modifiers
        statMap.addModifier(healthStatIndex,
            new StaticModifier("RPG_HEALTH", Modifier.Type.PERCENT, 2.5f));
    }

    @Override
    public void onEntityRemoved(Holder<EntityStore> holder, RemoveReason reason,
            Store<EntityStore> store) {
        // Cleanup if needed
    }
}
```

**AddReason values:** `SPAWN`, `LOAD`, `TELEPORT`, `TRANSFER`
**RemoveReason values:** `REMOVE`, `UNLOAD`, `TELEPORT`, `TRANSFER`

**Source:** Verified in TrailOfOrbis and community mods (4 holder systems).

---

## 6. EntityTickingSystem\<EntityStore\>

**Package:** `com.hypixel.hytale.component.system.tick.EntityTickingSystem`

Per-entity per-frame tick (~20 TPS). Framework iterates matching entities — you process one entity per `tick()` call.

**When to use:** Health regen, ailment DoT, timer countdowns, UI tracking — anything requiring per-tick per-entity granularity.

```java
public class AilmentTickSystem extends EntityTickingSystem<EntityStore> {

    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.of(playerRefType, statMapType);
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk,
            Store<EntityStore> store, CommandBuffer<EntityStore> cb) {
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        PlayerRef playerRef = store.getComponent(ref, playerRefType);
        UUID uuid = playerRef.getUuid();

        // Apply DoT damage: magnitude * dt for smooth per-tick damage
        float dotDamage = tracker.tickEntity(uuid, dt);
        if (dotDamage > 0) {
            EntityStatMap statMap = store.getComponent(ref, statMapType);
            float health = statMap.get(HEALTH_INDEX).get();
            statMap.setStatValue(EntityStatMap.Predictable.SELF, HEALTH_INDEX,
                Math.max(0f, health - dotDamage));
        }
    }
}
```

**Tips:**
- If every-tick precision is wasteful, accumulate `dt` and check intervals manually, or use `DelayedEntitySystem` instead.
- Override `isParallel(chunkSize, taskCount)` returning `false` to force sequential execution (avoids race conditions with shared state).

**Source:** Verified in TrailOfOrbis and community mods (8 ticking systems).

---

## 7. TickingSystem\<EntityStore\>

**Package:** `com.hypixel.hytale.component.system.tick.TickingSystem`

Store-level tick — runs once per game tick, not per entity. You manually iterate via `store.forEachChunk()`.

**When to use:** Deferred processing (spawn queues), batch operations, rate-limited tasks.

```java
public class DeferredSpawnSystem extends TickingSystem<EntityStore> {
    private int tickCounter = 0;

    @Override
    public void tick(float dt, int tick, Store<EntityStore> store) {
        if (++tickCounter >= 10) {
            tickCounter = 0;
            spawnManager.processOneSpawn(store);
        }
    }
}
```

**With entity iteration** — implement `QuerySystem<EntityStore>` for chunk filtering:

```java
public class MobRegenerationSystem extends TickingSystem<EntityStore>
    implements QuerySystem<EntityStore> {

    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.of(scalingType, statMapType);
    }

    @Override
    public void tick(float dt, int tick, Store<EntityStore> store) {
        store.forEachChunk(tick, (chunk, cb) -> {
            for (int i = 0; i < chunk.size(); i++) {
                MobScalingComponent scaling = chunk.getComponent(i, scalingType);
                if (scaling == null) continue;
                // ... apply regen ...
            }
        });
    }
}
```

Without `QuerySystem`, `forEachChunk` iterates ALL chunks.

**Source:** Verified in TrailOfOrbis and community mods (3 ticking systems).

---

## 8. RefChangeSystem\<EntityStore, T\>

**Package:** `com.hypixel.hytale.component.system.RefChangeSystem`

Fires when a specific component type is added, set (modified), or removed from an entity. More granular than HolderSystem/RefSystem — reacts to component-level changes.

**When to use:** Buff applied/removed, equipment changed, status effect toggled.

```java
public class EquipmentChangeTracker
        extends RefChangeSystem<EntityStore, EquipmentComponent> {

    public EquipmentChangeTracker() {
        super(EquipmentComponent.getComponentType());
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }

    @Override
    public void onComponentSet(Ref<EntityStore> ref, EquipmentComponent equipment,
            Store<EntityStore> store, CommandBuffer<EntityStore> cb) {
        // Equipment was modified — recalculate stats
        PlayerRef player = store.getComponent(ref, PlayerRef.getComponentType());
        recalculateStats(player.getUuid());
    }

    @Override
    public void onComponentAdded(Ref<EntityStore> ref, EquipmentComponent equipment,
            Store<EntityStore> store, CommandBuffer<EntityStore> cb) { }

    @Override
    public void onComponentRemoved(Ref<EntityStore> ref, EquipmentComponent equipment,
            Store<EntityStore> store, CommandBuffer<EntityStore> cb) { }
}
```

**Note:** `DeathSystems.OnDeathSystem` is a specialized `RefChangeSystem<EntityStore, DeathComponent>` that only exposes `onComponentAdded`.

**Source:** Verified in community mods.

---

## 9. RefSystem\<EntityStore\>

**Package:** `com.hypixel.hytale.component.system.RefSystem`

Entity lifecycle with `Ref` + `CommandBuffer`. Same trigger as HolderSystem (entity add/remove) but provides deferred mutation via CommandBuffer.

**When to use:** Entity spawn/removal where you need to mutate the store (remove entities, add/remove components on other entities).

```java
public class RealmPassiveNPCRemover extends RefSystem<EntityStore> {

    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(NPCEntity.getComponentType());
    }

    @Override
    public void onEntityAdded(Ref<EntityStore> ref, AddReason reason,
            Store<EntityStore> store, CommandBuffer<EntityStore> cb) {
        World world = store.getExternalData().getWorld();
        if (world == null || !world.getName().startsWith("instance-realm_")) return;

        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        RPGMobClass classification = classificationService.classify(npc);

        if (classification == RPGMobClass.PASSIVE) {
            cb.removeEntity(ref, RemoveReason.REMOVE); // Deferred removal
        }
    }

    @Override
    public void onEntityRemove(Ref<EntityStore> ref, RemoveReason reason,
            Store<EntityStore> store, CommandBuffer<EntityStore> cb) {
        // No cleanup needed
    }
}
```

**Source:** Verified in TrailOfOrbis and community mods (3 ref systems).

---

## 10. DelayedSystem\<EntityStore\>

**Package:** `com.hypixel.hytale.component.system.DelayedSystem`

One-shot delayed execution on the store. Unlike `DelayedEntitySystem` (periodic per-entity), this fires once after a configured delay at the store level.

**When to use:** Deferred initialization, delayed store-wide cleanup, one-time setup after a delay.

```java
public class DelayedInitSystem extends DelayedSystem<EntityStore> {

    public DelayedInitSystem() {
        super(5.0f); // Fire once after 5 seconds
    }

    @Override
    public void tick(float dt, int tick, Store<EntityStore> store) {
        // Runs once after 5 second delay
        // Initialize late-binding resources
    }
}
```

**Source:** Verified in community mods.

---

## Registration

All systems are registered through `getEntityStoreRegistry()` in the plugin's `onEnable()`:

```java
// Direct instantiation (most common)
getEntityStoreRegistry().registerSystem(new RPGDamageSystem(this));

// No-arg constructor
getEntityStoreRegistry().registerSystem(new WeaponSlotChangeSystem());

// Store reference for later access
regenerationSystem = new RegenerationTickSystem();
getEntityStoreRegistry().registerSystem(regenerationSystem);
```

**Registration order does not determine execution order.** Execution order is controlled by dependencies (see below). However, register all systems in `onEnable()` before the server starts ticking.

---

## Dependencies

Dependencies control execution order between systems. Declared via `getDependencies()`.

```java
@Override
public Set<Dependency<EntityStore>> getDependencies() {
    return Set.of(
        // Order relative to a specific system
        new SystemDependency<>(Order.AFTER, BalancingInitialisationSystem.class),

        // Order relative to an entire system group (damage pipeline)
        new SystemGroupDependency<>(Order.BEFORE, DamageModule.get().getFilterDamageGroup()),

        // Run first in the entire chain
        RootDependency.first()
    );
}
```

**Types:** `SystemDependency` (vs specific system), `SystemGroupDependency` (vs system group), `RootDependency.first()` / `RootDependency.last()` (absolute ordering).

---

## Query Patterns

Queries filter which entities a system processes.

```java
// Single component type — entities with this component
Player.getComponentType()

// Match ALL entities
Query.any()

// Match entities with ANY components (empty archetype)
Archetype.empty()

// Multiple required components (AND)
Archetype.of(playerRefType, statMapType)
Query.and(playerRefType, playerType)
Query.and(playerRefType, Query.and(playerType, uuidType)) // 3+ components

// Component type as query (shorthand for single-component filter)
return PlayerRef.getComponentType(); // Same as Query.and(playerRefType)
```

---

## Component Registration

Components must be registered before systems that use them.

### Transient components (runtime-only, not saved)

```java
ComponentType<EntityStore, MobScalingComponent> type =
    getEntityStoreRegistry().registerComponent(
        MobScalingComponent.class,
        MobScalingComponent::new  // Factory for new instances
    );
```

### Persistent components (saved to disk via codec)

```java
ComponentType<EntityStore, SavedComponent> type =
    getEntityStoreRegistry().registerComponent(
        SavedComponent.class,
        "saved_component",        // Serialization name
        SavedComponent.CODEC       // Codec for serialization
    );
```

**Rule:** Register components in `onEnable()` BEFORE registering any systems that query them.

---

## Custom ECS Events

Define custom events extending `CancellableEcsEvent`, dispatch with `store.invoke(ref, event)`, handle with `EntityEventSystem<EntityStore, YourEvent>`.

```java
// 1. Define
public class SignalEvent extends CancellableEcsEvent {
    private final String data;
    public SignalEvent(String data) { this.data = data; }
    public String getData() { return data; }
}

// 2. Dispatch
store.invoke(ref, new SignalEvent("activate"));

// 3. Handle (via EntityEventSystem — see section 3)
```

**Source:** Verified in community mods.

---

## System Type Summary

| # | Class | Scope | Frequency | Has Query | Has CommandBuffer |
|---|-------|-------|-----------|-----------|-------------------|
| 1 | `DamageEventSystem` | Per-damage-event | On damage | Yes | Yes |
| 2 | `DeathSystems.OnDeathSystem` | Per-death | On death | Yes | Yes |
| 3 | `EntityEventSystem<S, T>` | Per-entity-event | On event | Yes | Yes |
| 4 | `DelayedEntitySystem<S>` | Per-entity | Fixed interval | Yes | Yes |
| 5 | `HolderSystem<S>` | Per-entity lifecycle | On add/remove | Yes | No (Holder) |
| 6 | `EntityTickingSystem<S>` | Per-entity | Every tick | Yes | Yes |
| 7 | `TickingSystem<S>` | Store-level | Every tick | Optional* | No** |
| 8 | `RefChangeSystem<S, T>` | Per-component-change | On change | Yes | Yes |
| 9 | `RefSystem<S>` | Per-entity lifecycle | On add/remove | Yes | Yes |
| 10 | `DelayedSystem<S>` | Store-level | One-shot | No | No** |

\* Implement `QuerySystem<EntityStore>` to add query support.
\** Access via `store.forEachChunk(tick, (chunk, cb) -> { ... })`.
