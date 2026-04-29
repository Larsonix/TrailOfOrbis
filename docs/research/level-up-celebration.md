# Research: Level-Up Celebration Overhaul

> Full technical research for Polish Roadmap Item #1.
> Covers: fullscreen banner, sound effects, detailed chat breakdown, milestone flair.

---

## Current Implementation

### What Happens Today

When a player levels up, `LevelingManager.addXp()` detects the level change and calls `LevelingEventDispatcher.dispatchLevelUp()`, which notifies all registered `LevelUpListener`s.

**Two listeners are registered:**

1. **Main plugin** (`TrailOfOrbis.java:842-863`) — grants attribute points + skill points, then calls `sendLevelUpNotification()`
2. **XpBarHudManager** (`XpBarHudManager.java:136`) — refreshes the XP bar HUD

### Current Notification (`TrailOfOrbis.java:2382-2423`)

**Chat message:**
```
* LEVEL UP! * You are now level 50! (+1 Attribute Points)
[GOLD]        [WHITE]              [GOLD]  [GRAY][GREEN]         [GRAY]
```

**Toast notification** (top-right popup):
- Title: `"Level Up !"` (gold)
- Subtitle: `"Level 50 (+1 Attribute Points)"`
- Style: `NotificationStyle.Success` (green checkmark)

### Data Available at Level-Up Time

The `LevelUpListener` callback receives:
- `playerId` (UUID)
- `newLevel` (int)
- `oldLevel` (int)
- `totalXp` (long)

**Derived in the listener:**
- `levelsGained = newLevel - oldLevel` (can be > 1 from admin commands)
- `attrPoints = levelsGained * config.getAttributes().getPointsPerLevel()` (default: 1/level)
- `skillPoints = levelsGained * config.getSkillTree().getPointsPerLevel()` (default: 1/level)

**Queryable after granting:**
- `data.getUnallocatedPoints()` — total unspent attribute points (via `AttributeManager`)
- `skillTreeManager.getAvailablePoints(playerId)` — total unspent skill points (via `SkillTreeManager`)

### Where the Code Lives

```
src/main/java/io/github/larsonix/trailoforbis/
├── leveling/
│   ├── core/LevelingManager.java           # addXp() → level detection → dispatch
│   ├── core/LevelingEventDispatcher.java   # dispatchLevelUp() → all listeners
│   ├── api/LevelingEvents.java             # LevelUpListener interface
│   └── api/LevelingService.java            # Public API
├── ui/hud/XpBarHudManager.java             # XP bar refresh on level-up
├── attributes/AttributeManager.java        # modifyUnallocatedPoints(), getUnallocatedPoints()
├── skilltree/SkillTreeManager.java         # grantSkillPoints(), getAvailablePoints()
├── util/MessageColors.java                 # Color constants (LEVEL_UP = #FFD700)
└── TrailOfOrbis.java                       # Level-up listener + sendLevelUpNotification()
```

---

## Feature 1: Fullscreen Banner

### API: `EventTitleUtil`

**Import:** `com.hypixel.hytale.server.core.util.EventTitleUtil`

**Simple call (4 params):**
```java
EventTitleUtil.showEventTitleToPlayer(playerRef, primaryTitle, secondaryTitle, isMajor);
```

**Full control (8 params):**
```java
EventTitleUtil.showEventTitleToPlayer(
    playerRef,          // PlayerRef — target player
    primaryTitle,       // Message — large center text
    secondaryTitle,     // Message — smaller subtitle below
    isMajor,            // boolean — true = decorative/large, false = subtle
    iconPath,           // String (nullable) — asset path to PNG icon
    duration,           // float — visible time in seconds (default 4.0f)
    fadeInDuration,     // float — fade-in time in seconds (default 1.5f)
    fadeOutDuration     // float — fade-out time in seconds (default 1.5f)
);
```

**Other variants:**
- `showEventTitleToWorld(...)` — all players in a world
- `showEventTitleToUniverse(...)` — all players everywhere
- `hideEventTitleFromPlayer(playerRef, fadeOutDuration)` — manual dismiss

**Constants:**
- `EventTitleUtil.DEFAULT_DURATION` = 4.0f
- `EventTitleUtil.DEFAULT_FADE_DURATION` = 1.5f

### Message Styling

The `Message` API supports:
- `Message.raw("text")` — plain text
- `.color("#RRGGBB")` — hex color
- `.bold(true)` — bold text
- `.italic(true)` — italic text
- `.insert(Message)` — append child message (for multi-style text)

### Proposed Banner Design

**Normal level-up:**
```
Primary:   "LEVEL 50"          (gold #FFD700, bold)
Subtitle:  "+1 Attribute Point, +1 Skill Point"  (white, normal)
isMajor:   false               (subtle style — frequent event)
Duration:  2.5s                (short, not intrusive)
FadeIn:    0.5s
FadeOut:   1.0s
Icon:      null                (no icon for normal levels)
```

**Milestone level-up (every 10, 25, 50, 100...):**
```
Primary:   "LEVEL 50!"         (gold #FFD700, bold)
Subtitle:  "Milestone Reached!" (green #55FF55)
isMajor:   true                (decorative style — rare event)
Duration:  4.0s                (longer, celebratory)
FadeIn:    1.0s
FadeOut:   1.5s
Icon:      null (or custom milestone icon if we add one to asset pack)
```

### Code Pattern

```java
// Normal level-up banner
Message title = Message.raw("LEVEL " + newLevel).color(MessageColors.LEVEL_UP).bold(true);
Message subtitle = Message.raw("+" + attrPoints + " Attribute Point" + (attrPoints != 1 ? "s" : ""))
    .color(MessageColors.WHITE)
    .insert(Message.raw(", +" + skillPoints + " Skill Point" + (skillPoints != 1 ? "s" : ""))
        .color(MessageColors.WHITE));

EventTitleUtil.showEventTitleToPlayer(
    playerRef, title, subtitle,
    false,   // isMajor
    null,    // no icon
    2.5f,    // duration
    0.5f,    // fadeIn
    1.0f     // fadeOut
);
```

### Thread Safety

All `EventTitleUtil` calls must be on the world thread. Since the level-up listener fires from `addXp()` which may not be on the world thread, wrap in:
```java
World world = PlayerWorldCache.findPlayerWorld(playerId);
if (world != null) {
    world.execute(() -> {
        EventTitleUtil.showEventTitleToPlayer(...);
    });
}
```

**Note:** The existing `sendLevelUpNotification()` already handles `PlayerRef` lookup via `PlayerWorldCache.findPlayerRef()`. The banner call should go in the same method, reusing the same `playerRef`.

---

## Feature 2: Level-Up Sound Effect

### API: `SoundUtil`

**Import:** `com.hypixel.hytale.server.core.universe.world.SoundUtil`

**Per-player 2D sound (no position attenuation — best for notifications):**
```java
SoundUtil.playSoundEvent2dToPlayer(
    playerRef,          // PlayerRef — target player
    soundEventIndex,    // int — resolved sound index
    soundCategory       // SoundCategory — SFX or UI
);

// With volume/pitch:
SoundUtil.playSoundEvent2dToPlayer(
    playerRef, soundEventIndex, soundCategory,
    volumeModifier,     // float — 1.0 = normal
    pitchModifier       // float — 1.0 = normal, >1 = higher pitch
);
```

**Sound index resolution:**
```java
import com.hypixel.hytale.protocol.SoundEvent;
import com.hypixel.hytale.protocol.SoundCategory;

int index = SoundEvent.getAssetMap().getIndex("SFX_Discovery_Z1_Medium");
if (index == 0) {
    // Sound not found — index 0 means invalid
    LOGGER.atWarning().log("Sound not found: SFX_Discovery_Z1_Medium");
    return;
}
```

**SoundCategory enum values:** `Music`, `Ambient`, `SFX`, `UI`

### Vanilla Sound Candidates

All sounds listed below exist in the vanilla asset catalog (`docs/reference/vanilla-asset-catalog.md`).

#### Discovery Sounds (Best for Level-Ups)

| Sound ID | Character | Notes |
|----------|-----------|-------|
| `SFX_Discovery_Z1_Short` | Quick joyful chime | Good for normal levels |
| `SFX_Discovery_Z1_Medium` | Longer discovery fanfare | Good for normal levels (slightly more dramatic) |
| `SFX_Discovery_Z2_Medium` | Zone 2 variant (different pitch) | Good for mid-range milestones |
| `SFX_Discovery_Z3_Medium` | Zone 3 variant | Good for higher milestones |
| `SFX_Discovery_Z4_Medium` | Zone 4 variant (most dramatic) | Good for major milestones |

**Properties of Discovery sounds:**
- `PreventSoundInterruption: true` — won't be cut off by other sounds
- `MusicDuckingVolume: -3` — background music lowers momentarily (cinematic!)
- `AudioCategory: AudioCat_Discovery`

#### Unlock/Complete Sounds (Alternatives)

| Sound ID | Character | Notes |
|----------|-----------|-------|
| `SFX_Memories_Unlock_Local` | Magical unlock | More mystical/arcane feel |
| `SFX_Workbench_Upgrade_Complete_Default` | Upgrade done | Simpler "task complete" chime |
| `SFX_Furnace_Bench_Processing_Complete` | Processing done | Subtle completion sound |
| `SFX_Chest_Legendary_FirstOpen_Player` | Epic chest open | Very dramatic — best for huge milestones |

### Recommended Sound Mapping

| Level-Up Type | Sound | Rationale |
|---------------|-------|-----------|
| Normal (every level) | `SFX_Discovery_Z1_Short` | Quick, satisfying, not annoying at high frequency |
| Milestone (10, 25) | `SFX_Discovery_Z1_Medium` | Slightly longer fanfare |
| Major milestone (50, 100) | `SFX_Discovery_Z3_Medium` | More dramatic zone variant |
| Huge milestone (250, 500, 1000) | `SFX_Chest_Legendary_FirstOpen_Player` | Maximum impact for rare events |

### Sound Pitch Variation (Optional Polish)

Use `pitchModifier` to make higher levels sound slightly more dramatic:
```java
float pitch = 1.0f + (newLevel / 500.0f) * 0.3f;  // Ranges from 1.0 to ~1.3 over 500 levels
SoundUtil.playSoundEvent2dToPlayer(playerRef, index, SoundCategory.UI, 1.0f, pitch);
```

### Code Pattern

```java
private void playLevelUpSound(PlayerRef playerRef, int newLevel, boolean isMilestone) {
    String soundKey = isMilestone
        ? config.getMilestoneSoundKey()   // e.g. "SFX_Discovery_Z3_Medium"
        : config.getNormalSoundKey();     // e.g. "SFX_Discovery_Z1_Short"

    int soundIndex = SoundEvent.getAssetMap().getIndex(soundKey);
    if (soundIndex == 0) {
        LOGGER.atWarning().log("Level-up sound not found: %s", soundKey);
        return;
    }

    SoundUtil.playSoundEvent2dToPlayer(playerRef, soundIndex, SoundCategory.UI);
}
```

---

## Feature 3: Detailed Chat Breakdown

### Available Data for Breakdown

After granting points in the level-up listener, we can query:

| Data | Source | Method |
|------|--------|--------|
| Levels gained | Listener params | `newLevel - oldLevel` |
| Attr points earned | Config | `levelsGained * config.getAttributes().getPointsPerLevel()` |
| Skill points earned | Config | `levelsGained * config.getSkillTree().getPointsPerLevel()` |
| Total unspent attr points | DB | `playerDataRepo.getData(playerId).getUnallocatedPoints()` |
| Total unspent skill points | DB | `skillTreeManager.getAvailablePoints(playerId)` |

### Message API Capabilities

- **Colors:** hex codes via `.color("#RRGGBB")` or constants from `MessageColors`
- **Bold/Italic:** `.bold(true)`, `.italic(true)`
- **Multi-line:** `\n` in `Message.raw()` works
- **Chaining:** `.insert(Message)` appends styled child messages
- **Immutable:** each method returns a new `Message` — reassign on insert

### Proposed Chat Format

**Normal level-up:**
```
═══════ LEVEL UP ═══════
  Level 49 → 50
  +1 Attribute Point (14 available)
  +1 Skill Point (5 available)
═════════════════════════
```

**Color scheme:**
- Header/footer bars: `LEVEL_UP` (#FFD700, gold)
- "Level X → Y": old level in `GRAY`, arrow in `WHITE`, new level in `LEVEL_UP`
- "+N Attribute Point": amount in `SUCCESS` (#55FF55), label in `WHITE`
- "(14 available)": `GRAY`
- "+N Skill Point": amount in `SUCCESS`, label in `WHITE`

**Multi-level jump (from admin command or XP burst):**
```
═══════ LEVEL UP ═══════
  Level 45 → 50 (+5 levels!)
  +5 Attribute Points (14 available)
  +5 Skill Points (10 available)
═════════════════════════
```

### Code Pattern

```java
private Message buildLevelUpChatMessage(int oldLevel, int newLevel, int attrPoints,
        int skillPoints, int totalAttrAvailable, int totalSkillAvailable) {
    int levelsGained = newLevel - oldLevel;

    Message msg = Message.empty()
        // Header
        .insert(Message.raw("═══════ LEVEL UP ═══════\n").color(MessageColors.LEVEL_UP))
        // Level line
        .insert(Message.raw("  Level ").color(MessageColors.WHITE))
        .insert(Message.raw(String.valueOf(oldLevel)).color(MessageColors.GRAY))
        .insert(Message.raw(" → ").color(MessageColors.WHITE))
        .insert(Message.raw(String.valueOf(newLevel)).color(MessageColors.LEVEL_UP).bold(true));

    // Multi-level indicator
    if (levelsGained > 1) {
        msg = msg.insert(Message.raw(" (+" + levelsGained + " levels!)").color(MessageColors.WARNING));
    }
    msg = msg.insert(Message.raw("\n").color(MessageColors.WHITE));

    // Attribute points line
    if (attrPoints > 0) {
        msg = msg
            .insert(Message.raw("  +").color(MessageColors.SUCCESS))
            .insert(Message.raw(String.valueOf(attrPoints)).color(MessageColors.SUCCESS))
            .insert(Message.raw(" Attribute Point" + (attrPoints != 1 ? "s" : "")).color(MessageColors.WHITE))
            .insert(Message.raw(" (" + totalAttrAvailable + " available)\n").color(MessageColors.GRAY));
    }

    // Skill points line
    if (skillPoints > 0) {
        msg = msg
            .insert(Message.raw("  +").color(MessageColors.SUCCESS))
            .insert(Message.raw(String.valueOf(skillPoints)).color(MessageColors.SUCCESS))
            .insert(Message.raw(" Skill Point" + (skillPoints != 1 ? "s" : "")).color(MessageColors.WHITE))
            .insert(Message.raw(" (" + totalSkillAvailable + " available)\n").color(MessageColors.GRAY));
    }

    // Footer
    msg = msg.insert(Message.raw("═════════════════════════").color(MessageColors.LEVEL_UP));

    return msg;
}
```

---

## Feature 4: Milestone Detection & Extra Flair

### Milestone Tiers

| Tier | Levels | Extra Flair |
|------|--------|-------------|
| **Huge** | 250, 500, 1000 | `isMajor` banner, dramatic sound, special chat message |
| **Major** | 50, 100 | `isMajor` banner, medium-dramatic sound |
| **Minor** | Every 10th (10, 20, 30...) | Normal banner with slightly longer duration |
| **Normal** | Everything else | Subtle banner, quick sound |

### Detection Logic

```java
private MilestoneTier getMilestoneTier(int level) {
    if (level % 250 == 0) return MilestoneTier.HUGE;      // 250, 500, 750, 1000...
    if (level % 50 == 0)  return MilestoneTier.MAJOR;     // 50, 100, 150, 200...
    if (level % 10 == 0)  return MilestoneTier.MINOR;     // 10, 20, 30, 40...
    return MilestoneTier.NORMAL;
}
```

### Milestone-Specific Behavior

| Property | Normal | Minor (×10) | Major (×50) | Huge (×250) |
|----------|--------|-------------|-------------|-------------|
| Banner `isMajor` | false | false | true | true |
| Banner duration | 2.5s | 3.0s | 4.0s | 5.0s |
| Banner fadeIn | 0.5s | 0.5s | 1.0s | 1.0s |
| Banner fadeOut | 1.0s | 1.0s | 1.5s | 1.5s |
| Sound | `SFX_Discovery_Z1_Short` | `SFX_Discovery_Z1_Medium` | `SFX_Discovery_Z3_Medium` | `SFX_Chest_Legendary_FirstOpen_Player` |
| Chat extras | Standard | Standard | "Milestone Reached!" line | "MAJOR MILESTONE!" line |

### Config Design

Add to `leveling.yml`:

```yaml
# Level-up celebration settings
celebration:
  # Fullscreen banner
  banner:
    enabled: true
    # Normal level-up banner
    normal:
      duration: 2.5
      fade_in: 0.5
      fade_out: 1.0
    # Milestone banner (every 10th level)
    minor_milestone:
      duration: 3.0
      fade_in: 0.5
      fade_out: 1.0
    # Major milestone (every 50th level)
    major_milestone:
      duration: 4.0
      fade_in: 1.0
      fade_out: 1.5
    # Huge milestone (every 250th level)
    huge_milestone:
      duration: 5.0
      fade_in: 1.0
      fade_out: 1.5

  # Sound effects
  sound:
    enabled: true
    normal: "SFX_Discovery_Z1_Short"
    minor_milestone: "SFX_Discovery_Z1_Medium"
    major_milestone: "SFX_Discovery_Z3_Medium"
    huge_milestone: "SFX_Chest_Legendary_FirstOpen_Player"

  # Chat breakdown
  chat:
    enabled: true
    # Show total available points in parentheses
    show_totals: true
    # Show decorative header/footer bars
    show_borders: true

  # Milestone level intervals
  milestones:
    minor: 10     # Every 10th level
    major: 50     # Every 50th level
    huge: 250     # Every 250th level
```

---

## Implementation Plan

### Step 1: Config Extension

Add `CelebrationConfig` class to `leveling/config/` to map the new YAML section. Nest under existing `LevelingConfig`.

### Step 2: Refactor `sendLevelUpNotification()`

The current method in `TrailOfOrbis.java:2382-2423` does everything inline. Refactor into a dedicated `LevelUpCelebrationService` class that handles:
- Milestone tier detection
- Banner display (EventTitleUtil)
- Sound playback (SoundUtil)
- Rich chat message building

**New class location:** `src/main/java/io/github/larsonix/trailoforbis/leveling/LevelUpCelebrationService.java`

### Step 3: Update Level-Up Listener

Change the listener in `TrailOfOrbis.java:842-863` to pass all needed data:
- Call `celebrationService.celebrate(playerId, oldLevel, newLevel, attrPoints, skillPoints)` instead of `sendLevelUpNotification()`
- The service queries total available points internally

### Step 4: Wire It Up

- Construct `LevelUpCelebrationService` after `LevelingManager`, `AttributeManager`, and `SkillTreeManager` are initialized
- Pass config, `PlayerWorldCache`, attribute repo, skill tree manager as dependencies

---

## API Import Reference

All imports needed for the new features:

```java
// Banner
import com.hypixel.hytale.server.core.util.EventTitleUtil;

// Sound
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.protocol.SoundEvent;
import com.hypixel.hytale.protocol.SoundCategory;

// Notifications (existing, keep)
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.server.core.util.NotificationStyle;

// Messages (existing, keep)
import com.hypixel.hytale.server.core.message.Message;
```

**Source paths (for decompiled reference):**
- `EventTitleUtil` → `com/hypixel/hytale/server/core/util/EventTitleUtil.java`
- `SoundUtil` → `com/hypixel/hytale/server/core/universe/world/SoundUtil.java`
- `SoundEvent` → `com/hypixel/hytale/protocol/SoundEvent.java`
- `SoundCategory` → `com/hypixel/hytale/protocol/SoundCategory.java`

---

## Risk Assessment

| Risk | Likelihood | Mitigation |
|------|-----------|------------|
| Sound key doesn't exist in game version | Low | Validate at startup, log warning, graceful fallback |
| Banner overlaps with other titles (realm entry, etc.) | Medium | Short durations for normal levels; one banner at a time is engine-enforced |
| Performance impact from frequent level-ups | Very Low | All calls are lightweight packets; no tick systems |
| Thread safety (level-up fires off world thread) | Medium | Wrap banner/sound calls in `world.execute()` |

---

## Testing Plan

### Manual Tests

1. **Normal level-up**: Use `/tooadmin xp add <player> <amount>` to trigger a single level-up
   - Verify: banner appears (subtle style), sound plays, chat shows breakdown with correct point counts
2. **Multi-level jump**: Add enough XP to jump 5+ levels at once
   - Verify: banner shows final level, chat shows "+5 levels!" and correct total points granted
3. **Milestone levels**: Set player to level 9, then level up to 10
   - Verify: slightly longer banner, different sound, no special chat line (minor milestone)
4. **Major milestone**: Set player to level 49, then level up to 50
   - Verify: `isMajor` banner (decorative), dramatic sound, "Milestone Reached!" in chat
5. **Config toggles**: Disable banner/sound/chat independently in config, verify each toggle works
6. **Sound validation**: Change sound key in config to invalid value, verify warning logged + no crash

### Commands for Testing

```
/tooadmin level set <player> 9       # Set to level 9
/tooadmin xp add <player> 99999     # Trigger level up to 10+
/tooadmin level set <player> 49      # Set to 49 for milestone test
/tooadmin xp add <player> 99999     # Trigger level 50 milestone
```

---

## Summary

| Feature | API | Ready? | Complexity |
|---------|-----|--------|------------|
| Fullscreen banner | `EventTitleUtil.showEventTitleToPlayer()` | Yes — API is documented and stable | Low |
| Sound effects | `SoundUtil.playSoundEvent2dToPlayer()` | Yes — 1,168 vanilla sounds available | Low |
| Rich chat breakdown | `Message` API with `.color()`, `.bold()`, `.insert()` | Yes — pattern established in death recap | Low-Medium |
| Milestone detection | Simple modulo math + config | Yes | Very Low |
| Config integration | New YAML section in `leveling.yml` | Yes — follows existing config pattern | Low |

**Total estimated effort: Medium** — mostly new code, no refactoring of existing systems needed. The level-up listener is a clean extension point.
