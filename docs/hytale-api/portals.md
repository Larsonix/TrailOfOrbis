# Hytale Portal System

The portal system handles teleportation between worlds via portal blocks. It is implemented by the built-in `PortalsPlugin` and covers portal device blocks (ChunkStore ECS), portal world resources (EntityStore ECS), instance spawning/removal, and a client-side timer UI driven by `UpdatePortal` packets. Our Realms system builds on top of this to create RPG dungeon instances.

**Package**: `com.hypixel.hytale.builtin.portals`

## Quick Reference

| Task | Approach |
|------|----------|
| Get PortalsPlugin singleton | `PortalsPlugin.getInstance()` |
| Get PortalDevice at block position | `BlockModule.getComponent(PortalDevice.getComponentType(), world, x, y, z)` |
| Create PortalDevice on block entity | `store.putComponent(blockRef, PortalsPlugin.getInstance().getPortalDeviceComponentType(), device)` |
| Set portal destination | `portalDevice.setDestinationWorld(targetWorld)` |
| Get PortalWorld resource from world | `entityStore.getResource(PortalWorld.getResourceType())` |
| Initialize PortalWorld | `portalWorld.init(portalType, timeLimit, removalCondition, gameplayConfig)` |
| Check if world is a portal world | `portalWorld.exists()` |
| Get remaining time | `portalWorld.getRemainingSeconds(world)` |
| Override remaining time | `PortalWorld.setRemainingSeconds(world, seconds)` |
| Suppress vanilla timer UI | `playerRef.getPacketHandler().write(new UpdatePortal(null, null))` |
| Change portal block state | `world.setBlockInteractionState(position, blockType, stateKey)` |
| Exit portal world (return player) | `InstancesPlugin.exitInstance(ref, commandBuffer)` |
| Teleport player into portal world | `InstancesPlugin.teleportPlayerToInstance(ref, commandBuffer, targetWorld, returnTransform)` |
| Look up PortalType asset | `PortalType.getAssetMap().getAsset("Henges")` |

## PortalsPlugin

Singleton built-in plugin that registers all portal ECS types, systems, interactions, and commands.

```java
import com.hypixel.hytale.builtin.portals.PortalsPlugin;

PortalsPlugin portals = PortalsPlugin.getInstance();
```

### Registered ECS Types

| Type | Registry | Class | Purpose |
|------|----------|-------|---------|
| `portalResourceType` | EntityStore resource | `PortalWorld` | Per-world portal state (timer, spawn point, died-in-world tracking) |
| `portalDeviceComponentType` | ChunkStore component | `PortalDevice` | Per-block-entity portal config and destination |
| `voidEventComponentType` | EntityStore component | `VoidEvent` | Void invasion event state |
| `voidPortalComponentType` | EntityStore component | `VoidSpawner` | Void invasion spawner |

Access type objects via getters:

```java
// For PortalDevice (ChunkStore component)
ComponentType<ChunkStore, PortalDevice> deviceType = PortalsPlugin.getInstance().getPortalDeviceComponentType();

// For PortalWorld (EntityStore resource)
ResourceType<EntityStore, PortalWorld> worldType = PortalsPlugin.getInstance().getPortalResourceType();
```

### Registered Interactions

| Codec Key | Class | Purpose |
|-----------|-------|---------|
| `"Portal"` | `EnterPortalInteraction` | Player walks into active portal block, teleports to destination world |
| `"PortalReturn"` | `ReturnPortalInteraction` | Player walks into return portal in destination world, exits instance |

These are registered on `Interaction.CODEC` and referenced from block JSON `Interactions.CollisionEnter`.

### Registered Page Supplier

| Codec Key | Class | Purpose |
|-----------|-------|---------|
| `"PortalDevice"` | `PortalDevicePageSupplier` | UI page shown when interacting with a Portal_Device block |

Registered on `OpenCustomUIInteraction.PAGE_CODEC`. We override this with `RealmPortalDevicePageSupplier` to intercept realm map usage.

### Key Constants

```java
PortalsPlugin.MAX_CONCURRENT_FRAGMENTS  // = 4, max simultaneous portal worlds
```

### Event Handlers

The plugin registers a global `RemoveWorldEvent` handler that automatically turns off all portal devices pointing to a removed world. This happens by iterating all other worlds and calling `PortalInvalidDestinationSystem.turnOffPortalsInWorld()`.

## PortalDevice Component

ChunkStore ECS component attached to block entities (e.g., Portal_Device blocks). Stores the portal's configuration and destination world.

```java
import com.hypixel.hytale.builtin.portals.components.PortalDevice;
import com.hypixel.hytale.builtin.portals.components.PortalDeviceConfig;
```

### Reading a PortalDevice

```java
// Option 1: Via BlockModule convenience method
PortalDevice device = BlockModule.getComponent(
    PortalDevice.getComponentType(), world, x, y, z);

// Option 2: Via block entity ref
Ref<ChunkStore> blockRef = BlockModule.getBlockEntity(world, x, y, z);
Store<ChunkStore> store = world.getChunkStore().getStore();
PortalDevice device = store.getComponent(blockRef,
    PortalsPlugin.getInstance().getPortalDeviceComponentType());
```

### Creating a PortalDevice

```java
// Get or create block entity ref
@SuppressWarnings("deprecation")
Ref<ChunkStore> blockRef = BlockModule.ensureBlockEntity(chunk, x, y, z);

// Create config and device
PortalDeviceConfig config = new PortalDeviceConfig();
PortalDevice device = new PortalDevice(config, blockType.getId());
device.setDestinationWorld(realmWorld);

// Attach to block entity
store.putComponent(blockRef,
    PortalsPlugin.getInstance().getPortalDeviceComponentType(), device);
```

### PortalDevice API

| Method | Returns | Description |
|--------|---------|-------------|
| `getConfig()` | `PortalDeviceConfig` | Portal block state configuration |
| `getBaseBlockTypeKey()` | `String` | Asset key of the base block type |
| `getBaseBlockType()` | `BlockType` (nullable) | Resolved block type asset |
| `getDestinationWorldUuid()` | `UUID` (nullable) | UUID of destination world |
| `getDestinationWorld()` | `World` (nullable) | Destination world if alive, else null |
| `setDestinationWorld(World)` | `void` | Sets destination via `world.getWorldConfig().getUuid()` |

### PortalDeviceConfig

Controls the visual states of the portal block. Each state maps to a block state variant defined in the block type's asset JSON.

| Method | Returns | Default | Description |
|--------|---------|---------|-------------|
| `getOnState()` | `String` | `"Active"` | Block state when portal is active (linked to a world) |
| `getSpawningState()` | `String` | `"Spawning"` | Transition state while instance is being created |
| `getOffState()` | `String` | `"default"` | Block state when inactive |
| `getReturnBlock()` | `String` (nullable) | `null` | Block type placed at spawn point in destination world |
| `getBlockStates()` | `String[]` | All 3 states | Array of all state keys for validation |
| `areBlockStatesValid(BlockType)` | `boolean` | - | Validates all states resolve to real block types |

### Changing Block State

```java
import com.hypixel.hytale.builtin.portals.utils.BlockTypeUtils;

// Activate portal (show visual effect)
BlockType currentType = world.getBlockType(x, y, z);
BlockType onState = BlockTypeUtils.getBlockForState(currentType, config.getOnState());
if (onState != null) {
    world.setBlockInteractionState(position, currentType, config.getOnState());
}

// Deactivate portal
world.setBlockInteractionState(position, currentType, config.getOffState());
```

### BlockTypeUtils

```java
import com.hypixel.hytale.builtin.portals.utils.BlockTypeUtils;

// Resolves a state name to a concrete BlockType
// If state is "default", returns the base block type
// Otherwise looks up blockType.getBlockForState(state)
BlockType resolved = BlockTypeUtils.getBlockForState(baseBlockType, stateName);
```

## PortalWorld Resource

EntityStore ECS resource that marks a world as a "portal world" (an instanced temporary world). Every world's EntityStore has a `PortalWorld` resource, but it is only initialized (has a PortalType) for actual portal worlds.

```java
import com.hypixel.hytale.builtin.portals.resources.PortalWorld;

Store<EntityStore> entityStore = world.getEntityStore().getStore();
PortalWorld portalWorld = entityStore.getResource(PortalWorld.getResourceType());
```

### Initialization

```java
import com.hypixel.hytale.builtin.portals.integrations.PortalGameplayConfig;
import com.hypixel.hytale.builtin.portals.integrations.PortalRemovalCondition;
import com.hypixel.hytale.server.core.asset.type.portalworld.PortalType;

PortalType portalType = PortalType.getAssetMap().getAsset("Henges");
PortalRemovalCondition removalCondition = new PortalRemovalCondition(300.0); // 5 min
PortalGameplayConfig gameplayConfig = new PortalGameplayConfig();

portalWorld.init(portalType, 300, removalCondition, gameplayConfig);
portalWorld.setSpawnPoint(spawnTransform);
```

After `init()`, `portalWorld.exists()` returns `true`, which is the gate for:
- `EnterPortalInteraction` allowing entry
- `ReturnPortalInteraction` allowing exit
- `PortalTrackerSystems.TrackerSystem` sending the initial `UpdatePortal` packet
- `PortalTrackerSystems.UiTickingSystem` sending timer updates

### PortalWorld API

| Method | Returns | Description |
|--------|---------|-------------|
| `exists()` | `boolean` | True if `init()` was called with a valid PortalType |
| `getPortalType()` | `PortalType` (nullable) | The portal type asset |
| `getTimeLimitSeconds()` | `int` | Configured time limit |
| `getElapsedSeconds(World)` | `double` | Seconds since timer started |
| `getRemainingSeconds(World)` | `double` | Seconds remaining |
| `setRemainingSeconds(World, double)` | `void` (static) | Override the remaining time |
| `getSpawnPoint()` | `Transform` (nullable) | Where players spawn in this world |
| `setSpawnPoint(Transform)` | `void` | Set spawn location |
| `getDiedInWorld()` | `Set<UUID>` | Players who died here (blocks re-entry) |
| `getSeesUi()` | `Set<UUID>` | Players currently seeing the timer UI |
| `getGameplayConfig()` | `PortalGameplayConfig` | Gameplay config (falls back to PortalType's config) |
| `getVoidEventConfig()` | `VoidEventConfig` (nullable) | Void invasion settings |
| `isVoidEventActive()` | `boolean` | Whether a void invasion is in progress |
| `createFullPacket(World)` | `UpdatePortal` | Full packet with definition + state (sent on first entry) |
| `createUpdatePacket(World)` | `UpdatePortal` | State-only packet (sent on timer ticks) |

## Portal Timer UI (UpdatePortal Packet)

The client displays a portal timer HUD driven by `UpdatePortal` packets. Understanding this packet is critical for suppressing the vanilla timer when using a custom HUD.

```java
import com.hypixel.hytale.protocol.packets.interface_.UpdatePortal;
import com.hypixel.hytale.protocol.packets.interface_.PortalDef;
import com.hypixel.hytale.protocol.packets.interface_.PortalState;
```

### Packet Structure

```java
public class UpdatePortal implements Packet, ToClientPacket {
    @Nullable public PortalState state;      // Current timer state
    @Nullable public PortalDef definition;   // Portal metadata (sent once)
}

public class PortalDef {
    @Nullable public String nameKey;         // Translation key for display name
    public int explorationSeconds;            // Exploration phase duration
    public int breachSeconds;                 // Void breach phase duration (0 if disabled)
}

public class PortalState {
    public int remainingSeconds;              // Current countdown
    public boolean breaching;                 // True during void breach phase
}
```

### How the Vanilla Timer System Works

1. **Player enters portal world**: `PortalTrackerSystems.TrackerSystem.onEntityAdded()` fires and sends a full `UpdatePortal` packet (definition + state) to the player.

2. **Every ~1 second**: `PortalTrackerSystems.UiTickingSystem.tick()` sends an `UpdatePortal` with updated state (remaining seconds). This only fires when:
   - `portalWorld.exists() == true`
   - `instanceData.getTimeoutTimer() != null`

3. **Player leaves portal world**: `TrackerSystem.onEntityRemove()` sends `UpdatePortal(null, null)` to clear the UI.

### Suppressing the Vanilla Timer

To use a custom timer HUD instead of the vanilla one, you must handle two things:

**1. Prevent UiTickingSystem from sending updates** by setting `timeoutTimer = null`:

```java
world.execute(() -> {
    Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
    InstanceDataResource instanceData = chunkStore.getResource(
        InstanceDataResource.getResourceType());
    instanceData.setTimeoutTimer(null);
});
```

**2. Hide the initial packet** sent by TrackerSystem (which fires regardless of `timeoutTimer`):

```java
// In PlayerReadyEvent handler for realm worlds:
PortalWorld portalWorld = store.getResource(PortalWorld.getResourceType());
if (portalWorld != null && portalWorld.exists()) {
    playerRef.getPacketHandler().write(new UpdatePortal(null, null));
    portalWorld.getSeesUi().remove(playerId);
}
```

**CRITICAL**: These must happen atomically in a single `world.execute()` block. If separated, system ticks can race between operations and re-enable the timer. See `RealmInstanceFactory.initializePortalWorldAndDisableVanillaTimer()` for the full pattern.

## PortalRemovalCondition

Controls when a portal world is automatically removed. Implements `RemovalCondition` (from the Instances plugin).

```java
import com.hypixel.hytale.builtin.portals.integrations.PortalRemovalCondition;
```

### Constructor

```java
// Default: 60 second timeout
PortalRemovalCondition condition = new PortalRemovalCondition();

// Custom timeout
PortalRemovalCondition condition = new PortalRemovalCondition(300.0); // 5 minutes
```

### How It Works

`PortalRemovalCondition` combines two removal strategies:

1. **Timeout**: After the configured time limit, the world is marked for removal. The timer starts when `InstanceDataResource.getTimeoutTimer()` is set (which happens when `TimeoutCondition.shouldRemoveWorld()` runs and sees a null timer -- it sets it to `now + timeout`).

2. **WorldEmpty**: 90 seconds after the last player leaves, the world is removed.

### API

| Method | Returns | Description |
|--------|---------|-------------|
| `getElapsedSeconds(World)` | `double` | Time since timer started |
| `getRemainingSeconds(World)` | `double` | Time remaining (from `InstanceDataResource.getTimeoutTimer()`) |
| `setRemainingSeconds(World, double)` | `void` (static) | Override remaining time by updating `timeoutTimer` |
| `shouldRemoveWorld(Store<ChunkStore>)` | `boolean` | True if timeout expired OR world empty for 90s |

### Disabling Vanilla Removal

To control world lifetime yourself (as our Realms system does), clear the removal conditions array:

```java
InstanceWorldConfig worldConfig = InstanceWorldConfig.ensureAndGet(world.getWorldConfig());
worldConfig.setRemovalConditions(); // Empty array = no auto-removal
```

**WARNING**: `TimeoutCondition.shouldRemoveWorld()` has a side effect -- if `timeoutTimer` is null, it **sets** the timer to `now + timeout`. You must clear `removalConditions` before setting `timeoutTimer` to null, or a system tick will race and re-set it. Do both in one `world.execute()`.

## PortalGameplayConfig

World-level gameplay configuration for portal worlds. Registered on `GameplayConfig.PLUGIN_CODEC` with key `"Portal"`.

```java
import com.hypixel.hytale.builtin.portals.integrations.PortalGameplayConfig;
```

### API

| Method | Returns | Description |
|--------|---------|-------------|
| `getVoidEvent()` | `VoidEventConfig` (nullable) | Void invasion configuration |

### Reading From a World

```java
PortalGameplayConfig portalConfig = world.getGameplayConfig()
    .getPluginConfig()
    .get(PortalGameplayConfig.class);
```

## PortalSpawnConfig (New in 2026.03.26)

Configuration for portal spawn behavior within portal worlds.

**Import:** `com.hypixel.hytale.server.core.asset.type.portalworld.PortalSpawnConfig`

| Method | Returns | Description |
|--------|---------|-------------|
| `isSpawningReturnPortal()` | `boolean` | Whether a return portal spawns in the destination |
| `getSpawnProviderOverride()` | `SpawnProvider` | Custom spawn location (null = default) |
| `getReturnBlockOverride()` | `BlockType` | Custom block type for return portal |
| `getReturnBlockOverrideId()` | `String` | Block type ID string |

## Portal Type JSON

Portal types define the metadata for a category of portal worlds. Located at `Server/PortalTypes/` in asset packs.

**Schema**: `Assets/Schema/PortalType.json`

### Fields

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `Parent` | `string` | - | Inherit from named PortalType |
| `InstanceId` | `string` | - | Instance folder name in `Assets/Server/Instances/` |
| `Description` | `PortalDescription` | - | Display metadata (see below) |
| `CursedItems` | `string[]` | `[]` | Item IDs that become cursed when dropped in this portal |
| `VoidInvasionEnabled` | `boolean` | `false` | Whether void invasion phase triggers |
| `GameplayConfig` | `string` | `"Portal"` | GameplayConfig asset ID for spawned worlds |

### PortalDescription Fields

| Field | Type | Description |
|-------|------|-------------|
| `DisplayName` | `string` | Translation key for portal name |
| `FlavorText` | `string` | Translation key for portal description |
| `ThemeColor` | `string` (hex) | Theme color used in UI |
| `DescriptionTags` | `PillTag[]` | Cosmetic UI tags |
| `Objectives` | `string[]` | Translation keys for objectives |
| `Tips` | `string[]` | Translation keys for tips/wisdom |
| `SplashImage` | `string` | Filename for splash art |

### Example: Our Realm_Default Portal Type

```json
{
  "InstanceId": "realm_basic_test",
  "Description": {
    "DisplayName": "server.rpg.realm.portal.title",
    "FlavorText": "server.rpg.realm.portal.description",
    "ThemeColor": "#AA00AAFF",
    "SplashImage": "DefaultArtwork.png",
    "Tips": []
  },
  "VoidInvasionEnabled": false,
  "GameplayConfig": "Portal"
}
```

### Accessing Portal Types in Java

```java
import com.hypixel.hytale.server.core.asset.type.portalworld.PortalType;

// Get by ID
PortalType type = PortalType.getAssetMap().getAsset("Henges");

// Get any available type (fallback)
PortalType fallback = PortalType.getAssetMap().getAssetMap().values()
    .stream().findFirst().orElse(null);

// Read properties
String instanceId = type.getInstanceId();       // e.g., "henges_small"
Message displayName = type.getDisplayName();     // Localized name
PortalDescription desc = type.getDescription();  // Full description object
GameplayConfig config = type.getGameplayConfig();
boolean hasVoid = type.isVoidInvasionEnabled();
Set<String> cursed = type.getCursedItems();
```

## Portal Item Keys

Vanilla "Fragment Key" items use `PortalKey` to specify which portal type they open and the time limit.

```java
import com.hypixel.hytale.server.core.asset.type.item.config.PortalKey;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;

// Check if an item is a portal key
Item itemAsset = Item.getAssetMap().getAsset(itemStack.getItemId());
PortalKey portalKey = itemAsset.getPortalKey();
if (portalKey != null) {
    String portalTypeId = portalKey.getPortalTypeId();
    int timeLimit = portalKey.getTimeLimitSeconds(); // -1 = use PortalType default
}
```

## Portal Interactions (Block JSON)

Portal entry/exit is driven by block interaction JSON, not by events. The Portal_Device block type has `CollisionEnter` interactions that trigger teleportation.

### Entry Portal (Portal_Device)

The vanilla Portal_Device uses `"Type": "Portal"` (mapped to `EnterPortalInteraction`):

```json
{
  "Interactions": {
    "CollisionEnter": {
      "Interactions": [
        { "Type": "Portal" }
      ]
    }
  }
}
```

`EnterPortalInteraction` flow:
1. Checks player has been in world > 3 seconds (`MINIMUM_TIME_IN_WORLD`)
2. Reads `PortalDevice` component from the block
3. Gets `destinationWorld` from the device
4. Fetches target world state asynchronously (checks `PortalWorld.exists()`, spawn point availability, died-in-world)
5. Calls `InstancesPlugin.teleportPlayerToInstance(ref, commandBuffer, targetWorld, returnTransform)`

### Return Portal

Vanilla uses `"Type": "PortalReturn"` (mapped to `ReturnPortalInteraction`):

```json
{
  "Interactions": {
    "CollisionEnter": {
      "Interactions": [
        { "Type": "PortalReturn" }
      ]
    }
  }
}
```

`ReturnPortalInteraction` flow:
1. Checks player has been in world > 15 seconds (shows "attuning" message after 4 seconds)
2. Checks `PortalWorld.exists()` -- requires initialized PortalWorld
3. Uncurses all items in inventory
4. Calls `InstancesPlugin.exitInstance(ref, commandBuffer)`

### Custom Victory Portal (Our Extension)

We register a custom `"RealmVictoryPortal"` interaction that mirrors `ReturnPortalInteraction` but removes realm HUDs before teleporting:

```json
{
  "Parent": "Portal_Return",
  "BlockType": {
    "Interactions": {
      "CollisionEnter": {
        "Interactions": [
          {
            "Type": "RealmVictoryPortal",
            "Next": {
              "Type": "Simple",
              "Effects": {
                "LocalSoundEventId": "SFX_Portal_Neutral_Teleport_Local"
              }
            }
          }
        ]
      }
    }
  }
}
```

The key difference: `RealmVictoryPortalInteraction` calls `RealmsManager.get().getHudManager().removeAllHudsForPlayerSync(playerId)` **before** `InstancesPlugin.exitInstance()`. This is necessary because the vanilla `DrainPlayerFromWorldEvent` fires too late for reliable HUD cleanup.

## Overriding the PortalDevice Page Supplier

To intercept Portal_Device interactions (e.g., for realm maps), register a custom `CustomPageSupplier` on the `"PortalDevice"` codec key:

```java
// In plugin setup()
getCodecRegistry(OpenCustomUIInteraction.PAGE_CODEC)
    .register("PortalDevice", RealmPortalDevicePageSupplier.class,
              RealmPortalDevicePageSupplier.CODEC);
```

Your supplier must implement `OpenCustomUIInteraction.CustomPageSupplier`:

```java
public class RealmPortalDevicePageSupplier
        implements OpenCustomUIInteraction.CustomPageSupplier {

    // Must include PortalDeviceConfig via codec to receive block config
    public static final BuilderCodec<RealmPortalDevicePageSupplier> CODEC =
        BuilderCodec.builder(RealmPortalDevicePageSupplier.class,
                             RealmPortalDevicePageSupplier::new)
            .appendInherited(
                new KeyedCodec<>("Config", PortalDeviceConfig.CODEC),
                (s, o) -> s.config = o,
                s -> s.config,
                (s, p) -> s.config = p.config)
            .add()
            .build();

    private PortalDeviceConfig config;

    @Override
    public CustomUIPage tryCreate(Ref<EntityStore> ref,
                                   ComponentAccessor<EntityStore> store,
                                   PlayerRef playerRef,
                                   InteractionContext context) {
        // Return null to handle interaction directly (no UI page)
        // Return a CustomUIPage to show vanilla-style UI
        // Delegate to PortalDeviceSummonPage/PortalDeviceActivePage for vanilla behavior
    }
}
```

**Important**: The `PortalDeviceConfig` from the codec comes from the Portal_Device block type's JSON configuration. If `config` is null, the block isn't properly configured.

## Automatic Portal Cleanup

### PortalInvalidDestinationSystem

ChunkStore `RefSystem` that auto-deactivates portal blocks with invalid destinations:

- **On block entity load** (`AddReason.LOAD`): If `PortalDevice.getDestinationWorld()` returns null (world was removed), the block is set to its `offState`.
- **On world removal** (`RemoveWorldEvent`): Iterates all portal devices in all other worlds and deactivates any pointing to the removed world.

This is automatic -- no plugin action needed. However, our `RealmPortalManager` also performs cleanup for faster response when a realm closes.

### PortalMarkerProvider

Automatically adds a "Portal" map marker to the world map at the first spawn point. Registered as a `WorldMapManager.MarkerProvider` on every world's `AddWorldEvent`.

## Key Imports

```java
// Plugin singleton
import com.hypixel.hytale.builtin.portals.PortalsPlugin;

// ECS components
import com.hypixel.hytale.builtin.portals.components.PortalDevice;
import com.hypixel.hytale.builtin.portals.components.PortalDeviceConfig;
import com.hypixel.hytale.builtin.portals.resources.PortalWorld;

// Configuration
import com.hypixel.hytale.builtin.portals.integrations.PortalGameplayConfig;
import com.hypixel.hytale.builtin.portals.integrations.PortalRemovalCondition;

// Portal type asset
import com.hypixel.hytale.server.core.asset.type.portalworld.PortalType;
import com.hypixel.hytale.server.core.asset.type.portalworld.PortalDescription;

// Portal item key
import com.hypixel.hytale.server.core.asset.type.item.config.PortalKey;

// Interactions
import com.hypixel.hytale.builtin.portals.interactions.EnterPortalInteraction;
import com.hypixel.hytale.builtin.portals.interactions.ReturnPortalInteraction;

// UI pages
import com.hypixel.hytale.builtin.portals.ui.PortalDevicePageSupplier;
import com.hypixel.hytale.builtin.portals.ui.PortalDeviceActivePage;
import com.hypixel.hytale.builtin.portals.ui.PortalDeviceSummonPage;

// Packets
import com.hypixel.hytale.protocol.packets.interface_.UpdatePortal;
import com.hypixel.hytale.protocol.packets.interface_.PortalDef;
import com.hypixel.hytale.protocol.packets.interface_.PortalState;

// Block utilities
import com.hypixel.hytale.builtin.portals.utils.BlockTypeUtils;

// Instance integration
import com.hypixel.hytale.builtin.instances.InstancesPlugin;
import com.hypixel.hytale.builtin.instances.config.InstanceWorldConfig;
import com.hypixel.hytale.builtin.instances.removal.InstanceDataResource;
```

## Edge Cases and Gotchas

### setDestinationWorld(null) Throws NPE

`PortalDevice.setDestinationWorld()` calls `world.getWorldConfig().getUuid()` internally. Passing `null` causes a `NullPointerException`. To "clear" a destination, leave the stale UUID -- `getDestinationWorld()` already returns `null` when the world no longer exists.

### PortalWorld.exists() Is the Gate for Everything

Both `EnterPortalInteraction` and `ReturnPortalInteraction` check `portalWorld.exists()` before proceeding. If you create an instance world and need portal interactions to work, you **must** call `portalWorld.init()` with a valid `PortalType`. Without it, players see "not in portal world" messages.

### TrackerSystem Sends Initial Packet Regardless of Timer

`PortalTrackerSystems.TrackerSystem.onEntityAdded()` sends a full `UpdatePortal` packet whenever `portalWorld.exists()` is true, regardless of whether `timeoutTimer` is set. The `UiTickingSystem` tick updates check `timeoutTimer`, but the initial packet does not. You must send `UpdatePortal(null, null)` in `PlayerReadyEvent` to hide it.

### Why PlayerReadyEvent, Not AddPlayerToWorldEvent

The initial `UpdatePortal` packet is sent by `TrackerSystem.onEntityAdded()`, which fires during the entity add sequence. If you send `UpdatePortal(null, null)` in `AddPlayerToWorldEvent`, the client's `resetManagers()` call (which happens after `AddPlayerToWorldEvent` but before `PlayerReadyEvent`) may re-process queued packets. Use `PlayerReadyEvent` to ensure the suppression packet is the last one the client sees.

### Atomic Timer Suppression Is Required

Three operations must happen in a single `world.execute()` block to prevent race conditions:

1. Clear `removalConditions` (prevents `TimeoutCondition` from auto-setting the timer)
2. Set `timeoutTimer = null` (prevents `UiTickingSystem` from sending updates)
3. Call `portalWorld.init()` (enables portal validation)

If separated across multiple `world.execute()` calls, system ticks between them can re-enable the timer. See `RealmInstanceFactory.initializePortalWorldAndDisableVanillaTimer()`.

### Minimum Time in World

- `EnterPortalInteraction`: 3 seconds (`Duration.ofMillis(3000L)`)
- `ReturnPortalInteraction` / `RealmVictoryPortalInteraction`: 15 seconds (`Duration.ofSeconds(15L)`)

These are hardcoded. Players cannot enter/exit portals before these thresholds. The return portal shows an "attuning to world" message after 4 seconds.

### Died-in-World Tracking

`PortalWorld.getDiedInWorld()` is a `ConcurrentHashMap`-backed set of player UUIDs. Players who die in a portal world are added to this set. `EnterPortalInteraction` checks this and shows the `PortalDeviceActivePage` instead of teleporting, which gives a "you already died here" UI.

### Thread Safety

- **PortalDevice operations**: Must be on the world thread (access ChunkStore)
- **PortalWorld resource access**: Must be on the world thread (access EntityStore)
- **setBlockInteractionState**: Must be on the world thread
- **UpdatePortal packet sending**: Safe from any thread (`PacketHandler.write()` is thread-safe)

## Reference

| Decompiled Source | Path |
|-------------------|------|
| `PortalsPlugin` | `com/hypixel/hytale/builtin/portals/PortalsPlugin.java` |
| `PortalDevice` | `com/hypixel/hytale/builtin/portals/components/PortalDevice.java` |
| `PortalDeviceConfig` | `com/hypixel/hytale/builtin/portals/components/PortalDeviceConfig.java` |
| `PortalWorld` | `com/hypixel/hytale/builtin/portals/resources/PortalWorld.java` |
| `PortalGameplayConfig` | `com/hypixel/hytale/builtin/portals/integrations/PortalGameplayConfig.java` |
| `PortalRemovalCondition` | `com/hypixel/hytale/builtin/portals/integrations/PortalRemovalCondition.java` |
| `PortalType` | `com/hypixel/hytale/server/core/asset/type/portalworld/PortalType.java` |
| `PortalDescription` | `com/hypixel/hytale/server/core/asset/type/portalworld/PortalDescription.java` |
| `PortalKey` | `com/hypixel/hytale/server/core/asset/type/item/config/PortalKey.java` |
| `EnterPortalInteraction` | `com/hypixel/hytale/builtin/portals/interactions/EnterPortalInteraction.java` |
| `ReturnPortalInteraction` | `com/hypixel/hytale/builtin/portals/interactions/ReturnPortalInteraction.java` |
| `PortalTrackerSystems` | `com/hypixel/hytale/builtin/portals/systems/PortalTrackerSystems.java` |
| `PortalInvalidDestinationSystem` | `com/hypixel/hytale/builtin/portals/systems/PortalInvalidDestinationSystem.java` |
| `PortalDevicePageSupplier` | `com/hypixel/hytale/builtin/portals/ui/PortalDevicePageSupplier.java` |
| `PortalMarkerProvider` | `com/hypixel/hytale/builtin/portals/integrations/PortalMarkerProvider.java` |
| `BlockTypeUtils` | `com/hypixel/hytale/builtin/portals/utils/BlockTypeUtils.java` |
| `UpdatePortal` | `com/hypixel/hytale/protocol/packets/interface_/UpdatePortal.java` |
| `PortalDef` | `com/hypixel/hytale/protocol/packets/interface_/PortalDef.java` |
| `PortalState` | `com/hypixel/hytale/protocol/packets/interface_/PortalState.java` |

| Schema | Path |
|--------|------|
| `PortalType.json` | `Assets/Schema/PortalType.json` |

| Our Plugin Files | Path |
|------------------|------|
| `RealmPortalManager` | `src/main/java/.../maps/instance/RealmPortalManager.java` |
| `RealmInstanceFactory` | `src/main/java/.../maps/instance/RealmInstanceFactory.java` |
| `RealmRemovalHandler` | `src/main/java/.../maps/instance/RealmRemovalHandler.java` |
| `RealmPlayerEnterListener` | `src/main/java/.../maps/listeners/RealmPlayerEnterListener.java` |
| `RealmPortalDevicePageSupplier` | `src/main/java/.../maps/listeners/RealmPortalDevicePageSupplier.java` |
| `RealmVictoryPortalInteraction` | `src/main/java/.../maps/completion/interactions/RealmVictoryPortalInteraction.java` |
| `RPG_Victory_Portal.json` | `src/main/resources/hytale-assets/Server/Item/Items/Portal/RPG_Victory_Portal.json` |
| `Realm_Default.json` | `src/main/resources/hytale-assets/Server/PortalTypes/Realm_Default.json` |
