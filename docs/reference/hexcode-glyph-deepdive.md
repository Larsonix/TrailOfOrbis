# Hexcode Glyph Deep Dive — Complete Technical Reference

All data from decompiled source code (v0.6.0). Covers exact execute() logic, damage formulas, type system, data flow, equipment stats, and advanced combo knowledge.

**Companion docs**: `hexcode-analysis.md` (architecture), `hexcode-spellcrafting-guide.md` (gameplay overview)

---

## Table of Contents

1. [Type System & Data Flow](#type-system--data-flow)
2. [Per-Glyph Mechanics](#per-glyph-mechanics)
3. [Projectile System](#projectile-system)
4. [Equipment & Stats](#equipment--stats)
5. [Advanced Combo Knowledge](#advanced-combo-knowledge)

---

## Type System & Data Flow

### HexVar Conversion Matrix

Six types with polymorphic conversion. Entry point: `HexVar.convertTo(targetClass, accessor)`.

```
FROM → TO        NumberVar        PositionVar           RotationVar          ColorVar
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
NumberVar        identity         (n, n, n)             (n, n, n)            unsupported
PositionVar      length()         identity              forward()→yaw/pitch  RGBA components
RotationVar      1.0 or 0.0       forward() unit vec    identity             unsupported
EntityVar        1.0 or 0.0       Transform position    HeadRotation         unsupported
BlockVar         1.0              (x+0.5,y+0.5,z+0.5)  (0, 0, 0)            unsupported
ColorVar         luminance*       RGB components         via position          identity
```

*Luminance formula: `0.299×R + 0.587×G + 0.114×B`

**Special: `resolveSelf(partner)` behavior** — used by math glyphs before conversion:
- `EntityVar.resolveSelf(partner)`: if partner is RotationVar → convert to rotation; else → convert to position
- `BlockVar.resolveSelf(partner)`: always → toPosition()
- All others: return identity (no resolution needed)

### Slot Resolution Chain (readSlot)

`Glyph.readSlot(key, hexContext)` — MAX_RESOLVE_DEPTH = 8 (ThreadLocal counter):

```
1. Check depth < 8 (return null if exceeded)
2. Get Slot by key → get first linked glyph ID
3. If no link → resolve asset default:
   a. GlyphAsset.getSlot(key).defaultValue → NumberVar(default)
   b. Java default (caller override)
   c. hexContext.getVariable(DEFAULT_SLOT)
   d. NumberVar(0.0) fallback
4. If link exists → fetch linked Glyph from hex context
5. Get handler → call handler.readValue(linkedGlyph, hexContext)
6. readValue() typically calls readSlot() recursively on ITS inputs
7. Chain terminates at: source glyphs (Number, Self, Variable) or depth limit
```

### Output Propagation

Three output methods:

```java
writeOutput(value, ctx)       // Sets both DEFAULT_SLOT ("0") AND glyph.id
writeDefaultOutput(value, ctx) // Sets DEFAULT_SLOT only
writeSelfOutput(value, ctx)    // Sets glyph.id only
```

Values stored in `HexContext.variables` (HashMap<String, HexVar>). Downstream glyphs read via `readSlot()` which resolves linked glyph IDs through the chain.

**Key**: Variables are NOT auto-propagated to "Next" glyphs. They must be explicitly written and explicitly read through slot links.

### Branching Mechanics

```java
// HexExecuter.continueExecution()
boolean multiBranch = nextGlyphs.size() > 1;
for (String nextNodeId : nextGlyphs) {
    executeNode(nextNodeId, multiBranch ? hexContext.branch() : hexContext);
}
```

`branch()` creates:
- **Copied** (independent): variables HashMap (shallow copy), colors (cloned)
- **Shared** (same reference): root, accessor, hex, volatilityTracker, executionId

Branches are isolated for variables but compete for volatility budget.

### Volatility Cost Formula

```
baseCost     = asset.volatility.getCostForRepeat(repeatCount)
qualityFactor = (1 - glyph.volatility) * 0.5 + 0.5
                // Perfect draw (1.0) → 0.5x cost
                // Worst draw (0.0)   → 1.0x cost
finalCost    = baseCost * qualityFactor * volatilityMultiplier

// Actual drain has randomness for costs > 1.0:
drain = finalCost <= 1.0 ? finalCost : 1.0 + random() * (finalCost - 1.0)
remainingBudget -= drain
```

### Math Glyph Type Coercion

Add/Multiply use the same algorithm:

```java
a = a.resolveSelf(b, accessor);  // EntityVar→Position/Rotation, BlockVar→Position
b = b.resolveSelf(a, accessor);
b = b.convertTo(a.getClass(), accessor);  // Convert b to type of a
// Then dispatch on a's type
```

| Operation | Number+Number | Position+Position | Rotation+Rotation | Color+Color |
|-----------|--------------|-------------------|-------------------|-------------|
| **Add** | a + b | vector add | euler add | RGBA add (alpha clamped 1.0) |
| **Subtract** | a - b | vector sub | euler sub | RGBA sub |
| **Multiply** | a × b | per-component multiply | per-component multiply | RGBA multiply (alpha clamped [0,1]) |
| **Divide** | a / b (zero-safe) | per-component divide | per-component divide | RGBA divide |

Mixed types: `Entity + Number` → Entity resolves to Position → Number converts to Position → vector add.

### Splice Cloning (Interfere/Resonate)

`ConstructSplicer.splice()` mechanics:

```
1. Rewire: Caster's Output glyphs → their Next slots replaced with target's original pending chain
2. Clone: Deep-copy ALL caster glyphs into target hex (Glyph.clone() keeps same UUID)
3. Variables: Merge per policy (PREFER_TARGET or PREFER_CASTER)
4. Donate: Add volatility budget to target tracker
5. Install chain:
   - APPEND_TAIL (Resonate): newChain = originalNext + casterChildren
   - REPLACE (Interfere):    newChain = casterChildren
```

Only **Drain** and **Freeze** implement splice interface. All other constructs: Interfere strips them, Resonate has no effect.

---

## Per-Glyph Mechanics

### Targeting

#### Self
- Reads `hexContext.getCasterRef()` → extracts `UUIDComponent` → returns `EntityVar`
- Edge: null casterRef → no output written

#### Beam
- Slots: `SOURCE` (required), `ROTATION` (optional, falls back to SOURCE), `RANGE` (default: 32.0)
- Resolves eye position → direction → raycast via `TargetUtil.getTargetLocation()` + `getTargetEntity()`
- Compares entity hit vs block hit distances → picks closest
- Spawn offset: origin + direction × 1.5
- Output: `EntityVar` (entity hit) or `BlockVar` (block hit)
- Edge: no hits → renders MISS beam, fails

#### Area
- Slots: `CENTER` (required), `RADIUS` (default: 5.0)
- If CENTER is BlockVar → gathers blocks in sphere (skip EMPTY); else → gathers entities (skip caster, need UUIDComponent)
- **Per target**: `hexContext.branch()` → `writeOutput()` → `continueFromSlot(NEXT)`
- Volatility cost: `baseCost × repeatCount × 1.67 × areaScale`
- Edge: 0 targets → single continue without branching

### Attack

#### Bolt
- Slots: `TARGET` (required), `POWER` (default: 5.0)
- **Damage formula**: `power × hexContext.getMagicPowerMultiplier()`
- Damage source: `Damage.EnvironmentSource("hex_bolt")`, cause: "Environment"
- On entity: applies shock effect + damage via `DamageSystems.executeDamage()`
- On block: triggers block's RootInteraction (Use type)
- Output: BlockVar on block hit; none on entity hit

#### Combust
- Slots: `CENTER` (default slot fallback), `RADIUS` (default: 3.0), `MAGNITUDE` (default: 10.0)
- Center offset: +0.1 Y
- **Damage**: magnitude (full, no falloff: 1.0)
- **Knockback**: force = magnitude, velocityY = magnitude × 0.3
- Destroys soft blocks in sphere (`gathering.isSoft()` → set to Empty)
- Applies Burn effect (5.0s, OVERWRITE) to all entities in sphere
- **If magnitude ≥ 15.0**: places Lava_Source at center
- Output: none

#### Shatter
- Slots: `SOURCE`, `DIRECTION`, `COUNT` (default: 5, max: 16), `SPREAD` (default: 30°), `SPEED` (default: 20.0), `GRAVITY` (default: 10.0)
- Spawns COUNT shard entities in a cone distribution
- Per shard: offset 1.0 along direction, model "Shatter" scaled 0.35x, 10min TTL
- **Cone math**: center + ring; ring uses spherical coordinates with even azimuth distribution
- Shards have ProjectileHit/Miss/Bounce interactions (like Projectile)
- Physics: ProjectilePhysicsConfig(gravity, bounces=0)

#### Force
- Slots: `TARGET` (required entity), `DIRECTION` (default: (0,1,0) upward), `MAGNITUDE` (default: 20.0)
- Applies velocity: `direction × magnitude` via `VelocityUtil.applyVelocity(Add)`
- Output: none

#### Gust
- Slots: `CENTER` (required), `RADIUS` (default: 5.0), `MAGNITUDE` (default: 10.0)
- Center offset: +0.1 Y
- **Damage formula**: `2.0 + (magnitude × 0.2)` — linear, no falloff
- **Knockback**: force = magnitude, velocityY = magnitude × 0.3
- Uses ExplosionUtils (same as Combust but no block destruction, no burn)

### Elemental

#### Freeze
- Slots: `TARGET` (required entity), `DURATION` (default: 3.0)
- Applies "Hexcode_Freeze" EntityEffect (OVERWRITE)
- Places "Rock_Ice" block at floor beneath target (records original block)
- Creates FreezeState(frozenBlocks, duration, nextGlyphIds)
- **Spliceable**: fires pending chain on natural expiry via onEnd()
- Edge: block below is EMPTY → skip ice placement

#### Glaciate
- Slots: `TARGET` (required position), `OFFSET` (optional), `DURATION` (default: 5.0)
- Spawns falling ice entity at offset position (default: target + (0, 10, 0))
- Model: "Glaciate_Ice" scaled 2.0x
- Adds GlaciateComponent(damageRadius=2.0, damageMultiplier=1.0)
- Physics via GlaciatePhysicsConfig
- Output: EntityVar (ice entity reference)

#### Ignite
- Slots: `TARGET` (required entity), `DURATION` (default: 5.0)
- Applies "Burn" EntityEffect (OVERWRITE)
- No construct, no output. Continues NEXT immediately.

#### Growth
- Slots: `TARGET` (optional), `AMOUNT` (default: 5.0, clamped [1,20]), `DURATION` (default: 10.0)
- **Entity target**: applies "Hexcode_Growth" effect (duration, OVERWRITE)
- **Block target — farming**: advances growth stages by `max(1, amount/5)`, updates FarmingBlock component
- **Block target — non-farming (bonemeal)**: `max(3, amount×1.5)` attempts, 35% chance each, radius 2, places random vegetation from: `[Plant_Grass_Short, Plant_Grass_Tall, Plant_Flower_Daisy, Plant_Flower_Poppy, Plant_Fern]`

### Defense/Buff

#### Fortify
- Slots: `TARGET` (required), `AMOUNT` (default: 5.0, clamped [1,20]), `DURATION` (default: 20.0)
- **Damage reduction**: `amount × 0.5` (flat reduction)
- **Entity**: applies "Hexcode_Fortify" effect + creates/updates FortifyState construct. Chain deferred to construct expiry.
- **Block**: heals block by `amount × 0.05` via `damageBlock(negative)`. Chain immediate.
- Volatility cost scaled: `baseCost × max(1.0, amount/5.0)`

#### Erode
- Slots: `TARGET` (required), `AMOUNT` (default: 5.0, clamped [1,20]), `DURATION` (default: 100.0)
- **Vulnerability multiplier**: `amount × 0.05`
- **Entity**: applies "Hexcode_Erode" effect + creates/updates ErodeState construct. Chain deferred.
- **Block**: damages by `amount × 0.05`. If health ≤ 0.5 and not destroyed: `makeBlockFragile(duration)`. Chain immediate.

#### Ensnare
- Slots: `TARGET` (required position), `RADIUS` (default: 3.0), `DAMAGE` (default: 8.0), `DURATION` (default: 5.0)
- Iterates cylinder, 50% density (hash-based), ground scan ±3 blocks
- Max 64 spikes, model "Ensnare_Spike" scaled 0.5x
- Creates EnsnareComponent(spikes, duration, damage, cooldown=1.0s)
- Damage via `DamageSystems.executeDamage("Environment")`, single hit per entity per lifetime
- **Volatility**: uses `harshScale()` — linear up to threshold, then exponential penalty:
  - `harshScale(value, ref, threshold, exp)`: if value ≤ threshold → value/ref; else → base + excess × (value/threshold)^exp

### Movement

#### Warp
- Slots: `DESTINATION` (required position), `TARGET` (optional entity)
- If TARGET null → no-op (continues NEXT)
- Volatility scaled by distance: `computeAreaScale(distance)`
- Calls `BlockUtils.moveToDestination(target, dest, world, hexContext)`

#### Swap
- Slots: `A` (required), `B` (required) — entity/block/position
- Volatility scaled by distance between A and B
- Calls `BlockUtils.swapPair(a, b, world, hexContext)`

#### Levitate
- Slots: `TARGET` (optional entity), `INTENSITY` (default: 0.0, clamped [0,10]), `DURATION` (default: 100.0, clamped [1,600])
- **Intensity 0** = full weightlessness: drag set to 50.0 (WEIGHTLESS_DRAG)
- **Intensity > 0** = preserves original drag
- Modifies PhysicsValues(mass, drag, gravityEnabled=true)
- If falling (velocity Y < 0): adds upward counter-velocity
- Creates/updates LevitateState, preserves original physics for restoration

#### Phase
- Slots: `TARGET` (required block), `DURATION` (default: 60.0, clamped [10,200]), `INTENSITY` (default: 5.0, clamped [1,15])
- Checks block quality vs intensity: `quality > intensity` → fail (can't phase tough blocks)
- Saves original block type + rotation → sets block to Empty
- Creates PhaseComponent(phasedBlocks list)
- **On cleanup**: restores blocks + crush damage 4.0 per block-volume to entities in area
- Output: BlockVar(original position)

### Control/Utility

#### Scale
- Slots: `TARGET` (required entity), `MAGNITUDE` (default: 2.0, clamped [0.25, 4.0]), `DURATION` (default: 5.0, min 0.1)
- Multiplies EntityScaleComponent by magnitude (creates if missing)
- Scales BoundingBox by magnitude
- Spawns visual entity mounted to target at offset (0, 2.5, 0)
- **Stacks multiplicatively**: existing 2x + new 2x = 4x total

#### Domain
- Slots: `TARGET` (required position), `MAGNITUDE` (radius, default: 5.0, clamped [2,15]), `DURATION` (default: 10.0), `POWER` (default: 1.0, min 0.1)
- **Upfront mana cost**: `20.0 × (1 + (power-1) × 0.5)`
  - Power 1.0 → 20 mana; Power 2.0 → 30 mana; Power 3.0 → 40 mana
- **Drain per second**: `20.0 × (radius/5.0) × 0.1`
  - Radius 5 → 2.0/sec; Radius 10 → 4.0/sec; Radius 15 → 6.0/sec
- **Trigger cost**: 5.0 mana per entity entry
- Spawns zone entity with DomainZoneComponent + DebugComponent (sphere wireframe)
- Volatility scaled by radius²: `computeAreaScale(radius²)`
- Output: EntityVar (zone entity)

#### Conjure
- Slots: `COORDS_A` (default: (0.5,0.5,0.5)), `COORDS_B` (default: (-0.5,-0.5,-0.5)), `ANCHOR` (required), `DURATION` (default: 5.0), `INTERVAL` (default: -1.0 = disabled)
- Coords can be absolute (PositionVar.isAbsolute) or relative to anchor
- Calculates bounding box: min/max components → center + halfExtents
- Spawns zone with ConjureZoneComponent(halfExtents, interval)
- Hard collision: "Hexcode_Conjure_HardCollision" config
- Volatility scaled by volume
- Output: EntityVar (zone entity)

#### Drain
- Slots: `TARGET` (required entity), `HP` (optional %), `STAMINA` (optional %), `DESTINATION` (optional entity, default: caster), `DURATION` (default: 1.0)
- **Conversion rates**: HP→mana = 1.5, Stamina→mana = 0.6
- **HP drain restriction**: only from SELF (same UUID as caster). Enemy HP drain blocked.
- Stat defaults: if neither HP nor STAMINA provided → Health at 15%
- Total drain: `(drainPercent/100) × stat.max`, capped at `stat.current - 1.0` for health (never kills)
- Creates DrainState(sourceStatIndex, destRef, rate, totalDrain, duration, nextGlyphIds)
- **Spliceable**: fires pending chain on onEnd()

#### Delay
- Slots: `DURATION` (default: 1.0)
- If duration ≤ 0 → instant fire (no construct, continues NEXT immediately)
- If no next links → silent no-op (no entity allocated)
- Creates DelayState(seconds, nextLinks copy, colors)
- Spawns entity with optional "Delay" model

#### Concentration
- No slots read
- **Requires**: caster holding primary item (HexcasterIdleComponent check)
- Spawns visual orb mounted to caster at offset (0, 1.4, 1.2)
- **Budget boost**: `tracker.addBudget(remainingBudget × 0.5)` — adds 50% of REMAINING budget
- Creates ConcentrationState(visualRef)

#### Arc
- Slots: `TARGET` (required entity), `JUMP` (default: 15.0 distance), `DELAY` (default: 0.75 seconds)
- Initializes visited set with caster + origin UUIDs
- Finds first target via `ArcUtils.getNextArcTarget(pos, jumpDist, visited, accessor)`
- If no target in range → render fizzle, fail
- Creates ArcState(glyph, branches=nextLinks, visited, maxJump, delay)
- Construct ticks: execute branch → countdown delay → hop to nearest unvisited → repeat
- Per-hop volatility: scaled by `jumpDistance / 15.0`

### Comparison Glyphs

```java
// Equal — routes execution based on a == b
List<String> next = glyph.getNextLinks();
if (a.equalTo(b)) {
    continueExecution(List.of(next.get(0)), hexContext);   // TRUE → 1st link
} else {
    continueExecution(next.subList(1, next.size()), hexContext); // FALSE → rest
}

// Greater — routes based on a > b; Less — routes based on a < b
// Same pattern using compareTo() > 0 or < 0
```

### Value Glyphs

| Glyph | readValue() returns |
|-------|-------------------|
| Number_N | NumberVar(N) |
| Pi | NumberVar(Math.PI) |
| Variable | hexContext.getVariable(glyph.id) |
| Position | PositionVar from x,y,z slots |
| Rotation | RotationVar from pitch,yaw,roll slots |
| IsHolding | NumberVar(1.0) if caster holds primary, else NumberVar(0.0) |
| Style | ColorVar from r,g,b,a slots (or vector splatting) |
| Output | Sets hex color context; all downstream inherit |
| Debug | Prints mana, volatility, slots, variables to chat |

---

## Projectile System

### Spawn Mechanics (ProjectileGlyph)

- Model: `Glyph_Projectile_Flight`, scale 0.5x, TTL 10 minutes
- Spawn offset: 1.5 blocks along direction from source eye position
- Launch velocity: `direction × speed`
- Physics config: bounciness=0.999, bounceLimit=0.001, terminalVelocityAir=200, sticksVertically=true

### Interaction Wiring

Three interactions registered per projectile:

| Interaction | Handler | Behavior |
|-------------|---------|----------|
| **ProjectileHit** | HexProjectileHitInteraction | Writes EntityVar or BlockVar output → executes next links → **removes projectile** |
| **ProjectileMiss** | HexProjectileMissInteraction | Writes BlockVar output (block collision) → executes next links → **removes projectile** |
| **ProjectileBounce** | HexProjectileBounceInteraction | Renders bounce visual → **keeps projectile alive** → does NOT execute next links |

**Key insight**: Bounce does NOT fire the glyph chain. Only Hit and Miss do. Bounces are purely physical — the projectile keeps bouncing until it hits an entity (Hit) or reaches a final collision (Miss).

This means a "Projectile (bounces: 5) → Bolt" spell fires Bolt **once** on final impact, not per bounce. The bounces just extend the projectile's physical travel path.

### ProjectileState Component

Carried on the projectile entity, stores:
```
hexContext: HexContext (from trigger)
triggeringGlyphId: String
nextLinks: List<String> (copied from glyph)
```

Persists through all bounces until Hit or Miss consumes it.

### Shatter Comparison

Shatter spawns multiple independent shard entities (like a shotgun). Each shard has its own Hit/Miss/Bounce interactions. Unlike Projectile, shards have:
- Scale 0.35x (smaller)
- Gravity always applied (default: 10.0)
- No bounces (bounceCount = 0)
- Cone distribution with configurable spread

---

## Equipment & Stats

### Hex Staff Tiers

All basic staffs use Ring casting style.

| Tier | Mana (from armor set) | Volatility (from armor set) |
|------|----------------------|---------------------------|
| Crude | 0 | 0 |
| Copper | 34 | 5 |
| Bronze | 96 | 13 |
| Iron | 177 | 23 |
| Thorium | 272 | 35 |
| Cobalt | 380 | 49 |
| Adamantite | 500 | 65 |
| Mithril | 630 | 82 |
| Onyxium | 770 | 100 |

### Cloth/Leather Armor Mana & Volatility

| Material | Mana | Volatility |
|----------|------|-----------|
| Wood | 5 | 1 |
| Wool | 2 | 2 |
| Leather_Soft | 20 | 3 |
| Cloth_Wool | 40 | 5 |
| Diving_Crude | 50 | 6 |
| Leather_Light | 70 | 9 |
| Cloth_Cotton | 100 | 13 |
| Bronze_Ornate | 125 | 16 |
| Leather_Medium | 140 | 18 |
| Cloth_Linen | 180 | 23 |
| Leather_Heavy | 230 | 30 |
| Cloth_Silk | 300 | 39 |
| Leather_Raven | 340 | 44 |
| Steel | 420 | 55 |
| Cloth_Cindercloth | 440 | 57 |
| Steel_Ancient | 550 | 72 |
| Prisma | 700 | 91 |

Armor weight distribution: Chest 40%, Legs 30%, Head 20%, Hands 10%.

### Hex Book Capacities

| Book | Max Glyphs | Primary Color | Secondary Color |
|------|-----------|--------------|----------------|
| Default (Hex_Book) | 8 | #8D6E63 (brown) | #BCAAA4 |
| Arcane | 12 | #0D47A1 (dark blue) | #42A5F5 |
| Fire | 6 | #E65100 (deep orange) | #FFB74D |
| Ice | TBD | TBD | TBD |
| Life | TBD | TBD | TBD |
| Void | TBD | TBD | TBD |

### Casting Styles

| Style | Layout | Distance | Notes |
|-------|--------|----------|-------|
| **Ring** | 360° circle | 3.0 blocks | Angle step: 360°/count |
| **Arc** | 120° front arc | 3.0 blocks | Start: lookYaw - 60°; step: 120°/(count-1) |
| **Sphere** | Fibonacci hemisphere | 3.0 blocks | Golden angle distribution; elevation 0-90° |

### Weapon Imbuement

Weapons can cast hexes on hit via `ImbuementData`:

```
manaOverride:      -1 = use hex default, ≥0 = override
manaMultiplier:    1.0 = no change
volatilityOverride: -1 = use hex default, ≥0 = override
volatilityMultiplier: 1.0 = no change
powerOverride:     -1 = use hex default, ≥0 = override
powerMultiplier:   1.0 = no change
colors:            custom hex visual override
```

Hex resolution priority: in-memory object > compressed ID (deserialize) > asset ID (load).

Trigger: `WeaponImbuementSystem` extends `DamageEventSystem` → checks main hand for ImbuementData → `ImbuementExecutor.execute()` → fires `HexCastEvent`.

### Custom Entity Stats

Three stats injected by Hexcode via armor reflection patching:
- **Volatility** — spell complexity budget
- **Magic_Power** — damage/effect multiplier (`getMagicPowerMultiplier()`)
- **MagicCharges** — cooldown/stacking modifier

---

## Advanced Combo Knowledge

### Critical Findings from Source Code

#### 1. Bounce Does NOT Fire Chains
Projectile bounces are purely physical. Only Hit (entity collision) and Miss (final block collision) execute the glyph's next links. A "Projectile (bounces=5) → Bolt" fires Bolt **once** at the final impact, not 5 times.

**Implication**: To get multi-impact behavior, use **Shatter** (multiple independent projectiles) instead of bouncing Projectile. Or use Area after Projectile hit.

#### 2. HP Drain is Self-Only
DrainGlyph blocks HP drain from non-self entities (UUID check). You can only drain your OWN health → mana. Stamina drain has no such restriction — you CAN drain enemy stamina.

**Implication**: "Drain enemy HP" builds don't work. Use Stamina drain for offensive resource denial.

#### 3. Area Branches Share Volatility
Area creates independent variable contexts per target, but they all share the same VolatilityTracker. The first targets get cheap glyph costs; later targets face escalating costs. Order matters for budget.

**Implication**: In Area → Bolt, if there are 10 targets, the 10th Bolt costs ~50x the 1st (at K=5). Practical limit is ~4-6 targets per Area before fizzle.

#### 4. Concentration Boosts REMAINING Budget
`addBudget(remainingBudget × 0.5)` — it multiplies what's LEFT, not the original total. Cast Concentration FIRST before spending any volatility to maximize the boost.

**Implication**: `Concentration → Area → Bolt` is optimal. `Area → Bolt → Concentration` wastes it — by then the budget is depleted.

#### 5. Fortify/Erode Chain Timing Differs by Target Type
- Entity target: chain deferred to construct expiry (via onEnd)
- Block target: chain fires immediately

**Implication**: `Fortify → Bolt` on an entity means Bolt fires when the shield expires (delayed payoff). On a block, Bolt fires instantly after healing.

#### 6. Domain Upfront + Drain Costs
Domain costs mana both upfront (20-40 based on power) AND per second (2-6 based on radius) AND per trigger (5 per entity entry). Triple cost layer means Domains are extremely mana-hungry.

**Implication**: Low-radius (2-3) Domains with moderate power are most cost-efficient. Max-radius Domains drain mana in seconds.

#### 7. Phase Intensity Gates Block Quality
Phase checks `blockQuality > intensity` and fails if the block is too tough. Intensity maxes at 15, so high-quality blocks (quality 16+) can't be phased at all.

**Implication**: Phase is limited to soft/medium blocks. Can't phase ore, obsidian, or reinforced blocks.

#### 8. Conjure Interval Creates True Loops
Conjure with `interval > 0` re-triggers its chain on ALL contained entities every N seconds. Unlike Delay chains (which consume budget from original cast), Conjure interval appears to re-use the construct's existing budget context.

**Implication**: Conjure (interval=2s) → Bolt could potentially fire Bolt every 2 seconds on entities inside the zone. This is the closest thing to a true repeating loop in Hexcode.

#### 9. Shatter Shards Are Independent Projectiles
Each shard has its own ProjectileState, its own Hit/Miss/Bounce interactions. A Shatter with count=8 creates 8 independent projectiles, each of which fires the glyph chain on impact independently.

**Implication**: `Shatter (count=8) → Freeze` fires 8 independent Freezes on 8 different targets hit by shards. This is a "fire and forget" area attack that doesn't use Area's escalating costs.

#### 10. Glyph IDs Preserved in Splicing
When Interfere/Resonate clone glyphs, the glyph UUID is NOT regenerated. Slot links reference these UUIDs. This means the injected glyphs are fully functional in the target's hex context — all internal wiring works.

**Implication**: Complex multi-glyph chains can be spliced intact. You can Interfere a 5-glyph chain onto someone's Freeze, and all 5 glyphs will execute correctly when the Freeze expires.

### Optimal Spell Templates

#### Best Damage-Per-Volatility
```
Self → Beam → Bolt
```
2 glyphs, minimal cost. Bolt damage = power × magicPowerMultiplier.

#### Best AoE Damage
```
Concentration → Self → Beam → Area (r=5) → Bolt (power=15)
```
Concentration first (max budget boost). Small radius keeps target count manageable.

#### Best Sustained DPS
```
Self → Beam → Domain (r=3, power=1) → Bolt (power=10)
```
Small radius = cheap drain (1.2/sec). Each entity entry triggers Bolt independently. No repeat escalation between different entities.

#### Best Crowd Control
```
Self → Beam → Shatter (count=8, spread=45°) → on Hit: Freeze (5s)
```
8 independent shards, each freezing its target. No Area volatility tax. Spread controls coverage.

#### Best Burst Combo
```
Concentration → Self → Beam → Erode (amount=20, 10s) → Delay (1s) → Bolt (power=25)
```
Apply max vulnerability (+1.0 multiplier), wait 1s, then Bolt hits the debuffed target. Delayed payoff pattern.

#### Best Zone Control
```
Self → Beam → Conjure (box 6×3×6, interval=3s) → Ensnare (r=2, dmg=8, 5s)
```
Conjure re-deploys spike traps every 3 seconds inside a persistent box. Enemies entering get spiked, then spiked again 3 seconds later.

#### Best Resource War
```
Self → Beam → Drain (stamina=50%, dest=Self, 5s) → onEnd: Freeze (3s) → onEnd: Bolt (20)
```
Drain 50% of enemy stamina → your mana for 5 seconds. When Drain expires, Freeze. When Freeze expires, Bolt. Three-stage attack from one cast. Both Drain and Freeze are spliceable — Resonate can extend them.

#### Best Counter-Spell
```
Self → Beam → Interfere → Scale (0.25x, 10s) → Freeze (5s)
```
Hijack all active constructs on target. Replace their pending chains with: shrink to quarter size + freeze. Two debuffs from one counter.

---

## TrailOfOrbis Damage Integration

### Ratio-Based Base Replacement

TrailOfOrbis replaces Hexcode's internal `magicPowerMultiplier` (caster stat) with RPG power while preserving the player's glyph design as a multiplier.

**Formula** (RPGDamageSystem.gatherCombatInputs):
```
rawGlyphPower = vanillaDamage / baseHexPower       (strip Hexcode's caster stat)
glyphMultiplier = rawGlyphPower / slotDefault       (normalize: default = 1.0x)
rpgBaseDamage = ourRPGPower × glyphMultiplier       (RPG stats × glyph design)
```

- `baseHexPower`: captured BEFORE echo via ThreadLocal in HexCastEventInterceptor (echo preserved in ratio)
- `slotDefault`: per-glyph default from `GlyphAsset.getSlot(name).getDefaultValue()` (cached at startup)
- `ourRPGPower`: `weaponBaseDamage + flatSpellDamage + flatElementalDamage[glyphElement]`

**Per-glyph slot mapping:**

| Glyph | Damage Slot | Code Default | JSON Default |
|-------|-------------|-------------|--------------|
| Bolt | `power` | 5.0 | 15.0 |
| Combust | `magnitude` | 10.0 | (from JSON) |
| Gust | `magnitude` | 10.0 | 15.0 |
| Ensnare | `damage` | (varies) | (from JSON) |
| Phase | `intensity` | (varies) | (from JSON) |

Glaciate/Shatter have no player-modifiable damage slot → fallback path.

**Examples:**
- Basic bolt (power=15, default=15): glyphMult = 1.0 → damage = RPG power
- Player wires 30 into power: glyphMult = 2.0 → double damage (at 2× volatility cost)
- Echo proc: baseHexPower captured pre-echo → echo factor stays → damage doubled

Pipeline then applies % spell damage, elemental modifiers, critical strikes, and defenses normally. Flat spell and flat elemental are baked into `ourRPGPower` and NOT added again in pipeline Steps 1-2.
