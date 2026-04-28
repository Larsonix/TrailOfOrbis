package io.github.larsonix.trailoforbis.stones;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.ExtraInfo;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Data record for a stone item.
 *
 * <p>Stones are native Hytale items defined in
 * {@code Server/Item/Items/RPG/Stones/}. They do not require dynamic
 * registration or client sync — the asset pack provides names,
 * descriptions, quality colors, and stacking.
 *
 * <p>This record holds the stone type for metadata serialization and
 * provides convenience accessors that delegate to {@link StoneType}.
 *
 * @param stoneType The stone type defining behavior
 * @see StoneType
 */
public record StoneItemData(@Nonnull StoneType stoneType) {

    /**
     * Compact constructor with validation.
     */
    public StoneItemData {
        Objects.requireNonNull(stoneType, "stoneType cannot be null");
    }

    /**
     * Creates a StoneItemData for a stone type.
     *
     * @param stoneType The stone type
     * @return New StoneItemData
     */
    @Nonnull
    public static StoneItemData of(@Nonnull StoneType stoneType) {
        return new StoneItemData(stoneType);
    }

    // =========================================================================
    // ACCESSORS (delegate to StoneType)
    // =========================================================================

    /**
     * Gets the native Hytale item ID for this stone.
     *
     * @return The item ID matching the JSON asset filename
     */
    @Nonnull
    public String getNativeItemId() {
        return stoneType.getNativeItemId();
    }

    /**
     * Gets the display name of this stone.
     *
     * @return Human-readable name
     */
    @Nonnull
    public String getDisplayName() {
        return stoneType.getDisplayName();
    }

    /**
     * Gets the description of this stone.
     *
     * @return Description text
     */
    @Nonnull
    public String getDescription() {
        return stoneType.getDescription();
    }

    /**
     * Gets the rarity of this stone.
     *
     * @return The stone's rarity
     */
    @Nonnull
    public GearRarity getRarity() {
        return stoneType.getRarity();
    }

    // =========================================================================
    // CODEC
    // =========================================================================

    private static final String KEY_STONE_TYPE = "stoneType";

    /**
     * Codec for serialization to ItemStack metadata.
     *
     * <p>Only stores the stone type name — no instance ID needed for native items.
     */
    public static final Codec<StoneItemData> CODEC = new Codec<>() {
        @Override
        public StoneItemData decode(@Nonnull BsonValue encoded, @Nonnull ExtraInfo extraInfo) {
            if (!(encoded instanceof BsonDocument doc)) {
                throw new IllegalArgumentException("Expected BsonDocument");
            }

            String typeName = doc.getString(KEY_STONE_TYPE).getValue();
            StoneType stoneType = StoneType.valueOf(typeName);

            return new StoneItemData(stoneType);
        }

        @Override
        @Nonnull
        public BsonValue encode(@Nonnull StoneItemData data, @Nonnull ExtraInfo extraInfo) {
            BsonDocument doc = new BsonDocument();
            doc.put(KEY_STONE_TYPE, new BsonString(data.stoneType().name()));
            return doc;
        }

        @Override
        @Nonnull
        public com.hypixel.hytale.codec.schema.config.Schema toSchema(
                @Nonnull com.hypixel.hytale.codec.schema.SchemaContext context) {
            return new com.hypixel.hytale.codec.schema.config.ObjectSchema();
        }
    };

    @Override
    public String toString() {
        return "StoneItemData[" +
            "type=" + stoneType +
            ", rarity=" + stoneType.getRarity() +
            ", itemId=" + stoneType.getNativeItemId() +
            "]";
    }
}
