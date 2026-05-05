package io.github.larsonix.trailoforbis.gear.loot;

import com.hypixel.hytale.server.core.inventory.ItemStack;

import io.github.larsonix.trailoforbis.gear.config.EquipmentStatConfig;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig;
import io.github.larsonix.trailoforbis.gear.config.TestConfigFactory;
import io.github.larsonix.trailoforbis.gear.generation.GearGenerator;
import io.github.larsonix.trailoforbis.gear.item.ItemRegistryService;
import io.github.larsonix.trailoforbis.gear.loot.LootCalculator.LootRoll;
import io.github.larsonix.trailoforbis.gear.loot.LootGenerator.EquipmentSlot;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;

import io.github.larsonix.trailoforbis.gear.generation.RarityRoller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for LootGenerator using DynamicLootRegistry (dynamic mode only).
 */
@ExtendWith(MockitoExtension.class)
class LootGeneratorTest {

    private GearBalanceConfig balanceConfig;
    private ModifierConfig modifierConfig;
    private ItemRegistryService itemRegistry;

    @BeforeEach
    void setUp() {
        balanceConfig = TestConfigFactory.createDefaultBalanceConfig();
        modifierConfig = TestConfigFactory.createDefaultModifierConfig();

        itemRegistry = mock(ItemRegistryService.class);
        lenient().when(itemRegistry.isInitialized()).thenReturn(true);
        lenient().when(itemRegistry.isRegistered(anyString())).thenReturn(false);
    }

    /**
     * Creates a mock DynamicLootRegistry that reports items available
     * for all slots at COMMON rarity with default weights.
     */
    private DynamicLootRegistry createMockRegistry() {
        DynamicLootRegistry registry = mock(DynamicLootRegistry.class);
        lenient().when(registry.isDiscovered()).thenReturn(true);

        // All rarities available
        lenient().when(registry.getAvailableRarities())
            .thenReturn(EnumSet.allOf(GearRarity.class));

        // All slots available at any rarity
        lenient().when(registry.getAvailableSlotsForRarity(any()))
            .thenReturn(EnumSet.allOf(EquipmentSlot.class));

        // Default slot weights
        Map<EquipmentSlot, Integer> weights = new EnumMap<>(EquipmentSlot.class);
        weights.put(EquipmentSlot.WEAPON, 30);
        weights.put(EquipmentSlot.HEAD, 15);
        weights.put(EquipmentSlot.CHEST, 15);
        weights.put(EquipmentSlot.LEGS, 15);
        weights.put(EquipmentSlot.HANDS, 15);
        weights.put(EquipmentSlot.OFF_HAND, 10);
        lenient().when(registry.getSlotWeights()).thenReturn(weights);

        // Categories available for any (rarity, slot)
        lenient().when(registry.getAvailableCategoriesForRaritySlot(any(), any()))
            .thenReturn(Set.of("Sword", "Axe", "Mace"));

        // Default category weights
        lenient().when(registry.getCategoryWeights(any()))
            .thenReturn(Map.of("Sword", 1.0, "Axe", 1.0, "Mace", 1.0));

        // Return a valid skin
        lenient().when(registry.selectSkin(any(), anyString(), any()))
            .thenReturn("Weapon_Sword_Iron");

        return registry;
    }

    // =========================================================================
    // DROP GENERATION TESTS
    // =========================================================================

    @Nested
    @DisplayName("Drop Generation Tests")
    class DropGenerationTests {

        @Test
        @DisplayName("generateDrops returns empty list for NO_DROP")
        void generateDrops_NoDrop_ReturnsEmpty() {
            GearGenerator realGenerator = new GearGenerator(balanceConfig, modifierConfig, EquipmentStatConfig.unrestricted(), itemRegistry);
            LootGenerator lootGen = new LootGenerator(realGenerator, createMockRegistry());

            List<ItemStack> drops = lootGen.generateDrops(LootRoll.NO_DROP);

            assertTrue(drops.isEmpty());
        }

        @Test
        @DisplayName("generateDrops returns exact drop count when generation succeeds")
        void generateDrops_ValidRoll_MatchesCount() {
            GearGenerator mockGenerator = mock(GearGenerator.class);
            RarityRoller mockRarityRoller = mock(RarityRoller.class);
            when(mockGenerator.getRarityRoller()).thenReturn(mockRarityRoller);
            when(mockRarityRoller.roll(anyDouble(), any())).thenReturn(GearRarity.COMMON);

            DynamicLootRegistry registry = createMockRegistry();
            LootGenerator lootGen = spy(new LootGenerator(mockGenerator, registry, new Random(12345)));
            ItemStack baseItem = mock(ItemStack.class);
            ItemStack generatedItem = mock(ItemStack.class);

            doReturn(baseItem).when(lootGen).createBaseItem(anyString());
            when(mockGenerator.generate(eq(baseItem), eq(50), anyString(), any(GearRarity.class)))
                .thenReturn(generatedItem);

            LootRoll roll = new LootRoll(true, 3, 25.0, 50);
            List<ItemStack> drops = lootGen.generateDrops(roll);

            assertEquals(3, drops.size(), "Should generate exactly dropCount items");
            assertTrue(drops.stream().allMatch(Objects::nonNull), "Generated drops must all be non-null");
        }

        @Test
        @DisplayName("generateSingleDrop returns item when generation succeeds")
        void generateSingleDrop_ReturnsItem() {
            GearGenerator mockGenerator = mock(GearGenerator.class);
            RarityRoller mockRarityRoller = mock(RarityRoller.class);
            when(mockGenerator.getRarityRoller()).thenReturn(mockRarityRoller);
            when(mockRarityRoller.roll(anyDouble(), any())).thenReturn(GearRarity.RARE);

            DynamicLootRegistry registry = createMockRegistry();
            LootGenerator lootGen = spy(new LootGenerator(mockGenerator, registry, new Random(12345)));
            ItemStack baseItem = mock(ItemStack.class);
            ItemStack generatedItem = mock(ItemStack.class);

            doReturn(baseItem).when(lootGen).createBaseItem(anyString());
            when(mockGenerator.generate(eq(baseItem), eq(50), anyString(), any(GearRarity.class)))
                .thenReturn(generatedItem);

            ItemStack drop = lootGen.generateSingleDrop(50, 25.0);

            assertNotNull(drop);
            assertSame(generatedItem, drop);
        }

        @Test
        @DisplayName("generateSingleDrop returns null when registry not discovered")
        void generateSingleDrop_NotDiscovered_ReturnsNull() {
            GearGenerator realGenerator = new GearGenerator(balanceConfig, modifierConfig, EquipmentStatConfig.unrestricted(), itemRegistry);
            DynamicLootRegistry registry = mock(DynamicLootRegistry.class);
            when(registry.isDiscovered()).thenReturn(false);
            when(registry.getSlotWeights()).thenReturn(Map.of());

            LootGenerator lootGen = new LootGenerator(realGenerator, registry);

            assertNull(lootGen.generateSingleDrop(50, 25.0));
        }
    }

    // =========================================================================
    // BUILDER TESTS
    // =========================================================================

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("builder can chain all methods without throwing")
        void builder_CanChainAllMethods() {
            GearGenerator realGenerator = new GearGenerator(balanceConfig, modifierConfig, EquipmentStatConfig.unrestricted(), itemRegistry);
            LootGenerator lootGen = new LootGenerator(realGenerator, createMockRegistry(), new Random(12345));

            assertDoesNotThrow(() -> {
                lootGen.drop()
                    .level(50)
                    .slot(EquipmentSlot.LEGS)
                    .rarityBonus(25.0)
                    .rarity(GearRarity.LEGENDARY)
                    .baseItem("Weapon_Sword_Iron")
                    .build();
            });
        }

        @Test
        @DisplayName("builder slot method accepts all slots")
        void builder_SlotMethodAcceptsAllSlots() {
            GearGenerator realGenerator = new GearGenerator(balanceConfig, modifierConfig, EquipmentStatConfig.unrestricted(), itemRegistry);
            LootGenerator lootGen = new LootGenerator(realGenerator, createMockRegistry(), new Random(12345));

            for (EquipmentSlot slot : EquipmentSlot.values()) {
                assertDoesNotThrow(() -> {
                    lootGen.drop()
                        .level(1)
                        .slot(slot)
                        .build();
                });
            }
        }

        @Test
        @DisplayName("builder rarity method accepts all rarities")
        void builder_RarityMethodAcceptsAllRarities() {
            GearGenerator realGenerator = new GearGenerator(balanceConfig, modifierConfig, EquipmentStatConfig.unrestricted(), itemRegistry);
            LootGenerator lootGen = new LootGenerator(realGenerator, createMockRegistry(), new Random(12345));

            for (GearRarity rarity : GearRarity.values()) {
                assertDoesNotThrow(() -> {
                    lootGen.drop()
                        .level(1)
                        .rarity(rarity)
                        .build();
                });
            }
        }
    }

    // =========================================================================
    // SLOT WEIGHT TESTS
    // =========================================================================

    @Nested
    @DisplayName("Slot Weight Tests")
    class SlotWeightTests {

        @Test
        @DisplayName("getSlotWeights returns weights from registry")
        void getSlotWeights_ReturnsRegistryWeights() {
            GearGenerator realGenerator = new GearGenerator(balanceConfig, modifierConfig, EquipmentStatConfig.unrestricted(), itemRegistry);
            LootGenerator lootGen = new LootGenerator(realGenerator, createMockRegistry());

            Map<EquipmentSlot, Integer> weights = lootGen.getSlotWeights();

            assertFalse(weights.isEmpty());
            assertEquals(30, weights.get(EquipmentSlot.WEAPON));
            assertEquals(10, weights.get(EquipmentSlot.OFF_HAND));
        }

        @Test
        @DisplayName("weapon has highest weight")
        void weapon_HighestWeight() {
            GearGenerator realGenerator = new GearGenerator(balanceConfig, modifierConfig, EquipmentStatConfig.unrestricted(), itemRegistry);
            LootGenerator lootGen = new LootGenerator(realGenerator, createMockRegistry());

            Map<EquipmentSlot, Integer> weights = lootGen.getSlotWeights();
            int maxWeight = weights.values().stream().mapToInt(Integer::intValue).max().orElse(0);
            assertEquals(weights.get(EquipmentSlot.WEAPON), maxWeight);
        }
    }

    // =========================================================================
    // INTEGRATION TESTS
    // =========================================================================

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("full loot generation pipeline does not throw")
        void fullPipeline_DoesNotThrow() {
            GearGenerator realGenerator = new GearGenerator(balanceConfig, modifierConfig, EquipmentStatConfig.unrestricted(), itemRegistry);
            LootGenerator lootGen = new LootGenerator(realGenerator, createMockRegistry(), new Random(12345));

            LootRoll roll = new LootRoll(true, 1, 10.0, 50);

            assertDoesNotThrow(() -> lootGen.generateDrops(roll));
        }

        @Test
        @DisplayName("generateSingleDrop with real generator does not throw")
        void generateSingleDrop_RealGenerator() {
            GearGenerator realGenerator = new GearGenerator(balanceConfig, modifierConfig, EquipmentStatConfig.unrestricted(), itemRegistry);
            LootGenerator lootGen = new LootGenerator(realGenerator, createMockRegistry(), new Random(12345));

            assertDoesNotThrow(() -> lootGen.generateSingleDrop(50, 25.0));
        }
    }

    // =========================================================================
    // ACCESSOR TESTS
    // =========================================================================

    @Nested
    @DisplayName("Accessor Tests")
    class AccessorTests {

        @Test
        @DisplayName("getGearGenerator returns injected generator")
        void getGearGenerator_ReturnsInjected() {
            GearGenerator realGenerator = new GearGenerator(balanceConfig, modifierConfig, EquipmentStatConfig.unrestricted(), itemRegistry);
            DynamicLootRegistry registry = createMockRegistry();
            LootGenerator lootGen = new LootGenerator(realGenerator, registry);

            assertSame(realGenerator, lootGen.getGearGenerator());
        }

        @Test
        @DisplayName("getDynamicRegistry returns injected registry")
        void getDynamicRegistry_ReturnsInjected() {
            GearGenerator realGenerator = new GearGenerator(balanceConfig, modifierConfig, EquipmentStatConfig.unrestricted(), itemRegistry);
            DynamicLootRegistry registry = createMockRegistry();
            LootGenerator lootGen = new LootGenerator(realGenerator, registry);

            assertSame(registry, lootGen.getDynamicRegistry());
        }
    }
}
