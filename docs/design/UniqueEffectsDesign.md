# Unique Item Effects Design

Comprehensive design for unique item special effects, categorized by implementation complexity and effect type.

**Based on**: POE1/POE2 effect research + Hytale plugin capabilities

---

## Table of Contents

1. [Effect Categories Overview](#effect-categories-overview)
2. [Easily Implementable Effects](#easily-implementable-effects)
3. [Moderately Complex Effects](#moderately-complex-effects)
4. [Avoid For Now](#avoid-for-now)
5. [Example Unique Items](#example-unique-items)
6. [Technical Implementation Notes](#technical-implementation-notes)

---

## Effect Categories Overview

### What Makes Effects "Easy" vs "Hard"

| Complexity | Criteria | Examples |
|------------|----------|----------|
| **Easy** | Uses existing stats, events, simple conditions | Stat bonuses, on-hit effects, damage conversion |
| **Moderate** | Requires state tracking, timers, stacking | "Recently" effects, buff stacks, cooldowns |
| **Hard** | Requires new systems, 3D creation, complex AI | Spawning minions, custom projectiles, new animations |

### Available Hooks (From Research)

| System | What We Can Hook |
|--------|------------------|
| **Damage Events** | On deal damage, on take damage, damage amount modification |
| **Kill Events** | On kill, on assist, death info |
| **Equipment Events** | On equip, on unequip, slot change |
| **Movement** | Position, velocity, jumping state, sprinting |
| **Combat State** | In combat, last hit time, target info |
| **Stats** | All ComputedStats values, can modify dynamically |
| **Health/Mana/Stamina** | Current values, max values, regen rates |

---

## Easily Implementable Effects

### Category 1: Conditional Stat Bonuses ("While X")

Effects that check a condition and apply stat bonuses while true.

| Condition Type | Example Effect | Implementation |
|----------------|----------------|----------------|
| **Health Threshold** | "+50% damage while below 30% health" | Check health %, apply damage modifier |
| **Mana Threshold** | "+20% cast speed while mana is full" | Check mana == maxMana |
| **Resource Empty** | "+100% crit chance while stamina is empty" | Check stamina == 0 |
| **Moving** | "+15% evasion while moving" | Check velocity > 0 |
| **Stationary** | "+30% armor while standing still" | Check velocity == 0 |
| **In Air** | "+25% damage while airborne" | Check !onGround |
| **Sprinting** | "+10% movement speed while sprinting" | Check sprint state |
| **In Combat** | "+20% attack speed while in combat" | Check combat timer |
| **Out of Combat** | "+5% health regen while out of combat" | Check combat timer expired |
| **Full Health** | "Cannot be critically hit while at full health" | Check health == maxHealth |

**POE Examples**:
- "Mokou's Embrace": Increased attack/cast speed while ignited
- "Rise of the Phoenix": Additional fire resistance while low-life

---

### Category 2: On-Hit Effects (Dealing Damage)

Effects that trigger when you hit an enemy.

| Effect Type | Example | Implementation |
|-------------|---------|----------------|
| **Chance to Heal** | "10% chance to heal 5% of damage dealt on hit" | RNG on damage event, heal player |
| **Life Steal** | "3% of damage dealt as health" | Calculate % of damage, heal |
| **Mana on Hit** | "Gain 5 mana on hit" | Add mana on damage event |
| **Apply Debuff** | "20% chance to slow enemy by 30% for 3s on hit" | RNG, apply slow effect |
| **Bonus Damage** | "Deal 10% of target's current health as bonus damage" | Read target health, add damage |
| **Chain Damage** | "10% chance to deal 50% damage to nearby enemy on hit" | Find nearby, deal damage |
| **Stat Steal** | "Steal 5% of target's armor on hit for 4s" | Reduce target armor, buff self |

**POE Examples**:
- "Thief's Torment": Gives life and mana back when you hit enemies
- "Eye of Winter amulet": Blind and chill enemies on hit

---

### Category 3: On-Kill Effects

Effects that trigger when you kill an enemy.

| Effect Type | Example | Implementation |
|-------------|---------|----------------|
| **Heal on Kill** | "Recover 10% of max health on kill" | On death event, heal player |
| **Resource on Kill** | "Gain 20 mana on kill" | On death event, add mana |
| **Buff on Kill** | "Gain 15% movement speed for 5s on kill" | Apply timed buff |
| **Damage Buff** | "Gain 5% damage per kill, max 50%, resets after 10s" | Stacking buff with timer |
| **Explosion** | "Enemies explode for 10% of their max HP on kill" | AoE damage on death position |
| **Soul Harvest** | "Gain 1 soul per kill, consume 10 souls for massive buff" | Counter + activation |

**POE Examples**:
- "Headhunter": Gain rare monster modifiers on kill
- "Daresso's Passion": 25% chance to gain Frenzy Charge on kill

---

### Category 4: On-Take-Damage Effects

Effects that trigger when you receive damage.

| Effect Type | Example | Implementation |
|-------------|---------|----------------|
| **Damage Reflection** | "Reflect 20% of physical damage taken to attacker" | On damage received, deal to source |
| **Defensive Buff** | "Gain 30% armor for 3s when hit" | Apply timed buff on damage |
| **Counter Attack** | "25% chance to automatically attack when hit" | Trigger attack on damage |
| **Damage Absorption** | "10% of damage taken is absorbed as mana" | Reduce damage, gain mana |
| **Revenge Damage** | "Your next attack deals +50% damage after being hit" | Flag + damage modifier |
| **Thorns** | "Attackers take 50 physical damage" | Flat damage to attacker |

---

### Category 5: Damage Conversion & Modification

Effects that change how damage works.

| Effect Type | Example | Implementation |
|-------------|---------|----------------|
| **Element Conversion** | "50% of physical damage converted to fire" | Modify damage calculation |
| **Added Element** | "Attacks deal additional 20 fire damage" | Add flat damage |
| **Damage as Extra** | "Gain 15% of physical damage as extra cold" | Calculate bonus, add |
| **Type Amplification** | "Fire damage increased by 30%" | Multiply fire damage |
| **Penetration** | "Damage penetrates 15% of enemy fire resistance" | Reduce effective resistance |
| **True Damage** | "10% of damage dealt is converted to true damage" | Bypass defenses |

**POE Examples**:
- "Moonbender's Wing": Converts physical damage to cold or lightning

---

### Category 6: Enemy State Conditionals

Extra effects based on enemy condition.

| Condition | Example Effect | Implementation |
|-----------|----------------|----------------|
| **Burning Enemy** | "+30% damage to burning enemies" | Check enemy has burn debuff |
| **Frozen Enemy** | "Hits against frozen enemies always crit" | Check freeze, force crit |
| **Low Health** | "+50% damage to enemies below 30% health" | Check enemy health % |
| **Full Health** | "First hit against full health deals double damage" | Check enemy health == max |
| **Debuffed** | "+20% damage to slowed enemies" | Check for any debuff |
| **Elite/Boss** | "+25% damage to elite enemies" | Check mob classification |

**POE Examples**:
- "Singularity": 100% increased damage vs hindered enemies

---

### Category 7: Positional Effects

Effects based on positioning.

| Position | Example Effect | Implementation |
|----------|----------------|----------------|
| **Behind Target** | "+40% damage when attacking from behind" | Compare facing directions |
| **Above Target** | "+25% damage when above the enemy" | Compare Y positions |
| **Close Range** | "+20% damage within 3 blocks" | Calculate distance |
| **Long Range** | "+15% projectile damage beyond 10 blocks" | Calculate distance |
| **Elevation** | "+10% damage per block of height advantage" | Calculate Y difference |

---

### Category 8: Resource Trade-offs

Effects that spend one resource for another benefit.

| Trade-off | Example | Implementation |
|-----------|---------|----------------|
| **Health for Damage** | "Spend 5% max health to deal 30% more damage" | Deduct health, apply buff |
| **Mana for Defense** | "Spend mana instead of taking damage (1:2 ratio)" | Intercept damage, drain mana |
| **Stamina for Speed** | "Sprint costs no stamina but drains health" | Modify sprint mechanics |
| **All-in** | "Deal 200% damage but take 50% of it as self-damage" | High risk/reward |

**POE Examples**:
- "Cloak of Defiance": 30% of damage taken from mana before life
- Blood Magic: Spend life instead of mana

---

## Moderately Complex Effects

### Category 9: "Recently" Time-Based Effects

Effects that check if something happened in the last X seconds (typically 4s).

| Trigger | Example Effect | Implementation |
|---------|----------------|----------------|
| **Killed Recently** | "+20% damage if you killed in last 4s" | Track last kill timestamp |
| **Hit Recently** | "+30% armor if hit in last 4s" | Track last damage taken timestamp |
| **Jumped Recently** | "Double damage on next attack after jumping" | Track jump timestamp |
| **Used Ability Recently** | "+15% cast speed if used ability in last 4s" | Track ability timestamp |
| **Crit Recently** | "Guaranteed crit if you crit in last 4s" | Track last crit timestamp |

**POE Definition**: "Recently" refers to the past 4 seconds.

---

### Category 10: Stacking Buffs

Effects that build up over time or actions.

| Stack Type | Example | Implementation |
|------------|---------|----------------|
| **On Hit Stacks** | "Gain Fury on hit, +2% damage per stack, max 25" | Counter, cap, decay timer |
| **On Kill Stacks** | "Gain Rampage stack on kill, +1% speed per stack" | Counter, reset on timeout |
| **On Take Damage** | "Gain Vengeance stack when hit, consume for burst" | Counter, active ability |
| **Time-Based** | "Gain 1 stack per second in combat, +5% damage each" | Periodic increment |
| **Combo System** | "Consecutive hits grant +10% damage, resets on miss" | Track hit streak |

**POE Examples**:
- Rampage: Kill streaks grant stacking bonuses
- Frenzy/Power/Endurance Charges

---

### Category 11: Cooldown-Based Effects

Effects with internal cooldowns or periodic triggers.

| Type | Example | Implementation |
|------|---------|----------------|
| **Periodic Trigger** | "Every 5 seconds, gain 10% of max health" | Timer system |
| **Proc Cooldown** | "On hit effect can only trigger once per 2s" | Timestamp tracking |
| **Charge System** | "Store up to 3 charges, each use consumes 1" | Counter, recharge timer |
| **Burst Window** | "Every 30s, next 5s deal double damage" | Cycle timer |

---

### Category 12: Aura/Proximity Effects

Effects that affect nearby entities.

| Type | Example | Implementation |
|------|---------|----------------|
| **Damage Aura** | "Enemies within 5 blocks take 10 fire damage/sec" | Periodic area check |
| **Debuff Aura** | "Enemies within 3 blocks are slowed by 20%" | Apply effect to nearby |
| **Buff Aura** | "Allies within 10 blocks gain +10% damage" | Apply to friendly entities |
| **Proximity Bonus** | "+5% damage for each enemy within 5 blocks" | Count nearby, scale bonus |

---

## Avoid For Now

These effects require complex systems not worth building initially:

| Effect Type | Why Avoid |
|-------------|-----------|
| **Spawn Minions** | Requires entity creation, AI, pathfinding |
| **Create Projectiles** | Requires projectile system integration |
| **Custom Animations** | Requires client-side assets |
| **Transform Player** | Complex model/stat replacement |
| **Teleportation** | Position manipulation edge cases |
| **Time Manipulation** | Slowmo/freeze effects are complex |
| **Copy Enemy Abilities** | Requires dynamic skill system |

---

## Example Unique Items

### Weapons

#### "Bloodthirst" (Sword)
*"The blade drinks deep, and so shall you."*

| Effect | Type |
|--------|------|
| +50-80 Physical Damage | Base stat |
| 5% of damage dealt as health | Life Steal (On-Hit) |
| +30% damage while below 50% health | Conditional (Health) |
| Lose 2% max health per second while in combat | Trade-off |

---

#### "Stormcaller's Wrath" (Staff)
*"The sky answers your fury."*

| Effect | Type |
|--------|------|
| +40-60 Lightning Damage | Base stat |
| 25% chance to deal double lightning damage | On-Hit Proc |
| +50% lightning damage to wet enemies | Enemy Conditional |
| Lightning damage penetrates 20% resistance | Penetration |

---

#### "Executioner's Edge" (Axe)
*"Mercy is for the weak."*

| Effect | Type |
|--------|------|
| +60-100 Physical Damage | Base stat |
| +100% damage to enemies below 20% health | Enemy Conditional |
| Kills restore 15% max health | On-Kill |
| -20% damage to enemies above 50% health | Drawback |

---

### Armor

#### "Phoenix Heart" (Chestplate)
*"From ashes, reborn."*

| Effect | Type |
|--------|------|
| +200 Max Health | Base stat |
| +50% fire resistance | Base stat |
| When you would die, instead heal to 30% (once per 5 minutes) | Death Prevention |
| +25% damage for 10s after triggering rebirth | Post-Trigger Buff |

---

#### "Coward's Sanctuary" (Light Armor)
*"Survival is victory."*

| Effect | Type |
|--------|------|
| +30% evasion while below 30% health | Conditional (Health) |
| +50% movement speed while below 30% health | Conditional (Health) |
| Cannot deal critical hits | Drawback |
| -30% damage dealt | Drawback |

---

#### "Berserker's Fury" (Heavy Armor)
*"Pain is power."*

| Effect | Type |
|--------|------|
| +100 Armor | Base stat |
| Gain 2% damage for each 1% health missing | Scaling Conditional |
| Take 10% increased damage | Drawback |
| Cannot be healed by external sources | Drawback |

---

### Boots

#### "Shadowstep Treads"
*"You were never there."*

| Effect | Type |
|--------|------|
| +25% movement speed | Base stat |
| Attacks from behind deal +40% damage | Positional |
| +50% movement speed for 2s after killing from behind | On-Kill Conditional |
| First hit after not attacking for 3s deals double damage | Recently (inverted) |

---

#### "Groundshaker Stompers"
*"The earth trembles."*

| Effect | Type |
|--------|------|
| +50 Armor | Base stat |
| Jumping deals 50 damage to enemies within 3 blocks on landing | On-Land AoE |
| +100% jump force | Base stat |
| -30% movement speed | Drawback |

---

### Accessories

#### "Gambler's Coin" (Amulet)
*"Fortune favors the bold... sometimes."*

| Effect | Type |
|--------|------|
| 10% chance to deal triple damage | On-Hit Proc |
| 10% chance to deal zero damage | On-Hit Proc (negative) |
| +50% to critical multiplier | Base stat |
| +10 Luck | Base stat |

---

#### "Equilibrium Band" (Ring)
*"Balance in all things."*

| Effect | Type |
|--------|------|
| Your lowest resistance becomes equal to your highest | Stat Equalization |
| +20% to all resistances | Base stat |
| -15% damage dealt | Drawback |

---

#### "Vampiric Seal" (Ring)
*"Life begets life."*

| Effect | Type |
|--------|------|
| 8% life steal | On-Hit |
| +20% damage while at full health | Conditional |
| No natural health regeneration | Drawback |
| Heal for 5% max health on kill | On-Kill |

---

### Shields

#### "Mirror of Retribution"
*"Your sins, returned."*

| Effect | Type |
|--------|------|
| +100 Armor | Base stat |
| 40% of blocked damage reflected to attacker | On-Block Reflect |
| +30% block chance | Base stat |
| Blocking costs stamina | Trade-off |

---

#### "Bulwark of the Coward"
*"Behind me, the storm."*

| Effect | Type |
|--------|------|
| +200 Armor | Base stat |
| +50% armor while standing still | Conditional (Stationary) |
| Blocking makes you immune to next hit (5s cooldown) | Cooldown Proc |
| -50% damage dealt while shield equipped | Major Drawback |

---

## Technical Implementation Notes

### Effect System Architecture

```
UniqueEffect (Interface)
├── ConditionalEffect (checks state, returns stat modifier)
├── OnHitEffect (triggers on dealing damage)
├── OnKillEffect (triggers on kill)
├── OnDamagedEffect (triggers on taking damage)
├── PeriodicEffect (triggers on timer)
└── AuraEffect (affects nearby entities)

EffectContext
├── Player reference
├── Target reference (if applicable)
├── Damage info (if applicable)
├── Timestamp
└── Random seed
```

### State Tracking Needed

| State | Storage | Update Frequency |
|-------|---------|------------------|
| Last kill timestamp | Per-player | On kill |
| Last hit timestamp | Per-player | On damage dealt |
| Last damaged timestamp | Per-player | On damage taken |
| Stack counts | Per-player per-effect | On trigger |
| Cooldown timestamps | Per-player per-effect | On trigger |
| Combat state | Per-player | Tick-based |

### Performance Considerations

1. **Conditional checks** should be cached and only recalculated when relevant state changes
2. **On-hit effects** run frequently - keep them lightweight
3. **Aura effects** should use spatial partitioning for entity lookups
4. **Stacking buffs** should have a maximum cap to prevent runaway values

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-01-23 | Initial design based on POE research |
