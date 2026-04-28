package io.github.larsonix.trailoforbis.gear.equipment;

import io.github.larsonix.trailoforbis.gear.equipment.EquipmentValidator.ValidationResult;
import io.github.larsonix.trailoforbis.gear.util.EquipmentSectionIds;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.SlotFilter;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.hypixel.hytale.component.ComponentType;
import org.mockito.MockedStatic;

/**
 * Tests for EquipmentListener.
 */
class EquipmentListenerTest {

    // Mocks
    private EquipmentValidator validator;
    private EquipmentFeedback feedback;

    // Test instance
    private EquipmentListener listener;

    // Test data
    private static final UUID PLAYER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        validator = mock(EquipmentValidator.class);
        feedback = mock(EquipmentFeedback.class);

        listener = new EquipmentListener(validator, feedback);
    }

    @Nested
    @DisplayName("validateSlotSwitch")
    class ValidateSlotSwitch {

        @Test
        @DisplayName("Allows non-hotbar and non-utility sections")
        void nonGearSection_allowed() {
            Player player = mock(Player.class);
            boolean result = listener.validateSlotSwitch(player, -999, (byte) 0);
            assertTrue(result);
        }

        @Test
        @DisplayName("Allows switch to empty slot")
        void emptySlot_allowed() {
            // Setup mocks - no PlayerRef needed since empty slot returns early
            Player player = mock(Player.class);
            Inventory inventory = mock(Inventory.class);
            ItemContainer hotbar = mock(ItemContainer.class);

            when(player.getInventory()).thenReturn(inventory);
            when(inventory.getHotbar()).thenReturn(hotbar);
            when(hotbar.getCapacity()).thenReturn((short) 10);
            when(hotbar.getItemStack((short) 0)).thenReturn(null);  // Empty slot

            boolean result = listener.validateSlotSwitch(player, EquipmentSectionIds.HOTBAR, (byte) 0);
            assertTrue(result);
        }

        @Test
        @DisplayName("Allows switch when requirements met")
        @SuppressWarnings("unchecked")
        void requirementsMet_allowed() {
            // Setup mocks
            Player player = mock(Player.class);
            Inventory inventory = mock(Inventory.class);
            ItemContainer hotbar = mock(ItemContainer.class);
            PlayerRef playerRef = mock(PlayerRef.class);
            ItemStack item = mock(ItemStack.class);
            Ref<EntityStore> ref = mock(Ref.class);
            Store<EntityStore> store = mock(Store.class);
            ComponentType<EntityStore, PlayerRef> componentType = mock(ComponentType.class);

            // Mock the static method PlayerRef.getComponentType()
            try (MockedStatic<PlayerRef> playerRefStatic = mockStatic(PlayerRef.class)) {
                playerRefStatic.when(PlayerRef::getComponentType).thenReturn(componentType);

                when(player.getInventory()).thenReturn(inventory);
                // Mock ECS component lookup pattern (future-proof)
                when(player.getReference()).thenReturn(ref);
                when(ref.isValid()).thenReturn(true);
                when(ref.getStore()).thenReturn(store);
                when(store.getComponent(ref, componentType)).thenReturn(playerRef);
                when(playerRef.getUuid()).thenReturn(PLAYER_ID);
                when(inventory.getHotbar()).thenReturn(hotbar);
                when(hotbar.getCapacity()).thenReturn((short) 10);
                when(hotbar.getItemStack((short) 1)).thenReturn(item);
                when(item.isEmpty()).thenReturn(false);

                // Player meets requirements
                ValidationResult validationResult = new ValidationResult(true, java.util.Map.of());
                when(validator.checkRequirements(eq(PLAYER_ID), eq(item))).thenReturn(validationResult);

                boolean result = listener.validateSlotSwitch(player, EquipmentSectionIds.HOTBAR, (byte) 1);
                assertTrue(result);
            }
        }

        @Test
        @DisplayName("Blocks switch when requirements not met")
        @SuppressWarnings("unchecked")
        void requirementsNotMet_blocked() {
            // Setup mocks
            Player player = mock(Player.class);
            Inventory inventory = mock(Inventory.class);
            ItemContainer hotbar = mock(ItemContainer.class);
            PlayerRef playerRef = mock(PlayerRef.class);
            ItemStack item = mock(ItemStack.class);
            Ref<EntityStore> ref = mock(Ref.class);
            Store<EntityStore> store = mock(Store.class);
            ComponentType<EntityStore, PlayerRef> componentType = mock(ComponentType.class);

            // Mock the static method PlayerRef.getComponentType()
            try (MockedStatic<PlayerRef> playerRefStatic = mockStatic(PlayerRef.class)) {
                playerRefStatic.when(PlayerRef::getComponentType).thenReturn(componentType);

                when(player.getInventory()).thenReturn(inventory);
                // Mock ECS component lookup pattern
                when(player.getReference()).thenReturn(ref);
                when(ref.isValid()).thenReturn(true);
                when(ref.getStore()).thenReturn(store);
                when(store.getComponent(ref, componentType)).thenReturn(playerRef);
                when(playerRef.getUuid()).thenReturn(PLAYER_ID);
                when(inventory.getHotbar()).thenReturn(hotbar);
                when(hotbar.getCapacity()).thenReturn((short) 10);
                when(hotbar.getItemStack((short) 2)).thenReturn(item);
                when(item.isEmpty()).thenReturn(false);

                // Player does NOT meet requirements
                ValidationResult validationResult = new ValidationResult(false, java.util.Map.of());
                when(validator.checkRequirements(eq(PLAYER_ID), eq(item))).thenReturn(validationResult);

                boolean result = listener.validateSlotSwitch(player, EquipmentSectionIds.HOTBAR, (byte) 2);
                assertFalse(result);
                verify(feedback).sendRequirementsNotMet(eq(playerRef), eq("weapon"), eq(validationResult));
            }
        }

        @Test
        @DisplayName("Handles utility section correctly")
        @SuppressWarnings("unchecked")
        void utilitySection_handledCorrectly() {
            // Setup mocks
            Player player = mock(Player.class);
            Inventory inventory = mock(Inventory.class);
            ItemContainer utility = mock(ItemContainer.class);
            PlayerRef playerRef = mock(PlayerRef.class);
            ItemStack item = mock(ItemStack.class);
            Ref<EntityStore> ref = mock(Ref.class);
            Store<EntityStore> store = mock(Store.class);
            ComponentType<EntityStore, PlayerRef> componentType = mock(ComponentType.class);

            // Mock the static method PlayerRef.getComponentType()
            try (MockedStatic<PlayerRef> playerRefStatic = mockStatic(PlayerRef.class)) {
                playerRefStatic.when(PlayerRef::getComponentType).thenReturn(componentType);

                when(player.getInventory()).thenReturn(inventory);
                // Mock ECS component lookup pattern
                when(player.getReference()).thenReturn(ref);
                when(ref.isValid()).thenReturn(true);
                when(ref.getStore()).thenReturn(store);
                when(store.getComponent(ref, componentType)).thenReturn(playerRef);
                when(playerRef.getUuid()).thenReturn(PLAYER_ID);
                when(inventory.getUtility()).thenReturn(utility);
                when(utility.getCapacity()).thenReturn((short) 4);
                when(utility.getItemStack((short) 0)).thenReturn(item);
                when(item.isEmpty()).thenReturn(false);

                // Player does NOT meet requirements
                ValidationResult validationResult = new ValidationResult(false, java.util.Map.of());
                when(validator.checkRequirements(eq(PLAYER_ID), eq(item))).thenReturn(validationResult);

                boolean result = listener.validateSlotSwitch(player, EquipmentSectionIds.UTILITY, (byte) 0);
                assertFalse(result);
                verify(feedback).sendRequirementsNotMet(eq(playerRef), eq("utility"), eq(validationResult));
            }
        }
    }

    // Note: createArmorValidationFilter was replaced by createCompositeArmorFilter
    // which is private. Filter behavior is now tested through setupArmorValidation
    // integration tests or by testing the EquipmentValidator directly.

    @Nested
    @DisplayName("EquipmentSectionIds")
    class EquipmentSectionIdsTests {

        @Test
        @DisplayName("HOTBAR constant is correct")
        void hotbarConstant() {
            assertEquals(-1, EquipmentSectionIds.HOTBAR);
        }

        @Test
        @DisplayName("ARMOR constant is correct")
        void armorConstant() {
            assertEquals(-3, EquipmentSectionIds.ARMOR);
        }

        @Test
        @DisplayName("UTILITY constant is correct")
        void utilityConstant() {
            assertEquals(-5, EquipmentSectionIds.UTILITY);
        }

        @Test
        @DisplayName("isGearSection returns true for gear sections")
        void isGearSection_true() {
            assertTrue(EquipmentSectionIds.isGearSection(EquipmentSectionIds.HOTBAR));
            assertTrue(EquipmentSectionIds.isGearSection(EquipmentSectionIds.ARMOR));
            assertTrue(EquipmentSectionIds.isGearSection(EquipmentSectionIds.UTILITY));
        }

        @Test
        @DisplayName("isGearSection returns false for other sections")
        void isGearSection_false() {
            assertFalse(EquipmentSectionIds.isGearSection(0));
            assertFalse(EquipmentSectionIds.isGearSection(1));
            assertFalse(EquipmentSectionIds.isGearSection(-999));
        }

        @Test
        @DisplayName("getSectionName returns correct names")
        void getSectionName() {
            assertEquals("Hotbar", EquipmentSectionIds.getSectionName(EquipmentSectionIds.HOTBAR));
            assertEquals("Armor", EquipmentSectionIds.getSectionName(EquipmentSectionIds.ARMOR));
            assertEquals("Utility", EquipmentSectionIds.getSectionName(EquipmentSectionIds.UTILITY));
        }

        @Test
        @DisplayName("getSectionName returns Unknown for other sections")
        void getSectionName_unknown() {
            String name = EquipmentSectionIds.getSectionName(-999);
            assertTrue(name.startsWith("Unknown"));
        }
    }
}
