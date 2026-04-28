package io.github.larsonix.trailoforbis.ui.skilltree;

/**
 * Holds position configuration for a single skill tree node.
 * Coordinates are relative to canvas center.
 *
 * <p>For 3D sanctum rendering:
 * <ul>
 *   <li>relativeX → World X (horizontal)</li>
 *   <li>relativeY → World Z (horizontal depth)</li>
 *   <li>relativeZ → World Y offset (vertical height)</li>
 * </ul>
 */
public record NodePositionConfig(
    String nodeId,
    int relativeX,
    int relativeY,
    int relativeZ
) {
    /**
     * Constructor for 2D positions (z defaults to 0).
     */
    public NodePositionConfig(String nodeId, int relativeX, int relativeY) {
        this(nodeId, relativeX, relativeY, 0);
    }

    /**
     * Gets the absolute X position on the canvas.
     */
    public int getAbsoluteX(int centerX) {
        return centerX + relativeX;
    }

    /**
     * Gets the absolute Y position on the canvas.
     */
    public int getAbsoluteY(int centerY) {
        return centerY + relativeY;
    }

    /**
     * Gets the relative Z (height) for 3D rendering.
     * This is used as a world Y offset in the sanctum.
     */
    public int getRelativeZ() {
        return relativeZ;
    }
}
