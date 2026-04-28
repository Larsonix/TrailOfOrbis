package io.github.larsonix.trailoforbis.stones;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link StoneUtils}.
 *
 * <p>Tests the ItemStack serialization and deserialization of stone data.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StoneUtils")
class StoneUtilsTest {

    @Mock
    private ItemStack mockItemStack;

    @Mock
    private ItemStack mockResultStack;

    // =========================================================================
    // IS_STONE TESTS
    // =========================================================================

    @Nested
    @DisplayName("isStone")
    class IsStoneTests {

        @Test
        @DisplayName("should return false for null ItemStack")
        void isStone_ReturnsFalse_ForNullItemStack() {
            assertFalse(StoneUtils.isStone(null));
        }

        @Test
        @DisplayName("should return false when marker key is missing and item ID is not a stone")
        void isStone_ReturnsFalse_WhenMarkerMissing() {
            when(mockItemStack.isEmpty()).thenReturn(false);
            when(mockItemStack.getFromMetadataOrNull(eq(StoneUtils.KEY_IS_STONE), any(Codec.class)))
                .thenReturn(null);
            when(mockItemStack.getItemId()).thenReturn("Weapon_Sword_Iron");

            assertFalse(StoneUtils.isStone(mockItemStack));
        }

        @Test
        @DisplayName("should return false when marker key is false and item ID is not a stone")
        void isStone_ReturnsFalse_WhenMarkerFalse() {
            when(mockItemStack.isEmpty()).thenReturn(false);
            when(mockItemStack.getFromMetadataOrNull(eq(StoneUtils.KEY_IS_STONE), any(Codec.class)))
                .thenReturn(false);
            when(mockItemStack.getItemId()).thenReturn("Weapon_Sword_Iron");

            assertFalse(StoneUtils.isStone(mockItemStack));
        }

        @Test
        @DisplayName("should return true when marker key is true")
        void isStone_ReturnsTrue_WhenMarkerTrue() {
            when(mockItemStack.isEmpty()).thenReturn(false);
            when(mockItemStack.getFromMetadataOrNull(eq(StoneUtils.KEY_IS_STONE), any(Codec.class)))
                .thenReturn(true);

            assertTrue(StoneUtils.isStone(mockItemStack));
        }

        @Test
        @DisplayName("should return false for empty ItemStack")
        void isStone_ReturnsFalse_ForEmptyItemStack() {
            when(mockItemStack.isEmpty()).thenReturn(true);

            assertFalse(StoneUtils.isStone(mockItemStack));
        }

        @Test
        @DisplayName("should return true for native stone item ID even without metadata")
        void isStone_ReturnsTrue_ForNativeStoneId() {
            when(mockItemStack.isEmpty()).thenReturn(false);
            when(mockItemStack.getFromMetadataOrNull(eq(StoneUtils.KEY_IS_STONE), any(Codec.class)))
                .thenReturn(null);
            when(mockItemStack.getItemId()).thenReturn("RPG_Stone_Gaias_Calibration");

            assertTrue(StoneUtils.isStone(mockItemStack));
        }
    }

    // =========================================================================
    // READ_STONE_TYPE TESTS
    // =========================================================================

    @Nested
    @DisplayName("readStoneType")
    class ReadStoneTypeTests {

        @Test
        @DisplayName("should return empty for null ItemStack")
        void readStoneType_ReturnsEmpty_ForNull() {
            Optional<StoneType> result = StoneUtils.readStoneType(null);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should return empty when not a stone")
        void readStoneType_ReturnsEmpty_ForNonStone() {
            when(mockItemStack.isEmpty()).thenReturn(false);
            when(mockItemStack.getFromMetadataOrNull(eq(StoneUtils.KEY_STONE_TYPE), any(Codec.class)))
                .thenReturn(null);
            when(mockItemStack.getItemId()).thenReturn("Weapon_Sword_Iron");

            Optional<StoneType> result = StoneUtils.readStoneType(mockItemStack);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should return correct type for stone")
        void readStoneType_ReturnsCorrectType() {
            when(mockItemStack.isEmpty()).thenReturn(false);
            when(mockItemStack.getFromMetadataOrNull(eq(StoneUtils.KEY_STONE_TYPE), any(Codec.class)))
                .thenReturn("ALTERVERSE_SHARD");

            Optional<StoneType> result = StoneUtils.readStoneType(mockItemStack);

            assertTrue(result.isPresent());
            assertEquals(StoneType.ALTERVERSE_SHARD, result.get());
        }

        @Test
        @DisplayName("should return empty for corrupted metadata")
        void readStoneType_ReturnsEmpty_ForCorruptedMetadata() {
            when(mockItemStack.isEmpty()).thenReturn(false);
            when(mockItemStack.getFromMetadataOrNull(eq(StoneUtils.KEY_STONE_TYPE), any(Codec.class)))
                .thenReturn("INVALID_TYPE");
            when(mockItemStack.getItemId()).thenReturn("Weapon_Sword_Iron");

            Optional<StoneType> result = StoneUtils.readStoneType(mockItemStack);
            assertTrue(result.isEmpty(), "Should return empty for invalid type name");
        }

        @Test
        @DisplayName("should return empty when type is null")
        void readStoneType_ReturnsEmpty_WhenTypeNull() {
            when(mockItemStack.isEmpty()).thenReturn(false);
            when(mockItemStack.getFromMetadataOrNull(eq(StoneUtils.KEY_STONE_TYPE), any(Codec.class)))
                .thenReturn(null);
            when(mockItemStack.getItemId()).thenReturn("Weapon_Sword_Iron");

            Optional<StoneType> result = StoneUtils.readStoneType(mockItemStack);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should return empty when type is empty string")
        void readStoneType_ReturnsEmpty_WhenTypeEmpty() {
            when(mockItemStack.isEmpty()).thenReturn(false);
            when(mockItemStack.getFromMetadataOrNull(eq(StoneUtils.KEY_STONE_TYPE), any(Codec.class)))
                .thenReturn("");
            when(mockItemStack.getItemId()).thenReturn("Weapon_Sword_Iron");

            Optional<StoneType> result = StoneUtils.readStoneType(mockItemStack);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should resolve type from native item ID when metadata is missing")
        void readStoneType_ResolvesFromNativeId() {
            when(mockItemStack.isEmpty()).thenReturn(false);
            when(mockItemStack.getFromMetadataOrNull(eq(StoneUtils.KEY_STONE_TYPE), any(Codec.class)))
                .thenReturn(null);
            when(mockItemStack.getItemId()).thenReturn("RPG_Stone_Gaias_Calibration");

            Optional<StoneType> result = StoneUtils.readStoneType(mockItemStack);

            assertTrue(result.isPresent());
            assertEquals(StoneType.GAIAS_CALIBRATION, result.get());
        }
    }

    // =========================================================================
    // READ_STONE_TYPE_OR_THROW TESTS
    // =========================================================================

    @Nested
    @DisplayName("readStoneTypeOrThrow")
    class ReadStoneTypeOrThrowTests {

        @Test
        @DisplayName("should return stone type when valid")
        void readStoneTypeOrThrow_ReturnsType_WhenValid() {
            when(mockItemStack.isEmpty()).thenReturn(false);
            when(mockItemStack.getFromMetadataOrNull(eq(StoneUtils.KEY_STONE_TYPE), any(Codec.class)))
                .thenReturn("GAIAS_CALIBRATION");

            StoneType result = StoneUtils.readStoneTypeOrThrow(mockItemStack);

            assertEquals(StoneType.GAIAS_CALIBRATION, result);
        }

        @Test
        @DisplayName("should throw for non-stone")
        void readStoneTypeOrThrow_Throws_ForNonStone() {
            when(mockItemStack.isEmpty()).thenReturn(false);
            when(mockItemStack.getFromMetadataOrNull(eq(StoneUtils.KEY_STONE_TYPE), any(Codec.class)))
                .thenReturn(null);
            when(mockItemStack.getItemId()).thenReturn("Weapon_Sword_Iron");

            assertThrows(IllegalArgumentException.class, () ->
                StoneUtils.readStoneTypeOrThrow(mockItemStack));
        }
    }

    // =========================================================================
    // WRITE_STONE_TYPE TESTS
    // =========================================================================

    @Nested
    @DisplayName("writeStoneType")
    class WriteStoneTypeTests {

        @Test
        @DisplayName("should write stone marker and type")
        void writeStoneType_WritesMarkerAndType() {
            when(mockItemStack.withMetadata(eq(StoneUtils.KEY_IS_STONE), any(Codec.class), eq(true)))
                .thenReturn(mockResultStack);
            when(mockResultStack.withMetadata(eq(StoneUtils.KEY_STONE_TYPE), any(Codec.class), eq("VARYNS_TOUCH")))
                .thenReturn(mockResultStack);

            ItemStack result = StoneUtils.writeStoneType(mockItemStack, StoneType.VARYNS_TOUCH);

            assertNotNull(result);
            verify(mockItemStack).withMetadata(eq(StoneUtils.KEY_IS_STONE), any(Codec.class), eq(true));
            verify(mockResultStack).withMetadata(eq(StoneUtils.KEY_STONE_TYPE), any(Codec.class), eq("VARYNS_TOUCH"));
        }

        @Test
        @DisplayName("should throw on null itemStack")
        void writeStoneType_ThrowsOnNullItemStack() {
            assertThrows(NullPointerException.class, () ->
                StoneUtils.writeStoneType(null, StoneType.GAIAS_CALIBRATION));
        }

        @Test
        @DisplayName("should throw on null stoneType")
        void writeStoneType_ThrowsOnNullStoneType() {
            assertThrows(NullPointerException.class, () ->
                StoneUtils.writeStoneType(mockItemStack, null));
        }
    }

    // =========================================================================
    // CREATE_STONE_ITEM TESTS
    // =========================================================================

    @Nested
    @DisplayName("createStoneItem")
    class CreateStoneItemTests {

        @Test
        @DisplayName("should throw on null stoneType")
        void createStoneItem_ThrowsOnNullStoneType() {
            assertThrows(NullPointerException.class, () ->
                StoneUtils.createStoneItem(null));
        }

        @Test
        @DisplayName("should throw on invalid count (0)")
        void createStoneItem_ThrowsOnZeroCount() {
            assertThrows(IllegalArgumentException.class, () ->
                StoneUtils.createStoneItem(StoneType.GAIAS_CALIBRATION, 0));
        }

        @Test
        @DisplayName("should throw on invalid count (101)")
        void createStoneItem_ThrowsOnTooHighCount() {
            assertThrows(IllegalArgumentException.class, () ->
                StoneUtils.createStoneItem(StoneType.GAIAS_CALIBRATION, 101));
        }
    }

    // =========================================================================
    // GET_DISPLAY_NAME TESTS
    // =========================================================================

    @Nested
    @DisplayName("getDisplayName")
    class GetDisplayNameTests {

        @Test
        @DisplayName("should return null for null ItemStack")
        void getDisplayName_ReturnsNull_ForNull() {
            assertNull(StoneUtils.getDisplayName(null));
        }

        @Test
        @DisplayName("should return null for non-stone")
        void getDisplayName_ReturnsNull_ForNonStone() {
            when(mockItemStack.isEmpty()).thenReturn(false);
            when(mockItemStack.getFromMetadataOrNull(eq(StoneUtils.KEY_STONE_TYPE), any(Codec.class)))
                .thenReturn(null);
            when(mockItemStack.getItemId()).thenReturn("Weapon_Sword_Iron");

            assertNull(StoneUtils.getDisplayName(mockItemStack));
        }

        @Test
        @DisplayName("should return display name for stone")
        void getDisplayName_ReturnsName_ForStone() {
            when(mockItemStack.isEmpty()).thenReturn(false);
            when(mockItemStack.getFromMetadataOrNull(eq(StoneUtils.KEY_STONE_TYPE), any(Codec.class)))
                .thenReturn("GAIAS_CALIBRATION");

            String name = StoneUtils.getDisplayName(mockItemStack);

            assertEquals("Gaia's Calibration", name);
        }
    }

    // =========================================================================
    // GET_DESCRIPTION TESTS
    // =========================================================================

    @Nested
    @DisplayName("getDescription")
    class GetDescriptionTests {

        @Test
        @DisplayName("should return null for null ItemStack")
        void getDescription_ReturnsNull_ForNull() {
            assertNull(StoneUtils.getDescription(null));
        }

        @Test
        @DisplayName("should return null for non-stone")
        void getDescription_ReturnsNull_ForNonStone() {
            when(mockItemStack.isEmpty()).thenReturn(false);
            when(mockItemStack.getFromMetadataOrNull(eq(StoneUtils.KEY_STONE_TYPE), any(Codec.class)))
                .thenReturn(null);
            when(mockItemStack.getItemId()).thenReturn("Weapon_Sword_Iron");

            assertNull(StoneUtils.getDescription(mockItemStack));
        }

        @Test
        @DisplayName("should return description for stone")
        void getDescription_ReturnsDescription_ForStone() {
            when(mockItemStack.isEmpty()).thenReturn(false);
            when(mockItemStack.getFromMetadataOrNull(eq(StoneUtils.KEY_STONE_TYPE), any(Codec.class)))
                .thenReturn("VARYNS_TOUCH");

            String desc = StoneUtils.getDescription(mockItemStack);

            assertNotNull(desc);
            assertTrue(desc.contains("Corrupts"), "Description should mention corruption");
        }
    }

    // =========================================================================
    // IS_SAME_STONE_TYPE TESTS
    // =========================================================================

    @Nested
    @DisplayName("isSameStoneType")
    class IsSameStoneTypeTests {

        @Mock
        private ItemStack mockOtherStack;

        @Test
        @DisplayName("should return false when first is null")
        void isSameStoneType_ReturnsFalse_WhenFirstNull() {
            assertFalse(StoneUtils.isSameStoneType(null, mockItemStack));
        }

        @Test
        @DisplayName("should return false when second is null")
        void isSameStoneType_ReturnsFalse_WhenSecondNull() {
            assertFalse(StoneUtils.isSameStoneType(mockItemStack, null));
        }

        @Test
        @DisplayName("should return false when both are null")
        void isSameStoneType_ReturnsFalse_WhenBothNull() {
            assertFalse(StoneUtils.isSameStoneType(null, null));
        }

        @Test
        @DisplayName("should return false for non-stones")
        void isSameStoneType_ReturnsFalse_ForNonStones() {
            when(mockItemStack.isEmpty()).thenReturn(false);
            when(mockItemStack.getFromMetadataOrNull(eq(StoneUtils.KEY_STONE_TYPE), any(Codec.class)))
                .thenReturn(null);
            when(mockItemStack.getItemId()).thenReturn("Weapon_Sword_Iron");

            when(mockOtherStack.isEmpty()).thenReturn(false);
            when(mockOtherStack.getFromMetadataOrNull(eq(StoneUtils.KEY_STONE_TYPE), any(Codec.class)))
                .thenReturn(null);
            when(mockOtherStack.getItemId()).thenReturn("Weapon_Axe_Iron");

            assertFalse(StoneUtils.isSameStoneType(mockItemStack, mockOtherStack));
        }

        @Test
        @DisplayName("should return false for different stone types")
        void isSameStoneType_ReturnsFalse_ForDifferentTypes() {
            when(mockItemStack.isEmpty()).thenReturn(false);
            when(mockItemStack.getFromMetadataOrNull(eq(StoneUtils.KEY_STONE_TYPE), any(Codec.class)))
                .thenReturn("GAIAS_CALIBRATION");

            when(mockOtherStack.isEmpty()).thenReturn(false);
            when(mockOtherStack.getFromMetadataOrNull(eq(StoneUtils.KEY_STONE_TYPE), any(Codec.class)))
                .thenReturn("ALTERVERSE_SHARD");

            assertFalse(StoneUtils.isSameStoneType(mockItemStack, mockOtherStack));
        }

        @Test
        @DisplayName("should return true for same stone type")
        void isSameStoneType_ReturnsTrue_ForSameType() {
            when(mockItemStack.isEmpty()).thenReturn(false);
            when(mockItemStack.getFromMetadataOrNull(eq(StoneUtils.KEY_STONE_TYPE), any(Codec.class)))
                .thenReturn("GAIAS_GIFT");

            when(mockOtherStack.isEmpty()).thenReturn(false);
            when(mockOtherStack.getFromMetadataOrNull(eq(StoneUtils.KEY_STONE_TYPE), any(Codec.class)))
                .thenReturn("GAIAS_GIFT");

            assertTrue(StoneUtils.isSameStoneType(mockItemStack, mockOtherStack));
        }
    }

    // =========================================================================
    // IS_NATIVE_STONE_ID TESTS
    // =========================================================================

    @Nested
    @DisplayName("isNativeStoneId")
    class IsNativeStoneIdTests {

        @Test
        @DisplayName("should return false for null")
        void isNativeStoneId_ReturnsFalse_ForNull() {
            assertFalse(StoneUtils.isNativeStoneId(null));
        }

        @Test
        @DisplayName("should return false for non-stone item ID")
        void isNativeStoneId_ReturnsFalse_ForNonStone() {
            assertFalse(StoneUtils.isNativeStoneId("Weapon_Sword_Iron"));
        }

        @Test
        @DisplayName("should return true for native stone item ID")
        void isNativeStoneId_ReturnsTrue_ForStoneId() {
            assertTrue(StoneUtils.isNativeStoneId("RPG_Stone_Gaias_Calibration"));
        }
    }
}
