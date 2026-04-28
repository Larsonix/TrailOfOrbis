package io.github.larsonix.trailoforbis.gems.codec;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.schema.SchemaContext;
import com.hypixel.hytale.codec.schema.config.ArraySchema;
import com.hypixel.hytale.codec.schema.config.ObjectSchema;
import com.hypixel.hytale.codec.schema.config.Schema;
import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.gems.model.GemData;
import io.github.larsonix.trailoforbis.gems.model.GemType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.BsonString;
import org.bson.BsonValue;

public final class GemCodecs {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    public static final Codec<GemData> GEM_DATA_CODEC = new Codec<GemData>() {

        public GemData decode(@Nonnull BsonValue encoded, ExtraInfo extraInfo) {
            if (!encoded.isDocument()) {
                throw new IllegalArgumentException("Expected BsonDocument for GemData, got: " + encoded.getBsonType());
            }
            BsonDocument doc = encoded.asDocument();
            String gemId = GemCodecs.getStringOrThrow(doc, "gemId");
            int level = GemCodecs.getIntOrDefault(doc, "level", 1);
            int quality = GemCodecs.getIntOrDefault(doc, "quality", 1);
            long xp = GemCodecs.getLongOrDefault(doc, "xp", 0L);
            GemType gemType = GemCodecs.parseGemType(doc, "gemType");
            return new GemData(gemId, level, quality, xp, gemType);
        }

        public BsonValue encode(@Nonnull GemData data, ExtraInfo extraInfo) {
            BsonDocument doc = new BsonDocument();
            doc.put("gemId", new BsonString(data.gemId()));
            doc.put("level", new BsonInt32(data.level()));
            doc.put("quality", new BsonInt32(data.quality()));
            doc.put("xp", new BsonInt64(data.xp()));
            doc.put("gemType", new BsonString(data.gemType().name()));
            return doc;
        }

        public Schema toSchema(@Nonnull SchemaContext context) {
            return new ObjectSchema();
        }
    };
    public static final Codec<List<GemData>> GEM_LIST_CODEC = GemCodecs.createListCodec(GEM_DATA_CODEC);

    private GemCodecs() {
        throw new UnsupportedOperationException("Utility class");
    }

    private static <T> Codec<List<T>> createListCodec(final Codec<T> elementCodec) {
        return new Codec<List<T>>() {

            public List<T> decode(@Nonnull BsonValue encoded, ExtraInfo extraInfo) {
                if (!encoded.isArray()) {
                    throw new IllegalArgumentException("Expected BsonArray, got: " + encoded.getBsonType());
                }
                BsonArray array = encoded.asArray();
                ArrayList<T> result = new ArrayList<>(array.size());
                for (int i = 0; i < array.size(); ++i) {
                    BsonValue element = array.get(i);
                    result.add(elementCodec.decode(element, extraInfo));
                }
                return result;
            }

            @Nonnull
            public BsonValue encode(@Nonnull List<T> list, ExtraInfo extraInfo) {
                BsonArray array = new BsonArray();
                for (T element : list) {
                    array.add(elementCodec.encode(element, extraInfo));
                }
                return array;
            }

            public Schema toSchema(@Nonnull SchemaContext context) {
                ArraySchema schema = new ArraySchema();
                schema.setItem(elementCodec.toSchema(context));
                return schema;
            }
        };
    }

    @Nonnull
    public static Optional<GemData> safeDecodeGemData(BsonValue value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(GEM_DATA_CODEC.decode(value, null));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to decode GemData from BSON");
            return Optional.empty();
        }
    }

    private static String getStringOrThrow(BsonDocument doc, String key) {
        if (!doc.containsKey(key)) {
            throw new IllegalArgumentException("Missing required field: " + key);
        }
        BsonValue value = doc.get(key);
        if (!value.isString()) {
            throw new IllegalArgumentException("Field '" + key + "' must be string, got: " + value.getBsonType());
        }
        return value.asString().getValue();
    }

    private static int getIntOrDefault(BsonDocument doc, String key, int defaultValue) {
        if (!doc.containsKey(key)) {
            return defaultValue;
        }
        BsonValue value = doc.get(key);
        if (value.isInt32()) {
            return value.asInt32().getValue();
        }
        if (value.isInt64()) {
            return (int) value.asInt64().getValue();
        }
        if (value.isDouble()) {
            return (int) value.asDouble().getValue();
        }
        return defaultValue;
    }

    private static long getLongOrDefault(BsonDocument doc, String key, long defaultValue) {
        if (!doc.containsKey(key)) {
            return defaultValue;
        }
        BsonValue value = doc.get(key);
        if (value.isInt64()) {
            return value.asInt64().getValue();
        }
        if (value.isInt32()) {
            return value.asInt32().getValue();
        }
        if (value.isDouble()) {
            return (long) value.asDouble().getValue();
        }
        return defaultValue;
    }

    private static GemType parseGemType(BsonDocument doc, String key) {
        if (!doc.containsKey(key)) {
            return GemType.ACTIVE;
        }
        String typeStr = doc.get(key).asString().getValue();
        try {
            return GemType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            LOGGER.atWarning().log("Unknown gem type '%s', defaulting to ACTIVE", typeStr);
            return GemType.ACTIVE;
        }
    }
}
