# Monster Modifier System — Implementation Plan

> **Status**: Implementation-ready architecture
> **Date**: 2026-05-10
> **Prerequisites**: `MonsterModifierDesign.md` (design spec), `MonsterModifierCapabilities.md` (engine capabilities)
> **Architecture source**: 4 parallel research agents mapped every relevant pattern in the codebase

---

## 1. Architecture Overview

The modifier system follows Trail of Orbis conventions exactly: Manager pattern, YAML-driven config, ECS components with BuilderCodec, HolderSystem + TickingSystem, RPGEntityEffect for visuals, and ServiceRegistry for cross-system access.

### Package Structure

```
src/main/java/io/github/larsonix/trailoforbis/mobs/modifiers/
├── ModifierType.java              # Enum — all 21 modifier definitions with metadata
├── ModifierCategory.java          # Enum — A through F categories
├── ModifierTier.java              # Enum — TIER_1 through TIER_4 with level gates
├── MobModifierConfig.java         # YAML config — all tunable parameters
├── MobModifierManager.java        # Central manager — lifecycle, init, shutdown
├── MobModifierRoller.java         # Spawn-time rolling — pool selection, exclusions
├── MobModifierApplier.java        # Applies visuals + stats + nameplate on spawn
├── MobModifierEffectRegistry.java # Pre-cached EntityEffects per modifier type
├── MobModifierComponent.java      # ECS component — stores active modifiers on entity
├── MobModifierTickSystem.java     # ECS TickingSystem — behavioral modifiers (regen, enrage, auras)
├── MobModifierDeathHandler.java   # Death triggers — volatile, rallying, summoner cleanup
└── MobModifierService.java        # Public API interface for ServiceRegistry
```

**11 files total.** Each has a single responsibility. No file exceeds what's needed.

### Why This Structure

| Class | Follows Pattern From | Rationale |
|-------|---------------------|-----------|
| `ModifierType` | `AilmentType` | Enum with per-type metadata, factory methods, display utilities |
| `MobModifierConfig` | `MobScalingConfig` | SnakeYAML POJO with defaults, snake_case setters, validation |
| `MobModifierManager` | `MobScalingManager` | Standard manager template (constructor → initialize → shutdown) |
| `MobModifierComponent` | `MobScalingComponent` | BuilderCodec ECS component, registered in setup() |
| `MobModifierEffectRegistry` | `MobSpeedEffectManager` | Pre-cache EntityEffects, batch register with asset store |
| `MobModifierTickSystem` | `MobRegenerationSystem` | QuerySystem + TickingSystem for chunk iteration |
| `MobModifierDeathHandler` | `CombatTriggerHandler` | On-death event processing |

---

## 2. Initialization Order

The modifier system slots into the existing phase chain:

```
Phase 6.5:  MobScalingManager.initialize()           ← already exists
Phase 6.6:  MobModifierManager.initialize()           ← NEW (depends on MobScalingManager)
Phase 6.9:  registerEcsSystems()                      ← register MobModifierTickSystem here
```

### setup() Phase (Lightweight, No I/O)

```java
// In TrailOfOrbis.setup(), after existing component registrations:
mobModifierComponentType = getEntityStoreRegistry().registerComponent(
    MobModifierComponent.class,
    "trailoforbis:MobModifierComponent",
    MobModifierComponent.CODEC
);
```

### start() Phase 6.6

```java
// After MobScalingManager (Phase 6.5), before registerEcsSystems (Phase 6.9):
mobModifierManager = new MobModifierManager(this, configManager, mobScalingManager);
if (!mobModifierManager.initialize()) {
    LOGGER.atSevere().log("MobModifierManager initialization failed");
}
ServiceRegistry.register(MobModifierService.class, mobModifierManager);
```

### registerEcsSystems() Addition

```java
// In registerEcsSystems(), alongside other mob systems:
getEntityStoreRegistry().registerSystem(new MobModifierTickSystem(this));
```

### shutdown() (Reverse Order)

```java
// Before MobScalingManager shutdown:
if (mobModifierManager != null) {
    mobModifierManager.shutdown();
}
```

---

## 3. Data Model

### ModifierType Enum

Mirrors `AilmentType` — each value carries its full definition:

```java
public enum ModifierType {
    // Category A: Stat-Based (Tier 1)
    HARDENED(ModifierCategory.STAT, ModifierTier.TIER_1,
        "Hardened", "#8B7355", "#A0926B", "Stoneskin",
        new StatBonus().armor(0.50)),
    VIGOROUS(ModifierCategory.STAT, ModifierTier.TIER_1,
        "Vigorous", "#2D5A27", "#4A8B3F", null,
        new StatBonus().maxHp(0.50)),
    FIERCE(ModifierCategory.STAT, ModifierTier.TIER_1,
        "Fierce", "#8B2500", "#CC3300", null,
        new StatBonus().damage(0.35)),
    SWIFT(ModifierCategory.STAT, ModifierTier.TIER_1,
        "Swift", "#88CCFF", "#CCECFF", null,
        new StatBonus().speed(0.40)),
    RESOLUTE(ModifierCategory.STAT, ModifierTier.TIER_1,
        "Resolute", "#3A3A3A", "#5A5A5A", null,
        new StatBonus().knockbackResist(1.0)),

    // Category B: Elemental (Tier 2)
    BLAZING(ModifierCategory.ELEMENTAL, ModifierTier.TIER_2,
        "Blazing", "#FF4400", "#FFAA00", "Burn",
        new StatBonus().elementDamage(ElementType.FIRE, 0.30).ailmentChance(0.20)),
    FROZEN(ModifierCategory.ELEMENTAL, ModifierTier.TIER_2,
        "Frozen", "#44AADD", "#CCECFF", "Freeze",
        new StatBonus().elementDamage(ElementType.WATER, 0.30).ailmentChance(0.20)),
    THUNDEROUS(ModifierCategory.ELEMENTAL, ModifierTier.TIER_2,
        "Thunderous", "#DDAA00", "#FFFF44", null,
        new StatBonus().elementDamage(ElementType.LIGHTNING, 0.30).ailmentChance(0.20)),
    VENOMOUS(ModifierCategory.ELEMENTAL, ModifierTier.TIER_2,
        "Venomous", "#225522", "#66AA44", "Poison",
        new StatBonus().elementDamage(ElementType.VOID, 0.30).ailmentChance(0.20)),

    // Category C: Tactical Counter (Tier 3)
    REFLECTIVE(...), WARDING(...), EVASIVE(...),

    // Category D: Behavioral (Tier 2 + Tier 4)
    ENRAGED(...), REGENERATING(...), SHADOW_STEP(...), SUMMONER(...),

    // Category E: Aura/Area (Tier 3 + Tier 4)
    PACK_LEADER(...), FROST_AURA(...), RALLYING(...),

    // Category F: Death Trigger (Tier 4)
    VOLATILE(...);

    // Fields
    private final ModifierCategory category;
    private final ModifierTier tier;
    private final String displayName;
    private final String tintBottom;  // Hex color
    private final String tintTop;     // Hex color
    private final String modelVfxId;  // Vanilla VFX asset or null
    private final StatBonus statBonus;

    // Utility methods
    public boolean hasBehavior() { return category == BEHAVIORAL || category == AURA || category == DEATH; }
    public boolean hasDeathTrigger() { return this == VOLATILE || this == RALLYING || this == VENOMOUS; }
    public boolean hasAura() { return this == PACK_LEADER || this == FROST_AURA; }
    public boolean requiresTick() { return hasBehavior() || hasAura(); }
    public static List<ModifierType> availableAtLevel(int level) { ... }
    public static ModifierType fromName(String name) { ... }
}
```

### StatBonus (Value Object)

Immutable, composable stat bonus definition:

```java
public record StatBonus(
    double armorPercent,
    double maxHpPercent,
    double damagePercent,
    double speedPercent,
    double knockbackResist,
    double evasionChance,
    double elementalResistPercent,
    Map<ElementType, Double> elementDamagePercent,
    double ailmentChanceBonus,
    double reflectPercent
) {
    // Builder-style factory methods for enum construction
    public StatBonus armor(double pct) { return new StatBonus(pct, ...rest from this...); }
    public StatBonus maxHp(double pct) { ... }
    public StatBonus elementDamage(ElementType el, double pct) { ... }
    // etc.
}
```

### MobModifierComponent (ECS)

Follows `MobScalingComponent` pattern exactly:

```java
public class MobModifierComponent implements Component<EntityStore> {

    public static final BuilderCodec<MobModifierComponent> CODEC =
        BuilderCodec.builder(MobModifierComponent.class, MobModifierComponent::new)
            .append(new KeyedCodec<>("Modifiers", createModifierListCodec()),
                    MobModifierComponent::setModifierNames,
                    MobModifierComponent::getModifierNames)
            .add()
            .append(new KeyedCodec<>("EnrageTriggered", Codec.BOOLEAN),
                    MobModifierComponent::setEnrageTriggered,
                    MobModifierComponent::isEnrageTriggered)
            .add()
            .append(new KeyedCodec<>("SummonThreshold60", Codec.BOOLEAN),
                    MobModifierComponent::setSummonThreshold60Triggered,
                    MobModifierComponent::isSummonThreshold60Triggered)
            .add()
            .append(new KeyedCodec<>("SummonThreshold30", Codec.BOOLEAN),
                    MobModifierComponent::setSummonThreshold30Triggered,
                    MobModifierComponent::isSummonThreshold30Triggered)
            .add()
            .build();

    // Serialized fields
    private List<String> modifierNames = List.of();  // Stored as strings for forward compat
    private boolean enrageTriggered = false;
    private boolean summonThreshold60Triggered = false;
    private boolean summonThreshold30Triggered = false;

    // Non-serialized (reconstructed from names on load)
    private List<ModifierType> modifiers = List.of();
    private long lastDamageTimestamp = 0;
    private List<Ref<EntityStore>> summonedMinions = new ArrayList<>();

    // Reconstruct transient state from serialized names
    public void resolveModifiers() {
        this.modifiers = modifierNames.stream()
            .map(ModifierType::fromName)
            .filter(Objects::nonNull)
            .toList();
    }

    public boolean hasModifier(ModifierType type) {
        return modifiers.contains(type);
    }

    public int modifierCount() {
        return modifiers.size();
    }

    // ... getters, setters, clone(), getComponentType()
}
```

**Key design decisions:**
- **Store modifier names as strings**, not enum ordinals. This ensures forward compatibility — if we add/remove/reorder modifiers, existing saved entities still load correctly.
- **Threshold flags are serialized** — if a server restarts mid-fight, Summoner won't re-trigger its 60% threshold.
- **Transient state** (lastDamageTimestamp, summonedMinions) is NOT serialized — recalculated on load.

---

## 4. Config Structure

File: `src/main/resources/config/mob-modifiers.yml`

```yaml
# ===========================================================================
# Monster Modifier Configuration
# ===========================================================================
enabled: true

# ---------------------------------------------------------------------------
# Tier System
# ---------------------------------------------------------------------------
# Modifier count per mob rarity tier
modifier_count:
  elite: 1
  boss: 2
  elite_boss: 3

# Stat multipliers per tier (replaces current mob-rarity.yml values)
tier_stats:
  elite:
    hp: 1.2
    damage: 1.05
    armor: 1.1
    evasion: 1.0
    speed: 1.0
    xp: 3.0
    ailment_effectiveness: 0.85
  boss:
    hp: 2.0
    damage: 1.1
    armor: 1.2
    evasion: 1.0
    speed: 1.0
    xp: 10.0
    ailment_effectiveness: 0.7
  elite_boss:
    hp: 3.0
    damage: 1.15
    armor: 1.3
    evasion: 1.0
    speed: 1.0
    xp: 15.0
    ailment_effectiveness: 0.6

# Realm overrides
realm_tier_stats:
  elite:
    hp: 1.4
  boss:
    hp: 2.5
  elite_boss:
    hp: 3.5

# ---------------------------------------------------------------------------
# Level Gating
# ---------------------------------------------------------------------------
level_gates:
  tier_1: 1     # Stat modifiers
  tier_2: 10    # Elemental + Enraged
  tier_3: 25    # Tactical + aura
  tier_4: 40    # Complex behavioral + death

# ---------------------------------------------------------------------------
# Reward Scaling (per modifier count)
# ---------------------------------------------------------------------------
reward_scaling:
  1: { iiq: 2.0, iir: 1.5 }
  2: { iiq: 4.0, iir: 3.0 }
  3: { iiq: 7.0, iir: 5.0 }

# ---------------------------------------------------------------------------
# Exclusion Rules
# ---------------------------------------------------------------------------
# Pairs that cannot coexist on the same mob
exclusions:
  - [hardened, warding]
  - [evasive, frost_aura]

# ---------------------------------------------------------------------------
# Visual Settings
# ---------------------------------------------------------------------------
visuals:
  elite_scale: 1.15
  boss_scale: 1.30
  elite_boss_scale: 1.40
  nameplate_prefix_elite: "★ "
  nameplate_prefix_boss: "★★ "
  nameplate_prefix_elite_boss: "★★★ "

# ---------------------------------------------------------------------------
# Individual Modifier Settings
# ---------------------------------------------------------------------------
# Each modifier's tunable parameters. Adding a new modifier here
# automatically makes it available (if ModifierType enum entry exists).
modifiers:
  # --- Category A: Stat-Based ---
  hardened:
    armor_bonus_percent: 0.50
    tint_bottom: "#8B7355"
    tint_top: "#A0926B"
    model_vfx: "Stoneskin"

  vigorous:
    hp_bonus_percent: 0.50
    tint_bottom: "#2D5A27"
    tint_top: "#4A8B3F"

  fierce:
    damage_bonus_percent: 0.35
    tint_bottom: "#8B2500"
    tint_top: "#CC3300"

  swift:
    speed_bonus_percent: 0.40
    tint_bottom: "#88CCFF"
    tint_top: "#CCECFF"

  resolute:
    knockback_multiplier: 0.0
    tint_bottom: "#3A3A3A"
    tint_top: "#5A5A5A"

  # --- Category B: Elemental ---
  blazing:
    element: FIRE
    element_damage_percent: 0.30
    ailment_chance_bonus: 0.20
    trail_damage_percent: 0.05
    trail_duration_seconds: 3.0
    tint_bottom: "#FF4400"
    tint_top: "#FFAA00"
    model_vfx: "Burn"

  frozen:
    element: WATER
    element_damage_percent: 0.30
    ailment_chance_bonus: 0.20
    aura_slow_percent: 0.15
    aura_radius: 4.0
    tint_bottom: "#44AADD"
    tint_top: "#CCECFF"
    model_vfx: "Freeze"

  thunderous:
    element: LIGHTNING
    element_damage_percent: 0.30
    ailment_chance_bonus: 0.20
    strike_cooldown_seconds: 8.0
    strike_telegraph_seconds: 1.5
    strike_damage_percent: 0.50
    tint_bottom: "#DDAA00"
    tint_top: "#FFFF44"

  venomous:
    element: VOID
    element_damage_percent: 0.30
    ailment_chance_bonus: 0.20
    death_cloud_duration_seconds: 5.0
    death_cloud_dps_percent: 0.10
    tint_bottom: "#225522"
    tint_top: "#66AA44"
    model_vfx: "Poison"

  # --- Category C: Tactical Counter ---
  reflective:
    reflect_percent: 0.15
    reflect_hp_floor: 0.20
    tint_bottom: "#888888"
    tint_top: "#CCCCCC"

  warding:
    elemental_resist_bonus: 0.50
    tint_bottom: "#442266"
    tint_top: "#7744AA"

  evasive:
    dodge_chance: 0.25
    tint_bottom: "#445566"
    tint_top: "#778899"

  # --- Category D: Behavioral ---
  enraged:
    hp_threshold: 0.40
    damage_bonus: 0.30
    speed_bonus: 0.30
    scale_increase: 0.10
    tint_bottom: "#CC0000"
    tint_top: "#FF2200"

  regenerating:
    heal_percent_per_second: 0.02
    idle_delay_seconds: 4.0
    tint_bottom: "#22CC22"
    tint_top: "#88FF88"

  shadow_step:
    cooldown_seconds: 8.0
    frontal_arc_degrees: 90
    tint_bottom: "#1A0033"
    tint_top: "#330066"
    model_vfx: "Portal_Teleport"

  summoner:
    threshold_1_hp: 0.60
    threshold_2_hp: 0.30
    summon_count: 2
    max_summons: 4
    summon_level_offset: -5
    tint_bottom: "#888822"
    tint_top: "#CCCC44"

  # --- Category E: Aura/Area ---
  pack_leader:
    damage_bonus: 0.20
    speed_bonus: 0.15
    aura_radius: 12.0
    tint_bottom: "#AA8800"
    tint_top: "#FFCC44"
    model_vfx: "Drop_Rare"

  frost_aura:
    slow_percent: 0.20
    aura_radius: 6.0
    tint_bottom: "#3388BB"
    tint_top: "#AADDEE"

  rallying:
    hp_bonus: 0.30
    damage_bonus: 0.25
    buff_duration_seconds: 20.0
    buff_radius: 15.0
    tint_bottom: "#660000"
    tint_top: "#AA2222"

  # --- Category F: Death Trigger ---
  volatile:
    charge_delay_seconds: 2.0
    explosion_damage_percent: 0.80
    explosion_radius: 5.0
    tint_bottom: "#FF6600"
    tint_top: "#FFCC00"
```

### Config Class Pattern

```java
public class MobModifierConfig {
    private boolean enabled = true;
    private Map<String, Integer> modifier_count = Map.of("elite", 1, "boss", 2, "elite_boss", 3);
    private Map<String, Integer> level_gates = Map.of("tier_1", 1, "tier_2", 10, "tier_3", 25, "tier_4", 40);
    private List<List<String>> exclusions = List.of(List.of("hardened", "warding"), List.of("evasive", "frost_aura"));
    private Map<String, ModifierSettings> modifiers = new LinkedHashMap<>();

    // Nested per-modifier settings
    public static class ModifierSettings {
        // All fields optional with defaults — only relevant fields set per modifier
        private double armor_bonus_percent = 0;
        private double hp_bonus_percent = 0;
        private double damage_bonus_percent = 0;
        private double speed_bonus_percent = 0;
        private double knockback_multiplier = 1.0;
        private String element = null;
        private double element_damage_percent = 0;
        private double ailment_chance_bonus = 0;
        private double reflect_percent = 0;
        private double elemental_resist_bonus = 0;
        private double dodge_chance = 0;
        private double hp_threshold = 0;
        private double heal_percent_per_second = 0;
        private double idle_delay_seconds = 0;
        private double cooldown_seconds = 0;
        private double aura_radius = 0;
        private double slow_percent = 0;
        private double explosion_damage_percent = 0;
        private double explosion_radius = 0;
        private double charge_delay_seconds = 0;
        private String tint_bottom = null;
        private String tint_top = null;
        private String model_vfx = null;
        // ... getters, setters
    }

    public boolean isEnabled() { return enabled; }
    public int getModifierCount(String tier) { return modifier_count.getOrDefault(tier, 0); }
    public int getLevelGate(ModifierTier tier) { ... }
    public boolean isExcluded(ModifierType a, ModifierType b) { ... }
    public ModifierSettings getSettings(ModifierType type) { ... }
    public void validate() { ... }
}
```

---

## 5. System Architecture

### MobModifierManager (Lifecycle Owner)

```
Constructor(plugin, configManager, mobScalingManager)
    └── Store dependencies only (no I/O)

initialize()
    ├── Load config: configManager.getMobModifierConfig()
    ├── Validate config
    ├── Create MobModifierRoller(config)
    ├── Create MobModifierApplier(config, effectRegistry)
    ├── Create MobModifierEffectRegistry()
    ├── effectRegistry.initialize()  ← pre-create + register EntityEffects
    ├── Create MobModifierDeathHandler(config, plugin)
    ├── Register death event: DamageEvent → check isDying → handle death triggers
    └── Return true

shutdown()
    ├── effectRegistry.shutdown()  ← clear cached effects
    └── Clear state
```

### MobModifierEffectRegistry (Visual Effect Cache)

Follows `MobSpeedEffectManager` pattern:

```
initialize()
    ├── For each ModifierType:
    │   ├── Create RPGEntityEffect with:
    │   │   ├── ID: "rpg_mod_{type.name().toLowerCase()}" (e.g., "rpg_mod_blazing")
    │   │   ├── ApplicationEffects:
    │   │   │   ├── tint (bottom + top from config)
    │   │   │   ├── modelVFX (from config, nullable)
    │   │   │   ├── particles (per-modifier particle set)
    │   │   │   ├── speed multiplier (for Swift)
    │   │   │   └── knockback multiplier (for Resolute)
    │   │   ├── infinite: true (permanent on mob)
    │   │   ├── overlapBehavior: IGNORE (don't replace if somehow reapplied)
    │   │   └── name: null (hide from status effect UI)
    │   └── Add to pendingEffects list
    └── Batch register: EntityEffect.getAssetStore().loadAssets(PACK_KEY, pendingEffects)

getEffect(ModifierType) → RPGEntityEffect
    └── Cached lookup from pre-created map
```

### Integration Into MobScalingSystem (Spawn Hook)

The modifier system hooks into the EXISTING `MobScalingSystem.onEntityAdd()` — no new HolderSystem needed:

```
MobScalingSystem.onEntityAdd(holder, reason, store)
    ├── ... existing scaling logic ...
    ├── Determine tier (normal/elite/boss/elite_boss)
    ├── IF tier has modifiers:
    │   ├── MobModifierRoller.roll(mobLevel, tier)
    │   │   ├── Determine modifier count from tier
    │   │   ├── Filter pool by level gate
    │   │   ├── Random selection (no duplicates)
    │   │   ├── Check exclusion list (reroll if needed, max 3 attempts)
    │   │   └── Return List<ModifierType>
    │   ├── Create MobModifierComponent with modifier names
    │   ├── holder.addComponent(modifierType, component)
    │   ├── MobModifierApplier.apply(holder, modifiers, store)
    │   │   ├── Apply stat bonuses to MobStats during generation
    │   │   ├── Apply EntityEffects (visual per modifier)
    │   │   ├── Apply EntityScaleComponent (tier-based)
    │   │   ├── Set Nameplate text with modifier names
    │   │   └── Apply speed/knockback multipliers
    │   └── ... continue with scaled stats ...
    └── ... existing stat application ...
```

**Critical: Stats are baked into MobStats at generation time.** Since `MobStats` is an immutable record, modifier stat bonuses (Hardened +50% armor, Fierce +35% damage) are applied DURING `MobStatFactory.generate()`, not after. This means:

```java
// In MobStatFactory.generate() — new parameter:
public MobStats generate(int level, double distanceBonus, ..., List<ModifierType> modifiers) {
    // ... existing pool calculation ...
    double armor = convertStat(...) * getModifierMultiplier(modifiers, "armor");
    double damage = convertStat(...) * getModifierMultiplier(modifiers, "damage");
    // ... rest of generation with modifier-aware values ...
}
```

### MobModifierTickSystem (Behavioral Modifiers)

ECS TickingSystem + QuerySystem for chunk iteration:

```java
@Dependency(of = BalancingInitialisationSystem.class, order = Order.AFTER)
public class MobModifierTickSystem extends TickingSystem<EntityStore>
    implements QuerySystem<EntityStore> {

    private static final float TICK_INTERVAL = 0.5f; // 2 ticks/second
    private float accumulator = 0;

    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.of(
            plugin.getMobScalingComponentType(),
            plugin.getMobModifierComponentType()
        );
    }

    @Override
    public void tick(float dt, int tick, Store<EntityStore> store) {
        accumulator += dt;
        if (accumulator < TICK_INTERVAL) return;
        accumulator -= TICK_INTERVAL;

        store.forEachChunk(tick, (chunk, commandBuffer) -> {
            processChunk(chunk, store, TICK_INTERVAL);
        });
    }

    private void processChunk(ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store, float dt) {
        int size = chunk.size();
        for (int i = 0; i < size; i++) {
            MobModifierComponent mods = chunk.getComponent(i, modifierType);
            MobScalingComponent scaling = chunk.getComponent(i, scalingType);
            Ref<EntityStore> mobRef = chunk.getReferenceTo(i);

            // Only process mobs with behavioral modifiers
            if (!mods.hasAnyBehavioral()) continue;

            // ENRAGED: Check HP threshold
            if (mods.hasModifier(ModifierType.ENRAGED) && !mods.isEnrageTriggered()) {
                float currentHp = getCurrentHp(mobRef, store);
                float maxHp = getMaxHp(mobRef, store);
                if (currentHp / maxHp <= config.getEnrageThreshold()) {
                    triggerEnrage(mobRef, mods, store);
                }
            }

            // REGENERATING: Heal if idle
            if (mods.hasModifier(ModifierType.REGENERATING)) {
                long now = System.currentTimeMillis();
                if (now - mods.getLastDamageTimestamp() > config.getRegenIdleDelay()) {
                    applyRegen(mobRef, store, dt);
                }
            }

            // SUMMONER: Check HP thresholds
            if (mods.hasModifier(ModifierType.SUMMONER)) {
                checkSummonerThresholds(mobRef, mods, scaling, store);
            }

            // PACK_LEADER: Buff nearby allies
            if (mods.hasModifier(ModifierType.PACK_LEADER)) {
                refreshPackLeaderAura(mobRef, store);
            }

            // FROST_AURA: Slow nearby players
            if (mods.hasModifier(ModifierType.FROST_AURA)) {
                refreshFrostAura(mobRef, store);
            }

            // THUNDEROUS: Lightning strike cooldown
            if (mods.hasModifier(ModifierType.THUNDEROUS)) {
                checkLightningStrike(mobRef, mods, store);
            }

            // BLAZING: Fire trail
            if (mods.hasModifier(ModifierType.BLAZING)) {
                updateFireTrail(mobRef, store);
            }
        }
    }
}
```

**Performance**: Only iterates entities with BOTH MobScalingComponent AND MobModifierComponent (the Query archetype). Normal mobs are skipped entirely. At 0.5s tick interval, processing 5-10 modified mobs per realm is negligible.

### MobModifierDeathHandler

Hooks into the existing death detection in RPGDamageSystem:

```
On mob death (isDying flag set):
    ├── Check MobModifierComponent exists
    ├── Read modifier list
    ├── For each modifier with death trigger:
    │   ├── VOLATILE:
    │   │   ├── Schedule delayed explosion (2.0s via world.execute timer)
    │   │   ├── Apply charging visual effect immediately
    │   │   ├── On timer: AoE damage at position, spawn explosion particles
    │   │   └── Clean up visual
    │   ├── VENOMOUS:
    │   │   ├── Spawn poison cloud particle at death position
    │   │   ├── Schedule 5s tick system for cloud damage
    │   │   └── Clean up after duration
    │   ├── RALLYING:
    │   │   ├── Find non-elite/non-boss entities within radius
    │   │   ├── Apply 20s buff EntityEffect (damage + HP)
    │   │   └── Buff auto-expires via effect duration
    │   └── SUMMONER (cleanup):
    │       ├── Iterate tracked summon refs
    │       ├── Despawn all tracked minions
    │       └── Clear ref list
    └── Continue normal death processing
```

### Combat Pipeline Hooks

Modifiers integrate at specific phases WITHOUT modifying the pipeline itself:

| Modifier | Where It Acts | How |
|----------|--------------|-----|
| **Stat mods** (Hardened, Vigorous, Fierce, Swift, Resolute) | MobStats generation (pre-combat) | Multipliers baked into MobStatFactory output |
| **Elemental** (Blazing, Frozen, Thunderous, Venomous) | MobStats.elementalStats (pre-combat) | Flat elemental damage added during generation |
| **Ailment chance** | CombatAilmentApplicator (Phase 6) | `statusEffectChance` already read from attacker stats — modifier bonus baked into MobStats |
| **Reflective** | CombatRecoveryProcessor (Phase 7) | Extend thorns system — read reflect% from MobStats |
| **Warding** | MobStats.elementalStats (pre-combat) | Elemental resistances baked into generation |
| **Evasive** | AvoidanceProcessor (Phase 3) | New mob-side dodge check — read dodge chance from MobModifierComponent |
| **Enraged** | MobModifierTickSystem (runtime) | Applies stat EntityEffect on HP threshold |
| **Shadow Step** | MobModifierTickSystem (runtime) | BodyMotionTeleport on frontal hit detection |

**Key insight**: Most modifiers need ZERO changes to the combat pipeline. Stats are baked at generation, and the existing pipeline reads those stats. Only Evasive (new avoidance check) and Reflective (extend thorns) need pipeline modifications.

---

## 6. Extensibility

### Adding a New Modifier (Checklist)

1. Add enum value to `ModifierType.java` with category, tier, display name, tint colors, VFX, stat bonus
2. Add config section to `mob-modifiers.yml` with tunable parameters
3. If stat-based only: **done** — MobStatFactory already reads StatBonus from the enum
4. If behavioral: add case in `MobModifierTickSystem.processChunk()`
5. If death trigger: add case in `MobModifierDeathHandler`
6. If visual effect: add particle definition in `MobModifierEffectRegistry`

**Steps 1-2 are always required. Steps 3-6 are conditional.** A pure stat modifier (like Hardened) requires only enum + config — zero code changes in any system.

### Adding a New Modifier Category

1. Add value to `ModifierCategory` enum
2. Add new tier entries if needed
3. Systems already iterate `ModifierType.values()` — new category is automatically included

### Adding a New Modifier Tier

1. Add value to `ModifierTier` enum with level gate
2. Add `tier_N: <level>` entry to config
3. MobModifierRoller automatically includes new tier in pool filtering

---

## 7. Performance Budget

| Operation | Frequency | Cost | Budget |
|-----------|-----------|------|--------|
| Modifier rolling at spawn | Once per elite/boss spawn | ~0.1ms (random + exclusion check) | Negligible |
| Effect registration | Once at startup | ~50ms (21 effects × batch register) | Startup only |
| EntityEffect application | Once per spawn per modifier | ~0.5ms per effect | Negligible |
| Tick system (behavioral) | 2/sec, only modified mobs | ~0.05ms per mob per tick | 5-10 mobs = 0.5ms/sec |
| Aura range check | 2/sec per aura mob | ~0.1ms (distance calc to players) | 2-3 aura mobs = 0.3ms/sec |
| Death handler | Once per modified mob death | ~1ms (AoE check + spawn/despawn) | Event-driven |
| Particle rendering | Continuous, client-side | 2-3 emitters per modifier | 15 emitters for 5 elites (< 50 limit) |

**Total overhead**: <1ms per server tick for a typical realm with 5-10 modified mobs. No performance concerns.

---

## 8. Implementation Phases

### Phase 1: Foundation (Estimate: core architecture)
- `ModifierType`, `ModifierCategory`, `ModifierTier` enums
- `MobModifierComponent` with codec
- `MobModifierConfig` + YAML file
- `MobModifierManager` (lifecycle, init, shutdown)
- `MobModifierRoller` (pool selection, exclusions)
- Component registration in setup()
- Manager initialization in start()
- **Test**: Mobs roll modifiers at spawn, component is attached with correct data, serializes/deserializes on world save/load

### Phase 2: Visuals + Stats (Estimate: visible results)
- `MobModifierEffectRegistry` (pre-cache 21 effects)
- `MobModifierApplier` (apply effects + nameplate + scale)
- Stat bonus integration into `MobStatFactory.generate()`
- Tier stat rebalancing (update mob-rarity.yml)
- **Test**: Modified mobs are visually different (tint, particles, scale, nameplate). Stats are correct (Hardened has more armor, etc.)

### Phase 3: Behavioral Modifiers
- `MobModifierTickSystem` with all behavioral checks
- Enrage trigger (HP threshold → apply EntityEffect)
- Regenerating (idle timer → heal)
- Shadow Step (frontal hit detection → BodyMotionTeleport)
- Summoner (HP threshold → NPCPlugin.spawnNPC)
- Thunderous lightning strike (cooldown → particle telegraph → damage)
- Blazing fire trail (position tracking → particle + area damage)
- **Test**: Each behavioral modifier works as designed

### Phase 4: Auras + Death + Combat Integration
- Pack Leader aura (range check → stat buff on allies)
- Frost Aura (range check → slow on players)
- `MobModifierDeathHandler` (Volatile, Rallying, Venomous cloud, Summoner cleanup)
- Evasive dodge check in AvoidanceProcessor
- Reflective extension in CombatRecoveryProcessor
- **Test**: Auras affect nearby entities correctly, death triggers fire correctly, combat hooks work

### Phase 5: Reward Integration + Polish
- Per-modifier IIQ/IIR scaling in LootCalculator
- Multi-modifier visual stacking verification
- Exclusion rule validation
- Config reload support (admin command)
- Wiki documentation updates
- **Test**: Loot scales with modifier count, all 21 modifiers work individually and in combination, no degenerate combos

---

## 9. File Summary

| File | Lines (est.) | Responsibility |
|------|-------------|---------------|
| `ModifierType.java` | 200 | Enum with 21 values, metadata, utility methods |
| `ModifierCategory.java` | 15 | 6-value enum |
| `ModifierTier.java` | 25 | 4-value enum with level gates |
| `StatBonus.java` | 80 | Immutable stat bonus record with builder factories |
| `MobModifierConfig.java` | 150 | YAML config POJO with nested ModifierSettings |
| `MobModifierManager.java` | 120 | Manager lifecycle (init, shutdown, service) |
| `MobModifierRoller.java` | 80 | Pool filtering, random selection, exclusion checks |
| `MobModifierApplier.java` | 150 | Apply visuals + stats + nameplate to entity |
| `MobModifierEffectRegistry.java` | 120 | Pre-create + cache 21 EntityEffects |
| `MobModifierComponent.java` | 100 | ECS component with codec, threshold flags |
| `MobModifierTickSystem.java` | 250 | Behavioral modifier processing (chunk iteration) |
| `MobModifierDeathHandler.java` | 150 | Death triggers (volatile, rallying, cleanup) |
| `MobModifierService.java` | 30 | Public API interface |
| `mob-modifiers.yml` | 180 | Full config file |
| **Total** | **~1,650** | Complete modifier system |

---

## 10. Integration Touchpoints (Existing Files Modified)

| File | Change | Scope |
|------|--------|-------|
| `TrailOfOrbis.java` | Add component registration (setup), manager init (start), ECS system registration, shutdown | 20 lines |
| `MobStatFactory.java` | Accept `List<ModifierType>` parameter, apply stat multipliers during generation | 30 lines |
| `MobScalingSystem.java` | After tier determination, call MobModifierRoller + MobModifierApplier | 15 lines |
| `RPGDamageSystem.java` | On death detection, call MobModifierDeathHandler; update lastDamageTimestamp | 10 lines |
| `AvoidanceProcessor.java` | Add mob-side dodge check for Evasive modifier | 15 lines |
| `CombatRecoveryProcessor.java` | Extend thorns to include Reflective modifier (with HP floor cap) | 10 lines |
| `LootCalculator.java` | Read modifier count, apply per-modifier IIQ/IIR | 10 lines |
| `mob-rarity.yml` | Update tier multipliers to new lower values | Config only |
| `ConfigManager.java` | Load mob-modifiers.yml | 5 lines |
| **Total modifications** | | **~115 lines across 9 files** |
