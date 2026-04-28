package io.github.larsonix.trailoforbis.gear.codec;

import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.GearModifier;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.gear.model.ModifierType;

import com.hypixel.hytale.codec.EmptyExtraInfo;
import com.hypixel.hytale.codec.ExtraInfo;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.BsonString;
import org.bson.BsonValue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GearCodecs class.
 * Tests BSON codec encoding/decoding for gear data structures.
 */
class GearCodecsTest {

    private static final ExtraInfo EXTRA_INFO = EmptyExtraInfo.EMPTY;

    // =========================================================================
    // MODIFIER_CODEC - encode
    // =========================================================================

    @Nested
    @DisplayName("MODIFIER_CODEC encode")
    class ModifierCodecEncode {

        @Test
        @DisplayName("encode produces valid BsonDocument")
        void modifierCodec_encode_producesValidBson() {
            GearModifier modifier = GearModifier.of(
                "sharp", "Sharp", ModifierType.PREFIX,
                "physical_damage", GearModifier.STAT_TYPE_FLAT, 10.0
            );

            BsonValue encoded = GearCodecs.MODIFIER_CODEC.encode(modifier, EXTRA_INFO);

            assertNotNull(encoded);
            assertTrue(encoded instanceof BsonDocument);
        }

        @Test
        @DisplayName("encode includes all fields")
        void modifierCodec_encode_includesAllFields() {
            GearModifier modifier = GearModifier.of(
                "sharp", "Sharp", ModifierType.PREFIX,
                "physical_damage", GearModifier.STAT_TYPE_FLAT, 15.5
            );

            BsonDocument doc = (BsonDocument) GearCodecs.MODIFIER_CODEC.encode(modifier, EXTRA_INFO);

            assertTrue(doc.containsKey("id"));
            assertTrue(doc.containsKey("displayName"));
            assertTrue(doc.containsKey("type"));
            assertTrue(doc.containsKey("statId"));
            assertTrue(doc.containsKey("statType"));
            assertTrue(doc.containsKey("value"));

            assertEquals("sharp", doc.getString("id").getValue());
            assertEquals("Sharp", doc.getString("displayName").getValue());
            assertEquals("PREFIX", doc.getString("type").getValue());
            assertEquals("physical_damage", doc.getString("statId").getValue());
            assertEquals(GearModifier.STAT_TYPE_FLAT, doc.getString("statType").getValue());
            assertEquals(15.5, doc.getDouble("value").getValue());
        }

        @Test
        @DisplayName("encode handles negative value")
        void modifierCodec_encode_handlesNegativeValue() {
            GearModifier modifier = GearModifier.of(
                "cursed", "Cursed", ModifierType.PREFIX,
                "luck", GearModifier.STAT_TYPE_FLAT, -5.0
            );

            BsonDocument doc = (BsonDocument) GearCodecs.MODIFIER_CODEC.encode(modifier, EXTRA_INFO);

            assertEquals(-5.0, doc.getDouble("value").getValue());
        }

        @Test
        @DisplayName("encode handles decimal value")
        void modifierCodec_encode_handlesDecimalValue() {
            GearModifier modifier = GearModifier.of(
                "sharp", "Sharp", ModifierType.PREFIX,
                "physical_damage", GearModifier.STAT_TYPE_PERCENT, 12.345
            );

            BsonDocument doc = (BsonDocument) GearCodecs.MODIFIER_CODEC.encode(modifier, EXTRA_INFO);

            assertEquals(12.345, doc.getDouble("value").getValue(), 0.0001);
        }
    }

    // =========================================================================
    // MODIFIER_CODEC - decode
    // =========================================================================

    @Nested
    @DisplayName("MODIFIER_CODEC decode")
    class ModifierCodecDecode {

        @Test
        @DisplayName("decode valid document succeeds")
        void modifierCodec_decode_validDocument_succeeds() {
            BsonDocument doc = new BsonDocument()
                .append("id", new BsonString("sharp"))
                .append("displayName", new BsonString("Sharp"))
                .append("type", new BsonString("PREFIX"))
                .append("statId", new BsonString("physical_damage"))
                .append("statType", new BsonString(GearModifier.STAT_TYPE_FLAT))
                .append("value", new BsonDouble(10.0));

            GearModifier modifier = GearCodecs.MODIFIER_CODEC.decode(doc, EXTRA_INFO);

            assertEquals("sharp", modifier.id());
            assertEquals("Sharp", modifier.displayName());
            assertEquals(ModifierType.PREFIX, modifier.type());
            assertEquals("physical_damage", modifier.statId());
            assertEquals(GearModifier.STAT_TYPE_FLAT, modifier.statType());
            assertEquals(10.0, modifier.value());
        }

        @Test
        @DisplayName("decode missing field throws exception")
        void modifierCodec_decode_missingField_throwsException() {
            BsonDocument doc = new BsonDocument()
                .append("id", new BsonString("sharp"))
                .append("displayName", new BsonString("Sharp"));
                // Missing type, statId, statType, value

            assertThrows(IllegalArgumentException.class, () ->
                GearCodecs.MODIFIER_CODEC.decode(doc, EXTRA_INFO)
            );
        }

        @Test
        @DisplayName("decode wrong field type throws exception")
        void modifierCodec_decode_wrongFieldType_throwsException() {
            BsonDocument doc = new BsonDocument()
                .append("id", new BsonInt32(123)) // Should be string
                .append("displayName", new BsonString("Sharp"))
                .append("type", new BsonString("PREFIX"))
                .append("statId", new BsonString("physical_damage"))
                .append("statType", new BsonString(GearModifier.STAT_TYPE_FLAT))
                .append("value", new BsonDouble(10.0));

            assertThrows(IllegalArgumentException.class, () ->
                GearCodecs.MODIFIER_CODEC.decode(doc, EXTRA_INFO)
            );
        }

        @Test
        @DisplayName("decode null document throws exception")
        void modifierCodec_decode_nullDocument_throwsException() {
            assertThrows(IllegalArgumentException.class, () ->
                GearCodecs.MODIFIER_CODEC.decode(null, EXTRA_INFO)
            );
        }

        @Test
        @DisplayName("decode unknown modifier type throws exception")
        void modifierCodec_decode_unknownModifierType_throwsException() {
            BsonDocument doc = new BsonDocument()
                .append("id", new BsonString("sharp"))
                .append("displayName", new BsonString("Sharp"))
                .append("type", new BsonString("INVALID_TYPE"))
                .append("statId", new BsonString("physical_damage"))
                .append("statType", new BsonString(GearModifier.STAT_TYPE_FLAT))
                .append("value", new BsonDouble(10.0));

            assertThrows(IllegalArgumentException.class, () ->
                GearCodecs.MODIFIER_CODEC.decode(doc, EXTRA_INFO)
            );
        }
    }

    // =========================================================================
    // MODIFIER_CODEC - round-trip
    // =========================================================================

    @Nested
    @DisplayName("MODIFIER_CODEC round-trip")
    class ModifierCodecRoundTrip {

        @Test
        @DisplayName("round-trip prefix modifier")
        void modifierCodec_roundTrip_prefixModifier() {
            GearModifier original = GearModifier.of(
                "sharp", "Sharp", ModifierType.PREFIX,
                "physical_damage", GearModifier.STAT_TYPE_FLAT, 10.0
            );

            BsonValue encoded = GearCodecs.MODIFIER_CODEC.encode(original, EXTRA_INFO);
            GearModifier decoded = GearCodecs.MODIFIER_CODEC.decode(encoded, EXTRA_INFO);

            assertEquals(original, decoded);
        }

        @Test
        @DisplayName("round-trip suffix modifier")
        void modifierCodec_roundTrip_suffixModifier() {
            GearModifier original = GearModifier.of(
                "of_the_whale", "of the Whale", ModifierType.SUFFIX,
                "max_health", GearModifier.STAT_TYPE_PERCENT, 15.0
            );

            BsonValue encoded = GearCodecs.MODIFIER_CODEC.encode(original, EXTRA_INFO);
            GearModifier decoded = GearCodecs.MODIFIER_CODEC.decode(encoded, EXTRA_INFO);

            assertEquals(original, decoded);
        }

        @Test
        @DisplayName("round-trip negative value")
        void modifierCodec_roundTrip_negativeValue() {
            GearModifier original = GearModifier.of(
                "cursed", "Cursed", ModifierType.PREFIX,
                "luck", GearModifier.STAT_TYPE_FLAT, -10.0
            );

            BsonValue encoded = GearCodecs.MODIFIER_CODEC.encode(original, EXTRA_INFO);
            GearModifier decoded = GearCodecs.MODIFIER_CODEC.decode(encoded, EXTRA_INFO);

            assertEquals(original, decoded);
        }

        @Test
        @DisplayName("round-trip decimal value")
        void modifierCodec_roundTrip_decimalValue() {
            GearModifier original = GearModifier.of(
                "precise", "Precise", ModifierType.PREFIX,
                "critical_chance", GearModifier.STAT_TYPE_PERCENT, 7.55
            );

            BsonValue encoded = GearCodecs.MODIFIER_CODEC.encode(original, EXTRA_INFO);
            GearModifier decoded = GearCodecs.MODIFIER_CODEC.decode(encoded, EXTRA_INFO);

            assertEquals(original, decoded);
        }
    }

    // =========================================================================
    // MODIFIER_LIST_CODEC
    // =========================================================================

    @Nested
    @DisplayName("MODIFIER_LIST_CODEC")
    class ModifierListCodec {

        @Test
        @DisplayName("empty list encodes as empty array")
        void modifierListCodec_emptyList_encodesAsEmptyArray() {
            List<GearModifier> emptyList = new ArrayList<>();

            BsonValue encoded = GearCodecs.MODIFIER_LIST_CODEC.encode(emptyList, EXTRA_INFO);

            assertTrue(encoded instanceof BsonArray);
            assertEquals(0, ((BsonArray) encoded).size());
        }

        @Test
        @DisplayName("multiple modifiers preserves order")
        void modifierListCodec_multipleModifiers_preservesOrder() {
            GearModifier mod1 = GearModifier.of("a", "A", ModifierType.PREFIX, "stat1", GearModifier.STAT_TYPE_FLAT, 1.0);
            GearModifier mod2 = GearModifier.of("b", "B", ModifierType.PREFIX, "stat2", GearModifier.STAT_TYPE_FLAT, 2.0);
            GearModifier mod3 = GearModifier.of("c", "C", ModifierType.PREFIX, "stat3", GearModifier.STAT_TYPE_FLAT, 3.0);
            List<GearModifier> list = Arrays.asList(mod1, mod2, mod3);

            BsonValue encoded = GearCodecs.MODIFIER_LIST_CODEC.encode(list, EXTRA_INFO);
            List<GearModifier> decoded = GearCodecs.MODIFIER_LIST_CODEC.decode(encoded, EXTRA_INFO);

            assertEquals(3, decoded.size());
            assertEquals("a", decoded.get(0).id());
            assertEquals("b", decoded.get(1).id());
            assertEquals("c", decoded.get(2).id());
        }

        @Test
        @DisplayName("round-trip preserves all modifiers")
        void modifierListCodec_roundTrip_preservesAllModifiers() {
            GearModifier mod1 = GearModifier.of("sharp", "Sharp", ModifierType.PREFIX, "phys", GearModifier.STAT_TYPE_FLAT, 10.0);
            GearModifier mod2 = GearModifier.of("of_whale", "of the Whale", ModifierType.SUFFIX, "health", GearModifier.STAT_TYPE_PERCENT, 20.0);
            List<GearModifier> original = Arrays.asList(mod1, mod2);

            BsonValue encoded = GearCodecs.MODIFIER_LIST_CODEC.encode(original, EXTRA_INFO);
            List<GearModifier> decoded = GearCodecs.MODIFIER_LIST_CODEC.decode(encoded, EXTRA_INFO);

            assertEquals(original, decoded);
        }
    }

    // =========================================================================
    // GEAR_DATA_CODEC - encode
    // =========================================================================

    @Nested
    @DisplayName("GEAR_DATA_CODEC encode")
    class GearDataCodecEncode {

        @Test
        @DisplayName("encode produces valid BsonDocument")
        void gearDataCodec_encode_producesValidBson() {
            GearData data = GearData.builder()
                .level(50)
                .rarity(GearRarity.EPIC)
                .quality(75)
                .build();

            BsonValue encoded = GearCodecs.GEAR_DATA_CODEC.encode(data, EXTRA_INFO);

            assertNotNull(encoded);
            assertTrue(encoded instanceof BsonDocument);
        }

        @Test
        @DisplayName("encode includes all fields")
        void gearDataCodec_encode_includesAllFields() {
            GearData data = GearData.builder()
                .level(50)
                .rarity(GearRarity.EPIC)
                .quality(75)
                .build();

            BsonDocument doc = (BsonDocument) GearCodecs.GEAR_DATA_CODEC.encode(data, EXTRA_INFO);

            assertTrue(doc.containsKey("level"));
            assertTrue(doc.containsKey("rarity"));
            assertTrue(doc.containsKey("quality"));
            assertTrue(doc.containsKey("prefixes"));
            assertTrue(doc.containsKey("suffixes"));

            assertEquals(50, doc.getInt32("level").getValue());
            assertEquals("EPIC", doc.getString("rarity").getValue());
            assertEquals(75, doc.getInt32("quality").getValue());
        }

        @Test
        @DisplayName("encode with empty modifier lists")
        void gearDataCodec_encode_emptyModifierLists() {
            GearData data = GearData.builder()
                .level(1)
                .rarity(GearRarity.COMMON)
                .quality(50)
                .build();

            BsonDocument doc = (BsonDocument) GearCodecs.GEAR_DATA_CODEC.encode(data, EXTRA_INFO);

            assertTrue(doc.get("prefixes").isArray());
            assertTrue(doc.get("suffixes").isArray());
            assertEquals(0, doc.getArray("prefixes").size());
            assertEquals(0, doc.getArray("suffixes").size());
        }

        @Test
        @DisplayName("encode with modifiers")
        void gearDataCodec_encode_withModifiers() {
            GearModifier prefix = GearModifier.of("sharp", "Sharp", ModifierType.PREFIX,
                "phys", GearModifier.STAT_TYPE_FLAT, 10.0);
            GearModifier suffix = GearModifier.of("of_whale", "of the Whale", ModifierType.SUFFIX,
                "health", GearModifier.STAT_TYPE_PERCENT, 20.0);

            GearData data = GearData.builder()
                .level(50)
                .rarity(GearRarity.RARE)
                .quality(75)
                .addPrefix(prefix)
                .addSuffix(suffix)
                .build();

            BsonDocument doc = (BsonDocument) GearCodecs.GEAR_DATA_CODEC.encode(data, EXTRA_INFO);

            assertEquals(1, doc.getArray("prefixes").size());
            assertEquals(1, doc.getArray("suffixes").size());
        }
    }

    // =========================================================================
    // GEAR_DATA_CODEC - decode
    // =========================================================================

    @Nested
    @DisplayName("GEAR_DATA_CODEC decode")
    class GearDataCodecDecode {

        @Test
        @DisplayName("decode valid document succeeds")
        void gearDataCodec_decode_validDocument_succeeds() {
            BsonDocument doc = new BsonDocument()
                .append("level", new BsonInt32(50))
                .append("rarity", new BsonString("EPIC"))
                .append("quality", new BsonInt32(75))
                .append("prefixes", new BsonArray())
                .append("suffixes", new BsonArray());

            GearData data = GearCodecs.GEAR_DATA_CODEC.decode(doc, EXTRA_INFO);

            assertEquals(50, data.level());
            assertEquals(GearRarity.EPIC, data.rarity());
            assertEquals(75, data.quality());
            assertTrue(data.prefixes().isEmpty());
            assertTrue(data.suffixes().isEmpty());
        }

        @Test
        @DisplayName("decode missing level throws exception")
        void gearDataCodec_decode_missingLevel_throwsException() {
            BsonDocument doc = new BsonDocument()
                .append("rarity", new BsonString("EPIC"))
                .append("quality", new BsonInt32(75))
                .append("prefixes", new BsonArray())
                .append("suffixes", new BsonArray());

            assertThrows(IllegalArgumentException.class, () ->
                GearCodecs.GEAR_DATA_CODEC.decode(doc, EXTRA_INFO)
            );
        }

        @Test
        @DisplayName("decode missing rarity throws exception")
        void gearDataCodec_decode_missingRarity_throwsException() {
            BsonDocument doc = new BsonDocument()
                .append("level", new BsonInt32(50))
                .append("quality", new BsonInt32(75))
                .append("prefixes", new BsonArray())
                .append("suffixes", new BsonArray());

            assertThrows(IllegalArgumentException.class, () ->
                GearCodecs.GEAR_DATA_CODEC.decode(doc, EXTRA_INFO)
            );
        }

        @Test
        @DisplayName("decode missing quality throws exception")
        void gearDataCodec_decode_missingQuality_throwsException() {
            BsonDocument doc = new BsonDocument()
                .append("level", new BsonInt32(50))
                .append("rarity", new BsonString("EPIC"))
                .append("prefixes", new BsonArray())
                .append("suffixes", new BsonArray());

            assertThrows(IllegalArgumentException.class, () ->
                GearCodecs.GEAR_DATA_CODEC.decode(doc, EXTRA_INFO)
            );
        }

        @Test
        @DisplayName("decode missing prefixes returns empty list")
        void gearDataCodec_decode_missingPrefixes_returnsEmptyList() {
            BsonDocument doc = new BsonDocument()
                .append("level", new BsonInt32(50))
                .append("rarity", new BsonString("EPIC"))
                .append("quality", new BsonInt32(75))
                .append("suffixes", new BsonArray());

            GearData data = GearCodecs.GEAR_DATA_CODEC.decode(doc, EXTRA_INFO);

            assertTrue(data.prefixes().isEmpty());
        }

        @Test
        @DisplayName("decode missing suffixes returns empty list")
        void gearDataCodec_decode_missingSuffixes_returnsEmptyList() {
            BsonDocument doc = new BsonDocument()
                .append("level", new BsonInt32(50))
                .append("rarity", new BsonString("EPIC"))
                .append("quality", new BsonInt32(75))
                .append("prefixes", new BsonArray());

            GearData data = GearCodecs.GEAR_DATA_CODEC.decode(doc, EXTRA_INFO);

            assertTrue(data.suffixes().isEmpty());
        }
    }

    // =========================================================================
    // GEAR_DATA_CODEC - round-trip
    // =========================================================================

    @Nested
    @DisplayName("GEAR_DATA_CODEC round-trip")
    class GearDataCodecRoundTrip {

        @Test
        @DisplayName("round-trip minimal data")
        void gearDataCodec_roundTrip_minimalData() {
            GearData original = GearData.builder()
                .level(1)
                .rarity(GearRarity.COMMON)
                .quality(50)
                .build();

            BsonValue encoded = GearCodecs.GEAR_DATA_CODEC.encode(original, EXTRA_INFO);
            GearData decoded = GearCodecs.GEAR_DATA_CODEC.decode(encoded, EXTRA_INFO);

            assertEquals(original, decoded);
        }

        @Test
        @DisplayName("round-trip full data")
        void gearDataCodec_roundTrip_fullData() {
            GearModifier prefix1 = GearModifier.of("sharp", "Sharp", ModifierType.PREFIX,
                "phys", GearModifier.STAT_TYPE_FLAT, 10.0);
            GearModifier prefix2 = GearModifier.of("deadly", "Deadly", ModifierType.PREFIX,
                "crit", GearModifier.STAT_TYPE_PERCENT, 5.0);
            GearModifier suffix = GearModifier.of("of_whale", "of the Whale", ModifierType.SUFFIX,
                "health", GearModifier.STAT_TYPE_PERCENT, 20.0);

            GearData original = GearData.builder()
                .level(100)
                .rarity(GearRarity.LEGENDARY)
                .quality(95)
                .addPrefix(prefix1)
                .addPrefix(prefix2)
                .addSuffix(suffix)
                .build();

            BsonValue encoded = GearCodecs.GEAR_DATA_CODEC.encode(original, EXTRA_INFO);
            GearData decoded = GearCodecs.GEAR_DATA_CODEC.decode(encoded, EXTRA_INFO);

            assertEquals(original, decoded);
        }

        @Test
        @DisplayName("round-trip perfect quality")
        void gearDataCodec_roundTrip_perfectQuality() {
            GearData original = GearData.builder()
                .level(50)
                .rarity(GearRarity.EPIC)
                .quality(GearData.PERFECT_QUALITY)
                .build();

            BsonValue encoded = GearCodecs.GEAR_DATA_CODEC.encode(original, EXTRA_INFO);
            GearData decoded = GearCodecs.GEAR_DATA_CODEC.decode(encoded, EXTRA_INFO);

            assertEquals(original, decoded);
            assertEquals(GearData.PERFECT_QUALITY, decoded.quality());
        }

        @Test
        @DisplayName("round-trip all rarities")
        void gearDataCodec_roundTrip_allRarities() {
            for (GearRarity rarity : GearRarity.values()) {
                GearData original = GearData.builder()
                    .level(50)
                    .rarity(rarity)
                    .quality(75)
                    .build();

                BsonValue encoded = GearCodecs.GEAR_DATA_CODEC.encode(original, EXTRA_INFO);
                GearData decoded = GearCodecs.GEAR_DATA_CODEC.decode(encoded, EXTRA_INFO);

                assertEquals(original, decoded, "Failed for rarity: " + rarity);
            }
        }
    }

    // =========================================================================
    // Safe decode methods
    // =========================================================================

    @Nested
    @DisplayName("Safe decode methods")
    class SafeDecodeMethods {

        @Test
        @DisplayName("safeDecodeModifier null returns empty")
        void safeDecodeModifier_null_returnsEmpty() {
            Optional<GearModifier> result = GearCodecs.safeDecodeModifier(null);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("safeDecodeModifier valid returns Optional")
        void safeDecodeModifier_valid_returnsOptional() {
            BsonDocument doc = new BsonDocument()
                .append("id", new BsonString("sharp"))
                .append("displayName", new BsonString("Sharp"))
                .append("type", new BsonString("PREFIX"))
                .append("statId", new BsonString("phys"))
                .append("statType", new BsonString(GearModifier.STAT_TYPE_FLAT))
                .append("value", new BsonDouble(10.0));

            Optional<GearModifier> result = GearCodecs.safeDecodeModifier(doc);

            assertTrue(result.isPresent());
            assertEquals("sharp", result.get().id());
        }

        @Test
        @DisplayName("safeDecodeModifier invalid returns empty")
        void safeDecodeModifier_invalid_returnsEmpty() {
            BsonDocument doc = new BsonDocument()
                .append("invalid", new BsonString("data"));

            Optional<GearModifier> result = GearCodecs.safeDecodeModifier(doc);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("safeDecodeGearData null returns empty")
        void safeDecodeGearData_null_returnsEmpty() {
            Optional<GearData> result = GearCodecs.safeDecodeGearData(null);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("safeDecodeGearData valid returns Optional")
        void safeDecodeGearData_valid_returnsOptional() {
            BsonDocument doc = new BsonDocument()
                .append("level", new BsonInt32(50))
                .append("rarity", new BsonString("EPIC"))
                .append("quality", new BsonInt32(75))
                .append("prefixes", new BsonArray())
                .append("suffixes", new BsonArray());

            Optional<GearData> result = GearCodecs.safeDecodeGearData(doc);

            assertTrue(result.isPresent());
            assertEquals(50, result.get().level());
        }

        @Test
        @DisplayName("safeDecodeGearData invalid returns empty")
        void safeDecodeGearData_invalid_returnsEmpty() {
            BsonDocument doc = new BsonDocument()
                .append("invalid", new BsonString("data"));

            Optional<GearData> result = GearCodecs.safeDecodeGearData(doc);

            assertTrue(result.isEmpty());
        }
    }
}
