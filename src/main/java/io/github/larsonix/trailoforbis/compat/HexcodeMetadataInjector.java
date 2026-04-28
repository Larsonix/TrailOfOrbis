package io.github.larsonix.trailoforbis.compat;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.schema.SchemaContext;
import com.hypixel.hytale.codec.schema.config.ObjectSchema;
import com.hypixel.hytale.codec.schema.config.Schema;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.gear.model.WeaponType;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.BsonValue;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Injects Hexcode-compatible metadata onto RPG gear ItemStacks.
 *
 * <p>When a Hexcode staff or spellbook drops as RPG loot, this class injects
 * the "HexStaff" or "HexBook" metadata key with a BSON structure that Hexcode's
 * {@code CasterInventory} can decode. This means the item is immediately usable
 * for spell casting without Hexcode needing to do its own initialization.
 *
 * <p>All BSON structures are written using Hytale's Codec API — zero Hexcode imports.
 *
 * <h2>Metadata Keys</h2>
 * <ul>
 *   <li>"HexStaff" → {@code {"StyleId": "ring", "CastDecayRate": 0.05}}</li>
 *   <li>"HexBook" → {@code {"Hexes": [], "MaxCapacity": N, "BookId": "", "HexNames": {}, "HexColors": {}}}</li>
 * </ul>
 *
 * @see HexcodeCompat#isLoaded()
 */
public final class HexcodeMetadataInjector {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Metadata key for hex staff data — must match CasterInventory.METADATA_KEY_HEX_STAFF */
    public static final String KEY_HEX_STAFF = "HexStaff";

    /** Metadata key for hex book data — must match CasterInventory.METADATA_KEY_HEX_BOOK */
    public static final String KEY_HEX_BOOK = "HexBook";

    // =========================================================================
    // DATA RECORDS
    // =========================================================================

    /**
     * Staff metadata matching Hexcode's HexStaffComponent BSON format.
     *
     * @param styleId The cast style ID (e.g., "ring", "line", "cone")
     * @param castDecayRate Rate of cast decay (lower = more sustained)
     */
    public record HexStaffData(String styleId, float castDecayRate) {
        public static final HexStaffData DEFAULT = new HexStaffData("ring", 0.05f);
    }

    /**
     * Book metadata matching Hexcode's HexBookComponent BSON format.
     *
     * @param maxCapacity Maximum hex spell slots (scales with RPG rarity)
     * @param bookId The book asset ID (empty for RPG-generated books)
     */
    public record HexBookData(int maxCapacity, String bookId) {}

    // =========================================================================
    // CODECS
    // =========================================================================

    /**
     * Codec that writes BSON compatible with Hexcode's HexStaffComponent.CODEC.
     *
     * <p>BSON structure: {@code {"StyleId": "ring", "CastDecayRate": 0.05}}
     */
    public static final Codec<HexStaffData> HEX_STAFF_CODEC = createHexStaffCodec();

    /**
     * Codec that writes BSON compatible with Hexcode's HexBookComponent.CODEC.
     *
     * <p>BSON structure: {@code {"Hexes": [], "MaxCapacity": N, "BookId": "", "HexNames": {}, "HexColors": {}}}
     */
    public static final Codec<HexBookData> HEX_BOOK_CODEC = createHexBookCodec();

    private static Codec<HexStaffData> createHexStaffCodec() {
        return new Codec<>() {
            @Override
            public HexStaffData decode(@Nonnull BsonValue encoded, @Nonnull ExtraInfo extraInfo) {
                if (!(encoded instanceof BsonDocument doc)) {
                    return HexStaffData.DEFAULT;
                }
                String styleId = doc.containsKey("StyleId")
                        ? doc.getString("StyleId").getValue()
                        : "ring";
                float castDecayRate = doc.containsKey("CastDecayRate")
                        ? (float) doc.getDouble("CastDecayRate").getValue()
                        : 0.05f;
                return new HexStaffData(styleId, castDecayRate);
            }

            @Override
            @Nonnull
            public BsonValue encode(@Nonnull HexStaffData data, @Nonnull ExtraInfo extraInfo) {
                BsonDocument doc = new BsonDocument();
                doc.put("StyleId", new BsonString(data.styleId()));
                doc.put("CastDecayRate", new BsonDouble(data.castDecayRate()));
                return doc;
            }

            @Override
            @Nonnull
            public Schema toSchema(@Nonnull SchemaContext context) {
                return new ObjectSchema();
            }
        };
    }

    private static Codec<HexBookData> createHexBookCodec() {
        return new Codec<>() {
            @Override
            public HexBookData decode(@Nonnull BsonValue encoded, @Nonnull ExtraInfo extraInfo) {
                if (!(encoded instanceof BsonDocument doc)) {
                    return new HexBookData(10, "");
                }
                int maxCapacity = doc.containsKey("MaxCapacity")
                        ? doc.getInt32("MaxCapacity").getValue()
                        : 10;
                String bookId = doc.containsKey("BookId")
                        ? doc.getString("BookId").getValue()
                        : "";
                return new HexBookData(maxCapacity, bookId);
            }

            @Override
            @Nonnull
            public BsonValue encode(@Nonnull HexBookData data, @Nonnull ExtraInfo extraInfo) {
                BsonDocument doc = new BsonDocument();
                doc.put("Hexes", new BsonArray());             // Empty hex list
                doc.put("MaxCapacity", new BsonInt32(data.maxCapacity()));
                doc.put("BookId", new BsonString(data.bookId()));
                doc.put("HexNames", new BsonDocument());       // Empty map
                doc.put("HexColors", new BsonDocument());      // Empty map
                return doc;
            }

            @Override
            @Nonnull
            public Schema toSchema(@Nonnull SchemaContext context) {
                return new ObjectSchema();
            }
        };
    }

    // =========================================================================
    // INJECTION
    // =========================================================================

    /**
     * Injects Hexcode metadata onto an ItemStack if the base item is a Hexcode
     * staff, wand, or spellbook.
     *
     * <p>This must be called BEFORE {@code GearUtils.setGearData()} so the
     * hex metadata survives the ItemStack reconstruction (the 5-arg constructor
     * copies the full BsonDocument via {@code getMetadata()}).
     *
     * @param itemStack The base ItemStack (before RPG metadata)
     * @param baseItemId The original item ID for weapon type detection
     * @param rarity The RPG rarity (determines spellbook capacity)
     * @return ItemStack with hex metadata injected, or unchanged if not a hex item
     */
    @Nonnull
    public static ItemStack injectIfApplicable(
            @Nonnull ItemStack itemStack,
            @Nullable String baseItemId,
            @Nonnull GearRarity rarity) {

        if (baseItemId == null) {
            return itemStack;
        }

        WeaponType weaponType = WeaponType.fromItemIdOrUnknown(baseItemId);

        return switch (weaponType) {
            case STAFF, WAND -> injectHexStaff(itemStack, baseItemId);
            case SPELLBOOK -> injectHexBook(itemStack, baseItemId, rarity);
            default -> itemStack;
        };
    }

    /**
     * Injects HexStaff metadata for staffs and wands.
     */
    @Nonnull
    private static ItemStack injectHexStaff(@Nonnull ItemStack itemStack, @Nonnull String baseItemId) {
        HexStaffData data = HexStaffData.DEFAULT;
        ItemStack result = itemStack.withMetadata(KEY_HEX_STAFF, HEX_STAFF_CODEC, data);
        LOGGER.atFine().log("[HexMetadata] Injected HexStaff metadata for %s (style=%s, decay=%.3f)",
                baseItemId, data.styleId(), data.castDecayRate());
        return result;
    }

    /**
     * Injects HexBook metadata for spellbooks, with capacity scaled by rarity.
     */
    @Nonnull
    private static ItemStack injectHexBook(
            @Nonnull ItemStack itemStack,
            @Nonnull String baseItemId,
            @Nonnull GearRarity rarity) {

        int maxCapacity = getBookCapacityForRarity(rarity);
        HexBookData data = new HexBookData(maxCapacity, "");
        ItemStack result = itemStack.withMetadata(KEY_HEX_BOOK, HEX_BOOK_CODEC, data);
        LOGGER.atFine().log("[HexMetadata] Injected HexBook metadata for %s (capacity=%d, rarity=%s)",
                baseItemId, maxCapacity, rarity);
        return result;
    }

    // =========================================================================
    // CAPACITY SCALING
    // =========================================================================

    /**
     * Returns spellbook max capacity scaled by RPG rarity.
     *
     * <p>Higher rarity books can hold more hex spells:
     * <ul>
     *   <li>Common: 4</li>
     *   <li>Uncommon: 6</li>
     *   <li>Rare: 8</li>
     *   <li>Epic: 10</li>
     *   <li>Legendary: 12</li>
     *   <li>Mythic/Unique: 15</li>
     * </ul>
     */
    public static int getBookCapacityForRarity(@Nonnull GearRarity rarity) {
        return switch (rarity) {
            case COMMON -> 4;
            case UNCOMMON -> 6;
            case RARE -> 8;
            case EPIC -> 10;
            case LEGENDARY -> 12;
            case MYTHIC, UNIQUE -> 15;
        };
    }

    // =========================================================================
    // READ HELPERS (for tooltips)
    // =========================================================================

    /**
     * Reads HexStaff metadata from an ItemStack.
     *
     * @param itemStack The item to read from
     * @return The staff data, or null if not present
     */
    @Nullable
    public static HexStaffData readHexStaffData(@Nullable ItemStack itemStack) {
        if (itemStack == null) {
            return null;
        }
        return itemStack.getFromMetadataOrNull(KEY_HEX_STAFF, HEX_STAFF_CODEC);
    }

    /**
     * Reads HexBook metadata from an ItemStack.
     *
     * @param itemStack The item to read from
     * @return The book data, or null if not present
     */
    @Nullable
    public static HexBookData readHexBookData(@Nullable ItemStack itemStack) {
        if (itemStack == null) {
            return null;
        }
        return itemStack.getFromMetadataOrNull(KEY_HEX_BOOK, HEX_BOOK_CODEC);
    }

    private HexcodeMetadataInjector() {}
}
