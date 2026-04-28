package io.github.larsonix.trailoforbis.maps.items;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.maps.core.RealmBiomeType;
import io.github.larsonix.trailoforbis.maps.core.RealmLayoutShape;
import io.github.larsonix.trailoforbis.maps.core.RealmLayoutSize;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifier;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifierType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RealmMapUtils}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RealmMapUtils")
class RealmMapUtilsTest {

    @Mock
    private ItemStack mockItemStack;

    @Mock
    private ItemStack mockResultStack;

    private RealmMapData sampleMapData;

    @BeforeEach
    void setUp() {
        sampleMapData = RealmMapData.builder()
            .level(50)
            .rarity(GearRarity.EPIC)
            .quality(75)
            .biome(RealmBiomeType.VOLCANO)
            .size(RealmLayoutSize.LARGE)
            .shape(RealmLayoutShape.CIRCULAR)
            .addPrefix(new RealmModifier(RealmModifierType.MONSTER_HEALTH, 30, false))
            .addSuffix(new RealmModifier(RealmModifierType.EXPERIENCE_BONUS, 20, false))
            .identified(true)
            .corrupted(false)
            .build();
    }

    @Nested
    @DisplayName("isRealmMap")
    class IsRealmMapTests {

        @Test
        @DisplayName("Should return false for null ItemStack")
        void shouldReturnFalseForNull() {
            assertFalse(RealmMapUtils.isRealmMap(null));
        }

        @Test
        @DisplayName("Should return false when marker key is missing")
        void shouldReturnFalseWhenMarkerMissing() {
            when(mockItemStack.getFromMetadataOrNull(eq(RealmMapUtils.KEY_IS_MAP), any(Codec.class)))
                .thenReturn(null);

            assertFalse(RealmMapUtils.isRealmMap(mockItemStack));
        }

        @Test
        @DisplayName("Should return false when marker key is false")
        void shouldReturnFalseWhenMarkerFalse() {
            when(mockItemStack.getFromMetadataOrNull(eq(RealmMapUtils.KEY_IS_MAP), any(Codec.class)))
                .thenReturn(false);

            assertFalse(RealmMapUtils.isRealmMap(mockItemStack));
        }

        @Test
        @DisplayName("Should return true when marker key is true")
        void shouldReturnTrueWhenMarkerTrue() {
            when(mockItemStack.getFromMetadataOrNull(eq(RealmMapUtils.KEY_IS_MAP), any(Codec.class)))
                .thenReturn(true);

            assertTrue(RealmMapUtils.isRealmMap(mockItemStack));
        }
    }

    @Nested
    @DisplayName("readMapData")
    class ReadMapDataTests {

        @Test
        @DisplayName("Should return empty for null ItemStack")
        void shouldReturnEmptyForNull() {
            Optional<RealmMapData> result = RealmMapUtils.readMapData(null);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should return empty when not a realm map")
        void shouldReturnEmptyWhenNotRealmMap() {
            when(mockItemStack.getFromMetadataOrNull(eq(RealmMapUtils.KEY_IS_MAP), any(Codec.class)))
                .thenReturn(null);

            Optional<RealmMapData> result = RealmMapUtils.readMapData(mockItemStack);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should return map data when present")
        void shouldReturnMapDataWhenPresent() {
            when(mockItemStack.getFromMetadataOrNull(eq(RealmMapUtils.KEY_IS_MAP), any(Codec.class)))
                .thenReturn(true);
            when(mockItemStack.getFromMetadataOrNull(eq(RealmMapUtils.KEY_MAP_DATA), any(Codec.class)))
                .thenReturn(sampleMapData);

            Optional<RealmMapData> result = RealmMapUtils.readMapData(mockItemStack);

            assertTrue(result.isPresent());
            assertEquals(50, result.get().level());
            assertEquals(GearRarity.EPIC, result.get().rarity());
            assertEquals(RealmBiomeType.VOLCANO, result.get().biome());
        }

        @Test
        @DisplayName("Should return empty when map data is null")
        void shouldReturnEmptyWhenMapDataNull() {
            when(mockItemStack.getFromMetadataOrNull(eq(RealmMapUtils.KEY_IS_MAP), any(Codec.class)))
                .thenReturn(true);
            when(mockItemStack.getFromMetadataOrNull(eq(RealmMapUtils.KEY_MAP_DATA), any(Codec.class)))
                .thenReturn(null);

            Optional<RealmMapData> result = RealmMapUtils.readMapData(mockItemStack);
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("getLevel")
    class GetLevelTests {

        @Test
        @DisplayName("Should return 1 for null ItemStack")
        void shouldReturnOneForNull() {
            assertEquals(1, RealmMapUtils.getLevel(null));
        }

        @Test
        @DisplayName("Should return 1 when not a realm map")
        void shouldReturnOneWhenNotRealmMap() {
            when(mockItemStack.getFromMetadataOrNull(eq(RealmMapUtils.KEY_IS_MAP), any(Codec.class)))
                .thenReturn(null);

            assertEquals(1, RealmMapUtils.getLevel(mockItemStack));
        }

        @Test
        @DisplayName("Should return map level when present")
        void shouldReturnMapLevel() {
            when(mockItemStack.getFromMetadataOrNull(eq(RealmMapUtils.KEY_IS_MAP), any(Codec.class)))
                .thenReturn(true);
            when(mockItemStack.getFromMetadataOrNull(eq(RealmMapUtils.KEY_MAP_DATA), any(Codec.class)))
                .thenReturn(sampleMapData);

            assertEquals(50, RealmMapUtils.getLevel(mockItemStack));
        }
    }

    @Nested
    @DisplayName("getRarity")
    class GetRarityTests {

        @Test
        @DisplayName("Should return empty for null ItemStack")
        void shouldReturnEmptyForNull() {
            assertTrue(RealmMapUtils.getRarity(null).isEmpty());
        }

        @Test
        @DisplayName("Should return rarity when present")
        void shouldReturnRarity() {
            when(mockItemStack.getFromMetadataOrNull(eq(RealmMapUtils.KEY_IS_MAP), any(Codec.class)))
                .thenReturn(true);
            when(mockItemStack.getFromMetadataOrNull(eq(RealmMapUtils.KEY_MAP_DATA), any(Codec.class)))
                .thenReturn(sampleMapData);

            Optional<GearRarity> result = RealmMapUtils.getRarity(mockItemStack);
            assertTrue(result.isPresent());
            assertEquals(GearRarity.EPIC, result.get());
        }
    }

    @Nested
    @DisplayName("getBiome")
    class GetBiomeTests {

        @Test
        @DisplayName("Should return empty for null ItemStack")
        void shouldReturnEmptyForNull() {
            assertTrue(RealmMapUtils.getBiome(null).isEmpty());
        }

        @Test
        @DisplayName("Should return biome when present")
        void shouldReturnBiome() {
            when(mockItemStack.getFromMetadataOrNull(eq(RealmMapUtils.KEY_IS_MAP), any(Codec.class)))
                .thenReturn(true);
            when(mockItemStack.getFromMetadataOrNull(eq(RealmMapUtils.KEY_MAP_DATA), any(Codec.class)))
                .thenReturn(sampleMapData);

            Optional<RealmBiomeType> result = RealmMapUtils.getBiome(mockItemStack);
            assertTrue(result.isPresent());
            assertEquals(RealmBiomeType.VOLCANO, result.get());
        }
    }

    @Nested
    @DisplayName("getSize")
    class GetSizeTests {

        @Test
        @DisplayName("Should return empty for null ItemStack")
        void shouldReturnEmptyForNull() {
            assertTrue(RealmMapUtils.getSize(null).isEmpty());
        }

        @Test
        @DisplayName("Should return size when present")
        void shouldReturnSize() {
            when(mockItemStack.getFromMetadataOrNull(eq(RealmMapUtils.KEY_IS_MAP), any(Codec.class)))
                .thenReturn(true);
            when(mockItemStack.getFromMetadataOrNull(eq(RealmMapUtils.KEY_MAP_DATA), any(Codec.class)))
                .thenReturn(sampleMapData);

            Optional<RealmLayoutSize> result = RealmMapUtils.getSize(mockItemStack);
            assertTrue(result.isPresent());
            assertEquals(RealmLayoutSize.LARGE, result.get());
        }
    }

    @Nested
    @DisplayName("isIdentified")
    class IsIdentifiedTests {

        @Test
        @DisplayName("Should return false for null ItemStack")
        void shouldReturnFalseForNull() {
            assertFalse(RealmMapUtils.isIdentified(null));
        }

        @Test
        @DisplayName("Should return identified status")
        void shouldReturnIdentifiedStatus() {
            when(mockItemStack.getFromMetadataOrNull(eq(RealmMapUtils.KEY_IS_MAP), any(Codec.class)))
                .thenReturn(true);
            when(mockItemStack.getFromMetadataOrNull(eq(RealmMapUtils.KEY_MAP_DATA), any(Codec.class)))
                .thenReturn(sampleMapData);

            assertTrue(RealmMapUtils.isIdentified(mockItemStack));
        }
    }

    @Nested
    @DisplayName("isCorrupted")
    class IsCorruptedTests {

        @Test
        @DisplayName("Should return false for null ItemStack")
        void shouldReturnFalseForNull() {
            assertFalse(RealmMapUtils.isCorrupted(null));
        }

        @Test
        @DisplayName("Should return corrupted status")
        void shouldReturnCorruptedStatus() {
            when(mockItemStack.getFromMetadataOrNull(eq(RealmMapUtils.KEY_IS_MAP), any(Codec.class)))
                .thenReturn(true);
            when(mockItemStack.getFromMetadataOrNull(eq(RealmMapUtils.KEY_MAP_DATA), any(Codec.class)))
                .thenReturn(sampleMapData);

            assertFalse(RealmMapUtils.isCorrupted(mockItemStack));
        }
    }

    @Nested
    @DisplayName("writeMapData")
    class WriteMapDataTests {

        @Test
        @DisplayName("Should reject null ItemStack")
        void shouldRejectNullItemStack() {
            assertThrows(NullPointerException.class, () ->
                RealmMapUtils.writeMapData(null, sampleMapData));
        }

        @Test
        @DisplayName("Should reject null RealmMapData")
        void shouldRejectNullMapData() {
            assertThrows(NullPointerException.class, () ->
                RealmMapUtils.writeMapData(mockItemStack, null));
        }

        @Test
        @DisplayName("Should write map data to ItemStack")
        void shouldWriteMapData() {
            // Set up chain of withMetadata calls (includes KEY_BASE_ITEM_ID and backup keys)
            when(mockItemStack.withMetadata(eq(RealmMapUtils.KEY_IS_MAP), any(Codec.class), eq(true)))
                .thenReturn(mockResultStack);
            when(mockResultStack.withMetadata(eq(RealmMapUtils.KEY_MAP_DATA), any(Codec.class), eq(sampleMapData)))
                .thenReturn(mockResultStack);
            when(mockResultStack.withMetadata(eq(RealmMapUtils.KEY_BASE_ITEM_ID), any(Codec.class), any()))
                .thenReturn(mockResultStack);
            // Backup keys
            when(mockResultStack.withMetadata(eq(RealmMapUtils.KEY_LEVEL), any(Codec.class), any()))
                .thenReturn(mockResultStack);
            when(mockResultStack.withMetadata(eq(RealmMapUtils.KEY_RARITY), any(Codec.class), any()))
                .thenReturn(mockResultStack);
            when(mockResultStack.withMetadata(eq(RealmMapUtils.KEY_BIOME), any(Codec.class), any()))
                .thenReturn(mockResultStack);
            when(mockResultStack.withMetadata(eq(RealmMapUtils.KEY_SIZE), any(Codec.class), any()))
                .thenReturn(mockResultStack);
            when(mockResultStack.withMetadata(eq(RealmMapUtils.KEY_SHAPE), any(Codec.class), any()))
                .thenReturn(mockResultStack);
            when(mockResultStack.withMetadata(eq(RealmMapUtils.KEY_IDENTIFIED), any(Codec.class), any()))
                .thenReturn(mockResultStack);

            ItemStack result = RealmMapUtils.writeMapData(mockItemStack, sampleMapData);

            assertNotNull(result);
            verify(mockItemStack).withMetadata(eq(RealmMapUtils.KEY_IS_MAP), any(Codec.class), eq(true));
            verify(mockResultStack).withMetadata(eq(RealmMapUtils.KEY_MAP_DATA), any(Codec.class), eq(sampleMapData));
        }
    }

    @Nested
    @DisplayName("removeMapData")
    class RemoveMapDataTests {

        @Test
        @DisplayName("Should reject null ItemStack")
        void shouldRejectNullItemStack() {
            assertThrows(NullPointerException.class, () ->
                RealmMapUtils.removeMapData(null));
        }

        @Test
        @DisplayName("Should remove map data from ItemStack")
        void shouldRemoveMapData() {
            // removeMapData removes primary keys and backup keys
            when(mockItemStack.withMetadata(eq(RealmMapUtils.KEY_IS_MAP), any(Codec.class), isNull()))
                .thenReturn(mockResultStack);
            when(mockResultStack.withMetadata(eq(RealmMapUtils.KEY_MAP_DATA), any(Codec.class), isNull()))
                .thenReturn(mockResultStack);
            when(mockResultStack.withMetadata(eq(RealmMapUtils.KEY_INSTANCE_ID), any(Codec.class), isNull()))
                .thenReturn(mockResultStack);
            when(mockResultStack.withMetadata(eq(RealmMapUtils.KEY_BASE_ITEM_ID), any(Codec.class), isNull()))
                .thenReturn(mockResultStack);
            // Backup keys
            when(mockResultStack.withMetadata(eq(RealmMapUtils.KEY_LEVEL), any(Codec.class), isNull()))
                .thenReturn(mockResultStack);
            when(mockResultStack.withMetadata(eq(RealmMapUtils.KEY_RARITY), any(Codec.class), isNull()))
                .thenReturn(mockResultStack);
            when(mockResultStack.withMetadata(eq(RealmMapUtils.KEY_BIOME), any(Codec.class), isNull()))
                .thenReturn(mockResultStack);
            when(mockResultStack.withMetadata(eq(RealmMapUtils.KEY_SIZE), any(Codec.class), isNull()))
                .thenReturn(mockResultStack);
            when(mockResultStack.withMetadata(eq(RealmMapUtils.KEY_SHAPE), any(Codec.class), isNull()))
                .thenReturn(mockResultStack);
            when(mockResultStack.withMetadata(eq(RealmMapUtils.KEY_IDENTIFIED), any(Codec.class), isNull()))
                .thenReturn(mockResultStack);

            ItemStack result = RealmMapUtils.removeMapData(mockItemStack);

            assertNotNull(result);
            verify(mockItemStack).withMetadata(eq(RealmMapUtils.KEY_IS_MAP), any(Codec.class), isNull());
        }
    }

    @Nested
    @DisplayName("identify")
    class IdentifyTests {

        @Test
        @DisplayName("Should reject null ItemStack")
        void shouldRejectNullItemStack() {
            assertThrows(NullPointerException.class, () ->
                RealmMapUtils.identify(null));
        }

        @Test
        @DisplayName("Should return original if not a realm map")
        void shouldReturnOriginalIfNotRealmMap() {
            when(mockItemStack.getFromMetadataOrNull(eq(RealmMapUtils.KEY_IS_MAP), any(Codec.class)))
                .thenReturn(null);

            ItemStack result = RealmMapUtils.identify(mockItemStack);

            assertSame(mockItemStack, result);
        }

        @Test
        @DisplayName("Should return original if already identified")
        void shouldReturnOriginalIfAlreadyIdentified() {
            when(mockItemStack.getFromMetadataOrNull(eq(RealmMapUtils.KEY_IS_MAP), any(Codec.class)))
                .thenReturn(true);
            when(mockItemStack.getFromMetadataOrNull(eq(RealmMapUtils.KEY_MAP_DATA), any(Codec.class)))
                .thenReturn(sampleMapData); // already identified

            ItemStack result = RealmMapUtils.identify(mockItemStack);

            assertSame(mockItemStack, result);
        }

        @Test
        @DisplayName("Should identify unidentified map")
        void shouldIdentifyUnidentifiedMap() {
            // Create unidentified version
            RealmMapData unidentified = new RealmMapData(
                sampleMapData.level(),
                sampleMapData.rarity(),
                sampleMapData.quality(),
                sampleMapData.biome(),
                sampleMapData.size(),
                sampleMapData.shape(),
                sampleMapData.prefixes(),
                sampleMapData.suffixes(),
                sampleMapData.corrupted(),
                false, // not identified
                null   // instanceId
            );

            when(mockItemStack.getFromMetadataOrNull(eq(RealmMapUtils.KEY_IS_MAP), any(Codec.class)))
                .thenReturn(true);
            when(mockItemStack.getFromMetadataOrNull(eq(RealmMapUtils.KEY_MAP_DATA), any(Codec.class)))
                .thenReturn(unidentified);
            when(mockItemStack.withMetadata(eq(RealmMapUtils.KEY_IS_MAP), any(Codec.class), eq(true)))
                .thenReturn(mockResultStack);
            when(mockResultStack.withMetadata(eq(RealmMapUtils.KEY_MAP_DATA), any(Codec.class), any(RealmMapData.class)))
                .thenReturn(mockResultStack);
            when(mockResultStack.withMetadata(eq(RealmMapUtils.KEY_BASE_ITEM_ID), any(Codec.class), any()))
                .thenReturn(mockResultStack);
            // Backup keys
            when(mockResultStack.withMetadata(eq(RealmMapUtils.KEY_LEVEL), any(Codec.class), any()))
                .thenReturn(mockResultStack);
            when(mockResultStack.withMetadata(eq(RealmMapUtils.KEY_RARITY), any(Codec.class), any()))
                .thenReturn(mockResultStack);
            when(mockResultStack.withMetadata(eq(RealmMapUtils.KEY_BIOME), any(Codec.class), any()))
                .thenReturn(mockResultStack);
            when(mockResultStack.withMetadata(eq(RealmMapUtils.KEY_SIZE), any(Codec.class), any()))
                .thenReturn(mockResultStack);
            when(mockResultStack.withMetadata(eq(RealmMapUtils.KEY_SHAPE), any(Codec.class), any()))
                .thenReturn(mockResultStack);
            when(mockResultStack.withMetadata(eq(RealmMapUtils.KEY_IDENTIFIED), any(Codec.class), any()))
                .thenReturn(mockResultStack);

            ItemStack result = RealmMapUtils.identify(mockItemStack);

            assertNotNull(result);
            verify(mockResultStack).withMetadata(eq(RealmMapUtils.KEY_MAP_DATA), any(Codec.class), any(RealmMapData.class));
        }
    }

    @Nested
    @DisplayName("corrupt")
    class CorruptTests {

        @Test
        @DisplayName("Should reject null ItemStack")
        void shouldRejectNullItemStack() {
            assertThrows(NullPointerException.class, () ->
                RealmMapUtils.corrupt(null));
        }

        @Test
        @DisplayName("Should return original if not a realm map")
        void shouldReturnOriginalIfNotRealmMap() {
            when(mockItemStack.getFromMetadataOrNull(eq(RealmMapUtils.KEY_IS_MAP), any(Codec.class)))
                .thenReturn(null);

            ItemStack result = RealmMapUtils.corrupt(mockItemStack);

            assertSame(mockItemStack, result);
        }
    }

    @Nested
    @DisplayName("updateMapData")
    class UpdateMapDataTests {

        @Test
        @DisplayName("Should reject null ItemStack")
        void shouldRejectNullItemStack() {
            assertThrows(NullPointerException.class, () ->
                RealmMapUtils.updateMapData(null, d -> d));
        }

        @Test
        @DisplayName("Should reject null updater")
        void shouldRejectNullUpdater() {
            assertThrows(NullPointerException.class, () ->
                RealmMapUtils.updateMapData(mockItemStack, null));
        }

        @Test
        @DisplayName("Should return original if not a realm map")
        void shouldReturnOriginalIfNotRealmMap() {
            when(mockItemStack.getFromMetadataOrNull(eq(RealmMapUtils.KEY_IS_MAP), any(Codec.class)))
                .thenReturn(null);

            ItemStack result = RealmMapUtils.updateMapData(mockItemStack, d -> d.withQuality(100));

            assertSame(mockItemStack, result);
        }

        @Test
        @DisplayName("Should apply updater function")
        void shouldApplyUpdater() {
            when(mockItemStack.getFromMetadataOrNull(eq(RealmMapUtils.KEY_IS_MAP), any(Codec.class)))
                .thenReturn(true);
            when(mockItemStack.getFromMetadataOrNull(eq(RealmMapUtils.KEY_MAP_DATA), any(Codec.class)))
                .thenReturn(sampleMapData);
            when(mockItemStack.withMetadata(eq(RealmMapUtils.KEY_IS_MAP), any(Codec.class), eq(true)))
                .thenReturn(mockResultStack);
            when(mockResultStack.withMetadata(eq(RealmMapUtils.KEY_MAP_DATA), any(Codec.class), any(RealmMapData.class)))
                .thenReturn(mockResultStack);
            when(mockResultStack.withMetadata(eq(RealmMapUtils.KEY_BASE_ITEM_ID), any(Codec.class), any()))
                .thenReturn(mockResultStack);
            // Backup keys
            when(mockResultStack.withMetadata(eq(RealmMapUtils.KEY_LEVEL), any(Codec.class), any()))
                .thenReturn(mockResultStack);
            when(mockResultStack.withMetadata(eq(RealmMapUtils.KEY_RARITY), any(Codec.class), any()))
                .thenReturn(mockResultStack);
            when(mockResultStack.withMetadata(eq(RealmMapUtils.KEY_BIOME), any(Codec.class), any()))
                .thenReturn(mockResultStack);
            when(mockResultStack.withMetadata(eq(RealmMapUtils.KEY_SIZE), any(Codec.class), any()))
                .thenReturn(mockResultStack);
            when(mockResultStack.withMetadata(eq(RealmMapUtils.KEY_SHAPE), any(Codec.class), any()))
                .thenReturn(mockResultStack);
            when(mockResultStack.withMetadata(eq(RealmMapUtils.KEY_IDENTIFIED), any(Codec.class), any()))
                .thenReturn(mockResultStack);

            ItemStack result = RealmMapUtils.updateMapData(mockItemStack, d -> d.withQuality(100));

            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("isValid")
    class IsValidTests {

        @Test
        @DisplayName("Should return false for null ItemStack")
        void shouldReturnFalseForNull() {
            assertFalse(RealmMapUtils.isValid(null));
        }

        @Test
        @DisplayName("Should return false when not a realm map")
        void shouldReturnFalseWhenNotRealmMap() {
            when(mockItemStack.getFromMetadataOrNull(eq(RealmMapUtils.KEY_IS_MAP), any(Codec.class)))
                .thenReturn(null);

            assertFalse(RealmMapUtils.isValid(mockItemStack));
        }

        @Test
        @DisplayName("Should return true when valid realm map")
        void shouldReturnTrueWhenValid() {
            when(mockItemStack.getFromMetadataOrNull(eq(RealmMapUtils.KEY_IS_MAP), any(Codec.class)))
                .thenReturn(true);
            when(mockItemStack.getFromMetadataOrNull(eq(RealmMapUtils.KEY_MAP_DATA), any(Codec.class)))
                .thenReturn(sampleMapData);

            assertTrue(RealmMapUtils.isValid(mockItemStack));
        }

        @Test
        @DisplayName("Should return false when marker present but data missing")
        void shouldReturnFalseWhenDataMissing() {
            when(mockItemStack.getFromMetadataOrNull(eq(RealmMapUtils.KEY_IS_MAP), any(Codec.class)))
                .thenReturn(true);
            when(mockItemStack.getFromMetadataOrNull(eq(RealmMapUtils.KEY_MAP_DATA), any(Codec.class)))
                .thenReturn(null);

            assertFalse(RealmMapUtils.isValid(mockItemStack));
        }
    }
}
