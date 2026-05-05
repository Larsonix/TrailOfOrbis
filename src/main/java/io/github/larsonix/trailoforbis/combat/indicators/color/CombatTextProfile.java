package io.github.larsonix.trailoforbis.combat.indicators.color;

import com.hypixel.hytale.protocol.Color;

import javax.annotation.Nonnull;

/**
 * Defines the complete visual appearance of a combat text instance.
 *
 * <p>Profiles are built from YAML config at startup and are immutable.
 * Used to build on-demand {@code EntityUIComponent} packets that override
 * the vanilla CombatText template at its original index.
 *
 * @param id Unique identifier (e.g., "fire_normal", "lightning_crit", "dodged")
 * @param color RGB color for the text
 * @param fontSize Font size in pixels (vanilla default: 68.0)
 * @param duration How long the text is visible in seconds (vanilla default: 0.4)
 * @param hitAngleModifierStrength How much the hit angle affects position (0.0–10.0)
 * @param animations Animation keyframes (scale, position, opacity)
 */
public record CombatTextProfile(
    @Nonnull String id,
    @Nonnull Color color,
    float fontSize,
    float duration,
    float hitAngleModifierStrength,
    @Nonnull CombatTextAnimation[] animations
) {

    /**
     * Creates a profile.
     */
    @Nonnull
    public static CombatTextProfile of(
        @Nonnull String id,
        @Nonnull Color color,
        float fontSize,
        float duration,
        float hitAngleModifierStrength,
        @Nonnull CombatTextAnimation[] animations
    ) {
        return new CombatTextProfile(id, color, fontSize, duration, hitAngleModifierStrength, animations);
    }
}
