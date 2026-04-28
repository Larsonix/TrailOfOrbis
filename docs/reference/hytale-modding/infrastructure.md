# Infrastructure — Hytale Modding Reference

Events, commands, packets, sound, particles, camera, scheduling, config, codecs, assets, permissions, i18n, dynamic light, debug rendering.

**Sources**: All 39 analyzed mods.

---

## Events

### Registration

```java
// Instance-scoped
getEventRegistry().register(PlayerConnectEvent.class, event -> { /* ... */ });

// Global-scoped (all worlds)
getEventRegistry().registerGlobal(AddWorldEvent.class, event -> { /* ... */ });
```

Both return a registration handle. Auto-unregistered on plugin disable.

### Event Catalog

#### Player Events

| Event | When It Fires | Key Data |
|-------|--------------|----------|
| `PlayerConnectEvent` | Player connects (before world join) | `.getPlayerRef()` |
| `PlayerReadyEvent` | Fully loaded, assets received | `.getPlayerRef()` |
| `PlayerDisconnectEvent` | Player disconnects | `.getPlayerRef()` |
| `AddPlayerToWorldEvent` | Player added to a world | `.getPlayerRef()`, `.getWorld()` |
| `DrainPlayerFromWorldEvent` | Player about to leave a world | `.getPlayerRef()`, `.getWorld()` |
| `PlayerMouseButtonEvent` | Mouse click | `.getPlayerRef()`, `.getButton()` |
| `PlayerInteractEvent` | E-key interact with entity | `.getPlayerRef()`, `.getTargetRef()` |

**Lifecycle**: `Connect` → `AddToWorld` → `Ready` → ... → `DrainFromWorld` → `Disconnect`

#### World Events

| Event | When It Fires | Key Data |
|-------|--------------|----------|
| `AddWorldEvent` | World created/loaded | `.getWorld()` |
| `RemoveWorldEvent` | World being removed | `.getWorld()` |
| `BootEvent` | Server boot complete | (none) |
| `ShutdownEvent` | Server shutting down | (none) |

#### Block Events

| Event | When It Fires | Key Data |
|-------|--------------|----------|
| `UseBlockEvent.Pre` | About to use block (cancelable) | `.getPlayerRef()`, `.getBlockPos()`, `.getBlockType()` |
| `UseBlockEvent.Post` | Block used (after processing) | same as Pre |
| `PlaceBlockEvent` | Block placed | `.getPlayerRef()`, `.getBlockPos()`, `.getBlockType()` |
| `BreakBlockEvent` | Block broken | `.getPlayerRef()`, `.getBlockPos()`, `.getBlockType()` |
| `DamageBlockEvent` | Block damaged (not yet broken) | `.getPlayerRef()`, `.getBlockPos()` |

#### Item Events

| Event | When It Fires | Key Data |
|-------|--------------|----------|
| `DropItemEvent.Drop` | Item dropped | `.getItemStack()`, `.getPosition()` |
| `CraftRecipeEvent.Pre` | About to craft (cancelable) | `.getPlayerRef()`, `.getRecipe()` |
| `CraftRecipeEvent.Post` | Crafting completed | `.getPlayerRef()`, `.getRecipe()`, `.getResult()` |
| `InventoryChangeEvent` | Any inventory change | `.getInventory()` |
| `LivingEntityInventoryChangeEvent` | Living entity inventory change | `.getEntityRef()`, `.getSlot()` |

#### Other Events

| Event | When It Fires | Key Data |
|-------|--------------|----------|
| `ChunkUnloadEvent` | Chunk unloading | `.getChunkPos()` |
| `KillFeedEvent.KillerMessage` | Kill feed generated | `.getKiller()`, `.getVictim()`, `.getMessage()` |
| `LoadedAssetsEvent<K,V,M>` | Typed assets loaded | `.getAssetMap()`, `.getLoadedAssets()` |
| `RemovedAssetsEvent<K,V,M>` | Typed assets removed | `.getAssetMap()`, `.getRemovedKeys()` |

---

## Commands

### Command Types

| Base Class | Use Case |
|-----------|----------|
| `AbstractPlayerCommand` | In-game only — `execute(PlayerRef player)` |
| `AbstractCommand` | Console or player — `execute(CommandSender sender)` |
| `AbstractCommandCollection` | Group of sub-commands under a prefix |

### Defining a Command

```java
public class MyCommand extends AbstractPlayerCommand {
    private final RequiredArg<String> targetArg = new RequiredArg<>("target", ArgTypes.STRING);

    public MyCommand() {
        super("mycommand");
        addArg(targetArg);
    }

    @Override
    protected void execute(PlayerRef player) {
        String target = targetArg.get();
    }
}
```

**Arg types**: `ArgTypes.STRING`, `.INTEGER`, `.FLOAT`, `.BOOLEAN`, `.PLAYER`

### Registration & Programmatic Execution

```java
getCommandRegistry().registerCommand(new MyCommand());

CommandManager.get().handleCommand(playerRef, "/mycommand arg1");
CommandManager.get().handleCommand(ConsoleSender.INSTANCE, "/mycommand arg1");
```

---

## Packets & Protocol

### Intercepting Packets

```java
// Outbound (server → client) — return packet to send, null to suppress
PacketAdapters.registerOutbound(packet -> {
    if (packet instanceof UpdatePlayerInventory inv) { /* modify */ }
    return packet;
});

// Inbound (client → server)
PacketAdapters.registerInbound(packet -> { return packet; });
```

### Sending Packets

```java
playerRef.getPacketHandler().write(packet);        // cached
playerRef.getPacketHandler().writeNoCache(packet);  // uncached (dynamic data)
```

### Key Packet Types

| Packet | Purpose |
|--------|---------|
| `UpdatePlayerInventory` | Full inventory sync |
| `OpenWindow` / `UpdateWindow` / `UpdateItems` | Container UI |
| `UpdateAnchorUI` | Anchored UI elements |
| `SetServerCamera` | Client camera control |
| `UpdateWorldMap` / `ClearWorldMap` | World map data |
| `SetMovementStates` | Movement overrides (fly, noclip) |
| `SetGameMode` | Client game mode |
| `PlaySoundEvent2D` / `PlaySoundEvent3D` | Sound playback |
| `SpawnModelParticles` | Particle effects |
| `UpdateTranslations` | Push translation strings |
| `AssetInitialize` / `AssetPart` / `AssetFinalize` | Asset streaming sequence |

### Sub-Packet Handlers

```java
public class MyPacketHandler extends SubPacketHandler {
    @Override
    public void handle(PlayerRef player, ByteBuf data) { /* ... */ }
}

ServerManager.get().registerSubPacketHandlers(myHandler);
```

---

## Sound

```java
int soundIndex = SoundEvent.getAssetMap().getIndex("UI_Click");

// 3D positional (heard by nearby players)
SoundUtil.playSoundEvent3d(soundIndex, SoundCategory.SFX, position, store);

// 2D to specific player
SoundUtil.playSoundEvent2dToPlayer(playerRef, soundIndex, SoundCategory.SFX);

// Direct packet construction (volume, pitch control)
new PlaySoundEvent2D(soundIndex, SoundCategory.SFX, 0.8f, 1.2f);
new PlaySoundEvent3D(soundIndex, SoundCategory.SFX, position, volume, pitch, maxDistance);
```

---

## Particles

```java
// Simple — visible to all nearby
ParticleUtil.spawnParticleEffect(effectId, position, store);

// Targeted — full transform, specific viewers
ParticleUtil.spawnParticleEffect(
    effectId, position, pitch, yaw, roll, scale,
    0,              // flags
    viewerRefs,     // Collection<PlayerRef> — null for all
    store
);
```

---

## Camera

```java
ServerCameraSettings settings = new ServerCameraSettings();
settings.setDistance(10.0f);
settings.setRotation(new Vector3f(30, 0, 0));       // pitch, yaw, roll
settings.setPositionOffset(new Vector3f(0, 2, 0));
settings.setAttachedToEntityId(entityId);            // follow entity
settings.setMovementMultiplier(0.5f);

SetServerCamera packet = new SetServerCamera(
    ClientCameraView.Custom, true /* lock input */, settings
);
playerRef.getPacketHandler().write(packet);
```

Reset to default by sending a packet with the default view mode.

---

## Scheduling

```java
// One-shot delayed
ScheduledFuture<?> future = HytaleServer.SCHEDULED_EXECUTOR.schedule(
    () -> { /* ... */ }, 5, TimeUnit.SECONDS
);

// Periodic repeating
ScheduledFuture<?> future = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(
    () -> { /* ... */ }, 0, 1, TimeUnit.SECONDS
);

// Register for auto-cancel on plugin disable
plugin.getTaskRegistry().registerTask(future);
```

### World Thread Dispatch

Entity and world operations **must** run on the world thread:

```java
world.execute(() -> { /* safe entity/block ops */ });

// Delayed world-thread execution
CompletableFuture.delayedExecutor(2, TimeUnit.SECONDS, world)
    .execute(() -> { /* runs on world thread after 2s */ });
```

---

## Config

```java
// Preloaded config (recommended)
Config<MyConfig> config = Config.preloadedConfig(
    plugin.getDataDirectory(), "my-config", MyConfig.CODEC, MyConfig.defaults()
);
config.load().join();
MyConfig cfg = config.get();
config.save();

// Shorthand
Config<MyConfig> config = plugin.withConfig("my-config", MyConfig.CODEC);
```

Data directory resolves to `mods/<modId>_<PluginName>/config/`.

---

## Codecs

### Builder Codecs

```java
// Basic
public static final Codec<MyConfig> CODEC = BuilderCodec.builder(MyConfig.class, MyConfig::new)
    .field("maxHealth", Codec.INTEGER, MyConfig::getMaxHealth, MyConfig::setMaxHealth)
    .field("name", Codec.STRING, MyConfig::getName, MyConfig::setName)
    .build();

// Asset variant (for asset-loaded types)
public static final Codec<MyAsset> CODEC = AssetBuilderCodec.builder(MyAsset.class, MyAsset::new)
    .field("identifier", Codec.STRING, MyAsset::getId, MyAsset::setId)
    .build();
```

### Polymorphic Codec

Dispatches on a `"Type"` discriminator field:

```java
public static final Codec<BaseEffect> CODEC = new StringCodecMapCodec<>("Type")
    .register("Damage", DamageEffect.CODEC)
    .register("Heal", HealEffect.CODEC)
    .register("Buff", BuffEffect.CODEC);
// JSON: { "Type": "Damage", "Amount": 50 }
```

### Recursive Codecs

```java
CodecStore.STATIC.putCodecSupplier("my_type", () -> MyType.CODEC);  // lazy registration
Codec<MyType> ref = CodecStore.STATIC.getCodec("my_type");          // lazy resolution
```

### Primitive Types

`Codec.INTEGER`, `.STRING`, `.FLOAT`, `.BOOLEAN`, `.DURATION`, `.UUID_STRING`, `.PATH`

---

## Assets

### Defining & Registering an Asset Store

```java
HytaleAssetStore<String, MyAsset> store = HytaleAssetStore
    .builder(MyAsset.class, new DefaultAssetMap<>())
    .setPath("Server/MyMod/Data/")
    .setCodec(MyAsset.CODEC)
    .setKeyFunction(MyAsset::getId)
    .build();

plugin.getAssetRegistry().register(store);
```

### Accessing & Reacting to Assets

```java
MyAsset asset = store.getAssetMap().get("some_id");

// Load event
getEventRegistry().register(LoadedAssetsEvent.class, event -> {
    if (event.getAssetMap() == store.getAssetMap()) {
        Map<String, MyAsset> loaded = event.getLoadedAssets();
    }
});

// Removal event (hot-reload)
getEventRegistry().register(RemovedAssetsEvent.class, event -> {
    if (event.getAssetMap() == store.getAssetMap()) {
        Set<String> removed = event.getRemovedKeys();
    }
});
```

### Asset Pack Management

```java
AssetModule.get().getAssetPacks();       // list all packs
AssetModule.get().registerPack(pack);    // register custom pack
AssetModule.get().getAssetMonitor();     // hot-reload monitor
```

### Asset Streaming

Send assets to clients at runtime via packet sequence: `AssetInitialize` → `AssetPart` (one or more) → `AssetFinalize`. Use `CommonAsset` for data representation.

### Asset Patch System

Override base game assets without replacing files:

```json
{
  "_BaseAssetPath": "Server/Item/Items/Weapon_Sword_Iron.json",
  "_op": "add",
  "CustomData": { "MyMod_Enchantment": "Fire" }
}
```

Patches merge fields. Applied in asset pack load order.

---

## Dynamic Light

```java
DynamicLight light = store.getComponent(ref, DynamicLight.getComponentType());
light.setColorLight(new ColorLight(12.0f, 1.0f, 0.8f, 0.2f));  // radius, r, g, b
```

---

## Permissions

```java
PermissionsModule.get().addUserPermission(playerUUID, Set.of("mymod.admin", "mymod.teleport"));
PermissionsModule.get().removeUserPermission(playerUUID, Set.of("mymod.admin"));
```

---

## Internationalization (i18n)

```java
// Server-side lookup
String msg = I18nModule.get().getMessage("en-US", "mymod.welcome_msg");

// Push translations to client
playerRef.getPacketHandler().write(new UpdateTranslations(translationMap));
```

Translation files: `Server/Languages/<locale>/` in asset packs, loaded automatically.

---

## Messages

```java
Message msg = Message.raw("Hello ").color("#ffffff")
    .append(Message.raw("World").color("#ff4444"));

Message link = Message.raw("Click here").color("#5599ff").link("https://example.com");

player.sendMessage(msg);
```

---

## Debug Rendering

```java
Matrix4f transform = new Matrix4f().translate(x, y, z).scale(size);
Vector4f color = new Vector4f(1.0f, 0.0f, 0.0f, 1.0f);  // RGBA

DebugUtils.add(world, DebugShape.Cube, transform, color, 0.5f, 0);
```

Shapes: `Cube`, `Sphere`, `Line`, etc. Visible to players with debug view enabled.

---

## Quick Reference

| Operation | Code |
|-----------|------|
| Register event | `getEventRegistry().register(EventClass.class, handler)` |
| Register command | `getCommandRegistry().registerCommand(cmd)` |
| Intercept packet | `PacketAdapters.registerOutbound(filter)` |
| Send packet | `playerRef.getPacketHandler().write(packet)` |
| Play 3D sound | `SoundUtil.playSoundEvent3d(idx, SoundCategory.SFX, pos, store)` |
| Play 2D sound | `SoundUtil.playSoundEvent2dToPlayer(player, idx, SoundCategory.SFX)` |
| Spawn particles | `ParticleUtil.spawnParticleEffect(id, pos, store)` |
| Schedule task | `HytaleServer.SCHEDULED_EXECUTOR.schedule(r, delay, unit)` |
| World thread | `world.execute(() -> { ... })` |
| Load config | `Config.preloadedConfig(dir, name, codec, defaults)` |
| Register assets | `plugin.getAssetRegistry().register(store)` |
| Set permission | `PermissionsModule.get().addUserPermission(uuid, perms)` |
| Dynamic light | `DynamicLight.setColorLight(new ColorLight(radius, r, g, b))` |
| Debug shape | `DebugUtils.add(world, DebugShape.Cube, matrix, color, alpha, flags)` |
