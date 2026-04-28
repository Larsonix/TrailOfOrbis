# Player Guide System — Design Document

**Status**: Design Phase
**Goal**: Contextual popups at key moments to teach players systems as they encounter them, with "Learn More" links to the Voile in-game wiki.

---

## 1. Problem Statement

Trail of Orbis has zero player guidance. The core gameplay loop (kill → loot → upgrade → realm) is intuitive, but the mod has deep systems that players can't understand without external help:

- **Gear terminology** (Modifier, Quality, Implicit, Rarity) appears on every tooltip with no explanation
- **Attribute/Skill points** appear on level up with no hint where to spend them
- **Stones** accumulate in inventory with zero context
- **Death penalty** at L20+ is a punishing surprise
- **Realm gateway chain** (Thorium → Arcanist's Workbench → Ancient Gateway → 100 Memories) has no in-game guide
- **Hidden systems** (damage pipeline, loot filter, mob scaling, map modifiers) require commands or wiki knowledge to discover

The guide system does NOT teach the game — the game teaches itself. The guide **names what the player just encountered** and gives them the ONE thing they need to know right now.

---

## 2. Player Journey Timeline

Based on source code analysis of all systems:

### Level 1-5: First Steps (Minutes 0-30)
- Player spawns with XP bar HUD, empty hotbar, no guidance
- Crafts first tools/weapons at workbench → items auto-convert to RPG gear (confusing tooltips)
- Kills first mobs → XP, gear drops (8%), stone drops (5%), map drops (very rare, intentionally)
- First level up at ~3 mob kills → gets 1 Attribute Point + 1 Skill Point they can spend immediately
- Player already has starting stones (~10 of each type) — but doesn't know what they're for
- Stones from drops accumulate further with zero context

### Level 5-20: Overworld Phase (Hours 1-5)
- **This is the longest phase** — realm maps drop VERY rarely and can't be used yet
- Gateway chain: need Thorium (desert south) + Arcanist's Workbench + 100 Memories
- Player discovers `/stats` page, allocates attribute points
- Player discovers Skill Sanctum (`/sanctum`) — 485 nodes in 3D, overwhelming
- First deaths happen (no penalty below L20)
- First ailments from mobs (burn, freeze, shock, poison)
- Mob difficulty scales with distance from spawn (invisible to player)

### Level 20-35: Realm Era
- Ancient Gateway crafted → first realm entered
- Timer-based combat arenas with boosted XP and loot
- Map modifiers discovered (prefix = difficulty, suffix = rewards)
- **Death penalty activates at L20** — 50% XP progress lost on death
- Elite and boss mobs encountered

### Level 35-50+: Mid-Late Game
- Larger realm sizes unlock
- Stone crafting becomes intentional
- Build optimization: attribute + skill tree + gear synergy
- Loot filter needed for inventory management

---

## 3. What's Obvious vs What's Confusing

### Obvious (no guide needed)
- XP bar fills → level up (clear visual feedback)
- Gear drops from mobs (obvious)
- Equipping better gear makes you stronger (immediate)
- Realms are dungeons — kill mobs, get loot (after first entry)
- Core loop: kill → loot → upgrade (self-evident)

### Confusing (guide needed)
- Gear tooltips: what do Rarity, Quality, Modifiers mean?
- Where/how to spend Attribute and Skill points
- What are stones for?
- The gateway chain to unlock realms
- Death penalty existing (and when it starts)
- Ailment effects and how to counter them
- Mob scaling with distance
- Map modifiers (prefix/suffix system)
- Loot filter exists
- Respec costs stones (not free)

---

## 4. Core Design Principles

### 4.1 One Popup at a Time
A boolean `isPopupShowing` per player. If a popup is already visible, new triggers are skipped. They re-fire on the next natural occurrence.

### 4.2 Collision Handling: Show Most Important, Defer the Rest
When multiple milestones trigger simultaneously (same tick), show the **most important** one and skip the rest. Skipped milestones are NOT marked complete — they re-fire on the next natural occurrence.

This works because triggers fall into two categories:
- **One-shot-ish** (level thresholds, deaths, realm entries) — these re-occur naturally (more level ups, more deaths, more realm entries), so they retry until shown. Level thresholds use `>=` checks, not `==`.
- **Repeatable** (item pickups: gear, stone, map) — happen constantly, will fire again next mob kill.

**No queue, no cooldown, no timers needed.** Just: "is a popup already showing? If yes, skip."

| Priority | Type | Why |
|----------|------|-----|
| **CRITICAL** | Level thresholds, deaths, realm entry, sanctum visit | Important one-time knowledge; re-triggers on every future occurrence until shown |
| **NORMAL** | Item pickups (gear, stone, map), ailments, elite kills | Happen constantly, zero loss from deferring |
| **LOW** | Welcome (on join), loot filter hint (inventory fullness) | Can always defer |

Highest-priority unshown milestone wins. Others stay unmarked and wait.

### 4.3 Fire Once Per Player, Ever
Each milestone fires once per player across all sessions. Tracked in database.

### 4.4 Mark Complete on Display, Not on Trigger
The milestone is marked complete in the DB when the popup is actually **shown** to the player, not when the trigger fires. This prevents phantom completions from popups that were skipped due to another popup being active.

### 4.5 Popup Content: 2-4 Lines Max
Each popup answers ONE question. "Learn More" opens the relevant Voile wiki page. "Got it" dismisses.

---

## 5. Failure Modes

### 5.1 Player Disconnects Mid-Popup
- Popup is already shown → milestone was already marked complete in DB. No issue.
- Player never clicked "Got it" or "Learn More" → popup disappears on disconnect (HUD cleanup in `onPlayerDisconnect`). Milestone is still complete — they saw it even if they didn't interact.

### 5.2 Popup Dismissal Fails (UI crash, event not received)
- `isPopupShowing` stays true → no more popups show for this session
- On disconnect → `isPopupShowing` cleared (it's in-memory per player)
- On reconnect → fresh state, missed milestones that were marked complete stay complete, unmarked ones re-trigger normally
- **Safety valve**: if `isPopupShowing` has been true for > 60 seconds without interaction, force-clear it. The popup either rendered or it didn't — either way, unblock future popups.

### 5.3 One-Shot Milestone Fires While Another Popup Is Showing
- Example: Level 20 popup is showing, player dies → first death milestone fires
- First death is skipped because popup is active
- **Problem**: first death is one-shot — it won't naturally re-trigger
- **Solution for true one-shots**: deaths, realm entries, and sanctum visits aren't truly one-shot. Players die again. They enter realms again. They visit sanctum again. These triggers fire on EVERY occurrence until marked complete, not just the literal first time.
- **Level thresholds** are the only true one-shots. Solution: check level thresholds on EVERY level up (`if newLevel >= 20 && milestone not complete`), not just exactly at level 20. This means even if L20 popup was blocked, it shows at L21, L22, etc.

### 5.4 Multiple Milestones in Same Tick
- Mob kill drops gear + stone + grants level up
- Priority gate picks the highest-priority one (level up = CRITICAL)
- Gear and stone milestones stay unmarked → re-fire on next pickup
- No queue, no timer, no cooldown needed

### 5.5 Server Restart / Crash
- In-memory state (`isPopupShowing`, transient popup data) lost
- DB state (completed milestones) persisted
- On restart: player reconnects, completed milestones stay complete, incomplete ones re-trigger normally
- No data loss possible

### 5.6 Player Progresses Past a Milestone Without Triggering It
- Example: player gets gear from a chest before the guide system is initialized (race during plugin startup)
- Not a real problem — they'll get more gear. The milestone fires on NEXT gear pickup.
- For level thresholds: checked as `>=`, not `==`, so they catch up.

---

## 6. Milestone List

### Phase 1: First Session (Level 1-5)

#### M01: Welcome
- **Trigger**: New player created in database (`PlayerDataRepository.create()` path in `PlayerJoinListener.onPlayerReady()`)
- **Priority**: LOW
- **Content**:
  > **Welcome to Trail of Orbis**
  > The world's corrupted. Everything beyond the safe zone wants you dead, and it only gets worse the further you go. You're one of the last ones standing, so no pressure.
  >
  > Set up a base near spawn, craft some gear, kill some stuff. When you feel ready, push south toward the desert. There's Thorium down there, and you're gonna need it to build a Gateway and start running Realms. That's where the real game begins.
- **Learn More**: `Larsonix:TrailOfOrbis:getting-started`
- **Wiki Status**: GOOD (8/10) — may need rewrite to match this lore framing

#### M02: First Gear
- **Trigger**: First RPG gear item enters player inventory (any acquisition path: drop, chest, craft)
- **Priority**: NORMAL (repeatable)
- **Content**:
  > **Your First Gear Drop**
  > So you picked up your first real piece of gear. Welcome to the rabbit hole.
  >
  > Three things matter on every item: Rarity is the color, it goes from grey (trash) to orange (don't you dare drop that). Quality is a number from 1 to 101, higher means the stats hit harder. And Modifiers are the stat bonuses listed underneath, each piece rolls random ones. More rarity means more mods. Better quality means stronger mods. You'll be comparing tooltips for the rest of your life now.
- **Learn More**: `Larsonix:TrailOfOrbis:rarities`
- **Wiki Status**: EXCELLENT (9/10)

#### M03: First Level Up
- **Trigger**: Player level increases (level >= 2, checked on every level up until shown)
- **Priority**: CRITICAL
- **Content**:
  > **You Leveled Up!**
  > You got an Attribute Point and a Skill Point. Two different systems, both important.
  >
  > Type /stats to open the Attribute page. You pick one of 6 elements (Fire, Water, Lightning, Earth, Wind, Void) and each one shapes your build differently. Fire hits harder, Earth makes you tanky, Lightning makes you fast, you get the idea.
  >
  > Type /skilltree to enter the Skill Sanctum. That's a whole 3D world with a massive passive tree. Spend your Skill Point there. Don't worry about getting it wrong, you can respec.
- **Learn More**: `Larsonix:TrailOfOrbis:index` (attributes)
- **Wiki Status**: EXCELLENT (10/10)

#### M04: First Stone
- **Trigger**: First stone item enters player inventory
- **Priority**: NORMAL (repeatable)
- **Content**:
  > **You Found a Stone**
  > That's a Stone. It looks like a random drop but it's actually the entire crafting system in this mod.
  >
  > Stones let you reroll your gear's modifiers, upgrade rarity, change quality, add new stats, even corrupt items for risky bonuses. There's like 28 different types and they all do something specific. Right-click one to see what it works on. Don't throw any away, you will regret it.
- **Learn More**: `Larsonix:TrailOfOrbis:index` (stones)
- **Wiki Status**: NEEDS AUDIT

#### M16: Gear Requirements
- **Trigger**: Player fails to equip an item due to level or attribute requirements. Fires on every failed equip until shown.
- **Priority**: CRITICAL (player is frustrated RIGHT NOW)
- **Content**:
  > **Can't Equip That?**
  > Can't equip that? Yeah, gear has requirements.
  >
  > Every piece of equipment has a level requirement and some also need specific attribute points in certain elements. Check the tooltip, it lists everything you need. If it says "Requires 15 Fire" and you've got 10, either level up and put more points in Fire or find gear that fits your current build. Your build choices determine what you can and can't wear.
- **Learn More**: `Larsonix:TrailOfOrbis:requirements`
- **Wiki Status**: UNKNOWN — needs audit
- **NOTE**: Fires on failed equip attempt, detected via `EquipmentListener.setupArmorValidation()`.

### Phase 2: Overworld Progression (Level 5-20)

#### M05: First Gateway Crafted
- **Trigger**: Player crafts their first Portal_Device (Ancient Gateway) at an Arcanist's Workbench. Hook via `CraftRecipeEvent` for Portal_Device item.
- **Priority**: CRITICAL
- **Content**:
  > **You Built a Gateway**
  > Nice, you built a Gateway. This is where things get real.
  >
  > Place it down somewhere, use a Realm Map on it, and walk through the portal that opens. On the other side is a timed combat arena full of mobs. Kill all of them before the timer runs out and you win, rewards spawn in the center. If the timer expires, you get kicked out with nothing. Maps are consumed on use so don't waste them on fights you can't win.
- **Learn More**: `Larsonix:TrailOfOrbis:map-crafting`
- **Wiki Status**: NEEDS AUDIT
- **NOTE**: Portal_Device crafting is a **vanilla Hytale progression feature** (Thorium + Arcanist's Workbench + 100 Memories). We hook the vanilla craft event.

#### M06: First Death
- **Trigger**: Player dies (any cause). Fires on every death until shown.
- **Priority**: CRITICAL
- **Content**:
  > **You Died**
  > You died. It happens. Probably won't be the last time either.
  >
  > Good news: below Level 20, dying is completely free. No penalty, no lost progress, nothing. Die as much as you want, it's your learning phase. After Level 20 though, every death costs you 50% of your XP progress toward the next level. You can never actually lose a level, just the progress within it. So enjoy the free deaths while they last.
- **Learn More**: `Larsonix:TrailOfOrbis:death-penalty`
- **Wiki Status**: GOOD (7/10)

#### M07: First Ailment
- **Trigger**: Ailment applied to the player (burn, freeze, shock, or poison). Fires on every ailment until shown.
- **Priority**: NORMAL (repeatable)
- **Content** (dynamic, one variant shown based on which ailment triggered it):
  > **Ailment: Burn**
  > You're on fire. That's a Burn ailment. It deals fire damage over time based on how hard you got hit, lasts about 4 seconds. The bigger the hit that caused it, the more it hurts. Fire Resistance from your Earth and gear stats reduces it. You'll see a lot of this one.

  > **Ailment: Freeze**
  > Frozen. That's a Freeze ailment. Your movement and actions are slowed, anywhere from 5% to 30% depending on how hard the hit was relative to your max HP. Lasts 3 seconds. Cold Resistance counters it. Getting frozen in a bad spot is how most deaths start.

  > **Ailment: Shock**
  > Shocked. For the next 2 seconds, you take more damage from everything. Up to 50% more if the hit was big enough. It's the shortest ailment but probably the deadliest because everything else hits harder while it's up. Lightning Resistance is your friend here.

  > **Ailment: Poison**
  > Poisoned. Unlike the other ailments, Poison stacks. Up to 10 times. Each stack does its own damage independently, 30% of the hit that applied it over 5 seconds per stack. At max stacks it gets ugly fast. Poison Resistance reduces it, or just stop getting hit for a few seconds.
- **Learn More**: `Larsonix:TrailOfOrbis:index` (ailments)
- **Wiki Status**: EXCELLENT (9/10)
- **NOTE**: All 4 variants share one milestone. Once ANY ailment popup is shown, the milestone is complete.

#### M08: Skill Sanctum Visit
- **Trigger**: Player enters the Skill Sanctum (`SkillSanctumManager`). Fires on every visit until shown.
- **Priority**: CRITICAL
- **Content**:
  > **The Skill Sanctum**
  > Welcome to the Skill Sanctum. This is your passive skill tree, except it's a 3D world you walk around in. Pretty cool right?
  >
  > Press F on glowing nodes to allocate them. Press F again on an allocated node to remove it (costs a refund point). Click on any node to see exactly what it does. Each arm of the tree matches an element, start near the center and work outward toward whatever fits your build.
  >
  > Messed everything up? Type /too skilltree respec. First 3 are free, after that it costs refund points. Use Orbs of Unlearning to get more.
- **Learn More**: `Larsonix:TrailOfOrbis:index` (skill-tree)
- **Wiki Status**: NEEDS WORK (6/10) — lacks example node effects
- **VERIFIED**: Full respec costs 50% of total node costs in refund points. Individual deallocation costs 1 per node. First 3 respecs free. Orbs of Unlearning grant 1 refund point each.
- **BUG FIXED**: Attribute full reset now also costs 50% refund points (was free before). Fixed in `AttributeManager.resetAllAttributes()`.

#### M09: Mob Scaling Awareness (Level 10)
- **Trigger**: Player reaches level >= 10 (checked on every level up until shown)
- **Priority**: CRITICAL
- **Content**:
  > **The World Gets Tougher**
  > By now you've probably noticed mobs hitting harder than before. That's not random.
  >
  > Mobs in this world scale with distance from spawn. The further out you go, the stronger they get, more HP, more damage, the works. But they also drop better loot and give more XP. It's a constant trade. If something feels too tough, pull back closer to spawn and gear up before pushing further. The world doesn't care about your feelings, only your stats.
- **Learn More**: `Larsonix:TrailOfOrbis:scaling`
- **Wiki Status**: GOOD (8/10)

#### M10: Death Penalty Warning (Level 20)
- **Trigger**: Player reaches level >= 20 (checked on every level up until shown)
- **Priority**: CRITICAL
- **Content**:
  > **Death Penalty Active**
  > This is the one you actually need to read.
  >
  > From this point on, every time you die, you lose 50% of your XP progress toward the next level. Not your total XP, just the progress within your current level. You can never drop a level, but losing half your grind to a stupid death hurts. Trust me. Play careful, keep your gear up to date, and maybe don't fight that Elite at half HP.
- **Learn More**: `Larsonix:TrailOfOrbis:death-penalty`
- **Wiki Status**: GOOD (7/10)

### Phase 3: Realm Era (Level 20-35)

#### M11: First Realm Entered
- **Trigger**: Player teleported into a realm instance. Fires on every realm entry until shown.
- **Priority**: CRITICAL
- **Content**:
  > **Inside a Realm**
  > You're inside a Realm. Timer's ticking, mobs are spawned, let's go.
  >
  > Kill every single mob before the timer runs out. You get 1.5x XP and double the item drops in here compared to the overworld, so it's absolutely worth it. Clear the arena and victory rewards spawn in the center. But if the timer hits zero, you get kicked out and get nothing. Also, dying in here still triggers the death penalty if you're above Level 20, so don't treat it like a safe playground.
- **Learn More**: `Larsonix:TrailOfOrbis:index` (realms)
- **Wiki Status**: GOOD (8/10)
- **VERIFIED**: Death penalty applies in realms. Realm boosts: 1.5x XP, +100% IIQ, 1.2x loot multiplier.

#### M17: Stuck After Realm Completion
- **Trigger**: Player is still inside a completed realm instance 30 seconds after completion. Fires once per player ever.
- **Priority**: CRITICAL
- **Content**:
  > **Hey, You Stuck?**
  > Hey, you stuck? Yeah... WorldGenV2 is quirky, and I write exceptionally good code, so the exit portal being out of reach totally wasn't my fault. Quite sad. But don't worry, I'm a kind guy. Type /too realm exit and you're out. You're welcome.
- **Learn More**: `Larsonix:TrailOfOrbis:index` (realms)
- **Wiki Status**: GOOD (8/10)
- **NOTE**: No invincibility needed, realm is cleared. Timer check: `isCompleted && timeSinceCompletion > 30s && playerStillInside`.

#### M12: Map with 2+ Modifiers
- **Trigger**: Realm map with 2 or more modifiers enters player inventory. Fires on every such map until shown.
- **Priority**: NORMAL (repeatable)
- **Content**:
  > **Map Modifiers**
  > This map has modifiers on it, so it's not a normal run.
  >
  > Prefixes make the realm harder: more mob HP, faster attacks, less time on the clock, that kind of thing. Suffixes make the rewards better: more drops, rarer items, bonus XP, higher elite chance. The harder the map, the more rewarding it is. That's the whole point. If a map looks scary, it's probably worth running.
- **Learn More**: `Larsonix:TrailOfOrbis:modifiers`
- **Wiki Status**: GOOD (8/10)
- **NOTE**: 2+ modifier threshold avoids collision with M05.

#### M13: First Elite Kill
- **Trigger**: Player kills an elite-tier or higher mob. Fires on every elite kill until shown.
- **Priority**: NORMAL (repeatable)
- **Content**:
  > **Elite Mobs**
  > You just took down an Elite. They've got 1.5x the stats of a normal mob and drop 1.5x the XP. Not bad for a warmup.
  >
  > Bosses are another level though: 3x stats, 5x XP. You'll recognize them by the bar above their heads showing their classification. They don't go down easy but the XP is massive. And just between us... what do you think happens when a Boss rolls the Elite modifier?
- **Learn More**: `Larsonix:TrailOfOrbis:elites-bosses`
- **Wiki Status**: GOOD (8/10)

### Phase 4: Depth Discovery (Level 25-50)

#### M14: Larger Realms (Level 25)
- **Trigger**: Player reaches level >= 25 (checked on every level up until shown)
- **Priority**: CRITICAL
- **Content**:
  > **Larger Realms**
  > Your drop pool just expanded. Large Realm Maps can now drop for you.
  >
  > Large means 40 mobs, a 15 minute timer, and a guaranteed boss in the arena. The loot scales with size too, way more drops per run. At level 50, Massive maps unlock: 70 mobs, 20 minutes, two guaranteed bosses, and loot that makes everything before it look like pocket change.
- **Learn More**: `Larsonix:TrailOfOrbis:sizes`
- **Wiki Status**: NEEDS AUDIT

#### M15: Loot Filter Hint
- **Trigger**: Player inventory reaches ~80% capacity (check on every item entering inventory until shown). Only fires after M02 (First Gear) is already complete. Fallback trigger: level >= 7.
- **Priority**: LOW
- **Content**:
  > **Loot Filter**
  > Your inventory's getting full and it's only going to get worse from here.
  >
  > Type /lf to open the Loot Filter. You can set it to hide low rarity drops so they never even show up on the ground. No more picking through 20 grey items to find the one blue. You can set a quick filter with /lf quick rare to only see Rare and above, or build custom rules if you want to get fancy. Change it anytime.
- **Learn More**: `Larsonix:TrailOfOrbis:loot-filter`
- **Wiki Status**: NEEDS AUDIT
- **NOTE**: Inventory capacity check via manual slot iteration (no isFull() API). Fallback: level >= 7.

---

## 7. UI Design

### UI Type: Basic HyUI Page

Simple `InteractiveCustomUIPage` we force open. Player reads, clicks a button, done.

### Invincibility During Guide Popup

**Goal**: Player can't die while reading a guide popup. But this MUST be bulletproof — no exploit path to permanent invincibility.

**Approach: Damage suppression, not god mode**

```
On popup show:
  → Set per-player flag: guidePopupOpen = true
  → In RPGDamageSystem: if guidePopupOpen, set damage to 0 (suppress, don't cancel event)

On popup dismiss ("Got it" or "Learn More"):
  → Clear guidePopupOpen flag
  → Normal damage resumes immediately

Safety valves (defense in depth):
  1. Auto-dismiss after 60 seconds → clears flag
  2. Player disconnect → clears flag (in-memory, dies with session)
  3. Server restart → clears flag (in-memory)
  4. World transition (teleport, realm entry/exit) → clears flag + force-dismiss popup
  5. Player death event → if somehow still reachable, clears flag
```

**Why this can't be exploited:**
- Flag is in-memory only (not persisted) — survives nothing
- 60-second hard timeout means even if dismiss event is lost, invincibility expires
- World transitions clear it (can't carry invincibility between worlds)
- No player action can SET the flag — only `GuideManager.showPopup()` can, and it only fires for uncompleted milestones
- Once a milestone is shown, it's marked complete in DB — can never re-trigger the same popup

**What if the dismiss event never arrives?**
- 60-second safety valve clears `guidePopupOpen` AND `isPopupShowing`
- Player returns to normal, and the milestone is still marked complete (it was shown)
- Worst case: 60 seconds of invincibility, once, for one milestone

### Popup Layout

```
┌──────────────────────────────────────────────────┐
│  Custom UI Page (InteractiveCustomUIPage)         │
│  decorated-container, ~500x200                    │
│  Title: milestone title (e.g., "Your First Gear") │
│                                                    │
│  container-contents:                               │
│    <p> 2-4 lines of guide text </p>               │
│                                                    │
│    [Learn More]                      [Got it]     │
│    secondary-button                  secondary-btn │
│    → closes page + opens Voile       → closes page │
└──────────────────────────────────────────────────┘
```

**"Learn More"**: closes guide page, then dispatches `CommandManager.get().handleCommand(playerRef, "voile Larsonix:TrailOfOrbis:<topic>")`
**"Got it"**: closes guide page + marks milestone complete in DB
**Auto-dismiss**: 60 seconds → force close + mark complete + clear invincibility

### Interaction with Other UIs
- Must NOT conflict with XP bar HUD (it's a HUD, this is a page — no conflict)
- Must NOT open during realm timer (player is in combat) — defer via `isPopupShowing` check
- Must NOT open during death recap — death recap is a page too, would conflict
- Must NOT open during Voile wiki — same issue, page conflict
- Guard: only show if no other custom page is currently open for the player

---

## 8. Technical Architecture

### Package
`io.github.larsonix.trailoforbis.guide`

### Classes

| Class | Responsibility |
|-------|---------------|
| `GuideManager` | Milestone registry, trigger routing, popup lifecycle, `isPopupShowing` + `guidePopupOpen` state, invincibility flag, 60s safety valve timer |
| `GuideMilestone` | Enum of all milestones (17 core + 2 conditional mod compat) with: id, priority, title, content, wiki topic, trigger type |
| `GuidePopupPage` | Custom UI Page (`InteractiveCustomUIPage`) — decorated-container with text + buttons, handles Learn More / Got it events |
| `GuidePlayerState` | Per-player in-memory state: `isPopupShowing`, `guidePopupOpen` (invincibility flag), `currentMilestoneId`, safety valve timestamp |
| `GuideRepository` | DB access: `rpg_guide_milestones` table (check/mark completion) |

### Database

```sql
CREATE TABLE IF NOT EXISTS rpg_guide_milestones (
    player_uuid  CHAR(36) NOT NULL,
    milestone_id VARCHAR(64) NOT NULL,
    completed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (player_uuid, milestone_id)
);
```

### Trigger Integration Points

| Milestone | Hook Location | Detection |
|-----------|--------------|-----------|
| M01 Welcome | `PlayerJoinListener.onPlayerReady()` | `repo.create()` path (new player) |
| M02 First Gear | Inventory change detection | RPG gear item ID pattern in inventory |
| M03 First Level Up | `LevelingManager` level change callback | `newLevel >= 2` |
| M04 First Stone | Inventory change detection | Stone item ID pattern in inventory |
| M05 First Gateway | Vanilla craft event (`CraftRecipeEvent`) | Portal_Device crafted (vanilla Hytale progression) |
| M16 Gear Requirements | `EquipmentListener` equip rejection | Player fails to equip item due to level/attribute reqs |
| M06 First Death | Death handling system | Player death event |
| M07 First Ailment | `AilmentTracker.apply()` | Ailment applied to player entity |
| M08 Sanctum Visit | `SkillSanctumManager` instance creation | Player enters sanctum |
| M09 Level 10 | `LevelingManager` level change | `newLevel >= 10` |
| M10 Level 20 | `LevelingManager` level change | `newLevel >= 20` |
| M11 First Realm | `RealmsManager` realm teleport | Player enters realm instance |
| M12 Modified Map | Inventory change detection | Map item with 2+ modifiers |
| M13 Elite Kill | Mob death attribution | Killed mob has elite+ classification |
| M14 Level 25 | `LevelingManager` level change | `newLevel >= 25` |
| M15 Loot Filter | Inventory change detection | Inventory >= 80% full (fallback: `newLevel >= 7`) |
| M16 Gear Requirements | `EquipmentListener` equip rejection | Player fails to equip item due to level/attribute reqs |
| M17 Stuck in Realm | Realm timer check | `isCompleted && timeSinceCompletion > 30s && playerStillInside` |
| MC01 Hexcode Item | Inventory change + `HexcodeCompat.isLoaded()` | First hex staff/wand/spellbook enters inventory |
| MC02 Loot4Everyone | First chest open + `Loot4EveryoneBridge` available | First container interaction when L4E is active |

### Voile Dependency

**Optional dependency** — guide system works without Voile installed:
- "Learn More" button dispatches `/voile <topic>` via `CommandManager.get().handleCommand()`
- If Voile is not loaded, the command does nothing (player just sees "unknown command")
- Could detect Voile presence and hide the "Learn More" button if absent
- Manifest: `"IwakuraEnterprises:Voile": "*"` (optional)

### Mod Compatibility: Hexcode & Loot4Everyone

If compatible mods are detected at runtime, add extra milestones that briefly explain their integration with Trail of Orbis and link to their own Voile wiki pages (if they have one) or the HytaleModding.dev wiki.

**Detection**: Both already have compat layers in our codebase:
- Hexcode: `HexcodeCompat.isLoaded()` (`compat/HexcodeCompat.java`)
- Loot4Everyone: `Loot4EveryoneBridge` (`compat/Loot4EveryoneBridge.java`) — reflection bridge to L4E's per-player chest API

#### MC01: Hexcode — First Hex Item (conditional)
- **Trigger**: `HexcodeCompat.isLoaded()` is true AND player picks up their first Hexcode item (hex staff, wand, or spellbook). Fires on every hex item pickup until shown.
- **Priority**: NORMAL (repeatable)
- **Content**:
  > **Hexcode Integration**
  > That's a hex weapon. It casts spells. If you're wondering why there's a magic system alongside an RPG mod, it's because this server runs Hexcode too, and they're wired together.
  >
  > Your Water attribute boosts max mana and spell power. Fire adds raw magic power. Lightning makes you cast faster. So if you want to be a battlemage, invest in those three elements and watch both your sword hits and your spells scale up together.
- **Learn More**: Link to Hexcode's Voile wiki if available, otherwise their HM Wiki page
- **NOTE**: Detect hex items via `HexcodeCompat` asset maps. Only fires when Hexcode is loaded AND player acquires hex item.

#### MC02: Loot4Everyone Detected (conditional)
- **Trigger**: `Loot4EveryoneBridge` is available AND player opens their first chest. Fires on every chest open until shown.
- **Priority**: LOW
- **Content**:
  > **Loot4Everyone Active**
  > Quick heads up: every chest in this world has its own loot for each player. You're not racing anyone to open containers, you're not stealing someone else's drops. Each player gets their own items inside every chest. Open everything you see, it's all yours.
- **Learn More**: Link to Loot4Everyone wiki if available
- **NOTE**: Detection via `ContainerLootInterceptor.isLoot4EveryoneManaged()` — already built in.

---

## 9. Wiki Pages — Readiness Status

Voile topic identifiers are `Larsonix:TrailOfOrbis:<filename-without-md>`. Nesting is via `sub-topics` in frontmatter — topic IDs are flat filenames.

| Milestone | Voile Topic ID | Quality | Action |
|-----------|---------------|---------|--------|
| M01 Welcome | `Larsonix:TrailOfOrbis:getting-started` | GOOD (8/10) | Rewrite to match lore framing |
| M02 First Gear | `Larsonix:TrailOfOrbis:rarities` | EXCELLENT (9/10) | Ready |
| M03 First Level Up | `Larsonix:TrailOfOrbis:index` (attributes) | EXCELLENT (10/10) | Ready |
| M04 First Stone | `Larsonix:TrailOfOrbis:index` (stones) | UNKNOWN | **Audit against source** |
| M05 First Gateway | `Larsonix:TrailOfOrbis:map-crafting` | UNKNOWN | **Audit** |
| M06 First Death | `Larsonix:TrailOfOrbis:death-penalty` | GOOD (7/10) | Minor tightening |
| M07 First Ailment | `Larsonix:TrailOfOrbis:index` (ailments) | EXCELLENT (9/10) | Ready |
| M08 Sanctum | `Larsonix:TrailOfOrbis:index` (skill-tree) | NEEDS WORK (6/10) | **Add example node effects** |
| M09 Level 10 | `Larsonix:TrailOfOrbis:scaling` | GOOD (8/10) | Ready |
| M10 Level 20 | `Larsonix:TrailOfOrbis:death-penalty` | GOOD (7/10) | Same as M06 |
| M11 First Realm | `Larsonix:TrailOfOrbis:index` (realms) | GOOD (8/10) | Ready |
| M12 Map Mods | `Larsonix:TrailOfOrbis:modifiers` | GOOD (8/10) | Ready |
| M13 Elite Kill | `Larsonix:TrailOfOrbis:elites-bosses` | GOOD (8/10) | Ready |
| M14 Level 25 | `Larsonix:TrailOfOrbis:sizes` | UNKNOWN | **Audit** |
| M15 Loot Filter | `Larsonix:TrailOfOrbis:loot-filter` | UNKNOWN | **Audit** |

**NOTE**: Some topic IDs are `index` which is ambiguous (attributes/index vs ailments/index vs realms/index). Need to verify how Voile resolves `sub-topics` — the topic might need the parent's sub-topic reference, not the raw `index` ID. Test in-game before finalizing.

**10 of 15 ready or near-ready. 5 need audit/rework.**

---

## 10. Open Questions

### Resolved
- [x] Queue vs priority gate → **No queue. Show most important, defer rest.** Deferred milestones re-fire on next natural occurrence.
- [x] Respec cost → **Stones of Oblivion** (not free)
- [x] Map modifier trigger threshold → **2+ modifiers** to avoid collision with first map
- [x] Realm access → **Gateway chain** (Thorium + Arcanist's Workbench + 100 Memories), not instant activation
- [x] Death in realms → **Death penalty DOES apply in realms** (verified: `XpLossSystem` is global, no realm exemption)
- [x] M01 Welcome → Rewritten with lore framing and directional goal (go south for Thorium)
- [x] M03 Level Up → Now mentions both `/stats` AND `/skilltree`
- [x] M08 Sanctum → Uses actual controls: "Press F to allocate, click to see details"
- [x] M13 Elite Kill → Added fun teaser about Elite Bosses
- [x] M15 Loot Filter → Changed to inventory fullness trigger (~80% capacity, fallback level >= 7)
- [x] Invincibility during popup → Damage suppression with 5-layer safety valve (auto-dismiss, disconnect, restart, world transition, death event)
- [x] Mod compatibility → Conditional milestones for Hexcode and Loot4Everyone when detected
- [x] **HUD vs Custom UI Page** → **Basic HyUI page** we force open. Simple `InteractiveCustomUIPage`, nothing fancy.
- [x] **Gateway chain wording (M05)** → Brief summary is enough. Players see crafting details in the workbenches themselves.
- [x] **Starting stones** → **0**. Players start with no stones. M04 doesn't mention starting inventory.
- [x] **Missing milestone for gateway crafted?** → **No separate milestone.** M05 was MOVED to trigger on first gateway craft instead. That IS the milestone.
- [x] **Loot4Everyone detection** → **Already have `Loot4EveryoneBridge`** (`compat/Loot4EveryoneBridge.java`). Detection is built in.
- [x] **Wiki page topic identifiers** → Verified: flat filenames without `.md`. Format: `Larsonix:TrailOfOrbis:<filename>`. Nesting via `sub-topics` in frontmatter. Potential `index` ambiguity needs in-game testing.
- [x] **M05 trigger** → **Moved from first map pickup to first gateway craft.** Maps drop too early and too rarely — the gateway craft is the real "you're ready" moment. No popup on map pickup.
- [x] **Popup content tone** → **"Your Dev Talking"** — 4th wall, casual, developer's own voice. Humor as personality, not as gimmick. Info is still clear but delivered with character. See Section 12.

### Unresolved
- [ ] **Inventory capacity check (M15)**: How to count filled inventory slots via Hytale API? No `isFull()` method — need to iterate slots manually per container. Fallback: `newLevel >= 7`.
- [ ] **Ambiguous `index` topic IDs**: Multiple pages share `index` as filename (attributes, ailments, realms, skill-tree). Need to test in-game how Voile resolves these — may need unique IDs or parent-scoped references.
- [ ] **Workshop all 16 milestone texts in Option A tone** — M05, M06, M11, M13 are written in tone, others still neutral. Need consistency pass for all 16 + 2 mod compat.
- [ ] **M01 Welcome lore accuracy** — "corrupted world, last hope" framing needs validation against actual game lore. Is this the story we're telling?
- [ ] **CraftRecipeEvent for Portal_Device (M05)**: Need to verify we can hook vanilla crafting events and detect the specific Portal_Device item being crafted.
- [ ] **Equip rejection event (M16)**: Verify how `EquipmentListener.setupArmorValidation()` rejects items — does it fire an event we can hook, or do we need to add a callback?

### Prerequisites (bugs to fix before guide ships)
- [ ] **FIX: Attribute full reset is free** — `AttributeManager.resetAllAttributes()` doesn't deduct refund points. Should cost 50% of total allocated points in attribute refund points (matching skill tree model). See Section 6, M08 notes.

---

## 11. Tone: "Your Dev Talking"

The guide system uses a **4th-wall-breaking, casual developer voice**. Info-first, humor as personality — not cringe, not forced, just how the dev actually talks. Short sentences, fragments, dry wit.

### Rules
- **Info comes first** — every popup teaches something concrete. The humor wraps it, never replaces it.
- **Keep it short** — 2-4 lines max. If it needs more, the wiki page handles the depth.
- **Break the 4th wall when it's funny** — acknowledge that this is a tutorial, that the player is reading a popup, that the dev put this here on purpose. Don't overdo it.
- **Drop lore hints, don't lecture** — M01 can set the stage ("the world is corrupted"), but M02 doesn't need to be in-character. Mix freely.
- **Tease, don't explain** — "And what would happen if a Boss became an Elite...?" is better than "Elite Bosses exist and have 4.5x stats."
- **Match the moment** — death popup can be slightly sarcastic ("happens to the best of us"), first gear can be enthusiastic ("the grey ones are garbage"), level 20 can be ominous ("from now on, dying costs you").

### Tone Examples (draft — all milestones will be workshopped)

**M01 Welcome** (atmospheric + directional):
> The world is corrupted — you're one of its last hopes. Set up a base near spawn, gear up, and prepare to push south toward the desert for Thorium. Mobs get stronger the further you go from spawn. Kill them to earn XP, find gear, and collect Realm Maps.

**M02 First Gear** (enthusiastic + educational):
> Yeah so gear here isn't just "sword go bonk." It has Rarity (that's the color), Quality (1-100, higher is better, obviously), and Modifiers — random stat bonuses that make each piece unique. Keep the shiny ones, trash the grey ones.

**M05 First Gateway** (congratulatory + practical):
> Nice. Place it down, slap a Realm Map on it, and step through the portal. Inside you'll find a timed arena — kill everything before time runs out and the loot is yours. Maps are consumed on use, so pick your fights.

**M06 First Death** (dry, slightly sarcastic):
> Happens to the best of us. Good news: below Level 20, dying is free. Bad news: after Level 20, you lose half your XP progress toward the next level. Your level never drops though, so it's fine. Mostly.

**M13 First Elite Kill** (teasing):
> You just killed something that was trying VERY hard to kill you. Elites have beefed-up stats and give bonus XP. Bosses are even worse. And what would happen if a Boss became an Elite...?

---

## 12. Implementation Order

1. **Workshop all 15 milestone texts** in Option A tone (this doc, Section 6 + Section 11)
2. **Resolve `index` topic ID ambiguity** — test in-game with Voile, may need unique IDs per section
3. **Wiki rework** — fix the 5 pages that need audit/rewrite before guide can link to them
4. **DB table + GuideRepository** — simple CRUD for milestone completion
5. **GuidePopupPage** — HyUI page with decorated-container, text, buttons, event handling
6. **GuideManager** — milestone enum, trigger routing, priority logic, popup lifecycle, invincibility flag + safety valves
7. **Damage suppression hook** — integrate `guidePopupOpen` check into `RPGDamageSystem`
8. **Trigger hooks** — integrate with existing managers (leveling, gear, ailment, realm, sanctum, inventory)
9. **Voile integration** — "Learn More" button dispatches `/voile` command
10. **Mod compat milestones** — conditional Hexcode/Loot4Everyone detection + guide content
11. **Testing** — new player flow, rapid milestone triggers, disconnect/reconnect, server restart, invincibility exploit testing, mod compat on/off
