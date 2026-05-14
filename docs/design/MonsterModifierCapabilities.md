# Monster Modifier Capabilities — What Hytale's Engine Allows

> **Purpose**: Documents what is confirmed possible, confirmed impossible, and uncertain for implementing monster modifiers in Hytale. This determines the design space.
>
> **Date**: 2026-05-10
> **Status**: Research complete
> **Sources**: Decompiled Hytale server (5,218 classes), 50 vendor mods, our plugin (730 files), client data

---

## 1. Visual Differentiation

How can we make a modified mob LOOK different from a normal mob?

### Confirmed Possible

| Capability | API | Notes |
|-----------|-----|-------|
| **Entity scaling** | `EntityScaleComponent.setScale(float)` | Per-entity, runtime, networked. Default 1.0. Elite at 1.2 = immediately noticeable. |
| **Entity tinting** | `ApplicationEffects.entityBottomTint/entityTopTint` | Bottom-to-top gradient. ARGB color. Red tint for enraged, blue for frost, etc. |
| **Particle effects** | `ApplicationEffects.particles[]` (ModelParticle array) | Attached to entity. Fire particles, frost crystals, lightning sparks. Visible within 75 blocks. |
| **ModelVFX shaders** | `ApplicationEffects.modelVFXId` | Highlight color + bloom, noise overlay, dissolve/pulse animations. The richest visual option. |
| **Model override** | `EntityEffect.modelOverride/modelChange` | Swap entire model or texture at runtime. Heavy — better for bosses than elites. |
| **Status effect icon** | `EntityEffect.statusEffectIcon` | 2D icon rendered in UI. Shows in status panels. |
| **Sound effects** | `ApplicationEffects.soundEventIdWorld/Local` | World-audible (all nearby players) or local (affected player only). Mono required for world. |
| **Animation override** | `ApplicationEffects.entityAnimationId` | Play custom animation on entity. |
| **Speed multiplier** | `ApplicationEffects.horizontalSpeedMultiplier` | Movement speed visual + gameplay. 1.5 = 50% faster movement. |
| **Knockback multiplier** | `ApplicationEffects.knockbackMultiplier` | Affects how much KB the entity receives. 0.0 = immovable. |

### Already Built (Our Plugin)

| Wrapper | File | What It Does |
|---------|------|-------------|
| `RPGApplicationEffects` | `mobs/speed/RPGApplicationEffects.java` | Full builder: `.withTint()`, `.withParticles()`, `.withModelVFX()`, `.colorFromHex()` |
| `RPGEntityEffect` | `mobs/speed/RPGEntityEffect.java` | Creates infinite-duration EntityEffects with any ApplicationEffects |
| `MobSpeedEffectManager` | `mobs/speed/MobSpeedEffectManager.java` | Applies speed effects to mobs via `EffectControllerComponent` |

### Confirmed NOT Possible

| Capability | Why |
|-----------|-----|
| **Nameplate colors** | `Nameplate` component is plain text only — no color, no formatting |
| **Glow outlines** | No built-in outline system. ModelVFX highlight is the closest (shader-based) |
| **Floating debuff icons** | Only CombatText (numbers) and EntityStat (bars) exist as entity UI types |
| **World-positioned custom UI** | HyUI is player-attached, not entity-attached. Would need entity-tracking refresh loop |
| **Multiple health bars** | EntityStatUIComponent is one-per-entity-type |
| **Emissive maps** | Not directly supported |
| **Dynamic mesh deformation** | Scale is the only mesh-level change |

### Key Visual Design Implications

1. **The nameplate IS customizable as text** — `Nameplate.setText("★ Blazing Trork Captain ★")` works. Just no colors.
2. **Tint + particles + ModelVFX is the main visual language**. These stack and are already wrapped in our codebase.
3. **Scale is the simplest tier indicator** — 1.0 normal, 1.15 elite, 1.3 boss. Immediately readable in 3D.
4. **Sound is critical for danger warning** — especially for on-death effects or charging attacks. We can play per-entity sounds.
5. **DisplayNameComponent** (separate from Nameplate) supports Message format with colors — used in death messages, chat. "You were killed by **Blazing Trork Captain**" can be colored.

---

## 2. Mob Behavior & AI

What can we make modified mobs DO differently?

### The AI Architecture

Hytale does NOT use behavior trees. It uses an **action-based system**:

```
Role (asset blueprint) → CombatActionEvaluator (tick-based decision maker) → Actions (concrete behaviors)
```

- `CombatActionEvaluator` evaluates conditions each tick and selects combat actions
- `CombatActionOption` defines a specific action with conditions, cooldowns, positioning
- Conditions include: `RecentSustainedDamageCondition`, `TotalSustainedDamageCondition`, `TargetMemoryCountCondition`

### Confirmed Mob Behaviors

| Behavior | API | Notes |
|----------|-----|-------|
| **Use abilities/spells** | `AbilityCombatAction` with `RootInteraction` | NPCs can use ANY configured interaction. Primary/secondary attacks, abilities. |
| **Teleport/dash** | `BodyMotionTeleport` | Instant teleport with max distance, Y offset, sector angle. 0.5s cooldown. |
| **Spawn minions** | `NPCPlugin.spawnNPC()` or `SpawnNPCInteraction` | Full entity creation at position. Can specify role, stats. |
| **Fire projectiles** | `AbilityCombatAction` with ballistic mode | Ranged attacks via interactions. Our `ProjectileSystem` already handles projectile damage. |
| **Apply self-buffs** | `ActionApplyEntityEffect` | NPC applies an EntityEffect to itself (speed, tint, particles, stats). |
| **Apply debuffs to target** | `ActionApplyEntityEffect` on target | Apply effects to the player being fought. |
| **Change stats at runtime** | `EntityStatMap.addModifier()` | Add/remove stat modifiers dynamically. |
| **Speed changes** | `RPGEntityEffect` + `EffectControllerComponent` | Our system already does this. 0.5x to 2.0x confirmed working. |
| **Positioning** | `AbilityCombatAction.Positioning` enum | NPCs can position to FRONT, BEHIND, or FLANK of target before attacking. |
| **Track damage memory** | `TargetMemory` component | NPCs remember who damaged them and how much. |
| **Combo chains** | Action system | NPCs can execute sequential attack combos. |
| **Pathfinding to target** | `BodyMotionFind` | Standard chase behavior. |
| **Maintain distance** | `BodyMotionMaintainDistance` | Kiting/ranged behavior. |

### Confirmed NOT Possible

| Behavior | Why | Workaround |
|----------|-----|-----------|
| **Direct velocity/impulse** | No API for adding force vectors to NPCs | Use teleport or pathfinding |
| **Custom behavior trees** | No scripting language, all asset-based | Use CombatActionEvaluator conditions or ECS systems |
| **Direct attack speed control** | Client gates input timing | Combo window manipulation via action system |
| **Block destruction by mobs** | No native mob block-breaking API | Could use `world.setBlock()` from server, but risky |

### Key Behavior Design Implications

1. **Teleport is our "charge" mechanic** — BodyMotionTeleport with max distance = a dash/charge. Position behind target = an ambush.
2. **Minion spawning is fully supported** — NPCs can summon other NPCs mid-fight. Full entities with their own AI.
3. **Self-buff on low HP = Enrage** — Use ECS tick system to check mob HP, apply speed/damage EntityEffect when below threshold.
4. **We don't need custom AI** — Most modifier behaviors can be implemented as ECS systems that check mob state each tick and apply effects, rather than modifying the mob's AI directly.

---

## 3. Nameplate & Display

How do players identify what they're fighting?

### What We Can Set

| Display | API | Format | Colors? |
|---------|-----|--------|---------|
| **Floating nameplate** | `Nameplate.setText(String)` | Plain text | NO |
| **Display name** (death msgs, chat) | `DisplayNameComponent.setDisplayName(Message)` | Rich Message | YES — color, bold, italic |
| **Health bar** | `EntityStatUIComponent` | Progress bar | Asset-defined |
| **Damage numbers** | `CombatTextUpdate` via damage JSON assets | Float number + color | YES — per damage type |

### Creative Display Options

1. **Unicode symbols in nameplate text**: `setText("★ Blazing Trork Captain ★")` — stars, element symbols, skull icons
2. **Modifier keywords in name**: `setText("[Blazing] Trork Captain")` or `setText("Trork Captain the Armored")`
3. **Tier prefix**: `setText("◆ Elite ◆ Trork Captain")` vs `setText("◇◇ Boss ◇◇ Dragon")`
4. **Level display**: `setText("Lv.42 ★ Blazing Trork Captain")`

### How Players Will Actually Read Modifiers

Since nameplates can't have colors, the visual identification stack is:

```
DISTANCE (50+ blocks): Scale difference + particle glow → "That's not a normal mob"
APPROACH (20-50 blocks): Nameplate text visible → "It's a Blazing Elite"
COMBAT (0-20 blocks): Tint + particles + ModelVFX + sound → "Fire attacks, burning trail"
DEATH: DisplayName in death message with colors → "You were slain by ★ Blazing Trork Captain ★"
```

---

## 4. Combat Pipeline Hooks

Where can modifier behaviors inject into our damage system?

### The 7-Phase Pipeline

```
Phase 1: Context Resolution (attacker/defender stats, attack type)
Phase 2: 10-Step Damage Calculation (flat → conversion → % → more → conditional → crit → defense → true)
Phase 3: Avoidance (dodge → evasion → block → parry)
Phase 4.5: Death Recap Trace
Phase 5: Post-Calc Modifications (blocking, energy shield, MoM, shock amp, vulnerability)
Phase 6: Combat Feedback (ailments, triggers, effects, indicators)
Phase 7: Recovery (leech, steal, thorns, block heal)
```

### Hook Points for Modifiers

| Hook | Phase | Best For | Example Modifier |
|------|-------|----------|-----------------|
| **ConditionalMultiplierCalculator** | 2 (Step 7) | Damage scaling based on state | "Enraged" (+50% damage below 40% HP) |
| **DamageModifierProcessor** | 5 | Post-defense damage modification | "Armored" (extra DR), "Reflective" (return damage) |
| **AvoidanceProcessor** | 3 | Dodge/block modification | "Evasive" (+dodge chance), "Fortified" (can't be staggered) |
| **CombatAilmentApplicator** | 6 | Status effect application | "Venomous" (poison on hit), "Blazing" (burn on hit) |
| **CombatTriggerHandler** | 6 | On-crit, on-kill, when-hit events | "Undying" (revive on death), "Rallying" (buff allies on death) |
| **CombatEffectRegistry** | 6 | Custom on-hit/on-kill actions | "Summoner" (spawn minion on kill threshold) |
| **CombatRecoveryProcessor** | 7 | Leech, thorns, healing | "Vampiric" (lifesteal), "Thorned" (reflect damage) |
| **EntityStatMap.addModifier()** | Pre-combat | Persistent stat changes | "Armored" (+50% armor), "Swift" (+40% speed) |

### Key Pipeline Design Implications

1. **Most modifiers are just stat additions** — "Armored" = add armor modifier. "Swift" = add speed modifier. No code change per modifier, just config.
2. **Conditional modifiers use existing hooks** — ConditionalMultiplierCalculator already checks HP thresholds, ailment states.
3. **On-death effects hook into CombatTriggerHandler** — ON_KILL trigger fires on lethal damage.
4. **Ailment-adding modifiers hook into CombatAilmentApplicator** — can modify ailment chance, type, or magnitude.
5. **Damage reflection is already architectured** — Thorns system in Phase 7 does exactly this.

---

## 5. What Vendor Mods Do

### EndgameAndQoL (Most Relevant)
- `GenericBossDamageSystem`: Boss health tracking via EntityStatMap, enrage timer, weapon-specific weaknesses
- `BossBarHtmlBuilder`: Custom boss bar UI
- Pattern: Component-based state tracking → effect application → UI feedback

### EndlessLeveling
- `MobAugmentDiagnostics`: Mob augment system (modifier-like)
- Pattern: Augments applied as stat modifiers to mobs

### AuraMagic
- Shield mechanic via custom component + EntityEffects

### Common Pattern Across All
1. Custom ECS component stores modifier state
2. `EffectControllerComponent` applies visual + gameplay effects
3. `EntityStatMap` modifiers for stat changes
4. ECS systems tick to check conditions and apply effects

---

## 6. Capability Matrix — At a Glance

### Visual
| Feature | Status | Confidence |
|---------|--------|-----------|
| Scale mobs | ✅ | HIGH — EntityScaleComponent |
| Tint mobs | ✅ | HIGH — ApplicationEffects tint |
| Particle effects on mobs | ✅ | HIGH — ModelParticle array |
| Shader effects (glow, pulse) | ✅ | HIGH — ModelVFX system |
| Custom nameplate text | ✅ | HIGH — Nameplate.setText() |
| Nameplate colors | ❌ | HIGH — confirmed text-only |
| Per-entity sound | ✅ | HIGH — soundEventIdWorld |
| Custom health bar | ⚠️ | MEDIUM — EntityStatUIComponent, needs asset |
| Floating icons/text | ❌ | HIGH — only CombatText numbers |

### Behavior
| Feature | Status | Confidence |
|---------|--------|-----------|
| Teleport/charge | ✅ | HIGH — BodyMotionTeleport |
| Spawn minions | ✅ | HIGH — NPCPlugin.spawnNPC() |
| Use abilities | ✅ | HIGH — AbilityCombatAction |
| Speed changes | ✅ | HIGH — our RPGEntityEffect |
| Self-buff at HP threshold | ✅ | HIGH — ECS system + EntityEffect |
| Projectile attacks | ✅ | HIGH — RootInteraction + ballistic |
| Direct velocity/knockback charge | ❌ | HIGH — no API |
| Block destruction | ⚠️ | LOW — possible but risky |

### Combat
| Feature | Status | Confidence |
|---------|--------|-----------|
| Stat-based modifiers (armor, speed, etc.) | ✅ | HIGH — EntityStatMap |
| Damage reflection | ✅ | HIGH — Thorns in Phase 7 |
| Ailment application on hit | ✅ | HIGH — CombatAilmentApplicator |
| On-death effects | ✅ | HIGH — CombatTriggerHandler |
| Conditional damage scaling | ✅ | HIGH — ConditionalMultiplierCalculator |
| Avoidance modification | ✅ | HIGH — AvoidanceProcessor |
| Custom damage types | ✅ | HIGH — DamageType enum + JSON assets |

---

## 7. The Design Space This Opens

### What's Easy (Stat-based, config-driven)
- Extra HP, damage, armor, speed, evasion, elemental resistance
- Ailment chance on attacks (poison/burn/freeze/shock)
- Damage reflection (thorns)
- Lifesteal
- Knockback resistance/immunity

### What's Moderate (ECS system + effects)
- Enrage at low HP (tick system checks HP, applies buff effect)
- Speed boost/slow aura (apply effects to nearby entities)
- Regeneration (tick system heals if not damaged recently)
- Periodic projectile attacks (timer-based ability trigger)
- Minion summoning at HP thresholds

### What's Complex (Custom systems needed)
- Teleport/charge attacks (BodyMotionTeleport + positioning + telegraph)
- Shield/barrier mechanic (custom stat + visual + break condition)
- Adaptive resistance (track damage types, modify resistances dynamically)
- Death effects (explosion, ground hazard, minion spawn on death)
- Pack leader aura (find nearby same-faction mobs, apply buffs)

### What's Not Worth Pursuing
- Reflect-kills (anti-fun, confirmed by D3's mistake)
- Hard immunities (too punishing without ARPG build diversity)
- Block destruction (griefing risk in multiplayer)
- Custom AI behavior trees (too heavy, action system is sufficient)

---

## 8. Resolved Questions

All questions answered via decompiled code analysis:

### 1. Can multiple EntityEffects stack on one mob? — YES
`EffectControllerComponent` uses `Int2ObjectMap<ActiveEntityEffect>` keyed by effect ID. Different IDs coexist — tint + speed + VFX all run simultaneously. Same ID follows `OverlapBehavior` (EXTEND/IGNORE/OVERWRITE). Stat modifiers and visuals from all active effects apply independently. **One gotcha**: Model changes don't stack (first wins), but we'd use tint+particles+VFX, not model swaps.

### 2. Does EntityScaleComponent affect hitbox? — NO (Visual Only)
Confirmed via Hexcode source (ScaleGlyph.java) and decompiled Hytale physics. `EntityScaleComponent` is sent to client for rendering but the server-side `BoundingBox` component (used for collision) is never modified by scale. Physics providers don't read EntityScaleComponent. Scale is purely cosmetic. This is fine — visual identification is the goal, and consistent hitbox means combat feel doesn't change.

### 3. What ModelVFX assets exist in vanilla? — 33 Assets
Full catalog at `Assets/Server/Entity/ModelVFX/`:

| Relevant for Modifiers | Asset ID | Visual |
|------------------------|----------|--------|
| Armor/shield buff | `Stoneskin` | Damage reduction look |
| Fire ailment | `Burn` | Orange highlight + fire tint |
| Ice ailment | `Freeze` | Blue-white highlight |
| Poison ailment | `Poison` | Green/black highlight |
| Invulnerability | `Intangible` / `Intangible_Dark` | Phase state |
| Teleport | `Portal_Teleport` | Bright cyan |
| Empowered buff | `Trinket_Empowered` | Power glow |
| Rarity glow | `Drop_Legendary/Epic/Rare/Uncommon` | Rarity highlight |
| Death | `Death` | Death visual |

Plus full custom VFX support: HighlightColor, Bloom, Noise, Animation curves, Loop modes. We can create custom ModelVFX JSON assets for modifier-specific visuals.

### 4. Nameplate timing — NOT A CONCERN
We already handle nameplates correctly. Also, nameplates are the least desirable visual approach (basic, ugly). Tint + particles + ModelVFX + scale is the primary visual language.

### 5. Sound from EntityEffect — VERIFY DURING IMPL
`soundEventIdWorld` should broadcast from entity position. Low risk — verify during first implementation.

### 6. Particle performance limit — ~50 EMITTERS
Confirmed ~50 concurrent particle emitters is the practical limit. With 5 elites at 2-3 emitters each = 10-15 total, well within budget.
