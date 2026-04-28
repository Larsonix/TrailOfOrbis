package io.github.larsonix.trailoforbis.skilltree.conditional;

/**
 * Trigger types for conditional effects on skill nodes.
 */
public enum ConditionalTrigger {
    /**
     * Triggered when the player kills an enemy.
     * Typical duration: 4-8 seconds.
     */
    ON_KILL,

    /**
     * Triggered when the player lands a critical strike.
     * Typical duration: 3-6 seconds.
     */
    ON_CRIT,

    /**
     * Triggered when the player takes damage.
     * Typical duration: 2-5 seconds.
     */
    WHEN_HIT,

    /**
     * Active while player health is below threshold (default 35%).
     * Persistent effect (no duration).
     */
    LOW_LIFE,

    /**
     * Active while player health is at 100%.
     * Persistent effect (no duration).
     */
    FULL_LIFE,

    /**
     * Active while player mana is at 100%.
     * Persistent effect (no duration).
     */
    FULL_MANA,

    /**
     * Active while player mana is below threshold (default 35%).
     * Persistent effect (no duration).
     */
    LOW_MANA,

    /**
     * Triggered when the player uses a skill/ability.
     * Typical duration: 3-6 seconds.
     */
    ON_SKILL_USE,

    /**
     * Triggered when the player blocks an attack.
     * Typical duration: 2-4 seconds.
     */
    ON_BLOCK,

    /**
     * Triggered when the player dodges/evades an attack.
     * Typical duration: 2-4 seconds.
     */
    ON_EVADE,

    /**
     * Active while the player is moving.
     * Persistent effect.
     */
    WHILE_MOVING,

    /**
     * Active while the player is stationary.
     * Persistent effect.
     */
    WHILE_STATIONARY,

    /**
     * Active while the player has an active buff.
     * Persistent effect.
     */
    WHILE_BUFFED,

    /**
     * Triggered when the player applies a status effect (burn, freeze, etc.).
     * Typical duration: 4-6 seconds.
     */
    ON_INFLICT_STATUS;

    /**
     * Checks if this trigger produces a timed effect (vs persistent).
     */
    public boolean isTimedTrigger() {
        return switch (this) {
            case ON_KILL, ON_CRIT, WHEN_HIT, ON_SKILL_USE, ON_BLOCK, ON_EVADE, ON_INFLICT_STATUS -> true;
            case LOW_LIFE, FULL_LIFE, FULL_MANA, LOW_MANA, WHILE_MOVING, WHILE_STATIONARY, WHILE_BUFFED -> false;
        };
    }

    /**
     * Checks if this trigger is threshold-based (requires a threshold value).
     */
    public boolean isThresholdBased() {
        return this == LOW_LIFE || this == LOW_MANA;
    }

    /**
     * Gets the default duration for this trigger type (in seconds).
     */
    public double getDefaultDuration() {
        return switch (this) {
            case ON_KILL -> 4.0;
            case ON_CRIT -> 3.0;
            case WHEN_HIT -> 2.0;
            case ON_SKILL_USE -> 4.0;
            case ON_BLOCK -> 3.0;
            case ON_EVADE -> 3.0;
            case ON_INFLICT_STATUS -> 5.0;
            default -> 0.0; // Persistent effects have no duration
        };
    }
}
