package io.github.larsonix.trailoforbis.maps.core;

import com.hypixel.hytale.codec.codecs.EnumCodec;

import javax.annotation.Nonnull;

/**
 * Defines the shape variants for Realm arena layouts.
 *
 * <p>Shape affects:
 * <ul>
 *   <li>Arena boundary geometry</li>
 *   <li>Spawn point distribution</li>
 *   <li>Combat flow and kiting patterns</li>
 *   <li>Visual appearance</li>
 * </ul>
 *
 * <p>Shape is primarily cosmetic but affects gameplay flow.
 */
public enum RealmLayoutShape {

    /**
     * Circular arena with equal distance from center to all edges.
     * Best for kiting and circular movement patterns.
     */
    CIRCULAR("Circular"),

    /**
     * Rectangular arena with corners.
     * Allows corner trapping strategies.
     */
    RECTANGULAR("Rectangular"),

    /**
     * Irregular arena with varied edges.
     * More unpredictable combat flow.
     */
    IRREGULAR("Irregular");

    private final String displayName;

    RealmLayoutShape(@Nonnull String displayName) {
        this.displayName = displayName;
    }

    /**
     * Gets the human-readable display name.
     *
     * @return The display name (e.g., "Circular", "Rectangular")
     */
    @Nonnull
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Checks if this shape has corners.
     *
     * <p>Shapes with corners allow different tactical options.
     *
     * @return true if the shape has corners
     */
    public boolean hasCorners() {
        return this == RECTANGULAR || this == IRREGULAR;
    }

    /**
     * Gets a random shape with equal probability.
     *
     * @param random The random source
     * @return A random shape
     */
    @Nonnull
    public static RealmLayoutShape random(@Nonnull java.util.Random random) {
        RealmLayoutShape[] values = values();
        return values[random.nextInt(values.length)];
    }

    /**
     * Parse shape from string (case-insensitive).
     *
     * @param name Shape name
     * @return The corresponding RealmLayoutShape
     * @throws IllegalArgumentException if name is not recognized
     */
    @Nonnull
    public static RealmLayoutShape fromString(@Nonnull String name) {
        if (name == null) {
            throw new IllegalArgumentException("Shape name cannot be null");
        }
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown layout shape: " + name);
        }
    }

    /**
     * Codec for serialization/deserialization.
     */
    public static final EnumCodec<RealmLayoutShape> CODEC = new EnumCodec<>(RealmLayoutShape.class);
}
