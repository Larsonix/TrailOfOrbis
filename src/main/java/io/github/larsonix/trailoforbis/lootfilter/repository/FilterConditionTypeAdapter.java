package io.github.larsonix.trailoforbis.lootfilter.repository;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import io.github.larsonix.trailoforbis.gear.model.ArmorMaterial;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.gear.model.WeaponType;
import io.github.larsonix.trailoforbis.lootfilter.model.ConditionType;
import io.github.larsonix.trailoforbis.lootfilter.model.CorruptionFilter;
import io.github.larsonix.trailoforbis.lootfilter.model.FilterCondition;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Gson TypeAdapter for the sealed {@link FilterCondition} interface.
 *
 * <p>Serializes using a {@code "type"} discriminator field matching {@link ConditionType}.
 * Deserializes by reading the type first, then parsing remaining fields into the correct record.
 */
public final class FilterConditionTypeAdapter extends TypeAdapter<FilterCondition> {

    @Override
    public void write(JsonWriter out, FilterCondition condition) throws IOException {
        out.beginObject();
        out.name("type").value(condition.type().name());

        switch (condition) {
            case FilterCondition.MinRarity c -> out.name("threshold").value(c.threshold().name());
            case FilterCondition.MaxRarity c -> out.name("threshold").value(c.threshold().name());
            case FilterCondition.EquipmentSlotCondition c -> {
                out.name("slots");
                writeStringSet(out, c.slots());
            }
            case FilterCondition.WeaponTypeCondition c -> {
                out.name("types");
                out.beginArray();
                for (WeaponType t : c.types()) out.value(t.name());
                out.endArray();
            }
            case FilterCondition.ArmorMaterialCondition c -> {
                out.name("materials");
                out.beginArray();
                for (ArmorMaterial m : c.materials()) out.value(m.name());
                out.endArray();
            }
            case FilterCondition.ItemLevelRange c -> {
                out.name("min").value(c.min());
                out.name("max").value(c.max());
            }
            case FilterCondition.QualityRange c -> {
                out.name("min").value(c.min());
                out.name("max").value(c.max());
            }
            case FilterCondition.RequiredModifiers c -> {
                out.name("modifierIds");
                writeStringSet(out, c.modifierIds());
                out.name("minCount").value(c.minCount());
            }
            case FilterCondition.ModifierValueRange c -> {
                out.name("modifierId").value(c.modifierId());
                out.name("minValue").value(c.minValue());
                out.name("maxValue").value(c.maxValue());
            }
            case FilterCondition.ImplicitCondition c -> {
                out.name("minPercentile").value(c.minPercentile());
                out.name("damageTypes");
                writeStringSet(out, c.damageTypes());
            }
            case FilterCondition.MinModifierCount c -> out.name("count").value(c.count());
            case FilterCondition.CorruptionStateCondition c -> out.name("filter").value(c.filter().name());
        }

        out.endObject();
    }

    @Override
    public FilterCondition read(JsonReader in) throws IOException {
        // Read all fields into a map first, then construct the right type
        in.beginObject();
        Map<String, Object> fields = new LinkedHashMap<>();
        while (in.hasNext()) {
            String key = in.nextName();
            fields.put(key, readValue(in));
        }
        in.endObject();

        String typeStr = (String) fields.get("type");
        if (typeStr == null) throw new IOException("Missing 'type' field in FilterCondition");
        ConditionType type = ConditionType.valueOf(typeStr);

        return switch (type) {
            case MIN_RARITY -> new FilterCondition.MinRarity(
                    GearRarity.fromString((String) fields.get("threshold")));
            case MAX_RARITY -> new FilterCondition.MaxRarity(
                    GearRarity.fromString((String) fields.get("threshold")));
            case EQUIPMENT_SLOT -> new FilterCondition.EquipmentSlotCondition(
                    toStringSet(fields.get("slots")));
            case WEAPON_TYPE -> {
                Set<WeaponType> types = new HashSet<>();
                for (String s : toStringSet(fields.get("types"))) {
                    types.add(WeaponType.valueOf(s));
                }
                yield new FilterCondition.WeaponTypeCondition(types);
            }
            case ARMOR_MATERIAL -> {
                Set<ArmorMaterial> materials = new HashSet<>();
                for (String s : toStringSet(fields.get("materials"))) {
                    materials.add(ArmorMaterial.valueOf(s));
                }
                yield new FilterCondition.ArmorMaterialCondition(materials);
            }
            case ITEM_LEVEL_RANGE -> new FilterCondition.ItemLevelRange(
                    toInt(fields.get("min")), toInt(fields.get("max")));
            case QUALITY_RANGE -> new FilterCondition.QualityRange(
                    toInt(fields.get("min")), toInt(fields.get("max")));
            case REQUIRED_MODIFIERS -> new FilterCondition.RequiredModifiers(
                    toStringSet(fields.get("modifierIds")),
                    toInt(fields.get("minCount")));
            case MODIFIER_VALUE_RANGE -> new FilterCondition.ModifierValueRange(
                    (String) fields.get("modifierId"),
                    toDouble(fields.get("minValue")),
                    toDouble(fields.get("maxValue")));
            case IMPLICIT_CONDITION -> new FilterCondition.ImplicitCondition(
                    toDouble(fields.get("minPercentile")),
                    toStringSet(fields.get("damageTypes")));
            case MIN_MODIFIER_COUNT -> new FilterCondition.MinModifierCount(
                    toInt(fields.get("count")));
            case CORRUPTION_STATE -> new FilterCondition.CorruptionStateCondition(
                    CorruptionFilter.valueOf((String) fields.get("filter")));
        };
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════

    private void writeStringSet(JsonWriter out, Set<String> set) throws IOException {
        out.beginArray();
        for (String s : set) out.value(s);
        out.endArray();
    }

    private Object readValue(JsonReader in) throws IOException {
        return switch (in.peek()) {
            case STRING -> in.nextString();
            case NUMBER -> in.nextDouble();
            case BOOLEAN -> in.nextBoolean();
            case NULL -> { in.nextNull(); yield null; }
            case BEGIN_ARRAY -> {
                var list = new java.util.ArrayList<String>();
                in.beginArray();
                while (in.hasNext()) list.add(in.nextString());
                in.endArray();
                yield list;
            }
            default -> throw new IOException("Unexpected token: " + in.peek());
        };
    }

    @SuppressWarnings("unchecked")
    private Set<String> toStringSet(Object obj) {
        if (obj instanceof java.util.Collection<?> c) {
            return new HashSet<>((java.util.Collection<String>) c);
        }
        return Set.of();
    }

    private int toInt(Object obj) {
        if (obj instanceof Number n) return n.intValue();
        return 0;
    }

    private double toDouble(Object obj) {
        if (obj instanceof Number n) return n.doubleValue();
        return 0.0;
    }
}
