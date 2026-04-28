package io.github.larsonix.trailoforbis.maps.items;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import io.github.larsonix.trailoforbis.gear.item.CustomItemInstanceId;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.maps.core.RealmBiomeType;
import io.github.larsonix.trailoforbis.maps.core.RealmLayoutShape;
import io.github.larsonix.trailoforbis.maps.core.RealmLayoutSize;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Optional;

/**
 * Utilities for reading and writing {@link RealmMapData} on ItemStacks.
 *
 * <p>This class provides the primary API for realm map metadata operations:
 * <ul>
 *   <li>{@link #isRealmMap(ItemStack)} - Check if an item is a realm map</li>
 *   <li>{@link #readMapData(ItemStack)} - Read map data from an item</li>
 *   <li>{@link #writeMapData(ItemStack, RealmMapData)} - Write map data to an item</li>
 * </ul>
 *
 * <p>All operations are null-safe and return Optional/default values for missing data.
 *
 * <h2>Thread Safety</h2>
 * <p>All methods are thread-safe. ItemStack operations are atomic (immutable pattern).
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Check if item is a realm map
 * if (RealmMapUtils.isRealmMap(itemStack)) {
 *     Optional<RealmMapData> data = RealmMapUtils.readMapData(itemStack);
 *     data.ifPresent(d -> {
 *         LOGGER.atInfo().log("Level: %s", d.level());
 *         LOGGER.atInfo().log("Biome: %s", d.biome());
 *     });
 * }
 *
 * // Create a realm map
 * RealmMapData mapData = RealmMapData.builder()
 *     .level(50)
 *     .rarity(GearRarity.EPIC)
 *     .biome(RealmBiomeType.VOLCANO)
 *     .build();
 * ItemStack realmMap = RealmMapUtils.writeMapData(baseItem, mapData);
 * }</pre>
 *
 * @see RealmMapData
 */
public final class RealmMapUtils {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // =========================================================================
    // METADATA KEYS
    // =========================================================================

    /** Namespace prefix for all realm map metadata keys */
    public static final String KEY_PREFIX = "REALM:";

    /** Main map data key - stores the full RealmMapData as BSON */
    public static final String KEY_MAP_DATA = KEY_PREFIX + "MapData";

    /** Marker key to quickly identify realm maps without full deserialization */
    public static final String KEY_IS_MAP = KEY_PREFIX + "IsMap";

    /** Instance ID key - stores the unique instance ID for custom item registration */
    public static final String KEY_INSTANCE_ID = KEY_PREFIX + "InstanceId";

    /** Base item ID key - stores the Hytale base item ID used for registration */
    public static final String KEY_BASE_ITEM_ID = KEY_PREFIX + "BaseItemId";

    // =========================================================================
    // BACKUP KEYS (simple codecs for reconnect recovery)
    // =========================================================================

    /** Backup key for map level - simple INTEGER codec survives when BSON fails */
    public static final String KEY_LEVEL = KEY_PREFIX + "Level";

    /** Backup key for rarity - simple STRING codec (enum name) */
    public static final String KEY_RARITY = KEY_PREFIX + "Rarity";

    /** Backup key for biome - simple STRING codec (enum name) */
    public static final String KEY_BIOME = KEY_PREFIX + "Biome";

    /** Backup key for size - simple STRING codec (enum name) */
    public static final String KEY_SIZE = KEY_PREFIX + "Size";

    /** Backup key for shape - simple STRING codec (enum name) */
    public static final String KEY_SHAPE = KEY_PREFIX + "Shape";

    /** Backup key for identified flag - simple BOOLEAN codec */
    public static final String KEY_IDENTIFIED = KEY_PREFIX + "Identified";

    // =========================================================================
    // READ OPERATIONS
    // =========================================================================

    /**
     * Check if an ItemStack is a realm map.
     *
     * <p>An item is considered a realm map if it has the REALM:IsMap marker key.
     * This is a fast check that doesn't require deserializing the full map data.
     *
     * @param itemStack The item to check (may be null)
     * @return true if the item is a realm map
     */
    public static boolean isRealmMap(@Nullable ItemStack itemStack) {
        if (itemStack == null) {
            return false;
        }
        Boolean isMap = itemStack.getFromMetadataOrNull(KEY_IS_MAP, Codec.BOOLEAN);
        return Boolean.TRUE.equals(isMap);
    }

    /**
     * Read realm map data from an ItemStack.
     *
     * <p>Returns {@link Optional#empty()} if:
     * <ul>
     *   <li>The ItemStack is null</li>
     *   <li>The item is not a realm map</li>
     *   <li>The metadata is corrupted (logs warning)</li>
     * </ul>
     *
     * @param itemStack The item to read from (may be null)
     * @return The map data, or empty if not present or corrupted
     */
    @Nonnull
    public static Optional<RealmMapData> readMapData(@Nullable ItemStack itemStack) {
        if (itemStack == null) {
            return Optional.empty();
        }

        // Quick check - is this a realm map?
        if (!isRealmMap(itemStack)) {
            return Optional.empty();
        }

        try {
            RealmMapData data = itemStack.getFromMetadataOrNull(KEY_MAP_DATA, RealmMapData.CODEC);
            return Optional.ofNullable(data);

        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to read realm map data from ItemStack");
            return Optional.empty();
        }
    }

    /**
     * Get just the map level from metadata.
     *
     * @param itemStack The item to read from
     * @return The map level, or 1 if not present or not a realm map
     */
    public static int getLevel(@Nullable ItemStack itemStack) {
        return readMapData(itemStack)
            .map(RealmMapData::level)
            .orElse(1);
    }

    /**
     * Get just the rarity from metadata.
     *
     * @param itemStack The item to read from
     * @return The rarity, or empty if not a realm map
     */
    @Nonnull
    public static Optional<GearRarity> getRarity(@Nullable ItemStack itemStack) {
        return readMapData(itemStack).map(RealmMapData::rarity);
    }

    /**
     * Get just the biome type from metadata.
     *
     * @param itemStack The item to read from
     * @return The biome type, or empty if not a realm map
     */
    @Nonnull
    public static Optional<RealmBiomeType> getBiome(@Nullable ItemStack itemStack) {
        return readMapData(itemStack).map(RealmMapData::biome);
    }

    /**
     * Get just the layout size from metadata.
     *
     * @param itemStack The item to read from
     * @return The layout size, or empty if not a realm map
     */
    @Nonnull
    public static Optional<RealmLayoutSize> getSize(@Nullable ItemStack itemStack) {
        return readMapData(itemStack).map(RealmMapData::size);
    }

    /**
     * Check if the map is identified (modifiers revealed).
     *
     * @param itemStack The item to check
     * @return true if identified, false if not a realm map or unidentified
     */
    public static boolean isIdentified(@Nullable ItemStack itemStack) {
        return readMapData(itemStack)
            .map(RealmMapData::identified)
            .orElse(false);
    }

    /**
     * Check if the map is corrupted.
     *
     * @param itemStack The item to check
     * @return true if corrupted, false if not a realm map or not corrupted
     */
    public static boolean isCorrupted(@Nullable ItemStack itemStack) {
        return readMapData(itemStack)
            .map(RealmMapData::corrupted)
            .orElse(false);
    }

    // =========================================================================
    // WRITE OPERATIONS
    // =========================================================================

    /**
     * Write realm map data to an ItemStack.
     *
     * <p>This creates a new ItemStack with the map data written to metadata.
     * The original ItemStack is not modified (immutable pattern).
     *
     * <p>Writes the following metadata keys:
     * <ul>
     *   <li>REALM:IsMap - Boolean marker for quick identification</li>
     *   <li>REALM:MapData - Full RealmMapData as BSON</li>
     *   <li>REALM:InstanceId - Compact instance ID string (if present)</li>
     *   <li>REALM:BaseItemId - Base Hytale item ID</li>
     * </ul>
     *
     * @param itemStack The item to write to (must not be null)
     * @param mapData The map data to write (must not be null)
     * @return New ItemStack with map data in metadata
     * @throws NullPointerException if itemStack or mapData is null
     */
    @Nonnull
    public static ItemStack writeMapData(@Nonnull ItemStack itemStack, @Nonnull RealmMapData mapData) {
        Objects.requireNonNull(itemStack, "ItemStack cannot be null");
        Objects.requireNonNull(mapData, "RealmMapData cannot be null");

        ItemStack result = itemStack
            .withMetadata(KEY_IS_MAP, Codec.BOOLEAN, true)
            .withMetadata(KEY_MAP_DATA, RealmMapData.CODEC, mapData)
            .withMetadata(KEY_BASE_ITEM_ID, Codec.STRING, mapData.getBaseItemId());

        // Write instance ID if present
        if (mapData.instanceId() != null) {
            result = result.withMetadata(KEY_INSTANCE_ID, Codec.STRING, mapData.instanceId().toCompactString());
        }

        // Write backup keys for fallback recovery (simple codecs survive when BSON fails)
        result = result
            .withMetadata(KEY_LEVEL, Codec.INTEGER, mapData.level())
            .withMetadata(KEY_RARITY, Codec.STRING, mapData.rarity().name())
            .withMetadata(KEY_BIOME, Codec.STRING, mapData.biome().name())
            .withMetadata(KEY_SIZE, Codec.STRING, mapData.size().name())
            .withMetadata(KEY_SHAPE, Codec.STRING, mapData.shape().name())
            .withMetadata(KEY_IDENTIFIED, Codec.BOOLEAN, mapData.identified());

        LOGGER.atFine().log("Created realm map: level=%d, rarity=%s, biome=%s, size=%s, instanceId=%s",
            mapData.level(), mapData.rarity(), mapData.biome(), mapData.size(),
            mapData.instanceId() != null ? mapData.instanceId().toItemId() : "none");

        return result;
    }

    /**
     * Reads the instance ID from an ItemStack.
     *
     * @param itemStack The item to read from
     * @return The instance ID, or empty if not a realm map or no instance ID
     */
    @Nonnull
    public static Optional<CustomItemInstanceId> getInstanceId(@Nullable ItemStack itemStack) {
        if (!isRealmMap(itemStack)) {
            return Optional.empty();
        }

        String compactId = itemStack.getFromMetadataOrNull(KEY_INSTANCE_ID, Codec.STRING);
        if (compactId == null || compactId.isEmpty()) {
            return Optional.empty();
        }

        return Optional.ofNullable(CustomItemInstanceId.tryFromCompactString(compactId));
    }

    /**
     * Reads the base item ID from an ItemStack.
     *
     * @param itemStack The item to read from
     * @return The base item ID, or the default if not present
     */
    @Nonnull
    public static String getBaseItemId(@Nullable ItemStack itemStack) {
        if (!isRealmMap(itemStack)) {
            return RealmMapData.DEFAULT_BASE_ITEM_ID;
        }

        String baseItemId = itemStack.getFromMetadataOrNull(KEY_BASE_ITEM_ID, Codec.STRING);
        return baseItemId != null ? baseItemId : RealmMapData.DEFAULT_BASE_ITEM_ID;
    }

    /**
     * Gets the custom item ID for this realm map.
     *
     * <p>This is the ID used in UpdateItems packets and asset map registration.
     *
     * @param itemStack The item to check
     * @return The custom item ID, or empty if no instance ID
     */
    @Nonnull
    public static Optional<String> getCustomItemId(@Nullable ItemStack itemStack) {
        return getInstanceId(itemStack).map(CustomItemInstanceId::toItemId);
    }

    /**
     * Checks if this realm map has a registered instance ID.
     *
     * <p>Maps with instance IDs have been registered for custom display.
     *
     * @param itemStack The item to check
     * @return true if the map has an instance ID
     */
    public static boolean hasInstanceId(@Nullable ItemStack itemStack) {
        return getInstanceId(itemStack).isPresent();
    }

    /**
     * Remove all realm map data from an ItemStack.
     *
     * <p>This creates a new ItemStack with all REALM: metadata keys removed.
     *
     * @param itemStack The item to cleanse (must not be null)
     * @return New ItemStack without realm map metadata
     * @throws NullPointerException if itemStack is null
     */
    @Nonnull
    public static ItemStack removeMapData(@Nonnull ItemStack itemStack) {
        Objects.requireNonNull(itemStack, "ItemStack cannot be null");

        return itemStack
            .withMetadata(KEY_IS_MAP, Codec.BOOLEAN, null)
            .withMetadata(KEY_MAP_DATA, RealmMapData.CODEC, null)
            .withMetadata(KEY_INSTANCE_ID, Codec.STRING, null)
            .withMetadata(KEY_BASE_ITEM_ID, Codec.STRING, null)
            // Clear backup keys
            .withMetadata(KEY_LEVEL, Codec.INTEGER, null)
            .withMetadata(KEY_RARITY, Codec.STRING, null)
            .withMetadata(KEY_BIOME, Codec.STRING, null)
            .withMetadata(KEY_SIZE, Codec.STRING, null)
            .withMetadata(KEY_SHAPE, Codec.STRING, null)
            .withMetadata(KEY_IDENTIFIED, Codec.BOOLEAN, null);
    }

    // =========================================================================
    // UPDATE OPERATIONS
    // =========================================================================

    /**
     * Identify a realm map (reveal its modifiers).
     *
     * <p>If the map is already identified or not a realm map, returns the original.
     *
     * @param itemStack The item to identify
     * @return ItemStack with identified map, or original if not applicable
     */
    @Nonnull
    public static ItemStack identify(@Nonnull ItemStack itemStack) {
        Objects.requireNonNull(itemStack, "ItemStack cannot be null");

        Optional<RealmMapData> dataOpt = readMapData(itemStack);
        if (dataOpt.isEmpty()) {
            return itemStack;
        }

        RealmMapData data = dataOpt.get();
        if (data.identified()) {
            return itemStack; // Already identified
        }

        return writeMapData(itemStack, data.identify());
    }

    /**
     * Corrupt a realm map (prevents further modification).
     *
     * <p>If the map is already corrupted or not a realm map, returns the original.
     *
     * @param itemStack The item to corrupt
     * @return ItemStack with corrupted map, or original if not applicable
     */
    @Nonnull
    public static ItemStack corrupt(@Nonnull ItemStack itemStack) {
        Objects.requireNonNull(itemStack, "ItemStack cannot be null");

        Optional<RealmMapData> dataOpt = readMapData(itemStack);
        if (dataOpt.isEmpty()) {
            return itemStack;
        }

        RealmMapData data = dataOpt.get();
        if (data.corrupted()) {
            return itemStack; // Already corrupted
        }

        return writeMapData(itemStack, data.corrupt());
    }

    /**
     * Update the map data on an existing realm map item.
     *
     * <p>If the item is not a realm map, returns the original unchanged.
     *
     * @param itemStack The item to update
     * @param updater Function that takes current data and returns updated data
     * @return Updated ItemStack, or original if not a realm map
     */
    @Nonnull
    public static ItemStack updateMapData(
            @Nonnull ItemStack itemStack,
            @Nonnull java.util.function.Function<RealmMapData, RealmMapData> updater) {
        Objects.requireNonNull(itemStack, "ItemStack cannot be null");
        Objects.requireNonNull(updater, "updater cannot be null");

        Optional<RealmMapData> dataOpt = readMapData(itemStack);
        if (dataOpt.isEmpty()) {
            return itemStack;
        }

        RealmMapData updated = updater.apply(dataOpt.get());
        return writeMapData(itemStack, updated);
    }

    // =========================================================================
    // VALIDATION
    // =========================================================================

    /**
     * Validate that an ItemStack contains valid realm map data.
     *
     * @param itemStack The item to validate
     * @return true if the item has valid, readable realm map data
     */
    public static boolean isValid(@Nullable ItemStack itemStack) {
        if (itemStack == null) {
            return false;
        }
        if (!isRealmMap(itemStack)) {
            return false;
        }
        // Try to actually read the data
        return readMapData(itemStack).isPresent();
    }

    // =========================================================================
    // INSTANCE ID RECOVERY
    // =========================================================================

    /**
     * Attempts to recover a map's instanceId from its ItemStack.
     *
     * <p>Maps are created with custom item IDs like "rpg_map_1706123456789_0".
     * If the metadata's instanceId is corrupted, we can recover it from the
     * ItemStack's item type string.
     *
     * <p>This is useful when:
     * <ul>
     *   <li>Map metadata was corrupted during serialization</li>
     *   <li>Map was created before instanceId metadata was added</li>
     *   <li>Player reconnects and metadata parsing fails</li>
     * </ul>
     *
     * @param itemStack The map item stack
     * @return The recovered instanceId, or empty if the item type is not a custom map ID
     */
    @Nonnull
    public static Optional<CustomItemInstanceId> recoverInstanceIdFromItemStack(@Nonnull ItemStack itemStack) {
        Objects.requireNonNull(itemStack, "itemStack cannot be null");

        String itemId = itemStack.getItemId();

        // Check if this is a custom map item ID
        if (CustomItemInstanceId.isMapId(itemId)) {
            return Optional.ofNullable(CustomItemInstanceId.tryFromItemId(itemId));
        }

        return Optional.empty();
    }

    /**
     * Repairs map data by recovering instanceId from ItemStack if missing.
     *
     * <p>When a player reconnects with a map in their inventory, the metadata
     * might fail to deserialize the instanceId. However, the ItemStack's item
     * type IS the custom item ID (e.g., "rpg_map_1706123456789_0"), so we can
     * recover the instanceId from it.
     *
     * <p>This method:
     * <ol>
     *   <li>Returns the original mapData if instanceId is already set</li>
     *   <li>Attempts to recover instanceId from the ItemStack's item type</li>
     *   <li>Returns a repaired RealmMapData with the recovered instanceId</li>
     *   <li>Returns the original mapData if recovery fails</li>
     * </ol>
     *
     * @param itemStack The map item stack
     * @param mapData The map data (possibly with null instanceId)
     * @return The repaired map data with instanceId, or original if recovery fails
     */
    @Nonnull
    public static RealmMapData repairMapIfNeeded(
            @Nonnull ItemStack itemStack,
            @Nonnull RealmMapData mapData) {
        Objects.requireNonNull(itemStack, "itemStack cannot be null");
        Objects.requireNonNull(mapData, "mapData cannot be null");

        if (mapData.instanceId() != null) {
            return mapData;  // Already valid
        }

        // Try to recover from ItemStack's item type
        Optional<CustomItemInstanceId> recoveredId = recoverInstanceIdFromItemStack(itemStack);
        if (recoveredId.isPresent()) {
            LOGGER.atInfo().log("Recovered map instanceId from ItemStack: %s",
                recoveredId.get().toItemId());
            return mapData.withInstanceId(recoveredId.get());
        }

        // Could not recover - this map uses a vanilla item type (e.g., Objective_Treasure_Map)
        // It won't crash the client but won't have custom display either
        LOGGER.atFine().log("Map has no custom item ID - using vanilla display");
        return mapData;
    }

    // =========================================================================
    // PATTERN-BASED DETECTION (for reconnect recovery)
    // =========================================================================

    /**
     * Checks if an ItemStack is a realm map by examining its item ID pattern.
     *
     * <p>This is a fallback detection method for when metadata (KEY_IS_MAP)
     * fails to deserialize on player reconnect. Realm maps have custom item IDs
     * matching the pattern "rpg_map_*".
     *
     * @param itemStack The item to check (may be null)
     * @return true if the item ID matches the map pattern
     */
    public static boolean isMapByItemId(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return false;
        }
        return CustomItemInstanceId.isMapId(itemStack.getItemId());
    }

    /**
     * Checks if an ItemStack is a realm map using any available method.
     *
     * <p>Tries metadata-based detection first (fast), then falls back to
     * item ID pattern matching if metadata fails. This handles reconnect
     * scenarios where BSON metadata deserialization fails.
     *
     * @param itemStack The item to check (may be null)
     * @return true if the item is a realm map (by metadata OR item ID pattern)
     */
    public static boolean isMapAnyMethod(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return false;
        }
        // Try metadata first (fast path)
        if (isRealmMap(itemStack)) {
            return true;
        }
        // Fallback to item ID pattern (reconnect recovery)
        return isMapByItemId(itemStack);
    }

    /**
     * Reads map data with fallback recovery from backup keys and item ID.
     *
     * <p>When a player reconnects, BSON metadata deserialization may fail for
     * the full RealmMapData. However, the following survive:
     * <ul>
     *   <li>Item ID pattern: "rpg_map_*" (allows instanceId recovery)</li>
     *   <li>Backup keys: KEY_LEVEL, KEY_RARITY, KEY_BIOME, KEY_SIZE, KEY_SHAPE, KEY_IDENTIFIED</li>
     * </ul>
     *
     * <p>This method:
     * <ol>
     *   <li>Tries normal {@link #readMapData(ItemStack)} first</li>
     *   <li>If that fails, checks if item ID matches map pattern</li>
     *   <li>Recovers instanceId from item ID</li>
     *   <li>Recovers map properties from backup metadata keys</li>
     *   <li>Reconstructs RealmMapData from recovered pieces</li>
     * </ol>
     *
     * <p>Note: Recovered maps will have empty modifier lists since modifiers
     * are not stored in backup keys. The map is still functional for display
     * and portal opening, but modifiers may not be shown until full data is
     * re-synced from the server.
     *
     * @param itemStack The item to read from (may be null)
     * @return The map data, or empty if not a map or unrecoverable
     */
    @Nonnull
    public static Optional<RealmMapData> readMapDataWithFallback(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return Optional.empty();
        }

        // Try normal read first (handles metadata-intact case)
        Optional<RealmMapData> normalRead = readMapData(itemStack);
        if (normalRead.isPresent()) {
            // Repair instanceId if missing
            RealmMapData data = normalRead.get();
            return Optional.of(repairMapIfNeeded(itemStack, data));
        }

        // Normal read failed - try pattern-based detection
        if (!isMapByItemId(itemStack)) {
            return Optional.empty();  // Not a map
        }

        LOGGER.atInfo().log("Map metadata corrupted, attempting fallback recovery from item ID: %s",
            itemStack.getItemId());

        // Recover instanceId from item ID
        Optional<CustomItemInstanceId> instanceIdOpt = recoverInstanceIdFromItemStack(itemStack);
        if (instanceIdOpt.isEmpty()) {
            LOGGER.atWarning().log("Failed to recover map instanceId from item ID: %s",
                itemStack.getItemId());
            return Optional.empty();
        }

        // Try to read properties from backup metadata keys
        try {
            Integer levelInt = itemStack.getFromMetadataOrNull(KEY_LEVEL, Codec.INTEGER);
            String rarityName = itemStack.getFromMetadataOrNull(KEY_RARITY, Codec.STRING);
            String biomeName = itemStack.getFromMetadataOrNull(KEY_BIOME, Codec.STRING);
            String sizeName = itemStack.getFromMetadataOrNull(KEY_SIZE, Codec.STRING);
            String shapeName = itemStack.getFromMetadataOrNull(KEY_SHAPE, Codec.STRING);
            Boolean identified = itemStack.getFromMetadataOrNull(KEY_IDENTIFIED, Codec.BOOLEAN);

            // Validate that we have minimum required fields
            if (levelInt == null || rarityName == null || biomeName == null || sizeName == null) {
                LOGGER.atWarning().log("Map backup keys incomplete for: %s (level=%s, rarity=%s, biome=%s, size=%s)",
                    itemStack.getItemId(), levelInt, rarityName, biomeName, sizeName);
                return Optional.empty();
            }

            // Parse enums
            GearRarity rarity = GearRarity.valueOf(rarityName);
            RealmBiomeType biome = RealmBiomeType.valueOf(biomeName);
            RealmLayoutSize size = RealmLayoutSize.valueOf(sizeName);
            RealmLayoutShape shape = shapeName != null
                ? RealmLayoutShape.valueOf(shapeName)
                : RealmLayoutShape.CIRCULAR;

            // Reconstruct map data with empty modifiers
            RealmMapData reconstructed = RealmMapData.builder()
                .level(levelInt)
                .rarity(rarity)
                .biome(biome)
                .size(size)
                .shape(shape)
                .identified(Boolean.TRUE.equals(identified))
                .instanceId(instanceIdOpt.get())
                .build();

            LOGGER.atInfo().log("Successfully recovered map data: level=%d, rarity=%s, biome=%s, instanceId=%s",
                levelInt, rarity, biome, instanceIdOpt.get().toItemId());
            return Optional.of(reconstructed);

        } catch (IllegalArgumentException e) {
            LOGGER.atWarning().withCause(e).log("Failed to parse backup keys for map: %s",
                itemStack.getItemId());
            return Optional.empty();
        }
    }

    // Prevent instantiation
    private RealmMapUtils() {}
}
