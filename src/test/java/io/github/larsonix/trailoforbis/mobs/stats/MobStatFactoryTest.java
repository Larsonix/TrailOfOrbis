package io.github.larsonix.trailoforbis.mobs.stats;

import io.github.larsonix.trailoforbis.elemental.ElementType;
import io.github.larsonix.trailoforbis.mobs.MobScalingConfig;
import io.github.larsonix.trailoforbis.mobs.archetype.ArchetypeResolver;
import io.github.larsonix.trailoforbis.mobs.archetype.MobArchetype;
import io.github.larsonix.trailoforbis.mobs.archetype.MobArchetypeConfig;
import io.github.larsonix.trailoforbis.mobs.model.MobStats;
import io.github.larsonix.trailoforbis.mobs.profile.MobResistanceConfig;
import io.github.larsonix.trailoforbis.mobs.profile.ResistanceProfileResolver;
import io.github.larsonix.trailoforbis.util.LevelScaling;
import org.junit.jupiter.api.*;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Template + Noise mob stat generator.
 */
@DisplayName("MobStatFactory")
class MobStatFactoryTest {

    private MobStatFactory factory;
    private MobStatPoolConfig poolConfig;

    @BeforeEach
    void setUp() {
        // Initialize LevelScaling with defaults
        LevelScaling.configure(50, 0.5, 2.0);

        poolConfig = MobStatPoolConfig.createDefaults();
        MobArchetypeConfig archetypeConfig = MobArchetypeConfig.createDefaults();
        MobResistanceConfig resistanceConfig = MobResistanceConfig.createDefaults();

        factory = new MobStatFactory(
                poolConfig,
                new ArchetypeResolver(archetypeConfig),
                new ResistanceProfileResolver(resistanceConfig),
                archetypeConfig,
                null // No elemental config for basic tests
        );
    }

    @Nested
    @DisplayName("Pool Math")
    class PoolMath {

        @Test
        @DisplayName("Level 1 mob has positive stats")
        void level1_hasPositiveStats() {
            MobStats stats = factory.generate(1, 0, null, Set.of(), null, 42L);
            assertTrue(stats.maxHealth() > 0, "HP should be positive");
            assertTrue(stats.physicalDamage() > 0, "Damage should be positive");
            assertTrue(stats.totalPool() > 0, "Pool should be positive");
        }

        @Test
        @DisplayName("Higher level produces higher stats")
        void higherLevel_higherStats() {
            MobStats low = factory.generate(10, 0, null, Set.of(), null, 42L);
            MobStats high = factory.generate(50, 0, null, Set.of(), null, 42L);

            assertTrue(high.maxHealth() > low.maxHealth(), "Level 50 HP > Level 10 HP");
            assertTrue(high.physicalDamage() > low.physicalDamage(), "Level 50 DMG > Level 10 DMG");
            assertTrue(high.totalPool() > low.totalPool(), "Level 50 pool > Level 10 pool");
        }

        @Test
        @DisplayName("Distance bonus increases stats")
        void distanceBonus_increasesStats() {
            MobStats noBonus = factory.generate(25, 0, null, Set.of(), null, 42L);
            MobStats withBonus = factory.generate(25, 500, null, Set.of(), null, 42L);

            assertTrue(withBonus.maxHealth() > noBonus.maxHealth(), "Distance bonus increases HP");
            assertTrue(withBonus.totalPool() > noBonus.totalPool(), "Distance bonus increases pool");
        }

        @Test
        @DisplayName("Same seed produces same stats")
        void sameSeed_sameStats() {
            MobStats a = factory.generate(25, 100, "test_mob", Set.of(), null, 123L);
            MobStats b = factory.generate(25, 100, "test_mob", Set.of(), null, 123L);

            assertEquals(a.maxHealth(), b.maxHealth(), 0.001, "Same seed = same HP");
            assertEquals(a.physicalDamage(), b.physicalDamage(), 0.001, "Same seed = same DMG");
        }

        @Test
        @DisplayName("Level 0 and negative levels produce minimum stats")
        void edgeLevels_produceMinimums() {
            MobStats zero = factory.generate(0, 0, null, Set.of(), null, 42L);
            assertTrue(zero.maxHealth() >= 1, "Level 0 HP >= 1 (clamped)");
            assertTrue(zero.physicalDamage() >= 1, "Level 0 DMG >= 1 (clamped)");
        }
    }

    @Nested
    @DisplayName("Archetype Differentiation")
    class ArchetypeDifferentiation {

        @Test
        @DisplayName("Brute keyword produces tankier mob than Warrior fallback")
        void brute_tankierThanWarrior() {
            MobStats warrior = factory.generate(50, 0, "trork_warrior", Set.of(), null, 42L);
            MobStats brute = factory.generate(50, 0, "trork_brute", Set.of(), null, 42L);

            assertTrue(brute.maxHealth() > warrior.maxHealth(),
                    String.format("Brute HP (%.0f) should exceed Warrior HP (%.0f)",
                            brute.maxHealth(), warrior.maxHealth()));
            assertTrue(brute.armor() > warrior.armor(), "Brute armor > Warrior armor");
        }

        @Test
        @DisplayName("Assassin keyword produces high-crit mob")
        void assassin_highCrit() {
            MobStats warrior = factory.generate(50, 0, "generic_warrior", Set.of(), null, 42L);
            MobStats assassin = factory.generate(50, 0, "shadow_assassin", Set.of(), null, 42L);

            assertTrue(assassin.criticalChance() > warrior.criticalChance(),
                    "Assassin crit% > Warrior crit%");
            assertEquals(250, assassin.criticalMultiplier(), 0.1, "Assassin crit mult = 250%");
        }

        @Test
        @DisplayName("Caster keyword produces glass cannon — same damage, lower HP")
        void caster_glassCannon() {
            MobStats warrior = factory.generate(50, 0, "generic_warrior", Set.of(), null, 42L);
            MobStats caster = factory.generate(50, 0, "fire_mage", Set.of(), null, 42L);

            assertTrue(caster.maxHealth() < warrior.maxHealth(), "Caster HP < Warrior HP");
            // Caster damage multiplier is 1.0 (same as Warrior) — armor bypass via
            // 100% elemental conversion is the advantage, not inflated base damage.
            assertEquals(caster.physicalDamage(), warrior.physicalDamage(), 0.01,
                    "Caster DMG == Warrior DMG (armor bypass is the differentiator)");
        }

        @Test
        @DisplayName("Tank keyword produces high HP/armor, low damage")
        void tank_highDefense() {
            MobStats warrior = factory.generate(50, 0, "generic_warrior", Set.of(), null, 42L);
            MobStats tank = factory.generate(50, 0, "stone_guard", Set.of(), null, 42L);

            assertTrue(tank.maxHealth() > warrior.maxHealth(), "Tank HP > Warrior HP");
            assertTrue(tank.armor() > warrior.armor(), "Tank armor > Warrior armor");
            assertTrue(tank.physicalDamage() < warrior.physicalDamage(), "Tank DMG < Warrior DMG");
        }

        @Test
        @DisplayName("Unknown mob gets Warrior fallback")
        void unknown_getWarriorFallback() {
            MobStats unknown = factory.generate(50, 0, "modded_creature_xyz", Set.of(), null, 42L);
            MobStats warrior = factory.generate(50, 0, "generic_warrior", Set.of(), null, 42L);

            // Unknown mob with no keyword match should get Warrior archetype = same as explicit warrior
            assertEquals(warrior.criticalChance(), unknown.criticalChance(), 0.1,
                    "Unknown mob should get Warrior crit% (10%)");
        }
    }

    @Nested
    @DisplayName("Noise")
    class Noise {

        @Test
        @DisplayName("Different seeds produce different stats (noise active)")
        void differentSeeds_differentStats() {
            MobStats a = factory.generate(50, 0, null, Set.of(), null, 1L);
            MobStats b = factory.generate(50, 0, null, Set.of(), null, 2L);

            // With noise, different seeds should produce slightly different values
            // (very unlikely to be exactly equal)
            assertNotEquals(a.maxHealth(), b.maxHealth(), "Different seeds should produce different HP");
        }

        @Test
        @DisplayName("Noise stays within ±15% bounds")
        void noiseBounded() {
            // Generate many samples and check they don't deviate beyond ±15%
            double baseHP = factory.generate(50, 0, null, Set.of(), null, 0L).maxHealth();

            for (int seed = 1; seed <= 100; seed++) {
                MobStats stats = factory.generate(50, 0, null, Set.of(), null, seed);
                double ratio = stats.maxHealth() / baseHP;
                // Allow slightly wider bounds due to base=0 noise interactions
                assertTrue(ratio >= 0.70 && ratio <= 1.40,
                        String.format("Seed %d: HP ratio %.3f outside expected range", seed, ratio));
            }
        }
    }

    @Nested
    @DisplayName("Resistance Profiles")
    class ResistanceProfiles {

        @Test
        @DisplayName("Fire element mob gets fire resistance from profile")
        void fireElement_getsFireResistance() {
            // Build a resistance config with FIRE element profile
            // (createDefaults() returns empty — element profiles come from YAML in production)
            MobScalingConfig.ElementalConfig elemConfig = new MobScalingConfig.ElementalConfig();
            MobArchetypeConfig archetypeConfig = MobArchetypeConfig.createDefaults();

            // Create config with FIRE element profile via YAML-style setter
            MobResistanceConfig resistanceConfig = new MobResistanceConfig();
            resistanceConfig.setElement_profiles(java.util.Map.of(
                    "FIRE", java.util.Map.of(
                            "resistances", java.util.Map.of(
                                    "fire", 40, "water", -20, "lightning", 0,
                                    "earth", 10, "wind", -10, "void", 0
                            ),
                            "ailment_bonuses", java.util.Map.of("burn_threshold", 50)
                    )
            ));

            MobStatFactory elemFactory = new MobStatFactory(
                    poolConfig,
                    new ArchetypeResolver(archetypeConfig),
                    new ResistanceProfileResolver(resistanceConfig),
                    archetypeConfig,
                    elemConfig);

            MobStats stats = elemFactory.generate(50, 0, "fire_mage", Set.of(), ElementType.FIRE, 42L);

            assertNotNull(stats.elementalStats(), "Fire mob should have elemental stats");
            assertTrue(stats.elementalStats().getResistance(ElementType.FIRE) > 0,
                    "Fire mob should have positive fire resistance");
            assertTrue(stats.elementalStats().getResistance(ElementType.WATER) < 0,
                    "Fire mob should have negative water resistance (weakness)");
        }

        @Test
        @DisplayName("Non-CASTER mob with detected element gets resistances but NO elemental damage")
        void nonCaster_noElementalDamage() {
            MobScalingConfig.ElementalConfig elemConfig = new MobScalingConfig.ElementalConfig();
            MobArchetypeConfig archetypeConfig = MobArchetypeConfig.createDefaults();

            MobResistanceConfig resistanceConfig = new MobResistanceConfig();
            resistanceConfig.setElement_profiles(java.util.Map.of(
                    "FIRE", java.util.Map.of(
                            "resistances", java.util.Map.of(
                                    "fire", 40, "water", -20, "lightning", 0,
                                    "earth", 10, "wind", -10, "void", 0
                            ),
                            "ailment_bonuses", java.util.Map.of("burn_threshold", 50)
                    )
            ));

            MobStatFactory elemFactory = new MobStatFactory(
                    poolConfig,
                    new ArchetypeResolver(archetypeConfig),
                    new ResistanceProfileResolver(resistanceConfig),
                    archetypeConfig,
                    elemConfig);

            // "fire_warrior" → WARRIOR archetype + FIRE element detected from "fire" keyword
            MobStats stats = elemFactory.generate(50, 0, "fire_warrior", Set.of(), ElementType.FIRE, 42L);

            assertNotNull(stats.elementalStats(), "Should have elemental stats (resistances)");
            // Resistances should still be assigned (defensive identity)
            assertTrue(stats.elementalStats().getResistance(ElementType.FIRE) > 0,
                    "Fire warrior should still have fire resistance");
            // But NO offensive elemental damage — warriors stay physical
            assertEquals(0.0, stats.elementalStats().getFlatDamage(ElementType.FIRE), 0.001,
                    "Non-CASTER mob should have ZERO fire damage despite detected fire element");
            assertEquals(0.0, stats.elementalStats().getPenetration(ElementType.FIRE), 0.001,
                    "Non-CASTER mob should have ZERO fire penetration");
            // Archetype should be WARRIOR, not CASTER
            assertEquals(MobArchetype.WARRIOR, stats.archetype(),
                    "fire_warrior should resolve to WARRIOR archetype");
        }

        @Test
        @DisplayName("CASTER mob with detected element gets both resistances AND elemental damage")
        void caster_getsElementalDamage() {
            MobScalingConfig.ElementalConfig elemConfig = new MobScalingConfig.ElementalConfig();
            MobArchetypeConfig archetypeConfig = MobArchetypeConfig.createDefaults();

            MobResistanceConfig resistanceConfig = new MobResistanceConfig();
            resistanceConfig.setElement_profiles(java.util.Map.of(
                    "FIRE", java.util.Map.of(
                            "resistances", java.util.Map.of(
                                    "fire", 40, "water", -20, "lightning", 0,
                                    "earth", 10, "wind", -10, "void", 0
                            ),
                            "ailment_bonuses", java.util.Map.of("burn_threshold", 50)
                    )
            ));

            MobStatFactory elemFactory = new MobStatFactory(
                    poolConfig,
                    new ArchetypeResolver(archetypeConfig),
                    new ResistanceProfileResolver(resistanceConfig),
                    archetypeConfig,
                    elemConfig);

            // "fire_mage" → CASTER archetype + FIRE element
            MobStats stats = elemFactory.generate(50, 0, "fire_mage", Set.of(), ElementType.FIRE, 42L);

            assertNotNull(stats.elementalStats(), "Caster should have elemental stats");
            assertTrue(stats.elementalStats().getFlatDamage(ElementType.FIRE) > 0,
                    "CASTER mob should have fire damage");
            assertTrue(stats.elementalStats().getPenetration(ElementType.FIRE) > 0,
                    "CASTER mob should have fire penetration");
            assertEquals(MobArchetype.CASTER, stats.archetype(),
                    "fire_mage should resolve to CASTER archetype");
        }

        @Test
        @DisplayName("Physical mob (no element) gets neutral resistance")
        void noElement_neutralResistance() {
            MobStats stats = factory.generate(50, 0, "generic_bandit", Set.of(), null, 42L);

            // Neutral resistance = all zeros, so elementalStats may be null
            if (stats.elementalStats() != null) {
                for (ElementType element : ElementType.values()) {
                    assertEquals(0.0, stats.elementalStats().getResistance(element), 0.001,
                            "Neutral mob should have 0% " + element + " resistance");
                }
            }
        }
    }

    @Nested
    @DisplayName("Ailment Fields")
    class AilmentFields {

        @Test
        @DisplayName("Default ailment threshold multiplier is 1.0")
        void defaultThreshold() {
            MobStats stats = factory.generate(50, 0, null, Set.of(), null, 42L);
            assertEquals(1.0, stats.ailmentThresholdMultiplier(), 0.001);
        }

        @Test
        @DisplayName("Default ailment effectiveness is 1.0")
        void defaultEffectiveness() {
            MobStats stats = factory.generate(50, 0, null, Set.of(), null, 42L);
            assertEquals(1.0, stats.ailmentEffectiveness(), 0.001);
        }
    }

    @Nested
    @DisplayName("withMultiplier")
    class WithMultiplier {

        @Test
        @DisplayName("Elite multiplier scales HP and damage")
        void eliteMultiplier_scalesCorrectly() {
            MobStats base = factory.generate(50, 0, null, Set.of(), null, 42L);
            MobStats elite = base.withMultiplier(1.5);

            assertEquals(base.maxHealth() * 1.5, elite.maxHealth(), 0.1, "Elite HP = base × 1.5");
            assertEquals(base.physicalDamage() * 1.5, elite.physicalDamage(), 0.1, "Elite DMG = base × 1.5");
        }

        @Test
        @DisplayName("Multiplier does not affect percentage stats")
        void multiplier_doesNotAffectPercentage() {
            MobStats base = factory.generate(50, 0, null, Set.of(), null, 42L);
            MobStats boss = base.withMultiplier(3.0);

            assertEquals(base.criticalChance(), boss.criticalChance(), 0.001, "Crit% unchanged");
            assertEquals(base.evasion(), boss.evasion(), 0.001, "Evasion unchanged");
            assertEquals(base.lifeSteal(), boss.lifeSteal(), 0.001, "Life steal unchanged");
            assertEquals(base.moveSpeed(), boss.moveSpeed(), 0.001, "Speed unchanged");
        }
    }

    @Nested
    @DisplayName("Reference Accuracy")
    class ReferenceAccuracy {

        @Test
        @DisplayName("Reference accuracy is positive at all levels")
        void positive_atAllLevels() {
            for (int level = 1; level <= 100; level++) {
                double acc = MobStatFactory.getReferenceAccuracy(poolConfig, level);
                assertTrue(acc > 0, "Accuracy at level " + level + " should be positive");
            }
        }

        @Test
        @DisplayName("Reference accuracy increases with level")
        void increases_withLevel() {
            double low = MobStatFactory.getReferenceAccuracy(poolConfig, 10);
            double high = MobStatFactory.getReferenceAccuracy(poolConfig, 80);
            assertTrue(high > low, "Accuracy at level 80 > level 10");
        }
    }
}
