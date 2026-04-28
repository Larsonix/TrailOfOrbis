package io.github.larsonix.trailoforbis.stones;

import com.hypixel.hytale.codec.codecs.EnumCodec;

import javax.annotation.Nonnull;

/**
 * Defines which item types a stone can be used on.
 *
 * <p>Some stones work only on gear, some only on maps,
 * and some work on both.
 *
 * @see StoneType
 */
public enum ItemTargetType {

    /**
     * Stone can only be used on gear items.
     */
    GEAR_ONLY("Gear"),

    /**
     * Stone can only be used on realm maps.
     */
    MAP_ONLY("Maps"),

    /**
     * Stone can be used on both gear and maps.
     */
    BOTH("Gear & Maps");

    private final String displayName;

    ItemTargetType(@Nonnull String displayName) {
        this.displayName = displayName;
    }

    /**
     * Gets the human-readable display name.
     *
     * @return The display name
     */
    @Nonnull
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Checks if this target type includes gear.
     *
     * @return true if gear is a valid target
     */
    public boolean includesGear() {
        return this == GEAR_ONLY || this == BOTH;
    }

    /**
     * Checks if this target type includes maps.
     *
     * @return true if maps are a valid target
     */
    public boolean includesMaps() {
        return this == MAP_ONLY || this == BOTH;
    }

    /**
     * Checks if the given item is a valid target for this type.
     *
     * @param item The item to check
     * @return true if the item can be targeted
     */
    public boolean isValidTarget(@Nonnull ModifiableItem item) {
        return switch (item.itemTargetType()) {
            case GEAR_ONLY -> includesGear();
            case MAP_ONLY -> includesMaps();
            case BOTH -> true;
        };
    }

    /**
     * Codec for serialization/deserialization.
     */
    public static final EnumCodec<ItemTargetType> CODEC = new EnumCodec<>(ItemTargetType.class);
}
