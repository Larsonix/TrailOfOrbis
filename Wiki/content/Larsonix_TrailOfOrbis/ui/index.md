---
name: Interface Guide
title: Interface Guide
description: Custom UI pages - stats display, attribute allocation, and realm HUD
author: Larsonix
sort-index: 0
order: 130
published: true
---

# Interface Guide

Trail of Orbis adds custom UI pages for managing your character. Every page has navigation buttons linking to every other page - open any one and click your way through the rest.

---

## Character Stats Page

Your complete character profile. All computed stats from every source, organized by category with color-coded values.

### How to Open

```

/stats

```

You can also navigate here from other UI pages using the built-in navigation buttons.

### What You See

The stats page is **800px wide** with scrollable content that resizes to fit all stat categories. It shows your **computed** values - the final numbers after attributes, gear modifiers, quality scaling, and skill tree bonuses are all combined.

```

Final Stat = Base + Attribute Grants + Gear Modifiers + Skill Tree Bonuses

```

Stats update in real-time. Allocating an attribute point, equipping new gear, or changing skill nodes reflects here immediately.

### Color Coding

| Color | Meaning |
|-------|---------|
| **Green** | Positive bonus (above base) |
| **Red** | Negative value (below base or penalty) |

### Navigation

| Button | Opens |
|--------|-------|
| **Attributes** | Attribute allocation page |
| **Skill Tree** | Enters the Skill Sanctum |
| **Close** | Closes the stats page |

> [!TIP]
> `/stats` is the best single command to learn. From here you can reach attribute allocation and the Skill Sanctum, covering all 3 core progression systems from one entry point.

---

## Attribute Allocation Page

Spend your unallocated attribute points across the 6 elements. Every allocation is previewed before committing, so you can experiment without risk.

### How to Open

```

/attr

```

You can also click the **Attributes** button from the stats page.

### How It Works

The attribute page is **700px wide and 531px tall**. It shows all 6 elements with their current point allocations and provides controls for adjusting.

1. **View your current allocation** across each element
2. **See available points** ready to spend
3. **Use +1/+5 and -1 buttons** to adjust points per element
4. **Delta display** shows pending changes for each element
5. **Save or Cancel** to commit or discard everything

### Allocation Controls

| Button | Action |
|--------|--------|
| **-1** | Remove one pending point from this element |
| **+1** | Add one pending point to this element |
| **+5** | Add 5 pending points to this element |

These buttons only affect the pending allocation. Nothing is committed until you press Save.

### The Save/Cancel Workflow

| Button | Action |
|--------|--------|
| **Save** | Commit all pending allocations, stats are applied |
| **Cancel** | Discard all pending allocations, nothing changes |

This prevents accidental misclicks. Allocate points across multiple elements, preview the total effect via the delta display, then commit everything or cancel without losing anything.

### Quick Allocation via Commands

For precise allocation without the UI :

```

/too attr allocate fire         - +1 Fire
/too attr unallocate wind 5     - -5 Wind
/too attr reset                 - Full respec

```

> [!TIP]
> The UI is best for exploring what each element does and previewing stat changes. Commands are best for quick bulk allocation when you already know your build.

---

## Realm HUD

While inside a Realm, a dedicated HUD shows your session info : time remaining, mobs killed, and realm properties.

### When It Appears

The Realm HUD is visible during **active realm sessions** only. It appears when you enter a Realm and disappears when you exit (on completion, timeout, or emergency exit).

### HUD Elements

| Element | Shows | Updates |
|---------|-------|---------|
| **Timer** | Time remaining (e.g., "4:23") | Every second, counts down |
| **Kill Counter** | Mobs killed / Total (e.g., "18/25") | On each kill |
| **Realm Info** | Biome, level, active modifiers | Static for the session |

### Timer

| Size | Starting Time |
|------|---------------|
| Small | 5:00 (300 seconds) |
| Medium | 10:00 (600 seconds) |
| Large | 15:00 (900 seconds) |
| Massive | 20:00 (1200 seconds) |

The **Reduced Time** prefix modifier can decrease the starting time by 10-40%.

When the timer reaches 0 :
- You're exited from the Realm automatically
- Victory rewards are forfeited
- Loot from individual kills during the session is kept

### Kill Counter

Shows how many mobs you've killed versus the total required. When the counter hits 100% (last mob dies), the Realm transitions to the ENDING state and triggers the victory reward sequence.

The total mob count scales with Realm level :

```

actualMonsters = baseCount * (1 + level * 0.02)

```

### Completion Tracking

Behind the HUD, the system tracks detailed metrics :

| Metric | Description |
|--------|-------------|
| Total monsters | Total mobs spawned in this instance |
| Remaining monsters | Mobs still alive |
| Per-player kills | Kill count per player |
| Per-player damage | Damage dealt per player |
| Elites killed | Number of elite mobs killed |
| Bosses killed | Number of boss mobs killed |

> [!NOTE]
> You can also view Realm information via `/too realm info` in chat. The HUD provides the same core info in a persistent visual format during combat.

---

## Related Pages

- [Commands](commands) - Full command reference including UI shortcuts
- [The 6 Elements](attributes) - What each element does when you allocate points
- [Realm Rewards](realm-rewards) - What happens when the timer runs out or you complete a Realm
- [Getting Started](getting-started) - How to access these UI pages for the first time
