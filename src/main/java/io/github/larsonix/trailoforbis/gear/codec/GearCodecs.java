package io.github.larsonix.trailoforbis.gear.codec;

import io.github.larsonix.trailoforbis.gear.instance.GearInstanceId;
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
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.schema.SchemaContext;
import com.hypixel.hytale.codec.schema.config.Schema;
import com.hypixel.hytale.codec.schema.config.ArraySchema;
import com.hypixel.hytale.codec.schema.config.ObjectSchema;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.BsonString;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonBoolean;
import org.bson.BsonArray;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nonnull;

/**
 * BSON codecs for serializing gear data structures.
 *
 * <p>These codecs allow storing {@link GearModifier} and {@link GearData}
 * in Hytale's ItemStack metadata system.
 *
 * <p>Usage:
 * <pre>{@code
 * // Write a modifier to metadata
 * itemStack = itemStack.withMetadata("RPG:Prefixes",
 *     GearCodecs.MODIFIER_LIST_CODEC, prefixList);
 *
 * // Read a modifier from metadata
 * List<GearModifier> prefixes = itemStack.getFromMetadataOrNull(
 *     "RPG:Prefixes", GearCodecs.MODIFIER_LIST_CODEC);
 * }</pre>
 */
public final class GearCodecs {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // =========================================================================
    // SHARED DECODE HELPERS
    // =========================================================================

    private static String getStringOrThrow(BsonDocument doc, String key) {
        if (!doc.containsKey(key)) {
            throw new IllegalArgumentException("Missing required field: " + key);
        }
        BsonValue value = doc.get(key);
        if (!value.isString()) {
            throw new IllegalArgumentException("Field " + key + " must be string, got: " + value.getBsonType());
        }
        return value.asString().getValue();
    }

    private static double getDoubleOrThrow(BsonDocument doc, String key) {
        if (!doc.containsKey(key)) {
            throw new IllegalArgumentException("Missing required field: " + key);
        }
        BsonValue value = doc.get(key);
        if (value.isDouble()) {
            return value.asDouble().getValue();
        } else if (value.isInt32()) {
            return value.asInt32().getValue();
        } else if (value.isInt64()) {
            return value.asInt64().getValue();
        }
        throw new IllegalArgumentException("Field " + key + " must be numeric, got: " + value.getBsonType());
    }

    private static int getIntOrThrow(BsonDocument doc, String key) {
        if (!doc.containsKey(key)) {
            throw new IllegalArgumentException("Missing required field: " + key);
        }
        BsonValue value = doc.get(key);
        if (value.isInt32()) {
            return value.asInt32().getValue();
        } else if (value.isInt64()) {
            return (int) value.asInt64().getValue();
        }
        throw new IllegalArgumentException("Field " + key + " must be integer, got: " + value.getBsonType());
    }

    // =========================================================================
    // MODIFIER CODEC
    // =========================================================================

    /**
     * Codec for serializing a single {@link GearModifier}.
     *
     * <p>BSON structure:
     * <pre>
     * {
     *   "id": "sharp",
     *   "displayName": "Sharp",
     *   "type": "PREFIX",
     *   "statId": "physical_damage",
     *   "statType": "flat",
     *   "value": 15.5,
     *   "locked": false  // optional, defaults to false for backwards compatibility
     * }
     * </pre>
     */
    public static final Codec<GearModifier> MODIFIER_CODEC = createModifierCodec();

    private static Codec<GearModifier> createModifierCodec() {
        return new Codec<>() {
            // Keys used in BSON document
            private static final String KEY_ID = "id";
            private static final String KEY_DISPLAY_NAME = "displayName";
            private static final String KEY_TYPE = "type";
            private static final String KEY_STAT_ID = "statId";
            private static final String KEY_STAT_TYPE = "statType";
            private static final String KEY_VALUE = "value";
            private static final String KEY_LOCKED = "locked";

            @Override
            public GearModifier decode(@Nonnull BsonValue encoded, @Nonnull ExtraInfo extraInfo) {
                if (!(encoded instanceof BsonDocument doc)) {
                    throw new IllegalArgumentException(
                        "Expected BsonDocument, got: " + (encoded == null ? "null" : encoded.getClass())
                    );
                }

                try {
                    String id = getStringOrThrow(doc, KEY_ID);
                    String displayName = getStringOrThrow(doc, KEY_DISPLAY_NAME);
                    String typeStr = getStringOrThrow(doc, KEY_TYPE);
                    String statId = getStringOrThrow(doc, KEY_STAT_ID);
                    String statType = getStringOrThrow(doc, KEY_STAT_TYPE);
                    double value = getDoubleOrThrow(doc, KEY_VALUE);

                    // Backwards compatible: default to false if missing
                    boolean locked = doc.containsKey(KEY_LOCKED) && doc.getBoolean(KEY_LOCKED).getValue();

                    ModifierType type = ModifierType.valueOf(typeStr);

                    return new GearModifier(id, displayName, type, statId, statType, value, locked);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Failed to decode GearModifier: " + e.getMessage(), e);
                }
            }

            @Override
            @Nonnull
            public BsonValue encode(@Nonnull GearModifier modifier, @Nonnull ExtraInfo extraInfo) {
                BsonDocument doc = new BsonDocument();
                doc.put(KEY_ID, new BsonString(modifier.id()));
                doc.put(KEY_DISPLAY_NAME, new BsonString(modifier.displayName()));
                doc.put(KEY_TYPE, new BsonString(modifier.type().name()));
                doc.put(KEY_STAT_ID, new BsonString(modifier.statId()));
                doc.put(KEY_STAT_TYPE, new BsonString(modifier.statType()));
                doc.put(KEY_VALUE, new BsonDouble(modifier.value()));
                doc.put(KEY_LOCKED, new BsonBoolean(modifier.locked()));
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
    // WEAPON IMPLICIT CODEC
    // =========================================================================

    /**
     * Codec for serializing a {@link WeaponImplicit}.
     *
     * <p>BSON structure:
     * <pre>
     * {
     *   "damageType": "physical_damage",
     *   "minValue": 153.0,
     *   "maxValue": 198.0,
     *   "rolledValue": 175.5
     * }
     * </pre>
     */
    public static final Codec<WeaponImplicit> IMPLICIT_CODEC = createImplicitCodec();

    private static Codec<WeaponImplicit> createImplicitCodec() {
        return new Codec<>() {
            private static final String KEY_DAMAGE_TYPE = "damageType";
            private static final String KEY_MIN_VALUE = "minValue";
            private static final String KEY_MAX_VALUE = "maxValue";
            private static final String KEY_ROLLED_VALUE = "rolledValue";

            @Override
            public WeaponImplicit decode(@Nonnull BsonValue encoded, @Nonnull ExtraInfo extraInfo) {
                if (!(encoded instanceof BsonDocument doc)) {
                    throw new IllegalArgumentException(
                        "Expected BsonDocument, got: " + (encoded == null ? "null" : encoded.getClass())
                    );
                }

                try {
                    String damageType = getStringOrThrow(doc, KEY_DAMAGE_TYPE);
                    double minValue = getDoubleOrThrow(doc, KEY_MIN_VALUE);
                    double maxValue = getDoubleOrThrow(doc, KEY_MAX_VALUE);
                    double rolledValue = getDoubleOrThrow(doc, KEY_ROLLED_VALUE);

                    return WeaponImplicit.of(damageType, minValue, maxValue, rolledValue);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Failed to decode WeaponImplicit: " + e.getMessage(), e);
                }
            }

            @Override
            @Nonnull
            public BsonValue encode(@Nonnull WeaponImplicit implicit, @Nonnull ExtraInfo extraInfo) {
                BsonDocument doc = new BsonDocument();
                doc.put(KEY_DAMAGE_TYPE, new BsonString(implicit.damageType()));
                doc.put(KEY_MIN_VALUE, new BsonDouble(implicit.minValue()));
                doc.put(KEY_MAX_VALUE, new BsonDouble(implicit.maxValue()));
                doc.put(KEY_ROLLED_VALUE, new BsonDouble(implicit.rolledValue()));
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
    // ARMOR IMPLICIT CODEC
    // =========================================================================

    /**
     * Codec for serializing an {@link ArmorImplicit}.
     *
     * <p>BSON structure:
     * <pre>
     * {
     *   "defenseType": "armor",
     *   "minValue": 72.0,
     *   "maxValue": 98.0,
     *   "rolledValue": 85.0
     * }
     * </pre>
     */
    public static final Codec<ArmorImplicit> ARMOR_IMPLICIT_CODEC = createArmorImplicitCodec();

    private static Codec<ArmorImplicit> createArmorImplicitCodec() {
        return new Codec<>() {
            private static final String KEY_DEFENSE_TYPE = "defenseType";
            private static final String KEY_MIN_VALUE = "minValue";
            private static final String KEY_MAX_VALUE = "maxValue";
            private static final String KEY_ROLLED_VALUE = "rolledValue";

            @Override
            public ArmorImplicit decode(@Nonnull BsonValue encoded, @Nonnull ExtraInfo extraInfo) {
                if (!(encoded instanceof BsonDocument doc)) {
                    throw new IllegalArgumentException(
                        "Expected BsonDocument, got: " + (encoded == null ? "null" : encoded.getClass())
                    );
                }

                try {
                    String defenseType = getStringOrThrow(doc, KEY_DEFENSE_TYPE);
                    double minValue = getDoubleOrThrow(doc, KEY_MIN_VALUE);
                    double maxValue = getDoubleOrThrow(doc, KEY_MAX_VALUE);
                    double rolledValue = getDoubleOrThrow(doc, KEY_ROLLED_VALUE);

                    return ArmorImplicit.of(defenseType, minValue, maxValue, rolledValue);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Failed to decode ArmorImplicit: " + e.getMessage(), e);
                }
            }

            @Override
            @Nonnull
            public BsonValue encode(@Nonnull ArmorImplicit implicit, @Nonnull ExtraInfo extraInfo) {
                BsonDocument doc = new BsonDocument();
                doc.put(KEY_DEFENSE_TYPE, new BsonString(implicit.defenseType()));
                doc.put(KEY_MIN_VALUE, new BsonDouble(implicit.minValue()));
                doc.put(KEY_MAX_VALUE, new BsonDouble(implicit.maxValue()));
                doc.put(KEY_ROLLED_VALUE, new BsonDouble(implicit.rolledValue()));
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
    // MODIFIER LIST CODEC
    // =========================================================================

    /**
     * Codec for serializing a list of {@link GearModifier}s.
     *
     * <p>Encodes to a BsonArray of modifier documents.
     */
    public static final Codec<List<GearModifier>> MODIFIER_LIST_CODEC = createListCodec(MODIFIER_CODEC);

    /**
     * Creates a codec that wraps an element codec to handle List<T>.
     *
     * @param elementCodec The codec for individual elements
     * @param <T> The element type
     * @return A codec for List<T>
     */
    private static <T> Codec<List<T>> createListCodec(Codec<T> elementCodec) {
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

    // =========================================================================
    // GEAR DATA CODEC
    // =========================================================================

    /**
     * Codec for serializing complete {@link GearData}.
     *
     * <p>BSON structure:
     * <pre>
     * {
     *   "instanceId": "1706123456789_42",  // optional, for backwards compatibility
     *   "level": 50,
     *   "rarity": "EPIC",
     *   "quality": 75,
     *   "prefixes": [...],
     *   "suffixes": [...],
     *   "corrupted": false,  // optional, defaults to false for backwards compatibility
     *   "implicit": {...},   // optional, null for non-weapons or legacy items
     *   "armorImplicit": {...}  // optional, null for non-armor or legacy items
     * }
     * </pre>
     *
     * <p><b>Note:</b> This codec is provided for completeness but typically
     * you should use {@link io.github.larsonix.trailoforbis.gear.util.GearUtils} which writes fields individually.
     */
    public static final Codec<GearData> GEAR_DATA_CODEC = createGearDataCodec();

    private static Codec<GearData> createGearDataCodec() {
        return new Codec<>() {
            private static final String KEY_INSTANCE_ID = "instanceId";
            private static final String KEY_LEVEL = "level";
            private static final String KEY_RARITY = "rarity";
            private static final String KEY_QUALITY = "quality";
            private static final String KEY_PREFIXES = "prefixes";
            private static final String KEY_SUFFIXES = "suffixes";
            private static final String KEY_CORRUPTED = "corrupted";
            private static final String KEY_IMPLICIT = "implicit";
            private static final String KEY_ARMOR_IMPLICIT = "armorImplicit";
            private static final String KEY_BASE_ITEM_ID = "baseId";

            @Override
            public GearData decode(@Nonnull BsonValue encoded, @Nonnull ExtraInfo extraInfo) {
                if (!(encoded instanceof BsonDocument doc)) {
                    throw new IllegalArgumentException(
                        "Expected BsonDocument, got: " + (encoded == null ? "null" : encoded.getClass())
                    );
                }

                try {
                    // Read instanceId (optional for backwards compatibility)
                    GearInstanceId instanceId = null;
                    if (doc.containsKey(KEY_INSTANCE_ID)) {
                        BsonValue instanceIdValue = doc.get(KEY_INSTANCE_ID);
                        if (instanceIdValue.isString()) {
                            instanceId = GearInstanceId.tryFromCompactString(
                                instanceIdValue.asString().getValue()
                            );
                        }
                    }

                    int level = getIntOrThrow(doc, KEY_LEVEL);
                    String rarityStr = getStringOrThrow(doc, KEY_RARITY);
                    int quality = getIntOrThrow(doc, KEY_QUALITY);

                    GearRarity rarity = GearRarity.fromString(rarityStr);

                    List<GearModifier> prefixes = decodeModifierList(doc, KEY_PREFIXES, extraInfo);
                    List<GearModifier> suffixes = decodeModifierList(doc, KEY_SUFFIXES, extraInfo);

                    // Backwards compatible: default to false if missing
                    boolean corrupted = doc.containsKey(KEY_CORRUPTED) && doc.getBoolean(KEY_CORRUPTED).getValue();

                    // Backwards compatible: default to null if missing
                    WeaponImplicit implicit = null;
                    if (doc.containsKey(KEY_IMPLICIT) && !doc.get(KEY_IMPLICIT).isNull()) {
                        implicit = IMPLICIT_CODEC.decode(doc.get(KEY_IMPLICIT), extraInfo);
                    }

                    // Backwards compatible: default to null if missing
                    ArmorImplicit armorImplicit = null;
                    if (doc.containsKey(KEY_ARMOR_IMPLICIT) && !doc.get(KEY_ARMOR_IMPLICIT).isNull()) {
                        armorImplicit = ARMOR_IMPLICIT_CODEC.decode(doc.get(KEY_ARMOR_IMPLICIT), extraInfo);
                    }

                    // Backwards compatible: default to null if missing
                    String baseItemId = null;
                    if (doc.containsKey(KEY_BASE_ITEM_ID)) {
                        BsonValue baseIdValue = doc.get(KEY_BASE_ITEM_ID);
                        if (baseIdValue.isString()) {
                            baseItemId = baseIdValue.asString().getValue();
                        }
                    }

                    // Decode gem data (backwards compatible: defaults to null/empty/0)
                    GemData activeGem = null;
                    if (doc.containsKey("activeGem") && !doc.get("activeGem").isNull()) {
                        activeGem = GemCodecs.GEM_DATA_CODEC.decode(doc.get("activeGem"), extraInfo);
                    }
                    List<GemData> supportGems = List.of();
                    if (doc.containsKey("supportGems") && !doc.get("supportGems").isNull()) {
                        supportGems = GemCodecs.GEM_LIST_CODEC.decode(doc.get("supportGems"), extraInfo);
                    }
                    int supportSlotCount = 0;
                    if (doc.containsKey("supportSlotCount") && doc.get("supportSlotCount").isInt32()) {
                        supportSlotCount = doc.get("supportSlotCount").asInt32().getValue();
                    }

                    return new GearData(instanceId, level, rarity, quality, prefixes, suffixes, corrupted, implicit, armorImplicit, baseItemId, activeGem, supportGems, supportSlotCount);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Failed to decode GearData: " + e.getMessage(), e);
                }
            }

            @Override
            @Nonnull
            public BsonValue encode(@Nonnull GearData data, @Nonnull ExtraInfo extraInfo) {
                BsonDocument doc = new BsonDocument();

                // Write instanceId if present
                if (data.instanceId() != null) {
                    doc.put(KEY_INSTANCE_ID, new BsonString(data.instanceId().toCompactString()));
                }

                doc.put(KEY_LEVEL, new BsonInt32(data.level()));
                doc.put(KEY_RARITY, new BsonString(data.rarity().name()));
                doc.put(KEY_QUALITY, new BsonInt32(data.quality()));
                doc.put(KEY_PREFIXES, MODIFIER_LIST_CODEC.encode(data.prefixes(), extraInfo));
                doc.put(KEY_SUFFIXES, MODIFIER_LIST_CODEC.encode(data.suffixes(), extraInfo));
                doc.put(KEY_CORRUPTED, new BsonBoolean(data.corrupted()));

                // Write implicit if present
                if (data.implicit() != null) {
                    doc.put(KEY_IMPLICIT, IMPLICIT_CODEC.encode(data.implicit(), extraInfo));
                }

                // Write armor implicit if present
                if (data.armorImplicit() != null) {
                    doc.put(KEY_ARMOR_IMPLICIT, ARMOR_IMPLICIT_CODEC.encode(data.armorImplicit(), extraInfo));
                }

                // Write baseItemId if present
                if (data.baseItemId() != null) {
                    doc.put(KEY_BASE_ITEM_ID, new BsonString(data.baseItemId()));
                }

                return doc;
            }

            @Override
            @Nonnull
            public Schema toSchema(@Nonnull SchemaContext context) {
                return new ObjectSchema();
            }

            private List<GearModifier> decodeModifierList(BsonDocument doc, String key, ExtraInfo extraInfo) {
                if (!doc.containsKey(key)) {
                    return new ArrayList<>();
                }
                BsonValue value = doc.get(key);
                if (!value.isArray()) {
                    LOGGER.atWarning().log("Field %s expected array, got %s - returning empty list",
                        key, value.getBsonType());
                    return new ArrayList<>();
                }
                return MODIFIER_LIST_CODEC.decode(value, extraInfo);
            }
        };
    }

    // =========================================================================
    // UTILITY METHODS
    // =========================================================================

    /**
     * Safely decode a GearModifier, returning Optional.empty() on failure.
     *
     * @param encoded The encoded BSON data (may be null)
     * @return The decoded modifier, or empty if null or decoding fails
     */
    public static Optional<GearModifier> safeDecodeModifier(BsonValue encoded) {
        if (encoded == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(MODIFIER_CODEC.decode(encoded, null));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to decode GearModifier");
            return Optional.empty();
        }
    }

    /**
     * Safely decode GearData, returning Optional.empty() on failure.
     *
     * @param encoded The encoded BSON data (may be null)
     * @return The decoded gear data, or empty if null or decoding fails
     */
    public static Optional<GearData> safeDecodeGearData(BsonValue encoded) {
        if (encoded == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(GEAR_DATA_CODEC.decode(encoded, null));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to decode GearData");
            return Optional.empty();
        }
    }

    /**
     * Safely decode an ArmorImplicit, returning Optional.empty() on failure.
     *
     * @param encoded The encoded BSON data (may be null)
     * @return The decoded armor implicit, or empty if null or decoding fails
     */
    public static Optional<ArmorImplicit> safeDecodeArmorImplicit(BsonValue encoded) {
        if (encoded == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(ARMOR_IMPLICIT_CODEC.decode(encoded, null));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to decode ArmorImplicit");
            return Optional.empty();
        }
    }

    /**
     * Safely decode a WeaponImplicit, returning Optional.empty() on failure.
     *
     * @param encoded The encoded BSON data (may be null)
     * @return The decoded implicit, or empty if null or decoding fails
     */
    public static Optional<WeaponImplicit> safeDecodeImplicit(BsonValue encoded) {
        if (encoded == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(IMPLICIT_CODEC.decode(encoded, null));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to decode WeaponImplicit");
            return Optional.empty();
        }
    }

    // Prevent instantiation
    private GearCodecs() {}
}
