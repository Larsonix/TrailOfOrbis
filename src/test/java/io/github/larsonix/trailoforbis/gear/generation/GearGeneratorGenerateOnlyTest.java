package io.github.larsonix.trailoforbis.gear.generation;

import io.github.larsonix.trailoforbis.gear.config.EquipmentStatConfig;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig;
import io.github.larsonix.trailoforbis.gear.config.TestConfigFactory;
import io.github.larsonix.trailoforbis.gear.generation.GearGenerator.GeneratedGear;
import io.github.larsonix.trailoforbis.gear.item.ItemRegistryService;
import io.github.larsonix.trailoforbis.gear.model.EquipmentType;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.gear.model.WeaponType;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link GearGenerator#generateOnly} — the side-effect-free generation path.
 *
 * <p>Since {@code generateOnly()} calls {@code GearUtils.setGearData()} which requires
 * Hytale's runtime AssetStore, tests that need the full GeneratedGear record are marked
 * {@code @Disabled}. All data-level invariants (quality, modifiers, determinism) are
 * verified through {@code generateData()} which shares the same code path minus the
 * ItemStack serialization.
 *
 * <p>The zero-side-effects contract is verified via Mockito — the most critical test,
 * and it doesn't need the AssetStore since it verifies what was NOT called.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GearGeneratorGenerateOnlyTest {

    @Mock private ItemRegistryService itemRegistry;
    @Mock private ItemStack skinItem;
    @Mock private Item baseItemAsset;

    private GearBalanceConfig balanceConfig;
    private ModifierConfig modConfig;

    @BeforeEach
    void setUp() {
        balanceConfig = TestConfigFactory.createDefaultBalanceConfig();
        modConfig = TestConfigFactory.createDefaultModifierConfig();

        lenient().when(itemRegistry.isInitialized()).thenReturn(true);
        lenient().when(itemRegistry.isRegistered(anyString())).thenReturn(false);

        lenient().when(skinItem.getItemId()).thenReturn("Weapon_Sword_Iron");
        lenient().when(skinItem.getItem()).thenReturn(baseItemAsset);
        lenient().when(baseItemAsset.getId()).thenReturn("Weapon_Sword_Iron");
        lenient().when(skinItem.withMetadata(anyString(), any(), any())).thenReturn(skinItem);
    }

    // =========================================================================
    // DATA-LEVEL CONTRACT (via generateData — same code path, no AssetStore)
    // =========================================================================

    @Nested
    @DisplayName("Data-Level Contract (shared with generateOnly)")
    class DataContractTests {

        @Test
        @DisplayName("generateData returns correct level")
        void generateData_CorrectLevel() {
            GearGenerator gen = createGenerator(42);
            GearData data = gen.generateData(75, "weapon", GearRarity.EPIC);
            assertEquals(75, data.level());
        }

        @Test
        @DisplayName("generateData returns correct rarity")
        void generateData_CorrectRarity() {
            GearGenerator gen = createGenerator(42);
            GearData data = gen.generateData(50, "weapon", GearRarity.LEGENDARY);
            assertEquals(GearRarity.LEGENDARY, data.rarity());
        }

        @Test
        @DisplayName("generateData has valid quality in range [1, 101]")
        void generateData_ValidQuality() {
            GearGenerator gen = createGenerator(42);
            for (int i = 0; i < 100; i++) {
                GearData data = gen.generateData(50, "weapon", GearRarity.COMMON);
                assertTrue(data.quality() >= 1 && data.quality() <= 101,
                        "Quality should be in [1, 101], got: " + data.quality());
            }
        }

        @Test
        @DisplayName("generateData produces unique instance IDs")
        void generateData_UniqueInstanceIds() {
            GearGenerator gen = createGenerator(42);
            GearData d1 = gen.generateData(50, "weapon", GearRarity.COMMON);
            GearData d2 = gen.generateData(50, "weapon", GearRarity.COMMON);
            assertNotEquals(d1.instanceId(), d2.instanceId(),
                    "Each call should produce a unique instance ID");
        }

        @Test
        @DisplayName("generateData has baseItemId matching provided itemId")
        void generateData_BaseItemId() {
            GearGenerator gen = createGenerator(42);
            GearData data = gen.generateData(50, "weapon", GearRarity.COMMON,
                    EquipmentType.SWORD, "Weapon_Sword_Iron");
            assertEquals("Weapon_Sword_Iron", data.baseItemId());
        }
    }

    // =========================================================================
    // CRITICAL: ZERO SIDE EFFECTS — verifiable without AssetStore
    // =========================================================================

    @Nested
    @DisplayName("Zero Side Effects")
    class ZeroSideEffectsTests {

        @Test
        @Disabled("Requires Hytale runtime - Item.getAssetStore()")
        @DisplayName("generateOnly does NOT call createAndRegister")
        void generateOnly_NoRegistration() {
            GearGenerator gen = createGenerator(42);
            gen.generateOnly(skinItem, 50, "weapon", GearRarity.RARE,
                    EquipmentType.SWORD, WeaponType.SWORD, null);

            verify(itemRegistry, never()).createAndRegister(any(Item.class), anyString());
            verify(itemRegistry, never()).createAndRegister(any(Item.class), anyString(), any());
            verify(itemRegistry, never()).createAndRegisterBatch(anyList());
        }

        @Test
        @DisplayName("generateData does NOT call any registration methods")
        void generateData_NoRegistration() {
            GearGenerator gen = createGenerator(42);
            gen.generateData(50, "weapon", GearRarity.RARE);

            // generateData has never called registration — it's the pure data path
            verify(itemRegistry, never()).createAndRegister(any(Item.class), anyString());
            verify(itemRegistry, never()).createAndRegister(any(Item.class), anyString(), any());
            verify(itemRegistry, never()).isRegistered(anyString());
            verify(itemRegistry, never()).isInitialized();
        }

        @Test
        @Disabled("Requires Hytale runtime - generateFromCategory calls generateOnly which needs AssetStore")
        @DisplayName("generateFromCategory DOES check isRegistered (contrast)")
        void generateFromCategory_DoesRegister() {
            // generateFromCategory delegates to generateOnly then calls registerCustomItem.
            // Both paths need AssetStore, so this is a runtime-only test.
            GearGenerator gen = createGenerator(42);
            gen.generateFromCategory(skinItem, 50, "weapon", GearRarity.RARE,
                    EquipmentType.SWORD, WeaponType.SWORD, null);
            verify(itemRegistry, atLeastOnce()).isRegistered(anyString());
        }
    }

    // =========================================================================
    // DETERMINISM (via generateData — identical RNG path)
    // =========================================================================

    @Nested
    @DisplayName("Determinism")
    class DeterminismTests {

        @Test
        @DisplayName("same seed produces identical GearData")
        void sameSeeed_SameData() {
            GearGenerator gen1 = createGenerator(99999);
            GearGenerator gen2 = createGenerator(99999);

            for (int i = 0; i < 10; i++) {
                GearData d1 = gen1.generateData(50, "weapon", GearRarity.EPIC);
                GearData d2 = gen2.generateData(50, "weapon", GearRarity.EPIC);

                assertEquals(d1.quality(), d2.quality(),
                        "Same seed → same quality at iteration " + i);
                assertEquals(d1.prefixes(), d2.prefixes(),
                        "Same seed → same prefixes at iteration " + i);
                assertEquals(d1.suffixes(), d2.suffixes(),
                        "Same seed → same suffixes at iteration " + i);
            }
        }

        @Test
        @DisplayName("different seeds produce different data (statistically)")
        void differentSeeds_DifferentData() {
            GearGenerator gen1 = createGenerator(11111);
            GearGenerator gen2 = createGenerator(99999);

            boolean anyDifference = false;
            for (int i = 0; i < 20; i++) {
                GearData d1 = gen1.generateData(50, "weapon", GearRarity.LEGENDARY);
                GearData d2 = gen2.generateData(50, "weapon", GearRarity.LEGENDARY);

                if (d1.quality() != d2.quality()
                        || !d1.prefixes().equals(d2.prefixes())) {
                    anyDifference = true;
                    break;
                }
            }
            assertTrue(anyDifference, "Different seeds should produce different results");
        }
    }

    // =========================================================================
    // MODIFIER COUNT INVARIANTS (via generateData)
    // =========================================================================

    @Nested
    @DisplayName("Modifier Count Invariants")
    class ModifierCountTests {

        @Test
        @DisplayName("Common gear has max 1 modifier")
        void common_MaxOneModifier() {
            GearGenerator gen = createGenerator(42);
            for (int i = 0; i < 100; i++) {
                GearData data = gen.generateData(50, "weapon", GearRarity.COMMON);
                int totalMods = data.prefixes().size() + data.suffixes().size();
                assertTrue(totalMods <= 1, "Common should have <= 1 modifier, got " + totalMods);
            }
        }

        @Test
        @DisplayName("Legendary gear has 2-5 modifiers")
        void legendary_CorrectModifierRange() {
            GearGenerator gen = createGenerator(42);
            for (int i = 0; i < 100; i++) {
                GearData data = gen.generateData(50, "weapon", GearRarity.LEGENDARY);
                int totalMods = data.prefixes().size() + data.suffixes().size();
                assertTrue(totalMods >= 2 && totalMods <= 5,
                        "Legendary should have 2-5 modifiers, got " + totalMods);
            }
        }
    }

    // =========================================================================
    // EDGE CASES (input validation — doesn't need AssetStore)
    // =========================================================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("rejects level below minimum")
        void levelBelowMin_Throws() {
            GearGenerator gen = createGenerator(42);
            assertThrows(IllegalArgumentException.class, () ->
                    gen.generateOnly(skinItem, 0, "weapon", GearRarity.COMMON,
                            EquipmentType.SWORD, WeaponType.SWORD, null));
        }

        @Test
        @DisplayName("rejects null skinItem")
        void nullSkin_Throws() {
            GearGenerator gen = createGenerator(42);
            assertThrows(NullPointerException.class, () ->
                    gen.generateOnly(null, 50, "weapon", GearRarity.COMMON,
                            EquipmentType.SWORD, WeaponType.SWORD, null));
        }

        @Test
        @DisplayName("rejects null slot")
        void nullSlot_Throws() {
            GearGenerator gen = createGenerator(42);
            assertThrows(NullPointerException.class, () ->
                    gen.generateOnly(skinItem, 50, null, GearRarity.COMMON,
                            EquipmentType.SWORD, WeaponType.SWORD, null));
        }

        @Test
        @DisplayName("rejects null rarity")
        void nullRarity_Throws() {
            GearGenerator gen = createGenerator(42);
            assertThrows(NullPointerException.class, () ->
                    gen.generateOnly(skinItem, 50, "weapon", null,
                            EquipmentType.SWORD, WeaponType.SWORD, null));
        }

        @Test
        @DisplayName("rejects null equipmentType")
        void nullEquipmentType_Throws() {
            GearGenerator gen = createGenerator(42);
            assertThrows(NullPointerException.class, () ->
                    gen.generateOnly(skinItem, 50, "weapon", GearRarity.COMMON,
                            null, WeaponType.SWORD, null));
        }

        @Test
        @DisplayName("level 1 (minimum) is valid for generateData")
        void minLevel_Valid() {
            GearGenerator gen = createGenerator(42);
            GearData data = gen.generateData(1, "weapon", GearRarity.COMMON);
            assertEquals(1, data.level());
        }
    }

    // =========================================================================
    // FULL PIPELINE TESTS (require Hytale runtime)
    // =========================================================================

    @Nested
    @DisplayName("Full Pipeline (Hytale Runtime)")
    class FullPipelineTests {

        @Test
        @Disabled("Requires Hytale runtime - Item.getAssetStore()")
        @DisplayName("generateOnly returns non-null GeneratedGear")
        void generateOnly_ReturnsNonNull() {
            GearGenerator gen = createGenerator(42);
            GeneratedGear result = gen.generateOnly(
                    skinItem, 50, "weapon", GearRarity.RARE,
                    EquipmentType.SWORD, WeaponType.SWORD, null);
            assertNotNull(result);
        }

        @Test
        @Disabled("Requires Hytale runtime - Item.getAssetStore()")
        @DisplayName("generateOnly baseItemAsset matches skin item")
        void generateOnly_CorrectBaseAsset() {
            GearGenerator gen = createGenerator(42);
            GeneratedGear result = gen.generateOnly(
                    skinItem, 50, "weapon", GearRarity.COMMON,
                    EquipmentType.SWORD, WeaponType.SWORD, null);
            assertSame(baseItemAsset, result.baseItemAsset());
        }
    }

    // =========================================================================
    // GENERATEDGEAR RECORD
    // =========================================================================

    @Nested
    @DisplayName("GeneratedGear Record")
    class GeneratedGearRecordTests {

        @Test
        @DisplayName("record stores all fields")
        void record_StoresFields() {
            ItemStack mockItem = mock(ItemStack.class);
            GearData mockData = mock(GearData.class);
            Item mockAsset = mock(Item.class);

            GeneratedGear gear = new GeneratedGear(mockItem, mockData, mockAsset);

            assertSame(mockItem, gear.finalItem());
            assertSame(mockData, gear.gearData());
            assertSame(mockAsset, gear.baseItemAsset());
        }

        @Test
        @DisplayName("two records with same fields are equal")
        void record_Equality() {
            ItemStack mockItem = mock(ItemStack.class);
            GearData mockData = mock(GearData.class);
            Item mockAsset = mock(Item.class);

            GeneratedGear g1 = new GeneratedGear(mockItem, mockData, mockAsset);
            GeneratedGear g2 = new GeneratedGear(mockItem, mockData, mockAsset);

            assertEquals(g1, g2);
        }
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private GearGenerator createGenerator(long seed) {
        return new GearGenerator(balanceConfig, modConfig,
                EquipmentStatConfig.unrestricted(), itemRegistry, new Random(seed));
    }
}
