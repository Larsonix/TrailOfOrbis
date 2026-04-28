package io.github.larsonix.trailoforbis.mobs.speed;

import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.RemovalBehavior;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Custom EntityEffect subclass that allows programmatic creation of speed effects.
 *
 * <p>Hytale's EntityEffect has protected fields, so we extend it to gain access
 * and create effects without needing JSON assets.
 *
 * <p>This is specifically designed for mob speed scaling in the RPG system.
 */
public class RPGEntityEffect extends EntityEffect {

    /**
     * Creates a new RPG entity effect with a specific ID.
     *
     * @param id The unique identifier for this effect (e.g., "rpg_speed_1.2")
     */
    public RPGEntityEffect(@Nonnull String id) {
        super(id);
    }

    /**
     * Creates a speed effect with the specified multiplier.
     *
     * @param id              Unique effect ID
     * @param speedMultiplier Horizontal speed multiplier (1.0 = normal, 1.5 = 50% faster)
     * @return Configured RPGEntityEffect
     */
    public static RPGEntityEffect createSpeedEffect(@Nonnull String id, float speedMultiplier) {
        RPGEntityEffect effect = new RPGEntityEffect(id);

        // Set the application effects with our custom speed multiplier
        effect.applicationEffects = new RPGApplicationEffects(speedMultiplier);

        // Configure as infinite effect (permanent for mobs)
        effect.infinite = true;
        effect.duration = 0.0f;

        // Don't show in UI (this is internal mob scaling)
        effect.name = null;
        effect.statusEffectIcon = null;
        effect.debuff = false;

        // Standard behavior
        effect.overlapBehavior = OverlapBehavior.OVERWRITE;
        effect.removalBehavior = RemovalBehavior.COMPLETE;
        effect.invulnerable = false;

        return effect;
    }

    /**
     * Sets the application effects (allows access to protected field).
     *
     * @param effects The application effects to set
     */
    public void setApplicationEffects(@Nonnull RPGApplicationEffects effects) {
        this.applicationEffects = effects;
    }

    /**
     * Sets whether this effect is infinite (permanent).
     *
     * @param infinite true for permanent effects
     */
    public void setInfinite(boolean infinite) {
        this.infinite = infinite;
    }

    /**
     * Sets the effect duration in seconds.
     *
     * @param duration Duration in seconds (0 for infinite effects)
     */
    public void setDuration(float duration) {
        this.duration = duration;
    }

    /**
     * Sets the overlap behavior when the effect is applied multiple times.
     *
     * @param behavior OVERWRITE replaces, EXTEND adds time, IGNORE keeps existing
     */
    public void setOverlapBehavior(@Nonnull OverlapBehavior behavior) {
        this.overlapBehavior = behavior;
    }

    /**
     * Sets the display name of this effect (shown in status effect UI).
     *
     * @param name Localization key or display name, or null to hide
     */
    public void setName(@Nullable String name) {
        this.name = name;
    }

    /**
     * Sets whether this effect is a debuff.
     *
     * @param debuff true for debuffs (shown with debuff styling in UI)
     */
    public void setDebuff(boolean debuff) {
        this.debuff = debuff;
    }

    /**
     * Sets the status effect icon path.
     *
     * @param icon Icon path (e.g., "UI/StatusEffects/Burn.png"), or null for no icon
     */
    public void setStatusEffectIcon(@Nullable String icon) {
        this.statusEffectIcon = icon;
    }

    /**
     * Sets the death message localization key.
     *
     * @param key Localization key (e.g., "server.general.deathCause.burn"), or null
     */
    public void setDeathMessageKey(@Nullable String key) {
        this.deathMessageKey = key;
    }

    /**
     * Sets the removal behavior when the effect expires or is removed.
     *
     * @param behavior COMPLETE or DURATION
     */
    public void setRemovalBehavior(@Nonnull RemovalBehavior behavior) {
        this.removalBehavior = behavior;
    }
}
