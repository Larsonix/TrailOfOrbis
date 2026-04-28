# Hytale JSON Asset Schemas

Complete reference for all Hytale JSON asset formats used in server mods. One example per schema type. Verified against Hytale server source and community mod implementations.

## 1. manifest.json

Every mod's root file. Defines identity, dependencies, and asset pack inclusion.

```json
{
  "Group": "com.example",
  "Name": "mymod",
  "Version": "1.0.0",
  "Description": "Example mod.",
  "Authors": [{ "Name": "Author", "Website": "" }],
  "ServerVersion": "2026.03.26-89796e57b",
  "Dependencies": {
    "com.other:OtherMod": ">=1.0.0"
  },
  "OptionalDependencies": {
    "me.partypro:partypro": "*"
  },
  "LoadBefore": {},
  "DisabledByDefault": false,
  "IncludesAssetPack": true,
  "Main": "com.example.mymod.MyModPlugin",
  "SubPlugins": []
}
```

| Field | Type | Notes |
|-------|------|-------|
| `Group` | string | Java package / author namespace |
| `Name` | string | Unique mod name |
| `Version` | string | Semver or date-based |
| `ServerVersion` | string | `"*"` for any, or specific build hash |
| `Dependencies` | object | `"Group:Name": ">=version"` |
| `IncludesAssetPack` | boolean | Whether Common/ folder has client assets |
| `Main` | string | Fully qualified plugin entry class |

## 2. Item Definitions

**Path:** `Server/Item/Items/**/*.json`

### Weapon (Sword)

```json
{
  "TranslationProperties": { "Name": "mymod.items.Weapon_Sword_Gaias_Wrath.name" },
  "Model": "Items/Weapons/Sword/Gaias_Wrath.blockymodel",
  "Texture": "Items/Weapons/Sword/Gaias_Wrath.png",
  "Icon": "Icons/ItemsGenerated/MyMod_Gaias_Wrath_Sword.png",
  "IconProperties": { "Scale": 0.42, "Rotation": [45, 90, 0], "Translation": [-34, -33.5] },
  "PlayerAnimationsId": "Sword",
  "Reticle": "DefaultMelee",
  "Quality": "Relic",
  "ItemLevel": 60,
  "MaxDurability": 230,
  "DurabilityLossOnHit": 0.21,
  "Categories": ["Items.Weapons"],
  "Tags": { "Type": ["Weapon"], "Family": ["Sword"] },
  "ItemSoundSetId": "ISS_Weapons_Blade_Large",
  "Interactions": {
    "Primary": "Root_Weapon_Sword_Primary",
    "Secondary": "Root_Weapon_Sword_Secondary_Guard",
    "Ability1": "Root_Weapon_Sword_Signature_Vortexstrike"
  },
  "Weapon": {
    "EntityStatsToClear": ["SignatureEnergy"],
    "StatModifiers": { "SignatureEnergy": [{ "Amount": 13, "CalculationType": "Additive" }] }
  },
  "InteractionVars": {
    "Swing_Left_Damage": {
      "Interactions": [{
        "Parent": "Weapon_Sword_Primary_Swing_Left_Damage",
        "DamageCalculator": { "BaseDamage": { "Physical": 14 } },
        "DamageEffects": { "WorldSoundEventId": "SFX_Sword_T2_Impact" }
      }]
    },
    "Guard_Wield": {
      "Interactions": [{
        "Parent": "Weapon_Sword_Secondary_Guard_Wield",
        "StaminaCost": { "Value": 7, "CostType": "Damage" }
      }]
    }
  },
  "Trails": [{ "TargetEntityPart": "PrimaryItem", "TargetNodeName": "Sword Base", "TrailId": "Default" }],
  "Particles": [{ "SystemId": "Block_Gem_Sparks", "TargetNodeName": "Sword Base", "Color": "#5ae554" }],
  "Light": { "Color": "#041", "Radius": 1 },
  "ItemAppearanceConditions": {
    "SignatureEnergy": [{
      "Condition": [100, 100], "ConditionValueType": "Percent",
      "Particles": [{ "SystemId": "...", "TargetEntityPart": "PrimaryItem" }],
      "ModelVFXId": "Gaias_Wrath_Signature_Status"
    }]
  },
  "Recipe": {
    "TimeSeconds": 5,
    "Input": [{ "ItemId": "Ingredient_Bar_Adamantite", "Quantity": 27 }],
    "BenchRequirement": [{ "Type": "Crafting", "Id": "Weapon_Bench", "RequiredTierLevel": 3 }]
  }
}
```

Also supports: `MaxStack`, `Utility.Compatible`, `DroppedItemAnimation`, `FirstPersonParticles`, `Scale`, `UsePlayerAnimations`.

### Bench / Block Type

```json
{
  "TranslationProperties": { "Name": "Life Forge" },
  "Categories": ["Furniture.Benches"],
  "Tags": { "Type": ["Bench"] },
  "BlockType": {
    "Material": "Solid", "DrawType": "Model", "Opacity": "Transparent",
    "CustomModel": "Items/Models/LifeForge.blockymodel",
    "CustomModelTexture": [{ "Texture": "Items/Textures/LifeForge.png" }],
    "HitboxType": "Bench_LifeForge",
    "VariantRotation": "NESW",
    "Bench": {
      "Type": "Crafting",
      "Id": "LifeForge",
      "Categories": [{ "Id": "WoodBlocks", "Icon": "Icons/.../Wood_Fir_Trunk.png", "Name": "Wood Log Crafting" }],
      "TierLevels": [{
        "UpgradeRequirement": {
          "Material": [{ "ItemId": "Ingredient_Life_Essence", "Quantity": 15 }],
          "TimeSeconds": 3
        }
      }]
    },
    "BlockEntity": { "Components": { "BenchBlock": {} } },
    "Gathering": { "Breaking": { "GatherType": "Benches" } },
    "Support": { "Down": [{ "FaceType": "Full" }] }
  }
}
```

### Common Item Fields Reference

| Field | Type | Notes |
|-------|------|-------|
| `TranslationProperties` | object | `Name` and `Description` lang keys |
| `Model` / `Texture` / `Icon` | string | Asset paths relative to Common/ |
| `Quality` | string | Common, Uncommon, Rare, Epic, Legendary, Relic, Mythic, Unique |
| `ItemLevel` | int | Level requirement / display |
| `MaxStack` | int | Stack size (weapons = 1) |
| `Categories` | string[] | Item browser categories |
| `Tags` | object | `Type` and `Family` arrays for classification |
| `PlayerAnimationsId` | string | Sword, Daggers, Battleaxe, Mace, Bow, Scythe, Block, etc. |
| `Interactions` | object | Primary, Secondary, Ability1 interaction tree roots |
| `InteractionVars` | object | Per-swing damage overrides keyed by var name |
| `Weapon` | object | StatModifiers, EntityStatsToClear, RenderDualWielded |
| `Recipe` | object | Inline recipe (or use separate Recipe file) |
| `Utility` | object | `Compatible: true` for utility slot |

### StatModifier Format

```json
{
  "StatName": [{
    "Amount": 10,
    "CalculationType": "Additive",
    "Target": "Max"
  }]
}
```

`CalculationType`: `"Additive"` or `"Multiplicative"`. `Target` is optional (e.g., `"Max"` for MaxHealth).

### Damage Types

`Physical`, `Elemental`, `Fire`, `Ice`, `Lightning`, `Void`, `Projectile`, `Fall`

## 3. Drop Tables

**Path:** `Server/Drops/**/*.json`

Recursive tree structure with node types: `Single`, `Choice`, `Multiple`, `Empty`, `Droplist`.

```json
{
  "Container": {
    "Type": "Multiple",
    "Containers": [
      {
        "Type": "Single",
        "Weight": 100.0,
        "Item": {
          "ItemId": "Ingredient_Life_Essence",
          "QuantityMin": 40,
          "QuantityMax": 45
        }
      },
      {
        "Type": "Choice",
        "RollsMin": 1,
        "RollsMax": 2,
        "Containers": [
          {
            "Type": "Single",
            "Weight": 70,
            "Item": { "ItemId": "Ingredient_Fire_Essence", "QuantityMin": 1, "QuantityMax": 3 }
          },
          { "Type": "Empty", "Weight": 30 }
        ]
      },
      {
        "Type": "Droplist",
        "DropListId": "Drops_NPC_Zombie_Common"
      }
    ]
  }
}
```

| Node Type | Behavior |
|-----------|----------|
| `Single` | Drops one item. `Weight` for parent Choice selection |
| `Choice` | Picks one child per roll. `RollsMin`/`RollsMax` |
| `Multiple` | Drops ALL children |
| `Empty` | Nothing (weighted filler in Choice) |
| `Droplist` | References another drop table by ID |

Item fields: `ItemId`, `QuantityMin`, `QuantityMax` (or `Quantity` for exact), `MinCount`, `MaxCount`.

## 4. Recipes

**Path:** `Server/Item/Recipes/**/*.json`

```json
{
  "Input": [
    { "ItemId": "MyMod_Essence", "Quantity": 1 }
  ],
  "PrimaryOutput": { "ItemId": "MyMod_Portal_Key", "Quantity": 1 },
  "Output": [
    { "ItemId": "MyMod_Portal_Key", "Quantity": 1 }
  ],
  "BenchRequirement": [{
    "Id": "MyMod_Workbench",
    "Type": "Crafting",
    "Categories": ["MyMod_Keys"],
    "RequiredTierLevel": 1
  }],
  "TimeSeconds": 3,
  "KnowledgeRequired": false
}
```

| Field | Notes |
|-------|-------|
| `Input` | Array of `{ItemId, Quantity}` |
| `Output` | Array of outputs |
| `PrimaryOutput` | Main output (shown in UI) |
| `BenchRequirement` | Bench type: `Crafting`, `Processing`, `DiagramCrafting` |
| `TimeSeconds` | Craft duration |
| `KnowledgeRequired` | Must player discover recipe first |
| `ResourceTypeId` | For processing bench resource types |

## 5. NPC Roles

**Path:** `Server/NPC/Roles/**/*.json`

### Variant (inherits from template)

```json
{
  "Type": "Variant",
  "Reference": "Template_Soulblight_Zombie",
  "Modify": {
    "MaxHealth": 30,
    "Appearance": "Zombie",
    "Weapons": ["Weapon_Sword_Steel_Rusty", "Weapon_Longsword_Crude"],
    "AttackDistance": 3,
    "DesiredAttackDistanceRange": [2, 2.5],
    "FlockArray": ["Soulblight_Zombie"],
    "Attack": "Root_NPC_Attack_Melee",
    "ViewSector": 270,
    "HearingRange": 8,
    "ApplySeparation": true
  },
  "Parameters": {
    "NameTranslationKey": { "Value": "server.npcRoles.Soulblight_Zombie.name" }
  }
}
```

### Template (Abstract)

Templates define `"Type": "Abstract"` and use `Parameters` with `{Value, Description}` objects plus `{"Compute": "ParamName"}` references. Key parameter groups:

**Detection:** `ViewRange`, `ViewSector`, `HearingRange`, `AbsoluteDetectionRange`, `AlertedRange`

**Combat:** `Attack`, `AttackSequence`, `AttackDistance`, `AttackPauseRange`, `MeleeDamage`, `BlockAbility`, `BlockProbability`, `CombatDirectWeight`, `CombatStrafeWeight`, `CombatAlwaysMovingWeight`, `CombatStrafingDurationRange`, `CombatStrafingFrequencyRange`, `CombatAttackPreDelay`, `CombatAttackPostDelay`, `CombatBackOffAfterAttack`, `CombatBackOffDistanceRange`, `CombatFleeIfTooCloseDistance`, `DesiredAttackDistanceRange`, `UseCombatActionEvaluator`

**Movement:** `ChaseRelativeSpeed`, `CombatMovingRelativeSpeed`, `CombatBackwardsRelativeSpeed`, `ClimbHeight`, `MinJumpHeight`

**Leash:** `LeashDistance`, `HardLeashDistance`, `LeashMinPlayerDistance`, `LeashTimer`, `LeashRelativeSpeed`

**Flocking:** `FlockSpawnTypes`, `FlockAllowedNPC`, `ApplySeparation`, `SeparationDistance`

**Lifecycle:** `DespawnTimer`, `MaxHealth`, `DropList`, `NameTranslationKey`

`MotionControllerList` defines movement types:
```json
"MotionControllerList": [{
  "Type": "Walk",
  "MaxWalkSpeed": 9,
  "Gravity": 10,
  "MaxFallSpeed": 15,
  "MaxRotationSpeed": 360,
  "Acceleration": 15
}]
```

## 6. NPC Models

Set via `Appearance` parameter referencing a model set. Fields: `Model` (.blockymodel path), `Texture` (.png), `EyeHeight`, `HitBox`, `Scale`, `AnimationSets` (state-to-anim), `RandomAttachmentSets` (visual variety).

## 7. NPC Spawning

**World:** In biome JSON: `"NPCs": [{ "RoleId": "Zombie", "Weight": 10 }], "DayTimeRange": [0.75, 0.25]`

**Beacon:** `MinDistance`, `MaxSpawned`, `SpawnAfterGameTime`

**Suppression:** `{ "Range": 100, "SuppressedRoles": ["*"] }`

## 8. NPC Groups / Flocks / Attitudes

In NPC role `Parameters`: `DefaultNPCAttitude` / `DefaultPlayerAttitude` (`"Ignore"`, `"Hostile"`, `"Friendly"`), `FlockSpawnTypes` / `FlockAllowedNPC` (role ID arrays), `DisableDamageGroups` (`["Self", "Zombie"]`).

## 9. Entity Effects

**Path:** `Server/Entity/Effects/**/*.json`

```json
{
  "Duration": 2,
  "Infinite": false,
  "Debuff": true,
  "OverlapBehavior": "Overwrite",
  "StatusEffectIcon": "UI/StatusEffects/Burn.png",
  "DamageCalculatorCooldown": 1,
  "DamageCalculator": {
    "BaseDamage": { "Fire": 3 }
  },
  "DamageEffects": {
    "WorldSoundEventId": "SFX_Effect_Burn_World",
    "PlayerSoundEventId": "SFX_Effect_Burn_Local"
  },
  "ApplicationEffects": {
    "EntityBottomTint": "#100600",
    "EntityTopTint": "#cf2302",
    "ScreenEffect": "ScreenEffects/Fire.png",
    "WorldSoundEventId": "SFX_Effect_Burn_World",
    "LocalSoundEventId": "SFX_Effect_Burn_Local",
    "Particles": [{ "SystemId": "Effect_Fire", "Scale": 0.6 }],
    "ModelVFXId": "Burn"
  }
}
```

| Field | Notes |
|-------|-------|
| `Duration` | Seconds (omit or `Infinite: true` for permanent) |
| `OverlapBehavior` | `"Overwrite"`, `"Stack"`, `"Extend"` |
| `DamageCalculator` | Periodic damage (tick rate = `DamageCalculatorCooldown`) |
| `StatModifiers` | Stat changes while active |
| `ApplicationEffects` | Visuals: tints, screen overlays, particles, sounds |
| `AbilityEffects` | Trigger abilities on application |
| `StatusEffectIcon` | HUD icon path |
| `Debuff` | Boolean (affects UI display) |

## 10. Interactions

**Path:** `Server/Item/Interactions/**/*.json`

Interactions form a tree of nodes. Each file is one node.

### Node Types

| Type | Purpose |
|------|---------|
| `Charging` | Hold-to-charge mechanic |
| `Chaining` | Sequential combo chain |
| `Simple` | Single action |
| `Serial` | Execute children in order |
| `Parallel` | Execute children simultaneously |
| `Selector` | Choose child based on conditions |
| `DamageEntity` | Apply damage (Stab, AOECircle, Sweep) |
| `ChangeStat` | Modify entity stat |
| `StatsCondition` | Branch on stat value |
| `ApplyEffect` | Apply entity effect |
| `Explode` | Area explosion |
| `EquipItem` | Swap equipment |
| `BlockCondition` | Check block under cursor |
| `Replace` | Variable substitution |

### Interaction Node Example (Damage Selector)

```json
{
  "Type": "Selector",
  "SelectorType": "AOECircle",
  "Range": 3.5,
  "Arc": 120,
  "MaxTargets": 5,
  "Next": {
    "Type": "DamageEntity",
    "DamageCalculator": {
      "BaseDamage": { "Physical": 38 }
    }
  }
}
```

### InteractionVar Override (from item definition)

InteractionVars in item JSON override parent interaction damage/effects. See weapon example in section 2 for `Swing_Left_Damage` format. Key sub-fields:

- `Parent`: Base interaction to inherit from
- `DamageCalculator`: `{ "BaseDamage": { "Physical": N } }`
- `EntityStatsOnHit`: `[{ "EntityStatId": "SignatureEnergy", "Amount": 3 }]`
- `StaminaCost`: `{ "Value": 7, "CostType": "Damage" }`
- `AngledDamage`: Directional damage with `Angle`, `AngleDistance`, separate `DamageCalculator`
- `DamageEffects.Knockback`: `{ "Direction": {X,Y,Z}, "Type": "Force", "Force": N, "VelocityType": "Set", "VelocityConfig": { "AirResistance", "GroundResistance", "Threshold", "Style" } }`

### Effects Block (within interactions)

```json
"Effects": {
  "Animations": [{ "Id": "Slash_Left", "Speed": 1.0 }],
  "Particles": [{ "SystemId": "Slash_Trail", "TargetEntityPart": "PrimaryItem" }],
  "WorldSoundEventId": "SFX_Swing_Light",
  "Camera": { "Shake": { "Amplitude": 0.3, "Duration": 0.2 } }
}
```

## 11. Projectile Configs

Projectile configuration is embedded in interaction nodes or item definitions:

```json
{
  "Type": "LaunchProjectile",
  "ProjectileId": "Arrow_Fire",
  "LaunchForce": 35,
  "Physics": {
    "Gravity": 9.8,
    "TerminalVelocity": 50,
    "RotationMode": "Velocity"
  },
  "Interactions": {
    "Spawn": { "Type": "Simple", "Effects": { "Particles": [...] } },
    "Hit": { "Type": "DamageEntity", "DamageCalculator": { "BaseDamage": { "Projectile": 25 } } },
    "Miss": { "Type": "Simple", "Effects": { "Particles": [...] } }
  }
}
```

## 12. Qualities

**Path:** `Server/Item/Qualities/*.json`

```json
{
  "QualityValue": 6,
  "ItemTooltipTexture": "UI/ItemQualities/Tooltips/ItemTooltipMythic.png",
  "SlotTexture": "UI/ItemQualities/Slots/SlotMythic.png",
  "TextColor": "#ff4500",
  "LocalizationKey": "server.general.qualities.Mythic",
  "VisibleQualityLabel": true,
  "RenderSpecialSlot": true,
  "ItemEntityConfig": { "ParticleSystemId": "Drop_Legendary", "ParticleColor": "#ff4500" }
}
```

Also supports: `ItemTooltipArrowTexture`, `BlockSlotTexture`, `SpecialSlotTexture` (usually same as SlotTexture).

**Quality tiers:** Common (0), Uncommon (1), Rare (2), Epic (3), Legendary (4), Relic (5), Mythic (6), Unique (7+).

## 13. Barter Shops

Barter shops are defined in NPC role JSON or via separate config. Trade structure:

```json
{
  "DisplayNameKey": "server.npcRoles.Trader.shop.name",
  "RefreshInterval": 3600,
  "TradeSlots": [
    {
      "Type": "Fixed",
      "Input": [{ "ItemId": "Coin_Gold", "Quantity": 50 }],
      "Output": { "ItemId": "Weapon_Sword_Iron", "Quantity": 1 },
      "Stock": 5
    }
  ]
}
```

## 14. Portal Types

**Path:** `Server/PortalTypes/*.json`

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
  "GameplayConfig": "Portal",
  "PlayerSpawn": { "X": 0, "Y": 64, "Z": 0 }
}
```

## 15. Biomes / Environments / Weather

**Path:** `Server/HytaleGenerator/Biomes/**/*.json`. Node-graph terrain system. Environments contain `TerrainNodes`, `VegetationNodes`, `NPCs` (role+weight), `WeatherForecasts` (`WeatherId`, `Probability`, `Duration`), `Tints` (`Sky`, `Fog`).

## 16. Sound Events

**Path:** `Server/SoundEvents/**/*.json`. Fields: `Parent` (inherit), `Layers` (array of `SoundId`/`Volume`/`PitchRange`), `MaxInstance`, `CooldownSeconds`.

## 17. Patch System

Mods can patch vanilla assets using `_BaseAssetPath` and `_op`:

```json
{
  "_BaseAssetPath": "Server/Drops/NPC/Drops_NPC_Zombie.json",
  "_op": "add",
  "Container": {
    "Type": "Single",
    "Weight": 5,
    "Item": { "ItemId": "Custom_Rare_Drop", "QuantityMin": 1, "QuantityMax": 1 }
  }
}
```

This appends to the vanilla drop table without replacing it. Used extensively by community mods.

## 18. Block Spawners

Block spawners define what entities/items appear in container blocks:

```json
{
  "Entries": [
    {
      "Name": "Dungeon_Chest_T2",
      "State": {
        "Container": {
          "DropListId": "Drops_Container_Dungeon_T2"
        }
      }
    }
  ]
}
```

## 19. Movement Configs

NPC movement via `MotionControllerList` (see section 5 for full example). Types: `Walk` (MaxWalkSpeed, Gravity, Acceleration, JumpHeight, StepHeight), `Fly` (MaxFlySpeed, FlyAcceleration), `Swim` (MaxSwimSpeed, SwimAcceleration).

Player movement modifiers use `StatModifiers` on items/effects:

```json
{ "MovementSpeed": [{ "Amount": 1.5, "CalculationType": "Multiplicative" }] }
```

## Quick Reference: File Path Conventions

| Asset Type | Path Pattern |
|-----------|-------------|
| Items | `Server/Item/Items/{Category}/{Name}.json` |
| Recipes | `Server/Item/Recipes/{Mod}/{Name}.json` |
| Interactions | `Server/Item/Interactions/{Weapon}/{Move}/{Node}.json` |
| Qualities | `Server/Item/Qualities/{Name}.json` |
| Drops | `Server/Drops/{Category}/{Name}.json` |
| NPC Roles | `Server/NPC/Roles/{Species}/{Variant}.json` |
| NPC Templates | `Server/NPC/Roles/{Species}/Templates/{Name}.json` |
| Effects | `Server/Entity/Effects/{Category}/{Name}.json` |
| Portals | `Server/PortalTypes/{Name}.json` |
| Biomes | `Server/HytaleGenerator/Biomes/{Name}/{Name}.json` |
| Languages | `Server/Languages/{locale}/items.lang` |
| Particles | `Server/Particles/{Category}/{Name}.particlespawner` |
| Instances | `Server/Instances/{name}/instance.bson` |
