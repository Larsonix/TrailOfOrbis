package io.github.larsonix.trailoforbis.gear.util;

import io.github.larsonix.trailoforbis.gear.codec.GearCodecs;
import io.github.larsonix.trailoforbis.gear.instance.GearInstanceId;
import io.github.larsonix.trailoforbis.gear.instance.GearInstanceIdGenerator;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.GearModifier;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.gear.model.ModifierType;
import io.github.larsonix.trailoforbis.gear.model.ArmorImplicit;
import io.github.larsonix.trailoforbis.gear.model.WeaponImplicit;
import io.github.larsonix.trailoforbis.gems.codec.GemCodecs;
import io.github.larsonix.trailoforbis.gems.model.GemData;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * High-level utilities for reading and writing gear data on ItemStacks.
 *
 * <p>This class provides the primary API for gear metadata operations:
 * <ul>
 *   <li>{@link #getGearData(ItemStack)} - Read full gear data from an item</li>
 *   <li>{@link #setGearData(ItemStack, GearData)} - Write full gear data to an item</li>
 *   <li>{@link #isRpgGear(ItemStack)} - Check if an item has RPG data</li>
 * </ul>
 *
 * <p>All operations are null-safe and return Optional/default values for missing data.
 *
 * <h2>Thread Safety</h2>
 * <p>All methods are thread-safe. ItemStack operations are atomic (immutable pattern).
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Check if item is RPG gear
 * if (GearUtils.isRpgGear(itemStack)) {
 *     // Get gear data
 *     Optional<GearData> data = GearUtils.getGearData(itemStack);
 *     data.ifPresent(d -> {
 *         LOGGER.atInfo().log("Level: %s", d.level());
 *         LOGGER.atInfo().log("Rarity: %s", d.rarity());
 *     });
 * }
 *
 * // Create RPG gear
 * GearData newData = GearData.builder()
 *     .level(50)
 *     .rarity(GearRarity.EPIC)
 *     .quality(75)
 *     .build();
 * ItemStack rpgItem = GearUtils.setGearData(itemStack, newData);
 * }</pre>
 */
public final class GearUtils {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // =========================================================================
    // METADATA KEYS
    // =========================================================================

    /** Namespace prefix for all RPG metadata keys */
    public static final String KEY_PREFIX = "RPG:";

    /** Item level metadata key */
    public static final String KEY_LEVEL = KEY_PREFIX + "Level";

    /** Rarity metadata key (stored as String) */
    public static final String KEY_RARITY = KEY_PREFIX + "Rarity";

    /** Quality metadata key */
    public static final String KEY_QUALITY = KEY_PREFIX + "Quality";

    /** Prefix modifiers metadata key */
    public static final String KEY_PREFIXES = KEY_PREFIX + "Prefixes";

    /** Suffix modifiers metadata key */
    public static final String KEY_SUFFIXES = KEY_PREFIX + "Suffixes";

    /** Instance ID metadata key (for unique per-item display) */
    public static final String KEY_INSTANCE_ID = KEY_PREFIX + "InstanceId";

    /** Base item ID metadata key (stores original item ID before custom ID assignment) */
    public static final String KEY_BASE_ITEM_ID = KEY_PREFIX + "BaseItemId";

    /** Corrupted flag metadata key (true if item has been corrupted by Varyn's Touch) */
    public static final String KEY_CORRUPTED = KEY_PREFIX + "Corrupted";

    /** Weapon implicit damage metadata key (null for non-weapons) */
    public static final String KEY_IMPLICIT = KEY_PREFIX + "Implicit";

    /** Armor implicit defense metadata key (null for non-armor) */
    public static final String KEY_ARMOR_IMPLICIT = KEY_PREFIX + "ArmorImplicit";

    /** Active gem metadata key (null for gear without gems) */
    public static final String KEY_ACTIVE_GEM = KEY_PREFIX + "ActiveGem";

    /** Support gems list metadata key (null or empty for gear without support gems) */
    public static final String KEY_SUPPORT_GEMS = KEY_PREFIX + "SupportGems";

    /** Support slot count metadata key (0 for gear without socket stones applied) */
    public static final String KEY_SUPPORT_SLOT_COUNT = KEY_PREFIX + "SupportSlotCount";

    // =========================================================================
    // READ OPERATIONS
    // =========================================================================

    /**
     * Check if an ItemStack contains RPG gear data.
     *
     * <p>An item is considered RPG gear if it has the RPG:Rarity metadata key.
     * This is the minimal marker for RPG gear.
     *
     * @param itemStack The item to check (may be null)
     * @return true if the item has RPG gear data
     */
    public static boolean isRpgGear(ItemStack itemStack) {
        if (itemStack == null) {
            return false;
        }
        // Check for rarity key as the marker for RPG gear
        String rarity = itemStack.getFromMetadataOrNull(KEY_RARITY, Codec.STRING);
        return rarity != null;
    }

    /**
     * Read complete gear data from an ItemStack.
     *
     * <p>Returns {@link Optional#empty()} if:
     * <ul>
     *   <li>The ItemStack is null</li>
     *   <li>The item is not RPG gear (no rarity key)</li>
     *   <li>The metadata is corrupted (logs warning)</li>
     * </ul>
     *
     * @param itemStack The item to read from (may be null)
     * @return The gear data, or empty if not present or corrupted
     */
    public static Optional<GearData> readGearData(ItemStack itemStack) {
        return getGearData(itemStack);
    }

    /**
     * Read complete gear data from an ItemStack.
     *
     * <p>Returns {@link Optional#empty()} if:
     * <ul>
     *   <li>The ItemStack is null</li>
     *   <li>The item is not RPG gear (no rarity key)</li>
     *   <li>The metadata is corrupted (logs warning)</li>
     * </ul>
     *
     * @param itemStack The item to read from (may be null)
     * @return The gear data, or empty if not present or corrupted
     * @deprecated Use {@link #readGearData(ItemStack)} instead
     */
    @Deprecated
    public static Optional<GearData> getGearData(ItemStack itemStack) {
        if (itemStack == null) {
            return Optional.empty();
        }

        try {
            // Read required fields
            Integer level = itemStack.getFromMetadataOrNull(KEY_LEVEL, Codec.INTEGER);
            String rarityStr = itemStack.getFromMetadataOrNull(KEY_RARITY, Codec.STRING);
            Integer quality = itemStack.getFromMetadataOrNull(KEY_QUALITY, Codec.INTEGER);

            // Check if this is RPG gear (rarity is the marker)
            if (rarityStr == null) {
                return Optional.empty();
            }

            // Parse rarity
            GearRarity rarity;
            try {
                rarity = GearRarity.fromString(rarityStr);
            } catch (IllegalArgumentException e) {
                LOGGER.atWarning().log("Invalid rarity '%s' in item metadata, ignoring item", rarityStr);
                return Optional.empty();
            }

            // Read instance ID (may be null for legacy items)
            String instanceIdStr = itemStack.getFromMetadataOrNull(KEY_INSTANCE_ID, Codec.STRING);
            GearInstanceId instanceId = GearInstanceId.tryFromCompactString(instanceIdStr);

            // Default values for optional/missing fields
            int actualLevel = (level != null) ? level : 1;
            int actualQuality = (quality != null) ? quality : GearData.QUALITY_BASELINE;

            // Read modifiers (may be empty lists)
            List<GearModifier> prefixes = readModifierList(itemStack, KEY_PREFIXES, ModifierType.PREFIX);
            List<GearModifier> suffixes = readModifierList(itemStack, KEY_SUFFIXES, ModifierType.SUFFIX);

            // Read corrupted flag (defaults to false for backwards compatibility)
            Boolean corrupted = itemStack.getFromMetadataOrNull(KEY_CORRUPTED, Codec.BOOLEAN);
            boolean isCorrupted = (corrupted != null) && corrupted;

            // Read implicit (null for non-weapons or legacy items)
            WeaponImplicit implicit = itemStack.getFromMetadataOrNull(KEY_IMPLICIT, GearCodecs.IMPLICIT_CODEC);

            // Read armor implicit (null for non-armor or legacy items)
            ArmorImplicit armorImplicit = itemStack.getFromMetadataOrNull(KEY_ARMOR_IMPLICIT, GearCodecs.ARMOR_IMPLICIT_CODEC);

            // Read baseItemId (null for legacy items)
            String baseItemId = itemStack.getFromMetadataOrNull(KEY_BASE_ITEM_ID, Codec.STRING);

            // Read gem socket data (defaults to null/empty/0 for backwards compatibility)
            GemData activeGem = itemStack.getFromMetadataOrNull(KEY_ACTIVE_GEM, GemCodecs.GEM_DATA_CODEC);
            List<GemData> supportGems = itemStack.getFromMetadataOrNull(KEY_SUPPORT_GEMS, GemCodecs.GEM_LIST_CODEC);
            if (supportGems == null) {
                supportGems = List.of();
            }
            Integer rawSlotCount = itemStack.getFromMetadataOrNull(KEY_SUPPORT_SLOT_COUNT, Codec.INTEGER);
            int supportSlotCount = rawSlotCount != null ? rawSlotCount : 0;

            // Build and return with all fields
            return Optional.of(new GearData(instanceId, actualLevel, rarity, actualQuality, prefixes, suffixes, isCorrupted, implicit, armorImplicit, baseItemId, activeGem, supportGems, supportSlotCount));

        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to read gear data from ItemStack");
            return Optional.empty();
        }
    }

    /**
     * Read a modifier list from metadata.
     *
     * @param itemStack The item to read from
     * @param key The metadata key
     * @param expectedType The expected modifier type (for validation)
     * @return List of modifiers (may be empty, never null)
     */
    private static List<GearModifier> readModifierList(ItemStack itemStack, String key, ModifierType expectedType) {
        List<GearModifier> modifiers = itemStack.getFromMetadataOrNull(key, GearCodecs.MODIFIER_LIST_CODEC);

        if (modifiers == null) {
            return Collections.emptyList();
        }

        // Validate all modifiers have expected type
        List<GearModifier> valid = new ArrayList<>(modifiers.size());
        for (GearModifier mod : modifiers) {
            if (mod.type() == expectedType) {
                valid.add(mod);
            } else {
                LOGGER.atWarning().log("Modifier '%s' has type %s but was in %s list - skipping",
                    mod.id(), mod.type(), expectedType);
            }
        }

        return valid;
    }

    /**
     * Get just the item level from metadata.
     *
     * @param itemStack The item to read from
     * @return The item level, or 1 if not present
     */
    public static int getLevel(ItemStack itemStack) {
        if (itemStack == null) {
            return 1;
        }
        Integer level = itemStack.getFromMetadataOrNull(KEY_LEVEL, Codec.INTEGER);
        return (level != null) ? level : 1;
    }

    /**
     * Get just the rarity from metadata.
     *
     * @param itemStack The item to read from
     * @return The rarity, or empty if not RPG gear
     */
    public static Optional<GearRarity> getRarity(ItemStack itemStack) {
        if (itemStack == null) {
            return Optional.empty();
        }
        String rarityStr = itemStack.getFromMetadataOrNull(KEY_RARITY, Codec.STRING);
        if (rarityStr == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(GearRarity.fromString(rarityStr));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Get just the quality from metadata.
     *
     * @param itemStack The item to read from
     * @return The quality, or 50 (baseline) if not present
     */
    public static int getQuality(ItemStack itemStack) {
        if (itemStack == null) {
            return GearData.QUALITY_BASELINE;
        }
        Integer quality = itemStack.getFromMetadataOrNull(KEY_QUALITY, Codec.INTEGER);
        return (quality != null) ? quality : GearData.QUALITY_BASELINE;
    }

    // =========================================================================
    // WRITE OPERATIONS
    // =========================================================================

    /**
     * Write complete gear data to an ItemStack.
     *
     * <p>This creates a new ItemStack with the gear data written to metadata.
     * The original ItemStack is not modified (immutable pattern).
     *
     * <p><b>IMPORTANT:</b> When the gearData has an instanceId, this method creates
     * a new ItemStack with a custom itemId (e.g., "rpg_gear_xxx") that matches
     * the instance ID. The original base item ID is stored in metadata so it can
     * be retrieved later for server-side operations via {@link #getBaseItem(ItemStack)}.
     *
     * <p>Writes the following metadata keys:
     * <ul>
     *   <li>RPG:Level - Item level</li>
     *   <li>RPG:Rarity - Rarity name string</li>
     *   <li>RPG:Quality - Quality percentage</li>
     *   <li>RPG:Prefixes - List of prefix modifiers</li>
     *   <li>RPG:Suffixes - List of suffix modifiers</li>
     *   <li>RPG:InstanceId - Unique instance identifier</li>
     *   <li>RPG:BaseItemId - Original item ID before custom ID assignment</li>
     * </ul>
     *
     * @param itemStack The item to write to (must not be null)
     * @param gearData The gear data to write (must not be null, must have instanceId)
     * @return New ItemStack with custom itemId and gear data in metadata
     * @throws NullPointerException if itemStack or gearData is null
     * @throws IllegalArgumentException if gearData does not have an instanceId
     */
    public static ItemStack setGearData(ItemStack itemStack, GearData gearData) {
        Objects.requireNonNull(itemStack, "ItemStack cannot be null");
        Objects.requireNonNull(gearData, "GearData cannot be null");

        // GearData must have an instanceId for proper item sync
        if (!gearData.hasInstanceId()) {
            throw new IllegalArgumentException("GearData must have an instanceId for setGearData");
        }

        // Determine base item ID - prioritize gearData, then metadata, then itemStack
        // This prevents the bug where itemStack.getItemId() returns the custom instance ID
        // (e.g., "rpg_gear_xxx") instead of the original item ID (e.g., "Weapon_Sword_Iron")
        String baseItemId = gearData.baseItemId();
        if (baseItemId == null || baseItemId.isEmpty()) {
            baseItemId = getBaseItemId(itemStack);
        }
        if (baseItemId == null || baseItemId.isEmpty()) {
            baseItemId = itemStack.getItemId();
            // Warn if we're falling back to a custom ID - this indicates a bug upstream
            if (baseItemId != null && baseItemId.startsWith("rpg_gear_")) {
                LOGGER.atWarning().log(
                    "WARNING: Using custom ID '%s' as baseItemId - this is a bug! " +
                    "GearData should have baseItemId set. Item will display incorrectly.",
                    baseItemId);
            }
        }

        // Get the custom item ID from gear data
        String customItemId = gearData.getItemId();

        // Build metadata on the original ItemStack first
        ItemStack withMetadata = itemStack
            .withMetadata(KEY_LEVEL, Codec.INTEGER, gearData.level())
            .withMetadata(KEY_RARITY, Codec.STRING, gearData.rarity().name())
            .withMetadata(KEY_QUALITY, Codec.INTEGER, gearData.quality())
            .withMetadata(KEY_PREFIXES, GearCodecs.MODIFIER_LIST_CODEC, gearData.prefixes())
            .withMetadata(KEY_SUFFIXES, GearCodecs.MODIFIER_LIST_CODEC, gearData.suffixes())
            .withMetadata(KEY_INSTANCE_ID, Codec.STRING, gearData.instanceId().toCompactString())
            .withMetadata(KEY_BASE_ITEM_ID, Codec.STRING, baseItemId)
            .withMetadata(KEY_CORRUPTED, Codec.BOOLEAN, gearData.corrupted());

        // Write implicit if present (weapons only)
        if (gearData.implicit() != null) {
            withMetadata = withMetadata.withMetadata(KEY_IMPLICIT, GearCodecs.IMPLICIT_CODEC, gearData.implicit());
        }

        // Write armor implicit if present (armor/shields only)
        if (gearData.armorImplicit() != null) {
            withMetadata = withMetadata.withMetadata(KEY_ARMOR_IMPLICIT, GearCodecs.ARMOR_IMPLICIT_CODEC, gearData.armorImplicit());
        }

        // Compute durability values BEFORE creating ItemStack
        // This is critical because the 3-arg ItemStack constructor calls getItem().getMaxDurability()
        // which returns 0 for custom itemIds (they don't exist in the asset map)
        double curDur = 0;
        double curMaxDur = 0;

        Item baseItem = Item.getAssetMap().getAsset(baseItemId);
        if (baseItem != null) {
            double maxDur = baseItem.getMaxDurability();
            if (maxDur > 0) {
                // Use original durability if valid, otherwise default to max
                curDur = itemStack.getDurability();
                curMaxDur = itemStack.getMaxDurability();

                // If the item didn't have durability set (0), use base item's max durability
                if (curMaxDur <= 0) {
                    curMaxDur = maxDur;
                }
                if (curDur <= 0) {
                    curDur = curMaxDur; // Full durability by default
                }
            }
        }

        // Use 5-argument constructor that explicitly sets durability
        // This bypasses the getItem() lookup that fails for custom itemIds
        //
        // NOTE: getMetadata() is deprecated but no alternative exists for our use case.
        // We need the full BsonDocument to construct an ItemStack with a different itemId
        // while preserving all metadata. Hytale's API doesn't provide a withItemId() method
        // or any other way to change the itemId while keeping metadata intact.
        @SuppressWarnings("deprecation")
        ItemStack result = new ItemStack(
            customItemId,
            withMetadata.getQuantity(),
            curDur,
            curMaxDur,
            withMetadata.getMetadata()
        );

        LOGGER.atFine().log("Created RPG gear: %s (base: %s, durability: %.1f/%.1f)",
            customItemId, baseItemId, curDur, curMaxDur);

        return result;
    }

    /**
     * Write complete gear data to an ItemStack.
     *
     * <p>This is an alias for {@link #setGearData(ItemStack, GearData)}.
     *
     * @param itemStack The item to write to (must not be null)
     * @param gearData The gear data to write (must not be null, must have instanceId)
     * @return New ItemStack with custom itemId and gear data in metadata
     * @throws NullPointerException if itemStack or gearData is null
     * @throws IllegalArgumentException if gearData does not have an instanceId
     */
    public static ItemStack writeGearData(ItemStack itemStack, GearData gearData) {
        return setGearData(itemStack, gearData);
    }

    /**
     * Remove all RPG gear data from an ItemStack.
     *
     * <p>This creates a new ItemStack with all RPG: metadata keys removed.
     * Useful for "cleansing" an item.
     *
     * <p><b>Note:</b> This does NOT restore the original base item ID.
     * If you need the original item, use {@link #getBaseItemId(ItemStack)} first.
     *
     * @param itemStack The item to cleanse (must not be null)
     * @return New ItemStack without RPG metadata
     * @throws NullPointerException if itemStack is null
     */
    public static ItemStack removeGearData(ItemStack itemStack) {
        Objects.requireNonNull(itemStack, "ItemStack cannot be null");

        // Remove all RPG keys by setting to null
        // Note: withMetadata with null value removes the key
        return itemStack
            .withMetadata(KEY_LEVEL, Codec.INTEGER, null)
            .withMetadata(KEY_RARITY, Codec.STRING, null)
            .withMetadata(KEY_QUALITY, Codec.INTEGER, null)
            .withMetadata(KEY_PREFIXES, GearCodecs.MODIFIER_LIST_CODEC, null)
            .withMetadata(KEY_SUFFIXES, GearCodecs.MODIFIER_LIST_CODEC, null)
            .withMetadata(KEY_INSTANCE_ID, Codec.STRING, null)
            .withMetadata(KEY_BASE_ITEM_ID, Codec.STRING, null)
            .withMetadata(KEY_CORRUPTED, Codec.BOOLEAN, null)
            .withMetadata(KEY_ARMOR_IMPLICIT, GearCodecs.ARMOR_IMPLICIT_CODEC, null);
    }

    /**
     * Ensures the gear item has an instance ID assigned.
     *
     * <p>If the item already has an instance ID, returns the original item unchanged.
     * If the item is not RPG gear, returns the original item unchanged.
     * Otherwise, generates a new instance ID and returns a new ItemStack with it.
     *
     * <p>Use this to migrate legacy items created before instance IDs were introduced.
     *
     * @param itemStack The item to check (must not be null)
     * @return ItemStack with instance ID (may be same instance if already has one)
     */
    @Nonnull
    public static ItemStack ensureInstanceId(@Nonnull ItemStack itemStack) {
        Objects.requireNonNull(itemStack, "ItemStack cannot be null");

        // Check if it's RPG gear
        Optional<GearData> dataOpt = readGearData(itemStack);
        if (dataOpt.isEmpty()) {
            return itemStack; // Not RPG gear, return unchanged
        }

        GearData data = dataOpt.get();
        if (data.hasInstanceId()) {
            return itemStack; // Already has instance ID
        }

        // Generate new instance ID and update
        GearInstanceId newId = GearInstanceIdGenerator.generate();
        GearData updatedData = data.withInstanceId(newId);

        LOGGER.atFine().log("Generated instance ID for legacy item: %s", newId.toItemId());

        return setGearData(itemStack, updatedData);
    }

    /**
     * Gets just the instance ID from an item.
     *
     * @param itemStack The item to read from
     * @return The instance ID, or empty if not present
     */
    @Nonnull
    public static Optional<GearInstanceId> getInstanceId(ItemStack itemStack) {
        if (itemStack == null) {
            return Optional.empty();
        }
        String instanceIdStr = itemStack.getFromMetadataOrNull(KEY_INSTANCE_ID, Codec.STRING);
        return Optional.ofNullable(GearInstanceId.tryFromCompactString(instanceIdStr));
    }

    /**
     * Gets the base item ID stored in metadata.
     *
     * <p>When gear is created, the original item ID (e.g., "Weapon_Axe_Copper")
     * is stored in metadata before the ItemStack's itemId is changed to a custom
     * instance ID (e.g., "rpg_gear_xxx"). This method retrieves that original ID.
     *
     * @param itemStack The item to read from
     * @return The base item ID, or null if not stored
     */
    @javax.annotation.Nullable
    public static String getBaseItemId(ItemStack itemStack) {
        if (itemStack == null) {
            return null;
        }
        return itemStack.getFromMetadataOrNull(KEY_BASE_ITEM_ID, Codec.STRING);
    }

    /**
     * Gets the base Item for server-side operations.
     *
     * <p>RPG gear ItemStacks use custom itemIds (e.g., "rpg_gear_xxx") for client-side
     * tooltip matching. For server-side operations that need the actual Item asset
     * (model, durability, equipment slot, etc.), use this method to get the original
     * base item.
     *
     * <p>Falls back to itemStack.getItem() if no base item ID is stored.
     *
     * @param itemStack The item to get the base Item for
     * @return The base Item, or the ItemStack's current Item if no base is stored
     */
    @Nonnull
    public static Item getBaseItem(@Nonnull ItemStack itemStack) {
        Objects.requireNonNull(itemStack, "ItemStack cannot be null");

        // Try to get stored base item ID
        String baseItemId = getBaseItemId(itemStack);
        if (baseItemId != null) {
            Item item = Item.getAssetMap().getAsset(baseItemId);
            if (item != null) {
                return item;
            }
            LOGGER.atWarning().log("Base item not found in asset map: %s", baseItemId);
        }

        // Fallback to current item
        return itemStack.getItem();
    }

    /**
     * Update just the quality on an existing RPG item.
     *
     * <p>Reads existing data, updates quality, writes back.
     *
     * @param itemStack The item to update
     * @param newQuality The new quality value
     * @return Updated ItemStack, or original if not RPG gear
     */
    public static ItemStack updateQuality(ItemStack itemStack, int newQuality) {
        Optional<GearData> existing = getGearData(itemStack);
        if (existing.isEmpty()) {
            return itemStack;
        }
        GearData updated = existing.get().withQuality(newQuality);
        return setGearData(itemStack, updated);
    }

    /**
     * Update just the rarity on an existing RPG item.
     *
     * <p>Note: May fail if current modifiers exceed new rarity's max.
     *
     * @param itemStack The item to update
     * @param newRarity The new rarity
     * @return Updated ItemStack, or original if not RPG gear or invalid
     */
    public static ItemStack updateRarity(ItemStack itemStack, GearRarity newRarity) {
        Optional<GearData> existing = getGearData(itemStack);
        if (existing.isEmpty()) {
            return itemStack;
        }
        try {
            GearData updated = existing.get().withRarity(newRarity);
            return setGearData(itemStack, updated);
        } catch (IllegalArgumentException e) {
            LOGGER.atWarning().log("Cannot update rarity to %s: %s", newRarity, e.getMessage());
            return itemStack;
        }
    }

    // =========================================================================
    // VALIDATION HELPERS
    // =========================================================================

    /**
     * Validate that gear data is internally consistent.
     *
     * <p>Checks:
     * <ul>
     *   <li>Level is within valid range</li>
     *   <li>Quality is within valid range</li>
     *   <li>Modifier count doesn't exceed rarity maximum</li>
     *   <li>All prefixes are PREFIX type</li>
     *   <li>All suffixes are SUFFIX type</li>
     * </ul>
     *
     * @param gearData The data to validate
     * @return List of validation errors (empty if valid)
     */
    public static List<String> validateGearData(GearData gearData) {
        List<String> errors = new ArrayList<>();

        // Level range
        if (gearData.level() < GearData.MIN_LEVEL || gearData.level() > GearData.MAX_LEVEL) {
            errors.add("Level " + gearData.level() + " is out of range [" +
                GearData.MIN_LEVEL + ", " + GearData.MAX_LEVEL + "]");
        }

        // Quality range
        if (gearData.quality() < GearData.MIN_QUALITY || gearData.quality() > GearData.PERFECT_QUALITY) {
            errors.add("Quality " + gearData.quality() + " is out of range [" +
                GearData.MIN_QUALITY + ", " + GearData.PERFECT_QUALITY + "]");
        }

        // Modifier count
        int totalMods = gearData.modifierCount();
        if (totalMods > gearData.rarity().getMaxModifiers()) {
            errors.add("Too many modifiers (" + totalMods + ") for " +
                gearData.rarity() + " (max " + gearData.rarity().getMaxModifiers() + ")");
        }

        // Prefix types
        for (GearModifier mod : gearData.prefixes()) {
            if (mod.type() != ModifierType.PREFIX) {
                errors.add("Modifier '" + mod.id() + "' in prefixes has wrong type: " + mod.type());
            }
        }

        // Suffix types
        for (GearModifier mod : gearData.suffixes()) {
            if (mod.type() != ModifierType.SUFFIX) {
                errors.add("Modifier '" + mod.id() + "' in suffixes has wrong type: " + mod.type());
            }
        }

        return errors;
    }

    // Prevent instantiation
    private GearUtils() {}
}
