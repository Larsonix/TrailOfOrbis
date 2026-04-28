# Hytale Assets for TrailOfOrbis Realms

This directory contains the Hytale server assets needed for procedurally generated realm arenas.

## Directory Structure

```
hytale-assets/
├── Common/
│   └── Items/
│       └── RealmMaps/
│           ├── Realm_Map.blockymodel     # 3D model for realm map item
│           └── Realm_Map_Texture.png     # Texture for realm map item
└── Server/
    ├── Item/
    │   └── Items/
    │       └── RPG/
    │           └── Realm_Map.json        # Custom realm map item (no Interactions)
    ├── HytaleGenerator/
    │   ├── WorldStructures/
    │   │   └── Realm_Basic.json          # WorldStructure defining realm generation
    │   └── Biomes/
    │       └── Realms/
    │           └── Realm_Arena_Basic.json  # Biome with arena shape & materials
    └── Instances/
        └── realm_basic_test/
            └── config.json               # Instance config referencing WorldStructure
```

## Deployment

Copy these assets to your Hytale server's mod asset pack directory:

```bash
# Deploy to the TrailOfOrbis_Realms asset pack (test server path)
SERVER_MODS="/mnt/c/Users/Larsonix/Documents/HytaleServeur/ServerTest/mods/TrailOfOrbis_Realms"

# Create directories
mkdir -p "$SERVER_MODS/Server/Item/Items/RPG"
mkdir -p "$SERVER_MODS/Server/HytaleGenerator/WorldStructures"
mkdir -p "$SERVER_MODS/Server/HytaleGenerator/Biomes/Realms"
mkdir -p "$SERVER_MODS/Server/Instances/realm_basic_test"
mkdir -p "$SERVER_MODS/Common/Items/RealmMaps"

# Copy all assets
cp src/main/resources/hytale-assets/Server/Item/Items/RPG/Realm_Map.json "$SERVER_MODS/Server/Item/Items/RPG/"
cp src/main/resources/hytale-assets/Server/HytaleGenerator/WorldStructures/Realm_Basic.json "$SERVER_MODS/Server/HytaleGenerator/WorldStructures/"
cp -r src/main/resources/hytale-assets/Server/HytaleGenerator/Biomes/Realms/* "$SERVER_MODS/Server/HytaleGenerator/Biomes/Realms/"
cp src/main/resources/hytale-assets/Server/Instances/realm_basic_test/config.json "$SERVER_MODS/Server/Instances/realm_basic_test/"
cp src/main/resources/hytale-assets/Common/Items/RealmMaps/* "$SERVER_MODS/Common/Items/RealmMaps/"
```

### Assets to Deploy

| Asset | Purpose |
|-------|---------|
| `Server/Item/Items/RPG/Realm_Map.json` | Custom item definition (no Interactions block) |
| `Common/Items/RealmMaps/Realm_Map.blockymodel` | 3D model for the map item |
| `Common/Items/RealmMaps/Realm_Map_Texture.png` | Texture for the map item |
| `Server/HytaleGenerator/WorldStructures/Realm_Basic.json` | World structure for realm generation |
| `Server/HytaleGenerator/Biomes/Realms/*.json` | Biome definitions for arenas |
| `Server/Instances/realm_basic_test/config.json` | Instance spawn configuration |

### Why Custom Realm_Map Item?

The custom `Realm_Map` item is used instead of the vanilla `Objective_Treasure_Map` because:
- Vanilla treasure maps have a built-in `StartObjectiveInteraction` that tries to start `TreasureMapObjective`
- This causes "Failed to find objective asset 'TreasureMapObjective'" warnings in logs
- The custom item has no `Interactions` block, so it only responds to our plugin's event handlers

## How It Works

### WorldStructure (Realm_Basic.json)
Defines the world generation parameters:
- References biome `Realm_Arena_Basic`
- Sets base height at Y=64
- No biome blending (single biome arena)

### Biome (Realm_Arena_Basic.json)
Defines the terrain shape and materials:
- **Floor**: Flat stone surface at Y=64
- **Walls**: Stone walls from Y=64 to Y=80 when |X| > 50 or |Z| > 50
- **Arena size**: 100x100 blocks (-50 to +50)
- **Material**: Rock_Stone (can be changed to Soil_Grass, etc.)

### Instance (config.json)
Configures the spawned world:
- References `Realm_Basic` WorldStructure
- Spawn point at (0, 65, 0)
- Auto-delete when empty (30s grace period)
- 30-minute timeout

## Testing

After deploying, the Java code can spawn this realm using:

```java
InstancesPlugin.get().spawnInstance("realm_basic_test", originWorld, returnPoint);
```

## Arena Dimensions

The basic arena is configured as:
- **Size**: 100x100 blocks
- **Height**: Floor at Y=64, Walls up to Y=80 (16 blocks high)
- **Boundaries**: X: -50 to +50, Z: -50 to +50

## Customization

### Change Arena Size
Edit `Realm_Arena_Basic.json` and modify the curve points:
- Change `-50` and `50` to different values for wall boundaries
- Adjust `-100` and `100` for the full range

### Change Materials
Edit the `MaterialProvider` section to use different blocks:
- `Soil_Grass` - Grass block
- `Soil_Dirt` - Dirt
- `Rock_Stone` - Stone
- See Hytale's block registry for more options

### Change Wall Height
Modify the wall height curve (currently 79-80):
- Change `79` to desired wall top height minus 1
- Change `80` to desired wall top height

## Troubleshooting

1. **Arena doesn't spawn**: Check Hytale server logs for asset loading errors
2. **No terrain**: Verify WorldStructure name matches in config.json
3. **Biome not found**: Ensure biome JSON is in correct location with matching name
