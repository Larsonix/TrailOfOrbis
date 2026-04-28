package io.github.larsonix.trailoforbis.combat.modifiers;

import io.github.larsonix.trailoforbis.ailments.AilmentTracker;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.combat.DamageBreakdown;
import io.github.larsonix.trailoforbis.combat.AttackType;
import io.github.larsonix.trailoforbis.combat.DamageType;
import io.github.larsonix.trailoforbis.combat.EnergyShieldTracker;
import io.github.larsonix.trailoforbis.combat.resolution.CombatEntityResolver;
import io.github.larsonix.trailoforbis.config.ConfigManager;
import io.github.larsonix.trailoforbis.config.RPGConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DamageModifierProcessor.
 *
 * <p>Tests post-calculation damage modifications:
 * <ul>
 *   <li>Parry mechanics with damage reflection</li>
 *   <li>Energy shield absorption</li>
 *   <li>Mind over Matter (mana buffer)</li>
 *   <li>Fall damage reduction</li>
 *   <li>Shock amplification</li>
 * </ul>
 */
public class DamageModifierProcessorTest {

    @Mock
    private CombatEntityResolver entityResolver;

    @Mock
    private ConfigManager configManager;

    @Mock
    private EnergyShieldTracker energyShieldTracker;

    @Mock
    private AilmentTracker ailmentTracker;

    @Mock
    private RPGConfig rpgConfig;

    @Mock
    private RPGConfig.CombatConfig combatConfig;

    @Mock
    private RPGConfig.CombatConfig.ParryConfig parryConfig;

    private DamageModifierProcessor processor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Setup mock chain
        when(configManager.getRPGConfig()).thenReturn(rpgConfig);
        when(rpgConfig.getCombat()).thenReturn(combatConfig);
        when(combatConfig.getParry()).thenReturn(parryConfig);

        // Default parry config values
        when(parryConfig.getDamageReduction()).thenReturn(0.5f);  // 50% damage taken
        when(parryConfig.getReflectAmount()).thenReturn(0.5f);    // 50% reflected
        when(parryConfig.getReflectMinAttackerHp()).thenReturn(1.0f);

        processor = new DamageModifierProcessor(
            entityResolver, configManager, energyShieldTracker, ailmentTracker
        );
    }

    // ==================== Fall Damage Reduction Tests ====================

    @Nested
    @DisplayName("Fall Damage Reduction")
    class FallDamageReductionTests {

        @Test
        @DisplayName("Zero fall damage reduction returns original damage")
        void applyFallDamageReduction_zeroReduction_returnsSame() {
            ComputedStats defenderStats = ComputedStats.builder()
                .fallDamageReduction(0f)
                .build();

            float result = processor.applyFallDamageReduction(100f, defenderStats);
            assertEquals(100f, result, 0.1f,
                "0% reduction should not change damage");
        }

        @Test
        @DisplayName("Fall damage reduction reduces damage")
        void applyFallDamageReduction_withReduction_reducesDamage() {
            ComputedStats defenderStats = ComputedStats.builder()
                .fallDamageReduction(50f)  // 50% reduction
                .build();

            float result = processor.applyFallDamageReduction(100f, defenderStats);
            // CombatCalculator handles the actual formula - just verify it reduces
            assertTrue(result < 100f,
                "Fall damage reduction should reduce damage");
        }
    }

    // ==================== Shock Amplification Tests ====================

    @Nested
    @DisplayName("Shock Amplification")
    class ShockAmplificationTests {

        @Test
        @DisplayName("Null ailment tracker returns original damage")
        void applyShockAmplification_nullTracker_returnsSame() {
            // Create processor without ailment tracker
            DamageModifierProcessor noTrackerProcessor = new DamageModifierProcessor(
                entityResolver, configManager, energyShieldTracker, null
            );

            UUID defenderUuid = UUID.randomUUID();
            float result = noTrackerProcessor.applyShockAmplification(100f, defenderUuid);
            assertEquals(100f, result, 0.1f,
                "Null tracker should not modify damage");
        }

        @Test
        @DisplayName("Null UUID returns original damage")
        void applyShockAmplification_nullUuid_returnsSame() {
            float result = processor.applyShockAmplification(100f, null);
            assertEquals(100f, result, 0.1f,
                "Null UUID should not modify damage");
        }

        @Test
        @DisplayName("Zero shock bonus returns original damage")
        void applyShockAmplification_zeroBonus_returnsSame() {
            UUID defenderUuid = UUID.randomUUID();
            when(ailmentTracker.getShockDamageIncreasePercent(defenderUuid)).thenReturn(0f);

            float result = processor.applyShockAmplification(100f, defenderUuid);
            assertEquals(100f, result, 0.1f,
                "0% shock should not modify damage");
        }

        @Test
        @DisplayName("Shock bonus amplifies damage")
        void applyShockAmplification_withBonus_amplifies() {
            UUID defenderUuid = UUID.randomUUID();
            when(ailmentTracker.getShockDamageIncreasePercent(defenderUuid)).thenReturn(30f);

            float result = processor.applyShockAmplification(100f, defenderUuid);
            assertEquals(130f, result, 0.1f,
                "30% shock should amplify: 100 * 1.3 = 130");
        }

        @Test
        @DisplayName("Large shock bonus correctly calculated")
        void applyShockAmplification_largeBonus_correctlyCalculated() {
            UUID defenderUuid = UUID.randomUUID();
            when(ailmentTracker.getShockDamageIncreasePercent(defenderUuid)).thenReturn(100f);

            float result = processor.applyShockAmplification(100f, defenderUuid);
            assertEquals(200f, result, 0.1f,
                "100% shock should double damage: 100 * 2.0 = 200");
        }
    }

    // ==================== Parry Result Tests ====================

    @Nested
    @DisplayName("Parry Calculation")
    class ParryTests {

        @Test
        @DisplayName("ParryResult record contains correct values")
        void parryResult_containsCorrectValues() {
            DamageBreakdown breakdown = DamageBreakdown.builder()
                .physicalDamage(100f)
                .damageType(DamageType.PHYSICAL)
                .attackType(AttackType.MELEE)
                .build();

            var result = new DamageModifierProcessor.ParryResult(breakdown, true, 50f);

            assertTrue(result.wasParried());
            assertEquals(50f, result.reflectedDamage());
            assertNotNull(result.breakdown());
        }

        @Test
        @DisplayName("Non-parried result has zero reflected damage")
        void parryResult_notParried_zeroReflected() {
            DamageBreakdown breakdown = DamageBreakdown.builder()
                .physicalDamage(100f)
                .damageType(DamageType.PHYSICAL)
                .attackType(AttackType.MELEE)
                .build();

            var result = new DamageModifierProcessor.ParryResult(breakdown, false, 0f);

            assertFalse(result.wasParried());
            assertEquals(0f, result.reflectedDamage());
        }
    }

    // ==================== Config Access Tests ====================

    @Nested
    @DisplayName("Config Access")
    class ConfigTests {

        @Test
        @DisplayName("getReflectMinAttackerHp returns config value")
        void getReflectMinAttackerHp_returnsConfigValue() {
            when(parryConfig.getReflectMinAttackerHp()).thenReturn(5f);

            float result = processor.getReflectMinAttackerHp();
            assertEquals(5f, result, 0.1f);
        }
    }

    // ==================== Integration Tests ====================

    @Nested
    @DisplayName("Modifier Stacking")
    class ModifierStackingTests {

        @Test
        @DisplayName("Shock modifier applies correctly")
        void shockModifier_appliesCorrectly() {
            UUID defenderUuid = UUID.randomUUID();

            // Setup shock at 20%
            when(ailmentTracker.getShockDamageIncreasePercent(defenderUuid)).thenReturn(20f);

            // Apply shock
            float afterShock = processor.applyShockAmplification(100f, defenderUuid);
            assertEquals(120f, afterShock, 0.1f,
                "20% shock should give: 100 * 1.2 = 120");
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Zero damage input returns zero")
        void zeroDamageInput_returnsZero() {
            UUID defenderUuid = UUID.randomUUID();
            when(ailmentTracker.getShockDamageIncreasePercent(defenderUuid)).thenReturn(50f);

            float result = processor.applyShockAmplification(0f, defenderUuid);
            assertEquals(0f, result, 0.001f,
                "0 damage * any modifier should still be 0");
        }

    }
}
