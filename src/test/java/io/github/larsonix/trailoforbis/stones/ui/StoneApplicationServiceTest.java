package io.github.larsonix.trailoforbis.stones.ui;

import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.stones.StoneActionRegistry;
import io.github.larsonix.trailoforbis.stones.StoneType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link StoneApplicationService}.
 *
 * <p>Tests the stone application service, focusing on:
 * <ul>
 *   <li>Constructor validation</li>
 *   <li>ApplicationResult factory methods</li>
 *   <li>Basic validation (container/slot checks)</li>
 *   <li>Exception handling</li>
 * </ul>
 *
 * <p>Note: Full integration tests require static mocking of StoneUtils.isStone()
 * which is not done here. These tests focus on the validation paths that don't
 * require the static method check.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StoneApplicationService")
class StoneApplicationServiceTest {

    @Mock
    private StoneActionRegistry actionRegistry;

    @Mock
    private Inventory inventory;

    @Mock
    private ItemContainer stoneContainer;

    @Mock
    private ItemContainer targetContainer;

    @Mock
    private ItemStack stoneItem;

    @Mock
    private ItemStack targetItem;

    @Mock
    private GearData gearData;

    private StoneApplicationService service;

    @BeforeEach
    void setUp() {
        service = new StoneApplicationService(actionRegistry);
    }

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTOR TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("should throw NPE for null actionRegistry")
        void constructor_throwsNPE_forNullRegistry() {
            assertThrows(NullPointerException.class, () ->
                new StoneApplicationService(null));
        }

        @Test
        @DisplayName("should store action registry")
        void constructor_storesActionRegistry() {
            assertSame(actionRegistry, service.getActionRegistry());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // VALIDATION TESTS - STONE CONTAINER
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Stone Container Validation")
    class StoneContainerValidationTests {

        @Test
        @DisplayName("should fail when stone container is null")
        void apply_fails_whenStoneContainerNull() {
            when(inventory.getHotbar()).thenReturn(null);

            var result = service.apply(
                inventory, StoneType.GAIAS_GIFT, (short) 0, ContainerType.HOTBAR,
                targetItem, gearData, (short) 1, ContainerType.STORAGE
            );

            assertFalse(result.success());
            assertEquals("Cannot access stone container !", result.message());
        }

        @Test
        @DisplayName("should fail when stone is not in slot")
        void apply_fails_whenStoneNotInSlot() {
            when(inventory.getHotbar()).thenReturn(stoneContainer);
            when(stoneContainer.getItemStack((short) 0)).thenReturn(null);

            var result = service.apply(
                inventory, StoneType.GAIAS_GIFT, (short) 0, ContainerType.HOTBAR,
                targetItem, gearData, (short) 1, ContainerType.STORAGE
            );

            assertFalse(result.success());
            assertEquals("Stone no longer in inventory !", result.message());
        }

        @Test
        @DisplayName("should fail when stone slot is empty")
        void apply_fails_whenStoneSlotEmpty() {
            when(inventory.getHotbar()).thenReturn(stoneContainer);
            when(stoneContainer.getItemStack((short) 0)).thenReturn(stoneItem);
            when(stoneItem.isEmpty()).thenReturn(true);

            var result = service.apply(
                inventory, StoneType.GAIAS_GIFT, (short) 0, ContainerType.HOTBAR,
                targetItem, gearData, (short) 1, ContainerType.STORAGE
            );

            assertFalse(result.success());
            assertEquals("Stone no longer in inventory !", result.message());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // EXCEPTION HANDLING TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Exception Handling")
    class ExceptionHandlingTests {

        @Test
        @DisplayName("should return error result when exception thrown")
        void apply_returnsError_whenExceptionThrown() {
            when(inventory.getHotbar()).thenThrow(new RuntimeException("Test exception"));

            var result = service.apply(
                inventory, StoneType.GAIAS_GIFT, (short) 0, ContainerType.HOTBAR,
                targetItem, gearData, (short) 1, ContainerType.STORAGE
            );

            assertFalse(result.success());
            assertEquals("An error occurred !", result.message());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // APPLICATION RESULT TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ApplicationResult")
    class ApplicationResultTests {

        @Test
        @DisplayName("failure factory creates failure result")
        void failure_createsFailureResult() {
            var result = StoneApplicationService.ApplicationResult.failure("Test error");

            assertFalse(result.success());
            assertEquals("Test error", result.message());
            assertNull(result.updatedTargetItem());
        }

        @Test
        @DisplayName("success factory creates success result")
        void success_createsSuccessResult() {
            var result = StoneApplicationService.ApplicationResult.success("Success!", targetItem, null);

            assertTrue(result.success());
            assertEquals("Success!", result.message());
            assertSame(targetItem, result.updatedTargetItem());
            assertNull(result.modifiedData());
        }
    }

}
