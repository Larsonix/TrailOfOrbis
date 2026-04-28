# Technical Architecture

This document details the technical implementation of the Realms system, including class structure, data flow, and integration points.

## Package Structure

```
io.github.larsonix.trailoforbis.maps/
├── RealmsManager.java              # Main coordinator
├── api/
│   ├── RealmsService.java          # Public API interface
│   └── events/
│       ├── RealmCreatedEvent.java
│       ├── RealmEnteredEvent.java
│       ├── RealmCompletedEvent.java
│       └── RealmClosedEvent.java
├── config/
│   ├── RealmsConfig.java           # Main configuration
│   ├── RealmTemplateConfig.java
│   └── RealmModifierConfig.java
├── core/
│   ├── RealmInstance.java          # Active realm state
│   ├── RealmMapData.java           # Map item data (immutable)
│   ├── RealmCompletionTracker.java # Kill tracking
│   └── RealmState.java             # Lifecycle enum
├── items/
│   ├── RealmMapItem.java           # Map item integration
│   ├── RealmMapComponent.java      # ECS component for maps
│   └── RealmMapDropListener.java   # Drop on mob kill
├── modifiers/
│   ├── ModifierType.java           # Modifier definitions
│   ├── ModifierPool.java           # Weighted selection
│   ├── RealmModifier.java          # Individual modifier
│   └── ModifierApplicationSystem.java
├── templates/
│   ├── RealmTemplate.java          # Template data
│   ├── RealmTemplateManager.java   # Template loading/selection
│   └── MonsterSpawnPoint.java
├── spawning/
│   ├── RealmMobSpawner.java        # Monster placement
│   ├── RealmMobPool.java           # Mob selection by biome
│   ├── RealmMobComponent.java      # ECS component for realm mobs
│   └── RealmMobDeathListener.java  # Death tracking
├── stones/
│   ├── StoneType.java              # Stone definitions (shared with gear)
│   ├── MapStoneActions.java        # Map-specific stone actions
│   └── StoneDropListener.java      # Stone drops
├── instance/
│   ├── RealmInstanceFactory.java   # Create realm worlds
│   ├── RealmPortalManager.java     # Portal handling
│   └── RealmRemovalHandler.java    # Cleanup on close
├── systems/
│   ├── RealmProgressSystem.java    # Progress UI updates
│   ├── RealmTimerSystem.java       # Timeout handling
│   └── RealmModifierSystem.java    # Apply modifiers to entities
├── ui/
│   ├── RealmMapUI.java             # Map display
│   ├── RealmProgressUI.java        # In-realm progress
│   └── RealmCraftingUI.java        # Stone application
└── listeners/
    ├── RealmEntryListener.java     # Portal entry handling
    ├── RealmExitListener.java      # Exit/death handling
    └── RealmMapUseListener.java    # Map activation
```

## Core Classes

### RealmsManager

Central coordinator for the Realms system:

```java
public class RealmsManager implements RealmsService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final TrailOfOrbis plugin;
    private final RealmsConfig config;
    private final RealmTemplateManager templateManager;
    private final RealmInstanceFactory instanceFactory;
    private final RealmMobSpawner mobSpawner;
    private final StoneManager stoneManager;
    private final ModifierPool modifierPool;

    // Active realms by map ID
    private final Map<UUID, RealmInstance> activeRealms = new ConcurrentHashMap<>();

    // Player to realm mapping
    private final Map<UUID, UUID> playerRealms = new ConcurrentHashMap<>();

    public RealmsManager(TrailOfOrbis plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager().getRealmsConfig();
        this.templateManager = new RealmTemplateManager(config.getTemplatesPath());
        this.modifierPool = new ModifierPool(config.getModifierConfig());
        this.mobSpawner = new RealmMobSpawner(modifierPool);
        this.stoneManager = new StoneManager();
        this.instanceFactory = new RealmInstanceFactory(templateManager, mobSpawner, config);
    }

    // ==================== Lifecycle ====================

    public void setup() {
        // Register ECS components
        RealmMobComponent.TYPE = getEntityStoreRegistry().registerComponent(
            RealmMobComponent.class,
            "RealmMobComponent",
            RealmMobComponent.CODEC
        );

        RealmPlayerComponent.TYPE = getEntityStoreRegistry().registerComponent(
            RealmPlayerComponent.class,
            "RealmPlayerComponent",
            RealmPlayerComponent.CODEC
        );

        // Register systems
        getEntityStoreRegistry().registerSystem(new RealmProgressSystem(this));
        getEntityStoreRegistry().registerSystem(new RealmTimerSystem(this));
        getEntityStoreRegistry().registerSystem(new RealmModifierSystem(this));

        // Register listeners
        registerListeners();

        LOGGER.atInfo().log("RealmsManager setup complete");
    }

    public void start() {
        // Load templates
        templateManager.loadTemplates(config.getTemplatesPath());

        // Load modifier pool
        modifierPool.loadFromConfig(config.getModifierConfig());

        LOGGER.atInfo().log("RealmsManager started with %d templates",
            templateManager.getTemplateCount());
    }

    public void shutdown() {
        // Close all active realms
        for (RealmInstance realm : activeRealms.values()) {
            closeRealm(realm, RealmCloseReason.SERVER_SHUTDOWN);
        }

        activeRealms.clear();
        playerRealms.clear();

        LOGGER.atInfo().log("RealmsManager shutdown complete");
    }

    // ==================== Realm Creation ====================

    @Override
    public CompletableFuture<RealmInstance> createRealm(RealmMapData mapData, PlayerRef opener) {
        UUID mapId = mapData.getMapId();

        if (activeRealms.containsKey(mapId)) {
            return CompletableFuture.failedFuture(
                new RealmAlreadyExistsException(mapId));
        }

        LOGGER.atInfo().log("Creating realm for map %s (level %d) by player %s",
            mapId, mapData.getLevel(), opener.getUuid());

        return instanceFactory.createRealm(mapData, opener)
            .thenApply(realm -> {
                activeRealms.put(mapId, realm);

                // Fire event
                fireEvent(new RealmCreatedEvent(realm, opener.getUuid()));

                return realm;
            });
    }

    // ==================== Player Entry/Exit ====================

    @Override
    public void enterRealm(PlayerRef player, RealmInstance realm) {
        UUID playerId = player.getUuid();
        UUID realmId = realm.getMapId();

        // Track player
        playerRealms.put(playerId, realmId);

        // Add component to player
        player.getWorld().execute(() -> {
            Store<EntityStore> store = player.getStore();
            CommandBuffer<EntityStore> cmd = new CommandBuffer<>();

            RealmPlayerComponent component = new RealmPlayerComponent(realmId, Instant.now());
            cmd.addComponent(player.getRef(), RealmPlayerComponent.TYPE, component);
            cmd.execute(store);
        });

        // Teleport to realm
        InstancesPlugin.teleportPlayerToInstance(
            player,
            player.getComponentAccessor(),
            realm.getWorld(),
            null
        );

        // Fire event
        fireEvent(new RealmEnteredEvent(realm, playerId));

        LOGGER.atInfo().log("Player %s entered realm %s", playerId, realmId);
    }

    @Override
    public void exitRealm(PlayerRef player) {
        UUID playerId = player.getUuid();
        UUID realmId = playerRealms.remove(playerId);

        if (realmId == null) return;

        // Remove component
        player.getWorld().execute(() -> {
            Store<EntityStore> store = player.getStore();
            CommandBuffer<EntityStore> cmd = new CommandBuffer<>();
            cmd.removeComponent(player.getRef(), RealmPlayerComponent.TYPE);
            cmd.execute(store);
        });

        // Teleport back via Instance system
        InstancesPlugin.exitInstance(player, player.getComponentAccessor());

        LOGGER.atInfo().log("Player %s exited realm %s", playerId, realmId);
    }

    // ==================== Completion ====================

    @Override
    public void triggerCompletion(RealmInstance realm) {
        if (realm.getState() != RealmState.ACTIVE) return;

        realm.setState(RealmState.ENDING);

        LOGGER.atInfo().log("Realm %s completed!", realm.getMapId());

        // Calculate and distribute rewards
        distributeCompletionRewards(realm);

        // Fire event
        fireEvent(new RealmCompletedEvent(realm, realm.getCompletionTracker()));

        // Schedule cleanup
        scheduleCleanup(realm, config.getCompletionGracePeriod());
    }

    // ==================== Queries ====================

    @Override
    public Optional<RealmInstance> getRealmById(UUID mapId) {
        return Optional.ofNullable(activeRealms.get(mapId));
    }

    @Override
    public Optional<RealmInstance> getPlayerRealm(UUID playerId) {
        UUID realmId = playerRealms.get(playerId);
        if (realmId == null) return Optional.empty();
        return getRealmById(realmId);
    }

    @Override
    public boolean isPlayerInRealm(UUID playerId) {
        return playerRealms.containsKey(playerId);
    }

    // ==================== Private Methods ====================

    private void closeRealm(RealmInstance realm, RealmCloseReason reason) {
        UUID mapId = realm.getMapId();

        // Remove from tracking
        activeRealms.remove(mapId);

        // Exit all players
        for (UUID playerId : realm.getPlayersInRealm()) {
            PlayerRef player = getPlayer(playerId);
            if (player != null) {
                exitRealm(player);
            }
            playerRealms.remove(playerId);
        }

        // Remove world via Instance system
        InstancesPlugin.safeRemoveInstance(realm.getWorld().getName());

        // Fire event
        fireEvent(new RealmClosedEvent(realm, reason));

        LOGGER.atInfo().log("Realm %s closed (reason: %s)", mapId, reason);
    }

    private void registerListeners() {
        EventRegistry registry = plugin.getEventRegistry();

        registry.register(RealmMapUseListener.class, new RealmMapUseListener(this));
        registry.register(RealmEntryListener.class, new RealmEntryListener(this));
        registry.register(RealmExitListener.class, new RealmExitListener(this));
        registry.register(RealmMobDeathListener.class, new RealmMobDeathListener(this));
        registry.register(RealmMapDropListener.class, new RealmMapDropListener(this, modifierPool));
    }
}
```

### RealmInstance

Active realm state:

```java
public class RealmInstance {
    private final UUID mapId;
    private final World world;
    private final RealmTemplate template;
    private final RealmMapData mapData;
    private final UUID openerId;
    private final Instant createdAt;
    private final RealmCompletionTracker completionTracker;
    private final Set<UUID> playersInRealm;

    private volatile RealmState state;
    private volatile Instant expiresAt;

    public RealmInstance(UUID mapId, World world, RealmTemplate template,
                         RealmMapData mapData, UUID openerId, int totalMonsters) {
        this.mapId = mapId;
        this.world = world;
        this.template = template;
        this.mapData = mapData;
        this.openerId = openerId;
        this.createdAt = Instant.now();
        this.completionTracker = new RealmCompletionTracker(mapId, totalMonsters);
        this.playersInRealm = ConcurrentHashMap.newKeySet();
        this.state = RealmState.CREATED;
    }

    public int getSecondsRemaining() {
        if (expiresAt == null) return -1;
        long remaining = Duration.between(Instant.now(), expiresAt).getSeconds();
        return (int) Math.max(0, remaining);
    }

    public void addPlayer(UUID playerId) {
        playersInRealm.add(playerId);
    }

    public void removePlayer(UUID playerId) {
        playersInRealm.remove(playerId);
    }

    // Getters...
}
```

### RealmMapData

Immutable map item data:

```java
public final class RealmMapData {
    private final UUID mapId;
    private final int level;
    private final GearRarity rarity;  // Reuses GearRarity enum
    private final String templateId;
    private final RealmBiomeType biomeType;
    private final RealmLayoutShape layoutShape;
    private final RealmLayoutSize layoutSize;
    private final List<RealmModifier> modifiers;
    private final int quality;
    private final boolean corrupted;

    public static final Codec<RealmMapData> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.STRING.fieldOf("mapId").forGetter(d -> d.mapId.toString()),
            Codec.INT.fieldOf("level").forGetter(RealmMapData::level),
            GearRarity.CODEC.fieldOf("rarity").forGetter(RealmMapData::rarity),  // Uses GearRarity
            Codec.STRING.fieldOf("templateId").forGetter(RealmMapData::templateId),
            RealmBiomeType.CODEC.fieldOf("biomeType").forGetter(RealmMapData::biomeType),
            RealmLayoutShape.CODEC.fieldOf("layoutShape").forGetter(RealmMapData::layoutShape),
            RealmLayoutSize.CODEC.fieldOf("layoutSize").forGetter(RealmMapData::layoutSize),
            RealmModifier.CODEC.listOf().fieldOf("modifiers").forGetter(RealmMapData::modifiers),
            Codec.INT.optionalFieldOf("quality", 0).forGetter(RealmMapData::quality),
            Codec.BOOL.optionalFieldOf("corrupted", false).forGetter(RealmMapData::corrupted)
        ).apply(instance, RealmMapData::fromCodec)
    );

    // Computed properties
    public float getTotalItemQuantity() {
        float base = quality / 100f;  // Quality gives IIQ
        for (RealmModifier mod : modifiers) {
            if (mod.type() == ModifierType.ITEM_QUANTITY) {
                base += mod.value() / 100f;
            }
        }
        return base;
    }

    public float getTotalItemRarity() {
        float base = 0;
        for (RealmModifier mod : modifiers) {
            if (mod.type() == ModifierType.ITEM_RARITY) {
                base += mod.value() / 100f;
            }
        }
        return base;
    }

    public float getTotalXpBonus() {
        float base = 0;
        for (RealmModifier mod : modifiers) {
            if (mod.type() == ModifierType.EXPERIENCE_BONUS) {
                base += mod.value() / 100f;
            }
        }
        return base;
    }

    public int getDifficultyRating() {
        int total = modifiers.stream()
            .mapToInt(RealmModifier::difficultyContribution)
            .sum();
        return Math.min(5, Math.max(1, total / 3));
    }

    // Builder pattern
    public Builder toBuilder() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        // Builder implementation...
    }
}
```

## Data Flow Diagrams

### Map Activation Flow

```
┌───────────────────────────────────────────────────────────────────────────┐
│                           MAP ACTIVATION FLOW                             │
├───────────────────────────────────────────────────────────────────────────┤
│                                                                           │
│  Player right-clicks Realm Map                                            │
│       │                                                                   │
│       ▼                                                                   │
│  RealmMapUseListener.onItemUse()                                          │
│       │                                                                   │
│       ├── Extract RealmMapComponent from item                             │
│       ├── Validate: player not already in realm                           │
│       ├── Validate: map not already used (UUID check)                     │
│       │                                                                   │
│       ▼                                                                   │
│  RealmsManager.createRealm(mapData, player)                               │
│       │                                                                   │
│       ├── RealmInstanceFactory.createRealm()                              │
│       │       │                                                           │
│       │       ├── templateManager.selectTemplate()                        │
│       │       ├── InstancesPlugin.spawnInstance()                         │
│       │       ├── mobSpawner.spawnMonstersForRealm()                      │
│       │       └── return CompletableFuture<RealmInstance>                 │
│       │                                                                   │
│       ├── Register in activeRealms map                                    │
│       ├── Fire RealmCreatedEvent                                          │
│       │                                                                   │
│       ▼                                                                   │
│  RealmPortalManager.createPortal(player.position, realm)                  │
│       │                                                                   │
│       └── Spawn portal entity at player location                          │
│                                                                           │
│  Player can now enter portal                                              │
│                                                                           │
└───────────────────────────────────────────────────────────────────────────┘
```

### Completion Flow

```
┌───────────────────────────────────────────────────────────────────────────┐
│                          COMPLETION FLOW                                  │
├───────────────────────────────────────────────────────────────────────────┤
│                                                                           │
│  Monster dies in Realm                                                    │
│       │                                                                   │
│       ▼                                                                   │
│  RealmMobDeathListener.onComponentAdded()                                 │
│       │                                                                   │
│       ├── Get RealmMobComponent → realmId                                 │
│       ├── Get RealmInstance from RealmsManager                            │
│       ├── Extract killer UUID                                             │
│       │                                                                   │
│       ▼                                                                   │
│  RealmCompletionTracker.onMonsterKilled(killerId)                         │
│       │                                                                   │
│       ├── Decrement remainingMonsters                                     │
│       ├── Add killer to participatingPlayers                              │
│       │                                                                   │
│       ├── if remainingMonsters <= 0:                                      │
│       │       │                                                           │
│       │       ▼                                                           │
│       │   RealmsManager.triggerCompletion(realm)                          │
│       │       │                                                           │
│       │       ├── Set state = ENDING                                      │
│       │       ├── distributeCompletionRewards()                           │
│       │       ├── Fire RealmCompletedEvent                                │
│       │       └── scheduleCleanup()                                       │
│       │                                                                   │
│       └── Update progress UI for all players                              │
│                                                                           │
└───────────────────────────────────────────────────────────────────────────┘
```

## Integration Points

### With LevelingManager

```java
// In RealmMobDeathListener
public void onMobKilled(Ref<EntityStore> mob, UUID killerId, RealmInstance realm) {
    // Get base XP
    int baseXp = levelingManager.calculateMobXp(mob);

    // Apply realm XP bonus
    float xpBonus = realm.getMapData().getTotalXpBonus();
    int finalXp = Math.round(baseXp * (1 + xpBonus));

    // Award XP
    levelingManager.addXp(killerId, finalXp, XpSource.REALM_KILL);
}
```

### With MobScalingManager

```java
// In RealmMobSpawner
public void applyRealmScaling(Holder<EntityStore> mob, RealmMapData mapData) {
    // Force fixed level (not dynamic)
    MobScalingComponent scaling = new MobScalingComponent(
        mapData.getLevel(),
        false  // usePlayerLevel = false
    );
    mob.addComponent(MobScalingComponent.TYPE, scaling);

    // Apply modifier stat bonuses
    for (RealmModifier mod : mapData.getModifiers()) {
        switch (mod.type()) {
            case MONSTER_HEALTH -> applyHealthBonus(mob, mod.value());
            case MONSTER_DAMAGE -> applyDamageBonus(mob, mod.value());
            case MONSTER_SPEED -> applySpeedBonus(mob, mod.value());
        }
    }
}
```

### With LootListener

```java
// In modified LootListener
public void calculateLoot(Ref<EntityStore> mob, UUID killerId) {
    // Check if in realm
    Optional<RealmInstance> realm = realmsManager.getPlayerRealm(killerId);

    float iiq = 0;
    float iir = 0;

    if (realm.isPresent()) {
        RealmMapData mapData = realm.get().getMapData();
        iiq += mapData.getTotalItemQuantity();
        iir += mapData.getTotalItemRarity();
    }

    // Add player bonuses
    iiq += playerStats.getItemQuantity(killerId);
    iir += playerStats.getItemRarity(killerId);

    // Calculate drops with bonuses
    return lootCalculator.calculate(mob, iiq, iir);
}
```

## Database Schema

```sql
-- Realm completion statistics
CREATE TABLE rpg_realm_completions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    map_id VARCHAR(36) NOT NULL,
    player_id VARCHAR(36) NOT NULL,
    completed_at TIMESTAMP NOT NULL,
    map_level INT NOT NULL,
    map_rarity VARCHAR(20) NOT NULL,  -- Uses GearRarity: COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, MYTHIC
    biome_type VARCHAR(30) NOT NULL,
    monsters_killed INT NOT NULL,
    total_monsters INT NOT NULL,
    time_taken_seconds INT NOT NULL,
    completion_type VARCHAR(20) NOT NULL,  -- FULL, TIMEOUT, ABANDONED
    modifiers TEXT,  -- JSON array of modifier types
    INDEX idx_player (player_id),
    INDEX idx_completed_at (completed_at)
);

-- Player realm statistics (aggregate)
CREATE TABLE rpg_realm_player_stats (
    player_id VARCHAR(36) PRIMARY KEY,
    total_realms_entered INT DEFAULT 0,
    total_realms_completed INT DEFAULT 0,
    total_monsters_killed BIGINT DEFAULT 0,
    total_time_in_realms_seconds BIGINT DEFAULT 0,
    highest_level_completed INT DEFAULT 0,
    last_realm_at TIMESTAMP NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## Service Registration

In `TrailOfOrbis.start()`:

```java
// Initialize RealmsManager
realmsManager = new RealmsManager(this);
realmsManager.setup();  // Register ECS components/systems
realmsManager.start();  // Load templates, configs

// Register service
ServiceRegistry.register(RealmsService.class, realmsManager);
```

In `TrailOfOrbis.shutdown()`:

```java
// Shutdown in reverse order
if (realmsManager != null) {
    realmsManager.shutdown();
}
ServiceRegistry.unregister(RealmsService.class);
```
