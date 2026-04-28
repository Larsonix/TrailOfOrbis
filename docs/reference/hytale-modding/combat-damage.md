# Combat, Damage & Death

Definitive reference for Hytale's combat pipeline, death system, interaction chains, and projectiles. Verified against Hytale server source and community mod implementations.

---

## Damage Pipeline (3 Stages)

All damage flows through a 3-stage ECS pipeline managed by `DamageModule`. Custom systems extend `DamageEventSystem` and declare which stage they belong to via `getGroup()`.

### Stage 1: Gather (Early)

```java
DamageModule.get().getGatherDamageGroup()
```

Cancel damage before computation. Vanilla: `FallDamagePlayers`, `FallDamageNPCs`, `OutOfWorldDamage`, `CanBreathe`. **Use for:** Level-gating weapons, DOT tick systems, cancelling specific sources.

### Stage 2: Filter (Mid)

```java
DamageModule.get().getFilterDamageGroup()
```

Modify damage amounts -- the primary injection point. Vanilla: `PlayerDamageFilterSystem`, `FilterUnkillable`, `ArmorDamageReduction`, `WieldingDamageReduction`, `SequenceModifier`. **Use for:** Lifesteal, thorns, damage reduction, elemental resistance, accessories, crit modifiers.

### Stage 3: Inspect (Post)

```java
DamageModule.get().getInspectDamageGroup()
```

Read-only observation. Vanilla: `ApplyDamage`, `DamageArmor`, `DamageStamina`, `PlayerHitIndicators`, `EntityUIEvents`, `ApplyKnockback`. **Use for:** DPS tracking, analytics, burn/slow on-hit effects, indicator suppression.

### Vanilla System Order (30 systems)

```
 1. OrderGatherFilter         16. RecordLastCombat
 2. ApplyDamage               17. ApplyParticles
 3. CanBreathe                18. ApplySoundEffects
 4. OutOfWorldDamage          19. HitAnimation
 5. FallDamagePlayers         20. TrackLastDamage
 6. FallDamageNPCs            21. DamageArmor
 7. FilterPlayerWorldConfig   22. DamageStamina
 8. FilterNPCWorldConfig      23. DamageAttackerTool
 9. FilterUnkillable          24. PlayerHitIndicators
10. PlayerDamageFilterSystem  25. ReticleEvents
11. WieldingDamageReduction   26. EntityUIEvents
12. WieldingKnockbackReduce   27. ApplyKnockback
13. ArmorKnockbackReduction   28. ApplyPlayerKnockback
14. ArmorDamageReduction      29. DeathSystems.ClearHealth
15. HackKnockbackValues       30. SequenceModifier
```

### Writing a DamageEventSystem

```java
public class MyDamageSystem extends DamageEventSystem {

    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getFilterDamageGroup();
    }

    @Override
    public Query<EntityStore> getQuery() { return Query.any(); }

    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(new SystemDependency<>(Order.BEFORE, DamageSystems.ArmorDamageReduction.class));
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                       @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> cb, @Nonnull Damage damage) {
        // Your logic
    }

    // Override to also process cancelled events (default skips them)
    @Override
    protected boolean shouldProcessEvent(@Nonnull Damage damage) { return true; }
}
```

**Between-stage positioning** (TrailOfOrbis `RPGDamageSystem` pattern):

```java
return Set.of(
    new SystemGroupDependency<>(Order.AFTER, DamageModule.get().getGatherDamageGroup()),
    new SystemGroupDependency<>(Order.BEFORE, DamageModule.get().getFilterDamageGroup()),
    new SystemDependency<>(Order.BEFORE, DamageSystems.ApplyDamage.class)
);
```

---

## The Damage Object

### Core API

| Method | Description |
|--------|-------------|
| `damage.getAmount()` / `.setAmount(float)` | Read/write current damage |
| `damage.getInitialAmount()` | Original damage (immutable) |
| `damage.isCancelled()` / `.setCancelled(boolean)` | Cancel damage |
| `damage.getCause()` | `DamageCause` |
| `damage.getSource()` | `Damage.Source` (see subtypes) |

### Damage Sources

```java
// Entity (player or NPC)
Damage.EntitySource es = (Damage.EntitySource) damage.getSource();
Ref<EntityStore> attackerRef = es.getRef();

// Projectile (extends EntitySource — check instanceof FIRST)
Damage.ProjectileSource ps = (Damage.ProjectileSource) damage.getSource();
Ref<EntityStore> projectileRef = ps.getProjectile();
Ref<EntityStore> shooterRef = ps.getRef();

// Other sources: Damage.EnvironmentSource, Damage.CommandSource, Damage.NULL_SOURCE
```

**Critical:** `ProjectileSource extends EntitySource`. Always check `instanceof ProjectileSource` before `instanceof EntitySource`.

### Creating & Applying Damage

```java
Damage damage = new Damage(Damage.NULL_SOURCE, DamageCause.PHYSICAL, amount);
// Or with source: new Damage(new Damage.EntitySource(attackerRef), cause, amount)

DamageSystems.executeDamage(targetRef, commandBuffer, damage);
DamageSystems.executeDamage(targetRef, componentAccessor, damage);  // overload
```

### DamageCause

```java
DamageCause cause = DamageCause.getAssetMap().getAsset("Physical");
```

Built-in: `PHYSICAL`, `PROJECTILE`, `COMMAND`, `DROWNING`, `ENVIRONMENT`, `FALL`, `OUT_OF_WORLD`, `SUFFOCATION`.

Key properties: `getId()`, `getInherits()` (parent for resistance chains), `isDurabilityLoss()`, `isStaminaLoss()`, `doesBypassResistances()`, `getDamageTextColor()`.

### Custom Metadata (MetaKey)

Tag damage events with arbitrary data (DOT, proc, thorns guard, etc.):

```java
// Register (static, once at class load)
public static final MetaKey<Boolean> MY_TAG =
    Damage.META_REGISTRY.registerMetaObject(d -> Boolean.FALSE);

// Without default (returns null if absent)
public static final MetaKey<Boolean> MY_TAG =
    Damage.META_REGISTRY.registerMetaObject();

damage.putMetaObject(MY_TAG, Boolean.TRUE);          // write
Boolean val = damage.getIfPresentMetaObject(MY_TAG);  // read
boolean has = damage.hasMetaObject(MY_TAG);            // check
```

**Built-in MetaKeys:** `Damage.HIT_LOCATION` (Vector4d), `Damage.HIT_ANGLE` (Float), `Damage.IMPACT_PARTICLES`, `Damage.IMPACT_SOUND_EFFECT`, `Damage.CAMERA_EFFECT`, `Damage.DEATH_ICON` (String), `Damage.BLOCKED` (Boolean), `Damage.STAMINA_DRAIN_MULTIPLIER` (Float), `Damage.KNOCKBACK_COMPONENT` (KnockbackComponent), `DamageCalculatorSystems.DAMAGE_SEQUENCE` (DamageSequence).

### Thorns Guard Pattern

Prevent infinite recursion when reflecting damage. Use a MetaKey as a guard:

```java
public static final MetaKey<Boolean> THORNS_REFLECT =
    Damage.META_REGISTRY.registerMetaObject();

// In handler: skip reflected damage
if (damage.hasMetaObject(THORNS_REFLECT)) return;

// When reflecting:
Damage thorns = new Damage(new Damage.EntitySource(defenderRef), DamageCause.PHYSICAL, amt);
thorns.putMetaObject(THORNS_REFLECT, Boolean.TRUE);
DamageSystems.executeDamage(attackerRef, commandBuffer, thorns);
```

Source: Verified in community mod implementations.

---

## Vanilla Damage Formula

```
FINAL = round(
  ((BASE * TYPE_SCALE * (1 +/- RANDOM) * BROKEN_PENALTY) + ARMOR_FLAT_ENHANCE)
  * ARMOR_MULT_ENHANCE * SEQUENTIAL_MOD * (1 - ARMOR_RESIST_MULT) * WIELDING_MOD
  - ARMOR_RESIST_FLAT
)
```

| Component | Source |
|-----------|--------|
| `BASE_DAMAGE` | `DamageCalculator.baseDamage[DamageCause]` |
| `TYPE_SCALE` | DPS: `duration * base`, ABSOLUTE: `base` |
| `RANDOM` | `DamageCalculator.randomPercentageModifier` |
| `SEQUENTIAL_MOD` | `max(1 - step * hits, minimum)` |
| `ARMOR_RESIST_*` | `ItemArmor.damageResistanceValues` (flat + mult), inheritance chain |
| `WIELDING_MOD` | Block/parry state (0.0-1.0) |

`CalculationType.ADDITIVE` = `v + a`; `CalculationType.MULTIPLICATIVE` = `v * a`.

---

## Death System

When health reaches zero, a `DeathComponent` is added. Extend `DeathSystems.OnDeathSystem` to react.

### Detecting Death

```java
public class MyDeathHandler extends DeathSystems.OnDeathSystem {
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(Player.getComponentType());  // or NPCEntity.getComponentType()
    }

    @Override
    public void onComponentAdded(@Nonnull Ref<EntityStore> ref,
            @Nonnull DeathComponent deathComponent,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> cb) {
        Damage killingBlow = deathComponent.getDeathInfo();
        Damage.Source killer = killingBlow.getSource();
    }
}
```

### Key Operations

```java
// Suppress vanilla drops
deathComponent.setItemsLossMode(DeathConfig.ItemsLossMode.NONE);

// Spawn custom drops
Holder<EntityStore>[] drops = ItemComponent.generateItemDrops(store, items, pos, rot);
commandBuffer.addEntities(drops, AddReason.SPAWN);

// Programmatic kill
DeathComponent.tryAddComponent(commandBuffer, entityRef, damage);

// Get killer
if (deathComponent.getDeathInfo().getSource() instanceof Damage.EntitySource es) {
    Ref<EntityStore> killerRef = es.getRef();
}
```

### Ordering

```java
return Set.of(
    new SystemDependency<>(Order.BEFORE, NPCDamageSystems.DropDeathItems.class)
    // or: new SystemDependency<>(Order.BEFORE, DeathSystems.DropPlayerDeathItems.class)
);
```

### XP-on-Kill Pattern

Use `EntityTickingSystem` (not `OnDeathSystem`) with a guard component to prevent double-processing:

```java
public class KillXpSystem extends EntityTickingSystem<EntityStore> {
    private final Query<EntityStore> query = Query.and(
        Archetype.of(DeathComponent.getComponentType()),
        Query.not(Archetype.of(xpAwardedType))
    );
    private final Set<Dependency<EntityStore>> deps = Set.of(
        new SystemGroupDependency<>(Order.AFTER, DamageModule.get().getInspectDamageGroup())
    );

    @Override
    public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk,
                     Store<EntityStore> store, CommandBuffer<EntityStore> cb) {
        DeathComponent death = chunk.getComponent(index, DeathComponent.getComponentType());
        Damage damage = death.getDeathInfo();
        // Award XP, then mark processed:
        cb.ensureAndGetComponent(chunk.getReferenceTo(index), xpAwardedType);
    }
}
```

Source: Verified in community mod implementations.

---

## Interaction Chains

### Core Access

```java
ComponentType<EntityStore, InteractionManager> imType =
    InteractionModule.get().getInteractionManagerComponent();
InteractionManager manager = store.getComponent(entityRef, imType);
Map<InteractionType, InteractionChain> chains = manager.getChains();
```

### InteractionChain API

| Method | Description |
|--------|-------------|
| `chain.getType()` | `InteractionType.Primary`, `Secondary`, etc. |
| `chain.getServerState()` | `InteractionState.NotFinished`, `Finished`, etc. |
| `chain.getInitialRootInteraction().getId()` | Weapon/item type string |
| `chain.getTimeShift()` / `.setTimeShift(float)` | Time acceleration (0 = normal) |
| `chain.flagDesync()` | Force resync to client |

### Attack Speed via TimeShift

```java
for (InteractionChain chain : manager.getChains().values()) {
    if (chain.getType() != InteractionType.Primary
        && chain.getType() != InteractionType.Secondary) continue;
    if (chain.getServerState() != InteractionState.NotFinished) continue;
    chain.setTimeShift(attackSpeedMultiplier - 1.0f);  // 0.5 = 1.5x speed
    chain.flagDesync();
}
```

### Chain Management

`manager.tryStartChain(...)`, `.startChain(...)`, `.cancelChains()`, `.initChain(...)`, `.queueExecuteChain(...)`.

### Other

- **Detect weapon from damage:** `damage.getIfPresentMetaObject(DamageCalculatorSystems.DAMAGE_SEQUENCE)`
- **Synthetic context:** `InteractionContext.forProxyEntity(ref, commandBuffer)`
- **Root lookup:** `RootInteraction.getRootInteractionOrUnknown(id)`

### Custom Interactions

```java
public class MyAction extends SimpleInstantInteraction {
    public static final BuilderCodec<MyAction> CODEC =
        BuilderCodec.builder(MyAction.class, MyAction::new).build();

    @Override
    protected void firstRun(InteractionType type, InteractionContext ctx,
                            CooldownHandler cooldownHandler) { /* logic */ }
}

// Register in setup():
getCodecRegistry(Interaction.CODEC).register("MyAction", MyAction.class, MyAction.CODEC);
```

---

## Projectiles

### Spawning Programmatically

```java
TimeResource time = store.getResource(TimeResource.getResourceType());
Holder<EntityStore> holder = ProjectileComponent.assembleDefaultProjectile(
    time, "MyProjectileConfig", origin, rotation);

ProjectileComponent pc = holder.getComponent(ProjectileComponent.getComponentType());
if (pc.getProjectile() == null) pc.initialize();

pc.shoot(holder, shooterUuid, origin.getX(), origin.getY(), origin.getZ(),
         rotation.getYaw(), rotation.getPitch());
store.addEntity(holder, AddReason.SPAWN);
```

Source: Verified in community mod implementations.

### Config Lookup

```java
ProjectileConfig config = ProjectileConfig.getAssetMap().getAsset("My_Config");
```

### JSON Schema (abbreviated)

Config fields: `LaunchForce` (velocity), `Physics` (gravity, drag, bounce), `Interactions` (on-hit damage/effects/sounds), `Lifetime`, `Model`/`Particles`.

### Projectile Damage

Projectiles fire through the standard pipeline with `Damage.ProjectileSource`. They bypass `DamageSequence` -- damage is applied via `executeDamage` directly.

---

## Damage Types

| DamageCause ID | Inherits | Notes |
|----------------|----------|-------|
| `Physical` | -- | Melee attacks |
| `Elemental` | -- | Base for elemental subtypes |
| `Ice` | `Elemental` | Ice damage |
| `Projectile` | -- | Ranged attacks |
| `Fall` | -- | `bypassResistances: false` |

Custom causes via JSON, lookup: `DamageCause.getAssetMap().getAsset(id)`. Armor resistance inherits: `Slash -> Physical -> All` via `ArmorResistanceModifiers.inheritedParentId`.

---

## Key Imports

```java
// Pipeline
import com.hypixel.hytale.server.core.modules.entity.damage.*;  // Damage, DamageCause,
    // DamageEventSystem, DamageModule, DamageSystems, DamageCalculatorSystems
import com.hypixel.hytale.server.core.meta.MetaKey;
// Death
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.asset.type.gameplay.DeathConfig;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
// Interactions
import com.hypixel.hytale.server.core.entity.{InteractionChain, InteractionContext, InteractionManager};
import com.hypixel.hytale.server.core.modules.interaction.InteractionModule;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.{SimpleInstantInteraction,
    RootInteraction, Interaction};
import com.hypixel.hytale.protocol.{InteractionState, InteractionType};
// Projectiles
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.modules.projectile.config.ProjectileConfig;
```

---

## Source Mods (Evidence Base)

| Mod | Patterns Demonstrated |
|-----|-----------------------|
| **TrailOfOrbis** | Full RPG pipeline (between-stage positioning, MetaKey, indicator suppression, death recap, attack speed time shift, projectile stats, vanilla drop suppression) |
| **Community mods** | Filter systems, damage modifiers, DOT, stat mods, death drop overrides, projectile spawning, interaction chain timing, animation sync |
