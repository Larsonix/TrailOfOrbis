package io.github.larsonix.trailoforbis.maps.codec;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.ExtraInfo;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifier;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifierType;
import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for RealmCodecs - 8 test cases.
 *
 * <p>Covers BSON list codec for RealmModifier serialization,
 * round-trip encoding/decoding, and error handling.
 */
class RealmCodecsTest {

    private ExtraInfo extraInfo;

    @BeforeEach
    void setUp() {
        extraInfo = mock(ExtraInfo.class);
    }

    // =========================================================================
    // MODIFIER LIST CODEC TESTS
    // =========================================================================

    @Nested
    @DisplayName("MODIFIER_LIST_CODEC")
    class ModifierListCodecTests {

        @Test
        @DisplayName("encode and decode round-trip preserves data")
        void modifierListCodec_encodeAndDecode_roundTrip() {
            List<RealmModifier> original = List.of(
                RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 40),
                RealmModifier.of(RealmModifierType.ITEM_QUANTITY, 25),
                new RealmModifier(RealmModifierType.EXPERIENCE_BONUS, 15, true) // locked
            );

            BsonValue encoded = RealmCodecs.MODIFIER_LIST_CODEC.encode(original, extraInfo);
            List<RealmModifier> decoded = RealmCodecs.MODIFIER_LIST_CODEC.decode(encoded, extraInfo);

            assertEquals(original.size(), decoded.size());
            for (int i = 0; i < original.size(); i++) {
                RealmModifier orig = original.get(i);
                RealmModifier dec = decoded.get(i);
                assertEquals(orig.type(), dec.type(), "Type should match at index " + i);
                assertEquals(orig.value(), dec.value(), "Value should match at index " + i);
                assertEquals(orig.locked(), dec.locked(), "Locked state should match at index " + i);
            }
        }

        @Test
        @DisplayName("empty list encodes and decodes correctly")
        void modifierListCodec_emptyList_handlesCorrectly() {
            List<RealmModifier> original = List.of();

            BsonValue encoded = RealmCodecs.MODIFIER_LIST_CODEC.encode(original, extraInfo);
            List<RealmModifier> decoded = RealmCodecs.MODIFIER_LIST_CODEC.decode(encoded, extraInfo);

            assertTrue(decoded.isEmpty());
            assertTrue(encoded.isArray());
            assertEquals(0, encoded.asArray().size());
        }

        @Test
        @DisplayName("decode throws on non-array BSON type")
        void modifierListCodec_invalidBsonType_throwsException() {
            BsonValue notAnArray = new BsonString("not an array");

            assertThrows(IllegalArgumentException.class, () ->
                RealmCodecs.MODIFIER_LIST_CODEC.decode(notAnArray, extraInfo)
            );
        }

        @Test
        @DisplayName("single modifier round-trip works")
        void modifierListCodec_singleModifier_roundTrip() {
            List<RealmModifier> original = List.of(
                RealmModifier.of(RealmModifierType.MONSTER_HEALTH, 50)
            );

            BsonValue encoded = RealmCodecs.MODIFIER_LIST_CODEC.encode(original, extraInfo);
            List<RealmModifier> decoded = RealmCodecs.MODIFIER_LIST_CODEC.decode(encoded, extraInfo);

            assertEquals(1, decoded.size());
            assertEquals(RealmModifierType.MONSTER_HEALTH, decoded.get(0).type());
            assertEquals(50, decoded.get(0).value());
            assertFalse(decoded.get(0).locked());
        }
    }

    // =========================================================================
    // GENERIC LIST CODEC TESTS
    // =========================================================================

    @Nested
    @DisplayName("createListCodec Generic")
    class GenericListCodecTests {

        @Test
        @DisplayName("generic list codec works with any element codec")
        void createListCodec_genericType_worksWithAnyCodec() {
            // Create a simple string codec for testing
            Codec<String> stringCodec = new Codec<>() {
                @Override
                public String decode(BsonValue encoded, ExtraInfo extraInfo) {
                    return encoded.asString().getValue();
                }

                @Override
                public BsonValue encode(String value, ExtraInfo extraInfo) {
                    return new BsonString(value);
                }

                @Override
                public com.hypixel.hytale.codec.schema.config.Schema toSchema(
                        com.hypixel.hytale.codec.schema.SchemaContext context) {
                    return new com.hypixel.hytale.codec.schema.config.StringSchema();
                }
            };

            Codec<List<String>> stringListCodec = RealmCodecs.createListCodec(stringCodec);
            List<String> original = List.of("hello", "world", "test");

            BsonValue encoded = stringListCodec.encode(original, extraInfo);
            List<String> decoded = stringListCodec.decode(encoded, extraInfo);

            assertEquals(original, decoded);
        }

        @Test
        @DisplayName("generic list codec preserves order")
        void createListCodec_preservesOrder() {
            Codec<List<RealmModifier>> codec = RealmCodecs.MODIFIER_LIST_CODEC;
            List<RealmModifier> original = List.of(
                RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 10),
                RealmModifier.of(RealmModifierType.MONSTER_HEALTH, 20),
                RealmModifier.of(RealmModifierType.EXTRA_MONSTERS, 30)
            );

            BsonValue encoded = codec.encode(original, extraInfo);
            List<RealmModifier> decoded = codec.decode(encoded, extraInfo);

            assertEquals(RealmModifierType.MONSTER_DAMAGE, decoded.get(0).type());
            assertEquals(RealmModifierType.MONSTER_HEALTH, decoded.get(1).type());
            assertEquals(RealmModifierType.EXTRA_MONSTERS, decoded.get(2).type());
        }

        @Test
        @DisplayName("encoded format is BsonArray")
        void createListCodec_encodedFormat_isBsonArray() {
            List<RealmModifier> original = List.of(
                RealmModifier.of(RealmModifierType.ITEM_RARITY, 25)
            );

            BsonValue encoded = RealmCodecs.MODIFIER_LIST_CODEC.encode(original, extraInfo);

            assertTrue(encoded.isArray(), "Encoded value should be a BsonArray");
            BsonArray array = encoded.asArray();
            assertEquals(1, array.size());
            assertTrue(array.get(0).isDocument(), "Array elements should be BsonDocuments");
        }

        @Test
        @DisplayName("toSchema returns ArraySchema")
        void createListCodec_toSchema_returnsArraySchema() {
            com.hypixel.hytale.codec.schema.SchemaContext context =
                mock(com.hypixel.hytale.codec.schema.SchemaContext.class);

            com.hypixel.hytale.codec.schema.config.Schema schema =
                RealmCodecs.MODIFIER_LIST_CODEC.toSchema(context);

            assertTrue(schema instanceof com.hypixel.hytale.codec.schema.config.ArraySchema,
                "Schema should be an ArraySchema");
        }
    }

    // =========================================================================
    // CODEC MIGRATION TESTS
    // =========================================================================

    @Nested
    @DisplayName("Codec Migration - Removed Modifiers")
    class CodecMigrationTests {

        @Test
        @DisplayName("removed modifier types should be silently filtered during decode")
        void removedModifierType_filteredDuringDecode() {
            // Simulate old saved data with a removed modifier type
            BsonArray array = new BsonArray();

            // Valid modifier
            BsonDocument validMod = new BsonDocument();
            validMod.put("type", new BsonString("MONSTER_DAMAGE"));
            validMod.put("value", new BsonInt32(40));
            validMod.put("locked", new BsonBoolean(false));
            array.add(validMod);

            // Removed modifier (DAMAGE_OVER_TIME no longer exists)
            BsonDocument removedMod = new BsonDocument();
            removedMod.put("type", new BsonString("DAMAGE_OVER_TIME"));
            removedMod.put("value", new BsonInt32(15));
            removedMod.put("locked", new BsonBoolean(false));
            array.add(removedMod);

            // Another valid modifier
            BsonDocument validMod2 = new BsonDocument();
            validMod2.put("type", new BsonString("ITEM_QUANTITY"));
            validMod2.put("value", new BsonInt32(25));
            validMod2.put("locked", new BsonBoolean(true));
            array.add(validMod2);

            // Decode should skip the removed modifier
            List<RealmModifier> decoded = RealmCodecs.MODIFIER_LIST_CODEC.decode(array, extraInfo);

            assertEquals(2, decoded.size(), "Should have 2 modifiers (removed one filtered)");
            assertEquals(RealmModifierType.MONSTER_DAMAGE, decoded.get(0).type());
            assertEquals(40, decoded.get(0).value());
            assertEquals(RealmModifierType.ITEM_QUANTITY, decoded.get(1).type());
            assertEquals(25, decoded.get(1).value());
            assertTrue(decoded.get(1).locked());
        }

        @Test
        @DisplayName("all removed modifiers should be filtered from old data")
        void allRemovedModifiers_filteredFromOldData() {
            String[] removedTypes = {
                "DAMAGE_OVER_TIME", "REDUCED_VISIBILITY", "TEMPORAL_CHAINS",
                "REFLECT_DAMAGE", "LIFE_LEECH", "ENRAGE_ON_LOW",
                "CANNOT_BE_SLOWED", "AVOID_DEATH"
            };

            BsonArray array = new BsonArray();
            for (String type : removedTypes) {
                BsonDocument doc = new BsonDocument();
                doc.put("type", new BsonString(type));
                doc.put("value", new BsonInt32(10));
                doc.put("locked", new BsonBoolean(false));
                array.add(doc);
            }

            List<RealmModifier> decoded = RealmCodecs.MODIFIER_LIST_CODEC.decode(array, extraInfo);

            assertTrue(decoded.isEmpty(), "All removed modifiers should be filtered");
        }
    }
}
