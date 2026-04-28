package io.github.larsonix.trailoforbis.stones.ui;

import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import io.github.larsonix.trailoforbis.gear.item.ItemDisplayNameService;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.maps.core.RealmBiomeType;
import io.github.larsonix.trailoforbis.maps.core.RealmLayoutShape;
import io.github.larsonix.trailoforbis.maps.core.RealmLayoutSize;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.items.RealmMapUtils;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifier;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifierType;
import io.github.larsonix.trailoforbis.stones.ModifiableItem;
import io.github.larsonix.trailoforbis.stones.StoneActionRegistry;
import io.github.larsonix.trailoforbis.stones.StoneType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CompatibleItemScanner")
class CompatibleItemScannerTest {

    @Mock
    private ItemDisplayNameService displayNameService;
    @Mock
    private Inventory inventory;
    @Mock
    private ItemContainer armor;
    @Mock
    private ItemContainer hotbar;
    @Mock
    private ItemContainer storage;
    @Mock
    private ItemContainer utility;
    @Mock
    private ItemStack mapItem;

    @Test
    @DisplayName("Fortune's Compass should include full-modifier maps when compass bonus is below cap")
    void fortunesCompassIncludesFullModifierMap() {
        CompatibleItemScanner scanner = new CompatibleItemScanner(displayNameService);
        StoneActionRegistry registry = new StoneActionRegistry();
        RealmMapData fullModifierMap = new RealmMapData(
            50, GearRarity.COMMON, 50, RealmBiomeType.FOREST,
            RealmLayoutSize.MEDIUM, RealmLayoutShape.CIRCULAR,
            List.of(RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 20)),
            List.of(),
            15,
            false,
            true,
            null
        );

        setUpInventoryWithSingleStorageItem();
        when(mapItem.getItem()).thenReturn(null);
        when(mapItem.getItemId()).thenReturn("Realm_Map");
        when(displayNameService.getDisplayName(any(ModifiableItem.class), any(ItemStack.class)))
            .thenReturn("Forest Map");

        try (MockedStatic<RealmMapUtils> mapUtils = mockStatic(RealmMapUtils.class)) {
            mapUtils.when(() -> RealmMapUtils.readMapData(mapItem)).thenReturn(Optional.of(fullModifierMap));

            List<CompatibleItemScanner.ScannedItem> result =
                scanner.scan(inventory, StoneType.FORTUNES_COMPASS, registry);

            assertEquals(1, result.size());
            assertEquals(ContainerType.STORAGE, result.get(0).container());
            assertEquals("Forest Map", result.get(0).displayName());
        }
    }

    @Test
    @DisplayName("Fortune's Compass should exclude maps already at +20 compass bonus")
    void fortunesCompassExcludesCappedMap() {
        CompatibleItemScanner scanner = new CompatibleItemScanner(displayNameService);
        StoneActionRegistry registry = new StoneActionRegistry();
        RealmMapData cappedCompassMap = new RealmMapData(
            50, GearRarity.COMMON, 50, RealmBiomeType.FOREST,
            RealmLayoutSize.MEDIUM, RealmLayoutShape.CIRCULAR,
            List.of(RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 20)),
            List.of(),
            20,
            false,
            true,
            null
        );

        setUpInventoryWithSingleStorageItem();

        try (MockedStatic<RealmMapUtils> mapUtils = mockStatic(RealmMapUtils.class)) {
            mapUtils.when(() -> RealmMapUtils.readMapData(mapItem)).thenReturn(Optional.of(cappedCompassMap));

            List<CompatibleItemScanner.ScannedItem> result =
                scanner.scan(inventory, StoneType.FORTUNES_COMPASS, registry);

            assertTrue(result.isEmpty());
        }
    }

    private void setUpInventoryWithSingleStorageItem() {
        when(inventory.getArmor()).thenReturn(armor);
        when(inventory.getHotbar()).thenReturn(hotbar);
        when(inventory.getStorage()).thenReturn(storage);
        when(inventory.getUtility()).thenReturn(utility);

        when(armor.getCapacity()).thenReturn((short) 0);
        when(hotbar.getCapacity()).thenReturn((short) 0);
        when(storage.getCapacity()).thenReturn((short) 1);
        when(utility.getCapacity()).thenReturn((short) 0);

        when(storage.getItemStack((short) 0)).thenReturn(mapItem);
        when(mapItem.isEmpty()).thenReturn(false);
    }
}
