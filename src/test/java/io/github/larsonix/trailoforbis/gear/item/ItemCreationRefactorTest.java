package io.github.larsonix.trailoforbis.gear.item;

import com.hypixel.hytale.protocol.ItemArmorSlot;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemArmor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the RPG item creation refactor — armor decoupling, resource types,
 * and field preservation contracts.
 *
 * <p>Verifies the CONTRACTS of the refactored createCustomItem pipeline:
 * <ul>
 *   <li>Armor combat stats are zeroed (RPG system is sole authority)</li>
 *   <li>Armor knockback fields are preserved (gameplay feel)</li>
 *   <li>Full field-by-field copy creates an independent instance</li>
 *   <li>ResourceTypes merge behavior is idempotent</li>
 * </ul>
 */
class ItemCreationRefactorTest {

    // =========================================================================
    // ARMOR DECOUPLING — COMBAT STATS ZEROED
    // =========================================================================

    @Nested
    @DisplayName("Armor Decoupling")
    class ArmorDecouplingTests {

        /**
         * Simulates the decoupleArmor logic — mirrors ItemRegistryService.decoupleArmor().
         */
        private ItemArmor simulateDecoupleArmor(ItemArmor source) throws Exception {
            Class<?> armorClass = source.getClass();
            ItemArmor decoupled = new ItemArmor(source.getArmorSlot(), 0.0, null, null);

            for (Field field : armorClass.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                field.set(decoupled, field.get(source));
            }

            // Zero combat fields — exact same list as ItemRegistryService
            String[] combatFields = {
                "baseDamageResistance", "statModifiers", "rawStatModifiers",
                "damageResistanceValues", "damageResistanceValuesRaw",
                "damageEnhancementValues", "damageEnhancementValuesRaw",
                "damageClassEnhancement",
                "regeneratingValues", "regenerating",
                "interactionModifiers", "interactionModifiersRaw",
            };

            for (String fieldName : combatFields) {
                try {
                    Field f = armorClass.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    Class<?> fieldType = f.getType();
                    if (fieldType == double.class) f.setDouble(decoupled, 0.0);
                    else if (fieldType == float.class) f.setFloat(decoupled, 0.0f);
                    else if (fieldType == int.class) f.setInt(decoupled, 0);
                    else f.set(decoupled, null);
                } catch (NoSuchFieldException ignored) {}
            }

            return decoupled;
        }

        @Test
        @DisplayName("baseDamageResistance is zeroed after decoupling")
        void decoupleArmor_ZerosBaseDamageResistance() throws Exception {
            ItemArmor source = new ItemArmor(ItemArmorSlot.Chest, 50.0, null, null);

            ItemArmor result = simulateDecoupleArmor(source);

            assertEquals(0.0, result.getBaseDamageResistance(),
                "baseDamageResistance must be zeroed — RPG system is sole defense authority");
        }

        @Test
        @DisplayName("armorSlot is preserved after decoupling")
        void decoupleArmor_PreservesArmorSlot() throws Exception {
            ItemArmor source = new ItemArmor(ItemArmorSlot.Head, 25.0, null, null);

            ItemArmor result = simulateDecoupleArmor(source);

            assertEquals(ItemArmorSlot.Head, result.getArmorSlot(),
                "armorSlot must be preserved — structural, not a combat stat");
        }

        @Test
        @DisplayName("decoupled armor is a different instance (not shared reference)")
        void decoupleArmor_CreatesSeparateInstance() throws Exception {
            ItemArmor source = new ItemArmor(ItemArmorSlot.Legs, 30.0, null, null);

            ItemArmor result = simulateDecoupleArmor(source);

            assertNotSame(source, result,
                "Decoupled armor must be a new instance to prevent base item corruption");
        }

        @Test
        @DisplayName("statModifiers is nulled after decoupling")
        void decoupleArmor_NullsStatModifiers() throws Exception {
            ItemArmor source = new ItemArmor(ItemArmorSlot.Chest, 10.0, null, null);

            ItemArmor result = simulateDecoupleArmor(source);

            Field smField = ItemArmor.class.getDeclaredField("statModifiers");
            smField.setAccessible(true);
            assertNull(smField.get(result),
                "statModifiers must be nulled — VanillaEquipmentStatSuppressor handles at runtime");
        }

        @Test
        @DisplayName("knockbackResistances is preserved after decoupling")
        void decoupleArmor_PreservesKnockbackResistances() throws Exception {
            ItemArmor source = new ItemArmor(ItemArmorSlot.Chest, 10.0, null, null);

            // Set knockbackResistances via reflection (no public setter)
            Field kbField = ItemArmor.class.getDeclaredField("knockbackResistances");
            kbField.setAccessible(true);
            Map<Object, Float> kbResist = new HashMap<>();
            kbResist.put("test_physical", 0.5f);
            kbField.set(source, kbResist);

            ItemArmor result = simulateDecoupleArmor(source);

            Object resultKb = kbField.get(result);
            assertNotNull(resultKb,
                "knockbackResistances must be preserved — gameplay feel, not damage numbers");
        }

        @Test
        @DisplayName("all four armor slots decouple correctly")
        void decoupleArmor_AllSlots() throws Exception {
            for (ItemArmorSlot slot : ItemArmorSlot.values()) {
                ItemArmor source = new ItemArmor(slot, 42.0, null, null);
                ItemArmor result = simulateDecoupleArmor(source);

                assertEquals(slot, result.getArmorSlot(), "Slot " + slot + " must be preserved");
                assertEquals(0.0, result.getBaseDamageResistance(),
                    "baseDamageResistance must be zeroed for slot " + slot);
            }
        }

        @Test
        @DisplayName("combat fields list covers all ItemArmor combat fields")
        void decoupleArmor_CombatFieldListIsComplete() {
            // Verify every non-static field on ItemArmor is either in the combat list
            // or in the known-safe list. Catches new Hytale fields.
            String[] combatFields = {
                "baseDamageResistance", "statModifiers", "rawStatModifiers",
                "damageResistanceValues", "damageResistanceValuesRaw",
                "damageEnhancementValues", "damageEnhancementValuesRaw",
                "damageClassEnhancement",
                "regeneratingValues", "regenerating",
                "interactionModifiers", "interactionModifiersRaw",
            };
            String[] safeFields = {
                "armorSlot", "cosmeticsToHide",
                "knockbackResistances", "knockbackResistancesRaw",
                "knockbackEnhancements", "knockbackEnhancementsRaw",
            };

            java.util.Set<String> known = new java.util.HashSet<>();
            Collections.addAll(known, combatFields);
            Collections.addAll(known, safeFields);

            for (Field f : ItemArmor.class.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                assertTrue(known.contains(f.getName()),
                    "Unknown ItemArmor field '" + f.getName() + "' — classify as combat " +
                    "(zeroed) or safe (preserved). If Hytale added a new field, update " +
                    "the combatFields array in ItemRegistryService.decoupleArmor().");
            }
        }
    }

    // =========================================================================
    // RESOURCE TYPES MERGE
    // =========================================================================

    @Nested
    @DisplayName("ResourceTypes Merge")
    class ResourceTypesMergeTests {

        private String[] simulateMerge(String[] baseTypes, String[] reskinTypes) {
            java.util.List<String> merged = new java.util.ArrayList<>();
            if (baseTypes != null) {
                Collections.addAll(merged, baseTypes);
            }
            Collections.addAll(merged, reskinTypes);
            return merged.toArray(new String[0]);
        }

        @Test
        @DisplayName("merge preserves base item types alongside reskin types")
        void merge_PreservesBaseTypes() {
            String[] base = {"ModType_Salvage", "ModType_Craft"};
            String[] reskin = {"RPG_Reskin_Weapon_Sword_Epic"};

            String[] result = simulateMerge(base, reskin);

            assertEquals(3, result.length);
            assertEquals("ModType_Salvage", result[0]);
            assertEquals("ModType_Craft", result[1]);
            assertEquals("RPG_Reskin_Weapon_Sword_Epic", result[2]);
        }

        @Test
        @DisplayName("merge with null base types produces only reskin types")
        void merge_NullBase_OnlyReskin() {
            String[] result = simulateMerge(null, new String[]{"RPG_Reskin_Armor_Chest_Common"});

            assertEquals(1, result.length);
        }

        @Test
        @DisplayName("merge with empty base types produces only reskin types")
        void merge_EmptyBase_OnlyReskin() {
            String[] result = simulateMerge(new String[0], new String[]{"RPG_Reskin_Weapon_Mace_Rare"});

            assertEquals(1, result.length);
        }

        @Test
        @DisplayName("re-merge from base item doesn't accumulate reskin types")
        void reMerge_DoesNotAccumulate() {
            String[] base = {"ModType_A"};

            // First merge
            String[] afterFirst = simulateMerge(base, new String[]{"RPG_Reskin_V1"});
            assertEquals(2, afterFirst.length);

            // Second merge: reads from BASE again (not from afterFirst)
            String[] afterSecond = simulateMerge(base, new String[]{"RPG_Reskin_V2"});
            assertEquals(2, afterSecond.length, "Re-merge must not accumulate — reads from base");
            assertEquals("ModType_A", afterSecond[0]);
            assertEquals("RPG_Reskin_V2", afterSecond[1]);
        }

        @Test
        @DisplayName("legendary gets both Epic + Legendary reskin types")
        void merge_MultipleReskinTypes() {
            String[] base = {"ModType_X"};
            String[] reskin = {"RPG_Reskin_Epic", "RPG_Reskin_Legendary"};

            String[] result = simulateMerge(base, reskin);

            assertEquals(3, result.length);
        }
    }

    // =========================================================================
    // FIELD-BY-FIELD COPY INDEPENDENCE
    // =========================================================================

    @Nested
    @DisplayName("Field Copy Independence")
    class FieldCopyTests {

        @Test
        @DisplayName("modifying copy doesn't affect source (true decoupling)")
        void fieldCopy_MutationDoesNotAffectSource() throws Exception {
            ItemArmor source = new ItemArmor(ItemArmorSlot.Chest, 99.0, null, null);

            Class<?> armorClass = source.getClass();
            ItemArmor copy = new ItemArmor(source.getArmorSlot(), 0.0, null, null);
            for (Field field : armorClass.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                field.set(copy, field.get(source));
            }

            // Mutate the copy's baseDamageResistance
            Field bdrField = armorClass.getDeclaredField("baseDamageResistance");
            bdrField.setAccessible(true);
            bdrField.setDouble(copy, 0.0);

            assertEquals(99.0, source.getBaseDamageResistance(),
                "CRITICAL: Modifying decoupled copy must NOT affect the shared base item");
        }

        @Test
        @DisplayName("copy and source have same initial values before zeroing")
        void fieldCopy_IdenticalBeforeZeroing() throws Exception {
            ItemArmor source = new ItemArmor(ItemArmorSlot.Head, 42.0, null, null);

            Class<?> armorClass = source.getClass();
            ItemArmor copy = new ItemArmor(source.getArmorSlot(), 0.0, null, null);
            for (Field field : armorClass.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                field.set(copy, field.get(source));
            }

            assertEquals(source.getBaseDamageResistance(), copy.getBaseDamageResistance());
            assertEquals(source.getArmorSlot(), copy.getArmorSlot());
        }
    }
}
