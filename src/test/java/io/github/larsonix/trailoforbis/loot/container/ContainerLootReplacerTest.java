package io.github.larsonix.trailoforbis.loot.container;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

import io.github.larsonix.trailoforbis.gear.conversion.ItemClassifier;
import io.github.larsonix.trailoforbis.gear.conversion.ItemClassifier.Classification;
import io.github.larsonix.trailoforbis.gear.conversion.ItemClassifier.ItemType;
import io.github.larsonix.trailoforbis.gear.loot.RealmLootContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link ContainerLootReplacer} — the container content analysis and
 * replacement algorithm, including total replacement, selective replacement,
 * quality bonus application, and edge cases.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContainerLootReplacerTest {

    @Mock private ContainerLootGenerator lootGenerator;
    @Mock private ItemClassifier itemClassifier;
    @Mock private ContainerTierClassifier tierClassifier;

    private ContainerLootConfig config;
    private ContainerLootReplacer replacer;

    private static final UUID TEST_PLAYER = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        config = ContainerLootConfig.createDefaults();
        // Enable weapon and armor removal by default
        config.getItemRemoval().setRemove_weapons(true);
        config.getItemRemoval().setRemove_armor(true);

        replacer = new ContainerLootReplacer(config, lootGenerator, itemClassifier, tierClassifier);
    }

    // =========================================================================
    // Container Mock Helpers
    // =========================================================================

    /**
     * Creates a mock ItemContainer with the given capacity and items.
     * Items map: slot number -> item (null entries = empty slots).
     */
    private ItemContainer mockContainer(short capacity, Map<Short, ItemStack> items) {
        ItemContainer container = mock(ItemContainer.class);
        when(container.getCapacity()).thenReturn(capacity);

        for (short slot = 0; slot < capacity; slot++) {
            ItemStack item = items.get(slot);
            when(container.getItemStack(slot)).thenReturn(item);
        }

        // replaceAll uses Hytale's SlotReplacementFunction — just return null (ListTransaction)
        // We verify behavior through ReplacementResult and argument captures, not container state
        when(container.replaceAll(any())).thenReturn(null);

        return container;
    }

    /**
     * Creates a mock ItemStack. Does NOT stub getItemId() inline to avoid
     * Mockito's "unfinished stubbing" when called inside when().thenReturn().
     * Use {@link #itemWithId(String)} when you need getItemId() for preserved/removed lists.
     */
    private ItemStack mockItem() {
        return mock(ItemStack.class);
    }

    /**
     * Creates a mock ItemStack with a stubbed getItemId().
     * MUST be called as a standalone statement, never inside when().thenReturn().
     */
    private ItemStack itemWithId(String itemId) {
        ItemStack item = mock(ItemStack.class);
        when(item.getItemId()).thenReturn(itemId);
        return item;
    }

    private ContainerLootContext overworldContext(int sourceLevel, int playerLevel) {
        return ContainerLootContext.overworld(sourceLevel, playerLevel);
    }

    private ContainerLootContext realmContextWithQuality(int qualityBonus) {
        return ContainerLootContext.realm(10, 10, new RealmLootContext(0, 0), qualityBonus);
    }

    // =========================================================================
    // TOTAL REPLACEMENT (replaceTotal)
    // =========================================================================

    @Nested
    @DisplayName("Total Replacement (replaceTotal)")
    class TotalReplacement {

        @Test
        @DisplayName("Clears all existing items")
        void replaceTotal_clearsAllExistingItems() {
            Map<Short, ItemStack> items = new HashMap<>();
            items.put((short) 0, mockItem());
            items.put((short) 1, mockItem());
            items.put((short) 2, mockItem());

            var container = mockContainer((short) 5, items);

            // Generate 3 RPG items
            when(lootGenerator.generateLootForSlots(any(), any(), any(), anyInt()))
                .thenReturn(List.of(mockItem(), mockItem(), mockItem()));

            when(tierClassifier.getItemRange(any())).thenReturn(new int[]{3, 5});

            var result = replacer.replaceTotal(container, overworldContext(10, 10),
                ContainerTier.BOSS, TEST_PLAYER);

            assertEquals(3, result.itemsRemoved(), "Should count 3 existing items as removed");
        }

        @Test
        @DisplayName("Places generated loot in container")
        void replaceTotal_placesGeneratedLoot() {
            Map<Short, ItemStack> items = new HashMap<>();
            var container = mockContainer((short) 10, items);

            List<ItemStack> rpgLoot = List.of(mockItem(), mockItem());
            when(lootGenerator.generateLootForSlots(any(), any(), any(), anyInt()))
                .thenReturn(rpgLoot);

            when(tierClassifier.getItemRange(any())).thenReturn(new int[]{2, 2});

            var result = replacer.replaceTotal(container, overworldContext(10, 10),
                ContainerTier.DUNGEON, TEST_PLAYER);

            assertEquals(2, result.itemsAdded(), "Should report 2 items added");
        }

        @Test
        @DisplayName("Caps target count at container capacity")
        void replaceTotal_capsTargetCountAtCapacity() {
            Map<Short, ItemStack> items = new HashMap<>();
            var container = mockContainer((short) 3, items); // Only 3 slots

            // Config says 5-8 items, but container is only 3
            when(tierClassifier.getItemRange(any())).thenReturn(new int[]{5, 8});

            when(lootGenerator.generateLootForSlots(any(), any(), any(), anyInt()))
                .thenReturn(List.of(mockItem(), mockItem(), mockItem()));

            replacer.replaceTotal(container, overworldContext(10, 10),
                ContainerTier.BOSS, TEST_PLAYER);

            // Verify generator was called with capped target
            ArgumentCaptor<Integer> slotsCaptor = ArgumentCaptor.forClass(Integer.class);
            verify(lootGenerator).generateLootForSlots(any(), any(), any(), slotsCaptor.capture());
            assertTrue(slotsCaptor.getValue() <= 3,
                "Target slots should be capped at container capacity (3)");
        }

        @Test
        @DisplayName("Zero capacity returns empty result")
        void replaceTotal_zeroCapacity_returnsEmptyResult() {
            ItemContainer container = mock(ItemContainer.class);
            when(container.getCapacity()).thenReturn((short) 0);

            var result = replacer.replaceTotal(container, overworldContext(10, 10),
                ContainerTier.BASIC, TEST_PLAYER);

            assertEquals(0, result.itemsRemoved());
            assertEquals(0, result.itemsAdded());
            assertEquals(0, result.itemsPreserved());
        }

        @Test
        @DisplayName("Result counts existing items as removed")
        void replaceTotal_resultCountsExistingAsRemoved() {
            Map<Short, ItemStack> items = new HashMap<>();
            items.put((short) 0, mockItem());
            items.put((short) 1, mockItem());
            // Slot 2 is empty

            var container = mockContainer((short) 3, items);
            when(tierClassifier.getItemRange(any())).thenReturn(new int[]{1, 1});
            when(lootGenerator.generateLootForSlots(any(), any(), any(), anyInt()))
                .thenReturn(List.of(mockItem()));

            var result = replacer.replaceTotal(container, overworldContext(10, 10),
                ContainerTier.BASIC, TEST_PLAYER);

            assertEquals(2, result.itemsRemoved(), "2 existing items should be counted as removed");
        }
    }

    // =========================================================================
    // SELECTIVE REPLACEMENT (replace)
    // =========================================================================

    @Nested
    @DisplayName("Selective Replacement (replace)")
    class SelectiveReplacement {

        @Test
        @DisplayName("Removes weapons when configured")
        void replace_removesWeapons_whenConfigured() {
            config.getItemRemoval().setRemove_weapons(true);

            Map<Short, ItemStack> items = new HashMap<>();
            ItemStack weapon = mockItem();
            items.put((short) 0, weapon);

            var container = mockContainer((short) 5, items);
            when(itemClassifier.classify(weapon))
                .thenReturn(new Classification(ItemType.WEAPON, "weapon"));
            when(lootGenerator.generateLoot(any(), any(), any()))
                .thenReturn(List.of(mockItem()));

            var result = replacer.replace(container, overworldContext(10, 10),
                ContainerTier.BASIC, TEST_PLAYER);

            assertTrue(result.itemsRemoved() >= 1, "Should remove at least 1 weapon");
        }

        @Test
        @DisplayName("Removes armor when configured")
        void replace_removesArmor_whenConfigured() {
            config.getItemRemoval().setRemove_armor(true);

            Map<Short, ItemStack> items = new HashMap<>();
            ItemStack armor = mockItem();
            items.put((short) 0, armor);

            var container = mockContainer((short) 5, items);
            when(itemClassifier.classify(armor))
                .thenReturn(new Classification(ItemType.ARMOR, "chest"));
            when(lootGenerator.generateLoot(any(), any(), any()))
                .thenReturn(List.of(mockItem()));

            var result = replacer.replace(container, overworldContext(10, 10),
                ContainerTier.BASIC, TEST_PLAYER);

            assertTrue(result.itemsRemoved() >= 1, "Should remove at least 1 armor piece");
        }

        @Test
        @DisplayName("Preserves weapons when not configured to remove")
        void replace_preservesWeapons_whenNotConfigured() {
            config.getItemRemoval().setRemove_weapons(false);

            Map<Short, ItemStack> items = new HashMap<>();
            ItemStack weapon = mockItem();
            items.put((short) 0, weapon);

            var container = mockContainer((short) 5, items);
            when(itemClassifier.classify(weapon))
                .thenReturn(new Classification(ItemType.WEAPON, "weapon"));
            when(lootGenerator.generateLoot(any(), any(), any()))
                .thenReturn(List.of());

            var result = replacer.replace(container, overworldContext(10, 10),
                ContainerTier.BASIC, TEST_PLAYER);

            assertTrue(result.itemsPreserved() >= 1, "Should preserve weapon when removal disabled");
        }

        @Test
        @DisplayName("Preserves materials (OTHER type)")
        void replace_preservesMaterials() {
            Map<Short, ItemStack> items = new HashMap<>();
            ItemStack material = mockItem();
            items.put((short) 0, material);

            var container = mockContainer((short) 5, items);
            when(itemClassifier.classify(material))
                .thenReturn(Classification.OTHER);
            when(lootGenerator.generateLoot(any(), any(), any()))
                .thenReturn(List.of());

            var result = replacer.replace(container, overworldContext(10, 10),
                ContainerTier.BASIC, TEST_PLAYER);

            assertTrue(result.itemsPreserved() >= 1, "Material items should be preserved");
            assertEquals(0, result.itemsRemoved(), "No items should be removed");
        }

        @Test
        @DisplayName("Explicit preserved list overrides classification")
        void replace_explicitPreservedList_overridesClassification() {
            config.getItemRemoval().setPreserved_items(List.of("Weapon_Sword_Iron"));

            // Pre-create item with ID BEFORE any when() calls
            ItemStack weapon = itemWithId("Weapon_Sword_Iron");

            Map<Short, ItemStack> items = new HashMap<>();
            items.put((short) 0, weapon);

            var container = mockContainer((short) 5, items);
            // Even though it classifies as WEAPON, it's in the preserved list
            when(itemClassifier.classify(weapon))
                .thenReturn(new Classification(ItemType.WEAPON, "weapon"));
            when(lootGenerator.generateLoot(any(), any(), any()))
                .thenReturn(List.of());

            var result = replacer.replace(container, overworldContext(10, 10),
                ContainerTier.BASIC, TEST_PLAYER);

            assertTrue(result.itemsPreserved() >= 1,
                "Explicitly preserved item should not be removed even if classified as weapon");
        }

        @Test
        @DisplayName("Explicit removed list overrides classification")
        void replace_explicitRemovedList_overridesClassification() {
            config.getItemRemoval().setRemoved_items(List.of("Material_Iron_Ingot"));

            // Pre-create item with ID BEFORE any when() calls
            ItemStack material = itemWithId("Material_Iron_Ingot");
            ItemStack rpgItem = mockItem();

            Map<Short, ItemStack> items = new HashMap<>();
            items.put((short) 0, material);

            var container = mockContainer((short) 5, items);
            when(itemClassifier.classify(material))
                .thenReturn(Classification.OTHER);
            when(lootGenerator.generateLoot(any(), any(), any()))
                .thenReturn(List.of(rpgItem));

            var result = replacer.replace(container, overworldContext(10, 10),
                ContainerTier.BASIC, TEST_PLAYER);

            assertTrue(result.itemsRemoved() >= 1,
                "Explicitly removed item should be removed even if classified as OTHER");
        }

        @Test
        @DisplayName("Empty container adds loot to empty slots")
        void replace_emptyContainer_addsLootToEmptySlots() {
            Map<Short, ItemStack> items = new HashMap<>();
            var container = mockContainer((short) 5, items);
            when(lootGenerator.generateLoot(any(), any(), any()))
                .thenReturn(List.of(mockItem(), mockItem()));

            var result = replacer.replace(container, overworldContext(10, 10),
                ContainerTier.BASIC, TEST_PLAYER);

            assertEquals(0, result.itemsRemoved(), "No items to remove from empty container");
            assertEquals(2, result.itemsAdded(), "Should add generated loot to empty slots");
        }

        @Test
        @DisplayName("Mixed container preserves non-gear items")
        void replace_mixedContainer_preservesOtherItems() {
            Map<Short, ItemStack> items = new HashMap<>();
            ItemStack weapon = mockItem();
            ItemStack material = mockItem();
            items.put((short) 0, weapon);
            items.put((short) 1, material);

            var container = mockContainer((short) 5, items);
            when(itemClassifier.classify(weapon))
                .thenReturn(new Classification(ItemType.WEAPON, "weapon"));
            when(itemClassifier.classify(material))
                .thenReturn(Classification.OTHER);
            when(lootGenerator.generateLoot(any(), any(), any()))
                .thenReturn(List.of(mockItem()));

            var result = replacer.replace(container, overworldContext(10, 10),
                ContainerTier.BASIC, TEST_PLAYER);

            assertEquals(1, result.itemsRemoved(), "Only the weapon should be removed");
            assertTrue(result.itemsPreserved() >= 1, "Material should be preserved");
        }
    }

    // =========================================================================
    // RESULT RECORD
    // =========================================================================

    @Nested
    @DisplayName("Result Record")
    class ResultRecord {

        @Test
        @DisplayName("summary() formats correctly")
        void replacementResult_summary_formatsCorrectly() {
            var result = new ContainerLootReplacer.ReplacementResult(3, 5, 2, ContainerTier.BOSS);
            String summary = result.summary();

            assertTrue(summary.contains("3"), "Summary should contain removed count");
            assertTrue(summary.contains("5"), "Summary should contain added count");
            assertTrue(summary.contains("2"), "Summary should contain preserved count");
            assertTrue(summary.contains("BOSS"), "Summary should contain tier name");
        }

        @Test
        @DisplayName("Result fields are accessible")
        void replacementResult_fieldsAccessible() {
            var result = new ContainerLootReplacer.ReplacementResult(1, 2, 3, ContainerTier.DUNGEON);

            assertEquals(1, result.itemsRemoved());
            assertEquals(2, result.itemsAdded());
            assertEquals(3, result.itemsPreserved());
            assertEquals(ContainerTier.DUNGEON, result.tier());
        }
    }

    // =========================================================================
    // EDGE CASES
    // =========================================================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Null container throws NullPointerException")
        void replace_nullContainer_throwsNPE() {
            assertThrows(NullPointerException.class, () ->
                replacer.replace(null, overworldContext(10, 10), ContainerTier.BASIC, TEST_PLAYER)
            );
        }

        @Test
        @DisplayName("Null playerId throws NullPointerException")
        void replace_nullPlayerId_throwsNPE() {
            ItemContainer container = mock(ItemContainer.class);
            assertThrows(NullPointerException.class, () ->
                replacer.replace(container, overworldContext(10, 10), ContainerTier.BASIC, null)
            );
        }

        @Test
        @DisplayName("Null loot context throws NullPointerException")
        void replace_nullContext_throwsNPE() {
            ItemContainer container = mock(ItemContainer.class);
            assertThrows(NullPointerException.class, () ->
                replacer.replace(container, null, ContainerTier.BASIC, TEST_PLAYER)
            );
        }

        @Test
        @DisplayName("Null tier throws NullPointerException")
        void replace_nullTier_throwsNPE() {
            ItemContainer container = mock(ItemContainer.class);
            assertThrows(NullPointerException.class, () ->
                replacer.replace(container, overworldContext(10, 10), null, TEST_PLAYER)
            );
        }

        @Test
        @DisplayName("replaceTotal null container throws NullPointerException")
        void replaceTotal_nullContainer_throwsNPE() {
            assertThrows(NullPointerException.class, () ->
                replacer.replaceTotal(null, overworldContext(10, 10), ContainerTier.BASIC, TEST_PLAYER)
            );
        }

        @Test
        @DisplayName("More generated loot than available slots — excess is dropped")
        void replace_moreGeneratedThanSlots_excessDropped() {
            // Container with 2 slots, both occupied by weapons → cleared = 2 available
            Map<Short, ItemStack> items = new HashMap<>();
            ItemStack w1 = mockItem();
            ItemStack w2 = mock(ItemStack.class);
            lenient().when(w2.getItemId()).thenReturn("Weapon_Axe_Iron");
            items.put((short) 0, w1);
            items.put((short) 1, w2);

            var container = mockContainer((short) 2, items);
            when(itemClassifier.classify(any()))
                .thenReturn(new Classification(ItemType.WEAPON, "weapon"));

            // Generator produces 5 items but only 2 slots available
            when(lootGenerator.generateLoot(any(), any(), any()))
                .thenReturn(List.of(
                    mockItem(), mockItem(), mockItem(), mockItem(), mockItem()
                ));

            var result = replacer.replace(container, overworldContext(10, 10),
                ContainerTier.BASIC, TEST_PLAYER);

            // Only 2 slots available (cleared weapons), so at most 2 items placed
            assertTrue(result.itemsAdded() <= 2,
                "Can't place more items than available slots");
        }
    }
}
