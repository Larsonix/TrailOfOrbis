package io.github.larsonix.trailoforbis.skilltree.conditional;

/**
 * Defines how a conditional effect behaves when triggered again while already active.
 */
public enum StackingBehavior {
    /**
     * Refresh the duration to full. Effect value stays the same.
     * Example: "On kill, gain 20% attack speed for 4s" - refreshes to 4s on each kill.
     */
    REFRESH,

    /**
     * Add a stack (up to max). Each stack adds the effect value.
     * Example: "On kill, gain a stack of Frenzy (+5% damage per stack, max 5)"
     */
    STACK,

    /**
     * Cannot be triggered again while active.
     * Example: "On kill, gain 50% damage for 4s (cannot trigger again while active)"
     */
    NO_REFRESH,

    /**
     * Consume the effect when hit/used, ending it early.
     * Example: "On crit, your next attack deals 50% more damage"
     */
    CONSUME_ON_HIT,

    /**
     * Consume the effect when using a skill.
     * Example: "On kill, your next skill costs no mana"
     */
    CONSUME_ON_SKILL,

    /**
     * Each trigger extends the duration (adds time instead of refreshing).
     * Example: "On kill, gain +10% move speed for 2s (stacks duration)"
     */
    EXTEND_DURATION
}
