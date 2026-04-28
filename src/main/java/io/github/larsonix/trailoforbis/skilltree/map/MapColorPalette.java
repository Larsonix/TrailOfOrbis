package io.github.larsonix.trailoforbis.skilltree.map;

/**
 * Color constants for skill tree map rendering.
 *
 * <p>All colors are in ARGB format: 0xAARRGGBB
 * where AA=alpha, RR=red, GG=green, BB=blue.
 */
public final class MapColorPalette {

    private MapColorPalette() {
        // Utility class
    }

    // ==================== Chunk Size ====================

    /** Pixels per map chunk (standard Hytale map chunk size) */
    public static final int CHUNK_SIZE = 512;

    // ==================== Layout ====================

    /** Pixels between node centers */
    public static final int NODE_SPACING = 64;

    /** Node circle radius in pixels */
    public static final int NODE_RADIUS = 20;

    /** Notable node radius (larger) */
    public static final int NOTABLE_RADIUS = 28;

    /** Keystone node radius (largest) */
    public static final int KEYSTONE_RADIUS = 36;

    /** Connection line thickness */
    public static final int CONNECTION_WIDTH = 4;

    // ==================== Base Colors ====================

    /** Background color - dark blue-black */
    public static final int BACKGROUND = 0xFF0a0a1a;

    /** Connection lines - dim blue-gray */
    public static final int CONNECTION = 0xFF333355;

    /** Grid lines (optional) - very dim */
    public static final int GRID = 0xFF151525;

    // ==================== Node States ====================

    /** Locked node - dark gray */
    public static final int NODE_LOCKED = 0xFF333333;

    /** Available to allocate - white */
    public static final int NODE_AVAILABLE = 0xFFffffff;

    /** Allocated normal node - gold */
    public static final int NODE_ALLOCATED = 0xFFffd700;

    /** Allocated notable - bright gold */
    public static final int NOTABLE_ALLOCATED = 0xFFffaa00;

    /** Allocated keystone - orange-red */
    public static final int KEYSTONE_ALLOCATED = 0xFFff4500;

    // ==================== Node Borders ====================

    /** Normal node border */
    public static final int BORDER_NORMAL = 0xFF888888;

    /** Notable node border */
    public static final int BORDER_NOTABLE = 0xFFccaa00;

    /** Keystone node border */
    public static final int BORDER_KEYSTONE = 0xFFff8800;

    // ==================== Path Colors ====================

    /** STR path - red tint */
    public static final int PATH_STR = 0xFFff6666;

    /** DEX path - green tint */
    public static final int PATH_DEX = 0xFF66ff66;

    /** INT path - blue tint */
    public static final int PATH_INT = 0xFF6666ff;

    /** VIT path - yellow tint */
    public static final int PATH_VIT = 0xFFffff66;

    // ==================== Utility Methods ====================

    /**
     * Creates an ARGB color from components.
     *
     * @param alpha Alpha channel (0-255)
     * @param red Red channel (0-255)
     * @param green Green channel (0-255)
     * @param blue Blue channel (0-255)
     * @return Packed ARGB color
     */
    public static int argb(int alpha, int red, int green, int blue) {
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    /**
     * Blends two colors (simple 50/50 blend).
     *
     * @param color1 First color
     * @param color2 Second color
     * @return Blended color
     */
    public static int blend(int color1, int color2) {
        int a1 = (color1 >> 24) & 0xFF;
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int a2 = (color2 >> 24) & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        return argb(
            (a1 + a2) / 2,
            (r1 + r2) / 2,
            (g1 + g2) / 2,
            (b1 + b2) / 2
        );
    }
}
