package io.github.larsonix.trailoforbis.skilltree;

import javax.annotation.Nonnull;

/**
 * Visual states for skill tree nodes.
 *
 * <p>State hierarchy (priority order):
 * <ol>
 *   <li>HOVERED_* - Mouse is over the node</li>
 *   <li>ALLOCATED_* - Node is allocated</li>
 *   <li>AVAILABLE - Node can be allocated</li>
 *   <li>LOCKED - Node cannot be allocated yet</li>
 * </ol>
 *
 * <p>Each state provides distinct visual feedback:
 * <ul>
 *   <li>Locked: Dark, low opacity - clearly unavailable</li>
 *   <li>Available: Bright white - ready to allocate</li>
 *   <li>Allocated: Region-colored - shows ownership</li>
 *   <li>Hovered: Highlighted version of base state</li>
 * </ul>
 */
public enum NodeVisualState {

    // ═══════════════════════════════════════════════════════════════════
    // LOCKED STATES - Node cannot be allocated
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Node is locked (prerequisites not met).
     * Dark gray, low visibility.
     */
    LOCKED("#2a2a2a(0.6)", "#3a3a3a(0.7)"),

    /**
     * Locked node is being hovered - show it's locked but give feedback.
     */
    LOCKED_HOVERED("#3a3a3a(0.75)", "#4a4a4a(0.8)"),

    // ═══════════════════════════════════════════════════════════════════
    // AVAILABLE STATE - Node can be allocated
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Node is available for allocation.
     * Bright white, clearly clickable.
     */
    AVAILABLE("#cccccc(0.9)", "#ffffff(0.95)"),

    /**
     * Available node is being hovered - highlight to show it's clickable.
     */
    AVAILABLE_HOVERED("#ffffff(0.95)", "#ffffff(1.0)"),

    // ═══════════════════════════════════════════════════════════════════
    // ALLOCATED STATES - Per-region colors (set dynamically)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Basic node is allocated. Color varies by region.
     * Uses region's allocated color.
     */
    ALLOCATED_BASIC(null, null),

    /**
     * Allocated basic node is being hovered.
     */
    ALLOCATED_BASIC_HOVERED(null, null),

    /**
     * Notable node is allocated. Uses brighter region color.
     */
    ALLOCATED_NOTABLE(null, null),

    /**
     * Allocated notable node is being hovered.
     */
    ALLOCATED_NOTABLE_HOVERED(null, null),

    /**
     * Keystone node is allocated. Uses distinct region color.
     */
    ALLOCATED_KEYSTONE(null, null),

    /**
     * Allocated keystone node is being hovered.
     */
    ALLOCATED_KEYSTONE_HOVERED(null, null);

    // ═══════════════════════════════════════════════════════════════════
    // FIELDS & CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════════

    private final String backgroundColor;
    private final String hoveredBackgroundColor;

    NodeVisualState(String backgroundColor, String hoveredBackgroundColor) {
        this.backgroundColor = backgroundColor;
        this.hoveredBackgroundColor = hoveredBackgroundColor;
    }

    // ═══════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the background color for this state.
     * For allocated states, use {@link #getColorForRegion} instead.
     */
    public String getBackgroundColor() {
        return backgroundColor;
    }

    /**
     * Checks if this is a hovered state.
     */
    public boolean isHovered() {
        return name().endsWith("_HOVERED");
    }

    /**
     * Checks if this is an allocated state.
     */
    public boolean isAllocated() {
        return name().startsWith("ALLOCATED_");
    }

    /**
     * Gets the non-hovered version of this state.
     */
    @Nonnull
    public NodeVisualState getBaseState() {
        return switch (this) {
            case LOCKED_HOVERED -> LOCKED;
            case AVAILABLE_HOVERED -> AVAILABLE;
            case ALLOCATED_BASIC_HOVERED -> ALLOCATED_BASIC;
            case ALLOCATED_NOTABLE_HOVERED -> ALLOCATED_NOTABLE;
            case ALLOCATED_KEYSTONE_HOVERED -> ALLOCATED_KEYSTONE;
            default -> this;
        };
    }

    /**
     * Gets the hovered version of this state.
     */
    @Nonnull
    public NodeVisualState getHoveredState() {
        return switch (this) {
            case LOCKED, LOCKED_HOVERED -> LOCKED_HOVERED;
            case AVAILABLE, AVAILABLE_HOVERED -> AVAILABLE_HOVERED;
            case ALLOCATED_BASIC, ALLOCATED_BASIC_HOVERED -> ALLOCATED_BASIC_HOVERED;
            case ALLOCATED_NOTABLE, ALLOCATED_NOTABLE_HOVERED -> ALLOCATED_NOTABLE_HOVERED;
            case ALLOCATED_KEYSTONE, ALLOCATED_KEYSTONE_HOVERED -> ALLOCATED_KEYSTONE_HOVERED;
        };
    }

    // ═══════════════════════════════════════════════════════════════════
    // REGION-SPECIFIC COLORS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the background color for an allocated state with region-specific theming.
     *
     * @param region The skill tree region for color theming
     * @param hovered Whether the node is currently hovered
     * @return The appropriate background color string
     */
    @Nonnull
    public String getColorForRegion(@Nonnull SkillTreeRegion region, boolean hovered) {
        // For non-allocated states, return the static color
        if (!isAllocated() && backgroundColor != null) {
            return hovered ? hoveredBackgroundColor : backgroundColor;
        }

        // For allocated states, use region-specific colors
        String baseColor = switch (this.getBaseState()) {
            case ALLOCATED_BASIC -> region.getAllocatedColor();
            case ALLOCATED_NOTABLE -> region.getNotableColor();
            case ALLOCATED_KEYSTONE -> region.getKeystoneColor();
            default -> backgroundColor;
        };

        if (baseColor == null) {
            return "#888888(0.8)"; // Fallback
        }

        // If hovered, brighten the color
        if (hovered) {
            return brightenColor(baseColor);
        }

        return baseColor;
    }

    /**
     * Brightens a color for hover effect.
     * Increases alpha and shifts toward white.
     */
    @Nonnull
    private static String brightenColor(@Nonnull String color) {
        // Format: #rrggbb(alpha) -> brighten by increasing alpha
        if (color.contains("(")) {
            // Replace alpha with higher value
            int parenIndex = color.indexOf('(');
            String hex = color.substring(0, parenIndex);
            // Brighten hex slightly by blending with white
            String brighterHex = blendWithWhite(hex, 0.15f);
            return brighterHex + "(0.98)";
        }
        return color;
    }

    /**
     * Blends a hex color toward white.
     */
    @Nonnull
    private static String blendWithWhite(@Nonnull String hex, float amount) {
        if (!hex.startsWith("#") || hex.length() < 7) {
            return hex;
        }

        try {
            int r = Integer.parseInt(hex.substring(1, 3), 16);
            int g = Integer.parseInt(hex.substring(3, 5), 16);
            int b = Integer.parseInt(hex.substring(5, 7), 16);

            // Blend toward white
            r = Math.min(255, (int)(r + (255 - r) * amount));
            g = Math.min(255, (int)(g + (255 - g) * amount));
            b = Math.min(255, (int)(b + (255 - b) * amount));

            return String.format("#%02x%02x%02x", r, g, b);
        } catch (NumberFormatException e) {
            return hex;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // STATE DETERMINATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Determines the visual state for a node based on its properties.
     *
     * @param isAllocated Whether the node is allocated
     * @param canAllocate Whether the node can be allocated (prerequisites met)
     * @param isNotable Whether this is a notable node
     * @param isKeystone Whether this is a keystone node
     * @param isHovered Whether the mouse is over this node
     * @return The appropriate visual state
     */
    @Nonnull
    public static NodeVisualState determine(
            boolean isAllocated,
            boolean canAllocate,
            boolean isNotable,
            boolean isKeystone,
            boolean isHovered
    ) {
        NodeVisualState baseState;

        if (isAllocated) {
            if (isKeystone) {
                baseState = ALLOCATED_KEYSTONE;
            } else if (isNotable) {
                baseState = ALLOCATED_NOTABLE;
            } else {
                baseState = ALLOCATED_BASIC;
            }
        } else if (canAllocate) {
            baseState = AVAILABLE;
        } else {
            baseState = LOCKED;
        }

        return isHovered ? baseState.getHoveredState() : baseState;
    }
}
