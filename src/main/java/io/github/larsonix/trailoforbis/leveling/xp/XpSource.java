package io.github.larsonix.trailoforbis.leveling.xp;

import javax.annotation.Nonnull;

/**
 * Represents the source of an XP gain or loss event.
 *
 * <p>Used for:
 * <ul>
 *   <li>Analytics and logging</li>
 *   <li>Conditional XP multipliers (e.g., 2x quest XP)</li>
 *   <li>Event handling (listeners can check source type)</li>
 * </ul>
 */
public enum XpSource {

    /**
     * XP from killing a hostile mob.
     * Amount based on mob stats (level, pool, tier).
     */
    MOB_KILL("Mob Kill"),

    /**
     * XP from completing a quest.
     */
    QUEST_COMPLETE("Quest Complete"),

    /**
     * XP from quest progress milestones.
     */
    QUEST_PROGRESS("Quest Progress"),

    /**
     * XP granted via admin command.
     */
    ADMIN_COMMAND("Admin Command"),

    /**
     * XP from party member sharing.
     */
    PARTY_SHARE("Party Share"),

    /**
     * XP from crafting items.
     */
    CRAFTING("Crafting"),

    /**
     * XP from gathering resources.
     */
    GATHERING("Gathering"),

    /**
     * XP from discovering new areas.
     */
    EXPLORATION("Exploration"),

    /**
     * XP from killing a mob inside a Realm instance.
     * May include realm-specific XP bonuses from modifiers.
     */
    REALM_KILL("Realm Kill"),

    /**
     * XP bonus awarded upon completing a Realm.
     */
    REALM_COMPLETION("Realm Completion"),

    /**
     * XP lost on player death.
     */
    DEATH_PENALTY("Death Penalty"),

    /**
     * XP from other/unspecified sources.
     */
    OTHER("Other");

    private final String displayName;

    XpSource(@Nonnull String displayName) {
        this.displayName = displayName;
    }

    /** Gets the human-readable display name. */
    @Nonnull
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns true if this is an XP gain source (not a penalty/loss).
     *
     * @return true for gain sources, false for loss sources
     */
    public boolean isGainSource() {
        return this != DEATH_PENALTY;
    }

    /**
     * Returns true if this is an XP loss source.
     *
     * @return true for loss sources
     */
    public boolean isLossSource() {
        return this == DEATH_PENALTY;
    }
}
