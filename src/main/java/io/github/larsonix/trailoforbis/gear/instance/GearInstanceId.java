package io.github.larsonix.trailoforbis.gear.instance;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Unique identifier for a gear instance.
 *
 * <p>Each gear piece gets a unique instance ID that:
 * <ul>
 *   <li>Uniquely identifies this specific item across the server</li>
 *   <li>Converts to a unique Hytale item ID for per-player display</li>
 *   <li>Persists in item metadata for reload/reconnect handling</li>
 * </ul>
 *
 * <p>Format: {@code rpg_gear_{timestamp}_{counter}}
 * Example: {@code rpg_gear_1706123456789_42}
 *
 * <p>The combination of timestamp + counter ensures uniqueness:
 * <ul>
 *   <li>Different timestamps = different IDs (normal case)</li>
 *   <li>Same timestamp = counter disambiguates (high-frequency generation)</li>
 * </ul>
 *
 * @param timestamp Unix timestamp in milliseconds when generated
 * @param counter Atomic counter value at generation time
 */
public record GearInstanceId(long timestamp, long counter) {

    /** Prefix for all RPG gear item IDs */
    public static final String PREFIX = "rpg_gear_";

    /** Separator between timestamp and counter */
    private static final String SEPARATOR = "_";

    /**
     * Compact constructor with validation.
     */
    public GearInstanceId {
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
     * @return Item ID string (e.g., "rpg_gear_1706123456789_42")
     */
    @Nonnull
    public String toItemId() {
        return PREFIX + timestamp + SEPARATOR + counter;
    }

    /**
     * Converts this instance ID to a compact string for metadata storage.
     *
     * <p>Format: {@code timestamp_counter} (without prefix)
     *
     * @return Compact string (e.g., "1706123456789_42")
     */
    @Nonnull
    public String toCompactString() {
        return timestamp + SEPARATOR + counter;
    }

    /**
     * Checks if a string is an RPG gear item ID.
     *
     * @param itemId The item ID to check (may be null)
     * @return true if this is an RPG gear item ID
     */
    public static boolean isRpgGearId(@Nullable String itemId) {
        return itemId != null && itemId.startsWith(PREFIX);
    }

    /**
     * Parses a GearInstanceId from an item ID string.
     *
     * @param itemId The item ID (e.g., "rpg_gear_1706123456789_42")
     * @return The parsed GearInstanceId
     * @throws IllegalArgumentException if the format is invalid
     */
    @Nonnull
    public static GearInstanceId fromItemId(@Nonnull String itemId) {
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
     * Parses a GearInstanceId from a compact string (metadata format).
     *
     * @param compactString The compact string (e.g., "1706123456789_42")
     * @return The parsed GearInstanceId
     * @throws IllegalArgumentException if the format is invalid
     */
    @Nonnull
    public static GearInstanceId fromCompactString(@Nonnull String compactString) {
        Objects.requireNonNull(compactString, "compactString cannot be null");

        int separatorIndex = compactString.lastIndexOf(SEPARATOR);
        if (separatorIndex == -1) {
            throw new IllegalArgumentException(
                "Invalid compact string format, expected 'timestamp_counter': " + compactString
            );
        }

        try {
            long timestamp = Long.parseLong(compactString.substring(0, separatorIndex));
            long counter = Long.parseLong(compactString.substring(separatorIndex + 1));
            return new GearInstanceId(timestamp, counter);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "Invalid compact string format, cannot parse numbers: " + compactString, e
            );
        }
    }

    /**
     * Attempts to parse a GearInstanceId from an item ID, returning null on failure.
     *
     * @param itemId The item ID to parse (may be null)
     * @return The parsed GearInstanceId, or null if invalid
     */
    @Nullable
    public static GearInstanceId tryFromItemId(@Nullable String itemId) {
        if (itemId == null || !isRpgGearId(itemId)) {
            return null;
        }
        try {
            return fromItemId(itemId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Attempts to parse a GearInstanceId from a compact string, returning null on failure.
     *
     * @param compactString The compact string to parse (may be null)
     * @return The parsed GearInstanceId, or null if invalid
     */
    @Nullable
    public static GearInstanceId tryFromCompactString(@Nullable String compactString) {
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
        return "GearInstanceId[" + toItemId() + "]";
    }
}
