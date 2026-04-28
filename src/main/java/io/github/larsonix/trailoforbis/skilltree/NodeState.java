package io.github.larsonix.trailoforbis.skilltree;

/**
 * Visual state of a skill node orb in the Skill Sanctum.
 *
 * <p>Determines both the appearance (light intensity, texture variant) and
 * interactability of a node:
 * <ul>
 *   <li>{@link #LOCKED} - Dim, not yet reachable, not interactable</li>
 *   <li>{@link #AVAILABLE} - Pulsing, can be allocated with F-key</li>
 *   <li>{@link #ALLOCATED} - Bright steady glow, already spent</li>
 * </ul>
 *
 * <p>Extracted to {@code skilltree} package to break the circular dependency
 * between {@code sanctum} and {@code skilltree} — both packages reference
 * this enum, but it's a skill-tree domain concept.
 */
public enum NodeState {
    /**
     * Node is locked - not yet reachable on the skill tree.
     * Dim appearance, not interactable.
     */
    LOCKED(0.2f),

    /**
     * Node is available - can be allocated if player has points.
     * Pulsing appearance, interactable with F-key.
     */
    AVAILABLE(0.6f),

    /**
     * Node has been allocated - player has spent points here.
     * Bright steady glow, no longer interactable.
     */
    ALLOCATED(1.0f);

    private final float lightIntensity;

    NodeState(float lightIntensity) {
        this.lightIntensity = lightIntensity;
    }

    /**
     * Gets the light intensity multiplier for this state.
     *
     * @return Light intensity (0.0 to 1.0)
     */
    public float getLightIntensity() {
        return lightIntensity;
    }
}
