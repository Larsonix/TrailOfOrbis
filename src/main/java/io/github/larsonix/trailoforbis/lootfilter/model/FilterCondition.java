package io.github.larsonix.trailoforbis.lootfilter.model;

import io.github.larsonix.trailoforbis.gear.model.ArmorMaterial;
import io.github.larsonix.trailoforbis.gear.model.EquipmentType;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.gear.model.WeaponImplicit;
import io.github.larsonix.trailoforbis.gear.model.WeaponType;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A single filter condition that evaluates whether an item matches.
 *
 * <p>Sealed interface with 12 record implementations — one per {@link ConditionType}.
 * Within a rule, multiple conditions are AND'd together (all must match).
 */
public sealed interface FilterCondition permits
        FilterCondition.MinRarity,
        FilterCondition.MaxRarity,
        FilterCondition.EquipmentSlotCondition,
        FilterCondition.WeaponTypeCondition,
        FilterCondition.ArmorMaterialCondition,
        FilterCondition.ItemLevelRange,
        FilterCondition.QualityRange,
        FilterCondition.RequiredModifiers,
        FilterCondition.ModifierValueRange,
        FilterCondition.ImplicitCondition,
        FilterCondition.MinModifierCount,
        FilterCondition.CorruptionStateCondition {

    /** The discriminator type for serialization. */
    ConditionType type();

    /** Returns true if the item matches this condition. */
    boolean matches(@Nonnull GearData gearData, @Nonnull EquipmentType equipmentType);

    /** Human-readable summary for UI and commands. */
    String describe();

    // ═══════════════════════════════════════════════════════════════════
    // IMPLEMENTATIONS
    // ═══════════════════════════════════════════════════════════════════

    /** rarity >= threshold */
    record MinRarity(@Nonnull GearRarity threshold) implements FilterCondition {
        @Override
        public ConditionType type() { return ConditionType.MIN_RARITY; }

        @Override
        public boolean matches(@Nonnull GearData g, @Nonnull EquipmentType e) {
            return g.rarity().ordinal() >= threshold.ordinal();
        }

        @Override
        public String describe() { return threshold.name() + " or better"; }
    }

    /** rarity <= threshold */
    record MaxRarity(@Nonnull GearRarity threshold) implements FilterCondition {
        @Override
        public ConditionType type() { return ConditionType.MAX_RARITY; }

        @Override
        public boolean matches(@Nonnull GearData g, @Nonnull EquipmentType e) {
            return g.rarity().ordinal() <= threshold.ordinal();
        }

        @Override
        public String describe() { return threshold.name() + " or worse"; }
    }

    /** Equipment slot membership (uses EquipmentType.getSlot()) */
    record EquipmentSlotCondition(@Nonnull Set<String> slots) implements FilterCondition {
        public EquipmentSlotCondition {
            slots = Set.copyOf(slots);
        }

        @Override
        public ConditionType type() { return ConditionType.EQUIPMENT_SLOT; }

        @Override
        public boolean matches(@Nonnull GearData g, @Nonnull EquipmentType e) {
            return slots.contains(e.getSlot());
        }

        @Override
        public String describe() {
            return slots.stream()
                    .map(FilterCondition::capitalize)
                    .sorted()
                    .collect(Collectors.joining(", "));
        }
    }

    /** Weapon type membership */
    record WeaponTypeCondition(@Nonnull Set<WeaponType> types) implements FilterCondition {
        public WeaponTypeCondition {
            types = Set.copyOf(types);
        }

        @Override
        public ConditionType type() { return ConditionType.WEAPON_TYPE; }

        @Override
        public boolean matches(@Nonnull GearData g, @Nonnull EquipmentType e) {
            if (e.getCategory() != EquipmentType.Category.WEAPON) return false;
            return e.getWeaponType() != null && types.contains(e.getWeaponType());
        }

        @Override
        public String describe() {
            return types.stream()
                    .map(WeaponType::getIdPattern)
                    .sorted()
                    .collect(Collectors.joining(", "));
        }
    }

    /** Armor material membership */
    record ArmorMaterialCondition(@Nonnull Set<ArmorMaterial> materials) implements FilterCondition {
        public ArmorMaterialCondition {
            materials = Set.copyOf(materials);
        }

        @Override
        public ConditionType type() { return ConditionType.ARMOR_MATERIAL; }

        @Override
        public boolean matches(@Nonnull GearData g, @Nonnull EquipmentType e) {
            if (e.getCategory() != EquipmentType.Category.ARMOR) return false;
            return e.getArmorMaterial() != null && materials.contains(e.getArmorMaterial());
        }

        @Override
        public String describe() {
            return materials.stream()
                    .map(m -> capitalize(m.name()))
                    .sorted()
                    .collect(Collectors.joining(", "));
        }
    }

    /** min <= level <= max */
    record ItemLevelRange(int min, int max) implements FilterCondition {
        public ItemLevelRange {
            if (min > max) {
                int tmp = min;
                min = max;
                max = tmp;
            }
            min = Math.max(1, min);
            max = Math.min(1_000_000, max);
        }

        @Override
        public ConditionType type() { return ConditionType.ITEM_LEVEL_RANGE; }

        @Override
        public boolean matches(@Nonnull GearData g, @Nonnull EquipmentType e) {
            return g.level() >= min && g.level() <= max;
        }

        @Override
        public String describe() {
            if (min == max) return "Level " + min;
            return "Level " + min + "–" + max;
        }
    }

    /** min <= quality <= max */
    record QualityRange(int min, int max) implements FilterCondition {
        public QualityRange {
            if (min > max) {
                int tmp = min;
                min = max;
                max = tmp;
            }
            min = Math.max(1, min);
            max = Math.min(101, max);
        }

        @Override
        public ConditionType type() { return ConditionType.QUALITY_RANGE; }

        @Override
        public boolean matches(@Nonnull GearData g, @Nonnull EquipmentType e) {
            return g.quality() >= min && g.quality() <= max;
        }

        @Override
        public String describe() {
            if (max >= 101) return "Quality " + min + "+";
            if (min <= 1) return "Quality ≤" + max;
            return "Quality " + min + "–" + max;
        }
    }

    /** At least N of the listed modifiers present on the item */
    record RequiredModifiers(@Nonnull Set<String> modifierIds, int minCount) implements FilterCondition {
        public RequiredModifiers {
            modifierIds = Set.copyOf(modifierIds);
            minCount = Math.max(1, minCount);
        }

        @Override
        public ConditionType type() { return ConditionType.REQUIRED_MODIFIERS; }

        @Override
        public boolean matches(@Nonnull GearData g, @Nonnull EquipmentType e) {
            long matchCount = g.allModifiers().stream()
                    .map(m -> m.id())
                    .filter(modifierIds::contains)
                    .count();
            return matchCount >= minCount;
        }

        @Override
        public String describe() {
            String mods = String.join(", ", modifierIds.stream().sorted().toList());
            if (minCount == 1 && modifierIds.size() == 1) return "Has: " + mods;
            if (minCount >= modifierIds.size()) return "Has all: " + mods;
            return "Has " + minCount + "+ of: " + mods;
        }
    }

    /** Specific modifier with roll value in range */
    record ModifierValueRange(@Nonnull String modifierId, double minValue, double maxValue)
            implements FilterCondition {
        public ModifierValueRange {
            if (minValue > maxValue) {
                double tmp = minValue;
                minValue = maxValue;
                maxValue = tmp;
            }
        }

        @Override
        public ConditionType type() { return ConditionType.MODIFIER_VALUE_RANGE; }

        @Override
        public boolean matches(@Nonnull GearData g, @Nonnull EquipmentType e) {
            return g.allModifiers().stream()
                    .filter(m -> m.id().equals(modifierId))
                    .anyMatch(m -> m.value() >= minValue && m.value() <= maxValue);
        }

        @Override
        public String describe() {
            if (maxValue >= 999_999) return modifierId + " ≥ " + formatValue(minValue);
            if (minValue <= 0) return modifierId + " ≤ " + formatValue(maxValue);
            return modifierId + " " + formatValue(minValue) + "–" + formatValue(maxValue);
        }

        private static String formatValue(double v) {
            return v == (long) v ? String.valueOf((long) v) : String.valueOf(v);
        }
    }

    /** Weapon implicit roll quality and damage type */
    record ImplicitCondition(double minPercentile, @Nonnull Set<String> damageTypes)
            implements FilterCondition {
        public ImplicitCondition {
            minPercentile = Math.clamp(minPercentile, 0.0, 1.0);
            damageTypes = Set.copyOf(damageTypes);
        }

        @Override
        public ConditionType type() { return ConditionType.IMPLICIT_CONDITION; }

        @Override
        public boolean matches(@Nonnull GearData g, @Nonnull EquipmentType e) {
            WeaponImplicit impl = g.implicit();
            if (impl == null) return false;
            if (!damageTypes.isEmpty() && !damageTypes.contains(impl.damageType())) {
                return false;
            }
            return impl.rollPercentile() >= minPercentile;
        }

        @Override
        public String describe() {
            List<String> parts = new ArrayList<>();
            if (!damageTypes.isEmpty()) {
                parts.add(damageTypes.stream()
                        .map(FilterCondition::formatDamageType)
                        .sorted()
                        .collect(Collectors.joining("/")));
            }
            if (minPercentile > 0.0) {
                parts.add("roll ≥ " + (int) (minPercentile * 100) + "%");
            }
            if (parts.isEmpty()) return "Has weapon implicit";
            return "Implicit: " + String.join(", ", parts);
        }
    }

    /** Total modifiers >= threshold */
    record MinModifierCount(int count) implements FilterCondition {
        public MinModifierCount {
            count = Math.clamp(count, 0, 6);
        }

        @Override
        public ConditionType type() { return ConditionType.MIN_MODIFIER_COUNT; }

        @Override
        public boolean matches(@Nonnull GearData g, @Nonnull EquipmentType e) {
            return g.allModifiers().size() >= count;
        }

        @Override
        public String describe() { return count + "+ modifiers"; }
    }

    /** Corruption state filter */
    record CorruptionStateCondition(@Nonnull CorruptionFilter filter) implements FilterCondition {
        @Override
        public ConditionType type() { return ConditionType.CORRUPTION_STATE; }

        @Override
        public boolean matches(@Nonnull GearData g, @Nonnull EquipmentType e) {
            return switch (filter) {
                case CORRUPTED_ONLY -> g.corrupted();
                case NOT_CORRUPTED -> !g.corrupted();
                case EITHER -> true;
            };
        }

        @Override
        public String describe() {
            return switch (filter) {
                case CORRUPTED_ONLY -> "Corrupted only";
                case NOT_CORRUPTED -> "Not corrupted";
                case EITHER -> "Any corruption";
            };
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // SHARED HELPERS
    // ═══════════════════════════════════════════════════════════════════

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        String clean = s.replace("_", " ");
        String[] words = clean.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!sb.isEmpty()) sb.append(" ");
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) sb.append(word.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }

    private static String formatDamageType(String type) {
        String[] parts = type.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!sb.isEmpty()) sb.append(" ");
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) sb.append(part.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }
}
