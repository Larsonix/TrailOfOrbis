package io.github.larsonix.trailoforbis.combat.indicators.color;

import javax.annotation.Nonnull;

/**
 * A single animation keyframe for combat text rendering.
 *
 * <p>Maps directly to Hytale's {@code CombatTextUIComponentAnimationEvent} subtypes:
 * <ul>
 *   <li>{@link AnimationType#SCALE} — Scale from {@code startScale} to {@code endScale}</li>
 *   <li>{@link AnimationType#POSITION} — Drift by {@code (positionOffsetX, positionOffsetY)}</li>
 *   <li>{@link AnimationType#OPACITY} — Fade from {@code startOpacity} to {@code endOpacity}</li>
 * </ul>
 *
 * <p>Time values ({@code startAt}, {@code endAt}) are fractions of the total duration (0.0–1.0).
 *
 * @param type The animation type
 * @param startAt Start time as fraction of duration (0.0–1.0)
 * @param endAt End time as fraction of duration (0.0–1.0)
 * @param startScale Starting scale factor (for SCALE type)
 * @param endScale Ending scale factor (for SCALE type)
 * @param positionOffsetX Horizontal drift in pixels (for POSITION type)
 * @param positionOffsetY Vertical drift in pixels (for POSITION type, negative = upward)
 * @param startOpacity Starting opacity 0.0–1.0 (for OPACITY type)
 * @param endOpacity Ending opacity 0.0–1.0 (for OPACITY type)
 */
public record CombatTextAnimation(
    @Nonnull AnimationType type,
    float startAt,
    float endAt,
    float startScale,
    float endScale,
    float positionOffsetX,
    float positionOffsetY,
    float startOpacity,
    float endOpacity
) {

    /**
     * Types of combat text animation keyframes.
     */
    public enum AnimationType {
        SCALE,
        POSITION,
        OPACITY
    }

    /**
     * Creates a scale animation keyframe.
     */
    @Nonnull
    public static CombatTextAnimation scale(float startAt, float endAt, float startScale, float endScale) {
        return new CombatTextAnimation(AnimationType.SCALE, startAt, endAt, startScale, endScale, 0f, 0f, 0f, 0f);
    }

    /**
     * Creates a position drift animation keyframe.
     */
    @Nonnull
    public static CombatTextAnimation position(float startAt, float endAt, float offsetX, float offsetY) {
        return new CombatTextAnimation(AnimationType.POSITION, startAt, endAt, 0f, 0f, offsetX, offsetY, 0f, 0f);
    }

    /**
     * Creates an opacity fade animation keyframe.
     */
    @Nonnull
    public static CombatTextAnimation opacity(float startAt, float endAt, float startOpacity, float endOpacity) {
        return new CombatTextAnimation(AnimationType.OPACITY, startAt, endAt, 0f, 0f, 0f, 0f, startOpacity, endOpacity);
    }
}
