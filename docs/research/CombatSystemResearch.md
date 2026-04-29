# Combat System Research

Comprehensive research into Hytale's combat and damage system for RPG gear stat integration.

**Research Date**: 2026-01-23
**Status**: Complete
**Priority**: 4 (Combat System)

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Damage Calculation Pipeline](#damage-calculation-pipeline)
3. [Damage Formula](#damage-formula)
4. [StatModifier System](#statmodifier-system)
5. [Damage Events & Hooks](#damage-events--hooks)
6. [Armor & Defense System](#armor--defense-system)
7. [Combat System Architecture](#combat-system-architecture)
8. [Key Injection Points for RPG Stats](#key-injection-points-for-rpg-stats)
9. [Implementation Recommendations](#implementation-recommendations)

---

## Executive Summary

### Questions Answered

| Question | Answer |
|----------|--------|
| How is damage calculated? | Multi-stage pipeline: Base → Scaling → Random → Broken Penalty → Armor Enhancement → Sequential → Armor Reduction → Wielding → Apply |
| Where can we inject modifier bonuses? | `StatModifiersManager.recalculateEntityStatModifiers()`, `ArmorDamageReduction`, `DamageEntityInteraction` |
| How does ItemWeapon.statModifiers work? | `Int2ObjectMap<StaticModifier[]>` applied via StatModifiersManager when weapon equipped |
| Are there damage events we can hook into? | YES - `Damage` event is cancellable, amount modifiable in Filter group |
| How is armor/defense calculated? | Flat reduction then multiplicative: `(damage - flat) * (1 - multiplier)` |

### Key Findings

1. **Damage Event is Fully Hookable**: The `Damage` class extends `CancellableEcsEvent` - can cancel or modify amount
2. **StatModifier System is Extensible**: Supports ADDITIVE and MULTIPLICATIVE modifiers with keyed sources
3. **Armor Enhancement Exists**: Armor can INCREASE outgoing damage, not just reduce incoming
4. **Multiple Injection Points**: Filter group systems allow damage modification before application
5. **Metadata System**: `Damage.getMetaStore()` allows attaching custom data to damage events

---

## Damage Calculation Pipeline

### Complete Flow

```
Attack Initiated
    │
    ▼
┌─────────────────────────────────────────────────────────────┐
│ DamageEntityInteraction.attemptEntityDamage0()              │
│ File: .../interaction/config/server/DamageEntityInteraction.java │
│ Lines: 296-381                                              │
└─────────────────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────────────┐
│ DamageCalculator.calculateDamage(durationSeconds)           │
│ File: .../interaction/config/server/combat/DamageCalculator.java │
│ Lines: 72-86                                                │
│                                                             │
│ • Base Damage (per DamageCause)                            │
│ • Type Scaling (DPS vs ABSOLUTE)                           │
│ • Random Variance (±randomPercentageModifier)              │
└─────────────────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────────────┐
│ DamageCalculatorSystems.queueDamageCalculator()             │
│ File: .../entity/damage/DamageCalculatorSystems.java        │
│ Lines: 42-56                                                │
│                                                             │
│ • Apply Broken Weapon Penalty                              │
└─────────────────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────────────┐
│ Armor Enhancement Modifiers (from attacker's armor)         │
│ File: DamageEntityInteraction.java, Lines: 383-433          │
│                                                             │
│ • Flat enhancement (additive)                              │
│ • Multiplier enhancement (multiplicative)                  │
│ • Damage class bonuses (LIGHT/CHARGED/SIGNATURE)           │
└─────────────────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────────────┐
│ DAMAGE EVENT SYSTEMS (DamageSystems.java)                   │
│ File: .../entity/damage/DamageSystems.java                  │
│                                                             │
│ ┌─────────────────────────────────────────────────────────┐│
│ │ GATHER GROUP                                            ││
│ │ • FallDamageNPCs, FallDamagePlayers                    ││
│ │ • OutOfWorldDamage, CanBreathe                         ││
│ └─────────────────────────────────────────────────────────┘│
│                     │                                       │
│                     ▼                                       │
│ ┌─────────────────────────────────────────────────────────┐│
│ │ FILTER GROUP ◀── BEST INJECTION POINT                  ││
│ │ • PlayerDamageFilterSystem (spawn protection, PvP)     ││
│ │ • FilterUnkillable (invulnerability)                   ││
│ │ • ArmorDamageReduction (Lines 176-289) ◀── KEY        ││
│ │ • WieldingDamageReduction (block/parry)                ││
│ │ • SequenceModifier (sequential hit penalty)            ││
│ └─────────────────────────────────────────────────────────┘│
│                     │                                       │
│                     ▼                                       │
│ ┌─────────────────────────────────────────────────────────┐│
│ │ INSPECT GROUP                                           ││
│ │ • ApplyDamage (subtract from health) Lines: 1136-1173  ││
│ │ • DamageArmor, DamageStamina                           ││
│ │ • Effects, Sounds, Particles                           ││
│ └─────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────────────┐
│ Health <= 0?                                                │
│ YES → DeathComponent.tryAddComponent() → Death Systems      │
└─────────────────────────────────────────────────────────────┘
```

### System Registration Order

From `DamageModule.java` (Lines 51-109):

```
1.  OrderGatherFilter
2.  ApplyDamage
3.  CanBreathe
4.  OutOfWorldDamage
5.  FallDamagePlayers
6.  FallDamageNPCs
7.  FilterPlayerWorldConfig
8.  FilterNPCWorldConfig
9.  FilterUnkillable
10. PlayerDamageFilterSystem
11. WieldingDamageReduction      ◀── Filter Group
12. WieldingKnockbackReduction
13. ArmorKnockbackReduction
14. ArmorDamageReduction         ◀── Filter Group (KEY)
15. HackKnockbackValues
16. RecordLastCombat
17. ApplyParticles
18. ApplySoundEffects
19. HitAnimation
20. TrackLastDamage
21. DamageArmor
22. DamageStamina
23. DamageAttackerTool
24. PlayerHitIndicators
25. ReticleEvents
26. EntityUIEvents
27. ApplyKnockback
28. ApplyPlayerKnockback
29. DeathSystems.ClearHealth
30. SequenceModifier              ◀── Damage Sequence
```

---

## Damage Formula

### Complete Formula

```
FINAL_DAMAGE = round(
    (
        (BASE_DAMAGE * TYPE_SCALE * (1 ± RANDOM_VAR) * BROKEN_PENALTY)
        + ATTACKER_ARMOR_FLAT
    )
    * ATTACKER_ARMOR_MULT
    * SEQUENTIAL_MODIFIER
    * (1 - ARMOR_RESIST_MULT)
    * WIELDING_MODIFIER
    - ARMOR_RESIST_FLAT
)
```

### Component Breakdown

| Component | Source | Calculation |
|-----------|--------|-------------|
| `BASE_DAMAGE` | `DamageCalculator.baseDamage[DamageCause]` | Per damage type |
| `TYPE_SCALE` | `DamageCalculator.type` | DPS: `duration * base`, ABSOLUTE: `base` |
| `RANDOM_VAR` | `DamageCalculator.randomPercentageModifier` | `±percentage` |
| `BROKEN_PENALTY` | `BrokenPenalties.getWeapon()` | `1.0 - penalty` if broken |
| `ATTACKER_ARMOR_FLAT` | `ItemArmor.damageEnhancementValues[ADDITIVE]` | Sum of all armor pieces |
| `ATTACKER_ARMOR_MULT` | `ItemArmor.damageEnhancementValues[MULTIPLICATIVE]` | Product of all armor pieces |
| `SEQUENTIAL_MODIFIER` | `DamageCalculator.sequentialModifierStep/Minimum` | `max(1 - step * hits, minimum)` |
| `ARMOR_RESIST_FLAT` | `ItemArmor.baseDamageResistance + damageResistanceValues[ADDITIVE]` | Sum per damage cause |
| `ARMOR_RESIST_MULT` | `ItemArmor.damageResistanceValues[MULTIPLICATIVE]` | Sum capped at 1.0 |
| `WIELDING_MODIFIER` | Block/parry state | 0.0 to 1.0 |

### Code Reference: Armor Reduction

**File**: `DamageSystems.java`, Lines 205-212

```java
float amount = Math.max(0.0f, damage.getAmount() - (float)damageModEntry.flatModifier);
amount *= Math.max(0.0f, 1.0f - damageModEntry.multiplierModifier);

// Handle inheritance chain (e.g., Slash → Physical → All)
while (damageModEntry.inheritedParentId != null &&
       (damageModEntry = resistances.get(damageModEntry.inheritedParentId)) != null) {
    amount = Math.max(0.0f, damage.getAmount() - (float)damageModEntry.flatModifier);
    amount *= Math.max(0.0f, 1.0f - damageModEntry.multiplierModifier);
}
damage.setAmount(amount);
```

---

## StatModifier System

### Class Structure

**File**: `.../entitystats/modifier/StaticModifier.java`

```java
public class StaticModifier extends Modifier {
    protected CalculationType calculationType;  // ADDITIVE or MULTIPLICATIVE
    protected float amount;                     // The modifier value

    public float apply(float statValue) {
        return this.calculationType.compute(statValue, this.amount);
    }
}

public enum CalculationType {
    ADDITIVE {
        public float compute(float value, float amount) {
            return value + amount;  // 100 + 10 = 110
        }
    },
    MULTIPLICATIVE {
        public float compute(float value, float amount) {
            return value * amount;  // 100 * 1.1 = 110
        }
    }
}
```

### ItemWeapon StatModifiers

**File**: `.../asset/type/item/config/ItemWeapon.java`

```java
public class ItemWeapon {
    // Config format (string keys from JSON)
    @Nullable
    protected Map<String, StaticModifier[]> rawStatModifiers;

    // Resolved format (int indices for performance)
    @Nullable
    protected Int2ObjectMap<StaticModifier[]> statModifiers;

    // Stats to reset when weapon unequipped
    protected String[] rawEntityStatsToClear;
    @Nullable
    protected int[] entityStatsToClear;
}
```

### ItemArmor StatModifiers

**File**: `.../asset/type/item/config/ItemArmor.java`

```java
public class ItemArmor {
    // Defensive modifiers
    protected Map<DamageCause, StaticModifier[]> damageResistanceValues;
    protected double baseDamageResistance;

    // Offensive modifiers (when wearing this armor)
    protected Map<DamageCause, StaticModifier[]> damageEnhancementValues;
    protected Map<DamageClass, StaticModifier[]> damageClassEnhancement;

    // Stat bonuses
    protected Int2ObjectMap<StaticModifier[]> statModifiers;

    // Knockback
    protected Map<DamageCause, Float> knockbackResistances;
    protected Map<DamageCause, Float> knockbackEnhancements;
}
```

### StatModifiersManager Application Flow

**File**: `.../entity/StatModifiersManager.java`

```java
public void recalculateEntityStatModifiers(Ref<EntityStore> ref,
                                           EntityStatMap statMap,
                                           ComponentAccessor<EntityStore> componentAccessor) {
    // 1. Clear flagged stats
    for (int stat : statsToClear) {
        statMap.minimizeStatValue(EntityStatMap.Predictable.SELF, stat);
    }

    // 2. Apply effect modifiers (buffs/debuffs)
    applyEffectModifiers(statMap, calculateEffectStatModifiers(ref, componentAccessor));

    // 3. Apply armor modifiers (with broken penalties)
    applyStatModifiers(statMap, computeStatModifiers(brokenPenalties, inventory));

    // 4. Apply weapon modifiers
    addItemStatModifiers(inventory.getItemInHand(), statMap, "*Weapon_",
                         v -> v.getWeapon().getStatModifiers());

    // 5. Apply utility modifiers (if compatible)
    if (itemInHand == null || itemInHand.getItem().getUtility().isCompatible()) {
        addItemStatModifiers(inventory.getUtilityItem(), statMap, "*Utility_",
                             v -> v.getUtility().getStatModifiers());
    }
}
```

### Modifier Key Format

```
"Armor_ADDITIVE"      → Armor additive modifier
"Armor_MULTIPLICATIVE" → Armor multiplicative modifier
"*Weapon_0"           → First weapon modifier
"*Weapon_1"           → Second weapon modifier
"*Utility_0"          → First utility modifier
"Effect_ADDITIVE"     → Effect additive modifier
```

### JSON Configuration Example

**File**: `Assets/Server/Item/Items/Armor/Onyxium/Armor_Onyxium_Head.json`

```json
{
  "Armor": {
    "ArmorSlot": "Head",
    "BaseDamageResistance": 0,
    "StatModifiers": {
      "Health": [
        { "Amount": 9, "CalculationType": "Additive" }
      ],
      "Mana": [
        { "Amount": 13, "CalculationType": "Additive" }
      ]
    },
    "DamageResistance": {
      "Physical": [
        { "Amount": 0.05, "CalculationType": "Multiplicative" }
      ]
    }
  }
}
```

---

## Damage Events & Hooks

### Primary Damage Event

**File**: `.../entity/damage/Damage.java`

```java
public class Damage extends CancellableEcsEvent implements IMetaStore<Damage> {

    // Core data
    private float amount;           // Current damage (modifiable)
    private final float initialAmount;  // Original damage (immutable)
    private Source source;          // Who caused damage
    private DamageCause damageCause;    // Damage type

    // Cancellation
    public void setCancelled(boolean cancelled);
    public boolean isCancelled();

    // Modification
    public float getAmount();
    public void setAmount(float amount);

    // Metadata (extensible)
    public MetaStore<Damage> getMetaStore();
}
```

### Available Metadata Keys

```java
Damage.HIT_LOCATION          // Vector4d - Impact position
Damage.HIT_ANGLE             // Float - Angle of attack
Damage.IMPACT_PARTICLES      // Particles - Visual effects
Damage.IMPACT_SOUND_EFFECT   // SoundEffect - Impact sound
Damage.CAMERA_EFFECT         // CameraEffect - Screen shake
Damage.DEATH_ICON            // String - Kill feed icon
Damage.BLOCKED               // Boolean - Was damage blocked?
Damage.STAMINA_DRAIN_MULTIPLIER  // Float - Stamina cost
Damage.CAN_BE_PREDICTED      // Boolean - Client prediction
Damage.KNOCKBACK_COMPONENT   // KnockbackComponent - Physics
```

### Damage Source Types

```java
// Entity caused damage (player, NPC)
public class EntitySource implements Source {
    Ref<EntityStore> sourceRef;
}

// Projectile caused damage
public class ProjectileSource extends EntitySource {
    Ref<EntityStore> projectile;
}

// Command caused damage (/kill, etc.)
public class CommandSource implements Source {
    CommandSender commandSender;
}

// Environment caused damage (fall, drowning)
public class EnvironmentSource implements Source {
    String type;  // "Fall", "Drowning", etc.
}
```

### DamageCause Properties

**File**: `.../entity/damage/DamageCause.java`

```java
public class DamageCause {
    protected String id;                // "Physical", "Fire", etc.
    protected String inherits;          // Parent damage type
    protected boolean durabilityLoss;   // Damages equipment?
    protected boolean staminaLoss;      // Costs stamina?
    protected boolean bypassResistances; // Ignores armor?
    protected String damageTextColor;   // Combat text color
    protected String animationId;       // "Hurt" animation
    protected String deathAnimationId;  // "Death" animation
}
```

### Built-in Damage Causes

```
PHYSICAL      - Melee attacks
PROJECTILE    - Ranged attacks
COMMAND       - /kill, etc.
DROWNING      - Underwater
ENVIRONMENT   - Generic environmental
FALL          - Fall damage
OUT_OF_WORLD  - Void damage
SUFFOCATION   - In blocks
```

### Kill Feed Events

**File**: `.../entity/damage/event/KillFeedEvent.java`

```java
// All are cancellable
KillFeedEvent.Display         // Broadcast to all players
KillFeedEvent.DecedentMessage // Message to killed player
KillFeedEvent.KillerMessage   // Message to killer
```

### Block Damage Event

**File**: `.../event/events/ecs/DamageBlockEvent.java`

```java
public class DamageBlockEvent extends CancellableEcsEvent {
    ItemStack itemInHand;    // Tool being used
    Vector3i targetBlock;    // Block position
    BlockType blockType;     // Block type
    float currentDamage;     // Cumulative damage
    float damage;            // This tick's damage (modifiable)
}
```

---

## Armor & Defense System

### Armor Slots

**File**: `.../protocol/ItemArmorSlot.java`

```java
public enum ItemArmorSlot {
    Head(0),   // Helmet
    Chest(1),  // Chestplate
    Hands(2),  // Gauntlets
    Legs(3);   // Leggings
}
```

### Defense Calculation

**File**: `DamageSystems.java`, Lines 216-257

```java
public static Map<DamageCause, ArmorResistanceModifiers> getResistanceModifiers(
    World world, ItemContainer inventory, boolean canApplyItemStackPenalties,
    EffectControllerComponent effectControllerComponent) {

    Map<DamageCause, ArmorResistanceModifiers> result = new HashMap<>();

    // Iterate all armor slots
    for (short i = 0; i < inventory.getCapacity(); i++) {
        ItemStack itemStack = inventory.getItemStack(i);
        ItemArmor itemArmor = itemStack.getItem().getArmor();

        // Get resistance values
        Map<DamageCause, StaticModifier[]> resistances = itemArmor.getDamageResistanceValues();
        double flatResistance = itemArmor.getBaseDamageResistance();

        // Accumulate per damage cause
        for (Entry<DamageCause, StaticModifier[]> entry : resistances.entrySet()) {
            ArmorResistanceModifiers mods = result.computeIfAbsent(entry.getKey(),
                k -> new ArmorResistanceModifiers());

            for (StaticModifier modifier : entry.getValue()) {
                if (modifier.getCalculationType() == ADDITIVE) {
                    mods.flatModifier += modifier.getAmount();
                } else {
                    mods.multiplierModifier += modifier.getAmount();
                }
            }

            // Add base resistance
            mods.flatModifier += flatResistance;

            // Apply broken penalty
            if (canApplyItemStackPenalties && itemStack.isBroken()) {
                double penalty = brokenPenalties.getWeapon(0.0);
                mods.flatModifier *= (1.0 - penalty);
                mods.multiplierModifier *= (1.0 - penalty);
            }
        }
    }

    // Add effect resistances (buffs/debuffs)
    addResistanceModifiersFromEntityEffects(result, effectControllerComponent);

    return result;
}
```

### ArmorResistanceModifiers Structure

```java
public static class ArmorResistanceModifiers {
    public int flatModifier;              // Total flat reduction
    public float multiplierModifier;      // Total percentage reduction (0.0-1.0+)
    public DamageCause inheritedParentId; // For inheritance chain
}
```

### Bypass Resistances

Some damage types ignore armor entirely:

```java
// In ArmorDamageReduction system
if (!damage.getCause().doesBypassResistances() && !resistances.isEmpty()) {
    // Apply armor reduction
} else {
    // Full damage passes through
}
```

### Knockback Resistance

**File**: `DamageSystems.java`, Lines 292-343

```java
// ArmorKnockbackReduction system
float knockbackResistanceModifier = 0.0f;

for (ItemStack armor : armorContainer) {
    Map<DamageCause, Float> knockbackResistances = armor.getItem().getArmor().getKnockbackResistances();
    knockbackResistanceModifier += knockbackResistances.get(damage.getCause());
}

knockbackComponent.addModifier(Math.max(1.0f - knockbackResistanceModifier, 0.0f));
```

### Armor Durability Loss

**File**: `DamageSystems.java`, Lines 728-765

```java
// DamageArmor system - randomly damages one armor piece
if (damageCause.isDurabilityLoss()) {
    // Find non-broken armor pieces
    ShortArrayList armorPieces = findNonBrokenArmor(inventory);

    // Randomly select one and reduce durability by 3
    short slot = armorPieces.getShort(random(armorPieces.size()));
    entity.decreaseItemStackDurability(ref, armor.getItemStack(slot), -3, slot, commandBuffer);
}
```

---

## Combat System Architecture

### Attack Initiation (NPC)

**File**: `.../npc/corecomponents/combat/ActionAttack.java`

```java
public void execute() {
    // 1. Validate attack readiness
    if (CombatSupport.isExecutingAttack()) return;

    // 2. Set up aiming
    calculateAimTime();

    // 3. Get weapon interaction
    InteractionType interactionType = getInteractionType(); // Primary/Secondary/Ability1-3

    // 4. Line of sight check
    if (!hasLineOfSight(target)) return;

    // 5. Create interaction chain
    InteractionChain chain = new InteractionChain(weapon, interactionType);

    // 6. Queue attack
    queueInteraction(chain);
    CombatSupport.executingAttack = true;
}
```

### Damage Classes

**File**: `.../interaction/config/server/combat/DamageClass.java`

```java
public enum DamageClass {
    UNKNOWN,    // Default
    LIGHT,      // Light attacks (faster)
    CHARGED,    // Heavy attacks (slower, more damage)
    SIGNATURE;  // Special moves
}
```

### DamageCalculator Configuration

```java
public class DamageCalculator {
    public enum Type {
        DPS,      // Damage per second (scales with duration)
        ABSOLUTE  // Fixed damage (no scaling)
    }

    protected Type type;
    protected Map<DamageCause, Float> baseDamage;
    protected float randomPercentageModifier;    // ±variance
    protected float sequentialModifierStep;      // Reduction per hit
    protected float sequentialModifierMinimum;   // Minimum multiplier
}
```

### DamageEffects Configuration

**File**: `.../interaction/config/server/combat/DamageEffects.java`

```java
public class DamageEffects {
    protected ModelParticle[] modelParticles;     // On-model particles
    protected WorldParticle[] worldParticles;     // World particles
    protected String localSoundEventId;           // Attacker sound
    protected String worldSoundEventId;           // World sound
    protected String playerSoundEventId;          // Receiver sound
    protected double viewDistance = 75.0;         // Effect visibility
    protected Knockback knockback;                // Physics
    protected String cameraEffectId;              // Screen shake
    protected float staminaDrainMultiplier = 1.0f; // Stamina cost
}
```

### Combat Config

**File**: `.../asset/type/gameplay/CombatConfig.java`

```java
public class CombatConfig {
    protected int outOfCombatDelay = 5000;        // ms to exit combat
    protected String staminaBrokenEffect;         // Exhaustion effect
    protected boolean displayHealthBars = true;
    protected boolean displayCombatText = true;
    protected boolean disableNPCIncomingDamage = false;
    protected boolean disablePlayerIncomingDamage = false;
}
```

---

## Key Injection Points for RPG Stats

### 1. StatModifiersManager (Recommended)

**Location**: `StatModifiersManager.recalculateEntityStatModifiers()`

**Approach**: Add custom RPG stat modifiers alongside existing weapon/armor modifiers

```java
// After weapon modifiers are applied
addItemStatModifiers(itemInHand, statMap, "*Weapon_", ...);

// Add RPG gear modifiers here
addRpgGearModifiers(itemInHand, statMap, "*RpgWeapon_");
addRpgArmorModifiers(armorContainer, statMap, "*RpgArmor_");
```

**Pros**:
- Integrates with existing stat system
- Modifiers recalculated on equipment change
- Supports ADDITIVE and MULTIPLICATIVE

**Cons**:
- Modifies stats, not damage directly
- Need to map RPG stats to entity stats

### 2. ArmorDamageReduction System (Filter Group)

**Location**: `DamageSystems.ArmorDamageReduction.handle()`

**Approach**: Register custom DamageEventSystem before or after ArmorDamageReduction

```java
public class RpgDamageModification extends DamageEventSystem {
    @Override
    public void handle(int index, ArchetypeChunk<EntityStore> chunk,
                       Store<EntityStore> store, CommandBuffer<EntityStore> buffer,
                       Damage damage) {
        // Get attacker's RPG stats
        float attackBonus = getAttackerRpgBonus(damage.getSource());

        // Get defender's RPG stats
        float defenseBonus = getDefenderRpgDefense(chunk, index);

        // Modify damage
        float newDamage = (damage.getAmount() + attackBonus) * (1 - defenseBonus);
        damage.setAmount(newDamage);
    }
}
```

**Pros**:
- Direct damage modification
- Access to attacker and defender
- Can add custom damage causes

**Cons**:
- Runs every damage tick
- Need to register with DamageModule

### 3. DamageEntityInteraction (Pre-Event)

**Location**: `DamageEntityInteraction.attemptEntityDamage0()`

**Approach**: Intercept before damage event is queued

**Pros**:
- Early in pipeline
- Can modify DamageCalculator result

**Cons**:
- Harder to hook into
- Misses non-interaction damage

### 4. Damage Event Listener

**Approach**: Register EventRegistry listener for Damage events

```java
eventRegistry.register(Damage.class, damage -> {
    if (damage.isCancelled()) return;

    // Modify based on RPG stats
    float rpgBonus = calculateRpgBonus(damage);
    damage.setAmount(damage.getAmount() * rpgBonus);
});
```

**Pros**:
- Standard event pattern
- Easy to implement

**Cons**:
- May run after some systems
- Timing dependent on registration order

---

## Implementation Recommendations

### Recommended Approach: Hybrid System

1. **Use StatModifiersManager for Base Stats**
   - Add RPG stat bonuses (Strength, Intelligence) as entity stat modifiers
   - These affect base damage calculations naturally

2. **Register Custom DamageEventSystem for Combat Modifiers**
   - Insert in Filter group after ArmorDamageReduction
   - Apply percentage-based damage bonuses/reductions
   - Handle elemental damage types

3. **Extend ItemArmor/ItemWeapon Metadata**
   - Store RPG stats in BsonDocument metadata
   - Read during stat/damage calculations

### Example Implementation Structure

```java
// 1. RPG Stat Modifier System (registered in Filter group)
public class RpgCombatModifierSystem extends DamageEventSystem {

    @Override
    public void handle(int index, ArchetypeChunk<EntityStore> chunk,
                       Store<EntityStore> store, CommandBuffer<EntityStore> buffer,
                       Damage damage) {

        // Get attacker RPG data
        if (damage.getSource() instanceof EntitySource entitySource) {
            RpgStats attackerStats = getRpgStats(entitySource.getSourceRef());

            // Apply damage bonus from Strength
            float strengthBonus = attackerStats.getStrength() * 0.02f; // 2% per point
            damage.setAmount(damage.getAmount() * (1 + strengthBonus));
        }

        // Get defender RPG data
        RpgStats defenderStats = getRpgStats(chunk.getReferenceTo(index));

        // Apply damage reduction from Constitution
        float constitutionReduction = defenderStats.getConstitution() * 0.01f; // 1% per point
        damage.setAmount(damage.getAmount() * (1 - constitutionReduction));

        // Handle elemental damage
        DamageCause cause = damage.getCause();
        if (isElementalDamage(cause)) {
            float elementalResist = defenderStats.getElementalResistance(cause);
            damage.setAmount(damage.getAmount() * (1 - elementalResist));
        }
    }
}

// 2. RPG Equipment Stat Applier (in StatModifiersManager)
public class RpgEquipmentStatApplier {

    public void applyRpgModifiers(EntityStatMap statMap, Inventory inventory) {
        // Apply weapon RPG stats
        ItemStack weapon = inventory.getItemInHand();
        if (weapon != null) {
            BsonDocument rpgData = weapon.getMetadata().getDocument("rpg");
            if (rpgData != null) {
                applyRpgStatModifiers(statMap, rpgData, "*RpgWeapon_");
            }
        }

        // Apply armor RPG stats
        for (short i = 0; i < inventory.getArmor().getCapacity(); i++) {
            ItemStack armor = inventory.getArmor().getItemStack(i);
            if (armor != null) {
                BsonDocument rpgData = armor.getMetadata().getDocument("rpg");
                if (rpgData != null) {
                    applyRpgStatModifiers(statMap, rpgData, "*RpgArmor_" + i + "_");
                }
            }
        }
    }
}
```

### Key Files to Reference

| Purpose | File Path |
|---------|-----------|
| Damage Pipeline | `.../entity/damage/DamageSystems.java` |
| Damage Event | `.../entity/damage/Damage.java` |
| Damage Calculation | `.../interaction/config/server/combat/DamageCalculator.java` |
| Damage Interaction | `.../interaction/config/server/DamageEntityInteraction.java` |
| Stat Modifiers | `.../entity/StatModifiersManager.java` |
| Static Modifier | `.../entitystats/modifier/StaticModifier.java` |
| Item Armor | `.../asset/type/item/config/ItemArmor.java` |
| Item Weapon | `.../asset/type/item/config/ItemWeapon.java` |
| Damage Cause | `.../entity/damage/DamageCause.java` |
| Damage Module | `.../entity/damage/DamageModule.java` |
| Combat Config | `.../asset/type/gameplay/CombatConfig.java` |

---

## Summary

The Hytale combat system provides multiple injection points for RPG stat integration:

1. **Damage is Modifiable**: `Damage.setAmount()` allows changing damage at any point in the filter group
2. **Events are Cancellable**: `Damage.setCancelled(true)` prevents damage application
3. **Metadata is Extensible**: `Damage.getMetaStore()` allows attaching custom RPG data
4. **Stats Flow Through**: Equipment stat modifiers automatically affect damage calculations
5. **Multiple Hooks Available**: Filter group, stat recalculation, event listeners

**Recommended Strategy**:
- Store RPG stats in item BsonDocument metadata
- Apply base stat bonuses via StatModifiersManager
- Apply combat-specific bonuses via custom DamageEventSystem in Filter group
- Use DamageCause inheritance for elemental damage types

This architecture allows full RPG stat integration while preserving vanilla damage calculations and compatibility with other systems.
