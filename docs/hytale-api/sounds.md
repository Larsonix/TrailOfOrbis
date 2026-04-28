# Hytale Playing Sounds Skill

Use this skill when playing sounds to players in Hytale plugins. Sounds are played using `SoundUtil` with a positional `TransformComponent` and a sound index resolved from the `SoundEvent` asset map.

---

## Quick Reference

| Concept | Description |
|---------|-------------|
| **SoundEvent** | Asset map for resolving sound IDs to numeric indexes |
| **SoundUtil** | Utility class for playing sounds to players |
| **SoundCategory** | Classifies sound type (`SFX`, `UI`, `Music`, `Ambient`) |
| **TransformComponent** | Provides the 3D position where the sound plays |
| **World.execute()** | Required thread-safe execution context for sound playback |

---

## Sound Playback Flow

1. **Resolve the sound index** from `SoundEvent.getAssetMap()`
2. **Get the player reference** (`Ref<EntityStore>`)
3. **Get the world** and entity store
4. **Execute on the world thread** via `world.execute()`
5. **Get the TransformComponent** for the sound position
6. **Play the sound** via `SoundUtil.playSoundEvent3dToPlayer()`

---

## Required Imports

```java
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.protocol.SoundEvent;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.component.Ref;
```

---

## Sound Indexes

Sounds are referenced by numeric index, resolved from the `SoundEvent` asset map using the sound's string key:

```java
int index = SoundEvent.getAssetMap().getIndex("SFX_Cactus_Large_Hit");
```

See the [full list of available sounds](https://hytalemodding.dev/en/docs/server/sounds) for valid sound keys.

---

## Sound Categories

`SoundCategory` classifies the type of sound being played:

| Category | Use Case |
|----------|----------|
| `SoundCategory.SFX` | Sound effects (combat, interactions, impacts) |
| `SoundCategory.UI` | User interface sounds (clicks, notifications) |
| `SoundCategory.Music` | Background music |
| `SoundCategory.Ambient` | Environmental/ambient audio |

---

## Getting the TransformComponent

The `TransformComponent` determines the 3D position of the sound. Two approaches:

### From the Player (Recommended)

Plays the sound at the player's current position:

```java
TransformComponent transform = store.getStore().getComponent(
    playerRef,
    EntityModule.get().getTransformComponentType()
);
```

### From a Custom Position

Plays the sound at an arbitrary world position (player must be close enough to hear):

```java
Vector3d position = new Vector3d(100, 64, 200);
Vector3f rotation = new Vector3f(0, 0, 0);
TransformComponent transform = new TransformComponent(position, rotation);
```

---

## Basic Example

Play a sound to a player at their current position:

```java
public void playSound(Player player) {
    int index = SoundEvent.getAssetMap().getIndex("SFX_Cactus_Large_Hit");
    World world = player.getWorld();
    EntityStore store = world.getEntityStore();
    Ref<EntityStore> playerRef = player.getReference();

    world.execute(() -> {
        TransformComponent transform = store.getStore().getComponent(
            playerRef,
            EntityModule.get().getTransformComponentType()
        );

        SoundUtil.playSoundEvent3dToPlayer(
            playerRef,
            index,
            SoundCategory.UI,
            transform.getPosition(),
            store.getStore()
        );
    });
}
```

---

## Common Use Cases

### Play a UI Sound on Event

```java
public void onPlayerAction(Player player) {
    int soundIndex = SoundEvent.getAssetMap().getIndex("SFX_UI_Click");
    World world = player.getWorld();
    EntityStore store = world.getEntityStore();
    Ref<EntityStore> playerRef = player.getReference();

    world.execute(() -> {
        TransformComponent transform = store.getStore().getComponent(
            playerRef,
            EntityModule.get().getTransformComponentType()
        );

        SoundUtil.playSoundEvent3dToPlayer(
            playerRef,
            soundIndex,
            SoundCategory.UI,
            transform.getPosition(),
            store.getStore()
        );
    });
}
```

### Play a Sound at a Specific Location

```java
public void playSoundAtPosition(Player player, double x, double y, double z, String soundKey) {
    int soundIndex = SoundEvent.getAssetMap().getIndex(soundKey);
    World world = player.getWorld();
    EntityStore store = world.getEntityStore();
    Ref<EntityStore> playerRef = player.getReference();

    world.execute(() -> {
        Vector3d position = new Vector3d(x, y, z);
        Vector3f rotation = new Vector3f(0, 0, 0);
        TransformComponent transform = new TransformComponent(position, rotation);

        SoundUtil.playSoundEvent3dToPlayer(
            playerRef,
            soundIndex,
            SoundCategory.SFX,
            transform.getPosition(),
            store.getStore()
        );
    });
}
```

### Play a Combat SFX

```java
public void playCombatSound(Player player, String soundKey) {
    int soundIndex = SoundEvent.getAssetMap().getIndex(soundKey);
    World world = player.getWorld();
    EntityStore store = world.getEntityStore();
    Ref<EntityStore> playerRef = player.getReference();

    world.execute(() -> {
        TransformComponent transform = store.getStore().getComponent(
            playerRef,
            EntityModule.get().getTransformComponentType()
        );

        SoundUtil.playSoundEvent3dToPlayer(
            playerRef,
            soundIndex,
            SoundCategory.SFX,
            transform.getPosition(),
            store.getStore()
        );
    });
}
```

---

## Utility Wrapper Class

Consider creating a utility wrapper for consistent sound playback:

```java
public class Sounds {

    public static void playToPlayer(Player player, String soundKey, SoundCategory category) {
        int index = SoundEvent.getAssetMap().getIndex(soundKey);
        World world = player.getWorld();
        EntityStore store = world.getEntityStore();
        Ref<EntityStore> playerRef = player.getReference();

        world.execute(() -> {
            TransformComponent transform = store.getStore().getComponent(
                playerRef,
                EntityModule.get().getTransformComponentType()
            );

            SoundUtil.playSoundEvent3dToPlayer(
                playerRef,
                index,
                category,
                transform.getPosition(),
                store.getStore()
            );
        });
    }

    public static void playAtPosition(Player player, String soundKey, SoundCategory category,
                                      double x, double y, double z) {
        int index = SoundEvent.getAssetMap().getIndex(soundKey);
        World world = player.getWorld();
        EntityStore store = world.getEntityStore();
        Ref<EntityStore> playerRef = player.getReference();

        world.execute(() -> {
            Vector3d position = new Vector3d(x, y, z);
            Vector3f rotation = new Vector3f(0, 0, 0);
            TransformComponent transform = new TransformComponent(position, rotation);

            SoundUtil.playSoundEvent3dToPlayer(
                playerRef,
                index,
                category,
                transform.getPosition(),
                store.getStore()
            );
        });
    }

    public static void playSfx(Player player, String soundKey) {
        playToPlayer(player, soundKey, SoundCategory.SFX);
    }

    public static void playUi(Player player, String soundKey) {
        playToPlayer(player, soundKey, SoundCategory.UI);
    }
}
```

---

## Best Practices

1. **Always use `world.execute()`**: Sound playback must run on the world thread for thread safety
2. **Prefer player transform**: Getting the transform from the player ensures they hear the sound; custom positions risk being out of audible range
3. **Choose the correct SoundCategory**: This affects volume mixing and player audio settings
4. **Cache sound indexes**: If playing the same sound frequently, resolve the index once and reuse it
5. **Validate sound keys**: Ensure the sound key exists in the `SoundEvent` asset map before playing
6. **Don't spam sounds**: Avoid playing many sounds in rapid succession as it can overwhelm the client audio

---

## playSoundEvent3dToPlayer Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `playerRef` | `Ref<EntityStore>` | The target player reference |
| `soundIndex` | `int` | Numeric index from `SoundEvent.getAssetMap()` |
| `category` | `SoundCategory` | Sound classification (`SFX`, `UI`, `Music`, `Ambient`) |
| `position` | `Vector3d` | 3D world position of the sound source |
| `store` | `Store<EntityStore>` | The entity store instance |

---

## SoundEvent JSON Schema

Sound events are defined as `.json` files under `Server/Audio/SoundEvents/`. The authoritative schema is at `Assets/Schema/SoundEvent.json`. Below are the key fields.

### Top-Level Fields

| Field | Type | Default | Range | Description |
|-------|------|---------|-------|-------------|
| `Parent` | string | — | — | Inherit properties from another SoundEvent (e.g., `"SFX_Attn_Moderate"` for standard attenuation presets). |
| `Volume` | number | `0.0` | -100 to 10 | Volume adjustment in decibels. Additive with layer volumes and parent volume. |
| `Pitch` | number | `0.0` | -12 to 12 | Pitch adjustment in semitones. |
| `MusicDuckingVolume` | number | `0.0` | -100 to 0 | Amount to duck music volume (dB) while this sound plays. |
| `AmbientDuckingVolume` | number | `0.0` | -100 to 0 | Amount to duck ambient sounds (dB) while this sound plays. |
| `StartAttenuationDistance` | number | `2.0` | — | Distance in blocks at which volume begins to attenuate. |
| `MaxDistance` | number | `16.0` | — | Distance in blocks at which the sound is fully silent. |
| `MaxInstance` | integer | `50` | 1–100 | Maximum concurrent instances of this sound event. |
| `PreventSoundInterruption` | boolean | `false` | — | When `true`, new instances won't cut off currently-playing instances. |
| `Layers` | array | (required) | — | Array of `SoundEventLayer` entries (min 1). Each layer plays independently, allowing layered audio. |
| `AudioCategory` | string | — | — | Audio category for mix routing (e.g., `"AudioCat_Sword"`, `"AudioCat_Ambient"`). |
| `Tags` | object | — | — | Key-value tag map for asset classification. |

### SoundEventLayer Fields

Each entry in the `Layers` array is a `SoundEventLayer`:

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `Files` | array | (required) | List of `.ogg` file paths (relative to `Server/Audio/`). One is chosen at random per play. |
| `Volume` | number | — | Volume offset for this layer in decibels. Additive with the top-level volume. |
| `StartDelay` | number | — | Delay in seconds after the event triggers before this layer starts playing. |
| `Looping` | boolean | `false` | Whether this layer loops continuously. |
| `Probability` | integer | `100` | Chance (0–100%) that this layer plays when the event triggers. |
| `ProbabilityRerollDelay` | number | — | Seconds before re-evaluating probability for a non-playing layer. |
| `RoundRobinHistorySize` | integer | `0` | Prevents the same file from repeating within this many plays. `0` = disabled. |
| `RandomSettings` | object | — | Randomization for pitch and volume (see below). |

### RandomSettings Fields

| Field | Type | Description |
|-------|------|-------------|
| `MinVolume` | number | Minimum random volume offset (dB). |
| `MaxVolume` | number | Maximum random volume offset (dB). |
| `MinPitch` | number | Minimum random pitch offset (semitones). |
| `MaxPitch` | number | Maximum random pitch offset (semitones). |
| `MaxStartOffset` | number | Maximum random start offset into the sound file (seconds). Useful for looping ambient sounds. |

### Example: Vanilla Sound Event

From `Server/Audio/SoundEvents/SFX_Sword_T2_Impact.json`:

```json
{
  "Layers": [
    {
      "Files": [
        "Sounds/Weapons/Shared/Impacts/Light_Melee_Impact_Base_03.ogg",
        "Sounds/Weapons/Shared/Impacts/Light_Melee_Impact_Base_04.ogg"
      ],
      "Volume": -2.0,
      "RandomSettings": {
        "MinVolume": -1,
        "MinPitch": -2,
        "MaxPitch": 2
      },
      "StartDelay": 0
    },
    {
      "Files": [
        "Sounds/Weapons/Sword/T2/Impacts/Sword_T2_Impact_01.ogg",
        "Sounds/Weapons/Sword/T2/Impacts/Sword_T2_Impact_02.ogg",
        "Sounds/Weapons/Sword/T2/Impacts/Sword_T2_Impact_03.ogg"
      ],
      "Volume": 4.0,
      "RandomSettings": {
        "MinVolume": -1,
        "MinPitch": -2,
        "MaxPitch": 2
      },
      "StartDelay": 0,
      "RoundRobinHistorySize": 1
    }
  ],
  "Volume": 0,
  "PreventSoundInterruption": true,
  "AudioCategory": "AudioCat_Sword",
  "Parent": "SFX_Attn_Moderate"
}
```

**Key patterns from vanilla sounds:**
- **Layered audio**: Combat sounds typically use 2+ layers (base impact + weapon-specific) playing simultaneously for richer audio.
- **Attenuation parents**: Most SFX inherit from `SFX_Attn_Moderate`, `SFX_Attn_Loud`, `SFX_Attn_Quiet`, or `SFX_Attn_VeryQuiet` which set `StartAttenuationDistance` and `MaxDistance`.
- **Randomization**: Nearly all vanilla sounds use `RandomSettings` with pitch variation (-2 to +2 semitones) and slight volume variation (-1 dB) to avoid repetitive audio.
- **Round-robin**: `RoundRobinHistorySize: 1` prevents the same variant from playing twice in a row.

For the full schema including all nested type definitions, see `Assets/Schema/SoundEvent.json` and `Assets/Schema/common.json#/definitions/SoundEventLayer`.

---

## PlaySoundEventLocalPlayer (New in 2026.03.26)

A new packet type for playing sounds only to the local player. Unlike `PlaySoundEvent2D` (which broadcasts to the player via their PacketHandler), this provides separate local and world sound event indexes with volume/pitch modifiers.

**Import:** `com.hypixel.hytale.protocol.packets.world.PlaySoundEventLocalPlayer`

**Packet ID:** 362

**Fields:**
| Field | Type | Description |
|-------|------|-------------|
| `localSoundEventIndex` | `int` | Sound index for local (2D) playback |
| `worldSoundEventIndex` | `int` | Sound index for world (3D) playback |
| `category` | `SoundCategory` | Sound category (SFX, UI, Music, Ambient) |
| `volumeModifier` | `float` | Volume multiplier (1.0 = normal) |
| `pitchModifier` | `float` | Pitch multiplier (1.0 = normal) |

**Use case:** Personal audio feedback (skill activations, level-ups, quest notifications) without broadcasting to nearby players.

---

## Related APIs

- [Particles](particles.md) — Visual effects (often paired with sounds)
- [Vanilla Asset Catalog](../reference/vanilla-asset-catalog.md) — All 1168 sound event IDs

---

## References

- [Official Documentation](https://hytalemodding.dev/en/docs/guides/plugin/playing-sounds)
- [Available Sounds List](https://hytalemodding.dev/en/docs/server/sounds)
