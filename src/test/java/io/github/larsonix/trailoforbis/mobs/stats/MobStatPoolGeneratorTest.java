package io.github.larsonix.trailoforbis.mobs.stats;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for the mob stat pool generation pipeline.
 *
 * <p>Tests three layers of the stat generation system:
 * <ol>
 *   <li>{@link StatConfig} - Per-stat conversion, clamping, base values</li>
 *   <li>{@link MobStatPoolConfig} - Progressive scaling factor, alpha weights, archetypes</li>
 *   <li>{@link DirichletDistributor} + {@link MobStatGenerator} - Full pipeline properties:
 *       Dirichlet distribution sums, stat clamping, archetype influence, seed determinism</li>
 * </ol>
 */
@DisplayName("MobStatPoolGenerator (Pipeline)")
class MobStatPoolGeneratorTest {

    private MobStatPoolConfig poolConfig;
    private MobStatGenerator generator;

    @BeforeEach
    void setUp() {
        poolConfig = MobStatPoolConfig.createDefaults();
        generator = new MobStatGenerator(poolConfig);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // StatConfig Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("StatConfig")
    class StatConfigTests {

        @Test
        @DisplayName("Default StatConfig has factor 1.0 and no clamping")
        void defaultStatConfig_hasSaneDefaults() {
            StatConfig cfg = new StatConfig();
            assertEquals(1.0, cfg.getFactor(), 0.001);
            assertEquals(0.0, cfg.getMinValue(), 0.001);
            assertEquals(Double.MAX_VALUE, cfg.getMaxValue(), 0.001);
            assertEquals(0.0, cfg.getBaseValue(), 0.001);
            assertEquals(1.0, cfg.getAlphaWeight(), 0.001);
        }

        @Test
        @DisplayName("applyFactor multiplies pool share by factor")
        void applyFactor_multipliesPoolShareByFactor() {
            StatConfig cfg = new StatConfig(0.5, 0.0, 100.0, 0.0, 1.0);
            assertEquals(25.0, cfg.applyFactor(50.0), 0.001);
        }

        @Test
        @DisplayName("applyFactor with factor 0 returns 0")
        void applyFactor_withZeroFactor_returnsZero() {
            StatConfig cfg = new StatConfig(0.0, 0.0, 100.0, 0.0, 1.0);
            assertEquals(0.0, cfg.applyFactor(999.0), 0.001);
        }

        @Test
        @DisplayName("applyFactor with negative factor inverts")
        void applyFactor_withNegativeFactor_inverts() {
            // Attack cooldown uses negative factor (-0.05): higher pool = lower cooldown
            StatConfig cfg = new StatConfig(-0.05, 0.1, 3.0, 1.0, 1.0);
            assertEquals(-5.0, cfg.applyFactor(100.0), 0.001);
        }

        @Test
        @DisplayName("clamp constrains to min/max range")
        void clamp_constrainsToMinMaxRange() {
            StatConfig cfg = new StatConfig(1.0, 10.0, 50.0, 0.0, 1.0);
            assertEquals(10.0, cfg.clamp(5.0), 0.001);   // Below min
            assertEquals(25.0, cfg.clamp(25.0), 0.001);   // In range
            assertEquals(50.0, cfg.clamp(999.0), 0.001);  // Above max
        }

        @Test
        @DisplayName("clamp with equal min and max pins to that value")
        void clamp_equalMinMax_pinsToValue() {
            StatConfig cfg = new StatConfig(1.0, 42.0, 42.0, 0.0, 1.0);
            assertEquals(42.0, cfg.clamp(0.0), 0.001);
            assertEquals(42.0, cfg.clamp(100.0), 0.001);
        }

        @Test
        @DisplayName("finalize applies factor, adds base, and clamps")
        void finalize_appliesFactorAddsBaseAndClamps() {
            // Factor 0.5, base 10, min 0, max 100
            StatConfig cfg = new StatConfig(0.5, 0.0, 100.0, 10.0, 1.0);
            // finalize(60) = clamp(60 * 0.5 + 10) = clamp(40) = 40
            assertEquals(40.0, cfg.finalize(60.0), 0.001);
        }

        @Test
        @DisplayName("finalize clamps above max")
        void finalize_clampsAboveMax() {
            StatConfig cfg = new StatConfig(1.0, 0.0, 50.0, 30.0, 1.0);
            // finalize(100) = clamp(100 + 30) = clamp(130) = 50
            assertEquals(50.0, cfg.finalize(100.0), 0.001);
        }

        @Test
        @DisplayName("finalize clamps below min")
        void finalize_clampsBelowMin() {
            StatConfig cfg = new StatConfig(-0.1, 0.1, 3.0, 1.0, 1.0);
            // finalize(100) = clamp(100 * -0.1 + 1.0) = clamp(-9.0) = 0.1
            assertEquals(0.1, cfg.finalize(100.0), 0.001);
        }

        @Test
        @DisplayName("finalize with zero pool share returns base value")
        void finalize_zeroPoolShare_returnsBaseValue() {
            StatConfig cfg = new StatConfig(0.88, 1.0, 100000.0, 100.0, 1.0);
            // finalize(0) = clamp(0 + 100) = 100
            assertEquals(100.0, cfg.finalize(0.0), 0.001);
        }

        @Test
        @DisplayName("fromMobStatType creates config from enum defaults")
        void fromMobStatType_createsFromEnumDefaults() {
            StatConfig cfg = StatConfig.fromMobStatType(MobStatType.MOVE_SPEED);
            assertEquals(1.0, cfg.getFactor(), 0.001);  // Default factor before custom override
            assertEquals(0.3, cfg.getMinValue(), 0.001);
            assertEquals(3.0, cfg.getMaxValue(), 0.001);
            assertEquals(1.0, cfg.getBaseValue(), 0.001);
            assertEquals(1.0, cfg.getAlphaWeight(), 0.001);
        }

        @Test
        @DisplayName("DEFAULT constant has identity configuration")
        void defaultConstant_hasIdentityConfig() {
            assertEquals(1.0, StatConfig.DEFAULT.getFactor(), 0.001);
            assertEquals(0.0, StatConfig.DEFAULT.getMinValue(), 0.001);
            assertEquals(Double.MAX_VALUE, StatConfig.DEFAULT.getMaxValue(), 0.001);
            assertEquals(0.0, StatConfig.DEFAULT.getBaseValue(), 0.001);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MobStatPoolConfig - Progressive Scaling Factor Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Progressive Scaling Factor")
    class ProgressiveScalingFactorTests {

        @Test
        @DisplayName("Level 1 returns minimum factor (0.335)")
        void level1_returnsMinimumFactor() {
            // minFactor + (1 - minFactor) * (1/20) = 0.3 + 0.7 * 0.05 = 0.335
            double factor = poolConfig.calculateScalingFactor(1);
            assertEquals(0.335, factor, 0.001);
        }

        @Test
        @DisplayName("Level 10 returns midpoint factor (0.65)")
        void level10_returnsMidpointFactor() {
            // 0.3 + 0.7 * (10/20) = 0.3 + 0.35 = 0.65
            double factor = poolConfig.calculateScalingFactor(10);
            assertEquals(0.65, factor, 0.001);
        }

        @Test
        @DisplayName("Level at soft cap (20) returns 1.0")
        void levelAtSoftCap_returnsOne() {
            double factor = poolConfig.calculateScalingFactor(20);
            assertEquals(1.0, factor, 0.001);
        }

        @Test
        @DisplayName("Level above soft cap still returns 1.0")
        void levelAboveSoftCap_returnsOne() {
            assertEquals(1.0, poolConfig.calculateScalingFactor(50), 0.001);
            assertEquals(1.0, poolConfig.calculateScalingFactor(100), 0.001);
        }

        @Test
        @DisplayName("Level 0 produces minFactor (0.3)")
        void levelZero_producesMinFactor() {
            // 0.3 + 0.7 * (0/20) = 0.3
            assertEquals(0.3, poolConfig.calculateScalingFactor(0), 0.001);
        }

        @Test
        @DisplayName("Disabled progressive scaling always returns 1.0")
        void disabledProgressiveScaling_returnsOne() {
            poolConfig.setProgressiveScalingEnabled(false);
            assertEquals(1.0, poolConfig.calculateScalingFactor(1), 0.001);
            assertEquals(1.0, poolConfig.calculateScalingFactor(5), 0.001);
            assertEquals(1.0, poolConfig.calculateScalingFactor(20), 0.001);
        }

        @Test
        @DisplayName("Custom soft cap level changes progression curve")
        void customSoftCapLevel_changesProgressionCurve() {
            poolConfig.setProgressiveScalingSoftCapLevel(100);
            // Level 50: 0.3 + 0.7 * (50/100) = 0.3 + 0.35 = 0.65
            assertEquals(0.65, poolConfig.calculateScalingFactor(50), 0.001);
            // Level 100: 1.0
            assertEquals(1.0, poolConfig.calculateScalingFactor(100), 0.001);
        }

        @Test
        @DisplayName("Custom min factor changes baseline")
        void customMinFactor_changesBaseline() {
            poolConfig.setProgressiveScalingMinFactor(0.5);
            // Level 1: 0.5 + 0.5 * (1/20) = 0.5 + 0.025 = 0.525
            assertEquals(0.525, poolConfig.calculateScalingFactor(1), 0.001);
            // Level 0: 0.5
            assertEquals(0.5, poolConfig.calculateScalingFactor(0), 0.001);
        }

        @Test
        @DisplayName("Scaling factor monotonically increases with level")
        void scalingFactor_monotonicallyIncreases() {
            double previous = 0.0;
            for (int level = 0; level <= 25; level++) {
                double factor = poolConfig.calculateScalingFactor(level);
                assertTrue(factor >= previous,
                    "Factor at level " + level + " (" + factor + ") should be >= " + previous);
                previous = factor;
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MobStatPoolConfig - Alpha Weights and Archetypes Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Alpha Weights and Archetypes")
    class AlphaWeightsAndArchetypesTests {

        @Test
        @DisplayName("All stat types have a StatConfig entry")
        void allStatTypes_haveStatConfigEntry() {
            for (MobStatType type : MobStatType.values()) {
                StatConfig cfg = poolConfig.getStatConfig(type);
                assertNotNull(cfg, "Missing StatConfig for " + type);
            }
        }

        @Test
        @DisplayName("Alpha weight defaults to enum value when no config override")
        void alphaWeight_defaultsToEnumValue() {
            // HEALTH_REGEN has alphaWeight 0.1 in the enum
            double alpha = poolConfig.getAlphaWeight(MobStatType.HEALTH_REGEN);
            assertEquals(0.1, alpha, 0.001);
        }

        @Test
        @DisplayName("Alpha weight from StatConfig overrides enum default")
        void alphaWeight_fromStatConfig_overridesDefault() {
            poolConfig.getStatConfig(MobStatType.MAX_HEALTH).setAlphaWeight(5.0);
            assertEquals(5.0, poolConfig.getAlphaWeight(MobStatType.MAX_HEALTH), 0.001);
        }

        @Test
        @DisplayName("All five base archetypes exist")
        void allBaseArchetypes_exist() {
            String[] baseTypes = {"warrior", "ranger", "mage", "tank", "assassin"};
            for (String type : baseTypes) {
                double[] weights = poolConfig.getArchetypeWeights(type);
                assertNotNull(weights, "Missing archetype: " + type);
                assertEquals(MobStatType.values().length, weights.length,
                    "Archetype " + type + " weight count should match stat count");
            }
        }

        @Test
        @DisplayName("Archetype weights are case-insensitive")
        void archetypeWeights_areCaseInsensitive() {
            assertNotNull(poolConfig.getArchetypeWeights("WARRIOR"));
            assertNotNull(poolConfig.getArchetypeWeights("Warrior"));
            assertNotNull(poolConfig.getArchetypeWeights("warrior"));
        }

        @Test
        @DisplayName("Unknown archetype returns null")
        void unknownArchetype_returnsNull() {
            assertNull(poolConfig.getArchetypeWeights("nonexistent"));
        }

        @Test
        @DisplayName("Null archetype name returns null")
        void nullArchetypeName_returnsNull() {
            assertNull(poolConfig.getArchetypeWeights(null));
        }

        @Test
        @DisplayName("All archetype weights are non-negative")
        void allArchetypeWeights_areNonNegative() {
            Map<String, double[]> allArchetypes = poolConfig.getAllArchetypes();
            for (Map.Entry<String, double[]> entry : allArchetypes.entrySet()) {
                for (int i = 0; i < entry.getValue().length; i++) {
                    assertTrue(entry.getValue()[i] >= 0.0,
                        "Archetype " + entry.getKey() + " weight[" + i + "] is negative: " + entry.getValue()[i]);
                }
            }
        }

        @Test
        @DisplayName("Warrior archetype emphasizes health and physical damage")
        void warriorArchetype_emphasizesHealthAndDamage() {
            double[] weights = poolConfig.getArchetypeWeights("warrior");
            // MAX_HEALTH is index 0 = 2.5, PHYSICAL_DAMAGE is index 1 = 2.5
            assertTrue(weights[0] >= 2.0, "Warrior should have high health weight");
            assertTrue(weights[1] >= 2.0, "Warrior should have high damage weight");
        }

        @Test
        @DisplayName("Tank archetype emphasizes armor and health")
        void tankArchetype_emphasizesArmorAndHealth() {
            double[] weights = poolConfig.getArchetypeWeights("tank");
            // MAX_HEALTH is index 0 = 3.0, ARMOR is index 2 = 3.0
            assertTrue(weights[0] >= 2.5, "Tank should have high health weight");
            assertTrue(weights[2] >= 2.5, "Tank should have high armor weight");
        }

        @Test
        @DisplayName("Specialist archetypes exist (pyromancer, frost_warden, etc)")
        void specialistArchetypes_exist() {
            String[] specialists = {
                "pyromancer", "frost_warden", "storm_caller", "void_weaver",
                "berserker", "juggernaut", "duelist", "ravager",
                "spellblade", "necromancer", "elemental", "shade",
                "guardian", "executioner"
            };
            for (String name : specialists) {
                double[] weights = poolConfig.getArchetypeWeights(name);
                assertNotNull(weights, "Missing specialist archetype: " + name);
                assertEquals(MobStatType.values().length, weights.length,
                    "Archetype " + name + " weight count mismatch");
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MobStatPoolConfig - Default Config Values Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Default Config Values")
    class DefaultConfigValuesTests {

        @Test
        @DisplayName("Points per level defaults to 15.0")
        void pointsPerLevel_defaultsTo15() {
            assertEquals(15.0, poolConfig.getPointsPerLevel(), 0.001);
        }

        @Test
        @DisplayName("Boss pool multiplier defaults to 1.5")
        void bossPoolMultiplier_defaultsTo1_5() {
            assertEquals(1.5, poolConfig.getBossPoolMultiplier(), 0.001);
        }

        @Test
        @DisplayName("Elite pool multiplier defaults to 1.25")
        void elitePoolMultiplier_defaultsTo1_25() {
            assertEquals(1.25, poolConfig.getElitePoolMultiplier(), 0.001);
        }

        @Test
        @DisplayName("Dirichlet precision defaults to 1.0")
        void dirichletPrecision_defaultsTo1() {
            assertEquals(1.0, poolConfig.getDirichletPrecision(), 0.001);
        }

        @Test
        @DisplayName("Progressive scaling enabled by default")
        void progressiveScaling_enabledByDefault() {
            assertTrue(poolConfig.isProgressiveScalingEnabled());
        }

        @Test
        @DisplayName("Soft cap level defaults to 20")
        void softCapLevel_defaultsTo20() {
            assertEquals(20, poolConfig.getProgressiveScalingSoftCapLevel());
        }

        @Test
        @DisplayName("Min scaling factor defaults to 0.3")
        void minScalingFactor_defaultsTo0_3() {
            assertEquals(0.3, poolConfig.getProgressiveScalingMinFactor(), 0.001);
        }

        @Test
        @DisplayName("Custom conversion factors applied to health, damage, etc")
        void customConversionFactors_appliedCorrectly() {
            assertEquals(0.88, poolConfig.getStatConfig(MobStatType.MAX_HEALTH).getFactor(), 0.001);
            assertEquals(0.067, poolConfig.getStatConfig(MobStatType.PHYSICAL_DAMAGE).getFactor(), 0.001);
            assertEquals(0.282, poolConfig.getStatConfig(MobStatType.ARMOR).getFactor(), 0.001);
            assertEquals(0.0022, poolConfig.getStatConfig(MobStatType.MOVE_SPEED).getFactor(), 0.001);
        }

        @Test
        @DisplayName("Base values set for stats with non-zero defaults")
        void baseValues_setForStatsWithDefaults() {
            assertEquals(5.0, poolConfig.getStatConfig(MobStatType.CRITICAL_CHANCE).getBaseValue(), 0.001);
            assertEquals(150.0, poolConfig.getStatConfig(MobStatType.CRITICAL_MULTIPLIER).getBaseValue(), 0.001);
            assertEquals(1.0, poolConfig.getStatConfig(MobStatType.MOVE_SPEED).getBaseValue(), 0.001);
            assertEquals(1.0, poolConfig.getStatConfig(MobStatType.ATTACK_SPEED).getBaseValue(), 0.001);
            assertEquals(100.0, poolConfig.getStatConfig(MobStatType.ACCURACY).getBaseValue(), 0.001);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Dirichlet Distribution Statistical Properties
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Dirichlet Distribution Properties")
    class DirichletDistributionPropertiesTests {

        private DirichletDistributor distributor;

        @BeforeEach
        void setUp() {
            distributor = new DirichletDistributor();
        }

        @Test
        @DisplayName("Shares sum to total pool within tolerance over many trials")
        void shares_sumToTotalPool_overManyTrials() {
            Random random = new Random(42L);
            double totalPool = 500.0;

            for (int trial = 0; trial < 100; trial++) {
                Map<MobStatType, Double> shares = distributor.distribute(totalPool, poolConfig, random);
                double sum = shares.values().stream().mapToDouble(Double::doubleValue).sum();
                assertEquals(totalPool, sum, 0.01,
                    "Trial " + trial + ": shares should sum to total pool");
            }
        }

        @Test
        @DisplayName("No share is negative over many trials")
        void noShare_isNegative_overManyTrials() {
            Random random = new Random(77L);
            double totalPool = 1000.0;

            for (int trial = 0; trial < 200; trial++) {
                Map<MobStatType, Double> shares = distributor.distribute(totalPool, poolConfig, random);
                for (Map.Entry<MobStatType, Double> entry : shares.entrySet()) {
                    assertTrue(entry.getValue() >= 0.0,
                        "Trial " + trial + ": " + entry.getKey() + " share is negative: " + entry.getValue());
                }
            }
        }

        @Test
        @DisplayName("Pool of 1 still distributes correctly")
        void poolOfOne_distributesCorrectly() {
            Random random = new Random(123L);
            Map<MobStatType, Double> shares = distributor.distribute(1.0, poolConfig, random);

            double sum = shares.values().stream().mapToDouble(Double::doubleValue).sum();
            assertEquals(1.0, sum, 0.001);
            for (Double share : shares.values()) {
                assertTrue(share >= 0.0 && share <= 1.0);
            }
        }

        @Test
        @DisplayName("Very large pool distributes without overflow")
        void veryLargePool_distributesWithoutOverflow() {
            Random random = new Random(999L);
            Map<MobStatType, Double> shares = distributor.distribute(1_000_000.0, poolConfig, random);

            double sum = shares.values().stream().mapToDouble(Double::doubleValue).sum();
            assertEquals(1_000_000.0, sum, 1.0);

            for (Double share : shares.values()) {
                assertFalse(Double.isNaN(share), "Share should not be NaN");
                assertFalse(Double.isInfinite(share), "Share should not be Infinite");
            }
        }

        @Test
        @DisplayName("Each stat receives some portion in typical distribution")
        void eachStat_receivesSomePortion() {
            Random random = new Random(42L);
            Map<MobStatType, Double> shares = distributor.distribute(10000.0, poolConfig, random);

            for (MobStatType type : MobStatType.values()) {
                assertTrue(shares.containsKey(type), "Missing stat: " + type);
                // With uniform alpha weights, each stat should get some share
                assertTrue(shares.get(type) >= 0.0, type + " should have non-negative share");
            }
        }

        @Test
        @DisplayName("Different seeds produce different distributions")
        void differentSeeds_produceDifferentDistributions() {
            Map<MobStatType, Double> shares1 = distributor.distribute(1000.0, poolConfig, new Random(111L));
            Map<MobStatType, Double> shares2 = distributor.distribute(1000.0, poolConfig, new Random(222L));

            boolean anyDifferent = false;
            for (MobStatType type : MobStatType.values()) {
                if (Math.abs(shares1.get(type) - shares2.get(type)) > 0.1) {
                    anyDifferent = true;
                    break;
                }
            }
            assertTrue(anyDifferent, "Different seeds should produce different distributions");
        }

        @Test
        @DisplayName("Same seed produces identical distributions")
        void sameSeed_producesIdenticalDistributions() {
            Map<MobStatType, Double> shares1 = distributor.distribute(1000.0, poolConfig, new Random(42L));
            Map<MobStatType, Double> shares2 = distributor.distribute(1000.0, poolConfig, new Random(42L));

            for (MobStatType type : MobStatType.values()) {
                assertEquals(shares1.get(type), shares2.get(type), 0.001,
                    "Same seed should produce identical share for " + type);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Full Pipeline: MobStatGenerator Integration Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Full Generation Pipeline")
    class FullPipelineTests {

        @Test
        @DisplayName("Level 1 mob with no distance bonus has minimal stats")
        void level1_noDistanceBonus_hasMinimalStats() {
            MobStatProfile profile = generator.generate(1, 0.0, 12345L);

            assertEquals(1, profile.mobLevel());
            assertTrue(profile.totalPool() > 0, "Level 1 should still have some pool");
            assertTrue(profile.maxHealth() >= 1.0, "Health should be at least 1");
            assertTrue(profile.physicalDamage() >= 1.0, "Damage should be at least 1");
        }

        @Test
        @DisplayName("Higher level produces more total pool")
        void higherLevel_producesMoreTotalPool() {
            MobStatProfile low = generator.generate(5, 0.0, 42L);
            MobStatProfile mid = generator.generate(20, 0.0, 42L);
            MobStatProfile high = generator.generate(50, 0.0, 42L);

            assertTrue(mid.totalPool() > low.totalPool(),
                "Level 20 pool should exceed level 5 pool");
            assertTrue(high.totalPool() > mid.totalPool(),
                "Level 50 pool should exceed level 20 pool");
        }

        @Test
        @DisplayName("Distance bonus increases total pool")
        void distanceBonus_increasesTotalPool() {
            MobStatProfile noBonus = generator.generate(10, 0.0, 42L);
            MobStatProfile withBonus = generator.generate(10, 500.0, 42L);

            assertTrue(withBonus.totalPool() > noBonus.totalPool(),
                "Distance bonus should increase total pool");
        }

        @Test
        @DisplayName("Generated stats respect min/max bounds from StatConfig")
        void generatedStats_respectMinMaxBounds() {
            // Generate many profiles and verify bounds
            for (long seed = 1; seed <= 50; seed++) {
                MobStatProfile profile = generator.generate(30, 200.0, seed);

                // Move speed: min 0.3, max 3.0 (from enum defaults)
                StatConfig speedConfig = poolConfig.getStatConfig(MobStatType.MOVE_SPEED);
                assertTrue(profile.moveSpeed() >= speedConfig.getMinValue(),
                    "Seed " + seed + ": moveSpeed " + profile.moveSpeed() + " < min " + speedConfig.getMinValue());
                assertTrue(profile.moveSpeed() <= speedConfig.getMaxValue(),
                    "Seed " + seed + ": moveSpeed " + profile.moveSpeed() + " > max " + speedConfig.getMaxValue());

                // Critical chance: min 0, max 75
                assertTrue(profile.criticalChance() >= 0.0);
                assertTrue(profile.criticalChance() <= 75.0);

                // Dodge chance: min 0, max 50
                assertTrue(profile.dodgeChance() >= 0.0);
                assertTrue(profile.dodgeChance() <= 50.0);

                // Block chance: min 0, max 40
                assertTrue(profile.blockChance() >= 0.0);
                assertTrue(profile.blockChance() <= 40.0);

                // Attack speed: min 0.5, max 2.5
                assertTrue(profile.attackSpeed() >= 0.5);
                assertTrue(profile.attackSpeed() <= 2.5);
            }
        }

        @Test
        @DisplayName("Base values applied to stats that have non-zero defaults")
        void baseValues_appliedToStatsWithDefaults() {
            // With a pool of 0 (level 1, minFactor 0.3), stats should still have base values
            // Critical chance base = 5.0, Critical multiplier base = 150.0
            MobStatProfile profile = generator.generate(1, 0.0, 42L);

            // These should be at or above their base values (base + some pool share)
            assertTrue(profile.criticalChance() >= 5.0,
                "Critical chance should be at least base value 5.0, was " + profile.criticalChance());
            assertTrue(profile.criticalMultiplier() >= 100.0,
                "Critical multiplier should be at least min value 100.0, was " + profile.criticalMultiplier());
        }

        @Test
        @DisplayName("Move speed never drops below minimum (0.3)")
        void moveSpeed_neverDropsBelowMinimum() {
            for (long seed = 1; seed <= 100; seed++) {
                MobStatProfile profile = generator.generate(1, 0.0, seed);
                assertTrue(profile.moveSpeed() >= 0.3,
                    "Seed " + seed + ": moveSpeed " + profile.moveSpeed() + " below minimum 0.3");
            }
        }

        @Test
        @DisplayName("All elemental damage stats are non-negative")
        void allElementalDamage_nonNegative() {
            MobStatProfile profile = generator.generate(30, 300.0, 42L);
            assertTrue(profile.fireDamage() >= 0.0);
            assertTrue(profile.waterDamage() >= 0.0);
            assertTrue(profile.lightningDamage() >= 0.0);
            assertTrue(profile.voidDamage() >= 0.0);
        }

        @Test
        @DisplayName("All resistance stats are within bounds")
        void allResistance_withinBounds() {
            MobStatProfile profile = generator.generate(50, 500.0, 42L);
            // Max 90% for resistances
            assertTrue(profile.fireResistance() >= 0.0 && profile.fireResistance() <= 90.0);
            assertTrue(profile.waterResistance() >= 0.0 && profile.waterResistance() <= 90.0);
            assertTrue(profile.lightningResistance() >= 0.0 && profile.lightningResistance() <= 90.0);
            assertTrue(profile.voidResistance() >= 0.0 && profile.voidResistance() <= 90.0);
        }

        @Test
        @DisplayName("Seed determinism: same inputs always produce same outputs")
        void seedDeterminism_sameInputsSameOutputs() {
            MobStatProfile p1 = generator.generate(25, 150.0, 99999L);
            MobStatProfile p2 = generator.generate(25, 150.0, 99999L);

            assertEquals(p1.totalPool(), p2.totalPool(), 0.001);
            assertEquals(p1.maxHealth(), p2.maxHealth(), 0.001);
            assertEquals(p1.physicalDamage(), p2.physicalDamage(), 0.001);
            assertEquals(p1.moveSpeed(), p2.moveSpeed(), 0.001);
            assertEquals(p1.criticalChance(), p2.criticalChance(), 0.001);
            assertEquals(p1.fireDamage(), p2.fireDamage(), 0.001);
            assertEquals(p1.armor(), p2.armor(), 0.001);
        }

        @Test
        @DisplayName("Different seeds produce different stat profiles")
        void differentSeeds_produceDifferentProfiles() {
            MobStatProfile p1 = generator.generate(25, 150.0, 11111L);
            MobStatProfile p2 = generator.generate(25, 150.0, 22222L);

            // Total pool is deterministic (same level/bonus), but stat distribution differs
            assertEquals(p1.totalPool(), p2.totalPool(), 0.001,
                "Total pool should be the same for same level/bonus");
            // At least one stat should differ
            boolean anyDifferent = p1.maxHealth() != p2.maxHealth()
                || p1.physicalDamage() != p2.physicalDamage()
                || p1.moveSpeed() != p2.moveSpeed();
            assertTrue(anyDifferent, "Different seeds should produce different distributions");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Boss/Special Multiplier Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Boss and Special Multiplier")
    class BossMultiplierTests {

        @Test
        @DisplayName("Boss multiplier scales all combat stats")
        void bossMultiplier_scalesAllCombatStats() {
            MobStatProfile base = generator.generate(20, 100.0, 42L);
            MobStatProfile boss = base.withBossMultiplier(2.0);

            assertEquals(base.mobLevel(), boss.mobLevel());
            assertEquals(base.totalPool() * 2.0, boss.totalPool(), 0.001);
            assertTrue(boss.maxHealth() > base.maxHealth());
            assertTrue(boss.physicalDamage() > base.physicalDamage());
        }

        @Test
        @DisplayName("generateSpecial applies multiplier correctly")
        void generateSpecial_appliesMultiplierCorrectly() {
            MobStatProfile base = generator.generate(20, 100.0, 42L);
            MobStatProfile special = generator.generateSpecial(20, 100.0, 1.5, 42L);

            assertEquals(base.totalPool() * 1.5, special.totalPool(), 0.001);
        }

        @Test
        @DisplayName("Multiplier of 1.0 produces identical stats")
        void multiplierOne_producesIdenticalStats() {
            MobStatProfile base = generator.generate(15, 50.0, 42L);
            MobStatProfile same = base.withBossMultiplier(1.0);

            assertEquals(base.totalPool(), same.totalPool(), 0.001);
            assertEquals(base.maxHealth(), same.maxHealth(), 0.001);
            assertEquals(base.physicalDamage(), same.physicalDamage(), 0.001);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Base Stats (Equal Distribution) Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getBaseStats (Equal Distribution)")
    class BaseStatsTests {

        @Test
        @DisplayName("Base stats at level 1 are scaled")
        void baseStats_level1_isScaled() {
            MobStatProfile profile = generator.getBaseStats(1);
            assertEquals(1, profile.mobLevel());
            // Level 1 with progressive scaling should still have a pool > 0
            assertTrue(profile.totalPool() > 0);
        }

        @Test
        @DisplayName("Base stats at high level have larger pool")
        void baseStats_highLevel_hasLargerPool() {
            MobStatProfile low = generator.getBaseStats(5);
            MobStatProfile high = generator.getBaseStats(50);

            assertTrue(high.totalPool() > low.totalPool());
        }

        @Test
        @DisplayName("Base stats are deterministic (no random seed variation)")
        void baseStats_areDeterministic() {
            MobStatProfile p1 = generator.getBaseStats(20);
            MobStatProfile p2 = generator.getBaseStats(20);

            assertEquals(p1.maxHealth(), p2.maxHealth(), 0.001);
            assertEquals(p1.physicalDamage(), p2.physicalDamage(), 0.001);
            assertEquals(p1.moveSpeed(), p2.moveSpeed(), 0.001);
        }

        @Test
        @DisplayName("getBaseStats reports correct level")
        void getBaseStats_reportsCorrectLevel() {
            assertEquals(10, generator.getBaseStats(10).mobLevel());
            assertEquals(50, generator.getBaseStats(50).mobLevel());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MobStatProfile Methods Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("MobStatProfile Methods")
    class MobStatProfileMethodsTests {

        @Test
        @DisplayName("UNSCALED profile has expected sentinel values")
        void unscaledProfile_hasExpectedValues() {
            MobStatProfile u = MobStatProfile.UNSCALED;
            assertEquals(1, u.mobLevel());
            assertEquals(0.0, u.totalPool(), 0.001);
            assertEquals(50.0, u.maxHealth(), 0.001);
            assertEquals(5.0, u.physicalDamage(), 0.001);
            assertEquals(1.0, u.moveSpeed(), 0.001);
            assertEquals(1.0, u.attackSpeed(), 0.001);
            assertEquals(5.0, u.criticalChance(), 0.001);
            assertEquals(150.0, u.criticalMultiplier(), 0.001);
            assertFalse(u.isScaled());
        }

        @Test
        @DisplayName("isScaled returns true when level > 1")
        void isScaled_trueWhenLevelAboveOne() {
            MobStatProfile profile = generator.generate(5, 0.0, 42L);
            assertTrue(profile.isScaled());
        }

        @Test
        @DisplayName("isScaled returns true when totalPool > 0 (even level 1)")
        void isScaled_trueWhenPoolAboveZero() {
            MobStatProfile profile = generator.generate(1, 100.0, 42L);
            assertTrue(profile.isScaled());
        }

        @Test
        @DisplayName("getScaledDamage multiplies base damage by physicalDamage factor")
        void getScaledDamage_multipliesCorrectly() {
            MobStatProfile profile = generator.generate(20, 100.0, 42L);
            double baseDmg = 10.0;
            assertEquals(baseDmg * profile.physicalDamage(), profile.getScaledDamage(baseDmg), 0.001);
        }

        @Test
        @DisplayName("getEffectiveCooldown divides base by attack speed")
        void getEffectiveCooldown_dividesCorrectly() {
            MobStatProfile profile = generator.generate(20, 100.0, 42L);
            double baseCooldown = 2.0;
            assertEquals(baseCooldown / profile.attackSpeed(), profile.getEffectiveCooldown(baseCooldown), 0.001);
        }

        @Test
        @DisplayName("getEffectiveRange adds attack range to base")
        void getEffectiveRange_addsCorrectly() {
            MobStatProfile profile = generator.generate(20, 100.0, 42L);
            double baseRange = 3.0;
            assertEquals(baseRange + profile.attackRange(), profile.getEffectiveRange(baseRange), 0.001);
        }

        @Test
        @DisplayName("getTotalElementalDamage sums four elements")
        void getTotalElementalDamage_sumsFourElements() {
            MobStatProfile profile = generator.generate(30, 300.0, 42L);
            double expected = profile.fireDamage() + profile.waterDamage()
                + profile.lightningDamage() + profile.voidDamage();
            assertEquals(expected, profile.getTotalElementalDamage(), 0.001);
        }

        @Test
        @DisplayName("getTotalElementalResistance averages four resistances")
        void getTotalElementalResistance_averagesFourResistances() {
            MobStatProfile profile = generator.generate(30, 300.0, 42L);
            double expected = (profile.fireResistance() + profile.waterResistance()
                + profile.lightningResistance() + profile.voidResistance()) / 4.0;
            assertEquals(expected, profile.getTotalElementalResistance(), 0.001);
        }

        @Test
        @DisplayName("getEffectivePower is non-negative and at least mob level")
        void getEffectivePower_nonNegativeAndAtLeastLevel() {
            MobStatProfile profile = generator.generate(25, 200.0, 42L);
            int power = profile.getEffectivePower();
            assertTrue(power > 0);
            assertTrue(power >= profile.mobLevel());
        }

        @Test
        @DisplayName("toComputedStats returns non-null")
        void toComputedStats_returnsNonNull() {
            MobStatProfile profile = generator.generate(20, 100.0, 42L);
            assertNotNull(profile.toComputedStats());
        }

        @Test
        @DisplayName("getEffectiveDamage includes elemental damage and crit multiplier")
        void getEffectiveDamage_includesElementalAndCrit() {
            MobStatProfile profile = generator.generate(30, 300.0, 42L);

            double noCrit = profile.getEffectiveDamage(10.0, false);
            double withCrit = profile.getEffectiveDamage(10.0, true);

            // Non-crit: base * physDmg + elementals
            double expectedNoCrit = 10.0 * profile.physicalDamage()
                + profile.fireDamage() + profile.waterDamage()
                + profile.lightningDamage() + profile.voidDamage();
            assertEquals(expectedNoCrit, noCrit, 0.001);

            // Crit should be higher than non-crit (multiplier > 100%)
            assertTrue(withCrit >= noCrit, "Crit damage should be >= non-crit damage");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MobStatType Enum Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("MobStatType Enum")
    class MobStatTypeEnumTests {

        @Test
        @DisplayName("All stat types have non-null config key and display name")
        void allStatTypes_haveNonNullMetadata() {
            for (MobStatType type : MobStatType.values()) {
                assertNotNull(type.configKey, type + " has null configKey");
                assertNotNull(type.displayName, type + " has null displayName");
                assertNotNull(type.category, type + " has null category");
            }
        }

        @Test
        @DisplayName("Min value is less than or equal to max value for all types")
        void minValue_lessOrEqualToMaxValue() {
            for (MobStatType type : MobStatType.values()) {
                assertTrue(type.minValue <= type.maxValue,
                    type + ": minValue " + type.minValue + " > maxValue " + type.maxValue);
            }
        }

        @Test
        @DisplayName("Base value is within min/max bounds for all types")
        void baseValue_withinBounds() {
            for (MobStatType type : MobStatType.values()) {
                assertTrue(type.baseValue >= type.minValue,
                    type + ": baseValue " + type.baseValue + " < minValue " + type.minValue);
                assertTrue(type.baseValue <= type.maxValue,
                    type + ": baseValue " + type.baseValue + " > maxValue " + type.maxValue);
            }
        }

        @Test
        @DisplayName("Alpha weights are positive for all types")
        void alphaWeights_arePositive() {
            for (MobStatType type : MobStatType.values()) {
                assertTrue(type.alphaWeight > 0,
                    type + " has non-positive alphaWeight: " + type.alphaWeight);
            }
        }

        @Test
        @DisplayName("Category enum covers all expected categories")
        void categoryEnum_coversExpectedCategories() {
            MobStatType.Category[] categories = MobStatType.Category.values();
            assertTrue(categories.length >= 10,
                "Expected at least 10 stat categories, found " + categories.length);
        }

        @Test
        @DisplayName("Stat count matches archetype weight array length")
        void statCount_matchesArchetypeWeightLength() {
            int expected = MobStatType.values().length;
            double[] warriorWeights = poolConfig.getArchetypeWeights("warrior");
            assertEquals(expected, warriorWeights.length,
                "Archetype weight array length should match stat count");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Edge Cases and Stress Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Level 0 does not crash")
        void levelZero_doesNotCrash() {
            MobStatProfile profile = generator.generate(0, 0.0, 42L);
            assertNotNull(profile);
            assertEquals(0, profile.mobLevel());
        }

        @Test
        @DisplayName("Negative distance bonus treated as negative pool contribution")
        void negativeDistanceBonus_treatedAsNegativePool() {
            // While negative distance bonus is unusual, the generator should not crash
            assertDoesNotThrow(() -> generator.generate(10, -50.0, 42L));
        }

        @Test
        @DisplayName("Very high level does not overflow")
        void veryHighLevel_doesNotOverflow() {
            MobStatProfile profile = generator.generate(10000, 0.0, 42L);
            assertNotNull(profile);
            assertTrue(profile.totalPool() > 0);
            assertFalse(Double.isNaN(profile.totalPool()));
            assertFalse(Double.isInfinite(profile.totalPool()));
        }

        @Test
        @DisplayName("Seed of 0 works correctly")
        void seedZero_worksCorrectly() {
            MobStatProfile profile = generator.generate(20, 100.0, 0L);
            assertNotNull(profile);
            assertTrue(profile.maxHealth() > 0);
        }

        @Test
        @DisplayName("Long.MIN_VALUE seed works correctly")
        void seedLongMinValue_worksCorrectly() {
            MobStatProfile profile = generator.generate(20, 100.0, Long.MIN_VALUE);
            assertNotNull(profile);
            assertTrue(profile.maxHealth() > 0);
        }

        @Test
        @DisplayName("Many sequential generates produce varied distributions")
        void manySequentialGenerates_produceVariedDistributions() {
            double minHealth = Double.MAX_VALUE;
            double maxHealth = 0;

            for (long seed = 1; seed <= 100; seed++) {
                MobStatProfile profile = generator.generate(20, 100.0, seed);
                minHealth = Math.min(minHealth, profile.maxHealth());
                maxHealth = Math.max(maxHealth, profile.maxHealth());
            }

            // There should be meaningful variance in health across 100 different seeds
            assertTrue(maxHealth > minHealth * 1.1,
                "Expected meaningful health variance, min=" + minHealth + " max=" + maxHealth);
        }
    }
}
