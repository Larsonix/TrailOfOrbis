# Monster Modifier System — Design Specification

> **Status**: Design complete, ready for implementation
> **Date**: 2026-05-10
> **Companion docs**:
> - `MonsterModifierResearch.md` — Industry research (9 games analyzed)
> - `MonsterModifierCapabilities.md` — Engine capabilities (all questions resolved)

---

## 1. Design Philosophy

### What This System Accomplishes

Every elite and boss encounter should feel unique. Right now, an Elite Trork Captain is just a Normal Trork Captain with 2.5x HP. The fight is identical, just slower. With modifiers:

- **Each elite is a story.** "I fought a Blazing Trork that left fire trails everywhere."
- **Each elite demands adaptation.** You see the modifier, you adjust your approach.
- **Each elite is worth killing.** More modifiers = better loot, always.

### Core Principles

1. **Mechanics over numbers.** Difficulty comes from what the mob DOES, not how much HP it has. Stat multipliers are lowered to make room for modifier-driven challenge.
2. **Readable from a distance.** In Hytale's 3D world, players see modified mobs coming. The visual language works at 100 blocks (scale), 50 blocks (glow/particles), and 10 blocks (full effects).
3. **Fully random.** Any modifier can appear on any mob. Surprises are fun. Dead rolls (modifier has no effect on this mob type) are handled by a small exclusion list, not by thematic filtering.
4. **Progressive complexity.** Level 5 elites get simple stat modifiers. Level 50 elites might teleport behind you and summon minions. Complexity scales with player experience.
5. **Fair warning.** Every modifier has a visual telegraph. Death effects have a delay. No invisible one-shots.

---

## 2. Visual Identity System

### The Distance Gradient

No ARPG has this. In top-down games, you see everything at once. In Hytale, the player gradually discovers the threat:

| Distance | What the Player Sees | Visual Layer |
|----------|---------------------|-------------|
| **100+ blocks** | "Something bigger over there" | `EntityScaleComponent` — 1.15x for elite, 1.3x for boss |
| **50-100 blocks** | "It's glowing — fire? ice?" | `ModelVFX` — element-colored highlight with bloom |
| **20-50 blocks** | Nameplate becomes readable: "★ Blazing Trork Captain" | `Nameplate.setText()` |
| **10-20 blocks** | Full particle effects visible, element aura, ground effects | `ApplicationEffects.particles[]` |
| **Combat range** | Hear the sound, see the tint, experience the mechanic | `soundEventIdWorld` + `entityTint` + gameplay |
| **On death** | Death message with modifier name | `DisplayNameComponent` (Message format, colored) |

### Visual Stack Per Modifier

Each modifier has a unique visual signature composed of these layers:

| Layer | Tool | Purpose | Performance Cost |
|-------|------|---------|-----------------|
| **Scale** | `EntityScaleComponent` | Tier identification (not per-modifier) | Negligible |
| **Tint** | `ApplicationEffects.entityBottomTint/entityTopTint` | Element/modifier color identification | Negligible |
| **ModelVFX** | `ApplicationEffects.modelVFXId` | Glow, highlight, shader effects | Low |
| **Particles** | `ApplicationEffects.particles[]` | Element particles, aura effects | Medium |
| **Sound** | `ApplicationEffects.soundEventIdWorld` | Ambient presence + danger warning | Negligible |

All layers stack via separate `EntityEffect` IDs on the same mob. Confirmed: multiple effects coexist.

### Stacking Visuals (Multi-Modifier Mobs)

Bosses have 2 modifiers, Elite Bosses have 3. Each modifier adds its own EntityEffect:

```
Elite (1 mod):      Base scale + 1 modifier effect
Boss (2 mods):      Base scale + 2 modifier effects (tints blend, particles overlap)
Elite Boss (3 mods): Base scale + 3 modifier effects (visually intense — intended)
```

A boss with "Blazing" + "Evasive" would have fire tint + fire particles + afterimage dodge particles. The visual intensity signals danger level — this is a feature, not a problem.

### Nameplate Format

```
★ [Modifier] [Mob Name]           — 1 modifier (elite)
★★ [Modifier] [Mob Name] the [Modifier]  — 2 modifiers (boss)
★★★ [Mod] [Mob Name] the [Mod] [Mod]     — 3 modifiers (elite boss)
```

Examples:
```
★ Blazing Trork Captain
★★ Thunderous Dragon the Regenerating
★★★ Swift Scarak Broodmother the Enraged Volatile
```

Stars indicate tier at a glance. Offensive modifier as prefix, defensive/behavioral as suffix. This is the FALLBACK identification — the visual effects are primary.

---

## 3. Tier System & Stat Rebalancing

### Current vs. New Tier Multipliers

The philosophy shift: **lower stats, add mechanics**. Modifiers provide the challenge, not HP inflation.

| Stat | Normal | Elite (current) | Elite (new) | Boss (current) | Boss (new) | Elite Boss (new) |
|------|--------|-----------------|-------------|----------------|------------|-----------------|
| **HP** | 1.0x | 2.5x | **1.2x** | 8.0x | **2.0x** | **3.0x** |
| **Damage** | 1.0x | 1.3x | **1.05x** | 1.5x | **1.1x** | **1.15x** |
| **Armor** | 1.0x | 1.5x | **1.1x** | 2.0x | **1.2x** | **1.3x** |
| **Evasion** | 1.0x | 1.3x | **1.0x** | 1.0x | **1.0x** | **1.0x** |
| **Speed** | 1.0x | 1.1x | **1.0x** | 1.0x | **1.0x** | **1.0x** |
| **XP** | 1.0x | 3.0x | **3.0x** | 10.0x | **10.0x** | **15.0x** |
| **IIQ** | 1.0x | 3.0x | → per-modifier | 10.0x | → per-modifier | → per-modifier |
| **IIR** | 1.0x | 2.0x | → per-modifier | 8.0x | → per-modifier | → per-modifier |
| **Ailment Eff** | 1.0x | 0.7x | **0.85x** | 0.4x | **0.7x** | **0.6x** |
| **Modifiers** | 0 | 0 | **1** | 0 | **2** | **3** |

**Rationale — "Mechanics over numbers":**
- **HP barely inflated**: An Elite takes 2-3 extra hits. A Boss takes roughly twice as long. The modifier mechanic IS the difficulty — HP just makes the mob feel slightly meatier, never spongy.
- **Damage barely inflated**: With modifiers adding elemental damage, ailments, and behavioral pressure, the base mob barely hits harder. The modifier-granted abilities provide the actual threat.
- **Armor minimal**: Modifier-granted defenses (Hardened, Evasive) replace flat tier inflation. This way "Hardened" is a meaningful modifier, not redundant with a tier bonus that already gave armor.
- **Evasion/Speed removed from tier**: Speed and evasion modifiers (Swift, Evasive) should feel impactful when rolled, not diluted by tier bonuses already providing them.
- **XP unchanged**: Killing modded mobs should still feel very rewarding for progression.
- **IIQ/IIR moved to per-modifier**: See Reward Integration section.
- **Ailment effectiveness higher**: With much lower HP pools, ailments need to remain relevant. Mobs shouldn't be nearly immune to ailments just because they're elite.

### What Is an "Elite Boss"?

A Boss mob (dragon, zone boss, etc.) that also rolled elite at spawn time. This is rare and exciting:

- **Spawn chance**: Same elite spawn formula, but applied to Boss-classified mobs
- **Formula**: `chance = min(0.05 + level × 0.0001, 0.25)` — so 5-25% of bosses become Elite Bosses
- **Visual**: Boss scale (1.3x) + 3 modifier effects stacking = extremely dramatic
- **Reward**: Highest IIQ/IIR multiplier in the game

### Realm Overrides

Realms are opt-in endgame content. Slightly tougher baselines:

| Tier | Realm HP Override | Realm Modifier Count |
|------|-------------------|---------------------|
| Elite | 1.4x (was 1.2x) | 1 (same) |
| Boss | 2.5x (was 2.0x) | 2 (same) |
| Elite Boss | 3.5x (was 3.0x) | 3 (same) |

---

## 4. The Modifier Pool

### Overview

21 modifiers across 6 categories, spanning 4 complexity tiers.

| Category | Count | Combat Depth | Description |
|----------|-------|-------------|-------------|
| **A. Stat-Based** | 5 | Light | Changes the math |
| **B. Elemental** | 4 | Medium | Adds element + ailment |
| **C. Tactical Counter** | 3 | Medium | Punishes a playstyle |
| **D. Behavioral** | 4 | Heavy | Changes how the mob fights |
| **E. Aura/Area** | 3 | Medium-Heavy | Affects space around the mob |
| **F. Death Trigger** | 2 | Medium | Fires on kill |

### Complexity Tiers (Level-Gated)

| Tier | Available From | Modifiers | Design Intent |
|------|---------------|-----------|---------------|
| **Tier 1** | Level 1+ | Hardened, Vigorous, Fierce, Swift, Resolute | New players learn "elites are different" through simple stat changes. No mechanical surprises. |
| **Tier 2** | Level 10+ | Blazing, Frozen, Thunderous, Venomous, Enraged | Introduces elemental modifiers and the first behavioral mod (Enraged). Players who've built some gear and learned elements. |
| **Tier 3** | Level 25+ | Evasive, Warding, Regenerating, Pack Leader, Frost Aura | Tactical modifiers that reward specific counter-strategies. Players with diverse gear and skill tree investment. |
| **Tier 4** | Level 40+ | Reflective, Shadow Step, Summoner, Volatile, Rallying | Complex mechanics requiring real adaptation. Endgame players with strong builds. |

When a mob rolls a modifier, it picks randomly from all tiers **at or below** the mob's level tier. Higher-level mobs have a larger pool and more chance of drawing complex modifiers.

---

### Category A: Stat-Based (Tier 1)

These change the math of the fight. The player notices the mob is tougher in a specific way.

---

#### A1. Hardened

**Mechanic**: +50% armor. The mob takes significantly less physical damage. Elemental damage is unaffected.

**Visual**:
- Tint: Dull gray-brown gradient (bottom `#8B7355`, top `#A0926B`)
- ModelVFX: `Stoneskin` (vanilla asset — armor/shield visual)
- Particles: Subtle stone dust particles
- Sound: Metallic clink ambient

**Player Counter**: Use elemental weapons or spells. Physical builds need armor penetration.

**Edge Cases**:
- Stacks with mob's natural armor from archetype. Could make Tanks very tanky. Acceptable — the counter is elemental damage.
- On a Caster archetype (0.3x base armor), +50% of a small number is negligible. Not a dead roll because 0.3 × 1.5 = 0.45 is still a meaningful relative increase.

---

#### A2. Vigorous

**Mechanic**: +50% max HP (multiplicative with tier multiplier). The mob simply takes more hits to kill.

**Visual**:
- Tint: Deep green gradient (bottom `#2D5A27`, top `#4A8B3F`)
- ModelVFX: Custom — subtle green pulse, LoopMirror, slow
- Particles: Faint green vitality motes orbiting the mob
- Sound: Deep heartbeat ambient (low volume)

**Player Counter**: Sustained DPS builds. Burst builds take longer but aren't countered — this is a pure numbers check.

**Edge Cases**:
- On a Boss with 2.0x HP base, Vigorous gives 3.0x total. Still well below the OLD Boss HP of 8.0x. The fight is longer but the modifiers provide the challenge, not the HP bar.
- Combined with Regenerating on a multi-modifier boss: very tanky. Acceptable as a difficult combo — players can still burst through it.

---

#### A3. Fierce

**Mechanic**: +35% damage dealt. All attacks hit harder. Simple and dangerous.

**Visual**:
- Tint: Red gradient (bottom `#8B2500`, top `#CC3300`)
- ModelVFX: Custom — red highlight, thin, pulsing
- Particles: Faint red anger wisps
- Sound: Low growl ambient

**Player Counter**: Defensive play. Block more, dodge more, use defensive stats. Or kill it fast.

**Edge Cases**:
- On a Boss that already has 1.1x damage: 1.1 × 1.35 = 1.485x. Below the old Boss damage of 1.5x. The modifier creates pressure without exceeding previous levels.
- On an Assassin archetype (1.4x base damage × 1.35 fierce = 1.89x effective). Dangerous but the tier damage multiplier is only 1.05-1.15x, so total is 1.4 × 1.05 × 1.35 = 1.98x. Comparable to old Elite damage (1.3x × 1.4 = 1.82x). Slightly higher but the mob has much less HP now.

---

#### A4. Swift

**Mechanic**: +40% movement speed. The mob chases harder and repositions constantly. Attack speed unchanged (client-gated).

**Visual**:
- Tint: Light blue-white gradient (bottom `#88CCFF`, top `#CCECFF`)
- ModelVFX: Custom — wind streak highlight, BottomUp direction, fast duration
- Particles: Speed trail particles behind the mob
- Sound: Rushing wind ambient

**Player Counter**: Range. Kite. Use slows (Freeze ailment). Or hold ground and block.

**Edge Cases**:
- Applied via `horizontalSpeedMultiplier` in ApplicationEffects. Confirmed working.
- On a mob that already has high speed (Beast archetype 1.15x): 1.15 × 1.4 = 1.61x speed. Fast but not teleporting. Acceptable.
- Dead roll on immobile/turret-type mobs? No turret mobs exist in Hytale currently. If they did, add to exclusion list.

---

#### A5. Resolute

**Mechanic**: Cannot be knocked back, staggered, or interrupted. Knockback multiplier set to 0.0. The mob is an unstoppable force.

**Visual**:
- Tint: Dark iron gradient (bottom `#3A3A3A`, top `#5A5A5A`)
- ModelVFX: Custom — subtle dark pulse, ground-anchored feel
- Particles: Heavy dust around feet
- Sound: Deep rumble ambient

**Player Counter**: Can't stunlock or kite with knockback. Must dodge or block attacks instead of interrupting them. Positioning and movement become critical.

**Edge Cases**:
- Uses `knockbackMultiplier: 0.0` in ApplicationEffects. Confirmed supported.
- On a Tank archetype (already 80% KB resist): redundant. Not a dead roll because 80% → 100% is still a qualitative change (completely immune vs. sometimes pushed).

---

### Category B: Elemental Enchantment (Tier 2)

These add an elemental identity to any mob, tying into our element and ailment systems. Each adds bonus damage of that element and a chance to apply the corresponding ailment.

---

#### B1. Blazing

**Mechanic**: Attacks deal +30% of base damage as bonus Fire damage. 20% chance to apply Burn ailment on hit. Leaves a short burning trail while moving (particle-only, no block modification — deals small fire DoT to players standing in it via area tick).

**Visual**:
- Tint: Fire gradient (bottom `#FF4400`, top `#FFAA00`)
- ModelVFX: `Burn` (vanilla asset)
- Particles: Flame particles attached + fire trail particles behind mob
- Sound: Crackling fire ambient

**Player Counter**: Fire resistance. Burn ailment resist. Stay out of the trail — kite or circle.

**Ailment Integration**: Uses existing `AilmentType.BURN` via `CombatAilmentApplicator`. The 20% chance is additive with any existing fire ailment chance.

**Edge Cases**:
- On a mob that already has Fire element: stacks (more fire damage, higher burn chance). Thematically coherent and dangerous. Acceptable.
- Trail is particle-only + area damage tick system, NOT block placement. No terrain modification, no cleanup needed.
- Trail duration: 3 seconds, damage: 5% of mob's base damage per second.

---

#### B2. Frozen

**Mechanic**: Attacks deal +30% of base damage as bonus Water damage. 20% chance to apply Freeze ailment on hit. Slows all players within 4 blocks by 15% (passive aura via ApplicationEffects speed multiplier on player).

**Visual**:
- Tint: Ice gradient (bottom `#44AADD`, top `#CCECFF`)
- ModelVFX: `Freeze` (vanilla asset)
- Particles: Frost crystal particles + cold mist at feet
- Sound: Icy wind ambient

**Player Counter**: Water/Cold resistance. Freeze resist. Stay at range to avoid slow aura. Fire damage to counter thematically.

**Edge Cases**:
- Slow aura applies to players, not the mob itself. Applied via temporary EntityEffect on players within range, ticking every 0.5s.
- Multiple Frozen mobs in range: slow doesn't stack (same effect ID on player, IGNORE overlap).

---

#### B3. Thunderous

**Mechanic**: Attacks deal +30% of base damage as bonus Lightning damage. 20% chance to apply Shock ailment on hit. Every 8 seconds, calls a lightning strike at the player's position with a 1.5-second visual telegraph (glowing circle on ground via particles).

**Visual**:
- Tint: Electric gradient (bottom `#DDAA00`, top `#FFFF44`)
- ModelVFX: Custom — electric highlight with spark noise overlay
- Particles: Electric spark particles, occasional arcs
- Sound: Electrical crackle ambient + thunder clap on lightning strike

**Player Counter**: Lightning resistance. Shock resist. Watch for the ground telegraph and dodge. The 1.5s delay is generous — always dodgeable.

**Edge Cases**:
- Lightning strike damage: 50% of mob's base damage as Lightning type. Enough to matter, not enough to one-shot.
- Telegraph uses particle ground indicator (circle of sparks), not block modification.
- Multiple Thunderous mobs: each has independent 8s timer. Can create overlapping strikes — dangerous but readable.

---

#### B4. Venomous

**Mechanic**: Attacks deal +30% of base damage as bonus Void damage. 20% chance to apply Poison ailment on hit (stacking DoT). On death, releases a poison cloud at death location lasting 5 seconds, dealing Void DoT to players inside.

**Visual**:
- Tint: Toxic gradient (bottom `#225522`, top `#66AA44`)
- ModelVFX: `Poison` (vanilla asset)
- Particles: Poison mist particles, green drip particles
- Sound: Hissing ambient + gurgling on death cloud

**Player Counter**: Void resistance. Poison ailment resist. Step away from death location (death cloud is the main threat, not the on-hit).

**Edge Cases**:
- Death cloud is a Category F element (death trigger) baked into this modifier. This means Venomous is a hybrid B+F modifier. Acceptable — the death cloud is thematic and teaches players to respect corpses.
- Poison stacks up to 10 (existing ailment system cap). Venomous mob attacking rapidly can stack high. Dangerous but counterable with Poison resistance.

---

### Category C: Tactical Counter (Tier 3)

These punish specific playstyles and force adaptation. Players who always fight the same way will struggle; players who adapt will thrive.

---

#### C1. Reflective

**Mechanic**: Returns 15% of physical damage taken back to the attacker as the mob's primary element (or Physical if no element). Reflected damage is capped — it can never reduce the attacker below 20% HP from a single reflection tick.

**Visual**:
- Tint: Mirror-silver gradient (bottom `#888888`, top `#CCCCCC`)
- ModelVFX: Custom — chrome highlight, high thickness, bloom enabled
- Particles: Brief flash particles on the mob each time damage is reflected
- Sound: Metallic ping on each reflection

**Player Counter**: Use elemental damage (only physical reflects). Use ranged weapons (same mechanic but gives you reaction time). Use slower, harder hits instead of fast attacks (fewer reflection ticks). Lifesteal counters reflected damage.

**Hook Point**: Phase 7 — `CombatRecoveryProcessor`, extends existing Thorns system. Reflected damage IS thorns with a different source and cap.

**Edge Cases**:
- 20% HP floor prevents reflection-kills. Lesson from D3: reflect should never be the thing that kills you. It's pressure, not a death sentence.
- On a Vigorous (extra HP) boss: more total HP to reflect from, but the % is the same. Not a degenerate combo.
- Player with lifesteal: heals from their attacks AND takes reflection damage. Net effect depends on stats — creates interesting gearing decisions.

---

#### C2. Warding

**Mechanic**: +50% resistance to ALL elemental damage (Fire, Water, Lightning, Earth, Wind, Void). Physical and Magic damage are unaffected. The mob is magically shielded.

**Visual**:
- Tint: Arcane purple gradient (bottom `#442266`, top `#7744AA`)
- ModelVFX: Custom — purple highlight, noise overlay with scroll, LoopMirror
- Particles: Arcane sigil particles orbiting the mob
- Sound: Low arcane hum ambient

**Player Counter**: Physical weapons. Armor penetration. The opposite of Hardened — Warding rewards physical builds while Hardened rewards elemental builds.

**Edge Cases**:
- +50% elemental resistance stacks with existing mob resistances. A mob with 30% fire resist + Warding = 80% fire resist. Approaching but not reaching immunity (cap is 75% in our system). Wait — if our cap is 75%, then 30+50 = 80 is capped to 75. So this is safe. Verify: does our resistance cap apply to mobs? Need to confirm. If not, could create near-immunities. Mitigation: ensure mob resistance cap exists or reduce Warding to +40%.
- Warding + Hardened on a 2-modifier boss: high physical DR AND high elemental resist. This is the toughest defensive combo possible. Not a dead roll — it's a genuinely hard fight. Players need mixed damage + penetration. Acceptable as a rare, challenging combo.

---

#### C3. Evasive

**Mechanic**: 25% chance to dodge melee attacks. Cannot dodge projectiles, AoE damage, or spells. On successful dodge, the mob briefly shows an afterimage.

**Visual**:
- Tint: Translucent blue-gray gradient (bottom `#445566`, top `#778899`)
- ModelVFX: Custom — thin highlight, fast pulse animation
- Particles: Afterimage ghost particles on dodge (triggered via effect, not constant)
- Sound: Whoosh sound on dodge

**Player Counter**: Ranged weapons. AoE abilities. Spells. Accuracy stat reduces dodge chance.

**Hook Point**: Phase 3 — `AvoidanceProcessor`. Add a mob-side dodge check. Currently avoidance is player-only; this extends it to mobs.

**Edge Cases**:
- 25% dodge against melee feels impactful but not frustrating. At most, 1 in 4 swings misses. Two misses in a row (6.25% chance) is rare enough to not feel bad.
- On a Ranger archetype mob (ranged attacker): the mod protects against melee approach. Thematically weird (a ranged mob dodging melee) but mechanically interesting — it punishes closing distance. Not a dead roll.
- Dodge chance does NOT apply to DoT (ailments bypass it). Confirmed — ailment damage has no avoidance check.

---

### Category D: Behavioral (Tier 2/4)

These change HOW the mob fights. The most memorable modifiers.

---

#### D1. Enraged (Tier 2)

**Mechanic**: Below 40% HP, the mob gains: +50% attack speed (via ApplicationEffects), +30% damage (stat modifier), +10% scale increase (visual intensity). Effect is permanent once triggered — no toggling.

**Visual**:
- Tint: Intensifying red (bottom `#CC0000`, top `#FF2200`) — applied on trigger
- ModelVFX: Custom — aggressive red highlight, pulsing, LoopMirror, fast
- Particles: Red rage particles intensifying around mob
- Sound: Roar/growl sound effect on trigger + aggressive ambient after
- Scale: Increases from base to base+0.1 on trigger (e.g., elite 1.15→1.25)

**Player Counter**: Finish the mob quickly once it enrages. Front-load damage. Or play defensively and respect the increased pressure.

**Hook Point**: ECS tick system checks mob HP each tick. On crossing 40% threshold, applies Enraged EntityEffect (one-time, irreversible).

**Edge Cases**:
- Attack speed via `horizontalSpeedMultiplier` doesn't directly affect attack speed — it's movement speed. True attack speed increase needs combo window manipulation or stat modifier on the mob's attack speed stat. Verify: does our `EntityStatMap` have attack speed? If not, use damage increase as the primary mechanic. Movement speed increase (+30%) as secondary — mob becomes more aggressive in chasing.
- Scale change is visual only (hitbox unchanged). The growth is dramatic and signals "phase 2."
- On a Boss already at 1.3x scale: grows to 1.4x. Intimidating. Good.
- If Fierce (+35% damage) + Enraged (+30% at low HP): total 1.35 × 1.30 = 1.755x damage at low HP. With tier multiplier of 1.1x (Boss): 1.1 × 1.35 × 1.30 = 1.93x. Below old Boss damage of 1.5x in absolute terms, but these are two stacking modifiers on a 2-modifier Boss — it should feel dangerous. Acceptable.

---

#### D2. Regenerating (Tier 3)

**Mechanic**: Heals 2% of max HP per second if the mob has not taken damage in the last 4 seconds. Healing is visible (green particles). Healing stops immediately on taking any damage.

**Visual**:
- Tint: None while dormant. Bright green pulse (bottom `#22CC22`, top `#88FF88`) when healing.
- ModelVFX: Custom — green highlight, only active during healing
- Particles: Green healing motes rising from mob (only during healing)
- Sound: Gentle hum during healing, stops on damage

**Player Counter**: Maintain pressure. Don't disengage. DoT ailments (Burn, Poison) prevent regeneration because they continuously deal damage. Ranged weapons allow healing denial at distance.

**Hook Point**: ECS tick system. Track `lastDamageTimestamp` per mob. If `now - lastDamageTimestamp > 4.0s`, heal 2% per tick. Apply/remove healing EntityEffect for visuals.

**Edge Cases**:
- 2% per second = full heal in 50 seconds of no combat. Players would need to fully disengage for almost a minute for a full reset. Reasonable — this is a "don't AFK mid-fight" mechanic.
- DoT prevents regen: Burn/Poison deals damage each tick → resets the 4s timer. This is intentional and creates build synergy (ailment builds naturally counter Regenerating).
- Vigorous + Regenerating on a boss: large HP pool + regen. Without DoT, this is a sustained DPS check. With DoT, it's trivialized. Good balance — rewards prepared players.

---

#### D3. Shadow Step (Tier 4)

**Mechanic**: When hit from the front (attacker facing same direction as mob), the mob teleports behind the attacker. 8-second cooldown. Brief smoke particle effect at origin, reappear behind with a short attack window.

**Visual**:
- Tint: Dark purple-black gradient (bottom `#1A0033`, top `#330066`)
- ModelVFX: `Portal_Teleport` (vanilla — bright cyan flash on teleport)
- Particles: Smoke puff at origin. Void wisps at destination.
- Sound: Whoosh on teleport + subtle reappearance sound behind player

**Player Counter**: Attack from the side or behind. Circle-strafe. In 3D Hytale combat, positioning is real — this rewards players who don't just face-tank. Alternatively, after the teleport, quickly turn around.

**Hook Point**: `BodyMotionTeleport` with positioning set to BEHIND. Triggered by combat event system — on receiving damage, check if attacker is within frontal arc. If yes and cooldown is ready, execute teleport.

**Edge Cases**:
- "Front" detection: Define as within a ~90° cone of the mob's facing direction. Uses dot product of attacker→mob direction vs mob facing. Standard technique.
- 8-second cooldown prevents teleport spam. On the fastest attack speed, a player might get 8-12 hits between teleports. Enough to deal meaningful damage.
- In multiplayer: mob teleports behind the player who hit it from the front. Other players still see it move. Creates dynamic positioning in group fights.
- On a ranged mob (Ranger archetype): teleporting behind a player to melee is unusual for a ranged mob. Thematically weird but mechanically interesting — forces melee range on a ranged enemy. "Fully random" means we accept this. It's fun.
- Does BodyMotionTeleport interrupt the mob's current action? Need to verify. If it does, the mob might cancel an attack to teleport, making it less threatening. If it doesn't, the mob attacks THEN teleports, which could look buggy. Testing required.

---

#### D4. Summoner (Tier 4)

**Mechanic**: At 60% and 30% HP thresholds, spawns 2 allied mobs of the same type as the elite/boss (Normal tier, no modifiers, scaled to -5 levels below the summoner). Maximum 4 summoned at once. Summoned mobs despawn when the summoner dies.

**Visual**:
- Tint: Sickly yellow-green gradient (bottom `#888822`, top `#CCCC44`)
- ModelVFX: Custom — brief summoning flash at spawn locations
- Particles: Summoning circle particles at spawn points (ground level, ~3 block radius)
- Sound: Horn/call sound on summon

**Player Counter**: Kill the summoner quickly (race to 30% before adds overwhelm). Focus the summoner, not the adds (they despawn on death). AoE builds shine here. In multiplayer, one player tanks the summoner while others handle adds.

**Hook Point**: ECS tick system monitors summoner HP. On crossing 60%/30% thresholds, `NPCPlugin.spawnNPC()` at offset positions around the summoner. Track summoned entity refs in a component. On summoner death event, despawn all tracked entities.

**Edge Cases**:
- Summoned mobs are Normal tier, no modifiers, -5 levels. They're fodder — the point is crowd control pressure, not additional elite fights.
- Max 4 summoned prevents infinite army. If 2 are alive at the 30% threshold, only 2 more spawn.
- On a Boss already fighting in an arena: adds add chaos. On a solo player: potentially overwhelming. Mitigation: -5 levels means they're much weaker. A level 50 player vs level 45 adds should handle them quickly.
- Summoned mobs drop NO loot and give NO XP. They're ephemeral. This prevents exploiting the summoner for infinite farming.
- If summoner is Regenerating too: could cycle between thresholds, re-summoning. Mitigation: each threshold triggers only ONCE (tracked per-entity). Cannot re-trigger.

---

### Category E: Aura/Area (Tier 3/4)

These affect the space around the mob and change how you approach the pack.

---

#### E1. Pack Leader (Tier 3)

**Mechanic**: All mobs within 12 blocks of the same faction gain +20% damage and +15% movement speed. The buff is an aura — it applies while in range and fades when the leader dies or they leave range. Only affects non-Elite, non-Boss mobs (prevents elite-buffing-elite chains).

**Visual**:
- Tint: Gold gradient (bottom `#AA8800`, top `#FFCC44`)
- ModelVFX: `Drop_Rare` (vanilla — rarity highlight, golden)
- Particles: Crown-like particles above mob + faint golden lines connecting to buffed mobs (if technically feasible — otherwise just the crown particles)
- Sound: Commanding presence ambient (drum-like low pulse)

**Player Counter**: Kill the leader first. Prioritize target. In a pack of 6 mobs, the Pack Leader is the one with gold particles. Pull it away from the pack. Use CC on the pack while focusing the leader.

**Hook Point**: ECS tick system. Each tick, find all non-elite/non-boss entities within 12 blocks of same faction. Apply temporary stat modifiers (+20% damage, +15% speed). On leader death, remove all applied modifiers.

**Edge Cases**:
- "Same faction" detection: Use Hytale's NPCGroup or faction membership. Need to verify we can query this. Fallback: same mob type (role name prefix match).
- Buffed mob lines (connecting particles): Likely too expensive/complex for v1. Crown particles on the leader + gold tint on buffed mobs is sufficient.
- Multiple Pack Leaders: Each buffs independently. Buffs stack additively (+40% damage if 2 leaders). Rare but powerful. Acceptable.
- In realms where all mobs are hostile: "same faction" might mean ALL mobs. Need faction-based filtering. If no faction data available, fallback to "same NPCGroup."

---

#### E2. Frost Aura (Tier 3)

**Mechanic**: Slows all players within 6 blocks by 20% movement speed. Continuously applies while in range. Does NOT stack with other Frost Aura mobs (same effect ID on player, IGNORE overlap).

**Visual**:
- Tint: Ice blue gradient (bottom `#3388BB`, top `#AADDEE`)
- ModelVFX: Custom — cold highlight, frost noise overlay
- Particles: Cold mist cloud at ground level around mob, frost crystal particles
- Sound: Icy wind ambient, intensifies when player is within aura

**Player Counter**: Stay at range (>6 blocks). Ranged weapons negate the aura entirely. Melee players need movement speed gear, slow resistance, or acceptance. Fire damage melts the aura thematically but doesn't mechanically counter it.

**Hook Point**: ECS tick system. Each tick, find all players within 6 blocks. Apply temporary EntityEffect with `horizontalSpeedMultiplier: 0.8`. Effect expires in 0.6s (re-applied each tick while in range). On mob death, effect naturally expires.

**Edge Cases**:
- 20% slow + Frozen modifier's 15% aura: These are different effects with different IDs. Both apply. Player gets 0.8 × 0.85 = 0.68x speed (32% slow). Significant but not immobilizing. Acceptable — it's a 2-modifier boss at that point.
- Player with Freeze resistance: our freeze resist reduces Freeze AILMENT, not Frost Aura slow. These are different systems. Could add an "aura resistance" stat later if needed.
- Performance: 0.5s tick interval, range check on all players. With 1-4 players and ~5 potential aura mobs in a realm, this is negligible.

---

#### E3. Rallying (Tier 4)

**Mechanic**: On death, all allies within 15 blocks gain +30% max HP (healed to the new max) and +25% damage for 20 seconds. Visual buff on empowered mobs. Does NOT affect other Elites or Bosses.

**Visual**:
- Tint: Blood red gradient (bottom `#660000`, top `#AA2222`)
- ModelVFX: Custom — red empowerment pulse on death burst
- Particles: Death burst particles (expanding ring) + red empowerment particles on buffed mobs
- Sound: War cry sound on death + battle drum ambient on empowered mobs

**Player Counter**: Kill the Rallying mob LAST (so nothing gets buffed). Or kill it FIRST when the pack is small (fewer mobs to buff). Or kill all mobs simultaneously. Creates real tactical decision-making.

**Hook Point**: On-death event for the Rallying mob. Find all non-elite/non-boss entities within 15 blocks. Apply 20-second EntityEffect with stat modifiers. Effect is temporary — no permanent buff.

**Edge Cases**:
- Kill order matters: This is the design intent. "Do I kill the dangerous one first and buff everything else, or save it for last?" This is the kind of tactical thinking the system should create.
- If all nearby mobs are already dead: Rallying triggers but affects nobody. Effectively a dead roll. But the player earned that by killing the pack first. Rewarding play, not punishing it.
- Rallying mob that's also Volatile: Die → buff allies AND explode. Double death effect. Dramatic and dangerous. Acceptable — that's a Tier 4 + Tier 4 combo that would only appear on bosses.

---

### Category F: Death Trigger (Tier 4)

These fire when the mob dies. They teach players that the fight isn't over until the effects clear.

---

#### F1. Volatile

**Mechanic**: On death, the mob's body glows and charges for 2.0 seconds, then explodes dealing 80% of its base damage as Fire damage in a 5-block radius. The body remains visible and glowing during the charge. After explosion, leaves a brief burning ground (particles, 3s, small DoT).

**Visual**:
- Tint: Bright orange pulsing (intensifying over the 2s charge)
- ModelVFX: Custom — bright orange highlight, BottomUp direction, accelerating pulse, bloom
- Particles: Expanding fire particles during charge, large explosion burst on detonation
- Sound: Rising high-pitched charging sound → explosion boom

**Player Counter**: Move away after the kill. The 2-second delay is generous — always escapable if you're paying attention. Audio cue starts immediately on kill. Visual cue is unmistakable (glowing bright orange). First time you die to it, you learn. Second time, you're already dodging.

**Hook Point**: On-death event. Instead of normal death, enter 2s "volatile death" state (mob rendered as dying/collapsed but not despawned). After 2s, deal AoE damage at position, spawn explosion particles, then despawn.

**Edge Cases**:
- 2.0-second delay is critical for fairness. Testing may reveal this should be 1.5s or 2.5s. Tune based on playtest.
- 80% base damage as Fire = significant but not one-shot (base damage × 0.80, before player fire resistance). At endgame with decent fire resist, survivable.
- XP loss on death to Volatile feels particularly bad because you already "won" the fight. The 2s telegraph + clear audio mitigates this. If still too punishing, reduce damage to 60%.
- Multiple Volatile mobs dying simultaneously: multiple explosion zones. Dangerous but each has its own visual/audio. Acceptable.
- In multiplayer: the killer might dodge but their teammates might not realize. The audio cue broadcasting to all nearby players helps.

---

#### F2. (Moved) — Rallying

Rallying was listed in Category E but is also a death trigger. It lives in Category E because its primary identity is "affects the space around the mob" and the death is just the trigger condition.

**Note**: Venomous (B4) also has a death component (poison cloud). Death triggers can be standalone (Volatile) or embedded in other modifiers.

---

## 5. Modifier Rolling

### Roll Process

When a mob spawns as Elite or Boss:

```
1. Determine modifier count: Elite=1, Boss=2, Elite Boss=3
2. Determine available tier: based on mob level (Level 1-9 = Tier 1 only, etc.)
3. Roll modifier from available pool (all tiers ≤ mob's tier, weighted equally)
4. Check exclusion list — if this modifier + existing modifiers is an excluded combo, reroll (max 3 attempts)
5. Apply modifier: add EntityEffect, modify stats, set nameplate text
6. Repeat for each modifier slot
```

### Exclusion List (Dead Rolls + Degenerate Combos)

| Combo | Reason |
|-------|--------|
| Hardened + Warding (on same mob) | No damage type works well — anti-fun |
| Evasive + Frost Aura (on same mob) | Can't approach (slowed) + can't hit (dodged) — frustrating |
| Duplicate modifier | Same modifier twice is a dead roll |

**Note**: The exclusion list is deliberately SHORT. Most combos are allowed, even tough ones. The philosophy is "fully random, let's have fun." Only combos that are genuinely anti-fun (no viable counter) or meaningless (duplicates) are excluded.

### Weight System

All modifiers in the available pool have equal weight for v1. If playtesting reveals certain modifiers are too common or too rare, introduce per-modifier weights later.

---

## 6. Reward Integration

### Per-Modifier Loot Scaling

Each modifier on a mob adds to its loot bonus. This replaces the flat tier-based IIQ/IIR:

| Modifier Count | IIQ Bonus | IIR Bonus | XP Bonus |
|----------------|-----------|-----------|----------|
| 0 (normal) | 1.0x | 1.0x | 1.0x |
| 1 (elite) | 2.0x | 1.5x | 3.0x |
| 2 (boss) | 4.0x | 3.0x | 10.0x |
| 3 (elite boss) | 7.0x | 5.0x | 15.0x |

The scaling is **super-linear** — 3 modifiers isn't 3x the reward of 1, it's 3.5x. This incentivizes engaging the hardest content.

### Hidden Rewards

No modifier-specific drops in v1. All modifiers feed into the same loot table with increased quantity and rarity. Players don't know what will drop — they just know it'll be better.

**Future (v2)**: Consider modifier-themed crafting materials (Ember Core from Blazing, Storm Shard from Thunderous) for targeted farming. But v1 ships with hidden rewards only.

---

## 7. Edge Cases & Failure Modes

### Player Death Scenarios

| Scenario | Fairness Assessment | Mitigation |
|----------|--------------------|-----------|
| Die to Volatile explosion | Fair — 2s telegraph, audio cue | Clear visual + sound. Reduce damage if too punishing. |
| Die to Fierce + Enraged burst | Fair — Enraged is visible (red glow, roar) | Kill before 40% HP or play defensive. |
| Die to Shadow Step ambush | Mostly fair — teleport is visible | 8s cooldown means you can recover between teleports. |
| Die to Venomous poison cloud after kill | Fair — cloud is visible | Move away from corpse. |
| Die to Reflective while attacking | Fair — capped at 20% HP floor | Player can't be killed by reflection alone. Must take other damage. |
| Die to Frost Aura + melee mob | Fair — stay at range | Rewards build diversity. |

### Performance Scenarios

| Scenario | Concern | Budget |
|----------|---------|--------|
| 5 elites on screen with particles | Medium | ~15 emitters, well within 50 limit |
| Pack Leader buffing 10 mobs | Low | 10 stat modifier applications per tick (0.5s interval) |
| Frost Aura range check every tick | Low | 1 mob × 4 players × distance check = negligible |
| Summoner spawning 4 adds | Medium | 4 full entity spawns. One-time cost at HP threshold. |

### Multiplayer Edge Cases

| Scenario | Handling |
|----------|---------|
| Player A triggers Enraged, Player B is fighting | Both players see the visual. Enrage is mob-state, not player-specific. |
| Player A kills Volatile mob, Player B is nearby | Both get the audio cue. Both see the visual. Explosion damages all players in radius. |
| Shadow Step targets Player A, Player B is also attacking | Teleports behind whoever triggered it (frontal hit). Other player sees the teleport. |
| One player kites while another DPSes a Regenerating mob | Regen only triggers if NO damage taken for 4s. As long as either player hits, no regen. Good multiplayer synergy. |

---

## 8. Technical Architecture (High-Level)

### New Components & Systems

| Component/System | Purpose |
|-----------------|---------|
| `MobModifierComponent` | ECS component storing active modifiers on a mob. Fields: `List<MobModifier>`, `modifierEffectIds[]`, `spawnThresholdFlags` (for Summoner). Attached at spawn time alongside `MobScalingComponent`. |
| `MobModifierRoller` | Rolls modifiers at spawn: determines count from tier, selects from level-appropriate pool, checks exclusions, returns `List<MobModifier>`. |
| `MobModifierApplier` | Applies modifiers to a mob: creates EntityEffects (visual), modifies EntityStatMap (stats), sets Nameplate text, applies ApplicationEffects. |
| `MobModifierTickSystem` | ECS tick system for behavioral modifiers: checks HP for Enraged/Summoner thresholds, manages Regenerating heal timer, manages Frost Aura/Pack Leader range checks. |
| `MobModifierCombatHooks` | Integrates with RPGDamageCalculator: elemental damage addition (B1-B4), reflection (C1), evasion (C3), death triggers (F1). |
| `MobModifierDeathHandler` | Handles death events for modified mobs: Volatile explosion, Venomous cloud, Rallying buff, Summoner despawn. |

### Integration Points

```
Spawn → MobScalingSystem.onEntityAdd()
  └→ MobModifierRoller.roll(level, tier)
  └→ MobModifierApplier.apply(entity, modifiers)
      ├→ EffectControllerComponent.addEffect() × N (visual per modifier)
      ├→ EntityStatMap.addModifier() × N (stat changes)
      ├→ Nameplate.setText() (modifier name format)
      └→ MobModifierComponent attached to entity

Combat → RPGDamageSystem
  └→ MobModifierCombatHooks.preCalculate() (elemental damage addition)
  └→ MobModifierCombatHooks.postAvoidance() (Evasive dodge check)
  └→ MobModifierCombatHooks.postDamage() (Reflective, ailment chance boost)

Tick → MobModifierTickSystem (every 0.5s)
  └→ Check Enraged threshold
  └→ Check Summoner threshold
  └→ Manage Regenerating timer
  └→ Apply/remove Frost Aura + Pack Leader buffs

Death → MobModifierDeathHandler
  └→ Volatile: schedule explosion
  └→ Venomous: spawn poison cloud
  └→ Rallying: buff nearby mobs
  └→ Summoner: despawn tracked minions

Loot → LootCalculator
  └→ Read modifier count from MobModifierComponent
  └→ Apply per-modifier IIQ/IIR bonuses
```

### Config Structure

New config file: `config/mob-modifiers.yml`

```yaml
# Master switch
enabled: true

# Modifier count per tier
modifier_count:
  elite: 1
  boss: 2
  elite_boss: 3

# Level gating
level_gates:
  tier_1: 1    # Stat modifiers
  tier_2: 10   # Elemental + Enraged
  tier_3: 25   # Tactical + behavioral
  tier_4: 40   # Complex + death

# Per-modifier reward scaling
reward_scaling:
  modifiers_1: { iiq: 2.0, iir: 1.5 }
  modifiers_2: { iiq: 4.0, iir: 3.0 }
  modifiers_3: { iiq: 7.0, iir: 5.0 }

# Exclusion rules (pairs that can't coexist)
exclusions:
  - [hardened, warding]
  - [evasive, frost_aura]

# Individual modifier configs
modifiers:
  hardened:
    tier: 1
    category: stat
    armor_bonus: 0.50
    tint_bottom: "#8B7355"
    tint_top: "#A0926B"
    model_vfx: "Stoneskin"
    # ... (full config per modifier)
```

---

## 9. Playtesting Plan

### Phase 1: Core Validation (5 modifiers)
Implement Tier 1 only: Hardened, Vigorous, Fierce, Swift, Resolute.
- Verify visual identification at distance
- Verify stat changes feel meaningful without being spongy
- Verify loot scaling feels rewarding
- Verify new tier multipliers (lower stats) feel right

### Phase 2: Elemental + First Behavioral (5 more)
Add: Blazing, Frozen, Thunderous, Venomous, Enraged.
- Verify elemental damage + ailment application
- Verify Enraged HP threshold trigger
- Verify fire trail / frost aura / lightning telegraph feel fair
- Verify death cloud (Venomous) is readable

### Phase 3: Tactical + Complex (6 more)
Add: Evasive, Warding, Regenerating, Pack Leader, Frost Aura, Reflective.
- Verify counter-strategies work (physical vs Warding, elemental vs Hardened)
- Verify aura range checks perform well
- Verify Reflective cap prevents frustration
- Verify Pack Leader kill-order creates interesting decisions

### Phase 4: Endgame (5 more)
Add: Shadow Step, Summoner, Volatile, Rallying, (Resolute already in Phase 1).
- Verify Shadow Step teleport feels fair (8s cooldown, frontal arc detection)
- Verify Summoner spawns don't overwhelm (performance + difficulty)
- Verify Volatile 2s telegraph is enough time
- Verify Rallying creates meaningful kill-order decisions

### Phase 5: Multi-Modifier Balance
Enable 2-modifier bosses and 3-modifier elite bosses.
- Test every modifier combination (21×20 = 420 pairs, but prioritize the 3 excluded combos and the "scary" combos)
- Verify visual stacking reads well
- Verify difficulty curve is appropriate
- Verify reward scaling incentivizes engagement

---

## 10. Summary

| Aspect | Decision |
|--------|----------|
| **Modifier count** | Elite: 1, Boss: 2, Elite Boss: 3 |
| **Pool size** | 21 modifiers across 6 categories |
| **Randomness** | Fully random (any mod on any mob) |
| **Level gating** | 4 tiers (Lv1+, 10+, 25+, 40+) |
| **Stat rebalance** | Minimal inflation (Elite 1.2x HP, Boss 2.0x, Elite Boss 3.0x) — modifiers ARE the challenge |
| **Where** | Everywhere (overworld + realms, same system) |
| **Rewards** | Hidden + difficulty-scaled (per-modifier IIQ/IIR) |
| **Visual language** | Tint + ModelVFX + Particles + Sound + Scale (NOT nameplate-first) |
| **Exclusions** | 2 banned combos + no duplicates |
| **Combat depth** | All three: stat, stat+behavior, full overhaul |
