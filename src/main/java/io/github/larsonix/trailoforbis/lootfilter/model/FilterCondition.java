package io.github.larsonix.trailoforbis.lootfilter.model;

import io.github.larsonix.trailoforbis.gear.model.ArmorImplicit;
import io.github.larsonix.trailoforbis.gear.model.EquipmentType;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.gear.model.WeaponImplicit;
import io.github.larsonix.trailoforbis.gear.model.WeaponType;
import io.github.larsonix.trailoforbis.maps.core.RealmBiomeType;
import io.github.larsonix.trailoforbis.maps.core.RealmLayoutSize;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifier;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifierType;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A single filter condition that evaluates whether an item matches.
 *
 * <p>Sealed interface with record implementations — one per {@link ConditionType}.
 * Within a rule, multiple conditions are AND'd together (all must match).
 *
 * <p>Conditions may apply to gear, maps, or both. Gear rules use {@link #matches},
 * map rules use {@link #matchesMap}. The default {@code matchesMap} returns true
 * (gear-only conditions auto-pass when used in map evaluation).
 */
public sealed interface FilterCondition permits
        FilterCondition.MinRarity,
        FilterCondition.MaxRarity,
        FilterCondition.EquipmentSlotCondition,
        FilterCondition.WeaponTypeCondition,
        FilterCondition.ArmorImplicitCondition,
        FilterCondition.ItemLevelRange,
        FilterCondition.QualityRange,
        FilterCondition.RequiredModifiers,
        FilterCondition.ModifierValueRange,
        FilterCondition.ImplicitCondition,
        FilterCondition.MinModifierCount,
        FilterCondition.CorruptionStateCondition,
        FilterCondition.BiomeCondition,
        FilterCondition.MapSizeCondition,
        FilterCondition.MapModifierCondition {

    /** The discriminator type for serialization. */
    ConditionType type();

    /** Returns true if gear matches this condition. */
    boolean matches(@Nonnull GearData gearData, @Nonnull EquipmentType equipmentType);

    /** Returns true if a realm map matches this condition. Default: pass-through. */
    default boolean matchesMap(@Nonnull RealmMapData mapData) { return true; }

    /** Whether this condition is only meaningful for maps (not available in gear rules). */
    default boolean isMapOnly() { return false; }

    /** Whether this condition is only meaningful for gear (not available in map rules). */
    default boolean isGearOnly() { return false; }

    /** Human-readable summary for UI and commands. */
    String describe();

    // ═══════════════════════════════════════════════════════════════════
    // IMPLEMENTATIONS
    // ═══════════════════════════════════════════════════════════════════

    /** rarity >= threshold (shared: works for gear and maps) */
    record MinRarity(@Nonnull GearRarity threshold) implements FilterCondition {
        @Override
        public ConditionType type() { return ConditionType.MIN_RARITY; }

        @Override
        public boolean matches(@Nonnull GearData g, @Nonnull EquipmentType e) {
            return g.rarity().ordinal() >= threshold.ordinal();
        }

        @Override
        public boolean matchesMap(@Nonnull RealmMapData m) {
            return m.rarity().ordinal() >= threshold.ordinal();
        }

        @Override
        public String describe() { return threshold.name() + " or better"; }
    }

    /** rarity <= threshold (shared: works for gear and maps) */
    record MaxRarity(@Nonnull GearRarity threshold) implements FilterCondition {
        @Override
        public ConditionType type() { return ConditionType.MAX_RARITY; }

        @Override
        public boolean matches(@Nonnull GearData g, @Nonnull EquipmentType e) {
            return g.rarity().ordinal() <= threshold.ordinal();
        }

        @Override
        public boolean matchesMap(@Nonnull RealmMapData m) {
            return m.rarity().ordinal() <= threshold.ordinal();
        }

        @Override
        public String describe() { return threshold.name() + " or worse"; }
    }

    /** Equipment slot membership (gear-only) */
    record EquipmentSlotCondition(@Nonnull Set<String> slots) implements FilterCondition {
        public EquipmentSlotCondition {
            slots = Set.copyOf(slots);
        }

        @Override public ConditionType type() { return ConditionType.EQUIPMENT_SLOT; }
        @Override public boolean isGearOnly() { return true; }

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

    /** Weapon type membership (gear-only) */
    record WeaponTypeCondition(@Nonnull Set<WeaponType> types) implements FilterCondition {
        public WeaponTypeCondition {
            types = Set.copyOf(types);
        }

        @Override public ConditionType type() { return ConditionType.WEAPON_TYPE; }
        @Override public boolean isGearOnly() { return true; }

        @Override
        public boolean matches(@Nonnull GearData g, @Nonnull EquipmentType e) {
            if (e.getCategory() != EquipmentType.Category.WEAPON) return false;
            return e.getWeaponType() != null && types.contains(e.getWeaponType());
        }

        @Override
        public String describe() {
            return types.stream()
                    .map(t -> capitalize(t.name()))
                    .sorted()
                    .collect(Collectors.joining(", "));
        }
    }

    /** Armor implicit defense type filter (gear-only) */
    record ArmorImplicitCondition(@Nonnull Set<String> defenseTypes) implements FilterCondition {
        public ArmorImplicitCondition {
            defenseTypes = Set.copyOf(defenseTypes);
        }

        @Override public ConditionType type() { return ConditionType.ARMOR_IMPLICIT; }
        @Override public boolean isGearOnly() { return true; }

        @Override
        public boolean matches(@Nonnull GearData g, @Nonnull EquipmentType e) {
            ArmorImplicit impl = g.armorImplicit();
            if (impl == null) return false;
            return defenseTypes.contains(impl.defenseType());
        }

        @Override
        public String describe() {
            return defenseTypes.stream()
                    .map(FilterCondition::formatDamageType)
                    .sorted()
                    .collect(Collectors.joining(", "));
        }
    }

    /** min <= level <= max (shared: works for gear and maps) */
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

        @Override public ConditionType type() { return ConditionType.ITEM_LEVEL_RANGE; }

        @Override
        public boolean matches(@Nonnull GearData g, @Nonnull EquipmentType e) {
            return g.level() >= min && g.level() <= max;
        }

        @Override
        public boolean matchesMap(@Nonnull RealmMapData m) {
            return m.level() >= min && m.level() <= max;
        }

        @Override
        public String describe() {
            if (min == max) return "Level " + min;
            return "Level " + min + "–" + max;
        }
    }

    /** min <= quality <= max (shared: works for gear and maps) */
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

        @Override public ConditionType type() { return ConditionType.QUALITY_RANGE; }

        @Override
        public boolean matches(@Nonnull GearData g, @Nonnull EquipmentType e) {
            return g.quality() >= min && g.quality() <= max;
        }

        @Override
        public boolean matchesMap(@Nonnull RealmMapData m) {
            return m.quality() >= min && m.quality() <= max;
        }

        @Override
        public String describe() {
            if (max >= 101) return "Quality " + min + "+";
            if (min <= 1) return "Quality ≤" + max;
            return "Quality " + min + "–" + max;
        }
    }

    /** At least N of the listed gear modifiers present (gear-only) */
    record RequiredModifiers(@Nonnull Set<String> modifierIds, int minCount) implements FilterCondition {
        public RequiredModifiers {
            modifierIds = Set.copyOf(modifierIds);
            minCount = Math.max(1, minCount);
        }

        @Override public ConditionType type() { return ConditionType.REQUIRED_MODIFIERS; }
        @Override public boolean isGearOnly() { return true; }

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

    /** Specific gear modifier with roll value in range (gear-only) */
    record ModifierValueRange(@Nonnull String modifierId, double minValue, double maxValue)
            implements FilterCondition {
        public ModifierValueRange {
            if (minValue > maxValue) {
                double tmp = minValue;
                minValue = maxValue;
                maxValue = tmp;
            }
        }

        @Override public ConditionType type() { return ConditionType.MODIFIER_VALUE_RANGE; }
        @Override public boolean isGearOnly() { return true; }

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

    /** Weapon implicit roll quality and damage type (gear-only) */
    record ImplicitCondition(double minPercentile, @Nonnull Set<String> damageTypes)
            implements FilterCondition {
        public ImplicitCondition {
            minPercentile = Math.clamp(minPercentile, 0.0, 1.0);
            damageTypes = Set.copyOf(damageTypes);
        }

        @Override public ConditionType type() { return ConditionType.IMPLICIT_CONDITION; }
        @Override public boolean isGearOnly() { return true; }

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

    /** Total modifiers >= threshold (shared: works for gear and maps) */
    record MinModifierCount(int count) implements FilterCondition {
        public MinModifierCount {
            count = Math.clamp(count, 0, 6);
        }

        @Override public ConditionType type() { return ConditionType.MIN_MODIFIER_COUNT; }

        @Override
        public boolean matches(@Nonnull GearData g, @Nonnull EquipmentType e) {
            return g.allModifiers().size() >= count;
        }

        @Override
        public boolean matchesMap(@Nonnull RealmMapData m) {
            return m.modifierCount() >= count;
        }

        @Override
        public String describe() { return count + "+ modifiers"; }
    }

    /** Corruption state filter (shared: works for gear and maps) */
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
        public boolean matchesMap(@Nonnull RealmMapData m) {
            return switch (filter) {
                case CORRUPTED_ONLY -> m.corrupted();
                case NOT_CORRUPTED -> !m.corrupted();
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
    // MAP-ONLY CONDITIONS
    // ═══════════════════════════════════════════════════════════════════

    /** Realm biome membership (map-only) */
    record BiomeCondition(@Nonnull Set<RealmBiomeType> biomes) implements FilterCondition {
        public BiomeCondition { biomes = Set.copyOf(biomes); }

        @Override public ConditionType type() { return ConditionType.MAP_BIOME; }
        @Override public boolean isMapOnly() { return true; }
        @Override public boolean matches(@Nonnull GearData g, @Nonnull EquipmentType e) { return true; }

        @Override
        public boolean matchesMap(@Nonnull RealmMapData m) {
            return biomes.contains(m.biome());
        }

        @Override
        public String describe() {
            return biomes.stream()
                    .map(RealmBiomeType::getDisplayName)
                    .sorted()
                    .collect(Collectors.joining(", "));
        }
    }

    /** Realm map size membership (map-only) */
    record MapSizeCondition(@Nonnull Set<RealmLayoutSize> sizes) implements FilterCondition {
        public MapSizeCondition { sizes = Set.copyOf(sizes); }

        @Override public ConditionType type() { return ConditionType.MAP_SIZE; }
        @Override public boolean isMapOnly() { return true; }
        @Override public boolean matches(@Nonnull GearData g, @Nonnull EquipmentType e) { return true; }

        @Override
        public boolean matchesMap(@Nonnull RealmMapData m) {
            return sizes.contains(m.size());
        }

        @Override
        public String describe() {
            return sizes.stream()
                    .map(s -> capitalize(s.name()))
                    .sorted()
                    .collect(Collectors.joining(", "));
        }
    }

    /** Realm map modifier presence (map-only) */
    record MapModifierCondition(@Nonnull Set<RealmModifierType> modifierTypes, int minCount)
            implements FilterCondition {
        public MapModifierCondition {
            modifierTypes = Set.copyOf(modifierTypes);
            minCount = Math.max(1, minCount);
        }

        @Override public ConditionType type() { return ConditionType.MAP_MODIFIER; }
        @Override public boolean isMapOnly() { return true; }
        @Override public boolean matches(@Nonnull GearData g, @Nonnull EquipmentType e) { return true; }

        @Override
        public boolean matchesMap(@Nonnull RealmMapData m) {
            long count = Stream.concat(m.prefixes().stream(), m.suffixes().stream())
                    .map(RealmModifier::type)
                    .filter(modifierTypes::contains)
                    .count();
            return count >= minCount;
        }

        @Override
        public String describe() {
            String mods = modifierTypes.stream()
                    .map(RealmModifierType::getDisplayName)
                    .sorted()
                    .collect(Collectors.joining(", "));
            if (minCount == 1 && modifierTypes.size() == 1) return "Has: " + mods;
            if (minCount >= modifierTypes.size()) return "Has all: " + mods;
            return "Has " + minCount + "+ of: " + mods;
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
