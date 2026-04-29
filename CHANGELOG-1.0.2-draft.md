## Trail of Orbis v1.0.2 - Patch Notes

---

### Combat

- Blocking reworked from scratch. Holding block with a weapon reduces damage by **33%**, with a shield it's **66%**. **Block Chance** is now a % chance to **Perfect Block** the hit and take zero damage when you're actively blocking.
- Passive block chance removed. The old random block that fired without holding a shield was just a worse Dodge. Gone.
- **Block Heal** and **Block Counter** stats actually work now. Right gear (like "Bulwark" prefix on shields) or skill nodes can heal you or reflect damage back when you block.
- Offhand item stats (shields, spellbooks, etc.) no longer vanish when you're not using the offhand. Stats stay as long as the item is equipped.
- Fixed player max health flickering when changing equipment.
- Combat log (`/too combat detail`) now shows "Perfect Block" or "Weapon Block / Shield Block" with the exact reduction %. Damage numbers are the real final amount after everything, blocking included.
- Death recap now accounts for block damage reduction.
- **Bonus stamina regen stops while blocking**, matching vanilla Hytale. Before this, Stamina Regen kept ticking during block, which defeated the point of stamina costs.
- **Bonus mana regen stops while charging an attack.** No more free mana during charge-up.
- **Bonus oxygen regen stops while suffocating.** If your head is underwater or inside a block, bonus regen pauses.

### Gear & Crafting

- **Crafting preview tooltips**: Vanilla weapons and armor now show RPG info in their tooltip before you craft. Level range, max rarity, modifier count. The misleading vanilla stats (Physical Resistance, etc.) are gone, replaced with a color-coded preview.
- Crafted gear level is based on the **material's origin zone** now, not your player level. Wood = early zone, iron = mid zone, mithril = late zone. Gear from a zone matches the enemies there.
- Arrows, bombs, and darts no longer get RPG stats. Stackable ammo stays vanilla.
- **Reskin (transmog) actually works now.** Before it would give you a random RPG item and keep your old one. Now it correctly moves your RPG data to the new skin.
- Reskin recipes show up reliably at the Builder's Workbench, including after server restarts.
- Armor reskins are more flexible: any Rare helmet can become any other Rare helmet, regardless of material. Weapons stay strict, a Rare dagger only sees other Rare daggers.
- **Item sync overhauled.** Items showing as "?" icons, wrong stats, or lost RPG data should sync properly now. Sync is also suppressed during world transitions (teleports, realm entry/exit) to prevent client crashes.

### Hexcode Compatibility

- Hex spell kills now correctly credit the caster for XP, realm progress, and loot. Old system guessed the nearest player, which caused wrong attributions. New system tracks exactly who cast what: direct spells, constructs, projectiles, all of it.
- Hexcode spellbooks no longer spam false warnings in the server log.

### UI & Quality of Life

- Added an **"I know what I'm doing"** button to the welcome guide popup. One click, no more guides ever.
- New guide popup after your first **Gateway Upgrade**.
- Stats page no longer shows "Passive Block Chance" (removed). The block stat is now labeled **"Perfect Block Chance"**.
- Attribute page (Earth element) now labels its block stat correctly.
- Voile wiki links in guide popups fixed.
- **Realm maps, gems, lights, and connection blocks** removed from creative inventory search. Only Stones stay in the TrailOfOrbis creative tab.
- **Ancient Gateway upgrade UI** opens no matter what you're holding. Weapons, food, tools, whatever. Only Fragment Keys and Realm Maps trigger their own UIs. Before you had to empty your hand.
- New **Combat Text Colors** guide on first combat. Explains the element-colored damage numbers and crits.
- New **Blocking** guide on first active block. Covers the 33%/66% reduction and Perfect Block chance.
- New **Crafting RPG Gear** guide on first crafted weapon/armor. Material-based level, tooltip previews.
- **Skill Sanctum** guide now tells you how to leave (`/skilltree` command or buttons in `/stats` and `/attr`).
- Text formatting fixes across guide descriptions, skill tree arm descriptions, and error messages.
- Tried to fully rework the Guides UI to make it cleaner, more readable, just before going to bed (probably a bad idea, sorry)

### Skill Tree & Attributes

- All skill tree nodes that gave "Passive Block Chance" now give **"Block Chance"**. Feeds directly into Perfect Block when you're blocking with a shield.
- "Blocking" gear modifier prefix now maps to the new block chance stat correctly.
- Earth element's block stat renamed, now feeds Perfect Block instead of the old passive system.

### Config

- New `material_distances` config in vanilla-conversion.yml. Defines where each material spawns in the world, which sets the crafted gear level.
- New `crafting_level_multiplier` to tune crafted gear power vs zone mobs (default 1.0 = matches zone).
- Block chance config keys renamed across config.yml, gear-modifiers.yml, and skill-tree.yml.

