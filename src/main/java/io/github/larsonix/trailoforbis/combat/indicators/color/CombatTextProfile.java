package io.github.larsonix.trailoforbis.combat.indicators.color;

import com.hypixel.hytale.protocol.Color;

import javax.annotation.Nonnull;

/**
 * Defines the complete visual appearance of a combat text instance.
 *
 * <p>Each profile corresponds to one registered {@code EntityUIComponent} template
 * at a unique client-side index. Profiles are built from YAML config at startup
 * and are immutable.
 *
 * <p>Examples: {@code "fire_normal"} (orange, standard size), {@code "lightning_crit"}
 * (bright yellow, larger), {@code "dodged"} (gray, smaller, slow fade).
 *
 * @param id Unique identifier (e.g., "fire_normal", "lightning_crit", "dodged")
 * @param color RGB color for the text
 * @param fontSize Font size in pixels (vanilla default: 68.0)
 * @param duration How long the text is visible in seconds (vanilla default: 0.4)
 * @param hitAngleModifierStrength How much the hit angle affects position (0.0–10.0)
 * @param animations Animation keyframes (scale, position, opacity)
 * @param templateIndex The client-side EntityUI template index (assigned by registry)
 */
public record CombatTextProfile(
    @Nonnull String id,
    @Nonnull Color color,
    float fontSize,
    float duration,
    float hitAngleModifierStrength,
    @Nonnull CombatTextAnimation[] animations,
    int templateIndex
) {

    /** Sentinel value indicating the template index has not been assigned yet. */
    public static final int UNASSIGNED_INDEX = -1;

    /**
     * Creates a profile with an unassigned template index.
     * The index is set later by {@code CombatTextTemplateRegistry} during registration.
     */
    @Nonnull
    public static CombatTextProfile unregistered(
        @Nonnull String id,
        @Nonnull Color color,
        float fontSize,
        float duration,
        float hitAngleModifierStrength,
        @Nonnull CombatTextAnimation[] animations
    ) {
        return new CombatTextProfile(id, color, fontSize, duration, hitAngleModifierStrength, animations, UNASSIGNED_INDEX);
    }

    /**
     * Creates a copy with the given template index.
     */
    @Nonnull
    public CombatTextProfile withTemplateIndex(int templateIndex) {
        return new CombatTextProfile(id, color, fontSize, duration, hitAngleModifierStrength, animations, templateIndex);
    }

    public boolean isRegistered() {
        return templateIndex != UNASSIGNED_INDEX;
    }
}
