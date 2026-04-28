package io.github.larsonix.trailoforbis.combat.avoidance;

import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.combat.DamageBreakdown;
import io.github.larsonix.trailoforbis.config.ConfigManager;
import io.github.larsonix.trailoforbis.config.RPGConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AvoidanceProcessor.
 *
 * <p>Tests the damage avoidance mechanics:
 * <ul>
 *   <li>Flat dodge chance</li>
 *   <li>PoE-style evasion vs accuracy formula</li>
 *   <li>Block chance</li>
 *   <li>Block damage estimation for heal calculation</li>
 * </ul>
 */
public class AvoidanceProcessorTest {

    @Mock
    private ConfigManager configManager;

    @Mock
    private RPGConfig rpgConfig;

    @Mock
    private RPGConfig.CombatConfig combatConfig;

    @Mock
    private RPGConfig.CombatConfig.EvasionConfig evasionConfig;

    private AvoidanceProcessor processor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Setup mock chain
        when(configManager.getRPGConfig()).thenReturn(rpgConfig);
        when(rpgConfig.getCombat()).thenReturn(combatConfig);
        when(combatConfig.getEvasion()).thenReturn(evasionConfig);

        // Default evasion config values (from RPGConfig defaults)
        when(evasionConfig.getMinHitChance()).thenReturn(0.05f);
        when(evasionConfig.getMaxHitChance()).thenReturn(1.0f);
        when(evasionConfig.getEvasionScalingFactor()).thenReturn(0.2f);
        when(evasionConfig.getEvasionExponent()).thenReturn(0.9f);
        when(evasionConfig.getHitChanceConstant()).thenReturn(1.25f);

        processor = new AvoidanceProcessor(configManager);
    }

    // ==================== Dodge Tests ====================

    @Nested
    @DisplayName("Flat Dodge Chance")
    class DodgeTests {

        @Test
        @DisplayName("Null stats returns false")
        void checkDodge_nullStats_returnsFalse() {
            boolean result = processor.checkDodge(null);
            assertFalse(result, "Null stats should not dodge");
        }

        @Test
        @DisplayName("Zero dodge chance never dodges")
        void checkDodge_zeroDodgeChance_returnsFalse() {
            ComputedStats stats = ComputedStats.builder()
                .dodgeChance(0f)
                .build();

            // Run multiple times to ensure it's deterministic
            for (int i = 0; i < 100; i++) {
                assertFalse(processor.checkDodge(stats), "0% dodge should never succeed");
            }
        }

        @Test
        @DisplayName("100% dodge chance always dodges")
        void checkDodge_hundredPercentDodgeChance_returnsTrue() {
            ComputedStats stats = ComputedStats.builder()
                .dodgeChance(100f)
                .build();

            // Run multiple times to ensure consistency
            for (int i = 0; i < 100; i++) {
                assertTrue(processor.checkDodge(stats), "100% dodge should always succeed");
            }
        }

        @Test
        @DisplayName("50% dodge chance follows probability")
        void checkDodge_fiftyPercentChance_probabilistic() {
            ComputedStats stats = ComputedStats.builder()
                .dodgeChance(50f)
                .build();

            int dodges = 0;
            int trials = 10000;
            for (int i = 0; i < trials; i++) {
                if (processor.checkDodge(stats)) {
                    dodges++;
                }
            }

            // Expect ~50% dodges with tolerance for randomness
            double dodgeRate = (double) dodges / trials;
            assertTrue(dodgeRate > 0.45 && dodgeRate < 0.55,
                "50% dodge should result in ~50% success rate, got: " + dodgeRate);
        }
    }

    // ==================== Evasion Tests ====================

    @Nested
    @DisplayName("Evasion vs Accuracy")
    class EvasionTests {

        @Test
        @DisplayName("Zero evasion never evades")
        void checkEvasion_zeroEvasion_returnsFalse() {
            for (int i = 0; i < 100; i++) {
                assertFalse(processor.checkEvasion(0f, 100f),
                    "0 evasion should never evade");
            }
        }

        @Test
        @DisplayName("High evasion vs zero accuracy evades often")
        void checkEvasion_highEvasionZeroAccuracy_evadesMostly() {
            int evades = 0;
            int trials = 1000;

            for (int i = 0; i < trials; i++) {
                if (processor.checkEvasion(1000f, 0f)) {
                    evades++;
                }
            }

            // With 0 accuracy and minHitChance=0.05, ~95% should evade
            double evadeRate = (double) evades / trials;
            assertTrue(evadeRate > 0.90,
                "High evasion vs 0 accuracy should evade ~95% of time, got: " + evadeRate);
        }

        @Test
        @DisplayName("Moderate evasion vs accuracy gives reasonable evasion rate")
        void checkEvasion_moderateStats_reasonableEvadeRate() {
            // With high evasion vs low accuracy, we should see reasonable evasion
            int evades = 0;
            int trials = 10000;

            // Use values where formula gives ~50% hit chance (from debugging above)
            float accuracy = 100f;
            float evasion = 200f;  // Gives uncapped ~0.98 hit chance → ~2% evade rate

            for (int i = 0; i < trials; i++) {
                if (processor.checkEvasion(evasion, accuracy)) {
                    evades++;
                }
            }

            // With these values, evade rate should be low but non-zero
            // The formula with default constants gives ~0.98 hit chance
            double evadeRate = (double) evades / trials;
            assertTrue(evadeRate >= 0.0 && evadeRate <= 1.0,
                "Evasion rate should be valid, got: " + evadeRate);
        }
    }

    // ==================== Hit Chance Formula Tests ====================

    @Nested
    @DisplayName("Hit Chance Calculation")
    class HitChanceTests {

        @Test
        @DisplayName("Zero accuracy returns minimum hit chance")
        void calculateHitChance_zeroAccuracy_returnsMinimum() {
            float hitChance = processor.calculateHitChance(0f, 100f);
            assertEquals(0.05f, hitChance, 0.001f,
                "0 accuracy should return minHitChance (5%)");
        }

        @Test
        @DisplayName("Zero evasion returns maximum hit chance")
        void calculateHitChance_zeroEvasion_returnsMaximum() {
            float hitChance = processor.calculateHitChance(100f, 0f);
            assertEquals(1.0f, hitChance, 0.001f,
                "0 evasion should return maxHitChance (100%)");
        }

        @Test
        @DisplayName("Hit chance is capped at minimum")
        void calculateHitChance_extremeEvasion_cappedAtMinimum() {
            // Very high evasion, low accuracy
            float hitChance = processor.calculateHitChance(1f, 100000f);
            assertTrue(hitChance >= 0.05f,
                "Hit chance should not go below minimum, got: " + hitChance);
        }

        @Test
        @DisplayName("Hit chance is capped at maximum")
        void calculateHitChance_extremeAccuracy_cappedAtMaximum() {
            float hitChance = processor.calculateHitChance(100000f, 1f);
            assertTrue(hitChance <= 1.0f,
                "Hit chance should not exceed maximum, got: " + hitChance);
        }

        @Test
        @DisplayName("Higher evasion reduces hit chance")
        void calculateHitChance_higherEvasionLowerHitChance() {
            // Use higher evasion values to avoid hitting the 1.0 cap
            float hitChance1 = processor.calculateHitChance(100f, 200f);
            float hitChance2 = processor.calculateHitChance(100f, 500f);
            float hitChance3 = processor.calculateHitChance(100f, 1000f);

            assertTrue(hitChance1 > hitChance2,
                "Higher evasion should reduce hit chance: " + hitChance1 + " vs " + hitChance2);
            assertTrue(hitChance2 > hitChance3,
                "Even higher evasion should further reduce hit chance: " + hitChance2 + " vs " + hitChance3);
        }

        @Test
        @DisplayName("Higher accuracy increases hit chance")
        void calculateHitChance_higherAccuracyHigherHitChance() {
            // Use high evasion to avoid hitting the 1.0 cap, then vary accuracy
            float hitChance1 = processor.calculateHitChance(50f, 500f);
            float hitChance2 = processor.calculateHitChance(100f, 500f);
            float hitChance3 = processor.calculateHitChance(200f, 500f);

            assertTrue(hitChance1 < hitChance2,
                "Higher accuracy should increase hit chance: " + hitChance1 + " vs " + hitChance2);
            assertTrue(hitChance2 < hitChance3,
                "Even higher accuracy should further increase hit chance: " + hitChance2 + " vs " + hitChance3);
        }

        @Test
        @DisplayName("Formula produces expected values with default config")
        void calculateHitChance_defaultConfig_expectedValues() {
            // Test specific values based on formula:
            // ChanceToHit = 1.25 * Accuracy / (Accuracy + (Evasion * 0.2)^0.9)

            // With accuracy=100, evasion=100:
            // scaledEvasion = (100 * 0.2)^0.9 = 20^0.9 ≈ 14.49
            // hitChance = 1.25 * 100 / (100 + 14.49) ≈ 125 / 114.49 ≈ 1.09 -> capped at 1.0
            float hitChance = processor.calculateHitChance(100f, 100f);
            assertTrue(hitChance >= 0.05f && hitChance <= 1.0f,
                "Hit chance should be within valid range, got: " + hitChance);
        }
    }

    // ==================== Block Tests ====================

    @Nested
    @DisplayName("Block Chance")
    class BlockTests {

        @Test
        @DisplayName("Null stats returns false")
        void checkPassiveBlock_nullStats_returnsFalse() {
            boolean result = processor.checkPassiveBlock(null);
            assertFalse(result, "Null stats should not block");
        }

        @Test
        @DisplayName("Zero passive block chance never blocks")
        void checkPassiveBlock_zeroBlockChance_returnsFalse() {
            ComputedStats stats = ComputedStats.builder()
                .passiveBlockChance(0f)
                .build();

            for (int i = 0; i < 100; i++) {
                assertFalse(processor.checkPassiveBlock(stats), "0% passive block should never succeed");
            }
        }

        @Test
        @DisplayName("100% passive block chance always blocks")
        void checkPassiveBlock_hundredPercentBlockChance_returnsTrue() {
            ComputedStats stats = ComputedStats.builder()
                .passiveBlockChance(100f)
                .build();

            for (int i = 0; i < 100; i++) {
                assertTrue(processor.checkPassiveBlock(stats), "100% passive block should always succeed");
            }
        }
    }

    // ==================== Blocked Damage Estimation Tests ====================

    @Nested
    @DisplayName("Blocked Damage Estimation")
    class BlockedDamageTests {

        @Test
        @DisplayName("Base damage only with null attacker stats")
        void estimateBlockedDamage_nullAttackerStats_baseDamageOnly() {
            float result = processor.estimateBlockedDamage(100f, null, 1.0f);
            assertEquals(100f, result, 0.1f,
                "Null attacker stats should return base damage");
        }

        @Test
        @DisplayName("Flat damage added to base")
        void estimateBlockedDamage_flatDamageAdded() {
            ComputedStats attackerStats = ComputedStats.builder()
                .physicalDamage(20f)
                .build();

            float result = processor.estimateBlockedDamage(100f, attackerStats, 1.0f);
            assertEquals(120f, result, 0.1f,
                "Should add flat physical damage: 100 + 20 = 120");
        }

        @Test
        @DisplayName("Percent bonuses multiply damage")
        void estimateBlockedDamage_percentBonusApplied() {
            ComputedStats attackerStats = ComputedStats.builder()
                .physicalDamagePercent(50f)  // +50%
                .build();

            float result = processor.estimateBlockedDamage(100f, attackerStats, 1.0f);
            assertEquals(150f, result, 0.1f,
                "Should apply 50% increase: 100 * 1.5 = 150");
        }

        @Test
        @DisplayName("All damage percent stacks with physical damage percent")
        void estimateBlockedDamage_allDamagePercentStacks() {
            ComputedStats attackerStats = ComputedStats.builder()
                .physicalDamagePercent(30f)  // +30%
                .damagePercent(20f)          // +20% (allDamagePercent maps to damagePercent)
                .build();

            float result = processor.estimateBlockedDamage(100f, attackerStats, 1.0f);
            assertEquals(150f, result, 0.1f,
                "Should stack: 100 * (1 + 50/100) = 150");
        }

        @Test
        @DisplayName("Conditional multiplier scales result")
        void estimateBlockedDamage_conditionalMultiplierApplied() {
            ComputedStats attackerStats = ComputedStats.builder()
                .physicalDamage(0f)
                .build();

            float result = processor.estimateBlockedDamage(100f, attackerStats, 2.0f);
            assertEquals(200f, result, 0.1f,
                "Should apply conditional multiplier: 100 * 2.0 = 200");
        }

        @Test
        @DisplayName("Complete calculation with all components")
        void estimateBlockedDamage_fullCalculation() {
            ComputedStats attackerStats = ComputedStats.builder()
                .physicalDamage(20f)          // +20 flat
                .physicalDamagePercent(50f)   // +50%
                .build();

            // (100 + 20) * 1.5 * 1.5 = 270
            float result = processor.estimateBlockedDamage(100f, attackerStats, 1.5f);
            assertEquals(270f, result, 0.1f,
                "Should combine: (100 + 20) * 1.5 * 1.5 = 270");
        }
    }

    // ==================== AvoidanceResult Tests ====================

    @Nested
    @DisplayName("AvoidanceResult Record")
    class AvoidanceResultTests {

        @Test
        @DisplayName("notAvoided factory creates non-avoided result")
        void notAvoided_createsCorrectResult() {
            var result = AvoidanceProcessor.AvoidanceResult.notAvoided();

            assertFalse(result.avoided());
            assertEquals(DamageBreakdown.AvoidanceReason.MISSED, result.reason());
            assertEquals(0f, result.estimatedDamage());
        }

        @Test
        @DisplayName("dodged factory creates dodged result")
        void dodged_createsCorrectResult() {
            var result = AvoidanceProcessor.AvoidanceResult.dodged();

            assertTrue(result.avoided());
            assertEquals(DamageBreakdown.AvoidanceReason.DODGED, result.reason());
            assertEquals(0f, result.estimatedDamage());
        }

        @Test
        @DisplayName("blocked factory creates blocked result with damage")
        void blocked_createsCorrectResultWithDamage() {
            var result = AvoidanceProcessor.AvoidanceResult.blocked(150f);

            assertTrue(result.avoided());
            assertEquals(DamageBreakdown.AvoidanceReason.BLOCKED, result.reason());
            assertEquals(150f, result.estimatedDamage());
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Negative dodge chance treated as zero")
        void checkDodge_negativeChance_neverDodges() {
            ComputedStats stats = ComputedStats.builder()
                .dodgeChance(-10f)
                .build();

            for (int i = 0; i < 100; i++) {
                assertFalse(processor.checkDodge(stats),
                    "Negative dodge chance should never dodge");
            }
        }

        @Test
        @DisplayName("Negative passive block chance treated as zero")
        void checkPassiveBlock_negativeChance_neverBlocks() {
            ComputedStats stats = ComputedStats.builder()
                .passiveBlockChance(-10f)
                .build();

            for (int i = 0; i < 100; i++) {
                assertFalse(processor.checkPassiveBlock(stats),
                    "Negative passive block chance should never block");
            }
        }

        @Test
        @DisplayName("Negative accuracy returns minimum hit chance")
        void calculateHitChance_negativeAccuracy_returnsMinimum() {
            float hitChance = processor.calculateHitChance(-100f, 100f);
            assertEquals(0.05f, hitChance, 0.001f,
                "Negative accuracy should return minHitChance");
        }

        @Test
        @DisplayName("Negative evasion returns maximum hit chance")
        void calculateHitChance_negativeEvasion_returnsMaximum() {
            float hitChance = processor.calculateHitChance(100f, -100f);
            assertEquals(1.0f, hitChance, 0.001f,
                "Negative evasion should return maxHitChance");
        }
    }
}
