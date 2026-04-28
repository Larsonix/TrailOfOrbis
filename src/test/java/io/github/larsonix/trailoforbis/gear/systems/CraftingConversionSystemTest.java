package io.github.larsonix.trailoforbis.gear.systems;

import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.gear.conversion.VanillaItemConverter;
import io.github.larsonix.trailoforbis.gear.util.GearUtils;
import io.github.larsonix.trailoforbis.leveling.api.LevelingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CraftingConversionSystem Tests")
class CraftingConversionSystemTest {

    @Mock
    private VanillaItemConverter converter;

    @Mock
    private LevelingService levelingService;

    @Mock
    private Player player;

    @Mock
    private Inventory inventory;

    private CraftingConversionSystem system;
    private MockContainer armor;
    private MockContainer hotbar;
    private MockContainer utility;
    private MockContainer storage;
    private MockContainer backpack;
    private CombinedItemContainer combinedEverything;
    private final UUID playerId = UUID.randomUUID();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        armor = new MockContainer((short) 3);
        hotbar = new MockContainer((short) 3);
        utility = new MockContainer((short) 3);
        storage = new MockContainer((short) 3);
        backpack = new MockContainer((short) 3);
        combinedEverything = mock(CombinedItemContainer.class);

        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getArmor()).thenReturn(armor.container);
        when(inventory.getHotbar()).thenReturn(hotbar.container);
        when(inventory.getUtility()).thenReturn(utility.container);
        when(inventory.getStorage()).thenReturn(storage.container);
        when(inventory.getBackpack()).thenReturn(backpack.container);
        when(inventory.getCombinedArmorHotbarUtilityStorage()).thenReturn(combinedEverything);

        ItemStackTransaction addTx = mock(ItemStackTransaction.class);
        when(addTx.succeeded()).thenReturn(true);
        when(addTx.getRemainder()).thenReturn(null);
        when(combinedEverything.addItemStack(any(ItemStack.class), anyBoolean(), anyBoolean(), anyBoolean()))
            .thenReturn(addTx);

        ComponentType<EntityStore, PlayerRef> playerRefType = mock(ComponentType.class);
        ComponentType<EntityStore, Player> playerType = mock(ComponentType.class);
        ComponentType<EntityStore, UUIDComponent> uuidType = mock(ComponentType.class);

        system = new CraftingConversionSystem(
            converter,
            levelingService,
            (p, delayMs, runnable) -> {
                // Intentionally no-op: tests invoke processing explicitly.
            },
            5,
            1L,
            5000L,
            playerRefType,
            playerType,
            uuidType
        );
    }

    @Test
    @DisplayName("Inventory craft converts eligible hotbar output once")
    void inventoryCraft_convertsEligibleOutput() {
        ItemStack crafted = mockStack("TestSword", 1);
        ItemStack converted = mockStack("TestSword", 1);
        hotbar.set((short) 0, null);

        system.enqueuePendingRequestForTesting(player, playerId, "TestSword", 1, 25);
        hotbar.set((short) 0, crafted);

        when(converter.convertCrafted(crafted, 25)).thenReturn(Optional.of(converted));

        int convertedCount = system.processPendingRequestsNowForTesting(player, playerId);

        assertEquals(1, convertedCount);
        assertSame(converted, hotbar.get((short) 0));
        assertEquals(0, system.pendingRequestCountForTesting(playerId));
        verify(converter).convertCrafted(crafted, 25);
    }

    @Test
    @DisplayName("Bench craft converts eligible output in backpack container")
    void benchCraft_convertsBackpackOutput() {
        ItemStack crafted = mockStack("BenchAxe", 1);
        ItemStack converted = mockStack("BenchAxe", 1);

        system.enqueuePendingRequestForTesting(player, playerId, "BenchAxe", 1, 40);
        backpack.set((short) 1, crafted);

        when(converter.convertCrafted(crafted, 40)).thenReturn(Optional.of(converted));

        int convertedCount = system.processPendingRequestsNowForTesting(player, playerId);

        assertEquals(1, convertedCount);
        assertSame(converted, backpack.get((short) 1));
        assertEquals(0, system.pendingRequestCountForTesting(playerId));
        verify(converter).convertCrafted(crafted, 40);
    }

    @Test
    @DisplayName("Quantity > 1 converts only requested produced amount")
    void quantityGreaterThanOne_convertsOnlyRequestedAmount() {
        ItemStack first = mockStack("BulkBow", 1);
        ItemStack second = mockStack("BulkBow", 1);
        ItemStack third = mockStack("BulkBow", 1);

        system.enqueuePendingRequestForTesting(player, playerId, "BulkBow", 2, 55);
        storage.set((short) 0, first);
        storage.set((short) 1, second);
        storage.set((short) 2, third);

        when(converter.convertCrafted(any(ItemStack.class), eq(55)))
            .thenAnswer(invocation -> Optional.of(mockStack("BulkBow", 1)));

        int convertedCount = system.processPendingRequestsNowForTesting(player, playerId);

        assertEquals(2, convertedCount);
        assertSame(third, storage.get((short) 2));
        assertEquals(0, system.pendingRequestCountForTesting(playerId));
        verify(converter, times(2)).convertCrafted(any(ItemStack.class), eq(55));
    }

    @Test
    @DisplayName("Already RPG crafted output is never reconverted")
    void alreadyRpgGear_isNoOp() {
        ItemStack alreadyRpg = mockStack("RpgSpear", 1);
        when(alreadyRpg.getFromMetadataOrNull(eq(GearUtils.KEY_RARITY), any())).thenReturn("Rare");
        hotbar.set((short) 0, alreadyRpg);

        system.enqueuePendingRequestForTesting(player, playerId, "RpgSpear", 1, 30);
        int convertedCount = system.processPendingRequestsNowForTesting(player, playerId);

        assertEquals(0, convertedCount);
        assertSame(alreadyRpg, hotbar.get((short) 0));
        verify(converter, never()).convertCrafted(any(ItemStack.class), anyInt());
    }

    @Test
    @DisplayName("Non-weapon and non-armor crafted output is skipped")
    void nonConvertibleCraftedOutput_isSkipped() {
        @SuppressWarnings("unchecked")
        DefaultAssetMap<String, Item> assetMap = mock(DefaultAssetMap.class);
        Item itemAsset = mock(Item.class);
        when(assetMap.getAsset("WoodPlank")).thenReturn(itemAsset);
        when(itemAsset.getWeapon()).thenReturn(null);
        when(itemAsset.getArmor()).thenReturn(null);

        try (MockedStatic<Item> itemStatic = org.mockito.Mockito.mockStatic(Item.class)) {
            itemStatic.when(Item::getAssetMap).thenReturn(assetMap);

            boolean queued = system.queueCraftConversionForItem(
                player,
                playerId,
                "WoodPlank",
                1,
                10,
                false
            );

            assertFalse(queued);
            assertEquals(0, system.pendingRequestCountForTesting(playerId));
            verify(converter, never()).convertCrafted(any(ItemStack.class), anyInt());
        }
    }

    @Test
    @DisplayName("No double conversion across immediate and deferred processing")
    void immediateThenDeferred_noDoubleConversion() {
        ItemStack crafted = mockStack("CycleSword", 1);
        ItemStack converted = mockStack("CycleSword", 1);

        system.enqueuePendingRequestForTesting(player, playerId, "CycleSword", 1, 35);

        int immediate = system.processPendingRequestsNowForTesting(player, playerId);
        assertEquals(0, immediate);
        assertEquals(1, system.pendingRequestCountForTesting(playerId));

        hotbar.set((short) 0, crafted);
        when(converter.convertCrafted(crafted, 35)).thenReturn(Optional.of(converted));

        int deferred = system.processPendingRequestsNowForTesting(player, playerId);
        int repeated = system.processPendingRequestsNowForTesting(player, playerId);

        assertEquals(1, deferred);
        assertEquals(0, repeated);
        assertEquals(0, system.pendingRequestCountForTesting(playerId));
        verify(converter, times(1)).convertCrafted(crafted, 35);
    }

    private static ItemStack mockStack(String itemId, int quantity) {
        ItemStack stack = mock(ItemStack.class);
        when(stack.isEmpty()).thenReturn(false);
        when(stack.getItemId()).thenReturn(itemId);
        when(stack.getQuantity()).thenReturn(quantity);
        return stack;
    }

    private static final class MockContainer {
        private final ItemContainer container = mock(ItemContainer.class);
        private final Map<Short, ItemStack> slots = new HashMap<>();

        private MockContainer(short capacity) {
            when(container.getCapacity()).thenReturn(capacity);
            when(container.getItemStack(anyShort()))
                .thenAnswer(invocation -> slots.get(invocation.getArgument(0)));
            when(container.setItemStackForSlot(anyShort(), any(ItemStack.class)))
                .thenAnswer(invocation -> {
                    short slot = invocation.getArgument(0);
                    ItemStack value = invocation.getArgument(1);
                    slots.put(slot, value);
                    ItemStackSlotTransaction tx = mock(ItemStackSlotTransaction.class);
                    when(tx.succeeded()).thenReturn(true);
                    return tx;
                });

            ItemStackTransaction addTx = mock(ItemStackTransaction.class);
            when(addTx.succeeded()).thenReturn(true);
            when(addTx.getRemainder()).thenReturn(null);
            when(container.addItemStack(any(ItemStack.class), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(addTx);
        }

        private void set(short slot, ItemStack value) {
            slots.put(slot, value);
        }

        private ItemStack get(short slot) {
            return slots.get(slot);
        }
    }
}
