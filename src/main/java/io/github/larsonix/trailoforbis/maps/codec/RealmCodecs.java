package io.github.larsonix.trailoforbis.maps.codec;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.schema.SchemaContext;
import com.hypixel.hytale.codec.schema.config.ArraySchema;
import com.hypixel.hytale.codec.schema.config.Schema;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifier;

import org.bson.BsonArray;
import org.bson.BsonValue;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * BSON codecs for serializing realm data structures.
 *
 * <p>Provides codec utilities for RealmModifier lists and other realm data.
 */
public final class RealmCodecs {

    private RealmCodecs() {}

    /**
     * Codec for serializing a list of {@link RealmModifier}s.
     *
     * <p>Filters out nulls during decode — removed modifier types (e.g., DAMAGE_OVER_TIME)
     * return null from the element codec and are silently dropped.
     */
    public static final Codec<List<RealmModifier>> MODIFIER_LIST_CODEC = createNullFilteringListCodec(RealmModifier.CODEC);

    /**
     * Creates a codec that wraps an element codec to handle List&lt;T&gt;.
     *
     * @param elementCodec The codec for individual elements
     * @param <T> The element type
     * @return A codec for List&lt;T&gt;
     */
    public static <T> Codec<List<T>> createListCodec(Codec<T> elementCodec) {
        return new Codec<>() {
            @Override
            public List<T> decode(@Nonnull BsonValue encoded, @Nonnull ExtraInfo extraInfo) {
                if (!encoded.isArray()) {
                    throw new IllegalArgumentException(
                        "Expected BsonArray, got: " + encoded.getBsonType()
                    );
                }
                BsonArray array = encoded.asArray();
                List<T> result = new ArrayList<>(array.size());
                for (int i = 0; i < array.size(); i++) {
                    BsonValue element = array.get(i);
                    result.add(elementCodec.decode(element, extraInfo));
                }
                return result;
            }

            @Override
            @Nonnull
            public BsonValue encode(@Nonnull List<T> list, @Nonnull ExtraInfo extraInfo) {
                BsonArray array = new BsonArray();
                for (T element : list) {
                    array.add(elementCodec.encode(element, extraInfo));
                }
                return array;
            }

            @Override
            @Nonnull
            public Schema toSchema(@Nonnull SchemaContext context) {
                ArraySchema schema = new ArraySchema();
                schema.setItem(elementCodec.toSchema(context));
                return schema;
            }
        };
    }

    /**
     * Creates a list codec that filters out null elements during decode.
     *
     * <p>Used when the element codec may return null for removed/unknown values
     * (e.g., removed modifier types from old saved data).
     *
     * @param elementCodec The codec for individual elements (may return null)
     * @param <T> The element type
     * @return A codec for List&lt;T&gt; that skips null elements
     */
    public static <T> Codec<List<T>> createNullFilteringListCodec(Codec<T> elementCodec) {
        return new Codec<>() {
            @Override
            public List<T> decode(@Nonnull BsonValue encoded, @Nonnull ExtraInfo extraInfo) {
                if (!encoded.isArray()) {
                    throw new IllegalArgumentException(
                        "Expected BsonArray, got: " + encoded.getBsonType()
                    );
                }
                BsonArray array = encoded.asArray();
                List<T> result = new ArrayList<>(array.size());
                for (int i = 0; i < array.size(); i++) {
                    BsonValue element = array.get(i);
                    T decoded = elementCodec.decode(element, extraInfo);
                    if (decoded != null) {
                        result.add(decoded);
                    }
                }
                return result;
            }

            @Override
            @Nonnull
            public BsonValue encode(@Nonnull List<T> list, @Nonnull ExtraInfo extraInfo) {
                BsonArray array = new BsonArray();
                for (T element : list) {
                    array.add(elementCodec.encode(element, extraInfo));
                }
                return array;
            }

            @Override
            @Nonnull
            public Schema toSchema(@Nonnull SchemaContext context) {
                ArraySchema schema = new ArraySchema();
                schema.setItem(elementCodec.toSchema(context));
                return schema;
            }
        };
    }
}
