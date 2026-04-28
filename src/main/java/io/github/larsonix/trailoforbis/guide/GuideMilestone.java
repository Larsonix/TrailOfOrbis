package io.github.larsonix.trailoforbis.guide;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * All guide milestones. Each fires once per player, ever.
 *
 * <p>Priority determines which milestone wins when multiple trigger simultaneously:
 * CRITICAL > NORMAL > LOW. Lower-priority milestones are deferred (not marked complete)
 * and re-fire on their next natural trigger.
 */
public enum GuideMilestone {

    // ═══════════════════════════════════════════════════════════════════
    // PHASE 1: FIRST SESSION (Level 1-5)
    // ═══════════════════════════════════════════════════════════════════

    WELCOME(
        "welcome",
        Priority.LOW,
        "Welcome to Trail of Orbis",
        "**The world's corrupted.** Everything beyond the safe zone wants you dead, "
            + "and it only gets worse the further you go. You're one of the last ones standing, so no pressure."
            + "\n\n"
            + "Those portals around spawn ? Those are ||Ancient Gateways||. Walk up to one empty-handed and press ||F||. "
            + "They channel **Realm Maps** into combat arenas — timed, instanced, full of loot. But each gateway "
            + "has a tier that caps the map level. Right now they're all ||Copper|| (max level ||10||). "
            + "Push into the overworld, mine harder ores, upgrade your gateways. That's the loop."
            + "\n\n"
            + "*Craft gear. Kill stuff. Run Realms. Repeat forever.*",
        "Larsonix:TrailOfOrbis:getting-started"
    ),

    FIRST_GEAR(
        "first_gear",
        Priority.NORMAL,
        "Your First Gear Drop",
        "**So you picked up your first real piece of gear.** Welcome to the rabbit hole."
            + "\n\n"
            + "Three things matter on every item: ||Rarity|| is the color, it goes from grey (trash) "
            + "to orange (don't you dare drop that). ||Quality|| is a number from ||1|| to ||101||, higher means "
            + "the stats hit harder. And ||Modifiers|| are the stat bonuses listed underneath, each piece "
            + "rolls random ones. More rarity means more mods. Better quality means stronger mods."
            + "\n\n"
            + "*You'll be comparing tooltips for the rest of your life now.*",
        "Larsonix:TrailOfOrbis:rarities"
    ),

    FIRST_LEVEL_UP(
        "first_level_up",
        Priority.CRITICAL,
        "You Leveled Up!",
        "**You got an Attribute Point and a Skill Point.** Two different systems, both important."
            + "\n\n"
            + "Type ||/stats|| to open the Attribute page. You pick one of ||6 elements|| "
            + "(Fire, Water, Lightning, Earth, Wind, Void) and each one shapes your build differently. "
            + "Fire hits harder, Earth makes you tanky, Lightning makes you fast, you get the idea."
            + "\n\n"
            + "Type ||/skilltree|| to enter the **Skill Sanctum**. That's a whole 3D world with a massive "
            + "passive tree. Spend your Skill Point there."
            + "\n\n"
            + "*Don't worry about getting it wrong, you can respec.*",
        "Larsonix:TrailOfOrbis:stat-reference"
    ),

    FIRST_STONE(
        "first_stone",
        Priority.NORMAL,
        "You Found a Stone",
        "**That's a Stone.** It looks like a random drop but it's actually the entire crafting system in this mod."
            + "\n\n"
            + "Stones let you reroll your gear's **Modifiers**, upgrade **Rarity**, change **Quality**, add new stats, "
            + "even corrupt items for risky bonuses. There's like ||25|| different types and they all do something "
            + "specific. Right-click one to see what it works on."
            + "\n\n"
            + "*Don't throw any away, you will regret it.*",
        "Larsonix:TrailOfOrbis:getting-started"
    ),

    GEAR_REQUIREMENTS(
        "gear_requirements",
        Priority.CRITICAL,
        "Can't Equip That?",
        "**Can't equip that?** Yeah, gear has requirements."
            + "\n\n"
            + "Every piece of equipment has a ||level requirement|| and some also need specific ||attribute points|| "
            + "in certain elements. Check the tooltip, it lists everything you need. If it says \"Requires ||15 Fire||\" "
            + "and you've got 10, either level up and put more points in Fire or find gear that fits your current "
            + "build."
            + "\n\n"
            + "*Your build choices determine what you can and can't wear.*",
        "Larsonix:TrailOfOrbis:requirements"
    ),

    // ═══════════════════════════════════════════════════════════════════
    // PHASE 2: OVERWORLD PROGRESSION (Level 5-20)
    // ═══════════════════════════════════════════════════════════════════

    FIRST_GATEWAY(
        "first_gateway",
        Priority.CRITICAL,
        "Ancient Gateway",
        "**That's an Ancient Gateway.** Every portal has a tier that limits the max level "
            + "of Realm Maps it can channel."
            + "\n\n"
            + "This one's ||Copper|| — capped at level ||10||. Walk up empty-handed, press ||F||, and you'll see "
            + "the upgrade UI. Bring ||metal bars|| and ||essences|| from the overworld to upgrade : ||Iron|| gets you "
            + "to level 20, ||Gold|| to 30, all the way up to ||Adamantite|| which removes the cap entirely. "
            + "The ores get rarer and the zones get more dangerous the further out you go."
            + "\n\n"
            + "*Mine ores, upgrade gateways, unlock harder Realms. That's how you progress.*",
        "Larsonix:TrailOfOrbis:ancient-gateways"
    ),

    FIRST_DEATH(
        "first_death",
        Priority.CRITICAL,
        "You Died",
        "**You died.** *It happens. Probably won't be the last time either.*"
            + "\n\n"
            + "Good news: below ||Level 20||, dying is completely free. No penalty, no lost progress, nothing. "
            + "Die as much as you want, it's your learning phase."
            + "\n\n"
            + "After ||Level 20|| though, every death costs you ||50%|| of your XP progress toward the next level. "
            + "You can never actually lose a level, just the progress within it."
            + "\n\n"
            + "*So enjoy the free deaths while they last.*",
        "Larsonix:TrailOfOrbis:death-penalty"
    ),

    FIRST_AILMENT_BURN(
        "first_ailment",
        Priority.NORMAL,
        "Ailment: Burn",
        "**You're on fire.** That's a ||Burn|| ailment. It deals fire damage over time based on how hard "
            + "you got hit, lasts about ||4 seconds||. The bigger the hit that caused it, the more it hurts. "
            + "||Fire Resistance|| from your Earth and gear stats reduces it."
            + "\n\n"
            + "*You'll see a lot of this one.*",
        "Larsonix:TrailOfOrbis:burn"
    ),

    FIRST_AILMENT_FREEZE(
        "first_ailment",
        Priority.NORMAL,
        "Ailment: Freeze",
        "**Frozen.** That's a ||Freeze|| ailment. Your movement and actions are slowed, anywhere from "
            + "||5%|| to ||30%|| depending on how hard the hit was relative to your max HP. Lasts ||3 seconds||. "
            + "||Cold Resistance|| counters it."
            + "\n\n"
            + "*Getting frozen in a bad spot is how most deaths start.*",
        "Larsonix:TrailOfOrbis:freeze"
    ),

    FIRST_AILMENT_SHOCK(
        "first_ailment",
        Priority.NORMAL,
        "Ailment: Shock",
        "**Shocked.** For the next ||2 seconds||, you take more damage from everything. Up to ||50%|| more if the "
            + "hit was big enough. It's the shortest ailment but probably the deadliest because everything "
            + "else hits harder while it's up. ||Lightning Resistance|| is your friend here.",
        "Larsonix:TrailOfOrbis:shock"
    ),

    FIRST_AILMENT_POISON(
        "first_ailment",
        Priority.NORMAL,
        "Ailment: Poison",
        "**Poisoned.** Unlike the other ailments, ||Poison|| stacks. Up to ||10 times||. Each stack does its own "
            + "damage independently, ||30%|| of the hit that applied it over ||5 seconds|| per stack. At max stacks "
            + "it gets ugly fast. ||Poison Resistance|| reduces it."
            + "\n\n"
            + "*Or just stop getting hit for a few seconds.*",
        "Larsonix:TrailOfOrbis:poison"
    ),

    SKILL_SANCTUM(
        "skill_sanctum",
        Priority.CRITICAL,
        "The Skill Sanctum",
        "**Welcome to the Skill Sanctum.** This is your passive skill tree, except it's a 3D world "
            + "you walk around in. Pretty cool right?"
            + "\n\n"
            + "Press ||F|| on glowing nodes to allocate them. Press ||F|| again on an allocated node to remove it "
            + "(costs a refund point). **Click** on any node to see exactly what it does. Each arm of the tree "
            + "matches an element, start near the center and work outward."
            + "\n\n"
            + "Messed everything up? Type ||/too skilltree respec||. First ||3|| are free, after that it costs "
            + "refund points. Use **Orbs of Unlearning** to get more.",
        "Larsonix:TrailOfOrbis:allocation"
    ),

    MOB_SCALING(
        "mob_scaling",
        Priority.CRITICAL,
        "The World Gets Tougher",
        "**By now you've probably noticed mobs hitting harder than before.** That's not random."
            + "\n\n"
            + "Mobs in this world **scale with distance from spawn**. The further out you go, the stronger "
            + "they get, more HP, more damage, the works. But they also drop better loot and give more XP. "
            + "It's a constant trade. If something feels too tough, pull back closer to spawn and gear up "
            + "before pushing further."
            + "\n\n"
            + "*The world doesn't care about your feelings, only your stats.*",
        "Larsonix:TrailOfOrbis:scaling"
    ),

    DEATH_PENALTY_ACTIVE(
        "death_penalty_active",
        Priority.CRITICAL,
        "Death Penalty Active",
        "**This is the one you actually need to read.**"
            + "\n\n"
            + "From this point on, every time you die, you lose ||50%|| of your XP progress toward the next "
            + "level. Not your total XP, just the progress within your current level. You can **never** drop "
            + "a level, but losing half your grind to a stupid death hurts. Trust me."
            + "\n\n"
            + "*Play careful, keep your gear up to date, and maybe don't fight that Elite at half HP.*",
        "Larsonix:TrailOfOrbis:death-penalty"
    ),

    // ═══════════════════════════════════════════════════════════════════
    // PHASE 3: REALM ERA (Level 20-35)
    // ═══════════════════════════════════════════════════════════════════

    FIRST_REALM(
        "first_realm",
        Priority.CRITICAL,
        "Inside a Realm",
        "**You're inside a Realm.** Timer's ticking, mobs are spawned, let's go."
            + "\n\n"
            + "Kill every single mob before the timer runs out. You get ||1.5x XP|| and ||double|| the item drops "
            + "in here compared to the overworld, so it's absolutely worth it. Clear the arena and victory "
            + "rewards spawn in the center. But if the timer hits zero, you get kicked out and get **nothing**."
            + "\n\n"
            + "Dying in here still triggers the death penalty if you're above ||Level 20||."
            + "\n\n"
            + "*Don't treat it like a safe playground.*",
        "Larsonix:TrailOfOrbis:overworld-vs-realms"
    ),

    STUCK_IN_REALM(
        "stuck_in_realm",
        Priority.CRITICAL,
        "Hey, You Stuck?",
        "**Hey, you stuck?** Yeah... WorldGenV2 is quirky, and I write exceptionally good code, so the exit "
            + "portal being out of reach totally wasn't my fault. *Quite sad.*"
            + "\n\n"
            + "But don't worry, I'm a kind guy. Type ||/too realm exit|| and you're out."
            + "\n\n"
            + "*You're welcome.*",
        "Larsonix:TrailOfOrbis:map-crafting"
    ),

    MAP_MODIFIERS(
        "map_modifiers",
        Priority.NORMAL,
        "Map Modifiers",
        "**This map has modifiers on it,** so it's not a normal run."
            + "\n\n"
            + "**Prefixes** make the realm harder: more mob HP, faster attacks, less time on the clock, "
            + "that kind of thing. **Suffixes** make the rewards better: more drops, rarer items, bonus XP, "
            + "higher elite chance. The harder the map, the more rewarding it is. That's the whole point."
            + "\n\n"
            + "*If a map looks scary, it's probably worth running.*",
        "Larsonix:TrailOfOrbis:modifiers"
    ),

    FIRST_ELITE(
        "first_elite",
        Priority.NORMAL,
        "Elite Mobs",
        "**You just took down an Elite.** They've got ||1.5x|| the stats of a normal mob and drop ||1.5x|| the XP. "
            + "Not bad for a warmup."
            + "\n\n"
            + "**Bosses** are another level though: ||3x|| stats, ||5x|| XP. You'll recognize them by the bar above "
            + "their heads showing their classification. They don't go down easy but the XP is massive."
            + "\n\n"
            + "*And just between us... what do you think happens when a Boss rolls the Elite modifier?*",
        "Larsonix:TrailOfOrbis:elites-bosses"
    ),

    // ═══════════════════════════════════════════════════════════════════
    // PHASE 4: DEPTH DISCOVERY (Level 25-50)
    // ═══════════════════════════════════════════════════════════════════

    LARGER_REALMS(
        "larger_realms",
        Priority.CRITICAL,
        "Larger Realms",
        "**Your drop pool just expanded.** ||Large|| Realm Maps can now drop for you."
            + "\n\n"
            + "Large means ||40 mobs||, a ||15 minute|| timer, and a **guaranteed boss** in the arena. The loot "
            + "scales with size too, way more drops per run. At ||level 50||, **Massive** maps unlock: ||70 mobs||, "
            + "||20 minutes||, **two guaranteed bosses**."
            + "\n\n"
            + "*And loot that makes everything before it look like pocket change.*",
        "Larsonix:TrailOfOrbis:sizes"
    ),

    LOOT_FILTER(
        "loot_filter",
        Priority.LOW,
        "Loot Filter",
        "**Your inventory's getting full** and it's only going to get worse from here."
            + "\n\n"
            + "Type ||/lf|| to open the Loot Filter. You can set it to hide low rarity drops so they never "
            + "even show up on the ground. No more picking through 20 grey items to find the one blue. "
            + "You can set a quick filter with ||/lf quick rare|| to only see **Rare** and above, or build custom "
            + "rules if you want to get fancy."
            + "\n\n"
            + "*Change it anytime.*",
        "Larsonix:TrailOfOrbis:loot-filter"
    ),

    // ═══════════════════════════════════════════════════════════════════
    // MOD COMPATIBILITY (Conditional)
    // ═══════════════════════════════════════════════════════════════════

    HEXCODE_ITEM(
        "hexcode_item",
        Priority.NORMAL,
        "Hexcode Integration",
        "**That's a hex weapon.** It casts spells. If you're wondering why there's a magic system "
            + "alongside an RPG mod, it's because this server runs **Hexcode** too, and they're wired together."
            + "\n\n"
            + "Your ||Water|| attribute boosts max mana and spell power. ||Fire|| adds raw magic power. ||Lightning|| "
            + "makes you cast faster. So if you want to be a battlemage, invest in those three elements "
            + "and watch both your sword hits and your spells scale up together.",
        null
    ),

    LOOT4EVERYONE(
        "loot4everyone",
        Priority.LOW,
        "Loot4Everyone Active",
        "**Quick heads up:** every chest in this world has its own loot for **each player**. You're not racing "
            + "anyone to open containers, you're not stealing someone else's drops."
            + "\n\n"
            + "*Open everything you see, it's all yours.*",
        null
    );

    // ═══════════════════════════════════════════════════════════════════
    // FIELDS
    // ═══════════════════════════════════════════════════════════════════

    private final String id;
    private final Priority priority;
    private final String title;
    private final String content;
    private final String wikiTopic;

    GuideMilestone(
            @Nonnull String id,
            @Nonnull Priority priority,
            @Nonnull String title,
            @Nonnull String content,
            @Nullable String wikiTopic) {
        this.id = id;
        this.priority = priority;
        this.title = title;
        this.content = content;
        this.wikiTopic = wikiTopic;
    }

    @Nonnull public String getId() { return id; }
    @Nonnull public Priority getPriority() { return priority; }
    @Nonnull public String getTitle() { return title; }
    @Nonnull public String getContent() { return content; }
    @Nullable public String getWikiTopic() { return wikiTopic; }

    /**
     * Whether this milestone has a "Learn More" wiki link.
     */
    public boolean hasWikiLink() {
        return wikiTopic != null;
    }

    /**
     * Whether this milestone should suppress damage while showing.
     * Stuck-in-realm doesn't need it (realm is already cleared).
     */
    public boolean shouldSuppressDamage() {
        return this != STUCK_IN_REALM;
    }

    /**
     * Whether this milestone is allowed to show while the player is inside a realm.
     * Most milestones are deferred to avoid interrupting combat.
     */
    public boolean canShowInRealm() {
        return this == FIRST_REALM || this == STUCK_IN_REALM;
    }

    // ═══════════════════════════════════════════════════════════════════
    // PRIORITY
    // ═══════════════════════════════════════════════════════════════════

    public enum Priority {
        CRITICAL(0),
        NORMAL(1),
        LOW(2);

        private final int weight;

        Priority(int weight) {
            this.weight = weight;
        }

        public int getWeight() {
            return weight;
        }
    }
}
