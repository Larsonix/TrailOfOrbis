# Realm Modifiers

Modifiers are affixes on Realm Maps that alter gameplay, difficulty, and rewards. This system is inspired by POE's map modifiers.

## Implementation Complexity Guide

Each modifier is tagged with implementation complexity:
- **[TRIVIAL]** - Single stat multiplier or flag check
- **[EASY]** - Simple calculation or condition
- **[MEDIUM]** - Multiple systems involved, but straightforward
- **[COMPLEX]** - Requires significant new code (avoid for v1)

---

## Modifier Categories

### Monster Stat Modifiers (Prefix)
Direct stat modifications applied when monsters spawn. All are **[TRIVIAL]** - just multiply the stat.

| Modifier | Effect | Difficulty | Impl |
|----------|--------|------------|------|
| `MONSTER_DAMAGE` | Monsters deal +X% damage | +1-3 | TRIVIAL |
| `MONSTER_HEALTH` | Monsters have +X% max health | +1-3 | TRIVIAL |
| `MONSTER_SPEED` | Monsters have +X% movement speed | +1-2 | TRIVIAL |
| `MONSTER_ATTACK_SPEED` | Monsters attack X% faster | +1-2 | TRIVIAL |
| `MONSTER_ARMOR` | Monsters have +X% armor | +1-2 | TRIVIAL |
| `MONSTER_EVASION` | Monsters have +X% evasion | +1-2 | TRIVIAL |
| `MONSTER_ACCURACY` | Monsters have +X% accuracy | +1 | TRIVIAL |
| `MONSTER_CRIT_CHANCE` | Monsters have +X% crit chance | +2 | TRIVIAL |
| `MONSTER_CRIT_DAMAGE` | Monsters deal +X% crit damage | +2 | TRIVIAL |
| `MONSTER_LIFE_REGEN` | Monsters regenerate X% life/sec | +2-3 | TRIVIAL |

### Monster Count Modifiers (Prefix)
Affect spawning quantities. **[EASY]** - multiply spawn count during realm creation.

| Modifier | Effect | Difficulty | Impl |
|----------|--------|------------|------|
| `EXTRA_MONSTERS` | +X% more monsters spawn | +1-3 | EASY |
| `EXTRA_ELITES` | +X% more elite monsters | +2-3 | EASY |
| `EXTRA_BOSSES` | +1 additional boss spawns | +4 | EASY |
| `MONSTER_PACK_SIZE` | Monster packs have +X% size | +1-2 | EASY |
| `FEWER_MONSTERS` | -X% monsters (faster clear) | -1 | EASY |

### Player Debuff Modifiers (Prefix)
Reduce player effectiveness. **[TRIVIAL]** - apply negative stat modifier to players in realm.

| Modifier | Effect | Difficulty | Impl |
|----------|--------|------------|------|
| `PLAYER_REDUCED_DAMAGE` | Players deal -X% damage | +2-3 | TRIVIAL |
| `PLAYER_REDUCED_SPEED` | Players have -X% movement speed | +1-2 | TRIVIAL |
| `PLAYER_REDUCED_ATTACK_SPEED` | Players attack X% slower | +1-2 | TRIVIAL |
| `PLAYER_REDUCED_ARMOR` | Players have -X% armor | +1-2 | TRIVIAL |
| `PLAYER_REDUCED_EVASION` | Players have -X% evasion | +1-2 | TRIVIAL |
| `PLAYER_REDUCED_MAX_HEALTH` | Players have -X% max health | +3-4 | TRIVIAL |
| `PLAYER_REDUCED_MAX_MANA` | Players have -X% max mana | +2 | TRIVIAL |
| `PLAYER_REDUCED_REGEN` | Players regenerate X% slower | +1-2 | TRIVIAL |
| `PLAYER_REDUCED_LIFE_LEECH` | -X% life leech effectiveness | +2 | TRIVIAL |

### Resistance Modifiers (Prefix)
Modify elemental interactions. **[EASY]** - apply resistance modifier.

| Modifier | Effect | Difficulty | Impl |
|----------|--------|------------|------|
| `MONSTER_FIRE_RESIST` | Monsters have +X% fire resistance | +1-2 | TRIVIAL |
| `MONSTER_WATER_RESIST` | Monsters have +X% water resistance | +1-2 | TRIVIAL |
| `MONSTER_LIGHTNING_RESIST` | Monsters have +X% lightning resistance | +1-2 | TRIVIAL |
| `MONSTER_POISON_RESIST` | Monsters have +X% poison resistance | +1-2 | TRIVIAL |
| `MONSTER_PHYSICAL_RESIST` | Monsters have +X% physical resistance | +2-3 | TRIVIAL |
| `MONSTER_ALL_RESIST` | Monsters have +X% all resistances | +2-4 | TRIVIAL |
| `PLAYER_FIRE_WEAKNESS` | Players have -X% fire resistance | +1-2 | TRIVIAL |
| `PLAYER_WATER_WEAKNESS` | Players have -X% water resistance | +1-2 | TRIVIAL |
| `PLAYER_LIGHTNING_WEAKNESS` | Players have -X% lightning resistance | +1-2 | TRIVIAL |
| `PLAYER_POISON_WEAKNESS` | Players have -X% poison resistance | +1-2 | TRIVIAL |
| `PLAYER_ELEMENTAL_WEAKNESS` | Players have -X% all elemental res | +2-3 | TRIVIAL |

### Elemental Conversion Modifiers (Prefix)
Change damage types. **[EASY]** - set damage type override on monster component.

| Modifier | Effect | Difficulty | Impl |
|----------|--------|------------|------|
| `FIRE_DAMAGE` | Monsters deal only Fire damage | +1 | EASY |
| `WATER_DAMAGE` | Monsters deal only Water damage | +1 | EASY |
| `LIGHTNING_DAMAGE` | Monsters deal only Lightning damage | +1 | EASY |
| `POISON_DAMAGE` | Monsters deal only Poison damage | +1 | EASY |
| `PHYSICAL_DAMAGE` | Monsters deal only Physical damage | +0 | EASY |
| `VOID_DAMAGE` | Monsters deal additional Void damage | +2 | EASY |
| `RANDOM_ELEMENT` | Each monster has random element | +1 | EASY |

### Timer Modifiers (Suffix)
Affect realm duration. **[TRIVIAL]** - multiply duration value.

| Modifier | Effect | Difficulty | Reward | Impl |
|----------|--------|------------|--------|------|
| `REDUCED_TIME` | -X% realm duration | +2-3 | +X% IIQ | TRIVIAL |
| `EXTENDED_TIME` | +X% realm duration | -1 | 0 | TRIVIAL |
| `TIGHT_TIMER` | 50% less time | +4 | +30% IIQ | TRIVIAL |
| `RELAXED_TIMER` | 100% more time | -2 | 0 | TRIVIAL |

### Reward Modifiers (Suffix)
Increase rewards. **[TRIVIAL]** - store bonus values, apply during loot/xp calculation.

| Modifier | Effect | Difficulty | Impl |
|----------|--------|------------|------|
| `ITEM_QUANTITY` | +X% Item Quantity (IIQ) | 0 | TRIVIAL |
| `ITEM_RARITY` | +X% Item Rarity (IIR) | 0 | TRIVIAL |
| `EXPERIENCE_BONUS` | +X% Experience gained | 0 | TRIVIAL |
| `CURRENCY_BONUS` | +X% Currency drop chance | 0 | TRIVIAL |
| `MAP_BONUS` | +X% Realm Map drop chance | 0 | TRIVIAL |
| `GOLD_BONUS` | +X% Gold drops | 0 | TRIVIAL |
| `GEAR_BONUS` | +X% Gear drop chance | 0 | TRIVIAL |
| `FLAT_XP_BONUS` | +X flat XP per kill | 0 | TRIVIAL |
| `COMPLETION_BONUS` | +X% completion reward | 0 | TRIVIAL |

### Penalty Modifiers (Suffix)
Add risk for reward. **[EASY]** - flag checks on death/exit events.

| Modifier | Effect | Difficulty | Reward | Impl |
|----------|--------|------------|--------|------|
| `DEATH_DROPS_LOOT` | Drop gathered loot on death | +3 | +25% IIQ | EASY |
| `DEATH_LOSES_XP` | Lose X% XP on death | +2 | +15% XP | EASY |
| `DEATH_ENDS_REALM` | Dying closes the realm | +4 | +40% IIQ | EASY |
| `NO_REGENERATION` | Health doesn't regenerate | +3 | +25% IIQ | EASY |
| `NO_MANA_REGEN` | Mana doesn't regenerate | +2 | +15% IIQ | EASY |
| `NO_LEECH` | Life/mana leech disabled | +3 | +25% IIQ | EASY |
| `NO_POTIONS` | Cannot use potions | +4 | +35% IIQ | EASY |
| `NO_PORTAL_EXIT` | Can't exit until complete/death | +5 | +50% IIQ | EASY |
| `SINGLE_LIFE` | Only 1 life, no respawn | +5 | +50% IIQ | EASY |
| `TICKING_DAMAGE` | Take X damage per second | +2-3 | +20% IIQ | EASY |

### Level Modifiers (Suffix)
Affect monster levels. **[TRIVIAL]** - add/multiply to base level.

| Modifier | Effect | Difficulty | Reward | Impl |
|----------|--------|------------|--------|------|
| `MONSTER_LEVEL_BONUS` | Monsters are +X levels higher | +2-4 | +X% XP | TRIVIAL |
| `MONSTER_LEVEL_BONUS_PERCENT` | Monsters are +X% levels higher | +4-8 | +X% XP | TRIVIAL |
| `MONSTER_LEVEL_PENALTY` | Monsters are -X levels lower | -1-2 | 0 | TRIVIAL |
| `MONSTER_LEVEL_PENALTY_PERCENT` | Monsters are -X% levels lower | -2-4 | 0 | TRIVIAL |


### Area Modifiers (Suffix)
Affect the realm environment. **[EASY to MEDIUM]** - set flags checked by systems.

| Modifier | Effect | Difficulty | Impl |
|----------|--------|------------|------|
| `DARKNESS` | Reduced visibility radius | +1 | EASY |
| `LARGER_AREA` | +X% realm size | +1 | EASY |
| `SMALLER_AREA` | -X% realm size | -1 | EASY |
| `OPEN_LAYOUT` | Fewer obstacles | -1 | EASY |

### Boss Modifiers (Suffix)
Affect boss encounters. **[EASY]** - apply stat multipliers to boss classification.

| Modifier | Effect | Difficulty | Reward | Impl |
|----------|--------|------------|--------|------|
| `BOSS_HEALTH` | Boss has +X% health | +2-3 | +X% IIR | TRIVIAL |
| `BOSS_DAMAGE` | Boss deals +X% damage | +2-3 | +X% IIR | TRIVIAL |
| `BOSS_SPEED` | Boss has +X% speed | +1-2 | +X% IIR | TRIVIAL |
| `TWIN_BOSS` | Two bosses spawn | +5 | +100% IIR | EASY |
| `NO_BOSS` | No boss spawns | -2 | -50% IIR | EASY |
| `BOSS_MINIONS` | Boss spawns with X minions | +2 | +20% IIR | EASY |

---

## Modifier Value Rolling

When a modifier rolls on a map, its numeric value is determined by two factors:
1. **Map Rarity** → How many times to roll (keep best)
2. **Map Level** → Minimum floor via soft cap formula

This creates a simple but effective system where higher rarity and level maps naturally get better modifier values.

---

### Rarity Scaling: Roll and Keep Best

Higher rarity maps roll multiple times and **keep the best result**. This elegantly pushes higher rarities toward better values without complex weight tables.

```
┌─────────────────────────────────────────────────────────────────┐
│  RARITY → NUMBER OF ROLLS (KEEP BEST)                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  COMMON      Roll 1 time   ●○○○○○   Keep it (no choice)         │
│  UNCOMMON    Roll 2 times  ●●○○○○   Keep best of 2              │
│  RARE        Roll 3 times  ●●●○○○   Keep best of 3              │
│  EPIC        Roll 4 times  ●●●●○○   Keep best of 4              │
│  LEGENDARY   Roll 5 times  ●●●●●○   Keep best of 5              │
│  MYTHIC      Roll 6 times  ●●●●●●   Keep best of 6              │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**Example: Monster Damage modifier (range 10-75%)**

```
COMMON map:    Roll once         → 34%           → Final: 34%
MYTHIC map:    Roll 6 times      → 18, 42, 31, 67, 29, 45
                                 → Keep best     → Final: 67%
```

**Statistical effect:**
- COMMON: Average roll (median of range)
- MYTHIC: ~90th percentile of range (top 10% of possible rolls)

```java
int rollModifierValue(int min, int max, GearRarity rarity) {
    int rolls = rarity.ordinal() + 1;  // COMMON=1, MYTHIC=6
    int best = 0;

    for (int i = 0; i < rolls; i++) {
        int roll = random.nextInt(min, max + 1);
        best = Math.max(best, roll);
    }

    return best;
}
```

---

### Level Scaling: Soft Cap Minimum Floor

Map level sets a **minimum floor** for modifier values using a soft cap formula. This supports **infinite levels** with diminishing returns - you approach but never reach the theoretical maximum.

**Formula:**
```
floor_percent = level / (level + K)

where K is the soft cap constant (default: 1000)
```

**With K = 1000:**
```
┌─────────────────────────────────────────────────────────────────┐
│  LEVEL → MINIMUM FLOOR (% of max value)                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Level 10      →  10 / 1010   =   1% floor                      │
│  Level 50      →  50 / 1050   =   5% floor                      │
│  Level 100     → 100 / 1100   =   9% floor                      │
│  Level 250     → 250 / 1250   =  20% floor                      │
│  Level 500     → 500 / 1500   =  33% floor                      │
│  Level 1000    → 1000 / 2000  =  50% floor                      │
│  Level 2000    → 2000 / 3000  =  67% floor                      │
│  Level 5000    → 5000 / 6000  =  83% floor                      │
│  Level 10000   → 10000 / 11000 = 91% floor                      │
│  Level ∞       → approaches but NEVER reaches 100%              │
│                                                                 │
│  ──────────────────────────────────────────────────────────     │
│  100% ┤                                    ............-----    │
│       │                          ..........                     │
│   75% ┤                    ......                               │
│       │               .....                                     │
│   50% ┤          .....                                          │
│       │       ...                                               │
│   25% ┤    ...                                                  │
│       │  ..                                                     │
│    0% ┼.─────────┬──────────┬──────────┬──────────┬─────────    │
│       0       1000       2000       5000      10000             │
│                         Level                                   │
└─────────────────────────────────────────────────────────────────┘
```

**How floor affects rolls:**

```java
int rollWithFloor(int baseMin, int baseMax, int level, int softCapK) {
    // Calculate floor percentage
    float floorPercent = (float) level / (level + softCapK);

    // Convert to actual minimum value
    int range = baseMax - baseMin;
    int floor = baseMin + (int)(range * floorPercent);

    // Roll between floor and max (instead of baseMin and max)
    return random.nextInt(floor, baseMax + 1);
}
```

**Example: Monster Damage (base range 10-75%) at different levels:**

```
Level 100:   floor = 10 + (65 * 0.09) = 16%   → Roll range: 16-75%
Level 500:   floor = 10 + (65 * 0.33) = 31%   → Roll range: 31-75%
Level 1000:  floor = 10 + (65 * 0.50) = 42%   → Roll range: 42-75%
Level 5000:  floor = 10 + (65 * 0.83) = 64%   → Roll range: 64-75%
```

---

### Combined Example

A **LEGENDARY Level 1000 map** rolling Monster Damage (base 10-75%):

```java
// Step 1: Calculate level floor
int softCapK = 1000;  // From config
float floorPercent = 1000f / (1000 + softCapK);  // = 0.50 (50%)
int floor = 10 + (int)((75 - 10) * floorPercent);  // = 42%

// Step 2: Roll based on rarity (LEGENDARY = 5 rolls)
int[] rolls = {47, 62, 51, 68, 55};  // 5 random values between 42-75
int best = 68;  // Keep the best

// Final result: +68% Monster Damage
// A COMMON Level 100 map would likely get ~25-40%
// This LEGENDARY Level 1000 map gets 68%!
```

---

### Configuration

```yaml
# In realm-modifiers.yml
value_scaling:
  # Rarity determines number of rolls (keep best)
  # Formula: rolls = rarity.ordinal() + 1
  # COMMON=1, UNCOMMON=2, RARE=3, EPIC=4, LEGENDARY=5, MYTHIC=6
  rarity_roll_count:
    enabled: true
    # Override defaults if needed:
    # COMMON: 1
    # UNCOMMON: 2
    # RARE: 3
    # EPIC: 4
    # LEGENDARY: 5
    # MYTHIC: 6

  # Level determines minimum floor via soft cap
  # Formula: floor_percent = level / (level + soft_cap_k)
  level_floor:
    enabled: true
    soft_cap_k: 1000    # Higher = slower scaling
                        # K=500:  50% floor at level 500
                        # K=1000: 50% floor at level 1000 (default)
                        # K=2000: 50% floor at level 2000

  # Examples of K values for tuning:
  # ┌─────────┬────────────────────────────────────────┐
  # │    K    │ Level to reach 50% floor               │
  # ├─────────┼────────────────────────────────────────┤
  # │   500   │ Level 500  (fast progression)          │
  # │  1000   │ Level 1000 (default, moderate)         │
  # │  2000   │ Level 2000 (slow progression)          │
  # │  5000   │ Level 5000 (very slow, long grind)     │
  # └─────────┴────────────────────────────────────────┘
```

## Modifier Data Structure

```java
public record RealmModifier(
    ModifierType type,
    int value,
    int tier,
    int difficultyContribution
) {
    public static final Codec<RealmModifier> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            ModifierType.CODEC.fieldOf("type").forGetter(RealmModifier::type),
            Codec.INT.fieldOf("value").forGetter(RealmModifier::value),
            Codec.INT.fieldOf("tier").forGetter(RealmModifier::tier),
            Codec.INT.fieldOf("difficulty").forGetter(RealmModifier::difficultyContribution)
        ).apply(instance, RealmModifier::new)
    );

    public String getDisplayText() {
        return type.format(value);
    }
}
```

## Modifier Type Implementation

Modifiers are defined by their **stat type** which determines how they're applied:

```java
public enum ModifierStatType {
    // Multipliers - multiply a base stat by (1 + value/100)
    DAMAGE_MULTIPLIER,          // Monster damage
    MAX_HEALTH_MULTIPLIER,      // Monster max health
    MOVEMENT_SPEED_MULTIPLIER,  // Movement speed
    ATTACK_SPEED_MULTIPLIER,    // Attack speed
    ARMOR_MULTIPLIER,           // Armor value
    CRIT_CHANCE_BONUS,          // Flat crit chance addition

    // Player debuffs - reduce player stats
    PLAYER_DAMAGE_MULTIPLIER,
    PLAYER_MOVEMENT_MULTIPLIER,
    PLAYER_REGEN_MULTIPLIER,

    // Resistances
    MONSTER_FIRE_RESISTANCE,
    MONSTER_WATER_RESISTANCE,
    MONSTER_LIGHTNING_RESISTANCE,
    MONSTER_POISON_RESISTANCE,
    MONSTER_ALL_RESISTANCE,
    PLAYER_ELEMENTAL_RESISTANCE,

    // Overrides - set a specific value
    DAMAGE_TYPE_OVERRIDE,       // Set damage element

    // Spawn modifications
    SPAWN_COUNT_MULTIPLIER,
    ELITE_SPAWN_MULTIPLIER,
    BOSS_COUNT,

    // Timer
    DURATION_MULTIPLIER,

    // Rewards - stored and applied during loot calculation
    IIQ_BONUS,
    IIR_BONUS,
    XP_BONUS,
    MAP_DROP_BONUS,

    // Level
    LEVEL_BONUS,

    // Boss specific
    BOSS_HEALTH_MULTIPLIER,
    BOSS_DAMAGE_MULTIPLIER,

    // Flags - boolean toggles checked by systems
    FLAG
}

public enum ModifierFlag {
    NO_REGEN,           // Disable health/mana regen
    NO_LEECH,           // Disable life/mana leech
    NO_EXIT,            // Cannot use portal to exit
    DEATH_DROPS_LOOT,   // Drop loot on death
    DEATH_LOSES_XP,     // Lose XP on death
    DEATH_ENDS_REALM,   // Realm closes on death
    NO_POTIONS,         // Cannot use potions
    SINGLE_LIFE         // No respawn
}
```

### Applying Multiplier Modifiers

For stat multipliers, the formula is simple:

```java
// During monster spawn or player entry
float applyMultiplierMod(float baseStat, RealmModifier mod) {
    // value is a percentage, e.g., 25 means +25%
    float multiplier = 1.0f + (mod.value() / 100f);
    return baseStat * multiplier;
}

// For inverted (debuff) modifiers
float applyInvertedMod(float baseStat, RealmModifier mod) {
    // value 25 means -25%
    float multiplier = 1.0f - (mod.value() / 100f);
    return baseStat * multiplier;
}
```

### Applying Flag Modifiers

Flags are simple boolean checks:

```java
// In RealmContext
public boolean hasFlag(ModifierFlag flag) {
    return modifiers.stream()
        .filter(m -> m.statType() == ModifierStatType.FLAG)
        .anyMatch(m -> m.flagValue() == flag);
}

// Usage in systems
if (realmContext.hasFlag(ModifierFlag.NO_REGEN)) {
    // Skip regeneration tick
    return;
}
```

### Applying Reward Modifiers

Rewards are summed and applied during loot/XP calculation:

```java
// Aggregate all reward bonuses
float getTotalIIQ(List<RealmModifier> modifiers) {
    return modifiers.stream()
        .filter(m -> m.statType() == ModifierStatType.IIQ_BONUS)
        .mapToInt(RealmModifier::value)
        .sum() / 100f;
}

// During loot calculation
float iiqBonus = getTotalIIQ(realm.getModifiers()) + playerStats.getIIQ();
int dropCount = (int)(baseDropCount * (1 + iiqBonus));
```

## Modifier Pool & Rolling

```java
public class ModifierPool {
    private final Map<ModifierType, ModifierDefinition> definitions;
    private final Map<GearRarity, List<WeightedModifier>> rarityPools;

    public RealmModifier rollModifier(int mapLevel, GearRarity rarity) {
        // Get pool for this rarity
        List<WeightedModifier> pool = rarityPools.get(rarity);

        // Apply level restrictions
        pool = pool.stream()
            .filter(m -> m.getMinLevel() <= mapLevel)
            .filter(m -> m.getMaxLevel() >= mapLevel)
            .toList();

        // Weighted random selection
        float totalWeight = pool.stream().mapToFloat(WeightedModifier::getWeight).sum();
        float roll = random.nextFloat() * totalWeight;

        float cumulative = 0;
        for (WeightedModifier weighted : pool) {
            cumulative += weighted.getWeight();
            if (roll < cumulative) {
                return rollModifierValue(weighted.getType(), mapLevel);
            }
        }

        return rollModifierValue(pool.get(0).getType(), mapLevel);
    }

    private RealmModifier rollModifierValue(ModifierType type, int mapLevel) {
        ModifierDefinition def = definitions.get(type);
        ModifierTier[] tiers = def.getTiers();

        // Roll tier
        ModifierTier tier = rollTier(tiers);

        // Roll value within tier
        int value = random.nextInt(tier.minValue(), tier.maxValue() + 1);

        // Calculate difficulty contribution
        int difficulty = def.calculateDifficulty(value);

        return new RealmModifier(type, value, tier.ordinal(), difficulty);
    }
}
```

## Modifier Stacking Rules

1. **Same Type** - Modifiers of the same type don't stack; only one allowed per map
2. **Categories** - No limit on modifiers from same category
3. **Conflicts** - Some modifiers conflict (e.g., FIRE_ONLY and ICE_ONLY)
4. **Rarity Lock** - Higher rarity maps can roll higher tier modifiers

```java
public boolean canAddModifier(RealmModifier newModifier, List<RealmModifier> existing) {
    // Check for same type
    if (existing.stream().anyMatch(m -> m.type() == newModifier.type())) {
        return false;
    }

    // Check for conflicts
    Set<ModifierType> conflicts = CONFLICT_MAP.get(newModifier.type());
    if (conflicts != null) {
        if (existing.stream().anyMatch(m -> conflicts.contains(m.type()))) {
            return false;
        }
    }

    return true;
}
```

## Modifier Application

Modifiers are applied at different points depending on their type:

### Application Timing

| Modifier Type | When Applied | How Applied |
|---------------|--------------|-------------|
| Monster stats | Monster spawn | Stat multiplier on component |
| Player debuffs | Player enters realm | Temporary stat modifier |
| Spawn counts | Realm creation | Multiply spawn quantities |
| Elemental | Monster spawn | Set damage type override |
| Timer | Realm creation | Multiply duration |
| Rewards | Loot/XP calculation | Sum bonuses and apply |
| Flags | Event checks | Boolean flag lookup |
| Boss mods | Boss spawn | Apply to boss classification |
| Level bonus | Monster spawn | Add to base level |

### Monster Modifier Application (at spawn)

```java
public class RealmMobSpawner {

    private void applyModifiersToMob(Holder<EntityStore> mob, RealmMapData mapData) {
        EntityStatMap stats = mob.get(EntityStatMap.TYPE);

        for (RealmModifier mod : mapData.getModifiers()) {
            switch (mod.statType()) {
                case DAMAGE_MULTIPLIER -> {
                    float mult = 1.0f + mod.value() / 100f;
                    stats.addModifier(DAMAGE, new StaticModifier("REALM", MULTIPLY, mult));
                }
                case MAX_HEALTH_MULTIPLIER -> {
                    float mult = 1.0f + mod.value() / 100f;
                    stats.addModifier(MAX_HEALTH, new StaticModifier("REALM", MULTIPLY, mult));
                }
                case MOVEMENT_SPEED_MULTIPLIER -> {
                    float mult = 1.0f + mod.value() / 100f;
                    stats.addModifier(MOVE_SPEED, new StaticModifier("REALM", MULTIPLY, mult));
                }
                case DAMAGE_TYPE_OVERRIDE -> {
                    RealmMobComponent comp = mob.get(RealmMobComponent.TYPE);
                    comp.setDamageType(mod.elementValue());
                }
                case LEVEL_BONUS -> {
                    MobScalingComponent scaling = mob.get(MobScalingComponent.TYPE);
                    scaling.setLevel(scaling.getLevel() + mod.value());
                }
                // Multiplier modifiers are trivial - just multiply the stat
            }
        }
    }
}
```

### Player Modifier Application (on entry)

```java
public class RealmEntryListener {

    private void applyPlayerDebuffs(PlayerRef player, RealmMapData mapData) {
        EntityStatMap stats = player.getStatMap();

        for (RealmModifier mod : mapData.getModifiers()) {
            switch (mod.statType()) {
                case PLAYER_DAMAGE_MULTIPLIER -> {
                    // Inverted: value 25 means -25% damage
                    float mult = 1.0f - mod.value() / 100f;
                    stats.addModifier(DAMAGE, new StaticModifier("REALM_DEBUFF", MULTIPLY, mult));
                }
                case PLAYER_MOVEMENT_MULTIPLIER -> {
                    float mult = 1.0f - mod.value() / 100f;
                    stats.addModifier(MOVE_SPEED, new StaticModifier("REALM_DEBUFF", MULTIPLY, mult));
                }
                case PLAYER_REGEN_MULTIPLIER -> {
                    float mult = 1.0f - mod.value() / 100f;
                    stats.addModifier(REGEN_RATE, new StaticModifier("REALM_DEBUFF", MULTIPLY, mult));
                }
                // Debuffs removed on realm exit
            }
        }
    }
}
```

### Flag Checks (in relevant systems)

```java
// In RegenerationTickSystem
public void tick(...) {
    RealmPlayerComponent realmComp = store.getComponent(ref, RealmPlayerComponent.TYPE);
    if (realmComp != null) {
        RealmInstance realm = realmsManager.getRealmById(realmComp.realmId());
        if (realm.hasFlag(ModifierFlag.NO_REGEN)) {
            return;  // Skip regen tick entirely
        }
    }
    // Normal regen logic...
}

// In PortalUseListener
public void onPortalUse(PlayerRef player, Portal portal) {
    RealmInstance realm = getPlayerRealm(player);
    if (realm != null && realm.hasFlag(ModifierFlag.NO_EXIT)) {
        player.sendMessage("You cannot leave until the realm is complete!");
        event.cancel();
        return;
    }
    // Normal exit logic...
}
```

### Reward Application (during loot)

```java
// In LootListener (modified for realms)
public void onMobKilled(Ref<EntityStore> mob, UUID killerId) {
    float iiq = playerStats.getIIQ(killerId);
    float iir = playerStats.getIIR(killerId);
    float xpBonus = 0;

    // Add realm bonuses if in realm
    Optional<RealmInstance> realm = realmsManager.getPlayerRealm(killerId);
    if (realm.isPresent()) {
        iiq += realm.get().getTotalIIQ();
        iir += realm.get().getTotalIIR();
        xpBonus += realm.get().getTotalXPBonus();
    }

    // Apply to loot calculation
    List<ItemStack> drops = lootGenerator.generate(mob, iiq, iir);
    int xp = (int)(baseXp * (1 + xpBonus));
}
```

## Configuration

`realm-modifiers.yml`:

```yaml
# Modifier categories for organization
categories:
  MONSTER_STAT:
    color: "#FF6666"
    prefix: true
  MONSTER_COUNT:
    color: "#FF9966"
    prefix: true
  PLAYER_DEBUFF:
    color: "#FF4444"
    prefix: true
  RESISTANCE:
    color: "#AA66FF"
    prefix: true
  ELEMENTAL:
    color: "#AA44FF"
    prefix: true
  TIMER:
    color: "#FFAA44"
    prefix: false
  REWARD:
    color: "#44FF44"
    prefix: false
  PENALTY:
    color: "#FF6644"
    prefix: false
  LEVEL:
    color: "#66AAFF"
    prefix: false
  BOSS:
    color: "#FFD700"
    prefix: false

# Modifier definitions
modifiers:
  # ============ MONSTER STAT MODIFIERS ============
  monster_damage:
    name: "Increased Monster Damage"
    category: MONSTER_STAT
    stat: DAMAGE_MULTIPLIER
    format: "Monsters deal +{value}% damage"
    tiers:
      - { min: 10, max: 20, weight: 0.50, difficulty: 1 }
      - { min: 21, max: 35, weight: 0.30, difficulty: 2 }
      - { min: 36, max: 50, weight: 0.15, difficulty: 3 }
      - { min: 51, max: 75, weight: 0.05, difficulty: 4 }
    min_level: 1
    max_level: 100
    min_rarity: COMMON

  monster_health:
    name: "Increased Monster Life"
    category: MONSTER_STAT
    stat: MAX_HEALTH_MULTIPLIER
    format: "Monsters have +{value}% maximum life"
    tiers:
      - { min: 15, max: 30, weight: 0.50, difficulty: 1 }
      - { min: 31, max: 50, weight: 0.30, difficulty: 2 }
      - { min: 51, max: 75, weight: 0.15, difficulty: 3 }
      - { min: 76, max: 100, weight: 0.05, difficulty: 4 }
    min_level: 1
    max_level: 100
    min_rarity: COMMON

  monster_speed:
    name: "Monster Haste"
    category: MONSTER_STAT
    stat: MOVEMENT_SPEED_MULTIPLIER
    format: "Monsters have +{value}% movement speed"
    tiers:
      - { min: 10, max: 20, weight: 0.60, difficulty: 1 }
      - { min: 21, max: 35, weight: 0.30, difficulty: 2 }
      - { min: 36, max: 50, weight: 0.10, difficulty: 3 }
    min_level: 5
    max_level: 100
    min_rarity: COMMON

  monster_attack_speed:
    name: "Monster Frenzy"
    category: MONSTER_STAT
    stat: ATTACK_SPEED_MULTIPLIER
    format: "Monsters attack {value}% faster"
    tiers:
      - { min: 10, max: 20, weight: 0.60, difficulty: 1 }
      - { min: 21, max: 30, weight: 0.30, difficulty: 2 }
      - { min: 31, max: 40, weight: 0.10, difficulty: 3 }
    min_level: 10
    max_level: 100
    min_rarity: UNCOMMON

  monster_crit_chance:
    name: "Monster Precision"
    category: MONSTER_STAT
    stat: CRIT_CHANCE_BONUS
    format: "Monsters have +{value}% critical strike chance"
    tiers:
      - { min: 5, max: 10, weight: 0.60, difficulty: 1 }
      - { min: 11, max: 20, weight: 0.30, difficulty: 2 }
      - { min: 21, max: 30, weight: 0.10, difficulty: 3 }
    min_level: 15
    max_level: 100
    min_rarity: UNCOMMON

  monster_armor:
    name: "Armored Monsters"
    category: MONSTER_STAT
    stat: ARMOR_MULTIPLIER
    format: "Monsters have +{value}% armor"
    tiers:
      - { min: 20, max: 40, weight: 0.50, difficulty: 1 }
      - { min: 41, max: 70, weight: 0.35, difficulty: 2 }
      - { min: 71, max: 100, weight: 0.15, difficulty: 3 }
    min_level: 10
    max_level: 100
    min_rarity: UNCOMMON

  # ============ MONSTER COUNT MODIFIERS ============
  extra_monsters:
    name: "Populated"
    category: MONSTER_COUNT
    stat: SPAWN_COUNT_MULTIPLIER
    format: "+{value}% more monsters"
    tiers:
      - { min: 10, max: 20, weight: 0.50, difficulty: 1 }
      - { min: 21, max: 35, weight: 0.30, difficulty: 2 }
      - { min: 36, max: 50, weight: 0.15, difficulty: 3 }
      - { min: 51, max: 75, weight: 0.05, difficulty: 4 }
    min_level: 1
    max_level: 100
    min_rarity: COMMON

  extra_elites:
    name: "Elite Presence"
    category: MONSTER_COUNT
    stat: ELITE_SPAWN_MULTIPLIER
    format: "+{value}% more elite monsters"
    tiers:
      - { min: 20, max: 40, weight: 0.50, difficulty: 2 }
      - { min: 41, max: 70, weight: 0.35, difficulty: 3 }
      - { min: 71, max: 100, weight: 0.15, difficulty: 4 }
    min_level: 20
    max_level: 100
    min_rarity: RARE

  # ============ PLAYER DEBUFF MODIFIERS ============
  player_reduced_damage:
    name: "Player Weakness"
    category: PLAYER_DEBUFF
    stat: PLAYER_DAMAGE_MULTIPLIER
    format: "Players deal -{value}% damage"
    inverted: true  # negative effect
    tiers:
      - { min: 10, max: 15, weight: 0.50, difficulty: 2 }
      - { min: 16, max: 25, weight: 0.35, difficulty: 3 }
      - { min: 26, max: 35, weight: 0.15, difficulty: 4 }
    min_level: 15
    max_level: 100
    min_rarity: UNCOMMON

  player_reduced_speed:
    name: "Hindered"
    category: PLAYER_DEBUFF
    stat: PLAYER_MOVEMENT_MULTIPLIER
    format: "Players have -{value}% movement speed"
    inverted: true
    tiers:
      - { min: 10, max: 20, weight: 0.60, difficulty: 1 }
      - { min: 21, max: 30, weight: 0.30, difficulty: 2 }
      - { min: 31, max: 40, weight: 0.10, difficulty: 3 }
    min_level: 10
    max_level: 100
    min_rarity: UNCOMMON

  player_reduced_regen:
    name: "Suppressed Recovery"
    category: PLAYER_DEBUFF
    stat: PLAYER_REGEN_MULTIPLIER
    format: "Players regenerate {value}% slower"
    inverted: true
    tiers:
      - { min: 20, max: 40, weight: 0.50, difficulty: 1 }
      - { min: 41, max: 60, weight: 0.35, difficulty: 2 }
      - { min: 61, max: 80, weight: 0.15, difficulty: 3 }
    min_level: 10
    max_level: 100
    min_rarity: UNCOMMON

  # ============ RESISTANCE MODIFIERS ============
  monster_fire_resist:
    name: "Fire Resistant Monsters"
    category: RESISTANCE
    stat: MONSTER_FIRE_RESISTANCE
    format: "Monsters have +{value}% fire resistance"
    tiers:
      - { min: 20, max: 40, weight: 0.60, difficulty: 1 }
      - { min: 41, max: 60, weight: 0.30, difficulty: 2 }
      - { min: 61, max: 75, weight: 0.10, difficulty: 3 }
    min_level: 10
    max_level: 100
    min_rarity: UNCOMMON
    conflicts: [fire_damage]  # Pointless combo

  monster_all_resist:
    name: "Resistant Monsters"
    category: RESISTANCE
    stat: MONSTER_ALL_RESISTANCE
    format: "Monsters have +{value}% all resistances"
    tiers:
      - { min: 10, max: 20, weight: 0.50, difficulty: 2 }
      - { min: 21, max: 35, weight: 0.35, difficulty: 3 }
      - { min: 36, max: 50, weight: 0.15, difficulty: 4 }
    min_level: 25
    max_level: 100
    min_rarity: RARE

  player_elemental_weakness:
    name: "Elemental Vulnerability"
    category: RESISTANCE
    stat: PLAYER_ELEMENTAL_RESISTANCE
    format: "Players have -{value}% elemental resistances"
    inverted: true
    tiers:
      - { min: 10, max: 20, weight: 0.50, difficulty: 2 }
      - { min: 21, max: 35, weight: 0.35, difficulty: 3 }
      - { min: 36, max: 50, weight: 0.15, difficulty: 4 }
    min_level: 20
    max_level: 100
    min_rarity: RARE

  # ============ ELEMENTAL CONVERSION MODIFIERS ============
  fire_damage:
    name: "Burning"
    category: ELEMENTAL
    stat: DAMAGE_TYPE_OVERRIDE
    value: FIRE
    format: "Monsters deal Fire damage"
    difficulty: 1
    min_level: 10
    max_level: 100
    min_rarity: UNCOMMON
    conflicts: [water_damage, lightning_damage, poison_damage, physical_damage]

  water_damage:
    name: "Drowning"
    category: ELEMENTAL
    stat: DAMAGE_TYPE_OVERRIDE
    value: WATER
    format: "Monsters deal Water damage"
    difficulty: 1
    min_level: 10
    max_level: 100
    min_rarity: UNCOMMON
    conflicts: [fire_damage, lightning_damage, poison_damage, physical_damage]

  lightning_damage:
    name: "Shocking"
    category: ELEMENTAL
    stat: DAMAGE_TYPE_OVERRIDE
    value: LIGHTNING
    format: "Monsters deal Lightning damage"
    difficulty: 1
    min_level: 10
    max_level: 100
    min_rarity: UNCOMMON
    conflicts: [fire_damage, water_damage, poison_damage, physical_damage]

  poison_damage:
    name: "Toxic"
    category: ELEMENTAL
    stat: DAMAGE_TYPE_OVERRIDE
    value: POISON
    format: "Monsters deal Poison damage"
    difficulty: 1
    min_level: 10
    max_level: 100
    min_rarity: UNCOMMON
    conflicts: [fire_damage, water_damage, lightning_damage, physical_damage]

  # ============ TIMER MODIFIERS ============
  reduced_time:
    name: "Time Pressure"
    category: TIMER
    stat: DURATION_MULTIPLIER
    format: "-{value}% realm duration"
    inverted: true
    tiers:
      - { min: 15, max: 25, weight: 0.60, difficulty: 2, reward_iiq: 15 }
      - { min: 26, max: 40, weight: 0.30, difficulty: 3, reward_iiq: 25 }
      - { min: 41, max: 50, weight: 0.10, difficulty: 4, reward_iiq: 35 }
    min_level: 15
    max_level: 100
    min_rarity: UNCOMMON

  extended_time:
    name: "Extended"
    category: TIMER
    stat: DURATION_MULTIPLIER
    format: "+{value}% realm duration"
    tiers:
      - { min: 25, max: 50, weight: 0.70, difficulty: -1 }
      - { min: 51, max: 100, weight: 0.30, difficulty: -2 }
    min_level: 1
    max_level: 100
    min_rarity: COMMON

  # ============ REWARD MODIFIERS ============
  item_quantity:
    name: "Bountiful"
    category: REWARD
    stat: IIQ_BONUS
    format: "+{value}% Item Quantity"
    tiers:
      - { min: 5, max: 15, weight: 0.50 }
      - { min: 16, max: 30, weight: 0.30 }
      - { min: 31, max: 50, weight: 0.15 }
      - { min: 51, max: 75, weight: 0.05 }
    difficulty: 0
    min_level: 1
    max_level: 100
    min_rarity: COMMON

  item_rarity:
    name: "Lucky"
    category: REWARD
    stat: IIR_BONUS
    format: "+{value}% Item Rarity"
    tiers:
      - { min: 10, max: 25, weight: 0.50 }
      - { min: 26, max: 50, weight: 0.30 }
      - { min: 51, max: 80, weight: 0.15 }
      - { min: 81, max: 120, weight: 0.05 }
    difficulty: 0
    min_level: 1
    max_level: 100
    min_rarity: COMMON

  experience_bonus:
    name: "Enlightening"
    category: REWARD
    stat: XP_BONUS
    format: "+{value}% Experience gained"
    tiers:
      - { min: 10, max: 20, weight: 0.50 }
      - { min: 21, max: 35, weight: 0.30 }
      - { min: 36, max: 50, weight: 0.15 }
      - { min: 51, max: 75, weight: 0.05 }
    difficulty: 0
    min_level: 1
    max_level: 100
    min_rarity: COMMON

  map_bonus:
    name: "Cartographer's"
    category: REWARD
    stat: MAP_DROP_BONUS
    format: "+{value}% Realm Map drop chance"
    tiers:
      - { min: 10, max: 25, weight: 0.60 }
      - { min: 26, max: 50, weight: 0.30 }
      - { min: 51, max: 75, weight: 0.10 }
    difficulty: 0
    min_level: 10
    max_level: 100
    min_rarity: UNCOMMON

  # ============ PENALTY MODIFIERS ============
  no_regeneration:
    name: "No Regeneration"
    category: PENALTY
    stat: FLAG
    value: NO_REGEN
    format: "Life and Mana do not regenerate"
    difficulty: 3
    reward_iiq: 25
    min_level: 20
    max_level: 100
    min_rarity: RARE

  no_leech:
    name: "Cannot Leech"
    category: PENALTY
    stat: FLAG
    value: NO_LEECH
    format: "Life and Mana leech are disabled"
    difficulty: 3
    reward_iiq: 25
    min_level: 25
    max_level: 100
    min_rarity: RARE

  death_drops_loot:
    name: "Risky"
    category: PENALTY
    stat: FLAG
    value: DEATH_DROPS_LOOT
    format: "Dying drops all gathered loot"
    difficulty: 3
    reward_iiq: 30
    min_level: 20
    max_level: 100
    min_rarity: RARE

  no_portal_exit:
    name: "Locked"
    category: PENALTY
    stat: FLAG
    value: NO_EXIT
    format: "Cannot leave until completed or death"
    difficulty: 5
    reward_iiq: 50
    min_level: 30
    max_level: 100
    min_rarity: EPIC

  # ============ LEVEL MODIFIERS ============
  monster_level_bonus:
    name: "Deadly"
    category: LEVEL
    stat: LEVEL_BONUS
    format: "Monsters are +{value} levels"
    tiers:
      - { min: 1, max: 2, weight: 0.50, difficulty: 2, reward_xp: 10 }
      - { min: 3, max: 4, weight: 0.30, difficulty: 3, reward_xp: 20 }
      - { min: 5, max: 7, weight: 0.15, difficulty: 4, reward_xp: 35 }
      - { min: 8, max: 10, weight: 0.05, difficulty: 5, reward_xp: 50 }
    min_level: 15
    max_level: 90  # Cap at 90 so +10 doesn't exceed 100
    min_rarity: RARE

  # ============ BOSS MODIFIERS ============
  boss_health:
    name: "Resilient Boss"
    category: BOSS
    stat: BOSS_HEALTH_MULTIPLIER
    format: "Boss has +{value}% maximum life"
    tiers:
      - { min: 25, max: 50, weight: 0.50, difficulty: 2, reward_iir: 15 }
      - { min: 51, max: 100, weight: 0.35, difficulty: 3, reward_iir: 30 }
      - { min: 101, max: 150, weight: 0.15, difficulty: 4, reward_iir: 50 }
    min_level: 15
    max_level: 100
    min_rarity: UNCOMMON

  boss_damage:
    name: "Devastating Boss"
    category: BOSS
    stat: BOSS_DAMAGE_MULTIPLIER
    format: "Boss deals +{value}% damage"
    tiers:
      - { min: 20, max: 40, weight: 0.50, difficulty: 2, reward_iir: 15 }
      - { min: 41, max: 70, weight: 0.35, difficulty: 3, reward_iir: 30 }
      - { min: 71, max: 100, weight: 0.15, difficulty: 4, reward_iir: 50 }
    min_level: 15
    max_level: 100
    min_rarity: UNCOMMON

  twin_boss:
    name: "Twinned"
    category: BOSS
    stat: BOSS_COUNT
    value: 2
    format: "Area contains two Bosses"
    difficulty: 5
    reward_iir: 100
    min_level: 40
    max_level: 100
    min_rarity: EPIC
```

## Difficulty Rating Calculation

```java
public int calculateDifficultyRating(List<RealmModifier> modifiers) {
    int total = 0;
    for (RealmModifier mod : modifiers) {
        total += mod.difficultyContribution();
    }

    // Convert to star rating (1-5)
    return Math.min(5, Math.max(1, total / 3));
}
```

## Display Colors

Modifiers are color-coded by category:

| Category | Color | Hex |
|----------|-------|-----|
| DIFFICULTY | Red | #FF4444 |
| ELEMENTAL | Purple | #AA44FF |
| REWARD | Green | #44FF44 |
| PENALTY | Orange | #FFAA44 |
| SPECIAL | Gold | #FFD700 |
