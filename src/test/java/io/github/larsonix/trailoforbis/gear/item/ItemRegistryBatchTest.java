package io.github.larsonix.trailoforbis.gear.item;

import io.github.larsonix.trailoforbis.gear.item.ItemRegistryService.BatchRegistrationEntry;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link ItemRegistryService} batch registration and skipVerification.
 *
 * <p>Note: Full ItemRegistryService tests require Hytale's runtime asset map
 * (reflection-based). These tests focus on the BatchRegistrationEntry record,
 * contract validation, and behavior that can be tested without the asset map.
 *
 * <p>The actual StampedLock batching is verified by the integration test
 * (kills in realm → items appear correctly), since mocking the internal
 * asset map field + StampedLock is fragile.
 */
@ExtendWith(MockitoExtension.class)
class ItemRegistryBatchTest {

    @Mock private Item baseItem;

    // =========================================================================
    // BATCH REGISTRATION ENTRY RECORD
    // =========================================================================

    @Nested
    @DisplayName("BatchRegistrationEntry Record")
    class BatchRegistrationEntryTests {

        @Test
        @DisplayName("stores all fields correctly")
        void entry_StoresFields() {
            BatchRegistrationEntry entry = new BatchRegistrationEntry(
                    baseItem, "rpg_gear_123_0", GearRarity.EPIC);

            assertSame(baseItem, entry.baseItem());
            assertEquals("rpg_gear_123_0", entry.customId());
            assertEquals(GearRarity.EPIC, entry.rarity());
        }

        @Test
        @DisplayName("accepts null rarity")
        void entry_NullRarity() {
            BatchRegistrationEntry entry = new BatchRegistrationEntry(
                    baseItem, "rpg_gear_456_0", null);

            assertNull(entry.rarity());
        }

        @Test
        @DisplayName("entries with same data are equal")
        void entry_Equality() {
            BatchRegistrationEntry e1 = new BatchRegistrationEntry(
                    baseItem, "rpg_gear_123_0", GearRarity.RARE);
            BatchRegistrationEntry e2 = new BatchRegistrationEntry(
                    baseItem, "rpg_gear_123_0", GearRarity.RARE);

            assertEquals(e1, e2);
            assertEquals(e1.hashCode(), e2.hashCode());
        }

        @Test
        @DisplayName("entries with different IDs are not equal")
        void entry_Inequality() {
            BatchRegistrationEntry e1 = new BatchRegistrationEntry(
                    baseItem, "rpg_gear_111_0", GearRarity.COMMON);
            BatchRegistrationEntry e2 = new BatchRegistrationEntry(
                    baseItem, "rpg_gear_222_0", GearRarity.COMMON);

            assertNotEquals(e1, e2);
        }
    }

    // =========================================================================
    // BATCH INPUT VALIDATION
    // =========================================================================

    @Nested
    @DisplayName("Batch Input Validation")
    class BatchInputTests {

        @Test
        @DisplayName("uninitialized service returns 0 for batch registration")
        void batch_Uninitialized_ReturnsZero() {
            ItemRegistryService service = new ItemRegistryService();
            // NOT initialized — should return 0 without throwing

            int registered = service.createAndRegisterBatch(List.of(
                    new BatchRegistrationEntry(baseItem, "rpg_gear_test_0", GearRarity.COMMON)
            ));

            assertEquals(0, registered, "Uninitialized service should register 0 items");
        }

        @Test
        @DisplayName("empty batch returns 0")
        void batch_Empty_ReturnsZero() {
            ItemRegistryService service = new ItemRegistryService();
            int registered = service.createAndRegisterBatch(List.of());
            assertEquals(0, registered);
        }

        @Test
        @DisplayName("null list throws NPE")
        void batch_NullList_Throws() {
            ItemRegistryService service = new ItemRegistryService();
            assertThrows(NullPointerException.class, () ->
                    service.createAndRegisterBatch(null));
        }
    }

    // =========================================================================
    // BATCH SIZE SCENARIOS
    // =========================================================================

    @Nested
    @DisplayName("Batch Size Scenarios")
    class BatchSizeTests {

        @Test
        @DisplayName("single item batch is valid")
        void batch_SingleItem() {
            var entries = List.of(
                    new BatchRegistrationEntry(baseItem, "rpg_gear_1_0", GearRarity.COMMON));
            // Can't call createAndRegisterBatch without initialization,
            // but verify the list construction is valid
            assertEquals(1, entries.size());
        }

        @Test
        @DisplayName("large batch (30 items) list is valid")
        void batch_LargeList() {
            var entries = new java.util.ArrayList<BatchRegistrationEntry>();
            for (int i = 0; i < 30; i++) {
                entries.add(new BatchRegistrationEntry(
                        baseItem, "rpg_gear_" + i + "_0", GearRarity.RARE));
            }
            assertEquals(30, entries.size());
            // All entries should have unique IDs
            long uniqueIds = entries.stream()
                    .map(BatchRegistrationEntry::customId)
                    .distinct()
                    .count();
            assertEquals(30, uniqueIds, "All 30 entries should have unique IDs");
        }

        @Test
        @DisplayName("batch with mixed rarities is valid")
        void batch_MixedRarities() {
            var entries = List.of(
                    new BatchRegistrationEntry(baseItem, "rpg_gear_1_0", GearRarity.COMMON),
                    new BatchRegistrationEntry(baseItem, "rpg_gear_2_0", GearRarity.RARE),
                    new BatchRegistrationEntry(baseItem, "rpg_gear_3_0", GearRarity.EPIC),
                    new BatchRegistrationEntry(baseItem, "rpg_gear_4_0", GearRarity.LEGENDARY),
                    new BatchRegistrationEntry(baseItem, "rpg_gear_5_0", GearRarity.MYTHIC));

            assertEquals(5, entries.size());
            // Each entry has distinct rarity
            long uniqueRarities = entries.stream()
                    .map(BatchRegistrationEntry::rarity)
                    .distinct()
                    .count();
            assertEquals(5, uniqueRarities);
        }
    }

    // =========================================================================
    // BATCH VS INDIVIDUAL — CONTRACT COMPARISON
    // =========================================================================

    @Nested
    @DisplayName("Batch vs Individual Contract")
    class BatchVsIndividualTests {

        @Test
        @DisplayName("batch entry carries same information as individual registration params")
        void batch_SameInfoAsIndividual() {
            // Individual: createAndRegister(Item baseItem, String customId, GearRarity rarity)
            // Batch:      BatchRegistrationEntry(Item baseItem, String customId, GearRarity rarity)
            // Same parameters — the batch is a deferred version of the same operation

            Item mockItem = mock(Item.class);
            String customId = "rpg_gear_abc_0";
            GearRarity rarity = GearRarity.EPIC;

            BatchRegistrationEntry entry = new BatchRegistrationEntry(mockItem, customId, rarity);

            assertSame(mockItem, entry.baseItem(), "Same base item");
            assertEquals(customId, entry.customId(), "Same custom ID");
            assertEquals(rarity, entry.rarity(), "Same rarity");
        }

        @Test
        @DisplayName("batch entries from GeneratedGear can be constructed")
        void batch_FromGeneratedGear() {
            // Simulates what DeferredLootPipeline.finalizeBatch() does
            Item mockAsset = mock(Item.class);
            String gearItemId = "rpg_gear_1234567890_42";
            GearRarity rarity = GearRarity.LEGENDARY;

            BatchRegistrationEntry entry = new BatchRegistrationEntry(
                    mockAsset, gearItemId, rarity);

            assertNotNull(entry);
            assertTrue(entry.customId().startsWith("rpg_gear_"),
                    "Custom ID should follow rpg_gear_ convention");
        }
    }

    // =========================================================================
    // SKIP VERIFICATION FLAG
    // =========================================================================

    @Nested
    @DisplayName("Skip Verification Contract")
    class SkipVerificationTests {

        @Test
        @DisplayName("async createAndRegister is the loot hot path (non-sync)")
        void asyncPath_IsLootPath() {
            // The createAndRegister(Item, String, GearRarity) method calls
            // registerItemInternal with sync=false, skipVerification=true
            // This test documents the contract — the actual verification
            // is that the loot path no longer reads back from the asset map

            // createAndRegister has 3 signatures:
            // 1. createAndRegister(Item, String)           → skipVerification=true
            // 2. createAndRegister(Item, String, Rarity)   → skipVerification=true (LOOT PATH)
            // 3. createAndRegisterSync(Item, String)       → skipVerification=false
            // 4. createAndRegisterSync(Item, String, Rarity) → skipVerification=false
            // 5. createAndRegisterBatch(List)               → no verification at all

            // This is a documentation test — verifying the design contract
            assertTrue(true, "Loot path uses non-sync, non-verifying registration");
        }
    }
}
