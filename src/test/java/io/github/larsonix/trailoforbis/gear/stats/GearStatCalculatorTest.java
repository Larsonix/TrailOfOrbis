package io.github.larsonix.trailoforbis.gear.stats;

import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig;
import io.github.larsonix.trailoforbis.gear.equipment.EquipmentValidator;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.GearModifier;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.gear.model.ModifierType;
import io.github.larsonix.trailoforbis.gear.stats.GearStatCalculator.GearBonuses;

import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for GearStatCalculator.
 */
@ExtendWith(MockitoExtension.class)
class GearStatCalculatorTest {

    @Mock
    private GearBalanceConfig balanceConfig;

    @Mock
    private EquipmentValidator validator;

    @Mock
    private Inventory inventory;

    @Mock
    private ItemContainer armorContainer;

    private GearStatCalculator calculator;
    private UUID playerId;

    @BeforeEach
    void setUp() {
        // Pass null for ItemRegistryService in tests - it's optional
        calculator = new GearStatCalculator(balanceConfig, validator, null);
        playerId = UUID.randomUUID();
    }

    // =========================================================================
    // Empty / Null tests
    // =========================================================================

    @Test
    @DisplayName("calculateBonuses - empty inventory returns empty bonuses")
    void calculateBonuses_EmptyInventory_EmptyBonuses() {
        when(inventory.getArmor()).thenReturn(armorContainer);
        when(armorContainer.getCapacity()).thenReturn((short) 4);
        when(armorContainer.getItemStack(anyShort())).thenReturn(null);
        when(inventory.getItemInHand()).thenReturn(null);
        when(inventory.getUtility()).thenReturn(null);

        GearBonuses bonuses = calculator.calculateBonuses(playerId, inventory);

        assertTrue(bonuses.isEmpty());
    }

    @Test
    @DisplayName("calculateBonuses - null armor container handled")
    void calculateBonuses_NullArmorContainer_Handled() {
        when(inventory.getArmor()).thenReturn(null);
        when(inventory.getItemInHand()).thenReturn(null);
        when(inventory.getUtility()).thenReturn(null);

        GearBonuses bonuses = calculator.calculateBonuses(playerId, inventory);

        assertTrue(bonuses.isEmpty());
    }

    // =========================================================================
    // GearBonuses record tests
    // =========================================================================

    @Test
    @DisplayName("GearBonuses.EMPTY is empty")
    void gearBonuses_EMPTY_isEmpty() {
        assertTrue(GearBonuses.EMPTY.isEmpty());
    }

    @Test
    @DisplayName("GearBonuses.getFlat returns 0 for missing stat")
    void gearBonuses_getFlat_MissingStat_ReturnsZero() {
        assertEquals(0.0, GearBonuses.EMPTY.getFlat("nonexistent"));
    }

    @Test
    @DisplayName("GearBonuses.getPercent returns 0 for missing stat")
    void gearBonuses_getPercent_MissingStat_ReturnsZero() {
        assertEquals(0.0, GearBonuses.EMPTY.getPercent("nonexistent"));
    }

    @Test
    @DisplayName("GearBonuses.getFlat is case insensitive")
    void gearBonuses_getFlat_CaseInsensitive() {
        GearBonuses bonuses = new GearBonuses(
            java.util.Map.of("physical_damage", 10.0),
            java.util.Map.of(),
            0.0,
            null,
            false
        );
        assertEquals(10.0, bonuses.getFlat("PHYSICAL_DAMAGE"));
    }

    @Test
    @DisplayName("GearBonuses.getPercent is case insensitive")
    void gearBonuses_getPercent_CaseInsensitive() {
        GearBonuses bonuses = new GearBonuses(
            java.util.Map.of(),
            java.util.Map.of("crit_chance", 5.0),
            0.0,
            null,
            false
        );
        assertEquals(5.0, bonuses.getPercent("CRIT_CHANCE"));
    }

    @Test
    @DisplayName("GearBonuses.isEmpty returns false when has flat bonuses")
    void gearBonuses_isEmpty_WithFlat_ReturnsFalse() {
        GearBonuses bonuses = new GearBonuses(
            java.util.Map.of("physical_damage", 10.0),
            java.util.Map.of(),
            0.0,
            null,
            false
        );
        assertFalse(bonuses.isEmpty());
    }

    @Test
    @DisplayName("GearBonuses.isEmpty returns false when has percent bonuses")
    void gearBonuses_isEmpty_WithPercent_ReturnsFalse() {
        GearBonuses bonuses = new GearBonuses(
            java.util.Map.of(),
            java.util.Map.of("crit_chance", 5.0),
            0.0,
            null,
            false
        );
        assertFalse(bonuses.isEmpty());
    }

    @Test
    @DisplayName("GearBonuses.hasWeaponDamage returns true when weapon damage > 0")
    void gearBonuses_hasWeaponDamage_WithDamage_ReturnsTrue() {
        GearBonuses bonuses = new GearBonuses(
            java.util.Map.of(),
            java.util.Map.of(),
            150.0,
            null,
            true
        );
        assertTrue(bonuses.hasWeaponDamage());
    }

    @Test
    @DisplayName("GearBonuses.hasWeaponDamage returns false when weapon damage is 0")
    void gearBonuses_hasWeaponDamage_WithoutDamage_ReturnsFalse() {
        GearBonuses bonuses = new GearBonuses(
            java.util.Map.of(),
            java.util.Map.of(),
            0.0,
            null,
            false
        );
        assertFalse(bonuses.hasWeaponDamage());
    }

    // =========================================================================
    // Constructor tests
    // =========================================================================

    @Test
    @DisplayName("constructor - null balanceConfig throws NPE")
    void constructor_NullBalanceConfig_ThrowsNPE() {
        assertThrows(NullPointerException.class, () ->
            new GearStatCalculator(null, validator, null));
    }

    @Test
    @DisplayName("constructor - null validator throws NPE")
    void constructor_NullValidator_ThrowsNPE() {
        assertThrows(NullPointerException.class, () ->
            new GearStatCalculator(balanceConfig, null, null));
    }

    @Test
    @DisplayName("constructor - null itemRegistryService is allowed")
    void constructor_NullItemRegistryService_Allowed() {
        // ItemRegistryService is optional - null should not throw
        assertDoesNotThrow(() ->
            new GearStatCalculator(balanceConfig, validator, null));
    }

    // =========================================================================
    // Quality multiplier tests
    // =========================================================================

    @Test
    @DisplayName("GearData.qualityMultiplier - quality 50 gives 1.0x")
    void gearData_QualityMultiplier_Baseline() {
        GearData data = GearData.builder()
            .level(1)
            .rarity(GearRarity.COMMON)
            .quality(50)
            .build();

        assertEquals(1.0, data.qualityMultiplier(), 0.01);
    }

    @Test
    @DisplayName("GearData.qualityMultiplier - quality 100 gives 1.5x")
    void gearData_QualityMultiplier_Max() {
        GearData data = GearData.builder()
            .level(1)
            .rarity(GearRarity.COMMON)
            .quality(100)
            .build();

        assertEquals(1.5, data.qualityMultiplier(), 0.01);
    }

    @Test
    @DisplayName("GearData.qualityMultiplier - quality 25 gives 0.75x")
    void gearData_QualityMultiplier_Low() {
        GearData data = GearData.builder()
            .level(1)
            .rarity(GearRarity.COMMON)
            .quality(25)
            .build();

        assertEquals(0.75, data.qualityMultiplier(), 0.01);
    }

    // =========================================================================
    // Modifier processing tests
    // =========================================================================

    @Test
    @DisplayName("GearModifier.isFlat returns true for flat modifier")
    void gearModifier_isFlat_FlatModifier() {
        GearModifier mod = GearModifier.of(
            "sharp", "Sharp", ModifierType.PREFIX,
            "physical_damage", GearModifier.STAT_TYPE_FLAT, 10.0
        );
        assertTrue(mod.isFlat());
    }

    @Test
    @DisplayName("GearModifier.isPercent returns true for percent modifier")
    void gearModifier_isPercent_PercentModifier() {
        GearModifier mod = GearModifier.of(
            "of_the_whale", "of the Whale", ModifierType.SUFFIX,
            "max_health", GearModifier.STAT_TYPE_PERCENT, 10.0
        );
        assertTrue(mod.isPercent());
    }

    // =========================================================================
    // GearData with modifiers
    // =========================================================================

    @Test
    @DisplayName("GearData with prefixes - allModifiers includes prefixes")
    void gearData_WithPrefixes_AllModifiersIncludesPrefixes() {
        GearModifier prefix = GearModifier.of(
            "sharp", "Sharp", ModifierType.PREFIX,
            "physical_damage", GearModifier.STAT_TYPE_FLAT, 10.0
        );

        GearData data = GearData.builder()
            .level(1)
            .rarity(GearRarity.UNCOMMON)
            .quality(50)
            .prefixes(List.of(prefix))
            .build();

        assertEquals(1, data.allModifiers().size());
        assertEquals(prefix, data.allModifiers().get(0));
    }

    @Test
    @DisplayName("GearData with suffixes - allModifiers includes suffixes")
    void gearData_WithSuffixes_AllModifiersIncludesSuffixes() {
        GearModifier suffix = GearModifier.of(
            "of_vitality", "of Vitality", ModifierType.SUFFIX,
            "max_health", GearModifier.STAT_TYPE_FLAT, 20.0
        );

        GearData data = GearData.builder()
            .level(1)
            .rarity(GearRarity.UNCOMMON)
            .quality(50)
            .suffixes(List.of(suffix))
            .build();

        assertEquals(1, data.allModifiers().size());
        assertEquals(suffix, data.allModifiers().get(0));
    }

    @Test
    @DisplayName("GearData with mixed modifiers - allModifiers includes both")
    void gearData_WithMixed_AllModifiersIncludesBoth() {
        GearModifier prefix = GearModifier.of(
            "sharp", "Sharp", ModifierType.PREFIX,
            "physical_damage", GearModifier.STAT_TYPE_FLAT, 10.0
        );
        GearModifier suffix = GearModifier.of(
            "of_vitality", "of Vitality", ModifierType.SUFFIX,
            "max_health", GearModifier.STAT_TYPE_FLAT, 20.0
        );

        GearData data = GearData.builder()
            .level(1)
            .rarity(GearRarity.RARE)
            .quality(50)
            .prefixes(List.of(prefix))
            .suffixes(List.of(suffix))
            .build();

        assertEquals(2, data.allModifiers().size());
    }

    // =========================================================================
    // Empty modifier lists
    // =========================================================================

    @Test
    @DisplayName("GearData with no modifiers - allModifiers is empty")
    void gearData_NoModifiers_AllModifiersEmpty() {
        GearData data = GearData.builder()
            .level(1)
            .rarity(GearRarity.COMMON)
            .quality(50)
            .build();

        assertTrue(data.allModifiers().isEmpty());
    }

    @Test
    @DisplayName("GearData.hasModifiers returns false when no modifiers")
    void gearData_hasModifiers_NoModifiers_ReturnsFalse() {
        GearData data = GearData.builder()
            .level(1)
            .rarity(GearRarity.COMMON)
            .quality(50)
            .build();

        assertFalse(data.hasModifiers());
    }

    @Test
    @DisplayName("GearData.hasModifiers returns true when has modifiers")
    void gearData_hasModifiers_WithModifiers_ReturnsTrue() {
        GearModifier prefix = GearModifier.of(
            "sharp", "Sharp", ModifierType.PREFIX,
            "physical_damage", GearModifier.STAT_TYPE_FLAT, 10.0
        );

        GearData data = GearData.builder()
            .level(1)
            .rarity(GearRarity.UNCOMMON)
            .quality(50)
            .prefixes(List.of(prefix))
            .build();

        assertTrue(data.hasModifiers());
    }

    @Test
    @DisplayName("GearData.modifierCount returns correct count")
    void gearData_modifierCount_ReturnsCorrectCount() {
        GearModifier prefix = GearModifier.of(
            "sharp", "Sharp", ModifierType.PREFIX,
            "physical_damage", GearModifier.STAT_TYPE_FLAT, 10.0
        );
        GearModifier suffix = GearModifier.of(
            "of_vitality", "of Vitality", ModifierType.SUFFIX,
            "max_health", GearModifier.STAT_TYPE_FLAT, 20.0
        );

        GearData data = GearData.builder()
            .level(1)
            .rarity(GearRarity.RARE)
            .quality(50)
            .prefixes(List.of(prefix))
            .suffixes(List.of(suffix))
            .build();

        assertEquals(2, data.modifierCount());
    }
}
