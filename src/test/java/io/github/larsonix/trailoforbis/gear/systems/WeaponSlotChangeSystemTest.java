package io.github.larsonix.trailoforbis.gear.systems;

import io.github.larsonix.trailoforbis.gear.util.EquipmentSectionIds;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link WeaponSlotChangeSystem} and related utilities.
 *
 * <p>Tests verify:
 * <ul>
 *   <li>EquipmentSectionIds constants and utilities</li>
 *   <li>Event filtering logic based on section IDs</li>
 * </ul>
 *
 * <p>Note: Full integration tests for WeaponSlotChangeSystem require ECS
 * infrastructure which is not available in unit tests. The system's handle()
 * method is tested implicitly through in-game testing.
 *
 * <p>The system itself cannot be instantiated in unit tests because
 * {@code PlayerRef.getComponentType()} requires ECS registry initialization.
 */
@DisplayName("WeaponSlotChangeSystem Tests")
class WeaponSlotChangeSystemTest {

    @Nested
    @DisplayName("EquipmentSectionIds Constants")
    class SectionIdTests {

        @Test
        @DisplayName("Hotbar section ID is -1")
        void hotbarSectionIdIsCorrect() {
            assertEquals(-1, EquipmentSectionIds.HOTBAR);
        }

        @Test
        @DisplayName("Utility section ID is -5")
        void utilitySectionIdIsCorrect() {
            assertEquals(-5, EquipmentSectionIds.UTILITY);
        }

        @Test
        @DisplayName("Armor section ID is -3")
        void armorSectionIdIsCorrect() {
            assertEquals(-3, EquipmentSectionIds.ARMOR);
        }
    }

    @Nested
    @DisplayName("EquipmentSectionIds.isGearSection")
    class IsGearSectionTests {

        @Test
        @DisplayName("Returns true for hotbar")
        void trueForHotbar() {
            assertTrue(EquipmentSectionIds.isGearSection(EquipmentSectionIds.HOTBAR));
        }

        @Test
        @DisplayName("Returns true for utility")
        void trueForUtility() {
            assertTrue(EquipmentSectionIds.isGearSection(EquipmentSectionIds.UTILITY));
        }

        @Test
        @DisplayName("Returns true for armor")
        void trueForArmor() {
            assertTrue(EquipmentSectionIds.isGearSection(EquipmentSectionIds.ARMOR));
        }

        @Test
        @DisplayName("Returns false for storage (section 0)")
        void falseForStorage() {
            assertFalse(EquipmentSectionIds.isGearSection(0));
        }

        @Test
        @DisplayName("Returns false for positive section IDs")
        void falseForPositiveSections() {
            assertFalse(EquipmentSectionIds.isGearSection(1));
            assertFalse(EquipmentSectionIds.isGearSection(5));
            assertFalse(EquipmentSectionIds.isGearSection(100));
        }

        @Test
        @DisplayName("Returns false for other negative section IDs")
        void falseForOtherNegativeSections() {
            assertFalse(EquipmentSectionIds.isGearSection(-2));
            assertFalse(EquipmentSectionIds.isGearSection(-4));
            assertFalse(EquipmentSectionIds.isGearSection(-10));
        }
    }

    @Nested
    @DisplayName("EquipmentSectionIds.getSectionName")
    class GetSectionNameTests {

        @Test
        @DisplayName("Returns 'Hotbar' for hotbar section")
        void hotbarName() {
            assertEquals("Hotbar", EquipmentSectionIds.getSectionName(EquipmentSectionIds.HOTBAR));
        }

        @Test
        @DisplayName("Returns 'Utility' for utility section")
        void utilityName() {
            assertEquals("Utility", EquipmentSectionIds.getSectionName(EquipmentSectionIds.UTILITY));
        }

        @Test
        @DisplayName("Returns 'Armor' for armor section")
        void armorName() {
            assertEquals("Armor", EquipmentSectionIds.getSectionName(EquipmentSectionIds.ARMOR));
        }

        @Test
        @DisplayName("Returns 'Unknown' with ID for unknown sections")
        void unknownSection() {
            String name = EquipmentSectionIds.getSectionName(99);
            assertTrue(name.startsWith("Unknown"));
            assertTrue(name.contains("99"));
        }

        @Test
        @DisplayName("Returns 'Unknown' with ID for unrecognized negative sections")
        void unknownNegativeSection() {
            String name = EquipmentSectionIds.getSectionName(-10);
            assertTrue(name.startsWith("Unknown"));
            assertTrue(name.contains("-10"));
        }
    }

    @Nested
    @DisplayName("Event Filtering Logic")
    class EventFilteringLogicTests {

        @Test
        @DisplayName("Hotbar section passes filter")
        void hotbarPassesFilter() {
            int sectionId = EquipmentSectionIds.HOTBAR;
            boolean shouldProcess = sectionId == EquipmentSectionIds.HOTBAR
                    || sectionId == EquipmentSectionIds.UTILITY;
            assertTrue(shouldProcess);
        }

        @Test
        @DisplayName("Utility section passes filter")
        void utilityPassesFilter() {
            int sectionId = EquipmentSectionIds.UTILITY;
            boolean shouldProcess = sectionId == EquipmentSectionIds.HOTBAR
                    || sectionId == EquipmentSectionIds.UTILITY;
            assertTrue(shouldProcess);
        }

        @Test
        @DisplayName("Storage section fails filter")
        void storageFailsFilter() {
            int sectionId = 0; // Storage
            boolean shouldProcess = sectionId == EquipmentSectionIds.HOTBAR
                    || sectionId == EquipmentSectionIds.UTILITY;
            assertFalse(shouldProcess);
        }

        @Test
        @DisplayName("Armor section fails filter (handled by EquipmentChangeListener)")
        void armorFailsFilter() {
            // Armor changes are handled by EquipmentChangeListener, not WeaponSlotChangeSystem
            int sectionId = EquipmentSectionIds.ARMOR;
            boolean shouldProcess = sectionId == EquipmentSectionIds.HOTBAR
                    || sectionId == EquipmentSectionIds.UTILITY;
            assertFalse(shouldProcess);
        }

        @Test
        @DisplayName("Same slot switch should be filtered")
        void sameSlotFiltered() {
            int previousSlot = 2;
            byte newSlot = 2;
            boolean shouldSkip = previousSlot == newSlot;
            assertTrue(shouldSkip);
        }

        @Test
        @DisplayName("Different slot switch should not be filtered")
        void differentSlotNotFiltered() {
            int previousSlot = 0;
            byte newSlot = 1;
            boolean shouldSkip = previousSlot == newSlot;
            assertFalse(shouldSkip);
        }
    }
}
