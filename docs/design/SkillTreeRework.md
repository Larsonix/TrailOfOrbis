# Skill Tree Rework — Master Design Document

> **Status**: Review pass complete — all phases designed
> **Scope**: Full rework of all 485 skill tree node values, stat assignments, keystone mechanics, nexus effects, and descriptions.
> **What does NOT change**: The tree structure (485 nodes, 15 regions, 3D layout, connections, allocation code, sanctum). Only what the nodes DO.

---

## 1. Why This Rework Is Needed

The skill tree was implemented quickly from a draft design doc. The structure is solid (node types, tiers, connections, synergy scaling, 3D sanctum) but the content — what each node actually gives — was never properly designed. Problems:

1. **No power budget** — Node values are arbitrary. A tier-1 basic gives +6% here, +3.8% there, with no formula explaining why.
2. **Octant arms have no identity** — The 8 hybrid arms are random grabs from 3 neighbor stat pools. No cluster themes, no organizational logic, no reason to invest in Havoc vs just going deeper into Fire + Void + Lightning separately.
3. **Elemental arms are better but not clean** — The v2 design doc describes "damage way" vs "stats way" lanes, but actual YAML doesn't follow this consistently. Cluster themes bleed.
4. **Keystones are stat sticks** — "+X% to stat, -Y% to other stat" is not build-defining. PoE keystones change HOW you play (Mind Over Matter, Chaos Inoculation, Iron Reflexes). Ours are just bigger numbers with penalties.
5. **9 keystone stats don't work** — HP_SCALING_DAMAGE, SPEED_TO_SPELL_POWER, ATK_SPEED_TO_SPELL_POWER, EVASION_TO_ARMOR, DAMAGE_FROM_MANA_PERCENT, EXECUTE_DAMAGE_PERCENT, DAMAGE_VS_FROZEN_PERCENT, DAMAGE_VS_SHOCKED_PERCENT, MANA_AS_DAMAGE_BUFFER are declared but not wired into the combat pipeline. (Needs verification per-stat before fixing.)
6. **All 8 nexus nodes are identical** — Each is "+3% to 3 elements." No unique scaling, no reason to prefer one octant over another.
7. **Stat distribution has no strategy** — Block appears everywhere. Health regen barely exists. Charged attack only in Fire. No rule governs which stats appear where.
8. **Descriptions are inconsistent** — Some misstate values, some don't explain mechanics, formatting varies.

## 2. Ground Truth

**The attribute/element system is locked and correct.** This is the foundation everything else is built on.

### 6 Elements — Identity Stats (from config.yml, verified)

Each element grants 5 core identity stats with zero overlap. Water has a 6th (ES Regen) from the F4 rework. Hexcode integration stats (magicPower, volatilityMax, castSpeed) are excluded from tree design — they're attribute-only grants for spell compatibility.

| Element | Fantasy | Identity Stats (per point) |
|---------|---------|--------------------------|
| **FIRE** | Glass Cannon | Physical Dmg % (0.4), Charged Atk Dmg % (0.3), Crit Multiplier (0.6), Burn Dmg % (0.4), Ignite Chance (0.1) |
| **WATER** | Arcane Mage | Spell Dmg % (0.5), Max Mana (1.5), ES % (0.5), ES Regen (0.2), Mana Regen (0.15), Freeze Chance (0.1) |
| **LIGHTNING** | Storm Blitz | Atk Speed % (0.3), Move Speed % (0.15), Crit Chance (0.1), Stamina Regen (0.1), Shock Chance (0.1) |
| **EARTH** | Iron Fortress | Max HP % (0.5), Armor (3.5), HP Regen (0.02), Block Chance (0.05), KB Resistance (0.3) |
| **WIND** | Ghost Ranger | Evasion (5.0), Accuracy (3.0), Projectile Dmg % (0.5), Jump Force % (0.15), Projectile Speed % (0.3) |
| **VOID** | Life Devourer | Life Steal (0.2), True Dmg % (0.1), DoT Dmg % (0.3), Mana on Kill (0.5), Status Effect Duration (0.3) |

> **Note**: Water has 6 identity stats (ES Regen added in the F4 energy shield rework). All other elements have 5. Tree clusters for Water distribute all 6.
>
> **Note**: Earth's HP Regen (0.02/pt) and Block Chance (0.05/pt) are much lower than other elements' per-point values. This is intentional — armor (3.5/pt) and max HP % (0.5/pt) are Earth's primary scaling. Regen and block are supplementary. Tree values for these stats are calibrated accordingly.

### Base Constants

| Stat | Base Value |
|------|------------|
| Critical Chance | 5.0% |
| Critical Multiplier | 150% |
| Accuracy | 10.0 |
| ES Regen Delay | 3.0s |
| Max Health | 100 |

### Progression

- 1 attribute point + 1 skill point per level
- Level 100 player: ~101 attribute points, ~101 skill points
- Skill points: 667 total to fill entire tree (impossible — forces choices)
- Respec: free, unlimited

## 3. Design Approach

### Why Archetypes First, Not Stats First

The instinct is "map every stat to an element, then assign nodes." This produces a clean spreadsheet but not interesting builds. Stats don't create playstyles — playstyles demand stats.

**The right sequence**: define what each arm's player DOES in combat → then discover which stats serve that playstyle → then assign values.

The attribute system already handles stat-to-element mapping (30 stats, locked). The skill tree's job isn't to repeat that — it's to create specialization paths that make two players with identical attributes play differently.

### The Qualitative Tier Shift

The most important design rule: each tier of the tree provides a **qualitatively different** kind of power.

| Tier | Node Type | What It Provides | Design Intent |
|------|-----------|------------------|---------------|
| 1-3 | Basic | Identity stats (same as attributes) | More of what you already do — quantitative |
| 4 | Notable | Stats attributes DON'T give (penetration, conversion, conditionals, multipliers) | New capabilities — qualitative shift |
| 5 | Synergy | Scaling bonuses per investment depth | Reward commitment — investment payoff |
| 5 | Nexus Hub | Unique effect scaling with attribute points | Octant identity — emergence bonus |
| 6 | Keystone | Mechanics that change HOW you play | Build-defining — paradigm shift |

**This is the key insight**: basics give you MORE, notables give you NEW, keystones give you DIFFERENT.

Currently all three tiers give the same kind of bonus (just bigger), which is why the tree feels flat.

### The Pathing Tax

Players spend skill points to PATH through basic nodes to reach notables and keystones. Path nodes shouldn't feel wasted — they should give meaningful identity stats. But they also shouldn't be so good that notables/keystones feel unnecessary. The basic→notable→keystone progression is a deliberate escalation in impact-per-point AND in the KIND of power offered.

### The Octant Value Proposition

An octant arm must offer something you CANNOT get by investing in its 3 elemental arms separately. Otherwise there's no reason to path through bridges to reach it.

What octants provide:
1. **Cross-element stat combinations** in a single path (convenience + efficiency)
2. **Unique nexus effects** that scale with multi-element attribute investment
3. **Unique keystones** with mechanics no elemental arm offers
4. **Notables** that blend neighbor identities in ways no single element can

The nexus is the critical piece — it's the reward for deep octant investment that can't be replicated elsewhere.

### Skill Tree vs Gear vs Attributes: Division of Power

| Mechanic | Attributes | Gear | Skill Tree |
|----------|-----------|------|-----------|
| Identity stats (the 30) | **Primary** (linear per point) | Moderate (modifiers) | Moderate (basic nodes) |
| Flat damage/defense | Moderate | **Primary** | Minimal |
| % Increased | Moderate | Moderate | Moderate |
| % More (multiplicative) | Never | Never | **Exclusive** |
| Per-element penetration | Never | Fire pen + spell pen (weapons only) | **Primary** (all 6 elements via notables) |
| Damage conversion | Never | 6 infusion suffixes | **Primary** |
| Conditional damage | Never | Some suffixes | **Primary** |
| Derivation stats (stat→stat) | Never | Never | **Exclusive** |
| On-event mechanics | Never | Never | **Exclusive** |
| Mana-as-damage, mana shield | Never | Never | **Exclusive** |

## 4. Phases

### Phase 1: Archetype Definition (all 14 arms)

For each arm, answer:
1. **What's the fantasy in one sentence?** (gameplay language, not stat language)
2. **What does this build DO in a fight?** (damage pattern, survival method, unique behavior)
3. **What makes it feel different from its closest neighbors?**

Start with 6 elemental arms (simpler — single-element identity), then 8 octant arms (harder — emergent identity from 3 elements).

### Phase 2: Stat Vocabulary + Tiering Rules

Categorize every stat in the game by WHERE it can appear in the tree:
- **Tier B**: Basic nodes only — identity stats from the arm's element(s)
- **Tier N**: Notable nodes — broader stats (penetration, conversion, conditional, multiplicative)
- **Tier K**: Keystone mechanics — unique behaviors, not traditional stats
- **Tier S**: Synergy scaling — depth-reward stats

Define rules: "penetration NEVER appears on a basic node." "Flat damage is minimal in the tree." "Conversion is notable-tier or higher."

### Phase 3: Keystone + Nexus Design

Design as gameplay mechanics, not stat bundles. For each:
- What behavior does this enable or change?
- What's the real trade-off?
- Does the combat system support this today, or do we need new code?

Decide which of the current 9 broken stats are worth implementing vs replacing.

### Phase 4: Cluster Design (per-arm internal structure)

Each arm's 4 clusters organized into 2 lanes. Assign:
- Lane themes (offensive/utility, damage/sustain, etc.)
- Specific stats per cluster (driven by archetype + tiering rules)
- Notable effects per cluster

### Phase 5: Power Budget + Values

Formula-driven, derived from attribute ground truth:
- How much power does 1 skill point buy at each tier?
- What's the ratio of skill-point-power to attribute-point-power?
- How do values scale across tiers 1→6?

### Phase 6: YAML Generation + Descriptions

The actual implementation. Edit `skill-tree.yml` with all new values. Write clear, consistent, mechanically accurate descriptions for all 485 nodes.

## 5. Phase 1 — Arm Archetypes

### Design Template

For each arm:
- **Fantasy**: One sentence, gameplay language
- **Combat Pattern**: How they deal damage, how they survive, what makes them tick
- **Differentiation**: What makes this arm feel different from its closest neighbors
- **Lane 1 theme**: The offensive/damage-focused half of the arm
- **Lane 2 theme**: The utility/defensive/complementary half of the arm
- **Keystone concepts**: 2 build-defining mechanics (not values — just the idea)

---

### 5.1 FIRE — The Berserker

**Fantasy**: Hit as hard as possible. One big swing should hurt.

**Combat Pattern**: Fire builds deal damage through raw physical force amplified by critical strikes. They favor charged attacks (heavy hits) over speed. Survival is not their concern — they kill before they die. Burn provides sustained damage after the initial hit, punishing enemies who survive the burst.

**Differentiation**:
- vs Lightning (also offensive): Lightning is about SPEED and FREQUENCY — many fast hits. Fire is about IMPACT — fewer, harder hits.
- vs Void (also aggressive): Void sustains through damage dealt (life steal). Fire doesn't sustain at all — pure offense.

**Lane 1 — Ignition (damage scaling)**:
Clusters 1+3. Physical damage, crit multiplier, charged attack damage. The "hit harder" lane. Notables unlock fire penetration, execute damage, fire conversion.

**Lane 2 — Pyre (ailment/DoT)**:
Clusters 2+4. Burn damage, ignite chance, burn duration. The "keep burning" lane. Notables unlock DoT scaling, burn spread, fire-specific multipliers.

**Keystone concepts**:
1. **Damage conversion + elemental identity**: Convert your physical damage to fire, making you fully commit to fire as your damage type. Powerful but locks you out of physical builds.
2. **Glass cannon amplifier**: Massively increase all damage output, but at a severe survivability cost. The "berserker rage" fantasy.

---

### 5.2 WATER — The Arcane Controller

**Fantasy**: Command magical energy. Your mana pool IS your power.

**Combat Pattern**: Water builds deal spell damage fueled by a deep mana pool. Energy Shield provides a defensive buffer that regenerates, creating a "second health bar" playstyle. They don't tank hits with armor — they absorb them with shield and recover. Freeze provides crowd control, locking enemies down rather than burning them over time.

**Differentiation**:
- vs Fire (also damage): Fire is raw burst. Water is sustained, resource-managed casting.
- vs Earth (also defensive): Earth stacks armor and blocks. Water has a regenerating energy shield — proactive vs reactive defense.

**Lane 1 — Torrent (spell damage)**:
Clusters 1+3. Spell damage, max mana, water damage scaling. The "stronger spells" lane. Notables unlock water penetration, mana-to-damage conversion, spell multipliers.

**Lane 2 — Glacier (control + shield)**:
Clusters 2+4. Freeze chance, energy shield, mana regen. The "freeze and survive" lane. Notables unlock freeze synergy (damage vs frozen), ES scaling, freeze duration.

**Keystone concepts**:
1. **Freeze exploitation**: Massively amplify damage against frozen targets, with improved freeze application. Turns Water from "generic caster" into "freeze-shatter specialist."
2. **Mana-as-power**: Your mana pool directly amplifies your damage, but spells cost more. Rewards building an enormous mana pool — the fantasy of "unlimited power."

---

### 5.3 LIGHTNING — The Storm Dancer

**Fantasy**: Strike fast, move fast, never stop. Death by a thousand cuts.

**Combat Pattern**: Lightning builds overwhelm enemies with attack speed and critical strike frequency. Where Fire lands one massive hit, Lightning lands ten smaller ones — but crits make several of those devastating. High mobility lets them reposition constantly. Shock debuffs enemies to take more damage, rewarding sustained pressure.

**Differentiation**:
- vs Fire (also crit): Fire stacks crit MULTIPLIER (bigger crits). Lightning stacks crit CHANCE (more frequent crits). Different scaling curves.
- vs Wind (also mobile): Wind is mobile at RANGE (projectiles). Lightning is mobile in MELEE (attack speed + movement).

**Lane 1 — Surge (speed + crits)**:
Clusters 1+3. Attack speed, crit chance, movement speed. The "faster everything" lane. Notables unlock lightning penetration, attack speed multipliers, crit synergies.

**Lane 2 — Tempest (ailment + utility)**:
Clusters 2+4. Shock chance, stamina regen, shock scaling. The "shock and exploit" lane. Notables unlock damage vs shocked, shock duration, shock-triggered effects.

**Keystone concepts**:
1. **Shock exploitation**: Massively amplify damage against shocked targets, with improved shock application. Makes Lightning a debuff-focused damage dealer.
2. **Speed-to-power conversion**: Convert excess attack speed or movement speed into other stats. The "so fast it becomes power" fantasy. High risk (sacrifice defense for speed, then convert speed to offense).

---

### 5.4 EARTH — The Unbreakable Wall

**Fantasy**: You don't die. Ever. Enemies break against you.

**Combat Pattern**: Earth builds maximize effective health through armor, HP, health regen, and blocking. They don't dodge — they take the hit and barely notice. Block provides damage prevention on big hits. Knockback resistance means they hold position. They deal less damage, but they outlast everything. Thorns and block-counter mechanics let them deal damage PASSIVELY — enemies hurt themselves by attacking.

**Differentiation**:
- vs Water (also defensive): Water's defense (ES) is a regenerating buffer — it breaks and reforms. Earth's defense (armor + HP + block) is permanent — it never breaks, just absorbs.
- vs Void (also sustaining): Void sustains by dealing damage (life steal). Earth sustains by being inherently durable — no damage output required.

**Lane 1 — Bastion (raw durability)**:
Clusters 1+3. Max HP, armor, HP regen. The "bigger wall" lane. Notables unlock armor scaling, physical resistance, HP-based effects.

**Lane 2 — Rampart (active defense + retaliation)**:
Clusters 2+4. Block chance, knockback resistance, earth damage. The "punish attackers" lane. Notables unlock block synergies, thorns, block-counter mechanics.

**Keystone concepts**:
1. **Absolute fortress**: Massive defensive amplification (armor, block, HP) at the cost of all offense. The "unkillable" fantasy — you literally cannot die, but you kill slowly.
2. **Retaliation tank**: Convert your tankiness into offense. HP scales damage. Blocking deals counter-damage. The "the tankier I am, the harder I hit" paradigm.

---

### 5.5 VOID — The Parasite

**Fantasy**: Feed on your enemies. Their pain is your sustenance.

**Combat Pattern**: Void builds have no inherent defense. They survive entirely by stealing life from enemies through damage dealt. This creates a feast-or-famine playstyle — while dealing damage, they're nearly immortal; when they stop (stunned, no targets), they're fragile. DoT effects provide sustained damage AND sustained healing. True damage bypasses defenses, making them effective against armored targets. Status effect duration extends their ailments and sustain windows.

**Differentiation**:
- vs Fire (also offensive): Fire is pure burst with no sustain. Void is sustained aggression with built-in healing.
- vs Water (also sustaining): Water's sustain is passive (ES regenerates on its own). Void's sustain is active — you MUST deal damage to survive.

**Lane 1 — Blight (sustain-through-damage)**:
Clusters 1+3. Life steal, true damage, life leech. The "feed on pain" lane. Notables unlock enhanced leech mechanics, true damage scaling, lifesteal synergies.

**Lane 2 — Entropy (DoT + debuff)**:
Clusters 2+4. DoT damage, status duration, mana on kill. The "slow death" lane. Notables unlock DoT amplification, void penetration, resistance reduction, DoT-triggered effects.

**Keystone concepts**:
1. **Full void conversion**: Convert all damage to void, fully committing to the void damage type. Bypasses armor (void hits resistances), but makes you vulnerable to void-resistant enemies.
2. **Parasitic link**: Massively amplify life leech and true damage, but increase damage taken. The ultimate risk/reward — you're a glass cannon that heals by attacking.

---

### 5.6 WIND — The Ghost

**Fantasy**: They can't hit what they can't catch. Death from a distance.

**Combat Pattern**: Wind builds fight at range using projectiles, relying on evasion and accuracy to avoid damage entirely. Where Earth tanks hits and Water absorbs them, Wind DODGES them. Accuracy ensures their projectile attacks land. Jump force and projectile speed extend their effective range. They're fragile if caught (low armor, low HP) but hard to pin down.

**Differentiation**:
- vs Lightning (also agile): Lightning is fast IN melee. Wind avoids melee entirely — range is the defense.
- vs Fire (also damaging): Fire hits hard at close range. Wind hits consistently at long range.

**Lane 1 — Gale (projectile offense)**:
Clusters 1+3. Projectile damage, accuracy, projectile speed. The "hit from far" lane. Notables unlock wind penetration, projectile-specific multipliers, ranged damage amplification.

**Lane 2 — Zephyr (evasion + mobility)**:
Clusters 2+4. Evasion, jump force, dodge chance. The "untouchable" lane. Notables unlock evasion scaling, movement-on-evade effects, evasion-triggered bonuses.

**Keystone concepts**:
1. **Phantom**: Massively amplify evasion and gain on-evade bonuses. The "you literally cannot touch me" fantasy. Drawback: zero armor/HP, so anything that DOES hit is catastrophic.
2. **Sky Piercer**: Massively amplify projectile damage and add spell penetration. The "sniper" fantasy — massive single-target ranged damage. Drawback: melee damage gutted, commitment to ranged.

---

### 5.7 HAVOC — The Berserker Assassin (Fire + Void + Lightning)

**Fantasy**: Kill everything before it kills you, and heal from the carnage.

**Combat Pattern**: Havoc combines Fire's raw damage with Lightning's speed and Void's life steal into the ultimate aggressive melee build. They attack fast, crit hard, and sustain through sheer violence. DoTs from Fire and Void provide background damage while they chain-kill. This is the "action game protagonist" — constant offense, constant motion, constant healing from kills.

**Differentiation**:
- vs Fire (pure offense): Fire has no sustain. Havoc sustains through Void's life steal — they're aggressive AND survivable.
- vs Striker (also fast+offensive): Striker is about precision and evasion — surgical. Havoc is about chaos and overwhelm — berserker.
- vs Juggernaut (also Fire+Void): Juggernaut tanks while fighting. Havoc doesn't tank — they heal by killing faster.

**Lane 1 — Carnage (crit + speed)**:
Fire's crit multiplier + Lightning's attack speed + crit chance. The "blender" lane.

**Lane 2 — Ruin (DoT + sustain)**:
Void's DoT + life steal + Fire's burn. The "sustained violence" lane.

**Keystone concepts**:
1. **Kill chain**: On-kill effects that stack (speed, crit, DoT). The more you kill, the more dangerous you become. Resets when combat ends.
2. **DoT detonation**: Critting a DoT-affected target instantly deals all remaining DoT as burst. Synergizes with both Fire's burn and Void's DoTs.

---

### 5.8 JUGGERNAUT — The War Machine (Fire + Void + Earth)

**Fantasy**: Walk into anything and walk out alive, leaving destruction behind.

**Combat Pattern**: Juggernaut combines Fire's physical damage with Earth's tankiness and Void's sustain. Unlike Havoc (which sustains through speed and kills), Juggernaut sustains through being inherently durable AND stealing life. They're slow but unstoppable — high armor, high HP, moderate damage, constant healing. They excel in prolonged fights where their durability advantage compounds.

**Differentiation**:
- vs Earth (pure tank): Earth is purely defensive. Juggernaut converts tankiness into offense — HP scales damage, blocking hurts attackers.
- vs Havoc (also Fire+Void): Havoc is fast and fragile between kills. Juggernaut is slow and always tough.
- vs Lich (also tanky+sustain): Lich sustains through ES and DoTs (magical). Juggernaut sustains through HP and life steal (physical).

**Lane 1 — Conquest (damage + durability)**:
Fire's physical damage + Earth's armor and HP. The "armored warrior" lane.

**Lane 2 — Bloodforge (sustain + retaliation)**:
Void's life steal + Fire's burn + Earth's block. The "unkillable bruiser" lane.

**Keystone concepts**:
1. **Blood fortress**: Block chance scales with health, life steal scales with blocking. The "more HP = more block = more steal" loop.
2. **Colossus**: HP directly scales physical damage. The "I'm tanky AND I hit hard because I'm tanky" paradigm. Slow, but devastating.

---

### 5.9 STRIKER — The Blade Dancer (Fire + Wind + Lightning)

**Fantasy**: Precision kills. Every hit matters, every dodge is an opening.

**Combat Pattern**: Striker combines Lightning's speed with Wind's evasion and Fire's crit damage into the assassin archetype. They dodge attacks and counter with devastating crits. Unlike Havoc (which overwhelms with volume), Striker is surgical — fewer hits, but each one is precisely placed for maximum damage. Evasion is their defense; crits are their offense.

**Differentiation**:
- vs Lightning (pure speed): Lightning is fast for volume. Striker is fast for precision — dodge, then counter-crit.
- vs Havoc (also fast+offensive): Havoc berserks through enemies. Striker picks them apart.
- vs Wind (also evasive): Wind evades at range. Striker evades in melee and counter-attacks.

**Lane 1 — Quicksilver (speed + crit)**:
Lightning's attack speed + Fire's crit multiplier + crit chance. The "quick and deadly" lane.

**Lane 2 — Precision (evasion + counter)**:
Wind's evasion + accuracy + dodge-triggered bonuses. The "untouchable swordsman" lane.

**Keystone concepts**:
1. **Blade dance**: Evading an attack guarantees your next hit crits. The "parry-riposte" fantasy. Requires evasion investment to trigger reliably.
2. **Momentum**: Consecutive hits within a window stack escalating damage. The "combo counter" fantasy — sustained pressure rewarded.

---

### 5.10 WARDEN — The Iron Ranger (Fire + Wind + Earth)

**Fantasy**: Hold the line and rain death from a fortified position.

**Combat Pattern**: Warden combines Wind's projectile damage with Earth's fortification and Fire's raw power. They plant themselves, block incoming attacks, and retaliate with powerful ranged strikes. Unlike Wind (which is mobile and evasive), Warden is STATIONARY and armored. Their projectiles hit harder because they're not running — they're aiming. Block is their defense; charged projectile attacks are their offense.

**Differentiation**:
- vs Wind (also ranged): Wind is mobile and evasive. Warden is stationary and armored — turret vs scout.
- vs Earth (also tanky): Earth is pure defense. Warden uses defense to ENABLE offense — blocking creates counterattack windows.
- vs Juggernaut (also tanky+offense): Juggernaut is melee tank. Warden is ranged tank.

**Lane 1 — Garrison (fortified defense)**:
Earth's armor + block + Fire's charged attack. The "fortified position" lane.

**Lane 2 — Outrider (ranged offense)**:
Wind's projectile damage + accuracy + projectile speed. The "precision fire" lane.

**Keystone concepts**:
1. **Fortified volley**: Projectile hits grant stacking armor. The "the more I shoot, the tougher I get" loop. Rewards sustained ranged combat.
2. **Block counter**: Successful blocks deal massive damage back to the attacker. The "punish anyone who tries to hit me" fantasy. Commits to block-based defense.

---

### 5.11 WARLOCK — The Dark Caster (Water + Void + Lightning)

**Fantasy**: Corrupt everything. Spells drain, debuff, and destroy.

**Combat Pattern**: Warlock combines Water's spell damage with Void's life steal and Lightning's crit chance into a dark caster archetype. Unlike Water (which is a pure mage), Warlock sacrifices mana efficiency for raw spell power fueled by stealing resources from enemies. They cast powerful spells, sustain through spell leech, and debuff with status effects. Crits make their spells devastating. Mana management is their core challenge — spells are expensive but lethal.

**Differentiation**:
- vs Water (also spell-focused): Water is efficient and controlled. Warlock is expensive and overwhelming.
- vs Lich (also Water+Void): Lich is defensive and DoT-focused. Warlock is offensive and burst-focused.
- vs Havoc (also aggressive+sustain): Havoc is melee berserker. Warlock is ranged spell berserker.

**Lane 1 — Hex (offensive spellcasting)**:
Water's spell damage + Void's DoT + Lightning's crit. The "devastating spells" lane.

**Lane 2 — Ritual (resource drain)**:
Water's mana + Void's mana on kill + life steal. The "fuel the machine" lane.

**Keystone concepts**:
1. **Soul siphon**: Spell hits leech both life and mana. The "every spell feeds me" fantasy. Costs HP upfront or has reduced defenses.
2. **Spell echo**: Spells have a chance to repeat as void damage. The "double cast" fantasy. Expensive on mana but devastating.

---

### 5.12 LICH — The Undying Sorcerer (Water + Void + Earth)

**Fantasy**: You cannot truly kill what refuses to stay dead. Slow, inevitable, eternal.

**Combat Pattern**: Lich combines Water's ES with Earth's HP and Void's DoTs into the tankiest caster archetype. Unlike Warlock (which is burst and aggressive), Lich wins through attrition — layered defenses (HP + ES + regen) combined with relentless DoTs that kill slowly but surely. They don't need to burst enemies down because they can't be burst down themselves. Status effects stack over time. The longer the fight, the stronger Lich gets.

**Differentiation**:
- vs Warlock (also Water+Void): Warlock bursts with spells. Lich grinds with DoTs.
- vs Earth (also tanky): Earth is physically tough. Lich is magically tough (ES + mana buffer).
- vs Juggernaut (also durable+sustain): Juggernaut is a physical tank. Lich is a magical tank.

**Lane 1 — Grasp (DoT + spell)**:
Void's DoT + Water's spell damage + energy shield. The "slow death" lane.

**Lane 2 — Crypt (layered defense)**:
Earth's HP + armor + Water's ES + HP regen. The "two health bars" lane.

**Keystone concepts**:
1. **Plague resilience**: Inflicting ailments grants stacking elemental resistance. The "the more I curse, the tougher I become" loop. Anti-synergy with burst damage.
2. **Undying shell**: DoTs you inflict regenerate your energy shield. The "my DoTs are my shield regen" paradigm. Only works if you maintain DoTs on enemies.

---

### 5.13 TEMPEST — The Battle Mage (Water + Wind + Lightning)

**Fantasy**: Cast while moving. Speed IS spell power.

**Combat Pattern**: Tempest combines Water's spell damage with Lightning's speed and Wind's mobility into a mobile caster. Unlike Water (which stands and casts), Tempest is always moving — their speed directly enhances their spell power. Unlike Striker (which is a melee assassin), Tempest fights at range with spells. They're the "kiting mage" — dealing damage while never standing still, using movement speed and evasion to avoid danger.

**Differentiation**:
- vs Water (also spell-focused): Water is a stationary turret caster. Tempest is a mobile hit-and-run caster.
- vs Striker (also fast): Striker is fast in melee. Tempest is fast at range.
- vs Wind (also mobile+ranged): Wind uses physical projectiles. Tempest uses magical spells.

**Lane 1 — Squall (spell + speed)**:
Water's spell damage + Lightning's attack speed + crit. The "rapid casting" lane.

**Lane 2 — Tailwind (mobility + projectile)**:
Wind's projectile damage + movement speed + evasion. The "run and gun" lane.

**Keystone concepts**:
1. **Arcane velocity**: Attack speed bonus converts to spell damage. The "faster I swing, harder my spells hit" paradigm. Rewards stacking speed.
2. **Storm runner**: Movement speed bonus converts to spell damage. The "faster I move, harder my spells hit" paradigm. Rewards constant motion.

---

### 5.14 SENTINEL — The Guardian (Water + Wind + Earth)

**Fantasy**: The shield that protects everyone. Unassailable defense.

**Combat Pattern**: Sentinel combines Earth's block with Wind's evasion and Water's ES into the ultimate defensive archetype. Unlike Earth (which is a wall), Sentinel has LAYERED defense — they block, evade, AND absorb with ES. They deal minimal damage but are virtually impossible to kill. Their offense comes from defensive mechanics — thorns, block counter, reflection. Every defensive layer they stack makes the next layer more effective.

**Differentiation**:
- vs Earth (also tanky): Earth is one-dimensional defense (armor+HP). Sentinel layers three defense types (block+evasion+ES).
- vs Lich (also durable caster): Lich is durable and deals DoT damage. Sentinel is durable and deals almost no damage — pure defense.
- vs Warden (also defensive): Warden uses defense to enable ranged offense. Sentinel uses defense AS offense (thorns, counter).

**Lane 1 — Aegis (block + shield)**:
Earth's block + Water's energy shield + HP. The "layered defense" lane.

**Lane 2 — Vigilance (evasion + awareness)**:
Wind's evasion + accuracy + Earth's HP regen + Water's mana regen. The "nothing gets past me" lane — layered awareness with regeneration.

**Keystone concepts**:
1. **Fortress aura**: Convert evasion to armor. Collapses two defense types into one massively amplified stat. Trades dodge chance for raw damage reduction.
2. **Adaptive guard**: Taking ailment damage grants temporary immunity to that element. The "what doesn't kill me makes me stronger" paradigm. Reactive defense.

---

## 6. Implementation Status of Keystone Stats

These stats are referenced by keystone nodes. Status needs per-stat verification against actual combat code before implementation work.

### Confirmed Working (8)
- `DETONATE_DOT_ON_CRIT` — Havoc KS2
- `CONSECUTIVE_HIT_BONUS` — Striker KS2
- `BLOCK_COUNTER_DAMAGE` — Warden KS2
- `SPELL_ECHO_CHANCE` — Warlock KS2
- `SHIELD_REGEN_ON_DOT` — Lich KS2
- `BLOCK_HEAL_PERCENT` — Earth KS1
- `DAMAGE_TAKEN_PERCENT` — Fire KS2
- `IMMUNITY_ON_AILMENT` — Sentinel KS2

### Flagged for Verification (9)
- `HP_SCALING_DAMAGE` — Juggernaut KS2 — reported as no field in ComputedStats
- `SPEED_TO_SPELL_POWER` — Tempest KS2 — reported as no field
- `ATK_SPEED_TO_SPELL_POWER` — Tempest KS1 — reported as no field
- `EVASION_TO_ARMOR` — Sentinel KS1 — field exists but reported as never read
- `DAMAGE_FROM_MANA_PERCENT` — Water KS2 — field exists but reported as never read
- `EXECUTE_DAMAGE_PERCENT` — Fire notable — field exists but reported as not checked in combat
- `DAMAGE_VS_FROZEN_PERCENT` — Water KS1 — field exists but reported as never checks freeze
- `DAMAGE_VS_SHOCKED_PERCENT` — Lightning KS1 — field exists but reported as never checks shock
- `MANA_AS_DAMAGE_BUFFER` — Lich synergy — calc exists but mana deduction reported broken

> **NOTE**: These findings come from subagent searches which may have false negatives. Each stat needs explicit grep verification against RPGDamageCalculator, RPGDamageSystem, ConditionalMultiplierCalculator, CombatRecoveryProcessor, and StatsCombiner before being declared truly broken.

---

## 7. Phase 2 — Stat Vocabulary & Tiering Rules

### 7.1 The Three Stat Sources

Every stat in the game comes from one or more of three sources. Understanding which source PRIMARILY owns each stat is critical for the tree to feel distinct.

| Source | Role | Count | Examples |
|--------|------|-------|---------|
| **Attributes** | Linear base scaling. Reliable, always-on. | 30 identity stats | +0.4% phys dmg per Fire point |
| **Gear** | Flat + percent from equipment. RNG-driven, item-hunting. | ~108 rollable stats | +12 armor suffix, +3% crit chance prefix |
| **Skill Tree** | Specialization. Mechanics that shape HOW you play. | ~171 addressable stats | Penetration, conversion, multipliers, on-event triggers |

The overlap is intentional — all three sources can grant "+physical damage%." But each source has stats it PRIMARILY owns:

### 7.2 True Tree-Exclusive Stats

These stats **cannot** be obtained from gear or attributes. They are the tree's unique selling point.

**Multiplicative ("MORE") Damage**
- `damage_multiplier` — Global MORE damage (PoE-style, multiplicative with everything)
- `fire_multiplier`, `water_multiplier`, `lightning_multiplier`, `earth_multiplier`, `void_multiplier`, `wind_multiplier` — Per-element MORE

**Derivation Stats (Stat-to-Stat Conversion)**
- `hp_scaling_damage` — Max HP → Physical Damage %
- `speed_to_spell_power` — Move Speed % → Spell Damage %
- `atk_speed_to_spell_power` — Attack Speed % → Spell Damage %
- `evasion_to_armor` — Evasion → Armor

**On-Event Mechanics**
- `detonate_dot_on_crit` — Crit detonates remaining DoT as burst
- `consecutive_hit_bonus` — Stacking damage per hit within window
- `spell_echo_chance` — Spells repeat as bonus void damage
- `block_counter_damage` — Blocks deal % damage back
- `shield_regen_on_dot` — Your DoTs restore your energy shield
- `immunity_on_ailment` — Taking ailment grants temporary resistance

**Unique Modifiers**
- `damage_taken_percent` — Flat % more/less damage taken
- `non_crit_damage_percent` — Modifier on non-critical hits
- `mana_as_damage_buffer` — Mana absorbs damage before HP

### 7.3 Tree-Primary Stats (Tree is the main source, gear has limited access)

These stats CAN appear on gear but only as rare suffixes/prefixes on limited slots. The tree is where you BUILD around them.

| Stat Category | Gear Access | Tree Role |
|---------------|-------------|-----------|
| **Per-element penetration** (6) | Fire pen + spell pen (weapons only) | All 6 elements via notables |
| **Damage conversion** (6) | As weapon suffixes (infusions) | As notables, more accessible |
| **Conditional damage** (execute, vs frozen, vs shocked, at low life) | As rare weapon suffixes | As notables with higher values |
| **Thorns / reflect** | Shield-only prefixes | Keystone-level amplification |
| **Block mechanics** (block heal, block counter) | Shield suffixes | Keystone synergies |

### 7.4 Shared Stats (Both gear and tree provide meaningfully)

These are the "bread and butter" stats. Gear and tree both contribute, and stacking from both sources is the intended progression.

- All 30 identity stats (phys dmg%, spell dmg%, armor, evasion, etc.)
- Crit chance, crit multiplier
- Attack speed, movement speed
- Max health, max mana, energy shield
- Health regen, mana regen, stamina regen
- Elemental resistances
- Life steal, life leech
- Ailment chances (ignite, freeze, shock)
- DoT damage, status duration
- Accuracy

### 7.5 Stat Tiering Rules

These rules govern which node tier can grant which kind of stat. This is what creates the qualitative progression (basics = MORE, notables = NEW, keystones = DIFFERENT).

#### Tier B — Basic Nodes (Tier 1-3, cost 1 point)

**Rule**: Basic nodes grant ONLY identity stats from their arm's element(s).

| Arm Type | Allowed Stats |
|----------|--------------|
| Elemental arm | That element's 5 identity stats |
| Octant arm | Curated subset from the 3 neighbors' 15 identity stats (see 7.7) |

**Forbidden on basics**:
- Penetration (any element)
- Conversion (any element)
- Conditional damage (execute, vs frozen, vs shocked, etc.)
- Multiplicative/MORE bonuses
- Derivation stats
- Elemental damage types not matching the arm
- Flat damage (minimal — gear's domain)

**Format**: Single stat per basic node. Two stats on branch nodes (the branching point in each cluster).

**Why**: Basics are the "pathing tax" — they should give reliable, predictable scaling of identity stats. The player knows exactly what they're getting: more of their element's core power. No surprises, no complexity.

#### Tier N — Notable Nodes (Tier 4, cost 2 points)

**Rule**: Notables unlock stats that basics DON'T provide. This is the qualitative shift — investing deeper into an arm gives you NEW capabilities.

**Allowed on notables (in addition to identity stats)**:
- Per-element penetration (matching the arm's element)
- Damage conversion (matching the arm's element)
- Conditional damage (execute, vs frozen, vs shocked — matching the arm's ailment)
- Element-specific damage % (e.g., Fire Damage % on Fire arm)
- DoT amplification (burn duration, DoT damage — matching the arm)
- Broader combat stats: block damage reduction, shield effectiveness, health recovery %
- Multi-stat combinations (2-3 stats per notable)

**Forbidden on notables**:
- Multiplicative/MORE bonuses (keystone-exclusive)
- Derivation stats (keystone-exclusive)
- On-event mechanics (keystone-exclusive)
- Stats from unrelated elements (Fire notable should not give Water penetration)

**Format**: 2-3 stats per notable. At least one stat should be something basics DON'T give.

**Why**: Notables are the reward for pathing through a cluster. They should feel like an upgrade in KIND, not just in amount. A Fire notable giving "+15% Fire Damage, +10 Fire Penetration" is more exciting than one giving "+15% Physical Damage, +8% Physical Damage" because penetration is NEW.

#### Tier S — Synergy Nodes (Tier 5, cost 2 points)

**Rule**: Synergies scale with investment depth. They use the "per X nodes allocated in this arm" formula.

**Allowed stats**: Any stat that appears on basics or notables for that arm. The stat should be one that benefits from stacking (%, flat rating, chance %).

**Format**: "Per 3 nodes allocated: +X stat (max Y)." Four scaling synergies + one hub per arm.

**Synergy Hubs (Nexus nodes)**:
- Elemental hubs: +% to the arm's element damage + one complementary stat
- Octant hubs: **UNIQUE effect** — see Phase 3 for design. Must NOT be identical across octants.

#### Tier K — Keystone Nodes (Tier 6, cost 3 points)

**Rule**: Keystones change HOW you play. They are not stat sticks — they introduce mechanics, conversions, or trade-offs that define a build identity.

**Allowed (exclusive to keystones)**:
- Multiplicative/MORE damage bonuses
- Derivation stats (HP→damage, speed→spell, evasion→armor)
- On-event mechanics (detonate DoT, spell echo, block counter, etc.)
- Conditional state changes (immunity on ailment, damage taken modifiers)
- Conversion at high values (40%+ conversion commits you to an element)

**Required**: Every keystone MUST have a meaningful drawback that:
1. Is thematically appropriate to the arm's fantasy
2. Creates a real trade-off (not just "slightly less of something you don't care about")
3. Does NOT use movement speed reduction (movement is too fundamental)

**Format**: 1-2 bonuses (at least one being a unique mechanic) + 1-2 drawbacks.

**Drawback guidelines by arm theme**:

| Arm Fantasy | Good Drawbacks | Bad Drawbacks |
|-------------|---------------|---------------|
| Offense (Fire, Havoc, Striker) | -Max HP%, -Armor, +Damage Taken | -Move Speed |
| Defense (Earth, Sentinel) | -Damage%, -Attack Speed | -Move Speed, -Evasion (if arm identity) |
| Sustain (Void, Juggernaut, Lich) | -Damage when full HP, +Damage Taken, -Crit | -Move Speed |
| Speed (Lightning, Tempest) | -Max HP%, -Armor | -Move Speed (contradicts identity) |
| Range (Wind, Warden) | -Melee Damage, -Block | -Move Speed |
| Magic (Water, Warlock) | -Max HP%, +Mana Cost, -Non-Crit Damage | -Move Speed |

### 7.6 Elemental Arm Stat Maps

Each elemental arm uses its 5 identity stats across 4 clusters in 2 lanes. The mapping below shows which stats appear where and follows the archetype definitions from Phase 1.

**Structure reminder**: Each arm has 4 clusters (1-4), organized into Lane 1 (clusters 1+3) and Lane 2 (clusters 2+4). Each cluster has 5 basic nodes + 1 notable.

#### FIRE (Berserker)

| | Lane 1 — Ignition (burst damage) | Lane 2 — Pyre (ailment/DoT) |
|---|---|---|
| **Inner cluster** (1/2) | Phys Dmg %, Crit Multiplier | Burn Dmg %, Ignite Chance |
| **Inner notable** | Fire Damage %, Fire Penetration | Burn Duration %, DoT Damage % |
| **Outer cluster** (3/4) | Charged Atk %, Phys Dmg % | Crit Multiplier, Charged Atk % |
| **Outer notable** | Execute Damage %, Crit Multiplier | Fire Penetration, Charged Atk Dmg % |

**Basic stat rotation**: Physical Damage %, Crit Multiplier, Charged Attack %, Burn Damage %, Ignite Chance — all 5 identity stats used, distributed by lane theme.

#### WATER (Arcane Controller) — 6 identity stats

| | Lane 1 — Torrent (spell power) | Lane 2 — Glacier (control + shield) |
|---|---|---|
| **Inner cluster** | Spell Dmg %, Max Mana | Freeze Chance, Energy Shield % |
| **Inner notable** | Water Damage %, Water Penetration | Damage vs Frozen %, Freeze Chance |
| **Outer cluster** | Mana Regen, Max Mana | Spell Dmg %, ES Regen |
| **Outer notable** | Max Mana %, Mana Regen, ES Regen | Water Penetration, Spell Dmg % |

> Water has 6 identity stats: Spell Dmg %, Max Mana, ES %, ES Regen, Mana Regen, Freeze Chance. ES Regen appears in outer clusters where the shield sustainability theme deepens.

#### LIGHTNING (Storm Dancer)

| | Lane 1 — Surge (speed + crits) | Lane 2 — Tempest (shock + utility) |
|---|---|---|
| **Inner cluster** | Atk Speed %, Crit Chance | Shock Chance, Move Speed % |
| **Inner notable** | Atk Speed %, Crit Chance, Lightning Dmg % | Damage vs Shocked %, Shock Chance |
| **Outer cluster** | Stamina Regen, Atk Speed % | Crit Chance, Move Speed % |
| **Outer notable** | Lightning Penetration, Atk Speed % | Lightning Penetration, Crit Chance |

#### EARTH (Unbreakable Wall)

| | Lane 1 — Bastion (raw durability) | Lane 2 — Rampart (active defense) |
|---|---|---|
| **Inner cluster** | Max HP %, Armor | Block Chance, KB Resistance |
| **Inner notable** | Max HP %, Armor, Physical Resistance | Block Chance, Armor, Earth Dmg % |
| **Outer cluster** | HP Regen, Armor | Max HP %, KB Resistance |
| **Outer notable** | HP Regen %, KB Resistance, Health Recovery | Block Dmg Reduction, Block Heal % |

#### VOID (Parasite)

| | Lane 1 — Blight (sustain-through-damage) | Lane 2 — Entropy (DoT + debuff) |
|---|---|---|
| **Inner cluster** | Life Steal, True Dmg % | DoT Dmg %, Status Duration |
| **Inner notable** | Life Steal, Life Leech, True Dmg % | Status Duration, Void Penetration |
| **Outer cluster** | True Dmg %, Life Steal | DoT Dmg %, Mana on Kill |
| **Outer notable** | True Dmg %, Life Leech, Mana on Kill | DoT Dmg %, Void Penetration |

#### WIND (Ghost)

| | Lane 1 — Gale (projectile offense) | Lane 2 — Zephyr (evasion + mobility) |
|---|---|---|
| **Inner cluster** | Proj Dmg %, Accuracy | Evasion, Jump Force % |
| **Inner notable** | Proj Dmg %, Wind Dmg %, Wind Penetration | Evasion, Move Speed %, Dodge Chance |
| **Outer cluster** | Proj Speed %, Accuracy | Evasion, Jump Force % |
| **Outer notable** | Proj Dmg %, Wind Penetration, Accuracy | Evasion %, Proj Speed %, Jump Force |

### 7.7 Octant Arm Stat Curation

Octant arms have access to 15 stats (5 per neighbor). The key rule: **each cluster uses 2-3 curated stats from the neighbors, chosen to serve the cluster's lane theme.** No cluster uses all 15 randomly.

The curation follows from the archetype: which of the 15 available stats best serve THIS octant's combat fantasy?

#### HAVOC (Fire + Void + Lightning) — Berserker Assassin

**Available pool** (15): Phys Dmg%, Charged Atk%, Crit Mult, Burn Dmg%, Ignite Chance, Life Steal, True Dmg%, DoT Dmg%, Mana on Kill, Status Duration, Atk Speed%, Move Speed%, Crit Chance, Stamina Regen, Shock Chance

**Lane 1 — Carnage (fast crits)**: Crit Mult (Fire), Atk Speed% (Lightning), Crit Chance (Lightning), Phys Dmg% (Fire)
**Lane 2 — Ruin (DoT sustain)**: DoT Dmg% (Void), Life Steal (Void), Burn Dmg% (Fire), Ignite Chance (Fire)

**Excluded from basics** (available but not used in this octant's basics): Charged Atk%, True Dmg%, Mana on Kill, Status Duration, Move Speed%, Stamina Regen, Shock Chance. These don't serve Havoc's "fast crit berserker" identity — they belong to other octants.

**Notables should add**: Armor Penetration, Execute Damage, conditional on-kill bonuses.

#### JUGGERNAUT (Fire + Void + Earth) — War Machine

**Available pool** (15): Phys Dmg%, Charged Atk%, Crit Mult, Burn Dmg%, Ignite Chance, Life Steal, True Dmg%, DoT Dmg%, Mana on Kill, Status Duration, Max HP%, Armor, HP Regen, Block Chance, KB Resistance

**Lane 1 — Conquest (armored warrior)**: Phys Dmg% (Fire), Max HP% (Earth), Armor (Earth), Charged Atk% (Fire)
**Lane 2 — Bloodforge (sustain + retaliation)**: Life Steal (Void), Block Chance (Earth), HP Regen (Earth), Burn Dmg% (Fire)

**Excluded**: True Dmg%, DoT Dmg%, Mana on Kill, Status Duration, Crit Mult, Ignite Chance, KB Resistance (narrow, moved to synergies where relevant)

**Notables should add**: Thorns Damage, Block Heal, HP-based effects.

#### STRIKER (Fire + Wind + Lightning) — Blade Dancer

**Available pool** (15): Phys Dmg%, Charged Atk%, Crit Mult, Burn Dmg%, Ignite Chance, Evasion, Accuracy, Proj Dmg%, Jump Force%, Proj Speed%, Atk Speed%, Move Speed%, Crit Chance, Stamina Regen, Shock Chance

**Lane 1 — Quicksilver (speed + crit)**: Atk Speed% (Lightning), Crit Chance (Lightning), Crit Mult (Fire), Move Speed% (Lightning)
**Lane 2 — Precision (evasion + counter)**: Evasion (Wind), Accuracy (Wind), Phys Dmg% (Fire), Stamina Regen (Lightning)

**Excluded**: Charged Atk%, Burn Dmg%, Ignite Chance, Proj Dmg%, Jump Force%, Proj Speed%, Shock Chance — these serve other archetypes.

**Notables should add**: Dodge Chance, on-evade triggers, consecutive hit synergy.

#### WARDEN (Fire + Wind + Earth) — Iron Ranger

**Available pool** (15): Phys Dmg%, Charged Atk%, Crit Mult, Burn Dmg%, Ignite Chance, Evasion, Accuracy, Proj Dmg%, Jump Force%, Proj Speed%, Max HP%, Armor, HP Regen, Block Chance, KB Resistance

**Lane 1 — Garrison (fortified defense)**: Armor (Earth), Block Chance (Earth), Max HP% (Earth), Charged Atk% (Fire)
**Lane 2 — Outrider (ranged offense)**: Proj Dmg% (Wind), Accuracy (Wind), Proj Speed% (Wind), Phys Dmg% (Fire)

**Excluded**: Crit Mult, Burn Dmg%, Ignite Chance, Evasion, Jump Force%, HP Regen, KB Resistance

**Notables should add**: Block-on-projectile-hit, charged shot bonuses, fortified position conditionals.

#### WARLOCK (Water + Void + Lightning) — Dark Caster

**Available pool** (15): Spell Dmg%, Max Mana, ES%, Mana Regen, Freeze Chance, Life Steal, True Dmg%, DoT Dmg%, Mana on Kill, Status Duration, Atk Speed%, Move Speed%, Crit Chance, Stamina Regen, Shock Chance

**Lane 1 — Hex (offensive spellcasting)**: Spell Dmg% (Water), Crit Chance (Lightning), DoT Dmg% (Void), Mana on Kill (Void)
**Lane 2 — Ritual (resource drain)**: Max Mana (Water), Mana Regen (Water), Life Steal (Void), Atk Speed% (Lightning)

**Excluded**: ES%, Freeze Chance, True Dmg%, Status Duration, Move Speed%, Stamina Regen, Shock Chance

**Notables should add**: Mana Leech, Spell Penetration, status effect chance bonuses.

#### LICH (Water + Void + Earth) — Undying Sorcerer

**Available pool** (15): Spell Dmg%, Max Mana, ES%, Mana Regen, Freeze Chance, Life Steal, True Dmg%, DoT Dmg%, Mana on Kill, Status Duration, Max HP%, Armor, HP Regen, Block Chance, KB Resistance

**Lane 1 — Grasp (DoT + spell)**: Spell Dmg% (Water), DoT Dmg% (Void), ES% (Water), Status Duration (Void)
**Lane 2 — Crypt (layered defense)**: Max HP% (Earth), Armor (Earth), HP Regen (Earth), Mana Regen (Water)

**Excluded**: Max Mana, Freeze Chance, Life Steal, True Dmg%, Mana on Kill, Block Chance, KB Resistance

**Notables should add**: Mana as Damage Buffer, ES regen, DoT-triggered defense.

#### TEMPEST (Water + Wind + Lightning) — Battle Mage

**Available pool** (15): Spell Dmg%, Max Mana, ES%, Mana Regen, Freeze Chance, Evasion, Accuracy, Proj Dmg%, Jump Force%, Proj Speed%, Atk Speed%, Move Speed%, Crit Chance, Stamina Regen, Shock Chance

**Lane 1 — Squall (spell + speed)**: Spell Dmg% (Water), Atk Speed% (Lightning), Crit Chance (Lightning), Max Mana (Water)
**Lane 2 — Tailwind (mobility + projectile)**: Move Speed% (Lightning), Proj Dmg% (Wind), Evasion (Wind), Proj Speed% (Wind)

**Excluded**: ES%, Mana Regen, Freeze Chance, Accuracy, Jump Force%, Stamina Regen, Shock Chance

**Notables should add**: Speed-to-spell synergies, projectile spell bonuses, mobility-triggered effects.

#### SENTINEL (Water + Wind + Earth) — Guardian

**Available pool** (15): Spell Dmg%, Max Mana, ES%, Mana Regen, Freeze Chance, Evasion, Accuracy, Proj Dmg%, Jump Force%, Proj Speed%, Max HP%, Armor, HP Regen, Block Chance, KB Resistance

**Lane 1 — Aegis (block + shield)**: Block Chance (Earth), ES% (Water), Max HP% (Earth), Armor (Earth)
**Lane 2 — Vigilance (evasion + awareness)**: Evasion (Wind), Accuracy (Wind), HP Regen (Earth), Mana Regen (Water)

**Excluded**: Spell Dmg%, Max Mana, Freeze Chance, Proj Dmg%, Jump Force%, Proj Speed%, KB Resistance

**Notables should add**: Crit Nullify, Elemental Resistance (all), Thorns, Reflect, Evasion-to-Armor synergies.

### 7.8 Stat Placement Validation Rules

These are checkable invariants that every node in the final tree must satisfy:

1. **Basic node uses only identity stats** — For elemental arms: only that element's 5 stats. For octant arms: only stats from the curated subset in section 7.7.
2. **Notable adds at least one non-identity stat** — Penetration, conversion, conditional, broader combat stat, or element-specific damage.
3. **No flat elemental damage on basic nodes** — Flat damage is gear's domain. Tree basics use % only.
4. **Penetration never on basics** — Notable-tier minimum.
5. **Conversion never on basics** — Notable-tier minimum (keystone for large values like 40%+).
6. **MORE multipliers only on keystones** — Never on basics, notables, or synergies.
7. **Derivation stats only on keystones** — HP→damage, speed→spell, evasion→armor.
8. **On-event mechanics only on keystones or notables with conditional field** — Basics never have conditional triggers.
9. **No cross-element stats on elemental basics** — Fire basic cannot grant Water Penetration.
10. **Octant basics draw only from their 3 neighbors** — Havoc basic cannot grant Earth stats.
11. **Keystones always have drawbacks** — No free lunches.
12. **Drawbacks never use movement speed** — Per design decision.
13. **Each arm uses all its identity stats** — No identity stat should be absent from its arm's basics.
14. **Synergy hubs are unique per region** — No two hubs grant the same modifiers.

### 7.9 Bridge Node Rules

Bridges connect adjacent elemental arms. Each bridge has 3 nodes.

**Bridge stat rules**:
- Bridge nodes grant stats from BOTH connected elements
- Bridge tier 1: small % bonus from each element (identity stats, one per element)
- Bridge tier 2: slightly larger %, can introduce one cross-element synergy stat
- Bridge tier 3: the bridge "payoff" — a notable-tier combination or conditional

**Current problem**: All bridges grant flat elemental damage (+5 Fire, +5 Lightning). Flat damage should be minimal in the tree.
**Fix**: Replace flat damage with % bonuses from each connected element's identity stats. Bridge tier 3 can introduce a cross-element notable-tier effect.

### 7.10 Summary: What Makes The Tree Feel Distinct

After this rework, the three sources of power will feel clearly different:

| Layer | What It Feels Like |
|-------|-------------------|
| **Attributes** | "I'm a Fire/Earth hybrid" — broad identity, linear scaling, reliable |
| **Gear** | "I found an amazing sword with +crit and fire penetration" — item-hunting, RNG-driven, flat+% |
| **Skill Tree** | "I specialize in freeze-shatter with mana-as-power" — chosen specialization, unique mechanics, build-defining |

The tree is where builds diverge. Two players with 50 Fire / 50 Earth attributes and similar gear will play DIFFERENTLY based on their tree choices — one might be a crit-execute specialist (Fire Lane 1 → Havoc), the other a block-counter tank (Earth Lane 2 → Juggernaut).

---

## 8. Phase 3 — Keystone & Nexus Design

### 8.1 Keystone Design Philosophy

A keystone isn't a "big notable." It's a **paradigm shift** — it changes HOW your character works, not just how much damage they deal.

**The test**: Can you explain a keystone without numbers? If you can only say "it gives more damage," it's a stat stick. If you can say "it makes your crits detonate all active DoTs as a single burst," that's a mechanic.

**Each keystone should pass 3 checks**:
1. **Identity**: Does it create a build that plays differently from any other keystone?
2. **Trade-off**: Does the drawback force a real decision, not just "slightly less of something you don't use"?
3. **Synergy**: Does it reward investment in this arm's identity stats specifically?

**Two keystones per arm, serving different fantasies**:
- **KS1**: The arm's "signature move" — the mechanic most central to the arm's identity
- **KS2**: The arm's "alternate build" — a different way to express the same element/archetype

---

### 8.2 Elemental Arm Keystones (12)

#### FIRE — The Berserker

**KS1: Inferno Master** — *Elemental Conversion Specialist*
- **Mechanic**: Convert 50% of Physical damage to Fire. Gain +15% Fire Damage Multiplier (MORE).
- **Drawback**: -25% Max HP
- **Fantasy**: You're no longer a physical fighter — you're a fire warrior. Every swing deals fire damage. Armor is useless against you, but fire resistance hurts.
- **Synergy**: Rewards Fire attribute investment (fire penetration, fire damage %) and fire tree notables. Combines with Burn DoT lane for additional fire-scaled damage.
- **Why this works**: Conversion is one of the deepest ARPG mechanics. It changes which defenses matter, which penetration you want, which elements you stack. This one decision reshapes your entire gear priority.

**KS2: Berserker's Rage** — *Pain Fuels Power*
- **Mechanic**: Your damage increases by 1% for every 2% of HP you're missing (at 50% HP = +25% damage, at 10% HP = +45% damage). Attacks you deal cost 3% of your current HP.
- **Drawback**: Attacks cost HP. Cannot life steal above 50% HP.
- **Fantasy**: You hurt yourself to become stronger. Every swing drains your life, but the lower you go, the harder you hit. You're constantly managing a death spiral — one wrong move and you die, but played right you're the highest damage in the game.
- **Synergy**: Life steal from Void keeps you alive but can't bring you above 50%, creating a sweet spot. Fire's burst damage matters more because each swing costs HP — you want fewer, harder hits.
- **Why this works**: It creates active gameplay tension every fight. You're watching your HP, deciding whether to keep attacking or retreat. The self-damage + missing-HP-scaling creates a loop no other keystone has.

---

#### WATER — The Arcane Controller

**KS1: Glacial Mastery** — *Shatter Specialist*
- **Mechanic**: Hitting a Frozen target SHATTERS the freeze, dealing burst Water damage equal to 200% of the hit that froze them. Shattered targets cannot be re-frozen for 4s. +15% Freeze Chance.
- **Drawback**: -20% damage against non-Frozen targets
- **Fantasy**: You freeze, then shatter. The shatter burst is devastating but then you have a 4s window where you deal reduced damage (can't re-freeze, and non-frozen penalty applies). Creates a rhythmic freeze→shatter→wait→freeze cycle.
- **Synergy**: Freeze chance from Water attribute makes the cycle faster. Crit multiplier from Fire amplifies the initial hit that gets stored as shatter damage. Attack speed from Lightning shortens the wait between cycles.
- **Why this works**: The shatter mechanic creates a TIMING game. You want your biggest hit to be the one that applies freeze (so the stored shatter is huge), then any subsequent hit triggers the burst. The 4s cooldown prevents mindless spam and creates windows of vulnerability.

**KS2: Arcane Reservoir** — *Mana-as-Power*
- **Mechanic**: Your maximum mana adds to your spell damage at a rate of 1% spell damage per 5 mana. +25% Max Mana.
- **Drawback**: +30% Mana Cost on all spells/abilities
- **Fantasy**: Your mana pool IS your power. The bigger your mana pool, the harder you hit. But everything costs more — mana management becomes your core gameplay.
- **Synergy**: Rewards Water attribute (max mana, mana regen) and mana-stacking gear. Creates a distinct "big mana pool" build identity.
- **Why this works**: It turns a defensive/utility resource (mana) into an offensive stat, creating a fundamentally different gearing priority. You want max mana on every gear piece now.

---

#### LIGHTNING — The Storm Dancer

**KS1: Thundergod** — *Chain Lightning*
- **Mechanic**: When you Shock a target, the shock CHAINS to 1 nearby enemy within 8 blocks, applying 50% of the original shock. Chained shocks can chain once more (3 targets total). +15% Shock Chance.
- **Drawback**: -30 Armor, -15% single-target damage
- **Fantasy**: You're a walking storm. Hit one enemy and lightning arcs to its allies. In dense mob packs, one hit can shock 3 targets. But your single-target damage is lower — you're an AoE specialist, not a boss killer.
- **Synergy**: Shock chance from Lightning attribute makes chains trigger more often. Attack speed means more initial hits = more chain procs. Status duration from Void extends the chained shocks.
- **Why this works**: It changes your TARGET PRIORITY. You aim for the center of a pack, not the most dangerous enemy. Single-target penalty forces a real build trade-off. The chain mechanic is unique to this keystone.

**KS2: Overcharge** — *Speed Overflow*
- **Mechanic**: Attack Speed above 30% bonus is converted to Spell Damage % at a 1:1 ratio. +15% Attack Speed.
- **Drawback**: -20% Max HP
- **Fantasy**: You're so fast that excess speed becomes magical power. Stack attack speed beyond what you need for attacks, and the overflow fuels your spells.
- **Synergy**: Rewards Lightning attribute (attack speed) and creates a natural Lightning/Water hybrid (speed→spell power). Gear with attack speed becomes dual-purpose.
- **Why this works**: The 30% threshold means you need real investment before the conversion kicks in. It creates a breakpoint that players optimize around — "I need 30% attack speed before this keystone does anything for spells."

---

#### EARTH — The Unbreakable Wall

**KS1: Living Fortress** — *Immovable Object*
- **Mechanic**: While standing still (not moving for 1s+), you gain Fortified state: +50% Armor, +15% Block Chance, and blocked attacks restore 5% of blocked damage as HP. Moving breaks Fortified instantly.
- **Drawback**: -25% All Damage. Fortified only while stationary.
- **Fantasy**: You plant yourself and become nearly indestructible. Moving means losing everything. You're a siege tower — devastating in a defensive position, vulnerable while repositioning. Every fight has a decision: do I move to dodge this attack, or do I stand and tank it?
- **Synergy**: Armor from Earth attribute is amplified 1.5x while Fortified. Block chance stacks with the bonus. HP regen during Fortified is very high.
- **Why this works**: The stationary requirement creates genuine moment-to-moment decisions. It's not just "always tanky" — it's "tanky when I CHOOSE to commit to a position." This is a real behavioral change, not a stat buff.

**KS2: Tectonic Bulwark** — *HP-as-Offense*
- **Mechanic**: Gain bonus Physical Damage % equal to 1% per 50 Max HP above 200. +20% Max HP. Thorns damage equals 20% of your Armor.
- **Drawback**: -30% Evasion, -15% Attack Speed
- **Fantasy**: The bigger you are, the harder you hit. Your massive HP pool directly translates to damage. Thorns punish anyone who attacks you.
- **Synergy**: Rewards Earth attribute (max HP, armor) and HP-stacking gear. At 500 HP → +6% phys damage. At 1000 HP → +16%. Scales with investment.
- **Why this works**: It's the "tanky bruiser" keystone — you trade speed and dodge for raw HP that doubles as offense. Different from Living Fortress (which is pure defense) because you WANT to be attacked (thorns).

---

#### VOID — The Parasite

**KS1: Void Walker** — *Between Worlds*
- **Mechanic**: Convert 50% of all damage to Void. When you deal Void damage, 15% of it ignores ALL defenses (treated as True Damage). Void DoTs you inflict also apply this 15% true conversion.
- **Drawback**: -20% Max HP, -5% All Elemental Resistance
- **Fantasy**: You reach into the void with every attack. Half your damage becomes void (bypassing armor, hitting resistances), and a portion of that void damage goes even further — bypassing EVERYTHING. Against any target, you always deal some irreducible damage.
- **Synergy**: Void penetration from tree reduces the resistance portion. DoT from Void attribute feeds the true damage conversion over time. Life steal sustains through the HP penalty.
- **Why this works**: Unlike Inferno Master (which just changes which defense matters), Void Walker adds a TRUE DAMAGE component that makes your damage partially unblockable. That's a fundamentally different relationship with enemy defenses — you don't care what they stack, some damage always gets through.

**KS2: Parasitic Link** — *Sustain Through Violence*
- **Mechanic**: +10% Life Leech (all damage heals you). +10% of all damage dealt is added as True Damage.
- **Drawback**: +20% Damage Taken. Cannot regenerate HP passively (HP regen set to 0).
- **Fantasy**: You are a vampire. You CANNOT heal passively — your only healing is through damage dealt. Stop attacking and you die. But while attacking, you're nearly immortal.
- **Synergy**: Rewards Void attribute (life steal, true damage) and forces aggressive playstyle. The "no regen" drawback is the most extreme trade-off in the tree — it fundamentally changes how you play.
- **Why this works**: Setting HP regen to 0 is a PoE-style keystone mechanic (like Blood Magic or Chaos Inoculation). It's not just "less" — it's "never." That's what makes it a real paradigm shift.

---

#### WIND — The Ghost

**KS1: Phantom** — *Untouchable*
- **Mechanic**: +30% Evasion. When you evade an attack, gain +15% Damage and +10% Movement Speed for 3s (refreshes on evade).
- **Drawback**: -35 Armor (flat), -20% Max HP
- **Fantasy**: You can't be hit, and every dodge makes you stronger. But if something DOES connect, it's devastating — you have no armor and low HP.
- **Synergy**: Rewards Wind attribute (evasion) and Lightning attribute (movement speed). The evade→buff cycle rewards high evasion investment with both offense and mobility.
- **Why this works**: The conditional buff on evade creates a gameplay loop — you WANT enemies to attack you (and miss). High evasion makes the buff near-permanent. Low armor means the rare hit that lands really hurts.

**KS2: Sky Piercer** — *Marksman's Focus*
- **Mechanic**: After not attacking for 1.5s, your next projectile attack becomes a Focused Shot: deals 80% bonus damage, ignores 20% of all enemy resistances, and has guaranteed accuracy (cannot miss). Only one Focused Shot per charge cycle.
- **Drawback**: -30% Melee Damage, -20% Attack Speed
- **Fantasy**: You're a sniper. You wait, aim, and fire one devastating shot. Then you reposition and do it again. Fast spamming is penalized (attack speed reduction), but each deliberate shot is massively rewarded.
- **Synergy**: Projectile damage from Wind amplifies the focused shot. Accuracy is guaranteed so you can invest elsewhere. The attack speed penalty means you naturally have the 1.5s gap between shots.
- **Why this works**: It creates a HIT-AND-RUN rhythm — shoot, move, wait, shoot. The 1.5s charge window means you can't just spam. It rewards patience and positioning, which is the sniper fantasy.

---

### 8.3 Octant Arm Keystones (16)

Octant keystones MUST offer mechanics you can't get from any elemental arm. They're the reason to path deep into an octant.

#### HAVOC (Fire + Void + Lightning) — Berserker Assassin

**KS1: Rampage** — *Kill Chain Escalation*
- **Mechanic**: On Kill: gain a Rampage stack (max 5, lasts 6s, refreshes per kill). Each stack grants +8% Attack Speed, +4% Crit Multiplier, +2% DoT Damage.
- **Drawback**: -15% Max HP
- **Fantasy**: The more you kill, the more unstoppable you become. A 5-stack Rampage gives +40% attack speed, +20% crit mult, +10% DoT. But it requires CONTINUOUS killing — if combat stops, you lose everything.
- **Why octant-exclusive**: No single element does "kill chain stacking." It requires Fire's damage, Void's DoT, AND Lightning's speed working together.

**KS2: Chain Detonation** — *DoT Burst Specialist*
- **Mechanic**: Critical hits against targets with active DoTs instantly deal 100% of remaining DoT damage as burst Void damage, then clear the DoTs.
- **Drawback**: -30% Status Effect Duration, -10% Crit Chance
- **Fantasy**: You apply DoTs (burn, poison, bleed), then detonate them with a crit. The burst damage can be enormous if multiple DoTs are stacked. But your DoTs last shorter (less time to detonate) and you crit less often (each detonation is a bigger decision).
- **Why octant-exclusive**: Requires Fire's burn application + Void's DoT scaling + Lightning's crit chance. The interplay between ailment application and crit timing is a mechanical depth no single element offers.

---

#### JUGGERNAUT (Fire + Void + Earth) — War Machine

**KS1: Blood Fortress** — *Vampiric Aegis*
- **Mechanic**: When you Block an attack, your next 3 attacks within 4s gain Life Steal equal to 15% of the blocked damage. Successful blocks also grant a Blood Shield (10% of blocked damage as temporary HP lasting 5s, stacks up to 30% Max HP).
- **Drawback**: -25% Attack Speed. Life Steal from other sources reduced by 50%.
- **Fantasy**: Blocking doesn't just protect you — it charges your offense. Block a big hit, then your next swings heal massively. The temporary HP from Blood Shield means you WANT to get hit (and block it). The attack speed penalty means fewer swings to spend your charges, making each one count.
- **Why octant-exclusive**: Block (Earth) + Life Steal conversion (Void) + physical damage to capitalize on the charged swings (Fire). The block→charge→heal loop doesn't exist in any single element.

**KS2: Colossus** — *HP Scales Everything*
- **Mechanic**: Gain +1% Physical Damage per 50 Max HP above 200. +15% Max HP.
- **Drawback**: -25% Attack Speed, -50 Evasion (flat)
- **Fantasy**: You are massive, slow, and every swing hits like a truck. 1000 HP = +16% physical damage. Stack HP items and you become a wrecking ball.
- **Why octant-exclusive**: HP scaling (Earth) → Physical damage (Fire) with Void's sustain keeping you alive. The "slow powerhouse" archetype doesn't exist in any pure element.

---

#### STRIKER (Fire + Wind + Lightning) — Blade Dancer

**KS1: Blade Dance** — *Evade-to-Crit Conversion*
- **Mechanic**: When you evade an attack, your next hit within 2s is a guaranteed Critical Strike with +25% bonus Crit Multiplier.
- **Drawback**: -25 Armor (flat), -15% Max HP
- **Fantasy**: Every dodge is a setup. Evade, then strike with a guaranteed devastating crit. High skill expression — you want enemies attacking you (and missing).
- **Why octant-exclusive**: Evasion (Wind) → Guaranteed Crit (Lightning) → Amplified Crit Multiplier (Fire). The dodge→crit counter-attack is Striker's unique identity.

**KS2: Momentum** — *Combo Counter*
- **Mechanic**: Each consecutive hit within 2s grants a stack of Momentum (+3% damage per stack, max 10 = +30%). All stacks lost if 2s passes without hitting.
- **Drawback**: -20% Max HP
- **Fantasy**: Sustained pressure rewarded. Don't stop swinging — every hit makes the next stronger. Miss or pause, and you start over.
- **Why octant-exclusive**: Attack speed (Lightning) + accuracy (Wind) + raw damage scaling (Fire). The combo mechanic rewards the fast-and-precise identity of Striker.

---

#### WARDEN (Fire + Wind + Earth) — Iron Ranger

**KS1: Earthen Volley** — *Fortified Barrage*
- **Mechanic**: While Blocking (holding shield), you can still fire projectile attacks at -40% projectile damage. Each projectile hit while blocking grants a Bulwark stack (+5 Armor for 8s, max 6 stacks = +30 Armor). Bulwark stacks persist after you stop blocking.
- **Drawback**: -20% Spell Damage, -10% Melee Damage
- **Fantasy**: You hold your shield up AND shoot at the same time. Other players choose "block OR attack" — you do both. The armor stacks reward sustained defensive firing. You're a turret behind a shield wall.
- **Why octant-exclusive**: Block (Earth) + Projectile attacks (Wind) + physical damage scaling (Fire). "Block and shoot simultaneously" is mechanically unique and only makes sense at this intersection.

**KS2: Stalwart Counter** — *Block Reflects Damage*
- **Mechanic**: Successful blocks deal 150% of the blocked damage back to the attacker as Physical damage. +10% Block Chance.
- **Drawback**: -20% Spell Damage, -20% Attack Speed
- **Fantasy**: Enemies kill themselves by attacking you. Your offense IS your defense. Stack block chance and you become a damage mirror.
- **Why octant-exclusive**: Block (Earth) + counter damage scaled by physical power (Fire) + accuracy ensuring the counter connects (Wind). Pure defense that deals offense.

---

#### WARLOCK (Water + Void + Lightning) — Dark Caster

**KS1: Soul Siphon** — *Spell Vampire*
- **Mechanic**: Spell kills fully restore your mana. Spell critical hits restore 8% of damage dealt as HP. Overkill damage on spell kills is stored as Siphon Charge (up to 50% of your Max HP), which decays at 10%/s and is added as bonus Spell Damage % on your next spell cast.
- **Drawback**: -20% Max HP, -15 Armor (flat). Non-spell attacks deal 30% less damage.
- **Fantasy**: You're a dark mage who feeds on death. Kill with a spell → full mana, ready to cast again. Overkill something with a massive crit → the excess damage charges your next spell. You're a chain-caster who gets stronger with each kill, but only through spells — weapon attacks are gutted.
- **Why octant-exclusive**: Spell damage (Water) + overkill/sustain loop (Void) + crit to trigger the HP restore (Lightning). Forces spell-only combat — a true caster commitment.

**KS2: Arcane Overload** — *Spell Echo*
- **Mechanic**: Spell/magic damage has a 25% chance to repeat as 50% bonus Void damage. +10% Spell Damage.
- **Drawback**: -20% Max Mana, +15% Mana Cost
- **Fantasy**: Your spells sometimes fire twice. Devastating bursts, but expensive — mana management is critical. The echo deals Void damage, rewarding void penetration investment.
- **Why octant-exclusive**: Spell base (Water) + void damage type (Void) + proc frequency via speed (Lightning). No single element creates "spell echo."

---

#### LICH (Water + Void + Earth) — Undying Sorcerer

**KS1: Plague Resilience** — *Ailment = Defense*
- **Mechanic**: For each unique ailment type you've inflicted on enemies (burn, freeze, shock, poison), gain +4% All Elemental Resistance (max 4 types = +16%). +15% Max HP.
- **Drawback**: -20% Attack Speed, -10% Crit Chance
- **Fantasy**: The more you curse, the tougher you become. Spread ailments across enemies and you become resistant to everything. Rewards multi-ailment builds.
- **Why octant-exclusive**: DoT/ailments (Void) + durability (Earth) + magical framework (Water). The "I'm tanky BECAUSE I'm cursing" loop.

**KS2: Undying Shell** — *DoT Restores Shield*
- **Mechanic**: DoTs you've inflicted restore your Energy Shield at 5% of their tick damage per tick. +25% Energy Shield.
- **Drawback**: -25 Evasion (flat), -10% Attack Speed
- **Fantasy**: Your DoTs are your shield generator. Keep DoTs active on enemies and your ES regenerates continuously. Rewards spreading DoTs to multiple targets.
- **Why octant-exclusive**: ES (Water) + DoT ownership (Void) + durability backbone (Earth). The "my damage IS my defense" loop, but magical rather than Juggernaut's physical version.

---

#### TEMPEST (Water + Wind + Lightning) — Battle Mage

**KS1: Arcane Velocity** — *Speed Powers Spells*
- **Mechanic**: 50% of your total Attack Speed bonus is added as Spell Damage %. +10% Attack Speed.
- **Drawback**: -25 Armor (flat), -10% Block Chance
- **Fantasy**: You attack fast, and that speed directly fuels your spell power. Attack speed becomes a dual-purpose stat — faster swings AND stronger spells.
- **Why octant-exclusive**: Attack speed (Lightning) → Spell power conversion (Water) + mobility to survive while casting (Wind). The "fast mage" identity.

**KS2: Storm Runner** — *Motion Powers Spells*
- **Mechanic**: 40% of your total Movement Speed bonus is added as Spell Damage %. +10% Movement Speed.
- **Drawback**: -25% Max HP
- **Fantasy**: You must keep moving to be powerful. Standing still means weak spells. Running around the battlefield means devastating casts. The mobile mage fantasy perfected.
- **Why octant-exclusive**: Movement (Lightning+Wind) → Spell power (Water). Rewards a playstyle where you literally run in circles casting. Unique gameplay loop.

---

#### SENTINEL (Water + Wind + Earth) — The Guardian

**KS1: Fortress Aura** — *Evasion Becomes Armor*
- **Mechanic**: 25% of your Evasion rating is added as bonus Armor. +15% Block Chance.
- **Drawback**: -20% Damage Multiplier (MORE reduction)
- **Fantasy**: You convert agility into raw toughness. With high evasion from Wind investment, you gain massive bonus armor on top of Earth's base armor. Defense layering taken to the extreme.
- **Why octant-exclusive**: Evasion (Wind) → Armor (Earth) + block (Earth) + ES (Water). No single element creates "evasion converts to armor."

**KS2: Adaptive Guard** — *Ailment Immunity*
- **Mechanic**: When you're hit by an ailment (burn/freeze/shock/poison), gain +80% resistance to that element for 5s. +10% HP Regen.
- **Drawback**: -15% Fire Damage, -15% Water Damage, -15% Lightning Damage
- **Fantasy**: What doesn't kill you makes you immune. First hit of each ailment type hurts, but then you resist it. In multi-element fights, you progressively become invulnerable to everything.
- **Why octant-exclusive**: Multi-defense layering (Earth+Water+Wind). The damage penalty affects three elements — you trade offense for reactive defense.

---

### 8.4 Nexus Hub Design (14 unique effects)

Nexus hubs are the gateway to keystones. They MUST be unique per arm — no more identical "+3% to three elements."

**Design principle for elemental nexus hubs**: Each hub grants a thematic dual-stat bonus that captures the arm's identity. One offensive + one defensive/utility stat.

**Design principle for octant nexus hubs**: Each hub grants a **unique mechanic** that scales with the player's attribute points in the octant's 3 elements. This is the octant's unique value proposition — the reward for multi-element attribute investment.

#### Elemental Nexus Hubs (6)

| Hub | Name | Effect | Design Rationale |
|-----|------|--------|-----------------|
| **Fire** | Heart of Fire | +5% Fire Damage, +5% Physical Damage | Fire's dual identity: element + physical. Simple, clean. |
| **Water** | Heart of Water | +5% Water Damage, +3 Mana Regen | Water's dual identity: element + mana economy. |
| **Lightning** | Heart of Lightning | +5% Lightning Damage, +5% Attack Speed | Lightning's dual identity: element + speed. |
| **Earth** | Heart of Earth | +5% Earth Damage, +5% Max HP | Earth's dual identity: element + durability. **Fixed**: was duplicate HP%. |
| **Void** | Heart of Void | +5% Void Damage, +5% DoT Damage | Void's dual identity: element + damage-over-time. |
| **Wind** | Heart of Wind | +5% Wind Damage, +5% Projectile Damage | Wind's dual identity: element + ranged. |

#### Octant Nexus Hubs (8) — Unique Scaling Effects

Each octant nexus grants a unique bonus that scales with the **sum of attribute points** in its 3 elements. This rewards deep multi-element investment. The scaling rate is tuned so the bonus is noticeable at 30 total points (10+10+10) and significant at 90+ total points (30+30+30).

| Hub | Name | Scaling Mechanic | At 30pts | At 90pts | At 150pts |
|-----|------|-----------------|----------|----------|-----------|
| **Havoc** | Nexus of Havoc | **Rampage Power**: +0.1% Crit Multiplier per point in (Fire+Void+Lightning) | +3% Crit Mult | +9% | +15% |
| **Juggernaut** | Nexus of Juggernaut | **Unbreakable**: +0.15% Max HP per point in (Fire+Void+Earth) | +4.5% HP | +13.5% | +22.5% |
| **Striker** | Nexus of Striker | **Blade Tempo**: +0.1% Attack Speed per point in (Fire+Wind+Lightning) | +3% Atk Spd | +9% | +15% |
| **Warden** | Nexus of Warden | **Fortified Range**: +0.15% Projectile Damage per point in (Fire+Wind+Earth) | +4.5% Proj | +13.5% | +22.5% |
| **Warlock** | Nexus of Warlock | **Dark Knowledge**: +0.15% Spell Damage per point in (Water+Void+Lightning) | +4.5% Spell | +13.5% | +22.5% |
| **Lich** | Nexus of Lich | **Necrotic Bond**: +0.2 Energy Shield per point in (Water+Void+Earth) | +6 ES | +18 ES | +30 ES |
| **Tempest** | Nexus of Tempest | **Storm Surge**: +0.1% All Damage per point in (Water+Wind+Lightning) | +3% All Dmg | +9% | +15% |
| **Sentinel** | Nexus of Sentinel | **Guardian Aura**: +0.1% All Elemental Resistance per point in (Water+Wind+Earth) | +3% All Res | +9% | +15% |

**Why this design works**:
1. **Each nexus is unique** — no two grant the same stat
2. **Scales with attributes** — rewards players who invest attribute points in all 3 of the octant's elements
3. **The stat matches the archetype** — Havoc gets crit mult (aggressive), Sentinel gets all resistance (defensive), Tempest gets all damage (versatile)
4. **Differentiates from elemental hubs** — elemental hubs are flat bonuses, octant hubs are scaling bonuses. More points = more power. This is the reason to go deep into an octant.
5. **Creates build decisions** — a player with 50 Fire / 50 Lightning / 0 Void gets less from Havoc's nexus than one with 35/35/30. This rewards balanced multi-element builds in the octant's elements.

**Implementation note**: The nexus hub node needs a new synergy type (e.g., `ATTRIBUTE_SUM_SCALING`) that reads from `AttributeCalculator.getAttributePoints(ElementType)` for the 3 relevant elements. This is a new stat type that doesn't currently exist but follows the same pattern as `BRANCH_COUNT` and `ELEMENTAL_COUNT`.

---

### 8.5 Keystone Drawback Summary

Every keystone drawback, verified against the rule "never use movement speed":

| Arm | KS1 Drawback | KS2 Drawback |
|-----|-------------|-------------|
| Fire | -25% Max HP | Self-damage (3% HP/attack), no steal above 50% HP |
| Water | -20% dmg vs non-Frozen, 4s freeze cooldown | +30% Mana Cost |
| Lightning | -30 Armor, -15% single-target dmg | -20% Max HP |
| Earth | -25% Damage, stationary requirement | -30% Evasion, -15% Atk Speed |
| Void | -20% Max HP, -5% All Elem Resist | +20% Dmg Taken, 0 HP Regen |
| Wind | -35 Armor, -20% Max HP | -30% Melee Dmg, -20% Atk Speed |
| Havoc | -15% Max HP | -30% Status Duration, -10% Crit Chance |
| Juggernaut | -25% Atk Speed, -50% other life steal | -25% Atk Speed, -50 Evasion |
| Striker | -25 Armor, -15% Max HP | -20% Max HP |
| Warden | -20% Spell Dmg, -10% Melee Dmg | -20% Spell Dmg, -20% Atk Speed |
| Warlock | -20% Max HP, -15 Armor, -30% non-spell dmg | -20% Max Mana, +15% Mana Cost |
| Lich | -20% Atk Speed, -10% Crit Chance | -25 Evasion, -10% Atk Speed |
| Tempest | -25 Armor, -10% Block Chance | -25% Max HP |
| Sentinel | -20% Damage Multiplier | -15% Fire/Water/Lightning Dmg |

**Zero movement speed penalties.** Drawbacks are thematic. Several keystones now have BEHAVIORAL drawbacks (self-damage, stationary requirement, freeze cooldown, non-spell penalty) rather than just stat reductions — these create more interesting trade-offs.

---

## 9. Phase 4 — Cluster Design

### 9.1 Node Structure Reminder

Each cluster has 6 nodes in a diamond pattern:

```
[_1] → [_2] → [_branch] → [_notable]
                  ├─ [_3]
                  └─ [_4]
```

- `_1`: Entry basic (1 stat)
- `_2`: Progression basic (1 stat)
- `_branch`: Branch point basic (2 stats — split path)
- `_3`: Side path basic (1 stat)
- `_4`: Side path basic (1 stat)
- `_notable`: Cluster payoff (2-3 stats, at least one non-identity)

**Per arm**: 4 clusters (2 lanes × 2 depth levels) + 5 synergy + 2 keystones + 1 entry = 32 nodes.

### 9.2 Notation Convention

For each cluster below:
- **Stats** use shorthand: `PhysDmg%`, `CritMult`, `AtkSpd%`, etc.
- **[N]** marks a stat that's new/non-identity (notable-tier unlock)
- Entry node and synergy nodes are listed separately after the 4 clusters

---

### 9.3 Elemental Arms — Full Cluster Design

#### FIRE — The Berserker

**Entry**: `fire_entry` — +5% Physical Damage

**Cluster 1: Ignition** (Lane 1 — Burst Damage, Inner)
| Node | Stat(s) |
|------|---------|
| ignition_1 | +6% Physical Damage |
| ignition_2 | +5 Crit Multiplier |
| ignition_branch | +3% Physical Damage, +3 Crit Multiplier |
| ignition_3 | +6% Physical Damage |
| ignition_4 | +6 Crit Multiplier |
| **Ignition Notable: Searing Strikes** | +10% **[N]** Fire Damage, +8 **[N]** Fire Penetration |

**Cluster 2: Pyre** (Lane 2 — Ailment/DoT, Inner)
| Node | Stat(s) |
|------|---------|
| pyre_1 | +6% Burn Damage |
| pyre_2 | +4% Ignite Chance |
| pyre_branch | +3% Burn Damage, +2% Ignite Chance |
| pyre_3 | +6% Burn Damage |
| pyre_4 | +4% Ignite Chance |
| **Pyre Notable: Pyromaniac** | +12% **[N]** DoT Damage, +15% **[N]** Burn Duration |

**Cluster 3: Eruption** (Lane 1 — Burst Damage, Outer)
| Node | Stat(s) |
|------|---------|
| eruption_1 | +5% Charged Attack Damage |
| eruption_2 | +5% Physical Damage |
| eruption_branch | +3% Charged Attack Damage, +3% Physical Damage |
| eruption_3 | +7% Charged Attack Damage |
| eruption_4 | +6% Physical Damage |
| **Eruption Notable: Executioner** | +15% **[N]** Execute Damage, +5 Crit Multiplier |

**Cluster 4: Inferno** (Lane 2 — Ailment/DoT, Outer)
| Node | Stat(s) |
|------|---------|
| inferno_1 | +6% Burn Damage |
| inferno_2 | +4% Ignite Chance |
| inferno_branch | +3% Burn Damage, +2% Ignite Chance |
| inferno_3 | +5 Crit Multiplier |
| inferno_4 | +6% Burn Damage |
| **Inferno Notable: Blazing Fury** | +8 **[N]** Fire Penetration, +10% **[N]** Charged Attack Damage |

**Fire Synergies** (per 3 nodes allocated in Fire arm):
1. Strength in Numbers: +2% Physical Damage (cap 20%)
2. Infernal Mastery: +3% Fire Damage (cap 30%)
3. Burning Resolve: +0.5 Crit Multiplier (cap 5)
4. Charged Momentum: +1% Charged Attack Damage (cap 10%)

**Fire Hub**: Heart of Fire — +5% Fire Damage, +5% Physical Damage

---

#### WATER — The Arcane Controller

**Entry**: `water_entry` — +5% Spell Damage

**Cluster 1: Torrent** (Lane 1 — Spell Power, Inner)
| Node | Stat(s) |
|------|---------|
| torrent_1 | +6% Spell Damage |
| torrent_2 | +8 Max Mana |
| torrent_branch | +3% Spell Damage, +4 Max Mana |
| torrent_3 | +6% Spell Damage |
| torrent_4 | +8 Max Mana |
| **Torrent Notable: Arcane Surge** | +8% **[N]** Water Damage, +6 **[N]** Water Penetration |

**Cluster 2: Glacier** (Lane 2 — Control + Shield, Inner)
| Node | Stat(s) |
|------|---------|
| glacier_1 | +4% Freeze Chance |
| glacier_2 | +5% Energy Shield |
| glacier_branch | +2% Freeze Chance, +3% Energy Shield |
| glacier_3 | +4% Freeze Chance |
| glacier_4 | +5% Energy Shield |
| **Glacier Notable: Permafrost** | +15% **[N]** Damage vs Frozen, +5% Freeze Chance |

**Cluster 3: Depths** (Lane 1 — Spell Power, Outer)
| Node | Stat(s) |
|------|---------|
| depths_1 | +1.5 Mana Regen |
| depths_2 | +8 Max Mana |
| depths_branch | +1 Mana Regen, +4 Max Mana |
| depths_3 | +1.5 Mana Regen |
| depths_4 | +6% Spell Damage |
| **Depths Notable: Wellspring** | +5% **[N]** Max Mana %, +3 Mana Regen, +2 **[N]** ES Regen |

**Cluster 4: Confluence** (Lane 2 — Control + Shield, Outer)
| Node | Stat(s) |
|------|---------|
| confluence_1 | +6% Spell Damage |
| confluence_2 | +5% Energy Shield |
| confluence_branch | +3% Spell Damage, +2 ES Regen |
| confluence_3 | +5% Energy Shield |
| confluence_4 | +2 ES Regen |
| **Confluence Notable: Shatter** | +8 **[N]** Water Penetration, +5% Spell Damage |

**Water Synergies**:
1. Arcane Accumulation: +2% Spell Damage (cap 20%)
2. Tidal Force: +3% Water Damage (cap 30%)
3. Frost Intensification: +1% Freeze Chance (cap 10%)
4. Barrier Growth: +2% Energy Shield (cap 20%)

**Water Hub**: Heart of Water — +5% Water Damage, +3 Mana Regen

---

#### LIGHTNING — The Storm Dancer

**Entry**: `lightning_entry` — +4% Attack Speed

**Cluster 1: Surge** (Lane 1 — Speed + Crits, Inner)
| Node | Stat(s) |
|------|---------|
| surge_1 | +4% Attack Speed |
| surge_2 | +4% Crit Chance |
| surge_branch | +2% Attack Speed, +2% Crit Chance |
| surge_3 | +4% Attack Speed |
| surge_4 | +4% Crit Chance |
| **Surge Notable: Lightning Reflexes** | +5% Attack Speed, +5% Crit Chance, +5% **[N]** Lightning Damage |

**Cluster 2: Tempest** (Lane 2 — Shock + Utility, Inner)
| Node | Stat(s) |
|------|---------|
| tempest_cl_1 | +4% Shock Chance |
| tempest_cl_2 | +3% Move Speed |
| tempest_cl_branch | +2% Shock Chance, +2% Move Speed |
| tempest_cl_3 | +4% Shock Chance |
| tempest_cl_4 | +3% Move Speed |
| **Tempest Notable: Static Field** | +15% **[N]** Damage vs Shocked, +5% Shock Chance |

**Cluster 3: Arc** (Lane 1 — Speed + Crits, Outer)
| Node | Stat(s) |
|------|---------|
| arc_1 | +1.0 Stamina Regen |
| arc_2 | +4% Attack Speed |
| arc_branch | +0.5 Stamina Regen, +2% Attack Speed |
| arc_3 | +1.0 Stamina Regen |
| arc_4 | +4% Attack Speed |
| **Arc Notable: Quickening** | +8 **[N]** Lightning Penetration, +5% Attack Speed |

**Cluster 4: Conduit** (Lane 2 — Shock + Utility, Outer)
| Node | Stat(s) |
|------|---------|
| conduit_1 | +4% Crit Chance |
| conduit_2 | +3% Move Speed |
| conduit_branch | +2% Crit Chance, +2% Move Speed |
| conduit_3 | +4% Crit Chance |
| conduit_4 | +3% Move Speed |
| **Conduit Notable: Chain Strike** | +8 **[N]** Lightning Penetration, +5% Crit Chance |

**Lightning Synergies**:
1. Storm Buildup: +1% Attack Speed (cap 10%)
2. Voltage Surge: +3% Lightning Damage (cap 30%)
3. Chain Reaction: +0.5% Crit Chance (cap 5%)
4. Static Charge: +1% Shock Chance (cap 10%)

**Lightning Hub**: Heart of Lightning — +5% Lightning Damage, +5% Attack Speed

---

#### EARTH — The Unbreakable Wall

**Entry**: `earth_entry` — +5% Max HP

**Cluster 1: Bastion** (Lane 1 — Raw Durability, Inner)
| Node | Stat(s) |
|------|---------|
| bastion_1 | +5% Max HP |
| bastion_2 | +6 Armor |
| bastion_branch | +3% Max HP, +3 Armor |
| bastion_3 | +5% Max HP |
| bastion_4 | +6 Armor |
| **Bastion Notable: Ironhide** | +8% Max HP, +5% **[N]** Physical Resistance, +4 Armor |

**Cluster 2: Rampart** (Lane 2 — Active Defense, Inner)
| Node | Stat(s) |
|------|---------|
| rampart_1 | +2% Block Chance |
| rampart_2 | +4% KB Resistance |
| rampart_branch | +1% Block Chance, +2% KB Resistance |
| rampart_3 | +2% Block Chance |
| rampart_4 | +4% KB Resistance |
| **Rampart Notable: Stoneguard** | +3% Block Chance, +5% **[N]** Block Damage Reduction, +5% **[N]** Earth Damage |

**Cluster 3: Bulwark** (Lane 1 — Raw Durability, Outer)
| Node | Stat(s) |
|------|---------|
| bulwark_1 | +0.3 HP Regen |
| bulwark_2 | +6 Armor |
| bulwark_branch | +0.2 HP Regen, +3 Armor |
| bulwark_3 | +0.3 HP Regen |
| bulwark_4 | +5% Max HP |
| **Bulwark Notable: Unbreakable** | +5% **[N]** Health Recovery, +0.5 HP Regen, +5% KB Resistance |

**Cluster 4: Citadel** (Lane 2 — Active Defense, Outer)
| Node | Stat(s) |
|------|---------|
| citadel_1 | +5% Max HP |
| citadel_2 | +4% KB Resistance |
| citadel_branch | +3% Max HP, +2% KB Resistance |
| citadel_3 | +2% Block Chance |
| citadel_4 | +5% Max HP |
| **Citadel Notable: Mountain's Resolve** | +5% **[N]** Block Heal, +5% Block Damage Reduction, +5% **[N]** Earth Damage |

**Earth Synergies**:
1. Bedrock Foundation: +2% Max HP (cap 20%)
2. Mountain's Might: +3% Earth Damage (cap 30%)
3. Shield Attunement: +0.5% Block Chance (cap 5%)
4. Enduring Fortitude: +3 Armor (cap 30)

**Earth Hub**: Heart of Earth — +5% Earth Damage, +5% Max HP

---

#### VOID — The Parasite

**Entry**: `void_entry` — +5% DoT Damage

**Cluster 1: Blight** (Lane 1 — Sustain-Through-Damage, Inner)
| Node | Stat(s) |
|------|---------|
| blight_1 | +0.4% Life Steal |
| blight_2 | +5% DoT Damage |
| blight_branch | +0.2% Life Steal, +3% DoT Damage |
| blight_3 | +0.4% Life Steal |
| blight_4 | +5% DoT Damage |
| **Blight Notable: Sanguine Drain** | +1% Life Steal, +3% **[N]** Life Leech, +5% DoT Damage |

**Cluster 2: Entropy** (Lane 2 — DoT + Debuff, Inner)
| Node | Stat(s) |
|------|---------|
| entropy_1 | +5% Status Effect Duration |
| entropy_2 | +1 Mana on Kill |
| entropy_branch | +3% Status Effect Duration, +0.5 Mana on Kill |
| entropy_3 | +5% Status Effect Duration |
| entropy_4 | +1 Mana on Kill |
| **Entropy Notable: Lingering Torment** | +10% Status Effect Duration, +6 **[N]** Void Penetration |

**Cluster 3: Shadow** (Lane 1 — Sustain-Through-Damage, Outer)
| Node | Stat(s) |
|------|---------|
| shadow_1 | +0.3% True Damage |
| shadow_2 | +0.4% Life Steal |
| shadow_branch | +0.2% True Damage, +0.2% Life Steal |
| shadow_3 | +0.3% True Damage |
| shadow_4 | +0.4% Life Steal |
| **Shadow Notable: Essence Harvest** | +0.5% True Damage, +3% **[N]** Life Leech, +1 Mana on Kill |

**Cluster 4: Abyss** (Lane 2 — DoT + Debuff, Outer)
| Node | Stat(s) |
|------|---------|
| abyss_1 | +5% DoT Damage |
| abyss_2 | +1 Mana on Kill |
| abyss_branch | +3% DoT Damage, +0.5 Mana on Kill |
| abyss_3 | +5% DoT Damage |
| abyss_4 | +5% Status Effect Duration |
| **Abyss Notable: Void Corruption** | +8% DoT Damage, +8 **[N]** Void Penetration |

**Void Synergies**:
1. Dark Accumulation: +0.3% Life Steal (cap 3%)
2. Void Resonance: +3% Void Damage (cap 30%)
3. Soul Harvest: +2% DoT Damage (cap 20%)
4. Entropy Growth: +1% Status Effect Duration (cap 10%)

**Void Hub**: Heart of Void — +5% Void Damage, +5% DoT Damage

---

#### WIND — The Ghost

**Entry**: `wind_entry` — +5% Projectile Damage

**Cluster 1: Gale** (Lane 1 — Projectile Offense, Inner)
| Node | Stat(s) |
|------|---------|
| gale_1 | +5% Projectile Damage |
| gale_2 | +5 Accuracy |
| gale_branch | +3% Projectile Damage, +3 Accuracy |
| gale_3 | +5% Projectile Damage |
| gale_4 | +5 Accuracy |
| **Gale Notable: Wind Archer** | +8% Projectile Damage, +6 **[N]** Wind Penetration, +5% **[N]** Wind Damage |

**Cluster 2: Zephyr** (Lane 2 — Evasion + Mobility, Inner)
| Node | Stat(s) |
|------|---------|
| zephyr_1 | +8 Evasion |
| zephyr_2 | +3% Jump Force |
| zephyr_branch | +4 Evasion, +2% Jump Force |
| zephyr_3 | +8 Evasion |
| zephyr_4 | +3% Jump Force |
| **Zephyr Notable: Skybound** | +10 Evasion, +3% **[N]** Dodge Chance, +3% **[N]** Move Speed |

**Cluster 3: Marksman** (Lane 1 — Projectile Offense, Outer)
| Node | Stat(s) |
|------|---------|
| marksman_1 | +5% Projectile Speed |
| marksman_2 | +5 Accuracy |
| marksman_branch | +3% Projectile Speed, +3 Accuracy |
| marksman_3 | +5% Projectile Speed |
| marksman_4 | +5% Projectile Damage |
| **Marksman Notable: Sharpshooter** | +8% Projectile Damage, +8 **[N]** Wind Penetration |

**Cluster 4: Drift** (Lane 2 — Evasion + Mobility, Outer)
| Node | Stat(s) |
|------|---------|
| drift_1 | +8 Evasion |
| drift_2 | +3% Jump Force |
| drift_branch | +4 Evasion, +2% Jump Force |
| drift_3 | +8 Evasion |
| drift_4 | +3% Jump Force |
| **Drift Notable: Tailwind** | +5% **[N]** Evasion %, +5% Projectile Speed, +3% **[N]** Move Speed |

**Wind Synergies**:
1. Wind Convergence: +2% Projectile Damage (cap 20%)
2. Gale Force: +3% Wind Damage (cap 30%)
3. Eye Training: +5 Accuracy (cap 50)
4. Updraft: +5 Evasion (cap 50)

**Wind Hub**: Heart of Wind — +5% Wind Damage, +5% Projectile Damage

---

### 9.4 Octant Arms — Full Cluster Design

Octant basics use curated stats from their 3 neighbors (as defined in Phase 2, section 7.7). Notables introduce cross-element synergies and broader combat stats.

#### HAVOC (Fire + Void + Lightning) — Berserker Assassin

**Entry**: `havoc_entry` — +4% Physical Damage

**Cluster 1: Carnage** (Lane 1 — Fast Crits, Inner)
| Node | Stat(s) | Source |
|------|---------|--------|
| carnage_1 | +5 Crit Multiplier | Fire |
| carnage_2 | +4% Attack Speed | Lightning |
| carnage_branch | +3 Crit Multiplier, +2% Attack Speed | Fire+Lightning |
| carnage_3 | +4% Crit Chance | Lightning |
| carnage_4 | +5% Physical Damage | Fire |
| **Carnage Notable: Killing Spree** | ON_KILL: +10% Atk Speed, +8% Crit Chance for 4s. +5% **[N]** Armor Penetration | Multi |

**Cluster 2: Frenzy** (Lane 2 — DoT Sustain, Inner)
| Node | Stat(s) | Source |
|------|---------|--------|
| frenzy_1 | +5% DoT Damage | Void |
| frenzy_2 | +0.4% Life Steal | Void |
| frenzy_branch | +3% DoT Damage, +0.2% Life Steal | Void |
| frenzy_3 | +6% Burn Damage | Fire |
| frenzy_4 | +4% Ignite Chance | Fire |
| **Frenzy Notable: Death's Touch** | +10% DoT Damage, +0.5% **[N]** True Damage, +5% **[N]** Status Effect Chance | Multi |

**Cluster 3: Ruin** (Lane 1 — Fast Crits, Outer)
| Node | Stat(s) | Source |
|------|---------|--------|
| ruin_1 | +4% Crit Chance | Lightning |
| ruin_2 | +5% Physical Damage | Fire |
| ruin_branch | +2% Crit Chance, +3% Physical Damage | Fire+Lightning |
| ruin_3 | +5 Crit Multiplier | Fire |
| ruin_4 | +4% Attack Speed | Lightning |
| **Ruin Notable: Storm of Blades** | +8% Attack Speed, +8 Crit Multiplier, +5% **[N]** All Damage | Multi |

**Cluster 4: Mayhem** (Lane 2 — DoT Sustain, Outer)
| Node | Stat(s) | Source |
|------|---------|--------|
| mayhem_1 | +0.3% True Damage | Void |
| mayhem_2 | +5% DoT Damage | Void |
| mayhem_branch | +0.2% True Damage, +3 Crit Multiplier | Void+Fire |
| mayhem_3 | +4% Attack Speed | Lightning |
| mayhem_4 | +0.4% Life Steal | Void |
| **Mayhem Notable: Bloodbath** | +12% **[N]** Execute Damage, +1% Life Steal | Multi |

**Havoc Synergies** (per 3 nodes in Havoc):
1. Chaos Resonance: +2 Crit Multiplier (cap 20)
2. Shattered Defenses: +1% Armor Penetration (cap 10%)
3. Bloodlust: +2% Execute Damage (cap 20%)
4. Rampage Buildup: +0.1% True Damage (cap 1%)

> Havoc's True Damage synergy capped at 1% (not 2%) — true damage bypasses all defenses, so even small values are extremely powerful. 1% at cap is still significant as a synergy reward.

---

#### JUGGERNAUT (Fire + Void + Earth) — War Machine

**Entry**: `juggernaut_entry` — +4% Physical Damage

**Cluster 1: Conquest** (Lane 1 — Armored Warrior, Inner)
| Node | Stat(s) | Source |
|------|---------|--------|
| conquest_1 | +5% Physical Damage | Fire |
| conquest_2 | +5% Max HP | Earth |
| conquest_branch | +3% Physical Damage, +3% Max HP | Fire+Earth |
| conquest_3 | +6 Armor | Earth |
| conquest_4 | +5% Physical Damage | Fire |
| **Conquest Notable: Iron Reaver** | +10% Physical Damage, +5% Max HP, +5 **[N]** Armor | Multi |

**Cluster 2: Dominion** (Lane 2 — Sustain + Retaliation, Inner)
| Node | Stat(s) | Source |
|------|---------|--------|
| dominion_1 | +2% Block Chance | Earth |
| dominion_2 | +0.3 HP Regen | Earth |
| dominion_branch | +1% Block Chance, +0.2 HP Regen | Earth |
| dominion_3 | +0.4% Life Steal | Void |
| dominion_4 | +6% Burn Damage | Fire |
| **Dominion Notable: Blood Guard** | +5% Block Chance, +1% Life Steal, +5% **[N]** Thorns Damage | Multi |

**Cluster 3: Bloodforge** (Lane 1 — Armored Warrior, Outer)
| Node | Stat(s) | Source |
|------|---------|--------|
| bloodforge_1 | +5% Charged Attack Damage | Fire |
| bloodforge_2 | +5% Max HP | Earth |
| bloodforge_branch | +3% Charged Attack Damage, +3% Max HP | Fire+Earth |
| bloodforge_3 | +0.4% Life Steal | Void |
| bloodforge_4 | +6 Armor | Earth |
| **Bloodforge Notable: Relentless** | +10% Charged Attack Damage, +0.5% **[N]** True Damage, +5% **[N]** Block Heal | Multi |

**Cluster 4: Tyrant** (Lane 2 — Sustain + Retaliation, Outer)
| Node | Stat(s) | Source |
|------|---------|--------|
| tyrant_1 | +5% Max HP | Earth |
| tyrant_2 | +5% DoT Damage | Void |
| tyrant_branch | +3% Max HP, +0.2% Life Steal | Earth+Void |
| tyrant_3 | +6% Burn Damage | Fire |
| tyrant_4 | +6 Armor | Earth |
| **Tyrant Notable: Indomitable** | +8% Max HP, +8% **[N]** Thorns Damage, +5% **[N]** Physical Resistance | Multi |

**Juggernaut Synergies**:
1. Unbreakable Wall: +2% Max HP (cap 20%)
2. Blood Pact: +0.3% Life Steal (cap 3%)
3. Iron Thorns: +2% Thorns Damage (cap 20%)
4. Battle Recovery: +0.2 HP Regen (cap 2.0)

---

#### STRIKER (Fire + Wind + Lightning) — Blade Dancer

**Entry**: `striker_entry` — +3% Attack Speed

**Cluster 1: Quicksilver** (Lane 1 — Speed + Crit, Inner)
| Node | Stat(s) | Source |
|------|---------|--------|
| quicksilver_1 | +4% Attack Speed | Lightning |
| quicksilver_2 | +4% Crit Chance | Lightning |
| quicksilver_branch | +2% Attack Speed, +2% Crit Chance | Lightning |
| quicksilver_3 | +3% Move Speed | Lightning |
| quicksilver_4 | +5 Crit Multiplier | Fire |
| **Quicksilver Notable: Quick Draw** | +5% Attack Speed, +5% Move Speed, +5% **[N]** Projectile Damage | Multi |

**Cluster 2: Precision** (Lane 2 — Evasion + Counter, Inner)
| Node | Stat(s) | Source |
|------|---------|--------|
| precision_1 | +8 Evasion | Wind |
| precision_2 | +5 Accuracy | Wind |
| precision_branch | +4 Evasion, +3 Accuracy | Wind |
| precision_3 | +5% Physical Damage | Fire |
| precision_4 | +1.0 Stamina Regen | Lightning |
| **Precision Notable: Vital Strike** | +8 Crit Multiplier, +4% Crit Chance, +3% **[N]** Dodge Chance | Multi |

**Cluster 3: Ambush** (Lane 1 — Speed + Crit, Outer)
| Node | Stat(s) | Source |
|------|---------|--------|
| ambush_1 | +5% Physical Damage | Fire |
| ambush_2 | +4% Attack Speed | Lightning |
| ambush_branch | +3% Physical Damage, +2% Attack Speed | Fire+Lightning |
| ambush_3 | +4% Crit Chance | Lightning |
| ambush_4 | +5 Crit Multiplier | Fire |
| **Ambush Notable: Relentless Assault** | +5% Attack Speed, +5% Physical Damage, +5% **[N]** Charged Attack Damage | Multi |

**Cluster 4: Flurry** (Lane 2 — Evasion + Counter, Outer)
| Node | Stat(s) | Source |
|------|---------|--------|
| flurry_1 | +8 Evasion | Wind |
| flurry_2 | +4% Crit Chance | Lightning |
| flurry_branch | +4 Evasion, +2% Crit Chance | Wind+Lightning |
| flurry_3 | +5% Physical Damage | Fire |
| flurry_4 | +5 Accuracy | Wind |
| **Flurry Notable: Feint** | ON_EVADE: +12% Crit Chance for 3s. +8 Evasion, +3% **[N]** Dodge Chance | Multi |

**Striker Synergies**:
1. Quicksilver Reflexes: +1% Attack Speed (cap 10%)
2. Precision Training: +0.5% Crit Chance (cap 5%)
3. Burst Protocol: +1% Move Speed (cap 10%)
4. Shadow Step: +1% Dodge Chance (cap 10%)

---

#### WARDEN (Fire + Wind + Earth) — Iron Ranger

**Entry**: `warden_entry` — +6 Armor

**Cluster 1: Garrison** (Lane 1 — Fortified Defense, Inner)
| Node | Stat(s) | Source |
|------|---------|--------|
| garrison_1 | +6 Armor | Earth |
| garrison_2 | +2% Block Chance | Earth |
| garrison_branch | +3 Armor, +1% Block Chance | Earth |
| garrison_3 | +5% Max HP | Earth |
| garrison_4 | +5% Charged Attack Damage | Fire |
| **Garrison Notable: Shield Wall** | +5% Block Chance, +5% Max HP, +5% **[N]** KB Resistance | Multi |

**Cluster 2: Outrider** (Lane 2 — Ranged Offense, Inner)
| Node | Stat(s) | Source |
|------|---------|--------|
| outrider_1 | +5% Projectile Damage | Wind |
| outrider_2 | +5 Accuracy | Wind |
| outrider_branch | +3% Projectile Damage, +3 Accuracy | Wind |
| outrider_3 | +5% Projectile Speed | Wind |
| outrider_4 | +5% Physical Damage | Fire |
| **Outrider Notable: Iron Rain** | +10% Projectile Damage, +5 **[N]** Armor, +5% **[N]** Wind Damage | Multi |

**Cluster 3: Ironclad** (Lane 1 — Fortified Defense, Outer)
| Node | Stat(s) | Source |
|------|---------|--------|
| ironclad_1 | +5% Physical Damage | Fire |
| ironclad_2 | +6 Armor | Earth |
| ironclad_branch | +3% Physical Damage, +3 Armor | Fire+Earth |
| ironclad_3 | +5 Crit Multiplier | Fire |
| ironclad_4 | +5% Max HP | Earth |
| **Ironclad Notable: Power Shot** | +8% Charged Attack Damage, +5 Crit Multiplier, +5% **[N]** Projectile Damage | Multi |

**Cluster 4: Palisade** (Lane 2 — Ranged Offense, Outer)
| Node | Stat(s) | Source |
|------|---------|--------|
| palisade_1 | +5% Projectile Damage | Wind |
| palisade_2 | +2% Block Chance | Earth |
| palisade_branch | +3% Projectile Damage, +1% Block Chance | Wind+Earth |
| palisade_3 | +5 Accuracy | Wind |
| palisade_4 | +5% Charged Attack Damage | Fire |
| **Palisade Notable: Fortified Position** | ON_BLOCK: +10% Projectile Damage for 3s. +5% Block Chance, +5% **[N]** Projectile Speed | Multi |

**Warden Synergies**:
1. Fortified Range: +2% Projectile Damage (cap 20%)
2. Power Draw: +2% Charged Attack Damage (cap 20%)
3. Eagle Eye: +5 Accuracy (cap 50)
4. Immovable Stance: +1% KB Resistance (cap 10%)

---

#### WARLOCK (Water + Void + Lightning) — Dark Caster

**Entry**: `warlock_entry` — +4% Spell Damage

**Cluster 1: Hex** (Lane 1 — Offensive Spellcasting, Inner)
| Node | Stat(s) | Source |
|------|---------|--------|
| hex_1 | +6% Spell Damage | Water |
| hex_2 | +5% DoT Damage | Void |
| hex_branch | +3% Spell Damage, +3% DoT Damage | Water+Void |
| hex_3 | +1 Mana on Kill | Void |
| hex_4 | +4% Crit Chance | Lightning |
| **Hex Notable: Dark Arcana** | +8% Spell Damage, +5% DoT Damage, +6 **[N]** Void Penetration | Multi |

**Cluster 2: Ritual** (Lane 2 — Resource Drain, Inner)
| Node | Stat(s) | Source |
|------|---------|--------|
| ritual_1 | +8 Max Mana | Water |
| ritual_2 | +1.5 Mana Regen | Water |
| ritual_branch | +4 Max Mana, +1 Mana Regen | Water |
| ritual_3 | +0.4% Life Steal | Void |
| ritual_4 | +4% Attack Speed | Lightning |
| **Ritual Notable: Mind Drain** | +3 Mana Regen, +1% Life Steal, +3% **[N]** Mana Leech | Multi |

**Cluster 3: Malice** (Lane 1 — Offensive Spellcasting, Outer)
| Node | Stat(s) | Source |
|------|---------|--------|
| malice_1 | +4% Crit Chance | Lightning |
| malice_2 | +5% DoT Damage | Void |
| malice_branch | +2% Crit Chance, +3% DoT Damage | Lightning+Void |
| malice_3 | +6% Spell Damage | Water |
| malice_4 | +0.3% True Damage | Void |
| **Malice Notable: Eldritch Blast** | +5% True Damage, +5% Crit Chance, +5% **[N]** Status Effect Chance | Multi |

**Cluster 4: Damnation** (Lane 2 — Resource Drain, Outer)
| Node | Stat(s) | Source |
|------|---------|--------|
| damnation_1 | +6% Spell Damage | Water |
| damnation_2 | +5% Status Effect Duration | Void |
| damnation_branch | +3% Spell Damage, +3% Status Effect Duration | Water+Void |
| damnation_3 | +4% Attack Speed | Lightning |
| damnation_4 | +1 Mana on Kill | Void |
| **Damnation Notable: Cursed Knowledge** | +8% Spell Damage, +8% Status Effect Duration, +6 **[N]** Spell Penetration | Multi |

**Warlock Synergies**:
1. Arcane Corruption: +2% Spell Damage (cap 20%)
2. Eldritch Power: +3 Max Mana (cap 30)
3. Hex Mastery: +1% Status Effect Chance (cap 10%)
4. Mind Siphon: +1% Mana Leech (cap 10%)

---

#### LICH (Water + Void + Earth) — Undying Sorcerer

**Entry**: `lich_entry` — +4% Spell Damage

**Cluster 1: Grasp** (Lane 1 — DoT + Spell, Inner)
| Node | Stat(s) | Source |
|------|---------|--------|
| grasp_1 | +6% Spell Damage | Water |
| grasp_2 | +5% DoT Damage | Void |
| grasp_branch | +3% Spell Damage, +3% DoT Damage | Water+Void |
| grasp_3 | +5% Energy Shield | Water |
| grasp_4 | +5% Status Effect Duration | Void |
| **Grasp Notable: Death's Embrace** | +8% DoT Damage, +5% Spell Damage, +6 **[N]** Void Penetration | Multi |

**Cluster 2: Crypt** (Lane 2 — Layered Defense, Inner)
| Node | Stat(s) | Source |
|------|---------|--------|
| crypt_1 | +5% Max HP | Earth |
| crypt_2 | +6 Armor | Earth |
| crypt_branch | +3% Max HP, +3 Armor | Earth |
| crypt_3 | +0.3 HP Regen | Earth |
| crypt_4 | +1.5 Mana Regen | Water |
| **Crypt Notable: Necrotic Armor** | +5% Max HP, +5 Armor, +5% **[N]** Physical Resistance | Multi |

**Cluster 3: Requiem** (Lane 1 — DoT + Spell, Outer)
| Node | Stat(s) | Source |
|------|---------|--------|
| requiem_1 | +5% DoT Damage | Void |
| requiem_2 | +5% Energy Shield | Water |
| requiem_branch | +3% DoT Damage, +3% Energy Shield | Void+Water |
| requiem_3 | +5% Status Effect Duration | Void |
| requiem_4 | +6% Spell Damage | Water |
| **Requiem Notable: Soul Anchor** | +1% Life Steal, +8% Energy Shield, +5% **[N]** ES Regen | Multi |

**Cluster 4: Decay** (Lane 2 — Layered Defense, Outer)
| Node | Stat(s) | Source |
|------|---------|--------|
| decay_1 | +5% Max HP | Earth |
| decay_2 | +5% DoT Damage | Void |
| decay_branch | +3% Max HP, +3% DoT Damage | Earth+Void |
| decay_3 | +1.5 Mana Regen | Water |
| decay_4 | +0.3 HP Regen | Earth |
| **Decay Notable: Withering Presence** | +8% DoT Damage, +5% Max HP, +5% **[N]** Mana as Damage Buffer | Multi |

**Lich Synergies**:
1. Necrotic Bond: +2% Energy Shield (cap 20%)
2. Plague Growth: +2% DoT Damage (cap 20%)
3. Soul Barrier: +1% Mana as Damage Buffer (cap 10%)
4. Lingering Torment: +1% Status Effect Duration (cap 10%)

---

#### TEMPEST (Water + Wind + Lightning) — Battle Mage

**Entry**: `tempest_entry` — +4% Spell Damage

**Cluster 1: Squall** (Lane 1 — Spell + Speed, Inner)
| Node | Stat(s) | Source |
|------|---------|--------|
| squall_1 | +6% Spell Damage | Water |
| squall_2 | +4% Attack Speed | Lightning |
| squall_branch | +3% Spell Damage, +2% Attack Speed | Water+Lightning |
| squall_3 | +4% Crit Chance | Lightning |
| squall_4 | +8 Max Mana | Water |
| **Squall Notable: Swift Cast** | +5% Attack Speed, +5% Crit Chance, +5% **[N]** Lightning Damage | Multi |

**Cluster 2: Tailwind** (Lane 2 — Mobility + Projectile, Inner)
| Node | Stat(s) | Source |
|------|---------|--------|
| tailwind_1 | +3% Move Speed | Lightning |
| tailwind_2 | +5% Projectile Damage | Wind |
| tailwind_branch | +2% Move Speed, +3% Projectile Damage | Lightning+Wind |
| tailwind_3 | +8 Evasion | Wind |
| tailwind_4 | +5% Projectile Speed | Wind |
| **Tailwind Notable: Arcane Gust** | +8% Spell Damage, +5% Projectile Damage, +5% **[N]** Wind Damage | Multi |

**Cluster 3: Cyclone** (Lane 1 — Spell + Speed, Outer)
| Node | Stat(s) | Source |
|------|---------|--------|
| cyclone_1 | +4% Attack Speed | Lightning |
| cyclone_2 | +6% Spell Damage | Water |
| cyclone_branch | +2% Attack Speed, +3% Spell Damage | Lightning+Water |
| cyclone_3 | +3% Move Speed | Lightning |
| cyclone_4 | +8 Max Mana | Water |
| **Cyclone Notable: Windborne** | +8 Evasion, +5% Move Speed, +5% **[N]** Shock Chance | Multi |

**Cluster 4: Maelstrom** (Lane 2 — Mobility + Projectile, Outer)
| Node | Stat(s) | Source |
|------|---------|--------|
| maelstrom_1 | +5% Projectile Damage | Wind |
| maelstrom_2 | +4% Crit Chance | Lightning |
| maelstrom_branch | +3% Projectile Damage, +2% Crit Chance | Wind+Lightning |
| maelstrom_3 | +6% Spell Damage | Water |
| maelstrom_4 | +8 Evasion | Wind |
| **Maelstrom Notable: Eye of the Storm** | +8% Spell Damage, +5% Attack Speed, +5% **[N]** All Damage | Multi |

**Tempest Synergies**:
1. Storm Surge: +1% All Damage (cap 10%)
2. Static Buildup: +1% Shock Chance (cap 10%)
3. Arcane Flow: +1.5 Mana Regen (cap 15)
4. Wind Runner: +1% Move Speed (cap 10%)

---

#### SENTINEL (Water + Wind + Earth) — The Guardian

**Entry**: `sentinel_entry` — +3% Block Chance

**Cluster 1: Aegis** (Lane 1 — Block + Shield, Inner)
| Node | Stat(s) | Source |
|------|---------|--------|
| aegis_1 | +2% Block Chance | Earth |
| aegis_2 | +5% Energy Shield | Water |
| aegis_branch | +1% Block Chance, +3% Energy Shield | Earth+Water |
| aegis_3 | +5% Max HP | Earth |
| aegis_4 | +6 Armor | Earth |
| **Aegis Notable: Guardian's Ward** | +5% Block Chance, +8 Energy Shield, +5% **[N]** Block Damage Reduction | Multi |

**Cluster 2: Vigilance** (Lane 2 — Evasion + Awareness, Inner)
| Node | Stat(s) | Source |
|------|---------|--------|
| vigilance_1 | +8 Evasion | Wind |
| vigilance_2 | +5 Accuracy | Wind |
| vigilance_branch | +4 Evasion, +3 Accuracy | Wind |
| vigilance_3 | +0.3 HP Regen | Earth |
| vigilance_4 | +1.5 Mana Regen | Water |
| **Vigilance Notable: Keen Senses** | +10 Evasion, +8 Accuracy, +3% **[N]** Crit Nullify Chance | Multi |

**Cluster 3: Restoration** (Lane 1 — Block + Shield, Outer)
| Node | Stat(s) | Source |
|------|---------|--------|
| restoration_1 | +5% Max HP | Earth |
| restoration_2 | +5% Energy Shield | Water |
| restoration_branch | +3% Max HP, +3% Energy Shield | Earth+Water |
| restoration_3 | +6 Armor | Earth |
| restoration_4 | +1.5 Mana Regen | Water |
| **Restoration Notable: Restorative Aura** | +3 HP Regen, +3 Mana Regen, +5% **[N]** Health Recovery | Multi |

**Cluster 4: Haven** (Lane 2 — Evasion + Awareness, Outer)
| Node | Stat(s) | Source |
|------|---------|--------|
| haven_1 | +8 Evasion | Wind |
| haven_2 | +2% Block Chance | Earth |
| haven_branch | +4 Evasion, +1% Block Chance | Wind+Earth |
| haven_3 | +5% Max HP | Earth |
| haven_4 | +5 Accuracy | Wind |
| **Haven Notable: Stalwart Defense** | +5% Block Chance, +8 Evasion, +3% **[N]** All Elemental Resistance | Multi |

**Sentinel Synergies**:
1. Guardian's Resolve: +1% Block Chance (cap 10%)
2. Watchful Eye: +5 Evasion (cap 50)
3. Warding Aura: +1% All Elemental Resistance (cap 10%)
4. Stalwart Focus: +1% Crit Nullify Chance (cap 10%)

---

### 9.5 Bridge Node Redesign

All 12 bridges have 3 tiers: two pathing nodes (identity stats from both elements) and a tier-3 PAYOFF node that creates a **cross-element synergy effect** — something neither element provides alone.

| Bridge | Tier 1 | Tier 2 | Tier 3 — Cross-Element Payoff |
|--------|--------|--------|-------------------------------|
| **Fire↔Lightning** | +4% Phys Dmg, +3% Atk Speed | +3 Crit Mult, +3% Crit Chance | **Thundering Blows**: Crit strikes have +10% chance to Shock. Shocked targets take +8% Fire Damage. |
| **Fire↔Void** | +4% Phys Dmg, +4% DoT Dmg | +3% Burn Dmg, +0.2% Life Steal | **Burning Void**: Burn DoT ticks restore 3% of tick damage as HP (leech from fire DoTs). |
| **Fire↔Wind** | +4% Phys Dmg, +4% Proj Dmg | +3% Charged Atk, +4 Accuracy | **Boiling Currents**: Charged projectile attacks deal +15% bonus Fire damage. |
| **Fire↔Earth** | +4% Phys Dmg, +4% Max HP | +3 Crit Mult, +3 Armor | **Wild Fury**: +5% Physical Damage, +5% Max HP — the classic bruiser stats. |
| **Water↔Lightning** | +4% Spell Dmg, +3% Atk Speed | +6 Max Mana, +3% Crit Chance | **Storm Shatter**: Spell crits against Frozen targets deal +20% bonus Lightning damage. |
| **Water↔Void** | +4% Spell Dmg, +4% DoT Dmg | +4% Energy Shield, +0.2% Life Steal | **Void Chill**: Freeze Chance +5%. Frozen enemies take +8% Void damage. |
| **Water↔Wind** | +4% Spell Dmg, +4% Proj Dmg | +6 Max Mana, +4 Evasion | **Arctic Arcana**: Projectile spells gain +10% Spell Damage and +5% Freeze Chance. |
| **Water↔Earth** | +4% Spell Dmg, +4% Max HP | +4% Energy Shield, +3 Armor | **Glacial Fortress**: Block Chance +3%. While blocking, ES regen is not interrupted by hits. |
| **Lightning↔Void** | +3% Atk Speed, +4% DoT Dmg | +3% Crit Chance, +3% Status Duration | **Void Storm**: Shock ticks deal +5% bonus Void damage. Status Duration +5%. |
| **Lightning↔Wind** | +3% Atk Speed, +4% Proj Dmg | +3% Move Speed, +4 Accuracy | **Abyssal Storm**: Projectile attacks gain +8% Attack Speed. Move Speed +3%. |
| **Earth↔Void** | +4% Max HP, +4% DoT Dmg | +3 Armor, +0.2% Life Steal | **Soul Garden**: HP Regen +0.5/s. DoTs you inflict heal you for 2% of their tick damage. |
| **Earth↔Wind** | +4% Max HP, +4% Proj Dmg | +3 Armor, +4 Evasion | **Life Stream**: Evasion +8. Evading an attack restores 2% Max HP. |

**Design principle**: Each bridge tier 3 creates an effect that REQUIRES both elements to make sense. "Crit strikes shock" needs Fire's crits + Lightning's shock. "Freeze boosts void damage" needs Water's freeze + Void's damage type. "Block doesn't interrupt ES regen" needs Earth's block + Water's ES. These are mini-synergies that reward investing in both connected elements.

**Power level**: Bridge payoffs are roughly 1.5 notable equivalents — stronger than a basic node but weaker than a full notable. They're the reward for crossing into another element's territory.

### 9.6 Phase 4 Validation

Every node in the design above can be checked against Phase 2's 14 invariants:

1. **Basic nodes use only identity stats** — All elemental basics use their 5 stats. All octant basics use curated subsets from 7.7. Verified per cluster.
2. **Notables add non-identity stats** — Every notable has at least one **[N]** tagged stat (penetration, conditional, element damage, broader combat).
3. **No flat elemental damage on basics** — Zero instances. All basics use % or flat-to-rating (armor, evasion, accuracy, HP regen, mana regen).
4. **Penetration never on basics** — All penetration appears on notables only.
5. **Conversion never on basics** — Conversion is keystone-only (Inferno Master, Void Walker).
6. **MORE multipliers keystone-only** — Damage Multiplier only on keystones.
7. **Derivation stats keystone-only** — HP→damage, speed→spell, evasion→armor all keystone.
8. **On-event mechanics on notables/keystones only** — ON_KILL (Havoc notable), ON_EVADE (Striker notable), ON_BLOCK (Warden notable) all on notables with conditional. Keystone events on keystones.
9. **No cross-element on elemental basics** — Verified: Fire basics don't grant Water stats, etc.
10. **Octant basics from 3 neighbors only** — Verified per cluster's Source column.
11. **Keystones always have drawbacks** — All 28 have drawbacks (Phase 3).
12. **No movement speed drawbacks** — Zero instances (Phase 3).
13. **Each arm uses all identity stats** — All 5 identity stats appear in each elemental arm's basics. All curated octant stats appear.
14. **Synergy hubs unique per region** — All 14 hubs have distinct effects (Phase 3).

---

## 10. Phase 5 — Power Budget & Values

### 10.1 The Core Question

How much is 1 skill point worth? And how do we make sure +5% Physical Damage and +6 Armor and +4% Crit Chance are all "fair" for 1 point?

The answer: anchor everything to the **attribute ground truth**. 1 attribute point has a known, fixed value per element. 1 skill point should be worth a defined RATIO of that.

### 10.2 Attribute Point Values (Ground Truth)

What 1 attribute point gives, by element:

| Element | Stat | Per-Point | Stat Category |
|---------|------|-----------|---------------|
| **Fire** | Physical Damage % | 0.4 | Offense % |
| | Charged Attack Damage % | 0.3 | Offense % |
| | Crit Multiplier | 0.6 | Offense flat |
| | Burn Damage % | 0.4 | Offense % |
| | Ignite Chance | 0.1 | Chance % |
| **Water** | Spell Damage % | 0.5 | Offense % |
| | Max Mana | 1.5 | Resource flat |
| | Energy Shield % | 0.5 | Defense % |
| | ES Regen | 0.2 | Regen flat |
| | Mana Regen | 0.15 | Regen flat |
| | Freeze Chance | 0.1 | Chance % |
| **Lightning** | Attack Speed % | 0.3 | Speed % |
| | Move Speed % | 0.15 | Speed % |
| | Crit Chance | 0.1 | Chance % |
| | Stamina Regen | 0.1 | Regen flat |
| | Shock Chance | 0.1 | Chance % |
| **Earth** | Max HP % | 0.5 | Resource % |
| | Armor | 3.5 | Defense flat |
| | HP Regen | 0.02 | Regen flat |
| | Block Chance | 0.05 | Chance % |
| | KB Resistance | 0.3 | Defense % |
| **Wind** | Evasion | 5.0 | Defense flat |
| | Accuracy | 3.0 | Rating flat |
| | Projectile Damage % | 0.5 | Offense % |
| | Jump Force % | 0.15 | Utility % |
| | Projectile Speed % | 0.3 | Utility % |
| **Void** | Life Steal | 0.2 | Sustain % |
| | True Damage % | 0.1 | Offense % |
| | DoT Damage % | 0.3 | Offense % |
| | Mana on Kill | 0.5 | Resource flat |
| | Status Effect Duration | 0.3 | Utility % |

### 10.3 The Skill-to-Attribute Ratio

**Design choice**: 1 skill point on a basic node is worth approximately **1.2 attribute points** of the stat it grants.

**Why 1.2x, not 1.0x?**
- Attribute points are FREE (you get them automatically on level-up)
- Skill points require PATHING (you spend points on nodes you may not want to reach the ones you do)
- If skill points were worth exactly 1.0x, pathing through basics would feel like a waste vs just leveling attributes
- At 1.2x, each basic node feels slightly better per-point than an attribute point, compensating for the pathing tax

**Why not higher (e.g., 2.0x)?**
- At 50 skill points (level 50), you can allocate ~35 basic nodes after pathing
- At 2.0x, 35 basic nodes = 70 attribute points of power, which would dwarf actual attributes
- The tree should COMPLEMENT attributes, not overshadow them

### 10.4 Basic Node Values (Tier 1-3, cost 1 point)

Using the 1.2x ratio, a basic node granting a single stat:

| Stat | Per-Attr-Point | × 1.2 | Rounded Basic Value |
|------|---------------|-------|---------------------|
| **Offense %** | | | |
| Physical Damage % | 0.4 | 0.48 | **+5%** |
| Spell Damage % | 0.5 | 0.60 | **+6%** |
| Projectile Damage % | 0.5 | 0.60 | **+5%** |
| Charged Attack Damage % | 0.3 | 0.36 | **+5%** |
| Burn Damage % | 0.4 | 0.48 | **+6%** |
| DoT Damage % | 0.3 | 0.36 | **+5%** |
| **Crit** | | | |
| Crit Multiplier (flat) | 0.6 | 0.72 | **+5** |
| Crit Chance % | 0.1 | 0.12 | **+4%** |
| **Speed** | | | |
| Attack Speed % | 0.3 | 0.36 | **+4%** |
| Move Speed % | 0.15 | 0.18 | **+3%** |
| **Defense** | | | |
| Max HP % | 0.5 | 0.60 | **+5%** |
| Armor (flat) | 3.5 | 4.2 | **+6** |
| Energy Shield % | 0.5 | 0.60 | **+5%** |
| ES Regen (flat /s) | 0.2 | 0.24 | **+2** |
| KB Resistance % | 0.3 | 0.36 | **+4%** |
| Block Chance % | 0.05 | 0.06 | **+2%** |
| Evasion (flat) | 5.0 | 6.0 | **+8** |
| **Rating flat** | | | |
| Accuracy (flat) | 3.0 | 3.6 | **+5** |
| Max Mana (flat) | 1.5 | 1.8 | **+8** |
| **Regen flat** | | | |
| HP Regen | 0.02 | 0.024 | **+0.3** |
| Mana Regen | 0.15 | 0.18 | **+1.5** |
| Stamina Regen | 0.1 | 0.12 | **+1.0** |
| **Chance %** | | | |
| Ignite Chance | 0.1 | 0.12 | **+4%** |
| Freeze Chance | 0.1 | 0.12 | **+4%** |
| Shock Chance | 0.1 | 0.12 | **+4%** |
| **Sustain %** | | | |
| Life Steal | 0.2 | 0.24 | **+0.4%** |
| True Damage % | 0.1 | 0.12 | **+0.3%** |
| **Resource flat** | | | |
| Mana on Kill | 0.5 | 0.6 | **+1** |
| **Utility %** | | | |
| Status Effect Duration | 0.3 | 0.36 | **+5%** |
| Jump Force % | 0.15 | 0.18 | **+3%** |
| Projectile Speed % | 0.3 | 0.36 | **+5%** |

**Rounding philosophy**: Values are rounded to clean numbers that feel good in-game. The exact 1.2x ratio is a GUIDE, not a law. Some stats are rounded up slightly (Armor 6→8 because +6 armor feels insignificant), others down (Crit Chance 0.12→4% because crit chance is extremely powerful per-point).

**Branch nodes** (2 stats): Each stat at ~60% of a single-stat basic. E.g., a branch giving Physical Damage % + Crit Multiplier = +3% Phys + 3 Crit Mult.

### 10.5 Notable Node Values (Tier 4, cost 2 points)

**Design choice**: A notable is worth **2.5 basic nodes** of power, distributed across 2-3 stats.

Why 2.5x (not 2.0x)?
- Notables cost 2 points but are the cluster payoff — they should feel like a reward
- They introduce non-identity stats (penetration, conditional, element damage) which are qualitatively more valuable
- The slight premium (2.5x vs 2.0x) makes reaching a notable feel worthwhile

**Notable budget**: ~2.5 × (1 basic node value) = spread across 2-3 stats

| Notable Stat Type | Typical Value | Rationale |
|-------------------|---------------|-----------|
| Element Damage % | +8-10% | Notable-exclusive, broader than identity |
| Element Penetration | +6-8 | Tree-primary stat, high impact |
| Conditional Damage % | +12-15% | Situational, so higher base value |
| DoT Duration % | +12-15% | Extends existing DoT, moderate impact |
| Dodge Chance % | +3% | Very powerful per-point (full avoidance) |
| Status Effect Chance | +5% | Moderate ailment impact |
| Block Heal % / Block Dmg Reduction | +5% | Notable-tier defensive mechanic |
| Physical Resistance % | +5% | Direct damage reduction, very strong |
| Health Recovery % | +5% | Multiplier on all healing |
| Identity stat (bonus) | +5% / +5 | Smaller portion of the notable's budget |

### 10.6 Synergy Node Values (Tier 5, cost 2 points)

**Design choice**: Each synergy grants a bonus per 3 allocated nodes in the arm, with a cap at ~10 stacks (30 nodes, roughly the full arm).

The per-3-node bonus should be roughly **0.5 basic nodes** of power. At full investment (10 stacks), total synergy value = ~5 basic nodes = significant but not dominant.

| Synergy Stat Type | Per 3 Nodes | Cap (10 stacks) | As Basic Equivalents |
|-------------------|-------------|-----------------|---------------------|
| Damage % (phys, spell, proj, DoT) | +2% | 20% | ~4 basic nodes |
| Element Damage % | +3% | 30% | ~5 basic nodes |
| Crit Multiplier | +0.5 | 5 | ~1 basic node |
| Crit Chance / Atk Speed / Block | +0.5-1% | 5-10% | ~2 basic nodes |
| Armor / Evasion / Accuracy (flat) | +5 | 50 | ~6 basic nodes |
| Life Steal | +0.3% | 3% | ~7 attr points |
| True Damage | +0.2% | 2% | ~20 attr points (high value) |
| All Damage | +1% | 10% | N/A (unique) |
| All Elemental Resistance | +1% | 10% | N/A (unique) |

### 10.7 Synergy Hub Values (Tier 5 notable, cost 2 points)

**Elemental hubs**: Fixed dual-stat bonus. Each stat at approximately 1 basic node value.

| Hub | Stat 1 | Stat 2 |
|-----|--------|--------|
| Heart of Fire | +5% Fire Damage | +5% Physical Damage |
| Heart of Water | +5% Water Damage | +3 Mana Regen |
| Heart of Lightning | +5% Lightning Damage | +5% Attack Speed |
| Heart of Earth | +5% Earth Damage | +5% Max HP |
| Heart of Void | +5% Void Damage | +5% DoT Damage |
| Heart of Wind | +5% Wind Damage | +5% Projectile Damage |

**Octant hubs**: Attribute-sum scaling (see Phase 3, section 8.4 for full table). Values designed so at 90 total attribute points (30+30+30), the bonus roughly equals one notable's worth of power.

### 10.8 Keystone Values (Tier 6, cost 3 points)

**Design choice**: Keystones are NOT valued by the basic-node ratio. They're valued by the MECHANIC they introduce. The numbers should make the mechanic feel impactful but not broken.

**Guidelines**:
- Conversion: 40-50% (enough to commit to an element, but not 100% — still some physical)
- Multipliers (MORE): +15-30% (multiplicative with everything, so even "small" values are massive)
- Conditional bonuses: +25-40% (only active some of the time)
- Drawbacks: Roughly 60-70% of the total bonus value. Keystones should be NET positive but with a real cost.

**Drawback severity guide**:
- -15% to -25% of a core stat = moderate (you feel it but can compensate)
- -30% or more = severe (defines your build — you must work around it)
- +% Damage Taken = most dangerous drawback (scales with enemy power)
- 0 HP Regen (Void KS2) = extreme (unique drawback, unique build)

### 10.9 Entry Node Values (Tier 1, cost 1 point)

Entry nodes are the gateway to each arm. They cost 1 point and grant a single identity stat.

**Design choice**: Entry nodes are worth exactly **1.0 basic node** of the arm's signature stat. They're not premium — they're the minimum buy-in.

| Arm | Entry Stat | Value | Ratio |
|-----|-----------|-------|-------|
| Fire | Physical Damage % | +5% | 1.0x |
| Water | Spell Damage % | +5% | 1.0x |
| Lightning | Attack Speed % | +4% | 1.0x |
| Earth | Max HP % | +5% | 1.0x |
| Void | DoT Damage % | +5% | 1.0x |
| Wind | Projectile Damage % | +5% | 1.0x |
| Havoc | Physical Damage % | +4% | 0.8x |
| Juggernaut | Physical Damage % | +4% | 0.8x |
| Striker | Attack Speed % | +3% | 0.8x |
| Warden | Armor | +6 | 0.8x |
| Warlock | Spell Damage % | +4% | 0.8x |
| Lich | Spell Damage % | +4% | 0.8x |
| Tempest | Spell Damage % | +4% | 0.8x |
| Sentinel | Block Chance % | +3% | 0.8x |

Octant entries are consistently 80% of the elemental entry for their signature stat. Breadth (access to 3 elements' stats) compensates for the per-node reduction.

### 10.10 Bridge Node Values

Bridges cost 1 point each (3 nodes per bridge). Each bridge node grants 2 stats (one from each connected element).

**Design choice**: Each stat on a bridge node = **0.5 basic nodes** of that stat. Two stats × 0.5 = 1.0 basic node equivalent total.

Bridges are NOT premium — they're pathing connectors. Their value is in ACCESS (reaching the other element or an octant), not in raw power.

| Bridge Tier | Stat 1 Value | Stat 2 Value | Total Equivalent |
|-------------|-------------|-------------|-----------------|
| Tier 1 | ~0.5 basic | ~0.5 basic | 1.0 basic |
| Tier 2 | ~0.6 basic | ~0.6 basic | 1.2 basic |
| Tier 3 (Payoff) | +5% elem dmg | +5% elem dmg | ~2.0 basic (reward for full bridge) |

### 10.11 Total Power Budget — A Level 50 Player

At level 50, a player has ~50 skill points and ~50 attribute points.

**Attribute power**: 50 points invested in 1-2 elements.
- Pure Fire (50 pts): +20% Phys Dmg, +15% Charged Atk, +30 Crit Mult, +20% Burn Dmg, +5% Ignite
- Split Fire/Earth (25/25): +10% Phys Dmg, +7.5% Charged Atk, +15 Crit Mult, +12.5% HP, +125 Armor, +5 HP Regen, +5% Block, +7.5% KB Resist

**Skill point allocation** (~50 points):
- Entry (1pt) → 8 inner basics (8pts) → 1 inner notable (2pts) → 8 outer basics (8pts) → 1 outer notable (2pts) → 4 synergies (8pts) → 1 hub (2pts) → 1 keystone (3pts) = **34 points** for one full lane + keystone
- Remaining 16 points for: second lane entry, some basics, maybe a second notable, or bridge to another arm

**Expected tree power at 50pts** (one arm focus):
- ~16 basic nodes: ~80% worth of one full arm's identity stats
- 2 notables: 2 non-identity effects (penetration + conditional)
- 4 synergies: scaling bonuses (at ~10 nodes allocated = ~3 stacks each)
- 1 hub: +5% element dmg + complementary stat
- 1 keystone: 1 build-defining mechanic

This feels right: the tree adds meaningful specialization on top of attributes, without dwarfing them. A player's power comes ~40% from attributes, ~30% from gear, ~30% from tree at level 50.

### 10.12 Value Application to Phase 4 Nodes

The values already placed in Phase 4 cluster tables use the budget defined here. To verify:

**Fire Cluster 1 (Ignition)** — 5 basics + 1 notable, costing 7 points:
- ignition_1: +6% Phys Dmg (1.0 basic) ✓
- ignition_2: +5 Crit Mult (1.0 basic) ✓
- ignition_branch: +3% Phys, +3 Crit Mult (0.6+0.6 = 1.2 basic) ✓
- ignition_3: +6% Phys Dmg (1.0 basic) ✓
- ignition_4: +6 Crit Mult (1.0 basic) ✓
- Notable: +10% Fire Dmg, +8 Fire Pen (2.5 basic worth) ✓

Total cluster: ~7.2 basic equivalents for 7 points = 1.03x per point. On budget.

**Earth Cluster 1 (Bastion)** — verification:
- bastion_1: +5% Max HP (1.0 basic) ✓
- bastion_2: +6 Armor (1.0 basic) ✓
- bastion_branch: +3% Max HP, +3 Armor (0.6+0.5 = 1.1 basic) ✓
- bastion_3: +5% Max HP (1.0 basic) ✓
- bastion_4: +6 Armor (1.0 basic) ✓
- Notable: +8% Max HP, +5% Phys Resist, +4 Armor (2.5 basic) ✓

Total: ~7.6 basic equivalents for 7 points = 1.09x per point. Slightly generous due to defensive stats being harder to abuse — intentional.

### 10.13 Cross-Stat Equivalence Table

This table defines "what is 1 basic node worth for each stat" — the Rosetta Stone for balancing different stat types against each other.

| 1 Basic Node = | Value | Reasoning |
|----------------|-------|-----------|
| +5-6% Offense (phys, spell, proj, burn, DoT) | Standard | ~1.2× attribute point |
| +5 Crit Multiplier | Standard | 0.6/attr × 1.2 ≈ 5 after rounding |
| +4% Crit Chance | Standard | Crit chance is very impactful per-% |
| +4% Attack Speed | Standard | Also very impactful per-% |
| +3% Move Speed | Standard | 0.15/attr × 1.2, rounded generously |
| +5% Max HP | Standard | Scales with base HP pool |
| +6 Armor | Standard | 3.5/attr × 1.2 ≈ 4.2, rounded to 6 for feel |
| +5% Energy Shield | Standard | Same as Max HP % |
| +2 ES Regen /s | Standard | 0.2/attr × 1.2, rounded to clean number |
| +8 Evasion | Standard | 5/attr × 1.2, rounded up |
| +5 Accuracy | Standard | 3/attr × 1.2, rounded up |
| +8 Max Mana | Standard | 1.5/attr × 1.2, rounded up significantly (mana less impactful per unit) |
| +2% Block Chance | Standard | 0.05/attr × 1.2 = 0.06 → rounded to 2% (block is powerful per-%) |
| +4% Ailment Chance | Standard | Chance-based, moderate impact |
| +0.4% Life Steal | Standard | Sustain %, high value per-% |
| +0.3% True Damage | Standard | Defense-bypassing, very high value |
| +5% Status Duration / Projectile Speed | Standard | Utility stats |
| +3% Jump Force | Standard | Niche utility |
| +0.3 HP Regen /s | Standard | 0.02/attr × 1.2 = 0.024, rounded to 0.3 for playability |
| +1.5 Mana Regen /s | Standard | Per-second regen |
| +1.0 Stamina Regen /s | Standard | Per-second regen |
| +4% KB Resistance | Standard | 0.3/attr × 1.2, rounded |
| +1 Mana on Kill | Standard | Resource on trigger |

> **Earth calibration note**: Earth's per-attribute-point values for HP Regen (0.02) and Block Chance (0.05) are much lower than other elements. The 1.2x ratio produces very small numbers (0.024 regen, 0.06% block). For playability, these are rounded UP significantly: +0.3 HP Regen and +2% Block Chance per basic node. This means Earth tree nodes are relatively MORE generous compared to Earth attributes — intentional, since Earth's attribute power is concentrated in Max HP% and Armor, while regen and block are supplementary. The tree is where you build a serious regen or block build.
