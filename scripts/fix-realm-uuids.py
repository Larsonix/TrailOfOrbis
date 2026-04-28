#!/usr/bin/env python3
"""
Regenerates instance.bson files with proper 16-byte UUIDs.

The Hytale UUID codec requires exactly 16 bytes. Previous files used
template names encoded as base64, which fails for names > 16 characters.

This script regenerates all instance.bson files with proper random UUIDs.
"""

import json
import os
import base64
import uuid

INSTANCES_DIR = os.environ.get(
    "HYTALE_INSTANCES_DIR",
    os.path.join(os.environ.get("HYTALE_SERVER_DIR", "."), "mods", "TrailOfOrbis_Realms", "Server", "Instances")
)

def generate_uuid_binary():
    """Generate a random UUID as base64-encoded binary (16 bytes)."""
    random_uuid = uuid.uuid4()
    uuid_bytes = random_uuid.bytes
    return base64.b64encode(uuid_bytes).decode('ascii')

def get_world_structure_name(template_name):
    """Convert template folder name to WorldStructure name."""
    # realm_forest -> Realm_Forest
    # realm_corrupted_massive -> Realm_Corrupted_Massive
    parts = template_name.split('_')
    return '_'.join(part.capitalize() for part in parts)

def get_discovery_info(template_name):
    """Get discovery title/subtitle based on template name."""
    # Parse biome and size from template name
    parts = template_name.replace('realm_', '').split('_')

    # Handle size suffix
    sizes = {'small', 'large', 'massive'}
    size = None
    if parts[-1] in sizes:
        size = parts[-1].capitalize()
        biome = '_'.join(parts[:-1])
    else:
        biome = '_'.join(parts)

    biome_title = biome.replace('_', ' ').title()

    if size:
        title = f"{biome_title} Realm ({size})"
    else:
        title = f"{biome_title} Realm"

    subtitles = {
        'forest': "Nature's Challenge",
        'desert': "Sands of Trial",
        'mountains': "Summit's Test",
        'corrupted': "Dark Corruption",
        'caverns': "Depths Below",
        'volcano': "Flames of Fury",
        'jungle': "Wild Trials",
        'tundra': "Frozen Wasteland",
        'beach': "Coastal Chaos",
        'swamp': "Murky Depths",
        'void': "The Abyss"
    }

    subtitle = subtitles.get(biome, "Unknown Challenge")
    return title, subtitle

def create_instance_bson(template_name):
    """Create instance.bson content for a template."""
    world_structure = get_world_structure_name(template_name)
    title, subtitle = get_discovery_info(template_name)
    icon = f"{world_structure}.png"

    return {
        "Version": 4,
        "UUID": {
            "$binary": generate_uuid_binary(),
            "$type": "04"
        },
        "Seed": 12345,
        "SpawnProvider": {
            "Id": "Global",
            "SpawnPoint": {
                "X": 0.0,
                "Y": 65.0,
                "Z": 0.0,
                "Pitch": 0.0,
                "Yaw": 0.0,
                "Roll": 0.0
            }
        },
        "WorldGen": {
            "Type": "HytaleGenerator",
            "WorldStructure": world_structure
        },
        "WorldMap": {
            "Type": "Disabled"
        },
        "ChunkStorage": {
            "Type": "Hytale"
        },
        "ChunkConfig": {},
        "IsTicking": True,
        "IsBlockTicking": True,
        "IsPvpEnabled": True,
        "IsFallDamageEnabled": True,
        "IsGameTimePaused": False,
        "GameTime": "0001-01-01T12:00:00.000Z",
        "RequiredPlugins": {},
        "GameMode": "Adventure",
        "IsSpawningNPC": False,
        "IsSpawnMarkersEnabled": False,
        "IsAllNPCFrozen": False,
        "IsCompassUpdating": False,
        "IsSavingPlayers": False,
        "IsSavingChunks": False,
        "SaveNewChunks": False,
        "IsUnloadingChunks": True,
        "IsObjectiveMarkersEnabled": False,
        "DeleteOnUniverseStart": False,
        "DeleteOnRemove": True,
        "ResourceStorage": {
            "Type": "Hytale"
        },
        "Plugin": {
            "Instance": {
                "RemovalConditions": [
                    {
                        "Type": "WorldEmpty",
                        "GracePeriod": 30.0
                    },
                    {
                        "Type": "Timeout",
                        "Duration": 1800.0
                    }
                ],
                "PreventReconnection": False,
                "Discovery": {
                    "TitleKey": title,
                    "SubtitleKey": subtitle,
                    "Display": True,
                    "AlwaysDisplay": False,
                    "Icon": icon,
                    "Major": True,
                    "Duration": 3.0,
                    "FadeInDuration": 1.0,
                    "FadeOutDuration": 1.0
                }
            }
        }
    }

def main():
    """Regenerate all instance.bson files."""
    if not os.path.exists(INSTANCES_DIR):
        print(f"Error: Instances directory not found: {INSTANCES_DIR}")
        return

    # Skip special templates
    skip_templates = {'realm_basic_test', 'realm_skill_sanctum'}

    count = 0
    errors = 0

    for name in sorted(os.listdir(INSTANCES_DIR)):
        template_path = os.path.join(INSTANCES_DIR, name)
        if not os.path.isdir(template_path):
            continue

        if name in skip_templates:
            print(f"Skipping: {name}")
            continue

        bson_path = os.path.join(template_path, "instance.bson")

        try:
            content = create_instance_bson(name)

            with open(bson_path, 'w', newline='\n') as f:
                json.dump(content, f, indent=2)

            # Verify the UUID is correct length
            uuid_b64 = content["UUID"]["$binary"]
            uuid_bytes = base64.b64decode(uuid_b64)
            assert len(uuid_bytes) == 16, f"UUID length is {len(uuid_bytes)}, expected 16"

            print(f"Fixed: {name} (UUID: {len(uuid_bytes)} bytes)")
            count += 1

        except Exception as e:
            print(f"Error fixing {name}: {e}")
            errors += 1

    print(f"\nDone! Fixed {count} files, {errors} errors.")

if __name__ == "__main__":
    main()
