package io.github.larsonix.trailoforbis.skilltree.conditional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for a conditional effect on a skill node.
 *
 * <p>Conditional effects are bonuses that only activate when certain
 * conditions are met (on-kill, on-crit, when hit, etc.).
 *
 * <h2>Example YAML:</h2>
 * <pre>
 * conditional:
 *   trigger: ON_KILL
 *   duration: 4.0
 *   stacking: REFRESH
 *   max_stacks: 1
 *   cooldown: 0.0
 *   effects:
 *     - stat: ATTACK_SPEED_PERCENT
 *       value: 20.0
 *     - stat: MOVEMENT_SPEED_PERCENT
 *       value: 10.0
 * </pre>
 *
 * @see ConditionalTrigger
 * @see StackingBehavior
 */
public class ConditionalConfig {

    /**
     * The trigger that activates this effect.
     */
    private ConditionalTrigger trigger;

    /**
     * Duration of the effect in seconds.
     * Set to 0 or negative for persistent effects (threshold-based).
     */
    private double duration = 4.0;

    /**
     * How the effect behaves when triggered again while active.
     */
    private StackingBehavior stacking = StackingBehavior.REFRESH;

    /**
     * Maximum number of stacks (for STACK behavior).
     */
    private int maxStacks = 1;

    /**
     * Cooldown between activations in seconds.
     */
    private double cooldown = 0.0;

    /**
     * Health threshold for threshold-based triggers (e.g., LOW_LIFE = 0.35).
     */
    private double threshold = 0.35;

    /**
     * The stat effects when this conditional is active.
     */
    private List<ConditionalEffect> effects = new ArrayList<>();

    // Default constructor for YAML
    public ConditionalConfig() {
    }

    // ═══════════════════════════════════════════════════════════════════
    // GETTERS
    // ═══════════════════════════════════════════════════════════════════

    @Nonnull
    public ConditionalTrigger getTrigger() {
        return trigger != null ? trigger : ConditionalTrigger.ON_KILL;
    }

    public double getDuration() {
        return duration;
    }

    /**
     * Checks if this is a timed effect (has duration).
     */
    public boolean isTimedEffect() {
        return duration > 0;
    }

    /**
     * Checks if this is a persistent/threshold effect (no duration).
     */
    public boolean isPersistentEffect() {
        return duration <= 0;
    }

    @Nonnull
    public StackingBehavior getStacking() {
        return stacking != null ? stacking : StackingBehavior.REFRESH;
    }

    public int getMaxStacks() {
        return maxStacks > 0 ? maxStacks : 1;
    }

    public double getCooldown() {
        return cooldown;
    }

    /**
     * Checks if this effect has a cooldown.
     */
    public boolean hasCooldown() {
        return cooldown > 0;
    }

    public double getThreshold() {
        return threshold;
    }

    @Nonnull
    public List<ConditionalEffect> getEffects() {
        return effects != null ? effects : List.of();
    }

    // ═══════════════════════════════════════════════════════════════════
    // SETTERS (for YAML deserialization)
    // ═══════════════════════════════════════════════════════════════════

    public void setTrigger(ConditionalTrigger trigger) {
        this.trigger = trigger;
    }

    public void setDuration(double duration) {
        this.duration = duration;
    }

    public void setStacking(StackingBehavior stacking) {
        this.stacking = stacking;
    }

    public void setMaxStacks(int maxStacks) {
        this.maxStacks = maxStacks;
    }

    // Alias for YAML (max_stacks)
    public void setMax_stacks(int maxStacks) {
        this.maxStacks = maxStacks;
    }

    public void setCooldown(double cooldown) {
        this.cooldown = cooldown;
    }

    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }

    public void setEffects(List<ConditionalEffect> effects) {
        this.effects = effects;
    }

    // Single effect setter for simple YAML (effect: instead of effects:)
    public void setEffect(ConditionalEffect effect) {
        this.effects = new ArrayList<>();
        this.effects.add(effect);
    }

    @Override
    public String toString() {
        return String.format("ConditionalConfig{trigger=%s, duration=%.1f, stacking=%s, effects=%d}",
            trigger, duration, stacking, effects != null ? effects.size() : 0);
    }

    // ═══════════════════════════════════════════════════════════════════
    // INNER CLASSES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * A single stat effect within a conditional.
     */
    public static class ConditionalEffect {
        /**
         * The stat to modify when active.
         */
        private String stat;

        /**
         * The value to add when active.
         */
        private double value;

        /**
         * The modifier type (FLAT, PERCENT, MULTIPLIER).
         */
        private String modifierType = "PERCENT";

        public ConditionalEffect() {
        }

        @Nonnull
        public String getStat() {
            return stat != null ? stat : "";
        }

        public double getValue() {
            return value;
        }

        @Nonnull
        public String getModifierType() {
            return modifierType != null ? modifierType : "PERCENT";
        }

        public void setStat(String stat) {
            this.stat = stat;
        }

        public void setValue(double value) {
            this.value = value;
        }

        public void setModifierType(String modifierType) {
            this.modifierType = modifierType;
        }

        // Alias for YAML
        public void setModifier_type(String modifierType) {
            this.modifierType = modifierType;
        }

        @Override
        public String toString() {
            return String.format("ConditionalEffect{stat=%s, value=%.1f, type=%s}",
                stat, value, modifierType);
        }
    }
}
