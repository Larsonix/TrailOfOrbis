package io.github.larsonix.trailoforbis.stones;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Optional;

/**
 * Utility class for reading and writing stone data to ItemStacks.
 *
 * <p>Stone data is stored in ItemStack metadata using string keys prefixed with "RPG:Stone:".
 * Stones are native Hytale items defined in {@code Server/Item/Items/RPG/Stones/} —
 * they do not require dynamic registration or client sync.
 *
 * <h2>Metadata Structure</h2>
 * <pre>
 * RPG:Stone:IsStone  - Boolean marker (true)
 * RPG:Stone:Type     - StoneType enum name (e.g., "GAIAS_CALIBRATION")
 * RPG:Stone:Data     - Full StoneItemData as BSON
 * </pre>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Create a stone item (uses native Hytale item ID)
 * ItemStack gaiasCalibration = StoneUtils.createStoneItem(StoneType.GAIAS_CALIBRATION);
 *
 * // Check if an item is a stone
 * if (StoneUtils.isStone(itemStack)) {
 *     StoneType type = StoneUtils.readStoneType(itemStack).get();
 * }
 * }</pre>
 *
 * @see StoneType
 * @see StoneItemData
 */
public final class StoneUtils {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // ═══════════════════════════════════════════════════════════════════
    // METADATA KEYS
    // ═══════════════════════════════════════════════════════════════════

    /** Namespace prefix for all stone metadata keys */
    public static final String KEY_PREFIX = "RPG:Stone:";

    /** Metadata key for stone type. */
    public static final String KEY_STONE_TYPE = KEY_PREFIX + "Type";

    /** Metadata key marking an item as a stone. */
    public static final String KEY_IS_STONE = KEY_PREFIX + "IsStone";

    /** Main stone data key - stores the full StoneItemData as BSON */
    public static final String KEY_STONE_DATA = KEY_PREFIX + "Data";

    /** Native item ID prefix for stone items */
    private static final String NATIVE_STONE_PREFIX = "RPG_Stone_";

    // ═══════════════════════════════════════════════════════════════════
    // PRIVATE CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════════

    private StoneUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // ═══════════════════════════════════════════════════════════════════
    // CREATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a stone ItemStack using the native Hytale item ID.
     *
     * <p>The returned item uses the native item definition from the asset pack,
     * which provides the display name, description, quality color, model, texture,
     * and right-click interaction (stone picker UI) — no registration or sync needed.
     *
     * @param stoneType The stone type to create
     * @return A new ItemStack with stone metadata
     */
    @Nonnull
    public static ItemStack createStoneItem(@Nonnull StoneType stoneType) {
        Objects.requireNonNull(stoneType, "stoneType cannot be null");

        String nativeId = stoneType.getNativeItemId();
        ItemStack item = new ItemStack(nativeId, 1);
        return writeStoneType(item, stoneType);
    }

    /**
     * Creates a stone ItemStack with a specific stack count.
     *
     * @param stoneType The stone type
     * @param count Stack count (1-100)
     * @return A new ItemStack configured as a stone
     * @throws IllegalArgumentException if count is not in valid range
     */
    @Nonnull
    public static ItemStack createStoneItem(@Nonnull StoneType stoneType, int count) {
        Objects.requireNonNull(stoneType, "stoneType cannot be null");

        if (count < 1 || count > 100) {
            throw new IllegalArgumentException("Count must be between 1 and 100, got: " + count);
        }

        String nativeId = stoneType.getNativeItemId();
        ItemStack item = new ItemStack(nativeId, count);
        return writeStoneType(item, stoneType);
    }

    // ═══════════════════════════════════════════════════════════════════
    // DETECTION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Checks if an ItemStack is a stone.
     *
     * <p>Uses metadata-based detection first (fast), then falls back to
     * native item ID prefix check.
     *
     * @param itemStack The item to check
     * @return true if the item is a stone
     */
    public static boolean isStone(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return false;
        }

        // Primary: metadata check
        Boolean isStone = itemStack.getFromMetadataOrNull(KEY_IS_STONE, Codec.BOOLEAN);
        if (Boolean.TRUE.equals(isStone)) {
            return true;
        }

        // Fallback: native item ID check
        return isNativeStoneId(itemStack.getItemId());
    }

    /**
     * Checks if an item ID is a native stone item ID.
     *
     * @param itemId The item ID to check (may be null)
     * @return true if the ID starts with "RPG_Stone_"
     */
    public static boolean isNativeStoneId(@Nullable String itemId) {
        return itemId != null && itemId.startsWith(NATIVE_STONE_PREFIX);
    }

    // ═══════════════════════════════════════════════════════════════════
    // READ OPERATIONS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Reads the stone type from an ItemStack.
     *
     * @param itemStack The item to read from
     * @return Optional containing the stone type, or empty if not a stone
     */
    @Nonnull
    public static Optional<StoneType> readStoneType(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return Optional.empty();
        }

        // Try metadata first
        String typeName = itemStack.getFromMetadataOrNull(KEY_STONE_TYPE, Codec.STRING);
        if (typeName != null && !typeName.isEmpty()) {
            try {
                return Optional.of(StoneType.valueOf(typeName));
            } catch (IllegalArgumentException e) {
                // Unknown stone type - fall through to item ID detection
            }
        }

        // Fallback: derive type from native item ID
        String itemId = itemStack.getItemId();
        if (isNativeStoneId(itemId)) {
            for (StoneType type : StoneType.values()) {
                if (type.getNativeItemId().equals(itemId)) {
                    return Optional.of(type);
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Reads the stone type from an ItemStack, throwing if invalid.
     *
     * @param itemStack The item to read from
     * @return The stone type
     * @throws IllegalArgumentException if the item is not a valid stone
     */
    @Nonnull
    public static StoneType readStoneTypeOrThrow(@Nonnull ItemStack itemStack) {
        return readStoneType(itemStack)
                .orElseThrow(() -> new IllegalArgumentException("Item is not a valid stone"));
    }

    /**
     * Reads full stone data from an ItemStack.
     *
     * @param itemStack The item to read from (may be null)
     * @return The stone data, or empty if not present or corrupted
     */
    @Nonnull
    public static Optional<StoneItemData> readStoneData(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return Optional.empty();
        }

        try {
            // Try to read full StoneItemData from BSON first
            StoneItemData data = itemStack.getFromMetadataOrNull(KEY_STONE_DATA, StoneItemData.CODEC);
            if (data != null) {
                return Optional.of(data);
            }

            // Fallback: build from stone type
            Optional<StoneType> typeOpt = readStoneType(itemStack);
            return typeOpt.map(StoneItemData::of);

        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to read stone data from ItemStack");
            return Optional.empty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // WRITE OPERATIONS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Writes stone type metadata to an ItemStack.
     *
     * <p>Marks the item as a stone and stores the stone type.
     * Returns a new ItemStack with the metadata applied.
     *
     * @param itemStack The base item to write to
     * @param stoneType The stone type to write
     * @return A new ItemStack with stone data
     */
    @Nonnull
    public static ItemStack writeStoneType(
            @Nonnull ItemStack itemStack,
            @Nonnull StoneType stoneType) {
        Objects.requireNonNull(itemStack, "itemStack cannot be null");
        Objects.requireNonNull(stoneType, "stoneType cannot be null");

        return itemStack
                .withMetadata(KEY_IS_STONE, Codec.BOOLEAN, true)
                .withMetadata(KEY_STONE_TYPE, Codec.STRING, stoneType.name());
    }

    /**
     * Writes full stone data to an ItemStack.
     *
     * @param itemStack The item to write to
     * @param stoneData The stone data to write
     * @return New ItemStack with stone data in metadata
     */
    @Nonnull
    public static ItemStack writeStoneData(@Nonnull ItemStack itemStack, @Nonnull StoneItemData stoneData) {
        Objects.requireNonNull(itemStack, "ItemStack cannot be null");
        Objects.requireNonNull(stoneData, "StoneItemData cannot be null");

        return itemStack
            .withMetadata(KEY_IS_STONE, Codec.BOOLEAN, true)
            .withMetadata(KEY_STONE_TYPE, Codec.STRING, stoneData.stoneType().name())
            .withMetadata(KEY_STONE_DATA, StoneItemData.CODEC, stoneData);
    }

    // ═══════════════════════════════════════════════════════════════════
    // UTILITY METHODS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Removes all stone data from an ItemStack.
     *
     * @param itemStack The item to cleanse
     * @return New ItemStack without stone metadata
     */
    @Nonnull
    public static ItemStack removeStoneData(@Nonnull ItemStack itemStack) {
        Objects.requireNonNull(itemStack, "ItemStack cannot be null");

        return itemStack
            .withMetadata(KEY_IS_STONE, Codec.BOOLEAN, null)
            .withMetadata(KEY_STONE_TYPE, Codec.STRING, null)
            .withMetadata(KEY_STONE_DATA, StoneItemData.CODEC, null);
    }

    /**
     * Gets a display-friendly name for a stone item.
     *
     * @param itemStack The stone item
     * @return The display name, or null if not a stone
     */
    @Nullable
    public static String getDisplayName(@Nullable ItemStack itemStack) {
        return readStoneType(itemStack).map(StoneType::getDisplayName).orElse(null);
    }

    /**
     * Gets a description for a stone item.
     *
     * @param itemStack The stone item
     * @return The stone's effect description, or null if not a stone
     */
    @Nullable
    public static String getDescription(@Nullable ItemStack itemStack) {
        return readStoneType(itemStack).map(StoneType::getDescription).orElse(null);
    }

    /**
     * Checks if two stone items are the same type.
     *
     * @param a First item
     * @param b Second item
     * @return true if both are the same stone type
     */
    public static boolean isSameStoneType(@Nullable ItemStack a, @Nullable ItemStack b) {
        Optional<StoneType> typeA = readStoneType(a);
        Optional<StoneType> typeB = readStoneType(b);

        if (typeA.isEmpty() || typeB.isEmpty()) {
            return false;
        }

        return typeA.get() == typeB.get();
    }

    /**
     * Validates that an ItemStack contains valid stone data.
     *
     * @param itemStack The item to validate
     * @return true if the item has valid, readable stone data
     */
    public static boolean isValid(@Nullable ItemStack itemStack) {
        if (itemStack == null) {
            return false;
        }
        if (!isStone(itemStack)) {
            return false;
        }
        return readStoneType(itemStack).isPresent();
    }
}
