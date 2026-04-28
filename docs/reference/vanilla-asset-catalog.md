# Vanilla Asset Catalog

Auto-generated catalog of all vanilla Hytale asset IDs for particles, entity effects, sounds, and damage causes. Use this as a reference when calling particle/sound/effect APIs.

**Do not edit manually** — regenerate with `~/tools/catalog-vanilla-assets.sh`.

**Generated:** 2026-03-06 16:20:20

---

## Summary

| Asset Type | Count | ID Convention |
|-----------|-------|---------------|
| Particle Systems | **581** | Relative path from `Particles/` without `.particlesystem` |
| Entity Effects | **127** | Relative path from `Entity/Effects/` without `.json` |
| Sound Events | **1168** | Filename without `.json` (globally unique) |
| Damage Causes | **15** | Filename without `.json` |
| **Total** | **1891** | |

---

## Particle Systems (581)

Asset IDs are relative paths from `Server/Particles/` without the `.particlesystem` extension.

Usage: `ParticleUtil.spawnParticleEffect("Category/Name", position, accessor)`

### Block

```
Block/Block_Top_Glow
Block/Clay/Block_Break_Clay
Block/Clay/Block_Hit_Clay
Block/Crystal/Block_Break_Crystal
Block/Crystal/Block_Gem_Sparks
Block/Crystal/Block_Hit_Crystal
Block/Crystal/Block_Land_Hard_Crystal
Block/Crystal/Block_Land_Soft_Crystal
Block/Crystal/Block_Run_Crystal
Block/Crystal/Block_Sprint_Crystal
Block/Dirt/Block_Break_Dirt
Block/Dirt/Block_Hit_Dirt
Block/Dirt/Block_Land_Hard_Dirt
Block/Dirt/Block_Land_Soft_Dirt
Block/Dirt/Block_Run_Dirt
Block/Dirt/Block_Sprint_Dirt
Block/Dust/Block_Break_Dust
Block/Dust/Block_Build_Generic_Dust
Block/Dust/Block_Hit_Dust
Block/Dust/Block_Land_Hard_Dust
Block/Dust/Block_Land_Soft_Dust
Block/Dust/Block_Sprint_Dust
Block/Flowers/Block_Break_Soft_Flower
Block/Grass/Block_Break_Grass
Block/Grass/Block_Break_Grass_Earth
Block/Grass/Block_Land_Hard_Grass
Block/Grass/Block_Sprint_Grass
Block/Ice/Block_Break_Ice
Block/Ice/Block_Hit_Ice
Block/Ice/Block_Land_Hard_Ice
Block/Ice/Block_Land_Soft_Ice
Block/Ice/Block_Run_Ice
Block/Ice/Block_Sprint_Ice
Block/Lava/Block_Land_Lava_Hard
Block/Lava/Block_Land_Lava_Soft
Block/Lava/Block_Lava_Bubbles
Block/Lava/Block_Run_Lava
Block/Leaves/Block_Break_Branches
Block/Leaves/Block_Break_Branches_Light
Block/Leaves/Block_Break_Leaves
Block/Leaves/Block_Break_Leaves_Fir
Block/Leaves/Block_Break_Leaves_Fir_Light
Block/Leaves/Block_Break_Leaves_Light
Block/Leaves/Block_Break_Leaves_Round
Block/Leaves/Block_Break_Leaves_Round_Light
Block/Leaves/Block_Break_Leaves_Sharp
Block/Leaves/Block_Break_Leaves_Sharp_Light
Block/Leaves/Block_Break_Leaves_Snow
Block/Leaves/Block_Break_Leaves_Snow_Light
Block/Leaves/Block_Land_Hard_Branches
Block/Leaves/Block_Land_Hard_Leaves
Block/Leaves/Block_Land_Hard_Leaves_Fir
Block/Leaves/Block_Land_Hard_Leaves_Round
Block/Leaves/Block_Land_Hard_Leaves_Sharp
Block/Leaves/Block_Land_Hard_Leaves_Snow
Block/Leaves/Block_Land_Soft_Leaves
Block/Leaves/Block_Land_Soft_Leaves_Fir
Block/Leaves/Block_Land_Soft_Leaves_Round
Block/Leaves/Block_Land_Soft_Leaves_Sharp
Block/Leaves/Block_Land_Soft_Leaves_Snow
Block/Leaves/Block_Sprint_Leaves
Block/Leaves/Block_Sprint_Leaves_Fir
Block/Leaves/Block_Sprint_Leaves_Round
Block/Leaves/Block_Sprint_Leaves_Sharp
Block/Leaves/Block_Sprint_Leaves_Snow
Block/Metal/Block_Break_Metal
Block/Metal/Block_Hit_Metal
Block/Metal/Block_Land_Hard_Metal
Block/Misc/Block_Hit_Fail
Block/Mud/Block_Break_Mud
Block/Mud/Block_Hit_Mud
Block/Mud/Block_Land_Mud_Hard
Block/Mud/Block_Land_Mud_Soft
Block/Mud/Block_Run_Mud
Block/Mud/Block_Sprint_Mud
Block/Sand/Block_Break_Sand
Block/Sand/Block_Hit_Sand
Block/Sand/Block_Land_Sand_Hard
Block/Sand/Block_Land_Sand_Soft
Block/Sand/Block_Run_Sand
Block/Sand/Block_Sprint_Sand
Block/Snow/Block_Break_Snow
Block/Snow/Block_Hit_Snow
Block/Snow/Block_Land_Snow_Hard
Block/Snow/Block_Land_Snow_Soft
Block/Snow/Block_Run_Snow
Block/Snow/Block_Sprint_Snow
Block/Stone/Block_Break_Ore
Block/Stone/Block_Break_Stone
Block/Stone/Block_Hit_Stone
Block/Stone/Block_Land_Hard_Stone
Block/Stone/Block_Land_Soft_Stone
Block/Stone/Block_Sprint_Stone
Block/Stone/Stone_Bounce
Block/Stone/Stone_Death
Block/Tar/Block_Land_Tar_Soft
Block/Tar/Block_Run_Tar
Block/Water/Underwater_Effects
Block/Water/Water_Bubble_Stream
Block/Water/Water_Run
Block/Water/Water_Sprint
Block/Wood/Block_Break_Wood
Block/Wood/Block_Break_Wood_Light
Block/Wood/Block_Hit_Wood
Block/Wood/Block_Land_Hard_Wood
Block/Wood/Block_Land_Soft_Wood
Block/Wood/Block_Sprint_Wood

### Combat

```
Combat/Battleaxe/Bash/Battleaxe_Bash
Combat/Battleaxe/Bash/Impact_Battleaxe_Bash
Combat/Battleaxe/Signature/Battleaxe_Signature_Whirlwind
Combat/Crossbow/Bash/Crossbow_Bash
Combat/Crossbow/Bash/Impact_Crossbow_Bash
Combat/Daggers/Bash/Daggers_Bash
Combat/Daggers/Bash/Impact_Daggers_Bash
Combat/Daggers/Basic/Daggers_Stab_Trail
Combat/Daggers/Basic/Impact_Dagger_Slash
Combat/Daggers/Basic/Impact_Dagger_Stab
Combat/Daggers/Charged/Dagger_Charging
Combat/Daggers/Charged/Dagger_Charging_FP
Combat/Daggers/Charged/Daggers_Charged_Trail
Combat/Daggers/Charged/Daggers_Pounce_FP
Combat/Daggers/Charged/Daggers_Pounce_Flash
Combat/Daggers/Charged/Daggers_Pounce_Flash_FP
Combat/Daggers/Charged/Impact_Dagger_Stab_Charged
Combat/Daggers/Signature/Dagger_Signature_Cast
Combat/Daggers/Signature/Dagger_Signature_Cast2
Combat/Daggers/Signature/Dagger_Signature_Status
Combat/Daggers/Signature/Daggers_Lunge_Trail
Combat/Daggers/Signature/Daggers_Signature_Dash
Combat/Daggers/Signature/Daggers_Signature_Ready
Combat/Daggers/Signature/Daggers_Signature_Slash
Combat/Daggers/Signature/Daggers_Signature_Sweep
Combat/Daggers/Signature/Impact_Daggers_Signature
Combat/Daggers/Signature/Impact_Daggers_Signature_Slash
Combat/Daggers/Special/Dagger_Dash_Backward_FP
Combat/Daggers/Special/Dagger_Dash_Forward_FP
Combat/Daggers/Special/Dagger_Dash_Left_FP
Combat/Daggers/Special/Dagger_Dash_Straight_FP
Combat/Daggers/Special/Daggers_Dash_Status
Combat/Daggers/Special/Daggers_Dash_Straight
Combat/Empty
Combat/Fire_Stick/Fire_Charge1
Combat/Fire_Stick/Fire_ChargeOrange
Combat/Fire_Stick/Fire_ChargeRed
Combat/Fire_Stick/Fire_ChargeWhite
Combat/Fire_Stick/Fire_ChargeYellow
Combat/Fire_Stick/Fire_Charge_Charging1
Combat/Fire_Stick/Fire_Charge_Charging_Constant
Combat/Fire_Stick/Fire_Charge_Soak1
Combat/Fire_Stick/Fire_Charge_Soak2
Combat/Fire_Stick/Fire_Charge_Soak3
Combat/Fire_Stick/Fire_Charge_Soak4
Combat/Fire_Stick/Fire_Charged1
Combat/Fire_Stick/Fire_Charged2
Combat/Fire_Stick/Fire_Charged3
Combat/Fire_Stick/Fire_Charged4
Combat/Fire_Stick/Fire_Stick_Charge_Charging
Combat/Fire_Stick/Fire_Trap/Fire_AoE
Combat/Fire_Stick/Fire_Trap/Fire_AoE2
Combat/Fire_Stick/Fire_Trap/Fire_AoE_Grow
Combat/Fire_Stick/Fire_Trap/Fire_AoE_Grow2
Combat/Fire_Stick/Fire_Trap/Fire_AoE_Spawn
Combat/Fire_Stick/Fire_Trap/Fire_Trap_Preview
Combat/Fire_Stick/Fire_Trap/Fire_Trap_Preview_GS
Combat/Fire_Stick/Fireball_Charge_To_4
Combat/Flamethrower/Flamethrower
Combat/Impact/Critical/Impact_Critical
Combat/Impact/Misc/Feathers_Black/Impact_Feathers_Black
Combat/Impact/Misc/Fire/Impact_Fire
Combat/Impact/Misc/Ice/Impact_Ice
Combat/Impact/Misc/Impact_Poison
Combat/Impact/Misc/Void/VoidImpact
Combat/Mace/Bash/Impact_Mace_Bash
Combat/Mace/Bash/Mace_Bash
Combat/Mace/Basic/Impact_Mace_Basic
Combat/Mace/Charged/Impact_Mace_Charged
Combat/Mace/Charged/Mace_Charging
Combat/Mace/Signature/Impact_Mace_Signature
Combat/Mace/Signature/Mace_Signature_Cast
Combat/Mace/Signature/Mace_Signature_Cast_End
Combat/Mace/Signature/Mace_Signature_Ground_Hit
Combat/Mace/Signature/Mace_Signature_Slash
Combat/Poison/Placeholder_Poison_Melee_Slash
Combat/Shield/Bash/Impact_Shield_Bash
Combat/Shield/Bash/Shield_Bash
Combat/Shortbow/Bash/Impact_Shortbow_Bash
Combat/Shortbow/Bash/Shortbow_Bash
Combat/Sword/Bash/Impact_Sword_Bash
Combat/Sword/Bash/Sword_Bash
Combat/Sword/Basic/Impact_Blade_01
Combat/Sword/Basic/Impact_Sword_Basic
Combat/Sword/Basic/Impact_Sword_Basic_Stronk
Combat/Sword/Charged/Impact_Sword_Charged
Combat/Sword/Charged/Sword_Charged_Trail
Combat/Sword/Charged/Sword_Charged_Trail_Blade
Combat/Sword/Charged/Sword_Charged_Trail_Blade_Praetorian
Combat/Sword/Charged/Sword_Charged_Trail_Praetorian
Combat/Sword/Charged/Sword_Charging
Combat/Sword/Charged/Sword_Charging_Praetorian
Combat/Sword/Signature/Bow_Signature_Status
Combat/Sword/Signature/Bow_Signature_Status_FP
Combat/Sword/Signature/Crossbow_Signature_Status
Combat/Sword/Signature/Crossbow_Signature_Status_FP
Combat/Sword/Signature/Impact_Sword_Signature
Combat/Sword/Signature/Impact_Sword_Signature_Spin
Combat/Sword/Signature/Sword_Signature_AoE
Combat/Sword/Signature/Sword_Signature_AoE2
Combat/Sword/Signature/Sword_Signature_Dash_Effect
Combat/Sword/Signature/Sword_Signature_Dash_Trail
Combat/Sword/Signature/Sword_Signature_Ready
Combat/Sword/Signature/Sword_Signature_Status
Combat/Sword/Signature/Sword_Signature_Status_Spawn
Combat/Sword/Special/Shield_Block
Combat/Sword/Special/Shield_Shatter

### Deployables

```
Deployables/Healing_Totem/Totem_Heal_AoE
Deployables/Healing_Totem/Totem_Heal_AttachOnStatue
Deployables/Healing_Totem/Totem_Heal_Extra
Deployables/Healing_Totem/Totem_Heal_Simple_Test
Deployables/Healing_Totem/Totem_Heal_TopIcon
Deployables/Slowness_Totem/Totem_Slow_AoE
Deployables/Slowness_Totem/Totem_Slow_AttachOnStatue
Deployables/Slowness_Totem/Totem_Slow_Extra
Deployables/Slowness_Totem/Totem_Slow_Simple_Test

### Drop

```
Drop/Common/Drop_Common
Drop/Epic/Drop_Epic
Drop/Item/Item
Drop/Legendary/Drop_Legendary
Drop/Rare/Drop_Rare
Drop/Uncommon/Drop_Uncommon

### Root

```
Dust_Sparkles_Fine

### Explosion

```
Explosion/Explosion_Big/Explosion_Big
Explosion/Explosion_Fail/Explosion_Fail
Explosion/Explosion_Fail/Gun_Impact
Explosion/Explosion_Medium/Explosion_Medium
Explosion/Explosion_Small/Explosion_Small
Explosion/Explosion_Small/Impact_Explosion

### Item

```
Item/Candle/Candle_Fire
Item/Cauldron/Corrupted_Bubbles
Item/Fire_Green/Fire_Green
Item/Fire_Teal/Fire_Teal
Item/Fireplace/Camfire_New1
Item/Fireplace/Campfire_New2
Item/Fireplace/Campfire_New_Cartoon
Item/Fireplace/Campfire_New_GS
Item/Fireplace/Fluid_Fire
Item/Fireplace_Blue/Fire_Blue
Item/Food/Food_Eat
Item/Furnace/Fire_Furnace_On
Item/Fuse_Bomb/Fuse_Bomb
Item/Lantern/Earth_Brazier_Glow
Item/Lantern/Kweebec_Lantern
Item/Lantern/Temple_Light_Lantern
Item/Plants/Jungle_Flower_Sparks
Item/Plants/Plant_Eternal
Item/Plants/Plant_Health_Tier1
Item/Plants/Plant_Health_Tier2
Item/Plants/Plant_Health_Tier3
Item/Plants/Plant_Mana_Tier1
Item/Plants/Plant_Mana_Tier2
Item/Plants/Plant_Mana_Tier3
Item/Plants/Plant_Stamina_Tier1
Item/Plants/Plant_Stamina_Tier2
Item/Plants/Plant_Stamina_Tier3
Item/Plants/Seeds_Eternal
Item/Potion/Item_Break_Glass
Item/Potion/Item_Break_GlassMagic
Item/Potion/Item_Break_GlassPoison
Item/Potion/Item_Break_GlassPotion
Item/Potion/Item_Break_GlassPotionSmall
Item/Potion/Item_Break_Glass_Small
Item/Seeds/Seed_Place_Dust
Item/Smoke/Smoke_Black
Item/Torch/Torch_Fire
Item/Torch/Torch_Fire_Green
Item/WateringCan/Water_Can_Splash
Item/WateringCan/Watering_Can
Item/Willow_Fruit/Willow_Fruit

### Memories

```
Memories/ForgottenTemple_Leaves
Memories/ForgottenTemple_Leaves_Small
Memories/MemoryRecordedStatue
Memories/MemoryUnlock
Memories/Memory_Catch_Rune
Memories/Memory_Projectile_Sparks

### NPC

```
NPC/Bat_Ice
NPC/Bee_Swarm/Bee_Swarm_Friendly
NPC/Bee_Swarm/Bee_Swarm_Hostile
NPC/Emberwulf/Fire_Crest
NPC/Emotions/Alerted
NPC/Emotions/Angry
NPC/Emotions/Hearts
NPC/Emotions/Hearts_Subtle
NPC/Emotions/Hungry
NPC/Emotions/Question
NPC/Emotions/Question_Subtle
NPC/Emotions/Sleepy
NPC/Emotions/Stunned
NPC/Emotions/Want_Food_Apple
NPC/Emotions/Want_Food_Aubergine
NPC/Emotions/Want_Food_Carrot
NPC/Emotions/Want_Food_Cauliflower
NPC/Emotions/Want_Food_Chilli
NPC/Emotions/Want_Food_Corn
NPC/Emotions/Want_Food_Cotton
NPC/Emotions/Want_Food_Lettuce
NPC/Emotions/Want_Food_Onion
NPC/Emotions/Want_Food_Potato
NPC/Emotions/Want_Food_Pumpkin
NPC/Emotions/Want_Food_Tomato
NPC/Emotions/Want_Food_Turnip
NPC/Eye_Void/Eye_Void_Eye
NPC/Eye_Void/Eye_Void_Eye_Big
NPC/Eye_Void/Eye_Void_Smoke_Green
NPC/Eye_Void/Eye_Void_Smoke_Teal
NPC/Flies/Flies_Poop
NPC/Flies/Flies_Scattered
NPC/Goblin/Goblin_Torch_Fire
NPC/Golem/Golem_Fire_Glow
NPC/Golem/Golem_Fire_Glow_Arm
NPC/Golem/Golem_Lava_Jaw
NPC/Hedera/Hedera_Scream
NPC/Klops/Pipe_Bubbles
NPC/Sheep/Sheep_Wool_Shear
NPC/Skeleton_Burnt_Praetorian/Praetorian_Summon_Energy
NPC/Skeleton_Burnt_Praetorian/Praetorian_Summon_Ground
NPC/Skeleton_Burnt_Praetorian/Praetorian_Summon_Spawn
NPC/Spectre_Void/Spectre_Void_Arms
NPC/Spectre_Void/Spectre_Void_Body
NPC/Spectre_Void/Spectre_Void_Hands
NPC/Spectre_Void/Spectre_Void_Head
NPC/Spectre_Void/Spectre_Void_Tail
NPC/Spirit_Fire/Spirit_Fire_Jaw
NPC/Spirit_Wind/Wind_Spirit_Hand
NPC/Spirit_Wind/Wind_Spirit_Tail
NPC/Spirit_Wind/Wind_Spirit_Tentacle
NPC/Undead_Digging/Undead_Digging
NPC/Void_Dragon/Void_Dragon_Effects

### Projectile

```
Projectile/Acid/Status_Poisoned
Projectile/Ice_Boulder/IceBoulderTrail
Projectile/Iceball/IceBall
Projectile/Iceball/IceBall_Explosion
Projectile/Shot/Shot

### Spell

```
Spell/Azure_Spiral/Azure_Spiral
Spell/Beam/Beam_Lightning2
Spell/BlueBeam/Blue_Beam
Spell/E_Sphere_Old/E_Sphere_Old
Spell/Fireworks/Firework_GS
Spell/Fireworks/Firework_Mix2
Spell/Fireworks/Firework_Mix3
Spell/Fireworks/Firework_Mix4
Spell/Flying_Orb
Spell/GreenOrb/GreenOrb
Spell/GreenOrb/GreenOrbImpact
Spell/GreenOrb/GreenOrbTrail
Spell/Ice_Blast/Ice_Blast
Spell/Lightning/Lightning
Spell/MagicBlast/Magic_Hit
Spell/Portal/MagicPortal
Spell/Portal/MagicPortal_Default
Spell/Portal/MagicPortal_Fire
Spell/Portal/MagicPortal_Flat
Spell/Portal/MagicPortal_ForgottenTemple
Spell/Portal/MagicPortal_GS
Spell/Portal/MagicPortal_Stones
Spell/Portal/MagicPortal_Taiga
Spell/Portal/MagicPortal_VoidKeyArt
Spell/Portal/PlayerSpawn_Portal
Spell/Portal/PlayerSpawn_Spawn
Spell/Portal/Portal_Going_Through_Blue
Spell/Portal/Portal_Purple
Spell/Portal/Portal_Round_Blue
Spell/Portal/Portal_Round_Blue2
Spell/Portal/Portal_Round_Zone2Dungeon
Spell/Portal/Portal_Square_Blue
Spell/Portal/Portal_Square_Blue_Fit
Spell/Portal/Portal_Zone2Dungeon_Billboard
Spell/Rings/Rings_Rings
Spell/Rings/Rings_Rings_Ice
Spell/Teleport/Teleport
Spell/Teleport/Teleport_Infinite

### Status_Effect

```
Status_Effect/Crown_Gold/Effect_Crown_Gold
Status_Effect/Death/Effect_Death
Status_Effect/Fire/Effect_Fire
Status_Effect/Heal/Aura_Heal
Status_Effect/Heal/Aura_Sphere
Status_Effect/Heal/Effect_Heal
Status_Effect/Poison/Effect_Poison
Status_Effect/Potion_Health/Potion_Health_Heal
Status_Effect/Potion_Health/Potion_Health_Implosion
Status_Effect/Potion_Morph/Potion_Morph_Burst
Status_Effect/Potion_Signature/Potion_Signature_Burst
Status_Effect/Potion_Stamina/Potion_Stamina_Burst
Status_Effect/Shield/E_Sphere
Status_Effect/Snow/Effect_Snow
Status_Effect/Snow/Effect_Snow_Impact

### Weapon

```
Weapon/Bow/Bow_Charging
Weapon/Bow/Bow_Signature_Charge
Weapon/Bow/Bow_Signature_Launch
Weapon/Bow/Bow_Signature_Projectile_Sparks
Weapon/Fire_Staff/Fire_Staff_Activation
Weapon/LaserRifle/Laser_Impact
Weapon/Lightning_Sword/Lightning_Sword
Weapon/Longsword/Longsword
Weapon/Musket/Musket
Weapon/Musket/Shotgun
Weapon/Rifle/RifleShooting
Weapon/Rifle/RifleShooting_Impact
Weapon/Spear/Spear
Weapon/Staff/Ice_Staff
Weapon/Staff/Ice_Staff_Charging
Weapon/Staff/Staff_Bronze
Weapon/Staff/Staff_Wood_Skeleton
Weapon/Sword/FireSword
Weapon/Sword/Sword_Charge
Weapon/Sword/Sword_Charge2
Weapon/Sword/Sword_Charge3
Weapon/Sword/Weapon_Frost_Mist
Weapon/Wand/Wood_Wand

### Weather

```
Weather/Ash/Ash
Weather/Ash/Ash_Storm
Weather/Azure/Azurewood_Weather
Weather/Butterfly/Butterflies_GS
Weather/Dust_Sparkles/Dust_Cave_Flies
Weather/Dust_Sparkles/Dust_Sparkles
Weather/Dust_Sparkles/Dust_Sparkles_Light
Weather/Firefly/Fireflies_GS
Weather/Firelands/Burning_Wasteland
Weather/Firelands/Embers
Weather/Fog/Fog
Weather/ForgottenTemple_CenterWind
Weather/ForgottenTemple_Circle
Weather/ForgottenTemple_Island_Spark
Weather/ForgottenTemple_RoofSparks
Weather/ForgottenTemple_Spark_Spiral
Weather/Geyzer/Geyzer
Weather/Geyzer/Geyzer_Timed
Weather/Instances/Forgotten_Temple/Magic_Sparks_Temple_GS
Weather/Leaves/Leaves_Autumn_Forest_Wind
Weather/Leaves/Leaves_Oak_Wind
Weather/Magic_Sparks/Magic_Sparks_GS
Weather/Magic_Sparks/Magic_Sparks_Heavy_GS
Weather/Poison/Weather_Posion_Smoke
Weather/Rain/Rain
Weather/Rain/Rain_Heavy
Weather/Rain/Rain_Horizontal
Weather/Rain/Rain_Light
Weather/Rain/Water_Dripping
Weather/Sand/Sand_Storm
Weather/Snow/Snow_Heavy
Weather/Snow/Snow_Light
Weather/Snow/Snow_Storm
Weather/Wind

### _Example

```
_Example/Emitter_Orientation_Debug
_Example/Emitter_Orientation_Debug2
_Example/Emitter_Orientation_Debug_Gravity
_Example/Emitter_Orientation_None_Debug
_Example/Erosion_Status_Effect
_Example/Example_Fireflies
_Example/Example_Firework_ColorBase
_Example/Example_Firework_Mix
_Example/Example_Fireworks
_Example/Example_Glow_Front
_Example/Example_Glow_Square
_Example/Example_Growing_Disk
_Example/Example_Growing_Sphere
_Example/Example_Hit
_Example/Example_Projectile_Impact
_Example/Example_Projectile_Trail
_Example/Example_Shield
_Example/Example_Sparkfall
_Example/Example_Sparks_Directional
_Example/Example_Spiral
_Example/Example_Spiral_Horizontal
_Example/Example_Tornado
_Example/Example_UVMotion
_Example/Example_Vertical_Buff
_Example/Test
_Example/Test_System_Sparks

### _Test

```
_Test/Cinematic/Cinematic/CinematicCollar
_Test/Cinematic/Cinematic/Cinematic_Fire_Firework
_Test/Cinematic/Cinematic/Cinematic_Fireworks_Red_XL
_Test/Cinematic/Cinematic/Cinematic_Pink_Smoke
_Test/Cinematic/Cinematic/Cinematic_Portal_Appear
_Test/Cinematic/Cinematic/Cinematic_Portal_Appear_L
_Test/Cinematic/Cinematic/Cinematic_Portal_Appear_XXL
_Test/Cinematic/Hedera/Hedera_Item_Activate
_Test/Cinematic/Hedera/Hedera_Tree_Fireflies
_Test/Cinematic/Hedera/Portal_Ground_Hedera
_Test/Cinematic/Hedera/Portal_Hedera_Spawn2
_Test/Cinematic/Hedera/Portal_Hedera_Spawn3
_Test/Cinematic/Pizza/Sauce_Splash
_Test/Dance/Dance_Light_NoControl
_Test/Dance/Dance_Light_Player
_Test/Dance/Dance_Lights
_Test/Dance/Dance_Lights2
_Test/Dance/Dance_Lights3
_Test/Debug/Debug
_Test/Editor/Creative_Switch
_Test/Editor/EditorTool_Paint
_Test/Editor/EditorTool_Place_Ground
_Test/Editor/EditorTool_Place_Ground2
_Test/Fire/Debug_Fire_Ring
_Test/Fire/Fire_AoE
_Test/Fire/Fire_AoE2
_Test/Fire/Fire_AoE_Grow
_Test/Fire/Fire_AoE_Grow2
_Test/Fire/Fire_AoE_Spawn
_Test/Fire/Fire_Charge1
_Test/Fire/Fire_ChargeOrange
_Test/Fire/Fire_ChargeRed
_Test/Fire/Fire_ChargeWhite
_Test/Fire/Fire_ChargeYellow
_Test/Fire/Fire_Charge_Charging1
_Test/Fire/Fire_Charge_Charging_Constant
_Test/Fire/Fire_Charge_Soak1
_Test/Fire/Fire_Charge_Soak2
_Test/Fire/Fire_Charge_Soak3
_Test/Fire/Fire_Charge_Soak4
_Test/Fire/Fire_Charged1
_Test/Fire/Fire_Charged2
_Test/Fire/Fire_Charged3
_Test/Fire/Fire_Charged4
_Test/Fire/Fire_Pit
_Test/Fire/Fire_Projectile
_Test/Fire/Fire_Stick_Charge_Charging
_Test/Fire/Fire_Trap_Preview
_Test/Fire/Fire_Trap_Preview_GS
_Test/Fire/Projectile_Fire_Static
_Test/Fire/Projectile_Fire_Static_Dark
_Test/HealBeams/BeamEmiter_Heal_Green
_Test/HealBeams/BeamEmiter_Heal_Red
_Test/HealBeams/Beam_Heal_Green
_Test/HealBeams/Beam_Heal_Green2
_Test/HealBeams/Beam_Heal_Green_Old
_Test/HealBeams/Beam_Heal_Red
_Test/HealBeams/Beam_Heal_Red3
_Test/HealBeams/Beam_Heal_Red_Old
_Test/HealTotem/Totem_Heal_AoE
_Test/HealTotem/Totem_Heal_AttachOnStatue
_Test/HealTotem/Totem_Heal_TopIcon
_Test/MagicRnD/Beam/ForgottenTemple_Beam
_Test/MagicRnD/Beam/Test_Beam
_Test/MagicRnD/Buff/Test_Cast_Buff
_Test/MagicRnD/Orb/Test_Mage_Orb
_Test/NatureRnD/NatureBeam
_Test/SlowTotem/Totem_Slow_AoE
_Test/SlowTotem/Totem_Slow_AttachOnStatue
_Test/SmokesRnD/Smoke_Floor
_Test/SmokesRnD/Smoke_Fluffy_Floor
_Test/SmokesRnD/Smoke_Gold_Brazier
_Test/SmokesRnD/Smoke_Gold_Brazier_Small
_Test/SmokesRnD/Smoke_Green_Brazier
_Test/SmokesRnD/Smoke_Tall_Round
_Test/Sticks/1H_Stick_Charging
_Test/Sticks/1H_Stick_Parry_Active
_Test/Sticks/2H_Stick_Charging
_Test/Sticks/2H_Stick_Parry_Active
_Test/Sticks/Heal_Stick_ThornsUp
_Test/Sticks/HealingStick_Spin2FP
_Test/Sticks/HealthStick_Spin
_Test/Sticks/Nature_Buff
_Test/Sticks/Nature_Buff_Projectile
_Test/Sticks/Nature_Buff_Spawn
_Test/Sticks/Nature_Buff_SpawnInstant
_Test/Sticks/OneH_Stick_HeabyStab_ActivatedFP
_Test/Sticks/OneH_Stick_HeavyStab_Activated
_Test/Sticks/OneH_Stick_Parry_Active2
_Test/Sticks/OneH_Stick_Parry_Active2FP
_Test/Sticks/Spawners/Nature_Buff_Projectile
_Test/Sticks/Stick_Slam_Ground
_Test/Sticks/Stick_Slam_Ground_Large
_Test/Sticks/Stick_Slam_Ground_Medium
_Test/Sticks/Stick_Slam_Ground_Small
_Test/Sticks/TwoH_Stick_Parry_Active2
_Test/Sticks/TwoH_Stick_Parry_Active2FP
_Test/WaterRnD/Splash
_Test/WaterRnD/Water_Splash
_Test/WaterRnD/Water_Splash_Sofr_Ver4
_Test/WaterRnD/Water_Splash_Soft
_Test/WaterRnD/Water_Splash_Soft_Ver1
_Test/WaterRnD/Water_Splash_Soft_Ver2
_Test/WaterRnD/Water_Splash_Soft_Ver3
```

---

## Entity Effects (127)

Asset IDs are relative paths from `Server/Entity/Effects/` without `.json`.

Usage: `EntityEffect.getAssetMap().getAsset("Category/Name")`

| ID | Duration | Infinite | Debuff | Has Particles | Has StatModifiers | Has DamageCalc |
|----|----------|----------|--------|---------------|-------------------|----------------|
| `BlockPlacement/BlockPlaceFail` | 3600.0 | - | - | no | no | yes |
| `BlockPlacement/BlockPlaceSuccess` | 3600.0 | - | - | no | no | yes |
| `Damage/Red_Flash` | 0.2 | - | - | no | no | no |
| `Deployables/Healing_Totem_Heal` | 1.0 | - | false | no | yes | no |
| `Deployables/Slowness_Totem_Slow` | 1.0 | - | true | no | no | no |
| `Drop/Drop_Epic` | - | true | - | yes | no | no |
| `Drop/Drop_Legendary` | - | true | - | yes | no | no |
| `Drop/Drop_Rare` | - | true | - | yes | no | no |
| `Drop/Drop_Uncommon` | - | true | - | yes | no | no |
| `Food/Boost/Food_Health_Boost_Large` | 480 | - | - | no | no | no |
| `Food/Boost/Food_Health_Boost_Medium` | 240 | - | - | no | no | no |
| `Food/Boost/Food_Health_Boost_Small` | 120 | - | - | no | no | no |
| `Food/Boost/Food_Health_Boost_Tiny` | 60 | - | - | no | no | no |
| `Food/Boost/Food_Stamina_Boost_Large` | 60 | - | - | no | no | no |
| `Food/Boost/Food_Stamina_Boost_Medium` | 60 | - | - | no | no | no |
| `Food/Boost/Food_Stamina_Boost_Small` | 60 | - | - | no | no | no |
| `Food/Boost/Food_Stamina_Boost_Tiny` | 60 | - | - | no | no | no |
| `Food/Buff/Food_Instant_Heal_Bread` | 0.1 | - | - | no | yes | no |
| `Food/Buff/Food_Instant_Heal_T1` | 0.1 | - | - | no | yes | no |
| `Food/Buff/Food_Instant_Heal_T2` | 0.1 | - | - | no | yes | no |
| `Food/Buff/Food_Instant_Heal_T3` | 0.1 | - | - | no | yes | no |
| `Food/Buff/FruitVeggie_Buff_T1` | 45 | - | - | no | no | no |
| `Food/Buff/FruitVeggie_Buff_T2` | 150 | - | - | no | yes | no |
| `Food/Buff/FruitVeggie_Buff_T3` | 360 | - | - | no | yes | no |
| `Food/Buff/HealthRegen_Buff_T1` | 45 | - | - | no | yes | no |
| `Food/Buff/HealthRegen_Buff_T2` | 150 | - | - | no | yes | no |
| `Food/Buff/HealthRegen_Buff_T3` | 360 | - | - | no | yes | no |
| `Food/Buff/Meat_Buff_T1` | 45 | - | - | no | no | no |
| `Food/Buff/Meat_Buff_T2` | 150 | - | - | no | no | no |
| `Food/Buff/Meat_Buff_T3` | 360 | - | - | no | no | no |
| `Food/Buff/_Deprecated/Food_Buff_Medium_T1` | 60 | - | - | no | no | no |
| `Food/Buff/_Deprecated/Food_Buff_Medium_T2` | 120 | - | - | no | no | no |
| `Food/Buff/_Deprecated/Food_Buff_Medium_T3` | 240 | - | - | no | no | no |
| `Food/Buff/_Deprecated/Food_Buff_Small_T1` | 120 | - | - | no | yes | no |
| `Food/Buff/_Deprecated/Food_Buff_Small_T2` | 240 | - | - | no | yes | no |
| `Food/Buff/_Deprecated/Food_Buff_Small_T3` | 360 | - | - | no | yes | no |
| `Food/Buff/_Deprecated/Food_EffectCondition_Buff_Medium` | - | - | - | no | no | no |
| `Food/Buff/_Deprecated/Food_EffectCondition_Buff_Small` | - | - | - | no | no | no |
| `Food/Regen/Food_Health_Regen_Large` | 360 | - | - | no | yes | no |
| `Food/Regen/Food_Health_Regen_Medium` | 180 | - | - | no | yes | no |
| `Food/Regen/Food_Health_Regen_Small` | 120 | - | - | no | yes | no |
| `Food/Regen/Food_Health_Regen_Tiny` | 60 | - | - | no | yes | no |
| `Food/Regen/Food_Stamina_Regen_Large` | 360 | - | - | no | yes | no |
| `Food/Regen/Food_Stamina_Regen_Medium` | 240 | - | - | no | yes | no |
| `Food/Regen/Food_Stamina_Regen_Small` | 120 | - | - | no | yes | no |
| `Food/Regen/Food_Stamina_Regen_Tiny` | 60 | - | - | no | yes | no |
| `Food/Restore/Food_Health_Restore_Large` | 0.1 | - | - | no | yes | no |
| `Food/Restore/Food_Health_Restore_Medium` | 0.1 | - | - | no | yes | no |
| `Food/Restore/Food_Health_Restore_Small` | 0.1 | - | - | no | yes | no |
| `Food/Restore/Food_Health_Restore_Tiny` | 0.1 | - | - | no | yes | no |
| `Food/Restore/Food_Stamina_Restore_Large` | 0.1 | - | - | no | yes | no |
| `Food/Restore/Food_Stamina_Restore_Medium` | 0.1 | - | - | no | yes | no |
| `Food/Restore/Food_Stamina_Restore_Small` | 0.1 | - | - | no | yes | no |
| `Food/Restore/Food_Stamina_Restore_Tiny` | 0.1 | - | - | no | yes | no |
| `GameMode/Creative` | 0.2 | - | - | yes | no | no |
| `Immunity/Immunity_Environmental` | - | true | - | no | no | no |
| `Immunity/Immunity_Fire` | - | true | - | no | no | no |
| `Mana/Mana` | 2 | - | - | no | yes | no |
| `Mana/Mana_Drain` | - | - | - | no | yes | no |
| `Mana/Mana_High` | 2 | - | - | no | yes | no |
| `Mana/Mana_Low` | 2 | - | - | no | yes | no |
| `Mana/Mana_Regen` | 0.75 | - | - | no | yes | no |
| `Mana/Mana_Regen_High` | 1.5 | - | - | no | yes | no |
| `Mana/Mana_Regen_Low` | 0.25 | - | - | no | yes | no |
| `Movement/Dodge_Invulnerability` | 0.25 | - | - | no | no | no |
| `Movement/Dodge_Left` | 0.25 | - | - | no | no | no |
| `Movement/Dodge_Right` | 0.25 | - | - | no | no | no |
| `Npc/Death` | 0.0 | true | - | yes | no | no |
| `Npc/Npc_Heal_Low` | 2 | - | - | no | yes | no |
| `Npc/Return_Home_Healing` | 2 | - | - | no | yes | no |
| `Portals/Portal_Teleport` | 0.5 | - | - | yes | no | no |
| `Potion/Potion_Health_Greater_Regen` | 5.05 | - | - | yes | yes | no |
| `Potion/Potion_Health_Instant_Greater` | 0.1 | - | - | no | yes | no |
| `Potion/Potion_Health_Instant_Lesser` | 0.1 | - | - | no | yes | no |
| `Potion/Potion_Health_Lesser_Regen` | 5.05 | - | - | yes | yes | no |
| `Potion/Potion_Morph_Dog` | 60 | - | - | yes | no | no |
| `Potion/Potion_Morph_Frog` | 60 | - | - | yes | no | no |
| `Potion/Potion_Morph_Mosshorn` | 60 | false | true | yes | no | no |
| `Potion/Potion_Morph_Mouse` | 60 | - | - | yes | no | no |
| `Potion/Potion_Morph_Pigeon` | 60 | - | - | yes | no | no |
| `Potion/Potion_Signature_Greater_Regen` | 30.05 | - | - | no | yes | no |
| `Potion/Potion_Signature_Lesser_Regen` | 30.05 | - | - | no | yes | no |
| `Potion/Potion_Stamina_Cooldown` | 15 | - | true | no | no | no |
| `Potion/Potion_Stamina_Instant_Greater` | 1.5 | - | - | yes | yes | no |
| `Potion/Potion_Stamina_Instant_Lesser` | 1.5 | - | - | yes | yes | no |
| `Potion/Potion_Stamina_Regen` | 15 | - | - | no | yes | no |
| `Projectiles/Arrow/Crossbow/Crossbow_Combo_1` | 5 | - | - | no | no | no |
| `Projectiles/Arrow/Crossbow/Crossbow_Combo_2` | 5 | - | - | no | no | no |
| `Projectiles/Arrow/Two_Handed_Bow_Ability2_Slow` | 5 | - | - | no | no | no |
| `Projectiles/Bomb/Bomb_Explode_Stun` | 5 | - | - | yes | no | no |
| `Projectiles/Rubble/Rubble_Hit` | 1 | - | - | yes | no | no |
| `Projectiles/Rubble/Rubble_Miss` | 1 | - | - | yes | no | no |
| `Stamina/Stamina_Broken` | - | true | true | no | no | no |
| `Stamina/Stamina_Broken_Immune` | 0.1 | - | - | no | no | no |
| `Stamina/Stamina_Error_State` | 1.5 | - | - | no | no | no |
| `Stamina/Stamina_Regen_Delay_Action` | 10 | - | - | no | no | no |
| `Status/Antidote` | 120 | false | false | no | no | no |
| `Status/Burn` | 3 | false | true | yes | no | yes |
| `Status/Freeze` | - | - | - | yes | no | no |
| `Status/Immune` | 20 | - | - | no | no | no |
| `Status/Lava_Burn` | 5 | false | true | yes | no | yes |
| `Status/Poison` | 16 | false | true | yes | no | yes |
| `Status/Poison_T1` | 16 | - | - | no | no | yes |
| `Status/Poison_T2` | 16 | - | - | no | no | yes |
| `Status/Poison_T3` | 31 | - | - | no | no | yes |
| `Status/Root` | 10 | - | - | yes | no | no |
| `Status/Slow` | 10 | - | - | no | no | no |
| `Status/Stun` | 10 | - | - | yes | no | no |
| `Tests/Damage` | - | - | - | yes | no | yes |
| `Tests/Damage_High` | 2 | - | - | yes | no | yes |
| `Tests/Disappear` | 1 | - | - | no | no | yes |
| `Tests/Erosion_Test` | 0.5 | - | - | yes | no | no |
| `Tests/Rat_Transform` | 10 | - | - | no | no | no |
| `Tests/Stick_Stun` | 2 | - | - | yes | no | no |
| `Tests/Stoneskin` | 10.0 | - | - | yes | no | no |
| `Tests/Sword_Test` | 1.0 | false | - | no | no | yes |
| `Weapons/Battleaxe_Downstrike_Jump` | 5 | - | - | no | no | no |
| `Weapons/Battleaxe_Whirlwind` | 2 | - | - | no | no | no |
| `Weapons/Dagger_Dash` | 0.25 | - | - | yes | no | no |
| `Weapons/Dagger_Pounce` | 0.4 | - | - | yes | no | no |
| `Weapons/Dagger_Signature` | 1.0 | false | - | no | no | no |
| `Weapons/Flame_Staff_Burn` | 3 | false | true | yes | no | yes |
| `Weapons/FlamethrowerSource` | 0.5 | false | - | no | no | no |
| `Weapons/Intangible_Dark` | 0.4 | - | - | yes | no | no |
| `Weapons/Intangible_Smol` | 0.5 | - | - | yes | no | no |
| `Weapons/Mace_Signature` | 1.0 | false | - | no | no | no |
| `Weapons/Sword_Signature_SpinStab` | 2.0 | false | - | no | no | no |

---

## Sound Events (1168)

Sound event IDs are the filename without `.json` (globally unique).

Usage: `SoundUtil.playSoundEvent("SFX_Name", position, accessor)`

### BlockSounds

```
SFX_Bone_Break
SFX_Bone_Build
SFX_Bone_Hit
SFX_Bone_Land
SFX_Bone_Walk
SFX_Bramble_MoveIn
SFX_Branch_Break
SFX_Branch_Build
SFX_Branch_Hit
SFX_Branch_Land
SFX_Branch_Walk
SFX_Brazier_Break
SFX_Brazier_Build
SFX_Bush_Break
SFX_Bush_Hit
SFX_Bush_MoveIn
SFX_Cactus_Break
SFX_Cactus_Hit
SFX_Cactus_Large_Hit
SFX_Cactus_Small_Break
SFX_Cactus_Small_Hit
SFX_Campfire_Break
SFX_Campfire_Build
SFX_Campfire_Default_Loop
SFX_Candle_Default_Loop
SFX_Candle_Off
SFX_Cauldron_Bubbling
SFX_Cauldron_Bubbling_Small
SFX_Clay_Pot_Large_Break
SFX_Clay_Pot_Large_Build
SFX_Clay_Pot_Large_Hit
SFX_Clay_Pot_Large_Walk
SFX_Clay_Pot_Small_Break
SFX_Clay_Pot_Small_Build
SFX_Clay_Pot_Small_Hit
SFX_Clay_Pot_Small_Walk
SFX_Cloth_Break
SFX_Cloth_Build
SFX_Cloth_Hit
SFX_Cloth_Land
SFX_Cloth_Walk
SFX_Cocoon_Active
SFX_Cocoon_Break
SFX_Cocoon_Build
SFX_Cocoon_Hit
SFX_Cocoon_Walk
SFX_Coins_Land
SFX_Coins_Walk
SFX_Crops_Grow
SFX_Crops_Grow_Stage_Complete
SFX_Crystal_Break
SFX_Crystal_Build
SFX_Crystal_Hit
SFX_Crystal_Walk
SFX_Default_Break
SFX_Default_Build
SFX_Default_Clone
SFX_Default_Harvest
SFX_Default_Walk
SFX_Dirt_Break
SFX_Dirt_Build
SFX_Dirt_Clone
SFX_Dirt_Hit
SFX_Dirt_Land
SFX_Dirt_Walk
SFX_Soft_Land
SFX_Eggsac_Active
SFX_Fern_Break
SFX_Fern_MoveIn
SFX_Flame_Break
SFX_Flame_Build
SFX_Flame_Default_Loop
SFX_Gem_Break
SFX_Gem_Emit_Loop
SFX_Glass_Break
SFX_Grass_Break
SFX_Grass_Build
SFX_Grass_Hit
SFX_Grass_Land
SFX_Grass_Walk
SFX_Gravel_Break
SFX_Gravel_Build
SFX_Gravel_Hit
SFX_Gravel_Land
SFX_Gravel_Walk
SFX_Ice_Break
SFX_Ice_Build
SFX_Ice_Hit
SFX_Ice_Land
SFX_Ice_Walk
SFX_Leaves_Break
SFX_Leaves_Hit
SFX_Leaves_Walk
SFX_LeavesGround_Break
SFX_LeavesGround_Hit
SFX_LeavesGround_Land
SFX_LeavesGround_Walk
SFX_Metal_Break
SFX_Metal_Build
SFX_Metal_Hit
SFX_Metal_Land
SFX_Metal_Walk
SFX_Mud_Break
SFX_Mud_Build
SFX_Mud_Hit
SFX_Mud_Land
SFX_Mud_Walk
SFX_Mushroom_Break
SFX_Mushroom_Harvest
SFX_Ore_Break
SFX_Ore_Hit
SFX_Plant_Break
SFX_Plant_Hit
SFX_Plant_MoveIn
SFX_Plushie_Break
SFX_Plushie_Build
SFX_Poop_Break
SFX_Poop_Hit
SFX_Poop_Walk
SFX_Reeds_MoveIn
SFX_Rope_Break
SFX_Rope_Build
SFX_Rope_Land
SFX_Rope_Walk
SFX_Unbreakable_Block
SFX_Sand_Break
SFX_Sand_Build
SFX_Sand_Hit
SFX_Sand_Land
SFX_Sand_Walk
SFX_Seeds_Place
SFX_Snow_Break
SFX_Snow_Build
SFX_Snow_Hit
SFX_Snow_Land
SFX_Snow_Walk
SFX_Soft_Break
SFX_Soft_Build
SFX_Soft_Hit
SFX_Soft_Walk
SFX_Sticks_Break
SFX_Stone_Break
SFX_Stone_Build
SFX_Stone_Harvest
SFX_Stone_Hit
SFX_Stone_Land
SFX_Stone_Walk
SFX_Tall_Grass_MoveIn
SFX_Tombstone_Break
SFX_Torch_Break
SFX_Torch_Build
SFX_Torch_Default_Loop
SFX_Torch_Off
SFX_Torch_On_Loop
SFX_Trashpile_Land
SFX_Trashpile_Walk
SFX_Water_MoveIn
SFX_Water_MoveOut
SFX_Web_MoveIn
SFX_Window_Break
SFX_Window_Stone_Break
SFX_Wisp_Lamp_Loop
SFX_Wood_Break
SFX_Wood_Build
SFX_Wood_Hit
SFX_Wood_Land
SFX_Wood_Walk
```

### Environments

```
SFX_Emit_Lake_Water
SFX_Emit_Forgotten_Whispers
SFX_Emit_Temple_Wisps
SFX_Emit_Tree_Creak
SFX_Emit_Wind_Grass
SFX_Emit_Wind_Gusts
SFX_Env_Emit_Fluid_Lava
SFX_Env_Emit_Fluid_Water
SFX_Env_Emit_Fluid_Water_Far
SFX_Env_Emit_Geyzer
SFX_Forgotten_Temple_Emit_Birds
SFX_Forgotten_Temple_Emit_Birds_Interior
SFX_Global_Weather_Thunder
SFX_Z1_Emit_Forest_Autumn_Day_Birds
SFX_Z1_Emit_Forest_Autumn_Day_Insects
SFX_Z1_Emit_Forest_Autumn_Day_Wind
SFX_Z1_Emit_Forest_Azure_Day_Insects
SFX_Z1_Emit_Forest_Azure_Day_Wind
SFX_Z1_Emit_Forest_Gen_Day_Birds
SFX_Z1_Emit_Forest_Gen_Day_Insects
SFX_Z1_Emit_Forest_Gen_Day_Winds
SFX_Z1_Emit_Forest_Moss_Day_Birds
SFX_Z1_Emit_Forest_Moss_Day_Insects
SFX_Z1_Emit_Forest_Moss_Day_Wind
SFX_Z1_Emit_Forest_Night_Birds
SFX_Z1_Emit_Forest_Night_Insects
SFX_Z1_Emit_Forest_Night_Wind
SFX_Z1_Emit_Kweebec_Village_Wind
SFX_Z1_Emit_Mountain_Day_Birds
SFX_Z1_Emit_Plains_Gen_Day_Birds
SFX_Z1_Emit_Plains_Gen_Day_Insects
SFX_Z1_Emit_Plains_Gen_Day_Wind
SFX_Z1_Emit_Plains_Gen_Night_Birds
SFX_Z1_Emit_Plains_Gen_Night_Insects
SFX_Z1_Emit_Plains_Gen_Night_Wind
SFX_Z1_Emit_Shore_Day_Birds
SFX_Z1_Emit_Shore_Waves
SFX_Z1_Emit_Shore_Wind
SFX_Z1_Emit_Swamp_Day_Birds
SFX_Z1_Emit_Swamp_Day_Frogs
SFX_Z1_Emit_Swamp_Day_Insects
SFX_Z1_Emit_Swamp_Day_Wind
SFX_Z1_Emit_Swamp_Night_Frogs
SFX_Z1_Emit_Trork_Camp
SFX_Z1_Shore_Day_Birds
SFX_Z3_Emit_Cave_Ice_Crackle
SFX_Z3_Emit_Cave_Ice_Rumble
SFX_Z3_Emit_Cave_Ice_Stress
SFX_Z3_Emit_Cave_Snow_Crackle
SFX_Z3_Emit_Cave_Snow_Melt
SFX_Z3_Emit_Hedera_FX
SFX_Z3_Emit_Hedera_PlantRustle
SFX_Z3_Emit_Tree_Creak
SFX_Z3_Emit_Wind_Leaves
SFX_Z3_Emit_Wind_Leaves_Stereo
SFX_Z3_Forest_Day_Birds
SFX_Z3_Forest_Day_General
SFX_Z3_Forest_Night_Birds
```

### SFX

```
SFX_Chest_Legendary_Close_Player
SFX_Chest_Legendary_FirstOpen_Player
SFX_Chest_Legendary_Loop
SFX_Chest_Wooden_Open_Player
SFX_Arcane_Workbench_Close_Local
SFX_Arcane_Workbench_Craft
SFX_Arcane_Workbench_Open_Local
SFX_Campfire_Close_Local
SFX_Campfire_Open_Local
SFX_Generic_Crafting_Failed
SFX_Memories_Unlock_Local
SFX_Workbench_Upgrade_Complete_Default
SFX_Workbench_Upgrade_Start_Default
SFX_Creative_Play_Add_Mask
SFX_Creative_Play_Brush_Erase
SFX_Creative_Play_Brush_Mode
SFX_Creative_Play_Brush_Paint_Base
SFX_Creative_Play_Brush_Paint_Idle_Layer
SFX_Creative_Play_Brush_Paint_Move_Layer
SFX_Creative_Play_Brush_Shape
SFX_Creative_Play_Brush_Stamp
SFX_Creative_Play_Error
SFX_Creative_Play_Eyedropper_Select
SFX_Creative_Play_Paste
SFX_Creative_Play_Selection_Drag
SFX_Creative_Play_Selection_Place
SFX_Creative_Play_Selection_Scale
SFX_Creative_Play_Selection_Widget
SFX_Creative_Play_Set_Mask
SFX_Rotate_Pitch_Default
SFX_Rotate_Roll_Default
SFX_Rotate_Yaw_Default
SFX_Deployable_Totem_Heal_Despawn
SFX_Deployable_Totem_Heal_Effect_Local
SFX_Deployable_Totem_Heal_Spawn
SFX_Deployable_Totem_Slowing_Despawn
SFX_Deployable_Totem_Slowing_Effect_Local
SFX_Deployable_Totem_Slowing_Spawn
SFX_Effect_Burn_Local
SFX_Effect_Burn_World
SFX_Effect_Poison_Local
SFX_Effect_Poison_World
SFX_Stone_Coffin_Open_Close
SFX_Consume_Bread
SFX_Consume_Bread_Local
SFX_Health_Potion_High_Drink
SFX_Health_Potion_High_Drink_Local
SFX_Health_Potion_Low_Drink
SFX_Health_Potion_Low_Drink_Local
SFX_Stamina_Potion_Success
SFX_Portal_Void
SFX_Mug_Fill
SFX_Mug_Fill_Local
SFX_Avatar_Powers_Disable
SFX_Avatar_Powers_Disable_Local
SFX_Avatar_Powers_Enable
SFX_Avatar_Powers_Enable_Local
SFX_Portal_Neutral
SFX_Portal_Neutral_Open
SFX_Portal_Neutral_Teleport_Local
SFX_Divine_Respawn
SFX_Antelope_Alerted
SFX_Antelope_Death
SFX_Antelope_Hurt
SFX_Antelope_Run
SFX_Antelope_Walk
SFX_Bat_Alerted
SFX_Bat_Death
SFX_Bat_Hurt
SFX_Bear_Grizzly_Alerted
SFX_Bear_Grizzly_Attack
SFX_Bear_Grizzly_Death
SFX_Bear_Grizzly_Hurt
SFX_Bear_Grizzly_Run
SFX_Bear_Grizzly_Sleep
SFX_Bear_Walk
SFX_Bison_Alerted
SFX_Bison_Death
SFX_Bison_Hurt
SFX_Bison_Idle
SFX_Bison_Run
SFX_Bison_Walk
SFX_Camel_Alerted
SFX_Camel_Death
SFX_Camel_Hurt
SFX_Camel_Laydown
SFX_Camel_Run
SFX_Camel_Sleep
SFX_Camel_Wake
SFX_Camel_Walk
SFX_Crocodile_Alerted
SFX_Crocodile_Death
SFX_Crocodile_Hurt
SFX_Deer_Doe_Alerted
SFX_Deer_Doe_Death
SFX_Deer_Doe_Hurt
SFX_Deer_Doe_Run
SFX_Deer_Doe_Sleep
SFX_Deer_Stag_Alerted
SFX_Deer_Stag_Death
SFX_Deer_Stag_Hurt
SFX_Deer_Stag_Roar
SFX_Deer_Stag_Run
SFX_Deer_Stag_Sleep
SFX_Deer_Walk
SFX_Emberwulf_Alerted
SFX_Emberwulf_Attack_Bite
SFX_Emberwulf_Death
SFX_Emberwulf_Hurt
SFX_Emberwulf_Run
SFX_Emberwulf_Sleep
SFX_Emberwulf_Walk
SFX_Fen_Stalker_Alerted
SFX_Fen_Stalker_Attack_Swing
SFX_Fen_Stalker_Attack_Swipe
SFX_Fen_Stalker_Death
SFX_Fen_Stalker_Eat
SFX_Fen_Stalker_Eat_Finish
SFX_Fen_Stalker_Greet
SFX_Fen_Stalker_Hurt
SFX_Fen_Stalker_Run
SFX_Fen_Stalker_Scared
SFX_Fen_Stalker_Seek
SFX_Fen_Stalker_Sniff
SFX_Fox_Alerted
SFX_Fox_Death
SFX_Fox_Hurt
SFX_Fox_Run
SFX_Fox_Sleep
SFX_Frog_Alerted
SFX_Frog_Croak
SFX_Frog_Death
SFX_Frog_Hurt
SFX_Frog_Idle
SFX_Frog_Run
SFX_Gecko_Alerted
SFX_Gecko_Death
SFX_Gecko_Hurt
SFX_Golem_Earth_Alerted
SFX_Golem_Earth_Death
SFX_Golem_Earth_Hurt
SFX_Golem_Earth_Laydown
SFX_Golem_Earth_Slam
SFX_Golem_Earth_Slam_Impact
SFX_Golem_Earth_Spin
SFX_Golem_Earth_Stomp
SFX_Golem_Earth_Stomp_Impact
SFX_Golem_Earth_Swing
SFX_Golem_Earth_Swing_Impact
SFX_Golem_Earth_Wake
SFX_Golem_Firesteel_Alerted_01
SFX_Golem_Firesteel_Alerted_02
SFX_Golem_Firesteel_Death
SFX_Golem_Firesteel_Laydown
SFX_Golem_Firesteel_Wake
SFX_Golem_Frost_Alerted
SFX_Golem_Frost_Death
SFX_Golem_Frost_Hurt
SFX_Golem_Frost_Laydown
SFX_Golem_Frost_Slam
SFX_Golem_Frost_Slam_Impact
SFX_Golem_Frost_Spin
SFX_Golem_Frost_Stomp
SFX_Golem_Frost_Swing
SFX_Golem_Frost_Swing_Impact
SFX_Golem_Frost_Wake
SFX_Golem_Sand_Alerted
SFX_Golem_Sand_Death
SFX_Golem_Sand_Hurt
SFX_Golem_Sand_Laydown
SFX_Golem_Sand_Slam
SFX_Golem_Sand_Slam_Impact
SFX_Golem_Sand_Spin
SFX_Golem_Sand_Stomp
SFX_Golem_Sand_Stomp_Impact
SFX_Golem_Sand_Swing
SFX_Golem_Sand_Swing_Impact
SFX_Golem_Sand_Wake
SFX_Hyena_Alerted
SFX_Hyena_Death
SFX_Hyena_Hurt
SFX_Hyena_Idle
SFX_Larva_Alerted
SFX_Larva_Death
SFX_Larva_Despawn
SFX_Larva_Hurt
SFX_Larva_Spawn
SFX_Leopard_Snow_Alerted
SFX_Leopard_Snow_Death
SFX_Leopard_Snow_Hurt
SFX_Leopard_Snow_Run
SFX_Meerkat_Alerted
SFX_Meerkat_Death
SFX_Meerkat_Hurt
SFX_Meerkat_Idle
SFX_Moose_Bull_Alerted
SFX_Mouse_Alerted
SFX_Mouse_Death
SFX_Mouse_Flee
SFX_Mouse_Hurt
SFX_Mouse_Run
SFX_Mouse_Sleep
SFX_Raptor_Cave_Alerted
SFX_Raptor_Cave_Idle
SFX_Rat_Death
SFX_Rat_Hurt
SFX_Scarak_Fighter_Alerted
SFX_Scarak_Fighter_Death
SFX_Scarak_Fighter_Hurt
SFX_Scarak_Spitball_Fire
SFX_Scarak_Seeker_Alerted
SFX_Scarak_Seeker_Death
SFX_Scarak_Seeker_Hurt
SFX_Scorpion_Alerted
SFX_Scorpion_Death
SFX_Scorpion_Run
SFX_Scorpion_Threaten
SFX_Snake_Alerted
SFX_Snake_Death
SFX_Snake_Hurt
SFX_Snake_Idle
SFX_Spark_Living_Alerted
SFX_Spark_Living_Death
SFX_Spider_Alerted
SFX_Spider_Death
SFX_Spider_Run
SFX_Spirit_Root_Alerted
SFX_Spirit_Root_Death_01
SFX_Spirit_Root_Death_02
SFX_Spirit_Root_Hurt
SFX_Spirit_Root_Spawn
SFX_Squirrel_Alerted
SFX_Squirrel_Death
SFX_Squirrel_Hurt
SFX_Squirrel_Run
SFX_Tiger_Sabertooth_Alerted
SFX_Tiger_Sabertooth_Death
SFX_Tiger_Sabertooth_Hurt
SFX_Tiger_Sabertooth_Run
SFX_Toad_Rhino_Alerted
SFX_Toad_Rhino_Death
SFX_Toad_Rhino_Hurt
SFX_Toad_Rhino_Run
SFX_Toad_Rhino_Tongue_Impact
SFX_Toad_Rhino_Tongue_Whoosh
SFX_Toad_Rhino_Magma_Alerted
SFX_Toad_Rhino_Magma_Death
SFX_Toad_Rhino_Magma_Hurt
SFX_Toad_Rhino_Magma_Run
SFX_Toad_Rhino_Magma_Tongue_Impact
SFX_Toad_Rhino_Magma_Tongue_Whoosh
SFX_Wolf_Alerted
SFX_Wolf_Death
SFX_Wolf_Hurt
SFX_Wolf_Run
SFX_Wolf_Sleep
SFX_Yeti_Alerted
SFX_Crow_Death
SFX_Crow_Hurt
SFX_Duck_Alerted
SFX_Duck_Death
SFX_Duck_Hurt
SFX_Duck_Run
SFX_Flamingo_Alerted
SFX_Flamingo_Death
SFX_Flamingo_Fly
SFX_Flamingo_Hurt
SFX_Owl_Alerted
SFX_Owl_Death
SFX_Owl_Hurt
SFX_Pigeon_Death
SFX_Pigeon_Hurt
SFX_Raven_Alerted
SFX_Raven_Death
SFX_Raven_Flee
SFX_Raven_Hurt
SFX_Sparrow_Alerted
SFX_Sparrow_Death
SFX_Sparrow_Hurt
SFX_Sparrow_Idle
SFX_Tetrabird_Alerted
SFX_Tetrabird_Death
SFX_Tetrabird_Flee
SFX_Tetrabird_Hurt
SFX_Tetrabird_Run
SFX_Vulture_Alerted
SFX_Vulture_Death
SFX_Vulture_Flee
SFX_Vulture_Hurt
SFX_Woodpecker_Death
SFX_Woodpecker_Hurt
SFX_Feran_Death
SFX_Goblin_Alerted
SFX_Goblin_Death
SFX_Goblin_Hurt
SFX_Goblin_Run
SFX_Goblin_Search
SFX_Klops_Alerted
SFX_Klops_Death
SFX_Klops_Hurt
SFX_Klops_Idle
SFX_Klops_Run
SFX_Outlander_Hurt
SFX_Trork_Alerted
SFX_Trork_Death
SFX_Trork_Exertion
SFX_Trork_Hurt_01
SFX_Trork_Hurt_02
SFX_Trork_Run
SFX_Trork_Search
SFX_Trork_Sleep
SFX_Trork_Chieftain_Alerted
SFX_Trork_Chieftain_Death
SFX_Trork_Chieftain_Hurt
SFX_Trork_Chieftain_Run
SFX_Trork_Chieftain_Search
SFX_Boar_Alerted
SFX_Boar_Death
SFX_Boar_Hurt
SFX_Boar_Run
SFX_Boar_Sleep
SFX_Boar_Walk
SFX_Bunny_Alerted
SFX_Bunny_Death
SFX_Bunny_Hurt
SFX_Calf_Hurt
SFX_Calf_Run
SFX_Calf_Walk
SFX_Chick_Alerted
SFX_Chick_Death
SFX_Chick_Hurt
SFX_Chicken_Alerted
SFX_Chicken_Death
SFX_Chicken_Flee
SFX_Chicken_Hurt
SFX_Chicken_Run
SFX_Chicken_Walk
SFX_Cow_Alerted
SFX_Cow_Death
SFX_Cow_Hurt
SFX_Cow_Idle
SFX_Cow_Run
SFX_Cow_Sleep
SFX_Cow_Walk
SFX_Goat_Run
SFX_Goat_Walk
SFX_Horse_Alerted
SFX_Horse_Death
SFX_Horse_Hurt
SFX_Horse_Idle
SFX_Horse_Run
SFX_Horse_Sleep
SFX_Horse_Wake
SFX_Lamb_Alerted
SFX_Lamb_Death
SFX_Lamb_Hurt
SFX_Pig_Alerted
SFX_Pig_Death
SFX_Pig_Hurt
SFX_Pig_Run
SFX_Pig_Walk
SFX_Piglet_Alerted
SFX_Piglet_Death
SFX_Piglet_Hurt
SFX_Piglet_Run
SFX_Rabbit_Alerted
SFX_Rabbit_Death
SFX_Rabbit_Hurt
SFX_Rabbit_Run
SFX_Rabbit_Sleep
SFX_Ram_Alerted
SFX_Ram_Death
SFX_Ram_Hurt
SFX_Ram_Run
SFX_Ram_Sleep
SFX_Sheep_Alerted
SFX_Sheep_Death
SFX_Sheep_Hurt
SFX_Sheep_Run
SFX_Sheep_Sheared
SFX_Sheep_Walk
SFX_Warthog_Alerted
SFX_Warthog_Death
SFX_Warthog_Hurt
SFX_Warthog_Piglet_Alerted
SFX_Warthog_Piglet_Death
SFX_Warthog_Piglet_Hurt
SFX_Warthog_Piglet_Run
SFX_Warthog_Run
SFX_Warthog_Sleep
SFX_Warthog_Walk
SFX_Dragon_Sleep
SFX_Rex_Alerted
SFX_Rex_Bite
SFX_Rex_Death
SFX_Rex_Hurt
SFX_Rex_Walk
SFX_Fish_Death
SFX_Fish_Flee
SFX_Fish_Hurt
SFX_Shark_Death
SFX_Shark_Dive
SFX_Shark_Hurt
SFX_Shark_Swim
SFX_Skeleton_Praetorian_Alerted
SFX_Skeleton_Praetorian_Death_1
SFX_Skeleton_Praetorian_Death_2
SFX_Skeleton_Praetorian_Death_3
SFX_Skeleton_Praetorian_Death_4
SFX_Skeleton_Praetorian_Despawn_1
SFX_Skeleton_Praetorian_Despawn_2
SFX_Skeleton_Praetorian_Hurt
SFX_Skeleton_Praetorian_Run
SFX_Skeleton_Praetorian_Search_2
SFX_Skeleton_Praetorian_Spawn_1
SFX_Skeleton_Praetorian_Walk
SFX_Skeleton_Alerted
SFX_Skeleton_Death_1
SFX_Skeleton_Death_2
SFX_Skeleton_Death_3
SFX_Skeleton_Death_4
SFX_Skeleton_Despawn_1
SFX_Skeleton_Despawn_2
SFX_Skeleton_Hurt
SFX_Skeleton_Run
SFX_Skeleton_Search_2
SFX_Skeleton_Spawn_1
SFX_Skeleton_Spawn_2
SFX_Skeleton_Walk
SFX_Zombie_Alerted
SFX_Zombie_Attack_Bite
SFX_Zombie_Attack_Swing
SFX_Zombie_Death
SFX_Zombie_Despawn
SFX_Zombie_Hurt
SFX_Zombie_Pursuit
SFX_Zombie_ScratchBack
SFX_Zombie_Spawn
SFX_Crawler_Void_Alerted
SFX_Crawler_Void_Alerted_02
SFX_Crawler_Void_Death
SFX_Crawler_Void_Despawn
SFX_Crawler_Void_Hurt
SFX_Crawler_Void_Run
SFX_Crawler_Void_Sleep
SFX_Crawler_Void_Spawn
SFX_Crawler_Void_Spawn_02
SFX_Crawler_Void_Walk
SFX_Eye_Void_Alerted
SFX_Eye_Void_Attack_Blast
SFX_Eye_Void_Attack_Summon
SFX_Eye_Void_Death
SFX_Eye_Void_Fly_Movement
SFX_Eye_Void_Hurt
SFX_Eye_Void_Idle
SFX_Hedera_Scream
SFX_Spawn_Void_Alerted
SFX_Spawn_Void_Attack
SFX_Spawn_Void_Death
SFX_Spawn_Void_Hurt
SFX_Spawn_Void_Run
SFX_Player_Drop_Item
SFX_Player_Grab_Item
SFX_Player_Pickup_Item
SFX_Player_Death
SFX_Player_Death_Drown
SFX_Player_Death_Fall
SFX_Player_Fall
SFX_Player_Hurt
SFX_Player_Hurt_Burn
SFX_Player_Hurt_Drowning
SFX_Player_Hurt_Fall
SFX_Potion_Drink_Success
SFX_Player_Climb_Down
SFX_Player_Climb_Side
SFX_Player_Climb_Up
SFX_Player_Glide_Motion
SFX_Player_Glide_Stationary
SFX_Player_Jump
SFX_Player_Mantle
SFX_Player_Roll
SFX_Player_Slide
SFX_Player_Swim
SFX_Player_Swim_Fast
SFX_Player_Swim_Jump
SFX_Sleep_Fail
SFX_Sleep_Notification
SFX_Sleep_Notification_Loop
SFX_Sleep_Success
SFX_Arrow_FullCharge_Hit
SFX_Arrow_FullCharge_Miss
SFX_Arrow_HalfCharge_Hit
SFX_Arrow_HalfCharge_Miss
SFX_Arrow_NoCharge_Hit
SFX_Arrow_NoCharge_Miss
SFX_Arrow_Whistle
SFX_Blunderbuss_Bullet_WhizBy
SFX_Blunderbuss_Hit
SFX_Blunderbuss_Miss
SFX_Egg_Hit
SFX_Egg_Miss
SFX_GunPvP_Assault_Rifle_Bullet_Death
SFX_GunPvP_Grenade_Frag_Bounce
SFX_GunPvP_Grenade_Frag_Death
SFX_GunPvP_Grenade_Frag_Hit
SFX_GunPvP_Grenade_Frag_Miss
SFX_GunPvP_Handgun_Bullet_Death
SFX_Ice_Ball_Death
SFX_Ice_Bolt_Death
SFX_Goblin_Lobber_Bomb_Bounce
SFX_Goblin_Lobber_Bomb_Death
SFX_Goblin_Lobber_Bomb_Hit
SFX_Goblin_Lobber_Bomb_Miss
SFX_Outlander_Hunter_Arrow_Hit
SFX_Outlander_Hunter_Arrow_Miss
SFX_Scarak_Seeker_Spitball_Death
SFX_Axe_Stone_Trork_Hit
SFX_Axe_Stone_Trork_Miss
SFX_Bomb_Fire_Goblin_Bounce
SFX_Bomb_Fire_Goblin_Death
SFX_Bomb_Fire_Goblin_Hit
SFX_Bomb_Fire_Goblin_Miss
SFX_Poop_Bounce
SFX_Projectile_Poop_Hit
SFX_Rubble_Bounce
SFX_Rubble_Hit
SFX_Fireball_Bounce
SFX_Fireball_Death
SFX_Fireball_Miss
SFX_Arrow_Fire_Hit
SFX_Arrow_Fire_Miss
SFX_Arrow_Frost_Hit
SFX_Arrow_Frost_Miss
SFX_Music_Ducking_2db
SFX_Test_Blip_A
SFX_Test_Blip_B
SFX_Test_Blip_C
SFX_Capture_Crate_Capture_Fail_Local
SFX_Capture_Crate_Capture_Succeed
SFX_Capture_Crate_Capture_Succeed_Local
SFX_Capture_Crate_Spawn_Fail_Local
SFX_Capture_Crate_Spawn_Succeed
SFX_Hatchet_T1_Swing_RL_Local
SFX_Hatchet_T2_Impact_Nice
SFX_Hoe_T1_Swing_Down_Local
SFX_Hoe_T1_Till
SFX_Pickaxe_T1_Swing_Down_Local
SFX_Pickaxe_T2_Impact_Nice
SFX_Shears_Activate
SFX_Tool_Watering_Can_Water
SFX_Tool_T1_Swing
SFX_Shovel_T1_Swing_RL_Local
SFX_Shovel_T2_Impact_Nice
SFX_Discovery_Z1_Medium
SFX_Discovery_Z1_Short
SFX_Discovery_Z2_Medium
SFX_Discovery_Z2_Short
SFX_Discovery_Z3_Medium
SFX_Discovery_Z3_Short
SFX_Discovery_Z4_Medium
SFX_Discovery_Z4_Short
SFX_Alchemy_Bench_Close
SFX_Alchemy_Bench_Craft
SFX_Alchemy_Bench_Open
SFX_Armour_Bench_Close
SFX_Armour_Bench_Craft
SFX_Armour_Bench_Open
SFX_Bench_Placeholder
SFX_Campfire_Processing
SFX_Campfire_Processing_End
SFX_Campfire_Processing_Failed
SFX_Furnace_Bench_Close
SFX_Furnace_Bench_Open
SFX_Furnace_Bench_Processing
SFX_Furnace_Bench_Processing_Complete
SFX_Furnace_Bench_Processing_End
SFX_Furnace_Bench_Processing_Failed
SFX_Lumbermill_Bench_Close
SFX_Lumbermill_Bench_Open
SFX_Lumbermill_Bench_Processing
SFX_Processing_Placeholder
SFX_Weapon_Bench_Close
SFX_Weapon_Bench_Craft
SFX_Weapon_Bench_Open
SFX_Workbench_Close
SFX_Workbench_Craft
SFX_Workbench_Open
SFX_Chest_Legendary_Open
SFX_Chest_Wooden_Close
SFX_Chest_Wooden_Open
SFX_Door_Ancient_Close
SFX_Door_Ancient_Open
SFX_Door_Crude_Close
SFX_Door_Crude_Open
SFX_Door_Desert_Close
SFX_Door_Desert_Open
SFX_Door_Jungle_Close
SFX_Door_Jungle_Open
SFX_Door_Lumberjack_Close
SFX_Door_Lumberjack_Open
SFX_Door_Temple_Dark_Close
SFX_Door_Temple_Dark_Open
SFX_Door_Temple_Light_Close
SFX_Door_Temple_Light_Open
SFX_Door_Wooden_Close
SFX_Door_Wooden_Open
SFX_Player_Craft_Item_Inventory
SFX_Drag_Armor_Cloth
SFX_Drag_Armor_Heavy
SFX_Drag_Armor_Leather
SFX_Drag_Blocks_Gravel
SFX_Drag_Blocks_Soft
SFX_Drag_Blocks_Splatty
SFX_Drag_Blocks_Stone
SFX_Drag_Blocks_Wood
SFX_Drag_Item_Default
SFX_Drag_Items_Bones
SFX_Drag_Items_Chest
SFX_Drag_Items_Clay
SFX_Drag_Items_Cloth
SFX_Drag_Items_Foliage
SFX_Drag_Items_Gadget
SFX_Drag_Items_Gems
SFX_Drag_Items_Ingots
SFX_Drag_Items_Leather
SFX_Drag_Items_Metal
SFX_Drag_Items_Paper
SFX_Drag_Items_Potion
SFX_Drag_Items_Seeds
SFX_Drag_Items_Shells
SFX_Drag_Items_Splatty
SFX_Drag_Weapon_Blade_Small
SFX_Drag_Weapon_Blunt_Large
SFX_Drag_Weapons_Arrows
SFX_Drag_Weapons_Blade_Large
SFX_Drag_Weapons_Blunt_Small
SFX_Drag_Weapons_Books
SFX_Drag_Weapons_Shield_Metal
SFX_Drag_Weapons_Shield_Wood
SFX_Drag_Weapons_Stone_Large
SFX_Drag_Weapons_Stone_Small
SFX_Drag_Weapons_Wand
SFX_Drag_Weapons_Wood
SFX_Drop_Armor_Cloth
SFX_Drop_Armor_Heavy
SFX_Drop_Armor_Leather
SFX_Drop_Blocks_Gravel
SFX_Drop_Blocks_Soft
SFX_Drop_Blocks_Splatty
SFX_Drop_Blocks_Stone
SFX_Drop_Blocks_Wood
SFX_Drop_Item_Default
SFX_Drop_Items_Bones
SFX_Drop_Items_Chest
SFX_Drop_Items_Clay
SFX_Drop_Items_Cloth
SFX_Drop_Items_Foliage
SFX_Drop_Items_Gadget
SFX_Drop_Items_Gems
SFX_Drop_Items_Ingots
SFX_Drop_Items_Leather
SFX_Drop_Items_Metal
SFX_Drop_Items_Paper
SFX_Drop_Items_Potion
SFX_Drop_Items_Seeds
SFX_Drop_Items_Shells
SFX_Drop_Items_Splatty
SFX_Drop_Weapon_Blade_Small
SFX_Drop_Weapon_Blunt_Large
SFX_Drop_Weapons_Arrows
SFX_Drop_Weapons_Blade_Large
SFX_Drop_Weapons_Blunt_Small
SFX_Drop_Weapons_Books
SFX_Drop_Weapons_Shield_Metal
SFX_Drop_Weapons_Shield_Wood
SFX_Drop_Weapons_Stone_Large
SFX_Drop_Weapons_Stone_Small
SFX_Drop_Weapons_Wand
SFX_Drop_Weapons_Wood
SFX_Incorrect_Tool
SFX_Item_Break
SFX_Item_Repair
SFX_Torch_Swing_Left_Local
SFX_Torch_Swing_Right_Local
SFX_Axe_Crude_Impact
SFX_Axe_Crude_Swing
SFX_Axe_Iron_Impact
SFX_Axe_Iron_Swing
SFX_Axe_Special_Impact
SFX_Axe_Special_Swing
SFX_Battleaxe_T1_Launch
SFX_Battleaxe_T1_Launch_Local
SFX_Battleaxe_T1_Swing_Charged
SFX_Battleaxe_T1_Swing_Charged_Local
SFX_Battleaxe_T2_Swing_Charged
SFX_Battleaxe_T2_Swing_Charged_Local
SFX_Battleaxe_T1_Block_Impact
SFX_Battleaxe_T1_Impact
SFX_Battleaxe_T2_Impact
SFX_Battleaxe_T1_Raise
SFX_Battleaxe_T1_Raise_Local
SFX_Battleaxe_T1_Shove
SFX_Battleaxe_T1_Shove_Local
SFX_Battleaxe_T1_Swing
SFX_Battleaxe_T1_Swing_Down_Local
SFX_Battleaxe_T1_Swing_LR_Local
SFX_Battleaxe_T1_Swing_RL_Local
SFX_Battleaxe_T2_Raise
SFX_Battleaxe_T2_Raise_Local
SFX_Battleaxe_T2_Swing
SFX_Battleaxe_T2_Swing_Down_Local
SFX_Battleaxe_T2_Swing_LR_Local
SFX_Battleaxe_T2_Swing_RL_Local
SFX_Battleaxe_T2_Signature_End
SFX_Battleaxe_T2_Signature_End_Local
SFX_Battleaxe_T2_Signature_Swing
SFX_Battleaxe_T2_Signature_Swing_Local
SFX_Bomb_Fuse
SFX_Bow_No_Ammo
SFX_Bow_T1_Block_Impact
SFX_Bow_T1_Draw
SFX_Bow_T1_Draw_Local
SFX_Bow_T1_Raise
SFX_Bow_T1_Raise_Local
SFX_Bow_T1_Shoot
SFX_Bow_T1_Shoot_Local
SFX_Bow_T1_Swing
SFX_Bow_T1_Swing_Local
SFX_Bow_T2_Draw
SFX_Bow_T2_Draw_Local
SFX_Bow_T2_Shoot
SFX_Bow_T2_Shoot_Local
SFX_Bow_T2_Signature_Loop
SFX_Bow_T2_Signature_Loop_Local
SFX_Bow_T2_Signature_Nock
SFX_Bow_T2_Signature_Nock_Local
SFX_Bow_T2_Signature_Shoot
SFX_Bow_T2_Signature_Shoot_Local
SFX_Weapon_Charge_Swing
SFX_Club_Special_Impact
SFX_Club_Special_Swing
SFX_Club_Steel_Impact
SFX_Club_Steel_Swing
SFX_Club_Wood_Impact
SFX_Club_Wood_Swing
SFX_Daggers_T1_Slash_Impact
SFX_Daggers_T1_Stab_Double_Impact
SFX_Daggers_T1_Stab_Impact
SFX_Daggers_T2_Slash_Impact
SFX_Daggers_T2_Stab_Double_Impact
SFX_Daggers_T2_Stab_Impact
SFX_Daggers_T1_Guard
SFX_Daggers_T1_Guard_Local
SFX_Daggers_T1_Pounce
SFX_Daggers_T1_Pounce_Local
SFX_Daggers_T1_Stab_Left_Local
SFX_Daggers_T1_Stab_Retreat
SFX_Daggers_T1_Stab_Retreat_Local
SFX_Daggers_T1_Stab_Right_Local
SFX_Daggers_T1_Swing
SFX_Daggers_T1_Swing_Double
SFX_Daggers_T1_Swing_Double_Local
SFX_Daggers_T1_Swing_LR_Local
SFX_Daggers_T1_Swing_RL_Local
SFX_Daggers_T2_Guard
SFX_Daggers_T2_Guard_Local
SFX_Daggers_T2_Signature_P1
SFX_Daggers_T2_Signature_P1_Local
SFX_Daggers_T2_Signature_P2
SFX_Daggers_T2_Signature_P2_Local
SFX_Daggers_T2_Signature_P3
SFX_Daggers_T2_Signature_P3_Local
SFX_Daggers_T2_Stab_Left_Local
SFX_Daggers_T2_Stab_Retreat
SFX_Daggers_T2_Stab_Retreat_Local
SFX_Daggers_T2_Stab_Right_Local
SFX_Daggers_T2_Swing
SFX_Daggers_T2_Swing_Double
SFX_Daggers_T2_Swing_Double_Local
SFX_Daggers_T2_Swing_LR_Local
SFX_Daggers_T2_Swing_RL_Local
SFX_Flail_Charge
SFX_Flail_Charge_Local
SFX_Flail_Swing
SFX_Flail_Swing_Left_Local
SFX_Flail_Swing_Right_Local
SFX_Blunderbuss_Fire
SFX_Blunderbuss_Fire_Local
SFX_Blunderbuss_Load
SFX_Blunderbuss_Load_Local
SFX_Blunderbuss_No_Ammo
SFX_Gun_Fire
SFX_Handgun_Fire
SFX_Handgun_Fire_Local
SFX_Pistol_Fire
SFX_Rifle_Fire
SFX_Rifle_Fire_Local
SFX_Hand_Crossbow_T1_Block_Impact
SFX_Hand_Crossbow_T1_Raise
SFX_Hand_Crossbow_T1_Raise_Local
SFX_Hand_Crossbow_T1_Shove
SFX_Hand_Crossbow_T1_Shove_Local
SFX_Hand_Crossbow_T2_Load
SFX_Hand_Crossbow_T2_Load_Local
SFX_Hand_Crossbow_T2_Reload_Start
SFX_Hand_Crossbow_T2_Reload_Start_Local
SFX_Ice_Item_Impact
SFX_Ice_Item_Swing
SFX_Kweebec_Plushie_Impact
SFX_Club_Meat_Impact
SFX_Club_Meat_Swing
SFX_Longsword_Special_Impact
SFX_Longsword_Special_Swing
SFX_Longsword_Steel_Charged_Swing
SFX_Longsword_Steel_Impact
SFX_Longsword_Steel_Swing
SFX_Mace_T1_Block_Impact
SFX_Mace_T1_Impact
SFX_Mace_T2_Impact
SFX_Mace_T1_Raise
SFX_Mace_T1_Raise_Local
SFX_Mace_T1_Shove
SFX_Mace_T1_Shove_Local
SFX_Mace_T1_Swing
SFX_Mace_T1_Swing_Charged
SFX_Mace_T1_Swing_Charged_LR_Local
SFX_Mace_T1_Swing_Charged_RL_Local
SFX_Mace_T1_Swing_Charged_Up_Local
SFX_Mace_T1_Swing_LR_Local
SFX_Mace_T1_Swing_RL_Local
SFX_Mace_T1_Swing_Up_Local
SFX_Mace_T2_Raise
SFX_Mace_T2_Raise_Local
SFX_Mace_T2_Swing
SFX_Mace_T2_Swing_Charged
SFX_Mace_T2_Swing_Charged_LR_Local
SFX_Mace_T2_Swing_Charged_RL_Local
SFX_Mace_T2_Swing_Charged_Up_Local
SFX_Mace_T2_Swing_LR_Local
SFX_Mace_T2_Swing_RL_Local
SFX_Mace_T2_Swing_Up_Local
SFX_Mace_T2_Signature_Impact
SFX_Mace_T2_Signature_Impact_Local
SFX_Mace_T2_Signature_Launch
SFX_Mace_T2_Signature_Launch_Local
SFX_Light_Melee_T1_Guard_Hit
SFX_Light_Melee_T1_Impact
SFX_Light_Melee_T2_Guard_Hit
SFX_T1_Impact_Blunt
SFX_Light_Melee_T1_Block
SFX_Light_Melee_T1_Lunge
SFX_Light_Melee_T1_Lunge_Charge
SFX_Light_Melee_T1_Shove
SFX_Light_Melee_T1_Swing
SFX_Light_Melee_T2_Block
SFX_Light_Melee_T2_Guard_Break
SFX_Light_Melee_T2_Lunge
SFX_Light_Melee_T2_Lunge_Charge
SFX_Light_Melee_T2_Swing
SFX_Shield_T1_Break
SFX_Shield_T1_Impact
SFX_Shield_T1_Raise
SFX_Shield_T1_Raise_Local
SFX_Shield_T1_Swing
SFX_Shield_T1_Swing_Local
SFX_Shield_T2_Impact
SFX_Shield_T2_Raise
SFX_Shield_T2_Raise_Local
SFX_Shield_T2_Swing
SFX_Shield_T2_Swing_Local
SFX_Spear_Impact
SFX_Spear_Lunge
SFX_Spear_Lunge_Local
SFX_Spear_Miss
SFX_Spear_Projectile_Impact
SFX_Spear_Throw
SFX_Spear_Throw_Charge
SFX_Spear_Throw_Charge_Local
SFX_Spear_Throw_Local
SFX_Skeleton_Mage_Spellbook_Charge
SFX_Skeleton_Mage_Spellbook_Impact
SFX_Staff_Flame_Consume_Charge_1
SFX_Staff_Flame_Consume_Charge_1_Local
SFX_Staff_Flame_Consume_Charge_2
SFX_Staff_Flame_Consume_Charge_2_Local
SFX_Staff_Flame_Consume_Charge_3
SFX_Staff_Flame_Consume_Charge_3_Local
SFX_Staff_Flame_Consume_Charge_4
SFX_Staff_Flame_Consume_Charge_4_Local
SFX_Staff_Flame_Fireball_Impact
SFX_Staff_Flame_Fireball_Launch
SFX_Staff_Flame_Fireball_Launch_Local
SFX_Staff_Flame_Flamethrower
SFX_Staff_Flame_Flamethrower_End
SFX_Staff_Flame_Flamethrower_End_Local
SFX_Staff_Flame_Flamethrower_Impact
SFX_Staff_Flame_Flamethrower_Local
SFX_Staff_Flame_Trap_Deploy
SFX_Staff_Flame_Trap_Despawn
SFX_Staff_Flame_Trap_Loop
SFX_Staff_Charged_Loop
SFX_Staff_Fire_Shoot
SFX_Staff_Ice_Shoot
SFX_Tornado
SFX_Sword_T2_Impact
SFX_Sword_T1_Block_Local
SFX_Sword_T1_Lunge_Charge_Local
SFX_Sword_T1_Lunge_Local
SFX_Sword_T1_Shove_Local
SFX_Sword_T1_Swing
SFX_Sword_T1_Swing_Down
SFX_Sword_T1_Swing_Down_Local
SFX_Sword_T1_Swing_LR_Local
SFX_Sword_T1_Swing_RL_Local
SFX_Sword_T2_Block_Local
SFX_Sword_T2_Lunge_Charge_Local
SFX_Sword_T2_Lunge_Local
SFX_Sword_T2_Signature_Part_1
SFX_Sword_T2_Signature_Part_1_Local
SFX_Sword_T2_Signature_Part_2
SFX_Sword_T2_Signature_Part_2_Local
SFX_Sword_T2_Swing
SFX_Sword_T2_Swing_Down
SFX_Sword_T2_Swing_Down_Local
SFX_Sword_T2_Swing_LR_Local
SFX_Sword_T2_Swing_RL_Local
SFX_Trork_Throwing_Axe
SFX_Torch_Impact
SFX_Torch_Swing
SFX_NPC_Unarmed_Impact
SFX_NPC_Unarmed_Swing
SFX_Player_Unarmed_Swing_Left
SFX_Player_Unarmed_Swing_Right
SFX_Unarmed_Impact
SFX_Unarmed_Swing
SFX_Wand_Fire_Shoot
SFX_Wand_Ice_Shoot
```

### Root

```
SFX_Attn_ExtremelyQuiet
SFX_Attn_Loud
SFX_Attn_Moderate
SFX_Attn_Quiet
SFX_Attn_VeryLoud
SFX_Attn_VeryQuiet
```

---

## Damage Causes (15)

| ID | Parent | DamageTextColor | DurabilityLoss | StaminaLoss | BypassResistances |
|----|--------|-----------------|----------------|-------------|-------------------|
| `Bludgeoning` | Physical | - | - | - | no |
| `Command` | - | - | - | - | yes |
| `Drowning` | Environment | - | - | - | no |
| `Elemental` | - | - | - | - | no |
| `Environment` | - | - | - | - | yes |
| `Environmental` | - | - | - | - | no |
| `Fall` | Environment | - | - | - | no |
| `Fire` | Elemental | - | - | - | no |
| `Ice` | Elemental | - | - | - | no |
| `OutOfWorld` | Environment | - | - | - | no |
| `Physical` | - | - | - | - | no |
| `Poison` | - | #00FF00 | - | - | no |
| `Projectile` | - | - | - | - | no |
| `Slashing` | Physical | - | - | - | no |
| `Suffocation` | Environment | - | - | - | no |

---

*Generated by `~/tools/catalog-vanilla-assets.sh` — do not edit manually.*
