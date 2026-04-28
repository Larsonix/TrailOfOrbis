package io.github.larsonix.trailoforbis.gear.equipment;

import io.github.larsonix.trailoforbis.attributes.AttributeType;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig.AttributeRequirementsConfig;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig.ModifierDefinition;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig.StatType;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.GearModifier;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.gear.model.ModifierType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for RequirementCalculator.
 *
 * <p>Note: These tests use FIRE/VOID/etc. for attribute types as the system
 * now uses elemental attributes. The test names still refer to "STR" and "INT"
 * for clarity about what type of modifier is being tested.
 */
class RequirementCalculatorTest {

    // Mocks
    private GearBalanceConfig balanceConfig;
    private ModifierConfig modifierConfig;
    private AttributeRequirementsConfig attrReqConfig;

    // Test instance
    private RequirementCalculator calculator;

    @BeforeEach
    void setUp() {
        // Create mocks
        balanceConfig = mock(GearBalanceConfig.class);
        modifierConfig = mock(ModifierConfig.class);
        attrReqConfig = mock(AttributeRequirementsConfig.class);

        // Default config behavior
        when(balanceConfig.attributeRequirements()).thenReturn(attrReqConfig);
        when(attrReqConfig.minItemLevelForRequirements()).thenReturn(10);

        // Default empty modifier lists
        when(modifierConfig.prefixList()).thenReturn(Collections.emptyList());
        when(modifierConfig.suffixList()).thenReturn(Collections.emptyList());
    }

    // Helper methods
    private ModifierDefinition createModifierDef(String id, String stat, AttributeType requiredAttr) {
        return new ModifierDefinition(
            id,
            id,
            stat,
            StatType.FLAT,
            1.0, 10.0,
            0.1,
            100,
            requiredAttr,
            null  // all slots
        );
    }

    private GearModifier createModifier(String id, String stat, ModifierType type) {
        return GearModifier.of(id, id, type, stat, GearModifier.STAT_TYPE_FLAT, 5.0);
    }

    @Nested
    @DisplayName("calculateRequirements")
    class CalculateRequirements {

        @Test
        @DisplayName("Returns empty map for null gear data")
        void nullGearData_returnsEmptyMap() {
            calculator = new RequirementCalculator(balanceConfig, modifierConfig);
            Map<AttributeType, Integer> result = calculator.calculateRequirements(null);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Returns empty map for gear below minimum level")
        void belowMinLevel_returnsEmptyMap() {
            // Setup: min level is 10, item is level 5
            when(attrReqConfig.minItemLevelForRequirements()).thenReturn(10);

            // Create modifier definition with FIRE requirement (physical damage element)
            ModifierDefinition fireDef = createModifierDef("mighty", "physical_damage", AttributeType.FIRE);
            when(modifierConfig.prefixList()).thenReturn(List.of(fireDef));

            calculator = new RequirementCalculator(balanceConfig, modifierConfig);

            // Gear with level 5 (below min 10)
            GearModifier prefix = createModifier("mighty", "physical_damage", ModifierType.PREFIX);
            GearData gear = GearData.of(null, 5, GearRarity.RARE, 50, List.of(prefix), Collections.emptyList());

            Map<AttributeType, Integer> result = calculator.calculateRequirements(gear);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Returns empty map for gear with no requiring modifiers")
        void noRequiringModifiers_returnsEmptyMap() {
            // Setup: modifier has no attribute requirement
            ModifierDefinition noReqDef = createModifierDef("sharp", "physical_damage", null);
            when(modifierConfig.prefixList()).thenReturn(List.of(noReqDef));
            when(attrReqConfig.minItemLevelForRequirements()).thenReturn(1);

            calculator = new RequirementCalculator(balanceConfig, modifierConfig);

            GearModifier prefix = createModifier("sharp", "physical_damage", ModifierType.PREFIX);
            GearData gear = GearData.of(null, 20, GearRarity.RARE, 50, List.of(prefix), Collections.emptyList());

            Map<AttributeType, Integer> result = calculator.calculateRequirements(gear);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Returns correct requirements for single modifier")
        void singleModifierWithRequirement_returnsRequirement() {
            // Setup modifier with FIRE requirement
            ModifierDefinition fireDef = createModifierDef("mighty", "physical_damage", AttributeType.FIRE);
            when(modifierConfig.prefixList()).thenReturn(List.of(fireDef));
            when(attrReqConfig.minItemLevelForRequirements()).thenReturn(1);
            when(attrReqConfig.calculateRequirement(20, GearRarity.RARE)).thenReturn(15);

            calculator = new RequirementCalculator(balanceConfig, modifierConfig);

            GearModifier prefix = createModifier("mighty", "physical_damage", ModifierType.PREFIX);
            GearData gear = GearData.of(null, 20, GearRarity.RARE, 50, List.of(prefix), Collections.emptyList());

            Map<AttributeType, Integer> result = calculator.calculateRequirements(gear);
            assertEquals(1, result.size());
            assertEquals(15, result.get(AttributeType.FIRE));
        }

        @Test
        @DisplayName("Returns same requirement for multiple modifiers with same attribute")
        void multipleModifiersSameAttribute_returnsSingleRequirement() {
            // Both modifiers require FIRE
            ModifierDefinition fireDef1 = createModifierDef("mighty", "physical_damage", AttributeType.FIRE);
            ModifierDefinition fireDef2 = createModifierDef("brutal", "attack_speed", AttributeType.FIRE);
            when(modifierConfig.prefixList()).thenReturn(List.of(fireDef1, fireDef2));
            when(attrReqConfig.minItemLevelForRequirements()).thenReturn(1);
            when(attrReqConfig.calculateRequirement(20, GearRarity.RARE)).thenReturn(15);

            calculator = new RequirementCalculator(balanceConfig, modifierConfig);

            GearModifier prefix1 = createModifier("mighty", "physical_damage", ModifierType.PREFIX);
            GearModifier prefix2 = createModifier("brutal", "attack_speed", ModifierType.PREFIX);
            GearData gear = GearData.of(null, 20, GearRarity.RARE, 50, List.of(prefix1, prefix2), Collections.emptyList());

            Map<AttributeType, Integer> result = calculator.calculateRequirements(gear);
            // Should have only one entry (FIRE), not two
            assertEquals(1, result.size());
            assertEquals(15, result.get(AttributeType.FIRE));
        }

        @Test
        @DisplayName("Returns multiple requirements for different attributes")
        void multipleModifiersDifferentAttributes_returnsMultipleRequirements() {
            // One requires FIRE (physical), one requires VOID (spell)
            ModifierDefinition fireDef = createModifierDef("mighty", "physical_damage", AttributeType.FIRE);
            ModifierDefinition voidDef = createModifierDef("wise", "spell_damage", AttributeType.VOID);
            when(modifierConfig.prefixList()).thenReturn(List.of(fireDef));
            when(modifierConfig.suffixList()).thenReturn(List.of(voidDef));
            when(attrReqConfig.minItemLevelForRequirements()).thenReturn(1);
            when(attrReqConfig.calculateRequirement(20, GearRarity.RARE)).thenReturn(15);

            calculator = new RequirementCalculator(balanceConfig, modifierConfig);

            GearModifier prefix = createModifier("mighty", "physical_damage", ModifierType.PREFIX);
            GearModifier suffix = createModifier("wise", "spell_damage", ModifierType.SUFFIX);
            GearData gear = GearData.of(null, 20, GearRarity.RARE, 50, List.of(prefix), List.of(suffix));

            Map<AttributeType, Integer> result = calculator.calculateRequirements(gear);
            assertEquals(2, result.size());
            assertEquals(15, result.get(AttributeType.FIRE));
            assertEquals(15, result.get(AttributeType.VOID));
        }

        @Test
        @DisplayName("Returns empty map when requirement amount is 0")
        void requirementAmountZero_returnsEmptyMap() {
            ModifierDefinition fireDef = createModifierDef("mighty", "physical_damage", AttributeType.FIRE);
            when(modifierConfig.prefixList()).thenReturn(List.of(fireDef));
            when(attrReqConfig.minItemLevelForRequirements()).thenReturn(1);
            when(attrReqConfig.calculateRequirement(anyInt(), any())).thenReturn(0);

            calculator = new RequirementCalculator(balanceConfig, modifierConfig);

            GearModifier prefix = createModifier("mighty", "physical_damage", ModifierType.PREFIX);
            GearData gear = GearData.of(null, 5, GearRarity.COMMON, 50, List.of(prefix), Collections.emptyList());

            Map<AttributeType, Integer> result = calculator.calculateRequirements(gear);
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("hasRequirements")
    class HasRequirements {

        @Test
        @DisplayName("Returns false for null gear data")
        void nullGearData_returnsFalse() {
            calculator = new RequirementCalculator(balanceConfig, modifierConfig);
            assertFalse(calculator.hasRequirements(null));
        }

        @Test
        @DisplayName("Returns false for gear below minimum level")
        void belowMinLevel_returnsFalse() {
            when(attrReqConfig.minItemLevelForRequirements()).thenReturn(10);
            calculator = new RequirementCalculator(balanceConfig, modifierConfig);

            GearData gear = GearData.of(null, 5, GearRarity.RARE, 50, Collections.emptyList(), Collections.emptyList());
            assertFalse(calculator.hasRequirements(gear));
        }

        @Test
        @DisplayName("Returns false for gear with no requiring modifiers")
        void noRequiringModifiers_returnsFalse() {
            when(attrReqConfig.minItemLevelForRequirements()).thenReturn(1);
            calculator = new RequirementCalculator(balanceConfig, modifierConfig);

            GearData gear = GearData.of(null, 20, GearRarity.RARE, 50, Collections.emptyList(), Collections.emptyList());
            assertFalse(calculator.hasRequirements(gear));
        }

        @Test
        @DisplayName("Returns true for gear with requiring modifiers")
        void hasRequiringModifier_returnsTrue() {
            ModifierDefinition fireDef = createModifierDef("mighty", "physical_damage", AttributeType.FIRE);
            when(modifierConfig.prefixList()).thenReturn(List.of(fireDef));
            when(attrReqConfig.minItemLevelForRequirements()).thenReturn(1);

            calculator = new RequirementCalculator(balanceConfig, modifierConfig);

            GearModifier prefix = createModifier("mighty", "physical_damage", ModifierType.PREFIX);
            GearData gear = GearData.of(null, 20, GearRarity.RARE, 50, List.of(prefix), Collections.emptyList());

            assertTrue(calculator.hasRequirements(gear));
        }
    }

    @Nested
    @DisplayName("getRequiredAttributeForStat")
    class GetRequiredAttributeForStat {

        @Test
        @DisplayName("Returns null for unknown stat")
        void unknownStat_returnsNull() {
            calculator = new RequirementCalculator(balanceConfig, modifierConfig);
            assertNull(calculator.getRequiredAttributeForStat("unknown_stat"));
        }

        @Test
        @DisplayName("Returns correct attribute for known stat")
        void knownStat_returnsAttribute() {
            ModifierDefinition fireDef = createModifierDef("mighty", "physical_damage", AttributeType.FIRE);
            when(modifierConfig.prefixList()).thenReturn(List.of(fireDef));

            calculator = new RequirementCalculator(balanceConfig, modifierConfig);
            assertEquals(AttributeType.FIRE, calculator.getRequiredAttributeForStat("physical_damage"));
        }

        @Test
        @DisplayName("Lookup is case-insensitive")
        void caseInsensitive() {
            ModifierDefinition fireDef = createModifierDef("mighty", "physical_damage", AttributeType.FIRE);
            when(modifierConfig.prefixList()).thenReturn(List.of(fireDef));

            calculator = new RequirementCalculator(balanceConfig, modifierConfig);
            assertEquals(AttributeType.FIRE, calculator.getRequiredAttributeForStat("PHYSICAL_DAMAGE"));
            assertEquals(AttributeType.FIRE, calculator.getRequiredAttributeForStat("Physical_Damage"));
        }
    }

    @Nested
    @DisplayName("getStatsForAttribute")
    class GetStatsForAttribute {

        @Test
        @DisplayName("Returns empty set for attribute with no stats")
        void noStats_returnsEmptySet() {
            calculator = new RequirementCalculator(balanceConfig, modifierConfig);
            Set<String> stats = calculator.getStatsForAttribute(AttributeType.WIND);
            assertTrue(stats.isEmpty());
        }

        @Test
        @DisplayName("Returns all stats for an attribute")
        void multipleStats_returnsAll() {
            ModifierDefinition fireDef1 = createModifierDef("mighty", "physical_damage", AttributeType.FIRE);
            ModifierDefinition fireDef2 = createModifierDef("brutal", "attack_speed", AttributeType.FIRE);
            ModifierDefinition voidDef = createModifierDef("wise", "spell_damage", AttributeType.VOID);
            when(modifierConfig.prefixList()).thenReturn(List.of(fireDef1, fireDef2, voidDef));

            calculator = new RequirementCalculator(balanceConfig, modifierConfig);
            Set<String> stats = calculator.getStatsForAttribute(AttributeType.FIRE);

            assertEquals(2, stats.size());
            assertTrue(stats.contains("physical_damage"));
            assertTrue(stats.contains("attack_speed"));
        }
    }

    @Nested
    @DisplayName("calculateRequirementAmount")
    class CalculateRequirementAmount {

        @Test
        @DisplayName("Returns 0 for level below minimum")
        void belowMinLevel_returnsZero() {
            when(attrReqConfig.minItemLevelForRequirements()).thenReturn(10);
            calculator = new RequirementCalculator(balanceConfig, modifierConfig);

            int result = calculator.calculateRequirementAmount(5, GearRarity.RARE);
            assertEquals(0, result);
        }

        @Test
        @DisplayName("Delegates to config for valid level")
        void validLevel_delegatesToConfig() {
            when(attrReqConfig.minItemLevelForRequirements()).thenReturn(1);
            when(attrReqConfig.calculateRequirement(20, GearRarity.EPIC)).thenReturn(25);
            calculator = new RequirementCalculator(balanceConfig, modifierConfig);

            int result = calculator.calculateRequirementAmount(20, GearRarity.EPIC);
            assertEquals(25, result);
            verify(attrReqConfig).calculateRequirement(20, GearRarity.EPIC);
        }
    }
}
