package io.github.larsonix.trailoforbis.gear.item;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Unique identifier for custom RPG items (realm maps).
 *
 * <p>Similar to {@link io.github.larsonix.trailoforbis.gear.instance.GearInstanceId}
 * but with type-specific prefixes for custom item categories:
 * <ul>
 *   <li>Maps: {@code rpg_map_timestamp_counter}</li>
 * </ul>
 *
 * <p>Format: {@code rpg_{type}_{timestamp}_{counter}}
 * Example: {@code rpg_map_1706123456789_42}
 *
 * <p>The combination of timestamp + counter ensures uniqueness:
 * <ul>
 *   <li>Different timestamps = different IDs (normal case)</li>
 *   <li>Same timestamp = counter disambiguates (high-frequency generation)</li>
 * </ul>
 *
 * @param type The item type
 * @param timestamp Unix timestamp in milliseconds when generated
 * @param counter Atomic counter value at generation time
 */
public record CustomItemInstanceId(
    @Nonnull ItemType type,
    long timestamp,
    long counter
) {

    /**
     * Supported custom item types.
     */
    public enum ItemType {
        /** Realm map items */
        MAP("map"),
        /** Skill gem items */
        GEM("gem");

        private final String prefix;

        ItemType(String prefix) {
            this.prefix = prefix;
        }

        /**
         * Gets the prefix used in item IDs.
         *
         * @return The prefix (e.g., "map", "stone")
         */
        @Nonnull
        public String getPrefix() {
            return prefix;
        }

        /**
         * Attempts to parse an ItemType from an item ID.
         *
         * @param itemId The full item ID (e.g., "rpg_map_123_0")
         * @return The ItemType, or null if not a recognized format
         */
        @Nullable
        public static ItemType fromItemId(@Nullable String itemId) {
            if (itemId == null || !itemId.startsWith(PREFIX)) {
                return null;
            }
            String suffix = itemId.substring(PREFIX.length());
            for (ItemType type : values()) {
                if (suffix.startsWith(type.prefix + SEPARATOR)) {
                    return type;
                }
            }
            return null;
        }
    }

    /** Global prefix for all RPG custom item IDs */
    public static final String PREFIX = "rpg_";

    /** Separator between components */
    private static final String SEPARATOR = "_";

    /**
     * Compact constructor with validation.
     */
    public CustomItemInstanceId {
        Objects.requireNonNull(type, "type cannot be null");
        if (timestamp < 0) {
            throw new IllegalArgumentException("Timestamp cannot be negative: " + timestamp);
        }
        if (counter < 0) {
            throw new IllegalArgumentException("Counter cannot be negative: " + counter);
        }
    }

    /**
     * Converts this instance ID to a Hytale item ID string.
     *
     * <p>This ID is used as the key in {@code UpdateItems} packets,
     * allowing per-instance item definitions.
     *
     * @return Item ID string (e.g., "rpg_map_1706123456789_42")
     */
    @Nonnull
    public String toItemId() {
        return PREFIX + type.prefix + SEPARATOR + timestamp + SEPARATOR + counter;
    }

    /**
     * Converts this instance ID to a compact string for metadata storage.
     *
     * <p>Format: {@code type_timestamp_counter} (without global prefix)
     *
     * @return Compact string (e.g., "map_1706123456789_42")
     */
    @Nonnull
    public String toCompactString() {
        return type.prefix + SEPARATOR + timestamp + SEPARATOR + counter;
    }

    /**
     * Checks if a string is an RPG custom item ID.
     *
     * @param itemId The item ID to check (may be null)
     * @return true if this is an RPG custom item ID (but not gear)
     */
    public static boolean isCustomItemId(@Nullable String itemId) {
        if (itemId == null || !itemId.startsWith(PREFIX)) {
            return false;
        }
        // Check if it matches any custom item type (but not gear)
        String suffix = itemId.substring(PREFIX.length());
        for (ItemType type : ItemType.values()) {
            if (suffix.startsWith(type.prefix + SEPARATOR)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if a string is an RPG map item ID.
     *
     * @param itemId The item ID to check (may be null)
     * @return true if this is an RPG map item ID
     */
    public static boolean isMapId(@Nullable String itemId) {
        return itemId != null && itemId.startsWith(PREFIX + ItemType.MAP.prefix + SEPARATOR);
    }

    /**
     * Parses a CustomItemInstanceId from an item ID string.
     *
     * @param itemId The item ID (e.g., "rpg_map_1706123456789_42")
     * @return The parsed CustomItemInstanceId
     * @throws IllegalArgumentException if the format is invalid
     */
    @Nonnull
    public static CustomItemInstanceId fromItemId(@Nonnull String itemId) {
        Objects.requireNonNull(itemId, "itemId cannot be null");

        if (!itemId.startsWith(PREFIX)) {
            throw new IllegalArgumentException(
                "Invalid item ID format, expected prefix '" + PREFIX + "': " + itemId
            );
        }

        String suffix = itemId.substring(PREFIX.length());
        return fromCompactString(suffix);
    }

    /**
     * Parses a CustomItemInstanceId from a compact string (metadata format).
     *
     * @param compactString The compact string (e.g., "map_1706123456789_42")
     * @return The parsed CustomItemInstanceId
     * @throws IllegalArgumentException if the format is invalid
     */
    @Nonnull
    public static CustomItemInstanceId fromCompactString(@Nonnull String compactString) {
        Objects.requireNonNull(compactString, "compactString cannot be null");

        // Find the type prefix
        ItemType foundType = null;
        String remaining = null;

        for (ItemType type : ItemType.values()) {
            String typePrefix = type.prefix + SEPARATOR;
            if (compactString.startsWith(typePrefix)) {
                foundType = type;
                remaining = compactString.substring(typePrefix.length());
                break;
            }
        }

        if (foundType == null) {
            throw new IllegalArgumentException(
                "Invalid compact string format, unknown type: " + compactString
            );
        }

        // Parse timestamp_counter
        int separatorIndex = remaining.lastIndexOf(SEPARATOR);
        if (separatorIndex == -1) {
            throw new IllegalArgumentException(
                "Invalid compact string format, expected 'type_timestamp_counter': " + compactString
            );
        }

        try {
            long timestamp = Long.parseLong(remaining.substring(0, separatorIndex));
            long counter = Long.parseLong(remaining.substring(separatorIndex + 1));
            return new CustomItemInstanceId(foundType, timestamp, counter);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "Invalid compact string format, cannot parse numbers: " + compactString, e
            );
        }
    }

    /**
     * Attempts to parse a CustomItemInstanceId from an item ID, returning null on failure.
     *
     * @param itemId The item ID to parse (may be null)
     * @return The parsed CustomItemInstanceId, or null if invalid
     */
    @Nullable
    public static CustomItemInstanceId tryFromItemId(@Nullable String itemId) {
        if (itemId == null || !isCustomItemId(itemId)) {
            return null;
        }
        try {
            return fromItemId(itemId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Attempts to parse a CustomItemInstanceId from a compact string, returning null on failure.
     *
     * @param compactString The compact string to parse (may be null)
     * @return The parsed CustomItemInstanceId, or null if invalid
     */
    @Nullable
    public static CustomItemInstanceId tryFromCompactString(@Nullable String compactString) {
        if (compactString == null || compactString.isEmpty()) {
            return null;
        }
        try {
            return fromCompactString(compactString);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "CustomItemInstanceId[" + toItemId() + "]";
    }

    // ═══════════════════════════════════════════════════════════════════
    // GENERATOR
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Thread-safe generator for unique custom item instance IDs.
     */
    public static final class Generator {

        /** Atomic counter for same-millisecond disambiguation */
        private static final AtomicLong COUNTER = new AtomicLong(0);

        /** Last timestamp used for generation */
        private static volatile long lastTimestamp = 0;

        /** Lock object for synchronized generation */
        private static final Object LOCK = new Object();

        /**
         * Generates a new unique CustomItemInstanceId.
         *
         * <p>Thread-safe: can be called concurrently from any thread.
         *
         * @param type The item type
         * @return A new unique CustomItemInstanceId
         */
        @Nonnull
        public static CustomItemInstanceId generate(@Nonnull ItemType type) {
            Objects.requireNonNull(type, "type cannot be null");

            synchronized (LOCK) {
                long timestamp = System.currentTimeMillis();
                long counter;

                if (timestamp == lastTimestamp) {
                    // Same millisecond - increment counter
                    counter = COUNTER.incrementAndGet();
                } else {
                    // New millisecond - reset counter
                    lastTimestamp = timestamp;
                    COUNTER.set(0);
                    counter = 0;
                }

                return new CustomItemInstanceId(type, timestamp, counter);
            }
        }

        /**
         * Generates a new map instance ID.
         *
         * @return A new unique map instance ID
         */
        @Nonnull
        public static CustomItemInstanceId generateMap() {
            return generate(ItemType.MAP);
        }

        /**
         * Generates a new gem instance ID.
         *
         * @return A new unique gem instance ID
         */
        @Nonnull
        public static CustomItemInstanceId generateGem() {
            return generate(ItemType.GEM);
        }

        // Prevent instantiation
        private Generator() {
            throw new UnsupportedOperationException("Utility class");
        }
    }
}
