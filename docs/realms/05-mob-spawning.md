# Mob Spawning & Completion Tracking

This document covers how monsters are spawned in Realms and how completion is tracked.

## Design Philosophy

**Key Principle:** Monsters are pre-spawned before player entry, NOT spawned during gameplay.

This ensures:
1. Known monster count for completion tracking
2. Fair, predictable difficulty
3. No surprise spawns disrupting strategy
4. Clear progress indication to players

## Spawn Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                    MOB SPAWNING FLOW                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  REALM CREATED                                                  │
│       │                                                         │
│       ▼                                                         │
│  ┌─────────────────┐                                            │
│  │ Load Template   │ ─── Get spawn points from template         │
│  └────────┬────────┘                                            │
│           │                                                     │
│           ▼                                                     │
│  ┌─────────────────┐                                            │
│  │ Calculate Mobs  │ ─── Apply map level, modifiers, size       │
│  └────────┬────────┘                                            │
│           │                                                     │
│           ▼                                                     │
│  ┌─────────────────┐                                            │
│  │ Select Mob Pool │ ─── Based on biome and level               │
│  └────────┬────────┘                                            │
│           │                                                     │
│           ▼                                                     │
│  ┌─────────────────┐                                            │
│  │ Spawn Monsters  │ ─── Place at spawn points with variance    │
│  └────────┬────────┘                                            │
│           │                                                     │
│           ▼                                                     │
│  ┌─────────────────┐                                            │
│  │ Apply Scaling   │ ─── Set level, stats via MobScalingManager │
│  └────────┬────────┘                                            │
│           │                                                     │
│           ▼                                                     │
│  ┌─────────────────┐                                            │
│  │ Register Tracker│ ─── Count = total spawned                  │
│  └────────┬────────┘                                            │
│           │                                                     │
│           ▼                                                     │
│  REALM READY FOR ENTRY                                          │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Monster Count Calculation

```java
public class RealmMobCalculator {

    public int calculateTotalMonsters(RealmTemplate template, RealmMapData mapData) {
        // Base count from template
        int baseCount = template.estimatedMonsters();

        // Size multiplier
        float sizeMultiplier = mapData.getLayoutSize().getMonsterMultiplier();

        // Level scaling (higher levels = slightly more monsters)
        float levelMultiplier = 1.0f + (mapData.getLevel() * 0.005f);  // +0.5% per level

        // Modifier bonuses
        float modifierMultiplier = 1.0f;
        for (RealmModifier mod : mapData.getModifiers()) {
            if (mod.type() == ModifierType.EXTRA_MONSTERS) {
                modifierMultiplier += mod.value() / 100f;
            }
        }

        int total = Math.round(baseCount * sizeMultiplier * levelMultiplier * modifierMultiplier);

        // Clamp to reasonable bounds
        return Math.clamp(total, 10, 500);
    }
}
```

## Mob Pool Selection

Each biome has an associated mob pool:

```java
public class RealmMobPool {
    private final Map<RealmBiomeType, BiomeMobPool> biomePools;

    public BiomeMobPool getPool(RealmBiomeType biome) {
        return biomePools.getOrDefault(biome, getDefaultPool());
    }
}

public record BiomeMobPool(
    List<WeightedMob> normalMobs,
    List<WeightedMob> eliteMobs,
    List<WeightedMob> bossMobs
) {
    public WeightedMob selectNormal(Random random, int level) {
        return selectFromPool(normalMobs, random, level);
    }

    public WeightedMob selectElite(Random random, int level) {
        return selectFromPool(eliteMobs, random, level);
    }

    public WeightedMob selectBoss(Random random, int level) {
        return selectFromPool(bossMobs, random, level);
    }

    private WeightedMob selectFromPool(List<WeightedMob> pool, Random random, int level) {
        List<WeightedMob> valid = pool.stream()
            .filter(m -> level >= m.minLevel() && level <= m.maxLevel())
            .toList();

        if (valid.isEmpty()) valid = pool;

        float totalWeight = valid.stream().mapToFloat(WeightedMob::weight).sum();
        float roll = random.nextFloat() * totalWeight;

        float cumulative = 0;
        for (WeightedMob mob : valid) {
            cumulative += mob.weight();
            if (roll < cumulative) {
                return mob;
            }
        }
        return valid.get(0);
    }
}

public record WeightedMob(
    String mobTypeId,
    float weight,
    int minLevel,
    int maxLevel,
    MobClassification classification
) {}
```

## Spawn Point Distribution

```java
public class RealmMobSpawner {
    private final RealmMobPool mobPool;
    private final MobScalingManager scalingManager;
    private final Random random = new Random();

    public SpawnResult spawnMonstersForRealm(World world, RealmTemplate template,
                                              RealmMapData mapData) {
        int totalMonsters = calculateTotalMonsters(template, mapData);
        BiomeMobPool pool = mobPool.getPool(mapData.getBiomeType());

        List<SpawnedMob> spawned = new ArrayList<>();
        int remaining = totalMonsters;

        // Spawn at each spawn point
        for (MonsterSpawnPoint point : template.monsterSpawns()) {
            int countForPoint = calculateCountForPoint(point, remaining, template.monsterSpawns().size());

            for (int i = 0; i < countForPoint && remaining > 0; i++) {
                // Select mob type based on spawn point type
                WeightedMob mobType = switch (point.type()) {
                    case NORMAL -> pool.selectNormal(random, mapData.getLevel());
                    case ELITE -> pool.selectElite(random, mapData.getLevel());
                    case BOSS -> pool.selectBoss(random, mapData.getLevel());
                    case PACK -> pool.selectNormal(random, mapData.getLevel());
                };

                // Calculate spawn position with variance
                Vector3 spawnPos = calculateSpawnPosition(point);

                // Spawn the mob
                Ref<EntityStore> mobRef = spawnMob(world, mobType, spawnPos, mapData);

                if (mobRef != null) {
                    spawned.add(new SpawnedMob(mobRef, mobType, spawnPos));
                    remaining--;
                }
            }
        }

        // Spawn any remaining at random valid positions
        while (remaining > 0) {
            Vector3 randomPos = getRandomValidPosition(world, template);
            WeightedMob mobType = pool.selectNormal(random, mapData.getLevel());

            Ref<EntityStore> mobRef = spawnMob(world, mobType, randomPos, mapData);
            if (mobRef != null) {
                spawned.add(new SpawnedMob(mobRef, mobType, randomPos));
                remaining--;
            }
        }

        return new SpawnResult(spawned, spawned.size());
    }

    private Vector3 calculateSpawnPosition(MonsterSpawnPoint point) {
        // Add random offset within radius
        float angle = random.nextFloat() * 2 * (float)Math.PI;
        float distance = random.nextFloat() * point.radius();

        float offsetX = (float)Math.cos(angle) * distance;
        float offsetZ = (float)Math.sin(angle) * distance;

        return new Vector3(
            point.position().x() + offsetX,
            point.position().y(),
            point.position().z() + offsetZ
        );
    }

    private Ref<EntityStore> spawnMob(World world, WeightedMob mobType,
                                       Vector3 position, RealmMapData mapData) {
        // Create entity holder
        Holder<EntityStore> holder = createMobHolder(mobType.mobTypeId(), position);

        // Apply realm-specific scaling
        applyRealmScaling(holder, mapData, mobType.classification());

        // Add to world via command buffer
        world.execute(() -> {
            Store<EntityStore> store = world.getEntityStore();
            CommandBuffer<EntityStore> cmd = new CommandBuffer<>();
            cmd.addEntities(new Holder[]{holder}, AddReason.SPAWN);
            cmd.execute(store);
        });

        return holder.getRef();
    }

    private void applyRealmScaling(Holder<EntityStore> holder, RealmMapData mapData,
                                    MobClassification classification) {
        // Set fixed level (map level)
        RealmMobComponent realmComponent = new RealmMobComponent(
            mapData.getMapId(),
            mapData.getLevel(),
            classification
        );
        holder.addComponent(RealmMobComponent.TYPE, realmComponent);

        // Apply modifier effects
        for (RealmModifier mod : mapData.getModifiers()) {
            applyModifierToMob(holder, mod);
        }
    }
}
```

## Completion Tracking

### Tracker Component

```java
public class RealmCompletionTracker {
    private final UUID realmId;
    private final AtomicInteger totalMonsters;
    private final AtomicInteger remainingMonsters;
    private final AtomicInteger killedByPlayers;
    private final Set<UUID> participatingPlayers;
    private final Instant startTime;
    private volatile boolean completed;

    public RealmCompletionTracker(UUID realmId, int totalMonsters) {
        this.realmId = realmId;
        this.totalMonsters = new AtomicInteger(totalMonsters);
        this.remainingMonsters = new AtomicInteger(totalMonsters);
        this.killedByPlayers = new AtomicInteger(0);
        this.participatingPlayers = ConcurrentHashMap.newKeySet();
        this.startTime = Instant.now();
        this.completed = false;
    }

    public void onMonsterKilled(UUID killerId) {
        int remaining = remainingMonsters.decrementAndGet();
        killedByPlayers.incrementAndGet();

        if (killerId != null) {
            participatingPlayers.add(killerId);
        }

        if (remaining <= 0 && !completed) {
            completed = true;
            // Completion will be handled by RealmCompletionSystem
        }
    }

    public float getCompletionProgress() {
        int total = totalMonsters.get();
        int remaining = remainingMonsters.get();
        return (float)(total - remaining) / total;
    }

    public int getRemainingMonsters() {
        return remainingMonsters.get();
    }

    public boolean isCompleted() {
        return completed || remainingMonsters.get() <= 0;
    }
}
```

### Death Listener for Tracking

```java
public class RealmMobDeathListener extends DeathSystems.OnDeathSystem {

    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.of(RealmMobComponent.getComponentType());
    }

    @Override
    public void onComponentAdded(Ref<EntityStore> ref, DeathComponent death,
                                  Store<EntityStore> store, CommandBuffer<EntityStore> cmd) {
        // Get realm component
        RealmMobComponent realmMob = store.getComponent(ref, RealmMobComponent.TYPE);
        if (realmMob == null) return;

        // Get realm tracker
        RealmInstance realm = realmsManager.getRealmById(realmMob.realmId());
        if (realm == null) return;

        // Extract killer
        UUID killerId = extractKillerId(death, store);

        // Update tracker
        realm.getCompletionTracker().onMonsterKilled(killerId);

        // Check for completion
        if (realm.getCompletionTracker().isCompleted()) {
            realmsManager.triggerCompletion(realm);
        }
    }

    private UUID extractKillerId(DeathComponent death, Store<EntityStore> store) {
        if (death.getKiller() != null) {
            Ref<EntityStore> killerRef = death.getKiller();
            PlayerRef playerRef = store.getComponent(killerRef, PlayerRef.getComponentType());
            if (playerRef != null) {
                return playerRef.getUuid();
            }
        }
        return null;
    }
}
```

## Progress Display

Players see real-time progress:

```
╔════════════════════════════════════════╗
║  REALM PROGRESS                        ║
╠════════════════════════════════════════╣
║  ████████████░░░░░░░░  47/80           ║
║                                        ║
║  Time Remaining: 4:23                  ║
║  Monsters Left: 33                     ║
╚════════════════════════════════════════╝
```

### Progress Update System

```java
public class RealmProgressSystem extends EntityTickingSystem<EntityStore> {
    private static final float UPDATE_INTERVAL = 1.0f;  // Update every second
    private float timeSinceUpdate = 0;

    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.of(PlayerRef.getComponentType(), RealmPlayerComponent.TYPE);
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk chunk, Store store, CommandBuffer cmd) {
        timeSinceUpdate += dt;
        if (timeSinceUpdate < UPDATE_INTERVAL) return;
        timeSinceUpdate = 0;

        Ref<EntityStore> playerRef = chunk.getReferenceTo(index);
        RealmPlayerComponent realmPlayer = store.getComponent(playerRef, RealmPlayerComponent.TYPE);

        RealmInstance realm = realmsManager.getRealmById(realmPlayer.realmId());
        if (realm == null) return;

        // Send progress update to player
        PlayerRef player = store.getComponent(playerRef, PlayerRef.getComponentType());
        sendProgressUpdate(player, realm);
    }

    private void sendProgressUpdate(PlayerRef player, RealmInstance realm) {
        RealmCompletionTracker tracker = realm.getCompletionTracker();

        int remaining = tracker.getRemainingMonsters();
        int total = tracker.getTotalMonsters();
        float progress = tracker.getCompletionProgress();
        int secondsRemaining = realm.getSecondsRemaining();

        // Send to UI system
        uiService.updateRealmProgress(player.getUuid(), new RealmProgressData(
            remaining, total, progress, secondsRemaining
        ));
    }
}
```

## Mob Configuration

`realm-mobs.yml`:

```yaml
mob_pools:
  forest:
    normal:
      - mob: "hytale:wolf"
        weight: 1.0
        min_level: 1
        max_level: 20
      - mob: "hytale:spider"
        weight: 0.8
        min_level: 1
        max_level: 30
      - mob: "hytale:bear"
        weight: 0.5
        min_level: 10
        max_level: 50
    elite:
      - mob: "hytale:dire_wolf"
        weight: 1.0
        min_level: 1
        max_level: 30
      - mob: "hytale:giant_spider"
        weight: 0.7
        min_level: 15
        max_level: 50
    boss:
      - mob: "hytale:forest_guardian"
        weight: 1.0
        min_level: 1
        max_level: 100

  desert:
    normal:
      - mob: "hytale:scorpion"
        weight: 1.0
        min_level: 1
        max_level: 40
      - mob: "hytale:sand_serpent"
        weight: 0.6
        min_level: 20
        max_level: 60
    elite:
      - mob: "hytale:giant_scorpion"
        weight: 1.0
        min_level: 10
        max_level: 50
    boss:
      - mob: "hytale:sand_wyrm"
        weight: 1.0
        min_level: 1
        max_level: 100

  volcano:
    normal:
      - mob: "hytale:fire_elemental"
        weight: 1.0
        min_level: 30
        max_level: 80
      - mob: "hytale:lava_slime"
        weight: 0.8
        min_level: 20
        max_level: 70
    elite:
      - mob: "hytale:magma_golem"
        weight: 1.0
        min_level: 40
        max_level: 90
    boss:
      - mob: "hytale:volcanic_titan"
        weight: 1.0
        min_level: 50
        max_level: 100

spawn_settings:
  normal_pack_size:
    min: 1
    max: 3
  elite_spawn_chance: 0.15  # 15% of spawns are elite
  boss_count:
    small: 0
    medium: 1
    large: 2
    massive: 3
  spawn_radius: 3.0  # Variance around spawn point
```
