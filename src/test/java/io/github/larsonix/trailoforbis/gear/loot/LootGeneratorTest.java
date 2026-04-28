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
import static org.mockito.Mockito.lenient;

/**
 * Tests for LootGenerator - 20 test cases.
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

        // Create mock ItemRegistryService with lenient stubbing (not all tests call these)
        itemRegistry = mock(ItemRegistryService.class);
        lenient().when(itemRegistry.isInitialized()).thenReturn(true);
        lenient().when(itemRegistry.isRegistered(anyString())).thenReturn(false);
    }

    // =========================================================================
    // SLOT SELECTION TESTS
    // =========================================================================

    @Nested
    @DisplayName("Slot Selection Tests")
    class SlotSelectionTests {

        @Test
        @DisplayName("selectRandomSlot returns all slot types over many trials")
        void selectRandomSlot_ReturnsAllSlots() {
            GearGenerator realGenerator = new GearGenerator(balanceConfig, modifierConfig, EquipmentStatConfig.unrestricted(), itemRegistry);
            LootGenerator lootGen = new LootGenerator(realGenerator);

            Set<EquipmentSlot> selectedSlots = new HashSet<>();
            for (int i = 0; i < 1000; i++) {
                selectedSlots.add(lootGen.selectRandomSlot());
            }

            assertEquals(6, selectedSlots.size(), "Should eventually select all slot types");
        }

        @Test
        @DisplayName("slot weights affect distribution")
        void slotWeights_AffectDistribution() {
            GearGenerator realGenerator = new GearGenerator(balanceConfig, modifierConfig, EquipmentStatConfig.unrestricted(), itemRegistry);
            LootGenerator lootGen = new LootGenerator(realGenerator);

            Map<EquipmentSlot, Integer> counts = new EnumMap<>(EquipmentSlot.class);
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                counts.put(slot, 0);
            }

            for (int i = 0; i < 10000; i++) {
                EquipmentSlot slot = lootGen.selectRandomSlot();
                counts.merge(slot, 1, Integer::sum);
            }

            // WEAPON should be more common than OFF_HAND based on weights
            assertTrue(counts.get(EquipmentSlot.WEAPON) > counts.get(EquipmentSlot.OFF_HAND),
                "Weapon should be more common than off-hand");
        }

        @Test
        @DisplayName("selectBaseItem returns non-null for all slots")
        void selectBaseItem_ReturnsNonNull() {
            GearGenerator realGenerator = new GearGenerator(balanceConfig, modifierConfig, EquipmentStatConfig.unrestricted(), itemRegistry);
            LootGenerator lootGen = new LootGenerator(realGenerator);

            for (EquipmentSlot slot : EquipmentSlot.values()) {
                String itemId = lootGen.selectBaseItem(slot);
                assertNotNull(itemId, "Should have base items for slot: " + slot);
            }
        }
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
            LootGenerator lootGen = new LootGenerator(realGenerator);

            List<ItemStack> drops = lootGen.generateDrops(LootRoll.NO_DROP);

            assertTrue(drops.isEmpty());
        }

        @Test
        @DisplayName("generateDrops returns exact drop count when generation succeeds")
        void generateDrops_ValidRoll_MatchesCount() {
            GearGenerator mockGenerator = mock(GearGenerator.class);
            RarityRoller mockRarityRoller = mock(RarityRoller.class);
            when(mockGenerator.getRarityRoller()).thenReturn(mockRarityRoller);
            when(mockRarityRoller.roll(anyDouble())).thenReturn(GearRarity.COMMON);

            LootGenerator lootGen = spy(new LootGenerator(mockGenerator, new Random(12345)));
            ItemStack baseItem = mock(ItemStack.class);
            ItemStack generatedItem = mock(ItemStack.class);

            doReturn(baseItem).when(lootGen).createBaseItem(anyString());
            when(mockGenerator.generate(eq(baseItem), eq(50), anyString(), any(GearRarity.class)))
                .thenReturn(generatedItem);

            LootRoll roll = new LootRoll(true, 3, 25.0, 50);
            List<ItemStack> drops = lootGen.generateDrops(roll);

            assertEquals(3, drops.size(), "Should generate exactly dropCount items");
            assertTrue(drops.stream().allMatch(Objects::nonNull), "Generated drops must all be non-null");
            verify(mockGenerator, times(3))
                .generate(eq(baseItem), eq(50), anyString(), any(GearRarity.class));
        }

        @Test
        @DisplayName("generateSingleDrop returns item when generation succeeds")
        void generateSingleDrop_ReturnsItem() {
            GearGenerator mockGenerator = mock(GearGenerator.class);
            RarityRoller mockRarityRoller = mock(RarityRoller.class);
            when(mockGenerator.getRarityRoller()).thenReturn(mockRarityRoller);
            when(mockRarityRoller.roll(anyDouble())).thenReturn(GearRarity.RARE);

            LootGenerator lootGen = spy(new LootGenerator(mockGenerator, new Random(12345)));
            ItemStack baseItem = mock(ItemStack.class);
            ItemStack generatedItem = mock(ItemStack.class);

            doReturn(baseItem).when(lootGen).createBaseItem(anyString());
            when(mockGenerator.generate(eq(baseItem), eq(50), anyString(), any(GearRarity.class)))
                .thenReturn(generatedItem);

            ItemStack drop = lootGen.generateSingleDrop(50, 25.0);

            assertNotNull(drop);
            assertSame(generatedItem, drop);
        }
    }

    // =========================================================================
    // BUILDER TESTS
    // =========================================================================

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("builder level method works")
        void builder_LevelMethodWorks() {
            GearGenerator realGenerator = new GearGenerator(balanceConfig, modifierConfig, EquipmentStatConfig.unrestricted(), itemRegistry);
            LootGenerator lootGen = new LootGenerator(realGenerator, new Random(12345));

            // Just test the builder doesn't throw
            assertDoesNotThrow(() -> {
                lootGen.drop()
                    .level(50)
                    .build();
            });
        }

        @Test
        @DisplayName("builder can chain all methods")
        void builder_CanChainAllMethods() {
            GearGenerator realGenerator = new GearGenerator(balanceConfig, modifierConfig, EquipmentStatConfig.unrestricted(), itemRegistry);
            LootGenerator lootGen = new LootGenerator(realGenerator, new Random(12345));

            // Just test the builder chain doesn't throw
            assertDoesNotThrow(() -> {
                lootGen.drop()
                    .level(50)
                    .slot(EquipmentSlot.LEGS)
                    .rarityBonus(25.0)
                    .rarity(GearRarity.LEGENDARY)
                    .baseItem("hytale:iron_sword")
                    .build();
            });
        }

        @Test
        @DisplayName("builder slot method accepts all slots")
        void builder_SlotMethodAcceptsAllSlots() {
            GearGenerator realGenerator = new GearGenerator(balanceConfig, modifierConfig, EquipmentStatConfig.unrestricted(), itemRegistry);
            LootGenerator lootGen = new LootGenerator(realGenerator, new Random(12345));

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
            LootGenerator lootGen = new LootGenerator(realGenerator, new Random(12345));

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
    // STATIC DATA TESTS
    // =========================================================================

    @Nested
    @DisplayName("Config Data Tests")
    class ConfigDataTests {

        private LootGenerator lootGenerator;

        @BeforeEach
        void setUpConfigTests() {
            GearGenerator gearGen = new GearGenerator(balanceConfig, modifierConfig, EquipmentStatConfig.unrestricted(), itemRegistry);
            lootGenerator = new LootGenerator(gearGen);
        }

        @Test
        @DisplayName("getBaseItems returns non-empty map")
        void getBaseItems_NonEmpty() {
            Map<EquipmentSlot, List<String>> baseItems = lootGenerator.getBaseItems();

            assertFalse(baseItems.isEmpty());
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                assertTrue(baseItems.containsKey(slot), "Should have items for " + slot);
                assertFalse(baseItems.get(slot).isEmpty(), "Items list for " + slot + " should not be empty");
            }
        }

        @Test
        @DisplayName("getSlotWeights returns valid weights")
        void getSlotWeights_ValidWeights() {
            Map<EquipmentSlot, Integer> weights = lootGenerator.getSlotWeights();

            assertFalse(weights.isEmpty());
            int totalWeight = weights.values().stream().mapToInt(Integer::intValue).sum();
            assertEquals(100, totalWeight, "Slot weights should sum to 100");
        }

        @Test
        @DisplayName("weapon has highest weight")
        void weapon_HighestWeight() {
            Map<EquipmentSlot, Integer> weights = lootGenerator.getSlotWeights();

            int maxWeight = weights.values().stream().mapToInt(Integer::intValue).max().orElse(0);
            assertEquals(weights.get(EquipmentSlot.WEAPON), maxWeight);
        }

        @Test
        @DisplayName("all slots have positive weights")
        void allSlots_PositiveWeights() {
            Map<EquipmentSlot, Integer> weights = lootGenerator.getSlotWeights();

            for (EquipmentSlot slot : EquipmentSlot.values()) {
                assertTrue(weights.get(slot) > 0, "Slot " + slot + " should have positive weight");
            }
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
            LootGenerator lootGen = new LootGenerator(realGenerator, new Random(12345));

            LootRoll roll = new LootRoll(true, 1, 10.0, 50);

            assertDoesNotThrow(() -> lootGen.generateDrops(roll));
        }

        @Test
        @DisplayName("generateSingleDrop with real generator")
        void generateSingleDrop_RealGenerator() {
            GearGenerator realGenerator = new GearGenerator(balanceConfig, modifierConfig, EquipmentStatConfig.unrestricted(), itemRegistry);
            LootGenerator lootGen = new LootGenerator(realGenerator, new Random(12345));

            // May return null if item creation fails, but should not throw
            assertDoesNotThrow(() -> lootGen.generateSingleDrop(50, 25.0));
        }
    }

    // =========================================================================
    // DETERMINISM TESTS
    // =========================================================================

    @Nested
    @DisplayName("Determinism Tests")
    class DeterminismTests {

        @Test
        @DisplayName("same seed produces same slot selection")
        void sameSeed_SameSlotSelection() {
            GearGenerator realGenerator = new GearGenerator(balanceConfig, modifierConfig, EquipmentStatConfig.unrestricted(), itemRegistry);

            LootGenerator gen1 = new LootGenerator(realGenerator, new Random(99999));
            LootGenerator gen2 = new LootGenerator(realGenerator, new Random(99999));

            for (int i = 0; i < 10; i++) {
                assertEquals(gen1.selectRandomSlot(), gen2.selectRandomSlot());
            }
        }

        @Test
        @DisplayName("same seed produces same base item selection")
        void sameSeed_SameBaseItemSelection() {
            GearGenerator realGenerator = new GearGenerator(balanceConfig, modifierConfig, EquipmentStatConfig.unrestricted(), itemRegistry);

            LootGenerator gen1 = new LootGenerator(realGenerator, new Random(99999));
            LootGenerator gen2 = new LootGenerator(realGenerator, new Random(99999));

            EquipmentSlot slot = EquipmentSlot.WEAPON;
            for (int i = 0; i < 10; i++) {
                assertEquals(gen1.selectBaseItem(slot), gen2.selectBaseItem(slot));
            }
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
            LootGenerator lootGen = new LootGenerator(realGenerator);

            assertEquals(realGenerator, lootGen.getGearGenerator());
        }
    }
}
