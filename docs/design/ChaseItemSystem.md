# Chase Item System

## Motivation

- Need excessive long-term goals for endgame players
- Playtesters (including best friend) are already over level 200
- Inspired by BDO (Black Desert Online) — players spend 10k hours farming one item
- The friend loves that about BDO

## Core Concept

- Every single biome has a "Chase Item" tied to it
- It's a unique item with a unique effect
- When a player has it (wherever in inventory, hotbar, or backpack), it activates
- Some effects are always-on (infinite potions — no reason to ever turn off)
- Some effects are toggleable via right-click (surface immunity — sometimes you want ice sliding)
- Whether an effect is toggle or always-on is per-item based on common sense

## Acquisition

- Players must collect 10 identical "parts" of the chase item
- Parts drop from any mob in that biome's realms
- Drop chance: 1 in 20,000 per mob kill
- Drop chance is a pure flat rate — NOT affected by:
  - Player bonuses
  - Map modifiers
  - Item quantity bonuses
  - Item rarity bonuses
  - Any other multiplier
- Drop chance VERY SLIGHTLY increases with the level of the map — just enough to prevent players farming level 1 maps for parts

## Assembly

- Parts are a single item type per chase item, max stack size 10
- Tooltip evolves as you collect more (e.g., 3/10, 7/10)
- When player has 10/10, right-click to transform into the final chase item
- On successful assembly: banner, global chat message, animation, particles — the whole festival, fully personalized per item
- Implementation: similar to Stones (not like maps or gear — not RPG items codewise)

## Biomes (9 Active)

| # | Biome | Effect |
|---|-------|--------|
| 1 | Forest | Infinite health potion |
| 2 | Desert | Infinite mana potion |
| 3 | Volcano | Infinite gear stash |
| 4 | Tundra | Infinite map stash |
| 5 | Beach | Infinite stone bag |
| 6 | Jungle | Extended pickup radius |
| 7 | Caverns | Navi light |
| 8 | Frozen Crypts | Surface nullification |
| 9 | Sand Tombs | Auto-identify |

Excluded: Void, Swamp, Corrupted (not active), Mountains (no map), Skill Sanctum (utility)

Saved for future biome: Auto-loot to stash

## Effects (10 Confirmed, Unassigned to Biomes)

1. Infinite health potion — auto-activates every 5s if in inventory AND activated (toggleable on/off)
2. Infinite mana potion — auto-activates every 5s if in inventory AND activated (toggleable on/off)
3. Surface nullification (slippery ice, slowing mud, etc.) — toggleable
4. Infinite stone bag — right-click to open (see details below)
5. Infinite gear stash — placeable furniture (see details below)
6. Infinite map stash — placeable furniture (see details below)
7. Navi light (glowing orb follows player like Navi in Zelda) — toggleable
8. Extended pickup radius — always-on
9. Auto-identify (all gear entering inventory is automatically identified without needing a stone) — always-on
10. Auto-loot to stash — two modes (see details below)

### Future Ideas (Not In Current 10)

11. Remote container access — link to a container by punching, right-click to open it from anywhere

## Chase Item Details

### Infinite Potions (Health & Mana)

- If the chase item is in the player's inventory and activated, it auto-activates every 5 seconds
- Restores 20% of max health / max mana per tick
- No right-click needed, no manual use — just passively restores every 5s
- Toggleable on/off

### Stone Bag

- Right-click to open
- Opens vanilla inventory (with backpack open if player has one) + our stone UI on the right side
- Player has access to everything at once (their inventory + all stored stones)
- All stones dropped/picked up by player automatically go into the bag
- Infinite stone storage
- Can right-click any stone in this UI to "use" it directly (no need to have it in-hand)

### Gear Stash (Placeable Furniture)

- A block the player places in the world
- Infinite storage for gear/weapons/offhands
- Simple numbered pages (page 1, 2, 3...) — pages are just pagination of current view
- Filters change what's shown, pages adjust accordingly
- Favorites system — mark items to find them quickly
- No tabs, no renaming, no color-coding — organization comes from filters
- UI is paginated (only current page loaded at a time — zero performance concern even with 10K+ items)
- Shows player's vanilla inventory on the left so they can equip directly from stash (if possible)
- **ABSOLUTE PROTECTION**: No way to destroy this block other than the player who placed it
  - Immune to TNT/explosions
  - Immune to other players breaking it
  - Immune to other mods destroying it
  - Immune to any conceivable griefing vector
  - ONLY the original player who placed it can remove it
  - Must be protected against everything and anything

### Map Stash (Placeable Furniture)

- A block the player places in the world
- Infinite storage for realm maps
- Simple numbered pages (page 1, 2, 3...) — pages are just pagination of current view
- Filters (by biome, level, rarity, etc.) change what's shown, pages adjust accordingly
- Favorites system — mark maps to find them quickly
- No tabs, no renaming, no color-coding — organization comes from filters
- UI is paginated (only current page loaded at a time — zero performance concern even with 10K+ items)
- Cannot activate maps directly from stash (must take them out first)
- **ABSOLUTE PROTECTION**: No way to destroy this block other than the player who placed it
  - Immune to TNT/explosions
  - Immune to other players breaking it
  - Immune to other mods destroying it
  - Immune to any conceivable griefing vector
  - ONLY the original player who placed it can remove it
  - Must be protected against everything and anything

### Navi Light

- Glowing orb that follows the player (like Navi in Zelda)
- Properly illuminates surrounding blocks — real light source, not just cosmetic
- Toggleable on/off

### Extended Pickup Radius

- 2x the default pickup radius
- Always-on

### Auto-Identify

- All gear entering the player's inventory is automatically identified without needing a stone
- Always-on

### Auto-Loot to Stash

- Has 2 modes instead of on/off: **Map mode** and **Gear mode**
- Punch (hit) any chest with the item in the selected mode to link that chest as the target
- In Gear mode: any gear/weapon/offhand looted goes directly to the linked chest
- In Map mode: any map looted goes directly to the linked chest
- If the linked chest is full, items go to player inventory instead (fallback)
- Fully compatible with the infinite gear stash and infinite map stash chase items (ideal targets)
- **Open technical questions:**
  - What happens if the chest's chunk is not loaded? (DB-level storage? Queue until loaded?)
  - How to ensure no item is ever corrupted or lost during transfer
  - How to ensure no item is modified/duplicated during the process
  - Full compatibility with our infinite stash chase items

### Remote Container Access

- Left-punch any container to link it to this item
- Right-click the item from anywhere to open that linked container remotely
- Same technical questions as auto-loot: chunk loading, item safety
- Works with any container (vanilla chests, our infinite stashes, etc.)

## Design Constraints

- Chase items must NEVER give offensive or defensive boosts
- Effects must be purely QOL / Utility
- Must be universally desirable — things almost everyone wants
- Must integrate perfectly in both Hytale and our mod

## Visuals

- Every single chase item has a different unique design (final item)
- Every single chase item has a different unique "part" design
- All parts of the same item look identical (just stack to 10)
- Art by LadyPaladra (later)

## Open Questions

- Assign effects to biomes
- Assembly animation/particles specifics per item
- Global chat message format
- Exact scaling formula for map level → drop chance boost
